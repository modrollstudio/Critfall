# rules.json — the global ruleset

`config/critfall/rules.json` holds every feature flag. It is created with defaults on first
launch, and re-read on every `/reload` (hot-reloadable). **Every mechanic is individually
toggleable, and any flag set to `false` cleanly restores vanilla behavior for that mechanic only**
— GameTests enforce this per flag. Unknown keys warn and are ignored; a malformed file falls back
to defaults rather than crashing; bad values fall back per-key with a warning.

Default file:

```json
{
  "format_version": 1,
  "attack_rolls": { "enabled": true, "players": true, "mobs": true, "projectiles": true, "spells": true },
  "damage_dice": { "enabled": true },
  "crits": { "enabled": true, "rule": "max_dice", "nat20_always_hits": true },
  "fumbles": {
    "enabled": true,
    "nat1_always_misses": true,
    "confirmation_roll": { "enabled": true, "dc": 10 },
    "cooldown_ticks": 200,
    "durability_break": { "enabled": true, "mode": "set_to_1", "percent": 25 }
  },
  "fallbacks": { "unknown_entity": "derive", "unknown_weapon": "derive" },
  "feedback": { "roll_visibility": "everyone" },
  "balance": { "global_damage_multiplier": 1.0, "disable_vanilla_armor_reduction": true }
}
```

## attack_rolls

Master switch plus per-source switches. `enabled: false` disables the whole mod's interception.
`players` / `mobs` gate whose attacks roll. `projectiles` / `spells` are parsed now and take
effect in M5 (M3 rolls direct melee only).

## damage_dice

`enabled: false` keeps the to-hit roll but applies the vanilla damage amount on hit — no damage
dice are drawn from the RNG at all.

## crits

- `rule` — how crit damage is computed: `max_dice` (dice maximized, modifiers unchanged),
  `double_dice` (dice rolled twice, modifiers once — 5e style), `double_total` (roll once, double
  everything).
- `nat20_always_hits` — a natural 20 hits regardless of AC. Raised crit ranges from profiles
  (e.g. `crit_range: 19`) still need the attack to actually hit; only the natural 20 auto-hits.

## fumbles — with real-time frequency safeguards

Tabletop assumes 2–3 attack rolls a minute; Minecraft melee makes 30+, so a raw 5% nat-1
consequence would fire constantly (PLAN.md §9.1). Three safeguards ship **on by default**:

- `confirmation_roll` — a nat 1 only triggers consequences if a second d20 rolls **below** `dc`
  (default 10, valid 2–20). Roughly halves fumble frequency; a saved fumble is a plain miss.
- `cooldown_ticks` — after a triggered fumble, further nat 1s by the same attacker are plain
  misses for this long (default 200 = 10s; 0 disables the cooldown).
- `durability_break.mode` — `set_to_1` drops the weapon to 1 remaining durability (dramatic);
  `percent_loss` removes `percent`% of max durability per fumble instead, so repeated fumbles
  wear gear down rather than trashing it. The PLAN.md shorthand `"mode": "percent_loss:25"` is
  also accepted. Neither mode ever fully breaks the weapon mid-swing.
- `nat1_always_misses: false` lets a high attack bonus land even on a natural 1 (house rule).

`hit_nearest_ally`, `self_damage`, `drop_weapon`, `stumble`, and `applies_to` are part of the
spec but land with the M4 outcome-table executor — they are recognized (no "unknown key" warning)
and inert for now.

## fallbacks

What happens when no datapack profile matches: `derive` (default) computes plausible stats from
vanilla attributes (PLAN.md §4.3); `vanilla_passthrough` leaves the damage event untouched —
`unknown_entity` checks the defender, `unknown_weapon` checks the attacker's dice sources.

## feedback

`roll_visibility`: `everyone` (attacker and target players, M3 scope — M6 widens this),
`attacker_only`, or `off`. `sounds` / `particles` are reserved for the M6 client module.

## balance

- `global_damage_multiplier` — scales all rolled damage (misses already cut DPS ~40%; packs tuned
  around vanilla time-to-kill may want > 1).
- `disable_vanilla_armor_reduction` — AC already represents armor, so vanilla armor reduction is
  zeroed for rolled damage by default to avoid double-dipping (enchantments, Resistance and
  absorption still apply — see docs/design-decisions.md). Set `false` to let armor double-dip.
