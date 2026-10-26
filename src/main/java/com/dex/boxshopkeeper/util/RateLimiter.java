package com.dex.boxshopkeeper.util;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/** Per-player last-action tick for each rate-limited action (§17.3). Cleared on quit. */
public final class RateLimiter {

    public enum Action { TRADE, NAVIGATION, COMMAND, EDITOR }

    private final IntSupplier currentTick;
    private final Map<Action, Map<UUID, Integer>> lastAction = new EnumMap<>(Action.class);

    public RateLimiter(IntSupplier currentTick) {
        this.currentTick = currentTick;
        for (Action action : Action.values()) {
            lastAction.put(action, new HashMap<>());
        }
    }

    /**
     * Records the action and returns true when at least {@code intervalTicks} passed since the last one;
     * returns false (and records nothing) otherwise.
     */
    public boolean tryAcquire(UUID player, Action action, int intervalTicks) {
        int now = currentTick.getAsInt();
        Map<UUID, Integer> map = lastAction.get(action);
        Integer last = map.get(player);
        if (last != null && now - last < intervalTicks) {
            return false;
        }
        map.put(player, now);
        return true;
    }

    public void forget(UUID player) {
        for (Map<UUID, Integer> map : lastAction.values()) {
            map.remove(player);
        }
    }

    public int trackedEntries() {
        int total = 0;
        for (Map<UUID, Integer> map : lastAction.values()) {
            total += map.size();
        }
        return total;
    }
}
