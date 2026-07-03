package studio.modroll.critfall.outcome;

import studio.modroll.critfall.combat.AttackOutcome;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.OutcomeEffect;
import studio.modroll.critfall.data.OutcomeTable;
import studio.modroll.critfall.data.Trigger;
import studio.modroll.critfall.dice.DiceRoller;

/**
 * The pure half of the outcome-table executor: does a trigger fire for this roll, which weighted
 * effect is picked, and is that effect enabled in rules.json. No Minecraft world access, so every
 * decision is unit-testable; {@link OutcomeExecutor} applies the picked effect to live entities.
 */
public final class OutcomeSelector {

    private OutcomeSelector() {}

    /**
     * Whether {@code trigger} fires for this resolved attack. {@code nat_1} fires only on a
     * confirmed {@link AttackOutcome#FUMBLE} (the confirmation-roll and cooldown safeguards have
     * already spoken) and {@code nat_20} only on a {@link AttackOutcome#CRIT} — so disabling
     * fumbles or crits in rules.json silences those tables wholesale. Miss margins count from the
     * attack total, and roll ranges match the natural die on any outcome.
     */
    public static boolean triggers(Trigger trigger, AttackResult result) {
        return switch (trigger) {
            case Trigger.Natural natural ->
                result.natural() == natural.face()
                        && (natural.face() != 1 || result.outcome() == AttackOutcome.FUMBLE)
                        && (natural.face() != 20 || result.outcome() == AttackOutcome.CRIT);
            case Trigger.MissByAtLeast miss ->
                !result.isHit() && result.armorClass() - result.attackTotal() >= miss.margin();
            case Trigger.RollRange range -> result.natural() >= range.min() && result.natural() <= range.max();
        };
    }

    /**
     * Picks one effect from the table's weighted list. A single-entry table draws nothing from
     * the RNG; otherwise one die of {@code totalWeight} sides decides (face n lands in the entry
     * whose cumulative weight first reaches n).
     */
    public static OutcomeEffect pick(OutcomeTable table, DiceRoller roller) {
        if (table.effects().size() == 1) {
            return table.effects().getFirst().effect();
        }
        int face = roller.die(table.totalWeight());
        int cumulative = 0;
        for (OutcomeTable.WeightedEffect candidate : table.effects()) {
            cumulative += candidate.weight();
            if (face <= cumulative) {
                return candidate.effect();
            }
        }
        throw new IllegalStateException("weighted pick " + face + " exceeded total weight " + table.totalWeight());
    }

    /**
     * The rules.json gate for each effect type. A disabled effect is a no-op when picked — it is
     * NOT filtered out beforehand, so turning one consequence off never changes the odds of the
     * others — a flag off restores vanilla for that mechanic only.
     */
    public static boolean enabled(OutcomeEffect effect, Rules rules) {
        return switch (effect) {
            case OutcomeEffect.Nothing ignored -> true;
            case OutcomeEffect.DamageDurability ignored -> rules.fumbles().durabilityBreak();
            case OutcomeEffect.HitNearestAlly ignored ->
                rules.fumbles().hitNearestAlly().enabled();
            case OutcomeEffect.SelfDamage ignored ->
                rules.fumbles().selfDamage().enabled();
            case OutcomeEffect.DropWeapon ignored -> rules.fumbles().dropWeapon();
            case OutcomeEffect.Stumble ignored -> rules.fumbles().stumble().enabled();
            case OutcomeEffect.ApplyEffect ignored -> rules.crits().applyEffect();
            case OutcomeEffect.Knockback ignored -> rules.crits().knockback();
        };
    }
}
