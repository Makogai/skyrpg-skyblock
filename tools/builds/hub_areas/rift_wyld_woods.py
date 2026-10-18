#!/usr/bin/env python3
"""The Rift: the Wyld Woods (x -160..-11, z 60..199) and the Rift Wizard Tower (the arrival).

Giant interconnected trees with rope-and-plank walkways, treehouses, slime launch tubes, the Broken Cage, Enigma's Crib,
the Pumpgrotto, the Shifted Tavern, The Bastion, Inverted Sirius's shack and, on the east side, the tall violet-and-gold
Rift Wizard Tower where players arrive. The outer edges break off into the void as jagged cliffs with a hanging underside.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_wyld_woods.py
"""
import math

from kit import hash01, noise
from rift_lib import new_area, GROUND, SEAM, CROSSINGS, ARRIVAL, LAND_BOTTOM

area = new_area("wyld_woods", version=1)
S, F = area.set, area.fill
X0, X1, Z0, Z1 = -160, -11, 60, 199
RING = 88                    # the walkway level (deck block y; players walk at 89)
TCX, TCZ = -46, 110          # the Wizard Tower's centre (the arrival is its centre cell)
AIR = "minecraft:air"


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def seg_dist(px, pz, a, b):
    (x1, z1), (x2, z2) = a, b
    dx, dz = x2 - x1, z2 - z1
    L2 = dx * dx + dz * dz
    t = 0 if L2 == 0 else max(0, min(1, ((px - x1) * dx + (pz - z1) * dz) / L2))
    return math.hypot(px - (x1 + t * dx), pz - (z1 + t * dz))


def empty(x, y, z):
    return area.get(x, y, z) is None


def free(x, y, z):
    return area.get(x, y, z) in (None, AIR)


def lamp(x, y, z):
    """A purple lantern: a sea lantern behind purple stained glass."""
    S(x, y, z, "sea_lantern")
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
        if empty(x + dx, y + dy, z + dz):
            S(x + dx, y + dy, z + dz, "purple_stained_glass")


# ------------------------------------------------------------------------------------------------ layout
MAIN = [(-57, 110), (-68, 106), (-76, 92), (-92, 82), (-105, 68), (-105, 60)]   # tower -> the Black Lagoon crossing
assert CROSSINGS[("wyld_woods", "black_lagoon")] == ("z", 60, -105)
SIDE = {
    "tavern": [(-70, 105), (-76, 112)],
    "crib": [(-64, 108), (-62, 124), (-68, 130), (-78, 142), (-84, 158), (-92, 156)],
    "lake": [(-92, 156), (-100, 163)],
    "bastion": [(-84, 158), (-82, 168), (-76, 175)],
    "west": [(-105, 86), (-124, 92), (-136, 92), (-145, 100), (-146, 114), (-146, 130), (-141, 142)],
    "sirius": [(-136, 92), (-136, 96)],
    "bark": [(-141, 142), (-125, 146), (-115, 146)],
    "flowers": [(-106, 71), (-117, 74), (-128, 77)],
    "telkar": [(-105, 64), (-113, 63)],
}
# (centre x, z, radius): ground kept level at y 68 for the buildings.
FLATS = [(TCX, TCZ, 15), (-76, 119, 9), (-68, 134, 11), (-82, 163, 7), (-136, 102, 7), (-141, 146, 8), (-72, 182, 12),
         (-128, 77, 5), (-101, 88, 6), (-106, 124, 5), (-39, 158, 4), (-62, 152, 4), (-102, 176, 16), (-113, 63, 5),
         (-94, 112, 3), (-92, 157, 5), (-125, 153, 8)]
LAKE = (-102, 176)
LAKE_R = 12.5
OUTER_INSET_SEED = 11

# name: (cx, cz, trunk radius, height above the ground, wood)
TREES = {
    "t1": (-138, 80, 3.5, 40, "mangrove"),
    "t2": (-118, 102, 4, 46, "pale_oak"),
    "t3": (-88, 100, 4, 44, "dark_oak"),
    "t4": (-64, 84, 3, 38, "mangrove"),
    "t5": (-135, 127, 4.5, 48, "dark_oak"),
    "t6": (-96, 150, 3, 42, "pale_oak"),
    "t7": (-72, 148, 4, 45, "mangrove"),
    "t8": (-48, 166, 4, 44, "dark_oak"),
    "t9": (-30, 82, 3, 40, "pale_oak"),
    "t10": (-28, 140, 4, 42, "mangrove"),
    "t11": (-138, 168, 4, 44, "pale_oak"),
    "t12": (-128, 181, 3, 40, "dark_oak"),
    "t13": (-115, 138, 4.5, 46, "dark_oak"),    # the big trunk of "Tough Bark" (9 wide)
}
LINKS = [("t1", "t2"), ("t2", "t3"), ("t3", "t4"), ("t4", "t9"), ("t2", "t5"), ("t5", "t13"), ("t13", "t6"), ("t6", "t7"),
         ("t7", "t8"), ("t5", "t11"), ("t11", "t12"), ("t8", "t10"), ("t13", "t11")]
TOWER_LINKS = [("t4", None), ("t10", None)]      # walkways to the tower's balcony
HOUSES = {"t2": "Argofay Trafficker 1", "t5": "Argofay Trafficker 2", "t7": "Argofay Trafficker 3", "t11": "Argofay Trafficker 4"}
RING_H = {k: int(v[2] + 6.5) for k, v in TREES.items()}


def hw(t):
    return int(TREES[t][2])


# ------------------------------------------------------------------------------------------------ terrain
def edge_dists(x, z):
    d = [(x - X0, z, 0), (X1 - x, z, 1), (Z1 - z, x, 2)]
    if x >= -60:
        d.append((z - Z0, x, 3))
    return d


def land_e(x, z):
    """Distance in from the jagged cliff edge (negative: void)."""
    e = 1e9
    for dist, p, i in edge_dists(x, z):
        inset = 2 + 6 * (0.5 + 0.5 * noise(p, i * 37, OUTER_INSET_SEED, 1.8)) + 2.5 * hash01(p, i, 3)
        e = min(e, dist - inset)
    return e


def flat_w(x, z):
    w = 0.0
    if x <= -61:
        zz = z - Z0
        w = 1.0 if zz <= SEAM + 2 else smooth(1 - (zz - SEAM - 2) / 8)
    for pts, half in [(MAIN, 3.5)] + [(p, 2.5) for p in SIDE.values()]:
        for a, b in zip(pts, pts[1:]):
            d = seg_dist(x, z, a, b)
            w = max(w, 1.0 if d <= half else smooth(1 - (d - half) / 7))
    for cx, cz, r in FLATS:
        d = math.hypot(x - cx, z - cz)
        w = max(w, 1.0 if d <= r else smooth(1 - (d - r) / 6))
    return w


SURF = area.mix({"grass_block": 10, "moss_block": 3, "podzol": 3, "rooted_dirt": 2, "coarse_dirt": 1, "mud": 1})
UNDER = area.mix({"dirt": 6, "rooted_dirt": 2, "mud": 1, "coarse_dirt": 1})
BODY = area.mix({"tuff": 5, "deepslate": 3, "andesite": 2, "dirt": 1})
UNDERSIDE = area.mix({"tuff": 8, "deepslate": 7, "crying_obsidian": 1, "purpur_block": 1, "amethyst_block": 1,
                      "calcite": 1, "tuff_bricks": 1})
cols = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = land_e(x, z)
        if e < 0:
            continue
        raw = 68 + 2.5 * noise(x, z, 2, 1.1) + 1.5 * noise(x, z, 6, 2.7)
        w = flat_w(x, z)
        top = int(round(68 + (raw - 68) * (1 - w)))
        top = max(65, min(72, top))
        dl = math.hypot(x - LAKE[0], z - LAKE[1])
        if dl <= LAKE_R + 1.5:
            top = 61 + int(round(7 * smooth((dl - 10) / 3.5)))
        elif e < 3:
            top = max(64, top - int(hash01(x, 9, z, 2) * 3))
        cols[(x, z)] = (top, e)

for (x, z), (top, e) in cols.items():
    lake = math.hypot(x - LAKE[0], z - LAKE[1]) <= LAKE_R + 1.5
    area.set(x, top, z, "mud" if lake and top < 67 else SURF(x, top, z))
    for y in range(top - 1, top - 4, -1):
        area.set(x, y, z, UNDER(x, y, z))
    for y in range(top - 4, LAND_BOTTOM - 1, -1):
        area.set(x, y, z, BODY(x, y, z))
    # the hanging underside: thick inland, thin at the edge, with the odd spike
    depth = int(30 * smooth(e / 28.0) + 3 * noise(x, z, 13, 1.4))
    if hash01(x, 5, z, 4) < 0.07 and e < 26:
        depth += 4 + int(hash01(x, 6, z, 4) * 10)
    bottom = max(8, LAND_BOTTOM - 1 - depth)
    for y in range(LAND_BOTTOM - 1, bottom - 1, -1):
        area.set(x, y, z, UNDERSIDE(x, y, z))
    if depth > 4 and hash01(x, 7, z, 5) < 0.04:
        area.set(x, bottom - 1, z, "amethyst_cluster[facing=down]")
    area.top[(x, z)] = top


