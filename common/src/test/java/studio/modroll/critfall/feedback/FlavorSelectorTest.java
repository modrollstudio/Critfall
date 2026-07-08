package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.data.FlavorPool;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.SequenceRandom;

class FlavorSelectorTest {

    private static FlavorPool pool(Map<String, List<String>> lines) {
        return new FlavorPool(ResourceLocation.parse("test:p"), List.of(), java.util.Set.of(), lines, 0);
    }

    @Test
    void picksIndexedLineFromRoller() {
        FlavorPool pool = pool(Map.of(FlavorPool.CRIT, List.of("k0", "k1", "k2")));
        // roll(1..3) with a forced face of 2 selects index 1
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(2));
        assertEquals(Optional.of("k1"), FlavorSelector.pick(Optional.of(pool), FlavorPool.CRIT, roller));
    }

    @Test
    void emptyWhenNoPool() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces());
        assertTrue(
                FlavorSelector.pick(Optional.empty(), FlavorPool.CRIT, roller).isEmpty());
    }

    @Test
    void emptyWhenOutcomeHasNoLines() {
        FlavorPool pool = pool(Map.of(FlavorPool.CRIT, List.of("k0")));
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces());
        assertTrue(FlavorSelector.pick(Optional.of(pool), FlavorPool.FUMBLE, roller)
                .isEmpty());
    }
}
