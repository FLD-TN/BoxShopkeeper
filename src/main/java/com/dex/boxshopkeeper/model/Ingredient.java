package com.dex.boxshopkeeper.model;

import org.bukkit.inventory.ItemStack;

/**
 * A template item and the required amount. {@code template} is null when the stored item could
 * not be decoded; {@code encoded} always holds the Base64 exactly as stored on disk.
 * The template is shared and must never be mutated: clone it first.
 */
public record Ingredient(ItemStack template, String encoded, int amount) {

    public boolean broken() {
        return template == null;
    }

    public int maxStack() {
        return template == null ? 1 : Math.max(1, template.getMaxStackSize());
    }

    /** Number of real stacks needed to show this ingredient: {@code ceil(amount / maxStack)}. */
    public int displaySlots() {
        int max = maxStack();
        return (amount + max - 1) / max;
    }

    public Ingredient withAmount(int newAmount) {
        return new Ingredient(template, encoded, newAmount);
    }
}
