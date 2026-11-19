package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.ArmorClassScenarios;

/** NeoForge registration shim: delegates to the shared {@link ArmorClassScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmorClassGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void armorClassSourcesCountOnAPlayersHit(GameTestHelper helper) {
        ArmorClassScenarios.armorClassSourcesCountOnAPlayersHit(helper);
    }

    @GameTest(template = TEMPLATE)
    public void armorClassSourcesCountInDrivenAndHazardAttacks(GameTestHelper helper) {
        ArmorClassScenarios.armorClassSourcesCountInDrivenAndHazardAttacks(helper);
    }

    @GameTest(template = TEMPLATE)
    public void raisedArmorClassTurnsAPlayersHitIntoAMiss(GameTestHelper helper) {
        ArmorClassScenarios.raisedArmorClassTurnsAPlayersHitIntoAMiss(helper);
    }

    @GameTest(template = TEMPLATE)
    public void raisedArmorClassStillLetsANatural20Hit(GameTestHelper helper) {
        ArmorClassScenarios.raisedArmorClassStillLetsANatural20Hit(helper);
    }
}
