package studio.modroll.critfall.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackDelivery;

/**
 * Fired server-side whenever a living entity damages another, before any of Critfall's own
 * resolution — even for damage that is later cancelled or passed through. Observe-only; full
 * firing contract in docs/api.md.
 */
public record CombatInteractionEvent(
        LivingEntity attacker, LivingEntity target, DamageSource source, AttackDelivery delivery) {}
