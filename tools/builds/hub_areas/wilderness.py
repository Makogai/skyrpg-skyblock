#!/usr/bin/env python3
"""The Wilderness (Hypixel's Hub, east of the village): a wild, gentle island of meadows and birch/oak woods with the
Fairy Pond (Tia the Fairy), a fishing lake with a dock and the Fisherman's Hut, the Tavern (Shifty, Lucius), the Dark
Auction house (Sirius) and a broken soul-sand cage as a secret.

    python tools/builds/hub_areas/wilderness.py
"""
import math

from kit import Area, hash01

G = 64                      # surface; players stand at 65 (the village's level)
CX, CZ = 185, 15            # island centre
area = Area("wilderness", (96, 20, -60), (175, 90, 160), version=1)
meadow = area.mix({"grass_block": 12, "moss_block": 1, "rooted_dirt": 1, "coarse_dirt": 1})
# A shape factor that pushes the west rim out so the island meets the bridge end at (124, 4).
cols = area.island(CX, CZ, 62, top=G, seed=21, surface=meadow, hills=2.0, depth=44, wobble=0.12,
                   shape=lambda x, z: 1.0 + 0.06 * max(0.0, (CX - x) / 62) * max(0.0, 1 - abs(z - 3) / 14))

# ---------------------------------------------------------------- the way in
area.flatten(122, -4, 142, 10, G, "minecraft:grass_block")
area.bridge((95, 0), (124, 4), y=G, width=3)
# Make sure there is ground under the bridge's landing: extend land columns up to x = 125.
for x in range(122, 128):
    for z in range(0, 9):
        if (x, z) not in area.top:
            area.set(x, G, z, "minecraft:grass_block")
            for y in range(G - 1, G - 4, -1):
                area.set(x, y, z, "minecraft:dirt")
            for y in range(G - 4, G - 8, -1):
                area.set(x, y, z, "minecraft:andesite")
            area.top[(x, z)] = G
            cols.add((x, z))

reserved = []     # (x1, z1, x2, z2) rectangles kept free of trees and boulders


def reserve(x1, z1, x2, z2, m=2):
    reserved.append((min(x1, x2) - m, min(z1, z2) - m, max(x1, x2) + m, max(z1, z2) + m))


def is_reserved(x, z):
    return any(a <= x <= c and b <= z <= d for a, b, c, d in reserved)


def disc(cx, cz, r, ry=None):
    ry = ry or r
    return [(x, z) for x in range(int(cx - r - 1), int(cx + r + 2)) for z in range(int(cz - ry - 1), int(cz + ry + 2))
            if ((x - cx) / r) ** 2 + ((z - cz) / ry) ** 2 <= 1]


def carve(cells, floor, depth=4):
    """Water `depth` deep (from the surface down), a floor of `floor(x, z)` under it."""
    for (x, z) in cells:
        if (x, z) not in area.top:
            continue
        for y in range(G - depth + 1, G + 1):
            area.set(x, y, z, "minecraft:water")
        area.set(x, G - depth, z, floor(x, z))
        for y in range(G + 1, G + 4):
            area.set(x, y, z, "minecraft:air")
        area.top[(x, z)] = G - depth


# ---------------------------------------------------------------- the Fairy Pond (south-west) with Tia's rock
FPX, FPZ = 150, 38
area.flatten(FPX - 12, FPZ - 12, FPX + 12, FPZ + 12, G, "minecraft:grass_block")
pond = disc(FPX, FPZ, 8.5, 7.5)
clay_sand = area.mix({"clay": 3, "sand": 3, "gravel": 1, "moss_block": 1})
carve(pond, lambda x, z: clay_sand(x, G, z), depth=4)
pond_set = set(pond)
reserve(FPX - 11, FPZ - 10, FPX + 11, FPZ + 10, 3)
# Tia's rock: a mossy little island in the middle.
for (x, z) in disc(FPX, FPZ, 2.4):
    for y in range(G - 3, G):
        area.set(x, y, z, "mossy_cobblestone" if hash01(x, y, z, 4) < 0.5 else "moss_block")
    area.set(x, G, z, "moss_block")
    area.top[(x, z)] = G
