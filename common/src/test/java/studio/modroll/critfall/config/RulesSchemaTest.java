package studio.modroll.critfall.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.config.RulesSchema.Setting;
import studio.modroll.critfall.data.RulesLoader;

/** Holds the screen's schema in step with {@link RulesLoader}, key by key. */
class RulesSchemaTest {

    @Test
    void schemaCoversEveryKeyOfTheDefaultFile() {
        Set<String> fileKeys = new TreeSet<>();
        leaves(JsonParser.parseString(RulesLoader.DEFAULT_FILE).getAsJsonObject(), "", fileKeys);
        fileKeys.remove("format_version");
        Set<String> schemaKeys =
                RulesSchema.SETTINGS.stream().map(Setting::key).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(fileKeys, schemaKeys);
    }

    @Test
    void mainPageShowsTheFewSwitchesMostPlayersWant() {
        assertEquals(
                List.of(
                        "attack_rolls.enabled",
                        "damage_dice.enabled",
                        "crits.enabled",
                        "fumbles.enabled",
                        "fumbles.durability_break.enabled",
                        "fumbles.hit_nearest_ally.enabled",
                        "advantage_sources.jump_attack",
                        "feedback.roll_visibility",
                        "feedback.flavor.enabled"),
                RulesSchema.MAIN.stream().map(Setting::key).toList());
        for (Setting setting : RulesSchema.MAIN) {
            assertTrue(RulesSchema.SETTINGS.contains(setting), setting.key() + " is a rules.json key");
            assertTrue(
                    setting instanceof RulesSchema.Toggle || setting instanceof RulesSchema.Choice,
                    setting.key() + " is a switch or a choice, never a text field");
        }
    }

