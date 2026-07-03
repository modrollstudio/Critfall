package studio.modroll.critfall.data;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * The loaded datapack state, swapped atomically on every (re)load. Reads happen on the server
 * thread mid-combat; loads happen from {@code /reload} — hence immutable snapshots behind
 * volatile fields, same pattern as {@link studio.modroll.critfall.RollService}.
 */
public final class ProfileStore {

    private static volatile Map<ResourceLocation, EntityProfile> entityProfiles = Map.of();
    private static volatile Map<ResourceLocation, ItemProfile> itemProfiles = Map.of();
    private static volatile Map<ResourceLocation, OutcomeTable> outcomeTables = Map.of();

    private ProfileStore() {}

    public static void setEntityProfiles(Map<ResourceLocation, EntityProfile> profiles) {
        entityProfiles = Map.copyOf(profiles);
    }

    public static void setItemProfiles(Map<ResourceLocation, ItemProfile> profiles) {
        itemProfiles = Map.copyOf(profiles);
    }

    public static void setOutcomeTables(Map<ResourceLocation, OutcomeTable> tables) {
        outcomeTables = Map.copyOf(tables);
    }

    public static Map<ResourceLocation, EntityProfile> entityProfiles() {
        return entityProfiles;
    }

    public static Map<ResourceLocation, ItemProfile> itemProfiles() {
        return itemProfiles;
    }

    public static Optional<OutcomeTable> outcomeTable(ResourceLocation id) {
        return Optional.ofNullable(outcomeTables.get(id));
    }

    public static Optional<EntityProfile> findEntityProfile(
            ResourceLocation entityTypeId, Predicate<ResourceLocation> tagTest) {
        return resolve(entityProfiles.values(), entityTypeId, tagTest);
    }

    public static Optional<ItemProfile> findItemProfile(ResourceLocation itemId, Predicate<ResourceLocation> tagTest) {
        return resolve(itemProfiles.values(), itemId, tagTest);
    }

    /**
     * Picks the winning profile for {@code id}: highest {@code priority} first; among equals the
     * most specific matching entry wins (exact id &gt; tag &gt; namespace wildcard); a remaining
     * tie goes to the lexicographically smaller file id so resolution is deterministic across
     * reloads.
     */
    static <T extends Profile> Optional<T> resolve(
            Collection<T> profiles, ResourceLocation id, Predicate<ResourceLocation> tagTest) {
        T best = null;
        int bestSpecificity = 0;
        for (T profile : profiles) {
            int specificity = 0;
            for (MatchEntry entry : profile.matches()) {
                if (entry.specificity() > specificity && entry.matches(id, tagTest)) {
                    specificity = entry.specificity();
                }
            }
            if (specificity == 0) {
                continue;
            }
            if (best == null || wins(profile, specificity, best, bestSpecificity)) {
                best = profile;
                bestSpecificity = specificity;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean wins(Profile candidate, int specificity, Profile best, int bestSpecificity) {
        if (candidate.priority() != best.priority()) {
            return candidate.priority() > best.priority();
        }
        if (specificity != bestSpecificity) {
            return specificity > bestSpecificity;
        }
        return candidate.id().compareTo(best.id()) < 0;
    }
}
