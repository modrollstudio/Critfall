package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.ProjectileScenarios;

/** NeoForge registration shim: delegates to the shared {@link ProjectileScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProjectileGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void arrowMissCancelsAllDamage(GameTestHelper helper) {
        ProjectileScenarios.arrowMissCancelsAllDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void arrowHitRollsBowProfileDice(GameTestHelper helper) {
        ProjectileScenarios.arrowHitRollsBowProfileDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void ammoProfileAddsDiceOnTopOfTheBow(GameTestHelper helper) {
        ProjectileScenarios.ammoProfileAddsDiceOnTopOfTheBow(helper);
    }

    @GameTest(template = TEMPLATE)
    public void thrownTridentUsesTridentProfile(GameTestHelper helper) {
        ProjectileScenarios.thrownTridentUsesTridentProfile(helper);
    }

    @GameTest(template = TEMPLATE)
    public void zeroDamageProjectileStaysVanilla(GameTestHelper helper) {
        ProjectileScenarios.zeroDamageProjectileStaysVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void ownerlessArrowStaysVanilla(GameTestHelper helper) {
        ProjectileScenarios.ownerlessArrowStaysVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void projectileRollsDisabledRestoresVanilla(GameTestHelper helper) {
        ProjectileScenarios.projectileRollsDisabledRestoresVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbledShotWearsTheBowStillInHand(GameTestHelper helper) {
        ProjectileScenarios.fumbledShotWearsTheBowStillInHand(helper);
    }

    // Real shots fly for several ticks with the roller swapped: one per batch, so none overlap.
    @GameTest(template = TEMPLATE, batch = "critfall_real_shot_1")
    public void playerBowShotHitLandsOnTheTarget(GameTestHelper helper) {
        ProjectileScenarios.playerBowShotHitLandsOnTheTarget(helper);
    }

    @GameTest(template = TEMPLATE, batch = "critfall_real_shot_2")
    public void missedBowShotFliesPastTheTarget(GameTestHelper helper) {
        ProjectileScenarios.missedBowShotFliesPastTheTarget(helper);
    }

    @GameTest(template = TEMPLATE, batch = "critfall_real_shot_3")
    public void missedCrossbowShotFliesPastTheTarget(GameTestHelper helper) {
        ProjectileScenarios.missedCrossbowShotFliesPastTheTarget(helper);
    }

    @GameTest(template = TEMPLATE, batch = "critfall_real_shot_4")
    public void missedShotCanStillHitWhatIsBehindTheTarget(GameTestHelper helper) {
        ProjectileScenarios.missedShotCanStillHitWhatIsBehindTheTarget(helper);
    }
}
