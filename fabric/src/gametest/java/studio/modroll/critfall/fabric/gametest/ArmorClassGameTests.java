package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.ArmorClassScenarios;

/** Fabric registration shim: delegates to the shared {@link ArmorClassScenarios} bodies. */
public class ArmorClassGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void armorClassSourcesCountOnAPlayersHit(GameTestHelper helper) {
        ArmorClassScenarios.armorClassSourcesCountOnAPlayersHit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void armorClassSourcesCountInDrivenAndHazardAttacks(GameTestHelper helper) {
        ArmorClassScenarios.armorClassSourcesCountInDrivenAndHazardAttacks(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void raisedArmorClassTurnsAPlayersHitIntoAMiss(GameTestHelper helper) {
        ArmorClassScenarios.raisedArmorClassTurnsAPlayersHitIntoAMiss(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void raisedArmorClassStillLetsANatural20Hit(GameTestHelper helper) {
        ArmorClassScenarios.raisedArmorClassStillLetsANatural20Hit(helper);
    }
}
