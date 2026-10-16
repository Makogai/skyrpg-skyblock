#!/usr/bin/env python3
"""The Coal Mine (Hypixel's Hub, north of the village): a rocky island with a rugged mountain, a big framed mine
entrance facing the bridge, tunnels and caverns full of coal ore, the Hub Crypts below (Crypt Ghouls, a Golden Ghoul),
and the sealed gate to the Gold Mine.

    python tools/builds/hub_areas/coal_mine.py
"""
import math
from kit import Area, hash01, noise

G = 77                      # surface; players stand at 78 (the Starwatch islet's level)
ICX, ICZ = 5, -170          # island centre
area = Area("coal_mine", (-55, 20, -235), (125, 125, 138), version=1, shell=3)

surface = area.mix({"grass_block": 5, "coarse_dirt": 2, "gravel": 2, "podzol": 1})
rock = area.mix({"andesite": 5, "tuff": 3, "cobblestone": 2, "calcite": 1})


def lobe(x, z):
    """Stretches the island towards the bridge's landing so it always meets it."""
    return 1 + 0.25 * math.exp(-(((x - 20) ** 2 + (z + 122) ** 2) / (2 * 16 ** 2)))


cols = area.island(ICX, ICZ, 55, top=G, seed=21, surface=surface, hills=1.5, depth=46, stone=rock, shape=lobe)

# ---------------------------------------------------------------- the mountain
MCX, MCZ, MRX, MRZ = 5, -180, 38, 30
high = area.mix({"calcite": 3, "tuff": 3, "andesite": 3, "cobblestone": 1})
mid = area.mix({"andesite": 5, "tuff": 3, "cobblestone": 3, "mossy_cobblestone": 1})
summit = (MCX, MCZ, -1)
for (x, z) in sorted(cols):
    e = math.hypot((x - MCX) / MRX, (z - MCZ) / MRZ) + 0.10 * noise(x, z, 7, 1.6)
    if e >= 1:
        continue
    f = 1 - e ** 1.6
    h = G + int(round(26 * f + 4 * noise(x, z, 9, 2.4) * f))
    if h <= G:
        continue
    for y in range(G, h + 1):
        if y >= 97 and hash01(x, y, z, 3) < 0.8:
            b = high(x, y, z)
        else:
            b = mid(x, y, z)
        if y >= h - 1 and hash01(x, y, z, 9) < 0.035:
            b = "minecraft:coal_ore"
        area.set(x, y, z, b)
    area.top[(x, z)] = h
    if h > summit[2]:
        summit = (x, z, h)
SX, SZ, SH = summit

# ---------------------------------------------------------------- the plaza in front of the mine, the bridge landing
plaza_pick = area.mix({"gravel": 3, "coarse_dirt": 3, "cobblestone": 1, "grass_block": 1})
PCX, PCZ = 5, -142


def in_plaza(x, z):
    return ((x - PCX) / 20.0) ** 2 + ((z - PCZ) / 18.0) ** 2 < 1


for (x, z) in sorted(cols):
    if in_plaza(x, z):
        area.flatten(x, z, x, z, G, plaza_pick(x, G, z))
    elif math.hypot(x - 22, z + 124) < 10:
        area.flatten(x, z, x, z, G, "minecraft:grass_block")

area.bridge((38, -89), (22, -122), y=G)
LANE = [(22, -121), (22, -132), (15, -140), (8, -148)]
area.path(LANE, width=3, blocks=area.mix({"gravel": 3, "cobblestone": 2, "coarse_dirt": 1, "dirt_path": 2}))


def near_lane(x, z, d=5):
    for (ax, az), (bx, bz) in zip(LANE, LANE[1:]):
        vx, vz = bx - ax, bz - az
        t = max(0, min(1, ((x - ax) * vx + (z - az) * vz) / float(vx * vx + vz * vz)))
        if math.hypot(x - (ax + vx * t), z - (az + vz * t)) < d:
            return True
    return False


# ---------------------------------------------------------------- the carving engine (tunnels, caverns, crypts)
AIR, FLOOR, STAIR, FORCE = set(), {}, {}, set()
SUPS, RAILS, CAVF, CRYPT = [], {}, [], set()
mine_floor = area.mix({"cobblestone": 3, "gravel": 3, "stone_bricks": 2, "coarse_dirt": 1})
crypt_floor = area.mix({"deepslate_tiles": 3, "polished_deepslate": 2, "cracked_deepslate_tiles": 1, "cobbled_deepslate": 1})
crypt_wall = area.mix({"deepslate_bricks": 6, "cracked_deepslate_bricks": 1, "polished_deepslate": 1, "deepslate_tiles": 1})


