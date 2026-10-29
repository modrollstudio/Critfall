package studio.modroll.critfall.api.feedback;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * S2C feedback for one resolved attack roll (M6). Flat primitives only — the client rebuilds every
 * {@link net.minecraft.network.chat.Component} from these fields, and the modless fallback renders
 * the same data server-side via {@link studio.modroll.critfall.combat.CombatText}. {@code flavorKey} is
 * present only when the server's anti-spam gate let a flavor line through; {@code consequences} lists
 * the outcome-table consequences that actually fired. {@code natural} is the kept d20 face, which
 * {@code rollMode} and {@code droppedNatural} complete into the {@link #roll()} detail.
 * {@code flavorAttacker} and {@code flavorTarget} fill the flavor line's placeholders; both are
 * {@link FlavorName#NONE} when there is no flavor line. {@code critMultiplier} is the crit multiplier a
 * hit's dice damage carried (vanilla's or another mod's, while Critfall's own crits are off), 1 when
 * none did; the readout shows it after the dice so the number matches the damage dealt. {@code taken} is
 * the health a hit actually took once Resistance, Protection, difficulty and absorption had their say
 * ({@link #TAKEN_UNKNOWN} when not known: a miss, dry-run, damage dice off, or a hurt no loader hook saw);
 * the readout shows it after the roll when it differs, {@code 1d10+4 = 12 → 7}.
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
        boolean dryRun,
        RollMode rollMode,
        OptionalInt droppedNatural,
        int defenderAcBonus,
        FlavorName flavorAttacker,
        FlavorName flavorTarget,
        float critMultiplier,
        float taken)
        implements CustomPacketPayload {

    /** {@link #taken()} when the health the hit took is not known. */
    public static final float TAKEN_UNKNOWN = -1.0F;

    /** Convenience without the damage taken (pre-0.2.18 callers). */
    public RollFeedbackPayload(
            AttackOutcome outcome,
            int natural,
            int attackTotal,
            int armorClass,
            int damage,
            String diceNotation,
            boolean showDamage,
            Optional<String> flavorKey,
            List<ConsequenceLine> consequences,
            boolean dryRun,
            RollMode rollMode,
            OptionalInt droppedNatural,
            int defenderAcBonus,
            FlavorName flavorAttacker,
            FlavorName flavorTarget,
            float critMultiplier) {
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
                dryRun,
                rollMode,
                droppedNatural,
                defenderAcBonus,
                flavorAttacker,
                flavorTarget,
                critMultiplier,
                TAKEN_UNKNOWN);
    }

    /** Convenience without a carried crit multiplier (pre-0.2.17 callers). */
    public RollFeedbackPayload(
            AttackOutcome outcome,
            int natural,
            int attackTotal,
            int armorClass,
            int damage,
            String diceNotation,
            boolean showDamage,
            Optional<String> flavorKey,
            List<ConsequenceLine> consequences,
            boolean dryRun,
            RollMode rollMode,
            OptionalInt droppedNatural,
            int defenderAcBonus,
            FlavorName flavorAttacker,
            FlavorName flavorTarget) {
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
                dryRun,
                rollMode,
                droppedNatural,
                defenderAcBonus,
                flavorAttacker,
                flavorTarget,
                1.0F);
    }

    /** Convenience without flavor names (pre-0.2.15 callers). */
    public RollFeedbackPayload(
            AttackOutcome outcome,
            int natural,
            int attackTotal,
            int armorClass,
            int damage,
            String diceNotation,
            boolean showDamage,
            Optional<String> flavorKey,
            List<ConsequenceLine> consequences,
            boolean dryRun,
            RollMode rollMode,
            OptionalInt droppedNatural,
            int defenderAcBonus) {
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
                dryRun,
                rollMode,
                droppedNatural,
                defenderAcBonus,
                FlavorName.NONE,
                FlavorName.NONE);
    }

    /** Convenience for a plain normal roll against an unmodified AC (pre-0.2.6 callers). */
    public RollFeedbackPayload(
            AttackOutcome outcome,
            int natural,
            int attackTotal,
            int armorClass,
            int damage,
            String diceNotation,
            boolean showDamage,
            Optional<String> flavorKey,
            List<ConsequenceLine> consequences,
            boolean dryRun) {
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
                dryRun,
                RollMode.NORMAL,
                OptionalInt.empty(),
                0);
    }

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

    public RollDetail roll() {
        return new RollDetail(rollMode, natural, droppedNatural);
    }

    /** The defender's AC (with any provided AC modifier), before {@link #defenderAcBonus()} was applied. */
    public int baseArmorClass() {
        return armorClass - defenderAcBonus;
    }

    /**
     * Decode-side cap on the consequence list. Real payloads carry at most a couple of lines (one
     * per fired outcome table); without a cap a hostile/corrupted server could claim a 2³¹−1
     * element list and the vanilla collection reader would preallocate it — OOM instead of a
     * clean decode-error disconnect (audit 0.2 finding D2).
     */
    private static final int MAX_CONSEQUENCES = 64;

    /**
     * {@code critfall:roll_feedback_v4} since 0.2.18, which added the damage taken ({@code _v3} since
     * 0.2.17 added the carried crit multiplier, {@code _v2} since 0.2.15 the flavor line's names). An older
     * client only has an older channel, so it gets the action bar instead of a packet it cannot read.
     */
    public static final Type<RollFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "roll_feedback_v4"));

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
        buf.writeEnum(p.rollMode);
        buf.writeBoolean(p.droppedNatural.isPresent());
        if (p.droppedNatural.isPresent()) {
            buf.writeVarInt(p.droppedNatural.getAsInt());
        }
        buf.writeInt(p.defenderAcBonus);
        FlavorName.write(buf, p.flavorAttacker);
        FlavorName.write(buf, p.flavorTarget);
        buf.writeFloat(p.critMultiplier);
        buf.writeFloat(p.taken);
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
        RollMode rollMode = buf.readEnum(RollMode.class);
        OptionalInt dropped = buf.readBoolean() ? OptionalInt.of(buf.readVarInt()) : OptionalInt.empty();
        int defenderAcBonus = buf.readInt();
        FlavorName flavorAttacker = FlavorName.read(buf);
        FlavorName flavorTarget = FlavorName.read(buf);
        float critMultiplier = buf.readFloat();
        float taken = buf.readFloat();
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
                dryRun,
                rollMode,
                dropped,
                defenderAcBonus,
                flavorAttacker,
                flavorTarget,
                critMultiplier,
                taken);
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
