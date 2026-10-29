// Species traits on Critfall hazards (Critfall 0.2.14+): a halfling's Luck and a dwarf's poison
// resilience, plus Bless on attack rolls.
// KubeJS server script — requires Critfall + KubeJS. Replace the tag checks with your own traits.
const CritfallEvents = Java.loadClass('studio.modroll.critfall.api.event.CritfallEvents');
const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation');

const POISON_NEEDLE = ResourceLocation.parse('critfall_depths:trap/poison_needle');

CritfallEvents.onHazardRoll(event => {
    const target = event.target();
    if (target.tags.contains('halfling')) {
        event.rerollOnes();          // a natural 1 rolls again once
    }
    // Hazards with an id can be matched without parsing their label.
    if (event.id().filter(id => id.equals(POISON_NEEDLE)).isPresent() && target.tags.contains('dwarf')) {
        event.grantAdvantage();
    }
});

CritfallEvents.onPreAttackRoll(event => {
    const attacker = event.attacker();
    if (attacker && attacker.tags.contains('blessed')) {
        event.addBonus(2);           // shown in the readout's modifier
    }
});
