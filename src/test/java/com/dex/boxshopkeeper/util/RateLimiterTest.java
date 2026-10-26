package com.dex.boxshopkeeper.util;

import com.dex.boxshopkeeper.util.RateLimiter.Action;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void limitsPerActionAndIsEmptyAfterQuit() {
        AtomicInteger tick = new AtomicInteger(100);
        RateLimiter limiter = new RateLimiter(tick::get);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(player, Action.TRADE, 4));
        assertFalse(limiter.tryAcquire(player, Action.TRADE, 4));
        assertTrue(limiter.tryAcquire(player, Action.NAVIGATION, 5));
        tick.addAndGet(4);
        assertTrue(limiter.tryAcquire(player, Action.TRADE, 4));
        assertTrue(limiter.tryAcquire(player, Action.COMMAND, 20));
        assertTrue(limiter.tryAcquire(player, Action.EDITOR, 2));
        assertEquals(4, limiter.trackedEntries());

        limiter.forget(player);
        assertEquals(0, limiter.trackedEntries());
    }
}
