package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.SequenceRandom;
import studio.modroll.critfall.data.FlavorLines;
import studio.modroll.critfall.data.FlavorPool;

class FlavorSelectorTest {

    @Test
    void picksIndexedLineFromRoller() {
        FlavorLines lines = new FlavorLines(Map.of(FlavorPool.CRIT, List.of("k0", "k1", "k2")));
        // roll(1..3) with a forced face of 2 selects index 1
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(2));
        assertEquals(Optional.of("k1"), FlavorSelector.pick(lines, FlavorPool.CRIT, roller));
    }

    @Test
    void emptyWhenNoLines() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces());
        assertTrue(
                FlavorSelector.pick(FlavorLines.NONE, FlavorPool.CRIT, roller).isEmpty());
    }

    @Test
    void emptyWhenOutcomeHasNoLines() {
        FlavorLines lines = new FlavorLines(Map.of(FlavorPool.CRIT, List.of("k0")));
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces());
        assertTrue(FlavorSelector.pick(lines, FlavorPool.FUMBLE, roller).isEmpty());
    }
}
