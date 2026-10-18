#!/usr/bin/env python3
"""The Rift, Living Cave (x 0..69, z -240..-121): a rocky hill with a big blue-tinted cavern inside (lapis ore veins in the
walls, sea lanterns, glow lichen, two glass "Living Metal snakes" lying on the floor) and, below it, Living Stillness: a frozen
sub-cave (snow, powder snow pits, ice spikes) reached by a stair ramp down from the cavern floor.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_living_cave.py

South (z -121) faces the Village Plaza's Deja Vu Alley: a path of floating islets leads north from its last islets to a
peninsula and the cave's south mouth. East (x 69/70) meets the Colosseum: the surface stays at y 68 within SEAM of x 69
around z -198..-162, with a 5-wide crossing path at z -180 leading west to the cave's east mouth. The west and north sides
and the rest of the east and south sides are the Rift's outer edge (broken cliffs, hanging underside).
"""
import math

from kit import hash01, noise
from rift_lib import new_area, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("living_cave", version=1)
area.shell = 4
S = area.set
G = GROUND
X0, X1, Z0, Z1 = 0, 69, -240, -121
CROSS_Z = CROSSINGS[("living_cave", "colosseum")][2]            # -180
assert CROSSINGS[("living_cave", "colosseum")][:2] == ("x", 70) and CROSS_Z == -180
AIR = "minecraft:air"

CAVE_FLOOR = 62                       # cavern floor block y (players stand at 63)
STILL_FLOOR = 42                      # Living Stillness floor block y (players stand at 43)
SOUL_CAVE = (38, -197)
SOUL_STILL = (3, -203)
PLUG = (22, -219)                     # the snow block over Montezuma's piece 8 (y 43)
ROCK_NPC = (47, -211)
CHIP_NPC = (23, -194)
CART = (21, -192)                     # mining cart track runs along x at z -192


def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def jag(a, b, s):
    return 5 * noise(a, b, s, 1.2) + 2.5 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 1.2 * (hash01(int(a), 2, int(b), s) - 0.5)


# ---------------------------------------------------------------- the outline
def east_cliff(z):
    d = max(0.0, -198 - z, z + 162)
    return 11 * sm(d / 12.0)


def edge(x, z):
    """Distance in blocks to the Rift's outer edge (negative = void)."""
    wx = 2 + jag(z, 0, 1)
    if abs(z + 203) < 16:
        wx = min(wx, -0.5)
    dw = x - wx
    dn = z - (-238 + jag(x, 5, 3))
    c = east_cliff(z)
    de = 69.5 - c - (0.7 * jag(z, 9, 2) if c > 0.3 else 0) - x
    zs = -139 - 10 * sm((abs(x - 43) - 8) / 16.0) + 0.6 * jag(x, 7, 4)
    return min(dw, dn, de, zs - z)


LAND = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z)
        if e >= 0:
            LAND[(x, z)] = e


# ---------------------------------------------------------------- caves: shapes
def qmain(x, z):
    return ((x - 34) / 22.0) ** 2 + ((z + 196) / 30.0) ** 2 + 0.10 * noise(x, z, 61, 1.6)


def qled(x, z):
    return ((x - 9) / 8.5) ** 2 + ((z + 203) / 10.0) ** 2 + 0.08 * noise(x, z, 62, 2.0)


def qstill(x, z):
    return ((x - 26) / 20.0) ** 2 + ((z + 210) / 21.0) ** 2 + 0.10 * noise(x, z, 71, 1.7)


def near(x, z, p, r):
    return math.hypot(x - p[0], z - p[1]) <= r


def keep_cave(x, z):
    """Cavern floor columns kept at exactly y 62 / the ledge slope (spots, mouths, the ramp head)."""
    return (near(x, z, SOUL_CAVE, 6) or near(x, z, ROCK_NPC, 4) or near(x, z, CHIP_NPC, 4) or near(x, z, (CART[0] + 2, CART[1]), 6)
            or (z > -173 and abs(x - 40) < 6) or (x > 49 and abs(z - CROSS_Z) < 5)
            or (30 <= x <= 36 and -206 <= z <= -200))


def keep_still(x, z):
    return near(x, z, PLUG, 5) or (29 <= x <= 37 and -229 <= z <= -218)


def floor_cave(x, z):
    f = int(round(62 + 5 * sm((15 - x) / 9.0)))
    if x >= 18 and not keep_cave(x, z) and noise(x, z, 64, 1.6) > 0.55:
        f += 1
    return f


def ceil_cave(x, z):
    q1, q2 = qmain(x, z), qled(x, z)
    h = 0.0
    if q1 < 1:
        h = 22 * math.sqrt(1 - q1)
    if q2 < 1:
        h = max(h, 9 * math.sqrt(1 - q2))
    return max(5, int(round(h + 1.5 * noise(x, z, 63, 2.0))))


def floor_still(x, z):
    f = STILL_FLOOR
    if not keep_still(x, z):
        n = noise(x, z, 72, 1.8)
        f += 2 if n > 0.75 else 1 if n > 0.42 else 0
    return f


def ceil_still(x, z):
    q = qstill(x, z)
    return max(5, int(round(4 + 9 * math.sqrt(max(0.0, 1 - q)) + 1.2 * noise(x, z, 73, 2.0))))


CAV = {}      # (x, z) -> (floor y, ceiling air height)
STILL = {}
for (x, z), e in LAND.items():
    if e >= 3 and (qmain(x, z) < 1 or qled(x, z) < 1):
        CAV[(x, z)] = (floor_cave(x, z), ceil_cave(x, z))
    if e >= 6 and qstill(x, z) < 1:
        STILL[(x, z)] = (floor_still(x, z), ceil_still(x, z))

