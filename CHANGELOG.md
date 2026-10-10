# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.12] - 2026-10-10

Creatures without hands no longer punch, and Critfall requires Minecraft 1.21.1.
### Added
- `critfall:fights_with_fists` entity tag: decides which empty-handed mobs punch.

### Fixed

- Creatures without hands, such as zoglins, slimes and iron golems, were described as punching: a
  zoglin kill read "Finished with one final, bare-fisted blow." Their crit, fumble and kill lines now
  describe a natural attack. Players, zombies, piglins, villagers, illagers, witches, endermen and
  skeletons still punch when empty-handed.
- A ranged attack or a spell made with an empty hand also read as a punch. It now uses the
  natural-attack lines, whoever made it.
- Fabric allowed any Minecraft 1.21.x, and Critfall crashed on 1.21.2 or later. Both loaders now
  require exactly 1.21.1.

## [0.2.11] - 2026-10-08

Kill lines show only on real kills.

### Fixed

- A kill flavor line (e.g. "Beaten down, bare-handed") was chosen from the rolled damage against the
  target's health before vanilla applied Resistance, absorption, a Totem of Undying, or other mods'
  damage handling. A high roll against a golem-punched player with Resistance V showed a kill line on
  every swing with no damage taken. The feedback for a rolled hit (attack or failed saving throw) is
  now sent once the target's `hurt` has fully resolved, and the kill line shows only if the target died.
  A survived hit gets the normal hit line.
- A hit that took no health and no absorption keeps its rolled readout and appends `no damage taken`
  (new consequence key `critfall.consequence.resisted`), so it no longer reads as damage taken. Also
  applies to `RollService.performAttack`. Absorbed and totem-saved hits are not marked: they took
  damage. A hit where a Totem of Undying fired is never marked, even at 1 HP, where the totem leaves
  health where it started.

### Notes

- Rolls, damage and the rest of the readout are unchanged. Misses and fumbles still send their
  feedback straight away; hits now send it at the end of the same `hurt` call instead of during it.
- Both loaders wrap `LivingEntity.hurt` with a MixinExtras `@WrapMethod` to send the held feedback.
  This is NeoForge's first mixin (`critfall.mixins.json`, declared in `neoforge.mods.toml`).
- `critfall.consequence.resisted` is the only new key. The server-rendered action bar (clients without
  Critfall) uses `translatableWithFallback` and reads `no damage taken`. A Critfall client builds the
  text itself from the key in the packet, so a 0.2.10 client on a 0.2.11 server shows the raw key for a
  negated hit: the packet has no field to carry a fallback string.
- Totem use is detected with an inject at the return of `LivingEntity.checkTotemDeathProtection`, on
  both loaders.
- GameTests on both loaders: a lethal roll against a target with Resistance V, holding a Totem of
  Undying (totem used), or with enough absorption shows no kill line; an unprotected target dies and
  shows the kill line; a target at 1 HP saved by a totem shows no kill line and is not marked resisted.
  A unit test checks the resisted readout renders in English without the lang file.

## [0.2.10] - 2026-10-04

A modifier provider can add to the defender's AC.

### Added

- `ModifierProvider.acModifier(defender, attacker)`, returning an `OptionalInt` (empty by default). A
  present value is **added** to the defender's AC as Critfall computes it (entity profile
  `armor_class`, else derived from armor and toughness): AC 10 with `2` rolls against AC 12, with `-1`
  against AC 9. Empty changes nothing. Asked once per attack roll on the automatic pipeline (melee,
  projectile, thrown, spell attack) and by `RollService.performAttack`/`attackRoll`. Saving throws have
  no AC and never ask.
- The provided value is part of the defender's AC on the result: `AttackResult.armorClass()` and
  `baseArmorClass()` include it, and `AttackContext.withDefenderAcBonus` stacks on top
  (`vs AC 17 (12+5)`). The readout shows the final AC. Outcome-table miss margins measure from it.
- A bad answer (a throw, `null`, or a value beyond ±1,000,000) keeps Critfall's own AC and is logged
  once per provider, like the other methods. The value is not clamped otherwise.
- `rules.json` `modifier_providers.enabled: false` ignores it, as for the other methods.

### Notes

- With no provider, or one that does not implement `acModifier`, every roll is unchanged from 0.2.9.
- `RollService.effectiveEntity` and `/critfall inspect` still show Critfall's own AC; the provider
  answers per attacker, which neither names.
- Unit tests cover the addition, a negative value, zero, the toggle, and throw/`null`/out-of-range
  fallbacks logging once. GameTests on both loaders cover a melee roll that hit at AC 10 missing at the
  provided AC 12, a negative value turning a miss into a hit, ranged and spell attacks, driven
  `attackRoll`/`performAttack` (stacked with `withDefenderAcBonus`), and a throwing provider falling back
  to AC 10 with one log line over three attacks.

