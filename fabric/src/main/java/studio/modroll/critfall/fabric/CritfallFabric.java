package studio.modroll.critfall.fabric;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.command.CritfallCommands;
import studio.modroll.critfall.data.ProfileReloadListener;
import studio.modroll.critfall.data.RulesLoader;
import studio.modroll.critfall.data.RulesReloadListener;
import studio.modroll.critfall.fabric.network.CritfallPayloads;
import studio.modroll.critfall.fabric.network.FabricFeedbackDispatcher;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * Fabric entrypoint — the loader-specific wiring around the shared common code. Mirrors
 * {@link studio.modroll.critfall.Critfall} usage on NeoForge one-to-one: rules load, feedback sink,
 * commands, datapack reload listeners, per-entity suppression cleanup, and the S2C payload codecs.
 * Damage interception itself is wired through {@link FabricDamageHook} (ALLOW_DAMAGE) plus the
 * {@code LivingEntityMixin}.
 */
public final class CritfallFabric implements ModInitializer {

    private static final ResourceLocation OUTCOME_TABLES =
            ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "outcome_table");

    private final Path rulesFile =
            FabricLoader.getInstance().getConfigDir().resolve(Critfall.MOD_ID).resolve("rules.json");

    @Override
    public void onInitialize() {
        Critfall.init();
        RollRuntime.setRules(RulesLoader.load(rulesFile));
        FeedbackSink.set(FabricFeedbackDispatcher.asSink());
        CritfallPayloads.registerCodecs();

        // Damage rolls run in ALLOW_DAMAGE (correct point: after invulnerability, before mitigation);
        // LivingEntityMixin applies the rolled amount + armor bypass it decides.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(FabricDamageHook::allowDamage);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            studio.modroll.critfall.combat.SuppressionStore.clear();
            // Per-entity cooldown maps outlive a world in one JVM (audit 0.2 A1/A2): drop them so
            // a fresh world never sees another world's timestamps and nothing accumulates.
            studio.modroll.critfall.combat.FumbleCooldowns.clear();
            studio.modroll.critfall.feedback.FlavorCooldowns.clear();
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> CritfallCommands.register(dispatcher, registryAccess));

        registerReloadListeners();
    }

    // Outcome tables load before the profiles that validate their table references; rules reload
    // last. Fabric honours these via getFabricDependencies() rather than registration order.
    private void registerReloadListeners() {
        ResourceManagerHelper data = ResourceManagerHelper.get(PackType.SERVER_DATA);
        register(data, "outcome_table", List.of(), ProfileReloadListener.outcomeTables());
        register(data, "entity_profile", List.of(OUTCOME_TABLES), ProfileReloadListener.entityProfiles());
        register(data, "item_profile", List.of(OUTCOME_TABLES), ProfileReloadListener.itemProfiles());
        register(data, "spell_profile", List.of(OUTCOME_TABLES), ProfileReloadListener.spellProfiles());
        register(data, "flavor_pool", List.of(OUTCOME_TABLES), ProfileReloadListener.flavorPools());
        register(data, "rules", List.of(OUTCOME_TABLES), new RulesReloadListener(rulesFile));
    }

    private static void register(
            ResourceManagerHelper helper,
            String path,
            Collection<ResourceLocation> dependencies,
            PreparableReloadListener delegate) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, path);
        helper.registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public Collection<ResourceLocation> getFabricDependencies() {
                return dependencies;
            }

            @Override
            public CompletableFuture<Void> reload(
                    PreparationBarrier barrier,
                    ResourceManager manager,
                    ProfilerFiller prepareProfiler,
                    ProfilerFiller applyProfiler,
                    Executor prepareExecutor,
                    Executor applyExecutor) {
                return delegate.reload(
                        barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
            }
        });
    }
}
