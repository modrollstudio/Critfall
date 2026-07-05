package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.Rules;

/**
 * The shipped {@code rules.json} presets (docs/presets.md) must all parse cleanly and set the flags
 * they advertise. The {@code :common:test} working directory is the {@code common/} module, so the
 * presets sit one level up under {@code examples/presets/}.
 */
class PresetConfigTest {

    private static Rules parse(String preset, List<String> warnings) throws Exception {
        Path file = Path.of("..", "examples", "presets", preset);
        String text = Files.readString(file);
        return RulesLoader.parse(JsonParser.parseString(text).getAsJsonObject(), warnings::add);
    }

    @Test
    void temperedMatchesShippedDefaults() throws Exception {
        List<String> warnings = new ArrayList<>();
        Rules rules = parse("tempered.json", warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(Rules.DEFAULTS, rules, "tempered.json must equal the shipped defaults");
    }

    @Test
    void classicHasRawFumbles() throws Exception {
        List<String> warnings = new ArrayList<>();
        Rules rules = parse("classic.json", warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertFalse(rules.fumbles().confirmationRoll(), "classic disables the confirmation roll");
        assertEquals(0, rules.fumbles().cooldownTicks(), "classic runs with no fumble cooldown");
    }

    @Test
    void liteDisablesCritsAndFumbles() throws Exception {
        List<String> warnings = new ArrayList<>();
        Rules rules = parse("lite.json", warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertFalse(rules.crits().enabled(), "lite disables crits");
        assertFalse(rules.fumbles().enabled(), "lite disables fumbles");
        assertTrue(rules.damageDice(), "lite keeps damage dice on");
    }
}
