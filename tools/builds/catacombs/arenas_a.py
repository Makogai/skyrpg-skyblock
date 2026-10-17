"""Boss arenas A: Floor I Bonzo, Floor II Scarf, Floor III The Professor, Floor IV Thorn.

    python tools\\builds\\catacombs\\arenas_a.py

Everything is seeded per boss, so reruns give identical arenas. Decoration goes through put(), which refuses to
touch the fight area (x/z 5..55, y 9..22), so only the outer ring and the space above y 22 are ever decorated.
"""
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from arena_kit import Arena, FLOOR, LOW, HIGH, OPEN_TOP, MID  # noqa: E402

LAST = 60
SIDES = ("north", "south", "west", "east")
INWARD = {"north": "south", "south": "north", "west": "east", "east": "west"}
CHAIN = "minecraft:iron_chain[axis=y]"


# -- helpers --------------------------------------------------------------------------------------------------------
def free(x, y, z):
    if not (1 <= x <= LAST - 1 and 1 <= z <= LAST - 1 and 1 <= y <= 28):
        return False
    if LOW <= x <= HIGH and LOW <= z <= HIGH and FLOOR < y <= OPEN_TOP:
        return False
    return True


def put(a, x, y, z, block):
    if free(x, y, z):
        a.set(x, y, z, block)


def pfill(a, x1, y1, z1, x2, y2, z2, block):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                put(a, x, y, z, block)


def at(side, i, d):
    """i runs along the wall (0..60), d is the depth from the wall (0 = the wall itself, 1 = first interior cell)."""
    if side == "north":
        return i, d
    if side == "south":
        return i, LAST - d
    if side == "west":
        return d, i
    return LAST - d, i


def sput(a, side, i, d, y, block):
    x, z = at(side, i, d)
    put(a, x, y, z, block)


def floor_pattern(a, fn):
    for x in range(1, LAST):
        for z in range(1, LAST):
            a.set(x, FLOOR, z, fn(x, z))


def lights(a, light, lantern, step=10, off=5):
    """Ceiling light blocks (outer face stays solid) and hanging lanterns from y 23 up on chains."""
    for x in range(off, LAST, step):
        for z in range(off, LAST, step):
            a.set(x, 29, z, light)
    for x in range(10, LAST - 5, 10):
        for z in range(10, LAST - 5, 10):
            for y in range(24, 29):
                a.set(x, y, z, CHAIN)
            a.set(x, 23, z, lantern)


def seg_dist(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    t = max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz)))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


def dist(x, z):
    return math.hypot(x - MID, z - MID)


