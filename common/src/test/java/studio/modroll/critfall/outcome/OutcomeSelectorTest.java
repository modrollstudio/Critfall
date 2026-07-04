package studio.modroll.critfall.outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.OutcomeEffect;
import studio.modroll.critfall.data.OutcomeTable;
import studio.modroll.critfall.data.Trigger;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.SequenceRandom;

class OutcomeSelectorTest {

    private static final Trigger NAT_1 = new Trigger.Natural(1);
    private static final Trigger NAT_20 = new Trigger.Natural(20);

    private static AttackResult result(AttackOutcome outcome, int natural, int attackTotal, int armorClass) {
        return new AttackResult(outcome, natural, attackTotal, armorClass, 0);
    }

    @Test
    void natOneFiresOnlyOnConfirmedFumble() {
        assertTrue(OutcomeSelector.triggers(NAT_1, result(AttackOutcome.FUMBLE, 1, 4, 12)));
        assertFalse(
                OutcomeSelector.triggers(NAT_1, result(AttackOutcome.MISS, 1, 4, 12)),
                "a saved/suppressed/disabled nat 1 is a plain miss — no consequences");
        assertFalse(
                OutcomeSelector.triggers(NAT_1, result(AttackOutcome.HIT, 1, 21, 12)),
                "nat1_always_misses off can land a nat 1 — that is not a fumble");
    }

    @Test
    void natTwentyFiresOnlyOnCrit() {
        assertTrue(OutcomeSelector.triggers(NAT_20, result(AttackOutcome.CRIT, 20, 23, 12)));
        assertFalse(
                OutcomeSelector.triggers(NAT_20, result(AttackOutcome.HIT, 20, 23, 12)),
                "crits disabled turns a nat 20 into a plain hit — no crit effects");
        assertFalse(
                OutcomeSelector.triggers(NAT_20, result(AttackOutcome.CRIT, 19, 22, 12)),
                "a raised crit range crits on 19, but the nat_20 trigger reads the die face");
    }

    @Test
    void missMarginCountsDownFromArmorClass() {
        Trigger missBy5 = new Trigger.MissByAtLeast(5);
        assertTrue(OutcomeSelector.triggers(missBy5, result(AttackOutcome.MISS, 3, 7, 12)), "missed by exactly 5");
        assertFalse(OutcomeSelector.triggers(missBy5, result(AttackOutcome.MISS, 4, 8, 12)), "missed by only 4");
        assertTrue(
                OutcomeSelector.triggers(missBy5, result(AttackOutcome.FUMBLE, 1, 5, 12)),
                "a fumble is also a miss and counts its margin");
        assertFalse(
                OutcomeSelector.triggers(missBy5, result(AttackOutcome.FUMBLE, 1, 21, 12)),
                "a forced nat-1 miss with a huge bonus has no positive margin");
        assertFalse(OutcomeSelector.triggers(missBy5, result(AttackOutcome.HIT, 15, 19, 12)));
    }

    @Test
    void rollRangeMatchesTheNaturalDieOnAnyOutcome() {
        Trigger lowRolls = new Trigger.RollRange(2, 5);
        assertTrue(OutcomeSelector.triggers(lowRolls, result(AttackOutcome.MISS, 2, 6, 12)));
        assertTrue(OutcomeSelector.triggers(lowRolls, result(AttackOutcome.HIT, 5, 15, 12)));
        assertFalse(OutcomeSelector.triggers(lowRolls, result(AttackOutcome.FUMBLE, 1, 5, 12)));
        assertFalse(OutcomeSelector.triggers(lowRolls, result(AttackOutcome.MISS, 6, 10, 12)));
    }

    private static OutcomeTable table(OutcomeTable.WeightedEffect... effects) {
        return new OutcomeTable(ResourceLocation.parse("test:table"), NAT_1, List.of(effects));
    }

