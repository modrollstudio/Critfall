# Critfall Public API

Everything a mod or KubeJS script needs lives in **`studio.modroll.critfall.api`** and its
subpackages (`.event`, `.dice`, `.combat`, `.feedback`). Nothing outside `api` is required to
consume Critfall — every type appearing in an `api` signature is itself in `api` (since 0.2.1).
The API is **loader-agnostic** — it works identically on NeoForge and (from M8) Fabric — and is
covered by the semantic-versioning promise: it will not break without a major version bump.

All rolls are server-authoritative and go through Critfall's injectable combat RNG, so the API adds
no randomness of its own and stays deterministic under test.

The `api` subpackages:

| Package | Types |
|---------|-------|
| `api.dice` | `DiceExpression`, `DiceRoller`, `RollResult`, `DieRoll`, `RollMode`, `DiceParseException` |
| `api.combat` | `AttackResult`, `AttackOutcome`, `SaveResult` |
| `api.feedback` | `RollFeedbackPayload`, `ConsequenceLine` |
| `api.event` | the events below |

## `RollService`

The static entry point (`studio.modroll.critfall.api.RollService`).

### Dice

```java
RollResult r = RollService.roll("2d6+3");   // or roll(DiceExpression)
int total = r.total();
```

### Deterministic testing (the RNG seam)

`RollService.setRoller(DiceRoller)` replaces the roller behind every combat roll, so an external
mod's tests can force exact die faces — a nat 1, a nat 20 — the same way Critfall's own tests do.
Build a `DiceRoller` on your own scripted `java.util.random.RandomGenerator` (have `nextInt(bound)`
return `face - 1`) and restore afterwards:

```java
RollService.setRoller(new DiceRoller(myScriptedGenerator)); // next d20 shows the face you script
try {
    AttackResult result = RollService.performAttack(attacker, target, ctx); // e.g. a forced crit
} finally {
    RollService.resetRoller();
}
```

**Test scope only.** Rolls are server-authoritative; swapping the server's RNG in normal gameplay
changes combat for everyone on it. `resetRoller()` restores the default randomly seeded roller.

### Effective-profile queries

The resolved combat stats for any entity/item — the datapack profile value, or the attribute-derived
fallback (PLAN §4.3) when a field is absent. These are exactly the values the live pipeline uses.

```java
EffectiveEntityProfile eff = RollService.effectiveEntity(livingEntity);
int ac        = eff.armorClass();
int atkBonus  = eff.attackBonus();
int saveBonus = eff.saveBonus();
Optional<DiceExpression> melee  = eff.meleeDamage();
Optional<DiceExpression> ranged = eff.rangedDamage();
int critRange = eff.critRange();

EffectiveItemProfile item = RollService.effectiveItem(itemStack);
```

### Driven attacks

`attackRoll` resolves an attack (firing the events below) **without** touching the world.
`performAttack` runs the full pipeline: rolls, applies damage, runs outcome tables, and emits
feedback. Both take an `AttackContext`.

```java
AttackContext ctx = AttackContext.melee(source, weaponStack);
AttackResult result = RollService.attackRoll(attacker, target, ctx);   // resolve only

RollService.suppress(attacker);        // see "Suppression" below
RollService.suppress(target);
AttackResult applied = RollService.performAttack(attacker, target, ctx);
```

`AttackResult` exposes `outcome()` (`MISS`/`FUMBLE`/`HIT`/`CRIT`), `natural()`, `attackTotal()`,
`armorClass()`, `damage()`, and `isHit()`.

> **Armor note.** `performAttack` applies damage exactly like the automatic pipeline: the attack
> roll's AC already stood in for armor, so vanilla armor reduction is bypassed on the resulting
> `hurt` (governed by the same `balance.disable_vanilla_armor_reduction` flag — turn it off and
> both paths armor-reduce again). No armor-bypassing damage source is needed; API-driven and
> automatic attacks deal identical final damage to an armored target.

> **Suppression.** The `hurt` inside `performAttack` never re-rolls through the automatic
> interception, suppressed or not. Still call `RollService.suppress(...)` when an orchestrator
> owns an entity's combat, so *other* real-time damage involving it stands down too.

Fine-grained helpers are also available: `savingThrow(target, saveBonus, dc)`,
`fireOutcomes(attacker, target, result, damageDice, weapon)`, and
`sendRollFeedback(attacker, target, payload)`.

### `AttackContext` and `AttackDelivery`

`AttackContext` bundles the delivery method, the damage source, the weapon stack, the roll mode, and
optional overrides. Factories: `AttackContext.melee/projectile/thrown/spell(source, weapon)`.
Wither methods return a copy: `withMode(RollMode)`, `withAttackBonus(int)`, `withDamageDice(...)`.

