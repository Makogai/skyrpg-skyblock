#!/usr/bin/env python3
"""The Park, west half (Hypixel's Foraging island): Birch Park (the arrival island, a bright birch forest with a lake,
benches and lamps), Spruce Woods (tall spruces, ferns and podzol, a viking camp) and the Howling Cave (a rock hill on its
own small island, north of Spruce Woods, with wolves). Charlie, Vanessa, Kelly, Gustave, the Melancholic Viking and
Old Shaman Nyko stand about. The east bridge continues in the other helper's half.

    python tools/builds/hub_areas/park_west.py
"""
import math
from kit import Area, hash01

G = 64
area = Area("park_west", (-260, 20, -150), (258, 120, 300), version=1, dimension="skyrpg_skyblock:the_park",
            island="The Park", shell=2)
AIR = (None, "minecraft:air")

# ---------------------------------------------------------------- islands
BX, BZ = -70, 0            # Birch Park
SX, SZ = -190, -55         # Spruce Woods
HX, HZ = -190, -133        # Howling Cave island (hill centre is HZ - 1)
birch_cols = area.island(BX, BZ, 52, top=G, seed=31, hills=2.0, depth=36, wobble=0.12,
                         surface=area.mix({"grass_block": 12, "moss_block": 1}),
                         stone=area.mix({"andesite": 5, "tuff": 3, "cobblestone": 2, "calcite": 1}))
spruce_cols = area.island(SX, SZ, 50, top=G, seed=47, hills=2.5, depth=36, wobble=0.12,
                          surface=area.mix({"podzol": 5, "grass_block": 3, "coarse_dirt": 2, "moss_block": 1}),
                          stone=area.mix({"andesite": 4, "tuff": 4, "cobblestone": 2, "cobbled_deepslate": 2}))
cave_cols = area.island(HX, HZ, 15, top=G, seed=5, hills=0, depth=16, wobble=0.08,
                        surface=area.mix({"coarse_dirt": 3, "gravel": 2, "podzol": 2, "moss_block": 1}),
                        stone=area.mix({"andesite": 4, "tuff": 4, "cobbled_deepslate": 3, "cobblestone": 1}))


def landing(px, pz, r, cols):
    """A round landing at y 64 where the island is missing, so a bridge end touches ground."""
    for x in range(px - r, px + r + 1):
        for z in range(pz - r, pz + r + 1):
            if (x - px) ** 2 + (z - pz) ** 2 > r * r or (x, z) in area.top:
                continue
            area.set(x, G, z, "minecraft:grass_block")
            for y in range(G - 1, G - 4, -1):
                area.set(x, y, z, "minecraft:dirt")
            depth = 3 + int((r - math.hypot(x - px, z - pz)) * 1.5)
            for y in range(G - 4, G - 4 - depth, -1):
                area.set(x, y, z, "minecraft:andesite" if hash01(x, y, z, 2) < 0.6 else "minecraft:tuff")
            cols.add((x, z))
            area.top[(x, z)] = G


# bridge ends: Birch Park <-> Spruce Woods, Birch Park <-> east (the other helper), Spruce Woods <-> cave island
BS_A, BS_B = (-117, -21), (-147, -35)
EAST = (-20, 40)
north_edge = min(z for (x, z) in spruce_cols if x == -190)
south_edge = max(z for (x, z) in cave_cols if x == -190)
CB_A, CB_B = (-190, north_edge + 3), (-190, south_edge - 3)
ARR = (-22, 0)
PAD = (-28, 10)
for p, r, cols in ((BS_A, 6, birch_cols), (BS_B, 6, spruce_cols), (EAST, 7, birch_cols), (ARR, 10, birch_cols),
                   (PAD, 6, birch_cols)):
    landing(p[0], p[1], r, cols)
    area.flatten(p[0] - r - 2, p[1] - r - 2, p[0] + r + 2, p[1] + r + 2, G)
landing(HX + 2, HZ + 16, 7, cave_cols)   # the porch where Nyko's campfire is
for p in (CB_A, CB_B):
    area.flatten(p[0] - 5, p[1] - 5, p[0] + 5, p[1] + 5, G)

# ---------------------------------------------------------------- bridges
area.bridge(BS_A, BS_B, y=G)
area.bridge(CB_A, CB_B, y=G, width=3)
area.bridge(EAST, (45, 40), y=G)          # the other helper builds the same line east of x -3

