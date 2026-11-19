package studio.modroll.critfall.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.HazardAttack;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.combat.Rules;

class CritfallEventsTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void cleanup() {
        CritfallEvents.clearListeners();
    }

    private static AttackContext ctx() {
        return AttackContext.melee(null, ItemStack.EMPTY);
    }

    @Test
    void preAttackListenerCanMutateBonusAndMode() {
        CritfallEvents.onPreAttackRoll(e -> {
            e.attackBonus(e.attackBonus() + 2);
            e.mode(RollMode.ADVANTAGE);
        });
        PreAttackRollEvent fired = CritfallEvents.firePreAttackRoll(null, null, ctx(), 3, RollMode.NORMAL);
        assertEquals(5, fired.attackBonus());
        assertEquals(RollMode.ADVANTAGE, fired.mode());
        assertFalse(fired.isCanceled());
    }

    @Test
    void preAttackListenerCanCancel() {
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::cancel);
        assertTrue(CritfallEvents.firePreAttackRoll(null, null, ctx(), 0, RollMode.NORMAL)
                .isCanceled());
    }

    @Test
    void postAttackListenerCanAdjustDamageAndVeto() {
        CritfallEvents.onPostAttackRoll(e -> {
            e.finalDamage(e.finalDamage() * 2);
            e.veto();
        });
        AttackResult result = new AttackResult(AttackOutcome.HIT, 15, 18, 12, 6);
        PostAttackRollEvent fired = CritfallEvents.firePostAttackRoll(null, null, ctx(), result);
        assertEquals(12, fired.finalDamage());
        assertTrue(fired.isVetoed());
    }

    @Test
    void raisingTheArmorClassTurnsAHitIntoAMiss() {
        CritfallEvents.onPostAttackRoll(e -> e.raiseArmorClass(5));
        AttackResult[] seenAfter = new AttackResult[1];
        CritfallEvents.onPostAttackRoll(e -> seenAfter[0] = e.result());
        // d20 13 + 3 = 16 hits AC 14; AC 19 does not.
        AttackResult hit = new AttackResult(AttackOutcome.HIT, 13, 16, 14, 0, 7);
        PostAttackRollEvent fired = CritfallEvents.firePostAttackRoll(null, null, ctx(), hit);
        AttackResult result = fired.result();
        assertEquals(AttackOutcome.MISS, result.outcome());
        assertEquals(19, result.armorClass());
        assertEquals(5, result.defenderAcBonus());
        assertEquals(14, result.baseArmorClass());
        assertEquals(0, result.damage());
        assertEquals(0, fired.finalDamage());
        assertEquals(result, seenAfter[0], "later listeners see the miss");
    }

    @Test
    void aRaiseTheTotalStillMeetsKeepsTheHit() {
        CritfallEvents.onPostAttackRoll(e -> {
            e.finalDamage(9);
            e.raiseArmorClass(2);
            e.raiseArmorClass(2);
        });
        AttackResult hit = new AttackResult(AttackOutcome.CRIT, 19, 22, 14, 1, 12);
        PostAttackRollEvent fired = CritfallEvents.firePostAttackRoll(null, null, ctx(), hit);
        assertEquals(AttackOutcome.CRIT, fired.result().outcome());
        assertEquals(18, fired.result().armorClass(), "raises add up");
        assertEquals(5, fired.result().defenderAcBonus());
        assertEquals(12, fired.result().damage());
        assertEquals(9, fired.finalDamage());
    }

    @Test
    void aNatural20StillHitsARaisedArmorClass() {
        AttackResult crit = new AttackResult(AttackOutcome.CRIT, 20, 21, 14, 0, 16);
        PostAttackRollEvent event = new PostAttackRollEvent(null, null, ctx(), crit);
        event.raiseArmorClass(10);
        assertEquals(AttackOutcome.CRIT, event.result().outcome());
        assertEquals(16, event.finalDamage());
    }

    @Test
    void aNatural20MissesARaisedArmorClassWhenTheRuleIsOff() {
        Rules before = RollRuntime.rules();
        RollRuntime.setRules(new Rules(
                before.attackRolls(),
                before.damageDice(),
                new Rules.Crits(true, Rules.CritRule.MAX_DICE, false, true, true),
                before.fumbles(),
                before.spells(),
                before.fallbacks(),
                before.feedback(),
                before.balance()));
        try {
            AttackResult hit = new AttackResult(AttackOutcome.CRIT, 20, 21, 14, 0, 16);
            PostAttackRollEvent event = new PostAttackRollEvent(null, null, ctx(), hit);
            event.raiseArmorClass(10);
            assertEquals(AttackOutcome.MISS, event.result().outcome());
        } finally {
            RollRuntime.setRules(before);
        }
    }

    @Test
    void raisingTheArmorClassOfAMissOnlyRaisesIt() {
        AttackResult miss = new AttackResult(AttackOutcome.FUMBLE, 1, 4, 14, 0, 0);
        PostAttackRollEvent event = new PostAttackRollEvent(null, null, ctx(), miss);
        event.raiseArmorClass(5);
        assertEquals(AttackOutcome.FUMBLE, event.result().outcome());
        assertEquals(19, event.result().armorClass());
        assertThrows(IllegalArgumentException.class, () -> event.raiseArmorClass(-1));
    }

    @Test
    void combatInteractionListenerObservesAttackerTargetAndDelivery() {
        CombatInteractionEvent[] seen = new CombatInteractionEvent[1];
        CritfallEvents.onCombatInteraction(e -> seen[0] = e);
        CritfallEvents.fireCombatInteraction(null, null, null, AttackDelivery.THROWN);
        assertNotNull(seen[0]);
        assertNull(seen[0].attacker());
        assertNull(seen[0].target());
        assertEquals(AttackDelivery.THROWN, seen[0].delivery());
    }

    @Test
    void clearListenersDropsCombatInteractionListeners() {
        boolean[] called = {false};
        CritfallEvents.onCombatInteraction(e -> called[0] = true);
        CritfallEvents.clearListeners();
        CritfallEvents.fireCombatInteraction(null, null, null, AttackDelivery.MELEE);
        assertFalse(called[0]);
    }

    @Test
    void listenerExceptionIsIsolated() {
        CritfallEvents.onCrit(e -> {
            throw new RuntimeException("boom");
        });
        boolean[] second = {false};
        CritfallEvents.onCrit(e -> second[0] = true);
        AttackResult result = new AttackResult(AttackOutcome.CRIT, 20, 24, 12, 7);
        CritfallEvents.fireCrit(null, null, ctx(), result); // must not throw
        assertTrue(second[0]);
    }

    private static HazardAttack darts() {
        return HazardAttack.of("Darts", DamageTypes.ARROW, 5, DiceExpression.parse("1d4"));
    }

    private static RollMode fire(HazardAttack attack) {
        return CritfallEvents.fireHazardRoll(new HazardRollEvent(null, attack)).mode();
    }

    @Test
    void hazardRollListenerSeesTheSaveAndGrantsAdvantage() {
        HazardSave gas = HazardSave.of(
                "Gas vent", DamageTypes.MAGIC, ModifierProvider.CONSTITUTION_SAVE, 13, DiceExpression.parse("2d6"));
        RollMode[] seen = new RollMode[1];
        CritfallEvents.onHazardRoll(e -> {
            seen[0] = e.mode();
            if (e.save().map(HazardSave::saveKey).orElse("").equals(ModifierProvider.CONSTITUTION_SAVE)) {
                e.grantAdvantage();
            }
        });
        HazardRollEvent fired = CritfallEvents.fireHazardRoll(new HazardRollEvent(null, gas));
        assertEquals(RollMode.NORMAL, seen[0]);
        assertEquals(RollMode.ADVANTAGE, fired.mode());
        assertEquals(HazardRollEvent.Kind.SAVE, fired.kind());
        assertEquals("Gas vent", fired.label());
        assertEquals(DamageTypes.MAGIC, fired.damageType());
        assertTrue(fired.attack().isEmpty());
    }

    @Test
    void hazardRollEventStartsFromTheAttacksMode() {
        HazardRollEvent fired =
                CritfallEvents.fireHazardRoll(new HazardRollEvent(null, darts().withMode(RollMode.DISADVANTAGE)));
        assertEquals(HazardRollEvent.Kind.ATTACK, fired.kind());
        assertEquals(RollMode.DISADVANTAGE, fired.mode());
        assertEquals(5, fired.attack().orElseThrow().attackBonus());
    }

    @Test
    void disadvantageAloneRollsWithDisadvantage() {
        CritfallEvents.onHazardRoll(HazardRollEvent::imposeDisadvantage);
        assertEquals(RollMode.DISADVANTAGE, fire(darts()));
    }

    @Test
    void advantageAndDisadvantageRollNormally() {
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        CritfallEvents.onHazardRoll(HazardRollEvent::imposeDisadvantage);
        assertEquals(RollMode.NORMAL, fire(darts()));
    }

    @Test
    void severalAdvantagesStillCancelAgainstOneDisadvantage() {
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        assertEquals(RollMode.ADVANTAGE, fire(darts()));
        CritfallEvents.onHazardRoll(HazardRollEvent::imposeDisadvantage);
        assertEquals(RollMode.NORMAL, fire(darts()));
    }

    @Test
    void theHazardsOwnModeIsOneSource() {
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        assertEquals(RollMode.NORMAL, fire(darts().withMode(RollMode.DISADVANTAGE)));
        assertEquals(RollMode.ADVANTAGE, fire(darts().withMode(RollMode.ADVANTAGE)));
    }

    @Test
    void listenersSeeTheModeSoFar() {
        RollMode[] seen = new RollMode[2];
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        CritfallEvents.onHazardRoll(e -> seen[0] = e.mode());
        CritfallEvents.onHazardRoll(HazardRollEvent::imposeDisadvantage);
        CritfallEvents.onHazardRoll(e -> seen[1] = e.mode());
        fire(darts());
        assertEquals(RollMode.ADVANTAGE, seen[0]);
        assertEquals(RollMode.NORMAL, seen[1]);
    }

    @Test
    void throwingHazardRollListenerKeepsTheOtherSources() {
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        CritfallEvents.onHazardRoll(e -> {
            throw new IllegalStateException("bad listener");
        });
        assertEquals(RollMode.ADVANTAGE, fire(darts()));
    }

    @Test
    void clearListenersDropsHazardRollListeners() {
        CritfallEvents.onHazardRoll(HazardRollEvent::grantAdvantage);
        CritfallEvents.clearListeners();
        assertEquals(RollMode.NORMAL, fire(darts()));
    }

    private static PreAttackRollEvent firePreAttack(RollMode mode) {
        return CritfallEvents.firePreAttackRoll(null, null, ctx(), 3, mode);
    }

    @Test
    void preAttackAdvantageAndDisadvantageCancel() {
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::grantAdvantage);
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::imposeDisadvantage);
        assertEquals(RollMode.NORMAL, firePreAttack(RollMode.NORMAL).mode());
    }

    @Test
    void preAttackTwoAdvantagesAreStillAdvantage() {
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::grantAdvantage);
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::grantAdvantage);
        assertEquals(RollMode.ADVANTAGE, firePreAttack(RollMode.NORMAL).mode());
    }

    @Test
    void preAttackOwnModeCountsAsASource() {
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::imposeDisadvantage);
        assertEquals(RollMode.NORMAL, firePreAttack(RollMode.ADVANTAGE).mode());
        assertEquals(RollMode.DISADVANTAGE, firePreAttack(RollMode.DISADVANTAGE).mode());
    }

    @Test
    void preAttackReplacingTheModeDropsEarlierSources() {
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::grantAdvantage);
        CritfallEvents.onPreAttackRoll(e -> e.mode(RollMode.DISADVANTAGE));
        assertEquals(RollMode.DISADVANTAGE, firePreAttack(RollMode.NORMAL).mode());
    }

    @Test
    void preAttackSourcesAfterAReplacementCombineWithIt() {
        CritfallEvents.onPreAttackRoll(e -> e.mode(RollMode.DISADVANTAGE));
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::grantAdvantage);
        assertEquals(RollMode.NORMAL, firePreAttack(RollMode.NORMAL).mode());
    }

    @Test
    void preAttackBonusesAddUpAndReplacingKeepsWorking() {
        CritfallEvents.onPreAttackRoll(e -> e.addBonus(2));
        CritfallEvents.onPreAttackRoll(e -> e.addBonus(-1));
        assertEquals(4, firePreAttack(RollMode.NORMAL).attackBonus());
        CritfallEvents.onPreAttackRoll(e -> e.attackBonus(10));
        assertEquals(10, firePreAttack(RollMode.NORMAL).attackBonus());
    }

    @Test
    void preAttackRerollOnesIsOffUntilAsked() {
        assertFalse(firePreAttack(RollMode.NORMAL).rerollsOnes());
        CritfallEvents.onPreAttackRoll(PreAttackRollEvent::rerollOnes);
        assertTrue(firePreAttack(RollMode.NORMAL).rerollsOnes());
    }

    @Test
    void hazardRollEventCarriesTheHazardId() {
        ResourceLocation id = ResourceLocation.parse("crawl:trap/darts");
        HazardRollEvent withId = CritfallEvents.fireHazardRoll(new HazardRollEvent(null, darts().withId(id)));
        assertEquals(Optional.of(id), withId.id());
        HazardSave gas = HazardSave.of(
                "Gas", DamageTypes.MAGIC, ModifierProvider.CONSTITUTION_SAVE, 12, DiceExpression.parse("1d6"));
        assertEquals(
                Optional.empty(),
                CritfallEvents.fireHazardRoll(new HazardRollEvent(null, gas)).id());
    }

    @Test
    void hazardRollBonusesAddUpAndRerollIsOffUntilAsked() {
        CritfallEvents.onHazardRoll(e -> e.addBonus(2));
        CritfallEvents.onHazardRoll(e -> e.addBonus(3));
        HazardRollEvent event = CritfallEvents.fireHazardRoll(new HazardRollEvent(null, darts()));
        assertEquals(5, event.bonus());
        assertFalse(event.rerollsOnes());
        CritfallEvents.onHazardRoll(HazardRollEvent::rerollOnes);
        assertTrue(CritfallEvents.fireHazardRoll(new HazardRollEvent(null, darts()))
                .rerollsOnes());
    }
}
