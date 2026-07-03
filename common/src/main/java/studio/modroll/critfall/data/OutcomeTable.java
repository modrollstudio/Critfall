package studio.modroll.critfall.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * A trigger bound to a weighted list of effects, loaded from
 * {@code data/<ns>/critfall/outcome_table/*.json}. One generic system for fumbles AND crit
 * effects — profiles reference tables per trigger ({@code fumble_table}, {@code crit_table}).
 * M3 loads and validates these; the executor that actually applies effects is M4, so effect
 * parameters are kept as raw JSON here.
 */
public record OutcomeTable(ResourceLocation id, Trigger trigger, List<WeightedEffect> effects) {

    public static final int FORMAT_VERSION = 1;

    /**
     * One effect candidate. {@code type} is an effect id the M4 executor will interpret (e.g.
     * {@code critfall:damage_durability}); {@code params} carries every other key of the effect
     * object, uninterpreted until M4.
     */
    public record WeightedEffect(ResourceLocation type, int weight, JsonObject params) {}

    public int totalWeight() {
        int total = 0;
        for (WeightedEffect effect : effects) {
            total += effect.weight();
        }
        return total;
    }

    public static OutcomeTable parse(ResourceLocation id, JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "outcome_table " + id, warn);
        j.checkFormatVersion(FORMAT_VERSION);

        Trigger trigger = Trigger.parse(j.raw("trigger"), "outcome_table " + id);

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
            effects.add(parseEffect(element.getAsJsonObject()));
        }
        j.finish();
        return new OutcomeTable(id, trigger, List.copyOf(effects));
    }

    private static WeightedEffect parseEffect(JsonObject json) {
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
        return new WeightedEffect(type, weight, params);
    }
}
