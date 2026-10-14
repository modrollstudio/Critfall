package studio.modroll.critfall.api.combat;

/** What a successful save against a hazard takes ({@link studio.modroll.critfall.api.HazardSave}). Since 0.2.13. */
public enum SuccessDamage {
    /** Half the rolled damage, rounded down. */
    HALF,
    /** No damage. */
    NONE
}
