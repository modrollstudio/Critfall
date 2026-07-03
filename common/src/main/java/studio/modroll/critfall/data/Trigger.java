package studio.modroll.critfall.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What fires an outcome table. Only the shapes named in PLAN.md §4.2 exist: natural 1/20, a miss
 * margin, and a natural-roll range. The M4 executor gives these their runtime semantics; M3 only
 * parses and validates.
 */
public sealed interface Trigger {

    /** A specific natural d20 face — {@code "nat_1"} and {@code "nat_20"} in JSON. */
    record Natural(int face) implements Trigger {
        @Override
        public String toString() {
            return "nat_" + face;
        }
    }

    /** Fires when the attack missed by at least this margin below the AC. */
    record MissByAtLeast(int margin) implements Trigger {
        @Override
        public String toString() {
            return "miss_by_at_least " + margin;
        }
    }

    /** Fires when the natural roll is within {@code [min, max]}. */
    record RollRange(int min, int max) implements Trigger {
        @Override
        public String toString() {
            return "roll_range " + min + ".." + max;
        }
    }

    static Trigger parse(JsonElement element, String context) {
        if (element == null) {
            throw new IllegalArgumentException("missing 'trigger'");
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return switch (element.getAsString()) {
                case "nat_1" -> new Natural(1);
                case "nat_20" -> new Natural(20);
                default ->
                    throw new IllegalArgumentException("unknown trigger \"" + element.getAsString()
                            + "\" — expected \"nat_1\", \"nat_20\", or an object trigger");
            };
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("'trigger' must be a string or an object");
        }
        JsonObject json = element.getAsJsonObject();
        LenientJson j = new LenientJson(json, context + ".trigger", message -> {
            throw new IllegalArgumentException(message);
        });
        String type = j.optionalString("type")
                .orElseThrow(() -> new IllegalArgumentException("object trigger needs a 'type'"));
        Trigger trigger =
                switch (type) {
                    case "miss_by_at_least" -> {
                        int margin = j.getInt("margin", -1);
                        if (margin < 1) {
                            throw new IllegalArgumentException("miss_by_at_least needs 'margin' of at least 1");
                        }
                        yield new MissByAtLeast(margin);
                    }
                    case "roll_range" -> {
                        int min = j.getInt("min", -1);
                        int max = j.getInt("max", -1);
                        if (min < 1 || max > 20 || min > max) {
                            throw new IllegalArgumentException("roll_range needs 1 <= min <= max <= 20");
                        }
                        yield new RollRange(min, max);
                    }
                    default -> throw new IllegalArgumentException("unknown trigger type \"" + type + "\"");
                };
        j.finish();
        return trigger;
    }
}
