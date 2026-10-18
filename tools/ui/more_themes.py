"""The other themes' pixel art (mining, crimson, dungeon, magic, fishing, galatea), on themes.py's helpers."""
import math
import random

from PIL import Image

from themes import nine, noise_fill, rgb, rounded_panel, save, shade, tile


# ---------------------------------------------------------------- shared painters

def framed(size, band, corner, outline, bevel, inner):
    """A frame: an outline, a 6px band painted by band(along, across, horizontal), a bevel, an inner line, and corners."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d == 0:
                img.putpixel((x, y), outline)
            elif d <= 6:
                horizontal = min(y, size - 1 - y) <= min(x, size - 1 - x)
                img.putpixel((x, y), band(x if horizontal else y, d, horizontal))
            elif d == 7:
                img.putpixel((x, y), bevel)
            elif d == 8:
                img.putpixel((x, y), inner)
    for cx, cy in ((0, 0), (size - 9, 0), (0, size - 9), (size - 9, size - 9)):
        corner(img, cx, cy)
    return img


def bricks(rng, light, base, dark, mortar, width=8, extra=None):
    """A band of two brick courses with mortar; extra(along, across, horizontal) may return a colour (ore, moss...)."""
    tint = {}

    def band(along, across, horizontal):
        if across == 4:
            return mortar
        course = 0 if across < 4 else 1
        offset = 0 if course == 0 else width // 2
        if (along + offset) % width == 0:
            return mortar
        key = (course, (along + offset) // width, horizontal)
        if key not in tint:
            tint[key] = rng.choice([-0.08, 0.0, 0.05, 0.1])
        if extra:
            c = extra(along, across, horizontal)
            if c:
                return c
        c = light if across in (1, 5) else dark if across in (3, 6) else base
        return shade(c, tint[key])
    return band


def plate_corner(fill, light, dark, outline, centre=None):
    def corner(img, cx, cy):
        for y in range(9):
            for x in range(9):
                if (x, y) in ((0, 0), (8, 0), (0, 8), (8, 8)):
                    continue
                c = fill
                if x in (0, 8) or y in (0, 8):
                    c = outline
                elif x == 1 or y == 1:
                    c = light
                elif x == 7 or y == 7:
                    c = dark
                img.putpixel((cx + x, cy + y), c)
        if centre:
            centre(img, cx, cy)
    return corner


def gem(colour):
    def paint(img, cx, cy):
        for x, y in ((4, 2), (3, 3), (4, 3), (5, 3), (2, 4), (3, 4), (4, 4), (5, 4), (6, 4), (3, 5), (4, 5), (5, 5), (4, 6)):
            img.putpixel((cx + x, cy + y), colour)
        img.putpixel((cx + 3, cy + 3), shade(colour, 0.55))
        img.putpixel((cx + 4, cy + 2), shade(colour, 0.35))
        for x, y in ((5, 5), (4, 6), (6, 4)):
            img.putpixel((cx + x, cy + y), shade(colour, -0.35))
    return paint


def skull(img, cx, cy):
    bone, shadow, hole = rgb("e8e2cf"), rgb("b3ab92"), rgb("1a1414")
    for y in range(2, 7):
        for x in range(2, 7):
            img.putpixel((cx + x, cy + y), bone)
    for x in (2, 6):
        img.putpixel((cx + x, cy + 6), shadow)
    img.putpixel((cx + 3, cy + 4), hole)
    img.putpixel((cx + 5, cy + 4), hole)
    img.putpixel((cx + 4, cy + 5), shadow)
    for x in (3, 5):
        img.putpixel((cx + x, cy + 6), hole)


def noise_tile(rng, palette, weights, specks=None, size=32):
    img = Image.new("RGBA", (size, size))
    noise_fill(img, (0, 0, size, size), palette, weights, rng)
    for colour, count in (specks or []):
        for _ in range(count):
            img.putpixel((rng.randrange(size), rng.randrange(size)), colour)
    return img


def block_button(rng, palette, weights, outline, bright, lines=None):
    """A 24 x 14 button with a block's texture: noise from the palette and an optional line pattern."""
    w, h = 24, 14
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            if (x in (0, w - 1)) and (y in (0, h - 1)):
                continue
            if x in (0, w - 1) or y in (0, h - 1):
                img.putpixel((x, y), outline)
                continue
            c = rng.choices(palette, weights)[0]
            if lines:
                lc = lines(x, y)
                if lc:
                    c = lc
            if y == 1:
                c = shade(c, 0.18)
            if y == h - 2:
                c = shade(c, -0.3)
            img.putpixel((x, y), shade(c, bright))
    return img


