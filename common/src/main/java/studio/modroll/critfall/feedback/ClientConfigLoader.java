package studio.modroll.critfall.feedback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.data.LenientJson;

/** Reads {@code config/critfall/client.json}; writes defaults when absent; never throws. */
public final class ClientConfigLoader {

    static final String DEFAULT_FILE = """
            {
              "format_version": 1,
              "rolls": true,
              "flavor": true,
              "sounds": true,
              "particles": true
            }
            """;

    private ClientConfigLoader() {}

    public static ClientConfig load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_FILE);
                Critfall.LOG.info("Wrote default client config to {}", file);
                return ClientConfig.DEFAULTS;
            }
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return parse(json, Critfall.LOG::warn);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Critfall.LOG.error("Could not read {} — using default client config: {}", file, e.toString());
            return ClientConfig.DEFAULTS;
        }
    }

    public static ClientConfig parse(JsonObject json, Consumer<String> warn) {
        LenientJson j = new LenientJson(json, "client.json", warn);
        j.checkFormatVersion(ClientConfig.FORMAT_VERSION);
        ClientConfig config = new ClientConfig(
                j.getBool("rolls", true),
                j.getBool("flavor", true),
                j.getBool("sounds", true),
                j.getBool("particles", true));
        j.finish();
        return config;
    }
}