area.set(FPX - 1, G + 1, FPZ + 1, "flowering_azalea")
area.set(FPX + 1, G + 1, FPZ - 1, "flowering_azalea")
area.set(FPX + 1, G + 1, FPZ + 1, "pink_tulip")
area.set(FPX - 1, G + 1, FPZ - 1, "azure_bluet")
area.set(FPX, G + 1, FPZ - 1, "lily_of_the_valley")
area.npc("Tia the Fairy", FPX, G + 1, FPZ, yaw=180.0, note="the Fairy Pond; sells Fairy Souls rewards / exchanges souls")
# lily pads, glow lichen on the pond floor and rocks, little glow in the dark
for (x, z) in pond:
    if (x, z) in area.top and abs(x - FPX) + abs(z - FPZ) > 3 and area.get(x, G, z) == "minecraft:water":
        r = hash01(x, 31, z, 2)
        if r < 0.16:
            area.set(x, G + 1, z, "lily_pad")
        elif r < 0.24:
            area.set(x, G - 3, z, "glow_lichen[down=true,waterlogged=true]")
        elif r < 0.27:
            area.set(x, G - 3, z, "sea_lantern")
# azalea trees and flowers around the rim
for (tx, tz) in ((FPX - 11, FPZ - 4), (FPX + 10, FPZ + 6), (FPX - 4, FPZ + 10)):
    area.oak(tx, G + 1, tz, height=4, radius=2, log="oak_log", leaves="flowering_azalea_leaves", seed=tx)
    area.set(tx + 1, G + 1, tz + 1, "azalea")
for (x, z) in sorted(disc(FPX, FPZ, 12, 11)):
    if (x, z) in pond_set or (x, z) not in area.top:
        continue
    if area.get(x, G, z) != "minecraft:grass_block" or area.get(x, G + 1, z) not in (None, "minecraft:air"):
        continue
    r = hash01(x, 41, z, 3)
    if r < 0.30:
        area.set(x, G + 1, z, ["pink_tulip", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley",
                               "pink_petals[flower_amount=3,facing=north]", "flowering_azalea", "peony"][int(r * 100) % 8]
                 if int(r * 100) % 8 != 7 else "azalea")
# ---------------------------------------------------------------- the fishing lake (south-east), dock and Fisherman's Hut
LX, LZ = 217, 32
area.flatten(LX - 20, LZ - 22, LX + 20, LZ + 20, G, "minecraft:grass_block")
lake = disc(LX, LZ, 15, 13)
lake_floor = area.mix({"sand": 5, "clay": 2, "gravel": 2, "dirt": 1})
carve(lake, lambda x, z: lake_floor(x, G, z), depth=5)
lake_set = set(lake)
reserve(LX - 18, LZ - 17, LX + 18, LZ + 16, 2)
for (x, z) in lake:
    if (x, z) in area.top and area.get(x, G, z) == "minecraft:water":
        r = hash01(x, 51, z, 2)
        if r < 0.07:
            area.set(x, G + 1, z, "lily_pad")
        elif r < 0.12:
            area.set(x, G - 4, z, "seagrass")
        elif r < 0.14:
            area.set(x, G - 4, z, "tall_seagrass[half=lower]")
            area.set(x, G - 3, z, "tall_seagrass[half=upper]")
# reeds on the shore
for (x, z) in sorted(disc(LX, LZ, 17, 15)):
    if (x, z) in lake_set or (x, z) not in area.top:
        continue
    near_water = any((x + dx, z + dz) in lake_set for dx in (-1, 0, 1) for dz in (-1, 0, 1))
    if near_water and hash01(x, 52, z, 2) < 0.3 and area.get(x, G, z) == "minecraft:grass_block":
        area.set(x, G + 1, z, "short_grass")
        if hash01(x, 53, z) < 0.3:
            area.set(x, G + 1, z, "sugar_cane[age=3]")
            area.set(x, G, z, "minecraft:dirt")

