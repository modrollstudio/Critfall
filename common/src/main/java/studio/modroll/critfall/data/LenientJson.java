package studio.modroll.critfall.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads a {@link JsonObject} the way our config/datapack boundary rules demand:
 * unknown keys produce a warning instead of a crash, wrong-typed scalars fall back to their
 * default with a warning, and {@code format_version} is checked for forward compatibility.
 * Structural problems the author must fix (missing required fields, bad dice, bad ids) are thrown
 * by the callers as {@link IllegalArgumentException} so the whole file is rejected loudly.
 *
 * <p>Call {@link #finish()} after reading every known key — it reports the leftovers.
 */
public final class LenientJson {

    private final JsonObject json;
    private final String context;
    private final Consumer<String> warn;
    private final Set<String> consumed = new HashSet<>();
    private final List<LenientJson> children = new ArrayList<>();

    public LenientJson(JsonObject json, String context, Consumer<String> warn) {
        this.json = json;
        this.context = context;
        this.warn = warn;
    }

    public boolean has(String key) {
        return json.has(key);
    }

    /** Consumes and returns the raw element, or null when absent. For union-typed fields. */
    public JsonElement raw(String key) {
        consumed.add(key);
        return json.get(key);
    }

    public int getInt(String key, int def) {
        JsonElement e = raw(key);
        if (e == null) {
            return def;
        }
        if (isNumber(e)) {
            return e.getAsInt();
        }
        warnType(key, "a number");
        return def;
    }

    public OptionalInt optionalInt(String key) {
        JsonElement e = raw(key);
        if (e == null) {
            return OptionalInt.empty();
        }
        if (isNumber(e)) {
            return OptionalInt.of(e.getAsInt());
        }
        warnType(key, "a number");
        return OptionalInt.empty();
    }

    public double getDouble(String key, double def) {
        JsonElement e = raw(key);
        if (e == null) {
            return def;
        }
        if (isNumber(e)) {
            return e.getAsDouble();
        }
        warnType(key, "a number");
        return def;
    }

    public boolean getBool(String key, boolean def) {
        JsonElement e = raw(key);
        if (e == null) {
            return def;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        warnType(key, "true or false");
        return def;
    }

    public String getString(String key, String def) {
        return optionalString(key).orElse(def);
    }

    public Optional<String> optionalString(String key) {
        JsonElement e = raw(key);
        if (e == null) {
            return Optional.empty();
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return Optional.of(e.getAsString());
        }
        warnType(key, "a string");
        return Optional.empty();
    }

    /** A list of strings; a single string is accepted as a one-element list. */
    public List<String> stringList(String key) {
        JsonElement e = raw(key);
        if (e == null) {
            return List.of();
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return List.of(e.getAsString());
        }
        if (e.isJsonArray()) {
            List<String> out = new ArrayList<>();
            for (JsonElement item : e.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    out.add(item.getAsString());
                } else {
                    warn.accept(context + ": '" + key + "' entries must be strings, skipped " + item);
                }
            }
            return out;
        }
        warnType(key, "a string or list of strings");
        return List.of();
    }

    /** Nested object; absent or wrong-typed yields an empty object (with a warning when wrong-typed). */
    public LenientJson object(String key) {
        JsonElement e = raw(key);
        JsonObject nested;
        if (e == null) {
            nested = new JsonObject();
        } else if (e.isJsonObject()) {
            nested = e.getAsJsonObject();
        } else {
            warnType(key, "an object");
            nested = new JsonObject();
        }
        LenientJson child = new LenientJson(nested, context + "." + key, warn);
        children.add(child);
        return child;
    }

    /** Marks a spec'd-but-not-yet-implemented key as recognized so it does not warn as unknown. */
    public void reserved(String key, String reason) {
        if (json.has(key)) {
            warn.accept(context + ": '" + key + "' is not implemented yet (" + reason + ") and was ignored");
        }
        consumed.add(key);
    }

    /** Missing version means 1; a newer version than we support parses anyway, with a warning. */
    public void checkFormatVersion(int supported) {
        int version = getInt("format_version", 1);
        if (version > supported) {
            warn.accept(context + ": format_version " + version + " is newer than supported version " + supported
                    + " — parsing anyway, values may be misread");
        }
    }

    /** Warns about every key that was never consumed, recursing into nested objects. */
    public void finish() {
        for (String key : json.keySet()) {
            if (!consumed.contains(key)) {
                warn.accept(context + ": unknown key '" + key + "' (ignored)");
            }
        }
        for (LenientJson child : children) {
            child.finish();
        }
    }

    private static boolean isNumber(JsonElement e) {
        return e.isJsonPrimitive() && ((JsonPrimitive) e).isNumber();
    }

    private void warnType(String key, String expected) {
        warn.accept(context + ": '" + key + "' must be " + expected + ", using default");
    }
}
