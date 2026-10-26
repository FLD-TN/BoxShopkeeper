package com.dex.boxshopkeeper.model;

import org.bukkit.inventory.ItemStack;

/**
 * The item given by a trade. {@code template} is null when the stored item could not be decoded.
 * The template is shared and must never be mutated: clone it first.
 */
public record ResultSpec(ItemStack template, String encoded, int amount) {

    public boolean broken() {
        return template == null;
    }

    public int maxStack() {
        return template == null ? 1 : Math.max(1, template.getMaxStackSize());
    }
}
