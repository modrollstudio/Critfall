package studio.modroll.critfall.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.modroll.critfall.config.RulesSchemaTest.setting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.RulesLoader;

class RulesEditorTest {

    /** The 0.2.16 default file: the last one written with weapon damage on. */
    private static final String OLD_DEFAULT_FILE = RulesLoader.DEFAULT_FILE.replace(
            "\"durability_break\": { \"enabled\": false", "\"durability_break\": { \"enabled\": true");

    @TempDir
    Path dir;

    @Test
    void changesSurviveSaveAndReload() throws Exception {
        Path file = dir.resolve("critfall/rules.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, RulesLoader.DEFAULT_FILE);

        RulesEditor editor = RulesEditor.read(file);
        assertTrue(editor.set(RulesSchema.DURABILITY_BREAK, "true"));
        assertTrue(editor.set(RulesSchema.DURABILITY_MODE, "percent_loss"));
        assertTrue(editor.set(RulesSchema.DURABILITY_PERCENT, "40"));
        assertTrue(editor.set(setting("crits.enabled"), "false"));
        assertTrue(editor.set(setting("crits.rule"), "double_total"));
        assertTrue(editor.set(setting("fumbles.confirmation_roll.dc"), "15"));
        assertTrue(editor.set(setting("fumbles.self_damage.dice"), "2d6+1"));
        assertTrue(editor.set(setting("balance.global_damage_multiplier"), "1.5"));
        assertTrue(editor.set(setting("feedback.roll_visibility"), "attacker_only"));
        Map<String, String> shown = editor.values();
        Rules edited = editor.rules();
        editor.save(file);

        assertEquals(edited, RulesLoader.load(file), "the loader reads back what the screen saved");
        RulesEditor reopened = RulesEditor.read(file);
        assertEquals(shown, reopened.values(), "the screen shows what it saved");
        assertEquals(editor.write(), reopened.write());
        assertFalse(Files.exists(file.resolveSibling("rules.json.tmp")));

        Rules rules = RulesLoader.load(file);
        assertTrue(rules.fumbles().durabilityBreak());
        assertEquals(Rules.DurabilityMode.PERCENT_LOSS, rules.fumbles().durabilityMode());
        assertEquals(40, rules.fumbles().durabilityPercent());
        assertFalse(rules.crits().enabled());
        assertEquals(15, rules.fumbles().confirmationDc());
        assertEquals(1.5, rules.balance().globalDamageMultiplier());
    }

    @Test
    void savedFileParsesWithoutWarnings() throws Exception {
        Path file = dir.resolve("rules.json");
        RulesEditor editor = RulesEditor.read(file);
        editor.set(setting("fumbles.cooldown_ticks"), "0");
        editor.save(file);
        List<String> warnings = new ArrayList<>();
        RulesLoader.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject(), warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(
                Files.readString(file).contains("\"damage_dice\": { \"enabled\": true }"),
                "flat objects stay on one line");
    }

    @Test
    void allSettingsEditsACopyTheMainPageNeverSees() {
        RulesEditor main = RulesEditor.of(RulesLoader.DEFAULT_FILE);
        main.set(setting("crits.enabled"), "false");
        RulesEditor all = main.copy();
        assertEquals("false", all.value(setting("crits.enabled")), "the main page's changes carry over");

        all.set(setting("fumbles.confirmation_roll.dc"), "15");
        all.set(setting("crits.enabled"), "true");
        assertEquals(
                "10", main.value(setting("fumbles.confirmation_roll.dc")), "cancelling there leaves the main page");
        assertEquals("false", main.value(setting("crits.enabled")));
    }

    @Test
    void rejectedInputLeavesTheFileAlone() {
        RulesEditor editor = RulesEditor.of(RulesLoader.DEFAULT_FILE);
        String before = editor.write();
        assertFalse(editor.set(setting("fumbles.confirmation_roll.dc"), "21"));
        assertFalse(editor.set(setting("fumbles.hit_nearest_ally.radius"), "0"));
        assertFalse(editor.set(setting("spells.saves.default_dc"), "31"));
        assertFalse(editor.set(setting("fumbles.self_damage.dice"), "lots"));
        assertFalse(editor.set(setting("balance.global_damage_multiplier"), "0"));
        assertFalse(editor.set(setting("crits.rule"), "triple"));
        assertEquals(before, editor.write());
    }

    @Test
    void keysTheScreenDoesNotShowAreKept() {
        RulesEditor editor = RulesEditor.of("""
                {
                  "format_version": 1,
                  "advantage_sources": { "jump_attack": true, "sneak_attack": true },
                  "my_note": "keep me",
                  "crits": { "enabled": true }
                }
                """);
        editor.set(setting("crits.enabled"), "false");
        JsonObject written = JsonParser.parseString(editor.write()).getAsJsonObject();
        assertEquals("keep me", written.get("my_note").getAsString());
        assertTrue(
                written.getAsJsonObject("advantage_sources").get("sneak_attack").getAsBoolean());
        assertEquals(1, written.get("format_version").getAsInt());
        assertFalse(written.getAsJsonObject("crits").get("enabled").getAsBoolean());
        assertFalse(written.has("fumbles"), "a key the file leaves out is only written once changed");
    }

    @Test
    void invalidFileValuesShowAsTheLoadersFallback() {
        RulesEditor editor = RulesEditor.of(
                "{\"fumbles\": {\"confirmation_roll\": {\"dc\": 50}, \"enabled\": \"yes\"}, \"crits\": 3}");
        assertEquals("10", editor.value(setting("fumbles.confirmation_roll.dc")));
        assertEquals("true", editor.value(setting("fumbles.enabled")));
        assertEquals("true", editor.value(setting("crits.enabled")));
        assertTrue(editor.set(setting("crits.enabled"), "false"), "a non-object on the path is replaced");
        assertFalse(editor.rules().crits().enabled());
    }

    @Test
    void percentShorthandModeKeepsItsPercent() {
        RulesEditor editor = RulesEditor.of(
                "{\"fumbles\": {\"durability_break\": {\"enabled\": true, \"mode\": \"percent_loss:40\"}}}");
        assertEquals("percent_loss", editor.value(RulesSchema.DURABILITY_MODE));
        assertEquals("40", editor.value(RulesSchema.DURABILITY_PERCENT));
        assertEquals(40, editor.rules().fumbles().durabilityPercent());

        editor.set(RulesSchema.DURABILITY_MODE, "percent_loss");
        assertEquals("40", editor.value(RulesSchema.DURABILITY_PERCENT));
        assertEquals(40, editor.rules().fumbles().durabilityPercent(), "rewriting the mode keeps the shorthand's 40");
    }

    @Test
    void presetsApplyAndAreRecognised() {
        RulesEditor editor = RulesEditor.of(RulesLoader.DEFAULT_FILE);
        assertTrue(editor.matches(RulesPresets.text("tempered")), "the default file is the Tempered preset");
        assertFalse(editor.matches(RulesPresets.text("classic")));

        editor.applyPreset(RulesPresets.text("lite"));
        assertTrue(editor.matches(RulesPresets.text("lite")));
        assertFalse(editor.rules().crits().enabled());
        assertFalse(editor.rules().fumbles().enabled());

        editor.set(setting("crits.enabled"), "true");
        RulesPresets.IDS.forEach(id -> assertFalse(editor.matches(RulesPresets.text(id)), id));
    }

    @Test
    void presetsParseWithoutWarnings() {
        for (String id : RulesPresets.IDS) {
            List<String> warnings = new ArrayList<>();
            RulesLoader.parse(JsonParser.parseString(RulesPresets.text(id)).getAsJsonObject(), warnings::add);
            assertTrue(warnings.isEmpty(), id + ": " + warnings);
        }
        assertEquals(
                Rules.DEFAULTS,
                RulesLoader.parse(
                        JsonParser.parseString(RulesPresets.text("tempered")).getAsJsonObject(), w -> {}),
                "Tempered is the shipped default");
    }

    @Test
    void unreadableFileShowsDefaultsAndSaysSo() throws Exception {
        Path file = dir.resolve("rules.json");
        Files.writeString(file, "{ \"crits\": { \"enabled\": false ");
        RulesEditor editor = RulesEditor.read(file);
        assertTrue(editor.unreadable());
        assertEquals(Rules.DEFAULTS, editor.rules());
        assertFalse(RulesEditor.read(dir.resolve("missing.json")).unreadable());
    }

    @Test
    void weaponDamageIsOffOnlyInFreshConfigs() throws Exception {
        // A fresh config: the loader writes the default file, and the screen shows the same.
        Path fresh = dir.resolve("fresh/rules.json");
        assertFalse(RulesLoader.load(fresh).fumbles().durabilityBreak());
        assertTrue(Files.readString(fresh).contains("\"durability_break\": { \"enabled\": false"));
        assertEquals("false", RulesEditor.read(fresh).value(RulesSchema.DURABILITY_BREAK));
        assertEquals(
                "false", RulesEditor.read(dir.resolve("never-written.json")).value(RulesSchema.DURABILITY_BREAK));

        // A config written by 0.2.16 or earlier says "enabled": true, and keeps it, untouched.
        Path existing = dir.resolve("existing/rules.json");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, OLD_DEFAULT_FILE);
        assertNotEquals(RulesLoader.DEFAULT_FILE, OLD_DEFAULT_FILE);
        assertTrue(RulesLoader.load(existing).fumbles().durabilityBreak());
        assertEquals(OLD_DEFAULT_FILE, Files.readString(existing), "loading never rewrites an existing file");
        RulesEditor editor = RulesEditor.read(existing);
        assertEquals("true", editor.value(RulesSchema.DURABILITY_BREAK));

        // Saving other changes from the screen keeps it on, too.
        editor.set(setting("crits.enabled"), "false");
        editor.save(existing);
        assertTrue(RulesLoader.load(existing).fumbles().durabilityBreak());
    }
}
