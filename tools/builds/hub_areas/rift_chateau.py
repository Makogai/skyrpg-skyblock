#!/usr/bin/env python3
"""The Rift, Stillgore Chateau (x 200..299, z -240..-31): a gothic vampire castle on a crag. Blackstone, deepslate bricks,
red nether bricks, crimson wood, dark and red glass, chains and soul lanterns. A curtain wall with two gates (north z -178,
south z -84) around two courtyards and the Great Hall (the Vampire Slayer altar; a cellar under it reached by holes and a
stair trench), a throne room (the Prince), corner towers, the tall climbable Fairylosophy Tower, the Balloon Tower, a glass
conservatory, a glass mausoleum, a floor-hole shaft, a graveyard nook, six Blood Effigies on the grounds, a flower-pot
trail, and the Oubliette: a dungeon prison under courtyard B (floor y 45) whose spiral stair climbs out to the courtyard.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_chateau.py

The Colosseum meets the west edge (crossing z -180) and the Photon Pathway too (crossing z -80): the surface keeps y 68
within SEAM of x 200 there. Every other side is the Rift's outer edge (jagged cliffs, hanging underside, floating shards).
"""
import math

from kit import hash01, noise
from rift_lib import new_area, land, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

assert CROSSINGS[("photon_pathway", "chateau")] == ("x", 200, -80)
assert CROSSINGS[("colosseum", "chateau")] == ("x", 200, -180)

area = new_area("chateau", version=1)
area.shell = 6
S = area.set
G = GROUND
P = 78                                   # the plateau's surface block y (players walk at 79)
X0, X1, Z0, Z1 = 200, 299, -240, -31
AIR = "minecraft:air"
CROSS_Z = (-180, -80)


def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def jag(a, b, s):
    return 5 * noise(a, b, s, 1.2) + 2.5 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 1.2 * (hash01(int(a), 2, int(b), s) - 0.5)


def west_x(z):
    base = 204 + 0.6 * jag(z, 0, 1)
    for zc in CROSS_Z:
        t = max(0.0, min(1.0, (abs(z - zc) - 8) / 4.0))
        base = min(base, 199 + t * (base - 199))
    return base


def edge(x, z):
    """Distance in blocks to the Rift's outer edge (negative = void)."""
    dw = x - west_x(z)
    de = (295 + 1.3 * jag(z, 9, 2)) - x
    dn = z - (-235 + 1.3 * jag(x, 5, 3))
    ds = (-36 + 1.3 * jag(x, 7, 4)) - z
    return min(dw, de, dn, ds)


def rect_d(x, z, r):
    return math.hypot(max(r[0] - x, 0, x - r[2]), max(r[1] - z, 0, z - r[3]))


PLAT = (226, -196, 290, -70)
CASTLE = (226, -184, 288, -78)
EFFIGIES = [(238, -190), (274, -190), (288, -150), (286, -74), (246, -74), (228, -132)]
GRAVE = (246, -192, 254, -186)              # the graveyard nook, outside the north wall
PADS = [(ex - 4, ez - 4, ex + 4, ez + 4) for ex, ez in EFFIGIES] + [(GRAVE[0] - 3, GRAVE[1] - 3, GRAVE[2] + 3, GRAVE[3] + 3)]


def in_pad(x, z):
    return any(r[0] <= x <= r[2] and r[1] <= z <= r[3] for r in PADS)


def top_y(x, z):
    d = rect_d(x, z, PLAT)
    rise = 10 * sm(1 - d / 20)
    gate = sm((x - 207) / 10.0)
    off = 0.0
    if d > 0:
        off = 3.2 * noise(x, z, 11, 1.8) * min(1.0, d / 5) * gate
    else:
        dc = rect_d(x, z, CASTLE)
        if dc > 0 and not in_pad(x, z):
            off = 2.4 * noise(x, z, 13, 2.2) * sm(dc / 6.0) * gate
    return G + int(round(rise + off))


# ---------------------------------------------------------------- roads (the crossings continue to the two gates)
PATHS = {
    "cross_n": ([(200, -180), (212, -180)], 5),
    "road_n": ([(212, -180), (260, -180), (260, -177)], 3),
    "cross_s": ([(200, -80), (212, -80)], 5),
    "road_s": ([(212, -80), (260, -80), (260, -85)], 3),
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

T = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        if edge(x, z) < 0:
            continue
        T[(x, z)] = top_y(x, z)
for (x, z), name in path_cols.items():          # roads keep their own level on the grounds (the crossing stays y 68)
    if (x, z) in T and x <= 207:
        T[(x, z)] = G

# ---------------------------------------------------------------- ground
SURF = area.mix({"grass_block": 4, "podzol": 3, "coarse_dirt": 2, "rooted_dirt": 1, "gravel": 1})
SURF_CROSS = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3})
SURF_ROAD = area.mix({"coarse_dirt": 3, "gravel": 3, "cobbled_deepslate": 2, "rooted_dirt": 1, "polished_blackstone_bricks": 1})
UNDER = area.mix({"coarse_dirt": 3, "tuff": 3, "blackstone": 2, "rooted_dirt": 1})
BODY = area.mix({"deepslate": 5, "tuff": 4, "blackstone": 3, "basalt": 1, "andesite": 1, "polished_tuff": 1})
UNDERSIDE = area.mix({"tuff": 5, "deepslate": 4, "blackstone": 3, "basalt": 1, "calcite": 1})


def under_depth(x, z, e):
    t = sm(e / 55.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


for (x, z), top in T.items():
    e = edge(x, z)
    nm = path_cols.get((x, z))
    if nm and x <= 214:
        surf = SURF_CROSS
    elif nm:
        surf = SURF_ROAD
    elif e < 2.5:
        surf = lambda x, y, z: "minecraft:blackstone" if hash01(x, 8, z, 1) < 0.5 else "minecraft:basalt[axis=y]"
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

# floating shards beyond the outer cliffs
for i in range(60):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 6))
    if edge(x, z) > -3 or edge(x, z) < -16 or x < 214 or i > 24:
        continue
    y = 50 + int(hash01(i, 2, 0, 77) * 34)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    b = "minecraft:tuff" if q < 0.7 else "minecraft:blackstone"
                    if hash01(x + dx, y + dy, z + dz, 5) < 0.08:
                        b = "minecraft:crying_obsidian"
                    S(x + dx, y + dy, z + dz, b)
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:amethyst_block" if k % 2 == 0 else "minecraft:crying_obsidian")

# ---------------------------------------------------------------- materials and helpers
WALL = area.mix({"deepslate_bricks": 6, "polished_blackstone_bricks": 3, "cracked_deepslate_bricks": 2, "red_nether_bricks": 1,
                 "blackstone": 1})
FLOOR = area.mix({"deepslate_tiles": 5, "polished_blackstone_bricks": 3, "cracked_deepslate_tiles": 1, "polished_deepslate": 2,
                  "cracked_polished_blackstone_bricks": 1})
ROOF = "minecraft:deepslate_tiles"
occ = set()                                 # columns kept free of trees and flowers