## [0.2.9] - 2026-10-02

A player's jump attack rolls with advantage instead of dealing vanilla's crit damage.

### Added

- **Jump attacks roll with advantage.** A player melee hit that meets vanilla's critical-hit
  conditions (falling, not on the ground, fully charged, not sprinting, climbing, in water, blind or
  riding) rolls its attack with advantage. Vanilla decides the crit (NeoForge's
  `CriticalHitEvent.isVanillaCritical()`, and on Fabric the branch where vanilla applies its
  multiplier), so its conditions are not re-implemented. The readout shows both d20s, as with any
  advantage roll. Applies to the automatic real-time pipeline only: mob attacks and
  `RollService.performAttack`/`attackRoll` are unchanged.
- It combines with other sources the 5e way: a `PreAttackRollEvent` listener that sets disadvantage
  on a jump attack makes the roll normal instead of replacing the advantage. Listeners see the
  advantage as the event's base mode (previously always `NORMAL` on the automatic path), and setting
  `NORMAL` clears it.
- `rules.json` `advantage_sources.jump_attack` (default `true`). `false` restores 0.2.8 behaviour
  exactly. The other keys PLAN.md sketches under `advantage_sources` are recognised and warn as not
  implemented yet (the whole block used to).

### Changed

- Vanilla's 1.5× crit multiplier no longer applies to a jump hit Critfall rolls: damage stays
  dice-based and only a natural 20 crits. This matters wherever the vanilla amount reaches the
  result: dice derived for a weapon without a profile (a bare-handed jump hit with 4 attack damage
  rolled `1d12` in 0.2.8, it now rolls `1d8`) and the amount applied with `damage_dice` off. A jump
  hit Critfall does not roll (player rolls off, a vanilla passthrough fallback, a suppressed
  participant) keeps the multiplier, and so does a dry run's vanilla damage. Vanilla's crit sound,
  particles and no-sweep rule are unchanged.

### Notes

- GameTests on both loaders drive real `Player.attack` swings: a jump hit rolls with advantage and a
  grounded hit does not; a listener's disadvantage makes a jump hit normal; the multiplier is gone
  from the derived dice and the dice-off amount; the multiplier stays when player rolls are off and in
  dry-run; the flag off reproduces 0.2.8's numbers; and driven attacks by a falling player roll
  normally.

## [0.2.8] - 2026-10-02

The provided damage modifier stacks on Critfall's own damage dice instead of replacing their flat part,
and melee damage enchantments count toward a profiled weapon's dice.

### Changed

- **Semantic change:** `ModifierProvider.damageModifier` is now **added** to Critfall's damage dice
  (`1d8+2` with `5` rolls `1d8+7`; 0.2.7 rolled `1d8+5`). The flat part it used to replace carries
  the weapon and the attacker's buffs, so with a provider registered weapon material (wooden vs stone
  sword), bow draw strength, Power and the Strength/Weakness effects stopped affecting damage. A
  provider written for 0.2.7 that returned a full damage bonus should now return only its own bonus.
  `attackModifier` and `saveModifier` still replace.
- A provided `0` now leaves the damage dice unchanged (0.2.7 dropped their flat part).

### Fixed

- Damage enchantments (Sharpness, Smite, Bane of Arthropods, Impaling, modded ones) were ignored on
  melee hits with a weapon that has an item profile: the `modifier_from: attack_damage_attribute`
  bonus was built from the attack-damage attribute, and vanilla adds enchantment damage separately.
  The bonus is now built from the attribute plus the enchantment damage vanilla computes against the
  actual target, so target-specific enchantments only count where vanilla applies them (Smite on
  undead, Bane on arthropods). An iron sword with Sharpness V on a player now rolls `1d8+5` instead of
  `1d8+2`. Applies to the automatic melee pipeline and to melee `RollService.performAttack`/`attackRoll`
  (including their derived fallback for weapons without a profile). Projectile and thrown hits already
  counted Power and Impaling through the vanilla projectile damage. The to-hit bonus is unchanged.

### Added

- `DiceExpression.plusModifier(int)`: the same dice with a value added to the constants (`1d8+2+1d4`
  plus `3` is `1d8+1d4+5`); dice-less expressions and `0` come back unchanged.
