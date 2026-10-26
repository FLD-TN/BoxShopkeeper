package com.dex.boxshopkeeper.gui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallCapsTest {

    @Test
    void convertsVietnameseKeepingDiacritics() {
        assertEquals("ᴄʜᴜộᴛ ᴛʀáɪ", SmallCaps.apply("Chuột trái"));
        assertEquals("ᴄʜế ᴛạᴏ", SmallCaps.apply("Chế Tạo"));
    }

    @Test
    void keepsTagsAndPlaceholders() {
        assertEquals("<accent>ᴛʀᴀᴏ đổɪ %item%", SmallCaps.apply("<accent>Trao đổi %item%"));
        assertEquals("<muted>ᴄầɴ <text>%amount%", SmallCaps.apply("<muted>Cần <text>%amount%"));
    }

    @Test
    void disabledLeavesTextUnchanged() {
        assertEquals("Chuột trái", SmallCaps.apply("Chuột trái", false));
        assertEquals("<accent>Trao đổi %item%", SmallCaps.apply("<accent>Trao đổi %item%", false));
    }

    @Test
    void leavesDigitsSymbolsAndLoneMarkers() {
        assertEquals("x128 ✔ ✘ • › ─ 100%", SmallCaps.apply("x128 ✔ ✘ • › ─ 100%"));
        assertEquals("ᴀ < ʙ", SmallCaps.apply("a < b"));
    }

    @Test
    void escapedTagIsConvertedAsText() {
        assertEquals("\\<ᴛêɴ>", SmallCaps.apply("\\<tên>"));
    }
}
