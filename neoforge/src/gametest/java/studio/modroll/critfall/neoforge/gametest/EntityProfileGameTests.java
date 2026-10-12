package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.EntityProfileScenarios;

/** NeoForge registration shim: delegates to the shared {@link EntityProfileScenarios} bodies. */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class EntityProfileGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void assignedProfileBeatsTheTypeProfile(GameTestHelper helper) {
        EntityProfileScenarios.assignedProfileBeatsTheTypeProfile(helper);
    }

    @GameTest(template = TEMPLATE)
    public void assignedProfileDrivesTheLiveRoll(GameTestHelper helper) {
        EntityProfileScenarios.assignedProfileDrivesTheLiveRoll(helper);
    }

    @GameTest(template = TEMPLATE)
    public void assignedProfileIsSavedWithTheEntity(GameTestHelper helper) {
        EntityProfileScenarios.assignedProfileIsSavedWithTheEntity(helper);
    }

    @GameTest(template = TEMPLATE)
    public void summonDataAssignsTheProfile(GameTestHelper helper) {
        EntityProfileScenarios.summonDataAssignsTheProfile(helper);
    }

    @GameTest(template = TEMPLATE)
    public void unloadedAssignedProfileFallsBackToTheType(GameTestHelper helper) {
        EntityProfileScenarios.unloadedAssignedProfileFallsBackToTheType(helper);
    }
}
