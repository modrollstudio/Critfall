package studio.modroll.critfall.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * Jump attacks ({@code advantage_sources.jump_attack}) through a real {@code Player.attack}, so each
 * loader's hook on vanilla's crit decision is exercised. The attacker is a bare-handed player with
 * attack damage 4: +2 to hit (derived), and 4 vanilla damage that derives {@code 1d8}. Vanilla's
 * 1.5x crit would make it 6, which derives {@code 1d12}. The pig's profile AC is 10.
 */
public final class JumpAttackScenarios {

    private static final float ATTACK_DAMAGE = 4.0F;
    private static final float VANILLA_CRIT = ATTACK_DAMAGE * 1.5F;

    private static final Rules JUMP_OFF =
            rules(Rules.DEFAULTS.attackRolls(), true, Rules.DryRun.DEFAULTS, new Rules.AdvantageSources(false));

    public static void jumpHitRollsWithAdvantage(GameTestHelper helper) {
        Player player = attacker(helper, true);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        run(
                helper,
                Rules.DEFAULTS,
                sink -> {
                    // advantage 3/15 keeps 15: 15+2=17 vs AC 10 hits; 1d8 rolls 5
                    player.attack(pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    expectRoll(helper, sink.lastRoll(), RollMode.ADVANTAGE, AttackOutcome.HIT);
                    expectReadout(helper, sink.lastRoll(), "d20 adv 3/15 → 15", "1d8 = 5");
                },
                3,
                15,
                5);
        helper.succeed();
    }

    public static void groundedHitRollsNormally(GameTestHelper helper) {
        Player player = attacker(helper, false);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        run(
                helper,
                Rules.DEFAULTS,
                sink -> {
                    // a single d20: 15+2=17 vs AC 10 hits; 1d8 rolls 5
                    player.attack(pig);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                    expectRoll(helper, sink.lastRoll(), RollMode.NORMAL, AttackOutcome.HIT);
                    expectReadout(helper, sink.lastRoll(), "d20 15+2=17", "1d8 = 5");
                },
                15,
                5);
        helper.succeed();
    }

    public static void jumpHitWithDisadvantageSourceRollsNormally(GameTestHelper helper) {
        Player player = attacker(helper, true);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        List<RollMode> seen = new ArrayList<>();
        CritfallEvents.onPreAttackRoll(event -> {
            seen.add(event.mode());
            event.mode(RollMode.DISADVANTAGE);
        });
        try {
            run(
                    helper,
                    Rules.DEFAULTS,
                    sink -> {
                        // advantage + disadvantage = one d20: 15+2=17 hits; 1d8 rolls 5
                        player.attack(pig);
                        expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                        expectRoll(helper, sink.lastRoll(), RollMode.NORMAL, AttackOutcome.HIT);
                        if (!seen.equals(List.of(RollMode.ADVANTAGE))) {
                            helper.fail("the listener must see the jump attack's advantage, saw " + seen);
                        }
                    },
                    15,
                    5);
        } finally {
            CritfallEvents.clearListeners();
        }
        helper.succeed();
    }

    public static void jumpHitDropsVanillaCritMultiplier(GameTestHelper helper) {
        Player player = attacker(helper, true);
        Pig rolled = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        Pig vanillaAmount = CombatScenarios.spawnCalm(helper, EntityType.PIG, 5, 5);
        run(
                helper,
                Rules.DEFAULTS,
                sink -> {
                    // the derived dice come from the 4 vanilla damage (1d8), not the crit's 6 (1d12);
                    // a kept 15 is a plain HIT: only a natural 20 crits
                    player.attack(rolled);
                    expectHealth(helper, rolled, rolled.getMaxHealth() - 5.0F);
                    expectRoll(helper, sink.lastRoll(), RollMode.ADVANTAGE, AttackOutcome.HIT);
                    expectReadout(helper, sink.lastRoll(), "1d8 = 5");
                },
                3,
                15,
                5);
        run(
                helper,
                rules(Rules.DEFAULTS.attackRolls(), false, Rules.DryRun.DEFAULTS, Rules.AdvantageSources.DEFAULTS),
                sink -> {
                    // damage dice off apply the vanilla amount: 4, without the 1.5x
                    player.attack(vanillaAmount);
                    expectHealth(helper, vanillaAmount, vanillaAmount.getMaxHealth() - ATTACK_DAMAGE);
                    expectRoll(helper, sink.lastRoll(), RollMode.ADVANTAGE, AttackOutcome.HIT);
                },
                3,
                15);
        helper.succeed();
    }

    public static void jumpAttackOffMatchesPreviousRelease(GameTestHelper helper) {
        Player player = attacker(helper, true);
        Pig rolled = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        Pig vanillaAmount = CombatScenarios.spawnCalm(helper, EntityType.PIG, 5, 5);
        run(
                helper,
                JUMP_OFF,
                sink -> {
                    // one d20, and dice derived from the crit's 6 (1d12), as in 0.2.8
                    player.attack(rolled);
                    expectHealth(helper, rolled, rolled.getMaxHealth() - 7.0F);
                    expectRoll(helper, sink.lastRoll(), RollMode.NORMAL, AttackOutcome.HIT);
                    expectReadout(helper, sink.lastRoll(), "d20 15+2=17", "1d12 = 7");
                },
                15,
                7);
        run(
                helper,
                rules(Rules.DEFAULTS.attackRolls(), false, Rules.DryRun.DEFAULTS, new Rules.AdvantageSources(false)),
                sink -> {
                    player.attack(vanillaAmount);
                    expectHealth(helper, vanillaAmount, vanillaAmount.getMaxHealth() - VANILLA_CRIT);
                    expectRoll(helper, sink.lastRoll(), RollMode.NORMAL, AttackOutcome.HIT);
                },
                15);
        helper.succeed();
    }

    public static void unrolledJumpHitKeepsVanillaCrit(GameTestHelper helper) {
        Player player = attacker(helper, true);
        Pig unrolled = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        Pig dryRun = CombatScenarios.spawnCalm(helper, EntityType.PIG, 5, 5);
        // player rolls off: vanilla's hit, crit multiplier included; no die is drawn
        run(
                helper,
                rules(
                        new Rules.AttackRolls(true, false, true, true, true),
                        true,
                        Rules.DryRun.DEFAULTS,
                        Rules.AdvantageSources.DEFAULTS),
                sink -> {
                    player.attack(unrolled);
                    expectHealth(helper, unrolled, unrolled.getMaxHealth() - VANILLA_CRIT);
                });
        // dry-run shows the advantage roll the live game would make, but vanilla damage stands
        run(
                helper,
                rules(Rules.DEFAULTS.attackRolls(), true, new Rules.DryRun(true), Rules.AdvantageSources.DEFAULTS),
                sink -> {
                    player.attack(dryRun);
                    expectHealth(helper, dryRun, dryRun.getMaxHealth() - VANILLA_CRIT);
                    expectRoll(helper, sink.lastRoll(), RollMode.ADVANTAGE, AttackOutcome.HIT);
                },
                3,
                15,
                5);
        helper.succeed();
    }

    public static void drivenAttacksIgnoreJumping(GameTestHelper helper) {
        Player player = attacker(helper, true);
        Pig performed = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        Pig rolledOnly = CombatScenarios.spawnCalm(helper, EntityType.PIG, 5, 5);
        AttackContext melee =
                AttackContext.melee(helper.getLevel().damageSources().playerAttack(player), player.getMainHandItem());
        run(
                helper,
                Rules.DEFAULTS,
                sink -> {
                    // a falling attacker's driven attack rolls one d20
                    AttackResult result = RollService.performAttack(player, performed, melee);
                    if (result.roll().mode() != RollMode.NORMAL) {
                        helper.fail("a driven attack must ignore jumping, rolled " + result.roll());
                    }
                    expectHealth(helper, performed, performed.getMaxHealth() - 5.0F);
                    AttackResult roll = RollService.attackRoll(player, rolledOnly, melee);
                    if (roll.roll().mode() != RollMode.NORMAL) {
                        helper.fail("attackRoll must ignore jumping, rolled " + roll.roll());
                    }
                },
                15,
                5,
                15,
                5);
        // a driven attack's own advantage is still replaced, not cancelled, by a listener's disadvantage
        CritfallEvents.onPreAttackRoll(event -> event.mode(RollMode.DISADVANTAGE));
        try {
            run(
                    helper,
                    Rules.DEFAULTS,
                    sink -> {
                        AttackResult result =
                                RollService.attackRoll(player, rolledOnly, melee.withMode(RollMode.ADVANTAGE));
                        if (result.roll().mode() != RollMode.DISADVANTAGE) {
                            helper.fail("a driven attack's listener mode must stand, rolled " + result.roll());
                        }
                    },
                    // disadvantage 15/3 keeps 3: 3+2=5 misses AC 10, so no damage die
                    15,
                    3);
        } finally {
            CritfallEvents.clearListeners();
        }
        helper.succeed();
    }

    /**
     * A bare-handed player in the test structure, fully charged (the mock is never ticked, so its
     * attack-strength ticker would otherwise read zero). {@code falling} sets up vanilla's jump crit:
     * airborne with some fall distance; the rest of the conditions hold for a fresh player in air.
     */
    private static Player attacker(GameTestHelper helper, boolean falling) {
        Player player =
                new Player(
                        helper.getLevel(),
                        helper.absolutePos(new BlockPos(1, 2, 1)),
                        0.0F,
                        new GameProfile(UUID.randomUUID(), "critfall-jumper")) {
                    @Override
                    public boolean isSpectator() {
                        return false;
                    }

                    @Override
                    public boolean isCreative() {
                        return false;
                    }

                    @Override
                    public float getAttackStrengthScale(float adjustTicks) {
                        return 1.0F;
                    }
                };
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ATTACK_DAMAGE);
        player.setOnGround(!falling);
        player.fallDistance = falling ? 1.0F : 0.0F;
        return player;
    }

