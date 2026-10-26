package com.dex.boxshopkeeper.source;

import com.dex.boxshopkeeper.model.Ingredient;
import org.bukkit.entity.Player;

/**
 * Where ingredients are counted and taken from (§7). The transaction never touches the player
 * inventory directly for ingredients. Future sources (ExtraStorage, inventory-then-storage) plug in
 * here without changing {@link com.dex.boxshopkeeper.trade.TradeService}.
 */
public interface IngredientSource {

    /** How many items matching the ingredient are available. */
    long count(Player player, Ingredient ingredient);

    /**
     * Remove exactly {@code amount} matching items; returns a receipt that can undo it.
     * @throws IllegalStateException when fewer than {@code amount} items are available (nothing removed)
     */
    RemovalReceipt remove(Player player, Ingredient ingredient, long amount);
}
