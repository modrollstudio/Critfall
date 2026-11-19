package studio.modroll.critfall.api.combat;

/**
 * The outcome of {@link studio.modroll.critfall.api.RollService#performHazardSave}. Since 0.2.13.
 *
 * @param save the target's saving throw
 * @param damage the damage Critfall applied: rolled, after the target's resistances and the global damage
 *     multiplier, halved or zero on a success. Vanilla mitigation (armor, unless {@code
 *     balance.disable_vanilla_armor_reduction} is on, Resistance, absorption) still follows
 */
public record HazardSaveResult(SaveResult save, int damage) {

    public boolean saved() {
        return save.saved();
    }
}
