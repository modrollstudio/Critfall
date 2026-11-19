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
    private volatile HealFeedbackPayload lastHeal;
    private volatile LivingEntity lastHealer;
    private volatile LivingEntity lastHealTarget;

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

    @Override
    public void heal(
            LivingEntity healer, LivingEntity target, HealFeedbackPayload payload, Rules.FeedbackVisibility v) {
        this.lastHeal = payload;
        this.lastHealer = healer;
        this.lastHealTarget = target;
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

    public HealFeedbackPayload lastHeal() {
        return lastHeal;
    }

    public LivingEntity lastHealer() {
        return lastHealer;
    }

    public LivingEntity lastHealTarget() {
        return lastHealTarget;
    }

    public void reset() {
        lastRoll = null;
        lastSave = null;
        lastCheck = null;
        lastCheckRoller = null;
        lastHeal = null;
        lastHealer = null;
        lastHealTarget = null;
    }
}
