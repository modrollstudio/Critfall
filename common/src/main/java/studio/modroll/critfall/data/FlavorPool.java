package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * A pool of narrative flavor-line translation keys (PLAN §4.5), loaded from
 * {@code data/<ns>/critfall/flavor_pool/*.json} and matched against the attack's weapon item with
 * the same {@code matches}/{@code priority} resolution as the other profiles. {@code lines} groups
 * keys by outcome — only {@code crit}, {@code fumble}, and {@code kill} ever produce flavor.
 */
public record FlavorPool(ResourceLocation id, List<MatchEntry> matches, Map<String, List<String>> lines, int priority)
        implements Profile {

    public static final int FORMAT_VERSION = 1;
    public static final String CRIT = "crit";
    public static final String FUMBLE = "fumble";
    public static final String KILL = "kill";
    private static final Set<String> KNOWN_OUTCOMES = Set.of(CRIT, FUMBLE, KILL);

    /** The keys listed for {@code outcome}, or an empty list when the pool has none. */
    public List<String> lines(String outcome) {
        return lines.getOrDefault(outcome, List.of());
    }

    public static FlavorPool parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "flavor_pool " + id, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        List<String> matchTexts = j.stringList("matches");
        if (matchTexts.isEmpty()) {
            throw new IllegalArgumentException("'matches' must list at least one item id, #tag, or namespace:*");
        }
        List<MatchEntry> matches = new ArrayList<>(matchTexts.size());
        for (String text : matchTexts) {
            matches.add(MatchEntry.parse(text));
        }

        LenientJson linesJson = j.object("lines");
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String outcome : KNOWN_OUTCOMES) {
            List<String> keys = linesJson.stringList(outcome);
            if (!keys.isEmpty()) {
                lines.put(outcome, List.copyOf(keys));
            }
        }
        // Warn (do not throw) about outcome keys we do not recognize — forward compatibility.
        linesJson.finish();

        int priority = j.getInt("priority", 0);
        j.finish();
        return new FlavorPool(id, List.copyOf(matches), Map.copyOf(lines), priority);
    }
}
