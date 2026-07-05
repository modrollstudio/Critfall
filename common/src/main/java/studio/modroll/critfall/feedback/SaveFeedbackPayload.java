package studio.modroll.critfall.feedback;

import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.combat.Rules;

/** S2C feedback for one resolved saving throw (M6). Flat primitives only; see {@link RollFeedbackPayload}. */
public record SaveFeedbackPayload(
        int natural,
        int saveTotal,
        int dc,
        boolean saved,
        Rules.SaveOutcome onSuccess,
        String diceNotation,
        int damage,
        boolean showDamage,
        Optional<String> flavorKey,
        boolean dryRun)
        implements CustomPacketPayload {

    /** Convenience for the common non-dry-run case (and the M6 tests that predate the flag). */
    public SaveFeedbackPayload(
            int natural,
            int saveTotal,
            int dc,
            boolean saved,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            Optional<String> flavorKey) {
        this(natural, saveTotal, dc, saved, onSuccess, diceNotation, damage, showDamage, flavorKey, false);
    }

    public static final Type<SaveFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "save_feedback"));

    public static final StreamCodec<FriendlyByteBuf, SaveFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(SaveFeedbackPayload::encode, SaveFeedbackPayload::decode);

    @Override
    public Type<SaveFeedbackPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, SaveFeedbackPayload p) {
        buf.writeInt(p.natural);
        buf.writeInt(p.saveTotal);
        buf.writeVarInt(p.dc);
        buf.writeBoolean(p.saved);
        buf.writeEnum(p.onSuccess);
        buf.writeUtf(p.diceNotation);
        buf.writeVarInt(p.damage);
        buf.writeBoolean(p.showDamage);
        buf.writeOptional(p.flavorKey, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(p.dryRun);
    }

    private static SaveFeedbackPayload decode(FriendlyByteBuf buf) {
        int natural = buf.readInt();
        int saveTotal = buf.readInt();
        int dc = buf.readVarInt();
        boolean saved = buf.readBoolean();
        Rules.SaveOutcome onSuccess = buf.readEnum(Rules.SaveOutcome.class);
        String dice = buf.readUtf();
        int damage = buf.readVarInt();
        boolean showDamage = buf.readBoolean();
        Optional<String> flavor = buf.readOptional(FriendlyByteBuf::readUtf);
        boolean dryRun = buf.readBoolean();
        return new SaveFeedbackPayload(
                natural, saveTotal, dc, saved, onSuccess, dice, damage, showDamage, flavor, dryRun);
    }
}
