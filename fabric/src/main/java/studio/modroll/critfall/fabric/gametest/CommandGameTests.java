package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.CommandScenarios;

/** Fabric registration shim: delegates to the shared {@link CommandScenarios} bodies (M8). */
public class CommandGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void inspectWithSelectorExecutesThroughTheDispatcher(GameTestHelper helper) {
        CommandScenarios.inspectWithSelectorExecutesThroughTheDispatcher(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void inspectWithoutArgumentRaycastsTheCrosshairEntity(GameTestHelper helper) {
        CommandScenarios.inspectWithoutArgumentRaycastsTheCrosshairEntity(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void checkWithItemArgumentExecutesThroughTheDispatcher(GameTestHelper helper) {
        CommandScenarios.checkWithItemArgumentExecutesThroughTheDispatcher(helper);
    }
}
