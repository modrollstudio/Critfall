package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.CombatScenarios;

/** Fabric registration shim: delegates to the shared {@link CombatScenarios} bodies (M8). */
public class CombatGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void missCancelsAllDamage(GameTestHelper helper) {
        CombatScenarios.missCancelsAllDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void hitAppliesProfileDiceInsteadOfVanilla(GameTestHelper helper) {
        CombatScenarios.hitAppliesProfileDiceInsteadOfVanilla(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void naturalTwentyDealsMaximizedDice(GameTestHelper helper) {
        CombatScenarios.naturalTwentyDealsMaximizedDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void profileArmorClassDecidesTheHit(GameTestHelper helper) {
        CombatScenarios.profileArmorClassDecidesTheHit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void heldWeaponUsesItemProfileDice(GameTestHelper helper) {
        CombatScenarios.heldWeaponUsesItemProfileDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void attackRollsDisabledRestoresVanillaDamage(GameTestHelper helper) {
        CombatScenarios.attackRollsDisabledRestoresVanillaDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mobRollsDisabledRestoresVanillaForMobs(GameTestHelper helper) {
        CombatScenarios.mobRollsDisabledRestoresVanillaForMobs(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void playerRollsDisabledRestoresVanillaForPlayers(GameTestHelper helper) {
        CombatScenarios.playerRollsDisabledRestoresVanillaForPlayers(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void damageDiceDisabledKeepsVanillaAmountOnHit(GameTestHelper helper) {
        CombatScenarios.damageDiceDisabledKeepsVanillaAmountOnHit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void critsDisabledRollsNormalDamageOnNatTwenty(GameTestHelper helper) {
        CombatScenarios.critsDisabledRollsNormalDamageOnNatTwenty(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumblesDisabledLeaveWeaponUntouched(GameTestHelper helper) {
        CombatScenarios.fumblesDisabledLeaveWeaponUntouched(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleSetsWeaponDurabilityToOne(GameTestHelper helper) {
        CombatScenarios.fumbleSetsWeaponDurabilityToOne(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleConfirmationRollSavesTheWeapon(GameTestHelper helper) {
        CombatScenarios.fumbleConfirmationRollSavesTheWeapon(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleConfirmationFailureTriggersConsequence(GameTestHelper helper) {
        CombatScenarios.fumbleConfirmationFailureTriggersConsequence(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumbleCooldownDowngradesTheSecondNatOne(GameTestHelper helper) {
        CombatScenarios.fumbleCooldownDowngradesTheSecondNatOne(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void rolledDamageBypassesVanillaArmorReduction(GameTestHelper helper) {
        CombatScenarios.rolledDamageBypassesVanillaArmorReduction(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void armorReductionAppliesWhenBypassFlagIsOff(GameTestHelper helper) {
        CombatScenarios.armorReductionAppliesWhenBypassFlagIsOff(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void globalDamageMultiplierScalesRolledDamage(GameTestHelper helper) {
        CombatScenarios.globalDamageMultiplierScalesRolledDamage(helper);
    }
}
