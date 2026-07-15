package studio.modroll.critfall.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackDelivery;

/**
 * Fired when the damage interception detects a damaging interaction between two living entities —
 * server-side, before any of Critfall's own gating or resolution, so it fires even when the damage
 * is later cancelled, exempt, passthrough, dry-run, or involves suppressed entities. Never fired
 * for environmental damage, {@code RollService.performAttack} damage, or outcome-effect damage.
 * Purely observational; the full firing contract is in docs/api.md.
 */
public record CombatInteractionEvent(
        LivingEntity attacker, LivingEntity target, DamageSource source, AttackDelivery delivery) {}
