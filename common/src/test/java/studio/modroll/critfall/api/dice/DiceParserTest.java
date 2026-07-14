package studio.modroll.critfall.api.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DiceParserTest {

    @Test
    void parsesSimpleDice() {
        DiceExpression expr = DiceExpression.parse("2d6");
        assertEquals("2d6", expr.toString());
        assertEquals(2, expr.minValue());
        assertEquals(12, expr.maxValue());
    }

    @Test
    void countDefaultsToOne() {
        DiceExpression expr = DiceExpression.parse("d20");
        assertEquals("1d20", expr.toString());
        assertEquals(1, expr.minValue());
        assertEquals(20, expr.maxValue());
    }

    @Test
    void parsesPositiveModifier() {
        DiceExpression expr = DiceExpression.parse("2d6+3");
        assertEquals(5, expr.minValue());
        assertEquals(15, expr.maxValue());
    }

    @Test
    void parsesNegativeModifier() {
        DiceExpression expr = DiceExpression.parse("2d6-1");
        assertEquals(1, expr.minValue());
        assertEquals(11, expr.maxValue());
    }

    @Test
    void parsesConstantOnly() {
        DiceExpression expr = DiceExpression.parse("5");
        assertEquals(5, expr.minValue());
        assertEquals(5, expr.maxValue());
    }

    @Test
    void parsesMultipleDiceTerms() {
        DiceExpression expr = DiceExpression.parse("1d8+2d6+3");
        assertEquals(6, expr.minValue());
        assertEquals(23, expr.maxValue());
    }

    @Test
    void plusConcatenatesTerms() {
        DiceExpression combined = DiceExpression.parse("1d8+2").plus(DiceExpression.parse("1d4"));
        assertEquals("1d8+2+1d4", combined.toString());
        assertEquals(4, combined.minValue());
        assertEquals(14, combined.maxValue());
    }

    @Test
    void plusKeepsNegativeLeadingTerms() {
        DiceExpression combined = DiceExpression.parse("1d8").plus(DiceExpression.parse("-1"));
        assertEquals("1d8-1", combined.toString());
        assertEquals(0, combined.minValue());
    }

    @Test
    void plusRejectsTermOverflow() {
        DiceExpression max = DiceExpression.parse("1" + "+1".repeat(99)); // exactly MAX_TERMS terms
        assertThrows(DiceParseException.class, () -> max.plus(DiceExpression.parse("1")));
    }

    @Test
    void parsesKeepHighest() {
        DiceExpression expr = DiceExpression.parse("2d20kh1");
        assertEquals("2d20kh1", expr.toString());
        assertEquals(1, expr.minValue());
        assertEquals(20, expr.maxValue());
    }

    @Test
    void parsesKeepLowest() {
        DiceExpression expr = DiceExpression.parse("4d6kl3");
        assertEquals("4d6kl3", expr.toString());
        assertEquals(3, expr.minValue());
        assertEquals(18, expr.maxValue());
    }

    @Test
    void keepCountDefaultsToOne() {
        assertEquals("2d20kh1", DiceExpression.parse("2d20kh").toString());
        assertEquals("2d20kl1", DiceExpression.parse("2d20kl").toString());
    }

    @Test
    void ignoresWhitespace() {
        assertEquals("2d6+3", DiceExpression.parse("  2d6 + 3 ").toString());
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("2d20kh1", DiceExpression.parse("2D20KH1").toString());
    }

    @Test
    void parsesLeadingMinus() {
        DiceExpression expr = DiceExpression.parse("-1d4+10");
        assertEquals(6, expr.minValue());
        assertEquals(9, expr.maxValue());
    }

    @Test
    void allowsSingleSidedDice() {
        DiceExpression expr = DiceExpression.parse("3d1");
        assertEquals(3, expr.minValue());
        assertEquals(3, expr.maxValue());
    }

    @Test
    void acceptsMaximumLimits() {
        assertNotNull(DiceExpression.parse("100d1000"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2d6",
                "1d20",
                "2d20kh1",
                "4d6kl3",
                "2d6+3",
                "2d6-1",
                "1d8+2d6+3",
                "5",
                "-1d4+10",
            })
    void canonicalFormRoundTrips(String canonical) {
        assertEquals(canonical, DiceExpression.parse(canonical).toString());
        assertEquals(
                canonical,
                DiceExpression.parse(DiceExpression.parse(canonical).toString()).toString());
    }

    @ParameterizedTest
    @CsvSource({
        "'', empty",
        "'   ', blank",
        "d, missing sides",
        "2d, missing sides",
        "+, bare operator",
        "2d6+, trailing operator",
        "abc, garbage",
        "2x6, wrong separator",
        "2d6foo, trailing garbage",
        "0d6, zero dice",
        "2d0, zero sides",
        "2d6kh0, keep zero",
        "2d6kh3, keep more than rolled",
        "kh1, keep without dice",
        "1d6++2, double operator",
        "101d6, too many dice",
        "1d1001, too many sides",
        "2d-6, negative sides",
    })
    void rejectsInvalidInput(String input, String why) {
        DiceParseException e = assertThrows(DiceParseException.class, () -> DiceExpression.parse(input), why);
        assertNotNull(e.getMessage(), why);
        assertFalse(e.getMessage().isBlank(), why);
    }

    @Test
    void rejectsNull() {
        assertThrows(DiceParseException.class, () -> DiceExpression.parse(null));
    }
}
