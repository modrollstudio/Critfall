package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.CommandScenarios;
import studio.modroll.critfall.gametest.DryRunScenarios;
import studio.modroll.critfall.gametest.GenerateScenarios;
import studio.modroll.critfall.gametest.ReportScenarios;

/** NeoForge registration shim: delegates to the shared {@link CommandScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void inspectWithSelectorExecutesThroughTheDispatcher(GameTestHelper helper) {
        CommandScenarios.inspectWithSelectorExecutesThroughTheDispatcher(helper);
    }

    @GameTest(template = TEMPLATE)
    public void inspectWithoutArgumentRaycastsTheCrosshairEntity(GameTestHelper helper) {
        CommandScenarios.inspectWithoutArgumentRaycastsTheCrosshairEntity(helper);
    }

    @GameTest(template = TEMPLATE)
    public void checkWithItemArgumentExecutesThroughTheDispatcher(GameTestHelper helper) {
        CommandScenarios.checkWithItemArgumentExecutesThroughTheDispatcher(helper);
    }

    @GameTest(template = TEMPLATE)
    public void dryRunLeavesWorldAndWeaponUntouched(GameTestHelper helper) {
        DryRunScenarios.dryRunLeavesWorldAndWeaponUntouched(helper);
    }

    @GameTest(template = TEMPLATE)
    public void generateWritesADatapack(GameTestHelper helper) {
        GenerateScenarios.generateWritesADatapack(helper);
    }

    @GameTest(template = TEMPLATE)
    public void reportClassifiesProfileVsFallback(GameTestHelper helper) {
        ReportScenarios.reportClassifiesProfileVsFallback(helper);
    }
}
