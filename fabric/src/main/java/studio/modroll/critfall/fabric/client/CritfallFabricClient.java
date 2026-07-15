package studio.modroll.critfall.fabric.client;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.ClientConfig;
import studio.modroll.critfall.feedback.ClientConfigLoader;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

/**
 * Client-only entrypoint: loads {@code config/critfall/client.json} and registers the feedback
 * payload receivers. Only invoked on the physical client, so the client render classes it reaches
 * ({@link ClientFeedbackReceiver}) never load on a dedicated server. Mirrors the NeoForge
 * {@code CritfallClient} + {@code ClientFeedbackReceiver}.
 */
public final class CritfallFabricClient implements ClientModInitializer {

    private static volatile ClientConfig config = ClientConfig.DEFAULTS;

    public static ClientConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        Path file = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Critfall.MOD_ID)
                .resolve("client.json");
        config = ClientConfigLoader.load(file);

        ClientPlayNetworking.registerGlobalReceiver(RollFeedbackPayload.TYPE, (payload, context) -> context.client()
                .execute(() -> ClientFeedbackReceiver.renderRoll(payload)));
        ClientPlayNetworking.registerGlobalReceiver(SaveFeedbackPayload.TYPE, (payload, context) -> context.client()
                .execute(() -> ClientFeedbackReceiver.renderSave(payload)));
    }
}
