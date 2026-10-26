package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.config.PluginConfig.SoundKey;
import com.dex.boxshopkeeper.currency.CurrencyBalances;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import com.dex.boxshopkeeper.trade.TradeService;
import com.dex.boxshopkeeper.trade.TradeService.Mode;
import com.dex.boxshopkeeper.trade.TradeService.Outcome;
import com.dex.boxshopkeeper.util.Numbers;
import com.dex.boxshopkeeper.util.RateLimiter.Action;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Screen [3]: one trade in full; ingredients as a 3 × 3 grid like a crafting table. */
public final class TradeDetailGui {

    private final BoxShopkeeperPlugin plugin;

    public TradeDetailGui(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Shop shop, Trade trade, int returnPage) {
        TradeDetailHolder holder = new TradeDetailHolder(shop.id(), trade.id(), returnPage);
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.trade-detail", shop.styledName()));
        render(player, holder, shop, trade);
        player.openInventory(inventory);
    }

    /** Re-renders in place; falls back to the shop view when the trade is gone or disabled. */
    public void refresh(Player player, TradeDetailHolder holder) {
        Shop shop = plugin.shops().get(holder.shopId());
        if (shop == null || !MainMenuGui.canOpen(player, shop)) {
            // Called from click handlers too: closing inside InventoryClickEvent is unsafe, so next tick.
            plugin.closeLater(player);
            return;
        }
        Trade trade = shop.trade(holder.tradeId());
        if (trade == null || !trade.usable()) {
            plugin.shopView().open(player, shop, holder.returnPage());
            return;
        }
        render(player, holder, shop, trade);
    }

    private void render(Player player, TradeDetailHolder holder, Shop shop, Trade trade) {
        Inventory inventory = holder.getInventory();
        Messages messages = plugin.messages();
        GuiItems items = plugin.guiItems();
        items.background(inventory, Button.DETAIL_BACKGROUND);
        inventory.setItem(TradeDetailHolder.MARKER_SLOT, items.button(Button.RESULT_MARKER,
                messages.line("gui.trade-detail.result-marker.name"), messages.lines("gui.trade-detail.result-marker.lore")));

        ResultSpec result = trade.result();
        ItemStack shown = result.template().clone();
        shown.setAmount(Math.min(result.amount(), result.maxStack()));
        inventory.setItem(TradeDetailHolder.RESULT_SLOT, shown);

        CurrencyBalances balances = CurrencyBalances.of(player, plugin.currencies());
        TradeService.Status status = plugin.tradeService().status(player, trade, balances);
        List<ItemStack> stacks = new ArrayList<>();
        List<Ingredient> ingredients = trade.ingredients();
        List<CurrencyCost> costs = trade.costs();
        // Cost slots are reserved first so a cost is never hidden.
        int itemCapacity = Math.max(0, TradeDetailHolder.INGREDIENT_CAPACITY - costs.size());
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            List<Component> block = new ArrayList<>(messages.lines("gui.trade-detail.need",
                    Ph.of("amount", ingredient.amount())));
            block.addAll(messages.lines(status.enough()[i] ? "gui.trade-detail.have-enough" : "gui.trade-detail.have-missing",
                    Ph.of("have", status.counts()[i])));
            int max = ingredient.maxStack();
            int remaining = ingredient.amount();
            while (remaining > 0 && stacks.size() < itemCapacity) {
                int chunk = Math.min(max, remaining);
                stacks.add(ingredientStack(ingredient.template(), chunk, block));
                remaining -= chunk;
            }
        }
        for (int i = 0; i < costs.size() && stacks.size() < TradeDetailHolder.INGREDIENT_CAPACITY; i++) {
            stacks.add(costStack(costs.get(i), status.costAvailable()[i], status.costEnough()[i], status.costBalance()[i]));
        }
        for (int i = 0; i < GridLayout.GRID.length; i++) {
            inventory.setItem(GridLayout.GRID[i], i < stacks.size() ? stacks.get(i) : null);
        }

        inventory.setItem(TradeDetailHolder.BACK_SLOT, items.button(Button.BACK, "gui.controls.back"));
        int max = plugin.tradeService().maxFeasible(player, trade, balances);
        List<Component> lore = new ArrayList<>();
        lore.addAll(messages.lines(status.allEnough() ? "gui.trade-button.enough" : "gui.trade-button.missing"));
        if (result.amount() > result.maxStack()) {
            lore.addAll(messages.lines("gui.trade-button.receive", Ph.of("amount", result.amount())));
        }
        lore.add(Component.empty());
        lore.addAll(messages.lines("gui.trade-button.once"));
        lore.addAll(max > 0
                ? messages.lines("gui.trade-button.all-count", Ph.of("max", max))
                : messages.lines("gui.trade-button.all"));
        inventory.setItem(TradeDetailHolder.TRADE_SLOT,
                items.button(Button.TRADE, messages.line("gui.trade-button.name"), lore));
        holder.revision(shop.revision());
    }

    /**
     * The token look without PDC keys, named with %cost%, with the same need/have lines as an
     * item ingredient ({@code %amount%} is the %cost% component, {@code %have%} the balance as a %cost%).
     */
    private ItemStack costStack(CurrencyCost cost, boolean available, boolean enough, long balance) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>();
        Component name = plugin.currencyToken().costName(cost.type(), cost.amount());
        lore.addAll(messages.lines("gui.trade-detail.need", Ph.of("amount", name)));
        if (!available) {
            lore.addAll(messages.lines("gui.trade-detail.currency-unavailable"));
        } else {
            lore.addAll(messages.lines(enough ? "gui.trade-detail.have-enough" : "gui.trade-detail.have-missing",
                    Ph.of("have", plugin.currencyToken().costName(cost.type(), balance))));
        }
        return GuiItems.withNameAndLore(plugin.currencyToken().look(cost.type()),
                name, lore);
    }

    private static ItemStack ingredientStack(ItemStack template, int amount, List<Component> block) {
        ItemStack stack = template.clone();
        stack.setAmount(amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            List<Component> own = meta.lore();
            if (own != null && !own.isEmpty()) {
                lore.addAll(own);
                lore.add(Component.empty());
            }
            lore.addAll(block);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void click(Player player, TradeDetailHolder holder, int slot, ClickType click) {
        Shop shop = plugin.shops().get(holder.shopId());
        if (shop == null || !MainMenuGui.canOpen(player, shop)) {
            plugin.closeLater(player);
            return;
        }
        if (slot == TradeDetailHolder.BACK_SLOT && click == ClickType.LEFT) {
            if (plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.NAVIGATION, plugin.settings().navigationTicks)) {
                plugin.settings().sound(SoundKey.CLICK).play(player);
                plugin.shopView().open(player, shop, holder.returnPage());
            }
            return;
        }
        if (slot != TradeDetailHolder.TRADE_SLOT) {
            return;
        }
        // Same keys as the shop view: left once, shift + left all (a plain right click never trades).
        Mode mode = click == ClickType.LEFT ? Mode.ONCE : click == ClickType.SHIFT_LEFT ? Mode.ALL : null;
        if (mode == null
                || !plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.TRADE, plugin.settings().cooldownTicks)) {
            return;
        }
        Outcome outcome = plugin.tradeService().execute(player, holder.shopId(), holder.revision(), holder.tradeId(), mode);
        switch (outcome) {
            case SUCCESS, CHANGED, DISABLED, CURRENCY_UNAVAILABLE, CURRENCY_FAILED -> refresh(player, holder);
            case STATE, ERROR -> plugin.closeLater(player);
            default -> {
            }
        }
    }
}
