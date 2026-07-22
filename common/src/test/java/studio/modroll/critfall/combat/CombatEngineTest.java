package studio.modroll.critfall.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.ContestResult;
import studio.modroll.critfall.api.combat.ContestSide;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.dice.SequenceRandom;
import studio.modroll.critfall.combat.CombatEngine.AttackInput;

class CombatEngineTest {

    private static final DiceExpression D6 = DiceExpression.parse("1d6");

    private static AttackResult resolve(
            SequenceRandom rng, Rules rules, int bonus, int ac, RollMode mode, DiceExpression dice) {
        return CombatEngine.resolveAttack(new DiceRoller(rng), rules, new AttackInput(bonus, ac, mode, dice));
    }

    private static AttackResult resolveWithDefenderBonus(
            SequenceRandom rng, Rules rules, int bonus, int ac, int defenderAcBonus, DiceExpression dice) {
        return CombatEngine.resolveAttack(
                new DiceRoller(rng),
                rules,
                new AttackInput(bonus, ac, RollMode.NORMAL, dice, 20, false, defenderAcBonus));
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
    void defenderAcBonusRaisesTheEffectiveAcIntoAMiss() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(13);
        AttackResult result = resolveWithDefenderBonus(rng, Rules.DEFAULTS, 1, 10, 5, D6);
        assertEquals(AttackOutcome.MISS, result.outcome(), "14 vs effective AC 15 (10+5) misses");
        assertEquals(15, result.armorClass(), "armorClass exposes the effective AC");
        assertEquals(5, result.defenderAcBonus());
        assertEquals(10, result.baseArmorClass());
        assertTrue(rng.isExhausted(), "a miss must not roll damage dice");
    }

