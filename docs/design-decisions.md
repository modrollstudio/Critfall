# Design decisions

Decisions with lasting consequences, and why they were made. Newest first.

## 0.2.23: An attack's damage dice on its result

**Decision:** `AttackResult` carries the damage dice it rolled, as `damageRolls()`, a list of `DieRoll` in roll
order. The record's earlier constructors stay and leave it empty.

**Why:** Chromatic Orb leaps when two of its d8s show the same number, so a spell has to see the faces, not only
the total. Rolling the dice itself would roll outside Critfall. The faces were already rolled inside the engine;
keeping them costs nothing and changes no roll.

## 0.2.22: Damage with no roll to hit

**Decision:** `performDamage` and `rollDamage` hurt a target by dice with no d20, credited to the attacker, and
show their own readout on a new optional channel, `critfall:damage_feedback`, like a heal's.

**Why:** Magic Missile never misses. A mod hurting with a damage source of its own would skip Critfall's
resistances by damage type and show no readout, so Spells could not stay on the public API. The damage is
applied as a hazard save's is, so armor, resistances and the global multiplier treat it the same. A new
packet, rather than another field on the heal's, leaves every existing packet as it was.

## 0.2.21: A roll can land later

**Decision:** `rollAttack`, `rollHazardSave` and `rollHeal` do everything their `perform` call does up to the
hurt: the dice, the events, an attack's outcome tables. The `Rolled` they return applies the damage or the heal
and shows the readout on `land()`, once. The `perform` calls are rolling and landing in one.

