package studio.modroll.critfall.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackDelivery;

/**
 * Fired the moment Critfall's damage interception detects a damaging combat interaction between
 * two living entities — server-side, at the loader-parity point (after invulnerability checks,
 * before mitigation), and BEFORE any of Critfall's own resolution. It fires regardless of what
 * happens to the damage afterwards: a rolled hit, a miss/fumble that cancels it, a listener
 * cancel/veto, an exempt or always-hits damage type, a vanilla passthrough fallback, dry-run, a
 * suppressed participant, or attack rolls disabled in rules.json — all still fire it.
 *
 * <p>It does NOT fire for damage with no living attacker (environmental), for the damage a
 * consumer applies itself via {@code RollService.performAttack}, or for damage dealt by Critfall's
 * own outcome effects. Purely observational: firing never changes combat resolution, and it
 * reports every qualifying damage event (e.g. each projectile impact), not once per fight — any
 * "combat has started" debouncing is the listener's concern. See docs/api.md for the full
 * contract.
 */
public record CombatInteractionEvent(
        LivingEntity attacker, LivingEntity target, DamageSource source, AttackDelivery delivery) {}
