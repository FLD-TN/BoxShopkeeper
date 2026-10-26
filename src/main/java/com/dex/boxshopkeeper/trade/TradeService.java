package com.dex.boxshopkeeper.trade;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.currency.CurrencyBalances;
import com.dex.boxshopkeeper.currency.CurrencyProvider;
import com.dex.boxshopkeeper.currency.CurrencyRegistry;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.item.ItemMatcher;
import com.dex.boxshopkeeper.item.ItemNames;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import com.dex.boxshopkeeper.source.IngredientSource;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import com.dex.boxshopkeeper.util.Numbers;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/** Counting, feasibility (§5.4) and the atomic transaction (§5.5). Main thread only. */
public final class TradeService {

    public enum Mode { ONCE, ALL }

    public enum Outcome {
        SUCCESS, NOT_ENOUGH, INVENTORY_FULL, GAMEMODE, STATE, DISABLED, CHANGED, ERROR,
        CURRENCY_UNAVAILABLE, CURRENCY_FAILED
    }

    /**
     * Per-ingredient counts from the source, indexed like {@code trade.ingredients()}, and per-cost
     * balances, indexed like {@code trade.costs()}.
     */
    public record Status(long[] counts, boolean[] enough, boolean allEnough,
                         boolean[] costAvailable, long[] costBalance, boolean[] costEnough) {
    }

    private final BoxShopkeeperPlugin plugin;
    private final IngredientSource source;

    public TradeService(BoxShopkeeperPlugin plugin, IngredientSource source) {
        this.plugin = plugin;
        this.source = source;
    }