    @Test
    void withoutTheDefenderBonusTheSameRollHits() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(13, 4);
        AttackResult result = resolveWithDefenderBonus(rng, Rules.DEFAULTS, 1, 10, 0, D6);
        assertEquals(AttackOutcome.HIT, result.outcome(), "14 vs base AC 10 hits — the +5 is what changed it");
        assertEquals(10, result.armorClass());
        assertEquals(0, result.defenderAcBonus());
        assertEquals(10, result.baseArmorClass());
    }

    @Test
    void negativeDefenderAcBonusTurnsAMissIntoAHit() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(5, 4);
        AttackResult result = resolveWithDefenderBonus(rng, Rules.DEFAULTS, 1, 10, -5, D6);
        assertEquals(
                AttackOutcome.HIT, result.outcome(), "6 vs effective AC 5 (10-5) hits — base AC 10 would have missed");
        assertEquals(5, result.armorClass());
        assertEquals(-5, result.defenderAcBonus());
        assertEquals(10, result.baseArmorClass());
    }

    @Test
    void naturalTwentyStillCritsThroughAnImpossibleDefenderBonus() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(20);
        AttackResult result = resolveWithDefenderBonus(rng, Rules.DEFAULTS, 0, 10, 50, DiceExpression.parse("2d6+1"));
        assertEquals(AttackOutcome.CRIT, result.outcome(), "a natural 20 hits regardless of AC");
        assertEquals(60, result.armorClass());
        assertEquals(50, result.defenderAcBonus());
    }

    @Test
    void naturalOneStillFumblesThroughANegativeDefenderBonus() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(1, 9);
        AttackResult result = resolveWithDefenderBonus(rng, Rules.DEFAULTS, 20, 10, -50, D6);
        assertEquals(AttackOutcome.FUMBLE, result.outcome(), "a natural 1 always misses, then confirms the fumble");
        assertTrue(rng.isExhausted());
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
                new AttackInput(0, 10, RollMode.NORMAL, DiceExpression.parse("2d6+1"), 19, false, 0));
        assertEquals(AttackOutcome.CRIT, result.outcome());
        assertEquals(13, result.damage());
    }

    @Test
    void raisedCritRangeDoesNotAutoHit() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(19);
        AttackResult result = CombatEngine.resolveAttack(
                new DiceRoller(rng), Rules.DEFAULTS, new AttackInput(0, 25, RollMode.NORMAL, D6, 19, false, 0));
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
                base.spells(),
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
                new DiceRoller(rng), Rules.DEFAULTS, new AttackInput(0, 10, RollMode.NORMAL, D6, 20, true, 0));
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

    @Test
    void saveMeetingTheDcSucceeds() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(11);
        SaveResult result = CombatEngine.resolveSave(new DiceRoller(rng), 2, 13);
        assertTrue(result.saved(), "11 + 2 = 13 vs DC 13 — meets it beats it");
        assertEquals(11, result.natural());
        assertEquals(13, result.saveTotal());
        assertTrue(rng.isExhausted(), "a save draws exactly one d20");
    }

    @Test
    void saveBelowTheDcFails() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(10);
        SaveResult result = CombatEngine.resolveSave(new DiceRoller(rng), 2, 13);
        assertFalse(result.saved(), "10 + 2 = 12 vs DC 13 fails");
    }

    @Test
    void contestInitiatorWinsOnHigherTotal() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(15, 10);
        ContestResult result = CombatEngine.resolveContest(new DiceRoller(rng), 2, RollMode.NORMAL, 0, RollMode.NORMAL);
        assertEquals(15, result.initiatorNatural());
        assertEquals(17, result.initiatorTotal());
        assertEquals(10, result.opponentNatural());
        assertEquals(10, result.opponentTotal());
        assertEquals(ContestSide.INITIATOR, result.winner());
        assertTrue(result.initiatorWins());
        assertTrue(rng.isExhausted(), "a contest draws exactly one d20 per side");
    }

    @Test
    void contestOpponentWinsOnHigherTotal() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(4, 18);
        ContestResult result = CombatEngine.resolveContest(new DiceRoller(rng), 3, RollMode.NORMAL, 0, RollMode.NORMAL);
        assertEquals(7, result.initiatorTotal());
        assertEquals(18, result.opponentTotal());
        assertEquals(ContestSide.OPPONENT, result.winner());
        assertFalse(result.initiatorWins());
    }

    @Test
    void contestTieGoesToOpponent() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(10, 8);
        ContestResult result = CombatEngine.resolveContest(new DiceRoller(rng), 0, RollMode.NORMAL, 2, RollMode.NORMAL);
        assertEquals(10, result.initiatorTotal());
        assertEquals(10, result.opponentTotal());
        assertEquals(ContestSide.OPPONENT, result.winner(), "5e default: a tie goes to the opponent");
        assertFalse(result.initiatorWins());
    }

    @Test
    void contestAppliesRollModePerSide() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(3, 18, 12, 4);
        ContestResult result =
                CombatEngine.resolveContest(new DiceRoller(rng), 0, RollMode.ADVANTAGE, 0, RollMode.DISADVANTAGE);
        assertEquals(18, result.initiatorNatural(), "advantage keeps the higher of the initiator's two d20s");
        assertEquals(4, result.opponentNatural(), "disadvantage keeps the lower of the opponent's two d20s");
        assertEquals(ContestSide.INITIATOR, result.winner());
        assertTrue(rng.isExhausted());
    }

    @Test
    void advantageAttackReportsBothNaturalsAndTheKeptOne() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(7, 18, 4);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 4, 14, RollMode.ADVANTAGE, D6);
        assertEquals(RollMode.ADVANTAGE, result.roll().mode());
        assertEquals(18, result.roll().kept());
        assertEquals(OptionalInt.of(7), result.roll().dropped());
        assertEquals(18, result.natural(), "the kept face is the result's natural");
    }

    @Test
    void disadvantageAttackReportsBothNaturalsAndTheKeptOne() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(19, 3);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 0, 14, RollMode.DISADVANTAGE, D6);
        assertEquals(RollMode.DISADVANTAGE, result.roll().mode());
        assertEquals(3, result.roll().kept());
        assertEquals(OptionalInt.of(19), result.roll().dropped());
    }

    @Test
    void normalAttackReportsNoDroppedDie() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(13, 4);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 1, 10, RollMode.NORMAL, D6);
        assertEquals(RollMode.NORMAL, result.roll().mode());
        assertEquals(OptionalInt.empty(), result.roll().dropped());
    }

    @Test
    void attackRollDetailSurvivesADamageAdjustment() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(7, 18, 4);
        AttackResult result = resolve(rng, Rules.DEFAULTS, 4, 14, RollMode.ADVANTAGE, D6);
        assertEquals(result.roll(), result.withDamage(99).roll());
    }

    @Test
    void saveCarriesItsModeAndBothDice() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(6, 17);
        SaveResult result = CombatEngine.resolveSave(new DiceRoller(rng), 2, 13, RollMode.ADVANTAGE);
        assertEquals(RollMode.ADVANTAGE, result.roll().mode());
        assertEquals(17, result.roll().kept());
        assertEquals(OptionalInt.of(6), result.roll().dropped());
        assertEquals(19, result.saveTotal());
        assertTrue(rng.isExhausted());
    }

    @Test
    void plainSaveIsANormalOneDieRoll() {
        SaveResult result = CombatEngine.resolveSave(new DiceRoller(SequenceRandom.ofDieFaces(11)), 2, 13);
        assertEquals(RollMode.NORMAL, result.roll().mode());
        assertEquals(OptionalInt.empty(), result.roll().dropped());
    }

    @Test
    void contestCarriesPerSideModeAndDice() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(3, 18, 12, 4);
        ContestResult result =
                CombatEngine.resolveContest(new DiceRoller(rng), 0, RollMode.ADVANTAGE, 0, RollMode.DISADVANTAGE);
        assertEquals(RollMode.ADVANTAGE, result.initiatorRoll().mode());
        assertEquals(18, result.initiatorRoll().kept());
        assertEquals(OptionalInt.of(3), result.initiatorRoll().dropped());
        assertEquals(RollMode.DISADVANTAGE, result.opponentRoll().mode());
        assertEquals(4, result.opponentRoll().kept());
        assertEquals(OptionalInt.of(12), result.opponentRoll().dropped());
    }

    @Test
    void normalContestSideReportsNoDroppedDie() {
        SequenceRandom rng = SequenceRandom.ofDieFaces(15, 10);
        ContestResult result = CombatEngine.resolveContest(new DiceRoller(rng), 2, RollMode.NORMAL, 0, RollMode.NORMAL);
        assertEquals(OptionalInt.empty(), result.initiatorRoll().dropped());
        assertEquals(OptionalInt.empty(), result.opponentRoll().dropped());
    }

    @Test
    void saveHasNoNaturalTwentyOrOneSpecialCase() {
        assertFalse(
                CombatEngine.resolveSave(new DiceRoller(SequenceRandom.ofDieFaces(20)), 0, 25)
                        .saved(),
                "a natural 20 does not beat an unreachable DC");
        assertTrue(
                CombatEngine.resolveSave(new DiceRoller(SequenceRandom.ofDieFaces(1)), 4, 5)
                        .saved(),
                "a natural 1 still saves when the total meets the DC");
    }
}
