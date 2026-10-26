package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.source.RemovalReceipt;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Undo handle for one currency withdrawal: {@link #rollback()} deposits the amount back. A failed
 * deposit is logged as SEVERE for manual compensation; rollback never throws, so the other receipts
 * of the transaction still roll back.
 */
public final class CurrencyReceipt implements RemovalReceipt {

    /** Gives the amount back; returns null on success or a short error text. */
    @FunctionalInterface
    public interface Refund {
        String refund();
    }

    private final Logger logger;
    private final String playerName;
    private final UUID playerId;
    private final String type;
    private final long amount;
    private final String reason;
    private final Refund refund;

    public CurrencyReceipt(Logger logger, String playerName, UUID playerId, String type, long amount,
                           String reason, Refund refund) {
        this.logger = logger;
        this.playerName = playerName;
        this.playerId = playerId;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.refund = refund;
    }

    @Override
    public void rollback() {
        String error;
        Throwable cause = null;
        try {
            error = refund.refund();
        } catch (RuntimeException | LinkageError e) {
            error = e.toString();
            cause = e;
        }
        if (error == null) {
            return;
        }
        try {
            logger.log(Level.SEVERE, "Currency rollback failed: player=" + playerName + " uuid=" + playerId
                    + " type=" + type + " amount=" + amount + " reason=" + reason
                    + ". Compensate manually (" + error + ")", cause);
        } catch (RuntimeException ignored) {
            // rollback never throws
        }
    }
}
