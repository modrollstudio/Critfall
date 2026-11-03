# Design decisions

Decisions with lasting consequences, and why they were made. Newest first.

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

**Problem (PLAN.md §9.4):** Armor would count twice. A target's Armor Class is derived from its
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
