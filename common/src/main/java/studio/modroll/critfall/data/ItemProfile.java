package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.dice.DiceExpression;

/**
 * Weapon/item stats, loaded from {@code data/<ns>/critfall/item_profile/*.json} (see
 * docs/datapack-formats.md). The referenced fumble/crit outcome tables are executed from M4;
 * until then they are validated and shown by {@code /critfall check} only.
 */
public record ItemProfile(
        ResourceLocation id,
        List<MatchEntry> matches,
        Optional<DiceExpression> damage,
        ModifierFrom modifierFrom,
        OptionalInt critRange,
        Optional<ResourceLocation> fumbleTable,
        Optional<ResourceLocation> critTable,
        Set<String> properties,
        int priority)
        implements Profile {

    public static final int FORMAT_VERSION = 1;

    /** Where the flat damage bonus added to the dice comes from. */
    public enum ModifierFrom {
        /** Bonus keeps the item's real (post-modifier) attack damage average — the default. */
        ATTACK_DAMAGE_ATTRIBUTE,
        /** The dice expression is used as-is. */
        NONE;

        static ModifierFrom parse(String text) {
            return switch (text) {
                case "attack_damage_attribute" -> ATTACK_DAMAGE_ATTRIBUTE;
                case "none" -> NONE;
                default ->
                    throw new IllegalArgumentException(
                            "'modifier_from' must be \"attack_damage_attribute\" or \"none\", was \"" + text + "\"");
            };
        }
    }

    public static ItemProfile parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "item_profile " + id, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        List<String> matchTexts = j.stringList("matches");
        if (matchTexts.isEmpty()) {
            throw new IllegalArgumentException("'matches' must list at least one item id, #tag, or namespace:*");
        }
        List<MatchEntry> matches = new ArrayList<>(matchTexts.size());
        for (String text : matchTexts) {
            matches.add(MatchEntry.parse(text));
        }

        Optional<DiceExpression> damage = j.optionalString("damage").map(DiceExpression::parse);
        ModifierFrom modifierFrom = ModifierFrom.parse(j.getString("modifier_from", "attack_damage_attribute"));
        OptionalInt critRange = j.optionalInt("crit_range");
        EntityProfile.validateCritRange(critRange);
        Optional<ResourceLocation> fumbleTable =
                j.optionalString("fumble_table").map(ItemProfile::parseTableId);
        Optional<ResourceLocation> critTable = j.optionalString("crit_table").map(ItemProfile::parseTableId);
        Set<String> properties = Set.copyOf(j.stringList("properties"));
        int priority = j.getInt("priority", 0);
        j.finish();
        return new ItemProfile(
                id,
                List.copyOf(matches),
                damage,
                modifierFrom,
                critRange,
                fumbleTable,
                critTable,
                properties,
                priority);
    }

    static ResourceLocation parseTableId(String text) {
        ResourceLocation parsed = ResourceLocation.tryParse(text);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid outcome table id \"" + text + "\"");
        }
        return parsed;
    }
}
