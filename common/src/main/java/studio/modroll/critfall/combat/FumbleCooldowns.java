package studio.modroll.critfall.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when each attacker last triggered a fumble so further nat 1s within
 * {@code fumbles.cooldown_ticks} stay plain misses (PLAN.md §9.1 — real-time attack rates make
 * back-to-back fumble consequences miserable). Keyed by entity UUID against the level's game
 * time. Entries only matter within the cooldown window, so the map self-prunes expired entries
 * once it grows past {@link #PRUNE_SIZE} (audit 0.2 finding A1: transient mobs on a long-running
 * server otherwise accumulate forever), and both loaders {@link #clear()} it on server stop.
 */
public final class FumbleCooldowns {

    /** Prune expired entries when a record pushes the map past this size — amortized O(1). */
    private static final int PRUNE_SIZE = 4096;

    private static final Map<UUID, Long> lastFumble = new ConcurrentHashMap<>();

    private FumbleCooldowns() {}

    public static boolean isOnCooldown(UUID attacker, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = lastFumble.get(attacker);
        // last > gameTime means the entry was stamped in a previous world whose clock ran ahead
        // (this map and player UUIDs outlive a world in one JVM) — expired, not eternal cooldown.
        return last != null && last <= gameTime && gameTime - last < cooldownTicks;
    }

    public static void record(UUID attacker, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return; // cooldown disabled: entries would never be read, so don't accumulate them
        }
        if (lastFumble.size() >= PRUNE_SIZE) {
            lastFumble.values().removeIf(last -> last > gameTime || gameTime - last >= cooldownTicks);
        }
        lastFumble.put(attacker, gameTime);
    }

    /** For tests and server shutdown (both loaders call this on SERVER_STOPPING). */
    public static void clear() {
        lastFumble.clear();
    }

    /** For tests. */
    static int size() {
        return lastFumble.size();
    }
}
