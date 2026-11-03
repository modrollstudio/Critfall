package studio.modroll.critfall.fabric.mixin;

import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.modroll.critfall.combat.HurtFeedback;

/**
 * Tells {@link HurtFeedback} the health damage a hurt applied, for the readout's damage taken. Vanilla
 * records it here from both {@code LivingEntity.actuallyHurt} and {@code Player.actuallyHurt}, right before
 * {@code setHealth}, once armor, Resistance, Protection and absorption have had their say, and not capped
 * at the health left.
 */
@Mixin(CombatTracker.class)
public abstract class CombatTrackerMixin {

    @Shadow
    @Final
    private LivingEntity mob;

    @Inject(method = "recordDamage", at = @At("HEAD"))
    private void critfall$recordTaken(DamageSource source, float damage, CallbackInfo ci) {
        HurtFeedback.damageTaken(mob, damage);
    }
}
