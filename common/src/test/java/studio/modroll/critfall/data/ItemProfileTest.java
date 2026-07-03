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

class ItemProfileTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:swords");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesFullProfile() {
        List<String> warnings = new ArrayList<>();
        ItemProfile profile = ItemProfile.parse(ID, json("""
                        {
                          "format_version": 1,
                          "matches": ["#minecraft:swords"],
                          "damage": "1d8",
                          "modifier_from": "attack_damage_attribute",
                          "crit_range": 20,
                          "fumble_table": "critfall:default_melee",
                          "crit_table": "critfall:default_crit",
                          "properties": ["finesse"],
                          "priority": 5
                        }
                        """), warnings::add);
        assertEquals("1d8", profile.damage().orElseThrow().toString());
        assertEquals(ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE, profile.modifierFrom());
        assertEquals(20, profile.critRange().orElseThrow());
        assertEquals(
                "critfall:default_melee", profile.fumbleTable().orElseThrow().toString());
        assertEquals("critfall:default_crit", profile.critTable().orElseThrow().toString());
        assertTrue(profile.properties().contains("finesse"));
        assertEquals(5, profile.priority());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void modifierFromDefaultsToAttribute() {
        ItemProfile profile =
                ItemProfile.parse(ID, json("{\"matches\": [\"minecraft:stick\"], \"damage\": \"1d4\"}"), w -> {});
        assertEquals(ItemProfile.ModifierFrom.ATTACK_DAMAGE_ATTRIBUTE, profile.modifierFrom());
    }

    @Test
    void modifierFromNoneParses() {
        ItemProfile profile = ItemProfile.parse(
                ID, json("{\"matches\": [\"minecraft:stick\"], \"modifier_from\": \"none\"}"), w -> {});
        assertEquals(ItemProfile.ModifierFrom.NONE, profile.modifierFrom());
    }

    @Test
    void badModifierFromRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemProfile.parse(
                        ID, json("{\"matches\": [\"minecraft:stick\"], \"modifier_from\": \"level\"}"), w -> {}));
    }

    @Test
    void missingMatchesRejectsFile() {
        assertThrows(IllegalArgumentException.class, () -> ItemProfile.parse(ID, json("{}"), w -> {}));
    }

    @Test
    void badTableIdRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemProfile.parse(
                        ID, json("{\"matches\": [\"minecraft:stick\"], \"fumble_table\": \"NOT AN ID\"}"), w -> {}));
    }

    @Test
    void unknownKeysWarn() {
        List<String> warnings = new ArrayList<>();
        ItemProfile.parse(ID, json("{\"matches\": [\"minecraft:stick\"], \"dmg\": \"1d4\"}"), warnings::add);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("dmg"), warnings.toString());
    }
}
