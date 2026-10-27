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

    /** {@code faces}: the cosmetic dice, the d100 kill chance first when a kill draws one. */
    private RollFeedbackPayload build(AttackOutcome outcome, int natural, boolean isKill, long gameTime, int... faces) {
        return build(outcome, natural, isKill, false, Rules.DEFAULTS, gameTime, faces);
    }

    private RollFeedbackPayload build(
            AttackOutcome outcome,
            int natural,
            boolean isKill,
            boolean alwaysFlavorKill,
            Rules rules,
            long gameTime,
            int... faces) {
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
                rules,
                new DiceRoller(SequenceRandom.ofDieFaces(faces)),
                target,
                gameTime,
                1.0F,
                alwaysFlavorKill);
    }

    private SaveFeedbackPayload buildSave(boolean isKill, Rules rules, long gameTime, int... faces) {
        return buildSave(isKill, false, rules, gameTime, faces);
    }

    private SaveFeedbackPayload buildSave(
            boolean isKill, boolean alwaysFlavorKill, Rules rules, long gameTime, int... faces) {
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
                new DiceRoller(SequenceRandom.ofDieFaces(faces)),
                target,
                gameTime,
                alwaysFlavorKill);
    }

    private static Rules killChance(int percent) {
        return new Rules(
                Rules.AttackRolls.DEFAULTS,
                true,
                Rules.Crits.DEFAULTS,
                Rules.Fumbles.DEFAULTS,
                Rules.Spells.DEFAULTS,
                Rules.Fallbacks.DEFAULTS,
                new Rules.Feedback(Rules.FeedbackVisibility.EVERYONE, new Rules.Feedback.Flavor(true, 20, percent)),
                Rules.Balance.DEFAULTS);
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
    void ordinaryKillGetsALineAtTheKillChance() {
        assertEquals(25, Rules.DEFAULTS.feedback().flavor().killChancePercent(), "1 in 4 by default");
        assertTrue(build(AttackOutcome.HIT, 13, true, 100, 25).flavorKey().isPresent(), "d100 25 is within 25%");
        FlavorCooldowns.clear();
        assertTrue(build(AttackOutcome.HIT, 13, true, 100, 26).flavorKey().isEmpty(), "d100 26 is not");
        assertFalse(FlavorCooldowns.isOnCooldown(target, 100, 20), "a kill without a line records no cooldown");
    }

    @Test
    void namedMobOrBossKillAlwaysGetsALine() {
        // no die at all: no d100, and the pool's one line needs no pick
        assertTrue(build(AttackOutcome.HIT, 13, true, true, Rules.DEFAULTS, 100)
                .flavorKey()
                .isPresent());
        FlavorCooldowns.clear();
        assertTrue(buildSave(true, true, Rules.DEFAULTS, 100).flavorKey().isPresent());
        FlavorCooldowns.clear();
        assertTrue(
                build(AttackOutcome.HIT, 13, true, true, killChance(0), 100)
                        .flavorKey()
                        .isPresent(),
                "even with the chance at 0");
    }

    @Test
    void killChanceAtItsEndsDrawsNoDie() {
        assertTrue(build(AttackOutcome.HIT, 13, true, false, killChance(0), 100)
                .flavorKey()
                .isEmpty());
        assertTrue(build(AttackOutcome.HIT, 13, true, false, killChance(100), 100)
                .flavorKey()
                .isPresent());
    }

    @Test
    void namedMobStillWaitsOutTheCooldown() {
        FlavorCooldowns.record(target, 100, 20);
        assertTrue(build(AttackOutcome.HIT, 13, true, true, Rules.DEFAULTS, 105)
                .flavorKey()
                .isEmpty());
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
                new DiceRoller(SequenceRandom.ofDieFaces(1)), // the kill chance passes, the pool is empty
                target,
                100,
                1.0F,
                false);
        assertTrue(payload.flavorKey().isEmpty());
        assertFalse(FlavorCooldowns.isOnCooldown(target, 100, 20));
    }

    @Test
    void flavorLineCarriesBothNames() {
        RollFeedbackPayload payload = build(AttackOutcome.CRIT, 20, false, 100);
        assertEquals(ATTACKER, payload.flavorAttacker());
        assertEquals(TARGET, payload.flavorTarget());
        FlavorCooldowns.clear(); // the crit's line put the target on cooldown
        SaveFeedbackPayload save = buildSave(true, Rules.DEFAULTS, 100, 1);
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
    void saveKillOffCooldownGetsFlavorAtTheKillChance() {
        assertTrue(buildSave(true, Rules.DEFAULTS, 100, 1).flavorKey().isPresent());
        FlavorCooldowns.clear();
        assertTrue(buildSave(true, Rules.DEFAULTS, 100, 90).flavorKey().isEmpty());
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
