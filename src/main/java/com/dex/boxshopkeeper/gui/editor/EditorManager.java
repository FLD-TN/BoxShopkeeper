package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import com.dex.boxshopkeeper.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates editor sessions: which shop an admin holds, the unsaved trade draft, and the
 * transitions between screens and chat prompts. Main thread only.
 */
public final class EditorManager {

    private final BoxShopkeeperPlugin plugin;
    private final EditorLocks locks = new EditorLocks();
    private final ChatPrompt prompts;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final ShopListEditor shopList;
    private final ShopEditor shopEditor;
    private final TradeEditor tradeEditor;

    public EditorManager(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
        this.prompts = new ChatPrompt(plugin);
        this.shopList = new ShopListEditor(plugin, this);
        this.shopEditor = new ShopEditor(plugin, this);
        this.tradeEditor = new TradeEditor(plugin, this);
    }

    public ChatPrompt prompts() {
        return prompts;
    }

    public EditorLocks locks() {
        return locks;
    }

    ShopListEditor shopList() {
        return shopList;
    }

    ShopEditor shopEditor() {
        return shopEditor;
    }

    TradeEditor tradeEditor() {
        return tradeEditor;
    }

    EditorSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new EditorSession());
    }

    EditorSession existingSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // ---------------------------------------------------------------- entry points

    public void openList(Player player, int page) {
        EditorSession session = session(player);
        leaveShop(player, session);
        session.listPage = page;
        shopList.open(player, page);
    }

    /** Locks the shop for the admin and opens its editor; refuses when another admin holds it. */
    public boolean enterShop(Player player, String shopId, int page) {
        Shop shop = plugin.shops().get(shopId);
        if (shop == null) {
            plugin.messages().send(player, "shop.unknown", Ph.of("shop", shopId));
            return false;
        }
        UUID holder = locks.holder(shopId);
        if (holder != null && !holder.equals(player.getUniqueId())) {
            plugin.messages().send(player, "editor.locked",
                    Ph.of("shop", shop.styledName()), Ph.of("player", nameOf(holder)));
            return false;
        }
        EditorSession session = session(player);
        if (session.shopId != null && !session.shopId.equals(shopId)) {
            leaveShop(player, session);
        }
        locks.acquire(shopId, player.getUniqueId());
        session.shopId = shopId;
        session.shopPage = page;
        session.draft = null;
        shopEditor.open(player, shop, page);
        return true;
    }

    /** Reopens the shop editor of the current session (after a prompt, save or cancel). */
    void reopenShop(Player player) {
        EditorSession session = existingSession(player);
        Shop shop = session == null || session.shopId == null ? null : plugin.shops().get(session.shopId);
        if (shop == null) {
            openList(player, session == null ? 0 : session.listPage);
            return;
        }
        session.draft = null;
        shopEditor.open(player, shop, session.shopPage);
    }

    /** Creates an empty shop (validated), saves it and returns it; sends the error otherwise. */
    public Shop createShop(org.bukkit.command.CommandSender sender, String id) {
        if (!Shop.ID_PATTERN.matcher(id).matches()) {
            plugin.messages().send(sender, "editor.invalid-id");
            return null;
        }
        if (plugin.shops().get(id) != null) {
            plugin.messages().send(sender, "editor.id-exists", Ph.of("shop", id));
            return null;
        }
        int max = plugin.settings().limits.shops();
        if (plugin.shops().size() >= max) {
            plugin.messages().send(sender, "editor.limit",
                    Ph.of("limit", plugin.messages().line("editor.limits.shops", Ph.of("max", max))));
            return null;
        }
        Shop shop = plugin.shops().save(Shop.builder(id), plugin.settings());
        plugin.refreshViewers(id);
        return shop;
    }

    // ---------------------------------------------------------------- session end

    /** Drops the shop part of a session: unsaved draft discarded, lock released. */
    void leaveShop(Player player, EditorSession session) {
        if (session.draft != null && session.draft.dirty()) {
            plugin.messages().send(player, "editor.discarded");
        }
        session.draft = null;
        session.shopId = null;
        locks.release(player.getUniqueId());
    }

    public void onClose(Player player, InventoryCloseEvent.Reason reason) {
        UUID id = player.getUniqueId();
        if (prompts.has(id) || !sessions.containsKey(id)) {
            return;
        }
        if (reason == InventoryCloseEvent.Reason.OPEN_NEW) {
            // Another screen is opening: keep the session if it is one of ours.
            Bukkit.getScheduler().runTask(plugin, () -> verify(id));
            return;
        }
        end(id, player);
    }

    private void verify(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            end(id, null);
            return;
        }
        if (prompts.has(id) || player.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder) {
            return;
        }
        end(id, player);
    }

    /** Ends the whole session. {@code player} may be null (quit), then no message is sent. */
    void end(UUID id, Player player) {
        EditorSession session = sessions.remove(id);
        if (session != null && session.draft != null && session.draft.dirty() && player != null) {
            plugin.messages().send(player, "editor.discarded");
        }
        locks.release(id);
    }

    public void quit(UUID id) {
        prompts.stop(id);
        end(id, null);
    }

    /** Closes every editor, stops prompts, releases every lock (reload guard and disable). */
    public void shutdown() {
        prompts.stopAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorHolder) {
                player.closeInventory();
            }
        }
        sessions.clear();
        locks.clear();
    }

    public boolean busy() {
        return !locks.isEmpty();
    }

    public int trackedSessions() {
        return sessions.size();
    }

    public String lockHolderName(String shopId) {
        UUID holder = locks.holder(shopId);
        return holder == null ? null : nameOf(holder);
    }

    private static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? id.toString() : offline.getName();
    }
}
