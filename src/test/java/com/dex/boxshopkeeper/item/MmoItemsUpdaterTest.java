package com.dex.boxshopkeeper.item;

import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Trade;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MmoItemsUpdaterTest {

    /** An item without server registries: {@code key} is its MMOItems TYPE:ID (null = plain item). */
    private static final class FakeItem extends ItemStack {
        final String key;
        final String version;

        FakeItem(String key, String version) {
            this.key = key;
            this.version = version;
        }

        @Override
        public Material getType() {
            return Material.PAPER;
        }
    }

    private static String encode(ItemStack item) {
        FakeItem fake = (FakeItem) item;
        return fake.key + "@" + fake.version;
    }

    /** MMOItems now knows MATERIAL:TIER_1, SWORD:BLADE and BOOM (which throws), all at version v2. */
    private static final MmoItemsUpdater.Source SOURCE = new MmoItemsUpdater.Source() {
        @Override
        public String key(ItemStack item) {
            return ((FakeItem) item).key;
        }

        @Override
        public ItemStack fresh(String key) {
            return switch (key) {
                case "MATERIAL:TIER_1", "SWORD:BLADE" -> new FakeItem(key, "v2");
                case "MATERIAL:BOOM" -> throw new IllegalStateException("template error");
                default -> null;
            };
        }
    };

    private static Ingredient ingredient(String key, String version, int amount) {
        FakeItem item = new FakeItem(key, version);
        return new Ingredient(item, encode(item), amount);
    }

    private static ResultSpec result(String key, String version, int amount) {
        FakeItem item = new FakeItem(key, version);
        return new ResultSpec(item, encode(item), amount);
    }

    private final MmoItemsUpdater updater = new MmoItemsUpdater(SOURCE, MmoItemsUpdaterTest::encode, 65_536);

    @Test
    void rebuildsIngredientsAndResultKeepingAmounts() {
        Trade trade = new Trade("t1", 10,
                List.of(ingredient("MATERIAL:TIER_1", "v1", 16), ingredient(null, "plain", 3)),
                List.of(new CurrencyCost("gem", 20)), null, result("SWORD:BLADE", "v1", 1), true, null);
        MmoItemsUpdater.Result result = updater.update(List.of(trade));

        assertTrue(result.changed());
        assertEquals(2, result.items());
        assertEquals(1, result.tradesChanged());
        assertTrue(result.problems().isEmpty());
        Trade updated = result.trades().get(0);
        assertEquals("MATERIAL:TIER_1@v2", updated.ingredients().get(0).encoded());
        assertEquals(16, updated.ingredients().get(0).amount());
        assertSame(trade.ingredients().get(1), updated.ingredients().get(1)); // plain item untouched
        assertEquals("SWORD:BLADE@v2", updated.result().encoded());
        assertEquals(1, updated.result().amount());
        assertEquals(10, updated.slot());
        assertEquals(trade.costs(), updated.costs());
        assertTrue(updated.enabled());
    }

    @Test
    void upToDateAndPlainTradesAreKeptAsTheSameInstance() {
        Trade current = new Trade("t1", null, List.of(ingredient("MATERIAL:TIER_1", "v2", 1)),
                result("SWORD:BLADE", "v2", 1), true, null);
        Trade plain = new Trade("t2", null, List.of(ingredient(null, "a", 1)), result(null, "b", 1), true, null);
        MmoItemsUpdater.Result result = updater.update(List.of(current, plain));
        assertFalse(result.changed());
        assertEquals(0, result.tradesChanged());
        assertSame(current, result.trades().get(0));
        assertSame(plain, result.trades().get(1));
    }

    @Test
    void unknownIdIsReportedAndKept() {
        Trade trade = new Trade("t3", null, List.of(ingredient("SWORD:OLD", "v1", 1)),
                result("MATERIAL:TIER_1", "v1", 2), true, null);
        MmoItemsUpdater.Result result = updater.update(List.of(trade));
        assertEquals(1, result.items());
        assertEquals(List.of(new MmoItemsUpdater.Problem("SWORD:OLD", "t3", "not found")), result.problems());
        assertEquals("SWORD:OLD@v1", result.trades().get(0).ingredients().get(0).encoded());
        assertEquals("MATERIAL:TIER_1@v2", result.trades().get(0).result().encoded());
    }

    @Test
    void failingTemplateIsReportedAndKept() {
        Trade trade = new Trade("t4", null, List.of(ingredient("MATERIAL:BOOM", "v1", 1)),
                result(null, "x", 1), true, null);
        MmoItemsUpdater.Result result = updater.update(List.of(trade));
        assertFalse(result.changed());
        assertEquals(List.of(new MmoItemsUpdater.Problem("MATERIAL:BOOM", "t4", "template error")), result.problems());
    }

    @Test
    void tooLargeItemIsReportedAndKept() {
        MmoItemsUpdater small = new MmoItemsUpdater(SOURCE, MmoItemsUpdaterTest::encode, 4);
        Trade trade = new Trade("t5", null, List.of(ingredient("MATERIAL:TIER_1", "v1", 1)),
                result(null, "x", 1), true, null);
        MmoItemsUpdater.Result result = small.update(List.of(trade));
        assertFalse(result.changed());
        assertEquals("too large", result.problems().get(0).reason());
    }

    @Test
    void brokenTradeKeepsItsProblemAndRawEntries() {
        Ingredient broken = new Ingredient(null, "not-base64", 2);
        List<Map<String, Object>> raw = List.of(Map.of("currency", "robux", "amount", 5));
        Trade trade = new Trade("t6", null, List.of(broken, ingredient("MATERIAL:TIER_1", "v1", 1)), List.of(), raw,
                result("SWORD:BLADE", "v1", 1), true, "unknown currency 'robux'");
        Trade updated = updater.update(List.of(trade)).trades().get(0);
        assertSame(broken, updated.ingredients().get(0));
        assertNull(updated.ingredients().get(0).template());
        assertEquals("MATERIAL:TIER_1@v2", updated.ingredients().get(1).encoded());
        assertEquals("unknown currency 'robux'", updated.problem());
        assertEquals(raw, updated.rawEntries());
    }
}
