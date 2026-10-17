#!/usr/bin/env python3
"""Dwarven Mines, part 1: the Dwarven Village (the big cavern hub), the Dwarven Tavern and The Lift (the arrival, east end).

    python tools/builds/hub_areas/mines_village.py

The box (x -60..130, z -175..-75, y 100..235) is solid rock with the spaces carved out (numpy): a great village cavern with
a rock ceiling, the lift cavern at the east end with its shaft going up out of sight, and three stair tunnels to the
doorways: 1 south to the Gates (x 0, z -76, floor 182), 2 west to the Upper Mines (x -60, z -110, floor 176), 3 south-east to
Lava Springs (x 95, z -76, floor 197). Floors are the floor block's y; players stand one above.
"""
import json
import math
from collections import deque
import numpy as np
from kit import Area, hash01
import dungeon_kit

OX, OY, OZ = -60, 100, -175
NX, NY, NZ = 190, 135, 100
F = 200                                    # the village's floor block; players stand at 201
area = Area("mines_village", (OX, OY, OZ), (NX, NY, NZ), version=1, shell=2,
            dimension="skyrpg_skyblock:dwarven_mines", island="Dwarven Mines")


def idx(x, y, z):
    return x - OX, y - OY, z - OZ


def n2(x, z, seed=0.0, scale=1.0):
    s = seed * 1.7
    return (np.sin(x * 0.083 * scale + s) * np.cos(z * 0.071 * scale + s * 0.6) + 0.6 * np.sin((x + z) * 0.047 * scale + s * 1.3)
            + 0.4 * np.sin(x * 0.19 * scale - z * 0.17 * scale + s * 2.1) + 0.25 * np.cos(x * 0.31 * scale + z * 0.29 * scale - s)) / 2.25


# ---------------------------------------------------------------- the carved space
A = np.zeros((NX, NY, NZ), bool)
STAIR = {}                                  # (x, y, z) -> block string (stairs on the tunnel slopes)
XS = (np.arange(NX) + OX).reshape(NX, 1).astype(float)
ZS = (np.arange(NZ) + OZ).reshape(1, NZ).astype(float)


def carve(x1, y1, z1, x2, y2, z2):
    x1, x2 = sorted((x1, x2))
    y1, y2 = sorted((y1, y2))
    z1, z2 = sorted((z1, z2))
    a, b, c = idx(max(x1, OX), max(y1, OY), max(z1, OZ))
    d, e, f = idx(min(x2, OX + NX - 1), min(y2, OY + NY - 1), min(z2, OZ + NZ - 1))
    A[a:d + 1, b:e + 1, c:f + 1] = True


def cavern(cx, cz, rx, rz, hmax, seed):
    u = np.hypot((XS - cx) / rx, (ZS - cz) / rz) + 0.07 * n2(XS, ZS, seed, 2.0)
    h = np.floor(hmax * np.sqrt(np.clip(1 - u ** 3, 0, 1))) + np.rint(2 * n2(XS, ZS, seed + 3, 3.0))
    h = np.maximum(h, 6).astype(int)
    for ix in range(NX):
        x = ix + OX
        if x < -56 or x > 126:
            continue
        for iz in range(NZ):
            z = iz + OZ
            if z < -171 or z > -79 or u[ix, iz] >= 1:
                continue
            A[ix, F - OY + 1:F - OY + 1 + h[ix, iz], iz] = True


cavern(14, -128, 66, 40, 26, 3)             # the village
cavern(102, -118, 26, 34, 24, 5)            # the lift
carve(68, F + 1, -142, 90, F + 16, -108)    # the wide way between them
carve(-5, F + 1, -165, 5, F + 21, -156)     # the lava alcove
carve(21, F + 1, -157, 43, F + 21, -141)    # headroom over the tavern


def run(points_fn, count, floor_fn, stair_block, facing):
    pass


# doorway 1: bay, a 5-wide stair run east and down 18 blocks, then south to the line (x -2..2, floor 182 at z -78..-76)
carve(-30, F + 1, -100, -20, F + 12, -80)
for x in range(-20, -1):
    f = F - (x + 20)
    carve(x, f + 1, -85, x, f + 5, -81)
    if x > -20:
        for z in range(-85, -80):
            STAIR[(x, f, z)] = "minecraft:stone_brick_stairs[facing=west,half=bottom]"
carve(-2, 183, -85, 2, 187, -76)
# doorway 2: bay at the cavern's west end, a 5-wide stair run south and down 24 blocks, flat, then west to the line
carve(-57, F + 1, -155, -44, F + 13, -147)
for i in range(0, 25):
    z, f = -150 + i, F - i
    carve(-56, f + 1, z, -52, f + 5, z)
    if i > 0:
        for x in range(-56, -51):
            STAIR[(x, f, z)] = "minecraft:stone_brick_stairs[facing=north,half=bottom]"
carve(-56, 177, -126, -52, 181, -108)
carve(-60, 177, -112, -56, 181, -108)
# doorway 3: a few steps down from the lift cavern then south (x 93..97, floor 197 at z -78..-76)
carve(93, F + 1, -88, 97, F + 5, -85)
for z in range(-84, -75):
    f = max(197, F - (z + 85))
    carve(93, f + 1, z, 97, f + 5, z)
    if f > 197:
        for x in range(93, 98):
            STAIR[(x, f, z)] = "minecraft:stone_brick_stairs[facing=north,half=bottom]"
# the lift shaft goes up out of sight
carve(109, F + 20, -112, 115, 230, -104)
# fairy soul nook (behind the barrels beside the lava alcove)
carve(6, F + 1, -165, 9, F + 3, -162)

CEIL = {}
for ix in range(NX):
    for iz in range(NZ):
        ys = np.nonzero(A[ix, :, iz])[0]
        if len(ys):
            CEIL[(ix + OX, iz + OZ)] = int(ys.max()) + OY


# ---------------------------------------------------------------- the rock
SOLID = ~A
for (x, y, z) in STAIR:
    SOLID[idx(x, y, z)] = True


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


AIR = ~SOLID
SHELL = dil(AIR, 3) & SOLID
for s in (slice(0, 3), slice(NX - 3, NX)):
    SHELL[s, :, :] = SOLID[s, :, :]
for s in (slice(0, 3), slice(NZ - 3, NZ)):
    SHELL[:, :, s] = SOLID[:, :, s]
