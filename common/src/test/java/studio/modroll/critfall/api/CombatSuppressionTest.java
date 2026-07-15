package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CombatSuppressionTest {

    private final UUID id = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        CombatSuppression.clearAllForTesting();
    }

    @Test
    void notSuppressedByDefault() {
        assertFalse(CombatSuppression.isSuppressed(id));
    }

    @Test
    void suppressThenRelease() {
        CombatSuppression.suppress(id);
        assertTrue(CombatSuppression.isSuppressed(id));
        CombatSuppression.release(id);
        assertFalse(CombatSuppression.isSuppressed(id));
    }

    @Test
    void clearAllForTestingRemovesAll() {
        CombatSuppression.suppress(id);
        CombatSuppression.suppress(UUID.randomUUID());
        CombatSuppression.clearAllForTesting();
        assertFalse(CombatSuppression.isSuppressed(id));
        assertTrue(CombatSuppression.suppressedUuids().isEmpty());
    }

    @Test
    void suppressedUuidsReflectsSuppressAndRelease() {
        CombatSuppression.suppress(id);
        assertTrue(CombatSuppression.suppressedUuids().contains(id));
        CombatSuppression.release(id);
        assertTrue(CombatSuppression.suppressedUuids().isEmpty());
    }

    @Test
    void suppressedUuidsIsUnmodifiable() {
        CombatSuppression.suppress(id);
        Set<UUID> view = CombatSuppression.suppressedUuids();
        assertThrows(UnsupportedOperationException.class, () -> view.remove(id));
        assertThrows(UnsupportedOperationException.class, () -> view.add(UUID.randomUUID()));
        assertTrue(CombatSuppression.isSuppressed(id));
    }

    @Test
    void suppressedUuidsIsLiveView() {
        Set<UUID> view = CombatSuppression.suppressedUuids();
        CombatSuppression.suppress(id);
        assertTrue(view.contains(id));
    }
}
