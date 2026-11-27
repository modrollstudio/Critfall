package studio.modroll.critfall.feedback;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.Critfall;

/**
 * S2C feedback for damage with no roll to hit, from {@link studio.modroll.critfall.api.RollService#rollDamage}: {@code
 * Magic Missile: 3d4+3 = 9 damage}.
 *
 * @param label names the damage: a translation key, or plain text; empty for none
 * @param diceNotation the damage dice
 * @param dealt the damage Critfall applied, after resistances
 * @param taken the health the target actually lost, shown after the damage when it differs
 */
public record DamageFeedbackPayload(String label, String diceNotation, int dealt, float taken)
        implements CustomPacketPayload {

    public DamageFeedbackPayload {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(diceNotation, "diceNotation");
    }

    public static final Type<DamageFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "damage_feedback"));

    public static final StreamCodec<FriendlyByteBuf, DamageFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(DamageFeedbackPayload::encode, DamageFeedbackPayload::decode);

    @Override
    public Type<DamageFeedbackPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, DamageFeedbackPayload p) {
        buf.writeUtf(p.label);
        buf.writeUtf(p.diceNotation);
        buf.writeInt(p.dealt);
        buf.writeFloat(p.taken);
    }

    private static DamageFeedbackPayload decode(FriendlyByteBuf buf) {
        return new DamageFeedbackPayload(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readFloat());
    }
}
