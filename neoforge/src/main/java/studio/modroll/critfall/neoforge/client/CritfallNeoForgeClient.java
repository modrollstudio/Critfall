package studio.modroll.critfall.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.client.RulesScreen;

/**
 * The client-only entrypoint: the mod list's Config button opens the rules screen. A separate
 * {@code @Mod} class for {@link Dist#CLIENT} keeps the screen classes off a dedicated server.
 */
@Mod(value = Critfall.MOD_ID, dist = Dist.CLIENT)
public final class CritfallNeoForgeClient {

    public CritfallNeoForgeClient(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> RulesScreen.forCurrentGame(
                        parent,
                        FMLPaths.CONFIGDIR.get().resolve(Critfall.MOD_ID).resolve("rules.json")));
    }
}
