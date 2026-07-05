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
  "crits": {
    "enabled": true,
    "rule": "max_dice",
    "nat20_always_hits": true,
    "apply_effect": { "enabled": true },
    "knockback": { "enabled": true }
  },
  "fumbles": {
    "enabled": true,
    "nat1_always_misses": true,
    "confirmation_roll": { "enabled": true, "dc": 10 },
    "cooldown_ticks": 200,
    "durability_break": { "enabled": true, "mode": "set_to_1", "percent": 25 },
    "hit_nearest_ally": { "enabled": true, "radius": 4, "can_hit_players": true, "respect_pvp_rules": true },
    "self_damage": { "enabled": false, "dice": "1d4" },
    "drop_weapon": { "enabled": false },
    "stumble": { "enabled": false, "slowness_ticks": 40 },
    "applies_to": "players_and_mobs"
  },
  "spells": {
    "saves": { "enabled": true, "default_dc": 13, "on_success": "half" }
  },
  "fallbacks": { "unknown_entity": "derive", "unknown_weapon": "derive", "unknown_spell": "derive" },
  "feedback": { "roll_visibility": "everyone", "flavor": { "enabled": true, "cooldown_ticks": 20 } },
  "balance": { "global_damage_multiplier": 1.0, "disable_vanilla_armor_reduction": true }
}
```

## attack_rolls

Master switch plus per-source switches. `enabled: false` disables the whole mod's interception.
`players` / `mobs` gate whose attacks roll (by attacker, for every category). `projectiles`
gates rolls on projectile impact (arrows, tridents, thrown items, fireballs); `spells` gates
spell-classified damage (see docs/compat.md). Either set to `false` restores vanilla behavior
for that category only.

## damage_dice

`enabled: false` keeps the to-hit roll but applies the vanilla damage amount on hit — no damage
dice are drawn from the RNG at all.

## crits

- `rule` — how crit damage is computed: `max_dice` (dice maximized, modifiers unchanged),
  `double_dice` (dice rolled twice, modifiers once — 5e style), `double_total` (roll once, double
  everything).
- `nat20_always_hits` — a natural 20 hits regardless of AC. Raised crit ranges from profiles
  (e.g. `crit_range: 19`) still need the attack to actually hit; only the natural 20 auto-hits.
- `apply_effect` / `knockback` — gates for the matching outcome-table effects (the nat-20
  "shot in the eye" status effect and the extra shove; see docs/datapack-formats.md). Disabling
  one turns that consequence into a no-op wherever a table picks it, without changing the odds of
  the table's other entries. `crits.enabled: false` silences whole `nat_20` tables as well.

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

A **confirmed** fumble fires the attacker's fumble outcome table (held item profile first, then
entity profile — see docs/datapack-formats.md). Each consequence the table can pick is gated here
individually; a disabled consequence is a no-op when picked, leaving the rest of the table's odds
untouched:

- `durability_break` — gates `critfall:damage_durability` and supplies its mode/percent.
- `hit_nearest_ally` — gates `critfall:hit_nearest_ally` and supplies the default `radius`
  (1–64). `can_hit_players: false` never redirects a fumble into a player;
  `respect_pvp_rules: true` additionally honors the server's PvP setting and team friendly-fire
  rules when one player's fumble would hit another.
- `self_damage` — gates `critfall:self_damage` and supplies the default `dice`.
- `drop_weapon` — gates `critfall:drop_weapon` (ships disabled — it is the most disruptive).
- `stumble` — gates `critfall:stumble` and supplies the default `slowness_ticks`.
- `applies_to` — `players`, `mobs`, or `players_and_mobs`: whose nat 1s can become fumbles at
  all. Outside the set, a nat 1 is a plain miss (no confirmation roll, no consequences).

## spells

- `saves.enabled` — the saving-throw resolution (M5): a spell profile with
  `"resolution": "save"` has the TARGET roll d20 + `save_bonus` vs a DC instead of the caster
  rolling to hit — the right feel for AoE. With saves disabled, save-profiles resolve as attack
  rolls instead (attack rolls being the spell default, this is the closest still-rolled behavior;
  set `attack_rolls.spells: false` to make spells fully vanilla).
- `saves.default_dc` (1–30) — used when the profile names no `save.dc`.
- `saves.on_success` — `half` (rounded down) or `negate`; profile `save.on_success` overrides.

A successful save's remaining damage (and a failed save's full damage, when the profile has
dice) bypasses vanilla armor reduction under the same `disable_vanilla_armor_reduction` flag as
attack rolls — the defense already happened, it was the save.

## fallbacks

What happens when no datapack profile matches: `derive` (default) computes plausible stats from
vanilla attributes (PLAN.md §4.3); `vanilla_passthrough` leaves the damage event untouched —
`unknown_entity` checks the defender, `unknown_weapon` checks the attacker's dice sources
(melee and projectile), `unknown_spell` checks spell-classified damage whose type no spell
profile matches (`derive` rolls an attack with dice derived from the vanilla amount).

## feedback

Server-side feedback policy. Rendering itself (roll readout, flavor lines, sounds, particles) is
client-side and toggled per-client in `config/critfall/client.json` — see
[client-feedback.md](client-feedback.md).

- `roll_visibility`: `everyone` (attacker and target players), `attacker_only`, or `off` — who the
  server sends roll feedback to at all.
- `flavor`: the server-authoritative anti-spam gate for narrative flavor lines.
  - `enabled` — master switch for sending flavor lines (a client can still hide them locally).
  - `cooldown_ticks` (default `20`) — per-target minimum ticks between non-priority flavor lines. A
    nat-20 crit or nat-1 fumble always sends (priority) and resets this; a plain kill is gated by it.

`sounds` / `particles` are **no longer server settings** — they moved to the client config (rendering
is client-side). If present in an old `rules.json` they are ignored with a one-line warning.

## balance

- `global_damage_multiplier` — scales all rolled damage (misses already cut DPS ~40%; packs tuned
  around vanilla time-to-kill may want > 1).
- `disable_vanilla_armor_reduction` — AC already represents armor, so vanilla armor reduction is
  zeroed for rolled damage by default to avoid double-dipping (enchantments, Resistance and
  absorption still apply — see docs/design-decisions.md). Set `false` to let armor double-dip.
