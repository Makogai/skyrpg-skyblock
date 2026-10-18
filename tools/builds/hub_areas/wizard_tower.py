#!/usr/bin/env python3
"""The Wizard Tower (Hypixel's Hub, the way into the Rift): a floating garden island of strange flowers, amethyst and a small
pond, with a tall slender purpur tower in the middle (7 floors, a spiral stair inside, a pointed deepslate roof with an end-rod
spire). The top floor holds the Rift portal; Grumblefoot sells scrolls on the ground floor. A sky bridge joins the island to the
Wilderness' south coast.

    python tools/builds/hub_areas/wizard_tower.py
"""
import math
from kit import Area, hash01

G = 64                      # surface; players stand at 65 (the village's level)
CX, CZ = 170, 180           # island centre = tower axis
BX = 168                    # the bridge runs north-south at x = BX
# The box starts at z 74 (not 110) so the bridge can span the gap from the Wilderness' south coast (its island ends at z ~82).
area = Area("wizard_tower", (100, 20, 74), (140, 185, 166), version=1)
grass = area.mix({"grass_block": 9, "moss_block": 3, "podzol": 1, "rooted_dirt": 1})
cols = area.island(CX, CZ, 40, top=G, seed=33, surface=grass, hills=1.5, depth=36, wobble=0.14,
                   stone=area.mix({"andesite": 5, "tuff": 4, "calcite": 1, "deepslate": 2, "amethyst_block": 1}))

# ---------------------------------------------------------------- the way in: the bridge from the Wilderness' south coast
north_rim = min(z for (x, z) in cols if x == BX)
area.flatten(BX - 8, north_rim, BX + 8, north_rim + 14, G, "minecraft:grass_block")
area.bridge((BX, 77), (BX, north_rim + 8), y=G, width=3, deck="dark_oak_planks", rail="dark_oak_fence",
            beam="stripped_dark_oak_log")
BRIDGE_END = north_rim + 8

# The tower's yard: a level plaza.
area.flatten(CX - 15, CZ - 15, CX + 15, CZ + 15, G, "minecraft:grass_block")
tiles = area.mix({"deepslate_tiles": 4, "polished_deepslate": 3, "cobbled_deepslate": 1, "polished_tuff": 2})
for x in range(CX - 14, CX + 15):
    for z in range(CZ - 14, CZ + 15):
        d = math.hypot(x - CX, z - CZ)
        if d <= 12.5:
            area.set(x, G, z, tiles(x, G, z))
            area.top[(x, z)] = G
        elif d <= 13.5:
            area.set(x, G, z, "amethyst_block" if hash01(x, 0, z, 4) < 0.5 else "polished_deepslate")
path_tiles = area.mix({"deepslate_tiles": 3, "polished_deepslate": 2, "cobbled_deepslate": 1, "dirt_path": 1})
area.path([(BX, BRIDGE_END), (BX, CZ - 12), (CX, CZ - 10)], width=3, blocks=path_tiles)

# ---------------------------------------------------------------- the tower
R_OUT, R_WALL, R_STAIR = 8.0, 6.5, 4.5     # outer radius, inner radius, where the stair ring starts
FLOORS, H = 7, 12                           # 7 floors of 12 blocks
F = [G + H * i for i in range(FLOORS + 1)]   # floor block y of each floor; F[7] is the top ceiling
TOP = F[FLOORS]
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def polar(x, z):
    dx, dz = x - CX, z - CZ
    return math.hypot(dx, dz), math.degrees(math.atan2(dz, dx)) % 360.0


def near(angle, offset, width):
    a = (angle - offset) % 45.0
    return min(a, 45.0 - a) < width


