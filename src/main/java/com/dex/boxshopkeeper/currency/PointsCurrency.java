package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * {@code points}: PlayerPoints 3.x. The only class importing {@code org.black_ixx.playerpoints}.
 * {@code take}/{@code give} fire a synchronous cancellable event, so they run on the main thread only.
 */
final class PointsCurrency implements CurrencyHook {

    private static final String PLUGIN = "PlayerPoints";

    private final Logger logger;
    private PlayerPointsAPI api;

    PointsCurrency(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return CurrencyCost.POINTS;
    }

    @Override
    public void refresh() {
        PlayerPoints instance = PlayerPoints.getInstance();
        api = instance == null ? null : instance.getAPI();
    }

    @Override
    public void forget(Plugin disabling) {
        if (PLUGIN.equals(disabling.getName())) {
            api = null;
        }
    }

    @Override
    public boolean available() {
        return api != null;
    }

    @Override
    public boolean ready(Player player) {
        return player.isOnline();
    }

    @Override
    public long balance(Player player) {
        PlayerPointsAPI current = api;
        return current == null ? 0 : Math.max(0, current.look(player.getUniqueId()));
    }

    @Override
    public RemovalReceipt withdraw(Player player, long amount, String reason) {
        PlayerPointsAPI current = api;
        if (current == null) {
            throw new CurrencyException("PlayerPoints is not available");
        }
        UUID id = player.getUniqueId();
        int points = Math.toIntExact(amount);
        if (!current.take(id, points)) {
            throw new CurrencyException("PlayerPoints refused to take " + points);
        }
        return new CurrencyReceipt(logger, player.getName(), id, id(), amount, reason,
                () -> current.give(id, points) ? null : "PlayerPoints refused to give " + points);
    }

    @Override
    public long maxAmount() {
        return CurrencyCost.maxAmount(CurrencyCost.POINTS);
    }
}
