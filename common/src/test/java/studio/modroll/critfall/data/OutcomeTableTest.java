package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.dice.DiceExpression;

class OutcomeTableTest {

    private static final ResourceLocation ID = ResourceLocation.parse("test:table");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static OutcomeTable parse(String text) {
        return OutcomeTable.parse(ID, json(text), w -> {});
    }

    @Test
    void parsesNatOneTableWithWeightsAndTypedEffects() {
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
        assertEquals(
                new OutcomeEffect.DamageDurability(), table.effects().get(0).effect());
        assertEquals(
                new OutcomeEffect.HitNearestAlly(OptionalInt.of(4)),
                table.effects().get(1).effect(),
                "extra keys become effect parameters");
        assertEquals(new OutcomeEffect.Nothing(), table.effects().get(2).effect());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void parsesEveryEffectTypeWithParams() {
        OutcomeTable table = parse("""
                {
                  "trigger": "nat_20",
                  "effects": [
                    { "type": "critfall:self_damage", "dice": "1d4" },
                    { "type": "critfall:self_damage" },
                    { "type": "critfall:drop_weapon" },
                    { "type": "critfall:stumble", "slowness_ticks": 40 },
                    { "type": "critfall:stumble" },
                    { "type": "critfall:apply_effect", "effect": "minecraft:slowness", "ticks": 60 },
                    { "type": "critfall:apply_effect", "effect": "minecraft:weakness", "ticks": 100, "amplifier": 1 },
                    { "type": "critfall:knockback", "strength": 1.5 },
                    { "type": "critfall:knockback" },
                    { "type": "critfall:hit_nearest_ally" }
                  ]
                }
                """);
        List<OutcomeEffect> effects = table.effects().stream()
                .map(OutcomeTable.WeightedEffect::effect)
                .toList();
        assertEquals(
                List.of(
                        new OutcomeEffect.SelfDamage(Optional.of(DiceExpression.parse("1d4"))),
                        new OutcomeEffect.SelfDamage(Optional.empty()),
                        new OutcomeEffect.DropWeapon(),
                        new OutcomeEffect.Stumble(OptionalInt.of(40)),
                        new OutcomeEffect.Stumble(OptionalInt.empty()),
                        new OutcomeEffect.ApplyEffect(ResourceLocation.parse("minecraft:slowness"), 60, 0),
                        new OutcomeEffect.ApplyEffect(ResourceLocation.parse("minecraft:weakness"), 100, 1),
                        new OutcomeEffect.Knockback(1.5),
                        new OutcomeEffect.Knockback(1.0),
                        new OutcomeEffect.HitNearestAlly(OptionalInt.empty())),
                effects);
    }

    @Test
    void unknownEffectTypeWarnsAndIsDropped() {
        List<String> warnings = new ArrayList<>();
        OutcomeTable table = OutcomeTable.parse(ID, json("""
                        {
                          "trigger": "nat_1",
                          "effects": [
                            { "type": "critfall:damage_durability" },
                            { "type": "critfall:summon_dragon", "weight": 5 },
                            { "type": "othermod:custom_effect" }
                          ]
                        }
                        """), warnings::add);
        assertEquals(1, table.effects().size(), "unknown effect types are dropped, known ones stay");
        assertEquals(1, table.totalWeight(), "a dropped effect's weight does not count");
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("summon_dragon"), warnings.toString());
    }

    @Test
    void tableWithOnlyUnknownEffectsRejects() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parse("{\"trigger\": \"nat_1\", \"effects\": [{\"type\": \"critfall:summon_dragon\"}]}"));
    }

    @Test
    void parsesNatTwentyAndObjectTriggers() {
        assertEquals(
                new Trigger.Natural(20),
                parse("{\"trigger\": \"nat_20\", \"effects\": [{\"type\": \"critfall:nothing\"}]}")
                        .trigger());
        assertEquals(
                new Trigger.MissByAtLeast(5),
                parse(
                                "{\"trigger\": {\"type\": \"miss_by_at_least\", \"margin\": 5}, \"effects\": [{\"type\": \"critfall:nothing\"}]}")
                        .trigger());
        assertEquals(
                new Trigger.RollRange(2, 5),
                parse(
                                "{\"trigger\": {\"type\": \"roll_range\", \"min\": 2, \"max\": 5}, \"effects\": [{\"type\": \"critfall:nothing\"}]}")
                        .trigger());
    }

    @Test
    void badTriggersReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parse("{\"trigger\": \"nat_7\", \"effects\": [{\"type\": \"critfall:nothing\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"effects\": [{\"type\": \"critfall:nothing\"}]}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": {\"type\": \"roll_range\", \"min\": 6, \"max\": 2}, \"effects\": [{\"type\": \"critfall:nothing\"}]}"));
    }

    @Test
    void badEffectsReject() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trigger\": \"nat_1\", \"effects\": []}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"trigger\": \"nat_1\"}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> parse("{\"trigger\": \"nat_1\", \"effects\": [{\"weight\": 1}]}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": \"nat_1\", \"effects\": [{\"type\": \"critfall:nothing\", \"weight\": 0}]}"));
    }

    @Test
    void weightSumOverflowingIntRejects() {
        // Two individually-valid weights whose sum wraps negative would make the weighted pick
        // roll a die with negative sides mid-combat — the file must be rejected at load instead.
        assertThrows(IllegalArgumentException.class, () -> parse("""
                        {
                          "trigger": "nat_1",
                          "effects": [
                            { "type": "critfall:nothing", "weight": 2000000000 },
                            { "type": "critfall:damage_durability", "weight": 2000000000 }
                          ]
                        }
                        """));
    }

    @Test
    void largeButSafeWeightSumParses() {
        OutcomeTable table = parse("""
                {
                  "trigger": "nat_1",
                  "effects": [
                    { "type": "critfall:nothing", "weight": 1000000000 },
                    { "type": "critfall:damage_durability", "weight": 1000000000 }
                  ]
                }
                """);
        assertEquals(2000000000, table.totalWeight());
    }

    @Test
    void badKnownEffectParamsReject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": \"nat_1\", \"effects\": [{\"type\": \"critfall:hit_nearest_ally\", \"radius\": 0}]}"),
                "radius below 1 must reject");
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": \"nat_20\", \"effects\": [{\"type\": \"critfall:apply_effect\", \"ticks\": 60}]}"),
                "apply_effect without an effect id must reject");
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": \"nat_20\", \"effects\": [{\"type\": \"critfall:apply_effect\", \"effect\": \"minecraft:slowness\"}]}"),
                "apply_effect without ticks must reject");
        assertThrows(
                studio.modroll.critfall.dice.DiceParseException.class,
                () -> parse(
                        "{\"trigger\": \"nat_1\", \"effects\": [{\"type\": \"critfall:self_damage\", \"dice\": \"not dice\"}]}"),
                "bad dice must reject");
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": \"nat_20\", \"effects\": [{\"type\": \"critfall:knockback\", \"strength\": -1}]}"),
                "non-positive knockback must reject");
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(
                        "{\"trigger\": \"nat_1\", \"effects\": [{\"type\": \"critfall:stumble\", \"slowness_ticks\": 0}]}"),
                "zero-tick stumble must reject");
    }
}
