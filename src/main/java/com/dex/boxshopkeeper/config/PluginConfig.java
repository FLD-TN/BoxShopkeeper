package com.dex.boxshopkeeper.config;

import com.dex.boxshopkeeper.gui.style.Palette;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.util.SkullTextures;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable snapshot of {@code config.yml}. A reload builds a new instance. */
public final class PluginConfig {

    public static final int MAX_AMOUNT = 1_000_000;
    /** Keeps {@code max-per-click × 10^12} below 2^53, so a Vault amount is exact as a double. */
    public static final int MAX_PER_CLICK = 9_000;

    public enum Button {
        FILLER("filler.material", "GRAY_STAINED_GLASS_PANE"),
        FRAME("frame.material", "BLACK_STAINED_GLASS_PANE"),
        HEADER("materials.header", "CRAFTING_TABLE"),
        DETAIL_BACKGROUND("materials.detail-background", "BLACK_STAINED_GLASS_PANE"),
        BACK("materials.back", "ARROW"),
        PREVIOUS("materials.previous", "ARROW"),
        NEXT("materials.next", "ARROW"),
        PAGE("materials.page", "PAPER"),
        TRADE("materials.trade-button", "SMITHING_TABLE"),
        INFO("materials.info", "PAPER"),
        DEFAULT_SHOP_ICON("materials.default-shop-icon", "CHEST"),
        BROKEN_TRADE("materials.broken-trade", "BARRIER"),
        CREATE_SHOP("materials.create-shop", "WRITABLE_BOOK"),
        ADD_TRADE("materials.add-trade", "CRAFTING_TABLE"),
        RENAME("materials.rename", "NAME_TAG"),
        DESCRIPTION("materials.description", "BOOK"),
        ICON("materials.icon", "ITEM_FRAME"),
        MENU_ORDER("materials.menu-order", "COMPARATOR"),
        PERMISSION("materials.permission", "TRIPWIRE_HOOK"),
        DELETE("materials.delete", "BARRIER"),
        INGREDIENT_SLOT("materials.ingredient-slot", "WHITE_STAINED_GLASS_PANE"),
        RESULT_SLOT("materials.result-slot", "LIME_STAINED_GLASS_PANE"),
        RESULT_MARKER("materials.result-marker", "YELLOW_STAINED_GLASS_PANE"),
        SAVE("materials.save", "LIME_CONCRETE"),
        CANCEL("materials.cancel", "ARROW"),
        TOGGLE_ON("materials.toggle-on", "LIME_DYE"),
        TOGGLE_OFF("materials.toggle-off", "GRAY_DYE"),
        CONFIRM("materials.confirm", "LIME_DYE"),
        DENY("materials.deny", "GRAY_DYE");

        private final String path;
        private final String fallback;

        Button(String path, String fallback) {
            this.path = path;
            this.fallback = fallback;
        }
    }

    public enum SoundKey { CLICK, SUCCESS, REFUSED }

    public record SoundSpec(Sound sound, float volume, float pitch) {
        public void play(Player player) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public record Limits(int shops, int tradesPerShop, int ingredientsPerTrade, int displaySlotsPerTrade,
                         int templateBytes, int shopNameChars, int descriptionLines, int descriptionLineChars) {
    }

    /**
     * {@code currencies.<type>}: display name and the look of the editor token (update 01, §9).
     * {@code texture} is the Base64 head texture, set only when the material is a player head.
     */
    public record CurrencyStyle(String name, Material material, int customModelData, boolean glint,
                                String tokenName, List<String> tokenLore, String texture, TextColor color) {

        /**
         * {@code text} in the currency colour (1.3.1): parts without their own colour take it, and the item name is
         * not italic.
         */
        public Component paint(Component text) {
            return Component.text().color(color).decoration(TextDecoration.ITALIC, false).append(text).build();
        }
    }

    /** {@code currencies.<type>.color} when missing or invalid: &a, &e and the gem purple of BoxCore. */
    private static final Map<String, String> COLORS = Map.of(
            CurrencyCost.VAULT, "#55FF55",
            CurrencyCost.POINTS, "#FFFF55",
            CurrencyCost.GEM, "#8245F8");

