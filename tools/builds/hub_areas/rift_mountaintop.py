#!/usr/bin/env python3
"""The Rift, the Mountaintop (x -10..199, z -30..59): the Rift's tallest peak (summit y 152 plateau, spire to ~170) rising
from y 68 foothills, with a long switchback / ring trail up its north, east, south and west faces (rope bridges over cracks),
and around it: the Continuum (a plaza of floating time-shards at the foot), the Time Chamber (a sealed clockwork chamber
inside the mountain), the Time-Torn Isles (floating isles east of the peak, joined by jumps), Rose's End (a rose garden
ledge), the Walk of Fame (a wide terrace of statues), the Cerebral Citadel (a temple on the summit plateau with five Shen
alcoves), the Wizardman Bureau (a sealed office on the east shoulder), the Trial Grounds / Wizard Brawl ring, the Vents
(a smoking hill) and, to the east, low foothills and an archipelago of isles reached by rope bridges.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_mountaintop.py

Neighbours: the Village Plaza lies north of x 20 (a rope bridge from its south cliff, y 68), the Photon Pathway north of
x 70..199 (crossing at x 130, land at y 68 up to z -30). West, east and south are the Rift's outer edge.
"""
import math

from kit import hash01, noise
from rift_lib import new_area, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("mountaintop", version=1)
area.shell = 6
S = area.set
G = GROUND                      # 68
X0, X1, Z0, Z1 = -10, 199, -30, 59
PX, PZ = 29, 22                 # the summit plateau's centre (the Citadel)
PY = 152                        # plateau surface block y (players stand at 153)
CROSS_P = CROSSINGS[("village_plaza", "mountaintop")][2]        # 20
CROSS_E = CROSSINGS[("photon_pathway", "mountaintop")][2]       # 130
FACE = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}


# ---------------------------------------------------------------- helpers
def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def jag(a, b, s, k=1.0):
    return k * (5 * noise(a, b, s, 1.2) + 2.5 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5)
                + 1.2 * (hash01(int(a), 2, int(b), s) - 0.5))


def box(x1, y1, z1, x2, y2, z2, block):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                S(x, y, z, block)


def cut(x1, y1, z1, x2, y2, z2):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                area.clear(x, y, z)


def stair(x, y, z, facing, block="quartz_stairs", half="bottom"):
    S(x, y, z, f"minecraft:{block}[facing={facing},half={half}]")


def slab(x, y, z, block="quartz_slab", t="bottom"):
    S(x, y, z, f"minecraft:{block}[type={t}]")


def sign(x, y, z, lines, block="minecraft:dark_oak_wall_sign[facing=south]", color="white", glow=False):
    ox, oy, oz = area.origin
    if area.inside(x, y, z):
        area.piece.sign(x - ox, y - oy, z - oz, lines, block=block, color=color, glow=glow)


def lamp(x, y, z):
    for h in range(0, 3):
        S(x, y + h, z, "minecraft:dark_oak_fence")
    S(x, y + 3, z, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, y + 3, z + dz, "minecraft:purple_stained_glass")
    S(x, y + 4, z, "minecraft:purple_stained_glass")
    S(x, y + 5, z, "minecraft:end_rod[facing=up]")


# ---------------------------------------------------------------- the land's outline
ISLES = [(168, -10, 9), (186, 4, 8), (172, 28, 9), (190, 31, 6), (167, 8, 3), (169, 17, 3), (180, -22, 4)]


def edge(x, z):
    """Distance in blocks to the Rift's outer edge (negative = void)."""
    if x <= 153:
        dw = x - (-7 + jag(z, 0, 1, 0.35))
        ds = (55 + jag(x, 7, 4, 0.5)) - z
        op = sm((x - 104) / 8.0) * (1 - sm((x - 147) / 6.0))      # open to the Photon Pathway for x 112..145
        dn = z - (-23 - 17 * op + jag(x, 5, 3, 0.8) * (1 - op))
        de = (151 + jag(z, 9, 2, 0.6)) - x
        return min(dw, ds, dn, de)
    best = -99.0
    for i, (cx, cz, r) in enumerate(ISLES):
        a = math.atan2(z - cz, x - cx)
        rr = r * (1 + 0.2 * math.sin(3 * a + i) + 0.1 * math.sin(5 * a + 2 * i)) + 0.8 * noise(x, z, i, 1.5)
        best = max(best, rr - math.hypot(x - cx, z - cz))
    dn = z - (-23 + jag(x, 5, 3, 0.8))
    return min(best, dn, 199 - x)


EDGE = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z)
        if e >= 0:
            EDGE[(x, z)] = e


# ---------------------------------------------------------------- the trails
TR = {}                 # (x, z) -> dict(y, d, stair, bridge, kind, i): every walkable trail column
NOTCH = {}              # (x, z) -> y the ground drops to under a rope bridge over a crack
CENTRES = []            # (kind, i, x, z, y, dx, dz, bridge) for each trail cell


def poly_cells(pts):
    out = [pts[0]]
    corners = [0]
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        dx = (x2 > x1) - (x2 < x1)
        dz = (z2 > z1) - (z2 < z1)
        assert dx == 0 or dz == 0, (pts, x1, z1, x2, z2)
        for i in range(1, abs(x2 - x1) + abs(z2 - z1) + 1):
            out.append((x1 + dx * i, z1 + dz * i))
        corners.append(len(out) - 1)
    return out, corners


def make_trail(kind, pts, y0, y1, flat=None, span=None, extras=None, hw=1):
    """Cells along an axis-aligned polyline, y rising from y0 to y1 (stairs where it steps up, flat landings at the corners,
    over rope-bridge spans and where `flat` says so). Registers the columns (3 wide, `extras` adds world offsets)."""
    cells, corners = poly_cells(pts)
    n = len(cells)
    flatf = [False] * n
    for c in corners:
        for i in range(max(0, c - 2), min(n, c + 3)):
            flatf[i] = True
    bridge = [False] * n
    for i, (x, z) in enumerate(cells):
        if (x, z) not in EDGE:
            bridge[i] = True
        if span and span(x, z):
            bridge[i] = True
            flatf[i] = True
        if flat and flat(x, z):
            flatf[i] = True
        if bridge[i]:
            flatf[i] = True
    rising = [i for i in range(1, n) if not flatf[i]]
    rate = (y1 - y0) / max(1, len(rising))
    assert rate <= 1.0, (kind, rate)
    acc, ys = float(y0), [y0]
    for i in range(1, n):
        if not flatf[i]:
            acc += rate
        ys.append(int(round(acc)))
    for i, (x, z) in enumerate(cells):
        a, b = cells[min(i + 1, n - 1)], cells[max(i - 1, 0)]
        d = (a[0] - x, a[1] - z) if i < n - 1 else (x - b[0], z - b[1])
        stair_ = i > 0 and ys[i] > ys[i - 1]
        offs = [(-d[1] * o, d[0] * o) for o in range(-hw, hw + 1)]
        if extras and extras[0](x, z):
            offs += extras[1]
        for ox, oz in offs:
            c = (x + ox, z + oz)
            if c not in TR:
                TR[c] = dict(y=ys[i], d=d, stair=stair_, bridge=bridge[i], kind=kind, i=i)
        CENTRES.append((kind, i, x, z, ys[i], d[0], d[1], bridge[i]))
        if span and span(x, z):
            for o in range(-2, 3):
                NOTCH[(x - d[1] * o, z + d[0] * o)] = ys[i] - 14
    return cells, ys


MAIN = [(20, -30), (20, -12), (54, -12), (54, -6), (8, -6), (8, 0), (58, 0), (58, 46), (1, 46), (1, 12), (12, 12), (12, 6),
        (46, 6), (46, 40), (12, 40), (12, 26), (20, 26)]


def main_flat(x, z):
    return (z < -12 or (x == 1 and 20 <= z <= 34) or (x == 58 and 14 <= z <= 30) or (z == 46 and 25 <= x <= 33)
            or (x == 8 and -7 <= z <= 1))


def main_span(x, z):
    return ((z == -6 and 30 <= x <= 40) or (z == 6 and 22 <= x <= 34) or (x == 46 and 14 <= z <= 26))


def main_extras(x, z):
    return z == 46 and 8 <= x <= 52          # the Walk of Fame: the south leg widens to the south


MAIN_CELLS, MAIN_Y = make_trail("main", MAIN, G, PY, main_flat, main_span,
                                (main_extras, [(0, k) for k in range(2, 6)]))


def ty(x, z):
    return TR[(x, z)]["y"]


