package com.dex.boxshopkeeper.gui;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.config.Messages;
import com.dex.boxshopkeeper.config.PluginConfig.Button;
import com.dex.boxshopkeeper.config.PluginConfig.SoundKey;
import com.dex.boxshopkeeper.gui.style.GridLayout;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.model.Shop;
import com.dex.boxshopkeeper.util.RateLimiter.Action;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Screen [1] (§5.6): every shop the player may open. No Back button. */
public final class MainMenuGui {

    private static final Comparator<Shop> ORDER =
            Comparator.comparingInt(Shop::menuOrder).thenComparing(Shop::id);

    private final BoxShopkeeperPlugin plugin;

    public MainMenuGui(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean canOpen(Player player, Shop shop) {
        return !shop.permissionRequired() || player.hasPermission(shop.permission());
    }

    public List<Shop> visibleShops(Player player) {
        List<Shop> visible = new ArrayList<>();
        for (Shop shop : plugin.shops().all()) {
            if (canOpen(player, shop)) {
                visible.add(shop);
            }
        }
        visible.sort(ORDER);
        return visible;
    }

    public void open(Player player, int page) {
        List<Shop> shops = visibleShops(player);
        MainMenuHolder holder = new MainMenuHolder(GridLayout.clampPage(page, shops.size()));
        Inventory inventory = holder.createInventory(plugin.messages().title("gui.title.main-menu", ""));
        render(holder, inventory, shops);
        player.openInventory(inventory);
    }

    /** Re-renders in place. */
    public void refresh(Player player, MainMenuHolder holder) {
        List<Shop> shops = visibleShops(player);
        holder.page(GridLayout.clampPage(holder.page(), shops.size()));
        render(holder, holder.getInventory(), shops);
    }

    private void render(MainMenuHolder holder, Inventory inventory, List<Shop> shops) {
        GuiItems items = plugin.guiItems();
        Messages messages = plugin.messages();
        items.frame(inventory, false);
        inventory.setItem(GridLayout.HEADER_SLOT, items.button(Button.HEADER,
                messages.line("gui.header.main-menu.name"),
                messages.lines("gui.header.main-menu.lore", Ph.of("count", shops.size()))));
        String[] ids = new String[inventory.getSize()];
        if (shops.isEmpty()) {
            inventory.setItem(GridLayout.CENTRE_SLOT, items.button(Button.INFO, "gui.main-menu.empty"));
        }
        int[] slots = GridLayout.pageSlots(shops.size(), holder.page());
        int first = GridLayout.firstIndex(holder.page());
        for (int i = 0; i < slots.length; i++) {
            Shop shop = shops.get(first + i);
            inventory.setItem(slots[i], entry(shop));
            ids[slots[i]] = shop.id();
        }
        holder.slotShopIds(ids);
        items.controls(inventory, holder.page(), GridLayout.pages(shops.size()), false);
    }

    private ItemStack entry(Shop shop) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>();
        for (Component line : shop.styledDescription()) {
            lore.addAll(messages.lines("gui.main-menu.description-line", Ph.of("line", line)));
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.addAll(messages.lines("gui.main-menu.trade-count", Ph.of("count", shop.usableTrades().size())));
        lore.addAll(messages.lines("gui.main-menu.action"));
        return GuiItems.withNameAndLore(icon(shop),
                messages.line("gui.main-menu.name", Ph.of("shop", shop.styledName())), lore);
    }

    /** The shop icon without stats, or the default icon material. */
    public ItemStack icon(Shop shop) {
        ItemStack base = shop.icon() != null
                ? shop.icon()
                : new ItemStack(plugin.settings().material(Button.DEFAULT_SHOP_ICON));
        return plugin.guiItems().stripped(base, 1);
    }

    public void click(Player player, MainMenuHolder holder, int slot, ClickType click) {
        if (click != ClickType.LEFT) {
            return;
        }
        String shopId = holder.shopAt(slot);
        if (shopId != null) {
            if (!plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.NAVIGATION, plugin.settings().navigationTicks)) {
                return;
            }
            Shop shop = plugin.shops().get(shopId);
            if (shop == null || !canOpen(player, shop)) {
                refresh(player, holder);
                return;
            }
            plugin.settings().sound(SoundKey.CLICK).play(player);
            plugin.shopView().open(player, shop, 0);
            return;
        }
        int delta = GuiItems.isControl(slot, GridLayout.PREVIOUS_COLUMN) ? -1
                : GuiItems.isControl(slot, GridLayout.NEXT_COLUMN) ? 1 : 0;
        if (delta != 0 && pageExists(player, holder.page() + delta)
                && plugin.rateLimiter().tryAcquire(player.getUniqueId(), Action.NAVIGATION, plugin.settings().navigationTicks)) {
            plugin.settings().sound(SoundKey.CLICK).play(player);
            holder.page(holder.page() + delta);
            refresh(player, holder);
        }
    }

    private boolean pageExists(Player player, int page) {
        return page >= 0 && page < GridLayout.pages(visibleShops(player).size());
    }
}
