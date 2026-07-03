# Datapack formats

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
Three outcome tables ship with it: `critfall:default_melee` / `default_crit` (referenced by the
weapon item profiles) and `critfall:default_unarmed` (referenced by the barehanded melee mob
profiles — zombies, spiders, slimes, endermites, hoglins, endermen, ravagers, phantoms — so a
zombie horde can fumble into itself; mobs that hold weapons get their tables from the weapon's
item profile instead).

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

Use `/critfall inspect [<entity>]` and `/critfall check [<item>]` (permission level 2) to see
the effective stats and which file won. `inspect` without an argument inspects whatever your
crosshair points at (up to 32 blocks, blocks occlude).

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
  "fumble_table": "critfall:default_melee",
  "crit_table": "critfall:default_crit",
  "priority": 0
}
```

Every stat is **optional** — an absent field falls back to attribute derivation for that field
only (PLAN.md §4.3), so you can pin just the AC and leave the rest derived. Constraints:
`armor_class` ≥ 1, `crit_range` in 2–20. `damage` currently understands only `melee` (ranged and
spell keys come with M5). `damage_modifiers` lists match damage type ids or `#tags`; immunity
zeroes damage, resist halves, vulnerable doubles, resist+vulnerable cancel.

> **Profile AC is pinned.** A profile's `armor_class` is the entity's AC, full stop — equipped
> armor does NOT raise it. Derived entities (no profile, or profile without `armor_class`) are the
> opposite: their AC is computed from the live armor/toughness attributes, so gear counts. Pin AC
> for mobs whose difficulty you want stable (bosses, tuned encounters); leave it derived where an
> armored zombie should genuinely be harder to hit.

`fumble_table` / `crit_table` name outcome tables for the entity's own attacks (a zombie's flailing
claws). When the attacker's **held item** has a profile with tables, the item's tables win — same
precedence as damage dice.

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
    { "type": "critfall:hit_nearest_ally", "weight": 1 },
    { "type": "critfall:nothing", "weight": 1 }
  ]
}
```

One generic system for fumbles and crit effects — a table binds a trigger to a weighted effect
list, and profiles reference tables per trigger slot (`fumble_table`, `crit_table`). Both slots
are consulted on **every** attack and each table fires when its own trigger matches the roll, so a
`miss_by_at_least` table in the fumble slot works fine. When a table fires, ONE effect is picked
from the list (chance = `weight / total weight`; `weight` defaults to 1, must be ≥ 1). All keys of
an effect other than `type`/`weight` are that effect's parameters. An unknown effect `type` is
skipped with a load warning (forward compatibility); bad parameters of a known type reject the
file.

### Triggers

| Trigger | Fires when |
|---|---|
| `"nat_1"` | the natural roll is 1 **and** the fumble was confirmed — the rules.json confirmation roll, cooldown, `applies_to`, and `fumbles.enabled` safeguards all apply first |
| `"nat_20"` | the natural roll is 20 **and** the attack critted (`crits.enabled` off silences it) |
| `{"type": "miss_by_at_least", "margin": 5}` | the attack missed and `AC − attack total ≥ margin` |
| `{"type": "roll_range", "min": 2, "max": 5}` | the natural d20 face is in `[min, max]`, on any outcome |

### Effects

Every effect is individually toggleable in `rules.json` (see docs/rules-config.md). A disabled
effect that gets picked is a no-op — it is not filtered from the table, so turning one consequence
off never changes the odds of the others. Parameters marked *(rules default)* fall back to the
rules.json value when omitted.

| Effect `type` | Affects | Parameters | rules.json gate |
|---|---|---|---|
| `critfall:nothing` | — | — | always on |
| `critfall:damage_durability` | attacker's held weapon | — (mode/percent come from rules.json) | `fumbles.durability_break` |
| `critfall:hit_nearest_ally` | nearest bystander around the attacker | `radius` 1–64 *(rules default)* | `fumbles.hit_nearest_ally` |
| `critfall:self_damage` | attacker | `dice` *(rules default)* | `fumbles.self_damage` |
| `critfall:drop_weapon` | attacker's held weapon | — | `fumbles.drop_weapon` |
| `critfall:stumble` | attacker (slowness) | `slowness_ticks` ≥ 1 *(rules default)* | `fumbles.stumble` |
| `critfall:apply_effect` | target (status effect) | `effect` id (required), `ticks` ≥ 1 (required), `amplifier` 0–255 (default 0) | `crits.apply_effect` |
| `critfall:knockback` | target | `strength` in (0, 10] (default 1, in vanilla knockback-enchantment levels) | `crits.knockback` |

`hit_nearest_ally` redirects the fumbled swing: the nearest attackable living entity around the
attacker — excluding the attacker and the original target — takes a fresh roll of the attack's
damage dice. Player bystanders are policy-gated by rules.json (`can_hit_players`, and
`respect_pvp_rules` honors the server PvP setting and team friendly-fire rules); redirected damage
never triggers another attack roll.