YC = ty(29, 46)          # the Time Chamber's door level (the south leg's landing)
YR = ty(1, 27)           # Rose's End
YB = ty(58, 22)          # the Wizardman Bureau's shoulder
YW = ty(8, -3)           # the balloons' ledge
YE = ty(58, 0)           # where the east road meets the trail

EAST = [(CROSS_E, -30), (CROSS_E, 0), (58, 0)]
make_trail("east", EAST, G, YE, lambda x, z: x >= 112 or z < 0, None,
           (lambda x, z: z < -18, [(-2, 0), (2, 0)]))
make_trail("trial", [(120, 0), (120, 6)], G, G)
make_trail("road", [(CROSS_E, -12), (168, -12), (168, -2), (186, -2), (186, 22), (172, 22), (172, 28)], G, G)

# ---------------------------------------------------------------- heights
VENT = (86, -12)
CONT = (20, -12)


def base_height(x, z):
    n = noise(x, z, 21, 1.5) * 0.5 + 0.5
    amp = (4 + 12 * sm((x - 80) / 70.0)) * sm((z + 20) / 22.0)
    h = 68 + amp * n ** 1.4
    d = math.hypot(x - VENT[0], z - VENT[1])
    if d < 17:
        h = max(h, 68 + 23 * (1 - d / 17.0) ** 1.2 * (0.85 + 0.3 * (noise(x, z, 8, 3) * 0.5 + 0.5)))
    return h


# the tent: the mountain is the upper envelope of slopes falling away from every ledge of the trail
W, D = X1 - X0 + 1, Z1 - Z0 + 1
NEG = -1e9
K = 1.55
TENT = [[NEG] * D for _ in range(W)]
for (x, z), t in TR.items():
    if t["kind"] in ("main", "east") and not t["bridge"] and X0 <= x <= X1 and Z0 <= z <= Z1:
        TENT[x - X0][z - Z0] = max(TENT[x - X0][z - Z0], t["y"])
for x in range(16, 43):                                     # the summit plateau
    for z in range(11, 35):
        TENT[x - X0][z - Z0] = PY
for x in range(W):
    for z in range(D):
        v = TENT[x][z]
        for dx, dz, c in ((-1, 0, K), (0, -1, K), (-1, -1, K * 1.41), (1, -1, K * 1.41)):
            a, b = x + dx, z + dz
            if 0 <= a < W and 0 <= b < D:
                v = max(v, TENT[a][b] - c)
        TENT[x][z] = v
for x in range(W - 1, -1, -1):
    for z in range(D - 1, -1, -1):
        v = TENT[x][z]
        for dx, dz, c in ((1, 0, K), (0, 1, K), (1, 1, K * 1.41), (-1, 1, K * 1.41)):
            a, b = x + dx, z + dz
            if 0 <= a < W and 0 <= b < D:
                v = max(v, TENT[a][b] - c)
        TENT[x][z] = v

H = {}
for (x, z), e in EDGE.items():
    t = TENT[x - X0][z - Z0]
    h = max(base_height(x, z), t)
    if t > 72:
        k = min(1.0, (h - 72) / 18.0)
        h += (5.5 * noise(x, z, 31, 0.8) + 3.0 * noise(x, z, 32, 2.2) + 2.0 * (hash01(x, 5, z, 2) - 0.5)) * k
        if hash01(x, 9, z, 3) < 0.01 and h > 90:
            h += 3 + int(hash01(x, 8, z, 4) * 6)
    H[(x, z)] = h

# the trail's ledges: the ground blends into the trail's level within a few blocks
NEAR = {}
R = 5
for (x, z), t in TR.items():
    if t["bridge"]:
        continue
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            d = math.hypot(dx, dz)
            if d > R:
                continue
            c = (x + dx, z + dz)
            if c in H and (c not in NEAR or d < NEAR[c][0]):
                NEAR[c] = (d, t["y"])
for c, (d, y) in NEAR.items():
    if c in TR:
        H[c] = y
    elif d <= 1.01:
        H[c] = y
    else:
        w = sm(1 - (d - 1) / (R - 1.0))
        H[c] = H[c] * (1 - w) + y * w


def platform(x1, z1, x2, z2, y, bl=3, mode="set", disc=None):
    """Levels (or only raises, mode 'max') the ground to y over a rectangle (or disc), blending over `bl` blocks."""
    for x in range(x1 - bl, x2 + bl + 1):
        for z in range(z1 - bl, z2 + bl + 1):
            c = (x, z)
            if c not in H or c in TR:
                continue
            if disc:
                d = max(0.0, math.hypot(x - disc[0], z - disc[1]) - disc[2])
            else:
                d = math.hypot(max(x1 - x, 0, x - x2), max(z1 - z, 0, z - z2))
            if d > bl:
                continue
            w = 1.0 if d == 0 else sm(1 - d / bl)
            new = H[c] * (1 - w) + y * w
            H[c] = max(H[c], new) if mode == "max" else new


platform(CONT[0] - 11, CONT[1] - 11, CONT[0] + 11, CONT[1] + 11, G, 3, disc=(CONT[0], CONT[1], 11))
platform(0, -7, 6, 1, YW, 3)                                    # the balloons' ledge
platform(3, 20, 10, 34, YR, 3)                                  # Rose's End
platform(61, 12, 76, 30, YB, 3)                                 # the Wizardman Bureau's shoulder
platform(16, 11, 42, 34, PY, 3)                                 # the summit plateau
platform(22, 41, 36, 44, YC + 16, 2, mode="max")                # the rock face around the Time Chamber's door
platform(18, 22, 40, 44, YC + 19, 2, mode="max")                # the rock over the chamber
platform(120, 16, 120, 16, G, 3, disc=(120, 16, 13))            # the Trial Grounds
for c, t in NOTCH.items():
    if c in H:
        H[c] = min(H[c], t)
H = {c: int(round(v)) for c, v in H.items()}
for c, t in TR.items():
    if c in H and not t["bridge"]:
        H[c] = t["y"]
for c in H:
    area.top[c] = H[c]

# ---------------------------------------------------------------- what the ground is made of
SL = {}
for (x, z), h in H.items():
    SL[(x, z)] = max(abs(h - H.get((x + dx, z + dz), h)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))

KIND = {}
for (x, z), h in H.items():
    sl = SL[(x, z)]
    n = noise(x, z, 41, 1.3)
    if math.hypot(x - VENT[0], z - VENT[1]) < 16 and h > 69:
        k = "vent"
    elif h >= 136 or (h >= 120 and n > 0.25 and sl <= 3):
        k = "snow" if sl <= 4 else "rock"
    elif h >= 100:
        k = "rock"
    elif h >= 80:
        k = "grass" if (sl <= 1 and n > 0.0) else "rock"
    else:
        k = "grass" if sl <= 3 else "rock"
    KIND[(x, z)] = k

ROCK = area.mix({"andesite": 5, "tuff": 4, "deepslate": 2, "calcite": 1})
ROCKH = area.mix({"tuff": 4, "deepslate": 4, "andesite": 3, "calcite": 2})
CLIFF = area.mix({"deepslate": 4, "tuff": 4, "andesite": 3, "calcite": 1, "smooth_basalt": 1})
DIRT = area.mix({"dirt": 4, "coarse_dirt": 3, "rooted_dirt": 1})
BODY = area.mix({"tuff": 5, "andesite": 4, "deepslate": 4, "calcite": 1})
UNDERSIDE = area.mix({"tuff": 6, "andesite": 2, "deepslate": 4, "calcite": 1, "mud": 1})


def surface(x, y, z):
    k = KIND[(x, z)]
    r = hash01(x, y, z, 3)
    sl = SL[(x, z)]
    if k == "grass":
        if r < 0.07:
            return "minecraft:coarse_dirt"
        if r < 0.1:
            return "minecraft:rooted_dirt"
        return "minecraft:grass_block"
    if k == "snow":
        return "minecraft:snow_block" if r < 0.82 else "minecraft:packed_ice" if r < 0.9 else "minecraft:calcite"
    if k == "vent":
        return ("minecraft:tuff" if r < 0.4 else "minecraft:blackstone" if r < 0.6 else "minecraft:gravel" if r < 0.7
                else "minecraft:coarse_dirt" if r < 0.85 else "minecraft:andesite")
    if sl >= 4:
        return CLIFF(x, y, z)
    return ROCKH(x, y, z) if y >= 110 else ROCK(x, y, z)


def under(x, y, z):
    k = KIND[(x, z)]
    if k == "grass":
        return DIRT(x, y, z)
    if k == "snow":
        return "minecraft:calcite" if hash01(x, y, z, 5) < 0.4 else "minecraft:andesite"
    return CLIFF(x, y, z) if SL[(x, z)] >= 4 else ROCK(x, y, z)


