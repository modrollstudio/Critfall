package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.Rules;

class ConsequenceLineTest {

    @Test
    void ofKeyHasNoArg() {
        ConsequenceLine line = ConsequenceLine.of(ConsequenceLine.DROP_WEAPON);
        assertEquals("critfall.consequence.drop_weapon", line.key());
        assertTrue(line.arg().isEmpty());
    }

    @Test
    void ofKeyAndArgCarriesArg() {
        ConsequenceLine line = ConsequenceLine.of(ConsequenceLine.HIT_ALLY, "Villager");
        assertEquals(Optional.of("Villager"), line.arg());
    }

    @Test
    void durabilityKeyDependsOnMode() {
        assertEquals(
                ConsequenceLine.DURABILITY_BROKEN,
                ConsequenceLine.durability(Rules.DurabilityMode.SET_TO_1).key());
        assertEquals(
                ConsequenceLine.DURABILITY_WORN,
                ConsequenceLine.durability(Rules.DurabilityMode.PERCENT_LOSS).key());
    }
}
