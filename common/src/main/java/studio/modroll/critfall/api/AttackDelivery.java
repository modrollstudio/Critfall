package studio.modroll.critfall.api;

/**
 * How an attack is delivered — part of {@link AttackContext}. Distinguishes hybrid items (issue #9):
 * a {@link #THROWN} trident matches the ranged flavor pool, a {@link #MELEE} trident stab the melee
 * one. Consumers set this; the automatic pipeline maps its {@code DamageCategory} onto it.
 */
public enum AttackDelivery {
    MELEE,
    PROJECTILE,
    THROWN,
    SPELL
}
