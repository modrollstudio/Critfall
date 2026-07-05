package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.Rules;

class CapturingFeedbackSinkTest {

    private static RollFeedbackPayload roll() {
        return new RollFeedbackPayload(AttackOutcome.HIT, 12, 15, 10, 5, "1d6+1", true, Optional.empty(), List.of());
    }

    @Test
    void capturesLastRollPayload() {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        assertNull(sink.lastRoll());
        RollFeedbackPayload payload = roll();
        sink.roll(null, null, payload, Rules.FeedbackVisibility.EVERYONE);
        assertSame(payload, sink.lastRoll());
    }

    @Test
    void resetClearsCapturedPayloads() {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        sink.roll(null, null, roll(), Rules.FeedbackVisibility.EVERYONE);
        sink.reset();
        assertNull(sink.lastRoll());
    }
}
