package studio.modroll.critfall.api;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;

/**
 * The resolved combat stats an item contributes — its profile dice (with the attack-damage modifier
 * folded in when {@code modifier_from} says so) and crit range, or empty dice when the item has no
 * profile (the caller then applies the {@code fallbacks.unknown_weapon} rule). Query via
 * {@link RollService#effectiveItem}.
 */
public record EffectiveItemProfile(
        Optional<DiceExpression> damage,
        int critRange,
        Optional<ResourceLocation> fumbleTable,
        Optional<ResourceLocation> critTable,
        Set<String> properties) {

    static EffectiveItemProfile of(Optional<ItemProfile> profile, double attackDamageAttr) {
        // Reuse the melee resolver with an empty entity: it folds in the attack-damage modifier
        // exactly as combat does, and yields the item dice only (no entity fallback).
        Optional<DiceExpression> damage = AttackDice.resolve(profile, Optional.<EntityProfile>empty(), attackDamageAttr)
                .map(AttackDice.Resolved::dice);
        int critRange = profile.map(ItemProfile::critRange)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElse(20);
        return new EffectiveItemProfile(
                damage,
                critRange,
                profile.flatMap(ItemProfile::fumbleTable),
                profile.flatMap(ItemProfile::critTable),
                profile.map(ItemProfile::properties).orElse(Set.of()));
    }
}