# the stream: from the pond east to the lake, meandering
stream = []
for i in range(0, 52):
    x = FPX + 8 + i
    z = int(round(FPZ - 4 - i * 0.11 + 2.2 * math.sin(i * 0.22)))
    stream.append((x, z))
for (x, z) in stream:
    for dz in (0, 1):
        if (x, z + dz) in area.top and (x, z + dz) not in pond_set and ((x - 217) / 15.2) ** 2 + ((z + dz - 32) / 13.2) ** 2 > 1:
            area.set(x, G - 1, z + dz, "minecraft:gravel")
            area.set(x, G, z + dz, "minecraft:water")
            area.set(x, G + 1, z + dz, "minecraft:air")
            area.top[(x, z + dz)] = G - 1
reserve(FPX + 8, min(z for _, z in stream), FPX + 59, max(z for _, z in stream) + 1, 1)

# The Fisherman's Hut on stilts (door on the north side, a back door onto the dock to the south).
HX1, HX2, HZ1, HZ2 = 211, 221, 15, 24
reserve(HX1, HZ1, HX2, HZ2, 3)


def building(x1, z1, x2, z2, base, beam, wall, floor, roof_stair, roof_slab, door_x, door_z_side="south", stories=1,
             door="spruce_door", window="glass_pane", wall_h=None, corner=None, roof_gable=None):
    """A timber-frame building: foundation row, frame, windows, floors, gabled roof along x. Returns floor heights."""
    corner = corner or beam
    roof_gable = roof_gable or wall
    wall_h = wall_h or (5 if stories == 1 else 10)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            area.set(x, G, z, floor)
            edge = x in (x1, x2) or z in (z1, z2)
            is_corner = x in (x1, x2) and z in (z1, z2)
            for y in range(G + 1, G + wall_h + 1):
                if not edge:
                    area.set(x, y, z, "minecraft:air")
                    continue
                along = x - x1 if z in (z1, z2) else z - z1
                win_rows = (G + 3, G + 4) if y in (G + 3, G + 4) else (G + 8, G + 9) if y in (G + 8, G + 9) and stories == 2 else None
                if is_corner:
                    area.set(x, y, z, f"{corner}[axis=y]")
                elif y <= G + 2:
                    area.set(x, y, z, base)
                elif along % 4 == 0:
                    area.set(x, y, z, f"{beam}[axis=y]")
                elif win_rows and along % 4 == 2:
                    area.set(x, y, z, window)
                elif y == G + 6 and stories == 2:
                    area.set(x, y, z, f"{beam}[axis={'x' if z in (z1, z2) else 'z'}]")
                else:
                    area.set(x, y, z, wall)
    if stories == 2:
        for x in range(x1 + 1, x2):
            for z in range(z1 + 1, z2):
                area.set(x, G + 6, z, floor)
    # roof, a gable along x
    top = G + wall_h + 1
    for i in range(0, 10):
        zl, zr = z1 - 1 + i, z2 + 1 - i
        if zl > zr:
            break
        for x in range(x1 - 1, x2 + 2):
            if zl == zr:
                area.set(x, top + i, zl, roof_slab)
            else:
                area.set(x, top + i, zl, f"{roof_stair}[facing=south]")
                area.set(x, top + i, zr, f"{roof_stair}[facing=north]")
        if zl < zr:
            for z in range(zl + 1, zr):
                if z1 <= z <= z2:
                    for x in (x1, x2):
                        area.set(x, top + i, z, roof_gable)
    # door
    dz = z2 if door_z_side == "south" else z1
    face = door_z_side
    area.set(door_x, G + 1, dz, f"{door}[facing={face},half=lower,hinge=left]")
    area.set(door_x, G + 2, dz, f"{door}[facing={face},half=upper,hinge=left]")


