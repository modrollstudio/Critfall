package studio.modroll.critfall.neoforge;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import studio.modroll.critfall.RollService;
import studio.modroll.critfall.combat.AttackDice;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.CombatEngine;
import studio.modroll.critfall.combat.CombatText;
import studio.modroll.critfall.combat.DamageCategory;
import studio.modroll.critfall.combat.DamageClassifier;
import studio.modroll.critfall.combat.Derivation;
import studio.modroll.critfall.combat.FumbleCooldowns;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.RollMode;
import studio.modroll.critfall.outcome.OutcomeExecutor;

/**
 * Intercepts {@code LivingIncomingDamageEvent} — the earliest point in NeoForge's damage sequence
 * (after invulnerability checks, before any mitigation) — and replaces vanilla damage with a d20
 * attack roll vs the target's AC. Stats come from datapack profiles (entity + held item) with
 * per-field fallback to attribute derivation. M3 scope: melee only; projectiles and spells pass
 * through until M5.
 */
public final class DamageEventHandler {

    private DamageEventHandler() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (OutcomeExecutor.isApplyingEffects()) {
            return; // damage dealt BY an outcome effect (redirected swing, self damage) never re-rolls
        }
        Rules rules = RollService.rules();
        if (!rules.attackRolls().enabled()) {
            return;
        }
        DamageSource source = event.getSource();
        if (DamageClassifier.classify(source) != DamageCategory.MELEE) {
            return; // M3: only direct melee is rolled; everything else stays vanilla
        }
        LivingEntity attacker = (LivingEntity) source.getEntity(); // MELEE guarantees a living attacker
        boolean playerAttacker = attacker instanceof Player;
        if (playerAttacker
                ? !rules.attackRolls().players()
                : !rules.attackRolls().mobs()) {
            return;
        }

        Optional<EntityProfile> targetProfile = ProfileLookup.forEntity(target);
        if (targetProfile.isEmpty() && rules.fallbacks().unknownEntity() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        }
        Optional<EntityProfile> attackerProfile = ProfileLookup.forEntity(attacker);
        Optional<ItemProfile> weaponProfile = ProfileLookup.forItem(attacker.getMainHandItem());

        double attackAttribute = attacker.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : event.getAmount();
        int attackBonus =
                intStat(attackerProfile.map(EntityProfile::attackBonus), () -> Derivation.attackBonus(attackAttribute));
        int armorClass = intStat(
                targetProfile.map(EntityProfile::armorClass),
                () -> Derivation.armorClass(
                        target.getAttributeValue(Attributes.ARMOR),
                        target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));

        DiceExpression damageDice;
        int critRange;
        Optional<AttackDice.Resolved> resolved = AttackDice.resolve(weaponProfile, attackerProfile, attackAttribute);
        if (resolved.isPresent()) {
            damageDice = resolved.get().dice();
            critRange = resolved.get().critRange();
        } else if (rules.fallbacks().unknownWeapon() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        } else {
            damageDice = Derivation.damageDice(event.getAmount());
            critRange = AttackDice.entityCritRange(attackerProfile);
        }

        long gameTime = target.level().getGameTime();
        boolean fumbleSuppressed = !fumbleAppliesTo(rules.fumbles().appliesTo(), playerAttacker)
                || FumbleCooldowns.isOnCooldown(
                        attacker.getUUID(), gameTime, rules.fumbles().cooldownTicks());

        AttackResult result = CombatEngine.resolveAttack(
                RollService.roller(),
                rules,
                new CombatEngine.AttackInput(
                        attackBonus, armorClass, RollMode.NORMAL, damageDice, critRange, fumbleSuppressed));

        switch (result.outcome()) {
            case MISS -> event.setCanceled(true);
            case FUMBLE -> {
                event.setCanceled(true);
                FumbleCooldowns.record(attacker.getUUID(), gameTime);
            }
            case HIT, CRIT -> {
                if (rules.damageDice()) {
                    float damage = result.damage();
                    damage *= targetProfile
                            .map(profile -> ProfileLookup.damageMultiplier(profile, source))
                            .orElse(1.0f);
                    damage *= (float) rules.balance().globalDamageMultiplier();
                    event.setAmount(damage);
                }
                if (rules.balance().disableVanillaArmorReduction()) {
                    // AC already represents armor — letting vanilla reduce again would double-dip.
                    event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0f);
                }
            }
        }
        // Runs for every outcome: fumble/crit tables fire on nat 1/nat 20, and pack-authored
        // tables may trigger on miss margins or roll ranges.
        OutcomeExecutor.run(
                attacker, target, result, damageDice, rules, RollService.roller(), weaponProfile, attackerProfile);
        sendFeedback(attacker, target, result, damageDice, rules.feedback(), rules.damageDice());
    }

    private static boolean fumbleAppliesTo(Rules.AppliesTo appliesTo, boolean playerAttacker) {
        return switch (appliesTo) {
            case PLAYERS -> playerAttacker;
            case MOBS -> !playerAttacker;
            case PLAYERS_AND_MOBS -> true;
        };
    }

    private static int intStat(Optional<OptionalInt> profileValue, java.util.function.IntSupplier derived) {
        return profileValue
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElseGet(derived::getAsInt);
    }

    private static void sendFeedback(
            LivingEntity attacker,
            LivingEntity target,
            AttackResult result,
            DiceExpression dice,
            Rules.FeedbackVisibility visibility,
            boolean showDamage) {
        if (visibility == Rules.FeedbackVisibility.OFF) {
            return;
        }
        String text = CombatText.describe(result, dice.toString(), showDamage);
        if (attacker instanceof Player player) {
            player.displayClientMessage(Component.literal(text), true);
        }
        if (visibility == Rules.FeedbackVisibility.EVERYONE && target instanceof Player player) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }
}
