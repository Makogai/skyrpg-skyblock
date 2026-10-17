#!/usr/bin/env python3
"""Dwarven Mines, the centre box (x -60..60, z -75..175, y 95..235): Gates to the Mines, The Forge, Cliffside Veins,
Divan's Gateway, The Mist and the Great Ice Wall, carved out of solid rock.

    python tools/builds/hub_areas/mines_center.py

The whole box is rock; the spaces are carved out of it with numpy masks, dressed with features (a dict of blocks), checked
for walkability with a breadth-first search (every NPC, warp, fairy soul and doorway must be reachable from the arrival),
lit (ceiling lamps, wall-embedded lights, invisible light blocks outside the Mist), veined with Mithril, then only the
rock within 2 blocks of air is written (the kit hollows the rest).

Floors: the "floor y" of the brief is the y of the floor BLOCK; players stand at y + 1.
"""
import math
import sys
from collections import deque
from pathlib import Path

import numpy as np

from kit import Area, hash01, noise

OX, OY, OZ = -60, 95, -75
NX, NY, NZ = 120, 140, 250
X1, Y1, Z1 = OX + NX, OY + NY, OZ + NZ          # exclusive maxima: 60, 235, 175

area = Area("mines_center", (OX, OY, OZ), (NX, NY, NZ), version=1, shell=2,
            dimension="skyrpg_skyblock:dwarven_mines", island="Dwarven Mines")

rock = np.ones((NX, NY, NZ), dtype=bool)         # True = solid natural rock
F = {}                                           # (x, y, z) -> block: everything built (overrides the rock)


def sq(v):
    return math.sqrt(max(0.0, v))


def inb(x, y, z):
    return OX <= x < X1 and OY <= y < Y1 and OZ <= z < Z1


def carve(x1, y1, z1, x2, y2, z2):
    x1, x2 = max(min(x1, x2), OX), min(max(x1, x2), X1 - 1)
    y1, y2 = max(min(y1, y2), OY), min(max(y1, y2), Y1 - 1)
    z1, z2 = max(min(z1, z2), OZ), min(max(z1, z2), Z1 - 1)
    if x1 > x2 or y1 > y2 or z1 > z2:
        return
    rock[x1 - OX:x2 - OX + 1, y1 - OY:y2 - OY + 1, z1 - OZ:z2 - OZ + 1] = False


def put(x, y, z, block):
    if inb(x, y, z):
        F[(x, y, z)] = block if ":" in block.split("[")[0] else "minecraft:" + block


def fill(x1, y1, z1, x2, y2, z2, block):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                put(x, y, z, block)


def pick(x, y, z, table, seed=0):
    """table: [(block, weight), ...]"""
    total = sum(w for _, w in table)
    r = hash01(x, y, z, seed + 31) * total
    for block, w in table:
        r -= w
        if r <= 0:
            return block
    return table[-1][0]


STONEPAL = [("stone_bricks", 6), ("cracked_stone_bricks", 1), ("polished_andesite", 2), ("andesite", 1)]
DARKPAL = [("polished_blackstone_bricks", 6), ("blackstone", 2), ("cracked_polished_blackstone_bricks", 1),
           ("polished_deepslate", 1)]


def pave(x, y, z, table=STONEPAL):
    put(x, y, z, pick(x, y, z, table, 3))


def top_air(x, z):
    col = rock[x - OX, :, z - OZ]
    idx = np.nonzero(~col)[0]
    return int(idx.max()) + OY if len(idx) else None


FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


def stair(x, y, z, asc, block="stone_brick_stairs", half="bottom"):
    put(x, y, z, f"{block}[facing={FACE[asc]},half={half}]")


def run(sx, sz, d, top, steps, lat, base, cheek_lo=True, cheek_hi=True, block="stone_brick_stairs",
        solid="stone_bricks", wall="stone_brick_wall", head=8):
    """A stair run from (sx, sz) in direction d; step i's top is at top - i. lat = (a, b), the lateral extent
    (z if the run is along x, x if along z). Solid masonry fills below down to `base`; cheeks are raised side strips."""
    dx, dz = d
    asc = (-dx, -dz)
    la, lb = lat
    for i in range(steps):
        cx, cz = sx + dx * i, sz + dz * i
        y = top - i
        for l in range(la - 1, lb + 2):
            px, pz = (cx, l) if dx else (l, cz)
            if la <= l <= lb:
                carve(px, y + 1, pz, px, y + head, pz)
                for yy in range(base, y):
                    put(px, yy, pz, solid)
                stair(px, y, pz, asc, block)
            elif (l < la and cheek_lo) or (l > lb and cheek_hi):
                for yy in range(base, y + 1):
                    put(px, yy, pz, solid)
                put(px, y + 1, pz, wall)


def landing(x1, z1, x2, z2, top, base, table=STONEPAL, head=9):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            carve(x, top + 1, z, x, top + head, z)
            for yy in range(base, top):
                put(x, yy, z, "stone_bricks")
            pave(x, top, z, table)


def chain_lamp(x, z, drop, soul=False):
    ty = top_air(x, z)
    if ty is None:
        return
    for i in range(drop):
        put(x, ty - i, z, "iron_chain[axis=y]")
    put(x, ty - drop, z, f"{'soul_' if soul else ''}lantern[hanging=true]")


def column(x, z, y1, y2, block):
    for y in range(y1, y2 + 1):
        put(x, y, z, block)


# ============================================================ constants
FF = 175                         # Forge floor
GF = 182                         # Gates floor
PZ = -21                         # lava pool centre z (x = 0)
LAYERS = [(175, 34, 26), (158, 26, 21), (140, 18, 16), (124, 12, 10), (115, 8, 6)]   # Cliffside terraces (floor, hx, hz)
CCX, CCZ = 12, 48

# ============================================================ carving: Gates to the Mines
terrace = []
for x in range(-36, 37):
    for z in range(-73, -48):
        u = (abs(x) / 34) ** 4 + (abs(z + 61) / 11.5) ** 4 + 0.05 * noise(x, z, 6, 1.4)
        if u < 1:
            h = 14 + int(18 * sq(1 - u))
            carve(x, GF + 1, z, x, GF + h, z)
            terrace.append((x, z))
carve(-2, GF + 1, -75, 2, GF + 5, -72)                       # doorway 1 (floor 182, 5 wide, 5 tall)
carve(-21, GF + 1, -50, 21, GF + 28, -45)                    # the balcony over the Forge

