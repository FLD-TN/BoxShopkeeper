package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import com.dex.boxcore.api.GemResult;
import com.dex.boxcore.api.GemService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/** {@code gem}: BoxCore 1.3.0 GEM. The only class importing {@code com.dex.boxcore.api}. */
final class GemCurrency implements CurrencyHook {

    private static final String PLUGIN = "BoxCore";

    private final Logger logger;
    private GemService service;

    GemCurrency(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return CurrencyCost.GEM;
    }

    @Override
    public void refresh() {
        service = Bukkit.getServicesManager().load(GemService.class); // null on BoxCore < 1.3.0
    }

    @Override
    public void forget(Plugin disabling) {
        if (PLUGIN.equals(disabling.getName())) {
            service = null;
        }
    }

    @Override
    public String pluginDisplayName() {
        GemService current = service;
        return current == null ? null : current.displayName();
    }

    @Override
    public boolean available() {
        return service != null;
    }

    @Override
    public boolean ready(Player player) {
        GemService current = service;
        return current != null && current.isLoaded(player.getUniqueId());
    }

    @Override
    public long balance(Player player) {
        GemService current = service;
        return current == null ? 0 : Math.max(0, current.balance(player.getUniqueId()));
    }

    @Override
    public RemovalReceipt withdraw(Player player, long amount, String reason) {
        GemService current = service;
        if (current == null) {
            throw new CurrencyException("BoxCore gems are not available");
        }
        UUID id = player.getUniqueId();
        GemResult result = current.withdraw(id, amount, reason);
        if (result != GemResult.SUCCESS) {
            throw new CurrencyException(String.valueOf(result));
        }
        return new CurrencyReceipt(logger, player.getName(), id, id(), amount, reason, () -> {
            GemResult back = current.deposit(id, amount, reason + ":rollback");
            return back == GemResult.SUCCESS ? null : String.valueOf(back);
        });
    }

    @Override
    public long maxAmount() {
        return CurrencyCost.maxAmount(CurrencyCost.GEM);
    }
}
