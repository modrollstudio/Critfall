package studio.modroll.critfall.fabric.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.modroll.critfall.fabric.CritfallHurtState;

/**
 * Fabric damage interception. The roll itself runs in {@code ServerLivingEntityEvents.ALLOW_DAMAGE}
 * ({@link studio.modroll.critfall.fabric.FabricDamageHook}) — the one point that matches NeoForge's
 * {@code LivingIncomingDamageEvent}: after the invulnerability/i-frame checks, before mitigation, and
 * before {@code actuallyHurt}. That event owns MISS/FUMBLE cancellation (no mixin needed for it).
 *
 * <p>This mixin only does what no Fabric event can: replace the outgoing amount with the rolled
 * damage and suppress base-armor reduction. It reads the decision the hook cached on this entity via
 * {@link CritfallHurtState}. Because the hook runs strictly before {@code actuallyHurt}, the fields
 * are always set before these injectors read them — no injector-ordering ambiguity.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements CritfallHurtState {

    @Unique
    private boolean critfall$active;

    @Unique
    private float critfall$amount;

    @Unique
    private boolean critfall$bypassArmor;

    @Override
    public void critfall$store(float amount, boolean bypassArmor) {
        this.critfall$active = true;
        this.critfall$amount = amount;
        this.critfall$bypassArmor = bypassArmor;
    }

    @Override
    public void critfall$clear() {
        this.critfall$active = false;
        this.critfall$amount = 0.0f;
        this.critfall$bypassArmor = false;
    }

    @Override
    public boolean critfall$active() {
        return critfall$active;
    }

    @Override
    public float critfall$amount() {
        return critfall$amount;
    }

    @Override
    public boolean critfall$bypassArmor() {
        return critfall$bypassArmor;
    }

    /** Substitute the rolled damage for the vanilla amount on a HIT/CRIT (runs after the hook). */
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
    private float critfall$replaceAmount(float amount) {
        return critfall$active ? critfall$amount : amount;
    }

    /** Fabric equivalent of NeoForge addReductionModifier(ARMOR, ->0): skip base armor when asked. */
    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void critfall$bypassArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (critfall$active && critfall$bypassArmor) {
            cir.setReturnValue(amount);
        }
    }
}
