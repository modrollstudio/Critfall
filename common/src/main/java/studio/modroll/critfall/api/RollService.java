package studio.modroll.critfall.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.ContestResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.RollResult;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.combat.AttackPipeline;
import studio.modroll.critfall.combat.CombatEngine;
import studio.modroll.critfall.combat.DamageInterception;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.feedback.FeedbackBuilder;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.outcome.OutcomeExecutor;

/**
 * The public entry point for driving and observing Critfall combat (PLAN §4.4/§12). Other mods and
 * KubeJS scripts use this: roll dice, run a full attack, query effective profiles, suppress the
 * automatic pipeline for entities an orchestrator owns, and emit feedback. All rolls go through the
 * injectable combat roller — this class adds no randomness of its own.
 */
public final class RollService {

    private RollService() {}

    public static RollResult roll(String expression) {
        return RollRuntime.roller().roll(expression);
    }

    public static RollResult roll(DiceExpression expression) {
        return RollRuntime.roller().roll(expression);
    }

    public static DiceRoller roller() {
        return RollRuntime.roller();
    }

    public static DiceRoller feedbackRoller() {
        return RollRuntime.feedbackRoller();
    }

    /**
     * Deterministic-testing seam: forces exact die faces via a scripted roller. Test scope only —
     * rolls are server-authoritative, so this must not be left installed outside a test.
     */
    public static void setRoller(DiceRoller roller) {
        RollRuntime.setRoller(Objects.requireNonNull(roller, "roller"));
    }

    public static void resetRoller() {
        RollRuntime.resetRoller();
    }

    public static void suppress(Entity entity) {
        CombatSuppression.suppress(entity.getUUID());
    }

    public static void release(Entity entity) {
        CombatSuppression.release(entity.getUUID());
    }

    public static boolean isSuppressed(Entity entity) {
        return CombatSuppression.isSuppressed(entity.getUUID());
    }

    /**
     * Whether the hurt currently being applied to {@code target} is a Critfall-driven attack — the
     * damage from {@link #performAttack} (equivalently the internal {@code applyRolledDamage}), as
     * opposed to real-time vanilla or other-mod damage. Query it inside a loader damage listener to
     * exempt Critfall's own driven attacks from your own combat handling. False everywhere else.
     */
    public static boolean isDrivenDamage(LivingEntity target) {
        return DamageInterception.isDrivenApply(target);
    }

    public static EffectiveEntityProfile effectiveEntity(LivingEntity entity) {
        return EffectiveEntityProfile.of(
                ProfileLookup.forEntity(entity),
                entity.getAttributeValue(Attributes.ARMOR),
                entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                        ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        : 0.0);
    }

    public static EffectiveItemProfile effectiveItem(ItemStack stack) {
        return EffectiveItemProfile.of(ProfileLookup.forItem(stack), 0.0);
    }

    public static SaveResult savingThrow(LivingEntity target, int saveBonus, int dc) {
        return CombatEngine.resolveSave(RollRuntime.roller(), saveBonus, dc);
    }

    /** Resolves a contested check between two entities via {@link ContestContext}; see {@link ContestResult} for the tie rule. */
    public static ContestResult contest(LivingEntity initiator, LivingEntity opponent, ContestContext ctx) {
        return CombatEngine.resolveContest(
                RollRuntime.roller(),
                ctx.initiatorBonus(),
                ctx.initiatorMode(),
                ctx.opponentBonus(),
                ctx.opponentMode());
    }

    public static List<ConsequenceLine> fireOutcomes(
            LivingEntity attacker,
            LivingEntity target,
            AttackResult result,
            DiceExpression damageDice,
            ItemStack weapon) {
        return OutcomeExecutor.run(
                attacker,
                target,
                result,
                damageDice,
                RollRuntime.rules(),
                RollRuntime.roller(),
                weapon,
                ProfileLookup.forItem(weapon),
                ProfileLookup.forEntity(attacker));
    }

    public static void sendRollFeedback(LivingEntity attacker, LivingEntity target, RollFeedbackPayload payload) {
        FeedbackSink.get()
                .roll(attacker, target, payload, RollRuntime.rules().feedback().visibility());
    }

