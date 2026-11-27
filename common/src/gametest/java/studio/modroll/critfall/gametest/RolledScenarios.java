package studio.modroll.critfall.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.Rolled;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.DamageResult;
import studio.modroll.critfall.api.combat.HazardSaveResult;
import studio.modroll.critfall.api.combat.HealResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * {@link RollService#rollAttack}, {@link RollService#rollHazardSave} and {@link RollService#rollHeal}: the dice roll
 * at once, nothing lands until {@link Rolled#land}, and landing uses no dice of its own.
 */
public final class RolledScenarios {

    private RolledScenarios() {}

    public static void attackRollsNowAndLandsLater(GameTestHelper helper) {
        Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        RollService.suppress(husk);
        RollService.suppress(pig);
        try {
            withSink(sink -> {
                Rolled<AttackResult> rolled = roll(
                        helper,
                        () -> RollService.rollAttack(
                                husk,
                                pig,
                                AttackContext.melee(
                                        helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem())),
                        13,
                        4);
                expectNothingLanded(helper, pig, sink.lastRoll());
                // 13 + 3 = 16 vs AC 10 hits; 1d4+1 rolled a 4 for 5.
                AttackResult result = rolled.land();
                if (!result.isHit() || result.damage() != 5) {
                    helper.fail("expected a hit for 5, got " + result);
                }
                expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                expectReadout(
                        helper,
                        sink.lastRoll() == null ? null : CombatText.actionBar(sink.lastRoll()),
                        "d20 13+3=16 vs AC 10 — HIT");
                try {
                    rolled.land();
                    helper.fail("an attack must land only once");
                } catch (IllegalStateException expected) {
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                }
            });
        } finally {
            RollService.release(husk);
            RollService.release(pig);
        }
        helper.succeed();
    }

    public static void hazardSaveRollsNowAndLandsLater(GameTestHelper helper) {
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        HazardSave spikes = HazardSave.of(
                "Spike trap", DamageTypes.CACTUS, ModifierProvider.DEXTERITY_SAVE, 13, DiceExpression.parse("2d6"));
        withSink(sink -> {
            // d20 5 + 0 = 5 vs DC 13 fails; 2d6 rolls 3 + 4 = 7.
            Rolled<HazardSaveResult> rolled = roll(helper, () -> RollService.rollHazardSave(pig, spikes), 5, 3, 4);
            expectNothingLanded(helper, pig, sink.lastCheck());
            HazardSaveResult result = rolled.land();
            if (result.saved() || result.damage() != 7) {
                helper.fail("expected a failed save taking 7, got " + result);
            }
            expectHealth(helper, pig, pig.getMaxHealth() - 7.0F);
            expectReadout(
                    helper,
                    sink.lastCheck() == null ? null : CombatText.actionBar(sink.lastCheck()),
                    "Spike trap: DEX save d20 5+0=5 vs DC 13 — FAILED 2d6 = 7");
        });
        helper.succeed();
    }

    public static void healRollsNowAndLandsLater(GameTestHelper helper) {
        Player healer = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        pig.setHealth(4.0F);
        withSink(sink -> {
            // 1d4+1 rolls 3 + 1 = 4.
            Rolled<HealResult> rolled = roll(
                    helper, () -> RollService.rollHeal(healer, pig, "Cure Wounds", DiceExpression.parse("1d4+1")), 3);
            expectHealth(helper, pig, 4.0F);
            if (sink.lastHeal() != null) {
                helper.fail("nothing may show before the heal lands");
            }
            HealResult result = rolled.land();
            if (result.healed() != 4.0F) {
                helper.fail("expected 4 healed, got " + result);
            }
            expectHealth(helper, pig, 8.0F);
            expectReadout(
                    helper,
                    sink.lastHeal() == null ? null : CombatText.actionBar(sink.lastHeal()),
                    "Cure Wounds: 1d4+1 = 4 healed");
        });
        helper.succeed();
    }

    public static void damageRollsNowAndLandsLater(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        withSink(sink -> {
            // 1d4+1 rolls 3 + 1 = 4.
            Rolled<DamageResult> rolled = roll(
                    helper,
                    () -> RollService.rollDamage(
                            attacker, pig, "Magic Missile", DiceExpression.parse("1d4+1"), DamageTypes.MAGIC),
                    3);
            expectHealth(helper, pig, 10.0F);
            if (sink.lastDamage() != null) {
                helper.fail("nothing may show before the damage lands");
            }
            DamageResult result = rolled.land();
            if (result.damage() != 4) {
                helper.fail("expected 4 damage, got " + result);
            }
            expectHealth(helper, pig, 6.0F);
            expectReadout(
                    helper,
                    sink.lastDamage() == null ? null : CombatText.actionBar(sink.lastDamage()),
                    "Magic Missile: 1d4+1 = 4 damage");
        });
        helper.succeed();
    }

    /** Rolls on the scripted {@code faces}; landing happens after, on the server's own roller. */
    private static <R> Rolled<R> roll(GameTestHelper helper, Supplier<Rolled<R>> roll, int... faces) {
        List<Rolled<R>> rolled = new ArrayList<>(1);
        CombatScenarios.withRolls(helper, Rules.DEFAULTS, () -> rolled.add(roll.get()), faces);
        return rolled.getFirst();
    }

    private static void withSink(Consumer<CapturingFeedbackSink> action) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            action.accept(sink);
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void expectNothingLanded(GameTestHelper helper, LivingEntity target, Object readout) {
        expectHealth(helper, target, target.getMaxHealth());
        if (readout != null) {
            helper.fail("nothing may show before the roll lands");
        }
    }

    private static void expectReadout(GameTestHelper helper, Component readout, String fragment) {
        String text = readout == null ? null : readout.getString();
        if (text == null || !text.contains(fragment)) {
            helper.fail("the readout must contain \"" + fragment + "\", was \"" + text + "\"");
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
