# Loader parity (NeoForge / Fabric)

Critfall targets NeoForge 1.21.1 (primary) and Fabric 1.21.1. **All gameplay is identical on both
loaders** — the entire combat pipeline lives in the loader-agnostic `common` module, and the full
57-test GameTest suite runs from the *same* shared test bodies (`common/src/gametest`) on both. This
page documents the two places where the loader *plumbing* necessarily differs, and confirms neither
changes observable behaviour.

## What is shared

Everything that decides an outcome: dice engine, profile resolution and fallback derivation, the
`AttackPipeline`, outcome-table executor, rules config, feedback payloads, and the whole
`DamageInterception` orchestration (classify → resolve → roll → apply → feedback). Each loader only
adapts its native hooks to these shared classes.

## Difference 1 — where damage is intercepted

Both loaders run `DamageInterception.handle(...)` at the same logical point: **after the
invulnerability / i-frame checks, before mitigation.** Only the hook differs.

| | NeoForge | Fabric |
|---|---|---|
| Hook | `LivingIncomingDamageEvent` (a first-class cancel/modify event) | `ServerLivingEntityEvents.ALLOW_DAMAGE` for the roll + cancel, plus a mixin on `LivingEntity` for amount/armor |
| Cancel (MISS/FUMBLE) | `event.setCanceled(true)` | `ALLOW_DAMAGE` returns `false` |
| Replace amount (HIT/CRIT) | `event.setAmount(rolled)` | `@ModifyVariable` on `actuallyHurt`'s amount |

Fabric has no event that can *modify* the incoming amount (`ALLOW_DAMAGE` is cancel-only), so a
mixin is required — exactly as PLAN §4.1 anticipated, and the only mixin in the mod. It is kept
minimal (amount substitution + armor bypass) and documented in `LivingEntityMixin`. The pipeline
still runs exactly once, in `ALLOW_DAMAGE`, which fabric-api injects at the `isSleeping()` call in
`hurt` — verified to sit after the invulnerability checks and before `actuallyHurt`, matching the
NeoForge event's position.

## Difference 2 — armor bypass mechanism

Rolled damage bypasses vanilla **base-armor** reduction (AC already represents armor — see
`docs/design-decisions.md`). Enchantment protection, the Resistance effect, and absorption still
apply on both loaders.

| NeoForge | Fabric |
|---|---|
| `event.addReductionModifier(DamageContainer.Reduction.ARMOR, (c, r) -> 0f)` | `@Inject` on `getDamageAfterArmorAbsorb` returns the input unchanged when the roll asked to bypass |

Both zero out the same reduction stage. The GameTest `rolledDamageBypassesVanillaArmorReduction`
(diamond-chestplate husk takes the full 7-damage crit) and `armorReductionAppliesWhenBypassFlagIsOff`
(armor reduces when the flag is off) pass on both loaders, proving equivalence.

## Known edge cases (not exercised by vanilla combat)

- **Rapid re-hits inside i-frames:** vanilla's partial-damage path (`amount <= lastHurt`) compares the
  *vanilla* amount on Fabric (the roll's amount is substituted only at `actuallyHurt`), whereas the
  NeoForge event feeds the modified amount into that comparison. This only differs for a second HIT
  landing within another entity's active i-frames within the same tick — not produced by normal
  attack cadence, and not by any GameTest.
- **Shield blocking:** on Fabric a rolled hit is applied at `actuallyHurt` after vanilla's block
  zeroing; Critfall does not currently special-case shields on either loader. Pack devs who want
  shields to matter should exempt or tune accordingly.

Neither edge case affects the shipped mechanics or the test suite; both are noted for completeness.

## Build differences

- NeoForge builds with ModDevGradle (NeoForm, Mojang official mappings). Fabric builds with Fabric
  Loom + `officialMojangMappings()` — the same names, so `common`'s compiled classes are bundled and
  remapped into the Fabric jar unchanged.
- The shared GameTest bodies compile against unmodified NeoForm, so they use only vanilla-public APIs
  (e.g. the indirect spell `DamageSource` is built via the public constructor, since
  `DamageSources.source(...)` is public only under NeoForge's access transformer).
