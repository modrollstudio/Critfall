package studio.modroll.critfall.api;

import java.util.function.Supplier;

/**
 * Dice already rolled, waiting to land: from {@link RollService#rollAttack}, {@link RollService#rollHazardSave} or
 * {@link RollService#rollHeal}. Rolling decides everything; landing applies it and shows the readout. For something
 * that takes time to arrive, such as a spell's bolt: roll it as it goes off, land it when it gets there, and drop it
 * if its target is gone by then. Since 0.2.21.
 */
public final class Rolled<R> {

    private Supplier<R> landing;

    Rolled(Supplier<R> landing) {
        this.landing = landing;
    }

    /** Applies the roll and shows its readout, as the {@code perform} call would have. Lands once; again throws. */
    public R land() {
        if (landing == null) {
            throw new IllegalStateException("already landed");
        }
        Supplier<R> now = landing;
        landing = null;
        return now.get();
    }
}
