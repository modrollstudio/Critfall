package studio.modroll.critfall.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import studio.modroll.critfall.api.ArmorClassSource;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.HazardAttack;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * AC from more than one mod ({@link ArmorClassSource}) and an AC raised after the roll ({@link
 * studio.modroll.critfall.api.event.PostAttackRollEvent#raiseArmorClass}). The pig has AC 10 and 10 health, the
 * bare handed player hits at +7 for 1d8, the husk at +3 for 1d4+1.
 */
public final class ArmorClassScenarios {

    private ArmorClassScenarios() {}

    /** A player's real hit counts the provider first, then each source in order, each seeing the AC so far. */
    public static void armorClassSourcesCountOnAPlayersHit(GameTestHelper helper) {
        Player player = JumpAttackScenarios.attacker(helper, false);
        Pig pig = spawnPig(helper);
        List<Integer> soFar = new ArrayList<>();
        List<LivingEntity> attackers = new ArrayList<>();
        ArmorClassSource shield = (defender, attacker, armorClass) -> {
            soFar.add(armorClass);
            attackers.add(attacker);
            return 5;
        };
        ArmorClassSource floor = (defender, attacker, armorClass) -> Math.max(0, 18 - armorClass);
        run(
                helper,
                List.of(shield, floor),
                sink -> {
                    // AC 10, the provider's +2, the shield's +5 is 17, the floor lifts it to 18: 10 + 7 = 17 misses.
                    player.attack(pig);
                    expectHealth(helper, pig, pig.getMaxHealth());
                    expectReadout(helper, sink.lastRoll(), "d20 10+7=17 vs AC 18 — MISS");
                },
                10);
        if (!soFar.equals(List.of(12)) || attackers.size() != 1 || attackers.getFirst() != player) {
            helper.fail(
                    "the shield must be asked once, by the player, after the provider, was " + soFar + " " + attackers);
        }
        helper.succeed();
    }

    /**
     * Driven and hazard attacks count the sources too, as does {@link RollService#armorClass}; Critfall's own AC
     * does not change.
     */
    public static void armorClassSourcesCountInDrivenAndHazardAttacks(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnPig(helper);
        RollService.suppress(husk);
        RollService.suppress(pig);
        List<LivingEntity> attackers = new ArrayList<>();
        ArmorClassSource shield = (defender, attacker, armorClass) -> {
            attackers.add(attacker);
            return 5;
        };
        try {
            run(
                    helper,
                    List.of(shield),
                    sink -> {
                        // 10 + 3 = 13 vs AC 17 (10, +2, +5) misses.
                        AttackContext claws = AttackContext.melee(
                                helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                        AttackResult driven = RollService.performAttack(husk, pig, claws);
                        expectArmorClass(helper, driven, AttackOutcome.MISS, 17, 0);
                        // 11 + 5 = 16 vs AC 17 misses.
                        HazardAttack darts =
                                HazardAttack.of("Dart trap", DamageTypes.ARROW, 5, DiceExpression.parse("1d4"));
                        AttackResult hazard = RollService.performHazardAttack(pig, darts);
                        expectArmorClass(helper, hazard, AttackOutcome.MISS, 17, 0);
                        expectHealth(helper, pig, pig.getMaxHealth());
                        if (RollService.armorClass(pig, null) != 17 || RollService.armorClass(pig, husk) != 17) {
                            helper.fail("RollService.armorClass must count the provider and the sources");
                        }
                        if (RollService.effectiveEntity(pig).armorClass() != 10
                                || RollService.armorClassBreakdown(pig).total() != 10) {
                            helper.fail("Critfall's own AC must stay 10");
                        }
                    },
                    10,
                    11);
        } finally {
            RollService.release(husk);
            RollService.release(pig);
        }
        if (!attackers.equals(Arrays.asList(husk, null, null, husk))) {
            helper.fail("the source must get the attacker, null for the hazard, was " + attackers);
        }
        helper.succeed();
    }

    /** A reaction after the roll turns a player's real hit into a miss: no damage, and the readout says why. */
    public static void raisedArmorClassTurnsAPlayersHitIntoAMiss(GameTestHelper helper) {
        Player player = JumpAttackScenarios.attacker(helper, false);
        Pig pig = spawnPig(helper);
        List<AttackOutcome> seen = new ArrayList<>();
        CritfallEvents.onPostAttackRoll(event -> {
            if (event.target() == pig && event.result().isHit()) {
                event.raiseArmorClass(5);
            }
        });
        CritfallEvents.onPostAttackRoll(event -> seen.add(event.result().outcome()));
        try {
            run(
                    helper,
                    List.of(),
                    sink -> {
                        // 3 + 7 = 10 hits the pig's AC 10 (with no provider) and 1d8 rolls 6; AC 15 does not.
                        player.attack(pig);
                        expectHealth(helper, pig, pig.getMaxHealth());
                        expectReadout(helper, sink.lastRoll(), "d20 3+7=10 vs AC 15 (10+5) — MISS");
                    },
                    false,
                    3,
                    6);
        } finally {
            CritfallEvents.clearListeners();
        }
        if (!seen.equals(List.of(AttackOutcome.MISS))) {
            helper.fail("a later listener must see the miss, saw " + seen);
        }
        helper.succeed();
    }

    /** A natural 20 still hits a raised AC, on the driven path too. */
    public static void raisedArmorClassStillLetsANatural20Hit(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnPig(helper);
        RollService.suppress(husk);
        RollService.suppress(pig);
        CritfallEvents.onPostAttackRoll(event -> event.raiseArmorClass(20));
        try {
            run(
                    helper,
                    List.of(),
                    sink -> {
                        // A natural 20 crits for 1d4+1 at its most, 5, whatever the AC.
                        AttackContext claws = AttackContext.melee(
                                helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                        AttackResult result = RollService.performAttack(husk, pig, claws);
                        expectArmorClass(helper, result, AttackOutcome.CRIT, 30, 20);
                        expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                        expectReadout(helper, sink.lastRoll(), "d20 20+3=23 vs AC 30 (10+20) — CRIT!");
                    },
                    false,
                    20);
        } finally {
            CritfallEvents.clearListeners();
            RollService.release(husk);
            RollService.release(pig);
        }
        helper.succeed();
    }

    private static void run(
            GameTestHelper helper,
            List<ArmorClassSource> sources,
            Consumer<CapturingFeedbackSink> action,
            int... faces) {
        run(helper, sources, action, true, faces);
    }

    /** {@code withProvider} registers a provider adding 2 to every AC. */
    private static void run(
            GameTestHelper helper,
            List<ArmorClassSource> sources,
            Consumer<CapturingFeedbackSink> action,
            boolean withProvider,
            int... faces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        if (withProvider) {
            RollService.registerModifierProvider(new ModifierProvider() {
                @Override
                public OptionalInt acModifier(LivingEntity defender, LivingEntity attacker) {
                    return OptionalInt.of(2);
                }
            });
        }
        sources.forEach(RollService::addArmorClassSource);
        try {
            CombatScenarios.withRolls(helper, Rules.DEFAULTS, () -> action.accept(sink), faces);
        } finally {
            sources.forEach(RollService::removeArmorClassSource);
            RollService.clearModifierProvider();
            FeedbackSink.set(previous);
        }
    }

    private static Pig spawnPig(GameTestHelper helper) {
        return CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
    }

    private static void expectArmorClass(
            GameTestHelper helper, AttackResult result, AttackOutcome outcome, int armorClass, int defenderAcBonus) {
        if (result.outcome() != outcome
                || result.armorClass() != armorClass
                || result.defenderAcBonus() != defenderAcBonus) {
            helper.fail("expected a " + outcome + " vs AC " + armorClass + " (bonus " + defenderAcBonus + "), got "
                    + result);
        }
    }

    private static void expectReadout(GameTestHelper helper, RollFeedbackPayload payload, String fragment) {
        String readout = payload == null ? null : CombatText.actionBar(payload).getString();
        if (readout == null || !readout.contains(fragment)) {
            helper.fail("the readout must contain \"" + fragment + "\", was \"" + readout + "\"");
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
