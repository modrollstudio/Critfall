package studio.modroll.critfall.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import studio.modroll.critfall.combat.Rules;

/**
 * Moves a {@code rules.json} written before 0.2.18 ({@code format_version} 1, or none) to the current format, once.
 * Those files all say {@code "disable_vanilla_armor_reduction": true}, but only because it was the shipped default,
 * and that rule is what 0.2.18 changes: armor raised AC but never reduced a hit, so a strong mob hit an armored
 * player as hard as a bare one. The move turns the flag off and writes the new AC divisors where the file names none;
 * nothing else changes.
 * The migrated file says {@code format_version} 2, so a pack maker who turns the flag back on keeps it.
 */
public final class RulesMigration {

    /** Logged once, with the file, when the loader saves a migrated file. */
    public static final String MESSAGE = "Moved {} to Critfall 0.2.18's armor rule: armor now reduces rolled hits and"
            + " gives AC from armor / 4. Set balance.disable_vanilla_armor_reduction to true (and armor_ac_divisor to 2)"
            + " for the old rule.";

    private RulesMigration() {}

    /** Migrates {@code root} in place. @return whether it was older than the current format and changed */
    public static boolean migrate(JsonObject root) {
        if (version(root) >= Rules.FORMAT_VERSION) {
            return false;
        }
        JsonElement existing = root.get("balance");
        JsonObject balance =
                existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
        balance.addProperty("disable_vanilla_armor_reduction", false);
        if (!balance.has("armor_ac_divisor")) {
            balance.addProperty("armor_ac_divisor", Rules.Balance.DEFAULT_ARMOR_AC_DIVISOR);
        }
        if (!balance.has("toughness_ac_divisor")) {
            balance.addProperty("toughness_ac_divisor", Rules.Balance.DEFAULT_TOUGHNESS_AC_DIVISOR);
        }
        root.add("balance", balance);
        withVersionFirst(root);
        return true;
    }

    /** The file's {@code format_version}, 1 when it has none (as {@link LenientJson#checkFormatVersion} reads it). */
    private static int version(JsonObject root) {
        JsonElement version = root.get("format_version");
        return version != null
                        && version.isJsonPrimitive()
                        && version.getAsJsonPrimitive().isNumber()
                ? version.getAsInt()
                : 1;
    }

    /** Sets the current {@code format_version}, keeping it the file's first key as the shipped file has it. */
    private static void withVersionFirst(JsonObject root) {
        Map<String, JsonElement> rest = root.asMap();
        JsonObject copy = new JsonObject();
        copy.addProperty("format_version", Rules.FORMAT_VERSION);
        rest.forEach((key, value) -> {
            if (!key.equals("format_version")) {
                copy.add(key, value);
            }
        });
        for (String key : root.keySet().toArray(String[]::new)) {
            root.remove(key);
        }
        copy.asMap().forEach(root::add);
    }
}
