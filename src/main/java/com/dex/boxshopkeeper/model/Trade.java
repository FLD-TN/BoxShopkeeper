package com.dex.boxshopkeeper.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Ingredients and currency costs → one result. Immutable; an edit replaces the whole trade. */
public final class Trade {

    private final String id;
    private final Integer slot;
    private final List<Ingredient> ingredients;
    private final List<CurrencyCost> costs;
    /** Non-item ingredient entries exactly as read, kept for a trade with a problem (update 01, §5.3), or null. */
    private final List<Map<String, Object>> rawEntries;
    private final ResultSpec result;
    private final boolean enabled;
    private final String problem;

    /** Shop-view icon without lore, built once on first display. */
    private ItemStack iconBase;

    public Trade(String id, Integer slot, List<Ingredient> ingredients, ResultSpec result,
                 boolean enabled, String problem) {
        this(id, slot, ingredients, List.of(), null, result, enabled, problem);
    }

    public Trade(String id, Integer slot, List<Ingredient> ingredients, List<CurrencyCost> costs,
                 List<Map<String, Object>> rawEntries, ResultSpec result, boolean enabled, String problem) {
        this.id = id;
        this.slot = slot;
        this.ingredients = List.copyOf(ingredients);
        this.costs = List.copyOf(costs);
        this.rawEntries = rawEntries == null ? null : List.copyOf(rawEntries);
        this.result = result;
        this.enabled = enabled;
        this.problem = problem;
    }

    public String id() {
        return id;
    }

    public Integer slot() {
        return slot;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    /** Currency costs, one per type, in the fixed order (gem, points, vault). */
    public List<CurrencyCost> costs() {
        return costs;
    }

    /** Entries written back verbatim instead of {@link #costs()}, or null. */
    public List<Map<String, Object>> rawEntries() {
        return rawEntries;
    }

    public ResultSpec result() {
        return result;
    }

    public Trade withId(String newId) {
        return new Trade(newId, slot, ingredients, costs, rawEntries, result, enabled, problem);
    }

    /** The enabled flag as stored on disk (set by the admin). */
    public boolean enabled() {
        return enabled;
    }

    /** Why the trade cannot be used even if enabled (broken item, invalid data), or null. */
    public String problem() {
        return problem;
    }

    public boolean broken() {
        return problem != null;
    }

    /** Whether players can see and use this trade. */
    public boolean usable() {
        return enabled && problem == null;
    }

    /** Σ ceil(a_i / m_i) + one slot per cost: slots the trade detail needs to show every ingredient. */
    public int displaySlots() {
        int total = costs.size();
        for (Ingredient ingredient : ingredients) {
            total += ingredient.displaySlots();
        }
        return total;
    }

    public ItemStack iconBase() {
        return iconBase;
    }

    public void iconBase(ItemStack icon) {
        this.iconBase = icon;
    }
}
