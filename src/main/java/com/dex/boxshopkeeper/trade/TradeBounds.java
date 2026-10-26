package com.dex.boxshopkeeper.trade;

/**
 * Upper bounds on the number of trades (update 01, §6.3). Pure, on primitive arrays.
 * The inventory simulation then decides the final {@code n} below this bound.
 */
public final class TradeBounds {

    public static final long UNBOUNDED = Long.MAX_VALUE;

    private TradeBounds() {
    }

    /** {@code n_ing = min_i floor(c_i / a_i)}, {@link #UNBOUNDED} without item ingredient. */
    public static long items(long[] counts, long[] amounts) {
        long bound = UNBOUNDED;
        for (int i = 0; i < amounts.length; i++) {
            bound = Math.min(bound, counts[i] / amounts[i]);
        }
        return bound;
    }

    /**
     * {@code n_cost = min_c floor(balance_c / amount_c)}, {@link #UNBOUNDED} without cost, 0 when a
     * cost is not available or not ready (negative balance).
     */
    public static long costs(long[] balances, long[] amounts) {
        long bound = UNBOUNDED;
        for (int i = 0; i < amounts.length; i++) {
            if (balances[i] < 0) {
                return 0;
            }
            bound = Math.min(bound, balances[i] / amounts[i]);
        }
        return bound;
    }

    /** {@code n_max = min(n_ing, n_cost, max-per-click)}. */
    public static int max(long items, long costs, int maxPerClick) {
        return (int) Math.max(0, Math.min(Math.min(items, costs), maxPerClick));
    }
}
