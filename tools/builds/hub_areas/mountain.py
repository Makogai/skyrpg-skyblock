#!/usr/bin/env python3
"""The Mountain (Hypixel's Hub, south of the village) with High Level behind it: a big floating island with a snowy peak
you climb by a winding path, Scoop's cabin on the summit, the Carrot farm inside the mountain, the Dungeon Hub portal and
a hot air balloon (Amelia, Christopher) on the mountainside, and High Level's tall wooden door on the south plateau
where Skeletons spawn at night.

    python tools/builds/hub_areas/mountain.py
"""
import math

from kit import Area, hash01, noise

G = 64
CX, CZ = 0, 190            # island centre
MX, MZ, MR = 0, 186, 48    # mountain centre / foot radius
PEAK = 96                  # summit at G + PEAK = 160
D0 = 0.21                  # flat summit disc radius (fraction of MR) ~10 blocks
TOP = G + PEAK
area = Area("mountain", (-90, 20, 86), (180, 170, 200), version=2)

rock = area.mix({"andesite": 5, "tuff": 2, "granite": 2, "calcite": 1, "cobblestone": 1, "diorite": 1})
grassy = area.mix({"grass_block": 12, "coarse_dirt": 1, "podzol": 1})
cols = area.island(CX, CZ, 66, top=G, seed=7, surface=grassy, hills=0.0, depth=8, wobble=0.08,
                   stone=area.mix({"andesite": 6, "tuff": 3, "cobblestone": 1, "granite": 2}))

# ---------------------------------------------------------------- the north foot: the bridge arrives at y 64
area.bridge((0, 87), (0, 118), y=G, width=3)
for x in range(-5, 6):
    for z in range(119, 128):
        if (x, z) not in area.top or area.top[(x, z)] != G:
            area.set(x, G, z, "minecraft:grass_block")
            for y in (G - 1, G - 2, G - 3):
                area.set(x, y, z, "minecraft:dirt")
            for y in range(G - 4, G - 9, -1):
                if area.get(x, y, z) is None:
                    area.set(x, y, z, rock(x, y, z))
            area.top[(x, z)] = G
            cols.add((x, z))


# ---------------------------------------------------------------- helpers
def S(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


DIRN = {(0, 1): "south", (0, -1): "north", (1, 0): "east", (-1, 0): "west"}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}
ROT = {"south": 0, "west": 4, "north": 8, "east": 12}


class Frame:
    """Local frame at (cx, cz): b runs along `out` (snapped to an axis), a sideways."""

    def __init__(self, cx, cz, out):
        ox, oz = out
        self.o = ((1 if ox > 0 else -1), 0) if abs(ox) >= abs(oz) else (0, (1 if oz > 0 else -1))
        self.l = (-self.o[1], self.o[0])
        self.cx, self.cz = cx, cz
        self.name = DIRN[self.o]
        self.back = DIRN[(-self.o[0], -self.o[1])]

    def w(self, a, b):
        return self.cx + self.l[0] * a + self.o[0] * b, self.cz + self.l[1] * a + self.o[1] * b

    def set(self, a, b, y, block, data=None):
        x, z = self.w(a, b)
        area.set(x, y, z, block, data)

    def fill(self, a1, b1, y1, a2, b2, y2, block):
        for a in range(min(a1, a2), max(a1, a2) + 1):
            for b in range(min(b1, b2), max(b1, b2) + 1):
                for y in range(min(y1, y2), max(y1, y2) + 1):
                    self.set(a, b, y, block)


def support(x, y, z, block=None):
    """Fills below y until something solid is met."""
    for yy in range(y - 1, y - 70, -1):
        if area.solid(x, yy, z):
            break
        area.set(x, yy, z, block or rock(x, yy, z))


# ---------------------------------------------------------------- the winding path up (sampled first)
SLOPE = 0.30
samples = []
phi, yy = 0.0, float(G)
while True:
    t = (yy - G) / PEAK
    r = MR * (D0 + (1 - D0) * (1 - max(0.0, t) ** 0.8)) if t < 1 else MR * D0
    samples.append((MX + r * math.sin(phi), MZ - r * math.cos(phi), yy))
    if yy >= TOP - 0.4:
        break
    phi += 1.0 / max(r, 3.0)
    yy += SLOPE


def sample_at(y_target):
    for i, (x, z, y) in enumerate(samples):
        if y >= y_target:
            return i, x, z, y
    return len(samples) - 1, *samples[-1]


def outward(x, z):
    d = math.hypot(x - MX, z - MZ) or 1.0
    return (x - MX) / d, (z - MZ) / d


