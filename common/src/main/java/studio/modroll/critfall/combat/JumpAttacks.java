package studio.modroll.critfall.combat;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * The jump attack advantage source ({@code advantage_sources.jump_attack}): a player melee swing
 * vanilla treats as a critical hit rolls its attack with advantage instead of taking vanilla's
 * crit damage multiplier. Vanilla decides the crit (each loader hooks the point in
 * {@code Player.attack} where it applies the multiplier), so its conditions are never
 * re-implemented here.
 *
 * <p>The swing is recorded on the server thread between that point and the {@code hurt} it leads
 * to, where {@link DamageInterception} consumes it. Each loader calls {@link #beginAttack} as
 * {@code Player.attack} starts, so a swing whose {@code hurt} never reached the roll (an
 * invulnerable target) cannot leak into the next one.
 */
public final class JumpAttacks {

    private record Pending(UUID attacker, UUID target, long gameTime) {}

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private JumpAttacks() {}

    /** Forgets any jump attack recorded on this thread. Loaders call it as each player attack starts. */
    public static void beginAttack() {
        PENDING.remove();
    }

    /**
     * Called where vanilla has decided a player's melee swing on {@code target} is a critical hit,
     * just before it scales the damage by {@code multiplier}.
     *
     * @return the multiplier to apply instead: 1 when the automatic pipeline will roll this hit (the
     *     advantage replaces the extra damage), else {@code multiplier} unchanged
     */
    public static float onVanillaCrit(Player attacker, Entity target, float multiplier) {
        if (attacker.level().isClientSide() || !(target instanceof LivingEntity living)) {
            return multiplier;
        }
        Rules rules = RollRuntime.rules();
        if (!rules.advantageSources().jumpAttack()) {
            return multiplier;
        }
        PENDING.set(
                new Pending(attacker.getUUID(), living.getUUID(), living.level().getGameTime()));
        // Dry-run shows the advantage roll but leaves vanilla damage, crit multiplier included.
        boolean replaced = !rules.dryRun().enabled()
                && DamageInterception.rollsMelee(
                        rules, attacker, living, attacker.damageSources().playerAttack(attacker));
        return replaced ? 1.0F : multiplier;
    }

    /** True, once, when this melee hurt is the swing a jump attack was recorded for. */
    static boolean consume(LivingEntity attacker, LivingEntity target) {
        Pending pending = PENDING.get();
        if (pending == null
                || !pending.attacker().equals(attacker.getUUID())
                || !pending.target().equals(target.getUUID())
                || pending.gameTime() != target.level().getGameTime()) {
            return false;
        }
        PENDING.remove();
        return true;
    }

    /**
     * The roll mode after {@code PreAttackRollEvent} listeners saw a jump attack's advantage and left
     * {@code listenerMode}. Advantage and disadvantage cancel the 5e way: a listener's disadvantage
     * makes the roll normal rather than replacing the advantage. A listener that sets normal
     * explicitly clears the advantage.
     */
    static RollMode withJumpAdvantage(RollMode listenerMode) {
        return listenerMode == RollMode.DISADVANTAGE ? RollMode.NORMAL : listenerMode;
    }
}
