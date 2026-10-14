package studio.modroll.critfall.data;

import java.util.List;
import java.util.Map;

/**
 * The flavor keys one attack can pick from, by outcome: the matched pool's lines followed by the base
 * pools' lines, resolved for one item, delivery and dimension by {@link ProfileStore#findFlavorLines}.
 */
public record FlavorLines(Map<String, List<String>> lines) {

    public static final FlavorLines NONE = new FlavorLines(Map.of());

    /** The keys for {@code outcome}, or an empty list when there are none. */
    public List<String> lines(String outcome) {
        return lines.getOrDefault(outcome, List.of());
    }
}
