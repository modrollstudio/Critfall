package studio.modroll.critfall.feedback;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;

/**
 * A {@link FeedbackSink} that records the most recent dispatched payloads for test assertions. Shared
 * GameTests install this via {@link FeedbackSink#set} to inspect what the pipeline emitted, on either
 * loader, without depending on a loader-specific dispatcher.
 */
public final class CapturingFeedbackSink implements FeedbackSink {

    private volatile RollFeedbackPayload lastRoll;
    private volatile SaveFeedbackPayload lastSave;
    private volatile CheckFeedbackPayload lastCheck;
    private volatile LivingEntity lastCheckRoller;

    @Override
    public void roll(
            LivingEntity attacker, LivingEntity target, RollFeedbackPayload payload, Rules.FeedbackVisibility v) {
        this.lastRoll = payload;
    }

    @Override
    public void save(
            LivingEntity attacker, LivingEntity target, SaveFeedbackPayload payload, Rules.FeedbackVisibility v) {
        this.lastSave = payload;
    }

    @Override
    public void check(LivingEntity roller, CheckFeedbackPayload payload, Rules.FeedbackVisibility v) {
        this.lastCheck = payload;
        this.lastCheckRoller = roller;
    }

    public RollFeedbackPayload lastRoll() {
        return lastRoll;
    }

    public SaveFeedbackPayload lastSave() {
        return lastSave;
    }

    public CheckFeedbackPayload lastCheck() {
        return lastCheck;
    }

    /** Who {@link #lastCheck()} was shown to. */
    public LivingEntity lastCheckRoller() {
        return lastCheckRoller;
    }

    public void reset() {
        lastRoll = null;
        lastSave = null;
        lastCheck = null;
        lastCheckRoller = null;
    }
}