- GameTests on both loaders drive real vanilla hits (`Mob.doHurtTarget`, an arrow's own tick) and
  check, with and without a provider, that sword material, Strength II and Weakness, bow draw strength,
  Power and Sharpness each change the damage; that Smite counts against a zombie but not a pig, and
  Bane of Arthropods against a spider but not a zombie; and that a driven melee attack counts
  Sharpness.

### Notes

- Unchanged: the minimum-1 damage floor, dice-less amounts ignoring the provider, and, with no provider
  registered, every roll with an unenchanted weapon (the new GameTests pin the 0.2.7 numbers).

## [0.2.7] - 2026-10-01

A modifier provider hook: another mod can supply the modifiers on Critfall's attack, damage and save
rolls without Critfall depending on it.

### Added

- `ModifierProvider` in `studio.modroll.critfall.api`: `attackModifier(attacker, target, delivery)`,
  `damageModifier(attacker, delivery)` and `saveModifier(entity, saveKey)`, each returning an
  `OptionalInt` (empty by default). A present value **replaces** Critfall's own bonus for that roll
  (profile `attack_bonus`, the flat part of the damage dice, `save_bonus`, or their derived
  fallbacks); empty keeps it. Asked on the automatic pipeline (melee, projectile, thrown, spell attack,
  spell save and its damage) and by `RollService.performAttack`/`attackRoll`.
- One provider slot: `RollService.registerModifierProvider`, `clearModifierProvider`,
  `modifierProvider`. A second, different provider logs a warning and the last one wins.
- Save key `ModifierProvider.SPELL_SAVE` (`"critfall:spell"`) for the save against a
  `"resolution": "save"` spell profile, the only place Critfall rolls a save.
- A dice-less damage amount (the derived flat `1` for a 1-damage hit) has no modifier to replace and
  stays as is.
- A bad answer (a throw, `null`, or a damage modifier beyond ±1,000,000) falls back to Critfall's own
  bonus and is logged once per provider and roll type.
- `rules.json` `modifier_providers.enabled` (default `true`); `false` ignores any provider. Added to the
  default file and the three presets.
- `DiceExpression.modifier()`, `withModifier(int)` (`1d8+2+1d4` with `5` is `1d8+1d4+5`; dice-less
  expressions come back unchanged) and `hasDice()`.
- `AttackDelivery.isRanged()`; `AttackContext.isRanged()` delegates to it.
- `AttackResult.attackBonus()` and `SaveResult.saveBonus()`: the modifier the roll actually used,
  after the provider and any `PreAttackRollEvent` change. The readout already shows it:
  `d20 13-5=8 vs AC 10`, `HIT 1d6+4 = 8`, `save d20 8+5=13 vs DC 13`.
- Documented in `docs/api.md` and `docs/rules-config.md`. Unit tests cover dice-modifier replacement,
  every resolution rule, last-wins registration, the rules key, the damage floor and the log-once
  behaviour. GameTests on both loaders cover no provider matching 0.2.6, each roll type replaced (with
  readouts), an empty answer, the toggle, driven attacks and explicit bonuses, the damage floor on a
  hit and a failed save, and a dice-less hit.

### Changed

- A hit's rolled damage is at least 1 (was 0), with or without a provider: HIT, CRIT under every crit
  rule, and a failed save's rolled dice. Only dice that can roll 0 or less are affected (`1d4-3`, a
  negative provided modifier); no shipped profile can. The floor comes before `PostAttackRollEvent`,
  multipliers and resistances, so a listener's zero and immunities still stand. With `damage_dice`
  off the vanilla amount applies as before.

### Notes

- Explicit caller values are not Critfall's bonus and never go to the provider:
  `AttackContext.withAttackBonus`, `withDamageDice`, and the `saveBonus` of `RollService.savingThrow`.
- `RollService.effectiveEntity` and `/critfall inspect` show Critfall's own values, not the
  provider's, since the provider answers per attacker/target pair (noted in `docs/commands.md` and
  `docs/api.md`).
- No KubeJS binding: Critfall has no KubeJS plugin (scripts use `Java.loadClass`), so there was no
  one-line place to add it.

## [0.2.6] - 2026-07-22

Roll detail on results and the feedback payload. Closes the gap logged in 0.2.4 and again in 0.2.5:
results and payloads carried only the resolved d20 face, so neither a consumer nor Critfall's own
readout could show **how** a roll was made — only what it landed on.

### Added

