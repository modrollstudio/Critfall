package studio.modroll.critfall.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.dice.DiceExpression;

/**
 * Fallback derivation (PLAN.md §4.3): plausible tabletop stats computed from vanilla attribute
 * values so the mod works with zero configuration in any modpack. The numbers here are the M2
 * hardcoded defaults — M3 replaces them with datapack profiles and keeps these only as the
 * last-resort fallback.
 */
public final class Derivation {

    private static final int MAX_ATTACK_BONUS = 12;

    /** A derived Armor Class before armor and toughness. */
    public static final int BASE_ARMOR_CLASS = 10;

    /**
     * Flat damage 1..15 mapped to the dice expression with the same average (±0.5). Pre-parsed
     * once — this table is hit on every derived-fallback attack, the out-of-box path for every
     * unprofiled mob, and re-parsing the notation per hit was pure hot-path waste (audit 0.2 B2).
     */
    private static final DiceExpression[] DICE_TABLE = parseAll(
            "1", "1d4", "1d6", "1d8", "1d10", "1d12", "2d6", "2d6+1", "2d8", "2d8+1", "2d10", "2d10+1", "2d12",
            "2d12+1", "2d12+2");

    private static DiceExpression[] parseAll(String... notations) {
        DiceExpression[] parsed = new DiceExpression[notations.length];
        for (int i = 0; i < notations.length; i++) {
            parsed[i] = DiceExpression.parse(notations[i]);
        }
        return parsed;
    }

    private Derivation() {}

    /** {@link #armorClass(double, double, int, int)} with the divisors of the current rules ({@code balance}). */
    public static int armorClass(double armor, double toughness) {
        Rules.Balance balance = RollRuntime.rules().balance();
        return armorClass(armor, toughness, balance.armorAcDivisor(), balance.toughnessAcDivisor());
    }

    /**
     * {@code AC = 10 + floor(armor / armorDivisor) + floor(toughness / toughnessDivisor)}, never below 1. Since
     * 0.2.18 the shipped divisors are 4 and 4, as armor reduces rolled hits too; before, armor counted
     * twice as much ({@code armor / 2}) and reduced nothing.
     */
    public static int armorClass(double armor, double toughness, int armorDivisor, int toughnessDivisor) {
        int ac = BASE_ARMOR_CLASS + armorPart(armor, armorDivisor) + armorPart(toughness, toughnessDivisor);
        return Math.max(1, ac);
    }

    /** What {@code points} of armor or toughness add to a derived AC: {@code floor(points / divisor)}. */
    public static int armorPart(double points, int divisor) {
        return (int) Math.floor(points / divisor);
    }

    /** {@code floor(attackDamage / 2)}, clamped to {@code [0, 12]}. */
    public static int attackBonus(double attackDamage) {
        return Math.clamp((int) Math.floor(attackDamage / 2.0), 0, MAX_ATTACK_BONUS);
    }

    /**
     * {@link #attackBonus(double)}, plus {@code balance.player_attack_bonus} when {@code attacker} is a player:
     * a mob's attack bonus is tuned in its profile, a player's only ever derives.
     */
    public static int attackBonus(LivingEntity attacker, double attackDamage) {
        int bonus = attackBonus(attackDamage);
        return attacker instanceof Player
                ? bonus + RollRuntime.rules().balance().playerAttackBonus()
                : bonus;
    }

    /**
     * Flat bonus added to an item profile's dice when {@code modifier_from} is
     * {@code attack_damage_attribute}: the difference between the item's real (post-modifier)
     * attack damage and the dice average, so expected rolled damage tracks the item's vanilla
     * power (an Apotheosis-boosted sword keeps its edge over a plain one). Clamped to [-12, 12]: since
     * 0.2.18 a weak item rolls below its dice average too (a pillager's bolt, a wooden pickaxe).
     */
    public static int itemDamageBonus(double attackDamage, double diceAverage) {
        return Math.clamp((int) Math.round(attackDamage - diceAverage), -MAX_ATTACK_BONUS, MAX_ATTACK_BONUS);
    }

    /**
     * Maps a flat vanilla damage value to the dice expression whose average is within 0.5 of it
     * (e.g. {@code 3 → 1d6}, {@code 7 → 2d6}, {@code 8 → 2d6+1}). Above the table, buckets of
     * d12s with a flat adjustment keep the same average.
     */
    public static DiceExpression damageDice(double flatDamage) {
        int target = Math.max(1, (int) Math.round(flatDamage));
        if (target <= DICE_TABLE.length) {
            return DICE_TABLE[target - 1];
        }
        int count = Math.max(2, (int) Math.round(target / 6.5));
        int adjustment = target - (int) Math.round(count * 6.5);
        String notation =
                count + "d12" + (adjustment > 0 ? "+" + adjustment : adjustment < 0 ? String.valueOf(adjustment) : "");
        return DiceExpression.parse(notation);
    }
}