# ---------------------------------------------------------------- level spots
PLAZA = (-48, -3)
area.flatten(PLAZA[0] - 7, PLAZA[1] - 7, PLAZA[0] + 7, PLAZA[1] + 7, G)
LAKE = (-90, -24)
LAKE_R = 10
CAMP = (-172, -42)
area.flatten(CAMP[0] - 20, CAMP[1] - 15, CAMP[0] + 12, CAMP[1] + 14, G)

# ---------------------------------------------------------------- Birch Park paths
pm_b = area.mix({"dirt_path": 5, "coarse_dirt": 1, "gravel": 1})
bp = set()
bp |= area.path([ARR, (-34, 0), PLAZA, (-60, 0), (-74, -6), (-92, -10), (-106, -16), BS_A], width=3, blocks=pm_b)
bp |= area.path([ARR, (-22, 18), (-22, 30), EAST], width=3, blocks=pm_b)
bp |= area.path([ARR, (-24, 6), PAD], width=2, blocks=pm_b)
bp |= area.path([PLAZA, (-52, 18), (-62, 30), (-76, 36)], width=2, blocks=pm_b)
bp |= area.path([(-74, -6), (-70, -22), (-62, -34)], width=2, blocks=pm_b)
bp |= area.path([(-76, 36), (-92, 28), (-104, 16), (-106, -16)], width=2, blocks=pm_b)
bp |= area.path([(-92, -10), (-88, -10), (-82, -16)], width=2, blocks=pm_b)

# ---------------------------------------------------------------- Spruce Woods paths
pm_s = area.mix({"coarse_dirt": 3, "dirt_path": 3, "gravel": 1})
sp = set()
sp |= area.path([BS_B, (-158, -37), CAMP], width=3, blocks=pm_s)
sp |= area.path([CAMP, (-186, -60), (-190, -80), CB_A], width=3, blocks=pm_s)
sp |= area.path([(-190, -80), (-215, -75), (-228, -62)], width=2, blocks=pm_s)
sp |= area.path([CAMP, (-170, -22), (-186, -10), (-200, -8)], width=2, blocks=pm_s)
sp |= area.path([(-186, -60), (-214, -42), (-225, -30)], width=2, blocks=pm_s)
cp = area.path([(-190, south_edge - 3), (-190, HZ + 4)], width=3, blocks=pm_s)

# ---------------------------------------------------------------- Birch Park features
# The lake (Hypixel has squid in it): water one below the grass, a sand rim, a tiny islet in the middle.
lake = set()
for x in range(LAKE[0] - LAKE_R - 2, LAKE[0] + LAKE_R + 3):
    for z in range(LAKE[1] - LAKE_R - 2, LAKE[1] + LAKE_R + 3):
        a = math.atan2(z - LAKE[1], x - LAKE[0])
        r = LAKE_R * (1 + 0.18 * math.sin(3 * a + 1.0) + 0.1 * math.sin(5 * a))
        d = math.hypot(x - LAKE[0], z - LAKE[1])
        if (x, z) not in birch_cols:
            continue
        if d <= r:
            lake.add((x, z))
            area.set(x, G, z, "minecraft:air")
            area.set(x, G - 1, z, "minecraft:water")
            area.set(x, G - 2, z, "minecraft:water")
            area.set(x, G - 3, z, "minecraft:clay" if hash01(x, 1, z) < 0.5 else "minecraft:sand")
            for y in (G - 4, G - 5):
                area.set(x, y, z, "minecraft:dirt")
            area.top[(x, z)] = G - 1
        elif d <= r + 1.6:
            area.set(x, G, z, "minecraft:sand" if hash01(x, 2, z) < 0.7 else "minecraft:gravel")
lake_near = {(x + a, z + b) for (x, z) in lake for a in range(-2, 3) for b in range(-2, 3)}
ISLET = LAKE
for dx in range(-1, 2):
    for dz in range(-1, 2):
        if abs(dx) + abs(dz) <= 1:
            x, z = ISLET[0] + dx, ISLET[1] + dz
            area.set(x, G - 2, z, "minecraft:dirt")
            area.set(x, G - 1, z, "minecraft:dirt")
            area.set(x, G, z, "minecraft:grass_block")
area.set(ISLET[0], G + 1, ISLET[1] - 1, "minecraft:azure_bluet")
area.set(ISLET[0] + 1, G + 1, ISLET[1], "minecraft:short_grass")
area.fairy_soul(ISLET[0], G + 1, ISLET[1])
# a little pier
for i in range(0, 7):
    z = LAKE[1] + 4 + i
    for x in (LAKE[0] - 1, LAKE[0], LAKE[0] + 1):
        if z <= LAKE[1] + 9 or True:
            area.set(x, G, z, "minecraft:birch_planks")
