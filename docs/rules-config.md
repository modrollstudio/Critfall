# rules.json — the global ruleset

`config/critfall/rules.json` holds every feature flag. It is created with defaults on first
launch, and re-read on every `/reload` (hot-reloadable). **Every mechanic is individually
toggleable, and any flag set to `false` cleanly restores vanilla behavior for that mechanic only**
— GameTests enforce this per flag. Unknown keys warn and are ignored; a malformed file falls back
to defaults rather than crashing; bad values fall back per-key with a warning.

## In-game config screen

Every key below can also be edited in game: **Mods → Critfall → Config** on NeoForge, or Mod Menu's
config button on Fabric (Mod Menu is optional). The main page has the three [presets](presets.md) at
the top and the switches most players want: `attack_rolls.enabled`, `damage_dice.enabled`,
`crits.enabled`, `fumbles.enabled`, `fumbles.durability_break.enabled`,
`fumbles.hit_nearest_ally.enabled`, `advantage_sources.jump_attack`, `feedback.roll_visibility` and
`feedback.flavor.enabled`.
**All settings** opens every key, grouped by section; a value the file loader would reject turns red
there (the same ranges as below). Changes made on the main page carry over to All settings; Cancel
there goes back to the main page without them, Save there saves everything. **Save** writes this same file and loads it the way `/reload` does, so a
singleplayer or LAN world uses the new rules at once. Keys the screen does not show (unknown keys,
reserved keys, `format_version`) are kept as they are.

Connected to a server, the config button shows no rules, only a line saying the server sets them:
the server plays by its own file, which never reaches your game, so change that one on the server
and `/reload`. There are no per-world rules files; the
one file in the config folder applies to every world.

Default file:

```json
{
  "format_version": 2,
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
    "durability_break": { "enabled": false, "mode": "set_to_1", "percent": 25 },
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
  "feedback": { "roll_visibility": "everyone", "flavor": { "enabled": true, "cooldown_ticks": 20, "kill_chance_percent": 25 } },
  "balance": { "global_damage_multiplier": 1.0, "disable_vanilla_armor_reduction": false, "armor_ac_divisor": 4, "toughness_ac_divisor": 4, "player_attack_bonus": 5 },
  "modifier_providers": { "enabled": true },
  "advantage_sources": { "jump_attack": true }
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

With dice on, a hit's rolled damage (and a failed save's) is at least 1, however negative the
modifier. Immunities, `global_damage_multiplier` and resistances apply after that floor.

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
- `enabled: false` turns Critfall's crits off and leaves crits to vanilla and other mods: a jump
  crit keeps its multiplier instead of becoming an advantage roll, a crit another mod makes (on
  NeoForge, through `CriticalHitEvent`) keeps its multiplier, and a hit Critfall rolls with profile
  dice deals that multiplier on top of its dice, and the readout shows it (`1d8 = 7 ×1.5`). Dice
  derived from the vanilla amount already carry it. Natural 20s are then plain hits (they still auto-hit while `nat20_always_hits` is on).

## fumbles — with real-time frequency safeguards

Tabletop assumes 2–3 attack rolls a minute; Minecraft melee makes 30+, so a raw 5% nat-1
consequence would fire constantly (PLAN.md §9.1). Three safeguards ship **on by default**:

- `confirmation_roll` — a nat 1 only triggers consequences if a second d20 rolls **below** `dc`
  (default 10, valid 2–20). Roughly halves fumble frequency; a saved fumble is a plain miss.
- `cooldown_ticks` — after a triggered fumble, further nat 1s by the same attacker are plain
  misses for this long (default 200 = 10s; 0 disables the cooldown).
- `durability_break.mode` — what a fumble does to the weapon once `durability_break.enabled` is
  on (off by default since 0.2.17): `set_to_1` drops the weapon to 1 remaining durability (dramatic);
  `percent_loss` removes `percent`% of max durability per fumble instead, so repeated fumbles
  wear gear down rather than trashing it. The PLAN.md shorthand `"mode": "percent_loss:25"` is
  also accepted. Neither mode ever fully breaks the weapon mid-swing.
- `nat1_always_misses: false` lets a high attack bonus land even on a natural 1 (house rule).

A **confirmed** fumble fires the attacker's fumble outcome table (held item profile first, then
entity profile — see docs/datapack-formats.md). Each consequence the table can pick is gated here
individually; a disabled consequence is a no-op when picked, leaving the rest of the table's odds
untouched:

- `durability_break` — gates `critfall:damage_durability` and supplies its mode/percent. **Off by
  default** since 0.2.17: losing an enchanted weapon to one unlucky roll is opt-in. Configs written
  by an earlier version say `"enabled": true` and keep it; a file that leaves the key out gets the
  new default, off. Unbreakable items are never affected.
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
dice) follows the same `disable_vanilla_armor_reduction` flag as attack rolls: reduced by vanilla
armor by default, or not at all when the flag is on.

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
  - `kill_chance_percent` (default `25`, 0–100, since 0.2.17) — how likely an ordinary killing blow
    is to get a line. A named mob (a name tag, or a mod's named elites and bosses, such as Critfall:
    Depths gives its own) and any boss in `#c:bosses` (the Ender Dragon and the Wither, plus whatever
    packs add) always get one. Before 0.2.17 every kill off cooldown got a line. The chance is a
    cosmetic roll, separate from the combat dice.

