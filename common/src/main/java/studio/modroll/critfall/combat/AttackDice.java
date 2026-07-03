package studio.modroll.critfall.combat;

import java.util.Optional;
import java.util.OptionalInt;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.dice.DiceExpression;

/**
 * Picks the damage dice and crit range for one attack. Precedence: the held weapon's item
 * profile, then the attacker's entity profile melee dice, then empty — the caller applies the
 * {@code fallbacks.unknown_weapon} rule (derive from the vanilla amount, or pass through).
 * Pure JVM: profiles in, dice out.
 */
public final class AttackDice {

    public record Resolved(DiceExpression dice, int critRange) {}

    private AttackDice() {}

    public static Optional<Resolved> resolve(
            Optional<ItemProfile> weapon, Optional<EntityProfile> attacker, double attackDamageAttribute) {
        Optional<DiceExpression> weaponDice = weapon.flatMap(ItemProfile::damage);
        if (weaponDice.isPresent()) {
            DiceExpression dice = weaponDice.get();
            if (weapon.get().modifierFrom() == ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE) {
                int bonus = Derivation.itemDamageBonus(attackDamageAttribute, dice.averageValue());
                if (bonus > 0) {
                    dice = DiceExpression.parse(dice + "+" + bonus);
                }
            }
            // A weapon's crit range beats the wielder's; a keen blade stays keen in anyone's hands.
            int critRange = weapon.get().critRange().orElse(entityCritRange(attacker));
            return Optional.of(new Resolved(dice, critRange));
        }
        return attacker.flatMap(EntityProfile::meleeDamage).map(dice -> new Resolved(dice, entityCritRange(attacker)));
    }

    public static int entityCritRange(Optional<EntityProfile> profile) {
        return profile.map(EntityProfile::critRange).orElse(OptionalInt.empty()).orElse(20);
    }
}
