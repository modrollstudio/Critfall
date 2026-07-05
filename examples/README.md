# Critfall examples

Drop-in [KubeJS](https://kubejs.com/) **server scripts** demonstrating the Critfall public API
(`studio.modroll.critfall.api`). Copy a file into your pack's `kubejs/server_scripts/` folder. These
require KubeJS to be installed alongside Critfall; **Critfall itself does not depend on KubeJS**.

Modern KubeJS exposes public Java classes to scripts directly, so these call `RollService` and
`CritfallEvents` with `Java.loadClass(...)` — no extra plugin. See [`docs/api.md`](../docs/api.md)
for the full API.

- `pre_attack_advantage.js` — grant advantage on the attack roll when the attacker is sneaking.
- `roll_and_suppress.js` — suppress two entities and drive an attack entirely from script.
- `effective_profile_lookup.js` — read an entity's effective AC / attack bonus / dice.

> **Note on KubeJS event names.** KubeJS's own event hooks (`ItemEvents`, `EntityEvents`, …) vary
> across KubeJS versions and Minecraft versions. The **Critfall** calls inside these scripts
> (`RollService.*`, `CritfallEvents.*`) are the stable contract; adapt the surrounding KubeJS hook
> to whatever your KubeJS version provides.
