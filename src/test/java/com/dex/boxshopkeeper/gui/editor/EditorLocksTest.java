package com.dex.boxshopkeeper.gui.editor;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorLocksTest {

    @Test
    void oneAdminPerShop() {
        EditorLocks locks = new EditorLocks();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(locks.acquire("tradecoal", a));
        assertFalse(locks.acquire("tradecoal", b));
        assertEquals(a, locks.holder("tradecoal"));
        assertTrue(locks.acquire("tradecoal", a));

        // Moving to another shop releases the first one.
        assertTrue(locks.acquire("tradeiron", a));
        assertTrue(locks.acquire("tradecoal", b));

        locks.release(a);
        locks.release(b);
        assertNull(locks.holder("tradecoal"));
        assertTrue(locks.isEmpty());
    }
}
