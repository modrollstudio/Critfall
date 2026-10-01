package studio.modroll.critfall.api.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiceModifierTest {

    private static DiceExpression dice(String text) {
        return DiceExpression.parse(text);
    }

    @Test
    void modifierSumsTheConstantTerms() {
        assertEquals(0, dice("2d6").modifier());
        assertEquals(3, dice("1d8+2+1").modifier());
        assertEquals(-1, dice("1d8+2-3").modifier());
        assertEquals(1, dice("1").modifier());
    }

    @Test
    void withModifierReplacesTheConstantsAndKeepsTheDice() {
        assertEquals("1d8+5", dice("1d8+2").withModifier(5).toString());
        assertEquals("1d8+1d4+5", dice("1d8+2+1d4").withModifier(5).toString());
        assertEquals("1d6-2", dice("1d6+1").withModifier(-2).toString());
        assertEquals("2d6+3", dice("2d6").withModifier(3).toString());
        assertEquals("1d8+1d4", dice("1d8+2+1d4").withModifier(0).toString());
    }

    @Test
    void expressionWithNoDiceIsLeftUnchanged() {
        DiceExpression flat = dice("1");
        assertSame(flat, flat.withModifier(4));
        assertSame(flat, flat.withModifier(Integer.MIN_VALUE), "no modifier to replace, so nothing to reject");
    }

    @Test
    void plusModifierAddsToTheConstantsAndKeepsTheDice() {
        assertEquals("1d8+5", dice("1d8+2").plusModifier(3).toString());
        assertEquals("1d8+1d4+5", dice("1d8+2+1d4").plusModifier(3).toString());
        assertEquals("1d6-1", dice("1d6+1").plusModifier(-2).toString());
        assertEquals("2d6+3", dice("2d6").plusModifier(3).toString());
        assertEquals("1d8", dice("1d8+2").plusModifier(-2).toString());
    }

    @Test
    void plusZeroOrNoDiceIsLeftUnchanged() {
        DiceExpression mixed = dice("1d8+2+1d4");
        assertSame(mixed, mixed.plusModifier(0));
        DiceExpression flat = dice("1");
        assertSame(flat, flat.plusModifier(4));
        assertSame(flat, flat.plusModifier(Integer.MIN_VALUE), "no dice to add to, so nothing to reject");
    }

    @Test
    void plusModifierEnforcesTheConstantLimitOnTheSum() {
        assertEquals("1d6+1000000", dice("1d6+1").plusModifier(999_999).toString());
        assertThrows(DiceParseException.class, () -> dice("1d6+1").plusModifier(1_000_000));
        assertThrows(DiceParseException.class, () -> dice("1d6").plusModifier(Integer.MIN_VALUE));
        assertThrows(DiceParseException.class, () -> dice("1d6+1000000").plusModifier(Integer.MAX_VALUE));
    }

    @Test
    void hasDiceTellsRollsFromFlatAmounts() {
        assertTrue(dice("1d6").hasDice());
        assertTrue(dice("2+1d4").hasDice());
        assertFalse(dice("1").hasDice());
        assertFalse(dice("3-1").hasDice());
    }

    @Test
    void replacedExpressionRollsWithTheNewModifier() {
        RollResult result = dice("1d6+1").withModifier(4).roll(SequenceRandom.ofDieFaces(3));
        assertEquals(7, result.total());
        assertEquals(4, result.modifier());
    }

    @Test
    void withModifierEnforcesTheConstantLimit() {
        assertThrows(DiceParseException.class, () -> dice("1d6").withModifier(Integer.MIN_VALUE));
        assertThrows(DiceParseException.class, () -> dice("1d6").withModifier(1_000_001));
    }
}
