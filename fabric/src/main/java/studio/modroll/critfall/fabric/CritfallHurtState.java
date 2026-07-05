package studio.modroll.critfall.fabric;

/**
 * Duck-typing seam implemented by {@link LivingEntityMixin}: carries one damage roll's decision from
 * the {@code ALLOW_DAMAGE} handler (which runs the pipeline at the correct point) across to the
 * {@code actuallyHurt} / {@code getDamageAfterArmorAbsorb} mixins that apply the rolled amount and
 * bypass armor. Every {@code LivingEntity} implements this at runtime via the mixin.
 */
public interface CritfallHurtState {

    /** Cache a HIT/CRIT decision: the rolled amount to apply and whether to bypass base armor. */
    void critfall$store(float amount, boolean bypassArmor);

    /** Reset before each damage roll so a stale decision never leaks into the next hurt. */
    void critfall$clear();

    /** True when a rolled amount is pending for the in-flight hurt. */
    boolean critfall$active();

    /** The rolled amount to substitute for the vanilla damage. */
    float critfall$amount();

    /** Whether the in-flight rolled hit bypasses vanilla base-armor reduction. */
    boolean critfall$bypassArmor();
}
