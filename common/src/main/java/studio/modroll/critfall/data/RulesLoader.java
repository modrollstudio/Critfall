package studio.modroll.critfall.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Consumer;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.combat.Rules;

/**
 * Reads {@code config/critfall/rules.json}. A missing file is created with the defaults; a
 * malformed or partial file NEVER crashes the server — bad values fall back to their default with
 * a warning, unknown keys warn, keys spec'd for later milestones are recognized but inert.
 */
public final class RulesLoader {

    /** Written verbatim when no config exists. Keep in sync with {@link Rules#DEFAULTS}. */
    static final String DEFAULT_FILE = """
            {
              "format_version": 1,
              "attack_rolls": { "enabled": true, "players": true, "mobs": true, "projectiles": true, "spells": true },
              "damage_dice": { "enabled": true },
              "crits": { "enabled": true, "rule": "max_dice", "nat20_always_hits": true },
              "fumbles": {
                "enabled": true,
                "nat1_always_misses": true,
                "confirmation_roll": { "enabled": true, "dc": 10 },
                "cooldown_ticks": 200,
                "durability_break": { "enabled": true, "mode": "set_to_1", "percent": 25 }
              },
              "fallbacks": { "unknown_entity": "derive", "unknown_weapon": "derive" },
              "feedback": { "roll_visibility": "everyone" },
              "balance": { "global_damage_multiplier": 1.0, "disable_vanilla_armor_reduction": true }
            }
            """;

    private RulesLoader() {}

    /** Loads the rules file, creating it with defaults when absent. Never throws. */
    public static Rules load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_FILE);
                Critfall.LOG.info("Wrote default rules config to {}", file);
                return Rules.DEFAULTS;
            }
            String text = Files.readString(file);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            return parse(json, Critfall.LOG::warn);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Critfall.LOG.error("Could not read {} — using default rules: {}", file, e.toString());
            return Rules.DEFAULTS;
        }
    }

    public static Rules parse(JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "rules.json", warn);
        j.checkFormatVersion(Rules.FORMAT_VERSION);
        j.reserved("advantage_sources", "advantage mechanics land in a later milestone");

        LenientJson attackRolls = j.object("attack_rolls");
        Rules.AttackRolls attack = new Rules.AttackRolls(
                attackRolls.getBool("enabled", true),
                attackRolls.getBool("players", true),
                attackRolls.getBool("mobs", true),
                attackRolls.getBool("projectiles", true),
                attackRolls.getBool("spells", true));

        boolean damageDice = j.object("damage_dice").getBool("enabled", true);

        LenientJson crits = j.object("crits");
        Rules.Crits critRules = new Rules.Crits(
                crits.getBool("enabled", true),
                parseEnum(crits, "rule", Rules.CritRule.class, Rules.CritRule.MAX_DICE, warn),
                crits.getBool("nat20_always_hits", true));

        LenientJson fumbles = j.object("fumbles");
        fumbles.reserved("hit_nearest_ally", "outcome table executor lands in M4");
        fumbles.reserved("self_damage", "outcome table executor lands in M4");
        fumbles.reserved("drop_weapon", "outcome table executor lands in M4");
        fumbles.reserved("stumble", "outcome table executor lands in M4");
        fumbles.reserved("applies_to", "outcome table executor lands in M4");
        LenientJson confirmation = fumbles.object("confirmation_roll");
        LenientJson durability = fumbles.object("durability_break");
        String modeText = durability.getString("mode", "set_to_1");
        Rules.DurabilityMode durabilityMode = parseDurabilityMode(modeText, warn);
        int defaultPercent = shorthandPercent(modeText).orElse(25);
        Rules.Fumbles fumbleRules = new Rules.Fumbles(
                fumbles.getBool("enabled", true),
                fumbles.getBool("nat1_always_misses", true),
                confirmation.getBool("enabled", true),
                intInRange(confirmation, "dc", 10, 2, 20, warn),
                intInRange(fumbles, "cooldown_ticks", 200, 0, Integer.MAX_VALUE, warn),
                durability.getBool("enabled", true),
                durabilityMode,
                intInRange(durability, "percent", defaultPercent, 1, 100, warn));

        LenientJson fallbacks = j.object("fallbacks");
        Rules.Fallbacks fallbackRules = new Rules.Fallbacks(
                parseEnum(fallbacks, "unknown_entity", Rules.FallbackMode.class, Rules.FallbackMode.DERIVE, warn),
                parseEnum(fallbacks, "unknown_weapon", Rules.FallbackMode.class, Rules.FallbackMode.DERIVE, warn));

        LenientJson feedback = j.object("feedback");
        feedback.reserved("sounds", "client feedback module lands in M6");
        feedback.reserved("particles", "client feedback module lands in M6");
        Rules.FeedbackVisibility visibility = parseEnum(
                feedback, "roll_visibility", Rules.FeedbackVisibility.class, Rules.FeedbackVisibility.EVERYONE, warn);

        LenientJson balance = j.object("balance");
        double multiplier = balance.getDouble("global_damage_multiplier", 1.0);
        if (multiplier <= 0) {
            warn.accept("rules.json: 'global_damage_multiplier' must be positive, using 1.0");
            multiplier = 1.0;
        }
        Rules.Balance balanceRules =
                new Rules.Balance(multiplier, balance.getBool("disable_vanilla_armor_reduction", true));

        j.finish();
        return new Rules(attack, damageDice, critRules, fumbleRules, fallbackRules, visibility, balanceRules);
    }

    /**
     * Accepts {@code "set_to_1"}, {@code "percent_loss"}, and the PLAN.md shorthand
     * {@code "percent_loss:25"}; an explicit {@code percent} key overrides the shorthand suffix.
     */
    private static Rules.DurabilityMode parseDurabilityMode(String text, Consumer<String> warn) {
        return switch (text.startsWith("percent_loss:") ? "percent_loss" : text) {
            case "set_to_1" -> Rules.DurabilityMode.SET_TO_1;
            case "percent_loss" -> Rules.DurabilityMode.PERCENT_LOSS;
            default -> {
                warn.accept("rules.json: unknown durability mode \"" + text + "\", using set_to_1");
                yield Rules.DurabilityMode.SET_TO_1;
            }
        };
    }

    private static OptionalInt shorthandPercent(String modeText) {
        if (!modeText.startsWith("percent_loss:")) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(modeText.substring("percent_loss:".length())));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static <E extends Enum<E>> E parseEnum(
            LenientJson json, String key, Class<E> type, E def, Consumer<String> warn) {
        String text = json.getString(key, def.name().toLowerCase(Locale.ROOT));
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn.accept("rules.json: unknown value \"" + text + "\" for '" + key + "', using "
                    + def.name().toLowerCase(Locale.ROOT));
            return def;
        }
    }

    private static int intInRange(LenientJson json, String key, int def, int min, int max, Consumer<String> warn) {
        int value = json.getInt(key, def);
        if (value < min || value > max) {
            warn.accept("rules.json: '" + key + "' must be between " + min + " and " + max + ", using " + def);
            return def;
        }
        return value;
    }
}