    private static final Map<String, String> TOKEN_MATERIALS = Map.of(
            CurrencyCost.VAULT, "GOLD_NUGGET",
            CurrencyCost.POINTS, "EMERALD",
            CurrencyCost.GEM, "AMETHYST_SHARD");

    public final int cooldownTicks;
    public final int maxPerClick;
    public final boolean smallCaps;
    public final Palette palette;
    public final Limits limits;
    public final int navigationTicks;
    public final int commandTicks;
    public final int editorTicks;
    public final int backupsKeep;
    /** At most one backup per shop in this many seconds (a burst of editor saves keeps the oldest state). */
    public final int backupMinIntervalSeconds;
    public final boolean loggingEnabled;
    public final int logKeepDays;
    public final int logQueueSize;
    /** Problems found while reading, logged by the caller. */
    public final List<String> warnings = new ArrayList<>();
    public final List<String> notices = new ArrayList<>();
    private final Map<String, CurrencyStyle> currencies = new LinkedHashMap<>();
    private final Map<Button, Material> materials = new EnumMap<>(Button.class);
    private final Map<SoundKey, SoundSpec> sounds = new EnumMap<>(SoundKey.class);

    private PluginConfig(FileConfiguration c) {
        this.cooldownTicks = Math.max(0, c.getInt("trade.cooldown-ticks", 4));
        int perClick = c.getInt("trade.max-per-click", 2304);
        this.maxPerClick = clamp(perClick, 1, MAX_PER_CLICK);
        if (perClick != maxPerClick) {
            warnings.add("trade.max-per-click: " + perClick + " is outside 1.." + MAX_PER_CLICK + ", using " + maxPerClick);
        }
        this.smallCaps = false; // small caps are no longer used; gui.small-caps is ignored
        this.palette = Palette.load(c.getConfigurationSection("gui.palette"));
        this.limits = new Limits(
                clamp(c.getInt("limits.shops", 200), 1, 10_000),
                clamp(c.getInt("limits.trades-per-shop", 500), 1, 10_000),
                clamp(c.getInt("limits.ingredients-per-trade", 9), 1, 9),
                clamp(c.getInt("limits.display-slots-per-trade", 9), 1, 9),
                clamp(c.getInt("limits.template-bytes", 65_536), 1024, 1_048_576),
                clamp(c.getInt("limits.shop-name-chars", 32), 1, 48),
                clamp(c.getInt("limits.description-lines", 6), 0, 20),
                clamp(c.getInt("limits.description-line-chars", 48), 1, 60));
        this.navigationTicks = Math.max(0, c.getInt("rate-limit.navigation-ticks", 5));
        this.commandTicks = Math.max(0, c.getInt("rate-limit.command-ticks", 20));
        this.editorTicks = Math.max(0, c.getInt("rate-limit.editor-ticks", 2));
        this.backupsKeep = Math.max(0, c.getInt("backups.keep-per-shop", 20));
        this.backupMinIntervalSeconds = clamp(c.getInt("backups.min-interval-seconds", 60), 1, 86_400);
        this.loggingEnabled = c.getBoolean("logging.enabled", true);
        this.logKeepDays = Math.max(1, c.getInt("logging.keep-days", 30));
        this.logQueueSize = clamp(c.getInt("logging.queue-size", 10_000), 100, 1_000_000);

        ConfigurationSection gui = c.getConfigurationSection("gui");
        for (Button button : Button.values()) {
            String name = gui == null ? null : gui.getString(button.path);
            materials.put(button, material(name == null ? button.fallback : name, "gui." + button.path));
        }
        sounds.put(SoundKey.CLICK, sound(c, "sounds.click", "UI_BUTTON_CLICK", 0.4f, 1.0f));
        sounds.put(SoundKey.SUCCESS, sound(c, "sounds.success", "ENTITY_EXPERIENCE_ORB_PICKUP", 0.5f, 1.2f));
        sounds.put(SoundKey.REFUSED, sound(c, "sounds.refused", "BLOCK_NOTE_BLOCK_BASS", 0.5f, 0.8f));

        if (!c.contains("currencies", true)) {
            notices.add("config.yml has no 'currencies' section, using the bundled defaults");
        }
        int upgraded = OldDefaults.upgrade(c, OldDefaults.CONFIG);
        if (upgraded > 0) {
            notices.add("config.yml: " + upgraded + " currency text(s) still had the 1.3.0 default, using the 1.3.1 "
                    + "text (currency colours, vault as $, points as Xu)");
        }
        for (String type : CurrencyCost.ORDER) {
            currencies.put(type, currencyStyle(c, type));
        }
    }

