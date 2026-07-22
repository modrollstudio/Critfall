package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

class CombatTextTest {

    @Test
    void fumbleFallbackIncludesConsequenceText() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.FUMBLE,
                1,
                4,
                14,
                0,
                "1d8",
                true,
                Optional.empty(),
                List.of(ConsequenceLine.of(ConsequenceLine.DURABILITY_BROKEN)));
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.contains("FUMBLE"), text);
        assertTrue(text.contains("weapon"), text);
        assertFalse(text.contains("durability.broken"), text);
    }

    @Test
    void dryRunActionBarIsPrefixed() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.MISS, 1, 5, 14, 0, "2d6", false, Optional.empty(), List.of(), true);
        assertTrue(CombatText.actionBar(payload).getString().startsWith("dry-run · "), "expected dry-run prefix");
    }

    @Test
    void savedNatOneReadsAsNoFumble() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.MISS, 1, 4, 14, 0, "1d8", true, Optional.empty(), List.of());
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.toUpperCase().contains("NAT 1"), text);
        assertTrue(text.toLowerCase().contains("no fumble"), text);
    }

    @Test
    void advantageShowsBothDiceAndTheKeptOne() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                18,
                22,
                14,
                5,
                "1d6+1",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.ADVANTAGE,
                OptionalInt.of(7),
                0);
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.startsWith("d20 adv 7/18 → 18+4=22 vs AC 14"), text);
    }

    @Test
    void disadvantageShowsBothDiceAndTheKeptOne() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.MISS,
                4,
                4,
                14,
                0,
                "1d6",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.DISADVANTAGE,
                OptionalInt.of(19),
                0);
        assertTrue(CombatText.actionBar(payload).getString().startsWith("d20 dis 19/4 → 4+0=4"), "was: " + payload);
    }

    @Test
    void normalRollStaysTerse() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6+1", true, Optional.empty(), List.of());
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.startsWith("d20 13+3=16 vs AC 10"), text);
        assertFalse(text.contains("/"), "a normal roll must not render a second die: " + text);
    }

    @Test
    void defenderAcBonusIsShownAsASplit() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.MISS,
                13,
                15,
                16,
                0,
                "1d6",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                2);
        assertTrue(CombatText.actionBar(payload).getString().contains("vs AC 16 (14+2)"), "expected the AC split");
    }

    @Test
    void negativeDefenderAcBonusIsShownAsASplit() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                8,
                8,
                8,
                3,
                "1d6",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                -2);
        assertTrue(CombatText.actionBar(payload).getString().contains("vs AC 8 (10-2)"), "expected the AC split");
    }

    @Test
    void zeroDefenderAcBonusIsNotSplit() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6+1", true, Optional.empty(), List.of());
        assertFalse(CombatText.actionBar(payload).getString().contains("("), "no modifier, no split");
    }

    @Test
    void saveWithAdvantageShowsBothDice() {
        SaveFeedbackPayload payload = new SaveFeedbackPayload(
                17,
                19,
                13,
                true,
                Rules.SaveOutcome.NEGATE,
                "2d6",
                0,
                false,
                Optional.empty(),
                false,
                RollMode.ADVANTAGE,
                OptionalInt.of(6));
        assertTrue(CombatText.actionBar(payload).getString().startsWith("save d20 adv 6/17 → 17+2=19 vs DC 13"));
    }

    @Test
    void hitShowsDamage() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6+1", true, Optional.empty(), List.of());
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.contains("16"), text);
        assertTrue(text.contains("HIT"), text);
        assertTrue(text.contains("5"), text);
    }
}