def buttons(out, rng, palette, outline, hover_outline, disabled, lines=None):
    save(block_button(rng, palette, [55, 30, 15], outline, 0.0, lines), f"{out}/button.png", nine(24, 14, 4))
    save(block_button(rng, palette, [55, 30, 15], hover_outline, 0.15, lines), f"{out}/button_hover.png", nine(24, 14, 4))
    save(block_button(rng, disabled, [60, 25, 15], outline, 0.0), f"{out}/button_disabled.png", nine(24, 14, 4))


def cards_and_tabs(out, fill, border, hover_border, hi, lo, tab_fill, tab_border, tab_active_fill, accent):
    save(rounded_panel(24, 24, fill, border, hi, lo), f"{out}/card.png", nine(24, 24, 4))
    save(rounded_panel(24, 24, shade(fill, 0.08), hover_border, shade(hi, 0.1), lo), f"{out}/card_hover.png", nine(24, 24, 4))
    save(rounded_panel(24, 24, shade(fill, -0.25), shade(border, -0.3), None, None), f"{out}/card_locked.png", nine(24, 24, 4))
    save(rounded_panel(24, 14, tab_fill, tab_border, shade(tab_fill, 0.12), None), f"{out}/tab.png", nine(24, 14, 3))
    save(rounded_panel(24, 14, tab_active_fill, accent, shade(tab_active_fill, 0.15), None), f"{out}/tab_active.png", nine(24, 14, 3))


def divider(out, w, h, paint, cap):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    paint(img)
    save(img, f"{out}/divider.png", tile(w, h))
    left = Image.new("RGBA", (11, 9), (0, 0, 0, 0))
    cap(left)
    save(left, f"{out}/divider_left.png")
    save(left.transpose(Image.FLIP_LEFT_RIGHT), f"{out}/divider_right.png")


def pixels(img, points, dx=0, dy=0):
    for x, y, c in points:
        img.putpixel((x + dx, y + dy), rgb(c))


# ---------------------------------------------------------------- mining: stone bricks with ore, a minecart rail

def mining(out):
    rng = random.Random(11)
    ores = [rgb("5de0e6"), rgb("f2c94c"), rgb("d8443a"), rgb("4ed37a")]

    def ore(along, across, horizontal):
        if across in (2, 5) and rng.random() < 0.035:
            return rng.choice(ores)
        return None
    band = bricks(rng, rgb("7c7f87"), rgb("63666d"), rgb("4b4d53"), rgb("2a2b2f"), 8, ore)
    corner = plate_corner(rgb("3b3e45"), rgb("5b5f68"), rgb("26282d"), rgb("0e0f12"), gem(rgb("5ce1e6")))
    save(framed(48, band, corner, rgb("0e0f12"), rgb("1d1f24"), rgb("2c2f35")), f"{out}/frame.png", nine(48, 48, 9))
    save(noise_tile(rng, [rgb("15171b"), rgb("181a1f"), rgb("121418")], [70, 15, 15], [(rgb("1f2229"), 14), (rgb("23333a"), 2)]),
         f"{out}/background.png", tile(32, 32))

    def rail(img):
        tie, tie_dark, iron, iron_dark = rgb("6b4a2a"), rgb("4e341c"), rgb("b4bac2"), rgb("6c7178")
        for x in range(8):
            if x % 4 in (1, 2):
                for y in range(0, 6):
                    img.putpixel((x, y), tie if x % 4 == 1 else tie_dark)
            img.putpixel((x, 1), iron)
            img.putpixel((x, 4), iron)
            img.putpixel((x, 2), iron_dark)
            img.putpixel((x, 5), rgb("000000", 120))

    def pickaxe(img):
        pixels(img, [(1, 3, "8fd6db"), (2, 2, "8fd6db"), (3, 1, "bff0f2"), (4, 1, "8fd6db"), (5, 1, "8fd6db"), (6, 1, "5fb2b8"),
                     (7, 2, "5fb2b8"), (8, 3, "4a9aa0"), (5, 2, "8a6236"), (4, 3, "8a6236"), (3, 4, "6b4a2a"), (2, 5, "8a6236"),
                     (1, 6, "6b4a2a")])
    divider(out, 8, 6, rail, pickaxe)
    cards_and_tabs(out, rgb("1c1e23"), rgb("3a3d45"), rgb("5ce1e6"), rgb("25282e"), rgb("15171a"),
                   rgb("191b20"), rgb("33363d"), rgb("252a31"), rgb("5ce1e6"))
    buttons(out, rng, [rgb("7a7c82"), rgb("6c6e74"), rgb("85878d")], rgb("101114"), rgb("2f8f94"),
            [rgb("3a3b40"), rgb("333438"), rgb("2c2d31")])


