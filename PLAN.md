# Project Plan: "Critfall" — Data-Driven d20 Combat for Minecraft

> Working title — pick any original name. Avoid "D&D"/"Dungeons & Dragons" in the mod name and assets (WotC trademarks). The *mechanics* (d20 vs AC, crits) are not copyrightable, and SRD 5.2 content is CC-BY-4.0 if you ever want to reference it.

## 1. Vision

An open-source mod that replaces Minecraft's damage system with tabletop-style dice resolution:

- Every damaging action (melee, ranged, spells, mob attacks) triggers an **attack roll**: `d20 + modifiers` vs the target's **Armor Class (AC)**.
- Miss → no damage. Hit → damage is rolled from **damage dice** (e.g. `2d6+3`) instead of vanilla flat values.
- **Nat 1 (fumble)**: configurable consequences — weapon durability drops to 1, hit redirects to nearest ally/player in range, self-damage, drop weapon, etc.
- **Nat 20 (crit)**: max damage, double dice, or double total (configurable).
- **Everything is data-driven** (datapacks + configs) so any modpack dev can define AC/dice for *any* mob, boss, weapon, or spell from *any* mod — without writing code.
- **Graceful fallbacks** so the mod works out-of-the-box in a 300-mod pack with zero configuration.

Target audience: modpack developers first, players second, mod developers (API consumers) third.

## 2. Core design principles

1. **Data over code.** All balance lives in datapack JSON, reloadable with `/reload`. Code only implements mechanics.
2. **Never crash, always fall back.** Unknown entity → derive AC from its armor attribute. Unknown weapon → derive dice from its attack damage attribute. Unknown damage source → configurable default (roll or pass through).
3. **Tag-driven compatibility.** Use entity type tags, item tags, and damage type tags as the primary matching mechanism. This is how packs already customize things.
4. **Server-authoritative.** All rolls happen server-side. Client only renders feedback (roll popups, sounds).
5. **API-first.** Internal systems consume the same public API exposed to other mods and KubeJS.

## 3. Platform & versions

- **Language/stack:** Java 21, NeoForge **1.21.1** as primary target (current modpack standard-bearer for new packs).
- **Multiloader from day one** using a multiloader template (common/neoforge/fabric source sets) — Fabric support matters for adoption. Skip old Forge 1.20.1 initially; backport later only if there's demand (it's a big maintenance cost).
- **Build:** Gradle, ModDevGradle/Loom, GitHub Actions CI, publish to Modrinth + CurseForge via `mc-publish` action.

## 4. Architecture

### 4.1 The damage pipeline interception

Hook the earliest damage event on each loader:
- NeoForge: `LivingIncomingDamageEvent` (cancel/modify)
- Fabric: `ServerLivingEntityEvents.ALLOW_DAMAGE` + damage amount mixin (or the `EntityDamageEvents` from Fabric API where available)

Flow per damage event:

```
incoming damage
  → classify DamageSource (melee / projectile / spell / environmental / DoT / true)
  → exempt? (fall, void, /kill, starve, tick-based DoT…) → pass through vanilla
  → resolve attacker profile (attack bonus, damage dice, crit range, fumble table)
  → resolve defender profile (AC, resistances/immunities/vulnerabilities by damage type)
  → ROLL: d20 (+ advantage/disadvantage) + attack bonus vs AC
      nat 1  → fumble handler chain
      miss   → cancel damage, feedback
      hit    → roll damage dice (+ modifiers), apply
      nat 20 → crit rule (max / double dice / double total)
  → fire post-roll API event (mods can veto/modify)
  → apply final damage, broadcast feedback packet
```

Critical detail — **DoT and environmental damage must not roll every tick**. Use a damage type tag `#critfall:exempt` (pre-populated: fall, drown, starve, cactus, fire tick, poison, wither, magic from potions…) and `#critfall:always_hits` for things that should skip the to-hit but still roll dice for damage.

### 4.2 Data formats (datapack registries)

**Entity stat profiles** — `data/<ns>/critfall/entity_profile/*.json`
```json
{
  "matches": ["minecraft:enderman"],          // or "#minecraft:undead", or "#critfall:bosses"
  "armor_class": 14,
  "attack_bonus": 4,
  "damage": { "melee": "2d6+3" },
  "crit_range": 20,
  "damage_modifiers": {
    "resist": ["#critfall:physical"],
    "immune": [],
    "vulnerable": ["minecraft:fire"]
  },
  "priority": 10                                // higher wins when multiple match
}
```

