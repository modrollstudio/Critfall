package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.ModifierProviderScenarios;

/** NeoForge registration shim: delegates to the shared {@link ModifierProviderScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModifierProviderGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void noProviderRollsExactlyAsBefore(GameTestHelper helper) {
        ModifierProviderScenarios.noProviderRollsExactlyAsBefore(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providerReplacesMeleeAttackBonus(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesMeleeAttackBonus(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providerAddsToDamageModifier(GameTestHelper helper) {
        ModifierProviderScenarios.providerAddsToDamageModifier(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providerReplacesRangedAttackAndAddsToDamage(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesRangedAttackAndAddsToDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providerReplacesSaveBonus(GameTestHelper helper) {
        ModifierProviderScenarios.providerReplacesSaveBonus(helper);
    }

    @GameTest(template = TEMPLATE)
    public void emptyProviderFallsBackToOwnBonus(GameTestHelper helper) {
        ModifierProviderScenarios.emptyProviderFallsBackToOwnBonus(helper);
    }

    @GameTest(template = TEMPLATE)
    public void toggleOffIgnoresProvider(GameTestHelper helper) {
        ModifierProviderScenarios.toggleOffIgnoresProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenAttackUsesProvider(GameTestHelper helper) {
        ModifierProviderScenarios.drivenAttackUsesProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void explicitAttackBonusBeatsProvider(GameTestHelper helper) {
        ModifierProviderScenarios.explicitAttackBonusBeatsProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void negativeDamageModifierStillDealsOne(GameTestHelper helper) {
        ModifierProviderScenarios.negativeDamageModifierStillDealsOne(helper);
    }

    @GameTest(template = TEMPLATE)
    public void diceLessDamageIgnoresProvider(GameTestHelper helper) {
        ModifierProviderScenarios.diceLessDamageIgnoresProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void failedSaveNegativeModifierStillDealsOne(GameTestHelper helper) {
        ModifierProviderScenarios.failedSaveNegativeModifierStillDealsOne(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providerRaisesMeleeArmorClass(GameTestHelper helper) {
        ModifierProviderScenarios.providerRaisesMeleeArmorClass(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providerLowersMeleeArmorClass(GameTestHelper helper) {
        ModifierProviderScenarios.providerLowersMeleeArmorClass(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providedArmorClassAppliesToRangedAttacks(GameTestHelper helper) {
        ModifierProviderScenarios.providedArmorClassAppliesToRangedAttacks(helper);
    }

    @GameTest(template = TEMPLATE)
    public void providedArmorClassAppliesToSpellAttacks(GameTestHelper helper) {
        ModifierProviderScenarios.providedArmorClassAppliesToSpellAttacks(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenAttackUsesProvidedArmorClass(GameTestHelper helper) {
        ModifierProviderScenarios.drivenAttackUsesProvidedArmorClass(helper);
    }

    @GameTest(template = TEMPLATE)
    public void badArmorClassAnswerFallsBackAndLogsOnce(GameTestHelper helper) {
        ModifierProviderScenarios.badArmorClassAnswerFallsBackAndLogsOnce(helper);
    }
}
