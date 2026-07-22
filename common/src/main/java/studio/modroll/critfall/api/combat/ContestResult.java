package studio.modroll.critfall.api.combat;

import studio.modroll.critfall.api.dice.RollDetail;

/**
 * Result of a contested check; ties go to the opponent (5e default), so a win needs a strictly higher
 * total. Each side rolls under its own mode, so each side carries its own {@link RollDetail}.
 */
public record ContestResult(
        int initiatorNatural,
        int initiatorTotal,
        int opponentNatural,
        int opponentTotal,
        RollDetail initiatorRoll,
        RollDetail opponentRoll) {

    public ContestResult(int initiatorNatural, int initiatorTotal, int opponentNatural, int opponentTotal) {
        this(
                initiatorNatural,
                initiatorTotal,
                opponentNatural,
                opponentTotal,
                RollDetail.normal(initiatorNatural),
                RollDetail.normal(opponentNatural));
    }

    public boolean initiatorWins() {
        return initiatorTotal > opponentTotal;
    }

    public ContestSide winner() {
        return initiatorWins() ? ContestSide.INITIATOR : ContestSide.OPPONENT;
    }
}
