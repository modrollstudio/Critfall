package studio.modroll.critfall.api.combat;

/** Result of a contested check; ties go to the opponent (5e default), so a win needs a strictly higher total. */
public record ContestResult(int initiatorNatural, int initiatorTotal, int opponentNatural, int opponentTotal) {

    public boolean initiatorWins() {
        return initiatorTotal > opponentTotal;
    }

    public ContestSide winner() {
        return initiatorWins() ? ContestSide.INITIATOR : ContestSide.OPPONENT;
    }
}
