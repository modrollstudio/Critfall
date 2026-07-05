package studio.modroll.critfall.combat;

/**
 * Loader-agnostic view of a single in-flight damage event, letting {@link DamageInterception} run
 * identically on both loaders. NeoForge backs this with {@code LivingIncomingDamageEvent}; Fabric
 * backs it from a {@code LivingEntity#hurt} mixin. {@code bypassArmor} zeroes only vanilla base
 * armor reduction — enchantment protection, the Resistance effect, and absorption still apply,
 * matching the M2 armor decision (docs/design-decisions.md).
 */
public interface IncomingDamage {

    /** The incoming amount before mitigation (the value the roll's damage will replace). */
    float amount();

    /** Replace the outgoing damage with the rolled amount (HIT/CRIT). */
    void setAmount(float amount);

    /** Cancel the damage entirely (MISS/FUMBLE, or a listener veto). */
    void cancel();

    /** Suppress vanilla base-armor reduction for this event. */
    void bypassArmor();
}
