#!/usr/bin/env python3
"""The Rift, Dreadfarm (x -110..-1, z -210..-121): a twisted, wilted mirror of the Hub's farm. Small plots of dead and
peculiar crops (dead bushes, brown mushrooms on mycelium, rose bushes, berries, wilted wheat, wither roses), crooked
fences, Shania's farmhouse and a creepy barn, animal pens, scarecrows, Baba Yaga's hut on stilts, a balloon silo, and the
Great Beanstalk: a huge twisting vine stalk climbing to y 150 with a spiral of leaf pads (Enigma Soul at the top). A
cracked stone-brick wall at the north-east hides a nook (Montezuma's piece 5).

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_dreadfarm.py

West Village (x -110..-31) and the Village Plaza (x -30..-1) lie along z = -121: the surface keeps y 68 within SEAM of
that side and the main paths cross at x -70 and x -15. West, north and east are the Rift's outer edge.
"""
import math

from kit import hash01, noise
from rift_lib import new_area, land, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("dreadfarm", version=1)
area.shell = 11
S = area.set
G = GROUND
X0, X1, Z0, Z1 = -110, -1, -210, -121
CROSS_W = CROSSINGS[("west_village", "dreadfarm")][2]      # -70
CROSS_P = CROSSINGS[("village_plaza", "dreadfarm")][2]     # -15

# the main features
FARMHOUSE = (-62, -152, -50, -144)      # x0, z0, x1, z1 (walls), door on the south side at x -56
BARN = (-40, -156, -26, -143)           # door on the south side at x -34..-32
STALK = (-50, -197)                     # Great Beanstalk base centre
SILO = (-98, -163)
HUT = (-98, -181)
NOOK = (-25, -199)                      # Montezuma's spot
WALL_Z = -194                           # the cracked wall's z


# ---------------------------------------------------------------- helpers
def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def jag(a, b, s):
    return 3.2 * noise(a, b, s, 1.2) + 1.6 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 0.9 * (hash01(int(a), 2, int(b), s) - 0.5)


def edge(x, z):
    """Distance in blocks to the Rift's outer edge (negative = void)."""
    dw = x - (-106 + jag(z, 0, 1))
    de = (-4 + jag(z, 9, 2)) - x
    dn = z - (-208 + jag(x, 5, 3))
    return min(dw, de, dn)


def rect_d(x, z, r):
    dx = max(r[0] - x, 0, x - r[2])
    dz = max(r[1] - z, 0, z - r[3])
    return math.hypot(dx, dz)


# flat ground (exactly y 68): buildings, plots, pens, the beanstalk base
FIELD_A = (-90, -188, -60, -155)
FIELD_B = (-52, -183, -27, -157)
FLATS = [FARMHOUSE_R := (FARMHOUSE[0] - 3, FARMHOUSE[1] - 3, FARMHOUSE[2] + 3, FARMHOUSE[3] + 4),
         (BARN[0] - 2, BARN[1] - 2, BARN[2] + 2, BARN[3] + 4), FIELD_A, FIELD_B,
         (-106, -154, -76, -121), (-103, -171, -92, -158), (-104, -190, -92, -171), (-72, -203, -80, -192),
         (-102, -206, -88, -190),
         (STALK[0] - 9, STALK[1] - 9, STALK[0] + 9, STALK[1] + 9), (-48, -143, -3, -121)]
BEAN_BASE = 9


def hills(x, z):
    f = sm((-127 - z) / 10.0)
    h = max(0.0, noise(x, z, 31, 1.8) + 0.12) * 4.2 * f
    d = min(rect_d(x, z, r) for r in FLATS)
    return h * sm(d / 6.0)


def top_of(x, z):
    nd = math.hypot(x - NOOK[0], z - NOOK[1])
    if nd < 10:
        return 71
    t = G + int(round(hills(x, z)))
    if nd < 17:
        k = sm((17 - nd) / 7.0)
        t = max(t, int(round(G + 3 * k)))
    return t


# ---------------------------------------------------------------- paths
PATHS = {
    "cross_w": ([(CROSS_W, -121), (CROSS_W, -135)], 5),
    "cross_p": ([(CROSS_P, -121), (CROSS_P, -135)], 5),
    "road": ([(CROSS_W, -135), (CROSS_W, -140), (-44, -140), (-15, -140), (CROSS_P, -135)], 3),
    "west": ([(CROSS_W, -135), (-86, -135), (-98, -137), (-100, -127)], 3),
    "field_a": ([(CROSS_W, -140), (CROSS_W, -186)], 2),
    "hut": ([(CROSS_W, -158), (-88, -162), (-97, -172)], 2),
    "silo": ([(-86, -135), (-90, -150), (-96, -159)], 2),
    "field_b": ([(-44, -140), (-44, -176), (-43, -178)], 2),
    "farmhouse": ([(-56, -140), (-56, -143)], 3),
    "barn": ([(-33, -140), (-33, -143)], 4),
    "wall": ([(CROSS_P, -140), (-15, -170), (-22, -190), (-25, -192)], 3),
    "stalk": ([(-44, -165), (-47, -185), (-50, -190)], 2),
    "pots": ([(-98, -190), (-96, -196), (-100, -200), (-95, -204)], 1),
    "buttons": ([(-88, -178), (-82, -190), (-77, -193)], 2),
}
path_cols = {}
for name, (pts, w) in PATHS.items():
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            px, pz = x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n
            r = w / 2.0
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz <= r * r + 0.3:
                        path_cols.setdefault((int(round(px)) + dx, int(round(pz)) + dz), name)

# ---------------------------------------------------------------- the ground
SURF = area.mix({"grass_block": 4, "podzol": 4, "coarse_dirt": 3, "rooted_dirt": 1, "mycelium": 0.4})
PATHB = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3, "dirt_path": 1})
ROAD = area.mix({"dirt_path": 6, "coarse_dirt": 3, "mud": 1, "gravel": 1})
UNDER = area.mix({"dirt": 5, "coarse_dirt": 2, "rooted_dirt": 2, "mud": 1})
BODY = area.mix({"tuff": 6, "dirt": 3, "mud": 3, "packed_mud": 2, "deepslate": 3, "calcite": 1, "andesite": 1})
UNDERSIDE = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1})

T = {}


def under_depth(x, z, e):
    t = sm(e / 48.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z) if z < -121 else min(x - (-106 + jag(z, 0, 1)), (-4 + jag(z, 9, 2)) - x)
        # the south side is flush with the neighbours: its east/west ends still break off at the outer edge
        if z >= -123:
            e = min(x - (-106 + jag(z, 0, 1)), (-4 + jag(z, 9, 2)) - x)
        if e < 0:
            continue
        top = top_of(x, z)
        if z >= -127:
            top = G
        T[(x, z)] = top
        pc = path_cols.get((x, z))
        if pc in ("cross_w", "cross_p") or (pc and z > -127):
            surf = PATHB
        elif pc:
            surf = ROAD
        elif e < 2.5:
            surf = lambda x, y, z: "minecraft:mud" if hash01(x, 8, z, 1) < 0.5 else "minecraft:coarse_dirt"
        else:
            surf = SURF
        land(area, x, z, top, surf, UNDER, BODY)
        area.top[(x, z)] = top
        d = under_depth(x, z, e)
        for k in range(1, d + 1):
            y = LAND_BOTTOM - k
            r = hash01(x, y, z, 12)
            if e < 14 and r < 0.04:
                b = "minecraft:crying_obsidian"
            elif r < 0.035:
                b = "minecraft:amethyst_block"
            else:
                b = UNDERSIDE(x, y, z)
            S(x, y, z, b)
        if e < 30 and hash01(x, 10, z, 13) < 0.012:
            ln = 3 + int(hash01(x, 11, z, 14) * 8)
            yy = LAND_BOTTOM - d
            for i in range(ln):
                S(x, yy - 1 - i, z, "minecraft:crying_obsidian" if hash01(x, i, z, 15) < 0.4 else "minecraft:amethyst_block")
            S(x, yy - 1 - ln, z, "minecraft:amethyst_cluster[facing=down]")

