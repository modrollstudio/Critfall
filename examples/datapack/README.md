# Critfall example datapack

A small, working datapack that shows every Critfall registry in one place. Drop it into a world's
`datapacks/` folder (or a Fabric/NeoForge server's), run `/reload`, and it takes effect immediately.
Everything here matches real vanilla ids, so you can try it without any other mod — then rename the
`matches` entries to point at your pack's own mobs and items.

See `docs/datapack-formats.md` for the full field reference.

## What each file does

| File | Registry | Demonstrates |
|------|----------|--------------|
| `entity_profile/tuned_boss.json` | `entity_profile` | A hand-tuned boss (the Warden): fixed AC/attack bonus, melee **and** ranged dice, a narrowed crit range (19–20), a `save_bonus` for AoE spell saves, resist/immune/vulnerable damage modifiers, and a high `priority` so it wins over any broader match. |
| `item_profile/example_blade.json` | `item_profile` | A custom weapon (the netherite sword): explicit dice with `modifier_from: attack_damage_attribute` so real stats still scale the bonus, a per-weapon `crit_range`, its own `fumble_table`, a built-in `crit_table`, and a `finesse` property. |
| `outcome_table/example_fumble.json` | `outcome_table` | A `nat_1` fumble table with a weighted mix of effects (damage durability, hit a nearby ally, stumble, or nothing). Referenced by the blade above. |
| `flavor_pool/example_blade.json` | `flavor_pool` | Narrative lines for crit/fumble/kill, keyed by translation id. `priority: 10` makes it beat the built-in sword pool for the netherite sword. |

## Cross-references

- The blade's `fumble_table` points at **this pack's** `example:example_fumble`.
- The blade and boss both reuse Critfall's built-in `critfall:default_crit` crit table — you can
  reference tables from any loaded pack.
- The flavor lines (`example.flavor.blade.*`) are translation keys. Add matching entries to an
  `assets/example/lang/en_us.json` if you want custom text; without them a client shows the raw key.

## Adapting it

1. Change each `matches` array to your target entity/item ids or tags (`"#c:bosses"`,
   `"alexsmobs:*"`, …).
2. Tune the numbers. Run `/critfall inspect <entity>` and `/critfall check <item>` in-game to see
   which profile wins and what the effective values are.
3. `/reload` after every edit.
