package studio.modroll.critfall.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.client.RulesScreen;

/** Mod Menu's mod list opens the rules screen. Only loaded when Mod Menu is installed. */
public final class CritfallModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> RulesScreen.forCurrentGame(
                parent,
                FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve(Critfall.MOD_ID)
                        .resolve("rules.json"));
    }
}
