package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.util.RateLimiter.Action;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Editor clicks. Every click is cancelled first. The only click let through is a plain left or
 * right click inside the admin's own inventory, so the admin can pick items up onto the cursor;
 * nothing can ever move into or out of the editor inventory. {@code chetao.admin} is re-checked
 * on every click.
 */
public final class EditorListener implements Listener {

    private final BoxShopkeeperPlugin plugin;

    public EditorListener(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean ownInventoryPickup(InventoryClickEvent event, Inventory top) {
        Inventory clicked = event.getClickedInventory();
        return clicked != null && clicked != top
                && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (!(holder instanceof EditorHolder editor)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        try {
            if (!player.hasPermission("chetao.admin")) {
                plugin.closeLater(player);
                return;
            }
            if (ownInventoryPickup(event, top)) {
                event.setCancelled(false);
                return;
            }
            plugin.updateInventoryLater(player);
            int slot = event.getRawSlot();
            if (event.getClickedInventory() != top || slot < 0 || slot >= top.getSize()) {
                return;
            }
            if (!plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.EDITOR, plugin.settings().editorTicks)) {
                return;
            }
            EditorManager manager = plugin.editor();
            switch (editor.screen()) {
                case SHOP_LIST -> manager.shopList().click(player, editor, slot, event.getClick());
                case SHOP -> manager.shopEditor().click(player, editor, slot, event.getClick());
                case TRADE -> manager.tradeEditor().click(player, editor, slot, event.getClick());
                case CONFIRM -> manager.shopEditor().clickConfirm(player, editor, slot, event.getClick());
            }
        } catch (Throwable error) {
            event.setCancelled(true);
            plugin.fail(player, "Error handling an editor click", error);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickEnforce(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof EditorHolder
                && (!ownInventoryPickup(event, top) || !event.getWhoClicked().hasPermission("chetao.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        cancelDragIntoEditor(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragEnforce(InventoryDragEvent event) {
        cancelDragIntoEditor(event);
    }

    private static void cancelDragIntoEditor(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof EditorHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof EditorHolder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        try {
            plugin.editor().onClose(player, event.getReason());
        } catch (Throwable error) {
            plugin.errors().report("Error handling editor close", error);
        }
    }
}
