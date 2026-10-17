#!/usr/bin/env python3
"""The Deep Caverns (Hypixel's third Mining island, Mining V): a tall floating mountain of deepslate, tuff and andesite
with six cave layers stacked inside it, joined by wide spiral ramps and a central lift shaft: Gunpowder Mines, Lapis
Quarry, Pigmen's Den, Slimehill, Diamond Reserve and the Obsidian Sanctuary. The way in is a stair tunnel at the top.

    python tools/builds/hub_areas/deep_caverns.py

The mountain is computed with numpy (a solid mask, the carved caves, the materials), then only the visible shell (3 blocks
under any air) is written into the build; dressing (rails, TNT crates, shrine, NPC spots...) is placed afterwards.
"""
import math
import numpy as np
from kit import Area, hash01
import dungeon_kit
from kit import Area, hash01

OX, OY, OZ = -110, 10, -110
NX, NY, NZ = 220, 190, 220
area = Area("deep_caverns", (OX, OY, OZ), (NX, NY, NZ), version=1, dimension="skyrpg_skyblock:deep_caverns",
            island="Deep Caverns", shell=3)

TOP = 185                          # the plateau's surface; players stand at 186
LAYERS = [  # name, floor, cx, cz, rx, rz, height
    ("Gunpowder Mines", 168, 0, 0, 44, 40, 13),
    ("Lapis Quarry", 138, 4, -3, 42, 38, 14),
    ("Pigmen's Den", 108, -4, 3, 41, 41, 15),
    ("Slimehill", 78, 3, 4, 44, 38, 16),
    ("Diamond Reserve", 48, -3, -3, 40, 40, 16),
    ("Obsidian Sanctuary", 18, 0, 0, 34, 34, 15),
]
FL = [l[1] for l in LAYERS]
RAMP_R, RAMP_STEP, RAMP_DROP = 27.0, 1.3, 30
rng = np.random.default_rng(7)

# ---------------------------------------------------------------- grids
X = (np.arange(NX) + OX).reshape(NX, 1, 1).astype(np.float64)
Y = (np.arange(NY) + OY).reshape(1, NY, 1).astype(np.float64)
Z = (np.arange(NZ) + OZ).reshape(1, 1, NZ).astype(np.float64)
R2 = np.hypot(X, Z)                               # (NX,1,NZ)
TH = np.arctan2(Z, X)


def n2(x, z, seed=0.0, scale=1.0):
    s = seed * 1.7
    return (np.sin(x * 0.083 * scale + s) * np.cos(z * 0.071 * scale + s * 0.6) + 0.6 * np.sin((x + z) * 0.047 * scale + s * 1.3)
            + 0.4 * np.sin(x * 0.19 * scale - z * 0.17 * scale + s * 2.1) + 0.25 * np.cos(x * 0.31 * scale + z * 0.29 * scale - s)) / 2.25


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def idx(x, y, z):
    return x - OX, y - OY, z - OZ


# ---------------------------------------------------------------- the rock mass
wob = 3.5 * np.sin(3 * TH + 1.0) + 2.5 * np.sin(5 * TH + 0.04 * Y) + 2.0 * np.sin(7 * TH - 0.03 * Y + 2.0) + 2.0 * n2(X, Z, 3, 1.5)
base = 66 + 14 * smooth((Y - 34) / 80.0) - 7 * smooth((Y - 172) / 13.0)
rout = base + wob
under = 12 + 22 * np.clip((R2 - 34) / 30.0, 0, 1) ** 1.4 + 2.5 * (n2(X, Z, 5, 2.2) * 0.5 + 0.5)
crag = np.rint(10 * smooth((R2 - 64) / 8.0) * (0.5 + 0.5 * n2(X, Z, 9, 1.8))).astype(np.int32)
htop = TOP + crag                                  # (NX,1,NZ)
S = (Y >= under) & (R2 <= rout) & (Y <= htop)
del wob, base, rout, under
print("mass cells", int(S.sum()))

# ---------------------------------------------------------------- the carved space
A = np.zeros((NX, NY, NZ), bool)          # every carved cell (rooms, ramps, shaft, entrance, pockets)
A_room = np.zeros_like(A)
SHAFT = np.zeros_like(A)
ENTR = np.zeros_like(A)
STAIR = {}                                # (x, y, z) -> block string (stairs, ledges, walls placed after)
SUPPORT = set()
RAMP_COLS = [set() for _ in range(6)]


def cave_u(k, x, z):
    _, F, cx, cz, rx, rz, H = LAYERS[k]
    return np.hypot((x - cx) / rx, (z - cz) / rz) + 0.12 * n2(x, z, 11 + k, 2.0)


def cave_h(k, x, z):
    u = cave_u(k, x, z)
    h = np.maximum(3, np.floor(LAYERS[k][6] * np.sqrt(np.maximum(0, 1 - u ** 3)))).astype(np.int32)
    return np.where(u < 1, h, 0)


for k, (name, F, cx, cz, rx, rz, H) in enumerate(LAYERS):
    h = cave_h(k, X, Z)                                           # (NX,1,NZ)
    room = (Y > F) & (Y <= F + h)
    A_room |= room
    A |= room

# the lift shaft between the layers (5x5 at the middle)
for k in range(5):
    x1, y1, z1 = idx(-2, FL[k + 1] + 1, -2)
    ys = FL[k] - 1 - (FL[k + 1] + 1) + 1
    SHAFT[x1:x1 + 5, y1:y1 + ys, z1:z1 + 5] = True
A |= SHAFT

# the spiral ramps: layer k -> k+1
a_start = []
a = -2.0
for k in range(5):
    a_start.append(a)
    a = a + RAMP_DROP * RAMP_STEP / RAMP_R + math.pi