# Points of interest on the path.
_, px, pz, py = sample_at(100)           # Dungeon Hub portal (terrace cut into the slope, inward of the path)
ux, uz = outward(px, pz)
PORTAL = (int(round(px - ux * 8)), int(round(pz - uz * 8)), int(round(py)), (ux, uz))
_, px, pz, py = sample_at(116)           # hot air balloon (platform on the outer side)
ux, uz = outward(px, pz)
BALLOON = (int(round(px + ux * 5)), int(round(pz + uz * 5)), int(round(py)), (ux, uz))
_, px, pz, py = sample_at(131)           # the hidden ledge
ux, uz = outward(px, pz)
LEDGE = (int(round(px + ux * 7)), int(round(pz + uz * 7)), int(round(py)), (ux, uz))
END = samples[-1]
TERRACES = [(PORTAL[0], PORTAL[1], 6, 4, PORTAL[2]), (BALLOON[0], BALLOON[1], 4, 4, BALLOON[2]),
            (LEDGE[0], LEDGE[1], 1, 3, LEDGE[2])]

# ---------------------------------------------------------------- the terrain: the mountain on the island
def mountain_h(x, z):
    d = math.hypot(x - MX, z - MZ) / MR
    d *= 1 + 0.07 * noise(x, z, 4, 1.2) * S((d - 0.25) / 0.3)
    dd = max(0.0, (d - D0) / (1 - D0))
    h = G + PEAK * max(0.0, 1 - dd) ** 1.25
    # High Level: the plateau in the south and the cliff the door is set into.
    h = max(h, G + 8 * S((z - 226) / 10) * S((46 - abs(x)) / 12))
    if 210 <= z <= 235 and abs(x) <= 30:
        ctop = 90 + 2 * noise(x, z, 9, 1.0) - max(0, abs(x) - 18) * 3.0 - max(0, 224 - z) * 2.0
        h = max(h, ctop)
    # the tunnel to the carrot cave: keep a ridge over it
    mn = G + min(9.0, max(0.0, (z - 137) * 2.2))
    if abs(x) <= 3:
        h = max(h, mn)
    elif abs(x) <= 6:
        h = max(h, G + (mn - G) * (6 - abs(x)) / 3)
    for (tx, tz, hs, margin, ty) in TERRACES:
        dist = max(abs(x - tx), abs(z - tz))
        if dist <= hs:
            h = ty
        elif dist < hs + margin:
            w = 1 - (dist - hs) / margin
            h = h * (1 - w) + ty * w
    return h


Hmap = {}
for (x, z) in cols:
    Hmap[(x, z)] = max(area.top[(x, z)], int(round(mountain_h(x, z))))

in_plateau = lambda x, z: z >= 236 and abs(x) <= 44
plateau_mix = area.mix({"gravel": 3, "coarse_dirt": 3, "andesite": 2, "tuff": 1})


def top_block(x, y, z):
    slope = max(abs(y - Hmap.get(n, y)) for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))
    if y <= G + 1 and not in_plateau(x, z):
        return grassy(x, y, z)
    if in_plateau(x, z) and slope <= 1:
        return plateau_mix(x, y, z)
    if y >= 150:
        return "minecraft:snow_block"
    if y >= 138:
        if slope >= 4:
            return rock(x, y, z)
        return "minecraft:snow_block" if hash01(x, y, z, 31) < 0.7 else rock(x, y, z)
    if slope >= 3:
        return rock(x, y, z)
    if y <= 110:
        return grassy(x, y, z)
    return area.mix({"grass_block": 2, "coarse_dirt": 1, "andesite": 2, "gravel": 1, "tuff": 1})(x, y, z)


for (x, z) in sorted(cols):
    y0, ht = area.top[(x, z)], Hmap[(x, z)]
    if ht <= y0:
        continue
    surf = top_block(x, ht, z)
    soil = surf.endswith(("grass_block", "coarse_dirt", "podzol"))
    for y in range(y0, ht):
        area.set(x, y, z, "minecraft:dirt" if (soil and y >= ht - 3) else rock(x, y, z))
    area.set(x, ht, z, surf)
    area.top[(x, z)] = ht

# ---------------------------------------------------------------- trees on the lower slopes and the meadow
terrace_cols = set()
for (tx, tz, hs, margin, ty) in TERRACES:
    for x in range(tx - hs - margin - 3, tx + hs + margin + 4):
        for z in range(tz - hs - margin - 3, tz + hs + margin + 4):
            terrace_cols.add((x, z))
ring = lambda x, z: abs(math.hypot(x - MX, z - 187) - 51) < 3.5


def tree_ok(x, z):
    return not ((x, z) in terrace_cols or in_plateau(x, z) or ring(x, z) or abs(x) < 8 and z < 160 or z > 226)


