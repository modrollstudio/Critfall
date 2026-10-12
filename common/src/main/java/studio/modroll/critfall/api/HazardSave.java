package studio.modroll.critfall.api;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * Damage the target saves against, with no living attacker: a trap, a gas cloud, a falling block. Roll it
 * with {@link RollService#performHazardSave}. Since 0.2.13.
 *
 * @param label names the hazard in the readout: a translation key, or plain text
 * @param damageType the damage type of the hurt, which the target's resistances and death message use
 * @param saveKey the save the {@link ModifierProvider} is asked for, such as {@link
 *     ModifierProvider#DEXTERITY_SAVE}
 * @param dc the save's DC
 * @param damage rolled on a failed save, and on a success that takes {@link SuccessDamage#HALF}
 * @param onSuccess what a successful save takes
 * @param mode advantage or disadvantage on the save
 */
public record HazardSave(
        String label,
        ResourceKey<DamageType> damageType,
        String saveKey,
        int dc,
        DiceExpression damage,
        SuccessDamage onSuccess,
        RollMode mode) {

    public HazardSave {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(damageType, "damageType");
        Objects.requireNonNull(saveKey, "saveKey");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(mode, "mode");
    }

    /** Half damage on a success, rolled normally. */
    public static HazardSave of(
            String label, ResourceKey<DamageType> damageType, String saveKey, int dc, DiceExpression damage) {
        return new HazardSave(label, damageType, saveKey, dc, damage, SuccessDamage.HALF, RollMode.NORMAL);
    }

    public HazardSave withOnSuccess(SuccessDamage newOnSuccess) {
        return new HazardSave(label, damageType, saveKey, dc, damage, newOnSuccess, mode);
    }

    public HazardSave withMode(RollMode newMode) {
        return new HazardSave(label, damageType, saveKey, dc, damage, onSuccess, newMode);
    }
}
