package com.dex.boxshopkeeper.gui.style;

/**
 * Chest layout: every screen is a 6-row double chest with a decorated frame around 4 × 7 inner
 * slots. List entries fill the inner area left to right, top to bottom; unused inner slots stay
 * empty. Trades are shown as a 3 × 3 ingredient grid with the result on its right.
 */
public final class GridLayout {

    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * COLUMNS;
    public static final int INNER_COLUMNS = 7;
    public static final int INNER_ROWS = 4;
    public static final int PER_PAGE = INNER_COLUMNS * INNER_ROWS;

    public static final int BACK_COLUMN = 0;
    public static final int PREVIOUS_COLUMN = 3;
    public static final int PAGE_COLUMN = 4;
    public static final int NEXT_COLUMN = 5;

    /** Top-centre slot holding the screen's header item. */
    public static final int HEADER_SLOT = 4;
    /** Centre of the inner area, used for "empty" info items. */
    public static final int CENTRE_SLOT = 22;

    /** 3 × 3 ingredient grid (columns 2–4, rows 1–3), in reading order. */
    public static final int[] GRID = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    /** Result slot, right of the grid's middle row, with a marker on each side. */
    public static final int RESULT_SLOT = 24;
    public static final int[] RESULT_MARKERS = {23, 25};

    private static final int[] ACCENTS = {0, 1, 9, 7, 8, 17, 36, 45, 46, 44, 52, 53};

    private GridLayout() {
    }

    /** Number of pages, at least 1. */
    public static int pages(int entries) {
        return Math.max(1, (entries + PER_PAGE - 1) / PER_PAGE);
    }

    public static int clampPage(int page, int entries) {
        return Math.max(0, Math.min(page, pages(entries) - 1));
    }

    /** Index of the first entry shown on a page (0-based page). */
    public static int firstIndex(int page) {
        return page * PER_PAGE;
    }

    /** Slots of the entries shown on one page (0-based page), filled from the top-left. */
    public static int[] pageSlots(int entries, int page) {
        int count = Math.max(0, Math.min(PER_PAGE, entries - firstIndex(page)));
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = (1 + i / INNER_COLUMNS) * COLUMNS + 1 + i % INNER_COLUMNS;
        }
        return slots;
    }

    /** Slot of a column in the bottom row. */
    public static int bottomSlot(int column) {
        return (ROWS - 1) * COLUMNS + column;
    }

    public static boolean isBorder(int slot) {
        int row = slot / COLUMNS;
        int column = slot % COLUMNS;
        return row == 0 || row == ROWS - 1 || column == 0 || column == COLUMNS - 1;
    }

    /** Frame corners drawn in the accent material (an L shape in each corner). */
    public static boolean isAccent(int slot) {
        for (int accent : ACCENTS) {
            if (accent == slot) {
                return true;
            }
        }
        return false;
    }

    /** Position of a slot in {@link #GRID}, or -1. */
    public static int gridIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) {
            if (GRID[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