    private static Rules rules(
            Rules.AttackRolls attackRolls,
            boolean damageDice,
            Rules.DryRun dryRun,
            Rules.AdvantageSources advantageSources) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                attackRolls,
                damageDice,
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance(),
                dryRun,
                base.modifierProviders(),
                advantageSources);
    }

    /** Runs {@code action} with feedback captured under the given rules and scripted rolls. */
    private static void run(GameTestHelper helper, Rules rules, Consumer<CapturingFeedbackSink> action, int... faces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            CombatScenarios.withRolls(helper, rules, () -> action.accept(sink), faces);
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void expectRoll(
            GameTestHelper helper, RollFeedbackPayload payload, RollMode mode, AttackOutcome outcome) {
        if (payload == null) {
            helper.fail("no feedback payload was dispatched");
            return;
        }
        if (payload.rollMode() != mode || payload.outcome() != outcome) {
            helper.fail("expected a " + mode + " " + outcome + ", was " + payload.rollMode() + " " + payload.outcome());
        }
    }

    private static void expectReadout(GameTestHelper helper, RollFeedbackPayload payload, String... fragments) {
        if (payload == null) {
            helper.fail("no feedback payload was dispatched");
            return;
        }
        String readout = CombatText.actionBar(payload).getString();
        for (String fragment : fragments) {
            if (!readout.contains(fragment)) {
                helper.fail("the readout must contain \"" + fragment + "\", was \"" + readout + "\"");
            }
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
