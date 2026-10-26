package com.dex.boxshopkeeper.gui;

/** Screen [1]: every shop the player may open. */
public final class MainMenuHolder extends GuiHolder {

    private int page;
    private String[] slotShopIds = new String[0];

    public MainMenuHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = page;
    }

    public String shopAt(int slot) {
        return slot >= 0 && slot < slotShopIds.length ? slotShopIds[slot] : null;
    }

    public void slotShopIds(String[] ids) {
        this.slotShopIds = ids;
    }
}
