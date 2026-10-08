package studio.modroll.critfall.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Holds a rolled hit's feedback until the target's {@code LivingEntity.hurt} has fully resolved, so
 * the kill line reflects what actually happened. The roll runs before vanilla mitigation: Resistance,
 * absorption, a Totem of Undying, or another mod's damage handling can still turn a lethal roll into
 * a survived (or fully negated) hit. Each loader wraps {@code hurt} and calls {@link #enter} /
 * {@link #exit} around it; {@link DamageInterception} parks the feedback with {@link #defer}.
 *
 * <p>Frames are a per-thread stack, not one slot per entity, so a hurt nested inside another
 * (a listener damaging a second entity mid-hurt) flushes its own feedback at its own exit.
 */
public final class HurtFeedback {

    private HurtFeedback() {}

    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    /** What the hurt actually did to the target. */
    public record Result(boolean killed, boolean negated) {

        /** Used when no wrapped hurt is in flight: the result is unknown, so never claim a kill. */
        static final Result UNKNOWN = new Result(false, false);
    }

    private static final class Frame {
        final LivingEntity target;
        float health;
        float absorption;
        boolean deathProtectionUsed;
        Consumer<Result> pending;

        Frame(LivingEntity target) {
            this.target = target;
        }

        /** Records the pre-hit state; called before the damage lands. */
        void snapshot() {
            health = target.getHealth();
            absorption = target.getAbsorptionAmount();
        }

        /**
         * Killed: the target is dead after the hurt (a totem or a "survive at 1 HP" effect leaves it
         * alive). Negated: it lost neither health nor absorption — Resistance V, a shield, a cancel —
         * and no totem fired (a totem at 1 HP ends where it started, but the hit was lethal).
         */
        Result result() {
            boolean killed = target.isDeadOrDying();
            boolean negated = !killed
                    && !deathProtectionUsed
                    && target.getHealth() >= health
                    && target.getAbsorptionAmount() >= absorption;
            return new Result(killed, negated);
        }
    }

    /** Loader hook: {@code target}'s {@code hurt} is starting. */
    public static void enter(LivingEntity target) {
        FRAMES.get().push(new Frame(target));
    }

    /**
     * Loader hook: {@code target}'s {@code hurt} is over. Emits the parked feedback when the hurt
     * {@code completed}; drops it when the hurt threw. Pops any frame left above this one (only a
     * throw that skipped its exit can leave one).
     */
    public static void exit(LivingEntity target, boolean completed) {
        Deque<Frame> frames = FRAMES.get();
        while (!frames.isEmpty()) {
            Frame frame = frames.pop();
            if (frame.target == target) {
                if (completed && frame.pending != null) {
                    frame.pending.accept(frame.result());
                }
                return;
            }
        }
    }

    /**
     * Loader hook: a Totem of Undying (or anything vanilla's totem check accepts) just kept
     * {@code entity} alive. Marks every in-flight frame for it, so the hit never reads as negated.
     */
    public static void deathProtectionUsed(LivingEntity entity) {
        for (Frame frame : FRAMES.get()) {
            if (frame.target == entity) {
                frame.deathProtectionUsed = true;
            }
        }
    }

    /**
     * Parks {@code emit} until the in-flight hurt on {@code target} resolves. Called before the
     * damage lands, so the health snapshot is the pre-hit state. Outside a wrapped hurt (no loader
     * hook saw it start) it emits at once with {@link Result#UNKNOWN}.
     */
    static void defer(LivingEntity target, Consumer<Result> emit) {
        Frame top = FRAMES.get().peek();
        if (top == null || top.target != target || top.pending != null) {
            emit.accept(Result.UNKNOWN);
            return;
        }
        top.snapshot();
        top.pending = emit;
    }

    /** Runs {@code hurt}, which damages {@code target}, and reports what it did (the driven API path). */
    public static Result observe(LivingEntity target, Runnable hurt) {
        Frame frame = new Frame(target);
        frame.snapshot();
        Deque<Frame> frames = FRAMES.get();
        frames.push(frame);
        try {
            hurt.run();
        } finally {
            frames.removeFirstOccurrence(frame);
        }
        return frame.result();
    }
}
