package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.config.PluginConfig.SoundKey;
import com.dex.boxshopkeeper.gui.GuiItems;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.model.Shop;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Editor screen 1 (§6.1): every shop, click to edit, button to create. */
final class ShopListEditor {

    private static final int CREATE_COLUMN = 8;

    private final BoxShopkeeperPlugin plugin;
    private final EditorManager manager;

    ShopListEditor(BoxShopkeeperPlugin plugin, EditorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private List<Shop> shops() {
        List<Shop> shops = new ArrayList<>(plugin.shops().all());
        shops.sort(Comparator.comparingInt(Shop::menuOrder).thenComparing(Shop::id));
        return shops;
    }

    void open(Player player, int page) {
        List<Shop> shops = shops();
        int clamped = GridLayout.clampPage(page, shops.size());
        EditorHolder holder = new EditorHolder(EditorHolder.Screen.SHOP_LIST, null, null, null, clamped);
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.editor-list", ""));
        render(holder, shops);
        player.openInventory(inventory);
    }

    private void render(EditorHolder holder, List<Shop> shops) {
        Inventory inventory = holder.getInventory();
        GuiItems items = plugin.guiItems();
        Messages messages = plugin.messages();
        items.frame(inventory, false);
        inventory.setItem(GridLayout.HEADER_SLOT, items.button(Button.HEADER,
                messages.line("gui.header.editor-list.name"),
                messages.lines("gui.header.editor-list.lore", Ph.of("count", shops.size()))));
        String[] ids = new String[inventory.getSize()];
        if (shops.isEmpty()) {
            inventory.setItem(GridLayout.CENTRE_SLOT, items.button(Button.INFO, "gui.editor.empty-list"));
        }
        int[] slots = GridLayout.pageSlots(shops.size(), holder.page());
        int first = GridLayout.firstIndex(holder.page());
        for (int i = 0; i < slots.length; i++) {
            Shop shop = shops.get(first + i);
            inventory.setItem(slots[i], entry(shop));
            ids[slots[i]] = shop.id();
        }
        holder.slotIds(ids);
        items.controls(inventory, holder.page(), GridLayout.pages(shops.size()), false);
        inventory.setItem(GridLayout.bottomSlot(CREATE_COLUMN),
                items.button(Button.CREATE_SHOP, messages.line("gui.editor.create-shop.name"),
                        messages.lines("gui.editor.create-shop.lore")));
    }

    private ItemStack entry(Shop shop) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>();
        lore.addAll(messages.lines("gui.editor.shop-entry.id", Ph.of("id", shop.id())));
        lore.addAll(messages.lines("gui.editor.shop-entry.trades", Ph.of("count", shop.trades().size())));
        lore.addAll(messages.lines("gui.editor.shop-entry.permission",
                Ph.of("permission", messages.line(shop.permissionRequired() ? "gui.editor.flag-on" : "gui.editor.flag-off"))));
        String editing = manager.lockHolderName(shop.id());
        if (editing != null) {
            lore.addAll(messages.lines("editor.locked", Ph.of("shop", shop.styledName()), Ph.of("player", editing)));
        }
        lore.add(Component.empty());
        lore.addAll(messages.lines("gui.editor.shop-entry.action"));
        return GuiItems.withNameAndLore(plugin.mainMenu().icon(shop),
                messages.line("gui.editor.shop-entry.name", Ph.of("shop", shop.styledName())), lore);
    }

    void click(Player player, EditorHolder holder, int slot, ClickType click) {
        if (click != ClickType.LEFT) {
            return;
        }
        String shopId = holder.idAt(slot);
        if (shopId != null) {
            plugin.settings().sound(SoundKey.CLICK).play(player);
            manager.enterShop(player, shopId, 0);
            return;
        }
        if (GuiItems.isControl(slot, CREATE_COLUMN)) {
            plugin.settings().sound(SoundKey.CLICK).play(player);
            promptCreate(player, holder.page());
            return;
        }
        int pages = GridLayout.pages(plugin.shops().size());
        int delta = GuiItems.isControl(slot, GridLayout.PREVIOUS_COLUMN) ? -1
                : GuiItems.isControl(slot, GridLayout.NEXT_COLUMN) ? 1 : 0;
        int target = holder.page() + delta;
        if (delta != 0 && target >= 0 && target < pages) {
            plugin.settings().sound(SoundKey.CLICK).play(player);
            manager.session(player).listPage = target;
            holder.page(target);
            render(holder, shops());
        }
    }

    private void promptCreate(Player player, int page) {
        manager.prompts().start(player, "editor.what.id",
                (admin, input) -> {
                    if (!Shop.ID_PATTERN.matcher(input).matches()) {
                        plugin.messages().send(admin, "editor.invalid-id");
                        return false;
                    }
                    if (plugin.shops().get(input) != null) {
                        plugin.messages().send(admin, "editor.id-exists", Ph.of("shop", input));
                        return false;
                    }
                    Shop shop = manager.createShop(admin, input);
                    if (shop == null || !manager.enterShop(admin, shop.id(), 0)) {
                        manager.openList(admin, page);
                    }
                    return true;
                },
                admin -> manager.openList(admin, page),
                admin -> manager.end(admin, null));
        player.closeInventory();
    }
}
