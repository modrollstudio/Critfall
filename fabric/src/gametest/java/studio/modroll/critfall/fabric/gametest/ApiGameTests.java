package studio.modroll.critfall.fabric.gametest;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.gametest.ApiScenarios;

/** Fabric registration shim: delegates to the shared {@link ApiScenarios} bodies (M8). */
public class ApiGameTests implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenHitAppliesRolledDamageOnce(GameTestHelper helper) {
        ApiScenarios.drivenHitAppliesRolledDamageOnce(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenMissAppliesNoDamage(GameTestHelper helper) {
        ApiScenarios.drivenMissAppliesNoDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void defenderAcBonusTurnsHitIntoMiss(GameTestHelper helper) {
        ApiScenarios.defenderAcBonusTurnsHitIntoMiss(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void negativeDefenderAcBonusTurnsMissIntoHit(GameTestHelper helper) {
        ApiScenarios.negativeDefenderAcBonusTurnsMissIntoHit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void suppressedTargetIgnoredByAutoPipeline(GameTestHelper helper) {
        ApiScenarios.suppressedTargetIgnoredByAutoPipeline(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void postAttackListenerCanZeroOutDamage(GameTestHelper helper) {
        ApiScenarios.postAttackListenerCanZeroOutDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenAttackMatchesAutoPipelineOnArmoredTarget(GameTestHelper helper) {
        ApiScenarios.drivenAttackMatchesAutoPipelineOnArmoredTarget(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenAttackKeepsArmorReductionWhenBypassFlagOff(GameTestHelper helper) {
        ApiScenarios.drivenAttackKeepsArmorReductionWhenBypassFlagOff(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void combatInteractionFiresOnNormalHit(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresOnNormalHit(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void combatInteractionFiresWhenRollCancelsDamage(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresWhenRollCancelsDamage(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void combatInteractionFiresBeforeListenerCancel(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresBeforeListenerCancel(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void combatInteractionFiresForSuppressedParticipants(GameTestHelper helper) {
        ApiScenarios.combatInteractionFiresForSuppressedParticipants(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void drivenAttacksBypassInvulnerabilityFrames(GameTestHelper helper) {
        ApiScenarios.drivenAttacksBypassInvulnerabilityFrames(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void vanillaDamageStillRespectsInvulnerabilityFrames(GameTestHelper helper) {
        ApiScenarios.vanillaDamageStillRespectsInvulnerabilityFrames(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void listenerDetectsDrivenDamage(GameTestHelper helper) {
        ApiScenarios.listenerDetectsDrivenDamage(helper, DamageObserver::install);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void contestResolvesWinnerAndTotals(GameTestHelper helper) {
        ApiScenarios.contestResolvesWinnerAndTotals(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void contestTieGoesToOpponent(GameTestHelper helper) {
        ApiScenarios.contestTieGoesToOpponent(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void contestAppliesRollModePerSide(GameTestHelper helper) {
        ApiScenarios.contestAppliesRollModePerSide(helper);
    }

    /** Bridges a shared-scenario observer to Fabric's {@code ALLOW_DAMAGE}, registered once. */
    private static final class DamageObserver {

        private static volatile Consumer<LivingEntity> active;

        static {
            ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
                Consumer<LivingEntity> observer = active;
                if (observer != null) {
                    observer.accept(entity);
                }
                return true;
            });
        }

        static AutoCloseable install(Consumer<LivingEntity> observer) {
            active = observer;
            return () -> active = null;
        }
    }
}
