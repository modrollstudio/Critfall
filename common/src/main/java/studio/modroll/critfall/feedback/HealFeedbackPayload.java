package studio.modroll.critfall.feedback;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;

/**
 * S2C feedback for one heal from {@link studio.modroll.critfall.api.RollService#performHeal}: {@code Cure Wounds:
 * 2d8+3 = 12 healed}.
 *
 * @param label names the heal: a translation key, or plain text; empty for none
 * @param diceNotation the healing dice
 * @param rolled their total
 * @param healed the health the target actually gained, shown after the roll when it differs
 */
public record HealFeedbackPayload(String label, String diceNotation, int rolled, float healed)
        implements CustomPacketPayload {

    public HealFeedbackPayload {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(diceNotation, "diceNotation");
    }

    public static final Type<HealFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "heal_feedback"));

    public static final StreamCodec<FriendlyByteBuf, HealFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(HealFeedbackPayload::encode, HealFeedbackPayload::decode);

    @Override
    public Type<HealFeedbackPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, HealFeedbackPayload p) {
        buf.writeUtf(p.label);
        buf.writeUtf(p.diceNotation);
        buf.writeInt(p.rolled);
        buf.writeFloat(p.healed);
    }

    private static HealFeedbackPayload decode(FriendlyByteBuf buf) {
        return new HealFeedbackPayload(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readFloat());
    }
}
