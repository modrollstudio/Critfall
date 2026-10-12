package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload.Kind;

/** The readout for {@link CheckFeedbackPayload}, rendered without the lang file (the English fallbacks). */
class CheckReadoutTest {

    private static String text(CheckFeedbackPayload payload) {
        return CombatText.actionBar(payload).getString();
    }

    private static CheckFeedbackPayload hazardSave(int natural, int total, boolean saved, int damage) {
        return new CheckFeedbackPayload(
                Kind.SAVE,
                "Spike trap",
                natural,
                total,
                13,
                saved,
                false,
                RollMode.NORMAL,
                OptionalInt.empty(),
                "2d6",
                damage);
    }

    private static CheckFeedbackPayload hazardAttack(boolean hit, boolean crit, int damage) {
        return new CheckFeedbackPayload(
                Kind.ATTACK, "Dart trap", 15, 20, 14, hit, crit, RollMode.NORMAL, OptionalInt.empty(), "1d4", damage);
    }

    @Test
    void addOnSaveReadsLikeCritfallsSave() {
        SaveResult save = new SaveResult(8, 13, 13);
        assertEquals("Trap: save d20 8+5=13 vs DC 13 — SAVED", text(CheckFeedbackPayload.save("Trap", save)));
        assertEquals(
                "Trap: save d20 8+2=10 vs DC 13 — FAILED",
                text(CheckFeedbackPayload.save("Trap", new SaveResult(8, 10, 13))));
    }

    @Test
    void checkReadsSuccessOrFailed() {
        assertEquals(
                "Perception: check d20 14+3=17 vs DC 15 — SUCCESS",
                text(CheckFeedbackPayload.check("Perception", new SaveResult(14, 17, 15))));
        assertEquals(
                "Perception: check d20 9-1=8 vs DC 15 — FAILED",
                text(CheckFeedbackPayload.check("Perception", new SaveResult(9, 8, 15))));
    }

    @Test
    void emptyLabelHasNoPrefix() {
        assertEquals(
                "check d20 14+3=17 vs DC 15 — SUCCESS",
                text(CheckFeedbackPayload.check("", new SaveResult(14, 17, 15))));
    }

    @Test
    void advantageShowsBothDice() {
        SaveResult save = new SaveResult(17, 19, 15, new RollDetail(RollMode.ADVANTAGE, 17, OptionalInt.of(4)));
        assertEquals(
                "Stealth: check d20 adv 4/17 → 17+2=19 vs DC 15 — SUCCESS",
                text(CheckFeedbackPayload.check("Stealth", save)));
    }

    @Test
    void hazardSaveShowsTheDamage() {
        assertEquals("Spike trap: save d20 8+2=10 vs DC 13 — FAILED 2d6 = 7", text(hazardSave(8, 10, false, 7)));
        assertEquals("Spike trap: save d20 15+2=17 vs DC 13 — SAVED, half damage 3", text(hazardSave(15, 17, true, 3)));
        assertEquals("Spike trap: save d20 15+2=17 vs DC 13 — SAVED, no damage", text(hazardSave(15, 17, true, 0)));
    }

    @Test
    void hazardAttackReadsLikeAnAttack() {
        assertEquals("Dart trap: d20 15+5=20 vs AC 14 — HIT 1d4 = 3", text(hazardAttack(true, false, 3)));
        assertEquals("Dart trap: d20 15+5=20 vs AC 14 — CRIT! 1d4 = 4", text(hazardAttack(true, true, 4)));
        assertEquals("Dart trap: d20 15+5=20 vs AC 14 — MISS", text(hazardAttack(false, false, 0)));
    }

    @Test
    void factoriesCopyTheResult() {
        SaveResult save = new SaveResult(4, 6, 13, new RollDetail(RollMode.DISADVANTAGE, 4, OptionalInt.of(19)));
        CheckFeedbackPayload payload = CheckFeedbackPayload.save("Gas", save);
        assertEquals(save.roll(), payload.roll());
        assertEquals(2, payload.bonus());
        assertEquals(false, payload.success());
        assertEquals(false, payload.showsDamage());
    }
}
