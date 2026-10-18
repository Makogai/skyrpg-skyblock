#!/usr/bin/env python3
"""Galatea, the south half of Moonglade Marsh (x -150..149, z 0..159): Murkwater Loch (a great dark lake ringed with
mangroves, a long pier out to the contest pavilion), Murkwater Shallows (a sunken part of the lake floor with kelp,
sea pickles and a ruin), Murkwater Outpost (stilted huts and docks on the south shore), the South Wetlands (west) and
the South Reaches (east) with channels, mud, reeds, ruins and a few fig trees, SwampCut Inc. (a logging camp in the
south-east) and the Squid Cave (a flooded sea cave in a hill at the south cliff).

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python galatea_south.py

The north half (galatea_north.py) meets this one along z = 0: the surface keeps y 68 within SEAM blocks of it and the
main paths cross at x -60 and x 60. The outer edges (west, east, south) break off into the void with a hanging underside.
"""
import math

from kit import hash01, noise
from galatea_lib import new_area, land, lushlilacs, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("south", version=5)
area.shell = 3
S = area.set
G = GROUND                      # surface block y (players walk at 69)
WATER_TOP = G - 1               # water surface block y (67)
X0, X1, Z0, Z1 = -150, 149, 0, 159

LAKE_C = (0, 77)                # Murkwater Loch centre and radii
LAKE_R = (72, 46)
SH_C = (-38, 104)               # the Shallows' bowl (deeper lake floor)
SH_R = (21, 14)
CAVE_C = (-97, 144)             # the Squid Cave chamber
CAVE_R = (16, 8)
POOL_R = (12, 5.5)
CAMP_C = (124, 131)
PIER_X = 0
PAV = (-8, 76, 8, 89)           # pavilion platform x0, z0, x1, z1
PAV_C = (0, 82)

CL, CR = CROSSINGS


# ---------------------------------------------------------------- helpers
def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def segd(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    l2 = dx * dx + dz * dz
    t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / l2))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


def ell(x, z, cx, cz, rx, rz):
    return ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2


def jag(a, b, s):
    return 4 * noise(a, b, s, 1.2) + 2.0 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 1.0 * (hash01(int(a), 2, int(b), s) - 0.5)


def edge(x, z):
    """Distance in blocks to the island's outer edge (negative = void). The north (z = 0) is the seam: no edge."""
    f = sm(z / 20.0)
    dw = x - (-146 + f * jag(z, 0, 1))
    de = (145 + f * jag(z, 9, 2)) - x
    ds = (157 + 0.5 * jag(x, 7, 4)) - z
    return min(dw, de, ds)


def qlake(x, z):
    q = ell(x, z, LAKE_C[0], LAKE_C[1], *LAKE_R)
    # bays and bulges
    return q + 0.11 * noise(x, z, 3, 1.3) + 0.05 * noise(x, z, 8, 2.6)


# ---------------------------------------------------------------- paths
PATHS = {
    "cross_w": ([(CL, 0), (CL, 14)], 5),
    "cross_e": ([(CR, 0), (CR, 14)], 5),
    "w_main": ([(CL, 14), (CL, 30), (-52, 36), (-30, 28), (0, 26)], 3),
    "e_main": ([(CR, 14), (CR, 30), (52, 36), (30, 28), (0, 26)], 3),
    "w_wet": ([(CL, 30), (-80, 52), (-96, 66), (-104, 86), (-103, 106), (-98, 124), (-97, 131)], 3),
    "w_south": ([(-103, 106), (-86, 117), (-76, 126), (-52, 128), (-39, 124)], 3),
    "south_shore": ([(-39, 124), (-20, 129), (0, 128), (26, 128), (50, 127), (68, 121)], 3),
    "e_wet": ([(CR, 30), (84, 52), (98, 66), (100, 90), (92, 106), (80, 118), (68, 121)], 3),
    "camp": ([(92, 106), (104, 118), (118, 124)], 3),
    "e_ruin": ([(98, 66), (104, 52), (106, 46)], 3),
    "w_ruin": ([(-96, 66), (-110, 72), (-115, 80)], 3),
    "pier": ([(PIER_X, 26), (PIER_X, 40)], 3),
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

# the camp's yard and the Squid Cave hill and other planned dry ground
ISLETS = [(-34, 58, 5), (38, 62, 6), (22, 96, 5), (-62, 96, 4)]       # (cx, cz, r) islets in the lake
RUIN_W = (-116, 82)
RUIN_E = (106, 44)


def hill_top(x, z):
    e = ell(x, z, CAVE_C[0], CAVE_C[1] + 2, 27, 15)
    return G + int(round(11 * max(0.0, 1 - e) ** 0.8))


def chamber_cell(x, z):
    return ell(x, z, CAVE_C[0], CAVE_C[1], *CAVE_R) < 1


def pool_cell(x, z):
    return ell(x, z, CAVE_C[0], CAVE_C[1], *POOL_R) < 1


def cave_ceiling(x, z):
    e = ell(x, z, CAVE_C[0], CAVE_C[1], *CAVE_R)
    return 66 + 3 + int(5 * math.sqrt(max(0.0, 1 - e)))


def core_dry(x, z):
    if z <= 12:
        return True
    if (x, z) in path_cols:
        return True
    if ell(x, z, CAMP_C[0], CAMP_C[1], 27, 23) < 1:
        return True
    if ell(x, z, CAVE_C[0], CAVE_C[1] + 2, 29, 17) < 1:
        return True
    if math.hypot(x - RUIN_W[0], z - RUIN_W[1]) < 11 or math.hypot(x - RUIN_E[0], z - RUIN_E[1]) < 9:
        return True
    if -12 <= x <= 62 and z >= 126:             # the outpost's landward side
        return True
    return False


def flat(x, z):
    return (x, z) in path_cols or z <= SEAM + 6 or ell(x, z, CAMP_C[0], CAMP_C[1], 29, 25) < 1 \
        or math.hypot(x - RUIN_W[0], z - RUIN_W[1]) < 12 or math.hypot(x - RUIN_E[0], z - RUIN_E[1]) < 10 \
        or (-12 <= x <= 62 and z >= 126)


def noise_wet(x, z):
    return (abs(noise(x, z, 7, 1.5) - 0.02) < 0.085 or abs(noise(x, z, 19, 1.1) - 0.18) < 0.07
            or abs(noise(x, z, 23, 1.3) + 0.3) < 0.07 or noise(x, z, 31, 1.2) > 0.62)


# ---------------------------------------------------------------- the plan: land, lake, channels
T = {}          # (x, z) -> top block y of the column (land) or the floor block y (water)
WET = set()
LAKE = set()
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z)
        if e < 0:
            continue
        wet = False
        floor = None
        q = qlake(x, z)
        in_islet = any(math.hypot(x - cx, z - cz) < r + 0.6 * noise(x, z, 5, 3) for cx, cz, r in ISLETS)
        if e >= 4 and (x, z) not in path_cols and not core_dry(x, z) and not in_islet:
            if q < 1.0 and 1 - q > 0.0:
                wet = True
                s = 1 - q
                depth = 1 + int(round(8 * sm(s / 0.55) + 0.9 * noise(x, z, 12, 2.0)))
                sb = 1 - ell(x, z, SH_C[0], SH_C[1], *SH_R)
                if sb > 0:
                    depth = max(depth, 9) + int(round(3 * sm(sb / 0.6)))
                depth = max(1, min(12, depth))
                floor = WATER_TOP - depth
                LAKE.add((x, z))
            elif q > 1.13 and abs(x) > 0 and z > SEAM + 6:
                if noise_wet(x, z) and not (-12 <= x <= 62 and z >= 126):
                    wet = True
                    floor = WATER_TOP - 1 - (1 if noise(x, z, 33, 2.0) > 0.05 else 0) - (1 if noise(x, z, 34, 2.4) > 0.5 else 0)
        if wet:
            WET.add((x, z))
            T[(x, z)] = floor
        else:
            top = G
            if not flat(x, z):
                b = noise(x, z, 21, 2.2)
                top = G + (2 if b > 0.62 else 1 if b > 0.4 else 0)
            hr = ell(x, z, CAVE_C[0], CAVE_C[1], 1, 1)
            top = max(top, hill_top(x, z)) if (x, z) not in path_cols or hill_top(x, z) == G else top
            for cx, cz, r in ISLETS:
                d = math.hypot(x - cx, z - cz)
                if d < r - 1.2:
                    top = max(top, G + (1 if hash01(x, 5, z, 4) < 0.3 else 0))
            if chamber_cell(x, z):
                top = max(top, cave_ceiling(x, z) + 2)
            T[(x, z)] = top

# water needs a rim: any water column beside a cell outside the plan turns to land (and water beside a lower land column)
for (x, z) in list(WET):
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if (x + dx, z + dz) not in T:
            WET.discard((x, z))
            LAKE.discard((x, z))
            T[(x, z)] = G
            break
# land columns below the water surface beside water would leak nothing, but keep banks at 67 or 68 only
for c in list(T):
    if c not in WET and T[c] < WATER_TOP:
        T[c] = WATER_TOP

# banks: some land beside water drops to y 67 (mud flats where reeds grow)
BANK = set()
for c, top in list(T.items()):
    if c in WET or top != G or flat(*c) and c not in path_cols and False:
        continue
    if (c in path_cols) or flat(*c):
        continue
    if any((c[0] + dx, c[1] + dz) in WET for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        BANK.add(c)
        if hash01(c[0], 3, c[1], 6) < 0.6:
            T[c] = WATER_TOP

# ---------------------------------------------------------------- the ground
SURF_PATH = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3, "packed_mud": 1})
UNDER = area.mix({"mud": 5, "packed_mud": 2, "rooted_dirt": 2, "dirt": 1})
BODY = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1, "andesite": 1})
UNDERSIDE = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1})


