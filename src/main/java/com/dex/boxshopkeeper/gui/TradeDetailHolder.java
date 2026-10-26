package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.gui.style.GridLayout;

/**
 * Screen [3]: one trade in full detail on a plain background — ingredients in the 3 × 3 grid,
 * the result in column 7 with a marker above it and the trade button two rows below it,
 * Back in the bottom-left corner.
 */
public final class TradeDetailHolder extends GuiHolder {

    public static final int RESULT_SLOT = 25;
    public static final int MARKER_SLOT = RESULT_SLOT - GridLayout.COLUMNS;
    public static final int TRADE_SLOT = RESULT_SLOT + 2 * GridLayout.COLUMNS;
    public static final int BACK_SLOT = GridLayout.bottomSlot(GridLayout.BACK_COLUMN);
    public static final int INGREDIENT_CAPACITY = GridLayout.GRID.length;

    private final String shopId;
    private final String tradeId;
    private final int returnPage;
    private long revision;

    public TradeDetailHolder(String shopId, String tradeId, int returnPage) {
        this.shopId = shopId;
        this.tradeId = tradeId;
        this.returnPage = returnPage;
    }

    public String shopId() {
        return shopId;
    }

    public String tradeId() {
        return tradeId;
    }

    public int returnPage() {
        return returnPage;
    }

    public long revision() {
        return revision;
    }

    public void revision(long revision) {
        this.revision = revision;
    }
}
