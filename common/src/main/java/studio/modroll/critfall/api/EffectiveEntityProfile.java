package studio.modroll.critfall.api;

import java.util.Optional;
import java.util.OptionalInt;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.dice.DiceExpression;

/**
 * The resolved combat stats for an entity — each field is the datapack profile value if present, or
 * the attribute-derived fallback otherwise (PLAN §4.3), matching what the live pipeline uses. Query
 * these via {@link RollService#effectiveEntity}.
 */
public record EffectiveEntityProfile(
        int armorClass,
        int attackBonus,
        int saveBonus,
        Optional<DiceExpression> meleeDamage,
        Optional<DiceExpression> rangedDamage,
        int critRange) {

    public static EffectiveEntityProfile of(
            Optional<EntityProfile> profile, double armorAttr, double toughnessAttr, double attackDamageAttr) {
        int ac = profile.map(EntityProfile::armorClass)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElseGet(() -> Derivation.armorClass(armorAttr, toughnessAttr));
        int attackBonus = profile.map(EntityProfile::attackBonus)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElseGet(() -> Derivation.attackBonus(attackDamageAttr));
        int saveBonus = profile.map(EntityProfile::saveBonus)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElse(0);
        Optional<DiceExpression> melee = profile.flatMap(EntityProfile::meleeDamage);
        Optional<DiceExpression> ranged = profile.flatMap(EntityProfile::rangedDamage);
        int critRange = AttackDice.entityCritRange(profile);
        return new EffectiveEntityProfile(ac, attackBonus, saveBonus, melee, ranged, critRange);
    }
}
