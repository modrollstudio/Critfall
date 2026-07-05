package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlavorCooldownsTest {

    private final UUID target = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        FlavorCooldowns.clear();
    }

    @Test
    void notOnCooldownInitially() {
        assertFalse(FlavorCooldowns.isOnCooldown(target, 100, 20));
    }

    @Test
    void onCooldownWithinWindowThenExpires() {
        FlavorCooldowns.record(target, 100);
        assertTrue(FlavorCooldowns.isOnCooldown(target, 100, 20));
        assertTrue(FlavorCooldowns.isOnCooldown(target, 119, 20));
        assertFalse(FlavorCooldowns.isOnCooldown(target, 120, 20));
    }

    @Test
    void zeroCooldownDisablesTracking() {
        FlavorCooldowns.record(target, 100);
        assertFalse(FlavorCooldowns.isOnCooldown(target, 101, 0));
    }
}
