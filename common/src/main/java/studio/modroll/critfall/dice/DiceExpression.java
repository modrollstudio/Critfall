package studio.modroll.critfall.dice;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * An immutable, parsed dice expression such as {@code 2d6+3}, {@code 1d8+2d6+3} or {@code
 * 2d20kh1}.
 *
 * <p>Grammar (case-insensitive, whitespace ignored): one or more terms joined by {@code +} or
 * {@code -}; a term is either an integer constant or {@code [count]d<sides>[kh<n>|kl<n>]} where
 * {@code count} defaults to 1 and the keep count after {@code kh}/{@code kl} defaults to 1.
 *
 * <p>Sanity limits (dice come from datapack JSON, so bad input must not hurt the server): at most
 * {@value #MAX_DICE_PER_TERM} dice per term, {@value #MAX_SIDES} sides per die, {@value
 * #MAX_TERMS} terms, and {@value #MAX_LENGTH} characters.
 *
 * <p>{@link #toString()} returns the canonical lowercase form with no whitespace.
 */
public final class DiceExpression {

    static final int MAX_DICE_PER_TERM = 100;
    static final int MAX_SIDES = 1000;
    static final int MAX_TERMS = 100;
    static final int MAX_LENGTH = 256;
    private static final int MAX_CONSTANT = 1_000_000;

    private final List<Term> terms;
    private final String canonical;

    private DiceExpression(List<Term> terms, String canonical) {
        this.terms = terms;
        this.canonical = canonical;
    }

    /** Parses {@code text}, throwing {@link DiceParseException} if it is not a valid expression. */
    public static DiceExpression parse(String text) {
        if (text == null) {
            throw new DiceParseException("dice expression is null");
        }
        StringBuilder compact = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                compact.append(Character.toLowerCase(c));
            }
        }
        String input = compact.toString();
        if (input.isEmpty()) {
            throw new DiceParseException("dice expression is empty");
        }
        if (input.length() > MAX_LENGTH) {
            throw new DiceParseException("dice expression is longer than " + MAX_LENGTH + " characters");
        }

        Cursor cursor = new Cursor(input);
        List<Term> terms = new ArrayList<>();
        boolean first = true;
        while (cursor.hasNext()) {
            int sign = 1;
            if (first) {
                if (cursor.consumeIf('-')) {
                    sign = -1;
                }
            } else {
                char op = cursor.next();
                if (op == '-') {
                    sign = -1;
                } else if (op != '+') {
                    throw cursor.error("expected '+' or '-' before position " + cursor.position());
                }
            }
            terms.add(parseTerm(cursor, sign));
            first = false;
        }
        if (terms.size() > MAX_TERMS) {
            throw new DiceParseException("dice expression has more than " + MAX_TERMS + " terms");
        }

        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            if (term.sign() < 0) {
                canonical.append('-');
            } else if (i > 0) {
                canonical.append('+');
            }
            canonical.append(term.notation());
        }
        return new DiceExpression(List.copyOf(terms), canonical.toString());
    }

    private static Term parseTerm(Cursor cursor, int sign) {
        Integer lead = cursor.tryReadNumber();
        if (cursor.consumeIf('d')) {
            int count = lead == null ? 1 : lead;
            Integer sides = cursor.tryReadNumber();
            if (sides == null) {
                throw cursor.error("missing number of sides after 'd'");
            }
            KeepMode keep = KeepMode.ALL;
            int keepCount = count;
            if (cursor.consumeIf('k')) {
                if (cursor.consumeIf('h')) {
                    keep = KeepMode.HIGHEST;
                } else if (cursor.consumeIf('l')) {
                    keep = KeepMode.LOWEST;
                } else {
                    throw cursor.error("expected 'kh' or 'kl'");
                }
                Integer explicitKeep = cursor.tryReadNumber();
                keepCount = explicitKeep == null ? 1 : explicitKeep;
            }
            if (count < 1) {
                throw cursor.error("dice count must be at least 1");
            }
            if (count > MAX_DICE_PER_TERM) {
                throw cursor.error("at most " + MAX_DICE_PER_TERM + " dice per term");
            }
            if (sides < 1) {
                throw cursor.error("dice must have at least 1 side");
            }
            if (sides > MAX_SIDES) {
                throw cursor.error("dice can have at most " + MAX_SIDES + " sides");
            }
            if (keepCount < 1) {
                throw cursor.error("must keep at least 1 die");
            }
            if (keepCount > count) {
                throw cursor.error("cannot keep " + keepCount + " of " + count + " dice");
            }
            return new DiceTerm(sign, count, sides, keep, keepCount);
        }
        if (lead == null) {
            throw cursor.error("expected a number or 'd' at position " + cursor.position());
        }
        if (lead > MAX_CONSTANT) {
            throw cursor.error("constant larger than " + MAX_CONSTANT);
        }
        return new ConstantTerm(sign, lead);
    }

    /** Rolls this expression once, drawing every die from {@code rng}. */
    public RollResult roll(RandomGenerator rng) {
        List<DieRoll> dice = new ArrayList<>();
        int total = 0;
        int modifier = 0;
        for (Term term : terms) {
            switch (term) {
                case ConstantTerm constant -> {
                    int value = constant.sign() * constant.value();
                    modifier += value;
                    total += value;
                }
                case DiceTerm dt -> {
                    int[] values = new int[dt.count()];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = rng.nextInt(dt.sides()) + 1;
                    }
                    boolean[] kept = dt.selectKept(values);
                    for (int i = 0; i < values.length; i++) {
                        dice.add(new DieRoll(dt.sides(), values[i], kept[i]));
                        if (kept[i]) {
                            total += dt.sign() * values[i];
                        }
                    }
                }
            }
        }
        return new RollResult(total, dice, modifier);
    }

    /** Smallest total this expression can roll. */
    public int minValue() {
        int min = 0;
        for (Term term : terms) {
            min += term.sign() > 0 ? term.smallestMagnitude() : -term.largestMagnitude();
        }
        return min;
    }

    /**
     * Expected value, computed as {@code (min + max) / 2}. Exact for plain dice sums; a close
     * approximation for keep-highest/lowest terms — good enough for damage-bonus derivation.
     */
    public double averageValue() {
        return (minValue() + maxValue()) / 2.0;
    }

    /** Largest total this expression can roll. */
    public int maxValue() {
        int max = 0;
        for (Term term : terms) {
            max += term.sign() > 0 ? term.largestMagnitude() : -term.smallestMagnitude();
        }
        return max;
    }

    @Override
    public String toString() {
        return canonical;
    }

    /** Two expressions are equal when their canonical forms match (the canonical form determines the terms). */
    @Override
    public boolean equals(Object other) {
        return other instanceof DiceExpression expression && canonical.equals(expression.canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    private enum KeepMode {
        ALL,
        HIGHEST,
        LOWEST
    }

    private sealed interface Term {
        int sign();

        /** Smallest absolute value this term can contribute (before its sign is applied). */
        int smallestMagnitude();

        /** Largest absolute value this term can contribute (before its sign is applied). */
        int largestMagnitude();

        /** Canonical notation without the sign, e.g. {@code 2d20kh1} or {@code 3}. */
        String notation();
    }

    private record ConstantTerm(int sign, int value) implements Term {
        @Override
        public int smallestMagnitude() {
            return value;
        }

        @Override
        public int largestMagnitude() {
            return value;
        }

        @Override
        public String notation() {
            return Integer.toString(value);
        }
    }

    private record DiceTerm(int sign, int count, int sides, KeepMode keep, int keepCount) implements Term {

        /** Flags the {@code keepCount} highest (or lowest) dice; earlier dice win ties. */
        boolean[] selectKept(int[] values) {
            boolean[] kept = new boolean[values.length];
            if (keep == KeepMode.ALL) {
                java.util.Arrays.fill(kept, true);
                return kept;
            }
            Integer[] order = new Integer[values.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(
                    order,
                    (a, b) -> keep == KeepMode.HIGHEST
                            ? Integer.compare(values[b], values[a])
                            : Integer.compare(values[a], values[b]));
            for (int i = 0; i < keepCount; i++) {
                kept[order[i]] = true;
            }
            return kept;
        }

        @Override
        public int smallestMagnitude() {
            return keepCount();
        }

        @Override
        public int largestMagnitude() {
            return keepCount() * sides;
        }

        @Override
        public String notation() {
            StringBuilder sb = new StringBuilder().append(count).append('d').append(sides);
            if (keep == KeepMode.HIGHEST) {
                sb.append("kh").append(keepCount);
            } else if (keep == KeepMode.LOWEST) {
                sb.append("kl").append(keepCount);
            }
            return sb.toString();
        }
    }

    /** Simple character cursor over the compacted expression, with error helpers. */
    private static final class Cursor {
        private final String input;
        private int pos;

        Cursor(String input) {
            this.input = input;
        }

        boolean hasNext() {
            return pos < input.length();
        }

        char next() {
            return input.charAt(pos++);
        }

        boolean consumeIf(char c) {
            if (pos < input.length() && input.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        int position() {
            return pos;
        }

        /** Reads a run of digits, or returns null if the next character is not a digit. */
        Integer tryReadNumber() {
            int start = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                return null;
            }
            if (pos - start > 7) {
                throw error("number too large");
            }
            return Integer.parseInt(input, start, pos, 10);
        }

        DiceParseException error(String detail) {
            return new DiceParseException("invalid dice expression \"" + input + "\": " + detail);
        }
    }
}
