package studio.modroll.critfall.api.combat;

import studio.modroll.critfall.api.dice.RollDetail;

/**
 * The target's saving throw against a save-based spell (M5, PLAN.md §4.2): meets-it-beats-it, no
 * nat-1/nat-20 special cases (5e saves have none). The damage consequence is applied by the caller.
 *
 * @param roll how the d20 was rolled: mode, kept face, and the dropped face under advantage/disadvantage
 */
public record SaveResult(int natural, int saveTotal, int dc, RollDetail roll) {

    public SaveResult(int natural, int saveTotal, int dc) {
        this(natural, saveTotal, dc, RollDetail.normal(natural));
    }

    public boolean saved() {
        return saveTotal >= dc;
    }
}
