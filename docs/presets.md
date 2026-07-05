# Rules presets

Three complete `rules.json` files ship in [`examples/presets/`](../examples/presets/). Copy one over
`config/critfall/rules.json` and `/reload`. They differ only in how crits and fumbles behave — every
other mechanic stays at the shipped default. From any preset you can then toggle individual flags;
see [rules-config.md](rules-config.md) for the full flag reference.

## Tempered (`tempered.json`) — the shipped default

Conservative about fumble frequency, which matters because real-time Minecraft rolls 30+ attacks per
minute (a raw 5% nat-1 rate fires constantly):

- Fumble **confirmation roll on** (DC 10): a nat 1 only triggers a consequence if a second d20 also
  fails — roughly halving real-time fumbles.
- Fumble **cooldown 200 ticks** (10s): after a triggered fumble, further nat 1s are plain misses for
  10 seconds.

Pick this if you want tabletop flavor without fumbles dominating fast-paced combat.

## Classic (`classic.json`) — raw d20

The unfiltered tabletop feel:

- Confirmation roll **off**.
- Fumble cooldown **0**.

Every nat 1 that is not saved by other rules can immediately fumble. Great for slower, deliberate
combat or servers that lean into the chaos. Expect more dropped/damaged weapons and friendly-fire
swings, especially with fast-attack weapons.

## Lite (`lite.json`) — rolls without the swing

Attack rolls and damage dice, but no criticals or fumbles:

- `crits.enabled` **false**.
- `fumbles.enabled` **false**.

Nat 20 is a normal hit, nat 1 is a normal miss. Pick this if you want d20 hit/miss and dice damage
but none of the high-variance consequence system — the gentlest introduction for players new to the
mod.

## Rolling your own

Start from the closest preset and flip individual flags. Every consequence (durability break, hit
nearest ally, self-damage, drop weapon, stumble) is independently toggleable, and datapack outcome
tables can override behavior per weapon class or per boss. Turning any flag off cleanly restores
vanilla behavior for that mechanic only.