# ============================================================ carving: The Forge
hall = []
for x in range(-43, 44):
    for z in range(-49, -5):
        u = (abs(x) / 42) ** 4 + (abs(z + 27) / 21) ** 4 + 0.05 * noise(x, z, 1, 1.4)
        if u < 1:
            h = 6 + int(34 * sq(1 - u))
            carve(x, FF + 1, z, x, FF + h, z)
            hall.append((x, z))
hallset = set(hall)
carve(-60, FF + 1, -47, -32, FF + 5, -43)                    # doorway 4 (floor 175)
carve(38, 198, -42, 59, 202, -38)                            # doorway 7 (floor 197)
carve(30, FF + 1, -46, 41, 208, -34)                         # the east gallery alcove
carve(6, FF + 1, -8, 18, FF + 8, 22)                         # corridor Forge -> Cliffside Veins
for x in range(-8, 9):                                       # the lava pool
    for z in range(PZ - 8, PZ + 9):
        if math.hypot(x, z - PZ) <= 7.2:
            carve(x, 171, z, x, FF, z)

# ============================================================ carving: Cliffside Veins
chasm = {}
for x in range(-24, 49):
    for z in range(20, 77):
        u0 = (abs(x - CCX) / 34) ** 6 + (abs(z - CCZ) / 26) ** 6 + 0.04 * noise(x, z, 3, 1.3)
        if u0 >= 1:
            continue
        hi = 200 + int(20 * sq(1 - u0))
        k = 0
        for kk in range(4, -1, -1):
            fl, hx, hz = LAYERS[kk]
            u = (abs(x - CCX) / hx) ** 6 + (abs(z - CCZ) / hz) ** 6 + 0.04 * noise(x, z, 3 + kk, 1.3)
            if u < 1:
                k = kk
                break
        carve(x, LAYERS[k][0] + 1, z, x, hi, z)
        chasm[(x, z)] = k
carve(-60, 141, 58, -4, 145, 62)                             # doorway 5 (floor 140) and its tunnel
carve(28, 141, 38, 59, 145, 42)                              # doorway 8 (floor 140)
carve(-4, 141, 58, 4, 147, 87)                               # tunnel to Divan's Gateway
carve(47, 191, 46, 58, 202, 70)                              # the Cult of the Fallen Star's cave
# fairy soul B niche (beyond the pit's south edge)
carve(10, 116, 54, 12, 119, 57)

# ============================================================ carving: Divan's Gateway and The Mist
divan = []
for x in range(-30, 31):
    for z in range(85, 138):
        u = (abs(x) / 28) ** 4 + (abs(z - 111) / 24) ** 4 + 0.05 * noise(x, z, 2, 1.4)
        if u < 1:
            carve(x, 101, z, x, 150 + int(28 * sq(1 - u)), z)
            divan.append((x, z))
divset = set(divan)
carve(-4, 129, 135, 4, 134, 139)                             # tunnel through the south wall

# ============================================================ carving: Great Ice Wall
for x in range(-53, 54):
    for z in range(138, 168):
        ux = (abs(x) / 52) ** 6 + 0.04 * noise(x, z, 8, 1.3)
        zmax = 165 + int(2.5 * (noise(x, 200, 9, 1.6) * 0.5 + 0.5))
        if ux < 1 and z <= zmax:
            t = min(1.0, max(0.0, (z - 139) / 18.0))
            h = int((12 + 70 * t) * sq(1 - ux))
            if h >= 3:
                carve(x, 129, z, x, 128 + h, z)
carve(-60, 129, 148, -49, 133, 152)                          # doorway 6 (floor 128)
carve(49, 129, 148, 59, 133, 152)                            # doorway 9 (floor 128)

# ============================================================ THE GATES
for (x, z) in terrace:
    if abs(x) <= 4 and z <= -50:
        pave(x, GF, z, DARKPAL)
    else:
        pave(x, GF, z)
for x in range(-2, 3):                                       # the doorway's three blocks
    for z in range(-75, -72):
        put(x, GF, z, "polished_blackstone_bricks")
for x in range(-21, 22):                                     # the balcony floor
    for z in range(-50, -44):
        carve(x, GF + 1, z, x, GF + 28, z)
        for yy in range(FF, GF):
            put(x, yy, z, "stone_bricks")
        pave(x, GF, z, DARKPAL if abs(x) <= 4 else STONEPAL)
# the Forge stair from the balcony: tops 181..175 along z -44..-38
run(-6, -44, (0, 1), 181, 7, (-6, 6), FF - 1)


def statue(x, y, z):
    fill(x - 1, y, z - 1, x + 1, y + 1, z + 1, "polished_blackstone_bricks")
    for lx in (-1, 1):
        column(x + lx, z, y + 2, y + 4, "stone_bricks")
    fill(x - 1, y + 5, z, x + 1, y + 8, z + 1, "stone_bricks")
    fill(x - 1, y + 5, z, x + 1, y + 5, z + 1, "gilded_blackstone")
    put(x, y + 7, z, "chiseled_stone_bricks")
    column(x - 2, z, y + 5, y + 8, "stone_bricks")
    column(x + 2, z, y + 5, y + 8, "stone_bricks")
    put(x, y + 9, z, "chiseled_stone_bricks")
    put(x, y + 10, z, "stone_bricks")
    put(x, y + 11, z, "polished_blackstone_slab[type=bottom]")
    put(x - 1, y + 10, z, "stone_brick_stairs[facing=east,half=bottom]")
    put(x + 1, y + 10, z, "stone_brick_stairs[facing=west,half=bottom]")
    put(x, y + 8, z - 1, "stone_brick_stairs[facing=south,half=top]")
    column(x + 3, z, y + 5, y + 11, "stone_brick_wall")                # the hammer
    fill(x + 2, y + 12, z, x + 4, y + 12, z, "polished_blackstone_bricks")
    put(x + 3, y + 13, z, "sea_lantern")
    put(x, y + 9, z - 1, "sea_lantern")


