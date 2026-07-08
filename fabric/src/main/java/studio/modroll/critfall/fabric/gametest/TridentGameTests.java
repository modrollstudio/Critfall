package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.TridentScenarios;

/** Fabric registration shim: delegates to the shared {@link TridentScenarios} bodies (issue #3). */
public class TridentGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void meleeTridentStabUsesMeleeProfile(GameTestHelper helper) {
        TridentScenarios.meleeTridentStabUsesMeleeProfile(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thrownTridentUsesThrownProfile(GameTestHelper helper) {
        TridentScenarios.thrownTridentUsesThrownProfile(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void meleeTridentCritPicksMeleeFlavorPool(GameTestHelper helper) {
        TridentScenarios.meleeTridentCritPicksMeleeFlavorPool(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thrownTridentCritPicksThrownFlavorPool(GameTestHelper helper) {
        TridentScenarios.thrownTridentCritPicksThrownFlavorPool(helper);
    }
}
