package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
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
    void rollPayloadRoundTripsDryRunFlag() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.MISS, 1, 5, 14, 0, "2d6", false, Optional.empty(), List.of(), true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertTrue(RollFeedbackPayload.STREAM_CODEC.decode(buf).dryRun());
    }

    @Test
    void savePayloadRoundTripsDryRunFlag() {
        SaveFeedbackPayload payload = new SaveFeedbackPayload(
                12, 15, 13, true, Rules.SaveOutcome.HALF, "2d6", 3, true, Optional.empty(), true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SaveFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertTrue(SaveFeedbackPayload.STREAM_CODEC.decode(buf).dryRun());
    }

    @Test
    void decodeRejectsAbsurdConsequenceCountInsteadOfAllocating() {
        // A hostile/corrupted server can claim a 2^31-1 element consequence list; the decoder must
        // fail as a normal decode error (clean disconnect), not preallocate gigabytes and OOM.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(AttackOutcome.HIT);
        buf.writeVarInt(13); // natural
        buf.writeInt(16); // attackTotal
        buf.writeVarInt(10); // armorClass
        buf.writeVarInt(5); // damage
        buf.writeUtf("1d6");
        buf.writeBoolean(true); // showDamage
        buf.writeOptional(Optional.<String>empty(), FriendlyByteBuf::writeUtf); // flavorKey
        buf.writeVarInt(Integer.MAX_VALUE); // claimed consequence count
        assertThrows(DecoderException.class, () -> RollFeedbackPayload.STREAM_CODEC.decode(buf));
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
