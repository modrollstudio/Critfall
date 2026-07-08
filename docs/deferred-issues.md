# Deferred issues

Enhancements identified during development but out of scope for the milestone/issue that surfaced them. Tracked here until filed as GitHub issues.

## Environmental / creative-kill flavor lines

**Surfaced during:** issue #4 (flavor-line quality pass, 0.2.0).

**What's missing:** Flavor lines for environmental and creative-mode kills — fall damage, lava, drowning, and similar death causes — have no dedicated crit/fumble/kill lines. The current `flavor_pool` matching resolves against the *weapon* (`data/<ns>/critfall/flavor_pool/`, matched by item id/tag), and environmental deaths have no weapon to match against.

**Why deferred:** Text-only work (new lang keys + pool JSON) can't fill this gap by itself. It needs a death-cause / damage-source hook — a way to key a flavor pool off `DamageSource`/damage-type id instead of (or in addition to) the wielded item, similar to how `spell_profile` already matches by damage type. That's a resolution-logic change, not a content edit, so it's out of scope for a flavor text pass.

**Future enhancement:** Add damage-source-keyed flavor pool matching (e.g. a `damage_type` match key alongside the existing weapon `matches`), then ship `en_us` lines for fall, lava, drowning, and other environmental causes.
