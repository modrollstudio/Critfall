package studio.modroll.critfall.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.DiceParseException;
import studio.modroll.critfall.data.RulesLoader;

/**
 * Every {@code rules.json} key the config screen edits, in file order, with the defaults and valid
 * values {@link RulesLoader} uses: a value the loader would warn about and replace with its default
 * is one the screen rejects. RulesSchemaTest holds the two in step key by key.
 *
 * <p>Values travel as the text the screen shows: {@code "true"}, a choice such as
 * {@code "max_dice"}, a number, or dice notation.
 */
public final class RulesSchema {

    /** One key of the file. {@link #key()} is its dotted path, which also names its lang entries. */
    public sealed interface Setting permits Toggle, Choice, WholeNumber, Decimal, Dice {

        String key();

        String defaultValue();

        /** The value the loader takes from this element of the file, empty when it falls back. */
        Optional<String> read(JsonElement raw);

        /** The value typed or picked on the screen, empty when the loader would reject it. */
        Optional<String> accept(String input);

        /** How the value is written to the file. Only called with a value {@link #accept} returned. */
        JsonElement toJson(String value);

        default List<String> path() {
            return List.of(key().split("\\."));
        }

        /** The top-level key the setting lives under, which is the section the screen groups it in. */
        default String section() {
            return path().getFirst();
        }
    }

    /** On or off. */
    public record Toggle(String key, String defaultValue) implements Setting {

        @Override
        public Optional<String> read(JsonElement raw) {
            return raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()
                    ? Optional.of(Boolean.toString(raw.getAsBoolean()))
                    : Optional.empty();
        }

        @Override
        public Optional<String> accept(String input) {
            return input.equals("true") || input.equals("false") ? Optional.of(input) : Optional.empty();
        }

        @Override
        public JsonElement toJson(String value) {
            return new JsonPrimitive(Boolean.parseBoolean(value));
        }
    }

    /**
     * One of a fixed set of words, written lowercase.
     *
     * @param reader maps the file's text to the choice the loader makes of it
     */
    public record Choice(
            String key, String defaultValue, List<String> choices, Function<String, Optional<String>> reader)
            implements Setting {

        @Override
        public Optional<String> read(JsonElement raw) {
            return raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()
                    ? reader.apply(raw.getAsString())
                    : Optional.empty();
        }

        @Override
        public Optional<String> accept(String input) {
            return choices.contains(input) ? Optional.of(input) : Optional.empty();
        }

        @Override
        public JsonElement toJson(String value) {
            return new JsonPrimitive(value);
        }
    }

    /** A whole number from {@code min} to {@code max}, both included. */
    public record WholeNumber(String key, String defaultValue, int min, int max) implements Setting {

        private static final Pattern DIGITS = Pattern.compile("-?\\d+");

        @Override
        public Optional<String> read(JsonElement raw) {
            if (!isNumber(raw)) {
                return Optional.empty();
            }
            int value = raw.getAsInt();
            return value < min || value > max ? Optional.empty() : Optional.of(Integer.toString(value));
        }

        /** Digits only: the loader truncates a fraction in the file, the screen asks for a whole number. */
        @Override
        public Optional<String> accept(String input) {
            String text = input.trim();
            if (!DIGITS.matcher(text).matches()) {
                return Optional.empty();
            }
            BigInteger value = new BigInteger(text);
            return value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0
                    ? Optional.empty()
                    : Optional.of(value.toString());
        }

        @Override
        public JsonElement toJson(String value) {
            return new JsonPrimitive(Integer.parseInt(value));
        }

        /** The upper bound is only a guard against overflow, not a limit worth showing. */
        public boolean unbounded() {
            return max == Integer.MAX_VALUE;
        }
    }

    /** A finite number above zero. */
    public record Decimal(String key, String defaultValue) implements Setting {

        private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

        @Override
        public Optional<String> read(JsonElement raw) {
            return isNumber(raw) ? valid(raw.getAsDouble()) : Optional.empty();
        }

        @Override
        public Optional<String> accept(String input) {
            String text = input.trim();
            return NUMBER.matcher(text).matches() ? valid(Double.parseDouble(text)) : Optional.empty();
        }

        @Override
        public JsonElement toJson(String value) {
            return new JsonPrimitive(Double.parseDouble(value));
        }

        private static Optional<String> valid(double value) {
            return Double.isFinite(value) && value > 0 ? Optional.of(Double.toString(value)) : Optional.empty();
        }
    }

    /** Dice notation such as {@code 1d4} or {@code 2d6+1}, written in its canonical form. */
    public record Dice(String key, String defaultValue) implements Setting {

        @Override
        public Optional<String> read(JsonElement raw) {
            return raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()
                    ? accept(raw.getAsString())
                    : Optional.empty();
        }

        @Override
        public Optional<String> accept(String input) {
            try {
                return Optional.of(DiceExpression.parse(input).toString());
            } catch (DiceParseException e) {
                return Optional.empty();
            }
        }

        @Override
        public JsonElement toJson(String value) {
            return new JsonPrimitive(value);
        }
    }

    private static final int NO_LIMIT = Integer.MAX_VALUE;

