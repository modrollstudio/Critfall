package studio.modroll.critfall.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.HazardAttack;
import studio.modroll.critfall.api.HazardSave;
import studio.modroll.critfall.api.combat.AttackOutcome;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.HazardSaveResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.combat.SuccessDamage;
import studio.modroll.critfall.api.dice.DiceRoller;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.event.HazardRollEvent;
import studio.modroll.critfall.api.feedback.CheckFeedbackPayload;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.feedback.FeedbackSink;

/**
 * Damage with no living attacker ({@link HazardSave}, {@link HazardAttack}), behind {@link
 * studio.modroll.critfall.api.RollService#performHazardSave} and {@code performHazardAttack}. The rolls use
 * the combat roller; the damage is applied like a driven attack ({@link
 * DamageInterception#applyRolledDamage}) after the target's resistances and the global multiplier, and the
 * readout goes to the target. Each fires {@link HazardRollEvent} before its d20, but no attack events or
 * outcome tables: there is no attacker.
 */
public final class Hazards {

    private Hazards() {}

    /** The target saves (provider modifier, else {@code ownSaveBonus}); a fail takes the dice, a success half or none. */
    public static HazardSaveResult save(LivingEntity target, HazardSave hazard, int ownSaveBonus) {
        Rules rules = RollRuntime.rules();
        DiceRoller roller = RollRuntime.roller();
        DamageSource source = source(target, hazard.damageType());
        int saveBonus = Modifiers.saveBonus(rules, target, hazard.saveKey(), ownSaveBonus);
        RollMode mode = CritfallEvents.fireHazardRoll(new HazardRollEvent(target, hazard))
                .mode();
        SaveResult save = CombatEngine.resolveSave(roller, saveBonus, hazard.dc(), mode);

        float damage = 0;
        if (!save.saved() || hazard.onSuccess() == SuccessDamage.HALF) {
            // A failed save always hurts; the floor comes before resistances, so immunity still stands.
            damage = Math.max(
                            CombatEngine.MIN_HIT_DAMAGE,
                            roller.roll(hazard.damage()).total())
                    * multiplier(rules, target, source);
            if (save.saved()) {
                damage = (float) Math.floor(damage / 2);
            }
        }
        apply(target, source, damage);
        CheckFeedbackPayload payload = new CheckFeedbackPayload(
                CheckFeedbackPayload.Kind.SAVE,
                hazard.label(),
                save.natural(),
                save.saveTotal(),
                save.dc(),
                save.saved(),
                false,
                save.roll().mode(),
                save.roll().dropped(),
                hazard.damage().toString(),
                (int) damage);
        FeedbackSink.get().check(target, payload, rules.feedback().visibility());
        return new HazardSaveResult(save, (int) damage);
    }

    /** The hazard rolls against the target's AC ({@code ownArmorClass} plus the provider's modifier). */
    public static AttackResult attack(LivingEntity target, HazardAttack hazard, int ownArmorClass) {
        Rules rules = RollRuntime.rules();
        DamageSource source = source(target, hazard.damageType());
        int armorClass = Modifiers.armorClass(rules, target, null, ownArmorClass);
        RollMode mode = CritfallEvents.fireHazardRoll(new HazardRollEvent(target, hazard))
                .mode();
        // A hazard has no vanilla amount to fall back on, so it rolls its dice even with damage_dice off;
        // a natural 1 just misses: a trap cannot fumble.
        CombatEngine.AttackInput input =
                new CombatEngine.AttackInput(hazard.attackBonus(), armorClass, mode, hazard.damage(), 20, true, 0);
        AttackResult rolled = CombatEngine.resolveAttack(RollRuntime.roller(), withDamageDice(rules), input);

        float damage = rolled.isHit() ? rolled.damage() * multiplier(rules, target, source) : 0;
        AttackResult result = rolled.withDamage((int) damage);
        apply(target, source, damage);
        CheckFeedbackPayload payload = new CheckFeedbackPayload(
                CheckFeedbackPayload.Kind.ATTACK,
                hazard.label(),
                result.natural(),
                result.attackTotal(),
                result.armorClass(),
                result.isHit(),
                result.outcome() == AttackOutcome.CRIT,
                result.roll().mode(),
                result.roll().dropped(),
                hazard.damage().toString(),
                result.damage());
        FeedbackSink.get().check(target, payload, rules.feedback().visibility());
        return result;
    }

    private static DamageSource source(LivingEntity target, ResourceKey<DamageType> type) {
        return new DamageSource(target.level()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type));
    }

    /** The target profile's resist/immune/vulnerable multiplier for the source, times the global multiplier. */
    private static float multiplier(Rules rules, LivingEntity target, DamageSource source) {
        float profile = ProfileLookup.forEntity(target)
                .map(p -> ProfileLookup.damageMultiplier(p, source))
                .orElse(1.0f);
        return profile * (float) rules.balance().globalDamageMultiplier();
    }

    private static void apply(LivingEntity target, DamageSource source, float damage) {
        if (damage > 0) {
            DamageInterception.applyRolledDamage(target, source, damage);
        }
    }

    private static Rules withDamageDice(Rules r) {
        return r.damageDice()
                ? r
                : new Rules(
                        r.attackRolls(),
                        true,
                        r.crits(),
                        r.fumbles(),
                        r.spells(),
                        r.fallbacks(),
                        r.feedback(),
                        r.balance(),
                        r.dryRun(),
                        r.modifierProviders(),
                        r.advantageSources());
    }
}
