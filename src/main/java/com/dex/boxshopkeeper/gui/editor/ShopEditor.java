package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.config.PluginConfig.SoundKey;
import com.dex.boxshopkeeper.gui.GuiItems;
import com.dex.boxshopkeeper.gui.style.ColorCodes;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.item.ItemCodec;
import com.dex.boxshopkeeper.item.ItemNames;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import com.dex.boxshopkeeper.storage.ShopRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor screen 2 (§6.1): the trades of one shop plus shop settings. Shop settings are saved as
 * soon as they change (each change is one editor save); trades are saved from the trade editor.
 * A trade can be picked up (shift + right click) and dropped on another position, on any page,
 * with a single save.
 */
final class ShopEditor {

    static final int ADD_SLOT = 1;
    static final int RENAME_SLOT = 2;
    static final int DESCRIPTION_SLOT = 3;
    static final int ICON_SLOT = 4;
    static final int ORDER_SLOT = 5;
    static final int PERMISSION_SLOT = 6;
    static final int DELETE_SLOT = 7;
    static final int CONFIRM_SLOT = 20;
    static final int TARGET_SLOT = 22;
    static final int DENY_SLOT = 24;
    /** Cap on typed text including colour codes. */
    static final int RAW_TEXT_MAX = 256;

    private final BoxShopkeeperPlugin plugin;
    private final EditorManager manager;

