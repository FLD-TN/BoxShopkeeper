package com.dex.boxshopkeeper.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyCostTest {

    @Test
    void mergesByTypeInFixedOrder() {
        List<CurrencyCost> merged = CurrencyCost.merge(List.of(
                new CurrencyCost("vault", 50_000),
                new CurrencyCost("gem", 5),
                new CurrencyCost("vault", 20_000),
                new CurrencyCost("points", 10),
                new CurrencyCost("gem", 15)));
        assertEquals(List.of(
                new CurrencyCost("gem", 20),
                new CurrencyCost("points", 10),
                new CurrencyCost("vault", 70_000)), merged);
    }

    @Test
    void sumAtTheMaximumIsKept() {
        long half = CurrencyCost.maxAmount("vault") / 2;
        assertEquals(List.of(new CurrencyCost("vault", 2 * half)),
                CurrencyCost.merge(List.of(new CurrencyCost("vault", half), new CurrencyCost("vault", half))));
    }

    @Test
    void sumAboveTheMaximumIsRefusedNotCapped() {
        long max = CurrencyCost.maxAmount("vault");
        CurrencyCost.TooLargeException error = assertThrows(CurrencyCost.TooLargeException.class,
                () -> CurrencyCost.merge(List.of(new CurrencyCost("vault", max), new CurrencyCost("vault", 1))));
        assertEquals("vault", error.type());

        assertThrows(CurrencyCost.TooLargeException.class, () -> CurrencyCost.merge(List.of(
                new CurrencyCost("points", Integer.MAX_VALUE), new CurrencyCost("points", 1))));
        // Math.addExact overflow is reported the same way.
        assertThrows(CurrencyCost.TooLargeException.class, () -> CurrencyCost.merge(List.of(
                new CurrencyCost("gem", Long.MAX_VALUE), new CurrencyCost("gem", 1))));
    }

    @Test
    void maxAmounts() {
        assertEquals(1_000_000_000_000L, CurrencyCost.maxAmount("vault"));
        assertEquals(1_000_000_000_000L, CurrencyCost.maxAmount("gem"));
        assertEquals(Integer.MAX_VALUE, CurrencyCost.maxAmount("points"));
        assertEquals(-1, CurrencyCost.maxAmount("robux"));
    }

    @Test
    void parseAmountNeverClamps() {
        assertEquals(5_000_000, CurrencyCost.parseAmount(5_000_000, "vault"));
        assertEquals(1_000_000_000_000L, CurrencyCost.parseAmount(1_000_000_000_000L, "gem"));
        assertEquals(-1, CurrencyCost.parseAmount(1_000_000_000_001L, "gem"));
        assertEquals(-1, CurrencyCost.parseAmount(0, "vault"));
        assertEquals(-1, CurrencyCost.parseAmount(-5, "vault"));
        assertEquals(-1, CurrencyCost.parseAmount(1.5, "vault"));
        assertEquals(-1, CurrencyCost.parseAmount(50_000.0, "vault"));
        assertEquals(-1, CurrencyCost.parseAmount("50000", "vault"));
        assertEquals(-1, CurrencyCost.parseAmount(null, "vault"));
        assertEquals(-1, CurrencyCost.parseAmount(Integer.MAX_VALUE + 1L, "points"));
        assertEquals(-1, CurrencyCost.parseAmount(new BigInteger("99999999999999999999"), "vault"));
        assertEquals(-1, CurrencyCost.parseAmount(10, "robux"));
    }
}
