package studio.modroll.critfall.gametest;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.RollFeedbackPayload;

/**
 * End-to-end tests of issue #3 delivery-aware resolution: the same trident resolves different item
 * profiles and flavor pools depending on whether it was stabbed (MELEE) or thrown (THROWN).
 * Shipped defaults used below: zombies (drowned) attack bonus +3, pig AC 10, the trident_melee /
 * trident_thrown flavor pools.
 */
public final class TridentScenarios {

    private TridentScenarios() {}

    /** Melee-only 1d4 and thrown-only 2d6 profiles, both dice-verbatim, competing for the trident. */
    private static final Map<String, String> SPLIT_TRIDENT_PROFILES = Map.of(
            "critfall_test:trident_stab",
                    "{\"matches\": [\"minecraft:trident\"], \"delivery\": [\"melee\"],"
                            + " \"damage\": \"1d4\", \"modifier_from\": \"none\"}",
            "critfall_test:trident_throw",
                    "{\"matches\": [\"minecraft:trident\"], \"delivery\": [\"thrown\"],"
                            + " \"damage\": \"2d6\", \"modifier_from\": \"none\"}");

    public static void meleeTridentStabUsesMeleeProfile(GameTestHelper helper) {
        Drowned drowned = spawnCalm(helper, EntityType.DROWNED, 1, 1);
        drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withItemProfiles(
                SPLIT_TRIDENT_PROFILES,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            // 13 + 3 (zombies profile) = 16 vs pig AC 10 -> hit; the MELEE-constrained
                            // profile (1d4, verbatim) must beat both the thrown-only and the shipped
                            // unconstrained trident profile; face 3 -> 3 damage
                            pig.hurt(helper.getLevel().damageSources().mobAttack(drowned), 8.0F);
                            expectHealth(helper, pig, pig.getMaxHealth() - 3.0F);
                        },
                        13,
                        3));
        helper.succeed();
    }

    public static void thrownTridentUsesThrownProfile(GameTestHelper helper) {
        Drowned drowned = spawnCalm(helper, EntityType.DROWNED, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        ThrownTrident trident = new ThrownTrident(helper.getLevel(), drowned, new ItemStack(Items.TRIDENT));
        helper.getLevel().addFreshEntity(trident);
        withItemProfiles(
                SPLIT_TRIDENT_PROFILES,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            // 13 + 3 = 16 vs AC 10 -> hit; the THROWN-constrained profile (2d6, verbatim)
                            // must win; faces 4 and 2 -> 6 damage
                            pig.hurt(helper.getLevel().damageSources().trident(trident, drowned), 8.0F);
                            expectHealth(helper, pig, pig.getMaxHealth() - 6.0F);
                        },
                        13,
                        4,
                        2));
        helper.succeed();
    }

    public static void meleeTridentCritPicksMeleeFlavorPool(GameTestHelper helper) {
        Drowned drowned = spawnCalm(helper, EntityType.DROWNED, 1, 1);
        drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        expectCritFlavorPrefix(
                helper,
                "critfall.flavor.trident_melee.",
                () -> pig.hurt(helper.getLevel().damageSources().mobAttack(drowned), 8.0F));
    }

    public static void thrownTridentCritPicksThrownFlavorPool(GameTestHelper helper) {
        Drowned drowned = spawnCalm(helper, EntityType.DROWNED, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        ThrownTrident trident = new ThrownTrident(helper.getLevel(), drowned, new ItemStack(Items.TRIDENT));
        helper.getLevel().addFreshEntity(trident);
        expectCritFlavorPrefix(
                helper,
                "critfall.flavor.trident_thrown.",
                () -> pig.hurt(helper.getLevel().damageSources().trident(trident, drowned), 8.0F));
    }

    /** Runs {@code attack} on a scripted nat 20 and asserts the dispatched crit flavor key's pool. */
    private static void expectCritFlavorPrefix(GameTestHelper helper, String prefix, Runnable attack) {
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        // nat 20 -> crit (damage maximized, no damage-dice draw); the trident
                        // profile's default_crit table picks d4, face 4 = nothing; the flavor pick
                        // itself runs on the unscripted feedback roller, so assert the pool prefix
                        attack.run();
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null || payload.flavorKey().isEmpty()) {
                            helper.fail("a crit must dispatch a payload with a flavor line, was " + payload);
                        } else if (!payload.flavorKey().get().startsWith(prefix)) {
                            helper.fail("crit flavor must come from the " + prefix + " pool, was "
                                    + payload.flavorKey().get());
                        }
                    },
                    20,
                    4);
            helper.succeed();
        } finally {
            FeedbackSink.set(previous);
        }
    }

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /** Adds item profiles to the loaded set for the duration of {@code action}. */
    private static void withItemProfiles(Map<String, String> profiles, Runnable action) {
        Map<ResourceLocation, ItemProfile> before = ProfileStore.itemProfiles();
        Map<ResourceLocation, ItemProfile> patched = new HashMap<>(before);
        profiles.forEach((id, json) -> {
            ResourceLocation profileId = ResourceLocation.parse(id);
            patched.put(
                    profileId,
                    ItemProfile.parse(profileId, JsonParser.parseString(json).getAsJsonObject(), warning -> {}));
        });
        ProfileStore.setItemProfiles(patched);
        try {
            action.run();
        } finally {
            ProfileStore.setItemProfiles(before);
        }
    }

    private static void withRolls(GameTestHelper helper, Rules rules, Runnable action, int... faces) {
        ScriptedRandom scripted = ScriptedRandom.ofDieFaces(faces);
        Rules before = RollRuntime.rules();
        RollRuntime.setRoller(new DiceRoller(scripted));
        RollRuntime.setRules(rules);
        FumbleCooldowns.clear();
        try {
            action.run();
            if (!scripted.isExhausted()) {
                helper.fail("fewer dice were rolled than scripted");
            }
        } finally {
            RollRuntime.setRoller(new DiceRoller(new Random()));
            RollRuntime.setRules(before);
            FumbleCooldowns.clear();
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }
}
