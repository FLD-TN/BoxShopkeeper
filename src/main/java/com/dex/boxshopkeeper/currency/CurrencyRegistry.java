package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.gui.style.Text;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.source.RemovalReceipt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The three currency providers by id (update 01, §3.3). Never imports a third-party class: each hook
 * is created only while its plugin is enabled, and a {@link LinkageError} from it (plugin missing or
 * an incompatible version) is caught once and makes that currency unavailable. Service lookups are
 * cached and refreshed on plugin enable/disable, on service (un)registration and on reload.
 */
public final class CurrencyRegistry implements Listener {

    private final BoxShopkeeperPlugin plugin;
    private final Map<String, Slot> slots = new LinkedHashMap<>();

    public CurrencyRegistry(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
        Logger logger = plugin.getLogger();
        // Lambdas, not constructor references: a hook class is loaded only when its factory runs.
        slots.put(CurrencyCost.GEM, new Slot(CurrencyCost.GEM, "BoxCore", log -> new GemCurrency(log)));
        slots.put(CurrencyCost.POINTS, new Slot(CurrencyCost.POINTS, "PlayerPoints", log -> new PointsCurrency(log)));
        slots.put(CurrencyCost.VAULT, new Slot(CurrencyCost.VAULT, "Vault", log -> new VaultCurrency(log)));
        for (Slot slot : slots.values()) {
            slot.logger = logger;
        }
    }

    /** The provider of a type, or null for an unknown type. Never null for gem, points and vault. */
    public CurrencyProvider get(String type) {
        return type == null ? null : slots.get(type);
    }

    /**
     * Display name: {@code currencies.<type>.name} (colour tags allowed, never small caps), else the
     * plugin's own name (gem), else the id.
     */
    public Component displayName(String type) {
        PluginConfig.CurrencyStyle style = plugin.settings().currency(type);
        String raw;
        if (style != null && !style.name().isBlank()) {
            raw = style.name();
        } else {
            Slot slot = slots.get(type);
            String own = slot == null ? null : slot.pluginDisplayName();
            raw = own != null && !own.isBlank() ? Text.escape(own)
                    : type == null || type.isEmpty() ? "" : Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
        Component name = plugin.messages().text().parse(Text.prepare(raw, false));
        return style == null ? name : style.paint(name);
    }

    /** Re-creates hooks for enabled plugins and looks their services up again. */
    public void refresh() {
        for (Slot slot : slots.values()) {
            slot.refresh();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        refresh();
    }

    /**
     * The disabling plugin is still enabled and its services still registered while this event runs,
     * so caches are cleared now and looked up again on the next tick.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            return;
        }
        for (Slot slot : slots.values()) {
            slot.forget(event.getPlugin());
        }
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::refresh);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(ServiceRegisterEvent event) {
        refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        refresh();
    }

    /** One currency: its hook when the plugin is enabled, otherwise an always-unavailable provider. */
    private static final class Slot implements CurrencyProvider {
        private final String id;
        private final String pluginName;
        private final Function<Logger, CurrencyHook> factory;
        private Logger logger;
        private CurrencyHook hook;
        private boolean broken;

        Slot(String id, String pluginName, Function<Logger, CurrencyHook> factory) {
            this.id = id;
            this.pluginName = pluginName;
            this.factory = factory;
        }

        void refresh() {
            if (broken) {
                return;
            }
            if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
                hook = null;
                return;
            }
            try {
                if (hook == null) {
                    hook = factory.apply(logger);
                }
                hook.refresh();
            } catch (LinkageError e) {
                disable(e);
            } catch (RuntimeException e) {
                hook = null;
                logger.warning("Cannot hook " + pluginName + " for currency " + id + ": " + e);
            }
        }

        void forget(org.bukkit.plugin.Plugin disabling) {
            if (pluginName.equals(disabling.getName())) {
                hook = null;
                return;
            }
            CurrencyHook current = hook;
            if (current != null) {
                try {
                    current.forget(disabling);
                } catch (LinkageError e) {
                    disable(e);
                }
            }
        }

        private void disable(LinkageError error) {
            hook = null;
            if (!broken) {
                broken = true;
                logger.warning(pluginName + " found but its API could not be loaded (" + error
                        + "), currency " + id + " disabled");
            }
        }

        String pluginDisplayName() {
            CurrencyHook current = hook;
            try {
                return current == null ? null : current.pluginDisplayName();
            } catch (LinkageError e) {
                disable(e);
                return null;
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean available() {
            CurrencyHook current = hook;
            try {
                return current != null && current.available();
            } catch (LinkageError e) {
                disable(e);
                return false;
            }
        }

        @Override
        public boolean ready(Player player) {
            CurrencyHook current = hook;
            try {
                return current != null && current.ready(player);
            } catch (LinkageError e) {
                disable(e);
                return false;
            }
        }

        @Override
        public long balance(Player player) {
            CurrencyHook current = hook;
            try {
                return current == null ? 0 : Math.max(0, current.balance(player));
            } catch (LinkageError e) {
                disable(e);
                return 0;
            }
        }

        @Override
        public RemovalReceipt withdraw(Player player, long amount, String reason) {
            CurrencyHook current = hook;
            if (current == null) {
                throw new CurrencyException(pluginName + " is not available");
            }
            try {
                return current.withdraw(player, amount, reason);
            } catch (LinkageError e) {
                disable(e);
                throw new CurrencyException(pluginName + " API error: " + e, e);
            }
        }

        @Override
        public long maxAmount() {
            return CurrencyCost.maxAmount(id);
        }
    }
}
