package studio.modroll.critfall.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.config.RulesSchema.Setting;
import studio.modroll.critfall.data.RulesLoader;

/**
 * The config screen's model: {@code rules.json} as written, edited one {@link Setting} at a time.
 * Keys the screen does not show (format_version, keys reserved for later, unknown keys) stay in the
 * file untouched, and a key the file leaves out is only written once it is changed.
 *
 * <p>Each value shown is the one {@link RulesLoader} takes from the file: an invalid value in the
 * file shows as the default the loader falls back to. Pure JVM, no client classes, so it is unit
 * tested directly.
 */
public final class RulesEditor {

    private static final String SHORTHAND_PERCENT = "percent_loss:";

    private JsonObject root;
    private final boolean unreadable;

    private RulesEditor(JsonObject root, boolean unreadable) {
        this.root = root;
        this.unreadable = unreadable;
    }

    /**
     * Reads {@code file} the way the loader does. A missing file reads as the default file; one the
     * loader cannot parse reads as the defaults and is {@link #unreadable()}.
     */
    public static RulesEditor read(Path file) {
        if (!Files.exists(file)) {
            return of(RulesLoader.DEFAULT_FILE);
        }
        try {
            return of(Files.readString(file));
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return new RulesEditor(object(RulesLoader.DEFAULT_FILE), true);
        }
    }

    /** @throws JsonParseException or {@link IllegalStateException} when {@code text} is not a JSON object */
    public static RulesEditor of(String text) {
        return new RulesEditor(object(text), false);
    }

    /** An independent copy: edits to either leave the other as it was. */
    public RulesEditor copy() {
        return new RulesEditor(root.deepCopy(), unreadable);
    }

    /** The file could not be parsed, so the defaults show and saving replaces it. */
    public boolean unreadable() {
        return unreadable;
    }

    /** The value the loader takes for {@code setting}. */
    public String value(Setting setting) {
        Optional<String> value = element(setting.path()).flatMap(setting::read);
        if (setting == RulesSchema.DURABILITY_PERCENT) {
            return value.or(this::shorthandPercent).orElse(setting.defaultValue());
        }
        return value.orElse(setting.defaultValue());
    }

    /**
     * Writes {@code input} for {@code setting} when the loader would accept it.
     *
     * @return false, leaving the file as it was, when the input is rejected
     */
    public boolean set(Setting setting, String input) {
        Optional<String> value = setting.accept(input);
        if (value.isEmpty()) {
            return false;
        }
        if (setting == RulesSchema.DURABILITY_MODE) {
            // A "percent_loss:N" mode supplies the percent; keep it when the mode is rewritten.
            shorthandPercent()
                    .filter(percent ->
                            element(RulesSchema.DURABILITY_PERCENT.path()).isEmpty())
                    .flatMap(RulesSchema.DURABILITY_PERCENT::accept)
                    .ifPresent(percent -> put(RulesSchema.DURABILITY_PERCENT, percent));
        }
        put(setting, value.get());
        return true;
    }

    /** Replaces everything with a preset's file. */
    public void applyPreset(String presetText) {
        root = object(presetText);
    }

    /** Whether the file amounts to the same rules as {@code presetText}. */
    public boolean matches(String presetText) {
        return rules().equals(RulesLoader.parse(object(presetText), warning -> {}));
    }

    /** The rules the loader makes of the file as it stands. */
    public Rules rules() {
        return RulesLoader.parse(root.deepCopy(), warning -> {});
    }

    public String write() {
        return RulesFileFormat.write(root);
    }

    /** Writes the file, through a temporary file so a reader never sees half of it. */
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, write());
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Optional<String> shorthandPercent() {
        return element(RulesSchema.DURABILITY_MODE.path())
                .filter(mode ->
                        mode.isJsonPrimitive() && mode.getAsJsonPrimitive().isString())
                .map(JsonElement::getAsString)
                .filter(mode -> mode.startsWith(SHORTHAND_PERCENT))
                .flatMap(mode -> {
                    // As the loader: any whole number after the colon, and no range check, since it
                    // only stands in as the default of a percent key the file leaves out.
                    try {
                        return Optional.of(
                                Integer.toString(Integer.parseInt(mode.substring(SHORTHAND_PERCENT.length()))));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    /** The element at {@code path}, empty when it or an object on the way is missing. */
    private Optional<JsonElement> element(List<String> path) {
        JsonElement current = root;
        for (String key : path) {
            if (!current.isJsonObject() || !current.getAsJsonObject().has(key)) {
                return Optional.empty();
            }
            current = current.getAsJsonObject().get(key);
        }
        return Optional.of(current);
    }

    /** Writes at the setting's path, replacing anything on the way that is not an object. */
    private void put(Setting setting, String value) {
        List<String> path = setting.path();
        JsonObject parent = root;
        for (String key : path.subList(0, path.size() - 1)) {
            JsonElement child = parent.get(key);
            if (child == null || !child.isJsonObject()) {
                child = new JsonObject();
                parent.add(key, child);
            }
            parent = child.getAsJsonObject();
        }
        parent.add(path.getLast(), setting.toJson(value));
    }

    private static JsonObject object(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** For tests: the values the screen shows, by key. */
    Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        RulesSchema.SETTINGS.forEach(setting -> values.put(setting.key(), value(setting)));
        return values;
    }
}
