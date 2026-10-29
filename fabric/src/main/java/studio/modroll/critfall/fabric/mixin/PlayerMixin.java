package studio.modroll.critfall.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.modroll.critfall.combat.JumpAttacks;
import studio.modroll.critfall.fabric.CritfallHurtState;

/**
 * Fabric side of {@link JumpAttacks}; Fabric has no equivalent of NeoForge's
 * {@code CriticalHitEvent}. Vanilla's {@code Player.attack} loads its {@code 1.5F} crit multiplier
 * only inside the branch its own crit check guards, so that constant marks a jump attack without
 * re-implementing the check. A mod that changes that same constant ahead of us reaches us chained
 * in; one that scales crit damage some other way is not seen, so a hit Critfall rolls with profile
 * dice drops that extra damage.
 *
 * <p>It also lands a rolled hit on a player: {@code Player} overrides {@code actuallyHurt} without calling
 * the {@code LivingEntity} one, so {@link LivingEntityMixin}'s substitution of the rolled amount never
 * reaches a player.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void critfall$beginAttack(Entity target, CallbackInfo ci) {
        JumpAttacks.beginAttack();
    }

    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
    private float critfall$jumpAttack(float multiplier, Entity target) {
        return JumpAttacks.onCrit((Player) (Object) this, target, multiplier, true);
    }

    /** {@link LivingEntityMixin}'s rolled-amount substitution, for {@code Player}'s own {@code actuallyHurt}. */
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
    private float critfall$replaceAmount(float amount) {
        CritfallHurtState state = (CritfallHurtState) this;
        return state.critfall$active() ? state.critfall$amount() : amount;
    }
}
