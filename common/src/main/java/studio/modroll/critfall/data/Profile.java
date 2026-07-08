package studio.modroll.critfall.data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import studio.modroll.critfall.api.AttackDelivery;

/** What {@link ProfileStore} needs to resolve "which profile applies to this id". */
public interface Profile {

    /** The datapack file id this profile was loaded from, e.g. {@code critfall:zombies}. */
    ResourceLocation id();

    List<MatchEntry> matches();

    /** Higher wins; ties break on match specificity, then on lexicographically smaller id. */
    int priority();

    /**
     * The delivery methods this profile applies to (issue #3: a thrown trident vs a melee stab).
     * Empty means every delivery — the default for profile kinds that never constrain it.
     */
    default Set<AttackDelivery> deliveries() {
        return Set.of();
    }

    /** Parses the optional {@code delivery} list shared by item profiles and flavor pools. */
    static Set<AttackDelivery> parseDeliveries(LenientJson json) {
        Set<AttackDelivery> deliveries = new LinkedHashSet<>();
        for (String text : json.stringList("delivery")) {
            deliveries.add(
                    switch (text) {
                        case "melee" -> AttackDelivery.MELEE;
                        case "projectile" -> AttackDelivery.PROJECTILE;
                        case "thrown" -> AttackDelivery.THROWN;
                        case "spell" -> AttackDelivery.SPELL;
                        default ->
                            throw new IllegalArgumentException("'delivery' entries must be \"melee\", \"projectile\","
                                    + " \"thrown\", or \"spell\", was \"" + text + "\"");
                    });
        }
        return Set.copyOf(deliveries);
    }
}
