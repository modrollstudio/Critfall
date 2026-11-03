package studio.modroll.critfall.gametest;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.feedback.CapturingFeedbackSink;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * End-to-end tests of the damage interception: real entities, real {@code LivingEntity.hurt},
 * scripted RNG (PLAN.md §6). Stats come from the shipped default datapack: husk (zombies profile)
 * attacks with +3 and 1d4+1 melee dice; pig (livestock profile) has AC 10; wolf has AC 12.
 * Every rules.json flag has a test proving that turning it off restores vanilla behavior for
 * that mechanic only.
 */
public final class CombatScenarios {

    private static final String TEMPLATE = "empty";
    private static final float VANILLA_HIT = 3.0F;

    public static void missCancelsAllDamage(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 5 + 3 (husk profile) = 8 vs AC 10 (pig profile) -> miss
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("miss must deal no damage");
                    }
                },
                5);
        helper.succeed();
    }

    public static void hitAppliesProfileDiceInsteadOfVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 13 + 3 = 16 vs AC 10 -> hit, then profile dice 1d4+1 roll a 4 -> 5 damage
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                13,
                4);
        helper.succeed();
    }

    public static void naturalTwentyDealsMaximizedDice(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // nat 20 -> crit: 1d4+1 maximized to 5, no damage dice drawn from the RNG
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                20);
        helper.succeed();
    }

    public static void profileArmorClassDecidesTheHit(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Wolf wolf = spawnCalm(helper, EntityType.WOLF, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 8 + 3 = 11 vs wolf profile AC 12 -> miss
                    wolf.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, wolf, wolf.getMaxHealth());
                },
                8);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 9 + 3 = 12 vs AC 12 -> exact hit, 1d4+1 rolls a 3 -> 4 damage
                    wolf.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, wolf, wolf.getMaxHealth() - 4.0F);
                },
                9,
                3);
        helper.succeed();
    }

    public static void heldWeaponUsesItemProfileDice(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        // Sword item profile: 1d8 + bonus derived from the husk's live attack damage attribute.
        int bonus = Derivation.itemDamageBonus(husk.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.5);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - (3.0F + bonus));
                },
                13,
                3);
        helper.succeed();
    }

    public static void attackRollsDisabledRestoresVanillaDamage(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules rules = withAttackRolls(new Rules.AttackRolls(false, true, true, true, true));
        withRolls(helper, rules, () -> {
            // no faces scripted: any roll would fail the test
            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        });
        helper.succeed();
    }

    public static void mobRollsDisabledRestoresVanillaForMobs(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules rules = withAttackRolls(new Rules.AttackRolls(true, true, false, true, true));
        withRolls(helper, rules, () -> {
            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        });
        helper.succeed();
    }

    public static void playerRollsDisabledRestoresVanillaForPlayers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules rules = withAttackRolls(new Rules.AttackRolls(true, false, true, true, true));
        withRolls(helper, rules, () -> {
            pig.hurt(helper.getLevel().damageSources().playerAttack(player), VANILLA_HIT);
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        });
        helper.succeed();
    }

    public static void damageDiceDisabledKeepsVanillaAmountOnHit(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                false,
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
        withRolls(
                helper,
                rules,
                () -> {
                    // to-hit still rolls (13 + 3 = 16 vs 10) but the vanilla 3.0 goes through
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
                },
                13);
        helper.succeed();
    }

    public static void critsDisabledRollsNormalDamageOnNatTwenty(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules rules = withCrits(new Rules.Crits(false, Rules.CritRule.MAX_DICE, true, true, true));
        withRolls(
                helper,
                rules,
                () -> {
                    // nat 20 still auto-hits, but damage is rolled (4 + 1 = 5), not maximized
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                20,
                4);
        helper.succeed();
    }

    public static void fumblesDisabledLeaveWeaponUntouched(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(false, true, 10, 200, true, Rules.DurabilityMode.SET_TO_1, 25));
        withRolls(
                helper,
                rules,
                () -> {
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (hurt) {
                        helper.fail("nat 1 must still miss");
                    }
                    if (husk.getMainHandItem().getDamageValue() != 0) {
                        helper.fail("fumbles off must leave durability untouched");
                    }
                },
                1);
        helper.succeed();
    }

    public static void fumbleSetsWeaponDurabilityToOne(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        Rules rules = withFumbles(fumbles(true, false, 10, 0, true, Rules.DurabilityMode.SET_TO_1, 25));
        withRolls(
                helper,
                rules,
                () -> {
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("a fumble must miss");
                    }
                    ItemStack weapon = husk.getMainHandItem();
                    if (weapon.getDamageValue() != weapon.getMaxDamage() - 1) {
                        helper.fail("fumble must leave the weapon at 1 durability, was "
                                + (weapon.getMaxDamage() - weapon.getDamageValue()));
                    }
                },
                // nat 1, then the default_melee weighted pick (d5): face 1 = damage_durability
                1,
                1);
        helper.succeed();
    }

    public static void fumbleConfirmationRollSavesTheWeapon(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withRolls(
                helper,
                Rules.DEFAULTS, // confirmation on, DC 10
                () -> {
                    // nat 1, then confirmation 15 >= 10 succeeds -> plain miss, weapon safe
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (husk.getMainHandItem().getDamageValue() != 0) {
                        helper.fail("a saved fumble must not damage the weapon");
                    }
                },
                1,
                15);
        helper.succeed();
    }

    public static void fumbleConfirmationFailureTriggersConsequence(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withRolls(
                helper,
                WEAPON_DAMAGE,
                () -> {
                    // nat 1, confirmation 5 < 10 fails -> fumble confirmed, table pick 1 = durability
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    ItemStack weapon = husk.getMainHandItem();
                    if (weapon.getDamageValue() != weapon.getMaxDamage() - 1) {
                        helper.fail("confirmed fumble must set durability to 1");
                    }
                },
                1,
                5,
                1);
        helper.succeed();
    }

    public static void defaultFumbleLeavesWeaponAlone(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // nat 1, confirmation 5 < 10 fails -> fumble confirmed, table pick 1 = durability,
                    // which ships off: the pick is a no-op
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (hurt) {
                        helper.fail("a fumble must still miss");
                    }
                    if (husk.getMainHandItem().getDamageValue() != 0) {
                        helper.fail("weapon damage is opt-in: a default fumble must leave the weapon whole");
                    }
                },
                1,
                5,
                1);
        helper.succeed();
    }

    public static void fumbleCooldownDowngradesTheSecondNatOne(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        husk.setItemSlot(EquipmentSlot.MAINHAND, sword);
        Rules rules = withFumbles(fumbles(true, false, 10, 200, true, Rules.DurabilityMode.PERCENT_LOSS, 25));
        int expectedLoss = sword.getMaxDamage() * 25 / 100;
        withRolls(
                helper,
                rules,
                () -> {
                    // first nat 1 fumbles: table pick 1 = durability, percent_loss takes 25% of max
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (husk.getMainHandItem().getDamageValue() != expectedLoss) {
                        helper.fail("percent_loss fumble must cost 25% durability, damage was "
                                + husk.getMainHandItem().getDamageValue());
                    }
                    // second nat 1 within the 10s cooldown: plain miss, no confirmation, no wear
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    if (husk.getMainHandItem().getDamageValue() != expectedLoss) {
                        helper.fail("a nat 1 on cooldown must not damage the weapon again");
                    }
                },
                1,
                1,
                1);
        helper.succeed();
    }

    public static void rolledDamageBypassesVanillaArmorReduction(GameTestHelper helper) {
        Husk attacker = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk armored = spawnCalm(helper, EntityType.HUSK, 3, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float before = armored.getHealth();
        withRolls(
                helper,
                BYPASS_ARMOR,
                () -> {
                    // nat 20 -> crit for exactly 5 (1d4+1 maxed); with vanilla armor reduction
                    // active the armored husk would take noticeably less.
                    armored.hurt(helper.getLevel().damageSources().mobAttack(attacker), VANILLA_HIT);
                    expectHealth(helper, armored, before - 5.0F);
                },
                20);
        helper.succeed();
    }

    public static void armorReductionAppliesWhenBypassFlagIsOff(GameTestHelper helper) {
        Husk attacker = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk armored = spawnCalm(helper, EntityType.HUSK, 3, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float before = armored.getHealth();
        withRolls(
                helper,
                Rules.DEFAULTS, // the flag ships off since 0.2.18
                () -> {
                    armored.hurt(helper.getLevel().damageSources().mobAttack(attacker), VANILLA_HIT);
                    float lost = before - armored.getHealth();
                    if (lost <= 0.0F || lost >= 5.0F) {
                        helper.fail("vanilla armor must reduce the 5-damage crit, but " + lost + " was lost");
                    }
                },
                20);
        helper.succeed();
    }

    public static void globalDamageMultiplierScalesRolledDamage(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                new Rules.Balance(2.0, true));
        withRolls(
                helper,
                rules,
                () -> {
                    // hit for (4 + 1) * 2 = 10
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - 10.0F);
                },
                13,
                4);
        helper.succeed();
    }

    /**
     * A husk's rolled hit on a player scales with difficulty like vanilla's mob damage does: d20 13 + 3
     * vs AC 10 hits, 1d4+1 rolls 4 for 5, then Easy halves it plus 1 and Hard adds half.
     */
    public static void mobHitOnPlayerScalesOnEasy(GameTestHelper helper) {
        mobHitOnPlayerScales(helper, Difficulty.EASY, 3.5F);
    }

    public static void mobHitOnPlayerScalesOnNormal(GameTestHelper helper) {
        mobHitOnPlayerScales(helper, Difficulty.NORMAL, 5.0F);
    }

    public static void mobHitOnPlayerScalesOnHard(GameTestHelper helper) {
        mobHitOnPlayerScales(helper, Difficulty.HARD, 7.5F);
    }

    private static void mobHitOnPlayerScales(GameTestHelper helper, Difficulty difficulty, float expectedLoss) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        withDifficulty(
                helper,
                difficulty,
                () -> withRolls(
                        helper,
                        Rules.DEFAULTS,
                        () -> {
                            player.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                            expectHealth(helper, player, player.getMaxHealth() - expectedLoss);
                        },
                        13,
                        4));
        helper.succeed();
    }

    /**
     * Dice derived from the vanilla amount come from the amount before difficulty, so Hard scales the
     * roll once, not twice: a creeper's profile has no melee dice, so its vanilla 3 derives 1d6 (not
     * the 1d10 that Hard's 4.5 would), d20 13 + 2 vs AC 10 hits, 1d6 rolls 6, and Hard makes it 9.
     */
    public static void derivedDiceOnPlayerScaleOnceOnHard(GameTestHelper helper) {
        Creeper creeper = spawnCalm(helper, EntityType.CREEPER, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            withDifficulty(
                    helper,
                    Difficulty.HARD,
                    () -> withRolls(
                            helper,
                            Rules.DEFAULTS,
                            () -> {
                                player.hurt(helper.getLevel().damageSources().mobAttack(creeper), VANILLA_HIT);
                                expectHealth(helper, player, player.getMaxHealth() - 9.0F);
                                RollFeedbackPayload payload = sink.lastRoll();
                                if (payload == null || !payload.diceNotation().equals("1d6")) {
                                    helper.fail("expected dice derived from the vanilla 3, was " + payload);
                                }
                            },
                            13,
                            6));
        } finally {
            FeedbackSink.set(previous);
        }
        helper.succeed();
    }

    /**
     * Full iron (armor 15) is AC 13 since 0.2.18, not 17, and armor reduces the hit like vanilla: d20 10 + 3
     * hits, 1d4+1 rolls 4 for 5, and armor 15 takes half of a 5-point hit, so 2.5 is lost.
     */
    public static void armoredPlayerIsEasierToHitButTakesLess(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // A mock player never ticks, so its equipment would never reach its attributes: set the armor itself.
        player.getAttribute(Attributes.ARMOR).setBaseValue(15.0);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    player.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, player, player.getMaxHealth() - 2.5F);
                },
                10,
                4);
        helper.succeed();
    }

    /**
     * A player's derived attack bonus gets {@code balance.player_attack_bonus}: attack damage 6 derives +3, plus
     * 5 is +8, so d20 4 reaches the wolf's AC 12. Without the bonus 4 + 3 would miss.
     */
    public static void playerAttackBonusAddsToTheDerivedBonus(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
        Wolf wolf = spawnCalm(helper, EntityType.WOLF, 3, 3);
        CapturingFeedbackSink sink = new CapturingFeedbackSink();
        FeedbackSink previous = FeedbackSink.get();
        FeedbackSink.set(sink);
        try {
            withRolls(
                    helper,
                    Rules.DEFAULTS,
                    () -> {
                        wolf.hurt(helper.getLevel().damageSources().playerAttack(player), 6.0F);
                        RollFeedbackPayload payload = sink.lastRoll();
                        if (payload == null || payload.attackTotal() != 12 || payload.outcome() != AttackOutcome.HIT) {
                            helper.fail("expected d20 4 + 8 = 12 to hit AC 12, was " + payload);
                        }
                    },
                    4,
                    5);
        } finally {
            FeedbackSink.set(previous);
        }
        helper.succeed();
    }

    /** Runs {@code action} at {@code difficulty}, then puts the server's difficulty back. */
    static void withDifficulty(GameTestHelper helper, Difficulty difficulty, Runnable action) {
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty before = server.getWorldData().getDifficulty();
        server.setDifficulty(difficulty, true);
        try {
            action.run();
        } finally {
            server.setDifficulty(before, true);
        }
    }

    static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /** The shipped defaults with rolled hits skipping vanilla armor reduction, the rule before 0.2.18. */
    static final Rules BYPASS_ARMOR = withBalance(Rules.DEFAULTS, new Rules.Balance(1.0, true));

    static Rules withBalance(Rules rules, Rules.Balance balance) {
        return new Rules(
                rules.attackRolls(),
                rules.damageDice(),
                rules.crits(),
                rules.fumbles(),
                rules.spells(),
                rules.fallbacks(),
                rules.feedback(),
                balance,
                rules.dryRun(),
                rules.modifierProviders(),
                rules.advantageSources());
    }

    /** The shipped defaults with fumble weapon damage turned on, which ships off since 0.2.17. */
    static final Rules WEAPON_DAMAGE = withWeaponDamage(Rules.DEFAULTS);

    static Rules withWeaponDamage(Rules rules) {
        Rules.Fumbles f = rules.fumbles();
        return new Rules(
                rules.attackRolls(),
                rules.damageDice(),
                rules.crits(),
                new Rules.Fumbles(
                        f.enabled(),
                        f.nat1AlwaysMisses(),
                        f.confirmationRoll(),
                        f.confirmationDc(),
                        f.cooldownTicks(),
                        true,
                        f.durabilityMode(),
                        f.durabilityPercent(),
                        f.hitNearestAlly(),
                        f.selfDamage(),
                        f.dropWeapon(),
                        f.stumble(),
                        f.appliesTo()),
                rules.spells(),
                rules.fallbacks(),
                rules.feedback(),
                rules.balance(),
                rules.dryRun(),
                rules.modifierProviders(),
                rules.advantageSources());
    }

    /**
     * The shipped defaults with a flavor line on every kill: for tests of which line a kill gets, not
     * how often. At 100 the kill chance draws no die, so the cosmetic roller only picks the line.
     */
    static final Rules EVERY_KILL_LINE = withKillChance(Rules.DEFAULTS, 100);

    static Rules withKillChance(Rules rules, int percent) {
        Rules.Feedback feedback = rules.feedback();
        return new Rules(
                rules.attackRolls(),
                rules.damageDice(),
                rules.crits(),
                rules.fumbles(),
                rules.spells(),
                rules.fallbacks(),
                new Rules.Feedback(
                        feedback.visibility(),
                        new Rules.Feedback.Flavor(
                                feedback.flavor().enabled(), feedback.flavor().cooldownTicks(), percent)),
                rules.balance(),
                rules.dryRun(),
                rules.modifierProviders(),
                rules.advantageSources());
    }

    /** Runs {@code action} under the given rules and scripted roller, then restores everything. */
    static void withRolls(GameTestHelper helper, Rules rules, Runnable action, int... faces) {
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

    private static Rules withAttackRolls(Rules.AttackRolls attackRolls) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                attackRolls,
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
    }

    private static Rules withCrits(Rules.Crits crits) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                base.attackRolls(),
                base.damageDice(),
                crits,
                base.fumbles(),
                base.spells(),
                base.fallbacks(),
                base.feedback(),
                base.balance());
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

    private static Rules.Fumbles fumbles(
            boolean enabled,
            boolean confirmation,
            int dc,
            int cooldownTicks,
            boolean durabilityBreak,
            Rules.DurabilityMode mode,
            int percent) {
        return new Rules.Fumbles(
                enabled,
                true,
                confirmation,
                dc,
                cooldownTicks,
                durabilityBreak,
                mode,
                percent,
                Rules.HitNearestAlly.DEFAULTS,
                Rules.SelfDamage.DEFAULTS,
                false,
                Rules.Stumble.DEFAULTS,
                Rules.AppliesTo.PLAYERS_AND_MOBS);
    }
}
