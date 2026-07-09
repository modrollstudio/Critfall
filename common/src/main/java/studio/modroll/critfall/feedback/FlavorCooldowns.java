package studio.modroll.critfall.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-target anti-spam gate for narrative flavor lines (PLAN §4.5): at most one non-priority flavor
 * line per {@code cooldown_ticks} per target. Priority lines (nat 20 crit / nat 1 fumble) bypass this
 * in {@link FeedbackBuilder}. Keyed by target UUID against the level game time — same pattern and
 * same lifecycle as {@link studio.modroll.critfall.combat.FumbleCooldowns}: expired entries are
 * pruned once the map grows past {@link #PRUNE_SIZE} (audit 0.2 finding A2 — every flavored kill
 * used to leave a permanent entry) and both loaders {@link #clear()} it on server stop.
 */
public final class FlavorCooldowns {

    /** Prune expired entries when a record pushes the map past this size — amortized O(1). */
    private static final int PRUNE_SIZE = 4096;

    private static final Map<UUID, Long> lastFlavor = new ConcurrentHashMap<>();

    private FlavorCooldowns() {}

    public static boolean isOnCooldown(UUID target, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = lastFlavor.get(target);
        // last > gameTime is a leftover from a previous world's clock — expired, not eternal.
        return last != null && last <= gameTime && gameTime - last < cooldownTicks;
    }

    public static void record(UUID target, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return; // cooldown disabled: entries would never be read, so don't accumulate them
        }
        if (lastFlavor.size() >= PRUNE_SIZE) {
            lastFlavor.values().removeIf(last -> last > gameTime || gameTime - last >= cooldownTicks);
        }
        lastFlavor.put(target, gameTime);
    }

    /** For tests and server shutdown (both loaders call this on SERVER_STOPPING). */
    public static void clear() {
        lastFlavor.clear();
    }

    /** For tests. */
    static int size() {
        return lastFlavor.size();
    }
}
