// Grant advantage on the Critfall attack roll when the attacker is sneaking.
// KubeJS server script — requires Critfall + KubeJS.
// grantAdvantage() (Critfall 0.2.14+) combines with other sources the 5e way; on 0.2.13 and older,
// use event.mode(RollMode.ADVANTAGE), which replaces the mode instead.
const CritfallEvents = Java.loadClass('studio.modroll.critfall.api.event.CritfallEvents');

CritfallEvents.onPreAttackRoll(event => {
    const attacker = event.attacker();
    if (attacker && attacker.isCrouching()) {
        event.grantAdvantage();
    }
});
