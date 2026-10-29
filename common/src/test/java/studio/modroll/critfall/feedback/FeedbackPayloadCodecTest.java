package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.FlavorName;
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
    void flavorNamesRoundTrip() {
        RollFeedbackPayload roll = new RollFeedbackPayload(
                AttackOutcome.CRIT,
                20,
                24,
                14,
                12,
                "1d8+2",
                true,
                Optional.of("critfall.flavor.base.crit.0"),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                0,
                FlavorName.literal("Alex"),
                new FlavorName("entity.minecraft.zombie", true));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, roll);
        assertEquals(roll, RollFeedbackPayload.STREAM_CODEC.decode(buf));

        SaveFeedbackPayload save = new SaveFeedbackPayload(
                3,
                5,
                13,
                false,
                Rules.SaveOutcome.HALF,
                "2d6",
                9,
                true,
                Optional.of("critfall.flavor.base.kill.0"),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                new FlavorName("entity.minecraft.evoker", true),
                FlavorName.literal("Steve"));
        buf = new FriendlyByteBuf(Unpooled.buffer());
        SaveFeedbackPayload.STREAM_CODEC.encode(buf, save);
        assertEquals(save, SaveFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void critMultiplierRoundTrips() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                15,
                17,
                10,
                7,
                "1d8",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                0,
                FlavorName.NONE,
                FlavorName.NONE,
                1.5F);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, RollFeedbackPayload.STREAM_CODEC.decode(buf));
        assertEquals(
                1.0F,
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6", true, Optional.empty(), List.of())
                        .critMultiplier(),
                "callers that predate the field carry no multiplier");
    }

    @Test
    void damageTakenRoundTrips() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                15,
                17,
                10,
                12,
                "1d10+4",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                0,
                FlavorName.NONE,
                FlavorName.NONE,
                1.0F,
                6.8F);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, RollFeedbackPayload.STREAM_CODEC.decode(buf));
        assertEquals(
                RollFeedbackPayload.TAKEN_UNKNOWN,
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6", true, Optional.empty(), List.of())
                        .taken(),
                "callers that predate the field leave it unknown");
        assertEquals("critfall:roll_feedback_v4", RollFeedbackPayload.TYPE.id().toString());
    }

    @Test
    void saveDamageTakenRoundTrips() {
        SaveFeedbackPayload save = new SaveFeedbackPayload(
                8,
                10,
                13,
                false,
                Rules.SaveOutcome.HALF,
                "2d6",
                7,
                true,
                Optional.empty(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                FlavorName.NONE,
                FlavorName.NONE,
                2.8F);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SaveFeedbackPayload.STREAM_CODEC.encode(buf, save);
        assertEquals(save, SaveFeedbackPayload.STREAM_CODEC.decode(buf));
        assertEquals(
                RollFeedbackPayload.TAKEN_UNKNOWN,
                new SaveFeedbackPayload(
                                8,
                                10,
                                13,
                                false,
                                Rules.SaveOutcome.HALF,
                                "2d6",
                                7,
                                true,
                                Optional.empty(),
                                false,
                                RollMode.NORMAL,
                                OptionalInt.empty(),
                                FlavorName.NONE,
                                FlavorName.NONE)
                        .taken(),
                "callers that predate the field leave it unknown");
        assertEquals("critfall:save_feedback_v3", SaveFeedbackPayload.TYPE.id().toString());
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
    void rollPayloadRoundTripsRollDetailAndAcSplit() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                18,
                22,
                16,
                7,
                "1d8+2",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.ADVANTAGE,
                OptionalInt.of(7),
                2);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        RollFeedbackPayload decoded = RollFeedbackPayload.STREAM_CODEC.decode(buf);
        assertEquals(payload, decoded);
        assertEquals(new RollDetail(RollMode.ADVANTAGE, 18, OptionalInt.of(7)), decoded.roll());
        assertEquals(14, decoded.baseArmorClass());
    }

    @Test
    void rollPayloadRoundTripsWithoutAdvantageOrAcBonus() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.MISS, 5, 8, 10, 0, "1d6", true, Optional.empty(), List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        RollFeedbackPayload decoded = RollFeedbackPayload.STREAM_CODEC.decode(buf);
        assertEquals(payload, decoded);
        assertEquals(RollMode.NORMAL, decoded.rollMode());
        assertEquals(OptionalInt.empty(), decoded.droppedNatural());
        assertEquals(0, decoded.defenderAcBonus());
    }

    @Test
    void rollPayloadRoundTripsNegativeAcBonus() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                8,
                8,
                8,
                3,
                "1d6",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                -2);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RollFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, RollFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void savePayloadRoundTripsRollDetail() {
        SaveFeedbackPayload payload = new SaveFeedbackPayload(
                4,
                6,
                13,
                false,
                Rules.SaveOutcome.HALF,
                "2d6",
                7,
                true,
                Optional.empty(),
                false,
                RollMode.DISADVANTAGE,
                OptionalInt.of(19));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SaveFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        SaveFeedbackPayload decoded = SaveFeedbackPayload.STREAM_CODEC.decode(buf);
        assertEquals(payload, decoded);
        assertEquals(new RollDetail(RollMode.DISADVANTAGE, 4, OptionalInt.of(19)), decoded.roll());
    }

    @Test
    void savePayloadRoundTrips() {
        SaveFeedbackPayload payload =
                new SaveFeedbackPayload(12, 15, 13, true, Rules.SaveOutcome.HALF, "2d6", 3, true, Optional.empty());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SaveFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, SaveFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void checkPayloadRoundTrips() {
        CheckFeedbackPayload payload = new CheckFeedbackPayload(
                CheckFeedbackPayload.Kind.ATTACK,
                "crawl.trap.darts",
                20,
                25,
                14,
                true,
                true,
                RollMode.ADVANTAGE,
                OptionalInt.of(3),
                "1d4",
                4);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CheckFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, CheckFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void checkPayloadRoundTripsWithoutDamageOrSecondDie() {
        CheckFeedbackPayload payload = CheckFeedbackPayload.check("", new SaveResult(9, 7, 15));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CheckFeedbackPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, CheckFeedbackPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void checkPayloadRoundTripsSaveKeyAndHiddenOutcome() {
        CheckFeedbackPayload named = CheckFeedbackPayload.save("Trap", "critfall:dexterity", new SaveResult(8, 10, 13));
        CheckFeedbackPayload hidden = CheckFeedbackPayload.hiddenCheck("Search", new SaveResult(14, 17, 15));
        for (CheckFeedbackPayload payload : List.of(named, hidden)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            CheckFeedbackPayload.STREAM_CODEC.encode(buf, payload);
            assertEquals(payload, CheckFeedbackPayload.STREAM_CODEC.decode(buf));
        }
    }
}
