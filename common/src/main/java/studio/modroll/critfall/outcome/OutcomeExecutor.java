package studio.modroll.critfall.outcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.combat.AttackResult;
import studio.modroll.critfall.combat.Rules;
import studio.modroll.critfall.data.EntityProfile;
import studio.modroll.critfall.data.ItemProfile;
import studio.modroll.critfall.data.OutcomeEffect;
import studio.modroll.critfall.data.OutcomeTable;
import studio.modroll.critfall.data.ProfileStore;
import studio.modroll.critfall.dice.DiceExpression;
import studio.modroll.critfall.dice.DiceRoller;
import studio.modroll.critfall.feedback.ConsequenceLine;

/**
 * Applies outcome tables (PLAN.md §4.2/M4) after an attack roll resolved: gathers the tables the
 * attack's profiles reference, fires those whose trigger matches, and inflicts one weighted-picked
 * effect per fired table. Fumbles and crit effects are this same mechanism — only the trigger in
 * the table differs. All decisions are made in {@link OutcomeSelector}; this class only touches
 * the world.
 */
public final class OutcomeExecutor {

    /**
     * True while effects are being applied. Effects deal damage with plain {@code hurt()} calls
     * (redirected swings, self damage), and the damage interception must let those through instead
     * of rolling a second d20. Single flag, not a depth counter — the server thread is the only
     * writer and effects never nest.
     */
    private static boolean applyingEffects;

    private OutcomeExecutor() {}

    public static boolean isApplyingEffects() {
        return applyingEffects;
    }

    /**
     * Runs every referenced table whose trigger matches {@code result}. Table references come from
     * the attack's weapon item profile first, then the attacker's entity profile — same precedence
     * as damage dice; both slots (fumble and crit) are always consulted because a slot may hold a
     * table with any trigger (e.g. a {@code miss_by_at_least} table in the fumble slot).
     *
     * @param weapon the HELD stack that weapon effects (durability, drop) act on — the main hand
     *     for melee, whichever hand still holds the launcher for projectiles, or {@link
     *     ItemStack#EMPTY} when nothing applicable is held (a thrown trident); weapon effects
     *     then no-op
     */
    public static List<ConsequenceLine> run(
            LivingEntity attacker,
            LivingEntity target,
            AttackResult result,
            DiceExpression damageDice,
            Rules rules,
            DiceRoller roller,
            ItemStack weapon,
            Optional<ItemProfile> weaponProfile,
            Optional<EntityProfile> attackerProfile) {
        List<OutcomeTable> tables = new ArrayList<>(2);
        addTable(
                tables,
                weaponProfile
                        .flatMap(ItemProfile::fumbleTable)
                        .or(() -> attackerProfile.flatMap(EntityProfile::fumbleTable)));
        addTable(
                tables,
                weaponProfile
                        .flatMap(ItemProfile::critTable)
                        .or(() -> attackerProfile.flatMap(EntityProfile::critTable)));
        if (tables.isEmpty()) {
            return List.of();
        }
        List<ConsequenceLine> fired = new ArrayList<>();
        applyingEffects = true;
        try {
            for (OutcomeTable table : tables) {
                if (!OutcomeSelector.triggers(table.trigger(), result)) {
                    continue;
                }
                OutcomeEffect effect = OutcomeSelector.pick(table, roller);
                // Playtest audit trail (logs/debug.log): every fired table logs what it picked.
                if (OutcomeSelector.enabled(effect, rules)) {
                    Critfall.LOG.debug(
                            "Outcome table {} fired ({}) for {}: applying {}",
                            table.id(),
                            table.trigger(),
                            attacker.getName().getString(),
                            effect);
                    apply(effect, attacker, target, weapon, damageDice, rules, roller)
                            .ifPresent(fired::add);
                } else {
                    Critfall.LOG.debug(
                            "Outcome table {} fired ({}) for {}: picked {} but it is disabled in rules.json",
                            table.id(),
                            table.trigger(),
                            attacker.getName().getString(),
                            effect);
                }
            }
        } finally {
            applyingEffects = false;
        }
        return fired;
    }

    private static void addTable(List<OutcomeTable> tables, Optional<ResourceLocation> reference) {
        // A missing table already warned at datapack load; stay silent per-attack.
        reference
                .flatMap(ProfileStore::outcomeTable)
                .filter(table -> !tables.contains(table))
                .ifPresent(tables::add);
    }

