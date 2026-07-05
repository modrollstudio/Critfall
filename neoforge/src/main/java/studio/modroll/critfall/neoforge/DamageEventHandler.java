package studio.modroll.critfall.neoforge;

import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import studio.modroll.critfall.combat.DamageInterception;
import studio.modroll.critfall.combat.IncomingDamage;

/**
 * Adapts NeoForge's {@code LivingIncomingDamageEvent} — the earliest point in NeoForge's damage
 * sequence (after invulnerability checks, before any mitigation) — to the loader-agnostic
 * {@link DamageInterception}. All combat logic lives in common; this class only maps the four
 * mutation points onto the event.
 */
public final class DamageEventHandler {

    private DamageEventHandler() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageInterception.handle(new EventDamage(event), event.getEntity(), event.getSource());
    }

    private record EventDamage(LivingIncomingDamageEvent event) implements IncomingDamage {
        @Override
        public float amount() {
            return event.getAmount();
        }

        @Override
        public void setAmount(float amount) {
            event.setAmount(amount);
        }

        @Override
        public void cancel() {
            event.setCanceled(true);
        }

        @Override
        public void bypassArmor() {
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0f);
        }
    }
}
