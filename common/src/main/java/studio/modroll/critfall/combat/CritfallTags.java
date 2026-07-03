package studio.modroll.critfall.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import studio.modroll.critfall.Critfall;

/** Damage type tags pack devs use to steer Critfall (see docs/design-decisions.md). */
public final class CritfallTags {

    /** Damage types that always pass through vanilla untouched (DoT, environment, AoE…). */
    public static final TagKey<DamageType> EXEMPT = create("exempt");

    /** Damage types that skip the to-hit roll but still roll damage dice. */
    public static final TagKey<DamageType> ALWAYS_HITS = create("always_hits");

    private CritfallTags() {}

    private static TagKey<DamageType> create(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, name));
    }
}
