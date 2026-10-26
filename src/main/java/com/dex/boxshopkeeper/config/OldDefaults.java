package com.dex.boxshopkeeper.config;

import org.bukkit.configuration.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Bundled texts that a newer version rewrote. A file still holding one of them unchanged gets the new bundled
 * value; a text the owner edited is kept. Values are a String or a List of Strings.
 */
public final class OldDefaults {

    private static final String TOKEN_NAME = "<accent>%amount% %currency%";
    private static final List<String> TOKEN_LORE = List.of(
            "<muted>Token tiền tệ cho trình chỉnh sửa",
            "<muted>Đặt vào ô nguyên liệu của một trao đổi",
            "<muted>để yêu cầu <text>%amount% %currency%");

    /** config.yml, 1.3.0 → 1.3.1: currency colours, vault shown as $ and points as Xu. */
    public static final Map<String, List<Object>> CONFIG = Map.ofEntries(
            Map.entry("currencies.vault.name", List.of("Xu")),
            Map.entry("currencies.points.name", List.of("Points")),
            Map.entry("currencies.vault.token.name", List.of(TOKEN_NAME)),
            Map.entry("currencies.points.token.name", List.of(TOKEN_NAME)),
            Map.entry("currencies.gem.token.name", List.of(TOKEN_NAME)),
            Map.entry("currencies.vault.token.lore", List.of(TOKEN_LORE)),
            Map.entry("currencies.points.token.lore", List.of(TOKEN_LORE)),
            Map.entry("currencies.gem.token.lore", List.of(TOKEN_LORE)));

    /** messages.yml, 1.3.0 → 1.3.1: amounts of money in the currency colour. */
    public static final Map<String, List<Object>> MESSAGES = Map.ofEntries(
            Map.entry("trade.not-enough-currency",
                    List.of("<error>Không đủ <text>%currency%<error>, cần <text>%need% <error>đang có <text>%have%")),
            Map.entry("currency.given", List.of("<success>Đã nhận token <text>%amount% %currency%")),
            Map.entry("gui.editor.currency", List.of(List.of(
                    "<muted>Tiền tệ <text>%currency%",
                    "<muted>Số tiền <text>%amount%",
                    "<muted>Tạo token mới để đổi số tiền",
                    "<muted>Chuột giữa hoặc Q để xóa"))));

    private OldDefaults() {
    }

    /**
     * Replaces, in {@code file}, every value still equal to an old default by the bundled one
     * ({@code file.getDefaults()}).
     * @return how many values were replaced
     */
    public static int upgrade(Configuration file, Map<String, List<Object>> old) {
        Configuration bundled = file.getDefaults();
        if (bundled == null) {
            return 0;
        }
        int upgraded = 0;
        for (Map.Entry<String, List<Object>> e : old.entrySet()) {
            String path = e.getKey();
            if (!file.contains(path, true) || !bundled.contains(path)) {
                continue;
            }
            Object value = file.isList(path) ? file.getStringList(path) : file.get(path);
            if (e.getValue().contains(value)) {
                file.set(path, bundled.get(path));
                upgraded++;
            }
        }
        return upgraded;
    }
}
