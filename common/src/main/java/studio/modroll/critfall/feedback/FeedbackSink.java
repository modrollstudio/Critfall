package studio.modroll.critfall.feedback;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;

/**
 * The loader-agnostic seam for sending feedback (PLAN §4.5/§12.4). The internal pipeline and API
 * consumers both emit through {@link #get()}; on NeoForge the real sink forwards to the packet
 * dispatcher, while headless/common contexts keep the {@link #NOOP} default. Set once at mod init.
 */
public interface FeedbackSink {

    void roll(
            LivingEntity attacker,
            LivingEntity target,
            RollFeedbackPayload payload,
            Rules.FeedbackVisibility visibility);

    void save(
            LivingEntity attacker,
            LivingEntity target,
            SaveFeedbackPayload payload,
            Rules.FeedbackVisibility visibility);

    FeedbackSink NOOP = new FeedbackSink() {
        @Override
        public void roll(LivingEntity a, LivingEntity t, RollFeedbackPayload p, Rules.FeedbackVisibility v) {}

        @Override
        public void save(LivingEntity a, LivingEntity t, SaveFeedbackPayload p, Rules.FeedbackVisibility v) {}
    };

    /** Holder — a nested class so the interface's static field stays a constant. */
    final class Holder {
        private static volatile FeedbackSink current = NOOP;

        private Holder() {}
    }

    static void set(FeedbackSink sink) {
        Holder.current = sink == null ? NOOP : sink;
    }

    static FeedbackSink get() {
        return Holder.current;
    }
}
