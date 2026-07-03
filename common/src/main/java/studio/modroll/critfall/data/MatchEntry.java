package studio.modroll.critfall.data;

import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * One entry of a profile's {@code matches} list. Three forms, from most to least specific:
 * an exact registry id ({@code "minecraft:enderman"}), a tag ({@code "#minecraft:undead"}), or a
 * whole namespace ({@code "alexsmobs:*"}). Specificity breaks priority ties during resolution.
 *
 * <p>Tag membership is supplied by the caller as a predicate over the tag's id so this stays pure
 * and unit-testable without a loaded registry.
 */
public sealed interface MatchEntry {

    boolean matches(ResourceLocation id, Predicate<ResourceLocation> tagTest);

    /** Exact id = 3, tag = 2, namespace wildcard = 1. */
    int specificity();

    /** Parses one entry, throwing {@link IllegalArgumentException} on malformed input. */
    static MatchEntry parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty matches entry");
        }
        if (text.startsWith("#")) {
            return new Tag(parseId(text.substring(1), text));
        }
        if (text.endsWith(":*")) {
            String namespace = text.substring(0, text.length() - 2);
            if (!ResourceLocation.isValidNamespace(namespace)) {
                throw new IllegalArgumentException("invalid namespace in matches entry \"" + text + "\"");
            }
            return new Namespace(namespace);
        }
        return new Exact(parseId(text, text));
    }

    private static ResourceLocation parseId(String id, String entry) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        // tryParse accepts a degenerate empty path ("#" -> "minecraft:"), which can never match.
        if (parsed == null || parsed.getPath().isEmpty()) {
            throw new IllegalArgumentException("invalid id in matches entry \"" + entry + "\"");
        }
        return parsed;
    }

    record Exact(ResourceLocation id) implements MatchEntry {
        @Override
        public boolean matches(ResourceLocation candidate, Predicate<ResourceLocation> tagTest) {
            return id.equals(candidate);
        }

        @Override
        public int specificity() {
            return 3;
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

    record Tag(ResourceLocation tagId) implements MatchEntry {
        @Override
        public boolean matches(ResourceLocation candidate, Predicate<ResourceLocation> tagTest) {
            return tagTest.test(tagId);
        }

        @Override
        public int specificity() {
            return 2;
        }

        @Override
        public String toString() {
            return "#" + tagId;
        }
    }

    record Namespace(String namespace) implements MatchEntry {
        @Override
        public boolean matches(ResourceLocation candidate, Predicate<ResourceLocation> tagTest) {
            return candidate.getNamespace().equals(namespace);
        }

        @Override
        public int specificity() {
            return 1;
        }

        @Override
        public String toString() {
            return namespace + ":*";
        }
    }
}
