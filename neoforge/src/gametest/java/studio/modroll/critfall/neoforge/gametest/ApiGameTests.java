package studio.modroll.critfall.neoforge.gametest;

import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
    public void defenderAcBonusTurnsHitIntoMiss(GameTestHelper helper) {
        ApiScenarios.defenderAcBonusTurnsHitIntoMiss(helper);
    }

    @GameTest(template = TEMPLATE)
    public void negativeDefenderAcBonusTurnsMissIntoHit(GameTestHelper helper) {
        ApiScenarios.negativeDefenderAcBonusTurnsMissIntoHit(helper);
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

    @GameTest(template = TEMPLATE)
    public void drivenAttacksBypassInvulnerabilityFrames(GameTestHelper helper) {
        ApiScenarios.drivenAttacksBypassInvulnerabilityFrames(helper);
    }

    @GameTest(template = TEMPLATE)
    public void vanillaDamageStillRespectsInvulnerabilityFrames(GameTestHelper helper) {
        ApiScenarios.vanillaDamageStillRespectsInvulnerabilityFrames(helper);
    }

    @GameTest(template = TEMPLATE)
    public void listenerDetectsDrivenDamage(GameTestHelper helper) {
        ApiScenarios.listenerDetectsDrivenDamage(helper, DamageObserver::install);
    }

    @GameTest(template = TEMPLATE)
    public void contestResolvesWinnerAndTotals(GameTestHelper helper) {
        ApiScenarios.contestResolvesWinnerAndTotals(helper);
    }

    @GameTest(template = TEMPLATE)
    public void contestTieGoesToOpponent(GameTestHelper helper) {
        ApiScenarios.contestTieGoesToOpponent(helper);
    }

    @GameTest(template = TEMPLATE)
    public void contestAppliesRollModePerSide(GameTestHelper helper) {
        ApiScenarios.contestAppliesRollModePerSide(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenAdvantageResultReportsBothDice(GameTestHelper helper) {
        ApiScenarios.drivenAdvantageResultReportsBothDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void drivenDisadvantageResultReportsBothDice(GameTestHelper helper) {
        ApiScenarios.drivenDisadvantageResultReportsBothDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void rollDetailAndAcSplitReachTheFeedbackPayload(GameTestHelper helper) {
        ApiScenarios.rollDetailAndAcSplitReachTheFeedbackPayload(helper);
    }

    @GameTest(template = TEMPLATE)
    public void plainDrivenAttackPayloadCarriesNoRollDetail(GameTestHelper helper) {
        ApiScenarios.plainDrivenAttackPayloadCarriesNoRollDetail(helper);
    }

    @GameTest(template = TEMPLATE)
    public void savingThrowWithAdvantageReportsBothDice(GameTestHelper helper) {
        ApiScenarios.savingThrowWithAdvantageReportsBothDice(helper);
    }

    @GameTest(template = TEMPLATE)
    public void contestCarriesPerSideRollDetail(GameTestHelper helper) {
        ApiScenarios.contestCarriesPerSideRollDetail(helper);
    }

    /** Bridges a shared-scenario observer to NeoForge's {@code LivingIncomingDamageEvent}. */
    private static final class DamageObserver implements AutoCloseable {

        private final Consumer<LivingEntity> observer;

        private DamageObserver(Consumer<LivingEntity> observer) {
            this.observer = observer;
        }

        static AutoCloseable install(Consumer<LivingEntity> observer) {
            DamageObserver holder = new DamageObserver(observer);
            NeoForge.EVENT_BUS.register(holder);
            return holder;
        }

        @SubscribeEvent
        public void onDamage(LivingIncomingDamageEvent event) {
            observer.accept(event.getEntity());
        }

        @Override
        public void close() {
            NeoForge.EVENT_BUS.unregister(this);
        }
    }
}