    /** @param balances snapshot built once per render for this player */
    public Status status(Player player, Trade trade, CurrencyBalances balances) {
        List<Ingredient> ingredients = trade.ingredients();
        long[] counts = new long[ingredients.size()];
        boolean[] enough = new boolean[ingredients.size()];
        boolean all = !ingredients.isEmpty() || !trade.costs().isEmpty();
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            counts[i] = source.count(player, ingredient);
            enough[i] = counts[i] >= ingredient.amount();
            all &= enough[i];
        }
        List<CurrencyCost> costs = trade.costs();
        boolean[] costAvailable = new boolean[costs.size()];
        long[] costBalance = new long[costs.size()];
        boolean[] costEnough = new boolean[costs.size()];
        for (int i = 0; i < costs.size(); i++) {
            long balance = balances.balance(costs.get(i).type());
            costAvailable[i] = balance != CurrencyBalances.UNAVAILABLE;
            costBalance[i] = Math.max(0, balance);
            costEnough[i] = costAvailable[i] && balance >= costs.get(i).amount();
            all &= costEnough[i];
        }
        return new Status(counts, enough, all, costAvailable, costBalance, costEnough);
    }

    /** Largest number of trades the player can do right now (0 if none). */
    public int maxFeasible(Player player, Trade trade, CurrencyBalances balances) {
        int cap = TradeBounds.max(ingredientBound(player, trade), costBound(trade, balances),
                plugin.settings().maxPerClick);
        if (cap <= 0) {
            return 0;
        }
        return simulate(player, trade).maxFeasible(cap);
    }

    /**
     * Performs {@code 1} or {@code maxFeasible()} trades atomically, in this tick.
     * Messages and sounds are sent here; the caller refreshes or closes the GUI based on the outcome.
     */
    public Outcome execute(Player player, String shopId, long revision, String tradeId, Mode mode) {
        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
            return refuse(player, "trade.gamemode", Outcome.GAMEMODE);
        }
        if (player.isDead() || player.isSleeping() || !player.isOnline()) {
            return refuse(player, "trade.state", Outcome.STATE);
        }

        // 1. Re-read the current definition.
        Shop shop = plugin.shops().get(shopId);
        if (shop == null) {
            return refuse(player, "trade.disabled", Outcome.DISABLED);
        }
        if (shop.revision() != revision) {
            return refuse(player, "trade.changed", Outcome.CHANGED);
        }
        Trade trade = shop.trade(tradeId);
        if (trade == null || !trade.usable()) {
            return refuse(player, "trade.disabled", Outcome.DISABLED);
        }

        // 2. Every currency must be available and ready for this player.
        CurrencyRegistry currencies = plugin.currencies();
        for (CurrencyCost cost : trade.costs()) {
            CurrencyProvider provider = currencies.get(cost.type());
            if (provider == null || !provider.available() || !provider.ready(player)) {
                return refuse(player, "trade.currency-unavailable", Outcome.CURRENCY_UNAVAILABLE, currency(cost.type()));
            }
        }

        // 3. Decide n, with fresh balances.
        long itemBound = ingredientBound(player, trade);
        if (itemBound < 1) {
            return refuse(player, "trade.not-enough", Outcome.NOT_ENOUGH);
        }
        CurrencyBalances balances = CurrencyBalances.of(player, currencies);
        for (CurrencyCost cost : trade.costs()) {
            long balance = balances.balance(cost.type());
            if (balance == CurrencyBalances.UNAVAILABLE) {
                return refuse(player, "trade.currency-unavailable", Outcome.CURRENCY_UNAVAILABLE, currency(cost.type()));
            }
            if (balance < cost.amount()) {
                return refuse(player, "trade.not-enough-currency", Outcome.NOT_ENOUGH, currency(cost.type()),
                        Ph.of("need", plugin.currencyToken().costName(cost.type(), cost.amount())),
                        Ph.of("have", plugin.currencyToken().costName(cost.type(), balance)));
            }
        }
        long bound = Math.min(itemBound, costBound(trade, balances));
        InventorySimulator simulator = simulate(player, trade);
        int n;
        if (mode == Mode.ONCE) {
            if (!simulator.feasible(1)) {
                return refuse(player, "trade.inventory-full", Outcome.INVENTORY_FULL);
            }
            n = 1;
        } else {
            n = simulator.maxFeasible((int) Math.min(bound, plugin.settings().maxPerClick));
            if (n == 0) {
                return refuse(player, "trade.inventory-full", Outcome.INVENTORY_FULL);
            }
        }

        // 4. Withdraw currencies first: the only step that can fail for reasons outside this plugin.
        List<RemovalReceipt> receipts = new ArrayList<>();
        List<CurrencyPayment.Charge> charges = new ArrayList<>(trade.costs().size());
        for (CurrencyCost cost : trade.costs()) {
            charges.add(new CurrencyPayment.Charge(cost.type(), currencies.get(cost.type()),
                    Math.multiplyExact((long) n, cost.amount())));
        }
        try {
            CurrencyPayment.withdraw(player, charges, "shopkeeper:" + shopId + "/" + tradeId, receipts);
        } catch (CurrencyPayment.FailedException e) {
            CurrencyPayment.Charge failed = e.charge();
            plugin.getLogger().warning("Currency withdrawal failed: player=" + player.getName() + " shop=" + shopId
                    + " trade=" + tradeId + " type=" + failed.type() + " amount=" + failed.amount()
                    + ": " + e.getCause());
            return refuse(player, "trade.currency-failed", Outcome.CURRENCY_FAILED, currency(failed.type()));
        }

        // 5. Remove ingredients.
        try {
            for (Ingredient ingredient : trade.ingredients()) {
                receipts.add(source.remove(player, ingredient, (long) n * ingredient.amount()));
            }
        } catch (RuntimeException e) {
            CurrencyPayment.rollback(receipts);
            plugin.errors().report("Trade removal failed for " + player.getName() + " in " + shopId + "/" + tradeId, e);
            return refuse(player, "command.error", Outcome.ERROR);
        }

        // 6. Add results; by construction they fit.
        ResultSpec result = trade.result();
        long total = (long) n * result.amount();
        Placement placement = new Placement(player.getInventory(), new ArrayList<>());
        boolean placed;
        try {
            placed = place(placement, result.template(), total);
        } catch (RuntimeException e) {
            placement.rollback();
            CurrencyPayment.rollback(receipts);
            plugin.errors().report("Result placement failed for " + player.getName() + " in " + shopId + "/" + tradeId
                    + "; transaction rolled back", e);
            return refuse(player, "command.error", Outcome.ERROR);
        }
        if (!placed) {
            placement.rollback();
            CurrencyPayment.rollback(receipts);
            plugin.getLogger().severe("Result did not fit after a feasible check: player=" + player.getName()
                    + " shop=" + shopId + " trade=" + tradeId + " n=" + n + "; transaction rolled back");
            return refuse(player, "command.error", Outcome.ERROR);
        }

        // 7. Feedback and log. The trade is done: a failure here must never look like a failed trade.
        try {
            plugin.settings().sound(PluginConfig.SoundKey.SUCCESS).play(player);
            plugin.messages().sendAlways(player, "trade.success",
                    Ph.of("item", ItemNames.name(result.template())),
                    Ph.of("amount", total),
                    Ph.of("times", n));
        } catch (RuntimeException e) {
            plugin.errors().report("Trade feedback failed for " + player.getName() + " in " + shopId + "/" + tradeId, e);
        }
        try {
            plugin.tradeLogger().log(logLine(player, shop, trade, n, total));
        } catch (RuntimeException e) {
            plugin.errors().report("Trade log failed for " + player.getName() + " in " + shopId + "/" + tradeId, e);
        }
        return Outcome.SUCCESS;
    }

    private Outcome refuse(Player player, String key, Outcome outcome, Ph... placeholders) {
        plugin.settings().sound(PluginConfig.SoundKey.REFUSED).play(player);
        plugin.messages().send(player, key, placeholders);
        return outcome;
    }

    private Ph currency(String type) {
        return Ph.of("currency", plugin.currencies().displayName(type));
    }

    /** {@code n_ing}; unbounded when the trade has no item ingredient. */
    private long ingredientBound(Player player, Trade trade) {
        List<Ingredient> ingredients = trade.ingredients();
        long[] counts = new long[ingredients.size()];
        long[] amounts = new long[ingredients.size()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = source.count(player, ingredients.get(i));
            amounts[i] = ingredients.get(i).amount();
        }
        return TradeBounds.items(counts, amounts);
    }

    /** {@code n_cost}; unbounded without cost, 0 when a currency is not available or not ready. */
    private static long costBound(Trade trade, CurrencyBalances balances) {
        List<CurrencyCost> costs = trade.costs();
        long[] values = new long[costs.size()];
        long[] amounts = new long[costs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = balances.balance(costs.get(i).type());
            amounts[i] = costs.get(i).amount();
        }
        return TradeBounds.costs(values, amounts);
    }

    private InventorySimulator simulate(Player player, Trade trade) {
        PlayerInventory inventory = player.getInventory();
        List<Ingredient> ingredients = trade.ingredients();
        ItemStack resultTemplate = trade.result().template();
        int[] amounts = new int[InventorySimulator.SLOTS];
        int[] ingredientOf = new int[InventorySimulator.SLOTS];
        boolean[] resultSimilar = new boolean[InventorySimulator.SLOTS];
        Arrays.fill(ingredientOf, -1);
        for (int slot = 0; slot < InventorySimulator.SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            amounts[slot] = item.getAmount();
            for (int i = 0; i < ingredients.size(); i++) {
                if (ItemMatcher.matches(ingredients.get(i).template(), item)) {
                    ingredientOf[slot] = i;
                    break;
                }
            }
            resultSimilar[slot] = ItemMatcher.matches(resultTemplate, item);
        }
        long[] perTrade = new long[ingredients.size()];
        for (int i = 0; i < perTrade.length; i++) {
            perTrade[i] = ingredients.get(i).amount();
        }
        return new InventorySimulator(amounts, ingredientOf, resultSimilar, perTrade,
                trade.result().amount(), trade.result().maxStack());
    }

    /**
     * Fills partial result stacks, then empty slots, in slot order (the simulator's model). Every
     * change is recorded in {@code placement} before it is made, so a throw midway can be rolled back.
     * @return whether everything was placed
     */
    private static boolean place(Placement placement, ItemStack template, long total) {
        PlayerInventory inventory = placement.inventory();
        List<Placement.Change> changes = placement.changes();
        int max = Math.max(1, template.getMaxStackSize());
        long remaining = total;
        for (int slot = 0; slot < InventorySimulator.SLOTS && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (ItemMatcher.matches(template, item) && item.getAmount() < max) {
                int add = (int) Math.min(max - item.getAmount(), remaining);
                changes.add(new Placement.Change(slot, item.clone()));
                ItemStack grown = item.clone();
                grown.setAmount(item.getAmount() + add);
                inventory.setItem(slot, grown);
                remaining -= add;
            }
        }
        for (int slot = 0; slot < InventorySimulator.SLOTS && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.isEmpty()) {
                int put = (int) Math.min(max, remaining);
                ItemStack stack = template.clone();
                stack.setAmount(put);
                changes.add(new Placement.Change(slot, null));
                inventory.setItem(slot, stack);
                remaining -= put;
            }
        }
        return remaining == 0;
    }

    private record Placement(PlayerInventory inventory, List<Change> changes) {
        record Change(int slot, ItemStack before) {
        }

        void rollback() {
            for (int i = changes.size() - 1; i >= 0; i--) {
                inventory.setItem(changes.get(i).slot(), changes.get(i).before());
            }
        }
    }

    private static String logLine(Player player, Shop shop, Trade trade, int n, long total) {
        StringJoiner removed = new StringJoiner(", ", "[", "]");
        for (Ingredient ingredient : trade.ingredients()) {
            removed.add(ItemNames.describe(ingredient.template()) + " x" + ((long) n * ingredient.amount()));
        }
        StringJoiner paid = new StringJoiner(", ", "[", "]");
        for (CurrencyCost cost : trade.costs()) {
            paid.add(cost.type() + " " + ((long) n * cost.amount()));
        }
        String gave = "[" + ItemNames.describe(trade.result().template()) + " x" + total + "]";
        return TradeLogger.timestamp()
                + " | player=" + player.getName() + " uuid=" + player.getUniqueId()
                + " | shop=" + shop.id() + " trade=" + trade.id()
                + " | n=" + n
                + " | removed=" + removed
                + " | paid=" + paid
                + " | gave=" + gave;
    }
}
