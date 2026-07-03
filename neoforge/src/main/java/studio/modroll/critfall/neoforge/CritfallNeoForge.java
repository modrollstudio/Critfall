package studio.modroll.critfall.neoforge;

import java.nio.file.Path;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.command.CritfallCommands;
import studio.modroll.critfall.data.ProfileReloadListener;
import studio.modroll.critfall.data.RulesLoader;
import studio.modroll.critfall.data.RulesReloadListener;

@Mod(Critfall.MOD_ID)
public final class CritfallNeoForge {

    private final Path rulesFile =
            FMLPaths.CONFIGDIR.get().resolve(Critfall.MOD_ID).resolve("rules.json");

    public CritfallNeoForge() {
        Critfall.init();
        RollService.setRules(RulesLoader.load(rulesFile));
        NeoForge.EVENT_BUS.addListener(DamageEventHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        // Outcome tables must load before item profiles: profiles validate their table references.
        event.addListener(ProfileReloadListener.outcomeTables());
        event.addListener(ProfileReloadListener.entityProfiles());
        event.addListener(ProfileReloadListener.itemProfiles());
        event.addListener(new RulesReloadListener(rulesFile));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CritfallCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
