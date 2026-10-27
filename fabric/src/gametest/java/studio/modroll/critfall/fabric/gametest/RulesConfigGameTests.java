package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.RulesConfigScenarios;

/** Fabric registration shim: delegates to the shared {@link RulesConfigScenarios} bodies. */
public class RulesConfigGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void savedRulesReachTheLiveGame(GameTestHelper helper) {
        RulesConfigScenarios.savedRulesReachTheLiveGame(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void invalidInputIsRejectedLikeTheLoader(GameTestHelper helper) {
        RulesConfigScenarios.invalidInputIsRejectedLikeTheLoader(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void weaponDamageIsOffOnlyInFreshConfigs(GameTestHelper helper) {
        RulesConfigScenarios.weaponDamageIsOffOnlyInFreshConfigs(helper);
    }
}