def under_depth(x, z, e):
    t = sm(e / 55.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


N8 = [(dx, dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz]
UD = {c: under_depth(c[0], c[1], e) for c, e in EDGE.items()}
SOLID_RECTS = [(14, 18, 44, 46, YC - 3)]        # columns that must be solid down to y (round the Time Chamber)

for (x, z), top in H.items():
    e = EDGE[(x, z)]
    mn = min((H.get((x + dx, z + dz), 0) for dx, dz in N8))
    bf = max(LAND_BOTTOM, min(top - 11, mn - 11))
    if e < 6:
        bf = LAND_BOTTOM
    for (xa, za, xb, zb, yb) in SOLID_RECTS:
        if xa <= x <= xb and za <= z <= zb:
            bf = min(bf, yb)
    for y in range(top, bf - 1, -1):
        if y == top:
            S(x, y, z, surface(x, y, z))
        elif y >= top - 3:
            S(x, y, z, under(x, y, z))
        else:
            S(x, y, z, CLIFF(x, y, z) if SL[(x, z)] >= 4 and y > top - 8 else BODY(x, y, z))
    # the hanging underside (visible faces only)
    d = UD[(x, z)]
    nb = [UD.get((x + dx, z + dz), 0) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
    ftop = max(LAND_BOTTOM - d + 2, LAND_BOTTOM - 1 - min(nb))
    for k in range(LAND_BOTTOM - d, ftop + 1):
        r = hash01(x, k, z, 12)
        if e < 14 and r < 0.04:
            b = "minecraft:crying_obsidian"
        elif r < 0.035:
            b = "minecraft:amethyst_block"
        else:
            b = UNDERSIDE(x, k, z)
        S(x, k, z, b)
    if e < 30 and hash01(x, 10, z, 13) < 0.012:
        ln = 3 + int(hash01(x, 11, z, 14) * 8)
        yy = LAND_BOTTOM - d
        for i in range(ln):
            S(x, yy - 1 - i, z, "minecraft:crying_obsidian" if hash01(x, i, z, 15) < 0.4 else "minecraft:amethyst_block")
        S(x, yy - 1 - ln, z, "minecraft:amethyst_cluster[facing=down]")

# a few floating shards beyond the cliffs (deliberate)
for i in range(60):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 6))
    if x > 120 and z < -20:
        continue
    if not (-16 < edge(x, z) < -3):
        continue
    y = 50 + int(hash01(i, 2, 0, 77) * 40)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    b = "minecraft:tuff" if q < 0.7 else "minecraft:andesite"
                    if hash01(x + dx, y + dy, z + dz, 5) < 0.08:
                        b = "minecraft:crying_obsidian"
                    S(x + dx, y + dy, z + dz, b)
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:amethyst_block" if k % 2 == 0 else "minecraft:crying_obsidian")

# ---------------------------------------------------------------- trail surfaces, rope bridges, lamps
PATH_LOW = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3, "packed_mud": 1})
PATH_MID = area.mix({"andesite": 4, "polished_andesite": 3, "gravel": 1, "coarse_dirt": 1})
PATH_HI = area.mix({"polished_tuff": 4, "tuff_bricks": 4, "polished_andesite": 2})
DECK = area.mix({"dark_oak_planks": 5, "spruce_planks": 3, "oak_planks": 1})


def path_family(y):
    if y < 80:
        return PATH_LOW, "mud_brick_stairs"
    if y < 124:
        return PATH_MID, "andesite_stairs"
    return PATH_HI, "tuff_brick_stairs"


for (x, z), t in sorted(TR.items()):
    y = t["y"]
    if t["bridge"]:
        S(x, y, z, DECK(x, y, z))
        continue
    fam, st = path_family(y)
    if t["stair"]:
        stair(x, y, z, FACE[t["d"]], st)
        S(x, y - 1, z, fam(x, y - 1, z))
    else:
        S(x, y, z, fam(x, y, z))
    for h in range(1, 5):
        area.clear(x, y + h, z)

rails = set()
lamps = 0
for kind, i, x, z, y, dx, dz, br in CENTRES:
    if br:
        for o in (-2, 2):
            rx, rz = x - dz * o, z + dx * o
            S(rx, y, rz, "minecraft:dark_oak_fence")
            S(rx, y + 1, rz, "minecraft:dark_oak_fence")
            rails.add((rx, rz))
            if i % 4 == 0:
                S(rx, y + 1, rz, "minecraft:dark_oak_log[axis=y]")
                S(rx, y + 2, rz, "minecraft:dark_oak_fence")
                if i % 8 == 0:
                    S(rx, y + 3, rz, "minecraft:lantern[hanging=false]")
        if i % 3 == 0:
            for c in range(1, 5):
                S(x, y - c, z, "minecraft:iron_chain[axis=y]")
    elif i % 18 == 9 and kind in ("main", "east") and not (kind == "main" and z > 44) and (x, z) not in NOTCH:
        # lamps on the trail's edge lane, only on level ground
        o = 1
        lx, lz = x - dz * o, z + dx * o
        t = TR.get((lx, lz))
        if t and not t["stair"] and not t["bridge"] and t["y"] == y:
            lamp(lx, y + 1, lz)
            lamps += 1
# the rope bridges' ends: posts with lanterns
for kind, i, x, z, y, dx, dz, br in CENTRES:
    pass

# ---------------------------------------------------------------- the Plaza bridge's gate
first = next(c for c in CENTRES if c[0] == "main" and not c[7])
gz = first[3] + 1
for gx in (CROSS_P - 3, CROSS_P + 3):
    for h in range(0, 6):
        S(gx, G + 1 + h, gz, "minecraft:tuff_bricks" if h < 5 else "minecraft:chiseled_tuff")
    S(gx, G + 1, gz, "minecraft:polished_tuff")
for gx in range(CROSS_P - 3, CROSS_P + 4):
    S(gx, G + 7, gz, "minecraft:tuff_brick_slab[type=bottom]")
    if abs(gx - CROSS_P) <= 2:
        S(gx, G + 6, gz, "minecraft:tuff_brick_slab[type=top]")
S(CROSS_P, G + 5, gz, "minecraft:lantern[hanging=true]")
S(CROSS_P - 3, G + 8, gz, "minecraft:end_rod[facing=up]")
S(CROSS_P + 3, G + 8, gz, "minecraft:end_rod[facing=up]")
sign(CROSS_P + 1, G + 4, gz - 1, ["THE", "MOUNTAINTOP"], "minecraft:dark_oak_wall_sign[facing=north]", glow=True)

# ---------------------------------------------------------------- the Continuum (a plaza of floating time-shards)
cx, cz = CONT
for dx in range(-11, 12):
    for dz in range(-11, 12):
        r = math.hypot(dx, dz)
        x, z = cx + dx, cz + dz
        if (x, z) not in H or (x, z) in TR or r > 10.5:
            continue
        b = None
        if r < 1.6:
            b = "minecraft:crying_obsidian"
        elif 2.5 <= r < 3.4:
            b = "minecraft:amethyst_block" if (dx + dz) % 2 else "minecraft:purple_concrete"
        elif 5.5 <= r < 6.3:
            b = "minecraft:chiseled_deepslate" if hash01(x, 1, z, 3) < 0.5 else "minecraft:polished_blackstone_bricks"
        elif 8.6 <= r < 9.4:
            a = math.degrees(math.atan2(dz, dx)) % 30
            b = "minecraft:copper_block" if a < 7 or a > 23 else "minecraft:deepslate_tiles"
        elif r < 10.4:
            b = "minecraft:deepslate_tiles" if hash01(x, 2, z, 3) < 0.8 else "minecraft:cracked_deepslate_tiles"
        if b:
            S(x, G, z, b)
            for h in range(1, 5):
                area.clear(x, G + h, z)


def shard(x, y, z, r):
    for dx in range(-r, r + 1):
        for dy in range(-int(r * 1.8), int(r * 1.8) + 1):
            for dz in range(-r, r + 1):
                q = abs(dx) + abs(dz) + abs(dy) / 1.8
                if q <= r + 0.2:
                    S(x + dx, y + dy, z + dz, "minecraft:amethyst_block" if q <= r - 1.2 else "minecraft:purple_stained_glass")
    S(x, y - int(r * 1.8) - 1, z, "minecraft:amethyst_cluster[facing=down]")


