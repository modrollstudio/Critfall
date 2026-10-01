package studio.modroll.critfall.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The damage a weapon's enchantments add to a melee hit, by vanilla's own calculation. Vanilla adds it
 * on top of the attack-damage attribute, so a dice bonus built from the attribute alone misses it.
 * Asking vanilla against the real target keeps target-specific enchantments right (Smite on undead
 * only, Bane of Arthropods on arthropods only) and covers modded ones without special cases.
 */
public final class EnchantmentDamage {

    private EnchantmentDamage() {}

    /** What {@code weapon}'s enchantments add to {@code base} against {@code target}; 0 off the server. */
    public static double bonus(ItemStack weapon, LivingEntity target, DamageSource source, double base) {
        if (weapon.isEmpty() || !(target.level() instanceof ServerLevel level)) {
            return 0.0;
        }
        return EnchantmentHelper.modifyDamage(level, weapon, target, source, (float) base) - (float) base;
    }
}
