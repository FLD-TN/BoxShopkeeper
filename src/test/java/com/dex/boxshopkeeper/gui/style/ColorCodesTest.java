package com.dex.boxshopkeeper.gui.style;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorCodesTest {

    private static List<Component> flatten(Component component) {
        List<Component> all = new ArrayList<>();
        all.add(component);
        for (Component child : component.children()) {
            all.addAll(flatten(child));
        }
        return all;
    }

    private static boolean hasColor(Component component, TextColor color) {
        return flatten(component).stream().anyMatch(c -> color.equals(c.color()));
    }

    @Test
    void stripsEveryCodeForm() {
        assertEquals("Bộ Than", ColorCodes.strip("&a&lBộ &#81807FThan"));
        assertEquals("Bộ Than", ColorCodes.strip("<#81807F>Bộ Than"));
        assertEquals(7, ColorCodes.visibleLength("&6Bộ <#81807F>Than"));
    }

    @Test
    void smallCapsLeavesCodesAlone() {
        assertEquals("&aᴄʜế &#81807Fᴛạᴏ", ColorCodes.smallCaps("&aChế &#81807FTạo", true));
        assertEquals("<#81807F>ʙộ ᴛʜᴀɴ &l!", ColorCodes.smallCaps("<#81807F>Bộ Than &l!", true));
        assertEquals("&aChế", ColorCodes.smallCaps("&aChế", false));
    }

    @Test
    void parsesLegacyAndHexColours() {
        Component legacy = ColorCodes.parse("&aXanh");
        assertEquals("Xanh", Text.plain(legacy));
        assertTrue(hasColor(legacy, NamedTextColor.GREEN));

        Component hexTag = ColorCodes.parse("<#81807F>Xám");
        assertEquals("Xám", Text.plain(hexTag));
        assertTrue(hasColor(hexTag, TextColor.fromHexString("#81807F")));

        Component hexAmp = ColorCodes.parse("&#81807FXám");
        assertTrue(hasColor(hexAmp, TextColor.fromHexString("#81807F")));
    }

    @Test
    void isNotItalicAndKeepsPlainText() {
        Component plain = ColorCodes.parse("Bộ Than <red> 100%");
        assertEquals("Bộ Than <red> 100%", Text.plain(plain));
        assertEquals(TextDecoration.State.FALSE, plain.decoration(TextDecoration.ITALIC));
    }
}
