# Critfall 0.2 pre-release audit — performance, memory, concurrency, robustness

Audited on branch `audit/pre-release` (off `main`, post-#22). Scope: the damage pipeline, per-entity
state, datapack/config parsing, network payloads, commands, and file IO across `common/`,
`neoforge/`, and `fabric/`. Findings are graded Critical / High / Medium / Low. Every fix applied on
this branch is justified by a finding below and carries a regression test; behavior/balance is
unchanged.

Line numbers reference the tree as audited (pre-fix); files touched by fixes shift slightly after.

---

## A. Memory leaks / unbounded growth

### A1 — HIGH: `FumbleCooldowns` grows unbounded and is never cleared in production
`common/src/main/java/studio/modroll/critfall/combat/FumbleCooldowns.java:15`

Static `ConcurrentHashMap<UUID, Long>`, one entry per attacker that ever triggered a fumble.
The doc comment ("a few stale entries per dead mob are harmless, so there is no eviction") holds for
short sessions but not for a long-running server: mob-vs-mob combat fumbles constantly, and each
transient zombie/skeleton that fumbles leaves a permanent entry. `clear()` exists but is called
**only from tests and GameTests** — no server-lifecycle hook calls it (verified by grep; only
`CombatSuppression.clear()` is wired to SERVER_STOPPING on both loaders).

Second-order bug: the map is keyed by UUID against `level.getGameTime()`, both of which outlive a
world in the same JVM. In singleplayer, leaving world A (game time 1,000,000) and opening a fresh
world B (game time 0) leaves the player's UUID mapped to a *future* timestamp — `gameTime - last`
goes negative, which is `< cooldownTicks`, so the player is treated as **permanently on fumble
cooldown** (no fumble consequences) until world B's clock passes world A's.

**Resolution:** fixed. (1) Entries are no longer recorded when the cooldown is disabled
(`cooldownTicks <= 0` — they were dead weight), (2) the map self-prunes expired entries once it
crosses a size threshold, (3) `isOnCooldown` treats a future-stamped entry as expired, and (4) both
loaders clear the map on server stop alongside `CombatSuppression`. Regression tests in
`FumbleCooldownsTest`.

### A2 — HIGH: `FlavorCooldowns` — same defect, faster growth
`common/src/main/java/studio/modroll/critfall/feedback/FlavorCooldowns.java:15`

Same pattern as A1 but keyed by **target** UUID and recorded on every crit/fumble/kill that produced
a flavor line — on a server with players grinding mob farms this accrues an entry per killed mob,
indefinitely. Same cross-world staleness (a target stamped with a future game time never shows
non-priority flavor again). Never cleared in production.

**Resolution:** fixed, same four-part fix as A1. Regression tests in `FlavorCooldownsTest`.

### A3 — LOW: `CombatSuppression` relies on API callers to release
`common/src/main/java/studio/modroll/critfall/api/CombatSuppression.java:15`

If an orchestrator mod suppresses an entity and never releases it (entity dies mid-encounter,
orchestrator bugs out), the UUID stays until server stop. Cleared on SERVER_STOPPING on both loaders
(`CritfallNeoForge.java:32`, `CritfallFabric.java:57`), so it cannot outlive a session, and the
suppress/release contract is documented API. Growth is bounded by orchestrator usage, not by combat
volume.

**Resolution:** documented, no change. The API contract (caller releases) is the design; a TTL would
change behavior for a legitimate long encounter.

### A4 — OK (verified): `ROLLED_APPLY` thread-local is correctly scoped
`common/src/main/java/studio/modroll/critfall/combat/DamageInterception.java:44-67`

The armor-bypass scope saves the previous value, restores it in `finally`, and calls
`ThreadLocal.remove()` when unwinding to empty — no leaked value on a pooled thread, and reentrancy
(thorns recoil during the scoped hurt) restores correctly. No action needed.

### A5 — LOW: static test seams retain the last payload
`neoforge/src/main/java/studio/modroll/critfall/neoforge/network/FeedbackDispatcher.java:30`

`lastRollPayload` (a GameTest seam living in production code) holds the most recent payload forever.
Single small record of primitives/strings — constant memory, no entity references.

**Resolution:** documented, no change (deferred — see `docs/deferred-issues.md`).

### A6 — OK (verified): Fabric per-entity hurt state cannot leak
`fabric/.../CritfallHurtState.java`, `LivingEntityMixin.java`

The rolled-damage decision is stored in `@Unique` fields **on the entity itself**, cleared at the
start of every ALLOW_DAMAGE, and garbage-collected with the entity. No global registry involved.

---

## B. Per-hit performance (hot path)

### B1 — MEDIUM: profile resolution is a linear scan over all loaded profiles, uncached
`common/src/main/java/studio/modroll/critfall/data/ProfileStore.java:101-126`,
`ProfileLookup.java:23-60`

`ProfileStore.resolve` iterates **every** loaded profile and every match entry on each lookup, and a
single melee hit performs 4–6 lookups (target entity, attacker entity, weapon item, flavor pool;
spells add a spell-profile and a second damage-modifier lookup). With the pack that
`/critfall generate` itself produces (~100 entity + ~60 item profiles for vanilla alone; far more in
a modpack) this is O(profiles) work 4–6× per hit, server-wide, including mob-vs-mob — exactly the
shape the audit brief flags. Each tag-entry evaluation also allocates a fresh
`TagKey.create(...)` (`ProfileLookup.java:26,40,49`).

There was **no cache to invalidate** on `/reload` — the question "is the cache invalidated
correctly?" was moot.

**Resolution:** fixed. `ProfileStore` now memoizes resolution per `(registry id, delivery)` in a
`ConcurrentHashMap` per profile kind, invalidated whenever that kind's store is swapped (every
`/reload` re-runs all reload listeners, and tag changes only land via `/reload`, so the cache can
never outlive the data it was computed from). Correctness rests on: same registry id ⇒ same tag
memberships between reloads. Cache size is bounded by registry size × 5 deliveries. Regression
tests in `ProfileStoreTest` (cache hit returns the same resolution; store swap invalidates).

