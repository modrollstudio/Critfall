package studio.modroll.critfall.feedback;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.feedback.FlavorName;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;

/**
 * S2C feedback for one resolved saving throw (M6). Flat primitives only; see {@link RollFeedbackPayload}, whose
 * {@code taken} this shares: the health the damage actually took, shown after the damage when it differs.
 */
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
        boolean dryRun,
        RollMode rollMode,
        OptionalInt droppedNatural,
        FlavorName flavorAttacker,
        FlavorName flavorTarget,
        float taken)
        implements CustomPacketPayload {

    /** Convenience without the damage taken (pre-0.2.18 callers). */
    public SaveFeedbackPayload(
            int natural,
            int saveTotal,
            int dc,
            boolean saved,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            Optional<String> flavorKey,
            boolean dryRun,
            RollMode rollMode,
            OptionalInt droppedNatural,
            FlavorName flavorAttacker,
            FlavorName flavorTarget) {
        this(
                natural,
                saveTotal,
                dc,
                saved,
                onSuccess,
                diceNotation,
                damage,
                showDamage,
                flavorKey,
                dryRun,
                rollMode,
                droppedNatural,
                flavorAttacker,
                flavorTarget,
                RollFeedbackPayload.TAKEN_UNKNOWN);
    }

    /** Convenience without flavor names (pre-0.2.15 callers). */
    public SaveFeedbackPayload(
            int natural,
            int saveTotal,
            int dc,
            boolean saved,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            Optional<String> flavorKey,
            boolean dryRun,
            RollMode rollMode,
            OptionalInt droppedNatural) {
        this(
                natural,
                saveTotal,
                dc,
                saved,
                onSuccess,
                diceNotation,
                damage,
                showDamage,
                flavorKey,
                dryRun,
                rollMode,
                droppedNatural,
                FlavorName.NONE,
                FlavorName.NONE);
    }

    /** Convenience for a plain normal roll (pre-0.2.6 callers). */
    public SaveFeedbackPayload(
            int natural,
            int saveTotal,
            int dc,
            boolean saved,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            Optional<String> flavorKey,
            boolean dryRun) {
        this(
                natural,
                saveTotal,
                dc,
                saved,
                onSuccess,
                diceNotation,
                damage,
                showDamage,
                flavorKey,
                dryRun,
                RollMode.NORMAL,
                OptionalInt.empty());
    }

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

    public RollDetail roll() {
        return new RollDetail(rollMode, natural, droppedNatural);
    }

    /**
     * {@code critfall:save_feedback_v3} since 0.2.18, which added the damage taken ({@code _v2} since 0.2.15),
     * for the same reason as {@link RollFeedbackPayload#TYPE}.
     */
    public static final Type<SaveFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "save_feedback_v3"));

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
        buf.writeEnum(p.rollMode);
        buf.writeBoolean(p.droppedNatural.isPresent());
        if (p.droppedNatural.isPresent()) {
            buf.writeVarInt(p.droppedNatural.getAsInt());
        }
        FlavorName.write(buf, p.flavorAttacker);
        FlavorName.write(buf, p.flavorTarget);
        buf.writeFloat(p.taken);
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
        RollMode rollMode = buf.readEnum(RollMode.class);
        OptionalInt dropped = buf.readBoolean() ? OptionalInt.of(buf.readVarInt()) : OptionalInt.empty();
        FlavorName flavorAttacker = FlavorName.read(buf);
        FlavorName flavorTarget = FlavorName.read(buf);
        float taken = buf.readFloat();
        return new SaveFeedbackPayload(
                natural,
                saveTotal,
                dc,
                saved,
                onSuccess,
                dice,
                damage,
                showDamage,
                flavor,
                dryRun,
                rollMode,
                dropped,
                flavorAttacker,
                flavorTarget,
                taken);
    }
}
