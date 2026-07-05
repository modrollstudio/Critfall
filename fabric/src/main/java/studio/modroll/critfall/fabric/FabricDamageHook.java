package studio.modroll.critfall.fabric;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.combat.DamageInterception;
import studio.modroll.critfall.combat.IncomingDamage;

/**
 * Drives {@link DamageInterception} from {@code ServerLivingEntityEvents.ALLOW_DAMAGE}, which fires
 * after the invulnerability/i-frame checks and before mitigation — the same point as NeoForge's
 * {@code LivingIncomingDamageEvent}. The event handles MISS/FUMBLE cancellation directly (returning
 * {@code false} denies the damage). A HIT/CRIT caches its rolled amount + armor-bypass flag on the
 * entity via {@link CritfallHurtState}, where {@code LivingEntityMixin} reads it inside
 * {@code actuallyHurt}. Registered in {@link CritfallFabric}.
 */
public final class FabricDamageHook {

    private FabricDamageHook() {}

    /** @return {@code false} to cancel the damage (MISS/FUMBLE/veto), {@code true} to let it through. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        CritfallHurtState state = (CritfallHurtState) entity;
        state.critfall$clear();
        Decision decision = new Decision(amount);
        DamageInterception.handle(decision, entity, source);
        if (decision.cancelled) {
            return false;
        }
        if (decision.amountSet) {
            state.critfall$store(decision.amount, decision.bypassArmor);
        }
        return true;
    }

    /** Mutable {@link IncomingDamage} the pipeline writes its decision into for one ALLOW_DAMAGE call. */
    private static final class Decision implements IncomingDamage {

        private float amount;
        private boolean amountSet;
        private boolean cancelled;
        private boolean bypassArmor;

        Decision(float amount) {
            this.amount = amount;
        }

        @Override
        public float amount() {
            return amount;
        }

        @Override
        public void setAmount(float amount) {
            this.amount = amount;
            this.amountSet = true;
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public void bypassArmor() {
            this.bypassArmor = true;
        }
    }
}
