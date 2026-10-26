package com.dex.boxshopkeeper.gui.style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts plugin text to small caps (§16.1). Palette tags ({@code <accent>}), placeholders
 * ({@code %item%}) and escaped characters are copied unchanged. Letters without a small-caps
 * form (Vietnamese letters with diacritics, digits, symbols) are only lower-cased.
 */
public final class SmallCaps {

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final Pattern TAG = Pattern.compile("</?[A-Za-z0-9_:#!?.-]+>");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z0-9_-]+%");

    private SmallCaps() {
    }

    public static String apply(String input, boolean enabled) {
        return enabled ? apply(input) : input;
    }

    public static String apply(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        Matcher tag = TAG.matcher(input);
        Matcher placeholder = PLACEHOLDER.matcher(input);
        int i = 0;
        int length = input.length();
        while (i < length) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < length) {
                out.append(c).append(input.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '<' && tag.region(i, length).lookingAt()) {
                out.append(input, i, tag.end());
                i = tag.end();
                continue;
            }
            if (c == '%' && placeholder.region(i, length).lookingAt()) {
                out.append(input, i, placeholder.end());
                i = placeholder.end();
                continue;
            }
            out.append(map(c));
            i++;
        }
        return out.toString();
    }

    private static char map(char c) {
        char lower = Character.toLowerCase(c);
        int index = FROM.indexOf(lower);
        return index >= 0 ? TO.charAt(index) : lower;
    }
}
