package studio.modroll.critfall.neoforge.gametest;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * End-to-end tests of the damage interception: real entities, real {@code LivingEntity.hurt},
 * scripted RNG (PLAN.md §6). Husk (attack damage 3 → +1 bonus) attacks a pig (no armor → AC 10)
 * with 3.0 flat damage (→ 1d6 dice) unless stated otherwise.
 */
@GameTestHolder(Critfall.MOD_ID)
@PrefixGameTestTemplate(false)
public class CombatGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void missCancelsAllDamage(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    // 5 + 1 = 6 vs AC 10 -> miss
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                    if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("miss must deal no damage");
                    }
                },
                5);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void hitAppliesRolledDamageInsteadOfVanilla(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        withRolls(
                helper,
                () -> {
                    // 13 + 1 = 14 vs AC 10 -> hit, then 1d6 rolls a 4
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                    expectHealth(helper, pig, pig.getMaxHealth() - 4.0F);
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
                () -> {
                    // nat 20 -> crit: 1d6 maximized to 6, no damage dice drawn from the RNG
                    pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                    expectHealth(helper, pig, pig.getMaxHealth() - 6.0F);
                },
                20);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void naturalOneSetsWeaponDurabilityToOne(GameTestHelper helper) {
        Husk husk = spawnCalm(helper, EntityType.HUSK, 1, 1);
        Pig pig = spawnCalm(helper, EntityType.PIG, 3, 3);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        husk.setItemSlot(EquipmentSlot.MAINHAND, sword);
        withRolls(
                helper,
                () -> {
                    boolean hurt = pig.hurt(helper.getLevel().damageSources().mobAttack(husk), 3.0F);
                    if (hurt || pig.getHealth() < pig.getMaxHealth()) {
                        helper.fail("a fumble must miss");
                    }
                    ItemStack weapon = husk.getMainHandItem();
                    if (weapon.getDamageValue() != weapon.getMaxDamage() - 1) {
                        helper.fail("fumble must leave the weapon at 1 durability, was "
                                + (weapon.getMaxDamage() - weapon.getDamageValue()));
                    }
                },
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
                () -> {
                    // nat 20 -> crit for exactly 6; with vanilla armor reduction active the armored husk
                    // (armor 2 innate + 8 chestplate) would take noticeably less than 6.
                    armored.hurt(helper.getLevel().damageSources().mobAttack(attacker), 3.0F);
                    expectHealth(helper, armored, before - 6.0F);
                },
                20);
        helper.succeed();
    }

    private static <T extends Mob> T spawnCalm(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 1, z));
        mob.setNoAi(true);
        return mob;
    }

    /** Runs {@code action} with a scripted roller, verifying every scripted face was consumed. */
    private static void withRolls(GameTestHelper helper, Runnable action, int... faces) {
        ScriptedRandom scripted = ScriptedRandom.ofDieFaces(faces);
        RollService.setRoller(new DiceRoller(scripted));
        try {
            action.run();
            if (!scripted.isExhausted()) {
                helper.fail("fewer dice were rolled than scripted");
            }
        } finally {
            RollService.setRoller(new DiceRoller(new Random()));
        }
    }

    private static void expectHealth(GameTestHelper helper, Mob mob, float expected) {
        if (Math.abs(mob.getHealth() - expected) > 0.001F) {
            helper.fail("expected health " + expected + " but was " + mob.getHealth(), mob);
        }
    }
}
