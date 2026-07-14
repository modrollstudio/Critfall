// Grant advantage on the Critfall attack roll when the attacker is sneaking.
// KubeJS server script — requires Critfall + KubeJS.
const CritfallEvents = Java.loadClass('studio.modroll.critfall.api.event.CritfallEvents');
const RollMode = Java.loadClass('studio.modroll.critfall.api.dice.RollMode');

CritfallEvents.onPreAttackRoll(event => {
    const attacker = event.attacker();
    if (attacker && attacker.isCrouching()) {
        event.mode(RollMode.ADVANTAGE);
    }
});