nt = 0
for (x, z) in sorted(cols):
    ht = Hmap[(x, z)]
    if ht > 108 or not tree_ok(x, z) or hash01(x, 41, z, 8) > 0.017:
        continue
    if (area.get(x, ht, z) or "") != "minecraft:grass_block" or area.get(x, ht + 1, z) not in (None, "minecraft:air"):
        continue
    if max(abs(ht - Hmap.get(n, ht)) for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))) > 2:
        continue
    if hash01(x, 42, z) < 0.8:
        area.spruce(x, ht + 1, z, height=7 + int(hash01(x, 43, z) * 5), seed=nt)
    else:
        area.oak(x, ht + 1, z, height=5, radius=2, seed=nt)
    nt += 1

# snow on the peak
for (x, z) in sorted(cols):
    ht = Hmap[(x, z)]
    if ht >= 148 and math.hypot(x - MX, z - MZ) > 9 and area.get(x, ht, z) == "minecraft:snow_block" \
            and area.get(x, ht + 1, z) in (None, "minecraft:air") and hash01(x, 51, z) < 0.4:
        area.set(x, ht + 1, z, f"minecraft:snow[layers={1 + int(hash01(x, 52, z) * 3)}]")

# ---------------------------------------------------------------- the Carrot farm inside the mountain
CAVE = (-13, 13, 154, 170)       # x1, x2, z1, z2
for x in range(CAVE[0], CAVE[1] + 1):
    for z in range(CAVE[2], CAVE[3] + 1):
        for y in range(G + 1, G + 11):
            area.set(x, y, z, "minecraft:air")
        area.set(x, G, z, "minecraft:dirt_path" if abs(x) <= 3 else "minecraft:dirt")
for x in (-12, -11, -10, -9, -7, -6, -5, -4, 4, 5, 6, 7, 9, 10, 11, 12):
    for z in range(155, 170):
        area.set(x, G, z, "minecraft:farmland[moisture=7]")
        area.set(x, G + 1, z, "minecraft:carrots[age=7]")
for x in (-8, 8):
    for z in range(155, 170):
        area.set(x, G, z, "minecraft:water[level=0]")
        area.set(x, G - 1, z, "minecraft:andesite")
for x in (-13, 13):
    for z in range(155, 170):
        pass
# a mossy stone-brick curb between aisle and beds, lanterns and glowstone
for z in range(155, 170):
    for x in (-3, 3):
        area.set(x, G, z, "minecraft:stone_bricks" if hash01(x, 0, z, 3) < 0.7 else "minecraft:mossy_stone_bricks")
for z in (157, 163, 169):
    for x in (-3, 3):
        area.fill(x, G + 1, z, x, G + 9, z, "minecraft:spruce_log[axis=y]")
    for x in (-8, 0, 8):
        for j in (G + 10, G + 9):
            area.set(x, j, z, "minecraft:chain[axis=y]")
        area.set(x, G + 8, z, "minecraft:lantern[hanging=true]")
for x in (-11, -5, 5, 11):
    for z in (156, 160, 164, 168):
        area.set(x, G + 11, z, "minecraft:glowstone")
for z in range(155, 170, 2):
    area.set(-13, G + 1, z, "minecraft:hay_block[axis=y]" if z % 6 == 1 else "minecraft:barrel[facing=up]")
    area.set(13, G + 1, z, "minecraft:composter[level=4]" if z % 6 == 1 else "minecraft:hay_block[axis=y]")
# a hidden alcove at the back (Fairy Soul)
for x in (-1, 0, 1):
    for z in (171, 172, 173):
        for y in (G + 1, G + 2, G + 3):
            area.set(x, y, z, "minecraft:air")
        area.set(x, G, z, "minecraft:polished_andesite")
area.set(0, G + 2, 173, "minecraft:soul_lantern[hanging=false]")
area.set(-1, G + 1, 173, "minecraft:hay_block[axis=y]")
area.set(1, G + 1, 173, "minecraft:hay_block[axis=y]")
# the tunnel from the north foot
for z in range(137, 154):
    for x in range(-3, 4):
        for y in range(G + 1, G + 6):
            inside = abs(x) <= 2 and y <= G + 4
            if inside:
                area.set(x, y, z, "minecraft:air")
            elif z >= 140:
                area.set(x, y, z, "minecraft:stone_bricks" if hash01(x, y, z, 6) < 0.7 else "minecraft:mossy_stone_bricks")
        area.set(x, G, z, "minecraft:stone_bricks" if abs(x) == 3 else "minecraft:dirt_path")
    if z % 4 == 1 and z >= 142:
        area.set(-2, G + 4, z, "minecraft:lantern[hanging=true]")
        area.set(2, G + 4, z, "minecraft:lantern[hanging=true]")
