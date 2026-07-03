package studio.modroll.critfall.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pure classification of a damage event. The loader glue extracts these booleans from the actual
 * {@code DamageSource} (tag membership, attacker entity) so this logic stays JVM-testable.
 */
public final class DamageClassifier {

    private DamageClassifier() {}

    /** Classifies a live damage source (thin extraction wrapper over the pure overload). */
    public static DamageCategory classify(DamageSource source) {
        return classify(
                source.is(CritfallTags.EXEMPT),
                source.is(CritfallTags.ALWAYS_HITS),
                source.is(DamageTypeTags.IS_PROJECTILE),
                source.getEntity() instanceof LivingEntity,
                source.isDirect());
    }

    /**
     * @param exempt damage type is in {@code #critfall:exempt}
     * @param alwaysHits damage type is in {@code #critfall:always_hits}
     * @param projectile damage type is in {@code #minecraft:is_projectile}
     * @param hasLivingAttacker the causing entity is a living entity
     * @param direct the causing entity is also the direct entity (no projectile/spell in between)
     */
    public static DamageCategory classify(
            boolean exempt, boolean alwaysHits, boolean projectile, boolean hasLivingAttacker, boolean direct) {
        if (exempt) {
            return DamageCategory.EXEMPT;
        }
        if (alwaysHits) {
            return DamageCategory.ALWAYS_HITS;
        }
        if (projectile) {
            return DamageCategory.PROJECTILE;
        }
        if (!hasLivingAttacker) {
            return DamageCategory.ENVIRONMENTAL;
        }
        return direct ? DamageCategory.MELEE : DamageCategory.OTHER;
    }
}
