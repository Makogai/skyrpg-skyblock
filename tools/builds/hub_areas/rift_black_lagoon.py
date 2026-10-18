#!/usr/bin/env python3
"""The Rift, Black Lagoon (x -200..-61, z -30..59): a dark swamp of water channels between muddy islets, giant
mushrooms, the Lagoon Hut on stilts, the Lagoon Cave (a hill with a parkour up to a ledge) and, under the lagoon, the
Leeches Lair (a cavern with a boss arena, reached by a sloped tunnel; a waterfall with a nook behind it).

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_black_lagoon.py

The Wyld Woods lie along z = 60 for x -160..-61: the surface keeps y 68 near z = 59 there and the main path crosses at
x -105. Every other side is the Rift's outer edge (jagged cliffs, hanging underside, a few floating shards).
"""
import math

from kit import hash01, noise
from rift_lib import new_area, land, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("black_lagoon", version=1)
area.shell = 6
S = area.set
G = GROUND                      # surface block y of the islets (players walk at 69)
WATER_TOP = G - 1               # channel water surface block (one below the islets)
BED = G - 6                     # channel bed block y (62)
X0, X1, Z0, Z1 = -200, -61, -30, 59
CROSS_X = CROSSINGS[("wyld_woods", "black_lagoon")][2]      # -105

LAIR_FLOOR = 30                 # cavern floor block y (players stand at 31)
LCX, LCZ = -118, 27             # cavern centre
HUT = (-119, 28)                # hut centre
GROVE = (-176, 38)              # giant mushroom grove centre
HILL = (-176, -3)              # the Lagoon Cave's hill
RUNES = (-132, -16)             # the spinning runes islet


# ---------------------------------------------------------------- helpers
def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def segd(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    l2 = dx * dx + dz * dz
    t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / l2))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


def polyd(x, z, pts):
    return min(segd(x, z, a[0], a[1], b[0], b[1]) for a, b in zip(pts, pts[1:]))


def ell(x, z, cx, cz, rx, rz):
    return ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2


def jag(a, b, s):
    return 5 * noise(a, b, s, 1.2) + 2.5 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 1.2 * (hash01(int(a), 2, int(b), s) - 0.5)


def edge(x, z):
    """Distance in blocks to the Rift's outer edge (negative = void)."""
    dw = x - (-195 + jag(z, 0, 1))
    de = (-66 + jag(z, 9, 2)) - x
    dn = z - (-25 + jag(x, 5, 3))
    ds = (55 + jag(x, 7, 4)) - z if x < -160 else 99
    return min(dw, de, dn, ds)


# ---------------------------------------------------------------- the plan: land, water, paths
STRONG = [(-100, -31), (-103, -14), (-92, -4), (-75, 6), (-62, 10)]            # a strong-current channel (north-east)
WEAK_DRY = [(-150, -4, 8), (-121, -13, 7), (-84, -19, 7), (-70, 24, 6), (-160, 24, 6), (-92, 6, 6), (-145, 32, 6)]


def core_dry(x, z):
    if z >= 50 and x >= -160:
        return True
    if ell(x, z, *GROVE, 26, 16) < 1 or ell(x, z, *HILL, 21, 21) < 1:
        return True
    if -97 <= x <= -69 and 32 <= z <= 46:
        return True
    return math.hypot(x - RUNES[0], z - RUNES[1]) < 7.5


def flat(x, z):
    """Columns kept at exactly y 68 (NPC spots, the lair entrance, the rune circle, the hut's surroundings)."""
    return ((-97 <= x <= -69 and 32 <= z <= 46) or math.hypot(x - RUNES[0], z - RUNES[1]) < 9
            or math.hypot(x - (-176), z - 36) < 8 or math.hypot(x - (-168), z - 41) < 5 or ell(x, z, *HUT, 18, 14) < 1)


def forced_wet(x, z):
    if polyd(x, z, STRONG) < 3.6 + 0.8 * noise(x, z, 5, 2):
        return True
    if ell(x, z, *HUT, 14, 11.5) < 1:
        return True
    r = math.hypot(x - RUNES[0], z - RUNES[1])
    return 7.5 <= r < 14.5


def noise_wet(x, z):
    return (abs(noise(x, z, 7, 1.5) - 0.02) < 0.085 or abs(noise(x, z, 19, 1.1) - 0.18) < 0.07
            or abs(noise(x, z, 23, 1.3) + 0.3) < 0.07 or noise(x, z, 31, 1.2) > 0.6)


PATHS = {
    "crossing": ([(CROSS_X, 59), (CROSS_X, 52)], 5),
    "main": ([(CROSS_X, 52), (-104, 46), (-107, 40), (-112, 35), (-113, 29), (-112, 28)], 3),
    "lair": ([(-104, 46), (-95, 46), (-86, 43), (-79, 40), (-76, 39)], 3),
    "cave": ([(-122, 21), (-130, 18), (-140, 16), (-150, 13), (-158, 10), (-168, 12), (-176, 10), (-176, 7)], 3),
    "grove": ([(-106, 51), (-125, 50), (-145, 48), (-160, 44), (-170, 40), (-176, 38)], 3),
}
PATHS_WET = {"crossing": False}
path_cols = {}                   # (x, z) -> path name
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

T = {}          # (x, z) -> top block y of the column (land) or BED (water)
WET = set()
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z)
        if e < 0:
            continue
        wet = False
        if e >= 3 and PATHS_WET.get(path_cols.get((x, z)), True):
            if core_dry(x, z):
                wet = False
            elif forced_wet(x, z):
                wet = True
            elif any(math.hypot(x - cx, z - cz) < r for cx, cz, r in WEAK_DRY):
                wet = False
            else:
                wet = noise_wet(x, z)
        if wet:
            WET.add((x, z))
            T[(x, z)] = BED
        else:
            top = G
            if (x, z) not in path_cols and not (z >= 50 and x >= -160) and not flat(x, z):
                b = noise(x, z, 21, 2.2)
                top = G + (2 if b > 0.62 else 1 if b > 0.4 else 0)
            hr = math.hypot(x - HILL[0], z - HILL[1])
            if hr < 20.5 and (x, z) not in path_cols:
                top = max(top, int(G + 22 * (1 - (hr / 20.5) ** 2) + 1.5 * noise(x, z, 8, 3)))
            T[(x, z)] = top

# water columns need a rim: any water column touching the void edge or beside a non-planned cell turns to land
for (x, z) in list(WET):
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if (x + dx, z + dz) not in T:
            WET.discard((x, z))
            T[(x, z)] = G
            break

BANK = {c for c in T if c not in WET and any((c[0] + dx, c[1] + dz) in WET for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}

# ---------------------------------------------------------------- the ground
SURF_PATH = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3, "packed_mud": 1})
UNDER = area.mix({"mud": 6, "packed_mud": 2, "rooted_dirt": 1, "dirt": 1})
BODY = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1, "andesite": 1})
UNDERSIDE = area.mix({"tuff": 6, "mud": 4, "packed_mud": 2, "deepslate": 3, "calcite": 1})


