package com.dex.boxshopkeeper.item;

import org.bukkit.inventory.ItemStack;

/**
 * Exact matching (§4.2): same material and identical item data, only the amount ignored.
 * There is deliberately no other mode and no plugin-specific logic.
 */
public final class ItemMatcher {

    private ItemMatcher() {
    }

    public static boolean matches(ItemStack template, ItemStack candidate) {
        return template != null && candidate != null && !candidate.isEmpty() && template.isSimilar(candidate);
    }
}
