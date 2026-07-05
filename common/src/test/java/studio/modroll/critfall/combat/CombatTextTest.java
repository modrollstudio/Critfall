package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.feedback.ConsequenceLine;
import studio.modroll.critfall.feedback.RollFeedbackPayload;

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
    void hitShowsDamage() {
        RollFeedbackPayload payload =
                new RollFeedbackPayload(AttackOutcome.HIT, 13, 16, 10, 5, "1d6+1", true, Optional.empty(), List.of());
        String text = CombatText.actionBar(payload).getString();
        assertTrue(text.contains("16"), text);
        assertTrue(text.contains("HIT"), text);
        assertTrue(text.contains("5"), text);
    }
}
