package com.dex.boxshopkeeper.currency;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Balances of one player, read at most once per currency (update 01, §6.2). A GUI render builds
 * one snapshot for all its trades; a transaction builds a fresh one.
 */
public final class CurrencyBalances {

    /** Balance of a currency that is not available or not ready for the player. */
    public static final long UNAVAILABLE = -1;

    private final Player player;
    private final Function<String, CurrencyProvider> providers;
    private final Map<String, Long> read = new HashMap<>(4);

    public CurrencyBalances(Player player, Function<String, CurrencyProvider> providers) {
        this.player = player;
        this.providers = providers;
    }

    public static CurrencyBalances of(Player player, CurrencyRegistry registry) {
        return new CurrencyBalances(player, registry::get);
    }

    /** The balance ({@code >= 0}), or {@link #UNAVAILABLE}. */
    public long balance(String type) {
        Long cached = read.get(type);
        if (cached != null) {
            return cached;
        }
        CurrencyProvider provider = providers.apply(type);
        long value = provider == null || !provider.available() || !provider.ready(player)
                ? UNAVAILABLE : Math.max(0, provider.balance(player));
        read.put(type, value);
        return value;
    }
}
