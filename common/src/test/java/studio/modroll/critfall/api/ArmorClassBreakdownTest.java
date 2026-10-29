package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.data.EntityProfile;

class ArmorClassBreakdownTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void derivedArmorClassAddsUpFromItsParts() {
        // Full netherite, with the shipped divisors 4 and 4: 10 + 5 + 3.
        ArmorClassBreakdown netherite = ArmorClassBreakdown.of(Optional.empty(), 20, 12);
        assertTrue(netherite.derived());
        assertEquals(10, netherite.base());
        assertEquals(5, netherite.armorPart());
        assertEquals(3, netherite.toughnessPart());
        assertEquals(18, netherite.total());
        assertEquals(EffectiveEntityProfile.of(Optional.empty(), 20, 12, 0).armorClass(), netherite.total());

        ArmorClassBreakdown iron = ArmorClassBreakdown.of(Optional.empty(), 15, 0);
        assertEquals(3, iron.armorPart());
        assertEquals(0, iron.toughnessPart());
        assertEquals(13, iron.total());
    }

    @Test
    void profileArmorClassHasNoArmorParts() {
        EntityProfile profile = EntityProfile.parse(
                net.minecraft.resources.ResourceLocation.parse("test:e"),
                com.google.gson.JsonParser.parseString("{\"matches\":[\"minecraft:zombie\"],\"armor_class\":15}")
                        .getAsJsonObject(),
                w -> {});
        ArmorClassBreakdown set = ArmorClassBreakdown.of(Optional.of(profile), 20, 12);
        assertFalse(set.derived());
        assertEquals(0, set.armorPart());
        assertEquals(0, set.toughnessPart());
        assertEquals(15, set.base());
        assertEquals(15, set.total());
    }
}
