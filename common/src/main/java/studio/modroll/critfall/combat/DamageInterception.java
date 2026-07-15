package studio.modroll.critfall.combat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import studio.modroll.critfall.RollRuntime;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.AttackDelivery;
import studio.modroll.critfall.api.CombatSuppression;
import studio.modroll.critfall.api.combat.AttackResult;
import studio.modroll.critfall.api.combat.SaveResult;
import studio.modroll.critfall.api.dice.DiceExpression;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.event.CritfallEvents;
import studio.modroll.critfall.api.feedback.ConsequenceLine;
import studio.modroll.critfall.api.feedback.RollFeedbackPayload;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.ProfileLookup;
import studio.modroll.critfall.data.SpellProfile;
import studio.modroll.critfall.feedback.FeedbackBuilder;
import studio.modroll.critfall.feedback.FeedbackSink;
import studio.modroll.critfall.feedback.SaveFeedbackPayload;
import studio.modroll.critfall.outcome.OutcomeExecutor;

/**
 * Loader-agnostic damage interception (extracted from the NeoForge handler in M8). Runs at the
 * earliest point in each loader's damage sequence (after invulnerability, before mitigation) and
 * replaces vanilla damage with a d20 attack roll vs the target's AC. Stats come from datapack
 * profiles (entity + item + spell) with per-field fallback to attribute derivation. Scope: melee,
 * projectiles (rolled on impact from the launcher/ammo profiles), and spell-classified damage
 * (attack roll or saving throw per spell profile). Exempt/environmental damage passes through
 * vanilla. Each loader adapts its damage event to the {@link IncomingDamage} seam.
 */
public final class DamageInterception {

    private DamageInterception() {}

    /** The target of an in-flight {@link #applyRolledDamage} hurt on this thread, else null. */
    private static final ThreadLocal<LivingEntity> ROLLED_APPLY = new ThreadLocal<>();

    /**
     * Applies damage an API-driven attack already resolved (PLAN §12): the {@code hurt} passes
     * through this interception without re-rolling, and — under the same
     * {@code balance.disable_vanilla_armor_reduction} flag as the automatic path — with vanilla
     * armor reduction bypassed, because the attack roll's AC already stood in for armor. Only the
     * hurt on {@code target} is scoped; recoil damage it triggers (thorns) is intercepted normally.
     *
     * @return what {@code LivingEntity.hurt} returned
     */
    public static boolean applyRolledDamage(LivingEntity target, DamageSource source, float amount) {
        LivingEntity previous = ROLLED_APPLY.get();
        ROLLED_APPLY.set(target);
        try {
            return target.hurt(source, amount);
        } finally {
            if (previous == null) {
                ROLLED_APPLY.remove();
            } else {
                ROLLED_APPLY.set(previous);
            }
        }
    }

    public static void handle(IncomingDamage dmg, LivingEntity target, DamageSource source) {
        if (target.level().isClientSide()) {
            return;
        }
        if (ROLLED_APPLY.get() == target) {
            // Damage applied by RollService.performAttack: already rolled, AC stood in for armor —
            // bypass vanilla reduction (same §9 decision as the automatic path) and never re-roll.
            if (RollRuntime.rules().balance().disableVanillaArmorReduction()) {
                dmg.bypassArmor();
            }
            return;
        }
        if (OutcomeExecutor.isApplyingEffects()) {
            return; // damage dealt BY an outcome effect (redirected swing, self damage) never re-rolls
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            // Before all gating below: docs/api.md promises this fires even for suppressed,
            // exempt, or cancelled damage.
            CritfallEvents.fireCombatInteraction(attacker, target, source, interactionDelivery(source));
        }
        if (isSuppressed(source, target)) {
            return; // an orchestrator (§12) owns this entity's combat — auto pipeline stands down
        }
        Rules rules = RollRuntime.rules();
        if (!rules.attackRolls().enabled()) {
            return;
        }
        if (rules.dryRun().enabled()) {
            // Compute and report the roll, but no mutator lands — the vanilla damage stands.
            dmg = new DryRunDamage(dmg);
        }
        switch (DamageClassifier.classify(source)) {
            case MELEE -> rollMelee(dmg, rules, source, target);
            case PROJECTILE -> rollProjectile(dmg, rules, source, target);
            case SPELL -> rollSpell(dmg, rules, source, target);
            case EXEMPT, ALWAYS_HITS, ENVIRONMENTAL -> {} // vanilla passthrough
        }
    }

