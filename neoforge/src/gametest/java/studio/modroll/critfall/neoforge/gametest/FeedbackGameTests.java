package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.FeedbackScenarios;

/** NeoForge registration shim: delegates to the shared {@link FeedbackScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class FeedbackGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void modlessFallbackCarriesConsequenceText(GameTestHelper helper) {
        FeedbackScenarios.modlessFallbackCarriesConsequenceText(helper);
    }

    @GameTest(template = TEMPLATE)
    public void critDispatchesCritPayload(GameTestHelper helper) {
        FeedbackScenarios.critDispatchesCritPayload(helper);
    }

    @GameTest(template = TEMPLATE)
    public void resistanceFiveLethalRollShowsNoKillLine(GameTestHelper helper) {
        FeedbackScenarios.resistanceFiveLethalRollShowsNoKillLine(helper);
    }

    @GameTest(template = TEMPLATE)
    public void totemLethalRollShowsNoKillLine(GameTestHelper helper) {
        FeedbackScenarios.totemLethalRollShowsNoKillLine(helper);
    }

    @GameTest(template = TEMPLATE)
    public void absorptionLethalRollShowsNoKillLine(GameTestHelper helper) {
        FeedbackScenarios.absorptionLethalRollShowsNoKillLine(helper);
    }

    @GameTest(template = TEMPLATE)
    public void realKillShowsKillLine(GameTestHelper helper) {
        FeedbackScenarios.realKillShowsKillLine(helper);
    }

    @GameTest(template = TEMPLATE)
    public void totemAtOneHealthIsNotMarkedResisted(GameTestHelper helper) {
        FeedbackScenarios.totemAtOneHealthIsNotMarkedResisted(helper);
    }
}
