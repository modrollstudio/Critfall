package studio.modroll.critfall.data;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** What {@link ProfileStore} needs to resolve "which profile applies to this id". */
public interface Profile {

    /** The datapack file id this profile was loaded from, e.g. {@code critfall:zombies}. */
    ResourceLocation id();

    List<MatchEntry> matches();

    /** Higher wins; ties break on match specificity, then on lexicographically smaller id. */
    int priority();
}
