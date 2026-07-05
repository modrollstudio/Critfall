package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.Rules;

class FeedbackPayloadCodecTest {

    @Test
    void rollPayloadRoundTrips() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.FUMBLE,
                1,
                4,
                14,
                0,
                "1d8+2",
                true,
                Optional.of("critfall.flavor.sword.fumble.0"),
                List.of(
                        ConsequenceLine.of(ConsequenceLine.DURABILITY_BROKEN),
                        ConsequenceLine.of(ConsequenceLine.HIT_ALLY, "Villager")));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, RollFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void rollPayloadRoundTripsWithoutFlavorOrConsequences() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6+1", true, Optional.empty(), List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, RollFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void savePayloadRoundTrips() {
        SaveFeedbackPayload payload =
                new SaveFeedbackPayload(12, 15, 13, true, Rules.SaveOutcome.HALF, "2d6", 3, true, Optional.empty());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SaveFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, SaveFeedbackPayload.STREAM_CODEC.decode(buf));
    }
}