# ---------------------------------------------------------------- tunnels (mouths and the ramp)
CARVE = set()          # extra air cells
AIRCELLS = []          # every air cell of the caves (x, y, z, still?) for lining and decoration
STAIRS = []            # (x, y, z, facing, floor y of the support under it)
CORR_COLS = {}         # (x, z) -> roof height needed (top block y)
PROTECT = set()
for x in range(40 - 8, 40 + 9):
    for z in range(-157, -142):
        if abs(x - 40) <= 2:
            PROTECT.add((x, z))


def corridor(sx, sz, dx, dz, length, label):
    lx, lz = (1, 0) if dz else (0, 1)
    up = {(0, -1): "south", (-1, 0): "east"}[(dx, dz)]
    for i in range(length):
        px, pz = sx + dx * i, sz + dz * i
        fy = max(CAVE_FLOOR, G - i)
        for k in range(-2, 3):
            x, z = px + lx * k, pz + lz * k
            for y in range(fy + 1, fy + 6):
                CARVE.add((x, y, z))
                AIRCELLS.append((x, y, z, False))
            CORR_COLS[(x, z)] = max(CORR_COLS.get((x, z), 0), fy + 8)
            STAIRS.append((x, fy, z, up if fy > CAVE_FLOOR else None, None))
    return up


corridor(40, -158, 0, -1, 10, "south mouth")          # x 38..42, z -158..-167
corridor(60, CROSS_Z, -1, 0, 10, "east mouth")        # x 60..51, z -182..-178

# the ramp down into Living Stillness: x 32..34, from z -203 (y 62) north to z -222 (y 43)
RAMP = []
for i in range(20):
    y, z = CAVE_FLOOR - i, -203 - i
    for x in (32, 33, 34):
        for yy in range(y + 1, y + 5):
            CARVE.add((x, yy, z))
            AIRCELLS.append((x, yy, z, True))
        RAMP.append((x, y, z))

# ---------------------------------------------------------------- terrain heights
FLAT = lambda x, z: ((x >= 60 and -200 <= z <= -160) or (abs(x - 40) <= 8 and z > -158) or (38 <= x <= 42 and z > -170)
                     or (x >= 49 and abs(z - CROSS_Z) <= 4))


def hill(x, z):
    q = ((x - 32) / 30.0) ** 2 + ((z + 198) / 42.0) ** 2
    return 0.0 if q >= 1 else 25 * (1 - q) ** 0.8 * sm((61 - x) / 9.0)


T = {}
for (x, z), e in LAND.items():
    t = G
    if not FLAT(x, z):
        b = noise(x, z, 21, 2.2)
        t += 1 if b > 0.45 else 0
        t += int(round(hill(x, z) + 1.5 * noise(x, z, 5, 1.7) * (1 if hill(x, z) > 2 else 0)))
    T[(x, z)] = t

REQ = {}
for (x, z), (f, c) in CAV.items():
    REQ[(x, z)] = max(REQ.get((x, z), 0), f + c + 4)
for (x, z), y in CORR_COLS.items():
    REQ[(x, z)] = max(REQ.get((x, z), 0), y)


def protected(x, z):
    return (x >= 61 and -200 <= z <= -160) or (x, z) in PROTECT


for (x, z), y in sorted(REQ.items()):
    if (x, z) in LAND:
        T[(x, z)] = max(T[(x, z)], y)
for (x, z), y in sorted(REQ.items()):
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            c = (x + dx, z + dz)
            if c in LAND and not protected(*c) and c not in CORR_COLS:
                t = y - max(abs(dx), abs(dz)) - (1 if abs(dx) + abs(dz) > 6 else 0)
                if t > T[c]:
                    T[c] = t

# ---------------------------------------------------------------- materials
ROCK = area.mix({"tuff": 8, "deepslate": 7, "andesite": 2, "calcite": 2, "dripstone_block": 1})
UNDERSIDE = area.mix({"tuff": 6, "deepslate": 5, "calcite": 1, "andesite": 1})
FLAT_SURF = area.mix({"grass_block": 5, "coarse_dirt": 2, "rooted_dirt": 1, "moss_block": 1, "tuff": 1})
HILL_SURF = area.mix({"tuff": 5, "deepslate": 3, "andesite": 2, "calcite": 2, "moss_block": 1, "gravel": 1})
CAVE_FLOOR_MIX = area.mix({"tuff": 4, "calcite": 2, "packed_ice": 2, "light_blue_terracotta": 2, "andesite": 1, "prismarine": 1})
STILL_FLOOR_MIX = area.mix({"packed_ice": 5, "calcite": 3, "tuff": 2, "blue_ice": 1})
PATH = area.mix({"coarse_dirt": 5, "rooted_dirt": 3, "mud_bricks": 3})