### B2 — MEDIUM: dice expressions re-parsed from strings on the hot path
`common/src/main/java/studio/modroll/critfall/combat/AttackDice.java:30,61`,
`Derivation.java:52-58`

Two per-hit string parses: (1) `AttackDice` appends the attribute-derived flat bonus by
`DiceExpression.parse(dice + "+" + bonus)` — a full tokenize/validate per hit for every profiled
weapon with `modifier_from: attack_damage_attribute` (the default); (2) `Derivation.damageDice`
parses a `DICE_TABLE` constant string per hit on every derived-fallback attack (the out-of-box
configuration for all unprofiled mobs).

**Resolution:** fixed. `Derivation.DICE_TABLE` is pre-parsed once into `DiceExpression`s; the
`AttackDice` bonus append now uses `DiceExpression.plus(...)` with pre-parsed constant expressions
(1–12) instead of building and parsing a string. Canonical forms are identical, so behavior is
bit-for-bit unchanged (existing `DerivationTest`/`AttackDiceTest` pin this). Above-table derivation
(flat damage > 15) still parses — it is rare (attack damage > 15) and kept simple on purpose.

### B3 — HIGH (robustness on the hot path): bonus append can throw mid-hit on a 100-term dice profile
`common/src/main/java/studio/modroll/critfall/combat/AttackDice.java:30,61`

`DiceExpression.parse(dice + "+" + bonus)` throws `DiceParseException` when the profile's dice
already sit at the engine's `MAX_TERMS` (100) limit — a *valid, loadable* datapack expression. The
exception propagates out of the damage event handler and **crashes the server on the first hit**
with that weapon. Contrived but datapack-reachable, and the sibling ammo-append path already guards
this exact case (`withAmmo`, `AttackDice.java:71-80`).

