package studio.modroll.critfall.gametest;

import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.RollService;
import studio.modroll.critfall.combat.Rules;

/**
 * The weapon and buff factors Critfall folds into its damage dice, with and without a modifier provider.
 * Hits go through vanilla's own code ({@code Mob.doHurtTarget}, {@code Arrow.tick}), so each factor
 * arrives the way it does in play. Every hit rolls d20 13 (husk +3 / skeleton +4 vs pig AC 10, zombie AC
 * 9, spider AC 13) and a d8 of 4. Swords and the bow roll 1d8 plus {@code round(vanilla damage - 4.5)}, clamped to 0..12. Targets
 * get 100 health so no hit is capped by a kill. Everything fits NeoForge's 5x5 {@code empty} structure.
 */
public final class DamageFactorScenarios {

    /** What the provider adds; zero without one. */
    private static final int PROVIDED = 2;

    private static final ModifierProvider PLUS_TWO = new ModifierProvider() {
        @Override
        public OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
            return OptionalInt.of(PROVIDED);
        }
    };

    public static void sharpnessWithoutProvider(GameTestHelper helper) {
        sharpness(helper, null);
    }

    public static void sharpnessWithProvider(GameTestHelper helper) {
        sharpness(helper, PLUS_TWO);
    }

    /** Smite I adds 2.5 against undead: iron 8 becomes 10.5, 1d8+6. A pig gets the plain 1d8+4. */
    public static void smiteCountsOnlyAgainstUndead(GameTestHelper helper) {
        Husk vsZombie = husk(helper, 0, enchanted(helper, Items.IRON_SWORD, Enchantments.SMITE, 1));
        Husk vsPig = husk(helper, 1, enchanted(helper, Items.IRON_SWORD, Enchantments.SMITE, 1));
        afterEquipment(helper, () -> {
            expectDamage(helper, "Smite vs zombie", melee(helper, null, vsZombie, EntityType.ZOMBIE, 0), 10);
            expectDamage(helper, "Smite vs pig", melee(helper, null, vsPig, EntityType.PIG, 1), 8);
        });
    }

    /** Bane of Arthropods I adds 2.5 against arthropods: 1d8+6 on a spider, the plain 1d8+4 on a zombie. */
    public static void baneOfArthropodsCountsOnlyAgainstArthropods(GameTestHelper helper) {
        Husk vsSpider = husk(helper, 0, enchanted(helper, Items.IRON_SWORD, Enchantments.BANE_OF_ARTHROPODS, 1));
        Husk vsZombie = husk(helper, 1, enchanted(helper, Items.IRON_SWORD, Enchantments.BANE_OF_ARTHROPODS, 1));
        afterEquipment(helper, () -> {
            expectDamage(helper, "Bane vs spider", melee(helper, null, vsSpider, EntityType.SPIDER, 0), 10);
            expectDamage(helper, "Bane vs zombie", melee(helper, null, vsZombie, EntityType.ZOMBIE, 1), 8);
        });
    }

    /** {@code RollService.performAttack} builds its melee dice the same way. */
    public static void sharpnessCountsOnDrivenAttack(GameTestHelper helper) {
        Husk plain = husk(helper, 0, new ItemStack(Items.IRON_SWORD));
        Husk sharp = husk(helper, 1, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5));
        afterEquipment(helper, () -> {
            expectDamage(helper, "driven iron sword", driven(helper, plain, 0), 8);
            expectDamage(helper, "driven Sharpness V", driven(helper, sharp, 1), 11);
        });
    }

    public static void swordMaterialWithoutProvider(GameTestHelper helper) {
        swordMaterial(helper, null);
    }

    public static void swordMaterialWithProvider(GameTestHelper helper) {
        swordMaterial(helper, PLUS_TWO);
    }

    public static void strengthAndWeaknessWithoutProvider(GameTestHelper helper) {
        strengthAndWeakness(helper, null);
    }

    public static void strengthAndWeaknessWithProvider(GameTestHelper helper) {
        strengthAndWeakness(helper, PLUS_TWO);
    }

    public static void bowChargeWithoutProvider(GameTestHelper helper) {
        bowCharge(helper, null);
    }

    public static void bowChargeWithProvider(GameTestHelper helper) {
        bowCharge(helper, PLUS_TWO);
    }

    public static void powerWithoutProvider(GameTestHelper helper) {
        power(helper, null);
    }

    public static void powerWithProvider(GameTestHelper helper) {
        power(helper, PLUS_TWO);
    }

    /** Sharpness V adds 3: iron 8 becomes 11, 1d8+7, against the plain sword's 1d8+4. */
    private static void sharpness(GameTestHelper helper, ModifierProvider provider) {
        Husk plain = husk(helper, 0, new ItemStack(Items.IRON_SWORD));
        Husk sharp = husk(helper, 1, enchanted(helper, Items.IRON_SWORD, Enchantments.SHARPNESS, 5));
        afterEquipment(helper, () -> {
            expectDamage(helper, "iron sword", melee(helper, provider, plain, 0), 8 + bonus(provider));
            expectDamage(helper, "Sharpness V", melee(helper, provider, sharp, 1), 11 + bonus(provider));
        });
    }

    /** Husk base 3 plus the sword: wooden 6 rolls 1d8+2, stone 7 rolls 1d8+3. */
    private static void swordMaterial(GameTestHelper helper, ModifierProvider provider) {
        Husk wooden = husk(helper, 0, new ItemStack(Items.WOODEN_SWORD));
        Husk stone = husk(helper, 1, new ItemStack(Items.STONE_SWORD));
        afterEquipment(helper, () -> {
            expectAttackDamage(helper, wooden, 6.0);
            expectAttackDamage(helper, stone, 7.0);
            expectDamage(helper, "wooden sword", melee(helper, provider, wooden, 0), 6 + bonus(provider));
            expectDamage(helper, "stone sword", melee(helper, provider, stone, 1), 7 + bonus(provider));
        });
    }

    /** Iron sword 8 rolls 1d8+4; Strength II (+6) makes 14, 1d8+10; Weakness (-4) makes 4, 1d8. */
    private static void strengthAndWeakness(GameTestHelper helper, ModifierProvider provider) {
        Husk plain = husk(helper, 0, new ItemStack(Items.IRON_SWORD));
        Husk strong = husk(helper, 1, new ItemStack(Items.IRON_SWORD));
        strong.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1));
        Husk weak = husk(helper, 2, new ItemStack(Items.IRON_SWORD));
        weak.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
        afterEquipment(helper, () -> {
            expectAttackDamage(helper, plain, 8.0);
            expectAttackDamage(helper, strong, 14.0);
            expectAttackDamage(helper, weak, 4.0);
            expectDamage(helper, "iron sword", melee(helper, provider, plain, 0), 8 + bonus(provider));
            expectDamage(helper, "Strength II", melee(helper, provider, strong, 1), 14 + bonus(provider));
            expectDamage(helper, "Weakness", melee(helper, provider, weak, 2), 4 + bonus(provider));
        });
    }

    /** A full draw (speed 3) hits for 6, 1d8+2; a half draw (speed 1.5) for 3, 1d8. */
    private static void bowCharge(GameTestHelper helper, ModifierProvider provider) {
        Skeleton skeleton = skeleton(helper);
        expectDamage(
                helper,
                "full draw",
                arrow(helper, provider, skeleton, new ItemStack(Items.BOW), 3.0, 0),
                6 + bonus(provider));
        expectDamage(
                helper,
                "half draw",
                arrow(helper, provider, skeleton, new ItemStack(Items.BOW), 1.5, 1),
                4 + bonus(provider));
        helper.succeed();
    }

    /** Power V raises the arrow's base damage from 2 to 4.5, so a full draw hits for 14 (13.5 rounded up), 1d8+10. */
    private static void power(GameTestHelper helper, ModifierProvider provider) {
        Skeleton skeleton = skeleton(helper);
        ItemStack powerBow = enchanted(helper, Items.BOW, Enchantments.POWER, 5);
        expectDamage(
                helper,
                "plain bow",
                arrow(helper, provider, skeleton, new ItemStack(Items.BOW), 3.0, 0),
                6 + bonus(provider));
        expectDamage(helper, "Power V", arrow(helper, provider, skeleton, powerBow, 3.0, 1), 14 + bonus(provider));
        helper.succeed();
    }

    private static int bonus(ModifierProvider provider) {
        return provider == null ? 0 : PROVIDED;
    }

    /** A held weapon's attribute modifiers land on the mob's next tick, so the hits wait one. */
    private static void afterEquipment(GameTestHelper helper, Runnable hits) {
        helper.runAfterDelay(1, () -> {
            hits.run();
            helper.succeed();
        });
    }

    /** The husk in row {@code z} hits a pig in the same row the way a hostile mob does. */
    private static float melee(GameTestHelper helper, ModifierProvider provider, Husk husk, int z) {
        return melee(helper, provider, husk, EntityType.PIG, z);
    }

    /** Spawned at the hit, so a zombie never ticks long enough to burn or suffocate. */
    private static float melee(
            GameTestHelper helper, ModifierProvider provider, Husk husk, EntityType<? extends Mob> type, int z) {
        Mob target = sturdy(helper, type, 3, z);
        return hit(helper, provider, target, () -> husk.doHurtTarget(target));
    }

    /** The husk in row {@code z} drives a melee attack on a pig through the API. */
    private static float driven(GameTestHelper helper, Husk husk, int z) {
        Pig pig = sturdy(helper, EntityType.PIG, 3, z);
        AttackContext ctx =
                AttackContext.melee(helper.getLevel().damageSources().mobAttack(husk), husk.getMainHandItem());
        return hit(helper, null, pig, () -> RollService.performAttack(husk, pig, ctx));
    }

    /**
     * An arrow from {@code bow} flying at {@code speed} (vanilla draws at {@code 3 * charge}) into a pig.
     * Not a crit arrow: vanilla adds a random bonus to those, which would hide the charge.
     */
    private static float arrow(
            GameTestHelper helper, ModifierProvider provider, Skeleton shooter, ItemStack bow, double speed, int z) {
        for (int x = 1; x <= 4; x++) {
            // NeoForge's empty template places no blocks, so the row may still be solid terrain.
            helper.setBlock(x, 1, z, Blocks.AIR);
        }
        Pig pig = sturdy(helper, EntityType.PIG, 3, z);
        Arrow arrow = new Arrow(helper.getLevel(), shooter, new ItemStack(Items.ARROW), bow);
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 1.4, z + 0.5));
        arrow.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        arrow.setDeltaMovement(speed, 0.0, 0.0);
        arrow.setCritArrow(false);
        helper.getLevel().addFreshEntity(arrow);
        return hit(helper, provider, pig, arrow::tick);
    }

    /** Health {@code target} lost to {@code attack}, with {@code provider} registered (null for none). */
    private static float hit(GameTestHelper helper, ModifierProvider provider, LivingEntity target, Runnable attack) {
        float before = target.getHealth();
        if (provider != null) {
            RollService.registerModifierProvider(provider);
        }
        try {
            CombatScenarios.withRolls(helper, Rules.DEFAULTS, attack, 13, 4);
        } finally {
            RollService.clearModifierProvider();
        }
        return before - target.getHealth();
    }

    private static Husk husk(GameTestHelper helper, int z, ItemStack weapon) {
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(0, 1, z));
        husk.setNoAi(true);
        husk.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        return husk;
    }

    /** Out of the arrows' path; only its profile (+4 to hit) matters. */
    private static Skeleton skeleton(GameTestHelper helper) {
        return CombatScenarios.spawnCalm(helper, EntityType.SKELETON, 0, 4);
    }

    private static <T extends Mob> T sturdy(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = CombatScenarios.spawnCalm(helper, type, x, z);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        mob.setHealth(100.0F);
        return mob;
    }

    private static ItemStack enchanted(
            GameTestHelper helper, Item item, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(
                helper.getLevel()
                        .registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(enchantment),
                level);
        return stack;
    }

    private static void expectAttackDamage(GameTestHelper helper, LivingEntity entity, double expected) {
        double actual = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (actual != expected) {
            helper.fail("expected attack damage " + expected + " but was " + actual, entity);
        }
    }

    private static void expectDamage(GameTestHelper helper, String what, float actual, int expected) {
        if (Math.abs(actual - expected) > 0.001F) {
            helper.fail(what + ": expected " + expected + " damage but was " + actual);
        }
    }
}
