package studio.modroll.critfall.api.dice;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * How one d20 check was rolled — enough for a consumer or a readout to show <em>how</em> a roll was
 * made, not just what it landed on.
 *
 * @param mode the roll mode the check used
 * @param kept the face the check resolved on (the same value as the result's {@code natural})
 * @param dropped the other face when advantage/disadvantage rolled two dice; empty under
 *     {@link RollMode#NORMAL}, which rolls one die and drops nothing
 */
public record RollDetail(RollMode mode, int kept, OptionalInt dropped) {

    public RollDetail {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(dropped, "dropped");
    }

    /** A straight one-die roll. */
    public static RollDetail normal(int kept) {
        return new RollDetail(RollMode.NORMAL, kept, OptionalInt.empty());
    }

    /** Reads the kept and dropped faces off a resolved {@link RollMode#d20Expression()} roll. */
    public static RollDetail of(RollMode mode, RollResult result) {
        return new RollDetail(
                mode,
                result.keptDice().getFirst().value(),
                result.dice().stream()
                        .filter(die -> !die.kept())
                        .mapToInt(DieRoll::value)
                        .findFirst());
    }

    /** Whether two dice were rolled, so a readout can show both faces instead of one. */
    public boolean hasTwoDice() {
        return dropped.isPresent();
    }
}
