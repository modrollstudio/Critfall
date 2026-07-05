# Critfall documentation

Tabletop-style d20 combat for Minecraft — data-driven attack rolls vs Armor Class, damage dice,
crits, and fumbles, for NeoForge and Fabric 1.21.1.

## Start here

- [Quickstart](quickstart.md) — the 5-minute "adopt me" path for modpack developers.
- [FAQ](faq.md) — why fire tick doesn't roll, spongey mobs, armor double-dipping, and more.
- [Commands & calibration](commands.md) — `generate`, `report`, `inspect`, `check`, and dry-run mode.
- [Presets](presets.md) — Tempered, Classic, and Lite starting configs.

## Configuring

- [Rules config](rules-config.md) — every `rules.json` feature flag.
- [Datapack formats](datapack-formats.md) — entity/item/spell profiles, outcome tables, flavor pools.
- [Client feedback](client-feedback.md) — the client config and how roll/flavor/sound/particle
  rendering works.

## Reference

- [Dice expressions](dice-expressions.md) — the `NdM+K`, keep-highest/lowest, advantage syntax.
- [Public API](api.md) — `RollService`, events, and suppression for other mods and KubeJS.
- [Compatibility](compat.md) — spell-mod classification research and tuning guidance.
- [Design decisions](design-decisions.md) — the armor/mitigation decision and its rationale.
- [Loader parity](loader-parity.md) — how NeoForge and Fabric behavior is kept identical.

## Examples

- [Example datapack](../examples/datapack/) — a tuned boss, a custom blade, a fumble table, and a
  flavor pool.
- [Presets](../examples/presets/) — the three ready-to-copy `rules.json` files.
- [KubeJS scripts](../examples/kubejs/) — driving the API from scripts.
