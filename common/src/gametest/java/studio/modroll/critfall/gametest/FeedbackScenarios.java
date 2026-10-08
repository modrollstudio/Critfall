package studio.modroll.critfall.gametest;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    /** Pig health before each lethal roll: the 7-damage hit below would kill it outright. */
    private static final float LOW_HEALTH = 3.0F;

    public static void resistanceFiveLethalRollShowsNoKillLine(GameTestHelper helper) {
        lethalHit(
                helper,
                pig -> pig.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4)),
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (pig.getHealth() != LOW_HEALTH) {
                        helper.fail("Resistance V must negate the hit, health was " + pig.getHealth());
                    }
                    if (!hasResisted(payload)) {
                        helper.fail("a fully negated hit must be marked resisted, was " + payload);
                    }
                });
    }

    public static void totemLethalRollShowsNoKillLine(GameTestHelper helper) {
        lethalHit(
                helper,
                pig -> pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING)),
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (!pig.getMainHandItem().isEmpty()) {
                        helper.fail("the totem should have been used");
                    }
                    if (hasResisted(payload)) {
                        helper.fail("a totem-saved hit still took damage, must not read as resisted");
                    }
                });
    }

    /** At 1 HP a totem leaves health where it started; the hit was still lethal, not resisted. */
    public static void totemAtOneHealthIsNotMarkedResisted(GameTestHelper helper) {
        lethalHit(
                helper,
                1.0F,
                pig -> pig.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING)),
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (!pig.getMainHandItem().isEmpty()) {
                        helper.fail("the totem should have been used");
                    }
                    if (hasResisted(payload)) {
                        helper.fail("a totem-saved lethal hit must not read as resisted, was " + payload);
                    }
                });
    }

    public static void absorptionLethalRollShowsNoKillLine(GameTestHelper helper) {
        lethalHit(
                helper,
                pig -> {
                    pig.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4));
                    if (pig.getAbsorptionAmount() < 8.0F) {
                        helper.fail("setup: Absorption V should give 20 absorption, was " + pig.getAbsorptionAmount());
                    }
                },
                (pig, payload) -> {
                    expectSurvived(helper, pig, payload);
                    if (pig.getHealth() != LOW_HEALTH || pig.getAbsorptionAmount() >= 20.0F) {
                        helper.fail("absorption should take the hit, health " + pig.getHealth() + " absorption "
                                + pig.getAbsorptionAmount());
                    }
                    if (hasResisted(payload)) {
                        helper.fail("an absorbed hit still took damage, must not read as resisted");
                    }
                });
    }

    public static void realKillShowsKillLine(GameTestHelper helper) {
        lethalHit(helper, pig -> {}, (pig, payload) -> {
            if (!pig.isDeadOrDying()) {
                helper.fail("the unprotected pig should have died");
            }
            if (payload.flavorKey().isEmpty() || !payload.flavorKey().get().contains(".kill.")) {
                helper.fail("a real kill must carry the kill line, was " + payload);
            }
            if (hasResisted(payload)) {
                helper.fail("a kill is not resisted");
            }
        });
    }

    /**
     * A husk hits a {@code health} pig: d20 13 + 3 vs AC 10 hits, 1d6+1 rolls 6 for 7 damage
     * — lethal as rolled. {@code protect} sets up what may keep the pig alive.
     */
    private static void lethalHit(
            GameTestHelper helper, Consumer<Pig> protect, BiConsumer<Pig, RollFeedbackPayload> check) {
        lethalHit(helper, LOW_HEALTH, protect, check);
    }

    private static void lethalHit(
            GameTestHelper helper, float health, Consumer<Pig> protect, BiConsumer<Pig, RollFeedbackPayload> check) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Husk husk = CombatScenarios.spawnCalm(helper, EntityType.HUSK, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(health);
            protect.accept(pig);
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null || payload.outcome() != AttackOutcome.HIT || payload.damage() != 7) {
                            helper.fail("expected a 7-damage HIT payload, was " + payload);
                        }
                        check.accept(pig, payload);
                    },
                    13,
                    6);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void expectSurvived(GameTestHelper helper, Pig pig, RollFeedbackPayload payload) {
        if (pig.isDeadOrDying()) {
            helper.fail("the pig should have survived the hit");
        }
        if (payload.flavorKey().isPresent()) {
            helper.fail("a survived hit must not show a kill line, was "
                    + payload.flavorKey().get());
        }
    }

    private static boolean hasResisted(RollFeedbackPayload payload) {
        return payload.consequences().stream().anyMatch(c -> c.key().equals(ConsequenceLine.RESISTED));
    }
}
