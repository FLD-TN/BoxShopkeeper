package com.dex.boxshopkeeper.trade;

import java.util.Arrays;

/**
 * Pure simulation of the 36 main inventory slots on primitive arrays (§5.4, §17.2).
 * No Bukkit, scheduler or file access, so it can be unit-tested directly.
 *
 * <p>Removal takes each ingredient from its <b>smallest matching stacks first</b> (ties: lower slot
 * first), exactly like {@link com.dex.boxshopkeeper.source.InventorySource}. Result placement fills
 * partial stacks of the result, then empty slots; the space available is therefore the sum of the
 * room in partial result stacks plus one full stack per empty slot.
 */
public final class InventorySimulator {

    public static final int SLOTS = 36;

    private final int[] amounts;
    private final boolean[] resultSimilar;
    private final int[][] order;
    private final long[] perTrade;
    private final long[] counts;
    private final long resultPerTrade;
    private final int resultMaxStack;
    private final int[] scratch;

    /**
     * @param amounts        amount in each slot (0 when empty); may exceed the max stack size
     * @param ingredientOf   index of the ingredient each slot matches, or -1
     * @param resultSimilar  whether each slot holds an item similar to the result
     * @param perTrade       required amount {@code a_i} of each ingredient for one trade
     * @param resultPerTrade result items {@code r} given by one trade
     * @param resultMaxStack max stack size of the result item
     */
    public InventorySimulator(int[] amounts, int[] ingredientOf, boolean[] resultSimilar,
                              long[] perTrade, long resultPerTrade, int resultMaxStack) {
        if (amounts.length != ingredientOf.length || amounts.length != resultSimilar.length) {
            throw new IllegalArgumentException("slot arrays differ in length");
        }
        this.amounts = amounts.clone();
        this.resultSimilar = resultSimilar.clone();
        this.perTrade = perTrade.clone();
        this.resultPerTrade = resultPerTrade;
        this.resultMaxStack = Math.max(1, resultMaxStack);
        this.scratch = new int[amounts.length];
        this.counts = new long[perTrade.length];
        this.order = new int[perTrade.length][];
        for (int i = 0; i < perTrade.length; i++) {
            boolean[] matches = new boolean[amounts.length];
            for (int s = 0; s < amounts.length; s++) {
                if (ingredientOf[s] == i && amounts[s] > 0) {
                    matches[s] = true;
                    counts[i] += amounts[s];
                }
            }
            order[i] = removalOrder(amounts, matches);
        }
    }

    /** Matching slots with a positive amount, smallest amount first, then lowest slot. */
    public static int[] removalOrder(int[] amounts, boolean[] matches) {
        int size = 0;
        for (int s = 0; s < amounts.length; s++) {
            if (matches[s] && amounts[s] > 0) {
                size++;
            }
        }
        long[] keys = new long[size];
        int k = 0;
        for (int s = 0; s < amounts.length; s++) {
            if (matches[s] && amounts[s] > 0) {
                keys[k++] = ((long) amounts[s] << 32) | s;
            }
        }
        Arrays.sort(keys);
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = (int) (keys[i] & 0xFFFFFFFFL);
        }
        return slots;
    }

    /** Matching items of an ingredient across all slots. */
    public long count(int ingredient) {
        return counts[ingredient];
    }

    /** {@code n_ing = min_i floor(c_i / a_i)}. */
    public long ingredientBound() {
        long bound = Long.MAX_VALUE;
        for (int i = 0; i < perTrade.length; i++) {
            bound = Math.min(bound, counts[i] / perTrade[i]);
        }
        return perTrade.length == 0 ? 0 : bound;
    }

    /** Whether {@code n} trades can be done: ingredients present and every result fits. */
    public boolean feasible(long n) {
        if (n <= 0) {
            return false;
        }
        long needed = multiply(n, resultPerTrade);
        return removeInto(n, true) && capacity() >= needed;
    }

    /**
     * Largest feasible {@code n <= cap}, or 0. Feasibility is not monotone in {@code n} (a larger
     * {@code n} can free a slot), so candidates are tested downwards from a cheap upper bound.
     * Without item ingredient (a currency-only trade) only {@code cap} bounds {@code n}.
     */
    public int maxFeasible(int cap) {
        long nMax = perTrade.length == 0 ? cap : Math.min(ingredientBound(), cap);
        if (nMax <= 0) {
            return 0;
        }
        // Removing more only frees space, so the space left after removing n_max bounds every n.
        removeInto(nMax, false);
        long nCap = capacity() / resultPerTrade;
        for (long n = Math.min(nMax, nCap); n >= 1; n--) {
            if (feasible(n)) {
                return (int) n;
            }
        }
        return 0;
    }

    /**
     * Copies the amounts into the scratch array and removes {@code n * a_i} of each ingredient.
     * @return false when {@code strict} and an ingredient runs out
     */
    private boolean removeInto(long n, boolean strict) {
        System.arraycopy(amounts, 0, scratch, 0, amounts.length);
        for (int i = 0; i < perTrade.length; i++) {
            long remaining = multiply(n, perTrade[i]);
            for (int slot : order[i]) {
                if (remaining == 0) {
                    break;
                }
                int take = (int) Math.min(remaining, scratch[slot]);
                scratch[slot] -= take;
                remaining -= take;
            }
            if (strict && remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** Result items that fit into the scratch state. */
    private long capacity() {
        long room = 0;
        for (int s = 0; s < scratch.length; s++) {
            int amount = scratch[s];
            if (amount == 0) {
                room += resultMaxStack;
            } else if (resultSimilar[s] && amount < resultMaxStack) {
                room += resultMaxStack - amount;
            }
        }
        return room;
    }

    private static long multiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
