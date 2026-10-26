package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.gui.style.GridLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Base of every plugin GUI holder. Holders identify screens (never titles) and keep only ids,
 * page numbers and other primitive state — never a player (§17.1). Every screen is a 6-row chest.
 */
public abstract class GuiHolder implements InventoryHolder {

    private Inventory inventory;

    public Inventory createInventory(Component title) {
        this.inventory = Bukkit.createInventory(this, GridLayout.SIZE, title);
        return inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