span = RAMP_DROP * RAMP_STEP / RAMP_R + 0.02
for k in range(5):
    F0 = FL[k]
    ca, sa = None, None
    for x in range(-40, 41):
        for z in range(-40, 41):
            rr = math.hypot(x, z)
            if abs(rr - RAMP_R) > 3.6:
                continue
            rel = (math.atan2(z, x) - a_start[k]) % (2 * math.pi)
            if rel > span:
                continue
            t = rel * RAMP_R / RAMP_STEP
            if t >= RAMP_DROP:
                continue
            ys = F0 - 1 - int(t)
            RAMP_COLS[k].add((x, z))
            ix, iy0, iz = idx(x, ys + 1, z)
            A[ix, iy0:iy0 + 5, iz] = True
            ang = math.atan2(z, x)
            ux, uz = math.sin(ang), -math.cos(ang)                # uphill direction
            if abs(ux) >= abs(uz):
                facing = "east" if ux > 0 else "west"
            else:
                facing = "south" if uz > 0 else "north"
            side = abs(rr - RAMP_R)
            if side <= 2.5:
                STAIR[(x, ys, z)] = f"minecraft:cobbled_deepslate_stairs[facing={facing},half=bottom]" if (x + z) % 7 else \
                    f"minecraft:deepslate_brick_stairs[facing={facing},half=bottom]"
            else:
                STAIR[(x, ys, z)] = "minecraft:polished_deepslate"
                STAIR[(x, ys + 1, z)] = "minecraft:cobbled_deepslate_wall"
            SUPPORT.add((x, ys - 1, z))

# the entrance stairs from the plateau down into the Gunpowder Mines
for i in range(17):
    ys, z = TOP - i, 58 - i
    for x in range(-3, 4):
        ix, iy0, iz = idx(x, ys + 1, z)
        ENTR[ix, iy0:iy0 + 6, iz] = True
        STAIR[(x, ys, z)] = "minecraft:stone_brick_stairs[facing=south,half=bottom]"
        SUPPORT.add((x, ys - 1, z))
for z in range(36, 43):
    for x in range(-3, 4):
        ix, iy0, iz = idx(x, 169, z)
        ENTR[ix, iy0:iy0 + 5, iz] = True
A |= ENTR


# pockets (Fairy Souls): sealed 3x3x3 rooms in the rock just beyond a cave wall; two blocks of stone to mine through
TUNM = np.zeros_like(A)
PASSAGES, COBWEBS = [], []


def pocket(k, ang):
    name, F, cx, cz, rx, rz, H = LAYERS[k]
    for d in np.arange(0.8, 1.6, 0.01):
        px, pz = int(round(cx + math.cos(ang) * rx * d)), int(round(cz + math.sin(ang) * rz * d))
        if cave_u(k, px, pz) > 1.0 + 3.6 / rx:
            break
    ix, iy, iz = idx(px, F + 1, pz)
    A[ix - 1:ix + 2, iy:iy + 3, iz - 1:iz + 2] = True
    TUNM[ix - 1:ix + 2, iy:iy + 3, iz - 1:iz + 2] = True
    # a hidden crawl passage (1 wide, 2 tall) from the cavern floor to the pocket, along the line to the cave centre
    dx, dz = cx - px, cz - pz
    n = int(math.hypot(dx, dz) * 2)
    cells_t = []
    for i in range(n + 1):
        x, z = int(round(px + dx * i / n)), int(round(pz + dz * i / n))
        if cave_u(k, x, z) < 0.97 and not any(0 <= abs(x - tx) + abs(z - tz) <= 0 for tx, tz in []):
            break
        if (x, z) not in cells_t:
            if cells_t and abs(x - cells_t[-1][0]) + abs(z - cells_t[-1][1]) > 1:
                cells_t.append((x, cells_t[-1][1]))
            cells_t.append((x, z))
    for (x, z) in cells_t:
        i2, j2, k2 = idx(x, F + 1, z)
        A[i2, j2:j2 + 2, k2] = True
        TUNM[i2, j2:j2 + 2, k2] = True
    mouth = cells_t[-1]
    PASSAGES.append((k, cells_t))
    COBWEBS.extend([(mouth[0], F + 1, mouth[1]), (mouth[0], F + 2, mouth[1])])
    return px, F + 1, pz


POCKETS = {1: pocket(1, 2.2), 2: pocket(2, -0.6), 4: pocket(4, 0.5), 5: pocket(5, 3.5)}

# every Fairy Soul must be reachable on foot: flood the 2-high walkable cells from the soul to the cave floor
for k_, (px, py, pz) in POCKETS.items():
    F_ = FL[k_]
    seen, todo, ok = {(px, pz)}, [(px, pz)], False
    while todo:
        x, z = todo.pop()
        if cave_u(k_, x, z) < 0.6:
            ok = True
            break
        for ddx, ddz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + ddx, z + ddz)
            if q in seen:
                continue
            i2, j2, k2 = idx(q[0], F_ + 1, q[1])
            if A[i2, j2, k2] and A[i2, j2 + 1, k2]:
                seen.add(q)
                todo.append(q)
    i2, j2, k2 = idx(px, F_ + 1, pz)
    assert ok and A[i2, j2, k2] and A[i2, j2 + 1, k2], f"soul {k_} unreachable"
    print("soul", (px, F_ + 1, pz), "reachable; passage", PASSAGES[[p[0] for p in PASSAGES].index(k_)][1][::-1][:1], "to",
          PASSAGES[[p[0] for p in PASSAGES].index(k_)][1][0])

# ---------------------------------------------------------------- shell + materials
SOLID = (S & ~A)
for (x, y, z), b in STAIR.items():
    ix, iy, iz = idx(x, y, z)
    SOLID[ix, iy, iz] = True
