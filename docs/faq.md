# FAQ

### Why doesn't fire tick / poison / fall damage roll to hit?

Damage-over-time and environmental damage would roll every tick, which feels awful and fires fumbles
constantly. Those damage types are tagged `#critfall:exempt` (pre-populated with fall, drown,
starve, cactus, fire tick, poison, wither, potion magic, and common tech/spell AoE pools) and pass
straight through as vanilla. Add your pack's own tech-mod damage types to that tag. There is also
`#critfall:always_hits` for damage that should skip the to-hit roll but still roll damage dice.

### The fallback stats for my modded mobs are wrong. Is that a bug?

No — fallbacks give *plausible* values from vanilla attributes, not *balanced* ones. A lich should be
low-AC/high-save and a knight high-AC, but that identity isn't derivable from an armor number. Run
`/critfall generate`, then hand-tune the ~30 mobs and bosses that matter. `/critfall report` shows
you which entities still rely on fallbacks.

### Does armor count twice?

No. When `balance.disable_vanilla_armor_reduction` is on (the default), rolled damage bypasses
vanilla base-armor reduction because the target's Armor Class already represents armor. Enchantment
protection, the Resistance effect, and absorption still apply. See
[design-decisions.md](design-decisions.md).

### Fumbles fire too often in fast combat.

Real-time Minecraft rolls far more often than a tabletop. Use the config safeguards (all on in the
Tempered preset): the confirmation roll, the fumble cooldown, and `percent_loss` durability instead
of `set_to_1`. Or switch fumbles off entirely with the Lite preset. These are config changes, not
code.

### My mobs feel spongey now — everything takes longer to kill.

Misses cut effective DPS by a large margin, so packs tuned around vanilla time-to-kill may feel slow.
Raise `balance.global_damage_multiplier`, or lower mob HP (pairs well with Damage Control-style
mods). This is expected and documented, not a defect.

### A spell mod's damage isn't rolling (or is rolling when it shouldn't).

If a spell mod fires generic damage without proper damage-type tags, Critfall can't classify it. Tag
the damage type (fire/necrotic/…) or exempt it, and add a `spell_profile` if you want save-based
resolution. Iron's Spells 'n Spellbooks and Ars Nouveau are researched with tuning guidance in
[compat.md](compat.md) — treat that as a starting point to verify in your pack, not a guarantee.

### Some boss ignores Critfall entirely.

Mods that bypass the damage event (direct `setHealth()` or a custom damage pipeline) never reach our
hook. Multipart/phase bosses (Ender Dragon, Wither shield) may need per-part or per-phase overrides.
Document known offenders for your pack; some bosses simply need hand-tuning.

### How do I test changes without breaking my playthrough?

Turn on dry-run (`"dry_run": { "enabled": true }`): rolls are shown but vanilla damage still applies
and no consequences fire. Calibrate during normal play, then turn it off. See
[commands.md](commands.md).

### Do players need the mod installed?

No. The server is authoritative and works with vanilla/modless clients — they get a plain action-bar
readout (including the consequence announcements). Installing the client mod adds the richer roll
display, flavor lines, sounds, and particles, each toggleable in `config/critfall/client.json`.
