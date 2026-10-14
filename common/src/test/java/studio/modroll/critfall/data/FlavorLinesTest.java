package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.modroll.critfall.api.AttackDelivery;

class FlavorLinesTest {

    private static final ResourceLocation SWORD = ResourceLocation.parse("minecraft:iron_sword");
    private static final ResourceLocation AXE = ResourceLocation.parse("minecraft:iron_axe");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");
    private static final ResourceLocation DEPTHS = ResourceLocation.parse("crawl:depths");

    private static FlavorPool pool(String id, String json) {
        return FlavorPool.parse(
                ResourceLocation.parse(id), JsonParser.parseString(json).getAsJsonObject(), w -> {
                    throw new AssertionError(w);
                });
    }

    private static final FlavorPool BASE = pool(
            "critfall:base", "{\"base\": true, \"lines\": {\"crit\": [\"base.crit\"], \"kill\": [\"base.kill\"]}}");
    private static final FlavorPool SWORDS = pool(
            "critfall:swords",
            "{\"matches\": [\"minecraft:iron_sword\"], \"lines\": {\"crit\": [\"sword.crit\"], \"fumble\": [\"sword.fumble\"]}}");
    private static final FlavorPool DEPTHS_BASE = pool(
            "crawl:depths",
            "{\"base\": true, \"dimension\": \"crawl:depths\", \"lines\": {\"crit\": [\"depths.crit\"]}}");
    private static final FlavorPool DEPTHS_SWORDS = pool(
            "crawl:depths_swords",
            "{\"matches\": [\"minecraft:iron_sword\"], \"dimension\": \"crawl:depths\","
                    + " \"lines\": {\"crit\": [\"depths.sword.crit\"]}}");

    private static FlavorLines lines(ResourceLocation item, ResourceLocation dimension, FlavorPool... pools) {
        return ProfileStore.flavorLines(List.of(pools), item, tag -> false, AttackDelivery.MELEE, dimension);
    }

    @Test
    void matchedPoolLinesComeFirstThenBase() {
        FlavorLines lines = lines(SWORD, null, BASE, SWORDS);
        assertEquals(List.of("sword.crit", "base.crit"), lines.lines(FlavorPool.CRIT));
        assertEquals(List.of("sword.fumble"), lines.lines(FlavorPool.FUMBLE));
        assertEquals(List.of("base.kill"), lines.lines(FlavorPool.KILL));
    }

    @Test
    void anUnmatchedItemGetsTheBaseLines() {
        assertEquals(List.of("base.crit"), lines(AXE, null, BASE, SWORDS).lines(FlavorPool.CRIT));
    }

    @Test
    void includeBaseFalseLeavesTheBaseOut() {
        FlavorPool swordsOnly = pool(
                "pack:swords",
                "{\"matches\": [\"minecraft:iron_sword\"], \"include_base\": false, \"priority\": 5,"
                        + " \"lines\": {\"crit\": [\"own.crit\"]}}");
        FlavorLines lines = lines(SWORD, null, BASE, SWORDS, swordsOnly);
        assertEquals(List.of("own.crit"), lines.lines(FlavorPool.CRIT));
        assertTrue(lines.lines(FlavorPool.KILL).isEmpty());
    }

    @Test
    void basePoolsAddUpInIdOrder() {
        FlavorPool other = pool("another:base", "{\"base\": true, \"lines\": {\"crit\": [\"other.crit\"]}}");
        assertEquals(
                List.of("other.crit", "base.crit"),
                lines(AXE, null, BASE, other).lines(FlavorPool.CRIT));
    }

    @Test
    void aDimensionWithItsOwnLinesUsesThem() {
        FlavorLines lines = lines(SWORD, DEPTHS, BASE, SWORDS, DEPTHS_BASE, DEPTHS_SWORDS);
        assertEquals(List.of("depths.sword.crit", "depths.crit"), lines.lines(FlavorPool.CRIT));
        assertEquals(
                List.of("depths.crit"),
                lines(AXE, DEPTHS, BASE, SWORDS, DEPTHS_BASE).lines(FlavorPool.CRIT));
    }

    @Test
    void anOutcomeTheDimensionLacksFallsBackToTheNormalPools() {
        FlavorLines lines = lines(SWORD, DEPTHS, BASE, SWORDS, DEPTHS_BASE, DEPTHS_SWORDS);
        assertEquals(List.of("sword.fumble"), lines.lines(FlavorPool.FUMBLE));
        assertEquals(List.of("base.kill"), lines.lines(FlavorPool.KILL));
    }

    @Test
    void otherDimensionsUseTheNormalPools() {
        FlavorLines nether = lines(SWORD, NETHER, BASE, SWORDS, DEPTHS_BASE, DEPTHS_SWORDS);
        assertEquals(List.of("sword.crit", "base.crit"), nether.lines(FlavorPool.CRIT));
        FlavorLines none = lines(SWORD, null, BASE, SWORDS, DEPTHS_BASE, DEPTHS_SWORDS);
        assertEquals(List.of("sword.crit", "base.crit"), none.lines(FlavorPool.CRIT));
    }

    @Test
    void noPoolsGiveNoLines() {
        assertEquals(FlavorLines.NONE, lines(SWORD, DEPTHS));
        assertTrue(Set.copyOf(FlavorPool.OUTCOMES).stream()
                .allMatch(o -> lines(SWORD, null).lines(o).isEmpty()));
    }
}
