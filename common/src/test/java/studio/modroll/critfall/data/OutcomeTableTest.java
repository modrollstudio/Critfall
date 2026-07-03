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

class OutcomeTableTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:table");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesNatOneTableWithWeightsAndParams() {
        List<String> warnings = new ArrayList<>();
        OutcomeTable table = OutcomeTable.parse(ID, json("""
                        {
                          "format_version": 1,
                          "trigger": "nat_1",
                          "effects": [
                            { "type": "critfall:damage_durability", "weight": 3 },
                            { "type": "critfall:hit_nearest_ally", "radius": 4 },
                            { "type": "critfall:nothing", "weight": 2 }
                          ]
                        }
                        """), warnings::add);
        assertEquals(new Trigger.Natural(1), table.trigger());
        assertEquals(3, table.effects().size());
        assertEquals(3, table.effects().get(0).weight());
        assertEquals(1, table.effects().get(1).weight(), "weight defaults to 1");
        assertEquals(6, table.totalWeight());
        assertEquals(4, table.effects().get(1).params().get("radius").getAsInt(), "extra keys become params");
        assertTrue(table.effects().get(0).params().isEmpty());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void parsesNatTwentyAndObjectTriggers() {
        assertEquals(
                new Trigger.Natural(20),
                OutcomeTable.parse(
                                ID,
                                json("{\"trigger\": \"nat_20\", \"effects\": [{\"type\": \"critfall:nothing\"}]}"),
                                w -> {})
                        .trigger());
        assertEquals(
                new Trigger.MissByAtLeast(5),
                OutcomeTable.parse(
                                ID,
                                json(
                                        "{\"trigger\": {\"type\": \"miss_by_at_least\", \"margin\": 5}, \"effects\": [{\"type\": \"critfall:nothing\"}]}"),
                                w -> {})
                        .trigger());
        assertEquals(
                new Trigger.RollRange(2, 5),
                OutcomeTable.parse(
                                ID,
                                json(
                                        "{\"trigger\": {\"type\": \"roll_range\", \"min\": 2, \"max\": 5}, \"effects\": [{\"type\": \"critfall:nothing\"}]}"),
                                w -> {})
                        .trigger());
    }

    @Test
    void badTriggersReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(
                        ID,
                        json("{\"trigger\": \"nat_7\", \"effects\": [{\"type\": \"critfall:nothing\"}]}"),
                        w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(ID, json("{\"effects\": [{\"type\": \"critfall:nothing\"}]}"), w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(
                        ID,
                        json(
                                "{\"trigger\": {\"type\": \"roll_range\", \"min\": 6, \"max\": 2}, \"effects\": [{\"type\": \"critfall:nothing\"}]}"),
                        w -> {}));
    }

    @Test
    void badEffectsReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(ID, json("{\"trigger\": \"nat_1\", \"effects\": []}"), w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(ID, json("{\"trigger\": \"nat_1\"}"), w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(
                        ID, json("{\"trigger\": \"nat_1\", \"effects\": [{\"weight\": 1}]}"), w -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> OutcomeTable.parse(
                        ID,
                        json(
                                "{\"trigger\": \"nat_1\", \"effects\": [{\"type\": \"critfall:nothing\", \"weight\": 0}]}"),
                        w -> {}));
    }
}
