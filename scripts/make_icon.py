"""Draw the Radio Golha app icon as an Android adaptive icon (vector drawables).

The icon: the word گلها in gold, set in Parastoo Bold, over a lapis rose seen from above, with the rose's petal tips
softly cut by rings in the background colour, like radio waves. Android icons can't use a font, so the word is
turned into outlines here: HarfBuzz joins the Persian letters, fontTools reads their shapes.

    build/icon-venv/bin/python scripts/make_icon.py <Parastoo-Bold.ttf> [--waves wide|subtle|echo]

The icon is paper: cream, a pale gold rose and the word in lapis. It doesn't change with dark mode on purpose: the
Pixel launcher draws an icon once and keeps that picture, so a night version would only show if the app happened to be
installed at night. Themed icons (the monochrome layer) are Android's way to follow the mode. The lapis palette, with a
gold word, is kept for the README's dark picture and in case we switch.

Needs fontTools and uharfbuzz, in a venv of their own: `python3 -m venv build/icon-venv &&
build/icon-venv/bin/pip install fonttools uharfbuzz`. Parastoo Bold comes from github.com/rastikerdar/parastoo-font. Writes, under app/src/main/res/:
drawable/ic_launcher_foreground.xml, drawable/ic_launcher_monochrome.xml, values/ic_launcher.xml and
mipmap-anydpi-v26/ic_launcher.xml + ic_launcher_round.xml; and docs/icon.svg + docs/icon-dark.svg (lapis) to look at.

Parastoo is (c) Saber Rastikerdar, SIL Open Font License 1.1; the icon contains the letter shapes, not the font.
"""
import math
import os
import sys

import uharfbuzz as hb
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

RES = "app/src/main/res"
WORD = "گلها"
CX = CY = 54.0  # the adaptive-icon canvas is 108 x 108; launchers show the middle 72, cut to their shape

ROLES = ("background", "rose", "rose_inner", "word")  # each becomes @color/ic_launcher_<role>
PALETTES = {  # in ROLES order
    "paper": ("#FFF4ECE0", "#FFEBCB86", "#FFE0B864", "#FF1E2B5E"),  # the icon: lapis word on cream, pale gold rose
    "lapis": ("#FF1E2B5E", "#FF2F4183", "#FF3B5099", "#FFE9B949"),  # gold word on deep blue
}
REFS = tuple(f"@color/ic_launcher_{role}" for role in ROLES)
BG, ROSE, ROSE2, GOLD = REFS