def surface_block(x, y, z):
    if (x, z) in BANK:
        return "minecraft:moss_block" if hash01(x, 1, z, 3) < 0.45 else "minecraft:mud"
    n = noise(x, z, 41, 2.6)
    h = hash01(x, 2, z, 9)
    if n > 0.25:
        return "minecraft:grass_block" if h < 0.8 else "minecraft:rooted_dirt"
    if n < -0.3:
        return "minecraft:moss_block" if h < 0.7 else "minecraft:mud"
    if h < 0.12:
        return "minecraft:packed_mud"
    if h < 0.2:
        return "minecraft:rooted_dirt"
    if h < 0.25:
        return "minecraft:podzol"
    return "minecraft:mud"


def under_depth(x, z, e):
    t = sm(e / 55.0)
    n = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * n) + 4 * n * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    if -145 <= x <= -92 and 3 <= z <= 49:
        d = max(d, 16 + 3 * hash01(x, 6, z, 8))
    return int(min(d, 29))


for (x, z), top in T.items():
    e = edge(x, z)
    if (x, z) in WET:
        area.set(x, BED, z, "minecraft:mud")
        for y in range(BED - 1, BED - 4, -1):
            S(x, y, z, UNDER(x, y, z))
        for y in range(BED - 4, LAND_BOTTOM - 1, -1):
            S(x, y, z, BODY(x, y, z))
        for y in range(BED + 1, WATER_TOP + 1):
            S(x, y, z, "minecraft:water")
        h = hash01(x, 3, z, 2)
        if h < 0.11:
            S(x, BED + 1, z, "minecraft:seagrass")
    else:
        if (x, z) in path_cols:
            surf = SURF_PATH
        elif e < 2.5:
            surf = lambda x, y, z: "minecraft:mud" if hash01(x, 8, z, 1) < 0.5 else "minecraft:packed_mud"
        else:
            surf = surface_block
        land(area, x, z, top, surf, UNDER, BODY)
        area.top[(x, z)] = top
    # the hanging underside under everything (thin at the outer edge, deep inside)
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
    # shards: crying obsidian / amethyst spikes hanging from the broken underside
    if e < 30 and hash01(x, 10, z, 13) < 0.012:
        ln = 3 + int(hash01(x, 11, z, 14) * 8)
        yy = LAND_BOTTOM - d
        for i in range(ln):
            S(x, yy - 1 - i, z, "minecraft:crying_obsidian" if hash01(x, i, z, 15) < 0.4 else "minecraft:amethyst_block")
        S(x, yy - 1 - ln, z, "minecraft:amethyst_cluster[facing=down]")

# floating shards beyond the cliffs (deliberate)
for i in range(40):
    x = int(X0 + 3 + hash01(i, 0, 0, 77) * (X1 - X0 - 6))
    z = int(Z0 + 3 + hash01(i, 1, 0, 77) * (Z1 - Z0 - 6))
    if edge(x, z) > -3 or edge(x, z) < -16 or (z > 52 and x >= -165) or i > 14:
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

occ = set()     # columns kept free of trees, mushrooms and plants


def reserve(cx, cz, r):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            occ.add((cx + dx, cz + dz))


for (x, z) in path_cols:
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            occ.add((x + dx, z + dz))


# ---------------------------------------------------------------- lamps and boardwalks
def lamp(x, y, z):
    """A purple lantern: a post with a sea lantern wrapped in purple glass."""
    for h in range(0, 3):
        S(x, y + h, z, "minecraft:mangrove_fence")
    S(x, y + 3, z, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, y + 3, z + dz, "minecraft:purple_stained_glass")
    S(x, y + 4, z, "minecraft:purple_stained_glass")
    S(x, y + 5, z, "minecraft:end_rod[facing=up]")


DECK = area.mix({"mangrove_planks": 6, "dark_oak_planks": 3, "spruce_planks": 1})
boards = 0
for (x, z), name in sorted(path_cols.items()):
    if (x, z) not in T:
        continue
    if (x, z) in WET:
        S(x, G, z, DECK(x, G, z))
        S(x, G - 1, z, "minecraft:water")   # keep the channel's water under the deck
        boards += 1
        # rails on the outer sides, posts into the bed, lamps
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n in T and n not in path_cols and name != "crossing":
                if n in WET:
                    S(x, G + 1, z, "minecraft:mangrove_fence")
                    break
        if (x + z) % 5 == 0:
            for y in range(BED + 1, G):
                S(x, y, z, "minecraft:mangrove_log[axis=y]")
    elif name != "crossing":
        pass

# lamps along the paths every ~11 blocks, set a block aside
lamps = 0
for name, (pts, w) in PATHS.items():
    total = 0.0
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        length = math.hypot(x2 - x1, z2 - z1)
        n = int(length)
        for i in range(n):
            total += 1
            if total % 11 != 0:
                continue
            px, pz = x1 + (x2 - x1) * i / max(1, n), z1 + (z2 - z1) * i / max(1, n)
            nx, nz = -(z2 - z1) / length, (x2 - x1) / length
            side = 1 if (lamps % 2) else -1
            lx, lz = int(round(px + nx * (w / 2 + 1.5) * side)), int(round(pz + nz * (w / 2 + 1.5) * side))
            if (lx, lz) in T and (lx, lz) not in WET and (lx, lz) not in path_cols:
                lamp(lx, T[(lx, lz)] + 1, lz)
                reserve(lx, lz, 1)
                lamps += 1

# ---------------------------------------------------------------- the Lagoon Hut (on stilts over its pond)
HX0, HX1, HZ0, HZ1 = -125, -113, 22, 34       # deck
reserve(-119, 28, 9)
for x in range(HX0, HX1 + 1):
    for z in range(HZ0, HZ1 + 1):
        S(x, G, z, "minecraft:mangrove_planks" if (x + z) % 2 else "minecraft:dark_oak_planks")
        for y in range(G + 1, G + 12):
            area.clear(x, y, z)
for x, z in ((HX0, HZ0), (HX1, HZ0), (HX0, HZ1), (HX1, HZ1), (HX0, 28), (HX1, 22), (-119, HZ0), (-119, HZ1), (HX1, HZ1 - 4)):
    for y in range(BED + 1, G):
        S(x, y, z, "minecraft:mangrove_log[axis=y]")
    S(x, G, z, "minecraft:stripped_mangrove_log[axis=y]")
    for y in range(BED + 1, WATER_TOP + 1):
        pass