DOOR_X = (CX - 1, CX, CX + 1)
for x in range(CX - 9, CX + 10):
    for z in range(CZ - 9, CZ + 10):
        d, ang = polar(x, z)
        if d >= R_OUT:
            continue
        wall = d >= R_WALL
        for i in range(FLOORS):
            f = F[i]
            if wall:
                for y in range(f, F[i + 1]):
                    if y == f:
                        block = "gold_block" if near(ang, 0, 4.5) else "amethyst_block"
                    elif y - f < 3 and i == 0:
                        block = "deepslate_tiles" if not near(ang, 0, 4.5) else "polished_blackstone_bricks"
                    elif near(ang, 0, 4.5):
                        block = "purpur_pillar[axis=y]"
                        if y == f + 1 or y == F[i + 1] - 1:
                            block = "gold_block" if (y - f) % 2 else "chiseled_purpur" if False else "gold_block"
                    elif near(ang, 22.5, 4.5) and f + 4 <= y <= f + 8:
                        block = "purple_stained_glass_pane"
                    elif near(ang, 22.5, 4.5) and y in (f + 3, f + 9):
                        block = "purpur_stairs[facing=north,half=bottom]" if False else "polished_blackstone_bricks"
                    else:
                        block = "purpur_block" if hash01(x, y, z, 6) < 0.8 else "purpur_pillar[axis=y]"
                    area.set(x, y, z, block)
            else:
                # floor slab (the stair well is cut later), air above
                area.set(x, f, z, "polished_deepslate" if d > 3.2 else ("amethyst_block" if (x + z) % 2 else "polished_deepslate"))
                for y in range(f + 1, F[i + 1]):
                    area.set(x, y, z, "air")
        # the ceiling of the top floor
        if d < R_OUT:
            area.set(x, TOP, z, "deepslate_tiles" if not wall else "gold_block")
        if wall:
            area.set(x, TOP, z, "gold_block")
        # tower foundation: plinth ring under the ground floor
        if wall and d >= R_WALL + 0.8:
            area.set(x, G - 1, z, "polished_blackstone_bricks")

# a wider footing: three steps round the base
for x in range(CX - 12, CX + 13):
    for z in range(CZ - 12, CZ + 13):
        d, ang = polar(x, z)
        if R_OUT <= d < R_OUT + 1.6 and not (abs(x - CX) <= 3 and z < CZ):
            area.set(x, G + 1, z, "deepslate_tile_slab[type=bottom]")

# spiral stair: ring cells R_STAIR..R_WALL, 12 steps a turn
N = H
for i in range(FLOORS - 1):
    f = F[i]
    for x in range(CX - 7, CX + 8):
        for z in range(CZ - 7, CZ + 8):
            d, ang = polar(x, z)
            if not (R_STAIR <= d < R_WALL):
                continue
            k = int(ang / 360.0 * N)
            th = math.radians(ang)
            tx, tz = -math.sin(th), math.cos(th)
            asc = ("east" if tx > 0 else "west") if abs(tx) > abs(tz) else ("south" if tz > 0 else "north")
            y = f + 1 + k
            area.set(x, y, z, f"purpur_stairs[facing={OPP[asc]},half=bottom]" if k % 2 == 0 else
                     f"polished_blackstone_brick_stairs[facing={OPP[asc]},half=bottom]")
            if k >= 9:
                # the well in the next floor
                area.set(x, F[i + 1], z, "air")
                for yy in range(F[i + 1] + 1, F[i + 1] + 3):
                    if area.get(x, yy, z) not in ("minecraft:air", None) and i + 1 == FLOORS:
                        pass
    # an arch of support: nothing else is needed, stairs hang off the wall ring

# the stair well also needs its floor ring kept solid where k < 9 (it is, from the floor slab)

# the door (north side, toward the bridge): a gold-trimmed arch
for x in DOOR_X:
    for z in range(CZ - 9, CZ - 5):
        for y in range(G + 1, G + 4):
            area.set(x, y, z, "air")
        if area.get(x, G, z) not in (None,):
            area.set(x, G, z, "polished_blackstone_bricks")
for x in (CX - 2, CX + 2):
    for y in range(G + 1, G + 5):
        area.set(x, y, CZ - 8, "gold_block" if y == G + 4 else "polished_blackstone_bricks")
    area.set(x, G + 4, CZ - 8, "gold_block")
for x in DOOR_X:
    area.set(x, G + 4, CZ - 8, "purpur_slab[type=top]")
area.set(CX - 1, G + 3, CZ - 9, "purpur_stairs[facing=east,half=top]")
area.set(CX + 1, G + 3, CZ - 9, "purpur_stairs[facing=west,half=top]")
area.set(CX, G + 3, CZ - 9, "soul_lantern[hanging=true]")
for x in (CX - 3, CX + 3):
    area.fill(x, G + 1, CZ - 10, x, G + 3, CZ - 10, "polished_blackstone_brick_wall")
    area.set(x, G + 4, CZ - 10, "soul_lantern[hanging=false]")
# a few steps up to the door
for i, z in enumerate((CZ - 10, CZ - 11)):
    pass

