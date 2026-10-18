"""Pixel art for the themed screens: each theme's window frame, background tile, header divider, cards, buttons and
tabs, plus the currency icons price chips use. Writes GUI sprites (with their .mcmeta scaling) under
skyblock/src/main/resources/assets/skyrpg_skyblock/textures/gui/sprites/, and a 4x preview of every theme.

    python tools/ui/themes.py            # write the sprites
    python tools/ui/themes.py --preview  # and $TEMP/theme-<name>.png previews
"""
import argparse
import json
import os
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SPRITES = ROOT / "skyblock/src/main/resources/assets/skyrpg_skyblock/textures/gui/sprites"


def rgb(hex_color, alpha=255):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def shade(color, amount):
    """Lighter (amount > 0) or darker (< 0), keeping alpha."""
    r, g, b, a = color
    if amount >= 0:
        return (int(r + (255 - r) * amount), int(g + (255 - g) * amount), int(b + (255 - b) * amount), a)
    return (int(r * (1 + amount)), int(g * (1 + amount)), int(b * (1 + amount)), a)


def save(image, path, scaling=None):
    path = SPRITES / path
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    meta = path.with_name(path.name + ".mcmeta")
    if scaling:
        meta.write_text(json.dumps({"gui": {"scaling": scaling}}, indent=4) + "\n", encoding="utf-8")
    elif meta.exists():
        meta.unlink()


def nine(w, h, border):
    return {"type": "nine_slice", "width": w, "height": h, "border": border}


def tile(w, h):
    return {"type": "tile", "width": w, "height": h}


def noise_fill(img, box, palette, weights, rng):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), rng.choices(palette, weights)[0])


# ---------------------------------------------------------------- shared shapes