# floating shards beyond the cliffs
for i in range(40):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 12))
    if edge(x, z) > -3 or edge(x, z) < -14 or i > 16:
        continue
    y = 52 + int(hash01(i, 2, 0, 77) * 26)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    b = "minecraft:tuff" if q < 0.7 else "minecraft:mud"
                    if hash01(x + dx, y + dy, z + dz, 5) < 0.08:
                        b = "minecraft:crying_obsidian"
                    S(x + dx, y + dy, z + dz, b)
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:amethyst_block" if k % 2 == 0 else "minecraft:crying_obsidian")

occ = set()


def reserve_rect(x0, z0, x1, z1, m=0):
    for x in range(x0 - m, x1 + m + 1):
        for z in range(z0 - m, z1 + m + 1):
            occ.add((x, z))


def reserve(cx, cz, r):
    reserve_rect(cx - r, cz - r, cx + r, cz + r)


for (x, z) in path_cols:
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            occ.add((x + dx, z + dz))


def gy(x, z):
    return T.get((x, z), G)


# ---------------------------------------------------------------- building helpers
def air_box(x0, y0, z0, x1, y1, z1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                area.clear(x, y, z)
                S(x, y, z, "minecraft:air")


def fence(x, y, z, kind="dark_oak_fence"):
    S(x, y, z, f"minecraft:{kind}")


def lantern_post(x, z, kind="soul_lantern"):
    y = gy(x, z)
    for h in range(1, 4):
        S(x, y + h, z, "minecraft:dark_oak_fence")
    S(x, y + 4, z, f"minecraft:{kind}[hanging=false]")


def crooked_fence(points, skip=0.14, kind="dark_oak_fence", gates=(), seed=0):
    """A fence line through the points with missing and sagging pieces; `gates` are (x, z) cells left as open gates."""
    seen = set()
    for (x1, z1), (x2, z2) in zip(points, points[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            x, z = int(round(x1 + (x2 - x1) * i / n)), int(round(z1 + (z2 - z1) * i / n))
            if (x, z) in seen or (x, z) not in T:
                continue
            seen.add((x, z))
            y = gy(x, z)
            if (x, z) in gates:
                S(x, y + 1, z, f"minecraft:{kind.replace('fence', 'fence_gate')}[facing={'north' if x1 == x2 else 'east'},open=true]")
                continue
            if (x, z) in path_cols and path_cols[(x, z)] not in ("pots",):
                continue
            h = hash01(x, 3, z, seed + 5)
            if h < skip:
                continue
            S(x, y + 1, z, f"minecraft:{kind}")
            if h > 0.93:
                S(x, y + 2, z, f"minecraft:{kind}")


def gable(x0, z0, x1, z1, wall_h, wall, corner, roof, ridge, floor, seed=0, ceiling=True):
    """A gabled building along x: floor at y G, walls y G+1..G+wall_h, roof of stairs (ridge in the middle)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, G, z, floor)
            edge_ = x in (x0, x1) or z in (z0, z1)
            for y in range(G + 1, G + wall_h + 1):
                if edge_:
                    b = corner if (x in (x0, x1) and z in (z0, z1)) else wall
                    if hash01(x, y, z, seed) < 0.06:
                        b = "minecraft:cobweb" if False else wall
                    S(x, y, z, b)
                else:
                    S(x, y, z, "minecraft:air")
            if ceiling and not edge_:
                S(x, G + wall_h, z, floor)
    i = 0
    while True:
        zl, zr = z0 - 1 + i, z1 + 1 - i
        y = G + wall_h + 1 + i
        if zl > zr:
            break
        for x in range(x0 - 1, x1 + 2):
            if zl == zr:
                S(x, y, zl, ridge)
            else:
                S(x, y, zl, f"minecraft:{roof}[facing=south,half=bottom]")
                S(x, y, zr, f"minecraft:{roof}[facing=north,half=bottom]")
        for z in range(zl + 1, zr):
            if z0 <= z <= z1:
                for x in (x0, x1):
                    S(x, y, z, wall)
        i += 1


def door(x, z, facing="south", wood="dark_oak"):
    for y in (G + 1, G + 2):
        S(x, y, z, "minecraft:air")
    S(x, G + 1, z, f"minecraft:{wood}_door[facing={facing},half=lower,hinge=left,open=false]")
    S(x, G + 2, z, f"minecraft:{wood}_door[facing={facing},half=upper,hinge=left,open=false]")


def windows(cells, y=G + 2):
    for (x, z) in cells:
        S(x, y, z, "minecraft:glass_pane")


# ---------------------------------------------------------------- Shania's farmhouse
fx0, fz0, fx1, fz1 = FARMHOUSE
reserve_rect(fx0 - 3, fz0 - 3, fx1 + 3, fz1 + 3)
gable(fx0, fz0, fx1, fz1, 4, "minecraft:spruce_planks", "minecraft:stripped_dark_oak_log[axis=y]",
      "dark_oak_stairs", "minecraft:dark_oak_planks", "minecraft:dark_oak_planks", seed=3)
for z in range(fz0, fz1 + 1):                       # half-timbered: logs every 4 blocks
    for x in range(fx0, fx1 + 1, 4):
        if z in (fz0, fz1) or x in (fx0, fx1):
            for y in range(G + 1, G + 5):
                if (x, z) != (-56, fz1):
                    S(x, y, z, "minecraft:stripped_dark_oak_log[axis=y]")
door(-56, fz1, "south")
windows([(-60, fz1), (-52, fz1), (-60, fz0), (-56, fz0), (-52, fz0), (fx0, -148), (fx1, -148), (fx0, -150), (fx1, -146)])
# broken window: a missing pane
S(-52, G + 2, fz1, "minecraft:cobweb")
for (x, z) in ((-61, -151), (-51, -151), (-61, -145), (-51, -145)):
    S(x, G + 4, z, "minecraft:soul_lantern[hanging=true]")
# furniture: a bed, table, bookshelves, a hearth
S(-61, G + 1, -150, "minecraft:red_bed[facing=east,part=foot]")
S(-60, G + 1, -150, "minecraft:red_bed[facing=east,part=head]")
for x in range(-61, -57):
    S(x, G + 1, fz0 + 0 + 0, "minecraft:bookshelf") if False else None
S(-61, G + 1, -148, "minecraft:bookshelf")
S(-61, G + 2, -148, "minecraft:bookshelf")
S(-58, G + 1, -149, "minecraft:dark_oak_fence")
S(-58, G + 2, -149, "minecraft:dark_oak_pressure_plate")
S(-57, G + 1, -149, "minecraft:dark_oak_stairs[facing=west,half=bottom]")
S(-59, G + 1, -149, "minecraft:dark_oak_stairs[facing=east,half=bottom]")
S(-52, G + 1, -151, "minecraft:cobblestone")
S(-52, G + 1, -150, "minecraft:furnace[facing=south,lit=false]")
S(-52, G + 1, -149, "minecraft:cauldron")
S(-51, G + 1, -147, "minecraft:barrel[facing=up]")
S(-51, G + 1, -146, "minecraft:chest[facing=west]")
S(-55, G + 1, -151, "minecraft:cobweb")
S(-61, G + 3, -145, "minecraft:cobweb")
S(-51, G + 3, -151, "minecraft:cobweb")
# the crooked chimney (leans one block east half way up)
for y in range(G + 1, G + 8):
    S(-52 + (1 if y > G + 5 else 0), y, -150, "minecraft:cobblestone" if y < G + 5 else "minecraft:stone_bricks"
      if False else "minecraft:cobblestone")
S(-51, G + 8, -150, "minecraft:campfire[lit=false]")
# porch
for x in range(-58, -53):
    S(x, G, fz1 + 1, "minecraft:dark_oak_planks")
    S(x, G, fz1 + 2, "minecraft:dark_oak_planks")
for x, z in ((-58, fz1 + 2), (-54, fz1 + 2)):
    for y in range(G + 1, G + 4):
        S(x, y, z, "minecraft:dark_oak_fence")
for x in range(-58, -53):
    S(x, G + 4, fz1 + 2, "minecraft:dark_oak_slab[type=bottom]")
S(-56, G + 3, fz1 + 2, "minecraft:soul_lantern[hanging=true]")
S(-58, G + 1, fz1 + 1, "minecraft:hay_block[axis=y]")
S(-54, G + 1, fz1 + 1, "minecraft:barrel[facing=up]")

# ---------------------------------------------------------------- the creepy barn
bx0, bz0, bx1, bz1 = BARN
reserve_rect(bx0 - 2, bz0 - 2, bx1 + 2, bz1 + 3)
gable(bx0, bz0, bx1, bz1, 6, "minecraft:dark_oak_planks", "minecraft:dark_oak_log[axis=y]", "spruce_stairs",
      "minecraft:spruce_planks", "minecraft:coarse_dirt", seed=7, ceiling=False)
for x in range(bx0, bx1 + 1):
    for z in range(bz0, bz1 + 1):
        if not (x in (bx0, bx1) or z in (bz0, bz1)):
            S(x, G, z, "minecraft:coarse_dirt" if hash01(x, 1, z, 4) < 0.7 else "minecraft:podzol")
for x in range(bx0 + 1, bx1, 3):                     # beams across
    for z in range(bz0 + 1, bz1):
        S(x, G + 6, z, "minecraft:dark_oak_planks") if x % 2 == 0 and False else None
for z in range(bz0, bz1 + 1):                        # posts every 3
    for x in (bx0, bx1):
        if (z - bz0) % 3 == 0:
            for y in range(G + 1, G + 7):
                S(x, y, z, "minecraft:dark_oak_log[axis=y]")
for x in range(bx0, bx1 + 1):
    if (x - bx0) % 3 == 0:
        for y in range(G + 1, G + 7):
            S(x, y, bz0, "minecraft:dark_oak_log[axis=y]")
            S(x, y, bz1, "minecraft:dark_oak_log[axis=y]")
# big double door, standing a little open, with a red X of fence braces
for x in (-34, -33, -32):
    for y in range(G + 1, G + 5):
        S(x, y, bz1, "minecraft:air")
S(-34, G + 5, bz1, "minecraft:dark_oak_planks")
for k in range(4):
    S(-34 - 1 + 0, G + 1, bz1, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=true]")
    S(-34 - 1 + 0, G + 2, bz1, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=true]")
    S(-31, G + 1, bz1, "minecraft:dark_oak_door[facing=south,half=lower,hinge=right,open=true]")
    S(-31, G + 2, bz1, "minecraft:dark_oak_door[facing=south,half=upper,hinge=right,open=true]")
for i in range(4):
    S(bx0 + 3 + i, G + 1 + i, bz1, "minecraft:dark_oak_fence")
    S(bx0 + 6 - i, G + 1 + i, bz1, "minecraft:dark_oak_fence")
# hayloft at the back, ladder, hay bales, stalls
for x in range(bx0 + 1, bx1):
    for z in range(bz0 + 1, bz0 + 5):
        S(x, G + 4, z, "minecraft:spruce_planks")
for z in range(bz0 + 1, bz0 + 5):
    S(bx0 + 1, G + 4, z, "minecraft:air") if z == bz0 + 2 else None
for y in range(G + 1, G + 6):
    S(bx0 + 1, y, bz0 + 2, "minecraft:ladder[facing=east]") if y > G else None
S(bx0 + 2, G + 5, bz0 + 2, "minecraft:hay_block[axis=y]")
for x in range(bx0 + 3, bx1 - 1, 2):
    S(x, G + 5, bz0 + 1, "minecraft:hay_block[axis=x]")
    S(x, G + 5, bz0 + 2, "minecraft:hay_block[axis=x]") if x % 4 == 1 else None
for x in range(bx0 + 2, bx0 + 12, 3):                # stalls along the east/west walls
    for z in range(bz0 + 6, bz1 - 2, 4):
        S(x, G + 1, z, "minecraft:dark_oak_fence")
        S(x + 1, G + 1, z, "minecraft:hay_block[axis=x]")
S(bx1 - 1, G + 1, bz0 + 6, "minecraft:cauldron")
S(bx1 - 1, G + 1, bz0 + 7, "minecraft:barrel[facing=up]")
S(bx1 - 1, G + 1, bz0 + 8, "minecraft:composter[level=4]")
for x, z in ((-36, -150), (-30, -153), (-38, -146), (-28, -152), (-33, -147)):
    S(x, G + 6, z, "minecraft:soul_lantern[hanging=true]")
for (x, y, z) in ((-38, G + 3, -154), (-29, G + 4, -151), (-32, G + 2, -145), (-39, G + 5, -147)):
    S(x, y, z, "minecraft:cobweb")
# broken hayloft window and a round window up in the gable
for z in (-151, -148, -145):
    S(bx0, G + 3, z, "minecraft:glass_pane")
    S(bx1, G + 3, z, "minecraft:glass_pane")
S(-33, G + 8, bz0, "minecraft:air")
S(-33, G + 8, bz1, "minecraft:glass_pane")
S(-33, G + 9, bz1, "minecraft:glass_pane")
# the weathervane: a crooked rod with a scythe-like crossbar
for y in range(G + 11, G + 15):
    S(-33, y, -150, "minecraft:iron_bars" if False else "minecraft:dark_oak_fence")
S(-32, G + 14, -150, "minecraft:dark_oak_fence")
S(-34, G + 15, -150, "minecraft:dark_oak_fence")
S(-33, G + 15, -150, "minecraft:dark_oak_fence")

# ---------------------------------------------------------------- crop plots
PLOT_KINDS = ["bush", "mushroom", "roses", "berry", "wheat", "wither"]


def plot(x0, z0, w, d, kind, seed=0):
    """A w x d plot: a log border at ground level and wilted crops on the inside."""
    reserve_rect(x0, z0, x0 + w - 1, z0 + d - 1)
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            y = G
            border = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            if border:
                axis = "x" if z in (z0, z0 + d - 1) else "z"
                S(x, y, z, f"minecraft:dark_oak_log[axis={axis}]" if hash01(x, 1, z, seed) > 0.12 else "minecraft:coarse_dirt")
                continue
            for yy in range(y + 1, y + 4):
                area.clear(x, yy, z)
            h = hash01(x, 2, z, seed)
            h2 = hash01(x, 3, z, seed + 1)
            if kind == "bush":
                S(x, y, z, "minecraft:coarse_dirt" if h2 < 0.6 else "minecraft:podzol")
                if h < 0.62:
                    S(x, y + 1, z, "minecraft:dead_bush")
            elif kind == "mushroom":
                S(x, y, z, "minecraft:mycelium" if h2 < 0.65 else "minecraft:podzol")
                if h < 0.7:
                    S(x, y + 1, z, "minecraft:brown_mushroom" if h2 < 0.8 else "minecraft:red_mushroom")
            elif kind == "roses":
                S(x, y, z, "minecraft:podzol" if h2 < 0.5 else "minecraft:rooted_dirt")
                if (x + z) % 2 == 0 and h < 0.85:
                    S(x, y + 1, z, "minecraft:rose_bush[half=lower]")
                    S(x, y + 2, z, "minecraft:rose_bush[half=upper]")
            elif kind == "berry":
                S(x, y, z, "minecraft:podzol" if h2 < 0.6 else "minecraft:grass_block")
                if h < 0.75:
                    S(x, y + 1, z, f"minecraft:sweet_berry_bush[age={1 + int(h * 4) % 3}]")
            elif kind == "wheat":
                cx, cz = x0 + w // 2, z0 + d // 2
                if (x, z) == (cx, cz):
                    S(x, y, z, "minecraft:water")
                else:
                    S(x, y, z, "minecraft:farmland[moisture=7]")
                    if h < 0.85:
                        S(x, y + 1, z, f"minecraft:wheat[age={int(h * 8) % 4}]")
            elif kind == "wither":
                S(x, y, z, "minecraft:soul_soil" if h2 < 0.7 else "minecraft:coarse_dirt")
                if h < 0.6 and area.get(x, y, z) == "minecraft:soul_soil":
                    S(x, y + 1, z, "minecraft:wither_rose")
                elif h < 0.7:
                    S(x, y + 1, z, "minecraft:dead_bush")


def make_field(r, cols, rows, size, gap, x_start, z_start, seed):
    k = seed
    for ri in range(rows):
        for ci in range(cols):
            x0 = x_start + ci * (size + gap[ci] if isinstance(gap, list) else size + gap)
            z0 = z_start + ri * (size + 2)
            plot(x0, z0, size, size, PLOT_KINDS[(ci + ri * 2 + k) % len(PLOT_KINDS)], seed=ci * 7 + ri * 13 + k)


# Field A: x -86..-62, z -182..-158; gap x -70..-69 for the road, 7x7 plots
FA_X = [-86, -77, -68]
FA_Z = [-182, -173, -164]
FA_SIZE = {-86: 7, -77: 7, -68: 7}
for ri, z0 in enumerate(FA_Z):
    for ci, x0 in enumerate(FA_X):
        plot(x0, z0, 7, 7, PLOT_KINDS[(ci * 2 + ri * 3 + 1) % 6], seed=ci * 5 + ri * 11)
# Field B: x -50..-29, z -180..-159, 6x6 plots, road at the gap x -44..-43
FB_X = [-50, -41, -32]
FB_Z = [-180, -172, -164]
for ri, z0 in enumerate(FB_Z):
    for ci, x0 in enumerate(FB_X):
        plot(x0, z0, 7 if ci < 2 else 6, 7, PLOT_KINDS[(ci + ri * 2 + 3) % 6], seed=ci * 3 + ri * 17 + 40)
# the road through field B (x -43..-42 gap between the first two plots)
for z in range(-185, -157):
    for x in (-43, -42):
        y = gy(x, z)
        S(x, y, z, "minecraft:dirt_path" if hash01(x, 5, z, 3) < 0.7 else "minecraft:coarse_dirt")
        for yy in range(y + 1, y + 4):
            area.clear(x, yy, z)

# crooked fences round the two fields (the roads are left open)
crooked_fence([(-89, -155), (-89, -186), (-61, -186), (-61, -155)], seed=1)
crooked_fence([(-89, -155), (-72, -155)], seed=2)
crooked_fence([(-67, -155), (-61, -155)], seed=2)
crooked_fence([(-53, -157), (-53, -184), (-28, -184), (-28, -157)], seed=3)
crooked_fence([(-53, -157), (-46, -157)], seed=4)
crooked_fence([(-40, -157), (-28, -157)], seed=4)


# ---------------------------------------------------------------- scarecrows
def scarecrow(x, z, facing="south", lean=0):
    y = gy(x, z)
    reserve(x, z, 1)
    S(x, y + 1, z, "minecraft:dark_oak_fence")
    S(x, y + 2, z, "minecraft:dark_oak_fence")
    S(x, y + 2 + lean, z, "minecraft:dark_oak_fence")
    for dx in (-1, 1):
        S(x + dx, y + 2, z, "minecraft:hay_block[axis=x]" if False else "minecraft:oak_fence")
        S(x + dx * 2, y + 1, z, "minecraft:oak_fence") if hash01(x, dx, z, 6) < 0.5 else None
    S(x, y + 3, z, f"minecraft:carved_pumpkin[facing={facing}]" if hash01(x, 9, z, 6) < 0.5 else
      f"minecraft:jack_o_lantern[facing={facing}]")
    S(x, y + 4, z, "minecraft:dark_oak_pressure_plate")


for (x, z, f) in ((-83, -179, "south"), (-65, -170, "south"), (-74, -161, "east"), (-47, -177, "south"),
                  (-38, -169, "west"), (-45, -161, "south"), (-20, -176, "south"), (-100, -180 + 12, "east"),
                  (-70, -150, "south")):
    if (x, z) in T:
        scarecrow(x, z, f, lean=0)

# ---------------------------------------------------------------- animal pens
PENS = {}


def pen(x0, z0, x1, z1, gate, floor, seed):
    reserve_rect(x0 - 1, z0 - 1, x1 + 1, z1 + 1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            y = gy(x, z)
            for yy in range(y + 1, y + 4):
                area.clear(x, yy, z)
            if (x, z) not in path_cols:
                S(x, y, z, floor(x, y, z))
    pts = [(x0, z0), (x1, z0), (x1, z1), (x0, z1), (x0, z0)]
    crooked_fence(pts, skip=0.1, kind="spruce_fence", gates=(gate,), seed=seed)


MUDMIX = area.mix({"mud": 4, "coarse_dirt": 3, "podzol": 2, "packed_mud": 1})
pen(-104, -152, -92, -140, (-98, -140), MUDMIX, 11)         # cows
pen(-90, -152, -78, -140, (-84, -140), MUDMIX, 12)          # sheep
pen(-104, -133, -92, -124, (-98, -133), MUDMIX, 13)         # chickens
pen(-88, -133, -78, -124, (-83, -133), MUDMIX, 14)          # more chickens
for (x, z) in ((-100, -149), (-96, -143), (-86, -149), (-81, -143)):    # troughs
    y = gy(x, z)
    S(x, y + 1, z, "minecraft:oak_stairs[facing=east,half=bottom]")
    S(x + 1, y + 1, z, "minecraft:oak_stairs[facing=west,half=bottom]")
for (x, z) in ((-103, -151), (-79, -151), (-93, -141), (-91, -151)):    # hay
    y = gy(x, z)
    S(x, y + 1, z, "minecraft:hay_block[axis=y]")
    S(x, y + 2, z, "minecraft:hay_block[axis=y]") if hash01(x, 1, z, 3) < 0.5 else None
# the chicken coop: a small shed in the chicken run
cx0, cz0 = -103, -132
for x in range(cx0, cx0 + 4):
    for z in range(cz0, cz0 + 3):
        S(x, G, z, "minecraft:spruce_planks")
        for y in range(G + 1, G + 3):
            if x in (cx0, cx0 + 3) or z in (cz0, cz0 + 2):
                S(x, y, z, "minecraft:spruce_planks" if (x + z) % 2 else "minecraft:dark_oak_planks")
        S(x, G + 3, z, "minecraft:spruce_slab[type=bottom]")
S(cx0 + 1, G + 1, cz0 + 2, "minecraft:air")
S(cx0 + 1, G + 2, cz0 + 2, "minecraft:air")
S(cx0 + 1, G + 1, cz0 + 1, "minecraft:hay_block[axis=y]")
S(cx0 + 2, G + 1, cz0 + 1, "minecraft:barrel[facing=up]")
# a wooden wagon wheel (fence cross) and a water tower stub near the chickens
for k in range(4):
    S(-90 + k, G + 1, -131, "minecraft:spruce_fence") if k % 2 == 0 else None

# ---------------------------------------------------------------- Baba Yaga's hut: stilts, crooked top, a ramp
hx, hz = HUT
reserve_rect(hx - 4, hz - 4, hx + 4, hz + 9)
FLOORY = G + 5
for (sx, sz) in ((-2, -2), (2, -2), (-2, 2), (2, 2), (0, -2), (0, 2)):
    for y in range(G + 1, FLOORY):
        S(hx + sx, y, hz + sz, "minecraft:dark_oak_log[axis=y]")
    # chicken toes
    S(hx + sx + (1 if sx > 0 else -1), G + 1, hz + sz, "minecraft:dark_oak_fence") if sx != 0 else None
for x in range(hx - 3, hx + 4):
    for z in range(hz - 3, hz + 5):
        S(x, FLOORY, z, "minecraft:dark_oak_planks" if (x + z) % 2 else "minecraft:spruce_planks")
for x in range(hx - 2, hx + 3):
    for z in range(hz - 2, hz + 3):
        wall = x in (hx - 2, hx + 2) or z in (hz - 2, hz + 2)
        for y in range(FLOORY + 1, FLOORY + 5):
            # the upper half leans one block east
            xx = x + (1 if y >= FLOORY + 3 else 0)
            if wall:
                S(xx, y, z, "minecraft:stripped_dark_oak_log[axis=y]" if (x in (hx - 2, hx + 2) and z in (hz - 2, hz + 2))
                  else "minecraft:spruce_planks" if (y + x + z) % 5 else "minecraft:dark_oak_planks")
            else:
                S(xx, y, z, "minecraft:air")
                S(x, y, z, "minecraft:air") if y < FLOORY + 3 else None
# crooked pointed hat roof
for k in range(5):
    r = 3 - k // 2 if k < 5 else 0
    for x in range(hx - r, hx + r + 1):
        for z in range(hz - r, hz + r + 1):
            if k < 4 and (abs(x - hx) == r or abs(z - hz) == r) or k == 4:
                S(x + 1 + (1 if k >= 3 else 0), FLOORY + 5 + k, z, "minecraft:dark_oak_planks" if k % 2 else "minecraft:spruce_planks")
for x in range(hx - 2, hx + 3):
    for z in range(hz - 2, hz + 3):
        S(x + 1, FLOORY + 5, z, "minecraft:dark_oak_planks") if (x in (hx - 2, hx + 2) or z in (hz - 2, hz + 2)) else \
            S(x + 1, FLOORY + 5, z, "minecraft:dark_oak_planks")
# door, windows, lights, cauldron and bookshelves
for y in (FLOORY + 1, FLOORY + 2):
    S(hx, y, hz + 2, "minecraft:air")
S(hx, FLOORY + 3, hz + 2, "minecraft:dark_oak_planks")
for (wx, wz) in ((hx - 2, hz), (hx + 2, hz), (hx, hz - 2)):
    S(wx + (0 if wx != hx + 2 else 0), FLOORY + 2, wz, "minecraft:glass_pane")
S(hx - 1, FLOORY + 1, hz - 1, "minecraft:cauldron")
S(hx + 1, FLOORY + 1, hz - 1, "minecraft:brewing_stand")
S(hx - 1, FLOORY + 1, hz + 1, "minecraft:bookshelf")
S(hx + 1, FLOORY + 1, hz + 1, "minecraft:potted_dead_bush")
S(hx, FLOORY + 4, hz, "minecraft:soul_lantern[hanging=true]")
S(hx - 1, FLOORY + 2, hz - 2, "minecraft:cobweb")
# porch and the ramp down
for z in range(hz + 3, hz + 5):
    for x in range(hx - 1, hx + 2):
        S(x, FLOORY, z, "minecraft:dark_oak_planks")
for k, z in enumerate(range(hz + 5, hz + 10)):
    y = FLOORY - k
    if y < G + 1:
        break
    S(hx, y, z, "minecraft:dark_oak_stairs[facing=north,half=bottom]")
    for yy in range(G + 1, y):
        S(hx, yy, z, "minecraft:dark_oak_planks")
    for dx in (-1, 1):
        S(hx + dx, y + 1, z, "minecraft:dark_oak_fence")
        for yy in range(G + 1, y + 1):
            S(hx + dx, yy, z, "minecraft:dark_oak_planks") if False else None
S(hx - 1, FLOORY + 1, hz + 3, "minecraft:dark_oak_fence")
S(hx + 1, FLOORY + 1, hz + 3, "minecraft:dark_oak_fence")
S(hx - 1, FLOORY + 2, hz + 3, "minecraft:soul_lantern[hanging=false]")
S(hx - 3, G + 1, hz + 4, "minecraft:dead_bush")
for (x, z) in ((hx - 4, hz - 4), (hx + 4, hz - 4), (hx + 5, hz + 6), (hx - 4, hz + 6)):
    S(x, gy(x, z) + 1, z, "minecraft:dead_bush")
# bones and skulls around
for (x, z, k) in ((hx - 3, hz + 7, "skeleton_skull"), (hx + 3, hz + 6, "bone_block")):
    if k == "bone_block":
        S(x, gy(x, z) + 1, z, "minecraft:bone_block[axis=y]")
    else:
        S(x, gy(x, z) + 1, z, "minecraft:skeleton_skull[rotation=4]")

# ---------------------------------------------------------------- the balloon silo (Farm Balloons)
sx, sz = SILO
reserve(sx, sz, 5)
SILOTOP = 100
for x in range(sx - 2, sx + 3):
    for z in range(sz - 2, sz + 3):
        wall = x in (sx - 2, sx + 2) or z in (sz - 2, sz + 2)
        S(x, G, z, "minecraft:dark_oak_planks")
        for y in range(G + 1, SILOTOP):
            if wall:
                corner = x in (sx - 2, sx + 2) and z in (sz - 2, sz + 2)
                band = (y - G) % 6 == 0
                b = ("minecraft:dark_oak_log[axis=y]" if corner else "minecraft:stripped_dark_oak_log[axis=x]" if band else
                     "minecraft:spruce_planks" if hash01(x, y, z, 21) < 0.85 else "minecraft:mossy_cobblestone")
                S(x, y, z, b)
            else:
                S(x, y, z, "minecraft:air")
for y in (G + 1, G + 2):
    S(sx, y, sz - 2, "minecraft:air")
for y in range(G + 1, SILOTOP + 2):
    S(sx, y, sz + 1, "minecraft:ladder[facing=north]")
for x in range(sx - 3, sx + 4):
    for z in range(sz - 3, sz + 4):
        S(x, SILOTOP, z, "minecraft:dark_oak_planks" if (x + z) % 2 else "minecraft:spruce_planks")
S(sx, SILOTOP, sz + 1, "minecraft:air")
for x in range(sx - 3, sx + 4):
    for z in range(sz - 3, sz + 4):
        if x in (sx - 3, sx + 3) or z in (sz - 3, sz + 3):
            S(x, SILOTOP + 1, z, "minecraft:dark_oak_fence")
for y in range(G + 8, SILOTOP, 8):
    S(sx, y, sz - 1, "minecraft:soul_lantern[hanging=false]") if False else None
for (x, z) in ((sx - 2, sz - 3), (sx + 2, sz - 3), (sx - 3, sz + 3), (sx + 3, sz + 3)):
    S(x, SILOTOP + 2, z, "minecraft:dark_oak_fence")
    S(x, SILOTOP + 3, z, "minecraft:soul_lantern[hanging=false]")
BALL_COLORS = ["red", "yellow", "light_blue", "lime", "orange", "pink", "white", "purple"]
for k, (bdx, bdz, bh, br) in enumerate([(-4, -3, 12, 3), (4, -2, 16, 3), (0, 4, 14, 2), (-5, 3, 19, 2), (5, 4, 10, 2),
                                          (0, -5, 20, 3), (-2, 0, 24, 3), (4, 0, 25, 2)]):
    bx_, bz_ = sx + bdx, sz + bdz
    by = SILOTOP + bh
    col = BALL_COLORS[k % len(BALL_COLORS)]
    for dx in range(-br, br + 1):
        for dy in range(-br - 1, br + 1):
            for dz in range(-br, br + 1):
                if dx * dx + (dy * 0.85) ** 2 + dz * dz <= br * br + 0.5:
                    S(bx_ + dx, by + dy, bz_ + dz, f"minecraft:{col}_wool" if hash01(dx, dy, dz, k) > 0.12 else
                      f"minecraft:{col}_terracotta")
    # the string: chain down to the deck rail
    tx, tz = max(sx - 3, min(sx + 3, bx_)), max(sz - 3, min(sz + 3, bz_))
    for y in range(SILOTOP + 2, by - br - 1):
        S(bx_, y, bz_, "minecraft:iron_chain[axis=y]")
S(sx - 3, SILOTOP + 2, sz + 3, "minecraft:dark_oak_fence")

# ---------------------------------------------------------------- the Great Beanstalk
BX0, BZ0 = STALK
STALK_TOP = 152
BASE_Y = G + 1


def centre(y):
    t = y - G
    return (BX0 + 2.0 * math.sin(t * 0.11) + 1.0 * math.sin(t * 0.27 + 1.0),
            BZ0 + 2.0 * math.cos(t * 0.09 + 1.0) + 1.0 * math.cos(t * 0.23))


def radius(y):
    t = (y - G) / (STALK_TOP - G)
    return 3.7 - 1.5 * t + 0.5 * math.sin((y - G) * 0.45)


STALKB = area.mix({"moss_block": 5, "lime_terracotta": 3, "green_terracotta": 3, "lime_concrete": 1, "green_concrete": 1,
                   "melon": 1})
stalk_cells = set()
for y in range(G + 1, STALK_TOP + 1):
    cx, cz = centre(y)
    r = radius(y) + (1.8 * max(0.0, 1 - (y - G) / 6.0))      # flared roots
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            if math.hypot(x - cx, z - cz) <= r + 0.5 * noise(x, z + y, 5, 3.0):
                S(x, y, z, STALKB(x, y, z))
                stalk_cells.add((x, y, z))
# the stalk's rootlets reach into the ground
for k in range(10):
    ang = k * math.pi / 5
    for rr in range(3, 9):
        x, z = int(round(BX0 + rr * math.cos(ang))), int(round(BZ0 + rr * math.sin(ang)))
        if (x, z) in T and hash01(x, 1, z, 81) < 0.8:
            S(x, T[(x, z)] + 1, z, "minecraft:oak_log[axis=x]" if False else "minecraft:moss_block")
            if rr < 6:
                S(x, T[(x, z)] + 2, z, "minecraft:moss_carpet")

# the spiral of leaf pads (2x2, one block higher every step) with leaves around and small landings
R_SP = 7.0
STEPS = STALK_TOP - G - 3
spiral = []
pad_air = set()
for k in range(STEPS):
    y = G + 1 + k
    ang = math.radians(100 + 22.0 * k)
    cx, cz = centre(y)
    px = int(round(cx + R_SP * math.cos(ang)))
    pz = int(round(cz + R_SP * math.sin(ang)))
    spiral.append((px, y, pz))
for k, (px, y, pz) in enumerate(spiral):
    for dx in (0, 1):
        for dz in (0, 1):
            S(px + dx, y, pz + dz, "minecraft:moss_block" if (dx + dz + k) % 3 else "minecraft:lime_terracotta")
            for yy in range(y + 1, y + 5):
                area.clear(px + dx, yy, pz + dz)
                pad_air.add((px + dx, yy, pz + dz))
    # the pad's frill
    for dx in range(-1, 3):
        for dz in range(-1, 3):
            if (dx in (-1, 2) or dz in (-1, 2)) and hash01(px + dx, y, pz + dz, 9) < 0.45 and area.get(px + dx, y, pz + dz) is None:
                S(px + dx, y, pz + dz, "minecraft:jungle_leaves[persistent=true]")
    # tethers: a vine hangs under every few pads
    if k % 5 == 2:
        for yy in range(y - 1, max(G + 1, y - 7), -1):
            if area.get(px, yy, pz) is None:
                S(px, yy, pz, "minecraft:vine[up=true]") if False else None
    # landings every 16 steps: a big leaf
    if k % 16 == 15:
        for dx in range(-3, 5):
            for dz in range(-3, 5):
                if math.hypot(dx - 0.5, dz - 0.5) <= 3.6 and area.get(px + dx, y, pz + dz) is None:
                    S(px + dx, y, pz + dz, "minecraft:moss_block" if hash01(px + dx, 1, pz + dz, 4) < 0.7 else "minecraft:jungle_leaves[persistent=true]")
                    for yy in range(y + 1, y + 5):
                        area.clear(px + dx, yy, pz + dz)
                        pad_air.add((px + dx, yy, pz + dz))
        S(px + 2, y + 1, pz + 2, "minecraft:soul_lantern[hanging=false]")

# big drooping leaves growing off the stalk
for k in range(14):
    y = G + 6 + int(k * (STALK_TOP - G - 14) / 13) + int(hash01(k, 0, 0, 91) * 4)
    ang = k * 2.4 + 0.7
    cx, cz = centre(y)
    r0 = radius(y)
    L = 7 + int(hash01(k, 1, 0, 91) * 4)
    for i in range(int(r0), int(r0) + L):
        wdt = 3 * math.sin(math.pi * (i - r0 + 1) / (L + 1))
        for s in range(-int(wdt) - 1, int(wdt) + 2):
            if abs(s) > wdt:
                continue
            x = int(round(cx + i * math.cos(ang) - s * math.sin(ang)))
            z = int(round(cz + i * math.sin(ang) + s * math.cos(ang)))
            yy = y - int((i - r0) * 0.35)
            if area.get(x, yy, z) is None and (x, yy, z) not in pad_air:
                S(x, yy, z, "minecraft:jungle_leaves[persistent=true]" if abs(s) < 1 or hash01(x, yy, z, 7) < 0.7 else
                  "minecraft:lime_wool")

# vines on the stalk
VS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
for (x, y, z) in sorted(stalk_cells):
    if hash01(x, y, z, 55) > 0.075:
        continue
    for face, (dx, dz) in VS.items():
        n = (x + dx, y, z + dz)
        if n not in stalk_cells and area.get(*n) is None:
            ln = 2 + int(hash01(x, y, z, 56) * 7)
            for i in range(ln):
                if (x + dx, y - i, z + dz) in stalk_cells or area.get(x + dx, y - i, z + dz) is not None:
                    break
                S(x + dx, y - i, z + dz, f"minecraft:vine[{OPP[face]}=true]")
            break

# the crown: a bud with a bright flower and the platform with the soul
tcx, tcz = centre(STALK_TOP)
TOPY = G + 1 + STEPS                   # the platform's block y
for dx in range(-9, 10):
    for dz in range(-9, 10):
        d = math.hypot(dx, dz)
        x, z = int(round(tcx)) + dx, int(round(tcz)) + dz
        if d <= 8.4 and area.get(x, TOPY, z) is None and (x, TOPY, z) not in stalk_cells:
            S(x, TOPY, z, "minecraft:moss_block" if d < 7.4 else "minecraft:jungle_leaves[persistent=true]")
            if d > 3.5:
                for yy in range(TOPY + 1, TOPY + 4):
                    area.clear(x, yy, z)
        if 5.5 < d <= 8.4 and hash01(x, 1, z, 3) < 0.3:
            S(x, TOPY - 1, z, "minecraft:jungle_leaves[persistent=true]")
SOUL_TOP = (int(round(tcx)) + 5, TOPY + 1, int(round(tcz)))
S(SOUL_TOP[0] + 1, TOPY + 1, SOUL_TOP[2] - 2, "minecraft:soul_lantern[hanging=false]")
S(SOUL_TOP[0] + 1, TOPY + 1, SOUL_TOP[2] + 2, "minecraft:soul_lantern[hanging=false]")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) + abs(dz) == 3:
            S(int(round(tcx)) + dx, STALK_TOP + 1, int(round(tcz)) + dz, "minecraft:lime_wool")
reserve(BX0, BZ0, 11)

# ---------------------------------------------------------------- the cracked wall and Montezuma's nook
NX, NZ = NOOK
wx0, wx1 = NX - 8, NX + 7
nz0, nz1 = NZ - 6, WALL_Z                     # the ruin's outer rectangle: z -206..-194
reserve_rect(wx0 - 2, nz0 - 2, wx1 + 2, nz1 + 3)
BR = area.mix({"stone_bricks": 5, "mossy_stone_bricks": 3, "cracked_stone_bricks": 2})
for x in range(wx0, wx1 + 1):
    for z in range(nz0, nz1 + 1):
        S(x, 71, z, "minecraft:mossy_stone_bricks" if hash01(x, 1, z, 5) < 0.5 else "minecraft:stone_bricks")
        top_y = 71 + 6
        for y in range(72, top_y + 1):
            inner = nz0 < z < nz1 and wx0 < x < wx1
            if inner:
                continue
            # a ruined crest: random missing blocks near the top
            if y > 74 and hash01(x, y, z, 17) < 0.25 * (y - 74):
                continue
            S(x, y, z, BR(x, y, z))
# the nook (x -29..-21, z -204..-196, 3-4 high), a stone ceiling
for x in range(NX - 4, NX + 4):
    for z in range(NZ - 5, NZ + 2):
        for y in range(72, 76):
            S(x, y, z, "minecraft:air")
        S(x, 71, z, "minecraft:mossy_cobblestone" if hash01(x, 2, z, 5) < 0.5 else "minecraft:stone_bricks")
        S(x, 76, z, "minecraft:stone_bricks")
        S(x, 77, z, "minecraft:stone_brick_slab[type=bottom]")
for x in range(NX - 5, NX + 5):
    for z in range(NZ - 6, NZ + 3):
        if x in (NX - 5, NX + 4) or z in (NZ - 6, NZ + 2):
            for y in range(72, 77):
                S(x, y, z, BR(x, y, z))
# the cracked wall itself (z -195..-194) in a plain stone-brick front, three cracked blocks wide
for x in range(NX - 5, NX + 5):
    for z in (NZ + 3, NZ + 4):
        for y in range(72, 77):
            if abs(x - NX) <= 1 and y <= 74:
                S(x, y, z, "minecraft:cracked_stone_bricks")
            else:
                S(x, y, z, BR(x, y, z))
for x in range(NX - 1, NX + 2):                  # the nook's own south wall is cracked too (three wide, three tall)
    for y in range(72, 75):
        S(x, y, NZ + 2, "minecraft:cracked_stone_bricks")
for y in range(72, 77):                          # lintel above the door-sized gap that is the cracked section
    pass
# inside: a lantern, hay and a barrel, a broken scythe of fence
S(NX - 3, 72, NZ - 4, "minecraft:soul_lantern[hanging=false]")
S(NX + 3, 72, NZ - 4, "minecraft:soul_lantern[hanging=false]")
S(NX + 3, 72, NZ - 3, "minecraft:barrel[facing=up]")
S(NX - 3, 72, NZ - 3, "minecraft:hay_block[axis=x]")
S(NX - 3, 72, NZ - 2, "minecraft:cobweb")
S(NX, 75, NZ - 2, "minecraft:soul_lantern[hanging=true]")
# a few cracked bricks and wall marks on the outside to hint at it
for (x, y) in ((NX - 3, 73), (NX + 3, 74), (NX - 2, 76)):
    S(x, y, NZ + 4, "minecraft:cracked_stone_bricks")
# the steps up to the wall from the road (terrain is 71 around the ruin already; lanterns by the front)
for dx in (-4, 4):
    for h in range(1, 4):
        S(NX + dx, 72 + h - 1, NZ + 6, "minecraft:cobblestone_wall") if h < 3 else S(NX + dx, 74, NZ + 6, "minecraft:soul_lantern[hanging=false]")
air_box(NX - 1, 72, NZ + 5, NX + 1, 75, NZ + 6)

# ---------------------------------------------------------------- the flower pot trail (Farm Flower Pot) and buttons
POTS = ["potted_poppy", "potted_dead_bush", "potted_azure_bluet", "potted_wither_rose", "potted_red_mushroom",
        "potted_brown_mushroom", "potted_oxeye_daisy", "potted_cornflower", "potted_allium", "potted_fern"]
trail = [(-98, -188), (-95, -190), (-96, -193), (-100, -194), (-101, -197), (-98, -200), (-95, -201), (-93, -203)]
pot_i = 0
for (x1, z1), (x2, z2) in zip(trail, trail[1:]):
    n = max(abs(x2 - x1), abs(z2 - z1))
    for i in range(0, n, 2):
        x, z = int(round(x1 + (x2 - x1) * i / n)), int(round(z1 + (z2 - z1) * i / n))
        if (x, z) not in T:
            continue
        y = gy(x, z)
        S(x + 1, y + 1, z, "minecraft:" + POTS[pot_i % len(POTS)])
        pot_i += 1
reserve_rect(-104, -206, -90, -186)
# the final pedestal: a stack of pots on logs
FPX, FPZ = -92, -204
S(FPX, gy(FPX, FPZ), FPZ, "minecraft:podzol")
for k in range(3):
    S(FPX, gy(FPX, FPZ) + 1 + k, FPZ, "minecraft:dark_oak_log[axis=y]")
S(FPX, gy(FPX, FPZ) + 4, FPZ, "minecraft:potted_wither_rose")
S(FPX - 1, gy(FPX - 1, FPZ) + 1, FPZ, "minecraft:potted_poppy")
S(FPX + 1, gy(FPX + 1, FPZ) + 1, FPZ, "minecraft:potted_dead_bush")

# the wall of buttons (x -84..-70, z -196), west to east
BW_Z = -196
for x in range(-85, -69):
    y = gy(x, BW_Z)
    for h in range(1, 4):
        if hash01(x, h, 0, 31) < 0.93:
            S(x, y + h, BW_Z, "minecraft:dark_oak_planks" if (x + h) % 3 else "minecraft:spruce_planks")
    S(x, y + 4, BW_Z, "minecraft:dark_oak_slab[type=bottom]")
    if -84 <= x <= -71:
        S(x, y + 2, BW_Z + 1, f"minecraft:{'oak' if x % 3 else 'birch'}_button[face=wall,facing=south,powered=false]")
reserve_rect(-87, -200, -68, -194)
for (x, z) in ((-86, BW_Z + 1), (-69, BW_Z + 1)):
    lantern_post(x, z)

# ---------------------------------------------------------------- lamps along the roads, dead trees, wild plants
lamps = 0
for name, (pts, w) in PATHS.items():
    if name in ("pots", "farmhouse", "barn"):
        continue
    total = 0.0
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        length = math.hypot(x2 - x1, z2 - z1)
        n = int(length)
        for i in range(n):
            total += 1
            if total % 13 != 0:
                continue
            px, pz = x1 + (x2 - x1) * i / max(1, n), z1 + (z2 - z1) * i / max(1, n)
            nx, nz = -(z2 - z1) / length, (x2 - x1) / length
            side = 1 if (lamps % 2) else -1
            lx, lz = int(round(px + nx * (w / 2 + 2.5) * side)), int(round(pz + nz * (w / 2 + 2.5) * side))
            if (lx, lz) in T and (lx, lz) not in path_cols and (lx, lz) not in occ:
                lantern_post(lx, lz)
                reserve(lx, lz, 1)
                lamps += 1

# gates at the two crossings (decoration only): crooked posts with a hanging sign-board
for cxg in (CROSS_W, CROSS_P):
    for dx in (-4, 4):
        for h in range(1, 5):
            S(cxg + dx, G + h, -123, "minecraft:dark_oak_log[axis=y]")
        S(cxg + dx, G + 5, -123, "minecraft:soul_lantern[hanging=false]")
    for x in range(cxg - 4, cxg + 5):
        S(x, G + 5, -123, "minecraft:dark_oak_planks") if abs(x - cxg) < 4 else None
        S(x, G + 6, -123, "minecraft:dark_oak_slab[type=bottom]") if abs(x - cxg) < 4 else None
    for dx in (-2, 0, 2):
        S(cxg + dx, G + 4, -123, "minecraft:iron_chain[axis=y]")
        S(cxg + dx, G + 3, -123, "minecraft:hay_block[axis=y]") if dx == 0 else None
    for x in range(cxg - 3, cxg + 4):
        area.clear(x, G + 1, -123) if False else None
    for dx in range(-4, 5):
        occ.add((cxg + dx, -123))

# dead trees (crooked, bare)
for x in range(X0 + 4, X1 - 3):
    for z in range(Z0 + 4, Z1 - 3):
        if (x, z) in occ or (x, z) not in T or z >= -128:
            continue
        if edge(x, z) < 4:
            continue
        if hash01(x, 0, z, 91) > 0.0045:
            continue
        area.dead_tree(x, T[(x, z)] + 1, z, height=5 + int(hash01(x, 3, z, 91) * 5),
                       log="dark_oak_log" if hash01(x, 1, z, 91) < 0.6 else "spruce_log", seed=x + z)
        reserve(x, z, 2)

WILD = (lambda x, z: ("minecraft:dead_bush" if hash01(x, 1, z, 5) < 0.4 else "minecraft:short_grass" if hash01(x, 2, z, 5) < 0.5
                      else "minecraft:fern" if hash01(x, 3, z, 5) < 0.4 else "minecraft:brown_mushroom"
                      if hash01(x, 4, z, 5) < 0.6 else "minecraft:wither_rose" if hash01(x, 5, z, 5) < 0.12
                      else "minecraft:red_mushroom"))
area.scatter({c for c in T if c not in path_cols}, 0.22, WILD, seed=4, avoid=lambda x, z: (x, z) in occ)

# ---------------------------------------------------------------- NPCs, souls, spawn spots (standing: surface + 1)
area.npc("Shania (Rift)", -56, 69, -141, 0.0, "in front of the farmhouse door (farming requests, Proto-Chicken)")
area.npc("Sick Farmer", -74, 69, -156, 270.0, "at the south-west corner of the crop field, by a crooked fence")
area.npc("The Baba Yaga", HUT[0], FLOORY + 1, HUT[1], 0.0, "a Porhtal eye in the crooked hut on chicken-leg stilts")
area.npc("enigma:Farm Flower Pot", FPX, gy(FPX, FPZ + 1) + 1, FPZ + 1, 0.0, "end of the flower-pot trail, north-west corner")
area.npc("enigma:Buttons", -77, gy(-77, BW_Z - 2) + 1, BW_Z - 2, 180.0, "behind the wall of wooden buttons (press them all)")
area.npc("enigma:Farm Balloons", SILO[0] + 2, SILOTOP + 1, SILO[1] - 2, 0.0, "on the deck at the top of the balloon silo")
area.npc("enigma:Beanstalk", SOUL_TOP[0], SOUL_TOP[1], SOUL_TOP[2], 0.0, "on the leaf crown at the top of the Great Beanstalk")
area.npc("spawn:montezuma_5", NX, 72, NZ, 0.0, "behind the cracked stone wall in the north-east ruin")
area.arrival(CROSS_W, 69, -123, 0.0)

# ---------------------------------------------------------------- zones and mobs


def mob(kind, name, level, health, damage, xp, coins, mx_, every, a, b, drops=None):
    """A Rift mob: `coins` are the Motes it drops (the mod pays Motes for kills in the Rift)."""
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


VOLT = [drop("VOLT_CRUX", 1, 1, 1)]
area.zone("Dreadfarm", box=((X0, 56, Z0), (X1, 170, Z1 - 1)), blocks={}, mobs=[
    mob("minecraft:zombie", "Volt", 4, 160, 7, 6, 155, 4, 8, (-88, 67, -186), (-61, 76, -156), VOLT),
    mob("minecraft:zombie", "Volt", 4, 160, 7, 6, 155, 4, 8, (-52, 67, -182), (-28, 76, -158), VOLT),
    mob("minecraft:zombie", "Volt", 4, 160, 7, 6, 155, 3, 10, (-70, 67, -204), (-45, 76, -186), VOLT),
    mob("minecraft:chicken", "Not Chicken", 1, 3, 0, 1, 0, 6, 6, (-104, 67, -134), (-92, 72, -123)),
    mob("minecraft:chicken", "Not Chicken", 1, 3, 0, 1, 0, 6, 6, (-88, 67, -134), (-77, 72, -123)),
])

# fences connect to their neighbours (the structure is placed as plain posts otherwise)
for (x, y, z), (entry, data) in list(area.piece.cells.items()):
    bid = entry["id"]
    if not bid.endswith("_fence") or "nether" in bid:
        continue
    ox, oy, oz = area.origin
    wx, wz = x + ox, z + oz
    props = []
    for face, (dx, dz) in VS.items():
        n = area.piece.cells.get((x + dx, y, z + dz))
        nid = n[0]["id"] if n else ""
        ok = nid.endswith("_fence") and "nether" not in nid or nid.endswith("_fence_gate") or (
            n and nid not in ("minecraft:air",) and not nid.endswith(("_slab", "_stairs", "_leaves", "_door", "_pane", "ladder", "lantern", "chain",
                                                                      "button", "plate", "pot", "bush", "mushroom", "grass", "fern", "rose",
                                                                      "vine", "cobweb", "pumpkin", "wool", "skull", "wheat", "torch"))
            and nid.split(":")[1] in ("dark_oak_planks", "spruce_planks", "dark_oak_log", "stripped_dark_oak_log", "hay_block",
                                      "cobblestone", "mossy_cobblestone", "oak_planks", "stone_bricks", "cracked_stone_bricks",
                                      "mossy_stone_bricks"))
        props.append(f"{face}={'true' if ok else 'false'}")
    area.piece.set(x, y, z, bid + "[" + ",".join(props) + "]")

area.save()
print("lamps", lamps, "spiral steps", len(spiral), "soul top", SOUL_TOP, "pots", pot_i)
