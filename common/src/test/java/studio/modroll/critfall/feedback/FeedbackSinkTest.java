package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.Rules;

class FeedbackSinkTest {

    @AfterEach
    void reset() {
        FeedbackSink.set(FeedbackSink.NOOP);
    }

    @Test
    void defaultsToNoop() {
        assertSame(FeedbackSink.NOOP, FeedbackSink.get());
    }

    @Test
    void registeredSinkReceivesRoll() {
        RollFeedbackPayload[] captured = {null};
        FeedbackSink.set(new FeedbackSink() {
            @Override
            public void roll(
                    net.minecraft.world.entity.LivingEntity a,
                    net.minecraft.world.entity.LivingEntity t,
                    RollFeedbackPayload p,
                    Rules.FeedbackVisibility v) {
                captured[0] = p;
            }

            @Override
            public void save(
                    net.minecraft.world.entity.LivingEntity a,
                    net.minecraft.world.entity.LivingEntity t,
                    SaveFeedbackPayload p,
                    Rules.FeedbackVisibility v) {}
        });
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6+1", true, Optional.empty(), List.of());
        FeedbackSink.get().roll(null, null, payload, Rules.FeedbackVisibility.EVERYONE);
        assertEquals(payload, captured[0]);
    }
}
