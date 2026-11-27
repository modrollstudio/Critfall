package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.RolledScenarios;

/** Fabric registration shim: delegates to the shared {@link RolledScenarios} bodies. */
public class RolledGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void attackRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.attackRollsNowAndLandsLater(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardSaveRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.hazardSaveRollsNowAndLandsLater(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void healRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.healRollsNowAndLandsLater(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void damageRollsNowAndLandsLater(GameTestHelper helper) {
        RolledScenarios.damageRollsNowAndLandsLater(helper);
    }
}
