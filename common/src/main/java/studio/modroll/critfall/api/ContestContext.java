package studio.modroll.critfall.api;

import java.util.Objects;
import studio.modroll.critfall.api.dice.RollMode;

/** Per-side bonus and roll mode for a contest; bonuses are caller-supplied since Critfall models no skills. */
public record ContestContext(int initiatorBonus, RollMode initiatorMode, int opponentBonus, RollMode opponentMode) {

    public ContestContext {
        Objects.requireNonNull(initiatorMode, "initiatorMode");
        Objects.requireNonNull(opponentMode, "opponentMode");
    }

    public static ContestContext of(int initiatorBonus, int opponentBonus) {
        return new ContestContext(initiatorBonus, RollMode.NORMAL, opponentBonus, RollMode.NORMAL);
    }

    public ContestContext withInitiatorMode(RollMode mode) {
        return new ContestContext(initiatorBonus, mode, opponentBonus, opponentMode);
    }

    public ContestContext withOpponentMode(RollMode mode) {
        return new ContestContext(initiatorBonus, initiatorMode, opponentBonus, mode);
    }
}
