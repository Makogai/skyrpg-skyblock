#!/usr/bin/env python3
"""The Dwarven Mines, west part (Hypixel's Upper Mines, Rampart's Quarry, Far Reserve and Goblin Burrows): one big rock
mass with the caverns carved out of it (a height map per column: floor + headroom), Mithril and Titanium veins in the
walls, floors and pillars, wooden scaffolding, mine-cart rails, a terraced quarry and the dim, gold-lined Goblin Burrows.

    python tools/builds/hub_areas/mines_west.py

Box x -180..-60, z -175..175, y 100..235 (world). "Floor y" below is the y of the floor block; players stand one above.
Doorways (5 wide, 5 tall, floor block at the given y): 2 (x=-60, z=-110, 176), 4 (z=-45, 175), 5 (z=60, 140), 6 (z=150, 128).
"""
import json
import math
import random
from collections import deque
from pathlib import Path

import numpy as np
from kit import Area, hash01
import dungeon_kit

OX, OY, OZ = -180, 100, -175
NX, NY, NZ = 120, 135, 350
area = Area("mines_west", (OX, OY, OZ), (NX, NY, NZ), version=1, shell=2,
            dimension="skyrpg_skyblock:dwarven_mines", island="Dwarven Mines")
rng = np.random.default_rng(11)
py = random.Random(5)

XW = (np.arange(NX) + OX).reshape(NX, 1).astype(np.float64)
ZW = (np.arange(NZ) + OZ).reshape(1, NZ).astype(np.float64)
YW = (np.arange(NY) + OY).reshape(1, NY, 1)

FLR = np.full((NX, NZ), -1, np.int16)      # floor block y of every carved column (-1: rock)
CEIL = np.full((NX, NZ), -1, np.int16)     # the highest air y of the column
OPEN = np.zeros((NX, NZ), bool)            # big caverns (decor goes here; corridors stay clear)
CORR = np.zeros((NX, NZ), bool)            # corridors, ramps
RAMPC = np.zeros((NX, NZ), bool)
OCC = np.zeros((NX, NZ), bool)             # decor footprints
ROUTE = np.zeros((NX, NZ), bool)           # walking lines, kept free of big decor
STAIR = {}                                 # (x, z) -> (facing uphill, floor y)
LIM = np.zeros((NX, NZ), bool)
LIM[(XW[:, 0] >= -176) & (XW[:, 0] <= -61), :] = True
LIM &= (ZW >= -171) & (ZW <= 169)


def n2(x, z, seed=0.0, scale=1.0):
    s = seed * 1.7
    return (np.sin(x * 0.083 * scale + s) * np.cos(z * 0.071 * scale + s * 0.6) + 0.6 * np.sin((x + z) * 0.047 * scale + s * 1.3)
            + 0.4 * np.sin(x * 0.19 * scale - z * 0.17 * scale + s * 2.1) + 0.25 * np.cos(x * 0.31 * scale + z * 0.29 * scale - s)) / 2.25


def stamp(mask, F, h, kind="open"):
    mask = mask & LIM
    Fa = np.broadcast_to(np.asarray(F, np.int16), (NX, NZ))
    ha = np.broadcast_to(np.asarray(h, np.int16), (NX, NZ))
    top = np.minimum(Fa + ha, 229)
    same = mask & (FLR == Fa)
    CEIL[same] = np.maximum(CEIL[same], top[same])
    new = mask & ~same
    FLR[new] = Fa[new]
    CEIL[new] = top[new]
    if kind == "open":
        OPEN[mask] = True
    else:
        CORR[mask] = True


def ix(x):
    return x - OX


def iz(z):
    return z - OZ


def rect(x1, z1, x2, z2, F, H, kind="corr"):
    m = np.zeros((NX, NZ), bool)
    m[ix(min(x1, x2)):ix(max(x1, x2)) + 1, iz(min(z1, z2)):iz(max(z1, z2)) + 1] = True
    stamp(m, F, H, kind)


def blob(cx, cz, rx, rz, F, H, seed, wob=0.13, min_h=7, rim=None):
    u = np.hypot((XW - cx) / rx, (ZW - cz) / rz) + wob * n2(XW, ZW, seed, 2.0)
    h = np.maximum(min_h, np.floor(H * np.sqrt(np.maximum(0, 1 - u ** 3)) * (1 + 0.1 * n2(XW, ZW, seed + 3, 1.5))))
    stamp(u < 1, F(u) if callable(F) else F, h)
    return u


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
DIRS = {(0, 1): "south", (0, -1): "north", (1, 0): "east", (-1, 0): "west"}


