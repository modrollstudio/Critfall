# Datapack formats

Critfall loads four kinds of JSON from datapacks, reloadable with `/reload`:

| Kind | Directory | Purpose |
|---|---|---|
| Entity profiles | `data/<ns>/critfall/entity_profile/*.json` | AC, attack/save bonus, melee/ranged dice, resist/immune/vulnerable per entity type |
| Item profiles | `data/<ns>/critfall/item_profile/*.json` | Damage dice, crit range, outcome tables per weapon/item |
| Spell profiles | `data/<ns>/critfall/spell_profile/*.json` | Attack-roll vs saving-throw resolution per DAMAGE TYPE (spells) |
| Outcome tables | `data/<ns>/critfall/outcome_table/*.json` | Trigger → weighted effect lists (fumbles AND crit effects) |

Every file carries `"format_version": 1`. Unknown keys log a warning and are ignored (forward
compatibility); a file that fails structural validation (missing `matches`, bad dice, bad ids) is
skipped with an error and the rest of the pack still loads. One broken file never crashes the
server.

The mod ships a default pack (namespace `critfall`) covering **every vanilla mob** and the vanilla
weapon classes, melee and ranged (bow `1d8`, crossbow `1d10`) — override any of it from your own
datapack (see *Matching & priority*). Four outcome tables ship with it: `critfall:default_melee` /
`default_crit` (referenced by the weapon item profiles), `critfall:default_ranged` (bow/crossbow
fumbles — durability wear or nothing; no wild swings into bystanders at range), and
`critfall:default_unarmed` (referenced by the barehanded melee mob profiles — zombies, spiders,
slimes, endermites, hoglins, endermen, ravagers, phantoms — so a zombie horde can fumble into
itself; mobs that hold weapons get their tables from the weapon's item profile instead).

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
  "save_bonus": 2,
  "damage": { "melee": "2d6+3", "ranged": "1d8" },
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
`armor_class` ≥ 1, `crit_range` in 2–20. `damage.melee` covers direct attacks; `damage.ranged`
covers the entity's item-less projectiles (ghast fireballs, shulker bullets — a held launcher's
item profile wins over it, same precedence as melee). `save_bonus` is added to the entity's d20
saving throw against save-based spells (absent = +0, nothing is derived). `damage_modifiers`
lists match damage type ids or `#tags`; immunity zeroes damage, resist halves, vulnerable
doubles, resist+vulnerable cancel.

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
  Apotheosis-boosted one keeps its edge. `"none"` uses the dice verbatim. **For projectile
  impacts** the reference stat is the vanilla projectile damage instead (launchers have no
  attack-damage attribute) — which is exactly what carries Power levels and draw strength, so an
  enchanted bow still out-damages a plain one.
- A weapon's `crit_range` beats the wielder's entity profile crit range.
- `fumble_table` / `crit_table` reference outcome tables; a missing reference logs a warning at
  load and shows as `MISSING` in `/critfall check`.
- `properties` are free-form strings (e.g. `finesse`), reserved for later mechanics.

Dice precedence for a melee attack: held item profile → attacker entity profile `damage.melee` →
`fallbacks.unknown_weapon` from rules.json (derive from the vanilla amount, or pass through).

**Projectiles (M5)** roll on impact. The "weapon" is the item the projectile was launched with:
the bow/crossbow recorded on an arrow, the trident itself for a thrown trident, or the thrown
item (snowball-likes). Dice precedence: launcher item profile → attacker entity profile
`damage.ranged` → `fallbacks.unknown_weapon`. **Ammunition adds dice on top**: if the arrow item
itself matches an item profile with `damage`, those dice are appended to the launcher's (give
`minecraft:tipped_arrow` a `"damage": "1d4"` profile and every bow fires `…+1d4` with them); the
default pack ships no arrow profiles, so plain arrows add nothing. Zero-damage projectiles
(snowballs, eggs) and ownerless ones (dispensers) stay vanilla. Fumble weapon-effects (durability,
drop) hit the launcher still in the shooter's hands — a thrown trident has nothing to wear, so
those pick as no-ops.

## Spell profile

```json
{
  "format_version": 1,
  "matches": ["#irons_spellbooks:fire_magic", "ars_nouveau:flare"],
  "resolution": "save",
  "damage": "6d6",
  "attack_bonus": 5,
  "crit_range": 20,
  "save": { "dc": 14, "on_success": "half" },
  "priority": 0
}
```

Spell profiles match **damage types** (exact id, `#tag`, or `namespace:*`) — how spell-classified
damage resolves (see docs/compat.md for what classifies as a spell and how the big spell mods
map onto this):

- `resolution` — `"attack_roll"` (default): the caster rolls d20 + bonus vs the target's AC,
  crits and fumbles included. `"save"`: the TARGET rolls d20 + its `save_bonus` vs the DC — the
  right feel for AoE; no crits, fumbles, or outcome tables on saves.
- `damage` — dice replacing the vanilla amount. Absent = dice derived from the vanilla amount
  per hit, which keeps the spell mod's own scaling.
- `attack_bonus` — absent = the caster's entity profile bonus, else derived from the vanilla
  amount. `crit_range` (2–20) — absent = caster profile / 20. Both ignored for saves.
- `save.dc` (1–30) — absent = `spells.saves.default_dc` from rules.json. `save.on_success` —
  `half` (rounded down) or `negate`; absent = the rules.json default. Ignored for attack rolls.

Everything but `matches` is optional. The default pack ships **no** spell profiles — unprofiled
spell damage resolves per `fallbacks.unknown_spell` (derive = attack roll with derived dice).

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
