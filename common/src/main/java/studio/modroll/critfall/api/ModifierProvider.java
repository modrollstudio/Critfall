package studio.modroll.critfall.api;

import java.util.OptionalInt;
import net.minecraft.world.entity.LivingEntity;

/**
 * Supplies the modifiers Critfall adds to its rolls; register one with {@link
 * RollService#registerModifierProvider}. A present value replaces Critfall's own bonus for that roll,
 * an empty one keeps it.
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

    /** Replaces the flat part of the damage dice: {@code 1d8+2} with {@code 5} rolls {@code 1d8+5}. */
    default OptionalInt damageModifier(LivingEntity attacker, AttackDelivery delivery) {
        return OptionalInt.empty();
    }

    /** The saving-throw modifier; {@code saveKey} names the save, such as {@link #SPELL_SAVE}. */
    default OptionalInt saveModifier(LivingEntity entity, String saveKey) {
        return OptionalInt.empty();
    }
}
