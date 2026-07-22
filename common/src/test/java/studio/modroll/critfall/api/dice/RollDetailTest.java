package studio.modroll.critfall.api.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class RollDetailTest {

    @Test
    void normalRollKeepsOneDieAndDropsNothing() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(13));
        RollDetail detail = RollDetail.of(RollMode.NORMAL, roller.d20(RollMode.NORMAL));
        assertEquals(RollMode.NORMAL, detail.mode());
        assertEquals(13, detail.kept());
        assertEquals(OptionalInt.empty(), detail.dropped());
        assertFalse(detail.hasTwoDice(), "a normal roll must not report a phantom second die");
    }

    @Test
    void advantageReportsBothFacesAndKeepsTheHigher() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(7, 18));
        RollDetail detail = RollDetail.of(RollMode.ADVANTAGE, roller.d20(RollMode.ADVANTAGE));
        assertEquals(RollMode.ADVANTAGE, detail.mode());
        assertEquals(18, detail.kept());
        assertEquals(OptionalInt.of(7), detail.dropped());
        assertTrue(detail.hasTwoDice());
    }

    @Test
    void disadvantageReportsBothFacesAndKeepsTheLower() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(19, 4));
        RollDetail detail = RollDetail.of(RollMode.DISADVANTAGE, roller.d20(RollMode.DISADVANTAGE));
        assertEquals(4, detail.kept());
        assertEquals(OptionalInt.of(19), detail.dropped());
    }

    @Test
    void normalFactoryMatchesARolledNormal() {
        assertEquals(new RollDetail(RollMode.NORMAL, 9, OptionalInt.empty()), RollDetail.normal(9));
    }
}
