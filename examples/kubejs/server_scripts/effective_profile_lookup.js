// Print the effective Critfall stats of any living entity that takes damage — the AC, attack bonus,
// and melee dice Critfall would use, resolved from the datapack profile or derived fallback.
const RollService = Java.loadClass('studio.modroll.critfall.api.RollService');

// The KubeJS hook name varies by version — adapt it; the RollService call is the stable part.
EntityEvents.hurt(event => {
    const target = event.entity;
    if (!target || !target.isLiving() || target.level.isClientSide()) return;

    const eff = RollService.effectiveEntity(target);
    console.log('Critfall effective profile for ' + target.type
        + ': AC=' + eff.armorClass()
        + ' atk=+' + eff.attackBonus()
        + ' melee=' + eff.meleeDamage());
});
