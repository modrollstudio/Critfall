package studio.modroll.critfall.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import studio.modroll.critfall.Critfall;

/** Tags pack devs use to steer Critfall (see docs/design-decisions.md). */
public final class CritfallTags {

    /** Damage types that always pass through vanilla untouched (DoT, environment, AoE…). */
    public static final TagKey<DamageType> EXEMPT = create("exempt");

    /** Damage types that skip the to-hit roll but still roll damage dice. */
    public static final TagKey<DamageType> ALWAYS_HITS = create("always_hits");

    /**
     * Damage types resolved as spells (M5), even when the caster is the direct entity. Ships
     * pre-populated with {@code #neoforge:is_magic} and the Iron's Spells / Ars Nouveau damage
     * types as optional entries (see docs/compat.md).
     */
    public static final TagKey<DamageType> SPELL = create("spell");

    /**
     * Entity types whose empty-handed melee attacks are punches and read with the unarmed flavor
     * lines. Every other empty-handed attack (by beasts, slimes, golems, modded mobs, or a ranged or
     * spell attack by anyone) gets the natural-attack pool instead (see {@link studio.modroll.critfall.data.ProfileLookup#forFlavor}).
     */
    public static final TagKey<EntityType<?>> FIGHTS_WITH_FISTS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "fights_with_fists"));

    private CritfallTags() {}

    private static TagKey<DamageType> create(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, name));
    }
}
