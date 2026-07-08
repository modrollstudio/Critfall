# Critfall branding

Logo assets for the Critfall mod (Modroll Studio). The mark is a d20 seen from above,
vertex-up, with the "20" pentagon face as a hot "critical" accent and a small impact
spark beneath it (the *fall* in Critfall). All SVGs are hand-authored vector paths with
transparent backgrounds and a square 512×512 viewBox.

## Files

| File | What it is | Where it's used |
|------|------------|-----------------|
| `critfall-logo.svg` | Full-color mark with impact spark (master source) | Anywhere large: README headers, banners, embeds |
| `critfall-logo-plain.svg` | Full-color mark without the spark, die centered | Contexts where the spark would clutter (tiny sizes, tight crops) |
| `critfall-logo-mono.svg` | Single-color mark, facet lines and "20" knocked out to transparency | GitHub org avatar, X/Twitter avatar, anywhere one-color. Recolor by editing the single `fill` on the `<rect>` |
| `critfall-icon-512.png` | 512×512 export of `critfall-logo.svg` | Modrinth project icon, CurseForge project icon |
| `critfall-icon-256.png` | 256×256 export | General-purpose (websites, launchers) |
| `critfall-icon-64.png` | 64×64 export | Small icon grids; also the size class of the in-mod `icon.png` referenced by `neoforge.mods.toml` / `fabric.mod.json` if wired up later |

Verified: at 64×64 the die silhouette, the hot 20-face, and the "20" numerals are all
still distinguishable, spark included.

## Palette

| Role | Hex |
|------|-----|
| Accent gradient top (orange) | `#FF8A3C` |
| Accent gradient bottom (crimson) | `#C21E3C` |
| Spark gradient bottom | `#E0452F` |
| Body facet, front (lightest) | `#3A455C` |
| Body facet, left | `#333D52` |
| Body facet, lower-left | `#2A3346` |
| Body facet, lower-right | `#222939` |
| Body facet, right (darkest) | `#1C2230` |
| Edge lines / numerals | `#141A28` |
| Mono mark fill | `#1D2433` |

Two-color scheme: cool slate body + one hot crimson-to-orange accent. Don't add more
accent colors.

## Regenerating PNGs

No rasterizer ships with the repo. Any SVG renderer works; the originals were exported
with [sharp](https://sharp.pixelplumbing.com/):

```js
// npm install sharp
const sharp = require("sharp");
for (const size of [512, 256, 64]) {
  sharp("critfall-logo.svg", { density: 300 })
    .resize(size, size)
    .png()
    .toFile(`critfall-icon-${size}.png`);
}
```
