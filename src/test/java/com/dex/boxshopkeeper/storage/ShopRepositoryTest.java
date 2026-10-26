package com.dex.boxshopkeeper.storage;

import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shop-file parsing with currency entries (update 01, §5). Item data in these files is a material
 * name, decoded into a fake item because real item decoding needs a running server.
 */
class ShopRepositoryTest {

    private static final PluginConfig.Limits LIMITS = new PluginConfig.Limits(200, 500, 9, 9, 65_536, 32, 6, 48);

    @TempDir
    Path folder;

    /** An item that needs no server registries: stacks to 64, similar by material. */
    private static final class FakeItem extends ItemStack {
        private final Material material;

        FakeItem(Material material) {
            this.material = material;
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other != null && other.getType() == getType();
        }
    }

    private ShopRepository repository() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new ShopRepository(folder, logger, data -> {
            if (data == null || data.isBlank()) {
                throw new IllegalArgumentException("empty item data");
            }
            return new FakeItem(Material.valueOf(data));
        });
    }

    /** Parses the {@code trades:} list of a shop file. */
    private List<Trade> parse(String trades) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("trades:\n" + trades);
        ShopRepository repository = repository();
        List<Map<?, ?>> raw = yaml.getMapList("trades");
        List<Trade> parsed = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            parsed.add(repository.parseTrade("test", i, raw.get(i), LIMITS));
        }
        return parsed;
    }

    /** Writes the trades with the shop serializer and returns each trade's {@code ingredients} list as written. */
    private static List<List<Map<?, ?>>> written(List<Trade> trades) throws InvalidConfigurationException {
        String text = ShopRepository.serialize(Shop.builder("test").trades(trades).build(1, false));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        List<List<Map<?, ?>>> out = new ArrayList<>();
        for (Map<?, ?> trade : yaml.getMapList("trades")) {
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> ingredients = (List<Map<?, ?>>) trade.get("ingredients");
            out.add(ingredients);
        }
        return out;
    }

    private static String serialized(List<Trade> trades) {
        return ShopRepository.serialize(Shop.builder("test").trades(trades).build(1, false));
    }

    @Test
    void legacyFileReadsTheSameTradesWithoutCosts() throws Exception {
        List<Trade> trades = parse("""
                  - id: t1
                    slot: 10
                    enabled: true
                    result: {item: NETHERITE_INGOT, amount: 1}
                    ingredients:
                      - {item: DIAMOND, amount: 128}
                      - {item: IRON_INGOT, amount: 3}
                      - {item: DIAMOND, amount: 64}
                  - id: t2
                    enabled: false
                    result: {item: BREAD, amount: 3}
                    ingredients:
                      - {item: WHEAT, amount: 2000000}
                  - id: t3
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: '', amount: 5}
                  - id: t4
                    result: {item: BREAD, amount: 1}
                    ingredients: []
                """);
        Trade t1 = trades.get(0);
        assertNull(t1.problem());
        assertTrue(t1.usable());
        assertEquals(10, t1.slot());
        assertEquals(2, t1.ingredients().size()); // similar items merged as before
        assertEquals("DIAMOND", t1.ingredients().get(0).encoded());
        assertEquals(192, t1.ingredients().get(0).amount());
        assertEquals(3, t1.ingredients().get(1).amount());
        assertTrue(t1.costs().isEmpty());
        assertNull(t1.rawEntries());
        assertEquals(4, t1.displaySlots());

        Trade t2 = trades.get(1);
        assertFalse(t2.enabled());
        assertEquals(PluginConfig.MAX_AMOUNT, t2.ingredients().get(0).amount()); // item amounts still clamp
        assertEquals("needs 15625 display slots", t2.problem());
        assertTrue(t2.costs().isEmpty());

        assertEquals("ingredient 0 cannot be decoded", trades.get(2).problem());
        assertTrue(trades.get(2).costs().isEmpty());
        assertEquals("no ingredients", trades.get(3).problem());

        // Written back exactly as 1.0.0 writes it: only item entries, no currency key anywhere.
        String text = serialized(trades);
        assertFalse(text.contains("currency"), text);
        List<List<Map<?, ?>>> written = written(trades);
        assertEquals(List.of(Map.of("item", "DIAMOND", "amount", 192), Map.of("item", "IRON_INGOT", "amount", 3)),
                written.get(0));
        assertEquals(List.of(Map.of("item", "", "amount", 5)), written.get(2));
        assertEquals(List.of(), written.get(3));
    }

    @Test
    void currencyEntriesBecomeMergedCostsInFixedOrder() throws Exception {
        List<Trade> trades = parse("""
                  - id: t1
                    result: {item: NETHERITE_INGOT, amount: 1}
                    ingredients:
                      - {currency: vault, amount: 50000}
                      - {item: DIAMOND, amount: 1}
                      - {currency: gem, amount: 20}
                      - {currency: vault, amount: 20000}
                """);
        Trade trade = trades.get(0);
        assertNull(trade.problem());
        assertEquals(1, trade.ingredients().size());
        assertEquals(List.of(new CurrencyCost("gem", 20), new CurrencyCost("vault", 70_000)), trade.costs());
        assertEquals(3, trade.displaySlots());

        List<Map<?, ?>> written = written(trades).get(0);
        assertEquals(List.of(
                Map.of("item", "DIAMOND", "amount", 1),
                Map.of("currency", "gem", "amount", 20),
                Map.of("currency", "vault", "amount", 70_000)), written);
        assertEquals(trade.costs(), parse(serialized(trades).substring(serialized(trades).indexOf("- id"))).get(0).costs());
    }

    @Test
    void currencyOnlyTradeIsUsable() throws Exception {
        Trade trade = parse("""
                  - id: t1
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: vault, amount: 1000}
                """).get(0);
        assertNull(trade.problem());
        assertTrue(trade.usable());
        assertTrue(trade.ingredients().isEmpty());
        assertEquals(List.of(new CurrencyCost("vault", 1000)), trade.costs());
        assertEquals(1, trade.displaySlots());
    }

    @Test
    void largeAmountIsKeptExactly() throws Exception {
        // CUR-23
        Trade trade = parse("""
                  - id: t1
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: vault, amount: 5000000}
                      - {currency: gem, amount: 1000000000000}
                """).get(0);
        assertNull(trade.problem());
        assertEquals(List.of(new CurrencyCost("gem", 1_000_000_000_000L), new CurrencyCost("vault", 5_000_000)),
                trade.costs());
    }

    @Test
    void bothOrNeitherKeyIsInvalid() throws Exception {
        List<Trade> trades = parse("""
                  - id: both
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: DIAMOND, currency: vault, amount: 5}
                  - id: neither
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {amount: 5}
                """);
        assertEquals("invalid ingredient", trades.get(0).problem());
        assertEquals("invalid ingredient", trades.get(1).problem());
        List<List<Map<?, ?>>> written = written(trades);
        assertEquals(List.of(Map.of("item", "DIAMOND", "currency", "vault", "amount", 5)), written.get(0));
        assertEquals(List.of(Map.of("amount", 5)), written.get(1));
    }

    @Test
    void unknownCurrencyDisablesOnlyThatTradeAndRoundTrips() throws Exception {
        // CUR-14
        List<Trade> trades = parse("""
                  - id: t1
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: DIAMOND, amount: 1}
                      - {currency: robux, amount: 5}
                      - {currency: vault, amount: 100}
                  - id: t2
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: WHEAT, amount: 3}
                """);
        assertEquals("unknown currency 'robux'", trades.get(0).problem());
        assertTrue(trades.get(1).usable());

        // The admin edits the other trade of the same shop and saves.
        Trade edited = new Trade("t2", null, List.of(new Ingredient(new FakeItem(Material.WHEAT), "WHEAT", 4)),
                trades.get(1).result(), true, null);
        List<Trade> saved = List.of(trades.get(0), edited);
        assertEquals(List.of(
                Map.of("item", "DIAMOND", "amount", 1),
                Map.of("currency", "robux", "amount", 5),
                Map.of("currency", "vault", "amount", 100)), written(saved).get(0));
        String text = serialized(saved);
        assertEquals("unknown currency 'robux'", parse(text.substring(text.indexOf("- id"))).get(0).problem());
    }

    @Test
    void invalidAmountsAreNeverClampedAndRoundTrip() throws Exception {
        List<Trade> trades = parse("""
                  - id: zero
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: vault, amount: 0}
                  - id: decimal
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: vault, amount: 1.5}
                  - id: text
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: gem, amount: lots}
                  - id: huge
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: vault, amount: 10000000000000}
                  - id: points
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: points, amount: 3000000000}
                  - id: missing
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: vault}
                """);
        for (Trade trade : trades) {
            assertEquals("invalid currency amount", trade.problem(), trade.id());
            assertTrue(trade.costs().isEmpty(), trade.id());
        }
        List<List<Map<?, ?>>> written = written(trades);
        assertEquals(List.of(Map.of("currency", "vault", "amount", 0)), written.get(0));
        assertEquals(List.of(Map.of("currency", "vault", "amount", 1.5)), written.get(1));
        assertEquals(List.of(Map.of("currency", "gem", "amount", "lots")), written.get(2));
        assertEquals(10_000_000_000_000L, ((Number) written.get(3).get(0).get("amount")).longValue());
        assertEquals(3_000_000_000L, ((Number) written.get(4).get(0).get("amount")).longValue());
        assertEquals(List.of(Map.of("currency", "vault")), written.get(5));
    }

    @Test
    void mergedSumAboveTheMaximumIsInvalid() throws Exception {
        List<Trade> trades = parse("""
                  - id: t1
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {currency: points, amount: 2000000000}
                      - {currency: points, amount: 2000000000}
                """);
        assertEquals("invalid currency amount", trades.get(0).problem());
        assertEquals(List.of(
                Map.of("currency", "points", "amount", 2_000_000_000),
                Map.of("currency", "points", "amount", 2_000_000_000)), written(trades).get(0));
    }

    @Test
    void costsCountTowardsTheLimits() throws Exception {
        List<Trade> trades = parse("""
                  - id: slots
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: DIAMOND, amount: 512}
                      - {currency: vault, amount: 1}
                      - {currency: gem, amount: 1}
                  - id: count
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: DIAMOND, amount: 1}
                      - {item: IRON_INGOT, amount: 1}
                      - {item: GOLD_INGOT, amount: 1}
                      - {item: COAL, amount: 1}
                      - {item: WHEAT, amount: 1}
                      - {item: BREAD, amount: 1}
                      - {item: STONE, amount: 1}
                      - {currency: vault, amount: 1}
                      - {currency: gem, amount: 1}
                      - {currency: points, amount: 1}
                """);
        assertEquals("needs 10 display slots", trades.get(0).problem());
        assertEquals("too many ingredients", trades.get(1).problem());
    }

    @Test
    void entryRewrittenByVersion100StaysDisabled() throws Exception {
        // CUR-22 follow-up: 1.0.0 re-saved the currency entry as an item entry without data.
        Trade trade = parse("""
                  - id: t1
                    result: {item: BREAD, amount: 1}
                    ingredients:
                      - {item: DIAMOND, amount: 1}
                      - {item: null, amount: 50000}
                """).get(0);
        assertEquals("ingredient 1 cannot be decoded", trade.problem());
        assertFalse(trade.usable());
    }
}
