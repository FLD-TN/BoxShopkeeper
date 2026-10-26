package com.dex.boxshopkeeper.config;

import com.dex.boxshopkeeper.gui.style.Text;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * All player-facing text (§16.7). Strings are converted to small caps once at load time;
 * placeholders are filled afterwards and therefore never converted.
 */
public final class Messages {

    public static final int TITLE_MAX = 32;
    private static final int ANTI_SPAM_TICKS = 20;
    /** Keys whose values are command syntax and must stay typeable. */
    private static final String RAW_PREFIX = "syntax.";

    private final Text text;
    private final Map<String, String> strings = new HashMap<>();
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> lastSent = new HashMap<>();
    private final Logger logger;

    private Messages(Text text, Logger logger) {
        this.text = text;
        this.logger = logger;
    }

    /**
     * @throws IOException                   when the file cannot be read
     * @throws InvalidConfigurationException when the file is not valid YAML
     */
    public static Messages load(File file, InputStream bundled, Text text, boolean smallCaps, Logger logger)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration defaults = new YamlConfiguration();
        if (bundled != null) {
            try (InputStreamReader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.isFile()) {
            yaml.load(file);
        }
        yaml.setDefaults(defaults);
        int upgraded = OldDefaults.upgrade(yaml, OldDefaults.MESSAGES);
        if (upgraded > 0) {
            logger.info("messages.yml: " + upgraded + " message(s) still had the 1.3.0 default, using the 1.3.1 text "
                    + "(amounts in the currency colour)");
        }

        Messages messages = new Messages(text, logger);
        for (String key : defaults.getKeys(true)) {
            messages.read(yaml, key, smallCaps);
        }
        for (String key : yaml.getKeys(true)) {
            messages.read(yaml, key, smallCaps);
        }
        return messages;
    }

    private void read(YamlConfiguration yaml, String key, boolean smallCaps) {
        if (yaml.isConfigurationSection(key)) {
            return;
        }
        if (key.startsWith(RAW_PREFIX)) {
            // Command syntax: shown as typed, never converted.
            strings.put(key, yaml.getString(key, ""));
            return;
        }
        if (yaml.isList(key)) {
            List<String> converted = new ArrayList<>();
            for (String line : yaml.getStringList(key)) {
                converted.addAll(Text.splitLines(Text.prepare(line, smallCaps)));
            }
            lists.put(key, List.copyOf(converted));
        } else {
            strings.put(key, Text.prepare(yaml.getString(key, ""), smallCaps));
        }
    }

    public Text text() {
        return text;
    }

    public String raw(String key) {
        String value = strings.get(key);
        if (value == null) {
            logger.warning("Missing message key " + key);
            return key;
        }
        return value;
    }

    public boolean isEmpty(String key) {
        String value = strings.get(key);
        return value == null || value.isEmpty();
    }

    public Component line(String key, Ph... placeholders) {
        return text.parse(raw(key), placeholders);
    }

    public List<Component> lines(String key, Ph... placeholders) {
        List<String> raw = lists.get(key);
        if (raw == null) {
            String single = strings.get(key);
            if (single == null) {
                logger.warning("Missing message key " + key);
                return List.of();
            }
            raw = single.isEmpty() ? List.of() : Text.splitLines(single);
        }
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(text.parse(line, placeholders));
        }
        return out;
    }

    /** An inventory title in the {@code title} colour, cut to {@link #TITLE_MAX} visible characters. */
    public Component title(String key, String shopName, Ph... extra) {
        return title(key, Component.text(shopName == null ? "" : shopName), extra);
    }

    /** As {@link #title(String, String, Ph...)}; a coloured name too long to fit is cut as plain text. */
    public Component title(String key, Component shopName, Ph... extra) {
        String template = raw(key);
        Ph[] all = new Ph[extra.length + 1];
        System.arraycopy(extra, 0, all, 1, extra.length);
        all[0] = Ph.of("shop", "");
        int available = Math.max(1, TITLE_MAX - Text.visibleLength(text.parse(template, all)));
        Component name = Text.visibleLength(shopName) <= available
                ? shopName
                : Component.text(Text.cut(Text.plain(shopName), available));
        all[0] = Ph.of("shop", name);
        Component title = text.parse(template, all).colorIfAbsent(text.palette().title());
        String plain = Text.plain(title);
        if (plain.codePointCount(0, plain.length()) > TITLE_MAX) {
            return Component.text(Text.cut(plain, TITLE_MAX), text.palette().title());
        }
        return title;
    }

    /** Sends a prefixed chat message; the same key reaches a player at most once per 20 ticks. */
    public void send(CommandSender sender, String key, Ph... placeholders) {
        if (isEmpty(key)) {
            return;
        }
        if (sender instanceof Player player && !allow(player.getUniqueId(), key)) {
            return;
        }
        Component message = line(key, placeholders);
        if (!isEmpty("prefix")) {
            message = line("prefix").append(message);
        }
        sender.sendMessage(message);
    }

    /**
     * Sends a prefixed chat message without anti-spam, for messages that differ every time (a
     * successful trade); their rate is already bounded by the action's own cooldown.
     */
    public void sendAlways(CommandSender sender, String key, Ph... placeholders) {
        if (isEmpty(key)) {
            return;
        }
        Component message = line(key, placeholders);
        if (!isEmpty("prefix")) {
            message = line("prefix").append(message);
        }
        sender.sendMessage(message);
    }

    /** Sends a message without prefix and without anti-spam (help and list output). */
    public void sendPlain(CommandSender sender, String key, Ph... placeholders) {
        if (isEmpty(key)) {
            return;
        }
        sender.sendMessage(line(key, placeholders));
    }

    private boolean allow(UUID player, String key) {
        int now = Bukkit.getCurrentTick();
        Map<String, Integer> sent = lastSent.computeIfAbsent(player, id -> new HashMap<>());
        Integer last = sent.get(key);
        if (last != null && now - last < ANTI_SPAM_TICKS) {
            return false;
        }
        sent.put(key, now);
        return true;
    }

    public void forget(UUID player) {
        lastSent.remove(player);
    }

    public int trackedPlayers() {
        return lastSent.size();
    }
}
