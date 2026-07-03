package studio.modroll.critfall.combat;

import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.RollMode;

/**
 * Resolves one attack: d20 (+ bonus) vs AC, then damage dice. Pure JVM — no Minecraft classes —
 * so every path (nat 1, nat 20, exact AC…) is unit-testable with a scripted RNG.
 */
public final class CombatEngine {

    private CombatEngine() {}

    /**
     * Rolls the attack and, when it lands, the damage.
     *
     * <p>Outcome rules (each individually toggleable via {@link Rules}): a natural 1 always misses
     * and becomes a {@link AttackOutcome#FUMBLE} when fumbles are enabled; a natural 20 always
     * hits and becomes a {@link AttackOutcome#CRIT} with maximized damage dice when crits are
     * enabled; otherwise {@code natural + attackBonus >= armorClass} hits.
     */
    public static AttackResult resolveAttack(
            DiceRoller roller, Rules rules, int attackBonus, int armorClass, RollMode mode, DiceExpression damageDice) {
        int natural = roller.d20(mode).keptDice().getFirst().value();
        int attackTotal = natural + attackBonus;

        boolean forcedMiss = natural == 1 && rules.nat1AlwaysMisses();
        boolean forcedHit = natural == 20 && rules.nat20AlwaysHits();
        boolean misses = forcedMiss || (!forcedHit && attackTotal < armorClass);

        if (misses) {
            AttackOutcome outcome = natural == 1 && rules.fumbles() ? AttackOutcome.FUMBLE : AttackOutcome.MISS;
            return new AttackResult(outcome, natural, attackTotal, armorClass, 0);
        }
        if (natural == 20 && rules.crits()) {
            int damage = Math.max(0, damageDice.maxValue());
            return new AttackResult(AttackOutcome.CRIT, natural, attackTotal, armorClass, damage);
        }
        int damage = Math.max(0, roller.roll(damageDice).total());
        return new AttackResult(AttackOutcome.HIT, natural, attackTotal, armorClass, damage);
    }
}
