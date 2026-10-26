package com.dex.boxshopkeeper.currency;

import org.bukkit.plugin.Plugin;

/**
 * A provider backed by another plugin's API. Implementations are the only classes importing that
 * API and are created by {@link CurrencyRegistry} only while the plugin is enabled.
 */
interface CurrencyHook extends CurrencyProvider {

    /** Looks the plugin's service up again and caches it. Never called per click. */
    void refresh();

    /** Clears the cached service when it belongs to {@code disabling}. */
    void forget(Plugin disabling);

    /** Display name offered by the plugin itself, or null. */
    default String pluginDisplayName() {
        return null;
    }
}
