package studio.modroll.critfall.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.ContestContext;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.ContestResult;
import studio.modroll.critfall.api.combat.ContestSide;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.event.CombatInteractionEvent;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * Drives combat purely through the public API (PLAN §12): the automatic damage interception is
 * suppressed, and {@link RollService#performAttack} rolls, applies damage, and reports the result.
 * Scripted RNG exhaustion proves exactly one roll happens (no double-roll from the auto pipeline).
 */
public final class ApiScenarios {

    private static final String TEMPLATE = "empty";
    private static final float VANILLA_HIT = 3.0F;

    public static void drivenHitAppliesRolledDamageOnce(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // 13 + 3 (husk profile) = 16 vs AC 10 (pig) -> hit; 1d6+1 rolls 4 -> 5 damage.
                    AttackContext ctx = AttackContext.melee(
                            helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                    AttackResult result = RollService.performAttack(husk, pig, ctx);
                    if (result.outcome() != AttackOutcome.HIT) {
                        helper.fail("expected HIT, got " + result.outcome());
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                13,
                4);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void drivenMissAppliesNoDamage(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // 5 + 3 = 8 vs AC 10 -> miss; no damage dice drawn.
                    AttackContext ctx = AttackContext.melee(
                            helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                    AttackResult result = RollService.performAttack(husk, pig, ctx);
                    if (result.outcome() != AttackOutcome.MISS) {
                        helper.fail("expected MISS, got " + result.outcome());
                    }
                    expectHealth(helper, pig, pig.getMaxHealth());
                },
                5);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void defenderAcBonusTurnsHitIntoMiss(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // 13 + 3 = 16 would beat the pig's base AC 10, but +7 cover lifts it to 17 -> miss.
                    AttackContext ctx = AttackContext.melee(
                                    helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem())
                            .withDefenderAcBonus(7);
                    AttackResult result = RollService.performAttack(husk, pig, ctx);
                    if (result.outcome() != AttackOutcome.MISS) {
                        helper.fail("expected MISS, got " + result.outcome());
                    }
                    if (result.armorClass() != 17 || result.defenderAcBonus() != 7 || result.baseArmorClass() != 10) {
                        helper.fail("expected AC 10 (+7) = 17, got " + result.baseArmorClass() + " (+"
                                + result.defenderAcBonus() + ") = " + result.armorClass());
                    }
                    expectHealth(helper, pig, pig.getMaxHealth());
                },
                13);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void negativeDefenderAcBonusTurnsMissIntoHit(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // 5 + 3 = 8 would miss the pig's base AC 10, but -2 (flanked) drops it to 8 -> hit; 1d6+1 rolls 4
                    // -> 5.
                    AttackContext ctx = AttackContext.melee(
                                    helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem())
                            .withDefenderAcBonus(-2);
                    AttackResult result = RollService.performAttack(husk, pig, ctx);
                    if (result.outcome() != AttackOutcome.HIT) {
                        helper.fail("expected HIT, got " + result.outcome());
                    }
                    if (result.armorClass() != 8 || result.defenderAcBonus() != -2 || result.baseArmorClass() != 10) {
                        helper.fail("expected AC 10 (-2) = 8, got " + result.baseArmorClass() + " ("
                                + result.defenderAcBonus() + ") = " + result.armorClass());
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                5,
                4);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void suppressedTargetIgnoredByAutoPipeline(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(pig);
        // No rolls scripted: if the auto pipeline rolled, ScriptedRandom would throw. Vanilla applies.
        Rules before = RollRuntime.rules();
        RollRuntime.setRoller(new DiceRoller(ScriptedRandom.ofDieFaces()));
        RollRuntime.setRules(Rules.DEFAULTS);
        FumbleCooldowns.clear();
        try {
            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        } finally {
            RollRuntime.setRoller(new DiceRoller(new Random()));
            RollRuntime.setRules(before);
            cleanup(husk, pig);
        }
        helper.succeed();
    }

    public static void postAttackListenerCanZeroOutDamage(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        CritfallEvents.onPostAttackRoll(e -> e.finalDamage(0));
        try {
            withRolls(
                    helper,
                    () -> {
                        // Hits (13+3 vs 10) and rolls damage (4), but the listener zeroes it -> no health lost.
                        AttackContext ctx = AttackContext.melee(
                                helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                        RollService.performAttack(husk, pig, ctx);
                        expectHealth(helper, pig, pig.getMaxHealth());
                    },
                    13,
                    4);
        } finally {
            CritfallEvents.clearListeners();
            cleanup(husk, pig);
        }
        helper.succeed();
    }

    public static void drivenAttackMatchesAutoPipelineOnArmoredTarget(GameTestHelper helper) {
        Husk attacker = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk autoTarget = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 3, 3);
        Husk apiTarget = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 3, 1);
        autoTarget.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        apiTarget.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float autoBefore = autoTarget.getHealth();
        float apiBefore = apiTarget.getHealth();
        withRolls(
                helper,
                () -> {
                    // Automatic pipeline: nat 20 -> crit for exactly 7 (1d6+1 maxed), armor bypassed.
                    autoTarget.hurt(helper.getLevel().damageSources().mobAttack(attacker), VANILLA_HIT);
                },
                20);
        RollService.suppress(attacker);
        RollService.suppress(apiTarget);
        withRolls(
                helper,
                () -> {
                    // API path, same nat 20: the hurt must not double-dip vanilla armor on top of AC.
                    AttackContext ctx = AttackContext.melee(
                            helper.getLevel().damageSources().mobAttack(attacker), attacker.getMainHandItem());
                    AttackResult result = RollService.performAttack(attacker, apiTarget, ctx);
                    if (result.outcome() != AttackOutcome.CRIT) {
                        helper.fail("expected CRIT, got " + result.outcome());
                    }
                },
                20);
        float autoLost = autoBefore - autoTarget.getHealth();
        float apiLost = apiBefore - apiTarget.getHealth();
        if (Math.abs(autoLost - apiLost) > 0.001F) {
            helper.fail("API attack lost " + apiLost + " but automatic attack lost " + autoLost
                    + " — armor double-dip on the API path");
        }
        if (Math.abs(apiLost - 7.0F) > 0.001F) {
            helper.fail("armored target must take the full rolled 7, but lost " + apiLost);
        }
        cleanup(attacker, apiTarget);
        helper.succeed();
    }

    public static void drivenAttackKeepsArmorReductionWhenBypassFlagOff(GameTestHelper helper) {
        Husk attacker = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk armored = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 3, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float before = armored.getHealth();
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                new Rules.Balance(1.0, false));
        RollService.suppress(attacker);
        RollService.suppress(armored);
        try {
            CombatScenarios.withRolls(
                    helper,
                    rules,
                    () -> {
                        AttackContext ctx = AttackContext.melee(
                                helper.getLevel().damageSources().mobAttack(attacker), attacker.getMainHandItem());
                        RollService.performAttack(attacker, armored, ctx);
                        float lost = before - armored.getHealth();
                        if (lost <= 0.0F || lost >= 7.0F) {
                            helper.fail("with the bypass flag off, vanilla armor must reduce the 7-damage crit, but "
                                    + lost + " was lost");
                        }
                    },
                    20);
        } finally {
            cleanup(attacker, armored);
        }
        helper.succeed();
    }

    public static void combatInteractionFiresOnNormalHit(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        List<CombatInteractionEvent> seen = new ArrayList<>();
        CritfallEvents.onCombatInteraction(seen::add);
        try {
            withRolls(
                    helper,
                    () -> {
                        // 13 + 3 = 16 vs AC 10 -> hit for 5.
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                        expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                        expectInteraction(helper, seen, husk, pig, AttackDelivery.MELEE);
                    },
                    13,
                    4);
        } finally {
            CritfallEvents.clearListeners();
            cleanup(husk, pig);
        }
        helper.succeed();
    }

    public static void combatInteractionFiresWhenRollCancelsDamage(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        List<CombatInteractionEvent> seen = new ArrayList<>();
        CritfallEvents.onCombatInteraction(seen::add);
        try {
            withRolls(
                    helper,
                    () -> {
                        // 5 + 3 = 8 vs AC 10 -> miss; the damage is cancelled.
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                        expectHealth(helper, pig, pig.getMaxHealth());
                        expectInteraction(helper, seen, husk, pig, AttackDelivery.MELEE);
                    },
                    5);
        } finally {
            CritfallEvents.clearListeners();
            cleanup(husk, pig);
        }
        helper.succeed();
    }

    public static void combatInteractionFiresBeforeListenerCancel(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        List<CombatInteractionEvent> seen = new ArrayList<>();
        boolean[] firedBeforePreAttack = {false};
        CritfallEvents.onCombatInteraction(seen::add);
        CritfallEvents.onPreAttackRoll(e -> {
            firedBeforePreAttack[0] = !seen.isEmpty();
            e.cancel();
        });
        try {
            // No faces scripted: a pre-cancel draws no die.
            withRolls(helper, () -> {
                pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                expectHealth(helper, pig, pig.getMaxHealth());
                expectInteraction(helper, seen, husk, pig, AttackDelivery.MELEE);
                if (!firedBeforePreAttack[0]) {
                    helper.fail("the interaction event must fire before PreAttackRollEvent");
                }
            });
        } finally {
            CritfallEvents.clearListeners();
            cleanup(husk, pig);
        }
        helper.succeed();
    }

    public static void combatInteractionFiresForSuppressedParticipants(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        List<CombatInteractionEvent> seen = new ArrayList<>();
        CritfallEvents.onCombatInteraction(seen::add);
        try {
            // No faces scripted: the auto pipeline stands down, vanilla applies.
            withRolls(helper, () -> {
                pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
                expectInteraction(helper, seen, husk, pig, AttackDelivery.MELEE);
            });
        } finally {
            CritfallEvents.clearListeners();
            cleanup(husk, pig);
        }
        helper.succeed();
    }

    public static void drivenAttacksBypassInvulnerabilityFrames(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // Two driven attacks in the same tick, each 13 + 3 = 16 vs AC 10 -> hit, die 2
                    // -> 3 damage. The second lands inside the target's hurt cooldown and must not
                    // be swallowed by it.
                    AttackContext ctx = AttackContext.melee(
                            helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                    AttackResult first = RollService.performAttack(husk, pig, ctx);
                    AttackResult second = RollService.performAttack(husk, pig, ctx);
                    if (first.outcome() != AttackOutcome.HIT || second.outcome() != AttackOutcome.HIT) {
                        helper.fail("expected two HITs, got " + first.outcome() + " and " + second.outcome());
                    }
                    expectHealth(helper, pig, pig.getMaxHealth() - 6.0F);
                },
                13,
                2,
                13,
                2);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void vanillaDamageStillRespectsInvulnerabilityFrames(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        // No faces scripted: suppressed participants take vanilla damage, no rolls.
        withRolls(helper, () -> {
            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
            boolean second = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
            if (second) {
                helper.fail("a second vanilla hit inside the hurt cooldown must be swallowed");
            }
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        });
        cleanup(husk, pig);
        helper.succeed();
    }

    /** Installs a loader damage-event listener observing each hurt entity; close to deactivate. */
    @FunctionalInterface
    public interface DamageObserverInstaller {
        AutoCloseable install(Consumer<LivingEntity> observer);
    }

    public static void listenerDetectsDrivenDamage(GameTestHelper helper, DamageObserverInstaller installer) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        List<Boolean> driven = new ArrayList<>();
        AutoCloseable observer = installer.install(target -> {
            if (target == pig) {
                driven.add(RollService.isDrivenDamage(target));
            }
        });
        try {
            withRolls(
                    helper,
                    () -> {
                        // Vanilla hit first (suppressed -> no roll), then a driven attack:
                        // 13 + 3 = 16 vs AC 10 -> hit, die 4 -> 5 damage.
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                        AttackContext ctx = AttackContext.melee(
                                helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                        RollService.performAttack(husk, pig, ctx);
                    },
                    13,
                    4);
        } finally {
            close(observer);
            cleanup(husk, pig);
        }
        if (RollService.isDrivenDamage(pig)) {
            helper.fail("isDrivenDamage must be false outside a driven hurt");
        }
        if (!List.of(false, true).equals(driven)) {
            helper.fail("expected the listener to see [non-driven, driven] but saw " + driven);
        }
        helper.succeed();
    }

    public static void contestResolvesWinnerAndTotals(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    // 15+2=17 beats 10+0=10.
                    ContestResult result = RollService.contest(husk, pig, ContestContext.of(2, 0));
                    if (result.winner() != ContestSide.INITIATOR) {
                        helper.fail("expected INITIATOR to win, got " + result.winner());
                    }
                    if (result.initiatorTotal() != 17 || result.opponentTotal() != 10) {
                        helper.fail("unexpected totals: " + result.initiatorTotal() + " vs " + result.opponentTotal());
                    }
                },
                15,
                10);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void contestTieGoesToOpponent(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    // Both total 10 -> tie goes to the opponent.
                    ContestResult result = RollService.contest(husk, pig, ContestContext.of(0, 2));
                    if (result.winner() != ContestSide.OPPONENT || result.initiatorWins()) {
                        helper.fail("a tie must go to the opponent, got " + result.winner());
                    }
                },
                10,
                8);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void contestAppliesRollModePerSide(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    // Advantage keeps 18 (of 3/18); disadvantage keeps 4 (of 12/4).
                    ContestContext ctx = ContestContext.of(0, 0)
                            .withInitiatorMode(RollMode.ADVANTAGE)
                            .withOpponentMode(RollMode.DISADVANTAGE);
                    ContestResult result = RollService.contest(husk, pig, ctx);
                    if (result.initiatorNatural() != 18 || result.opponentNatural() != 4) {
                        helper.fail("per-side roll mode not applied: kept " + result.initiatorNatural() + " and "
                                + result.opponentNatural());
                    }
                    if (result.winner() != ContestSide.INITIATOR) {
                        helper.fail("expected INITIATOR to win, got " + result.winner());
                    }
                },
                3,
                18,
                12,
                4);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void drivenAdvantageResultReportsBothDice(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // Advantage keeps 18 (of 7/18); 18 + 3 = 21 vs AC 10 -> hit, 1d6+1 rolls 4 -> 5.
                    AttackContext ctx = AttackContext.melee(
                                    helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem())
                            .withMode(RollMode.ADVANTAGE);
                    AttackResult result = RollService.performAttack(husk, pig, ctx);
                    expectRollDetail(helper, result.roll(), RollMode.ADVANTAGE, 18, 7);
                    if (result.natural() != 18) {
                        helper.fail("the kept face must be the result's natural, got " + result.natural());
                    }
                },
                7,
                18,
                4);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void drivenDisadvantageResultReportsBothDice(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        withRolls(
                helper,
                () -> {
                    // Disadvantage keeps 3 (of 19/3); 3 + 3 = 6 vs AC 10 -> miss, no damage dice drawn.
                    AttackContext ctx = AttackContext.melee(
                                    helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem())
                            .withMode(RollMode.DISADVANTAGE);
                    AttackResult result = RollService.performAttack(husk, pig, ctx);
                    expectRollDetail(helper, result.roll(), RollMode.DISADVANTAGE, 3, 19);
                    if (result.outcome() != AttackOutcome.MISS) {
                        helper.fail("expected MISS, got " + result.outcome());
                    }
                },
                19,
                3);
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void rollDetailAndAcSplitReachTheFeedbackPayload(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            withRolls(
                    helper,
                    () -> {
                        // Advantage keeps 18; 18 + 3 = 21 vs AC 10 (+4) = 14 -> hit, 1d6+1 rolls 4 -> 5.
                        AttackContext ctx = AttackContext.melee(
                                        helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem())
                                .withMode(RollMode.ADVANTAGE)
                                .withDefenderAcBonus(4);
                        RollService.performAttack(husk, pig, ctx);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null) {
                            helper.fail("the driven attack dispatched no feedback payload");
                            return;
                        }
                        expectRollDetail(helper, payload.roll(), RollMode.ADVANTAGE, 18, 7);
                        if (payload.defenderAcBonus() != 4 || payload.armorClass() != 14) {
                            helper.fail("expected AC 10 (+4) = 14 on the payload, got " + payload.baseArmorClass()
                                    + " (+" + payload.defenderAcBonus() + ") = " + payload.armorClass());
                        }
                        String readout = CombatText.actionBar(payload).getString();
                        if (!readout.contains("7/18") || !readout.contains("vs AC 14 (10+4)")) {
                            helper.fail("the readout must show both dice and the AC split, was: " + readout);
                        }
                    },
                    7,
                    18,
                    4);
        } finally {
            FeedbackSink.set(previous);
        }
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void plainDrivenAttackPayloadCarriesNoRollDetail(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            withRolls(
                    helper,
                    () -> {
                        AttackContext ctx = AttackContext.melee(
                                helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
                        RollService.performAttack(husk, pig, ctx);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null) {
                            helper.fail("the driven attack dispatched no feedback payload");
                            return;
                        }
                        if (payload.rollMode() != RollMode.NORMAL
                                || payload.droppedNatural().isPresent()
                                || payload.defenderAcBonus() != 0) {
                            helper.fail("a plain attack must report a normal one-die roll and no AC modifier, was "
                                    + payload.roll() + " / " + payload.defenderAcBonus());
                        }
                        String readout = CombatText.actionBar(payload).getString();
                        if (readout.contains("(")) {
                            helper.fail("no modifier, no AC split, was: " + readout);
                        }
                    },
                    13,
                    4);
        } finally {
            FeedbackSink.set(previous);
        }
        cleanup(husk, pig);
        helper.succeed();
    }

    public static void savingThrowWithAdvantageReportsBothDice(GameTestHelper helper) {
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    SaveResult save = RollService.savingThrow(pig, 2, 13, RollMode.ADVANTAGE);
                    expectRollDetail(helper, save.roll(), RollMode.ADVANTAGE, 17, 6);
                    if (!save.saved() || save.saveTotal() != 19) {
                        helper.fail("expected 17 + 2 = 19 vs DC 13 to save, got " + save.saveTotal());
                    }
                },
                6,
                17);
        helper.succeed();
    }

    public static void contestCarriesPerSideRollDetail(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    ContestContext ctx = ContestContext.of(0, 0)
                            .withInitiatorMode(RollMode.ADVANTAGE)
                            .withOpponentMode(RollMode.DISADVANTAGE);
                    ContestResult result = RollService.contest(husk, pig, ctx);
                    expectRollDetail(helper, result.initiatorRoll(), RollMode.ADVANTAGE, 18, 3);
                    expectRollDetail(helper, result.opponentRoll(), RollMode.DISADVANTAGE, 4, 12);
                },
                3,
                18,
                12,
                4);
        cleanup(husk, pig);
        helper.succeed();
    }

    private static void expectRollDetail(
            GameTestHelper helper, RollDetail detail, RollMode mode, int kept, int dropped) {
        if (detail.mode() != mode || detail.kept() != kept || detail.dropped().orElse(-1) != dropped) {
            helper.fail("expected " + mode + " keeping " + kept + " over " + dropped + ", got " + detail);
        }
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void expectInteraction(
            GameTestHelper helper,
            List<CombatInteractionEvent> seen,
            LivingEntity attacker,
            LivingEntity target,
            AttackDelivery delivery) {
        if (seen.size() != 1) {
            helper.fail("expected exactly one combat interaction event, got " + seen.size());
        }
        CombatInteractionEvent event = seen.get(0);
        if (event.attacker() != attacker || event.target() != target) {
            helper.fail("the interaction event carried the wrong participants");
        }
        if (event.delivery() != delivery) {
            helper.fail("expected delivery " + delivery + " but was " + event.delivery());
        }
        if (event.source() == null) {
            helper.fail("the interaction event must carry the damage source");
        }
    }

    private static void withRolls(GameTestHelper helper, Runnable action, int... faces) {
        ScriptedRandom scripted = ScriptedRandom.ofDieFaces(faces);
        Rules before = RollRuntime.rules();
        RollRuntime.setRoller(new DiceRoller(scripted));
        RollRuntime.setRules(Rules.DEFAULTS);
        FumbleCooldowns.clear();
        try {
            action.run();
            if (!scripted.isExhausted()) {
                helper.fail("more or fewer dice were rolled than scripted (a double roll would exhaust early)");
            }
        } finally {
            RollRuntime.setRoller(new DiceRoller(new Random()));
            RollRuntime.setRules(before);
            FumbleCooldowns.clear();
        }
    }

    private static void cleanup(LivingEntity a, LivingEntity b) {
        RollService.release(a);
        RollService.release(b);
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
