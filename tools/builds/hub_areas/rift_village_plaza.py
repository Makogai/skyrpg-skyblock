#!/usr/bin/env python3
"""The Rift: the Village Plaza (x -30..69, z -120..-31), Barrier Street and Deja Vu Alley.

A modest, slightly off town square with a fountain, Taylor's, a flower shop with a bookshelf, Seymour's shoe shop, the Barry
Center (protesters out front), the taped-off Murder House, the Lonely Terrace, the Half-Eaten Cave, Book in a Book, a bank, and
on the east side Barrier Street (Damia's auction stall, ending at a glass barrier gate). Deja Vu Alley is a string of floating
islets at the north-east. West Village meets it along x = -30 (crossing z -70), Dreadfarm along z = -121 (crossing x -15);
the east, south and the north east of x 0 are the Rift's broken outer edge.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_village_plaza.py
"""
import math

from kit import hash01, noise
from rift_lib import new_area, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("village_plaza", version=3)
area.shell = 13
S = area.set
X0, X1, Z0, Z1 = -30, 69, -120, -31
G = GROUND
AIR = "minecraft:air"
assert CROSSINGS[("west_village", "village_plaza")] == ("x", -30, -70)
assert CROSSINGS[("village_plaza", "dreadfarm")] == ("z", -121, -15)
PCX, PCZ = 0, -73                 # plaza centre (the fountain)
PLAZA_R = 13
HILL = (46, -58)                  # the Half-Eaten Cave's hill


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def empty(x, y, z):
    return area.get(x, y, z) is None


def free(x, y, z):
    return area.get(x, y, z) in (None, AIR)


def lamp(x, y, z):
    """A purple lantern: a sea lantern behind purple stained glass."""
    S(x, y, z, "sea_lantern")
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
        if empty(x + dx, y + dy, z + dz):
            S(x + dx, y + dy, z + dz, "purple_stained_glass")


def post_lamp(x, y, z, hgt=3, wood="dark_oak_fence", purple=False):
    for h in range(hgt):
        S(x, y + h, z, wood)
    if purple:
        lamp(x, y + hgt, z)
    else:
        S(x, y + hgt, z, "lantern[hanging=false]")


def sign(x, y, z, lines, block="minecraft:dark_oak_wall_sign[facing=south]", color="black", glow=False):
    ox, oy, oz = area.origin
    if area.inside(x, y, z):
        area.piece.sign(x - ox, y - oy, z - oz, lines, block=block, color=color, glow=glow)


# ------------------------------------------------------------------------------------------------ terrain
def jag(p, seed):
    return 2 + 5 * (0.5 + 0.5 * noise(p, seed * 37, 11, 1.8)) + 2.5 * hash01(p, seed, 3)


def land_e(x, z):
    """Distance in from the Rift's outer edge (negative: void). West and the north-west edge are seams (no inset)."""
    e_east = (X1 - x) - jag(z, 1)
    e_south = (Z1 - z) - jag(x, 2)
    t = smooth((x + 6) / 24.0)
    inset_n = t * (9 + 4 * noise(x, 13, 11, 1.8)) + t * 2 * hash01(x, 2, 3)
    e_north = (z - Z0) - inset_n
    return min(e_east, e_south, e_north)


def hill_bump(x, z):
    d = math.hypot(x - HILL[0], z - HILL[1])
    return 14 * smooth(1.6 * (1 - d / 18.0))


SURF = area.mix({"grass_block": 10, "moss_block": 2, "podzol": 2, "rooted_dirt": 1, "coarse_dirt": 2})
HILL_SURF = area.mix({"moss_block": 3, "tuff": 3, "grass_block": 2, "coarse_dirt": 1})
UNDER = area.mix({"dirt": 6, "rooted_dirt": 2, "coarse_dirt": 1, "mud": 1})
BODY = area.mix({"tuff": 5, "deepslate": 3, "andesite": 2, "dirt": 1})
UNDERSIDE = area.mix({"tuff": 8, "deepslate": 7, "crying_obsidian": 1, "purpur_block": 1, "amethyst_block": 1,
                      "calcite": 1, "tuff_bricks": 1})
cols = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = land_e(x, z)
        if e < 0:
            continue
        dx_ = max(X0 - x, 0, x - 46)
        dz_ = max(Z0 - z, 0, z - (-38))
        m = smooth(1 - math.hypot(dx_, dz_) / 8.0)
        raw = 2.5 * noise(x, z, 2, 1.1) + 1.5 * noise(x, z, 6, 2.7)
        top = 68 + int(round(raw * (1 - m)))
        bump = hill_bump(x, z)
        top += int(round(bump))
        if e < 3:
            top = max(64, top - int(hash01(x, 9, z, 2) * 3))
        if x <= X0 + SEAM + 1 or (z <= Z0 + SEAM + 1 and x <= -1):
            top = 68
        top = max(63, min(84, top))
        cols[(x, z)] = (top, e, bump)

for (x, z), (top, e, bump) in cols.items():
    area.set(x, top, z, HILL_SURF(x, top, z) if bump >= 4 else SURF(x, top, z))
    for y in range(top - 1, top - 4, -1):
        area.set(x, y, z, "tuff" if bump >= 4 else UNDER(x, y, z))
    for y in range(top - 4, LAND_BOTTOM - 1, -1):
        area.set(x, y, z, BODY(x, y, z))
    depth = int(30 * smooth(e / 28.0) + 3 * noise(x, z, 13, 1.4))
    if hash01(x, 5, z, 4) < 0.07 and e < 26:
        depth += 4 + int(hash01(x, 6, z, 4) * 10)
    bottom = max(8, LAND_BOTTOM - 1 - depth)
    for y in range(LAND_BOTTOM - 1, bottom - 1, -1):
        area.set(x, y, z, UNDERSIDE(x, y, z))
    if depth > 4 and hash01(x, 7, z, 5) < 0.04:
        area.set(x, bottom - 1, z, "amethyst_cluster[facing=down]")
    area.top[(x, z)] = top


def T(x, z):
    return area.top.get((x, z), 68)


# ------------------------------------------------------------------------------------------------ helpers
occupied = set()          # columns that stay free of trees and scatter


def reserve(x0, z0, x1, z1, margin=1):
    for x in range(min(x0, x1) - margin, max(x0, x1) + margin + 1):
        for z in range(min(z0, z1) - margin, max(z0, z1) + margin + 1):
            occupied.add((x, z))


def paint(x0, z0, x1, z1, blocks, y=G):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if (x, z) in area.top and area.top[(x, z)] == y:
                S(x, y, z, blocks(x, y, z))


