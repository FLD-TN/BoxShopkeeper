package com.dex.boxshopkeeper.item;

import com.dex.boxshopkeeper.gui.style.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Item names as shown to players and written to the trade log. */
public final class ItemNames {

    private ItemNames() {
    }

    /**
     * The item's own name component, unchanged: custom name, then item name, then the vanilla
     * translatable name (rendered in the client's language).
     */
    public static Component name(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return name;
            }
        }
        if (meta != null && meta.hasItemName()) {
            return meta.itemName();
        }
        return Component.translatable(item);
    }

    /** Short description for the log: {@code MATERIAL} or {@code MATERIAL "Custom name"}. */
    public static String describe(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String material = item.getType().name();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return material + " \"" + Text.plain(meta.displayName()) + "\"";
        }
        if (meta != null && meta.hasItemName()) {
            return material + " \"" + Text.plain(meta.itemName()) + "\"";
        }
        return material;
    }
}
