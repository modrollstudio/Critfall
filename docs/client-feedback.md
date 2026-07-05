# Client feedback (M6)

Critfall's combat resolves entirely on the server. The client feedback module renders that result —
roll readout, narrative flavor lines, sounds, particles — and is **fully optional**: the server works
with vanilla / non-Critfall clients, which fall back to a plain action-bar readout.

## What crosses the wire

Two server→client packets, both registered `optional()` so a client that doesn't understand them is
never disconnected:

- `critfall:roll_feedback` — one resolved attack roll: outcome, natural d20, attack total, AC,
  damage, dice notation, an optional flavor-line key, and the list of consequences that fired.
- `critfall:save_feedback` — one resolved saving throw: natural, total, DC, saved/failed, half/negate,
  dice, damage, optional flavor key.

The payloads carry only flat primitives + translation keys; the client builds all text from them.

## Modless / vanilla clients

A player without the Critfall client mod (or a vanilla client on a Critfall server) still gets a
**plain action-bar readout**, rendered server-side from the same payload — **including the consequence
announcements** ("NAT 1 — no fumble", "FUMBLE — weapon nearly broken!", "CRIT"). Because such a client
has no Critfall language file, every label and consequence uses translation *fallback* text, so it
stays legible in English. Flavor lines and sounds/particles are the only layers a modless client does
not get.

## Client config — `config/critfall/client.json`

Written with defaults on first launch. Each layer toggles independently (kitchen-sink packs hate HUD
spam); turning one off never affects the others.

```json
{
  "format_version": 1,
  "rolls": true,
  "flavor": true,
  "sounds": true,
  "particles": true
}
```

- `rolls` — the compact roll readout on the action bar.
- `flavor` — narrative flavor line in chat (only ever appears on crit/fumble/kill; see anti-spam).
- `sounds` — a short vanilla sound cue by outcome (crit / fumble / hit).
- `particles` — a small vanilla particle burst on crit/fumble.

Unknown keys warn and are ignored; `format_version` enables future migration.

## Server side — `rules.json` `feedback`

The server decides *what* to send and *whether* a flavor line is allowed; it never renders. See
[rules-config.md](rules-config.md#feedback).

- `roll_visibility` — `everyone` / `attacker_only` / `off`: who receives roll feedback at all.
- `flavor.enabled` — master switch for sending flavor lines.
- `flavor.cooldown_ticks` (default `20`) — per-target minimum ticks between non-priority flavor lines.

(PLAN's earlier `feedback.sounds` / `feedback.particles` were relocated here to the client config,
since rendering is client-side. Old configs carrying them load fine — the keys are ignored with a
warning.)

## Flavor lines & anti-spam

Flavor lines are datapack-driven message pools keyed by weapon category + outcome — see the
`flavor_pool` format in [datapack-formats.md](datapack-formats.md#flavor-pool). To add or replace
lines, ship a `flavor_pool` JSON (data pack) **and** the matching translation keys in a resource pack
`lang` file; the client resolves them, so they localize.

Anti-spam (server-authoritative, so it also throttles packets):

- Flavor lines fire **only on crits, fumbles, and kills** — ordinary hits/misses get the compact
  readout at most.
- **Per-target cooldown** (`feedback.flavor.cooldown_ticks`, default 20): at most one non-priority
  flavor line per target per window.
- **Priority:** a nat-20 crit or nat-1 fumble always sends its flavor line (bypassing and resetting the
  cooldown); a plain kill is gated by the cooldown.

Flavor selection uses a **separate feedback RNG** (`RollService.feedbackRoller()`), not the combat
roller, so a multi-line pool's random pick never perturbs the server-authoritative combat rolls (this
also keeps scripted-RNG GameTests deterministic).

## Kill detection

The "kill" that gates kill-flavor is **predicted** at damage time (the damage this hit will apply vs.
the target's current health), before other mods' post-hoc mitigators. It can occasionally mispredict
under those mods; it only affects the cosmetic flavor line, never gameplay.
