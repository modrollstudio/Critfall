package studio.modroll.critfall.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.modroll.critfall.combat.HurtFeedback;
import studio.modroll.critfall.data.EntityProfileHolder;
import studio.modroll.critfall.data.ProfileLookup;

/**
 * Wraps {@code LivingEntity.hurt} for {@link HurtFeedback}: the roll runs in
 * {@code LivingIncomingDamageEvent}, before mitigation, but its feedback is sent only once the hurt
 * has fully resolved, so a kill line means the target actually died. No NeoForge event fires after
 * the totem check, hence the mixin; a wrap rather than a RETURN inject so it also runs when the hurt
 * is cancelled early. It also tells {@link HurtFeedback} when a totem fired, which health alone
 * cannot show for a target already at 1 HP. It also holds and saves the entity profile assigned to
 * the entity ({@link EntityProfileHolder}).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements EntityProfileHolder {

    @Unique
    private ResourceLocation critfall$profileId;

    @Override
    public ResourceLocation critfall$profileId() {
        return critfall$profileId;
    }

    @Override
    public void critfall$setProfileId(ResourceLocation id) {
        this.critfall$profileId = id;
    }

    /** Saves an assigned entity profile with the entity. */
    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void critfall$saveProfile(CompoundTag tag, CallbackInfo ci) {
        ProfileLookup.saveAssignedProfile(this, tag);
    }

    /** Loads it back, also from {@code /summon} and {@code /data merge}. */
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void critfall$loadProfile(CompoundTag tag, CallbackInfo ci) {
        ProfileLookup.loadAssignedProfile(this, tag);
    }

    @WrapMethod(method = "hurt")
    private boolean critfall$resolveFeedback(DamageSource source, float amount, Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        HurtFeedback.enter(self);
        boolean completed = false;
        try {
            boolean hurt = original.call(source, amount);
            completed = true;
            return hurt;
        } finally {
            HurtFeedback.exit(self, completed);
        }
    }

    /** A totem kept this entity alive: the hurt was lethal, so it must never read as negated. */
    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void critfall$noteDeathProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            HurtFeedback.deathProtectionUsed((LivingEntity) (Object) this);
        }
    }
}
