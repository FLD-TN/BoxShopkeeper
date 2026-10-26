package com.dex.boxshopkeeper.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkullTexturesTest {

    private static final String HASH = "e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852";
    private static final String URL = "http://textures.minecraft.net/texture/" + HASH;

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    @Test
    void base64ValueIsKeptAsIs() {
        String value = Base64.getEncoder().encodeToString(
                ("{\"textures\":{\"SKIN\":{\"url\":\"" + URL + "\"}}}").getBytes(StandardCharsets.UTF_8));
        assertEquals(value, SkullTextures.normalize("  " + value + " "));
    }

    @Test
    void urlAndHashBecomeTheSameValue() {
        String fromUrl = SkullTextures.normalize(URL);
        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"" + URL + "\"}}}", decode(fromUrl));
        assertEquals(fromUrl, SkullTextures.normalize(HASH));
        assertEquals(fromUrl, SkullTextures.normalize(HASH.toUpperCase()));
    }

    @Test
    void blankOrInvalidIsNull() {
        assertNull(SkullTextures.normalize(null));
        assertNull(SkullTextures.normalize(""));
        assertNull(SkullTextures.normalize("   "));
        assertNull(SkullTextures.normalize("not a texture!"));
        assertNull(SkullTextures.normalize(Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8))));
    }
}
