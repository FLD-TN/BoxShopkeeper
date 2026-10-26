package com.dex.boxshopkeeper.gui.style;

import com.dex.boxshopkeeper.gui.style.Text.Ph;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {

    private final Text text = new Text(new Palette(
            TextColor.fromHexString("#E6E1D6"), TextColor.fromHexString("#8F8A80"),
            TextColor.fromHexString("#D9B26F"), TextColor.fromHexString("#9BBF8A"),
            TextColor.fromHexString("#CC7A6F"), TextColor.fromHexString("#3F3B36")));

    @Test
    void isNeverItalic() {
        Component line = text.parse("<muted>Cần <text>64");
        assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        assertEquals("Cần 64", Text.plain(line));
    }

    @Test
    void placeholderValuesAreNotParsedOrReplacedAgain() {
        Component line = text.parse("<error>✘ <text>%shop%", Ph.of("shop", "<accent>x %player%"), Ph.of("player", "y"));
        assertEquals("✘ <accent>x %player%", Text.plain(line));
    }

    @Test
    void interactiveTagsStayLiteral() {
        assertEquals("<click:run_command:/op x>a", Text.plain(text.parse("<click:run_command:/op x>a")));
        assertEquals("<hover:show_text:x>a", Text.plain(text.parse("<hover:show_text:x>a")));
    }

    @Test
    void rgbNamedColoursAndDecorationsAreParsed() {
        Component hex = text.parse("<#FF8800>Vàng");
        assertEquals("Vàng", Text.plain(hex));
        assertEquals(TextColor.fromHexString("#FF8800"), hex.children().isEmpty() ? hex.color() : hex.children().get(0).color());
        assertEquals("ab", Text.plain(text.parse("<red>a<bold>b")));
        assertEquals("Gem", Text.plain(text.parse("<gradient:#FF0000:#00FF00>Gem</gradient>")));
        Component italic = text.parse("<italic>x");
        assertEquals(TextDecoration.State.TRUE, italic.decoration(TextDecoration.ITALIC));
    }

    @Test
    void legacyCodesBecomeTags() {
        assertEquals("<green>a<bold>b<#FF8800>c<reset>d", Text.prepare("&aa&lb&#FF8800c&rd", false));
        assertEquals("R & D", Text.prepare("R & D", false));
        assertEquals("abc", Text.plain(text.parse(Text.prepare("&aa&lb&#FF8800c", false))));
    }

    @Test
    void smallCapsKeepsHexTags() {
        assertEquals("<#FF8800>ɢᴇᴍ <gradient:#AA00FF:#00FFAA>xᴜ</gradient>",
                Text.prepare("<#FF8800>Gem <gradient:#AA00FF:#00FFAA>Xu</gradient>", true));
        assertEquals("<light_purple>ᴀ", Text.prepare("&da", true));
    }

    @Test
    void typedNewlinesSplitLines() {
        assertEquals(java.util.List.of("", "<muted>Nguyên liệu"), Text.splitLines(Text.prepare("\\n<muted>Nguyên liệu", false)));
        assertEquals(java.util.List.of("a", "b"), Text.splitLines(Text.prepare("a\nb", false)));
        assertEquals(java.util.List.of("a"), Text.splitLines("a"));
    }

    @Test
    void cutAddsEllipsis() {
        assertEquals("abcd", Text.cut("abcd", 4));
        assertEquals("abc…", Text.cut("abcde", 4));
    }
}
