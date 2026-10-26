package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * One listener for screens [1] [2] [3]. Every click and drag whose top inventory is one of these
 * screens is cancelled first — including clicks in the player's own inventory — and cancelled again
 * at HIGHEST in case another plugin un-cancelled it. Only whitelisted actions are then performed by
 * the plugin itself.
 */
public final class PlayerGuiListener implements Listener {

    private final BoxShopkeeperPlugin plugin;

    public PlayerGuiListener(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isPlayerScreen(InventoryHolder holder) {
        return holder instanceof MainMenuHolder || holder instanceof ShopViewHolder || holder instanceof TradeDetailHolder;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!isPlayerScreen(holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            plugin.updateInventoryLater(player);
            int slot = event.getRawSlot();
            if (event.getClickedInventory() != top || slot < 0 || slot >= top.getSize()) {
                return;
            }
            if (holder instanceof MainMenuHolder menu) {
                plugin.mainMenu().click(player, menu, slot, event.getClick());
            } else if (holder instanceof ShopViewHolder view) {
                plugin.shopView().click(player, view, slot, event.getClick());
            } else if (holder instanceof TradeDetailHolder detail) {
                plugin.tradeDetail().click(player, detail, slot, event.getClick());
            }
        } catch (Throwable error) {
            plugin.fail(player, "Error handling a click in " + holder.getClass().getSimpleName(), error);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickEnforce(InventoryClickEvent event) {
        if (isPlayerScreen(event.getView().getTopInventory().getHolder(false))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (isPlayerScreen(event.getView().getTopInventory().getHolder(false))) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                plugin.updateInventoryLater(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragEnforce(InventoryDragEvent event) {
        if (isPlayerScreen(event.getView().getTopInventory().getHolder(false))) {
            event.setCancelled(true);
        }
    }
}
