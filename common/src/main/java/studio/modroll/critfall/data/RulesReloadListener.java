package studio.modroll.critfall.data;

import java.nio.file.Path;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import studio.modroll.critfall.RollService;

/**
 * Re-reads {@code config/critfall/rules.json} whenever datapacks reload, making the rules config
 * hot-reloadable via {@code /reload} even though it is not itself a datapack resource.
 */
public record RulesReloadListener(Path rulesFile) implements ResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        RollService.setRules(RulesLoader.load(rulesFile));
    }
}
