package studio.modroll.critfall.api.dice;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * How one d20 check was rolled.
 *
 * @param mode the roll mode the check used
 * @param kept the face the check resolved on (the same value as the result's {@code natural})
 * @param dropped the other face when advantage/disadvantage rolled two dice; empty under
 *     {@link RollMode#NORMAL}
 */
public record RollDetail(RollMode mode, int kept, OptionalInt dropped) {

    public RollDetail {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(dropped, "dropped");
    }

    public static RollDetail normal(int kept) {
        return new RollDetail(RollMode.NORMAL, kept, OptionalInt.empty());
    }

    public static RollDetail of(RollMode mode, RollResult result) {
        return new RollDetail(
                mode,
                result.keptDice().getFirst().value(),
                result.dice().stream()
                        .filter(die -> !die.kept())
                        .mapToInt(DieRoll::value)
                        .findFirst());
    }

    public boolean hasTwoDice() {
        return dropped.isPresent();
    }
}
