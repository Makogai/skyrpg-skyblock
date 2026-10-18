#!/usr/bin/env python3
"""The Rift, West Village (x -140..-31, z -120..-32): a slightly unsettling village of crooked timber-and-plaster houses
(pale oak, dark oak, mud bricks, calcite, purpur trims) on narrow lanes, with walkable rooftops linked by planks.
Sub-locations: the Cake House (two floors, a giant cake), the Infested House (cobwebs, a locked iron door with a barred
window, Kat), the Dolphin Trainer (a pool with a parkour course to a platform), the village square with a well (and a
flooded passage under it), Gunther's race start (banner arch) and a 10-checkpoint lap, the Mirrorverse door in a
dead-end alley, Okron's tower (Montezuma piece 4 on its roof).

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_west_village.py

Neighbours: the Village Plaza along x = -30 (crossing at z -70), the Dreadfarm along z = -121 (crossing at x -70): the
surface stays at y 68 there. The south side ends in a cliff over the void (a 3-wide rope-and-plank bridge at x -91..-89
runs to the box edge z -32, towards the Black Lagoon); the west edge (x -140) is the Rift's outer edge.
"""
import math
import random

from kit import hash01, noise
from rift_lib import new_area, land, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("west_village", version=3)
area.shell = 9
S = area.set
G = GROUND                      # surface block y (players walk at 69)
X0, X1, Z0, Z1 = -140, -31, -120, -31
R = random.Random(23)


def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def jag(a, b, s):
    return 5 * noise(a, b, s, 1.2) + 2.5 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 1.2 * (hash01(int(a), 2, int(b), s) - 0.5)


def zs(x):
    """South land edge (last land z) at column x: about -36, level at the bridge landing."""
    if -96 <= x <= -84:
        return -36
    return -36 + int(round(max(-2.0, min(1.0, jag(x, 7, 4) * 0.4))))


def edge(x, z):
    """Blocks to the Rift's outer edge (west) or the south cliff; negative = void. North and east are seams."""
    dw = x - (-137 + int(jag(z, 0, 1) * 0.8))
    ds = zs(x) - z
    return min(dw, ds)


# ---------------------------------------------------------------- lanes
LANES = {
    "A": ([(-134, -70), (-46, -70)], 3),
    "A2": ([(-46, -70), (-31, -70)], 5),            # the crossing to the Plaza (x -30 side), z -72..-68
    "B": ([(-70, -113), (-70, -70)], 3),
    "B2": ([(-70, -120), (-70, -113)], 5),          # the crossing to the Dreadfarm (z -121 side), x -72..-68
    "C": ([(-90, -70), (-90, -37)], 3),
    "D": ([(-112, -52), (-40, -52)], 3),
    "E": ([(-112, -118), (-112, -40)], 3),
    "F": ([(-112, -100), (-40, -100)], 3),
    "G": ([(-50, -100), (-50, -52)], 3),
    "J": ([(-38, -113), (-38, -52)], 3),
    "K": ([(-110, -86), (-72, -86)], 3),
    "H": ([(-124, -105), (-124, -45)], 3),
    "I1": ([(-135, -92), (-113, -92)], 3),
    "I2": ([(-135, -62), (-113, -62)], 3),
}
lane_cells = {}
lane_axis = {}
for name, (pts, w) in LANES.items():
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            px, pz = x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n
            r = w / 2.0
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz <= r * r + 0.3:
                        c = (int(round(px)) + dx, int(round(pz)) + dz)
                        lane_cells.setdefault(c, name)
                        lane_axis.setdefault(c, "x" if x1 != x2 else "z")

SQUARE = (-85, -76, -75, -64)                       # x1, z1, x2, z2 (paved)
COURT = (-127, -113, -121, -106)                    # the Mirrorverse courtyard (paved)
POOL = (-67, -118, -53, -104)                       # the Dolphin Trainer's pool, rim included
RESERVED = {}                                       # (x, z) -> what
RECTS = {
    "square": SQUARE, "court": COURT, "pool": (-68, -119, -52, -102),
    "race": (-48, -77, -38, -63), "chef": (-60, -78, -48, -71), "grubber": (-97, -78, -89, -71),
    "tower": (-67, -87, -57, -77), "well": (-85, -76, -75, -64), "gate": (-95, -41, -85, -37),
}


def inrect(x, z, r, m=0):
    return r[0] - m <= x <= r[2] + m and r[1] - m <= z <= r[3] + m


def reserved(x, z, m=0):
    return any(inrect(x, z, r, m) for r in RECTS.values())


# ---------------------------------------------------------------- the ground
UNDER = area.mix({"mud": 6, "packed_mud": 2, "rooted_dirt": 1, "dirt": 1})
BODY = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1, "andesite": 1})
UNDERSIDE = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1})
LANE_SURF = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3, "packed_mud": 2})
PAVE = area.mix({"mud_bricks": 4, "packed_mud": 3, "tuff_bricks": 3, "polished_tuff": 1})


def ground_block(x, y, z):
    if inrect(x, z, SQUARE) or inrect(x, z, COURT):
        return PAVE(x, y, z)
    c = (x, z)
    if c in lane_cells:
        return LANE_SURF(x, y, z)
    n = noise(x, z, 41, 2.6)
    h = hash01(x, 2, z, 9)
    if n > -0.1:
        return "minecraft:grass_block" if h < 0.85 else "minecraft:rooted_dirt"
    if n < -0.45:
        return "minecraft:moss_block" if h < 0.7 else "minecraft:mud"
    return "minecraft:coarse_dirt" if h < 0.5 else "minecraft:grass_block"


def under_depth(x, z, e):
    t = sm(e / 55.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    if x > -115 and z < -45:
        d = max(d, 14 + 4 * hash01(x, 6, z, 8))
    return int(min(d, 29))


LAND = set()
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z)
        if e < 0:
            continue
        top = G
        if e < 2.5 and (x, z) not in lane_cells:                     # broken cliff at the west and south rim
            top = G - int(hash01(x, 9, z, 3) * 3.4)
        LAND.add((x, z))
        land(area, x, z, top, ground_block, UNDER, BODY)
        area.top[(x, z)] = top
        d = under_depth(x, z, min(e, 99))
        for k in range(1, d + 1):
            y = LAND_BOTTOM - k
            r = hash01(x, y, z, 12)
            if e < 14 and r < 0.04:
                b = "minecraft:crying_obsidian"
            elif r < 0.035:
                b = "minecraft:amethyst_block"
            else:
                b = UNDERSIDE(x, y, z)
            S(x, y, z, b)
        if e < 30 and hash01(x, 10, z, 13) < 0.012:
            ln = 3 + int(hash01(x, 11, z, 14) * 8)
            yy = LAND_BOTTOM - d
            for i in range(ln):
                S(x, yy - 1 - i, z, "minecraft:crying_obsidian" if hash01(x, i, z, 15) < 0.4 else "minecraft:amethyst_block")
            S(x, yy - 1 - ln, z, "minecraft:amethyst_cluster[facing=down]")

# floating shards beyond the west and south cliffs
for i in range(40):
    x = int(X0 + hash01(i, 0, 0, 77) * 30)
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 6))
    if i > 9 or (x, z) in LAND or edge(x, z) < -16 or edge(x, z) > -3:
        continue
    y = 52 + int(hash01(i, 2, 0, 77) * 26)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    b = "minecraft:tuff" if q < 0.7 else "minecraft:mud"
                    if hash01(x + dx, y + dy, z + dz, 5) < 0.08:
                        b = "minecraft:crying_obsidian"
                    S(x + dx, y + dy, z + dz, b)
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:amethyst_block" if k % 2 == 0 else "minecraft:crying_obsidian")


