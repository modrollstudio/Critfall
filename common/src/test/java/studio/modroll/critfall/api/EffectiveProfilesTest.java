package studio.modroll.critfall.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.data.EntityProfile;

class EffectiveProfilesTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void entityFallsBackToDerivationWhenAbsent() {
        // No profile: AC derives from armor 6 + toughness 0; attack bonus derives from attack damage 5.
        EffectiveEntityProfile eff = EffectiveEntityProfile.of(Optional.empty(), 6, 0, 5);
        assertEquals(studio.modroll.critfall.combat.Derivation.armorClass(6, 0), eff.armorClass());
        assertEquals(studio.modroll.critfall.combat.Derivation.attackBonus(5), eff.attackBonus());
        assertEquals(20, eff.critRange());
        assertTrue(eff.meleeDamage().isEmpty());
    }

    @Test
    void entityProfileValuesWin() {
        EntityProfile profile = EntityProfile.parse(
                net.minecraft.resources.ResourceLocation.parse("test:e"),
                com.google.gson.JsonParser.parseString(
                                "{\"matches\":[\"minecraft:zombie\"],\"armor_class\":15,\"attack_bonus\":4,"
                                        + "\"save_bonus\":2,\"damage\":{\"melee\":\"2d6+1\"},\"crit_range\":19}")
                        .getAsJsonObject(),
                w -> {});
        EffectiveEntityProfile eff = EffectiveEntityProfile.of(Optional.of(profile), 6, 0, 5);
        assertEquals(15, eff.armorClass());
        assertEquals(4, eff.attackBonus());
        assertEquals(2, eff.saveBonus());
        assertEquals(19, eff.critRange());
        assertEquals("2d6+1", eff.meleeDamage().orElseThrow().toString());
    }
}
