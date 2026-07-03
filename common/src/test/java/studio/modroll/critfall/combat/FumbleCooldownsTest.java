package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FumbleCooldownsTest {

    private final UUID attacker = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        FumbleCooldowns.clear();
    }

    @Test
    void neverOnCooldownBeforeFirstFumble() {
        assertFalse(FumbleCooldowns.isOnCooldown(attacker, 1000, 200));
    }

    @Test
    void onCooldownWithinWindowThenExpires() {
        FumbleCooldowns.record(attacker, 1000);
        assertTrue(FumbleCooldowns.isOnCooldown(attacker, 1000, 200));
        assertTrue(FumbleCooldowns.isOnCooldown(attacker, 1199, 200));
        assertFalse(FumbleCooldowns.isOnCooldown(attacker, 1200, 200), "cooldown ends after exactly cooldownTicks");
    }

    @Test
    void zeroCooldownDisablesTracking() {
        FumbleCooldowns.record(attacker, 1000);
        assertFalse(FumbleCooldowns.isOnCooldown(attacker, 1001, 0));
    }

    @Test
    void cooldownIsPerAttacker() {
        FumbleCooldowns.record(attacker, 1000);
        assertFalse(FumbleCooldowns.isOnCooldown(UUID.randomUUID(), 1001, 200));
    }
}