def disc(half):
    return [(dx, dz) for dx in range(-half, half + 1) for dz in range(-half, half + 1) if dx * dx + dz * dz <= half * half + 1]


def carve(pts, half=2, h=4, floor=mine_floor, every=5, rails=False):
    seen = {}
    acc = 0.0
    nxt = 0.0
    for (x1, f1, z1), (x2, f2, z2) in zip(pts, pts[1:]):
        L = math.hypot(x2 - x1, z2 - z1)
        n = max(1, int(L * 2))
        ux, uz = (x2 - x1) / L, (z2 - z1) / L
        for i in range(n + 1):
            t = i / n
            cx, cy, cz = x1 + (x2 - x1) * t, f1 + (f2 - f1) * t, z1 + (z2 - z1) * t
            F, bx, bz = int(round(cy)), int(round(cx)), int(round(cz))
            for dx, dz in disc(half):
                seen.setdefault((bx + dx, bz + dz), F)
            if acc + L * t >= nxt:
                nxt += every
                SUPS.append((bx, bz, F, "z" if abs(ux) >= abs(uz) else "x", half, h, len(SUPS)))
            if rails and f1 == f2:
                RAILS[(bx, F + 1, bz)] = "north_south" if abs(uz) > abs(ux) else "east_west"
        acc += L
    for (x, z), F in seen.items():
        for y in range(F + 1, F + h + 1):
            AIR.add((x, y, z))
        FLOOR[(x, F, z)] = floor(x, F, z)


def cavern(cx, F, cz, rx, rz, hh, seed, pillars=3):
    cells = []
    for x in range(cx - rx - 2, cx + rx + 3):
        for z in range(cz - rz - 2, cz + rz + 3):
            r = math.hypot((x - cx) / rx, (z - cz) / rz) + 0.18 * noise(x, z, seed, 2.5)
            if r >= 1:
                continue
            hgt = int(4 + (hh - 4) * (1 - r * r) ** 0.7)
            for y in range(F + 1, F + hgt + 1):
                AIR.add((x, y, z))
            FLOOR[(x, F, z)] = mine_floor(x, F, z)
            cells.append((x, z, hgt, r))
    for p in range(pillars):
        a = hash01(p, 1, seed) * 6.28
        rr = 0.35 + 0.3 * hash01(p, 2, seed)
        px, pz = int(cx + math.cos(a) * rx * rr), int(cz + math.sin(a) * rz * rr)
        for dx in (0, 1):
            for dz in (0, 1):
                for y in range(F + 1, F + hh + 3):
                    AIR.discard((px + dx, y, pz + dz))
                    FORCE.add((px + dx, y, pz + dz))
    CAVF.extend((x, F, z, hgt, r) for (x, z, hgt, r) in cells)


def rect(x1, z1, x2, z2, F, h=4):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(F + 1, F + h + 1):
                AIR.add((x, y, z))
                CRYPT.add((x, y, z))
            FLOOR[(x, F, z)] = crypt_floor(x, F, z)


