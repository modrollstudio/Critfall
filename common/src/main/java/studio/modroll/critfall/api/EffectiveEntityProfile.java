package studio.modroll.critfall.api;

import java.util.Optional;
import java.util.OptionalInt;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.data.EntityProfile;

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

    static EffectiveEntityProfile of(
            Optional<EntityProfile> profile, double armorAttr, double toughnessAttr, double attackDamageAttr) {
        return of(profile, armorAttr, toughnessAttr, attackDamageAttr, false);
    }

    /** @param player a derived attack bonus gets {@code balance.player_attack_bonus} on top */
    static EffectiveEntityProfile of(
            Optional<EntityProfile> profile,
            double armorAttr,
            double toughnessAttr,
            double attackDamageAttr,
            boolean player) {
        int ac = profile.map(EntityProfile::armorClass)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElseGet(() -> Derivation.armorClass(armorAttr, toughnessAttr));
        int attackBonus = profile.map(EntityProfile::attackBonus)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElseGet(() -> Derivation.attackBonus(attackDamageAttr)
                        + (player ? RollRuntime.rules().balance().playerAttackBonus() : 0));
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
