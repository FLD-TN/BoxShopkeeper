package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.source.RemovalReceipt;
import org.bukkit.entity.Player;

/** One currency usable as a trade cost (update 01, §3.1). No third-party imports. Main thread only. */
public interface CurrencyProvider {

    /** {@code "vault"}, {@code "points"} or {@code "gem"}. */
    String id();

    /** Plugin present and enabled, its service found. Cheap; called on every render. */
    boolean available();

    /** Whether this player's balance can be read and changed now (gem: data loaded). */
    boolean ready(Player player);

    /** Current balance, {@code >= 0}. Only valid when {@link #ready(Player)}. */
    long balance(Player player);

    /**
     * Removes exactly {@code amount} ({@code >= 1}) or nothing.
     * @return a receipt whose {@link RemovalReceipt#rollback()} gives the amount back
     * @throws CurrencyException when the plugin refuses or fails (nothing removed)
     */
    RemovalReceipt withdraw(Player player, long amount, String reason);

    /** Largest amount one cost of this type may have. */
    long maxAmount();
}
