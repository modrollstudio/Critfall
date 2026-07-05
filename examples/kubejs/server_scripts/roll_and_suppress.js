// Example orchestrator: when a player right-clicks a mob with a stick, suppress both participants
// and resolve one Critfall attack entirely from script. Illustrates the §12 companion-mod path:
// suppression makes Critfall's automatic pipeline stand down so the script owns the combat.
const RollService = Java.loadClass('studio.modroll.critfall.api.RollService');
const AttackContext = Java.loadClass('studio.modroll.critfall.api.AttackContext');

// The KubeJS hook name varies by version — adapt it; the RollService calls are the stable part.
ItemEvents.entityInteracted('minecraft:stick', event => {
    const { player, target, level } = event;
    if (level.isClientSide() || !target.isLiving()) return;

    RollService.suppress(player);
    RollService.suppress(target);
    try {
        const ctx = AttackContext.melee(player.damageSources().playerAttack(player), player.getMainHandItem());
        const result = RollService.performAttack(player, target, ctx);
        player.tell('Critfall: ' + result.outcome() + ' for ' + result.damage());
    } finally {
        RollService.release(player);
        RollService.release(target);
    }
});
