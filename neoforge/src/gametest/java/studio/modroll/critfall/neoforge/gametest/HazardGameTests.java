package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.HazardScenarios;

/** NeoForge registration shim: delegates to the shared {@link HazardScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class HazardGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void failedSaveTakesTheRolledDamage(GameTestHelper helper) {
        HazardScenarios.failedSaveTakesTheRolledDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void successfulSaveTakesHalf(GameTestHelper helper) {
        HazardScenarios.successfulSaveTakesHalf(helper);
    }

    @GameTest(template = TEMPLATE)
    public void successfulSaveCanTakeNone(GameTestHelper helper) {
        HazardScenarios.successfulSaveCanTakeNone(helper);
    }

    @GameTest(template = TEMPLATE)
    public void checksStyleProviderModifiesTheSave(GameTestHelper helper) {
        HazardScenarios.checksStyleProviderModifiesTheSave(helper);
    }

    @GameTest(template = TEMPLATE)
    public void saveWithDisadvantageKeepsTheLowerDie(GameTestHelper helper) {
        HazardScenarios.saveWithDisadvantageKeepsTheLowerDie(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardAttackHitAppliesTheDice(GameTestHelper helper) {
        HazardScenarios.hazardAttackHitAppliesTheDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardAttackNaturalOneJustMisses(GameTestHelper helper) {
        HazardScenarios.hazardAttackNaturalOneJustMisses(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardAttackCritUsesTheCritRule(GameTestHelper helper) {
        HazardScenarios.hazardAttackCritUsesTheCritRule(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardAttackWithAdvantageKeepsTheHigherDie(GameTestHelper helper) {
        HazardScenarios.hazardAttackWithAdvantageKeepsTheHigherDie(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardAttackUsesTheProvidedArmorClass(GameTestHelper helper) {
        HazardScenarios.hazardAttackUsesTheProvidedArmorClass(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardDamageRespectsImmunity(GameTestHelper helper) {
        HazardScenarios.hazardDamageRespectsImmunity(helper);
    }

    @GameTest(template = TEMPLATE)
    public void listenerGrantsAdvantageOnHazardSave(GameTestHelper helper) {
        HazardScenarios.listenerGrantsAdvantageOnHazardSave(helper);
    }

    @GameTest(template = TEMPLATE)
    public void listenerImposesDisadvantageOnHazardAttack(GameTestHelper helper) {
        HazardScenarios.listenerImposesDisadvantageOnHazardAttack(helper);
    }

    @GameTest(template = TEMPLATE)
    public void advantageAndDisadvantageListenersRollNormally(GameTestHelper helper) {
        HazardScenarios.advantageAndDisadvantageListenersRollNormally(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardIdReachesTheEvent(GameTestHelper helper) {
        HazardScenarios.hazardIdReachesTheEvent(helper);
    }

    @GameTest(template = TEMPLATE)
    public void failedSaveWithoutDamageDealsNone(GameTestHelper helper) {
        HazardScenarios.failedSaveWithoutDamageDealsNone(helper);
    }

    @GameTest(template = TEMPLATE)
    public void failedSaveWithDamageStillDealsAtLeastOne(GameTestHelper helper) {
        HazardScenarios.failedSaveWithDamageStillDealsAtLeastOne(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hazardAttackWithoutDamageHitsForNone(GameTestHelper helper) {
        HazardScenarios.hazardAttackWithoutDamageHitsForNone(helper);
    }

    @GameTest(template = TEMPLATE)
    public void listenerBonusRaisesTheHazardSave(GameTestHelper helper) {
        HazardScenarios.listenerBonusRaisesTheHazardSave(helper);
    }

    @GameTest(template = TEMPLATE)
    public void listenerRerollsANaturalOneOnTheHazardSave(GameTestHelper helper) {
        HazardScenarios.listenerRerollsANaturalOneOnTheHazardSave(helper);
    }

    @GameTest(template = TEMPLATE)
    public void listenerBonusAndRerollOnHazardAttack(GameTestHelper helper) {
        HazardScenarios.listenerBonusAndRerollOnHazardAttack(helper);
    }
}
