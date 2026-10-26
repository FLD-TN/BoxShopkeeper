package com.dex.boxshopkeeper.trade;

import com.dex.boxshopkeeper.currency.CurrencyException;
import com.dex.boxshopkeeper.currency.CurrencyProvider;
import com.dex.boxshopkeeper.currency.CurrencyReceipt;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transaction ordering and rollback of the currency step, driven by fake providers (update 01, §6.4). */
class CurrencyPaymentTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String REASON = "shopkeeper:test/t1";

    private enum Mode { OK, REFUSE, THROW }

    private final List<String> events = new ArrayList<>();
    private final List<LogRecord> logged = new ArrayList<>();
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private final class Fake implements CurrencyProvider {
        private final String id;
        private final Mode mode;
        private final boolean refundFails;
        private final boolean refundThrows;

        Fake(String id, Mode mode, boolean refundFails, boolean refundThrows) {
            this.id = id;
            this.mode = mode;
            this.refundFails = refundFails;
            this.refundThrows = refundThrows;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean ready(Player player) {
            return true;
        }

        @Override
        public long balance(Player player) {
            return Long.MAX_VALUE;
        }

        @Override
        public RemovalReceipt withdraw(Player player, long amount, String reason) {
            events.add("withdraw " + id + " " + amount);
            if (mode == Mode.REFUSE) {
                throw new CurrencyException(id + " refused");
            }
            if (mode == Mode.THROW) {
                throw new IllegalStateException(id + " crashed");
            }
            return new CurrencyReceipt(logger, "Steve", PLAYER, id, amount, reason, () -> {
                events.add("refund " + id + " " + amount);
                if (refundThrows) {
                    throw new IllegalStateException("refund crashed");
                }
                return refundFails ? "refund refused" : null;
            });
        }

        @Override
        public long maxAmount() {
            return CurrencyCost.maxAmount(id);
        }
    }

    private CurrencyPayment.Charge charge(String type, long amount, Mode mode) {
        return new CurrencyPayment.Charge(type, new Fake(type, mode, false, false), amount);
    }

    @Test
    void withdrawsInOrderAndKeepsReceipts() throws Exception {
        List<RemovalReceipt> receipts = new ArrayList<>();
        CurrencyPayment.withdraw(null, List.of(
                charge("gem", 40, Mode.OK),
                charge("points", 10, Mode.OK),
                charge("vault", 100_000, Mode.OK)), REASON, receipts);
        assertEquals(List.of("withdraw gem 40", "withdraw points 10", "withdraw vault 100000"), events);
        assertEquals(3, receipts.size());
        assertTrue(logged.isEmpty());
    }

    @Test
    void failingWithdrawRefundsEarlierCurrenciesInReverse() {
        // CUR-11: vault fails after gem succeeded.
        List<RemovalReceipt> receipts = new ArrayList<>();
        CurrencyPayment.FailedException failure = assertThrows(CurrencyPayment.FailedException.class,
                () -> CurrencyPayment.withdraw(null, List.of(
                        charge("gem", 20, Mode.OK),
                        charge("points", 10, Mode.OK),
                        charge("vault", 50_000, Mode.REFUSE)), REASON, receipts));
        assertEquals("vault", failure.charge().type());
        assertInstanceOf(CurrencyException.class, failure.getCause());
        assertEquals(List.of("withdraw gem 20", "withdraw points 10", "withdraw vault 50000",
                "refund points 10", "refund gem 20"), events);
        assertTrue(receipts.isEmpty());
    }

    @Test
    void throwingProviderIsAFailure() {
        List<RemovalReceipt> receipts = new ArrayList<>();
        CurrencyPayment.FailedException failure = assertThrows(CurrencyPayment.FailedException.class,
                () -> CurrencyPayment.withdraw(null, List.of(
                        charge("gem", 20, Mode.OK),
                        charge("points", 10, Mode.THROW)), REASON, receipts));
        assertEquals("points", failure.charge().type());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(List.of("withdraw gem 20", "withdraw points 10", "refund gem 20"), events);
    }

    @Test
    void failedRollbackIsLoggedAndTheOthersStillRollBack() {
        List<RemovalReceipt> receipts = new ArrayList<>();
        assertThrows(CurrencyPayment.FailedException.class, () -> CurrencyPayment.withdraw(null, List.of(
                new CurrencyPayment.Charge("gem", new Fake("gem", Mode.OK, false, false), 20),
                new CurrencyPayment.Charge("points", new Fake("points", Mode.OK, true, false), 10),
                new CurrencyPayment.Charge("vault", new Fake("vault", Mode.REFUSE, false, false), 5)),
                REASON, receipts));
        assertEquals(List.of("withdraw gem 20", "withdraw points 10", "withdraw vault 5",
                "refund points 10", "refund gem 20"), events);
        assertEquals(1, logged.size());
        LogRecord record = logged.get(0);
        assertEquals(Level.SEVERE, record.getLevel());
        assertTrue(record.getMessage().startsWith("Currency rollback failed: player=Steve uuid=" + PLAYER
                + " type=points amount=10 reason=" + REASON + ". Compensate manually"), record.getMessage());
    }

    @Test
    void throwingRollbackNeverEscapes() {
        List<RemovalReceipt> receipts = new ArrayList<>();
        assertThrows(CurrencyPayment.FailedException.class, () -> CurrencyPayment.withdraw(null, List.of(
                new CurrencyPayment.Charge("gem", new Fake("gem", Mode.OK, false, true), 20),
                new CurrencyPayment.Charge("vault", new Fake("vault", Mode.REFUSE, false, false), 5)),
                REASON, receipts));
        assertEquals(List.of("withdraw gem 20", "withdraw vault 5", "refund gem 20"), events);
        assertEquals(1, logged.size());
        assertEquals(Level.SEVERE, logged.get(0).getLevel());
        assertInstanceOf(IllegalStateException.class, logged.get(0).getThrown());
    }

    @Test
    void itemReceiptsRollBackBeforeCurrencies() throws Exception {
        // Steps 5/6 failing: item receipts are rolled back first, then currency receipts, newest first.
        List<RemovalReceipt> receipts = new ArrayList<>();
        CurrencyPayment.withdraw(null, List.of(charge("gem", 20, Mode.OK), charge("vault", 50_000, Mode.OK)),
                REASON, receipts);
        receipts.add(() -> events.add("restore diamond"));
        receipts.add(() -> events.add("restore iron"));
        CurrencyPayment.rollback(receipts);
        assertEquals(List.of("withdraw gem 20", "withdraw vault 50000",
                "restore iron", "restore diamond", "refund vault 50000", "refund gem 20"), events);
    }

    @Test
    void noChargesDoNothing() throws Exception {
        List<RemovalReceipt> receipts = new ArrayList<>();
        CurrencyPayment.withdraw(null, List.of(), REASON, receipts);
        assertTrue(receipts.isEmpty());
        assertTrue(events.isEmpty());
    }
}