def level(x0, z0, x1, z1, y=G):
    """Flatten a rectangle (cut or fill) to surface y (used under buildings on slightly uneven ground)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            old = area.top.get((x, z))
            if old is None or old == y:
                continue
            for yy in range(y + 1, old + 1):
                S(x, yy, z, AIR)
            for yy in range(old, y):
                S(x, yy, z, "dirt")
            S(x, y, z, "grass_block")
            area.top[(x, z)] = y


PAVE = area.mix({"stone_bricks": 4, "polished_andesite": 3, "andesite": 1, "cracked_stone_bricks": 1, "mossy_stone_bricks": 1})
PATHB = area.mix({"coarse_dirt": 5, "rooted_dirt": 3, "mud_bricks": 2, "packed_mud": 1})
STREET = area.mix({"polished_andesite": 3, "stone_bricks": 3, "andesite": 1, "gravel": 1, "cracked_stone_bricks": 1})


def house(x0, z0, x1, z1, h, wall, frame, roof, slab, floor, door=None, dw=2, ridge="x", kind="gable",
          windows=True, parapet="stone_brick_wall", ceil=None):
    """A little building: floor at y 68, walls y 69..68+h, windows, a doorway (side, pos) and a gabled or flat roof.
    Returns the roof's top y."""
    level(x0 - 1, z0 - 1, x1 + 1, z1 + 1)
    reserve(x0, z0, x1, z1, 2)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            S(x, G, z, floor)
            for y in range(G + 1, G + h + 1):
                S(x, y, z, AIR)
    side_ok = {"N": (z0, "x"), "S": (z1, "x"), "W": (x0, "z"), "E": (x1, "z")}
    gap = set()
    if door:
        side, pos = door
        for i in range(dw):
            gap.add((side, pos + i))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            on_x = x in (x0, x1)
            on_z = z in (z0, z1)
            if not (on_x or on_z):
                continue
            corner = on_x and on_z
            side = "N" if z == z0 else "S" if z == z1 else "W" if x == x0 else "E"
            along = x if side in "NS" else z
            axis = "x" if side in "NS" else "z"
            if not corner and on_z and on_x:
                continue
            for y in range(G + 1, G + h + 1):
                if corner:
                    S(x, y, z, f"{frame}[axis=y]")
                    continue
                if (side, along) in gap:
                    if y <= G + 2:
                        S(x, y, z, AIR)
                    else:
                        S(x, y, z, f"{frame}[axis={axis}]")
                    continue
                if y == G + h:
                    S(x, y, z, f"{frame}[axis={axis}]")
                elif windows and G + 2 <= y <= G + 3 and h >= 4 and (along - (x0 if side in "NS" else z0)) % 4 == 2 \
                        and not any((side, along + d) in gap for d in (-1, 1)):
                    S(x, y, z, "glass_pane")
                else:
                    S(x, y, z, wall)
    if kind == "flat":
        ry = G + h + 1
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                S(x, ry - 1, z, ceil or wall)
                edge = x in (x0, x1) or z in (z0, z1)
                S(x, ry, z, parapet if edge else AIR)
        return ry
    if ridge == "x":
        A0, A1, L0, L1 = z0, z1, x0, x1
    else:
        A0, A1, L0, L1 = x0, x1, z0, z1
    n = A1 - A0 + 3
    top = G + h
    for k in range(n):
        a = A0 - 1 + k
        lvl = min(k, n - 1 - k)
        y = G + h + lvl
        top = max(top, y)
        if ridge == "x":
            facing = "south" if k < n / 2 else "north"
        else:
            facing = "east" if k < n / 2 else "west"
        for l in range(L0 - 1, L1 + 2):
            x, z = (l, a) if ridge == "x" else (a, l)
            if n % 2 == 1 and k == (n - 1) // 2:
                S(x, y, z, f"{slab}[type=bottom]")
            else:
                S(x, y, z, f"{roof}[facing={facing},half=bottom]")
        if A0 <= a <= A1:
            for l in (L0, L1):
                x, z = (l, a) if ridge == "x" else (a, l)
                for yy in range(G + h + 1, y):
                    S(x, yy, z, wall)
    return top


def outside_sign(side, pos, x0, z0, x1, z1, y, lines, wood="dark_oak"):
    if side == "N":
        sign(pos, y, z0 - 1, lines, f"minecraft:{wood}_wall_sign[facing=north]")
    elif side == "S":
        sign(pos, y, z1 + 1, lines, f"minecraft:{wood}_wall_sign[facing=south]")
    elif side == "W":
        sign(x0 - 1, y, pos, lines, f"minecraft:{wood}_wall_sign[facing=west]")
    else:
        sign(x1 + 1, y, pos, lines, f"minecraft:{wood}_wall_sign[facing=east]")


def barrel(x, y, z):
    S(x, y, z, "barrel[facing=up]")


def potted(x, y, z, kind):
    S(x, y, z, "potted_" + kind)


