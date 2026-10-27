package studio.modroll.critfall.gametest;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestHelper;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.config.RulesEditor;
import studio.modroll.critfall.config.RulesSchema;
import studio.modroll.critfall.config.RulesSchema.Setting;
import studio.modroll.critfall.data.RulesLoader;
import studio.modroll.critfall.data.RulesReloadListener;

/**
 * The config screen's model against a real {@code rules.json} on disk and the live rules: what it
 * saves, {@code /reload}'s path loads; what it rejects, the loader rejects; and weapon damage is off
 * only in configs written fresh. The screen itself is a thin layer over {@link RulesEditor}.
 */
public final class RulesConfigScenarios {

    private RulesConfigScenarios() {}

    public static void savedRulesReachTheLiveGame(GameTestHelper helper) {
        inTemporaryConfig(helper, file -> {
            Files.writeString(file, RulesLoader.DEFAULT_FILE);
            RulesEditor editor = RulesEditor.read(file);
            set(helper, editor, "fumbles.durability_break.enabled", "true");
            set(helper, editor, "fumbles.durability_break.mode", "percent_loss");
            set(helper, editor, "crits.enabled", "false");
            set(helper, editor, "fumbles.confirmation_roll.dc", "15");
            set(helper, editor, "fumbles.self_damage.dice", "2d6+1");
            set(helper, editor, "balance.global_damage_multiplier", "0.75");
            Map<String, String> shown = values(editor);
            Rules edited = editor.rules();
            editor.save(file);

            Rules before = RollRuntime.rules();
            try {
                RulesReloadListener.reload(file);
                if (!RollRuntime.rules().equals(edited)) {
                    helper.fail("the live rules must be what the screen saved, were " + RollRuntime.rules());
                }
            } finally {
                RollRuntime.setRules(before);
            }
            if (!values(RulesEditor.read(file)).equals(shown)) {
                helper.fail("reopening the screen must show what it saved");
            }
            if (!RulesLoader.load(file).equals(edited)) {
                helper.fail("the loader must read back what the screen saved");
            }
        });
        helper.succeed();
    }

    public static void invalidInputIsRejectedLikeTheLoader(GameTestHelper helper) {
        RulesEditor editor = RulesEditor.of(RulesLoader.DEFAULT_FILE);
        String untouched = editor.write();
        for (Setting setting : RulesSchema.SETTINGS) {
            for (String value : invalidValues(setting)) {
                if (editor.set(setting, value)) {
                    helper.fail("the screen took " + value + " for " + setting.key());
                }
                List<String> warnings = new ArrayList<>();
                RulesLoader.parse(file(setting, value), warnings::add);
                if (warnings.isEmpty()) {
                    helper.fail("the loader took " + value + " for " + setting.key() + " the screen rejects");
                }
            }
            for (String value : validValues(setting)) {
                List<String> warnings = new ArrayList<>();
                RulesLoader.parse(file(setting, value), warnings::add);
                if (!RulesEditor.of("{}").set(setting, value) || !warnings.isEmpty()) {
                    helper.fail(value + " for " + setting.key() + " must pass both, loader said " + warnings);
                }
            }
        }
        if (!editor.write().equals(untouched)) {
            helper.fail("rejected input must leave the file as it was");
        }
        helper.succeed();
    }

    public static void weaponDamageIsOffOnlyInFreshConfigs(GameTestHelper helper) {
        inTemporaryConfig(helper, file -> {
            if (RulesLoader.load(file).fumbles().durabilityBreak()) {
                helper.fail("a freshly written config must leave fumble weapon damage off");
            }
            if (!RulesEditor.read(file).value(RulesSchema.DURABILITY_BREAK).equals("false")) {
                helper.fail("the screen must show weapon damage off in a fresh config");
            }
            String written = RulesLoader.DEFAULT_FILE.replace(
                    "\"durability_break\": { \"enabled\": false", "\"durability_break\": { \"enabled\": true");
            Files.writeString(file, written);
            if (!RulesLoader.load(file).fumbles().durabilityBreak()) {
                helper.fail("a config that turned weapon damage on must keep it");
            }
            if (!Files.readString(file).equals(written)) {
                helper.fail("loading must never rewrite an existing config");
            }
        });
        helper.succeed();
    }

    private static List<String> invalidValues(Setting setting) {
        return switch (setting) {
            case RulesSchema.WholeNumber number when number.unbounded() -> List.of(Integer.toString(number.min() - 1));
            case RulesSchema.WholeNumber number ->
                List.of(Integer.toString(number.min() - 1), Integer.toString(number.max() + 1));
            case RulesSchema.Choice choice -> List.of("bogus");
            case RulesSchema.Dice dice -> List.of("bogus");
            case RulesSchema.Decimal decimal -> List.of("0", "-1");
            case RulesSchema.Toggle toggle -> List.of();
        };
    }

    private static List<String> validValues(Setting setting) {
        return switch (setting) {
            case RulesSchema.WholeNumber number when number.unbounded() -> List.of(Integer.toString(number.min()));
            case RulesSchema.WholeNumber number ->
                List.of(Integer.toString(number.min()), Integer.toString(number.max()));
            case RulesSchema.Choice choice -> choice.choices();
            case RulesSchema.Dice dice -> List.of("1d4", "2d6+1");
            case RulesSchema.Decimal decimal -> List.of("0.5", "2");
            case RulesSchema.Toggle toggle -> List.of("true", "false");
        };
    }

    /** A rules file holding just {@code value} at the setting's key, typed as the file would hold it. */
    private static JsonObject file(Setting setting, String value) {
        JsonObject root = new JsonObject();
        JsonObject parent = root;
        List<String> path = setting.path();
        for (String key : path.subList(0, path.size() - 1)) {
            JsonObject child = new JsonObject();
            parent.add(key, child);
            parent = child;
        }
        String last = path.getLast();
        switch (setting) {
            case RulesSchema.WholeNumber number -> parent.addProperty(last, Long.parseLong(value));
            case RulesSchema.Decimal decimal -> parent.addProperty(last, Double.parseDouble(value));
            case RulesSchema.Toggle toggle -> parent.addProperty(last, Boolean.parseBoolean(value));
            default -> parent.addProperty(last, value);
        }
        return root;
    }

    private static void set(GameTestHelper helper, RulesEditor editor, String key, String value) {
        Setting setting = RulesSchema.SETTINGS.stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow();
        if (!editor.set(setting, value)) {
            helper.fail("the screen must take " + value + " for " + key);
        }
    }

    private static Map<String, String> values(RulesEditor editor) {
        Map<String, String> values = new LinkedHashMap<>();
        RulesSchema.SETTINGS.forEach(setting -> values.put(setting.key(), editor.value(setting)));
        return values;
    }

    @FunctionalInterface
    private interface FileAction {
        void run(Path file) throws IOException;
    }

    /** Runs {@code action} on {@code critfall/rules.json} in a fresh directory, deleted afterwards. */
    private static void inTemporaryConfig(GameTestHelper helper, FileAction action) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("critfall-rules");
            Path configDir = Files.createDirectories(dir.resolve("critfall"));
            action.run(configDir.resolve("rules.json"));
        } catch (IOException e) {
            helper.fail("rules file I/O failed: " + e);
        } finally {
            if (dir != null) {
                try (Stream<Path> paths = Files.walk(dir)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(path -> path.toFile().delete());
                } catch (IOException ignored) {
                    // a leftover temporary directory is harmless
                }
            }
        }
    }
}
