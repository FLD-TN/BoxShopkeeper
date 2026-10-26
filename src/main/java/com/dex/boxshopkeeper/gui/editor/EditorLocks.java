package com.dex.boxshopkeeper.gui.editor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One admin per shop (§6.3). Released on back, close, quit, disable. Main thread only. */
public final class EditorLocks {

    private final Map<String, UUID> byShop = new HashMap<>();
    private final Map<UUID, String> byAdmin = new HashMap<>();

    /** The admin editing the shop, or null. */
    public UUID holder(String shopId) {
        return byShop.get(shopId);
    }

    /**
     * Locks the shop for the admin, releasing any other shop the admin held.
     * @return false when another admin holds it
     */
    public boolean acquire(String shopId, UUID admin) {
        UUID current = byShop.get(shopId);
        if (current != null && !current.equals(admin)) {
            return false;
        }
        release(admin);
        byShop.put(shopId, admin);
        byAdmin.put(admin, shopId);
        return true;
    }

    public void release(UUID admin) {
        String shopId = byAdmin.remove(admin);
        if (shopId != null) {
            byShop.remove(shopId);
        }
    }

    public boolean isEmpty() {
        return byShop.isEmpty();
    }

    public int size() {
        return byShop.size();
    }

    public void clear() {
        byShop.clear();
        byAdmin.clear();
    }
}