# ---------------------------------------------------------------- crimson: nether bricks and lava, a blaze rod

def crimson(out):
    rng = random.Random(13)

    def lava(along, across, horizontal):
        if across == 4 and rng.random() < 0.12:
            return rng.choice([rgb("ff8a2a"), rgb("ffb43c"), rgb("e0561e")])
        return None
    band = bricks(rng, rgb("5a1e22"), rgb("47161a"), rgb("341014"), rgb("1a0709"), 6, lava)

    def gilded(img, cx, cy):
        for _ in range(7):
            img.putpixel((cx + rng.randrange(2, 7), cy + rng.randrange(2, 7)), rng.choice([rgb("f2c94c"), rgb("c99a2e")]))
    corner = plate_corner(rgb("2a2326"), rgb("3e3438"), rgb("191416"), rgb("0b0607"), gilded)
    save(framed(48, band, corner, rgb("0b0607"), rgb("1c0b0d"), rgb("2c1215")), f"{out}/frame.png", nine(48, 48, 9))
    save(noise_tile(rng, [rgb("1a0c0d"), rgb("1d0e0f"), rgb("170a0b")], [70, 15, 15], [(rgb("3a1512"), 10), (rgb("8a3a1a"), 3)]),
         f"{out}/background.png", tile(32, 32))

    def rod(img):
        cols = [rgb("ffe08a"), rgb("f7b733"), rgb("e07b20"), rgb("a94c12")]
        for x in range(8):
            for y, c in zip(range(1, 5), cols):
                img.putpixel((x, y), shade(c, 0.12) if x % 4 == 0 else c)
            img.putpixel((x, 5), rgb("000000", 110))
        img.putpixel((2, 0), rgb("ff8a2a", 160))
        img.putpixel((6, 0), rgb("ffd27a", 140))

    def flame(img):
        pixels(img, [(3, 1, "ffe08a"), (2, 2, "ffb43c"), (3, 2, "ffe08a"), (4, 2, "ffb43c"), (1, 3, "e0561e"), (2, 3, "ffb43c"),
                     (3, 3, "ffe08a"), (4, 3, "ffb43c"), (5, 3, "e0561e"), (1, 4, "e0561e"), (2, 4, "ff8a2a"), (3, 4, "ffb43c"),
                     (4, 4, "ff8a2a"), (5, 4, "e0561e"), (2, 5, "e0561e"), (3, 5, "ff8a2a"), (4, 5, "e0561e"), (3, 6, "a94c12")], 2, 0)
    divider(out, 8, 6, rod, flame)
    cards_and_tabs(out, rgb("1f1416"), rgb("44262a"), rgb("ff8c3a"), rgb("2a1b1e"), rgb("150d0e"),
                   rgb("1c1213"), rgb("3a2225"), rgb("2e1a1c"), rgb("ff8c3a"))

    crack = set()
    for _ in range(4):
        x, y = rng.randrange(2, 22), rng.randrange(2, 12)
        for _ in range(5):
            crack.add((x, y))
            x += rng.choice([-1, 1])
            y += rng.choice([-1, 0, 1])

    def cracks(x, y):
        return rng.choice([rgb("ff8a2a"), rgb("ffb43c")]) if (x, y) in crack else None
    buttons(out, rng, [rgb("5a1a10"), rgb("6e2412"), rgb("4a140c")], rgb("120505"), rgb("ff8c3a"),
            [rgb("2c2629"), rgb("252023"), rgb("332c30")], cracks)


