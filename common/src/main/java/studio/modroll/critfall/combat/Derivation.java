package studio.modroll.critfall.combat;

import studio.modroll.critfall.dice.DiceExpression;

/**
 * Fallback derivation (PLAN.md §4.3): plausible tabletop stats computed from vanilla attribute
 * values so the mod works with zero configuration in any modpack. The numbers here are the M2
 * hardcoded defaults — M3 replaces them with datapack profiles and keeps these only as the
 * last-resort fallback.
 */
public final class Derivation {

    private static final int MAX_ATTACK_BONUS = 12;

    /** Flat damage 1..15 mapped to the dice expression with the same average (±0.5). */
    private static final String[] DICE_TABLE = {
        "1", "1d4", "1d6", "1d8", "1d10", "1d12", "2d6", "2d6+1", "2d8", "2d8+1", "2d10", "2d10+1", "2d12", "2d12+1",
        "2d12+2",
    };

    private Derivation() {}

    /** {@code AC = 10 + floor(armor / 2) + floor(toughness / 4)}, never below 1. */
    public static int armorClass(double armor, double toughness) {
        int ac = 10 + (int) Math.floor(armor / 2.0) + (int) Math.floor(toughness / 4.0);
        return Math.max(1, ac);
    }

    /** {@code floor(attackDamage / 2)}, clamped to {@code [0, 12]}. */
    public static int attackBonus(double attackDamage) {
        return Math.clamp((int) Math.floor(attackDamage / 2.0), 0, MAX_ATTACK_BONUS);
    }

    /**
     * Maps a flat vanilla damage value to the dice expression whose average is within 0.5 of it
     * (e.g. {@code 3 → 1d6}, {@code 7 → 2d6}, {@code 8 → 2d6+1}). Above the table, buckets of
     * d12s with a flat adjustment keep the same average.
     */
    public static DiceExpression damageDice(double flatDamage) {
        int target = Math.max(1, (int) Math.round(flatDamage));
        if (target <= DICE_TABLE.length) {
            return DiceExpression.parse(DICE_TABLE[target - 1]);
        }
        int count = Math.max(2, (int) Math.round(target / 6.5));
        int adjustment = target - (int) Math.round(count * 6.5);
        String notation =
                count + "d12" + (adjustment > 0 ? "+" + adjustment : adjustment < 0 ? String.valueOf(adjustment) : "");
        return DiceExpression.parse(notation);
    }
}
