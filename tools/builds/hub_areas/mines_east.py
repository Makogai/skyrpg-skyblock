#!/usr/bin/env python3
"""Dwarven Mines, east part (Hypixel's Lava Springs, Palace Bridge, Royal Mines, Royal Palace, Aristocrat Passage).

    python tools/builds/hub_areas/mines_east.py

Box x 60..220, z -75..175, y 100..235 (dimension skyrpg_skyblock:dwarven_mines). A solid block of rock with the spaces carved out
(numpy), a rock ceiling, everything lit. Layout (standing y in brackets):

  Lava Springs (197): a domed cavern of lava falls behind bars, lava channels with slab bridges, basalt, Mithril outcrops.
  Palace Bridge (197 -> 187): a long grand bridge over a chasm, with arches, statues, lantern posts, from a gatehouse in the
      cavern's south wall to the palace forecourt.
  Royal Mines (150, Pure Ore chamber 130): the chasm floor under the bridge, an east hall and a south hall, rails, supports,
      ores and Mithril; a stair down to the Pure Ore chamber; the Royal Stair up to the palace.
  Royal Palace (187): forecourt with lava fountains, a gate, the throne hall (lava waterfall behind the throne), the Grand Library.
  Aristocrat Passage (150 -> 128): a long vaulted corridor from the south hall down west to doorway 9.
"""
import math
import random
from collections import deque
import numpy as np
from kit import Area, hash01, mc, _full
import dungeon_kit

OX, OY, OZ = 60, 100, -75
NX, NY, NZ = 160, 135, 250
area = Area("mines_east", (OX, OY, OZ), (NX, NY, NZ), version=1, shell=2, dimension="skyrpg_skyblock:dwarven_mines",
            island="Dwarven Mines")
rnd = random.Random(2026)
rng = np.random.default_rng(2026)

# ---------------------------------------------------------------- the block grid
PAL = ["minecraft:air", "ROCK"]
IDS = {"minecraft:air": 0, "ROCK": 1}
G = np.ones((NX, NY, NZ), dtype=np.int16)          # 1 = rock (picked at the end), 0 = air
XW = (np.arange(NX) + OX)[:, None]
ZW = (np.arange(NZ) + OZ)[None, :]
Xg = (np.arange(NX) + OX)[:, None, None]
Yg = (np.arange(NY) + OY)[None, :, None]
Zg = (np.arange(NZ) + OZ)[None, None, :]
SIGNS = []


def bid(name):
    name = mc(name)
    if name not in IDS:
        IDS[name] = len(PAL)
        PAL.append(name)
    return IDS[name]


def _sl(x1, y1, z1, x2, y2, z2):
    x1, x2 = sorted((x1, x2))
    y1, y2 = sorted((y1, y2))
    z1, z2 = sorted((z1, z2))
    a, b = max(x1, OX) - OX, min(x2, OX + NX - 1) - OX + 1
    c, d = max(y1, OY) - OY, min(y2, OY + NY - 1) - OY + 1
    e, f = max(z1, OZ) - OZ, min(z2, OZ + NZ - 1) - OZ + 1
    if a >= b or c >= d or e >= f:
        return None
    return (slice(a, b), slice(c, d), slice(e, f))


def box(x1, y1, z1, x2, y2, z2, b, mask=None):
    s = _sl(x1, y1, z1, x2, y2, z2)
    if s is None:
        return
    G[s] = bid(b)
    if mask is not None and b == "air":
        mask[s] = True


def A(x1, y1, z1, x2, y2, z2, mask=None):
    box(x1, y1, z1, x2, y2, z2, "air", mask)


def P(x, y, z, b):
    if OX <= x < OX + NX and OY <= y < OY + NY and OZ <= z < OZ + NZ:
        G[x - OX, y - OY, z - OZ] = bid(b)


def PA(x, y, z, b):
    """Only into air."""
    if OX <= x < OX + NX and OY <= y < OY + NY and OZ <= z < OZ + NZ and G[x - OX, y - OY, z - OZ] == 0:
        G[x - OX, y - OY, z - OZ] = bid(b)


def gid(x, y, z):
    if OX <= x < OX + NX and OY <= y < OY + NY and OZ <= z < OZ + NZ:
        return int(G[x - OX, y - OY, z - OZ])
    return -1


def isair(x, y, z):
    return gid(x, y, z) == 0


def sign(x, y, z, facing, lines):
    P(x, y, z, f"dark_oak_wall_sign[facing={facing}]")
    SIGNS.append((x, y, z, facing, lines))


def n2(x, z, seed=0.0, scale=1.0):
    s = seed * 1.7
    return (np.sin(x * 0.083 * scale + s) * np.cos(z * 0.071 * scale + s * 0.6) + 0.6 * np.sin((x + z) * 0.047 * scale + s * 1.3)
            + 0.4 * np.sin(x * 0.19 * scale - z * 0.17 * scale + s * 2.1) + 0.25 * np.cos(x * 0.31 * scale + z * 0.29 * scale - s)) / 2.25


def cave(cx, cz, rx, rz, floor, hmax, seed=0, hmin=4, wob=0.1, p=2.0, xlo=65, xhi=213, zlo=-70, zhi=169, mask=None,
         top=226):
    """A rounded cavern: floor block at floor-1, air from `floor` up (a dome to hmax)."""
    u = (np.abs((XW - cx) / rx) ** p + np.abs((ZW - cz) / rz) ** p) ** (1 / p) + wob * n2(XW, ZW, seed, 1.0)
    inside = (u < 1) & (XW >= xlo) & (XW <= xhi) & (ZW >= zlo) & (ZW <= zhi)
    h = np.where(inside, hmin + np.floor((hmax - hmin) * np.sqrt(np.clip(1 - u ** 3, 0, 1)) + 0.5 * n2(XW, ZW, seed + 4, 2.5)).astype(int), 0)
    h = np.minimum(h, top - floor + 1)
    h = np.where(inside, np.maximum(h, 3), 0)
    m = (Yg >= floor) & (Yg < floor + h[:, None, :])
    G[m] = 0
    if mask is not None:
        mask |= m
    return m


def dil(M):
    R = M.copy()
    R[1:] |= M[:-1]
    R[:-1] |= M[1:]
    R[:, 1:] |= M[:, :-1]
    R[:, :-1] |= M[:, 1:]
    R[:, :, 1:] |= M[:, :, :-1]
    R[:, :, :-1] |= M[:, :, 1:]
    return R


def dress(M, mix, seed=0, cond=None):
    """Rock next to the carved space M becomes dressed blocks (stone bricks, bookshelves...)."""
    tgt = dil(M) & (G == 1)
    if cond is not None:
        tgt &= cond
    pick = area.mix(mix) if isinstance(mix, dict) else mix
    for ix, iy, iz in np.argwhere(tgt).tolist():
        G[ix, iy, iz] = bid(pick(ix + OX, iy + OY, iz + OZ, seed))


def col_up(x, z, y0, b, maxh=40):
    y = y0
    while y < y0 + maxh and isair(x, y, z):
        P(x, y, z, b)
        y += 1
    return y


def hb(x, y, z, s=0):
    return hash01(x, y, z, s)


# ---------------------------------------------------------------- Mithril and ore pickers
def mith(ti=0.02):
    r = rnd.random()
    if r < ti:
        return "polished_diorite"
    r = rnd.random()
    if r < 0.70:
        return rnd.choice(["gray_wool", "cyan_terracotta"])
    if r < 0.95:
        return rnd.choice(["prismarine", "prismarine_bricks", "dark_prismarine"])
    return "light_blue_wool"


