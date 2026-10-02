package studio.modroll.critfall.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.modroll.critfall.combat.JumpAttacks;

/**
 * Fabric side of {@link JumpAttacks}; Fabric has no equivalent of NeoForge's
 * {@code CriticalHitEvent}. Vanilla's {@code Player.attack} loads its {@code 1.5F} crit multiplier
 * only inside the branch its own crit check guards, so that constant marks a jump attack without
 * re-implementing the check.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void critfall$beginAttack(Entity target, CallbackInfo ci) {
        JumpAttacks.beginAttack();
    }

    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 1.5F))
    private float critfall$jumpAttack(float multiplier, Entity target) {
        return JumpAttacks.onVanillaCrit((Player) (Object) this, target, multiplier);
    }
}
