package studio.modroll.critfall.feedback;

import java.util.List;
import java.util.Optional;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.data.FlavorLines;

/** Randomly picks one flavor translation key for an outcome, using the injected RNG. */
public final class FlavorSelector {

    private FlavorSelector() {}

    public static Optional<String> pick(FlavorLines lines, String outcome, DiceRoller roller) {
        List<String> keys = lines.lines(outcome);
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        if (keys.size() == 1) {
            return Optional.of(keys.getFirst());
        }
        // Uniform 1..N pick through the same injected RNG primitive weighted outcome tables use.
        int index = roller.die(keys.size()) - 1;
        return Optional.of(keys.get(index));
    }
}