for x in range(-4, 5):
    for y in range(G + 1, G + 8):
        if abs(x) >= 3 or y >= G + 5:
            area.set(x, y, 140, "minecraft:stone_brick_slab[type=bottom]" if y == G + 7 else "minecraft:chiseled_stone_bricks"
                     if y == G + 6 and abs(x) < 3 else "minecraft:stone_bricks")
for x in (-4, 4):
    area.set(x, G + 8, 140, "minecraft:lantern[hanging=false]")
area.set(0, G + 4, 140, "minecraft:air")
area.set(3, G + 2, 139, "minecraft:dark_oak_sign[rotation=8]", {
    "id": "minecraft:sign", "front_text": {"messages": ['""', '"Carrot Farm"', '"(it grows back)"', '""'], "color": "white",
                                          "has_glowing_text": 1},
    "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.set(3, G + 1, 139, "minecraft:spruce_fence")
area.set(3, G + 1, 138, "minecraft:air")

# ---------------------------------------------------------------- the path up: lay it
cells = {}   # (x, z) -> (yf, stair_facing or None)
carve = {}
prev_cell, prev_yf = None, None
dedup = []
for (x, z, y) in samples:
    c = (int(round(x)), int(round(z)))
    if dedup and dedup[-1][0] == c:
        continue
    dedup.append((c, int(math.floor(y + 0.5))))
for i, (c, yf) in enumerate(dedup):
    j, k = max(0, i - 3), min(len(dedup) - 1, i + 3)
    tx, tz = dedup[k][0][0] - dedup[j][0][0], dedup[k][0][1] - dedup[j][0][1]
    along_x = abs(tx) >= abs(tz)
    diag = min(abs(tx), abs(tz)) > 0.55 * max(abs(tx), abs(tz), 1)
    face = DIRN[((-1 if tx > 0 else 1), 0)] if along_x else DIRN[(0, (-1 if tz > 0 else 1))]
    rises = i > 0 and yf > dedup[i - 1][1]
    x, z = c
    if diag:
        offs = [(dx, dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
    elif along_x:
        offs = [(0, dz) for dz in (-1, 0, 1)]
    else:
        offs = [(dx, 0) for dx in (-1, 0, 1)]
    for (dx, dz) in offs:
        cells[(x + dx, z + dz)] = (yf, face if rises else None)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                carve[(x + dx, z + dz)] = max(carve.get((x + dx, z + dz), -999), yf)
for (x, z), yf in carve.items():
    if (x, z) in cells:
        continue
    for y in range(yf + 1, yf + 4):
        area.set(x, y, z, "minecraft:air")
for (x, z), (yf, face) in cells.items():
    for y in range(yf + 1, yf + 5):
        area.set(x, y, z, "minecraft:air")
    if face:
        area.set(x, yf, z, f"minecraft:cobblestone_stairs[facing={face},half=bottom]")
    elif yf >= 112:
        area.set(x, yf, z, area.mix({"gravel": 3, "andesite": 2, "coarse_dirt": 1})(x, yf, z))
    else:
        area.set(x, yf, z, area.mix({"dirt_path": 5, "coarse_dirt": 1, "gravel": 1})(x, yf, z))
    support(x, yf, z, "minecraft:cobblestone" if hash01(x, yf, z) < 0.3 else None)
    area.top[(x, z)] = yf
# lantern posts at the outer edge
for i in range(10, len(dedup), 14):
    (x, z), yf = dedup[i]
    ux, uz = outward(x, z)
    qx, qz = int(round(x + ux * 2.2)), int(round(z + uz * 2.2))
    if (qx, qz) in cells or area.get(qx, yf, qz) is None and (qx, qz) not in area.top:
        continue
    area.set(qx, yf, qz, "minecraft:cobblestone_wall")
    support(qx, yf, qz)
    area.set(qx, yf + 1, qz, "minecraft:spruce_fence")
    area.set(qx, yf + 2, qz, "minecraft:spruce_fence")
    area.set(qx, yf + 3, qz, "minecraft:lantern[hanging=false]")
    for y in range(yf + 4, yf + 6):
        area.set(qx, y, qz, "minecraft:air")

# ---------------------------------------------------------------- Scoop's cabin on the summit
ex, ez, _ = END
H = Frame(MX, MZ, (ex - MX, ez - MZ))
H.cx, H.cz = H.w(0, -1)
Y0 = TOP
H.fill(-3, -3, Y0, 3, 3, Y0, "minecraft:spruce_planks")
for a in range(-5, 6):
    for b in range(-5, 7):
        for y in range(Y0 + 1, Y0 + 14):
            if abs(a) <= 4 and abs(b) <= 4 or (abs(a) <= 5 and 4 < b <= 6 and False):
                H.set(a, b, y, "minecraft:air")
for a in range(-3, 4):
    for b in range(-3, 4):
        wall = abs(a) == 3 or abs(b) == 3
        corner = abs(a) == 3 and abs(b) == 3
        for y in range(Y0 + 1, Y0 + 5):
            if corner:
                H.set(a, b, y, "minecraft:stripped_spruce_log[axis=y]")
            elif wall:
                win = y == Y0 + 2 and ((abs(a) == 3 and b in (-1, 1)) or (b == -3 and a in (-1, 1)) or (b == 3 and abs(a) == 2))
                H.set(a, b, y, "minecraft:glass_pane" if win else "minecraft:spruce_planks")
            else:
                H.set(a, b, y, "minecraft:air")
        if wall and not corner:
            H.set(a, b, Y0 + 1, "minecraft:cobblestone")
for b in range(-4, 5):
    k = abs(b)
    ry = Y0 + 4 + (4 - k)
    if k == 0:
        H.fill(-4, 0, ry, 4, 0, ry, "minecraft:spruce_planks")
    else:
        face = H.name if b > 0 else H.back
        for a in range(-4, 5):
            H.set(a, b, ry, f"minecraft:spruce_stairs[facing={face},half=bottom]")
        if k <= 3:
            for y in range(Y0 + 5, ry):
                H.set(-3, b, y, "minecraft:spruce_planks")
                H.set(3, b, y, "minecraft:spruce_planks")
# door, step, chimney, furniture
H.set(0, 3, Y0 + 1, f"minecraft:spruce_door[facing={H.name},half=lower,hinge=left]")
H.set(0, 3, Y0 + 2, f"minecraft:spruce_door[facing={H.name},half=upper,hinge=left]")
for a in (-1, 0, 1):
    H.set(a, 4, Y0, "minecraft:cobblestone")
H.set(-1, 4, Y0 + 1, "minecraft:lantern[hanging=false]")
H.set(-1, 4, Y0, "minecraft:cobblestone_wall")
H.set(-1, 4, Y0 + 1, "minecraft:lantern[hanging=false]")
H.fill(-2, -2, Y0 + 2, -2, -2, Y0 + 11, "minecraft:bricks")
H.set(-2, -2, Y0 + 1, f"minecraft:furnace[facing={H.name},lit=true]")
H.set(-2, -2, Y0 + 12, "minecraft:campfire[lit=true,signal_fire=false,facing=north]")
H.set(2, -2, Y0 + 1, "minecraft:crafting_table")
H.set(2, -1, Y0 + 1, "minecraft:barrel[facing=up]")
H.set(2, -2, Y0 + 2, "minecraft:flower_pot")
H.set(-2, 2, Y0 + 1, "minecraft:bookshelf")
H.set(-2, 1, Y0 + 1, "minecraft:bookshelf")
H.fill(-1, -1, Y0 + 1, 1, 1, Y0 + 1, "minecraft:red_carpet")
for y in (Y0 + 8, Y0 + 7, Y0 + 6):
    H.set(0, 0, y, "minecraft:chain[axis=y]")
H.set(0, 0, Y0 + 5, "minecraft:lantern[hanging=true]")
sx, sz = H.w(0, -1)
area.npc("Scoop", sx, Y0 + 1, sz, yaw=YAW[H.name], note="Scoop in his cabin on the summit (a quest giver)")
# a cairn with a Fairy Soul on the far side of the summit
cx_, cz_ = H.w(0, -8)
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if abs(dx) + abs(dz) < 2:
            area.set(cx_ + dx, Y0 + 1, cz_ + dz, "minecraft:cobblestone")
area.set(cx_, Y0 + 2, cz_, "minecraft:cobblestone_wall")
area.set(cx_, Y0 + 3, cz_, "minecraft:soul_lantern[hanging=false]")
fx, fz = H.w(2, -8)
SOUL_SUMMIT = (fx, Y0 + 1, fz)

# ---------------------------------------------------------------- the Dungeon Hub portal
px_, pz_, py_, pout = PORTAL
P = Frame(px_, pz_, pout)
P.cx, P.cz = px_, pz_
yt = py_
for a in range(-6, 7):
    for b in range(-6, 8):
        for y in range(yt + 1, yt + 13):
            P.set(a, b, y, "minecraft:air")
        P.set(a, b, yt, "minecraft:polished_blackstone_bricks" if abs(a) <= 4 and b <= 3 else "minecraft:andesite")
        support(*P.w(a, b)[:1], yt, P.w(a, b)[1])
P.fill(-5, -5, yt + 1, 5, -5, yt + 12, "minecraft:blackstone")          # back wall
P.fill(-6, -6, yt + 1, 6, -6, yt + 14, "minecraft:blackstone")
P.fill(-2, -5, yt + 1, 2, -5, yt + 6, "minecraft:obsidian")
P.fill(-2, -4, yt + 1, 2, -4, yt + 6, "minecraft:black_stained_glass")
for side in (-1, 1):
    P.fill(3 * side, -4, yt + 1, 3 * side, -3, yt + 8, "minecraft:polished_blackstone_bricks")
    P.set(3 * side, -4, yt + 4, "minecraft:gilded_blackstone")
    P.set(3 * side, -3, yt + 9, "minecraft:soul_lantern[hanging=false]")
    P.set(4 * side, -4, yt + 1, "minecraft:chiseled_polished_blackstone")
    P.set(4 * side, -4, yt + 2, "minecraft:polished_blackstone_wall")
    P.set(4 * side, -4, yt + 3, "minecraft:soul_lantern[hanging=false]")
    P.set(5 * side, 0, yt + 1, "minecraft:polished_blackstone_wall")
    P.set(5 * side, 0, yt + 2, "minecraft:soul_lantern[hanging=false]")
    P.set(2 * side, -3, yt + 8, "minecraft:polished_blackstone_brick_slab[type=bottom]")
P.fill(-3, -4, yt + 7, 3, -3, yt + 7, "minecraft:chiseled_polished_blackstone")
P.fill(-2, -4, yt + 8, 2, -3, yt + 8, "minecraft:polished_blackstone_brick_slab[type=bottom]")
for a in (-1, 1):
    P.set(a, -3, yt + 6, "minecraft:soul_lantern[hanging=true]")
P.set(0, -3, yt + 6, "minecraft:chain[axis=y]")
for a in (-2, 2):
    P.set(a, -2, yt + 1, "minecraft:soul_campfire[lit=true,facing=" + P.name + "]")
sx, sz = P.w(5, 2)
area.set(sx, yt + 1, sz, f"minecraft:dark_oak_sign[rotation={ROT[P.name]}]", {
    "id": "minecraft:sign", "front_text": {"messages": ['""', '"Dungeon Hub"', '"(coming soon)"', '""'], "color": "white",
                                          "has_glowing_text": 1},
    "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.spots["notes"].append(f"Dungeon Hub portal arch at ({px_}, {yt + 1}, {pz_}) facing {P.name}")

# ---------------------------------------------------------------- the hot air balloon (Amelia, Christopher)
bx, bz, by, bout = BALLOON
B = Frame(bx, bz, bout)
for a in range(-4, 5):
    for b in range(-4, 5):
        edge = max(abs(a), abs(b)) == 4
        for y in range(by + 1, by + 24):
            if area.get(*B.w(a, b)[:1], y, B.w(a, b)[1]) not in (None, "minecraft:air"):
                B.set(a, b, y, "minecraft:air")
        B.set(a, b, by, "minecraft:stripped_spruce_log[axis=x]" if edge else "minecraft:spruce_planks")
        support(*B.w(a, b)[:1], by, B.w(a, b)[1])
for a in range(-1, 2):
    for b in range(-1, 2):
        if max(abs(a), abs(b)) == 1:
            B.set(a, b, by + 1, "minecraft:dark_oak_planks")
            B.set(a, b, by + 2, "minecraft:spruce_trapdoor[facing=%s,half=bottom,open=true]" % B.name
                  if False else "minecraft:oak_fence")
B.set(0, 0, by + 1, "minecraft:campfire[lit=true,signal_fire=false,facing=north]")
B.set(0, 1, by + 1, "minecraft:air")
B.set(0, 1, by + 2, "minecraft:air")
for a, b in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    for y in range(by + 3, by + 7):
        B.set(a, b, y, "minecraft:oak_fence")
colors = ["red_wool", "white_wool", "orange_wool", "white_wool"]
CY, R = by + 12, 6.0
for dx in range(-7, 8):
    for dy in range(-7, 8):
        for dz in range(-7, 8):
            d = math.sqrt(dx * dx + dy * dy + dz * dz * 1.0)
            if not (R - 1.0 <= d <= R + 0.35):
                continue
            if dy < -4.2 and math.hypot(dx, dz) < 2.2:
                continue
            ang = math.atan2(dz, dx)
            col = colors[int(((ang + math.pi) / (2 * math.pi)) * 12) % 4]
            wx, wz = B.w(dx, dz)
            if area.get(wx, CY + dy, wz) in (None, "minecraft:air"):
                area.set(wx, CY + dy, wz, "minecraft:" + col)
for a in (-4, 4):
    for b in (-4, 4):
        B.set(a, b, by + 1, "minecraft:oak_fence")
        B.set(a, b, by + 2, "minecraft:lantern[hanging=false]")
ax, az = B.w(-3, 0)
area.npc("Amelia", ax, by + 1, az, yaw=YAW[B.name], note="by the hot air balloon")
cx2, cz2 = B.w(3, 0)
area.npc("Christopher", cx2, by + 1, cz2, yaw=YAW[B.name], note="by the hot air balloon")
area.spots["notes"].append(f"hot air balloon basket at ({bx}, {by + 1}, {bz})")

# ---------------------------------------------------------------- the hidden ledge (Fairy Soul)
lx, lz, ly, lout = LEDGE
L = Frame(lx, lz, lout)
for a in range(-1, 2):
    for b in range(-1, 2):
        L.set(a, b, ly, "minecraft:polished_andesite")
        support(*L.w(a, b)[:1], ly, L.w(a, b)[1])
        for y in range(ly + 1, ly + 5):
            if area.get(*L.w(a, b)[:1], y, L.w(a, b)[1]) not in (None, "minecraft:air"):
                L.set(a, b, y, "minecraft:air")
L.set(0, 1, ly + 1, "minecraft:lantern[hanging=false]")
SOUL_LEDGE = (lx, ly + 1, lz)

# ---------------------------------------------------------------- High Level: the door and its statues
Z_FACE = 235
for x in range(-3, 4):
    for y in range(73, 87):
        for z in (Z_FACE, Z_FACE - 1):
            area.set(x, y, z, "minecraft:air")
        wing = abs(x) >= 1
        band = y in (76, 81, 85)
        block = ("minecraft:stripped_dark_oak_log[axis=x]" if band else
                 "minecraft:iron_bars" if wing and y in (79, 80) and abs(x) == 1 else "minecraft:dark_oak_planks")
        if x == 0:
            block = "minecraft:stripped_spruce_log[axis=y]"
        area.set(x, y, Z_FACE - 2, block)
        for z in range(Z_FACE - 6, Z_FACE - 2):
            area.set(x, y, z, "minecraft:dark_oak_planks" if hash01(x, y, z) < 0.5 else "minecraft:spruce_planks")
for side in (-1, 1):
    for k in (4, 5):
        for y in range(73, 91):
            for z in (Z_FACE - 1, Z_FACE):
                area.set(side * k, y, z, "minecraft:polished_andesite" if (y % 5 == 0 and k == 4) else "minecraft:stone_bricks")
    area.set(side * 4, 87, Z_FACE, "minecraft:stone_brick_stairs[facing=%s,half=bottom]" % ("east" if side < 0 else "west"))
    for y in (73, 74):
        area.set(side * 6, y, Z_FACE, "minecraft:stone_bricks")
    area.set(side * 6, 75, Z_FACE, "minecraft:soul_lantern[hanging=false]")
for x in range(-5, 6):
    for y, blk in ((87, "minecraft:stone_bricks"), (88, "minecraft:chiseled_stone_bricks"), (89, "minecraft:stone_brick_slab[type=bottom]")):
        for z in (Z_FACE - 1, Z_FACE):
            if not (y == 87 and abs(x) <= 3):
                area.set(x, y, z, blk)
for x in range(-3, 4):
    area.set(x, 87, Z_FACE, "minecraft:dark_oak_slab[type=top]")
    area.set(x, 87, Z_FACE - 1, "minecraft:dark_oak_slab[type=top]")
for x in range(-3, 4):
    area.set(x, 73, Z_FACE + 1, "minecraft:stone_brick_slab[type=bottom]" if abs(x) % 2 else "minecraft:stone_bricks")
area.set(-1, 90, Z_FACE, "minecraft:soul_lantern[hanging=false]")
area.set(1, 90, Z_FACE, "minecraft:soul_lantern[hanging=false]")


def statue(cx, cz, s):
    f = area.fill
    f(cx - 2, 73, cz - 2, cx + 2, 73, cz + 2, "stone_bricks")
    f(cx - 2, 74, cz - 2, cx + 2, 74, cz + 2, "polished_andesite")
    for lx_ in (-2, 1):
        f(cx + lx_, 75, cz - 1, cx + lx_ + 1, 78, cz + 1, "andesite")
        f(cx + lx_, 75, cz + 1, cx + lx_ + 1, 75, cz + 1, "polished_andesite")
    f(cx - 2, 79, cz - 1, cx + 2, 79, cz + 1, "chiseled_stone_bricks")
    f(cx - 2, 80, cz - 1, cx + 2, 83, cz + 1, "andesite")
    f(cx - 1, 81, cz + 1, cx + 1, 82, cz + 1, "polished_andesite")
    f(cx - 3, 84, cz - 1, cx + 3, 84, cz + 1, "stone_bricks")
    f(cx - 3, 79, cz - 1, cx - 3, 83, cz + 1, "andesite")
    f(cx + 3, 79, cz - 1, cx + 3, 83, cz + 1, "andesite")
    f(cx - 1, 85, cz - 1, cx + 1, 87, cz + 1, "stone_bricks")
    f(cx - 1, 88, cz - 1, cx + 1, 88, cz + 1, "stone_brick_slab[type=bottom]")
    f(cx, 89, cz, cx, 90, cz, "andesite_wall")
    for e in (-1, 1):
        area.set(cx + e, 86, cz + 1, "black_stained_glass")
    # the sword on the outer arm
    sx_ = cx + 4 * s
    f(sx_ - 1, 79, cz, sx_ + 1, 79, cz, "polished_andesite")
    f(sx_, 80, cz, sx_, 92, cz, "calcite")
    area.set(sx_, 93, cz, "stone_brick_slab[type=bottom]")
    f(sx_, 75, cz, sx_, 78, cz, "andesite_wall")


for s in (-1, 1):
    statue(13 * s, 242, s)
# a brazier in front of each statue and bones about
for s in (-1, 1):
    for dx in (-3, 3):
        x = 13 * s + dx
        area.set(x, 73, 248, "minecraft:polished_blackstone_wall")
        area.set(x, 74, 248, "minecraft:soul_lantern[hanging=false]")
SOUL_DOOR = (-13, 73, 237)
area.set(-13, 73, 238, "minecraft:soul_lantern[hanging=false]")
area.set(-15, 73, 237, "minecraft:skeleton_skull[rotation=4]")
area.set(-12, 73, 236, "minecraft:bone_block[axis=x]")
area.set(-16, 73, 238, "minecraft:cobweb")

# ---------------------------------------------------------------- the ring path round the foot
EAST = [(0, 127), (0, 134)]
pts = []
for deg in (25, 50, 75, 100, 125, 150, 175):
    a = math.radians(deg)
    pts.append((int(round(51 * math.sin(a))), int(round(187 - 51 * math.cos(a)))))
ringblocks = area.mix({"dirt_path": 4, "coarse_dirt": 1, "gravel": 2})
area.path([(0, 119), (0, 135)], width=3, blocks=ringblocks)
for sgn in (1, -1):
    area.path([(0, 134)] + [(sgn * px_, pz_) for px_, pz_ in pts] + [(0, 240)], width=3, blocks=ringblocks)
# up to the door
area.path([(0, 240), (0, 236)], width=5, blocks=area.mix({"stone_bricks": 3, "cobblestone": 2, "mossy_stone_bricks": 1}))

# ---------------------------------------------------------------- grass, flowers, rocks
def bits(x, z):
    r = hash01(x, 61, z)
    if in_plateau(x, z):
        return "dead_bush" if r < 0.05 else "bone_block[axis=y]" if r < 0.06 else None
    ht = Hmap.get((x, z), 64)
    if ht > 110:
        return "short_grass" if r < 0.3 else "fern" if r < 0.4 else None
    return ("short_grass" if r < 0.55 else "fern" if r < 0.7 else "dandelion" if r < 0.77 else "cornflower" if r < 0.83 else
            "poppy" if r < 0.89 else "sweet_berry_bush[age=3]" if r < 0.91 else "lily_of_the_valley")


area.scatter(cols, 0.35, bits, seed=9, avoid=lambda x, z: (x, z) in terrace_cols or (x, z) in cells or abs(x) < 4 and z < 142)

# ---------------------------------------------------------------- Fairy Souls, warp, zone
area.fairy_soul(*SOUL_SUMMIT)
area.fairy_soul(0, G + 1, 172)
area.fairy_soul(*SOUL_DOOR)
area.fairy_soul(*SOUL_LEDGE)
area.warp("Mountain", 0, G + 1, 123, yaw=0.0)

plat = [(x, z) for (x, z) in cols if in_plateau(x, z) and Hmap[(x, z)] <= 73]
x1, x2 = min(x for x, _ in plat), max(x for x, _ in plat)
z1, z2 = min(z for _, z in plat), max(z for _, z in plat)
PLATEAU = ((x1, 72, 236), (x2, 95, z2))
area.zone("Mountain", blocks={"minecraft:carrots": {"becomes": "minecraft:carrots", "regrow": 8}}, mobs=[
    {"type": "minecraft:skeleton", "name": "Skeleton", "level": 6, "health": 200, "damage": 40, "xp": 6, "coins": 1,
     "max": 8, "every": 4, "time": "night", "from": list(PLATEAU[0]), "to": list(PLATEAU[1])}])
print("portal", PORTAL[:3], P.name, "balloon", BALLOON[:3], "ledge", LEDGE[:3], "summit door side", H.name, "plateau", PLATEAU)
area.save()
