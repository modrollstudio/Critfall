package studio.modroll.critfall.api.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.dice.RollMode;

/**
 * The public, loader-agnostic event bus for Critfall (PLAN §4.4). Mods and KubeJS scripts register
 * listeners here; both the automatic damage pipeline and {@link studio.modroll.critfall.api.RollService}
 * fire through it, so a listener sees every attack from either path. A listener that throws is caught
 * and logged — one bad script must not break combat for the server.
 */
public final class CritfallEvents {

    private static final List<Consumer<PreAttackRollEvent>> preAttack = new CopyOnWriteArrayList<>();
    private static final List<Consumer<PostAttackRollEvent>> postAttack = new CopyOnWriteArrayList<>();
    private static final List<Consumer<FumbleEvent>> fumble = new CopyOnWriteArrayList<>();
    private static final List<Consumer<CritEvent>> crit = new CopyOnWriteArrayList<>();

    private CritfallEvents() {}

    public static void onPreAttackRoll(Consumer<PreAttackRollEvent> listener) {
        preAttack.add(listener);
    }

    public static void onPostAttackRoll(Consumer<PostAttackRollEvent> listener) {
        postAttack.add(listener);
    }

    public static void onFumble(Consumer<FumbleEvent> listener) {
        fumble.add(listener);
    }

    public static void onCrit(Consumer<CritEvent> listener) {
        crit.add(listener);
    }

    public static PreAttackRollEvent firePreAttackRoll(
            LivingEntity attacker, LivingEntity target, AttackContext context, int attackBonus, RollMode mode) {
        PreAttackRollEvent event = new PreAttackRollEvent(attacker, target, context, attackBonus, mode);
        dispatch(preAttack, event);
        return event;
    }

    public static PostAttackRollEvent firePostAttackRoll(
            LivingEntity attacker, LivingEntity target, AttackContext context, AttackResult result) {
        PostAttackRollEvent event = new PostAttackRollEvent(attacker, target, context, result);
        dispatch(postAttack, event);
        return event;
    }

    public static void fireFumble(
            LivingEntity attacker, LivingEntity target, AttackContext context, AttackResult result) {
        dispatch(fumble, new FumbleEvent(attacker, target, context, result));
    }

    public static void fireCrit(
            LivingEntity attacker, LivingEntity target, AttackContext context, AttackResult result) {
        dispatch(crit, new CritEvent(attacker, target, context, result));
    }

    private static <T> void dispatch(List<Consumer<T>> listeners, T event) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                Critfall.LOG.error("A Critfall event listener threw — ignoring it for this attack", t);
            }
        }
    }

    /** For tests. */
    public static void clearListeners() {
        preAttack.clear();
        postAttack.clear();
        fumble.clear();
        crit.clear();
    }
}
