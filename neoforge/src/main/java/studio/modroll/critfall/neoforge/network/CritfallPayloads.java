package studio.modroll.critfall.neoforge.network;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Registers the M6 feedback payloads as OPTIONAL S2C channels so vanilla / non-mod clients are never
 * disconnected for not understanding them. The client receiver is only wired on the physical client
 * ({@link FMLEnvironment#dist}); on a dedicated server the codec is still registered (so the server
 * can send) but the client class is never loaded.
 */
public final class CritfallPayloads {

    private CritfallPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(
                RollFeedbackPayload.TYPE,
                RollFeedbackPayload.STREAM_CODEC,
                FMLEnvironment.dist.isClient() ? ClientFeedbackReceiver::receiveRoll : CritfallPayloads::ignore);
        registrar.playToClient(
                SaveFeedbackPayload.TYPE,
                SaveFeedbackPayload.STREAM_CODEC,
                FMLEnvironment.dist.isClient() ? ClientFeedbackReceiver::receiveSave : CritfallPayloads::ignore);
    }

    private static void ignore(Object payload, IPayloadContext context) {}
}