ORES = ["coal_ore"] * 3 + ["iron_ore"] * 3 + ["gold_ore"] * 2 + ["lapis_ore"] * 2 + ["redstone_ore"] * 2 + ["emerald_ore", "diamond_ore"]
PURE = ["coal_block", "iron_block", "gold_block", "lapis_block", "redstone_block", "emerald_block", "diamond_block"]

# ================================================================= CARVING
M_plaza = np.zeros(G.shape, bool)
M_hall = np.zeros(G.shape, bool)
M_lib = np.zeros(G.shape, bool)
M_ap = np.zeros(G.shape, bool)
M_ls = np.zeros(G.shape, bool)
M_rm = np.zeros(G.shape, bool)

# ---- Lava Springs
cave(104, -42, 42, 29, 197, 24, seed=1, hmin=5, mask=M_ls)
A(93, 197, -75, 97, 201, -70)               # doorway 3 (5x5 at the line)
A(92, 197, -70, 98, 202, -62)
A(60, 197, -42, 64, 201, -38)               # doorway 7
A(65, 197, -43, 72, 202, -37)
A(114, 197, -22, 126, 206, -8)              # gatehouse tunnel to the bridge

# ---- the chasm under the bridge (Royal Mines floor 150)
cave(120, 32.5, 32, 39.5, 150, 64, seed=2, hmin=60, wob=0.02, p=4, xlo=80, xhi=160, zlo=-7, zhi=72, mask=M_rm, top=213)

# ---- Royal Mines halls
cave(182, 40, 28, 38, 150, 16, seed=3, hmin=4, mask=M_rm)                    # east hall
A(146, 150, 28, 160, 158, 52, M_rm)
cave(118, 92, 30, 14, 150, 12, seed=4, hmin=4, mask=M_rm)                    # south hall
A(106, 150, 68, 132, 159, 82, M_rm)
cave(186, 36, 22, 24, 130, 12, seed=5, hmin=4, mask=M_rm)                    # Pure Ore chamber
A(207, 130, 34, 211, 134, 38, M_rm)                                           # fairy soul nook
# doorway 8: tunnel at 140, ramp up to 150, corridor into the chasm
A(60, 140, 38, 64, 144, 42, M_rm)
A(65, 140, 37, 72, 145, 43, M_rm)
for x in range(73, 83):
    s = 140 + (x - 72)
    A(x, s, 37, x, s + 5, 43, M_rm)
A(83, 150, 37, 92, 155, 43, M_rm)
# ramp from the east hall down to the Pure Ore chamber
A(182, 150, 70, 190, 156, 78, M_rm)
for z in range(54, 75):
    s = 150 - (74 - z)
    A(183, s, z, 189, s + 5, z, M_rm)
# Aristocrat Passage: from the south hall, stairs down 150 -> 128, then west to doorway 9
for z in range(98, 133):
    s = 150 if z <= 110 else 150 - (z - 110)
    A(97, s, z, 103, s + 7, z, M_ap)
A(97, 128, 133, 103, 135, 153, M_ap)
A(65, 128, 147, 103, 135, 153, M_ap)
A(60, 128, 148, 64, 132, 152, M_ap)

# ---- Royal Palace
cave(152.5, 81.5, 53.5, 8.6, 187, 14, seed=7, hmin=9, wob=0, p=4, xlo=98, xhi=206, zlo=73, zhi=90, mask=M_plaza)
A(122, 187, 94, 178, 207, 158, M_hall)                                         # throne hall
A(146, 187, 91, 154, 197, 93, M_hall)                                          # the gate
A(181, 187, 100, 203, 204, 145, M_lib)                                         # Grand Library
A(179, 187, 118, 180, 191, 122, M_lib)                                         # library door
# the Royal Stair: plaza (187) down to the east hall (150), x 197..203, z 77 -> 40
for z in range(40, 78):
    s = 150 + (z - 40)
    A(197, s, z, 203, s + 5, z, M_rm)

# ================================================================= LAVA SPRINGS dressing
WALLB = "polished_blackstone_bricks"
PWALL = "polished_blackstone_brick_wall"


def col_top(x, z, y):
    while isair(x, y + 1, z):
        y += 1
    return y


def lavafall(x, z, dx, dz, F, ytop, hw=1, rim_front=True):
    """A lava fall behind bars in an alcove in the wall. (x, z) is the first air cell, (dx, dz) points into the rock; a pool in
    front (t < 0). Everything around the lava is solid blackstone so it can't spill."""
    px, pz = -dz, dx

    def pos(t, l):
        return x + dx * t + px * l, z + dz * t + pz * l
    for t in range(-3, 4):
        for l in range(-hw - 1, hw + 2):
            X, Z = pos(t, l)
            edge = abs(l) == hw + 1
            if t == -3:
                if rim_front:
                    P(X, F - 1, Z, WALLB)
                    P(X, F, Z, PWALL)
            elif t < 0:
                if edge:
                    P(X, F - 1, Z, WALLB)
                    P(X, F, Z, PWALL)
                else:
                    P(X, F - 3, Z, "lava")
                    P(X, F - 2, Z, "lava")
                    P(X, F - 1, Z, "air")
            elif t == 0:
                if edge:
                    for y in range(F - 3, ytop + 2):
                        P(X, y, Z, WALLB)
                else:
                    P(X, F - 3, Z, "lava")
                    P(X, F - 2, Z, "lava")
                    for y in range(F - 1, ytop + 1):
                        P(X, y, Z, "iron_bars")
                    P(X, ytop + 1, Z, WALLB)
            elif t in (1, 2):
                if edge:
                    for y in range(F - 3, ytop + 2):
                        P(X, y, Z, WALLB)
                else:
                    for y in range(F - 3, ytop + 1):
                        P(X, y, Z, "lava")
                    P(X, ytop + 1, Z, WALLB)
            else:
                for y in range(F - 3, ytop + 2):
                    P(X, y, Z, WALLB)


LSF = 197
# find the cave walls for the falls
zA = next(z for z in range(-75, -20) if isair(82, LSF, z))
zC = next(z for z in range(-75, -20) if isair(116, LSF, z) and z > -60)
zD = next(z for z in range(-75, -20) if isair(136, LSF, z) and z > -62)
xE = next(x for x in range(160, 100, -1) if isair(x, LSF, -50))
lavafall(82, zA, 0, -1, LSF, min(col_top(82, zA, LSF), LSF + 16), hw=1, rim_front=False)
lavafall(116, zC, 0, -1, LSF, min(col_top(116, zC, LSF), LSF + 16), hw=2)
lavafall(136, zD, 0, -1, LSF, min(col_top(136, zD, LSF), LSF + 16), hw=2)
lavafall(xE, -50, 1, 0, LSF, min(col_top(xE, -50, LSF), LSF + 16), hw=2)

# the lava channel: down from the first fall along x 81..83, then east along z -31..-29
zs = zA + 3
A(81, 194, zs, 83, 196, -30)
A(81, 194, -31, 134, 196, -29)
box(81, 194, zs, 83, 195, -30, "lava")
box(81, 194, -31, 134, 195, -29, "lava")
BR1 = range(-43, -36)          # slab bridge over the vertical leg, z
BR2 = range(117, 124)          # and over the east leg, x
for z in range(zs, -29):
    if z in BR1:
        continue
    for x in (80, 84):
        PA(x, 197, z, PWALL)
