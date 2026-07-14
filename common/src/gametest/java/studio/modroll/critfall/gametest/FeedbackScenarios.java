package studio.modroll.critfall.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * The modless fallback must stay legible: a vanilla-client player still learns WHICH consequence
 * fired, not just the bare roll. Driven through the real damage handler with scripted RNG. A
 * loader-agnostic {@link CapturingFeedbackSink} records what the pipeline dispatched, so the same
 * body asserts on both loaders (the loader dispatchers are exercised by their own smoke paths).
 */
public final class FeedbackScenarios {

    private FeedbackScenarios() {}

    public static void modlessFallbackCarriesConsequenceText(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        // nat 1, confirmation 2 < DC 10 fails -> fumble confirmed, table pick 1 = durability
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null
                                || payload.consequences().stream()
                                        .noneMatch(c -> c.key().equals(ConsequenceLine.DURABILITY_BROKEN))) {
                            helper.fail("dispatched payload should carry the durability consequence, was " + payload);
                        }
                        String fallback = CombatText.actionBar(payload).getString();
                        if (!fallback.contains("durability.broken")
                                && !fallback.toLowerCase().contains("weapon")) {
                            helper.fail("modless fallback must announce the consequence, was: " + fallback);
                        }
                    },
                    1,
                    2,
                    1);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    public static void critDispatchesCritPayload(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F); // nat 20 -> crit
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null || payload.outcome() != AttackOutcome.CRIT) {
                            helper.fail("nat 20 should dispatch a CRIT payload, was " + payload);
                        }
                    },
                    20);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }
}