# walls x -123..-115, z 24..32 (interior air); deck is a veranda around
WX0, WX1, WZ0, WZ1 = -124, -114, 23, 33
for x in range(WX0, WX1 + 1):
    for z in range(WZ0, WZ1 + 1):
        wall = x in (WX0, WX1) or z in (WZ0, WZ1)
        for y in range(G + 1, G + 6):
            if wall:
                S(x, y, z, "minecraft:mangrove_planks" if y != G + 1 else "minecraft:stripped_mangrove_log[axis=y]"
                  if x in (WX0, WX1) and z in (WZ0, WZ1) else "minecraft:mangrove_planks")
            else:
                area.clear(x, y, z)
        S(x, G, z, "minecraft:spruce_planks" if not wall else "minecraft:dark_oak_planks")
for x, z in ((WX0, WZ0), (WX1, WZ0), (WX0, WZ1), (WX1, WZ1)):
    for y in range(G + 1, G + 5):
        S(x, y, z, "minecraft:stripped_mangrove_log[axis=y]")
# windows
for (x, z) in ((WX0, 27), (WX0, 29), (WX1, 25), (WX1, 31), (-120, WZ0), (-118, WZ0), (-120, WZ1), (-118, WZ1), (-122, WZ1), (-116, WZ1)):
    S(x, G + 2, z, "minecraft:glass_pane")
    S(x, G + 3, z, "minecraft:glass_pane")
# door (east, towards the path)
for y in (G + 1, G + 2):
    area.clear(WX1, y, 28)
S(WX1, G + 3, 28, "minecraft:mangrove_planks")
# gabled roof along x, ridge at z 28
for dz in range(-6, 7):
    z = 28 + dz
    ry = G + 5 + (6 - abs(dz)) - 1 if abs(dz) > 0 else G + 5 + 5
    for x in range(WX0 - 1, WX1 + 2):
        if abs(dz) > 0:
            facing = "north" if dz > 0 else "south"
            S(x, ry, z, f"minecraft:mangrove_stairs[facing={facing},half=bottom]")
            # gable ends close the triangle under the roof
            if x in (WX0, WX1) and abs(dz) <= 5:
                for y in range(G + 5, ry):
                    S(x, y, z, "minecraft:mangrove_planks")
        else:
            S(x, ry, z, "minecraft:mangrove_planks")
            if x in (WX0, WX1):
                for y in range(G + 5, ry):
                    S(x, y, z, "minecraft:mangrove_planks")
for x in range(WX0 - 1, WX1 + 2):
    S(x, G + 10, 28, "minecraft:mangrove_slab[type=bottom]")
# ceiling hole fix (interior stays 4 tall under y G+5 beams)
for x in range(WX0 + 1, WX1):
    for z in range(WZ0 + 1, WZ1):
        S(x, G + 5, z, "minecraft:mangrove_planks")
for (x, z) in ((-123, 25), (-115, 25), (-123, 31), (-115, 31)):
    S(x, G + 4, z, "minecraft:lantern[hanging=true]")
# furnishings
for x in range(-123, -118):
    S(x, G + 1, 24, "minecraft:bookshelf")
S(-122, G + 1, 31, "minecraft:red_bed[facing=west,part=foot]")
S(-123, G + 1, 31, "minecraft:red_bed[facing=west,part=head]")
S(-123, G + 1, 29, "minecraft:crafting_table")
S(-123, G + 1, 28, "minecraft:barrel[facing=up]")
S(-117, G + 1, 24, "minecraft:cauldron")
S(-115, G + 1, 24, "minecraft:chest[facing=south]")
S(-119, G + 1, 28, "minecraft:mangrove_fence")
S(-119, G + 2, 28, "minecraft:mangrove_pressure_plate")
for (x, z) in ((-118, 27), (-120, 29)):
    S(x, G + 1, z, "minecraft:mangrove_stairs[facing=east,half=bottom]")
S(-122, G + 1, 26, "minecraft:flower_pot")
S(-116, G + 1, 31, "minecraft:potted_brown_mushroom")
# the veranda: rails, a lamp and the boat Reed is building on the west dock
for x in range(HX0, HX1 + 1):
    for z in (HZ0, HZ1):
        if not (WX0 <= x <= WX1):
            S(x, G + 1, z, "minecraft:mangrove_fence")
for z in range(HZ0, HZ1 + 1):
    if not (WZ0 <= z <= WZ1) or True:
        S(HX0, G + 1, z, "minecraft:mangrove_fence") if z in (HZ0, HZ1) else None
lamp(HX1, G + 1, HZ0)
lamp(HX1, G + 1, HZ1)
for x in (-112, -111):
    for z in range(29, 34):
        S(x, G, z, "minecraft:mangrove_planks")
    for y in range(BED + 1, G):
        S(x, y, 33, "minecraft:mangrove_log[axis=y]") if x == -111 else None
for z in range(29, 34):
    S(-110, G, z, "minecraft:air") if False else None
S(-111, G + 1, 29, "minecraft:mangrove_fence")
S(-111, G + 1, 33, "minecraft:mangrove_fence")
S(-112, G + 1, 33, "minecraft:mangrove_fence")
# the unfinished boat (west of the hut, on a dock)
for x in range(-131, -125):
    for z in range(26, 31):
        S(x, G, z, "minecraft:mangrove_planks")
for x in range(-131, -126):
    for z in (26, 30):
        S(x, G + 1, z, "minecraft:oak_planks")
        S(x, G + 2, z, "minecraft:oak_planks" if x in (-131, -127) else "minecraft:air")
    S(x, G + 1, 28, "minecraft:oak_slab[type=bottom]")
S(-131, G + 1, 27, "minecraft:oak_planks")
S(-131, G + 1, 28, "minecraft:oak_planks")
S(-131, G + 1, 29, "minecraft:oak_planks")
for x in (-128, -129):
    S(x, G + 1, 28, "minecraft:air")
for y in range(BED + 1, G):
    for x, z in ((-131, 26), (-131, 30), (-126, 26), (-126, 30)):
        S(x, y, z, "minecraft:mangrove_log[axis=y]")
S(-129, G + 1, 31, "minecraft:barrel[facing=up]")

# ---------------------------------------------------------------- giant mushrooms
SIDES = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
mushrooms = []


