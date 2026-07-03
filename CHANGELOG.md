# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Multiloader project scaffold: `common` + `neoforge` modules targeting NeoForge 1.21.1 (Java 21, ModDevGradle), Spotless formatting, GitHub Actions CI. (M0)
- Dice engine (`studio.modroll.critfall.dice`): expression parser and roller supporting `NdM+K`, keep-highest/lowest (`kh`/`kl`), advantage/disadvantage, multi-term expressions, min/max bounds, per-die roll breakdown, and fully injectable RNG. No Minecraft dependencies. See `docs/dice-expressions.md`. (M1)
- Damage pipeline interception on NeoForge: melee attacks now make a d20 attack roll vs the target's derived Armor Class instead of always hitting. Miss cancels all damage; hit rolls damage dice derived from the vanilla amount; nat 20 crits for maximized dice; nat 1 fumbles and drops the attacker's weapon to 1 durability. Rolled damage bypasses vanilla armor reduction (see `docs/design-decisions.md`). Action-bar roll feedback for players. All mechanics individually flag-toggleable (hardcoded defaults until M3). (M2)
- Damage-type tags `#critfall:exempt` (pre-populated: DoT, environmental, AoE) and `#critfall:always_hits` for pack devs to steer what gets rolled. (M2)
- In-game GameTest suite (`runGameTestServer`) covering miss/hit/crit/fumble/armor-bypass with scripted RNG. (M2)
- Datapack registries: entity profiles (`data/<ns>/critfall/entity_profile/`), item profiles (`item_profile/`), and outcome tables (`outcome_table/`) with `/reload` support, per-file error isolation, unknown-key warnings, and `format_version` checks. Matching by exact id, `#tag`, or `namespace:*` wildcard with priority > specificity > file-id resolution. See `docs/datapack-formats.md`. (M3)
- Default datapack covering every vanilla mob (46 entity profiles) and the vanilla melee weapon classes (swords/axes/pickaxes/shovels/hoes/trident/mace), plus `critfall:default_melee` / `critfall:default_crit` outcome tables (executor lands in M4). (M3)
- `config/critfall/rules.json`: full feature-flag config, written with defaults on first launch, hot-reloaded on `/reload`. Includes crit rule selection (`max_dice`/`double_dice`/`double_total`) and fumble frequency safeguards — confirmation roll (on by default, DC 10), 10s fumble cooldown, and `set_to_1` vs `percent_loss` durability modes. Every flag off = vanilla behavior back, proven by GameTests. See `docs/rules-config.md`. (M3)
- Debug commands `/critfall inspect <entity>` and `/critfall check [<item>]` showing effective stats and which profile file won. (M3)
- Item-profile damage bonus derivation from the attacker's real attack-damage attribute, entity resist/immune/vulnerable damage modifiers, per-profile crit ranges, and `fallbacks.unknown_entity`/`unknown_weapon` passthrough modes. (M3)
- Outcome table executor: one generic system maps triggers (`nat_1`, `nat_20`, `miss_by_at_least`, `roll_range`) to weighted effect lists — fumble consequences and crit effects are the same mechanism. Effects: `damage_durability`, `hit_nearest_ally` (redirects the swing to the nearest bystander; respects PvP setting, team friendly-fire, and a `can_hit_players` policy), `self_damage`, `drop_weapon`, `stumble`, `apply_effect` (nat-20 "shot in the eye"), `knockback`, `nothing`. Every consequence is individually toggleable in `rules.json` (a disabled effect is a no-op when picked, preserving the other odds), with per-table parameter overrides. Entity profiles can now reference `fumble_table`/`crit_table` too (held-item tables win). Unknown effect types are skipped with a warning. (M4)
- `fumbles.applies_to` (`players`/`mobs`/`players_and_mobs`) gates whose nat 1s can fumble at all; `crits.apply_effect`/`crits.knockback` toggles. Default datapack tables now showcase `hit_nearest_ally` (fumble) and `knockback` (crit). (M4)
- `/critfall inspect` argument is now optional — with no argument it inspects the entity under your crosshair (32-block raycast, blocks occlude). (M4)
- GameTests now execute `/critfall` through the real command dispatcher (registration coverage), plus forced nat 1/nat 20 tests for every outcome effect, PvP/team redirect policy, `applies_to`, per-effect toggles, and a `miss_by_at_least` table. (M4)
- `critfall:default_unarmed` outcome table wired into the barehanded melee mob profiles (zombies, spiders, slimes, endermites, hoglins, endermen, ravagers, phantoms) — playtesting showed weaponless mobs had no fumble table at all, so hordes never fumbled into each other. Regression GameTest included. (M4)
- Debug-level audit logging (`logs/debug.log`) whenever an outcome table fires: which table, what it picked, whether rules.json gated it, and who a `hit_nearest_ally` swing redirected into (or that nobody was in range). (M4)
