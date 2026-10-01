package studio.modroll.critfall.api;

import java.util.OptionalInt;
import net.minecraft.world.entity.LivingEntity;

/**
 * Supplies the modifiers Critfall adds to its rolls; register one with {@link
 * RollService#registerModifierProvider}. A present attack or save value replaces Critfall's own bonus for
 * that roll; a present damage value is added to Critfall's own damage dice. An empty one keeps Critfall's
 * own.
 *
 * <p>Explicit API values ({@link AttackContext#withAttackBonus}, {@link AttackContext#withDamageDice},
 * the {@code saveBonus} of {@link RollService#savingThrow}) are never sent here. A provider that throws
 * falls back to Critfall's own bonus.
 */
public interface ModifierProvider {

    /** Save key for the save against a {@code "resolution": "save"} spell profile. */
    String SPELL_SAVE = "critfall:spell";

    /** The to-hit modifier; spell attack rolls arrive as {@link AttackDelivery#SPELL}. */
    default OptionalInt attackModifier(LivingEntity attacker, LivingEntity target, AttackDelivery delivery) {
        return OptionalInt.empty();
    }

    /**
     * Added to Critfall's own damage dice, which keep everything Critfall already counts (weapon material,
     * damage enchantments, draw strength, Power, the Strength and Weakness effects): {@code 1d8+2} with {@code 5} rolls {@code
     * 1d8+7}. A dice-less amount (the derived flat {@code 1}) ignores it. Before 0.2.8 this replaced the
     * flat part.
     */
    default OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
        return OptionalInt.empty();
    }

    /** The saving-throw modifier; {@code saveKey} names the save, such as {@link #SPELL_SAVE}. */
    default OptionalInt saveModifier(LivingEntity entity, String saveKey) {
        return OptionalInt.empty();
    }
}
