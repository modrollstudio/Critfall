package studio.modroll.critfall.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import studio.modroll.critfall.tools.DatapackGenerator;

/**
 * {@code /critfall generate} runs through the real dispatcher and lands a complete datapack in the
 * world's {@code datapacks/} folder — vanilla mobs and weapons both get a derived profile file. The
 * test deletes the generated pack afterwards: it is written into the shared GameTest world, and a
 * leftover would be auto-loaded on the next server start and pollute every other test's profiles.
 */
public final class GenerateScenarios {

    private GenerateScenarios() {}

    public static void generateWritesADatapack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DatapackGenerator.NAMESPACE);
        try {
            int result = CommandScenarios.execute(helper, CommandScenarios.serverSource(helper), "critfall generate");
            if (result < 1) {
                helper.fail("/critfall generate returned " + result);
            }
            assertExists(helper, packRoot.resolve("pack.mcmeta"));
            assertExists(
                    helper, packRoot.resolve("data/critfall_generated/critfall/entity_profile/minecraft_zombie.json"));
            assertExists(
                    helper,
                    packRoot.resolve("data/critfall_generated/critfall/item_profile/minecraft_iron_sword.json"));
        } finally {
            deleteRecursively(packRoot);
        }
        helper.succeed();
    }

    private static void assertExists(GameTestHelper helper, Path path) {
        if (!Files.exists(path)) {
            helper.fail("expected generated file at " + path);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
