package studio.modroll.critfall.data;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.combat.CritfallTags;

/**
 * Bridges {@link ProfileStore}'s pure id/tag resolution to live registry objects. This is the
 * only place profile matching touches Minecraft registries, so everything upstream stays
 * JVM-testable.
 */
public final class ProfileLookup {

    /**
     * The item id a flavor pool matches to catch an empty-handed attacker that does not fight with
     * fists ({@link CritfallTags#FIGHTS_WITH_FISTS}): a zoglin's or a slime's natural attack. Not a
     * real item, so only an exact {@code matches} entry reaches it.
     */
    public static final ResourceLocation NATURAL_ATTACK =
            ResourceLocation.fromNamespaceAndPath(Critfall.MOD_ID, "natural_attack");

    /** Assigned ids already warned about as not loaded. */
    private static final Set<ResourceLocation> UNKNOWN_ASSIGNED = ConcurrentHashMap.newKeySet();

    private ProfileLookup() {}

    /** The profile assigned to this entity when it is loaded, else the one matching its type or tags. */
    public static Optional<EntityProfile> forEntity(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        ResourceLocation assigned = assignedProfileId(entity).orElse(null);
        if (assigned != null
                && !ProfileStore.entityProfiles().containsKey(assigned)
                && UNKNOWN_ASSIGNED.add(assigned)) {
            Critfall.LOG.warn(
                    "Entity profile {} is assigned to a {} but is not loaded; using its type's profile (logged once)",
                    assigned,
                    id);
        }
        return ProfileStore.findEntityProfile(
                assigned, id, tagId -> entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, tagId)));
    }

    /** The profile id assigned to this entity, whether or not a profile with that id is loaded. */
    public static Optional<ResourceLocation> assignedProfileId(Entity entity) {
        return entity instanceof EntityProfileHolder holder
                ? Optional.ofNullable(holder.critfall$profileId())
                : Optional.empty();
    }

    /** Assigns {@code id} to {@code entity}, or clears it when null. */
    public static void assignProfile(LivingEntity entity, ResourceLocation id) {
        ((EntityProfileHolder) entity).critfall$setProfileId(id);
    }

    /** Mixin hook at the end of {@code LivingEntity.addAdditionalSaveData}. */
    public static void saveAssignedProfile(EntityProfileHolder holder, CompoundTag tag) {
        ResourceLocation id = holder.critfall$profileId();
        if (id != null) {
            tag.putString(EntityProfileHolder.NBT_KEY, id.toString());
        }
    }

    /**
     * Mixin hook at the end of {@code LivingEntity.readAdditionalSaveData}. The tag is the entity's whole
     * data ({@code /data merge} included), so an absent key clears the assignment.
     */
    public static void loadAssignedProfile(EntityProfileHolder holder, CompoundTag tag) {
        if (!tag.contains(EntityProfileHolder.NBT_KEY, Tag.TAG_STRING)) {
            holder.critfall$setProfileId(null);
            return;
        }
        String text = tag.getString(EntityProfileHolder.NBT_KEY);
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
            Critfall.LOG.warn("Ignoring {} \"{}\": not a valid profile id", EntityProfileHolder.NBT_KEY, text);
        }
        holder.critfall$setProfileId(id);
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
     * The flavor pool matching this weapon item used with {@code delivery}. An empty stack is item
     * minecraft:air (the unarmed pool) only for a melee attack by an {@code attacker} that fights with
     * fists; any other empty-handed attack (a beast's, or a ranged or spell one) is
     * {@link #NATURAL_ATTACK}.
     */
    public static Optional<FlavorPool> forFlavor(Entity attacker, ItemStack stack, AttackDelivery delivery) {
        if (stack.isEmpty()
                && (delivery != AttackDelivery.MELEE || !attacker.getType().is(CritfallTags.FIGHTS_WITH_FISTS))) {
            return ProfileStore.findFlavorPool(NATURAL_ATTACK, tagId -> false, delivery);
        }
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
