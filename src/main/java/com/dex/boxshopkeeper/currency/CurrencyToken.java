package com.dex.boxshopkeeper.currency;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.gui.style.Text;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.util.Numbers;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The editor-only currency token (update 01, §4). Its two PDC keys are the only data that identify
 * it; name and lore are display only and never read back.
 */
public final class CurrencyToken {

    /** Contents of a token's PDC. {@code amount} is -1 when missing or of the wrong type. */
    public record Data(String type, long amount) {
    }

    private final BoxShopkeeperPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey amountKey;
    /** Built looks per type, for the configuration they were built from. */
    private final Map<String, ItemStack> looks = new HashMap<>();
    private PluginConfig looksFor;

    public CurrencyToken(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "currency");
        this.amountKey = new NamespacedKey(plugin, "currency_amount");
    }

    /** A new token with the configured look and its PDC keys. */
    public ItemStack create(String type, long amount) {
        ItemStack item = look(type);
        ItemMeta meta = item.getItemMeta();
        PluginConfig.CurrencyStyle style = plugin.settings().currency(type);
        Ph[] placeholders = placeholders(type, amount);
        meta.displayName(placeholders[2].value());
        List<Component> lore = new ArrayList<>(style.tokenLore().size());
        for (String line : style.tokenLore()) {
            for (String part : Text.splitLines(Text.prepare(line, plugin.settings().smallCaps))) {
                lore.add(plugin.messages().text().parse(part, placeholders));
            }
        }
        meta.lore(lore.isEmpty() ? null : lore);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.LONG, amount);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The token's material, custom model data, glint and head texture, without name, lore or PDC
     * keys: the display item of a cost in the trade detail and the editor, never usable as a token.
     * Returns a fresh copy; the look itself is built once per type and configuration.
     */
    public ItemStack look(String type) {
        PluginConfig settings = plugin.settings();
        if (settings != looksFor) {
            looks.clear();
            looksFor = settings;
        }
        return looks.computeIfAbsent(type, key -> build(settings.currency(key))).clone();
    }

    private static ItemStack build(PluginConfig.CurrencyStyle style) {
        ItemStack item = new ItemStack(style == null ? Material.PAPER : style.material());
        if (style == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (style.customModelData() > 0) {
            meta.setCustomModelData(style.customModelData());
        }
        if (style.glint()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (style.texture() != null && meta instanceof SkullMeta skull) {
            // Same texture → same profile id, so identical tokens stack and the client caches the skin.
            UUID id = UUID.nameUUIDFromBytes(style.texture().getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(id, null);
            profile.setProperty(new ProfileProperty("textures", style.texture()));
            skull.setPlayerProfile(profile);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** The token data of an item, or null when the item is not a token. */
    public Data read(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        if (!data.has(typeKey)) {
            return null;
        }
        String type = data.has(typeKey, PersistentDataType.STRING) ? data.get(typeKey, PersistentDataType.STRING) : null;
        Long amount = data.has(amountKey, PersistentDataType.LONG) ? data.get(amountKey, PersistentDataType.LONG) : null;
        return new Data(type, amount == null ? -1 : amount);
    }

    /**
     * {@code %amount%} (grouped), {@code %currency%} (the coloured name) and {@code %cost%}: the
     * amount shown the way {@code currencies.<type>.token.name} formats it, in the currency colour
     * ({@code currencies.<type>.color}), e.g. "100 Gem" in purple or "1.250$" in green.
     */
    public Ph[] placeholders(String type, long amount) {
        Ph[] base = {Ph.of("amount", Numbers.group(amount)), Ph.of("currency", plugin.currencies().displayName(type))};
        PluginConfig.CurrencyStyle style = plugin.settings().currency(type);
        Component cost = style == null ? parse("%amount% %currency%", base) : style.paint(parse(style.tokenName(), base));
        return new Ph[]{base[0], base[1], Ph.of("cost", cost)};
    }

    /** {@code %cost%} alone: the item name of a cost in the trade detail and the editor. */
    public Component costName(String type, long amount) {
        return placeholders(type, amount)[2].value();
    }

    private Component parse(String raw, Ph[] placeholders) {
        Text text = plugin.messages().text();
        return text.parse(Text.prepare(raw, plugin.settings().smallCaps), placeholders);
    }
}