# ---------------------------------------------------------------- houses
DIRV = {"N": (0, -1), "S": (0, 1), "E": (1, 0), "W": (-1, 0)}
FACE = {"N": "north", "S": "south", "E": "east", "W": "west"}
OPP = {"N": "S", "S": "N", "E": "W", "W": "E"}
PALS = [
    dict(low="mud_bricks", up="calcite", beam="dark_oak_log", plate="stripped_dark_oak_log", roof="dark_oak_stairs", fill="calcite"),
    dict(low="pale_oak_planks", up="pale_oak_planks", beam="dark_oak_log", plate="stripped_dark_oak_log", roof="mud_brick_stairs", fill="pale_oak_planks"),
    dict(low="mud_bricks", up="mud_bricks", beam="pale_oak_log", plate="stripped_pale_oak_log", roof="pale_oak_stairs", fill="mud_bricks"),
    dict(low="calcite", up="calcite", beam="dark_oak_log", plate="stripped_dark_oak_log", roof="dark_oak_stairs", fill="calcite"),
]
FOOT = set()            # cells under any building (upper-floor overhangs included)
HOUSES = []             # records
ROOFS = []              # flat, walkable roofs: (x1, z1, x2, z2, y)


def door_block(kind, facing, half):
    return f"minecraft:{kind}[facing={facing},half={half},hinge=left,open=false,powered=false]"


