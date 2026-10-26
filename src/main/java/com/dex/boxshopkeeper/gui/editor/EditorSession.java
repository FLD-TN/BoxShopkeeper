package com.dex.boxshopkeeper.gui.editor;

/** Per-admin editor state while an editor screen or prompt is active. */
final class EditorSession {

    /** Shop the admin holds the lock for, or null on the shop list. */
    String shopId;
    int shopPage;
    int listPage;
    TradeDraft draft;
    /** Trade picked up in the shop editor to be moved, or null. Cleared whenever the shop editor is reopened. */
    String movingTradeId;
}
