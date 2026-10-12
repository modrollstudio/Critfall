package studio.modroll.critfall.api;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
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
 * @param damage rolled on a hit; a crit applies the server's crit rule to it
 * @param mode advantage or disadvantage on the attack roll
 */
public record HazardAttack(
        String label, ResourceKey<DamageType> damageType, int attackBonus, DiceExpression damage, RollMode mode) {

    public HazardAttack {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(damageType, "damageType");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(mode, "mode");
    }

    /** Rolled normally. */
    public static HazardAttack of(
            String label, ResourceKey<DamageType> damageType, int attackBonus, DiceExpression damage) {
        return new HazardAttack(label, damageType, attackBonus, damage, RollMode.NORMAL);
    }

    public HazardAttack withMode(RollMode newMode) {
        return new HazardAttack(label, damageType, attackBonus, damage, newMode);
    }
}
