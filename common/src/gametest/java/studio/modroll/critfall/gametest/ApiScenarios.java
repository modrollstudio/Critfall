package studio.modroll.critfall.gametest;

import java.util.Random;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.dice.DiceRoller;

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
