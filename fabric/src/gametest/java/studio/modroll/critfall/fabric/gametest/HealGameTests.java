package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.HealScenarios;

/** Fabric registration shim: delegates to the shared {@link HealScenarios} bodies. */
public class HealGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void healRollsHealsAndShowsTheReadout(GameTestHelper helper) {
        HealScenarios.healRollsHealsAndShowsTheReadout(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void healStopsAtMaximumHealth(GameTestHelper helper) {
        HealScenarios.healStopsAtMaximumHealth(helper);
    }
}