for sx in (-1, 1):
    for sz in (-66, -54):
        statue(sx * 23, GF + 1, sz)
    # the gate's pillars
    for x in range(14, 19):
        for z in range(-62, -57):
            xx = sx * x
            edge = x in (14, 18) and z in (-62, -58)
            for y in range(GF + 1, 212):
                b = "polished_blackstone_bricks" if edge else ("chiseled_stone_bricks" if (y - GF) % 7 == 0
                                                                else "stone_bricks")
                put(xx, y, z, b)
    for x in range(13, 20):                                  # capital
        for z in range(-63, -56):
            for y in range(204, 208):
                put(sx * x, y, z, "polished_blackstone_bricks" if y in (204, 207) else "stone_bricks")
    for y in range(GF + 6, 200, 6):                          # sea lantern bands
        for z in (-63, -57):
            for x in range(15, 18):
                put(sx * x, y, z, "sea_lantern")
for x in range(-14, 15):                                     # the lintel arch
    under = 200 + int(round(7 * sq(1 - (x / 13.5) ** 2)))
    for z in range(-62, -57):
        for y in range(under + 1, 212):
            put(x, y, z, "polished_blackstone_bricks" if y >= 210 else "stone_bricks")
    for z in (-63, -57):
        put(x, under, z, "chiseled_stone_bricks")
for x in range(-4, 5):
    for z in (-63, -57):
        put(x, 211, z, "gilded_blackstone")
for z in (-63, -57):
    put(0, 209, z, "sea_lantern")
    put(-1, 209, z, "sea_lantern")
    put(1, 209, z, "sea_lantern")
for x in (-9, -4, 4, 9):
    chain_lamp(x, -60, 4)
for x in (-26, -13, 0, 13, 26):
    for z in (-69, -53):
        chain_lamp(x, z, 5)
# fairy soul A: a niche in the west wall, behind a barrel stack
carve(-38, GF + 1, -58, -33, GF + 4, -56)
for z in (-58, -56):
    for y in (GF + 1, GF + 2):
        put(-34, y, z, "barrel[facing=up]")
put(-33, GF + 1, -58, "barrel[facing=up]")
put(-33, GF + 1, -56, "barrel[facing=up]")
for x in range(-38, -33):
    for z in range(-58, -55):
        put(x, GF, z, "stone_bricks")
put(-37, GF + 3, -57, "lantern[hanging=false]")
put(-37, GF + 1, -57, "barrel[facing=up]")

