# Dice expression syntax

The dice engine (`studio.modroll.critfall.api.dice`) parses tabletop-style dice notation. This is the
format used everywhere a profile or config asks for dice (e.g. `"damage": { "melee": "2d6+3" }`).

## Grammar

```
expression := ["-"] term (("+" | "-") term)*
term       := dice | constant
dice       := [count] "d" sides [("kh" | "kl") [keep]]
constant   := integer
```

- Case-insensitive, whitespace is ignored: `2D6 + 3` equals `2d6+3`.
- `count` defaults to 1: `d20` is `1d20`.
- `kh` keeps the highest dice, `kl` the lowest; `keep` defaults to 1: `2d20kh` is `2d20kh1`.
- Advantage is `2d20kh1`, disadvantage is `2d20kl1` (the `RollMode` enum builds these for you).

## Examples

| Expression   | Meaning                                          |
| ------------ | ------------------------------------------------ |
| `1d20`       | one twenty-sided die                             |
| `2d6+3`      | two d6 plus a flat 3                             |
| `2d20kh1`    | roll two d20, keep the highest (advantage)       |
| `2d20kl1`    | roll two d20, keep the lowest (disadvantage)     |
| `4d6kl3`     | roll four d6, keep the three lowest              |
| `1d8+2d6+3`  | mixed dice pools plus a modifier                 |
| `1d8-1d4`    | dice can be subtracted                           |
| `5`          | a flat constant (no dice)                        |

## Limits

Expressions come from datapack JSON, so hostile or typo'd input must not hurt the server.
Parsing rejects anything outside these limits with a `DiceParseException`:

- at most **100 dice per term**, **1000 sides per die**, **100 terms**, **256 characters**
- dice count, sides, and keep count must be at least 1; keep count can't exceed the dice count

## Java API

```java
DiceExpression expr = DiceExpression.parse("2d6+3");   // throws DiceParseException on bad input
DiceRoller roller = new DiceRoller(rng);               // rng is ALWAYS injected
RollResult result = roller.roll(expr);

result.total();      // grand total
result.dice();       // every die rolled, in order, with sides/value/kept flag
result.keptDice();   // only dice that count (kh/kl drops the rest)
result.modifier();   // sum of flat constant terms
expr.minValue();     // smallest possible total
expr.maxValue();     // largest possible total (used by the "max damage on crit" rule)

roller.d20(RollMode.ADVANTAGE);   // 2d20kh1 convenience for attack rolls
```

Rolls are fully deterministic for a given RNG: tests inject a seeded `java.util.Random` or a
scripted sequence to force exact outcomes (nat 1 / nat 20).