for z in range(LAKE[1] + 4, LAKE[1] + 11, 3):
    for x in (LAKE[0] - 2, LAKE[0] + 2):
        area.set(x, G, z, "minecraft:stripped_birch_log[axis=y]")
        area.set(x, G + 1, z, "minecraft:birch_fence")
        area.set(x, G + 2, z, "minecraft:lantern[hanging=false]")
area.path([(LAKE[0], LAKE[1] + 10), (-92, -10)], width=2, blocks=pm_b)
pier_cells = {(x, z) for x in range(LAKE[0] - 3, LAKE[0] + 4) for z in range(LAKE[1] + 3, LAKE[1] + 12)}

# The plaza: Charlie's spot.
px, pz = PLAZA
for dx in range(-5, 6):
    for dz in range(-5, 6):
        d = math.hypot(dx, dz)
        if d <= 5.2:
            area.set(px + dx, G, pz + dz, "minecraft:birch_planks" if d > 3.2 and (dx + dz) % 2 else "minecraft:polished_andesite")
area.set(px, G, pz, "minecraft:stripped_birch_log[axis=y]")
for dx, dz in ((-5, 0), (5, 0), (0, -5), (0, 5)):
    pass


def lamp(x, z, h=3, fence="birch_fence"):
    y = area.surface_y(x, z)
    if y is None:
        return
    for i in range(1, h):
        area.set(x, y + i, z, f"minecraft:{fence}")
    area.set(x, y + h, z, "minecraft:lantern[hanging=false]")


def bench(x, z, axis, wood="birch"):
    """A bench: two stairs facing south/north or east/west with a post at each end."""
    y = area.surface_y(x, z)
    if y is None:
        return
    face = "north" if axis == "x" else "west"
    for i in (0, 1):
        bx, bz = (x + i, z) if axis == "x" else (x, z + i)
        if area.top.get((bx, bz)) == y:
            area.set(bx, y + 1, bz, f"minecraft:{wood}_stairs[facing={face},half=bottom]")


for dx, dz in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
    lamp(px + dx, pz + dz)
bench(px - 3, pz - 5, "x")
bench(px + 2, pz - 5, "x")
bench(px - 3, pz + 5, "x")
bench(px + 2, pz + 5, "x")
for dx, dz in ((0, -2), (0, 2), (-2, 0), (2, 0)):
    area.set(px + dx, G + 1, pz + dz, "minecraft:birch_slab[type=bottom]" if False else "minecraft:flowering_azalea")
area.npc("Charlie", px, G + 1, pz + 1, yaw=90.0, note="in the plaza; Birch Park's forester")

# lamps and benches along the paths
for (x, z) in ((-30, -3), (-30, 3), (-34, 6), (-60, 3), (-60, -3), (-74, -9), (-82, -13), (-100, -13), (-24, 22), (-24, 34),
               (-66, 31), (-100, 22)):
    lamp(x, z)
for (x, z, ax) in ((-38, -4, "x"), (-38, 3, "x"), (-84, -7, "x"), (-97, -14, "x"), (-26, 26, "z"), (-66, 12, "x"),
                   (LAKE[0] - 7, LAKE[1] - 11, "x"), (LAKE[0] + 6, LAKE[1] - 11, "x")):
    bench(x, z, ax)
area.npc("Vanessa", -86, (area.surface_y(-86, -12) or G) + 1, -12, yaw=0.0, note="on the lake shore, by the bench; a fisher")