def under_depth(x, z, e):
    t = sm(e / 55.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 3 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


def lamp(x, y, z, hgt=3):
    """A blue lantern: a post with a sea lantern wrapped in blue glass."""
    for h in range(hgt):
        S(x, y + h, z, "minecraft:deepslate_brick_wall")
    S(x, y + hgt, z, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, y + hgt, z + dz, "minecraft:blue_stained_glass")
    S(x, y + hgt + 1, z, "minecraft:blue_stained_glass")


# ---------------------------------------------------------------- fill the land
for (x, z), e in sorted(LAND.items()):
    top = T[(x, z)]
    cav, still = CAV.get((x, z)), STILL.get((x, z))
    for y in range(LAND_BOTTOM, top + 1):
        if (x, y, z) in CARVE:
            continue
        if cav and cav[0] < y <= cav[0] + cav[1]:
            continue
        if still and still[0] < y <= still[0] + still[1]:
            continue
        if cav and y == cav[0]:
            S(x, y, z, CAVE_FLOOR_MIX(x, y, z))
        elif still and y == still[0]:
            S(x, y, z, STILL_FLOOR_MIX(x, y, z))
        elif y == top:
            if top <= G + 1 and e >= 2.5:
                S(x, y, z, FLAT_SURF(x, y, z))
            elif e < 2.5 and top <= G + 1:
                S(x, y, z, "minecraft:tuff" if hash01(x, 8, z, 1) < 0.5 else "minecraft:moss_block")
            else:
                S(x, y, z, HILL_SURF(x, y, z))
        elif y > top - 4 and top <= G + 1:
            S(x, y, z, "minecraft:dirt" if hash01(x, y, z, 2) < 0.7 else "minecraft:rooted_dirt")
        else:
            S(x, y, z, ROCK(x, y, z))
    if not (cav or still) or (cav and top > cav[0] + cav[1]):
        area.top[(x, z)] = top
    # the hanging underside (thin at the outer edge, deep inside)
    d = under_depth(x, z, e)
    for k in range(1, d + 1):
        y = LAND_BOTTOM - k
        r = hash01(x, y, z, 12)
        if e < 14 and r < 0.04:
            b = "minecraft:crying_obsidian"
        elif r < 0.03:
            b = "minecraft:amethyst_block"
        elif r < 0.04:
            b = "minecraft:purpur_block"
        else:
            b = UNDERSIDE(x, y, z)
        S(x, y, z, b)
    if e < 30 and hash01(x, 10, z, 13) < 0.012:
        ln = 3 + int(hash01(x, 11, z, 14) * 8)
        yy = LAND_BOTTOM - d
        for i in range(ln):
            S(x, yy - 1 - i, z, "minecraft:crying_obsidian" if hash01(x, i, z, 15) < 0.4 else "minecraft:amethyst_block")
        S(x, yy - 1 - ln, z, "minecraft:amethyst_cluster[facing=down]")

for (x, z), (f, c) in CAV.items():
    for y in range(f + 1, f + c + 1):
        AIRCELLS.append((x, y, z, False))
for (x, z), (f, c) in STILL.items():
    for y in range(f + 1, f + c + 1):
        AIRCELLS.append((x, y, z, True))


def floor_y(x, z):
    """The floor block y of the cave column (cavern, or Stillness when below)."""
    if (x, z) in CAV:
        return CAV[(x, z)][0]
    return None


# ---------------------------------------------------------------- stairs: mouths and ramp
for (x, y, z, up, _) in STAIRS:
    if up:
        S(x, y, z, f"minecraft:tuff_brick_stairs[facing={up},half=bottom]")
        for yy in range(y - 1, y - 3, -1):
            S(x, yy, z, "minecraft:tuff_bricks")
    else:
        if (x, z) not in CAV or CAV[(x, z)][0] != y:
            S(x, y, z, "minecraft:tuff_bricks" if hash01(x, 1, z, 4) < 0.6 else "minecraft:polished_tuff")
for (x, y, z) in RAMP:
    S(x, y, z, "minecraft:tuff_brick_stairs[facing=south,half=bottom]")
    lo = STILL[(x, z)][0] if (x, z) in STILL and y > STILL[(x, z)][0] else None
    base = (lo + 1) if lo is not None else y - 2
    for yy in range(max(base, y - 40), y):
        S(x, yy, z, "minecraft:tuff_bricks" if hash01(x, yy, z, 5) < 0.8 else "minecraft:packed_ice")
# the ramp's exit lands on the Stillness floor
for z in (-223, -224, -225):
    for x in (32, 33, 34):
        if (x, z) in STILL:
            S(x, STILL[(x, z)][0], z, "minecraft:polished_tuff")
# side rails of the ramp pit and lamps
for i in range(0, 20, 4):
    y, z = CAVE_FLOOR - i, -203 - i
    for x in (31, 35):
        if area.get(x, y + 1, z) in (None, AIR) and i >= 5:
            S(x, y + 1, z, "minecraft:deepslate_brick_wall")
    if i >= 8:
        for x in (32, 34):
            S(x, y + 4, z, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- cave lining: a blue tint, then lapis ore
LINED = set()
CAVEN = [c for c in AIRCELLS]


def tint(x, y, z, still):
    r = hash01(x, y, z, 33)
    if still:
        table = [(0.30, "packed_ice"), (0.42, "blue_ice"), (0.70, "calcite"), (0.80, "tuff"), (0.88, "light_blue_terracotta"),
                 (0.94, "white_terracotta"), (1.01, "packed_ice")]
    else:
        table = [(0.16, "calcite"), (0.30, "light_blue_terracotta"), (0.38, "packed_ice"), (0.42, "blue_ice"), (0.46, "prismarine"),
                 (0.52, "cyan_terracotta"), (0.60, "tuff"), (1.01, None)]
    for lim, b in table:
        if r < lim:
            return b
    return None


for (x, y, z, still) in CAVEN:
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        n = (x + dx, y + dy, z + dz)
        if n in LINED or dy == -1:
            continue
        b = area.get(*n)
        if b is None or b == AIR or n in CARVE:
            continue
        if b.endswith(("stairs[facing=south,half=bottom]", "stairs[facing=east,half=bottom]")) or b.endswith("tuff_bricks"):
            continue
        LINED.add(n)
        t = tint(n[0], n[1], n[2], still)
        if t and not b.endswith(("tuff_brick_stairs", "polished_tuff")):
            S(n[0], n[1], n[2], "minecraft:" + t)

# lapis ore veins in the walls (both zones; the zone makes them breakable)
lapis = 0
for n in sorted(LINED):
    if hash01(n[0], n[1], n[2], 91) < 0.011:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    m = (n[0] + dx, n[1] + dy, n[2] + dz)
                    if (m in LINED or (dx == dy == dz == 0)) and hash01(m[0], m[1], m[2], 92) < 0.6:
                        b = area.get(*m)
                        if b and b != AIR and not b.endswith(("stairs[facing=south,half=bottom]", "sea_lantern")):
                            S(m[0], m[1], m[2], "minecraft:lapis_ore")
                            lapis += 1


def lichen(x, y, z, p=0.9):
    """Glow lichen in an air cell: faces on the solid sides."""
    faces = []
    for name, (dx, dy, dz) in (("up", (0, 1, 0)), ("down", (0, -1, 0)), ("north", (0, 0, -1)), ("south", (0, 0, 1)),
                               ("east", (1, 0, 0)), ("west", (-1, 0, 0))):
        if area.solid(x + dx, y + dy, z + dz) and hash01(x + dx, y + dy, z + dz, 77) < p:
            faces.append(name)
    if faces and area.get(x, y, z) in (None, AIR):
        S(x, y, z, "minecraft:glow_lichen[" + ",".join(f"{f}=true" for f in faces[:3]) + "]")


def pod(x, y, z, glass="blue_stained_glass"):
    """A sea lantern wrapped in tinted glass where its neighbours are air."""
    S(x, y, z, "minecraft:sea_lantern")
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
        if area.get(x + dx, y + dy, z + dz) in (None, AIR):
            S(x + dx, y + dy, z + dz, "minecraft:" + glass)


# ---------------------------------------------------------------- cavern dressing: lights, lichen, stalactites
reserved = set()


def reserve(cx, cz, r):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            reserved.add((cx + dx, cz + dz))


for p in (SOUL_CAVE, ROCK_NPC, CHIP_NPC, (CART[0] + 3, CART[1])):
    reserve(p[0], p[1], 3)
reserve(50, -186, 3)
reserve(21, -174, 3)
for x in range(14, 33):
    for z in range(-195, -189):
        reserved.add((x, z))
reserve(33, -203, 4)
for x in range(30, 37):
    for z in range(-208, -199):
        reserved.add((x, z))
for (x, z), (f, c) in sorted(CAV.items()):
    top_air = f + c
    h = hash01(x, 6, z, 35)
    if h < 0.014 and c > 9:
        pod(x, top_air, z)
        S(x, top_air - 1, z, "minecraft:blue_stained_glass")
    elif h < 0.09 and c > 5:
        lichen(x, top_air, z, 0.9)
    elif h < 0.145 and c > 7 and (x, z) not in reserved:
        ln = 1 + int(hash01(x, 7, z, 36) * 4)
        for i in range(ln):
            t = "tip" if i == ln - 1 else "base" if i == 0 and ln > 1 else "middle"
            if ln == 1:
                t = "tip"
            S(x, top_air - i, z, f"minecraft:pointed_dripstone[vertical_direction=down,thickness={t}]")
    # wall lights near the rim and floor decoration
    rim = any((x + dx, z + dz) not in CAV for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if rim and hash01(x, 8, z, 38) < 0.14:
        yy = f + 2 + int(hash01(x, 9, z, 38) * 5)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) not in CAV and area.solid(x + dx, yy, z + dz) and area.get(x, yy, z) in (None, AIR):
                S(x + dx, yy, z + dz, "minecraft:sea_lantern" if hash01(x, yy, z, 3) < 0.35 else "minecraft:blue_ice")
                break
    if (x, z) not in reserved and hash01(x, 11, z, 39) < 0.007:
        ln = 1 + int(hash01(x, 12, z, 39) * 3)
        for i in range(ln):
            t = "tip" if i == ln - 1 else "base" if i == 0 else "middle"
            S(x, f + 1 + i, z, f"minecraft:pointed_dripstone[vertical_direction=up,thickness={t}]")
    elif (x, z) not in reserved and hash01(x, 13, z, 40) < 0.06 and area.get(x, f + 1, z) in (None, AIR):
        S(x, f + 1, z, "minecraft:glow_lichen[down=true]")
    elif (x, z) not in reserved and hash01(x, 14, z, 41) < 0.012:
        S(x, f + 1, z, "minecraft:amethyst_cluster[facing=up]" if hash01(x, 15, z, 41) < 0.3 else "minecraft:light_blue_candle[candles=2,lit=true]")

# prismarine pillars around the hall
pillars = []
for k in range(9):
    a = k * 2 * math.pi / 9 + 0.3
    x, z = int(round(34 + 17 * math.cos(a))), int(round(-196 + 24 * math.sin(a)))
    if (x, z) not in CAV or (x, z) in reserved or any(near(x, z, q, 7) for q in pillars):
        continue
    f, c = CAV[(x, z)]
    pillars.append((x, z))
    for dy in range(1, 9):
        b = "dark_prismarine" if dy % 3 == 0 else "prismarine_bricks"
        S(x, f + dy, z, "minecraft:" + b)
    S(x, f + 9, z, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, f + 9, z + dz, "minecraft:blue_stained_glass")
    S(x, f + 10, z, "minecraft:blue_stained_glass")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, f + 1, z + dz, "minecraft:prismarine_brick_stairs[facing=" + {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)] + ",half=bottom]")
    reserve(x, z, 2)

