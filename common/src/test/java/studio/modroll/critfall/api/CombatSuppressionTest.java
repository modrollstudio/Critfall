package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CombatSuppressionTest {

    private final UUID id = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        CombatSuppression.clear();
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
    void clearRemovesAll() {
        CombatSuppression.suppress(id);
        CombatSuppression.suppress(UUID.randomUUID());
        CombatSuppression.clear();
        assertFalse(CombatSuppression.isSuppressed(id));
    }
}