**Weapon/item profiles** — `data/<ns>/critfall/item_profile/*.json`
```json
{
  "matches": ["#minecraft:swords"],
  "damage": "1d8",
  "modifier_from": "attack_damage_attribute",   // scale dice bonus from the item's real stats
  "crit_range": 20,
  "fumble_table": "critfall:default_melee",
  "properties": ["finesse"]
}
```

**Fumble tables** — `data/<ns>/critfall/fumble_table/*.json`
Weighted list of effects: `damage_durability_to:1`, `hit_nearest_ally:{radius:4}`, `self_damage:"1d4"`, `drop_weapon`, `stumble:{slowness_ticks:40}`, `nothing`. Composable so pack devs make their own tables per weapon class or per boss.

**Spell/damage-type profiles** — match on damage type IDs/tags so Iron's Spells, Ars Nouveau, etc. work generically: a fire spell from any mod matched by `#minecraft:is_fire` gets `save`-style behavior if desired (optional v2 feature: DEX save for half instead of attack roll — AoE spells feel wrong with to-hit).

**Global ruleset config** — `config/critfall/rules.json`, per-world overridable, hot-reloadable. **Every mechanic is an individual feature flag** so pack devs (and players via server config) can turn off anything they dislike without touching the rest:

```json
{
  "format_version": 1,
  "attack_rolls":        { "enabled": true, "players": true, "mobs": true, "projectiles": true, "spells": true },
  "damage_dice":         { "enabled": true },
  "crits": {
    "enabled": true,
    "rule": "max_dice",                    // max_dice | double_dice | double_total
    "nat20_always_hits": true
  },
  "fumbles": {
    "enabled": true,
    "nat1_always_misses": true,
    "durability_break":  { "enabled": true, "set_durability_to": 1 },   // ← the "almost break weapon" toggle
    "hit_nearest_ally":  { "enabled": true, "radius": 4, "can_hit_players": true, "respect_pvp_rules": true },
    "self_damage":       { "enabled": false, "dice": "1d4" },
    "drop_weapon":       { "enabled": false },
    "stumble":           { "enabled": false, "slowness_ticks": 40 },
    "applies_to":        "players_and_mobs"  // players | mobs | players_and_mobs
  },
  "advantage_sources": {
    "attack_from_behind": true,
    "target_blinded": true,
    "sneak_attack": true,
    "low_attack_cooldown_disadvantage": true
  },
  "fallbacks": {
    "unknown_entity": "derive",             // derive | vanilla_passthrough
    "unknown_weapon": "derive"
  },
  "feedback": {
    "roll_visibility": "everyone",          // everyone | attacker_only | off
    "sounds": true, "particles": true
  },
  "balance": {
    "global_damage_multiplier": 1.0,
    "disable_vanilla_armor_reduction": true
  }
}
```

Rules for this config: any flag set to `false` must cleanly restore vanilla behavior for that mechanic only; unknown keys are ignored with a log warning (forward compat); `format_version` enables automatic migration. Fumble sub-toggles matter most — every fumble consequence is opt-in/out individually, and fumble tables in datapacks can additionally override per weapon class or per boss.

### 4.3 Fallback derivation (the "works in any modpack" secret sauce)

- **AC** = `10 + floor(armor_attribute / 2) + small bonus from armor_toughness`
- **Attack bonus** = scaled from attack damage attribute or entity category (boss tag → higher)
- **Damage dice** = map the entity/weapon's vanilla flat damage to the nearest dice expression with the same average (e.g. 7 dmg → `2d6`, avg 7). Ship a lookup table.
- Bosses (entities in `#c:bosses` or with boss bars) get elevated defaults automatically.

This must be rock-solid — it's what makes the mod droppable into RAD/ATM-style kitchen-sink packs.

### 4.4 Public API + KubeJS

- Java events: `PreAttackRollEvent` (modify bonus, force adv/disadv, cancel), `PostAttackRollEvent` (see result, modify damage), `FumbleEvent`, `CritEvent`.
- A `RollService` for other mods: `roll("2d6+3")`, `attackRoll(attacker, target, ctx)`.
- KubeJS plugin (separate optional module) exposing the same events so script devs get full control.
- Dice expression parser as its own well-tested package (`NdM+K`, advantage `2d20kh1`, keep-highest/lowest) — reusable, fuzz-tested.