def T(x, z):
    return area.top.get((x, z), 68)


# Pumpgrotto: the lake and the grotto beneath it ------------------------------------------------------------
LX, LZ = LAKE
for (x, z), (top, e) in cols.items():
    d = math.hypot(x - LX, z - LZ)
    if d <= LAKE_R and top < 67:
        for y in range(top + 1, 68):
            S(x, y, z, "water")
        if hash01(x, 3, z, 8) < 0.05:
            S(x, 68, z, "lily_pad")
    elif LAKE_R - 1.5 < d <= LAKE_R + 1.5 and hash01(x, 4, z, 8) < 0.12:
        S(x, T(x, z) + 1, z, "muddy_mangrove_roots")
GX0, GX1, GZ0, GZ1 = LX - 23, LX - 11, LZ - 6, LZ + 6          # the grotto room under the western bank
for x in range(GX0, GX1 + 1):
    for z in range(GZ0, GZ1 + 1):
        if math.hypot(x - (GX0 + GX1) / 2, z - LZ) > 7.2:
            continue
        for y in range(52, 59):
            S(x, y, z, "water" if y <= 55 else AIR)
        S(x, 51, z, "mossy_cobblestone" if hash01(x, 1, z, 9) < 0.4 else "deepslate_tiles")
        S(x, 59, z, "sea_lantern" if hash01(x, 2, z, 9) < 0.07 else ("glow_lichen[down=true]" if False else "mossy_stone_bricks"))
        if hash01(x, 8, z, 9) < 0.08:
            S(x, 58, z, "glow_lichen[up=true]")
        if hash01(x, 9, z, 9) < 0.07:
            S(x, 52, z, "amethyst_cluster[facing=up]")
GCX = (GX0 + GX1) // 2
for dx in range(-1, 2):
    for dz in range(-1, 2):
        S(GCX + dx, 55, LZ + dz, "mossy_stone_bricks")        # the island in the pocket
        for y in (56, 57, 58):
            S(GCX + dx, y, LZ + dz, AIR)
for x in range(GX1 - 1, LX - 4):                                 # tunnel to the lake
    for z in range(LZ - 1, LZ + 2):
        for y in range(52, 55):
            S(x, y, z, "water")
        S(x, 51, z, "deepslate_tiles")
        S(x, 55, z, "tuff_bricks")
for dx in range(-1, 2):                                          # the shaft down from the lake floor
    for dz in range(-1, 2):
        for y in range(52, 62):
            S(LX - 7 + dx, y, LZ + dz, "water")
SOUL_PRESSURIZED = (GCX, 56, LZ)

# Enigma's Crib: a small grove with a pool near the tower ----------------------------------------------------
CRX, CRZ = -68, 134
for dx in range(-4, 5):
    for dz in range(-4, 5):
        d = math.hypot(dx, dz)
        x, z = CRX + dx, CRZ + dz
        if d <= 2.9:
            S(x, 66, z, "mud")
            S(x, 67, z, "water")
            S(x, 68, z, AIR)
            if hash01(x, 1, z, 3) < 0.12:
                S(x, 68, z, "lily_pad")
        elif d <= 4.4:
            S(x, 68, z, "calcite" if hash01(x, 2, z, 3) < 0.5 else "tuff_bricks")
            if hash01(x, 3, z, 3) < 0.25:
                S(x, 69, z, "amethyst_cluster[facing=up]")
for k in range(6):                                               # standing stones around the pool
    a = k * math.pi / 3 + 0.4
    sx, sz = CRX + int(round(7 * math.cos(a))), CRZ + int(round(7 * math.sin(a)))
    for y in range(69, 69 + 3 + k % 2):
        S(sx, y, sz, "purpur_pillar" if y < 71 else "amethyst_block")
    lamp(sx, 69 + 4 + k % 2, sz)
    S(sx, 69, sz + 1, "glow_lichen[down=true]")
S(CRX, 69, CRZ + 7, "end_rod[facing=up]")
SOUL_NEXT = (CRX, 69, CRZ - 4)

# ------------------------------------------------------------------------------------------------ paths
path_cells = set()
PATHB = area.mix({"coarse_dirt": 5, "rooted_dirt": 3, "mud_bricks": 2, "packed_mud": 1})
for pts, wdt in [(MAIN, 5)] + [(p, 3) for p in SIDE.values()]:
    path_cells |= area.path(pts, width=wdt, blocks=PATHB, seed=3)
for (x, z) in list(path_cells):                      # the Black Lagoon crossing: rooted dirt / mud bricks at y 68 within the seam
    pass