# ---------------------------------------------------------------- the launch pad back to the Hub
LX, LZ = PAD
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "minecraft:slime_block" if abs(dx) < 2 and abs(dz) < 2 else "minecraft:stripped_birch_log[axis=x]")
area.set(LX, G + 1, LZ, "minecraft:heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(LX + dx, G + 1, LZ + dz, "minecraft:birch_fence")
    area.set(LX + dx, G + 2, LZ + dz, "minecraft:lantern[hanging=false]")
area.set(LX + 4, G + 1, LZ - 2, "minecraft:birch_sign[rotation=4]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"the Hub"', '""', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.pad("the Hub", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1,
         destination=("skyrpg_hub:hub", (-208.5, 65.0, 116.5), -60.0))
area.spots["notes"].append(f"Hub launch pad centre ({LX}, {G + 1}, {LZ}); pad box ({LX - 1}, {G + 1}, {LZ - 1}) to "
                           f"({LX + 1}, {G + 2}, {LZ + 1})")

# ---------------------------------------------------------------- Spruce Woods: the viking camp
cx, cz = CAMP
# Longhouse, west of the fire pit.
LX1, LX2, LZ1, LZ2 = -190, -178, -55, -47
DOOR = -184
for x in range(LX1, LX2 + 1):
    for z in range(LZ1, LZ2 + 1):
        area.set(x, G, z, "minecraft:spruce_planks" if (x + z) % 4 else "minecraft:dark_oak_planks")
        wall = x in (LX1, LX2) or z in (LZ1, LZ2)
        corner = x in (LX1, LX2) and z in (LZ1, LZ2)
        for y in range(G + 1, G + 5):
            if corner:
                area.set(x, y, z, "minecraft:stripped_spruce_log[axis=y]")
            elif wall:
                if y == G + 1:
                    area.set(x, y, z, "minecraft:cobblestone" if hash01(x, y, z, 3) < 0.6 else "minecraft:mossy_cobblestone")
                elif y == G + 3 and z in (LZ1, LZ2) and (x - LX1) % 4 == 2:
                    area.set(x, y, z, "minecraft:spruce_trapdoor[facing=north,half=top,open=true]" if False else "minecraft:glass_pane")
                else:
                    area.set(x, y, z, f"minecraft:dark_oak_log[axis={'x' if z in (LZ1, LZ2) else 'z'}]")
            else:
                area.set(x, y, z, "minecraft:air")
for y in (G + 1, G + 2, G + 3):
    area.set(DOOR, y, LZ2, "minecraft:air")
    area.set(DOOR + 1, y, LZ2, "minecraft:air")
area.set(DOOR - 1, G + 1, LZ2 + 1, "minecraft:spruce_fence")
area.set(DOOR - 1, G + 2, LZ2 + 1, "minecraft:lantern[hanging=false]")
area.set(DOOR + 2, G + 1, LZ2 + 1, "minecraft:spruce_fence")
area.set(DOOR + 2, G + 2, LZ2 + 1, "minecraft:lantern[hanging=false]")
# Gabled roof along x, overhanging.
for i in range(0, 6):
    zl, zr = LZ1 - 1 + i, LZ2 + 1 - i
    for x in range(LX1 - 1, LX2 + 2):
        if zl > zr:
            break
        if zl == zr:
            area.set(x, G + 5 + i, zl, "minecraft:spruce_slab[type=bottom]")
        else:
            area.set(x, G + 5 + i, zl, "minecraft:spruce_stairs[facing=south,half=bottom]")
            area.set(x, G + 5 + i, zr, "minecraft:spruce_stairs[facing=north,half=bottom]")
            if x in (LX1, LX2):
                for z in range(zl + 1, zr):
                    area.set(x, G + 5 + i, z, "minecraft:spruce_planks")
# Inside: a long table, benches, beds, barrels and a hearth.
for x in range(LX1 + 3, LX2 - 2):
    area.set(x, G + 1, LZ1 + 4, "minecraft:spruce_fence")
    area.set(x, G + 2, LZ1 + 4, "minecraft:spruce_pressure_plate")
    area.set(x, G + 1, LZ1 + 3, "minecraft:spruce_stairs[facing=south,half=bottom]")
    area.set(x, G + 1, LZ1 + 5, "minecraft:spruce_stairs[facing=north,half=bottom]")
for z in (LZ1 + 1, LZ1 + 2):
    area.set(LX1 + 1, G + 1, z, "minecraft:barrel[facing=up]" if z == LZ1 + 1 else "minecraft:chest[facing=east]")
area.set(LX2 - 1, G + 1, LZ1 + 1, "minecraft:red_bed[facing=east,part=foot]")
area.set(LX2 - 1, G + 1, LZ2 - 1, "minecraft:red_bed[facing=east,part=foot]")
area.set(LX2, G + 1, LZ1 + 1, "minecraft:cobblestone")
for x in (-186, -182):
    area.set(x, G + 4, LZ1 + 4, "minecraft:lantern[hanging=true]")
    area.set(x, G + 5, LZ1 + 4, "minecraft:chain[axis=y]") if False else None
# Dragon-prow gable ornaments.
for x in (LX1 - 1, LX2 + 1):
    area.set(x, G + 11, (LZ1 + LZ2) // 2, "minecraft:dark_oak_fence")
    area.set(x, G + 10, (LZ1 + LZ2) // 2, "minecraft:dark_oak_fence")

# Fire pit with log seats: Kelly and Gustave.
FX, FZ = -172, -40
for dx in range(-3, 4):
    for dz in range(-3, 4):
        d = math.hypot(dx, dz)
        if d <= 3.4:
            area.set(FX + dx, G, FZ + dz, "minecraft:gravel" if d > 1.8 else "minecraft:coarse_dirt")
area.set(FX, G + 1, FZ, "minecraft:campfire[lit=true,facing=north]")
for dx, dz, ax in ((-3, 0, "z"), (3, 0, "z"), (0, -3, "x"), (0, 3, "x")):
    area.set(FX + dx, G + 1, FZ + dz, f"minecraft:spruce_log[axis={ax}]")
area.npc("Kelly", FX + 1, G + 1, FZ - 1, yaw=90.0, note="by the viking fire pit; trades for wood")
area.npc("Gustave", FX - 1, G + 1, FZ + 2, yaw=-90.0, note="by the viking fire pit")


# Tents (A-frames of spruce stairs) and supplies.
for tx, tz in ((-165, -54), (-156, -50)):
    area.flatten(tx - 4, tz - 2, tx + 4, tz + 7, G)
    for i in range(5):
        for dx in (-2, -1, 0, 1, 2):
            x, z = tx + dx, tz + i
            if abs(dx) == 2:
                area.set(x, G + 1, z, "minecraft:spruce_stairs[facing=" + ("east" if dx < 0 else "west") + ",half=bottom]")
            elif abs(dx) == 1:
                area.set(x, G + 2, z, "minecraft:spruce_stairs[facing=" + ("east" if dx < 0 else "west") + ",half=bottom]")
                area.set(x, G + 1, z, "minecraft:air")
            else:
                area.set(x, G + 3, z, "minecraft:spruce_slab[type=bottom]")
                area.set(x, G + 1, z, "minecraft:air")
                area.set(x, G + 2, z, "minecraft:air")
    area.set(tx, G + 1, tz + 3, "minecraft:red_bed[facing=south,part=foot]")
    area.set(tx, G + 1, tz + 4, "minecraft:red_bed[facing=south,part=head]")
    area.set(tx + 3, G + 1, tz + 2, "minecraft:barrel[facing=up]")
    area.set(tx + 3, G + 1, tz + 3, "minecraft:chest[facing=west]")
# A weapon / drying rack and a lone viking on the cliff-side bench.
for x in range(-168, -163):
    area.set(x, G + 1, -30, "minecraft:spruce_fence")
    area.set(x, G + 2, -30, "minecraft:spruce_fence") if x in (-168, -164) else None
for x in range(-167, -164):
    area.set(x, G + 3, -30, "minecraft:spruce_fence")
    area.set(x, G + 2, -30, "minecraft:cobweb") if False else None
for x in (-166, -165):
    area.set(x, G + 2, -30, "minecraft:white_wool")
MV = (-158, -36)
area.flatten(MV[0] - 2, MV[1] - 2, MV[0] + 2, MV[1] + 2, G)
area.set(MV[0] + 1, G + 1, MV[1], "minecraft:spruce_stairs[facing=west,half=bottom]")
area.set(MV[0] - 1, G + 1, MV[1] + 2, "minecraft:spruce_fence")
area.set(MV[0] - 1, G + 2, MV[1] + 2, "minecraft:lantern[hanging=false]")
area.npc("Melancholic Viking", MV[0], G + 1, MV[1] - 1, yaw=-90.0, note="sulks on a bench by the camp's east edge; sells Rooted Candy")
for lx, lz in ((-175, -36), (-175, -46), (-160, -40)):
    lamp(lx, lz, fence="spruce_fence")

# ---------------------------------------------------------------- the Howling Cave (a rock hill on the north island)
CHX, CHZ = HX, HZ - 1
DOME_R = 13
CAVE_R = 9.5
rock = area.mix({"andesite": 4, "tuff": 4, "cobbled_deepslate": 3, "cobblestone": 1, "deepslate": 1})
for x in range(CHX - DOME_R - 1, CHX + DOME_R + 2):
    for z in range(CHZ - DOME_R - 1, CHZ + DOME_R + 2):
        d = math.hypot(x - CHX, z - CHZ)
        if d > DOME_R:
            continue
        h = 13 * math.sqrt(1 - (d / DOME_R) ** 2) + 1.2 * (hash01(x, 0, z, 8) - 0.5) + 2 * math.sin(x * 0.4) * math.cos(z * 0.37)
        for y in range(G, G + 1 + max(1, int(h))):
            area.set(x, y, z, rock(x, y, z))
        area.top[(x, z)] = G + max(1, int(h))
# Carve the cavern.
cavern = set()
for x in range(CHX - 11, CHX + 12):
    for z in range(CHZ - 11, CHZ + 12):
        a = math.atan2(z - CHZ, x - CHX)
        rr = CAVE_R * (1 + 0.14 * math.sin(3 * a + 0.5) + 0.08 * math.sin(7 * a))
        d = math.hypot(x - CHX, z - CHZ)
        if d >= rr:
            continue
        ceil = int(8.5 * math.sqrt(1 - (d / rr) ** 2) + 1.5 * hash01(x, 1, z, 9))
        if ceil < 2:
            continue
        cavern.add((x, z))
        area.set(x, G, z, "minecraft:coarse_dirt" if hash01(x, 2, z) < 0.35 else "minecraft:gravel" if hash01(x, 3, z) < 0.3
                 else "minecraft:podzol" if hash01(x, 4, z) < 0.2 else "minecraft:tuff")
        for y in range(G + 1, G + 1 + ceil):
            area.set(x, y, z, "minecraft:air")
        area.top[(x, z)] = G
# The entrance: an arched tunnel south from the cavern, then a rocky porch.
for z in range(CHZ + 6, CHZ + 15):
    for x in range(CHX - 2, CHX + 3):
        for y in range(G + 1, G + 5 - (1 if abs(x - CHX) == 2 else 0)):
            area.set(x, y, z, "minecraft:air")
        area.set(x, G, z, "minecraft:gravel" if hash01(x, 5, z) < 0.5 else "minecraft:coarse_dirt")
        area.top[(x, z)] = G
        cavern.add((x, z)) if z < CHZ + 12 else None
for x in range(CHX - 3, CHX + 4):
    if abs(x - CHX) == 3:
        for y in range(G + 1, G + 6):
            area.set(x, y, CHZ + 14, "minecraft:cobbled_deepslate")
        area.set(x, G + 6, CHZ + 14, "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]") if False else None
# Roof hole (a little light shaft) at the back.
for dx in (0, 1):
    for dz in (0, 1):
        for y in range(G + 7, G + 16):
            area.set(CHX - 3 + dx, y, CHZ - 3 + dz, "minecraft:air")
# Stalagmites / stalactites, moss, bones, soul lanterns.
for (x, z) in sorted(cavern):
    if z >= CHZ + 5 and abs(x - CHX) <= 3:
        continue   # keep the entrance lane clear
    r = hash01(x, 20, z, 2)
    if r < 0.05:
        n = 1 + int(hash01(x, 21, z) * 3)
        for i in range(n):
            thick = "tip" if i == n - 1 else "frustum" if i == n - 2 else "base"
            area.set(x, G + 1 + i, z, f"minecraft:pointed_dripstone[vertical_direction=up,thickness={thick}]")
    elif r < 0.09:
        area.set(x, G + 1, z, "minecraft:moss_carpet")
    elif r < 0.11:
        area.set(x, G + 1, z, "minecraft:bone_block[axis=x]" if hash01(x, 22, z) < 0.5 else "minecraft:skeleton_skull[rotation=%d]" % int(hash01(x, 23, z) * 16))
    elif r < 0.14:
        area.set(x, G + 1, z, "minecraft:brown_mushroom")
    # stalactites from the ceiling
    if hash01(x, 24, z, 3) < 0.07:
        y = G + 1
        while area.get(x, y, z) in AIR and y < G + 12:
            y += 1
        if area.get(x, y, z) not in AIR and y - G > 4:
            n = 1 + int(hash01(x, 25, z) * 3)
            for i in range(n):
                thick = "tip" if i == n - 1 else "frustum" if i == n - 2 else "base"
                area.set(x, y - 1 - i, z, f"minecraft:pointed_dripstone[vertical_direction=down,thickness={thick}]")
    # hanging soul lanterns: a few
    if hash01(x, 26, z, 4) < 0.012:
        y = G + 1
        while area.get(x, y, z) in AIR and y < G + 12:
            y += 1
        if area.get(x, y, z) not in AIR and y - G > 5:
            area.set(x, y - 1, z, "minecraft:chain[axis=y]")
            area.set(x, y - 2, z, "minecraft:soul_lantern[hanging=true]")
# Ledge with the fairy soul at the back, reached by stepping stones.
for (x, y, z) in ((CHX + 5, G + 1, CHZ - 5), (CHX + 4, G + 2, CHZ - 6), (CHX + 3, G + 3, CHZ - 7), (CHX + 2, G + 4, CHZ - 7)):
    area.set(x, y, z, "minecraft:tuff")
    for yy in range(G + 1, y):
        area.set(x, yy, z, "minecraft:tuff")
for dx in range(-1, 3):
    for dz in (-8, -7):
        area.set(CHX + dx, G + 4, CHZ + dz, "minecraft:tuff")
        for yy in range(G + 1, G + 4):
            area.set(CHX + dx, yy, CHZ + dz, "minecraft:tuff")
        for yy in range(G + 5, G + 8):
            area.set(CHX + dx, yy, CHZ + dz, "minecraft:air")
area.set(CHX, G + 5, CHZ - 7, "minecraft:amethyst_cluster[facing=up]")
area.fairy_soul(CHX + 1, G + 5, CHZ - 7)
# Campfire and Old Shaman Nyko at the entrance.
NX, NZ = CHX + 5, CHZ + 16
area.flatten(NX - 4, NZ - 3, NX + 4, NZ + 2, G, "minecraft:podzol")
area.set(NX, G + 1, NZ, "minecraft:campfire[lit=true,facing=west]")
area.set(NX - 2, G + 1, NZ - 1, "minecraft:spruce_log[axis=z]")
area.set(NX - 2, G + 1, NZ + 1, "minecraft:spruce_log[axis=z]")
area.set(NX + 2, G + 1, NZ + 1, "minecraft:barrel[facing=up]")
area.set(NX + 3, G + 1, NZ, "minecraft:soul_lantern[hanging=false]")
area.set(CHX - 4, G + 1, CHZ + 14, "minecraft:soul_lantern[hanging=false]")
area.npc("Old Shaman Nyko", NX + 1, G + 1, NZ, yaw=90.0, note="shop; at the campfire by the Howling Cave's entrance")
area.spots["notes"].append(f"Howling Cave: entrance south at ({CHX}, {G + 1}, {CHZ + 14}), cavern floor y {G + 1}, "
                           f"centre ({CHX}, {CHZ}); roof light shaft at ({CHX - 3}, {CHZ - 3})")

# ---------------------------------------------------------------- trees and undergrowth
def dilate(cells, r):
    out = set()
    for (x, z) in cells:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                out.add((x + dx, z + dz))
    return out


b_near = dilate(bp, 2) | dilate(pier_cells, 1) | lake_near
s_near = dilate(sp | cp, 2)
FAIRY_B = (-58, 30)        # tall birch with a soul in the canopy
FAIRY_S = (-218, -78)      # giant spruce with a soul on top


def birch_blocked(x, z):
    if (x, z) in b_near or math.hypot(x - PLAZA[0], z - PLAZA[1]) < 8:
        return True
    for p, r in ((BS_A, 9), (EAST, 9), (ARR, 13), (PAD, 8), (FAIRY_B, 4)):
        if math.hypot(x - p[0], z - p[1]) < r:
            return True
    return False


def spruce_blocked(x, z):
    if (x, z) in s_near:
        return True
    if CAMP[0] - 21 <= x <= CAMP[0] + 12 and CAMP[1] - 16 <= z <= CAMP[1] + 15:
        return True
    if (-190 - 6 <= x <= -190 + 6) and z < -90:
        return True
    for p, r in ((BS_B, 9), (CB_A, 8), (FAIRY_S, 4)):
        if math.hypot(x - p[0], z - p[1]) < r:
            return True
    return False


# Birch trees: slim white trunks, light canopies, with clearings from noise.
placed = []
for (x, z) in sorted(birch_cols, key=lambda p: hash01(p[0], 31, p[1], 8)):
    y = area.surface_y(x, z)
    if y != G and y != G + 1 and y != G + 2:
        continue
    if math.hypot(x - BX, z - BZ) > 52 or x < -124 or birch_blocked(x, z) or area.get(x, y + 1, z) not in AIR:
        continue
    if not area.get(x, y, z, ).endswith(("grass_block", "moss_block")):
        continue
    if hash01(x, 33, z, 3) > 0.16:
        continue
    if any(math.hypot(q[0] - x, q[1] - z) < 4.6 for q in placed):
        continue
    h = 7 + int(hash01(x, 34, z) * 5)
    area.oak(x, y + 1, z, height=h, radius=2 + int(hash01(x, 35, z) * 2), log="birch_log", leaves="birch_leaves", seed=x * 3 + z)
    placed.append((x, z))
print("birch trees:", len(placed))
# The tall birch with a fairy soul in its canopy.
fx, fz = FAIRY_B
fy = area.surface_y(fx, fz) or G
area.oak(fx, fy + 1, fz, height=17, radius=3, log="birch_log", leaves="birch_leaves", seed=9)
area.set(fx, fy + 18, fz, "minecraft:birch_leaves[persistent=true]")
area.set(fx, fy + 19, fz, "minecraft:air")
area.fairy_soul(fx, fy + 19, fz)

# Spruce trees
sp_placed = []
for (x, z) in sorted(spruce_cols, key=lambda p: hash01(p[0], 41, p[1], 9)):
    y = area.surface_y(x, z)
    if y is None or y < G or y > G + 3 or math.hypot(x - SX, z - SZ) > 47 or x > -138 or spruce_blocked(x, z):
        continue
    if area.get(x, y + 1, z) not in AIR or hash01(x, 43, z, 4) > 0.2:
        continue
    if any(math.hypot(q[0] - x, q[1] - z) < 4.4 for q in sp_placed):
        continue
    area.spruce(x, y + 1, z, height=10 + int(hash01(x, 44, z) * 8), seed=x * 5 + z)
    sp_placed.append((x, z))
print("spruce trees:", len(sp_placed))
fx, fz = FAIRY_S
fy = area.surface_y(fx, fz) or G
area.spruce(fx, fy + 1, fz, height=24, seed=77)
area.fairy_soul(fx, fy + 26, fz)
# A few dead spruces on the cave island.
for (x, z) in sorted(cave_cols):
    y = area.top.get((x, z))
    if y == G and hash01(x, 51, z, 2) < 0.012 and math.hypot(x - CHX, z - CHZ) > 14 and (x, z) not in cp and z < CHZ + 10:
        area.dead_tree(x, G + 1, z, height=5 + int(hash01(x, 52, z) * 4), log="spruce_log", seed=x)


def birch_under(x, z):
    r = hash01(x, 61, z)
    return ("short_grass" if r < 0.4 else "fern" if r < 0.5 else "dandelion" if r < 0.58 else "azure_bluet" if r < 0.65 else
            "oxeye_daisy" if r < 0.72 else "lily_of_the_valley" if r < 0.78 else "white_tulip" if r < 0.83 else
            "birch_sapling[stage=0]" if r < 0.88 else "birch_leaves[persistent=true]" if r < 0.92 else "brown_mushroom")


def spruce_under(x, z):
    r = hash01(x, 62, z)
    return ("fern" if r < 0.45 else "short_grass" if r < 0.6 else "spruce_sapling[stage=0]" if r < 0.7 else
            "brown_mushroom" if r < 0.8 else "sweet_berry_bush[age=3]" if r < 0.85 else "spruce_leaves[persistent=true]"
            if r < 0.9 else "red_mushroom")


area.scatter({c for c in birch_cols if c not in lake}, 0.4, birch_under, seed=11,
             avoid=lambda x, z: (x, z) in bp or (x, z) in pier_cells)
area.scatter({c for c in spruce_cols}, 0.42, spruce_under, seed=12, avoid=lambda x, z: (x, z) in sp)
area.scatter({c for c in cave_cols if c not in cp}, 0.1, lambda x, z: "fern" if hash01(x, 63, z) < 0.6 else "brown_mushroom",
             seed=13)

# ---------------------------------------------------------------- arrival, warp, zones
area.arrival(-22, G + 1, 0, 90.0)
area.warp("The Park", -22, G + 1, 0, yaw=90.0)

area.zone("Birch Park", box=((-135, 20, -65), (-3, 139, 70)),
          blocks={"minecraft:birch_log": {"becomes": "minecraft:air", "regrow": 20},
                  "minecraft:birch_leaves": {"becomes": "minecraft:air", "regrow": 15}})
area.zone("Spruce Woods", box=((-255, 20, -112), (-136, 139, 30)),
          blocks={"minecraft:spruce_log": {"becomes": "minecraft:air", "regrow": 20},
                  "minecraft:spruce_leaves": {"becomes": "minecraft:air", "regrow": 15}})
F1 = [CHX - 9, G + 1, CHZ - 9]
F2 = [CHX + 9, G + 6, CHZ + 8]
area.zone("Howling Cave", box=((-212, 20, -150), (-168, 139, -113)), mobs=[
    {"type": "minecraft:wolf", "name": "Pack Spirit", "level": 30, "health": 6000, "damage": 300, "xp": 22, "coins": 11,
     "max": 6, "every": 6, "indoors": True, "from": F1, "to": F2},
    {"type": "minecraft:wolf", "name": "Howling Spirit", "level": 35, "health": 7000, "damage": 450, "xp": 22, "coins": 11,
     "max": 3, "every": 10, "indoors": True, "from": F1, "to": F2},
    {"type": "minecraft:wolf", "name": "Soul of the Alpha", "level": 55, "health": 31150, "damage": 1425, "xp": 50,
     "coins": 50, "max": 1, "every": 120, "indoors": True, "from": [CHX - 6, G + 1, CHZ - 7], "to": [CHX + 6, G + 6, CHZ + 3]},
])
area.save()