    /** @throws IllegalArgumentException when a value cannot be parsed */
    public static PluginConfig load(FileConfiguration config) {
        return new PluginConfig(config);
    }

    public Material material(Button button) {
        return materials.get(button);
    }

    public SoundSpec sound(SoundKey key) {
        return sounds.get(key);
    }

    /** Style of a known currency type, or null. */
    public CurrencyStyle currency(String type) {
        return currencies.get(type);
    }

    /** Values missing from the owner's file come from the bundled defaults; invalid values fall back with a warning. */
    private CurrencyStyle currencyStyle(FileConfiguration c, String type) {
        String path = "currencies." + type;
        String name = c.getString(path + ".name", "");
        String fallback = TOKEN_MATERIALS.get(type);
        String materialName = c.getString(path + ".token.material", fallback);
        Material material = Material.matchMaterial(materialName == null ? "" : materialName.trim());
        if (material == null || !material.isItem() || material.isAir()) {
            warnings.add(path + ".token.material: unknown item material " + materialName + ", using " + fallback);
            material = Material.valueOf(fallback);
        }
        int customModelData = c.getInt(path + ".token.custom-model-data", 0);
        if (customModelData < 0) {
            warnings.add(path + ".token.custom-model-data: " + customModelData + " is negative, using 0");
            customModelData = 0;
        }
        String tokenName = c.getString(path + ".token.name", "<accent>%amount% %currency%");
        List<String> lore = c.isList(path + ".token.lore") ? c.getStringList(path + ".token.lore") : List.of();
        String rawTexture = c.getString(path + ".token.texture", "");
        String texture = SkullTextures.normalize(rawTexture);
        if (rawTexture != null && !rawTexture.isBlank()) {
            if (texture == null) {
                warnings.add(path + ".token.texture: not a head texture (Base64 value, skin URL or hash), ignored");
            } else if (material != Material.PLAYER_HEAD) {
                warnings.add(path + ".token.texture: ignored because the material is " + material + ", not PLAYER_HEAD");
                texture = null;
            }
        }
        TextColor color = currencyColor(c, type, warnings);
        return new CurrencyStyle(name == null ? "" : name, material, customModelData,
                c.getBoolean(path + ".token.glint", true), tokenName == null ? "" : tokenName, List.copyOf(lore),
                texture, color);
    }

    /** {@code currencies.<type>.color}; missing or invalid → the default of the type, with a warning when invalid. */
    static TextColor currencyColor(FileConfiguration c, String type, List<String> warnings) {
        String path = "currencies." + type + ".color";
        String fallback = COLORS.getOrDefault(type, "#FFFFFF");
        String raw = c.getString(path, fallback);
        TextColor color = raw == null ? null : TextColor.fromHexString(raw.trim());
        if (color == null) {
            warnings.add(path + ": " + raw + " is not a colour like '#55FF55', using " + fallback);
            color = TextColor.fromHexString(fallback);
        }
        return color;
    }

    private static Material material(String name, String path) {
        Material material = Material.matchMaterial(name.trim());
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException(path + ": unknown item material " + name);
        }
        return material;
    }

    private static SoundSpec sound(FileConfiguration c, String path, String sound, float volume, float pitch) {
        String name = c.getString(path + ".sound", sound).trim().toUpperCase(Locale.ROOT);
        Sound parsed;
        try {
            parsed = Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ".sound: unknown sound " + name);
        }
        float v = (float) Math.max(0.0, Math.min(0.6, c.getDouble(path + ".volume", volume)));
        float p = (float) Math.max(0.5, Math.min(2.0, c.getDouble(path + ".pitch", pitch)));
        return new SoundSpec(parsed, v, p);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