- `RollDetail(RollMode mode, int kept, OptionalInt dropped)` in `studio.modroll.critfall.api.dice`:
  how one d20 check was rolled. `kept` is the face the check resolved on (the same value as the
  result's `natural`); `dropped` carries the other face when advantage/disadvantage rolled two and is
  empty under `NORMAL`, so a normal roll never reports a phantom second die. `hasTwoDice()` is the
  render-both-faces test; `RollDetail.normal(int)` builds the plain case.
- Roll detail on every d20 result: `AttackResult.roll()`, `SaveResult.roll()`, and — because a contest
  has two sides with independent modes — `ContestResult.initiatorRoll()` / `opponentRoll()`.
  `AttackResult.withDamage` preserves it. The pre-existing constructors are unchanged and default to
  `RollDetail.normal(natural)`, so existing callers compile and behave exactly as before.
- `RollService.savingThrow(target, saveBonus, dc, RollMode)`: a saving throw rolled with
  advantage/disadvantage, so a save's roll detail can carry two dice. The three-argument overload is
  unchanged and rolls `NORMAL`.
- `RollFeedbackPayload` now carries `rollMode`, `droppedNatural`, and `defenderAcBonus` (with
  `roll()` and `baseArmorClass()` derived from them); `SaveFeedbackPayload` carries `rollMode` and
  `droppedNatural` with the same `roll()`. All are additive, appended after the existing fields in
  both the record and the stream codec, and default to the plain case (normal roll, no dropped die,
  no AC modifier) — the previous constructors still exist and produce byte-identical payloads to
  0.2.5 for a plain attack. The 0.2.5 note that surfacing the AC split on the wire needed a payload
  rework is now resolved.
- Richer Critfall readout. A roll made with advantage or disadvantage shows both dice and which was
  kept (`d20 adv 7/18 → 18+4=22 vs AC 14`), and a situational defender-AC modifier shows the split
  (`vs AC 16 (14+2)`, `vs AC 8 (10-2)`). Saving throws get the same treatment
  (`save d20 adv 6/17 → 17+2=19 vs DC 13`). A plain normal roll against an unmodified AC renders
  exactly as before (`d20 13+3=16 vs AC 10`) — the extra detail costs characters only when there is
  something to say. It rides the existing `rolls` client toggle and the modless action-bar fallback
  with no new switch.
- Documented in `docs/api.md` (reading both dice off an advantage roll) and `docs/client-feedback.md`.
  Unit tests cover the detail type, advantage/disadvantage/normal on attacks, saves and both contest
  sides, the readout in every form, and codec round-trips including the no-advantage and negative-AC
  cases; GameTests on both loaders cover an api-only caller reading roll detail off a driven attack
  result, the detail and AC split reaching the dispatched payload, and a plain attack still carrying
  neither.

## [0.2.5] - 2026-07-21

A per-attack defender-side AC modifier, from Critfall: Initiative's M5b (cover) need for a situational
"this target is harder to hit for this attack" that the attacker-side levers could not express.

### Added

- `AttackContext.withDefenderAcBonus(int)`: a situational modifier to the **defender's** AC for one
  driven attack — cover, prone-at-range, magical protection, or a penalty (flanked, restrained).
  Effective AC for the roll becomes `effectiveEntity(target).armorClass() + defenderAcBonus`, for both
  `performAttack` and the resolve-only `attackRoll`. It is per-attack and non-persistent (it never
  mutates the entity's profile), negatives are allowed (not clamped), and it defaults to `0` so
  existing callers are unaffected byte for byte. It replaces the old workaround of a negative
  `withAttackBonus`, which is numerically identical but misreports a defended target as a weakened
  attacker. The modifier shifts the to-hit threshold only — it is independent of
  advantage/disadvantage, and crit/fumble stay natural-based (a nat 20 still hits and crits, a nat 1
  still misses and can fumble).
- `AttackResult` now exposes the split so consumers and feedback can report honestly rather than infer:
  `armorClass()` is the **effective** AC the roll faced, `defenderAcBonus()` is the applied modifier,
  and `baseArmorClass()` (`= armorClass() - defenderAcBonus()`) is the defender's own AC — enough to
  render "AC 14 (+5)". Documented in `docs/api.md`; unit tests plus GameTests on both loaders cover a
  positive bonus turning a hit into a miss (and the control without it), a negative bonus turning a
  miss into a hit, crit/fumble staying natural-based through an extreme modifier, and the result
  exposure, all via the RNG seam.

### Notes

- Critfall's own S2C feedback readout shows the effective AC (`vs AC 17`) but does **not** break it
  down as `17 (10+7)`: surfacing the split on the wire would need the same feedback payload/codec
  rework declined in 0.2.4. The split lives on `AttackResult` for consumers that render their own
  readout; the payload gap stays on record for a future release.

## [0.2.4] - 2026-07-19

Contested rolls as a first-class primitive, from Critfall: Initiative's repeated need for opposed
checks (M4b Hide, M5 Shove).

### Added

- `RollService.contest(initiator, opponent, ContestContext)` → `ContestResult`: a D&D 5e contested
  (opposed) check — both entities roll a d20 + their supplied bonus, higher total wins. Rolls go
  through the same injectable combat roller as attacks and saves, so a consumer forces both sides in
  its own tests via the RNG seam. `ContestContext` carries each side's bonus and an independent
  `RollMode` (advantage/disadvantage per side); the initiator rolls first. `ContestResult` reports
  both naturals, both totals, `winner()` (`ContestSide.INITIATOR`/`OPPONENT`), and `initiatorWins()`.
  **Ties go to the opponent** (5e default: the initiator's check fails), expressed as strict
  `initiatorTotal > opponentTotal`; a consumer wanting the opposite compares the exposed totals
  itself, so no config flag is needed. Bonuses are caller-supplied — Critfall stays a dice engine and
  does not model skills or ability scores; the entities are in the signature for identity and so a
  future data-driven per-entity modifier map stays a non-breaking addition. Contests emit no Critfall
  readout (the attack-shaped feedback payload does not fit an opposed roll); consumers present the
  result themselves. Documented in `docs/api.md`; unit tests plus GameTests on both loaders cover the
  winner/totals, the tie rule, per-side advantage/disadvantage, and an external-style api-only caller.

### Notes

- The M4b/M5 feedback-payload gap (roll mode and both d20 faces are not on `RollFeedbackPayload` /
  `AttackResult`, so consumers annotate their own) was **not** addressed: exposing them would thread
  a roll mode and the dropped d20 through `AttackResult` and the whole feedback codec/renderer for an
  attack-shaped payload unrelated to contests. It stays on record for a future release.

## [0.2.3] - 2026-07-17

API additions from Critfall: Initiative's M3 integration findings.

### Fixed

- Driven damage is no longer swallowed by invulnerability frames: the `hurt` behind
  `RollService.performAttack` / `applyRolledDamage` clears the target's hurt cooldown before it
  lands, so each driven attack applies its full rolled damage even when the target was hit moments
  earlier (a turn-based swing, an opportunity attack, or several attackers focusing one target in a
  round). The hurt re-arms the cooldown exactly as a normal successful hit would, so vanilla i-frame
  behaviour for ordinary real-time damage is unchanged. GameTests on both loaders prove two driven
  attacks in the same tick both land in full and that non-driven damage still respects i-frames.

### Added

- `RollService.isDrivenDamage(LivingEntity)`: a public query for consumers to detect that the hurt
  currently being applied to an entity is a Critfall-driven attack (the damage from `performAttack`),
  as opposed to real-time vanilla or other-mod damage. Callers checked it from inside their own
  loader damage listeners to exempt Critfall's driven attacks from their handling, instead of
  mirroring Critfall's internal guard. Documented in `docs/api.md`; GameTests on both loaders prove
  an external-style damage listener reads the correct value for driven vs non-driven damage.

## [0.2.2] - 2026-07-15

API additions from Critfall: Initiative's M1 integration findings.

### Added

- `CombatInteractionEvent` + `CritfallEvents.onCombatInteraction(...)`: an observe-only API event
  that fires the moment the damage interception detects a damaging interaction between two living
  entities — server-side, at the same loader-parity point on NeoForge and Fabric, and before all of
  Critfall's own gating and resolution. It therefore fires even when the damage is subsequently
  cancelled (a miss/fumble or a listener cancel/veto), exempt/always-hits, a vanilla passthrough,
  dry-run, or involves suppressed participants — so an orchestrator (e.g. Critfall: Initiative) can
  detect combat without listening to the raw loader damage events and out-prioritizing Critfall's
  listener. The precise firing contract is documented in `docs/api.md`; GameTests on both loaders
  prove it fires for a normal hit and still fires when the damage is cancelled.
- `CombatSuppression.suppressedUuids()`: an unmodifiable read-only view of every currently
  suppressed UUID across all mods, so consumer tests can write global suppression leak checks
  instead of probing known UUIDs one by one.

### Changed

- The global suppression wipe is no longer part of the normal production API surface:
  `CombatSuppression.clear()` was renamed to `clearAllForTesting()` (test cleanup only — in
  production it would destroy every mod's running encounters). Critfall clears suppression state
  internally on server stop; the production mutation surface stays per-UUID `suppress`/`release`.

## [0.2.1] - 2026-07-14

API-hardening release from Critfall: Initiative's M0 integration findings.

### Fixed

- The release Fabric jar no longer declares `fabric-gametest` entrypoints (caught by Initiative M0):
  the entrypoint classes delegated to shared scenario bodies that are deliberately not shipped, so
  any consumer running Fabric GameTests with Critfall installed saw all of Critfall's tests fail
  with `NoClassDefFoundError`. The delegators and a dev-only `critfall_gametest` mod json moved to
  a `gametest` source set that only the `runGametest` run loads, mirroring the NeoForge layout.
- The NeoForge registration shims (and the `critfall:empty` gametest structure) likewise moved out
  of the release jar into the `gametest` source set, and a `verifyNoGametestInReleaseJar` check —
  wired into `./gradlew check` for every module — fails the build if gametest classes, entrypoints,
  or resources ever leak into a release jar again.

### Changed

- The public API surface is now self-contained: every type appearing in an `api` signature lives in
  `studio.modroll.critfall.api` (or an `api` subpackage), as `docs/api.md` always claimed. Relocated
  (packages only — no behavior change): the `dice` package → `api.dice`; `AttackResult` and
  `AttackOutcome` → `api.combat`; `CombatEngine.SaveResult` → top-level `api.combat.SaveResult`;
  `ConsequenceLine` and `RollFeedbackPayload` → `api.feedback`
  (`ConsequenceLine.durability(Rules.DurabilityMode)` became `durability(boolean broken)` to shed
  its internal-type parameter). Consumers compiled against 0.2.0's unofficial locations must
  re-import; the semver stability promise now formally covers these types.

### Added

- A deterministic-testing RNG seam for consumers: `RollService.setRoller(DiceRoller)` /
  `RollService.resetRoller()` let an external mod script exact die faces (nat 1, nat 20) in its own
  tests, the same way Critfall's tests do. Documented (test scope only) in `docs/api.md`.
- `maven-publish` wiring: `./gradlew publishToMavenLocal` publishes `studio.modroll:critfall-common/-neoforge/-fabric` so external mods (e.g. Critfall: Initiative) can consume the API as a real Maven artifact until the Modrinth maven artifact exists.

## [0.2.0] - 2026-07-08

### Added

- Delivery-aware profile and flavor matching (issue #3): item profiles and flavor pools accept an optional `"delivery": ["melee" | "projectile" | "thrown" | "spell"]` list restricting them to how the attack was delivered, so hybrid weapons resolve differently per use — a thrown trident and a melee trident stab now pick different dice and different flavor lines. The automatic pipeline detects THROWN for any projectile that is its own launcher (tridents, snowball-likes, and modded throwing weapons following the same pattern); API attacks already carry `AttackContext.delivery`. Resolution ties (same priority, same specificity) prefer the delivery-restricted profile. The shipped trident flavor pool split into `critfall:trident_melee` / `critfall:trident_thrown` (new melee stab lines; the old `critfall:trident` pool id and `critfall.flavor.trident.*` lang keys are gone). See `docs/datapack-formats.md`. Flavor for thrown weapons also now matches the weapon that flew instead of the (empty) hand that threw it.

- Spell-mod compatibility verified in a running game (issue #2): Iron's Spells 'n Spellbooks 1.21.1-3.16.2 and Ars Nouveau 5.12.1 (plus GeckoLib/Curios/Player Animator/Iron's Lib) now load in dev runs via a `localRuntime` dependency set from the Modrinth maven, and `SpellModCompatGameTests` drives damage through each mod's own damage-source factories: self-cast spell damage classifies as SPELL (not MELEE) and rolls a d20, school-tag save profiles resolve as saving throws with half damage on success, ground-AoE tick types (`fire_field`, `sourceberry_bush`) never roll, casterless Ars damage passes through, and `attack_rolls.spells: false` restores vanilla spell damage while melee rolls stay on. `docs/compat.md` upgraded from source-reading to verified-against-versions; note there that ISS 3.16.x deals `evocation_magic` (not `fire_magic`) for Fireball.

### Fixed

- Pre-release hardening audit (`docs/audit-0.2.md`) — correctness/robustness only, no behavior or balance change:
  - The per-attacker fumble-cooldown and per-target flavor-cooldown maps no longer grow without bound on a long-running server: entries are skipped when the cooldown is disabled, expired entries are pruned once the maps grow, and both loaders clear them on server stop. This also fixes a singleplayer bug where a cooldown stamped in one world could suppress fumbles (or flavor lines) near-permanently in a newer world whose game clock was behind.
  - An outcome table whose effect weights sum past `Integer.MAX_VALUE` is now rejected at datapack load instead of crashing the server with a negative-sided die the first time the table fires.
  - A weapon profile whose damage dice already sit at the 100-term engine cap no longer crashes the server when the attribute-derived flat bonus is appended mid-hit — the bonus is dropped, same policy as ammo dice.
  - The S2C roll-feedback decoder bounds the consequence-list length (max 64), so a hostile or corrupted server produces a clean decode-error disconnect instead of an `OutOfMemoryError` on the client.
  - `rules.json` `global_damage_multiplier` must now be a positive **finite** number: `1e999` (which overflows to Infinity and would deal infinite damage on every hit) falls back to 1.0 with a warning, as NaN would too.

### Changed (internal, from the audit)

- Profile/flavor/spell resolution is memoized per registry id (and delivery) instead of linearly scanning every loaded profile 4–6 times per hit; the cache is invalidated on every `/reload` store swap. Derivation's damage-dice table and the attribute-bonus terms are pre-parsed, removing per-hit dice-expression string parsing. Resolution semantics, RNG draw order, and canonical dice are unchanged (pinned by tests). Low-severity items deliberately not fixed are tracked in `docs/deferred-issues.md`.

- API-driven attacks (`RollService.performAttack`) no longer double-dip armor: the damage they apply now bypasses vanilla armor reduction exactly like the automatic pipeline (AC already stood in for armor), governed by the same `balance.disable_vanilla_armor_reduction` flag. The `hurt` inside `performAttack` also never re-rolls through the automatic interception, even when the participants were not suppressed. GameTests prove API and automatic attacks deal identical final damage to an armored target on both loaders.

### Changed

- Flavor-line quality pass (issue #4, text-only): rewrote placeholder-tier kill lines (sword, unarmed) and added five new weapon-category flavor pools — pickaxes, shovels, hoes, shears, and fishing rods — so those swings get dedicated crit/fumble/kill lines instead of no flavor at all. `docs/datapack-formats.md` shipped-pool list updated to match.

## [0.1.0] - 2026-07-05

### Added

- Pack-dev tooling (M9): `/critfall generate [missing] [confirm]` scans every living entity type and weapon-like item and writes a complete, editable datapack of derived profiles to `<world>/datapacks/critfall_generated/` (run `/reload` to load). Overwriting an existing generated pack requires `confirm`; only the profile folders it owns are rewritten, and a `README.txt` + `pack.mcmeta` note that the files are regenerated. `missing` emits only ids no loaded profile matches. See `docs/commands.md`. (M9)
- Coverage report (M9): `/critfall report` exports `entities-<timestamp>.csv/.json` and `items-<timestamp>.csv/.json` to `critfall-reports/`, listing every entity/item, whether an explicit profile or a fallback drives it, and the effective values — reviewable in a spreadsheet. (M9)
- Dry-run mode (M9): `rules.json` `dry_run.enabled` computes and displays every roll while vanilla damage still applies and no outcome effect fires, so pack devs calibrate during normal play without breaking a pack. The readout is prefixed `dry-run · `. Consequences are suppressed entirely (effect and readout); it is for calibrating hit/damage math, not previewing which fumble effect would fire. (M9)
- Three shipped `rules.json` presets in `examples/presets/` — **Tempered** (defaults), **Classic** (confirmation off, cooldown 0), **Lite** (crits/fumbles off) — with a parse-clean test. See `docs/presets.md`. (M9)
- Documentation site content in `docs/` (index, quickstart, FAQ, presets, commands), an example datapack in `examples/datapack/` (tuned boss + custom blade + fumble table + flavor pool), and a `Publish` GitHub Actions workflow (`mc-publish`) that ships the NeoForge and Fabric jars to Modrinth, CurseForge, and the GitHub release on tag. A versioning policy is documented in `CONTRIBUTING.md`. (M9)

### Added (pre-0.1.0 development)

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
- Projectile attack rolls: arrows, thrown tridents, and thrown items now roll d20 on impact instead of applying vanilla damage. Dice come from the launcher's item profile (new default profiles: bow `1d8`, crossbow `1d10`; the trident profile covers throws too) with the flat bonus derived from the vanilla projectile damage — so Power enchantments and draw strength still count. Ammunition with its own item profile ADDS its dice on top of the launcher's. Item-less mob projectiles use the new entity-profile `damage.ranged` key (ghast `2d6`, blaze `1d10`, shulker `1d8`, wither skull `2d8`). Zero-damage projectiles (snowballs/eggs) and ownerless ones (dispensers) stay vanilla; `attack_rolls.projectiles: false` restores vanilla wholesale. New `critfall:default_ranged` fumble table (durability wear or nothing); fumble weapon-effects hit the launcher in whichever hand still holds it and no-op for thrown tridents. (M5)
- Spell damage classification: damage tagged `#critfall:spell` — pre-populated with `#neoforge:is_magic` and the Iron's Spells 'n Spellbooks / Ars Nouveau damage types as optional entries — plus any untagged indirect living-caused damage now classifies as SPELL and rolls. The spell tag beats the projectile/melee heuristics because both major spell mods make the caster the direct entity of self-cast spells (research in `docs/compat.md`). New `spell_profile` datapack registry matched by damage type id/tag: `resolution` (attack roll vs save), optional `damage` dice, `attack_bonus`, `crit_range`, `save` block, `priority`. Unprofiled spell damage follows the new `fallbacks.unknown_spell` mode. (M5)
- Saving throws for AoE-feel spells: a spell profile with `"resolution": "save"` makes the TARGET roll d20 + `save_bonus` (new optional entity-profile key) vs the profile's DC — failure takes full damage, success takes half (rounded down) or nothing per `save.on_success`. No crits, fumbles, or outcome tables on saves. New `spells.saves` rules.json group (`enabled`, `default_dc` 13, `on_success` "half"); with saves disabled, save-profiles resolve as attack rolls. (M5)
- Iron's Spells 'n Spellbooks / Ars Nouveau compat research and tuning guide in `docs/compat.md`; their ground-AoE/DoT damage types (`fire_field`, `poison_cloud`, `dragon_breath_pool`, `blood_cauldron`, `heartstop`, `sourceberry_bush`) plus `#neoforge:is_poison`/`is_wither` added to `#critfall:exempt` as optional entries so effect ticks never roll. (M5)
- `/critfall inspect` now shows profiled ranged damage and save bonus; the dice engine gained `DiceExpression.plus()` for combining expressions. 13 new GameTests (projectile + spell/save paths) and 20 new unit tests. (M5)
- Client feedback module: one optional S2C packet (`critfall:roll_feedback` / `save_feedback`) carries the resolved roll; the client renders a roll readout (action bar), narrative flavor line (chat), sound, and particles, each independently toggleable in `config/critfall/client.json`. The server stays fully functional with no client mod — vanilla/modless clients get a plain action-bar readout that **includes the consequence announcements** ("NAT 1 — no fumble", "FUMBLE — weapon nearly broken!", "CRIT"), rendered server-side via the same formatter with translation-fallback text so a client without Critfall lang stays legible. (M6)
- Outcome consequences are now announced: `OutcomeExecutor` reports which effect fired (durability, redirected hit with the victim's name, drop, stumble, self-damage, applied effect, knockback), surfaced in both the client readout and the modless fallback. (M6)
- Narrative flavor lines: new `flavor_pool` datapack registry (`data/<ns>/critfall/flavor_pool/`), matched to the attack's weapon with the existing `matches`/`priority` resolution, mapping `crit`/`fumble`/`kill` to translation-key pools. Ships default pools + `en_us` lang for swords, axes, ranged (bow/crossbow), trident, mace, and a `minecraft:air` catch-all (unarmed/mobs), two lines per outcome. See `docs/client-feedback.md` and `docs/datapack-formats.md`. (M6)
- Server-authoritative flavor anti-spam (`rules.json` `feedback.flavor`): lines fire only on crit/fumble/kill, at most one non-priority line per target per `cooldown_ticks` (default 20); nat-20/nat-1 always send and reset the cooldown. Flavor selection draws from a separate `RollService.feedbackRoller()` so it never perturbs combat rolls. (M6)
- `rules.json` `feedback` gains the `flavor` block; `feedback.sounds`/`feedback.particles` moved to the client config (rendering is client-side) and are ignored with a warning if present. PLAN §4.5/§4.2 amended to match. (M6)
- New unit tests (ConsequenceLine, FlavorPool, FlavorSelector, FlavorCooldowns, payload codecs, CombatText renderer, FeedbackBuilder anti-spam, ClientConfig) and GameTests (crit payload dispatch, modless fallback carries consequence text). (M6)
- Public API (`studio.modroll.critfall.api`, M7): a stable surface for other mods and KubeJS. `RollService` rolls dice (`roll("2d6+3")`), resolves a full attack (`performAttack`) or just the roll (`attackRoll`) with an `AttackContext` (delivery method: melee/projectile/thrown/spell — issue #9), queries effective profiles (`effectiveEntity`/`effectiveItem` — AC, bonuses, dice, crit range, from datapack profile or derived fallback), and emits feedback. Loader-agnostic events (`PreAttackRollEvent` — modify bonus/advantage or cancel; `PostAttackRollEvent` — adjust damage or veto; `FumbleEvent`; `CritEvent`) fire from one shared `AttackPipeline` used by both the automatic pipeline and the API, via the `CritfallEvents` registry (a throwing listener is isolated). Per-entity `CombatSuppression` makes the automatic real-time interception stand down so an external orchestrator (the §12 turn-based companion mod) can own an entity's combat. Feedback is emittable by API consumers through a `FeedbackSink` seam. The internal RNG/rules holder was renamed `RollService`→`RollRuntime`. See `docs/api.md` and `/examples/kubejs/`. A GameTest drives a complete attack purely through the API with the automatic pipeline suppressed. (M7)
- **Fabric loader support (M8):** Critfall now ships for Fabric 1.21.1 with full behavioural parity to NeoForge. A new `fabric` module (Fabric Loom + `officialMojangMappings()`) wires the shared `common` code to Fabric hooks: `ServerLivingEntityEvents.ALLOW_DAMAGE` plus a single minimal `LivingEntity` mixin drive damage interception (Fabric has no amount-modifying damage event); `CommandRegistrationCallback`, `ResourceManagerHelper`, `PayloadTypeRegistry`/`ServerPlayNetworking`, `ClientPlayNetworking`, and `ServerLifecycleEvents` cover commands, `/reload` listeners, feedback packets, client rendering, and suppression cleanup. The ~490-line damage orchestration was extracted from the NeoForge handler into a loader-agnostic `combat.DamageInterception` behind a tiny `IncomingDamage` seam, so both loaders run the identical pipeline. The 57 GameTests now live once in a shared `common/src/gametest` source set and run on **both** loaders (NeoForge `runGameTestServer` + Fabric `runGametest`), 57/57 each. Behaviour differences are limited to plumbing and documented in `docs/loader-parity.md`. (M8)