# ---------------------------------------------------------------- the glass snakes (Living Metal snakes)
HEAD_CELLS = []


def snake(hx, hz, ang, length, amp, wave, label):
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    pts = []
    for i in range(length * 2 + 1):
        t = i / 2.0
        off = amp * math.sin(t * wave)
        pts.append((hx + t * ca - off * sa, hz + t * sa + off * ca, t))
    placed = 0
    for (px, pz, t) in pts:
        r = 1.6 - 0.9 * sm((t - length * 0.55) / (length * 0.45))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                x, z = int(round(px)) + dx, int(round(pz)) + dz
                if (x, z) not in CAV or any(near(x, z, q, 3.5) for q in (SOUL_CAVE, ROCK_NPC, CHIP_NPC)) or abs(z - CART[1]) <= 1 or x < 18:
                    continue
                d = math.hypot(x - px, z - pz)
                f = CAV[(x, z)][0]
                if d <= r:
                    S(x, f + 1, z, "minecraft:blue_stained_glass")
                    if d <= r - 0.8:
                        S(x, f + 2, z, "minecraft:blue_stained_glass")
                    if int(t) % 3 == 0 and d <= 0.8:
                        S(x, f, z, "minecraft:sea_lantern")
                    placed += 1
    # the head: a lapis block with sea lantern eyes
    hx_, hz_ = int(round(hx)), int(round(hz))
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            x, z = hx_ + dx, hz_ + dz
            if (x, z) in CAV:
                f = CAV[(x, z)][0]
                S(x, f + 1, z, "minecraft:lapis_block")
                S(x, f + 2, z, "minecraft:lapis_block" if abs(dx) + abs(dz) < 2 else AIR)
    fx, fz = int(round(hx - 2 * ca)), int(round(hz - 2 * sa))
    if (fx, fz) in CAV:
        f = CAV[(fx, fz)][0]
        S(fx, f + 1, fz, "minecraft:sea_lantern")
    HEAD_CELLS.append((hx_, hz_, label, placed))


