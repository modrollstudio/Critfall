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
 * <p>With {@code crits.enabled} off, Critfall stays out of crits altogether: the advantage source
 * stands down, the swing keeps its crit multiplier (vanilla's, or the one another mod set), and a
 * hit Critfall rolls carries that multiplier onto its dice damage. Critfall's crits are then simply
 * not there, and vanilla's and other mods' work as before.
 *
 * <p>The swing is recorded on the server thread between that point and the {@code hurt} it leads
 * to, where {@link DamageInterception} consumes it. Each loader calls {@link #beginAttack} as
 * {@code Player.attack} starts, so a swing whose {@code hurt} never reached the roll (an
 * invulnerable target) cannot leak into the next one.
 */
public final class JumpAttacks {

    /**
     * What a player's swing brings to its attack roll.
     *
     * @param advantage the swing is a jump attack: it rolls with advantage
     * @param critMultiplier the crit multiplier a rolled hit's dice damage carries; 1 unless crits
     *     are off and the swing was a crit
     */
    record Swing(boolean advantage, float critMultiplier) {
        static final Swing PLAIN = new Swing(false, 1.0F);
    }

    private record Pending(UUID attacker, UUID target, long gameTime, Swing swing) {}

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private JumpAttacks() {}

    /** Forgets any jump attack recorded on this thread. Loaders call it as each player attack starts. */
    public static void beginAttack() {
        PENDING.remove();
    }

    /**
     * Called where a player's melee swing on {@code target} is a critical hit, just before
     * {@code Player.attack} scales the damage by {@code multiplier}.
     *
     * @param vanilla vanilla's own crit check made it a crit, rather than another mod
     * @return the multiplier to apply instead: 1 when the automatic pipeline will roll this vanilla
     *     crit with advantage (the advantage replaces the extra damage), else {@code multiplier}
     *     unchanged
     */
    public static float onCrit(Player attacker, Entity target, float multiplier, boolean vanilla) {
        if (attacker.level().isClientSide() || !(target instanceof LivingEntity living)) {
            return multiplier;
        }
        Rules rules = RollRuntime.rules();
        if (!rules.crits().enabled()) {
            record(attacker, living, new Swing(false, multiplier));
            return multiplier;
        }
        if (!vanilla || !rules.advantageSources().jumpAttack()) {
            return multiplier;
        }
        record(attacker, living, new Swing(true, 1.0F));
        // Dry-run shows the advantage roll but leaves vanilla damage, crit multiplier included.
        boolean replaced = !rules.dryRun().enabled()
                && DamageInterception.rollsMelee(
                        rules, attacker, living, attacker.damageSources().playerAttack(attacker));
        return replaced ? 1.0F : multiplier;
    }

    private static void record(Player attacker, LivingEntity target, Swing swing) {
        PENDING.set(
                new Pending(attacker.getUUID(), target.getUUID(), target.level().getGameTime(), swing));
    }

    /** The recorded swing, once, when this melee hurt is the one it was recorded for; else plain. */
    static Swing consume(LivingEntity attacker, LivingEntity target) {
        Pending pending = PENDING.get();
        if (pending == null
                || !pending.attacker().equals(attacker.getUUID())
                || !pending.target().equals(target.getUUID())
                || pending.gameTime() != target.level().getGameTime()) {
            return Swing.PLAIN;
        }
        PENDING.remove();
        return pending.swing();
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