### 4.5 Client feedback (its own module, fully optional)

- Action bar or floating text: `⚄ 17 + 4 = 21 vs AC 14 — HIT! 2d6+3 = 11`
- Dice roll sound, crit/fumble jingles, particles.
- Client config to reduce/disable (kitchen-sink packs hate HUD spam).
- All driven by one small S2C packet; server works fine without the client mod installed (feedback just off) — keep it working server-side-only for Fabric servers with vanilla clients if feasible.

## 5. Milestones (work order)

**M0 — Repo scaffold (½ day)**
Multiloader template, CI, LICENSE (MIT recommended for max adoption; LGPL if you want share-alike), README skeleton, CONTRIBUTING.md with project conventions, `.github` issue templates.

**M1 — Dice engine (1 day)**
Standalone dice expression parser + roller with seeded-RNG unit tests. No Minecraft dependencies. 100% test coverage here — everything sits on it.

**M2 — Damage interception MVP (2–3 days)**
NeoForge event hook, damage classification, exempt tags, hardcoded default profiles, d20 vs derived AC, hit/miss/crit(max dmg)/fumble(durability→1). Playable end-to-end in a test world.

**M3 — Datapack registries (2–3 days)**
JSON codecs for entity/item profiles + fumble tables, tag matching with priority resolution, `/reload` support, `/critfall inspect <entity>` and `/critfall check <item>` debug commands. Ship a default datapack covering all vanilla mobs + weapon classes.

**M4 — Fumble system + redirect-hit (1–2 days)**
Fumble table executor incl. "hit nearest ally/player within radius" (raycast/AABB search, respects PvP rules and team checks), self-damage, drop weapon.

**M5 — Projectiles & spells (2 days)**
Arrow/trident/thrown handling (roll on impact, dice from bow/crossbow profile + arrow), generic spell-damage classification via damage type tags, saving-throw option for AoE. Compat-test against Iron's Spells 'n Spellbooks and Ars Nouveau.

**M6 — Client feedback module (1–2 days)**
Packet + rendering + sounds + client config.

**M7 — API + KubeJS module (1–2 days)**
Public events, RollService, KubeJS bindings, example scripts in `/examples`.

**M8 — Fabric port (1–2 days)**
Wire common code to Fabric hooks; parity test checklist.

**M9 — Release engineering (1 day)**
Wiki/docs site (or `docs/` with mdBook), example datapack repo, Modrinth/CurseForge pages, versioning policy, CHANGELOG, publish pipeline.

## 6. Testing strategy

- **Unit:** dice parser, profile resolution/priority, AC derivation, fallback mapping (pure JVM, fast).
- **GameTest framework:** automated in-game tests — "skeleton with AC 13 takes 0 damage when forced roll is 12", "nat 1 sets durability to 1", "nat 20 maxes dice". Force rolls via a test-only RNG injection point (make the RNG injectable from day one!).
- **CI:** build + unit + GameTests on every PR, both loaders.
- **Manual compat matrix:** vanilla, Better Combat, Iron's Spells, Ars Nouveau, Epic Fight (document known incompatibilities honestly).

## 7. Open-source & community setup

- MIT license, clear CONTRIBUTING.md, code style enforced (spotless).
- Everything configurable = fewer forks needed. Document the JSON schemas thoroughly with a schema file for editor autocomplete.
- "Adopt me" docs page for modpack devs: 5-minute quickstart, copy-paste profile examples, FAQ ("why doesn't fire tick roll?").
- Semantic versioning with a stable data-format version field in every JSON so you can migrate formats later.
- Discord or GitHub Discussions for pack-dev support.

## 8. The 400-mod problem: what we automate vs what pack devs must do

### 8.1 What the mod does automatically (zero config)

- **Derived AC** from final armor + toughness attributes (reads post-modifier values, so Apotheosis-style affixes are respected).
- **Derived damage dice** from attack damage attributes (avg-matching lookup table).
- **Category heuristics:** `MobCategory` (monster/creature/water), `#minecraft:undead`, `#c:bosses`/boss-bar detection → elevated boss defaults, fire-immune flag → fire resistance.
- **Namespace-level matching:** profiles can match `"alexsmobs:*"` so one JSON tunes a whole mod's mobs at once.

