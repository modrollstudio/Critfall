package studio.modroll.critfall.api.dice;

/** Thrown when a dice expression string is not valid. The message always says what was wrong. */
public class DiceParseException extends RuntimeException {

    public DiceParseException(String message) {
        super(message);
    }
}