**Why:** A spell's bolt flies before it hits, and Spells rolls as the spell goes off, so nothing in flight can
change the result, then lands the hit on arrival, or drops it when the target is gone. The events stay with the
roll because they change it (advantage, Shield's raise). A save's resistances are read on landing, as a hit's
armor is, since that is when the hurt happens. One small generic handle keeps the API to three new calls.

## 0.2.20: AC sources count in rolls, not in the entity's own AC

**Decision:** Other mods add to AC through any number of `ArmorClassSource`s, asked after the one
`ModifierProvider`, in the order added, each with the AC so far. They count wherever an attack rolls (real time,
driven, hazard attacks) and in the new `RollService.armorClass`. `effectiveEntity`, `armorClassBreakdown` and
`/critfall inspect`'s own line stay Critfall's own AC, without the provider or the sources.

**Why:** Spells needs Shield, Mage Armor, Haste and others at once, and the provider slot is Checks'. Passing the
AC so far lets a source set a floor (Barkskin) without knowing the others. The breakdown cannot count sources:
Checks' own AC modifier reads `armorClassBreakdown(...).total()` as the AC of what is worn, so counting sources
there would ask the provider from inside the provider, and skew Checks' body armor floor. Sources follow
`modifier_providers.enabled`, so the server's "other mods' modifiers off" switch still means Critfall's own AC.

## 0.2.20: A reaction raises the AC of a roll already made

**Decision:** `PostAttackRollEvent.raiseArmorClass(int)` measures the roll again against the higher AC, turning
a hit into a miss when it falls short; a natural 20 still hits under `nat20_always_hits`. The raise goes into
the result's `defenderAcBonus`, and the event's `result()` changes at once.

**Why:** Shield is cast after the attacker rolls. Folding the raise into `defenderAcBonus` keeps `baseArmorClass`
the defender's own AC and gives the readout its existing split (`vs AC 19 (14+5)`) with no new field on the
wire. Changing `result()` at once lets a second reaction see the miss and stay out.

## 0.2.20: A shared damage roll, a heal readout and attack labels

**Decision:** `HazardSave.withRolledDamage` carries a roll made once for an area; each target still saves and
resists on its own. `RollService.performHeal` rolls, heals through vanilla `heal`, and sends a heal readout on
its own optional channel, `critfall:heal_feedback`, shown to the healer and, under `everyone`, the target.
`AttackContext.withLabel` names a driven attack; the label rides the roll packet, which moved to
`critfall:roll_feedback_v5`, and the readout puts it first, as a check readout does.

**Why:** The 5e area rule is one damage roll for everyone; a new record component at the end, with the old
constructor kept, changes nothing for existing hazards. Healing had no readout at all. A new channel rather
than a new `CheckFeedbackPayload` kind keeps older clients connected: they fall back to the action bar text.

## 0.2.19: A hazard's source is credited like an attacker

**Decision:** `HazardSave` takes an optional source entity (`withSource`). The hazard's damage source then
has it as both the direct and the causing entity, the same shape as a player's own attack, and Critfall
leaves the rest to vanilla: kill credit, experience, player only loot, mob anger, PvP and team rules, the
death message.

**Why:** Companion mods have hazards a player sets off (a breath attack, a lit barrel). Crediting through the
damage source gives them everything a normal attack gets without Critfall tracking kills itself. The source
is a new record component at the end, with the old constructors kept, so every existing call compiles and
behaves as before. The rolls stay the hazard's: the source adds no bonus and fires no attack events.

## 0.2.18: Armor reduces rolled hits again, and counts for half as much AC

**Decision:** `balance.disable_vanilla_armor_reduction` now ships **off**: a rolled hit is reduced by
vanilla armor like any other hit. To keep armor from counting twice, it buys half as much AC as before:
`AC = 10 + floor(armor / 4) + floor(toughness / 4)` (both divisors in `rules.json`: `armor_ac_divisor`,
`toughness_ac_divisor`). Full leather is AC 11, iron 13, diamond 17, netherite 18.

**Problem:** With armor only raising AC (the M2 decision below), armor cut how *often* a mob hit, never
how *hard*. Expected damage against good armor stayed at or below vanilla's, but each hit that landed was
as big as against a bare player: a vindicator killed a player in full netherite in about 2 landed hits,
where vanilla takes 6. Letting armor reduce damage on top of the old AC overshot the other way (a zombie
needed about 124 swings to kill a diamond-armored player, vanilla 29), because AC already took most hits
away.

**Effect** (Normal, 20 HP player, measured by the 0.2.18 report): strong mobs against diamond and netherite
now need 4–5 landed hits (vanilla 5–6), and swings to kill against armor sit around 1.2–2× vanilla, so armor
still pays off through misses. Unarmored and leather players are nearly unchanged.

**Old configs are migrated:** every `rules.json` written before 0.2.18 says
`"disable_vanilla_armor_reduction": true`, but only because it was the shipped default, and that behaviour is
what this release fixes. So a file below `format_version` 2 is moved once (`RulesMigration`): flag off, the
divisors written in where it names none, `format_version` 2, one log line. A pack maker who sets the flag back
to `true` afterwards keeps it.

## 0.2.18: Players get +5 to hit

**Decision:** `balance.player_attack_bonus` (default 5) is added to a player's derived attack bonus
(`floor(attack_damage / 2)`). A mob's attack bonus is tuned in its profile; a player's only ever derived,
and against mob AC 9–15 it left common weapons needing up to 2× vanilla's swings to kill. With +5 they need
1.14× on average (worst: a stone sword against an iron golem, 1.37×). One number in `rules.json` rather than
lower AC on every mob, which would also change fights between mobs.

## M2: Rolled damage bypasses vanilla armor reduction

*Superseded in 0.2.18 (above): the flag ships off, and armor counts for half as much AC.*

**Decision:** When an attack roll hits and Critfall applies rolled damage, vanilla's armor damage
reduction is zeroed out for that hit (`disable_vanilla_armor_reduction` flag, default **on**, in
the hardcoded M2 rules — it becomes a `rules.json` flag in M3).

**Problem:** Armor would count twice. A target's Armor Class is derived from its
armor and toughness attributes (`AC = 10 + floor(armor/2) + floor(toughness/4)`), so armor already
makes you *harder to hit*. If vanilla then also reduced the damage of the hits that land, heavily
armored targets would be nearly unkillable and the tabletop math would collapse.

**How:** The damage hook adds a reduction modifier on NeoForge's `DamageContainer` that returns
`0` for `Reduction.ARMOR`. This is surgical:

- **Enchantments still work.** Protection etc. use `Reduction.ENCHANTMENTS`, which we do NOT
  touch — enchantments don't feed the AC formula, so they don't double-dip; they stay as the
  vanilla layer of "magical" mitigation on top.
- **Mob effects (Resistance) and absorption still work**, same reasoning.
- **Armor durability still ticks** — vanilla damages armor pieces independently of the reduction
  math.
- **Non-rolled damage is untouched.** Exempt/environmental/projectile (until M5) damage takes the
  vanilla path, with normal armor reduction. Turning the flag off restores vanilla reduction for
  rolled hits only, per the "flags cleanly restore vanilla" rule.

**Verified by** the `rolledDamageBypassesVanillaArmorReduction` GameTest: a diamond-chestplate
husk (armor 10) takes exactly the rolled damage from a forced crit.

## M2: Only direct melee is rolled

Damage classification exists for all categories (`EXEMPT`, `ALWAYS_HITS`, `PROJECTILE`,
`ENVIRONMENTAL`, `MELEE`, `OTHER`), but M2 rolls only `MELEE` — a living attacker whose causing
entity equals the direct entity. Projectiles and spells pass through vanilla until M5, DoT and
environment stay vanilla forever via `#critfall:exempt` (pre-populated with all vanilla
DoT/environmental/AoE damage types). Explosions are exempt for now — AoE gets a saving-throw
treatment in M5 rather than a nonsensical to-hit roll.

## M2: Attack stats derive from live attribute values

- **AC** from the target's `generic.armor` + `generic.armor_toughness` (post-modifier, so
  Apotheosis-style gear affixes are respected automatically).
- **Attack bonus** = `floor(attack_damage / 2)`, capped at +12.
- **Damage dice** are derived from the *live incoming damage amount* (not a static weapon table),
  mapped to the dice expression with the same average (`3 → 1d6`, `7 → 2d6`, `8 → 2d6+1`…, above
  15 buckets of d12s). This means strength effects, mob equipment and modded weapons keep their
  vanilla balance in dice form — a 400-mod pack gets plausible dice with zero configuration.

These formulas are the M2 hardcoded fallback tier; M3's datapack profiles override them per
entity/item, and they remain the safety net for anything unprofiled.
