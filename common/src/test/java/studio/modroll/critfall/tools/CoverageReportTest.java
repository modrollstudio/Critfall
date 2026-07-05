package studio.modroll.critfall.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.tools.CoverageReport.EntityRow;
import studio.modroll.critfall.tools.CoverageReport.ItemRow;

class CoverageReportTest {

    @Test
    void entitiesCsvHasHeaderAndRows() {
        String csv = CoverageReport.entitiesCsv(List.of(
                new EntityRow("minecraft:zombie", "profile:critfall:zombie", 12, 1, "1d6", 20, "-", "-"),
                new EntityRow("mod:thing", "fallback", 10, 0, "1d4", 20, "-", "-")));
        String[] lines = csv.strip().split("\n");
        assertEquals("id,source,armor_class,attack_bonus,melee_dice,crit_range,ranged_dice,save_bonus", lines[0]);
        assertTrue(lines[1].startsWith("minecraft:zombie,profile:critfall:zombie,12,"), lines[1]);
        assertTrue(lines[2].startsWith("mod:thing,fallback,10,"), lines[2]);
    }

    @Test
    void csvEscapesCommasInValues() {
        String csv =
                CoverageReport.entitiesCsv(List.of(new EntityRow("mod:a,b", "fallback", 10, 0, "1d4", 20, "-", "-")));
        assertTrue(csv.contains("\"mod:a,b\""), csv);
    }

    @Test
    void entitiesJsonIsAnArrayOfObjects() {
        String json = CoverageReport.entitiesJson(
                List.of(new EntityRow("minecraft:zombie", "fallback", 12, 1, "1d6", 20, "-", "-")));
        assertTrue(json.trim().startsWith("["), json);
        assertTrue(json.contains("\"source\": \"fallback\""), json);
    }

    @Test
    void itemsCsvHasHeaderAndRows() {
        String csv = CoverageReport.itemsCsv(List.of(
                new ItemRow("minecraft:iron_sword", "profile:critfall:swords", "1d8", "attack_damage_attribute", 20)));
        String[] lines = csv.strip().split("\n");
        assertEquals("id,source,dice,modifier_from,crit_range", lines[0]);
        assertTrue(lines[1].startsWith("minecraft:iron_sword,profile:critfall:swords,1d8,"), lines[1]);
    }
}
