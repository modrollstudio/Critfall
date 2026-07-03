package studio.modroll.critfall.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when each attacker last triggered a fumble so further nat 1s within
 * {@code fumbles.cooldown_ticks} stay plain misses (PLAN.md §9.1 — real-time attack rates make
 * back-to-back fumble consequences miserable). Keyed by entity UUID against the level's game
 * time; a few stale entries per dead mob are harmless, so there is no eviction.
 */
public final class FumbleCooldowns {

    private static final Map<UUID, Long> lastFumble = new ConcurrentHashMap<>();

    private FumbleCooldowns() {}

    public static boolean isOnCooldown(UUID attacker, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = lastFumble.get(attacker);
        return last != null && gameTime - last < cooldownTicks;
    }

    public static void record(UUID attacker, long gameTime) {
        lastFumble.put(attacker, gameTime);
    }

    /** For tests and server shutdown. */
    public static void clear() {
        lastFumble.clear();
    }
}