def mushroom(a, b, h, kind, R, big=False):
    """A giant mushroom: stem(s) at the ends of the segment a-b (a == b for a single one), cap of radius R over them.
    A long segment makes an arch or a shelter."""
    (ax, az), (bx, bz) = a, b
    ys = {}
    for end in (a, b):
        t = T.get(end)
        if t is None or end in WET:
            return False
        ys[end] = t
    base = max(ys.values())
    top = base + h
    stems = set()
    for (sx, sz) in {a, b}:
        r = 1 if big else 0
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                stems.add((sx + dx, sz + dz))
    cap = set()
    Hc = R * (0.85 if kind == "red" else 0.45)
    for x in range(min(ax, bx) - R - 1, max(ax, bx) + R + 2):
        for z in range(min(az, bz) - R - 1, max(az, bz) + R + 2):
            d = segd(x, z, ax, az, bx, bz)
            if d > R + 0.5:
                continue
            for dy in range(-3, int(Hc) + 2):
                if kind == "red":
                    if dy >= 0:
                        ok = (d / R) ** 2 + (dy / (Hc + 0.5)) ** 2 <= 1.0
                    else:
                        ok = R - 1.6 <= d <= R + 0.3 and dy >= -2
                else:
                    if dy >= 0:
                        ok = (d / R) ** 2 + (dy / (Hc + 0.5)) ** 2 <= 1.0 and dy <= 1
                    else:
                        ok = R - 1.3 <= d <= R + 0.3 and dy >= -1
                if ok:
                    cap.add((x, top + dy, z))
    for y in range(base + 1, top + 1):
        for (x, z) in stems:
            S(x, y, z, "minecraft:mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]")
    name = "minecraft:red_mushroom_block" if kind == "red" else "minecraft:brown_mushroom_block"
    for (x, y, z) in cap:
        faces = {}
        shell = False
        for k, (dx, dy, dz) in SIDES.items():
            out = (x + dx, y + dy, z + dz) not in cap
            faces[k] = out and (k != "down")
            shell |= out
        if not shell:
            continue
        # stems pass up into the cap's middle: no underside face over them
        S(x, y, z, name + "[" + ",".join(f"{k}={'true' if v else 'false'}" for k, v in faces.items()) + "]")
    for end in (a, b):
        reserve(end[0], end[1], 3)
    mushrooms.append((a, b, h, kind, R))
    return True


def add_mushrooms():
    # the grove: a jittered grid inside the ellipse, plus designed arches and shelters
    arches = [((-186, 42), (-177, 46), 11, "red", 5), ((-170, 30), (-162, 33), 9, "brown", 5),
              ((-160, 50), (-160, 42), 10, "brown", 4), ((-190, 30), (-190, 23), 12, "red", 5)]
    for a, b, h, k, R in arches:
        mushroom(a, b, h, k, R, big=True)
    step = 8
    i = 0
    for gx in range(-198, -148, step):
        for gz in range(22, 56, step):
            i += 1
            x = gx + int(hash01(gx, 1, gz, 71) * 6) - 3
            z = gz + int(hash01(gx, 2, gz, 71) * 6) - 3
            if ell(x, z, *GROVE, 30, 20) > 1 or (x, z) in occ or (x, z) not in T or (x, z) in WET:
                continue
            if any(math.hypot(x - c[0][0], z - c[0][1]) < 8 for c in mushrooms):
                continue
            h = 8 + int(hash01(gx, 3, gz, 71) * 8)
            kind = "red" if hash01(gx, 4, gz, 71) < 0.5 else "brown"
            R = 4 + int(hash01(gx, 5, gz, 71) * 2.6)
            mushroom((x, z), (x, z), h, kind, R, big=(R >= 6))
    # scattered across the lagoon: a jittered grid over the islets
    for gx in range(-196, -62, 15):
        for gz in range(-24, 52, 15):
            x = gx + int(hash01(gx, 6, gz, 72) * 9) - 4
            z = gz + int(hash01(gx, 7, gz, 72) * 9) - 4
            if (x, z) not in T or (x, z) in WET or (x, z) in occ or edge(x, z) < 8:
                continue
            if math.hypot(x - HILL[0], z - HILL[1]) < 24 or ell(x, z, *HUT, 20, 16) < 1 or ell(x, z, *GROVE, 30, 20) < 1:
                continue
            if hash01(gx, 8, gz, 72) < 0.35:
                continue
            if any(math.hypot(x - c[0][0], z - c[0][1]) < 10 for c in mushrooms):
                continue
            kind = "red" if hash01(gx, 9, gz, 72) < 0.5 else "brown"
            R = 4 + int(hash01(gx, 10, gz, 72) * 2.6)
            mushroom((x, z), (x, z), 8 + int(hash01(gx, 11, gz, 72) * 8), kind, R, big=(R >= 6))
    # a mushroom shelter and an arch out on the islets
    for a, b, h, k, R in [((-112, -6), (-104, -6), 10, "brown", 4), ((-96, 18), (-96, 26), 11, "red", 4)]:
        if all(c in T and c not in WET for c in (a, b)):
            mushroom(a, b, h, k, R, big=True)


# ---------------------------------------------------------------- trees (mangrove, dark oak, dead)
def mangrove(x, z, h=7, seed=0):
    base = T[(x, z)]
    # the flared root base on the ground and into nearby shallows
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        if hash01(x + dx, 1, z + dz, seed) < 0.55:
            c = (x + dx, z + dz)
            if c not in T or c in path_cols:
                continue
            ty = T[c]
            if c in WET:
                for y in range(ty + 1, G + 1):
                    S(c[0], y, c[1], "minecraft:mangrove_roots[waterlogged=true]" if y <= WATER_TOP else "minecraft:mangrove_roots")
            else:
                S(c[0], ty + 1, c[1], "minecraft:muddy_mangrove_roots[axis=y]" if hash01(c[0], 2, c[1], seed) < 0.5
                  else "minecraft:mangrove_roots")
    for y in range(base + 1, base + 1 + h):
        S(x, y, z, "minecraft:mangrove_log[axis=y]")
    top = base + h
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            for dy in range(-1, 4):
                r2 = dx * dx + dz * dz + (dy - 1) ** 2 * 1.6
                if r2 <= 13 and hash01(x + dx, top + dy, z + dz, seed + 3) > 0.12:
                    if area.get(x + dx, top + dy, z + dz) in (None, "minecraft:air"):
                        S(x + dx, top + dy, z + dz, "minecraft:mangrove_leaves[persistent=true]")
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if hash01(x + dx, 99, z + dz, seed + 5) < 0.22:
                for y in range(top - 1, top - 3, -1):
                    if area.get(x + dx, y, z + dz) == "minecraft:mangrove_leaves[persistent=true]" and area.get(x + dx, y - 1, z + dz) in (None, "minecraft:air"):
                        S(x + dx, y - 1, z + dz, "minecraft:mangrove_propagule[age=3,hanging=true,stage=0]")
                        break
    reserve(x, z, 2)


