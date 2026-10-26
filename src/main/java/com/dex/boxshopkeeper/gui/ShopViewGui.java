package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.config.PluginConfig.SoundKey;
import com.dex.boxshopkeeper.currency.CurrencyBalances;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.item.ItemNames;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import com.dex.boxshopkeeper.trade.TradeService;
import com.dex.boxshopkeeper.trade.TradeService.Mode;
import com.dex.boxshopkeeper.trade.TradeService.Outcome;
import com.dex.boxshopkeeper.util.RateLimiter.Action;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Screen [2] (§5.3): every usable trade of one shop, compact. */
public final class ShopViewGui {

    private final BoxShopkeeperPlugin plugin;

    public ShopViewGui(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Shop shop, int page) {
        List<Trade> trades = shop.usableTrades();
        ShopViewHolder holder = new ShopViewHolder(shop.id(), GridLayout.clampPage(page, trades.size()));
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.shop-view", shop.styledName()));
        render(player, holder, shop);
        player.openInventory(inventory);
    }

    /**
     * Brings an open view up to date with the current shop definition, in place; closes it when
     * the shop is gone or no longer allowed.
     */
    public void refresh(Player player, ShopViewHolder holder) {
        Shop shop = plugin.shops().get(holder.shopId());
        if (shop == null || !MainMenuGui.canOpen(player, shop)) {
            // Called from click handlers too: closing inside InventoryClickEvent is unsafe, so next tick.
            plugin.closeLater(player);
            return;
        }
        holder.page(GridLayout.clampPage(holder.page(), shop.usableTrades().size()));
        render(player, holder, shop);
    }

    private void render(Player player, ShopViewHolder holder, Shop shop) {
        Inventory inventory = holder.getInventory();
        GuiItems items = plugin.guiItems();
        List<Trade> trades = shop.usableTrades();
        items.frame(inventory, false);
        String[] ids = new String[inventory.getSize()];
        if (trades.isEmpty()) {
            inventory.setItem(GridLayout.CENTRE_SLOT, items.button(Button.INFO, "shop.empty"));
        }
        int[] slots = GridLayout.pageSlots(trades.size(), holder.page());
        int first = GridLayout.firstIndex(holder.page());
        CurrencyBalances balances = CurrencyBalances.of(player, plugin.currencies());
        for (int i = 0; i < slots.length; i++) {
            Trade trade = trades.get(first + i);
            inventory.setItem(slots[i], entry(player, trade, balances));
            ids[slots[i]] = trade.id();
        }
        holder.slotTradeIds(ids);
        holder.revision(shop.revision());
        // No Back button: players reach a shop directly (NPC / command), never through the main menu.
        items.controls(inventory, holder.page(), GridLayout.pages(trades.size()), false);
    }

    private ItemStack entry(Player player, Trade trade, CurrencyBalances balances) {
        if (trade.iconBase() == null) {
            trade.iconBase(plugin.guiItems().stripped(trade.result().template(), trade.result().amount()));
        }
        Messages messages = plugin.messages();
        TradeService.Status status = plugin.tradeService().status(player, trade, balances);
        List<Component> lore = new ArrayList<>();
        lore.addAll(messages.lines("gui.shop-view.header"));
        List<Ingredient> ingredients = trade.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            lore.addAll(messages.lines(status.enough()[i] ? "gui.shop-view.enough" : "gui.shop-view.missing",
                    Ph.of("amount", ingredient.amount()),
                    Ph.of("item", ItemNames.name(ingredient.template()))));
        }
        List<CurrencyCost> costs = trade.costs();
        for (int i = 0; i < costs.size(); i++) {
            CurrencyCost cost = costs.get(i);
            String key = !status.costAvailable()[i] ? "gui.shop-view.currency-unavailable"
                    : status.costEnough()[i] ? "gui.shop-view.currency-enough" : "gui.shop-view.currency-missing";
            lore.addAll(messages.lines(key, plugin.currencyToken().placeholders(cost.type(), cost.amount())));
        }
        List<Component> actions = messages.lines("gui.shop-view.actions");
        if (!actions.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(actions);
        }
        return GuiItems.withLore(trade.iconBase(), lore);
    }

    public void click(Player player, ShopViewHolder holder, int slot, ClickType click) {
        Shop shop = plugin.shops().get(holder.shopId());
        if (shop == null || !MainMenuGui.canOpen(player, shop)) {
            plugin.closeLater(player);
            return;
        }
        String tradeId = holder.tradeAt(slot);
        if (tradeId != null) {
            switch (click) {
                case LEFT -> trade(player, holder, tradeId, Mode.ONCE);
                case SHIFT_LEFT -> trade(player, holder, tradeId, Mode.ALL);
                case RIGHT -> {
                    if (navigate(player)) {
                        Trade trade = shop.trade(tradeId);
                        if (shop.revision() != holder.revision() || trade == null || !trade.usable()) {
                            refresh(player, holder);
                            return;
                        }
                        plugin.tradeDetail().open(player, shop, trade, holder.page());
                    }
                }
                default -> {
                }
            }
            return;
        }
        if (click != ClickType.LEFT) {
            return;
        }
        int pages = GridLayout.pages(shop.usableTrades().size());
        int delta = GuiItems.isControl(slot, GridLayout.PREVIOUS_COLUMN) ? -1
                : GuiItems.isControl(slot, GridLayout.NEXT_COLUMN) ? 1 : 0;
        int target = holder.page() + delta;
        if (delta != 0 && target >= 0 && target < pages && navigate(player)) {
            holder.page(target);
            refresh(player, holder);
        }
    }

    private boolean navigate(Player player) {
        if (!plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.NAVIGATION, plugin.settings().navigationTicks)) {
            return false;
        }
        plugin.settings().sound(SoundKey.CLICK).play(player);
        return true;
    }

    private void trade(Player player, ShopViewHolder holder, String tradeId, Mode mode) {
        if (!plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.TRADE, plugin.settings().cooldownTicks)) {
            return;
        }
        Outcome outcome = plugin.tradeService().execute(player, holder.shopId(), holder.revision(), tradeId, mode);
        switch (outcome) {
            case SUCCESS, CHANGED, DISABLED, CURRENCY_UNAVAILABLE, CURRENCY_FAILED -> refresh(player, holder);
            case STATE, ERROR -> plugin.closeLater(player);
            default -> {
            }
        }
    }
}
