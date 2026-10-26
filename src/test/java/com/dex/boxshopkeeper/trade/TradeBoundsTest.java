package com.dex.boxshopkeeper.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeBoundsTest {

    private static final long[] NONE = new long[0];

    @Test
    void noItemIngredientIsUnbounded() {
        assertEquals(TradeBounds.UNBOUNDED, TradeBounds.items(NONE, NONE));
        // Currency-only trade: vault 1000, balance 5500 → 5 trades.
        long costs = TradeBounds.costs(new long[]{5_500}, new long[]{1_000});
        assertEquals(5, costs);
        assertEquals(5, TradeBounds.max(TradeBounds.items(NONE, NONE), costs, 2304));
    }

    @Test
    void noCostIsUnbounded() {
        assertEquals(TradeBounds.UNBOUNDED, TradeBounds.costs(NONE, NONE));
        long items = TradeBounds.items(new long[]{128}, new long[]{64});
        assertEquals(2, TradeBounds.max(items, TradeBounds.costs(NONE, NONE), 2304));
    }

    @Test
    void itemsAndCostsTakeTheSmallest() {
        // CUR-09: 3 diamonds (1 each), 200.000 Xu (50.000 each), 45 Gem (20 each) → limited by Gem.
        long items = TradeBounds.items(new long[]{3}, new long[]{1});
        long costs = TradeBounds.costs(new long[]{45, 200_000}, new long[]{20, 50_000});
        assertEquals(3, items);
        assertEquals(2, costs);
        assertEquals(2, TradeBounds.max(items, costs, 2304));
    }

    @Test
    void shortCostGivesZero() {
        long costs = TradeBounds.costs(new long[]{40_000}, new long[]{50_000});
        assertEquals(0, TradeBounds.max(10, costs, 2304));
    }

    @Test
    void unavailableCostGivesZero() {
        long costs = TradeBounds.costs(new long[]{1_000_000, -1}, new long[]{1, 1});
        assertEquals(0, costs);
        assertEquals(0, TradeBounds.max(TradeBounds.UNBOUNDED, costs, 2304));
    }

    @Test
    void maxPerClickCaps() {
        assertEquals(9000, TradeBounds.max(TradeBounds.UNBOUNDED, TradeBounds.UNBOUNDED, 9000));
        assertEquals(64, TradeBounds.max(1_000_000, TradeBounds.costs(new long[]{Long.MAX_VALUE}, new long[]{1}), 64));
    }
}
