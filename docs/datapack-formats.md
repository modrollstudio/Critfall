# Datapack formats (M3)

Critfall loads three kinds of JSON from datapacks, reloadable with `/reload`:

| Kind | Directory | Purpose |
|---|---|---|
| Entity profiles | `data/<ns>/critfall/entity_profile/*.json` | AC, attack bonus, melee dice, resist/immune/vulnerable per entity type |
| Item profiles | `data/<ns>/critfall/item_profile/*.json` | Damage dice, crit range, outcome tables per weapon/item |
| Outcome tables | `data/<ns>/critfall/outcome_table/*.json` | Trigger → weighted effect lists (fumbles AND crit effects) |

Every file carries `"format_version": 1`. Unknown keys log a warning and are ignored (forward
compatibility); a file that fails structural validation (missing `matches`, bad dice, bad ids) is
skipped with an error and the rest of the pack still loads. One broken file never crashes the
server.

The mod ships a default pack (namespace `critfall`) covering **every vanilla mob** and the vanilla
melee weapon classes — override any of it from your own datapack (see *Matching & priority*).

## Matching & priority

`matches` entries come in three forms, from most to least specific:

| Form | Example | Specificity |
|---|---|---|
| Exact id | `"minecraft:enderman"` | 3 |
| Tag | `"#minecraft:undead"` | 2 |
| Namespace wildcard | `"alexsmobs:*"` (one JSON tunes a whole mod) | 1 |

When several profiles match the same entity/item, the winner is chosen by:

1. highest `priority` (an integer, default 0 — the shipped defaults all use 0, so any positive
   priority in your pack overrides them),
2. then the most specific matching entry (exact id beats tag beats wildcard),
3. then the lexicographically smaller file id, so resolution is deterministic.

Use `/critfall inspect <entity>` and `/critfall check [<item>]` (permission level 2) to see the
effective stats and which file won.

## Entity profile

```json
{
  "format_version": 1,
  "matches": ["minecraft:enderman"],
  "armor_class": 14,
  "attack_bonus": 6,
  "damage": { "melee": "2d6+3" },
  "crit_range": 20,
  "damage_modifiers": {
    "resist": ["#critfall:physical"],
    "immune": [],
    "vulnerable": ["minecraft:in_fire"]
  },
  "priority": 0
}
```

Every stat is **optional** — an absent field falls back to attribute derivation for that field
only (PLAN.md §4.3), so you can pin just the AC and leave the rest derived. Constraints:
`armor_class` ≥ 1, `crit_range` in 2–20. `damage` currently understands only `melee` (ranged and
spell keys come with M5). `damage_modifiers` lists match damage type ids or `#tags`; immunity
zeroes damage, resist halves, vulnerable doubles, resist+vulnerable cancel.

## Item profile

```json
{
  "format_version": 1,
  "matches": ["#minecraft:swords"],
  "damage": "1d8",
  "modifier_from": "attack_damage_attribute",
  "crit_range": 20,
  "fumble_table": "critfall:default_melee",
  "crit_table": "critfall:default_crit",
  "properties": ["finesse"],
  "priority": 0
}
```

- `modifier_from` — `"attack_damage_attribute"` (default) adds a flat bonus of
  `round(attacker's attack damage − dice average)`, clamped to 0…12, so the expected rolled damage
  tracks the item's real (post-modifier) vanilla power: an iron sword (6 damage) rolls `1d8+2`, an
  Apotheosis-boosted one keeps its edge. `"none"` uses the dice verbatim.
- A weapon's `crit_range` beats the wielder's entity profile crit range.
- `fumble_table` / `crit_table` reference outcome tables; a missing reference logs a warning at
  load and shows as `MISSING` in `/critfall check`.
- `properties` are free-form strings (e.g. `finesse`), reserved for later mechanics.

Dice precedence for an attack: held item profile → attacker entity profile `damage.melee` →
`fallbacks.unknown_weapon` from rules.json (derive from the vanilla amount, or pass through).

## Outcome table

```json
{
  "format_version": 1,
  "trigger": "nat_1",
  "effects": [
    { "type": "critfall:damage_durability", "weight": 3 },
    { "type": "critfall:nothing", "weight": 1 }
  ]
}
```

One generic system for fumbles and crit effects — a table binds a trigger to a weighted effect
list, and profiles reference tables per trigger. Triggers: `"nat_1"`, `"nat_20"`,
`{"type": "miss_by_at_least", "margin": 5}`, `{"type": "roll_range", "min": 2, "max": 5}`.
`weight` defaults to 1 (must be ≥ 1); all keys of an effect other than `type`/`weight` are that
effect's parameters.

> **M3 status:** tables are loaded, validated, and referenced by item profiles, but the executor
> that actually applies effects lands in **M4** — effect `type` ids are provisional until then.
> The M3 fumble consequence (weapon durability) is driven by `rules.json` directly.
