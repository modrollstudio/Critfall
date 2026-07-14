package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.dice.DiceParseException;

class EntityProfileTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:profile");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesFullProfile() {
        List<String> warnings = new ArrayList<>();
        EntityProfile profile = EntityProfile.parse(ID, json("""
                        {
                          "format_version": 1,
                          "matches": ["minecraft:enderman"],
                          "armor_class": 14,
                          "attack_bonus": 4,
                          "save_bonus": 3,
                          "damage": { "melee": "2d6+3", "ranged": "1d8" },
                          "crit_range": 19,
                          "damage_modifiers": {
                            "resist": ["#critfall:physical"],
                            "immune": [],
                            "vulnerable": ["minecraft:fire"]
                          },
                          "fumble_table": "critfall:default_melee",
                          "crit_table": "critfall:default_crit",
                          "priority": 10
                        }
                        """), warnings::add);
        assertEquals(14, profile.armorClass().orElseThrow());
        assertEquals(4, profile.attackBonus().orElseThrow());
        assertEquals(3, profile.saveBonus().orElseThrow());
        assertEquals("2d6+3", profile.meleeDamage().orElseThrow().toString());
        assertEquals("1d8", profile.rangedDamage().orElseThrow().toString());
        assertEquals(19, profile.critRange().orElseThrow());
        assertEquals(10, profile.priority());
        assertEquals(1, profile.damageModifiers().resist().size());
        assertEquals(1, profile.damageModifiers().vulnerable().size());
        assertEquals(
                "critfall:default_melee", profile.fumbleTable().orElseThrow().toString());
        assertEquals("critfall:default_crit", profile.critTable().orElseThrow().toString());
        assertTrue(warnings.isEmpty(), "spec-shaped profile must parse without warnings: " + warnings);
    }

    @Test
    void everyStatIsOptional() {
        EntityProfile profile = EntityProfile.parse(ID, json("{\"matches\": [\"minecraft:pig\"]}"), w -> {});
        assertTrue(profile.armorClass().isEmpty());
        assertTrue(profile.attackBonus().isEmpty());
        assertTrue(profile.saveBonus().isEmpty());
        assertTrue(profile.meleeDamage().isEmpty());
        assertTrue(profile.rangedDamage().isEmpty());
        assertTrue(profile.critRange().isEmpty());
        assertTrue(profile.damageModifiers().isEmpty());
        assertTrue(profile.fumbleTable().isEmpty());
        assertTrue(profile.critTable().isEmpty());
        assertEquals(0, profile.priority());
    }

    @Test
    void singleMatchStringIsAccepted() {
        EntityProfile profile = EntityProfile.parse(ID, json("{\"matches\": \"minecraft:pig\"}"), w -> {});
        assertEquals(1, profile.matches().size());
    }

    @Test
    void missingMatchesRejectsFile() {
        assertThrows(IllegalArgumentException.class, () -> EntityProfile.parse(ID, json("{}"), w -> {}));
        assertThrows(IllegalArgumentException.class, () -> EntityProfile.parse(ID, json("{\"matches\": []}"), w -> {}));
    }

    @Test
    void badDiceRejectsFile() {
        assertThrows(
                DiceParseException.class,
                () -> EntityProfile.parse(
                        ID, json("{\"matches\": [\"minecraft:pig\"], \"damage\": {\"melee\": \"2x6\"}}"), w -> {}));
    }

    @Test
    void badCritRangeRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityProfile.parse(ID, json("{\"matches\": [\"minecraft:pig\"], \"crit_range\": 1}"), w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityProfile.parse(ID, json("{\"matches\": [\"minecraft:pig\"], \"crit_range\": 21}"), w -> {}));
    }

    @Test
    void badArmorClassRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EntityProfile.parse(ID, json("{\"matches\": [\"minecraft:pig\"], \"armor_class\": 0}"), w -> {}));
    }

    @Test
    void unknownKeysWarnButDoNotReject() {
        List<String> warnings = new ArrayList<>();
        EntityProfile profile = EntityProfile.parse(
                ID, json("{\"matches\": [\"minecraft:pig\"], \"armour_class\": 12}"), warnings::add);
        assertTrue(profile.armorClass().isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("armour_class"), warnings.toString());
    }

    @Test
    void unknownKeyInsideNestedObjectWarns() {
        List<String> warnings = new ArrayList<>();
        EntityProfile.parse(
                ID,
                json("{\"matches\": [\"minecraft:pig\"], \"damage\": {\"melee\": \"1d4\", \"spell\": \"1d6\"}}"),
                warnings::add);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("spell"), warnings.toString());
    }

    @Test
    void newerFormatVersionWarnsButParses() {
        List<String> warnings = new ArrayList<>();
        EntityProfile profile = EntityProfile.parse(
                ID,
                json("{\"format_version\": 99, \"matches\": [\"minecraft:pig\"], \"armor_class\": 12}"),
                warnings::add);
        assertEquals(12, profile.armorClass().orElseThrow());
        assertTrue(warnings.getFirst().contains("format_version"), warnings.toString());
    }
}
