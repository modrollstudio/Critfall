package studio.modroll.critfall.api.event;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.api.combat.AttackResult;

/** Fired when an attack resolves to a critical hit (nat within crit range). Observational (PLAN §4.4). */
public record CritEvent(LivingEntity attacker, LivingEntity target, AttackContext context, AttackResult result) {}