# ---------------------------------------------------------------- dungeon: mossy stone bricks, skulls, a chain

def dungeon(out):
    rng = random.Random(17)

    def moss(along, across, horizontal):
        if across in (1, 2, 5) and rng.random() < 0.08:
            return rng.choice([rgb("4b6b2f"), rgb("5c7f37"), rgb("3c5626")])
        return None
    band = bricks(rng, rgb("8a8b8e"), rgb("737477"), rgb("5c5d60"), rgb("2c2d2f"), 8, moss)
    corner = plate_corner(rgb("2a2a2c"), rgb("3f3f42"), rgb("1a1a1c"), rgb("0c0c0d"), skull)
    save(framed(48, band, corner, rgb("0c0c0d"), rgb("1b1b1d"), rgb("2b2c2e")), f"{out}/frame.png", nine(48, 48, 9))
    save(noise_tile(rng, [rgb("151616"), rgb("181919"), rgb("121313")], [70, 15, 15], [(rgb("1f2420"), 12), (rgb("2a2222"), 3)]),
         f"{out}/background.png", tile(32, 32))

    def chain(img):
        hi, mid, lo = rgb("b8bcc2"), rgb("8a8f96"), rgb("565a60")
        for x in (0, 1, 2, 3):
            img.putpixel((x, 1), hi if x in (1, 2) else mid)
            img.putpixel((x, 4), lo if x in (1, 2) else mid)
        for x, c in ((0, mid), (3, mid)):
            img.putpixel((x, 2), c)
            img.putpixel((x, 3), lo)
        for x in (4, 5, 6, 7):
            img.putpixel((x, 2), hi if x in (4, 5) else mid)
            img.putpixel((x, 3), lo if x in (6, 7) else mid)
        for x in range(8):
            img.putpixel((x, 5), rgb("000000", 100))

    def torch(img):
        pixels(img, [(5, 1, "ffe08a"), (4, 2, "ffb43c"), (5, 2, "ffe08a"), (6, 2, "ffb43c"), (5, 3, "ff8a2a"), (5, 4, "7a5530"),
                     (5, 5, "7a5530"), (5, 6, "6a4626"), (5, 7, "6a4626"), (5, 8, "5a3a1e")])
    divider(out, 8, 6, chain, torch)
    cards_and_tabs(out, rgb("1b1c1e"), rgb("3b3d40"), rgb("d94a4a"), rgb("242528"), rgb("131415"),
                   rgb("19191b"), rgb("343538"), rgb("2a2526"), rgb("d94a4a"))

    def brick_lines(x, y):
        if y in (4, 9):
            return rgb("3b3c3e")
        if (y < 4 and x % 8 == 0) or (4 < y < 9 and x % 8 == 4) or (y > 9 and x % 8 == 0):
            return rgb("3b3c3e")
        return None
    buttons(out, rng, [rgb("7e7f82"), rgb("6f7073"), rgb("8b8c8f")], rgb("0c0c0d"), rgb("d94a4a"),
            [rgb("3c3d3f"), rgb("343537"), rgb("444547")], brick_lines)


# ---------------------------------------------------------------- magic: indigo stone, gold filigree, runes

