package studio.modroll.critfall.gametest;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.CritfallTags;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;

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

    /** A zoglin has no fists: its empty-handed kill reads from the natural-attack pool. */
    public static void zoglinKillShowsNaturalLine(GameTestHelper helper) {
        // d20 13 + 5 vs AC 10 hits, 2d6+2 rolls 6, 6 for 14 damage
        emptyHandedKill(helper, EntityType.ZOGLIN, "critfall.flavor.natural.kill.", 13, 6, 6);
    }

    /** A zombie fights with fists: its empty-handed kill keeps the unarmed line. */
    public static void zombieKillShowsUnarmedLine(GameTestHelper helper) {
        // d20 13 + 3 vs AC 10 hits, 1d6+1 rolls 6 for 7 damage
        emptyHandedKill(helper, EntityType.ZOMBIE, "critfall.flavor.default.kill.", 13, 6);
    }

    /**
     * An evoker fights with fists, but its fangs are a spell: an empty-handed spell kill reads from
     * the natural-attack pool. Vanilla fangs deal {@code minecraft:indirect_magic}, which ships in
     * {@code #critfall:exempt} and is never rolled, so this source keeps the fangs' shape (fangs
     * direct, evoker causing) with type {@code mob_attack} to reach the spell path, resolved here as
     * a saving throw.
     */
    public static void evokerFangKillShowsNaturalLine(GameTestHelper helper) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Evoker evoker = CombatScenarios.spawnCalm(helper, EntityType.EVOKER, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(LOW_HEALTH);
            if (!evoker.getType().is(CritfallTags.FIGHTS_WITH_FISTS)
                    || !evoker.getMainHandItem().isEmpty()) {
                helper.fail("setup: the evoker must be an empty-handed member of #critfall:fights_with_fists");
            }
            EvokerFangs fangs = new EvokerFangs(helper.getLevel(), pig.getX(), pig.getY(), pig.getZ(), 0.0F, 0, evoker);
            DamageSource bite = new DamageSource(
                    helper.getLevel()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(DamageTypes.MOB_ATTACK),
                    fangs,
                    evoker);
            SpellScenarios.withSpellProfile(
                    "{\"matches\": [\"minecraft:mob_attack\"], \"resolution\": \"save\", \"damage\": \"2d6\","
                            + " \"save\": {\"dc\": 13}}",
                    () -> CombatScenarios.withRolls(
                            helper,
                            Rules.DEFAULTS,
                            () -> {
                                // pig save 2 + 0 vs DC 13 fails; 2d6 rolls 6, 6 for 12 damage
                                pig.hurt(bite, 6.0F);
                                SaveFeedbackPayload payload = sink.lastSave();
                                if (!pig.isDeadOrDying()) {
                                    helper.fail("the pig should have died, payload " + payload);
                                }
                                if (payload == null
                                        || payload.flavorKey().isEmpty()
                                        || !payload.flavorKey().get().startsWith("critfall.flavor.natural.kill.")) {
                                    helper.fail("an evoker-fang kill must read as a natural kill, was " + payload);
                                }
                            },
                            2,
                            6,
                            6));
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static void emptyHandedKill(
            GameTestHelper helper, EntityType<? extends Mob> attackerType, String expectedPrefix, int... faces) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            Mob attacker = CombatScenarios.spawnCalm(helper, attackerType, 1, 1);
            Pig pig = CombatScenarios.spawnCalm(helper, EntityType.PIG, 3, 3);
            pig.setHealth(LOW_HEALTH);
            if (!attacker.getMainHandItem().isEmpty()) {
                helper.fail("setup: the attacker must be empty-handed, held " + attacker.getMainHandItem());
            }
            CombatScenarios.withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        pig.hurt(helper.getLevel().damageSources().mobAttack(attacker), 3.0F);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (!pig.isDeadOrDying()) {
                            helper.fail("the pig should have died, payload " + payload);
                        }
                        if (payload == null
                                || payload.flavorKey().isEmpty()
                                || !payload.flavorKey().get().startsWith(expectedPrefix)) {
                            helper.fail("expected a " + expectedPrefix + "* kill line, was " + payload);
                        }
                    },
                    faces);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
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
