package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Trade;

import java.util.ArrayList;
import java.util.List;

/** Unsaved state of the trade editor. Holds clones only, never the admin's real items. */
public final class TradeDraft {

    public static final int INGREDIENT_SLOTS = 9;

    /** What one grid slot holds: an item ingredient or a currency cost. */
    public sealed interface Entry permits ItemEntry, CostEntry {
    }

    public record ItemEntry(Ingredient ingredient) implements Entry {
    }

    public record CostEntry(CurrencyCost cost) implements Entry {
    }

    private final String tradeId;
    private final Integer slot;
    private final boolean isNew;
    private final Entry[] entries = new Entry[INGREDIENT_SLOTS];
    private ResultSpec result;
    private boolean enabled;
    private boolean dirty;

    private TradeDraft(String tradeId, Integer slot, boolean isNew) {
        this.tradeId = tradeId;
        this.slot = slot;
        this.isNew = isNew;
    }

    public static TradeDraft create(String tradeId) {
        TradeDraft draft = new TradeDraft(tradeId, null, true);
        draft.enabled = true;
        return draft;
    }

    /** Item ingredients first, then costs in the fixed order, in the next free slots. */
    public static TradeDraft of(Trade trade) {
        TradeDraft draft = new TradeDraft(trade.id(), trade.slot(), false);
        int next = 0;
        for (Ingredient ingredient : trade.ingredients()) {
            if (next < INGREDIENT_SLOTS) {
                draft.entries[next++] = new ItemEntry(ingredient);
            }
        }
        for (CurrencyCost cost : trade.costs()) {
            if (next < INGREDIENT_SLOTS) {
                draft.entries[next++] = new CostEntry(cost);
            }
        }
        draft.result = trade.result();
        draft.enabled = trade.enabled();
        return draft;
    }

    public String tradeId() {
        return tradeId;
    }

    public Integer slot() {
        return slot;
    }

    public boolean isNew() {
        return isNew;
    }

    public Entry entry(int index) {
        return entries[index];
    }

    public void entry(int index, Entry entry) {
        entries[index] = entry;
        dirty = true;
    }

    /** Item ingredients in slot order. */
    public List<Ingredient> ingredients() {
        List<Ingredient> list = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry instanceof ItemEntry item) {
                list.add(item.ingredient());
            }
        }
        return list;
    }

    /** Currency costs in slot order, not merged. */
    public List<CurrencyCost> costs() {
        List<CurrencyCost> list = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry instanceof CostEntry cost) {
                list.add(cost.cost());
            }
        }
        return list;
    }

    public ResultSpec result() {
        return result;
    }

    public void result(ResultSpec result) {
        this.result = result;
        dirty = true;
    }

    public boolean enabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }
}
