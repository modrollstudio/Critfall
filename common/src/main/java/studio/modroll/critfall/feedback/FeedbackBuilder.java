package studio.modroll.critfall.feedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.FlavorName;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.FlavorLines;
import studio.modroll.critfall.data.FlavorPool;

/**
 * Builds the S2C feedback payloads and owns the server-authoritative flavor anti-spam gate
 * (PLAN §4.5): flavor only on crit/fumble/kill; nat 20 crit and nat 1 fumble are priority and always
 * fire (bypassing and resetting the per-target cooldown); a non-crit kill fires only when the target
 * is off cooldown, and then always for a named mob or a boss ({@link FlavorKills}) but only at
 * {@code feedback.flavor.kill_chance_percent} for any other kill. Pure — the caller resolves the {@link FlavorLines} and the names for the line's
 * placeholders, and passes the target's UUID and the level game time.
 */
public final class FeedbackBuilder {

    private FeedbackBuilder() {}

    public static RollFeedbackPayload buildAttack(
            AttackResult result,
            boolean isKill,
            String diceNotation,
            boolean showDamage,
            List<ConsequenceLine> consequences,
            FlavorLines flavorLines,
            FlavorName attacker,
            FlavorName target,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime) {
        return buildAttack(
                result,
                isKill,
                diceNotation,
                showDamage,
                consequences,
                flavorLines,
                attacker,
                target,
                rules,
                roller,
                targetId,
                gameTime,
                1.0F,
                false);
    }

    /**
     * @param critMultiplier the crit multiplier the hit's dice damage carried, 1 for none; shown only
     *     with the damage, so a miss or damage dice off never shows it
     * @param alwaysFlavorKill a kill always gets a line, whatever the kill chance: the target was a
     *     named mob or a boss ({@link FlavorKills#alwaysFlavored})
     */
    public static RollFeedbackPayload buildAttack(
            AttackResult result,
            boolean isKill,
            String diceNotation,
            boolean showDamage,
            List<ConsequenceLine> consequences,
            FlavorLines flavorLines,
            FlavorName attacker,
            FlavorName target,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime,
            float critMultiplier,
            boolean alwaysFlavorKill) {
        Optional<String> flavor =
                pickFlavor(result.outcome(), isKill, alwaysFlavorKill, flavorLines, rules, roller, targetId, gameTime);
        return new RollFeedbackPayload(
                result.outcome(),
                result.natural(),
                result.attackTotal(),
                result.armorClass(),
                result.damage(),
                diceNotation,
                showDamage,
                flavor,
                List.copyOf(consequences),
                rules.dryRun().enabled(),
                result.roll().mode(),
                result.roll().dropped(),
                result.defenderAcBonus(),
                flavor.isPresent() ? attacker : FlavorName.NONE,
                flavor.isPresent() ? target : FlavorName.NONE,
                result.isHit() && showDamage ? critMultiplier : 1.0F);
    }

    public static SaveFeedbackPayload buildSave(
            SaveResult save,
            boolean isKill,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            FlavorLines flavorLines,
            FlavorName attacker,
            FlavorName target,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime) {
        return buildSave(
                save,
                isKill,
                onSuccess,
                diceNotation,
                damage,
                showDamage,
                flavorLines,
                attacker,
                target,
                rules,
                roller,
                targetId,
                gameTime,
                false);
    }

    /** @param alwaysFlavorKill as for {@link #buildAttack}: a kill always gets a line */
    public static SaveFeedbackPayload buildSave(
            SaveResult save,
            boolean isKill,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            FlavorLines flavorLines,
            FlavorName attacker,
            FlavorName target,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime,
            boolean alwaysFlavorKill) {
        // Saves have no crit/fumble; only a kill produces flavor, and it is never priority.
        Optional<String> flavor = isKill && rules.feedback().flavor().enabled()
                ? killLine(flavorLines, rules, roller, targetId, gameTime, alwaysFlavorKill)
                : Optional.empty();
        return new SaveFeedbackPayload(
                save.natural(),
                save.saveTotal(),
                save.dc(),
                save.saved(),
                onSuccess,
                diceNotation,
                damage,
                showDamage,
                flavor,
                rules.dryRun().enabled(),
                save.roll().mode(),
                save.roll().dropped(),
                flavor.isPresent() ? attacker : FlavorName.NONE,
                flavor.isPresent() ? target : FlavorName.NONE);
    }

    private static Optional<String> pickFlavor(
            AttackOutcome outcome,
            boolean isKill,
            boolean alwaysFlavorKill,
            FlavorLines flavorLines,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime) {
        if (!rules.feedback().flavor().enabled()) {
            return Optional.empty();
        }
        int cooldown = rules.feedback().flavor().cooldownTicks();
        // Priority lines: nat 20 crit and nat 1 fumble always show and reset the cooldown.
        if (outcome == AttackOutcome.CRIT) {
            return record(FlavorSelector.pick(flavorLines, FlavorPool.CRIT, roller), targetId, gameTime, cooldown);
        }
        if (outcome == AttackOutcome.FUMBLE) {
            return record(FlavorSelector.pick(flavorLines, FlavorPool.FUMBLE, roller), targetId, gameTime, cooldown);
        }
        if (isKill && (outcome == AttackOutcome.HIT)) {
            return killLine(flavorLines, rules, roller, targetId, gameTime, alwaysFlavorKill);
        }
        return Optional.empty();
    }

    /**
     * A non-crit kill is low priority: gated by the per-target cooldown, then shown always for a named
     * mob or a boss and at the kill chance for any other. The chance draws a d100 from the cosmetic
     * {@code roller} only when it is neither 0 nor 100, before the line itself is picked.
     */
    private static Optional<String> killLine(
            FlavorLines flavorLines,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime,
            boolean alwaysFlavorKill) {
        Rules.Feedback.Flavor flavor = rules.feedback().flavor();
        if (FlavorCooldowns.isOnCooldown(targetId, gameTime, flavor.cooldownTicks())) {
            return Optional.empty();
        }
        if (!alwaysFlavorKill && !chance(flavor.killChancePercent(), roller)) {
            return Optional.empty();
        }
        return record(
                FlavorSelector.pick(flavorLines, FlavorPool.KILL, roller), targetId, gameTime, flavor.cooldownTicks());
    }

    private static boolean chance(int percent, DiceRoller roller) {
        return percent >= 100 || (percent > 0 && roller.die(100) <= percent);
    }

    private static Optional<String> record(Optional<String> flavor, UUID targetId, long gameTime, int cooldownTicks) {
        if (flavor.isPresent()) {
            FlavorCooldowns.record(targetId, gameTime, cooldownTicks);
        }
        return flavor;
    }
}