    /** Resolves an attack (fires the pre/post/crit/fumble events) WITHOUT applying damage or feedback. */
    public static AttackResult attackRoll(LivingEntity attacker, LivingEntity target, AttackContext ctx) {
        return resolve(attacker, target, ctx).result();
    }

    /**
     * Drives a full attack: rolls, applies damage, runs outcome tables, and emits feedback. Damage
     * is applied the same way as the automatic pipeline: the attack roll's AC already stood in for
     * armor, so vanilla armor reduction is bypassed (under {@code balance.
     * disable_vanilla_armor_reduction}) and the {@code hurt} never re-rolls. Callers orchestrating
     * an entity's combat should still {@link #suppress} the participants so OTHER real-time damage
     * stands down. Returns the resolved {@link AttackResult}.
     */
    public static AttackResult performAttack(LivingEntity attacker, LivingEntity target, AttackContext ctx) {
        AttackPipeline.Bundle bundle = resolve(attacker, target, ctx);
        AttackResult result = bundle.result();
        if (!bundle.apply()) {
            return result; // canceled/vetoed by a listener
        }
        Rules rules = RollRuntime.rules();
        if (result.isHit() && result.damage() > 0) {
            DamageInterception.applyRolledDamage(target, ctx.source(), result.damage());
        }
        boolean isKill = result.isHit() && !target.isAlive();
        RollFeedbackPayload payload = FeedbackBuilder.buildAttack(
                result,
                isKill,
                damageDice(attacker, ctx).toString(),
                rules.damageDice(),
                bundle.consequences(),
                ProfileLookup.forFlavor(ctx.weapon(), ctx.delivery()),
                rules,
                RollRuntime.feedbackRoller(),
                target.getUUID(),
                target.level().getGameTime());
        FeedbackSink.get().roll(attacker, target, payload, rules.feedback().visibility());
        return result;
    }

    private static AttackPipeline.Bundle resolve(LivingEntity attacker, LivingEntity target, AttackContext ctx) {
        EffectiveEntityProfile targetEff = effectiveEntity(target);
        EffectiveEntityProfile attackerEff = effectiveEntity(attacker);
        int attackBonus = ctx.attackBonusOverride().orElse(attackerEff.attackBonus());
        DiceExpression dice = damageDice(attacker, ctx);
        int critRange = critRange(attacker, ctx);
        return AttackPipeline.resolve(
                attacker,
                target,
                ctx,
                new AttackPipeline.Params(attackBonus, targetEff.armorClass(), dice, critRange, ctx.mode(), false),
                RollRuntime.rules(),
                RollRuntime.roller(),
                ctx.weapon(),
                ProfileLookup.forItem(ctx.weapon(), ctx.delivery()),
                ProfileLookup.forEntity(attacker));
    }

    /** The damage dice: explicit override, else item profile, else entity profile (by delivery), else derived. */
    private static DiceExpression damageDice(LivingEntity attacker, AttackContext ctx) {
        if (ctx.damageDiceOverride().isPresent()) {
            return ctx.damageDiceOverride().get();
        }
        Optional<ItemProfile> item = ProfileLookup.forItem(ctx.weapon(), ctx.delivery());
        double attackDamage = attacker.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : 0.0;
        // Empty entity so the resolver yields the ITEM dice only — we then apply the correct entity
        // dice by delivery (melee vs. ranged), which AttackDice.resolve does not distinguish.
        Optional<DiceExpression> itemDice = AttackDice.resolve(item, Optional.<EntityProfile>empty(), attackDamage)
                .map(AttackDice.Resolved::dice);
        EffectiveEntityProfile eff = effectiveEntity(attacker);
        Optional<DiceExpression> entityDice = ctx.isRanged() ? eff.rangedDamage() : eff.meleeDamage();
        return itemDice.or(() -> entityDice).orElseGet(() -> Derivation.damageDice(Math.max(1.0, attackDamage)));
    }

    private static int critRange(LivingEntity attacker, AttackContext ctx) {
        Optional<ItemProfile> item = ProfileLookup.forItem(ctx.weapon(), ctx.delivery());
        if (item.isPresent() && item.get().critRange().isPresent()) {
            return item.get().critRange().getAsInt();
        }
        return effectiveEntity(attacker).critRange();
    }
}
