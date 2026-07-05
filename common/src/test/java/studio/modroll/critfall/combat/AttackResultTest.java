package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AttackResultTest {

    @Test
    void withDamageReplacesOnlyDamage() {
        AttackResult base = new AttackResult(AttackOutcome.HIT, 15, 18, 12, 6);
        AttackResult adjusted = base.withDamage(10);
        assertEquals(10, adjusted.damage());
        assertEquals(AttackOutcome.HIT, adjusted.outcome());
        assertEquals(15, adjusted.natural());
        assertEquals(18, adjusted.attackTotal());
        assertEquals(12, adjusted.armorClass());
        assertTrue(adjusted.isHit());
    }
}
