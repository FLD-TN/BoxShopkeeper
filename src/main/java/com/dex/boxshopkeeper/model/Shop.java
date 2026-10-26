package com.dex.boxshopkeeper.model;

import com.dex.boxshopkeeper.gui.style.ColorCodes;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A named collection of trades. Immutable: an edit builds a new instance with a new revision,
 * which lets open GUIs detect that the definition changed (§5.5 step 1).
 */
public final class Shop {

    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final ItemStack icon;
    private final String iconEncoded;
    private final int menuOrder;
    private final boolean permissionRequired;
    private final List<Trade> trades;

    private final long revision;
    private final Component styledName;
    private final String plainName;
    private final List<Component> styledDescription;
    private final List<Trade> usableTrades;

    private Shop(Builder b, long revision, boolean smallCaps) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.description = List.copyOf(b.description);
        this.icon = b.icon;
        this.iconEncoded = b.iconEncoded;
        this.menuOrder = b.menuOrder;
        this.permissionRequired = b.permissionRequired;
        this.trades = List.copyOf(b.trades);
        this.revision = revision;
        this.styledName = ColorCodes.parse(ColorCodes.smallCaps(displayName, smallCaps));
        this.plainName = ColorCodes.strip(displayName);
        List<Component> styled = new ArrayList<>(description.size());
        for (String line : description) {
            styled.add(ColorCodes.parse(ColorCodes.smallCaps(line, smallCaps)));
        }
        this.styledDescription = List.copyOf(styled);
        this.usableTrades = trades.stream().filter(Trade::usable).toList();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public Builder toBuilder() {
        Builder b = new Builder(id);
        b.displayName = displayName;
        b.description = new ArrayList<>(description);
        b.icon = icon;
        b.iconEncoded = iconEncoded;
        b.menuOrder = menuOrder;
        b.permissionRequired = permissionRequired;
        b.trades = new ArrayList<>(trades);
        return b;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> description() {
        return description;
    }

    /** Icon template (amount 1), or null to use the default material. Never mutate. */
    public ItemStack icon() {
        return icon;
    }

    public String iconEncoded() {
        return iconEncoded;
    }

    public int menuOrder() {
        return menuOrder;
    }

    public boolean permissionRequired() {
        return permissionRequired;
    }

    public String permission() {
        return "chetao.shop." + id;
    }

    /** All trades in GUI order, including disabled and broken ones. */
    public List<Trade> trades() {
        return trades;
    }

    /** Trades players can see, in GUI order. */
    public List<Trade> usableTrades() {
        return usableTrades;
    }

    public long revision() {
        return revision;
    }

    /** Display name with its colour codes applied, after small-caps conversion (cached). */
    public Component styledName() {
        return styledName;
    }

    /** Display name without colour codes. */
    public String plainName() {
        return plainName;
    }

    /** Description lines with their colour codes applied, after small-caps conversion (cached). */
    public List<Component> styledDescription() {
        return styledDescription;
    }

    public Trade trade(String tradeId) {
        for (Trade trade : trades) {
            if (trade.id().equals(tradeId)) {
                return trade;
            }
        }
        return null;
    }

    public static final class Builder {
        private final String id;
        private String displayName;
        private List<String> description = new ArrayList<>();
        private ItemStack icon;
        private String iconEncoded;
        private int menuOrder;
        private boolean permissionRequired;
        private List<Trade> trades = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        public String id() {
            return id;
        }

        public Builder displayName(String value) {
            this.displayName = value;
            return this;
        }

        public Builder description(List<String> value) {
            this.description = new ArrayList<>(value);
            return this;
        }

        public Builder icon(ItemStack template, String encoded) {
            this.icon = template;
            this.iconEncoded = encoded;
            return this;
        }

        public Builder menuOrder(int value) {
            this.menuOrder = value;
            return this;
        }

        public Builder permissionRequired(boolean value) {
            this.permissionRequired = value;
            return this;
        }

        public Builder trades(List<Trade> value) {
            this.trades = new ArrayList<>(value);
            return this;
        }

        public List<Trade> trades() {
            return trades;
        }

        public Shop build(long revision, boolean smallCaps) {
            return new Shop(this, revision, smallCaps);
        }
    }
}