# balcony on the top floor (south side) with a rail, for the view
BAL = F[FLOORS - 1]
for x in range(CX - 12, CX + 13):
    for z in range(CZ - 12, CZ + 13):
        d, ang = polar(x, z)
        if R_OUT <= d < 10.6 and 30 < ang < 150:
            area.set(x, BAL, z, "purpur_slab[type=top]" if d >= 10.0 else "purpur_block")
            if d >= 10.0:
                area.set(x, BAL + 1, z, "polished_deepslate_wall")
# a door opening onto the balcony
for x in (CX - 1, CX, CX + 1):
    for y in range(BAL + 1, BAL + 4):
        for z in range(CZ + 6, CZ + 9):
            area.set(x, y, z, "air")
area.set(CX, BAL + 3, CZ + 9, "air")

# ---------------------------------------------------------------- roof: a pointed cone of deepslate tiles with amethyst bands
ROOF_H = 24
for x in range(CX - 13, CX + 14):
    for z in range(CZ - 13, CZ + 14):
        d, ang = polar(x, z)
        if 8.0 <= d < 11.6:
            area.set(x, TOP, z, "gold_block" if d >= 11.0 else "deepslate_tiles")
            if d >= 11.0:
                area.set(x, TOP, z, "amethyst_block")
for j in range(ROOF_H):
    r = 10.0 * (1 - j / ROOF_H) ** 1.35 + 0.6
    y = TOP + 1 + j
    for x in range(CX - 11, CX + 12):
        for z in range(CZ - 11, CZ + 12):
            d, ang = polar(x, z)
            if r - 1.7 <= d < r or (r < 2.2 and d < r):
                if j % 8 == 7:
                    block = "amethyst_block"
                elif near(ang, 0, 4.0) and j % 2 == 0:
                    block = "purpur_block"
                else:
                    block = "deepslate_tiles" if hash01(x, y, z, 2) < 0.8 else "deepslate_bricks"
                area.set(x, y, z, block)
SPIRE = TOP + ROOF_H
area.set(CX, SPIRE, CZ, "gold_block")
area.set(CX, SPIRE + 1, CZ, "amethyst_block")
for i in range(2, 8):
    area.set(CX, SPIRE + i, CZ, "end_rod[facing=up]" if i == 7 else "lightning_rod[facing=up]" if False else "end_rod[facing=up]")
# the roof's eave lanterns
for a in range(0, 360, 45):
    lx, lz = int(round(CX + 11 * math.cos(math.radians(a)))), int(round(CZ + 11 * math.sin(math.radians(a))))
    area.set(lx, TOP - 1, lz, "soul_lantern[hanging=true]")
    area.set(lx, TOP, lz, "gold_block")

# ---------------------------------------------------------------- the interior: lights and furniture per floor
def chandelier(f, cx, cz, length=3):
    for i in range(length):
        area.set(cx, f + H - 1 - i, cz, "iron_chain[axis=y]" if i < length - 1 else "soul_lantern[hanging=true]")


for i in range(FLOORS):
    f = F[i]
    for dx, dz in ((0, 0),):
        chandelier(f, CX + dx, CZ + dz, 4 if i != FLOORS - 1 else 2)
    for dx, dz in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
        if math.hypot(dx, dz) < R_STAIR - 0.3 + 0.0:
            area.set(CX + dx, f + 1, CZ + dz, "candle[candles=3,lit=true]" if i % 2 == 0 else "soul_lantern[hanging=false]")

# floor 0: Grumblefoot's scroll shop
f0 = F[0]
area.fill(CX - 4, f0 + 1, CZ + 1, CX - 2, f0 + 1, CZ + 1, "dark_oak_slab[type=bottom]")
area.set(CX - 4, f0 + 2, CZ + 1, "candle[candles=2,lit=true]")
area.set(CX - 2, f0 + 2, CZ + 1, "lectern[facing=north]")
area.set(CX - 3, f0 + 2, CZ + 1, "enchanting_table")
area.set(CX + 3, f0 + 1, CZ + 2, "chest[facing=west]")
area.set(CX + 3, f0 + 1, CZ + 3, "barrel[facing=up]")
area.set(CX + 2, f0 + 1, CZ + 3, "brewing_stand")
# floor 1: alchemy; floor 2: library; floor 3: amethyst chamber; floor 4: observatory; floor 5: study
f1 = F[1]
for dx, dz in ((-3, -2), (-2, -3), (3, -2)):
    area.set(CX + dx, f1 + 1, CZ + dz, "cauldron")