def surface_block(x, y, z):
    if (x, z) in BANK:
        return "minecraft:moss_block" if hash01(x, 1, z, 3) < 0.35 else "minecraft:mud"
    n = noise(x, z, 41, 2.6)
    h = hash01(x, 2, z, 9)
    if n > 0.2:
        return "minecraft:grass_block" if h < 0.75 else "minecraft:moss_block" if h < 0.9 else "minecraft:rooted_dirt"
    if n < -0.3:
        return "minecraft:moss_block" if h < 0.7 else "minecraft:mud"
    if h < 0.1:
        return "minecraft:packed_mud"
    if h < 0.18:
        return "minecraft:rooted_dirt"
    if h < 0.3:
        return "minecraft:podzol"
    if h < 0.5:
        return "minecraft:grass_block"
    return "minecraft:mud" if h < 0.8 else "minecraft:moss_block"


def under_depth(x, z, e):
    t = sm(e / 55.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


for (x, z), top in T.items():
    e = edge(x, z)
    if (x, z) in WET:
        area.set(x, top, z, "minecraft:mud" if hash01(x, 6, z, 2) < 0.8 else "minecraft:packed_mud")
        for y in range(top - 1, top - 4, -1):
            S(x, y, z, UNDER(x, y, z))
        for y in range(top - 4, LAND_BOTTOM - 1, -1):
            S(x, y, z, BODY(x, y, z))
        for y in range(top + 1, WATER_TOP + 1):
            S(x, y, z, "minecraft:water")
    else:
        if (x, z) in path_cols:
            surf = SURF_PATH
        elif e < 2.5:
            surf = lambda x, y, z: "minecraft:mud" if hash01(x, 8, z, 1) < 0.5 else "minecraft:packed_mud"
        else:
            surf = surface_block
        land(area, x, z, top, surf, UNDER, BODY)
        area.top[(x, z)] = top
    d = under_depth(x, z, e)
    for k in range(1, d + 1):
        y = LAND_BOTTOM - k
        r = hash01(x, y, z, 12)
        if r < 0.03:
            b = "minecraft:amethyst_block" if e < 20 else "minecraft:calcite"
        else:
            b = UNDERSIDE(x, y, z)
        S(x, y, z, b)
    # hanging roots and moss strands under the island
    if e < 40 and hash01(x, 10, z, 13) < 0.02:
        ln = 3 + int(hash01(x, 11, z, 14) * 8)
        yy = LAND_BOTTOM - d
        for i in range(ln):
            S(x, yy - 1 - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" +
              ("tip" if i == ln - 1 else "frustum" if i == ln - 2 else "middle" if i else "base") + "]")
    elif hash01(x, 12, z, 15) < 0.015:
        for i in range(2 + int(hash01(x, 13, z, 16) * 5)):
            S(x, LAND_BOTTOM - d - 1 - i, z, "minecraft:hanging_roots")

# a few floating shards beyond the cliffs
for i in range(26):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(5 + hash01(i, 1, 0, 77) * (Z1 - 5))
    if edge(x, z) > -3 or edge(x, z) < -16 or i > 12:
        continue
    y = 50 + int(hash01(i, 2, 0, 77) * 26)
    r = 1.5 + hash01(i, 3, 0, 77) * 2.5
    for dx in range(-4, 5):
        for dy in range(-5, 3):
            for dz in range(-4, 5):
                q = (dx / r) ** 2 + (dy / (r * 0.9)) ** 2 + (dz / r) ** 2
                if q < 1:
                    S(x + dx, y + dy, z + dz, "minecraft:tuff" if q < 0.7 else "minecraft:mud")
    for k in range(int(r * 2) + 2):
        S(x, y - 5 - k, z, "minecraft:tuff" if k % 2 == 0 else "minecraft:calcite")

occ = set()      # columns kept free of trees and plants


def reserve(cx, cz, r):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            occ.add((cx + dx, cz + dz))


def reserve_rect(x0, z0, x1, z1, m=1):
    for x in range(x0 - m, x1 + m + 1):
        for z in range(z0 - m, z1 + m + 1):
            occ.add((x, z))


for (x, z) in path_cols:
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            occ.add((x + dx, z + dz))


# ---------------------------------------------------------------- building helpers
DECK = area.mix({"dark_oak_planks": 5, "spruce_planks": 4})


def floor_y(x, z):
    return T.get((x, z), 55)


def pile(x, z, ytop=G - 1, log="dark_oak_log"):
    """A piling from ytop down to the ground or lake floor."""
    fy = floor_y(x, z)
    for y in range(ytop, fy, -1):
        b = area.get(x, y, z)
        if b in (None, "minecraft:air", "minecraft:water"):
            S(x, y, z, f"minecraft:{log}[axis=y]")


def deck(x, z, y=G, block=None):
    S(x, y, z, (block or DECK)(x, y, z))
    # keep the lake's water where the deck sits over it
    if (x, z) in WET and area.get(x, y - 1, z) in (None, "minecraft:air"):
        S(x, y - 1, z, "minecraft:water")


def stairs(x, y, z, block, facing, half="bottom", shape="straight"):
    S(x, y, z, f"minecraft:{block}[facing={facing},half={half},shape={shape}]")


def hip_roof(x0, z0, x1, z1, y, stair, plank, over=1):
    """A hip roof over the rectangle, starting at y, overhanging by `over`."""
    xa, za, xb, zb = x0 - over, z0 - over, x1 + over, z1 + over
    i = 0
    while xa <= xb and za <= zb:
        yy = y + i
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                on_n, on_s, on_w, on_e = z == za, z == zb, x == xa, x == xb
                if xa == xb or za == zb:
                    S(x, yy, z, f"minecraft:{plank}")
                elif on_n:
                    stairs(x, yy, z, stair, "south")
                elif on_s:
                    stairs(x, yy, z, stair, "north")
                elif on_w:
                    stairs(x, yy, z, stair, "east")
                elif on_e:
                    stairs(x, yy, z, stair, "west")
                else:
                    S(x, yy, z, f"minecraft:{plank}")
        xa, za, xb, zb = xa + 1, za + 1, xb - 1, zb - 1
        i += 1
    return i


def pane(x, y, z, along_x):
    S(x, y, z, "minecraft:glass_pane[" + ("east=true,west=true" if along_x else "north=true,south=true") + "]")


def hut(x0, z0, x1, z1, door, wall="spruce_planks", frame="dark_oak_log", roof=("spruce_stairs", "spruce_planks"), h=4,
        pilings=True, floor_block=None):
    """A stilted hut: deck floor at y 68, walls y 69.., windows, a doorway at `door` (x, z on the wall), hip roof."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            deck(x, z, G, floor_block)
    if pilings:
        for x in range(x0, x1 + 1, 3):
            for z in range(z0, z1 + 1, 3):
                pile(x, z)
        for x in (x0, x1):
            for z in (z0, z1):
                pile(x, z)
    for y in range(G + 1, G + 1 + h):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                perimeter = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if not perimeter:
                    S(x, y, z, "minecraft:air")
                elif corner:
                    S(x, y, z, f"minecraft:{frame}[axis=y]")
                elif (x, z) == door and y <= G + 2:
                    S(x, y, z, "minecraft:air")
                elif y in (G + 2, G + 3) and ((x + z) % 3 == 0):
                    pane(x, y, z, z in (z0, z1))
                else:
                    S(x, y, z, f"minecraft:{wall}")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                if (x, z) != door:
                    S(x, G + h, z, f"minecraft:{frame}[axis={'x' if z in (z0, z1) else 'z'}]" if not (x in (x0, x1) and z in (z0, z1)) else f"minecraft:{frame}[axis=y]")
    hip_roof(x0, z0, x1, z1, G + 1 + h, roof[0], roof[1])
    # a lantern inside
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    S(cx, G + h, cz, "minecraft:lantern[hanging=true]")
    reserve_rect(x0, z0, x1, z1, 2)


def post_lantern(x, z, y=G + 1, soul=False):
    for h in range(3):
        S(x, y + h, z, "minecraft:dark_oak_fence")
    S(x, y + 3, z, "minecraft:soul_lantern[hanging=false]" if soul else "minecraft:lantern[hanging=false]")


def barrel(x, y, z, facing="up"):
    S(x, y, z, f"minecraft:barrel[facing={facing}]")


# ---------------------------------------------------------------- the pier and the contest pavilion
PIER_END = PAV[1] - 1
for z in range(27, PIER_END + 1):
    for dx in (-1, 0, 1):
        deck(PIER_X + dx, z)
    for dx in (-2, 2):
        S(PIER_X + dx, G, z, "minecraft:stripped_spruce_log[axis=z]")
        S(PIER_X + dx, G + 1, z, "minecraft:spruce_fence")
        if z % 8 == 0:
            post_lantern(PIER_X + dx, z, G + 1)
    if z % 4 == 0:
        for dx in (-2, 2):
            pile(PIER_X + dx, z)
reserve_rect(-5, 26, 5, PAV[3] + 2, 2)
# the pier's own landing steps at the shore end and a boat landing on the east side
for z in (46, 47, 48, 49):
    for dx in range(3, 8):
        deck(PIER_X + dx, z)
    for dx in (3, 7):
        pile(PIER_X + dx, z)
for dx in range(3, 8):
    S(PIER_X + dx, G + 1, 45, "minecraft:spruce_fence")
    S(PIER_X + dx, G + 1, 50, "minecraft:spruce_fence")
S(PIER_X + 7, G + 1, 46, "minecraft:spruce_fence")
S(PIER_X + 7, G + 1, 49, "minecraft:spruce_fence")
barrel(PIER_X + 6, G + 1, 47)
barrel(PIER_X + 6, G + 1, 48)
S(PIER_X + 6, G + 2, 47, "minecraft:lantern[hanging=false]")

# the hidden landing under the pier (a Fairy Soul)
for x in (-1, 0, 1):
    for z in (54, 55, 56):
        S(x, 65, z, "minecraft:spruce_planks")
for x, z in ((-1, 54), (1, 54), (-1, 56), (1, 56)):
    for y in range(64, floor_y(x, z), -1):
        S(x, y, z, "minecraft:dark_oak_log[axis=y]")
S(0, 66, 56, "minecraft:sea_lantern")

# pavilion platform
x0, z0, x1, z1 = PAV
for x in range(x0, x1 + 1):
    for z in range(z0, z1 + 1):
        deck(x, z, G, area.mix({"dark_oak_planks": 6, "spruce_planks": 3, "stripped_dark_oak_log": 1}))
        if x in (x0, x1) or z in (z0, z1):
            pass
for x in range(x0, x1 + 1, 3):
    for z in range(z0, z1 + 1, 3):
        pile(x, z)
for x in (x0, x1):
    for z in (z0, z1):
        pile(x, z)
for x in range(x0, x1 + 1):
    for z in (z0, z1):
        if not (z == z0 and abs(x - PIER_X) <= 1):
            S(x, G + 1, z, "minecraft:spruce_fence")
for z in range(z0, z1 + 1):
    for x in (x0, x1):
        S(x, G + 1, z, "minecraft:spruce_fence")
# the roof's posts: corners and midpoints
PX0, PZ0, PX1, PZ1 = x0 + 1, z0 + 1, x1 - 1, z1 - 1
posts = [(PX0, PZ0), (PX1, PZ0), (PX0, PZ1), (PX1, PZ1), (0, PZ0), (0, PZ1), (PX0, PAV_C[1]), (PX1, PAV_C[1]),
         (PX0 + 4, PZ0), (PX1 - 4, PZ0), (PX0 + 4, PZ1), (PX1 - 4, PZ1)]
for (px, pz) in posts:
    if (px, pz) == (0, PZ0):
        continue
    for y in range(G + 1, G + 6):
        S(px, y, pz, "minecraft:dark_oak_log[axis=y]")
for x in range(PX0, PX1 + 1):
    for z in (PZ0, PZ1):
        if (x, z) not in posts or True:
            if area.get(x, G + 5, z) in (None, "minecraft:air"):
                S(x, G + 5, z, "minecraft:stripped_dark_oak_log[axis=x]")
for z in range(PZ0, PZ1 + 1):
    for x in (PX0, PX1):
        if area.get(x, G + 5, z) in (None, "minecraft:air"):
            S(x, G + 5, z, "minecraft:stripped_dark_oak_log[axis=z]")
for x in range(PX0, PX1 + 1):
    for z in range(PZ0, PZ1 + 1):
        if x in (PX0, PX1) or z in (PZ0, PZ1):
            if (x, z) not in posts and (x + z) % 4 == 0 and (x, z) != (0, PZ0):
                S(x, G + 4, z, "minecraft:spruce_fence")
hip_roof(PX0, PZ0, PX1, PZ1, G + 6, "spruce_stairs", "spruce_planks", over=1)
# the roof's gable ridge lantern and chains over the middle
for k in range(1, 4):
    S(0, G + 11 - k, PAV_C[1], "minecraft:iron_chain[axis=y]")
S(0, G + 7, PAV_C[1], "minecraft:lantern[hanging=true]")
for dx, dz in ((-4, -3), (4, -3), (-4, 3), (4, 3)):
    S(dx, G + 5, PAV_C[1] + dz, "minecraft:soul_lantern[hanging=true]")
# inside: a rostrum, benches round the edge, a contest board and flowers
S(0, G + 1, PAV_C[1] - 3, "minecraft:lectern[facing=south]")
for x in (-2, -1, 0, 1, 2):
    S(x, G + 1, PAV_C[1] - 4, "minecraft:dark_oak_slab[type=bottom]")
for x in range(-5, 6, 1):
    if abs(x) > 1:
        stairs(x, G + 1, PAV_C[1] + 3, "spruce_stairs", "north")
for z in (PAV_C[1] - 1, PAV_C[1], PAV_C[1] + 1):
    stairs(-6, G + 1, z, "spruce_stairs", "east")
    stairs(6, G + 1, z, "spruce_stairs", "west")
S(-5, G + 1, PAV_C[1] - 3, "minecraft:flower_pot")
S(5, G + 1, PAV_C[1] - 3, "minecraft:flower_pot")
S(-5, G + 1, PAV_C[1] + 1, "minecraft:potted_blue_orchid")
S(5, G + 1, PAV_C[1] + 1, "minecraft:potted_azalea_bush")
S(-3, G + 1, PAV_C[1] - 3, "minecraft:barrel[facing=up]")
S(3, G + 1, PAV_C[1] - 3, "minecraft:barrel[facing=up]")
S(-3, G + 2, PAV_C[1] - 3, "minecraft:lantern[hanging=false]")
S(3, G + 2, PAV_C[1] - 3, "minecraft:lantern[hanging=false]")
reserve_rect(x0, z0, x1, z1, 3)
area.npc("Agatha", 0, G + 1, PAV_C[1] - 2, 0.0, "Starlyn contest host, in the pavilion at the end of the pier")
area.npc("spawn:contest", PAV_C[0], G + 1, PAV_C[1], 0.0, "the contest pavilion's centre")

# ---------------------------------------------------------------- Murkwater Shallows (the sunken ruin, kelp, sea lumies)
SBX, SBZ = SH_C
SFLOOR = floor_y(SBX, SBZ)
RUIN_BRICK = area.mix({"mossy_stone_bricks": 5, "stone_bricks": 2, "cracked_stone_bricks": 3, "mud_bricks": 1, "deepslate_bricks": 1})
rx0, rx1, rz0, rz1 = SBX - 8, SBX + 8, SBZ - 5, SBZ + 5
ry = lambda x, z: floor_y(x, z)
# a terrace level with the bowl floor under the ruin
for x in range(rx0 - 1, rx1 + 2):
    for z in range(rz0 - 1, rz1 + 2):
        fy = floor_y(x, z)
        for y in range(min(fy, SFLOOR) + 1, SFLOOR + 1):
            S(x, y, z, "minecraft:water" if y > SFLOOR else RUIN_BRICK(x, y, z))
        S(x, SFLOOR, z, RUIN_BRICK(x, SFLOOR, z) if (x + z) % 2 == 0 or True else "minecraft:mud")
        for y in range(SFLOOR + 1, SFLOOR + 7):
            S(x, y, z, "minecraft:water")
for x in range(rx0, rx1 + 1):
    for z in range(rz0, rz1 + 1):
        perim = x in (rx0, rx1) or z in (rz0, rz1)
        if perim:
            hgt = int(1 + 4 * hash01(x, 4, z, 31))
            if x in (rx0, rx1) and z in (rz0, rz1):
                hgt = 7
            if (x == SBX and z == rz0) or (z == SBZ and x == rx0) or (x == SBX and z == rz1):
                hgt = 0                         # gateways
            for k in range(1, hgt + 1):
                S(x, SFLOOR + k, z, RUIN_BRICK(x, SFLOOR + k, z))
# inner pillars and a statue plinth, with sea lanterns
for px, pz in ((SBX - 4, SBZ - 2), (SBX + 4, SBZ - 2), (SBX - 4, SBZ + 2), (SBX + 4, SBZ + 2)):
    for k in range(1, 5 + int(hash01(px, 1, pz, 5) * 3)):
        S(px, SFLOOR + k, pz, "minecraft:chiseled_stone_bricks" if k == 1 else RUIN_BRICK(px, SFLOOR + k, pz))
    S(px, SFLOOR + 1, pz, "minecraft:sea_lantern")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        S(SBX + dx, SFLOOR + 1, SBZ + dz, "minecraft:mossy_stone_bricks")
S(SBX, SFLOOR + 2, SBZ, "minecraft:chiseled_stone_bricks")
S(SBX, SFLOOR + 3, SBZ, "minecraft:sea_lantern")
# an archway
for k in range(1, 5):
    S(SBX - 1, SFLOOR + k, rz0, RUIN_BRICK(SBX - 1, SFLOOR + k, rz0))
    S(SBX + 1, SFLOOR + k, rz0, RUIN_BRICK(SBX + 1, SFLOOR + k, rz0))
S(SBX, SFLOOR + 5, rz0, "minecraft:chiseled_stone_bricks")
S(SBX - 1, SFLOOR + 5, rz0, RUIN_BRICK(SBX - 1, SFLOOR + 5, rz0))
S(SBX + 1, SFLOOR + 5, rz0, RUIN_BRICK(SBX + 1, SFLOOR + 5, rz0))
for k in range(1, 5):
    S(SBX, SFLOOR + k, rz0, "minecraft:water")
SOUL_SH = (SBX + 6, SFLOOR + 1, SBZ + 3)
reserve_rect(rx0, rz0, rx1, rz1, 0)

# kelp, seagrass and sea pickles over the Shallows (and a little through the rest of the lake floor)
n_pickles = 0
for (x, z) in sorted(LAKE):
    fy = T[(x, z)]
    if area.get(x, fy + 1, z) not in (None, "minecraft:water") or (x, z) in occ:
        continue
    h = hash01(x, 20, z, 41)
    inbowl = ell(x, z, SBX, SBZ, SH_R[0] + 3, SH_R[1] + 3) < 1
    if inbowl:
        if h < 0.06:
            ln = 2 + int(hash01(x, 21, z, 4) * 7)
            ln = min(ln, WATER_TOP - fy - 2)
            for k in range(1, ln + 1):
                S(x, fy + k, z, "minecraft:kelp[age=25]" if k == ln else "minecraft:kelp_plant")
        elif h < 0.22:
            S(x, fy + 1, z, "minecraft:seagrass")
        elif h < 0.30 and WATER_TOP - fy >= 3:
            S(x, fy + 1, z, "minecraft:tall_seagrass[half=lower]")
            S(x, fy + 2, z, "minecraft:tall_seagrass[half=upper]")
        elif h < 0.40:
            S(x, fy + 1, z, f"minecraft:sea_pickle[pickles={1 + int(hash01(x, 22, z, 3) * 4)},waterlogged=true]")
            n_pickles += 1
    else:
        if h < 0.012 and WATER_TOP - fy >= 4:
            ln = min(2 + int(hash01(x, 21, z, 4) * 5), WATER_TOP - fy - 2)
            for k in range(1, ln + 1):
                S(x, fy + k, z, "minecraft:kelp[age=25]" if k == ln else "minecraft:kelp_plant")
        elif h < 0.09:
            S(x, fy + 1, z, "minecraft:seagrass")

# ---------------------------------------------------------------- Nemo's dock (south shore of the Shallows)
NX = SBX
zs = max(z for (x, z) in LAKE if x == NX)
for z in range(zs - 9, zs + 4):
    for dx in (-1, 0, 1):
        deck(NX + dx, z, G)
    for dx in (-2, 2):
        S(NX + dx, G, z, "minecraft:stripped_spruce_log[axis=z]")
        S(NX + dx, G + 1, z, "minecraft:spruce_fence")
    if (z - zs) % 3 == 0:
        for dx in (-2, 2):
            pile(NX + dx, z)
post_lantern(NX - 2, zs - 9, G + 1, soul=True)
post_lantern(NX + 2, zs - 9, G + 1, soul=True)
# a gear stall at the dock's end: crate, barrels, a diving "helmet" rack (turtle helmet on a fence), a small shed roof
S(NX + 2, G + 1, zs - 4, "minecraft:barrel[facing=up]")
S(NX + 2, G + 1, zs - 5, "minecraft:barrel[facing=up]")
S(NX - 2, G + 1, zs - 4, "minecraft:composter")
S(NX - 2, G + 1, zs - 5, "minecraft:cauldron")
for k in range(3):
    S(NX - 3, G + 1 + k, zs - 7, "minecraft:dark_oak_fence")
    S(NX + 3, G + 1 + k, zs - 7, "minecraft:dark_oak_fence")
for dx in range(-3, 4):
    S(NX + dx, G + 4, zs - 7, "minecraft:spruce_slab[type=bottom]")
    S(NX + dx, G + 4, zs - 5, "minecraft:spruce_slab[type=bottom]")
    S(NX + dx, G + 4, zs - 6, "minecraft:spruce_slab[type=bottom]")
for dx in (-2, 2):
    pile(NX + dx, zs - 7)
# a lantern under the roof, not a post in front of Nemo (customers click him from the dock)
S(NX, G + 3, zs - 7, "minecraft:lantern[hanging=true]")
for dx in range(-3, 4):
    for dz in (-7, -5):
        pass
area.npc("Nemo", NX, G + 1, zs - 8, 0.0, "snorkeling gear seller, on the dock at the Shallows")
area.npc("spawn:shallows", SBX - 6, SFLOOR + 1, SBZ - 1, 0.0, "a standing spot on the lake floor in the Shallows, under water")
reserve_rect(NX - 4, zs - 10, NX + 4, zs + 4, 2)

# ---------------------------------------------------------------- Murkwater Outpost (stilted huts and docks, south shore)
OA = 26
zsA = max(z for (x, z) in LAKE if x == OA)
# boardwalk from the south path up to the huts
spine_z0, spine_z1 = zsA - 8, 127
for z in range(spine_z0, spine_z1 + 1):
    for dx in (-1, 0, 1):
        deck(OA + dx, z)
    for dx in (-2, 2):
        S(OA + dx, G, z, "minecraft:stripped_spruce_log[axis=z]")
        S(OA + dx, G + 1, z, "minecraft:spruce_fence")
    if z % 4 == 0:
        for dx in (-2, 2):
            pile(OA + dx, z)
# the trading post (Rosemary) on the lake edge
HA = (OA - 6, zsA - 10, OA + 6, zsA - 2)
hut(*HA, door=(OA, HA[3]), wall="dark_oak_planks", roof=("spruce_stairs", "spruce_planks"))
for x in range(OA - 4, OA + 5):
    # a waist-high counter, so customers can reach Rosemary over it
    S(x, G + 1, HA[1] + 4, "minecraft:stripped_dark_oak_log[axis=x]")
S(OA - 5, G + 1, HA[1] + 1, "minecraft:barrel[facing=up]")
S(OA + 5, G + 1, HA[1] + 1, "minecraft:barrel[facing=up]")
S(OA - 5, G + 1, HA[1] + 2, "minecraft:barrel[facing=up]")
S(OA + 5, G + 1, HA[1] + 2, "minecraft:barrel[facing=up]")
S(OA + 4, G + 1, HA[1] + 1, "minecraft:chest[facing=south]")
S(OA - 4, G + 1, HA[1] + 1, "minecraft:crafting_table")
S(OA - 4, G + 2, HA[1] + 1, "minecraft:potted_fern")
for dx in (-5, 5):
    S(OA + dx, G + 1, HA[3] - 1, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]" if False else "minecraft:composter")
area.npc("Rosemary", OA, G + 1, HA[1] + 3, 0.0, "Forest Essence shop, behind the trading post's counter")
# a second hut (the fisher's) east of the spine and a bunk hut west
HB = (OA + 10, zsA - 8, OA + 18, zsA - 2)
hut(*HB, door=(HB[0], HB[3] - 2), wall="spruce_planks", roof=("dark_oak_stairs", "dark_oak_planks"))
HC = (OA - 20, zsA - 7, OA - 12, zsA - 1)
hut(*HC, door=(HC[2], HC[3] - 2), wall="spruce_planks", roof=("dark_oak_stairs", "dark_oak_planks"))
for z in (HB[3] - 2,):
    for x in range(OA + 2, HB[0]):
        deck(x, z)
for z in (HC[3] - 2,):
    for x in range(HC[2] + 1, OA - 1):
        deck(x, z)
for x in range(OA + 2, HB[0]):
    for z in (HB[3] - 3, HB[3] - 1):
        S(x, G + 1, z, "minecraft:spruce_fence")
for x in range(HC[2] + 1, OA - 1):
    for z in (HC[3] - 3, HC[3] - 1):
        S(x, G + 1, z, "minecraft:spruce_fence")
for x in range(OA + 2, HB[0], 3):
    pile(x, HB[3] - 2)
for x in range(HC[2] + 1, OA - 1, 3):
    pile(x, HC[3] - 2)
# docks reaching out into the lake with crates, net poles and lanterns
def dock(dx0, z_from, z_to, w=2):
    for z in range(z_from, z_to - 1, -1):
        for dx in range(dx0, dx0 + w):
            deck(dx, z)
        S(dx0 - 1, G, z, "minecraft:stripped_spruce_log[axis=z]")
        S(dx0 + w, G, z, "minecraft:stripped_spruce_log[axis=z]")
        S(dx0 - 1, G + 1, z, "minecraft:spruce_fence")
        S(dx0 + w, G + 1, z, "minecraft:spruce_fence")
        if (z_from - z) % 4 == 0:
            pile(dx0 - 1, z)
            pile(dx0 + w, z)
    post_lantern(dx0 - 1, z_to, G + 1)
    barrel(dx0, G + 1, z_to + 2)
    barrel(dx0 + w - 1, G + 1, z_to + 3)
    reserve_rect(dx0 - 2, z_to - 1, dx0 + w + 1, z_from, 1)


dock(OA - 1, HA[1] - 1, HA[1] - 14, 3)
dock(HB[0] + 4, HB[1] - 1, HB[1] - 11, 2)
dock(HC[0] + 3, HC[1] - 1, HC[1] - 9, 2)
# a long dock east along the shore with fishing racks
for z in range(zsA - 1, zsA + 1):
    pass
reserve_rect(HC[0], HC[1], HB[2], 128, 3)
for x in range(HB[2] + 1, HB[2] + 12):
    for dz in (0, 1):
        deck(x, zsA - 4 + dz)
    S(x, G + 1, zsA - 5, "minecraft:spruce_fence")
    S(x, G + 1, zsA - 2, "minecraft:spruce_fence")
    if x % 3 == 0:
        pile(x, zsA - 4)
        pile(x, zsA - 3)
reserve_rect(HB[2], zsA - 6, HB[2] + 13, zsA, 1)
post_lantern(HB[2] + 11, zsA - 5, G + 1)

# ---------------------------------------------------------------- SwampCut Inc. (the logging camp, south-east)
CX0, CZ0 = CAMP_C


def log_pile(x0, z0, length, axis, rows=3, log="spruce_log"):
    for r in range(rows):
        for k in range(length - r):
            if axis == "x":
                for dz in range(0, 2 + (rows - r > 1)):
                    S(x0 + k + (r // 2), G + 1 + r, z0 + dz, f"minecraft:{log}[axis=x]")
            else:
                for dx in range(0, 2 + (rows - r > 1)):
                    S(x0 + dx, G + 1 + r, z0 + k + (r // 2), f"minecraft:{log}[axis=z]")


# yard: compacted podzol and coarse dirt (the land is already flat there)
for x in range(CX0 - 26, CX0 + 24):
    for z in range(CZ0 - 22, CZ0 + 24):
        if (x, z) in T and (x, z) not in WET and ell(x, z, CX0, CZ0, 24, 20) < 1 and area.top.get((x, z)) == G:
            h = hash01(x, 31, z, 6)
            S(x, G, z, "minecraft:podzol" if h < 0.35 else "minecraft:coarse_dirt" if h < 0.7 else "minecraft:rooted_dirt" if h < 0.85 else "minecraft:packed_mud")
# the sawmill: an open shed, two stonecutters and grindstones, stacked planks
SM = (CX0 - 16, CZ0 - 9, CX0 - 4, CZ0 - 1)
for x in (SM[0], SM[2]):
    for z in (SM[1], SM[3], (SM[1] + SM[3]) // 2):
        for y in range(G + 1, G + 6):
            S(x, y, z, "minecraft:dark_oak_log[axis=y]")
for x in range(SM[0], SM[2] + 1):
    for z in (SM[1], SM[3]):
        S(x, G + 5, z, "minecraft:stripped_dark_oak_log[axis=x]")
for z in range(SM[1], SM[3] + 1):
    for x in (SM[0], SM[2]):
        S(x, G + 5, z, "minecraft:stripped_dark_oak_log[axis=z]")
hip_roof(SM[0], SM[1], SM[2], SM[3], G + 6, "spruce_stairs", "spruce_planks", over=1)
for x in range(SM[0] + 2, SM[2] - 1, 3):
    S(x, G + 1, SM[1] + 2, "minecraft:stonecutter[facing=south]")
    S(x + 1, G + 1, SM[1] + 2, "minecraft:grindstone[face=floor,facing=south]")
for x in range(SM[0] + 1, SM[2], 1):
    for k in range(3):
        if (x + k) % 5:
            S(x, G + 1 + k, SM[3] - 1, "minecraft:spruce_planks" if k < 2 else "minecraft:dark_oak_planks")
S(SM[0] + 6, G + 5, SM[1] + 4, "minecraft:lantern[hanging=true]")
S(SM[0] + 6, G + 5, SM[1] + 7, "minecraft:lantern[hanging=true]")
for x in range(SM[0] + 3, SM[2] - 2, 2):
    S(x, G + 1, SM[1] + 5, "minecraft:spruce_log[axis=x]")
reserve_rect(SM[0] - 1, SM[1] - 1, SM[2] + 1, SM[3] + 1, 2)
# the bunkhouse
BH = (CX0 + 6, CZ0 - 12, CX0 + 16, CZ0 - 4)
hut(*BH, door=(BH[0], BH[3] - 2), wall="spruce_planks", roof=("dark_oak_stairs", "dark_oak_planks"), pilings=False)
for z in range(BH[1] + 2, BH[3] - 1, 3):
    S(BH[2] - 1, G + 1, z, "minecraft:red_bed[facing=west,part=foot]")
    S(BH[2] - 2, G + 1, z, "minecraft:red_bed[facing=west,part=head]")
# Amaury's axe stall: a counter under a striped awning
AX, AZ = CX0 - 2, CZ0 + 11
for x in range(AX - 4, AX + 5):
    S(x, G + 1, AZ + 2, "minecraft:stripped_spruce_log[axis=x]")
    S(x, G + 2, AZ + 2, "minecraft:spruce_slab[type=bottom]")
for x in (AX - 4, AX + 4):
    for z in (AZ - 3, AZ + 2):
        for y in range(G + 1, G + 5):
            S(x, y, z, "minecraft:dark_oak_fence")
for x in range(AX - 5, AX + 6):
    for z in range(AZ - 3, AZ + 3):
        S(x, G + 5, z, "minecraft:orange_wool" if (x // 1) % 2 == 0 else "minecraft:white_wool")
for x in range(AX - 3, AX + 4, 2):
    S(x, G + 1, AZ - 3, "minecraft:barrel[facing=south]")
S(AX - 3, G + 2, AZ + 2, "minecraft:lantern[hanging=false]")
S(AX + 3, G + 2, AZ + 2, "minecraft:lantern[hanging=false]")
for x in range(AX - 3, AX + 4):
    S(x, G + 4, AZ + 2, "minecraft:iron_chain[axis=y]") if x in (AX - 1, AX + 1) else None
for k in range(3):
    S(AX - 2 + 2 * k, G + 3, AZ + 2, "minecraft:iron_chain[axis=y]" if False else "minecraft:air")
S(AX, G + 1, AZ - 1, "minecraft:stripped_dark_oak_log[axis=y]")
area.npc("Amaury", AX, G + 1, AZ, 0.0, "axe seller at SwampCut Inc.")
reserve_rect(AX - 6, AZ - 4, AX + 6, AZ + 3, 2)
# log piles, stumps and felled logs
piles_at = [(CX0 + 8, CZ0 + 4, 7, "x", 3), (CX0 + 8, CZ0 + 8, 6, "x", 3), (CX0 - 22, CZ0 + 6, 6, "z", 3),
            (CX0 + 14, CZ0 + 14, 5, "z", 2)]
for (px, pz, ln, ax, rows) in piles_at:
    log_pile(px, pz, ln, ax, rows, "spruce_log" if hash01(px, 0, pz, 3) < 0.5 else "dark_oak_log")
    reserve_rect(px - 1, pz - 1, px + ln + 1, pz + 5, 0) if ax == "x" else reserve_rect(px - 1, pz - 1, px + 5, pz + ln + 1, 0)
# the hollow log stack (a Fairy Soul inside): a long tunnel through a big pile
HX, HZ = CX0 - 24, CZ0 - 12
for x in range(HX, HX + 8):
    for z in range(HZ, HZ + 5):
        for y in range(G + 1, G + 5):
            S(x, y, z, "minecraft:spruce_log[axis=x]" if (y + z) % 2 else "minecraft:dark_oak_log[axis=x]")
for x in range(HX, HX + 8):
    for y in (G + 1, G + 2):
        S(x, y, HZ + 2, "minecraft:air")
S(HX + 4, G + 3, HZ + 2, "minecraft:lantern[hanging=true]")
for x in range(HX + 1, HX + 7):
    S(x, G + 1, HZ + 2, "minecraft:moss_carpet")
SOUL_LOGS = (HX + 5, G + 1, HZ + 2)
reserve_rect(HX - 1, HZ - 1, HX + 8, HZ + 5, 1)
for i in range(20):
    sx = CX0 - 20 + int(hash01(i, 40, 0, 8) * 40)
    sz = CZ0 - 17 + int(hash01(i, 41, 0, 8) * 38)
    if (sx, sz) in occ or area.top.get((sx, sz)) != G or ell(sx, sz, CX0, CZ0, 22, 18) > 1:
        continue
    if hash01(i, 42, 0, 8) < 0.6:
        for k in range(1 + int(hash01(i, 43, 0, 8) * 2)):
            S(sx, G + 1 + k, sz, "minecraft:dark_oak_log[axis=y]")
        occ.add((sx, sz))
    else:
        ax = "x" if hash01(i, 44, 0, 8) < 0.5 else "z"
        for k in range(4):
            cx, cz = (sx + k, sz) if ax == "x" else (sx, sz + k)
            if (cx, cz) not in occ:
                S(cx, G + 1, cz, f"minecraft:spruce_log[axis={ax}]")
                occ.add((cx, cz))
# the watchtower
WTX, WTZ = CX0 + 16, CZ0 + 6
for dx, dz in ((0, 0), (4, 0), (0, 4), (4, 4)):
    for y in range(G + 1, G + 13):
        S(WTX + dx, y, WTZ + dz, "minecraft:dark_oak_log[axis=y]")
for dx in range(-1, 6):
    for dz in range(-1, 6):
        S(WTX + dx, G + 9, WTZ + dz, "minecraft:dark_oak_planks")
for dx in range(-1, 6):
    for dz in (-1, 5):
        S(WTX + dx, G + 10, WTZ + dz, "minecraft:spruce_fence")
for dz in range(0, 5):
    for dx in (-1, 5):
        S(WTX + dx, G + 10, WTZ + dz, "minecraft:spruce_fence")
for y in range(G + 1, G + 10):
    S(WTX + 2, y, WTZ - 1, "minecraft:ladder[facing=south]")
    S(WTX + 2, y, WTZ, "minecraft:dark_oak_planks") if False else None
S(WTX + 2, G + 9, WTZ - 1, "minecraft:air")
for y in range(G + 1, G + 10):
    S(WTX + 2, y, WTZ - 1, "minecraft:ladder[facing=south]")
for dx in range(-1, 6):
    S(WTX + dx, G + 10, WTZ - 1, "minecraft:spruce_fence") if dx != 2 else None
hip_roof(WTX, WTZ, WTX + 4, WTZ + 4, G + 13, "spruce_stairs", "spruce_planks", over=1)
S(WTX + 2, G + 12, WTZ + 2, "minecraft:lantern[hanging=true]")
for dx, dz in ((0, 0), (4, 0), (0, 4), (4, 4)):
    S(WTX + dx, G + 1, WTZ + dz, "minecraft:dark_oak_log[axis=y]")
for dz in (WTZ, WTZ + 4):
    for dx in range(0, 5):
        S(WTX + dx, G + 5, dz, "minecraft:dark_oak_planks") if dx not in (0, 4) else None
reserve_rect(WTX - 2, WTZ - 3, WTX + 6, WTZ + 6, 1)
# a signpost-ish gatepost at the camp's entrance (path comes in from the west)
for dz in (-2, 2):
    for y in range(G + 1, G + 6):
        S(CX0 - 28, y, 123 + dz + 1, "minecraft:dark_oak_log[axis=y]") if False else None
GX, GZ = 106, 118
for dz in (0, 6):
    for y in range(G + 1, G + 6):
        S(GX, y, GZ + dz, "minecraft:dark_oak_log[axis=y]")
for dz in range(0, 7):
    S(GX, G + 5, GZ + dz, "minecraft:stripped_dark_oak_log[axis=z]")
S(GX, G + 4, GZ + 1, "minecraft:lantern[hanging=true]")
S(GX, G + 4, GZ + 5, "minecraft:lantern[hanging=true]")
for dz in (1, 2, 3, 4, 5):
    if dz in (1, 5):
        S(GX, G + 4, GZ + dz, "minecraft:lantern[hanging=true]")
reserve_rect(GX - 1, GZ - 1, GX + 1, GZ + 7, 1)

# ---------------------------------------------------------------- ruins: moss and broken stone in the wetlands
MOSSY = area.mix({"mossy_stone_bricks": 5, "cracked_stone_bricks": 3, "stone_bricks": 2, "mossy_cobblestone": 2, "mud_bricks": 1})


def ruin_hall(cx, cz, w=11, d=9, seed=1):
    x0, x1, z0, z1 = cx - w // 2, cx + w // 2, cz - d // 2, cz + d // 2
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if (x, z) in T and (x, z) not in WET:
                S(x, G, z, MOSSY(x, G, z))
                area.top[(x, z)] = G
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            perim = x in (x0, x1) or z in (z0, z1)
            if (x, z) not in T or (x, z) in WET:
                continue
            if perim:
                corner = x in (x0, x1) and z in (z0, z1)
                hgt = 5 if corner else int(hash01(x, 3, z, seed) * 4)
                if (x == cx and z == z0) or (z == cz and x == x1):
                    hgt = 0                               # doorways
                for k in range(1, hgt + 1):
                    S(x, G + k, z, MOSSY(x, G + k, z))
                if hgt and hash01(x, 6, z, seed) < 0.5:
                    S(x, G + hgt + 1, z, "minecraft:moss_carpet")
            elif hash01(x, 8, z, seed) < 0.18:
                S(x, G + 1, z, "minecraft:moss_carpet")
            elif hash01(x, 9, z, seed) < 0.05:
                S(x, G + 1, z, MOSSY(x, G, z))
    # the altar
    S(cx, G + 1, cz, "minecraft:chiseled_stone_bricks")
    S(cx, G + 2, cz, "minecraft:soul_lantern[hanging=false]")
    for dx in (-1, 1):
        S(cx + dx, G + 1, cz, "minecraft:mossy_stone_brick_slab[type=bottom]")
    # an archway
    for k in range(1, 5):
        S(cx - 1, G + k, z1, MOSSY(cx - 1, G + k, z1))
        S(cx + 1, G + k, z1, MOSSY(cx + 1, G + k, z1))
    S(cx - 1, G + 5, z1, MOSSY(cx, G + 5, z1))
    S(cx, G + 5, z1, "minecraft:chiseled_stone_bricks")
    for k in range(1, 5):
        S(cx, G + k, z1, "minecraft:air")
    reserve_rect(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 1)


ruin_hall(RUIN_W[0], RUIN_W[1], 11, 9, 1)
# the eastern ruin: a broken round tower with a stair of ladders inside (a Fairy Soul on top)
TX, TZ = RUIN_E
for x in range(TX - 4, TX + 5):
    for z in range(TZ - 4, TZ + 5):
        if (x, z) in T and (x, z) not in WET:
            S(x, G, z, MOSSY(x, G, z))
            area.top[(x, z)] = G
        r = math.hypot(x - TX, z - TZ)
        if 2.3 <= r < 3.6 and (x, z) in T:
            hgt = 11 if (x - TX, z - TZ) in ((3, 0), (-3, 0), (0, 3), (0, -3), (2, 2), (-2, -2)) else 4 + int(hash01(x, 5, z, 2) * 6)
            if (x, z) in ((TX, TZ - 3), (TX + 1, TZ - 3)):
                hgt = 0
            for k in range(1, hgt + 1):
                S(x, G + k, z, MOSSY(x, G + k, z))
        elif r < 2.3 and (x, z) in T:
            S(x, G, z, MOSSY(x, G, z))
S(TX, G + 12, TZ + 3, MOSSY(TX, G + 12, TZ + 3))
for y in range(G + 1, G + 13):
    S(TX, y, TZ + 2, "minecraft:ladder[facing=north]")
    S(TX, y, TZ + 3, MOSSY(TX, y, TZ + 3)) if y > G + 11 else None
S(TX, G + 11, TZ + 2, "minecraft:ladder[facing=north]")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        S(TX + dx, G + 11, TZ + dz, MOSSY(TX + dx, G + 11, TZ + dz)) if (dx, dz) != (0, 2) else None
S(TX, G + 11, TZ + 1, "minecraft:mossy_stone_bricks")
SOUL_TOWER = (TX, G + 13, TZ)
S(TX, G + 12, TZ, "minecraft:air")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        S(TX + dx, G + 12, TZ + dz, "minecraft:air")
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        S(TX + dx, G + 12, TZ + dz, "minecraft:mossy_stone_bricks")
S(TX, G + 13, TZ, "minecraft:air")
reserve_rect(TX - 6, TZ - 6, TX + 6, TZ + 6, 1)
# a few more crumbled pillars scattered over the wetlands
for i in range(26):
    px = int(X0 + 8 + hash01(i, 50, 0, 3) * (X1 - X0 - 16))
    pz = int(16 + hash01(i, 51, 0, 3) * 125)
    if (px, pz) in occ or (px, pz) not in T or (px, pz) in WET or abs(px) < 74 or area.top.get((px, pz)) is None:
        continue
    if any((px + dx, pz + dz) in WET for dx in (-1, 0, 1) for dz in (-1, 0, 1)) or flat(px, pz):
        continue
    t = area.top[(px, pz)]
    for k in range(1, 2 + int(hash01(i, 52, 0, 3) * 4)):
        S(px, t + k, pz, MOSSY(px, t + k, pz))
    S(px, t + 1, pz + 1, "minecraft:moss_carpet")
    occ.add((px, pz))
    occ.add((px, pz + 1))

# ---------------------------------------------------------------- the Squid Cave
cx_, cz_ = CAVE_C
BED_C = 56
LEDGE = 64
for x in range(cx_ - CAVE_R[0] - 1, cx_ + CAVE_R[0] + 2):
    for z in range(cz_ - CAVE_R[1] - 1, cz_ + CAVE_R[1] + 2):
        if not chamber_cell(x, z):
            continue
        ceil = cave_ceiling(x, z)
        if pool_cell(x, z):
            S(x, BED_C, z, "minecraft:mud" if hash01(x, 1, z, 3) < 0.7 else "minecraft:moss_block")
            for y in range(BED_C + 1, LEDGE):
                S(x, y, z, "minecraft:water")
            for y in range(LEDGE, ceil + 1):
                S(x, y, z, "minecraft:air")
        else:
            S(x, LEDGE, z, "minecraft:moss_block" if hash01(x, 2, z, 3) < 0.45 else "minecraft:mud" if hash01(x, 2, z, 4) < 0.5 else "minecraft:tuff")
            for y in range(LEDGE + 1, ceil + 1):
                S(x, y, z, "minecraft:air")
            for y in range(LEDGE - 1, BED_C - 1, -1):
                S(x, y, z, "minecraft:tuff")
# the stairs down from the surface (south of the path's end)
for i in range(4):
    for s in (-1, 0, 1):
        x, z = cx_ + s, 133 + i
        S(x, G - 1 - i, z, "minecraft:mossy_cobblestone_stairs[facing=north,half=bottom,shape=straight]")
        for y in range(G - i, G + 4):
            S(x, y, z, "minecraft:air")
        S(x, G - 2 - i, z, "minecraft:mossy_cobblestone")
# corridor to the chamber ledge
for z in range(137, 141):
    for s in (-1, 0, 1):
        x = cx_ + s
        S(x, LEDGE, z, "minecraft:mossy_cobblestone" if hash01(x, 3, z, 7) < 0.5 else "minecraft:moss_block")
        for y in range(LEDGE + 1, LEDGE + 5):
            S(x, y, z, "minecraft:air")
# rock islets in the pool (the squid-spawning spots, and a place to stand): tuff pillars to just above the water
cave_pillars = 0
for x in range(cx_ - 13, cx_ + 14):
    for z in range(cz_ - 7, cz_ + 8):
        if pool_cell(x, z) and ell(x, z, cx_, cz_, *POOL_R) < 0.88 and hash01(x, 40, z, 5) < 0.09:
            for y in range(BED_C, LEDGE + 1):
                S(x, y, z, "minecraft:tuff" if y < LEDGE else "minecraft:moss_block")
            cave_pillars += 1
# glow: sea lanterns in the floor, glow lichen on walls and ceiling, amethyst, dripstone, hanging roots
for x in range(cx_ - 17, cx_ + 18):
    for z in range(cz_ - 9, cz_ + 10):
        if not chamber_cell(x, z):
            continue
        ceil = cave_ceiling(x, z)
        if pool_cell(x, z) and hash01(x, 11, z, 8) < 0.025:
            S(x, BED_C, z, "minecraft:sea_lantern")
        if area.get(x, ceil + 1, z) not in (None, "minecraft:air", "minecraft:water") and hash01(x, 12, z, 8) < 0.45:
            S(x, ceil, z, "minecraft:glow_lichen[down=false,up=true]" if False else "minecraft:glow_lichen[up=true]") if False else None
            S(x, ceil, z, "minecraft:glow_lichen[up=true]")
        if hash01(x, 13, z, 8) < 0.05 and area.get(x, ceil + 1, z) not in (None, "minecraft:air"):
            ln = 1 + int(hash01(x, 14, z, 8) * 3)
            for i in range(ln):
                S(x, ceil - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" + ("tip" if i == ln - 1 else "base") + "]")
        if hash01(x, 15, z, 8) < 0.04 and not pool_cell(x, z):
            S(x, LEDGE + 1, z, "minecraft:amethyst_cluster[facing=up]" if hash01(x, 16, z, 8) < 0.5 else "minecraft:glow_lichen[down=true]")
        for dx, dz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            nx, nz = x + dx, z + dz
            if not chamber_cell(nx, nz) and hash01(x, 17, z, 8) < 0.35 and (nx, nz) in T:
                y = LEDGE + 1 + int(hash01(x, 18, z, 8) * 3)
                if area.get(x, y, z) in (None, "minecraft:air") and y < ceil:
                    S(x, y, z, f"minecraft:glow_lichen[{face}=true]") if False else None
# an edge window of glow: patches of lichen on the chamber walls (attached to the wall block on the face)
for x in range(cx_ - 18, cx_ + 19):
    for z in range(cz_ - 10, cz_ + 11):
        if chamber_cell(x, z):
            continue
        for dx, dz, face in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            # cell (x, z) is wall; the chamber cell next to it is (x + dx, z + dz)
            nx, nz = x + dx, z + dz
            if chamber_cell(nx, nz):
                ceil = cave_ceiling(nx, nz)
                for y in range(LEDGE + 1, ceil):
                    if hash01(nx * 3 + dx, y, nz * 3 + dz, 19) < 0.22 and area.get(nx, y, nz) in (None, "minecraft:air"):
                        S(nx, y, nz, f"minecraft:glow_lichen[{face}=true]")
SOUL_CAVE = (cx_ + 13, LEDGE + 1, cz_ + 1)
for dx in range(-1, 2):
    for dz in range(-1, 2):
        S(cx_ + 13 + dx, LEDGE, cz_ + 1 + dz, "minecraft:moss_block")
        for y in range(LEDGE + 1, LEDGE + 4):
            S(cx_ + 13 + dx, y, cz_ + 1 + dz, "minecraft:air")
S(cx_ + 13, LEDGE + 1, cz_ + 3, "minecraft:amethyst_cluster[facing=up]")
S(cx_ + 12, LEDGE + 1, cz_ - 1, "minecraft:glow_lichen[down=true]")
reserve_rect(cx_ - 24, 131, cx_ + 24, 159, 0)

# ---------------------------------------------------------------- trees (the mod finds them: follow the rules in galatea_lib)
tree_cells = set()          # logs and roots of every placed tree
n_mangrove = 0
n_fig = 0
trees = []


def clear_of_trees(cells):
    for (x, y, z) in cells:
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(-2, 3):
                    if (x + dx, y + dy, z + dz) in tree_cells:
                        return False
    return True


def free_cell(x, y, z, water_ok=True):
    b = area.get(x, y, z)
    if b is None or b == "minecraft:air":
        return area.inside(x, y, z)
    if water_ok and b == "minecraft:water":
        return True
    return False


def mangrove(x, z, seed=0, height=None, islet=False):
    """A mangrove: roots in and above the water, a trunk, branches and a canopy. Returns its top y or None."""
    global n_mangrove
    ground = T.get((x, z))
    if ground is None:
        return None
    h = height or 6 + int(hash01(x, 0, z, seed) * 7)
    h = max(6, min(12, h))
    cells = {}                                  # (x, y, z) -> block
    roots = 0
    base = max(ground + 1, G - 1) if (x, z) in WET else ground + 1
    # the trunk
    if (x, z) in WET:
        for y in range(ground + 1, G - 1):
            cells[(x, y, z)] = "minecraft:muddy_mangrove_roots[axis=y]"
            roots += 1
    for y in range(base, base + h):
        cells[(x, y, z)] = "minecraft:mangrove_log[axis=y]"
    top = base + h - 1
    logs = h
    # the root skirt
    ring = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1), (2, 0), (-2, 0), (0, 2), (0, -2)]
    order = sorted(ring, key=lambda d: hash01(x + d[0], 1, z + d[1], seed))
    for dx, dz in order:
        if roots >= 15:
            break
        c = (x + dx, z + dz)
        if c not in T or c in path_cols:
            continue
        gy = T[c]
        col = {}
        if c in WET:
            for y in range(gy + 1, G + 1):
                if y < WATER_TOP:
                    col[(c[0], y, c[1])] = "minecraft:muddy_mangrove_roots[axis=y]"
                elif y == WATER_TOP:
                    col[(c[0], y, c[1])] = "minecraft:mangrove_roots[waterlogged=true]"
                else:
                    col[(c[0], y, c[1])] = "minecraft:mangrove_roots[waterlogged=false]"
        else:
            col[(c[0], gy + 1, c[1])] = "minecraft:mangrove_roots[waterlogged=false]"
            if hash01(c[0], 2, c[1], seed) < 0.5:
                col[(c[0], gy + 2, c[1])] = "minecraft:mangrove_roots[waterlogged=false]"
        if roots + len(col) > 20:
            continue
        cells.update(col)
        roots += len(col)
    if roots < 8:
        return None
    # branches
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    bi = 0
    leaf_spots = [(x, top + 1, z)]
    while logs < 24 and bi < 8:
        d = dirs[int(hash01(x, 10 + bi, z, seed) * 4) % 4]
        by = top - 1 - int(hash01(x, 20 + bi, z, seed) * 3)
        ln = 2 + int(hash01(x, 30 + bi, z, seed) * 3)
        axis = "x" if d[0] else "z"
        for i in range(1, ln + 1):
            p = (x + d[0] * i, by + (1 if i == ln and ln >= 3 else 0), z + d[1] * i)
            if p not in cells:
                cells[p] = f"minecraft:mangrove_log[axis={axis}]"
                logs += 1
        leaf_spots.append((x + d[0] * ln, by + 1, z + d[1] * ln))
        bi += 1
    if logs > 50:
        return None
    # validity: free space, clear of other trees
    for (px, py, pz), b in cells.items():
        if not area.inside(px, py, pz):
            return None
        if "roots" in b and "muddy" not in b:
            if not free_cell(px, py, pz):
                return None
        elif not free_cell(px, py, pz) and not (px == x and pz == z):
            return None
        if (px, pz) in path_cols:
            return None
    if not clear_of_trees(cells.keys()):
        return None
    if any((cx, cz) in occ and (cx, cz) != (x, z) for (cx, cy, cz) in cells if cy >= G + 1) and not islet:
        return None
    for p, b in cells.items():
        S(p[0], p[1], p[2], b)
        tree_cells.add(p)
    # canopy
    for lx, ly, lz in leaf_spots:
        big = (lx, lz) == (x, z)
        rad = 3.4 if big else 2.2
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                for dy in range(-2, 3 if big else 2):
                    r2 = dx * dx + dz * dz + (dy * 1.5) ** 2
                    if r2 <= rad * rad and hash01(lx + dx, ly + dy, lz + dz, seed + 3) > 0.1:
                        if area.get(lx + dx, ly + dy, lz + dz) in (None, "minecraft:air"):
                            S(lx + dx, ly + dy, lz + dz, "minecraft:mangrove_leaves[persistent=true]")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if hash01(x + dx, 99, z + dz, seed + 5) < 0.2:
                for y in range(top + 1, top - 3, -1):
                    if area.get(x + dx, y, z + dz) == "minecraft:mangrove_leaves[persistent=true]" and \
                            area.get(x + dx, y - 1, z + dz) in (None, "minecraft:air"):
                        S(x + dx, y - 1, z + dz, "minecraft:mangrove_propagule[age=3,hanging=true,stage=0]")
                        break
    # a firefly light under the canopy
    S(x + 1, top - 2, z, "minecraft:light[level=6]") if area.get(x + 1, top - 2, z) in (None, "minecraft:air") else None
    reserve(x, z, 2)
    n_mangrove += 1
    trees.append(("mangrove", x, z, top, logs, roots))
    return top


FIG_LEAVES = area.mix({"azalea_leaves": 3, "flowering_azalea_leaves": 2})


def fig(x, z, seed=0, big=False):
    global n_fig
    gy = area.top.get((x, z))
    if gy is None or (x, z) in WET:
        return False
    h = 5 + int(hash01(x, 0, z, seed) * 5)
    h = min(9, h)
    cells = {}
    footprint = [(0, 0), (1, 0), (0, 1), (1, 1)] if big else [(0, 0)]
    for dx, dz in footprint:
        gg = area.top.get((x + dx, z + dz))
        if gg is None or (x + dx, z + dz) in WET or (x + dx, z + dz) in path_cols:
            return False
        for y in range(gg + 1, gy + 1 + h):
            cells[(x + dx, y, z + dz)] = "minecraft:jungle_log[axis=y]"
    top = gy + h
    logs = len(cells)
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    leaf_spots = [(x, top, z)]
    nb = 3 + int(hash01(x, 3, z, seed) * 3)
    for bi in range(nb):
        d = dirs[int(hash01(x, 10 + bi, z, seed) * 4) % 4]
        by = top - 1 - int(hash01(x, 20 + bi, z, seed) * 3)
        ln = 1 + int(hash01(x, 30 + bi, z, seed) * 4)
        axis = "x" if d[0] else "z"
        sx = x + (1 if big and d[0] > 0 else 0)
        sz = z + (1 if big and d[1] > 0 else 0)
        for i in range(1, ln + 1):
            p = (sx + d[0] * i, by, sz + d[1] * i)
            if p not in cells:
                cells[p] = f"minecraft:jungle_log[axis={axis}]"
                logs += 1
        leaf_spots.append((sx + d[0] * ln, by + 1, sz + d[1] * ln))
    if logs < 12 or logs > 35:
        return False
    for (px, py, pz) in cells:
        if not area.inside(px, py, pz) or not free_cell(px, py, pz, False):
            if not (area.get(px, py, pz) in (None, "minecraft:air") or (px, pz) == (x, z)):
                return False
        if (px, pz) in occ and py > gy + 1:
            return False
    if not clear_of_trees(cells.keys()):
        return False
    for p, b in cells.items():
        S(p[0], p[1], p[2], b)
        tree_cells.add(p)
    for lx, ly, lz in leaf_spots:
        main = (lx, lz) == (x, z)
        rad = 3.6 if main else 2.3
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                for dy in range(-2, 3):
                    if dx * dx + dz * dz + (dy * 1.5) ** 2 <= rad * rad and hash01(lx + dx, ly + dy, lz + dz, seed + 3) > 0.12:
                        if area.get(lx + dx, ly + dy, lz + dz) in (None, "minecraft:air"):
                            S(lx + dx, ly + dy, lz + dz, FIG_LEAVES(lx + dx, ly + dy, lz + dz) + "[persistent=true]")
    reserve(x, z, 3)
    n_fig += 1
    trees.append(("fig", x, z, top, logs, 0))
    return True


# a tall mangrove on the biggest islet with a vine ladder (a Fairy Soul at the very top)
ISX, ISZ, _r = ISLETS[1]
FAIRY_TREE = None
for h in (12, 11, 10):
    for (tx, tz) in ((ISX, ISZ), (ISX + 1, ISZ), (ISX, ISZ + 1)):
        top = mangrove(tx, tz, seed=71, height=h, islet=True)
        if top:
            FAIRY_TREE = (tx, tz, top)
            break
    if FAIRY_TREE:
        break
# other islets
for cx, cz, r in (ISLETS[0], ISLETS[2], ISLETS[3]):
    mangrove(cx, cz, seed=cx + cz, islet=True)

# the lake ring: shallows 1-3 deep close to the shore
cands = [(x, z) for (x, z) in LAKE if T[(x, z)] >= WATER_TOP - 3 and (x, z) not in occ and z > 24 and
         0.80 < qlake(x, z) < 0.99]
cands.sort(key=lambda c: hash01(c[0], 60, c[1], 9))
placed = [(t[1], t[2]) for t in trees]
ring_count = 0
for (x, z) in cands:
    if ring_count >= 15 or n_mangrove >= 20:
        break
    if any(math.hypot(x - px, z - pz) < 15 for px, pz in placed):
        continue
    if any(ell(x, z, SH_C[0], SH_C[1], 27, 19) < 1 for _ in (0,)):
        continue
    if mangrove(x, z, seed=x * 7 + z):
        placed.append((x, z))
        ring_count += 1
# some in the wetlands' channels
wet_c = [(x, z) for (x, z) in WET if (x, z) not in LAKE and T[(x, z)] >= WATER_TOP - 2 and (x, z) not in occ and abs(x) > 80]
wet_c.sort(key=lambda c: hash01(c[0], 61, c[1], 9))
wcount = 0
for (x, z) in wet_c:
    if wcount >= 6 or n_mangrove >= 26:
        break
    if any(math.hypot(x - px, z - pz) < 17 for px, pz in placed):
        continue
    if mangrove(x, z, seed=x * 5 + z):
        placed.append((x, z))
        wcount += 1
# fig trees on dry hummocks
fig_c = [(x, z) for (x, z), t in area.top.items() if t >= G + 1 and (x, z) not in occ and abs(x) > 78 and z > 14 and
         area.get(x, t + 1, z) in (None, "minecraft:air")]
fig_c.sort(key=lambda c: hash01(c[0], 62, c[1], 9))
fplaced = []
for (x, z) in fig_c:
    if n_fig >= 8:
        break
    if any(math.hypot(x - px, z - pz) < 18 for px, pz in placed + fplaced):
        continue
    if fig(x, z, seed=x * 3 + z, big=(n_fig % 3 == 0)):
        fplaced.append((x, z))
# the vine ladder up the fairy tree's trunk (south side)
if FAIRY_TREE:
    tx, tz, top = FAIRY_TREE
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 1):
                S(tx + dx, top + 1, tz + dz, "minecraft:mangrove_leaves[persistent=true]")
    for y in range(G + 1, top + 2):
        b = area.get(tx, y, tz + 1)
        if b in (None, "minecraft:air") or b.startswith("minecraft:mangrove_leaves"):
            S(tx, y, tz + 1, "minecraft:vine[north=true]")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            S(tx + dx, top + 2, tz + dz, "minecraft:air")
            S(tx + dx, top + 3, tz + dz, "minecraft:air")
    SOUL_TREE = (tx, top + 2, tz)
else:
    SOUL_TREE = (ISX, G + 10, ISZ)

# ---------------------------------------------------------------- plants: lily pads, reeds, dripleaf, grass, ferns, bushes
lily = reeds = drip = 0
for (x, z), top in T.items():
    if (x, z) in occ:
        continue
    h = hash01(x, 30, z, 51)
    if (x, z) in WET:
        if area.get(x, G, z) in (None, "minecraft:air"):
            near = LAKE if (x, z) in LAKE else WET
            if h < (0.07 if (x, z) in LAKE else 0.12):
                S(x, G, z, "minecraft:lily_pad")
                lily += 1
        continue
    if area.get(x, top + 1, z) not in (None, "minecraft:air"):
        continue
    surf = area.get(x, top, z) or ""
    if top == WATER_TOP and (x, z) in BANK and surf.endswith(("mud", "moss_block")):
        if h < 0.5:
            for k in range(1 + int(hash01(x, 31, z, 5) * 3)):
                S(x, top + 1 + k, z, "minecraft:sugar_cane[age=0]")
            reeds += 1
        elif h < 0.58:
            S(x, top + 1, z, "minecraft:fern")
        continue
    if not surf.endswith(("grass_block", "moss_block", "mud", "podzol", "rooted_dirt", "coarse_dirt", "packed_mud")):
        continue
    if (x, z) in path_cols:
        continue
    r = hash01(x, 32, z, 52)
    if surf.endswith("mud") or surf.endswith("packed_mud"):
        if r < 0.05:
            S(x, top + 1, z, "minecraft:brown_mushroom")
        elif r < 0.09:
            S(x, top + 1, z, "minecraft:fern")
        continue
    if r < 0.16:
        S(x, top + 1, z, "minecraft:short_grass")
    elif r < 0.22:
        S(x, top + 1, z, "minecraft:fern")
    elif r < 0.245:
        S(x, top + 1, z, "minecraft:azalea")
    elif r < 0.265:
        S(x, top + 1, z, "minecraft:flowering_azalea")
    elif r < 0.285 and surf.endswith("moss_block"):
        f = ["north", "south", "east", "west"][int(hash01(x, 33, z, 4) * 4)]
        S(x, top + 1, z, f"minecraft:small_dripleaf[facing={f},half=lower,waterlogged=false]")
        S(x, top + 2, z, f"minecraft:small_dripleaf[facing={f},half=upper,waterlogged=false]")
        drip += 1
    elif r < 0.30:
        f = ["north", "south", "east", "west"][int(hash01(x, 33, z, 4) * 4)]
        S(x, top + 1, z, f"minecraft:big_dripleaf[facing={f},tilt=none,waterlogged=false]")
        drip += 1
    elif r < 0.315:
        S(x, top + 1, z, "minecraft:blue_orchid")
    elif r < 0.325:
        S(x, top + 1, z, "minecraft:red_mushroom")
    elif r < 0.345:
        S(x, top + 1, z, "minecraft:moss_carpet")
    elif r < 0.352 and area.top.get((x, z), 0) >= G and hash01(x, 34, z, 9) < 0.5:
        S(x, top + 1, z, "minecraft:firefly_bush")
# the lake's bank mud flats and dripleaf pads in shallow water near the shore
for (x, z) in LAKE:
    if (x, z) in occ or T[(x, z)] < WATER_TOP - 2:
        continue
    if area.get(x, G, z) in (None, "minecraft:air") and hash01(x, 35, z, 7) < 0.02:
        f = ["north", "south", "east", "west"][int(hash01(x, 36, z, 4) * 4)]
        S(x, G, z, f"minecraft:big_dripleaf[facing={f},tilt=none,waterlogged=false]")
        S(x, G - 1, z, "minecraft:water") if False else None
# lamps (soul lanterns) along the main paths
lamps = 0
for name, (pts, w) in PATHS.items():
    if name in ("pier",):
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
            lx, lz = int(round(px + nx * (w / 2 + 1.5) * side)), int(round(pz + nz * (w / 2 + 1.5) * side))
            if (lx, lz) in T and (lx, lz) not in WET and (lx, lz) not in path_cols and (lx, lz) not in occ and \
                    area.get(lx, T[(lx, lz)] + 1, lz) in (None, "minecraft:air"):
                post_lantern(lx, lz, T[(lx, lz)] + 1, soul=(lamps % 3 == 0))
                reserve(lx, lz, 1)
                lamps += 1

# ---------------------------------------------------------------- Fairy Souls, spots
area.fairy_soul(0, 66, 55)                                  # on the hidden landing under the pier
area.fairy_soul(*SOUL_TREE)                                 # the top of the tall mangrove on the north-east islet
area.fairy_soul(*SOUL_CAVE)                                 # the Squid Cave's far ledge
area.fairy_soul(*SOUL_SH)                                   # inside the sunken ruin in the Shallows
area.fairy_soul(*SOUL_TOWER)                                # the top of the broken tower in the South Reaches
area.fairy_soul(*SOUL_LOGS)                                 # inside the hollow log stack at SwampCut Inc.

# ---------------------------------------------------------------- zones and mobs
Y0, Y1 = 8, 150


def mob(kind, name, level, health, damage, xp, coins, mx_, every, a, b, night=False, indoors=False):
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b)}
    if night:
        spec["time"] = "night"
    if indoors:
        spec["indoors"] = True
    return spec


def zbox(x0, z0, x1, z1):
    return ((x0, Y0, z0), (x1, Y1, z1))


WET_W = ((-146, G - 3, 14), (-80, G + 12, 128))
WET_E = ((80, G - 3, 14), (146, G + 12, 98))
bogged_w = mob("minecraft:bogged", "Bogged", 40, 10000, 600, 30, 25, 8, 5, *WET_W)
phan_w = mob("minecraft:phantom", "Phanpyre", 30, 5000, 400, 20, 15, 4, 8, (-146, G - 3, 14), (-80, G + 25, 128), night=True)
bogged_e = mob("minecraft:bogged", "Bogged", 40, 10000, 600, 30, 25, 8, 5, *WET_E)
phan_e = mob("minecraft:phantom", "Phanpyre", 30, 5000, 400, 20, 15, 4, 8, (80, G - 3, 14), (146, G + 25, 98), night=True)
lumi = mob("minecraft:glow_squid", "Lumisquid", 10, 500, 0, 5, 5, 6, 6, (cx_ - 12, BED_C + 1, cz_ - 5), (cx_ + 12, LEDGE, cz_ + 5), indoors=True)  # in the pool: water mobs spawn in water

area.zone("Murkwater Loch", box=zbox(-75, 0, 75, 87), blocks={}, mobs=[])
area.zone("Murkwater Loch", box=zbox(-75, 88, -63, 124), blocks={}, mobs=[])
area.zone("Murkwater Shallows", box=zbox(-62, 88, -14, 124), blocks={}, mobs=[])
area.zone("Murkwater Outpost", box=zbox(-13, 88, 75, 159), blocks={}, mobs=[])
area.zone("South Wetlands", box=zbox(-150, 0, -76, 131), blocks={}, mobs=[bogged_w, phan_w])
area.zone("South Wetlands", box=zbox(-150, 132, -119, 159), blocks={}, mobs=[])
area.zone("South Wetlands", box=zbox(-75, 125, -14, 159), blocks={}, mobs=[])
area.zone("Squid Cave", box=zbox(-118, 132, -76, 159), blocks={}, mobs=[lumi])
area.zone("South Reaches", box=zbox(76, 0, 149, 99), blocks={}, mobs=[bogged_e, phan_e])
area.zone("South Reaches", box=zbox(76, 100, 99, 159), blocks={}, mobs=[])
area.zone("SwampCut Inc.", box=zbox(100, 100, 149, 159), blocks={}, mobs=[])
# Lushlilac in the Wetlands and the Reaches
print("lushlilacs", lushlilacs(area, T, 30, 7, lambda x, z: x < -75 or x > 75))
area.save()

print("mangroves", n_mangrove, "figs", n_fig, "pickles", n_pickles, "lily", lily, "reeds", reeds, "dripleaf", drip, "lamps", lamps)
for t in trees:
    print(t)
print("pier end z", PIER_END, "pavilion", PAV, "Nemo dock zs", zs, "Outpost zsA", zsA, "shallows floor", SFLOOR, "cave pillars", cave_pillars)
print("souls", area.spots["fairy_souls"])
for k, v in area.spots["npcs"].items():
    print(k, v["pos"])
