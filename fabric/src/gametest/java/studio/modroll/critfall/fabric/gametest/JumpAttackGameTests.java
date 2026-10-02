package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.JumpAttackScenarios;

/** Fabric registration shim: delegates to the shared {@link JumpAttackScenarios} bodies. */
public class JumpAttackGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void jumpHitRollsWithAdvantage(GameTestHelper helper) {
        JumpAttackScenarios.jumpHitRollsWithAdvantage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void groundedHitRollsNormally(GameTestHelper helper) {
        JumpAttackScenarios.groundedHitRollsNormally(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void jumpHitWithDisadvantageSourceRollsNormally(GameTestHelper helper) {
        JumpAttackScenarios.jumpHitWithDisadvantageSourceRollsNormally(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void jumpHitDropsVanillaCritMultiplier(GameTestHelper helper) {
        JumpAttackScenarios.jumpHitDropsVanillaCritMultiplier(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void jumpAttackOffMatchesPreviousRelease(GameTestHelper helper) {
        JumpAttackScenarios.jumpAttackOffMatchesPreviousRelease(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unrolledJumpHitKeepsVanillaCrit(GameTestHelper helper) {
        JumpAttackScenarios.unrolledJumpHitKeepsVanillaCrit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenAttacksIgnoreJumping(GameTestHelper helper) {
        JumpAttackScenarios.drivenAttacksIgnoreJumping(helper);
    }
}
