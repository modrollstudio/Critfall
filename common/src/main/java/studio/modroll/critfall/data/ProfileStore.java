package studio.modroll.critfall.data;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.api.AttackDelivery;

/**
 * The loaded datapack state, swapped atomically on every (re)load. Reads happen on the server
 * thread mid-combat; loads happen from {@code /reload} — hence immutable snapshots behind
 * volatile fields, same pattern as {@link studio.modroll.critfall.RollRuntime}.
 *
 * <p>Resolution is memoized per {@code (registry id, delivery)} (audit 0.2 finding B1: the linear
 * scan over every profile ran 4–6× per hit, server-wide). The cache is sound because a registry id
 * fully determines the match outcome within one datapack generation — tag membership hangs off the
 * id's registry entry, not the individual stack/entity — and every path that can change profiles
 * OR tags is a {@code /reload}, which re-runs the reload listeners and swaps the store, clearing
 * the cache here. Size is bounded by registry size × deliveries.
 */
public final class ProfileStore {

    private static volatile Map<ResourceLocation, EntityProfile> entityProfiles = Map.of();
    private static volatile Map<ResourceLocation, ItemProfile> itemProfiles = Map.of();
    private static volatile Map<ResourceLocation, SpellProfile> spellProfiles = Map.of();
    private static volatile Map<ResourceLocation, OutcomeTable> outcomeTables = Map.of();
    private static volatile Map<ResourceLocation, FlavorPool> flavorPools = Map.of();

    private record LookupKey(ResourceLocation id, AttackDelivery delivery) {}

    private static final Map<LookupKey, Optional<EntityProfile>> entityCache = new ConcurrentHashMap<>();
    private static final Map<LookupKey, Optional<ItemProfile>> itemCache = new ConcurrentHashMap<>();
    private static final Map<LookupKey, Optional<SpellProfile>> spellCache = new ConcurrentHashMap<>();
    private static final Map<LookupKey, Optional<FlavorPool>> flavorCache = new ConcurrentHashMap<>();

    private ProfileStore() {}

    public static void setEntityProfiles(Map<ResourceLocation, EntityProfile> profiles) {
        entityProfiles = Map.copyOf(profiles);
        entityCache.clear();
    }

    public static void setItemProfiles(Map<ResourceLocation, ItemProfile> profiles) {
        itemProfiles = Map.copyOf(profiles);
        itemCache.clear();
    }

    public static void setSpellProfiles(Map<ResourceLocation, SpellProfile> profiles) {
        spellProfiles = Map.copyOf(profiles);
        spellCache.clear();
    }

    public static void setOutcomeTables(Map<ResourceLocation, OutcomeTable> tables) {
        outcomeTables = Map.copyOf(tables);
    }

    public static void setFlavorPools(Map<ResourceLocation, FlavorPool> pools) {
        flavorPools = Map.copyOf(pools);
        flavorCache.clear();
    }

    public static Map<ResourceLocation, EntityProfile> entityProfiles() {
        return entityProfiles;
    }

    public static Map<ResourceLocation, ItemProfile> itemProfiles() {
        return itemProfiles;
    }

    public static Map<ResourceLocation, SpellProfile> spellProfiles() {
        return spellProfiles;
    }

    public static Map<ResourceLocation, OutcomeTable> outcomeTables() {
        return outcomeTables;
    }

    public static Map<ResourceLocation, FlavorPool> flavorPools() {
        return flavorPools;
    }

    public static Optional<OutcomeTable> outcomeTable(ResourceLocation id) {
        return Optional.ofNullable(outcomeTables.get(id));
    }

    public static Optional<EntityProfile> findEntityProfile(
            ResourceLocation entityTypeId, Predicate<ResourceLocation> tagTest) {
        return entityCache.computeIfAbsent(
                new LookupKey(entityTypeId, null),
                key -> resolve(entityProfiles.values(), entityTypeId, tagTest, null));
    }

    public static Optional<ItemProfile> findItemProfile(ResourceLocation itemId, Predicate<ResourceLocation> tagTest) {
        return findItemProfile(itemId, tagTest, null);
    }

    public static Optional<ItemProfile> findItemProfile(
            ResourceLocation itemId, Predicate<ResourceLocation> tagTest, AttackDelivery delivery) {
        return itemCache.computeIfAbsent(
                new LookupKey(itemId, delivery), key -> resolve(itemProfiles.values(), itemId, tagTest, delivery));
    }

    public static Optional<SpellProfile> findSpellProfile(
            ResourceLocation damageTypeId, Predicate<ResourceLocation> tagTest) {
        return spellCache.computeIfAbsent(
                new LookupKey(damageTypeId, null), key -> resolve(spellProfiles.values(), damageTypeId, tagTest, null));
    }

    public static Optional<FlavorPool> findFlavorPool(
            ResourceLocation itemId, Predicate<ResourceLocation> tagTest, AttackDelivery delivery) {
        return flavorCache.computeIfAbsent(
                new LookupKey(itemId, delivery), key -> resolve(flavorPools.values(), itemId, tagTest, delivery));
    }

    /**
     * Picks the winning profile for {@code id}: profiles constrained to other delivery methods are
     * out (a {@code null} delivery — no-context lookups like commands — ignores the constraint);
     * then highest {@code priority}; among equals the most specific matching entry wins (exact id
     * &gt; tag &gt; namespace wildcard), then a delivery-constrained profile beats an unconstrained
     * one (it is the more specific declaration); a remaining tie goes to the lexicographically
     * smaller file id so resolution is deterministic across reloads.
     */
    static <T extends Profile> Optional<T> resolve(
            Collection<T> profiles, ResourceLocation id, Predicate<ResourceLocation> tagTest, AttackDelivery delivery) {
        T best = null;
        int bestSpecificity = 0;
        for (T profile : profiles) {
            if (delivery != null
                    && !profile.deliveries().isEmpty()
                    && !profile.deliveries().contains(delivery)) {
                continue;
            }
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
        boolean candidateConstrained = !candidate.deliveries().isEmpty();
        if (candidateConstrained != !best.deliveries().isEmpty()) {
            return candidateConstrained;
        }
        return candidate.id().compareTo(best.id()) < 0;
    }
}