**Resolution:** fixed. The bonus append is guarded like `withAmmo`: if appending would exceed
`MAX_TERMS`, the flat bonus is dropped and the profile dice are used as-is. Regression test in
`AttackDiceTest` (100-term dice + attribute bonus resolves instead of throwing).

### B4 — OK (verified): AABB searches are bounded
`common/src/main/java/studio/modroll/critfall/outcome/OutcomeExecutor.java:194-209`

`hit_nearest_ally` inflates by a radius validated to 1..64 at both sources (rules.json:
`RulesLoader.java:122`; datapack effect: `OutcomeEffect.java:65`). One `getEntitiesOfClass` per
*fired* redirect effect (fumble-gated, cooldown-gated) — not per hit. Fine.

### B5 — LOW: small per-hit allocations (Optionals, records, lambdas)
`DamageInterception.rollAndApply` and friends allocate a handful of short-lived objects per hit
(contexts, params, bundles, `Optional` wrappers, capturing lambdas in `intStat`). All die young in
the nursery; none are worth contorting the code for after B1/B2 remove the real costs.

**Resolution:** documented, no change.

---

## C. Concurrency / thread-safety

### C1 — OK (verified): stores and rules swap atomically; all combat access is server-thread
`ProfileStore` (volatile immutable maps, `Map.copyOf` on set), `RollRuntime` (volatile roller/rules),
`FeedbackSink.Holder` (volatile). Reload listeners' `apply` phases run on the server thread between
ticks, so a reload cannot interleave with an in-flight hit; each hit also reads `rules()` into a
local. The B1 cache is invalidated inside the same setters and is a `ConcurrentHashMap` — safe even
under theoretical off-thread reads.

### C2 — OK (verified): RNG
The only RNG construction is `RollRuntime`'s two `DiceRoller(new Random())` instances (composition
root). `java.util.Random` is thread-safe; all game-logic use is server-thread anyway.
GameTests swap scripted rollers via the documented seams.

### C3 — LOW: `OutcomeExecutor.applyingEffects` is a plain static boolean
`common/src/main/java/studio/modroll/critfall/outcome/OutcomeExecutor.java:45`

Not volatile, not thread-local. Correct under the documented invariant (all entity damage happens on
the server thread; effects never nest — and set/reset is `try/finally`). A mod hurting entities from
another thread violates vanilla's own threading contract first. Making it a `ThreadLocal` would cost
hot-path lookups to defend against an illegal state.

**Resolution:** documented, no change (deferred — see `docs/deferred-issues.md`).

### C4 — LOW: `CritfallEvents` listener lists
`CopyOnWriteArrayList`, listeners caught-and-logged per dispatch. Registration during a dispatch is
safe. No change.

---

## D. Robustness / input validation

### D1 — HIGH: outcome-table weight sum can overflow `int` and crash the server on the first fumble
`common/src/main/java/studio/modroll/critfall/data/OutcomeTable.java:24-30`,
`OutcomeSelector.java:48`

Each `weight` is validated `>= 1` (`OutcomeTable.java:76-78`) but the **sum** is not: two entries of
2,000,000,000 pass individually, `totalWeight()` wraps negative, and `roller.die(totalWeight)`
throws `IllegalArgumentException` out of the damage event the first time the table fires —
a malformed/malicious datapack crashing the server, exactly what the boundary rules forbid.

**Resolution:** fixed. `OutcomeTable.parse` sums weights as `long` and rejects the file (logged
error, pack keeps loading — the standard per-file rejection path in `ProfileReloadListener`) when
the total exceeds `Integer.MAX_VALUE`. Regression tests in `OutcomeTableTest` (overflowing table
rejected; large-but-valid total accepted).

### D2 — HIGH: S2C payload decode preallocates an attacker-controlled list size (client OOM)
`common/src/main/java/studio/modroll/critfall/feedback/RollFeedbackPayload.java:89`

