# Critfall Public API

Everything a mod or KubeJS script needs lives in **`studio.modroll.critfall.api`** (and its
`.event` subpackage). Nothing outside `api` is required to consume Critfall. The API is
**loader-agnostic** — it works identically on NeoForge and (from M8) Fabric — and is covered by the
semantic-versioning promise: it will not break without a major version bump.

All rolls are server-authoritative and go through Critfall's injectable combat RNG, so the API adds
no randomness of its own and stays deterministic under test.

## `RollService`

The static entry point (`studio.modroll.critfall.api.RollService`).

### Dice

```java
RollResult r = RollService.roll("2d6+3");   // or roll(DiceExpression)
int total = r.total();
```

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

> **Armor note.** `performAttack` applies damage with `LivingEntity.hurt(ctx.source(), damage)`.
> Unlike the automatic pipeline — which zeroes vanilla armor reduction through the NeoForge damage
> event — the API path leaves vanilla armor reduction in place unless `ctx.source()` is itself
> armor-bypassing. An orchestrator that wants AC to be the only defense should supply an
> armor-bypassing damage source.

> **Suppress first.** Call `RollService.suppress(...)` on the participants before `performAttack`,
> or the automatic damage interception may *also* roll when `performAttack` calls `hurt`.

Fine-grained helpers are also available: `savingThrow(target, saveBonus, dc)`,
`fireOutcomes(attacker, target, result, damageDice, weapon)`, and
`sendRollFeedback(attacker, target, payload)`.

### `AttackContext` and `AttackDelivery`

`AttackContext` bundles the delivery method, the damage source, the weapon stack, the roll mode, and
optional overrides. Factories: `AttackContext.melee/projectile/thrown/spell(source, weapon)`.
Wither methods return a copy: `withMode(RollMode)`, `withAttackBonus(int)`, `withDamageDice(...)`.

`AttackDelivery` is `MELEE | PROJECTILE | THROWN | SPELL`. It disambiguates hybrid items (issue #9):
a `THROWN` trident resolves against ranged dice and the ranged flavor pool, a `MELEE` trident stab
against melee. `isRanged()` is true for `PROJECTILE`/`THROWN`.

## Suppression (`CombatSuppression`)

The per-entity "an orchestrator owns this entity's combat" flag from PLAN §12. While an entity is
suppressed, Critfall's automatic real-time interception **stands down** for any attack it is
involved in (as attacker or target) — so an external mod (e.g. the turn-based companion) can drive
combat itself via `performAttack`.

```java
RollService.suppress(entity);          // or CombatSuppression.suppress(uuid)
boolean owned = RollService.isSuppressed(entity);
RollService.release(entity);
```

Suppression is in-memory and transient: it is cleared on server stop (a restart ends any encounter).

## Events (`studio.modroll.critfall.api.event`)

Register listeners on `CritfallEvents`. Both the automatic pipeline and `RollService` fire through
it, so a listener sees **every** attack from either path. A listener that throws is caught and logged
— one bad listener cannot break combat.

| Event | When | Listener can |
|-------|------|--------------|
| `PreAttackRollEvent`  | before the d20 | change `attackBonus`, force `mode` (advantage/disadvantage), or `cancel()` |
| `PostAttackRollEvent` | after resolving, before damage | change `finalDamage`, or `veto()` |
| `FumbleEvent`         | outcome is a fumble | observe |
| `CritEvent`           | outcome is a crit | observe |

```java
import studio.modroll.critfall.api.*;
import studio.modroll.critfall.api.event.*;
import studio.modroll.critfall.dice.RollMode;

// Grant advantage when the attacker is sneaking:
CritfallEvents.onPreAttackRoll(event -> {
    if (event.attacker() != null && event.attacker().isCrouching()) {
        event.mode(RollMode.ADVANTAGE);
    }
});

// Halve all crit damage:
CritfallEvents.onPostAttackRoll(event -> {
    if (event.result().outcome() == studio.modroll.critfall.combat.AttackOutcome.CRIT) {
        event.finalDamage(event.finalDamage() / 2);
    }
});
```

A canceled `PreAttackRollEvent` means the attack does not happen (no damage, no outcome tables).
A vetoed `PostAttackRollEvent` means it resolved but applies no damage and runs no outcome tables.

## Feedback (`FeedbackSink`)

`studio.modroll.critfall.feedback.FeedbackSink` is the seam through which roll/flavor packets are sent.
API consumers emit the same packets the internal pipeline does via
`RollService.sendRollFeedback(attacker, target, payload)`; headless contexts keep the no-op default.

## KubeJS

Critfall does **not** depend on KubeJS, and there is no separate KubeJS module. Modern KubeJS
exposes public Java classes to scripts directly, so a **server script** calls the API above with
`Java.loadClass(...)`. See runnable examples in [`/examples/kubejs/`](../examples/README.md).
