package studio.modroll.critfall.neoforge.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.modroll.critfall.combat.MissedShotHolder;

/**
 * Lets an arrow that a rolled attack missed fly on past its target ({@link MissedShotHolder}). Vanilla
 * {@code onHitEntity} answers a failed hurt by bouncing the arrow back the way it came, which sent every
 * missed shot back into the shooter; this skips that bounce for a recorded miss and keeps the arrow from
 * striking that target again.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin implements MissedShotHolder {

    @Unique
    private IntSet critfall$missed;

    @Override
    public void critfall$addMissed(Entity target) {
        if (critfall$missed == null) {
            critfall$missed = new IntOpenHashSet(2);
        }
        critfall$missed.add(target.getId());
    }

    @Inject(
            method = "onHitEntity",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/projectile/AbstractArrow;deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)Z"),
            cancellable = true)
    private void critfall$flyPastMiss(EntityHitResult hit, CallbackInfo ci) {
        if (critfall$missed != null && critfall$missed.contains(hit.getEntity().getId())) {
            AbstractArrow self = (AbstractArrow) (Object) this;
            // Clients ran the same hit and slowed their copy for the bounce: resend the real motion.
            if (self.level() instanceof ServerLevel level) {
                level.getChunkSource().broadcast(self, new ClientboundSetEntityMotionPacket(self));
            }
            ci.cancel();
        }
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void critfall$skipMissed(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (critfall$missed != null && critfall$missed.contains(entity.getId())) {
            cir.setReturnValue(false);
        }
    }
}