def magic(out):
    rng = random.Random(19)
    gold, gold_dark = rgb("e8c35a"), rgb("9c7a2a")

    def band(along, across, horizontal):
        if across == 1:
            return gold
        if across == 6:
            return gold_dark
        if across in (3, 4) and along % 6 == 3:
            return rgb("b98cff")
        base = rgb("2f2350") if across in (2, 5) else rgb("3a2b63")
        return shade(base, rng.choice([-0.05, 0.0, 0.05]))

    def filigree(img, cx, cy):
        for x, y in ((2, 2), (3, 2), (2, 3), (4, 3), (3, 4), (5, 4), (4, 5), (6, 5), (5, 6), (6, 6)):
            img.putpixel((cx + x, cy + y), gold)
        img.putpixel((cx + 4, cy + 4), rgb("b98cff"))
    corner = plate_corner(rgb("221a3a"), rgb("3a2d5e"), rgb("150f26"), rgb("08060f"), filigree)
    save(framed(48, band, corner, rgb("08060f"), rgb("120d22"), rgb("241b40")), f"{out}/frame.png", nine(48, 48, 9))
    save(noise_tile(rng, [rgb("130f1f"), rgb("161122"), rgb("110d1b")], [70, 15, 15], [(rgb("2a2244"), 10), (rgb("6b5bb5"), 3)]),
         f"{out}/background.png", tile(32, 32))

    def runes(img):
        for x in range(12):
            img.putpixel((x, 3), gold_dark)
            img.putpixel((x, 6), rgb("000000", 90))
        for x, y in ((2, 1), (2, 2), (1, 2), (3, 2), (8, 4), (8, 5), (7, 4), (9, 4)):
            img.putpixel((x, y), rgb("b98cff"))

    def crystal(img):
        pixels(img, [(5, 1, "e2d1ff"), (4, 2, "b98cff"), (5, 2, "e2d1ff"), (6, 2, "8a63d2"), (4, 3, "b98cff"), (5, 3, "b98cff"),
                     (6, 3, "8a63d2"), (4, 4, "8a63d2"), (5, 4, "b98cff"), (6, 4, "6a46b0"), (5, 5, "6a46b0"), (5, 6, "4a2f80")], 1, 0)
    divider(out, 12, 7, runes, crystal)
    cards_and_tabs(out, rgb("1c1730"), rgb("3b2f5e"), rgb("b98cff"), rgb("251e3e"), rgb("141024"),
                   rgb("19142b"), rgb("332854"), rgb("2a2148"), rgb("e8c35a"))

    def flecks(x, y):
        return rgb("e8c35a") if (x * 5 + y * 7) % 23 == 0 else None
    buttons(out, rng, [rgb("2d4fa8"), rgb("26449a"), rgb("3a5fc0")], rgb("0a0f24"), rgb("e8c35a"),
            [rgb("2a2738"), rgb("242131"), rgb("302c40")], flecks)


# ---------------------------------------------------------------- fishing: driftwood and rope, waves

