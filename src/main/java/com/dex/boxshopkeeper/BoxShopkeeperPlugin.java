package com.dex.boxshopkeeper;

import com.dex.boxshopkeeper.command.TradeCommand;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.currency.CurrencyRegistry;
import com.dex.boxshopkeeper.currency.CurrencyToken;
import com.dex.boxshopkeeper.gui.GuiHolder;
import com.dex.boxshopkeeper.gui.GuiItems;
import com.dex.boxshopkeeper.gui.MainMenuGui;
import com.dex.boxshopkeeper.gui.MainMenuHolder;
import com.dex.boxshopkeeper.gui.PlayerGuiListener;
import com.dex.boxshopkeeper.gui.ShopViewGui;
import com.dex.boxshopkeeper.gui.ShopViewHolder;
import com.dex.boxshopkeeper.gui.TradeDetailGui;
import com.dex.boxshopkeeper.gui.TradeDetailHolder;
import com.dex.boxshopkeeper.gui.editor.EditorListener;
import com.dex.boxshopkeeper.gui.editor.EditorManager;
import com.dex.boxshopkeeper.gui.style.Text;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.source.InventorySource;
import com.dex.boxshopkeeper.storage.ShopRepository;
import com.dex.boxshopkeeper.trade.TradeLogger;
import com.dex.boxshopkeeper.trade.TradeService;
import com.dex.boxshopkeeper.util.ErrorReporter;
import com.dex.boxshopkeeper.util.RateLimiter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Wiring only: builds the services, registers listeners and the command, handles reload. */
public final class BoxShopkeeperPlugin extends JavaPlugin implements Listener {

    private PluginConfig settings;
    private Messages messages;
    private ShopRepository shops;
    private TradeLogger tradeLogger;
    private TradeService tradeService;
    private RateLimiter rateLimiter;
    private ErrorReporter errors;
    private CurrencyRegistry currencies;
    private CurrencyToken currencyToken;
    private GuiItems guiItems;
    private MainMenuGui mainMenu;
    private ShopViewGui shopView;
    private TradeDetailGui tradeDetail;
    private EditorManager editor;
    private TradeCommand command;
    private final Set<UUID> pendingUpdates = new HashSet<>();