def num(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def circle(x, y, r):
    return f"M{num(x - r)},{num(y)} a{num(r)},{num(r)} 0 1,0 {num(2 * r)},0 a{num(r)},{num(r)} 0 1,0 {num(-2 * r)},0 Z"


def ring(n, dist, r, offset, k=1.0):
    """n round petals of radius r, dist from the centre, the first at `offset` degrees from straight up."""
    out = []
    for i in range(n):
        t = math.radians(i * 360 / n + offset - 90)
        out.append((CX + k * dist * math.cos(t), CY + k * dist * math.sin(t), k * r))
    return out


def arc(r, deg, half):
    t1, t2 = math.radians(deg - half), math.radians(deg + half)
    x1, y1 = CX + r * math.cos(t1), CY + r * math.sin(t1)
    x2, y2 = CX + r * math.cos(t2), CY + r * math.sin(t2)
    return f"M{num(x1)},{num(y1)} A{num(r)},{num(r)} 0 0,1 {num(x2)},{num(y2)}"


def word_path(font_path, size=28.0, max_width=54.0):
    """The word's outline as path data, its ink centred on the canvas, at `size` units (narrowed to max_width)."""
    blob = hb.Blob.from_file_path(font_path)
    face = hb.Face(blob)
    font = hb.Font(face)
    buf = hb.Buffer()
    buf.add_str(WORD)
    buf.guess_segment_properties()
    hb.shape(font, buf, {})
    tt = TTFont(font_path)
    glyphs = tt.getGlyphSet()
    order = tt.getGlyphOrder()
    upem = tt["head"].unitsPerEm

    placed, pen_x = [], 0  # HarfBuzz gives right-to-left text in visual order, left to right
    for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
        placed.append((order[info.codepoint], pen_x + pos.x_offset, pos.y_offset))
        pen_x += pos.x_advance

    bounds = BoundsPen(glyphs)
    for name, gx, gy in placed:
        glyphs[name].draw(TransformPen(bounds, (1, 0, 0, 1, gx, gy)))
    x0, y0, x1, y1 = bounds.bounds
    s = size / upem
    if (x1 - x0) * s > max_width:
        s = max_width / (x1 - x0)
    tx = CX - s * (x0 + x1) / 2
    ty = CY + s * (y0 + y1) / 2  # font y points up, the canvas's y points down

    svg = SVGPathPen(glyphs, ntos=num)
    for name, gx, gy in placed:
        glyphs[name].draw(TransformPen(svg, (s, 0, 0, -s, s * gx + tx, -s * gy + ty)))
    return svg.getCommands()


def rose(k=1.0):
    """The rose: three rings of overlapping round petals, outer to inner, as (circles, fill, outline width)."""
    return [
        (ring(6, 17, 12, 0, k), ROSE, 1.4),
        (ring(6, 10.5, 9, 30, k), ROSE2, 1.3),
        (ring(4, 5, 6, 15, k), ROSE, 1.2),
    ]


def path(d, fill=None, stroke=None, width=None, alpha=None, cap=None):
    attrs = [f'android:pathData="{d}"']
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if stroke:
        attrs.append(f'android:strokeColor="{stroke}"')
        attrs.append(f'android:strokeWidth="{num(width)}"')
    if alpha is not None:
        attrs.append(f'android:{"strokeAlpha" if stroke and not fill else "fillAlpha"}="{num(alpha)}"')
    if cap:
        attrs.append(f'android:strokeLineCap="{cap}"')
    return "    <path " + "\n          ".join(attrs) + " />"


def vector(paths, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- {comment} Made by scripts/make_icon.py; edit that, not this file. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            + "\n".join(paths) + "\n</vector>\n")


def main():
    args = sys.argv[1:]

    def option(name, default):
        if name not in args:
            return default
        i = args.index(name)
        value = args[i + 1]
        del args[i:i + 2]
        return value

    waves = option("--waves", "wide")
    font_path = args[0]

    k = 0.8 if waves == "echo" else 1.0
    word = word_path(font_path, max_width=50 if waves == "echo" else 54)

    fg = []
    for circles, fill, width in rose(k):
        for x, y, r in circles:
            fg.append(path(circle(x, y, r), fill=fill, stroke=BG, width=width * k))
    spiral = f"M{num(CX - 1.5 * k)},{num(CY)} a{num(1.5 * k)},{num(1.5 * k)} 0 1,1 {num(1.5 * k)},{num(1.5 * k)} " \
             f"a{num(3.2 * k)},{num(3.2 * k)} 0 1,1 {num(-3.2 * k)},{num(-3.2 * k)} a{num(5 * k)},{num(5 * k)} 0 0,1 {num(5 * k)},{num(5 * k)}"
    fg.append(path(spiral, stroke=BG, width=1.2 * k, cap="round"))
    if waves == "subtle":  # thin, half-opaque rings near the outer petal tips (the rose reaches 29 from the centre)
        fg += [path(circle(CX, CY, r), stroke=BG, width=1.2, alpha=0.6) for r in (23.5, 27)]
    elif waves == "wide":
        fg += [path(circle(CX, CY, r), stroke=BG, width=2.2) for r in (21, 26)]
    elif waves == "echo":
        fg += [path(arc(28, d - 90, 11), stroke=ROSE2, width=2.2, cap="round") for d in range(0, 360, 45)]
        fg += [path(arc(32.5, d - 90, 8), stroke=ROSE2, width=1.8, alpha=0.55, cap="round") for d in range(0, 360, 45)]
    else:
        sys.exit(f"unknown --waves {waves}: use subtle, wide or echo")
    fg.append(path(word, fill=GOLD))

    # Themed icon (Android 13+): one colour, tinted by the system. The rose's silhouette, faint, and the word, solid.
    silhouette = " ".join(circle(x, y, r) for circles, _, _ in rose(k) for x, y, r in circles)
    mono = [path(silhouette, fill="#FF000000", alpha=0.3), path(word, fill="#FF000000")]

    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                "<!-- Made by scripts/make_icon.py. -->\n"
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
                "</adaptive-icon>\n")
    def colours(palette):
        lines = "".join(f'    <color name="ic_launcher_{role}">{value}</color>\n' for role, value in zip(ROLES, PALETTES[palette]))
        return ('<?xml version="1.0" encoding="utf-8"?>\n'
                f"<!-- The app icon's colours ({palette}). Made by scripts/make_icon.py. -->\n"
                f"<resources>\n{lines}</resources>\n")

    files = {
        "drawable/ic_launcher_foreground.xml": vector(fg, f"App icon, foreground: the rose ({waves} waves) and the word."),
        "drawable/ic_launcher_monochrome.xml": vector(mono, "App icon, themed (one colour)."),
        "values/ic_launcher.xml": colours("paper"),
        "mipmap-anydpi-v26/ic_launcher.xml": adaptive,
        "mipmap-anydpi-v26/ic_launcher_round.xml": adaptive,
    }
    for rel, text in files.items():
        out = os.path.join(RES, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            f.write(text)
        print(out)

    # The same icon as SVGs, cut to a circle the way a Pixel shows it: for the README and for looking at it on a Mac.
    for out, palette in (("docs/icon.svg", "paper"), ("docs/icon-dark.svg", "lapis")):
        with open(out, "w", encoding="utf-8") as f:
            f.write(svg(fg, dict(zip(REFS, PALETTES[palette]))))
        print(out)


def svg(paths, palette):
    """Turn the vector-drawable paths into SVG, showing the middle 72 units in a circle, as a launcher would."""
    import re

    def colour(argb):
        argb = palette.get(argb, argb)
        return "#" + argb[3:], int(argb[1:3], 16) / 255

    out = []
    for p in paths:
        a = dict(re.findall(r'android:(\w+)="([^"]*)"', p))
        attrs = [f'd="{a["pathData"]}"']
        if "fillColor" in a:
            c, o = colour(a["fillColor"])
            attrs.append(f'fill="{c}"')
            alpha = float(a.get("fillAlpha", 1)) * o
            if alpha < 1:
                attrs.append(f'fill-opacity="{num(alpha)}"')
        else:
            attrs.append('fill="none"')
        if "strokeColor" in a:
            c, o = colour(a["strokeColor"])
            attrs.append(f'stroke="{c}" stroke-width="{a["strokeWidth"]}"')
            alpha = float(a.get("strokeAlpha", 1)) * o
            if alpha < 1:
                attrs.append(f'stroke-opacity="{num(alpha)}"')
        if "strokeLineCap" in a:
            attrs.append(f'stroke-linecap="{a["strokeLineCap"]}"')
        out.append("<path " + " ".join(attrs) + "/>")
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="288" height="288">'
            '<clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath><g clip-path="url(#c)">'
            f'<rect width="108" height="108" fill="#{palette[BG][3:]}"/>' + "".join(out) + "</g></svg>\n")


if __name__ == "__main__":
    main()