for k in range(14):
    a = k * 2 * math.pi / 14 + 0.3
    rr = 6.5 + 4 * hash01(k, 1, 0, 9)
    shard(int(round(cx + rr * math.cos(a))), 74 + int(hash01(k, 2, 0, 9) * 22), int(round(cz + rr * math.sin(a))),
          2 if k % 3 else 3)
# a spiral of floating plates round the middle: the Ubik soul sits on the top one
SPIRAL = []
for k in range(8):
    a = k * math.pi / 4 + 0.4
    px, pz, py = int(round(cx + 4.2 * math.cos(a))), int(round(cz + 4.2 * math.sin(a))), G + 1 + k
    SPIRAL.append((px, pz, py))
    for dx in (0, 1):
        for dz in (0, 1):
            S(px + dx, py, pz + dz, "minecraft:amethyst_block" if (dx + dz) % 2 == 0 else "minecraft:purple_concrete")
    S(px, py + 1, pz, "minecraft:end_rod[facing=up]") if k % 2 == 0 else None
for y in range(G + 1, G + 13):
    S(cx, y, cz, "minecraft:quartz_pillar[axis=y]")
S(cx, G + 13, cz, "minecraft:sea_lantern")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    S(cx + dx, G + 13, cz + dz, "minecraft:purple_stained_glass")
S(cx, G + 14, cz, "minecraft:end_rod[facing=up]")
UBIK_SOUL = (SPIRAL[-1][0], SPIRAL[-1][2] + 1, SPIRAL[-1][1])
for x, z in ((cx - 12, cz - 3), (cx + 12, cz + 3), (cx - 3, cz + 12)):
    if (x, z) in H and (x, z) not in TR:
        lamp(x, H[(x, z)] + 1, z)

# ---------------------------------------------------------------- the balloons' ledge
bx, bz = 2, -3
for dx in range(-2, 3):
    for dz in range(-3, 4):
        if (bx + dx, bz + dz) in H and (bx + dx, bz + dz) not in TR:
            S(bx + dx, YW, bz + dz, "minecraft:polished_andesite" if (dx + dz) % 2 else "minecraft:andesite")
for h in range(1, 4):
    S(bx, YW + h, bz, "minecraft:dark_oak_log[axis=y]")
S(bx, YW + 4, bz, "minecraft:lantern[hanging=false]")
BALLOONS = [("red_wool", (0, 12, -5)), ("yellow_wool", (4, 15, -1)), ("lime_wool", (1, 17, -2)), ("light_blue_wool", (-2, 14, 0)),
            ("purple_wool", (3, 19, -6)), ("orange_wool", (5, 11, -4)), ("pink_wool", (-1, 20, -3))]
for name, (ox, oy, oz) in BALLOONS:
    ax, ay, az = bx + ox, YW + oy, bz + oz
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dy * dy * 1.2 + dz * dz <= 5.2:
                    S(ax + dx, ay + dy, az + dz, f"minecraft:{name}")
    for y in range(YW + 5, ay - 2):
        S(ax, y, az, "minecraft:iron_chain[axis=y]")
BALLOON_SOUL = (bx + 1, YW + 1, bz + 2)

# ---------------------------------------------------------------- Rose's End
rx0, rx1, rz0, rz1 = 3, 10, 20, 34
for x in range(rx0, rx1 + 1):
    for z in range(rz0, rz1 + 1):
        if (x, z) in TR or (x, z) not in H:
            continue
        S(x, YR, z, "minecraft:coarse_dirt" if x == 6 or (x in (5, 7) and z % 7 == 0) else "minecraft:grass_block")
        for h in range(1, 6):
            area.clear(x, YR + h, z)
        S(x, YR - 1, z, "minecraft:dirt")
PETAL = "minecraft:pink_petals[flower_amount=2,facing=north]"
for x in range(rx0, rx1 + 1):
    for z in range(rz0, rz1 + 1):
        if x == 6 or (x, z) in TR or abs(x - 6) <= 1 and abs(z - 27) <= 2:
            continue
        r = hash01(x, 1, z, 61)
        if r < 0.3:
            S(x, YR + 1, z, "minecraft:rose_bush[half=lower]")
            S(x, YR + 2, z, "minecraft:rose_bush[half=upper]")
        elif r < 0.42:
            S(x, YR + 1, z, "minecraft:peony[half=lower]")
            S(x, YR + 2, z, "minecraft:peony[half=upper]")
        elif r < 0.55:
            S(x, YR + 1, z, "minecraft:red_tulip")
        elif r < 0.65:
            S(x, YR + 1, z, "minecraft:poppy")
        elif r < 0.75:
            S(x, YR + 1, z, "minecraft:pink_tulip")
        elif r < 0.82:
            S(x, YR + 1, z, PETAL)
        elif r < 0.88:
            S(x, YR + 1, z, "minecraft:flowering_azalea")
        else:
            S(x, YR + 1, z, "minecraft:allium")
# a gazebo over the garden's middle
for gx, gz2 in ((4, 25), (8, 25), (4, 29), (8, 29)):
    for h in range(1, 5):
        S(gx, YR + h, gz2, "minecraft:cherry_log[axis=y]")
for x in range(3, 10):
    for z in range(24, 31):
        S(x, YR + 5, z, "minecraft:cherry_slab[type=bottom]") if (x, z) in H else None
for x, z in ((6, 27),):
    S(x, YR + 1, z, "minecraft:cherry_fence")
    S(x, YR + 2, z, "minecraft:lantern[hanging=false]")
for x, z in ((4, 27), (8, 27), (6, 25), (6, 29)):
    stair(x, YR + 1, z, {4: "east", 8: "west"}.get(x, "south" if z == 25 else "north"), "cherry_stairs")
for x, z in ((3, 20), (10, 20), (3, 34), (10, 34)):
    for h in range(1, 4):
        S(x, YR + h, z, "minecraft:cherry_fence")
    S(x, YR + 4, z, "minecraft:lantern[hanging=false]")
for x in range(rx0, rx1 + 1):                       # the cliff side of the garden: a low wall and hanging petals
    S(x, YR + 1, rz1 + 1, "minecraft:cherry_fence") if (x, rz1 + 1) in H and (x, rz1 + 1) not in TR else None
ROSE_SOUL = (9, YR + 1, 21)
for dx, dz in ((0, 0), (-1, 0), (0, -1)):
    S(9 + dx, YR + 1, 21 + dz, "minecraft:air") if False else None
area.clear(9, YR + 1, 21)
area.clear(9, YR + 2, 21)
area.clear(8, YR + 1, 21)
area.clear(9, YR + 1, 22)

# ---------------------------------------------------------------- the Walk of Fame (south terrace, statues)
HEADS = ["skeleton_skull", "zombie_head", "creeper_head", "piglin_head", "wither_skeleton_skull", "player_head"]
statues = []
for k, sx in enumerate(range(10, 53, 7)):
    sy = ty(sx, 46)
    sz = 50
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            S(sx + dx, sy + 1, sz + dz, "minecraft:polished_deepslate")
    S(sx, sy + 2, sz, "minecraft:deepslate_brick_wall")
    S(sx, sy + 3, sz, "minecraft:deepslate_brick_wall")
    S(sx, sy + 4, sz, ["minecraft:chiseled_quartz_block", "minecraft:polished_andesite", "minecraft:copper_block",
                       "minecraft:chiseled_deepslate"][k % 4])
    S(sx - 1, sy + 4, sz, "minecraft:deepslate_brick_wall")
    S(sx + 1, sy + 4, sz, "minecraft:deepslate_brick_wall")
    S(sx, sy + 5, sz, f"minecraft:{HEADS[k % len(HEADS)]}[rotation={(k * 5) % 16}]")
    S(sx - 1, sy + 1, sz - 2, "minecraft:lantern[hanging=false]") if False else None
    statues.append((sx, sy, sz))
for x in range(9, 55, 7):
    sy = ty(min(max(x, 8), 52), 46)
    S(x, sy + 1, 52, "minecraft:lantern[hanging=false]") if (x, 52) in H else None
# the plinth with the soul (a low step up) and Detective Amog's corner
SOUL_WOF = (13, ty(13, 46) + 2, 49)
S(13, ty(13, 46) + 1, 49, "minecraft:polished_deepslate")
S(13, ty(13, 46) + 1, 50, "minecraft:polished_deepslate")
AMOG = (44, ty(44, 46) + 1, 48)
for x in range(7, 54):
    sy = ty(min(max(x, 8), 52), 46)
    for zz in (51,):
        if (x, zz) in TR:
            S(x, sy + 1, zz, "minecraft:deepslate_brick_wall")
