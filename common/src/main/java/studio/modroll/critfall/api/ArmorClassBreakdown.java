package studio.modroll.critfall.api;

import java.util.Optional;
import java.util.OptionalInt;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;

/**
 * How Critfall arrives at an entity's own Armor Class ({@link EffectiveEntityProfile#armorClass()}), so an add-on can
 * show it without copying the formula. A derived AC is {@code base + armorPart + toughnessPart} (never below 1), with
 * the divisors of the current {@code rules.json}: {@code 10 + floor(armor / 4) + floor(toughness / 4)} by default.
 * When the entity's profile sets {@code armor_class}, {@link #derived()} is false, the parts are 0, and {@link #base()}
 * and {@link #total()} are the profile's value. A {@link ModifierProvider}'s AC modifier is not included. Query it via
 * {@link RollService#armorClassBreakdown}. Since 0.2.18.
 *
 * @param base 10 for a derived AC, else the profile's AC
 * @param armorPart what the armor attribute adds
 * @param toughnessPart what the armor toughness attribute adds
 * @param total the AC attacks roll against before any provider modifier
 * @param derived the AC comes from armor and toughness, not from an entity profile
 */
public record ArmorClassBreakdown(int base, int armorPart, int toughnessPart, int total, boolean derived) {

    static ArmorClassBreakdown of(Optional<EntityProfile> profile, double armor, double toughness) {
        OptionalInt set = profile.map(EntityProfile::armorClass).orElse(OptionalInt.empty());
        if (set.isPresent()) {
            return new ArmorClassBreakdown(set.getAsInt(), 0, 0, set.getAsInt(), false);
        }
        Rules.Balance balance = RollRuntime.rules().balance();
        return new ArmorClassBreakdown(
                Derivation.BASE_ARMOR_CLASS,
                Derivation.armorPart(armor, balance.armorAcDivisor()),
                Derivation.armorPart(toughness, balance.toughnessAcDivisor()),
                Derivation.armorClass(armor, toughness),
                true);
    }
}