SHELL[:, 0:3, :] = SOLID[:, 0:3, :]
SHELL[:, NY - 3:, :] = SOLID[:, NY - 3:, :]
NEAR = dil(AIR, 1) & SOLID                  # rock touching a cave

PAL, NAMES = {}, []


def bid(block):
    block = block if ":" in block.split("[")[0] else "minecraft:" + block
    if block not in PAL:
        PAL[block] = len(NAMES)
        NAMES.append(block)
    return PAL[block]


rng = np.random.default_rng(11)
M = np.full((NX, NY, NZ), -1, np.int16)


def pick(mask, weights, rr=None):
    rr = rng.random((NX, NY, NZ), dtype=np.float32) if rr is None else rr
    tot = float(sum(weights.values()))
    acc = 0.0
    for name, w in weights.items():
        M[mask & (rr >= acc / tot) & (rr < (acc + w) / tot)] = bid(name)
        acc += w


YY = (np.arange(NY) + OY).reshape(1, NY, 1)
pick(SHELL, {"stone": 34, "andesite": 18, "tuff": 14, "cobbled_deepslate": 14, "deepslate": 14, "cobblestone": 6})
air_above = np.zeros_like(A)
air_above[:, :-1] = A[:, 1:]
floor_mask = SOLID & air_above & SHELL
pick(floor_mask, {"stone": 5, "andesite": 4, "cobblestone": 3, "gravel": 2, "tuff": 2, "cobbled_deepslate": 2})
# lights set into the cave walls
rl = rng.random((NX, NY, NZ), dtype=np.float32)
wall_lit = NEAR & SHELL & ~floor_mask & (YY > F + 2)
M[wall_lit & (rl < 0.010)] = bid("glowstone")
M[wall_lit & (rl >= 0.010) & (rl < 0.016)] = bid("sea_lantern")
M[wall_lit & (rl >= 0.016) & (rl < 0.021)] = bid("shroomlight")
low = NEAR & SHELL & ~floor_mask & (YY <= F + 2) & (YY > 160)
M[low & (rl < 0.018)] = bid("sea_lantern")
for (x, y, z), b in STAIR.items():
    ix, iy, iz = idx(x, y, z)
    SHELL[ix, iy, iz] = True
    M[ix, iy, iz] = bid(b)
# the tunnels: brick-lined sides
tun = np.zeros_like(A)
for (x1, x2, z1, z2, y1, y2) in ((-20, -2, -86, -80, 181, 205), (-57, -51, -126, -108, 176, 182), (-61, -56, -113, -107, 176, 182),
                                 (-2, 2, -86, -76, 181, 188), (93, 97, -85, -76, 196, 203), (-57, -51, -150, -126, 175, 205)):
    a, b, c = idx(x1 - 1, y1, z1 - 1)
    d, e, f = idx(x2 + 1, y2, z2 + 1)
    tun[max(a, 0):d + 1, b:e + 1, max(c, 0):f + 1] = True
brick = NEAR & SHELL & tun & ~floor_mask
pick(brick, {"stone_bricks": 6, "mossy_stone_bricks": 2, "cracked_stone_bricks": 2, "polished_andesite": 1},
     rng.random((NX, NY, NZ), dtype=np.float32))
missing = SHELL & (M < 0)
M[missing] = bid("stone")
print("rock cells", int(SHELL.sum()))

ST = [dungeon_kit.state(n) for n in NAMES]
cells = area.piece.cells
pos = np.argwhere(SHELL)
vals = M[SHELL]
for (ix, iy, iz), v in zip(pos.tolist(), vals.tolist()):
    cells[(ix, iy, iz)] = (ST[v], None)
del SHELL, M, rl, wall_lit, low, brick, tun, NEAR, SOLID, AIR, floor_mask, air_above


# ---------------------------------------------------------------- helpers
def S(x, y, z, b, data=None):
    area.set(x, y, z, b, data)


def fill(x1, y1, z1, x2, y2, z2, b):
    area.fill(x1, y1, z1, x2, y2, z2, b)