def add_trees():
    for x in range(X0 + 4, X1 - 3, 1):
        for z in range(Z0 + 4, Z1 - 3, 1):
            if (x, z) in occ or (x, z) not in T or (x, z) in WET:
                continue
            e = edge(x, z)
            if e < 4 or z >= 50 and x >= -160 and abs(x - CROSS_X) < 8:
                continue
            h = hash01(x, 0, z, 91)
            if h > 0.02:
                continue
            if ell(x, z, *GROVE, 22, 14) < 1 and h > 0.006:
                continue
            if math.hypot(x - HILL[0], z - HILL[1]) < 21:
                continue
            k = hash01(x, 1, z, 91)
            if any(math.hypot(x - m[0][0], z - m[0][1]) < m[4] + 1 for m in mushrooms):
                continue
            if k < 0.45:
                mangrove(x, z, 6 + int(hash01(x, 2, z, 91) * 4), seed=x * 7 + z)
            elif k < 0.7:
                area.dead_tree(x, T[(x, z)] + 1, z, height=6 + int(hash01(x, 3, z, 91) * 5), log="mangrove_log", seed=x + z)
                reserve(x, z, 1)
            else:
                area.oak(x, T[(x, z)] + 1, z, height=6 + int(hash01(x, 3, z, 91) * 3), radius=3, log="dark_oak_log",
                         leaves="dark_oak_leaves", seed=x + z)
                reserve(x, z, 2)


# ---------------------------------------------------------------- the Lagoon Cave (parkour inside the hill)
CCX, CCZ = HILL[0], HILL[1] - 4          # chamber centre (-176, -14)
CRX, CRZ, CH = 12, 11, 17
for x in range(CCX - CRX, CCX + CRX + 1):
    for z in range(CCZ - CRZ, CCZ + CRZ + 1):
        q = ((x - CCX) / CRX) ** 2 + ((z - CCZ) / CRZ) ** 2
        if q >= 1:
            continue
        hgt = int(CH * math.sqrt(1 - q))
        # floor: pit with water except the landing by the entrance
        landing = abs(x - CCX) <= 3 and z >= CCZ + 5
        for y in range(G + 1, G + 1 + hgt + 1):
            area.clear(x, y, z)
        if not landing:
            for y in range(G - 7, G):
                area.clear(x, y, z)
            S(x, G - 8, z, "minecraft:mud")
            for y in range(G - 7, G - 1):
                S(x, y, z, "minecraft:water")
        else:
            S(x, G, z, "minecraft:packed_mud" if hash01(x, 1, z, 5) < 0.3 else "minecraft:mud_bricks")
        # walls and ceiling dressing
        if hgt >= 4 and hash01(x, 7, z, 3) < 0.05:
            S(x, G + 1 + hgt, z, "minecraft:glow_lichen[up=true]") if False else None
for x in range(CCX - 2, CCX + 3):
    for z in range(HILL[1] + 1, HILL[1] + 20):
        for y in range(G + 1, G + 5):
            area.clear(x, y, z)
        t = T.get((x, z))
        if t is not None and (x, z) not in WET:
            S(x, G, z, "minecraft:mud_bricks" if hash01(x, 1, z, 4) < 0.6 else "minecraft:packed_mud")
# the entrance's frame of stone and dripstone
for z in (HILL[1] + 8, HILL[1] + 9):
    for dy in range(1, 6):
        S(CCX - 3, G + dy, z, "minecraft:mud_brick_wall" if dy < 5 else "minecraft:mud_bricks")
        S(CCX + 3, G + dy, z, "minecraft:mud_brick_wall" if dy < 5 else "minecraft:mud_bricks")
for x in range(CCX - 3, CCX + 4):
    S(x, G + 6, HILL[1] + 8, "minecraft:mud_bricks")
    S(x, G + 6, HILL[1] + 9, "minecraft:mud_brick_slab[type=bottom]")
for x in (CCX - 2, CCX + 2):
    S(x, G + 5, HILL[1] + 8, "minecraft:lantern[hanging=true]")
    S(x, G + 5, HILL[1] + 9, "minecraft:air")
# entrance corridor leads into the chamber
for z in range(CCZ + 5, HILL[1] + 9):
    for x in range(CCX - 2, CCX + 3):
        S(x, G, z, "minecraft:mud_bricks" if hash01(x, 1, z, 4) < 0.7 else "minecraft:packed_mud")
# parkour platforms (2x2) spiralling up the wall, then the ledge
PARK = []
for k in range(10):
    ang = math.radians(125 + 30 * k)
    px = int(round(CCX + 7.5 * math.cos(ang)))
    pz = int(round(CCZ + 7.5 * math.sin(ang)))
    fy = G + (k + 1) // 2
    PARK.append((px, pz, fy))
for (px, pz, fy) in PARK:
    for dx in (0, 1):
        for dz in (0, 1):
            S(px + dx, fy, pz + dz, "minecraft:mud_bricks" if (dx + dz) % 2 == 0 else "minecraft:packed_mud")
            for y in range(G - 7, fy):
                if y > G - 8 and hash01(px + dx, y, pz + dz, 1) < 0.0:
                    S(px + dx, y, pz + dz, "minecraft:mud_bricks")
    for dx, dz in ((0, 0),):
        for y in range(G - 7, fy):
            S(px, y, pz, "minecraft:mangrove_log[axis=y]")
            S(px + 1, y, pz + 1, "minecraft:mangrove_log[axis=y]") if False else None
LEDGE_Y = G + 6
LEDGE = (CCX + 8, CCZ + 3)
for x in range(LEDGE[0] - 1, LEDGE[0] + 3):
    for z in range(LEDGE[1] - 1, LEDGE[1] + 3):
        for y in range(G - 7, LEDGE_Y):
            S(x, y, z, "minecraft:mud_bricks")
        S(x, LEDGE_Y, z, "minecraft:moss_block" if hash01(x, 1, z, 6) < 0.4 else "minecraft:mud_bricks")
