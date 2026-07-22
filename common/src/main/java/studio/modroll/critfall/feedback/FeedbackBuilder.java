package studio.modroll.critfall.feedback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.FlavorPool;

/**
 * Builds the S2C feedback payloads and owns the server-authoritative flavor anti-spam gate
 * (PLAN §4.5): flavor only on crit/fumble/kill; nat 20 crit and nat 1 fumble are priority and always
 * fire (bypassing and resetting the per-target cooldown); a non-crit kill fires only when the target
 * is off cooldown. Pure — the caller resolves the matching {@link FlavorPool} and passes the target's
 * UUID and the level game time.
 */
public final class FeedbackBuilder {

    private FeedbackBuilder() {}

    public static RollFeedbackPayload buildAttack(
            AttackResult result,
            boolean isKill,
            String diceNotation,
            boolean showDamage,
            List<ConsequenceLine> consequences,
            Optional<FlavorPool> pool,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime) {
        Optional<String> flavor = pickFlavor(result.outcome(), isKill, pool, rules, roller, targetId, gameTime);
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
                result.defenderAcBonus());
    }

    public static SaveFeedbackPayload buildSave(
            SaveResult save,
            boolean isKill,
            Rules.SaveOutcome onSuccess,
            String diceNotation,
            int damage,
            boolean showDamage,
            Optional<FlavorPool> pool,
            Rules rules,
            DiceRoller roller,
            UUID targetId,
            long gameTime) {
        // Saves have no crit/fumble; only a kill produces flavor, and it is never priority.
        Optional<String> flavor = Optional.empty();
        if (isKill && rules.feedback().flavor().enabled()) {
            int cooldown = rules.feedback().flavor().cooldownTicks();
            if (!FlavorCooldowns.isOnCooldown(targetId, gameTime, cooldown)) {
                flavor = FlavorSelector.pick(pool, FlavorPool.KILL, roller);
                if (flavor.isPresent()) {
                    FlavorCooldowns.record(targetId, gameTime, cooldown);
                }
            }
        }
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
                save.roll().dropped());
    }

    private static Optional<String> pickFlavor(
            AttackOutcome outcome,
            boolean isKill,
            Optional<FlavorPool> pool,
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
            return record(FlavorSelector.pick(pool, FlavorPool.CRIT, roller), targetId, gameTime, cooldown);
        }
        if (outcome == AttackOutcome.FUMBLE) {
            return record(FlavorSelector.pick(pool, FlavorPool.FUMBLE, roller), targetId, gameTime, cooldown);
        }
        // A non-crit kill is low priority: gated by the per-target cooldown.
        if (isKill && (outcome == AttackOutcome.HIT)) {
            if (FlavorCooldowns.isOnCooldown(targetId, gameTime, cooldown)) {
                return Optional.empty();
            }
            return record(FlavorSelector.pick(pool, FlavorPool.KILL, roller), targetId, gameTime, cooldown);
        }
        return Optional.empty();
    }

    private static Optional<String> record(Optional<String> flavor, UUID targetId, long gameTime, int cooldownTicks) {
        if (flavor.isPresent()) {
            FlavorCooldowns.record(targetId, gameTime, cooldownTicks);
        }
        return flavor;
    }
}
