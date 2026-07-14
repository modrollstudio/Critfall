package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.OutcomeScenarios;

/** Fabric registration shim: delegates to the shared {@link OutcomeScenarios} bodies (M8). */
public class OutcomeGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void natTwentyAppliesStatusEffectFromDefaultTable(GameTestHelper helper) {
        OutcomeScenarios.natTwentyAppliesStatusEffectFromDefaultTable(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void natTwentyKnockbackShovesTheTarget(GameTestHelper helper) {
        OutcomeScenarios.natTwentyKnockbackShovesTheTarget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleHitsNearestAllyWithTheAttacksDice(GameTestHelper helper) {
        OutcomeScenarios.fumbleHitsNearestAllyWithTheAttacksDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void barehandedMobFumbleCanHitItsPackmate(GameTestHelper helper) {
        OutcomeScenarios.barehandedMobFumbleCanHitItsPackmate(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hitNearestAllyIgnoresPlayersWhenPolicyForbidsIt(GameTestHelper helper) {
        OutcomeScenarios.hitNearestAllyIgnoresPlayersWhenPolicyForbidsIt(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hitNearestAllyRespectsTheServerPvpRule(GameTestHelper helper) {
        OutcomeScenarios.hitNearestAllyRespectsTheServerPvpRule(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hitNearestAllyRespectsTeamFriendlyFire(GameTestHelper helper) {
        OutcomeScenarios.hitNearestAllyRespectsTeamFriendlyFire(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleSelfDamageRollsTheConfiguredDice(GameTestHelper helper) {
        OutcomeScenarios.fumbleSelfDamageRollsTheConfiguredDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleDropsTheWeapon(GameTestHelper helper) {
        OutcomeScenarios.fumbleDropsTheWeapon(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleStumbleSlowsTheAttacker(GameTestHelper helper) {
        OutcomeScenarios.fumbleStumbleSlowsTheAttacker(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleReturnsDurabilityConsequence(GameTestHelper helper) {
        OutcomeScenarios.fumbleReturnsDurabilityConsequence(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void disabledConsequenceIsANoOpEvenWhenPicked(GameTestHelper helper) {
        OutcomeScenarios.disabledConsequenceIsANoOpEvenWhenPicked(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void appliesToMobsOnlySuppressesPlayerFumbles(GameTestHelper helper) {
        OutcomeScenarios.appliesToMobsOnlySuppressesPlayerFumbles(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void missMarginTableFiresOnABigMiss(GameTestHelper helper) {
        OutcomeScenarios.missMarginTableFiresOnABigMiss(helper);
    }
}
