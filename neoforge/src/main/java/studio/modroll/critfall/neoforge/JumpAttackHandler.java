package studio.modroll.critfall.neoforge;

import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import studio.modroll.critfall.combat.JumpAttacks;

/**
 * Adapts NeoForge's player-attack events to {@link JumpAttacks}. {@code CriticalHitEvent} carries
 * vanilla's own crit decision ({@link CriticalHitEvent#isVanillaCritical()}); it is read at the
 * lowest priority so a mod that cancelled the crit is respected and the replaced multiplier is the
 * last word.
 */
public final class JumpAttackHandler {

    private JumpAttackHandler() {}

    /** {@code Player.attack} fires this as it starts. */
    public static void onAttackEntity(AttackEntityEvent event) {
        JumpAttacks.beginAttack();
    }

    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.isVanillaCritical() && event.isCriticalHit()) {
            event.setDamageMultiplier(
                    JumpAttacks.onVanillaCrit(event.getEntity(), event.getTarget(), event.getDamageMultiplier()));
        }
    }
}
