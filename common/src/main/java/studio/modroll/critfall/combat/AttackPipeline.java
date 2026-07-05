package studio.modroll.critfall.combat;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.event.PostAttackRollEvent;
import studio.modroll.critfall.api.event.PreAttackRollEvent;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.dice.RollMode;
import studio.modroll.critfall.feedback.ConsequenceLine;
import studio.modroll.critfall.outcome.OutcomeExecutor;

/**
 * The single, loader-agnostic attack-resolution path (PLAN §2.5 "API-first"): fire
 * {@link PreAttackRollEvent}, roll via {@link CombatEngine}, fire {@link PostAttackRollEvent} plus
 * the crit/fumble events, then run outcome tables. Both the automatic {@code DamageEventHandler} and
 * {@link studio.modroll.critfall.api.RollService#performAttack} call this, so listeners see every attack
 * and the two paths cannot drift. The caller applies the resulting damage (event mutation on the
 * automatic path, {@code hurt} on the API path) and sends feedback.
 */
public final class AttackPipeline {

    private AttackPipeline() {}

    /** The resolved to-hit/damage inputs. {@code mode} is the base roll mode before any listener override. */
    public record Params(
            int attackBonus,
            int armorClass,
            DiceExpression damageDice,
            int critRange,
            RollMode mode,
            boolean fumbleSuppressed) {

        CombatEngine.AttackInput toInput(int bonus, RollMode rollMode) {
            return new CombatEngine.AttackInput(bonus, armorClass, rollMode, damageDice, critRange, fumbleSuppressed);
        }
    }

    /**
     * @param apply false when a listener canceled the pre-event or vetoed the post-event — the caller
     *     then applies no damage and runs no feedback; {@code result} still carries what was resolved
     *     (a MISS placeholder for a pre-cancel)
     */
    public record Bundle(AttackResult result, List<ConsequenceLine> consequences, boolean apply) {}

    public static Bundle resolve(
            LivingEntity attacker,
            LivingEntity target,
            AttackContext ctx,
            Params params,
            Rules rules,
            DiceRoller roller,
            ItemStack weapon,
            Optional<ItemProfile> weaponProfile,
            Optional<EntityProfile> attackerProfile) {

        PreAttackRollEvent pre =
                CritfallEvents.firePreAttackRoll(attacker, target, ctx, params.attackBonus(), params.mode());
        if (pre.isCanceled()) {
            AttackResult canceled = new AttackResult(AttackOutcome.MISS, 0, 0, params.armorClass(), 0);
            return new Bundle(canceled, List.of(), false);
        }

        AttackResult result = CombatEngine.resolveAttack(roller, rules, params.toInput(pre.attackBonus(), pre.mode()));

        PostAttackRollEvent post = CritfallEvents.firePostAttackRoll(attacker, target, ctx, result);
        if (post.isVetoed()) {
            return new Bundle(result, List.of(), false);
        }
        result = result.withDamage(Math.max(0, post.finalDamage()));

        if (result.outcome() == AttackOutcome.CRIT) {
            CritfallEvents.fireCrit(attacker, target, ctx, result);
        } else if (result.outcome() == AttackOutcome.FUMBLE) {
            CritfallEvents.fireFumble(attacker, target, ctx, result);
        }

        List<ConsequenceLine> consequences = OutcomeExecutor.run(
                attacker, target, result, params.damageDice(), rules, roller, weapon, weaponProfile, attackerProfile);
        return new Bundle(result, consequences, true);
    }
}
