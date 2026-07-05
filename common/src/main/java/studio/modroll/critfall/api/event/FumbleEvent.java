package studio.modroll.critfall.api.event;

import net.minecraft.world.entity.LivingEntity;
import studio.modroll.critfall.api.AttackContext;
import studio.modroll.critfall.combat.AttackResult;

/** Fired when an attack resolves to a fumble (nat 1, confirmed). Observational (PLAN §4.4). */
public record FumbleEvent(LivingEntity attacker, LivingEntity target, AttackContext context, AttackResult result) {}
