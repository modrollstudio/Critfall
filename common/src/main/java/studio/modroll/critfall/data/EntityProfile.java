package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.dice.DiceExpression;

/**
 * Tabletop stats for entity types, loaded from {@code data/<ns>/critfall/entity_profile/*.json}
 * (see docs/datapack-formats.md). Every stat is optional — an absent field falls back to
 * attribute derivation for that field only, so a profile can pin just the AC and leave the rest
 * derived.
 */
public record EntityProfile(
        ResourceLocation id,
        List<MatchEntry> matches,
        OptionalInt armorClass,
        OptionalInt attackBonus,
        Optional<DiceExpression> meleeDamage,
        OptionalInt critRange,
        DamageModifiers damageModifiers,
        Optional<ResourceLocation> fumbleTable,
        Optional<ResourceLocation> critTable,
        int priority)
        implements Profile {

    public static final int FORMAT_VERSION = 1;

    /**
     * Parses one profile file. Throws {@link IllegalArgumentException} (or {@link
     * studio.modroll.critfall.dice.DiceParseException}) on author errors that must reject the file;
     * recoverable oddities go to {@code warn}.
     */
    public static EntityProfile parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "entity_profile " + id, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        List<String> matchTexts = j.stringList("matches");
        if (matchTexts.isEmpty()) {
            throw new IllegalArgumentException("'matches' must list at least one entity id, #tag, or namespace:*");
        }
        List<MatchEntry> matches = new ArrayList<>(matchTexts.size());
        for (String text : matchTexts) {
            matches.add(MatchEntry.parse(text));
        }

        OptionalInt armorClass = j.optionalInt("armor_class");
        if (armorClass.isPresent() && armorClass.getAsInt() < 1) {
            throw new IllegalArgumentException("'armor_class' must be at least 1");
        }
        OptionalInt attackBonus = j.optionalInt("attack_bonus");

        LenientJson damage = j.object("damage");
        Optional<DiceExpression> meleeDamage = damage.optionalString("melee").map(DiceExpression::parse);

        OptionalInt critRange = j.optionalInt("crit_range");
        validateCritRange(critRange);

        DamageModifiers modifiers = DamageModifiers.parse(j.object("damage_modifiers"));
        // A weaponless mob's fumble/crit tables — a held weapon's item profile takes precedence.
        Optional<ResourceLocation> fumbleTable =
                j.optionalString("fumble_table").map(ItemProfile::parseTableId);
        Optional<ResourceLocation> critTable = j.optionalString("crit_table").map(ItemProfile::parseTableId);
        int priority = j.getInt("priority", 0);
        j.finish();
        return new EntityProfile(
                id,
                List.copyOf(matches),
                armorClass,
                attackBonus,
                meleeDamage,
                critRange,
                modifiers,
                fumbleTable,
                critTable,
                priority);
    }

    static void validateCritRange(OptionalInt critRange) {
        if (critRange.isPresent() && (critRange.getAsInt() < 2 || critRange.getAsInt() > 20)) {
            throw new IllegalArgumentException("'crit_range' must be between 2 and 20");
        }
    }
}
