# Commands & calibration

All `/critfall` subcommands require permission level 2 (operator). They are server-side and safe to
run at any time.

## `/critfall generate [missing] [confirm]`

Scans every living entity type and weapon-like item in the loaded pack and writes a complete,
editable datapack of **derived** profiles to `<world>/datapacks/critfall_generated/`. Run `/reload`
afterwards to load it. This is the fastest way to start tuning a big pack — you edit numbers in JSON
instead of authoring hundreds of files from scratch.

- `/critfall generate` — every living entity and weapon-like item.
- `/critfall generate missing` — only ids that no currently-loaded profile matches (the modded long
  tail).
- If `critfall_generated` already exists, the command refuses to overwrite until you add `confirm`
  (`/critfall generate confirm`, `/critfall generate missing confirm`). Only the generated
  `entity_profile/` and `item_profile/` folders are rewritten; anything else you put in that pack is
  left alone. A `README.txt` and the `pack.mcmeta` description note that the generated files are
  overwritten — copy anything you want to keep into your own pack.

The generated pack uses its own namespace (`critfall_generated`) and `priority: 0`, so your
hand-written profiles (higher priority, or in another pack) win over it.

## `/critfall report`

Exports a coverage report of every entity and item to `<game-dir>/critfall-reports/`:

- `entities-<timestamp>.csv` / `.json` — id, source (`profile:<id>` or `fallback`), armor class,
  attack bonus, melee dice, crit range, ranged dice, save bonus.
- `items-<timestamp>.csv` / `.json` — id, source, dice, `modifier_from`, crit range.

Open the CSV in a spreadsheet to see at a glance which mobs and weapons still rely on derived
fallbacks versus an explicit profile, and what the effective values are. The chat summary reports the
profiled/fallback split.

## `/critfall inspect [entity]`

Shows the effective combat stats for a living entity and **which profile file won** (or that stats
are derived). With no argument it inspects whatever your crosshair points at (32-block raycast,
blocks occlude). Essential for debugging tag-priority collisions.

## `/critfall check [item]`

Same idea for an item: the matched item profile, its dice, `modifier_from`, crit range, and any
referenced fumble/crit tables (with a warning if a referenced table is not loaded). With no argument
it checks your main-hand item.

## Dry-run mode (calibration)

Set `"dry_run": { "enabled": true }` in `config/critfall/rules.json` and `/reload`. Critfall then
computes and displays every roll, but **vanilla damage still applies and no fumble/crit effect
fires** — so you can calibrate a pack during normal playtesting without breaking it. The roll readout
is prefixed `dry-run · ` so you never mistake a shown roll for a real hit.

Dry-run suppresses outcome-table consequences entirely (both the effect and its readout line); it is
for calibrating hit/damage math, not for previewing which fumble effect would fire. Turn it off to go
live.
