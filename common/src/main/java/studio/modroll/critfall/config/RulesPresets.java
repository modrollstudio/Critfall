package studio.modroll.critfall.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The rules presets of docs/presets.md. The build copies {@code examples/presets} into the jar under
 * {@code assets/critfall/presets}, so the screen offers the same files the docs tell players to copy.
 */
public final class RulesPresets {

    /** In the order the screen cycles through them; the first is the shipped default. */
    public static final List<String> IDS = List.of("tempered", "classic", "lite");

    private RulesPresets() {}

    public static String text(String id) {
        String path = "/assets/critfall/presets/" + id + ".json";
        try (InputStream in = RulesPresets.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Critfall jar is missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
