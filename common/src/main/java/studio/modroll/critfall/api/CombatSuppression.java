package studio.modroll.critfall.api;

import java.util.Set;
import java.util.UUID;
import studio.modroll.critfall.combat.SuppressionStore;

/**
 * The per-entity suppression flag from PLAN §12: while an entity is suppressed, an external
 * orchestrator (e.g. the turn-based companion mod) owns its combat and Critfall's automatic damage
 * interception stands down for any attack it is involved in. In-memory only — suppression is
 * cleared internally on server stop, since a restart ends any encounter. Keyed by entity UUID.
 */
public final class CombatSuppression {

    private CombatSuppression() {}

    public static void suppress(UUID entity) {
        SuppressionStore.suppress(entity);
    }

    public static void release(UUID entity) {
        SuppressionStore.release(entity);
    }

    public static boolean isSuppressed(UUID entity) {
        return SuppressionStore.isSuppressed(entity);
    }

    /**
     * An unmodifiable live view of every currently suppressed UUID, across all mods. Read-only:
     * iteration is weakly consistent under concurrent suppress/release, and mutating the view
     * throws {@link UnsupportedOperationException}.
     */
    public static Set<UUID> suppressedUuids() {
        return SuppressionStore.view();
    }

    /**
     * Wipes every mod's suppressions at once — test cleanup ONLY, never production code (it would
     * destroy other mods' running encounters). Production code releases per entity via
     * {@link #release}; Critfall itself clears internally on server stop.
     */
    public static void clearAllForTesting() {
        SuppressionStore.clear();
    }
}
