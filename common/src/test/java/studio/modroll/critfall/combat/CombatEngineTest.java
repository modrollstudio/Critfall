package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.CombatEngine.AttackInput;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.RollMode;
import studio.modroll.critfall.dice.SequenceRandom;

class CombatEngineTest {

    private static final DiceExpression D6 = DiceExpression.parse("1d6");

    private static AttackResult resolve(
            SequenceRandom rng, Rules rules, int bonus, int ac, RollMode mode, DiceExpression dice) {
        return CombatEngine.resolveAttack(new DiceRoller(rng), rules, new AttackInput(bonus, ac, mode, dice));
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
    void critRuleDoubleDiceRollsTwiceAddsModifierOnce() {
        Rules rules = TestRules.withCrits(new Rules.Crits(true, Rules.CritRule.DOUBLE_DICE, true, true, true));
        SequenceRandom rng = SequenceRandom.ofDieFaces(20, 3, 4, 5, 6);
        AttackResult result = resolve(rng, rules, 0, 10, RollMode.NORMAL, DiceExpression.parse("2d6+1"));
        assertEquals(AttackOutcome.CRIT, result.outcome());
        assertEquals(19, result.damage(), "(3+4+1) + (5+6) — the +1 counts once");
        assertTrue(rng.isExhausted());
    }

    @Test
    void critRuleDoubleTotalDoublesEverything() {
        Rules rules = TestRules.withCrits(new Rules.Crits(true, Rules.CritRule.DOUBLE_TOTAL, true, true, true));
        SequenceRandom rng = SequenceRandom.ofDieFaces(20, 4);
        AttackResult result = resolve(rng, rules, 0, 10, RollMode.NORMAL, DiceExpression.parse("1d6+2"));
        assertEquals(AttackOutcome.CRIT, result.outcome());
        assertEquals(12, result.damage(), "2 * (4+2)");
    }

    @Test
    void raisedCritRangeCritsOnNineteenWhenItHits() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(19);
        AttackResult result = CombatEngine.resolveAttack(
                new DiceRoller(rng),
                Rules.DEFAULTS,
                new AttackInput(0, 10, RollMode.NORMAL, DiceExpression.parse("2d6+1"), 19, false));
        assertEquals(AttackOutcome.CRIT, result.outcome());
        assertEquals(13, result.damage());
    }

    @Test
    void raisedCritRangeDoesNotAutoHit() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(19);
        AttackResult result = CombatEngine.resolveAttack(
                new DiceRoller(rng), Rules.DEFAULTS, new AttackInput(0, 25, RollMode.NORMAL, D6, 19, false));
        assertEquals(AttackOutcome.MISS, result.outcome(), "only a natural 20 auto-hits");
    }

    @Test
    void damageDiceDisabledDrawsNothingFromTheRng() {
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                false,
                base.crits(),
                base.fumbles(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
        SequenceRandom rng = SequenceRandom.ofDieFaces(13);
        AttackResult result = resolve(rng, rules, 1, 10, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.HIT, result.outcome());
        assertEquals(0, result.damage(), "the vanilla amount applies downstream, no dice are rolled");
        assertTrue(rng.isExhausted(), "damage_dice off must not consume RNG");

        SequenceRandom critRng = SequenceRandom.ofDieFaces(20);
        AttackResult crit = resolve(critRng, rules, 1, 10, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.CRIT, crit.outcome());
        assertEquals(0, crit.damage());
        assertTrue(critRng.isExhausted());
    }

    @Test
    void critsDisabledTurnsNatTwentyIntoNormalHit() {
        Rules noCrits = TestRules.withCrits(new Rules.Crits(false, Rules.CritRule.MAX_DICE, true, true, true));
        SequenceRandom rng = SequenceRandom.ofDieFaces(20, 3);
        AttackResult result = resolve(rng, noCrits, 0, 50, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.HIT, result.outcome(), "nat20_always_hits still applies");
        assertEquals(3, result.damage(), "damage is rolled, not maximized");
    }

    @Test
    void naturalOneFumblesWhenConfirmationFails() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(1, 9);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 20, 5, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.FUMBLE, result.outcome(), "confirmation 9 < DC 10 fails — fumble confirmed");
        assertEquals(0, result.damage());
        assertTrue(rng.isExhausted());
    }

    @Test
    void naturalOneIsPlainMissWhenConfirmationSucceeds() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(1, 15);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 20, 5, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.MISS, result.outcome(), "confirmation 15 >= DC 10 saves the fumble");
        assertTrue(rng.isExhausted());
    }

    @Test
    void confirmationDisabledFumblesImmediately() {
        Rules rules = TestRules.withFumbles(TestRules.fumbles(true, true, false, 10));
        SequenceRandom rng = SequenceRandom.ofDieFaces(1);
        AttackResult result = resolve(rng, rules, 0, 10, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.FUMBLE, result.outcome());
        assertTrue(rng.isExhausted(), "no confirmation die may be drawn");
    }

    @Test
    void fumbleOnCooldownIsPlainMissWithoutConfirmationRoll() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(1);
        AttackResult result = CombatEngine.resolveAttack(
                new DiceRoller(rng), Rules.DEFAULTS, new AttackInput(0, 10, RollMode.NORMAL, D6, 20, true));
        assertEquals(AttackOutcome.MISS, result.outcome());
        assertTrue(rng.isExhausted(), "cooldown must short-circuit before the confirmation roll");
    }

    @Test
    void fumblesDisabledTurnsNatOneIntoPlainMiss() {
        Rules noFumbles = TestRules.withFumbles(TestRules.fumbles(false, true, false, 10));
        SequenceRandom rng = SequenceRandom.ofDieFaces(1);
        AttackResult result = resolve(rng, noFumbles, 20, 5, RollMode.NORMAL, D6);
        assertEquals(AttackOutcome.MISS, result.outcome());
        assertTrue(rng.isExhausted());
    }

    @Test
    void nat1AlwaysMissesDisabledLetsHighBonusLand() {
        Rules rules = TestRules.withFumbles(TestRules.fumbles(true, false, true, 10));
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
        SequenceRandom rng = SequenceRandom.ofDieFaces(1, 1, 3);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 5, RollMode.ADVANTAGE, D6);
        assertEquals(AttackOutcome.FUMBLE, result.outcome(), "both dice showed 1, confirmation 3 fails vs DC 10");
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
