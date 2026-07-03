package studio.modroll.critfall.combat;

/** Result of a d20 attack roll against an armor class. */
public enum AttackOutcome {
    /** Attack total under AC — no damage. */
    MISS,
    /** Natural 1 — misses and triggers fumble consequences. */
    FUMBLE,
    /** Attack total meets or beats AC — damage dice are rolled. */
    HIT,
    /** Natural 20 — hits regardless of AC with maximized damage dice. */
    CRIT
}
