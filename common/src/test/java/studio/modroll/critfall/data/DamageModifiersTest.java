package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DamageModifiersTest {

    private static final ResourceLocation FIRE = ResourceLocation.parse("minecraft:in_fire");
    private static final Set<ResourceLocation> FIRE_TAGS = Set.of(ResourceLocation.parse("minecraft:is_fire"));

    private static DamageModifiers parse(String json) {
        return EntityProfile.parse(
                        ResourceLocation.parse("test:profile"),
                        JsonParser.parseString("{\"matches\": [\"minecraft:pig\"], \"damage_modifiers\": " + json + "}")
                                .getAsJsonObject(),
                        w -> {})
                .damageModifiers();
    }

    @Test
    void immuneZeroesDamageAndBeatsEverything() {
        DamageModifiers modifiers =
                parse("{\"immune\": [\"#minecraft:is_fire\"], \"vulnerable\": [\"minecraft:in_fire\"]}");
        assertEquals(0.0f, modifiers.multiplier(FIRE, FIRE_TAGS::contains));
    }

    @Test
    void resistHalves() {
        assertEquals(0.5f, parse("{\"resist\": [\"minecraft:in_fire\"]}").multiplier(FIRE, tag -> false));
    }

    @Test
    void vulnerableDoubles() {
        assertEquals(2.0f, parse("{\"vulnerable\": [\"#minecraft:is_fire\"]}").multiplier(FIRE, FIRE_TAGS::contains));
    }

    @Test
    void resistAndVulnerableCancel() {
        DamageModifiers modifiers =
                parse("{\"resist\": [\"minecraft:in_fire\"], \"vulnerable\": [\"#minecraft:is_fire\"]}");
        assertEquals(1.0f, modifiers.multiplier(FIRE, FIRE_TAGS::contains));
    }

    @Test
    void unrelatedDamageTypeIsUnmodified() {
        DamageModifiers modifiers = parse("{\"immune\": [\"#minecraft:is_fire\"], \"resist\": [\"minecraft:cactus\"]}");
        assertEquals(1.0f, modifiers.multiplier(ResourceLocation.parse("minecraft:mob_attack"), tag -> false));
    }
}