def sign(x, y, z, facing, lines, wall=True, kind="oak"):
    area.set(x, y, z, f"{kind}_wall_sign[facing={facing}]",
             {"id": "minecraft:sign", "front_text": {"messages": ['""'] + [f'"{t}"' for t in lines] + ['""'] * (3 - len(lines)),
                                                      "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


# hut: stilts first (where there is water or air under it)
for x in range(HX1, HX2 + 1):
    for z in range(HZ1, HZ2 + 1):
        if (x - HX1) % 5 == 0 and (z - HZ1) % 3 == 0 or (x, z) in lake_set and x in (HX1, HX2) and z in (HZ1, HZ2):
            for y in range(G - 5, G):
                if area.get(x, y, z) in (None, "minecraft:air", "minecraft:water"):
                    area.set(x, y, z, "spruce_log[axis=y]")
for x in range(HX1 - 1, HX2 + 2):
    for z in range(HZ1 - 1, HZ2 + 2):
        if x in (HX1 - 1, HX2 + 1) or z in (HZ1 - 1, HZ2 + 1):
            if (x, z) in area.top:
                area.set(x, G, z, "spruce_planks")
        else:
            area.set(x, G, z, "spruce_planks")
building(HX1, HZ1, HX2, HZ2, "stripped_spruce_log[axis=y]", "spruce_log", "oak_planks", "spruce_planks", "spruce_stairs",
         "spruce_slab[type=bottom]", 216, "north", stories=1, wall_h=5, window="glass_pane")
# restore the planks deck border (building() overwrote the floor inside only)
area.set(216, G + 1, HZ2, "spruce_door[facing=south,half=lower,hinge=left]")
area.set(216, G + 2, HZ2, "spruce_door[facing=south,half=upper,hinge=left]")
area.set(216, G + 1, HZ1, "air")
area.set(216, G + 2, HZ1, "air")
area.set(216, G + 1, HZ1, "spruce_door[facing=north,half=lower,hinge=left]")
area.set(216, G + 2, HZ1, "spruce_door[facing=north,half=upper,hinge=left]")
# inside
for (x, z) in ((212, 16), (213, 16), (220, 16), (220, 17)):
    area.set(x, G + 1, z, "barrel[facing=up]")
area.set(212, G + 2, 16, "lantern[hanging=false]")
area.set(220, G + 1, 22, "crafting_table")
area.set(219, G + 1, 22, "cauldron")
area.set(212, G + 1, 22, "chest[facing=north]")
area.set(220, G + 1, 19, "composter[level=4]")
for x in (214, 218):
    area.set(x, G + 5, 19, "lantern[hanging=true]")
# nets of cobweb hanging off the stilts, drying rods on the outside of the hut
for x in range(HX1 + 1, HX2, 2):
    area.set(x, G + 1, HZ2 + 1, "oak_fence")
    area.set(x, G + 2, HZ2 + 1, "cobweb" if x % 4 == 1 else "oak_fence")
for z in range(HZ1 + 2, HZ2 - 1, 3):
    area.set(HX1 - 1, G + 1, z, "barrel[facing=up]")
sign(216, G + 4, HZ1 - 1, "north", ["Fisherman's", "Hut"])
# a south deck out the back door and the dock to the lake, ending in a T
for z in range(HZ2 + 1, 41):
    for x in range(215, 218):
        area.set(x, G, z, "spruce_planks")
        if (x == 215 or x == 217) and z % 4 == 0:
            for y in range(G - 5, G):
                area.set(x, y, z, "spruce_log[axis=y]")
            area.set(x, G + 1, z, "spruce_fence")
            area.set(x, G + 2, z, "lantern[hanging=false]" if z % 8 == 0 else "spruce_fence")
for x in range(212, 222):
    for z in range(40, 43):
        area.set(x, G, z, "spruce_planks")
for x in (212, 221):
    for z in (40, 42):
        for y in range(G - 5, G):
            area.set(x, y, z, "spruce_log[axis=y]")
        area.set(x, G + 1, z, "spruce_fence")
        area.set(x, G + 2, z, "lantern[hanging=false]")
area.set(216, G + 1, 42, "barrel[facing=up]")
area.set(214, G + 1, 41, "oak_trapdoor[facing=north,half=bottom,open=false]")
area.npc("Fisherman Gerald", 216, G + 1, 39, yaw=0.0, note="on the dock; fishing tips and rods")
# a small rowboat tied to the dock
area.set(220, G + 1, 38, "oak_boat") if False else None
area.fairy_soul(216, G - 2, 31)     # under the dock, in the water between the posts
area.set(216, G - 4, 31, "sand")

# ---------------------------------------------------------------- the Tavern (north-west of the middle)
TX1, TX2, TZ1, TZ2 = 148, 164, -20, -4
area.flatten(TX1 - 3, TZ1 - 3, TX2 + 3, TZ2 + 3, G, "minecraft:grass_block")
reserve(TX1, TZ1, TX2, TZ2, 3)
building(TX1, TZ1, TX2, TZ2, "stone_bricks", "dark_oak_log", "birch_planks", "oak_planks", "dark_oak_stairs",
         "dark_oak_slab[type=bottom]", 156, "south", stories=2, door="oak_door", roof_gable="dark_oak_planks")
sign(156, G + 5, TZ2 + 1, "south", ["The Tavern"])
for x in (152, 160):
    area.set(x, G + 4, TZ2 + 1, "lantern[hanging=false]")
    area.set(x, G + 3, TZ2 + 1, "oak_fence")
# ground floor: a bar along the back, tables, barrels, lanterns
for x in range(TX1 + 2, TX1 + 9):
    area.set(x, G + 1, TZ1 + 2, "barrel[facing=south]")
    area.set(x, G + 2, TZ1 + 2, "oak_pressure_plate" if x % 2 else "flower_pot")
for x in range(TX1 + 1, TX2):
    area.set(x, G + 5, TZ1 + 1, "air")
for (x, z) in ((TX1 + 1, TZ1 + 5), (TX1 + 1, TZ1 + 6), (TX1 + 1, TZ1 + 7)):
    area.set(x, G + 1, z, "barrel[facing=east]")
for (x, z) in ((158, -16), (161, -12), (153, -9)):
    area.set(x, G + 1, z, "oak_fence")
    area.set(x, G + 2, z, "oak_pressure_plate")
    area.set(x - 1, G + 1, z, "oak_stairs[facing=east,half=bottom]")
    area.set(x + 1, G + 1, z, "oak_stairs[facing=west,half=bottom]")
for x in range(TX1 + 3, TX2 - 1, 4):
    for z in (TZ1 + 3, TZ2 - 3):
        area.set(x, G + 5, z, "lantern[hanging=true]")
area.set(TX2 - 1, G + 1, TZ1 + 1, "furnace[facing=south,lit=false]")
area.set(TX2 - 2, G + 1, TZ1 + 1, "cauldron")
area.set(TX2 - 1, G + 1, TZ1 + 2, "barrel[facing=up]")
# the stairs up (west wall), floor 2: bookshelves, beds, a table
for i in range(6):
    area.set(TX1 + 1, G + 1 + i, TZ1 + 2 + i, "oak_stairs[facing=south,half=bottom]")
    if i < 5:
        area.set(TX1 + 1, G + 6, TZ1 + 2 + i, "air")
for z in range(TZ1 + 2, TZ1 + 9):
    area.set(TX1 + 1, G + 7, z, "oak_fence") if z in (TZ1 + 2, TZ1 + 3, TZ1 + 4, TZ1 + 5, TZ1 + 6) else None
for x in range(TX1 + 3, TX1 + 8):
    area.set(x, G + 7, TZ1 + 1, "bookshelf")
    area.set(x, G + 8, TZ1 + 1, "bookshelf")
for x in (TX2 - 3, TX2 - 1):
    area.set(x, G + 7, TZ1 + 1, "red_bed[facing=south,part=foot]")
    area.set(x, G + 7, TZ1 + 2, "red_bed[facing=south,part=head]")
area.set(158, G + 7, -12, "oak_fence")
area.set(158, G + 8, -12, "oak_pressure_plate")
area.set(158, G + 7, -13, "lantern[hanging=false]")
area.set(TX2 - 1, G + 7, TZ2 - 1, "barrel[facing=up]")
area.set(156, G + 7, TZ2 - 1, "chest[facing=north]")
area.npc("Shifty", 156, G + 1, -8, yaw=180.0, note="the Tavern; sells shady wares, Lucius's mate")
area.npc("Lucius", TX1 + 5, G + 1, TZ1 + 4, yaw=0.0, note="the Tavern bartender; Dark Auction announcements")

# ---------------------------------------------------------------- the Dark Auction house (next to the Tavern)
DX1, DX2, DZ1, DZ2 = 170, 186, -20, -4
area.flatten(DX1 - 3, DZ1 - 3, DX2 + 3, DZ2 + 3, G, "minecraft:coarse_dirt")
reserve(DX1, DZ1, DX2, DZ2, 3)
building(DX1, DZ1, DX2, DZ2, "polished_blackstone_bricks", "dark_oak_log", "blackstone", "polished_blackstone",
         "blackstone_stairs", "blackstone_slab[type=bottom]", 178, "south", stories=2, door="dark_oak_door",
         window="purple_stained_glass_pane", roof_gable="polished_blackstone_bricks")
# a few black glass windows instead of plain ones
for x in range(DX1 + 1, DX2):
    for y in (G + 3, G + 4, G + 8, G + 9):
        if area.get(x, y, DZ1) == "minecraft:purple_stained_glass_pane" and (x // 4) % 2:
            area.set(x, y, DZ1, "black_stained_glass_pane")
sign(178, G + 5, DZ2 + 1, "south", ["Dark Auction"], kind="crimson")
for x in (174, 182):
    area.set(x, G + 1, DZ2 + 1, "polished_blackstone_wall")
    area.set(x, G + 2, DZ2 + 1, "polished_blackstone_wall")
    area.set(x, G + 3, DZ2 + 1, "soul_lantern[hanging=false]")
area.set(DX1 - 1, G + 1, DZ2 + 1, "soul_campfire[lit=true,facing=south]")
area.set(DX2 + 1, G + 1, DZ2 + 1, "soul_campfire[lit=true,facing=south]")
# inside: a stage with an amethyst-crowned altar, rows of seats, hanging soul lanterns
for x in range(DX1 + 4, DX2 - 3):
    area.set(x, G + 1, DZ1 + 1, "polished_blackstone_slab[type=bottom]")
    area.set(x, G + 1, DZ1 + 2, "polished_blackstone_bricks")
area.set(178, G + 2, DZ1 + 2, "chiseled_polished_blackstone")
area.set(178, G + 3, DZ1 + 2, "amethyst_cluster[facing=up]")
for x in (175, 181):
    area.set(x, G + 2, DZ1 + 2, "soul_lantern[hanging=false]")
for z in range(-14, -6, 2):
    for x in list(range(DX1 + 2, DX1 + 6)) + list(range(DX2 - 5, DX2 - 1)):
        area.set(x, G + 1, z, "dark_oak_stairs[facing=north,half=bottom]")
for x in range(DX1 + 3, DX2 - 2, 4):
    for z in (DZ1 + 4, DZ2 - 2):
        area.set(x, G + 5, z, "soul_lantern[hanging=true]")
for i in range(6):
    area.set(DX2 - 1, G + 1 + i, DZ1 + 2 + i, "dark_oak_stairs[facing=south,half=bottom]")
    if i < 5:
        area.set(DX2 - 1, G + 6, DZ1 + 2 + i, "air")
area.set(DX1 + 1, G + 7, DZ1 + 1, "bookshelf")
area.set(DX1 + 2, G + 7, DZ1 + 1, "enchanting_table")
area.set(DX2 - 3, G + 7, DZ2 - 1, "crying_obsidian")
area.set(DX2 - 3, G + 8, DZ2 - 1, "soul_lantern[hanging=false]")
area.npc("Sirius", 178, G + 1, DZ2 + 2, yaw=0.0, note="outside the Dark Auction door; appears at the Dark Auction")

# ---------------------------------------------------------------- the broken cage (a secret, south of the middle)
KX, KZ = 190, 62
area.flatten(KX - 7, KZ - 7, KX + 7, KZ + 7, G, "minecraft:grass_block")
reserve(KX - 7, KZ - 7, KX + 11, KZ + 7, 1)
for (x, z) in disc(KX, KZ, 5.5):
    area.set(x, G, z, "soul_sand" if hash01(x, 61, z) < 0.8 else "soul_soil")
    if hash01(x, 62, z, 1) < 0.12:
        area.set(x, G + 1, z, "soul_fire")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2:
            broken = (dx, dz) == (0, 2) or (dx, dz) == (2, 0)
            for y in range(G + 1, G + 5):
                if broken and y <= G + 2:
                    continue
                if broken and y == G + 3 and (dx, dz) == (2, 0):
                    continue
                area.set(KX + dx, y, KZ + dz, "iron_bars")
        if max(abs(dx), abs(dz)) <= 2:
            area.set(KX + dx, G + 5, KZ + dz, "iron_bars") if (dx, dz) != (1, 0) else None
area.set(KX, G + 5, KZ, "iron_bars")
# the gallows arm and chains it hangs from
for y in range(G + 1, G + 11):
    area.set(KX + 4, y, KZ, "dark_oak_log[axis=y]")
for x in range(KX, KX + 5):
    area.set(x, G + 10, KZ, "dark_oak_log[axis=x]")
for y in range(G + 6, G + 10):
    area.set(KX, y, KZ, "chain[axis=y]")
area.set(KX + 2, G + 9, KZ, "chain[axis=y]")
area.set(KX + 2, G + 8, KZ, "soul_lantern[hanging=true]")
area.set(KX, G + 1, KZ - 1, "skeleton_skull[rotation=4]")
area.set(KX - 1, G + 1, KZ + 1, "bone_block[axis=y]")
area.set(KX + 1, G + 1, KZ, "soul_lantern[hanging=false]")
area.set(KX - 4, G + 1, KZ - 4, "cobweb")
area.fairy_soul(KX, G + 1, KZ)
area.spots["notes"].append(f"Broken soul-sand cage secret at ({KX}, {G + 1}, {KZ})")

# ---------------------------------------------------------------- paths
mix_path = area.mix({"dirt_path": 5, "coarse_dirt": 1, "gravel": 1})
area.path([(125, 3), (140, 3), (165, 3), (177, 3), (200, 3), (216, 3)], width=3, blocks=mix_path)
area.path([(156, 3), (156, -3)], width=3, blocks=mix_path)
area.path([(178, 3), (178, -3)], width=3, blocks=mix_path)
area.path([(216, 3), (216, 13)], width=3, blocks=mix_path)
area.path([(150, 3), (150, 20), (150, 28)], width=2, blocks=area.mix({"dirt_path": 3, "coarse_dirt": 2, "moss_block": 1}))
area.path([(190, 3), (190, 20), (190, 50), (190, 56)], width=2, blocks=area.mix({"coarse_dirt": 3, "gravel": 2, "dirt_path": 1}))
reserve(124, 0, 216, 6, 0)
for (x, z) in stream:
    for dz in (0, 1):
        if 189 <= x <= 191 and (x, z + dz) in area.top:
            area.set(x, G, z + dz, "spruce_planks")
            area.set(x, G + 1, z + dz, "air")
area.set(176, G + 1, 2, "lantern[hanging=false]") if False else None
for x in range(140, 215, 14):
    area.set(x, G + 1, 1, "spruce_fence")
    area.set(x, G + 2, 1, "lantern[hanging=false]")

# ---------------------------------------------------------------- the soul-collecting tree and fireflies
TTX, TTZ = 238, 4
area.oak(TTX, G + 1, TTZ, height=14, radius=4, log="oak_log", leaves="oak_leaves", seed=9)
reserve(TTX - 3, TTZ - 3, TTX + 3, TTZ + 3, 2)
area.fairy_soul(TTX, G + 1 + 17, TTZ)       # on the leaf cap
area.set(TTX, G + 1 + 14, TTZ, "oak_leaves[persistent=true]")
area.set(TTX, G + 1 + 15, TTZ, "oak_leaves[persistent=true]")
area.set(TTX, G + 1 + 16, TTZ, "oak_leaves[persistent=true]")
area.fairy_soul  # (soul spots are fixed below)
area.spots["fairy_souls"].append([FPX + 1, G + 1, FPZ + 2]) if False else None

# ---------------------------------------------------------------- trees, boulders, meadow
trees = 0
for (x, z) in sorted(cols):
    if is_reserved(x, z) or hash01(x, 11, z, 4) > 0.011:
        continue
    y = area.surface_y(x, z)
    if y is None or area.get(x, y, z) != "minecraft:grass_block" or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    birch = hash01(x, 12, z, 2) < 0.4
    height = (5 if birch else 6) + int(hash01(x, 13, z) * 3)
    if birch:
        area.oak(x, y + 1, z, height=height, radius=2, log="birch_log", leaves="birch_leaves", seed=trees)
    else:
        area.oak(x, y + 1, z, height=height, radius=3, seed=trees)
    trees += 1
boulders = 0
for (x, z) in sorted(cols):
    if is_reserved(x, z) or hash01(x, 15, z, 5) > 0.0016:
        continue
    y = area.surface_y(x, z)
    if y is None or area.get(x, y, z) != "minecraft:grass_block" or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    r = 1 + int(hash01(x, 16, z) * 2)
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(0, r + 1):
                if dx * dx + dz * dz + dy * dy * 2 <= r * r + 1 and (x + dx, z + dz) in area.top and \
                        area.get(x + dx, y + 1 + dy, z + dz) in (None, "minecraft:air"):
                    area.set(x + dx, y + 1 + dy, z + dz, ["mossy_cobblestone", "andesite", "cobblestone", "tuff"][
                        int(hash01(x + dx, dy, z + dz, 3) * 4) % 4])
    boulders += 1


def meadow_bits(x, z):
    r = hash01(x, 21, z)
    return ("short_grass" if r < 0.5 else "fern" if r < 0.58 else "dandelion" if r < 0.64 else "poppy" if r < 0.70 else
            "cornflower" if r < 0.76 else "oxeye_daisy" if r < 0.82 else "azure_bluet" if r < 0.87 else
            "allium" if r < 0.90 else "red_tulip" if r < 0.93 else "pink_tulip" if r < 0.96 else
            "oak_leaves[persistent=true]" if r < 0.975 else "brown_mushroom" if r < 0.99 else "lily_of_the_valley")


area.scatter(cols, 0.30, meadow_bits, seed=6, avoid=lambda x, z: (x, z) in lake_set or (x, z) in pond_set)

# ---------------------------------------------------------------- spots, warp, zone
area.fairy_soul(FPX - 1, G + 1, FPZ - 2) if False else None
area.fairy_soul(FPX, G + 1, FPZ + 2)            # on the Fairy Pond's rock, beside Tia
area.warp("Wilderness", 130, G + 1, 3, yaw=270.0)
area.zone("Wilderness")
area.save()
print(f"trees {trees}, boulders {boulders}; souls {area.spots['fairy_souls']}")
