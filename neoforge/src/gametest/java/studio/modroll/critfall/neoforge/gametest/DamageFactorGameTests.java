package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.DamageFactorScenarios;

/** NeoForge registration shim: delegates to the shared {@link DamageFactorScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class DamageFactorGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void swordMaterialWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.swordMaterialWithoutProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void swordMaterialWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.swordMaterialWithProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void strengthAndWeaknessWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.strengthAndWeaknessWithoutProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void strengthAndWeaknessWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.strengthAndWeaknessWithProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void bowChargeWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.bowChargeWithoutProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void bowChargeWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.bowChargeWithProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void powerWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.powerWithoutProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void powerWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.powerWithProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void sharpnessWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.sharpnessWithoutProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void sharpnessWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.sharpnessWithProvider(helper);
    }

    @GameTest(template = TEMPLATE)
    public void smiteCountsOnlyAgainstUndead(GameTestHelper helper) {
        DamageFactorScenarios.smiteCountsOnlyAgainstUndead(helper);
    }

    @GameTest(template = TEMPLATE)
    public void baneOfArthropodsCountsOnlyAgainstArthropods(GameTestHelper helper) {
        DamageFactorScenarios.baneOfArthropodsCountsOnlyAgainstArthropods(helper);
    }

    @GameTest(template = TEMPLATE)
    public void sharpnessCountsOnDrivenAttack(GameTestHelper helper) {
        DamageFactorScenarios.sharpnessCountsOnDrivenAttack(helper);
    }
}
