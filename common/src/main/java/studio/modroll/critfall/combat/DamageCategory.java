package studio.modroll.critfall.combat;

/** What kind of damage event we are looking at, decided by {@link DamageClassifier}. */
public enum DamageCategory {
    /** Tagged {@code #critfall:exempt} — always passes through vanilla untouched. */
    EXEMPT,
    /** Tagged {@code #critfall:always_hits} — skips the to-hit roll but still rolls damage dice. */
    ALWAYS_HITS,
    /** Arrow, trident, fireball… — rolled from M5 onward. */
    PROJECTILE,
    /** No living attacker (cactus, fall, world hazards) — never rolled. */
    ENVIRONMENTAL,
    /** A living entity hitting directly. The only category rolled in M2. */
    MELEE,
    /** Living attacker but indirect (spells, potions, other) — handled in M5. */
    OTHER
}
