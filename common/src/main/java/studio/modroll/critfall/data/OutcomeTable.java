package studio.modroll.critfall.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * A trigger bound to a weighted list of effects, loaded from
 * {@code data/<ns>/critfall/outcome_table/*.json}. One generic system for fumbles AND crit
 * effects — profiles reference tables per trigger ({@code fumble_table}, {@code crit_table}) and
 * the M4 executor applies whichever table's trigger matches the roll.
 */
public record OutcomeTable(ResourceLocation id, Trigger trigger, List<WeightedEffect> effects) {

    public static final int FORMAT_VERSION = 1;

    /** One effect candidate; the chance of being picked is {@code weight / totalWeight()}. */
    public record WeightedEffect(OutcomeEffect effect, int weight) {}

    public int totalWeight() {
        int total = 0;
        for (WeightedEffect effect : effects) {
            total += effect.weight();
        }
        return total;
    }

    public static OutcomeTable parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        String context = "outcome_table " + id;
        LenientJson j = new LenientJson(json, context, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        Trigger trigger = Trigger.parse(j.raw("trigger"), context);

        JsonElement effectsElement = j.raw("effects");
        if (effectsElement == null
                || !effectsElement.isJsonArray()
                || effectsElement.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("'effects' must be a non-empty array");
        }
        List<WeightedEffect> effects = new ArrayList<>();
        for (JsonElement element : effectsElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("every effect must be an object, found " + element);
            }
            parseEffect(element.getAsJsonObject(), context, warn).ifPresent(effects::add);
        }
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("no effect in 'effects' is of a known type");
        }
        // Each weight is validated >= 1, but the SUM must also stay int-safe: an overflowed
        // (negative) total would make the weighted pick roll a die with negative sides mid-combat.
        long totalWeight = 0;
        for (WeightedEffect effect : effects) {
            totalWeight += effect.weight();
        }
        if (totalWeight > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "total effect weight " + totalWeight + " exceeds the maximum of " + Integer.MAX_VALUE);
        }
        j.finish();
        return new OutcomeTable(id, trigger, List.copyOf(effects));
    }

    private static Optional<WeightedEffect> parseEffect(JsonObject json, String context, Consumer<String> warn) {
        JsonElement typeElement = json.get("type");
        if (typeElement == null || !typeElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("effect needs a 'type' id");
        }
        ResourceLocation type = ResourceLocation.tryParse(typeElement.getAsString());
        if (type == null) {
            throw new IllegalArgumentException("invalid effect type \"" + typeElement.getAsString() + "\"");
        }
        int weight = 1;
        if (json.has("weight")) {
            JsonElement weightElement = json.get("weight");
            if (!weightElement.isJsonPrimitive()
                    || !weightElement.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("effect 'weight' must be a number");
            }
            weight = weightElement.getAsInt();
            if (weight < 1) {
                throw new IllegalArgumentException("effect 'weight' must be at least 1");
            }
        }
        JsonObject params = new JsonObject();
        for (String key : json.keySet()) {
            if (!key.equals("type") && !key.equals("weight")) {
                params.add(key, json.get(key).deepCopy());
            }
        }
        int finalWeight = weight;
        return OutcomeEffect.parse(type, params, context, warn).map(effect -> new WeightedEffect(effect, finalWeight));
    }
}