for (x, y, z) in SUPPORT:
    y0 = y
    while y0 > OY and not SOLID[idx(x, y0, z)[0], y0 - OY, idx(x, y0, z)[2]]:
        SOLID[idx(x, y0, z)[0], y0 - OY, idx(x, y0, z)[2]] = True
        STAIR.setdefault((x, y0, z), "minecraft:cobbled_deepslate")
        y0 -= 1
STAIRMASK = np.zeros_like(A)
for (x, y, z) in STAIR:
    ix, iy, iz = idx(x, y, z)
    STAIRMASK[ix, iy, iz] = True


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


AIRALL = ~SOLID
OUTSIDE = ~dil(S, 0)
SHELL = dil(AIRALL, 3) & SOLID
WALL = dil(A & ~ENTR, 2) & SOLID & ~STAIRMASK & ~dil(TUNM, 1)          # mineable stone zone around the caves
SHW = dil(SHAFT, 1) & SOLID & ~STAIRMASK
EW = dil(ENTR, 1) & SOLID & ~STAIRMASK
OUT_AIR = ~(S | A | STAIRMASK)
SHELL &= (dil(AIRALL, 2) | dil(A, 3))
print("shell cells", int(SHELL.sum()))

PAL = {}
NAMES = []


def bid(block):
    block = block if ":" in block.split("[")[0] else "minecraft:" + block
    if block not in PAL:
        PAL[block] = len(NAMES)
        NAMES.append(block)
    return PAL[block]


M = np.full((NX, NY, NZ), -1, np.int16)
r0 = rng.random((NX, NY, NZ), dtype=np.float32)


def pick(mask, weights, rr=None):
    """Fills M where mask with blocks by weights, using random field rr."""
    rr = r0 if rr is None else rr
    tot = float(sum(weights.values()))
    acc = 0.0
    for name, w in weights.items():
        lo, hi = acc / tot, (acc + w) / tot
        M[mask & (rr >= lo) & (rr < hi)] = bid(name)
        acc += w


# outer rock
outer = SHELL & ~WALL & ~SHW & ~EW & ~STAIRMASK
pick(outer, {"deepslate": 34, "tuff": 26, "andesite": 22, "cobbled_deepslate": 16, "calcite": 2})
surf_above_air = SOLID & ~np.roll(SOLID, -1, axis=1)
topface = outer & surf_above_air & (Y >= TOP - 1) & ~A_room
rt = rng.random((NX, NY, NZ), dtype=np.float32)
pick(topface, {"tuff": 30, "andesite": 25, "cobbled_deepslate": 25, "gravel": 15, "coarse_dirt": 5}, rt)

# layer index per y
layer_of_y = np.zeros(NY, np.int8)
for yy in range(NY):
    yv = yy + OY
    best = 5
    for k in range(6):
        if yv >= FL[k] - 13:
            best = k
            break
    layer_of_y[yy] = best
LY = layer_of_y.reshape(1, NY, 1)

# cave walls: stone with each layer's ores, plus the layer's own non-mineable rock
ORES = {0: {"coal_ore": 5, "iron_ore": 3, "gold_ore": 2}, 1: {"lapis_ore": 1}, 2: {"redstone_ore": 1}, 3: {"emerald_ore": 1},
        4: {"diamond_ore": 1}, 5: {"diamond_ore": 1}}
ORE_P = {0: 0.30, 1: 0.30, 2: 0.30, 3: 0.20, 4: 0.20, 5: 0.14}
DECOR = {0: {"cobblestone": 3, "andesite": 3, "gravel": 1}, 1: {"andesite": 3, "tuff": 2, "calcite": 1},
         2: {"netherrack": 5, "red_terracotta": 2, "blackstone": 2}, 3: {"mossy_cobblestone": 4, "moss_block": 3, "green_terracotta": 1},
         4: {"deepslate": 3, "tuff": 2, "andesite": 1}, 5: {"blackstone": 4, "deepslate": 3, "polished_blackstone": 1}}
