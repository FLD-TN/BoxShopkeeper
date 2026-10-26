package com.dex.boxshopkeeper.gui.style;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridLayoutTest {

    @Test
    void entriesFillFromTheTopLeft() {
        assertArrayEquals(new int[]{10}, GridLayout.pageSlots(1, 0));
        assertArrayEquals(new int[]{10, 11, 12, 13, 14}, GridLayout.pageSlots(5, 0));
        int[] eight = GridLayout.pageSlots(8, 0);
        assertEquals(16, eight[6]);
        assertEquals(19, eight[7]);
    }

    @Test
    void twentyEightFillOnePage() {
        assertEquals(1, GridLayout.pages(28));
        int[] slots = GridLayout.pageSlots(28, 0);
        assertEquals(10, slots[0]);
        assertEquals(43, slots[27]);
        for (int slot : slots) {
            assertFalse(GridLayout.isBorder(slot));
        }
    }

    @Test
    void twentyNinePaginate() {
        assertEquals(2, GridLayout.pages(29));
        assertEquals(28, GridLayout.pageSlots(29, 0).length);
        assertArrayEquals(new int[]{10}, GridLayout.pageSlots(29, 1));
        assertEquals(1, GridLayout.clampPage(5, 29));
    }

    @Test
    void emptyHasNoEntries() {
        assertEquals(1, GridLayout.pages(0));
        assertEquals(0, GridLayout.pageSlots(0, 0).length);
    }

    @Test
    void gridIsThreeByThreeInsideTheFrame() {
        assertEquals(9, GridLayout.GRID.length);
        Set<Integer> special = new HashSet<>();
        for (int slot : GridLayout.GRID) {
            assertFalse(GridLayout.isBorder(slot));
            assertTrue(special.add(slot));
        }
        assertTrue(special.add(GridLayout.RESULT_SLOT));
        for (int marker : GridLayout.RESULT_MARKERS) {
            assertTrue(special.add(marker));
        }
        assertEquals(4, GridLayout.gridIndex(21));
        assertEquals(-1, GridLayout.gridIndex(GridLayout.RESULT_SLOT));
    }

    @Test
    void frameAndControls() {
        assertTrue(GridLayout.isAccent(0));
        assertTrue(GridLayout.isAccent(53));
        assertTrue(GridLayout.isBorder(GridLayout.HEADER_SLOT));
        assertFalse(GridLayout.isBorder(GridLayout.CENTRE_SLOT));
        assertEquals(45, GridLayout.bottomSlot(GridLayout.BACK_COLUMN));
        assertEquals(48, GridLayout.bottomSlot(GridLayout.PREVIOUS_COLUMN));
        assertEquals(49, GridLayout.bottomSlot(GridLayout.PAGE_COLUMN));
        assertEquals(50, GridLayout.bottomSlot(GridLayout.NEXT_COLUMN));
    }
}
