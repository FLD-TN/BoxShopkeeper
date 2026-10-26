package com.dex.boxshopkeeper.source;

/** Undo handle for one removal. */
public interface RemovalReceipt {

    /** Restore exactly what was removed. */
    void rollback();
}
