package studio.modroll.critfall.combat;

/** What kind of damage event we are looking at, decided by {@link DamageClassifier}. */
public enum DamageCategory {
    /** Tagged {@code #critfall:exempt} — always passes through vanilla untouched. */
    EXEMPT,
    /** Tagged {@code #critfall:always_hits} — skips the to-hit roll but still rolls damage dice. */
    ALWAYS_HITS,
    /** Arrow, trident, fireball… — rolled on impact from the launcher/ammo profiles. */
    PROJECTILE,
    /** No living attacker (cactus, fall, world hazards) — never rolled. */
    ENVIRONMENTAL,
    /** A living entity hitting directly (and not spell-tagged). */
    MELEE,
    /**
     * Tagged {@code #critfall:spell}, or any other indirect living-caused damage. Resolved via
     * spell profiles: attack roll or saving throw.
     */
    SPELL
}
