package studio.modroll.critfall.data;

import net.minecraft.resources.ResourceLocation;

/**
 * The entity profile assigned to one living entity, saved with it under {@link #NBT_KEY}. Each loader's
 * {@code LivingEntity} mixin implements this; read and write it through {@link ProfileLookup}.
 */
public interface EntityProfileHolder {

    /** The entity NBT key, so a pack can assign a profile with {@code /summon ... {CritfallProfile:"ns:id"}}. */
    String NBT_KEY = "CritfallProfile";

    /** Null when none is assigned. */
    ResourceLocation critfall$profileId();

    /** Null clears it. */
    void critfall$setProfileId(ResourceLocation id);
}
