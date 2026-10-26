package com.dex.boxshopkeeper.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/** Player-head texture values as typed in a config. Pure. */
public final class SkullTextures {

    private static final String TEXTURE_URL = "http://textures.minecraft.net/texture/";
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32,}");
    private static final Pattern URL = Pattern.compile("https?://\\S+");

    private SkullTextures() {
    }

    /**
     * The Base64 {@code textures} property value for a head. Accepts the Base64 value itself (as in
     * MMOItems or minecraft-heads.com "Value"), a skin URL, or the bare texture hash.
     * @return the Base64 value, or null when {@code input} is blank or not a texture
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim();
        if (HASH.matcher(value).matches()) {
            return fromUrl(TEXTURE_URL + value.toLowerCase());
        }
        if (URL.matcher(value).matches()) {
            return fromUrl(value);
        }
        try {
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            return json.contains("\"textures\"") && json.contains("\"url\"") ? value : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String fromUrl(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
