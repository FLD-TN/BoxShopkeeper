package com.dex.boxshopkeeper.item;

import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.inventory.ItemStack;

/**
 * The only class importing MMOItems (6.10). Load it only while MMOItems is enabled; callers guard
 * against {@link LinkageError}. Main thread.
 */
final class MmoItemsHook implements MmoItemsUpdater.Source {

    @Override
    public String key(ItemStack item) {
        String type = MMOItems.getTypeName(item);
        if (type == null || type.isEmpty()) {
            return null;
        }
        String id = MMOItems.getID(item);
        return id == null || id.isEmpty() ? null : type + ":" + id;
    }

    @Override
    public ItemStack fresh(String key) {
        int colon = key.indexOf(':');
        ItemStack item = MMOItems.plugin.getItem(key.substring(0, colon), key.substring(colon + 1));
        if (item == null || item.isEmpty()) {
            return null;
        }
        item.setAmount(1);
        return item;
    }
}
