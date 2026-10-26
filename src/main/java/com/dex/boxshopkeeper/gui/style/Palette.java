package com.dex.boxshopkeeper.gui.style;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;

/** The six colour tokens of §16.2, the only colours the plugin uses. */
public record Palette(TextColor text, TextColor muted, TextColor accent,
                      TextColor success, TextColor error, TextColor title) {

    public static Palette load(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("gui.palette is missing");
        }
        return new Palette(
                color(section, "text"),
                color(section, "muted"),
                color(section, "accent"),
                color(section, "success"),
                color(section, "error"),
                color(section, "title"));
    }

    private static TextColor color(ConfigurationSection section, String key) {
        String value = section.getString(key);
        TextColor color = value == null ? null : TextColor.fromHexString(value.trim());
        if (color == null) {
            throw new IllegalArgumentException("gui.palette." + key + " must be a #RRGGBB colour, got " + value);
        }
        return color;
    }
}