area.set(CX + 2, f1 + 1, CZ + 3, "brewing_stand")
area.set(CX - 2, f1 + 1, CZ + 3, "crafting_table")
area.set(CX, f1 + 1, CZ + 3, "barrel[facing=up]")
f2 = F[2]
for dz in (-3, -2, -1, 1, 2, 3):
    area.fill(CX - 4, f2 + 1, CZ + dz, CX - 4, f2 + 3, CZ + dz, "bookshelf") if abs(dz) <= 1 else None
area.fill(CX + 3, f2 + 1, CZ - 3, CX + 3, f2 + 3, CZ - 1, "bookshelf")
area.set(CX, f2 + 1, CZ + 3, "lectern[facing=north]")
area.set(CX - 2, f2 + 1, CZ - 3, "enchanting_table")
f3 = F[3]
for dx, dz in ((-3, 1), (3, -1), (0, -3), (0, 3)):
    area.set(CX + dx, f3 + 1, CZ + dz, "amethyst_block")
    area.set(CX + dx, f3 + 2, CZ + dz, "amethyst_cluster[facing=up]")
    area.set(CX + dx + (1 if dx <= 0 else -1), f3 + 1, CZ + dz, "small_amethyst_bud[facing=up]")
area.set(CX, f3 + 1, CZ, "budding_amethyst")
area.set(CX, f3 + 2, CZ, "large_amethyst_bud[facing=up]")
f4 = F[4]
for dx in (-2, 2):
    area.set(CX + dx, f4 + 1, CZ - 3, "chest[facing=south]")
area.set(CX, f4 + 1, CZ + 2, "red_carpet")
area.fill(CX - 1, f4 + 1, CZ + 2, CX + 1, f4 + 1, CZ + 2, "purple_carpet")
area.set(CX, f4 + 1, CZ - 3, "lectern[facing=south]")
f5 = F[5]
area.fill(CX - 2, f5 + 1, CZ - 3, CX + 2, f5 + 1, CZ - 3, "purple_carpet")
area.set(CX - 3, f5 + 1, CZ + 2, "bookshelf")
area.set(CX - 3, f5 + 2, CZ + 2, "bookshelf")
area.set(CX + 3, f5 + 1, CZ + 2, "enchanting_table")

# floor 6 (the top): the Rift portal, a crying obsidian frame with purple glass and amethyst around it
F6 = F[FLOORS - 1]
PZ = CZ - 3
# plinth
for x in range(CX - 3, CX + 3):
    for z in range(PZ - 1, PZ + 2):
        area.set(x, F6 + 1, z, "polished_blackstone_bricks")

FX1, FX2 = CX - 2, CX + 1
for x in range(FX1, FX2 + 1):
    for y in range(F6 + 2, F6 + 7):
        edge = x in (FX1, FX2) or y in (F6 + 2, F6 + 6)
        area.set(x, y, PZ, "crying_obsidian" if edge else "purple_stained_glass")
# amethyst round the frame
for y in range(F6 + 2, F6 + 7):
    for x in (FX1 - 1, FX2 + 1):
        area.set(x, y, PZ, "amethyst_block")
for x in range(FX1 - 1, FX2 + 2):
    area.set(x, F6 + 7, PZ, "amethyst_block")
    area.set(x, F6 + 8, PZ, "amethyst_cluster[facing=up]" if hash01(x, 1, PZ) < 0.6 else "air")
for x, y, f in ((FX1 - 2, F6 + 2, "up"), (FX2 + 2, F6 + 2, "up"), (FX1 - 2, F6 + 3, "up")):
    area.set(x, y, PZ, "amethyst_block")
    area.set(x, y + 1, PZ, "amethyst_cluster[facing=up]")
area.set(FX1 - 1, F6 + 7, PZ, "amethyst_cluster[facing=up]")
for dz in (-1, 1):
    for x in (FX1 - 1, FX2 + 1):
        area.set(x, F6 + 2, PZ + dz, "amethyst_cluster[facing=up]")
for x in (FX1 - 2, FX2 + 2):
    for y in range(F6 + 2, F6 + 5):
        area.set(x, y, PZ + 1, "end_rod[facing=up]") if y == F6 + 2 and False else None