def fishing(out):
    rng = random.Random(23)
    rope = [rgb("d9b77a"), rgb("b38f52"), rgb("8a6a35")]

    def band(along, across, horizontal):
        if across in (1, 6):
            return rope[(along + (0 if across == 1 else 1)) % 3]
        if rng.random() < 0.15:
            return rgb("4a5452")
        return rgb("6b7774") if across in (2, 3) else rgb("57625f")

    def knot(img, cx, cy):
        for y in range(1, 8):
            for x in range(1, 8):
                if (x - 4) ** 2 + (y - 4) ** 2 <= 10:
                    img.putpixel((cx + x, cy + y), [rgb("e3c389"), rgb("b38f52"), rgb("7a5c2c")][(x + y) % 3])
        img.putpixel((cx + 4, cy + 4), rgb("5a4220"))
    corner = plate_corner(rgb("2c3735"), rgb("435250"), rgb("1b2322"), rgb("081012"), knot)
    save(framed(48, band, corner, rgb("081012"), rgb("11202a"), rgb("1d3240")), f"{out}/frame.png", nine(48, 48, 9))
    bg = noise_tile(rng, [rgb("0e1620"), rgb("101924"), rgb("0c141c")], [70, 15, 15], [(rgb("17283a"), 10)])
    for x in range(32):
        bg.putpixel((x, int(16 + 2 * math.sin(x / 32 * 2 * math.pi))), rgb("15263a"))
    save(bg, f"{out}/background.png", tile(32, 32))

    def waves(img):
        crest, body, deep = rgb("bfe6ff"), rgb("4fa3d9"), rgb("2f6f9c")
        for x, top in enumerate([3, 2, 1, 1, 2, 3, 4, 4]):
            img.putpixel((x, top), crest)
            for y in range(top + 1, 6):
                img.putpixel((x, y), body if y < 5 else deep)

    def fish(img):
        pixels(img, [(2, 4, "f2994a"), (3, 3, "f2994a"), (3, 5, "f2994a"), (4, 3, "f7b267"), (4, 4, "f7b267"), (4, 5, "f2994a"),
                     (5, 3, "f7b267"), (5, 4, "f7b267"), (5, 5, "f2994a"), (6, 4, "f2994a"), (7, 3, "d9772e"), (7, 5, "d9772e"),
                     (8, 4, "d9772e"), (3, 4, "1a1a1a")])
    divider(out, 8, 6, waves, fish)
    cards_and_tabs(out, rgb("13202c"), rgb("2b4256"), rgb("5ca8ff"), rgb("1a2b3a"), rgb("0d1620"),
                   rgb("111d28"), rgb("26394b"), rgb("1b3042"), rgb("5ca8ff"))

    def tiles(x, y):
        return rgb("3c6e64") if x % 6 == 0 or y == 7 else None
    buttons(out, rng, [rgb("5b9c8f"), rgb("4e8b7f"), rgb("68ad9f")], rgb("071214"), rgb("bfe6ff"),
            [rgb("2b3436"), rgb("252d2f"), rgb("30393b")], tiles)


# ---------------------------------------------------------------- skyblock: slate with gold trim (the default look)

def skyblock(out):
    rng = random.Random(29)
    gold, gold_dark = rgb("f2b33d"), rgb("8a6a22")

    def band(along, across, horizontal):
        if across == 1:
            return gold
        if across == 2:
            return gold_dark
        if across == 6:
            return rgb("12151c")
        return shade(rgb("272c38") if across in (3, 4) else rgb("20242e"), rng.choice([-0.04, 0.0, 0.04]))

    def ornament(img, cx, cy):
        for x, y in ((4, 2), (3, 3), (5, 3), (2, 4), (6, 4), (3, 5), (5, 5), (4, 6)):
            img.putpixel((cx + x, cy + y), gold)
        img.putpixel((cx + 4, cy + 4), rgb("ffe08a"))
        for x, y in ((4, 3), (3, 4), (5, 4), (4, 5)):
            img.putpixel((cx + x, cy + y), rgb("b5402f"))
    corner = plate_corner(rgb("2a2f3b"), rgb("3a4150"), rgb("1a1e27"), rgb("07090d"), ornament)
    save(framed(48, band, corner, rgb("07090d"), rgb("10131a"), rgb("1c212b")), f"{out}/frame.png", nine(48, 48, 9))
    save(noise_tile(rng, [rgb("11141b"), rgb("13161e"), rgb("0f1218")], [70, 15, 15], [(rgb("1a1e28"), 10)]),
         f"{out}/background.png", tile(32, 32))

    def line(img):
        for x in range(12):
            img.putpixel((x, 2), gold)
            img.putpixel((x, 3), gold_dark)
            img.putpixel((x, 4), rgb("000000", 90))
        for x, y in ((6, 1), (5, 2), (7, 2), (6, 3)):
            img.putpixel((x, y), rgb("ffe08a"))

    def diamond(img):
        pixels(img, [(5, 1, "f2b33d"), (4, 2, "f2b33d"), (5, 2, "ffe08a"), (6, 2, "f2b33d"), (3, 3, "f2b33d"), (4, 3, "ffe08a"),
                     (5, 3, "b5402f"), (6, 3, "f2b33d"), (7, 3, "8a6a22"), (4, 4, "f2b33d"), (5, 4, "f2b33d"), (6, 4, "8a6a22"),
                     (5, 5, "8a6a22")], 1, 0)
    divider(out, 12, 6, line, diamond)
    cards_and_tabs(out, rgb("171c26"), rgb("29303e"), rgb("f2b33d"), rgb("1f2531"), rgb("10141b"),
                   rgb("141922"), rgb("262c39"), rgb("232a37"), rgb("f2b33d"))
    slate = [rgb("2a303d"), rgb("252b37"), rgb("303746")]
    buttons(out, rng, slate, rgb("8a6a22"), rgb("f2b33d"), [rgb("1c2029"), rgb("191c24"), rgb("20242e")])


