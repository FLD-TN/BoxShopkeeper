package com.dex.boxshopkeeper.trade;

import com.dex.boxshopkeeper.currency.CurrencyProvider;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Step 4 of the transaction (update 01, §6.4): withdraws every cost in order, collecting receipts.
 * Independent of the plugin so ordering and rollback can be unit-tested with fake providers.
 */
final class CurrencyPayment {

    /** {@code amount} of one currency, already multiplied by {@code n}. */
    record Charge(String type, CurrencyProvider provider, long amount) {
    }

    /** A withdrawal failed; the receipts collected before it were rolled back. */
    static final class FailedException extends Exception {
        private final Charge charge;

        FailedException(Charge charge, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.charge = charge;
        }

        Charge charge() {
            return charge;
        }
    }

    private CurrencyPayment() {
    }

    /**
     * Withdraws each charge in order, appending its receipt to {@code receipts}. When one fails
     * ({@link com.dex.boxshopkeeper.currency.CurrencyException} or any other runtime exception), every
     * receipt in {@code receipts} is rolled back in reverse order and the list is cleared.
     */
    static void withdraw(Player player, List<Charge> charges, String reason, List<RemovalReceipt> receipts)
            throws FailedException {
        for (Charge charge : charges) {
            try {
                receipts.add(charge.provider().withdraw(player, charge.amount(), reason));
            } catch (RuntimeException e) {
                rollback(receipts);
                receipts.clear();
                throw new FailedException(charge, e);
            }
        }
    }

    /** Rolls receipts back newest first. */
    static void rollback(List<RemovalReceipt> receipts) {
        for (int i = receipts.size() - 1; i >= 0; i--) {
            receipts.get(i).rollback();
        }
    }
}
