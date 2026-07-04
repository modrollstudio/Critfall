package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.modroll.critfall.combat.Rules;

class RulesLoaderTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void shippedDefaultFileParsesToDefaultsWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        Rules rules = RulesLoader.parse(json(RulesLoader.DEFAULT_FILE), warnings::add);
        assertEquals(Rules.DEFAULTS, rules, "DEFAULT_FILE text and Rules.DEFAULTS must stay in sync");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void emptyJsonYieldsDefaults() {
        assertEquals(Rules.DEFAULTS, RulesLoader.parse(json("{}"), w -> {}));
    }

    @Test
    void flagsTurnOff() {
        Rules rules = RulesLoader.parse(json("""
                        {
                          "attack_rolls": { "enabled": false, "players": false },
                          "damage_dice": { "enabled": false },
                          "crits": { "enabled": false, "rule": "double_dice", "nat20_always_hits": false },
                          "fumbles": { "enabled": false, "confirmation_roll": { "enabled": false }, "cooldown_ticks": 0 },
                          "balance": { "global_damage_multiplier": 0.5, "disable_vanilla_armor_reduction": false },
                          "feedback": { "roll_visibility": "off" }
                        }
                        """), w -> {});
        assertFalse(rules.attackRolls().enabled());
        assertFalse(rules.attackRolls().players());
        assertTrue(rules.attackRolls().mobs(), "unset keys keep their defaults");
        assertFalse(rules.damageDice());
        assertFalse(rules.crits().enabled());
        assertEquals(Rules.CritRule.DOUBLE_DICE, rules.crits().rule());
        assertFalse(rules.crits().nat20AlwaysHits());
        assertFalse(rules.fumbles().enabled());
        assertFalse(rules.fumbles().confirmationRoll());
        assertEquals(0, rules.fumbles().cooldownTicks());
        assertEquals(0.5, rules.balance().globalDamageMultiplier());
        assertFalse(rules.balance().disableVanillaArmorReduction());
        assertEquals(Rules.FeedbackVisibility.OFF, rules.feedback());
    }

    @Test
    void percentLossModeAndShorthandParse() {
        Rules explicit = RulesLoader.parse(
                json("{\"fumbles\": {\"durability_break\": {\"mode\": \"percent_loss\", \"percent\": 10}}}"), w -> {});
        assertEquals(Rules.DurabilityMode.PERCENT_LOSS, explicit.fumbles().durabilityMode());
        assertEquals(10, explicit.fumbles().durabilityPercent());

        Rules shorthand = RulesLoader.parse(
                json("{\"fumbles\": {\"durability_break\": {\"mode\": \"percent_loss:40\"}}}"), w -> {});
        assertEquals(Rules.DurabilityMode.PERCENT_LOSS, shorthand.fumbles().durabilityMode());
        assertEquals(40, shorthand.fumbles().durabilityPercent());
    }

    @Test
    void badValuesWarnAndFallBack() {
        List<String> warnings = new ArrayList<>();
        Rules rules = RulesLoader.parse(json("""
                        {
                          "crits": { "rule": "triple_damage" },
                          "fumbles": {
                            "confirmation_roll": { "dc": 50 },
                            "durability_break": { "mode": "explode", "percent": 500 }
                          },
                          "balance": { "global_damage_multiplier": -2 },
                          "chaos_mode": true
                        }
                        """), warnings::add);
        assertEquals(Rules.CritRule.MAX_DICE, rules.crits().rule());
        assertEquals(10, rules.fumbles().confirmationDc());
        assertEquals(Rules.DurabilityMode.SET_TO_1, rules.fumbles().durabilityMode());
        assertEquals(25, rules.fumbles().durabilityPercent());
        assertEquals(1.0, rules.balance().globalDamageMultiplier());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("chaos_mode")), "unknown key must warn: " + warnings);
        assertEquals(6, warnings.size(), warnings.toString());
    }

    @Test
    void reservedFutureKeysAreRecognizedNotUnknown() {
        List<String> warnings = new ArrayList<>();
        RulesLoader.parse(json("""
                        {
                          "advantage_sources": { "attack_from_behind": true },
                          "feedback": { "sounds": true, "particles": true }
                        }
                        """), warnings::add);
        assertTrue(
                warnings.stream().allMatch(w -> w.contains("not implemented yet")),
                "spec'd future keys must be recognized, not 'unknown': " + warnings);
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    void fumbleConsequenceKeysParse() {
        Rules rules = RulesLoader.parse(json("""
                        {
                          "crits": { "apply_effect": { "enabled": false }, "knockback": { "enabled": false } },
                          "fumbles": {
                            "hit_nearest_ally": { "enabled": false, "radius": 8, "can_hit_players": false, "respect_pvp_rules": false },
                            "self_damage": { "enabled": true, "dice": "2d6" },
                            "drop_weapon": { "enabled": true },
                            "stumble": { "enabled": true, "slowness_ticks": 100 },
                            "applies_to": "players"
                          }
                        }
                        """), w -> {});
        assertFalse(rules.crits().applyEffect());
        assertFalse(rules.crits().knockback());
        assertEquals(
                new Rules.HitNearestAlly(false, 8, false, false),
                rules.fumbles().hitNearestAlly());
        assertTrue(rules.fumbles().selfDamage().enabled());
        assertEquals("2d6", rules.fumbles().selfDamage().dice().toString());
        assertTrue(rules.fumbles().dropWeapon());
        assertEquals(new Rules.Stumble(true, 100), rules.fumbles().stumble());
        assertEquals(Rules.AppliesTo.PLAYERS, rules.fumbles().appliesTo());
    }

    @Test
    void spellSaveKeysParse() {
        Rules rules = RulesLoader.parse(json("""
                        {
                          "spells": { "saves": { "enabled": false, "default_dc": 15, "on_success": "negate" } },
                          "fallbacks": { "unknown_spell": "vanilla_passthrough" }
                        }
                        """), w -> {});
        assertFalse(rules.spells().saves().enabled());
        assertEquals(15, rules.spells().saves().defaultDc());
        assertEquals(Rules.SaveOutcome.NEGATE, rules.spells().saves().onSuccess());
        assertEquals(Rules.FallbackMode.VANILLA_PASSTHROUGH, rules.fallbacks().unknownSpell());
        assertEquals(
                Rules.FallbackMode.DERIVE, rules.fallbacks().unknownEntity(), "unset fallbacks keep their defaults");
    }

    @Test
    void badSaveDcWarnsAndFallsBack() {
        List<String> warnings = new ArrayList<>();
        Rules rules = RulesLoader.parse(json("{\"spells\": {\"saves\": {\"default_dc\": 99}}}"), warnings::add);
        assertEquals(13, rules.spells().saves().defaultDc());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void badSelfDamageDiceWarnsAndFallsBack() {
        List<String> warnings = new ArrayList<>();
        Rules rules = RulesLoader.parse(
                json("{\"fumbles\": {\"self_damage\": {\"enabled\": true, \"dice\": \"garbage\"}}}"), warnings::add);
        assertEquals("1d4", rules.fumbles().selfDamage().dice().toString());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void missingFileIsCreatedWithDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("critfall").resolve("rules.json");
        Rules rules = RulesLoader.load(file);
        assertEquals(Rules.DEFAULTS, rules);
        assertTrue(Files.exists(file), "default config file must be written");
        assertEquals(Rules.DEFAULTS, RulesLoader.load(file), "written file must load back to the same rules");
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("rules.json");
        Files.writeString(file, "{ not json !!!");
        assertEquals(Rules.DEFAULTS, RulesLoader.load(file));
    }
}
