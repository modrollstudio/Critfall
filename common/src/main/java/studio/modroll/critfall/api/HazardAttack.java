package studio.modroll.critfall.api;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * An attack roll with no living attacker: a dart trap, a swinging blade. Roll it with {@link
 * RollService#performHazardAttack}. Since 0.2.13.
 *
 * @param label names the hazard in the readout: a translation key, or plain text
 * @param damageType the damage type of the hurt, which the target's resistances and death message use
 * @param attackBonus added to the d20
 * @param damage rolled on a hit; a crit applies the server's crit rule to it. {@link HazardSave#NO_DAMAGE}
 *     for a hit that only decides an effect
 * @param mode advantage or disadvantage on the attack roll
 * @param id identifies the hazard to {@link studio.modroll.critfall.api.event.HazardRollEvent} listeners;
 *     empty for none. Since 0.2.14
 */
public record HazardAttack(
        String label,
        ResourceKey<DamageType> damageType,
        int attackBonus,
        DiceExpression damage,
        RollMode mode,
        Optional<ResourceLocation> id) {

    public HazardAttack {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(damageType, "damageType");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(id, "id");
    }

    /** A hazard with no id, as in 0.2.13. */
    public HazardAttack(
            String label, ResourceKey<DamageType> damageType, int attackBonus, DiceExpression damage, RollMode mode) {
        this(label, damageType, attackBonus, damage, mode, Optional.empty());
    }

    /** Rolled normally. */
    public static HazardAttack of(
            String label, ResourceKey<DamageType> damageType, int attackBonus, DiceExpression damage) {
        return new HazardAttack(label, damageType, attackBonus, damage, RollMode.NORMAL);
    }

    public HazardAttack withMode(RollMode newMode) {
        return new HazardAttack(label, damageType, attackBonus, damage, newMode, id);
    }

    /** Since 0.2.14. */
    public HazardAttack withId(ResourceLocation newId) {
        return new HazardAttack(
                label, damageType, attackBonus, damage, mode, Optional.of(Objects.requireNonNull(newId)));
    }

    /** False when {@code damage} is a flat {@code 0}: a hit deals nothing. Since 0.2.14. */
    public boolean dealsDamage() {
        return damage.hasDice() || damage.modifier() != 0;
    }
}