snake(50, -186, 180, 25, 3.0, 0.3, "A")
snake(21, -174, 22, 24, 2.5, 0.35, "B")

# ---------------------------------------------------------------- spots in the cavern
# the Living Cave soul: a dais of lights around the spot (the cell itself stays clear)
sx, sz = SOUL_CAVE
sf = CAV[SOUL_CAVE][0]
assert sf == CAVE_FLOOR
for dx in range(-3, 4):
    for dz in range(-3, 4):
        r = math.hypot(dx, dz)
        if 1.5 <= r < 3.2 and (sx + dx, sz + dz) in CAV:
            S(sx + dx, sf, sz + dz, "minecraft:dark_prismarine" if (dx + dz) % 2 else "minecraft:prismarine_bricks")
for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
    S(sx + dx, sf + 1, sz + dz, "minecraft:prismarine_wall")
    S(sx + dx, sf + 2, sz + dz, "minecraft:prismarine_wall")
    pod(sx + dx, sf + 3, sz + dz)
for dy in range(1, 5):
    area.clear(sx, sf + dy, sz)

# the Living Stillness soul on the west ledge
ex, ez = SOUL_STILL
ef = CAV[SOUL_STILL][0]
assert ef == 67, ef
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if (ex + dx, ez + dz) in CAV and 1.2 <= math.hypot(dx, dz) < 2.8:
            S(ex + dx, ef, ez + dz, "minecraft:dark_prismarine" if (dx + dz) % 2 else "minecraft:polished_tuff")
for dz in (-3, 3):
    S(ex + 1, ef + 1, ez + dz, "minecraft:prismarine_wall")
    S(ex + 1, ef + 2, ez + dz, "minecraft:prismarine_wall")
    pod(ex + 1, ef + 3, ez + dz)
for dy in range(1, 5):
    area.clear(ex, ef + dy, ez)
reserve(ex, ez, 3)

# Rock: a corner of boulders in the north-east of the cavern
rx, rz = ROCK_NPC
for (cx, cz, r, hgt) in ((50, -214, 2.8, 4), (52, -209, 2.4, 3), (49, -218, 2.6, 5), (53, -213, 2.0, 5), (46, -216, 1.8, 3)):
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            x, z = cx + dx, cz + dz
            if (x, z) not in CAV or math.hypot(x - rx, z - rz) < 2.2:
                continue
            d = math.hypot(dx, dz) / r
            if d > 1.05:
                continue
            f = CAV[(x, z)][0]
            for y in range(f, f + int(hgt * math.sqrt(max(0.0, 1.0 - d * d * 0.8)) + 1)):
                S(x, y, z, ["minecraft:cobbled_deepslate", "minecraft:tuff", "minecraft:andesite", "minecraft:smooth_basalt"][int(hash01(x, y, z, 5) * 4)])
f = CAV[(rx, rz)][0]
S(rx + 2, f + 1, rz, "minecraft:lantern[hanging=false]")
S(rx + 2, f, rz, "minecraft:polished_deepslate")
S(rx - 1, f + 1, rz + 1, "minecraft:lapis_ore") if False else None
S(rx - 1, f + 1, rz - 1, "minecraft:cobbled_deepslate_slab[type=bottom]")
for dy in range(1, 5):
    area.clear(rx, f + dy, rz)

# Chipper McPickaxe and his mining cart (a track along x at z -192)
cz = CART[1]
for x in range(16, 31):
    if (x, cz) in CAV:
        f = CAV[(x, cz)][0]
        S(x, f, cz, "minecraft:gravel" if x % 4 else "minecraft:polished_deepslate")
        S(x, f + 1, cz, "minecraft:rail[shape=east_west]")
for x in (CART[0], CART[0] + 1):
    f = CAV[(x, cz)][0]
    S(x, f + 1, cz, "minecraft:cauldron")
    S(x, f + 2, cz, "minecraft:lapis_block" if x == CART[0] else AIR)