area.set(CX - 3, F6 + 1, PZ + 2, "soul_lantern[hanging=false]")
area.set(CX + 2, F6 + 1, PZ + 2, "soul_lantern[hanging=false]")
area.npc("spawn:rift_portal", CX, F6 + 1, PZ + 3, yaw=180.0, note="where a player stands to enter the Rift (faces the portal)")
area.npc("Wizard", CX + 3, F6 + 1, PZ + 2, yaw=90.0, note="the Rift Wizard, beside the portal")

# ground floor NPCs and the way back from the Rift
area.npc("Grumblefoot", CX - 3, G + 1, CZ - 2, yaw=270.0, note="sells the travel scrolls; ground floor, left of the door")
area.npc("spawn:rift_return", CX, G + 1, CZ - 12, yaw=0.0, note="where players land coming back from the Rift, in front of the door facing it")

# ---------------------------------------------------------------- the garden
R_TOWER = 15
at_tower = lambda x, z: math.hypot(x - CX, z - CZ) < 15.5
at_bridge = lambda x, z: abs(x - BX) <= 3 and z < CZ
# the pond, west of the tower
PX, PZ_ = CX - 24, CZ + 6
for x in range(PX - 8, PX + 9):
    for z in range(PZ_ - 8, PZ_ + 9):
        d = math.hypot(x - PX, (z - PZ_) * 1.2)
        if d < 6.0 + 1.2 * math.sin(math.atan2(z - PZ_, x - PX) * 3):
            if (x, z) in area.top:
                area.set(x, G, z, "water")
                area.set(x, G - 1, z, "water")
                area.set(x, G - 2, z, "clay")
                area.top[(x, z)] = G - 1
        elif d < 7.4 and (x, z) in area.top:
            area.set(x, G, z, "mud" if hash01(x, 0, z, 9) < 0.5 else "moss_block")
            area.top[(x, z)] = G
# fix: clear any plants over the pond
for dx in range(-9, 10):
    for z in range(PZ_ - 9, PZ_ + 10):
        pass
area.set(PX, G + 1, PZ_, "lily_pad")
area.set(PX + 2, G + 1, PZ_ - 2, "lily_pad")
area.set(PX - 3, G + 1, PZ_ + 1, "lily_pad")
area.fill(PX - 4, G + 1, PZ_ + 6, PX - 4, G + 2, PZ_ + 6, "polished_tuff")
area.set(PX - 4, G + 3, PZ_ + 6, "soul_lantern[hanging=false]")

# amethyst crystals: clumps with clusters, and a few tall spires
used = []


def clump(cx, cz, seed):
    y = area.surface_y(cx, cz)
    if y is None or at_tower(cx, cz):
        return False
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if abs(dx) + abs(dz) <= 1 + (hash01(cx + dx, 5, cz + dz, seed) < 0.4):
                yy = area.surface_y(cx + dx, cz + dz)
                if yy is None:
                    continue
                h = 1 + int(hash01(cx + dx, 6, cz + dz, seed) * 2)
                for k in range(1, h + 1):
                    area.set(cx + dx, yy + k, cz + dz, "budding_amethyst" if k == 1 and hash01(cx, dx, cz, seed) < 0.3 else "amethyst_block")
                area.set(cx + dx, yy + h + 1, cz + dz, "amethyst_cluster[facing=up]")
        # side buds
    return True


n = 0
for (x, z) in sorted(cols):
    if hash01(x, 31, z, 5) < 0.0035 and not at_tower(x, z) and not at_bridge(x, z) and math.hypot(x - PX, z - PZ_) > 9:
        if math.hypot(x - CX, z - CZ) > 17:
            clump(x, z, n)
            n += 1


def spire(cx, cz, height):
    """A tall amethyst spire with a spiral of steps round it, a soul at the top."""
    y0 = area.surface_y(cx, cz)
    for k in range(1, height + 1):
        area.set(cx, y0 + k, cz, "amethyst_block")
    area.set(cx, y0 + height + 1, cz, "amethyst_cluster[facing=up]")
    for k, (dx, dz) in enumerate([(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)] * 2):
        if k >= height:
            break
        area.set(cx + dx, y0 + 1 + k, cz + dz, "smooth_basalt" if k % 2 else "tuff")
    return y0 + height


SPX, SPZ = CX + 25, CZ - 8
top_y = spire(SPX, SPZ, 9)

