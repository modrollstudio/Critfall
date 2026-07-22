package studio.modroll.critfall.api.combat;

import studio.modroll.critfall.api.dice.RollDetail;

/**
 * Everything the damage pipeline needs to know after an attack roll was resolved.
 *
 * @param outcome miss / fumble / hit / crit
 * @param natural the face the (kept) d20 showed, before any bonus
 * @param attackTotal natural + attack bonus
 * @param armorClass the effective AC the roll was made against (base defender AC + defenderAcBonus)
 * @param defenderAcBonus per-attack situational modifier to the defender's AC; may be negative
 * @param damage rolled damage on HIT, maximized dice on CRIT, 0 on MISS/FUMBLE; never negative
 * @param roll how the d20 was rolled (mode, kept face, dropped face)
 */
public record AttackResult(
        AttackOutcome outcome,
        int natural,
        int attackTotal,
        int armorClass,
        int defenderAcBonus,
        int damage,
        RollDetail roll) {

    public AttackResult(
            AttackOutcome outcome, int natural, int attackTotal, int armorClass, int defenderAcBonus, int damage) {
        this(outcome, natural, attackTotal, armorClass, defenderAcBonus, damage, RollDetail.normal(natural));
    }

    public AttackResult(AttackOutcome outcome, int natural, int attackTotal, int armorClass, int damage) {
        this(outcome, natural, attackTotal, armorClass, 0, damage);
    }

    public boolean isHit() {
        return outcome == AttackOutcome.HIT || outcome == AttackOutcome.CRIT;
    }

    public int baseArmorClass() {
        return armorClass - defenderAcBonus;
    }

    /** A copy with the damage replaced — used to apply {@code PostAttackRollEvent} adjustments. */
    public AttackResult withDamage(int newDamage) {
        return new AttackResult(outcome, natural, attackTotal, armorClass, defenderAcBonus, newDamage, roll);
    }
}
