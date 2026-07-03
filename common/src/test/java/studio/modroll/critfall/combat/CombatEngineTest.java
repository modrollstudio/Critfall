package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.RollMode;
import studio.modroll.critfall.dice.SequenceRandom;

class CombatEngineTest {

    private static final DiceExpression D6 = DiceExpression.parse("1d6");

    private static AttackResult resolve(
            SequenceRandom rng, Rules rules, int bonus, int ac, RollMode mode, DiceExpression dice) {
        return CombatEngine.resolveAttack(new DiceRoller(rng), rules, bonus, ac, mode, dice);
    }

    @Test
    void missWhenTotalBelowAc() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(5);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 1, 10, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.MISS, result.outcome());
        assertEquals(5, result.natural());
        assertEquals(6, result.attackTotal());
        assertEquals(10, result.armorClass());
        assertEquals(0, result.damage());
        assertTrue(rng.isExhausted(), "a miss must not roll damage dice");
    }

    @Test
    void hitRollsDamageDice() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(13, 4);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 1, 10, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.HIT, result.outcome());
        assertEquals(13, result.natural());
        assertEquals(14, result.attackTotal());
        assertEquals(4, result.damage());
        assertTrue(rng.isExhausted());
    }

    @Test
    void exactAcIsAHit() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(9, 2);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 1, 10, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.HIT, result.outcome());
        assertEquals(2, result.damage());
    }

    @Test
    void naturalTwentyIsCritWithMaximizedDamage() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(20);
        DiceExpression dice = DiceExpression.parse("2d6+1");
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 10, RollMode.NORMAL, dice);
        assertEquals(AttackOutcome.CRIT, result.outcome());
        assertEquals(13, result.damage(), "crit = maximized dice (2*6+1)");
        assertTrue(rng.isExhausted(), "maximized damage must not consume RNG");
    }

    @Test
    void naturalTwentyHitsEvenAgainstImpossibleAc() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(20);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 50, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.CRIT, result.outcome());
    }

    @Test
    void critsDisabledTurnsNatTwentyIntoNormalHit() {
        Rules noCrits = new Rules(true, true, true, true, false, true, true, true, true, 1, true, true);
        SequenceRandom rng = SequenceRandom.ofDieFaces(20, 3);
        AttackResult result = resolve(rng, noCrits, 0, 50, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.HIT, result.outcome(), "nat20_always_hits still applies");
        assertEquals(3, result.damage(), "damage is rolled, not maximized");
    }

    @Test
    void naturalOneIsFumbleEvenIfTotalWouldHit() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(1);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 20, 5, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.FUMBLE, result.outcome());
        assertEquals(0, result.damage());
        assertTrue(rng.isExhausted());
    }

    @Test
    void fumblesDisabledTurnsNatOneIntoPlainMiss() {
        Rules noFumbles = new Rules(true, true, true, true, true, true, false, true, true, 1, true, true);
        SequenceRandom rng = SequenceRandom.ofDieFaces(1);
        AttackResult result = resolve(rng, noFumbles, 20, 5, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.MISS, result.outcome());
    }

    @Test
    void nat1AlwaysMissesDisabledLetsHighBonusLand() {
        Rules rules = new Rules(true, true, true, true, true, true, true, false, true, 1, true, true);
        SequenceRandom rng = SequenceRandom.ofDieFaces(1, 4);
        AttackResult result = resolve(rng, rules, 20, 5, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.HIT, result.outcome(), "1 + 20 = 21 vs AC 5 hits when the house rule is off");
        assertEquals(4, result.damage());
    }

    @Test
    void advantageKeepsTheHigherD20() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(3, 18, 5);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 15, RollMode.ADVANTAGE, D6);
        assertEquals(AttackOutcome.HIT, result.outcome());
        assertEquals(18, result.natural());
        assertEquals(5, result.damage());
    }

    @Test
    void disadvantageKeepsTheLowerD20() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(3, 18);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 15, RollMode.DISADVANTAGE, D6);
        assertEquals(AttackOutcome.MISS, result.outcome());
        assertEquals(3, result.natural());
    }

    @Test
    void naturalOneOnAdvantageKeptDieIsFumble() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(1, 1);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 5, RollMode.ADVANTAGE, D6);
        assertEquals(AttackOutcome.FUMBLE, result.outcome(), "both dice showed 1 — kept die is a natural 1");
    }

    @Test
    void rolledDamageIsClampedAtZero() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(11, 1);
        DiceExpression dice = DiceExpression.parse("1d4-3");
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 10, RollMode.NORMAL, dice);
        assertEquals(AttackOutcome.HIT, result.outcome());
        assertEquals(0, result.damage(), "1 - 3 = -2 must clamp to 0");
    }

    @Test
    void isHitCoversHitAndCrit() {
        assertTrue(new AttackResult(AttackOutcome.HIT, 10, 10, 5, 3).isHit());
        assertTrue(new AttackResult(AttackOutcome.CRIT, 20, 20, 5, 6).isHit());
        assertTrue(!new AttackResult(AttackOutcome.MISS, 2, 2, 5, 0).isHit());
        assertTrue(!new AttackResult(AttackOutcome.FUMBLE, 1, 1, 5, 0).isHit());
    }
}
