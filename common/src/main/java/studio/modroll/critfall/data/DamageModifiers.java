package studio.modroll.critfall.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * A defender's resistances/immunities/vulnerabilities, matched against damage type ids or tags.
 * Immunity wins outright; resist and vulnerable cancel each other when both match.
 */
public record DamageModifiers(List<MatchEntry> resist, List<MatchEntry> immune, List<MatchEntry> vulnerable) {

    public static final DamageModifiers NONE = new DamageModifiers(List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return resist.isEmpty() && immune.isEmpty() && vulnerable.isEmpty();
    }

    /** Multiplier for rolled damage: 0 immune, 0.5 resist, 2 vulnerable, 1 otherwise. */
    public float multiplier(ResourceLocation damageTypeId, Predicate<ResourceLocation> tagTest) {
        if (anyMatches(immune, damageTypeId, tagTest)) {
            return 0.0f;
        }
        boolean resisted = anyMatches(resist, damageTypeId, tagTest);
        boolean vulnerableTo = anyMatches(vulnerable, damageTypeId, tagTest);
        if (resisted == vulnerableTo) {
            return 1.0f;
        }
        return resisted ? 0.5f : 2.0f;
    }

    private static boolean anyMatches(
            List<MatchEntry> entries, ResourceLocation id, Predicate<ResourceLocation> tagTest) {
        for (MatchEntry entry : entries) {
            if (entry.matches(id, tagTest)) {
                return true;
            }
        }
        return false;
    }

    static DamageModifiers parse(LenientJson json) {
        return new DamageModifiers(
                parseEntries(json.stringList("resist")),
                parseEntries(json.stringList("immune")),
                parseEntries(json.stringList("vulnerable")));
    }

    private static List<MatchEntry> parseEntries(List<String> texts) {
        List<MatchEntry> entries = new ArrayList<>(texts.size());
        for (String text : texts) {
            entries.add(MatchEntry.parse(text));
        }
        return List.copyOf(entries);
    }
}
