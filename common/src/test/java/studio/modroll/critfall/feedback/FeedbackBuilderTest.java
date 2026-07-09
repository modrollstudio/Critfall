package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.CombatEngine;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.FlavorPool;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.SequenceRandom;

class FeedbackBuilderTest {

    private final UUID target = UUID.randomUUID();

    private static Optional<studio.modroll.critfall.data.FlavorPool> pool() {
        return Optional.of(new studio.modroll.critfall.data.FlavorPool(
                ResourceLocation.parse("test:p"),
                List.of(),
                java.util.Set.of(),
                Map.of(
                        studio.modroll.critfall.data.FlavorPool.CRIT, List.of("crit.line"),
                        studio.modroll.critfall.data.FlavorPool.FUMBLE, List.of("fumble.line"),
                        studio.modroll.critfall.data.FlavorPool.KILL, List.of("kill.line")),
                0));
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
                Rules.DEFAULTS,
                new DiceRoller(SequenceRandom.ofDieFaces()),
                target,
                gameTime);
    }

    private SaveFeedbackPayload buildSave(boolean isKill, Rules rules, long gameTime) {
        CombatEngine.SaveResult save = new CombatEngine.SaveResult(13, 15, 13);
        return FeedbackBuilder.buildSave(
                save,
                isKill,
                Rules.SaveOutcome.HALF,
                "2d6",
                7,
                true,
                pool(),
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
        Optional<FlavorPool> critOnlyPool = Optional.of(new FlavorPool(
                ResourceLocation.parse("test:p"),
                List.of(),
                java.util.Set.of(),
                Map.of(FlavorPool.CRIT, List.of("c")),
                0));
        RollFeedbackPayload payload = FeedbackBuilder.buildAttack(
                new AttackResult(AttackOutcome.HIT, 13, 13, 10, 0),
                true,
                "1d6",
                true,
                List.of(),
                critOnlyPool,
                Rules.DEFAULTS,
                new DiceRoller(SequenceRandom.ofDieFaces()),
                target,
                100);
        assertTrue(payload.flavorKey().isEmpty());
        assertFalse(FlavorCooldowns.isOnCooldown(target, 100, 20));
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
