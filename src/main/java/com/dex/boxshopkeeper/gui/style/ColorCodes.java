package com.dex.boxshopkeeper.gui.style;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colour codes in admin-written text (shop display names and descriptions):
 * {@code &0-&f}, {@code &k-&o}, {@code &r}, and hex colours as {@code &#81807F} or {@code <#81807F>}.
 */
public final class ColorCodes {

    private static final Pattern CODE = Pattern.compile("&#[0-9A-Fa-f]{6}|<#[0-9A-Fa-f]{6}>|&[0-9A-Fa-fK-Ok-oRr]");
    private static final Pattern HEX_TAG = Pattern.compile("<#([0-9A-Fa-f]{6})>");
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private ColorCodes() {
    }

    /** Converts the text between colour codes to small caps; the codes are kept as they are. */
    public static String smallCaps(String raw, boolean enabled) {
        if (!enabled || raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        Matcher matcher = CODE.matcher(raw);
        int last = 0;
        while (matcher.find()) {
            out.append(SmallCaps.apply(raw.substring(last, matcher.start())));
            out.append(matcher.group());
            last = matcher.end();
        }
        out.append(SmallCaps.apply(raw.substring(last)));
        return out.toString();
    }

    /** A non-italic component; text before any colour code keeps the surrounding colour. */
    public static Component parse(String raw) {
        String normalized = HEX_TAG.matcher(raw == null ? "" : raw).replaceAll("&#$1");
        return LEGACY.deserialize(normalized).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** The text without any colour code. */
    public static String strip(String raw) {
        return raw == null ? "" : CODE.matcher(raw).replaceAll("");
    }

    /** Visible characters (code points) once colour codes are removed. */
    public static int visibleLength(String raw) {
        String plain = strip(raw);
        return plain.codePointCount(0, plain.length());
    }
}
