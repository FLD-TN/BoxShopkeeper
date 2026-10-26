package com.dex.boxshopkeeper.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.3.1: every currency has its colour; old default texts are upgraded, edited ones kept. */
class CurrencyColorTest {

    private static YamlConfiguration bundled(String name) throws Exception {
        try (InputStream in = CurrencyColorTest.class.getClassLoader().getResourceAsStream(name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** The owner's file as read by the plugin: the file itself, with the bundled copy as defaults. */
    private static YamlConfiguration server(YamlConfiguration file, String name) throws Exception {
        file.setDefaults(bundled(name));
        return file;
    }

    @Test
    void bundledColoursAndFormats() throws Exception {
        YamlConfiguration c = bundled("config.yml");
        List<String> warnings = new ArrayList<>();
        assertEquals(TextColor.fromHexString("#55FF55"), PluginConfig.currencyColor(c, "vault", warnings));
        assertEquals(TextColor.fromHexString("#FFFF55"), PluginConfig.currencyColor(c, "points", warnings));
        assertEquals(TextColor.fromHexString("#8245F8"), PluginConfig.currencyColor(c, "gem", warnings));
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals("$", c.getString("currencies.vault.name"));
        assertEquals("%amount%%currency%", c.getString("currencies.vault.token.name"));
        assertEquals("Xu", c.getString("currencies.points.name"));
        assertEquals("%amount% %currency%", c.getString("currencies.points.token.name"));
        assertEquals("%amount% %currency%", c.getString("currencies.gem.token.name"));
    }

    @Test
    void missingOrBadColour() throws Exception {
        List<String> warnings = new ArrayList<>();
        YamlConfiguration missing = new YamlConfiguration();
        assertEquals(TextColor.fromHexString("#8245F8"), PluginConfig.currencyColor(missing, "gem", warnings));
        assertTrue(warnings.isEmpty());
        YamlConfiguration bad = new YamlConfiguration();
        bad.set("currencies.points.color", "vàng");
        assertEquals(TextColor.fromHexString("#FFFF55"), PluginConfig.currencyColor(bad, "points", warnings));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("currencies.points.color")), warnings.toString());
    }

    @Test
    void paintColoursWhatHasNoColourOnly() {
        PluginConfig.CurrencyStyle gem = new PluginConfig.CurrencyStyle("", null, 0, true, "%amount% %currency%",
                List.of(), null, TextColor.fromHexString("#8245F8"));
        Component painted = gem.paint(Component.text("100 ")
                .append(Component.text("Gem")).append(Component.text("!", NamedTextColor.RED)));
        assertEquals(TextColor.fromHexString("#8245F8"), painted.color());
        Component inner = painted.children().get(0);
        assertNull(inner.color(), "inherits the purple");
        assertNull(inner.children().get(0).color(), "inherits the purple");
        assertEquals(NamedTextColor.RED, inner.children().get(1).color(), "an explicit colour wins");
    }

    @Test
    void oldConfigTextsAreUpgradedEditedOnesKept() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        for (Map.Entry<String, List<Object>> e : OldDefaults.CONFIG.entrySet()) old.set(e.getKey(), e.getValue().get(0));
        old.set("currencies.gem.token.name", "<red>%amount% đá quý");
        YamlConfiguration file = server(old, "config.yml");
        assertEquals(7, OldDefaults.upgrade(file, OldDefaults.CONFIG));
        assertEquals("$", file.getString("currencies.vault.name"));
        assertEquals("Xu", file.getString("currencies.points.name"));
        assertEquals("%amount%%currency%", file.getString("currencies.vault.token.name"));
        assertEquals("<muted>để yêu cầu %cost%", file.getStringList("currencies.points.token.lore").get(2));
        assertEquals("<red>%amount% đá quý", file.getString("currencies.gem.token.name"), "an edited text is kept");
        assertEquals(0, OldDefaults.upgrade(file, OldDefaults.CONFIG), "nothing left to upgrade");
    }

    @Test
    void oldMessagesAreUpgradedEditedOnesKept() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        for (Map.Entry<String, List<Object>> e : OldDefaults.MESSAGES.entrySet()) old.set(e.getKey(), e.getValue().get(0));
        old.set("currency.given", "<gold>Token: %amount%");
        YamlConfiguration file = server(old, "messages.yml");
        assertEquals(2, OldDefaults.upgrade(file, OldDefaults.MESSAGES));
        YamlConfiguration bundled = bundled("messages.yml");
        assertEquals(bundled.getString("trade.not-enough-currency"), file.getString("trade.not-enough-currency"));
        assertEquals(bundled.getStringList("gui.editor.currency"), file.getStringList("gui.editor.currency"));
        assertEquals("<gold>Token: %amount%", file.getString("currency.given"));
    }

    @Test
    void bundledTextsUseTheColouredPlaceholders() throws Exception {
        YamlConfiguration messages = bundled("messages.yml");
        String notEnough = messages.getString("trade.not-enough-currency");
        assertTrue(notEnough.contains("%need%") && notEnough.contains("%have%") && !notEnough.contains("<text>%"), notEnough);
        assertEquals("<success>Đã nhận token %cost%", messages.getString("currency.given"));
        YamlConfiguration config = bundled("config.yml");
        for (String type : List.of("vault", "points", "gem")) {
            assertTrue(!config.getString("currencies." + type + ".token.name").contains("<accent>"), type);
        }
    }
}
