package studio.modroll.critfall.combat;

/**
 * Plain-text roll feedback for the action bar. Temporary M2 debug output — M6 replaces this with
 * a proper client feedback module (packet, sounds, localization).
 */
public final class CombatText {

    private CombatText() {}

    /** e.g. {@code save d20 14+2=16 vs DC 13 - SAVED, half damage} / {@code ... - FAILED 2d6 = 7}. */
    public static String describeSave(
            CombatEngine.SaveResult result,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage) {
        int bonus = result.saveTotal() - result.natural();
        String save = "save d20 " + result.natural() + (bonus >= 0 ? "+" + bonus : String.valueOf(bonus)) + "="
                + result.saveTotal() + " vs DC " + result.dc();
        if (!result.saved()) {
            return save + " - FAILED" + (showDamage ? " " + diceNotation + " = " + damage : "");
        }
        return switch (onSuccess) {
            case NEGATE -> save + " - SAVED, no damage";
            case HALF -> save + " - SAVED, half damage" + (showDamage ? " " + damage : "");
        };
    }

    /** {@code showDamage} is false when damage dice are disabled and the vanilla amount applied. */
    public static String describe(AttackResult result, String diceNotation, boolean showDamage) {
        int bonus = result.attackTotal() - result.natural();
        String attack = "d20 " + result.natural() + (bonus >= 0 ? "+" + bonus : String.valueOf(bonus)) + "="
                + result.attackTotal() + " vs AC " + result.armorClass();
        String damage = showDamage ? " " + diceNotation + " = " + result.damage() : "";
        return switch (result.outcome()) {
            case MISS -> attack + " - MISS";
            case FUMBLE -> attack + " - FUMBLE!";
            case HIT -> attack + " - HIT" + damage;
            case CRIT -> attack + " - CRIT!" + damage;
        };
    }
}
