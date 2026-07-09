package studio.modroll.critfall.feedback;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.combat.AttackOutcome;

/**
 * S2C feedback for one resolved attack roll (M6). Flat primitives only — the client rebuilds every
 * {@link net.minecraft.network.chat.Component} from these fields, and the modless fallback renders
 * the same data server-side via {@link studio.modroll.critfall.combat.CombatText}. {@code flavorKey} is
 * present only when the server's anti-spam gate let a flavor line through; {@code consequences} lists
 * the outcome-table consequences that actually fired.
 */
public record RollFeedbackPayload(
        AttackOutcome outcome,
        int natural,
        int attackTotal,
        int armorClass,
        int damage,
        String diceNotation,
        boolean showDamage,
        Optional<String> flavorKey,
        List<ConsequenceLine> consequences,
        boolean dryRun)
        implements CustomPacketPayload {

    /** Convenience for the common non-dry-run case (and the M6 tests that predate the flag). */
    public RollFeedbackPayload(
            AttackOutcome outcome,
            int natural,
            int attackTotal,
            int armorClass,
            int damage,
            String diceNotation,
            boolean showDamage,
            Optional<String> flavorKey,
            List<ConsequenceLine> consequences) {
        this(
                outcome,
                natural,
                attackTotal,
                armorClass,
                damage,
                diceNotation,
                showDamage,
                flavorKey,
                consequences,
                false);
    }

    /**
     * Decode-side cap on the consequence list. Real payloads carry at most a couple of lines (one
     * per fired outcome table); without a cap a hostile/corrupted server could claim a 2³¹−1
     * element list and the vanilla collection reader would preallocate it — OOM instead of a
     * clean decode-error disconnect (audit 0.2 finding D2).
     */
    private static final int MAX_CONSEQUENCES = 64;

    public static final Type<RollFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "roll_feedback"));

    public static final StreamCodec<FriendlyByteBuf, RollFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(RollFeedbackPayload::encode, RollFeedbackPayload::decode);

    @Override
    public Type<RollFeedbackPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, RollFeedbackPayload p) {
        buf.writeEnum(p.outcome);
        buf.writeVarInt(p.natural);
        buf.writeInt(p.attackTotal);
        buf.writeVarInt(p.armorClass);
        buf.writeVarInt(p.damage);
        buf.writeUtf(p.diceNotation);
        buf.writeBoolean(p.showDamage);
        buf.writeOptional(p.flavorKey, FriendlyByteBuf::writeUtf);
        buf.writeCollection(p.consequences, RollFeedbackPayload::writeLine);
        buf.writeBoolean(p.dryRun);
    }

    private static RollFeedbackPayload decode(FriendlyByteBuf buf) {
        AttackOutcome outcome = buf.readEnum(AttackOutcome.class);
        int natural = buf.readVarInt();
        int attackTotal = buf.readInt();
        int armorClass = buf.readVarInt();
        int damage = buf.readVarInt();
        String dice = buf.readUtf();
        boolean showDamage = buf.readBoolean();
        Optional<String> flavor = buf.readOptional(FriendlyByteBuf::readUtf);
        List<ConsequenceLine> lines = buf.readCollection(
                size -> {
                    if (size < 0 || size > MAX_CONSEQUENCES) {
                        throw new DecoderException(
                                "consequence list length " + size + " out of bounds (max " + MAX_CONSEQUENCES + ")");
                    }
                    return new ArrayList<>(size);
                },
                RollFeedbackPayload::readLine);
        boolean dryRun = buf.readBoolean();
        return new RollFeedbackPayload(
                outcome,
                natural,
                attackTotal,
                armorClass,
                damage,
                dice,
                showDamage,
                flavor,
                List.copyOf(lines),
                dryRun);
    }

    static void writeLine(FriendlyByteBuf buf, ConsequenceLine line) {
        buf.writeUtf(line.key());
        buf.writeOptional(line.arg(), FriendlyByteBuf::writeUtf);
    }

    static ConsequenceLine readLine(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        Optional<String> arg = buf.readOptional(FriendlyByteBuf::readUtf);
        return new ConsequenceLine(key, arg);
    }
}