# trees: cherry (pink) and dark oak round the rim, away from the tower and the bridge
trees = 0
for (x, z) in sorted(cols):
    if hash01(x, 11, z, 6) > 0.0022 or at_tower(x, z) or at_bridge(x, z) or math.hypot(x - PX, z - PZ_) < 10:
        continue
    if math.hypot(x - SPX, z - SPZ) < 6:
        continue
    y = area.surface_y(x, z)
    if y is None or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    if hash01(x, 12, z) < 0.6:
        area.oak(x, y + 1, z, height=5 + int(hash01(x, 13, z) * 3), radius=3, log="cherry_log", leaves="cherry_leaves", seed=trees)
    else:
        area.oak(x, y + 1, z, height=6, radius=3, log="dark_oak_log", leaves="azalea_leaves", seed=trees)
    trees += 1

# strange flowers and little bits
flowers = ["allium", "azure_bluet", "cornflower", "blue_orchid", "lily_of_the_valley", "wither_rose", "torchflower", "allium",
           "red_tulip", "pink_tulip", "oxeye_daisy"]


def garden(x, z):
    if at_tower(x, z) and math.hypot(x - CX, z - CZ) < 14.2:
        return None
    r = hash01(x, 21, z)
    if r < 0.30:
        return "short_grass"
    if r < 0.42:
        return "fern"
    if r < 0.70:
        return flowers[int(hash01(x, 22, z) * len(flowers)) % len(flowers)]
    if r < 0.76:
        return "pink_petals[flower_amount=3,facing=north]"
    if r < 0.80:
        return "small_amethyst_bud[facing=up]" if False else "azalea"
    if r < 0.84:
        return "brown_mushroom" if hash01(x, 2, z) < 0.5 else "red_mushroom"
    return "short_grass"


area.scatter(cols, 0.38, garden, seed=8, avoid=lambda x, z: at_bridge(x, z) or abs(x - CX) <= 2 and z < CZ)
# tall flowers (lilac, peony, rose bush)
for (x, z) in sorted(cols):
    if hash01(x, 41, z, 3) > 0.05 or at_tower(x, z) or at_bridge(x, z):
        continue
    y = area.surface_y(x, z)
    if y is None or not (area.get(x, y, z) or "").endswith(("grass_block", "moss_block", "podzol")):
        continue
    if area.get(x, y + 1, z) not in (None, "minecraft:air") and not (area.get(x, y + 1, z) or "").endswith(("flower", "short_grass", "fern", "tulip")):
        continue
    kind = ("lilac", "peony", "rose_bush")[int(hash01(x, 42, z) * 3) % 3]
    area.set(x, y + 1, z, f"{kind}[half=lower]")
    area.set(x, y + 2, z, f"{kind}[half=upper]")
# lamp posts along the path
for s in (-1, 1):
    for z in range(BRIDGE_END + 4, CZ - 14, 8):
        px = BX + 3 * s
        yy = area.surface_y(px, z)
        if yy is not None:
            area.fill(px, yy + 1, z, px, yy + 3, z, "polished_deepslate_wall")
            area.set(px, yy + 4, z, "soul_lantern[hanging=false]")

# ---------------------------------------------------------------- the bridge gate, the warp, the fairy souls
GZ = BRIDGE_END - 2
for x in (BX - 3, BX + 3):
    area.fill(x, G + 1, GZ, x, G + 4, GZ, "purpur_pillar[axis=y]")
    area.set(x, G + 5, GZ, "gold_block")
    area.set(x, G + 6, GZ, "amethyst_cluster[facing=up]")
for x in range(BX - 3, BX + 4):
    if x not in (BX - 3, BX + 3):
        area.set(x, G + 5, GZ, "purpur_slab[type=bottom]")
area.set(BX, G + 4, GZ, "iron_chain[axis=y]")
area.set(BX, G + 3, GZ, "soul_lantern[hanging=true]")
area.set(BX + 4, G + 1, GZ + 1, "dark_oak_sign[rotation=4]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"Wizard Tower"', '"The way to the Rift"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.warp("Wizard Tower", BX, G + 1, GZ + 3, yaw=0.0)

area.fairy_soul(CX, TOP + 1 if False else TOP + 1, CZ + 11)        # on the roof's eave, behind the cone
area.fairy_soul(SPX, top_y + 2, SPZ)                                # on top of the amethyst spire in the garden
area.fairy_soul(PX + 1, G - 1, PZ_ + 2)                              # at the bottom of the pond

# ---------------------------------------------------------------- the zone: no mobs, nothing to break
area.zone("Wizard Tower", box=((CX - 52, 24, CZ - 52), (CX + 52, 200, CZ + 52)))
area.save()
