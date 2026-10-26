package com.dex.boxshopkeeper.trade;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySimulatorTest {

    private static final int N = InventorySimulator.SLOTS;

    private static int[] none() {
        int[] a = new int[N];
        Arrays.fill(a, -1);
        return a;
    }

    @Test
    void nonMonotoneCounterExample() {
        // Full inventory, 64 diamonds in slot 0, ingredient = 32 diamonds, result stacks to 64.
        int[] amounts = new int[N];
        Arrays.fill(amounts, 1);
        amounts[0] = 64;
        int[] ingredientOf = none();
        ingredientOf[0] = 0;
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N],
                new long[]{32}, 1, 64);
        assertFalse(sim.feasible(1));
        assertTrue(sim.feasible(2));
        assertEquals(2, sim.maxFeasible(2304));
    }

    @Test
    void fullInventoryNonStackableResultIsRefused() {
        int[] amounts = new int[N];
        Arrays.fill(amounts, 1);
        amounts[0] = 64;
        int[] ingredientOf = none();
        ingredientOf[0] = 0;
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N],
                new long[]{1}, 1, 1);
        assertFalse(sim.feasible(1));
        assertEquals(0, sim.maxFeasible(2304));
    }

    @Test
    void exactlyThirtySevenTrades() {
        int[] amounts = new int[N];
        int[] ingredientOf = none();
        int left = 370;
        for (int s = 0; left > 0; s++) {
            amounts[s] = Math.min(50, left);
            ingredientOf[s] = 0;
            left -= amounts[s];
        }
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N],
                new long[]{10}, 1, 64);
        assertEquals(37, sim.ingredientBound());
        assertEquals(37, sim.maxFeasible(2304));
    }

    @Test
    void amountAboveOneStackAcrossPartialStacks() {
        int[] amounts = new int[N];
        int[] ingredientOf = none();
        amounts[3] = 50;
        amounts[7] = 50;
        amounts[9] = 28;
        ingredientOf[3] = 0;
        ingredientOf[7] = 0;
        ingredientOf[9] = 0;
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N],
                new long[]{128}, 1, 1);
        assertEquals(128, sim.count(0));
        assertTrue(sim.feasible(1));
        assertEquals(1, sim.maxFeasible(2304));
    }

    @Test
    void fiveIngredients() {
        int[] amounts = new int[N];
        int[] ingredientOf = none();
        for (int i = 0; i < 5; i++) {
            amounts[i] = 64;
            ingredientOf[i] = i;
        }
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N],
                new long[]{1, 2, 4, 8, 16}, 1, 64);
        assertEquals(4, sim.maxFeasible(2304));
    }

    @Test
    void noIngredientIsBoundedByCapAndSpace() {
        // Currency-only trade: nothing to remove, ingredientBound() stays 0, only space and cap limit n.
        int[] amounts = new int[N];
        Arrays.fill(amounts, 1);
        amounts[0] = 0;
        amounts[1] = 0;
        InventorySimulator sim = new InventorySimulator(amounts, none(), new boolean[N], new long[0], 1, 64);
        assertEquals(0, sim.ingredientBound());
        assertTrue(sim.feasible(1));
        assertEquals(10, sim.maxFeasible(10));
        assertEquals(128, sim.maxFeasible(2304));

        InventorySimulator full = new InventorySimulator(new int[N], none(), new boolean[N], new long[0], 1, 1);
        assertEquals(36, full.maxFeasible(2304));
        Arrays.fill(amounts, 1);
        InventorySimulator noRoom = new InventorySimulator(amounts, none(), new boolean[N], new long[0], 1, 64);
        assertFalse(noRoom.feasible(1));
        assertEquals(0, noRoom.maxFeasible(2304));
    }

    @Test
    void capIsRespected() {
        int[] amounts = new int[N];
        int[] ingredientOf = none();
        amounts[0] = 64;
        ingredientOf[0] = 0;
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N],
                new long[]{1}, 1, 64);
        assertEquals(10, sim.maxFeasible(10));
    }

    @Test
    void overStackedSlotsAreCounted() {
        int[] amounts = new int[N];
        Arrays.fill(amounts, 1);
        amounts[0] = 127;
        int[] ingredientOf = none();
        ingredientOf[0] = 0;
        boolean[] similar = new boolean[N];
        similar[0] = true; // result similar to the over-stacked ingredient: no room until it drops below 64
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, similar,
                new long[]{1}, 1, 64);
        assertEquals(127, sim.count(0));
        assertFalse(sim.feasible(1));
        // Taking n leaves 127 - n; room n - 63 is never enough for n results.
        assertEquals(0, sim.maxFeasible(2304));

        // With one empty slot, room is 64 plus whatever the partial stack frees.
        amounts[35] = 0;
        InventorySimulator withSpace = new InventorySimulator(amounts, ingredientOf, similar,
                new long[]{1}, 1, 64);
        assertTrue(withSpace.feasible(1));
        assertEquals(127, withSpace.maxFeasible(2304));
    }

    @Test
    void removalOrderIsSmallestFirst() {
        int[] amounts = new int[N];
        boolean[] matches = new boolean[N];
        amounts[0] = 5;
        amounts[1] = 3;
        amounts[3] = 3;
        amounts[4] = 10;
        matches[0] = true;
        matches[1] = true;
        matches[2] = true; // empty slot is skipped
        matches[3] = true;
        matches[4] = true;
        assertArrayEquals(new int[]{1, 3, 0, 4}, InventorySimulator.removalOrder(amounts, matches));
    }

    /** The capacity bound must never hide a feasible n: compare with a plain downward search. */
    @Test
    void matchesBruteForce() {
        Random random = new Random(42);
        for (int round = 0; round < 3000; round++) {
            int k = 1 + random.nextInt(4);
            int[] amounts = new int[N];
            int[] ingredientOf = none();
            boolean[] similar = new boolean[N];
            int resultMax = random.nextBoolean() ? 64 : random.nextBoolean() ? 16 : 1;
            for (int s = 0; s < N; s++) {
                int kind = random.nextInt(k + 3);
                if (kind == 0) {
                    continue;
                }
                amounts[s] = 1 + random.nextInt(64);
                if (kind <= k) {
                    ingredientOf[s] = kind - 1;
                } else if (kind == k + 1) {
                    similar[s] = true;
                    amounts[s] = Math.min(amounts[s], resultMax);
                }
            }
            long[] perTrade = new long[k];
            for (int i = 0; i < k; i++) {
                perTrade[i] = 1 + random.nextInt(40);
            }
            long r = 1 + random.nextInt(8);
            InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, similar, perTrade, r, resultMax);
            int expected = 0;
            for (long n = Math.min(sim.ingredientBound(), 2304); n >= 1; n--) {
                if (sim.feasible(n)) {
                    expected = (int) n;
                    break;
                }
            }
            assertEquals(expected, sim.maxFeasible(2304), "round " + round);
        }
    }

    @Test
    void maxFeasibleWorstCaseIsUnderOneMillisecond() {
        // 18 ingredients, 1 each per trade, huge over-stacked slots so n_max reaches the cap.
        int[] amounts = new int[N];
        int[] ingredientOf = none();
        for (int s = 0; s < N; s++) {
            amounts[s] = 100_000;
            ingredientOf[s] = s % 18;
        }
        long[] perTrade = new long[18];
        Arrays.fill(perTrade, 1);
        InventorySimulator sim = new InventorySimulator(amounts, ingredientOf, new boolean[N], perTrade, 1, 64);
        for (int i = 0; i < 2000; i++) {
            sim.maxFeasible(2304); // warm-up
        }
        long[] times = new long[1000];
        for (int i = 0; i < times.length; i++) {
            long start = System.nanoTime();
            sim.maxFeasible(2304);
            times[i] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        long p99 = times[(int) (times.length * 0.99)];
        assertTrue(p99 < 1_000_000L, "p99 = " + p99 + " ns");
    }
}
