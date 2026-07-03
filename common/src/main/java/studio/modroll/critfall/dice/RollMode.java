package studio.modroll.critfall.dice;

/** How a d20 check is rolled: straight, or with advantage/disadvantage (roll two, keep one). */
public enum RollMode {
    NORMAL("1d20"),
    ADVANTAGE("2d20kh1"),
    DISADVANTAGE("2d20kl1");

    private final String d20Notation;
    private volatile DiceExpression d20Expression;

    RollMode(String d20Notation) {
        this.d20Notation = d20Notation;
    }

    /** The d20 check this mode stands for, e.g. {@code 2d20kh1} for advantage. */
    public DiceExpression d20Expression() {
        DiceExpression expr = d20Expression;
        if (expr == null) {
            expr = DiceExpression.parse(d20Notation);
            d20Expression = expr;
        }
        return expr;
    }
}