    @Test
    void schemaDefaultsAreTheLoaderDefaults() {
        JsonObject explicit = new JsonObject();
        RulesEditor editor = RulesEditor.of("{}");
        for (Setting setting : RulesSchema.SETTINGS) {
            assertEquals(setting.defaultValue(), editor.value(setting), setting.key());
            put(explicit, setting, setting.toJson(setting.defaultValue()));
        }
        List<String> warnings = new ArrayList<>();
        assertEquals(Rules.DEFAULTS, RulesLoader.parse(explicit, warnings::add));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void wholeNumbersAreRejectedExactlyWhereTheLoaderRejectsThem() {
        for (Setting setting : RulesSchema.SETTINGS) {
            if (setting instanceof RulesSchema.WholeNumber number) {
                List<Long> candidates = new ArrayList<>(List.of(
                        (long) number.min() - 1, (long) number.min(), (long) number.min() + 1, -5L, 0L, 7L, 1000L));
                if (!number.unbounded()) {
                    candidates.addAll(List.of((long) number.max() - 1, (long) number.max(), (long) number.max() + 1));
                }
                for (long candidate : candidates) {
                    assertParity(setting, Long.toString(candidate), new JsonPrimitive(candidate));
                }
            }
        }
    }

    @Test
    void choicesAreRejectedExactlyWhereTheLoaderRejectsThem() {
        for (Setting setting : RulesSchema.SETTINGS) {
            if (setting instanceof RulesSchema.Choice choice) {
                for (String candidate : choice.choices()) {
                    assertParity(setting, candidate, new JsonPrimitive(candidate));
                }
                assertParity(setting, "bogus", new JsonPrimitive("bogus"));
                assertParity(setting, "", new JsonPrimitive(""));
            }
        }
    }

    @Test
    void togglesDiceAndDecimalsAreRejectedExactlyWhereTheLoaderRejectsThem() {
        for (Setting setting : RulesSchema.SETTINGS) {
            switch (setting) {
                case RulesSchema.Toggle toggle -> {
                    assertParity(toggle, "true", new JsonPrimitive(true));
                    assertParity(toggle, "false", new JsonPrimitive(false));
                    assertParity(toggle, "yes", new JsonPrimitive("yes"));
                }
                case RulesSchema.Dice dice -> {
                    for (String candidate : List.of("1d4", "2d6+1", "3", "bogus", "", "d", "1d0")) {
                        assertParity(dice, candidate, new JsonPrimitive(candidate));
                    }
                }
                case RulesSchema.Decimal decimal -> {
                    for (String candidate : List.of("1.5", "1", "0.01", "0", "-2", "250")) {
                        assertParity(decimal, candidate, new JsonPrimitive(Double.parseDouble(candidate)));
                    }
                }
                default -> {}
            }
        }
    }

    @Test
    void fileValuesTheLoaderReadsInAnyCaseShowAsItsChoice() {
        RulesEditor editor =
                RulesEditor.of("{\"crits\": {\"rule\": \"DOUBLE_DICE\"}, \"fumbles\": {\"applies_to\": \"Players\"}}");
        assertEquals("double_dice", editor.value(setting("crits.rule")));
        assertEquals("players", editor.value(setting("fumbles.applies_to")));
        assertEquals(editor.rules(), RulesLoader.parse(materialized(editor), warning -> {}));
    }

    @Test
    void textTheScreenCannotTakeIsRejected() {
        Setting dc = setting("fumbles.confirmation_roll.dc");
        for (String text : List.of("", "abc", "1.5", "99999999999999999999", "1e3")) {
            assertFalse(dc.accept(text).isPresent(), text);
        }
        Setting multiplier = setting("balance.global_damage_multiplier");
        for (String text : List.of("", "NaN", "Infinity", "1e999", "abc")) {
            assertFalse(multiplier.accept(text).isPresent(), text);
        }
    }

    @Test
    void everyLabelTooltipSectionAndChoiceIsInTheLangFile() throws Exception {
        JsonObject lang;
        try (var reader = new InputStreamReader(
                RulesSchemaTest.class.getResourceAsStream("/assets/critfall/lang/en_us.json"),
                StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, List<Setting>> section : RulesSchema.bySection().entrySet()) {
            keys.add("critfall.config.section." + section.getKey());
            for (Setting setting : section.getValue()) {
                keys.add("critfall.config.rules." + setting.key());
                keys.add("critfall.config.rules." + setting.key() + ".tooltip");
                if (setting instanceof RulesSchema.Choice choice) {
                    choice.choices().forEach(value -> keys.add("critfall.config.choice." + value));
                }
            }
        }
        RulesPresets.IDS.forEach(id -> keys.add("critfall.config.preset." + id));
        keys.add("critfall.config.all_settings");
        keys.add("critfall.config.all_settings.tooltip");
        keys.add("critfall.config.preset.custom");
        for (String key : keys) {
            assertTrue(lang.has(key), "en_us.json is missing " + key);
            assertFalse(lang.get(key).getAsString().contains("\n"), key + " must be one line");
        }
    }

    /**
     * The screen takes {@code input} exactly when the loader reads {@code element} without a
     * warning, and then both arrive at the same rules.
     */
    private static void assertParity(Setting setting, String input, JsonElement element) {
        JsonObject file = new JsonObject();
        put(file, setting, element);
        List<String> warnings = new ArrayList<>();
        Rules loaded = RulesLoader.parse(file, warnings::add);
        boolean loaderAccepts = warnings.isEmpty();

        RulesEditor editor = RulesEditor.of("{}");
        String before = editor.write();
        boolean screenAccepts = editor.set(setting, input);
        assertEquals(
                loaderAccepts, screenAccepts, setting.key() + " = " + input + " (loader warnings: " + warnings + ")");
        if (screenAccepts) {
            assertEquals(loaded, editor.rules(), setting.key() + " = " + input);
        } else {
            assertEquals(before, editor.write(), "a rejected value must leave the file as it was");
        }
        // Read from the file, the value shown is the one the loader uses, valid or not.
        RulesEditor fromFile = RulesEditor.of(file.toString());
        assertEquals(loaded, RulesLoader.parse(materialized(fromFile), warning -> {}), setting.key() + " = " + input);
    }

    /** Every value the editor shows, written out explicitly. */
    static JsonObject materialized(RulesEditor editor) {
        JsonObject out = new JsonObject();
        for (Setting setting : RulesSchema.SETTINGS) {
            put(out, setting, setting.toJson(editor.value(setting)));
        }
        return out;
    }

    static Setting setting(String key) {
        return RulesSchema.setting(key);
    }

    private static void put(JsonObject root, Setting setting, JsonElement value) {
        List<String> path = setting.path();
        JsonObject parent = root;
        for (String key : path.subList(0, path.size() - 1)) {
            if (!parent.has(key)) {
                parent.add(key, new JsonObject());
            }
            parent = parent.getAsJsonObject(key);
        }
        parent.add(path.getLast(), value);
    }

    private static void leaves(JsonObject object, String prefix, Set<String> out) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue().isJsonObject()) {
                leaves(entry.getValue().getAsJsonObject(), key + ".", out);
            } else {
                out.add(key);
            }
        }
    }
}