`sounds` / `particles` are **no longer server settings** — they moved to the client config (rendering
is client-side). If present in an old `rules.json` they are ignored with a one-line warning.

## balance

- `global_damage_multiplier` — scales all rolled damage (misses already cut DPS ~40%; packs tuned
  around vanilla time-to-kill may want > 1).
- `disable_vanilla_armor_reduction` (default `false` since 0.2.18) — `true` skips vanilla armor
  reduction for rolled damage, so armor only raises AC (enchantments, Resistance and absorption still
  apply). Off, armor raises AC a little and reduces hits like vanilla — see docs/design-decisions.md.
- `armor_ac_divisor` (1–100, default `4`) and `toughness_ac_divisor` (1–100, default `4`, since 0.2.18) —
  a derived Armor Class is `10 + floor(armor / armor_ac_divisor) + floor(toughness /
  toughness_ac_divisor)`: full leather 11, iron 13, diamond 17, netherite 18. Profiles with an
  `armor_class` are unaffected; add-ons can read the parts with `RollService.armorClassBreakdown`.

### format_version and the 0.2.18 migration

`format_version` is 2 since 0.2.18. A file below it (1, or none) was written before 0.2.18, when
`disable_vanilla_armor_reduction: true` was simply the shipped default. The first load moves it to the
new armor rule once: the flag is turned off, `armor_ac_divisor` and `toughness_ac_divisor` are written in
where the file names none, `format_version` becomes 2, and the server logs one line saying so. Nothing
else in the file changes. The config screen shows such a file the same way before the server has loaded
it. Turning the flag back on afterwards stays: a version 2 file is never migrated.
- `player_attack_bonus` (−20–20, default `5`, since 0.2.18) — added to a player's attack bonus, which
  otherwise derives from the weapon's damage (`floor(attack_damage / 2)`: +3 for an iron sword, so +8).
  Without it common weapons needed up to 2× vanilla's swings to kill a mob; with it about 1.1×.

## modifier_providers

- `enabled` (default `true`): whether a [modifier provider](api.md#modifier-provider) registered by
  another mod is asked for attack, damage, save and AC modifiers. `false` ignores it, so every roll uses
  Critfall's own bonus and AC. Without a provider this flag changes nothing.

## advantage_sources

- `jump_attack` (default `true`): a player's melee hit that vanilla would score as a critical hit
  (falling, not on the ground, fully charged, not sprinting, climbing, swimming, blind or riding)
  rolls its attack with advantage instead. Vanilla's 1.5× crit damage is dropped from a hit Critfall
  rolls, so damage stays dice-based and only a natural 20 crits. The readout shows both d20s, as for
  any advantage roll. Mob attacks and attacks driven through `RollService` never get it.
  - It combines with other sources the 5e way: a `PreAttackRollEvent` listener that sets
    disadvantage on a jump attack makes the roll normal. The listener sees the advantage as the
    event's mode; setting `NORMAL` clears it.
  - A jump hit Critfall leaves vanilla (`attack_rolls.players: false`, a vanilla passthrough
    fallback, a suppressed participant) keeps vanilla's crit multiplier. Under `dry_run` the roll
    shows the advantage but the vanilla damage, multiplier included, still applies.
  - `false` leaves jump hits as before 0.2.9: a normal roll, with the vanilla multiplier in the
    vanilla amount.
  - Only while `crits.enabled` is on: with Critfall's crits off, jump crits are vanilla's again.

The other sources sketched in PLAN.md (`attack_from_behind`, `target_blinded`, `sneak_attack`,
`low_attack_cooldown_disadvantage`) are recognised but not implemented yet; setting them only
logs a warning.
