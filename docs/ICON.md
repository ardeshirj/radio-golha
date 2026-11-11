# The app icon

<img src="icon.svg" alt="The icon: a pale gold rose on cream, cut by two rings" width="144"> <img src="icon-dark.svg" alt="The lapis version: a blue rose on deep blue" width="144">

A rose seen from above (گل, the flower in گلها). The tips of the petals are cut by two rings in the background colour,
like radio waves. There's no word on it: the launcher writes the app's name, رادیو گلها, right under the icon. An
earlier version had گلها over the rose; `--word` still draws it. The left picture is the icon the app uses ("paper"). The right one ("lapis") is the same
drawing in the other palette, kept in case we switch.

## How it's made

It's all drawn by one script, so the icon is code, not artwork to edit by hand:

```
python3 -I scripts/make_icon.py
```

It needs only Python. The old icon with the word needs a font and two libraries:

```
python3 -m venv build/icon-venv && build/icon-venv/bin/pip install fonttools uharfbuzz   # once
build/icon-venv/bin/python -I scripts/make_icon.py --word build/fonts/Parastoo-Bold.ttf
```

Parastoo Bold comes from [github.com/rastikerdar/parastoo-font](https://github.com/rastikerdar/parastoo-font). Android
icons can't use a font, so the script turns the word into outlines: HarfBuzz joins the Persian letters the way they're
written, and fontTools reads their shapes.

The script writes these files. Don't edit them by hand, because the next run overwrites them:

| File | What it is |
|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | The rose and the wave cuts |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | One-colour version for themed icons: outer petals lighter, the heart solid |
| `app/src/main/res/values/ic_launcher.xml` | The four colours (background, rose, inner rose, word; the word's is used only with `--word`) |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` | Puts the layers together as an adaptive icon |
| `docs/icon.svg`, `docs/icon-dark.svg` | The two palettes as pictures, cut to a circle like a Pixel shows them |

Then build and install as usual (`make run`).

The README screenshots carry the icon at the bottom: paper on the light one, lapis on the dark one. They're drawn on
with `rsvg-convert` (`brew install librsvg`): the screenshot with rounded corners and a faint outline, and
`docs/icon.svg` or `icon-dark.svg` inlined at 176 px, centred on the bottom edge in a ring of the screen's background,
half below it on a transparent strip, so it reads as a badge rather than part of the screen. If the icon changes, redo them from screenshots without the icon (the
plain ones are in git before commit "Overlay the icon on the README screenshots").

## Changing it

All in `scripts/make_icon.py`:

- **Colours:** `PALETTES`, as `#AARRGGBB` in the order background, rose, inner rose, word. To use lapis for the app,
  swap which palette `colours("paper")` writes.
- **Size:** `--scale` (default 1.15) enlarges the rose and its rings together. At 1.15 the petal tips reach 33 from
  the centre; much more and round or squircle masks trim them.
- **Waves:** `--waves wide` (the default: two rings at radius 21 and 26, times the scale), `subtle` (thin,
  half-transparent) or `echo` (a smaller rose with arcs around it). The ring sizes are in `layers()`.
- **The rose:** `rose()` gives three rings of round petals: how many, how far from the centre, how big, and how far
  each ring is turned.
- **The word** (only with `--word`): `WORD`, and its size in `word_path()` (`size=28`, at most 54 wide). It's
  centred by its ink, not its letter boxes.

The canvas is 108 × 108. Launchers show only the middle 72, cut to their own shape, and anything important should stay
within 33 of the centre.

## Light and dark mode

- **The icon stays paper** in both modes. The Pixel launcher draws an app's icon once and keeps that picture, so a
  night version would only show if the app happened to be installed at night.
- **Themed icons** (Wallpaper & style) are Android's way to follow the mode: the launcher recolours the monochrome
  layer. On this phone that applies on the home screen, not in the app drawer.
- **The launch screen** does follow the mode: cream by day (`res/values/splash.xml`), lapis at night
  (`res/values-night/splash.xml`). Those two colours are set by hand, not by the script; keep them in step with the
  palettes if the colours change.
- **The app's own colours** are the same lapis, gold and cream, in `MainActivity.kt` (`GolhaLight`, `GolhaDark`).

## Fonts

The icon has no text, so it uses no font. With `--word` it holds Parastoo's letter shapes (by Saber Rastikerdar, SIL
Open Font License 1.1), not the font itself. The app's text is Vazirmatn; see [CREDITS.md](../CREDITS.md).
