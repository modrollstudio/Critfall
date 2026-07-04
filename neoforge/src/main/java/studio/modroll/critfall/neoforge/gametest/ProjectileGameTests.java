package studio.modroll.critfall.neoforge.gametest;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * End-to-end tests of M5 projectile interception: arrows roll on impact with dice from the bow's
 * item profile (bonus derived from the vanilla projectile damage, which carries Power and draw
 * strength), thrown tridents use the trident profile, ammo profiles add dice on top, and
 * 0-damage/ownerless projectiles stay vanilla. Shipped default profiles: skeleton +4/AC 12,
 * pig AC 10, bow 1d8, crossbow 1d10, trident 1d8, zombies (drowned) +3.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProjectileGameTests {

    private static final String TEMPLATE = "empty";
    /** Vanilla full-draw arrow damage; bow dice average 4.5 makes the derived bonus +2. */
    private static final float ARROW_DAMAGE = 6.0F;

    @GameTest(template = TEMPLATE)
    public void arrowMissCancelsAllDamage(GameTestHelper helper) {
        Skeleton skeleton = spawnCalm(helper, EntityType.SKELETON, 1, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Arrow arrow = shotArrow(helper, skeleton);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 5 + 4 (skeleton profile) = 9 vs pig AC 10 -> miss
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
                    if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("a missed arrow must deal no damage");
                    }
                },
                5);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void arrowHitRollsBowProfileDice(GameTestHelper helper) {
        Skeleton skeleton = spawnCalm(helper, EntityType.SKELETON, 1, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Arrow arrow = shotArrow(helper, skeleton);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 13 + 4 = 17 vs AC 10 -> hit; bow 1d8+2 rolls a 4 -> 6 damage
                    pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
                    expectHealth(helper, pig, pig.getMaxHealth() - 6.0F);
                },
                13,
                4);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void ammoProfileAddsDiceOnTopOfTheBow(GameTestHelper helper) {
        Skeleton skeleton = spawnCalm(helper, EntityType.SKELETON, 1, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Arrow arrow = shotArrow(helper, skeleton);
        withItemProfile(
                "critfall_test:hunting_arrow",
                "{\"matches\": [\"minecraft:arrow\"], \"damage\": \"1d4\", \"modifier_from\": \"none\"}",
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            // hit; dice 1d8+2+1d4 roll 4 and 2 -> 8 damage
                            pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
                            expectHealth(helper, pig, pig.getMaxHealth() - 8.0F);
                        },
                        13,
                        4,
                        2));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void thrownTridentUsesTridentProfile(GameTestHelper helper) {
        Drowned drowned = spawnCalm(helper, EntityType.DROWNED, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        ThrownTrident trident = new ThrownTrident(helper.getLevel(), drowned, new ItemStack(Items.TRIDENT));
        helper.getLevel().addFreshEntity(trident);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 13 + 3 (zombies profile) = 16 vs AC 10 -> hit; trident 1d8 with a +4 bonus
                    // derived from the vanilla 8.0 rolls a 4 -> 8 damage (and the trident is NOT
                    // double-counted as its own ammunition)
                    pig.hurt(helper.getLevel().damageSources().trident(trident, drowned), 8.0F);
                    expectHealth(helper, pig, pig.getMaxHealth() - 8.0F);
                },
                13,
                4);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void zeroDamageProjectileStaysVanilla(GameTestHelper helper) {
        Skeleton skeleton = spawnCalm(helper, EntityType.SKELETON, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Snowball snowball = new Snowball(helper.getLevel(), skeleton);
        helper.getLevel().addFreshEntity(snowball);
        withRolls(helper, Rules.DEFAULTS, () -> {
            // no faces scripted: a 0-damage snowball must not draw dice or invent damage
            pig.hurt(helper.getLevel().damageSources().thrown(snowball, skeleton), 0.0F);
            expectHealth(helper, pig, pig.getMaxHealth());
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void ownerlessArrowStaysVanilla(GameTestHelper helper) {
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Arrow arrow = new Arrow(
                helper.getLevel(),
                pig.getX(),
                pig.getY() + 2,
                pig.getZ(),
                new ItemStack(Items.ARROW),
                new ItemStack(Items.BOW));
        helper.getLevel().addFreshEntity(arrow);
        withRolls(helper, Rules.DEFAULTS, () -> {
            // dispenser-style arrow: nobody to roll for -> vanilla damage applies
            pig.hurt(helper.getLevel().damageSources().arrow(arrow, null), ARROW_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - ARROW_DAMAGE);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void projectileRollsDisabledRestoresVanilla(GameTestHelper helper) {
        Skeleton skeleton = spawnCalm(helper, EntityType.SKELETON, 1, 1);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Arrow arrow = shotArrow(helper, skeleton);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                new Rules.AttackRolls(true, true, true, false, true),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
        withRolls(helper, rules, () -> {
            pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
            expectHealth(helper, pig, pig.getMaxHealth() - ARROW_DAMAGE);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fumbledShotWearsTheBowStillInHand(GameTestHelper helper) {
        Skeleton skeleton = spawnCalm(helper, EntityType.SKELETON, 1, 1);
        ItemStack bow = new ItemStack(Items.BOW);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, bow);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Arrow arrow = shotArrow(helper, skeleton);
        Rules rules = withFumbles(new Rules.Fumbles(
                true,
                true,
                false,
                10,
                0,
                true,
                Rules.DurabilityMode.SET_TO_1,
                25,
                Rules.HitNearestAlly.DEFAULTS,
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withRolls(
                helper,
                rules,
                () -> {
                    // nat 1 -> fumble; default_ranged weighted pick (d5): face 1 = damage_durability,
                    // applied to the bow in the skeleton's hand (the arrow only carries a copy)
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().arrow(arrow, skeleton), ARROW_DAMAGE);
                    if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("a fumbled shot must miss");
                    }
                    if (bow.getDamageValue() != bow.getMaxDamage() - 1) {
                        helper.fail("the fumble must leave the held bow at 1 durability");
                    }
                },
                1,
                1);
        helper.succeed();
    }

    /** An arrow carrying the firing-weapon stack, the way bow/crossbow shots record it. */
    private static Arrow shotArrow(GameTestHelper helper, LivingEntity shooter) {
        Arrow arrow = new Arrow(helper.getLevel(), shooter, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        helper.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /** Adds one item profile to the loaded set for the duration of {@code action}. */
    private static void withItemProfile(String id, String json, Runnable action) {
        Map<ResourceLocation, ItemProfile> before = ProfileStore.itemProfiles();
        Map<ResourceLocation, ItemProfile> patched = new HashMap<>(before);
        ResourceLocation profileId = ResourceLocation.parse(id);
        patched.put(
                profileId,
                ItemProfile.parse(profileId, JsonParser.parseString(json).getAsJsonObject(), warning -> {}));
        ProfileStore.setItemProfiles(patched);
        try {
            action.run();
        } finally {
            ProfileStore.setItemProfiles(before);
        }
    }

    private static void withRolls(GameTestHelper helper, Rules rules, Runnable action, int... faces) {
        ScriptedRandom scripted = ScriptedRandom.ofDieFaces(faces);
        Rules before = RollService.rules();
        RollService.setRoller(new DiceRoller(scripted));
        RollService.setRules(rules);
        FumbleCooldowns.clear();
        try {
            action.run();
            if (!scripted.isExhausted()) {
                helper.fail("fewer dice were rolled than scripted");
            }
        } finally {
            RollService.setRoller(new DiceRoller(new Random()));
            RollService.setRules(before);
            FumbleCooldowns.clear();
        }
    }

    private static void expectHealth(GameTestHelper helper, LivingEntity entity, float expected) {
        if (Math.abs(entity.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + entity.getHealth(), entity);
        }
    }

    private static Rules withFumbles(Rules.Fumbles fumbles) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                fumbles,
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
    }
}