    /** Classified like the roll pipeline; best-effort for damage it would never roll. */
    private static AttackDelivery interactionDelivery(DamageSource source) {
        if (source.is(CritfallTags.SPELL)) {
            return AttackDelivery.SPELL;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return projectileDelivery(source, launcherStack(source));
        }
        return source.isDirect() ? AttackDelivery.MELEE : AttackDelivery.SPELL;
    }

    /** True when the target or the (living) attacker is externally suppressed (PLAN §12). */
    private static boolean isSuppressed(DamageSource source, LivingEntity target) {
        if (CombatSuppression.isSuppressed(target.getUUID())) {
            return true;
        }
        return source.getEntity() != null
                && CombatSuppression.isSuppressed(source.getEntity().getUUID());
    }

    private static void rollMelee(IncomingDamage dmg, Rules rules, DamageSource source, LivingEntity target) {
        LivingEntity attacker = (LivingEntity) source.getEntity(); // MELEE guarantees a living attacker
        if (!attackerRollsEnabled(rules, attacker)) {
            return;
        }
        Optional<EntityProfile> targetProfile = ProfileLookup.forEntity(target);
        if (targetProfile.isEmpty() && rules.fallbacks().unknownEntity() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        }
        Optional<EntityProfile> attackerProfile = ProfileLookup.forEntity(attacker);
        Optional<ItemProfile> weaponProfile = ProfileLookup.forItem(attacker.getMainHandItem(), AttackDelivery.MELEE);

        double attackAttribute = attacker.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : dmg.amount();
        int attackBonus =
                intStat(attackerProfile.map(EntityProfile::attackBonus), () -> Derivation.attackBonus(attackAttribute));

        DiceExpression damageDice;
        int critRange;
        Optional<AttackDice.Resolved> resolved = AttackDice.resolve(weaponProfile, attackerProfile, attackAttribute);
        if (resolved.isPresent()) {
            damageDice = resolved.get().dice();
            critRange = resolved.get().critRange();
        } else if (rules.fallbacks().unknownWeapon() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        } else {
            damageDice = Derivation.damageDice(dmg.amount());
            critRange = AttackDice.entityCritRange(attackerProfile);
        }

        rollAndApply(
                dmg,
                rules,
                source,
                attacker,
                target,
                targetProfile,
                attackerProfile,
                weaponProfile,
                attacker.getMainHandItem(),
                attacker.getMainHandItem(),
                AttackDelivery.MELEE,
                attackBonus,
                damageDice,
                critRange);
    }

    private static void rollProjectile(IncomingDamage dmg, Rules rules, DamageSource source, LivingEntity target) {
        if (!rules.attackRolls().projectiles()) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return; // dispenser/casterless projectiles stay vanilla — nobody to roll for
        }
        if (!attackerRollsEnabled(rules, attacker)) {
            return;
        }
        if (dmg.amount() <= 0) {
            return; // snowballs and eggs deal 0 — rolling dice would invent damage from nothing
        }
        Optional<EntityProfile> targetProfile = ProfileLookup.forEntity(target);
        if (targetProfile.isEmpty() && rules.fallbacks().unknownEntity() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        }
        Optional<EntityProfile> attackerProfile = ProfileLookup.forEntity(attacker);
        ItemStack launcher = launcherStack(source);
        AttackDelivery delivery = projectileDelivery(source, launcher);
        Optional<ItemProfile> weaponProfile = ProfileLookup.forItem(launcher, delivery);
        int attackBonus =
                intStat(attackerProfile.map(EntityProfile::attackBonus), () -> Derivation.attackBonus(dmg.amount()));

        DiceExpression damageDice;
        int critRange;
        Optional<AttackDice.Resolved> resolved = AttackDice.resolveRanged(
                weaponProfile, ammoDice(source, launcher, delivery), attackerProfile, dmg.amount());
        if (resolved.isPresent()) {
            damageDice = resolved.get().dice();
            critRange = resolved.get().critRange();
        } else if (rules.fallbacks().unknownWeapon() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        } else {
            damageDice = Derivation.damageDice(dmg.amount());
            critRange = AttackDice.entityCritRange(attackerProfile);
        }

