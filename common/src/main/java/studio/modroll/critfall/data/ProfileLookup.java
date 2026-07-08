package studio.modroll.critfall.data;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.api.AttackDelivery;

/**
 * Bridges {@link ProfileStore}'s pure id/tag resolution to live registry objects. This is the
 * only place profile matching touches Minecraft registries, so everything upstream stays
 * JVM-testable.
 */
public final class ProfileLookup {

    private ProfileLookup() {}

    public static Optional<EntityProfile> forEntity(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return ProfileStore.findEntityProfile(
                id, tagId -> entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, tagId)));
    }

    /** Delivery-blind item lookup for no-context callers (commands, generic API queries). */
    public static Optional<ItemProfile> forItem(ItemStack stack) {
        return forItem(stack, null);
    }

    /** The item profile for this stack used with {@code delivery} (a thrown trident vs a melee stab). */
    public static Optional<ItemProfile> forItem(ItemStack stack, AttackDelivery delivery) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ProfileStore.findItemProfile(id, tagId -> stack.is(TagKey.create(Registries.ITEM, tagId)), delivery);
    }

    /**
     * The flavor pool matching this weapon item used with {@code delivery}; an empty stack is item
     * minecraft:air (matches default).
     */
    public static Optional<FlavorPool> forFlavor(ItemStack stack, AttackDelivery delivery) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ProfileStore.findFlavorPool(id, tagId -> stack.is(TagKey.create(Registries.ITEM, tagId)), delivery);
    }

    /** The spell profile matching this source's DAMAGE TYPE (by id or tag). */
    public static Optional<SpellProfile> forSpell(DamageSource source) {
        Optional<ResourceLocation> typeId = source.typeHolder().unwrapKey().map(ResourceKey::location);
        if (typeId.isEmpty()) {
            return Optional.empty();
        }
        return ProfileStore.findSpellProfile(
                typeId.get(), tagId -> source.is(TagKey.create(Registries.DAMAGE_TYPE, tagId)));
    }

    /** The defender's resist/immune/vulnerable multiplier for this damage source. */
    public static float damageMultiplier(EntityProfile profile, DamageSource source) {
        if (profile.damageModifiers().isEmpty()) {
            return 1.0f;
        }
        Optional<ResourceLocation> typeId = source.typeHolder().unwrapKey().map(ResourceKey::location);
        if (typeId.isEmpty()) {
            return 1.0f;
        }
        return profile.damageModifiers()
                .multiplier(typeId.get(), tagId -> source.is(TagKey.create(Registries.DAMAGE_TYPE, tagId)));
    }
}
