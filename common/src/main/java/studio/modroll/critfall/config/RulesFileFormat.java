package studio.modroll.critfall.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes {@code rules.json} in the shape of the shipped default file, so a file saved from the screen
 * still reads well by hand: two-space indents, and an object of plain values on one line
 * ({@code "damage_dice": { "enabled": true }}).
 */
final class RulesFileFormat {

    private static final String INDENT = "  ";

    private RulesFileFormat() {}

    static String write(JsonObject root) {
        StringBuilder out = new StringBuilder();
        expanded(root, out, "");
        return out.append('\n').toString();
    }

    private static void value(JsonElement element, StringBuilder out, String indent) {
        if (!element.isJsonObject()) {
            out.append(element); // primitives, arrays and null as Gson writes them
        } else if (flat(element.getAsJsonObject())) {
            inline(element.getAsJsonObject(), out);
        } else {
            expanded(element.getAsJsonObject(), out, indent);
        }
    }

    private static void expanded(JsonObject object, StringBuilder out, String indent) {
        if (object.isEmpty()) {
            out.append("{}");
            return;
        }
        String inner = indent + INDENT;
        out.append("{\n");
        int left = object.size();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            out.append(inner).append(name(entry.getKey())).append(": ");
            value(entry.getValue(), out, inner);
            out.append(--left > 0 ? ",\n" : "\n");
        }
        out.append(indent).append('}');
    }

    private static void inline(JsonObject object, StringBuilder out) {
        if (object.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append(object.entrySet().stream()
                .map(entry -> name(entry.getKey()) + ": " + entry.getValue())
                .collect(Collectors.joining(", ", "{ ", " }")));
    }

    /** No nested objects or arrays. */
    private static boolean flat(JsonObject object) {
        return object.entrySet().stream()
                .allMatch(entry ->
                        entry.getValue().isJsonPrimitive() || entry.getValue().isJsonNull());
    }

    private static String name(String key) {
        return new JsonPrimitive(key).toString();
    }
}
