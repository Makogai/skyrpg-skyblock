#!/usr/bin/env python3
"""Galatea, Moonglade Marsh north (x -150..149, z -160..-1): Tangleburg (the arrival, Fusion House, Bank, Library, Hunting
Lodge, market, pond and dock), the Evergreen Plateau (terraced high ground with fig groves and Chillblade clearings), the
North and West Reaches (mixed forest, ruins, streams), the Forest Temple, a waterfall hiding a Fairy Soul and the Dragon's
Lair (a jade cave in the north cliff).

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python galatea_north.py

The south half (galatea_south.py) meets this along z = 0: the surface keeps y 68 within SEAM of it and the main paths cross
at x -60 and x 60 (5 wide). THE TREE RULES (galatea_lib): fig trees are jungle_log trunks/branches with azalea leaves, nothing
else uses jungle_log, and every fig's logs stay 2+ blocks from another tree's.
"""
import math

from kit import hash01, noise
from galatea_lib import new_area, land, lushlilacs, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("north", version=4)
area.shell = 1
S = area.set
G = GROUND
X0, X1, Z0, Z1 = -150, 149, -160, -1
ARRIVAL = (0, G + 1, -40)


# ---------------------------------------------------------------- small helpers
def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def jag(a, b, s):
    return 2.5 * noise(a, b, s, 1.3) + 1.4 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 0.8 * (hash01(int(a), 2, int(b), s) - 0.5)


def seam_f(z):
    return sm((-z - 10) / 14.0)


def edge(x, z):
    """Distance in blocks to the outer edge (west, east, north); the south side is the seam and stays flush."""
    sf = seam_f(z)
    xw = -147 + sf * (3 + jag(z, 0, 1))
    xe = 146 - sf * (3 + jag(z, 9, 2))
    zn = -156 + 3 + jag(x, 5, 3)
    return min(x - xw, xe - x, z - zn)


# ---------------------------------------------------------------- layout constants
PC = (-104, -106)          # plateau centre
PR = (58, 52)              # plateau radii
TOWN = (-37, -77, 47, -2)  # flat rectangle (x0, z0, x1, z1) around Tangleburg
LAIR_R = (72, -158, 118, -128)   # the bluff (x0, z0, x1, z1)
MOUND = (102, -66, 118, -54)     # the waterfall rock (x0, z0, x1, z1)
TEMPLE = (71, -106, 87, -90)     # outer walls
PLAT_TOP = 36                    # plateau gain at the core (top 104)


def town_d(x, z):
    dx = max(TOWN[0] - x, 0, x - TOWN[2])
    dz = max(TOWN[1] - z, 0, z - TOWN[3])
    return math.hypot(dx, dz)


def plateau_d(x, z):
    return math.hypot((x - PC[0]) / PR[0], (z - PC[1]) / PR[1]) + 0.07 * noise(x, z, 4, 1.6)


def plateau_gain(x, z):
    u = max(0.0, min(1.0, (1 - plateau_d(x, z)) / 0.55))
    k = u * 3
    i = min(int(k), 2)
    f = k - i
    return 12 * (i + 0.5 * f + 0.5 * sm(f)) if u < 1 else PLAT_TOP


def lair_gain(x, z):
    di = min(x - LAIR_R[0], LAIR_R[2] - x, LAIR_R[3] - z)
    m = sm(di / 6.0)
    return 26 * m + (1.5 * noise(x, z, 8, 1.3) * m)


def rolling(x, z):
    f = seam_f(z + 0) * sm(town_d(x, z) / 10.0)
    if z > -6:
        f = 0.0
    return (noise(x, z, 31, 1.1) * 0.5 + 0.5) * 4.0 * f


def raw_height(x, z):
    h = G + rolling(x, z) + plateau_gain(x, z) + lair_gain(x, z)
    return int(round(h))


# ---------------------------------------------------------------- water bodies
PONDS = [  # (cx, cz, radius, bed)
    (28, -28, 5.5, 64),        # Tangleburg's pond (Jaeger's dock)
    (-110, -22, 6.0, 65),      # the west pond
    (110, -49, 4.5, 65),       # the pool under the waterfall
    (104, -22, 5.0, 65),       # the east pond
]
STREAMS = [  # polylines, bed y 66
    [(-100, -56), (-100, -48), (-108, -40), (-111, -29)],
    [(110, -44), (106, -36), (103, -28)],
]
CHANNEL = {}      # (x, z) -> bed y for water cells
for (pcx, pcz, pr, bed) in PONDS:
    for x in range(int(pcx - pr - 4), int(pcx + pr + 5)):
        for z in range(int(pcz - pr - 4), int(pcz + pr + 5)):
            d = math.hypot(x - pcx, z - pcz) + 0.9 * noise(x, z, 6, 1.5)
            if d <= pr:
                CHANNEL[(x, z)] = bed
for pts in STREAMS:
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n * 2 + 1):
            px, pz = x1 + (x2 - x1) * i / (n * 2), z1 + (z2 - z1) * i / (n * 2)
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if dx * dx + dz * dz <= 2.4:
                        CHANNEL.setdefault((int(round(px)) + dx, int(round(pz)) + dz), 66)
BANK = {}     # columns round the water kept level
for (x, z) in list(CHANNEL):
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if (x + dx, z + dz) not in CHANNEL:
                BANK[(x + dx, z + dz)] = 1

# ---------------------------------------------------------------- the ramp onto the plateau
RAMP = [(-40, -42), (-52, -46), (-66, -62), (-84, -76), (-100, -86), (-110, -96), (-106, -106), (-102, -120), (-97, -134)]


def polyline_samples(points, step=0.5):
    out = []
    s = 0.0
    for (x1, z1), (x2, z2) in zip(points, points[1:]):
        length = math.hypot(x2 - x1, z2 - z1)
        n = max(1, int(length / step))
        for i in range(n):
            out.append((x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n, s + length * i / n))
        s += length
    out.append((points[-1][0], points[-1][1], s))
    return out


RAMP_S = polyline_samples(RAMP)
raw_s = [raw_height(int(round(x)), int(round(z))) for x, z, _ in RAMP_S]
smooth = []
for i in range(len(raw_s)):
    lo, hi = max(0, i - 10), min(len(raw_s), i + 11)
    smooth.append(sum(raw_s[lo:hi]) / (hi - lo))
ramp_h = []
prev = float(G)
for i, (x, z, s) in enumerate(RAMP_S):
    ds = 0.5
    prev = max(prev, min(smooth[min(i + 16, len(smooth) - 1)], prev + 0.5 * ds))
    ramp_h.append(prev)
RAMPB = {}        # (x, z) -> (dist, height)
for (x, z, s), h in zip(RAMP_S, ramp_h):
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            cx, cz = int(round(x)) + dx, int(round(z)) + dz
            d = math.hypot(cx - x, cz - z)
            if d > 5.4:
                continue
            old = RAMPB.get((cx, cz))
            if old is None or d < old[0]:
                RAMPB[(cx, cz)] = (d, h)

# ---------------------------------------------------------------- the heights
T = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        if edge(x, z) < 0:
            continue
        h = raw_height(x, z)
        rb = RAMPB.get((x, z))
        if rb:
            w = 1.0 if rb[0] <= 2.6 else sm((5.4 - rb[0]) / 2.8)
            h = int(round(h * (1 - w) + rb[1] * w))
        if (x, z) in CHANNEL:
            h = CHANNEL[(x, z)]
        elif (x, z) in BANK:
            h = max(67, min(h, 69)) if h < 75 else h
            if h > 68 and not (RAMPB.get((x, z))):
                h = 68 if h <= 70 else h
        mx0, mz0, mx1, mz1 = MOUND
        if mx0 - 2 <= x <= mx1 + 2 and mz0 - 2 <= z <= mz1 + 3 and (x, z) not in CHANNEL:
            h = G
        if TEMPLE[0] - 4 <= x <= TEMPLE[2] + 4 and TEMPLE[1] - 4 <= z <= TEMPLE[3] + 4:
            h = G
        if z > -6:
            h = G
        T[(x, z)] = h

# ---------------------------------------------------------------- surface materials
FOREST = area.mix({"grass_block": 5, "moss_block": 3, "podzol": 3, "rooted_dirt": 1})
HIGH = area.mix({"moss_block": 4, "grass_block": 4, "podzol": 3, "rooted_dirt": 1, "coarse_dirt": 1})
SWAMP = area.mix({"mud": 4, "moss_block": 3, "grass_block": 2, "podzol": 1})
TOWNG = area.mix({"grass_block": 7, "coarse_dirt": 2, "podzol": 1})
UNDER = area.mix({"dirt": 5, "coarse_dirt": 2, "rooted_dirt": 2, "mud": 1})
BODY = area.mix({"tuff": 6, "dirt": 3, "mud": 3, "packed_mud": 2, "deepslate": 3, "calcite": 1, "andesite": 1})
UNDERSIDE = area.mix({"tuff": 6, "mud": 3, "packed_mud": 2, "deepslate": 3, "calcite": 1, "moss_block": 1})
CLIFFMIX = area.mix({"mossy_cobblestone": 2, "tuff": 3, "moss_block": 3, "andesite": 1, "rooted_dirt": 1, "mud": 1})

# columns that must be solid all the way down (caves, the lair, the waterfall rock)
FULL = set()
for x in range(LAIR_R[0] - 4, LAIR_R[2] + 5):
    for z in range(LAIR_R[1], LAIR_R[3] + 4):
        FULL.add((x, z))
for x in range(MOUND[0] - 3, MOUND[2] + 4):
    for z in range(MOUND[1] - 3, MOUND[3] + 4):
        FULL.add((x, z))
CAVE_P = (-126, -131)      # plateau cave (glow berries): sinkhole ramp starts near here; rect kept solid
for x in range(CAVE_P[0] - 10, CAVE_P[0] + 40):
    for z in range(CAVE_P[1] - 10, CAVE_P[1] + 22):
        FULL.add((x, z))

SH = 4


def under_depth(x, z, e):
    t = sm(e / 48.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


def surf_for(x, z):
    if (x, z) in BANK or (x, z) in CHANNEL:
        return SWAMP
    if T[(x, z)] >= 80:
        return HIGH
    return FOREST


for (x, z), top in T.items():
    e = edge(x, z)
    rough = False
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = T.get((x + dx, z + dz))
        if n is None:
            if z + dz <= Z1:
                rough = True
        elif abs(n - top) >= 2:
            rough = True
    if e < 12 or rough or (x, z) in FULL:
        land(area, x, z, top, surf_for(x, z), UNDER, BODY)
    else:
        area.set(x, top, z, surf_for(x, z)(x, top, z))
        for y in range(top - 1, top - 4, -1):
            area.set(x, y, z, UNDER(x, y, z))
        for y in range(top - 4, top - 4 - SH, -1):
            area.set(x, y, z, BODY(x, y, z))
        for y in range(LAND_BOTTOM + SH, LAND_BOTTOM - 1, -1):
            area.set(x, y, z, BODY(x, y, z))
    area.top[(x, z)] = top
    d = under_depth(x, z, e if z < -6 else min(x - (-147), 146 - x))
    for k in range(1, d + 1):
        if e > 8 and 3 < k <= d - 4:
            continue
        y = LAND_BOTTOM - k
        r = hash01(x, y, z, 12)
        if r < 0.03 and e < 20:
            b = "minecraft:budding_amethyst" if r < 0.004 else "minecraft:amethyst_block"
        else:
            b = UNDERSIDE(x, y, z)
        S(x, y, z, b)
    if e < 30 and hash01(x, 10, z, 13) < 0.012:
        ln = 3 + int(hash01(x, 11, z, 14) * 8)
        yy = LAND_BOTTOM - d
        for i in range(ln):
            S(x, yy - 1 - i, z, "minecraft:moss_block" if hash01(x, i, z, 15) < 0.4 else "minecraft:tuff")
        S(x, yy - 1 - ln, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]")
    if hash01(x, 2, z, 21) < 0.012 and e < 6:
        for i in range(1, 2 + int(hash01(x, 3, z, 22) * 5)):
            S(x, LAND_BOTTOM - i - 2, z, "minecraft:hanging_roots") if area.get(x, LAND_BOTTOM - i - 2, z) is None else None

# interior cliff faces (terrace walls, the bluff): rock instead of dirt layers
for (x, z), top in T.items():
    nh = top
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = T.get((x + dx, z + dz))
        if n is not None and n < nh:
            nh = n
    if top - nh >= 2:
        for y in range(nh + 1, top):
            S(x, y, z, CLIFFMIX(x, y, z))

# floating shards beyond the west and east cliffs
for i in range(26):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 30))
    ee = edge(x, z)
    if ee > -3 or ee < -16:
        continue
    y = 52 + int(hash01(i, 2, 0, 77) * 40)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    S(x + dx, y + dy, z + dz, "minecraft:tuff" if q < 0.7 else "minecraft:moss_block")
    S(x, y + 3, z, "minecraft:fern") if False else None
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:tuff")

