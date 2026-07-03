package studio.modroll.critfall.neoforge.gametest;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * End-to-end tests of the damage interception: real entities, real {@code LivingEntity.hurt},
 * scripted RNG (PLAN.md §6). Stats come from the shipped default datapack: husk (zombies profile)
 * attacks with +3 and 1d6+1 melee dice; pig (livestock profile) has AC 10; wolf has AC 12.
 * Every rules.json flag has a test proving that turning it off restores vanilla behavior for
 * that mechanic only.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class CombatGameTests {

    private static final String TEMPLATE = "empty";
    private static final float VANILLA_HIT = 3.0F;

    @GameTest(template = TEMPLATE)
    public void missCancelsAllDamage(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void hitAppliesProfileDiceInsteadOfVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // 13 + 3 = 16 vs AC 10 -> hit, then profile dice 1d6+1 roll a 4 -> 5 damage
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - 5.0F);
                },
                13,
                4);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void naturalTwentyDealsMaximizedDice(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // nat 20 -> crit: 1d6+1 maximized to 7, no damage dice drawn from the RNG
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, pig, pig.getMaxHealth() - 7.0F);
                },
                20);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void profileArmorClassDecidesTheHit(GameTestHelper helper) {
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
                    // 9 + 3 = 12 vs AC 12 -> exact hit, 1d6+1 rolls a 3 -> 4 damage
                    wolf.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
                    expectHealth(helper, wolf, wolf.getMaxHealth() - 4.0F);
                },
                9,
                3);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void heldWeaponUsesItemProfileDice(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void attackRollsDisabledRestoresVanillaDamage(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void mobRollsDisabledRestoresVanillaForMobs(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules rules = withAttackRolls(new Rules.AttackRolls(true, true, false, true, true));
        withRolls(helper, rules, () -> {
            pig.hurt(helper.getLevel().damageSources().mobAttack(husk), VANILLA_HIT);
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void playerRollsDisabledRestoresVanillaForPlayers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules rules = withAttackRolls(new Rules.AttackRolls(true, false, true, true, true));
        withRolls(helper, rules, () -> {
            pig.hurt(helper.getLevel().damageSources().playerAttack(player), VANILLA_HIT);
            expectHealth(helper, pig, pig.getMaxHealth() - VANILLA_HIT);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void damageDiceDisabledKeepsVanillaAmountOnHit(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                false,
                base.crits(),
                base.fumbles(),
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

    @GameTest(template = TEMPLATE)
    public void critsDisabledRollsNormalDamageOnNatTwenty(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void fumblesDisabledLeaveWeaponUntouched(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void fumbleSetsWeaponDurabilityToOne(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void fumbleConfirmationRollSavesTheWeapon(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void fumbleConfirmationFailureTriggersConsequence(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        withRolls(
                helper,
                Rules.DEFAULTS,
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

    @GameTest(template = TEMPLATE)
    public void fumbleCooldownDowngradesTheSecondNatOne(GameTestHelper helper) {
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

    @GameTest(template = TEMPLATE)
    public void rolledDamageBypassesVanillaArmorReduction(GameTestHelper helper) {
        Husk attacker = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk armored = spawnCalm(helper, EntityType.HUSK, 3, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float before = armored.getHealth();
        withRolls(
                helper,
                Rules.DEFAULTS,
                () -> {
                    // nat 20 -> crit for exactly 7 (1d6+1 maxed); with vanilla armor reduction
                    // active the armored husk would take noticeably less.
                    armored.hurt(helper.getLevel().damageSources().mobAttack(attacker), VANILLA_HIT);
                    expectHealth(helper, armored, before - 7.0F);
                },
                20);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void armorReductionAppliesWhenBypassFlagIsOff(GameTestHelper helper) {
        Husk attacker = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Husk armored = spawnCalm(helper, EntityType.HUSK, 3, 3);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        float before = armored.getHealth();
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
                base.fallbacks(),
                base.feedback(),
                new Rules.Balance(1.0, false));
        withRolls(
                helper,
                rules,
                () -> {
                    armored.hurt(helper.getLevel().damageSources().mobAttack(attacker), VANILLA_HIT);
                    float lost = before - armored.getHealth();
                    if (lost <= 0.0F || lost >= 7.0F) {
                        helper.fail("vanilla armor must reduce the 7-damage crit, but " + lost + " was lost");
                    }
                },
                20);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void globalDamageMultiplierScalesRolledDamage(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        Rules base = Rules.DEFAULTS;
        Rules rules = new Rules(
                base.attackRolls(),
                base.damageDice(),
                base.crits(),
                base.fumbles(),
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

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
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

    private static Rules withAttackRolls(Rules.AttackRolls attackRolls) {
        Rules base = Rules.DEFAULTS;
        return new Rules(
                attackRolls,
                base.damageDice(),
                base.crits(),
                base.fumbles(),
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
