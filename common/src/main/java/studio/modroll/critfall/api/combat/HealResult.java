package studio.modroll.critfall.api.combat;

import java.util.Objects;
import studio.modroll.critfall.api.dice.RollResult;

/**
 * The outcome of {@link studio.modroll.critfall.api.RollService#performHeal}. Since 0.2.20.
 *
 * @param roll the healing dice as rolled
 * @param healed the health the target actually gained: less than the roll when it reached its maximum health, 0
 *     when it was dead or another mod stopped the heal
 */
public record HealResult(RollResult roll, float healed) {

    public HealResult {
        Objects.requireNonNull(roll, "roll");
    }
}
