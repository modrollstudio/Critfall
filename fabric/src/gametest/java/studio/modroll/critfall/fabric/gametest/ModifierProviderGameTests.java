package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.ModifierProviderScenarios;

/** Fabric registration shim: delegates to the shared {@link ModifierProviderScenarios} bodies. */
public class ModifierProviderGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void noProviderRollsExactlyAsBefore(GameTestHelper helper) {
        ModifierProviderScenarios.noProviderRollsExactlyAsBefore(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void providerReplacesMeleeAttackBonus(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesMeleeAttackBonus(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void providerReplacesDamageModifier(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesDamageModifier(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void providerReplacesRangedAttackAndDamage(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesRangedAttackAndDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void providerReplacesSaveBonus(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesSaveBonus(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void emptyProviderFallsBackToOwnBonus(GameTestHelper helper) {
        ModifierProviderScenarios.emptyProviderFallsBackToOwnBonus(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void toggleOffIgnoresProvider(GameTestHelper helper) {
        ModifierProviderScenarios.toggleOffIgnoresProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenAttackUsesProvider(GameTestHelper helper) {
        ModifierProviderScenarios.drivenAttackUsesProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void explicitAttackBonusBeatsProvider(GameTestHelper helper) {
        ModifierProviderScenarios.explicitAttackBonusBeatsProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void negativeDamageModifierStillDealsOne(GameTestHelper helper) {
        ModifierProviderScenarios.negativeDamageModifierStillDealsOne(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void diceLessDamageIgnoresProvider(GameTestHelper helper) {
        ModifierProviderScenarios.diceLessDamageIgnoresProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void failedSaveNegativeModifierStillDealsOne(GameTestHelper helper) {
        ModifierProviderScenarios.failedSaveNegativeModifierStillDealsOne(helper);
    }
}
