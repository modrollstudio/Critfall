# Deferred issues

Tracked findings from `docs/audit-0.2.md` that were deliberately **not** fixed in 0.2. Each entry
says why it was deferred and what would justify picking it up.

## From the 0.2 pre-release audit

### A5 (Low) — GameTest seams live in a production class
`neoforge/src/main/java/studio/modroll/critfall/neoforge/network/FeedbackDispatcher.java`

`lastRollPayload` and `actionBarSink` are mutable statics used by GameTests. Memory impact is a
single small record; the risk is conceptual (test hooks in shipped code), not operational.
Pick up if the dispatcher grows more seams — then extract a proper test double behind
`FeedbackSink` instead.

### C3 (Low) — `OutcomeExecutor.applyingEffects` is a plain static boolean
`common/src/main/java/studio/modroll/critfall/outcome/OutcomeExecutor.java`

Correct under the documented invariant (all entity damage runs on the server thread; effects never
nest; set/reset is try/finally). A `ThreadLocal` would defend against mods hurting entities
off-thread — which already violates vanilla's threading contract — at a hot-path cost. Revisit only
if a real off-thread damage path shows up in a bug report.

### D2-adjacent (Low) — `readEnum` on a hostile ordinal
`common/src/main/java/studio/modroll/critfall/feedback/RollFeedbackPayload.java`,
`SaveFeedbackPayload.java`

A crafted payload with an out-of-range enum ordinal throws `ArrayIndexOutOfBoundsException` in
decode, which the network layer already turns into a clean disconnect — the same behavior vanilla
payloads exhibit. Bounding it would only change the exception type. No action planned.

### B5 (Low) — small per-hit allocations
Short-lived `Optional`/record/lambda allocations in `DamageInterception.rollAndApply` and friends.
Nursery-collected, invisible next to the (now removed) per-hit scans and parses. Revisit only with
profiler evidence from a large server.
