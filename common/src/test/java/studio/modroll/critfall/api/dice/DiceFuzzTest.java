package studio.modroll.critfall.api.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import org.junit.jupiter.api.Test;

class DiceFuzzTest {

    @Test
    void randomValidExpressionsParseRollAndRoundTrip() {
        Random fuzz = new Random(20260703L);
        DiceRoller roller = new DiceRoller(new Random(1));
        for (int i = 0; i < 500; i++) {
            String expression = randomValidExpression(fuzz);
            DiceExpression expr = DiceExpression.parse(expression);
            assertEquals(
                    expr.toString(),
                    DiceExpression.parse(expr.toString()).toString(),
                    "canonical form must round-trip for " + expression);
            RollResult result = roller.roll(expr);
            assertTrue(
                    result.total() >= expr.minValue() && result.total() <= expr.maxValue(),
                    expression + " rolled " + result.total() + " outside bounds");
        }
    }

    @Test
    void randomGarbageNeverThrowsAnythingButDiceParseException() {
        Random fuzz = new Random(31337L);
        String alphabet = "0123456789dkhl+- x!";
        for (int i = 0; i < 2000; i++) {
            StringBuilder sb = new StringBuilder();
            int length = fuzz.nextInt(12);
            for (int c = 0; c < length; c++) {
                sb.append(alphabet.charAt(fuzz.nextInt(alphabet.length())));
            }
            String input = sb.toString();
            try {
                DiceExpression.parse(input);
            } catch (DiceParseException expected) {
                // fine — invalid input must be reported through this exception only
            } catch (RuntimeException e) {
                fail("parse(\"" + input + "\") threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private static String randomValidExpression(Random fuzz) {
        StringBuilder sb = new StringBuilder();
        int terms = 1 + fuzz.nextInt(3);
        for (int t = 0; t < terms; t++) {
            if (t > 0) {
                sb.append(fuzz.nextBoolean() ? "+" : "-");
            }
            if (fuzz.nextInt(4) == 0) {
                sb.append(1 + fuzz.nextInt(50)); // constant term
            } else {
                int count = 1 + fuzz.nextInt(100);
                int sides = 1 + fuzz.nextInt(1000);
                sb.append(count).append('d').append(sides);
                if (count > 1 && fuzz.nextBoolean()) {
                    sb.append(fuzz.nextBoolean() ? "kh" : "kl").append(1 + fuzz.nextInt(count));
                }
            }
        }
        return sb.toString();
    }
}
