package com.dex.boxshopkeeper.gui.editor;

import com.dex.boxshopkeeper.BoxShopkeeperPlugin;
import com.dex.boxshopkeeper.gui.style.Text;
import com.dex.boxshopkeeper.gui.style.Text.Ph;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Admin chat prompts (§11): 30 s timeout, {@code cancel} aborts. Chat is received on the async
 * chat thread, which only checks a concurrent id set and hands the text to the main thread.
 */
public final class ChatPrompt implements Listener {

    private static final long TIMEOUT_TICKS = 30 * 20L;

    /**
     * Receives one line; returns true when the prompt is finished, false to keep waiting.
     * Callbacks receive the player or its id; no player reference is kept.
     */
    public interface Handler {
        boolean accept(Player player, String input);
    }

    private record Pending(Handler handler, Consumer<Player> onCancel, Consumer<UUID> onTimeout, BukkitTask timeout) {
    }

    private final BoxShopkeeperPlugin plugin;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ChatPrompt(BoxShopkeeperPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, String whatKey, Handler handler, Consumer<Player> onCancel, Consumer<UUID> onTimeout) {
        stop(player.getUniqueId());
        UUID id = player.getUniqueId();
        pending.put(id, new Pending(handler, onCancel, onTimeout, schedule(id)));
        active.add(id);
        plugin.messages().send(player, "editor.prompt", Ph.of("what", plugin.messages().line(whatKey)));
    }

    private BukkitTask schedule(UUID id) {
        return Bukkit.getScheduler().runTaskLater(plugin, () -> timeout(id), TIMEOUT_TICKS);
    }

    public boolean has(UUID player) {
        return pending.containsKey(player);
    }

    public int size() {
        return pending.size();
    }

    /** Ends a prompt without callbacks (quit, disable). */
    public void stop(UUID player) {
        active.remove(player);
        Pending old = pending.remove(player);
        if (old != null) {
            old.timeout().cancel();
        }
    }

    public void stopAll() {
        for (UUID id : Set.copyOf(pending.keySet())) {
            stop(id);
        }
    }

    private void timeout(UUID id) {
        Pending old = pending.get(id);
        if (old == null) {
            return;
        }
        stop(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            plugin.messages().send(player, "editor.prompt-timeout");
        }
        old.onTimeout().accept(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!active.contains(id)) {
            return;
        }
        event.setCancelled(true);
        String input = Text.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handle(id, input));
    }

    private void handle(UUID id, String input) {
        Pending current = pending.get(id);
        Player player = Bukkit.getPlayer(id);
        if (current == null || player == null) {
            return;
        }
        if (!player.hasPermission("chetao.admin")) {
            // Permission removed while the prompt was open: drop it without applying the input.
            stop(id);
            plugin.editor().quit(id);
            return;
        }
        try {
            if (input.equalsIgnoreCase("cancel")) {
                stop(id);
                plugin.messages().send(player, "editor.prompt-cancelled");
                current.onCancel().accept(player);
                return;
            }
            if (current.handler().accept(player, input)) {
                if (pending.get(id) == current) {
                    stop(id);
                }
            } else if (pending.get(id) == current) {
                current.timeout().cancel();
                pending.put(id, new Pending(current.handler(), current.onCancel(), current.onTimeout(), schedule(id)));
            }
        } catch (Throwable error) {
            stop(id);
            plugin.fail(player, "Error handling chat input", error);
        }
    }
}
