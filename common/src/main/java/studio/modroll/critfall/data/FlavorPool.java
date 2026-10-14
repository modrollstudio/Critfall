package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.api.AttackDelivery;

/**
 * A pool of narrative flavor-line translation keys (PLAN §4.5), loaded from
 * {@code data/<ns>/critfall/flavor_pool/*.json} and matched against the attack's weapon item with
 * the same {@code matches}/{@code priority} resolution as the other profiles. {@code lines} groups
 * keys by outcome — only {@code crit}, {@code fumble}, and {@code kill} ever produce flavor.
 *
 * <p>A {@code base} pool has no {@code matches}: its lines join every attack's pick, unless the matched
 * pool sets {@code include_base} to false. A pool with a {@code dimension} is used only in that
 * dimension, where its lines replace the ones without a dimension (see {@link ProfileStore#findFlavorLines}).
 */
public record FlavorPool(
        ResourceLocation id,
        List<MatchEntry> matches,
        Set<AttackDelivery> deliveries,
        Map<String, List<String>> lines,
        int priority,
        boolean base,
        boolean includeBase,
        Optional<ResourceLocation> dimension)
        implements Profile {

    public static final int FORMAT_VERSION = 1;
    public static final String CRIT = "crit";
    public static final String FUMBLE = "fumble";
    public static final String KILL = "kill";
    public static final List<String> OUTCOMES = List.of(CRIT, FUMBLE, KILL);

    /** The keys listed for {@code outcome}, or an empty list when the pool has none. */
    public List<String> lines(String outcome) {
        return lines.getOrDefault(outcome, List.of());
    }

    public static FlavorPool parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "flavor_pool " + id, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        boolean base = j.getBool("base", false);
        List<String> matchTexts = j.stringList("matches");
        if (base && !matchTexts.isEmpty()) {
            throw new IllegalArgumentException("a base pool joins every attack, so it takes no 'matches'");
        }
        if (!base && matchTexts.isEmpty()) {
            throw new IllegalArgumentException("'matches' must list at least one item id, #tag, or namespace:*");
        }
        List<MatchEntry> matches = new ArrayList<>(matchTexts.size());
        for (String text : matchTexts) {
            matches.add(MatchEntry.parse(text));
        }

        Set<AttackDelivery> deliveries = Profile.parseDeliveries(j);
        if (base && !deliveries.isEmpty()) {
            throw new IllegalArgumentException("a base pool joins every attack, so it takes no 'delivery'");
        }
        Optional<ResourceLocation> dimension = j.optionalString("dimension").map(text -> {
            ResourceLocation parsed = ResourceLocation.tryParse(text);
            if (parsed == null) {
                throw new IllegalArgumentException("'dimension' must be a dimension id, was \"" + text + "\"");
            }
            return parsed;
        });
        boolean includeBase = j.getBool("include_base", true);

        LenientJson linesJson = j.object("lines");
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String outcome : OUTCOMES) {
            List<String> keys = linesJson.stringList(outcome);
            if (!keys.isEmpty()) {
                lines.put(outcome, List.copyOf(keys));
            }
        }
        // Warn (do not throw) about outcome keys we do not recognize — forward compatibility.
        linesJson.finish();

        int priority = j.getInt("priority", 0);
        j.finish();
        return new FlavorPool(
                id, List.copyOf(matches), deliveries, Map.copyOf(lines), priority, base, includeBase, dimension);
    }
}
