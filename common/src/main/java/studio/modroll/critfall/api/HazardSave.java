package studio.modroll.critfall.api;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
 * @param damage rolled on a failed save, and on a success that takes {@link SuccessDamage#HALF}; {@link
 *     #NO_DAMAGE} for a save that only decides an effect
 * @param onSuccess what a successful save takes
 * @param mode advantage or disadvantage on the save
 * @param id identifies the hazard to {@link studio.modroll.critfall.api.event.HazardRollEvent} listeners;
 *     empty for none. Since 0.2.14
 */
public record HazardSave(
        String label,
        ResourceKey<DamageType> damageType,
        String saveKey,
        int dc,
        DiceExpression damage,
        SuccessDamage onSuccess,
        RollMode mode,
        Optional<ResourceLocation> id) {

    /** The damage of a hazard that deals none: a failed save takes 0, not the usual minimum of 1. Since 0.2.14. */
    public static final DiceExpression NO_DAMAGE = DiceExpression.parse("0");

    public HazardSave {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(damageType, "damageType");
        Objects.requireNonNull(saveKey, "saveKey");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(id, "id");
    }

    /** A hazard with no id, as in 0.2.13. */
    public HazardSave(
            String label,
            ResourceKey<DamageType> damageType,
            String saveKey,
            int dc,
            DiceExpression damage,
            SuccessDamage onSuccess,
            RollMode mode) {
        this(label, damageType, saveKey, dc, damage, onSuccess, mode, Optional.empty());
    }

    /** Half damage on a success, rolled normally. */
    public static HazardSave of(
            String label, ResourceKey<DamageType> damageType, String saveKey, int dc, DiceExpression damage) {
        return new HazardSave(label, damageType, saveKey, dc, damage, SuccessDamage.HALF, RollMode.NORMAL);
    }

    public HazardSave withOnSuccess(SuccessDamage newOnSuccess) {
        return new HazardSave(label, damageType, saveKey, dc, damage, newOnSuccess, mode, id);
    }

    public HazardSave withMode(RollMode newMode) {
        return new HazardSave(label, damageType, saveKey, dc, damage, onSuccess, newMode, id);
    }

    /** Since 0.2.14. */
    public HazardSave withId(ResourceLocation newId) {
        return new HazardSave(
                label, damageType, saveKey, dc, damage, onSuccess, mode, Optional.of(Objects.requireNonNull(newId)));
    }

    /**
     * False when {@code damage} is a flat {@code 0}, such as {@link #NO_DAMAGE}: the save deals nothing either
     * way. Since 0.2.14.
     */
    public boolean dealsDamage() {
        return damage.hasDice() || damage.modifier() != 0;
    }
}
