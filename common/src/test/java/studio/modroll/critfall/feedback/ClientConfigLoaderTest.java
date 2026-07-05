package studio.modroll.critfall.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientConfigLoaderTest {

    @Test
    void parsesAllToggles() {
        ClientConfig config = ClientConfigLoader.parse(
                JsonParser.parseString("{ \"rolls\": true, \"flavor\": false, \"sounds\": true, \"particles\": false }")
                        .getAsJsonObject(),
                w -> {});
        assertTrue(config.rolls());
        assertFalse(config.flavor());
        assertTrue(config.sounds());
        assertFalse(config.particles());
    }

    @Test
    void defaultsWhenKeysAbsent() {
        ClientConfig config =
                ClientConfigLoader.parse(JsonParser.parseString("{}").getAsJsonObject(), w -> {});
        assertEquals(ClientConfig.DEFAULTS, config);
    }

    @Test
    void writesDefaultFileWhenMissing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("client.json");
        ClientConfig config = ClientConfigLoader.load(file);
        assertEquals(ClientConfig.DEFAULTS, config);
        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("format_version"));
    }
}
