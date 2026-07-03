package studio.modroll.critfall.combat;

/**
 * Feature flags for every combat mechanic. Hardcoded defaults for M2 — M3 loads this from
 * {@code config/critfall/rules.json} instead (same shape, see PLAN.md §4.2). Turning any flag off
 * must cleanly restore vanilla behavior for that mechanic only.
 */
public record Rules(
        boolean attackRolls,
        boolean attackRollsPlayers,
        boolean attackRollsMobs,
        boolean damageDice,
        boolean crits,
        boolean nat20AlwaysHits,
        boolean fumbles,
        boolean nat1AlwaysMisses,
        boolean fumbleDurabilityBreak,
        int fumbleSetDurabilityTo,
        boolean disableVanillaArmorReduction,
        boolean feedback) {

    public static final Rules DEFAULTS = new Rules(true, true, true, true, true, true, true, true, true, 1, true, true);
}