def ramp(sx, sz, d, count, T, width, H):
    """Stairs descending from floor T (the flat the first cell is level with), one block per cell along d."""
    up = OPP[DIRS[d]]
    for i in range(count):
        cx, cz = sx + d[0] * i, sz + d[1] * i
        for w in range(-(width // 2), width // 2 + 1):
            x, z = (cx + w, cz) if d[0] == 0 else (cx, cz + w)
            if not (0 <= ix(x) < NX and 0 <= iz(z) < NZ):
                continue
            FLR[ix(x), iz(z)] = T - i
            CEIL[ix(x), iz(z)] = min(T - i + H, 229)
            CORR[ix(x), iz(z)] = True
            RAMPC[ix(x), iz(z)] = True
            STAIR[(x, z)] = (up, T - i)


def route(points, half=2):
    for (x1, z1), (x2, z2) in zip(points, points[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            x, z = round(x1 + (x2 - x1) * i / n), round(z1 + (z2 - z1) * i / n)
            for dx in range(-half, half + 1):
                for dz in range(-half, half + 1):
                    if 0 <= ix(x + dx) < NX and 0 <= iz(z + dz) < NZ:
                        ROUTE[ix(x + dx), iz(z + dz)] = True


# ====================================================================== the carved spaces
# Upper Mines: a high, wide cavern, floor 176, a raised west plateau (182), a stair up to it
UF = 176
blob(-120, -125, 54, 38, UF, 34, 3, min_h=9)
blob(-163, -125, 12, 25, 182, 26, 4, wob=0.1, min_h=9)
rect(-86, -118, -61, -102, UF, 10, "open")                 # doorway 2 apron + passage (floor 176)
rect(-76, -112, -61, -108, UF, 6, "corr")                  # the 5-wide opening
# the stair up to the plateau at x -151..-146 (z -128..-122)
ramp(-151, -125, (1, 0), 6, 182, 7, 12)
# neck south to the quarry
rect(-130, -95, -102, -85, UF, 14, "corr")
for x in range(-130, -101):
    FLR[ix(x), iz(-84)] = UF
    CEIL[ix(x), iz(-84)] = UF + 14
    RAMPC[ix(x), iz(-84)] = True
    STAIR[(x, -84)] = ("north", UF)
    CORR[ix(x), iz(-84)] = True

# Rampart's Quarry: a terraced bowl, rim 175, bottom 169, 5-block terrace rings
def quarry_floor(u):
    level = np.clip(np.floor((0.80 - u) / 0.12) + 1, 0, 6)
    return (175 - level).astype(np.int16)


blob(-112, -48, 50, 36, quarry_floor, 26, 7, wob=0.1, min_h=9)
rect(-130, -83, -102, -80, 175, 12, "corr")                # connects the stair row to the rim
rect(-76, -47, -61, -43, 175, 6, "corr")                   # doorway 4 (floor 175)
rect(-72, -56, -61, -34, 175, 12, "open")
# the great stair down to the Far Reserve: two flights and a landing (x -116..-108)
rect(-116, -17, -108, -12, 175, 10, "corr")
ramp(-112, -11, (0, 1), 16, 175, 9, 9)                     # F 175 .. 160
rect(-116, 5, -108, 8, 159, 11, "corr")                    # landing
ramp(-112, 9, (0, 1), 17, 159, 9, 9)                       # F 159 .. 143 (z 9..25)
# Far Reserve: floor 142
blob(-118, 62, 42, 36, 142, 22, 9, min_h=8)
rect(-116, 26, -108, 32, 142, 10, "corr")
# doorway 5 (floor 140): tunnel east, two stairs up to 142
rect(-70, 58, -61, 62, 140, 6, "corr")
ramp(-72, 60, (1, 0), 2, 142, 5, 7)
rect(-78, 58, -73, 62, 142, 6, "corr")
# Goblin Burrows
rect(-120, 96, -116, 105, 142, 6, "corr")                  # from the Far Reserve
blob(-118, 116, 12, 12, 142, 9, 21, wob=0.1, min_h=6)      # A: the entrance chamber
rect(-131, 119, -127, 123, 142, 5, "corr")
ramp(-132, 121, (-1, 0), 6, 142, 5, 5)
rect(-151, 119, -138, 123, 136, 5, "corr")
blob(-160, 128, 12, 12, 136, 9, 22, wob=0.1, min_h=6)      # B
rect(-158, 138, -154, 139, 136, 5, "corr")
ramp(-156, 140, (0, 1), 4, 136, 5, 5)
rect(-158, 144, -154, 148, 132, 5, "corr")
blob(-150, 156, 11, 11, 132, 9, 23, wob=0.1, min_h=6)      # C
rect(-142, 154, -139, 158, 132, 5, "corr")
ramp(-138, 156, (1, 0), 4, 132, 5, 5)
rect(-134, 154, -124, 158, 128, 5, "corr")
blob(-112, 156, 13, 13, 128, 10, 24, wob=0.1, min_h=6)     # D: the big chamber
rect(-108, 116, -105, 120, 142, 5, "corr")
ramp(-104, 118, (1, 0), 4, 142, 5, 5)
rect(-100, 116, -97, 120, 138, 5, "corr")
blob(-86, 122, 11, 11, 138, 9, 25, wob=0.1, min_h=6)       # E
ramp(-80, 132, (0, 1), 10, 138, 5, 5)
rect(-82, 142, -78, 147, 128, 5, "corr")
rect(-101, 148, -61, 152, 128, 5, "corr")                  # doorway 6 corridor (floor 128)
# fairy soul rooms (hidden stubs)
rect(-90, 141, -88, 147, 128, 5, "corr")
rect(-93, 136, -85, 140, 128, 5, "open")
rect(-168, 155, -162, 157, 132, 5, "corr")
rect(-173, 152, -167, 160, 132, 5, "open")

AIR3 = (YW > FLR[:, None, :]) & (YW <= CEIL[:, None, :]) & (FLR[:, None, :] >= 0)
print("air cells", int(AIR3.sum()), "carved columns", int((FLR >= 0).sum()))


def dil(m, n=1):
    for _ in range(n):
        o = m.copy()
        o[1:] |= m[:-1]
        o[:-1] |= m[1:]
        o[:, 1:] |= m[:, :-1]
        o[:, :-1] |= m[:, 1:]
        o[:, :, 1:] |= m[:, :, :-1]
        o[:, :, :-1] |= m[:, :, 1:]
        m = o
    return m


SOLID = ~AIR3
ADJ = dil(AIR3, 1) & SOLID
SHELL = dil(AIR3, 2) & SOLID
del AIR3

# ====================================================================== materials
PAL, NAMES = {}, []


def bid(block):
    block = block if ":" in block.split("[")[0] else "minecraft:" + block
    if block not in PAL:
        PAL[block] = len(NAMES)
        NAMES.append(block)
    return PAL[block]


M = np.full((NX, NY, NZ), -1, np.int16)
r0 = rng.random((NX, NY, NZ), dtype=np.float32)
r1 = rng.random((NX, NY, NZ), dtype=np.float32)
cl = np.repeat(np.repeat(np.repeat(rng.random((NX // 5 + 1, NY // 5 + 1, NZ // 5 + 1), dtype=np.float32), 5, 0), 5, 1), 5, 2)[:NX, :NY, :NZ]
REGION = [bid("stone"), bid("andesite"), bid("tuff"), bid("cobbled_deepslate"), bid("deepslate"), bid("cobblestone")]
edges = [0.0, 0.34, 0.54, 0.70, 0.84, 0.94, 1.0001]
for k in range(6):
    m = SHELL & (cl >= edges[k]) & (cl < edges[k + 1])
    M[m & (r0 < 0.78)] = REGION[k]
    other = m & (r0 >= 0.78)
    M[other & (r1 < 0.25)] = REGION[0]
    M[other & (r1 >= 0.25) & (r1 < 0.5)] = REGION[1]
    M[other & (r1 >= 0.5) & (r1 < 0.75)] = REGION[2]
    M[other & (r1 >= 0.75)] = REGION[3]
M[SHELL & (M < 0)] = REGION[0]

FLOORM = SHELL & SOLID & (YW == FLR[:, None, :]) & (FLR[:, None, :] >= 0)
zq = (np.arange(NZ) + OZ)
BURROW = (zq >= 102).reshape(1, 1, NZ)
QUARRYZ = ((zq >= -88) & (zq < -12)).reshape(1, 1, NZ)
r2 = rng.random((NX, NY, NZ), dtype=np.float32)


def put(mask, weights, rr):
    tot = float(sum(weights.values()))
    acc = 0.0
    for name, w in weights.items():
        lo, hi = acc / tot, (acc + w) / tot
        M[mask & (rr >= lo) & (rr < hi)] = bid(name)
        acc += w


put(FLOORM & ~BURROW & ~QUARRYZ, {"stone": 26, "andesite": 20, "cobblestone": 14, "gravel": 9, "polished_andesite": 10, "tuff": 9,
                                  "coarse_dirt": 0}, r2)
put(FLOORM & QUARRYZ, {"stone_bricks": 14, "stone": 22, "andesite": 18, "cobblestone": 14, "polished_andesite": 10,
                       "cracked_stone_bricks": 6, "gravel": 8, "tuff": 8}, r2)
put(FLOORM & BURROW, {"blackstone": 24, "polished_blackstone": 12, "tuff": 18, "cobbled_deepslate": 22, "gravel": 10,
                      "deepslate": 8, "gold_block": 6}, r2)
# the Burrows are lined with gold
r3 = rng.random((NX, NY, NZ), dtype=np.float32)
put(ADJ & BURROW & ~FLOORM, {"gold_block": 46, "raw_gold_block": 10, "gold_ore": 13, "blackstone": 9, "cobbled_deepslate": 12,
                             "tuff": 6, "yellow_terracotta": 4}, r3)
inner = SHELL & ~ADJ & BURROW
M[inner & (r3 < 0.10)] = bid("gold_block")
M[inner & (r3 >= 0.10) & (r3 < 0.14)] = bid("gold_ore")
M[inner & (r3 >= 0.14) & (r3 < 0.30)] = bid("blackstone")
# Mithril rock-walls of the mines: a few darker tuff/deepslate stripes already come from the region clusters
del r0, r1, r2, r3, cl, inner

# ====================================================================== veins (Mithril, Titanium, gold ore)
MITH_BLOCKS = ["gray_wool", "cyan_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine", "light_blue_wool"]
TITAN = "polished_diorite"
Fnear = FLR.copy()
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
    Fnear = np.maximum(Fnear, np.roll(np.roll(FLR, dx, 0), dz, 1))
STAIRMASK = np.zeros((NX, NZ), bool)
for (x, z) in STAIR:
    STAIRMASK[ix(x), iz(z)] = True
REACH = ADJ & (Fnear[:, None, :] >= 0) & (YW >= Fnear[:, None, :]) & (YW <= Fnear[:, None, :] + 4)
CLAIM = np.zeros((NX, NY, NZ), bool)
vein_count = {"mithril": 0, "titanium": 0, "gold_ore": 0}


def mith_block(grade, depth):
    r = py.random()
    g3 = min(0.45, 0.06 + 0.04 * depth) if grade == 3 else 0
    if grade == 1:
        t = (0.88, 0.99)
    elif grade == 2:
        t = (0.45, 0.95)
    else:
        t = (0.2, 0.7)
    r2_ = py.random()
    if py.random() < 0.02:
        vein_count["titanium"] += 1
        return TITAN
    vein_count["mithril"] += 1
    if r2_ < t[0]:
        return "gray_wool" if py.random() < 0.5 else "cyan_terracotta"
    if r2_ < t[1]:
        q = py.random()
        return "prismarine" if q < 0.4 else "prismarine_bricks" if q < 0.75 else "dark_prismarine"
    return "light_blue_wool"


def grow_veins(zlo, zhi, count, seed, sizes=(3, 9)):
    sel = np.zeros((NX, NY, NZ), bool)
    sel[:, :, iz(zlo):iz(zhi)] = True
    cand = np.argwhere(REACH & sel)
    if len(cand) == 0:
        return
    pick = np.random.default_rng(seed).choice(len(cand), size=min(count, len(cand)), replace=False)
    for ci in pick:
        a, b, c = cand[ci]
        if CLAIM[a, b, c] or STAIRMASK[a, c]:
            continue
        depth = max(0, (176 - int(Fnear[a, c])) / 6.0)
        grade = 1 if py.random() < 0.6 else 2 if py.random() < 0.75 else 3
        size = py.randint(*sizes)
        cells, frontier = [(a, b, c)], [(a, b, c)]
        CLAIM[a, b, c] = True
        while frontier and len(cells) < size:
            ca, cb, cc = frontier[py.randrange(len(frontier))]
            nb = [(ca + 1, cb, cc), (ca - 1, cb, cc), (ca, cb + 1, cc), (ca, cb - 1, cc), (ca, cb, cc + 1), (ca, cb, cc - 1)]
            py.shuffle(nb)
            for (na, nb_, nc) in nb:
                if 0 <= na < NX and 0 <= nb_ < NY and 0 <= nc < NZ and iz(zlo) <= nc < iz(zhi) and SHELL[na, nb_, nc] \
                        and not CLAIM[na, nb_, nc] and not STAIRMASK[na, nc]:
                    # keep the vein on the cave surface layers (never above a head-high ceiling)
                    if nb_ + OY > Fnear[na, nc] + 5 and Fnear[na, nc] >= 0 and nb_ + OY > FLR[a, c] + 5:
                        continue
                    CLAIM[na, nb_, nc] = True
                    cells.append((na, nb_, nc))
                    frontier.append((na, nb_, nc))
                    break
            else:
                frontier.remove((ca, cb, cc))
        for (na, nb_, nc) in cells:
            M[na, nb_, nc] = bid(mith_block(grade, depth))


# zones in z (world): Upper Mines [-175,-88), Rampart's Quarry [-88,-12), Far Reserve [-12,102), Goblin Burrows [102,175)
ZUP, ZQ, ZFR, ZGB = (-175, -88), (-88, -12), (-12, 102), (102, 175)
grow_veins(*ZUP, 480, 1)
grow_veins(*ZQ, 480, 2)
grow_veins(*ZFR, 560, 3)
print("veins", vein_count)

# ====================================================================== write the rock into the build
ST = [dungeon_kit.state(n) for n in NAMES]
cells = area.piece.cells
pos = np.argwhere(SHELL & (M >= 0))
vals = M[SHELL & (M >= 0)]
for (a, b, c), v in zip(pos.tolist(), vals.tolist()):
    cells[(a, b, c)] = (ST[v], None)
print("rock cells", len(cells))


# ====================================================================== helpers for the dressing
def blk(x, y, z):
    c = area.piece.cells.get((x - OX, y - OY, z - OZ))
    return 'minecraft:air' if c is None else c[0]['id']


def carved(x, z):
    return 0 <= ix(x) < NX and 0 <= iz(z) < NZ and FLR[ix(x), iz(z)] >= 0


def fl(x, z):
    return int(FLR[ix(x), iz(z)])


def ce(x, z):
    return int(CEIL[ix(x), iz(z)])


def occ(x, z, r):
    OCC[max(0, ix(x - r)):ix(x + r) + 1, max(0, iz(z - r)):iz(z + r) + 1] = True


def free(x, z, r, flat=True, need_open=True):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            a, b = ix(x + dx), iz(z + dz)
            if not (0 <= a < NX and 0 <= b < NZ) or FLR[a, b] < 0 or OCC[a, b] or ROUTE[a, b] or RAMPC[a, b]:
                return False
            if need_open and not OPEN[a, b]:
                return False
            if flat and FLR[a, b] != FLR[ix(x), iz(z)]:
                return False
    return True


def find(zr, xr, r, tries=500, flat=True):
    for _ in range(tries):
        x, z = py.randint(*xr), py.randint(*zr)
        if free(x, z, r, flat):
            return x, z
    return None


def sign(x, y, z, rot, l2, l3, wood="spruce"):
    area.set(x, y, z, f"minecraft:{wood}_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": ['""', f'"{l2}"', f'"{l3}"', '""'],
                                                     "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def rock(x, y, z):
    r = py.random()
    area.set(x, y, z, "minecraft:stone" if r < 0.4 else "minecraft:andesite" if r < 0.65 else "minecraft:tuff" if r < 0.8 else "minecraft:cobbled_deepslate")


def mithril_here(x, y, z, depth=0.0):
    area.set(x, y, z, "minecraft:" + mith_block(1 if py.random() < 0.65 else 2 if py.random() < 0.8 else 3, depth))


def post(x, y, z, h=2, lamp="lantern[hanging=false]", wall="spruce_fence"):
    for i in range(h):
        area.set(x, y + i, z, "minecraft:" + wall)
    area.set(x, y + h, z, "minecraft:" + lamp)


for (x, z), (facing, F) in STAIR.items():
    b_ = "blackstone_stairs" if z >= 102 else "stone_brick_stairs"
    if b_ == "stone_brick_stairs" and (x + z) % 5 == 0:
        b_ = "cobblestone_stairs"
    area.set(x, F, z, f"minecraft:{b_}[facing={facing},half=bottom]")

# walking lines (kept clear of big decor)
route([(-61, -110), (-100, -110), (-125, -110), (-125, -125), (-146, -125), (-160, -125)])
route([(-100, -110), (-115, -92), (-115, -70), (-112, -50), (-112, -12), (-112, 28), (-118, 60), (-118, 100), (-118, 116)])
route([(-61, -45), (-100, -45), (-112, -48)])
route([(-61, 60), (-118, 60)])

SOULROOMS = [((-93, 136), (-85, 140)), ((-173, 152), (-167, 160)), ((-168, 155), (-162, 157)), ((-90, 141), (-88, 147))]
for (a, b), (c, d) in SOULROOMS:
    OCC[ix(a):ix(c) + 1, iz(b):iz(d) + 1] = True


# ---------------------------------------------------------------- NPC daises
def dais(cx, cz):
    F = fl(cx, cz)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            edge = max(abs(dx), abs(dz)) == 2
            area.set(cx + dx, F, cz + dz, "minecraft:stone_bricks" if edge else "minecraft:polished_andesite")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        post(cx + dx, F + 1, cz + dz, 1, "lantern[hanging=false]", "stone_brick_wall")
    occ(cx, cz, 3)


FRAISER = (-82, -103)
CARLTON = (-67, -54)
dais(*FRAISER)
dais(*CARLTON)
area.set(FRAISER[0] - 1, fl(*FRAISER) + 1, FRAISER[1] - 2, "minecraft:barrel[facing=up]")
area.set(CARLTON[0] - 1, fl(*CARLTON) + 1, CARLTON[1] + 2, "minecraft:barrel[facing=up]")
sign(-78, 177, -113, 12, "Upper Mines", "Mithril & Titanium")
sign(-64, 176, -50, 12, "Rampart's Quarry", "Mithril")
area.npc("Emissary Fraiser", FRAISER[0], 177, FRAISER[1], yaw=270.0, note="Emissary: commissions (Upper Mines)")
area.npc("Emissary Carlton", CARLTON[0], 176, CARLTON[1], yaw=270.0, note="Emissary: commissions (Rampart's Quarry)")


# ---------------------------------------------------------------- rails
def rail_line(x1, x2, z, F, buffers=True):
    x = x1
    placed = []
    while x <= x2:
        if carved(x, z) and fl(x, z) == F and not ROUTE[ix(x), iz(z)] and not OCC[ix(x), iz(z)] and OPEN[ix(x), iz(z)]:
            placed.append(x)
        elif placed:
            break
        x += 1
    if len(placed) < 8:
        return 0
    for x in placed:
        area.set(x, F + 1, z, "minecraft:powered_rail[shape=east_west,powered=false]" if (x - placed[0]) % 9 == 4
                 else "minecraft:rail[shape=east_west]")
        OCC[ix(x), iz(z) - 1:iz(z) + 2] = True
    if buffers:
        for x in (placed[0] - 1, placed[-1] + 1):
            if carved(x, z) and fl(x, z) == F:
                area.set(x, F + 1, z, "minecraft:stone_brick_wall")
                area.set(x, F + 2, z, "minecraft:lantern[hanging=false]")
    return len(placed)


print("rails", rail_line(-175, -153, -140, 182), rail_line(-172, -100, -155, 176), rail_line(-172, -96, -98, 176))
for z in (30, 44, 80, 92):
    print(" far rail", z, rail_line(-158, -78, z, 142))


# ---------------------------------------------------------------- wooden platforms with stairs (reachable)
def platform(cx, cz, rot, n=4):
    """A 5x5 spruce deck n blocks above the floor with a 3-wide stair up; rot picks the side the stair comes from."""
    def tr(u, v):
        return [(cx + u, cz + v), (cx - u, cz - v), (cx + v, cz + u), (cx + v, cz - u)][rot]
    face = ["east", "west", "south", "north"][rot]
    F = fl(cx, cz)
    for u in range(-3 - n, 4):
        for v in range(-3, 4):
            x, z = tr(u, v)
            if not free(x, z, 0, flat=True) or fl(x, z) != F:
                return False
    for u in range(-3 - n, 4):
        for v in range(-3, 4):
            x, z = tr(u, v)
            OCC[ix(x), iz(z)] = True
    for u in range(-2, 3):
        for v in range(-2, 3):
            x, z = tr(u, v)
            area.set(x, F + n, z, "minecraft:spruce_planks" if (u + v) % 2 else "minecraft:oak_planks")
    for (u, v) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        x, z = tr(u, v)
        for y in range(F + 1, F + n):
            area.set(x, y, z, "minecraft:spruce_log[axis=y]")
        area.set(x, F + n + 1, z, "minecraft:spruce_fence")
        area.set(x, F + n + 2, z, "minecraft:lantern[hanging=false]")
    for k in range(1, n + 1):
        u = -2 - n + (k - 1)
        for v in (-1, 0, 1):
            x, z = tr(u, v)
            area.set(x, F + k, z, f"minecraft:spruce_stairs[facing={face},half=bottom]")
            for y in range(F + 1, F + k):
                area.set(x, y, z, "minecraft:spruce_planks")
    for t in range(-2, 3):
        for (u, v) in ((2, t), (t, -2), (t, 2)):
            if (u, v) in ((2, -2), (2, 2), (-2, -2), (-2, 2)):
                continue
            x, z = tr(u, v)
            area.set(x, F + n + 1, z, "minecraft:spruce_fence")
    x, z = tr(1, 0)
    area.set(x, F + n + 1, z, "minecraft:barrel[facing=up]")
    x, z = tr(0, 1)
    area.set(x, F + n + 1, z, "minecraft:crafting_table")
    return True


def tower(cx, cz, extra=7):
    cols = [(cx + dx, cz + dz) for dx in (-2, 2) for dz in (-2, 2)]
    if not all(carved(x, z) and not OCC[ix(x), iz(z)] and not ROUTE[ix(x), iz(z)] for x, z in cols):
        return False
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if not carved(cx + dx, cz + dz) or RAMPC[ix(cx + dx), iz(cz + dz)] or ROUTE[ix(cx + dx), iz(cz + dz)]:
                return False
    top = max(fl(x, z) for x, z in cols) + extra
    if any(top + 3 > ce(x, z) for x, z in cols):
        return False
    for (x, z) in cols:
        for y in range(fl(x, z) + 1, top + 1):
            area.set(x, y, z, "minecraft:spruce_log[axis=y]" if y % 4 else "minecraft:stripped_spruce_log[axis=y]")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            area.set(cx + dx, top, cz + dz, "minecraft:spruce_planks" if (dx + dz) % 2 else "minecraft:oak_planks")
            if max(abs(dx), abs(dz)) == 2 and (dx, dz) not in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
                area.set(cx + dx, top + 1, cz + dz, "minecraft:spruce_fence")
    for (x, z) in cols:
        area.set(x, top + 1, z, "minecraft:spruce_fence")
        area.set(x, top + 2, z, "minecraft:lantern[hanging=false]")
    for t in range(-1, 2):
        y = top - 3
        area.set(cx + t, y, cz - 2, "minecraft:spruce_log[axis=x]")
        area.set(cx + t, y, cz + 2, "minecraft:spruce_log[axis=x]")
        area.set(cx - 2, y, cz + t, "minecraft:spruce_log[axis=z]")
        area.set(cx + 2, y, cz + t, "minecraft:spruce_log[axis=z]")
    area.set(cx, top + 1, cz, "minecraft:barrel[facing=up]")
    occ(cx, cz, 3)
    return True


def mound(cx, cz, R, depth=0.0):
    cols = {}
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            d = math.hypot(dx, dz)
            if d > R + 0.3 or not carved(cx + dx, cz + dz):
                continue
            h = 1
            if d <= R - 1.5:
                h = 2
            if d <= R - 3.0:
                h = 3
            cols[(cx + dx, cz + dz)] = fl(cx + dx, cz + dz) + h
    for (x, z), top in cols.items():
        base = fl(x, z)
        for y in range(base + 1, top + 1):
            exposed = y == top or any(cols.get((x + a, z + b), -1) < y for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if exposed and py.random() < 0.58:
                mithril_here(x, y, z, depth)
            else:
                rock(x, y, z)
    occ(cx, cz, R + 1)


def pillar(cx, cz, r=2):
    cols = [(cx + dx, cz + dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= r * r + 1]
    if not all(carved(x, z) for x, z in cols):
        return False
    for (x, z) in cols:
        for y in range(fl(x, z) + 1, ce(x, z) + 1):
            if py.random() < 0.2:
                mithril_here(x, y, z, 0.5)
            else:
                rock(x, y, z)
    occ(cx, cz, r + 2)
    return True


# ---------------------------------------------------------------- Upper Mines dressing
UP_X, UP_Z = (-172, -66), (-163, -88)
placed_up = 0
for k in range(40):
    pos_ = find(UP_Z, UP_X, 5, 200)
    if not pos_:
        break
    x, z = pos_
    if k < 5 and platform(x, z, py.randrange(4), 4):
        placed_up += 1
    elif k < 12 and tower(x, z):
        placed_up += 1
    elif k >= 12 and pillar(x, z, 2):
        placed_up += 1
        if placed_up > 22:
            break
print("upper dressing", placed_up)
for k in range(30):
    pos_ = find(UP_Z, UP_X, 4, 300, flat=False)
    if pos_:
        mound(pos_[0], pos_[1], py.randint(3, 4))
# the plateau: a fence along its edge, a stash, the fairy soul corner
for z in range(-150, -99):
    if carved(-152, z) and fl(-152, z) == 182 and not (-129 <= z <= -121):
        area.set(-152, 183, z, "minecraft:spruce_fence")
for (x, z) in ((-160, -112), (-168, -118), (-158, -138)):
    if carved(x, z) and carved(x + 1, z + 1):
        for (dx, dz, h) in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (1, 1, 2)):
            area.set(x + dx, 183, z + dz, "minecraft:barrel[facing=up]" if h == 1 else "minecraft:oak_planks")
            if h == 2:
                area.set(x + dx, 184, z + dz, "minecraft:barrel[facing=up]")
SOUL1 = (-165, 183, -146)
for dx, dz in ((-1, 0), (1, 0), (0, -1)):
    if carved(SOUL1[0] + dx, SOUL1[2] + dz):
        area.set(SOUL1[0] + dx, 183, SOUL1[2] + dz, "minecraft:barrel[facing=up]")
        area.set(SOUL1[0] + dx, 184, SOUL1[2] + dz, "minecraft:barrel[facing=up]")
area.set(SOUL1[0], 182, SOUL1[2], "minecraft:gold_block")
# a pool (fishing spot)
spot = find((-155, -95), (-110, -70), 6, 400)
if spot:
    px_, pz_ = spot
    F = fl(px_, pz_)
    for dx in range(-5, 6):
        for dz in range(-4, 5):
            if (dx / 5.0) ** 2 + (dz / 4.0) ** 2 <= 1:
                for y in range(F - 2, F + 1):
                    area.set(px_ + dx, y, pz_ + dz, "minecraft:water")
                area.set(px_ + dx, F - 3, pz_ + dz, "minecraft:stone")
    area.set(px_, F - 3, pz_, "minecraft:sea_lantern")
    for dx in (-6, 6):
        area.set(px_ + dx, F + 1, pz_, "minecraft:spruce_fence")
        area.set(px_ + dx, F + 2, pz_, "minecraft:lantern[hanging=false]")
    occ(px_, pz_, 7)
    print("pool at", spot)

# ---------------------------------------------------------------- Rampart's Quarry dressing
Q_X, Q_Z = (-160, -62), (-83, -10)
n_t = 0
for k in range(60):
    pos_ = find(Q_Z, Q_X, 3, 100, flat=False)
    if pos_ and tower(*pos_):
        n_t += 1
    if n_t >= 7:
        break
n_m = 0
for k in range(60):
    pos_ = find(Q_Z, Q_X, 3, 200, flat=False)
    if pos_:
        mound(pos_[0], pos_[1], py.randint(2, 3), depth=(176 - fl(*pos_)) / 6.0)
        n_m += 1
    if n_m >= 26:
        break
n_c = 0
for k in range(80):                      # stonecutters, barrels, anvils on the terraces
    pos_ = find(Q_Z, Q_X, 1, 100, flat=False)
    if pos_:
        x, z = pos_
        F = fl(x, z)
        r = py.random()
        area.set(x, F + 1, z, "minecraft:stonecutter[facing=north]" if r < 0.3 else "minecraft:barrel[facing=up]" if r < 0.7 else
                 "minecraft:anvil[facing=east]" if r < 0.85 else "minecraft:crafting_table")
        occ(x, z, 1)
        n_c += 1
print("quarry dressing", n_t, n_m, n_c)

# ---------------------------------------------------------------- Far Reserve dressing
FR_X, FR_Z = (-160, -76), (28, 97)
n_p = 0
for k in range(100):
    pos_ = find(FR_Z, FR_X, 4, 100)
    if pos_ and platform(pos_[0], pos_[1], py.randrange(4), py.choice((3, 4, 5))):
        n_p += 1
    if n_p >= 7:
        break
n_t = 0
for k in range(60):
    pos_ = find(FR_Z, FR_X, 3, 100)
    if pos_ and tower(*pos_):
        n_t += 1
    if n_t >= 4:
        break
n_pl = 0
for k in range(60):
    pos_ = find(FR_Z, FR_X, 3, 100)
    if pos_ and pillar(*pos_):
        n_pl += 1
    if n_pl >= 9:
        break
n_m = 0
for k in range(50):
    pos_ = find(FR_Z, FR_X, 4, 200, flat=False)
    if pos_:
        mound(pos_[0], pos_[1], py.randint(3, 4), depth=1.2)
        n_m += 1
    if n_m >= 18:
        break
print("far reserve dressing", n_p, n_t, n_pl, n_m)

# timber supports on the long stair (x -116..-108): arches every 8 cells
for z0 in range(-8, 26, 8):
    for x in (-116, -108):
        if carved(x, z0):
            for y in range(fl(x, z0) + 1, ce(x, z0) + 1):
                area.set(x, y, z0, "minecraft:spruce_log[axis=y]")
    for x in range(-116, -107):
        if carved(x, z0):
            area.set(x, ce(x, z0), z0, "minecraft:spruce_log[axis=x]")
    if carved(-112, z0):
        area.set(-112, ce(-112, z0) - 1, z0, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- lamps and ceilings (all mining caverns)
# ceiling glow, stalactites (mining caverns) and corridor lanterns
lit = []
stal = 0
for a in range(NX):
    for b in range(NZ):
        if FLR[a, b] < 0:
            continue
        x, z = a + OX, b + OZ
        top, F = int(CEIL[a, b]), int(FLR[a, b])
        if OPEN[a, b] and z < 102:
            if top - F >= 9:
                r = hash01(x, 5, z, 7)
                if r < 0.0030:
                    for dx, dz in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
                        if carved(x + dx, z + dz) and ce(x + dx, z + dz) == top:
                            area.set(x + dx, top, z + dz, "minecraft:glowstone" if z < -12 else "minecraft:shroomlight")
                elif r < 0.0075:
                    n = 1 + int(hash01(x, 6, z, 8) * 3)
                    for i in range(n):
                        area.set(x, top - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" +
                                 ("tip" if i == n - 1 else "frustum") + "]")
                    stal += 1
                elif r < 0.0105 and top - F >= 14:                 # hanging lanterns on chains
                    ln = 2 + int(hash01(x, 7, z, 9) * 4)
                    for i in range(ln):
                        area.set(x, top - i, z, "minecraft:chain[axis=y]")
                    area.set(x, top - ln, z, "minecraft:lantern[hanging=true]")
print("stalactites", stal)

# ---------------------------------------------------------------- Goblin Burrows dressing (dim)
CHAMBERS = {"A": (-118, 116, 12, 142), "B": (-160, 128, 12, 136), "C": (-150, 156, 11, 132), "D": (-112, 156, 13, 128),
            "E": (-86, 122, 11, 138)}


def gold_pile(x, z):
    F = fl(x, z)
    for (dx, dz, h) in ((0, 0, 3), (1, 0, 2), (-1, 0, 2), (0, 1, 2), (0, -1, 1), (1, 1, 1), (-1, -1, 1)):
        if carved(x + dx, z + dz):
            for y in range(fl(x + dx, z + dz) + 1, fl(x + dx, z + dz) + h + 1):
                area.set(x + dx, y, z + dz, "minecraft:gold_block" if y == F + h or py.random() < 0.6 else "minecraft:raw_gold_block")
    occ(x, z, 2)


def goblin_hut(x, z):
    F = fl(x, z)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            wall = max(abs(dx), abs(dz)) == 2
            for y in range(F + 1, F + 4):
                if wall:
                    door = dz == 2 and dx == 0 and y < F + 3
                    area.set(x + dx, y, z + dz, "minecraft:air" if door else "minecraft:spruce_planks" if y < F + 3 else "minecraft:spruce_slab[type=top]")
                else:
                    area.set(x + dx, y, z + dz, "minecraft:air")
            area.set(x + dx, F + 4, z + dz, "minecraft:spruce_slab[type=bottom]" if wall else "minecraft:spruce_planks")
    area.set(x, F + 1, z, "minecraft:campfire[lit=true]")
    area.set(x - 1, F + 1, z - 1, "minecraft:barrel[facing=up]")
    area.set(x + 1, F + 1, z - 1, "minecraft:gold_block")
    area.set(x, F + 1, z + 1, "minecraft:candle[candles=3,lit=true]")
    occ(x, z, 3)


for name, (cx, cz, r, F) in CHAMBERS.items():
    for k in range(4):
        a_ = py.random() * 6.283
        x, z = int(cx + math.cos(a_) * r * 0.45), int(cz + math.sin(a_) * r * 0.45)
        if carved(x, z) and fl(x, z) == F:
            t = ce(x, z)
            ln = 2 + py.randint(0, 2)
            for i in range(ln):
                area.set(x, t - i, z, "minecraft:chain[axis=y]")
            area.set(x, t - ln, z, "minecraft:soul_lantern[hanging=true]")
    for k in range(6):
        a_ = py.random() * 6.283
        d_ = py.uniform(0.3, 0.7) * r
        x, z = int(cx + math.cos(a_) * d_), int(cz + math.sin(a_) * d_)
        if carved(x, z) and fl(x, z) == F and not ROUTE[ix(x), iz(z)] and not OCC[ix(x), iz(z)] and \
                area.get(x, F + 1, z) in (None, "minecraft:air"):
            if k < 2:
                gold_pile(x, z)
            else:
                area.set(x, F + 1, z, "minecraft:candle[candles=3,lit=true]" if k == 2 else "minecraft:barrel[facing=up]" if k == 3
                         else "minecraft:gold_block" if k == 4 else "minecraft:cobweb")
for (x, z) in ((-120, 163), (-105, 164), (-148, 150)):
    if free(x, z, 3, True, True):
        goblin_hut(x, z)
for a in range(NX):
    for b in range(NZ):
        z = b + OZ
        if FLR[a, b] >= 0 and z >= 102 and OPEN[a, b] and hash01(a, 9, b, 3) < 0.02:
            t = int(CEIL[a, b])
            n = 1 + int(hash01(a, 10, b, 4) * 2)
            for i in range(n):
                area.set(a + OX, t - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" + ("tip" if i == n - 1 else "frustum") + "]")
SOUL2 = (-170, 133, 156)
SOUL3 = (-89, 129, 138)
for (sx, sy, sz) in (SOUL2, SOUL3):
    area.set(sx, sy - 1, sz, "minecraft:gold_block")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        area.set(sx + dx, sy, sz + dz, "minecraft:candle[candles=2,lit=true]" if (dx + dz) % 4 == 0 else "minecraft:raw_gold_block")
    area.set(sx, sy + 3, sz, "minecraft:soul_lantern[hanging=true]")
for (x, y) in ((-165, 133), (-165, 134)):
    area.set(x, y, 156, "minecraft:cobweb")
for y in (129, 130):
    area.set(-89, y, 144, "minecraft:cobweb")
sign(-100, 129, 146, 4, "Goblin Burrows", "Beware")
area.fairy_soul(*SOUL1)
area.fairy_soul(*SOUL2)
area.fairy_soul(*SOUL3)

# ---------------------------------------------------------------- lighting: fill every gap (mines bright, Burrows dim)
LIGHTS = {"minecraft:lantern": 15, "minecraft:glowstone": 15, "minecraft:shroomlight": 15, "minecraft:sea_lantern": 15,
          "minecraft:soul_lantern": 10, "minecraft:campfire": 15}
src = [(a + OX, b + OY, c + OZ, LIGHTS[e["id"]]) for (a, b, c), (e, _) in area.piece.cells.items() if e["id"] in LIGHTS]
S = np.array(src, dtype=np.int32).reshape(-1, 4)
added = {"post": 0, "hang": 0, "pedestal": 0}


def level_at(x, y, z):
    if len(S) == 0:
        return 0
    return int((S[:, 3] - (np.abs(S[:, 0] - x) + np.abs(S[:, 1] - y) + np.abs(S[:, 2] - z))).max())


def add_src(x, y, z, lv):
    global S
    S = np.vstack([S, np.array([[x, y, z, lv]], dtype=np.int32)])


def air_col(x, z, n):
    F = fl(x, z)
    return all(blk(x, F + i, z) == "minecraft:air" for i in range(1, n + 1))


order = [(a + OX, b + OZ) for a in range(NX) for b in range(NZ) if FLR[a, b] >= 0]
py.shuffle(order)
for (x, z) in order:
    F = fl(x, z)
    burrow = z >= 102
    need = 3 if burrow else 8
    if level_at(x, F + 1, z) >= need:
        continue
    done = False
    # 1. a post / pedestal on a nearby free floor column
    for r in (0, 1, 2, 3):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) != r or done:
                    continue
                px_, pz_ = x + dx, z + dz
                if not carved(px_, pz_) or not OPEN[ix(px_), iz(pz_)] or ROUTE[ix(px_), iz(pz_)] or RAMPC[ix(px_), iz(pz_)]                         or CORR[ix(px_), iz(pz_)] or not air_col(px_, pz_, 3):
                    continue
                if (px_, pz_) in ((-165, -146),):
                    continue
                if any(((px_ + a_, pz_ + b_) in ((SOUL1[0], SOUL1[2]), (SOUL2[0], SOUL2[2]), (SOUL3[0], SOUL3[2]))) for a_ in (-1, 0, 1) for b_ in (-1, 0, 1)):
                    continue
                Fp = fl(px_, pz_)
                if burrow:
                    area.set(px_, Fp + 1, pz_, "minecraft:gold_block")
                    area.set(px_, Fp + 2, pz_, "minecraft:soul_lantern[hanging=false]")
                    add_src(px_, Fp + 2, pz_, 10)
                    added["pedestal"] += 1
                else:
                    post(px_, Fp + 1, pz_, 2, "lantern[hanging=false]", py.choice(("spruce_fence", "oak_fence", "stone_brick_wall")))
                    add_src(px_, Fp + 3, pz_, 15)
                    added["post"] += 1
                OCC[ix(px_), iz(pz_)] = True
                done = True
    if done:
        continue
    # 2. a lantern hanging from the ceiling at the nearest column (edge columns first)
    best = None
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            px_, pz_ = x + dx, z + dz
            if not carved(px_, pz_) or px_ > -65 or blk(px_, ce(px_, pz_), pz_) != "minecraft:air":
                continue
            h = ce(px_, pz_) - fl(px_, pz_)
            if h > 12:
                continue
            edge = any(not carved(px_ + a_, pz_ + b_) for a_, b_ in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            score = abs(dx) + abs(dz) + (0 if edge else 3)
            if best is None or score < best[0]:
                best = (score, px_, pz_)
    if best:
        _, px_, pz_ = best
        t = ce(px_, pz_)
        area.set(px_, t, pz_, "minecraft:soul_lantern[hanging=true]" if burrow else "minecraft:lantern[hanging=true]")
        add_src(px_, t, pz_, 10 if burrow else 15)
        added["hang"] += 1
print("light fill", added)

# ====================================================================== zones and mobs
FULL_Y = (100, 235)


def vein_rule(regrow):
    return {"becomes": "minecraft:bedrock", "regrow": regrow}


MINING = {"minecraft:" + b: vein_rule(8) for b in ("gray_wool", "cyan_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine",
                                                       "light_blue_wool")}
MINING["minecraft:polished_diorite"] = vein_rule(12)
ITEMS = Path(__file__).resolve().parents[3] / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"


def drop(item, chance, mn=1, mx=1):
    return {"item": item, "chance": chance, "min": mn, "max": mx}


def mob(kind, name, level_, health, damage, xp, coins, mx, every, a_, b_, drops=None):
    m = {"type": kind, "name": name, "level": level_, "health": health, "damage": damage, "xp": xp, "coins": coins,
         "max": mx, "every": every, "from": list(a_), "to": list(b_), "indoors": True}
    if drops:
        m["drops"] = [d for d in drops if (ITEMS / (d["item"].lower() + ".json")).exists()]
    return m


def zbox(z1, z2):
    return ((OX, FULL_Y[0], z1), (OX + NX, FULL_Y[1], z2))


area.zone("Upper Mines", box=zbox(*ZUP), blocks=MINING,
          mobs=[mob("minecraft:husk", "Treasure Hoarder", 61, 15000, 500, 91, 20, 4, 8, (-150, 177, -140), (-100, 181, -110),
                    [drop("STARFALL", 1, 1, 2)])])
area.zone("Rampart's Quarry", box=zbox(*ZQ), blocks=MINING)
area.zone("Far Reserve", box=zbox(*ZFR), blocks=MINING)
area.zone("Goblin Burrows", box=zbox(*ZGB), blocks={"minecraft:gold_ore": vein_rule(4)},
          mobs=[mob("minecraft:zombie", "Goblin", 23, 800, 300, 34, 7, 6, 6, (-124, 129, 148), (-100, 133, 165), [drop("GOBLIN_EGG", 0.005)]),
                mob("minecraft:zombie", "Goblin", 23, 800, 300, 34, 7, 4, 7, (-124, 143, 110), (-112, 147, 122), [drop("GOBLIN_EGG", 0.005)]),
                mob("minecraft:zombie", "Knifethrower", 23, 800, 300, 34, 7, 4, 8, (-166, 137, 122), (-154, 141, 134), [drop("GOBLIN_EGG", 0.005)]),
                mob("minecraft:skeleton", "Fireslinger", 23, 800, 300, 34, 7, 3, 9, (-92, 139, 116), (-80, 143, 128))])
area.arrival(-90, 177, -110, yaw=90.0)

# ====================================================================== walkability check (BFS over the placed blocks)
cells = area.piece.cells


def passable(b):
    return b in ("minecraft:air", "minecraft:cobweb", "minecraft:cave_air") or b.endswith("rail")


def standable(b):
    return not passable(b) and not b.endswith(("fence", "wall", "chain", "iron_chain", "lantern", "water", "candle", "campfire",
                                                "pointed_dripstone", "bars", "sign")) and b != "minecraft:lava"


def bfs(start):
    seen = {start}
    todo = deque([start])
    while todo:
        x, y, z = todo.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx_, nz_ = x + dx, z + dz
            for dy in (1, 0, -1, -2, -3):
                ny = y + dy
                if (nx_, ny, nz_) in seen:
                    continue
                if not standable(blk(nx_, ny - 1, nz_)) or not passable(blk(nx_, ny, nz_)) or not passable(blk(nx_, ny + 1, nz_)):
                    continue
                if dy == 1 and not passable(blk(x, y + 2, z)):
                    continue
                seen.add((nx_, ny, nz_))
                todo.append((nx_, ny, nz_))
                break
    return seen


reach = bfs((-90, 177, -110))
columns = {(x, z) for (x, y, z) in reach}
targets = {"door2": (-62, 177, -110), "door4": (-62, 176, -45), "door5": (-62, 141, 60), "door6": (-62, 129, 150),
           "fraiser": (-82, 177, -103), "carlton": (-67, 176, -54), "soul1": SOUL1, "soul2": SOUL2, "soul3": SOUL3,
           "far reserve": (-118, 143, 60), "burrows A": (-118, 143, 116), "burrows D": (-112, 129, 156)}
for name, t in targets.items():
    print(f"  reach {name} {t}: {'yes' if t in reach else 'NO'}")
total_cols = int((FLR >= 0).sum())
print(f"reachable columns {len(columns)} of {total_cols} carved")
bad = [(x, z) for x in range(OX, OX + NX) for z in range(OZ, OZ + NZ) if carved(x, z) and (x, z) not in columns]
print("unreachable carved columns", len(bad), bad[:12])
sealed = 0
for (x, z) in bad:                      # tiny walled-off pockets with free floor: fill them with rock
    F = fl(x, z)
    if blk(x, F + 1, z) == "minecraft:air" and blk(x, F + 2, z) == "minecraft:air" and standable(blk(x, F, z)):
        for y in range(F + 1, F + 4):
            rock(x, y, z)
        sealed += 1
print("sealed pocket columns", sealed)
print("zones:")
for z_ in area.zones:
    print("  ", z_["name"], z_["from"], z_["to"], len(z_.get("blocks", {})), "block rules")
area.save()
