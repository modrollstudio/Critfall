package studio.modroll.critfall;

import java.util.Random;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * Composition root for combat randomness and rules. This is the ONLY place a real RNG is
 * constructed — no {@code new Random()}/{@code level.random} anywhere in game logic. Game code
 * asks this service for its roller, and tests/GameTests swap in a scripted one to force exact
 * rolls.
 */
public final class RollService {

    private static volatile DiceRoller roller = new DiceRoller(new Random());
    private static volatile Rules rules = Rules.DEFAULTS;

    private RollService() {}

    public static DiceRoller roller() {
        return roller;
    }

    public static Rules rules() {
        return rules;
    }

    /** Injection point for tests and (from M3) the dry-run/config commands. */
    public static void setRoller(DiceRoller newRoller) {
        roller = newRoller;
    }

    public static void setRules(Rules newRules) {
        rules = newRules;
    }
}
