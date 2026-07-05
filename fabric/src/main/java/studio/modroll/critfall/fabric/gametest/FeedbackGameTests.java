package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.FeedbackScenarios;

/** Fabric registration shim: delegates to the shared {@link FeedbackScenarios} bodies (M8). */
public class FeedbackGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modlessFallbackCarriesConsequenceText(GameTestHelper helper) {
        FeedbackScenarios.modlessFallbackCarriesConsequenceText(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void critDispatchesCritPayload(GameTestHelper helper) {
        FeedbackScenarios.critDispatchesCritPayload(helper);
    }
}
