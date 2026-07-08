# Mod compatibility notes

How Critfall's damage classification interacts with popular mods, and what pack devs need to
tag or profile. The Iron's Spells and Ars Nouveau sections are verified in-game against the
versions noted: `SpellModCompatGameTests` (neoforge `gametest` source set) loads the real jars
via `localRuntime` and drives damage through each mod's own damage-source factories —
`./gradlew :neoforge:runGameTestServer` re-runs the whole suite. Findings can still drift on
mod updates; bump the pinned versions in `gradle.properties` and re-run to re-verify.

## How spell classification works (M5)

`DamageClassifier` decides per damage event, in order:

1. `#critfall:exempt` → vanilla passthrough
2. `#critfall:always_hits` → no to-hit roll, damage dice only
3. **`#critfall:spell` → SPELL** (checked BEFORE the projectile/melee heuristics — see below)
4. `#minecraft:is_projectile` → PROJECTILE
5. no living attacker → vanilla passthrough
6. direct living attacker → MELEE; indirect living attacker → SPELL

SPELL damage resolves through **spell profiles** (`data/<ns>/critfall/spell_profile/*.json`,
matched by damage type id/tag): attack roll by default, or a saving throw. With no matching
profile, `fallbacks.unknown_spell` decides (derive = attack roll with dice derived from the
vanilla amount; `vanilla_passthrough` = leave it alone).

The shipped `#critfall:spell` tag pulls in `#neoforge:is_magic` (and `#c:is_magic`, plus each
mod's concrete damage types, all as optional entries) — so any mod that tags its damage types
into the NeoForge convention tag classifies as SPELL with **zero Critfall-specific config**.

Why the spell tag must beat the melee heuristic: both major spell mods construct many damage
sources with **the caster as both direct and causing entity** (`isDirect() == true`), which
without the tag would classify self-cast/touch spell damage as MELEE and roll it with the
caster's held-weapon dice.

## Iron's Spells 'n Spellbooks

Verified in-game against `irons_spellbooks` **1.21.1-3.16.2** (NeoForge 1.21.1, GeckoLib 4.9.2,
Iron's Lib 1.21.1-2.1.0): self-cast sources classify as SPELL and roll a d20, school-tag save
profiles resolve as saving throws (half on success), `fire_field` ticks stay vanilla, and
`attack_rolls.spells: false` restores vanilla for spell damage while melee rolls stay on.

- **Damage types** (all registered under `irons_spellbooks:`): nine school types — `fire_magic`,
  `ice_magic`, `lightning_magic`, `holy_magic`, `ender_magic`, `blood_magic`, `evocation_magic`,
  `eldritch_magic`, `nature_magic` — plus five entity/effect types: `fire_field`, `poison_cloud`,
  `dragon_breath_pool`, `blood_cauldron`, `heartstop`.
- **Tags:** every school type is in a per-school tag (`#irons_spellbooks:fire_magic` …) and all
  nine school tags are members of `#neoforge:is_magic`. The five entity/effect types are NOT in
  `is_magic`.
- **Source construction** (`SpellDamageSource extends DamageSource`): self/touch spells use
  `source(entity, spell)` → caster is direct AND causing entity; projectile spells use
  `source(projectile, caster, spell)` → indirect. Their types are never tagged
  `#minecraft:is_projectile`, so everything routes uniformly through the spell path.
- **What Critfall does out of the box:** the nine school types classify as SPELL via
  `#neoforge:is_magic`; the five ground-AoE/DoT types (`fire_field`, `poison_cloud`,
  `dragon_breath_pool`, `blood_cauldron`, `heartstop`) tick repeatedly and ship in
  `#critfall:exempt` — they must not roll every tick.
- **Caveats:**
  - Iron's applies its own school-resistance attribute multiplier *before* `hurt()`; when a
    Critfall spell profile supplies damage dice those replace the pre-multiplied amount, so
    their resistance attribute stops mattering for dice-profiled schools. Prefer Critfall
    `damage_modifiers` on the target for resistances, or leave `damage` out of the spell profile
    (derived dice track their scaled amount).
  - Summon minions (`IMagicSummon`) attack with regular mob melee — those roll as MELEE, which
    is correct.

Example datapack tuning (fire-school spells feel like a DEX save, other schools stay attack
rolls). Watch the school assignments, not the spell names: as of 3.16.x **Fireball deals
`evocation_magic`, not `fire_magic`** — profile the school tag a spell actually deals.

```json
// data/mypack/critfall/spell_profile/irons_fire.json
{
  "format_version": 1,
  "matches": ["#irons_spellbooks:fire_magic"],
  "resolution": "save",
  "save": { "dc": 14, "on_success": "half" }
}
```

## Ars Nouveau

Verified in-game against `ars_nouveau` **5.12.1** (NeoForge 1.21.1, GeckoLib 4.9.2, Curios
9.5.1): self-cast `DamageUtil` sources classify as SPELL and roll a d20, an `ars_nouveau:spell`
save profile resolves as a saving throw (half on success), `sourceberry_bush` stays vanilla,
casterless sources pass through vanilla, and `attack_rolls.spells: false` restores vanilla.

- **Damage types:** `ars_nouveau:spell` (generic spell damage — most damage effects),
  `ars_nouveau:flare`, `ars_nouveau:frost` (cold snap), `ars_nouveau:windshear`,
  `ars_nouveau:crush`, `ars_nouveau:sourceberry_bush`.
- **Tags:** all except `sourceberry_bush` are members of `#neoforge:is_magic` (as optional
  entries in their datagen). `crush` and `windshear` are additionally in
  `#minecraft:bypasses_armor`; `flare` is in `#minecraft:is_fire`, `frost` in
  `#minecraft:is_freezing`.
- **Source construction** (`DamageUtil.source(...)`, inner `SpellDamageSource`): with one entity
  argument the caster is direct AND causing (`isDirect() == true`); the two-entity overload is
  indirect; the no-entity overload has **no attacker at all** — Critfall passes those through
  vanilla (nothing to roll for).
- **What Critfall does out of the box:** the five magic types classify as SPELL via
  `#neoforge:is_magic`; `sourceberry_bush` (a berry-bush hazard) ships in `#critfall:exempt`.
- **Caveats:** Ars spell damage scales with amplifiers/enchanter tools at cast time; a fixed
  `damage` in a spell profile flattens that scaling. Leaving `damage` out keeps derived dice
  matched to the cast's actual amount while still applying the to-hit/save layer.

## Both mods: what a pack dev still decides

- Whether each school/type should be an **attack roll** (single-target bolt feel) or a **save**
  (AoE feel) — ship taste, not derivable. See the example above; `#critfall:spell` itself is a
  valid `matches` entry for a catch-all profile.
- Save DCs. The rules.json default (`spells.saves.default_dc`, 13) applies when a profile names
  none.
- Any spell types a pack adds via other addons: tag them into `#critfall:spell` (or
  `#neoforge:is_magic`) and, if they tick, into `#critfall:exempt`.

## Vanilla/NeoForge overlap worth knowing

- `#neoforge:is_magic` also contains vanilla `magic`, `indirect_magic`, `thorns`,
  `dragon_breath`, and (via nested tags) poison/wither DoT types. All of those are already in
  `#critfall:exempt`, and exempt wins first — potions, thorns, and DoTs stay vanilla even
  though they are nominally "magic".
- NeoForge remaps poison potion damage to its own `neoforge:poison` type; `#critfall:exempt`
  includes `#neoforge:is_poison` and `#neoforge:is_wither` (optional) so effect ticks never roll.
