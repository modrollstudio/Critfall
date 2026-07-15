package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.SpellScenarios;

/** NeoForge registration shim: delegates to the shared {@link SpellScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpellGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void indirectDamageRollsAsSpellWithDerivedDice(GameTestHelper helper) {
        SpellScenarios.indirectDamageRollsAsSpellWithDerivedDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void spellRollsDisabledRestoresVanilla(GameTestHelper helper) {
        SpellScenarios.spellRollsDisabledRestoresVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void unknownSpellPassthroughLeavesVanilla(GameTestHelper helper) {
        SpellScenarios.unknownSpellPassthroughLeavesVanilla(helper);
    }

    @GameTest(template = TEMPLATE)
    public void successfulSaveHalvesTheDamage(GameTestHelper helper) {
        SpellScenarios.successfulSaveHalvesTheDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void failedSaveTakesFullProfileDice(GameTestHelper helper) {
        SpellScenarios.failedSaveTakesFullProfileDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void negateOutcomeCancelsAllDamageOnSave(GameTestHelper helper) {
        SpellScenarios.negateOutcomeCancelsAllDamageOnSave(helper);
    }

    @GameTest(template = TEMPLATE)
    public void targetProfileSaveBonusCounts(GameTestHelper helper) {
        SpellScenarios.targetProfileSaveBonusCounts(helper);
    }

    @GameTest(template = TEMPLATE)
    public void savesDisabledFallBackToAttackRoll(GameTestHelper helper) {
        SpellScenarios.savesDisabledFallBackToAttackRoll(helper);
    }
}