        rollAndApply(
                dmg,
                rules,
                source,
                attacker,
                target,
                targetProfile,
                attackerProfile,
                weaponProfile,
                launcher,
                heldLauncher(attacker, launcher),
                delivery,
                attackBonus,
                damageDice,
                critRange);
    }

    /**
     * THROWN when the projectile IS its own launcher — the weapon recorded on it is the item that
     * flew (a trident or any modded throwing weapon following the same pattern), or a
     * snowball-style throwable. Everything else (arrow from a bow, item-less projectiles) is
     * PROJECTILE.
     */
    private static AttackDelivery projectileDelivery(DamageSource source, ItemStack launcher) {
        if (source.getDirectEntity() instanceof ThrowableItemProjectile) {
            return AttackDelivery.THROWN;
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow
                && !launcher.isEmpty()
                && arrow.getPickupItemStackOrigin().is(launcher.getItem())) {
            return AttackDelivery.THROWN;
        }
        return AttackDelivery.PROJECTILE;
    }

    /**
     * The item this projectile was launched with: the firing weapon recorded on the projectile
     * (bow/crossbow for arrows, the trident itself for thrown tridents), or the thrown item for
     * snowball-style projectiles. Empty for item-less projectiles (ghast fireballs).
     */
    private static ItemStack launcherStack(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (weapon != null && !weapon.isEmpty()) {
            return weapon;
        }
        return source.getDirectEntity() instanceof ThrowableItemProjectile thrown ? thrown.getItem() : ItemStack.EMPTY;
    }

    /** Extra dice an arrow with its own item profile adds on top of the launcher's dice. */
    private static Optional<DiceExpression> ammoDice(DamageSource source, ItemStack launcher, AttackDelivery delivery) {
        if (!(source.getDirectEntity() instanceof AbstractArrow arrow)) {
            return Optional.empty();
        }
        ItemStack ammo = arrow.getPickupItemStackOrigin();
        if (ammo == launcher) {
            return Optional.empty(); // a thrown trident is its own launcher, not ammunition
        }
        return ProfileLookup.forItem(ammo, delivery).flatMap(ItemProfile::damage);
    }

    /**
     * The held stack fumble weapon-effects act on. The projectile carries a COPY of the firing
     * weapon, so damaging that would be a no-op — find the matching stack still in the shooter's
     * hands (either hand; skeletons and players may hold the bow anywhere). Empty when it is gone
     * (a thrown trident), which makes weapon effects no-ops.
     */
    private static ItemStack heldLauncher(LivingEntity attacker, ItemStack launcher) {
        if (launcher.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack main = attacker.getMainHandItem();
        if (main.is(launcher.getItem())) {
            return main;
        }
        ItemStack off = attacker.getOffhandItem();
        if (off.is(launcher.getItem())) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static void rollSpell(IncomingDamage dmg, Rules rules, DamageSource source, LivingEntity target) {
        if (!rules.attackRolls().spells()) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return; // casterless magic (DamageUtil.source with no entity) stays vanilla
        }
        if (!attackerRollsEnabled(rules, attacker)) {
            return;
        }
        if (dmg.amount() <= 0) {
            return;
        }
        Optional<EntityProfile> targetProfile = ProfileLookup.forEntity(target);
        if (targetProfile.isEmpty() && rules.fallbacks().unknownEntity() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        }
        Optional<SpellProfile> spellProfile = ProfileLookup.forSpell(source);
        if (spellProfile.isEmpty() && rules.fallbacks().unknownSpell() == Rules.FallbackMode.VANILLA_PASSTHROUGH) {
            return;
        }

        boolean save = spellProfile.map(SpellProfile::resolution).orElse(SpellProfile.Resolution.ATTACK_ROLL)
                        == SpellProfile.Resolution.SAVE
                && rules.spells().saves().enabled();
        if (save) {
            applySave(dmg, rules, source, attacker, target, targetProfile, spellProfile.orElseThrow());
            return;
        }

        Optional<EntityProfile> attackerProfile = ProfileLookup.forEntity(attacker);
        // The caster's attack-damage attribute says nothing about spell power — the vanilla spell
        // damage does, so absent profile values derive from it.
        int attackBonus = intStat(
                spellProfile.map(SpellProfile::attackBonus),
                () -> intStat(
                        attackerProfile.map(EntityProfile::attackBonus), () -> Derivation.attackBonus(dmg.amount())));
        DiceExpression damageDice =
                spellProfile.flatMap(SpellProfile::damage).orElseGet(() -> Derivation.damageDice(dmg.amount()));
        int critRange =
                intStat(spellProfile.map(SpellProfile::critRange), () -> AttackDice.entityCritRange(attackerProfile));

        // The held item (a wand or staff) contributes its outcome tables only — spell dice always
        // come from the spell profile or derivation above.
        ItemStack held = attacker.getMainHandItem();
        rollAndApply(
                dmg,
                rules,
                source,
                attacker,
                target,
                targetProfile,
                attackerProfile,
                ProfileLookup.forItem(held, AttackDelivery.SPELL),
                held,
                held,
                AttackDelivery.SPELL,
                attackBonus,
                damageDice,
                critRange);
    }

    /**
     * The saving-throw path (PLAN.md §4.2): the TARGET rolls d20 + save bonus vs the profile's DC.
     * Failure takes the full damage (profile dice when present, the vanilla amount otherwise);
     * success takes half (rounded down) or nothing per {@code on_success}. No crits, fumbles, or
     * outcome tables — saves have none.
     */
    private static void applySave(
            IncomingDamage dmg,
            Rules rules,
            DamageSource source,
            LivingEntity attacker,
            LivingEntity target,
            Optional<EntityProfile> targetProfile,
            SpellProfile profile) {
        Rules.SpellSaves saves = rules.spells().saves();
        int dc = profile.saveDc().orElse(saves.defaultDc());
        Rules.SaveOutcome onSuccess = profile.onSuccess().orElse(saves.onSuccess());
        int saveBonus = intStat(targetProfile.map(EntityProfile::saveBonus), () -> 0);
        SaveResult save = CombatEngine.resolveSave(RollRuntime.roller(), saveBonus, dc);

        boolean useDice = rules.damageDice() && profile.damage().isPresent();
        float damage;
        if (useDice) {
            damage = Math.max(
                    0, RollRuntime.roller().roll(profile.damage().get()).total());
            damage *= targetProfile
                    .map(p -> ProfileLookup.damageMultiplier(p, source))
                    .orElse(1.0f);
            damage *= (float) rules.balance().globalDamageMultiplier();
        } else {
            damage = dmg.amount();
        }
        if (save.saved()) {
            damage = onSuccess == Rules.SaveOutcome.NEGATE ? 0 : (float) Math.floor(damage / 2);
        }

        if (damage <= 0) {
            dmg.cancel();
        } else {
            dmg.setAmount(damage);
            if (rules.balance().disableVanillaArmorReduction()) {
                // Same reasoning as attack rolls: tabletop damage is not armor-reduced — the
                // defense already happened (there, AC; here, the save).
                dmg.bypassArmor();
            }
        }

        boolean isKill = damage > 0 && target.getHealth() <= damage;
        String notation = useDice ? profile.damage().get().toString() : "vanilla";
        SaveFeedbackPayload payload = FeedbackBuilder.buildSave(
                save,
                isKill,
                onSuccess,
                notation,
                (int) damage,
                useDice || save.saved(),
                ProfileLookup.forFlavor(attacker.getMainHandItem(), AttackDelivery.SPELL),
                rules,
                RollRuntime.feedbackRoller(),
                target.getUUID(),
                target.level().getGameTime());
        FeedbackSink.get().save(attacker, target, payload, rules.feedback().visibility());
    }

    /**
     * @param weaponStack the item that made the attack (a thrown trident even when it left the
     *     hand) — drives event context and flavor matching
     * @param heldStack the stack fumble weapon-effects act on (empty when the weapon is gone)
     */
    private static void rollAndApply(
            IncomingDamage dmg,
            Rules rules,
            DamageSource source,
            LivingEntity attacker,
            LivingEntity target,
            Optional<EntityProfile> targetProfile,
            Optional<EntityProfile> attackerProfile,
            Optional<ItemProfile> weaponProfile,
            ItemStack weaponStack,
            ItemStack heldStack,
            AttackDelivery delivery,
            int attackBonus,
            DiceExpression damageDice,
            int critRange) {
        int armorClass = intStat(
                targetProfile.map(EntityProfile::armorClass),
                () -> Derivation.armorClass(
                        target.getAttributeValue(Attributes.ARMOR),
                        target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));

        boolean playerAttacker = attacker instanceof Player;
        long gameTime = target.level().getGameTime();
        boolean fumbleSuppressed = !fumbleAppliesTo(rules.fumbles().appliesTo(), playerAttacker)
                || FumbleCooldowns.isOnCooldown(
                        attacker.getUUID(), gameTime, rules.fumbles().cooldownTicks());

        AttackContext ctx = new AttackContext(
                delivery, source, weaponStack, RollMode.NORMAL, java.util.OptionalInt.empty(), Optional.empty());
        AttackPipeline.Bundle bundle = AttackPipeline.resolve(
                attacker,
                target,
                ctx,
                new AttackPipeline.Params(
                        attackBonus, armorClass, damageDice, critRange, RollMode.NORMAL, fumbleSuppressed),
                rules,
                RollRuntime.roller(),
                heldStack,
                weaponProfile,
                attackerProfile);
        AttackResult result = bundle.result();
        if (!bundle.apply()) {
            dmg.cancel(); // a listener canceled/vetoed: no damage, no feedback
            return;
        }

        switch (result.outcome()) {
            case MISS -> dmg.cancel();
            case FUMBLE -> {
                dmg.cancel();
                FumbleCooldowns.record(
                        attacker.getUUID(), gameTime, rules.fumbles().cooldownTicks());
            }
            case HIT, CRIT -> {
                if (rules.damageDice()) {
                    float damage = result.damage();
                    damage *= targetProfile
                            .map(profile -> ProfileLookup.damageMultiplier(profile, source))
                            .orElse(1.0f);
                    damage *= (float) rules.balance().globalDamageMultiplier();
                    dmg.setAmount(damage);
                }
                if (rules.balance().disableVanillaArmorReduction()) {
                    // AC already represents armor — letting vanilla reduce again would double-dip.
                    dmg.bypassArmor();
                }
            }
        }

        List<ConsequenceLine> consequences = bundle.consequences();
        // Approximation, documented: predicts the kill from the damage THIS hit will apply, before
        // any later mitigators (other mods' post-hoc reductions). Good enough for flavor gating.
        boolean isKill =
                result.isHit() && target.getHealth() <= result.damage() * killScale(rules, targetProfile, source);
        RollFeedbackPayload payload = FeedbackBuilder.buildAttack(
                result,
                isKill,
                damageDice.toString(),
                rules.damageDice(),
                consequences,
                ProfileLookup.forFlavor(weaponStack, delivery),
                rules,
                RollRuntime.feedbackRoller(),
                target.getUUID(),
                target.level().getGameTime());
        FeedbackSink.get().roll(attacker, target, payload, rules.feedback().visibility());
    }

    /**
     * The damage multiplier applied to {@code result.damage()} in the switch above (global × the
     * target profile's resist/immune/vulnerable for this source) — kept in sync so the kill
     * prediction matches the damage this hit will actually apply.
     */
    private static float killScale(Rules rules, Optional<EntityProfile> targetProfile, DamageSource source) {
        float multiplier = (float) rules.balance().globalDamageMultiplier();
        return multiplier
                * targetProfile
                        .map(p -> ProfileLookup.damageMultiplier(p, source))
                        .orElse(1.0f);
    }

    /** The {@code attack_rolls.players}/{@code attack_rolls.mobs} gate, by attacker kind. */
    private static boolean attackerRollsEnabled(Rules rules, LivingEntity attacker) {
        return attacker instanceof Player
                ? rules.attackRolls().players()
                : rules.attackRolls().mobs();
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
}
