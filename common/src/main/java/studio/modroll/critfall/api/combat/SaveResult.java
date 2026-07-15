package studio.modroll.critfall.api.combat;

/**
 * The target's saving throw against a save-based spell (M5, PLAN.md §4.2): meets-it-beats-it, no
 * nat-1/nat-20 special cases (5e saves have none). The damage consequence is applied by the caller.
 */
public record SaveResult(int natural, int saveTotal, int dc) {
    public boolean saved() {
        return saveTotal >= dc;
    }
}
