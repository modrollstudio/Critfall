package studio.modroll.critfall.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.RollMode;

/**
 * Everything a consumer supplies to drive an attack through {@link RollService}. {@code delivery}
 * selects melee vs. ranged resolution and flavor matching (issue #9); {@code source} carries the
 * damage type (for resist/immune and the {@code hurt} call); {@code weapon} is the attacking stack.
 * The overrides let advanced callers force advantage/disadvantage, a fixed attack bonus, or explicit
 * damage dice instead of the resolved profile values.
 */
public record AttackContext(
        AttackDelivery delivery,
        DamageSource source,
        ItemStack weapon,
        RollMode mode,
        OptionalInt attackBonusOverride,
        Optional<DiceExpression> damageDiceOverride) {

    public AttackContext {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(attackBonusOverride, "attackBonusOverride");
        Objects.requireNonNull(damageDiceOverride, "damageDiceOverride");
    }

    private static AttackContext of(AttackDelivery delivery, DamageSource source, ItemStack weapon) {
        return new AttackContext(delivery, source, weapon, RollMode.NORMAL, OptionalInt.empty(), Optional.empty());
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
        return new AttackContext(delivery, source, weapon, newMode, attackBonusOverride, damageDiceOverride);
    }

    public AttackContext withAttackBonus(int bonus) {
        return new AttackContext(delivery, source, weapon, mode, OptionalInt.of(bonus), damageDiceOverride);
    }

    public AttackContext withDamageDice(DiceExpression dice) {
        return new AttackContext(delivery, source, weapon, mode, attackBonusOverride, Optional.of(dice));
    }

    /** A ranged delivery (projectile or thrown) uses the entity profile's ranged dice, not melee. */
    public boolean isRanged() {
        return delivery == AttackDelivery.PROJECTILE || delivery == AttackDelivery.THROWN;
    }
}
