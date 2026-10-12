package studio.modroll.critfall.api.feedback;

import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;

/**
 * S2C feedback for one labelled d20 roll in Critfall's readout: a saving throw or an ability check against
 * a DC, or a hazard's attack against AC. Send one with {@link
 * studio.modroll.critfall.api.RollService#sendCheckFeedback}; build a save or check from its {@link
 * SaveResult} with {@link #save} or {@link #check}. Rides its own optional channel, so a client without it
 * gets the server-rendered action bar. Since 0.2.13.
 *
 * @param kind what was rolled
 * @param label names the roll, such as {@code "Perception"} or a trap: a translation key, or plain text;
 *     empty for none
 * @param natural the kept d20 face
 * @param total natural plus the bonus
 * @param dc the DC, or the AC for {@link Kind#ATTACK}
 * @param success saved, passed, or hit
 * @param crit an {@link Kind#ATTACK} that crit
 * @param rollMode how the d20 was rolled
 * @param droppedNatural the other face under advantage or disadvantage
 * @param diceNotation the damage dice, or empty when the roll shows no damage
 * @param damage the damage taken, shown with {@code diceNotation}
 */
public record CheckFeedbackPayload(
        Kind kind,
        String label,
        int natural,
        int total,
        int dc,
        boolean success,
        boolean crit,
        RollMode rollMode,
        OptionalInt droppedNatural,
        String diceNotation,
        int damage)
        implements CustomPacketPayload {

    public enum Kind {
        SAVE,
        CHECK,
        ATTACK
    }

    public CheckFeedbackPayload {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rollMode, "rollMode");
        Objects.requireNonNull(droppedNatural, "droppedNatural");
        Objects.requireNonNull(diceNotation, "diceNotation");
    }

    /** A saving throw with no damage shown. */
    public static CheckFeedbackPayload save(String label, SaveResult result) {
        return of(Kind.SAVE, label, result);
    }

    /** An ability or skill check; Checks returns its checks as a {@link SaveResult} too. */
    public static CheckFeedbackPayload check(String label, SaveResult result) {
        return of(Kind.CHECK, label, result);
    }

    private static CheckFeedbackPayload of(Kind kind, String label, SaveResult result) {
        return new CheckFeedbackPayload(
                kind,
                label,
                result.natural(),
                result.saveTotal(),
                result.dc(),
                result.saved(),
                false,
                result.roll().mode(),
                result.roll().dropped(),
                "",
                0);
    }

    public RollDetail roll() {
        return new RollDetail(rollMode, natural, droppedNatural);
    }

    /** The modifier the roll used. */
    public int bonus() {
        return total - natural;
    }

    public boolean showsDamage() {
        return !diceNotation.isEmpty();
    }

    public static final Type<CheckFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "check_feedback"));

    public static final StreamCodec<FriendlyByteBuf, CheckFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(CheckFeedbackPayload::encode, CheckFeedbackPayload::decode);

    @Override
    public Type<CheckFeedbackPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, CheckFeedbackPayload p) {
        buf.writeEnum(p.kind);
        buf.writeUtf(p.label);
        buf.writeVarInt(p.natural);
        buf.writeInt(p.total);
        buf.writeInt(p.dc);
        buf.writeBoolean(p.success);
        buf.writeBoolean(p.crit);
        buf.writeEnum(p.rollMode);
        buf.writeBoolean(p.droppedNatural.isPresent());
        if (p.droppedNatural.isPresent()) {
            buf.writeVarInt(p.droppedNatural.getAsInt());
        }
        buf.writeUtf(p.diceNotation);
        buf.writeVarInt(p.damage);
    }

    private static CheckFeedbackPayload decode(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        String label = buf.readUtf();
        int natural = buf.readVarInt();
        int total = buf.readInt();
        int dc = buf.readInt();
        boolean success = buf.readBoolean();
        boolean crit = buf.readBoolean();
        RollMode rollMode = buf.readEnum(RollMode.class);
        OptionalInt dropped = buf.readBoolean() ? OptionalInt.of(buf.readVarInt()) : OptionalInt.empty();
        String dice = buf.readUtf();
        int damage = buf.readVarInt();
        return new CheckFeedbackPayload(
                kind, label, natural, total, dc, success, crit, rollMode, dropped, dice, damage);
    }
}
