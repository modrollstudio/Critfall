package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.data.FlavorPool;

class FlavorTextTest {

    private static final Path POOLS =
            Path.of("src", "main", "resources", "data", "critfall", "critfall", "flavor_pool");
    private static final Path LANG = Path.of("src", "main", "resources", "assets", "critfall", "lang", "en_us.json");

    /** Attacker and target pairs a line must read right for: players, mobs, and a custom name. */
    private static final List<String[]> NAMES =
            List.of(new String[] {"Alex", "Zombie"}, new String[] {"Skeleton", "Steve"}, new String[] {
                "Wolf", "Sir Reginald the Third"
            });

    private static String fill(String pattern, String attacker, String target) {
        return FlavorText.fill(pattern, Component.literal(attacker), Component.literal(target))
                .getString();
    }

    @Test
    void fillsBothPlaceholdersAnywhereAndRepeatedly() {
        assertEquals("Alex hits Zombie.", fill("{attacker} hits {target}.", "Alex", "Zombie"));
        assertEquals(
                "Zombie hears Alex, then Alex.", fill("{target} hears {attacker}, then {attacker}.", "Alex", "Zombie"));
        assertEquals("No names here.", fill("No names here.", "Alex", "Zombie"));
        assertEquals("", fill("", "Alex", "Zombie"));
    }

    @Test
    void leavesOtherBracesAlone() {
        assertEquals("{nope} Alex", fill("{nope} {attacker}", "Alex", "Zombie"));
    }

    @Test
    void everyShippedLineFillsItsPlaceholders() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (FlavorPool pool : shippedPools()) {
            for (String outcome : FlavorPool.OUTCOMES) {
                for (String key : pool.lines(outcome)) {
                    if (!lang.has(key)) {
                        problems.add(pool.id() + ": no en_us text for " + key);
                        continue;
                    }
                    String pattern = lang.get(key).getAsString();
                    for (String[] names : NAMES) {
                        String line = fill(pattern, names[0], names[1]);
                        if (line.contains("{") || line.contains("}") || line.contains("critfall.") || line.isBlank()) {
                            problems.add(key + " reads \"" + line + "\"");
                        }
                    }
                    checked++;
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(checked >= 120, "expected the shipped pools to hold at least 120 lines, found " + checked);
    }

    @Test
    void theBasePoolHasFortyLinesPerOutcome() throws IOException {
        FlavorPool base =
                shippedPools().stream().filter(FlavorPool::base).findFirst().orElseThrow();
        for (String outcome : FlavorPool.OUTCOMES) {
            assertTrue(
                    base.lines(outcome).size() >= 40,
                    outcome + " has " + base.lines(outcome).size() + " lines");
        }
        assertFalse(base.dimension().isPresent());
    }

    private static List<FlavorPool> shippedPools() throws IOException {
        List<FlavorPool> pools = new ArrayList<>();
        try (Stream<Path> files = Files.list(POOLS)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                List<String> warnings = new ArrayList<>();
                pools.add(FlavorPool.parse(
                        ResourceLocation.fromNamespaceAndPath("critfall", name),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject(),
                        warnings::add));
                assertTrue(warnings.isEmpty(), file + ": " + warnings);
            }
        }
        return pools;
    }
}
