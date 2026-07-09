package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        FumbleCooldowns.record(attacker, 1000, 200);
        assertTrue(FumbleCooldowns.isOnCooldown(attacker, 1000, 200));
        assertTrue(FumbleCooldowns.isOnCooldown(attacker, 1199, 200));
        assertFalse(FumbleCooldowns.isOnCooldown(attacker, 1200, 200), "cooldown ends after exactly cooldownTicks");
    }

    @Test
    void zeroCooldownDisablesTracking() {
        FumbleCooldowns.record(attacker, 1000, 200);
        assertFalse(FumbleCooldowns.isOnCooldown(attacker, 1001, 0));
    }

    @Test
    void cooldownIsPerAttacker() {
        FumbleCooldowns.record(attacker, 1000, 200);
        assertFalse(FumbleCooldowns.isOnCooldown(UUID.randomUUID(), 1001, 200));
    }

    @Test
    void staleEntryFromAPreviousWorldIsNotOnCooldown() {
        // Singleplayer: fumble recorded near the end of world A (large game time), player opens
        // fresh world B (game time restarts) — the leftover future-stamped entry must not
        // suppress fumbles until world B's clock catches up.
        FumbleCooldowns.record(attacker, 1_000_000, 200);
        assertFalse(FumbleCooldowns.isOnCooldown(attacker, 5, 200));
    }

    @Test
    void recordStoresNothingWhenCooldownDisabled() {
        FumbleCooldowns.record(attacker, 1000, 0);
        assertEquals(0, FumbleCooldowns.size(), "disabled cooldown must not accumulate entries");
    }

    @Test
    void expiredEntriesArePrunedOnceMapGrows() {
        for (int i = 0; i < 5000; i++) {
            FumbleCooldowns.record(UUID.randomUUID(), 0, 200);
        }
        FumbleCooldowns.record(attacker, 10_000, 200);
        assertTrue(
                FumbleCooldowns.size() < 5000, "long-expired entries must be evicted, size=" + FumbleCooldowns.size());
        assertTrue(FumbleCooldowns.isOnCooldown(attacker, 10_050, 200), "the live entry survives the prune");
    }
}
