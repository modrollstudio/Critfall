# Quickstart: adopting Critfall in a modpack

Critfall works out of the box in any pack — unknown mobs and weapons get plausible stats derived
from their vanilla attributes. But five minutes of setup turns "plausible" into "tuned for your
pack." Here is the fast path.

## 1. Install

Drop the Critfall jar for your loader (NeoForge or Fabric) into `mods/`. On first launch it writes
`config/critfall/rules.json` with the shipped defaults. The server is authoritative; the client mod
is optional (feedback only).

## 2. Pick a feel

Copy one of the presets over `config/critfall/rules.json` and `/reload`:

- **Tempered** (the default) — fumble confirmation on, 10-second cooldown. Conservative about how
  often fumbles fire in real-time combat.
- **Classic** — raw d20: confirmation off, no cooldown. Every nat 1 can bite.
- **Lite** — attack rolls and damage dice only; crits and fumbles off.

See [presets.md](presets.md).

## 3. Scaffold a datapack

In-game (as an operator):

```
/critfall generate
/reload
```

This writes one derived profile per mob and weapon into
`<world>/datapacks/critfall_generated/`. Now every entity/item in your pack has an explicit,
editable JSON file to tune.

## 4. Tune the ~30 mobs that matter

Fallbacks give plausible numbers, not balanced ones — identity like "a lich is low-AC/high-save, a
knight is high-AC" is not derivable from attributes. Edit the generated JSON (or, better, put your
overrides in your own datapack with a higher `priority` so a re-generate never clobbers them). Use:

```
/critfall inspect      (aim at a mob)
/critfall check        (hold a weapon)
```

to see which profile won and what the effective values are. See
[datapack-formats.md](datapack-formats.md) for the field reference and
[example datapack](../examples/datapack/) for a worked example.

## 5. Calibrate without breaking play

Set `"dry_run": { "enabled": true }` and `/reload`. Rolls are shown but vanilla damage still applies
— play normally, watch the numbers, adjust. Turn it off to go live. See [commands.md](commands.md).

## 6. Review coverage

```
/critfall report
```

exports a spreadsheet of every entity/item showing which are profiled versus fallback and their
effective values — a checklist for what still needs a tuning pass.

## What you still own

- **Balance intent** for the handful of bosses and signature mobs that matter.
- **Damage-source tags** for magic mods that fire generic damage (see [compat.md](compat.md)).
- **Exemptions** for tech-mod damage that should not roll — curate `#critfall:exempt`.
- **HP/TTK** tuning, since misses cut effective DPS (pairs with the `global_damage_multiplier`).

See the [FAQ](faq.md) for the common questions.
