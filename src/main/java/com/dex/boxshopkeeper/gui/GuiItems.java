package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Shared GUI items: frame, filler, controls, buttons and stat-free icons. */
public final class GuiItems {

    private final BoxShopkeeperPlugin plugin;
    private final NamespacedKey iconKey;

    public GuiItems(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
        this.iconKey = new NamespacedKey(plugin, "icon");
    }

    public ItemStack filler() {
        ItemStack item = new ItemStack(plugin.settings().material(Button.FILLER));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Fills every slot with one plain pane (no frame), e.g. the trade detail background. */
    public void background(Inventory inventory, Button material) {
        ItemStack pane = pane(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane);
        }
    }

    private ItemStack pane(Button material) {
        ItemStack item = new ItemStack(plugin.settings().material(material));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Draws the frame (one plain border material). The inner area is filled with filler when
     * {@code fillInside}, otherwise left empty for list entries.
     */
    public void frame(Inventory inventory, boolean fillInside) {
        ItemStack frame = pane(Button.FRAME);
        ItemStack filler = fillInside ? filler() : null;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (GridLayout.isBorder(slot)) {
                inventory.setItem(slot, frame);
            } else {
                inventory.setItem(slot, filler);
            }
        }
    }

    public ItemStack button(Button material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(plugin.settings().material(material));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.isEmpty() ? null : lore);
        hideAll(meta);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack button(Button material, String nameKey, Ph... placeholders) {
        return button(material, plugin.messages().line(nameKey, placeholders), List.of());
    }

    /**
     * Places Back (optional), Previous, page indicator and Next at their fixed bottom-row columns;
     * hidden controls stay filler.
     */
    public void controls(Inventory inventory, int page, int pages, boolean back) {
        if (back) {
            inventory.setItem(GridLayout.bottomSlot(GridLayout.BACK_COLUMN),
                    button(Button.BACK, "gui.controls.back"));
        }
        if (pages > 1) {
            if (page > 0) {
                inventory.setItem(GridLayout.bottomSlot(GridLayout.PREVIOUS_COLUMN),
                        button(Button.PREVIOUS, "gui.controls.previous"));
            }
            inventory.setItem(GridLayout.bottomSlot(GridLayout.PAGE_COLUMN),
                    button(Button.PAGE, "gui.controls.page", Ph.of("page", page + 1), Ph.of("pages", pages)));
            if (page < pages - 1) {
                inventory.setItem(GridLayout.bottomSlot(GridLayout.NEXT_COLUMN),
                        button(Button.NEXT, "gui.controls.next"));
            }
        }
    }

    public static boolean isControl(int slot, int column) {
        return slot == GridLayout.bottomSlot(column);
    }

    /**
     * A copy of the template without its stats: same material, name, glint and model data,
     * own lore removed and every tooltip section hidden.
     */
    public ItemStack stripped(ItemStack template, int amount) {
        ItemStack icon = template.clone();
        icon.setAmount(Math.max(1, Math.min(amount, icon.getMaxStackSize())));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.lore(null);
            hideAll(meta);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /**
     * Hides every tooltip section. Items without explicit attribute modifiers get a neutral one,
     * because the hide flag has no effect on default attributes otherwise. Display copies only.
     */
    public void hideAll(ItemMeta meta) {
        if (!meta.hasAttributeModifiers()) {
            meta.addAttributeModifier(Attribute.GENERIC_LUCK,
                    new AttributeModifier(iconKey, 0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        meta.addItemFlags(ItemFlag.values());
    }

    /** A copy of {@code base} with its lore replaced. */
    public static ItemStack withLore(ItemStack base, List<Component> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** A copy of {@code base} with a new name and lore. */
    public static ItemStack withNameAndLore(ItemStack base, Component name, List<Component> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
