package studio.modroll.critfall.neoforge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.gametest.ApiScenarios;

/** NeoForge registration shim: delegates to the shared {@link ApiScenarios} bodies (M8). */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class ApiGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void drivenHitAppliesRolledDamageOnce(GameTestHelper helper) {
        ApiScenarios.drivenHitAppliesRolledDamageOnce(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenMissAppliesNoDamage(GameTestHelper helper) {
        ApiScenarios.drivenMissAppliesNoDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void suppressedTargetIgnoredByAutoPipeline(GameTestHelper helper) {
        ApiScenarios.suppressedTargetIgnoredByAutoPipeline(helper);
    }

    @GameTest(template = TEMPLATE)
    public void postAttackListenerCanZeroOutDamage(GameTestHelper helper) {
        ApiScenarios.postAttackListenerCanZeroOutDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenAttackMatchesAutoPipelineOnArmoredTarget(GameTestHelper helper) {
        ApiScenarios.drivenAttackMatchesAutoPipelineOnArmoredTarget(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenAttackKeepsArmorReductionWhenBypassFlagOff(GameTestHelper helper) {
        ApiScenarios.drivenAttackKeepsArmorReductionWhenBypassFlagOff(helper);
    }

    @GameTest(template = TEMPLATE)
    public void combatInteractionFiresOnNormalHit(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresOnNormalHit(helper);
    }

    @GameTest(template = TEMPLATE)
    public void combatInteractionFiresWhenRollCancelsDamage(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresWhenRollCancelsDamage(helper);
    }

    @GameTest(template = TEMPLATE)
    public void combatInteractionFiresBeforeListenerCancel(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresBeforeListenerCancel(helper);
    }

    @GameTest(template = TEMPLATE)
    public void combatInteractionFiresForSuppressedParticipants(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresForSuppressedParticipants(helper);
    }
}