`buf.readList(...)` reads a VarInt count and preallocates an `ArrayList` of that capacity *before*
reading any element (vanilla `FriendlyByteBuf.readCollection`). A malicious or corrupted server can
send a count of 2³¹−1 and force a multi-gigabyte allocation → `OutOfMemoryError` on the client,
which is a hard crash rather than the clean disconnect a decode error produces. Legitimate payloads
carry ≤ ~2 consequence lines. (The string fields are safe: `readUtf` enforces its length cap before
allocating. `readEnum` on a bad ordinal throws and disconnects cleanly — vanilla-equivalent, see
deferred list.)

**Resolution:** fixed. Decode now bounds the consequence count (64) and throws netty's
`DecoderException` beyond it — the network layer turns that into a clean disconnect instead of an
OOM. Regression test in `FeedbackPayloadCodecTest` (huge-count buffer → `DecoderException`, no
allocation explosion).

### D3 — OK (verified, initially suspected HIGH): lenient `NaN`/`Infinity` literals in rules.json
`common/src/main/java/studio/modroll/critfall/data/LenientJson.java`, `RulesLoader.java:73`

Suspected crash: `JsonParser.parseString` is lenient and accepts unquoted `NaN`/`Infinity`, and
Gson's `getAsInt` on such a value would throw `NumberFormatException`, which is not in
`RulesLoader.load`'s catch list. **Disproven by test**: Gson's lenient *tree* parser tokenizes an
unquoted `NaN`/`Infinity` as a STRING primitive, so `LenientJson`'s numeric readers take the
existing wrong-type path — warn + default, no throw. Pinning tests added
(`RulesLoaderTest.lenientNanIntFieldWarnsAndFallsBack`, `fileWithNanNumberNeverThrows`,
`ClientConfigLoaderTest.lenientNanNumberDoesNotThrow`) so a Gson behavior change surfaces as a
test failure, not a server crash.

### D4 — MEDIUM: `global_damage_multiplier: 1e999` overflows to Infinity and poisons damage
`common/src/main/java/studio/modroll/critfall/data/RulesLoader.java:157-161`

`1e999` is a well-formed number token, so it survives the tree parse as a number and
`getAsDouble` yields `Double.POSITIVE_INFINITY`, which passes the `<= 0` validation — every rolled
hit then deals infinite damage. (A NaN reaching the check would also pass, since NaN comparisons
are false, though the literal spelling arrives as a string per D3.) Config-author error, but the
file contract is fall-back-with-warning.

**Resolution:** fixed — the multiplier must be finite and positive, else default 1.0 with a
warning. Regression test in `RulesLoaderTest.nonFiniteGlobalDamageMultiplierWarnsAndFallsBack`
(covers `1e999`, `NaN`, `Infinity`).

### D5 — OK (verified): dice expression bounds
`DiceExpression.java:23-27` — ≤ 100 dice/term, ≤ 1000 sides, ≤ 100 terms, ≤ 256 chars, ≤ 7-digit
numbers, keep-count ≤ count. `9999d9999` is rejected at parse with a logged per-file error.
`DiceFuzzTest`/`DiceParserTest` already cover this, including the audit brief's exact case.

### D6 — OK (verified): datapack parse layer rejects per-file, never per-reload
`ProfileReloadListener.apply` (`:74-87`) wraps each file in `catch (RuntimeException)` — one broken
profile is skipped with an error and the rest of the pack loads. Trigger ranges (1..20), crit range
(2..20), AC ≥ 1, effect params (radius 1..64, ticks ≥ 1, amplifier 0..255, knockback (0,10]),
save DC 1..30, weights ≥ 1, match-entry ids are all validated with throws (file rejected) or
warnings per the boundary rules. Outcome tables cannot reference other tables — no recursion is
expressible. Unknown keys warn (`LenientJson.finish`), unknown effect types/flavor outcomes are
skipped for forward compatibility.