`AttackDelivery` is `MELEE | PROJECTILE | THROWN | SPELL`. It disambiguates hybrid items (issue #9):
the delivery is threaded through item-profile and flavor-pool resolution, so a profile/pool with a
`"delivery"` restriction (see docs/datapack-formats.md) only matches attacks delivered that way — a
`THROWN` trident picks the thrown dice and flavor lines, a `MELEE` stab the melee ones. It also
selects the entity profile's ranged vs melee dice: `isRanged()` is true for `PROJECTILE`/`THROWN`.
The automatic pipeline classifies a projectile that is its own launcher (tridents, snowball-likes,
modded throwing weapons that record themselves as the firing weapon) as `THROWN`.

## Suppression (`CombatSuppression`)

The per-entity "an orchestrator owns this entity's combat" flag from PLAN §12. While an entity is
suppressed, Critfall's automatic real-time interception **stands down** for any attack it is
involved in (as attacker or target) — so an external mod (e.g. the turn-based companion) can drive
combat itself via `performAttack`.

```java
RollService.suppress(entity);          // or CombatSuppression.suppress(uuid)
boolean owned = RollService.isSuppressed(entity);
RollService.release(entity);

Set<UUID> all = CombatSuppression.suppressedUuids();   // read-only, every mod's suppressions
```

Suppression is in-memory and transient: Critfall clears it internally on server stop (a restart
ends any encounter).

`suppressedUuids()` (since 0.2.2) is an **unmodifiable live view** of every currently suppressed
UUID across all mods — mutating it throws, and iteration is weakly consistent under concurrent
suppress/release. Use it for global leak checks in tests: assert it is empty once your encounter
has released everything, without having to enumerate known UUIDs.

**Test scope only:** `CombatSuppression.clearAllForTesting()` (the renamed 0.2.1 `clear()`) wipes
every mod's suppressions at once. It exists for test cleanup; calling it in production would
destroy other mods' running encounters. The production mutation surface is per-entity
`suppress`/`release` only.

## Events (`studio.modroll.critfall.api.event`)

Register listeners on `CritfallEvents`. Both the automatic pipeline and `RollService` fire through
it, so a listener sees **every** attack from either path. A listener that throws is caught and logged
— one bad listener cannot break combat.

| Event | When | Listener can |
|-------|------|--------------|
| `CombatInteractionEvent` | a damaging combat interaction is detected, before everything else (since 0.2.2) | observe |
| `PreAttackRollEvent`  | before the d20 | change `attackBonus`, force `mode` (advantage/disadvantage), or `cancel()` |
| `PostAttackRollEvent` | after resolving, before damage | change `finalDamage`, or `veto()` |
| `FumbleEvent`         | outcome is a fumble | observe |
| `CritEvent`           | outcome is a crit | observe |

```java
import studio.modroll.critfall.api.*;
import studio.modroll.critfall.api.event.*;
import studio.modroll.critfall.api.dice.RollMode;
import studio.modroll.critfall.api.combat.AttackOutcome;

// Grant advantage when the attacker is sneaking:
CritfallEvents.onPreAttackRoll(event -> {
    if (event.attacker() != null && event.attacker().isCrouching()) {
        event.mode(RollMode.ADVANTAGE);
    }
});

// Halve all crit damage:
CritfallEvents.onPostAttackRoll(event -> {
    if (event.result().outcome() == AttackOutcome.CRIT) {
        event.finalDamage(event.finalDamage() / 2);
    }
});
```

A canceled `PreAttackRollEvent` means the attack does not happen (no damage, no outcome tables).
A vetoed `PostAttackRollEvent` means it resolved but applies no damage and runs no outcome tables.

### `CombatInteractionEvent` — combat detection for orchestrators

`CritfallEvents.onCombatInteraction(...)` is the supported way to detect "combat has started":
an orchestrator (e.g. a turn-based encounter mod) no longer needs to listen to the raw loader
damage events and out-prioritize Critfall's own listener. The firing contract:

- **Where.** Server-side, fired by the shared interception on both loaders at the loader-parity
  point (after the game's invulnerability/i-frame checks, before mitigation) — behavior is
  identical on NeoForge and Fabric.
- **When.** Before *all* of Critfall's own resolution for that damage: before rules gating,
  damage classification, `PreAttackRollEvent`, and the d20. It therefore fires regardless of what
  happens to the damage afterwards — a rolled hit, a miss/fumble that cancels it, a listener
  `cancel()`/`veto()`, an `#critfall:exempt` or `#critfall:always_hits` damage type, a vanilla
  passthrough fallback, dry-run mode, `attack_rolls.enabled: false`, a **suppressed** attacker or
  target (so an orchestrator can see a new mob join an encounter it already owns), and zero-damage
  interactions (snowballs) all still fire it.
- **Never fired for:** damage with no living attacker (environmental / entity-less), the damage a
  consumer applies itself via `RollService.performAttack` (the consumer already has that result),
  and damage dealt by Critfall's own outcome effects (a redirected swing, fumble self-damage).
- **Cadence.** Once per qualifying damage event — every melee swing, every projectile impact. It
  means "a combat interaction was detected", not "a fight began"; debounce in the listener if you
  need encounter-session semantics.
- **Payload.** `attacker()` and `target()` (both `LivingEntity`), `source()` (the raw
  `DamageSource`), and `delivery()` (`AttackDelivery`, classified like the roll pipeline — THROWN
  for self-launched projectiles, SPELL for tagged/indirect magic, MELEE for direct hits;
  best-effort from the source's shape for damage the pipeline itself would never roll).
- **Observe-only.** The record is immutable and firing changes nothing about combat resolution.
  Ordering for one damage event: `CombatInteractionEvent` → `PreAttackRollEvent` → d20 →
  `PostAttackRollEvent` → `CritEvent`/`FumbleEvent`.

```java
CritfallEvents.onCombatInteraction(event -> {
    if (!encounter.isRunning()) {
        encounter.start(event.attacker(), event.target());
    }
});
```

## Feedback

API consumers emit the same packets the internal pipeline does via
`RollService.sendRollFeedback(attacker, target, payload)` with an
`api.feedback.RollFeedbackPayload`; headless contexts keep the no-op default sink.

## KubeJS

Critfall does **not** depend on KubeJS, and there is no separate KubeJS module. Modern KubeJS
exposes public Java classes to scripts directly, so a **server script** calls the API above with
`Java.loadClass(...)`. See runnable examples in [`/examples/kubejs/`](../examples/README.md).
