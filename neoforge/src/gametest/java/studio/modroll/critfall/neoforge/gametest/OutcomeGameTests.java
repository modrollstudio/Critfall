package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.OutcomeScenarios;

/** NeoForge registration shim: delegates to the shared {@link OutcomeScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class OutcomeGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void natTwentyAppliesStatusEffectFromDefaultTable(GameTestHelper helper) {
        OutcomeScenarios.natTwentyAppliesStatusEffectFromDefaultTable(helper);
    }

    @GameTest(template = TEMPLATE)
    public void natTwentyKnockbackShovesTheTarget(GameTestHelper helper) {
        OutcomeScenarios.natTwentyKnockbackShovesTheTarget(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleHitsNearestAllyWithTheAttacksDice(GameTestHelper helper) {
        OutcomeScenarios.fumbleHitsNearestAllyWithTheAttacksDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void barehandedMobFumbleCanHitItsPackmate(GameTestHelper helper) {
        OutcomeScenarios.barehandedMobFumbleCanHitItsPackmate(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hitNearestAllyIgnoresPlayersWhenPolicyForbidsIt(GameTestHelper helper) {
        OutcomeScenarios.hitNearestAllyIgnoresPlayersWhenPolicyForbidsIt(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hitNearestAllyRespectsTheServerPvpRule(GameTestHelper helper) {
        OutcomeScenarios.hitNearestAllyRespectsTheServerPvpRule(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hitNearestAllyRespectsTeamFriendlyFire(GameTestHelper helper) {
        OutcomeScenarios.hitNearestAllyRespectsTeamFriendlyFire(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleSelfDamageRollsTheConfiguredDice(GameTestHelper helper) {
        OutcomeScenarios.fumbleSelfDamageRollsTheConfiguredDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleDropsTheWeapon(GameTestHelper helper) {
        OutcomeScenarios.fumbleDropsTheWeapon(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleStumbleSlowsTheAttacker(GameTestHelper helper) {
        OutcomeScenarios.fumbleStumbleSlowsTheAttacker(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleReturnsDurabilityConsequence(GameTestHelper helper) {
        OutcomeScenarios.fumbleReturnsDurabilityConsequence(helper);
    }

    @GameTest(template = TEMPLATE)
    public void disabledConsequenceIsANoOpEvenWhenPicked(GameTestHelper helper) {
        OutcomeScenarios.disabledConsequenceIsANoOpEvenWhenPicked(helper);
    }

    @GameTest(template = TEMPLATE)
    public void appliesToMobsOnlySuppressesPlayerFumbles(GameTestHelper helper) {
        OutcomeScenarios.appliesToMobsOnlySuppressesPlayerFumbles(helper);
    }

    @GameTest(template = TEMPLATE)
    public void missMarginTableFiresOnABigMiss(GameTestHelper helper) {
        OutcomeScenarios.missMarginTableFiresOnABigMiss(helper);
    }
}