### D7 — OK (verified): commands and file writes
All `/critfall` subcommands require permission level 2. `generate` writes only under the world's
`datapacks/critfall_generated/` root; file names come from `ResourceLocation`s, whose charset
excludes `\` and path separators are flattened to `_`, so no traversal is constructible
(`DatapackGenerator.fileName`, `CritfallCommands.writePack`). Overwrite requires an explicit
`confirm`. `report` writes timestamped names into `critfall-reports/` under the server dir with no
external input in the path. Both catch `IOException` and report failure to the caller instead of
throwing.

### D8 — LOW: flavor keys are trusted translation keys client-side
`ClientFeedbackReceiver` (both loaders) renders `Component.translatable(flavorKey)` from the wire.
An unknown/hostile key renders as its raw string — vanilla-equivalent behavior, cosmetic only.
No change.

---

## E. Resource & lifecycle

### E1 — OK (verified): file IO
`Files.walk` in `CritfallCommands.deleteDir` is in try-with-resources; all other IO is
`Files.readString`/`writeString` (no streams held). Config loaders create parent dirs and write
defaults once; failures log and fall back to defaults.

### E2 — OK (verified): client feedback accumulates nothing
Action bar `setOverlayMessage` replaces the previous line; flavor goes to chat (vanilla-bounded
history); one fire-and-forget particle + sound per received payload; no per-hit scheduling or
handler registration. Config is loaded once at client init.

### E3 — OK (verified): reload listeners are registered once at init on both loaders; payload
channels are optional (NeoForge `.optional()` registrar, Fabric `canSend` negotiation) so
vanilla clients are never disconnected.

---

## Summary

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| A1 | High | FumbleCooldowns unbounded + cross-world stale entries | Fixed + tests |
| A2 | High | FlavorCooldowns unbounded + cross-world stale entries | Fixed + tests |
| B3 | High | Hot-path DiceParseException on 100-term profile dice | Fixed + test |
| D1 | High | Outcome-table weight-sum overflow crashes server | Fixed + tests |
| D2 | High | S2C consequence-list decode can OOM the client | Fixed + test |
| B1 | Medium | O(profiles) uncached lookup per hit | Fixed (memo cache) + tests |
| B2 | Medium | Per-hit dice string parsing | Fixed (pre-parse) |
| D4 | Medium | 1e999 multiplier overflows to Infinity, poisons damage | Fixed + tests |
| A3 | Low | CombatSuppression caller-release contract | Documented |
| A5 | Low | Static test seam retains last payload | Deferred |
| B5 | Low | Small per-hit allocations | Documented |
| C3 | Low | applyingEffects plain static boolean | Deferred |
| D3 | — | Lenient NaN literal crash — suspected, disproven by test | Pinning tests added |
| D8 | Low | Flavor keys trusted client-side | Documented |

Hot-path before/after (B1/B2): a melee hit previously ran 4–6 full linear scans over every loaded
profile (each tag test allocating a `TagKey`) plus 1–2 dice-expression string parses; it now does
4–6 `ConcurrentHashMap` gets (after first resolution per id) and zero string parses on the
table-covered paths. No behavioral change: resolution semantics, RNG draw order, and canonical dice
are identical, pinned by the existing unit/GameTest suites.

The D2 severity is not theoretical: before the fix, the regression test's hostile 2³¹−1 count
killed the JUnit test JVM outright with an `OutOfMemoryError`; after the fix it is a clean
`DecoderException`.

## Verification (Phase 3)

- `./gradlew check` green: spotless + all three modules compile + 321 unit tests, 0 failures,
  0 skipped (every fix's regression test observed failing before its fix — TDD red/green).
- GameTests green on both loaders: NeoForge `runGameTestServer` 75/75, Fabric `runGametest` 66/66.
- No behavior/balance change: no gameplay value was altered; the only observable differences are
  bug-states removed (crashes, the cross-world eternal cooldown, infinite/NaN damage from a
  malformed multiplier).

Deferred items are tracked in `docs/deferred-issues.md`.