    public static final Toggle DURABILITY_BREAK = toggle("fumbles.durability_break.enabled", false);
    public static final Choice DURABILITY_MODE = new Choice(
            "fumbles.durability_break.mode", "set_to_1", List.of("set_to_1", "percent_loss"), text -> Optional.of(text)
                    .map(mode -> mode.startsWith("percent_loss:") ? "percent_loss" : mode)
                    .filter(List.of("set_to_1", "percent_loss")::contains));
    /** Its default follows a {@code "percent_loss:N"} mode; {@link RulesEditor} resolves that. */
    public static final WholeNumber DURABILITY_PERCENT = number("fumbles.durability_break.percent", 25, 1, 100);

    public static final List<Setting> SETTINGS = List.of(
            toggle("attack_rolls.enabled", true),
            toggle("attack_rolls.players", true),
            toggle("attack_rolls.mobs", true),
            toggle("attack_rolls.projectiles", true),
            toggle("attack_rolls.spells", true),
            toggle("damage_dice.enabled", true),
            toggle("crits.enabled", true),
            choice("crits.rule", "max_dice", "max_dice", "double_dice", "double_total"),
            toggle("crits.nat20_always_hits", true),
            toggle("crits.apply_effect.enabled", true),
            toggle("crits.knockback.enabled", true),
            toggle("fumbles.enabled", true),
            toggle("fumbles.nat1_always_misses", true),
            toggle("fumbles.confirmation_roll.enabled", true),
            number("fumbles.confirmation_roll.dc", 10, 2, 20),
            number("fumbles.cooldown_ticks", 200, 0, NO_LIMIT),
            DURABILITY_BREAK,
            DURABILITY_MODE,
            DURABILITY_PERCENT,
            toggle("fumbles.hit_nearest_ally.enabled", true),
            number("fumbles.hit_nearest_ally.radius", 4, 1, 64),
            toggle("fumbles.hit_nearest_ally.can_hit_players", true),
            toggle("fumbles.hit_nearest_ally.respect_pvp_rules", true),
            toggle("fumbles.self_damage.enabled", false),
            new Dice("fumbles.self_damage.dice", "1d4"),
            toggle("fumbles.drop_weapon.enabled", false),
            toggle("fumbles.stumble.enabled", false),
            number("fumbles.stumble.slowness_ticks", 40, 1, NO_LIMIT),
            choice("fumbles.applies_to", "players_and_mobs", "players", "mobs", "players_and_mobs"),
            toggle("spells.saves.enabled", true),
            number("spells.saves.default_dc", 13, 1, 30),
            choice("spells.saves.on_success", "half", "half", "negate"),
            choice("fallbacks.unknown_entity", "derive", "derive", "vanilla_passthrough"),
            choice("fallbacks.unknown_weapon", "derive", "derive", "vanilla_passthrough"),
            choice("fallbacks.unknown_spell", "derive", "derive", "vanilla_passthrough"),
            choice("feedback.roll_visibility", "everyone", "everyone", "attacker_only", "off"),
            toggle("feedback.flavor.enabled", true),
            number("feedback.flavor.cooldown_ticks", 20, 0, NO_LIMIT),
            number("feedback.flavor.kill_chance_percent", 25, 0, 100),
            new Decimal("balance.global_damage_multiplier", "1.0"),
            toggle("balance.disable_vanilla_armor_reduction", true),
            toggle("dry_run.enabled", false),
            toggle("modifier_providers.enabled", true),
            toggle("advantage_sources.jump_attack", true));

    /**
     * The settings the screen's main page shows, in this order: the few switches most players want.
     * Every other key is on its All settings page.
     */
    public static final List<Setting> MAIN = Stream.of(
                    "attack_rolls.enabled",
                    "damage_dice.enabled",
                    "crits.enabled",
                    "fumbles.enabled",
                    "fumbles.durability_break.enabled",
                    "fumbles.hit_nearest_ally.enabled",
                    "advantage_sources.jump_attack",
                    "feedback.roll_visibility",
                    "feedback.flavor.enabled")
            .map(RulesSchema::setting)
            .toList();

    private RulesSchema() {}

    public static Setting setting(String key) {
        return SETTINGS.stream()
                .filter(setting -> setting.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no rules.json setting " + key));
    }

    /** The settings grouped by section, sections in file order. */
    public static Map<String, List<Setting>> bySection() {
        Map<String, List<Setting>> sections = new LinkedHashMap<>();
        for (Setting setting : SETTINGS) {
            sections.computeIfAbsent(setting.section(), section -> new ArrayList<>())
                    .add(setting);
        }
        return sections;
    }

    private static Toggle toggle(String key, boolean defaultValue) {
        return new Toggle(key, Boolean.toString(defaultValue));
    }

    private static WholeNumber number(String key, int defaultValue, int min, int max) {
        return new WholeNumber(key, Integer.toString(defaultValue), min, max);
    }

    /** A choice the loader reads as an enum constant, so the file's text matches in any case. */
    private static Choice choice(String key, String defaultValue, String... choices) {
        List<String> values = Arrays.asList(choices);
        return new Choice(
                key,
                defaultValue,
                values,
                // The loader upper-cases the text and looks up the enum constant of that name.
                text -> values.stream()
                        .filter(value -> value.toUpperCase(Locale.ROOT).equals(text.toUpperCase(Locale.ROOT)))
                        .findFirst());
    }

    private static boolean isNumber(JsonElement raw) {
        return raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber();
    }
}
