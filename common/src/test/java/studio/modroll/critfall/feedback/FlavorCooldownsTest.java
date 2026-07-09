package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        FlavorCooldowns.record(target, 100, 20);
        assertTrue(FlavorCooldowns.isOnCooldown(target, 100, 20));
        assertTrue(FlavorCooldowns.isOnCooldown(target, 119, 20));
        assertFalse(FlavorCooldowns.isOnCooldown(target, 120, 20));
    }

    @Test
    void zeroCooldownDisablesTracking() {
        FlavorCooldowns.record(target, 100, 20);
        assertFalse(FlavorCooldowns.isOnCooldown(target, 101, 0));
    }

    @Test
    void staleEntryFromAPreviousWorldIsNotOnCooldown() {
        FlavorCooldowns.record(target, 1_000_000, 20);
        assertFalse(FlavorCooldowns.isOnCooldown(target, 5, 20));
    }

    @Test
    void recordStoresNothingWhenCooldownDisabled() {
        FlavorCooldowns.record(target, 100, 0);
        assertEquals(0, FlavorCooldowns.size(), "disabled cooldown must not accumulate entries");
    }

    @Test
    void expiredEntriesArePrunedOnceMapGrows() {
        for (int i = 0; i < 5000; i++) {
            FlavorCooldowns.record(UUID.randomUUID(), 0, 20);
        }
        FlavorCooldowns.record(target, 10_000, 20);
        assertTrue(
                FlavorCooldowns.size() < 5000, "long-expired entries must be evicted, size=" + FlavorCooldowns.size());
        assertTrue(FlavorCooldowns.isOnCooldown(target, 10_010, 20), "the live entry survives the prune");
    }
}
