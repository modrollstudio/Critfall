package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.SpellScenarios;

/** Fabric registration shim: delegates to the shared {@link SpellScenarios} bodies (M8). */
public class SpellGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void indirectDamageRollsAsSpellWithDerivedDice(GameTestHelper helper) {
        SpellScenarios.indirectDamageRollsAsSpellWithDerivedDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void spellRollsDisabledRestoresVanilla(GameTestHelper helper) {
        SpellScenarios.spellRollsDisabledRestoresVanilla(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unknownSpellPassthroughLeavesVanilla(GameTestHelper helper) {
        SpellScenarios.unknownSpellPassthroughLeavesVanilla(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void successfulSaveHalvesTheDamage(GameTestHelper helper) {
        SpellScenarios.successfulSaveHalvesTheDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void failedSaveTakesFullProfileDice(GameTestHelper helper) {
        SpellScenarios.failedSaveTakesFullProfileDice(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void negateOutcomeCancelsAllDamageOnSave(GameTestHelper helper) {
        SpellScenarios.negateOutcomeCancelsAllDamageOnSave(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void targetProfileSaveBonusCounts(GameTestHelper helper) {
        SpellScenarios.targetProfileSaveBonusCounts(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void savesDisabledFallBackToAttackRoll(GameTestHelper helper) {
        SpellScenarios.savesDisabledFallBackToAttackRoll(helper);
    }
}
