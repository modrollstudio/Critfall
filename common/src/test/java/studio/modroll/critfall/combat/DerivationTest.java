package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.modroll.critfall.api.dice.DiceExpression;

class DerivationTest {

    @ParameterizedTest
    @CsvSource({
        // armor, toughness, expected AC   (vanilla reference points)
        "0,  0,  10", // pig, unarmored player
        "2,  0,  11", // zombie
        "4,  0,  12", // skeleton wither / leather-ish
        "15, 0,  17", // full iron player
        "20, 8,  22", // full diamond player
        "20, 12, 23", // full netherite player
    })
    void armorClassFromVanillaAttributes(double armor, double toughness, int expected) {
        assertEquals(expected, Derivation.armorClass(armor, toughness));
    }

    @Test
    void armorClassNeverBelowOne() {
        assertTrue(Derivation.armorClass(-100, -100) >= 1);
    }

    @ParameterizedTest
    @CsvSource({
        "0,  0", // no attack attribute
        "2,  1", // small mob
        "3,  1", // zombie
        "7,  3", // stone sword player
        "8,  4", // diamond sword player
        "15, 7", // iron golem heavy hit
        "40, 12", // capped
    })
    void attackBonusScalesWithAttackDamage(double attackDamage, int expected) {
        assertEquals(expected, Derivation.attackBonus(attackDamage));
    }

    @ParameterizedTest
    @CsvSource({
        // flat vanilla damage → dice with average within 0.5
        "0,   1",
        "1,   1",
        "2,   1d4",
        "3,   1d6",
        "4,   1d8",
        "5,   1d10",
        "6,   1d12",
        "7,   2d6",
        "8,   2d6+1",
        "9,   2d8",
        "10,  2d8+1",
        "11,  2d10",
        "12,  2d10+1",
        "13,  2d12",
        "14,  2d12+1",
        "15,  2d12+2",
    })
    void damageDiceLookupTable(double flat, String expected) {
        assertEquals(expected, Derivation.damageDice(flat).toString());
    }

    @Test
    void fractionalDamageRoundsBeforeMapping() {
        assertEquals("1d8", Derivation.damageDice(3.5).toString()); // rounds to 4
        assertEquals("1d6", Derivation.damageDice(3.4).toString());
    }

    @Test
    void averageStaysWithinHalfAPointOfTarget() {
        for (int flat = 1; flat <= 60; flat++) {
            DiceExpression dice = Derivation.damageDice(flat);
            double average = (dice.minValue() + dice.maxValue()) / 2.0;
            assertTrue(
                    Math.abs(average - flat) <= 0.5,
                    "flat " + flat + " mapped to " + dice + " with average " + average);
        }
    }

    @Test
    void derivedDiceNeverRollBelowZero() {
        for (int flat = 0; flat <= 60; flat++) {
            DiceExpression dice = Derivation.damageDice(flat);
            assertTrue(dice.minValue() >= 0, "flat " + flat + " mapped to " + dice + " which can roll negative");
        }
    }
}
