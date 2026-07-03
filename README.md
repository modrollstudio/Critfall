# Critfall

Tabletop-style d20 combat for Minecraft. Every hit rolls to attack against the target's Armor Class; damage comes from dice, nat 20s crit, nat 1s fumble — and every value and mechanic is data-driven so modpack developers can tune it for any mob, boss, weapon, or spell from any mod.

> ⚠️ Early development. Not yet released.

## For players
- Attacks roll `d20 + bonus` vs the target's AC. Miss = no damage.
- Damage is rolled from dice (e.g. `2d6+3`) instead of flat values.
- Nat 20: critical hit. Nat 1: fumble — configurable consequences.
- Every mechanic can be toggled off individually by the server/pack.

## For modpack developers
- Works out of the box in any pack: unknown mobs/weapons get derived stats from their attributes.
- `/critfall generate` dumps a complete editable datapack for every mob and weapon in your pack.
- Dry-run mode, live tuning commands, coverage reports, JSON schemas.
- See `docs/` (coming with M3) and PLAN.md for the full design.

## For mod developers
- Java API in `studio.modroll.critfall.api`: pre/post roll events, `RollService`, dice expression parser.
- Optional KubeJS bindings.

## Building
```
./gradlew build
```
Requires Java 21.

## Contributing
See CONTRIBUTING.md. Compat data for other mods lives in the community data repo (link TBD).

## License
MIT — see LICENSE.
