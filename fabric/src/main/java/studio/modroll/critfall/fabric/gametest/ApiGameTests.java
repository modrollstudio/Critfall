package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.ApiScenarios;

/** Fabric registration shim: delegates to the shared {@link ApiScenarios} bodies (M8). */
public class ApiGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenHitAppliesRolledDamageOnce(GameTestHelper helper) {
        ApiScenarios.drivenHitAppliesRolledDamageOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenMissAppliesNoDamage(GameTestHelper helper) {
        ApiScenarios.drivenMissAppliesNoDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void suppressedTargetIgnoredByAutoPipeline(GameTestHelper helper) {
        ApiScenarios.suppressedTargetIgnoredByAutoPipeline(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void postAttackListenerCanZeroOutDamage(GameTestHelper helper) {
        ApiScenarios.postAttackListenerCanZeroOutDamage(helper);
    }
}
