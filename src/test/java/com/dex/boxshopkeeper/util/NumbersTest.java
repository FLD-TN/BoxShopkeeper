package com.dex.boxshopkeeper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumbersTest {

    @Test
    void groupsThousandsWithDots() {
        assertEquals("0", Numbers.group(0));
        assertEquals("7", Numbers.group(7));
        assertEquals("999", Numbers.group(999));
        assertEquals("1.000", Numbers.group(1000));
        assertEquals("50.000", Numbers.group(50_000));
        assertEquals("120.000", Numbers.group(120_000));
        assertEquals("1.250.000", Numbers.group(1_250_000));
        assertEquals("1.000.000.000.000", Numbers.group(1_000_000_000_000L));
        assertEquals("9.223.372.036.854.775.807", Numbers.group(Long.MAX_VALUE));
    }

    @Test
    void negativeValuesKeepTheirSign() {
        assertEquals("-5", Numbers.group(-5));
        assertEquals("-12.000", Numbers.group(-12_000));
        assertEquals("-9.223.372.036.854.775.808", Numbers.group(Long.MIN_VALUE));
    }
}
