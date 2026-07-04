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
import studio.modroll.critfall.combat.Rules;

class SpellProfileTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:spell");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesFullSaveProfile() {
        List<String> warnings = new ArrayList<>();
        SpellProfile profile = SpellProfile.parse(ID, json("""
                        {
                          "format_version": 1,
                          "matches": ["#irons_spellbooks:fire_magic", "ars_nouveau:flare"],
                          "resolution": "save",
                          "damage": "6d6",
                          "save": { "dc": 15, "on_success": "half" },
                          "priority": 5
                        }
                        """), warnings::add);
        assertEquals(2, profile.matches().size());
        assertEquals(SpellProfile.Resolution.SAVE, profile.resolution());
        assertEquals("6d6", profile.damage().orElseThrow().toString());
        assertEquals(15, profile.saveDc().orElseThrow());
        assertEquals(Rules.SaveOutcome.HALF, profile.onSuccess().orElseThrow());
        assertEquals(5, profile.priority());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void parsesAttackRollProfileWithBonusAndCritRange() {
        SpellProfile profile = SpellProfile.parse(ID, json("""
                        {
                          "matches": ["irons_spellbooks:ice_magic"],
                          "resolution": "attack_roll",
                          "damage": "3d8",
                          "attack_bonus": 6,
                          "crit_range": 19
                        }
                        """), w -> {});
        assertEquals(SpellProfile.Resolution.ATTACK_ROLL, profile.resolution());
        assertEquals(6, profile.attackBonus().orElseThrow());
        assertEquals(19, profile.critRange().orElseThrow());
    }

    @Test
    void everythingButMatchesIsOptional() {
        SpellProfile profile = SpellProfile.parse(ID, json("{\"matches\": [\"#critfall:spell\"]}"), w -> {});
        assertEquals(SpellProfile.Resolution.ATTACK_ROLL, profile.resolution(), "attack roll is the default");
        assertTrue(profile.damage().isEmpty());
        assertTrue(profile.attackBonus().isEmpty());
        assertTrue(profile.critRange().isEmpty());
        assertTrue(profile.saveDc().isEmpty());
        assertTrue(profile.onSuccess().isEmpty());
        assertEquals(0, profile.priority());
    }

    @Test
    void missingMatchesRejectsFile() {
        assertThrows(IllegalArgumentException.class, () -> SpellProfile.parse(ID, json("{}"), w -> {}));
    }

    @Test
    void badResolutionRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpellProfile.parse(
                        ID, json("{\"matches\": [\"minecraft:magic\"], \"resolution\": \"contest\"}"), w -> {}));
    }

    @Test
    void badOnSuccessRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpellProfile.parse(
                        ID,
                        json("{\"matches\": [\"minecraft:magic\"], \"save\": {\"on_success\": \"reflect\"}}"),
                        w -> {}));
    }

    @Test
    void outOfRangeDcRejectsFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpellProfile.parse(
                        ID, json("{\"matches\": [\"minecraft:magic\"], \"save\": {\"dc\": 0}}"), w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpellProfile.parse(
                        ID, json("{\"matches\": [\"minecraft:magic\"], \"save\": {\"dc\": 31}}"), w -> {}));
    }

    @Test
    void unknownKeysWarnButDoNotReject() {
        List<String> warnings = new ArrayList<>();
        SpellProfile.parse(ID, json("{\"matches\": [\"minecraft:magic\"], \"school\": \"evocation\"}"), warnings::add);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("school"), warnings.toString());
    }
}
