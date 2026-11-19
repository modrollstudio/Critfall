package studio.modroll.critfall;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import studio.modroll.critfall.api.ArmorClassSource;
import studio.modroll.critfall.api.ModifierProvider;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.combat.Rules;

/**
 * Composition root for combat randomness and rules. This is the ONLY place a real RNG is
 * constructed — no {@code new Random()}/{@code level.random} anywhere in game logic. Game code
 * asks this service for its roller, and tests/GameTests swap in a scripted one to force exact
 * rolls.
 */
public final class RollRuntime {

    private static volatile DiceRoller roller = new DiceRoller(new Random());

    /**
     * Drives cosmetic feedback selection (flavor line picks) ONLY. Deliberately SEPARATE from the
     * combat {@link #roller()} so a multi-line flavor pool's extra {@code die(N)} draw never
     * perturbs the server-authoritative combat RNG that GameTests script.
     */
    private static volatile DiceRoller feedbackRoller = new DiceRoller(new Random());

    private static volatile Rules rules = Rules.DEFAULTS;

    private static volatile ModifierProvider modifierProvider;

    private static final CopyOnWriteArrayList<ArmorClassSource> armorClassSources = new CopyOnWriteArrayList<>();

    private RollRuntime() {}

    public static DiceRoller roller() {
        return roller;
    }

    public static DiceRoller feedbackRoller() {
        return feedbackRoller;
    }

    public static Rules rules() {
        return rules;
    }

    /** Injection point for tests and the dry-run/config commands. */
    public static void setRoller(DiceRoller newRoller) {
        roller = newRoller;
    }

    public static void resetRoller() {
        roller = new DiceRoller(new Random());
    }

    /** Injection point for tests, same as {@link #setRoller}, but for the cosmetic flavor roller. */
    public static void setFeedbackRoller(DiceRoller newRoller) {
        feedbackRoller = newRoller;
    }

    public static void setRules(Rules newRules) {
        rules = newRules;
    }

    public static Optional<ModifierProvider> modifierProvider() {
        return Optional.ofNullable(modifierProvider);
    }

    /** Returns the previous provider; null empties the slot. */
    public static synchronized ModifierProvider setModifierProvider(ModifierProvider provider) {
        ModifierProvider previous = modifierProvider;
        modifierProvider = provider;
        return previous;
    }

    /** In the order added. */
    public static List<ArmorClassSource> armorClassSources() {
        return armorClassSources;
    }

    /** False when {@code source} was already added. */
    public static boolean addArmorClassSource(ArmorClassSource source) {
        return armorClassSources.addIfAbsent(source);
    }

    public static boolean removeArmorClassSource(ArmorClassSource source) {
        return armorClassSources.remove(source);
    }
}