# ---------------------------------------------------------------- occupancy and misc helpers
RES = []          # (name, x0, z0, x1, z1) reserved rectangles (checked disjoint)
occ = set()       # columns trees and scatter keep away from
PATHCOLS = set()


def reserve(name, x0, z0, x1, z1, m=1, tree_m=3, check=True):
    if check:
        for (n, a0, b0, a1, b1) in RES:
            if x0 - m <= a1 and a0 - m <= x1 + 0 and z0 - m <= b1 and b0 - m <= z1:
                if not (x1 + m < a0 or a1 + m < x0 or z1 + m < b0 or b1 + m < z0):
                    raise SystemExit(f"overlap: {name} {(x0, z0, x1, z1)} with {n} {(a0, b0, a1, b1)}")
    RES.append((name, x0, z0, x1, z1))
    for x in range(x0 - tree_m, x1 + tree_m + 1):
        for z in range(z0 - tree_m, z1 + tree_m + 1):
            occ.add((x, z))


def gy(x, z):
    return T.get((x, z), G)


def clear_box(x0, y0, z0, x1, y1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                area.clear(x, y, z)


def fill(x0, y0, z0, x1, y1, z1, block):
    area.fill(x0, y0, z0, x1, y1, z1, block)


def sign(x, y, z, lines, kind="dark_oak_sign[rotation=0]", glow=1):
    S(x, y, z, kind, {"id": "minecraft:sign", "front_text": {"messages": [f'"{t}"' if t else '""' for t in lines], "color": "white",
                                                            "has_glowing_text": glow},
                      "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def lamp_post(x, z, kind="lantern", y=None):
    y = gy(x, z) if y is None else y
    for h in range(1, 4):
        S(x, y + h, z, "minecraft:dark_oak_fence")
    S(x, y + 4, z, f"minecraft:{kind}[hanging=false]")


# water cells (after the terrain so they sit in the carved basins)
for (x, z), bed in CHANNEL.items():
    if (x, z) not in T:
        continue
    S(x, bed, z, "minecraft:mud" if hash01(x, 1, z, 3) < 0.6 else "minecraft:clay")
    for y in range(bed + 1, 68):
        S(x, y, z, "minecraft:water")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            occ.add((x + dx, z + dz))

# ---------------------------------------------------------------- Tangleburg's yards and the paths
for x in range(TOWN[0], TOWN[2] + 1):
    for z in range(TOWN[1], TOWN[3] + 1):
        if (x, z) in T and T[(x, z)] == G and (x, z) not in CHANNEL and (x, z) not in BANK:
            S(x, G, z, TOWNG(x, G, z))

ROAD = area.mix({"dirt_path": 5, "coarse_dirt": 2, "gravel": 1, "mud_bricks": 1})
LANE = area.mix({"mud_bricks": 3, "packed_mud": 3, "gravel": 2, "coarse_dirt": 1, "dirt_path": 2})
WILDP = area.mix({"dirt_path": 4, "coarse_dirt": 3, "moss_block": 1, "rooted_dirt": 1})
PATHSPECS = []


def lay(points, width=3, kind=ROAD, name=""):
    done = area.path(points, width=width, blocks=kind)
    PATHCOLS.update(done)
    PATHSPECS.append((name, points, width))
    return done


# main roads to the two crossings (5 wide next to the seam), the ramp, the lanes and every door stub
lay([(-13, -42), (-40, -42), (-50, -33), (-60, -20), (-60, -12)], 4, LANE, "west road")
lay([(-60, -14), (-60, -1)], 5, LANE, "west crossing")
lay([(13, -36), (46, -36), (56, -30), (60, -22), (60, -12)], 4, LANE, "east road")
lay([(60, -14), (60, -1)], 5, LANE, "east crossing")
lay([(0, -27), (0, -12), (12, -9), (36, -10)], 3, LANE, "south road to the pad")
lay([(60, -12), (41, -10)], 3, LANE, "pad link")
lay(RAMP[:], 4, ROAD, "plateau ramp")
lay([(-28, -55), (31, -55)], 3, LANE, "north lane")
lay([(0, -55), (0, -54)], 3, LANE, "fusion step")
lay([(-18, -50), (-13, -50)], 2, LANE, "A")
lay([(-18, -34), (-13, -34)], 2, LANE, "B")
lay([(-38, -47), (-38, -42)], 2, LANE, "G")
lay([(-38, -37), (-38, -42)], 2, LANE, "H")
lay([(-9, -23), (-9, -27)], 2, LANE, "C")
lay([(9, -23), (9, -27)], 2, LANE, "D")
lay([(40, -27), (40, -35)], 2, LANE, "E")
lay([(-31, -28), (-31, -42)], 2, LANE, "lumber yard")
lay([(-23, -57), (-23, -55)], 2, LANE, "library")
lay([(17, -57), (17, -55)], 2, LANE, "bank")
lay([(29, -57), (29, -55)], 2, LANE, "F")
lay([(35, -48), (35, -37)], 2, LANE, "dax")
lay([(15, -47), (13, -47)], 2, LANE, "lodge")
# out into the reaches: the temple road, the lair road, a ring through the west woods
lay([(31, -55), (45, -70), (62, -86), (76, -89)], 3, ROAD, "temple road")
lay([(88, -90), (96, -108), (95, -118), (94, -128)], 3, ROAD, "lair road")
lay([(-60, -20), (-82, -14), (-100, -16)], 3, WILDP, "west woods")
lay([(60, -22), (78, -24), (96, -20)], 3, WILDP, "east woods")
lay([(46, -36), (70, -50), (90, -58), (99, -58)], 3, WILDP, "to the waterfall")
for p in PATHCOLS:
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            occ.add((p[0] + dx, p[1] + dz))

# ---------------------------------------------------------------- building kit
FACE = {"s": (0, 1, "south"), "n": (0, -1, "north"), "e": (1, 0, "east"), "w": (-1, 0, "west")}
DOORS = {}


def door_at(x, z, facing, wood="dark_oak", y=G):
    area.clear(x, y + 1, z)
    area.clear(x, y + 2, z)
    S(x, y + 1, z, f"minecraft:{wood}_door[facing={facing},half=lower,hinge=left,open=false]")
    S(x, y + 2, z, f"minecraft:{wood}_door[facing={facing},half=upper,hinge=left,open=false]")


def house(name, x0, z0, x1, z1, door, wall_h=4, wall="spruce_planks", frame="dark_oak_log", roof="dark_oak_stairs",
          roof_block="dark_oak_planks", floor="spruce_planks", base=None, door_wood="dark_oak", door_x=None, ridge=None,
          glass="glass_pane", y=G, tree_m=4, gable=None):
    """A timber building: floor at y, walls y+1..y+wall_h (the top row is the ceiling), a gabled roof with its ridge along the
    longer side. `door` is the side (n/s/e/w); returns the cell in front of the door."""
    reserve(name, x0, z0, x1, z1, m=1, tree_m=tree_m)
    base = base or wall
    gable = gable or wall
    ridge = ridge or ("x" if (x1 - x0) >= (z1 - z0) else "z")
    clear_box(x0 - 1, y + 1, z0 - 1, x1 + 1, y + wall_h + (min(x1 - x0, z1 - z0) // 2) + 5, z1 + 1)
    dx_, dz_, facing = FACE[door]
    if door == "s" or door == "n":
        dcell = ((x0 + x1) // 2 if door_x is None else door_x, z1 if door == "s" else z0)
    else:
        dcell = (x1 if door == "e" else x0, (z0 + z1) // 2 if door_x is None else door_x)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, y, z, f"minecraft:{floor}")
            e_ = x in (x0, x1) or z in (z0, z1)
            if not e_:
                S(x, y + wall_h, z, f"minecraft:{floor}")
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            post = (x - x0) % 4 == 0 if z in (z0, z1) else (z - z0) % 4 == 0
            for yy in range(y + 1, y + wall_h + 1):
                if corner or post:
                    b = f"{frame}[axis=y]"
                elif yy == y + 1:
                    b = base
                else:
                    b = wall
                S(x, yy, z, "minecraft:" + b)
    # windows
    if wall_h >= 4:
        for x in range(x0 + 1, x1):
            for z in (z0, z1):
                if (x - x0) % 4 == 2 and (x, z) != dcell:
                    S(x, y + 2, z, f"minecraft:{glass}")
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                if (z - z0) % 4 == 2 and (x, z) != dcell:
                    S(x, y + 2, z, f"minecraft:{glass}")
    door_at(dcell[0], dcell[1], facing, door_wood, y)
    front = (dcell[0] + dx_, dcell[1] + dz_)
    # roof
    i = 0
    while True:
        if ridge == "x":
            lo, hi = z0 - 1 + i, z1 + 1 - i
        else:
            lo, hi = x0 - 1 + i, x1 + 1 - i
        yy = y + wall_h + 1 + i
        if lo > hi:
            break
        rng = range(x0 - 1, x1 + 2) if ridge == "x" else range(z0 - 1, z1 + 2)
        for t in rng:
            if ridge == "x":
                if lo == hi:
                    S(t, yy, lo, "minecraft:" + roof_block)
                else:
                    S(t, yy, lo, f"minecraft:{roof}[facing=south,half=bottom]")
                    S(t, yy, hi, f"minecraft:{roof}[facing=north,half=bottom]")
            else:
                if lo == hi:
                    S(lo, yy, t, "minecraft:" + roof_block)
                else:
                    S(lo, yy, t, f"minecraft:{roof}[facing=east,half=bottom]")
                    S(hi, yy, t, f"minecraft:{roof}[facing=west,half=bottom]")
        for t in range(lo + 1, hi):
            if ridge == "x" and z0 <= t <= z1:
                for x in (x0, x1):
                    S(x, yy, t, "minecraft:" + gable)
            if ridge == "z" and x0 <= t <= x1:
                for z in (z0, z1):
                    S(t, yy, z, "minecraft:" + gable)
        i += 1
    DOORS[name] = front
    return front


def interior(x0, z0, x1, z1, door, wall_h=4, y=G, seed=0):
    """A bed, a table with stools, a hearth corner and shelves inside a house."""
    ix0, ix1, iz0, iz1 = x0 + 1, x1 - 1, z0 + 1, z1 - 1
    corners = [(ix0, iz0), (ix1, iz0), (ix0, iz1), (ix1, iz1)]
    far = {"s": lambda c: c[1], "n": lambda c: -c[1], "e": lambda c: c[0], "w": lambda c: -c[0]}[door]
    cx_, cz_ = sorted(corners, key=far)[0]
    west = cx_ == ix0
    col = "red" if seed % 2 else "blue"
    S(cx_, y + 1, cz_, f"minecraft:{col}_bed[facing={'west' if west else 'east'},part=head]")
    S(cx_ + (1 if west else -1), y + 1, cz_, f"minecraft:{col}_bed[facing={'west' if west else 'east'},part=foot]")
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    S(mx, y + 1, mz, "minecraft:dark_oak_fence")
    S(mx, y + 2, mz, "minecraft:dark_oak_pressure_plate")
    S(mx - 1, y + 1, mz, "minecraft:spruce_stairs[facing=east,half=bottom]")
    S(mx + 1, y + 1, mz, "minecraft:spruce_stairs[facing=west,half=bottom]")
    S(mx, y + wall_h - 1, mz, "minecraft:lantern[hanging=true]")
    hx = ix1 if west else ix0
    hz = iz1 if cz_ == iz0 else iz0
    S(hx, y + 1, hz, "minecraft:bookshelf")
    S(hx, y + 2, hz, "minecraft:bookshelf")
    step = -1 if hx == ix1 else 1
    S(hx + step, y + 1, hz, "minecraft:barrel[facing=up]")
    S(hx + 2 * step, y + 1, hz, "minecraft:crafting_table")


# ---------------------------------------------------------------- the plaza and the fountain
PLX0, PLX1, PLZ0, PLZ1 = -13, 13, -53, -27
reserve("plaza", PLX0, PLZ0, PLX1, PLZ1, m=0, tree_m=4, check=False)
PLAZA = area.mix({"packed_mud": 4, "mud_bricks": 3, "gravel": 1, "tuff_bricks": 1})
for x in range(PLX0, PLX1 + 1):
    for z in range(PLZ0, PLZ1 + 1):
        cx_, cz_ = x, z + 40
        if abs(x) >= 12 and abs(cz_) >= 12:
            continue
        r = math.hypot(cx_, cz_)
        if x in (PLX0, PLX1) or z in (PLZ0, PLZ1):
            b = "minecraft:polished_tuff"
        elif r < 1.2:
            b = "minecraft:chiseled_tuff_bricks"
        elif 5.5 <= r < 6.5:
            b = "minecraft:polished_tuff"
        elif r < 5.5:
            b = "minecraft:tuff_bricks" if (x + z) % 2 else "minecraft:polished_tuff"
        else:
            b = PLAZA(x, G, z)
        S(x, G, z, b)
# fountain at the north end of the square
FX, FZ = 0, -47
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if abs(dx) == 3 and abs(dz) == 3:
            continue
        if max(abs(dx), abs(dz)) == 3:
            S(FX + dx, G, FZ + dz, "minecraft:polished_tuff")
            S(FX + dx, G + 1, FZ + dz, "minecraft:tuff_brick_wall")
        else:
            S(FX + dx, G, FZ + dz, "minecraft:water")
            S(FX + dx, G - 1, FZ + dz, "minecraft:tuff_bricks")
for dy in range(1, 4):
    S(FX, G + dy, FZ, "minecraft:chiseled_tuff_bricks" if dy < 3 else "minecraft:polished_tuff")
S(FX, G + 4, FZ, "minecraft:copper_lantern[hanging=false]")
# planter boxes, lamp posts and benches round the square
for (px, pz) in ((-12, -52), (12, -52), (-12, -28), (12, -28)):
    S(px, G + 1, pz, "minecraft:dark_oak_planks")
    S(px, G + 2, pz, "minecraft:flowering_azalea")
for (px, pz) in ((-9, -52), (9, -52), (-13, -45), (13, -45), (-13, -35), (13, -35), (-9, -28), (9, -28)):
    lamp_post(px, pz, "lantern" if (px + pz) % 2 else "soul_lantern", y=G)
for (bx, bz, f) in ((-5, -50, "south"), (3, -50, "south"), (-6, -30, "north"), (4, -30, "north")):
    for k in range(3):
        S(bx + k, G + 1, bz, f"minecraft:spruce_stairs[facing={f},half=bottom]")

# ---------------------------------------------------------------- the houses
HOUSES = [
    ("house_a", -29, -54, -19, -46, "e", dict(wall="spruce_planks", frame="dark_oak_log", roof="dark_oak_stairs", roof_block="dark_oak_planks", base="mud_bricks")),
    ("house_b", -29, -38, -19, -30, "e", dict(wall="pale_oak_planks", frame="pale_oak_log", roof="spruce_stairs", roof_block="spruce_planks", floor="pale_oak_planks", base="mossy_cobblestone")),
    ("house_c", -14, -22, -5, -14, "n", dict(wall="dark_oak_planks", frame="stripped_dark_oak_log", roof="mud_brick_stairs", roof_block="mud_bricks", floor="dark_oak_planks", base="deepslate_bricks")),
    ("house_d", 5, -22, 14, -14, "n", dict(wall="spruce_planks", frame="dark_oak_log", roof="dark_oak_stairs", roof_block="dark_oak_planks", base="mud_bricks")),
    ("house_e", 36, -26, 45, -18, "n", dict(wall="pale_oak_planks", frame="stripped_pale_oak_log", roof="spruce_stairs", roof_block="spruce_planks", floor="spruce_planks", base="cobblestone")),
    ("house_f", 25, -66, 33, -58, "s", dict(wall="mud_bricks", frame="dark_oak_log", roof="dark_oak_stairs", roof_block="dark_oak_planks", floor="dark_oak_planks", base="deepslate_bricks")),
    ("house_g", -42, -56, -33, -48, "s", dict(wall="dark_oak_planks", frame="spruce_log", roof="spruce_stairs", roof_block="spruce_planks", base="mud_bricks")),
    ("house_h", -42, -36, -34, -30, "n", dict(wall="spruce_planks", frame="stripped_spruce_log", roof="dark_oak_stairs", roof_block="dark_oak_planks", floor="dark_oak_planks", base="mossy_cobblestone")),
]
for i, (name, x0, z0, x1, z1, door, kw) in enumerate(HOUSES):
    front = house(name, x0, z0, x1, z1, door, **kw)
    interior(x0, z0, x1, z1, door, seed=i)
    fx, fz = front
    if door in "ns":
        lamp_post(fx + 2, fz, "lantern" if i % 2 else "soul_lantern")
    else:
        lamp_post(fx, fz + 2, "lantern" if i % 2 else "soul_lantern")
    # a flower box under a window, a chimney on the roof
    if door in "ns":
        wx = x0 + 2
        S(wx, G + 1, (z1 + 1) if door == "s" else (z0 - 1), "minecraft:flowering_azalea")
    cx_ = x1 - 1 if door != "e" else x0 + 1
    for h in range(1, 9):
        S(cx_, G + 3 + h if False else G + 4 + h, z0 + 1 if door != "n" else z1 - 1, "minecraft:cobblestone") if False else None

# ---------------------------------------------------------------- the Fusion House
FH = (-9, -70, 9, -58)
house("fusion", *FH, "s", wall_h=6, wall="mud_bricks", frame="stripped_dark_oak_log", roof="oxidized_cut_copper_stairs",
      roof_block="oxidized_cut_copper", floor="dark_oak_planks", base="deepslate_bricks", glass="purple_stained_glass_pane",
      gable="weathered_cut_copper", tree_m=6)
for x in (-6, -5, 5, 6, -2, 2):                      # tall windows on the front
    for y in (G + 3, G + 4):
        S(x, y, -58, "minecraft:purple_stained_glass_pane") if x not in (-2, 2) else None
for z in (-67, -64, -61):                            # side windows
    for y in (G + 3, G + 4):
        S(-9, y, z, "minecraft:purple_stained_glass_pane")
        S(9, y, z, "minecraft:purple_stained_glass_pane")
# the machine: a copper floor, a ring of cauldrons and brewing stands round an empty heart (spawn:fusion_machine)
MX, MZ = 0, -65
for dx in range(-3, 4):
    for dz in range(-3, 4):
        S(MX + dx, G, MZ + dz, "minecraft:cut_copper" if (dx + dz) % 2 else "minecraft:waxed_weathered_cut_copper")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if dx == 0 and dz == 0:
            continue
        if dx and dz:
            S(MX + dx, G + 1, MZ + dz, "minecraft:cauldron")
        elif dx:
            S(MX + dx, G + 1, MZ + dz, "minecraft:brewing_stand")
        else:
            S(MX + dx, G + 1, MZ + dz, "minecraft:copper_grate")
for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    for h in range(1, 6):
        S(MX + dx, G + h, MZ + dz, "minecraft:amethyst_block" if h % 2 else "minecraft:purple_stained_glass")
    S(MX + dx, G + 6, MZ + dz, "minecraft:lightning_rod[facing=up]") if False else S(MX + dx, G + 6, MZ + dz, "minecraft:copper_block")
S(MX, G + 6, MZ, "minecraft:end_rod[facing=down]") if False else None
S(MX, G + 5, MZ, "minecraft:copper_chain[axis=y]")
S(MX, G + 4, MZ, "minecraft:amethyst_cluster[facing=down]")
for dx in (-2, 2):
    S(MX + dx, G + 1, MZ, "minecraft:copper_block")
    S(MX + dx, G + 2, MZ, "minecraft:amethyst_cluster[facing=up]")
for dz in (-2, 2):
    S(MX, G + 1, MZ + dz, "minecraft:copper_block")
    S(MX, G + 2, MZ + dz, "minecraft:amethyst_cluster[facing=up]")
# shelves of bottles along the side walls
for z in range(-69, -60):
    for x in (-8, 8):
        if z in (-65, -64, -66):
            continue
        S(x, G + 1, z, "minecraft:bookshelf")
        S(x, G + 2, z, "minecraft:bookshelf")
        S(x, G + 3, z, "minecraft:brewing_stand" if z % 2 else "minecraft:amethyst_cluster[facing=up]")
for x in range(-7, 8):                                # the back wall: a counter of cauldrons and copper
    if abs(x) > 3:
        S(x, G + 1, -69, "minecraft:cauldron" if x % 2 else "minecraft:barrel[facing=up]")
for (x, z) in ((-5, -60), (5, -60), (-5, -68), (5, -68)):
    S(x, G + 5, z, "minecraft:copper_lantern[hanging=true]")
S(0, G + 5, -62, "minecraft:copper_lantern[hanging=true]")
# two copper-and-glass tanks behind the house with pipes into the wall
reserve('fusion_tanks', -10, -77, 10, -71, m=0, tree_m=4, check=False)
for tx in (-6, 6):
    tz = -74
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            r = math.hypot(dx, dz)
            if r > 2.5:
                continue
            for h in range(1, 10):
                ring = r > 1.4
                if h == 9:
                    S(tx + dx, G + h, tz + dz, "minecraft:amethyst_block")
                elif ring:
                    S(tx + dx, G + h, tz + dz, "minecraft:purple_stained_glass" if 3 <= h <= 6 else
                      "minecraft:oxidized_copper" if h % 3 else "minecraft:weathered_copper")
                elif 3 <= h <= 6:
                    S(tx + dx, G + h, tz + dz, "minecraft:water")
    S(tx, G + 10, tz, "minecraft:lightning_rod[facing=up]")
    S(tx, G + 11, tz, "minecraft:lightning_rod[facing=up]")
    for z in (-71, -72):
        S(tx, G + 2, z, "minecraft:oxidized_copper")
S(0, G + 1, -57, "minecraft:copper_lantern[hanging=false]") if False else None
lamp_post(-3, -56, "soul_lantern")
lamp_post(3, -56, "soul_lantern")
area.npc("Kysha", 0, G + 1, -61, 180.0, "inside the Fusion House, at the machine")
area.npc("spawn:fusion_machine", MX, G + 1, MZ, 0.0, "the empty heart of the machine ring inside the Fusion House (floor at y 68)")

# ---------------------------------------------------------------- the Bank
house("bank", 13, -66, 22, -58, "s", wall_h=5, wall="tuff_bricks", frame="stripped_spruce_log", roof="weathered_cut_copper_stairs",
      roof_block="weathered_cut_copper", floor="polished_tuff", base="deepslate_bricks", gable="tuff_bricks", tree_m=5)
for x in range(14, 22):
    S(x, G + 1, -62, "minecraft:polished_tuff" if x != 21 else "minecraft:air")
    if x != 21:
        S(x, G + 2, -62, "minecraft:iron_bars")
        S(x, G + 3, -62, "minecraft:iron_bars")
for x in (14, 15, 16):
    S(x, G + 1, -65, "minecraft:gold_block")
    S(x, G + 2, -65, "minecraft:gold_block") if x != 15 else S(x, G + 2, -65, "minecraft:chest[facing=south]")
S(20, G + 1, -65, "minecraft:chest[facing=south]")
S(21, G + 1, -65, "minecraft:chest[facing=south]")
S(14, G + 1, -64, "minecraft:lectern[facing=east]")
for (x, z) in ((15, -60), (20, -60), (17, -64)):
    S(x, G + 4, z, "minecraft:copper_lantern[hanging=true]")
lamp_post(15, -56, "lantern")
lamp_post(19, -56, "lantern")
sign(17, G + 3, -57, ["", "BANK", "Banker Bonsai", ""], "dark_oak_wall_sign[facing=south]")
area.npc("Banker Bonsai", 17, G + 1, -64, 180.0, "behind the counter in the Bank")

# ---------------------------------------------------------------- the Library
house("library", -30, -70, -16, -58, "s", wall_h=5, wall="pale_oak_planks", frame="pale_oak_log", roof="spruce_stairs",
      roof_block="spruce_planks", floor="dark_oak_planks", base="mossy_stone_bricks", glass="glass_pane", tree_m=5)
for x in range(-29, -16):
    for y in (G + 1, G + 2, G + 3):
        S(x, y, -69, "minecraft:bookshelf")
for x in range(-28, -17):
    if x in (-23, -24):
        continue
    for y in (G + 1, G + 2):
        S(x, y, -65, "minecraft:bookshelf")
for x in range(-28, -17):
    if x in (-23, -24):
        continue
    for y in (G + 1, G + 2):
        S(x, y, -66, "minecraft:bookshelf") if x % 3 else S(x, y, -66, "minecraft:chiseled_bookshelf[facing=south]")
S(-27, G + 1, -61, "minecraft:lectern[facing=south]")
S(-20, G + 1, -61, "minecraft:lectern[facing=south]")
S(-18, G + 1, -62, "minecraft:enchanting_table")
S(-29, G + 1, -62, "minecraft:enchanting_table")
for x in (-22, -25):
    S(x, G + 1, -62, "minecraft:spruce_stairs[facing=west,half=bottom]") if False else None
S(-23, G + 4, -62, "minecraft:lantern[hanging=true]")
S(-27, G + 4, -66, "minecraft:lantern[hanging=true]")
S(-19, G + 4, -66, "minecraft:lantern[hanging=true]")
lamp_post(-26, -56, "soul_lantern")
lamp_post(-20, -56, "soul_lantern")
sign(-23, G + 3, -57, ["", "LIBRARY", "", ""], "dark_oak_wall_sign[facing=south]")
area.npc("Oden", -23, G + 1, -63, 180.0, "at the lectern aisle inside the Library")

# ---------------------------------------------------------------- the Hunting Lodge (door on the west, x 15)
house("lodge", 15, -54, 29, -42, "w", wall_h=5, wall="spruce_planks", frame="dark_oak_log", roof="dark_oak_stairs",
      roof_block="dark_oak_planks", floor="spruce_planks", base="cobblestone", door_x=-48, tree_m=5, door_wood="spruce")
TROPHY = ["skeleton_wall_skull", "zombie_wall_head", "creeper_wall_head", "piglin_wall_head", "wither_skeleton_wall_skull"]
for k, x in enumerate(range(17, 28, 2)):               # trophies on the north wall, banners between
    S(x, G + 3, -53, f"minecraft:{TROPHY[k % len(TROPHY)]}[facing=south]")
    if x + 1 < 28:
        S(x + 1, G + 3, -53, f"minecraft:{'red' if k % 2 else 'green'}_wall_banner[facing=south]")
for k, z in enumerate(range(-52, -43, 2)):             # and on the east wall
    S(28, G + 3, z, f"minecraft:{TROPHY[(k + 2) % len(TROPHY)]}[facing=west]")
for x in range(17, 28):                                # long table and shelves along the south wall
    S(x, G + 1, -43, "minecraft:barrel[facing=up]" if x % 3 == 0 else "minecraft:spruce_slab[type=bottom]" if x % 3 == 1 else "minecraft:hay_block[axis=x]")
for z in (-52, -50, -46):
    S(17, G + 1, z, "minecraft:red_carpet")
for x in (21, 22, 23):                                 # the pedestal of the Hunting Box (spawn:hunting_box)
    S(x, G + 1, -52, "minecraft:dark_oak_planks")
S(22, G + 2, -52, "minecraft:barrel[facing=up]")
S(21, G + 1, -51, "minecraft:dark_oak_stairs[facing=north,half=bottom]")
S(23, G + 1, -51, "minecraft:dark_oak_stairs[facing=north,half=bottom]")
S(26, G + 1, -48, "minecraft:furnace[facing=west,lit=true]")
S(26, G + 1, -47, "minecraft:cauldron")
S(26, G + 1, -49, "minecraft:smoker[facing=west,lit=true]")
for (x, z) in ((19, -50), (25, -50), (19, -46), (25, -46), (22, -48)):
    S(x, G + 4, z, "minecraft:lantern[hanging=true]")
S(24, G + 1, -52, "minecraft:brown_carpet")
for x in range(18, 21):
    S(x, G + 1, -49, "minecraft:white_wool") if False else None
sign(14, G + 3, -49, ["", "HUNTING", "LODGE", ""], "dark_oak_wall_sign[facing=west]")
for (x, z, kind) in ((13, -52, "skeleton_skull"), (13, -44, "zombie_head")):         # trophies on posts out front
    S(x, G + 1, z, "minecraft:dark_oak_fence")
    S(x, G + 2, z, f"minecraft:{kind}[rotation=4]")
for z in (-46, -50):
    lamp_post(13, z, "lantern")
area.npc("David Hunterborough", 23, G + 1, -48, 90.0, "inside the Hunting Lodge")
area.npc("spawn:hunting_box", 22, G + 2, -52, 0.0, "the chest-like Hunting Box, on a pedestal (planks at y 69) against the back wall")

# ---------------------------------------------------------------- Dax's trap shed and Auryon's lasso yard
house("dax_shed", 32, -54, 38, -49, "s", wall_h=3, wall="spruce_planks", frame="dark_oak_log", roof="spruce_stairs",
      roof_block="spruce_planks", floor="dark_oak_planks", base="cobblestone", tree_m=4)
for (x, z) in ((33, -52), (37, -52)):
    S(x, G + 1, z, "minecraft:barrel[facing=up]")
S(35, G + 1, -53, "minecraft:crafting_table")
S(36, G + 1, -53, "minecraft:iron_bars") if False else S(36, G + 1, -53, "minecraft:grindstone[face=floor,facing=north]")
S(34, G + 1, -53, "minecraft:cartography_table")
S(35, G + 2, -51, "minecraft:lantern[hanging=true]") if False else None
for (x, z) in ((31, -52), (30, -50), (39, -51), (40, -53)):                    # traps in the grass round the shed
    S(x, G + 1, z, "minecraft:iron_trapdoor[facing=north,half=bottom,open=false]")
    S(x + 1, G + 1, z, "minecraft:chain[axis=x]") if False else None
for (x, z) in ((31, -49), (39, -49)):
    S(x, G + 1, z, "minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]")
S(41, G + 1, -52, "minecraft:cobweb")
area.npc("Dax", 35, G + 1, -51, 0.0, "in his trap shed east of the Hunting Lodge, facing the door")
# the lasso yard
reserve("lasso_yard", 31, -47, 46, -39, m=0, tree_m=3, check=False)
for x in range(31, 47):
    for z in range(-47, -38):
        if x in (31, 46) or z in (-47, -39):
            if 34 <= x <= 36 and z in (-47, -39):
                continue
            S(x, G + 1, z, "minecraft:spruce_fence")
for (x, z) in ((38, -44), (42, -44), (40, -41), (44, -41)):                    # training posts with targets
    for h in range(1, 4):
        S(x, G + h, z, "minecraft:dark_oak_fence")
    S(x, G + 4, z, "minecraft:target")
    S(x + 1, G + 2, z, "minecraft:chain[axis=x]")
for (x, z) in ((33, -45), (45, -45), (33, -41), (45, -42)):
    S(x, G + 1, z, "minecraft:hay_block[axis=y]")
for x in range(37, 44):
    S(x, G, -42, "minecraft:coarse_dirt")
area.npc("Auryon", 36, G + 1, -43, 270.0, "lasso training yard east of the Hunting Lodge")

# ---------------------------------------------------------------- market stalls
def stall(name, x0, z0, front="s", cols=("red_wool", "white_wool"), goods=0):
    w, d = 5, 4
    reserve(name, x0, z0, x0 + w - 1, z0 + d - 1, m=0, tree_m=2, check=False)
    for (x, z) in ((x0, z0), (x0 + w - 1, z0), (x0, z0 + d - 1), (x0 + w - 1, z0 + d - 1)):
        for h in range(1, 4):
            S(x, G + h, z, "minecraft:spruce_fence")
    for x in range(x0, x0 + w):
        for z in range(z0 - 1, z0 + d + 1):
            S(x, G + 4, z, "minecraft:" + cols[(x - x0) % 2])
    fz = z0 + d - 1 if front == "s" else z0
    bz = z0 if front == "s" else z0 + d - 1
    for x in range(x0, x0 + w):
        S(x, G + 1, fz, ["minecraft:barrel[facing=up]", "minecraft:spruce_slab[type=top]", "minecraft:hay_block[axis=x]",
                         "minecraft:melon", "minecraft:pumpkin"][(x + goods) % 5] if (x - x0) % 2 == 0 else "minecraft:spruce_slab[type=bottom]")
    for x in range(x0, x0 + w):
        S(x, G + 1, bz, "minecraft:spruce_planks") if x in (x0 + 1, x0 + 3) else None
    S(x0 + 2, G + 3, bz, "minecraft:lantern[hanging=true]")
    return (x0 + 2, G + 1, z0 + 1 if front == "s" else z0 + d - 2)


stall("stall_a", -12, -33, "s", ("red_wool", "white_wool"), 0)
k = stall("stall_b", 8, -33, "s", ("blue_wool", "white_wool"), 1)
stall("stall_c", -12, -51, "n", ("green_wool", "white_wool"), 2)
stall("stall_d", 8, -51, "n", ("orange_wool", "white_wool"), 3)
area.npc("Kiara", k[0], k[1], k[2], 180.0, "behind her stall on the south-east side of the square")
area.npc("Hina", -7, G + 1, -40, 270.0, "the square, west of the arrival")
area.npc("Alan", 7, G + 1, -37, 90.0, "the square, east of the arrival")

# ---------------------------------------------------------------- Sawyer's lumber yard (west edge of town)
LY = (-34, -27, -24, -16)
reserve("lumber_yard", *LY, m=1, tree_m=3)
for (x0, z0, ax, wood) in ((-33, -26, "x", "dark_oak_log"), (-33, -24, "x", "spruce_log"), (-27, -19, "z", "dark_oak_log"),
                           (-26, -26, "z", "spruce_log")):
    for lvl in range(3):
        n = 5 - lvl
        for t in range(n):
            if ax == "x":
                S(x0 + t, G + 1 + lvl, z0, f"minecraft:{wood}[axis=x]")
            else:
                S(x0, G + 1 + lvl, z0 + t, f"minecraft:{wood}[axis=z]")
for (x, z) in ((-30, -21), (-28, -22), (-31, -18)):
    S(x, G + 1, z, "minecraft:stripped_dark_oak_log[axis=y]")
for (x, z) in ((-32, -22), (-32, -19)):
    S(x, G + 1, z, "minecraft:dark_oak_fence")
    S(x + 1, G + 1, z, "minecraft:dark_oak_fence")
    S(x, G + 2, z, "minecraft:dark_oak_slab[type=bottom]")
    S(x + 1, G + 2, z, "minecraft:dark_oak_slab[type=bottom]")
S(-29, G + 1, -17, "minecraft:stonecutter[facing=north]")
S(-28, G + 1, -17, "minecraft:grindstone[face=floor,facing=north]")
for (x, z) in ((-26, -23), (-31, -23)):
    for h in range(1, 4):
        S(x, G + h, z, "minecraft:dark_oak_fence")
    S(x, G + 4, z, "minecraft:lantern[hanging=false]")
area.npc("Sawyer", -29, G + 1, -20, 0.0, "in the lumber yard on the west edge of town")

# ---------------------------------------------------------------- Jaeger's pond, dock and nets
for x in range(21, 28):
    for z in (-29, -28, -27):
        S(x, G, z, "minecraft:spruce_planks" if (x + z) % 2 else "minecraft:dark_oak_planks")
for x in (22, 24, 26):
    for z in (-30, -26):
        for y in range(G - 3, G + 1):
            S(x, y, z, "minecraft:spruce_log[axis=y]")
        S(x, G + 1, z, "minecraft:spruce_fence")
S(27, G + 1, -30, "minecraft:lantern[hanging=false]") if False else None
for z in (-30, -26):
    S(27, G + 1, z, "minecraft:spruce_fence")
    S(27, G + 2, z, "minecraft:lantern[hanging=false]")
for k in range(5):                                       # drying nets: fence frames hung with cobweb
    S(17 + k * 1, G + 1, -33, "minecraft:spruce_fence") if k in (0, 4) else None
for (x, z0) in ((17, -34), (21, -34)):
    pass
for x0 in (16, 19):
    for h in range(1, 4):
        S(x0, G + h, -33, "minecraft:spruce_fence")
        S(x0 + 2, G + h, -33, "minecraft:spruce_fence")
    for x in range(x0 + 1, x0 + 2):
        for h in range(1, 4):
            S(x, G + h, -33, "minecraft:cobweb")
    S(x0 + 1, G + 4, -33, "minecraft:spruce_slab[type=bottom]")
for (x, z) in ((20, -28), (20, -26)):
    S(x, G + 1, z, "minecraft:barrel[facing=up]")
S(20, G + 1, -30, "minecraft:composter[level=3]")
area.npc("Jaeger", 22, G + 1, -28, 270.0, "at the end of the dock on Tangleburg's pond, fishing nets behind him")
reserve("pond", 21, -34, 34, -22, m=0, tree_m=3, check=False)

# ---------------------------------------------------------------- the lookout tower (a Fairy Soul at the top)
TW = (-26, -12, -22, -8)
reserve("tower", *TW, m=1, tree_m=4)
clear_box(TW[0] - 1, G + 1, TW[1] - 1, TW[2] + 1, G + 16, TW[3] + 1)
for x in range(TW[0], TW[2] + 1):
    for z in range(TW[1], TW[3] + 1):
        S(x, G, z, "minecraft:mud_bricks")
        e_ = x in (TW[0], TW[2]) or z in (TW[1], TW[3])
        corner = x in (TW[0], TW[2]) and z in (TW[1], TW[3])
        for y in range(G + 1, G + 11):
            if e_:
                S(x, y, z, "minecraft:dark_oak_log[axis=y]" if corner else
                  "minecraft:glass_pane" if (y in (G + 4, G + 5, G + 8) and (x + z) % 2 == 0) else "minecraft:spruce_planks")
        S(x, G + 11, z, "minecraft:dark_oak_planks") if not (x == -24 and z == -11) else None
        if e_ or True:
            pass
for x in range(TW[0] - 1, TW[2] + 2):
    for z in range(TW[1] - 1, TW[3] + 2):
        S(x, G + 11, z, "minecraft:dark_oak_planks") if (x, z) != (-24, -11) else None
        if x in (TW[0] - 1, TW[2] + 1) or z in (TW[1] - 1, TW[3] + 1):
            S(x, G + 12, z, "minecraft:dark_oak_fence")
for y in range(G + 1, G + 13):
    S(-24, y, -11, "minecraft:ladder[facing=south]")
door_at(TW[2], -10, "east")
for (x, z) in ((TW[0] - 1, TW[1] - 1), (TW[2] + 1, TW[1] - 1), (TW[0] - 1, TW[3] + 1), (TW[2] + 1, TW[3] + 1)):
    S(x, G + 13, z, "minecraft:lantern[hanging=false]")
    S(x, G + 12, z, "minecraft:dark_oak_fence")
lay([(-21, -10), (-4, -10), (0, -12)], 2, LANE, "tower")
for p in PATHCOLS:
    pass
area.fairy_soul(-25, G + 12, -10)

# ---------------------------------------------------------------- the launch pad back to the Park
PX, PZ = 38, -10
reserve("pad", PX - 3, PZ - 3, PX + 3, PZ + 3, m=0, tree_m=4, check=False)
for dx in range(-2, 3):
    for dz in range(-2, 3):
        S(PX + dx, G, PZ + dz, "minecraft:slime_block" if abs(dx) < 2 and abs(dz) < 2 else "minecraft:stripped_dark_oak_log[axis=x]")
S(PX, G + 1, PZ, "minecraft:heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    S(PX + dx, G + 1, PZ + dz, "minecraft:dark_oak_fence")
    S(PX + dx, G + 2, PZ + dz, "minecraft:lantern[hanging=false]")
sign(PX + 4, G + 1, PZ - 2, ["", "The Park", "", ""], "dark_oak_sign[rotation=4]")
area.pad("galatea_to_park", PX - 1, G + 1, PZ - 1, PX + 1, G + 2, PZ + 1,
         destination=("skyrpg_skyblock:the_park", (194.5, 65.0, 74.5), 90.0))
area.spots["notes"].append(f"Park launch pad centre ({PX}, {G + 1}, {PZ}); box ({PX - 1}, {G + 1}, {PZ - 1}) to ({PX + 1}, {G + 2}, {PZ + 1})")

# the arrival: the square's heart
area.arrival(ARRIVAL[0], ARRIVAL[1], ARRIVAL[2], 180.0)
area.warp("galatea", ARRIVAL[0], ARRIVAL[1], ARRIVAL[2], 180.0)

# ---------------------------------------------------------------- the waterfall rock (a Fairy Soul behind the water)
MX0, MZ0, MX1, MZ1 = MOUND
for x in range(MX0, MX1 + 1):
    for z in range(MZ0, MZ1 + 1):
        de = min(x - MX0, MX1 - x, z - MZ0)
        hgt = G + min(12, 3 + 2 * de)
        for y in range(G + 1, hgt):
            S(x, y, z, CLIFFMIX(x, y, z))
        S(x, hgt, z, "minecraft:moss_block" if hash01(x, 1, z, 4) < 0.7 else "minecraft:mossy_cobblestone")
        if hgt >= G + 8 and hash01(x, 2, z, 4) < 0.25 and (x, z) not in ((109, -54), (110, -54), (111, -54)):
            S(x, hgt + 1, z, "minecraft:flowering_azalea" if hash01(x, 3, z, 4) < 0.5 else "minecraft:azalea")
reserve("waterfall", MX0, MZ0, MX1, MZ1, m=1, tree_m=3, check=False)
TOPY = G + 12
for x in range(108, 113):                      # the basin on top
    for z in range(-56, -53):
        S(x, TOPY, z, "minecraft:mossy_cobblestone")
for x in (109, 110, 111):
    S(x, TOPY + 1, -55, "minecraft:water")
    S(x, TOPY + 1, -54, "minecraft:water")
    S(x, TOPY, -54, "minecraft:mossy_cobblestone")
    S(x, TOPY + 1, -53, "minecraft:water[level=1]")
    for y in range(G, TOPY + 1):
        S(x, y if y > G else G + 1, -53, "minecraft:water[level=8]")
for x in (108, 112):
    for z in (-55, -54):
        S(x, TOPY + 1, z, "minecraft:mossy_cobblestone")
for x in range(108, 113):
    S(x, TOPY + 1, -56, "minecraft:mossy_cobblestone")
# the cavity behind the water
for x in range(108, 113):
    for z in range(-60, -53):
        for y in range(G + 1, G + 5):
            if x in (108, 112) and z in (-60,):
                continue
            area.clear(x, y, z)
for x in (109, 110, 111):
    for y in (G + 1, G + 2, G + 3):
        area.clear(x, y, -54)
        S(x, y, -53, "minecraft:water[level=8]")
for x in range(108, 113):
    for z in range(-60, -54):
        S(x, G, z, "minecraft:moss_block")
        S(x, G + 1, z, "minecraft:moss_carpet") if hash01(x, 1, z, 5) < 0.6 and (x, z) != (110, -58) else None
S(108, G + 1, -59, "minecraft:soul_lantern[hanging=false]")
S(112, G + 1, -59, "minecraft:glow_lichen[down=true]") if False else None
S(110, G + 4, -57, "minecraft:glow_lichen[down=true]") if False else None
for (x, z) in ((109, -58), (111, -57), (110, -56)):
    S(x, G + 4, z, "minecraft:hanging_roots")
S(110, G + 1, -58, "minecraft:air")
area.fairy_soul(110, G + 1, -58)
for x in range(103, 117):                           # a path of stepping stones to the pool, mossy boulders around
    pass

# ---------------------------------------------------------------- the Forest Temple
TX0, TZ0, TX1, TZ1 = TEMPLE
reserve("temple", TX0, TZ0, TX1, TZ1, m=2, tree_m=3, check=False)
clear_box(TX0 - 2, G + 1, TZ0 - 2, TX1 + 2, G + 12, TZ1 + 2)
TB = area.mix({"stone_bricks": 5, "mossy_stone_bricks": 5, "cracked_stone_bricks": 2, "mossy_cobblestone": 1})
for x in range(TX0 - 2, TX1 + 3):
    for z in range(TZ0 - 2, TZ1 + 3):
        S(x, G, z, TB(x, G, z) if (TX0 <= x <= TX1 and TZ0 <= z <= TZ1) else
          ("minecraft:mossy_cobblestone" if hash01(x, 1, z, 6) < 0.35 else "minecraft:moss_block"))
for x in range(TX0, TX1 + 1):
    for z in range(TZ0, TZ1 + 1):
        edge_ = x in (TX0, TX1) or z in (TZ0, TZ1)
        corner = x in (TX0, TX1) and z in (TZ0, TZ1)
        if edge_:
            for y in range(G + 1, G + 7):
                if corner:
                    S(x, y, z, "minecraft:chiseled_stone_bricks" if y in (G + 1, G + 6) else "minecraft:mossy_stone_bricks")
                elif y > G + 3 and hash01(x, y, z, 7) < 0.16:
                    continue
                else:
                    S(x, y, z, TB(x, y, z))
for x in range(TX0 - 1, TX1 + 2):                  # roof slabs, broken up
    for z in range(TZ0 - 1, TZ1 + 2):
        if hash01(x, 9, z, 8) < 0.66:
            S(x, G + 7, z, "minecraft:mossy_stone_brick_slab[type=bottom]" if hash01(x, 8, z, 8) < 0.7 else "minecraft:stone_brick_slab[type=bottom]")
        if hash01(x, 10, z, 8) < 0.4 and (x, G + 7, z) in area.piece.cells if False else False:
            pass
# entrance on the south side (x 78..80) with pillars
for x in (78, 79, 80):
    for y in range(G + 1, G + 6):
        area.clear(x, y, TZ1)
for x in (77, 81):
    for y in range(G + 1, G + 7):
        S(x, y, TZ1 + 1, "minecraft:chiseled_stone_bricks" if y == G + 6 else "minecraft:mossy_stone_bricks")
for x in range(77, 82):
    S(x, G + 6, TZ1 + 1, "minecraft:mossy_stone_brick_slab[type=bottom]")
for z in (TZ1 + 1, TZ1 + 2):
    for x in (78, 79, 80):
        S(x, G, z, "minecraft:mossy_stone_brick_stairs[facing=north,half=bottom]" if z == TZ1 + 2 else "minecraft:chiseled_stone_bricks")
# colonnade
for px in (74, 84):
    for pz in (-102, -98, -94):
        for y in range(G + 1, G + 7):
            S(px, y, pz, "minecraft:mossy_stone_bricks" if y % 2 else "minecraft:stone_bricks") if hash01(px, y, pz, 9) > 0.1 or y < G + 4 else None
# the altar
for dx in (-1, 0, 1):
    S(79 + dx, G + 1, -105, "minecraft:mossy_stone_brick_stairs[facing=south,half=bottom]")
S(79, G + 1, -104, "minecraft:chiseled_stone_bricks")
S(79, G + 2, -104, "minecraft:candle[candles=3,lit=true]")
S(78, G + 1, -104, "minecraft:soul_lantern[hanging=false]")
S(80, G + 1, -104, "minecraft:soul_lantern[hanging=false]")
for dx in (-2, 2):
    S(79 + dx, G + 1, -104, "minecraft:mossy_cobblestone_wall")
    S(79 + dx, G + 2, -104, "minecraft:soul_lantern[hanging=false]")
# overgrowth: moss, vines, bushes, a tree-root or two
for x in range(TX0, TX1 + 1):
    for z in range(TZ0, TZ1 + 1):
        h = hash01(x, 11, z, 10)
        if 3 <= (x - TX0) and (TX1 - x) >= 3 and h < 0.18 and area.get(x, G + 1, z) is None and (x, z) != (79, -103):
            S(x, G + 1, z, "minecraft:moss_carpet")
        elif h < 0.24 and area.get(x, G + 1, z) is None and 76 <= x <= 82 and z <= -100:
            pass
for x in range(TX0, TX1 + 1):
    for z in (TZ0, TZ1):
        for y in range(G + 6, G + 2, -1):
            if area.get(x, y, z) and hash01(x, y, z, 11) < 0.25 and area.get(x, y - 1, z) is None:
                S(x, y - 1, z, "minecraft:vine[" + ("south=true" if z == TZ0 else "north=true") + "]") if False else None
for (x, z) in ((TX0 + 1, TZ1 + 3), (TX1 - 1, TZ1 + 3), (TX0 - 3, -98), (TX1 + 3, -100)):
    S(x, G + 1, z, "minecraft:flowering_azalea")
# the secret vault: x 84..86, z -105..-102 behind a curtain of vines
for z in range(-105, -100):
    for y in range(G + 1, G + 6):
        S(83, y, z, "minecraft:mossy_stone_bricks")
for x in range(83, 87):
    for y in range(G + 1, G + 6):
        S(x, y, -101, "minecraft:mossy_stone_bricks")
for y in (G + 1, G + 2):
    S(83, y, -103, "minecraft:vine[north=true,south=true]")
S(86, G + 1, -105, "minecraft:chest[facing=south]")
S(85, G + 3, -103, "minecraft:soul_lantern[hanging=true]") if False else S(85, G + 4, -103, "minecraft:lantern[hanging=true]")
area.npc("spawn:forest_temple", 79, G + 1, -103, 0.0, "in front of the altar, in the Forest Temple")
area.fairy_soul(85, G + 1, -103)

# ---------------------------------------------------------------- the Dragon's Lair
LCX, LCZ = 95, -143


def lair_q(x, y, z):
    return ((x - LCX) / 15.0) ** 2 + ((z - LCZ) / 11.0) ** 2 + ((y - G - 1) / 13.0) ** 2


LAIRWALL = area.mix({"moss_block": 3, "oxidized_copper": 3, "mossy_cobblestone": 2, "warped_wart_block": 2, "tuff": 1, "weathered_copper": 1,
                     "waxed_oxidized_copper": 1})
FLOORMIX = area.mix({"moss_block": 4, "mossy_cobblestone": 2, "warped_wart_block": 1, "oxidized_copper": 1, "rooted_dirt": 1})
for x in range(LCX - 17, LCX + 18):
    for z in range(LCZ - 13, LCZ + 14):
        for y in range(G, G + 17):
            q = lair_q(x, y, z)
            if y == G:
                if q - ((y - G - 1) / 13.0) ** 2 < 1.0:
                    S(x, y, z, FLOORMIX(x, y, z))
            elif q < 1.0:
                area.clear(x, y, z)
            elif q < 1.16 and area.get(x, y, z) is not None and y < G + 15:
                b = LAIRWALL(x, y, z)
                if hash01(x, y, z, 12) < 0.03 and y > G + 4:
                    b = "minecraft:verdant_froglight"
                S(x, y, z, b)
reserve("lair", LCX - 17, LCZ - 13, LCX + 17, LCZ + 13, m=0, tree_m=0, check=False)
# the mouth: a corridor and a rock arch
for z in range(-135, -125):
    for x in range(92, 99):
        for y in range(G + 1, G + 6):
            area.clear(x, y, z)
    for x in range(92, 99):
        S(x, G, z, "minecraft:moss_block" if hash01(x, 1, z, 13) < 0.6 else "minecraft:mossy_cobblestone")
for z in range(-135, -126):
    for x in range(90, 101):
        for y in range(G + 6, G + 10):
            S(x, y, z, LAIRWALL(x, y, z) if (x, z) not in () else "minecraft:moss_block")
for z in (-131, -129):
    for y in range(G + 1, G + 6):
        S(91, y, z, "minecraft:oxidized_cut_copper")
        S(99, y, z, "minecraft:oxidized_cut_copper")
for x in range(91, 100):
    S(x, G + 5, -129, "minecraft:oxidized_cut_copper_stairs[facing=south,half=top]" if x in (91, 99) else "minecraft:cut_copper_slab[type=top]" if False else "minecraft:oxidized_cut_copper")
for y in range(G + 1, G + 5):
    S(92, y, -127, "minecraft:copper_lantern[hanging=false]") if y == G + 1 and False else None
S(92, G + 1, -127, "minecraft:soul_lantern[hanging=false]")
S(98, G + 1, -127, "minecraft:soul_lantern[hanging=false]")
# the ledge at the back, with a flight of steps up from the cave floor
for x in range(88, 103):
    for z in range(-153, -148):
        for y in range(G + 1, G + 7):
            S(x, y, z, "minecraft:oxidized_cut_copper" if y == G + 6 and (x + z) % 3 == 0 else "minecraft:moss_block" if y == G + 6 else LAIRWALL(x, y, z))
for r in range(5):
    z = -148 + r
    top = G + 5 - r
    for x in range(93, 98):
        for y in range(G + 1, top):
            S(x, y, z, LAIRWALL(x, y, z))
        S(x, top, z, "minecraft:oxidized_cut_copper_stairs[facing=north,half=bottom]")
for (x, z) in ((89, -152), (101, -152), (89, -149), (101, -149)):
    S(x, G + 7, z, "minecraft:verdant_froglight")
for (x, z) in ((95, -152), (94, -152), (96, -152)):
    S(x, G + 7, z, "minecraft:emerald_block") if x == 95 else S(x, G + 7, z, "minecraft:oxidized_copper")
S(95, G + 8, -152, "minecraft:emerald_block")
S(92, G + 7, -151, "minecraft:amethyst_cluster[facing=up]") if False else None
area.npc("Jade Dragon", 95, G + 7, -150, 0.0, "on the ledge at the back of the Dragon's Lair (pet seller)")
# a pool, hanging roots, glow lichen and berry vines, a skylight
for x in range(98, 105):
    for z in range(-141, -135):
        if math.hypot(x - 101, z - 138) <= 2.8 - 0 and True:
            pass
for x in range(98, 106):
    for z in range(-142, -134):
        if math.hypot(x - 101.5, z - (-138)) <= 2.9:
            S(x, G, z, "minecraft:water")
            S(x, G - 1, z, "minecraft:clay")
ceiling = []
for x in range(LCX - 16, LCX + 17):
    for z in range(LCZ - 12, LCZ + 13):
        for y in range(G + 14, G + 3, -1):
            if area.get(x, y, z) is None and area.get(x, y + 1, z) not in (None, "minecraft:air") and lair_q(x, y, z) < 1.0:
                ceiling.append((x, y, z))
                break
for (x, y, z) in ceiling:
    h = hash01(x, 14, z, 14)
    if h < 0.08:
        ln = 2 + int(hash01(x, 15, z, 14) * 5)
        for i in range(ln):
            if area.get(x, y - i, z) is not None:
                break
            S(x, y - i, z, "minecraft:cave_vines_plant[berries=true]" if i < ln - 1 else "minecraft:cave_vines[age=25,berries=true]")
    elif h < 0.14:
        S(x, y, z, "minecraft:hanging_roots")
    elif h < 0.16:
        S(x, y, z, "minecraft:spore_blossom")
for x in range(LCX - 14, LCX + 15):
    for z in range(LCZ - 10, LCZ + 11):
        if area.get(x, G + 1, z) is None and area.get(x, G, z) not in (None, "minecraft:water") and hash01(x, 16, z, 15) < 0.12:
            S(x, G + 1, z, "minecraft:moss_carpet" if hash01(x, 17, z, 15) < 0.6 else "minecraft:azalea" if hash01(x, 18, z, 15) < 0.5
              else "minecraft:big_dripleaf")
for dx in (0, 1):                                       # the skylight shaft
    for dz in (0, 1):
        x, z = 94 + dx, -144 + dz
        top = T.get((x, z), G + 26)
        for y in range(G + 12, top + 1):
            area.clear(x, y, z)
        S(x, top, z, "minecraft:green_stained_glass")
        S(x, top - 1, z, "minecraft:green_stained_glass")
# the hidden alcove east of the cave (a Fairy Soul behind a curtain of roots)
for x in range(110, 114):
    for z in range(-146, -143):
        for y in range(G + 1, G + 4):
            area.clear(x, y, z)
        S(x, G, z, "minecraft:moss_block")
for y in (G + 1, G + 2):
    S(109, y, -145, "minecraft:hanging_roots")
    S(110, y, -145, "minecraft:air")
S(112, G + 3, -145, "minecraft:verdant_froglight")
area.fairy_soul(112, G + 1, -145)

# ---------------------------------------------------------------- the plateau cave (glow berries, a Fairy Soul)
CX, CZ = CAVE_P
T0 = T[(CX, CZ)]
floor_end = T0
for i in range(0, 22):
    x = CX + i
    fl = T0 - 1 - i // 2
    floor_end = fl
    for z in range(CZ - 1, CZ + 2):
        for y in range(fl + 1, fl + 4):
            area.clear(x, y, z)
        S(x, fl, z, "minecraft:moss_block" if hash01(x, 1, z, 16) < 0.7 else "minecraft:mossy_cobblestone")
        for y in range(fl + 4, T.get((x, z), fl + 4)):
            if area.get(x, y, z) is None:
                S(x, y, z, "minecraft:tuff")
ROOMX0, ROOMX1 = CX + 21, CX + 29
for x in range(ROOMX0, ROOMX1 + 1):
    for z in range(CZ - 5, CZ + 6):
        if math.hypot((x - (ROOMX0 + ROOMX1) / 2) / 4.8, (z - CZ) / 5.4) > 1.0:
            continue
        S(x, floor_end, z, "minecraft:moss_block" if hash01(x, 2, z, 16) < 0.7 else "minecraft:rooted_dirt")
        for y in range(floor_end + 1, floor_end + 6):
            area.clear(x, y, z)
        if hash01(x, 3, z, 16) < 0.35:
            ln = 1 + int(hash01(x, 4, z, 16) * 4)
            for i in range(ln):
                S(x, floor_end + 5 - i, z, "minecraft:cave_vines_plant[berries=true]" if i < ln - 1 else "minecraft:cave_vines[age=25,berries=true]")
        elif hash01(x, 5, z, 16) < 0.25:
            S(x, floor_end + 1, z, "minecraft:glow_lichen[down=true]") if False else S(x, floor_end + 1, z, "minecraft:moss_carpet")
        elif hash01(x, 6, z, 16) < 0.04:
            S(x, floor_end + 1, z, "minecraft:azalea")
S(ROOMX1 - 1, floor_end, CZ, "minecraft:water")
S(ROOMX1 - 2, floor_end, CZ + 1, "minecraft:water")
S(ROOMX1 - 2, floor_end + 1, CZ - 2, "minecraft:big_dripleaf") if False else None
reserve("plateau_cave", CX - 3, CZ - 5, CX + 8, CZ + 5, m=0, tree_m=2, check=False)
area.fairy_soul(ROOMX1 - 3, floor_end + 1, CZ)
area.spots["notes"].append(f"Plateau cave: sinkhole ramp at ({CX}, {T0}, {CZ}) going east into a berry room at floor y {floor_end}")

# ---------------------------------------------------------------- ruins
RUIN = area.mix({"stone_bricks": 4, "mossy_stone_bricks": 5, "cracked_stone_bricks": 3, "mossy_cobblestone": 2, "chiseled_stone_bricks": 1})
RUINS = [("ruin_w1", -122, -50, 10, 8), ("ruin_w2", -88, -34, 8, 8), ("ruin_n1", 20, -100, 9, 8), ("ruin_n2", 124, -98, 10, 9),
         ("ruin_n3", 58, -132, 8, 8), ("ruin_n4", -18, -108, 9, 8)]
for (name, rx, rz, w, d) in RUINS:
    reserve(name, rx, rz, rx + w - 1, rz + d - 1, m=1, tree_m=2, check=False)
    for x in range(rx, rx + w):
        for z in range(rz, rz + d):
            y = gy(x, z)
            edge_ = x in (rx, rx + w - 1) or z in (rz, rz + d - 1)
            for yy in range(y + 1, y + 7):
                area.clear(x, yy, z)
            S(x, y, z, RUIN(x, y, z) if hash01(x, 1, z, 17) < 0.7 else "minecraft:moss_block")
            if edge_:
                corner = x in (rx, rx + w - 1) and z in (rz, rz + d - 1)
                hgt = (3 + int(hash01(x, 2, z, 17) * 4)) if corner else int(hash01(x, 3, z, 17) * 4.5)
                if (x, z) in ((rx + w // 2, rz), (rx + w // 2, rz + d - 1)):
                    hgt = 0
                for h in range(1, hgt + 1):
                    S(x, y + h, z, RUIN(x, y + h, z))
            elif hash01(x, 4, z, 17) < 0.1:
                S(x, y + 1, z, "minecraft:moss_carpet")
    cx_, cz_ = rx + w // 2, rz + d // 2
    S(cx_, gy(cx_, cz_) + 1, cz_, "minecraft:mossy_stone_brick_stairs[facing=north,half=bottom]")
    S(cx_ + 1, gy(cx_ + 1, cz_) + 1, cz_, "minecraft:chiseled_stone_bricks")

# ---------------------------------------------------------------- the trees
LOGS = set()
LOGS_NEAR = set()
TRUNKS = []
FIGS = []
fig_logs_all = []


TR_GRID = {}


def add_trunk(x, z):
    TRUNKS.append((x, z))
    TR_GRID.setdefault((x // 6, z // 6), []).append((x, z))


def near_trunk(x, z, r):
    gx, gz = x // 6, z // 6
    for a in range(gx - 2, gx + 3):
        for b in range(gz - 2, gz + 3):
            for (tx, tz) in TR_GRID.get((a, b), ()):
                if abs(tx - x) < r and abs(tz - z) < r:
                    return True
    return False


def ok_ground(x, z, rad=1):
    if (x, z) not in T or (x, z) in occ or edge(x, z) < 6:
        return False
    h = T[(x, z)]
    for dx in range(-rad, rad + 1):
        for dz in range(-rad, rad + 1):
            n = T.get((x + dx, z + dz))
            if n is None or abs(n - h) > 1 or (x + dx, z + dz) in occ:
                return False
    return True


def fig_tree(x, z, seed, big=False, climb=False, force_h=None):
    """A fig tree: jungle_log trunk and branches, azalea canopy. Returns True when placed."""
    y = T[(x, z)] + 1
    r = lambda k: hash01(x, k, z, seed)
    h = force_h or (5 + int(r(1) * 5))
    if big:
        h = 5 + int(r(1) * 2)
    cols = [(x, z)] + ([(x + 1, z), (x, z + 1), (x + 1, z + 1)] if big else [])
    logs = {}
    for i in range(h):
        for c in cols:
            logs[(c[0], y + i, c[1])] = "y"
    dirs = [(1, 0), (0, 1), (-1, 0), (0, -1)]
    k0 = int(r(3) * 4)
    tips = []
    ring = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1), (1, 0)]
    if climb:
        for k, (rx_, rz_) in enumerate(ring):
            logs[(x + rx_, y + k, z + rz_)] = "x" if k % 2 == 0 else "z"
            if rx_ and rz_:
                logs[(x + rx_, y + k - 1, z + rz_)] = "y"
    nb = 0 if climb else 3 + int(r(2) * 2)
    for b in range(nb):
        dx, dz = dirs[(k0 + b) % 4]
        by = y + h - 1 - int(r(10 + b) * 3) if big else y + max(2, h - 1 - int(r(10 + b) * 3))
        ln = 1 + int(r(20 + b) * 4)
        ox = x + (1 if dx > 0 and big else 0)
        oz = z + (1 if dz > 0 and big else 0)
        for t in range(1, ln + 1):
            logs[(ox + dx * t, by, oz + dz * t)] = "x" if dx else "z"
        tx, tz = ox + dx * ln, oz + dz * ln
        if r(30 + b) < 0.5 and not climb:
            logs[(tx, by + 1, tz)] = "y"
            tips.append((tx, by + 2, tz))
        else:
            tips.append((tx, by + 1, tz))
    if not (12 <= len(logs) <= 35):
        return False
    for (lx, ly, lz) in logs:
        if (lx, lz) not in T or (lx, lz) in occ and (lx, lz) not in [(x, z)] or area.get(lx, ly, lz) is not None:
            return False
        if (lx, ly, lz) in LOGS_NEAR:
            return False
    for (lx, ly, lz), ax in logs.items():
        S(lx, ly, lz, f"minecraft:jungle_log[axis={ax}]")
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                for dz in range(-2, 3):
                    LOGS_NEAR.add((lx + dx, ly + dy, lz + dz))
        LOGS.add((lx, ly, lz))
    # canopy: a broad crown and a blob at each branch tip
    ccx, ccz = x + (0.5 if big else 0), z + (0.5 if big else 0)
    blobs = [(ccx, y + h - 1, ccz, 3.6 + (0.8 if big else 0), 2.5)] + [(tx, ty, tz, 2.3, 1.8) for (tx, ty, tz) in tips]
    if climb:
        blobs = [(ccx, y + h, ccz, 3.6, 2.0)] + blobs[1:]
    for (bx, by, bz, rx_, ry_) in blobs:
        for dx in range(-4, 5):
            for dy in range(-3, 4):
                for dz in range(-4, 5):
                    q = (dx / rx_) ** 2 + (dy / ry_) ** 2 + (dz / rx_) ** 2
                    if q <= 1.0 and hash01(int(bx) + dx, int(by) + dy, int(bz) + dz, seed) > 0.12:
                        px, py, pz = int(round(bx)) + dx, int(round(by)) + dy, int(round(bz)) + dz
                        if area.get(px, py, pz) is None:
                            S(px, py, pz, "minecraft:flowering_azalea_leaves[persistent=true]" if hash01(px, py, pz, 5) < 0.38
                              else "minecraft:azalea_leaves[persistent=true]")
    if climb:
        for k, (rx_, rz_) in enumerate(ring):                  # keep the spiral walkable: two air above every step
            for yy in (y + k + 1, y + k + 2, y + k + 3):
                if (x + rx_, yy, z + rz_) not in logs:
                    area.clear(x + rx_, yy, z + rz_)
    add_trunk(x, z)
    FIGS.append((x, z, len(logs)))
    # a lantern under the canopy now and then
    if r(40) < 0.25 and tips:
        tx, ty, tz = tips[0]
        for yy in range(ty, ty - 4, -1):
            if area.get(tx, yy, tz) is None and area.get(tx, yy + 1, tz) not in (None, "minecraft:air") and area.get(tx, yy + 1, tz).endswith("_leaves"):
                S(tx, yy, tz, "minecraft:lantern[hanging=true]")
                break
    return True


def in_plateau(x, z):
    return x <= -36 and z <= -70


def in_north_reaches(x, z):
    return (x >= -35 and z <= -73) or (x >= 46 and z > -73)


def in_west_reaches(x, z):
    return x <= -36 and z >= -69


# three Chillblade clearings on the plateau top (kept free of trees)
CLEARINGS = [(-116, -122), (-122, -100), (-88, -100)]
for (cx_, cz_) in CLEARINGS:
    for x in range(cx_ - 10, cx_ + 11):
        for z in range(cz_ - 10, cz_ + 11):
            if math.hypot(x - cx_, z - cz_) <= 10:
                occ.add((x, z))
# the climbable fig with a Fairy Soul at the top (on the plateau, off the paths)
SOULTREE = None
cands = [(x, z) for (x, z) in T if in_plateau(x, z) and ok_ground(x, z, 1) and T[(x, z)] >= 98 and (x, z) not in occ]
cands.sort(key=lambda c: hash01(c[0], 7, c[1], 33))
for (x, z) in cands:
    if fig_tree(x, z, 77, big=False, climb=True, force_h=9):
        SOULTREE = (x, z)
        break
if SOULTREE:
    sx, sz = SOULTREE
    top = T[(sx, sz)] + 1 + 9
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if (dx, dz) not in ((1, 0), (1, -1), (0, -1)):
                area.clear(sx + dx, top, sz + dz)
                S(sx + dx, top, sz + dz, "minecraft:azalea_leaves[persistent=true]")
            for yy in (top + 1, top + 2, top + 3):
                area.clear(sx + dx, yy, sz + dz)
    for (ax_, az_) in ((1, 0), (1, -1), (0, -1)):
        area.clear(sx + ax_, top, sz + az_)
    area.fairy_soul(sx, top + 1, sz)
    area.spots["notes"].append(f"Climbable fig with a Fairy Soul at the top: trunk ({sx}, {T[(sx, sz)] + 1}, {sz}), soul at ({sx}, {top + 1}, {sz})")
else:
    raise SystemExit("no place for the soul tree")

order = sorted(T.keys(), key=lambda c: hash01(c[0], 5, c[1], 3))
# fig groves: the plateau first (dense), then the two Reaches
for quota, region, spacing in ((27, in_plateau, 8), (9, in_north_reaches, 10), (6, in_west_reaches, 10)):
    n = 0
    for (x, z) in order:
        if n >= quota:
            break
        if not region(x, z) or near_trunk(x, z, spacing):
            continue
        big = hash01(x, 8, z, 3) < 0.25
        if not ok_ground(x, z, 2 if big else 1):
            continue
        if fig_tree(x, z, 100 + n, big=big):
            n += 1
    print("fig trees", region.__name__, n)

# ordinary trees: dark oak, spruce and pale oak
ENT_BOXES = [(119, -158, 147, -128), (-146, -66, -118, -48), (30, -158, 66, -128), (100, -110, 147, -84)]
BOG_BOXES = [(-136, -44, -100, -8), (84, -40, 128, -6), (-30, -110, 10, -84)]


def density(x, z):
    for (a, b, c, d) in ENT_BOXES:
        if a <= x <= c and b <= z <= d:
            return 0.13
    if in_plateau(x, z):
        return 0.012 if T[(x, z)] >= 90 else 0.02
    if town_d(x, z) < 6:
        return 0.0
    return 0.055


ntree = 0
for (x, z) in order:
    if hash01(x, 9, z, 4) > density(x, z) if (x, z) in T else True:
        continue
    if not ok_ground(x, z, 0) or near_trunk(x, z, 5):
        continue
    if (x, T[(x, z)] + 1, z) in LOGS_NEAR:
        continue
    kind = hash01(x, 10, z, 5)
    y = T[(x, z)] + 1
    tall = 6 + int(hash01(x, 11, z, 5) * 4)
    if any((x + dx, y + dy, z + dz) in LOGS_NEAR for dx in (-3, 0, 3) for dy in (1, 4) for dz in (-3, 0, 3)):
        continue
    if kind < 0.4:
        area.oak(x, y, z, height=tall, radius=3, log="dark_oak_log", leaves="dark_oak_leaves", seed=x * 7 + z)
    elif kind < 0.78:
        area.spruce(x, y, z, height=tall + 3, seed=x * 3 + z)
    else:
        area.oak(x, y, z, height=tall, radius=3, log="pale_oak_log", leaves="pale_oak_leaves", seed=x + z * 5)
    add_trunk(x, z)
    ntree += 1
print("ordinary trees", ntree, "fig trees", len(FIGS))

# ---------------------------------------------------------------- lamps along the roads
lamps = 0
for (name, pts, w) in PATHSPECS:
    if name in ("west woods", "east woods", "to the waterfall") or w < 3:
        continue
    total = 0.0
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        length = math.hypot(x2 - x1, z2 - z1)
        n = int(length)
        for i in range(n):
            total += 1
            if total % 15 != 0:
                continue
            px, pz = x1 + (x2 - x1) * i / max(1, n), z1 + (z2 - z1) * i / max(1, n)
            nx, nz = -(z2 - z1) / length, (x2 - x1) / length
            side = 1 if (lamps % 2) else -1
            lx, lz = int(round(px + nx * (w / 2 + 1.5) * side)), int(round(pz + nz * (w / 2 + 1.5) * side))
            if (lx, lz) in T and (lx, lz) not in PATHCOLS and area.get(lx, T[(lx, lz)] + 1, lz) is None and (lx, lz) not in CHANNEL \
                    and not (PLX0 - 1 <= lx <= PLX1 + 1 and PLZ0 - 1 <= lz <= PLZ1 + 1):
                lamp_post(lx, lz, "lantern" if town_d(lx, lz) < 12 else "soul_lantern")
                for dx in range(-2, 3):
                    for dz in range(-2, 3):
                        occ.add((lx + dx, lz + dz))
                lamps += 1

# ---------------------------------------------------------------- boulders, reeds, lily pads, undergrowth
boulders = 0
for (x, z) in sorted(T):
    if hash01(x, 12, z, 41) > 0.0009 or (x, z) in occ or edge(x, z) < 5 or town_d(x, z) < 8:
        continue
    r = 1.3 + hash01(x, 13, z, 41) * 1.1
    y0 = T[(x, z)] + 1
    for dx in range(-3, 4):
        for dy in range(-1, 3):
            for dz in range(-3, 4):
                q = (dx / (r * 1.2)) ** 2 + (dy / r) ** 2 + (dz / r) ** 2
                if q <= 1.0 and area.get(x + dx, y0 + dy, z + dz) is None:
                    h = hash01(x + dx, y0 + dy, z + dz, 42)
                    S(x + dx, y0 + dy, z + dz, "minecraft:mossy_cobblestone" if h < 0.55 else "minecraft:moss_block" if h < 0.8 else "minecraft:andesite")
    for dx, dz in ((int(r) + 1, 0), (-int(r) - 1, 1), (1, int(r) + 1)):
        if area.get(x + dx, y0, z + dz) is None:
            S(x + dx, y0, z + dz, "minecraft:fern")
    for dx in range(-int(r) - 2, int(r) + 3):
        for dz in range(-int(r) - 2, int(r) + 3):
            occ.add((x + dx, z + dz))
    boulders += 1

for (x, z), bed in CHANNEL.items():
    if (x, z) in T and bed <= 65 and hash01(x, 14, z, 43) < 0.22 and area.get(x, 68, z) is None:
        S(x, 68, z, "minecraft:lily_pad")
canes = 0
for (x, z) in BANK:
    if (x, z) not in T or (x, z) in CHANNEL or (x, z) in PATHCOLS or hash01(x, 15, z, 44) > 0.2 or T[(x, z)] > 69:
        continue
    if not any((x + dx, z + dz) in CHANNEL for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        continue
    if area.get(x, T[(x, z)] + 1, z) is None and area.get(x, T[(x, z)], z) in ("minecraft:grass_block", "minecraft:mud", "minecraft:moss_block",
                                                                              "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:coarse_dirt"):
        for k in range(1, 2 + int(hash01(x, 16, z, 44) * 3)):
            S(x, T[(x, z)] + k, z, "minecraft:sugar_cane[age=0]")
        canes += 1


def undergrowth(x, z):
    r = hash01(x, 3, z, 5)
    if (x, z) in BANK and r < 0.2:
        return "minecraft:small_dripleaf[half=lower]" if False else "minecraft:fern"
    if r < 0.28:
        return "minecraft:fern"
    if r < 0.52:
        return "minecraft:short_grass"
    if r < 0.57:
        return "minecraft:azalea"
    if r < 0.60:
        return "minecraft:flowering_azalea"
    if r < 0.63:
        return "minecraft:brown_mushroom"
    if r < 0.69:
        return "minecraft:moss_carpet"
    if r < 0.71:
        return "minecraft:sweet_berry_bush[age=3]"
    if r < 0.74:
        return "minecraft:firefly_bush"
    if r < 0.77:
        return "minecraft:wildflowers"
    if r < 0.80:
        return "minecraft:leaf_litter"
    return None


area.scatter(set(T), 0.30, undergrowth, seed=4, avoid=lambda x, z: (x, z) in PATHCOLS or any(a <= x <= c and b <= z <= d for (_n, a, b, c, d) in RES) or
             (TOWN[0] <= x <= TOWN[2] and TOWN[1] <= z <= TOWN[3] and hash01(x, 9, z, 9) < 0.6))

# ---------------------------------------------------------------- fences, panes and walls connect to their neighbours
import kit as _kit
NSEW = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
cells = area.piece.cells
for (x, y, z), (entry, data) in list(cells.items()):
    bid = entry["id"]
    name = bid.split(":")[1]
    if name.endswith("_fence") and "nether" not in name:
        kind = "fence"
    elif name.endswith("_pane") or name == "iron_bars":
        kind = "pane"
    elif name.endswith("_wall"):
        kind = "wall"
    else:
        continue
    props = []
    for face, (dx, dz) in NSEW.items():
        n = cells.get((x + dx, y, z + dz))
        nid = n[0]["id"].split(":")[1] if n else ""
        solid = bool(nid) and _kit._full(n[0]["id"]) and not nid.endswith("_leaves")
        if kind == "fence":
            ok = (nid.endswith("_fence") and "nether" not in nid) or nid.endswith("_fence_gate") or solid
        elif kind == "pane":
            ok = nid.endswith("_pane") or nid == "iron_bars" or nid.endswith("glass") or solid
        else:
            ok = nid.endswith("_wall") or solid
        props.append(f"{face}={('low' if ok else 'none') if kind == 'wall' else ('true' if ok else 'false')}")
    area.piece.set(x, y, z, bid + "[" + ",".join(props) + "]")

# ---------------------------------------------------------------- zones and mobs (boxes cover the whole island, no overlaps)
def mob(kind, name, level, health, damage, xp, coins, mx_, every, a, b):
    return {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b), "indoors": True}


def chill(cx_, cz_):
    gyc = T[(cx_, cz_)]
    return mob("minecraft:stray", "Chillblade", 35, 8000, 500, 25, 20, 4, 8, (cx_ - 8, gyc - 1, cz_ - 8), (cx_ + 8, gyc + 9, cz_ + 8))


def bogged(a, b):
    return mob("minecraft:bogged", "Bogged", 40, 10000, 600, 30, 25, 4, 8, (a[0], 64, a[1]), (b[0], 86, b[1]))


def ent(a, b):
    return mob("minecraft:creaking", "Ent", 50, 20000, 800, 45, 40, 3, 10, (a[0], 64, a[1]), (b[0], 90, b[1]))


YL, YH = 0, 200
area.zone("Tangleburg", box=((-35, YL, -72), (45, YH, -1)), blocks={}, mobs=[])
area.zone("Evergreen Plateau", box=((-150, YL, -160), (-36, YH, -70)), blocks={}, mobs=[chill(*c) for c in CLEARINGS])
area.zone("West Reaches", box=((-150, YL, -69), (-36, YH, -1)), blocks={},
          mobs=[bogged((-136, -44), (-100, -8)), ent((-146, -66), (-118, -48))])
area.zone("Forest Temple", box=((62, YL, -112), (96, YH, -82)), blocks={}, mobs=[])
area.zone("Dragon's Lair", box=((76, YL, -160), (112, YH, -126)), blocks={}, mobs=[])
NR = [
    ((-35, -160), (75, -126), [ent((30, -158), (66, -128))]),
    ((113, -160), (149, -126), [ent((119, -158), (147, -128))]),
    ((-35, -125), (149, -113), []),
    ((-35, -112), (61, -82), [bogged((-30, -110), (10, -84))]),
    ((97, -112), (149, -82), [ent((100, -110), (147, -84))]),
    ((-35, -81), (149, -73), []),
    ((46, -72), (149, -1), [bogged((84, -40), (128, -6))]),
]
for (a, b, mobs) in NR:
    area.zone("North Reaches", box=((a[0], YL, a[1]), (b[0], YH, b[1])), blocks={}, mobs=mobs)

# ---------------------------------------------------------------- checks
import collections
jl = {(x, y, z) for (x, y, z), (e, _) in cells.items() if e["id"] == "minecraft:jungle_log"}
seen = set()
comps = []
for p in sorted(jl):
    if p in seen:
        continue
    stack, comp = [p], []
    seen.add(p)
    while stack:
        q = stack.pop()
        comp.append(q)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (q[0] + dx, q[1] + dy, q[2] + dz)
                    if n in jl and n not in seen:
                        seen.add(n)
                        stack.append(n)
    comps.append(len(comp))
print("fig trees", len(FIGS), "log components (26-connected)", len(comps), "sizes min/max", min(comps), max(comps),
      "bad", [c for c in comps if not 12 <= c <= 35])
# anything else that uses jungle or mangrove wood
bad_ids = sorted({e["id"] for (e, _) in cells.values() if "mangrove" in e["id"] or ("jungle" in e["id"] and e["id"] != "minecraft:jungle_log")})
print("forbidden wood ids:", bad_ids)
print("ordinary trees", ntree, "boulders", boulders, "lamps", lamps, "reeds", canes)
print("ramp: start", ramp_h[0], "end", round(ramp_h[-1], 1), "plateau core", T[(-104, -106)])
# Lushlilac in the woods (not in town, the temple or the lair)
print("lushlilacs", lushlilacs(area, T, 40, 5, lambda x, z: not (-35 <= x <= 45 and -72 <= z <= -1) and not (62 <= x <= 112 and z <= -82)))
area.save()
