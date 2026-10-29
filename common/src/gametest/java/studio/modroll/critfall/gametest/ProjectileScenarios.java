package studio.modroll.critfall.gametest;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileStore;

/**
 * End-to-end tests of M5 projectile interception: arrows roll on impact with dice from the bow's
 * item profile (bonus derived from the vanilla projectile damage, which carries Power and draw
 * strength), thrown tridents use the trident profile, ammo profiles add dice on top, and
 * 0-damage/ownerless projectiles stay vanilla. Shipped default profiles: skeleton +4/AC 12,
 * pig AC 10, bow 1d8, crossbow 1d10, trident 1d8, zombies (drowned) +3.
 */
public final class ProjectileScenarios {

    private static final String TEMPLATE = "empty";
    /** Vanilla full-draw arrow damage; bow dice average 4.5 makes the derived bonus +2. */
    private static final float ARROW_DAMAGE = 6.0F;

    public static void arrowMissCancelsAllDamage(GameTestHelper helper) {
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

    public static void arrowHitRollsBowProfileDice(GameTestHelper helper) {
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

    public static void ammoProfileAddsDiceOnTopOfTheBow(GameTestHelper helper) {
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

    public static void thrownTridentUsesTridentProfile(GameTestHelper helper) {
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

    public static void zeroDamageProjectileStaysVanilla(GameTestHelper helper) {
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

    public static void ownerlessArrowStaysVanilla(GameTestHelper helper) {
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

    public static void projectileRollsDisabledRestoresVanilla(GameTestHelper helper) {
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

    public static void fumbledShotWearsTheBowStillInHand(GameTestHelper helper) {
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

    // Real shots: a fake survival player draws and releases the weapon and the arrow flies over the
    // following ticks. These swap the global roller for the whole flight, so the loader shims run
    // each in its own batch, where no other test can restore the roller mid-flight.

    public static void playerBowShotHitLandsOnTheTarget(GameTestHelper helper) {
        realShot(helper, Items.BOW, new int[] {15}, 3.5, (player, pig, arrow, shots) -> {
            // 15 + the bonus derived from the arrow damage beats pig AC 10
            if (shots.d20Rolls() != 1) {
                helper.fail("one shot must roll once, rolled " + shots.d20Rolls() + flight(helper, arrow, pig));
            }
            if (pig.getHealth() >= pig.getMaxHealth()) {
                helper.fail("a hit arrow must damage the pig", pig);
            }
            if (!arrow.isRemoved()) {
                helper.fail("an arrow that hit must be used up", arrow);
            }
        });
    }

    public static void missedBowShotFliesPastTheTarget(GameTestHelper helper) {
        realShot(
                helper,
                Items.BOW,
                new int[] {2},
                3.5,
                (player, pig, arrow, shots) -> expectFlewPast(helper, player, pig, arrow, shots));
    }

    public static void missedCrossbowShotFliesPastTheTarget(GameTestHelper helper) {
        realShot(
                helper,
                Items.CROSSBOW,
                new int[] {2},
                3.5,
                (player, pig, arrow, shots) -> expectFlewPast(helper, player, pig, arrow, shots));
    }

    public static void missedShotCanStillHitWhatIsBehindTheTarget(GameTestHelper helper) {
        // a tick of flight (~3 blocks) apart: an arrow takes one entity hit per tick
        Pig behind = spawnShotTarget(helper, 4.5);
        realShot(helper, Items.BOW, new int[] {2, 15}, 1.5, (player, pig, arrow, shots) -> {
            // misses the near pig, flies on, and rolls fresh against the one behind it
            if (shots.d20Rolls() != 2) {
                helper.fail("expected one roll per pig, rolled " + shots.d20Rolls() + flight(helper, arrow, pig));
            }
            expectHealth(helper, pig, pig.getMaxHealth());
            if (behind.getHealth() >= behind.getMaxHealth()) {
                helper.fail("the arrow that missed must still hit the pig behind", behind);
            }
        });
    }

    private static void expectFlewPast(
            GameTestHelper helper, Player player, Pig pig, AbstractArrow arrow, ShotRandom shots) {
        // 2 + at most +5 misses pig AC 10 (no player bonus here). Vanilla bounces an arrow whose hurt failed back the
        // way
        // it came, so a miss used to fly back into the shooter and roll again against them.
        if (shots.d20Rolls() != 1) {
            helper.fail("a missed shot must roll once and never again, rolled " + shots.d20Rolls()
                    + flight(helper, arrow, pig));
        }
        expectHealth(helper, pig, pig.getMaxHealth());
        expectHealth(helper, player, player.getMaxHealth());
        if (helper.relativeVec(arrow.position()).z <= helper.relativeVec(pig.position()).z) {
            helper.fail("a missed arrow must fly on past the target" + flight(helper, arrow, pig), arrow);
        }
    }

    /** Where the arrow ended up relative to the target, for failure messages. */
    private static String flight(GameTestHelper helper, AbstractArrow arrow, Pig pig) {
        return " (arrow at " + helper.relativeVec(arrow.position()) + (arrow.isRemoved() ? ", removed" : "")
                + " in " + helper.getLevel().getBlockState(arrow.blockPosition()) + " above "
                + helper.getLevel().getBlockState(arrow.blockPosition().below()) + " v=" + arrow.getDeltaMovement()
                + ", target at " + helper.relativeVec(pig.position()) + ")";
    }

    private static final Rules NO_PLAYER_BONUS =
            CombatScenarios.withBalance(Rules.DEFAULTS, new Rules.Balance(1.0, false, 4, 4, 0));

    private interface ShotCheck {
        void check(Player player, Pig pig, AbstractArrow arrow, ShotRandom shots);
    }

    /**
     * A survival fake player fires {@code weapon} for real, full draw, at a still pig at {@code targetZ};
     * the d20 shows {@code d20Faces} in order (the last repeats), every other die its max face. The player gets
     * no {@code balance.player_attack_bonus}, so a low d20 can still miss.
     */
    private static void realShot(GameTestHelper helper, Item weapon, int[] d20Faces, double targetZ, ShotCheck check) {
        Pig pig = spawnShotTarget(helper, targetZ);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 position = helper.absoluteVec(new Vec3(1.5, SHOT_Y, 0.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(player);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        ItemStack stack = new ItemStack(weapon);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getInventory().add(new ItemStack(Items.ARROW, 16));

        ShotRandom shots = new ShotRandom(d20Faces);
        Rules before = RollRuntime.rules();
        RollRuntime.setRoller(new DiceRoller(shots));
        RollRuntime.setRules(NO_PLAYER_BONUS);
        FumbleCooldowns.clear();

        player.startUsingItem(InteractionHand.MAIN_HAND);
        if (stack.is(Items.CROSSBOW)) {
            stack.releaseUsing(helper.getLevel(), player, stack.getUseDuration(player) - 40); // fully charged
            player.stopUsingItem();
            stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND); // fires the loaded bolt
        } else {
            stack.releaseUsing(helper.getLevel(), player, stack.getUseDuration(player) - 25); // full draw
            player.stopUsingItem();
        }
        List<AbstractArrow> fired = helper.getLevel()
                .getEntitiesOfClass(
                        AbstractArrow.class, player.getBoundingBox().inflate(3), a -> a.getOwner() == player);
        if (fired.size() != 1) {
            restoreRolls(before);
            helper.fail("the " + weapon + " must fire exactly one arrow, fired " + fired.size());
            return;
        }
        AbstractArrow arrow = fired.get(0);
        helper.runAfterDelay(30, () -> {
            try {
                check.check(player, pig, arrow, shots);
            } finally {
                restoreRolls(before);
                player.discard();
                arrow.discard();
            }
            helper.succeed();
        });
    }

    /**
     * The shooter stands at y 2, on the NeoForge template's floor (y 1), and every shot stays inside the
     * template's 5x5 footprint. Targets hang level with the arrow's launch height (a no-AI pig stays
     * where it is put), so a shot flies flat instead of into the floor.
     */
    private static final int SHOT_Y = 2;

    private static final double TARGET_Y = SHOT_Y + 1.07;

    private static Pig spawnShotTarget(GameTestHelper helper, double z) {
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(1.5, TARGET_Y, z));
        pig.setNoAi(true);
        return pig;
    }

    private static void restoreRolls(Rules before) {
        RollRuntime.setRoller(new DiceRoller(new Random()));
        RollRuntime.setRules(before);
        FumbleCooldowns.clear();
    }

    /** Scripted d20 faces (the last repeats) that counts its d20 draws; every other die shows its max. */
    private static final class ShotRandom implements RandomGenerator {

        private final int[] d20Faces;
        private int d20Rolls;

        ShotRandom(int[] d20Faces) {
            this.d20Faces = d20Faces;
        }

        @Override
        public int nextInt(int bound) {
            if (bound != 20) {
                return bound - 1;
            }
            int face = d20Faces[Math.min(d20Rolls, d20Faces.length - 1)];
            d20Rolls++;
            return face - 1;
        }

        @Override
        public long nextLong() {
            throw new IllegalStateException("dice rolling must go through nextInt(bound)");
        }

        int d20Rolls() {
            return d20Rolls;
        }
    }

    /** An arrow carrying the firing-weapon stack, the way bow/crossbow shots record it. */
    static Arrow shotArrow(GameTestHelper helper, LivingEntity shooter) {
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
