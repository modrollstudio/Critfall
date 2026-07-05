package studio.modroll.critfall.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import studio.modroll.critfall.Critfall;

/**
 * Loads one Critfall datapack directory on every {@code /reload} and server start. A file that
 * fails to parse is skipped with an error — one broken profile must never take down the rest of
 * the pack, let alone the server.
 */
public final class ProfileReloadListener<T> extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    @FunctionalInterface
    public interface Parser<T> {
        T parse(ResourceLocation id, JsonObject json, Consumer<String> warn);
    }

    private final String kind;
    private final Parser<T> parser;
    private final Consumer<Map<ResourceLocation, T>> store;

    private ProfileReloadListener(
            String directory, String kind, Parser<T> parser, Consumer<Map<ResourceLocation, T>> store) {
        super(GSON, directory);
        this.kind = kind;
        this.parser = parser;
        this.store = store;
    }

    /** Register AFTER {@link #outcomeTables()} — table references are validated against the store. */
    public static ProfileReloadListener<EntityProfile> entityProfiles() {
        return new ProfileReloadListener<>(
                "critfall/entity_profile",
                "entity profile",
                EntityProfile::parse,
                ProfileReloadListener::storeEntityProfiles);
    }

    /** Register AFTER {@link #outcomeTables()} — table references are validated against the store. */
    public static ProfileReloadListener<ItemProfile> itemProfiles() {
        return new ProfileReloadListener<>(
                "critfall/item_profile", "item profile", ItemProfile::parse, ProfileReloadListener::storeItemProfiles);
    }

    public static ProfileReloadListener<SpellProfile> spellProfiles() {
        return new ProfileReloadListener<>(
                "critfall/spell_profile", "spell profile", SpellProfile::parse, ProfileStore::setSpellProfiles);
    }

    public static ProfileReloadListener<OutcomeTable> outcomeTables() {
        return new ProfileReloadListener<>(
                "critfall/outcome_table", "outcome table", OutcomeTable::parse, ProfileStore::setOutcomeTables);
    }

    public static ProfileReloadListener<FlavorPool> flavorPools() {
        return new ProfileReloadListener<>(
                "critfall/flavor_pool", "flavor pool", FlavorPool::parse, ProfileStore::setFlavorPools);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, T> loaded = new HashMap<>();
        files.forEach((id, element) -> {
            try {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("root must be a JSON object");
                }
                loaded.put(id, parser.parse(id, element.getAsJsonObject(), Critfall.LOG::warn));
            } catch (RuntimeException e) {
                Critfall.LOG.error("Skipping bad {} {}: {}", kind, id, e.getMessage());
            }
        });
        store.accept(loaded);
        Critfall.LOG.info("Loaded {} {}s", loaded.size(), kind);
    }

    private static void storeItemProfiles(Map<ResourceLocation, ItemProfile> profiles) {
        ProfileStore.setItemProfiles(profiles);
        for (ItemProfile profile : profiles.values()) {
            warnMissingTable("item_profile", profile.id(), profile.fumbleTable(), "fumble_table");
            warnMissingTable("item_profile", profile.id(), profile.critTable(), "crit_table");
        }
    }

    private static void storeEntityProfiles(Map<ResourceLocation, EntityProfile> profiles) {
        ProfileStore.setEntityProfiles(profiles);
        for (EntityProfile profile : profiles.values()) {
            warnMissingTable("entity_profile", profile.id(), profile.fumbleTable(), "fumble_table");
            warnMissingTable("entity_profile", profile.id(), profile.critTable(), "crit_table");
        }
    }

    private static void warnMissingTable(
            String kind, ResourceLocation profileId, java.util.Optional<ResourceLocation> table, String field) {
        if (table.isPresent() && ProfileStore.outcomeTable(table.get()).isEmpty()) {
            Critfall.LOG.warn(
                    "{} {} references {} '{}' but no such outcome table is loaded",
                    kind,
                    profileId,
                    field,
                    table.get());
        }
    }
}
