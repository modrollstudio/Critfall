package studio.modroll.critfall.api;

import java.util.OptionalInt;
import net.minecraft.world.entity.LivingEntity;

/**
 * Supplies the modifiers Critfall adds to its rolls; register one with {@link
 * RollService#registerModifierProvider}. A present attack or save value replaces Critfall's own bonus for
 * that roll; a present damage or AC value is added to Critfall's own damage dice or the defender's AC. An
 * empty one keeps Critfall's own.
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

    /**
     * Added to the defender's AC as Critfall computes it (entity profile {@code armor_class}, else derived
     * from armor and toughness): AC {@code 10} with {@code 2} is rolled against as AC {@code 12}, with
     * {@code -1} as AC {@code 9}. Asked once per attack roll of every delivery, real-time and driven
     * ({@link RollService#attackRoll}, {@link RollService#performAttack}). The result counts it in
     * {@link studio.modroll.critfall.api.combat.AttackResult#baseArmorClass}, and an {@link
     * AttackContext#withDefenderAcBonus} stacks on top. Not clamped; a value beyond ±1,000,000 is a bad
     * answer. Since 0.2.10.
     */
    default OptionalInt acModifier(LivingEntity defender, LivingEntity attacker) {
        return OptionalInt.empty();
    }
}