    ShopEditor(BoxShopkeeperPlugin plugin, EditorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    void open(Player player, Shop shop, int page) {
        int clamped = GridLayout.clampPage(page, shop.trades().size());
        EditorHolder holder = new EditorHolder(EditorHolder.Screen.SHOP, shop.id(), null, null, clamped);
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.editor-shop", shop.styledName()));
        EditorSession session = manager.existingSession(player);
        if (session != null) {
            session.movingTradeId = null; // a pick never survives leaving this screen
        }
        render(holder, shop, null);
        player.openInventory(inventory);
    }

    /** @param moving id of the trade picked up for moving, or null */
    private void render(EditorHolder holder, Shop shop, String moving) {
        Inventory inventory = holder.getInventory();
        GuiItems items = plugin.guiItems();
        Messages messages = plugin.messages();
        items.frame(inventory, false);

        Component permission = messages.line(shop.permissionRequired() ? "gui.editor.flag-on" : "gui.editor.flag-off");
        inventory.setItem(ADD_SLOT, items.button(Button.ADD_TRADE,
                messages.line("gui.editor.add-trade.name"), messages.lines("gui.editor.add-trade.lore")));
        inventory.setItem(RENAME_SLOT, items.button(Button.RENAME,
                messages.line("gui.editor.rename.name"),
                messages.lines("gui.editor.rename.lore", Ph.of("shop", shop.styledName()))));
        List<Component> description = new ArrayList<>();
        for (Component line : shop.styledDescription()) {
            description.add(messages.line("gui.editor.description.current", Ph.of("line", line)));
        }
        if (!description.isEmpty()) {
            description.add(Component.empty());
        }
        description.addAll(messages.lines("gui.editor.description.lore"));
        inventory.setItem(DESCRIPTION_SLOT, items.button(Button.DESCRIPTION,
                messages.line("gui.editor.description.name"), description));
        ItemStack icon = GuiItems.withNameAndLore(plugin.mainMenu().icon(shop),
                messages.line("gui.editor.icon.name"), messages.lines("gui.editor.icon.lore"));
        inventory.setItem(ICON_SLOT, icon);
        inventory.setItem(ORDER_SLOT, items.button(Button.MENU_ORDER,
                messages.line("gui.editor.menu-order.name"),
                messages.lines("gui.editor.menu-order.lore", Ph.of("order", shop.menuOrder()))));
        inventory.setItem(PERMISSION_SLOT, items.button(Button.PERMISSION,
                messages.line("gui.editor.permission.name"),
                messages.lines("gui.editor.permission.lore", Ph.of("permission", permission), Ph.of("id", shop.id()))));
        inventory.setItem(DELETE_SLOT, items.button(Button.DELETE,
                messages.line("gui.editor.delete-shop.name"), messages.lines("gui.editor.delete-shop.lore")));

        List<Trade> trades = shop.trades();
        String[] ids = new String[inventory.getSize()];
        if (trades.isEmpty()) {
            inventory.setItem(GridLayout.CENTRE_SLOT, items.button(Button.INFO, "gui.editor.empty-shop"));
        }
        int[] slots = GridLayout.pageSlots(trades.size(), holder.page());
        int first = GridLayout.firstIndex(holder.page());
        for (int i = 0; i < slots.length; i++) {
            Trade trade = trades.get(first + i);
            inventory.setItem(slots[i], entry(trade, moving));
            ids[slots[i]] = trade.id();
        }
        holder.slotIds(ids);
        items.controls(inventory, holder.page(), GridLayout.pages(trades.size()), true);
    }

    private ItemStack entry(Trade trade, String moving) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>();
        lore.addAll(messages.lines("gui.editor.trade-entry.id", Ph.of("id", trade.id())));
        lore.addAll(messages.lines(trade.broken() ? "gui.editor.trade-entry.broken"
                : trade.enabled() ? "gui.editor.trade-entry.enabled" : "gui.editor.trade-entry.disabled"));
        if (!trade.ingredients().isEmpty() || !trade.costs().isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(messages.lines("gui.editor.trade-entry.ingredients-header"));
            for (Ingredient ingredient : trade.ingredients()) {
                lore.addAll(ingredient.broken()
                        ? messages.lines("gui.editor.trade-entry.broken-ingredient", Ph.of("amount", ingredient.amount()))
                        : messages.lines("gui.editor.trade-entry.ingredient",
                                Ph.of("amount", ingredient.amount()), Ph.of("item", ItemNames.name(ingredient.template()))));
            }
            for (CurrencyCost cost : trade.costs()) {
                lore.addAll(messages.lines("gui.editor.trade-entry.currency",
                        plugin.currencyToken().placeholders(cost.type(), cost.amount())));
            }
        }
        ResultSpec result = trade.result();
        if (result != null && !result.broken()) {
            lore.add(Component.empty());
            lore.addAll(messages.lines("gui.editor.trade-entry.result",
                    Ph.of("amount", result.amount()), Ph.of("item", ItemNames.name(result.template()))));
        }
        lore.add(Component.empty());
        boolean picked = trade.id().equals(moving);
        if (moving == null) {
            lore.addAll(messages.lines("gui.editor.trade-entry.actions"));
            lore.addAll(messages.lines("gui.editor.trade-entry.move-hint"));
        } else {
            lore.addAll(messages.lines(picked ? "gui.editor.trade-entry.picked" : "gui.editor.trade-entry.drop-here"));
        }
        ItemStack item = trade.result() == null || trade.result().broken()
                ? plugin.guiItems().button(Button.BROKEN_TRADE, Component.text(trade.id()), lore)
                : GuiItems.withLore(plugin.guiItems().stripped(trade.result().template(), trade.result().amount()), lore);
        if (picked) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(true);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    void click(Player player, EditorHolder holder, int slot, ClickType click) {
        Shop shop = plugin.shops().get(holder.shopId());
        EditorSession session = manager.existingSession(player);
        if (shop == null || session == null || !shop.id().equals(session.shopId)) {
            manager.openList(player, 0);
            return;
        }
        session.shopPage = holder.page();
        String tradeId = holder.idAt(slot);
        if (tradeId != null) {
            if (session.movingTradeId != null) {
                clickWhileMoving(player, holder, shop, session, tradeId, click);
            } else {
                clickTrade(player, holder, shop, session, tradeId, click);
            }
            return;
        }
        if (session.movingTradeId != null && click == ClickType.LEFT && !GridLayout.isBorder(slot)) {
            // An empty slot after the last trade: move to the end.
            drop(player, shop, session, -1);
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (slot == ICON_SLOT && !cursor.isEmpty() && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
            String encoded = ItemCodec.encode(cursor);
            if (ItemCodec.byteSize(encoded) > plugin.settings().limits.templateBytes()) {
                plugin.messages().send(player, "editor.item-too-large");
                return;
            }
            ItemStack template = cursor.clone();
            template.setAmount(1);
            save(player, shop.toBuilder().icon(template, encoded), true);
            return;
        }
        if (click != ClickType.LEFT) {
            return;
        }
        switch (slot) {
            case ADD_SLOT -> addTrade(player, shop);
            case RENAME_SLOT -> promptName(player);
            case DESCRIPTION_SLOT -> promptDescription(player);
            case ORDER_SLOT -> promptOrder(player);
            case PERMISSION_SLOT -> save(player, shop.toBuilder().permissionRequired(!shop.permissionRequired()), true);
            case DELETE_SLOT -> openConfirm(player, shop, EditorHolder.Confirm.DELETE_SHOP, null);
            default -> {
                if (GuiItems.isControl(slot, GridLayout.BACK_COLUMN)) {
                    plugin.settings().sound(SoundKey.CLICK).play(player);
                    manager.openList(player, session.listPage);
                    return;
                }
                int delta = GuiItems.isControl(slot, GridLayout.PREVIOUS_COLUMN) ? -1
                        : GuiItems.isControl(slot, GridLayout.NEXT_COLUMN) ? 1 : 0;
                int target = holder.page() + delta;
                if (delta != 0 && target >= 0 && target < GridLayout.pages(shop.trades().size())) {
                    plugin.settings().sound(SoundKey.CLICK).play(player);
                    session.shopPage = target;
                    holder.page(target);
                    render(holder, shop, session.movingTradeId);
                }
            }
        }
    }

    private void clickTrade(Player player, EditorHolder holder, Shop shop, EditorSession session,
                            String tradeId, ClickType click) {
        Trade trade = shop.trade(tradeId);
        if (trade == null) {
            return;
        }
        int index = shop.trades().indexOf(trade);
        switch (click) {
            case LEFT -> {
                if (trade.broken()) {
                    plugin.messages().send(player, "editor.broken-trade");
                    return;
                }
                plugin.settings().sound(SoundKey.CLICK).play(player);
                TradeDraft draft = TradeDraft.of(trade);
                manager.session(player).draft = draft;
                manager.tradeEditor().open(player, shop, draft, index);
            }
            case SHIFT_LEFT -> move(player, shop, index, index - 1);
            case RIGHT -> move(player, shop, index, index + 1);
            case DROP, CONTROL_DROP -> openConfirm(player, shop, EditorHolder.Confirm.DELETE_TRADE, tradeId);
            case SHIFT_RIGHT -> {
                session.movingTradeId = tradeId;
                plugin.settings().sound(SoundKey.CLICK).play(player);
                plugin.messages().send(player, "editor.move-picked", Ph.of("id", tradeId));
                render(holder, shop, tradeId);
            }
            default -> {
            }
        }
    }

    /** While a trade is picked up: left click on another trade drops it there, on itself cancels. */
    private void clickWhileMoving(Player player, EditorHolder holder, Shop shop, EditorSession session,
                                  String tradeId, ClickType click) {
        if (tradeId.equals(session.movingTradeId)) {
            if (click == ClickType.LEFT || click == ClickType.SHIFT_RIGHT) {
                session.movingTradeId = null;
                plugin.settings().sound(SoundKey.CLICK).play(player);
                plugin.messages().send(player, "editor.move-cancelled");
                render(holder, shop, null);
            }
            return;
        }
        if (click != ClickType.LEFT) {
            return;
        }
        Trade target = shop.trade(tradeId);
        drop(player, shop, session, target == null ? -1 : shop.trades().indexOf(target));
    }

    /**
     * Moves the picked trade so that it ends at {@code to} (the target's current index; the trades
     * from there on shift by one), or to the end when {@code to < 0}. One save, one backup.
     */
    private void drop(Player player, Shop shop, EditorSession session, int to) {
        Trade moving = shop.trade(session.movingTradeId);
        session.movingTradeId = null;
        if (moving == null) {
            manager.reopenShop(player);
            return;
        }
        List<Trade> trades = new ArrayList<>(shop.trades());
        int from = trades.indexOf(moving);
        int index = reorder(trades, from, to);
        session.shopPage = index / GridLayout.PER_PAGE;
        if (index == from) {
            plugin.settings().sound(SoundKey.CLICK).play(player);
            manager.reopenShop(player);
            return;
        }
        if (save(player, shop.toBuilder().trades(trades), false)) {
            plugin.messages().send(player, "editor.moved", Ph.of("id", moving.id()), Ph.of("position", index + 1));
        }
    }

    /**
     * Moves {@code list[from]} so that it ends at index {@code to}, or last when {@code to} is
     * negative or past the end; the entries in between shift by one.
     * @return the final index of the moved entry
     */
    static <T> int reorder(List<T> list, int from, int to) {
        T entry = list.remove(from);
        int index = to < 0 || to > list.size() ? list.size() : to;
        list.add(index, entry);
        return index;
    }

    private void addTrade(Player player, Shop shop) {
        int max = plugin.settings().limits.tradesPerShop();
        if (shop.trades().size() >= max) {
            plugin.messages().send(player, "editor.limit",
                    Ph.of("limit", plugin.messages().line("editor.limits.trades", Ph.of("max", max))));
            return;
        }
        plugin.settings().sound(SoundKey.CLICK).play(player);
        List<String> ids = shop.trades().stream().map(Trade::id).toList();
        TradeDraft draft = TradeDraft.create(ShopRepository.freeTradeId(ids));
        manager.session(player).draft = draft;
        manager.tradeEditor().open(player, shop, draft, shop.trades().size());
    }

    private void move(Player player, Shop shop, int from, int to) {
        List<Trade> trades = new ArrayList<>(shop.trades());
        if (to < 0 || to >= trades.size()) {
            return;
        }
        trades.add(to, trades.remove(from));
        int page = to / GridLayout.PER_PAGE;
        manager.session(player).shopPage = page;
        save(player, shop.toBuilder().trades(trades), false);
    }

    /** Publishes the shop (the file is written in the background) and reopens this editor. */
    boolean save(Player player, Shop.Builder builder, boolean announce) {
        Shop saved = plugin.shops().save(builder, plugin.settings());
        plugin.refreshViewers(saved.id());
        if (announce) {
            plugin.messages().send(player, "editor.saved", Ph.of("shop", saved.styledName()));
        }
        plugin.settings().sound(SoundKey.CLICK).play(player);
        manager.reopenShop(player);
        return true;
    }

    // ---------------------------------------------------------------- prompts

    private void promptName(Player player) {
        String shopId = sessionShopId(player);
        int max = plugin.settings().limits.shopNameChars();
        manager.prompts().start(player, "editor.what.name",
                (admin, input) -> {
                    if (input.isEmpty()) {
                        return false;
                    }
                    if (ColorCodes.visibleLength(input) > max || input.length() > RAW_TEXT_MAX) {
                        limit(admin, "editor.limits.name", max);
                        return false;
                    }
                    Shop current = currentShop(admin, shopId);
                    if (current != null) {
                        save(admin, current.toBuilder().displayName(input), true);
                    }
                    return true;
                },
                manager::reopenShop,
                id -> manager.end(id, null));
        player.closeInventory();
    }

    private void promptDescription(Player player) {
        String shopId = sessionShopId(player);
        PluginConfig.Limits limits = plugin.settings().limits;
        List<String> lines = new ArrayList<>();
        manager.prompts().start(player, "editor.what.description",
                (admin, input) -> {
                    if (input.equalsIgnoreCase("done")) {
                        Shop current = currentShop(admin, shopId);
                        if (current != null) {
                            save(admin, current.toBuilder().description(lines), true);
                        }
                        return true;
                    }
                    if (input.isEmpty()) {
                        return false;
                    }
                    if (lines.size() >= limits.descriptionLines()) {
                        limit(admin, "editor.limits.description-lines", limits.descriptionLines());
                        return false;
                    }
                    if (ColorCodes.visibleLength(input) > limits.descriptionLineChars() || input.length() > RAW_TEXT_MAX) {
                        limit(admin, "editor.limits.description-chars", limits.descriptionLineChars());
                        return false;
                    }
                    lines.add(input);
                    plugin.messages().send(admin, "editor.description-line", Ph.of("line", ColorCodes.parse(input)));
                    return false;
                },
                manager::reopenShop,
                id -> manager.end(id, null));
        player.closeInventory();
    }

    private void promptOrder(Player player) {
        String shopId = sessionShopId(player);
        manager.prompts().start(player, "editor.what.order",
                (admin, input) -> {
                    int order;
                    try {
                        order = Integer.parseInt(input);
                    } catch (NumberFormatException e) {
                        plugin.messages().send(admin, "editor.invalid-number");
                        return false;
                    }
                    Shop current = currentShop(admin, shopId);
                    if (current != null) {
                        save(admin, current.toBuilder().menuOrder(order), true);
                    }
                    return true;
                },
                manager::reopenShop,
                id -> manager.end(id, null));
        player.closeInventory();
    }

    private String sessionShopId(Player player) {
        EditorSession session = manager.existingSession(player);
        return session == null ? null : session.shopId;
    }

    /**
     * The shop a prompt was opened for, or null when the admin no longer edits that shop (another
     * {@code /chetao edit} or the shop list in between): the input is then dropped, never applied
     * to whatever shop the session holds now.
     */
    private Shop currentShop(Player player, String shopId) {
        EditorSession session = manager.existingSession(player);
        Shop shop = shopId == null || session == null || !shopId.equals(session.shopId)
                ? null : plugin.shops().get(shopId);
        if (shop == null) {
            plugin.messages().send(player, "editor.prompt-stale");
            if (session == null || session.shopId == null) {
                manager.openList(player, session == null ? 0 : session.listPage);
            }
            // Otherwise the admin is already in another shop's editor: leave it open.
        }
        return shop;
    }

    private void limit(Player player, String labelKey, int max) {
        plugin.messages().send(player, "editor.limit",
                Ph.of("limit", plugin.messages().line(labelKey, Ph.of("max", max))));
    }

    // ---------------------------------------------------------------- confirm

    void openConfirm(Player player, Shop shop, EditorHolder.Confirm action, String tradeId) {
        plugin.settings().sound(SoundKey.CLICK).play(player);
        EditorHolder holder = new EditorHolder(EditorHolder.Screen.CONFIRM, shop.id(), tradeId, action, 0);
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.editor-confirm", shop.styledName()));
        GuiItems items = plugin.guiItems();
        Messages messages = plugin.messages();
        items.frame(inventory, true);
        Component target = action == EditorHolder.Confirm.DELETE_SHOP ? shop.styledName() : Component.text(tradeId);
        Trade trade = tradeId == null ? null : shop.trade(tradeId);
        ItemStack targetIcon = action == EditorHolder.Confirm.DELETE_SHOP ? plugin.mainMenu().icon(shop)
                : trade != null && trade.result() != null && !trade.result().broken()
                ? items.stripped(trade.result().template(), trade.result().amount())
                : new ItemStack(plugin.settings().material(Button.BROKEN_TRADE));
        inventory.setItem(TARGET_SLOT, GuiItems.withNameAndLore(targetIcon,
                messages.line("gui.editor.confirm.target", Ph.of("target", target)), List.of()));
        inventory.setItem(CONFIRM_SLOT, items.button(Button.CONFIRM,
                messages.line("gui.editor.confirm.name", Ph.of("target", target)),
                messages.lines("gui.editor.confirm.lore")));
        inventory.setItem(DENY_SLOT, items.button(Button.DENY, "gui.editor.deny.name"));
        player.openInventory(inventory);
    }

    void clickConfirm(Player player, EditorHolder holder, int slot, ClickType click) {
        if (click != ClickType.LEFT) {
            return;
        }
        Shop shop = plugin.shops().get(holder.shopId());
        EditorSession session = manager.existingSession(player);
        if (shop == null || session == null || !shop.id().equals(session.shopId)) {
            manager.openList(player, 0);
            return;
        }
        if (slot == DENY_SLOT) {
            plugin.settings().sound(SoundKey.CLICK).play(player);
            manager.reopenShop(player);
            return;
        }
        if (slot != CONFIRM_SLOT) {
            return;
        }
        if (holder.confirm() == EditorHolder.Confirm.DELETE_SHOP) {
            plugin.shops().delete(shop.id(), plugin.settings());
            plugin.refreshViewers(shop.id());
            plugin.messages().send(player, "editor.deleted", Ph.of("shop", shop.styledName()));
            manager.openList(player, session.listPage);
            return;
        }
        List<Trade> trades = new ArrayList<>(shop.trades());
        trades.removeIf(trade -> trade.id().equals(holder.tradeId()));
        save(player, shop.toBuilder().trades(trades), true);
    }
}
