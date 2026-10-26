package com.dex.boxshopkeeper.command;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.gui.MainMenuGui;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.item.MmoItemsUpdater;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.util.Numbers;
import com.dex.boxshopkeeper.util.RateLimiter.Action;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** {@code /chetao} and its sub-commands (§8). Permissions are checked on every invocation. */
public final class TradeCommand implements TabExecutor {

    public static final String USE = "chetao.use";
    public static final String ADMIN = "chetao.admin";
    private static final int DELETE_CONFIRM_TICKS = 200;
    private static final String CONSOLE_KEY = "console";
    private static final Pattern DIGITS = Pattern.compile("\\d{1,18}");
    private static final List<String> TOKEN_AMOUNTS = List.of("100", "1000", "10000", "50000");
    private static final String UPDATE_MMOITEMS = "update-mmoitems";
    /** Problem lines shown per shop; the rest only go to the console. */
    private static final int PROBLEM_LINES = 10;

    private record PendingDelete(String shopId, int tick) {
    }

    private final BoxShopkeeperPlugin plugin;
    private final Map<String, PendingDelete> pendingDeletes = new HashMap<>();

    public TradeCommand(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    public void forget(UUID player) {
        pendingDeletes.remove(player.toString());
    }

    public int trackedEntries() {
        return pendingDeletes.size();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        try {
            handle(sender, args);
        } catch (Throwable error) {
            plugin.errors().report("Error running /chetao " + String.join(" ", args), error);
            plugin.messages().send(sender, "command.error");
        }
        return true;
    }

    private void handle(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (args.length == 0) {
            openMenu(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "open" -> open(sender, args);
            case "admin", "edit", "create", "delete", "list", "reload", "currency", UPDATE_MMOITEMS -> {
                if (!sender.hasPermission(ADMIN)) {
                    messages.send(sender, "command.no-permission");
                    return;
                }
                admin(sender, sub, args);
            }
            default -> help(sender);
        }
    }

    // ---------------------------------------------------------------- player commands

    private void openMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        // The main menu lists every shop: admins only. Players open shops through /chetao open or an NPC.
        if (!player.hasPermission(ADMIN)) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        if (plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.COMMAND, plugin.settings().commandTicks)) {
            plugin.mainMenu().open(player, 0);
        }
    }

