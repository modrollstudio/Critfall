package studio.modroll.critfall.api.combat;

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

    @Test
    void defenderAcBonusDefaultsToZeroOnTheConvenienceConstructor() {
        AttackResult result = new AttackResult(AttackOutcome.HIT, 15, 18, 12, 6);
        assertEquals(0, result.defenderAcBonus());
        assertEquals(12, result.baseArmorClass());
    }

    @Test
    void baseArmorClassBacksOutTheSituationalBonus() {
        AttackResult result = new AttackResult(AttackOutcome.MISS, 12, 16, 19, 5, 0);
        assertEquals(19, result.armorClass(), "armorClass is the effective AC the roll faced");
        assertEquals(5, result.defenderAcBonus());
        assertEquals(14, result.baseArmorClass(), "AC 14 (+5)");
    }

    @Test
    void withDamagePreservesTheDefenderAcBonus() {
        AttackResult base = new AttackResult(AttackOutcome.HIT, 15, 18, 19, 5, 6);
        AttackResult adjusted = base.withDamage(10);
        assertEquals(10, adjusted.damage());
        assertEquals(19, adjusted.armorClass());
        assertEquals(5, adjusted.defenderAcBonus());
        assertEquals(14, adjusted.baseArmorClass());
    }
}