    @Test
    void singleEffectTableDrawsNothingFromTheRng() {
        SequenceRandom rng = SequenceRandom.ofDieFaces();
        OutcomeTable single = table(new OutcomeTable.WeightedEffect(new OutcomeEffect.DropWeapon(), 3));
        assertEquals(new OutcomeEffect.DropWeapon(), OutcomeSelector.pick(single, new DiceRoller(rng)));
        assertTrue(rng.isExhausted());
    }

    @Test
    void weightedPickLandsByCumulativeWeight() {
        OutcomeTable weighted = table(
                new OutcomeTable.WeightedEffect(new OutcomeEffect.DamageDurability(), 3),
                new OutcomeTable.WeightedEffect(new OutcomeEffect.HitNearestAlly(OptionalInt.empty()), 1),
                new OutcomeTable.WeightedEffect(new OutcomeEffect.Nothing(), 1));
        assertEquals(
                new OutcomeEffect.DamageDurability(),
                OutcomeSelector.pick(weighted, new DiceRoller(SequenceRandom.ofDieFaces(1))));
        assertEquals(
                new OutcomeEffect.DamageDurability(),
                OutcomeSelector.pick(weighted, new DiceRoller(SequenceRandom.ofDieFaces(3))),
                "face 3 is still within the first entry's weight");
        assertEquals(
                new OutcomeEffect.HitNearestAlly(OptionalInt.empty()),
                OutcomeSelector.pick(weighted, new DiceRoller(SequenceRandom.ofDieFaces(4))));
        assertEquals(
                new OutcomeEffect.Nothing(),
                OutcomeSelector.pick(weighted, new DiceRoller(SequenceRandom.ofDieFaces(5))));
    }

    @Test
    void everyEffectMapsToItsOwnRulesFlag() {
        Rules defaults = Rules.DEFAULTS;
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.Nothing(), defaults));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.DamageDurability(), defaults));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.HitNearestAlly(OptionalInt.empty()), defaults));
        assertFalse(
                OutcomeSelector.enabled(new OutcomeEffect.SelfDamage(Optional.empty()), defaults),
                "self_damage ships disabled");
        assertFalse(OutcomeSelector.enabled(new OutcomeEffect.DropWeapon(), defaults), "drop_weapon ships disabled");
        assertFalse(
                OutcomeSelector.enabled(new OutcomeEffect.Stumble(OptionalInt.empty()), defaults),
                "stumble ships disabled");
        assertTrue(OutcomeSelector.enabled(
                new OutcomeEffect.ApplyEffect(ResourceLocation.parse("minecraft:slowness"), 60, 0), defaults));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.Knockback(1.0), defaults));

        Rules allOff = new Rules(
                defaults.attackRolls(),
                defaults.damageDice(),
                new Rules.Crits(true, Rules.CritRule.MAX_DICE, true, false, false),
                new Rules.Fumbles(
                        true,
                        true,
                        true,
                        10,
                        200,
                        false,
                        Rules.DurabilityMode.SET_TO_1,
                        25,
                        new Rules.HitNearestAlly(false, 4, true, true),
                        new Rules.SelfDamage(true, Rules.SelfDamage.DEFAULTS.dice()),
                        true,
                        new Rules.Stumble(true, 40),
                        Rules.AppliesTo.PLAYERS_AND_MOBS),
                defaults.spells(),
                defaults.fallbacks(),
                defaults.feedback(),
                defaults.balance());
        assertFalse(OutcomeSelector.enabled(new OutcomeEffect.DamageDurability(), allOff));
        assertFalse(OutcomeSelector.enabled(new OutcomeEffect.HitNearestAlly(OptionalInt.empty()), allOff));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.SelfDamage(Optional.empty()), allOff));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.DropWeapon(), allOff));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.Stumble(OptionalInt.empty()), allOff));
        assertFalse(OutcomeSelector.enabled(
                new OutcomeEffect.ApplyEffect(ResourceLocation.parse("minecraft:slowness"), 60, 0), allOff));
        assertFalse(OutcomeSelector.enabled(new OutcomeEffect.Knockback(1.0), allOff));
        assertTrue(OutcomeSelector.enabled(new OutcomeEffect.Nothing(), allOff));
    }
}