S(CART[0] + 1, CAV[(CART[0] + 1, cz)][0] + 2, cz, "minecraft:blue_ice")
f = CAV[(18, cz)][0]
S(17, CAV[(17, cz)][0] + 1, cz, "minecraft:polished_deepslate_slab[type=bottom]")
S(17, CAV[(17, cz)][0] + 2, cz, "minecraft:lantern[hanging=false]")
cx_, cz_ = CHIP_NPC
f = CAV[CHIP_NPC][0]
S(cx_ - 2, f + 1, cz_, "minecraft:grindstone[face=floor,facing=north]")
S(cx_ + 2, f + 1, cz_, "minecraft:barrel[facing=up]")
S(cx_ + 2, f + 2, cz_, "minecraft:lantern[hanging=false]")
for dy in range(1, 5):
    area.clear(cx_, f + dy, cz_)

# ---------------------------------------------------------------- Living Stillness: snow, powder snow, ice spikes
STILL_KEEP = set()
for dx in range(-4, 5):
    for dz in range(-4, 5):
        STILL_KEEP.add((PLUG[0] + dx, PLUG[1] + dz))
for x in range(30, 38):
    for z in range(-232, -214):
        STILL_KEEP.add((x, z))
for x in range(29, 39):
    for z in range(-213, -200):
        STILL_KEEP.add((x, z))
skeys = sorted(STILL)

# powder snow pits (two blocks deep)
pits = []
for i in range(60):
    x = 8 + int(hash01(i, 0, 0, 81) * 38)
    z = -230 + int(hash01(i, 1, 0, 81) * 40)
    if len(pits) >= 7 or (x, z) not in STILL or any(near(x, z, p, 9) for p in pits) or (x, z) in STILL_KEEP:
        continue
    r = 1.4 + hash01(i, 2, 0, 81) * 0.9
    if not all((x + dx, z + dz) in STILL and (x + dx, z + dz) not in STILL_KEEP for dx in (-3, 3) for dz in (-3, 3)):
        continue
    pits.append((x, z))
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            c = (x + dx, z + dz)
            if c not in STILL:
                continue
            d = math.hypot(dx, dz)
            fy = STILL[c][0]
            if d <= r:
                S(c[0], fy, c[1], "minecraft:powder_snow")
                S(c[0], fy - 1, c[1], "minecraft:powder_snow")
                S(c[0], fy - 2, c[1], "minecraft:packed_ice")
                S(c[0], fy + 1, c[1], AIR)
            elif d <= r + 1.2:
                S(c[0], fy, c[1], "minecraft:packed_ice")
                STILL_KEEP.add(c)
    STILL_KEEP.update((x + dx, z + dz) for dx in range(-3, 4) for dz in range(-3, 4))

# ice spikes up from the floor, icicles down from the ceiling
spikes = 0
for i in range(120):
    x = 7 + int(hash01(i, 0, 0, 82) * 40)
    z = -230 + int(hash01(i, 1, 0, 82) * 40)
    if (x, z) not in STILL or (x, z) in STILL_KEEP or any(near(x, z, q, 6) for q in pits) or spikes >= 12:
        continue
    fy, ch = STILL[(x, z)]
    r = 1.6 + hash01(i, 2, 0, 82) * 1.4
    h = min(ch - 1, 6 + int(hash01(i, 3, 0, 82) * 6))
    spikes += 1
    for dy in range(h):
        rad = r * (1 - dy / float(h))
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if math.hypot(dx, dz) <= rad + 0.2 and (x + dx, z + dz) in STILL:
                    yy = STILL[(x + dx, z + dz)][0] + 1 + dy
                    if dy == 0 or yy > STILL[(x + dx, z + dz)][0]:
                        S(x + dx, yy, z + dz, "minecraft:blue_ice" if math.hypot(dx, dz) < 0.9 else "minecraft:packed_ice")
    STILL_KEEP.update((x + dx, z + dz) for dx in range(-3, 4) for dz in range(-3, 4))
icicles = 0
for (x, z) in skeys:
    fy, ch = STILL[(x, z)]
    top_air = fy + ch
    if hash01(x, 20, z, 83) < 0.05 and ch > 6:
        ln = 2 + int(hash01(x, 21, z, 83) * 5)
        for i in range(ln):
            S(x, top_air - i, z, "minecraft:packed_ice" if i < ln - 1 else "minecraft:blue_ice")
            if i < ln - 2 and i % 2 == 0:
                for dx, dz in ((1, 0), (-1, 0)):
                    if hash01(x + dx, i, z + dz, 84) < 0.4:
                        S(x + dx, top_air - i, z + dz, "minecraft:packed_ice")
        icicles += 1
    elif hash01(x, 22, z, 85) < 0.0125 and ch > 7:
        pod(x, top_air, z, "light_blue_stained_glass")
        S(x, top_air - 1, z, "minecraft:light_blue_stained_glass")
    elif hash01(x, 23, z, 86) < 0.07:
        lichen(x, top_air, z, 0.9)
    # snow on the floor
    if (x, z) not in STILL_KEEP and area.get(x, fy + 1, z) in (None, AIR) and hash01(x, 24, z, 87) < 0.55:
        S(x, fy + 1, z, f"minecraft:snow[layers={1 + int(hash01(x, 25, z, 87) * 4)}]")
    elif (x, z) in STILL_KEEP and (x, z) not in pits and area.get(x, fy + 1, z) in (None, AIR) and hash01(x, 24, z, 88) < 0.25:
        S(x, fy + 1, z, "minecraft:snow[layers=1]")