# ------------------------------------------------------------------------------------------------ plaza and roads
for x in range(PCX - 16, PCX + 17):
    for z in range(PCZ - 16, PCZ + 17):
        d = math.hypot(x - PCX, z - PCZ)
        if d > PLAZA_R + 1.0 * noise(x, z, 4, 3):
            continue
        if (x, z) not in area.top:
            continue
        if 10.6 < d <= 11.7:
            S(x, G, z, "mud_bricks")
        elif 4.5 < d <= 5.6 and int(math.degrees(math.atan2(z - PCZ, x - PCX)) // 15) % 2 == 0:
            S(x, G, z, "polished_andesite")
        else:
            S(x, G, z, PAVE(x, G, z))
        area.top[(x, z)] = G
        occupied.add((x, z))
reserve(PCX - 14, PCZ - 14, PCX + 14, PCZ + 14, 0)

ROADS = [
    ([(-30, -70), (-13, -70)], 5),                                # West Village crossing
    ([(-15, -120), (-15, -98), (-9, -85)], 5),                    # Dreadfarm crossing, then to the plaza
    ([(8, -81), (14, -89), (22, -90)], 3),                        # plaza -> Barrier Street
    ([(-3, -60), (-3, -57)], 3),                                  # Barry Center
    ([(-13, -66), (-20, -60), (-14, -57)], 3),                    # Murder House yard
    ([(12, -66), (18, -57), (20, -50)], 3),                       # bank and terrace
    ([(13, -73), (26, -63), (33, -58)], 3),                       # the cave
    ([(-15, -100), (-24, -104), (-20, -106)], 3),                 # Cowboy Nick
    ([(-15, -91), (-24, -92)], 3),                                # Book in a Book
    ([(-13, -76), (-17, -77)], 3),                                # Seymour
    ([(-5, -92), (-5, -108), (8, -108)], 3),                      # back alley
    ([(7, -88), (7, -100)], 3),
]
road_cells = set()
for pts, w in ROADS:
    road_cells |= area.path(pts, width=w, blocks=PATHB, seed=5)
occupied |= road_cells
for x in range(X0, X0 + SEAM + 1):                                # the crossing at x -30: exact seam blocks
    for z in range(-72, -67):
        if (x, z) in area.top:
            S(x, G, z, PATHB(x, G, z))
for z in range(Z0, Z0 + SEAM + 1):
    for x in range(-17, -12):
        if (x, z) in area.top:
            S(x, G, z, PATHB(x, G, z))

# Barrier Street: a paved street along z -93..-87 from the plaza link to the gate at x 44
paint(20, -93, 45, -87, STREET)
for x in range(20, 46):
    for z in range(-93, -86):
        occupied.add((x, z))
for x in range(20, 46):
    for z in (-93, -87):
        if (x, z) in area.top and (x - 20) % 6 == 3:
            post_lamp(x, G + 1, z, 3, "dark_oak_fence", purple=(x % 12 == 3))

# lamp posts along the plaza edge
for k in range(8):
    a = k * math.pi / 4 + math.pi / 8
    x, z = PCX + int(round(12.2 * math.cos(a))), PCZ + int(round(12.2 * math.sin(a)))
    if (x, z) in area.top and (x, z) not in road_cells:
        post_lamp(x, G + 1, z, 3, "dark_oak_fence", purple=(k % 2 == 0))

# ------------------------------------------------------------------------------------------------ the fountain
for x in range(PCX - 6, PCX + 7):
    for z in range(PCZ - 6, PCZ + 7):
        d = math.hypot(x - PCX, z - PCZ)
        if d <= 3.4:
            S(x, G - 1, z, "stone_bricks")
            S(x, G, z, "water")
            S(x, G + 1, z, AIR)
        elif d <= 4.6:
            S(x, G, z, "stone_bricks")
            S(x, G + 1, z, "stone_brick_slab[type=bottom]" if d > 3.9 else "polished_andesite")
        if d <= 0.9:
            for y in range(G, G + 4):
                S(x, y, z, "polished_andesite" if y < G + 3 else "chiseled_stone_bricks")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    S(PCX + dx, G + 1, PCZ + dz, "water")
    S(PCX + dx, G + 2, PCZ + dz, AIR)
S(PCX, G + 4, PCZ, "water")
S(PCX, G + 5, PCZ, "lantern[hanging=false]")
for k in range(4):
    a = k * math.pi / 2 + math.pi / 4
    bx, bz = PCX + int(round(7.0 * math.cos(a))), PCZ + int(round(7.0 * math.sin(a)))
    face = {0: "west", 1: "north", 2: "east", 3: "south"}[k]
    S(bx, G + 1, bz, f"dark_oak_stairs[facing={face},half=bottom]")

# ------------------------------------------------------------------------------------------------ the shops
# Taylor's (x -10..-2, z -94..-86), door south
TAY = (-10, -94, -2, -86)
house(*TAY, 4, "pale_oak_planks", "stripped_pale_oak_log", "dark_oak_stairs", "dark_oak_slab", "spruce_planks",
      door=("S", -7), dw=2, ridge="x")
outside_sign("S", -7, *TAY, G + 3, ["Taylor's", "Tailoring &", "Alterations"])
for x in range(-9, -2):
    barrel(x, G + 1, -92) if x % 2 else S(x, G + 1, -92, "stripped_dark_oak_log[axis=x]")
for x, c in ((-9, "white"), (-8, "pink"), (-7, "light_blue"), (-4, "purple")):
    S(x, G + 1, -93, f"{c}_wool")
    S(x, G + 2, -93, f"{c}_wool")
S(-3, G + 1, -93, "loom[facing=south]")
S(-9, G + 1, -88, "cartography_table")
S(-3, G + 1, -88, "crafting_table")
for x in (-9, -3):
    S(x, G + 3, -90, "lantern[hanging=false]") if False else None
post_lamp(-9, G + 1, -90, 2)
TAYLOR_NPC = (-6, G + 1, -90)

# the flower shop (x 3..11, z -94..-86), door south; the bookshelf holds a Montezuma piece
FLO = (3, -94, 11, -86)
house(*FLO, 4, "cherry_planks", "stripped_cherry_log", "cherry_stairs", "cherry_slab", "spruce_planks",
      door=("S", 7), dw=2, ridge="x")
outside_sign("S", 7, *FLO, G + 3, ["Petal Pushers", "Flowers &", "Old Books"], "cherry")
for x in range(4, 11):
    S(x, G + 1, -93, "bookshelf")
    S(x, G + 2, -93, "bookshelf")
    S(x, G + 3, -93, "bookshelf") if x % 2 == 0 else None
S(6, G + 3, -93, "bookshelf")
S(6, G + 1, -92, "lectern[facing=south]")
FLOWERS = ["allium", "cornflower", "azure_bluet", "pink_tulip", "lily_of_the_valley", "poppy", "oxeye_daisy", "dandelion"]
for i, x in enumerate(range(4, 11, 1)):
    if x in (6,):
        continue
    potted(x, G + 1, -88, FLOWERS[i % len(FLOWERS)])
S(10, G + 1, -92, "composter")
S(4, G + 1, -92, "cauldron")
S(9, G + 1, -90, "dark_oak_fence")
S(9, G + 2, -90, "lantern[hanging=false]")
for x in range(3, 12):                                    # window boxes outside
    if x not in (7, 8):
        S(x, G + 1, -85, "dark_oak_trapdoor[facing=south,half=bottom,open=false]") if False else None
        potted(x, G + 1, -85, FLOWERS[(x * 3) % len(FLOWERS)]) if x % 2 else None
MONTEZUMA_6 = (6, G + 1, -92)

# Seymour's shoe shop (x -26..-18, z -82..-74), door east
SEY = (-26, -82, -18, -74)
house(*SEY, 4, "mud_bricks", "dark_oak_log", "mud_brick_stairs", "mud_brick_slab", "spruce_planks",
      door=("E", -79), dw=2, ridge="z")
outside_sign("E", -79, *SEY, G + 3, ["Seymour's", "Fine Footwear"])
for z in range(-81, -75):
    S(-25, G + 1, z, "dark_oak_slab[type=bottom]")
    S(-25, G + 2, z, "dark_oak_slab[type=top]")
    S(-25, G + 3, z, "dark_oak_slab[type=bottom]") if z % 2 else None
    if z % 2:
        potted(-25, G + 2, z, "brown_mushroom") if False else None
S(-23, G + 1, -75, "spruce_stairs[facing=south,half=bottom]")
S(-22, G + 1, -75, "spruce_stairs[facing=south,half=bottom]")
S(-21, G + 1, -81, "glass_pane")
S(-21, G + 2, -81, "glass_pane")
S(-19, G + 1, -76, "lantern[hanging=false]") if False else post_lamp(-19, G + 1, -75, 2)
SEYMOUR_NPC = (-22, G + 1, -78)

# the Barry Center (x -10..4, z -56..-46): a civic block with a flat roof, door north
BAR = (-10, -56, 4, -46)
house(*BAR, 5, "stone_bricks", "polished_andesite", "stone_brick_stairs", "stone_brick_slab", "polished_andesite",
      door=("N", -4), dw=3, kind="flat", windows=True)
outside_sign("N", -3, *BAR, G + 5, ["Barry Center", "Civic Affairs"], "spruce")
for z in (-56,):
    for x in (-5, -1):
        for y in range(G + 1, G + 6):
            S(x, y, z, "polished_andesite")
for x in range(-9, 4):
    S(x, G + 1, -51, "spruce_slab[type=bottom]") if -7 <= x <= 1 else None
barrel(-7, G + 1, -48)
barrel(1, G + 1, -48)
S(-9, G + 1, -52, "bookshelf")
S(-9, G + 2, -52, "bookshelf")
S(3, G + 1, -52, "bookshelf")
S(3, G + 2, -52, "bookshelf")
post_lamp(-8, G + 1, -49, 3, purple=True)
post_lamp(2, G + 1, -49, 3, purple=True)
BARRY_NPC = (-3, G + 1, -53)
# banners on the facade and out front
for x in (-9, -7, -0, 2):
    S(x, G + 3, -57, "red_wall_banner[facing=north]")
    S(x, G + 4, -57, "white_wall_banner[facing=north]")
for x in (-11, 6):
    S(x, G + 1, -58, "dark_oak_fence")
    S(x, G + 2, -58, "dark_oak_fence")
    S(x, G + 3, -58, "red_banner[rotation=8]")
PROTESTERS = [(-10, G + 1, -59), (-8, G + 1, -59), (-6, G + 1, -59), (0, G + 1, -59), (2, G + 1, -59), (4, G + 1, -59), (6, G + 1, -59)]
SLOGANS = [["BARRY OUT"], ["WE WANT", "ANSWERS"], ["SAVE THE", "PENS"], ["FREE", "THE RABBITS"], ["NO MORE", "MORPHS"], ["HONK IF", "ANGRY"],
           ["BARRY", "HIDES"]]
for (px, py, pz), lines in zip(PROTESTERS, SLOGANS):
    S(px, G + 1, pz + 1, "dark_oak_fence")
    sign(px, G + 2, pz + 1, lines, "minecraft:oak_sign[rotation=8]")

# Murder House (x -26..-17, z -57..-46): dark, boarded and taped off
MUR = (-26, -57, -17, -46)
house(*MUR, 4, "deepslate_bricks", "dark_oak_log", "deepslate_tile_stairs", "deepslate_tile_slab", "polished_deepslate",
      door=("N", -22), dw=2, ridge="x", windows=False)
for y in (G + 1, G + 2):                                           # the door is boarded up
    for x in (-22, -21):
        S(x, y, MUR[1], "dark_oak_planks")
for x in range(-26, -17, 3):                                        # boarded windows
    for y in (G + 2, G + 3):
        S(x, y, MUR[1], "dark_oak_trapdoor[facing=north,half=bottom,open=false]") if False else S(x, y, MUR[1], "spruce_planks")
for x in range(-25, -18):
    S(x, G + 1, -50, "cobweb") if hash01(x, 1, -50, 3) < 0.5 else None
S(-24, G + 1, -53, "white_carpet")
S(-23, G + 1, -53, "white_carpet")
S(-21, G + 1, -52, "red_carpet")
S(-19, G + 1, -48, "cobweb")
S(-25, G + 1, -48, "cobweb")
S(-22, G + 1, -48, "dark_oak_stairs[facing=north,half=bottom]")
# caution tape: fence posts with yellow panes between them, round the yard
TAPE = (-29, -60, -14, -44)
for x in range(TAPE[0], TAPE[2] + 1):
    for z in range(TAPE[1], TAPE[3] + 1):
        if x in (TAPE[0], TAPE[2]) or z in (TAPE[1], TAPE[3]):
            if (x, z) not in area.top:
                continue
            corner = (x in (TAPE[0], TAPE[2])) and (z in (TAPE[1], TAPE[3]))
            if corner or (x + z) % 5 == 0:
                S(x, G + 1, z, "dark_oak_fence")
                S(x, G + 2, z, "dark_oak_fence")
            else:
                S(x, G + 2, z, "yellow_stained_glass_pane")
reserve(*TAPE, 0)
for x in range(-17, -13):                                            # a gap in the tape is just the path in: leave it shut
    pass
AMOG_NPC = (-12, G + 1, -58)
sign(-13, G + 2, -58, ["DO NOT", "CROSS"], "minecraft:oak_sign[rotation=4]", color="red")
S(-13, G + 1, -58, "dark_oak_fence")

# Cryptosis's little stall (x -17..-12, z -54..-50)
for x in range(-17, -11):
    for z in range(-54, -49):
        if (x, z) in area.top:
            S(x, G, z, "polished_deepslate" if (x + z) % 2 else "deepslate_tiles")
reserve(-17, -54, -12, -50, 1)
for x, z in ((-17, -54), (-12, -54), (-17, -50), (-12, -50)):
    for y in range(G + 1, G + 4):
        S(x, y, z, "purpur_pillar")
    lamp(x, G + 4, z)
for x in range(-16, -12):
    for z in range(-53, -50):
        S(x, G + 4, z, "purple_wool") if (x + z) % 2 else S(x, G + 4, z, "magenta_wool")
S(-14, G + 1, -51, "amethyst_block")
S(-14, G + 2, -51, "amethyst_cluster[facing=up]")
S(-16, G + 1, -52, "crying_obsidian")
S(-12, G + 1, -52, "end_rod[facing=up]") if False else None
CRYPTOSIS_NPC = (-14, G + 1, -53)

# the Bank (x 15..22, z -66..-58), door west; the Bankster waits in the back corner with his Porhtal eye
BANK = (15, -66, 22, -58)
house(*BANK, 4, "stone_bricks", "polished_blackstone", "stone_brick_stairs", "stone_brick_slab", "polished_blackstone_bricks",
      door=("W", -63), dw=2, ridge="x", windows=False)
for z in (-65, -61):
    for y in (G + 2, G + 3):
        S(BANK[0], y, z, "iron_bars")
        S(BANK[2], y, z, "iron_bars")
for x in (17, 20):
    S(x, G + 2, BANK[1], "iron_bars")
    S(x, G + 3, BANK[1], "iron_bars")
outside_sign("W", -63, *BANK, G + 3, ["Rift", "Bank"], "spruce")
for z in range(-65, -61):
    S(17, G + 1, z, "gold_block" if z == -63 else "polished_blackstone_bricks")
S(17, G + 2, -63, "gold_block")
for x in (19, 20):
    S(x, G + 1, -60, "iron_bars")
S(21, G + 1, -64, "end_portal_frame[eye=true,facing=west]")
S(21, G + 1, -66, "chest[facing=south]") if False else None
post_lamp(16, G + 1, -59, 2)
BANKSTER_NPC = (20, G + 1, -64)

# Seraphine's hut (x 14..19, z -79..-73), door west
SER = (14, -79, 19, -73)
house(*SER, 4, "spruce_planks", "stripped_spruce_log", "spruce_stairs", "spruce_slab", "spruce_planks",
      door=("W", -77), dw=2, ridge="x", windows=True)
S(18, G + 1, -78, "brewing_stand")
S(18, G + 1, -77, "cauldron")
S(16, G + 1, -74, "bookshelf")
S(17, G + 1, -74, "bookshelf")
S(15, G + 1, -74, "red_bed[facing=south,part=foot]") if False else None
potted(18, G + 1, -74, "azalea_bush")
S(15, G + 1, -78, "dark_oak_fence")
S(15, G + 2, -78, "lantern[hanging=false]")
SERAPHINE_NPC = (17, G + 1, -76)

# Book in a Book: a tiny library (x -27..-20, z -97..-90), door south
LIB = (-27, -97, -20, -90)
house(*LIB, 4, "dark_oak_planks", "stripped_dark_oak_log", "dark_oak_stairs", "dark_oak_slab", "spruce_planks",
      door=("S", -24), dw=2, ridge="x")
outside_sign("S", -24, *LIB, G + 3, ["Book in a Book", "Please Whisper"])
for x in range(-26, -20):
    for y in (G + 1, G + 2, G + 3):
        S(x, y, -96, "bookshelf")
for z in range(-95, -91):
    for y in (G + 1, G + 2):
        S(-26, y, z, "bookshelf")
S(-21, G + 1, -95, "lectern[facing=south]")
S(-21, G + 1, -94, "dark_oak_stairs[facing=west,half=bottom]")
S(-23, G + 1, -93, "dark_oak_fence")
S(-23, G + 2, -93, "dark_oak_pressure_plate") if False else None
S(-23, G + 2, -93, "lantern[hanging=false]")
# the big book outside: a lectern-and-pages prop on the roof ridge
S(-24, G + 6, -94, "white_carpet") if False else None

# ------------------------------------------------------------------------------------------------ Cowboy Nick and his rabbit pen
PEN = (-28, -112, -22, -106)
level(PEN[0] - 1, PEN[1] - 1, PEN[2] + 1, PEN[3] + 1)
reserve(*PEN, 3)
for x in range(PEN[0], PEN[2] + 1):
    for z in range(PEN[1], PEN[3] + 1):
        edge = x in (PEN[0], PEN[2]) or z in (PEN[1], PEN[3])
        if edge:
            S(x, G + 1, z, "spruce_fence")
        else:
            S(x, G, z, "farmland[moisture=7]" if (x + z) % 3 == 0 else "coarse_dirt")
            if (x + z) % 3 == 0:
                S(x, G + 1, z, "carrots[age=7]")
for x, z in ((-27, -111), (-23, -107)):
    S(x, G + 1, z, "hay_block[axis=y]") if False else None
S(-25, G + 1, -109, "hay_block[axis=y]")
S(-24, G + 1, -109, "hay_block[axis=y]")
S(PEN[2], G + 1, -109, "oak_fence_gate[facing=east,open=false]") if False else S(PEN[2], G + 1, -109, "spruce_fence_gate[facing=east,open=false]")
S(-28, G + 2, -112, "lantern[hanging=false]")
S(-22, G + 2, -106, "lantern[hanging=false]")
sign(-23, G + 2, -105, ["Nick's", "Rabbit Pen"], "minecraft:spruce_sign[rotation=8]")
S(-23, G + 1, -105, "spruce_fence")
COWBOY_NPC = (-20, G + 1, -108)
# a lean-to for the horsezooka
for x in range(-20, -15):
    S(x, G + 3, -112, "spruce_slab[type=bottom]")
    S(x, G + 3, -111, "spruce_slab[type=bottom]")
for x in (-20, -16):
    S(x, G + 1, -112, "spruce_fence")
    S(x, G + 2, -112, "spruce_fence")
barrel(-18, G + 1, -112)
S(-19, G + 1, -112, "hay_block[axis=x]")
S(-17, G + 1, -112, "crafting_table")
reserve(-21, -113, -15, -110, 1)
SOUL_RABBIT = (-23, G + 1, -104)
S(-23, G + 1, -104, "stripped_dark_oak_log[axis=y]")          # a hat stand with a top-hat-ish head
S(-23, G + 2, -104, "black_carpet") if False else None
SOUL_RABBIT = (-22, G + 1, -103)

# ------------------------------------------------------------------------------------------------ the Lonely Terrace
TER = (14, -50, 26, -40)
level(TER[0], TER[1], TER[2], TER[3])
reserve(*TER, 1)
for x in range(TER[0], TER[2] + 1):
    for z in range(TER[1], TER[3] + 1):
        S(x, G, z, "polished_andesite" if (x + z) % 2 else "stone_bricks")
        if z == TER[3] or x == TER[2]:
            S(x, G + 1, z, "stone_brick_wall")
for x in range(TER[0], TER[2] + 1):
    S(x, G + 1, TER[1], "stone_brick_wall") if x < 17 or x > 20 else None
for z in range(TER[1], TER[3] + 1):
    S(TER[0], G + 1, z, "stone_brick_wall") if z > -45 else None
for x in (19, 20, 21):
    S(x, G + 1, -45, "spruce_stairs[facing=north,half=bottom]")           # the bench (one)
post_lamp(17, G + 1, -43, 3, "dark_oak_fence")
post_lamp(24, G + 1, -47, 3, "dark_oak_fence")
for x, z in ((15, -48), (25, -42)):
    potted(x, G + 1, z, "dandelion")
S(25, G + 1, -49, "dark_oak_fence")
S(24, G + 1, -49, "potted_oxeye_daisy")
AVAEIKX_NPC = (20, G + 1, -45)
SOUL_TERRACE = (24, G + 1, -48)
S(24, G + 1, -48, AIR)

# ------------------------------------------------------------------------------------------------ the Half-Eaten Cave
HX, HZ = HILL
for x in range(25, 36):                                           # the approach: a cut through the hill's foot
    for z in range(-61, -54):
        if (x, z) in area.top:
            old = area.top[(x, z)]
            for y in range(G, old + 1):
                S(x, y, z, AIR)
            S(x, G, z, PATHB(x, G, z))
            area.top[(x, z)] = G
            occupied.add((x, z))


def cave_cell(x, z):
    """Whether a column is hollow: the mouth (x 35..39, z -60..-56) then a chamber, ragged like something bit it."""
    mouth = 35 <= x <= 40 and abs(z + 58) <= 2.2 + 0.4 * noise(x, z, 3, 2)
    inner = math.hypot((x - 44) / 5.5, (z + 58) / 5.0) <= 1.0 + 0.12 * noise(x, z, 5, 2.5)
    return mouth or inner


for x in range(35, 52):
    for z in range(-66, -49):
        if (x, z) not in area.top or not cave_cell(x, z):
            continue
        ceil_y = G + 4 + (1 if x > 41 else 0)
        for y in range(G, ceil_y + 1):
            S(x, y, z, AIR)
        S(x, G - 1, z, "tuff")
        S(x, G, z, "moss_block" if hash01(x, 1, z, 7) < 0.3 else "tuff" if hash01(x, 2, z, 7) < 0.7 else "gravel")
        S(x, ceil_y + 1, z, "tuff") if empty(x, ceil_y + 1, z) else None
        if hash01(x, 3, z, 7) < 0.12 and x > 38:
            for i in range(1 + int(hash01(x, 4, z, 7) * 2)):
                S(x, ceil_y - i, z, "pointed_dripstone[vertical_direction=down,thickness=tip]" if i else
                  "pointed_dripstone[vertical_direction=down,thickness=base]") if False else None
            S(x, ceil_y, z, "pointed_dripstone[vertical_direction=down,thickness=tip]")
        if hash01(x, 5, z, 7) < 0.06:
            S(x, G + 1, z, "pointed_dripstone[vertical_direction=up,thickness=tip]")
        if hash01(x, 6, z, 7) < 0.05:
            S(x, G + 1, z, "cobweb")
# mouth arch and teeth marks
for z in range(-61, -55):
    for y in range(G + 1, G + 6):
        if empty(35, y, z) or area.get(35, y, z) != AIR:
            pass
S(41, G + 1, -62, "bone_block")
S(42, G + 1, -62, "bone_block")
S(46, G + 1, -54, "skeleton_skull[rotation=4]") if False else None
for (bx, by, bz, br) in ((37, 77, -50, 3), (45, 80, -64, 3), (40, 76, -66, 2), (52, 78, -55, 2)):     # bites out of the hill
    for dx in range(-br, br + 1):
        for dy in range(-br, br + 1):
            for dz in range(-br, br + 1):
                if dx * dx + dy * dy + dz * dz <= br * br + 1 and area.get(bx + dx, by + dy, bz + dz) is not None:
                    S(bx + dx, by + dy, bz + dz, AIR)
lamp(38, G + 3, -61)
lamp(38, G + 3, -55)
sign(34, G + 1, -61, ["Half-Eaten", "Cave"], "minecraft:spruce_sign[rotation=12]") if False else None
S(34, G + 1, -61, "spruce_fence")
sign(34, G + 2, -61, ["Half-Eaten", "Cave"], "minecraft:spruce_sign[rotation=4]")
# part 7: the cave is stuffed with hay past its mouth (the mod: the Horsezooka clears it, it grows back), with carrot
# patches at the back for Cowboy Nick's rabbit and an Enigma Soul (Horsing Around) at the very end.
CARROTS = []
for x in range(38, 52):
    for z in range(-66, -49):
        if (x, z) not in area.top or not cave_cell(x, z):
            continue
        ceil_y = G + 4 + (1 if x > 41 else 0)
        carrot = x >= 46 and abs(z + 58) <= 2 and (x + z) % 2 == 0
        if carrot:
            S(x, G, z, "farmland[moisture=7]")
            S(x, G + 1, z, "carrots[age=7]")
            CARROTS.append((x, z))
        if carrot:
            S(x, G + 2, z, "light[level=15]")                       # crops die in the dark: a light over each patch
        for y in range(G + (3 if carrot else 1), ceil_y + 1):
            if (x, z) == (49, -58) and y <= G + 2:
                S(x, y, z, AIR)                                     # the soul's pocket
            else:
                S(x, y, z, "hay_block[axis=y]")
SOUL_HORSING = (49, G + 1, -58)
area.keep_ids = {"minecraft:hay_block"}
print("cave carrots", len(CARROTS))
reserve(24, -62, 52, -53, 0)
for x in range(30, 62):
    for z in range(-75, -42):
        if (x, z) in area.top and hill_bump(x, z) > 0.5:
            occupied.add((x, z))

# ------------------------------------------------------------------------------------------------ Barrier Street
BS = (20, -100, 45, -80)
N_ROW = [(21, -100, 28, -94, 5, "stone_bricks", "stripped_dark_oak_log", "dark_oak_stairs", "dark_oak_slab", ("S", 24)),
         (35, -100, 42, -94, 6, "mud_bricks", "stripped_spruce_log", "spruce_stairs", "spruce_slab", ("S", 38))]
S_ROW = [(21, -86, 27, -80, 5, "stone_bricks", "stripped_dark_oak_log", "dark_oak_stairs", "dark_oak_slab", ("N", 24)),
         (29, -86, 35, -80, 4, "mud_bricks", "stripped_spruce_log", "spruce_stairs", "spruce_slab", ("N", 31))]
for (a, b, c, d, hh, wl, fr, st, sl, door) in N_ROW + S_ROW:
    house(a, b, c, d, hh, wl, fr, st, sl, "spruce_planks", door=door, dw=2, ridge="x")
for x in (22, 25):
    S(x, G + 1, -99, "barrel[facing=south]")
    S(x + 14, G + 1, -99, "barrel[facing=south]")
S(21, G + 1, -86 + 1) if False else None
lamp(24, G + 4, -97)
lamp(38, G + 4, -97)
lamp(24, G + 4, -83)
lamp(31, G + 3, -83)
sign(24, G + 3, -93, ["Shen's", "Auctions"], "minecraft:spruce_wall_sign[facing=south]") if False else None
outside_sign("S", 24, *N_ROW[0][:4], G + 3, ["Closed", "for the year"])
outside_sign("N", 31, *S_ROW[1][:4], G + 3, ["Lost &", "Found"])
# the roof building (x 37..43, z -86..-80): flat roof with a parapet, ladder up the street side, gold block on top
ROOF = (37, -86, 43, -80)
ry = house(*ROOF, 5, "stone_bricks", "stripped_dark_oak_log", "dark_oak_stairs", "dark_oak_slab", "spruce_planks",
           door=("N", 39), dw=2, kind="flat", parapet="stone_brick_wall")
for y in range(G + 1, ry + 1):
    S(40, y, -87, "ladder[facing=north]")
    if y > G + 3:
        pass
S(40, ry, -86, AIR)                      # the gap in the parapet at the ladder top
S(41, ry, -85, "stone_brick_wall")
SOUL_ROOF = (40, ry, -84)
S(40, ry - 1, -84, "stone_bricks")
S(42, ry, -84, "gold_block")             # the hidden gold block beside the soul
lamp(38, ry + 1, -81)
lamp(42, ry + 1, -81)
# Damia's auction stall in the gap of the north row (x 29..34): counter, awning, a hand bell and a bidding board
for x in range(29, 35):
    for z in range(-100, -94):
        S(x, G, z, "polished_andesite" if (x + z) % 2 else "stone_bricks")
for x in range(29, 35):
    for y in range(G + 1, G + 5):
        S(x, y, -100, "stone_bricks")
for z in (-99, -98, -97, -96, -95):
    S(29, G + 1, z, AIR)
for x in range(29, 35):
    S(x, G + 1, -94, "spruce_slab[type=top]" if x not in (31, 32) else "barrel[facing=south]")
for x in (29, 34):
    for y in range(G + 1, G + 4):
        S(x, y, -94, "stripped_dark_oak_log[axis=y]")
    for y in range(G + 1, G + 4):
        S(x, y, -100, "stripped_dark_oak_log[axis=y]")
for x in range(29, 35):
    for z in range(-98, -92):
        c = "red_wool" if (x + z) % 2 == 0 else "white_wool"
        S(x, G + 4, z, c) if z >= -97 else None
for x in range(29, 35):
    S(x, G + 4, -93, "red_carpet" if x % 2 == 0 else "white_carpet")
S(33, G + 2, -94, "bell[facing=south,attachment=floor]") if False else S(33, G + 2, -94, "bell[facing=north,attachment=floor]")
S(30, G + 1, -99, "lectern[facing=south]")
S(34, G + 1, -98, "chest[facing=west]")
S(33, G + 1, -99, "barrel[facing=up]")
for z in range(-99, -96):
    S(31, G + 3, z, "lantern[hanging=true]")
sign(31, G + 3, -99, ["SHEN'S", "AUCTIONS"], "minecraft:dark_oak_wall_sign[facing=south]", glow=True)
sign(32, G + 3, -99, ["Bids in", "Motes"], "minecraft:dark_oak_wall_sign[facing=south]")
DAMIA_NPC = (30, G + 1, -95)
# the glass gate (x 44, z -94..-86): clear glass in a frame, red panes laid in an X like a barrier sign
for z in range(-94, -85):
    for y in range(G + 1, G + 8):
        S(44, y, z, "glass")
for z in range(-94, -85):
    for y in range(G + 1, G + 8):
        k = z - (-94)
        if k in (0, 8) or y in (G + 1, G + 7):
            S(44, y, z, "red_stained_glass")
        elif abs((y - G - 1) * 8 // 6 - k) <= 0 or abs((y - G - 1) * 8 // 6 - (8 - k)) <= 0:
            S(44, y, z, "red_stained_glass")
for z in (-95, -85):
    for y in range(G + 1, G + 9):
        S(44, y, z, "polished_blackstone_bricks")
    S(44, G + 9, z, "chiseled_polished_blackstone")
    lamp(45, G + 9, z)
for z in range(-94, -85):
    S(44, G + 8, z, "polished_blackstone_slab[type=bottom]")
    S(45, G, z, "polished_blackstone_bricks")
sign(43, G + 4, -90, ["NO ENTRY", "(yet)"], "minecraft:dark_oak_wall_sign[facing=west]", color="red") if False else None
S(43, G + 1, -90, "dark_oak_fence")
sign(43, G + 2, -90, ["Barrier", "Street"], "minecraft:oak_sign[rotation=4]")

# ------------------------------------------------------------------------------------------------ the plaza's souls: Fleespook, Larva Silk, Balloons
# Plaza Fleespook: a hidden nook behind Taylor's with a ghostly white woolly shape
for dx, dz in ((-1, 0), (1, 0), (0, 1)):
    S(-5 + dx, G + 1, -98 + dz, "mossy_stone_brick_wall")
    S(-5 + dx, G + 2, -98 + dz, "mossy_stone_brick_wall")
S(-5, G + 1, -99, "white_wool")
S(-5, G + 2, -99, "white_wool")
S(-5, G + 3, -99, "white_stained_glass")
SOUL_FLEE = (-5, G + 1, -98)
# Larva Silk Trail: cobwebs and string leading east along the back alley to a silk-wrapped post
for i, x in enumerate(range(-3, 9)):
    S(x, G + 1, -108 + (1 if i % 3 == 0 else 0), "white_carpet") if i % 2 == 0 else None
    if hash01(x, 2, -108, 9) < 0.5:
        S(x, G + 3, -109, "cobweb")
for y in range(G + 1, G + 4):
    S(9, y, -108, "cobweb") if y == G + 3 else S(9, y, -108, "stripped_pale_oak_log[axis=y]")
S(8, G + 1, -107, "cobweb")
SOUL_LARVA = (8, G + 1, -107)
S(8, G + 1, -107, AIR)
# Plaza Balloons: a bunch of balloons floating high over the plaza, tied to a lamp post
BAL = (6, 88, -68)
for dx in range(-2, 3):
    for dy in range(-2, 3):
        for dz in range(-2, 3):
            if dx * dx + dy * dy + dz * dz <= 5:
                S(BAL[0] + dx, BAL[1] + dy, BAL[2] + dz, ["red", "yellow", "light_blue", "lime"][(dx + dz + 4) % 4] + "_wool")
for ox, oz, col in ((-3, 1, "pink"), (3, -1, "orange"), (1, 3, "purple")):
    for dx in range(-1, 2):
        for dy in range(-1, 2):
            for dz in range(-1, 2):
                if abs(dx) + abs(dy) + abs(dz) <= 2:
                    S(BAL[0] + ox + dx, BAL[1] - 4 + dy, BAL[2] + oz + dz, f"{col}_wool")
    for y in range(G + 7, BAL[1] - 5):
        S(BAL[0] + ox, y, BAL[2] + oz, "iron_chain[axis=y]")
for y in range(G + 1, BAL[1] - 2):
    S(BAL[0], y, BAL[2], "iron_chain[axis=y]") if y > G + 3 else S(BAL[0], y, BAL[2], "dark_oak_fence")
SOUL_BALLOONS = (BAL[0], BAL[1] + 3, BAL[2])

# ------------------------------------------------------------------------------------------------ Deja Vu Alley: floating islets
ISLETS = [(26, -114, 67, 2), (31, -117, 66, 2), (38, -117, 64, 2), (44, -115, 65, 2), (50, -118, 66, 2), (56, -114, 67, 3),
          (61, -109, 68, 2)]
for k, (cx, cz, y, r) in enumerate(ISLETS):
    for dx in range(-r - 1, r + 2):
        for dz in range(-r - 1, r + 2):
            d = math.hypot(dx, dz)
            rr = r + 0.5 * noise(cx + dx, cz + dz, k, 3)
            if d > rr + 0.4:
                continue
            x, z = cx + dx, cz + dz
            S(x, y, z, "grass_block" if d < rr - 0.3 else "moss_block")
            S(x, y - 1, z, "dirt")
            depth = int((r + 2) * (1 - (d / (rr + 0.5)) ** 2) * 1.6) + 1
            for i in range(depth):
                S(x, y - 2 - i, z, UNDERSIDE(x, y - 2 - i, z) if i > 1 else "tuff")
            if d > 0.9 and hash01(x, k, z, 5) < 0.3:
                S(x, y - 2 - depth, z, "hanging_roots")
            if hash01(x, k, z, 6) < 0.25 and d < rr - 0.5:
                S(x, y + 1, z, ["allium", "cornflower", "pink_petals[facing=north,flower_amount=2]", "short_grass"][int(hash01(x, k, z, 7) * 4)])
    if k in (1, 4):
        post_lamp(cx + r - 1, y + 1, cz, 2, purple=True)
for (cx, cz, y, r) in ISLETS[:1]:
    pass
# glow-lichen landing marker and a crying obsidian shard on the soul islet
S(37, 65, -118, "amethyst_cluster[facing=up]")
S(39, 65, -116, "pink_petals[facing=north,flower_amount=3]")
MONTEZUMA_7 = (38, 65, -117)
S(38, 65, -117, AIR)
S(44, 66, -115, "short_grass") if False else None
for dy in range(0, 4):
    S(61, 69 + dy, -109, "crying_obsidian") if dy < 2 else None

# ------------------------------------------------------------------------------------------------ back alleys (ruined walls, crates)
for (x, z) in [(-8, -103), (-8, -102), (-8, -101), (11, -103), (12, -104), (12, -103), (0, -112), (1, -112)]:
    if (x, z) in area.top:
        for y in range(G + 1, G + 2 + int(hash01(x, 0, z, 4) * 3)):
            S(x, y, z, "mossy_stone_bricks" if hash01(x, y, z, 5) < 0.5 else "cracked_stone_bricks")
for (x, z) in [(-1, -101), (6, -104), (10, -99), (3, -110), (-7, -110)]:
    S(x, G + 1, z, "barrel[facing=up]")
    S(x + 1, G + 1, z, "hay_block[axis=y]") if hash01(x, 1, z, 6) < 0.5 else None
reserve(-9, -112, 13, -97, 0)
# the south alley between the Murder House and the cliff
for (x, z) in [(-20, -44), (-16, -45), (-12, -42)]:
    if (x, z) in area.top:
        barrel(x, G + 1, z)
for z in range(-44, -40):
    pass

# ------------------------------------------------------------------------------------------------ trees and ground scatter
def town_tree(x, z, h, wood, leaves):
    y = T(x, z) + 1
    for i in range(h):
        S(x, y + i, z, f"{wood}[axis=y]")
    for dy in range(-1, 3):
        r = 3 - (1 if dy == 2 else 0)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dz * dz <= r * r + 1 and hash01(x + dx, y + h + dy, z + dz, 3) > 0.12:
                    if free(x + dx, y + h + dy, z + dz):
                        S(x + dx, y + h + dy, z + dz, f"{leaves}[persistent=true]")


cand = [(x, z) for x in range(X0 + 2, X1 - 3, 4) for z in range(Z0 + 2, Z1 - 3, 4)]
cand.sort(key=lambda p: hash01(p[0], 1, p[1], 61))
trees = []
for (x, z) in cand:
    if len(trees) >= 22:
        break
    c = cols.get((x, z))
    if not c or c[1] < 4 or (x, z) in occupied or abs(T(x, z) - 68) > 4 or c[2] > 1:
        continue
    if any((x + dx, z + dz) in occupied for dx in range(-3, 4) for dz in range(-3, 4)):
        continue
    if any(math.hypot(x - a, z - b) < 7 for a, b in trees):
        continue
    if x <= X0 + 8 and abs(z + 70) <= 6:
        continue
    if z <= Z0 + 8 and abs(x + 15) <= 6:
        continue
    trees.append((x, z))
    pick = hash01(x, 2, z, 62)
    if pick < 0.5:
        town_tree(x, z, 5 + int(hash01(x, 3, z, 62) * 3), "dark_oak_log", "dark_oak_leaves")
    elif pick < 0.8:
        town_tree(x, z, 5 + int(hash01(x, 3, z, 62) * 3), "pale_oak_log", "pale_oak_leaves")
    else:
        town_tree(x, z, 4 + int(hash01(x, 3, z, 62) * 3), "mangrove_log", "mangrove_leaves")

LAYERS = [("short_grass", 0.40), ("fern", 0.50), ("allium", 0.57), ("cornflower", 0.63), ("azure_bluet", 0.68),
          ("pink_petals[facing=north,flower_amount=2]", 0.75), ("pink_petals[facing=east,flower_amount=3]", 0.80),
          ("moss_carpet", 0.86), ("red_mushroom", 0.89), ("bush", 0.94), ("firefly_bush", 0.95)]
placed = 0
for (x, z), (top, e, bump) in cols.items():
    if (x, z) in occupied or hash01(x, 7, z, 31) > 0.55:
        continue
    surf = area.get(x, area.top[(x, z)], z) or ""
    if not surf.endswith(("grass_block", "podzol", "coarse_dirt", "moss_block", "rooted_dirt")):
        continue
    if not empty(x, area.top[(x, z)] + 1, z):
        continue
    r_ = hash01(x, 8, z, 32)
    for blk, thr in LAYERS:
        if r_ < thr:
            S(x, area.top[(x, z)] + 1, z, blk)
            placed += 1
            break

# ------------------------------------------------------------------------------------------------ rift shards: floating chunks past the cliffs
shards = 0
for (sx, sy, sz, sr) in [(72, 62, -110, 3), (73, 78, -70, 3), (70, 58, -40, 3), (50, 55, -26, 3), (8, 66, -28, 2), (-26, 70, -29, 2),
                         (32, 96, -124, 3), (12, 60, -124, 2), (66, 64, -124, 2), (64, 90, -90, 2)]:
    for dx in range(-sr, sr + 1):
        for dz in range(-sr, sr + 1):
            for dy in range(-2 * sr, sr + 1):
                taper = (dx * dx + dz * dz) / (sr * sr) + (dy / (2.2 * sr)) ** 2 if dy < 0 else (dx * dx + dz * dz) / (sr * sr) + (dy / 1.5) ** 2
                if taper <= 1.0 and X0 <= sx + dx <= X1 and Z0 <= sz + dz <= Z1 and (sx + dx, sz + dz) not in cols:
                    r_ = hash01(sx + dx, sy + dy, sz + dz, 41)
                    S(sx + dx, sy + dy, sz + dz, "crying_obsidian" if r_ < 0.15 else "amethyst_block" if r_ < 0.3 else "tuff" if r_ < 0.7 else "deepslate")
    shards += 1

# ------------------------------------------------------------------------------------------------ spots, zones
npcs = [("Barry", BARRY_NPC, 180.0, "inside the Barry Center, behind the desk"),
        ("Cowboy Nick", COWBOY_NPC, 90.0, "beside his rabbit pen, north-west"),
        ("Detective Amog", AMOG_NPC, 90.0, "outside the tape by the Murder House"),
        ("Seymour", SEYMOUR_NPC, -90.0, "in Seymour's shoe shop"),
        ("Seraphine", SERAPHINE_NPC, 90.0, "in her hut east of the plaza"),
        ("Cryptosis", CRYPTOSIS_NPC, 0.0, "at his purple stall by the Barry Center"),
        ("Damia", DAMIA_NPC, 0.0, "behind the auction counter on Barrier Street (30.5, 69, -94.5)"),
        ("Taylor", TAYLOR_NPC, 0.0, "in Taylor's"),
        ("Lonely Avaeikx", AVAEIKX_NPC, 0.0, "sitting on the Lonely Terrace's single bench"),
        ("The Bankster", BANKSTER_NPC, 90.0, "bank corner, next to the Porhtal eye")]
for name, (x, y, z), yaw, note in npcs:
    area.npc(name, x, y, z, yaw, note)
for i, (x, y, z) in enumerate(PROTESTERS, 1):
    area.npc(f"Protester {i}", x, y, z, 0.0, "in front of the Barry Center with a sign")
area.npc("spawn:montezuma_6", *MONTEZUMA_6, 0.0, "in a book at the flower shop's bookshelf (stand at (6, 69, -92))")
area.npc("spawn:montezuma_7", *MONTEZUMA_7, 0.0, "on the Deja Vu Alley islet at (38, 64, -117)")
area.npc("enigma:Rabbit Hat", *SOUL_RABBIT, 0.0, "by the rabbit pen")
area.npc("enigma:Horsing Around", *SOUL_HORSING, 0.0, "deep in the Half-Eaten Cave, behind the hay")
area.npc("spawn:half_eaten_cave", 36, G + 1, -58, 90.0, "the Half-Eaten Cave's mouth, facing in")
area.npc("enigma:Plaza Fleespook", *SOUL_FLEE, 0.0, "hidden nook behind Taylor's")
area.npc("enigma:Plaza Balloons", *SOUL_BALLOONS, 0.0, "on top of the balloon bunch, high over the plaza")
area.npc("enigma:Larva Silk Trail", *SOUL_LARVA, 0.0, "end of the cobweb trail in the north back alley")
area.npc("enigma:Lonely Terrace", *SOUL_TERRACE, 0.0, "far corner of the Lonely Terrace")
area.npc("enigma:Barrier Street Roof", *SOUL_ROOF, 0.0, "on the roof of the Barrier Street building (ladder on its street side), beside a gold block")
area.spots["notes"] += [
    "Fountain at (0, 68, -73); Half-Eaten Cave mouth (35, 69, -58); Barrier Street gate at x 44; Deja Vu islets: " +
    ", ".join(f"({x},{y},{z})" for x, z, y, _ in [(a, b, c, d) for a, b, c, d in ISLETS]),
    "Crossings: West Village at (-30, 68, -70), Dreadfarm at (-15, 68, -120)",
]

for nm, v in area.spots["npcs"].items():
    sx, sy, sz = int(math.floor(v["pos"][0])), int(v["pos"][1]), int(math.floor(v["pos"][2]))
    for dy in (0, 1, 2):
        if area.get(sx, sy + dy, sz) not in (None, AIR):
            S(sx, sy + dy, sz, AIR)


def mob(kind, name, level_, health, damage, xp, coins, mx, every, a, b, drops=None):
    """A Rift mob: `coins` are the Motes it drops (the mod pays Motes for kills in the Rift)."""
    spec = {"type": kind, "name": name, "level": level_, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


SCRIBE = [drop("SCRIBE_CRUX", 1, 1, 1)]
VP_BOXES = [((-30, 0, -120), (19, 200, -31)), ((20, 0, -105), (69, 200, -101)), ((46, 0, -100), (69, 200, -80)),
            ((20, 0, -79), (69, 200, -31)), ((63, 0, -120), (69, 200, -106))]
area.zone("Village Plaza", box=VP_BOXES[0], blocks={}, mobs=[
    mob("minecraft:zombie", "Scribe", 5, 130, 6, 6, 100, 5, 6, (-8, 69, -111), (12, 76, -98), SCRIBE),
    mob("minecraft:zombie", "Scribe", 5, 130, 6, 6, 100, 4, 6, (-28, 69, -44), (-12, 76, -36), SCRIBE),
])
for b in VP_BOXES[1:]:
    area.zone("Village Plaza", box=b)
area.zone("Barrier Street", box=((20, 0, -100), (45, 200, -80)))
area.zone("Deja Vu Alley", box=((20, 0, -120), (62, 200, -106)))
area.save()
print("trees", len(trees), "scatter", placed, "shards", shards, "islets", len(ISLETS))
print("surface at seam x=-30:", {area.surface_y(-30, z) for z in range(-73, -67)}, "z=-120:", {area.surface_y(x, -120) for x in range(-17, -12)})
print("roof y", ry, "soul roof", SOUL_ROOF)
