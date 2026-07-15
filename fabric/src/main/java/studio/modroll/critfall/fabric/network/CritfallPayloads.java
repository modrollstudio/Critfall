package studio.modroll.critfall.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Registers the feedback payloads as S2C types. Fabric sends only to clients that negotiated the
 * type ({@code ServerPlayNetworking.canSend}), so vanilla/modless clients are never disconnected —
 * the same optional-channel guarantee NeoForge gets from an optional registrar.
 */
public final class CritfallPayloads {

    private CritfallPayloads() {}

    public static void registerCodecs() {
        PayloadTypeRegistry.playS2C().register(RollFeedbackPayload.TYPE, RollFeedbackPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SaveFeedbackPayload.TYPE, SaveFeedbackPayload.STREAM_CODEC);
    }
}
