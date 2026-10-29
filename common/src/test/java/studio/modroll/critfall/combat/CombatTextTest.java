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
import studio.modroll.critfall.api.feedback.FlavorName;
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

    @Test
    void carriedCritMultiplierFollowsTheDice() {
        assertTrue(CombatText.actionBar(hit(1.5F)).getString().endsWith("HIT 1d8 = 7 ×1.5"));
        assertTrue(CombatText.actionBar(hit(2.0F)).getString().endsWith("HIT 1d8 = 7 ×2"));
        assertTrue(CombatText.actionBar(hit(1.0F)).getString().endsWith("HIT 1d8 = 7"));
        assertTrue(CombatText.actionBar(hit(1.25F)).getString().endsWith("×1.25"));
    }

    @Test
    void damageTakenFollowsTheRollWhenItDiffers() {
        assertTrue(CombatText.actionBar(taken(12, 1.0F, 7.0F, List.of()))
                .getString()
                .endsWith("HIT 1d10+4 = 12 → 7"));
        assertTrue(CombatText.actionBar(taken(12, 1.0F, 6.8000002F, List.of()))
                .getString()
                .endsWith("= 12 → 6.8"));
        assertTrue(CombatText.actionBar(taken(5, 1.0F, 7.5F, List.of()))
                .getString()
                .endsWith("= 5 → 7.5"));
        assertTrue(CombatText.actionBar(taken(7, 1.5F, 6.0F, List.of()))
                .getString()
                .endsWith("= 7 ×1.5 → 6"));
    }

    @Test
    void damageTakenEqualToTheRollIsNotRepeated() {
        assertTrue(CombatText.actionBar(taken(12, 1.0F, 12.0F, List.of()))
                .getString()
                .endsWith("HIT 1d10+4 = 12"));
        assertTrue(CombatText.actionBar(taken(7, 1.5F, 10.5F, List.of()))
                .getString()
                .endsWith("= 7 ×1.5"));
    }

    @Test
    void unknownDamageTakenIsNotShown() {
        String text = CombatText.actionBar(taken(12, 1.0F, RollFeedbackPayload.TAKEN_UNKNOWN, List.of()))
                .getString();
        assertTrue(text.endsWith("HIT 1d10+4 = 12"), text);
    }

    @Test
    void absorbedHitShowsNothingTaken() {
        assertTrue(CombatText.actionBar(taken(12, 1.0F, 0.0F, List.of()))
                .getString()
                .endsWith("= 12 → 0"));
    }

    @Test
    void resistedHitLeavesItToTheConsequenceLine() {
        String text = CombatText.actionBar(taken(12, 1.0F, 0.0F, List.of(ConsequenceLine.of(ConsequenceLine.RESISTED))))
                .getString();
        assertTrue(text.endsWith("HIT 1d10+4 = 12 — no damage taken"), text);
    }

    @Test
    void failedSaveShowsTheDamageTakenWhenItDiffers() {
        assertTrue(CombatText.actionBar(save(false, 7, 3.0F)).getString().endsWith("FAILED 2d6 = 7 → 3"));
        assertTrue(CombatText.actionBar(save(false, 7, 7.0F)).getString().endsWith("FAILED 2d6 = 7"));
        assertTrue(CombatText.actionBar(save(false, 7, RollFeedbackPayload.TAKEN_UNKNOWN))
                .getString()
                .endsWith("FAILED 2d6 = 7"));
    }

    @Test
    void halvedSaveShowsTheDamageTakenWhenItDiffers() {
        assertTrue(CombatText.actionBar(save(true, 3, 1.2F)).getString().endsWith("SAVED, half damage 3 → 1.2"));
        assertTrue(CombatText.actionBar(save(true, 3, 3.0F)).getString().endsWith("SAVED, half damage 3"));
    }

    private static SaveFeedbackPayload save(boolean saved, int damage, float taken) {
        return new SaveFeedbackPayload(
                8,
                10,
                13,
                saved,
                Rules.SaveOutcome.HALF,
                "2d6",
                damage,
                true,
                Optional.empty(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                FlavorName.NONE,
                FlavorName.NONE,
                taken);
    }

    private static RollFeedbackPayload taken(
            int damage, float critMultiplier, float taken, List<ConsequenceLine> consequences) {
        return new RollFeedbackPayload(
                AttackOutcome.HIT,
                15,
                17,
                10,
                damage,
                "1d10+4",
                true,
                Optional.empty(),
                consequences,
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                0,
                FlavorName.NONE,
                FlavorName.NONE,
                critMultiplier,
                taken);
    }

    private static RollFeedbackPayload hit(float critMultiplier) {
        return new RollFeedbackPayload(
                AttackOutcome.HIT,
                15,
                17,
                10,
                7,
                "1d8",
                true,
                Optional.empty(),
                List.of(),
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                0,
                FlavorName.NONE,
                FlavorName.NONE,
                critMultiplier);
    }

    /** New in 0.2.11: a client or fallback render without the lang entry still reads in English. */
    @Test
    void resistedHitReadsNoDamageTakenWithoutLang() {
        RollFeedbackPayload payload = new RollFeedbackPayload(
                AttackOutcome.HIT,
                15,
                18,
                12,
                9,
                "1d8+4",
                true,
                Optional.empty(),
                List.of(ConsequenceLine.of(ConsequenceLine.RESISTED)));
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.contains("no damage taken"), text);
        assertFalse(text.contains("critfall.consequence"), text);
    }
}
