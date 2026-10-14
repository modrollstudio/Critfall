package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.HazardScenarios;

/** Fabric registration shim: delegates to the shared {@link HazardScenarios} bodies. */
public class HazardGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void failedSaveTakesTheRolledDamage(GameTestHelper helper) {
        HazardScenarios.failedSaveTakesTheRolledDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void successfulSaveTakesHalf(GameTestHelper helper) {
        HazardScenarios.successfulSaveTakesHalf(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void successfulSaveCanTakeNone(GameTestHelper helper) {
        HazardScenarios.successfulSaveCanTakeNone(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void checksStyleProviderModifiesTheSave(GameTestHelper helper) {
        HazardScenarios.checksStyleProviderModifiesTheSave(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void saveWithDisadvantageKeepsTheLowerDie(GameTestHelper helper) {
        HazardScenarios.saveWithDisadvantageKeepsTheLowerDie(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardAttackHitAppliesTheDice(GameTestHelper helper) {
        HazardScenarios.hazardAttackHitAppliesTheDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardAttackNaturalOneJustMisses(GameTestHelper helper) {
        HazardScenarios.hazardAttackNaturalOneJustMisses(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardAttackCritUsesTheCritRule(GameTestHelper helper) {
        HazardScenarios.hazardAttackCritUsesTheCritRule(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardAttackWithAdvantageKeepsTheHigherDie(GameTestHelper helper) {
        HazardScenarios.hazardAttackWithAdvantageKeepsTheHigherDie(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardAttackUsesTheProvidedArmorClass(GameTestHelper helper) {
        HazardScenarios.hazardAttackUsesTheProvidedArmorClass(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardDamageRespectsImmunity(GameTestHelper helper) {
        HazardScenarios.hazardDamageRespectsImmunity(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listenerGrantsAdvantageOnHazardSave(GameTestHelper helper) {
        HazardScenarios.listenerGrantsAdvantageOnHazardSave(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listenerImposesDisadvantageOnHazardAttack(GameTestHelper helper) {
        HazardScenarios.listenerImposesDisadvantageOnHazardAttack(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void advantageAndDisadvantageListenersRollNormally(GameTestHelper helper) {
        HazardScenarios.advantageAndDisadvantageListenersRollNormally(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardIdReachesTheEvent(GameTestHelper helper) {
        HazardScenarios.hazardIdReachesTheEvent(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void failedSaveWithoutDamageDealsNone(GameTestHelper helper) {
        HazardScenarios.failedSaveWithoutDamageDealsNone(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void failedSaveWithDamageStillDealsAtLeastOne(GameTestHelper helper) {
        HazardScenarios.failedSaveWithDamageStillDealsAtLeastOne(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hazardAttackWithoutDamageHitsForNone(GameTestHelper helper) {
        HazardScenarios.hazardAttackWithoutDamageHitsForNone(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listenerBonusRaisesTheHazardSave(GameTestHelper helper) {
        HazardScenarios.listenerBonusRaisesTheHazardSave(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listenerRerollsANaturalOneOnTheHazardSave(GameTestHelper helper) {
        HazardScenarios.listenerRerollsANaturalOneOnTheHazardSave(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listenerBonusAndRerollOnHazardAttack(GameTestHelper helper) {
        HazardScenarios.listenerBonusAndRerollOnHazardAttack(helper);
    }
}
