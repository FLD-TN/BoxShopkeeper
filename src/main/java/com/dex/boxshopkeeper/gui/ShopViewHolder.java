package com.dex.boxshopkeeper.gui;

/** Screen [2]: the trades of one shop. */
public final class ShopViewHolder extends GuiHolder {

    private final String shopId;
    private int page;
    private long revision;
    private String[] slotTradeIds = new String[0];

    public ShopViewHolder(String shopId, int page) {
        this.shopId = shopId;
        this.page = page;
    }

    public String shopId() {
        return shopId;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = page;
    }

    public long revision() {
        return revision;
    }

    public void revision(long revision) {
        this.revision = revision;
    }

    public String tradeAt(int slot) {
        return slot >= 0 && slot < slotTradeIds.length ? slotTradeIds[slot] : null;
    }

    public void slotTradeIds(String[] ids) {
        this.slotTradeIds = ids;
    }
}
