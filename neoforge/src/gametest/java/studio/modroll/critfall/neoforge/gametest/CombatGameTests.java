package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.CombatScenarios;

/** NeoForge registration shim: delegates to the shared {@link CombatScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class CombatGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void missCancelsAllDamage(GameTestHelper helper) {
        CombatScenarios.missCancelsAllDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void hitAppliesProfileDiceInsteadOfVanilla(GameTestHelper helper) {
        CombatScenarios.hitAppliesProfileDiceInsteadOfVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void naturalTwentyDealsMaximizedDice(GameTestHelper helper) {
        CombatScenarios.naturalTwentyDealsMaximizedDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void profileArmorClassDecidesTheHit(GameTestHelper helper) {
        CombatScenarios.profileArmorClassDecidesTheHit(helper);
    }

    @GameTest(template = TEMPLATE)
    public void heldWeaponUsesItemProfileDice(GameTestHelper helper) {
        CombatScenarios.heldWeaponUsesItemProfileDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void attackRollsDisabledRestoresVanillaDamage(GameTestHelper helper) {
        CombatScenarios.attackRollsDisabledRestoresVanillaDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void mobRollsDisabledRestoresVanillaForMobs(GameTestHelper helper) {
        CombatScenarios.mobRollsDisabledRestoresVanillaForMobs(helper);
    }

    @GameTest(template = TEMPLATE)
    public void playerRollsDisabledRestoresVanillaForPlayers(GameTestHelper helper) {
        CombatScenarios.playerRollsDisabledRestoresVanillaForPlayers(helper);
    }

    @GameTest(template = TEMPLATE)
    public void damageDiceDisabledKeepsVanillaAmountOnHit(GameTestHelper helper) {
        CombatScenarios.damageDiceDisabledKeepsVanillaAmountOnHit(helper);
    }

    @GameTest(template = TEMPLATE)
    public void critsDisabledRollsNormalDamageOnNatTwenty(GameTestHelper helper) {
        CombatScenarios.critsDisabledRollsNormalDamageOnNatTwenty(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumblesDisabledLeaveWeaponUntouched(GameTestHelper helper) {
        CombatScenarios.fumblesDisabledLeaveWeaponUntouched(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleSetsWeaponDurabilityToOne(GameTestHelper helper) {
        CombatScenarios.fumbleSetsWeaponDurabilityToOne(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleConfirmationRollSavesTheWeapon(GameTestHelper helper) {
        CombatScenarios.fumbleConfirmationRollSavesTheWeapon(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleConfirmationFailureTriggersConsequence(GameTestHelper helper) {
        CombatScenarios.fumbleConfirmationFailureTriggersConsequence(helper);
    }

    @GameTest(template = TEMPLATE)
    public void fumbleCooldownDowngradesTheSecondNatOne(GameTestHelper helper) {
        CombatScenarios.fumbleCooldownDowngradesTheSecondNatOne(helper);
    }

    @GameTest(template = TEMPLATE)
    public void rolledDamageBypassesVanillaArmorReduction(GameTestHelper helper) {
        CombatScenarios.rolledDamageBypassesVanillaArmorReduction(helper);
    }

    @GameTest(template = TEMPLATE)
    public void armorReductionAppliesWhenBypassFlagIsOff(GameTestHelper helper) {
        CombatScenarios.armorReductionAppliesWhenBypassFlagIsOff(helper);
    }

    @GameTest(template = TEMPLATE)
    public void globalDamageMultiplierScalesRolledDamage(GameTestHelper helper) {
        CombatScenarios.globalDamageMultiplierScalesRolledDamage(helper);
    }
}
