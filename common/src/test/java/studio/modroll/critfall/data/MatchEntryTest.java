package studio.modroll.critfall.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MatchEntryTest {

    private static final ResourceLocation ZOMBIE = ResourceLocation.parse("minecraft:zombie");

    @Test
    void exactIdMatchesOnlyThatId() {
        MatchEntry entry = MatchEntry.parse("minecraft:zombie");
        assertTrue(entry.matches(ZOMBIE, tag -> false));
        assertFalse(entry.matches(ResourceLocation.parse("minecraft:husk"), tag -> false));
        assertEquals(3, entry.specificity());
    }

    @Test
    void bareIdDefaultsToMinecraftNamespace() {
        assertTrue(MatchEntry.parse("zombie").matches(ZOMBIE, tag -> false));
    }

    @Test
    void tagMatchesViaPredicate() {
        MatchEntry entry = MatchEntry.parse("#minecraft:undead");
        Set<ResourceLocation> memberOf = Set.of(ResourceLocation.parse("minecraft:undead"));
        assertTrue(entry.matches(ZOMBIE, memberOf::contains));
        assertFalse(entry.matches(ZOMBIE, tag -> false));
        assertEquals(2, entry.specificity());
    }

    @Test
    void namespaceWildcardMatchesWholeNamespace() {
        MatchEntry entry = MatchEntry.parse("alexsmobs:*");
        assertTrue(entry.matches(ResourceLocation.parse("alexsmobs:grizzly_bear"), tag -> false));
        assertFalse(entry.matches(ZOMBIE, tag -> false));
        assertEquals(1, entry.specificity());
    }

    @Test
    void malformedEntriesThrow() {
        assertThrows(IllegalArgumentException.class, () -> MatchEntry.parse(""));
        assertThrows(IllegalArgumentException.class, () -> MatchEntry.parse("UPPER:case"));
        assertThrows(IllegalArgumentException.class, () -> MatchEntry.parse("#"));
        assertThrows(IllegalArgumentException.class, () -> MatchEntry.parse("Bad Namespace:*"));
    }

    @Test
    void toStringRoundTrips() {
        assertEquals("minecraft:zombie", MatchEntry.parse("minecraft:zombie").toString());
        assertEquals("#minecraft:undead", MatchEntry.parse("#minecraft:undead").toString());
        assertEquals("alexsmobs:*", MatchEntry.parse("alexsmobs:*").toString());
    }
}
