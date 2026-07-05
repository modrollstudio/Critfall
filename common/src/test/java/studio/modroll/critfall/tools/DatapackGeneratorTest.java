package studio.modroll.critfall.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.tools.DatapackGenerator.EntityStat;
import studio.modroll.critfall.tools.DatapackGenerator.ItemStat;

class DatapackGeneratorTest {

    @Test
    void entityProfileUsesDerivedStatsAndPath() {
        EntityStat zombie = new EntityStat(ResourceLocation.parse("minecraft:zombie"), 2.0, 0.0, 3.0);
        Map<String, String> out = DatapackGenerator.generate(List.of(zombie), List.of());
        String path = "data/critfall_generated/critfall/entity_profile/minecraft_zombie.json";
        assertTrue(out.containsKey(path), out.keySet().toString());
        String json = out.get(path);
        assertTrue(json.contains("\"minecraft:zombie\""), json);
        assertTrue(json.contains("\"armor_class\": 11"), json); // 10 + floor(2/2) + floor(0/4)
        assertTrue(json.contains("\"attack_bonus\": 1"), json); // floor(3/2)
    }

    @Test
    void carriesOverwriteNoticeInReadmeNotInJson() {
        EntityStat zombie = new EntityStat(ResourceLocation.parse("minecraft:zombie"), 2.0, 0.0, 3.0);
        Map<String, String> out = DatapackGenerator.generate(List.of(zombie), List.of());
        assertTrue(out.getOrDefault("README.txt", "").contains("overwritten"), "README should warn about overwrite");
        // No per-file _comment key (it would trip the lenient parser's unknown-key warning).
        assertTrue(!out.get("data/critfall_generated/critfall/entity_profile/minecraft_zombie.json")
                .contains("_comment"));
    }

    @Test
    void itemProfileUsesDerivedDiceAndModifierFrom() {
        ItemStat sword = new ItemStat(ResourceLocation.parse("minecraft:iron_sword"), 6.0);
        Map<String, String> out = DatapackGenerator.generate(List.of(), List.of(sword));
        String path = "data/critfall_generated/critfall/item_profile/minecraft_iron_sword.json";
        String json = out.get(path);
        assertNotNull(json);
        assertTrue(json.contains("\"modifier_from\": \"attack_damage_attribute\""), json);
        assertTrue(json.contains("\"damage\""), json);
    }

    @Test
    void packMcmetaHasFormatAndWarning() {
        String meta = DatapackGenerator.packMcmeta();
        assertTrue(meta.contains("\"pack_format\": 48"), meta);
        assertTrue(meta.contains("overwritten"), meta);
    }

    @Test
    void pathSanitizesSlashesInModIds() {
        assertEquals("mod_deep_mob.json", DatapackGenerator.fileName(ResourceLocation.parse("mod:deep/mob")));
    }

    @Test
    void generatedEntityProfileParsesWithoutWarnings() {
        List<String> warnings = new ArrayList<>();
        EntityStat zombie = new EntityStat(ResourceLocation.parse("minecraft:zombie"), 2.0, 0.0, 3.0);
        String json = DatapackGenerator.generate(List.of(zombie), List.of())
                .get("data/critfall_generated/critfall/entity_profile/minecraft_zombie.json");
        EntityProfile.parse(
                ResourceLocation.parse("critfall_generated:minecraft_zombie"),
                JsonParser.parseString(json).getAsJsonObject(),
                warnings::add);
        assertTrue(warnings.isEmpty(), "unexpected warnings: " + warnings);
    }
}
