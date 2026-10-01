package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.DamageFactorScenarios;

/** Fabric registration shim: delegates to the shared {@link DamageFactorScenarios} bodies. */
public class DamageFactorGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void swordMaterialWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.swordMaterialWithoutProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void swordMaterialWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.swordMaterialWithProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void strengthAndWeaknessWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.strengthAndWeaknessWithoutProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void strengthAndWeaknessWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.strengthAndWeaknessWithProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bowChargeWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.bowChargeWithoutProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bowChargeWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.bowChargeWithProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void powerWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.powerWithoutProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void powerWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.powerWithProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sharpnessWithoutProvider(GameTestHelper helper) {
        DamageFactorScenarios.sharpnessWithoutProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sharpnessWithProvider(GameTestHelper helper) {
        DamageFactorScenarios.sharpnessWithProvider(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void smiteCountsOnlyAgainstUndead(GameTestHelper helper) {
        DamageFactorScenarios.smiteCountsOnlyAgainstUndead(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void baneOfArthropodsCountsOnlyAgainstArthropods(GameTestHelper helper) {
        DamageFactorScenarios.baneOfArthropodsCountsOnlyAgainstArthropods(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void sharpnessCountsOnDrivenAttack(GameTestHelper helper) {
        DamageFactorScenarios.sharpnessCountsOnDrivenAttack(helper);
    }
}