def rounded_panel(w, h, fill, border, highlight=None, shadow=None):
    """A panel with a 1px border and clipped corners, an optional top highlight and bottom shadow line inside."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if corner:
                continue
            img.putpixel((x, y), border if edge else fill)
    if highlight:
        for x in range(2, w - 2):
            img.putpixel((x, 1), highlight)
    if shadow:
        for x in range(2, w - 2):
            img.putpixel((x, h - 2), shadow)
    return img


def rivet(img, x, y, light, dark):
    img.putpixel((x, y), light)
    img.putpixel((x + 1, y), shade(light, -0.25))
    img.putpixel((x, y + 1), shade(light, -0.25))
    img.putpixel((x + 1, y + 1), dark)


# ---------------------------------------------------------------- farming

def farming(out):
    rng = random.Random(7)
    outline = rgb("120c06")
    plank = rgb("8f6638")
    plank_hi = rgb("b3854b")
    plank_lo = rgb("6a4826")
    grain = rgb("74512b")
    seam = rgb("4a3119")
    bevel = rgb("2a1c0f")
    inner = rgb("3b2a17")

    # The frame: oak planks round the edge, iron plates with a rivet in the corners.
    size, border = 48, 9
    frame = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d == 0:
                frame.putpixel((x, y), outline)
            elif d <= 6:
                horizontal = min(y, size - 1 - y) <= min(x, size - 1 - x)
                along = x if horizontal else y
                across = d
                c = plank
                if across == 1:
                    c = plank_hi
                elif across == 6:
                    c = plank_lo
                elif rng.random() < 0.18:
                    c = grain
                if along % 24 == 12 and 1 < across < 6:
                    c = seam
                frame.putpixel((x, y), c)
            elif d == 7:
                frame.putpixel((x, y), bevel)
            elif d == 8:
                frame.putpixel((x, y), inner)
    # Grain streaks along each plank (seamless every 30px tile, so short streaks only).
    for _ in range(40):
        x = rng.randrange(9, 39)
        y = rng.choice([2, 3, 4, 5, 42, 43, 44, 45])
        for k in range(rng.randrange(2, 6)):
            if 9 <= x + k < 39:
                frame.putpixel((x + k, y), grain)
        y2 = rng.randrange(9, 39)
        x2 = rng.choice([2, 3, 4, 5, 42, 43, 44, 45])
        for k in range(rng.randrange(2, 6)):
            if 9 <= y2 + k < 39:
                frame.putpixel((x2, y2 + k), grain)
    iron = rgb("7b8087")
    iron_hi = rgb("b2b8bf")
    iron_lo = rgb("4a4e54")
    for cx, cy in ((0, 0), (size - 9, 0), (0, size - 9), (size - 9, size - 9)):
        for y in range(9):
            for x in range(9):
                if (x, y) in ((0, 0), (8, 0), (0, 8), (8, 8)):
                    continue
                c = iron
                if x == 0 or y == 0:
                    c = outline
                elif x == 8 or y == 8:
                    c = outline
                elif x == 1 or y == 1:
                    c = iron_hi
                elif x == 7 or y == 7:
                    c = iron_lo
                frame.putpixel((cx + x, cy + y), c)
        rivet(frame, cx + 3, cy + 3, rgb("dfe3e8"), rgb("2c2f33"))
    save(frame, f"{out}/frame.png", nine(size, size, border))

    # The background: dark soil with faint straw flecks.
    bg = Image.new("RGBA", (32, 32))
    noise_fill(bg, (0, 0, 32, 32), [rgb("1b1610"), rgb("1e1812"), rgb("18130e")], [70, 15, 15], rng)
    for _ in range(9):
        x, y = rng.randrange(32), rng.randrange(32)
        for k in range(3):
            bg.putpixel(((x + k) % 32, (y + k // 2) % 32), rgb("272016"))
    save(bg, f"{out}/background.png", tile(32, 32))

    # The header divider: a twisted straw rope (tiles), and a wheat sprig at each end.
    hi, mid, lo = rgb("ecca62"), rgb("bf9a33"), rgb("7d611d")
    rope = Image.new("RGBA", (8, 6), (0, 0, 0, 0))
    rows = [[hi, mid, lo, mid], [mid, hi, mid, lo], [lo, mid, hi, mid]]
    for y, row in enumerate(rows):
        for x in range(8):
            rope.putpixel((x, y + 1), row[(x + y) % 4] if (x // 4) % 2 == 0 else row[(x - y) % 4])
    for x in range(8):
        rope.putpixel((x, 0), shade(lo, -0.3)[:3] + (90,))
        rope.putpixel((x, 4), rgb("000000", 110))
    save(rope, f"{out}/divider.png", tile(8, 6))
    sprig = Image.new("RGBA", (11, 9), (0, 0, 0, 0))
    stem = rgb("6f8f2e")
    for x in range(4, 11):
        sprig.putpixel((x, 4), stem)
    grains = [(1, 4), (2, 3), (2, 5), (3, 2), (3, 6), (4, 3), (4, 5), (5, 2), (5, 6), (6, 3), (6, 5), (0, 4)]
    for i, (x, y) in enumerate(grains):
        sprig.putpixel((x, y), hi if i % 3 == 0 else mid)
    for x, y in ((1, 3), (1, 5), (3, 4), (5, 4)):
        sprig.putpixel((x, y), lo)
    save(sprig, f"{out}/divider_left.png")
    save(sprig.transpose(Image.FLIP_LEFT_RIGHT), f"{out}/divider_right.png")

    # Cards: a dark wooden inset; hovered with a straw-gold edge; locked darker.
    save(rounded_panel(24, 24, rgb("241d14"), rgb("4a3923"), rgb("30261a"), rgb("1c160f")), f"{out}/card.png", nine(24, 24, 4))
    save(rounded_panel(24, 24, rgb("2c2418"), rgb("d8b04a"), rgb("3a2f20"), rgb("211a12")), f"{out}/card_hover.png", nine(24, 24, 4))
    save(rounded_panel(24, 24, rgb("1a1610"), rgb("2e261b"), None, None), f"{out}/card_locked.png", nine(24, 24, 4))

    # Buttons: a grass block (green top over dirt); hovered brighter; disabled cobblestone.
    def grass_button(bright, cobble=False):
        w, h = 24, 14
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        top = [rgb("6fb24f"), rgb("5c9a40"), rgb("80c460")]
        dirt = [rgb("7a5533"), rgb("8a6440"), rgb("684628")]
        stone = [rgb("6c6c6c"), rgb("5a5a5a"), rgb("7d7d7d")]
        for y in range(h):
            for x in range(w):
                if (x in (0, w - 1)) and (y in (0, h - 1)):
                    continue
                if x in (0, w - 1) or y in (0, h - 1):
                    c = rgb("1b120a")
                elif cobble:
                    c = rng.choices(stone, [60, 25, 15])[0]
                elif y <= 3 or (y == 4 and rng.random() < 0.45):
                    c = rng.choices(top, [60, 25, 15])[0]
                else:
                    c = rng.choices(dirt, [65, 20, 15])[0]
                if not (x in (0, w - 1) or y in (0, h - 1)):
                    c = shade(c, bright)
                img.putpixel((x, y), c)
        for x in range(1, w - 1):
            img.putpixel((x, h - 2), shade(img.getpixel((x, h - 2)), -0.35))
        return img
    save(grass_button(0.0), f"{out}/button.png", nine(24, 14, 4))
    save(grass_button(0.18), f"{out}/button_hover.png", nine(24, 14, 4))
    save(grass_button(-0.1, cobble=True), f"{out}/button_disabled.png", nine(24, 14, 4))

    # Tabs: like cards, the active one wheat-gold edged with a lighter fill.
    save(rounded_panel(24, 14, rgb("1f1912"), rgb("3a2d1c"), rgb("281f15"), None), f"{out}/tab.png", nine(24, 14, 3))
    save(rounded_panel(24, 14, rgb("3a2c19"), rgb("e5c34a"), rgb("4e3b21"), None), f"{out}/tab_active.png", nine(24, 14, 3))


# ---------------------------------------------------------------- currency icons (9 x 9)

def currencies():
    def medal(name, base):
        img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
        red, blue = rgb("c8323c"), rgb("3c64c8")
        for y in range(0, 4):
            img.putpixel((2 + y // 2, y), red)
            img.putpixel((3 + y // 2, y), shade(red, -0.25))
            img.putpixel((6 - y // 2, y), blue)
            img.putpixel((5 - y // 2, y), shade(blue, -0.25))
        disc = [(x, y) for y in range(3, 9) for x in range(1, 8) if (x - 4) ** 2 + (y - 5.5) ** 2 <= 8.5]
        for x, y in disc:
            img.putpixel((x, y), base)
        for x, y in disc:
            if (x - 4) ** 2 + (y - 5.5) ** 2 > 5.0:
                img.putpixel((x, y), shade(base, -0.35) if (x + y) > 9 else shade(base, 0.35))
        img.putpixel((4, 5), shade(base, 0.6))
        img.putpixel((4, 6), shade(base, -0.2))
        save(img, f"currency/{name}.png")
    medal("medal_gold", rgb("e8b923"))
    medal("medal_silver", rgb("c3cad3"))
    medal("medal_bronze", rgb("c47a3c"))

    def coin(name, base):
        img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
        for y in range(9):
            for x in range(9):
                r = (x - 4) ** 2 + (y - 4) ** 2
                if r <= 16:
                    c = base
                    if r > 9:
                        c = shade(base, 0.3) if x + y < 8 else shade(base, -0.4)
                    img.putpixel((x, y), c)
        for y in range(2, 7):
            img.putpixel((4, y), shade(base, -0.3))
        img.putpixel((3, 2), shade(base, 0.55))
        save(img, f"currency/{name}.png")
    coin("coins", rgb("f2c230"))
    coin("copper", rgb("d0703c"))

    pest = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    wing, body, dark, eye = rgb("d6e4ea", 220), rgb("4e8a2a"), rgb("2c5418"), rgb("e03c3c")
    for x, y in ((1, 2), (2, 1), (2, 2), (3, 2), (6, 1), (6, 2), (7, 2), (5, 2)):
        pest.putpixel((x, y), wing)
    for x, y in ((3, 3), (4, 3), (5, 3), (3, 4), (4, 4), (5, 4), (3, 5), (4, 5), (5, 5), (4, 6), (4, 7)):
        pest.putpixel((x, y), body)
    for x, y in ((3, 5), (5, 5), (4, 7)):
        pest.putpixel((x, y), dark)
    pest.putpixel((3, 3), eye)
    pest.putpixel((5, 3), eye)
    for x, y in ((2, 4), (6, 4), (2, 6), (6, 6)):
        pest.putpixel((x, y), dark)
    save(pest, "currency/pests.png")

    ticket = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    paper, edge, stripe = rgb("f3dc7a"), rgb("9b7d24"), rgb("c8323c")
    for y in range(2, 8):
        for x in range(0, 9):
            if (x in (0, 8)) and y == 4:
                continue
            c = paper
            if y in (2, 7) or x in (0, 8):
                c = edge
            elif x == 3:
                c = shade(paper, -0.2) if y % 2 else paper
            elif x in (5, 6) and y in (4, 5):
                c = stripe
            ticket.putpixel((x, y), c)
    save(ticket, "currency/tickets.png")

    mote = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y in range(9):
        for x in range(9):
            r = (x - 4) ** 2 + (y - 4) ** 2
            if r <= 12:
                mote.putpixel((x, y), rgb("ff5cf0") if r > 6 else rgb("ff8cf4") if r > 2 else rgb("ffe0fc"))
    mote.putpixel((3, 3), rgb("ffffff"))
    for x, y in ((0, 4), (8, 4), (4, 0), (4, 8)):
        mote.putpixel((x, y), rgb("b04cd8", 200))
    save(mote, "currency/motes.png")


THEMES = {"farming": farming}


def preview(name):
    """The theme's pieces laid out like a small window, at 4x."""
    base = SPRITES / f"theme/{name}"
    w, h = 160, 100
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 255))

    def nine_draw(img_path, x, y, ww, hh):
        meta = json.loads(Path(str(img_path) + ".mcmeta").read_text())["gui"]["scaling"]
        b = meta["border"]
        src = Image.open(img_path)
        sw, sh = src.size
        for py in range(hh):
            for px in range(ww):
                sx = px if px < b else sw - (ww - px) if px >= ww - b else b + (px - b) % (sw - 2 * b)
                sy = py if py < b else sh - (hh - py) if py >= hh - b else b + (py - b) % (sh - 2 * b)
                c = src.getpixel((sx, sy))
                if c[3]:
                    canvas.alpha_composite(Image.new("RGBA", (1, 1), c), (x + px, y + py))

    bg = Image.open(base / "background.png")
    for y in range(0, h, 32):
        for x in range(0, w, 32):
            canvas.alpha_composite(bg, (x, y))
    nine_draw(base / "frame.png", 0, 0, w, h)
    divider = Image.open(base / "divider.png")
    for x in range(20, w - 20, divider.size[0]):
        canvas.alpha_composite(divider, (x, 20))
    canvas.alpha_composite(Image.open(base / "divider_left.png"), (9, 18))
    canvas.alpha_composite(Image.open(base / "divider_right.png"), (w - 20, 18))
    nine_draw(base / "tab_active.png", 12, 30, 40, 14)
    nine_draw(base / "tab.png", 56, 30, 40, 14)
    nine_draw(base / "card.png", 12, 50, 64, 34)
    nine_draw(base / "card_hover.png", 82, 50, 64, 34)
    nine_draw(base / "button.png", 18, 66, 40, 14)
    nine_draw(base / "button_hover.png", 88, 66, 40, 14)
    nine_draw(base / "button_disabled.png", 100, 30, 40, 14)
    x = 20
    for c in ("medal_gold", "medal_silver", "medal_bronze", "coins", "copper", "pests", "tickets"):
        canvas.alpha_composite(Image.open(SPRITES / f"currency/{c}.png"), (x, 54))
        x += 11
    out = Path(os.environ.get("TEMP", ".")) / f"theme-{name}.png"
    canvas.resize((w * 4, h * 4), Image.NEAREST).save(out)
    print("preview", out)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--preview", action="store_true")
    args = parser.parse_args()
    import more_themes
    themes = dict(THEMES)
    themes.update(more_themes.THEMES)
    for name, build in themes.items():
        build(f"theme/{name}")
    currencies()
    print("wrote", ", ".join(themes), "and currencies")
    if args.preview:
        for name in themes:
            preview(name)


if __name__ == "__main__":
    main()
