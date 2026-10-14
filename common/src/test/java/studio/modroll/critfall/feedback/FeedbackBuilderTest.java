package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.SequenceRandom;
import studio.modroll.critfall.api.feedback.FlavorName;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.FlavorLines;
import studio.modroll.critfall.data.FlavorPool;

class FeedbackBuilderTest {

    private final UUID target = UUID.randomUUID();

    private static final FlavorName ATTACKER = FlavorName.literal("Alex");
    private static final FlavorName TARGET = new FlavorName("entity.minecraft.zombie", true);

    private static FlavorLines pool() {
        return new FlavorLines(Map.of(
                FlavorPool.CRIT, List.of("crit.line"),
                FlavorPool.FUMBLE, List.of("fumble.line"),
                FlavorPool.KILL, List.of("kill.line")));
    }

    private RollFeedbackPayload build(AttackOutcome outcome, int natural, boolean isKill, long gameTime) {
        AttackResult result = new AttackResult(outcome, natural, natural, 10, 0);
        return FeedbackBuilder.buildAttack(
                result,
                isKill,
                "1d6",
                true,
                List.of(),
                pool(),
                ATTACKER,
                TARGET,
                Rules.DEFAULTS,
                new DiceRoller(SequenceRandom.ofDieFaces()),
                target,
                gameTime);
    }

    private SaveFeedbackPayload buildSave(boolean isKill, Rules rules, long gameTime) {
        SaveResult save = new SaveResult(13, 15, 13);
        return FeedbackBuilder.buildSave(
                save,
                isKill,
                Rules.SaveOutcome.HALF,
                "2d6",
                7,
                true,
                pool(),
                ATTACKER,
                TARGET,
                rules,
                new DiceRoller(SequenceRandom.ofDieFaces()),
                target,
                gameTime);
    }

    private static Rules noFlavorRules() {
        return new Rules(
                Rules.AttackRolls.DEFAULTS,
                true,
                Rules.Crits.DEFAULTS,
                Rules.Fumbles.DEFAULTS,
                Rules.Spells.DEFAULTS,
                Rules.Fallbacks.DEFAULTS,
                new Rules.Feedback(Rules.FeedbackVisibility.EVERYONE, new Rules.Feedback.Flavor(false, 20)),
                Rules.Balance.DEFAULTS);
    }

    @AfterEach
    void cleanup() {
        FlavorCooldowns.clear();
    }

    @Test
    void ordinaryHitHasNoFlavor() {
        assertTrue(build(AttackOutcome.HIT, 13, false, 100).flavorKey().isEmpty());
    }

    @Test
    void critAlwaysGetsFlavorEvenOnCooldown() {
        FlavorCooldowns.record(target, 100, 20);
        assertTrue(build(AttackOutcome.CRIT, 20, false, 105).flavorKey().isPresent());
    }

    @Test
    void nonCritKillSuppressedWhileOnCooldown() {
        FlavorCooldowns.record(target, 100, 20);
        assertTrue(build(AttackOutcome.HIT, 13, true, 105).flavorKey().isEmpty());
    }

    @Test
    void nonCritKillGetsFlavorWhenNotOnCooldown() {
        assertTrue(build(AttackOutcome.HIT, 13, true, 100).flavorKey().isPresent());
    }

    @Test
    void flavorDisabledInRulesSendsNoFlavor() {
        AttackResult crit = new AttackResult(AttackOutcome.CRIT, 20, 20, 10, 0);
        RollFeedbackPayload payload = FeedbackBuilder.buildAttack(
                crit,
                false,
                "1d6",
                true,
                List.of(),
                pool(),
                ATTACKER,
                TARGET,
                noFlavorRules(),
                new DiceRoller(SequenceRandom.ofDieFaces()),
                target,
                100);
        assertFalse(payload.flavorKey().isPresent());
    }

    @Test
    void fumbleAlwaysGetsFlavorEvenOnCooldown() {
        FlavorCooldowns.record(target, 100, 20);
        assertTrue(build(AttackOutcome.FUMBLE, 1, false, 105).flavorKey().isPresent());
    }

    @Test
    void nonCritKillWithEmptyPoolRecordsNoCooldown() {
        FlavorLines critOnlyPool = new FlavorLines(Map.of(FlavorPool.CRIT, List.of("c")));
        RollFeedbackPayload payload = FeedbackBuilder.buildAttack(
                new AttackResult(AttackOutcome.HIT, 13, 13, 10, 0),
                true,
                "1d6",
                true,
                List.of(),
                critOnlyPool,
                ATTACKER,
                TARGET,
                Rules.DEFAULTS,
                new DiceRoller(SequenceRandom.ofDieFaces()),
                target,
                100);
        assertTrue(payload.flavorKey().isEmpty());
        assertFalse(FlavorCooldowns.isOnCooldown(target, 100, 20));
    }

    @Test
    void flavorLineCarriesBothNames() {
        RollFeedbackPayload payload = build(AttackOutcome.CRIT, 20, false, 100);
        assertEquals(ATTACKER, payload.flavorAttacker());
        assertEquals(TARGET, payload.flavorTarget());
        FlavorCooldowns.clear(); // the crit's line put the target on cooldown
        SaveFeedbackPayload save = buildSave(true, Rules.DEFAULTS, 100);
        assertEquals(ATTACKER, save.flavorAttacker());
        assertEquals(TARGET, save.flavorTarget());
    }

    @Test
    void noFlavorLineCarriesNoNames() {
        RollFeedbackPayload payload = build(AttackOutcome.HIT, 13, false, 100);
        assertEquals(FlavorName.NONE, payload.flavorAttacker());
        assertEquals(FlavorName.NONE, payload.flavorTarget());
    }

    @Test
    void saveKillOffCooldownGetsFlavor() {
        assertTrue(buildSave(true, Rules.DEFAULTS, 100).flavorKey().isPresent());
    }

    @Test
    void saveKillOnCooldownSuppressed() {
        FlavorCooldowns.record(target, 100, 20);
        assertTrue(buildSave(true, Rules.DEFAULTS, 105).flavorKey().isEmpty());
    }

    @Test
    void saveNonKillHasNoFlavor() {
        assertTrue(buildSave(false, Rules.DEFAULTS, 100).flavorKey().isEmpty());
    }

    @Test
    void saveFlavorDisabledInRulesSendsNone() {
        assertTrue(buildSave(true, noFlavorRules(), 100).flavorKey().isEmpty());
    }
}
