package studio.modroll.critfall.data;

import java.nio.file.Path;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import studio.modroll.critfall.RollRuntime;

/**
 * Re-reads {@code config/critfall/rules.json} whenever datapacks reload, making the rules config
 * hot-reloadable via {@code /reload} even though it is not itself a datapack resource. The config
 * screen saves through {@link #reload} too, so a saved file takes effect the same way.
 */
public record RulesReloadListener(Path rulesFile) implements ResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        reload(rulesFile);
    }

    /** Loads {@code rulesFile} into the live rules, exactly as {@code /reload} does. */
    public static void reload(Path rulesFile) {
        RollRuntime.setRules(RulesLoader.load(rulesFile));
    }
}
