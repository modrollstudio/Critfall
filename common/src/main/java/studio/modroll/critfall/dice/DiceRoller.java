package studio.modroll.critfall.dice;

import java.util.random.RandomGenerator;

/**
 * Rolls dice expressions with an injected RNG. Game logic must always receive its roller (and
 * therefore its randomness) from outside so tests can force exact rolls.
 */
public final class DiceRoller {

    private final RandomGenerator rng;

    public DiceRoller(RandomGenerator rng) {
        this.rng = rng;
    }

    /** Parses and rolls in one step. Throws {@link DiceParseException} on bad input. */
    public RollResult roll(String expression) {
        return roll(DiceExpression.parse(expression));
    }

    public RollResult roll(DiceExpression expression) {
        return expression.roll(rng);
    }

    /** Rolls a d20 check under the given mode (normal, advantage, disadvantage). */
    public RollResult d20(RollMode mode) {
        return roll(mode.d20Expression());
    }
}