for x in range(80, 136):
    if x in BR2:
        continue
    for z in (-32, -28):
        PA(x, 197, z, PWALL)
for z in range(-31, -28):
    PA(135, 197, z, PWALL)
    P(135, 196, z, WALLB)
box(81, 196, -43, 83, 196, -37, "stone_bricks")
for z in (-43, -37):
    box(81, 196, z, 83, 196, z, "polished_blackstone_bricks")
box(117, 196, -31, 123, 196, -29, "stone_bricks")
for x in (117, 123):
    box(x, 196, -31, x, 196, -29, "polished_blackstone_bricks")
for (px_, pz_) in [(80, -44), (80, -36), (84, -44), (84, -36), (116, -32), (124, -32), (116, -28), (124, -28)]:
    for y in (197, 198):
        P(px_, y, pz_, WALLB)
    P(px_, 199, pz_, "lantern[hanging=false]")

# gatehouse: pillars and a lintel over the bridge mouth, statues beside
for z in range(-13, -9):
    for x in (114, 115, 125, 126):
        for y in range(197, 207):
            P(x, y, z, WALLB)
    for x in range(116, 125):
        for y in (205, 206):
            P(x, y, z, WALLB)
        P(x, 204, z, "polished_blackstone_brick_stairs[facing=south,half=top]" if z == -10 else WALLB)
for x in (114, 115, 125, 126):
    for y in (199, 204):
        P(x, y, -13, "gilded_blackstone")
sign(116, 201, -11, "east", ["Palace Bridge", "to the Royal Palace"])
for y in range(197, 204):
    pass
P(120, 205, -10, "lantern[hanging=true]")
P(120, 206, -10, "iron_chain[axis=y]")