# wall lights at the rim
for (x, z) in skeys:
    fy, ch = STILL[(x, z)]
    if any((x + dx, z + dz) not in STILL for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))) and hash01(x, 26, z, 89) < 0.1:
        yy = fy + 2 + int(hash01(x, 27, z, 89) * 5)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) not in STILL and area.solid(x + dx, yy, z + dz):
                S(x + dx, yy, z + dz, "minecraft:sea_lantern")
                break

# the breakable snow block over Montezuma's piece 8 (a nook in the floor-level wall)
px, pz = PLUG
pf = STILL[PLUG][0]
assert pf == STILL_FLOOR
for dx, dz in ((-1, 0), (1, 0), (0, -1)):
    for y in (pf + 1, pf + 2, pf + 3):
        S(px + dx, y, pz + dz, "minecraft:packed_ice" if y < pf + 3 else "minecraft:calcite")
for dx in (-1, 0, 1):
    S(px + dx, pf + 3, pz + 1 - 1 if False else pz, "minecraft:calcite")
S(px, pf + 1, pz, "minecraft:snow_block")
S(px, pf + 2, pz, "minecraft:snow_block")
for dx, dz in ((-1, -1), (1, -1)):
    for y in (pf + 1, pf + 2, pf + 3):
        S(px + dx, y, pz + dz, "minecraft:calcite")
S(px, pf + 1, pz + 1, "minecraft:snow[layers=3]")
S(px - 1, pf + 1, pz + 1, "minecraft:snow[layers=2]")
S(px + 1, pf + 1, pz + 1, "minecraft:snow[layers=2]")
S(px - 2, pf + 1, pz + 1, "minecraft:blue_ice")
S(px + 2, pf + 1, pz + 1, "minecraft:blue_ice")
S(px - 2, pf + 2, pz + 1, "minecraft:lantern[hanging=false]")
S(px + 2, pf + 2, pz + 1, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- the outside: islets, paths, mouths
UNDERSIDE_I = area.mix({"tuff": 6, "deepslate": 4, "calcite": 1})
ISLETS = [(51, -124, 67, 2), (47, -129, 68, 2), (43, -134, 68, 2), (30, -130, 66, 1), (58, -131, 70, 2), (22, -137, 65, 2)]
for k, (cx, cz, y, r) in enumerate(ISLETS):
    for dx in range(-r - 1, r + 2):
        for dz in range(-r - 1, r + 2):
            d = math.hypot(dx, dz)
            rr = r + 0.5 * noise(cx + dx, cz + dz, k + 40, 3)
            if d > rr + 0.4:
                continue
            x, z = cx + dx, cz + dz
            S(x, y, z, "minecraft:grass_block" if d < rr - 0.3 else "minecraft:moss_block")
            S(x, y - 1, z, "minecraft:dirt")
            depth = int((r + 2) * (1 - (d / (rr + 0.5)) ** 2) * 1.6) + 1
            for i in range(depth):
                S(x, y - 2 - i, z, UNDERSIDE_I(x, y - 2 - i, z) if i > 1 else "minecraft:tuff")
            if d > 0.9 and hash01(x, k, z, 5) < 0.3:
                S(x, y - 2 - depth, z, "minecraft:hanging_roots")
            if hash01(x, k, z, 6) < 0.25 and d < rr - 0.5 and k < 3:
                S(x, y + 1, z, ["minecraft:allium", "minecraft:cornflower", "minecraft:pink_petals[facing=north,flower_amount=2]",
                                "minecraft:short_grass"][int(hash01(x, k, z, 7) * 4)])
    if k < 3:
        for dy in range(1, 4):
            area.clear(cx, y + dy, cz) if area.get(cx, y + dy, cz) in (None, AIR) else None
for (cx, cz, y, r) in ISLETS[:3:2]:
    lamp(cx + r - 1, y + 1, cz, 2)

# paths: the south approach (x 38..42) and the east crossing (z -182..-178)
for z in range(-158, -137):
    for x in range(38, 43):
        if (x, z) in LAND:
            S(x, T[(x, z)], z, PATH(x, 68, z))
            area.top[(x, z)] = T[(x, z)]
for x in range(61, 70):
    for z in range(CROSS_Z - 2, CROSS_Z + 3):
        if (x, z) in LAND:
            S(x, T[(x, z)], z, PATH(x, 68, z))
            area.top[(x, z)] = T[(x, z)]
for z in range(-166, -157):                 # the stretch between the mouth and the cavern is stairs/floor already
    pass
# lamps along both approaches
for z in range(-142, -157, -5):
    for x in (36, 44):
        if (x, z) in LAND and T[(x, z)] == G:
            lamp(x, G + 1, z)
for x in range(66, 61, -5):
    for z in (CROSS_Z - 4, CROSS_Z + 4):
        if (x, z) in LAND:
            lamp(x, G + 1, z)


def mouth(cx, cz, dx, dz, fy_top):
    """A frame for a cave mouth: pillars of calcite and tuff brick either side of the 5-wide opening, a lintel, lights."""
    lx, lz = (1, 0) if dz else (0, 1)
    for k in (-3, 3):
        x, z = cx + lx * k, cz + lz * k
        for y in range(fy_top, fy_top + 7):
            S(x, y, z, "minecraft:calcite" if y % 3 else "minecraft:tuff_bricks")
        S(x, fy_top + 7, z, "minecraft:sea_lantern")
        for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if area.get(x + ddx, fy_top + 7, z + ddz) in (None, AIR):
                S(x + ddx, fy_top + 7, z + ddz, "minecraft:blue_stained_glass")
    for k in range(-3, 4):
        x, z = cx + lx * k, cz + lz * k
        S(x, fy_top + 6, z, "minecraft:polished_tuff" if abs(k) != 3 else "minecraft:tuff_bricks")
    for k in (-2, 2):
        x, z = cx + lx * k, cz + lz * k
        S(x, fy_top + 5, z, "minecraft:lantern[hanging=true]")


mouth(40, -158, 0, -1, G + 1)
mouth(60, CROSS_Z, -1, 0, G + 1)

# a few bare pale oaks and tufts on the flats, plus plants
trees = 0
for x in range(X0 + 4, X1 - 3):
    for z in range(Z0 + 4, -138):
        if (x, z) not in LAND or T[(x, z)] != G or LAND[(x, z)] < 5 or (x, z) in CORR_COLS or protected(x, z):
            continue
        if abs(x - 40) < 6 and z > -165:
            continue
        if hash01(x, 0, z, 91) < 0.004 and trees < 14:
            area.dead_tree(x, G + 1, z, height=5 + int(hash01(x, 3, z, 91) * 5), log="pale_oak_log", seed=x + z)
            trees += 1
WILD = lambda x, z: ("minecraft:short_grass" if hash01(x, 1, z, 5) < 0.6 else "minecraft:fern" if hash01(x, 2, z, 5) < 0.5 else
                     "minecraft:pink_petals[flower_amount=2,facing=north]")
area.scatter({c for c in LAND if T[c] == G and c not in CORR_COLS and not protected(*c)}, 0.2, WILD, seed=6,
             avoid=lambda x, z: abs(x - 40) <= 2 and z > -158)

# floating shards beyond the cliffs
for i in range(60):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 6))
    if i > 16 or edge(x, z) > -3 or edge(x, z) < -16 or z > -143 or (x > 58 and -202 < z < -158):
        continue
    y = 52 + int(hash01(i, 2, 0, 77) * 26)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    b = "minecraft:tuff" if q < 0.7 else "minecraft:calcite"
                    if hash01(x + dx, y + dy, z + dz, 5) < 0.08:
                        b = "minecraft:crying_obsidian"
                    S(x + dx, y + dy, z + dz, b)
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:amethyst_block" if k % 2 == 0 else "minecraft:crying_obsidian")

