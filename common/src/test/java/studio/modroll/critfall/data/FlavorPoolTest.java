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

class FlavorPoolTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:sword");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesLinesByOutcome() {
        List<String> warnings = new ArrayList<>();
        FlavorPool pool = FlavorPool.parse(ID, json("""
                {
                  "format_version": 1,
                  "matches": ["#minecraft:swords"],
                  "priority": 3,
                  "lines": {
                    "crit": ["a.b.crit.0", "a.b.crit.1"],
                    "fumble": ["a.b.fumble.0"]
                  }
                }
                """), warnings::add);
        assertEquals(List.of("a.b.crit.0", "a.b.crit.1"), pool.lines(FlavorPool.CRIT));
        assertEquals(List.of("a.b.fumble.0"), pool.lines(FlavorPool.FUMBLE));
        assertTrue(pool.lines(FlavorPool.KILL).isEmpty());
        assertEquals(3, pool.priority());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void parsesDeliveryList() {
        List<String> warnings = new ArrayList<>();
        FlavorPool pool = FlavorPool.parse(ID, json("""
                {
                  "format_version": 1,
                  "matches": ["minecraft:trident"],
                  "delivery": ["thrown", "projectile"],
                  "lines": {"crit": ["a.b.crit.0"]}
                }
                """), warnings::add);
        assertEquals(
                java.util.Set.of(
                        studio.modroll.critfall.api.AttackDelivery.THROWN,
                        studio.modroll.critfall.api.AttackDelivery.PROJECTILE),
                pool.deliveries());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void absentDeliveryMatchesAllDeliveries() {
        FlavorPool pool = FlavorPool.parse(
                ID, json("{\"matches\": [\"minecraft:trident\"], \"lines\": {\"crit\": [\"x\"]}}"), w -> {});
        assertTrue(pool.deliveries().isEmpty());
    }

    @Test
    void rejectsUnknownDeliveryValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FlavorPool.parse(
                        ID,
                        json("{\"matches\": [\"minecraft:trident\"], \"delivery\": [\"yeeted\"],"
                                + " \"lines\": {\"crit\": [\"x\"]}}"),
                        w -> {}));
    }

    @Test
    void rejectsEmptyMatches() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FlavorPool.parse(ID, json("{\"lines\": {\"crit\": [\"x\"]}}"), w -> {}));
    }

    @Test
    void unknownOutcomeKeyWarns() {
        List<String> warnings = new ArrayList<>();
        FlavorPool.parse(
                ID, json("{\"matches\": [\"minecraft:stick\"], \"lines\": {\"whiff\": [\"x\"]}}"), warnings::add);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("whiff")), warnings.toString());
    }
}
