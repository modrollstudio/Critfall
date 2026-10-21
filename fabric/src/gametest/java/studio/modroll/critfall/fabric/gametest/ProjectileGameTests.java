package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.ProjectileScenarios;

/** Fabric registration shim: delegates to the shared {@link ProjectileScenarios} bodies (M8). */
public class ProjectileGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void arrowMissCancelsAllDamage(GameTestHelper helper) {
        ProjectileScenarios.arrowMissCancelsAllDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void arrowHitRollsBowProfileDice(GameTestHelper helper) {
        ProjectileScenarios.arrowHitRollsBowProfileDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ammoProfileAddsDiceOnTopOfTheBow(GameTestHelper helper) {
        ProjectileScenarios.ammoProfileAddsDiceOnTopOfTheBow(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thrownTridentUsesTridentProfile(GameTestHelper helper) {
        ProjectileScenarios.thrownTridentUsesTridentProfile(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void zeroDamageProjectileStaysVanilla(GameTestHelper helper) {
        ProjectileScenarios.zeroDamageProjectileStaysVanilla(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void ownerlessArrowStaysVanilla(GameTestHelper helper) {
        ProjectileScenarios.ownerlessArrowStaysVanilla(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void projectileRollsDisabledRestoresVanilla(GameTestHelper helper) {
        ProjectileScenarios.projectileRollsDisabledRestoresVanilla(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbledShotWearsTheBowStillInHand(GameTestHelper helper) {
        ProjectileScenarios.fumbledShotWearsTheBowStillInHand(helper);
    }

    // Real shots fly for several ticks with the roller swapped: one per batch, so none overlap.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "critfall_real_shot_1")
    public void playerBowShotHitLandsOnTheTarget(GameTestHelper helper) {
        ProjectileScenarios.playerBowShotHitLandsOnTheTarget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "critfall_real_shot_2")
    public void missedBowShotFliesPastTheTarget(GameTestHelper helper) {
        ProjectileScenarios.missedBowShotFliesPastTheTarget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "critfall_real_shot_3")
    public void missedCrossbowShotFliesPastTheTarget(GameTestHelper helper) {
        ProjectileScenarios.missedCrossbowShotFliesPastTheTarget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "critfall_real_shot_4")
    public void missedShotCanStillHitWhatIsBehindTheTarget(GameTestHelper helper) {
        ProjectileScenarios.missedShotCanStillHitWhatIsBehindTheTarget(helper);
    }
}
