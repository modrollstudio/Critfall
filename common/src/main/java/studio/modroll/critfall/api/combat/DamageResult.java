package studio.modroll.critfall.api.combat;

import java.util.Objects;
import studio.modroll.critfall.api.dice.RollResult;

/**
 * The outcome of {@link studio.modroll.critfall.api.RollService#rollDamage}. Since 0.2.22.
 *
 * @param roll the damage dice as rolled
 * @param damage what Critfall applied: at least 1, then the target's resistances and the global multiplier
 */
public record DamageResult(RollResult roll, int damage) {

    public DamageResult {
        Objects.requireNonNull(roll, "roll");
    }
}