    private void open(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "command.player-only");
                return;
            }
            if (!player.hasPermission(USE)) {
                messages.send(player, "command.no-permission");
                return;
            }
            if (!plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.COMMAND, plugin.settings().commandTicks)) {
                return;
            }
            Shop shop = plugin.shops().get(args[1].toLowerCase(Locale.ROOT));
            if (shop == null || !MainMenuGui.canOpen(player, shop)) {
                messages.send(player, "shop.unknown", Ph.of("shop", args[1]));
                return;
            }
            plugin.shopView().open(player, shop, 0);
            return;
        }
        if (args.length == 3) {
            // Console (all permissions) or chetao.admin only; command blocks without it are refused too.
            if (!sender.hasPermission(ADMIN)) {
                messages.send(sender, "command.no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messages.send(sender, "command.player-offline", Ph.of("player", args[2]));
                return;
            }
            Shop shop = plugin.shops().get(args[1].toLowerCase(Locale.ROOT));
            if (shop == null) {
                messages.send(sender, "shop.unknown", Ph.of("shop", args[1]));
                return;
            }
            if (!MainMenuGui.canOpen(target, shop)) {
                messages.send(sender, "command.target-no-access",
                        Ph.of("player", target.getName()), Ph.of("shop", shop.styledName()));
                return;
            }
            if (!plugin.rateLimiter().tryAcquire(target.getUniqueId(), Action.COMMAND, plugin.settings().commandTicks)) {
                return;
            }
            plugin.shopView().open(target, shop, 0);
            if (sender != target) {
                messages.send(sender, "command.opened-for",
                        Ph.of("shop", shop.styledName()), Ph.of("player", target.getName()));
            }
            return;
        }
        usage(sender, sender.hasPermission(ADMIN) ? "open-other" : "open");
    }

    private void help(CommandSender sender) {
        Messages messages = plugin.messages();
        boolean player = sender instanceof Player;
        boolean use = sender.hasPermission(USE);
        boolean admin = sender.hasPermission(ADMIN);
        messages.sendPlain(sender, "help.header");
        if (player && use) {
            helpLine(sender, "help");
            helpLine(sender, "open");
        }
        if (admin) {
            helpLine(sender, "open-other");
            if (player) {
                helpLine(sender, "menu");
                helpLine(sender, "admin");
                helpLine(sender, "edit");
                helpLine(sender, "currency");
            }
            helpLine(sender, "create");
            helpLine(sender, "delete");
            helpLine(sender, "list");
            helpLine(sender, "reload");
            helpLine(sender, UPDATE_MMOITEMS);
        }
    }

    private void helpLine(CommandSender sender, String key) {
        plugin.messages().sendPlain(sender, "help." + key, Ph.of("syntax", plugin.messages().raw("syntax." + key)));
    }

    private void usage(CommandSender sender, String syntaxKey) {
        plugin.messages().send(sender, "command.usage", Ph.of("usage", plugin.messages().raw("syntax." + syntaxKey)));
    }

    // ---------------------------------------------------------------- admin commands

    private void admin(CommandSender sender, String sub, String[] args) {
        Messages messages = plugin.messages();
        switch (sub) {
            case "admin" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "command.player-only");
                    return;
                }
                plugin.editor().openList(player, 0);
            }
            case "edit" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "command.player-only");
                    return;
                }
                if (args.length != 2) {
                    usage(sender, "edit");
                    return;
                }
                plugin.editor().enterShop(player, args[1].toLowerCase(Locale.ROOT), 0);
            }
            case "create" -> {
                if (args.length != 2) {
                    usage(sender, "create");
                    return;
                }
                Shop shop = plugin.editor().createShop(sender, args[1]);
                if (shop != null) {
                    messages.send(sender, "editor.saved", Ph.of("shop", shop.styledName()));
                    if (sender instanceof Player player) {
                        plugin.editor().enterShop(player, shop.id(), 0);
                    }
                }
            }
            case "delete" -> {
                if (args.length != 2) {
                    usage(sender, "delete");
                    return;
                }
                delete(sender, args[1].toLowerCase(Locale.ROOT));
            }
            case "list" -> list(sender);
            case "reload" -> plugin.reload(sender);
            case "currency" -> currency(sender, args);
            case UPDATE_MMOITEMS -> updateMmoItems(sender, args);
            default -> help(sender);
        }
    }

    /**
     * {@code /chetao update-mmoitems <shop|all> [preview]}: rebuilds every MMOItems template of the
     * shops from the current MMOItems templates, keeping amounts. One save (and one backup) per
     * changed shop; {@code preview} only reports. Shops being edited are skipped.
     */
    private void updateMmoItems(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        boolean preview = args.length == 3 && args[2].equalsIgnoreCase("preview");
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !preview)) {
            usage(sender, UPDATE_MMOITEMS);
            return;
        }
        // No rate limit: a one-off admin command must always answer.
        plugin.getLogger().info("update-mmoitems " + String.join(" ", args).substring(UPDATE_MMOITEMS.length()).trim()
                + " started by " + sender.getName());
        MmoItemsUpdater updater = MmoItemsUpdater.create(plugin.settings().limits.templateBytes());
        if (updater == null) {
            plugin.getLogger().info("update-mmoitems: MMOItems is not enabled or its API cannot be loaded");
            messages.send(sender, "update.no-mmoitems");
            return;
        }
        List<Shop> targets;
        if (args[1].equalsIgnoreCase("all")) {
            targets = new ArrayList<>(plugin.shops().all());
        } else {
            Shop shop = plugin.shops().get(args[1].toLowerCase(Locale.ROOT));
            if (shop == null) {
                messages.send(sender, "shop.unknown", Ph.of("shop", args[1]));
                return;
            }
            targets = List.of(shop);
        }
        int totalItems = 0;
        int totalShops = 0;
        for (Shop shop : targets) {
            String editing = plugin.editor().lockHolderName(shop.id());
            if (editing != null) {
                messages.sendPlain(sender, "update.locked", Ph.of("shop", shop.styledName()), Ph.of("player", editing));
                continue;
            }
            MmoItemsUpdater.Result result;
            try {
                result = updater.update(shop.trades());
            } catch (LinkageError e) {
                plugin.errors().report("MMOItems API is not compatible with /chetao update-mmoitems", e);
                messages.send(sender, "update.incompatible");
                return;
            }
            int shown = 0;
            for (MmoItemsUpdater.Problem problem : result.problems()) {
                plugin.getLogger().warning("update-mmoitems: " + shop.id() + "/" + problem.tradeId() + " "
                        + problem.key() + " kept: " + problem.reason());
                if (shown++ < PROBLEM_LINES) {
                    String reason = switch (problem.reason()) {
                        case "not found" -> messages.raw("update.reason.not-found");
                        case "too large" -> messages.raw("update.reason.too-large");
                        default -> problem.reason();
                    };
                    messages.sendPlain(sender, "update.problem", Ph.of("item", problem.key()),
                            Ph.of("shop", shop.id()), Ph.of("trade", problem.tradeId()), Ph.of("reason", reason));
                }
            }
            if (shown > PROBLEM_LINES) {
                messages.sendPlain(sender, "update.more-problems", Ph.of("count", shown - PROBLEM_LINES));
            }
            if (!result.changed()) {
                messages.sendPlain(sender, "update.unchanged", Ph.of("shop", shop.styledName()));
                continue;
            }
            if (!preview) {
                plugin.shops().save(shop.toBuilder().trades(result.trades()), plugin.settings());
                plugin.refreshViewers(shop.id());
                plugin.getLogger().info("update-mmoitems by " + sender.getName() + ": " + shop.id() + " "
                        + result.items() + " items in " + result.tradesChanged() + " trades");
            }
            messages.sendPlain(sender, preview ? "update.shop-preview" : "update.shop",
                    Ph.of("shop", shop.styledName()), Ph.of("items", result.items()),
                    Ph.of("trades", result.tradesChanged()));
            totalItems += result.items();
            totalShops++;
        }
        plugin.getLogger().info("update-mmoitems" + (preview ? " (preview)" : "") + " finished: " + totalItems
                + " items in " + totalShops + " of " + targets.size() + " shops");
        messages.sendPlain(sender, preview ? "update.preview-done" : "update.done",
                Ph.of("items", totalItems), Ph.of("shops", totalShops));
    }

    /** {@code /chetao currency <type> <amount>}: gives the admin an editor token (update 01, §4). */
    private void currency(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length != 3) {
            usage(sender, "currency");
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (!CurrencyCost.known(type)) {
            messages.send(player, "currency.unknown", Ph.of("type", args[1]));
            return;
        }
        long max = CurrencyCost.maxAmount(type);
        long amount = DIGITS.matcher(args[2]).matches() ? Long.parseLong(args[2]) : -1;
        if (amount < 1 || amount > max) {
            messages.send(player, "currency.amount", Ph.of("max", Numbers.group(max)));
            return;
        }
        if (!plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.COMMAND, plugin.settings().commandTicks)) {
            return;
        }
        ItemStack token = plugin.currencyToken().create(type, amount);
        if (!player.getInventory().addItem(token).isEmpty()) {
            messages.send(player, "currency.inventory-full");
            return;
        }
        Ph[] placeholders = plugin.currencyToken().placeholders(type, amount);
        messages.send(player, "currency.given", placeholders);
        if (!plugin.currencies().get(type).available()) {
            messages.send(player, "currency.unavailable-warning", placeholders);
        }
    }

    private void delete(CommandSender sender, String id) {
        Messages messages = plugin.messages();
        Shop shop = plugin.shops().get(id);
        if (shop == null) {
            messages.send(sender, "shop.unknown", Ph.of("shop", id));
            return;
        }
        String editing = plugin.editor().lockHolderName(id);
        if (editing != null) {
            messages.send(sender, "editor.locked", Ph.of("shop", shop.styledName()), Ph.of("player", editing));
            return;
        }
        String key = sender instanceof Player player ? player.getUniqueId().toString() : CONSOLE_KEY;
        int now = Bukkit.getCurrentTick();
        PendingDelete pending = pendingDeletes.get(key);
        if (pending == null || !pending.shopId().equals(id) || now - pending.tick() > DELETE_CONFIRM_TICKS) {
            pendingDeletes.put(key, new PendingDelete(id, now));
            messages.send(sender, "editor.delete-confirm", Ph.of("shop", shop.styledName()));
            return;
        }
        pendingDeletes.remove(key);
        plugin.shops().delete(id, plugin.settings());
        plugin.refreshViewers(id);
        messages.send(sender, "editor.deleted", Ph.of("shop", shop.styledName()));
    }

    private void list(CommandSender sender) {
        Messages messages = plugin.messages();
        List<Shop> shops = new ArrayList<>(plugin.shops().all());
        if (shops.isEmpty()) {
            messages.send(sender, "list.empty");
            return;
        }
        messages.sendPlain(sender, "list.header", Ph.of("count", shops.size()));
        for (Shop shop : shops) {
            messages.sendPlain(sender, "list.entry",
                    Ph.of("id", shop.id()),
                    Ph.of("shop", shop.styledName()),
                    Ph.of("trades", shop.trades().size()),
                    Ph.of("permission", messages.line(shop.permissionRequired() ? "list.flag-on" : "list.flag-off")));
        }
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        try {
            return complete(sender, args);
        } catch (Throwable error) {
            plugin.errors().report("Error in tab completion", error);
            return List.of();
        }
    }

    private List<String> complete(CommandSender sender, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        boolean player = sender instanceof Player;
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission(USE)) {
                options.add("help");
                if (player) {
                    options.add("open");
                }
            }
            if (admin) {
                if (!options.contains("open")) {
                    options.add("open");
                }
                if (player) {
                    options.add("admin");
                    options.add("edit");
                    options.add("currency");
                }
                options.addAll(List.of("create", "delete", "list", "reload", UPDATE_MMOITEMS));
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("open") && (sender.hasPermission(USE) || admin)) {
            for (Shop shop : plugin.shops().all()) {
                if (!(sender instanceof Player p) || MainMenuGui.canOpen(p, shop) || admin) {
                    options.add(shop.id());
                }
            }
            return filter(options, args[1]);
        }
        if (args.length == 3 && sub.equals("open") && admin) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
            return filter(options, args[2]);
        }
        if (admin && player && sub.equals("currency") && (args.length == 2 || args.length == 3)) {
            return filter(args.length == 2 ? CurrencyCost.ORDER : TOKEN_AMOUNTS, args[args.length - 1]);
        }
        if (admin && sub.equals(UPDATE_MMOITEMS) && args.length == 2) {
            options.add("all");
            for (Shop shop : plugin.shops().all()) {
                options.add(shop.id());
            }
            return filter(options, args[1]);
        }
        if (admin && sub.equals(UPDATE_MMOITEMS) && args.length == 3) {
            return filter(List.of("preview"), args[2]);
        }
        if (args.length == 2 && admin && (sub.equals("delete") || (player && sub.equals("edit")))) {
            for (Shop shop : plugin.shops().all()) {
                options.add(shop.id());
            }
            return filter(options, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
