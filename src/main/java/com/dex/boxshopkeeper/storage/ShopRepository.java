package com.dex.boxshopkeeper.storage;

import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.item.ItemCodec;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Loads, caches and writes {@code shops/<id>.yml} (§9). Templates are decoded once at load time.
 * Files are only written on an editor save: backup first, then an atomic replace. Saves publish
 * the shop at once and hand the disk work to one background thread, so writes keep their order and
 * the main thread never waits on the disk; {@link #flush()} waits for them (reload, disable).
 */
public final class ShopRepository {

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path shopsDirectory;
    private final Path backupsDirectory;
    private final Logger logger;
    private final Function<String, ItemStack> decoder;
    private final Map<String, Shop> shops = new TreeMap<>();
    private long lastRevision;
    /** Disk writes, one at a time in submission order. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BoxShopkeeper-ShopWriter");
        thread.setDaemon(true);
        return thread;
    });
    /** Last backup time per shop; writer thread only. */
    private final Map<String, Long> lastBackup = new HashMap<>();
    /** Told (on the writer thread) when a write fails; the plugin forwards it to the main thread. */
    private Consumer<String> failureListener = id -> {
    };

    public ShopRepository(Path dataFolder, Logger logger) {
        this(dataFolder, logger, ItemCodec::decode);
    }

    /** @param decoder item decoder; tests replace {@link ItemCodec#decode}, which needs a server */
    ShopRepository(Path dataFolder, Logger logger, Function<String, ItemStack> decoder) {
        this.shopsDirectory = dataFolder.resolve("shops");
        this.backupsDirectory = dataFolder.resolve("backups");
        this.logger = logger;
        this.decoder = decoder;
    }

    // ---------------------------------------------------------------- queries

    public Shop get(String id) {
        return id == null ? null : shops.get(id);
    }

    public Collection<Shop> all() {
        return shops.values();
    }

    public int size() {
        return shops.size();
    }

    public long nextRevision() {
        return ++lastRevision;
    }

    // ---------------------------------------------------------------- loading

    /** Thrown when a shop file is not valid YAML; the caller decides whether that aborts. */
    public static final class LoadException extends Exception {
        public LoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Reads every shop file without touching the cache or any file.
     * @param strict when true, a file that is not valid YAML aborts the whole load
     */
    public Map<String, Shop> loadAll(PluginConfig config, boolean strict) throws LoadException {
        Map<String, Shop> loaded = new TreeMap<>();
        try {
            Files.createDirectories(shopsDirectory);
        } catch (IOException e) {
            throw new LoadException("Cannot create " + shopsDirectory, e);
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shopsDirectory, "*.yml")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new LoadException("Cannot list " + shopsDirectory, e);
        }
        files.sort(Comparator.comparing(Path::toString));
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = name.substring(0, name.length() - ".yml".length());
            if (!Shop.ID_PATTERN.matcher(id).matches()) {
                logger.warning("Ignoring " + name + ": file name is not a valid shop id");
                continue;
            }
            try {
                loaded.put(id, parse(id, file, config));
            } catch (IOException | InvalidConfigurationException e) {
                if (strict) {
                    throw new LoadException("Cannot parse " + name + ": " + e.getMessage(), e);
                }
                logger.severe("Cannot parse " + name + ", shop skipped: " + e.getMessage());
            }
        }
        if (loaded.size() > config.limits.shops()) {
            logger.warning("There are " + loaded.size() + " shops, above the limit of " + config.limits.shops());
        }
        return loaded;
    }

    /** Replaces the cache with a loaded set, giving every shop a new revision. */
    public void replaceAll(Map<String, Shop> loaded, PluginConfig config) {
        shops.clear();
        for (Shop shop : loaded.values()) {
            shops.put(shop.id(), shop.toBuilder().build(nextRevision(), config.smallCaps));
        }
    }

    private Shop parse(String id, Path file, PluginConfig config) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        String storedId = yaml.getString("id", id);
        if (!id.equals(storedId)) {
            logger.warning("Shop file " + file.getFileName() + " declares id '" + storedId + "', using '" + id + "'");
        }

        Shop.Builder builder = Shop.builder(id)
                .displayName(yaml.getString("display-name", id))
                .description(yaml.getStringList("description"))
                .menuOrder(yaml.getInt("menu-order", 0))
                .permissionRequired(yaml.getBoolean("permission-required", false));

        String iconData = yaml.getString("icon");
        if (iconData != null && !iconData.isBlank()) {
            ItemStack icon = null;
            try {
                icon = decoder.apply(iconData);
            } catch (RuntimeException e) {
                logger.warning("Shop " + id + ": icon cannot be decoded (" + e.getMessage() + "), using the default icon");
            }
            builder.icon(icon, iconData);
        }

        List<Trade> trades = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<Map<?, ?>> rawTrades = yaml.getMapList("trades");
        for (int index = 0; index < rawTrades.size(); index++) {
            Trade trade = parseTrade(id, index, rawTrades.get(index), config.limits);
            if (!ids.add(trade.id())) {
                String fresh = freeTradeId(ids);
                logger.warning("Shop " + id + ": duplicate trade id " + trade.id() + ", renamed to " + fresh);
                ids.add(fresh);
                trade = trade.withId(fresh);
            }
            trades.add(trade);
        }
        if (trades.size() > config.limits.tradesPerShop()) {
            logger.warning("Shop " + id + " has " + trades.size() + " trades, above the limit of " + config.limits.tradesPerShop());
        }
        builder.trades(trades);
        return builder.build(0, config.smallCaps);
    }

    Trade parseTrade(String shopId, int index, Map<?, ?> raw, PluginConfig.Limits limits) {
        String tradeId = raw.get("id") == null ? "t" + (index + 1) : String.valueOf(raw.get("id"));
        Integer slot = raw.get("slot") instanceof Number number ? number.intValue() : null;
        boolean enabled = !(raw.get("enabled") instanceof Boolean flag) || flag;
        String where = "Shop " + shopId + " trade " + tradeId;
        String problem = null;

        ResultSpec result = null;
        if (raw.get("result") instanceof Map<?, ?> resultMap) {
            String data = resultMap.get("item") == null ? null : String.valueOf(resultMap.get("item"));
            int amount = amount(resultMap.get("amount"), where + " result");
            ItemStack template = decode(data, where + " result");
            if (template == null) {
                problem = "result cannot be decoded";
            }
            result = new ResultSpec(template, data, amount);
        } else {
            problem = "no result";
        }

        List<Ingredient> ingredients = new ArrayList<>();
        List<CurrencyCost> costs = new ArrayList<>();
        List<Map<String, Object>> rawEntries = new ArrayList<>();
        if (raw.get("ingredients") instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?, ?> map)) {
                    logger.warning(where + " ingredient " + i + ": not a map");
                    problem = "invalid ingredient";
                    continue;
                }
                boolean hasItem = map.containsKey("item");
                boolean hasCurrency = map.containsKey("currency");
                if (hasCurrency || !hasItem) {
                    // Not an item entry: written back exactly as read if the trade ends up with a problem.
                    rawEntries.add(copy(map));
                    if (hasItem || !hasCurrency) {
                        logger.warning(where + " ingredient " + i + ": needs exactly one of 'item' and 'currency'");
                        problem = "invalid ingredient";
                        continue;
                    }
                    String type = String.valueOf(map.get("currency"));
                    if (!CurrencyCost.known(type)) {
                        logger.warning(where + " ingredient " + i + ": unknown currency '" + type + "'");
                        problem = "unknown currency '" + type + "'";
                        continue;
                    }
                    long amount = CurrencyCost.parseAmount(map.get("amount"), type);
                    if (amount < 1) {
                        logger.warning(where + " ingredient " + i + ": " + type + " amount " + map.get("amount")
                                + " is not an integer from 1 to " + CurrencyCost.maxAmount(type));
                        problem = "invalid currency amount";
                        continue;
                    }
                    costs.add(new CurrencyCost(type, amount));
                    continue;
                }
                String data = map.get("item") == null ? null : String.valueOf(map.get("item"));
                int amount = amount(map.get("amount"), where + " ingredient " + i);
                ItemStack template = decode(data, where + " ingredient " + i);
                if (template == null) {
                    problem = "ingredient " + i + " cannot be decoded";
                }
                ingredients.add(new Ingredient(template, data, amount));
            }
        }
        if (ingredients.isEmpty() && costs.isEmpty() && problem == null) {
            problem = "no ingredients";
        }
        if (problem == null) {
            ingredients = mergeSimilar(ingredients);
            try {
                costs = CurrencyCost.merge(costs);
            } catch (CurrencyCost.TooLargeException e) {
                logger.warning(where + ": " + e.getMessage());
                problem = "invalid currency amount";
            }
        }
        if (problem == null && ingredients.size() + costs.size() > limits.ingredientsPerTrade()) {
            problem = "too many ingredients";
        }
        Trade trade = new Trade(tradeId, slot, ingredients, costs, problem == null ? null : rawEntries,
                result, enabled, problem);
        if (problem == null && trade.displaySlots() > limits.displaySlotsPerTrade()) {
            problem = "needs " + trade.displaySlots() + " display slots";
            trade = new Trade(tradeId, slot, ingredients, costs, rawEntries, result, enabled, problem);
        }
        if (problem != null) {
            logger.warning(where + " disabled: " + problem);
        }
        return trade;
    }

    /** A non-item entry as read: keys as strings, values untouched. */
    private static Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private ItemStack decode(String data, String where) {
        try {
            return decoder.apply(data);
        } catch (RuntimeException e) {
            logger.warning(where + ": item cannot be decoded (" + e.getMessage() + ")");
            return null;
        }
    }

    private int amount(Object raw, String where) {
        long value = raw instanceof Number number ? number.longValue() : 1;
        long clamped = Math.max(1, Math.min(PluginConfig.MAX_AMOUNT, value));
        if (clamped != value) {
            logger.warning(where + ": amount " + value + " clamped to " + clamped);
        }
        return (int) clamped;
    }

    /** Merges ingredients whose templates are similar, summing amounts (capped). Order is kept. */
    public static List<Ingredient> mergeSimilar(List<Ingredient> ingredients) {
        List<Ingredient> merged = new ArrayList<>();
        outer:
        for (Ingredient ingredient : ingredients) {
            for (int i = 0; i < merged.size(); i++) {
                Ingredient existing = merged.get(i);
                if (existing.template() != null && ingredient.template() != null
                        && existing.template().isSimilar(ingredient.template())) {
                    long sum = (long) existing.amount() + ingredient.amount();
                    merged.set(i, existing.withAmount((int) Math.min(PluginConfig.MAX_AMOUNT, sum)));
                    continue outer;
                }
            }
            merged.add(ingredient);
        }
        return merged;
    }

    public static String freeTradeId(Collection<String> used) {
        int n = 1;
        while (used.contains("t" + n)) {
            n++;
        }
        return "t" + n;
    }

    // ---------------------------------------------------------------- writing

    public void failureListener(Consumer<String> listener) {
        this.failureListener = listener;
    }

    /**
     * Publishes the shop with a new revision and queues its file write (backup + atomic replace).
     * The YAML text is built here, on the main thread, from the immutable shop.
     */
    public Shop save(Shop.Builder builder, PluginConfig config) {
        Shop shop = builder.build(nextRevision(), config.smallCaps);
        shops.put(shop.id(), shop);
        byte[] bytes = serialize(shop).getBytes(StandardCharsets.UTF_8);
        int keep = config.backupsKeep;
        long interval = config.backupMinIntervalSeconds * 1000L;
        submit(shop.id(), "save", () -> write(shop.id(), bytes, keep, interval));
        return shop;
    }

    /** Removes the shop now and queues the file deletion; the last state is always backed up. */
    public void delete(String id, PluginConfig config) {
        shops.remove(id);
        int keep = config.backupsKeep;
        submit(id, "delete", () -> {
            Path file = file(id);
            if (Files.exists(file)) {
                backup(id, file, keep, 0);
                Files.delete(file);
            }
        });
    }

    private interface DiskTask {
        void run() throws IOException;
    }

    private void submit(String id, String action, DiskTask task) {
        writer.execute(() -> {
            try {
                task.run();
            } catch (IOException | RuntimeException e) {
                logger.log(Level.SEVERE, "Cannot " + action + " shop file " + id
                        + ": the server keeps the in-memory version, which is lost on reload or restart", e);
                try {
                    failureListener.accept(id);
                } catch (RuntimeException ignored) {
                    // never let the error path throw
                }
            }
        });
    }

    /** Waits for every queued write (at most 30 s). Main thread: reload. */
    public void flush() {
        try {
            writer.submit(() -> {
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("Shop files are still being written: " + e);
        }
    }

    /** Writes what is queued and stops the writer (disable). */
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.severe("Shop files did not finish writing in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Path file(String id) {
        return shopsDirectory.resolve(id + ".yml");
    }

    /** Writer thread. */
    private void write(String id, byte[] bytes, int keep, long backupInterval) throws IOException {
        Files.createDirectories(shopsDirectory);
        Path file = file(id);
        if (Files.exists(file)) {
            backup(id, file, keep, backupInterval);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String serialize(Shop shop) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", shop.id());
        yaml.set("display-name", shop.displayName());
        yaml.set("description", shop.description());
        if (shop.iconEncoded() != null) {
            yaml.set("icon", shop.iconEncoded());
        }
        yaml.set("menu-order", shop.menuOrder());
        yaml.set("permission-required", shop.permissionRequired());
        List<Map<String, Object>> trades = new ArrayList<>();
        for (Trade trade : shop.trades()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", trade.id());
            if (trade.slot() != null) {
                map.put("slot", trade.slot());
            }
            map.put("enabled", trade.enabled());
            if (trade.result() != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("item", trade.result().encoded());
                result.put("amount", trade.result().amount());
                map.put("result", result);
            }
            List<Map<String, Object>> ingredients = new ArrayList<>();
            for (Ingredient ingredient : trade.ingredients()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("item", ingredient.encoded());
                entry.put("amount", ingredient.amount());
                ingredients.add(entry);
            }
            if (trade.rawEntries() != null) {
                ingredients.addAll(trade.rawEntries());
            } else {
                for (CurrencyCost cost : trade.costs()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("currency", cost.type());
                    entry.put("amount", cost.amount());
                    ingredients.add(entry);
                }
            }
            map.put("ingredients", ingredients);
            trades.add(map);
        }
        yaml.set("trades", trades);
        return yaml.saveToString();
    }

    /**
     * Writer thread. Copies the current file to {@code backups/}, at most once per {@code minInterval}
     * ms per shop: a burst of editor saves keeps the state from before the burst instead of rotating
     * every good backup out. {@code minInterval} 0 always backs up (delete).
     */
    private void backup(String id, Path file, int keep, long minInterval) throws IOException {
        if (keep <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastBackup.get(id);
        if (minInterval > 0 && last != null && now - last < minInterval) {
            return;
        }
        Files.createDirectories(backupsDirectory);
        String stamp = LocalDateTime.now().format(BACKUP_TIME);
        Path target = backupsDirectory.resolve(id + "-" + stamp + ".yml");
        if (Files.exists(target)) {
            // Same second as the previous backup: never overwrite an older state.
            target = backupsDirectory.resolve(id + "-" + stamp + "-" + (now % 1000) + ".yml");
        }
        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        lastBackup.put(id, now);

        Pattern own = Pattern.compile(Pattern.quote(id) + "-\\d{8}-\\d{6}(-\\d{1,3})?\\.yml");
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDirectory, id + "-*.yml")) {
            for (Path path : stream) {
                if (own.matcher(path.getFileName().toString()).matches()) {
                    backups.add(path);
                }
            }
        }
        backups.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        for (int i = keep; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
