package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ProfileStoreTest {

    private static final ResourceLocation ZOMBIE = ResourceLocation.parse("minecraft:zombie");
    private static final Set<ResourceLocation> ZOMBIE_TAGS = Set.of(ResourceLocation.parse("minecraft:undead"));

    private static EntityProfile profile(String id, int priority, String... matches) {
        StringBuilder list = new StringBuilder();
        for (String match : matches) {
            list.append(list.isEmpty() ? "" : ", ").append('"').append(match).append('"');
        }
        return EntityProfile.parse(
                ResourceLocation.parse(id),
                JsonParser.parseString("{\"matches\": [" + list + "], \"priority\": " + priority + "}")
                        .getAsJsonObject(),
                w -> {});
    }

    private static Optional<EntityProfile> resolve(EntityProfile... profiles) {
        return ProfileStore.resolve(List.of(profiles), ZOMBIE, ZOMBIE_TAGS::contains);
    }

    @Test
    void higherPriorityWins() {
        EntityProfile base = profile("pack:base", 0, "minecraft:zombie");
        EntityProfile override = profile("pack:override", 10, "#minecraft:undead");
        assertEquals("pack:override", resolve(base, override).orElseThrow().id().toString());
    }

    @Test
    void equalPriorityFallsBackToSpecificity() {
        EntityProfile byTag = profile("pack:by_tag", 0, "#minecraft:undead");
        EntityProfile byId = profile("pack:by_id", 0, "minecraft:zombie");
        EntityProfile byNamespace = profile("pack:by_namespace", 0, "minecraft:*");
        assertEquals(
                "pack:by_id",
                resolve(byTag, byId, byNamespace).orElseThrow().id().toString());
        assertEquals(
                "pack:by_tag", resolve(byTag, byNamespace).orElseThrow().id().toString());
    }

    @Test
    void fullTieBreaksOnSmallerFileId() {
        EntityProfile a = profile("apack:zombie", 0, "minecraft:zombie");
        EntityProfile b = profile("bpack:zombie", 0, "minecraft:zombie");
        assertEquals("apack:zombie", resolve(b, a).orElseThrow().id().toString());
    }

    @Test
    void profileUsesItsMostSpecificMatchingEntry() {
        // Matches by both wildcard and exact id -> counts as exact (specificity 3).
        EntityProfile broad = profile("pack:broad", 0, "minecraft:*", "minecraft:zombie");
        EntityProfile tag = profile("pack:tag", 0, "#minecraft:undead");
        assertEquals("pack:broad", resolve(broad, tag).orElseThrow().id().toString());
    }

    @Test
    void noMatchReturnsEmpty() {
        EntityProfile pigs = profile("pack:pigs", 0, "minecraft:pig");
        assertTrue(resolve(pigs).isEmpty());
    }

    @Test
    void storeRoundTrip() {
        EntityProfile zombie = profile("pack:zombie", 0, "minecraft:zombie");
        ProfileStore.setEntityProfiles(java.util.Map.of(zombie.id(), zombie));
        try {
            assertEquals(
                    "pack:zombie",
                    ProfileStore.findEntityProfile(ZOMBIE, ZOMBIE_TAGS::contains)
                            .orElseThrow()
                            .id()
                            .toString());
        } finally {
            ProfileStore.setEntityProfiles(java.util.Map.of());
        }
    }
}
