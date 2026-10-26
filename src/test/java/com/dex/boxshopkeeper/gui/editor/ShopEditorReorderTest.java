package com.dex.boxshopkeeper.gui.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopEditorReorderTest {

    private static List<String> list() {
        return new ArrayList<>(List.of("a", "b", "c", "d", "e"));
    }

    @Test
    void moveUpTakesTheTargetPosition() {
        List<String> list = list();
        assertEquals(1, ShopEditor.reorder(list, 4, 1));
        assertEquals(List.of("a", "e", "b", "c", "d"), list);
    }

    @Test
    void moveDownTakesTheTargetPosition() {
        List<String> list = list();
        assertEquals(3, ShopEditor.reorder(list, 0, 3));
        assertEquals(List.of("b", "c", "d", "a", "e"), list);
    }

    @Test
    void moveToTheEnd() {
        List<String> list = list();
        assertEquals(4, ShopEditor.reorder(list, 1, -1));
        assertEquals(List.of("a", "c", "d", "e", "b"), list);
        assertEquals(4, ShopEditor.reorder(list, 0, 4));
        assertEquals(List.of("c", "d", "e", "b", "a"), list);
    }

    @Test
    void sameIndexChangesNothing() {
        List<String> list = list();
        assertEquals(2, ShopEditor.reorder(list, 2, 2));
        assertEquals(list(), list);
    }
}