    private static Optional<ConsequenceLine> apply(
            OutcomeEffect effect,
            LivingEntity attacker,
            LivingEntity target,
            ItemStack weapon,
            DiceExpression damageDice,
            Rules rules,
            DiceRoller roller) {
        return switch (effect) {
            case OutcomeEffect.Nothing ignored -> Optional.empty();
            case OutcomeEffect.DamageDurability ignored -> {
                boolean damaged = damageDurability(weapon, rules.fumbles());
                yield damaged
                        ? Optional.of(ConsequenceLine.durability(rules.fumbles().durabilityMode()))
                        : Optional.empty();
            }
            case OutcomeEffect.HitNearestAlly ally -> hitNearestAlly(attacker, target, ally, damageDice, rules, roller);
            case OutcomeEffect.SelfDamage self -> {
                selfDamage(attacker, self, rules, roller);
                yield Optional.of(ConsequenceLine.of(ConsequenceLine.SELF_DAMAGE));
            }
            case OutcomeEffect.DropWeapon ignored -> {
                dropWeapon(attacker, weapon);
                yield Optional.of(ConsequenceLine.of(ConsequenceLine.DROP_WEAPON));
            }
            case OutcomeEffect.Stumble stumble -> {
                attacker.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        stumble.slownessTicks().orElse(rules.fumbles().stumble().slownessTicks()),
                        0));
                yield Optional.of(ConsequenceLine.of(ConsequenceLine.STUMBLE));
            }
            case OutcomeEffect.ApplyEffect statusEffect -> applyEffect(target, statusEffect);
            case OutcomeEffect.Knockback knockback -> {
                target.knockback(
                        0.4 * knockback.strength(), attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
                yield Optional.of(ConsequenceLine.of(ConsequenceLine.KNOCKBACK));
            }
        };
    }

    private static boolean damageDurability(ItemStack weapon, Rules.Fumbles fumbles) {
        if (!weapon.isDamageableItem()) {
            return false;
        }
        int maxDamage = weapon.getMaxDamage();
        switch (fumbles.durabilityMode()) {
            case SET_TO_1 -> weapon.setDamageValue(Math.max(weapon.getDamageValue(), maxDamage - 1));
            case PERCENT_LOSS -> {
                // Wears the weapon down but never past 1 remaining durability — fumbles should
                // punish, not silently delete gear mid-swing.
                int loss = Math.max(1, maxDamage * fumbles.durabilityPercent() / 100);
                weapon.setDamageValue(Math.min(maxDamage - 1, weapon.getDamageValue() + loss));
            }
        }
        return true;
    }

    private static Optional<ConsequenceLine> hitNearestAlly(
            LivingEntity attacker,
            LivingEntity target,
            OutcomeEffect.HitNearestAlly effect,
            DiceExpression damageDice,
            Rules rules,
            DiceRoller roller) {
        Rules.HitNearestAlly config = rules.fumbles().hitNearestAlly();
        int radius = effect.radius().orElse(config.radius());
        AABB search = attacker.getBoundingBox().inflate(radius);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : attacker.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        candidate -> isEligibleBystander(attacker, target, candidate, config))) {
            double distance = candidate.distanceToSqr(attacker);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            // nobody around to hit — the wild swing whooshes into air, no dice are rolled
            Critfall.LOG.debug(
                    "hit_nearest_ally: no eligible bystander within {} blocks of {}",
                    radius,
                    attacker.getName().getString());
            return Optional.empty();
        }
        int damage = Math.max(0, roller.roll(damageDice).total());
        Critfall.LOG.debug(
                "hit_nearest_ally: {} redirects into {} for {} ({})",
                attacker.getName().getString(),
                nearest.getName().getString(),
                damage,
                damageDice);
        if (damage > 0) {
            nearest.hurt(meleeSource(attacker), damage);
        }
        return Optional.of(
                ConsequenceLine.of(ConsequenceLine.HIT_ALLY, nearest.getName().getString()));
    }

    /**
     * Who a fumbled swing may land on: any attackable living bystander except the attacker and the
     * intended target. Player bystanders are policy-gated: {@code can_hit_players} excludes them
     * entirely, and {@code respect_pvp_rules} honors the server PvP setting and team
     * friendly-fire rules for player-vs-player redirects.
     */
    private static boolean isEligibleBystander(
            LivingEntity attacker, LivingEntity target, LivingEntity candidate, Rules.HitNearestAlly config) {
        if (candidate == attacker || candidate == target || !candidate.isAlive() || !candidate.isAttackable()) {
            return false;
        }
        if (candidate instanceof Player player) {
            if (!config.canHitPlayers() || player.isCreative() || player.isSpectator()) {
                return false;
            }
            if (config.respectPvpRules() && attacker instanceof Player attackingPlayer) {
                if (player.getServer() != null && !player.getServer().isPvpAllowed()) {
                    return false;
                }
                if (!attackingPlayer.canHarmPlayer(player)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void selfDamage(
            LivingEntity attacker, OutcomeEffect.SelfDamage effect, Rules rules, DiceRoller roller) {
        DiceExpression dice = effect.dice().orElse(rules.fumbles().selfDamage().dice());
        int damage = Math.max(0, roller.roll(dice).total());
        if (damage > 0) {
            attacker.hurt(attacker.damageSources().generic(), damage);
        }
    }

    private static void dropWeapon(LivingEntity attacker, ItemStack weapon) {
        if (weapon.isEmpty()) {
            return;
        }
        // Identity, not equality: the effect drops the exact stack the attack used, from
        // whichever hand still holds it.
        InteractionHand hand = attacker.getItemInHand(InteractionHand.MAIN_HAND) == weapon
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        if (attacker.getItemInHand(hand) != weapon) {
            return;
        }
        attacker.setItemInHand(hand, ItemStack.EMPTY);
        if (attacker instanceof Player player) {
            player.drop(weapon, false, true);
        } else {
            attacker.spawnAtLocation(weapon);
        }
    }

    private static Optional<ConsequenceLine> applyEffect(LivingEntity target, OutcomeEffect.ApplyEffect effect) {
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(effect.effect());
        if (holder.isEmpty()) {
            Critfall.LOG.warn("apply_effect references unknown status effect '{}' — skipped", effect.effect());
            return Optional.empty();
        }
        target.addEffect(new MobEffectInstance(holder.get(), effect.ticks(), effect.amplifier()));
        return Optional.of(ConsequenceLine.of(
                ConsequenceLine.APPLY_EFFECT,
                holder.get().value().getDisplayName().getString()));
    }

    private static DamageSource meleeSource(LivingEntity attacker) {
        return attacker instanceof Player player
                ? attacker.damageSources().playerAttack(player)
                : attacker.damageSources().mobAttack(attacker);
    }
}
