package studio.modroll.critfall.combat;

/**
 * Everything the damage pipeline needs to know after an attack roll was resolved.
 *
 * @param outcome miss / fumble / hit / crit
 * @param natural the face the (kept) d20 showed, before any bonus
 * @param attackTotal natural + attack bonus
 * @param armorClass the AC the roll was made against
 * @param damage rolled damage on HIT, maximized dice on CRIT, 0 on MISS/FUMBLE; never negative
 */
public record AttackResult(AttackOutcome outcome, int natural, int attackTotal, int armorClass, int damage) {

    public boolean isHit() {
        return outcome == AttackOutcome.HIT || outcome == AttackOutcome.CRIT;
    }

    /** A copy with the damage replaced — used to apply {@code PostAttackRollEvent} adjustments. */
    public AttackResult withDamage(int newDamage) {
        return new AttackResult(outcome, natural, attackTotal, armorClass, newDamage);
    }
}
