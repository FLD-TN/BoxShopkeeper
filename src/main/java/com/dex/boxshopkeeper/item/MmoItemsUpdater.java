package com.dex.boxshopkeeper.item;

import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Trade;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * {@code /chetao update-mmoitems}: replaces every MMOItems template of a shop (ingredients and
 * results) with a fresh item built from the current MMOItems template, keeping the amounts. Items of
 * other plugins and plain items are left untouched; a TYPE:ID that MMOItems no longer knows is kept
 * as it is and reported.
 */
public final class MmoItemsUpdater {

    /** Reads and builds MMOItems items; {@link MmoItemsHook} on a server, a fake in tests. */
    public interface Source {
        /** {@code TYPE:ID}, or null when the item is not an MMOItems item. */
        String key(ItemStack item);

        /** A new item (amount 1) from the current template, or null when TYPE:ID is unknown. */
        ItemStack fresh(String key);
    }

    /** A TYPE:ID that could not be rebuilt, and where it is used. */
    public record Problem(String key, String tradeId, String reason) {
    }

    /** The trades after the update and what changed. */
    public record Result(List<Trade> trades, int items, int tradesChanged, List<Problem> problems) {
        public boolean changed() {
            return items > 0;
        }
    }

    private final Source source;
    private final Function<ItemStack, String> encoder;
    private final int maxBytes;

    /**
     * @param encoder  item → stored Base64 ({@link ItemCodec#encode} on a server)
     * @param maxBytes {@code limits.template-bytes}; a bigger rebuilt item is reported, not stored
     */
    public MmoItemsUpdater(Source source, Function<ItemStack, String> encoder, int maxBytes) {
        this.source = source;
        this.encoder = encoder;
        this.maxBytes = maxBytes;
    }

    /**
     * The updater backed by MMOItems, or null when MMOItems is not enabled or its API cannot be
     * loaded (incompatible version).
     */
    public static MmoItemsUpdater create(int maxBytes) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return null;
        }
        try {
            return new MmoItemsUpdater(newHook(), ItemCodec::encode, maxBytes);
        } catch (LinkageError e) {
            return null;
        }
    }

    // A separate method so MmoItemsHook is resolved only after the MMOItems check.
    private static Source newHook() {
        return new MmoItemsHook();
    }

    public Result update(List<Trade> trades) {
        List<Trade> out = new ArrayList<>(trades.size());
        List<Problem> problems = new ArrayList<>();
        int items = 0;
        int changedTrades = 0;
        for (Trade trade : trades) {
            int before = items;
            List<Ingredient> ingredients = new ArrayList<>(trade.ingredients().size());
            for (Ingredient ingredient : trade.ingredients()) {
                Fresh fresh = rebuild(ingredient.template(), ingredient.encoded(), trade.id(), problems);
                if (fresh == null) {
                    ingredients.add(ingredient);
                } else {
                    ingredients.add(new Ingredient(fresh.item(), fresh.encoded(), ingredient.amount()));
                    items++;
                }
            }
            ResultSpec result = trade.result();
            if (result != null) {
                Fresh fresh = rebuild(result.template(), result.encoded(), trade.id(), problems);
                if (fresh != null) {
                    result = new ResultSpec(fresh.item(), fresh.encoded(), result.amount());
                    items++;
                }
            }
            if (items == before) {
                out.add(trade);
            } else {
                changedTrades++;
                out.add(new Trade(trade.id(), trade.slot(), ingredients, trade.costs(), trade.rawEntries(),
                        result, trade.enabled(), trade.problem()));
            }
        }
        return new Result(out, items, changedTrades, problems);
    }

    private record Fresh(ItemStack item, String encoded) {
    }

    /** The rebuilt template, or null when the item is not MMOItems, unchanged, or cannot be rebuilt. */
    private Fresh rebuild(ItemStack template, String encoded, String tradeId, List<Problem> problems) {
        if (template == null) {
            return null; // undecodable item data: never touched
        }
        String key = source.key(template);
        if (key == null) {
            return null;
        }
        ItemStack item;
        String data;
        try {
            item = source.fresh(key);
            if (item == null) {
                problems.add(new Problem(key, tradeId, "not found"));
                return null;
            }
            data = encoder.apply(item);
        } catch (RuntimeException e) {
            problems.add(new Problem(key, tradeId, String.valueOf(e.getMessage())));
            return null;
        }
        if (ItemCodec.byteSize(data) > maxBytes) {
            problems.add(new Problem(key, tradeId, "too large"));
            return null;
        }
        return data.equals(encoded) ? null : new Fresh(item, data);
    }
}
