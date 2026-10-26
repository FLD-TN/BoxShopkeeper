package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.gui.GuiHolder;

/** Every admin editor screen. Holds only ids, page and slot mapping. */
public final class EditorHolder extends GuiHolder {

    public enum Screen { SHOP_LIST, SHOP, TRADE, CONFIRM }

    public enum Confirm { DELETE_SHOP, DELETE_TRADE }

    private final Screen screen;
    private final String shopId;
    private final String tradeId;
    private final Confirm confirm;
    private int page;
    private String[] slotIds = new String[0];

    public EditorHolder(Screen screen, String shopId, String tradeId, Confirm confirm, int page) {
        this.screen = screen;
        this.shopId = shopId;
        this.tradeId = tradeId;
        this.confirm = confirm;
        this.page = page;
    }

    public Screen screen() {
        return screen;
    }

    public String shopId() {
        return shopId;
    }

    public String tradeId() {
        return tradeId;
    }

    public Confirm confirm() {
        return confirm;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = page;
    }

    public String idAt(int slot) {
        return slot >= 0 && slot < slotIds.length ? slotIds[slot] : null;
    }

    public void slotIds(String[] ids) {
        this.slotIds = ids;
    }
}
