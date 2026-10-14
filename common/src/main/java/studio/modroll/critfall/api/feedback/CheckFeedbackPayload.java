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
 * SaveResult} with {@link #save} or {@link #check}, or with {@link #hiddenCheck} to keep the DC and outcome
 * from the player. Rides its own optional channel, so a client without it gets the server-rendered action
 * bar. Since 0.2.13.
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
 * @param saveKey for a {@link Kind#SAVE}, the save's key, shown by its name ({@code "DEX save"}); empty for
 *     a plain {@code "save"}. Since 0.2.14
 * @param outcomeHidden the readout shows the roll and total only: no DC or AC, and no result. Since 0.2.14
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
        int damage,
        String saveKey,
        boolean outcomeHidden)
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
        Objects.requireNonNull(saveKey, "saveKey");
    }

    /** A payload with no save name and the outcome shown, as in 0.2.13. */
    public CheckFeedbackPayload(
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
            int damage) {
        this(kind, label, natural, total, dc, success, crit, rollMode, droppedNatural, diceNotation, damage, "", false);
    }

    /** A saving throw with no damage shown. */
    public static CheckFeedbackPayload save(String label, SaveResult result) {
        return of(Kind.SAVE, label, result);
    }

    /**
     * A saving throw named by its save key, such as {@code "DEX save"} for {@link
     * studio.modroll.critfall.api.ModifierProvider#DEXTERITY_SAVE}. Since 0.2.14.
     */
    public static CheckFeedbackPayload save(String label, String saveKey, SaveResult result) {
        return of(Kind.SAVE, label, result).withSaveKey(saveKey);
    }

    /** An ability or skill check; Checks returns its checks as a {@link SaveResult} too. */
    public static CheckFeedbackPayload check(String label, SaveResult result) {
        return of(Kind.CHECK, label, result);
    }

    /**
     * A check whose DC and result stay hidden: the readout shows the roll and total only, such as {@code
     * Search: check d20 14+3=17}. The payload carries no DC and no success, so a client cannot learn them.
     * Since 0.2.14.
     */
    public static CheckFeedbackPayload hiddenCheck(String label, SaveResult result) {
        return new CheckFeedbackPayload(
                Kind.CHECK,
                label,
                result.natural(),
                result.saveTotal(),
                0,
                false,
                false,
                result.roll().mode(),
                result.roll().dropped(),
                "",
                0,
                "",
                true);
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

    /** The same payload naming the save {@code key}; see {@link #saveKey()}. Since 0.2.14. */
    public CheckFeedbackPayload withSaveKey(String key) {
        return new CheckFeedbackPayload(
                kind,
                label,
                natural,
                total,
                dc,
                success,
                crit,
                rollMode,
                droppedNatural,
                diceNotation,
                damage,
                key,
                outcomeHidden);
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

    /**
     * {@code critfall:check_feedback_v2} since 0.2.14, which added the save key and hidden outcome to the
     * packet. A 0.2.13 client only has {@code critfall:check_feedback}, so it gets the action bar instead of a
     * packet it cannot read.
     */
    public static final Type<CheckFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "check_feedback_v2"));

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
        buf.writeUtf(p.saveKey);
        buf.writeBoolean(p.outcomeHidden);
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
        String saveKey = buf.readUtf();
        boolean outcomeHidden = buf.readBoolean();
        return new CheckFeedbackPayload(
                kind,
                label,
                natural,
                total,
                dc,
                success,
                crit,
                rollMode,
                dropped,
                dice,
                damage,
                saveKey,
                outcomeHidden);
    }
}