# ---------------------------------------------------------------- rift: crying obsidian with glitches, a torn seam

def rift(out):
    rng = random.Random(31)

    def band(along, across, horizontal):
        if rng.random() < 0.05:
            return rng.choice([rgb("ff5cf0"), rgb("5cf0ff")])
        if across == 1:
            return rgb("b04cd8")
        if across == 6:
            return rgb("1c0a2a")
        return shade(rgb("2d1240") if across in (2, 5) else rgb("3a1a55"), rng.choice([-0.06, 0.0, 0.06]))

    def geode(img, cx, cy):
        for x, y in ((4, 2), (3, 3), (4, 3), (5, 3), (2, 4), (3, 4), (5, 4), (6, 4), (3, 5), (4, 5), (5, 5), (4, 6)):
            img.putpixel((cx + x, cy + y), rgb("c78cff"))
        img.putpixel((cx + 4, cy + 4), rgb("ff5cf0"))
        img.putpixel((cx + 3, cy + 3), rgb("f0d8ff"))
    corner = plate_corner(rgb("241033"), rgb("3e1d57"), rgb("140820"), rgb("07030c"), geode)
    save(framed(48, band, corner, rgb("07030c"), rgb("12071c"), rgb("26103a")), f"{out}/frame.png", nine(48, 48, 9))
    save(noise_tile(rng, [rgb("140a1e"), rgb("170c22"), rgb("11081a")], [70, 15, 15], [(rgb("2c1640"), 10), (rgb("ff5cf0"), 2), (rgb("5cf0ff"), 1)]),
         f"{out}/background.png", tile(32, 32))

    def tear(img):
        jag = [2, 3, 2, 1, 2, 3, 4, 3, 2, 3]
        for x, y in enumerate(jag):
            img.putpixel((x, y), rgb("ff5cf0"))
            img.putpixel((x, y + 1), rgb("b04cd8"))
            img.putpixel((x, min(6, y + 2)), rgb("000000", 110))
        img.putpixel((3, 0), rgb("5cf0ff"))
        img.putpixel((7, 6), rgb("5cf0ff"))

    def orb(img):
        pixels(img, [(5, 2, "ffd0fa"), (4, 3, "ff8cf4"), (5, 3, "ffffff"), (6, 3, "ff5cf0"), (3, 4, "ff5cf0"), (4, 4, "ff8cf4"),
                     (5, 4, "ff8cf4"), (6, 4, "ff5cf0"), (7, 4, "b04cd8"), (4, 5, "ff5cf0"), (5, 5, "ff5cf0"), (6, 5, "b04cd8"),
                     (5, 6, "b04cd8")])
    divider(out, 10, 7, tear, orb)
    cards_and_tabs(out, rgb("1a0d27"), rgb("3e1d57"), rgb("ff5cf0"), rgb("24123a"), rgb("12081c"),
                   rgb("170b22"), rgb("361a4d"), rgb("2a1240"), rgb("ff5cf0"))

    def facets(x, y):
        return rgb("e0b8ff") if (x + y) % 7 == 0 else rgb("6a3a9a") if (x - y) % 9 == 0 else None
    buttons(out, rng, [rgb("8a5ac8"), rgb("7a4ab8"), rgb("9a6ad8")], rgb("12071c"), rgb("ff5cf0"),
            [rgb("2a2233"), rgb("241d2c"), rgb("30283a")], facets)


