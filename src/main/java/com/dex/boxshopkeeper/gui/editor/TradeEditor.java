package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.config.PluginConfig.SoundKey;
import com.dex.boxshopkeeper.currency.CurrencyToken;
import com.dex.boxshopkeeper.gui.GuiItems;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.item.ItemCodec;
import com.dex.boxshopkeeper.model.CurrencyCost;
import com.dex.boxshopkeeper.model.Ingredient;
import com.dex.boxshopkeeper.model.ResultSpec;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.model.Trade;
import com.dex.boxshopkeeper.storage.ShopRepository;
import com.dex.boxshopkeeper.util.Numbers;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor screen 3: ingredient ghost slots in the 3 × 3 grid, the result slot on its right (same
 * geometry as the player's trade detail), controls in the bottom row. The admin's cursor item is only cloned, never taken or given.
 */
final class TradeEditor {

    static final int RESULT_SLOT = GridLayout.RESULT_SLOT;
    static final int CANCEL_SLOT = 45;
    static final int TOGGLE_SLOT = 47;
    static final int INFO_SLOT = 49;
    static final int SAVE_SLOT = 51;

    private final BoxShopkeeperPlugin plugin;
    private final EditorManager manager;

    TradeEditor(BoxShopkeeperPlugin plugin, EditorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    void open(Player player, Shop shop, TradeDraft draft, int index) {
        EditorHolder holder = new EditorHolder(EditorHolder.Screen.TRADE, shop.id(), draft.tradeId(), null, index);
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.editor-trade",
                shop.styledName(), Ph.of("index", index + 1)));
        render(holder, draft);
        player.openInventory(inventory);
    }

    private void render(EditorHolder holder, TradeDraft draft) {
        Inventory inventory = holder.getInventory();
        GuiItems items = plugin.guiItems();
        Messages messages = plugin.messages();
        items.frame(inventory, true);
        for (int i = 0; i < TradeDraft.INGREDIENT_SLOTS; i++) {
            renderIngredient(inventory, draft, i);
        }
        renderResult(inventory, draft);
        ItemStack marker = items.button(Button.RESULT_MARKER,
                messages.line("gui.editor.result-marker.name"), messages.lines("gui.editor.result-marker.lore"));
        for (int slot : GridLayout.RESULT_MARKERS) {
            inventory.setItem(slot, marker);
        }
        inventory.setItem(CANCEL_SLOT, items.button(Button.CANCEL,
                messages.line("gui.editor.cancel.name"), messages.lines("gui.editor.cancel.lore")));
        renderToggle(inventory, draft);
        renderInfo(inventory, draft);
        inventory.setItem(SAVE_SLOT, items.button(Button.SAVE,
                messages.line("gui.editor.save.name"), messages.lines("gui.editor.save.lore")));
    }

    private void renderIngredient(Inventory inventory, TradeDraft draft, int index) {
        TradeDraft.Entry entry = draft.entry(index);
        int slot = GridLayout.GRID[index];
        if (entry == null) {
            Messages messages = plugin.messages();
            inventory.setItem(slot, plugin.guiItems().button(Button.INGREDIENT_SLOT,
                    messages.line("gui.editor.ingredient-slot.name"), messages.lines("gui.editor.ingredient-slot.lore")));
            return;
        }
        if (entry instanceof TradeDraft.CostEntry(CurrencyCost cost)) {
            inventory.setItem(slot, costDisplay(cost));
            return;
        }
        Ingredient ingredient = ((TradeDraft.ItemEntry) entry).ingredient();
        inventory.setItem(slot, display(ingredient.template(), ingredient.amount(), false));
    }

    /** The token look (no PDC keys) with the cost and how to change it. */
    private ItemStack costDisplay(CurrencyCost cost) {
        Messages messages = plugin.messages();
        Ph[] placeholders = plugin.currencyToken().placeholders(cost.type(), cost.amount());
        return GuiItems.withNameAndLore(plugin.currencyToken().look(cost.type()),
                plugin.currencyToken().costName(cost.type(), cost.amount()),
                messages.lines("gui.editor.currency", placeholders));
    }

    private void renderResult(Inventory inventory, TradeDraft draft) {
        ResultSpec result = draft.result();
        if (result == null) {
            Messages messages = plugin.messages();
            inventory.setItem(RESULT_SLOT, plugin.guiItems().button(Button.RESULT_SLOT,
                    messages.line("gui.editor.result-slot.name"), messages.lines("gui.editor.result-slot.lore")));
            return;
        }
        inventory.setItem(RESULT_SLOT, display(result.template(), result.amount(), true));
    }

    private void renderToggle(Inventory inventory, TradeDraft draft) {
        Messages messages = plugin.messages();
        inventory.setItem(TOGGLE_SLOT, plugin.guiItems().button(draft.enabled() ? Button.TOGGLE_ON : Button.TOGGLE_OFF,
                messages.line("gui.editor.toggle.name"),
                List.of(messages.line(draft.enabled() ? "gui.editor.toggle.enabled" : "gui.editor.toggle.disabled"))));
    }

    private void renderInfo(Inventory inventory, TradeDraft draft) {
        PluginConfig.Limits limits = plugin.settings().limits;
        List<Ingredient> merged = ShopRepository.mergeSimilar(draft.ingredients());
        int costs = (int) draft.costs().stream().map(CurrencyCost::type).distinct().count();
        int slots = costs;
        for (Ingredient ingredient : merged) {
            slots += ingredient.displaySlots();
        }
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>(messages.lines("gui.editor.info.lore",
                Ph.of("id", draft.tradeId()),
                Ph.of("count", merged.size() + costs),
                Ph.of("max-ingredients", limits.ingredientsPerTrade()),
                Ph.of("slots", slots),
                Ph.of("max-slots", limits.displaySlotsPerTrade())));
        if (draft.dirty() && !messages.isEmpty("gui.editor.info.unsaved")) {
            lore.add(Component.empty());
            lore.addAll(messages.lines("gui.editor.info.unsaved"));
        }
        inventory.setItem(INFO_SLOT, plugin.guiItems().button(Button.INFO, messages.line("gui.editor.info.name"), lore));
    }

    /** A clone of the template with its own lore, a separator and the editor controls. */
    private ItemStack display(ItemStack template, int amount, boolean result) {
        Messages messages = plugin.messages();
        ItemStack item = template.clone();
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            List<Component> own = meta.lore();
            if (own != null) {
                lore.addAll(own);
            }
            lore.addAll(messages.lines("gui.trade-detail.separator"));
            if (result) {
                lore.addAll(messages.lines("gui.editor.result-label"));
            }
            lore.addAll(messages.lines("gui.editor.ingredient", Ph.of("amount", amount)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    void click(Player player, EditorHolder holder, int slot, ClickType click) {
        EditorSession session = manager.existingSession(player);
        Shop shop = plugin.shops().get(holder.shopId());
        TradeDraft draft = session == null ? null : session.draft;
        if (shop == null || draft == null || !shop.id().equals(session.shopId)
                || !draft.tradeId().equals(holder.tradeId())) {
            manager.openList(player, 0);
            return;
        }
        Inventory inventory = holder.getInventory();
        ItemStack cursor = player.getItemOnCursor();
        boolean hasCursor = !cursor.isEmpty();

        int index = GridLayout.gridIndex(slot);
        if (index >= 0) {
            TradeDraft.Entry current = draft.entry(index);
            if (hasCursor && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
                CurrencyToken.Data token = plugin.currencyToken().read(cursor);
                if (token != null) {
                    // A currency token: its PDC becomes a cost; the cursor is left unchanged.
                    long max = CurrencyCost.maxAmount(token.type());
                    if (max < 1 || token.amount() < 1 || token.amount() > max) {
                        plugin.messages().send(player, "editor.currency-invalid");
                        return;
                    }
                    draft.entry(index, new TradeDraft.CostEntry(new CurrencyCost(token.type(), token.amount())));
                } else {
                    Template template = template(player, cursor);
                    if (template == null) {
                        return;
                    }
                    draft.entry(index, new TradeDraft.ItemEntry(
                            new Ingredient(template.item(), template.encoded(), clamp(cursor.getAmount()))));
                }
            } else if (!hasCursor && current instanceof TradeDraft.CostEntry) {
                // The amount of a cost is changed by making a new token; only removal is possible here.
                if (click != ClickType.MIDDLE && click != ClickType.DROP && click != ClickType.CONTROL_DROP) {
                    return;
                }
                draft.entry(index, null);
            } else if (!hasCursor && current instanceof TradeDraft.ItemEntry(Ingredient ingredient)) {
                Integer amount = adjust(ingredient.amount(), click);
                if (amount == null) {
                    return;
                }
                draft.entry(index, amount == 0 ? null : new TradeDraft.ItemEntry(ingredient.withAmount(amount)));
            } else {
                return;
            }
            plugin.settings().sound(SoundKey.CLICK).play(player);
            renderIngredient(inventory, draft, index);
            renderInfo(inventory, draft);
            return;
        }

        if (slot == RESULT_SLOT) {
            ResultSpec current = draft.result();
            if (hasCursor && (click == ClickType.LEFT || click == ClickType.RIGHT)) {
                if (plugin.currencyToken().read(cursor) != null) {
                    plugin.messages().send(player, "editor.currency-result");
                    return;
                }
                Template template = template(player, cursor);
                if (template == null) {
                    return;
                }
                draft.result(new ResultSpec(template.item(), template.encoded(), clamp(cursor.getAmount())));
            } else if (!hasCursor && current != null) {
                Integer amount = adjust(current.amount(), click);
                if (amount == null) {
                    return;
                }
                draft.result(amount == 0 ? null : new ResultSpec(current.template(), current.encoded(), amount));
            } else {
                return;
            }
            plugin.settings().sound(SoundKey.CLICK).play(player);
            renderResult(inventory, draft);
            renderInfo(inventory, draft);
            return;
        }

        if (click != ClickType.LEFT) {
            return;
        }
        switch (slot) {
            case CANCEL_SLOT -> {
                if (draft.dirty()) {
                    plugin.messages().send(player, "editor.discarded");
                }
                session.draft = null;
                plugin.settings().sound(SoundKey.CLICK).play(player);
                manager.reopenShop(player);
            }
            case TOGGLE_SLOT -> {
                draft.toggleEnabled();
                plugin.settings().sound(SoundKey.CLICK).play(player);
                renderToggle(inventory, draft);
                renderInfo(inventory, draft);
            }
            case SAVE_SLOT -> save(player, shop, draft);
            default -> {
            }
        }
    }

    /**
     * New amount for an amount-changing click, 0 to remove, or null for any other click.
     * Left +1, shift-left +64, right −1, shift-right −64 (minimum 1), middle or Q removes.
     */
    private static Integer adjust(int amount, ClickType click) {
        return switch (click) {
            case LEFT -> clamp((long) amount + 1);
            case SHIFT_LEFT -> clamp((long) amount + 64);
            case RIGHT -> clamp((long) amount - 1);
            case SHIFT_RIGHT -> clamp((long) amount - 64);
            case MIDDLE, DROP, CONTROL_DROP -> 0;
            default -> null;
        };
    }

    private static int clamp(long amount) {
        return (int) Math.max(1, Math.min(PluginConfig.MAX_AMOUNT, amount));
    }

    private record Template(ItemStack item, String encoded) {
    }

    /** Clones the cursor item (amount 1) as a template; the cursor itself is left untouched. */
    private Template template(Player player, ItemStack cursor) {
        String encoded = ItemCodec.encode(cursor);
        if (ItemCodec.byteSize(encoded) > plugin.settings().limits.templateBytes()) {
            plugin.messages().send(player, "editor.item-too-large");
            return null;
        }
        ItemStack item = cursor.clone();
        item.setAmount(1);
        return new Template(item, encoded);
    }

    private void save(Player player, Shop shop, TradeDraft draft) {
        PluginConfig.Limits limits = plugin.settings().limits;
        Messages messages = plugin.messages();
        List<Ingredient> ingredients = draft.ingredients();
        List<CurrencyCost> costs = draft.costs();
        if ((ingredients.isEmpty() && costs.isEmpty()) || draft.result() == null) {
            messages.send(player, "editor.invalid");
            return;
        }
        List<Ingredient> merged = ShopRepository.mergeSimilar(ingredients);
        List<CurrencyCost> mergedCosts;
        try {
            mergedCosts = CurrencyCost.merge(costs);
        } catch (CurrencyCost.TooLargeException e) {
            messages.send(player, "editor.currency-too-large",
                    Ph.of("currency", plugin.currencies().displayName(e.type())),
                    Ph.of("max", Numbers.group(CurrencyCost.maxAmount(e.type()))));
            return;
        }
        if (merged.size() + mergedCosts.size() > limits.ingredientsPerTrade()) {
            messages.send(player, "editor.limit", Ph.of("limit",
                    messages.line("editor.limits.ingredients", Ph.of("max", limits.ingredientsPerTrade()))));
            return;
        }
        Trade trade = new Trade(draft.tradeId(), draft.slot(), merged, mergedCosts, null,
                draft.result(), draft.enabled(), null);
        if (trade.displaySlots() > limits.displaySlotsPerTrade()) {
            messages.send(player, "editor.limit", Ph.of("limit",
                    messages.line("editor.limits.display-slots", Ph.of("max", limits.displaySlotsPerTrade()))));
            return;
        }
        List<Trade> trades = new ArrayList<>(shop.trades());
        boolean replaced = false;
        for (int i = 0; i < trades.size(); i++) {
            if (trades.get(i).id().equals(trade.id())) {
                trades.set(i, trade);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (trades.size() >= limits.tradesPerShop()) {
                messages.send(player, "editor.limit", Ph.of("limit",
                        messages.line("editor.limits.trades", Ph.of("max", limits.tradesPerShop()))));
                return;
            }
            trades.add(trade);
        }
        manager.shopEditor().save(player, shop.toBuilder().trades(trades), true);
    }
}
