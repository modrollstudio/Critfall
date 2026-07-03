# Design decisions

Decisions with lasting consequences, and why they were made. Newest first.

## M2: Rolled damage bypasses vanilla armor reduction

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
