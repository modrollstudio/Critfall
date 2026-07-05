package studio.modroll.critfall.neoforge.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.OutcomeEffect;
import studio.modroll.critfall.data.OutcomeTable;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.data.Trigger;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.feedback.ConsequenceLine;
import studio.modroll.critfall.outcome.OutcomeExecutor;

/**
 * End-to-end tests of the M4 outcome-table executor with forced nat 1 / nat 20 rolls. Custom
 * single-effect tables are injected under the id the sword item profile references
 * ({@code critfall:default_melee} / {@code default_crit}), so each consequence is exercised in
 * isolation and without a weighted-pick RNG draw. The attacker holds an iron sword — dice
 * {@code 1d8} plus a bonus derived from the live attack-damage attribute.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class OutcomeGameTests {

    private static final String TEMPLATE = "empty";
    private static final float VANILLA_HIT = 3.0F;
    private static final ResourceLocation MELEE_TABLE = ResourceLocation.parse("critfall:default_melee");
    private static final ResourceLocation CRIT_TABLE = ResourceLocation.parse("critfall:default_crit");

    @GameTest(template = TEMPLATE)
    public void natTwentyAppliesStatusEffectFromDefaultTable(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // nat 20 crit, then the default_crit weighted pick (d4): face 1 = apply_effect
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (!pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                        helper.fail("the shot-in-the-eye crit effect must slow the target");
                    }
                },
                20,
                1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void natTwentyKnockbackShovesTheTarget(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withTables(
                Map.of(CRIT_TABLE, table(CRIT_TABLE, new Trigger.Natural(20), new OutcomeEffect.Knockback(5.0))),
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            // vanilla hit knockback alone moves ~0.4 horizontally; strength 5 adds 2.0
                            if (pig.getDeltaMovement().horizontalDistance() <= 1.0) {
                                helper.fail("crit knockback must shove far beyond vanilla knockback, moved "
                                        + pig.getDeltaMovement().horizontalDistance());
                            }
                        },
                        20));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fumbleHitsNearestAllyWithTheAttacksDice(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        Pig ally = spawnCalm(helper, EntityType.PIG, 1, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        int bonus = Derivation.itemDamageBonus(husk.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.5);
        withTables(
                Map.of(
                        MELEE_TABLE,
                        table(
                                MELEE_TABLE,
                                new Trigger.Natural(1),
                                new OutcomeEffect.HitNearestAlly(OptionalInt.of(2)))),
                () -> withRolls(
                        helper,
                        withFumbles(fumbles(
                                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                                Rules.SelfDamage.DEFAULTS,
                                false,
                                Rules.Stumble.DEFAULTS,
                                Rules.AppliesTo.PLAYERS_AND_MOBS)),
                        () -> {
                            // nat 1 (no confirmation), redirect rolls the sword dice: 1d8 shows 5
                            target.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            expectHealth(helper, target, target.getMaxHealth());
                            expectHealth(helper, ally, ally.getMaxHealth() - (5.0F + bonus));
                        },
                        1,
                        5));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void barehandedMobFumbleCanHitItsPackmate(GameTestHelper helper) {
        // Regression: mobs hold no weapon, so no item profile supplies a fumble table — the
        // zombies entity profile's default_unarmed table (shipped datapack, NOT injected) must
        // provide the hit_nearest_ally consequence, or vanilla hordes never fumble into each other.
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        Pig packmate = spawnCalm(helper, EntityType.PIG, 1, 3);
        Rules rules = withFumbles(fumbles(
                new Rules.HitNearestAlly(true, 2, true, true),
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withRolls(
                helper,
                rules,
                () -> {
                    // nat 1, default_unarmed pick (d4): face 1 = hit_nearest_ally, then the husk's
                    // entity-profile dice 1d6+1 roll a 4 -> the packmate takes 5
                    target.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, target, target.getMaxHealth());
                    expectHealth(helper, packmate, packmate.getMaxHealth() - 5.0F);
                },
                1,
                1,
                4);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void hitNearestAllyIgnoresPlayersWhenPolicyForbidsIt(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        Player bystander = mockPlayerAt(helper, 1.5, 3.5);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                new Rules.HitNearestAlly(true, 4, false, true),
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withTables(
                Map.of(
                        MELEE_TABLE,
                        table(
                                MELEE_TABLE,
                                new Trigger.Natural(1),
                                new OutcomeEffect.HitNearestAlly(OptionalInt.of(2)))),
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            // can_hit_players false: nobody eligible, so no redirect dice are drawn
                            target.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            expectHealth(helper, bystander, bystander.getMaxHealth());
                        },
                        1));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void hitNearestAllyRespectsTheServerPvpRule(GameTestHelper helper) {
        Player attacker = mockPlayerAt(helper, 1.5, 1.5);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        Player victim = mockPlayerAt(helper, 1.5, 3.5);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        int bonus = Derivation.itemDamageBonus(attacker.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.5);
        Rules rules = withFumbles(fumbles(
                new Rules.HitNearestAlly(true, 4, true, true),
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvpBefore = server.isPvpAllowed();
        try {
            withTables(
                    Map.of(
                            MELEE_TABLE,
                            table(
                                    MELEE_TABLE,
                                    new Trigger.Natural(1),
                                    new OutcomeEffect.HitNearestAlly(OptionalInt.of(2)))),
                    () -> {
                        server.setPvpAllowed(false);
                        withRolls(
                                helper,
                                rules,
                                () -> {
                                    target.hurt(
                                            helper.getLevel().damageSources().playerAttack(attacker), VANILLA_HIT);
                                    expectHealth(helper, victim, victim.getMaxHealth());
                                },
                                1);
                        server.setPvpAllowed(true);
                        withRolls(
                                helper,
                                rules,
                                () -> {
                                    target.hurt(
                                            helper.getLevel().damageSources().playerAttack(attacker), VANILLA_HIT);
                                    expectHealth(helper, victim, victim.getMaxHealth() - (5.0F + bonus));
                                },
                                1,
                                5);
                    });
        } finally {
            server.setPvpAllowed(pvpBefore);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void hitNearestAllyRespectsTeamFriendlyFire(GameTestHelper helper) {
        Player attacker = mockPlayerAt(helper, 1.5, 1.5);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        Player teammate = mockPlayerAt(helper, 1.5, 3.5);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                new Rules.HitNearestAlly(true, 4, true, true),
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvpBefore = server.isPvpAllowed();
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("critfall_gametest");
        team.setAllowFriendlyFire(false);
        scoreboard.addPlayerToTeam(attacker.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
        try {
            server.setPvpAllowed(true);
            withTables(
                    Map.of(
                            MELEE_TABLE,
                            table(
                                    MELEE_TABLE,
                                    new Trigger.Natural(1),
                                    new OutcomeEffect.HitNearestAlly(OptionalInt.of(2)))),
                    () -> withRolls(
                            helper,
                            rules,
                            () -> {
                                // same team, friendly fire off: the teammate cannot be redirected to
                                target.hurt(helper.getLevel().damageSources().playerAttack(attacker), VANILLA_HIT);
                                expectHealth(helper, teammate, teammate.getMaxHealth());
                            },
                            1));
        } finally {
            server.setPvpAllowed(pvpBefore);
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fumbleSelfDamageRollsTheConfiguredDice(GameTestHelper helper) {
        Player attacker = mockPlayerAt(helper, 1.5, 1.5);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                new Rules.SelfDamage(true, Rules.SelfDamage.DEFAULTS.dice()),
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withTables(
                Map.of(
                        MELEE_TABLE,
                        table(MELEE_TABLE, new Trigger.Natural(1), new OutcomeEffect.SelfDamage(Optional.empty()))),
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            // nat 1, then the rules-default 1d4 self damage rolls a 3
                            target.hurt(helper.getLevel().damageSources().playerAttack(attacker), VANILLA_HIT);
                            expectHealth(helper, attacker, attacker.getMaxHealth() - 3.0F);
                        },
                        1,
                        3));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fumbleDropsTheWeapon(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                Rules.SelfDamage.DEFAULTS,
                true,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withTables(
                Map.of(MELEE_TABLE, table(MELEE_TABLE, new Trigger.Natural(1), new OutcomeEffect.DropWeapon())),
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            target.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            if (!husk.getMainHandItem().isEmpty()) {
                                helper.fail("a drop_weapon fumble must empty the attacker's hand");
                            }
                        },
                        1));
        helper.assertItemEntityPresent(Items.IRON_SWORD, new BlockPos(1, 1, 1), 2.0);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fumbleStumbleSlowsTheAttacker(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                Rules.SelfDamage.DEFAULTS,
                false,
                new Rules.Stumble(true, 40),
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withTables(
                Map.of(
                        MELEE_TABLE,
                        table(MELEE_TABLE, new Trigger.Natural(1), new OutcomeEffect.Stumble(OptionalInt.empty()))),
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            target.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            if (!husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                                helper.fail("a stumble fumble must slow the attacker");
                            }
                        },
                        1));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void fumbleReturnsDurabilityConsequence(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // nat 1 (miss) then confirmation d20 = 2 (< DC 10) -> confirmed fumble
                    AttackResult result = new AttackResult(AttackOutcome.FUMBLE, 1, 4, 10, 0);
                    List<ConsequenceLine> lines = OutcomeExecutor.run(
                            husk,
                            pig,
                            result,
                            DiceExpression.parse("1d8"),
                            Rules.DEFAULTS,
                            RollService.roller(),
                            husk.getMainHandItem(),
                            ProfileLookup.forItem(husk.getMainHandItem()),
                            ProfileLookup.forEntity(husk));
                    if (lines.stream().noneMatch(l -> l.key().equals(ConsequenceLine.DURABILITY_BROKEN))) {
                        helper.fail("fumble should report the durability_broken consequence, got " + lines);
                    }
                },
                1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void disabledConsequenceIsANoOpEvenWhenPicked(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        // drop_weapon stays at its default (disabled) — the table naming it must do nothing
        Rules rules = withFumbles(fumbles(
                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withTables(
                Map.of(MELEE_TABLE, table(MELEE_TABLE, new Trigger.Natural(1), new OutcomeEffect.DropWeapon())),
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            target.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            if (husk.getMainHandItem().isEmpty()) {
                                helper.fail("drop_weapon is disabled in rules.json and must not fire");
                            }
                        },
                        1));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void appliesToMobsOnlySuppressesPlayerFumbles(GameTestHelper helper) {
        Player attacker = mockPlayerAt(helper, 1.5, 1.5);
        Pig target = spawnCalm(helper, EntityType.PIG, 3, 1);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.MOBS));
        withRolls(
                helper,
                rules,
                () -> {
                    // a player's nat 1 is a plain miss: no confirmation, no table pick, no wear
                    target.hurt(helper.getLevel().damageSources().playerAttack(attacker), VANILLA_HIT);
                    if (attacker.getMainHandItem().getDamageValue() != 0) {
                        helper.fail("applies_to=mobs must leave player fumbles consequence-free");
                    }
                },
                1);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void missMarginTableFiresOnABigMiss(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Wolf wolf = spawnCalm(helper, EntityType.WOLF, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(
                Rules.Fumbles.DEFAULTS.hitNearestAlly(),
                Rules.SelfDamage.DEFAULTS,
                false,
                new Rules.Stumble(true, 40),
                Rules.AppliesTo.PLAYERS_AND_MOBS));
        withTables(
                Map.of(
                        MELEE_TABLE,
                        table(
                                MELEE_TABLE,
                                new Trigger.MissByAtLeast(5),
                                new OutcomeEffect.Stumble(OptionalInt.empty()))),
                () -> withRolls(
                        helper,
                        rules,
                        () -> {
                            // 3 + 3 = 6 vs wolf AC 12: missed by 6 — the fumble-slot table triggers on margin
                            wolf.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            if (!husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                                helper.fail("a miss_by_at_least table in the fumble slot must fire on a big miss");
                            }
                        },
                        3));
        helper.succeed();
    }

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /**
     * A survival mock player that is actually IN the level — {@code makeMockPlayer} only
     * constructs the entity, and the hit_nearest_ally AABB search can only find added entities.
     */
    private static Player mockPlayerAt(GameTestHelper helper, double x, double z) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 position = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static OutcomeTable table(ResourceLocation id, Trigger trigger, OutcomeEffect effect) {
        return new OutcomeTable(id, trigger, List.of(new OutcomeTable.WeightedEffect(effect, 1)));
    }

    /** Swaps in outcome-table overrides for the duration of {@code action}, then restores the pack. */
    private static void withTables(Map<ResourceLocation, OutcomeTable> overrides, Runnable action) {
        Map<ResourceLocation, OutcomeTable> before = ProfileStore.outcomeTables();
        Map<ResourceLocation, OutcomeTable> merged = new HashMap<>(before);
        merged.putAll(overrides);
        ProfileStore.setOutcomeTables(merged);
        try {
            action.run();
        } finally {
            ProfileStore.setOutcomeTables(before);
        }
    }

    /** Runs {@code action} under the given rules and scripted roller, then restores everything. */
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

    /** Fumbles with confirmation OFF and no cooldown so a scripted nat 1 always confirms. */
    private static Rules.Fumbles fumbles(
            Rules.HitNearestAlly hitNearestAlly,
            Rules.SelfDamage selfDamage,
            boolean dropWeapon,
            Rules.Stumble stumble,
            Rules.AppliesTo appliesTo) {
        return new Rules.Fumbles(
                true,
                true,
                false,
                10,
                0,
                true,
                Rules.DurabilityMode.SET_TO_1,
                25,
                hitNearestAlly,
                selfDamage,
                dropWeapon,
                stumble,
                appliesTo);
    }
}
