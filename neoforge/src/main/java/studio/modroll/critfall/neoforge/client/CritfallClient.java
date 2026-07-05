package studio.modroll.critfall.neoforge.client;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.feedback.ClientConfig;
import studio.modroll.critfall.feedback.ClientConfigLoader;

/**
 * Client-only state: the loaded {@link ClientConfig}. This class touches only {@link Path} and the
 * common config types — never a Minecraft render class — so it is safe to reference from the common
 * {@code @Mod} class ({@link studio.modroll.critfall.neoforge.CritfallNeoForge}) even though it is only
 * ever initialized on the physical client.
 */
public final class CritfallClient {

    private static volatile ClientConfig config = ClientConfig.DEFAULTS;

    private CritfallClient() {}

    public static void init() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(Critfall.MOD_ID).resolve("client.json");
        config = ClientConfigLoader.load(file);
    }

    public static ClientConfig config() {
        return config;
    }
}
