package studio.modroll.critfall.combat;

import java.util.Optional;
import java.util.OptionalInt;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceParseException;

/**
 * Picks the damage dice and crit range for one attack. Precedence: the held weapon's item
 * profile, then the attacker's entity profile melee dice, then empty — the caller applies the
 * {@code fallbacks.unknown_weapon} rule (derive from the vanilla amount, or pass through).
 * Pure JVM: profiles in, dice out.
 */
public final class AttackDice {

    public record Resolved(DiceExpression dice, int critRange) {}

    /**
     * Pre-parsed flat-bonus terms for {@code modifier_from: attack_damage_attribute}, indexed by
     * bonus value ({@link Derivation#itemDamageBonus} clamps to 0..12) — appending via
     * {@link DiceExpression#plus} yields the identical canonical expression the previous
     * string-concat-and-reparse produced, without a per-hit parse.
     */
    private static final DiceExpression[] BONUS_TERMS = new DiceExpression[13];

    static {
        for (int i = 1; i < BONUS_TERMS.length; i++) {
            BONUS_TERMS[i] = DiceExpression.parse(Integer.toString(i));
        }
    }

    private AttackDice() {}

    public static Optional<Resolved> resolve(
            Optional<ItemProfile> weapon, Optional<EntityProfile> attacker, double attackDamageAttribute) {
        Optional<DiceExpression> weaponDice = weapon.flatMap(ItemProfile::damage);
        if (weaponDice.isPresent()) {
            DiceExpression dice = weaponDice.get();
            if (weapon.get().modifierFrom() == ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE) {
                dice = withBonus(dice, Derivation.itemDamageBonus(attackDamageAttribute, dice.averageValue()));
            }
            // A weapon's crit range beats the wielder's; a keen blade stays keen in anyone's hands.
            int critRange = weapon.get().critRange().orElse(entityCritRange(attacker));
            return Optional.of(new Resolved(dice, critRange));
        }
        return attacker.flatMap(EntityProfile::meleeDamage).map(dice -> new Resolved(dice, entityCritRange(attacker)));
    }

    /**
     * Dice for a projectile impact (M5). Precedence: the launcher's item profile (bow, crossbow,
     * trident, or the thrown item itself), then the attacker's entity profile ranged dice, then
     * empty — the caller applies the {@code fallbacks.unknown_weapon} rule. Ammo with its own
     * item profile ADDS its dice on top ({@code ammoDice}).
     *
     * <p>{@code modifier_from: attack_damage_attribute} uses {@code vanillaDamage} (the vanilla
     * projectile damage) as the reference stat: launchers have no attack-damage attribute, and
     * the vanilla amount already carries Power levels and draw strength.
     */
    public static Optional<Resolved> resolveRanged(
            Optional<ItemProfile> launcher,
            Optional<DiceExpression> ammoDice,
            Optional<EntityProfile> attacker,
            double vanillaDamage) {
        Optional<DiceExpression> launcherDice = launcher.flatMap(ItemProfile::damage);
        if (launcherDice.isPresent()) {
            DiceExpression dice = launcherDice.get();
            if (launcher.get().modifierFrom() == ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE) {
                dice = withBonus(dice, Derivation.itemDamageBonus(vanillaDamage, dice.averageValue()));
            }
            int critRange = launcher.get().critRange().orElse(entityCritRange(attacker));
            return Optional.of(new Resolved(withAmmo(dice, ammoDice), critRange));
        }
        return attacker.flatMap(EntityProfile::rangedDamage)
                .map(dice -> new Resolved(withAmmo(dice, ammoDice), entityCritRange(attacker)));
    }

    private static DiceExpression withAmmo(DiceExpression base, Optional<DiceExpression> ammo) {
        if (ammo.isEmpty()) {
            return base;
        }
        try {
            return base.plus(ammo.get());
        } catch (DiceParseException e) {
            return base; // combined term count over the engine limit — the ammo bonus is dropped
        }
    }

    /** Appends the flat attribute bonus, dropping it when the dice already sit at the term cap. */
    private static DiceExpression withBonus(DiceExpression dice, int bonus) {
        if (bonus <= 0) {
            return dice;
        }
        try {
            return dice.plus(BONUS_TERMS[bonus]);
        } catch (DiceParseException e) {
            return dice; // profile dice already at MAX_TERMS — same policy as withAmmo
        }
    }

    public static int entityCritRange(Optional<EntityProfile> profile) {
        return profile.map(EntityProfile::critRange).orElse(OptionalInt.empty()).orElse(20);
    }
}