def clear_box(x0, y0, z0, x1, y1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                area.clear(x, y, z)


def reserve(x0, z0, x1, z1):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            occ.add((x, z))


def lamp(x, y, z, h=3):
    for k in range(h):
        S(x, y + k, z, "minecraft:polished_blackstone_brick_wall")
    S(x, y + h, z, "minecraft:soul_lantern[hanging=false]")


def chandelier(x, y, z, length=3):
    """A soul lantern on chains from a ceiling at y+length+1."""
    for k in range(length):
        S(x, y + 1 + k, z, "minecraft:iron_chain[axis=y]")
    S(x, y, z, "minecraft:soul_lantern[hanging=true]")


DIRS = {"e": (1, 0), "w": (-1, 0), "s": (0, 1), "n": (0, -1)}


def ring_cells(r):
    ring, inner = [], []
    for dx in range(-r - 1, r + 2):
        for dz in range(-r - 1, r + 2):
            d = math.hypot(dx, dz)
            if d < r - 0.5:
                inner.append((dx, dz))
            elif d < r + 0.5:
                ring.append((dx, dz))
    return ring, inner


def spiral(cx, cz, r_in, r_out, floor_y, n, per_rev, ang0, pick):
    """A spiral stair of full blocks (one up per step) around a pillar: step k has its block at floor_y + 1 + k."""
    step = 2 * math.pi / per_rev
    R = int(r_out) + 1
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            d = math.hypot(dx, dz)
            if d < r_in or d >= r_out:
                continue
            idx = int(math.floor(((math.atan2(dz, dx) - ang0) % (2 * math.pi)) / step + 0.5)) % per_rev
            for k in range(idx, n, per_rev):
                S(cx + dx, floor_y + 1 + k, cz + dz, pick(cx + dx, floor_y + 1 + k, cz + dz))


STEP = area.mix({"polished_blackstone_bricks": 3, "deepslate_bricks": 3, "deepslate_tiles": 2, "crimson_planks": 1})


def door_angle(face):
    dx, dz = DIRS[face]
    return math.atan2(dz, dx)


def tower(cx, cz, r, n, top_extra, door, cone=0, name=""):
    """A round tower from the plateau (floor block y 78) with an interior spiral stair up to a platform at y 78 + n.
    The walls rise `top_extra` above the platform; then a cone roof (cone > 0) or battlements."""
    floor_y = P
    p = floor_y + n
    ytop = p + top_extra
    ring, inner = ring_cells(r)
    r_in = 1.5
    r_out = r - 1.1
    per_rev = 16 if r >= 5 else 14
    ang0 = door_angle(door)
    for (dx, dz) in ring:
        for y in range(floor_y - 1, ytop + 1):
            S(cx + dx, y, cz + dz, WALL(cx + dx, y, cz + dz))
    for (dx, dz) in inner:
        for y in range(floor_y + 1, ytop + 1):
            area.clear(cx + dx, y, cz + dz)
        S(cx + dx, floor_y, cz + dz, FLOOR(cx + dx, floor_y, cz + dz))
    # banding and windows
    for (dx, dz) in ring:
        for y in range(floor_y + 8, ytop, 8):
            S(cx + dx, y, cz + dz, "minecraft:polished_blackstone_bricks")
    for y in range(floor_y + 6, ytop - 2, 9):
        for (dx, dz) in ((r, 0), (-r, 0), (0, r), (0, -r)):
            for yy in (y, y + 1, y + 2):
                S(cx + dx, yy, cz + dz, "minecraft:red_stained_glass_pane" if yy == y + 1 else "minecraft:iron_bars")
    # the pillar and the spiral
    for y in range(floor_y + 1, p):
        S(cx, y, cz, "minecraft:deepslate_bricks")
    spiral(cx, cz, r_in, r_out, floor_y, n, per_rev, ang0, STEP)
    # the platform (with a gap above the last steps)
    skip = {(n - 2) % per_rev, (n - 3) % per_rev, (n - 4) % per_rev}
    step = 2 * math.pi / per_rev
    for (dx, dz) in inner:
        d = math.hypot(dx, dz)
        idx = int(math.floor(((math.atan2(dz, dx) - ang0) % (2 * math.pi)) / step + 0.5)) % per_rev
        if d >= r_in and idx in skip:
            continue
        S(cx + dx, p, cz + dz, "minecraft:polished_blackstone_bricks" if (dx + dz) % 2 else "minecraft:deepslate_tiles")
    # the door: three high, through the wall on the `door` side
    ddx, ddz = DIRS[door]
    for (dx, dz) in ring + [c for c in inner if math.hypot(*c) >= r - 1.6]:
        if dx * ddx + dz * ddz >= r - 1.6 and abs(dx * ddz - dz * ddx) <= 1:
            for y in range(floor_y + 1, floor_y + 4):
                area.clear(cx + dx, y, cz + dz)
    # top: battlements or a cone
    if cone:
        for j in range(cone):
            rad = (r + 1.6) * (1 - j / cone)
            for dx in range(-r - 2, r + 3):
                for dz in range(-r - 2, r + 3):
                    d = math.hypot(dx, dz)
                    if rad - 1.3 <= d <= rad:
                        S(cx + dx, ytop + 1 + j, cz + dz, ROOF if j % 4 != 3 else "minecraft:crimson_planks")
        S(cx, ytop + cone, cz, ROOF)
        for k in range(1, 4):
            S(cx, ytop + cone + k, cz, "minecraft:iron_bars" if k < 3 else "minecraft:lightning_rod[facing=up]")
    else:
        for (dx, dz) in ring:
            if (dx + dz) % 2 == 0:
                S(cx + dx, ytop + 1, cz + dz, "minecraft:polished_blackstone_bricks")
        for (dx, dz) in ((r, 0), (-r, 0), (0, r), (0, -r)):
            lamp(cx + dx, ytop + 1, cz + dz, 1)
    # lights on chains inside
    for y in range(floor_y + 12, p - 2, 14):
        S(cx + r_out - 1 if False else cx, y, cz, "minecraft:deepslate_bricks")
    reserve(cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2)
    return p


# ---------------------------------------------------------------- the paved courtyards and the curtain wall
CA = (234, -111, 280, -86)     # courtyard A (south)
CB = (234, -176, 280, -145)    # courtyard B (north)
HX0, HX1, HZ0, HZ1 = 250, 270, -144, -112      # the Great Hall (outer wall lines)


def paved(x, z):
    n = noise(x, z, 3, 2.6)
    return not (n > 0.5 and hash01(x, 5, z, 8) < 0.9)


for rect in (CA, CB):
    for x in range(rect[0], rect[2] + 1):
        for z in range(rect[1], rect[3] + 1):
            if paved(x, z):
                S(x, P, z, FLOOR(x, P, z))
            else:
                S(x, P, z, "minecraft:moss_block" if hash01(x, 6, z, 8) < 0.5 else "minecraft:podzol")
            area.top[(x, z)] = P
            occ.add((x, z))
# a red processional way from each gate to the Great Hall's doors
for z in range(-111, -85):
    for x in (259, 261):
        S(x, P, z, "minecraft:polished_blackstone_bricks")
    S(260, P, z, "minecraft:red_nether_bricks")
for z in range(-176, -144):
    for x in (259, 261):
        S(x, P, z, "minecraft:polished_blackstone_bricks")
    S(260, P, z, "minecraft:red_nether_bricks")

WX0, WX1, WZ0, WZ1 = 232, 282, -178, -84       # the curtain wall's outer lines
wall_cells = set()
for x in range(WX0, WX1 + 1):
    for z in (WZ0, WZ0 + 1, WZ1 - 1, WZ1):
        wall_cells.add((x, z))
for z in range(WZ0, WZ1 + 1):
    for x in (WX0, WX0 + 1, WX1 - 1, WX1):
        wall_cells.add((x, z))
for (x, z) in wall_cells:
    outer = x in (WX0, WX1) or z in (WZ0, WZ1)
    for y in range(P, P + 7):
        S(x, y, z, WALL(x, y, z))
    if outer and (x + z) % 2 == 0:
        S(x, P + 7, z, "minecraft:polished_blackstone_bricks")
    elif outer and x % 8 == 0 and (x + z) % 2 == 1 and z in (WZ0, WZ1):
        S(x, P + 7, z, "minecraft:soul_lantern[hanging=false]")
# gates: five wide, four high, with a portcullis of bars, chains and a gatehouse block above
for gz in ((WZ0, WZ0 + 1), (WZ1 - 1, WZ1)):
    for x in range(258, 263):
        for z in gz:
            for y in range(P, P + 5):
                area.clear(x, y, z)
            S(x, P, z, "minecraft:red_nether_bricks" if x == 260 else "minecraft:polished_blackstone_bricks")
            S(x, P + 5, z, "minecraft:iron_bars")
            S(x, P + 6, z, "minecraft:deepslate_bricks")
    for z in gz:
        for x in (257, 263):
            for y in range(P, P + 12):
                S(x, y, z, "minecraft:polished_blackstone_bricks" if y % 4 else "minecraft:chiseled_polished_blackstone")
            S(x, P + 12, z, "minecraft:soul_lantern[hanging=false]")
    zo = gz[0] if gz[0] == WZ0 else gz[1]
    for x in range(257, 264):
        S(x, P + 7, zo, "minecraft:red_nether_bricks")
        S(x, P + 8, zo, "minecraft:deepslate_brick_slab[type=bottom]")
    for x in (258, 262):
        chandelier(x, P + 3, zo if zo == WZ0 else zo, 2)
# breaches: the west and east walls are broken in places
for (bx, bz0, bz1) in ((232, -152, -148), (282, -118, -115)):
    for z in range(bz0, bz1 + 1):
        for x in (bx, bx - 1 if bx == 282 else bx + 1):
            for y in range(P + 1, P + 8):
                if hash01(x, y, z, 3) < 0.8:
                    area.clear(x, y, z)
            if hash01(x, 1, z, 3) < 0.5:
                S(x, P, z, "minecraft:cracked_deepslate_bricks")
for (x, z) in wall_cells:
    if (x in (232, 233, 281, 282)) and 2 <= (x + z) % 11 < 3 and hash01(x, 2, z, 4) < 0.6:
        for y in range(P + 5, P + 8):
            area.clear(x, y, z)
reserve(WX0 - 3, WZ0 - 3, WX1 + 3, WZ1 + 3)

# ---------------------------------------------------------------- the Great Hall
for x in range(HX0, HX1 + 1):
    for z in range(HZ0, HZ1 + 1):
        edge_ = x in (HX0, HX1) or z in (HZ0, HZ1)
        S(x, P, z, FLOOR(x, P, z))
        if edge_:
            for y in range(P + 1, P + 14):
                S(x, y, z, WALL(x, y, z))
        else:
            for y in range(P + 1, P + 14):
                area.clear(x, y, z)
# piers and windows on the long walls
for z in range(HZ0, HZ1 + 1):
    for x in (HX0, HX1):
        pier = (z - HZ0) % 5 == 0
        for y in range(P + 1, P + 14):
            if pier:
                S(x, y, z, "minecraft:polished_blackstone_bricks" if y % 5 else "minecraft:chiseled_polished_blackstone")
        if not pier and (z - HZ0) % 5 in (1, 2, 3, 4) and 0 < z - HZ0 < HZ1 - HZ0:
            for y in range(P + 3, P + 12):
                S(x, y, z, "minecraft:red_stained_glass_pane" if 4 <= y - P <= 9 else "minecraft:black_stained_glass_pane")
for x in range(HX0 + 1, HX1):
    for z in (HZ0, HZ1):
        pass
# gables with rose windows, and the steep roof
ry_of = lambda x: P + 14 + (10 - abs(x - 260))
for z in (HZ0, HZ1):
    for x in range(HX0, HX1 + 1):
        for y in range(P + 14, ry_of(x)):
            S(x, y, z, "minecraft:deepslate_bricks")
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            d = math.hypot(dx, dy)
            if d < 4.2:
                S(260 + dx, P + 19 + dy, z, "minecraft:red_stained_glass" if d < 3.2 else "minecraft:polished_blackstone_bricks")
        S(260, P + 19, z, "minecraft:crying_obsidian")
for z in range(HZ0 - 1, HZ1 + 2):
    for x in range(HX0 - 1, HX1 + 2):
        dx = x - 260
        if abs(dx) == 11:
            S(x, P + 13, z, "minecraft:deepslate_tile_stairs[facing=%s,half=bottom]" % ("east" if dx < 0 else "west"))
            continue
        ry = ry_of(x)
        if dx == 0:
            S(x, ry, z, ROOF)
            S(x, ry + 1, z, "minecraft:deepslate_tile_slab[type=bottom]")
        else:
            S(x, ry, z, "minecraft:deepslate_tile_stairs[facing=%s,half=bottom]" % ("east" if dx < 0 else "west"))
            S(x, ry - 1, z, ROOF)
# the floor's carpet, pillars, beams, chandeliers
for z in range(HZ0 + 1, HZ1):
    for x in (259, 260, 261):
        S(x, P + 1, z, "minecraft:red_carpet")
for zz in range(HZ0 + 4, HZ1, 6):
    for x in (254, 266):
        for y in range(P + 1, P + 13):
            S(x, y, zz, "minecraft:polished_blackstone_bricks" if y < P + 12 else "minecraft:chiseled_polished_blackstone")
        S(x, P + 13, zz, "minecraft:deepslate_brick_stairs[facing=%s,half=top]" % ("east" if x < 260 else "west"))
        S(x + (1 if x < 260 else -1), P + 12, zz, "minecraft:deepslate_brick_stairs[facing=%s,half=top]" % ("east" if x < 260 else "west"))
        for dz in (-1, 1):
            S(x, P + 1, zz + dz, "minecraft:red_candle[candles=2,lit=true]") if hash01(x, 1, zz + dz, 6) < 0.6 else None
    for x in range(HX0 + 1, HX1):
        S(x, P + 13, zz, "minecraft:crimson_stem[axis=x]")
    for x in (257, 263):
        chandelier(x, P + 8, zz, 4)
# doors: north and south (the red way), east into the throne room
for x in range(259, 262):
    for z in (HZ0, HZ1):
        for y in range(P + 1, P + 6):
            area.clear(x, y, z)
        S(x, P + 6, z, "minecraft:polished_blackstone_bricks")
for z in (-134, -133, -132):
    for y in range(P + 1, P + 6):
        area.clear(HX1, y, z)
for z in range(HZ0 + 1, HZ1):
    if z % 9 == 0:
        S(HX0 + 1, P + 1, z, "minecraft:chain") if False else None
# the Vampire Slayer altar at the north end
for x in range(255, 266):
    for z in range(-143, -138):
        S(x, P + 1, z, "minecraft:polished_blackstone" if (x + z) % 2 else "minecraft:blackstone")
for dx in range(-3, 4):
    for dz in range(-3, 1):
        d = math.hypot(dx, dz)
        if 2.4 <= d < 3.4 and -143 <= -138 + dz <= -139:
            pass
S(260, P + 2, -143, "minecraft:enchanting_table")
S(257, P + 2, -143, "minecraft:red_candle[candles=3,lit=true]")
S(263, P + 2, -143, "minecraft:red_candle[candles=3,lit=true]")
S(258, P + 2, -143, "minecraft:soul_lantern[hanging=false]")
S(262, P + 2, -143, "minecraft:soul_lantern[hanging=false]")
for x in (255, 265):
    for k in range(P + 2, P + 9):
        S(x, k, -143, "minecraft:chiseled_polished_blackstone" if k % 3 == 0 else "minecraft:polished_blackstone_brick_wall")
S(260, P + 2, -139, "minecraft:red_carpet")
for dx in (-2, -1, 1, 2):
    S(260 + dx, P + 2, -139, "minecraft:red_candle[candles=1,lit=true]") if abs(dx) == 2 else None
# holes in the floor (the cellar below)
HOLES = [(252, -126, 253, -125), (263, -136, 264, -135), (262, -118, 263, -117)]
for (hx0, hz0, hx1, hz1) in HOLES:
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            area.clear(x, P, z)
            for y in range(P + 1, P + 3):
                area.clear(x, y, z)
    for x in range(hx0 - 1, hx1 + 2):
        for z in range(hz0 - 1, hz1 + 2):
            if not (hx0 <= x <= hx1 and hz0 <= z <= hz1) and hash01(x, 3, z, 5) < 0.55:
                S(x, P, z, "minecraft:cracked_deepslate_tiles")

# the cellar under the hall (floor block y 67, ceiling is the hall floor)
for x in range(HX0 + 1, HX1):
    for z in range(HZ0 + 1, HZ1):
        for y in range(68, P):
            area.clear(x, y, z)
        S(x, 67, z, FLOOR(x, 67, z))
for x in range(HX0, HX1 + 1):
    for z in (HZ0, HZ1):
        for y in range(66, P):
            S(x, y, z, WALL(x, y, z))
for z in range(HZ0, HZ1 + 1):
    for x in (HX0, HX1):
        for y in range(66, P):
            S(x, y, z, WALL(x, y, z))
for x in (255, 260, 265):
    for z in (-138, -130, -122, -116):
        for y in range(68, P):
            S(x, y, z, "minecraft:polished_blackstone_bricks" if y not in (68, 77) else "minecraft:chiseled_polished_blackstone")
for x in range(HX0 + 1, HX1):
    for z in range(HZ0 + 1, HZ1):
        h = hash01(x, 9, z, 17)
        if h < 0.05:
            S(x, 68, z, "minecraft:cobweb")
        elif h < 0.075:
            S(x, 68, z, "minecraft:red_candle[candles=%d,lit=true]" % (1 + int(h * 100) % 3))
        elif h < 0.085:
            S(x, 68, z, "minecraft:skeleton_skull[rotation=%d]" % (int(h * 1000) % 16))
for (x, z) in ((258, -135), (258, -125), (263, -128), (253, -119), (267, -134), (253, -133), (267, -121), (262, -141), (256, -141)):
    chandelier(x, 74, z, 3)
# stone coffins along the cellar's west wall
for z in (-140, -134, -128, -122, -116):
    for dz in (0, 1):
        S(252, 68, z + dz, "minecraft:crimson_slab[type=bottom]")
        S(251, 68, z + dz, "minecraft:red_nether_brick_slab[type=bottom]")
    S(252, 69, z, "minecraft:red_candle[candles=2,lit=true]")
# the stair trench from courtyard A down into the cellar (west door of its south wall)
for i in range(12):
    z = -101 - i
    y = P - i
    for x in (253, 254, 255):
        for yy in range(y + 1, y + 6):
            area.clear(x, yy, z)
        S(x, y, z, "minecraft:deepslate_brick_stairs[facing=south,half=bottom]")
        S(x, y - 1, z, "minecraft:deepslate_bricks")
    for x in (252, 256):
        for yy in range(y, P + 1):
            S(x, yy, z, WALL(x, yy, z))
        if i % 3 == 0:
            S(x, P + 1, z, "minecraft:polished_blackstone_brick_wall")
            S(x, P + 2, z, "minecraft:soul_lantern[hanging=false]")
for x in (253, 254, 255):
    for y in range(68, 72):
        area.clear(x, y, HZ1)
    S(x, 72, HZ1, "minecraft:polished_blackstone_bricks")
    for yy in range(67, 72):
        area.clear(x, yy, HZ1 - 1)
reserve(251, -112, 257, -100)

# ---------------------------------------------------------------- the Throne Room (east of the hall)
TX0, TX1, TZ0, TZ1 = 270, 282, -141, -125
for x in range(TX0, TX1 + 1):
    for z in range(TZ0, TZ1 + 1):
        edge_ = x in (TX0, TX1) or z in (TZ0, TZ1)
        S(x, P, z, "minecraft:polished_blackstone_bricks" if (x + z) % 2 else "minecraft:red_nether_bricks")
        for y in range(P + 1, P + 12):
            if edge_:
                S(x, y, z, WALL(x, y, z))
            else:
                area.clear(x, y, z)
        if edge_ and x != TX0:
            S(x, P + 12, z, "minecraft:deepslate_bricks")
            if (x + z) % 2 == 0:
                S(x, P + 13, z, "minecraft:polished_blackstone_bricks")
        elif not edge_:
            S(x, P + 12, z, ROOF)
for z in (-134, -133, -132):
    for y in range(P + 1, P + 6):
        area.clear(TX0, y, z)
for z in range(TZ0 + 1, TZ1, 4):
    for x in (TX1,):
        for y in range(P + 3, P + 9):
            S(x, y, z, "minecraft:red_stained_glass_pane")
for x in range(274, TX1, 4):
    for z in (TZ0, TZ1):
        for y in range(P + 3, P + 9):
            S(x, y, z, "minecraft:red_stained_glass_pane")
for x in range(TX0 + 1, TX1):                       # the red runner and the dais
    for z in (-134, -133, -132):
        S(x, P + 1, z, "minecraft:red_carpet")
for x in range(277, 282):
    for z in range(-137, -128):
        S(x, P + 1, z, "minecraft:blackstone")
S(281, P + 2, -133, "minecraft:crimson_stairs[facing=east,half=bottom]")
for dz in (-1, 1):
    S(281, P + 2, -133 + dz, "minecraft:crimson_stairs[facing=east,half=bottom]")
for k in range(P + 3, P + 8):
    S(281, k, -133, "minecraft:deepslate_brick_wall")
S(281, P + 8, -133, "minecraft:soul_lantern[hanging=false]")
for z in (-137, -129):
    for k in range(P + 2, P + 5):
        S(280, k, z, "minecraft:nether_brick_fence")
    S(280, P + 5, z, "minecraft:soul_lantern[hanging=false]")
for x in (274, 278):
    for z in (-138, -128):
        chandelier(x, P + 7, z, 3)
reserve(TX0, TZ0, TX1, TZ1)

# ---------------------------------------------------------------- the towers
P_FAIRY = tower(239, -128, 6, 60, 2, "e")                 # the Fairylosophy Tower
P_BALLOON = tower(281, -177, 5, 38, 2, "w")                # the Balloon Tower
tower(233, -85, 4, 24, 6, "e", cone=9)
tower(233, -177, 4, 30, 6, "e", cone=10)
tower(281, -85, 4, 20, 2, "w")
FAIRY = (239, P_FAIRY + 1, -128)
# a ring of pinnacles and chains on the Fairylosophy platform
for (dx, dz) in ((4, 0), (-4, 0), (0, 4), (0, -4)):
    for k in range(1, 4):
        S(239 + dx, P_FAIRY + k + 1, -128 + dz, "minecraft:polished_blackstone_brick_wall")
    S(239 + dx, P_FAIRY + 5, -128 + dz, "minecraft:soul_lantern[hanging=false]")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if dx or dz:
            S(239 + dx, P_FAIRY + 1, -128 + dz, "minecraft:red_candle[candles=2,lit=true]") if (dx + dz) % 2 else None
# the balloons over the Balloon Tower: red and black wool, tied by chains to the platform
BALLOON = (281, P_BALLOON + 1, -177)
for (ox, oy, oz, bc) in ((3, 12, 1, "red_wool"), (-3, 11, -2, "black_wool"), (1, 14, -3, "red_wool"), (-2, 15, 3, "black_wool"),
                         (0, 17, 0, "red_wool")):
    cx, cy, cz = 281 + ox, P_BALLOON + oy, -177 + oz
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            for dz in range(-3, 4):
                if dx * dx + dy * dy * 1.2 + dz * dz <= 5.2:
                    S(cx + dx, cy + dy, cz + dz, "minecraft:" + bc)
    for y in range(P_BALLOON + 2, cy - 2):
        if (ox, oz) != (0, 0):
            S(cx, y, cz, "minecraft:iron_chain[axis=y]")
    S(cx, cy - 3, cz, "minecraft:iron_chain[axis=y]")
for y in range(P_BALLOON + 2, P_BALLOON + 14):       # the centre line holds the soul's balloon
    S(281, y, -177, "minecraft:iron_chain[axis=y]")
area.clear(281, P_BALLOON + 1, -177)

# ---------------------------------------------------------------- Courtyard A: the glass conservatory
CX, CZ, CR = 242, -100, 7
for dx in range(-CR - 1, CR + 2):
    for dz in range(-CR - 1, CR + 2):
        for dy in range(0, CR + 2):
            d = math.sqrt(dx * dx + dy * dy + dz * dz)
            if CR - 0.8 <= d < CR + 0.5:
                if (dx + dz + dy) % 7 == 0 or (dx % 5 == 0 and dz % 5 == 0):
                    b = "minecraft:polished_blackstone_bricks"
                elif hash01(CX + dx, dy, CZ + dz, 9) < 0.62:
                    b = "minecraft:red_stained_glass"
                else:
                    b = "minecraft:black_stained_glass"
                S(CX + dx, P + 1 + dy, CZ + dz, b)
        d2 = math.hypot(dx, dz)
        if d2 < CR - 0.8:
            S(CX + dx, P, CZ + dz, "minecraft:moss_block" if hash01(dx, 1, dz, 3) < 0.55 else "minecraft:podzol")
            for dy in range(1, CR):
                area.clear(CX + dx, P + dy, CZ + dz)
            if 3.0 < d2 and hash01(dx, 2, dz, 5) < 0.45:
                S(CX + dx, P + 1, CZ + dz, ["minecraft:poppy", "minecraft:red_tulip", "minecraft:crimson_roots", "minecraft:wither_rose"]
                  [int(hash01(dx, 3, dz, 5) * 4) % 4])
for dx in range(-2, 3):                                    # the puzzle's stone circle
    for dz in range(-2, 3):
        d2 = math.hypot(dx, dz)
        if d2 < 2.6:
            S(CX + dx, P, CZ + dz, "minecraft:chiseled_polished_blackstone" if d2 < 1.2 else
              ("minecraft:red_concrete" if (dx + dz) % 2 else "minecraft:black_concrete"))
            for dy in range(1, 4):
                area.clear(CX + dx, P + dy, CZ + dz)
for (dx, dz) in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
    S(CX + dx, P + 1, CZ + dz, "minecraft:polished_blackstone_brick_wall")
    S(CX + dx, P + 2, CZ + dz, "minecraft:soul_lantern[hanging=false]")
for dz in range(CR - 2, CR + 2):                           # the door, south side
    for dx in (-1, 0, 1):
        for dy in range(1, 5):
            area.clear(CX + dx, P + dy, CZ + dz)
        S(CX + dx, P, CZ + dz, "minecraft:polished_blackstone_bricks")
SOUL_PUZZLE = (CX, P + 1, CZ)
reserve(CX - CR - 1, CZ - CR - 1, CX + CR + 1, CZ + CR + 1)

# ---------------------------------------------------------------- Courtyard B: the arena, the glass mausoleum, the hole shaft
AX, AZ = 260, -161
for dx in range(-10, 11):
    for dz in range(-10, 11):
        d = math.hypot(dx, dz)
        if d < 9.3:
            b = FLOOR(AX + dx, P, AZ + dz)
            if d < 1.6:
                b = "minecraft:crying_obsidian"
            elif 3.5 <= d < 4.5 or 8.0 <= d < 9.3:
                b = "minecraft:red_nether_bricks" if (dx + dz) % 3 else "minecraft:chiseled_polished_blackstone"
            elif 5.5 <= d < 6.2:
                b = "minecraft:polished_blackstone_bricks"
            S(AX + dx, P, AZ + dz, b)
for k in range(8):
    a = k * math.pi / 4
    x, z = int(round(AX + 9.8 * math.cos(a))), int(round(AZ + 9.8 * math.sin(a)))
    lamp(x, P + 1, z, 3)
reserve(AX - 11, AZ - 11, AX + 11, AZ + 11)

MX0, MX1, MZ0, MZ1 = 272, 278, -172, -166                  # the glass mausoleum
for x in range(MX0, MX1 + 1):
    for z in range(MZ0, MZ1 + 1):
        edge_ = x in (MX0, MX1) or z in (MZ0, MZ1)
        S(x, P, z, "minecraft:polished_blackstone_bricks")
        for y in range(P + 1, P + 6):
            if edge_:
                corner = x in (MX0, MX1) and z in (MZ0, MZ1)
                S(x, y, z, "minecraft:polished_blackstone_bricks" if corner else
                  ("minecraft:red_stained_glass" if hash01(x, y, z, 2) < 0.6 else "minecraft:black_stained_glass"))
            else:
                area.clear(x, y, z)
for j in range(4):                                         # a glass pyramid roof
    for x in range(MX0 + j, MX1 - j + 1):
        for z in range(MZ0 + j, MZ1 - j + 1):
            if x in (MX0 + j, MX1 - j) or z in (MZ0 + j, MZ1 - j) or j == 3:
                S(x, P + 6 + j, z, "minecraft:red_stained_glass" if (x + z) % 3 else "minecraft:black_stained_glass")
S(275, P + 10, -169, "minecraft:lightning_rod[facing=up]")
for x in (274, 275, 276):
    S(x, P + 1, -169, "minecraft:crimson_slab[type=bottom]")
S(275, P + 2, -169, "minecraft:red_candle[candles=3,lit=true]")
for z in (-170, -168):
    S(275, P + 1, z, "minecraft:soul_lantern[hanging=false]")
for y in range(P + 1, P + 4):
    area.clear(275, y, MZ1)
reserve(MX0 - 1, MZ0 - 1, MX1 + 1, MZ1 + 1)

# the hole shaft (the Holes soul at its bottom); a ladder up the south wall
SH = (240, -172)
BOT = 62
clear_box(SH[0] - 1, BOT + 1, SH[1] - 1, SH[0] + 1, P + 2, SH[1] + 1)
for x in range(SH[0] - 3, SH[0] + 4):                       # the chamber at the bottom
    for z in range(-176, -170):
        S(x, BOT, z, "minecraft:deepslate_tiles" if hash01(x, 1, z, 4) < 0.7 else "minecraft:cracked_deepslate_tiles")
        for y in range(BOT + 1, BOT + 5):
            area.clear(x, y, z)
for x in range(SH[0] - 4, SH[0] + 5):
    for z in range(-177, -169):
        for y in range(BOT, BOT + 6):
            if (x in (SH[0] - 4, SH[0] + 4) or z in (-177, -170)) and area.get(x, y, z) in (None, AIR):
                S(x, y, z, WALL(x, y, z))
for y in range(BOT + 1, P + 1):
    S(SH[0], y, -171, "minecraft:ladder[facing=north]")
    if y < P:
        S(SH[0], y, -170, WALL(SH[0], y, -170)) if area.get(SH[0], y, -170) in (None, AIR) else None
for (dx, dz) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
    S(SH[0] + 2 * dx, P + 1, SH[1] + 2 * dz, "minecraft:polished_blackstone_brick_wall")
for x in range(SH[0] - 2, SH[0] + 3):                       # a broken rim
    for z in range(SH[1] - 2, SH[1] + 3):
        if (abs(x - SH[0]) == 2 or abs(z - SH[1]) == 2) and hash01(x, 4, z, 6) < 0.6:
            S(x, P, z, "minecraft:cracked_deepslate_bricks")
S(SH[0] - 3, BOT + 1, -175, "minecraft:soul_lantern[hanging=false]")
S(SH[0] + 3, BOT + 1, -175, "minecraft:red_candle[candles=3,lit=true]")
S(SH[0] - 3, BOT + 1, -172, "minecraft:cobweb")
SOUL_HOLES = (SH[0] - 2, BOT + 1, -175)
reserve(SH[0] - 5, -178, SH[0] + 5, -168)

# ---------------------------------------------------------------- the Oubliette
OX0, OX1, OZ0, OZ1 = 247, 281, -168, -154
OFL = 45
STW = (243, -161)                                           # the stairwell tower
for x in range(OX0 - 1, OX1 + 2):
    for z in range(OZ0 - 1, OZ1 + 2):
        for y in range(OFL - 1, OFL + 10):
            S(x, y, z, WALL(x, y, z))
for x in range(OX0, OX1 + 1):
    for z in range(OZ0, OZ1 + 1):
        for y in range(OFL + 1, OFL + 8):
            area.clear(x, y, z)
        S(x, OFL, z, FLOOR(x, OFL, z))
        if hash01(x, 2, z, 7) < 0.07:
            S(x, OFL, z, "minecraft:cracked_deepslate_tiles")
# the central corridor is 3 wide; cells on both sides behind iron bars
DIVS = (249, 255, 261, 267, 273, 279)
for dx_ in DIVS:
    for y in range(OFL + 1, OFL + 8):
        for z in list(range(OZ0, -162)) + list(range(-160, OZ1 + 1)):
            if -163 <= z <= -159 and z not in (-163, -159):
                continue
            S(dx_, y, z, "minecraft:deepslate_bricks")
for z in (-163, -159):
    for x in range(OX0, OX1 + 1):
        if x in DIVS:
            for y in range(OFL + 1, OFL + 8):
                S(x, y, z, "minecraft:deepslate_bricks")
            continue
        for y in range(OFL + 1, OFL + 8):
            S(x, y, z, "minecraft:iron_bars")
for x in range(250, 279):                                    # corridor ends (the first and last cell stubs are walls)
    pass
# Julian's cell (north, x 262..266) has an open doorway; the others have iron doors
for (x0, z) in [(250 + 6 * i, -163) for i in range(5)] + [(250 + 6 * i, -159) for i in range(5)]:
    mid = x0 + 2
    if (mid, z) == (264, -163):
        for y in range(OFL + 1, OFL + 4):
            area.clear(mid, y, z)
        continue
    if hash01(mid, 1, z, 4) < 0.7:
        S(mid, OFL + 1, z, "minecraft:iron_door[facing=%s,half=lower,hinge=left,open=false]" % ("south" if z == -163 else "north"))
        S(mid, OFL + 2, z, "minecraft:iron_door[facing=%s,half=upper,hinge=left,open=false]" % ("south" if z == -163 else "north"))
for i in range(5):                                           # cell furnishings
    for z, dz in ((-165, 1), (-157, -1)):
        x0 = 250 + 6 * i
        S(x0, OFL + 1, z - dz if False else z, "minecraft:crimson_slab[type=bottom]")
        S(x0 + 1, OFL + 1, z, "minecraft:crimson_slab[type=bottom]")
        if hash01(x0, 3, z, 5) < 0.6:
            S(x0 + 4, OFL + 1, z, "minecraft:cobweb")
        S(x0 + 4, OFL + 1, z + (1 if z < -160 else -1) * 0 if False else z, "minecraft:cobweb") if hash01(x0, 4, z, 5) < 0.2 else None
    S(250 + 6 * i + 2, OFL + 6, -166, "minecraft:iron_chain[axis=y]")
    S(250 + 6 * i + 2, OFL + 5, -166, "minecraft:soul_lantern[hanging=true]") if i != 2 else None
for x in range(250, 281, 5):                                 # corridor lights
    S(x, OFL + 6, -161, "minecraft:iron_chain[axis=y]")
    S(x, OFL + 5, -161, "minecraft:soul_lantern[hanging=true]")
S(264, OFL + 7, -165, "minecraft:iron_chain[axis=y]") if False else None
# the landing end (x 280..281) where prisoners arrive, a bone pile and a sign wall
for z in (-162, -161, -160):
    for x in (280, 281):
        S(x, OFL, z, "minecraft:polished_deepslate")
S(281, OFL + 1, -162, "minecraft:skeleton_skull[rotation=4]")
S(281, OFL + 1, -160, "minecraft:cobweb")
S(279, OFL + 1, -161, "minecraft:red_candle[candles=3,lit=true]") if False else None
# the stairwell tower: a spiral from the corridor up to the courtyard (floor y 45 -> exit y 79)
SW_R = 4
ring, inner = ring_cells(SW_R)
cx, cz = STW
for (dx, dz) in ring:
    for y in range(OFL - 1, P + 10):
        S(cx + dx, y, cz + dz, WALL(cx + dx, y, cz + dz))
for (dx, dz) in inner:
    for y in range(OFL + 1, P + 10):
        area.clear(cx + dx, y, cz + dz)
    S(cx + dx, OFL, cz + dz, FLOOR(cx + dx, OFL, cz + dz))
for y in range(OFL + 1, P):
    S(cx, y, cz, "minecraft:deepslate_bricks")
spiral(cx, cz, 1.5, 3.4, OFL, 33, 16, 0.0, STEP)
for (dx, dz) in inner:                                       # the landing at the top, east of the last step
    ang = math.atan2(dz, dx)
    if math.hypot(dx, dz) >= 1.5 and 0.2 <= ang <= 0.9:
        S(cx + dx, P, cz + dz, "minecraft:polished_blackstone_bricks")
        for y in range(P + 1, P + 4):
            area.clear(cx + dx, y, cz + dz)
for y in range(OFL + 1, OFL + 8):                            # the corridor opens into the tower
    for z in (-162, -161, -160):
        for x in range(246, OX0 + 1):
            area.clear(x, y, z)
for y in range(P + 1, P + 4):                                # the exit door at the top, east side
    for z in (-162, -161, -160):
        for x in range(cx + 3, cx + 6):
            area.clear(x, y, z)
for y in range(P + 8, P + 9):                                # a small cone roof over the stairwell
    pass
for j in range(7):
    rad = (SW_R + 1.6) * (1 - j / 7)
    for dx in range(-SW_R - 2, SW_R + 3):
        for dz in range(-SW_R - 2, SW_R + 3):
            d = math.hypot(dx, dz)
            if rad - 1.3 <= d <= rad:
                S(cx + dx, P + 10 + j, cz + dz, ROOF)
S(cx, P + 16, cz, "minecraft:lightning_rod[facing=up]")
for dx in (4,):
    S(cx + dx + 1, P + 1, cz - 2, "minecraft:polished_blackstone_brick_wall")
    S(cx + dx + 1, P + 2, cz - 2, "minecraft:soul_lantern[hanging=false]")
    S(cx + dx + 1, P + 1, cz + 2, "minecraft:polished_blackstone_brick_wall")
    S(cx + dx + 1, P + 2, cz + 2, "minecraft:soul_lantern[hanging=false]")
reserve(cx - 6, cz - 6, cx + 8, cz + 6)

# ---------------------------------------------------------------- the Blood Effigies, the graveyard nook, the flower-pot trail
EFFIGY_SPOTS = []
for i, (ex, ez) in enumerate(EFFIGIES, 1):
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            ring_ = max(abs(dx), abs(dz)) == 2
            S(ex + dx, P, ez + dz, "minecraft:chiseled_polished_blackstone" if ring_ and (dx + dz) % 2 == 0 else
              ("minecraft:polished_blackstone_bricks" if ring_ else "minecraft:deepslate_tiles"))
            for y in range(P + 1, P + 6):
                area.clear(ex + dx, y, ez + dz)
    for (dx, dz) in ((-2, -2), (2, -2), (2, 2), (-2, 2)):
        S(ex + dx, P + 1, ez + dz, "minecraft:polished_blackstone_brick_wall")
        S(ex + dx, P + 2, ez + dz, "minecraft:soul_lantern[hanging=false]")
    S(ex, P + 1, ez, "minecraft:bedrock")
    for k in (2, 3, 4):
        S(ex, P + k, ez, "minecraft:redstone_block")
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        S(ex + dx, P + 1, ez + dz, "minecraft:red_candle[candles=2,lit=true]")
    vx, vz = 257 - ex, -130 - ez
    if abs(vx) >= abs(vz):
        sx, sz = (1 if vx > 0 else -1), 0
    else:
        sx, sz = 0, (1 if vz > 0 else -1)
    spot = (ex + 3 * sx, P + 1, ez + 3 * sz)
    EFFIGY_SPOTS.append(spot)
    reserve(ex - 4, ez - 4, ex + 4, ez + 4)

# the graveyard nook outside the north wall: graves, a dead tree, a hidden gap in the north hedge-wall
GX0, GZ0, GX1, GZ1 = GRAVE
for x in range(GX0, GX1 + 1):
    for z in range(GZ0, GZ1 + 1):
        edge_ = x in (GX0, GX1) or z in (GZ0, GZ1)
        S(x, P, z, "minecraft:coarse_dirt" if hash01(x, 1, z, 3) < 0.5 else "minecraft:podzol")
        for y in range(P + 1, P + 5):
            if edge_:
                S(x, y, z, "minecraft:deepslate_brick_wall" if y <= P + 2 else "minecraft:iron_bars")
            else:
                area.clear(x, y, z)
for y in range(P + 1, P + 5):                               # the hidden gap, behind a dead tree and leaves
    area.clear(250, y, GZ0)
for dx in (-1, 0, 1):
    for dy in range(0, 3):
        S(250 + dx, P + 1 + dy, GZ0 - 2, "minecraft:dark_oak_leaves[persistent=true]")
for k in range(6):
    S(250, P + 1 + k, GZ0 - 3, "minecraft:dark_oak_log[axis=y]")
for (gx, gz) in ((248, -190), (252, -190), (248, -188), (252, -188)):
    S(gx, P + 1, gz, "minecraft:cobbled_deepslate_slab[type=bottom]")
    S(gx, P + 2, gz, "minecraft:skeleton_skull[rotation=8]") if hash01(gx, 1, gz, 2) < 0.5 else None
S(250, P + 1, -188, "minecraft:soul_lantern[hanging=false]")
S(249, P + 1, -190, "minecraft:cobweb")
SOUL_FLEE = (250, P + 1, -189)

# the flower-pot trail: a winding blackstone path out from the south gate road, a pot every other block
TRAIL = [(264, -80), (268, -77), (273, -74), (278, -71), (282, -67), (284, -62), (281, -58), (284, -54), (286, -50)]
pots = ["potted_poppy", "potted_red_tulip", "potted_wither_rose", "potted_crimson_fungus", "potted_crimson_roots", "flower_pot"]
trail_cells = []
for (x1, z1), (x2, z2) in zip(TRAIL, TRAIL[1:]):
    n = max(abs(x2 - x1), abs(z2 - z1), 1)
    for i in range(n):
        trail_cells.append((int(round(x1 + (x2 - x1) * i / n)), int(round(z1 + (z2 - z1) * i / n))))
seen = set()
pi = 0
for (x, z) in trail_cells:
    if (x, z) in seen or (x, z) not in T:
        continue
    seen.add((x, z))
    S(x, T[(x, z)], z, "minecraft:polished_blackstone" if hash01(x, 1, z, 4) < 0.6 else "minecraft:cobbled_deepslate")
    area.top[(x, z)] = T[(x, z)]
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        occ.add((x + dx, z + dz))
    occ.add((x, z))
    pi += 1
    if pi % 2 == 0:
        sx, sz = (1, 0) if abs(x - 270) < 99 and pi % 4 == 0 else (0, 1)
        px, pz = x + sx * (1 if (pi // 2) % 2 else -1), z + sz * (1 if (pi // 2) % 2 else -1)
        if (px, pz) in T:
            S(px, T[(px, pz)] + 1, pz, "minecraft:" + pots[(pi // 2) % len(pots)])
            occ.add((px, pz))
ex_, ez_ = TRAIL[-1]
ey_ = T[(ex_, ez_)]
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if (ex_ + dx, ez_ + dz) in T:
            S(ex_ + dx, T[(ex_ + dx, ez_ + dz)], ez_ + dz, "minecraft:polished_blackstone_bricks")
            yy = T[(ex_ + dx, ez_ + dz)]
            if max(abs(dx), abs(dz)) == 2 and (dx + dz) % 2 == 0:
                S(ex_ + dx, yy + 1, ez_ + dz, "minecraft:flower_pot")
                S(ex_ + dx, yy + 1, ez_ + dz, "minecraft:potted_wither_rose")
            elif max(abs(dx), abs(dz)) < 2:
                area.clear(ex_ + dx, yy + 1, ez_ + dz)
S(ex_, ey_ + 1, ez_ + 1, "minecraft:soul_lantern[hanging=false]")
SOUL_POT = (ex_, ey_ + 1, ez_)
reserve(ex_ - 3, ez_ - 3, ex_ + 3, ez_ + 3)

# ---------------------------------------------------------------- grounds: dead trees, red flowers, lamps along the roads
TREES = [(240, -90), (276, -92), (270, -106), (248, -90), (238, -150), (276, -150), (276, -158), (238, -174), (270, -176), (246, -148),
         (278, -112), (246, -112), (266, -176)]
for (x, z) in TREES:
    area.dead_tree(x, P + 1, z, height=6 + int(hash01(x, 1, z, 3) * 5), log=("dark_oak_log" if hash01(x, 2, z, 3) < 0.6 else "crimson_stem"),
                   seed=x * 3 + z)
for x in range(X0 + 4, X1 - 3):
    for z in range(Z0 + 4, Z1 - 3):
        if (x, z) in occ or (x, z) not in T or edge(x, z) < 4 or hash01(x, 0, z, 91) > 0.0065:
            continue
        if (x, z) in path_cols or any(abs(x - px) < 3 and abs(z - pz) < 3 for px, pz in ((260, -180), (260, -80))):
            continue
        t = T[(x, z)]
        area.dead_tree(x, t + 1, z, height=5 + int(hash01(x, 3, z, 91) * 6),
                       log="dark_oak_log" if hash01(x, 4, z, 91) < 0.55 else "crimson_stem", seed=x + z)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                occ.add((x + dx, z + dz))
for (x, z) in path_cols:
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            occ.add((x + dx, z + dz))
for name, (pts, w) in PATHS.items():                         # lamps along the roads
    if name.startswith("cross"):
        continue
    total = 0
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1))
        for i in range(n):
            total += 1
            if total % 9:
                continue
            px, pz = x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n
            sgn = 1 if (total // 9) % 2 else -1
            lx, lz = (int(px), int(pz + 3 * sgn)) if x2 != x1 else (int(px + 3 * sgn), int(pz))
            if (lx, lz) in T and (lx, lz) not in path_cols and not (WX0 <= lx <= WX1 and WZ0 <= lz <= WZ1):
                lamp(lx, T[(lx, lz)] + 1, lz, 3)
                occ.add((lx, lz))
FLOWERS = ["minecraft:poppy", "minecraft:red_tulip", "minecraft:crimson_roots", "minecraft:short_grass", "minecraft:fern",
           "minecraft:poppy", "minecraft:wither_rose", "minecraft:crimson_fungus"]
area.scatter({c for c in T if c not in occ}, 0.22, lambda x, z: FLOWERS[int(hash01(x, 1, z, 6) * len(FLOWERS)) % len(FLOWERS)], seed=4,
             avoid=lambda x, z: (x, z) in occ)
# red flowers and grass in the courtyards' garden patches
for rect in (CA, CB):
    for x in range(rect[0], rect[2] + 1):
        for z in range(rect[1], rect[3] + 1):
            if area.get(x, P, z) in ("minecraft:moss_block", "minecraft:podzol") and area.get(x, P + 1, z) in (None, AIR) \
                    and hash01(x, 7, z, 8) < 0.45:
                S(x, P + 1, z, FLOWERS[int(hash01(x, 8, z, 8) * len(FLOWERS)) % len(FLOWERS)])
# lamps in the courtyards
for (x, z) in ((236, -108), (278, -108), (236, -88), (278, -88), (236, -174), (278, -174), (236, -148), (278, -148), (248, -108), (272, -88),
               (248, -174), (252, -148), (268, -148)):
    if area.get(x, P + 1, z) in (None, AIR):
        lamp(x, P + 1, z, 3)

# ---------------------------------------------------------------- NPCs, souls, spawn spots (standing spots)
MADDOX = (260, P + 2, -141)
PRINCE = (278, P + 2, -133)
JULIAN = (264, OFL + 1, -165)
GUARD = (270, OFL + 1, -161)
GRUBBER = (252, P + 1, -98)
area.npc("Maddox the Slayer (Rift)", *MADDOX, 0.0, "the Vampire Slayer altar at the north end of the Great Hall")
area.npc("Fairylosopher", *FAIRY, 0.0, "on the platform at the top of the Fairylosophy Tower (spiral stair inside, door on the east side)")
area.npc("Motes Grubber (Chateau)", *GRUBBER, 0.0, "in courtyard A, south of the Great Hall")
area.npc("Julian", *JULIAN, 0.0, "a prisoner in an Oubliette cell (north row, third cell)")
area.npc("The Prince", *PRINCE, 90.0, "a Porhtal eye on the dais in the throne room")
area.npc("Oubliette Guard", *GUARD, 90.0, "in the Oubliette corridor")
area.npc("enigma:Chateau Puzzle", *SOUL_PUZZLE, 0.0, "in the stone circle in the middle of the glass conservatory (courtyard A)")
area.npc("enigma:Chateau Fleespook", *SOUL_FLEE, 0.0, "in the graveyard nook outside the north wall (gap in the north side, behind a dead tree)")
area.npc("enigma:Chateau Flower Pot", *SOUL_POT, 0.0, "end of the flower-pot trail south of the castle")
area.npc("enigma:Chateau Balloons", *BALLOON[:1], 0.0, "") if False else None
area.npc("enigma:Chateau Balloons", BALLOON[0], BALLOON[1], BALLOON[2], 0.0, "on the Balloon Tower's platform (north-east corner), under the balloons")
area.npc("enigma:Chateau Holes", *SOUL_HOLES, 0.0, "at the bottom of the floor hole in courtyard B (ladder up)")
area.npc("spawn:vampire_arena", AX, P + 1, AZ, 0.0, "courtyard B's arena where slayer bosses fight")
area.npc("spawn:oubliette", 280, OFL + 1, -161, 90.0, "the Oubliette's east end, where prisoners land")
area.npc("spawn:oubliette_exit", STW[0] + 6, P + 1, STW[1], 270.0, "just outside the Oubliette stair tower's door (courtyard B)")
for i, (x, y, z) in enumerate(EFFIGY_SPOTS, 1):
    area.npc(f"spawn:effigy_{i}", x, y, z, 0.0, f"in front of Blood Effigy {i} (the effigy is 3 blocks away)")
area.spots["notes"] += [
    "Crossings: Colosseum at (200, 68, -180), Photon Pathway at (200, 68, -80); roads lead to the north gate (260, -178) and the south gate (260, -84)",
    "Great Hall x 250..270, z -144..-112 (cellar below, floor y 68; stair trench at x 253..255 from courtyard A); throne room x 270..282, z -141..-125",
    "Fairylosophy Tower (239, -128) r6, platform y %d; Balloon Tower (281, -177) r5, platform y %d" % (P_FAIRY, P_BALLOON),
    "Oubliette: corridor x 247..281, z -162..-160, floor y 45; stair tower (243, -161) spirals up to courtyard B",
]
for nm, v in area.spots["npcs"].items():
    sx, sy, sz = int(math.floor(v["pos"][0])), int(v["pos"][1]), int(math.floor(v["pos"][2]))
    for dy in (0, 1, 2):
        if area.get(sx, sy + dy, sz) not in (None, AIR):
            S(sx, sy + dy, sz, AIR)


# ---------------------------------------------------------------- zones and mobs
def mob(kind, name, level_, health, damage, xp, coins, mx_, every, a, b, drops=None):
    """A Rift mob: `coins` are the Motes it drops (the mod pays Motes for kills in the Rift)."""
    spec = {"type": kind, "name": name, "level": level_, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


THRALL = [drop("HEMOVIBE", 1, 1, 1), drop("VAMPIRIC_MELON", 1, 1, 1)]
FLEDGLING = [drop("HEMOVIBE", 1, 1, 1), drop("HEMOVIBE", 0.5, 1, 1), drop("VAMPIRIC_MELON", 1, 1, 1)]
SPLATTER = [drop("SPLATTER_CRUX", 1, 1, 1), drop("HEMOVIBE", 1, 1, 1)]
area.zone("Stillgore Chateau", box=((X0, 59, Z0), (X1, 199, Z1)), blocks={}, mobs=[
    mob("minecraft:zombie", "Thrall", 6, 190, 8, 30, 200, 6, 8, (236, 79, -110), (278, 86, -87), THRALL),
    mob("minecraft:zombie", "Fledgling", 8, 210, 9, 20, 250, 5, 10, (236, 79, -176), (278, 86, -147), FLEDGLING),
    mob("minecraft:zombie", "Thrall", 6, 190, 8, 30, 200, 4, 10, (226, 79, -196), (290, 86, -180), THRALL),
    mob("minecraft:zombie", "Splatter", 6, 135, 7, 30, 150, 5, 8, (252, 79, -142), (268, 86, -114), SPLATTER),
    mob("minecraft:zombie", "Splatter", 6, 135, 7, 30, 150, 4, 10, (252, 68, -141), (268, 75, -115), SPLATTER),
])
area.zone("Oubliette", box=((238, 40, -177), (283, 58, -145)), blocks={}, mobs=[
    mob("minecraft:zombie", "Oubliette Guard", 7, 200, 8, 6, 0, 4, 10, (248, 46, -162), (279, 52, -160)),
])
area.save()
print("Fairy platform y", P_FAIRY, "balloon platform y", P_BALLOON)
print("effigy spots", EFFIGY_SPOTS)
print("souls: puzzle", SOUL_PUZZLE, "flee", SOUL_FLEE, "pot", SOUL_POT, "holes", SOUL_HOLES, "balloons", BALLOON)
print("seam surface x=200..206 z=-180:", [area.surface_y(x, -180) for x in range(200, 208)], "z=-80:", [area.surface_y(x, -80) for x in range(200, 208)])
