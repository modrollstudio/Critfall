package studio.modroll.critfall.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-target anti-spam gate for narrative flavor lines (PLAN §4.5): at most one non-priority flavor
 * line per {@code cooldown_ticks} per target. Priority lines (nat 20 crit / nat 1 fumble) bypass this
 * in {@link FeedbackBuilder}. Keyed by target UUID against the level game time — same pattern as
 * {@link studio.modroll.critfall.combat.FumbleCooldowns}; stale entries are harmless, no eviction.
 */
public final class FlavorCooldowns {

    private static final Map<UUID, Long> lastFlavor = new ConcurrentHashMap<>();

    private FlavorCooldowns() {}

    public static boolean isOnCooldown(UUID target, long gameTime, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = lastFlavor.get(target);
        return last != null && gameTime - last < cooldownTicks;
    }

    public static void record(UUID target, long gameTime) {
        lastFlavor.put(target, gameTime);
    }

    /** For tests and server shutdown. */
    public static void clear() {
        lastFlavor.clear();
    }
}
