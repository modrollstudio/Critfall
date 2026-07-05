package studio.modroll.critfall.neoforge;

import java.nio.file.Path;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.command.CritfallCommands;
import studio.modroll.critfall.data.ProfileReloadListener;
import studio.modroll.critfall.data.RulesLoader;
import studio.modroll.critfall.data.RulesReloadListener;
import studio.modroll.critfall.neoforge.client.CritfallClient;
import studio.modroll.critfall.neoforge.network.CritfallPayloads;

@Mod(Critfall.MOD_ID)
public final class CritfallNeoForge {

    private final Path rulesFile =
            FMLPaths.CONFIGDIR.get().resolve(Critfall.MOD_ID).resolve("rules.json");

    public CritfallNeoForge(IEventBus modBus) {
        Critfall.init();
        RollRuntime.setRules(RulesLoader.load(rulesFile));
        studio.modroll.critfall.feedback.FeedbackSink.set(
                studio.modroll.critfall.neoforge.network.FeedbackDispatcher.asSink());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) ->
                studio.modroll.critfall.api.CombatSuppression.clear());
        NeoForge.EVENT_BUS.addListener(DamageEventHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        modBus.addListener(CritfallPayloads::register);
        modBus.addListener(this::onClientSetup);
    }

    /**
     * Loads the client-only feedback config. {@link FMLClientSetupEvent} is fired by FML only on the
     * physical client; the {@link FMLEnvironment#dist} check is an extra dist-safety guard so
     * {@link CritfallClient}, which touches no Minecraft render class, is never even referenced from
     * code that could run on a dedicated server.
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        if (FMLEnvironment.dist.isClient()) {
            CritfallClient.init();
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        // Outcome tables must load before item profiles: profiles validate their table references.
        event.addListener(ProfileReloadListener.outcomeTables());
        event.addListener(ProfileReloadListener.entityProfiles());
        event.addListener(ProfileReloadListener.itemProfiles());
        event.addListener(ProfileReloadListener.spellProfiles());
        event.addListener(ProfileReloadListener.flavorPools());
        event.addListener(new RulesReloadListener(rulesFile));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CritfallCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