def house(x1, z1, x2, z2, floors=1, door="S", pal=0, flat=None, ladder_out=True, furnish=True, ladder_in=True, jetty=True,
          door_i=None, name=""):
    P = PALS[pal % len(PALS)]
    rects = []
    for n in range(floors):
        r = [x1, z1, x2, z2]
        if n >= 1 and jetty:
            if door == "N":
                r[1] -= 1
            elif door == "S":
                r[3] += 1
            elif door == "W":
                r[0] -= 1
            else:
                r[2] += 1
        rects.append(tuple(r))
    roofy = G + 4 * floors
    for n in range(floors):
        base = G + 4 * n
        a1, b1, a2, b2 = rects[n]
        # floor plane
        floor = P and ("pale_oak_planks" if n == 0 else "dark_oak_planks")
        for x in range(a1, a2 + 1):
            for z in range(b1, b2 + 1):
                perim = x in (a1, a2) or z in (b1, b2)
                S(x, base, z, "minecraft:mud_bricks" if (n == 0 and perim) else f"minecraft:{floor}")
                if not perim:
                    for y in range(base + 1, base + 4):
                        S(x, y, z, "minecraft:air")
        # overhang braces
        if n >= 1 and rects[n] != rects[n - 1]:
            for x in range(a1, a2 + 1):
                for z in range(b1, b2 + 1):
                    if (a1, b1, a2, b2) != rects[n - 1] and not (rects[n - 1][0] <= x <= rects[n - 1][2] and rects[n - 1][1] <= z <= rects[n - 1][3]):
                        S(x, base - 1, z, f"minecraft:dark_oak_stairs[facing={FACE[OPP[door]]},half=top]" if hash01(x, base, z, 4) < 0.5 else "minecraft:dark_oak_fence")
        # walls
        for x in range(a1, a2 + 1):
            for z in range(b1, b2 + 1):
                if not (x in (a1, a2) or z in (b1, b2)):
                    continue
                corner = x in (a1, a2) and z in (b1, b2)
                if z in (b1, b2):
                    i, L, ax = x - a1, a2 - a1 + 1, "x"
                    side = "N" if z == b1 else "S"
                else:
                    i, L, ax = z - b1, b2 - b1 + 1, "z"
                    side = "W" if x == a1 else "E"
                wall = P["low"] if n == 0 else P["up"]
                isdoor = (n == 0 and side == door and not corner and i == (door_i if door_i is not None else L // 2))
                for y in range(base + 1, base + 4):
                    if corner:
                        b = f"minecraft:{P['beam']}[axis=y]"
                    elif isdoor and y <= base + 2:
                        b = door_block("pale_oak_door", FACE[door], "lower" if y == base + 1 else "upper")
                    elif y == base + 3:
                        b = f"minecraft:{P['plate']}[axis={ax}]"
                    elif i % 4 == 0:
                        b = f"minecraft:{P['beam']}[axis=y]"
                    elif i % 4 == 2 and y == base + 2 and not (n == 0 and side == door and abs(i - L // 2) <= 1):
                        b = "minecraft:purple_stained_glass" if hash01(x, y, z, 8) < 0.4 else "minecraft:glass"
                    else:
                        b = f"minecraft:{wall}"
                    S(x, y, z, b)
    # the roof
    a1, b1, a2, b2 = rects[-1]
    for x in range(a1, a2 + 1):
        for z in range(b1, b2 + 1):
            S(x, roofy, z, "minecraft:dark_oak_planks")
            FOOT.add((x, z))
    for n in range(floors):
        for x in range(rects[n][0], rects[n][2] + 1):
            for z in range(rects[n][1], rects[n][3] + 1):
                FOOT.add((x, z))
    is_flat = flat if flat is not None else (hash01(x1, 0, z1, 5) < 0.5)
    if is_flat:
        for x in range(a1, a2 + 1):
            for z in range(b1, b2 + 1):
                if x in (a1, a2) or z in (b1, b2):
                    S(x, roofy + 1, z, "minecraft:purpur_slab[type=bottom]")
        for (cx, cz) in ((a1, b1), (a1, b2), (a2, b1), (a2, b2)):
            S(cx, roofy + 1, cz, "minecraft:purpur_pillar[axis=y]")
        ROOFS.append((a1, b1, a2, b2, roofy))
    else:
        gable(a1, b1, a2, b2, roofy, P)
    rec = dict(rect=(x1, z1, x2, z2), top=rects[-1], floors=floors, door=door, roofy=roofy, flat=is_flat, name=name)
    HOUSES.append(rec)
    # door yard: lantern post beside the door
    ix1, iz1, ix2, iz2 = x1, z1, x2, z2
    dv = DIRV[door]
    if door in "NS":
        dx_ = x1 + (door_i if door_i is not None else (x2 - x1 + 1) // 2)
        dz_ = z1 if door == "N" else z2
        px_, pz_ = dx_ + 2, dz_ + dv[1]
        pxr, pzr = dx_ - 2, dz_ + dv[1]
    else:
        dz_ = z1 + (door_i if door_i is not None else (z2 - z1 + 1) // 2)
        dx_ = x1 if door == "W" else x2
        px_, pz_ = dx_ + dv[0], dz_ + 2
        pxr, pzr = dx_ + dv[0], dz_ - 2
    rec["door_cell"] = (dx_, dz_)
    for (px, pz) in ((px_, pz_),):
        if (px, pz) not in lane_cells and area.get(px, G + 1, pz) in (None, "minecraft:air") and (px, pz) in LAND:
            S(px, G + 1, pz, "minecraft:pale_oak_fence")
            S(px, G + 2, pz, "minecraft:pale_oak_fence")
            S(px, G + 3, pz, "minecraft:lantern[hanging=false]")
    if furnish:
        furnish_rooms(rec, rects, P)
    if ladder_in and floors >= 2 and furnish:
        back = OPP[door]
        a1, b1, a2, b2 = rects[0]
        if back == "N":
            lx, lz, lf = (a1 + a2) // 2, b1 + 1, "south"
        elif back == "S":
            lx, lz, lf = (a1 + a2) // 2, b2 - 1, "north"
        elif back == "W":
            lx, lz, lf = a1 + 1, (b1 + b2) // 2, "east"
        else:
            lx, lz, lf = a2 - 1, (b1 + b2) // 2, "west"
        S(lx, G + 4, lz, "minecraft:air")
        for y in range(G + 1, G + 6):
            S(lx, y, lz, f"minecraft:ladder[facing={lf}]")
    if ladder_out and is_flat:
        a1, b1, a2, b2 = rects[0]
        ua1, ub1, ua2, ub2 = rects[-1]
        opts = []
        back = OPP[door]
        for sd in (back, "E" if door in "NS" else "N", "W" if door in "NS" else "S"):
            if sd == "N":
                opts.append(((a1 + a2) // 2 + 1, b1 - 1, "north", (0, -1)))
            elif sd == "S":
                opts.append(((a1 + a2) // 2 + 1, b2 + 1, "south", (0, 1)))
            elif sd == "W":
                opts.append((a1 - 1, (b1 + b2) // 2 + 1, "west", (-1, 0)))
            else:
                opts.append((a2 + 1, (b1 + b2) // 2 + 1, "east", (1, 0)))
        for (lx, lz, lf, dv2) in opts:
            if (lx, lz) in FOOT or (lx, lz) in lane_cells or reserved(lx, lz) or (lx, lz) not in LAND:
                continue
            if sd_blocked(lx, lz):
                continue
            for y in range(G + 1, roofy + 2):
                S(lx, y, lz, f"minecraft:ladder[facing={lf}]")
            break
    return rec


def sd_blocked(x, z):
    return any(area.get(x, y, z) not in (None, "minecraft:air") for y in range(G + 1, G + 4))


def furnish_rooms(rec, rects, P):
    for n, (a1, b1, a2, b2) in enumerate(rects):
        base = G + 4 * n
        ix1, ix2, iz1, iz2 = a1 + 1, a2 - 1, b1 + 1, b2 - 1
        cx, cz = (ix1 + ix2) // 2, (iz1 + iz2) // 2
        S(cx, base + 3, cz, "minecraft:lantern[hanging=true]")
        back = OPP[rec["door"]]
        # a bed against the back wall, bookshelves at one side, a table
        if back in "NS":
            bz = iz1 if back == "N" else iz2
            hf = "north" if back == "N" else "south"
            bx = ix1 + (1 if hash01(ix1, base, iz1, 2) < 0.5 else 0)
            fz = bz + (1 if back == "N" else -1)
            S(bx, base + 1, bz, f"minecraft:red_bed[facing={hf},part=head]")
            S(bx, base + 1, fz, f"minecraft:red_bed[facing={hf},part=foot]")
            for x in (ix2, ix2 - 1):
                S(x, base + 1, bz, "minecraft:bookshelf")
                S(x, base + 2, bz, "minecraft:bookshelf")
        else:
            bx = ix1 if back == "W" else ix2
            hf = "west" if back == "W" else "east"
            bz = iz1 + (1 if hash01(ix1, base, iz1, 3) < 0.5 else 0)
            fx = bx + (1 if back == "W" else -1)
            S(bx, base + 1, bz, f"minecraft:red_bed[facing={hf},part=head]")
            S(fx, base + 1, bz, f"minecraft:red_bed[facing={hf},part=foot]")
            for z in (iz2, iz2 - 1):
                S(bx, base + 1, z, "minecraft:bookshelf")
                S(bx, base + 2, z, "minecraft:bookshelf")
        if (ix2 - ix1) >= 4 and (iz2 - iz1) >= 4:
            S(cx, base + 1, cz, "minecraft:pale_oak_fence")
            S(cx, base + 2, cz, "minecraft:pale_oak_pressure_plate")
        for (x, z) in ((ix1, iz1), (ix2, iz1), (ix1, iz2), (ix2, iz2)):
            if hash01(x, base, z, 6) < 0.3 and area.get(x, base + 3, z) in (None, "minecraft:air"):
                S(x, base + 3, z, "minecraft:cobweb")


def gable(a1, b1, a2, b2, roofy, P):
    along_x = (a2 - a1) >= (b2 - b1)
    lo, hi = (b1, b2) if along_x else (a1, a2)
    w = hi - lo + 3                       # with an eave on both sides
    kc = (w - 1) // 2
    for j in range(w):
        k = min(j, w - 1 - j)
        s = lo - 1 + j
        rising_first = j <= (w - 1) / 2
        if along_x:
            face = "south" if rising_first else "north"
        else:
            face = "east" if rising_first else "west"
        odd_center = (w % 2 == 1 and j == kc)
        rng = range(a1, a2 + 1) if along_x else range(b1, b2 + 1)
        for t in rng:
            x, z = (t, s) if along_x else (s, t)
            y = roofy + k
            if odd_center:
                S(x, y, z, "minecraft:purpur_slab[type=bottom]")
            elif k == 0:
                S(x, y, z, f"minecraft:purpur_stairs[facing={face},half=bottom]")
            else:
                S(x, y, z, f"minecraft:{P['roof']}[facing={face},half=bottom]")
            if t in (rng.start, rng.stop - 1):
                for yy in range(roofy + 1, y):
                    S(x, yy, z, f"minecraft:{P['fill']}")
    if hash01(a1, 1, b1, 9) < 0.5:
        cxx = a1 + 2 if along_x else (a1 + a2) // 2
        czz = (b1 + b2) // 2 if along_x else b1 + 2
        for y in range(roofy + 1, roofy + kc + 3):
            S(cxx, y, czz, "minecraft:mud_bricks")
        S(cxx, roofy + kc + 3, czz, "minecraft:mud_brick_wall")


# ---------------------------------------------------------------- reserved (special) buildings
def paint_rect(r, y, block):
    for x in range(r[0], r[2] + 1):
        for z in range(r[1], r[3] + 1):
            S(x, y, z, block)


# ---- the Cake House: x -108..-97, z -84..-73, two floors, a giant cake
CK = (-108, -84, -97, -73)
RESERVED_BUILD = [CK, (-108, -67, -98, -58), (-80, -62, -72, -54), (-66, -64, -58, -56), (-66, -49, -58, -43), (-84, -49, -76, -43)]
for r in RESERVED_BUILD:
    RECTS["b%d" % len(RECTS)] = (r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1)
house(*CK, floors=2, door="S", pal=3, flat=False, furnish=False, ladder_in=False, ladder_out=False, name="Cake House")
# interior x -107..-98, z -83..-74; floor 2 stands at y 73
SPONGE, FROST, CREAM = "minecraft:yellow_terracotta", "minecraft:pink_wool", "minecraft:white_wool"
for y in range(G + 1, G + 4):          # clear
    pass
for x in range(-105, -99):             # tier 1: 6x6
    for z in range(-81, -75):
        S(x, 69, z, SPONGE)
        S(x, 70, z, FROST)
for x in range(-104, -100):            # tier 2: 4x4 (up through the floor-2 hole)
    for z in range(-80, -76):
        S(x, 71, z, SPONGE)
        S(x, 72, z, FROST)
for x in range(-103, -101):            # tier 3: 2x2
    for z in range(-79, -77):
        S(x, 73, z, SPONGE)
        S(x, 74, z, CREAM)
for (x, z) in ((-103, -79), (-102, -79), (-103, -78), (-102, -78)):
    S(x, 75, z, "minecraft:pink_candle[candles=1,lit=true]" if (x + z) % 2 == 0 else "minecraft:red_candle[candles=1,lit=true]")
for x in range(-105, -99):             # white piping on tier 1 edge, strawberries on the ring
    for z in range(-81, -75):
        if x in (-105, -100) or z in (-81, -76):
            if (x + z) % 2 == 0:
                S(x, 71, z, "minecraft:red_wool")
            S(x, 70, z, CREAM if (x * 3 + z) % 3 == 0 else FROST)
for x in range(-105, -99):             # the floor-2 hole (6x6) over the cake
    for z in range(-81, -75):
        if not (-104 <= x <= -101 and -80 <= z <= -77):
            S(x, 72, z, "minecraft:air")
        S(x, 76 - 0, z, "minecraft:dark_oak_planks")
for z in range(-78, -74):              # stair run along the west wall: x -107..-106, up to floor 2
    for x in (-107, -106):
        S(x, 72, z, "minecraft:air")
for i, z in enumerate(range(-75, -79, -1)):
    for x in (-107, -106):
        S(x, 69 + i, z, "minecraft:pale_oak_stairs[facing=north,half=bottom]")
        if i:
            S(x, 68 + i, z, "minecraft:pale_oak_planks")
for (x, z) in ((-99, -82), (-98, -82), (-99, -75)):     # lanterns/candles and a table
    S(x, 69, z, "minecraft:pale_oak_fence")
    S(x, 70, z, "minecraft:white_candle[candles=3,lit=true]")
for (x, z) in ((-107, -83), (-107, -82), (-107, -81)):
    S(x, 69, z, "minecraft:bookshelf")
S(-103, 76 - 1, -82, "minecraft:lantern[hanging=true]")
S(-107, 73, -83, "minecraft:lantern[hanging=false]")
S(-100, 73, -75, "minecraft:white_candle[candles=2,lit=true]")
for (x, z) in ((-107, -82), (-106, -83)):
    S(x, 73, z, "minecraft:cobweb")

# ---- the Infested House: x -108..-98, z -67..-58, door north; a locked back room
IH = (-108, -67, -98, -58)
house(*IH, floors=1, door="N", pal=2, flat=False, furnish=False, ladder_out=False, name="Infested House")
for x in range(-107, -98):
    for z in range(-66, -58):
        for y in range(70, 72):
            if hash01(x, y, z, 31) < 0.3:
                S(x, y, z, "minecraft:cobweb")
        if hash01(x, 1, z, 32) < 0.12:
            S(x, 69, z, "minecraft:cobweb")
        if hash01(x, 2, z, 33) < 0.25:
            S(x, 68, z, "minecraft:mossy_cobblestone")
for x in range(-107, -98):             # partition at z -62 with an iron door and a barred window
    for y in range(69, 72):
        S(x, y, -62, "minecraft:mud_bricks")
S(-103, 69, -62, door_block("iron_door", "south", "lower"))
S(-103, 70, -62, door_block("iron_door", "south", "upper"))
for x in (-105, -101):
    S(x, 70, -62, "minecraft:iron_bars")
    S(x, 71, -62, "minecraft:iron_bars")
S(-103, 70, -63, "minecraft:stone_button[face=wall,facing=south,powered=false]")
S(-104, 71, -63, "minecraft:lantern[hanging=true]")
S(-103, 71, -60, "minecraft:soul_lantern[hanging=true]")
for (x, z) in ((-100, -66), (-107, -66)):
    S(x, 69, z, "minecraft:cobweb")
    S(x, 70, z, "minecraft:cobweb")
S(-107, 69, -60, "minecraft:red_bed[facing=west,part=head]")
S(-106, 69, -60, "minecraft:red_bed[facing=west,part=foot]")
S(-99, 69, -59, "minecraft:barrel[facing=up]")
S(-99, 69, -60, "minecraft:barrel[facing=up]")
S(-107, 69, -63, "minecraft:composter")         # Kat's Vermin Bin (the mod: empty the Turbomax Vacuum here)
S(-107, 70, -63, "minecraft:air")
S(-99, 69, -65, "minecraft:bookshelf")
S(-99, 70, -65, "minecraft:bookshelf")
S(-106, 69, -65, "minecraft:cauldron")
S(-106, 71, -65, "minecraft:cobweb")
S(-101, 69, -65, "minecraft:pale_oak_fence")
S(-101, 70, -65, "minecraft:pale_oak_pressure_plate")

for y in (69, 70, 71):                  # Kat's spot stays clear
    for x in (-101, -102):
        for z in (-64, -65):
            S(x, y, z, "minecraft:air")
# ---- NPC houses (normal furnishing)
house(-80, -62, -72, -54, floors=1, door="N", pal=1, flat=True, name="Alchemist")
house(-66, -64, -58, -56, floors=1, door="N", pal=0, flat=True, name="Finplex")
house(-66, -49, -58, -43, floors=1, door="N", pal=2, flat=True, name="Plumber Joe")
house(-84, -49, -76, -43, floors=1, door="N", pal=3, flat=True, name="Unhinged Kloon")
# Kloon's terminals
for x in (-83, -82, -81, -80, -79, -78, -77):
    S(x, 69, -48, "minecraft:crafter[crafting=false,orientation=north_up,triggered=false]")
    S(x, 70, -48, "minecraft:purple_stained_glass")

# ---------------------------------------------------------------- Okron's tower (Montezuma on the roof)
TX1, TZ1, TX2, TZ2 = -65, -85, -59, -79
TOWER_ROOF = 87
for x in range(TX1, TX2 + 1):
    for z in range(TZ1, TZ2 + 1):
        perim = x in (TX1, TX2) or z in (TZ1, TZ2)
        corner = x in (TX1, TX2) and z in (TZ1, TZ2)
        for y in range(G, TOWER_ROOF + 1):
            if perim:
                if corner:
                    b = "minecraft:dark_oak_log[axis=y]"
                elif y in (74, 80, TOWER_ROOF):
                    b = f"minecraft:stripped_dark_oak_log[axis={'x' if z in (TZ1, TZ2) else 'z'}]"
                elif y == G:
                    b = "minecraft:mud_bricks"
                elif y in (72, 78, 84) and (x - TX1 + z - TZ1) % 2 == 1 and not (z == TZ2 and x == -62):
                    b = "minecraft:purple_stained_glass"
                else:
                    b = "minecraft:calcite" if y < 74 else ("minecraft:pale_oak_planks" if y < 80 else "minecraft:mud_bricks")
            elif y in (G, 74, 80, TOWER_ROOF):
                b = "minecraft:pale_oak_planks" if y != TOWER_ROOF else "minecraft:dark_oak_planks"
            else:
                b = "minecraft:air"
            S(x, y, z, b)
        FOOT.add((x, z))
S(-62, 69, TZ2, door_block("pale_oak_door", "south", "lower"))
S(-62, 70, TZ2, door_block("pale_oak_door", "south", "upper"))
S(-62, 71, TZ2, "minecraft:calcite")
for y in range(69, TOWER_ROOF + 2):   # inside ladder up the north wall
    S(-62, y, TZ1 + 1, "minecraft:ladder[facing=south]")
for y in (74, 80, TOWER_ROOF):
    S(-62, y, TZ1 + 1, "minecraft:ladder[facing=south]")
for x in range(TX1, TX2 + 1):
    for z in range(TZ1, TZ2 + 1):
        if x in (TX1, TX2) or z in (TZ1, TZ2):
            S(x, TOWER_ROOF + 1, z, "minecraft:purpur_slab[type=bottom]")
for (x, z) in ((TX1, TZ1), (TX1, TZ2), (TX2, TZ1), (TX2, TZ2)):
    S(x, TOWER_ROOF + 1, z, "minecraft:purpur_pillar[axis=y]")
    S(x, TOWER_ROOF + 2, z, "minecraft:end_rod[facing=up]")
S(-62, 72, -81, "minecraft:lantern[hanging=false]")
S(-62, 69, -82, "minecraft:lectern[facing=south]")
for (x, y, z) in ((-64, 69, -84), (-60, 69, -84)):
    S(x, y, z, "minecraft:bookshelf")
    S(x, y + 1, z, "minecraft:bookshelf")
for (x, y, z) in ((-64, 75, -84), (-60, 75, -84), (-64, 81, -84), (-60, 81, -84)):
    S(x, y, z, "minecraft:bookshelf")
for y in (73, 79, 85):
    S(-62, y, -81, "minecraft:lantern[hanging=true]")
for y in range(G + 1, G + 3):
    S(-63, y, TZ2 + 1, "minecraft:pale_oak_fence") if False else None
# ladders outside the roof for the Montezuma piece are inside (above); a purpur beacon on the roof
S(-62, TOWER_ROOF + 1, -81, "minecraft:purple_candle[candles=3,lit=true]")

# ---------------------------------------------------------------- stalls
def stall(x1, z1, x2, z2, cloth, counter, extra=None, high=False):
    """An open stall facing south (towards lane A): a back wall at z1, a counter at z2, striped cloth roof."""
    top = G + (5 if high else 4)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            S(x, G, z, "minecraft:mud_bricks")
            S(x, top, z, f"minecraft:{cloth if (x - x1) % 2 == 0 else 'white'}_wool")
            for y in range(G + 1, top):
                S(x, y, z, "minecraft:air")
        S(x, G + 1, z1, "minecraft:pale_oak_planks")
        S(x, G + 2, z1, "minecraft:bookshelf" if (x - x1) % 2 else "minecraft:pale_oak_planks")
        S(x, G + 1, z2, f"minecraft:{counter}")
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        for y in range(G + 1, top):
            S(x, y, z, "minecraft:dark_oak_fence")
    S((x1 + x2) // 2, top - 1, z2 - 1, "minecraft:lantern[hanging=true]")
    if extra:
        extra(x1, z1, x2, z2)


def chef_extra(x1, z1, x2, z2):
    S(x1 + 1, G + 1, z1 + 1, "minecraft:smoker[facing=south,lit=false]")
    S(x2 - 1, G + 1, z1 + 1, "minecraft:cauldron")
    S(x1 + 2, G + 2, z2, "minecraft:white_candle[candles=2,lit=true]")


def joey_extra(x1, z1, x2, z2):
    S(x1 + 1, G + 1, z1 + 1, "minecraft:furnace[facing=south,lit=false]")
    S(x2 - 1, G + 1, z1 + 1, "minecraft:barrel[facing=up]")
    S(x1 + 2, G + 2, z2, "minecraft:red_candle[candles=2,lit=true]")


stall(-59, -77, -54, -73, "red", "barrel[facing=up]", chef_extra)               # Chef
stall(-53, -77, -49, -73, "orange", "barrel[facing=up]", joey_extra)            # Joey McPizza


def grub_extra(x1, z1, x2, z2):
    S(-93, 69, -73, "minecraft:pale_oak_planks")
    S(-93, 70, -73, "minecraft:purpur_slab[type=bottom]")          # a podium: Motes Grubber stands at y 70.5
    S(-95, 69, -76, "minecraft:barrel[facing=up]")
    S(-91, 69, -76, "minecraft:barrel[facing=up]")
    S(-95, 70, -76, "minecraft:amethyst_cluster[facing=up]")
    S(-91, 70, -76, "minecraft:amethyst_cluster[facing=up]")


stall(-96, -77, -90, -72, "purple", "purpur_slab[type=top]", grub_extra, high=True)
for x in range(-96, -89):             # re-open the air above the counter row for the taller stall
    pass

# ---------------------------------------------------------------- the square and its well; the passage below
WX, WZ = -80, -70
for dx in range(-2, 3):
    for dz in range(-2, 3):
        x, z = WX + dx, WZ + dz
        d = max(abs(dx), abs(dz))
        if d == 2:
            S(x, G, z, "minecraft:mud_bricks")
            S(x, G + 1, z, "minecraft:mud_brick_slab[type=bottom]")
        else:
            for y in range(61, G + 1):
                S(x, y, z, "minecraft:water")
            S(x, 60, z, "minecraft:prismarine_bricks")
for (dx, dz) in ((-2, -2), (-2, 2), (2, -2), (2, 2)):
    for y in range(G + 1, G + 5):
        S(WX + dx, y, WZ + dz, "minecraft:dark_oak_fence")
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if max(abs(dx), abs(dz)) == 3 and abs(dx) + abs(dz) < 6:
            S(WX + dx, G + 5, WZ + dz, "minecraft:dark_oak_slab[type=bottom]")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2:
            S(WX + dx, G + 5, WZ + dz, "minecraft:dark_oak_planks")
        elif max(abs(dx), abs(dz)) == 1:
            S(WX + dx, G + 6, WZ + dz, "minecraft:dark_oak_slab[type=bottom]")
S(WX, G + 6, WZ, "minecraft:purpur_block")
S(WX, G + 7, WZ, "minecraft:end_rod[facing=up]")
for y in range(G + 2, G + 5):
    S(WX, y, WZ, "minecraft:iron_chain[axis=y]")
# the passage: shaft bottom west, a flooded tunnel (y 62-63) to a small chamber with an air pocket
for x in range(-85, -81):                               # tunnel x -85..-82 (z -70)
    for y in (62, 63):
        S(x, y, -70, "minecraft:water")
        for dz in (-1, 1):
            S(x, y, -70 + dz, "minecraft:tuff_bricks")
    S(x, 61, -70, "minecraft:tuff_bricks")
    S(x, 64, -70, "minecraft:tuff_bricks")
S(-85, 63, -70, "minecraft:tuff_bricks")                # a low entrance (1 high) so the water stays put
S(-86, 63, -70, "minecraft:air")
for x in range(-89, -85):                               # chamber x -89..-86, z -72..-68
    for z in range(-72, -67):
        S(x, 61, z, "minecraft:polished_tuff")
        S(x, 62, z, "minecraft:water")
        for y in (63, 64, 65):
            S(x, y, z, "minecraft:air")
        S(x, 66, z, "minecraft:tuff_bricks")
S(-87, 66, -70, "minecraft:sea_lantern")
S(-89, 64, -72, "minecraft:glow_lichen[down=true]") if False else None
for (x, z) in ((-89, -72), (-89, -68), (-86, -72), (-86, -68)):
    S(x, 63, z, "minecraft:amethyst_cluster[facing=up]")
# benches and lamp posts on the square
for (x, z) in ((-83, -75), (-77, -75), (-83, -65), (-77, -65)):
    S(x, G + 1, z, "minecraft:pale_oak_stairs[facing=south,half=bottom]" if z < -70 else "minecraft:pale_oak_stairs[facing=north,half=bottom]")
for (x, z) in ((-85, -76), (-75, -76), (-85, -64), (-75, -64)):
    for y in range(G + 1, G + 4):
        S(x, y, z, "minecraft:dark_oak_fence")
    S(x, G + 4, z, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- the Dolphin Trainer
PX1, PZ1, PX2, PZ2 = -66, -117, -54, -105              # water interior
for x in range(POOL[0], POOL[2] + 1):
    for z in range(POOL[1], POOL[3] + 1):
        inside = PX1 <= x <= PX2 and PZ1 <= z <= PZ2
        if inside:
            for y in range(66, G + 1):
                S(x, y, z, "minecraft:water")
            S(x, 65, z, "minecraft:sea_lantern" if hash01(x, 1, z, 61) < 0.05 else "minecraft:prismarine_bricks")
        else:
            S(x, G, z, "minecraft:mud_bricks" if hash01(x, 2, z, 62) < 0.6 else "minecraft:packed_mud")
            S(x, G + 1, z, "minecraft:air")
            if (x in (POOL[0], POOL[2]) or z == POOL[1] or (z == POOL[3] and not (-62 <= x <= -58))) and (x + z) % 4:
                S(x, G + 1, z, "minecraft:mud_brick_wall")
for (x, z) in ((POOL[0], POOL[1]), (POOL[2], POOL[1]), (POOL[0], POOL[3]), (POOL[2], POOL[3])):
    for y in range(G + 1, G + 4):
        S(x, y, z, "minecraft:purpur_pillar[axis=y]")
    S(x, G + 4, z, "minecraft:lantern[hanging=false]")
STONES = [(-60, 69, -107, "mud_bricks"), (-63, 70, -109, "purpur_block"), (-59, 71, -111, "mud_bricks"),
          (-63, 72, -113, "purpur_block"), (-59, 73, -115, "mud_bricks")]
for i, (x, yy, z, b) in enumerate(STONES):          # parkour: stepping stones that climb
    S(x, yy, z, f"minecraft:{b}")
    if i % 2:
        S(x, yy - 1, z, "minecraft:iron_chain[axis=y]")
        S(x, yy - 2, z, "minecraft:iron_chain[axis=y]")
for x in range(-63, -60):                                  # the platform: x -63..-61? centred at -61
    for z in range(-118, -115):
        S(x - 0, 73 if False else 74 - 1, z, "minecraft:dark_oak_planks")
for x in range(-63, -60):
    for z in range(-118, -115):
        S(x, 73, z, "minecraft:dark_oak_planks")
S(-63, 74, -118, "minecraft:dark_oak_fence")
S(-61, 74, -118, "minecraft:dark_oak_fence")
S(-63, 75, -118, "minecraft:lantern[hanging=false]")
S(-61, 75, -118, "minecraft:lantern[hanging=false]")
for x in (-63, -62, -61):
    S(x, 74, -118, "minecraft:pale_oak_fence")
S(-62, 74, -118, "minecraft:purple_candle[candles=3,lit=true]")
for z in (-107, -106, -105):                               # entrance steps onto the first stone
    S(-62, G + 1, z, "minecraft:air")
# a dolphin statue on a pedestal at the start
for x in range(-58, -54):
    S(x, G + 1, -103, "minecraft:mud_bricks")
for x, y in ((-58, 70), (-57, 70), (-56, 70), (-55, 70), (-54, 71)):
    S(x, y, -103, "minecraft:light_gray_concrete" if x < -55 else "minecraft:gray_concrete")
S(-58, 71, -103, "minecraft:gray_concrete")
S(-59, 70, -103, "minecraft:gray_concrete")
S(-59, 69, -103, "minecraft:gray_concrete")
S(-56, 71, -103, "minecraft:gray_concrete")
S(-58, 70, -102, "minecraft:white_concrete")

# ---------------------------------------------------------------- the Mirrorverse door in the dead-end courtyard
for x in range(COURT[0] - 2, COURT[2] + 3):                # the courtyard's end wall at z -114 (door in it)
    for y in range(G + 1, G + 8):
        S(x, y, -114, "minecraft:dark_oak_planks" if (x + y) % 5 else "minecraft:pale_oak_planks")
for z in range(COURT[1] - 1, COURT[3] + 1):
    for y in range(G + 1, G + 8):
        for x in (COURT[0] - 1, COURT[2] + 1):
            S(x, y, z, "minecraft:calcite" if y > G + 1 else "minecraft:mud_bricks")
for x in (-126, -125, -124, -123, -122):
    for z in (-113,):
        S(x, G, z, "minecraft:polished_tuff")
for dy in range(0, 5):                                      # the ornate frame: crying obsidian, purpur, amethyst
    for x in (-126, -122):
        S(x, 69 + dy, -114, "minecraft:purpur_pillar[axis=y]")
        S(x, 69 + dy, -113, "minecraft:crying_obsidian" if dy % 2 else "minecraft:amethyst_block")
for x in range(-126, -121):
    S(x, 73, -114, "minecraft:purpur_block")
    S(x, 74, -114, "minecraft:crying_obsidian" if x % 2 else "minecraft:amethyst_block")
    S(x, 74, -113, "minecraft:purpur_slab[type=top]")
for x in (-125, -124, -123):
    for y in (69, 70, 71, 72):
        S(x, y, -114, "minecraft:purple_stained_glass")
        S(x, y, -115, "minecraft:black_concrete")
S(-124, 74, -113, "minecraft:lantern[hanging=false]")
for x in (-127, -121):
    S(x, 69, -113, "minecraft:purpur_pillar[axis=y]")
    S(x, 70, -113, "minecraft:purpur_pillar[axis=y]")
    S(x, 71, -113, "minecraft:end_rod[facing=up]")
S(-126, 69, -110, "minecraft:pale_oak_fence")
S(-126, 70, -110, "minecraft:lantern[hanging=false]")
for (x, z) in ((-123, -108),):
    S(x, 69, z, "minecraft:potted_pale_oak_sapling")

# ---------------------------------------------------------------- Gunther's race start, the lap and the gate arch
def banner_arch(x, zc, half):
    for z in (zc - half, zc + half):
        for y in range(G + 1, G + 6):
            S(x, y, z, "minecraft:pale_oak_log[axis=y]")
    for z in range(zc - half, zc + half + 1):
        S(x, G + 6, z, "minecraft:dark_oak_planks")
        S(x, G + 7, z, "minecraft:purpur_slab[type=bottom]")
    for z in range(zc - half + 1, zc + half, 2):
        S(x - 1, G + 5, z, "minecraft:purple_wall_banner[facing=west]")
        S(x + 1, G + 5, z, "minecraft:purple_wall_banner[facing=east]")
    S(x, G + 5, zc, "minecraft:lantern[hanging=true]")


banner_arch(-44, -70, 3)
for x in (-46, -45, -44, -43):                              # the checkered start line
    for z in range(-72, -67):
        S(x, G, z, "minecraft:white_concrete" if (x + z) % 2 else "minecraft:black_concrete")
for (x, z) in ((-46, -75), (-42, -75), (-46, -65), (-42, -65)):
    for y in range(G + 1, G + 4):
        S(x, y, z, "minecraft:dark_oak_fence")
    S(x, G + 4, z, "minecraft:lantern[hanging=false]")

CHECKPOINTS = [(-58, -70, "x"), (-70, -80, "z"), (-70, -98, "z"), (-84, -100, "x"), (-100, -100, "x"), (-112, -90, "z"),
               (-112, -72, "z"), (-112, -54, "z"), (-98, -52, "x"), (-60, -52, "x")]

# ---------------------------------------------------------------- pack the rest of the houses along the lanes
def rect_free(x1, z1, x2, z2, m=1):
    for x in range(x1 - m, x2 + m + 1):
        for z in range(z1 - m, z2 + m + 1):
            if (x, z) in FOOT:
                return False
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if (x, z) in lane_cells or reserved(x, z):
                return False
            if (x, z) not in LAND or edge(x, z) < 3:
                return False
    return True


def pack(name):
    pts, w = LANES[name]
    (ax, az), (bx, bz) = pts[0], pts[-1]
    along_x = az == bz
    lo, hi = (min(ax, bx), max(ax, bx)) if along_x else (min(az, bz), max(az, bz))
    c = az if along_x else ax
    for side in (-1, 1):
        t = lo + 2
        while t < hi - 5:
            width = R.randint(6, 11)
            floors = 2 if R.random() < 0.35 else 1
            placed = False
            for depth in (9, 8, 7, 6):
                r = 1 if floors == 2 else R.randint(0, 1)
                off = 2 + r + (w // 2 - 1)
                if along_x:
                    if side < 0:
                        z2_ = c - off
                        z1_ = z2_ - depth + 1
                        door = "S"
                    else:
                        z1_ = c + off
                        z2_ = z1_ + depth - 1
                        door = "N"
                    rx1, rx2, rz1, rz2 = t, t + width - 1, z1_, z2_
                else:
                    if side < 0:
                        x2_ = c - off
                        x1_ = x2_ - depth + 1
                        door = "E"
                    else:
                        x1_ = c + off
                        x2_ = x1_ + depth - 1
                        door = "W"
                    rx1, rx2, rz1, rz2 = x1_, x2_, t, t + width - 1
                fx1, fz1, fx2, fz2 = rx1, rz1, rx2, rz2
                if floors == 2:                      # the overhang
                    if door == "N":
                        fz1 -= 1
                    elif door == "S":
                        fz2 += 1
                    elif door == "W":
                        fx1 -= 1
                    else:
                        fx2 += 1
                if rect_free(fx1, fz1, fx2, fz2):
                    house(rx1, rz1, rx2, rz2, floors=floors, door=door, pal=R.randint(0, 3), door_i=None)
                    placed = True
                    break
            t += (width + R.randint(1, 3)) if placed else 2


for lane in ("A", "A2", "B", "C", "D", "E", "F", "G", "J", "K", "H", "I1", "I2"):
    pack(lane)


def fill_pass(tries=2500):
    """Second row: houses off the lanes, joined to the nearest lane by a short side path."""
    for _ in range(tries):
        x1 = R.randint(X0 + 4, X1 - 12)
        z1 = R.randint(Z0 + 3, -42 - 6)
        width, depth = R.randint(6, 10), R.randint(6, 9)
        x2, z2 = x1 + width - 1, z1 + depth - 1
        floors = 2 if R.random() < 0.3 else 1
        door = R.choice("NSEW")
        fx1, fz1, fx2, fz2 = x1, z1, x2, z2
        if floors == 2:
            if door == "N":
                fz1 -= 1
            elif door == "S":
                fz2 += 1
            elif door == "W":
                fx1 -= 1
            else:
                fx2 += 1
        if not rect_free(fx1, fz1, fx2, fz2):
            continue
        dv = DIRV[door]
        if door in "NS":
            dxx = x1 + width // 2
            dzz = z1 if door == "N" else z2
        else:
            dzz = z1 + depth // 2
            dxx = x1 if door == "W" else x2
        path = []
        hit = False
        for k in range(1, 8):
            c = (dxx + dv[0] * k, dzz + dv[1] * k)
            if c in FOOT or reserved(*c) or c not in LAND:
                break
            if c in lane_cells:
                hit = True
                break
            path.append(c)
        if not hit or len(path) > 5:
            continue
        for c in path:
            S(c[0], G, c[1], LANE_SURF(c[0], G, c[1]))
            lane_cells.setdefault(c, "side")
        house(x1, z1, x2, z2, floors=floors, door=door, pal=R.randint(0, 3))


fill_pass()
print("houses:", len(HOUSES), "flat roofs:", len(ROOFS))

# ---------------------------------------------------------------- roof bridges
BRIDGES = []
usage = {}
roofs = sorted(ROOFS)
for i, a in enumerate(roofs):
    cands = []
    for j, b in enumerate(roofs):
        if i == j or a[4] != b[4]:
            continue
        ox1, ox2 = max(a[0], b[0]), min(a[2], b[2])
        oz1, oz2 = max(a[1], b[1]), min(a[3], b[3])
        if ox2 - ox1 >= 2 and (b[1] - a[3] > 2 or a[1] - b[3] > 2):
            gap = (b[1] - a[3] - 1) if b[1] > a[3] else (a[1] - b[3] - 1)
            cands.append((gap, j, "z", (ox1 + ox2) // 2))
        elif oz2 - oz1 >= 2 and (b[0] - a[2] > 2 or a[0] - b[2] > 2):
            gap = (b[0] - a[2] - 1) if b[0] > a[2] else (a[0] - b[2] - 1)
            cands.append((gap, j, "x", (oz1 + oz2) // 2))
    cands.sort()
    for gap, j, ax_, mid in cands:
        if gap > 9 or usage.get(i, 0) >= 2 or usage.get(j, 0) >= 2 or (min(i, j), max(i, j)) in {(min(p), max(p)) for p in [(q[0], q[1]) for q in BRIDGES]}:
            continue
        b = roofs[j]
        y = a[4]
        if ax_ == "z":
            za, zb = (a[3] + 1, b[1] - 1) if b[1] > a[3] else (b[3] + 1, a[1] - 1)
            cells = [(mid, z) for z in range(za, zb + 1)]
        else:
            xa, xb = (a[2] + 1, b[0] - 1) if b[0] > a[2] else (b[2] + 1, a[0] - 1)
            cells = [(x, mid) for x in range(xa, xb + 1)]
        if any((c in FOOT) or area.get(c[0], y + 1, c[1]) not in (None, "minecraft:air") for c in cells):
            continue
        if any(area.get(c[0], y, c[1]) not in (None, "minecraft:air") for c in cells):
            continue
        usage[i] = usage.get(i, 0) + 1
        usage[j] = usage.get(j, 0) + 1
        for k, (x, z) in enumerate(cells):
            hole = len(cells) >= 6 and k == len(cells) // 2 and hash01(x, 0, z, 71) < 0.4
            if not hole:
                S(x, y, z, "minecraft:dark_oak_planks" if k % 2 else "minecraft:pale_oak_planks")
            # rope rail on one side
            sx, sz = (1, 0) if ax_ == "z" else (0, 1)
            if k % 2 == 0:
                S(x + sx, y, z + sz, "minecraft:iron_chain[axis=y]") if False else None
                S(x + sx, y + 1, z + sz, "minecraft:pale_oak_fence")
            else:
                S(x + sx, y + 1, z + sz, "minecraft:iron_chain[axis=%s]" % ("z" if ax_ == "z" else "x"))
        # the overhead rope under the planks (hanging chains)
        for (x, z) in cells[1::3]:
            S(x, y - 1, z, "minecraft:iron_chain[axis=y]")
        BRIDGES.append((i, j, len(cells), cells, y))
print("roof bridges:", len(BRIDGES), "roofs", len(roofs))

# ---------------------------------------------------------------- the lagoon bridge, the gate arch, lamps, checkpoints
for z in range(-37, Z1 + 1):
    for x in (-91, -90, -89):
        S(x, G, z, "minecraft:dark_oak_planks" if (z % 2) else "minecraft:pale_oak_planks")
        S(x, G - 1, z, "minecraft:air")
        for y in range(G + 1, G + 5):
            S(x, y, z, "minecraft:air")
    for x in (-92, -88):
        S(x, G, z, "minecraft:stripped_dark_oak_log[axis=z]")
        S(x, G + 1, z, "minecraft:dark_oak_fence")
        S(x, G + 2, z, "minecraft:iron_chain[axis=z]")
    if z % 3 == 0:
        for x in (-92, -88):
            S(x, G + 1, z, "minecraft:stripped_dark_oak_log[axis=y]")
            S(x, G + 2, z, "minecraft:stripped_dark_oak_log[axis=y]")
            S(x, G + 3, z, "minecraft:lantern[hanging=false]")
            for c in range(1, 4):
                S(x, G - c, z, "minecraft:iron_chain[axis=y]")
for z in range(-36, Z1 + 1):
    S(-90, G - 1, z, "minecraft:iron_chain[axis=y]") if z % 2 else None
# the gate arch where the bridge lands
for x in (-94, -93, -87, -86):
    for y in range(G + 1, G + 8):
        S(x, y, -39, "minecraft:purpur_pillar[axis=y]" if x in (-93, -87) else "minecraft:dark_oak_log[axis=y]")
for x in range(-95, -84):
    S(x, G + 8, -39, "minecraft:dark_oak_planks")
    S(x, G + 9, -39, "minecraft:purpur_slab[type=bottom]")
for x in (-92, -88):
    S(x, G + 7, -39, "minecraft:dark_oak_stairs[facing=east,half=top]" if x == -92 else "minecraft:dark_oak_stairs[facing=west,half=top]")
for x in (-91, -90, -89):
    S(x, G + 7, -39, "minecraft:iron_chain[axis=y]" if x != -90 else "minecraft:lantern[hanging=true]")
S(-90, G + 8, -39, "minecraft:purpur_block")
S(-90, G + 9, -39, "minecraft:end_rod[facing=up]")
for (x, z) in ((-94, -40), (-86, -40)):
    S(x, G + 1, z, "minecraft:purple_wall_banner[facing=north]") if False else None
for x in (-94, -86):
    for y in (G + 5, G + 6):
        S(x, y, -40, f"minecraft:purple_wall_banner[facing=north]") if y == G + 5 else None

# street lamps along the lanes
lamp_taken = set()
for (x, z), nm in sorted(lane_cells.items()):
    if nm in ("A2",) or (x + z) % 9 or (x, z) not in LAND:
        continue
    for dx, dz in ((0, 2), (0, -2), (2, 0), (-2, 0)):
        px, pz = x + dx, z + dz
        if (px, pz) in lane_cells or (px, pz) not in LAND or (px, pz) in FOOT or reserved(px, pz):
            continue
        if area.get(px, G + 1, pz) not in (None, "minecraft:air") or any((px + i, pz + j) in lamp_taken for i in range(-4, 5) for j in range(-4, 5)):
            continue
        if area.get(px, G, pz) in (None,):
            continue
        for y in range(G + 1, G + 4):
            S(px, y, pz, "minecraft:dark_oak_fence")
        S(px, G + 4, pz, "minecraft:lantern[hanging=false]" if hash01(px, 0, pz, 9) < 0.7 else "minecraft:soul_lantern[hanging=false]")
        lamp_taken.add((px, pz))
        break


def checkpoint_arch(n, x, z, axis):
    posts = ((x, z - 2), (x, z + 2)) if axis == "x" else ((x - 2, z), (x + 2, z))
    for (px, pz) in posts:
        if area.get(px, G + 1, pz) not in (None, "minecraft:air") or (px, pz) not in LAND:
            continue
        for y in range(G + 1, G + 5):
            S(px, y, pz, "minecraft:purpur_pillar[axis=y]")
        S(px, G + 5, pz, "minecraft:end_rod[facing=up]")
    S(x, G + 5, z, "minecraft:air")
    for d in (-1, 0, 1):
        px, pz = (x, z + d) if axis == "x" else (x + d, z)
        if area.get(px, G + 4, pz) in (None, "minecraft:air"):
            S(px, G + 4, pz, "minecraft:purpur_slab[type=bottom]")
    for d in (-1, 1):
        px, pz = (x, z + d) if axis == "x" else (x + d, z)
        if area.get(px, G + 3, pz) in (None, "minecraft:air") and d == 1:
            S(px, G + 3, pz, "minecraft:lantern[hanging=true]")
    S(x, G, z, "minecraft:purple_concrete") if False else None


for n, (x, z, ax_) in enumerate(CHECKPOINTS, 1):
    checkpoint_arch(n, x, z, ax_)

# ---------------------------------------------------------------- trees, plants
occ_near = set()
for (x, z) in FOOT:
    for i in range(-2, 3):
        for j in range(-2, 3):
            occ_near.add((x + i, z + j))
trees = 0
for (x, z) in sorted(LAND):
    if hash01(x, 55, z, 33) > 0.02 or (x, z) in occ_near or (x, z) in lane_cells or reserved(x, z, 2) or edge(x, z) < 3:
        continue
    if area.get(x, G, z) not in ("minecraft:grass_block", "minecraft:rooted_dirt", "minecraft:coarse_dirt", "minecraft:moss_block", "minecraft:mud"):
        continue
    if any((x + i, z + j) in lane_cells for i in range(-2, 3) for j in range(-2, 3)):
        continue
    area.oak(x, G + 1, z, height=4 + int(hash01(x, 1, z, 3) * 3), radius=2, log="dark_oak_log", leaves="dark_oak_leaves", seed=x * 7 + z)
    trees += 1
print("trees:", trees)
area.scatter(LAND, 0.16, lambda x, z: "minecraft:short_grass" if hash01(x, 3, z, 3) < 0.55 else (
    "minecraft:fern" if hash01(x, 3, z, 3) < 0.7 else "minecraft:pink_petals[flower_amount=2,facing=north]"), seed=4,
    avoid=lambda x, z: (x, z) in lane_cells or reserved(x, z))

# ---------------------------------------------------------------- spots, mobs, zone
NPCS = {
    "Gunther": (-45, 69, -74, 90.0, "by the race start line, east of the banner arch"),
    "Chef": (-57, 69, -75, 180.0, "in his hot dog stall, behind the counter"),
    "Joey McPizza": (-51, 69, -75, 180.0, "in his pizza stall, next to the Chef's"),
    "Unhinged Kloon": (-80, 69, -45, 180.0, "inside his terminal house south of lane D"),
    "Kat (Rift)": (-101, 69, -64, 90.0, "in the Infested House's front room (the locked room is behind her)"),
    "Sorcerer Okron": (-62, 69, -81, 180.0, "on the ground floor of the tall tower"),
    "Alchemist (Rift)": (-76, 69, -57, 180.0, "inside the house south of the square"),
    "Finplex": (-62, 69, -59, 180.0, "inside his house by lane B"),
    "Plumber Joe": (-62, 69, -45, 180.0, "inside his house south of lane D"),
    "Dr. Emmett": (-126, 69, -109, -90.0, "in the courtyard by the Mirrorverse door"),
}
for nm, (x, y, z, yaw, note) in NPCS.items():
    area.npc(nm, x, y, z, yaw, note)
area.npc("Motes Grubber (West Village)", -93, 70.5, -73, 0.0, "on a podium at his stall, west of the square (y 70.5)")
area.npc("spawn:race_start", -46, 69, -70, -90.0, "the race start line (banner arch at x -44)")
for n, (x, z, ax_) in enumerate(CHECKPOINTS, 1):
    area.npc(f"spawn:race_{n}", x, 69, z, 0.0, f"lap checkpoint {n}")
area.npc("spawn:mirrorverse_door", -124, 69, -111, 180.0, "in front of the ornate door frame at the north end of the courtyard")
area.npc("spawn:montezuma_4", -63, 88, -82, 0.0, "roof of Okron's tower (x -62.5, z -81.5)")
area.npc("enigma:Hot Dog Contest", -60, 69, -75, 0.0, "beside the Chef's stall")
area.npc("enigma:Infested House", -105, 69, -60, 0.0, "in the locked back room of the Infested House")
area.npc("spawn:vermin_bin", -107, 69, -63, 0.0, "Kat's Vermin Bin (a composter) in the Infested House's front room")
area.npc("spawn:infested_house", -103, 69, -65, 0.0, "the middle of the Infested House's front room (vermin crawl x -107..-99, z -66..-63)")
area.npc("spawn:kat_door", -103, 69, -63, 180.0, "in front of the locked iron door (its button is above)")
area.npc("spawn:kat_room", -103, 69, -60, 0.0, "inside the locked back room")
area.npc("enigma:Cake House 1", -98, 69, -83, 0.0, "behind the cake on the ground floor (north-east corner)")
area.npc("enigma:Cake House 2", -99, 73, -82, 0.0, "on the upper catwalk of the Cake House (north-east corner)")
area.npc("enigma:Dolphin Trainer", -62, 74, -117, 0.0, "the platform at the end of the pool parkour")
area.npc("enigma:Village Puzzle", -87, 62, -70, 0.0, "air pocket in the flooded chamber under the square: dive into the well, swim west")
# Between Rooftops: the middle of the longest plank bridge between roofs
best = None
for (i, j, n, cells, y) in BRIDGES:
    mx = sum(c[0] for c in cells) / n
    mz = sum(c[1] for c in cells) / n
    score = (n >= 4, n - 0.02 * math.hypot(mx + 85, mz + 85))
    if best is None or score > best[0]:
        best = (score, cells[len(cells) // 2], y)
if best:
    (bx, bz), by = best[1], best[2]
    area.npc("enigma:Between Rooftops", bx, by + 1, bz, 0.0, "on a plank bridge between two rooftops")
else:
    print("WARNING: no roof bridge for Between Rooftops")


def mob(kind, name, level, health, damage, xp, coins, mx, every, a, b, drops=None):
    """A Rift mob: `coins` are the Motes it drops (the mod pays Motes for kills in the Rift)."""
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


SHADOW = [drop("SHADOW_CRUX", 1, 1, 1)]
area.zone("West Village", box=((-140, 0, -120), (-31, 200, -32)), mobs=[
    mob("minecraft:zombie", "Shadow", 3, 125, 6, 6, 150, 4, 6, (-135, 69, -106), (-113, 76, -86), SHADOW),
    mob("minecraft:zombie", "Shadow", 3, 125, 6, 6, 150, 4, 6, (-135, 69, -84), (-113, 76, -64), SHADOW),
    mob("minecraft:zombie", "Shadow", 3, 125, 6, 6, 150, 3, 6, (-135, 69, -60), (-113, 76, -44), SHADOW),
])
area.spots["notes"] += [
    "Lagoon bridge: planks x -91..-89, z -37..-32 (deck y 68); the land's south edge is at z -36 there; the Lagoon's land at about z -30 leaves a 1-block gap beyond the box edge z -32",
    "Gate arch at (-94..-86, z -39) where the bridge lands; village lanes: A (z -70, E-W), B (x -70, N-S, to the Dreadfarm), C (x -90, to the bridge), D (z -52), E (x -112), F (z -100), G (x -50), H (x -124, to the courtyard)",
    "Cake House x -108..-97, z -84..-73 (two floors, cake in the atrium, stairs on the west wall); Infested House x -108..-98, z -67..-58 (iron door at (-103, 69, -62))",
    "Dolphin Trainer pool x -67..-53, z -118..-104; parkour stones from (-60, 70, -107) to the platform (-62, 74, -117)",
    "Well at (-80, 70); the flooded passage runs west under the square at y 62-63 to a chamber x -89..-86, z -72..-68",
    "Okron's tower x -65..-59, z -85..-79: inside ladder up to the roof (y 88)",
    f"Roof bridges: {len(BRIDGES)}; houses: {len(HOUSES)}",
]
area.save()
print("ground check: plaza seam", area.surface_y(-33, -70), "dreadfarm seam", area.surface_y(-70, -118), "bridge land", area.surface_y(-90, -37))