cl = np.repeat(np.repeat(np.repeat(rng.random((NX // 3 + 1, NY // 3 + 1, NZ // 3 + 1), dtype=np.float32), 3, 0), 3, 1), 3, 2)[:NX, :NY, :NZ]
ro = rng.random((NX, NY, NZ), dtype=np.float32)
rd = rng.random((NX, NY, NZ), dtype=np.float32)
for k in range(6):
    lm = WALL & SHELL & (LY == k)
    M[lm] = bid("stone")
    d = lm & (rd < 0.22)
    pick(d, DECOR[k], rng.random((NX, NY, NZ), dtype=np.float32))
    p_cluster = 0.55
    ore = lm & (((cl < p_cluster) & (ro < ORE_P[k])) | (ro < 0.025))
    pick(ore, ORES[k], rng.random((NX, NY, NZ), dtype=np.float32))
# the shaft walls and the entrance tunnel
shaft_r = rng.random((NX, NY, NZ), dtype=np.float32)
pick(SHELL & SHW, {"deepslate_bricks": 7, "cracked_deepslate_bricks": 2, "polished_deepslate": 2}, shaft_r)
pick(SHELL & EW & ~SHW, {"stone_bricks": 6, "mossy_stone_bricks": 2, "cracked_stone_bricks": 2, "polished_andesite": 1}, shaft_r)

# floors of the rooms
FLOORS = {0: {"gravel": 3, "cobblestone": 3, "coarse_dirt": 2, "andesite": 2},
          1: {"andesite": 4, "tuff": 3, "polished_andesite": 2, "calcite": 1, "lapis_block": 1},
          2: {"netherrack": 5, "red_terracotta": 3, "nether_bricks": 1, "red_nether_bricks": 1, "blackstone": 1},
          3: {"moss_block": 6, "mossy_cobblestone": 2, "lime_terracotta": 2, "green_terracotta": 1},
          4: {"deepslate_tiles": 3, "cobbled_deepslate": 3, "polished_deepslate": 2, "tuff": 2},
          5: {"blackstone": 4, "polished_blackstone": 3, "deepslate_tiles": 2, "crying_obsidian": 1}}
air_above = np.zeros_like(A_room)
air_above[:, :-1] = A_room[:, 1:]
for k in range(6):
    fm = SHELL & WALL & air_above & (LY == k) & (Y == FL[k])
    pick(fm, FLOORS[k], rng.random((NX, NY, NZ), dtype=np.float32))
# remaining -1 shell cells (stairs etc.) are filled below
for (x, y, z), b in STAIR.items():
    ix, iy, iz = idx(x, y, z)
    if 0 <= ix < NX and 0 <= iy < NY and 0 <= iz < NZ:
        SHELL[ix, iy, iz] = True
        M[ix, iy, iz] = bid(b)
missing = SHELL & (M < 0)
M[missing] = bid("deepslate")
print("missing filled", int(missing.sum()))

# ---------------------------------------------------------------- write the shell into the build
ST = [dungeon_kit.state(n) for n in NAMES]
cells = area.piece.cells
pos = np.argwhere(SHELL)
vals = M[SHELL]
for (ix, iy, iz), v in zip(pos.tolist(), vals.tolist()):
    cells[(ix, iy, iz)] = (ST[v], None)
print("cells written", len(cells))
del S, A, SOLID, SHELL, WALL, M, r0, rt, ro, rd, cl, AIRALL, SHAFT, ENTR, STAIRMASK, SHW, EW, shaft_r
hcol_cache = {}
for (x, y, z) in COBWEBS:
    area.set(x, y, z, 'minecraft:cobweb')


def blocked(k, x, z):
    """Near a ramp that starts or ends on layer k, or the lift dais."""
    if abs(x) <= 6 and abs(z) <= 6:
        return True
    for kk in (k - 1, k):
        if 0 <= kk < 5:
            for dx in (-3, 0, 3):
                for dz in (-3, 0, 3):
                    if (x + dx, z + dz) in RAMP_COLS[kk]:
                        return True
    return False


def free(k, x, z, lim=0.8):
    return bool(cave_u(k, x, z) < lim) and not blocked(k, x, z)


def hcol(k, x, z):
    return int(cave_h(k, np.array([[[x]]], float).reshape(1, 1, 1), np.array([[[z]]], float).reshape(1, 1, 1)).item())


def S_(x, y, z, b):
    area.set(x, y, z, b)


def sign(x, y, z, rot, l2, l3, wood="spruce"):
    area.set(x, y, z, f"minecraft:{wood}_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": ['""', f'"{l2}"', f'"{l3}"', '""'],
                                                     "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def post(x, y, z, h=3, wall="cobbled_deepslate_wall", lamp="lantern[hanging=false]"):
    area.fill(x, y, z, x, y + h - 1, z, wall)
    area.set(x, y + h, z, lamp)


def spots(k, count, seed, lim=0.78, spacing=0):
    out, tries = [], 0
    while len(out) < count and tries < count * 60:
        tries += 1
        x = int(-45 + 90 * hash01(tries, k, 1, seed))
        z = int(-45 + 90 * hash01(tries, k, 2, seed))
        if free(k, x, z, lim) and all(math.hypot(x - a, z - b) >= spacing for a, b in out):
            out.append((x, z))
    return out


# ---------------------------------------------------------------- the lift: dais, cage and chains on every layer
for k, (name, F, cx, cz, rx, rz, H) in enumerate(LAYERS):
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            edge = max(abs(dx), abs(dz)) == 3
            area.set(dx, F, dz, "minecraft:chiseled_polished_blackstone" if (edge and (dx + dz) % 2 == 0) else
                     "minecraft:polished_blackstone_bricks" if edge else "minecraft:polished_deepslate")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                gap = dz == 2 and abs(dx) <= 1
                corner = abs(dx) == 2 and abs(dz) == 2
                for y in range(F + 1, F + 4):
                    area.set(dx, y, dz, "minecraft:air" if gap else ("minecraft:polished_blackstone_wall" if corner else "minecraft:iron_bars"))
                area.set(dx, F + 4, dz, "minecraft:polished_blackstone_slab[type=bottom]")
    area.fill(-1, F + 4, -1, 1, F + 4, 1, "minecraft:polished_blackstone_slab[type=bottom]")
    area.set(0, F + 3, 0, "minecraft:lantern[hanging=true]")
    # chains up to the ceiling (or, between layers, up the shaft to the platform above)
    top = (FL[k - 1] - 1) if k > 0 else F + hcol(k, 0, 0)
    for (dx, dz) in ((0, 0), (-2, -2), (2, -2), (-2, 2), (2, 2)):
        if (dx, dz) == (0, 0):
            area.fill(0, F + 5, 0, 0, top, 0, "minecraft:chain[axis=y]")
        else:
            area.fill(dx, F + 5, dz, dx, F + 8, dz, "minecraft:chain[axis=y]")
    if k > 0:
        # a hanging cage frame where the shaft meets the platform above
        pass
    for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        area.set(dx, F + 1, dz, "minecraft:soul_lantern[hanging=false]")
    name_l2 = "Lift Operator" if k == 0 else f"Lift Operator ({name})"
    area.npc(name_l2, 0, F + 1, 0, yaw=0.0, note=f"the lift on the {name} layer")
    sign(0, F + 1, 3, 0, "Lift", name)

# the shaft's frame: bars at every layer joint so it reads as a lift shaft
for k in range(5):
    for y in range(FL[k + 1] + 20, FL[k] - 1, 6):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if max(abs(dx), abs(dz)) == 2 and area.get(dx, y, dz) in (None, "minecraft:air") and hash01(dx, y, dz, 3) < 0.5:
                    pass

# ---------------------------------------------------------------- ceilings: lanterns, drips (all layers)
LAMP = {0: "lantern[hanging=true]", 1: "soul_lantern[hanging=true]", 2: "shroomlight", 3: "verdant_froglight[axis=y]",
        4: "sea_lantern", 5: "soul_lantern[hanging=true]"}
for k, (name, F, cx, cz, rx, rz, H) in enumerate(LAYERS):
    for x in range(cx - rx, cx + rx + 1):
        for z in range(cz - rz, cz + rz + 1):
            if cave_u(k, x, z) >= 0.97 or blocked(k, x, z):
                continue
            h = hcol(k, x, z)
            if h < 4:
                continue
            top = F + h
            r1, r2, r3 = hash01(x, 40 + k, z, 4), hash01(x, 41 + k, z, 5), hash01(x, 42 + k, z, 6)
            if r1 < 0.0035:
                if k == 2 or k == 3 or k == 4:
                    area.set(x, top, z, LAMP[k])
                else:
                    area.set(x, top, z, LAMP[k])
            elif r2 < 0.02:
                n = 1 + int(r3 * 3)
                for i in range(n):
                    area.set(x, top - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" +
                             ("tip" if i == n - 1 else "frustum") + "]")
            elif k == 3 and r3 < 0.03:
                area.set(x, top, z, "minecraft:spore_blossom")
            elif k == 3 and r3 < 0.06:
                area.set(x, top, z, "minecraft:hanging_roots")

# ---------------------------------------------------------------- layer 1: Gunpowder Mines
k = 0
F = FL[0]
for z0 in (-22, 14, 28):                        # mine-cart rail lines with end buffers
    for x in range(-30, 31):
        if abs(x) <= 6 and abs(z0) <= 6:
            continue
        if free(0, x, z0, 0.88):
            area.set(x, F + 1, z0, "minecraft:rail[shape=east_west]" if x % 9 else "minecraft:powered_rail[shape=east_west,powered=false]")
    for x in (-31, 31):
        if free(0, x, z0, 0.9):
            area.set(x, F + 1, z0, "minecraft:stone_brick_wall")
for (x, z) in spots(0, 9, 11, 0.8, 9):           # TNT crates
    for (dx, dz, h) in ((0, 0, 2), (1, 0, 2), (0, 1, 1), (1, 1, 2), (2, 0, 1)):
        area.fill(x + dx, F + 1, z + dz, x + dx, F + h, z + dz, "minecraft:tnt")
    area.set(x - 1, F + 1, z, "minecraft:barrel[facing=up]")
for (x, z) in spots(0, 6, 12, 0.8, 8):           # coal heaps
    for (dx, dz, h) in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (-1, 0, 1)):
        area.fill(x + dx, F + 1, z + dz, x + dx, F + h, z + dz, "minecraft:coal_block")
for (x, z) in spots(0, 8, 13, 0.82, 10):         # timber arches
    for s in (-3, 3):
        area.fill(x + s, F + 1, z, x + s, F + 5, z, "minecraft:oak_log[axis=y]")
    area.fill(x - 3, F + 6, z, x + 3, F + 6, z, "minecraft:spruce_log[axis=x]")
    area.set(x, F + 5, z, "minecraft:lantern[hanging=true]")
for (x, z) in spots(0, 14, 14, 0.85, 5):
    post(x, F + 1, z, 2, "minecraft:spruce_fence")
for (x, z) in spots(0, 20, 15, 0.9, 4):
    area.set(x, F + 1, z, "minecraft:gravel" if hash01(x, 0, z) < 0.6 else "minecraft:cobblestone_slab[type=bottom]")

# ---------------------------------------------------------------- layer 2: Lapis Quarry
k = 1
F = FL[1]
for (x, z) in spots(1, 9, 21, 0.82, 8):          # lapis heaps and stepped cuts
    for (dx, dz, h) in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (-1, 0, 1), (0, -1, 2)):
        area.fill(x + dx, F + 1, z + dz, x + dx, F + h, z + dz, "minecraft:lapis_block")
for (x, z) in spots(1, 6, 22, 0.8, 11):          # scaffold towers
    for dy in range(1, 8):
        area.set(x, F + dy, z, "minecraft:scaffolding[bottom=false]")
    area.set(x + 1, F + 1, z, "minecraft:barrel[facing=up]")
for (x, z) in spots(1, 5, 23, 0.8, 12):          # terraces
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if dx * dx + dz * dz <= 10:
                area.set(x + dx, F + 1, z + dz, "minecraft:stone_brick_slab[type=bottom]" if dx * dx + dz * dz > 4 else "minecraft:stone_bricks")
area_pts = spots(1, 14, 24, 0.85, 6)
for (x, z) in area_pts:
    post(x, F + 1, z, 2, "minecraft:cobbled_deepslate_wall", "soul_lantern[hanging=false]")
for (x, z) in spots(1, 18, 25, 0.9, 4):
    area.set(x, F + 1, z, "minecraft:amethyst_cluster[facing=up]" if hash01(x, 1, z) < 0.3 else "minecraft:tuff")

# ---------------------------------------------------------------- layer 3: Pigmen's Den
k = 2
F = FL[2]
for (x, z) in spots(2, 6, 31, 0.78, 14):         # pigmen huts
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            wall = max(abs(dx), abs(dz)) == 3
            for y in range(F + 1, F + 5):
                if wall:
                    door = dz == 3 and abs(dx) <= 0 and y < F + 4
                    area.set(x + dx, y, z + dz, "minecraft:air" if door else (
                        "minecraft:red_nether_bricks" if y < F + 4 else "minecraft:nether_brick_slab[type=top]"))
                else:
                    area.set(x + dx, y, z + dz, "minecraft:air")
            area.set(x + dx, F + 5, z + dz, "minecraft:nether_brick_slab[type=bottom]" if max(abs(dx), abs(dz)) >= 2 else "minecraft:red_nether_bricks")
    area.set(x, F + 1, z, "minecraft:campfire[lit=true]")
    area.set(x + 2, F + 1, z - 2, "minecraft:barrel[facing=up]")
    area.set(x - 2, F + 1, z - 2, "minecraft:crimson_planks")
    area.set(x, F + 3, z - 3, "minecraft:lantern[hanging=false]" if False else "minecraft:red_nether_bricks")
for (x, z) in spots(2, 12, 32, 0.85, 6):         # netherrack mounds
    for (dx, dz, h) in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (-1, 0, 1), (0, -1, 1)):
        area.fill(x + dx, F + 1, z + dz, x + dx, F + h, z + dz, "minecraft:netherrack" if hash01(x + dx, 3, z) < 0.7 else "minecraft:red_terracotta")
    if hash01(x, 4, z) < 0.5:
        area.set(x, F + 3, z, "minecraft:crimson_fungus")
for (x, z) in spots(2, 14, 33, 0.88, 6):
    post(x, F + 1, z, 2, "minecraft:nether_brick_fence", "lantern[hanging=false]")
for (x, z) in spots(2, 16, 34, 0.9, 4):
    area.set(x, F + 1, z, "minecraft:nether_wart_block" if hash01(x, 5, z) < 0.5 else "minecraft:red_terracotta")

# ---------------------------------------------------------------- layer 4: Slimehill
k = 3
F = FL[3]
for (x, z) in spots(3, 10, 41, 0.8, 9):          # slime-green hillocks
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            d2 = dx * dx + dz * dz
            if d2 <= 16:
                h = int(3 * (1 - d2 / 17.0)) + 1
                for y in range(F + 1, F + h + 1):
                    area.set(x + dx, y, z + dz, "minecraft:moss_block" if y < F + h or d2 > 4 else "minecraft:slime_block")
for (x, z) in spots(3, 12, 42, 0.85, 6):         # slime columns
    n = 3 + int(hash01(x, 6, z) * 3)
    area.fill(x, F + 1, z, x, F + n, z, "minecraft:slime_block")
    area.set(x, F + n + 1, z, "minecraft:lime_stained_glass")
for (x, z) in spots(3, 40, 43, 0.9, 3):
    r = hash01(x, 7, z)
    area.set(x, F + 1, z, "minecraft:moss_carpet" if r < 0.5 else "minecraft:azalea" if r < 0.65 else "minecraft:fern" if r < 0.8 else "minecraft:big_dripleaf[facing=north]")

# ---------------------------------------------------------------- layer 5: Diamond Reserve
k = 4
F = FL[4]
for (x, z) in spots(4, 10, 51, 0.82, 8):         # amethyst-and-ice crystal clusters
    for (dx, dz, h) in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (-1, 0, 1)):
        area.fill(x + dx, F + 1, z + dz, x + dx, F + h, z + dz, "minecraft:amethyst_block")
    area.set(x, F + 4, z, "minecraft:amethyst_cluster[facing=up]")
for (x, z) in spots(4, 7, 52, 0.8, 10):          # mining camps: scaffold + barrels + lamps
    for dy in range(1, 6):
        area.set(x, F + dy, z, "minecraft:scaffolding[bottom=false]")
    area.set(x + 1, F + 1, z, "minecraft:barrel[facing=up]")
    area.set(x - 1, F + 1, z, "minecraft:crafting_table")
    area.set(x, F + 6, z, "minecraft:lantern[hanging=false]")
for (x, z) in spots(4, 14, 53, 0.88, 5):
    post(x, F + 1, z, 2, "minecraft:polished_deepslate_wall", "soul_lantern[hanging=false]")
for (x, z) in spots(4, 20, 54, 0.9, 4):
    area.set(x, F + 1, z, "minecraft:small_amethyst_bud[facing=up]" if hash01(x, 8, z) < 0.4 else "minecraft:cobbled_deepslate_slab[type=bottom]")

# ---------------------------------------------------------------- layer 6: Obsidian Sanctuary and its shrine
k = 5
F = FL[5]
SX, SZ = 0, -20
for dx in range(-7, 8):
    for dz in range(-7, 8):
        if dx * dx + dz * dz <= 52:
            ring = dx * dx + dz * dz > 36
            area.set(SX + dx, F, SZ + dz, "minecraft:polished_blackstone_bricks" if ring else "minecraft:polished_blackstone")
            for y in range(F + 1, F + 8):
                area.set(SX + dx, y, SZ + dz, "minecraft:air")
for ang in range(8):                              # eight obsidian pillars round the shrine
    a = ang * math.pi / 4
    px, pz = SX + int(round(math.cos(a) * 6)), SZ + int(round(math.sin(a) * 6))
    area.fill(px, F + 1, pz, px, F + 5, pz, "minecraft:obsidian")
    area.set(px, F + 6, pz, "minecraft:soul_lantern[hanging=false]")
    area.set(px, F + 4, pz, "minecraft:crying_obsidian")
for dx in range(-2, 3):                           # the altar
    for dz in range(-2, 3):
        area.set(SX + dx, F + 1, SZ + dz, "minecraft:polished_blackstone_slab[type=bottom]" if max(abs(dx), abs(dz)) == 2 else "minecraft:gilded_blackstone")
area.set(SX, F + 2, SZ, "minecraft:diamond_block")
for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    area.set(SX + dx, F + 2, SZ + dz, "minecraft:diamond_block")
    area.set(SX + dx, F + 3, SZ + dz, "minecraft:soul_lantern[hanging=false]")
area.set(SX, F + 3, SZ, "minecraft:enchanting_table" if False else "minecraft:beacon")
area.set(SX, F + 2, SZ + 3, "minecraft:soul_campfire[lit=true]")
for dx in (-1, 0, 1):
    area.set(SX + dx, F + 1, SZ + 5, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]")
for (x, z) in spots(5, 14, 61, 0.78, 8):         # obsidian spires, mineable
    if math.hypot(x - SX, z - SZ) < 12:
        continue
    n = 3 + int(hash01(x, 9, z) * 5)
    for (dx, dz, h) in ((0, 0, n), (1, 0, max(1, n - 3)), (0, 1, max(1, n - 2)), (-1, 0, 1)):
        area.fill(x + dx, F + 1, z + dz, x + dx, F + h, z + dz, "minecraft:obsidian")
    if hash01(x, 10, z) < 0.4:
        area.set(x, F + n + 1, z, "minecraft:crying_obsidian")
for (x, z) in spots(5, 10, 62, 0.85, 7):
    if math.hypot(x - SX, z - SZ) < 12:
        continue
    area.set(x, F + 1, z, "minecraft:diamond_block" if hash01(x, 11, z) < 0.35 else "minecraft:obsidian")
for (x, z) in spots(5, 14, 63, 0.9, 5):
    area.set(x, F + 1, z, "minecraft:polished_blackstone_wall")
    area.set(x, F + 2, z, "minecraft:soul_lantern[hanging=false]")

# ---------------------------------------------------------------- the top: plaza, gate, Walter's stall, the pad
ground = area.mix({"andesite": 5, "tuff": 4, "polished_andesite": 2, "cobbled_deepslate": 3})
for x in range(-20, 21):
    for z in range(46, 66):
        if abs(x) > 3 or z > 58:
            if area.get(x, TOP, z) is not None:
                area.set(x, TOP, z, ground(x, TOP, z))
for dx in range(-3, 4):                                       # the paving in front of the gate
    for z in range(59, 66):
        area.set(dx, TOP, z, "minecraft:stone_bricks" if (dx + z) % 5 else "minecraft:chiseled_stone_bricks")
# the entrance gate at z=60
for x in (-5, 5):
    area.fill(x, TOP + 1, 60, x, TOP + 7, 60, "minecraft:stone_bricks")
    area.set(x, TOP + 1, 60, "minecraft:chiseled_stone_bricks")
    area.set(x, TOP + 8, 60, "minecraft:lantern[hanging=false]")
    area.fill(x, TOP + 1, 61, x, TOP + 3, 61, "minecraft:polished_deepslate_wall")
area.fill(-6, TOP + 8, 60, 6, TOP + 8, 60, "minecraft:deepslate_brick_slab[type=bottom]")
area.fill(-5, TOP + 7, 60, 5, TOP + 7, 60, "minecraft:deepslate_bricks")
for x in (-3, 0, 3):
    area.set(x, TOP + 6, 60, "minecraft:chain[axis=y]")
    area.set(x, TOP + 5, 60, "minecraft:lantern[hanging=true]")
sign(0, TOP + 1, 62, 8, "Deep Caverns", "Mining V")
for (x, z) in ((-9, 63), (9, 63), (-9, 52), (9, 52), (-16, 48), (16, 48)):
    post(x, TOP + 1, z, 3, "minecraft:polished_deepslate_wall")
# Walter's stall (the shop) to the left of the gate
for dx in (-17, -11):
    for dz in (56, 61):
        area.fill(dx, TOP + 1, dz, dx, TOP + 3, dz, "minecraft:spruce_log[axis=y]")
for x in range(-18, -9):
    for z in range(55, 63):
        area.set(x, TOP + 4, z, "minecraft:spruce_slab[type=bottom]")
for x in range(-16, -11):
    area.set(x, TOP + 1, 59, "minecraft:barrel[facing=up]" if x % 2 else "minecraft:spruce_planks")
area.set(-17, TOP + 1, 58, "minecraft:crafting_table")
area.set(-11, TOP + 1, 58, "minecraft:barrel[facing=up]")
area.set(-14, TOP + 3, 59, "minecraft:lantern[hanging=true]")
area.set(-12, TOP + 2, 59, "minecraft:lantern[hanging=false]")
sign(-14, TOP + 1, 62, 0, "Walter", "Mining shop")
area.npc("Walter", -14, TOP + 1, 57, yaw=0.0, note="shop: mining goods at the cavern entrance")
# the launch pad to the Gold Mine
PX, PZ = 15, 57
for dx in range(-3, 4):
    for dz in range(-3, 4):
        area.set(PX + dx, TOP, PZ + dz, "minecraft:polished_blackstone_bricks" if max(abs(dx), abs(dz)) == 3 else "minecraft:polished_blackstone")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        area.set(PX + dx, TOP, PZ + dz, "minecraft:slime_block")
        for y in range(TOP + 1, TOP + 4):
            area.set(PX + dx, y, PZ + dz, "minecraft:air")
area.set(PX, TOP + 1, PZ, "minecraft:heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(PX + dx, TOP + 1, PZ + dz, "minecraft:polished_blackstone_wall")
    area.set(PX + dx, TOP + 2, PZ + dz, "minecraft:lantern[hanging=false]")
sign(PX, TOP + 1, PZ - 3, 8, "Gold Mine", "Launch pad", "dark_oak")
area.pad("the Gold Mine", PX - 1, TOP + 1, PZ - 1, PX + 1, TOP + 2, PZ + 1, warp="gold")
# ore piles, rails and a cart track across the plaza
for x in range(18, 36):
    if area.get(x, TOP, 50) is not None:
        area.set(x, TOP + 1, 50, "minecraft:rail[shape=east_west]")
area.set(36, TOP + 1, 50, "minecraft:polished_deepslate_wall")
area.set(36, TOP + 2, 50, "minecraft:lantern[hanging=false]")
for (x, z, k2) in ((22, 53, 3), (23, 53, 2), (22, 54, 2), (30, 54, 3), (31, 54, 2), (-26, 50, 2), (-27, 50, 1)):
    area.fill(x, TOP + 1, z, x, TOP + k2, z, "minecraft:coal_block" if x > 0 else "minecraft:raw_iron_block")
for (x, z) in ((26, 56), (-24, 47), (34, 48)):
    area.set(x, TOP + 1, z, "minecraft:barrel[facing=up]")
# the plateau's rim and crags: a few lantern posts, rock heaps
for ang in range(0, 360, 20):
    a = math.radians(ang)
    x, z = int(round(math.cos(a) * 62)), int(round(math.sin(a) * 62))
    if area.get(x, TOP, z) is not None and not (abs(x) < 24 and z > 40) and hash01(x, 5, z) < 0.6:
        post(x, TOP + 1, z, 3, "minecraft:cobbled_deepslate_wall")

# ---------------------------------------------------------------- NPCs, souls, warps
lx, ly, lz = POCKETS[1]
# NPC spots on the layers
area.npc("Lapis Miner", 12, FL[1] + 1, 14, yaw=180.0, note="Lapis Quarry: lapis dust buyer")
area.npc("Redstone Miner", -14, FL[2] + 1, -12, yaw=0.0, note="Pigmen's Den: redstone quests")
area.npc("Rhys", SX, FL[5] + 1, SZ + 4, yaw=180.0, note="Obsidian Sanctuary, at the shrine")
for (x, y, z) in ((12, FL[1] + 1, 14), (-14, FL[2] + 1, -12)):
    area.set(x, y - 1, z, "minecraft:polished_deepslate")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        area.set(x + dx, y, z + dz, "minecraft:lantern[hanging=false]" if (dx + dz) % 4 == 0 else "minecraft:barrel[facing=up]")
for k_, (px, py, pz) in POCKETS.items():
    area.set(px, py - 1, pz, "minecraft:gold_block" if k_ in (1, 5) else "minecraft:raw_gold_block")
    area.fairy_soul(px, py, pz)
area.arrival(0, TOP + 1, 62, yaw=180.0)
area.warp("Deep Caverns", 0, TOP + 1, 62, yaw=180.0)
for name, F, *_ in LAYERS:
    area.spots["notes"].append(f"{name}: floor y{F}, standing y{F + 1}")
area.spots["notes"].append("Lift at (0, F+1, 0) on every layer; entrance stairs x -3..3, z 58..42 from y185 down to y169; "
                           f"ramp k->k+1 spirals at radius {RAMP_R:.0f} from angle {[round(a, 2) for a in a_start]}")

# ---------------------------------------------------------------- zones (cut by y, one per layer; x/z -100..100)
LO, HI = -100, 100
blocks_common = {"minecraft:stone": {"becomes": "minecraft:bedrock", "regrow": 5}}


def bl(extra):
    b = {"minecraft:stone": {"becomes": "minecraft:bedrock", "regrow": 5}}
    for k_, v in extra.items():
        b["minecraft:" + k_] = {"becomes": "minecraft:bedrock", "regrow": v}
    return b


def mob(typ, name, lvl, hp, dmg, xp, coins, mx, every, F):
    return {"type": "minecraft:" + typ, "name": name, "level": lvl, "health": hp, "damage": dmg, "xp": xp, "coins": coins,
            "max": mx, "every": every, "indoors": True, "from": [-30, F + 1, -30], "to": [30, F + 5, 30]}


area.zone("Deep Caverns", box=((LO, TOP, LO), (HI, 199, HI)))
Y_TOPS = [FL[0] + 16] + [FL[k] + 16 for k in range(1, 6)]
Y_BOTS = [FL[k] - 13 for k in range(6)]
Y_BOTS[5] = 10
ZB = lambda k: ((LO, Y_BOTS[k], LO), (HI, FL[0] + 16 if k == 0 else Y_TOPS[k], HI))
area.zone("Gunpowder Mines", box=ZB(0), blocks=bl({"coal_ore": 6, "iron_ore": 7, "gold_ore": 8}),
          mobs=[mob("creeper", "Sneaky Creeper", 3, 120, 0, 8, 3, 6, 6, FL[0])])
area.zone("Lapis Quarry", box=ZB(1), blocks=bl({"lapis_ore": 6}),
          mobs=[mob("zombie", "Lapis Zombie", 7, 200, 50, 12, 5, 8, 5, FL[1])])
area.zone("Pigmen's Den", box=ZB(2), blocks=bl({"redstone_ore": 6}),
          mobs=[mob("zombified_piglin", "Redstone Pigman", 10, 250, 75, 20, 12, 8, 5, FL[2])])
area.zone("Slimehill", box=ZB(3), blocks=bl({"emerald_ore": 7}),
          mobs=[mob("slime", "Emerald Slime", 15, 150, 100, 12, 5, 6, 6, FL[3])])
area.zone("Diamond Reserve", box=ZB(4), blocks=bl({"diamond_ore": 8}),
          mobs=[mob("zombie", "Miner Zombie", 15, 250, 180, 20, 12, 6, 6, FL[4]),
                mob("skeleton", "Miner Skeleton", 15, 250, 150, 20, 12, 4, 8, FL[4])])
area.zone("Obsidian Sanctuary", box=ZB(5), blocks=bl({"diamond_ore": 8, "obsidian": 20, "diamond_block": 30}),
          mobs=[mob("zombie", "Miner Zombie", 20, 300, 250, 20, 12, 6, 6, FL[5]),
                mob("skeleton", "Miner Skeleton", 20, 300, 250, 20, 12, 4, 8, FL[5])])
for z_ in area.zones:
    print(z_["name"], z_["from"], z_["to"])
area.save()