# ---------------------------------------------------------------- NPCs, souls, spawn spots (players stand at floor + 1)
area.npc("Rock", ROCK_NPC[0], CAV[ROCK_NPC][0] + 1, ROCK_NPC[1], 90.0, "in a corner of boulders at the cavern's north-east; rock denizens lead to Living Metal Hearts")
area.npc("Chipper McPickaxe", CHIP_NPC[0], CAV[CHIP_NPC][0] + 1, CHIP_NPC[1], 180.0, "by the mining cart on the cavern's west side; sells the Anti-Sentient Pickaxe")
area.npc("enigma:Living Cave", SOUL_CAVE[0], CAV[SOUL_CAVE][0] + 1, SOUL_CAVE[1], 0.0, "on the prismarine dais in the middle of the cavern")
area.npc("enigma:Living Stillness", SOUL_STILL[0], CAV[SOUL_STILL][0] + 1, SOUL_STILL[1], 270.0, "on the high west ledge of the cavern (up the terraces)")
area.npc("spawn:montezuma_8", PLUG[0], STILL_FLOOR + 1, PLUG[1], 0.0, "under the breakable snow block in Living Stillness")

# ---------------------------------------------------------------- zones and mobs


def mob(kind, name, level, health, damage, xp, coins, mx_, every, a, b, drops=None):
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


LAPIS = {"minecraft:lapis_ore": {"becomes": "minecraft:tuff", "regrow": 30}}
STILL_BLOCKS = dict(LAPIS)
STILL_BLOCKS["minecraft:snow_block"] = {"becomes": "minecraft:air", "regrow": 60}
area.zone("Living Cave", box=((X0, 59, Z0), (X1, 140, Z1)), blocks=dict(LAPIS), mobs=[
    mob("minecraft:zombie", "Autonull", 5, 155, 7, 6, 400, 5, 8, (26, 62, -220), (44, 72, -206), [drop("NULLIFIED_METAL", 1, 1, 1)]),
    mob("minecraft:zombie", "Autonull", 5, 155, 7, 6, 400, 4, 8, (22, 62, -190), (48, 72, -176), [drop("NULLIFIED_METAL", 1, 1, 1)]),
])
area.zone("Living Stillness", box=((X0 + 2, 30, -236), (48, 58, -182)), blocks=STILL_BLOCKS, mobs=[
    mob("minecraft:stray", "Frozille", 5, 135, 6, 6, 150, 4, 8, (14, 43, -224), (28, 52, -206), [drop("FROSTY_CRUX", 1, 1, 1)]),
    mob("minecraft:stray", "Frozille", 5, 135, 6, 6, 150, 3, 10, (36, 43, -216), (42, 52, -200), [drop("FROSTY_CRUX", 1, 1, 1)]),
])

area.spots["notes"].append(
    f"Living Cave: cavern floor y {CAVE_FLOOR} (hall x 12..56, z -226..-166), west ledge y 67 (x 3..8, z -208..-198), south mouth "
    f"(40, 69, -158) down to the cavern at z -167, east mouth (60, 69, {CROSS_Z}) down to x 52, ramp into Living Stillness "
    f"x 32..34 from (33, 63, -203) to (33, 43, -222); Stillness floor y {STILL_FLOOR} (x 6..46, z -231..-189), snow plug "
    f"{PLUG[0]}, 43..44, {PLUG[1]}; snakes' heads at {[(a, b) for a, b, _, _ in HEAD_CELLS]}")
area.save()
print("lapis ore", lapis, "pits", len(pits), "spikes", spikes, "icicles", icicles, "trees", trees, "pillars", pillars, "snakes", HEAD_CELLS)