### 8.2 Tooling we MUST ship for pack devs (this is the adoption strategy)

1. **`/critfall generate`** — scans every registered entity type and weapon-like item in the loaded pack and **dumps a complete, editable datapack** with the derived values pre-filled. Pack devs tune numbers in JSON instead of authoring 400 files from scratch. This single command is the difference between "usable" and "abandoned."
2. **Live tuning commands** — `/critfall set ac @e[type=alexsmobs:grizzly_bear] 13`, `/critfall set dice ...` — writes back into the generated datapack, hot-reloads.
3. **Dry-run mode** — rolls are computed and displayed but vanilla damage still applies. Lets devs calibrate a pack during normal playtesting without breaking it.
4. **Coverage report** — `/critfall report` exports CSV/JSON of every entity/item, whether it has an explicit profile or fallback, and the effective values. Reviewable in a spreadsheet.
5. **`/critfall inspect`** (look at entity/item, see effective profile + which JSON file/priority won) — essential for debugging tag-priority collisions.
6. **JSON schemas** shipped for editor autocomplete/validation.
7. **Community compat-data repo** — a separate CC0 repository in the org where pack devs PR profile packs for popular mods (Alex's Mobs, Cataclysm, Bosses of Mass Destruction…). Ships nothing in the mod jar; devs cherry-pick. This crowdsources the long tail we can't do ourselves.

### 8.3 What pack devs still have to do (be honest in the docs)

- **Balance intent:** fallbacks give *plausible* values, not *good* ones. A lich boss should be low-AC/high-save, a knight high-AC — identity like that isn't derivable from attributes. Expect a tuning pass on the ~30 mobs that matter.
- **Damage-source classification for magic mods:** if a spell mod fires generic damage without proper damage type tags, the pack dev must tag it (fire/necrotic/etc.) or exempt it. Ship examples for Iron's Spells and Ars Nouveau.
- **Exemption decisions:** should Create crushing wheels, Mekanism lasers, or bleed effects roll to hit? Almost certainly not — pack devs curate `#critfall:exempt` for their tech mods. Pre-populate common namespaces with sane defaults.
- **HP rebalancing:** misses cut effective DPS; packs tuned around vanilla TTK may want the global damage multiplier or mob HP tweaks (pairs well with Damage Control-style mods).
- **Special bosses:** phase-based invulnerability (Wither shield), multipart entities (Ender Dragon, Cataclysm bosses) may need per-part or per-phase overrides — expose hooks, document patterns, accept that a handful of bosses need hand-tuning or a compat-repo entry.

### 8.4 What we genuinely cannot fix

- Mods that **bypass the damage event** (direct `setHealth()` or custom damage pipelines) never reach our hook — document known offenders, offer mixin-based compat modules case-by-case.
- Mods that implement their own hit detection/combat loops (Epic Fight deep integration) — we're downstream of damage only.
- Client visuals of other mods showing vanilla damage numbers — their problem, not ours.

## 9. Known hard problems (decide early)

1. **Attack speed vs turn-based feel:** Minecraft is real-time; spam-clicking = many rolls. Mitigate via vanilla attack cooldown scaling the attack bonus (low cooldown → disadvantage) — elegant and configurable.
2. **Mob-on-mob combat volume:** rolling for every zombie-vs-villager hit is fine perf-wise but consider a config to restrict rolls to player-involved combat only.
3. **Better Combat / Epic Fight:** they replace attack logic; you intercept damage, so you're mostly downstream of them — verify sweep attacks produce one roll per target.
4. **Shields & armor double-dipping:** if AC already represents armor, vanilla armor reduction must be disabled for rolled damage (apply damage as armor-bypassing, or zero out reduction) — otherwise armor counts twice. Make this explicit in the design.
5. **Health scaling:** d20 misses drop DPS ~40%+; packs may need mob HP tuning. Ship guidance + optional global damage multiplier.

## 10. Project ground rules

- Java 21, NeoForge 1.21.1 primary, multiloader layout `common/ neoforge/ fabric/`
- All gameplay values come from datapack JSON — never hardcode balance in Java
- RNG must be injectable everywhere (testing)
- Every new mechanic: unit test or GameTest before merge
- Public API in `studio.modroll.critfall.api` — never break without major version bump
- Run `./gradlew check` before considering any task done