    @Override
    public void onEnable() {
        errors = new ErrorReporter(getLogger());
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").isFile()) {
            saveResource("messages.yml", false);
        }
        shops = new ShopRepository(getDataFolder().toPath(), getLogger());
        shops.failureListener(this::reportSaveFailure);
        try {
            settings = PluginConfig.load(readConfig());
            messages = readMessages(settings);
            shops.replaceAll(shops.loadAll(settings, false), settings);
        } catch (Exception e) {
            getLogger().severe("Cannot load configuration, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        logConfigProblems(settings);
        currencies = new CurrencyRegistry(this);
        currencies.refresh();
        currencyToken = new CurrencyToken(this);

        tradeLogger = newTradeLogger(settings);
        rateLimiter = new RateLimiter(Bukkit::getCurrentTick);
        tradeService = new TradeService(this, new InventorySource());
        guiItems = new GuiItems(this);
        mainMenu = new MainMenuGui(this);
        shopView = new ShopViewGui(this);
        tradeDetail = new TradeDetailGui(this);
        editor = new EditorManager(this);
        command = new TradeCommand(this);

        getServer().getPluginManager().registerEvents(new PlayerGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new EditorListener(this), this);
        getServer().getPluginManager().registerEvents(editor.prompts(), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(currencies, this);

        PluginCommand chetao = getCommand("chetao");
        if (chetao != null) {
            chetao.setExecutor(command);
            chetao.setTabCompleter(command);
        }
        getLogger().info("Loaded " + shops.size() + " shops");
    }

    @Override
    public void onDisable() {
        if (editor != null) {
            editor.shutdown();
        }
        closeAllGuis();
        if (tradeLogger != null) {
            tradeLogger.shutdown();
        }
        if (shops != null) {
            shops.shutdown();
        }
        pendingUpdates.clear();
    }

    // ---------------------------------------------------------------- loading

    private YamlConfiguration readConfig() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        try (InputStream in = getResource("config.yml")) {
            if (in != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        }
        return config;
    }

    private void logConfigProblems(PluginConfig config) {
        config.notices.forEach(getLogger()::info);
        config.warnings.forEach(getLogger()::warning);
    }

    private Messages readMessages(PluginConfig config) throws Exception {
        try (InputStream bundled = getResource("messages.yml")) {
            return Messages.load(new File(getDataFolder(), "messages.yml"), bundled,
                    new Text(config.palette), config.smallCaps, getLogger());
        }
    }

    private TradeLogger newTradeLogger(PluginConfig config) {
        TradeLogger logger = new TradeLogger(getDataFolder().toPath().resolve("logs"),
                config.loggingEnabled, config.logQueueSize, getLogger());
        logger.pruneOld(config.logKeepDays);
        return logger;
    }

    /** {@code /chetao reload}: all-or-nothing; the old configuration stays on any error. */
    public void reload(CommandSender sender) {
        if (editor.busy()) {
            messages.send(sender, "reload.refused");
            return;
        }
        closeAllGuis();
        editor.shutdown();
        shops.flush(); // the files must hold every save before they are read again
        PluginConfig newSettings;
        Messages newMessages;
        Map<String, Shop> loaded;
        try {
            newSettings = PluginConfig.load(readConfig());
            newMessages = readMessages(newSettings);
            loaded = shops.loadAll(newSettings, true);
        } catch (Exception e) {
            getLogger().severe("Reload failed, keeping the previous configuration: " + e.getMessage());
            messages.send(sender, "reload.failed");
            return;
        }
        settings = newSettings;
        messages = newMessages;
        logConfigProblems(newSettings);
        currencies.refresh();
        shops.replaceAll(loaded, newSettings);
        tradeLogger.shutdown();
        tradeLogger = newTradeLogger(newSettings);
        messages.send(sender, "reload.done", Ph.of("shops", shops.size()));
    }

    /** Writer thread: tells online admins on the main thread that a shop file could not be written. */
    private void reportSaveFailure(String shopId) {
        if (!isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(TradeCommand.ADMIN)) {
                    messages.send(player, "editor.save-failed", Ph.of("shop", shopId));
                }
            }
        });
    }

    // ---------------------------------------------------------------- GUI helpers

    /** Re-sends the player's inventory next tick, once per tick (clears client-side ghost items). */
    public void updateInventoryLater(Player player) {
        UUID id = player.getUniqueId();
        if (pendingUpdates.add(id)) {
            Bukkit.getScheduler().runTask(this, () -> {
                pendingUpdates.remove(id);
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    online.updateInventory();
                }
            });
        }
    }

    /** Closes the player's plugin GUI on the next tick. */
    public void closeLater(Player player) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null && online.getOpenInventory().getTopInventory().getHolder(false) instanceof GuiHolder) {
                online.closeInventory();
            }
        });
    }

    /** Error path of every handler: log (rate-limited), tell the player, close the GUI next tick. */
    public void fail(Player player, String context, Throwable error) {
        errors.report(context, error);
        try {
            messages.send(player, "command.error");
        } catch (Throwable ignored) {
            // never let the error path throw
        }
        closeLater(player);
    }

    public void closeAllGuis() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof GuiHolder) {
                player.closeInventory();
            }
        }
    }

    /** After a shop is saved or deleted, refreshes every player viewing it on the next tick. */
    public void refreshViewers(String shopId) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
                try {
                    if (holder instanceof ShopViewHolder view && view.shopId().equals(shopId)) {
                        shopView.refresh(player, view);
                    } else if (holder instanceof TradeDetailHolder detail && detail.shopId().equals(shopId)) {
                        tradeDetail.refresh(player, detail);
                    } else if (holder instanceof MainMenuHolder menu) {
                        mainMenu.refresh(player, menu);
                    }
                } catch (Throwable error) {
                    fail(player, "Error refreshing a GUI after an edit of " + shopId, error);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        rateLimiter.forget(id);
        messages.forget(id);
        editor.quit(id);
        command.forget(id);
        pendingUpdates.remove(id);
    }

    // ---------------------------------------------------------------- accessors

    public PluginConfig settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public ShopRepository shops() {
        return shops;
    }

    public TradeLogger tradeLogger() {
        return tradeLogger;
    }

    public TradeService tradeService() {
        return tradeService;
    }

    public RateLimiter rateLimiter() {
        return rateLimiter;
    }

    public ErrorReporter errors() {
        return errors;
    }

    public CurrencyRegistry currencies() {
        return currencies;
    }

    public CurrencyToken currencyToken() {
        return currencyToken;
    }

    public GuiItems guiItems() {
        return guiItems;
    }

    public MainMenuGui mainMenu() {
        return mainMenu;
    }

    public ShopViewGui shopView() {
        return shopView;
    }

    public TradeDetailGui tradeDetail() {
        return tradeDetail;
    }

    public EditorManager editor() {
        return editor;
    }
}