# ============================================================ THE FORGE
for (x, z) in hall:
    d = math.hypot(x, z - PZ)
    if d <= 7.2:
        continue
    if d <= 8.9:
        put(x, FF, z, "chiseled_polished_blackstone" if (abs(x) < 2 or abs(z - PZ) < 2) else "polished_blackstone_bricks")
        put(x, FF + 1, z, "polished_blackstone_brick_wall")
    elif (x // 3 + z // 3) % 2 == 0:
        pave(x, FF, z, DARKPAL)
    else:
        pave(x, FF, z)
for x in range(-8, 9):
    for z in range(PZ - 8, PZ + 9):
        if math.hypot(x, z - PZ) <= 7.2:
            for y in range(171, 175):
                put(x, y, z, "lava[level=0]")
            put(x, 175, z, "minecraft:air")
            put(x, 171, z, "magma_block") if hash01(x, 171, z, 4) < 0.2 else None
for y in range(FF + 1, 212):
    put(0, y, PZ, "lava[level=8]")
for y in range(212, 215):
    put(0, y, PZ, "lava[level=0]")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if dx or dz:
            for y in range(211, 215):
                put(dx, y, PZ + dz, "polished_blackstone_bricks" if y > 211 else "chiseled_polished_blackstone")
for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
    put(dx, 213, PZ + dz, "shroomlight")
put(0, 175, PZ, "lava[level=8]")
# pillars
for (px, pz) in ((-22, -39), (22, -39), (-22, -12), (22, -12), (-35, -27), (35, -27)):
    ty = top_air(px, pz)
    for x in range(px - 1, px + 2):
        for z in range(pz - 1, pz + 2):
            for y in range(FF, ty + 1):
                corner = abs(x - px) == 1 and abs(z - pz) == 1
                put(x, y, z, "polished_blackstone_bricks" if corner else
                    ("chiseled_polished_blackstone" if (y - FF) % 6 == 5 else "stone_bricks"))
    for y in (FF + 1, FF + 2, FF + 3):
        put(px, y, pz - 2, "wall_torch[facing=north]") if False else None
    put(px + 2, FF + 3, pz, "lantern[hanging=false]") if (px + 2, pz) in hallset else None
    for ddx, ddz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        if (px + ddx, pz + ddz) in hallset:
            put(px + ddx, FF + 1, pz + ddz, "polished_blackstone_brick_wall")
            put(px + ddx, FF + 2, pz + ddz, "lantern[hanging=false]")
# the smith's row along the south wall, anvils in front
zmax = {}
for (x, z) in hall:
    zmax[x] = max(zmax.get(x, -999), z)
for x in range(-34, 35):
    if 4 <= x <= 20 or x not in zmax or abs(x) > 32:
        continue
    zz = zmax[x]
    if (x % 2 == 0):
        put(x, FF + 1, zz, ("furnace" if (x // 2) % 2 else "blast_furnace") + "[facing=north,lit=true]")
        put(x, FF + 2, zz, "polished_blackstone_brick_wall")
    elif x % 4 == 1:
        put(x, FF + 1, zz, "smithing_table" if x % 8 == 1 else "grindstone[face=floor,facing=north]")
    if x % 6 == 0:
        put(x, FF + 1, zz - 2, "anvil[facing=east]")
for ax in (-26, -20, 26):                                    # anvils and a lava cauldron by the north-west
    put(ax, FF + 1, -36, "anvil[facing=north]")
put(-28, FF + 1, -38, "lava_cauldron")
put(28, FF + 1, -38, "lava_cauldron")
put(-30, FF + 1, -38, "blast_furnace[facing=south,lit=true]")
put(30, FF + 1, -38, "blast_furnace[facing=south,lit=true]")
# chandeliers and chains
for (cx, cz, dr) in ((-14, -40, 9), (14, -40, 9), (-30, -20, 7), (30, -20, 7), (-12, -12, 10), (12, -30, 10),
                     (0, -42, 8), (-38, -38, 5), (38, -14, 5), (-16, -27, 11), (16, -14, 11), (-26, -32, 8)):
    chain_lamp(cx, cz, dr)
# the east gallery: balcony, stair
for x in range(30, 42):
    for z in range(-46, -33):
        for y in range(FF, 197):
            put(x, y, z, "stone_bricks")
        pave(x, 197, z, DARKPAL)
for x in range(38, 60):                                      # doorway 7 floor
    for z in range(-42, -37):
        put(x, 197, z, "polished_blackstone_bricks" if x >= 56 else "stone_bricks")
run(36, -33, (0, 1), 196, 22, (34, 38), FF - 1)
for z in (-45, -41, -37):
    put(31, 198, z, "lantern[hanging=false]")
# doorway 4 corridor floor and lanterns
for x in range(-60, -31):
    for z in range(-47, -42):
        put(x, FF, z, "polished_blackstone_bricks" if x <= -57 else "stone_bricks")
for x in (-56, -50, -44):
    put(x, FF + 4, -47, "lantern[hanging=false]") if False else None
    put(x, FF + 5, -45, "lantern[hanging=true]")

# ============================================================ CLIFFSIDE VEINS
for x in range(6, 19):                                       # corridor floor
    for z in range(-8, 23):
        put(x, FF, z, "stone_bricks" if (x in (6, 18) or z % 8 == 0) else "polished_andesite")
for z in (-4, 6, 16):
    for x in (6, 18):
        put(x, FF + 1, z, "polished_blackstone_brick_wall")
        put(x, FF + 2, z, "lantern[hanging=false]")
# stairs and landings between the terraces
landing(33, 28, 37, 31, 175, 158)
run(32, 28, (-1, 0), 174, 17, (28, 31), 157, cheek_lo=False)
landing(-5, 32, -2, 35, 158, 140)
run(-5, 36, (0, 1), 157, 18, (-5, -3), 139, cheek_lo=False)
landing(21, 39, 24, 42, 140, 124)
run(20, 39, (-1, 0), 139, 16, (39, 41), 123, cheek_lo=False)
landing(5, 43, 8, 46, 124, 115)
run(9, 43, (1, 0), 123, 9, (43, 45), 114, cheek_lo=False)
# the stair up to the Cult's cave
run(43, 48, (0, -1), 189, 14, (42, 44), 175)
landing(42, 49, 46, 53, 190, 175)
for x in range(47, 59):                                      # the cave floor
    for z in range(46, 71):
        put(x, 190, z, pick(x, 190, z, DARKPAL, 5))
for z in range(46, 71):
    pass
for dx in range(-2, 3):                                      # the fallen star's crater
    for dz in range(-2, 3):
        if abs(dx) + abs(dz) <= 3:
            put(52 + dx, 190, 58 + dz, "crying_obsidian" if abs(dx) + abs(dz) >= 2 else "magma_block")
put(52, 191, 58, "shroomlight")
put(52, 192, 58, "amethyst_cluster[facing=up]")
for (cx, cz) in ((48, 47), (57, 47), (48, 69), (57, 69), (48, 58), (57, 58)):
    column(cx, cz, 191, 194, "polished_blackstone_brick_wall")
    put(cx, 195, cz, "soul_lantern[hanging=false]")
for (cx, cz) in ((50, 50), (55, 52), (50, 66), (55, 64)):
    put(cx, 191, cz, "amethyst_cluster[facing=up]")
for z in range(47, 70, 4):
    put(58, 191, z, "chiseled_polished_blackstone")
    put(58, 192, z, "soul_lantern[hanging=false]")
# the pit: rocky bottom
for x in range(5, 20):
    for z in range(43, 54):
        if hash01(x, 0, z, 12) < 0.04:
            put(x, 116, z, "tuff")
# chains of lanterns above the chasm, at several heights
for (cx, cz, dr) in ((12, 30, 14), (0, 48, 16), (24, 48, 18), (12, 66, 14), (-10, 40, 8), (34, 60, 8), (12, 48, 24),
                     (36, 34, 6), (-8, 66, 8)):
    chain_lamp(cx, cz, dr)

# ============================================================ DIVAN'S GATEWAY and THE MIST
SOLID = "stone_bricks"
# the tunnel floor and the north stair
for x in range(-4, 5):
    for z in range(58, 88):
        put(x, 140, z, "polished_blackstone_bricks" if (abs(x) == 4 or z % 6 == 0) else "stone_bricks")
run(0, 88, (0, 1), 139, 5, (-4, 4), 101)
# plazas
for (x, z) in divan:
    if (93 <= z <= 97) or (119 <= z <= 123):
        if abs(x) <= 27:
            for y in range(101, 135):
                put(x, y, z, SOLID)
            put(x, 135, z, pick(x, 135, z, DARKPAL if abs(x) < 6 else STONEPAL, 7))
# the bridge
for z in range(98, 119):
    for x in range(-4, 5):
        put(x, 135, z, "polished_blackstone_bricks" if (abs(x) <= 1) else pick(x, 135, z, STONEPAL, 9))
        put(x, 134, z, SOLID)
        put(x, 133, z, "stone_brick_slab[type=top]" if abs(x) == 4 else SOLID)
for pz in (101, 115):                                        # piers
    for x in range(-2, 3):
        for z in range(pz - 1, pz + 2):
            for y in range(101, 133):
                put(x, y, z, SOLID)
# the south stair and floor
run(0, 124, (0, 1), 134, 7, (-4, 4), 101)
for x in range(-4, 5):
    for z in range(131, 140):
        for y in range(101, 128):
            put(x, y, z, SOLID) if (x, z) in divset else None
        put(x, 128, z, "polished_blackstone_bricks" if abs(x) == 4 or z % 5 == 0 else "stone_bricks")
for z in range(131, 140):
    for sx in (-5, 5):
        if (sx, z) in divset:
            for y in range(101, 129):
                put(sx, y, z, SOLID)
        put(sx, 129, z, "stone_brick_wall") if (sx, z) in divset else None
# the Mist stairs on the west wall (B first, A overrides the shared cheek)
run(-22, 115, (0, -1), 116, 17, (-22, -19), 101, cheek_lo=True, cheek_hi=True)
landing(-27, 116, -19, 119, 117, 101)
run(-27, 98, (0, 1), 134, 18, (-27, -24), 101, cheek_lo=False, cheek_hi=True)
# the Gateway: two towers, an arch, a crown
for sx in (-1, 1):
    for x in range(6, 11):
        for z in range(104, 113):
            for y in range(101, 160):
                edge = x in (6, 10) or z in (104, 112)
                b = "polished_blackstone_bricks" if (edge and (x in (6, 10) and z in (104, 112))) else \
                    ("chiseled_stone_bricks" if (y % 9 == 0 and edge) else "stone_bricks")
                put(sx * x, y, z, b)
    for x in range(5, 12):                                    # tower heads
        for z in range(103, 114):
            for y in range(160, 164):
                put(sx * x, y, z, "polished_blackstone_bricks" if y in (160, 163) else "stone_bricks")
    for x in (5, 8, 11):
        for z in (103, 106, 109, 113):
            put(sx * x, 164, z, "stone_brick_wall")
    for y in (138, 142, 146):                                 # glowing slits
        for z in (106, 108, 110):
            put(sx * 5 if False else sx * 6, y, z, "sea_lantern") if False else None
    for y in range(140, 158, 5):
        for z in (106, 110):
            put(sx * 5, y, z, "sea_lantern") if False else None
    for z in (106, 108, 110):
        for y in (139, 140, 143, 144, 147, 148, 151, 152):
            put(sx * 6, y, z, "sea_lantern")
for x in range(-5, 6):
    under = 146 + int(round(4 * sq(1 - (x / 5.5) ** 2)))
    for z in range(104, 113):
        for y in range(under + 1, 158):
            put(x, y, z, "polished_blackstone_bricks" if y >= 156 else "stone_bricks")
    for z in (104, 112):
        put(x, under, z, "chiseled_stone_bricks")
for x in range(-3, 4):
    for z in (104, 112):
        put(x, 157, z, "gilded_blackstone")
        put(x, 156, z, "sea_lantern") if abs(x) <= 1 else None
for z in range(100, 118, 3):                                 # lanterns on the bridge parapets
    for sx in (-1, 1):
        put(sx * 4, 137, z, "lantern[hanging=false]")
for (cx, cz, dr) in ((-14, 100, 10), (14, 100, 10), (-14, 116, 10), (14, 116, 10), (0, 96, 12), (0, 122, 12),
                     (-20, 108, 12), (20, 108, 12), (0, 108, 10)):
    chain_lamp(cx, cz, dr)
# Mithril outcrops flanking the bridge
OUTCROPS = ((7, 100), (-7, 101), (8, 116), (-8, 117), (11, 99), (-11, 99), (12, 118), (-12, 117), (14, 102), (-14, 114))
for (ox_, oz_) in OUTCROPS:
    height = 136 + int(hash01(ox_, 0, oz_, 3) * 3)
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if abs(dx) + abs(dz) == 2 and hash01(ox_ + dx, 1, oz_ + dz, 5) < 0.5:
                continue
            top_ = height - (1 if abs(dx) + abs(dz) else 0)
            for y in range(101, top_ + 1):
                put(ox_ + dx, y, oz_ + dz, "OUT")                    # resolved after the Mithril table below
# Mist: lanterns and fog
for (lx, lz) in ((-14, 92), (14, 92), (-16, 126), (16, 126), (0, 99), (0, 126), (-10, 104), (10, 120), (20, 108), (-20, 108)):
    if (lx, lz) in divset:
        put(lx, 101, lz, "soul_lantern[hanging=false]")
for y in (130, 124, 118, 112, 106):
    put(-23, y + 2, 100 + (134 - y)) if False else None
for i in range(18):                                          # soul lanterns along the stair cheeks
    put(-23, 134 - i + 2, 98 + i, "soul_lantern[hanging=false]") if i % 4 == 0 else None
    put(-18, 116 - i + 2, 115 - i, "soul_lantern[hanging=false]") if (i % 4 == 0 and i < 17) else None
fog_n = 0
for n in range(340):
    cx_ = -26 + int(hash01(n, 1, 0, 51) * 53)
    cz_ = 90 + int(hash01(n, 2, 0, 52) * 42)
    cy_ = 105 + int(hash01(n, 3, 0, 53) * 19)
    for m in range(5 + int(hash01(n, 4, 0, 54) * 8)):
        fx = cx_ + int(hash01(n, m, 5, 55) * 5) - 2
        fz = cz_ + int(hash01(n, m, 6, 56) * 5) - 2
        fy = cy_ + int(hash01(n, m, 7, 57) * 3) - 1
        if (fx, fz) not in divset or rock[fx - OX, fy - OY, fz - OZ] or (fx, fy, fz) in F:
            continue
        if fx <= -16 and 94 <= fz <= 122:
            continue
        if abs(fx) <= 5 and (fz in range(98, 119)) and fy > 120:
            continue
        put(fx, fy, fz, "white_stained_glass")
        fog_n += 1

# ============================================================ GREAT ICE WALL
for x in range(-52, 53):
    for z in range(139, 168):
        if not rock[x - OX, 129 - OY, z - OZ] and True:
            if 128 >= OY:
                pave(x, 128, z, [("snow_block", 4), ("packed_ice", 3), ("stone_bricks", 1)])
for x in list(range(-60, -48)) + list(range(49, 60)):
    for z in range(148, 153):
        put(x, 128, z, "polished_blackstone_bricks" if abs(x) >= 56 else "stone_bricks")
for x in range(-4, 5):
    for z in range(135, 140):
        put(x, 128, z, "polished_blackstone_bricks" if abs(x) == 4 or z % 5 == 0 else "stone_bricks")
# ice spires on the floor
for n in range(46):
    sx_ = -48 + int(hash01(n, 1, 1, 61) * 97)
    sz_ = 141 + int(hash01(n, 2, 1, 62) * 24)
    if abs(sx_) <= 6 and sz_ <= 146:
        continue
    if (abs(sx_) >= 49 and 146 <= sz_ <= 154):
        continue
    hgt = 3 + int(hash01(n, 3, 1, 63) * 11)
    for dx in range(0, 2):
        for dz in range(0, 2):
            hh = hgt - (dx + dz) * 2
            for y in range(129, 129 + max(0, hh)):
                if not rock[sx_ + dx - OX, y - OY, sz_ + dz - OZ]:
                    continue
                put(sx_ + dx, y, sz_ + dz, "blue_ice" if hash01(sx_ + dx, y, sz_ + dz, 64) < 0.35 else "packed_ice")
            put(sx_ + dx, 129, sz_ + dz, "blue_ice") if hh > 0 else None
# the fairy soul C cluster of spires in the south-west corner
for (px_, pz_, hh) in ((-47, 161, 7), (-46, 161, 6), (-45, 161, 8), (-47, 165, 6), (-45, 165, 7), (-44, 165, 5),
                       (-49, 160, 6), (-44, 162, 9), (-44, 163, 7), (-44, 164, 6)):
    column(px_, pz_, 129, 128 + hh, "packed_ice")
# Pure Emerald in the wall base
EMERALDS = []
for ex in (-40, -31, -20, -9, 9, 20, 31, 40):
    for (dx, dy, dz) in ((0, 0, 0), (1, 0, 0), (0, 1, 0), (1, 1, 0), (0, 0, 1)):
        EMERALDS.append((ex + dx, 129 + dy, 166 + dz))

# ============================================================ rails (before the walk check)


def build_arrays():
    S = rock.copy()                       # solid
    FLR = rock.copy()                     # can stand on
    for (x, y, z), b in F.items():
        i, j, k = x - OX, y - OY, z - OZ
        name = b.split(":")[1].split("[")[0]
        s = _solid(name)
        S[i, j, k] = s
        FLR[i, j, k] = s and not _nofloor(name)
    return S, FLR


_NONSOLID_EXACT = {"air", "light", "lantern", "soul_lantern", "iron_chain", "snow", "torch", "soul_torch", "wall_torch",
                   "vine", "cobweb", "lever", "amethyst_cluster", "OUT", "lava"}
_NONSOLID_END = ("_carpet", "_banner", "_sign", "_wall_sign", "_button", "_pressure_plate", "_head", "_skull", "candle", "rail")


def _solid(name):
    if name == "lava":
        return True
    if name in _NONSOLID_EXACT:
        return False
    return not name.endswith(_NONSOLID_END)


def _nofloor(name):
    return name.endswith("_wall") or name.endswith("fence") or name in ("lava", "campfire", "cauldron", "lava_cauldron",
                                                                        "barrel", "amethyst_cluster")


def solid_at(S, x, y, z):
    return True if not inb(x, y, z) else bool(S[x - OX, y - OY, z - OZ])


def rails(levels, x1, z1, x2, z2):
    S, FLR = build_arrays()
    add = []
    for y in levels:
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if not (solid_at(S, x, y, z) and FLR[x - OX, y - OY, z - OZ] and not solid_at(S, x, y + 1, z)
                        and not solid_at(S, x, y + 2, z) and (x, y + 1, z) not in F):
                    continue
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, nz = x + dx, z + dz
                    if (not solid_at(S, nx, y + 1, nz) and not solid_at(S, nx, y, nz) and not solid_at(S, nx, y - 1, nz)
                            and inb(nx, y, nz)):
                        add.append((x, y + 1, z))
                        break
    for (x, y, z) in add:
        put(x, y, z, "stone_brick_wall")
    return len(add)


n_rails = 0
for lv in (175, 158, 140, 124):
    n_rails += rails([lv], -24, 20, 48, 77)
n_rails += rails([182], -23, -52, 23, -43)
n_rails += rails([197], 28, -48, 43, -32)
n_rails += rails([135], -30, 90, 30, 126)
n_rails += rails([190], 40, 44, 58, 72)
n_rails += rails([117], -28, 114, -17, 121)

# connect walls
_DIRS = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}


def connect_walls():
    for (x, y, z), b in list(F.items()):
        name = b.split(":")[1]
        if not name.endswith("_wall") or "[" in name:
            continue
        sides = {}
        for face, (dx, dz) in _DIRS.items():
            nb = F.get((x + dx, y, z + dz), "")
            nbn = nb.split(":")[-1].split("[")[0]
            sides[face] = "low" if (nbn.endswith("_wall") or (nb and nbn in ("stone_bricks", "polished_blackstone_bricks")
                                                                 and False)) else "none"
        straight = (sides["east"] == sides["west"] == "low" and sides["north"] == sides["south"] == "none") or \
                   (sides["north"] == sides["south"] == "low" and sides["east"] == sides["west"] == "none")
        up = "false" if straight else "true"
        F[(x, y, z)] = (f"{b}[east={sides['east']},north={sides['north']},south={sides['south']},"
                        f"up={up},west={sides['west']}]")


connect_walls()

# ============================================================ walkability check
S, FLR = build_arrays()
Sb = S.reshape(-1).tobytes()
Fb = FLR.reshape(-1).tobytes()
NYZ = NY * NZ


def ix_(x, y, z):
    return ((x - OX) * NY + (y - OY)) * NZ + (z - OZ)


def valid(x, y, z):
    if not (OX <= x < X1 and OY + 1 <= y < Y1 - 2 and OZ <= z < Z1):
        return False
    i = ix_(x, y, z)
    return (not Sb[i]) and (not Sb[i + NZ]) and bool(Fb[i - NZ])


def reachable(start):
    seen = {start}
    q = deque([start])
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (OX <= nx < X1 and OZ <= nz < Z1):
                continue
            for dy in (0, 1, -1, -2, -3):
                ny = y + dy
                if (nx, ny, nz) in seen or not valid(nx, ny, nz):
                    continue
                if dy == 1 and Sb[ix_(x, y + 2, z)]:
                    continue
                if dy < 0 and (Sb[ix_(nx, y, nz)] or Sb[ix_(nx, y + 1, nz)]):
                    continue
                if dy < -1 and any(Sb[ix_(nx, y + h, nz)] for h in range(dy, 2)):
                    continue
                seen.add((nx, ny, nz))
                q.append((nx, ny, nz))
                break
    return seen


ARRIVAL = (0, GF + 1, -66)
NPCS = {
    "Forger": (0, FF + 1, -33, 180.0, "forge: the Forger, start a forge action"),
    "Fred": (-10, FF + 1, -33, 180.0, "shop: Fred, forge supplies"),
    "Jotraeline Greatforge": (10, FF + 1, -33, 180.0, "quest: Jotraeline Greatforge, the forge's master smith"),
    "Emissary Ceanna": (20, 176, 25, 180.0, "Emissary: commissions"),
    "Brarnas": (52, 191, 52, 90.0, "Cult of the Fallen Star: Brarnas"),
    "Dalir": (55, 191, 62, 90.0, "Cult of the Fallen Star: Dalir"),
    "Thondin": (49, 191, 66, 90.0, "Cult of the Fallen Star: Thondin"),
    "Silnar": (55, 191, 66, 90.0, "Cult of the Fallen Star: Silnar"),
    "Bylma": (-9, 136, 95, 180.0, "quest: Bylma at Divan's Gateway"),
}
FAIRY = [(-36, GF + 1, -57), (11, 116, 56), (-46, 129, 163)]
WARPS = {"The Forge": (0, FF + 1, -36, 0.0)}
DOORS = {1: (0, 183, -75), 4: (-60, 176, -45), 5: (-60, 141, 60), 6: (-60, 129, 150), 7: (59, 198, -40),
         8: (59, 141, 40), 9: (59, 129, 150)}

REACH = reachable(ARRIVAL)
problems = []
for name, (x, y, z, *_r) in NPCS.items():
    if (x, y, z) not in REACH:
        problems.append(f"NPC {name} {(x, y, z)}")
for p in FAIRY:
    if tuple(p) not in REACH:
        problems.append(f"fairy soul {p}")
for k, p in DOORS.items():
    if p not in REACH:
        problems.append(f"doorway {k} {p}")
for name, (x, y, z, _yaw) in WARPS.items():
    if (x, y, z) not in REACH:
        problems.append(f"warp {name}")
print("reachable standing spots:", len(REACH))
if problems:
    print("NOT REACHABLE:", problems)

# ============================================================ lights
lights = []                                                  # (x, y, z) of light sources


def in_mist(x, y, z):
    return 78 <= z < 136 and y < 126 and abs(x) <= 31


GLOW = [("glowstone", 3), ("shroomlight", 3), ("sea_lantern", 2)]
for x in range(-57, 58, 6):
    for z in range(-72, 172, 6):
        jx = x + int(hash01(x, 0, z, 71) * 3) - 1
        jz = z + int(hash01(x, 1, z, 72) * 3) - 1
        if not inb(jx, OY, jz):
            continue
        ty = top_air(jx, jz)
        if ty is None or ty < 140 or ty > 232:
            continue
        if in_mist(jx, ty, jz) and ty < 126:
            continue
        if not rock[jx - OX, ty + 1 - OY, jz - OZ]:
            continue
        # only caves with a floor: find the standing floor below
        put(jx, ty + 1, jz, pick(jx, ty, jz, GLOW, 4))
        lights.append((jx, ty + 1, jz))
for (x, y, z), b in F.items():
    name = b.split(":")[1].split("[")[0]
    if name in ("lantern", "glowstone", "sea_lantern", "shroomlight", "magma_block") or (name == "lava") or \
            name == "campfire" or name == "furnace" or name == "blast_furnace":
        if not in_mist(x, y, z):
            lights.append((x, y, z))
    elif name == "soul_lantern":
        pass
buckets = {}


def bkey(x, y, z):
    return (x // 6, y // 5, z // 6)


for p in lights:
    buckets.setdefault(bkey(*p), []).append(p)


def lit(x, y, z, r=8):
    kx, ky, kz = bkey(x, y, z)
    for a in range(-2, 3):
        for b in range(-2, 3):
            for c in range(-2, 3):
                for (lx, ly, lz) in buckets.get((kx + a, ky + b, kz + c), ()):
                    if abs(lx - x) + abs(ly - y) + abs(lz - z) <= r:
                        return True
    return False


n_embed = n_invis = 0
for (x, y, z) in sorted(REACH, key=lambda p: (p[1], p[0], p[2])):
    if in_mist(x, y, z):
        continue
    if lit(x, y + 1, z):
        continue
    placed = None
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        wx, wz = x + dx, z + dz
        if inb(wx, y + 1, wz) and rock[wx - OX, y + 1 - OY, wz - OZ] and (wx, y + 1, wz) not in F and \
                not (-60 < x < 60 and False):
            placed = (wx, y + 1, wz)
            break
    if placed and abs(placed[0]) < 59:
        ice = placed[2] >= 136
        put(*placed, "sea_lantern" if ice else pick(*placed, GLOW, 8))
        lights.append(placed)
        buckets.setdefault(bkey(*placed), []).append(placed)
        n_embed += 1
    elif (x, y + 2, z) not in F and not Sb[ix_(x, y + 2, z)]:
        put(x, y + 2, z, "light[level=14]")
        lights.append((x, y + 2, z))
        buckets.setdefault(bkey(x, y + 2, z), []).append((x, y + 2, z))
        n_invis += 1
print(f"lights: {len(lights)} sources, {n_embed} wall-embedded, {n_invis} invisible")

# ============================================================ emit the rock
air = ~rock
for _ in range(2):
    p = np.pad(air, 1, constant_values=True)
    air = air | p[:-2, 1:-1, 1:-1] | p[2:, 1:-1, 1:-1] | p[1:-1, :-2, 1:-1] | p[1:-1, 2:, 1:-1] | p[1:-1, 1:-1, :-2] | p[1:-1, 1:-1, 2:]
emit = rock & air


def rock_block(x, y, z):
    if 78 <= z < 136 and y < 126 and abs(x) <= 31 and (x, z) in divset or (78 <= z < 136 and y < 126 and y <= 126 and abs(x) < 40
                                                                          and (x, z) in divset):
        return pick(x, y, z, [("packed_ice", 5), ("snow_block", 4), ("white_wool", 3), ("calcite", 2), ("stone", 2)], 11)
    if z >= 136:
        if z >= 166 and y <= 226:
            return pick(x, y, z, [("packed_ice", 6), ("blue_ice", 4), ("snow_block", 1)], 12)
        n = noise(x * 1.3, z + y * 0.6, 14, 1.0)
        if n > 0.1:
            return pick(x, y, z, [("packed_ice", 4), ("snow_block", 3), ("blue_ice", 1)], 13)
        return pick(x, y, z, [("stone", 4), ("tuff", 3), ("andesite", 2), ("deepslate", 1)], 14)
    r = hash01(x, y, z, 5)
    n = noise(x + y * 0.5, z - y * 0.4, 7, 1.1)
    if n > 0.3:
        t = [("andesite", 5), ("tuff", 3), ("stone", 2)]
    elif n < -0.3 or y < 118:
        t = [("deepslate", 5), ("cobbled_deepslate", 4), ("tuff", 1)]
    else:
        t = [("stone", 5), ("andesite", 2), ("cobbled_deepslate", 2), ("tuff", 1), ("deepslate", 1)]
    return pick(x, y, z, t, 15)


R = {}
for (i, j, k) in np.argwhere(emit):
    x, y, z = int(i) + OX, int(j) + OY, int(k) + OZ
    if (x, y, z) not in F:
        R[(x, y, z)] = rock_block(x, y, z)

# ============================================================ Mithril veins
MITH1 = ("gray_wool", "cyan_terracotta")
MITH2 = ("prismarine", "prismarine_bricks", "dark_prismarine")
MITH3 = "light_blue_wool"
TITAN = "polished_diorite"


def mith(x, y, z):
    if hash01(x, y, z, 91) < 0.02:
        return TITAN
    r = hash01(x, y, z, 92)
    t3, t2 = (0.15, 0.30) if y < 135 else (0.04, 0.25)
    if r < t3:
        return MITH3
    if r < t3 + t2:
        return MITH2[int(hash01(x, y, z, 93) * 3) % 3]
    return MITH1[int(hash01(x, y, z, 94) * 2) % 2]


def in_mining(x, y, z):
    return (-4 <= z < 78) or (78 <= z < 136 and y >= 126)


# resolve the outcrops
for key, b in list(F.items()):
    if b == "minecraft:OUT":
        x, y, z = key
        F[key] = mith(x, y, z) if y >= 126 else rock_block(x, y, z)
for key, b in list(F.items()):
    pass

reach_mask = np.zeros((NX, NY, NZ), dtype=bool)
for (x, y, z) in REACH:
    reach_mask[x - OX, y - OY, z - OZ] = True
    reach_mask[x - OX, y + 1 - OY, z - OZ] = True
for _ in range(4):
    r_ = reach_mask.copy()
    r_[1:] |= reach_mask[:-1]
    r_[:-1] |= reach_mask[1:]
    r_[:, 1:] |= reach_mask[:, :-1]
    r_[:, :-1] |= reach_mask[:, 1:]
    r_[:, :, 1:] |= reach_mask[:, :, :-1]
    r_[:, :, :-1] |= reach_mask[:, :, 1:]
    reach_mask = r_
surface = rock & ~(np.pad(rock, 1, constant_values=True)[:-2, 1:-1, 1:-1]
                   & np.pad(rock, 1, constant_values=True)[2:, 1:-1, 1:-1]
                   & np.pad(rock, 1, constant_values=True)[1:-1, :-2, 1:-1]
                   & np.pad(rock, 1, constant_values=True)[1:-1, 2:, 1:-1]
                   & np.pad(rock, 1, constant_values=True)[1:-1, 1:-1, :-2]
                   & np.pad(rock, 1, constant_values=True)[1:-1, 1:-1, 2:])
cand = reach_mask & surface
mith_count = 0
protect = lambda x, y, z: (abs(x) >= 56 and 56 <= abs(x)) or (y == 140 and (x in range(-60, -54) or x >= 56))
for (i, j, k) in np.argwhere(cand):
    x, y, z = int(i) + OX, int(j) + OY, int(k) + OZ
    if not in_mining(x, y, z) or (x, y, z) not in R or (x, y, z) in F:
        continue
    if hash01(x, y, z, 77) >= 0.022:
        continue
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                p = (x + dx, y + dy, z + dz)
                if p in R and p not in F and in_mining(*p) and hash01(*p, 78) < 0.62 and \
                        not (abs(p[0]) >= 56):
                    R[p] = mith(*p)
                    mith_count += 1
# ores sprinkled on the pit floor (the deepest spots)
for x in range(6, 19):
    for z in range(44, 53):
        if hash01(x, 2, z, 79) < 0.28:
            put(x, 115, z, mith(x, 100, z))
            mith_count += 1
for (x, y, z) in EMERALDS:
    if (x, y, z) in R and (x, y, z - 1 if False else z) in R:
        R[(x, y, z)] = "emerald_block"
em_count = sum(1 for b in R.values() if b == "emerald_block")

# ============================================================ write it out
for (x, y, z), b in R.items():
    if (x, y, z) not in F:
        area.set(x, y, z, b)
for (x, y, z), b in F.items():
    area.set(x, y, z, b)

# fairy souls, NPCs, warps, arrival
for p in FAIRY:
    area.fairy_soul(*p)
for name, (x, y, z, yaw, note) in NPCS.items():
    area.npc(name, x, y, z, yaw, note)
for name, (x, y, z, yaw) in WARPS.items():
    area.warp(name, x, y, z, yaw)
area.arrival(*ARRIVAL, 0.0)

# ============================================================ zones
ITEMS = Path(__file__).resolve().parents[3] / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"


def mob(kind, name, level_, health, damage, xp, coins, mx, every, a_, b_, drops=None, indoors=False):
    m = {"type": kind, "name": name, "level": level_, "health": health, "damage": damage, "xp": xp, "coins": coins,
         "max": mx, "every": every, "from": list(a_), "to": list(b_)}
    if drops:
        m["drops"] = drops
    if indoors:
        m["indoors"] = True
    return m


def drop(item, chance, mn=1, mx=1):
    return {"item": item, "chance": chance, "min": mn, "max": mx}


assert (ITEMS / "glacite_jewel.json").exists()
MITHRIL = {f"minecraft:{b}": {"becomes": "minecraft:bedrock", "regrow": 8}
           for b in ("gray_wool", "cyan_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine", "light_blue_wool")}
MITHRIL["minecraft:polished_diorite"] = {"becomes": "minecraft:bedrock", "regrow": 12}
PURE = {"minecraft:emerald_block": {"becomes": "minecraft:bedrock", "regrow": 30}}

area.zone("Gates to the Mines", box=((-60, 95, -75), (60, 235, -50)), blocks={})
area.zone("The Forge", box=((-60, 95, -50), (60, 235, -4)), blocks={})
area.zone("Cliffside Veins", box=((-60, 95, -4), (60, 235, 78)), blocks=MITHRIL)
area.zone("Divan's Gateway", box=((-60, 126, 78), (60, 235, 136)), blocks=MITHRIL)
area.zone("The Mist", box=((-60, 95, 78), (60, 126, 136)), blocks={}, mobs=[
    mob("minecraft:creeper", "Ghost", 157, 1000000, 1000, 100, 52, 8, 6, (-26, 100, 90), (26, 112, 132), indoors=True)])
area.zone("Great Ice Wall", box=((-60, 95, 136), (60, 235, 175)), blocks=PURE, mobs=[
    mob("minecraft:stray", "Glacite Walker", 24, 888, 300, 36, 8, 16, 11, (-46, 127, 141), (46, 138, 164),
        [drop("GLACITE_JEWEL", 0.005)], indoors=True)])

area.save()
print(f"mithril blocks in veins: {mith_count}, emerald blocks: {em_count}, fog glass: {fog_n}, rails: {n_rails}")
