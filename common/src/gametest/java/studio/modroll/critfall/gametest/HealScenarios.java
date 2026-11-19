package studio.modroll.critfall.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.api.combat.HealResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/** {@link RollService#performHeal}: rolled on the combat roller, healed through vanilla, shown in the readout. */
public final class HealScenarios {

    private HealScenarios() {}

    public static void healRollsHealsAndShowsTheReadout(GameTestHelper helper) {
        Player healer = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
        pig.setHealth(4.0F);
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        // 1d4+1 rolls 3 + 1 = 4.
                        HealResult result =
                                RollService.performHeal(healer, pig, "Cure Wounds", DiceExpression.parse("1d4+1"));
                        if (result.roll().total() != 4 || result.healed() != 4.0F) {
                            helper.fail("expected a roll of 4 healing 4, got " + result);
                        }
                        expectHealth(helper, pig, 8.0F);
                        expectReadout(helper, sink, "Cure Wounds: 1d4+1 = 4 healed");
                        if (sink.lastHealer() != healer || sink.lastHealTarget() != pig) {
                            helper.fail("the readout must go to the healer and the target");
                        }
                    },
                    3);
        } finally {
            FeedbackSink.set(previous);
        }
        helper.succeed();
    }

    /** A heal past the maximum stops there, and the readout shows the roll and what was gained. */
    public static void healStopsAtMaximumHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth() - 5.0F);
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        // 2d8+3 rolls 4 + 5 + 3 = 12, but only 5 health is missing.
                        HealResult result =
                                RollService.performHeal(player, player, "Cure Wounds", DiceExpression.parse("2d8+3"));
                        if (result.roll().total() != 12 || result.healed() != 5.0F) {
                            helper.fail("expected a roll of 12 healing 5, got " + result);
                        }
                        expectHealth(helper, player, player.getMaxHealth());
                        expectReadout(helper, sink, "Cure Wounds: 2d8+3 = 12 → 5 healed");
                    },
                    4,
                    5);
        } finally {
            FeedbackSink.set(previous);
        }
        helper.succeed();
    }

    private static void expectReadout(GameTestHelper helper, CapturingFeedbackSink sink, String expected) {
        String readout = sink.lastHeal() == null
                ? null
                : CombatText.actionBar(sink.lastHeal()).getString();
        if (!expected.equals(readout)) {
            helper.fail("expected the readout \"" + expected + "\", was \"" + readout + "\"");
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