def flight(x, F, z, n, facing, width=3):
    d = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    side = (d[1], d[0])
    opp = {"north": "south", "south": "north", "east": "west", "west": "east"}[facing]
    for i in range(n):
        for s in range(-(width // 2), width // 2 + 1):
            bx, bz = x + d[0] * i + side[0] * s, z + d[1] * i + side[1] * s
            y = F - i
            blk = "stone_brick_stairs" if i < 7 else "deepslate_brick_stairs"
            STAIR[(bx, y, bz)] = f"minecraft:{blk}[facing={opp},half=bottom]"
            FLOOR[(bx, y - 1, bz)] = "minecraft:stone_bricks" if i < 7 else "minecraft:deepslate_bricks"
            for k in range(1, 4):
                AIR.add((bx, y + k, bz))


# ---- the mine: tunnels and caverns
T1 = [(5, 77, -166), (5, 77, -172), (-1, 76, -175), (-7, 73, -178), (-12, 71, -181)]
T2 = [(-12, 71, -181), (-4, 70, -186), (2, 68, -189), (8, 66, -186)]
T3 = [(8, 66, -186), (14, 66, -186), (20, 67, -189), (25, 68, -188), (30, 69, -184)]
T4 = [(5, 77, -172), (13, 77, -174), (20, 75, -178), (25, 72, -181), (30, 69, -184)]
T5 = [(-12, 71, -181), (-18, 71, -182), (-23, 71, -183), (-26, 71, -183)]
for T, r in ((T1, False), (T2, True), (T3, True), (T4, True), (T5, True)):
    carve(T, half=2, h=4, rails=r)
cavern(-12, 71, -181, 9, 8, 11, 31, pillars=2)
cavern(8, 66, -186, 11, 8, 14, 32, pillars=3)
cavern(30, 69, -184, 8, 7, 10, 33, pillars=2)
# The entrance alcove, cut into the mountain face.
for x in range(0, 11):
    for z in range(-166, -159):
        for y in range(G + 1, G + 8):
            AIR.add((x, y, z))
        FLOOR[(x, G, z)] = "minecraft:stone_bricks"
# The fairy-soul chamber: a sealed pocket north of the big cavern (mine through the stone to reach it).
for x in range(5, 12):
    for z in range(-203, -197):
        for y in range(67, 71):
            AIR.add((x, y, z))
        FLOOR[(x, 66, z)] = mine_floor(x, 66, z)
# ---- the Hub Crypts
flight(14, 66, -181, 11, "south")                 # mine cavern -> landing
rect(12, -170, 16, -166, 55)                      # landing
flight(11, 55, -168, 11, "west")                  # landing -> crypt hall
ROOMS = {"H": (-14, -173, -6, -163), "R1": (-14, -183, -8, -177), "R2": (-6, -182, 0, -176),
         "R3": (-14, -157, -8, -151), "R4": (-4, -160, 6, -150)}
for (x1, z1, x2, z2) in ROOMS.values():
    rect(x1, z1, x2, z2, 44, 5)
for c in ((-5, -169, 0, -167), (-12, -176, -10, -174), (-12, -162, -10, -158), (-3, -175, -1, -170),
          (-3, -166, -1, -161), (-7, -155, -5, -153)):
    rect(c[0], c[1], c[2], c[3], 44, 4)

for (x, y, z) in list(STAIR):
    AIR.discard((x, y, z))


def coal(x, y, z):
    return ((hash01(x // 3, y // 3, z // 3, 5) < 0.38 and hash01(x, y, z, 6) < 0.62) or hash01(x, y, z, 7) < 0.04)


def wall(x, y, z):
    if y >= 58:
        return "minecraft:coal_ore" if coal(x, y, z) else "minecraft:stone"
    return crypt_wall(x, y, z)


def solid_here(c):
    b = area.get(*c)
    return b is not None and b != "minecraft:air"


for c in AIR:
    if solid_here(c) and c not in STAIR:
        area.set(*c, "minecraft:air")
# Walls first (a one-block shell round everything carved, only where there is rock), then floors and stairs.
done = set()
for (x, y, z) in AIR:
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, y + dy, z + dz)
                if n in done or n in AIR or n in FLOOR or n in STAIR:
                    continue
                done.add(n)
                if solid_here(n):
                    area.set(*n, wall(*n))
for c in FORCE:
    area.set(*c, wall(*c))
for c, b in FLOOR.items():
    if c not in AIR:
        area.set(*c, b)
for c, b in STAIR.items():
    area.set(*c, b)

# ---------------------------------------------------------------- supports, lanterns, rails
for (bx, bz, F, axis, half, h, k) in SUPS:
    for s in (-half, half):
        for y in range(F + 1, F + h):
            c = (bx, y, bz + s) if axis == "z" else (bx + s, y, bz)
            if c in AIR:
                area.set(*c, "minecraft:oak_log[axis=y]")
    for s in range(-half, half + 1):
        c = (bx, F + h, bz + s) if axis == "z" else (bx + s, F + h, bz)
        if c in AIR:
            area.set(*c, f"minecraft:spruce_log[axis={axis}]")
    if k % 2 == 0 and (bx, F + h - 1, bz) in AIR:
        area.set(bx, F + h - 1, bz, "minecraft:lantern[hanging=true]")
for (x, y, z), shape in RAILS.items():
    if (x, y, z) in AIR and (x, y - 1, z) in FLOOR:
        area.set(x, y, z, f"minecraft:rail[shape={shape}]")
for z in range(-172, -149):
    area.set(5, G + 1, z, "minecraft:rail[shape=north_south]")
area.set(5, G + 1, -149, "minecraft:stone_brick_wall")
area.set(5, G + 2, -149, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- cavern dressing
for (x, F, z, hgt, r) in CAVF:
    k = hash01(x, 31, z, 2)
    if (x, F + 1, z) not in AIR:
        continue
    if k < 0.025 and r > 0.35:
        area.set(x, F + 1, z, "minecraft:cobblestone_slab[type=bottom]")
    elif k < 0.04 and r > 0.5:
        area.set(x, F + 1, z, "minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]")
    elif k < 0.05 and r > 0.5:
        area.set(x, F + 1, z, "minecraft:coal_block")
    top = F + hgt
    if hash01(x, 32, z, 2) < 0.025 and (x, top, z) in AIR:
        area.set(x, top, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]")
    elif hash01(x, 33, z, 2) < 0.006 and (x, top, z) in AIR:
        area.set(x, top, z, "minecraft:lantern[hanging=true]")
    elif hash01(x, 34, z, 2) < 0.02 and r > 0.8 and (x, top, z) in AIR:
        area.set(x, top, z, "minecraft:cobweb")

# ---------------------------------------------------------------- the entrance frame (spruce and oak, lanterns)
for x in (1, 9):
    area.fill(x, G + 1, -160, x, G + 5, -160, "minecraft:spruce_log[axis=y]")
    area.set(x, G, -160, "minecraft:stone_bricks")
area.fill(1, G + 6, -160, 9, G + 6, -160, "minecraft:spruce_log[axis=x]")
area.fill(0, G + 7, -160, 10, G + 7, -160, "minecraft:oak_planks")
area.fill(-1, G + 8, -160, 11, G + 8, -160, "minecraft:spruce_slab[type=bottom]")
area.set(2, G + 5, -160, "minecraft:spruce_stairs[facing=east,half=top]")
area.set(8, G + 5, -160, "minecraft:spruce_stairs[facing=west,half=top]")
for x in (4, 6):
    area.set(x, G + 5, -160, "minecraft:lantern[hanging=true]")
for x in (3, 7):
    area.fill(x, G + 1, -164, x, G + 3, -164, "minecraft:oak_log[axis=y]")
area.fill(2, G + 4, -164, 8, G + 4, -164, "minecraft:spruce_log[axis=x]")
for x in (-1, 11):
    area.fill(x, G + 1, -160, x, G + 3, -160, "minecraft:stone_bricks")
    area.set(x, G + 4, -160, "minecraft:lantern[hanging=false]")


def sign(x, y, z, rot, l2, l3):
    area.set(x, y, z, f"minecraft:spruce_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": ['""', f'"{l2}"', f'"{l3}"', '""'],
                                                     "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


sign(9, G + 1, -155, 8, "Coal Mine", "Mining skill")
# Plaza dressing: lantern posts, an ore pile, barrels.
for (x, z) in ((-3, -152), (13, -152), (-8, -142), (18, -142), (0, -130), (11, -130)):
    if area.surface_y(x, z) == G:
        area.fill(x, G + 1, z, x, G + 3, z, "minecraft:spruce_fence")
        area.set(x, G + 4, z, "minecraft:lantern[hanging=false]")
for (x, z, k) in ((-2, -157, 3), (-3, -157, 2), (-2, -156, 2), (12, -157, 3), (13, -157, 2)):
    area.fill(x, G + 1, z, x, G + k, z, "minecraft:coal_block")
for (x, z) in ((11, -156), (-2, -155), (-3, -154)):
    area.set(x, G + 1, z, "minecraft:barrel[facing=up]")

# ---------------------------------------------------------------- the Gold Mine gate
GX = -27
for z in (-185, -181):
    area.fill(GX, 72, z, GX, 75, z, "minecraft:stone_bricks")
    area.set(GX, 76, z, "minecraft:chiseled_stone_bricks")
for z in (-184, -183, -182):
    area.fill(GX, 72, z, GX, 74, z, "minecraft:iron_bars")
    area.set(GX, 75, z, "minecraft:gold_block")
    area.fill(GX - 1, 72, z, GX - 1, 74, z, "minecraft:raw_gold_block" if z == -183 else "minecraft:stone_bricks")
    area.fill(GX - 2, 71, z, GX - 2, 75, z, "minecraft:stone_bricks")
area.set(GX + 1, 72, -185, "minecraft:soul_lantern[hanging=false]")
area.set(GX + 1, 72, -181, "minecraft:soul_lantern[hanging=false]")
area.set(GX + 2, 72, -183, "minecraft:dark_oak_sign[rotation=12]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"Gold Mine"', '"(coming soon)"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.spots["notes"].append(f"Gold Mine gate at ({GX}, 72, -183), sealed (Mining I, coming soon)")

# ---------------------------------------------------------------- crypt dressing
cand = "minecraft:candle[candles=2,lit=false]"
for name, (x1, z1, x2, z2) in ROOMS.items():
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    for z, out in ((z1, z1 - 1), (z2, z2 + 1)):
        for x in range(x1 + 1, x2 - 1, 3):
            if any((xx, 46, out) in AIR for xx in (x, x + 1)) or any((xx, 46, z) in AIR and (xx, 45, out) in AIR for xx in ()):
                continue
            area.set(x, 45, z, "minecraft:deepslate_brick_slab[type=bottom]")
            area.set(x + 1, 45, z, "minecraft:deepslate_brick_slab[type=bottom]")
            area.set(x, 46, z, f"minecraft:skeleton_skull[rotation={int(hash01(x, 3, z) * 16)}]")
            area.set(x + 1, 46, z, cand)
    area.set(cx, 49, cz, "minecraft:soul_lantern[hanging=true]")
    area.set(x1, 49, z1, "minecraft:cobweb")
    area.set(x2, 49, z2, "minecraft:cobweb")
    for (x, z) in ((x1, z2), (x2, z1)):
        area.set(x, 46, z, "minecraft:bone_block[axis=y]")
        area.set(x, 47, z, "minecraft:soul_lantern[hanging=false]")
# The Golden Ghoul's lair: gold at the corners and a stone-brick throne.
for (x, z) in ((-3, -159), (5, -159), (-3, -151), (5, -151)):
    area.fill(x, 45, z, x, 47, z, "minecraft:gold_block")
area.set(1, 45, -151, "minecraft:chiseled_stone_bricks")
area.set(1, 46, -151, "minecraft:gold_block")
# Corridors: soul lanterns, webs, the odd candle.
for (x, y, z) in sorted(CRYPT):
    if y == 48 and (x, y, z) in AIR and (x, 49, z) not in AIR and hash01(x, 2, z, 41) < 0.05:
        area.set(x, y, z, "minecraft:soul_lantern[hanging=true]")
    elif y == 45 and hash01(x, 5, z, 42) < 0.012 and (x, 46, z) in AIR:
        area.set(x, y, z, cand)
    elif y == 48 and hash01(x, 6, z, 43) < 0.03 and (x, 49, z) not in AIR:
        area.set(x, y, z, "minecraft:cobweb")
area.set(-11, 45, -182, "minecraft:candle[candles=3,lit=true]")

# ---------------------------------------------------------------- the trail up the mountain
trail_pts = [(24, -146), (36, -160), (40, -178), (36, -195), (24, -205), (8, -203), (-6, -196), (-10, -184), (-2, -180), (SX, SZ)]
total = sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(trail_pts, trail_pts[1:]))
S_END = SH + 1
cells, acc = {}, 0.0
for (x1, z1), (x2, z2) in zip(trail_pts, trail_pts[1:]):
    L = math.hypot(x2 - x1, z2 - z1)
    n = int(L * 2)
    for i in range(n + 1):
        t = i / n
        cx, cz = x1 + (x2 - x1) * t, z1 + (z2 - z1) * t
        L2 = int(round(2 * (G + 1 + (S_END - G - 1) * (acc + L * t) / total)))
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx * dx + dz * dz <= 2:
                    cells.setdefault((int(round(cx)) + dx, int(round(cz)) + dz), L2)
    acc += L
for (x, z), lv in cells.items():
    if (x, z) not in cols:
        continue
    yb = lv // 2 - 1 if lv % 2 == 0 else (lv - 1) // 2
    fam = hash01(x, 0, z, 8) < 0.7
    for y in range(G, yb):
        area.set(x, y, z, "minecraft:andesite")
    if lv % 2 == 0:
        area.set(x, yb, z, "minecraft:stone_bricks" if fam else "minecraft:cobblestone")
    else:
        area.set(x, yb - 0, z, "minecraft:stone_brick_slab[type=bottom]" if fam else "minecraft:cobblestone_slab[type=bottom]")
        area.set(x, yb - 1, z, "minecraft:stone_bricks" if fam else "minecraft:cobblestone")
    for y in range(yb + 1, yb + 5):
        area.set(x, y, z, "minecraft:air")
    area.top[(x, z)] = yb
# Lanterns along the trail.
for i, ((x, z), lv) in enumerate(sorted(cells.items())):
    if hash01(x, 4, z, 11) < 0.012:
        yb = lv // 2 - 1 if lv % 2 == 0 else (lv - 1) // 2
        for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            nb = (x + dx, z + dz)
            if nb in cols and nb not in cells and area.top.get(nb, 0) >= yb:
                area.set(x + dx, area.top[nb] + 1, z + dz, "minecraft:cobblestone_wall")
                area.set(x + dx, area.top[nb] + 2, z + dz, "minecraft:lantern[hanging=false]")
                break

# ---------------------------------------------------------------- the hilltop
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if dx * dx + dz * dz <= 5:
            for y in range(SH - 3, SH):
                area.set(SX + dx, y, SZ + dz, "minecraft:calcite")
            area.set(SX + dx, SH, SZ + dz, "minecraft:calcite" if abs(dx) + abs(dz) < 3 else "minecraft:polished_andesite")
            for y in range(SH + 1, SH + 5):
                area.set(SX + dx, y, SZ + dz, "minecraft:air")
            area.top[(SX + dx, SZ + dz)] = SH
for (dx, dz) in ((2, 0), (-2, 0), (0, 2), (0, -2)):
    area.fill(SX + dx, SH + 1, SZ + dz, SX + dx, SH + 2, SZ + dz, "minecraft:cobblestone_wall")
    area.set(SX + dx, SH + 3, SZ + dz, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- trees, grass, rocks on the open ground
plateau = {c for c in cols if area.top.get(c) in (G, G + 1, G + 2) and not in_plaza(*c) and not near_lane(*c)
           and math.hypot(c[0] - MCX, (c[1] - MCZ) * 1.2) > 32}
for (x, z) in sorted(plateau):
    y = area.surface_y(x, z)
    if hash01(x, 11, z, 4) < 0.012 and (area.get(x, y, z) or "").endswith("grass_block") and area.get(x, y + 1, z) in (None, "minecraft:air"):
        area.spruce(x, y + 1, z, height=7 + int(hash01(x, 12, z) * 4), seed=x + z)


def ground_bits(x, z):
    r = hash01(x, 21, z)
    return "short_grass" if r < 0.55 else "fern" if r < 0.8 else "dead_bush" if r < 0.9 else "brown_mushroom"


area.scatter(plateau, 0.2, ground_bits, seed=6)
for (x, z) in sorted(plateau):
    if hash01(x, 13, z, 5) < 0.006:
        y = area.surface_y(x, z)
        for dx, dz, k in ((0, 0, 2), (1, 0, 1), (0, 1, 1)):
            area.set(x + dx, y + 1, z + dz, "minecraft:andesite" if k == 2 else "minecraft:cobblestone")

# ---------------------------------------------------------------- Fairy Souls, the warp
area.fairy_soul(-11, 45, -182)             # in the crypts, the far burial room
area.fairy_soul(SX, SH + 1, SZ)            # on the summit
area.fairy_soul(8, 67, -200)               # a sealed pocket behind the big cavern: mine through the stone
area.warp("Coal Mine", 5, G + 1, -153, yaw=180.0)

# ---------------------------------------------------------------- the zone
CRYPT_BOX = ((-15, 44, -184), (9, 50, -149))
area.zone("Coal Mine", box=((-50, 28, -232), (62, 112, -100)), blocks={
    "minecraft:coal_ore": {"becomes": "minecraft:bedrock", "regrow": 6},
    "minecraft:stone": {"becomes": "minecraft:bedrock", "regrow": 4}}, mobs=[
    {"type": "minecraft:zombie", "name": "Crypt Ghoul", "indoors": True, "level": 30, "health": 2000, "damage": 350, "xp": 36, "coins": 13,
     "max": 8, "every": 4, "from": list(CRYPT_BOX[0]), "to": list(CRYPT_BOX[1])},
    {"type": "minecraft:zombie", "name": "Golden Ghoul", "indoors": True, "level": 50, "health": 45000, "damage": 800, "xp": 50, "coins": 100,
     "max": 1, "every": 60, "from": list(CRYPT_BOX[0]), "to": list(CRYPT_BOX[1])}])
area.spots["notes"].append(f"Mine entrance (5, 78, -160) facing south; summit ({SX}, {SH}, {SZ}); Hub Crypts floors y44, "
                           "landing y55; Golden Ghoul room (-4..6, -160..-150)")
area.save()
