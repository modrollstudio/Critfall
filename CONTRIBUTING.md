# Contributing to Critfall

Thanks for your interest! Critfall is MIT-licensed and contributions are welcome.

## Development setup

1. Install a **JDK 21** (Temurin recommended). Gradle's toolchain support will auto-provision one if missing.
2. Clone the repo and run:

   ```
   ./gradlew check
   ```

   The first run downloads Minecraft and NeoForge artifacts — it takes a while. Subsequent runs are fast.
3. To launch a development client: `./gradlew :neoforge:runClient`
4. To run the in-game GameTest suite headlessly: `./gradlew :neoforge:runGameTestServer`

## Project layout

| Module      | Contents                                                                    |
| ----------- | --------------------------------------------------------------------------- |
| `common/`   | Loader-agnostic code (game logic, dice engine, data formats). No loader APIs. |
| `neoforge/` | NeoForge entrypoint, event hooks, loader-specific wiring.                    |
| `fabric/`   | Fabric entrypoint and hooks. Keep `common/` loader-agnostic so it stays cheap. |

The GameTest suite lives once in `common/src/gametest` and runs on both loaders:
`./gradlew :neoforge:runGameTestServer` and `./gradlew :fabric:runGametest`.

## Ground rules

These are enforced in review (see `PLAN.md` for the full spec):

- **Data over code.** All gameplay/balance values come from datapack JSON or the rules config — never hardcode balance numbers in Java.
- **Injectable RNG.** No direct `new Random()` or `level.random` in game logic. Tests must be able to force a nat 1 or a nat 20.
- **Tests required.** Every mechanic gets a unit test (pure JVM) or GameTest before it's considered done.
- **API stability.** Public API lives in `studio.modroll.critfall.api`. Never break it without a major version bump.
- **Feature flags.** Every mechanic must be individually toggleable via `rules.json`; turning a flag off must cleanly restore vanilla behavior for that mechanic only.
- **Server-authoritative.** All rolls happen server-side; the client module is feedback-only and optional.

## Versioning

- The mod follows [Semantic Versioning](https://semver.org/). Breaking changes to the public API
  (`studio.modroll.critfall.api`) require a **major** version bump — never break it in a minor or patch.
- Every config and datapack JSON carries a `format_version`. When a format changes incompatibly,
  bump its `format_version` and add a migration so old files keep loading; unknown keys always warn
  rather than crash, so additive changes are safe.
- Releases are cut by tagging `vX.Y.Z` (matching `mod_version` in `gradle.properties`) and publishing
  a GitHub release — the `Publish` workflow then uploads to Modrinth and CurseForge. Move the
  `CHANGELOG.md` `[Unreleased]` section under the new version with the release date.

## Code style

Formatting is enforced by [Spotless](https://github.com/diffplug/spotless) (Palantir Java Format).

```
./gradlew spotlessApply   # fix formatting
./gradlew check           # verify build + tests + formatting
```

CI runs `./gradlew check` on every push and PR — it must pass before merge.

## Pull requests

- Branch from `main`, keep PRs focused on one change.
- Add a `CHANGELOG.md` entry under **Unreleased** for user-visible changes.
- Document any new JSON format in `docs/` with an example file.
- Describe *why*, not just *what*, in the PR description.

## Reporting bugs

Use the issue templates. For modpack compatibility issues, always include the mod/modpack name and version, the loader, and a `latest.log`.
