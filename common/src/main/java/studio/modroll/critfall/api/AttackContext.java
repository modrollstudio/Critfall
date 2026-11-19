package studio.modroll.critfall.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * Everything a consumer supplies to drive an attack through {@link RollService}. {@code delivery}
 * selects melee vs. ranged resolution and flavor matching (issue #9); {@code source} carries the
 * damage type (for resist/immune and the {@code hurt} call); {@code weapon} is the attacking stack.
 * The overrides let advanced callers force advantage/disadvantage, a fixed attack bonus, explicit
 * damage dice instead of the resolved profile values, or a per-attack modifier to the defender's AC.
 * {@code label} names the attack in the readout (since 0.2.20): a translation key, or plain text; empty for none.
 */
public record AttackContext(
        AttackDelivery delivery,
        DamageSource source,
        ItemStack weapon,
        RollMode mode,
        OptionalInt attackBonusOverride,
        Optional<DiceExpression> damageDiceOverride,
        int defenderAcBonus,
        String label) {

    public AttackContext {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(attackBonusOverride, "attackBonusOverride");
        Objects.requireNonNull(damageDiceOverride, "damageDiceOverride");
        Objects.requireNonNull(label, "label");
    }

    /** A context with no label, as before 0.2.20. */
    public AttackContext(
            AttackDelivery delivery,
            DamageSource source,
            ItemStack weapon,
            RollMode mode,
            OptionalInt attackBonusOverride,
            Optional<DiceExpression> damageDiceOverride,
            int defenderAcBonus) {
        this(delivery, source, weapon, mode, attackBonusOverride, damageDiceOverride, defenderAcBonus, "");
    }

    private static AttackContext of(AttackDelivery delivery, DamageSource source, ItemStack weapon) {
        return new AttackContext(
                delivery, source, weapon, RollMode.NORMAL, OptionalInt.empty(), Optional.empty(), 0, "");
    }

    public static AttackContext melee(DamageSource source, ItemStack weapon) {
        return of(AttackDelivery.MELEE, source, weapon);
    }

    public static AttackContext projectile(DamageSource source, ItemStack weapon) {
        return of(AttackDelivery.PROJECTILE, source, weapon);
    }

    public static AttackContext thrown(DamageSource source, ItemStack weapon) {
        return of(AttackDelivery.THROWN, source, weapon);
    }

    public static AttackContext spell(DamageSource source, ItemStack weapon) {
        return of(AttackDelivery.SPELL, source, weapon);
    }

    public AttackContext withMode(RollMode newMode) {
        return new AttackContext(
                delivery, source, weapon, newMode, attackBonusOverride, damageDiceOverride, defenderAcBonus, label);
    }

    public AttackContext withAttackBonus(int bonus) {
        return new AttackContext(
                delivery, source, weapon, mode, OptionalInt.of(bonus), damageDiceOverride, defenderAcBonus, label);
    }

    public AttackContext withDamageDice(DiceExpression dice) {
        return new AttackContext(
                delivery, source, weapon, mode, attackBonusOverride, Optional.of(dice), defenderAcBonus, label);
    }

    /** Negatives are penalties (flanked, restrained) — the value is not clamped. */
    public AttackContext withDefenderAcBonus(int bonus) {
        return new AttackContext(delivery, source, weapon, mode, attackBonusOverride, damageDiceOverride, bonus, label);
    }

    /**
     * Names the attack in its readout, such as a spell cast through a held focus: {@code Fire Bolt: d20 13+5=18 vs
     * AC 12 — HIT 1d10 = 6}. A translation key, or plain text; empty for none. Since 0.2.20.
     */
    public AttackContext withLabel(String newLabel) {
        return new AttackContext(
                delivery,
                source,
                weapon,
                mode,
                attackBonusOverride,
                damageDiceOverride,
                defenderAcBonus,
                Objects.requireNonNull(newLabel, "label"));
    }

    /** A ranged delivery (projectile or thrown) uses the entity profile's ranged dice, not melee. */
    public boolean isRanged() {
        return delivery.isRanged();
    }
}
