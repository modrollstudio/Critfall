package studio.modroll.critfall.combat;

import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.ContestResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.RollDetail;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.dice.RollResult;

/**
 * Resolves one attack: d20 (+ bonus) vs AC, then damage dice. Pure JVM — no Minecraft classes —
 * so every path (nat 1, nat 20, confirmation, cooldown, each crit rule…) is unit-testable with a
 * scripted RNG.
 */
public final class CombatEngine {

    private CombatEngine() {}

    /**
     * Everything profile resolution feeds into one roll.
     *
     * @param critRange lowest natural roll that crits (20 = only nat 20); a raised range still
     *     needs the attack to hit — only nat 20 auto-hits
     * @param fumbleSuppressed nat 1s downgrade to plain misses while true — set when a fumble
     *     triggered within {@code fumbles.cooldown_ticks} or the attacker is outside
     *     {@code fumbles.applies_to}
     */
    public record AttackInput(
            int attackBonus,
            int armorClass,
            RollMode mode,
            DiceExpression damageDice,
            int critRange,
            boolean fumbleSuppressed,
            int defenderAcBonus) {

        public AttackInput(int attackBonus, int armorClass, RollMode mode, DiceExpression damageDice) {
            this(attackBonus, armorClass, mode, damageDice, 20, false, 0);
        }

        int effectiveArmorClass() {
            return armorClass + defenderAcBonus;
        }
    }

    /**
     * Rolls the attack and, when it lands, the damage.
     *
     * <p>Outcome rules (each individually toggleable via {@link Rules}): a natural 1 always misses
     * and becomes a {@link AttackOutcome#FUMBLE} when fumbles are enabled, not suppressed, and
     * the confirmation roll (a second d20 vs the configured DC) fails; a natural 20 always hits;
     * naturals at or above the crit range crit on a hit, with damage per the configured crit rule;
     * otherwise {@code natural + attackBonus >= armorClass} hits.
     */
    public static AttackResult resolveAttack(DiceRoller roller, Rules rules, AttackInput input) {
        RollDetail roll = d20(roller, input.mode());
        int natural = roll.kept();
        int attackTotal = natural + input.attackBonus();
        int armorClass = input.effectiveArmorClass();
        int defenderAcBonus = input.defenderAcBonus();

        boolean forcedMiss = natural == 1 && rules.fumbles().nat1AlwaysMisses();
        boolean forcedHit = natural == 20 && rules.crits().nat20AlwaysHits();
        boolean misses = forcedMiss || (!forcedHit && attackTotal < armorClass);

        if (misses) {
            boolean fumble = natural == 1
                    && rules.fumbles().enabled()
                    && !input.fumbleSuppressed()
                    && confirmFumble(roller, rules.fumbles());
            AttackOutcome outcome = fumble ? AttackOutcome.FUMBLE : AttackOutcome.MISS;
            return new AttackResult(outcome, natural, attackTotal, armorClass, defenderAcBonus, 0, roll);
        }
        // With damage dice off the vanilla amount applies downstream — draw nothing from the RNG.
        if (natural >= input.critRange() && rules.crits().enabled()) {
            int damage = rules.damageDice()
                    ? Math.max(0, critDamage(roller, rules.crits().rule(), input.damageDice()))
                    : 0;
            return new AttackResult(
                    AttackOutcome.CRIT, natural, attackTotal, armorClass, defenderAcBonus, damage, roll);
        }
        int damage =
                rules.damageDice() ? Math.max(0, roller.roll(input.damageDice()).total()) : 0;
        return new AttackResult(AttackOutcome.HIT, natural, attackTotal, armorClass, defenderAcBonus, damage, roll);
    }

    public static SaveResult resolveSave(DiceRoller roller, int saveBonus, int dc) {
        return resolveSave(roller, saveBonus, dc, RollMode.NORMAL);
    }

    public static SaveResult resolveSave(DiceRoller roller, int saveBonus, int dc, RollMode mode) {
        RollDetail roll = d20(roller, mode);
        return new SaveResult(roll.kept(), roll.kept() + saveBonus, dc, roll);
    }

    /** Each side rolls a d20 (own mode) + bonus; the initiator rolls first, so scripted rollers script it first. */
    public static ContestResult resolveContest(
            DiceRoller roller, int initiatorBonus, RollMode initiatorMode, int opponentBonus, RollMode opponentMode) {
        RollDetail initiatorRoll = d20(roller, initiatorMode);
        RollDetail opponentRoll = d20(roller, opponentMode);
        return new ContestResult(
                initiatorRoll.kept(),
                initiatorRoll.kept() + initiatorBonus,
                opponentRoll.kept(),
                opponentRoll.kept() + opponentBonus,
                initiatorRoll,
                opponentRoll);
    }

    private static RollDetail d20(DiceRoller roller, RollMode mode) {
        return RollDetail.of(mode, roller.d20(mode));
    }

    /** The fumble is confirmed (consequences fire) when the second d20 rolls BELOW the DC. */
    private static boolean confirmFumble(DiceRoller roller, Rules.Fumbles fumbles) {
        if (!fumbles.confirmationRoll()) {
            return true;
        }
        int confirmation = roller.d20(RollMode.NORMAL).keptDice().getFirst().value();
        return confirmation < fumbles.confirmationDc();
    }

    private static int critDamage(DiceRoller roller, Rules.CritRule rule, DiceExpression dice) {
        return switch (rule) {
            case MAX_DICE -> dice.maxValue();
            case DOUBLE_DICE -> {
                RollResult first = roller.roll(dice);
                RollResult second = roller.roll(dice);
                yield first.total() + second.total() - second.modifier();
            }
            case DOUBLE_TOTAL -> 2 * roller.roll(dice).total();
        };
    }
}
