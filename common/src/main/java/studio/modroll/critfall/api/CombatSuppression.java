package studio.modroll.critfall.api;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-entity suppression flag from PLAN §12: while an entity is suppressed, an external
 * orchestrator (e.g. the turn-based companion mod) owns its combat and Critfall's automatic damage
 * interception stands down for any attack it is involved in. In-memory only — encounters are
 * transient, so a server restart (which ends any encounter) clears everything. Keyed by entity UUID.
 */
public final class CombatSuppression {

    private static final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();

    private CombatSuppression() {}

    public static void suppress(UUID entity) {
        suppressed.add(entity);
    }

    public static void release(UUID entity) {
        suppressed.remove(entity);
    }

    public static boolean isSuppressed(UUID entity) {
        return suppressed.contains(entity);
    }

    /** For tests and server shutdown. */
    public static void clear() {
        suppressed.clear();
    }
}
