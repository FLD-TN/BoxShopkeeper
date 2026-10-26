package com.dex.boxshopkeeper.item;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Template storage (§4.1): {@link ItemStack#serializeAsBytes()} as Base64. The byte format embeds
 * the data version, so Paper upgrades stored items after a server upgrade.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    /** Encodes a copy of the item with amount 1. */
    public static String encode(ItemStack item) {
        ItemStack one = item.clone();
        one.setAmount(1);
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    /** Size in bytes of an encoded template once decoded from Base64. */
    public static int byteSize(String encoded) {
        int padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
        return encoded.length() / 4 * 3 - padding;
    }

    /**
     * @throws IllegalArgumentException when the string is not valid Base64 or not a valid item
     */
    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("empty item data");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded.trim());
        ItemStack item;
        try {
            item = ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cannot deserialize item: " + e.getMessage(), e);
        }
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("item is empty");
        }
        item.setAmount(1);
        return item;
    }
}