def sign(x, y, z, rot, l2, l3="", wood="spruce"):
    area.set(x, y, z, f"minecraft:{wood}_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": ['""', f'"{l2}"', f'"{l3}"', '""'],
                                                     "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def lamp_post(x, y, z, h=3, wall="cobbled_deepslate_wall"):
    fill(x, y, z, x, y + h - 1, z, wall)
    S(x, y + h, z, "minecraft:lantern[hanging=false]")


def barrel(x, y, z):
    S(x, y, z, "minecraft:barrel[facing=up]")


def is_air(x, y, z):
    return area.get(x, y, z) in (None, "minecraft:air")


def carved(x, y, z):
    ix, iy, iz = idx(x, y, z)
    return 0 <= ix < NX and 0 <= iy < NY and 0 <= iz < NZ and bool(A[ix, iy, iz])


RECTS = []                                  # building footprints (x1, z1, x2, z2), kept clear of posts and stalactites
NPC_SPOTS = []


def npc(name, x, z, yaw, note, y=F + 1):
    area.npc(name, x, y, z, yaw=float(yaw), note=note)
    NPC_SPOTS.append((name, x, y, z))


STONE = area.mix({"stone_bricks": 6, "mossy_stone_bricks": 2, "cracked_stone_bricks": 2})


def hall(x0, z0, w, d, door, wh=4, name=None, plank="spruce_planks", trim="spruce_log", pad=1):
    """A dwarven house: stone-brick base, spruce frame and planks, glass-pane windows, a gable roof of spruce stairs
    (ridge along x), a 3-wide arch on the `door` side ('n', 's', 'e', 'w') and a lantern on a chain inside."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    RECTS.append((x0 - 2, z0 - 2, x1 + 2, z1 + 2))
    top = F + wh
    xc, zc = (x0 + x1) // 2, (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, F, z, "minecraft:spruce_planks" if (x + z) % 5 else "minecraft:stripped_spruce_log[axis=x]")
            for y in range(F + 1, top + 1):
                S(x, y, z, "minecraft:air")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            along_x = z in (z0, z1) and not corner
            for y in range(F + 1, top + 1):
                if corner:
                    b = "minecraft:spruce_log[axis=y]"
                elif y == F + 1:
                    b = STONE(x, y, z)
                elif y == top:
                    b = f"minecraft:stripped_{trim}[axis={'x' if along_x else 'z'}]"
                elif y == F + 2 and (x + z) % 3 == 0:
                    b = "minecraft:glass_pane[east=true,west=true]" if along_x else "minecraft:glass_pane[north=true,south=true]"
                elif y == F + 2:
                    b = STONE(x, y, z)
                else:
                    b = "minecraft:" + plank
                S(x, y, z, b)
    # the arch
    if door in "ns":
        dz = z0 if door == "n" else z1
        for x in range(xc - 1, xc + 2):
            for y in range(F + 1, F + 4):
                S(x, y, dz, "minecraft:air")
        outside = dz - 1 if door == "n" else dz + 1
        for x in (xc - 2, xc + 2):
            S(x, F + 1, outside, "minecraft:barrel[facing=up]" if hash01(x, 1, outside) < 0.5 else "minecraft:stone_brick_wall")
            if not is_air(x, F + 1, outside):
                pass
        S(xc - 2, F + 3, dz, "minecraft:lantern[hanging=false]" if False else "minecraft:spruce_log[axis=y]")
        S(xc + 2, F + 3, dz, "minecraft:spruce_log[axis=y]")
        for x in (xc - 2, xc + 2):
            S(x, F + 1, dz, "minecraft:spruce_log[axis=y]")
            S(x, F + 2, dz, "minecraft:spruce_log[axis=y]")
        for x in range(xc - 1, xc + 2):
            S(x, F + 4, dz, "minecraft:spruce_planks")
        for x in (xc - 2, xc + 2):
            S(x, F + 4, outside, "minecraft:lantern[hanging=true]" if False else "minecraft:air")
        dpos = (xc, dz)
    else:
        dx = x0 if door == "w" else x1
        for z in range(zc - 1, zc + 2):
            for y in range(F + 1, F + 4):
                S(dx, y, z, "minecraft:air")
        for z in (zc - 2, zc + 2):
            for y in range(F + 1, F + 4):
                S(dx, y, z, "minecraft:spruce_log[axis=y]")
        dpos = (dx, zc)
    # the gable roof
    r = 0
    k = 0
    base = top + 1
    while True:
        zlo, zhi = z0 - 1 + k, z1 + 1 - k
        if zlo > zhi:
            break
        y = base + k
        for x in range(x0 - 1, x1 + 2):
            if zlo < zhi:
                S(x, y, zlo, "minecraft:spruce_stairs[facing=south,half=bottom]")
                S(x, y, zhi, "minecraft:spruce_stairs[facing=north,half=bottom]")
            else:
                S(x, y, zlo, "minecraft:spruce_planks")
        for z in range(zlo + 1, zhi):
            for x in (x0, x1):
                S(x, y, z, "minecraft:" + plank)
        k += 1
    ridge = base + k - 1
    # chain and lantern inside
    for y in range(top, ridge):
        if y > top:
            S(xc, y, zc, "minecraft:iron_chain[axis=y]")
    S(xc, top - 1, zc, "minecraft:lantern[hanging=true]")
    # corner lanterns outside the door
    return {"x0": x0, "z0": z0, "x1": x1, "z1": z1, "door": dpos, "xc": xc, "zc": zc, "top": top, "ridge": ridge}


def counter(x1, x2, z, y=F + 1, block="spruce_planks", window=True):
    for x in range(x1, x2 + 1):
        S(x, y, z, "minecraft:" + block)
        if window:
            S(x, y + 1, z, "minecraft:iron_bars" if (x - x1) % 3 else "minecraft:air")


def banner_wall(x, y, z, facing, color="red"):
    S(x, y, z, f"minecraft:{color}_wall_banner[facing={facing}]")


# ---------------------------------------------------------------- floors: the square, the roads
cx0, cz0 = 0, -125
PAVE = area.mix({"stone_bricks": 5, "polished_andesite": 3, "cracked_stone_bricks": 1, "deepslate_bricks": 1})
ROAD = area.mix({"stone_bricks": 4, "cobblestone": 3, "gravel": 2, "cracked_stone_bricks": 1, "polished_andesite": 1})


def paint(x, z, block):
    if carved(x, F + 1, z):
        S(x, F, z, block)


for x in range(-14, 15):
    for z in range(-139, -110):
        d = math.hypot(x - cx0, z - cz0)
        if d <= 13.4:
            if d <= 2.5:
                b = "minecraft:chiseled_stone_bricks"
            elif d <= 3.5:
                b = "minecraft:polished_deepslate"
            elif 8.5 < d <= 9.5 or d > 12.4:
                b = "minecraft:deepslate_tiles"
            elif 5.5 < d <= 6.5:
                b = "minecraft:chiseled_stone_bricks" if (x + z) % 2 == 0 else "minecraft:polished_andesite"
            else:
                b = PAVE(x, F, z)
            paint(x, z, b)


def road(points, width=5):
    for (xa, za), (xb, zb) in zip(points, points[1:]):
        n = max(abs(xb - xa), abs(zb - za), 1)
        for i in range(n + 1):
            px, pz = xa + (xb - xa) * i / n, za + (zb - za) * i / n
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if math.hypot(dx, dz) <= width / 2 + 0.2:
                        x, z = int(round(px)) + dx, int(round(pz)) + dz
                        if area.get(x, F, z) is not None and area.get(x, F + 1, z) in (None, "minecraft:air"):
                            paint(x, z, ROAD(x, F, z))


ROADS = [[(12, -125), (60, -125), (95, -118), (96, -100), (96, -84)],
         [(-12, -128), (-34, -135), (-46, -148), (-52, -152)],
         [(0, -113), (-6, -98), (-22, -96), (-25, -86)],
         [(32, -141), (32, -128)],
         [(100, -125), (100, -100)],
         [(96, -100), (112, -100)]]
for pts in ROADS:
    road(pts)

# ---------------------------------------------------------------- the central square
for dx in range(-2, 3):
    for dz in range(-2, 3):
        S(dx, F + 1, cz0 + dz, "minecraft:stone_brick_stairs[facing=north,half=bottom]" if False else "minecraft:stone_bricks")
fill(-1, F + 2, cz0 - 1, 1, F + 2, cz0 + 1, "minecraft:polished_deepslate")
fill(0, F + 3, cz0, 0, F + 8, cz0, "minecraft:chiseled_polished_blackstone")
S(0, F + 9, cz0, "minecraft:glowstone")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    S(dx, F + 9, cz0 + dz, "minecraft:iron_chain[axis=y]" if False else "minecraft:stone_brick_wall")
    S(dx, F + 8, cz0 + dz, "minecraft:lantern[hanging=true]")
for dx, dz in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
    S(dx, F + 1, cz0 + dz, "minecraft:stone_bricks")
    S(dx, F + 2, cz0 + dz, "minecraft:campfire[lit=true]")
for dx, dz in ((-10, -3), (10, 3), (-3, 10), (3, -10)):
    S(dx, F + 1, cz0 + dz, "minecraft:stone_brick_wall")
    S(dx, F + 2, cz0 + dz, "minecraft:stone_brick_wall")
    S(dx, F + 3, cz0 + dz, "minecraft:lantern[hanging=false]")
for ang in range(0, 360, 30):                          # lantern posts round the rim
    a = math.radians(ang + 15)
    lamp_post(int(round(math.cos(a) * 13.4)), F + 1, cz0 + int(round(math.sin(a) * 13.4)), 3, "minecraft:deepslate_brick_wall")
sign(5, F + 1, -118, 0, "Dwarven Village", "Mining II", "dark_oak")

# ---------------------------------------------------------------- the houses (north row, south row, east, decorative)
bank = hall(-30, -146, 11, 9, "s", wh=5, plank="spruce_planks")
for x in range(-30, -19):                                # gold trim on the bank's front
    S(x, F + 5, -138, "minecraft:stripped_spruce_log[axis=x]")
for x in (-30, -20):
    S(x, F + 6, -138, "minecraft:raw_gold_block")
counter(-28, -22, -141, window=True)
for x in range(-29, -20):
    S(x, F + 1, -145, "minecraft:gilded_blackstone" if x % 2 else "minecraft:raw_gold_block")
S(-29, F + 1, -144, "minecraft:barrel[facing=up]")
S(-21, F + 1, -144, "minecraft:barrel[facing=up]")
sign(-25, F + 1, -136, 0, "Bank", "Banker Broadjaw")
npc("Banker Broadjaw", -25, -143, 0, "bank: coins and the vault")

bubu = hall(-14, -146, 9, 9, "s")
counter(-12, -8, -141)
for x in (-13, -12, -11, -10, -9, -7):
    S(x, F + 1, -145, "minecraft:anvil[facing=north]" if x == -10 else "minecraft:barrel[facing=up]")
S(-7, F + 2, -145, "minecraft:lantern[hanging=false]")
S(-13, F + 1, -143, "minecraft:grindstone[face=floor,facing=north]")
sign(-10, F + 1, -136, 0, "Bubu", "Pickaxes")
npc("Bubu", -10, -143, 0, "shop: pickaxes and drills")

bul = hall(4, -146, 9, 9, "s")
counter(6, 10, -141)
for x in (5, 6, 7, 9, 10, 11):
    S(x, F + 1, -145, "minecraft:crafting_table" if x in (6, 10) else "minecraft:hay_block[axis=y]" if x == 5 else "minecraft:barrel[facing=up]")
S(11, F + 1, -143, "minecraft:furnace[facing=south,lit=true]")
sign(8, F + 1, -136, 0, "Bulvar", "Minions")
npc("Bulvar", 8, -143, 0, "shop: minions")

tav = hall(22, -156, 21, 15, "s", wh=6)               # the Dwarven Tavern
tx = 32
for x in range(23, 42):                                # a second, wider doorway and big windows
    pass
for z in (-150, -147):
    for x in (22, 42):
        for y in (F + 2, F + 3):
            S(x, y, z, "minecraft:glass_pane[north=true,south=true]")
for x in (25, 29, 35, 39):
    for y in (F + 2, F + 3):
        S(x, y, -156, "minecraft:glass_pane[east=true,west=true]")
counter(26, 38, -153, block="spruce_planks", window=False)
for x in range(26, 39):
    S(x, F + 2, -153, "minecraft:spruce_slab[type=bottom]" if x % 3 else "minecraft:lantern[hanging=false]")
for x in range(24, 41):
    S(x, F + 1, -155, "minecraft:barrel[facing=south]" if x % 2 else "minecraft:spruce_planks")
    S(x, F + 2, -155, "minecraft:barrel[facing=south]" if x % 3 else "minecraft:lantern[hanging=false]")
    S(x, F + 3, -155, "minecraft:spruce_slab[type=bottom]")
for x in (23, 41):                                     # kegs against the side walls
    for z in (-154, -153, -152):
        S(x, F + 1, z, "minecraft:barrel[facing=up]")
        if z != -153:
            S(x, F + 2, z, "minecraft:barrel[facing=up]")
S(41, F + 1, -145, "minecraft:stone_bricks")           # the fireplace
fill(41, F + 1, -145, 41, F + 3, -145, "minecraft:stone_bricks")
S(40, F + 1, -145, "minecraft:campfire[lit=true]")
for z, a, b in ((-149, 26, 27), (-149, 36, 37), (-146, 29, 30)):
    for x in range(a, b + 1):
        S(x, F + 1, z, "minecraft:spruce_fence")
        S(x, F + 1, z - 1, "minecraft:spruce_fence")
        S(x, F + 2, z, "minecraft:spruce_pressure_plate")
        S(x, F + 2, z - 1, "minecraft:spruce_pressure_plate")
    for x in (a - 1, b + 1):
        for zz in (z, z - 1):
            S(x, F + 1, zz, f"minecraft:spruce_stairs[facing={'east' if x < a else 'west'},half=bottom]")
sign(tx, F + 1, -140, 0, "Dwarven Tavern", "Brynmor")
for x in (28, 36):
    S(x, F + 1, -157, "minecraft:air")
npc("Brynmor", 32, -154, 0, "shop: tavern keeper behind the bar")
npc("Gimley", 25, -148, 270, "tavern: at the table")
npc("Bomin", 28, -148, 90, "tavern: at the table")
npc("Hornum", 35, -148, 270, "tavern: at the table")
npc("Sargwyn", 38, -148, 90, "tavern: at the table")
npc("Tarwen", 28, -145, 270, "tavern: at the table")
banner_wall(32, F + 4, -155, "south", "red")
banner_wall(24, F + 4, -155, "south", "orange")
banner_wall(40, F + 4, -155, "south", "orange")

smith = hall(-12, -108, 11, 9, "n", wh=5)
for x in (-11, -9, -5, -3):
    S(x, F + 1, -101, {-11: "minecraft:blast_furnace[facing=north,lit=true]", -9: "minecraft:anvil[facing=east]",
                      -5: "minecraft:smithing_table", -3: "minecraft:blast_furnace[facing=north,lit=true]"}[x])
S(-7, F + 1, -101, "minecraft:grindstone[face=floor,facing=south]")
S(-4, F + 1, -102, "minecraft:lava_cauldron")
S(-11, F + 1, -105, "minecraft:cauldron")
S(-3, F + 1, -105, "minecraft:anvil[facing=north]")
S(-7, F + 2, -101, "minecraft:lantern[hanging=false]")
sign(-7, F + 1, -110, 8, "Blacksmith", "")
npc("Blacksmith", -7, -104, 180, "blacksmith: repairs and reforges")

rhys = hall(6, -108, 9, 9, "n")
for x in (7, 8, 9, 13):
    barrel(x, F + 1, -101)
S(11, F + 1, -101, "minecraft:crafting_table")
S(13, F + 1, -105, "minecraft:chest[facing=west]")
sign(10, F + 1, -110, 8, "Rhys", "")
npc("Rhys", 10, -104, 180, "mining chatter and quests")

frag = hall(20, -108, 9, 9, "n")
for x in (21, 22, 27):
    S(x, F + 1, -101, "minecraft:amethyst_block")
S(24, F + 1, -101, "minecraft:amethyst_cluster[facing=up]")
S(26, F + 1, -101, "minecraft:lectern[facing=north]")
S(21, F + 1, -105, "minecraft:barrel[facing=up]")
sign(24, F + 1, -110, 8, "Fragilis", "")
npc("Fragilis", 24, -104, 180, "keeper of fragile things")

hut = hall(56, -146, 7, 7, "s", wh=4)
S(59, F + 1, -143, "minecraft:campfire[lit=true]")
S(57, F + 1, -144, "minecraft:hay_block[axis=y]")
S(61, F + 1, -144, "minecraft:barrel[facing=up]")
S(61, F + 1, -142, "minecraft:spruce_stairs[facing=west,half=bottom]")
S(57, F + 1, -142, "minecraft:spruce_stairs[facing=east,half=bottom]")
sign(59, F + 1, -138, 0, "Old Man Garry", "")
npc("Old Man Garry", 59, -144, 0, "old man by the campfire")

for (hx, hz, hw, hd, hd_door) in ((46, -110, 9, 7, "n"), (62, -110, 9, 7, "n"), (46, -142, 7, 7, "s"), (-46, -122, 7, 7, "e")):
    h = hall(hx, hz, hw, hd, hd_door)
    S(h["xc"] - 1, F + 1, h["zc"], "minecraft:barrel[facing=up]")
    S(h["xc"] + 1, F + 1, h["zc"], "minecraft:crafting_table")

# Don Expresso's coffee stall (open, south row east)
for dx in (34, 42):
    for dz in (-108, -101):
        fill(dx, F + 1, dz, dx, F + 4, dz, "minecraft:spruce_log[axis=y]")
for x in range(33, 44):
    for z in range(-109, -99):
        S(x, F + 5, z, "minecraft:spruce_slab[type=bottom]" if (x + z) % 2 else "minecraft:dark_oak_slab[type=bottom]")
RECTS.append((32, -111, 45, -98))
for x in range(35, 42):
    S(x, F + 1, -101, "minecraft:smoker[facing=north,lit=true]" if x in (35, 41) else "minecraft:spruce_planks")
    S(x, F + 2, -101, "minecraft:cauldron" if x in (37, 39) else "minecraft:barrel[facing=north]" if x == 40 else "minecraft:brewing_stand")
S(36, F + 4, -105, "minecraft:lantern[hanging=true]")
S(40, F + 4, -105, "minecraft:lantern[hanging=true]")
sign(38, F + 1, -108, 8, "Don Expresso", "Coffee")
npc("Don Expresso", 38, -104, 180, "coffee stall")

# the Station (Station Master): a rail platform with a roof deck, reached by stairs from the south, with a fairy soul on top
for x in range(-43, -28):
    for z in range(-131, -119):
        S(x, F + 5, z, "minecraft:spruce_planks" if (x + z) % 4 else "minecraft:stripped_spruce_log[axis=x]")
RECTS.append((-45, -133, -27, -112))
for x in (-42, -30):
    for z in (-130, -120):
        fill(x, F + 1, z, x, F + 4, z, "minecraft:spruce_log[axis=y]")
        S(x, F + 4, z + (1 if z == -130 else -1), "minecraft:lantern[hanging=true]")
for x in range(-41, -30):
    S(x, F, -127, "minecraft:polished_andesite")
    S(x, F + 1, -127, "minecraft:rail[shape=east_west]" if x % 5 else "minecraft:powered_rail[shape=east_west,powered=false]")
S(-42, F + 1, -127, "minecraft:stone_brick_wall")
S(-42, F + 2, -127, "minecraft:lantern[hanging=false]")
S(-30, F + 1, -127, "minecraft:stone_brick_wall")
S(-30, F + 2, -127, "minecraft:lantern[hanging=false]")
for z in (-123, -122):
    for x in (-41, -40, -39):
        barrel(x, F + 1, z)
S(-40, F + 2, -123, "minecraft:barrel[facing=up]")
sign(-36, F + 1, -118, 0, "Station", "Station Master")
npc("Station Master", -36, -125, 90, "fast travel: the minecart station")
for i, y in enumerate((F + 4, F + 3, F + 2, F + 1)):    # stairs up to the roof deck at z -119..-116
    z = -119 + i
    for x in (-38, -37, -36):
        S(x, y, z, "minecraft:stone_brick_stairs[facing=north,half=bottom]")
        for yy in range(F + 1, y):
            S(x, yy, z, "minecraft:stone_bricks")
for z in range(-131, -119):
    for x in (-43, -29):
        S(x, F + 6, z, "minecraft:spruce_fence")
for x in range(-43, -28):
    if x not in (-38, -37, -36):
        S(x, F + 6, -131, "minecraft:spruce_fence")
        S(x, F + 6, -120, "minecraft:spruce_fence")
S(-43, F + 7, -131, "minecraft:lantern[hanging=false]")
S(-29, F + 7, -131, "minecraft:lantern[hanging=false]")
area.fairy_soul(-36, F + 6, -126)
S(-36, F + 5, -126, "minecraft:gilded_blackstone")

# ---------------------------------------------------------------- the square's market stalls, barrel stacks, crates
STALLS = [(-12, -112, 5, 3), (8, -112, 5, 3), (14, -134, 3, 5)]
for sx, sz, sw, sd in STALLS:
    for x in (sx, sx + sw - 1):
        for z in (sz, sz + sd - 1):
            fill(x, F + 1, z, x, F + 3, z, "minecraft:spruce_fence")
    for x in range(sx - 1, sx + sw + 1):
        for z in range(sz - 1, sz + sd + 1):
            S(x, F + 4, z, "minecraft:red_wool" if (x + z) % 2 else "minecraft:white_wool")
    for x in range(sx + 1, sx + sw - 1):
        S(x, F + 1, sz + sd // 2, "minecraft:barrel[facing=up]" if x % 2 else "minecraft:spruce_planks")
    S(sx + sw // 2, F + 3, sz + sd // 2, "minecraft:lantern[hanging=true]")
    RECTS.append((sx - 2, sz - 2, sx + sw + 1, sz + sd + 1))
npc("Emissary Eliza", 4, -121, 135, "Emissary: commissions")
sign(4, F + 1, -122, 0, "Emissary", "Commissions", "dark_oak") if False else None
S(5, F + 1, -121, "minecraft:lectern[facing=west]")


def crates(x, z, n=3):
    for i in range(n):
        for (dx, dz, h) in ((0, 0, 2), (1, 0, 1), (0, 1, 1)):
            for y in range(F + 1, F + 1 + h):
                S(x + dx, y, z + dz, "minecraft:barrel[facing=up]" if hash01(x + dx, y, z + dz) < 0.7 else "minecraft:spruce_planks")
        break


for (x, z) in ((-18, -120), (18, -112), (54, -118), (66, -132), (-6, -150), (14, -150), (18, -152), (-16, -134), (50, -150), (-47, -137)):
    crates(x, z)

# ---------------------------------------------------------------- the lava alcove (a glass-fronted lava fall) and soul nook
fill(-4, F + 4, -171, 4, F + 27, -165, "minecraft:polished_blackstone_bricks")
fill(-2, F + 5, -170, 2, F + 25, -167, "minecraft:lava")
fill(-2, F + 5, -166, 2, F + 25, -166, "minecraft:orange_stained_glass")
fill(-3, F + 1, -165, 3, F + 3, -164, "minecraft:polished_blackstone_bricks")
fill(-2, F + 2, -164, 2, F + 2, -164, "minecraft:magma_block")
for x in (-4, 4):
    fill(x, F + 1, -165, x, F + 16, -165, "minecraft:polished_blackstone_bricks")
    for y in range(F + 4, F + 18, 4):
        S(x, y, -164, "minecraft:lantern[hanging=false]")
fill(-3, F + 4, -164, 3, F + 4, -164, "minecraft:polished_blackstone_brick_slab[type=bottom]")
sign(0, F + 1, -160, 8, "The Lava Fall", "", "dark_oak")
for x in range(-4, 5):
    if x not in (-4, 4):
        S(x, F + 1, -157, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]" if abs(x) <= 1 else "minecraft:air")
# nook: barrels hide the way in (the gap is at z -163); the fairy soul sits on a gilded block
fill(6, F + 1, -165, 6, F + 2, -164, "minecraft:barrel[facing=up]")
S(5, F + 1, -162, "minecraft:barrel[facing=up]")
S(5, F + 2, -162, "minecraft:barrel[facing=up]")
S(6, F + 3, -165, "minecraft:barrel[facing=up]")
for x in range(7, 10):
    for z in range(-165, -161):
        S(x, F, z, "minecraft:stone_bricks")
S(9, F + 1, -164, "minecraft:gilded_blackstone")
area.fairy_soul(9, F + 2, -164)
S(8, F + 1, -165, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- The Lift (east end)
LX, LZ = 112, -108
for dx in range(-5, 6):
    for dz in range(-5, 6):
        edge = max(abs(dx), abs(dz)) == 5
        b = ("minecraft:chiseled_polished_blackstone" if (dx + dz) % 2 == 0 else "minecraft:polished_blackstone_bricks") if edge else \
            "minecraft:polished_deepslate" if max(abs(dx), abs(dz)) > 2 else "minecraft:deepslate_tiles"
        S(LX + dx, F, LZ + dz, b)
for dx in range(-3, 4):                                   # the cage frame: pillars, beams, scaffold
    for dz in range(-3, 4):
        if max(abs(dx), abs(dz)) == 3 and abs(dx) in (0, 3) and abs(dz) in (0, 3):
            fill(LX + dx, F + 1, LZ + dz, LX + dx, F + 29, LZ + dz, "minecraft:deepslate_bricks")
for y in (F + 12, F + 20, F + 28):
    for d in range(-3, 4):
        for (px, pz, ax) in ((LX + d, LZ - 3, "x"), (LX + d, LZ + 3, "x"), (LX - 3, LZ + d, "z"), (LX + 3, LZ + d, "z")):
            if abs(d) not in (0, 3) or True:
                S(px, y, pz, f"minecraft:stripped_spruce_log[axis={ax}]")
    S(LX, y - 1, LZ - 3, "minecraft:lantern[hanging=true]")
    S(LX, y - 1, LZ + 3, "minecraft:lantern[hanging=true]")
for dx in range(-2, 3):                                   # the cage on chains
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2:
            gap = dx == -2 and abs(dz) <= 1
            for y in range(F + 1, F + 4):
                S(LX + dx, y, LZ + dz, "minecraft:air" if gap else "minecraft:iron_bars")
        S(LX + dx, F + 4, LZ + dz, "minecraft:polished_blackstone_slab[type=bottom]" if max(abs(dx), abs(dz)) == 2 else
          "minecraft:deepslate_brick_slab[type=bottom]")
S(LX, F + 3, LZ, "minecraft:lantern[hanging=true]")
for (dx, dz) in ((0, 0), (-2, -2), (2, -2), (-2, 2), (2, 2)):
    fill(LX + dx, F + 5, LZ + dz, LX + dx, F + 30, LZ + dz, "minecraft:iron_chain[axis=y]")
for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    S(LX + dx, F + 1, LZ + dz, "minecraft:soul_lantern[hanging=false]") if False else None
sign(LX - 6, F + 1, LZ, 12, "The Lift", "To the Mines", "dark_oak")
npc("Lift Operator (Dwarven Mines)", LX, LZ, 90, "the lift: down to the Deep Caverns")
RECTS.append((LX - 7, LZ - 7, LX + 7, LZ + 7))
# arrival plaza: paving, braziers, pillars, Braum's post
for x in range(94, 109):
    for z in range(-108, -93):
        if carved(x, F + 1, z):
            S(x, F, z, PAVE(x, F, z))
for (x, z) in ((94, -108), (108, -108), (94, -94), (108, -94), (101, -113), (101, -89)):
    fill(x, F + 1, z, x, F + 4, z, "minecraft:deepslate_bricks")
    S(x, F + 5, z, "minecraft:lantern[hanging=false]")
    S(x, F + 1, z, "minecraft:chiseled_stone_bricks")
npc("Emissary Braum", 104, -104, 90, "Emissary: commissions")
S(105, F + 1, -104, "minecraft:lectern[facing=west]")
sign(104, F + 1, -103, 0, "Emissary Braum", "", "dark_oak") if False else None
area.arrival(100, F + 1, -100, yaw=90.0)
area.warp("Dwarven Mines", 100, F + 1, -100, yaw=90.0)
# the launch pad back to the Deep Caverns
DC_LAND = (14, 19, 17, 180.0)                             # the Obsidian Sanctuary landing spot (see deep_caverns.py)
PX, PZ = 106, -92
for dx in range(-3, 4):
    for dz in range(-3, 4):
        S(PX + dx, F, PZ + dz, "minecraft:polished_blackstone_bricks" if max(abs(dx), abs(dz)) == 3 else "minecraft:polished_blackstone")
        for y in range(F + 1, F + 4):
            S(PX + dx, y, PZ + dz, "minecraft:air")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        S(PX + dx, F, PZ + dz, "minecraft:slime_block")
S(PX, F + 1, PZ, "minecraft:heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    S(PX + dx, F + 1, PZ + dz, "minecraft:polished_blackstone_wall")
    S(PX + dx, F + 2, PZ + dz, "minecraft:lantern[hanging=false]")
sign(PX, F + 1, PZ - 3, 8, "Deep Caverns", "Launch pad", "dark_oak")
area.pad("the Deep Caverns", PX - 1, F + 1, PZ - 1, PX + 1, F + 2, PZ + 1,
         destination=("skyrpg_skyblock:deep_caverns", (DC_LAND[0] + 0.5, DC_LAND[1], DC_LAND[2] + 0.5), DC_LAND[3]))
RECTS.append((PX - 4, PZ - 4, PX + 4, PZ + 4))
# the fairy soul tower: a platform on pillars with stairs up from the east
TX0, TZ0 = 100, -139
for x in range(TX0, TX0 + 7):
    for z in range(TZ0, TZ0 + 7):
        S(x, F + 9, z, "minecraft:spruce_planks" if (x + z) % 3 else "minecraft:stripped_spruce_log[axis=x]")
for (x, z) in ((TX0, TZ0), (TX0 + 6, TZ0), (TX0, TZ0 + 6), (TX0 + 6, TZ0 + 6)):
    fill(x, F + 1, z, x, F + 8, z, "minecraft:stone_bricks")
    S(x, F + 10, z, "minecraft:lantern[hanging=false]")
for i in range(8):
    x, y = TX0 + 7 + i, F + 8 - i
    for z in range(TZ0 + 2, TZ0 + 5):
        S(x, y, z, "minecraft:stone_brick_stairs[facing=west,half=bottom]")
        for yy in range(F + 1, y):
            S(x, yy, z, "minecraft:stone_bricks")
    for z in (TZ0 + 1, TZ0 + 5):
        S(x, y + 1, z, "minecraft:stone_brick_wall")
for x in range(TX0, TX0 + 7):
    for z in (TZ0, TZ0 + 6):
        S(x, F + 10, z, "minecraft:stone_brick_wall") if (x not in (TX0, TX0 + 6)) else None
for z in range(TZ0 + 1, TZ0 + 6):
    S(TX0, F + 10, z, "minecraft:stone_brick_wall")
for x in range(TX0 + 1, TX0 + 6):
    S(x, F + 10, TZ0 + 6, "minecraft:stone_brick_wall")
    S(x, F + 10, TZ0, "minecraft:stone_brick_wall")
S(TX0 + 3, F + 9, TZ0 + 3, "minecraft:gilded_blackstone")
area.fairy_soul(TX0 + 3, F + 10, TZ0 + 3)
RECTS.append((TX0 - 1, TZ0 - 1, TX0 + 16, TZ0 + 8))
# lift cavern dressing: crates, rails, signs
for (x, z) in ((118, -130), (120, -96), (86, -130), (118, -146), (88, -96)):
    crates(x, z)
for x in range(112, 124):
    if carved(x, F + 1, -125):
        S(x, F + 1, -125, "minecraft:rail[shape=east_west]")
S(124, F + 1, -125, "minecraft:stone_brick_wall")
S(124, F + 2, -125, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- lamp posts along the roads, tunnels lit
RECTS.append((97, -103, 103, -97))
posts = 0
for pts in ROADS:
    for (xa, za), (xb, zb) in zip(pts, pts[1:]):
        n = max(abs(xb - xa), abs(zb - za), 1)
        L = math.hypot(xb - xa, zb - za)
        nx_, nz_ = -(zb - za) / L, (xb - xa) / L
        for i in range(0, n + 1, 8):
            for sgn in (1, -1):
                x = int(round(xa + (xb - xa) * i / n + nx_ * 4 * sgn))
                z = int(round(za + (zb - za) * i / n + nz_ * 4 * sgn))
                if (i // 8 + (sgn > 0)) % 2:
                    continue
                if carved(x, F + 1, z) and all(is_air(x, y, z) for y in range(F + 1, F + 5)) and \
                        area.get(x, F, z) not in (None, "minecraft:air") and \
                        not any(a <= x <= c and b <= z <= d for a, b, c, d in RECTS):
                    lamp_post(x, F + 1, z)
                    posts += 1
print("lamp posts", posts)


def tunnel_lights():
    for x in range(-18, -2, 4):                           # doorway 1 run
        f = F - (x + 20)
        S(x, f + 4, -81, "minecraft:lantern[hanging=true]") if False else None
        for z in (-85, -81):
            S(x, f + 3, z, "minecraft:stone_brick_wall")
            S(x, f + 4, z, "minecraft:lantern[hanging=false]")
    for z in range(-84, -77, 3):
        for x in (-2, 2):
            S(x, 183, z, "minecraft:stone_brick_wall")
            S(x, 184, z, "minecraft:lantern[hanging=false]")
    for i in range(2, 25, 4):                             # doorway 2 run
        z, f = -150 + i, F - i
        for x in (-56, -52):
            S(x, f + 1, z, "minecraft:stone_brick_wall")
            S(x, f + 2, z, "minecraft:lantern[hanging=false]")
    for z in range(-124, -108, 4):
        for x in (-56, -52):
            S(x, 177, z, "minecraft:stone_brick_wall")
            S(x, 178, z, "minecraft:lantern[hanging=false]")
    for x in (-59, -57):
        for z in (-112, -108):
            S(x, 177, z, "minecraft:stone_brick_wall")
            S(x, 178, z, "minecraft:lantern[hanging=false]")
    for z in range(-84, -77, 3):                          # doorway 3
        f = max(197, F - (z + 85))
        for x in (93, 97):
            S(x, f + 1, z, "minecraft:stone_brick_wall")
            S(x, f + 2, z, "minecraft:lantern[hanging=false]")


tunnel_lights()
# arches at the three doorways
for (x, y, z, axis) in ((0, 182, -76, "x"), (-60, 176, -110, "z"), (95, 197, -76, "x")):
    pass

# ---------------------------------------------------------------- the ceiling: chains and lanterns, glowstone, stalactites
nl = 0
for x in range(-56, 127):
    for z in range(-171, -78):
        if (x, z) not in CEIL:
            continue
        c = CEIL[(x, z)]
        if c < F + 8:
            continue
        r1, r2, r3 = hash01(x, 91, z, 1), hash01(x, 92, z, 2), hash01(x, 93, z, 3)
        under_roof = any(a <= x <= cc and b <= z <= d for a, b, cc, d in RECTS)
        if not carved(x, c, z) or not is_air(x, c, z):
            continue
        if r1 < 0.0045 and not under_roof:                    # a lantern on a long chain
            n = 3 + int(r3 * 5)
            for i in range(n):
                S(x, c - i, z, "minecraft:iron_chain[axis=y]")
            S(x, c - n, z, "minecraft:lantern[hanging=true]")
            nl += 1
        elif r2 < 0.004:                                       # a glowstone cluster
            S(x, c, z, "minecraft:glowstone")
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if carved(x + dx, c, z + dz) and r3 < 0.5:
                    S(x + dx, c, z + dz, "minecraft:glowstone" if r3 < 0.25 else "minecraft:shroomlight")
        elif r2 < 0.03 and not under_roof:
            n = 1 + int(r3 * 3)
            for i in range(n):
                S(x, c - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" +
                  ("tip" if i == n - 1 else "frustum") + "]")
print("ceiling lanterns", nl)
S(0, F + 11, cz0, "minecraft:lantern[hanging=true]") if False else None

# ---------------------------------------------------------------- zones
area.zone("Dwarven Village", box=((-60, 100, -175), (85, 235, -75)))
area.zone("The Lift", box=((85, 100, -175), (130, 235, -75)))
area.spots["notes"].append("floors are the block y; players stand one above (village 200 -> 201). Doorways: 1 (0,-76) floor 182, "
                           "2 (-60,-110) floor 176, 3 (95,-76) floor 197")
area.spots["notes"].append(f"pad back to the Deep Caverns lands at {DC_LAND}")


# ---------------------------------------------------------------- walkability check (arrival -> doorways, NPCs, fairy souls)
OKP = ("air", "lantern", "chain", "sign", "rail", "plate", "torch", "carpet", "button", "banner", "campfire", "pot", "flower", "fungus")


def pas(x, y, z):
    b = area.get(x, y, z)
    if b is None:
        return carved(x, y, z)
    n = b.split(":")[1].split("[")[0]
    return n == "air" or any(n.endswith(s) for s in OKP) and n != "iron_chain_x"


def sol(x, y, z):
    return not pas(x, y, z)


def standable(x, y, z):
    return pas(x, y, z) and pas(x, y + 1, z) and sol(x, y - 1, z)


start = (100, F + 1, -100)
assert standable(*start)
seen = {start}
dq = deque([start])
while dq:
    x, y, z = dq.popleft()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for dy in (1, 0, -1):
            n = (x + dx, y + dy, z + dz)
            if n in seen or not standable(*n):
                continue
            if dy == 1 and not pas(x, y + 2, z):
                continue
            seen.add(n)
            dq.append(n)
print("reachable standing cells", len(seen))
targets = {"door1": (0, 183, -76), "door2": (-60, 177, -110), "door3": (95, 198, -76)}
for name, p in targets.items():
    for dx in (-2, 0, 2) if name != "door2" else (0,):
        q = (p[0] + (dx if name != "door2" else 0), p[1], p[2] + (dx if name == "door2" else 0))
        print(name, q, "REACHABLE" if q in seen else "UNREACHABLE")
bad = [(n, x, y, z) for (n, x, y, z) in NPC_SPOTS if (x, y, z) not in seen]
print("npc spots not reachable on foot:", bad)
print("fairy souls:", [(s, tuple(s) in seen) for s in area.spots["fairy_souls"]])

# clearance: every roof under the ceiling
for r in RECTS:
    pass
for name, h in (("bank", bank), ("tavern", tav), ("smith", smith)):
    c = min(CEIL.get((x, z), 0) for x in range(h["x0"], h["x1"] + 1, 2) for z in range(h["z0"], h["z1"] + 1, 2))
    print(name, "ridge", h["ridge"], "ceiling min", c)
area.save()
for z_ in area.zones:
    print(z_["name"], z_["from"], z_["to"])
