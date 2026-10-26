package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/** {@code vault}: the economy registered through Vault. The only class importing {@code net.milkbowl.vault}. */
final class VaultCurrency implements CurrencyHook {

    private final Logger logger;
    private Economy economy;
    private Plugin economyPlugin;
    private boolean timed;

    VaultCurrency(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String id() {
        return CurrencyCost.VAULT;
    }

    @Override
    public void refresh() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
        economyPlugin = registration == null ? null : registration.getPlugin();
    }

    @Override
    public void forget(Plugin disabling) {
        if (disabling.equals(economyPlugin)) {
            economy = null;
            economyPlugin = null;
        }
    }

    @Override
    public boolean available() {
        Economy current = economy;
        return current != null && current.isEnabled();
    }

    @Override
    public boolean ready(Player player) {
        return player.isOnline();
    }

    @Override
    public long balance(Player player) {
        Economy current = economy;
        if (current == null) {
            return 0;
        }
        double balance = current.getBalance(player);
        if (Double.isNaN(balance) || balance <= 0) {
            return 0;
        }
        return balance >= 0x1p63 ? Long.MAX_VALUE : (long) Math.floor(balance);
    }

    @Override
    public RemovalReceipt withdraw(Player player, long amount, String reason) {
        Economy current = economy;
        Plugin owner = economyPlugin;
        if (current == null) {
            throw new CurrencyException("no Vault economy");
        }
        long start = System.nanoTime();
        EconomyResponse response = current.withdrawPlayer(player, (double) amount);
        if (!timed) {
            timed = true;
            logger.info("Vault economy " + (owner == null ? "?" : owner.getName()) + ": first withdrawal took "
                    + String.format("%.2f", (System.nanoTime() - start) / 1_000_000.0) + " ms on the main thread");
        }
        if (response == null || !response.transactionSuccess()) {
            throw new CurrencyException(response == null ? "no response" : String.valueOf(response.errorMessage));
        }
        return new CurrencyReceipt(logger, player.getName(), player.getUniqueId(), id(), amount, reason, () -> {
            EconomyResponse back = current.depositPlayer(player, (double) amount);
            return back != null && back.transactionSuccess() ? null
                    : back == null ? "no response" : String.valueOf(back.errorMessage);
        });
    }

    @Override
    public long maxAmount() {
        return CurrencyCost.maxAmount(CurrencyCost.VAULT);
    }
}
