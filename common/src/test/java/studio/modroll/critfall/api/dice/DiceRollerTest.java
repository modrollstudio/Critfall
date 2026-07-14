package studio.modroll.critfall.api.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DiceRollerTest {

    @Test
    void singleDieUsesInjectedRng() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(13));
        RollResult result = roller.roll("1d20");
        assertEquals(13, result.total());
    }

    @Test
    void sumsMultipleDice() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(1, 3, 6));
        RollResult result = roller.roll("3d6");
        assertEquals(10, result.total());
        assertEquals(
                List.of(1, 3, 6), result.dice().stream().map(DieRoll::value).toList());
        assertTrue(result.dice().stream().allMatch(DieRoll::kept));
        assertEquals(0, result.modifier());
    }

    @Test
    void appliesPositiveModifier() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(2, 1));
        RollResult result = roller.roll("2d4+3");
        assertEquals(6, result.total());
        assertEquals(3, result.modifier());
    }

    @Test
    void totalCanGoNegative() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(1));
        RollResult result = roller.roll("1d6-2");
        assertEquals(-1, result.total());
        assertEquals(-2, result.modifier());
    }

    @Test
    void keepHighestKeepsTheHighestDie() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(5, 17));
        RollResult result = roller.roll("2d20kh1");
        assertEquals(17, result.total());
        assertEquals(2, result.dice().size());
        assertEquals(List.of(17), result.keptDice().stream().map(DieRoll::value).toList());
    }

    @Test
    void keepLowestKeepsTheLowestDie() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(5, 17));
        RollResult result = roller.roll("2d20kl1");
        assertEquals(5, result.total());
        assertEquals(List.of(5), result.keptDice().stream().map(DieRoll::value).toList());
    }

    @Test
    void keepHighestThreeOfFour() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(4, 1, 6, 3));
        RollResult result = roller.roll("4d6kh3");
        assertEquals(13, result.total());
        assertEquals(3, result.keptDice().size());
        assertEquals(4, result.dice().size());
    }

    @Test
    void keepWithTiesKeepsExactlyTheRequestedCount() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(4, 4, 4));
        RollResult result = roller.roll("3d6kh2");
        assertEquals(8, result.total());
        assertEquals(2, result.keptDice().size());
    }

    @Test
    void advantageKeepsHigherOfTwoD20s() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(1, 20));
        RollResult result = roller.d20(RollMode.ADVANTAGE);
        assertEquals(20, result.total());
        assertEquals(2, result.dice().size());
        assertEquals(List.of(20), result.keptDice().stream().map(DieRoll::value).toList());
    }

    @Test
    void disadvantageKeepsLowerOfTwoD20s() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(18, 3));
        RollResult result = roller.d20(RollMode.DISADVANTAGE);
        assertEquals(3, result.total());
        assertEquals(2, result.dice().size());
    }

    @Test
    void normalD20RollsExactlyOnce() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(7);
        DiceRoller roller = new DiceRoller(rng);
        RollResult result = roller.d20(RollMode.NORMAL);
        assertEquals(7, result.total());
        assertEquals(1, result.dice().size());
        assertTrue(rng.isExhausted());
    }

    @Test
    void canForceANaturalTwenty() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(20));
        assertEquals(20, roller.d20(RollMode.NORMAL).total());
    }

    @Test
    void canForceANaturalOne() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(1));
        assertEquals(1, roller.d20(RollMode.NORMAL).total());
    }

    @Test
    void rollsTermsLeftToRight() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(8, 2, 5));
        RollResult result = roller.roll("1d8+2d6+1");
        assertEquals(16, result.total());
        assertEquals(
                List.of(8, 2, 5), result.dice().stream().map(DieRoll::value).toList());
        assertEquals(1, result.modifier());
    }

    @Test
    void totalIsKeptDiceSumPlusModifier() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(4, 1, 6, 3));
        RollResult result = roller.roll("4d6kh3+2");
        int keptSum = result.keptDice().stream().mapToInt(DieRoll::value).sum();
        assertEquals(keptSum + result.modifier(), result.total());
        assertEquals(15, result.total());
    }

    @Test
    void subtractedDiceCountAgainstTotal() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(6, 2));
        RollResult result = roller.roll("1d8-1d4");
        assertEquals(4, result.total());
    }

    @Test
    void acceptsPreParsedExpressions() {
        DiceExpression expr = DiceExpression.parse("2d6+3");
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(4, 4));
        assertEquals(11, roller.roll(expr).total());
    }

    @Test
    void dieRollsRememberTheirSides() {
        DiceRoller roller = new DiceRoller(SequenceRandom.ofDieFaces(8, 2, 5));
        RollResult result = roller.roll("1d8+2d6");
        assertEquals(
                List.of(8, 6, 6), result.dice().stream().map(DieRoll::sides).toList());
    }
}
