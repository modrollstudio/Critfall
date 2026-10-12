package studio.modroll.critfall.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.gametest.EntityProfileScenarios;

/** Fabric registration shim: delegates to the shared {@link EntityProfileScenarios} bodies. */
public class EntityProfileGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void assignedProfileBeatsTheTypeProfile(GameTestHelper helper) {
        EntityProfileScenarios.assignedProfileBeatsTheTypeProfile(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void assignedProfileDrivesTheLiveRoll(GameTestHelper helper) {
        EntityProfileScenarios.assignedProfileDrivesTheLiveRoll(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void assignedProfileIsSavedWithTheEntity(GameTestHelper helper) {
        EntityProfileScenarios.assignedProfileIsSavedWithTheEntity(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void summonDataAssignsTheProfile(GameTestHelper helper) {
        EntityProfileScenarios.summonDataAssignsTheProfile(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void unloadedAssignedProfileFallsBackToTheType(GameTestHelper helper) {
        EntityProfileScenarios.unloadedAssignedProfileFallsBackToTheType(helper);
    }
}
