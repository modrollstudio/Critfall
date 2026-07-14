package studio.modroll.critfall.api.dice;

import java.util.List;

/**
 * Outcome of rolling a {@link DiceExpression}: the grand total plus a per-die breakdown for
 * feedback display and natural-roll (nat 1 / nat 20) detection.
 *
 * @param total grand total including sign of each term and the flat modifier
 * @param dice every die rolled, in roll order (left-to-right through the expression)
 * @param modifier the sum of all flat constant terms
 */
public record RollResult(int total, List<DieRoll> dice, int modifier) {

    public RollResult {
        dice = List.copyOf(dice);
    }

    /** Only the dice that count toward the total (kh/kl drops the rest). */
    public List<DieRoll> keptDice() {
        return dice.stream().filter(DieRoll::kept).toList();
    }
}