posts = 0
for a, b in zip(MAIN, MAIN[1:]):
    L = math.hypot(b[0] - a[0], b[1] - a[1])
    n = int(L // 9)
    for i in range(1, n + 1) if n else []:
        t = i / (n + 1)
        px, pz = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        nx, nz = -(b[1] - a[1]) / L, (b[0] - a[0]) / L
        x, z = int(round(px + nx * 3.5)), int(round(pz + nz * 3.5))
        if (x, z) in area.top and (x, z) not in path_cells:
            y = T(x, z)
            for h in range(1, 4):
                S(x, y + h, z, "dark_oak_fence")
            lamp(x, y + 4, z)
            posts += 1

# ------------------------------------------------------------------------------------------------ the Rift Wizard Tower
FLOORS = [68, 78, 88, 98, 108]


def tower():
    cx, cz = TCX, TCZ
    for dx in range(-12, 13):
        for dz in range(-12, 13):
            d = math.hypot(dx, dz)
            x, z = cx + dx, cz + dz
            if d <= 10.8:                                # foundation
                for y in range(58, 68):
                    S(x, y, z, "deepslate_bricks" if d > 9.4 or y < 66 else "deepslate_tiles")
            if d <= 8.2:                                 # ground floor
                if d <= 1.6:
                    blk = "amethyst_block"
                elif d <= 2.6:
                    blk = "gold_block"
                elif d <= 7.2:
                    blk = "polished_deepslate" if (dx + dz) % 2 == 0 else "purpur_block"
                else:
                    blk = "gold_block" if int(d * 3) % 2 == 0 else "crying_obsidian"
                S(x, 68, z, blk)
                for y in range(69, 118):
                    S(x, y, z, AIR)
            if 8.2 < d <= 10.8:
                S(x, 68, z, "polished_deepslate")
    # walls
    for y in range(69, 118):
        k = (y - 68) % 10
        fl = (y - 68) // 10
        for dx in range(-10, 11):
            for dz in range(-10, 11):
                d = math.hypot(dx, dz)
                if not (8.2 < d <= 9.3):
                    continue
                ang = math.degrees(math.atan2(dz, dx)) % 360
                m = ang % 45
                near = min(m, 45 - m)                    # degrees from a compass/diagonal axis
                x, z = cx + dx, cz + dz
                if k == 0:
                    blk = "gold_block"
                elif near < 4.2 and k not in (9,):
                    blk = "purpur_pillar"
                elif k == 9:
                    blk = "crying_obsidian" if hash01(x, y, z, 1) < 0.5 else "deepslate_tiles"
                else:
                    r = hash01(x, y, z, 2)
                    blk = "purpur_block" if r < 0.42 else "deepslate_tiles" if r < 0.8 else "amethyst_block" if r < 0.9 else "cracked_deepslate_tiles"
                # windows: compass slits on floors 0,2,4; diagonal slits on 1,3
                diag = int(round(ang / 45.0)) % 2 == 1
                if 3 <= k <= 6 and near < 3.0 and (diag == (fl % 2 == 1)):
                    blk = "purple_stained_glass_pane"
                if k in (2, 7) and near < 3.0 and (diag == (fl % 2 == 1)):
                    blk = "gold_block"
                S(x, y, z, blk)
    # openings: the west door (ground floor), the east balcony door (floor 3)
    for dz in (-1, 0, 1):
        for y in range(69, 73):
            for dx in range(-11, -7):
                S(cx + dx, y, cz + dz, AIR)
        for y in range(89, 93):
            for dx in range(8, 11):
                S(cx + dx, y, cz + dz, AIR)
    for dz in (-2, 2):
        for y in range(69, 74):
            S(cx - 9, y, cz + dz, "gold_block" if y % 2 else "purpur_pillar")
        S(cx - 9, 73, cz + dz, "gold_block")
    for dz in (-1, 0, 1):
        S(cx - 9, 73, cz + dz, "gold_block")
        S(cx - 10, 73, cz + dz, "crying_obsidian")
    for dx in range(-14, -10):                           # the doorstep
        for dz in range(-2, 3):
            S(cx + dx, 68, cz + dz, "purpur_block" if (dx + dz) % 2 else "polished_deepslate")
    # floors
    for f in range(1, 5):
        y = FLOORS[f]
        for dx in range(-9, 10):
            for dz in range(-9, 10):
                d = math.hypot(dx, dz)
                if d <= 8.2:
                    S(cx + dx, y, cz + dz, "gold_block" if 7.0 < d else ("purpur_block" if (dx + dz) % 2 == 0 else "polished_deepslate"))
                    for h in range(1, 10):
                        S(cx + dx, y + h, cz + dz, AIR)
    # stair runs: transition f goes from FLOORS[f] up to FLOORS[f+1]
    for f in range(4):
        yp, yn = FLOORS[f], FLOORS[f + 1]
        south = f % 2 == 0
        for i in range(10):
            x = cx - 5 + i if south else cx + 5 - i
            facing = "east" if south else "west"
            for zo in ((5, 6) if south else (-5, -6)):
                z = cz + zo
                y = yp + 1 + i
                for h in range(yp + 1, y):
                    S(x, h, z, "purpur_block")
                S(x, y, z, f"purpur_stairs[facing={facing},half=bottom]")
                for h in range(1, 5):
                    if i < 9 or h > 0:
                        S(x, y + h, z, AIR)
                if 4 <= i <= 8:
                    S(x, yn, z, AIR)                      # the hole in the next floor
                    for h in range(1, 4):
                        S(x, yn + h, z, AIR)
            if 4 <= i <= 8:                               # railing along the hole
                S(x, yn + 1, cz + (4 if south else -4), "dark_oak_fence")
    # lights and furniture
    for f in range(5):
        y = FLOORS[f]
        top = y + 10 if f < 4 else 118
        if f == 4:
            for dx in range(-9, 10):
                for dz in range(-9, 10):
                    if math.hypot(dx, dz) <= 9.3:
                        S(cx + dx, 118, cz + dz, "deepslate_tiles" if hash01(dx, 1, dz, 4) < 0.7 else "amethyst_block")
        for h in (1, 2, 3):
            S(cx, top - h, cz, "iron_chain[axis=y]")
        lamp(cx, top - 4, cz)
        for k in range(8):                                # wall sconces
            a = k * math.pi / 4 + math.pi / 8
            sx, sz = cx + int(round(7.4 * math.cos(a))), cz + int(round(7.4 * math.sin(a)))
            if free(sx, y + 3, sz) and free(sx, y + 1, sz):
                S(sx, y + 1, sz, "purpur_pillar")
                S(sx, y + 2, sz, "purpur_pillar")
                lamp(sx, y + 3, sz)
        for k in range(24):                               # bookshelves under the windows
            a = k * math.pi / 12
            sx, sz = cx + int(round(7.6 * math.cos(a))), cz + int(round(7.6 * math.sin(a)))
            if f >= 1 and free(sx, y + 1, sz) and free(sx, y + 2, sz) and hash01(sx, y, sz, 6) < 0.7 and math.hypot(sx - cx, sz - cz) > 7:
                S(sx, y + 1, sz, "bookshelf")
                S(sx, y + 2, sz, "bookshelf")
    S(cx, 69, cz - 5, "enchanting_table")
    S(cx + 4, FLOORS[1] + 1, cz, "enchanting_table")
    S(cx - 3, FLOORS[2] + 1, cz - 2, "brewing_stand")
    S(cx - 3, FLOORS[2] + 1, cz + 1, "cauldron")
    S(cx + 3, FLOORS[3] + 1, cz, "lectern[facing=west]")
    S(cx, FLOORS[4] + 1, cz, "amethyst_block")
    S(cx, FLOORS[4] + 2, cz, "amethyst_cluster[facing=up]")
    # the exit portal frame (ground floor, east side): crying obsidian ring, purple glass inside
    px = cx + 6
    for dz in range(-2, 2):
        S(px, 68, cz + dz, "crying_obsidian")
        S(px, 72, cz + dz, "crying_obsidian")
    for y in range(68, 73):
        S(px, y, cz - 2, "crying_obsidian")
        S(px, y, cz + 1, "crying_obsidian")
    for dz in (-1, 0):
        for y in range(69, 72):
            S(px, y, cz + dz, "purple_stained_glass")
    for dz in (-3, 2):
        S(px, 69, cz + dz, "purpur_pillar")
        S(px, 70, cz + dz, "purpur_pillar")
        lamp(px, 71, cz + dz)
    # the balcony (floor 3, y 88): deck, bars, pillars down to the ground
    for dx in range(-13, 14):
        for dz in range(-13, 14):
            d = math.hypot(dx, dz)
            if 9.3 < d <= 12.4:
                x, z = cx + dx, cz + dz
                S(x, 88, z, "gold_block" if d > 11.8 else ("purpur_block" if (dx + dz) % 2 else "polished_deepslate"))
                if d > 11.7 and not (dx > 0 and abs(dz) <= 1):
                    S(x, 89, z, "iron_bars")
                if d > 11.7 and int(math.degrees(math.atan2(dz, dx))) % 20 == 0:
                    S(x, 89, z, "gold_block")
                    lamp(x, 90, z)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        px_, pz_ = cx + int(round(11.4 * math.cos(a))), cz + int(round(11.4 * math.sin(a)))
        for y in range(68, 88):
            S(px_, y, pz_, "purpur_pillar")
    # the pointed roof
    for y in range(119, 139):
        R = 10.5 - (y - 119) * 0.55
        for dx in range(-11, 12):
            for dz in range(-11, 12):
                d = math.hypot(dx, dz)
                if d <= R and (d > R - 1.8 or R < 2):
                    row = (y - 119) % 5
                    blk = "amethyst_block" if row == 0 and y > 119 else "deepslate_tiles"
                    if y == 119:
                        blk = "crying_obsidian" if hash01(dx, y, dz, 3) < 0.5 else "purpur_block"
                    elif hash01(dx, y, dz, 3) < 0.12:
                        blk = "cracked_deepslate_tiles"
                    S(cx + dx, y, cz + dz, blk)
    for dx in range(-11, 12):                            # eaves
        for dz in range(-11, 12):
            if 10.2 < math.hypot(dx, dz) <= 11.2:
                S(cx + dx, 118, cz + dz, "gold_block" if (dx + dz) % 2 == 0 else "purpur_block")
    for y in range(138, 142):
        S(cx, y, cz, "gold_block")
    S(cx, 142, cz, "end_rod[facing=up]")
    S(cx, 143, cz, "end_rod[facing=up]")
    # flying buttress-like spires around the roof
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        sx, sz = cx + int(round(10 * math.cos(a))), cz + int(round(10 * math.sin(a)))
        for y in range(119, 126):
            S(sx, y, sz, "gold_block" if y == 125 else "purpur_pillar")
        S(sx, 126, sz, "end_rod[facing=up]")
    # a lantern-lit approach with banners of amethyst
    for dz in (-3, 3):
        for dx in (-17, -22):
            y = T(cx + dx, cz + dz) if (cx + dx, cz + dz) in area.top else 68
            for h in range(1, 4):
                S(cx + dx, y + h, cz + dz, "purpur_pillar")
            lamp(cx + dx, y + 4, cz + dz)


tower()
S(ARRIVAL[0] and -46, 69, 110, AIR)

# Enigma's little shrine at the tower's base
S(-59, 68, 106, "crying_obsidian")
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    S(-58 + dx * 2, 69, 106 + dz * 2, "amethyst_cluster[facing=up]")
S(-58, 68, 106, "gold_block")

# ------------------------------------------------------------------------------------------------ small buildings
def box_building(x0, z0, w, d, y0, h, wall, post, floor, roof_stairs, roof_slab, door_x=None, door_side="N", shear=0.0, sway=0.0):
    """A little house with a gabled roof along x; `shear` leans it east per level (the crooked Shifted Tavern)."""
    for k in range(h):
        ox = int(round(shear * k)) if k else 0
        oz = int(round(sway * k)) if k else 0
        y = y0 + k
        for x in range(x0 + ox, x0 + ox + w):
            for z in range(z0 + oz, z0 + oz + d):
                edge = x in (x0 + ox, x0 + ox + w - 1) or z in (z0 + oz, z0 + oz + d - 1)
                corner = x in (x0 + ox, x0 + ox + w - 1) and z in (z0 + oz, z0 + oz + d - 1)
                if not edge:
                    S(x, y, z, AIR)
                    continue
                if corner:
                    S(x, y, z, f"{post}[axis=y]")
                elif k in (2, 5) and (x - x0) % 3 == 1 and (z - z0) % 3 == 1:
                    S(x, y, z, "purple_stained_glass_pane")
                elif k in (2, 5) and ((x - x0) % 3 == 1 and z in (z0 + oz, z0 + oz + d - 1) or (z - z0) % 3 == 1 and x in (x0 + ox, x0 + ox + w - 1)):
                    S(x, y, z, "purple_stained_glass_pane")
                else:
                    S(x, y, z, wall)
    ox_t = int(round(shear * h)) if h else 0
    oz_t = int(round(sway * h)) if h else 0
    xs = range(x0 + ox_t - 1, x0 + ox_t + w + 1)
    for dz in range(-1, d + 1):
        zz = z0 + oz_t + dz
        lvl = min(dz + 1, d - dz) if True else 0
        lvl = min(dz, d - 1 - dz) + 1
        yy = y0 + h - 1 + lvl
        facing = "south" if dz < d / 2 else "north"
        for x in xs:
            if dz in (d // 2,) and d % 2:
                S(x, yy, zz, roof_slab)
            else:
                S(x, yy, zz, f"{roof_stairs}[facing={facing},half=bottom]")
        for x in (x0 + ox_t, x0 + ox_t + w - 1):
            for y in range(y0 + h, yy):
                if 0 <= dz < d:
                    S(x, y, zz, wall)
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            S(x, y0 - 1, z, floor)
    if door_x is not None:
        zd = z0 if door_side == "N" else z0 + d - 1
        for y in (y0, y0 + 1):
            S(door_x, y, zd, AIR)
        S(door_x, y0 + 2, zd, f"{post}[axis=x]")


# The Shifted Tavern (crooked) at (-76, 119)
box_building(-82, 115, 11, 9, 69, 8, "dark_oak_planks", "stripped_dark_oak_log", "spruce_planks", "dark_oak_stairs", "dark_oak_slab",
             door_x=-77, door_side="N", shear=0.45, sway=0.18)
for x in range(-81, -73):
    S(x, 69, 117, "barrel[facing=up]" if x % 2 == 0 else "stripped_dark_oak_log[axis=x]")
S(-72, 69, 121, "crafting_table")
for dx, dz in ((-79, 121), (-75, 120), (-80, 119)):
    S(dx, 69, dz, "dark_oak_fence")
    S(dx, 70, dz, "stone_pressure_plate")
lamp(-77, 73, 119)
S(-77, 72, 119, "iron_chain[axis=y]")
S(-77, 71, 119, "iron_chain[axis=y]")
S(-81, 69, 122, "brewing_stand")
S(-80, 69, 122, "cauldron")
S(-77, 69, 114, "lantern[hanging=false]")        # a lantern by the door
TAVERN_NPC = (-77, 69, 119)

# Inverted Sirius's shack (ground) at (-136, 101)
box_building(-140, 98, 7, 6, 69, 4, "mangrove_planks", "stripped_mangrove_log", "spruce_planks", "mangrove_stairs", "mangrove_slab",
             door_x=-137, door_side="N")
S(-138, 69, 102, "ender_chest[facing=north]")
S(-136, 69, 102, "chest[facing=north]")
S(-135, 69, 102, "chest[facing=north]")
S(-136, 69, 108, "ender_chest[facing=south]")
for dx in (-139, -133):
    S(dx, 69, 108, "chest[facing=south]")
S(-137, 69, 101, "lantern[hanging=false]") if False else None
lamp(-137, 72, 101)
SIRIUS_NPC = (-136, 69, 107)

# the Bug camp (Bughunter, Bugshopper) at (-82, 163)
for dx in range(-3, 4):                                   # a tent of purple wool
    h = 3 - abs(dx)
    for dz in range(-3, 4):
        S(-82 + dx, 69 + h, 163 + dz, "purple_wool" if (dz + dx) % 3 else "magenta_wool")
        if h > 0 and abs(dz) < 3 and abs(dx) < 3:
            S(-82 + dx, 69 + h - 1 if h > 1 else 69, 163 + dz, AIR) if False else None
for dx in range(-2, 3):
    for dy in range(0, 3 - abs(dx)):
        for dz in range(-2, 3):
            S(-82 + dx, 69 + dy, 163 + dz, AIR)
S(-82, 69, 162, "crafting_table")
S(-84, 69, 164, "barrel[facing=up]")
S(-80, 69, 164, "barrel[facing=up]")
for dz in (-3, 3):
    for dx in (-3, 3):
        S(-82 + dx, 69, 163 + dz, "dark_oak_fence")
        S(-82 + dx, 70, 163 + dz, "dark_oak_fence")
        S(-82 + dx, 71, 163 + dz, "lantern[hanging=false]")
# Motes Grubber's stall
for dx in range(-2, 3):
    S(-92 + dx, 69, 159, "spruce_slab[type=bottom]")
S(-94, 69, 160, "dark_oak_fence")
S(-94, 70, 160, "dark_oak_fence")
S(-90, 69, 160, "dark_oak_fence")
S(-90, 70, 160, "dark_oak_fence")
for dx in range(-94, -89):
    S(dx, 71, 160, "purple_carpet") if False else None
S(-94, 71, 160, "lantern[hanging=false]")
S(-90, 71, 160, "lantern[hanging=false]")
S(-92, 70, 159, "barrel[facing=up]")

# Tel Kar's camp by the crossing
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    S(-113 + dx * 2, 69, 63 + dz * 2, "mossy_cobblestone_wall") if False else None
S(-116, 69, 62, "mossy_stone_brick_wall")
S(-116, 70, 62, "mossy_stone_brick_wall")
lamp(-116, 71, 62)
S(-110, 69, 62, "mossy_stone_brick_wall")
S(-110, 70, 62, "mossy_stone_brick_wall")
lamp(-110, 71, 62)

# Broken Cage: a ruined iron-bar cage with a wither skeleton skull inside, at (-141, 146)
BCX, BCZ = -141, 146
for dx in range(-5, 6):
    for dz in range(-5, 6):
        d = max(abs(dx), abs(dz))
        if d <= 4:
            S(BCX + dx, 68, BCZ + dz, "cracked_stone_bricks" if hash01(dx, 0, dz, 5) < 0.35 else "mossy_stone_bricks")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2:
            for y in range(69, 74):
                if (dx in (-2, 2) and dz in (-2, 2)):
                    S(BCX + dx, y, BCZ + dz, "mossy_stone_brick_wall" if y < 73 else "tuff_bricks")
                elif hash01(BCX + dx, y, BCZ + dz, 6) > 0.3 and not (dz == 2 and dx in (0, 1)):
                    S(BCX + dx, y, BCZ + dz, "iron_bars")
        elif hash01(BCX + dx, 74, BCZ + dz, 6) > 0.6 and max(abs(dx), abs(dz)) < 2:
            pass
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if hash01(BCX + dx, 74, BCZ + dz, 7) > 0.45:
            S(BCX + dx, 74, BCZ + dz, "iron_bars")
S(BCX, 69, BCZ, "mossy_cobblestone")
S(BCX, 70, BCZ, "wither_skeleton_skull[rotation=8]")
for dx, dz in ((-1, -1), (1, 1)):
    S(BCX + dx, 69, BCZ + dz, "cobweb")
lamp(BCX - 4, 70, BCZ + 4)
lamp(BCX + 4, 70, BCZ - 4)
SOUL_CAGE = (BCX - 1, 69, BCZ + 1)
PORHTAL_NPC = (BCX + 3, 69, BCZ + 4)

# The Bastion: a mossy ruined fort at (-72, 182)
BX, BZ = -72, 182
for dx in range(-9, 10):
    for dz in range(-8, 9):
        if max(abs(dx) / 9.0, abs(dz) / 8.0) > 1.0:
            continue
        x, z = BX + dx, BZ + dz
        S(x, 68, z, "mossy_stone_bricks" if hash01(x, 0, z, 8) < 0.6 else "cracked_stone_bricks" if hash01(x, 1, z, 8) < 0.5 else "mossy_cobblestone")
        edge = abs(dx) == 9 or abs(dz) == 8
        if edge:
            hgt = 3 + int(hash01(x, 2, z, 8) * 5)
            if hash01(x, 3, z, 8) < 0.1:
                hgt = 0
            if abs(dx) in (8, 9) and abs(dz) in (7, 8):
                hgt = 9
            if dz == -8 and abs(dx) <= 1:
                hgt = 0                                                      # the gate
            for y in range(69, 69 + hgt):
                S(x, y, z, "mossy_stone_bricks" if hash01(x, y, z, 9) < 0.5 else "stone_bricks" if hash01(x, y, z, 10) < 0.5 else "cracked_stone_bricks")
            if hgt >= 9:
                S(x, 69 + hgt, z, "mossy_stone_brick_wall")
                S(x, 70 + hgt, z, "mossy_stone_brick_wall")
                lamp(x, 71 + hgt, z)
            elif hgt:
                S(x, 69 + hgt, z, "mossy_stone_brick_stairs[facing=north,half=bottom]" if hash01(x, 4, z, 8) < 0.4 else "mossy_cobblestone_wall")
        elif hash01(x, 5, z, 8) < 0.06:
            for y in range(69, 69 + 1 + int(hash01(x, 6, z, 8) * 3)):
                S(x, y, z, "mossy_cobblestone")
S(BX, 69, BZ, "mossy_cobblestone")
S(BX, 70, BZ, "chest[facing=south]")
S(BX - 3, 69, BZ + 2, "iron_bars")
S(BX + 3, 69, BZ - 2, "iron_bars")

# a trail of flower pots to a mossy altar at (-128, 77)
POTS = ["potted_allium", "potted_cornflower", "potted_azure_bluet", "potted_pink_tulip", "potted_lily_of_the_valley",
        "potted_red_mushroom", "potted_brown_mushroom", "potted_azalea_bush"]
trail = [(-108, 71), (-111, 73), (-114, 73), (-117, 75), (-120, 75), (-123, 77), (-126, 77)]
for i, (x, z) in enumerate(trail):
    S(x, T(x, z) + 1, z, POTS[i % len(POTS)])
AX, AZ = -129, 77
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2 and (dx, dz) != (-2, 0):
            S(AX + dx, 68, AZ + dz, "mossy_cobblestone")
S(AX, 68, AZ, "mossy_stone_bricks")
S(AX, 69, AZ, "potted_azalea_bush")
S(AX + 1, 69, AZ + 1, "glow_lichen[down=true]")
lamp(AX - 2, 70, AZ - 2)
SOUL_POT = (AX - 1, 69, AZ)

# Two Plates
for dx in (-2, 2):
    S(-101 + dx, 68, 88, "mossy_cobblestone")
    S(-101 + dx, 69, 88, "stone_pressure_plate")
S(-101, 68, 88, "pale_oak_planks")
S(-101, 69, 89, "lantern[hanging=false]") if False else None
SOUL_PLATES = (-101, 69, 88)

# Montezuma's tree-shaded spot
S(-94, 68, 112, "moss_block")
S(-94, 69, 113, "pink_petals[facing=north,flower_amount=3]")

# ------------------------------------------------------------------------------------------------ launch tubes
def launch(cx, cz, top_y, platform=False):
    y0 = T(cx, cz)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            S(cx + dx, y0, cz + dz, "slime_block")
    S(cx, y0 + 1, cz, "stone_pressure_plate")
    for y in range(y0 + 1, top_y + 1):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) == 2:
                    corner = abs(dx) == 2 and abs(dz) == 2
                    S(cx + dx, y, cz + dz, "purple_stained_glass" if not corner else "purpur_pillar")
                else:
                    S(cx + dx, y, cz + dz, AIR)
    for y in (y0 + 1, y0 + 2):
        S(cx, y, cz + 2, AIR)
    for dx in range(-2, 3):
        S(cx + dx, y0, cz + 3, "purpur_slab[type=bottom]") if False else None
    lamp(cx + 3, y0 + 1, cz + 3)
    for y in range(y0 + 6, top_y, 6):
        lamp(cx + 2, y, cz + 2) if False else S(cx + 2, y, cz + 2, "sea_lantern")
    if platform:
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if max(abs(dx), abs(dz)) > 1:
                    S(cx + dx, top_y, cz + dz, "pale_oak_planks")
                    if max(abs(dx), abs(dz)) == 4:
                        S(cx + dx, top_y + 1, cz + dz, "pale_oak_fence")
        for dx, dz in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
            lamp(cx + dx, top_y + 2, cz + dz)
        for k in range(1, 7):                          # braces under the platform
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                S(cx + dx * (2 + k // 2), top_y - k, cz + dz * (2 + k // 2), "stripped_pale_oak_log[axis=y]") if k < 4 else None


L1 = (-106, 124)
launch(*L1, 112, platform=True)
SOUL_SKY = (L1[0] + 3, 113, L1[1] + 3)
S(L1[0] + 3, 113, L1[1] + 3, AIR)
launch(-39, 158, RING + 1)
launch(-62, 152, RING + 1)
LAUNCHERS = [L1, (-39, 158), (-62, 152)]

# ------------------------------------------------------------------------------------------------ trees
def wood_blocks(w):
    return {"log": f"{w}_log", "strip": f"stripped_{w}_log", "plank": f"{w}_planks", "fence": f"{w}_fence",
            "stairs": f"{w}_stairs", "slab": f"{w}_slab"}


def trunk_radius(name, h, ang):
    cx, cz, r, H, w = TREES[name]
    rr = float(r)
    t = h / H
    if t > 0.72:
        rr = r * (1 - 0.4 * (t - 0.72) / 0.28)
    if h < 5 and abs(ang - math.pi / 2) > 0.5:
        lobe = 0.5 + 0.5 * math.sin(3 * ang + cx)
        rr += 1.9 * (1 - h / 5.0) * (0.35 + 0.65 * lobe)
    return max(2.0, rr)


BASE = {}
for name, (cx, cz, r, H, w) in TREES.items():
    base = T(cx, cz)
    BASE[name] = base
    wb = wood_blocks(w)
    rmax = int(r + 4)
    for dx in range(-rmax, rmax + 1):
        for dz in range(-rmax, rmax + 1):
            d = math.hypot(dx, dz)
            ang = math.atan2(dz, dx)
            x, z = cx + dx, cz + dz
            for h in range(-4, H):
                rr = trunk_radius(name, max(h, 0), ang)
                if d <= rr:
                    y = base + h
                    if h < 0 and y <= T(x, z) - 1:
                        continue
                    if h == 0:
                        for yy in range(T(x, z), base):
                            S(x, yy, z, f"{wb['log']}[axis=y]")
                    S(x, y, z, f"{wb['log']}[axis=y]")
                    if hash01(x, y, z, 12) < 0.04 and h > 2:
                        S(x, y, z, f"{wb['strip']}[axis=y]")
    # ladder up the south face
    fr = int(r)
    lx, lz = cx, cz + fr + 1
    for y in range(T(lx, lz) + 1, RING + 2):
        S(lx, y, lz, "ladder[facing=south]")


def ring(name):
    cx, cz, r, H, w = TREES[name]
    wb = wood_blocks(w)
    Hh = RING_H[name]
    fr = int(r)
    for dx in range(-Hh - 1, Hh + 2):
        for dz in range(-Hh - 1, Hh + 2):
            x, z = cx + dx, cz + dz
            d = math.hypot(dx, dz)
            if (x, z) == (cx, cz + fr + 1) or d > Hh + 0.5:
                continue
            if empty(x, RING, z):
                S(x, RING, z, wb["plank"] if hash01(x, 1, z, 13) < 0.85 else wb["slab"] + "[type=top]")
            if d > Hh - 0.6 and empty(x, RING + 1, z):
                S(x, RING + 1, z, wb["fence"])
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):         # braces under the ring
        for k in range(1, 7):
            x, z = cx + dx * (int(r) + k), cz + dz * (int(r) + k)
            axis = "x" if dx else "z"
            if empty(x, RING - k, z):
                S(x, RING - k, z, f"{wb['strip']}[axis={axis}]")
    q = int(round(Hh * 0.7))
    for sx, sz in ((q, q), (-q, q), (q, -q), (-q, -q)):
        for k in range(0, 3):
            S(cx + sx, RING - 1 - k, cz + sz, f"{wb['strip']}[axis=y]")
        S(cx + sx, RING + 2, cz + sz, wb["fence"])
        lamp(cx + sx, RING + 3, cz + sz)


def side_for(name):
    cx, cz, r, H, w = TREES[name]
    dirs = []
    for a, b in LINKS:
        if name in (a, b):
            o = b if a == name else a
            dirs.append(math.degrees(math.atan2(TREES[o][1] - cz, TREES[o][0] - cx)))
    for t, _ in TOWER_LINKS:
        if t == name:
            dirs.append(math.degrees(math.atan2(TCZ - cz, TCX - cx)))
    dirs.append(90.0)                                          # the ladder is on the south face
    best, bs = None, -1
    for side, ang in (("N", -90.0), ("E", 0.0), ("S", 90.0), ("W", 180.0)):
        sc = min(abs((ang - d + 180) % 360 - 180) for d in dirs)
        if sc > bs:
            best, bs = side, sc
    return best


def hmap(name, side, u, v):
    cx, cz = TREES[name][0], TREES[name][1]
    return {"N": (cx + u, cz - v), "S": (cx + u, cz + v), "E": (cx + v, cz + u), "W": (cx - v, cz + u)}[side]


def treehouse(name, npc_name):
    cx, cz, r, H, w = TREES[name]
    wb = wood_blocks(w)
    side = side_for(name)
    v0 = int(r) + 3
    y = RING
    face_plus = {"N": "north", "S": "south", "E": "east", "W": "west"}[side]
    face_minus = {"N": "south", "S": "north", "E": "west", "W": "east"}[side]
    for u in range(-3, 4):
        for v in range(v0, v0 + 7):
            x, z = hmap(name, side, u, v)
            edge = abs(u) == 3 or v in (v0, v0 + 6)
            corner = abs(u) == 3 and v in (v0, v0 + 6)
            S(x, y, z, wb["plank"])
            for h in range(1, 5):
                if not edge:
                    S(x, y + h, z, AIR)
                elif corner:
                    S(x, y + h, z, f"{wb['strip']}[axis=y]")
                elif h == 2 and ((abs(u) == 3 and v == v0 + 3) or (v == v0 + 6 and abs(u) == 1) or (v == v0 and abs(u) == 2)):
                    S(x, y + h, z, "purple_stained_glass_pane")
                else:
                    S(x, y + h, z, wb["plank"] if hash01(x, h, z, 14) < 0.7 else wb["log"] + "[axis=y]")
    for u_ in (-3, 3):
        for v_ in (v0 + 6, v0):
            x, z = hmap(name, side, u_, v_)
            for yy in range(RING - 1, RING - 9, -1):
                if empty(x, yy, z):
                    S(x, yy, z, f"{wb['strip']}[axis=y]")
    for h in (1, 2):                                          # the doorway faces the trunk
        S(*[hmap(name, side, 0, v0)[0], y + h, hmap(name, side, 0, v0)[1]], AIR)
    for u in range(-4, 5):                                    # roof along u
        for w_ in range(-1, 8):
            v = v0 - 1 + w_ if False else v0 + w_ - 1
            lvl = min(w_, 6 - w_) if 0 <= w_ <= 6 else (0 if w_ == -1 else 0)
            x, z = hmap(name, side, u, v)
            yy = y + 5 + (min(w_, 6 - w_) if 0 <= w_ <= 6 else -1) if 0 <= w_ <= 6 else y + 4
            if w_ in (-1, 7):
                continue
            if w_ == 3:
                S(x, yy, z, wb["slab"] + "[type=bottom]" if abs(u) == 4 else wb["plank"])
            elif abs(u) <= 4:
                S(x, yy, z, f"{wb['stairs']}[facing={face_plus if w_ < 3 else face_minus},half=bottom]")
            if abs(u) == 3 and 0 <= w_ <= 6:
                for y2 in range(y + 5, yy):
                    S(x, y2, z, wb["plank"])
    ix, iz = hmap(name, side, 2, v0 + 5)
    S(ix, y + 1, iz, wb["fence"])
    S(ix, y + 2, iz, "lantern[hanging=false]")
    bx_, bz_ = hmap(name, side, -2, v0 + 5)
    S(bx_, y + 1, bz_, "barrel[facing=up]")
    cx_, cz_ = hmap(name, side, -2, v0 + 4)
    S(cx_, y + 1, cz_, "crafting_table")
    nx, nz = hmap(name, side, 0, v0 + 3)
    area.npc(npc_name, nx, y + 1, nz, {"N": 0.0, "S": 180.0, "E": 90.0, "W": -90.0}[side],
             f"inside the treehouse on {name} (door faces the trunk, side {side})")
    return side


for n in TREES:
    ring(n)
HOUSE_SIDES = {}
for n, nm in HOUSES.items():
    HOUSE_SIDES[n] = treehouse(n, nm)


# ------------------------------------------------------------------------------------------------ walkways
def walkway(a, b, wood, stop_r=None):
    (x1, z1), (x2, z2) = a, b
    L = math.hypot(x2 - x1, z2 - z1)
    nx, nz = -(z2 - z1) / L, (x2 - x1) / L
    wb = wood_blocks(wood)
    axis = "x" if abs(x2 - x1) >= abs(z2 - z1) else "z"
    deck, rails, centre = set(), set(), []
    n = int(L * 2)
    for i in range(n + 1):
        t = i / n
        px, pz = x1 + (x2 - x1) * t, z1 + (z2 - z1) * t
        if stop_r and math.hypot(px - TCX, pz - TCZ) <= stop_r:
            continue
        centre.append((int(round(px)), int(round(pz))))
        for s in (-1, 0, 1):
            deck.add((int(round(px + nx * s)), int(round(pz + nz * s))))
        for s in (-2, 2):
            rails.add((int(round(px + nx * s)), int(round(pz + nz * s))))
    near_ring = {}
    for name, (cx, cz, r, H, w) in TREES.items():
        near_ring[name] = (cx, cz, RING_H[name])
    for (x, z) in deck:
        if empty(x, RING, z):
            S(x, RING, z, wb["plank"] if hash01(x, 2, z, 15) < 0.8 else wb["slab"] + "[type=top]")
        for y in (RING + 1, RING + 2, RING + 3):
            if area.get(x, y, z) in (wb["fence"].join(["minecraft:", ""]), "minecraft:" + wb["fence"], "minecraft:lantern", "minecraft:iron_chain", "minecraft:sea_lantern"):
                S(x, y, z, AIR)
    for (x, z) in rails:
        if (x, z) in deck:
            continue
        inside = any(math.hypot(x - cx, z - cz) <= Hh + 0.8 for cx, cz, Hh in near_ring.values())
        if math.hypot(x - TCX, z - TCZ) <= 12.8:
            inside = True
        if inside:
            continue
        S(x, RING + 1, z, wb["fence"])
        S(x, RING + 2, z, f"iron_chain[axis={axis}]")
    for i, (x, z) in enumerate(centre):                       # posts with purple lanterns, plates, glass
        if i % 14 == 7:
            for s in (-2, 2):
                px, pz = int(round(x + nx * s)), int(round(z + nz * s))
                if (px, pz) not in deck and free(px, RING + 3, pz):
                    S(px, RING + 1, pz, wb["fence"])
                    S(px, RING + 2, pz, wb["fence"])
                    lamp(px, RING + 3, pz)
        if i % 23 == 11:
            S(x, RING + 1, z, "stone_pressure_plate") if empty(x, RING + 1, z) else None
        if i % 31 == 17:
            S(x, RING, z, "purple_stained_glass")
    for k in range(2, len(centre) - 2, 10):                   # ropes hanging underneath
        x, z = centre[k]
        for y in (RING - 1, RING - 2, RING - 3):
            if empty(x, y, z):
                S(x, y, z, "iron_chain[axis=y]")
        if empty(x, RING - 4, z):
            S(x, RING - 4, z, "sea_lantern") if False else lamp(x, RING - 4, z)


for a, b in LINKS:
    walkway(TREES[a][:2], TREES[b][:2], TREES[a][4])
for t, _ in TOWER_LINKS:
    walkway(TREES[t][:2], (TCX, TCZ), TREES[t][4], stop_r=12.5)

# ------------------------------------------------------------------------------------------------ branches with houses
def branch(name, dx, dz, length, thick=3):
    cx, cz, r, H, w = TREES[name]
    wb = wood_blocks(w)
    Hh = RING_H[name]
    axis = "x" if dx else "z"
    for i in range(0, length):
        x = cx + dx * (Hh + i)
        z = cz + dz * (Hh + i)
        for s in range(-(thick // 2), thick // 2 + 1):
            for y in range(RING - 2, RING + 1):
                bx, bz = (x, z + s) if dx else (x + s, z)
                S(bx, y, bz, f"{wb['log']}[axis={axis}]")
        # an undercarriage
        bx, bz = x, z
        S(bx, RING - 3, bz, f"{wb['strip']}[axis={axis}]")
        if i % 3 == 1:
            for yy in range(RING - 4, RING - 7, -1):
                S(bx, yy, bz, "iron_chain[axis=y]")
    return (cx + dx * (Hh + length - 1), cz + dz * (Hh + length - 1))


# Sad Jacquelle's house on a branch off t3 (to the south)
t3 = TREES["t3"]
end = branch("t3", 0, 1, 10, thick=3)
SJX, SJZ = end[0], end[1] + 5
wb = wood_blocks("dark_oak")
for dx in range(-3, 4):
    for dz in range(-4, 5):
        S(SJX + dx, RING, SJZ + dz, wb["plank"])
        S(SJX + dx, RING - 1, SJZ + dz, wb["log"] + "[axis=x]")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        x, z = SJX + dx, SJZ + dz + 1
        edge = abs(dx) == 2 or abs(dz) == 2
        for h in range(1, 4):
            if not edge:
                S(x, RING + h, z, AIR)
            elif abs(dx) == 2 and abs(dz) == 2:
                S(x, RING + h, z, f"{wb['strip']}[axis=y]")
            elif h == 2 and (dx == 0 or dz == 0):
                S(x, RING + h, z, "purple_stained_glass_pane")
            else:
                S(x, RING + h, z, wb["plank"])
for dz in range(-3, 4):
    for dx in range(-3, 4):
        lvl = 3 - max(abs(dx), abs(dz - 1)) if False else 0
for dx in range(-3, 4):
    for dz in range(-3, 4):
        d = max(abs(dx), abs(dz))
        S(SJX + dx, RING + 4 + (2 - min(d, 2)) if d < 3 else RING + 4, SJZ + dz + 1,
          wb["slab"] + "[type=bottom]" if d == 3 else (wb["plank"] if d < 2 else wb["stairs"] + "[facing=north,half=bottom]"))
for h in (1, 2):
    S(SJX, RING + h, SJZ + 3, AIR)                  # the door (north wall faces the branch)
for h in (1, 2):
    S(SJX, RING + h, SJZ - 1, AIR)
S(SJX + 1, RING + 1, SJZ + 2, "lantern[hanging=false]")
S(SJX - 1, RING + 1, SJZ + 2, "barrel[facing=up]")
for dx in range(-3, 4):
    S(SJX + dx, RING + 1, SJZ - 4, wb["fence"])
    S(SJX + dx, RING + 1, SJZ + 5, wb["fence"])
for dz in range(-4, 6):
    S(SJX - 3, RING + 1, SJZ + dz, wb["fence"])
    S(SJX + 3, RING + 1, SJZ + dz, wb["fence"])
S(SJX, RING + 1, SJZ - 4, AIR)
S(SJX, RING + 1, SJZ - 3, AIR)
SAD_NPC = (end[0], RING + 1, end[1] - 2)

# Between Branches: a branch north off t6
end6 = branch("t6", 0, -1, 9, thick=3)
for dz in range(-2, 3):
    for dx in range(-2, 3):
        S(end6[0] + dx, RING, end6[1] - 1 + dz, "pale_oak_log[axis=z]") if abs(dx) < 2 and abs(dz) < 2 else None
S(end6[0] + 1, RING + 1, end6[1] - 1, "purple_stained_glass")
S(end6[0] - 1, RING + 1, end6[1] - 1, "mangrove_leaves[persistent=true]")
lamp(end6[0] + 1, RING + 2, end6[1] - 2)
SOUL_BRANCHES = (end6[0], RING + 1, end6[1] - 1)
S(*SOUL_BRANCHES[:1], SOUL_BRANCHES[1] - 1, SOUL_BRANCHES[2], AIR) if False else None

# Threebrothers on t6's ring, Tough Bark and its clearing
t6 = TREES["t6"]
THREEBROTHERS = (t6[0] + 7, RING + 1, t6[1] + 4)

# ------------------------------------------------------------------------------------------------ Fleespook: a hollow at the foot of t1
cx, cz, r, H, w = TREES["t1"]
fb = BASE["t1"]
for dx in range(-1, 2):
    for dz in range(-1, 2):
        if dx * dx + dz * dz <= 2:
            for y in range(fb + 1, fb + 4):
                S(cx + dx, y, cz + dz, AIR)
            S(cx + dx, fb, cz + dz, "moss_block")
for dz in range(-6, -1):
    for y in range(fb + 1, fb + 3):
        S(cx, y, cz + dz, AIR)
    S(cx, fb, cz + dz, "moss_block")
S(cx + 1, fb + 1, cz + 1, "glow_lichen[down=true]") if False else None
S(cx - 1, fb + 1, cz, "glow_lichen[east=true]")
S(cx + 1, fb + 2, cz, "glow_lichen[west=true]")
lamp(cx, fb + 4, cz) if False else S(cx, fb + 3, cz, "sea_lantern")
SOUL_FLEE = (cx, fb + 1, cz)

# ------------------------------------------------------------------------------------------------ canopies
LEAVES = ["dark_oak_leaves", "mangrove_leaves"]


def leaf(x, y, z, bias):
    if free(x, y, z):
        blk = LEAVES[0] if hash01(x, y, z, 21) < bias else LEAVES[1]
        S(x, y, z, f"{blk}[persistent=true]")


def blob(cx, cy, cz, R, Ry, seed, bias):
    for dx in range(-int(R), int(R) + 1):
        for dz in range(-int(R), int(R) + 1):
            h2 = (dx * dx + dz * dz) / (R * R)
            if h2 > 1:
                continue
            for dy in range(-int(Ry), int(Ry) + 1):
                v = h2 + (dy / Ry) ** 2
                if v <= 1 - 0.14 * hash01(cx + dx, cy + dy, cz + dz, seed) - 0.08 * noise(cx + dx, cz + dz, seed, 2.0):
                    leaf(cx + dx, cy + dy, cz + dz, bias)


for name, (cx, cz, r, H, w) in TREES.items():
    base = BASE[name]
    top = base + H
    bias = 0.6 if w != "mangrove" else 0.4
    blob(cx, top - 4, cz, 16, 10, 31 + cx, bias)
    for k in range(5):
        a = k * math.pi * 2 / 5 + hash01(cx, k, cz, 1) * 1.0
        ox, oz = cx + int(round(10 * math.cos(a))), cz + int(round(10 * math.sin(a)))
        oy = top - 6 + int(hash01(cx, k, cz, 2) * 5)
        blob(ox, oy, oz, 9, 6, 41 + k, bias)
        # a big branch up to the lobe
        for i in range(1, 11):
            x, z = cx + int(round(math.cos(a) * i * 1.2)), cz + int(round(math.sin(a) * i * 1.2))
            y = top - 14 + int(i * (oy - (top - 14)) / 11)
            if i > r - 1:
                S(x, y, z, f"{w}_log[axis={'x' if abs(math.cos(a)) > abs(math.sin(a)) else 'z'}]") if free(x, y, z) else None
    for i in range(0, 4):                                    # a few pieces of purple glass and lanterns up in the canopy
        a = hash01(cx, i, cz, 4) * 6.28
        gx, gz = cx + int(round(9 * math.cos(a))), cz + int(round(9 * math.sin(a)))
        gy = top - 9 + i
        if free(gx, gy, gz):
            S(gx, gy, gz, "purple_stained_glass") if i % 2 else lamp(gx, gy, gz)
# lesser trees in the gaps (3 wide, 22-30 high) so the floor between the giants isn't bare
LESS = []
cand = [(x, z) for x in range(-155, -12, 3) for z in range(64, 196, 3)]
cand.sort(key=lambda p: hash01(p[0], 1, p[1], 77))
SEGS = [(TREES[a][:2], TREES[b][:2]) for a, b in LINKS] + [(TREES[t][:2], (TCX, TCZ)) for t, _ in TOWER_LINKS]
PATHSEGS = [(a, b) for pts in [MAIN] + list(SIDE.values()) for a, b in zip(pts, pts[1:])]
for (x, z) in cand:
    if (x, z) not in cols or cols[(x, z)][1] < 7 or len(LESS) >= 70:
        continue
    if any(math.hypot(x - c[0], z - c[1]) <= RING_H[n] + 6 for n, c in TREES.items()):
        continue
    if math.hypot(x - TCX, z - TCZ) < 18 or any(math.hypot(x - lx, z - lz) < 9 for lx, lz in LESS):
        continue
    if any(seg_dist(x, z, a, b) < 6 for a, b in SEGS) or any(seg_dist(x, z, a, b) < 6 for a, b in PATHSEGS):
        continue
    if any(math.hypot(x - cx, z - cz) < r + 5 for cx, cz, r in FLATS) or math.hypot(x - LAKE[0], z - LAKE[1]) < LAKE_R + 5:
        continue
    LESS.append((x, z))
    wsel = ["mangrove", "pale_oak", "dark_oak"][int(hash01(x, 2, z, 78) * 3)]
    base = T(x, z)
    Ht = 22 + int(hash01(x, 3, z, 78) * 9)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if dx * dx + dz * dz <= 3:
                for h in range(-2, Ht):
                    S(x + dx, base + h, z + dz, f"{wsel}_log[axis=y]")
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        for h in range(0, 3):
            S(x + dx * (2 if h == 0 else 1), base + h, z + dz * (2 if h == 0 else 1), f"{wsel}_log[axis=y]") if h < 2 else None
    blob(x, base + Ht - 2, z, 8, 5, 91 + x, 0.55)
    for k in range(3):
        a = k * 2.1 + hash01(x, k, z, 79) * 2
        blob(x + int(round(6 * math.cos(a))), base + Ht - 5, z + int(round(6 * math.sin(a))), 5, 3.5, 95 + k, 0.55)
# hanging roots under the canopies
for (x, y, z), (entry, _) in list(area.piece.cells.items()):
    pass
roots = 0
for name, (cx, cz, r, H, w) in TREES.items():
    top = BASE[name] + H
    for dx in range(-18, 19):
        for dz in range(-18, 19):
            if dx * dx + dz * dz > 330:
                continue
            x, z = cx + dx, cz + dz
            if hash01(x, 55, z, 6) > 0.045:
                continue
            for y in range(top + 4, top - 18, -1):
                b = area.get(x + 0 - X0 + X0, y, z)
                if b and b.endswith("_leaves") and free(x, y - 1, z):
                    for i in range(1, 2 + int(hash01(x, y, z, 7) * 5)):
                        if free(x, y - i, z):
                            S(x, y - i, z, "hanging_roots")
                            roots += 1
                    break

# ------------------------------------------------------------------------------------------------ ground scatter
placed = 0
LAYERS = [("short_grass", 0.34), ("fern", 0.50), ("moss_carpet", 0.58), ("red_mushroom", 0.61), ("brown_mushroom", 0.64),
          ("allium", 0.68), ("cornflower", 0.71), ("azure_bluet", 0.74), ("pink_petals[facing=north,flower_amount=2]", 0.79),
          ("pink_petals[facing=east,flower_amount=3]", 0.82), ("glow_lichen[down=true]", 0.86), ("leaf_litter[facing=north,segment_amount=2]", 0.91),
          ("bush", 0.94), ("firefly_bush", 0.945)]
for (x, z), (top, e) in cols.items():
    if (x, z) in path_cells or hash01(x, 7, z, 31) > 0.62:
        continue
    surf = area.get(x, top, z) or ""
    if not surf.endswith(("grass_block", "podzol", "coarse_dirt", "moss_block", "rooted_dirt")):
        continue
    if not empty(x, top + 1, z):
        continue
    r_ = hash01(x, 8, z, 32)
    for blk, thr in LAYERS:
        if r_ < thr:
            S(x, top + 1, z, blk)
            placed += 1
            break

# ------------------------------------------------------------------------------------------------ rift shards: floating chunks past the cliffs
shards = 0
for k, (sx, sy, sz, sr) in enumerate([(-157, 78, 150, 3), (-154, 58, 93, 3), (-150, 90, 196, 2), (-120, 62, 197, 3), (-70, 80, 197, 3),
                                      (-14, 70, 185, 3), (-13, 95, 120, 3), (-15, 60, 70, 2), (-60, 66, 62, 2), (-30, 88, 62, 2),
                                      (-157, 66, 118, 2), (-100, 52, 197, 2)]):
    for dx in range(-sr, sr + 1):
        for dz in range(-sr, sr + 1):
            for dy in range(-2 * sr, sr + 1):
                taper = (dx * dx + dz * dz) / (sr * sr) + (dy / (2.2 * sr)) ** 2 if dy < 0 else (dx * dx + dz * dz) / (sr * sr) + (dy / 1.5) ** 2
                if taper <= 1.0 and X0 <= sx + dx <= X1 and Z0 <= sz + dz <= Z1 and (sx + dx, sz + dz) not in cols:
                    r_ = hash01(sx + dx, sy + dy, sz + dz, 41)
                    S(sx + dx, sy + dy, sz + dz, "crying_obsidian" if r_ < 0.15 else "amethyst_block" if r_ < 0.3 else "tuff" if r_ < 0.7 else "deepslate")
    shards += 1

# ------------------------------------------------------------------------------------------------ spots, zones
area.arrival(-46, 69, 110, -90.0)
area.warp("rift", -46, 69, 110, -90.0)
area.npc("spawn:exit_portal", -42, 69, 110, -90.0, "in front of the exit portal frame inside the Wizard Tower")
area.npc("Enigma", -58, 69, 106, -90.0, "at the Wizard Tower's base, outside the west door")
area.npc("Argofay Threebrothers", *THREEBROTHERS, 0.0, "on t6's walkway ring")
area.npc("Argofay Bughunter", -83, 69, 168, 0.0, "the bug camp")
area.npc("Argofay Bugshopper", -80, 69, 168, 0.0, "the bug camp")
area.npc("Sad Jacquelle", *SAD_NPC, 0.0, "on the branch next to her house (south of t3)")
area.npc("Inverted Sirius", *SIRIUS_NPC, 0.0, "behind his shack, by the transfer chests")
area.npc("Shifted", *TAVERN_NPC, 0.0, "inside the Shifted Tavern")
area.npc("Motes Grubber", -92, 69, 157, 180.0, "by his stall")
area.npc("Porhtal", *PORHTAL_NPC, 180.0, "at the Broken Cage")
area.npc("Tel Kar", -113, 69, 62, 90.0, "by the Black Lagoon crossing")
area.npc("spawn:montezuma_1", -94, 69, 112, 0.0, "a soul piece under a tree")
area.npc("enigma:Two Plates", *SOUL_PLATES, 0.0, "between two pressure plates")
area.npc("enigma:Up in the Sky", *SOUL_SKY, 0.0, "top of the slime launch tube at (-106, 124)")
area.npc("enigma:Between Branches", *SOUL_BRANCHES, 0.0, "end of the branch north of t6")
area.npc("enigma:Wither Cage", *SOUL_CAGE, 0.0, "inside the Broken Cage")
area.npc("enigma:Tough Bark", -115, 69, 146, 180.0, "at the big trunk (9 wide)")
area.npc("enigma:Woods Flower Pot", *SOUL_POT, 0.0, "end of the flower-pot trail")
area.npc("enigma:Fleespook", *SOUL_FLEE, 0.0, "hidden hollow at the foot of t1")
area.npc("enigma:Pressurized", *SOUL_PRESSURIZED, 0.0, "air pocket in the Pumpgrotto under the lake")
area.npc("enigma:Next to Enigma", *SOUL_NEXT, 0.0, "beside the pool in Enigma's Crib")
area.spots["notes"] += [
    "Slime launch tubes (pad of 3x3 slime blocks + pressure plate in a glass tube): " + ", ".join(f"({x}, {T(x, z)}, {z})" for x, z in LAUNCHERS),
    "Pumpgrotto: lake centre (-102, 176); dive down the shaft at (-109..-107, 176) to the tunnel (y 52-54) and up into the air pocket at the island (%d, 56, %d)" % (GCX, LZ),
    "Treehouse trunks: " + ", ".join(f"{n}=({TREES[n][0]},{TREES[n][1]})" for n in HOUSES) + "; ladders on the south face of every trunk up to the walkway level y 89",
]


# keep every spot clear: two blocks of air above the standing block
import math as _m
for nm, v in area.spots["npcs"].items():
    sx, sy, sz = int(_m.floor(v["pos"][0])), int(v["pos"][1]), int(_m.floor(v["pos"][2]))
    for dy in (0, 1, 2):
        if area.get(sx, sy + dy, sz) not in (None, AIR):
            S(sx, sy + dy, sz, AIR)


def mob(kind, name, level, health, damage, xp, coins, mx, every, a, b, drops=None):
    """A Rift mob: `coins` are the Motes it drops (the mod pays Motes for kills in the Rift)."""
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


SHY = [drop("SHAME_CRUX", 1, 1, 1)]
LARVA = [drop("LARVA_SILK", 0.5, 1, 1)]
WOLF = [drop("MUTED_BARK", 1, 1, 2)]
area.zone("Wyld Woods", box=((-160, 0, 60), (-58, 200, 199)), mobs=[
    mob("minecraft:zombie", "Shy", 1, 110, 4, 6, 100, 5, 6, (-108, 69, 112), (-96, 76, 132), SHY),
    mob("minecraft:zombie", "Shy", 1, 110, 4, 6, 100, 4, 6, (-132, 69, 160), (-120, 76, 170), SHY),
    mob("minecraft:silverfish", "Larva", 1, 3, 0, 1, 5, 6, 4, (-126, 69, 94), (-110, 78, 110), LARVA),
    mob("minecraft:silverfish", "Larva", 1, 3, 0, 1, 5, 6, 4, (-142, 69, 119), (-128, 78, 135), LARVA),
    mob("minecraft:silverfish", "Larva", 1, 3, 0, 1, 5, 5, 4, (-80, 69, 140), (-64, 78, 156), LARVA),
    mob("minecraft:silverfish", "Larva", 1, 3, 0, 1, 5, 5, 4, (-146, 69, 160), (-132, 78, 176), LARVA),
    mob("minecraft:wolf", "Barking Wolf", 2, 150, 6, 6, 40, 4, 8, (-132, 69, 150), (-118, 76, 160), WOLF),
])
# The tower's own footprint (and balcony) keeps Rift Time still; the rest of the east side is still the woods.
area.zone("Wizard Tower", box=((-57, 0, 97), (-33, 200, 123)))
area.zone("Wyld Woods", box=((-57, 0, 60), (-11, 200, 96)))
area.zone("Wyld Woods", box=((-57, 0, 124), (-11, 200, 199)))
area.zone("Wyld Woods", box=((-32, 0, 97), (-11, 200, 123)))
area.save()
print("trees", len(TREES), "posts", posts, "hanging roots", roots, "scatter", placed, "shards", shards,
      "house sides", HOUSE_SIDES, "launchers", LAUNCHERS)
print("tower ground:", area.surface_y(-46, 110), "arrival floor block:", area.get(-46, 68, 110))