# -- 1. Bonzo: the circus crypt --------------------------------------------------------------------------------------
def bonzo():
    a = Arena("bonzo")
    rng = random.Random("bonzo")
    a.shell(wall="minecraft:polished_blackstone_bricks", floor="minecraft:polished_blackstone",
            ceiling="minecraft:polished_blackstone_bricks")
    a.weather("bonzo", {"minecraft:polished_blackstone_bricks": [("minecraft:cracked_polished_blackstone_bricks", 0.15)]})

    def flo(x, z):
        d = dist(x, z)
        if d < 3:
            return "minecraft:gold_block"
        if d > 27 and (x + z) % 2:
            return "minecraft:blackstone"
        r = int(d // 5) % 3
        return ("minecraft:red_terracotta", "minecraft:white_terracotta", "minecraft:polished_blackstone")[r]
    floor_pattern(a, flo)

    # striped tent-wall layer
    for side in SIDES:
        for i in range(1, LAST):
            red = (i // 2) % 2 == 0
            for y in range(9, 23):
                if y <= 10:
                    b = "minecraft:polished_blackstone_bricks"
                elif y >= 21:
                    b = "minecraft:red_terracotta" if (i // 3) % 2 == 0 else "minecraft:white_terracotta"
                elif y == 11:
                    b = "minecraft:yellow_terracotta" if i % 6 == 0 else "minecraft:blackstone"
                else:
                    b = "minecraft:red_wool" if red else "minecraft:white_wool"
                    if y == 15 and i % 8 == 4:
                        b = "minecraft:shroomlight"
                sput(a, side, i, 1, y, b)
    # pillars with banners
    for side in SIDES:
        for n, i in enumerate(range(10, 51, 10)):
            if side == "north" and 12 <= i <= 48:
                continue
            for y in range(9, 23):
                b = "minecraft:chiseled_polished_blackstone" if y in (9, 22) else "minecraft:polished_blackstone_bricks"
                sput(a, side, i, 3, y, b)
            col = "red" if n % 2 == 0 else "white"
            for y in (15, 18):
                sput(a, side, i, 4, y, f"minecraft:{col}_wall_banner[facing={INWARD[side]}]")
            sput(a, side, i, 4, 9, "minecraft:red_candle[candles=3,lit=true]")
    # the stage at the north
    for x in range(14, 47):
        for z in (1, 2, 3):
            put(a, x, 9, z, "minecraft:polished_blackstone_bricks")
            put(a, x, 10, z, "minecraft:dark_oak_planks")
        put(a, x, 9, 4, "minecraft:brick_stairs[facing=south,half=bottom]")
        for y in range(11, 22):
            put(a, x, y, 1, "minecraft:red_wool" if (x // 2) % 2 == 0 else "minecraft:white_wool")
        put(a, x, 22, 1, "minecraft:red_terracotta")
        for y in (20, 21, 22):
            put(a, x, y, 4, "minecraft:red_terracotta" if (x // 2) % 2 == 0 else "minecraft:white_terracotta")
        if x % 4 == 0:
            put(a, x, 19, 4, "minecraft:shroomlight")
    for x in list(range(14, 18)) + list(range(43, 47)):
        pfill(a, x, 11, 2, x, 21, 3, "minecraft:red_wool")
    for x in (13, 47):
        for z in range(1, 5):
            for y in range(9, 23):
                put(a, x, y, z, "minecraft:gold_block" if y == 22 else "minecraft:polished_blackstone_bricks")
    # big top: a striped tent roof with a lit ring
    for x in range(1, LAST):
        for z in range(1, LAST):
            ang = math.atan2(z - MID, x - MID) + math.pi
            k = int(ang / (math.pi / 12))
            a.set(x, 28, z, "minecraft:red_wool" if k % 2 == 0 else "minecraft:white_wool")
    for r in (12, 22):
        for k in range(0, 24):
            ang = k * math.pi / 12 + math.pi / 24
            a.set(round(MID + r * math.cos(ang)), 28, round(MID + r * math.sin(ang)), "minecraft:shroomlight")
    # balloons
    colors = ["red", "yellow", "light_blue", "lime", "pink", "orange", "purple", "white"]
    for _ in range(18):
        bx, bz = rng.randint(4, 56), rng.randint(4, 56)
        if bx % 10 == 0 and bz % 10 == 0:
            continue
        for _ in range(rng.randint(2, 4)):
            cx, cz = bx + rng.randint(-2, 2), bz + rng.randint(-2, 2)
            cy = rng.randint(24, 25)
            if not (2 <= cx <= 58 and 2 <= cz <= 58):
                continue
            kind = rng.choice(("concrete", "wool"))
            c = f"minecraft:{rng.choice(colors)}_{kind}"
            for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
                put(a, cx + dx, cy + dy, cz + dz, c)
            for y in range(cy + 2, 29):
                put(a, cx, y, cz, CHAIN)
    lights(a, "minecraft:glowstone", "minecraft:lantern[hanging=true]")
    a.save()


# -- 2. Scarf: the ritual chamber ------------------------------------------------------------------------------------
def scarf():
    a = Arena("scarf")
    rng = random.Random("scarf")
    a.shell(wall="minecraft:deepslate_bricks", floor="minecraft:polished_deepslate", ceiling="minecraft:deepslate_bricks")
    a.weather("scarf", {"minecraft:deepslate_bricks": [("minecraft:cracked_deepslate_bricks", 0.15)]})
    verts = [(MID + 24 * math.cos(math.radians(-90 + 72 * k)), MID + 24 * math.sin(math.radians(-90 + 72 * k)))
             for k in range(5)]
    lines = [(verts[k], verts[(k + 2) % 5]) for k in range(5)]

    def flo(x, z):
        d = dist(x, z)
        if d < 1.5:
            return "minecraft:obsidian"
        if abs(d - 25) < 0.8:
            return "minecraft:red_nether_bricks"
        if abs(d - 27.5) < 0.6:
            return "minecraft:crying_obsidian"
        if d <= 26 and any(seg_dist(x, z, *p, *q) < 0.7 for p, q in lines):
            return "minecraft:crying_obsidian"
        if d < 5:
            return "minecraft:purple_terracotta"
        if d < 24 and abs(d - 14) < 0.6:
            return "minecraft:red_nether_bricks"
        r = rng.random()
        if r < 0.18:
            return "minecraft:cracked_deepslate_tiles"
        if r < 0.28:
            return "minecraft:polished_blackstone"
        return "minecraft:deepslate_tiles"
    floor_pattern(a, flo)

    for side in SIDES:
        for i in range(1, LAST):
            for y in range(9, 23):
                if y in (14, 20):
                    b = "minecraft:red_nether_bricks" if i % 2 == 0 else "minecraft:nether_bricks"
                elif y in (9, 22):
                    b = "minecraft:polished_deepslate"
                elif y == 17 and i % 10 == 5:
                    b = "minecraft:shroomlight"
                else:
                    b = "minecraft:deepslate_bricks"
                sput(a, side, i, 1, y, b)
        for n, i in enumerate((15, 25, 35, 45)):
            col = "purple" if n % 2 == 0 else "red"
            sput(a, side, i, 2, 17, f"minecraft:{col}_wall_banner[facing={INWARD[side]}]")
            sput(a, side, i, 2, 13, f"minecraft:{col}_wall_banner[facing={INWARD[side]}]")
    # tall pillars in the ring (corners and along the walls)
    spots = []
    for side in SIDES:
        for i in (10, 20, 40, 50):
            spots.append((side, i))
    pil = set()
    for side, i in spots:
        for di in (0, 1):
            for dd in (0, 1):
                x, z = at(side, i + di, 3 + dd)
                pil.add((x, z))
    for cx in (2, 57):
        for cz in (2, 57):
            for dx in (0, 1):
                for dz in (0, 1):
                    pil.add((cx + dx, cz + dz))
    for x, z in sorted(pil):
        for y in range(9, 29):
            if y in (9, 10, 21, 22):
                b = "minecraft:chiseled_deepslate"
            elif y in (15, 16):
                b = "minecraft:amethyst_block"
            else:
                b = "minecraft:polished_deepslate"
            put(a, x, y, z, b)
        # candles round the foot
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) not in pil and rng.random() < 0.45:
                put(a, x + dx, 9, z + dz,
                    f"minecraft:{rng.choice(('red', 'purple', 'black'))}_candle[candles={rng.randint(1, 4)},lit=true]")
    # arches between the pillars high up
    for side in SIDES:
        for i in range(10, 52):
            sput(a, side, i, 4, 22, "minecraft:deepslate_brick_stairs[facing=%s,half=top]" % INWARD[side])
    # loose candles along the walls
    for side in SIDES:
        for i in range(6, 55):
            if rng.random() < 0.12:
                x, z = at(side, i, 2)
                if (x, z) not in pil:
                    put(a, x, 9, z, f"minecraft:{rng.choice(('red', 'purple'))}_candle[candles={rng.randint(1, 4)},lit=true]")
    # the altar against the north wall
    for x in range(22, 39):
        put(a, x, 9, 4, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]")
        for z in (2, 3):
            put(a, x, 9, z, "minecraft:polished_blackstone_bricks")
        put(a, x, 9, 1, "minecraft:polished_blackstone_bricks")
    for x in range(24, 37):
        put(a, x, 10, 3, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]")
        for z in (1, 2):
            put(a, x, 10, z, "minecraft:polished_blackstone_bricks")
    for x in range(27, 34):
        put(a, x, 11, 2, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]")
        put(a, x, 11, 1, "minecraft:crying_obsidian")
        put(a, x, 12, 1, "minecraft:crying_obsidian")
    for x in (27, 29, 31, 33):
        put(a, x, 12, 2, "minecraft:red_candle[candles=4,lit=true]")
    put(a, 30, 12, 2, "minecraft:purple_candle[candles=3,lit=true]")
    put(a, 30, 13, 1, "minecraft:chiseled_polished_blackstone")
    for y in range(14, 22):
        for x in range(26, 35):
            put(a, x, y, 1, "minecraft:purple_terracotta")
        put(a, 30, y, 1, "minecraft:crying_obsidian" if y % 2 == 0 else "minecraft:magenta_glazed_terracotta")
    for x in (25, 35):
        for y in range(13, 22):
            put(a, x, y, 1, "minecraft:shroomlight" if y == 17 else "minecraft:red_nether_bricks")
    lights(a, "minecraft:shroomlight", "minecraft:soul_lantern[hanging=true]")
    a.save()


# -- 3. The Professor: the guardian laboratory ------------------------------------------------------------------------
def professor():
    a = Arena("professor")
    rng = random.Random("professor")
    a.shell(wall="minecraft:prismarine_bricks", floor="minecraft:dark_prismarine", ceiling="minecraft:prismarine_bricks")
    a.weather("professor", {"minecraft:prismarine_bricks": [("minecraft:prismarine", 0.12), ("minecraft:dark_prismarine", 0.08)]})

    def flo(x, z):
        d = dist(x, z)
        if 11 <= d < 12.2:
            return "minecraft:sea_lantern"
        if d < 3:
            return "minecraft:cyan_terracotta"
        if x % 6 == 0 or z % 6 == 0:
            return "minecraft:dark_prismarine"
        return "minecraft:prismarine_bricks" if (x // 6 + z // 6) % 2 == 0 else "minecraft:prismarine"
    floor_pattern(a, flo)

    for side in SIDES:
        for i in range(1, LAST):
            for y in range(9, 23):
                if y == 9:
                    b = "minecraft:dark_prismarine"
                elif y <= 17:
                    if i % 6 == 0:
                        b = "minecraft:dark_prismarine"
                    else:
                        b = "minecraft:bookshelf"
                        if y == 13 and rng.random() < 0.3:
                            b = f"minecraft:chiseled_bookshelf[facing={INWARD[side]}]"
                elif y == 18:
                    b = "minecraft:dark_prismarine"
                elif y == 20 and i % 6 == 3:
                    b = "minecraft:sea_lantern"
                else:
                    b = "minecraft:prismarine_bricks"
                sput(a, side, i, 1, y, b)
    # glass tanks
    tanks = []
    for side in SIDES:
        for i in (10, 20, 30, 40, 50):
            tanks.append(at(side, i, 3))
    for cx, cz in tanks:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                x, z = cx + dx, cz + dz
                put(a, x, 9, z, "minecraft:dark_prismarine")
                put(a, x, 17, z, "minecraft:dark_prismarine")
                put(a, x, 18, z, "minecraft:sea_lantern" if dx == 0 and dz == 0 else "minecraft:prismarine_brick_slab")
                for y in range(10, 17):
                    edge = dx != 0 and dz != 0
                    if edge:
                        b = "minecraft:dark_prismarine"
                    elif dx == 0 and dz == 0:
                        b = "minecraft:sea_lantern" if y in (11, 14) else "minecraft:prismarine_wall"
                    elif dx == 0 or dz == 0:
                        b = "minecraft:glass"
                    put(a, x, y, z, b)
        put(a, cx, 10, cz, "minecraft:sea_lantern")
    # lab benches between the tanks
    for side in SIDES:
        for i in (15, 25, 35, 45):
            f = INWARD[side]
            sput(a, side, i, 2, 9, f"minecraft:lectern[facing={f}]")
            sput(a, side, i + 1, 2, 9, "minecraft:brewing_stand" if i % 20 == 5 else "minecraft:cauldron")
            sput(a, side, i - 1, 2, 9, "minecraft:dark_prismarine_slab[type=bottom]")
    # beams across the top
    for z in (15, 30, 45):
        for x in range(1, LAST):
            a.set(x, 28, z, "minecraft:sea_lantern" if x % 6 == 3 else "minecraft:dark_prismarine")
    lights(a, "minecraft:sea_lantern", "minecraft:lantern[hanging=true]")
    a.save()


# -- 4. Thorn: the overgrown spirit cavern ----------------------------------------------------------------------------
def thorn():
    a = Arena("thorn")
    rng = random.Random("thorn")
    a.shell(wall="minecraft:mossy_stone_bricks", floor="minecraft:mossy_cobblestone", ceiling="minecraft:mossy_stone_bricks")
    a.weather("thorn", {"minecraft:mossy_stone_bricks": [("minecraft:cracked_stone_bricks", 0.1),
                                                          ("minecraft:moss_block", 0.12),
                                                          ("minecraft:mossy_cobblestone", 0.1)]})

    def flo(x, z):
        n = math.sin(x * 0.45) + math.sin(z * 0.37) + math.sin((x + z) * 0.23) + math.cos((x - z) * 0.31) + rng.random() * 0.8
        if n > 2.2:
            return "minecraft:rooted_dirt"
        if n > 1.2:
            return "minecraft:moss_block"
        if n > 0.2:
            return "minecraft:mossy_cobblestone"
        if n > -0.7:
            return "minecraft:moss_block" if rng.random() < 0.6 else "minecraft:podzol"
        if n > -1.6:
            return "minecraft:mossy_stone_bricks"
        return "minecraft:coarse_dirt"
    floor_pattern(a, flo)

    leaves = ["minecraft:dark_oak_leaves[persistent=true]", "minecraft:azalea_leaves[persistent=true]",
              "minecraft:flowering_azalea_leaves[persistent=true]", "minecraft:oak_leaves[persistent=true]"]
    # ring floor greenery
    for x in range(1, LAST):
        for z in range(1, LAST):
            if LOW <= x <= HIGH and LOW <= z <= HIGH:
                continue
            r = rng.random()
            if r < 0.35:
                put(a, x, 9, z, "minecraft:moss_carpet")
            elif r < 0.55:
                put(a, x, 9, z, "minecraft:short_grass")
            elif r < 0.63:
                put(a, x, 9, z, "minecraft:fern")
            elif r < 0.69:
                put(a, x, 9, z, rng.choice(("minecraft:azalea", "minecraft:flowering_azalea")))
            elif r < 0.72:
                put(a, x, 9, z, rng.choice(("minecraft:red_mushroom", "minecraft:brown_mushroom")))
    # mossy wall layer, partly collapsed, with vines
    for side in SIDES:
        for i in range(1, LAST):
            h = rng.randint(17, 22)
            for y in range(9, h + 1):
                r = rng.random()
                b = "minecraft:mossy_stone_bricks" if r < 0.5 else "minecraft:moss_block" if r < 0.7 else \
                    "minecraft:mossy_cobblestone" if r < 0.9 else "minecraft:cracked_stone_bricks"
                sput(a, side, i, 1, y, b)
            if h < 22:
                for y in range(h + 1, 23):
                    sput(a, side, i, 1, y, "minecraft:air")
    # vines: on the face of the wall layer, so they point at the wall (the opposite of "inward")
    for side in SIDES:
        for i in range(1, LAST):
            if rng.random() < 0.5:
                top = rng.randint(14, 22)
                for y in range(top, top - rng.randint(3, 9), -1):
                    x, z = at(side, i, 2)
                    wx, wz = at(side, i, 1)
                    if y >= 10 and free(x, y, z) and a.get(wx, y, wz) not in (None, "minecraft:air"):
                        # a vine touches the block it hangs on, which lies toward the wall (the side's own name)
                        a.set(x, y, z, f"minecraft:vine[{side}=true]")
    # dark oak trunks with canopies
    trunks = []
    for side in SIDES:
        for i in (8, 20, 40, 52):
            j = i + rng.randint(-2, 2)
            trunks.append(at(side, j, 3))
    for tx, tz in trunks:
        for dx in (0, 1):
            for dz in (0, 1):
                for y in range(9, 29):
                    put(a, tx + dx, y, tz + dz, "minecraft:dark_oak_log[axis=y]")
                put(a, tx + dx, 9, tz + dz, "minecraft:dark_oak_wood")
        for dx, dz in ((-1, 0), (2, 0), (0, -1), (0, 2), (-1, 1), (2, 1), (1, -1), (1, 2)):
            if rng.random() < 0.6:
                put(a, tx + dx, 9, tz + dz, "minecraft:mangrove_roots")
        for dx in range(-5, 7):
            for dz in range(-5, 7):
                for y in range(23, 29):
                    d = math.hypot(dx - 0.5, dz - 0.5) + abs(y - 26) * 0.7
                    if d < 5.5 and rng.random() < 0.85:
                        put(a, tx + dx, y, tz + dz, rng.choice(leaves))
    # the general canopy and what hangs from it
    for x in range(1, LAST):
        for z in range(1, LAST):
            if rng.random() < 0.9:
                a.set(x, 28, z, rng.choice(leaves))
            if rng.random() < 0.55:
                a.set(x, 27, z, rng.choice(leaves))
            if rng.random() < 0.15:
                a.set(x, 26, z, rng.choice(leaves))
            if rng.random() < 0.05:
                a.set(x, 25, z, rng.choice(leaves))
    for x in range(2, LAST - 1):
        for z in range(2, LAST - 1):
            if rng.random() < 0.05 and a.get(x, 28, z) is not None and (x % 10, z % 10) != (0, 0):
                n = rng.randint(2, 4)
                for y in range(27, 27 - n, -1):
                    if y >= 23:
                        put(a, x, y, z, "minecraft:cave_vines_plant[berries=true]")
                if 27 - n >= 23:
                    put(a, x, 27 - n, z, "minecraft:cave_vines[age=25,berries=true]")
    for k in range(40):
        x, z = rng.randint(2, 58), rng.randint(2, 58)
        a.set(x, 27, z, "minecraft:shroomlight")
    lights(a, "minecraft:glowstone", "minecraft:lantern[hanging=true]")
    a.save()


if __name__ == "__main__":
    bonzo()
    scarf()
    professor()
    thorn()
