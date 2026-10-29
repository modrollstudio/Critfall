package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.Rules;

class RulesMigrationTest {

    /** The default file 0.2.17 wrote: format_version 1, armor reduction skipped, no divisors. */
    static final String OLD_DEFAULT_FILE = RulesLoader.DEFAULT_FILE
            .replace("\"format_version\": 2", "\"format_version\": 1")
            .replace(
                    "\"balance\": { \"global_damage_multiplier\": 1.0, \"disable_vanilla_armor_reduction\": false,"
                            + " \"armor_ac_divisor\": 4, \"toughness_ac_divisor\": 4, \"player_attack_bonus\": 5 },",
                    "\"balance\": { \"global_damage_multiplier\": 1.0, \"disable_vanilla_armor_reduction\": true },");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void oldFileMovesToTheNewArmorRule() {
        JsonObject root = json(OLD_DEFAULT_FILE);
        assertTrue(RulesMigration.migrate(root));
        assertEquals(2, root.get("format_version").getAsInt());
        JsonObject balance = root.getAsJsonObject("balance");
        assertFalse(balance.get("disable_vanilla_armor_reduction").getAsBoolean());
        assertEquals(4, balance.get("armor_ac_divisor").getAsInt());
        assertEquals(4, balance.get("toughness_ac_divisor").getAsInt());
        assertEquals(1.0, balance.get("global_damage_multiplier").getAsDouble(), "other balance values stay");
        assertEquals(Rules.DEFAULTS, RulesLoader.parse(root, w -> {}));
    }

    @Test
    void otherSettingsOfAnOldFileStay() {
        JsonObject root = json(OLD_DEFAULT_FILE.replace(
                "\"durability_break\": { \"enabled\": false", "\"durability_break\": { \"enabled\": true"));
        RulesMigration.migrate(root);
        assertTrue(RulesLoader.parse(root, w -> {}).fumbles().durabilityBreak());
    }

    @Test
    void fileWithoutAVersionIsOldToo() {
        JsonObject root = json("{\"balance\": {\"disable_vanilla_armor_reduction\": true}}");
        assertTrue(RulesMigration.migrate(root));
        assertFalse(RulesLoader.parse(root, w -> {}).balance().disableVanillaArmorReduction());
        JsonObject noBalance = json("{\"format_version\": 1}");
        assertTrue(RulesMigration.migrate(noBalance));
        assertEquals(
                4, noBalance.getAsJsonObject("balance").get("armor_ac_divisor").getAsInt());
    }

    @Test
    void divisorsAnOldFileAlreadyNamesStay() {
        JsonObject root = json("{\"balance\": {\"disable_vanilla_armor_reduction\": true, \"armor_ac_divisor\": 3}}");
        RulesMigration.migrate(root);
        assertEquals(3, RulesLoader.parse(root, w -> {}).balance().armorAcDivisor());
        assertFalse(RulesLoader.parse(root, w -> {}).balance().disableVanillaArmorReduction());
    }

    @Test
    void currentFileIsLeftAloneEvenWhenItSkipsArmorAgain() {
        JsonObject root = json("{\"format_version\": 2, \"balance\": {\"disable_vanilla_armor_reduction\": true}}");
        assertFalse(RulesMigration.migrate(root));
        assertTrue(RulesLoader.parse(root, w -> {}).balance().disableVanillaArmorReduction());
        assertFalse(RulesMigration.migrate(json(RulesLoader.DEFAULT_FILE)));
    }
}