S(LEDGE[0] + 1, LEDGE_Y + 1, LEDGE[1] - 1, "minecraft:lantern[hanging=false]")
S(LEDGE[0] - 1, LEDGE_Y + 1, LEDGE[1] + 2, "minecraft:lantern[hanging=false]")
# the way back out of the pit: steps up the west side
for i in range(7):
    S(CCX - 10 + (i // 2), G - 8 + 1 + i, CCZ + 2 + 0, "minecraft:mud_brick_stairs[facing=east,half=bottom]") if False else None
for dz in range(0, 3):
    for i in range(6):
        S(CCX + 7 - i // 1 * 0, G - 7 + i, CCZ - 3 + dz, "minecraft:air") if False else None
# a ramp of stairs out of the water, west wall (x -186..-181, z -10..-8)
for i in range(7):
    x = CCX - 10 + i
    for z in (CCZ + 1, CCZ + 2):
        for y in range(G - 8, G - 2 + i):
            S(x, y, z, "minecraft:mud_bricks")
        S(x, G - 2 + i, z, "minecraft:mud_brick_slab[type=bottom]")
# glowing: lanterns and amethyst in the chamber
for k in range(16):
    ang = k * 2 * math.pi / 16
    for rr, hh in ((0.85, 0.7), (0.6, 0.85), (0.35, 0.95)):
        x = int(round(CCX + CRX * rr * math.cos(ang + rr)))
        z = int(round(CCZ + CRZ * rr * math.sin(ang + rr)))
        q = ((x - CCX) / CRX) ** 2 + ((z - CCZ) / CRZ) ** 2
        if q < 1:
            yy = G + int(CH * math.sqrt(1 - q))
            if area.get(x, yy, z) in (None, "minecraft:air") and area.get(x, yy + 1, z) not in (None, "minecraft:air") and hash01(x, 3, z, 9) < 0.7:
                S(x, yy, z, "minecraft:sea_lantern" if k % 3 == 0 else "minecraft:amethyst_cluster[facing=down]")
                if k % 3 == 0:
                    S(x, yy - 1, z, "minecraft:purple_stained_glass")
reserve(HILL[0], HILL[1] + 8, 3)

# ---------------------------------------------------------------- the Leeches Lair
FLOOR = LAIR_FLOOR


def cav_q(x, z):
    return (abs((x - LCX) / 21.5)) ** 4 + (abs((z - LCZ) / 21.0)) ** 4 + 0.12 * noise(x, z, 61, 1.6)


cavern = {}
for x in range(LCX - 24, LCX + 25):
    for z in range(LCZ - 24, LCZ + 25):
        q = cav_q(x, z)
        if q < 1:
            ceil = FLOOR + int(24 * math.sqrt(max(0.0, 1 - q)) + 1.5 * noise(x, z, 62, 2.0))
            cavern[(x, z)] = max(ceil, FLOOR + 3)
for (x, z), ceil in cavern.items():
    for y in range(FLOOR + 1, ceil + 1):
        area.clear(x, y, z)
    # the floor and lining: make sure there is solid rock under and around
    S(x, FLOOR, z, "minecraft:mud" if hash01(x, 1, z, 7) < 0.5 else "minecraft:moss_block")
    for y in range(FLOOR - 1, FLOOR - 5, -1):
        S(x, y, z, "minecraft:deepslate" if hash01(x, y, z, 7) < 0.6 else "minecraft:tuff")
    if hash01(x, 0, z, 33) < 0.05 and ceil > FLOOR + 6:
        for i in range(2 + int(hash01(x, 1, z, 33) * 4)):
            S(x, ceil - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=middle]")
        S(x, ceil - 2 - int(hash01(x, 1, z, 33) * 4), z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]") if False else None
    elif hash01(x, 5, z, 34) < 0.012 and ceil > FLOOR + 6:
        S(x, ceil + 1 - 1, z, "minecraft:glow_lichen[up=true]") if False else None
# the arena floor (about 30 x 30): deepslate tiles with a ring of crying obsidian and lights
AX0, AX1, AZ0, AZ1 = -134, -105, 13, 42
for x in range(AX0, AX1 + 1):
    for z in range(AZ0, AZ1 + 1):
        if (x, z) not in cavern:
            continue
        ring = x in (AX0, AX1) or z in (AZ0, AZ1)
        dd = math.hypot(x - (AX0 + AX1) / 2, z - (AZ0 + AZ1) / 2)
        b = ("minecraft:crying_obsidian" if ring and (x + z) % 4 == 0 else "minecraft:polished_deepslate" if ring
             else "minecraft:deepslate_tiles" if hash01(x, 1, z, 3) < 0.85 else "minecraft:cracked_deepslate_tiles")
        if abs(dd - 9) < 0.6 or abs(dd - 4) < 0.5:
            b = "minecraft:chiseled_deepslate" if hash01(x, 2, z, 3) < 0.5 else "minecraft:polished_blackstone_bricks"
        if dd < 1.2:
            b = "minecraft:crying_obsidian"
        S(x, FLOOR, z, b)
# ring lights around the arena
for x in range(AX0, AX1 + 1, 5):
    for z in (AZ0 - 1, AZ1 + 1):
        if (x, z) in cavern:
            for y in range(FLOOR + 1, FLOOR + 4):
                S(x, y, z, "minecraft:deepslate_brick_wall")
            S(x, FLOOR + 4, z, "minecraft:sea_lantern")
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                S(x + dx, FLOOR + 4, z + dz, "minecraft:purple_stained_glass")
            S(x, FLOOR + 5, z, "minecraft:purple_stained_glass")
for z in range(AZ0, AZ1 + 1, 5):
    for x in (AX0 - 1, AX1 + 1):
        if (x, z) in cavern and not (-139 <= x <= -135 and 11 <= z <= 36):
            for y in range(FLOOR + 1, FLOOR + 4):
                S(x, y, z, "minecraft:deepslate_brick_wall")
            S(x, FLOOR + 4, z, "minecraft:sea_lantern")
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                S(x + dx, FLOOR + 4, z + dz, "minecraft:purple_stained_glass")
            S(x, FLOOR + 5, z, "minecraft:purple_stained_glass")
# purple light pods in the ceiling, glow lichen on the walls
for (x, z), ceil in sorted(cavern.items()):
    h = hash01(x, 6, z, 35)
    if h < 0.006 and ceil > FLOOR + 8:
        S(x, ceil, z, "minecraft:sea_lantern")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if area.get(x + dx, ceil, z + dz) in (None, "minecraft:air"):
                S(x + dx, ceil, z + dz, "minecraft:purple_stained_glass")
        S(x, ceil - 1, z, "minecraft:purple_stained_glass")
    elif 0.006 <= h < 0.02 and ceil > FLOOR + 5 and area.get(x, ceil + 1, z) not in (None, "minecraft:air"):
        S(x, ceil, z, "minecraft:glow_lichen[up=true]")
    elif hash01(x, 7, z, 36) < 0.008 and (x, z) not in occ:
        for i in range(1, 2 + int(hash01(x, 8, z, 36) * 3)):
            S(x, FLOOR + i, z, "minecraft:pointed_dripstone[vertical_direction=up,thickness=middle]" if i > 1 else
              "minecraft:pointed_dripstone[vertical_direction=up,thickness=base]")
# stalagmite tips and crystals along the walls
for (x, z), ceil in sorted(cavern.items()):
    near_wall = any((x + dx, z + dz) not in cavern for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if near_wall and hash01(x, 9, z, 37) < 0.25:
        S(x, FLOOR + 1, z, "minecraft:amethyst_cluster[facing=up]" if hash01(x, 1, z, 9) < 0.3 else
          "minecraft:moss_carpet" if hash01(x, 2, z, 9) < 0.5 else "minecraft:brown_mushroom")

# the sloped tunnel from the surface (east of the lagoon) down into the lair
TZ = 39
for x in range(-76, -95, -1):
    y = x + 144
    for z in range(TZ - 1, TZ + 2):
        for yy in range(y + 1, y + 5):
            area.clear(x, yy, z)
        S(x, y, z, "minecraft:tuff_brick_stairs[facing=east,half=bottom]")
        for yy in range(y - 1, y - 3, -1):
            S(x, yy, z, "minecraft:tuff_bricks")
    for z in (TZ - 2, TZ + 2):
        for yy in range(y, y + 5):
            if area.get(x, yy, z) not in (None, "minecraft:air"):
                S(x, yy, z, "minecraft:tuff_bricks" if hash01(x, yy, z, 3) < 0.8 else "minecraft:mud_bricks")
    if x % 4 == 0 and y + 3 < G + 1:
        for z in (TZ - 1, TZ + 1):
            S(x, y + 3, z, "minecraft:lantern[hanging=true]")
# landing in the rock, then the ramp wedge along the cavern's east side
for x in range(-100, -94):
    for z in range(TZ - 2, TZ + 3):
        for y in range(50, 55):
            area.clear(x, y, z)
        for y in range(FLOOR + 1, 50):
            S(x, y, z, "minecraft:tuff_bricks")
        S(x, 49, z, "minecraft:tuff_bricks" if (x + z) % 3 else "minecraft:polished_tuff")
for x in range(-100, -97):
    for z in range(36, 17, -1):
        y = z + 13
        for yy in range(y + 1, y + 6):
            area.clear(x, yy, z)
        for yy in range(FLOOR + 1, y):
            S(x, yy, z, "minecraft:tuff_bricks")
        S(x, y, z, "minecraft:tuff_brick_stairs[facing=south,half=bottom]")
# railing on the open (west) side and lamps
for z in range(36, 19, -1):
    y = z + 13
    S(-101, y + 1, z, "minecraft:deepslate_brick_wall") if False else None
for z in range(34, 18, -3):
    S(-101, z + 14, z, "minecraft:deepslate_brick_wall")
    S(-101, z + 15, z, "minecraft:deepslate_brick_wall")
    S(-101, z + 16, z, "minecraft:sea_lantern")
    for dx, dz in ((-1, 0), (0, 1), (0, -1)):
        S(-101 + dx, z + 16, z + dz, "minecraft:purple_stained_glass")
    S(-101, z + 17, z, "minecraft:purple_stained_glass")
# Dr. Phear's alcove (south-east corner) and the way up
for x in range(-108, -102):
    for z in range(38, 43):
        if (x, z) in cavern:
            S(x, FLOOR, z, "minecraft:mud_bricks" if (x + z) % 2 else "minecraft:packed_mud")
for x, z in ((-108, 43), (-102, 43), (-108, 37)):
    for y in range(FLOOR + 1, FLOOR + 4):
        S(x, y, z, "minecraft:mud_brick_wall")
    S(x, FLOOR + 4, z, "minecraft:sea_lantern")
    S(x, FLOOR + 5, z, "minecraft:purple_stained_glass")
S(-104, FLOOR + 1, 42, "minecraft:cartography_table")
S(-106, FLOOR + 1, 42, "minecraft:barrel[facing=up]")
# the raised spot at the arena's north edge (negates two phases)
for dx in range(-1, 2):
    for dz in range(-1, 2):
        S(-121 + dx, FLOOR + 1, 14 + dz, "minecraft:deepslate_bricks" if dx or dz else "minecraft:crying_obsidian")
S(-121 + 1, FLOOR + 2, 14 + 1, "minecraft:amethyst_cluster[facing=up]")
S(-121 - 1, FLOOR + 2, 14 - 1, "minecraft:amethyst_cluster[facing=up]")

# the waterfall (west side) with a nook behind it, reached by stairs along the west wall
for x in range(-141, -134):
    for z in range(17, 21):
        for y in range(45, 50):
            area.clear(x, y, z)
        for y in range(FLOOR + 1, 44):
            if x >= -138:
                S(x, y, z, "minecraft:tuff_bricks")
        S(x, 44, z, "minecraft:tuff_bricks" if hash01(x, 1, z, 3) < 0.7 else "minecraft:mossy_stone_bricks"
          if False else "minecraft:tuff_bricks")
for x in range(-142, -138):
    for z in range(12, 17):
        for y in range(45, 49):
            area.clear(x, y, z)
        S(x, 44, z, "minecraft:tuff_bricks")
        S(x, 49, z, "minecraft:tuff")
        S(x, 43, z, "minecraft:tuff")
for x in range(-138, -135):
    for z in range(21, 35):
        y = 65 - z
        for yy in range(y + 1, y + 6):
            area.clear(x, yy, z)
        for yy in range(FLOOR + 1, y):
            S(x, yy, z, "minecraft:tuff_bricks")
        S(x, y, z, "minecraft:tuff_brick_stairs[facing=north,half=bottom]")
for z in range(12, 17):
    for y in range(FLOOR + 1, 55):
        S(-138, y, z, "minecraft:water")
    for y in range(50, 56):
        S(-139, y, z, "minecraft:deepslate")
        S(-137, y, z, "minecraft:air") if False else None
    S(-138, 55, z, "minecraft:deepslate")
for z in (11, 17):
    for y in range(FLOOR + 1, 55):
        if z == 11:
            S(-138, y, z, "minecraft:deepslate_bricks")
# rails along the stairs' open side + lights
for z in range(33, 21, -3):
    y = 65 - z
    S(-135, y + 1, z, "minecraft:deepslate_brick_wall")
    S(-135, y + 2, z, "minecraft:sea_lantern")
    S(-134, y + 2, z, "minecraft:purple_stained_glass")
    S(-135, y + 3, z, "minecraft:purple_stained_glass")
for x in range(-141, -135):
    S(x, 45, 20, "minecraft:deepslate_brick_wall")
S(-141, 45, 18, "minecraft:lantern[hanging=false]")
S(-140, 46, 13, "minecraft:glow_lichen[down=true]") if False else None
S(-141, 45, 13, "minecraft:lantern[hanging=false]")
# a pool where the fall lands
for x in range(-137, -133):
    for z in range(10, 18):
        if (x, z) in cavern:
            S(x, FLOOR, z, "minecraft:mud")
            S(x, FLOOR + 1, z, "minecraft:water") if x <= -135 else None

# the scattered vegetation on the islets (after the structures so they keep their space)
reserve(RUNES[0], RUNES[1], 7)
reserve(-176, 36, 6)
reserve(-168, 41, 3)
reserve(-79, 36, 3)
reserve(-112, 31, 2)
add_mushrooms()
add_trees()

PETALS = "minecraft:pink_petals[flower_amount=2,facing=north]"
WILD = (lambda x, z: ("minecraft:short_grass" if hash01(x, 1, z, 5) < 0.55 else "minecraft:fern" if hash01(x, 2, z, 5) < 0.55 else
                      PETALS if hash01(x, 3, z, 5) < 0.2 else "minecraft:brown_mushroom" if hash01(x, 4, z, 5) < 0.5 else
                      "minecraft:red_mushroom"))
area.scatter({c for c in T if c not in WET and c not in path_cols}, 0.28, WILD, seed=4,
             avoid=lambda x, z: (x, z) in occ)
# lily pads, dead bushes and a few reeds on the water
for (x, z) in sorted(WET):
    if (x, z) in path_cols or (x, z) in occ:
        continue
    if area.get(x, G, z) not in (None, "minecraft:air"):
        continue
    if hash01(x, 12, z, 41) < 0.1:
        S(x, G, z, "minecraft:lily_pad")
# glow lichen strings and mangrove trunks give the fog its glints: mud mounds with moss
for (x, z) in sorted(BANK):
    if (x, z) in occ or hash01(x, 13, z, 42) > 0.05:
        continue
    t = T[(x, z)]
    if area.get(x, t + 1, z) in (None, "minecraft:air"):
        S(x, t + 1, z, "minecraft:moss_carpet")

# ---------------------------------------------------------------- the Gill-Man, NPCs, souls, spawn spots
# (standing spots: surface + 1)
area.npc("Reed", -121, 69, 26, 180.0, "stranded in the Lagoon Hut; sells boats (a half-built one on the west dock)")
area.npc("Kay", -116, 69, 26, 180.0, "in the Lagoon Hut: Lil' Pads for Motes")
area.npc("Roy", -121, 69, 31, 0.0, "in the Lagoon Hut: sells an Enigma Soul for Deadgehog Spines")
area.npc("The Gill-Man", -117, 69, 30, 0.0, "a Porhtal eye in the Lagoon Hut")
area.npc("Dr. Edwin", -79, 69, 36, 90.0, "by the Leeches Lair entrance (the tunnel mouth east of the lagoon)")
area.npc("Dr. Phear", -104, 31, 40, 0.0, "inside the Leeches Lair, south-east corner (takes you back up)")
area.npc("Mushroom Guy", -168, 69, 41, 0.0, "in the giant mushroom grove (disables fast travel nearby)")
area.npc("enigma:Mushroom Puzzle", -176, 69, 35, 0.0, "in the middle of the giant mushroom grove")
area.npc("enigma:Spinning Runes", RUNES[0], 69, RUNES[1], 0.0, "on the rune circle on the islet ringed by a strong current")
area.npc("enigma:Lagoon Cave", LEDGE[0] + 1, LEDGE_Y + 1, LEDGE[1], 0.0, "at the top of the Lagoon Cave parkour")
area.npc("enigma:Dialogue", -112, 69, 31, 0.0, "by the Lagoon Hut's east veranda")
area.npc("spawn:leech_supreme", -120, 31, 28, 0.0, "the Leech Supreme's arena centre")
area.npc("spawn:leech_spot", -121, 32, 14, 0.0, "raised spot at the arena's north edge (negates two phases)")
area.npc("spawn:montezuma_3", -140, 45, 15, 90.0, "behind the waterfall, west wall of the Leeches Lair")
area.npc("spawn:lair_exit", -105, 31, 36, 0.0, "inside the lair by Dr. Phear")
area.arrival(CROSS_X, 69, 56, 0.0)

# the spinning runes: a small circle on the islet
rx, rz = RUNES
for dx in range(-5, 6):
    for dz in range(-5, 6):
        r = math.hypot(dx, dz)
        c = (rx + dx, rz + dz)
        if c in T and c not in WET:
            if 4.0 <= r < 4.9:
                S(c[0], G, c[1], "minecraft:chiseled_stone_bricks" if False else "minecraft:chiseled_deepslate")
            elif 2.0 <= r < 2.8:
                S(c[0], G, c[1], "minecraft:amethyst_block" if (dx + dz) % 2 else "minecraft:purple_concrete")
            elif r < 1.5:
                S(c[0], G, c[1], "minecraft:crying_obsidian")
            for y in range(G + 1, G + 4):
                area.clear(c[0], y, c[1])
for k in range(4):
    a = k * math.pi / 2 + math.pi / 4
    S(int(round(rx + 4.5 * math.cos(a))), G + 1, int(round(rz + 4.5 * math.sin(a))), "minecraft:end_rod[facing=up]")
reserve(rx, rz, 6)

# the mushroom puzzle: a ring of little mushrooms around a stump
mx, mz = -176, 36
for dx in range(-4, 5):
    for dz in range(-4, 5):
        c = (mx + dx, mz + dz)
        r = math.hypot(dx, dz)
        if c in T and c not in WET:
            for y in range(G + 1, G + 4):
                area.clear(c[0], y, c[1])
            if r < 3.6:
                S(c[0], G, c[1], "minecraft:mycelium" if hash01(dx, 1, dz, 3) < 0.7 else "minecraft:podzol")
for k in range(8):
    a = k * math.pi / 4
    x, z = int(round(mx + 3.2 * math.cos(a))), int(round(mz + 3.2 * math.sin(a)))
    kind = "minecraft:red_mushroom_block" if k % 2 == 0 else "minecraft:brown_mushroom_block"
    S(x, G + 1, z, "minecraft:mushroom_stem")
    S(x, G + 2, z, kind)
S(mx, G + 1, mz - 2, "minecraft:mushroom_stem")
S(mx, G + 2, mz - 2, "minecraft:red_mushroom_block")
reserve(mx, mz, 5)
reserve(-168, 41, 2)

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


# Hypixel's drops: Deadgehogs 150 Motes and 3 spines (50% one more); Lagoon Leeches two 50% rolls of 3 Lil' Pads.
GEHOG = [drop("DEADGEHOG_SPINE", 1, 3, 3), drop("DEADGEHOG_SPINE", 0.5, 1, 1)]
LEECH = [drop("LIL_PAD", 0.5, 3, 3), drop("LIL_PAD", 0.5, 3, 3)]
area.zone("Black Lagoon", box=((X0, 56, Z0), (X1, 130, Z1)), blocks={}, mobs=[
    mob("minecraft:skeleton", "Deadgehog", 1, 150, 9, 6, 150, 6, 8, (-160, 66, -25), (-75, 80, 20), GEHOG),
    mob("minecraft:skeleton", "Deadgehog", 1, 150, 9, 6, 150, 3, 10, (-100, 66, 22), (-62, 80, 50), GEHOG),
    mob("minecraft:silverfish", "Lagoon Leech", 2, 80, 3, 6, 40, 6, 6, (-155, 66, -10), (-95, 72, 22), LEECH),
    mob("minecraft:silverfish", "Lagoon Leech", 2, 80, 3, 6, 40, 4, 8, (-150, 66, 30), (-105, 72, 50), LEECH),
])
area.zone("Leeches Lair", box=((-145, 20, 3), (-87, 55, 50)), blocks={}, mobs=[])
area.save()
print("boardwalk deck blocks", boards, "lamps", lamps, "mushrooms", len(mushrooms))
