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
| `api.dice` | `DiceExpression`, `DiceRoller`, `RollResult`, `DieRoll`, `RollMode`, `RollDetail`, `DiceParseException` |
| `api.combat` | `AttackResult`, `AttackOutcome`, `SaveResult`, `ContestResult`, `ContestSide` |
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
`armorClass()`, `defenderAcBonus()`, `baseArmorClass()`, `damage()`, and `isHit()`. `armorClass()` is
the **effective** AC the roll was made against; `defenderAcBonus()` is the situational modifier that
was applied (see [Defender AC modifier](#defender-ac-modifier)), and `baseArmorClass()` is
`armorClass() - defenderAcBonus()` — the defender's own AC before the modifier. With no modifier set
they are equal and `defenderAcBonus()` is `0`.

> **Armor note.** `performAttack` applies damage exactly like the automatic pipeline: the attack
> roll's AC already stood in for armor, so vanilla armor reduction is bypassed on the resulting
> `hurt` (governed by the same `balance.disable_vanilla_armor_reduction` flag — turn it off and
> both paths armor-reduce again). No armor-bypassing damage source is needed; API-driven and
> automatic attacks deal identical final damage to an armored target.

> **Suppression.** The `hurt` inside `performAttack` never re-rolls through the automatic
> interception, suppressed or not. Still call `RollService.suppress(...)` when an orchestrator
> owns an entity's combat, so *other* real-time damage involving it stands down too.

> **Invulnerability frames.** Driven attacks are deliberate, discrete combat events, not the
> accidental rapid-fire that vanilla invulnerability frames guard against, so `performAttack`
> clears the target's hurt cooldown before its damage lands. Each driven attack therefore applies
> its **full rolled damage** even when the target was hit moments earlier — an opportunity attack,
> or several attackers focusing one target in the same round, are never swallowed. The hurt re-arms
> the cooldown just as a normal successful hit would, so vanilla i-frame behaviour for ordinary
> real-time damage is unchanged.

Fine-grained helpers are also available: `savingThrow(target, saveBonus, dc)` (and
`savingThrow(target, saveBonus, dc, RollMode)` to roll the save with advantage/disadvantage),
`fireOutcomes(attacker, target, result, damageDice, weapon)`, and
`sendRollFeedback(attacker, target, payload)`.

### Roll detail (how a roll was made)

Every d20 result carries a `RollDetail` describing **how** it was rolled, not just what it landed on:

```java
public record RollDetail(RollMode mode, int kept, OptionalInt dropped)
```

- `mode` — `NORMAL` / `ADVANTAGE` / `DISADVANTAGE`.
- `kept` — the face the check resolved on. Always equal to the result's `natural`.
- `dropped` — the other face when two dice were rolled; **empty** under `NORMAL`, which rolls one
  die and drops nothing. `hasTwoDice()` is the convenience test.

It is on every result, and on both sides of a contest (each side rolls under its own mode):

| Result | Accessor |
|--------|----------|
| `AttackResult` | `roll()` |
| `SaveResult` | `roll()` |
| `ContestResult` | `initiatorRoll()`, `opponentRoll()` |
| `RollFeedbackPayload` | `roll()` (from `rollMode()` + `natural()` + `droppedNatural()`) |
| `SaveFeedbackPayload` | `roll()` |

Reading both dice off an advantage attack to render your own presentation:

```java
AttackContext ctx = AttackContext.melee(source, weapon).withMode(RollMode.ADVANTAGE);
AttackResult result = RollService.performAttack(attacker, target, ctx);

RollDetail roll = result.roll();
if (roll.hasTwoDice()) {
    // e.g. "advantage: 7 / 18, kept 18"
    animateTwoDice(roll.dropped().getAsInt(), roll.kept(), roll.mode());
} else {
    animateOneDie(roll.kept());
}
```

`RollDetail.normal(face)` builds the plain one-die case, which is also what the pre-0.2.6
constructors of `AttackResult`/`SaveResult`/`ContestResult` default to — existing callers are
unaffected.

### Detecting driven damage

`RollService.isDrivenDamage(target)` tells you whether the hurt **currently being applied** to
`target` is a Critfall-driven attack — the damage from `performAttack` — as opposed to real-time
vanilla or other-mod damage. Query it from inside your own loader damage listener to exempt
Critfall's driven attacks from your handling (e.g. a participant-damage cancel that should not fire
on your own driven swings). It is `true` only during the driven `hurt`, on the entity taking it,
and `false` everywhere else.

```java
// NeoForge, inside a LivingIncomingDamageEvent listener:
if (RollService.isDrivenDamage(event.getEntity())) {
    return; // Critfall (or our orchestrator via Critfall) owns this hit — don't cancel it
}
```

Prefer this over inspecting the `DamageSource` or Critfall internals: it is the supported signal and
is covered by the semver promise. `CombatInteractionEvent` does **not** fire for driven damage (see
below), so a damage listener is the right place to distinguish it.

### Contested checks

`RollService.contest(initiator, opponent, ContestContext)` resolves a D&D 5e **opposed roll** — two
entities rolling against each other (Stealth vs Perception, Athletics vs Athletics for a shove or
grapple). Each side rolls a d20 under its own roll mode, adds its bonus, and the higher total wins.

```java
ContestContext ctx = ContestContext.of(stealthBonus, perceptionBonus)   // per-side bonuses
        .withInitiatorMode(RollMode.ADVANTAGE);                          // per-side advantage/disadvantage
ContestResult result = RollService.contest(hider, seeker, ctx);

if (result.winner() == ContestSide.INITIATOR) {   // == result.initiatorWins()
    // the hider stays hidden
}
```

`ContestResult` carries both sides' data for presentation: `initiatorNatural()`, `initiatorTotal()`,
`opponentNatural()`, `opponentTotal()`, plus `winner()` (`ContestSide.INITIATOR`/`OPPONENT`) and the
convenience `initiatorWins()`.

**Tie rule.** Ties go to the **opponent** — 5e's default: the initiator's check *fails* on a tie. This
is exactly `initiatorTotal() > opponentTotal()` deciding `initiatorWins()`. A consumer that wants the
opposite convention compares the two totals from the result itself; Critfall does not gate this behind
a config flag.

**Roll mode per side.** `initiatorMode`/`opponentMode` reuse the normal d20 mechanism
(`NORMAL`/`ADVANTAGE`/`DISADVANTAGE`) independently for each side. The **initiator rolls first**, so a
scripted roller (the [RNG seam](#deterministic-testing-the-rng-seam)) scripts the initiator's die
faces before the opponent's.

**Bonuses are caller-supplied.** `ContestContext` carries `initiatorBonus`/`opponentBonus`; the
consumer decides what a "Stealth" or "Athletics" bonus means. Critfall stays a dice engine and does
**not** model skills or ability scores — building a 5e skill system is out of scope (a design decision
for a separate project). The `initiator`/`opponent` entities are part of the signature for identity and
forward compatibility: a future data-driven per-entity named-modifier map (packs giving a mob a
`"stealth"`/`"athletics"` bonus) could read them without a breaking signature change. That richer option
is **future work**; today, supply the bonuses yourself.

**Feedback.** Contests do not emit a Critfall feedback readout — the attack-shaped
`RollFeedbackPayload` (outcome / AC / damage / dice) does not fit an opposed roll, and different
contests (Stealth vs Perception, a shove) want different presentation. Consumers present the result
themselves from the `ContestResult` fields (both naturals, both totals, and each side's
[roll detail](#roll-detail-how-a-roll-was-made) are exposed for exactly this).

### `AttackContext` and `AttackDelivery`

`AttackContext` bundles the delivery method, the damage source, the weapon stack, the roll mode, and
optional overrides. Factories: `AttackContext.melee/projectile/thrown/spell(source, weapon)`.
Wither methods return a copy: `withMode(RollMode)`, `withAttackBonus(int)`, `withDamageDice(...)`,
`withDefenderAcBonus(int)` (see below).

#### Defender AC modifier

`withDefenderAcBonus(int)` applies a situational modifier to the **defender's** AC for this one
attack — cover, prone-at-range, magical protection, or a penalty like flanked/restrained. It is the
honest way to express "this target is harder (or easier) to hit for this attack", rather than a
negative attacker bonus: the two are numerically identical but a negative `withAttackBonus` misreports
a defended target as a weakened attacker in events, feedback, and any consumer inspecting the result.

```java
AttackContext ctx = AttackContext.projectile(source, bow)
        .withDefenderAcBonus(5);   // target is behind half cover
AttackResult result = RollService.performAttack(archer, target, ctx);
// result.baseArmorClass() == target's own AC, result.defenderAcBonus() == 5,
// result.armorClass()     == the effective AC the d20 was compared against.  "AC 14 (+5)"
```

- **Per-attack and non-persistent.** It affects only this `attackRoll`/`performAttack` call; it never
  mutates the entity's profile or carries over to the next attack.
- **Effective AC** for the roll is `RollService.effectiveEntity(target).armorClass() + defenderAcBonus`.
- **Negatives are allowed** (situational penalties); the value is not clamped.
- **Defaults to `0`** — existing callers that never call it are unaffected, byte for byte.
- **Interaction with the roll.** The modifier shifts the to-hit *threshold* only; it is independent of
  advantage/disadvantage, which choose the kept d20 face. Crit and fumble are natural-based (a natural
  20 still hits and crits, a natural 1 still misses and can fumble) and so are unaffected by it.
- **Feedback.** Since 0.2.6 the split reaches the wire: `RollFeedbackPayload.defenderAcBonus()` and
  `baseArmorClass()` mirror the result, and Critfall's own readout renders `vs AC 17 (10+7)` when a
  modifier applied (and the plain `vs AC 17` when none did).

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

Set<UUID> all = CombatSuppression.suppressedUuids();   // read-only view, all mods
```

Suppression is in-memory and transient: Critfall clears it internally on server stop.

`suppressedUuids()` is an unmodifiable live view of every suppressed UUID — useful as a global
leak check in tests (assert it is empty once your encounter has released everything).

**Test scope only:** `clearAllForTesting()` (was `clear()`) wipes every mod's suppressions at
once. Never call it in production — release per entity instead.

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

### `CombatInteractionEvent` — detecting combat

The supported "combat started" signal: it fires whenever one living entity damages another, so an
orchestrator never needs to touch the raw loader damage events.

```java
CritfallEvents.onCombatInteraction(event -> {
    if (!encounter.isRunning()) {
        encounter.start(event.attacker(), event.target());
    }
});
```

**Fires:**

- Server-side, on both loaders at the same point: after the game's invulnerability checks, before
  mitigation.
- Before everything Critfall does with the damage — so it *always* fires, even when the damage is
  then cancelled (miss, fumble, listener cancel/veto), exempt or always-hits, a vanilla
  passthrough, dry-run, disabled in rules, zero damage, or between suppressed entities (so you can
  see a new mob join an encounter you already own).
- Once per damage event — every swing, every projectile impact. Debounce in the listener if you
  need "a fight began" semantics.

**Never fires for:**

- Environmental damage (no living attacker).
- Damage you apply yourself via `RollService.performAttack`.
- Damage dealt by Critfall's own outcome effects.

**Carries** `attacker()`, `target()`, `source()` (the raw `DamageSource`), and `delivery()`
(`AttackDelivery`, classified like the roll pipeline).

**Observe-only.** Firing changes nothing about combat resolution. Order per damage event:
`CombatInteractionEvent` → `PreAttackRollEvent` → d20 → `PostAttackRollEvent` →
`CritEvent`/`FumbleEvent`.

## Feedback

API consumers emit the same packets the internal pipeline does via
`RollService.sendRollFeedback(attacker, target, payload)` with an
`api.feedback.RollFeedbackPayload`; headless contexts keep the no-op default sink.

## KubeJS

Critfall does **not** depend on KubeJS, and there is no separate KubeJS module. Modern KubeJS
exposes public Java classes to scripts directly, so a **server script** calls the API above with
`Java.loadClass(...)`. See runnable examples in [`/examples/kubejs/`](../examples/README.md).
