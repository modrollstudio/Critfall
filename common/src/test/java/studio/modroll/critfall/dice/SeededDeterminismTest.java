package studio.modroll.critfall.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeededDeterminismTest {

    private static final List<String> EXPRESSIONS =
            List.of("1d20", "2d20kh1", "2d20kl1", "4d6kl3", "2d6+3", "1d8+2d6+3", "-1d4+10", "100d1000");

    @ParameterizedTest
    @ValueSource(longs = {0L, 42L, -1L, 123456789L})
    void sameSeedProducesIdenticalRolls(long seed) {
        for (String expression : EXPRESSIONS) {
            DiceRoller first = new DiceRoller(new Random(seed));
            DiceRoller second = new DiceRoller(new Random(seed));
            DiceExpression expr = DiceExpression.parse(expression);
            for (int i = 0; i < 200; i++) {
                RollResult a = first.roll(expr);
                RollResult b = second.roll(expr);
                assertEquals(a.total(), b.total(), expression);
                assertEquals(
                        a.dice().stream().map(DieRoll::value).toList(),
                        b.dice().stream().map(DieRoll::value).toList(),
                        expression);
            }
        }
    }

    @Test
    void differentSeedsProduceDifferentSequences() {
        DiceExpression d20 = DiceExpression.parse("1d20");
        DiceRoller first = new DiceRoller(new Random(1));
        DiceRoller second = new DiceRoller(new Random(2));
        List<Integer> a = new ArrayList<>();
        List<Integer> b = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            a.add(first.roll(d20).total());
            b.add(second.roll(d20).total());
        }
        assertNotEquals(a, b);
    }

    @Test
    void parsingHasNoHiddenState() {
        DiceRoller first = new DiceRoller(new Random(7));
        DiceRoller second = new DiceRoller(new Random(7));
        int a = first.roll(DiceExpression.parse("3d6+2")).total();
        int b = second.roll(DiceExpression.parse("3d6+2")).total();
        assertEquals(a, b);
    }

    @Test
    void everyResultStaysWithinDeclaredBounds() {
        Random rng = new Random(99);
        DiceRoller roller = new DiceRoller(rng);
        for (String expression : EXPRESSIONS) {
            DiceExpression expr = DiceExpression.parse(expression);
            for (int i = 0; i < 1000; i++) {
                RollResult result = roller.roll(expr);
                assertTrue(
                        result.total() >= expr.minValue() && result.total() <= expr.maxValue(),
                        expression + " produced " + result.total() + " outside [" + expr.minValue() + ", "
                                + expr.maxValue() + "]");
                for (DieRoll die : result.dice()) {
                    assertTrue(
                            die.value() >= 1 && die.value() <= die.sides(),
                            expression + " rolled " + die.value() + " on a d" + die.sides());
                }
            }
        }
    }

    @Test
    void allFacesAreReachable() {
        DiceRoller roller = new DiceRoller(new Random(4242));
        DiceExpression d6 = DiceExpression.parse("1d6");
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(roller.roll(d6).total());
        }
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), seen);
    }
}
