package com.dex.boxshopkeeper.gui.style;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds non-italic components from tagged strings. The parser knows the palette tags
 * ({@code <text> <muted> <accent> <success> <error>}) and the MiniMessage styling tags: colours
 * ({@code <#FF8800>}, {@code <red>}, {@code <color:#FF8800>}), decorations ({@code <bold>}),
 * {@code <gradient>}, {@code <rainbow>}, {@code <transition>} and {@code <reset>}. Click, hover and
 * other interactive tags stay literal. Placeholders are replaced after parsing, in one pass, with
 * the given components (never parsed as tags).
 */
public final class Text {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z0-9_-]+%");
    private static final Pattern LEGACY = Pattern.compile("&#([0-9A-Fa-f]{6})|&([0-9A-Fa-fK-Ok-oRr])");
    private static final String[] LEGACY_TAGS = new String[128];

    static {
        String codes = "0123456789abcdefklmnor";
        String[] tags = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
                "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white",
                "obfuscated", "bold", "strikethrough", "underlined", "italic", "reset"};
        for (int i = 0; i < codes.length(); i++) {
            LEGACY_TAGS[codes.charAt(i)] = "<" + tags[i] + ">";
            LEGACY_TAGS[Character.toUpperCase(codes.charAt(i))] = "<" + tags[i] + ">";
        }
    }
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final int MAX_CACHED = 2048;

    private final Palette palette;
    private final MiniMessage parser;
    /** Parsed templates by source string. Main thread only; a new Text (reload) starts empty. */
    private final Map<String, Component> parsed = new ConcurrentHashMap<>();

    public Text(Palette palette) {
        this.palette = palette;
        TagResolver tags = TagResolver.resolver(
                TagResolver.resolver("text", Tag.styling(palette.text())),
                TagResolver.resolver("muted", Tag.styling(palette.muted())),
                TagResolver.resolver("accent", Tag.styling(palette.accent())),
                TagResolver.resolver("success", Tag.styling(palette.success())),
                TagResolver.resolver("error", Tag.styling(palette.error())),
                StandardTags.color(),
                StandardTags.decorations(),
                StandardTags.gradient(),
                StandardTags.rainbow(),
                StandardTags.transition(),
                StandardTags.reset());
        this.parser = MiniMessage.builder().tags(tags).build();
    }

    public Palette palette() {
        return palette;
    }

    public Component parse(String input, Ph... placeholders) {
        // Inputs are message and config templates (a bounded set); components are immutable, so the
        // parsed template is shared and only the placeholder pass runs per call.
        Component component = parsed.get(input);
        if (component == null) {
            component = parser.deserialize(input);
            if (parsed.size() >= MAX_CACHED) {
                parsed.clear();
            }
            parsed.put(input, component);
        }
        if (placeholders.length > 0 && input.indexOf('%') >= 0) {
            Map<String, Component> values = new HashMap<>();
            for (Ph ph : placeholders) {
                values.put(ph.key(), ph.value());
            }
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(PLACEHOLDER)
                    .replacement((match, builder) -> {
                        Component value = values.get(match.group());
                        return value != null ? value : builder;
                    })
                    .build());
        }
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Prepares a configured string before small caps and parsing: legacy codes ({@code &a},
     * {@code &l}, {@code &#FF8800}) become tags and a typed backslash-n becomes a line break.
     */
    public static String prepare(String raw, boolean smallCaps) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        Matcher matcher = LEGACY.matcher(raw.replace("\\n", "\n"));
        StringBuilder out = new StringBuilder(raw.length() + 16);
        while (matcher.find()) {
            String tag = matcher.group(1) != null ? "<#" + matcher.group(1) + ">" : LEGACY_TAGS[matcher.group(2).charAt(0)];
            matcher.appendReplacement(out, Matcher.quoteReplacement(tag));
        }
        matcher.appendTail(out);
        return SmallCaps.apply(out.toString(), smallCaps);
    }

    /** Escapes text from another plugin so it is never read as tags. */
    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** Splits a prepared string at its line breaks. */
    public static List<String> splitLines(String prepared) {
        if (prepared.indexOf('\n') < 0) {
            return List.of(prepared);
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = prepared.indexOf('\n'); i >= 0; i = prepared.indexOf('\n', start)) {
            lines.add(prepared.substring(start, i));
            start = i + 1;
        }
        lines.add(prepared.substring(start));
        return lines;
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    /** Number of visible characters (code points) of a component. */
    public static int visibleLength(Component component) {
        String plain = plain(component);
        return plain.codePointCount(0, plain.length());
    }

    /** Cuts a plain string to {@code max} code points, ending with an ellipsis when cut. */
    public static String cut(String value, int max) {
        int length = value.codePointCount(0, value.length());
        if (length <= max) {
            return value;
        }
        if (max <= 0) {
            return "";
        }
        int end = value.offsetByCodePoints(0, max - 1);
        return value.substring(0, end) + "…";
    }

    /** A placeholder name ({@code %name%}) and its value. */
    public record Ph(String key, Component value) {

        public static Ph of(String name, String value) {
            return new Ph("%" + name + "%", Component.text(value));
        }

        public static Ph of(String name, long value) {
            return new Ph("%" + name + "%", Component.text(value));
        }

        public static Ph of(String name, Component value) {
            return new Ph("%" + name + "%", value);
        }
    }
}
