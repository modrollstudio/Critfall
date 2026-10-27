package studio.modroll.critfall.neoforge;

import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import studio.modroll.critfall.combat.JumpAttacks;

/**
 * Adapts NeoForge's player-attack events to {@link JumpAttacks}. {@code CriticalHitEvent} carries
 * vanilla's own crit decision ({@link CriticalHitEvent#isVanillaCritical()}) and any crit another
 * mod made; it is read at the lowest priority so a mod that cancelled or added a crit, or changed
 * its multiplier, is respected and the replaced multiplier is the last word.
 */
public final class JumpAttackHandler {

    private JumpAttackHandler() {}

    /** {@code Player.attack} fires this as it starts. */
    public static void onAttackEntity(AttackEntityEvent event) {
        JumpAttacks.beginAttack();
    }

    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.isCriticalHit()) {
            event.setDamageMultiplier(JumpAttacks.onCrit(
                    event.getEntity(), event.getTarget(), event.getDamageMultiplier(), event.isVanillaCritical()));
        }
    }
}
