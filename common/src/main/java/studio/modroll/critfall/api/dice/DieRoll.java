package studio.modroll.critfall.api.dice;

/**
 * A single physical die rolled during evaluation.
 *
 * @param sides how many sides the die had
 * @param value the face it showed, in {@code [1, sides]}
 * @param kept whether it counts toward the total (dropped by kh/kl otherwise)
 */
public record DieRoll(int sides, int value, boolean kept) {}