# ---------------------------------------------------------------- galatea: mossy fig wood and vines, a moon over the marsh

def galatea(out):
    rng = random.Random(41)
    vine = [rgb("6fbf4a"), rgb("4f9a35"), rgb("3a7a28")]

    def band(along, across, horizontal):
        # fig bark (pale grey-brown, vertical grain), moss creeping over the outer edge, a vine winding along the inside
        if across == 1 and rng.random() < 0.55:
            return [rgb("4f7a3a"), rgb("3f6a2e"), rgb("5c8a44")][along % 3]
        if across in (5, 6) and (along // 3) % 4 == 0:
            return vine[(along + across) % 3]
        grain = (along + (across * 3 if horizontal else across)) % 5 == 0
        if rng.random() < 0.12:
            return rgb("54483c")
        return rgb("7d6c5a") if grain else (rgb("968470") if across in (2, 3) else rgb("85745f"))

    def leaf(img, cx, cy):
        for y in range(1, 8):
            for x in range(1, 8):
                if abs(x - y) <= 2 and (x - 4) ** 2 + (y - 4) ** 2 <= 12:
                    img.putpixel((cx + x, cy + y), [rgb("8fd65e"), rgb("6fbf4a"), rgb("4f9a35")][(x + y) % 3])
        for i in range(2, 7):
            img.putpixel((cx + i, cy + i), rgb("2f5e22"))
    corner = plate_corner(rgb("3a3328"), rgb("5a4e3e"), rgb("231e17"), rgb("0b0f0a"), leaf)
    save(framed(48, band, corner, rgb("0b0f0a"), rgb("14201a"), rgb("223828")), f"{out}/frame.png", nine(48, 48, 9))
    bg = noise_tile(rng, [rgb("0d1610"), rgb("0f1912"), rgb("0b130d")], [70, 15, 15], [(rgb("1c3324"), 9), (rgb("b9f0d0"), 2)])
    save(bg, f"{out}/background.png", tile(32, 32))

    def vines(img):
        stem, light = rgb("4f9a35"), rgb("8fd65e")
        for x in range(8):
            img.putpixel((x, 3 + (1 if x in (2, 3) else 0) - (1 if x in (6, 7) else 0)), stem)
        img.putpixel((2, 1), light)
        img.putpixel((3, 2), light)
        img.putpixel((6, 5), light)
        img.putpixel((7, 4), light)

    def moon(img):
        pixels(img, [(x, y, "e6f4ff") for x, y in ((3, 1), (4, 1), (5, 1), (2, 2), (3, 2), (2, 3), (2, 4), (2, 5), (3, 6), (4, 7), (5, 7),
                                                      (3, 3), (3, 4), (3, 5), (4, 6), (6, 7))])
        pixels(img, [(4, 2, "b9d6ea"), (4, 3, "b9d6ea"), (4, 4, "b9d6ea"), (4, 5, "b9d6ea"), (5, 6, "b9d6ea")])
    divider(out, 8, 6, vines, moon)
    cards_and_tabs(out, rgb("132019"), rgb("2c4a36"), rgb("7fe0b4"), rgb("1a2c21"), rgb("0c1510"),
                   rgb("111c15"), rgb("26402f"), rgb("1b3324"), rgb("7fe0b4"))

    def planks(x, y):
        return rgb("5a4a38") if y == 7 or (x + (0 if y < 7 else 5)) % 10 == 0 else None
    buttons(out, rng, [rgb("8a9a5b"), rgb("7a8a4e"), rgb("98a866")], rgb("0b1208"), rgb("c8f5a8"),
            [rgb("343a2c"), rgb("2c3226"), rgb("3a4132")], planks)


THEMES = {"skyblock": skyblock, "rift": rift, "mining": mining, "crimson": crimson, "dungeon": dungeon, "magic": magic, "fishing": fishing, "galatea": galatea}
