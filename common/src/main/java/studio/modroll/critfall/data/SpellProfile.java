package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.combat.Rules;

/**
 * How spell-classified damage resolves, matched by DAMAGE TYPE id/tag (not entity or item),
 * loaded from {@code data/<ns>/critfall/spell_profile/*.json} (see docs/datapack-formats.md).
 * This is what makes Iron's Spells, Ars Nouveau, etc. tunable without code: one profile per
 * school tag or damage type id. Every stat is optional — absent fields fall back per field
 * (dice derived from the vanilla amount, attack bonus from the caster, DC from rules.json).
 */
public record SpellProfile(
        ResourceLocation id,
        List<MatchEntry> matches,
        Resolution resolution,
        Optional<DiceExpression> damage,
        OptionalInt attackBonus,
        OptionalInt critRange,
        OptionalInt saveDc,
        Optional<Rules.SaveOutcome> onSuccess,
        int priority)
        implements Profile {

    public static final int FORMAT_VERSION = 1;

    /** Whether the caster rolls to hit or the target rolls to save (PLAN.md §4.2 AoE option). */
    public enum Resolution {
        ATTACK_ROLL,
        SAVE;

        static Resolution parse(String text) {
            return switch (text) {
                case "attack_roll" -> ATTACK_ROLL;
                case "save" -> SAVE;
                default ->
                    throw new IllegalArgumentException(
                            "'resolution' must be \"attack_roll\" or \"save\", was \"" + text + "\"");
            };
        }
    }

    public static SpellProfile parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "spell_profile " + id, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        List<String> matchTexts = j.stringList("matches");
        if (matchTexts.isEmpty()) {
            throw new IllegalArgumentException("'matches' must list at least one damage type id, #tag, or namespace:*");
        }
        List<MatchEntry> matches = new ArrayList<>(matchTexts.size());
        for (String text : matchTexts) {
            matches.add(MatchEntry.parse(text));
        }

        Resolution resolution = Resolution.parse(j.getString("resolution", "attack_roll"));
        Optional<DiceExpression> damage = j.optionalString("damage").map(DiceExpression::parse);
        OptionalInt attackBonus = j.optionalInt("attack_bonus");
        OptionalInt critRange = j.optionalInt("crit_range");
        EntityProfile.validateCritRange(critRange);

        LenientJson save = j.object("save");
        OptionalInt saveDc = save.optionalInt("dc");
        if (saveDc.isPresent() && (saveDc.getAsInt() < 1 || saveDc.getAsInt() > 30)) {
            throw new IllegalArgumentException("'save.dc' must be between 1 and 30");
        }
        Optional<Rules.SaveOutcome> onSuccess =
                save.optionalString("on_success").map(SpellProfile::parseOnSuccess);

        int priority = j.getInt("priority", 0);
        j.finish();
        return new SpellProfile(
                id, List.copyOf(matches), resolution, damage, attackBonus, critRange, saveDc, onSuccess, priority);
    }

    private static Rules.SaveOutcome parseOnSuccess(String text) {
        return switch (text) {
            case "half" -> Rules.SaveOutcome.HALF;
            case "negate" -> Rules.SaveOutcome.NEGATE;
            default ->
                throw new IllegalArgumentException(
                        "'save.on_success' must be \"half\" or \"negate\", was \"" + text + "\"");
        };
    }
}