sign(30, ty(30, 46) + 2, 51, ["WALK OF", "FAME"], "minecraft:dark_oak_sign[rotation=0]", glow=True) if False else None

# ---------------------------------------------------------------- the Time Chamber (inside the mountain, door at the south landing)
CX0, CX1, CZ0, CZ1 = 18, 40, 20, 42
box(CX0, YC, CZ0, CX1, YC + 15, CZ1, "minecraft:deepslate_bricks")
cut(CX0 + 1, YC + 1, CZ0 + 1, CX1 - 1, YC + 14, CZ1 - 1)
CC = ((CX0 + CX1) // 2, 0)       # centre x 29
# floor: tiles with concentric copper gear rings
fcx, fcz = 29, 31
for x in range(CX0 + 1, CX1):
    for z in range(CZ0 + 1, CZ1):
        r = math.hypot(x - fcx, z - fcz)
        b = "minecraft:polished_deepslate" if (x + z) % 2 else "minecraft:deepslate_tiles"
        if r < 1.5:
            b = "minecraft:chiseled_copper"
        elif 4.5 <= r < 5.5:
            b = "minecraft:cut_copper" if hash01(x, 1, z, 3) < 0.5 else "minecraft:exposed_cut_copper"
        elif 8.5 <= r < 9.5:
            ang = math.degrees(math.atan2(z - fcz, x - fcx)) % 45
            b = "minecraft:weathered_cut_copper" if ang < 12 else "minecraft:oxidized_cut_copper"
        elif r < 8.5 and abs((math.degrees(math.atan2(z - fcz, x - fcx)) + 360) % 60) < 5 and r > 1.5:
            b = "minecraft:redstone_block"
        S(x, YC, z, b)
# the big clock face on the north wall
fx, fy, fz = 29, YC + 8, CZ0 + 1
for x in range(fx - 8, fx + 9):
    for y in range(fy - 8, fy + 9):
        r = math.hypot(x - fx, (y - fy))
        if r <= 7.4:
            S(x, y, fz, "minecraft:quartz_block" if r < 6.4 else "minecraft:copper_block" if r < 7.0 else "minecraft:black_concrete")
for k in range(12):
    a = k * math.pi / 6
    for rr in ((5.4, 6.2) if k % 3 else (4.6, 5.4, 6.2)):
        S(int(round(fx + rr * math.sin(a))), int(round(fy + rr * math.cos(a))), fz, "minecraft:black_concrete")
for rr in range(0, 7):                      # the hands (minute hand toward 12:10, hour hand toward 4)
    S(int(round(fx + rr * 0.17)), int(round(fy + rr * 0.98)), fz + 1, "minecraft:copper_block")
for rr in range(0, 4):
    S(int(round(fx + rr * 0.87)), int(round(fy - rr * 0.5)), fz + 1, "minecraft:cut_copper")
S(fx, fy, fz + 1, "minecraft:gold_block")
# gears on the side walls (rings of copper with redstone spokes)


def gear(plane_x, gz, gy, R_, flip):
    for dz in range(-R_ - 1, R_ + 2):
        for dy in range(-R_ - 1, R_ + 2):
            r = math.hypot(dz, dy)
            x = plane_x
            z, y = gz + dz, gy + dy
            if R_ - 1.0 <= r <= R_ + 0.4:
                teeth = (int(math.degrees(math.atan2(dy, dz)) + 360) // 15) % 2 == 0
                S(x, y, z, "minecraft:cut_copper" if teeth else "minecraft:exposed_cut_copper")
                if teeth and r > R_ - 0.3:
                    S(x + flip, y, z, "minecraft:copper_block")
            elif r < 1.2:
                S(x, y, z, "minecraft:chiseled_copper")
            elif r < R_ - 1.0 and (abs(dz) <= 0 or abs(dy) <= 0 or abs(abs(dz) - abs(dy)) <= 0):
                S(x, y, z, "minecraft:redstone_block" if (dz + dy) % 2 == 0 else "minecraft:copper_grate")


gear(CX0 + 1, 31, YC + 8, 6, 1)
gear(CX0 + 1, 24, YC + 6, 3, 1)
gear(CX1 - 1, 31, YC + 8, 6, -1)
gear(CX1 - 1, 38, YC + 8, 3, -1)
gear(CX1 - 1, 25, YC + 10, 4, -1)
# ceiling: copper bulbs and lanterns, a pendulum over the middle
for x in range(CX0 + 2, CX1 - 1, 4):
    for z in range(CZ0 + 3, CZ1 - 1, 4):
        S(x, YC + 14, z, "minecraft:copper_bulb[lit=true]")
for x in range(CX0 + 1, CX1):
    for z in range(CZ0 + 1, CZ1):
        if (x + z) % 3 == 0 and hash01(x, 1, z, 7) < 0.5:
            S(x, YC + 14, z, "minecraft:copper_grate")
for y in range(YC + 7, YC + 14):
    S(29, y, 31, "minecraft:iron_chain[axis=y]")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        for dy in (6, 7):
            S(29 + dx, YC + dy, 31 + dz, "minecraft:copper_block" if dx or dz or dy == 7 else "minecraft:gold_block")
# pillars and braziers along the walls
for z in (CZ0 + 6, CZ0 + 12, CZ0 + 18):
    for x in (CX0 + 3, CX1 - 3):
        for y in range(YC + 1, YC + 5):
            S(x, y, z, "minecraft:polished_deepslate_wall" if y < YC + 4 else "minecraft:copper_block")
        S(x, YC + 5, z, "minecraft:lantern[hanging=false]")
# the door: an arch through the rock to the landing (the rock face is at z 44, the trail's lane starts at z 45)
cut(28, YC + 1, CZ1, 30, YC + 4, 44)
for z in range(CZ1, 45):
    for x in (27, 31):
        for y in range(YC, YC + 6):
            S(x, y, z, "minecraft:deepslate_bricks")
    for x in range(27, 32):
        S(x, YC + 5, z, "minecraft:deepslate_bricks")
    for x in (28, 29, 30):
        S(x, YC, z, "minecraft:deepslate_tiles")
for y in range(YC, YC + 5):
    S(27, y, 44, "minecraft:chiseled_copper")
    S(31, y, 44, "minecraft:chiseled_copper")
for x in range(27, 32):
    S(x, YC + 5, 44, "minecraft:chiseled_copper")
S(28, YC + 4, 44, "minecraft:lantern[hanging=true]")
S(30, YC + 4, 44, "minecraft:lantern[hanging=true]")
sign(32, YC + 3, 45, ["TIME", "CHAMBER"], "minecraft:dark_oak_wall_sign[facing=south]", glow=True)
S(29, YC + 4, CZ1, "minecraft:copper_bulb[lit=true]")

# ---------------------------------------------------------------- the Cerebral Citadel (summit plateau)
BX0, BX1, BZ0, BZ1 = 19, 39, 14, 31
for x in range(BX0 - 2, BX1 + 3):
    for z in range(BZ0 - 2, BZ1 + 3):
        if 16 <= x <= 42 and 11 <= z <= 34:
            S(x, PY, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks")
            for h in range(1, 12):
                area.clear(x, PY + h, z)
for x in range(BX0, BX1 + 1):
    for z in range(BZ0, BZ1 + 1):
        wall = x in (BX0, BX1) or z in (BZ0, BZ1)
        for y in range(PY, PY + 10):
            if y == PY:
                S(x, y, z, "minecraft:quartz_bricks" if wall else "minecraft:chiseled_quartz_block" if (x + z) % 2 else "minecraft:smooth_quartz")
            elif y == PY + 9:
                S(x, y, z, "minecraft:quartz_block" if (x + z) % 3 else "minecraft:smooth_quartz")
            elif wall:
                S(x, y, z, "minecraft:quartz_bricks" if y % 4 else "minecraft:chiseled_quartz_block")
            else:
                area.clear(x, y, z)
for x, z in ((BX0, BZ0), (BX1, BZ0), (BX0, BZ1), (BX1, BZ1)):
    for y in range(PY + 1, PY + 10):
        S(x, y, z, "minecraft:quartz_pillar[axis=y]")
# the north wall is thick: five alcoves (z 15..17) for the five Shen
for x in range(BX0 + 1, BX1):
    for z in range(BZ0 + 1, BZ0 + 4):
        for y in range(PY + 1, PY + 9):
            S(x, y, z, "minecraft:quartz_bricks" if y % 4 else "minecraft:chiseled_quartz_block")
SHEN = [("Shen Albedo", 21, "white_concrete", "calcite"), ("Shen Chaos", 25, "black_concrete", "crying_obsidian"),
        ("Shen Citrinitas", 29, "yellow_concrete", "raw_gold_block"), ("Shen Magnum Opus", 33, "orange_concrete", "gold_block"),
        ("Shen Rubedo", 37, "red_concrete", "red_nether_bricks")]
for name, c, conc, acc in SHEN:
    cut(c - 1, PY + 1, 15, c + 1, PY + 5, 17)
    for x in range(c - 1, c + 2):
        for z in range(15, 18):
            S(x, PY, z, f"minecraft:{conc}")
        S(x, PY + 1, 14, f"minecraft:{conc}")
        S(x, PY + 2, 14, f"minecraft:{acc}")
        S(x, PY + 3, 14, f"minecraft:{conc}")
        S(x, PY + 5, 15, "minecraft:quartz_slab[type=top]") if False else None
    for x in (c - 1, c + 1):
        S(x, PY + 4, 14, f"minecraft:{acc}")
    S(c, PY + 4, 14, f"minecraft:{conc}")
    S(c - 1, PY + 4, 16, "minecraft:lantern[hanging=true]") if False else None
    S(c, PY + 5, 16, "minecraft:lantern[hanging=true]") if False else None
    cut(c, PY + 5, 16, c, PY + 5, 16)
    S(c, PY + 5, 16, "minecraft:quartz_bricks")
    S(c, PY + 4, 16, "minecraft:lantern[hanging=true]")
    for x in (c - 1, c + 1):
        stair(x, PY + 4, 17, "south", "quartz_stairs", "top")
    S(c, PY + 4, 17, "minecraft:quartz_slab[type=top]")
# the hall: pillars, a mosaic for the Citadel puzzle, windows, hanging lamps
for x in (23, 27, 31, 35):
    for z in (21, 27):
        for y in range(PY + 1, PY + 9):
            S(x, y, z, "minecraft:quartz_pillar[axis=y]")
        S(x, PY + 1, z, "minecraft:chiseled_quartz_block")
mx, mz = 29, 24
for dx in range(-4, 5):
    for dz in range(-4, 5):
        r = abs(dx) + abs(dz)
        if r <= 4:
            S(mx + dx, PY, mz + dz, ["minecraft:white_concrete", "minecraft:black_concrete", "minecraft:yellow_concrete",
                                     "minecraft:orange_concrete", "minecraft:red_concrete"][r % 5])
S(mx, PY, mz, "minecraft:amethyst_block")
for z in (19, 23, 27):
    for x in (BX0, BX1):
        for y in range(PY + 3, PY + 7):
            for dz in (0, 1):
                S(x, y, z + dz, "minecraft:purple_stained_glass")
for x, z in ((24, 24), (34, 24), (29, 20), (29, 28)):
    for y in range(PY + 7, PY + 9):
        S(x, y, z, "minecraft:iron_chain[axis=y]")
    S(x, PY + 6, z, "minecraft:lantern[hanging=true]")
# the doorway (south), steps and a colonnade
cut(28, PY + 1, BZ1, 30, PY + 5, BZ1)
for y in range(PY + 1, PY + 6):
    S(27, y, BZ1, "minecraft:quartz_pillar[axis=y]")
    S(31, y, BZ1, "minecraft:quartz_pillar[axis=y]")
for x in range(27, 32):
    S(x, PY + 6, BZ1, "minecraft:chiseled_quartz_block")
for x in (21, 25, 33, 37):
    for y in range(PY + 1, PY + 8):
        S(x, y, BZ1 + 2, "minecraft:quartz_pillar[axis=y]")
    S(x, PY + 8, BZ1 + 2, "minecraft:chiseled_quartz_block")
for x in range(20, 39):
    slab(x, PY + 9, BZ1 + 2, "quartz_slab")
for x in (23, 27, 31, 35):
    S(x, PY + 1, BZ1 + 2, "minecraft:lantern[hanging=false]")
# the way to the roof: stairs along the east wall
for k in range(1, 10):
    y, z = PY + k, 32 - k
    for x in (40, 41):
        for yy in range(PY + 1, y):
            S(x, yy, z, "minecraft:quartz_bricks")
        stair(x, y, z, "north", "quartz_stairs")
# the roof terrace: a parapet, the Porhtal eye (The 7th Sin) and the summit podium
for x in range(BX0, BX1 + 1):
    for z in range(BZ0, BZ1 + 1):
        if x in (BX0, BX1) or z in (BZ0, BZ1):
            if not (x == BX1 and 22 <= z <= 25):
                slab(x, PY + 10, z, "quartz_slab")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        S(29 + dx, PY + 9, 17 + dz, "minecraft:crying_obsidian" if dx or dz else "minecraft:polished_blackstone")
S(29, PY + 10, 17, "minecraft:end_portal_frame[eye=true,facing=south]")
for dx, dz in ((2, 0), (-2, 0), (0, -2)):
    S(29 + dx, PY + 10, 17 + dz, "minecraft:end_rod[facing=up]")
# the summit podium: two steps and a spire
for dx in range(-2, 3):
    for dz in range(-2, 3):
        S(29 + dx, PY + 10, 22 + dz, "minecraft:smooth_quartz")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        S(29 + dx, PY + 11, 22 + dz, "minecraft:quartz_block")
for dx, dz, f in ((0, 3, "north"), (0, -3, "south"), (3, 0, "west"), (-3, 0, "east")):
    pass
for dx in (-1, 0, 1):
    stair(29 + dx, PY + 10, 25, "north", "quartz_stairs")
for dz in (-1, 0, 1):
    stair(26, PY + 10, 22 + dz, "east", "quartz_stairs")
    stair(32, PY + 10, 22 + dz, "west", "quartz_stairs")
for y in range(PY + 12, PY + 19):
    S(29, y, 22, "minecraft:quartz_pillar[axis=y]" if y < PY + 17 else "minecraft:chiseled_quartz_block")
S(29, PY + 19, 22, "minecraft:sea_lantern")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    S(29 + dx, PY + 19, 22 + dz, "minecraft:purple_stained_glass")
S(29, PY + 20, 22, "minecraft:end_rod[facing=up]")
# SUMMIT SOUL stands on a podium step beside the spire
SUMMIT_SOUL = (31, PY + 12, 22)
area.clear(31, PY + 12, 22)
for z in (14, 31):
    for x in range(BX0 + 2, BX1 - 1, 4):
        S(x, PY + 10, z, "minecraft:end_rod[facing=up]") if False else None

# ---------------------------------------------------------------- the Wizardman Bureau (sealed office on the east shoulder)
WX0, WX1, WZ0, WZ1 = 63, 73, 15, 25
for x in range(WX0, WX1 + 1):
    for z in range(WZ0, WZ1 + 1):
        wall = x in (WX0, WX1) or z in (WZ0, WZ1)
        for y in range(YB, YB + 8):
            if y == YB:
                S(x, y, z, "minecraft:polished_blackstone_bricks")
            elif y == YB + 7:
                S(x, y, z, "minecraft:polished_blackstone")
            elif wall:
                S(x, y, z, "minecraft:polished_blackstone_bricks" if y != YB + 4 else "minecraft:chiseled_polished_blackstone")
            else:
                area.clear(x, y, z)
for z in range(WZ0 + 2, WZ1 - 1, 3):
    for x in (WX0, WX1):
        for y in (YB + 2, YB + 3):
            S(x, y, z, "minecraft:tinted_glass")
for x in range(WX0 + 2, WX1 - 1, 3):
    for z in (WZ0, WZ1):
        for y in (YB + 2, YB + 3):
            S(x, y, z, "minecraft:tinted_glass")
for x in range(WX0 - 1, WX1 + 2):                         # roof rim
    for z in range(WZ0 - 1, WZ1 + 2):
        if x in (WX0 - 1, WX1 + 1) or z in (WZ0 - 1, WZ1 + 1):
            slab(x, YB + 7, z, "polished_blackstone_slab")
# the locked door (west, facing the trail)
for y in (YB + 1, YB + 2):
    area.clear(WX0, y, 20)
S(WX0, YB + 1, 20, "minecraft:iron_door[facing=west,half=lower,hinge=left,open=false,powered=false]")
S(WX0, YB + 2, 20, "minecraft:iron_door[facing=west,half=upper,hinge=left,open=false,powered=false]")
for z in (19, 21):
    for y in (YB + 1, YB + 2, YB + 3):
        S(WX0 - 1, y, z, "minecraft:iron_bars")
S(WX0 - 1, YB + 3, 20, "minecraft:iron_bars")
S(WX0 - 1, YB + 4, 20, "minecraft:lantern[hanging=true]")
sign(WX0 - 1, YB + 2, 20, ["WIZARDMAN", "BUREAU", "CLOSED"], "minecraft:dark_oak_wall_sign[facing=west]", glow=True) if False else None
sign(WX0 - 1, YB + 5, 20, ["WIZARDMAN BUREAU"], "minecraft:dark_oak_wall_sign[facing=west]", glow=True)
for x in range(WX0 + 1, WX1):
    S(x, YB + 1, WZ1 - 1, "minecraft:bookshelf")
    S(x, YB + 2, WZ1 - 1, "minecraft:bookshelf") if x % 2 else None
S(68, YB + 1, 19, "minecraft:lectern[facing=west]")
S(69, YB + 1, 20, "minecraft:cartography_table")
S(70, YB + 1, 17, "minecraft:barrel[facing=up]")
for x, z in ((65, 17), (65, 23), (71, 17), (71, 23)):
    S(x, YB + 6, z, "minecraft:lantern[hanging=true]")
# lamps along the shoulder
for x, z in ((61, 13), (61, 29), (75, 13), (75, 29)):
    lamp(x, YB + 1, z)

# ---------------------------------------------------------------- the Time-Torn Isles
TT = [(82, 22, 1, 4), (89, 16, 2, 4), (96, 21, 3, 3), (102, 26, 4, 3), (108, 21, 5, 3), (114, 15, 6, 3), (121, 21, 7, 5)]


def float_isle(cx_, cz_, y, r, seed):
    for dx in range(-r - 2, r + 3):
        for dz in range(-r - 2, r + 3):
            a = math.atan2(dz, dx)
            rr = r * (1 + 0.15 * math.sin(3 * a + seed))
            d = math.hypot(dx, dz)
            if d > rr:
                continue
            depth = 2 + int((r * 1.4) * (1 - (d / rr) ** 2) ** 0.8 + 1.5 * hash01(cx_ + dx, 3, cz_ + dz, seed))
            S(cx_ + dx, y, cz_ + dz, "minecraft:moss_block" if hash01(cx_ + dx, 1, cz_ + dz, seed) < 0.6 else "minecraft:calcite")
            for k in range(1, depth + 1):
                b = ("minecraft:tuff" if k < 3 else "minecraft:andesite" if hash01(cx_ + dx, k, cz_ + dz, seed) < 0.6
                     else "minecraft:deepslate")
                if hash01(cx_ + dx, k, cz_ + dz, seed + 1) < 0.05:
                    b = "minecraft:amethyst_block"
                S(cx_ + dx, y - k, cz_ + dz, b)
    S(cx_, y - int(r * 1.4) - 3, cz_, "minecraft:amethyst_cluster[facing=down]")


for k, (ix, iz, dy, ir) in enumerate(TT):
    iy = YB + dy
    float_isle(ix, iz, iy, ir, 40 + k)
    # a small clockwork piece on each isle: a cog of copper on a post, and an end rod
    if k < len(TT) - 1:
        S(ix, iy + 1, iz, "minecraft:chiseled_copper")
        S(ix, iy + 2, iz, "minecraft:copper_grate")
        S(ix, iy + 3, iz, "minecraft:end_rod[facing=up]")
    for dx, dz in ((ir - 1, 0), (-(ir - 1), 1)):
        S(ix + dx, iy + 1, iz + dz, "minecraft:amethyst_cluster[facing=up]")
LAST = TT[-1]
LY = YB + LAST[2]
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) + abs(dz) <= 3:
            S(LAST[0] + dx, LY, LAST[1] + dz, "minecraft:chiseled_deepslate" if (dx + dz) % 2 else "minecraft:polished_blackstone_bricks")
for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    for h in range(1, 4):
        S(LAST[0] + dx, LY + h, LAST[1] + dz, "minecraft:polished_deepslate_wall") if False else S(LAST[0] + dx, LY + h, LAST[1] + dz, "minecraft:deepslate_brick_wall")
    S(LAST[0] + dx, LY + 4, LAST[1] + dz, "minecraft:sea_lantern")
for y in range(LY + 1, LY + 4):
    area.clear(LAST[0], y, LAST[1])
S(LAST[0], LY + 1, LAST[1], "minecraft:crying_obsidian")
TT_SOUL = (LAST[0], LY + 2, LAST[1])
area.clear(LAST[0], LY + 2, LAST[1])

# ---------------------------------------------------------------- the Trial Grounds (Wizard Brawl ring)
tx, tz = 120, 16
for dx in range(-13, 14):
    for dz in range(-13, 14):
        r = math.hypot(dx, dz)
        x, z = tx + dx, tz + dz
        if (x, z) not in H or (x, z) in TR:
            continue
        b = None
        if r < 9.5:
            b = "minecraft:deepslate_tiles" if hash01(x, 1, z, 3) < 0.8 else "minecraft:cracked_deepslate_tiles"
            if r < 1.4:
                b = "minecraft:crying_obsidian"
            elif 5.5 <= r < 6.3:
                b = "minecraft:chiseled_deepslate"
        elif r < 10.6:
            b = "minecraft:polished_blackstone_bricks" if (dx + dz) % 2 else "minecraft:polished_blackstone"
        elif r < 12.6:
            b = "minecraft:andesite" if hash01(x, 2, z, 3) < 0.6 else "minecraft:polished_andesite"
        if b:
            S(x, G, z, b)
            for h in range(1, 7):
                area.clear(x, G + h, z)
for k in range(10):
    a = k * math.pi / 5
    px, pz = int(round(tx + 11 * math.cos(a))), int(round(tz + 11 * math.sin(a)))
    if (px, pz) in TR:
        continue
    for h in range(1, 5):
        S(px, G + h, pz, "minecraft:deepslate_brick_wall")
    S(px, G + 5, pz, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(px + dx, G + 5, pz + dz, "minecraft:purple_stained_glass")
    S(px, G + 6, pz, "minecraft:end_rod[facing=up]")
for k in range(0, 360, 12):
    a = math.radians(k)
    px, pz = int(round(tx + 12.4 * math.cos(a))), int(round(tz + 12.4 * math.sin(a)))
    if (px, pz) in H and (px, pz) not in TR and not (70 < k < 110):
        stair(px, G + 1, pz, FACE[(int(round(-math.cos(a))), 0)] if abs(math.cos(a)) > 0.7 else FACE[(0, int(round(-math.sin(a))))],
              "polished_blackstone_brick_stairs") if False else None
sign(tx - 2, G + 3, tz - 11, ["TRIAL GROUNDS"], "minecraft:dark_oak_wall_sign[facing=south]", glow=True) if False else None
sign(tx + 2, G + 2, 7, ["TRIAL", "GROUNDS"], "minecraft:dark_oak_wall_sign[facing=north]", glow=True) if False else None
for x in (tx - 2, tx + 2):
    for h in range(1, 5):
        S(x, G + h, 6, "minecraft:deepslate_brick_wall")
    S(x, G + 5, 6, "minecraft:lantern[hanging=false]")
for x in range(tx - 2, tx + 3):
    S(x, G + 5, 6, "minecraft:polished_blackstone_brick_slab[type=bottom]") if abs(x - tx) < 2 else None
sign(tx, G + 4, 5, ["TRIAL GROUNDS"], "minecraft:dark_oak_wall_sign[facing=north]", glow=True) if False else None

# ---------------------------------------------------------------- the Vents (a smoking hill)
vx, vz = VENT
VTOP = max((h, c) for c, h in H.items() if math.hypot(c[0] - vx, c[1] - vz) < 6)
vents = 0
for (x, z), h in sorted(H.items()):
    r = math.hypot(x - vx, z - vz)
    if r >= 16 or (x, z) in TR or h <= 69:
        continue
    k = hash01(x, 6, z, 71)
    if k < 0.045:
        S(x, h + 1, z, "minecraft:campfire[lit=true,signal_fire=false,facing=north]")
        vents += 1
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if hash01(x + dx, 7, z + dz, 71) < 0.5 and (x + dx, z + dz) in H:
                S(x + dx, H[(x + dx, z + dz)] + 1, z + dz, "minecraft:cobweb")
        S(x, h + 2, z, "minecraft:cobweb") if hash01(x, 8, z, 71) < 0.3 else None
    elif k < 0.08:
        S(x, h + 1, z, "minecraft:cobweb")
    elif k < 0.1:
        S(x, h, z, "minecraft:magma_block")
VENT_SOUL = (VTOP[1][0], VTOP[0] + 1, VTOP[1][1])
for dx, dz in ((0, 0), (1, 0), (0, 1)):
    area.clear(VTOP[1][0] + dx, VTOP[0] + 1 + 0, VTOP[1][1] + dz)
    area.clear(VTOP[1][0] + dx, VTOP[0] + 2, VTOP[1][1] + dz)
S(VTOP[1][0] + 1, VTOP[0], VTOP[1][1], "minecraft:blackstone")
S(VTOP[1][0] - 1, VTOP[0] + 1, VTOP[1][1], "minecraft:campfire[lit=true,signal_fire=true,facing=north]")

# ---------------------------------------------------------------- the east: crossing, gate, isles' details
# the Photon Pathway crossing at x 130: a 5-wide path of coarse dirt / rooted dirt / mud bricks at y 68 (trail "east")
for x in (CROSS_E - 4, CROSS_E + 4):
    for h in range(1, 5):
        S(x, G + h, -28, "minecraft:mud_brick_wall")
    S(x, G + 5, -28, "minecraft:lantern[hanging=false]")
# rope-bridge end posts at the isles' chasm
for z in (-14, -10):
    for x in (150,):
        pass
# small decorations on the isles: dark oaks
for (cx_, cz_, r) in ISLES[:5]:
    pass

# ---------------------------------------------------------------- vegetation
OCC = set()
for (x, z), t in TR.items():
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            OCC.add((x + dx, z + dz))
for (x, z), (d, y) in NEAR.items():
    if d <= 3:
        OCC.add((x, z))


def reserve(cx_, cz_, rx, rz=None):
    rz = rx if rz is None else rz
    for dx in range(-rx, rx + 1):
        for dz in range(-rz, rz + 1):
            OCC.add((cx_ + dx, cz_ + dz))


reserve(20, -12, 13)
reserve(120, 16, 15)
reserve(68, 20, 10, 9)
reserve(6, 27, 6, 9)
reserve(2, -3, 5, 6)
reserve(29, 22, 16, 14)
reserve(29, 30, 14, 16)
for (x, z), h in sorted(H.items()):
    if (x, z) in OCC or KIND[(x, z)] not in ("grass",) or EDGE[(x, z)] < 3:
        continue
    r = hash01(x, 3, z, 81)
    sl = SL[(x, z)]
    if sl > 2 or h > 96:
        continue
    if area.get(x, h + 1, z) not in (None, "minecraft:air"):
        continue
    if r < 0.16:
        S(x, h + 1, z, "minecraft:short_grass" if hash01(x, 4, z, 81) < 0.6 else "minecraft:fern")
    elif r < 0.185:
        S(x, h + 1, z, PETAL)
    elif r < 0.2:
        S(x, h + 1, z, ["minecraft:allium", "minecraft:blue_orchid", "minecraft:azure_bluet"][int(hash01(x, 5, z, 81) * 3) % 3])
trees = 0
for x in range(X0 + 3, X1 - 2):
    for z in range(Z0 + 3, Z1 - 2):
        if (x, z) not in H or (x, z) in OCC or KIND[(x, z)] != "grass" or EDGE[(x, z)] < 5 or SL[(x, z)] > 1:
            continue
        h = H[(x, z)]
        if h > 92 or hash01(x, 0, z, 91) > 0.014:
            continue
        if any(math.hypot(x - tx2, z - tz2) < 4 for tx2, tz2 in ()):
            continue
        k = hash01(x, 1, z, 91)
        if area.get(x, h + 1, z) not in (None, "minecraft:air", "minecraft:short_grass", "minecraft:fern"):
            continue
        if k < 0.5:
            area.oak(x, h + 1, z, height=6 + int(hash01(x, 2, z, 91) * 3), radius=3, log="dark_oak_log",
                     leaves="dark_oak_leaves", seed=x + z)
        elif k < 0.8:
            area.spruce(x, h + 1, z, height=8 + int(hash01(x, 2, z, 91) * 5), seed=x * 3 + z)
        else:
            area.dead_tree(x, h + 1, z, height=5 + int(hash01(x, 3, z, 91) * 4), log="dark_oak_log", seed=x + z)
        trees += 1
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                OCC.add((x + dx, z + dz))

# ---------------------------------------------------------------- NPCs, souls, spawn spots (players stand at surface + 1)
area.npc("Ubik Von Neumann", 24, G + 1, -9, 90.0, "by the Continuum's floating shards")
area.npc("Old Timer", 15, G + 1, -9, 270.0, "on the Continuum plaza (Timite and the time guns)")
area.npc("Plate", 120, G + 1, 7, 0.0, "at the Trial Grounds' gate (Wizardman Trials)")
for name, c, _, _ in SHEN:
    area.npc(name, c, PY + 1, 16, 0.0, "in his alcove of the Cerebral Citadel")
area.npc("Madame Eleanor Q. Goldsworth I", 6, YR + 2 - 1, 23, 0.0, "in Rose's End")
area.npc("Oruo the Almighty", 31, YC + 1, 47 - 1, 0.0, "by the Time Chamber's door (sends you in against the Sun Gecko)")
area.npc("The 7th Sin", 29, PY + 10, 19, 0.0, "a Porhtal eye on the summit roof of the Citadel")
area.npc("Detective Amog (Mountaintop)", AMOG[0], AMOG[1], AMOG[2], 180.0, "on the Walk of Fame")
area.npc("enigma:Mountain Balloons", *BALLOON_SOUL, 0.0, "on the balloons' ledge at the trail's west turn")
area.npc("enigma:Ubik", *UBIK_SOUL, 0.0, "on the top plate of the floating spiral in the Continuum")
area.npc("enigma:Time-Torn Isles", *TT_SOUL, 0.0, "on the last of the Time-Torn Isles")
area.npc("enigma:Summit", *SUMMIT_SOUL, 0.0, "on the summit podium of the Citadel roof")
area.npc("enigma:Rose's End", *ROSE_SOUL, 0.0, "hidden in the roses at the back of Rose's End")
area.npc("enigma:The Vents", *VENT_SOUL, 0.0, "on top of the smoking hill")
area.npc("enigma:Walk of Fame", *SOUL_WOF, 0.0, "on a plinth at the west end of the Walk of Fame")
area.npc("enigma:Citadel Puzzle", 29, PY + 1, 24, 0.0, "on the mosaic in the Cerebral Citadel's hall")
area.npc("spawn:time_chamber", 29, YC + 1, 39, 180.0, "inside the Time Chamber, by the door")
area.npc("spawn:sun_gecko", 29, YC + 1, 31, 180.0, "the Time Chamber's boss spot under the pendulum")
area.npc("spawn:mountain_top", 29, PY + 10, 27, 0.0, "the summit: roof of the Cerebral Citadel")
# The Rift has one arrival, the Wizard Tower (rift_wyld_woods.py).
# area.arrival(CROSS_P, G + 1, -26, 180.0)

# ---------------------------------------------------------------- zones and mobs


def mob(kind, name, level, health, damage, xp, coins, mx_, every, a, b, drops=None):
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


PUFF = [drop("PUFF_CRUX", 0.1, 1, 1)]
area.zone("The Mountaintop", box=((41, 10, Z0), (X1, 200, Z1)), blocks={}, mobs=[
    mob("minecraft:slime", "Puff", 7, 100, 5, 6, 50, 6, 8, (64, 66, 4), (100, 84, 40), PUFF),
    mob("minecraft:slime", "Puff", 7, 100, 5, 6, 50, 6, 8, (96, 66, 24), (146, 84, 54), PUFF),
    mob("minecraft:slime", "Puff", 7, 100, 5, 6, 50, 5, 8, (100, 66, -22), (147, 80, 8), PUFF),
])
area.zone("The Mountaintop", box=((X0, 10, Z0), (17, 200, Z1)), blocks={})
area.zone("The Mountaintop", box=((18, 10, Z0), (40, 200, CZ0 - 1)), blocks={})
area.zone("The Mountaintop", box=((18, 10, CZ1 + 1), (40, 200, Z1)), blocks={})
area.zone("The Mountaintop", box=((18, 10, CZ0), (40, YC - 1, CZ1)), blocks={})
area.zone("The Mountaintop", box=((18, YC + 16, CZ0), (40, 200, CZ1)), blocks={})
area.zone("Time Chamber", box=((18, YC, CZ0), (40, YC + 15, CZ1)), blocks={})
area.save()
print("lamps", lamps, "trees", trees, "vents", vents, "YC", YC, "YR", YR, "YB", YB, "YW", YW, "YE", YE, "land columns", len(H),
      "max y", max(H.values()))