# ================================================================= THE BRIDGE
def ys_at(z):
    return 197 if z < 7 else max(187, 197 - z // 7)


for z in range(-8, 74):
    ys = ys_at(z)
    for x in range(117, 124):
        P(x, ys - 1, z, "stone_bricks" if (x + z) % 5 else "chiseled_stone_bricks" if x == 120 else "stone_bricks")
        P(x, ys - 2, z, "stone_bricks")
        for y in range(ys, ys + 5):
            P(x, y, z, "air")
    for x in (116, 124):
        P(x, ys - 1, z, WALLB)
        P(x, ys - 2, z, WALLB)
        P(x, ys, z, "stone_brick_wall")
        for y in range(ys + 1, ys + 5):
            P(x, y, z, "air")
    P(120, ys - 1, z, "polished_blackstone_bricks" if z % 2 == 0 else "stone_bricks")
# lantern posts and statues on the rails
for z in range(0, 73, 8):
    ys = ys_at(z)
    for x in (116, 124):
        for y in range(ys, ys + 3):
            P(x, y, z, WALLB)
        P(x, ys + 3, z, "lantern[hanging=false]")
for z in (20, 52):
    ys = ys_at(z)
    for x in (116, 124):
        P(x, ys, z, "stone_bricks")
        P(x, ys + 1, z, "chiseled_stone_bricks")
        P(x, ys + 2, z, "chiseled_stone_bricks")
        P(x, ys + 3, z, "polished_blackstone")
        P(x, ys + 4, z, "stone_brick_slab[type=top]")
        P(x, ys + 2, z - 1, "iron_bars")
        P(x, ys + 2, z + 1, "iron_bars")
# grand arches over the deck
for z in (14, 34, 54):
    ys = ys_at(z)
    for x in (116, 124):
        for y in range(ys, ys + 7):
            P(x, y, z, "polished_blackstone_bricks" if y != ys + 3 else "gilded_blackstone")
    for x in range(116, 125):
        P(x, ys + 7, z, "polished_blackstone_bricks")
        P(x, ys + 8, z, "stone_brick_slab[type=bottom]")
    P(120, ys + 6, z, "lantern[hanging=true]")
    for x in (118, 122):
        P(x, ys + 6, z, "red_wall_banner[facing=south]" if False else "air")
# piers and cross beams under the deck
for z in range(4, 70, 12):
    ys = ys_at(z)
    for dz in (0, 1):
        for x in (114, 115, 125, 126):
            for y in range(150, ys - 1):
                P(x, y, z + dz, "stone_bricks" if (y - 150) % 9 != 4 else "sea_lantern")
        for x in range(114, 127):
            P(x, ys - 3, z + dz, "polished_blackstone_bricks")
# the plaza's north edge
for x in range(100, 153):
    if 116 <= x <= 124:
        continue
    PA(x, 187, 73, "stone_brick_wall")

# ================================================================= ROYAL MINES dressing
# lava falls down the chasm's east wall
for zf in (14, 60):
    xw = next(x for x in range(120, 170) if gid(x, 151, zf) == 1)
    lavafall(xw - 1, zf, 1, 0, 150, 200, hw=2)


def rail_x(x1, x2, z, y=150):
    for x in range(x1, x2 + 1):
        if gid(x, y, z) == 0 and gid(x, y - 1, z) not in (0, -1):
            P(x, y, z, "powered_rail[shape=east_west,powered=true]" if (x % 9 == 0) else "rail[shape=east_west]")


def rail_z(z1, z2, x, y=150):
    for z in range(z1, z2 + 1):
        if gid(x, y, z) == 0 and gid(x, y - 1, z) not in (0, -1) and PAL[gid(x, y, z)] == "minecraft:air":
            P(x, y, z, "powered_rail[shape=north_south,powered=true]" if (z % 9 == 0) else "rail[shape=north_south]")


def support_x(x, z, y, h=4):
    """A timber frame across a track along x: two posts and a beam, a lantern hanging from it."""
    for dz in (-3, 3):
        for yy in range(y, y + h):
            PA(x, yy, z + dz, "oak_log[axis=y]")
    for dz in range(-3, 4):
        PA(x, y + h, z + dz, "oak_log[axis=z]")
    PA(x, y + h - 1, z, "lantern[hanging=true]")


def support_z(x, z, y, h=4):
    for dx in (-3, 3):
        for yy in range(y, y + h):
            PA(x + dx, yy, z, "oak_log[axis=y]")
    for dx in range(-3, 4):
        PA(x + dx, y + h, z, "oak_log[axis=x]")
    PA(x, y + h - 1, z, "lantern[hanging=true]")


rail_x(86, 208, 46)
rail_z(-6, 98, 120)
for x in range(92, 206, 8):
    if x in range(112, 129):
        continue
    support_x(x, 46, 150)
for z in range(2, 98, 8):
    support_z(120, z, 150)
# the ramp from doorway 8
for x in range(73, 83):
    s = 140 + (x - 72)
    for z in range(37, 44):
        P(x, s - 1, z, "stone_brick_stairs[facing=east,half=bottom]")
# the stair to the Pure Ore chamber
for z in range(54, 75):
    s = 150 - (74 - z)
    for x in range(183, 190):
        P(x, s - 1, z, "stone_brick_stairs[facing=south,half=bottom]")
    PA(182, s, z, "stone_brick_wall") if False else None
for z in range(54, 71):
    s = 150 - (74 - z)
    if z % 5 == 0:
        for x in (183, 189):
            PA(x, s + 2, z, "lantern[hanging=false]") if False else None
# sign for the mines at doorway 8, for the Pure Ore chamber and for the passage
sign(66, 142, 37, "south", ["Royal Mines"])
sign(183, 134, 53, "south", ["Pure Ore Chamber"]) if False else None

# ================================================================= ROYAL PALACE dressing
dress(M_hall, {"stone_bricks": 6, "polished_blackstone_bricks": 3, "cracked_stone_bricks": 1, "deepslate_bricks": 2}, seed=3)
dress(M_plaza, {"stone_bricks": 6, "polished_blackstone_bricks": 3, "cracked_stone_bricks": 1}, seed=4,
      cond=(Zg >= 91) & (Xg >= 104) & (Xg <= 200))
dress(M_lib, {"bookshelf": 1}, seed=5, cond=(Yg >= 188) & (Yg <= 192))
dress(M_lib, {"dark_oak_planks": 5, "stripped_dark_oak_log": 2, "spruce_planks": 1}, seed=6)
dress(M_ap, {"stone_bricks": 6, "polished_andesite": 2, "cracked_stone_bricks": 1, "polished_blackstone_bricks": 1}, seed=7)

# floors
for x in range(98, 207):
    for z in range(73, 91):
        if gid(x, 187, z) == 0 and not (196 <= x <= 204 and z <= 77):
            P(x, 186, z, "polished_blackstone_bricks" if ((x // 2 + z // 2) % 2) else "stone_bricks")
for x in range(122, 179):
    for z in range(94, 159):
        P(x, 186, z, "polished_blackstone_bricks" if ((x // 2 + z // 2) % 2) else "stone_bricks")
for x in range(146, 155):
    for z in range(91, 94):
        P(x, 186, z, "stone_bricks")
for x in range(179, 204):
    for z in range(100, 146):
        if gid(x, 187, z) == 0:
            P(x, 186, z, "dark_oak_planks" if (x + z) % 3 else "spruce_planks")

# carpet runner in the hall and up to the gate
for z in range(84, 150):
    if z in (91, 92, 93) or z < 91 or z >= 94:
        for x in range(148, 153):
            PA(x, 187, z, "red_carpet")
        PA(147, 187, z, "yellow_carpet")
        PA(153, 187, z, "yellow_carpet")

# facade pillars and banners on the plaza side
for x in (108, 120, 132, 142, 157, 167, 179, 191):
    for dx in (0, 1):
        for dz in (0, 1):
            y = 187
            while y < 200 and isair(x + dx, y + 1, 89 + dz):
                P(x + dx, y, 89 + dz, "polished_blackstone_bricks" if y not in (188, 196) else "gilded_blackstone")
                y += 1
            P(x + dx, y, 89 + dz, "chiseled_polished_blackstone")
for x in (114, 126, 137, 162, 173, 185, 197):
    P(x, 193, 90, "red_wall_banner[facing=north]")
    P(x, 192, 90, "air")
for x in (145, 155):
    for y in range(187, 199):
        P(x, y, 91, "polished_blackstone_bricks")
        P(x, y, 92, "polished_blackstone_bricks")
        P(x, y, 93, "polished_blackstone_bricks")
for x in range(146, 155):
    for z in (91, 92, 93):
        P(x, 198, z, "polished_blackstone_bricks")
        P(x, 197, z, "gilded_blackstone" if x in (146, 154) else "polished_blackstone_brick_stairs[facing=north,half=top]" if z == 91 else "polished_blackstone_bricks")
sign(145, 195, 90, "north", ["Royal Palace"]) if False else None
sign(144, 195, 90, "north", ["Royal Palace"])
# lava fountains in the forecourt
for (fx, fz) in ((128, 85), (176, 85)):
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            r2 = dx * dx + dz * dz
            X, Z = fx + dx, fz + dz
            if r2 <= 4:
                P(X, 184, Z, "air")
                P(X, 185, Z, "lava")
                P(X, 186, Z, "lava")
                for y in range(187, 192):
                    P(X, y, Z, "air")
            elif r2 <= 9:
                P(X, 187, Z, WALLB)
                P(X, 188, Z, PWALL)
    for y in range(187, 191):
        P(fx, y, fz, "chiseled_polished_blackstone")
    P(fx, 191, fz, "lantern[hanging=false]")
# statues and lantern posts in the forecourt (out of the way of the walking line)
for x in (104, 112, 136, 144, 160, 168, 192, 200):
    for y in range(187, 190):
        P(x, y, 77, WALLB)
    P(x, 190, 77, "lantern[hanging=false]")

# hall columns, beams, chandeliers, banners
for z in range(100, 151, 10):
    for x in (132, 167):
        for dx in (0, 1):
            for dz in (0, 1):
                for y in range(187, 207):
                    P(x + dx, y, z + dz, "gilded_blackstone" if y in (188, 205) else "polished_blackstone_bricks")
                P(x + dx, 206, z + dz, "chiseled_polished_blackstone")
for z in range(98, 156, 8):
    for x in range(122, 179):
        if isair(x, 207, z):
            P(x, 207, z, "dark_oak_log[axis=x]")
for z in range(102, 156, 8):
    for x in (141, 159):
        if isair(x, 206, z):
            for y in range(204, 207):
                P(x, y, z, "iron_chain[axis=y]")
            P(x, 203, z, "lantern[hanging=true]")
for z in range(131, 158, 8):
    P(122, 192, z, "red_wall_banner[facing=east]")
for z in (105, 125, 135, 145):
    P(178, 192, z, "red_wall_banner[facing=west]")
sign(178, 194, 120, "west", ["Grand Library", "(east door)"])
# west gallery: a stair up the west wall to a balcony (a fairy soul sits there)
for z in range(94, 108):
    s = 187 + (z - 94)
    for x in (122, 123, 124):
        for y in range(187, s):
            P(x, y, z, "stone_bricks")
        P(x, s - 1, z, "stone_brick_stairs[facing=south,half=bottom]") if s > 187 else None
        for y in range(s, s + 5):
            P(x, y, z, "air")
for z in range(107, 131):
    for x in (122, 123, 124):
        for y in range(187, 200):
            P(x, y, z, "stone_bricks")
        for y in range(200, 205):
            P(x, y, z, "air")
    P(125, 200, z, "stone_brick_wall")
    if z % 6 == 0:
        P(125, 201, z, "lantern[hanging=false]")
for z in (108, 114, 120, 126):
    P(122, 202, z, "red_wall_banner[facing=east]")
# the dais and the throne
for x in range(138, 163):
    P(x, 187, 149, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
    for z in range(150, 153):
        P(x, 187, z, "polished_blackstone_bricks")
    P(x, 188, 153, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
    P(x, 187, 153, "polished_blackstone_bricks")
    for z in range(154, 159):
        P(x, 187, z, "polished_blackstone_bricks")
        P(x, 188, z, "polished_blackstone_bricks")
for x in range(148, 153):
    for z in range(149, 158):
        for y in range(187, 190):
            if PAL[gid(x, y, z)] == "minecraft:air" and y == (188 if z <= 152 else 189 if z >= 154 else 188):
                pass
P(150, 189, 156, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
for x in (149, 151):
    P(x, 189, 156, "waxed_cut_copper_stairs[facing=south,half=bottom]")
for x in range(148, 153):
    for y in range(189, 197):
        P(x, y, 157, "waxed_cut_copper" if y < 195 else "gilded_blackstone")
        P(x, y, 158, "polished_blackstone_bricks")
for x in (147, 153):
    for y in range(189, 193):
        P(x, y, 157, "waxed_copper_block")
for x in range(139, 162):
    if not (146 <= x <= 154):
        PA(x, 189, 154, "red_carpet")
for x in range(148, 153):
    PA(x, 188, 150, "red_carpet")
    PA(x, 188, 151, "red_carpet")
    PA(x, 188, 152, "red_carpet")
    PA(x, 189, 155, "red_carpet")
# lava waterfall behind the throne: bars at z=159, lava behind
for x in range(138, 163):
    for z in range(159, 163):
        for y in range(187, 209):
            P(x, y, z, WALLB)
for x in range(139, 162):
    for z in (160, 161):
        for y in range(187, 208):
            P(x, y, z, "lava")
    for y in range(190, 207):
        P(x, y, 159, "iron_bars")
for x in range(139, 162):
    P(x, 189, 158, "polished_blackstone_brick_wall") if x not in range(147, 154) else None
# library furnishings
for z in list(range(104, 114)) + list(range(127, 141)):
    for x in (186, 187, 196, 197):
        for y in range(187, 190):
            P(x, y, z, "bookshelf")
        P(x, 190, z, "dark_oak_slab[type=bottom]")
for z in (108, 120, 132):
    for x in (184, 191, 199):
        for y in range(200, 205):
            P(x, y, z, "iron_chain[axis=y]")
        P(x, 199, z, "lantern[hanging=true]")
for (x, z, f) in ((190, 115, "south"), (194, 115, "south"), (190, 125, "north"), (194, 125, "north"), (183, 108, "east"), (201, 132, "west")):
    P(x, 187, z, f"lectern[facing={f}]")
P(191, 187, 126, "enchanting_table")
P(193, 187, 126, "enchanting_table")
for z in range(102, 144, 6):
    P(182, 190, z, "lantern[hanging=false]") if False else None
for z in range(103, 143, 8):
    PA(203, 192, z, "red_wall_banner[facing=west]") if False else None

# the Royal Stair
for z in range(40, 78):
    s = 150 + (z - 40)
    for x in range(197, 204):
        for y in range(150, s - 1):
            if gid(x, y, z) == 0:
                P(x, y, z, "stone_bricks")
        P(x, s - 1, z, "stone_brick_stairs[facing=south,half=bottom]") if s < 187 else None
    if s < 187:
        for x in (196, 204):
            PA(x, s, z, "stone_brick_wall")
            if z < 73 and gid(x, s - 1, z) == 0:
                P(x, s - 1, z, "stone_bricks")
                for y in range(150, s - 1):
                    PA(x, y, z, "stone_bricks")
    if z % 6 == 0 and s < 185:
        for x in (196, 204):
            PA(x, s + 1, z, "lantern[hanging=false]")

# ================================================================= ARISTOCRAT PASSAGE dressing
def stand_ap(z):
    return 150 if z <= 110 else max(128, 150 - (z - 110))


for z in range(111, 133):
    s = stand_ap(z)
    for x in range(97, 104):
        P(x, s - 1, z, "stone_brick_stairs[facing=north,half=bottom]")
for z in range(98, 154):
    s = stand_ap(z)
    if z <= 110:
        continue
    if z >= 133:
        for x in range(97, 104):
            P(x, 127, z, "polished_andesite" if (x + z) % 2 else "stone_bricks")
for x in range(65, 97):
    for z in range(147, 154):
        P(x, 127, z, "polished_andesite" if (x + z) % 2 else "stone_bricks")
for z in range(133, 152):
    for x in range(99, 102):
        PA(x, 128, z, "red_carpet")
for x in range(60, 97):
    for z in range(149, 152):
        PA(x, 128, z, "red_carpet")
for z in range(102, 150, 6):
    s = stand_ap(z)
    for x in (97, 103):
        for y in range(s, s + 7):
            P(x, y, z, "polished_blackstone_bricks" if y != s + 1 else "gilded_blackstone")
        P(x, s + 7, z, "chiseled_polished_blackstone")
    P(100, s + 6, z, "lantern[hanging=true]")
    for y in range(s + 7, s + 8):
        P(100, y, z, "iron_chain[axis=y]")
    if z % 12 == 0:
        P(97, s + 3, z + 3, "red_wall_banner[facing=east]")
        P(103, s + 3, z + 3, "red_wall_banner[facing=west]")
for x in range(66, 103, 6):
    for z in (147, 153):
        for y in range(128, 135):
            P(x, y, z, "polished_blackstone_bricks" if y != 129 else "gilded_blackstone")
        P(x, 135, z, "chiseled_polished_blackstone")
    P(x + 3, 133, 150, "lantern[hanging=true]")
    P(x + 3, 134, 150, "iron_chain[axis=y]")
    P(x + 3, 135, 150, "iron_chain[axis=y]")
    P(x + 3, 132, 150, "air")
    P(x + 3, 133, 150, "lantern[hanging=true]")
    P(x + 3, 131, 150, "air")
sign(66, 130, 147, "south", ["Aristocrat Passage"])

# Mithril-free decor: hanging banners on the south hall; barrels near the stations
for (x, z) in ((176, 54), (172, 38), (126, 98), (110, 98)):
    P(x, 150, z, "barrel[facing=up]")
    P(x + 1, 150, z, "barrel[facing=up]")

# ================================================================= scenery: boulders, columns, ceilings
KEEP = np.zeros((NX, NZ), bool)


def keep_disk(x, z, r):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if dx * dx + dz * dz <= r * r and 0 <= x + dx - OX < NX and 0 <= z + dz - OZ < NZ:
                KEEP[x + dx - OX, z + dz - OZ] = True


def keep_line(pts, r=4):
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            keep_disk(int(round(x1 + (x2 - x1) * i / n)), int(round(z1 + (z2 - z1) * i / n)), r)


NPCS = {
    "Emissary Lilith": (99, 197, -56, 90, "Emissary: commissions"),
    "Castle Guard": (117, 197, -4, 180, "Palace Bridge guard"),
    "Castle Guard 2": (123, 197, -4, 180, "Palace Bridge guard"),
    "Emissary Wilson": (170, 150, 52, 90, "Emissary: commissions"),
    "Marigold": (126, 150, 92, 180, "Royal Mines: quests and shop"),
    "King": (150, 189, 155, 180, "the dwarven King, on the throne dais"),
    "Queen Mismyla": (146, 189, 155, 180, "Queen Mismyla, beside the King"),
    "Dalbrek": (192, 187, 110, 90, "Grand Library"),
    "Tal Ker": (192, 187, 132, 90, "Grand Library"),
    "Puzzler": (176, 187, 115, 90, "the Puzzler, by the library door"),
}
ARRIVAL = (96, 197, -66, 0.0)
SOULS = [(123, 201, 125), (210, 131, 36)]
DOORS = {3: (95, 197, -75), 7: (60, 197, -40), 8: (60, 140, 40), 9: (60, 128, 150)}

keep_line([(95, -75), (95, -45), (120, -45), (120, -8)], 5)
keep_line([(60, -40), (95, -40)], 5)
keep_line([(84, 40), (100, 46), (205, 46)], 5)
keep_line([(60, 40), (90, 40)], 5)
keep_line([(120, -8), (120, 100)], 5)
keep_line([(100, 98), (100, 112)], 5)
keep_line([(186, 46), (186, 78)], 5)
keep_line([(200, 40), (200, 78)], 6)
keep_line([(186, 54), (186, 30)], 5)
keep_line([(80, -30), (140, -20)], 3)
for n, (x, y, z, yaw, note) in NPCS.items():
    keep_disk(x, z, 3)
keep_disk(ARRIVAL[0], ARRIVAL[2], 4)
for (x, y, z) in SOULS:
    keep_disk(x, z, 3)
for x in range(78, 138):                 # the channel and its rims
    for z in range(zs - 4, -26):
        if 77 <= x <= 87 and z <= -26 or (-35 <= z <= -25 and x <= 138):
            keep_disk(x, z, 1)
for z in range(zs - 3, -25):
    for x in range(78, 87):
        keep_disk(x, z, 1)


def zone_of(x, y, z):
    if z < -8:
        return "LS"
    if -8 <= z < 110 and y < 183:
        return "RM"
    return None


def boulder(cx, cz, r, floor, kind):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(0, r + 1):
                d = math.sqrt(dx * dx + dz * dz + dy * dy * 1.7)
                if d <= r + 0.35 - hb(cx + dx, dy, cz + dz, 3) * 0.5:
                    x, y, z = cx + dx, floor + dy, cz + dz
                    if gid(x, y, z) == 0:
                        if rnd.random() < 0.68:
                            if kind == "ore" and rnd.random() < 0.5:
                                P(x, y, z, rnd.choice(ORES))
                            else:
                                P(x, y, z, mith())
                        else:
                            G[x - OX, y - OY, z - OZ] = 1


def ok_spot(x, z, floor, r):
    for dx in range(-r - 1, r + 2):
        for dz in range(-r - 1, r + 2):
            xx, zz = x + dx, z + dz
            if not (0 <= xx - OX < NX and 0 <= zz - OZ < NZ) or KEEP[xx - OX, zz - OZ]:
                return False
            if gid(xx, floor, zz) != 0 or gid(xx, floor + 5, zz) != 0 or gid(xx, floor - 1, zz) != 1:
                return False
    return True


nb = 0
for _ in range(6000):
    if nb >= 70:
        break
    x, z = rnd.randint(66, 146), rnd.randint(-68, -14)
    r = rnd.choice([1, 1, 2, 2])
    if ok_spot(x, z, 197, r):
        boulder(x, z, r, 197, "mith")
        keep_disk(x, z, r + 2)
        nb += 1
print("LS boulders", nb)
nb = 0
for _ in range(1500):
    if nb >= 60:
        break
    x, z = rnd.randint(88, 208), rnd.randint(0, 104)
    r = rnd.choice([1, 1, 2, 2])
    if ok_spot(x, z, 150, r):
        boulder(x, z, r, 150, "ore")
        keep_disk(x, z, r + 2)
        nb += 1
print("RM boulders", nb)

# basalt columns in Lava Springs (floor to ceiling)
nb = 0
for _ in range(600):
    if nb >= 14:
        break
    x, z = rnd.randint(66, 146), rnd.randint(-68, -14)
    if ok_spot(x, z, 197, 1) and gid(x, 205, z) == 0:
        keep_disk(x, z, 4)
        for dx in (0, 1):
            for dz in (0, 1):
                y = 197
                while isair(x + dx, y, z + dz):
                    P(x + dx, y, z + dz, "basalt[axis=y]" if y % 7 else "smooth_basalt")
                    y += 1
        nb += 1
print("columns", nb)

# the Pure Ore chamber: seven outcrops
for (b, (x, z)) in zip(PURE, [(172, 26), (176, 48), (188, 18), (200, 26), (202, 40), (198, 50), (170, 38)]):
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(0, 3):
                if dx * dx + dz * dz + dy * dy * 1.8 <= 5.2 and gid(x + dx, 130 + dy, z + dz) == 0:
                    P(x + dx, 130 + dy, z + dz, b if rnd.random() < 0.6 else "deepslate")
    keep_disk(x, z, 4)
P(210, 135, 36, "sea_lantern")

# ================================================================= WALKING AND LIGHT CHECKS
LAVA_ID = set()


def base(n):
    return n.split(":")[1].split("[")[0] if ":" in n else n


PASS_NAMES = ("lantern", "iron_chain", "rail", "carpet", "banner", "sign", "torch", "fern", "flower", "button", "lever")


def is_pass(n):
    if n == "ROCK":
        return False
    b = base(n)
    if b == "sea_lantern":
        return False
    return b == "air" or any(k in b for k in PASS_NAMES)


def emit(n):
    if n == "ROCK":
        return 0
    b = base(n)
    if b in ("lava", "lantern", "sea_lantern", "glowstone", "shroomlight", "campfire"):
        return 15
    if b == "soul_lantern":
        return 10
    if b == "torch":
        return 14
    if b == "magma_block":
        return 3
    return 0


def opaque(n):
    return n == "ROCK" or _full(n)


def walk_arrays():
    pv = np.array([is_pass(n) for n in PAL])
    lv = np.array([base(n) == "lava" for n in PAL])
    PASS = pv[G]
    SOL = ~PASS & ~lv[G]
    below = np.zeros_like(PASS)
    below[:, 1:] = SOL[:, :-1]
    up1 = np.zeros_like(PASS)
    up1[:, :-1] = PASS[:, 1:]
    return PASS, PASS & up1 & below


def bfs(starts):
    PASS, STAND = walk_arrays()
    st = bytearray(STAND.ravel().tobytes())
    pa = bytearray(PASS.ravel().tobytes())
    sx, sy = NY * NZ, NZ
    seen = bytearray(len(st))
    dq = deque()
    for (x, y, z) in starts:
        i = ((x - OX) * NY + (y - OY)) * NZ + (z - OZ)
        if not st[i]:
            raise SystemExit(f"start {(x, y, z)} is not a standing spot")
        seen[i] = 1
        dq.append(i)
    n_total = len(st)
    while dq:
        c = dq.popleft()
        iz = c % NZ
        ix = c // sx
        for d, ok in ((sx, ix + 1 < NX), (-sx, ix > 0), (1, iz + 1 < NZ), (-1, iz > 0)):
            if not ok:
                continue
            n = c + d
            for dy in (0, 1, -1, -2, -3):
                m = n + dy * sy
                if not (0 <= m < n_total) or not st[m] or seen[m]:
                    continue
                if dy == 1 and not pa[c + 2 * sy]:
                    continue
                if dy < 0 and not all(pa[m + k * sy] for k in range(2, 2 - dy + 2)):
                    continue
                seen[m] = 1
                dq.append(m)
    return np.frombuffer(bytes(seen), dtype=np.uint8).reshape(G.shape).astype(bool), STAND


def light():
    em = np.array([emit(n) for n in PAL], dtype=np.int8)[G]
    trans = np.array([not opaque(n) for n in PAL])[G]
    L = em.copy()
    for _ in range(15):
        Nn = np.zeros_like(L)
        Nn[1:] = np.maximum(Nn[1:], L[:-1])
        Nn[:-1] = np.maximum(Nn[:-1], L[1:])
        Nn[:, 1:] = np.maximum(Nn[:, 1:], L[:, :-1])
        Nn[:, :-1] = np.maximum(Nn[:, :-1], L[:, 1:])
        Nn[:, :, 1:] = np.maximum(Nn[:, :, 1:], L[:, :, :-1])
        Nn[:, :, :-1] = np.maximum(Nn[:, :, :-1], L[:, :, 1:])
        Nn = np.maximum(Nn - 1, 0)
        L = np.maximum(L, np.where(trans, Nn, 0)).astype(np.int8)
    return L


# rock ceilings: shroomlights/glowstone clusters and dripstone, before the light fix
def decorate_ceilings():
    ceil = (G == 1)
    ceil[:, 1:, :] &= (G[:, :-1, :] == 0)
    ceil[:, 0, :] = False
    n = 0
    for ix, iy, iz in np.argwhere(ceil).tolist():
        x, y, z = ix + OX, iy + OY, iz + OZ
        h = hb(x, y, z, 11)
        if h < 0.004:
            G[ix, iy, iz] = bid("shroomlight" if z < -8 else "glowstone")
            n += 1
        elif h < 0.030 and G[ix, iy - 1, iz] == 0:
            ln = 1 + int(hb(x, 1, z, 12) * 3)
            for i in range(ln):
                if iy - 1 - i < 0 or G[ix, iy - 1 - i, iz] != 0:
                    break
                th = "tip" if i == ln - 1 else "middle"
                G[ix, iy - 1 - i, iz] = bid(f"pointed_dripstone[vertical_direction=down,thickness={th}]") if i == ln - 1 or ln == 1 else bid("pointed_dripstone[vertical_direction=down,thickness=base]") if i == 0 else bid("pointed_dripstone[vertical_direction=down,thickness=middle]")
    return n


print("ceiling lights", decorate_ceilings())

starts = [ARRIVAL[:3]]
reach, STAND = bfs(starts)
print("walkable cells", int(reach.sum()))
import os
if os.environ.get("DBG"):
    idx = np.argwhere(reach)
    print("reach bbox", idx.min(0) + [OX, OY, OZ], idx.max(0) + [OX, OY, OZ])
    for zz in range(-70, 80, 5):
        row = reach[:, :, zz - OZ].any(axis=1)
        print(zz, "".join("#" if row[i] else "." for i in range(0, NX, 2)))
    for zz in range(94, 160, 3):
        print(zz, "".join("#" if reach[i, 187 - OY:190 - OY, zz - OZ].any() else ("a" if G[i, 187 - OY, zz - OZ] == 0 else ".") for i in range(120 - OX, 182 - OX)))
    raise SystemExit

# veins of Mithril, Titanium and ores in the walls and floors players can reach
PASSA, _ = walk_arrays()
nov = [(x, z, 3) for (x, y, z, yaw, note) in NPCS.values()] + [(x, z, 3) for (x, y, z) in SOULS] + \
      [(95, -75, 6), (60, -40, 6), (60, 40, 6), (60, 150, 6), (ARRIVAL[0], ARRIVAL[2], 4)]
rc = np.argwhere(reach & (Yg < 400))
seeds = []
for ix, iy, iz in rc.tolist():
    x, y, z = ix + OX, iy + OY, iz + OZ
    zn = zone_of(x, y, z)
    if zn is None:
        continue
    if any((x - a) ** 2 + (z - b) ** 2 <= r * r for a, b, r in nov):
        continue
    if KEEP[ix, iz] and hb(x, y, z, 21) > 0.2:
        pass
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for dy in (0, 1, 2):
            if gid(x + dx, y + dy, z + dz) == 1 and hb(x + dx, y + dy, z + dz, 22) < (0.075 if zn == "LS" else 0.032):
                seeds.append((x + dx, y + dy, z + dz, zn))
    if gid(x, y - 1, z) == 1 and hb(x, y - 1, z, 23) < (0.03 if zn == "LS" else 0.012) and not KEEP[ix, iz]:
        seeds.append((x, y - 1, z, zn))
print("vein seeds", len(seeds))


def vein_block(zn, x, y, z):
    if zn == "RM" and 130 <= y <= 142 and 164 <= x <= 212 and 10 <= z <= 64:
        return rnd.choice(PURE) if rnd.random() < 0.85 else mith()
    if zn == "RM" and rnd.random() < 0.40:
        return rnd.choice(ORES)
    return mith()


placed = 0
for (x, y, z, zn) in seeds:
    cells = {(x, y, z)}
    frontier = [(x, y, z)]
    for gen in range(2):
        nxt = []
        for (a, b, c) in frontier:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                p = (a + dx, b + dy, c + dz)
                if p not in cells and gid(*p) == 1 and rnd.random() < (0.55 if gen == 0 else 0.3) and zone_of(*p) == zn:
                    cells.add(p)
                    nxt.append(p)
        frontier = nxt
    # a vein is one material family: pick it per block, but most of a vein shares a kind
    kind = rnd.random() < 0.5
    for p in cells:
        if gid(*p) != 1:
            continue
        P(*p, vein_block(zn, *p) if (kind or rnd.random() < 0.5) else mith())
        placed += 1
print("vein blocks", placed)

# final light fix: lanterns on chains from the ceiling (or sea lanterns in the wall) wherever a walkable spot is dim
for rnd_pass in range(14):
    L = light()
    dark = np.argwhere(reach & (L < 7))
    print("pass", rnd_pass, "dark standing cells", len(dark))
    if len(dark) == 0:
        break
    used = set()
    added = 0
    for ix, iy, iz in dark.tolist():
        key = (ix // 6, iy // 7, iz // 6)
        if any((key[0] + a, key[1] + b, key[2] + c) in used for a in (-1, 0, 1) for b in (-1, 0, 1) for c in (-1, 0, 1)):
            continue
        x, y, z = ix + OX, iy + OY, iz + OZ
        yc = None
        for yy in range(y + 3, y + 15):
            if gid(x, yy, z) != 0:
                yc = yy
                break
        done = False
        if yc is not None and gid(x, yc, z) in (1,) or (yc is not None and PAL[gid(x, yc, z)] not in ("minecraft:air",)):
            yl = min(y + 3, yc - 1)
            if yl >= y + 2 and all(isair(x, yy, z) for yy in range(yl, yc)):
                for yy in range(yl + 1, yc):
                    P(x, yy, z, "iron_chain[axis=y]")
                P(x, yl, z, "lantern[hanging=true]")
                done = True
        if not done:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for k in range(1, 5):
                    g = gid(x + dx * k, y + 1, z + dz * k)
                    if g == 1:
                        P(x + dx * k, y + 1, z + dz * k, "sea_lantern")
                        done = True
                        break
                    if g != 0:
                        break
                if done:
                    break
        if not done:
            P(x, y, z, "lantern[hanging=false]")
        used.add(key)
        added += 1
    print("  lights added", added)

# ================================================================= finish: rock, shell, write
ROCK_LS = {"stone": 5, "andesite": 2, "blackstone": 4, "basalt": 3, "smooth_basalt": 2, "tuff": 2, "cobbled_deepslate": 1}
ROCK_ALL = {"stone": 7, "andesite": 4, "tuff": 4, "cobbled_deepslate": 3, "deepslate": 2}


def finalize_rock():
    mask = (G == 1)
    r = rng.random(G.shape, dtype=np.float32)
    for region, weights in (((Zg < -8) & (Yg > 150), ROCK_LS), (None, ROCK_ALL)):
        tot = sum(weights.values())
        acc = 0.0
        m = mask if region is None else (mask & region)
        done = np.zeros(G.shape, bool)
        for name, w in weights.items():
            lo, hi = acc / tot, (acc + w) / tot
            acc += w
            sel = m & (r >= lo) & (r < hi) & ~done
            G[sel] = bid(name)
        mask &= (G == 1)
        mask2 = mask
    left = (G == 1)
    G[left] = bid("stone")


# floor surface decoration in Lava Springs: magma and basalt
for ix, iy, iz in np.argwhere((G == 1) & (np.concatenate([G[:, 1:, :], np.zeros((NX, 1, NZ), np.int16)], axis=1) == 0)).tolist():
    x, y, z = ix + OX, iy + OY, iz + OZ
    if z < -8 and y == 196 and not KEEP[ix, iz]:
        h = hb(x, y, z, 31)
        if h < 0.03:
            G[ix, iy, iz] = bid("magma_block")
        elif h < 0.10:
            G[ix, iy, iz] = bid("basalt")
        elif h < 0.17:
            G[ix, iy, iz] = bid("blackstone")
for k, (x, y, z) in DOORS.items():
    for t in range(0, 4):
        for w in range(-2, 3):
            for h in range(5):
                xx, zz = (x + w, z + t) if k == 3 else (x + t, z + w)
                P(xx, y + h, zz, "air")
finalize_rock()

reach2, STAND2 = bfs([ARRIVAL[:3]])
problems = []
for name, (x, y, z, yaw, note) in NPCS.items():
    if not reach2[x - OX, y - OY, z - OZ]:
        problems.append(("NPC", name, (x, y, z), [PAL[gid(x, yy, z)] for yy in (y - 1, y, y + 1)]))
for (x, y, z) in SOULS:
    if not reach2[x - OX, y - 1 - OY, z - OZ]:
        problems.append(("soul", (x, y, z), [PAL[gid(x, yy, z)] for yy in (y - 2, y - 1, y)]))
for k, (x, y, z) in DOORS.items():
    for t in (0, 1, 2, 3, 6):
        xx, zz = (x, z + t) if k == 3 else (x + t, z)
        if not reach2[xx - OX, y - OY, zz - OZ]:
            problems.append(("door", k, (xx, y, zz)))
reach3, _ = bfs([DOORS[9]])
for k, (x, y, z) in DOORS.items():
    if not reach3[x - OX, y - OY, z - OZ]:
        problems.append(("door9 cannot reach", k))
if os.environ.get("DBG3"):
    for zz in range(108, 116):
        print(zz, [(PAL[gid(100, yy, zz)][10:20], int(reach3[100 - OX, yy - OY, zz - OZ]), int(STAND2[100 - OX, yy - OY, zz - OZ])) for yy in range(146, 152)])
    for zz in range(122, 128):
        print(zz, [(PAL[gid(123, yy, zz)][10:20], int(reach2[123 - OX, yy - OY, zz - OZ]), int(STAND2[123 - OX, yy - OY, zz - OZ])) for yy in range(196, 202)])
if os.environ.get("DBG2"):
    idx = np.argwhere(reach3)
    print("reach3 bbox", idx.min(0) + [OX, OY, OZ], idx.max(0) + [OX, OY, OZ], len(idx))
print("problems:", problems)
# the doorways: 5x5 air at the line, 3 blocks in, solid floor under
for k, (x, y, z) in DOORS.items():
    for t in range(0, 4):
        for w in range(-2, 3):
            for h in range(5):
                xx, zz = (x + w, z + t) if k == 3 else (x + t, z + w)
                if gid(xx, y + h, zz) != 0:
                    problems.append(("door not open", k, (xx, y + h, zz), PAL[gid(xx, y + h, zz)]))
            fx, fz = (x + w, z + t) if k == 3 else (x + t, z + w)
            if is_pass(PAL[gid(fx, y - 1, fz)]) or gid(fx, y - 1, fz) == 0:
                problems.append(("door floor", k, (fx, y - 1, fz)))
mnames = {"gray_wool", "cyan_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine", "light_blue_wool", "polished_diorite"}
mid = [i for i, n in enumerate(PAL) if base(n) in mnames]
mm = np.isin(G, mid)
print("mithril/titanium blocks: Lava Springs", int(mm[:, :, :OZ * -1 - 9].sum()) if False else int(mm[:, :, : (-9 - OZ)].sum()), "Royal Mines", int(mm[:, :, (-8 - OZ):(110 - OZ)].sum()),
      "elsewhere", int(mm[:, :, (110 - OZ):].sum()) + int(mm[:, :, (73 - OZ):(110 - OZ)][:, (183 - OY):, :].sum()))
print("problems after door check:", problems)

# doorway frames check: 5x5 air at the lines
for k, (x, y, z) in DOORS.items():
    cells = [(x + (0 if k == 3 else 0), y + dy, z) for dy in range(5)]
print("door floor blocks:", {k: PAL[gid(x, y - 1, z)] for k, (x, y, z) in DOORS.items()})

nonfull = np.array([False if n == 'ROCK' else not _full(n) for n in PAL])
T = nonfull[G]
keepm = ~T & dil(dil(T))
decor = T & (G != 0)
write = keepm | decor
pos = np.argwhere(write)
vals = G[write]
ST = [dungeon_kit.state(n) if n != "ROCK" else dungeon_kit.state("stone") for n in PAL]
cells = area.piece.cells
for (ix, iy, iz), v in zip(pos.tolist(), vals.tolist()):
    cells[(ix, iy, iz)] = (ST[v], None)
print("cells written", len(cells))


def real_sign(x, y, z, facing, lines):
    lines = (list(lines) + ["", "", "", ""])[:4]
    area.piece.set(x - OX, y - OY, z - OZ, f"minecraft:dark_oak_wall_sign[facing={facing}]",
                   {"id": "minecraft:sign", "front_text": {"messages": lines, "color": "white", "has_glowing_text": 1},
                    "back_text": {"messages": ["", "", "", ""], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


for s in SIGNS:
    real_sign(*s)

# ================================================================= spots, zones, save
area.arrival(*ARRIVAL)
for name, (x, y, z, yaw, note) in NPCS.items():
    area.npc(name, x, y, z, yaw, note)
for s in SOULS:
    area.fairy_soul(*s)

MINE = {"becomes": "minecraft:bedrock", "regrow": 8}
TITAN = {"becomes": "minecraft:bedrock", "regrow": 12}
ORE = {"becomes": "minecraft:bedrock", "regrow": 4}
PUREr = {"becomes": "minecraft:bedrock", "regrow": 30}
MITHRIL = {f"minecraft:{b}": MINE for b in ("gray_wool", "cyan_terracotta", "prismarine", "prismarine_bricks", "dark_prismarine", "light_blue_wool")}
MITHRIL["minecraft:polished_diorite"] = TITAN
LS_BLOCKS = dict(MITHRIL)
RM_BLOCKS = dict(MITHRIL)
for o in ("coal", "iron", "gold", "lapis", "redstone", "emerald", "diamond"):
    RM_BLOCKS[f"minecraft:{o}_ore"] = ORE
    RM_BLOCKS[f"minecraft:{o}_block"] = PUREr

area.zone("Lava Springs", box=((60, 180, -75), (219, 234, -9)), blocks=LS_BLOCKS)
area.zone("Palace Bridge", box=((60, 183, -8), (219, 234, 72)))
area.zone("Royal Mines", box=((60, 100, -8), (219, 182, 109)), blocks=RM_BLOCKS)
area.zone("Royal Palace", box=((60, 183, 73), (219, 234, 174)))
area.zone("Aristocrat Passage", box=((60, 100, 110), (219, 182, 174)))
area.save()
print("arrival", area.arrival_spot)
