package studio.modroll.critfall.combat;

/**
 * Plain-text roll feedback for the action bar. Temporary M2 debug output — M6 replaces this with
 * a proper client feedback module (packet, sounds, localization).
 */
public final class CombatText {

    private CombatText() {}

    public static String describe(AttackResult result, String diceNotation) {
        int bonus = result.attackTotal() - result.natural();
        String attack = "d20 " + result.natural() + (bonus >= 0 ? "+" + bonus : String.valueOf(bonus)) + "="
                + result.attackTotal() + " vs AC " + result.armorClass();
        return switch (result.outcome()) {
            case MISS -> attack + " - MISS";
            case FUMBLE -> attack + " - FUMBLE!";
            case HIT -> attack + " - HIT " + diceNotation + " = " + result.damage();
            case CRIT -> attack + " - CRIT! " + diceNotation + " maxed = " + result.damage();
        };
    }
}
