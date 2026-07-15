package studio.modroll.critfall.combat;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backing state for {@link studio.modroll.critfall.api.CombatSuppression}, internal so the global
 * wipe stays off the public surface: both loaders clear it on server stop.
 */
public final class SuppressionStore {

    private static final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> view = Collections.unmodifiableSet(suppressed);

    private SuppressionStore() {}

    public static void suppress(UUID entity) {
        suppressed.add(entity);
    }

    public static void release(UUID entity) {
        suppressed.remove(entity);
    }

    public static boolean isSuppressed(UUID entity) {
        return suppressed.contains(entity);
    }

    public static Set<UUID> view() {
        return view;
    }

    public static void clear() {
        suppressed.clear();
    }
}
