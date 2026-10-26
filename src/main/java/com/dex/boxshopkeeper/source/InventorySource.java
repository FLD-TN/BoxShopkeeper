package com.dex.boxshopkeeper.source;

import com.dex.boxshopkeeper.item.ItemMatcher;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.trade.InventorySimulator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The 36 main inventory slots (hotbar and storage). Armour and off-hand are never used.
 * Removal order is the one of {@link InventorySimulator#removalOrder}: smallest stacks first.
 */
public final class InventorySource implements IngredientSource {

    @Override
    public long count(Player player, Ingredient ingredient) {
        if (ingredient.broken()) {
            return 0;
        }
        long total = 0;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < InventorySimulator.SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (ItemMatcher.matches(ingredient.template(), item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    @Override
    public RemovalReceipt remove(Player player, Ingredient ingredient, long amount) {
        PlayerInventory inventory = player.getInventory();
        int[] amounts = new int[InventorySimulator.SLOTS];
        boolean[] matches = new boolean[InventorySimulator.SLOTS];
        long available = 0;
        for (int slot = 0; slot < InventorySimulator.SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!ingredient.broken() && ItemMatcher.matches(ingredient.template(), item)) {
                amounts[slot] = item.getAmount();
                matches[slot] = true;
                available += item.getAmount();
            }
        }
        if (available < amount) {
            throw new IllegalStateException("not enough items: need " + amount + ", have " + available);
        }

        List<Change> changes = new ArrayList<>();
        long remaining = amount;
        for (int slot : InventorySimulator.removalOrder(amounts, matches)) {
            if (remaining == 0) {
                break;
            }
            ItemStack item = inventory.getItem(slot);
            int take = (int) Math.min(remaining, item.getAmount());
            changes.add(new Change(slot, item.clone()));
            if (take == item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                ItemStack reduced = item.clone();
                reduced.setAmount(item.getAmount() - take);
                inventory.setItem(slot, reduced);
            }
            remaining -= take;
        }
        return new SlotReceipt(inventory, changes);
    }

    private record Change(int slot, ItemStack before) {
    }

    /** Restores each touched slot to its exact previous content, newest change first. */
    private record SlotReceipt(PlayerInventory inventory, List<Change> changes) implements RemovalReceipt {
        @Override
        public void rollback() {
            for (int i = changes.size() - 1; i >= 0; i--) {
                Change change = changes.get(i);
                inventory.setItem(change.slot(), change.before());
            }
        }
    }
}
