#!/usr/bin/env python3
"""The Graveyard (Hypixel's Hub, west of the village): a dark island of podzol and moss, rows of graves inside an iron
fence, dead trees, Pat's house by the gate, the chapel with the Emerald Altar and the barred way down to the Hub Crypts,
and the launch pad to the Spider's Den at the back. Graveyard Zombies all day, Zombie Villagers at night.

    python tools/builds/hub_areas/graveyard.py
"""
from kit import Area, hash01

G = 64                      # surface; players stand at 65 (the village's level)
CX, CZ = -172, -36          # island centre
area = Area("graveyard", (-240, 20, -104), (184, 82, 140), version=2)
dark = area.mix({"podzol": 5, "coarse_dirt": 3, "grass_block": 2, "moss_block": 2, "rooted_dirt": 1})
cols = area.island(CX, CZ, 52, top=G, seed=11, surface=dark, hills=1.6, depth=40,
                   stone=area.mix({"stone": 4, "andesite": 3, "tuff": 3, "cobbled_deepslate": 2, "cobblestone": 1}))

# ---------------------------------------------------------------- the way in: a sky bridge from the village's west edge
area.bridge((-58, -16), (-127, -32), y=G, width=3, deck="dark_oak_planks", rail="dark_oak_fence", beam="stripped_dark_oak_log")

# The graveyard proper: a fenced field on level ground.
X1, X2, Z1, Z2 = -200, -142, -62, -12
area.flatten(X1 - 2, Z1 - 2, X2 + 2, Z2 + 2, G)
for x in range(X1, X2 + 1):
    for z in range(Z1, Z2 + 1):
        if x in (X1, X2) or z in (Z1, Z2):
            gate = x == X2 and -40 <= z <= -32 or x == X1 and -38 <= z <= -34
            if gate:
                continue
            pillar = (x - X1) % 7 == 0 and z in (Z1, Z2) or (z - Z1) % 7 == 0 and x in (X1, X2)
            if pillar:
                area.fill(x, G + 1, z, x, G + 3, z, "stone_bricks")
                area.set(x, G + 4, z, "stone_brick_slab[type=bottom]")
                area.set(x, G + 3, z, "chiseled_stone_bricks")
                if hash01(x, 0, z, 5) < 0.5:
                    area.set(x, G + 5, z, "soul_lantern[hanging=false]")
            else:
                area.set(x, G + 1, z, "mossy_stone_brick_wall" if hash01(x, 1, z) < 0.4 else "stone_brick_wall")
                area.set(x, G + 2, z, "iron_bars")
# The gate posts, east side.
for z in (-41, -31):
    area.fill(X2, G + 1, z, X2, G + 5, z, "polished_deepslate")
    area.set(X2, G + 6, z, "soul_lantern[hanging=false]")
area.fill(X2, G + 6, -40, X2, G + 6, -32, "dark_oak_slab[type=bottom]")
area.set(X2, G + 5, -36, "chain[axis=y]")
area.set(X2, G + 4, -36, "soul_lantern[hanging=true]")

# Paths: bridge -> gate -> down the middle to the chapel; a side path to Pat's house; out the back to the launch pad.
area.path([(-118, -30), (-130, -34), (-141, -36)], width=3,
          blocks=area.mix({"coarse_dirt": 3, "gravel": 2, "dirt_path": 2}))
area.path([(-142, -36), (-170, -36), (-186, -36)], width=3, blocks=area.mix({"gravel": 3, "coarse_dirt": 2, "cobblestone": 1}))
area.path([(-170, -36), (-170, -50)], width=2, blocks=area.mix({"gravel": 3, "coarse_dirt": 2}))
area.path([(-200, -36), (-214, -36)], width=3, blocks=area.mix({"gravel": 2, "coarse_dirt": 2}))
area.path([(-130, -34), (-130, -48), (-128, -56)], width=2, blocks=area.mix({"dirt_path": 3, "coarse_dirt": 1}))

# ---------------------------------------------------------------- graves
grave_tops = ["stone_brick_wall", "mossy_stone_brick_wall", "cobblestone_wall", "mossy_cobblestone_wall", "andesite_wall",
              "polished_blackstone_brick_wall", "deepslate_brick_wall"]
for row, z in enumerate(range(-58, -15, 5)):
    for x in range(-196, -146, 4):
        if -40 <= z <= -32 or -175 <= x <= -165 and z <= -46:
            continue   # the main path and the chapel's yard
        jitter = int(hash01(x, row, z, 3) * 2)
        gx, gz = x + jitter, z
        kind = int(hash01(gx, 0, gz, 7) * 10)
        if kind < 7:
            wall = grave_tops[int(hash01(gx, 1, gz, 8) * len(grave_tops)) % len(grave_tops)]
            area.set(gx, G + 1, gz, wall)
            if kind < 3:
                area.set(gx, G + 2, gz, "stone_button[face=floor,facing=north]" if kind == 0 else "stone_brick_slab[type=bottom]")
            elif kind == 6:
                area.set(gx, G + 2, gz, "candle[candles=2,lit=false]")
        elif kind < 9:
            # A cross.
            area.set(gx, G + 1, gz, "cobblestone_wall")
            area.set(gx, G + 2, gz, "cobblestone_wall")
            area.set(gx - 1, G + 2, gz, "stone_button[face=wall,facing=west]")
            area.set(gx + 1, G + 2, gz, "stone_button[face=wall,facing=east]")
        else:
            area.set(gx, G + 1, gz, "chiseled_stone_bricks")
            area.set(gx, G + 2, gz, "skeleton_skull[rotation=8]")
        # The grave itself in front: a mound of coarse dirt / podzol.
        area.set(gx, G, gz + 1, "coarse_dirt" if hash01(gx, 2, gz) < 0.5 else "podzol")
        area.set(gx, G, gz + 2, "rooted_dirt")
        if hash01(gx, 3, gz) < 0.15:
            area.set(gx, G + 1, gz + 2, "dead_bush")

# ---------------------------------------------------------------- the chapel (Emerald Altar, the barred way to the crypts)
CX1, CX2, CZ1, CZ2 = -179, -161, -62, -50
area.fill(CX1, G, CZ1, CX2, G, CZ2, "polished_deepslate")
for x in range(CX1, CX2 + 1):
    for z in range(CZ1, CZ2 + 1):
        wall = x in (CX1, CX2) or z in (CZ1, CZ2)
        for y in range(G + 1, G + 7):
            if wall:
                corner = x in (CX1, CX2) and z in (CZ1, CZ2)
                block = "deepslate_bricks" if not corner else "polished_deepslate"
                if hash01(x, y, z, 2) < 0.18 and not corner:
                    block = "cracked_deepslate_bricks"
                window = y in (G + 3, G + 4) and not corner and (z in (CZ1, CZ2) and x % 4 == 0 or x in (CX1, CX2) and z % 4 == 0)
                area.set(x, y, z, "iron_bars" if window else block)
            else:
                area.set(x, y, z, "air")
# Door on the south side, facing the path.
for y in (G + 1, G + 2, G + 3):
    for x in (-171, -170, -169):
        area.set(x, y, CZ2, "air")
area.set(-171, G + 3, CZ2, "deepslate_brick_stairs[facing=east,half=top]")
area.set(-169, G + 3, CZ2, "deepslate_brick_stairs[facing=west,half=top]")
# Gabled roof along x.
for i in range(0, 8):
    zl, zr = CZ1 - 1 + i, CZ2 + 1 - i
    if zl > zr:
        break
    for x in range(CX1 - 1, CX2 + 2):
        if zl == zr:
            area.set(x, G + 7 + i, zl, "deepslate_tile_slab[type=bottom]")
        else:
            area.set(x, G + 7 + i, zl, "deepslate_tile_stairs[facing=south]")
            area.set(x, G + 7 + i, zr, "deepslate_tile_stairs[facing=north]")
            for z in range(zl + 1, zr):
                if x in (CX1 - 1, CX2 + 1):
                    area.set(x, G + 7 + i, z, "deepslate_bricks" if x in (CX1 - 1, CX2 + 1) and i < 6 else "air")
# A small bell tower on the ridge, over the door end.
for x in (-172, -168):
    area.fill(x, G + 13, -57, x, G + 16, -57, "deepslate_brick_wall")
    area.fill(x, G + 13, -55, x, G + 16, -55, "deepslate_brick_wall")
area.fill(-172, G + 17, -57, -168, G + 17, -55, "deepslate_tile_slab[type=bottom]")
area.set(-170, G + 16, -56, "chain[axis=y]")
area.set(-170, G + 15, -56, "bell[attachment=ceiling,facing=south]")
# Inside: pews, the Emerald Altar at the back, candles; stairs down to the crypt gate.
for z in range(-58, -52, 2):
    for x in (-177, -176, -175, -165, -164, -163):
        area.set(x, G + 1, z, "dark_oak_stairs[facing=south]")
area.set(-170, G + 1, -60, "chiseled_stone_bricks")
area.set(-170, G + 2, -60, "emerald_block")
for x in (-172, -168):
    area.set(x, G + 1, -60, "polished_blackstone_bricks")
    area.set(x, G + 2, -60, "candle[candles=3,lit=true]")
area.set(-170, G + 3, -60, "end_rod[facing=up]")
# The crypt stairs (barred until the Hub Crypts open).
area.stairs_down(-177, G, -54, 5, "south", block="deepslate_brick_stairs", width=1, solid="deepslate_bricks")
area.fill(-178, G - 5, -50, -176, G - 2, -48, "deepslate_bricks")
area.fill(-177, G - 4, -50, -177, G - 3, -49, "air")
area.set(-177, G - 4, -49, "iron_bars")
area.set(-177, G - 3, -49, "iron_bars")
area.spots["notes"].append("Hub Crypts: the barred stairs in the chapel's west aisle (-177, 60, -49)")

# ---------------------------------------------------------------- Pat's house, just inside the gate (north side)
PX1, PX2, PZ1, PZ2 = -155, -147, -58, -51
area.fill(PX1, G, PZ1, PX2, G, PZ2, "spruce_planks")
for x in range(PX1, PX2 + 1):
    for z in range(PZ1, PZ2 + 1):
        wall = x in (PX1, PX2) or z in (PZ1, PZ2)
        corner = x in (PX1, PX2) and z in (PZ1, PZ2)
        for y in range(G + 1, G + 5):
            if corner:
                area.set(x, y, z, "spruce_log[axis=y]")
            elif wall:
                area.set(x, y, z, "glass_pane" if y == G + 2 and (x == PX1 + 2 or x == PX2 - 2 or z == PZ1 + 3) else
                         "cobblestone" if y == G + 1 else "spruce_planks")
            else:
                area.set(x, y, z, "air")
for x in range(PX1 - 1, PX2 + 2):
    area.fill(x, G + 5, PZ1 - 1, x, G + 5, PZ2 + 1, "spruce_slab[type=bottom]")
area.set(-151, G + 1, PZ2, "spruce_door[facing=south,half=lower,hinge=left]")
area.set(-151, G + 2, PZ2, "spruce_door[facing=south,half=upper,hinge=left]")
area.set(-153, G + 1, -57, "barrel[facing=up]")
area.set(-149, G + 1, -57, "crafting_table")
area.set(-148, G + 1, -57, "barrel[facing=up]")
area.set(-148, G + 2, -57, "flower_pot")
area.set(-150, G + 4, -55, "lantern[hanging=true]")
area.npc("Pat", -151, G + 1, -55, yaw=180.0, note="sells Flint and Gravel")
area.path([(-151, -50), (-151, -40)], width=2, blocks=area.mix({"coarse_dirt": 2, "gravel": 1}))

# ---------------------------------------------------------------- the launch pad to the Spider's Den (at the back)
LX, LZ = -218, -36
area.flatten(LX - 4, LZ - 4, LX + 4, LZ + 4, G, "polished_blackstone_bricks")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "polished_blackstone")
area.set(LX, G + 1, LZ, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(LX + dx, G + 1, LZ + dz, "polished_blackstone_wall")
    area.set(LX + dx, G + 2, LZ + dz, "soul_lantern[hanging=false]")
area.set(LX + 4, G + 1, LZ - 2, "dark_oak_sign[rotation=4]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"Spider\'s Den"', '"Combat I"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.pad("the Spider's Den", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1, warp="spider", skill="combat", level=1)
area.spots["notes"].append(f"Spider's Den launch pad at ({LX}, {G + 1}, {LZ})")

# ---------------------------------------------------------------- dead trees, bones, webs, pumpkins
inside = lambda x, z: X1 - 3 <= x <= X2 + 3 and Z1 - 3 <= z <= Z2 + 3
trees = 0
for (x, z) in sorted(cols):
    if inside(x, z) or hash01(x, 11, z, 4) > 0.012:
        continue
    y = area.surface_y(x, z)
    if y is None or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    height = 5 + int(hash01(x, 12, z) * 5)
    area.dead_tree(x, y + 1, z, height=height, log="dark_oak_log" if hash01(x, 13, z) < 0.4 else "spruce_log", seed=trees)
    if hash01(x, 14, z) < 0.5:
        area.set(x + 1, y + height - 1, z, "cobweb")
    trees += 1
# A few dead trees inside the fence too, between the graves.
for (x, z) in ((-190, -22), (-150, -24), (-186, -48), (-146, -46), (-160, -20)):
    area.dead_tree(x, G + 1, z, height=7, log="spruce_log", seed=x)
    area.set(x - 1, G + 5, z, "cobweb")


def ground_bits(x, z):
    r = hash01(x, 21, z)
    if inside(x, z):
        return "dead_bush" if r < 0.3 else "fern" if r < 0.5 else None
    return ("short_grass" if r < 0.5 else "fern" if r < 0.72 else "dead_bush" if r < 0.88 else
            "carved_pumpkin[facing=east]" if r < 0.895 else "jack_o_lantern[facing=south]" if r < 0.9 else
            "bone_block[axis=x]" if r < 0.915 else "cobweb" if r < 0.95 else "brown_mushroom")


area.scatter(cols, 0.18, ground_bits, seed=6)
# Soul campfires by the gate.
area.set(-139, G + 1, -42, "soul_campfire[lit=true,facing=north]")
area.set(-139, G + 1, -30, "soul_campfire[lit=true,facing=south]")

# ---------------------------------------------------------------- Fairy Souls (3, like Hypixel's Graveyard)
area.fairy_soul(-170, G + 18, -56)        # on top of the bell tower
area.fairy_soul(-218, G + 1, -46)         # by the launch pad, behind a dead tree
area.fairy_soul(-177, G - 4, -50)         # down the crypt stairs, behind the bars
area.warp("Graveyard", -128, G + 1, -33, yaw=90.0)

# ---------------------------------------------------------------- the zone: the island's area name, and its mobs
FIELD = ((X1 + 1, G - 2, Z1 + 1), (X2 - 1, G + 10, Z2 - 1))
area.zone("Graveyard", mobs=[
    {"type": "minecraft:zombie", "name": "Graveyard Zombie", "level": 1, "health": 100, "damage": 20, "xp": 6, "coins": 1,
     "max": 14, "every": 2.5, "from": list(FIELD[0]), "to": list(FIELD[1])},
    {"type": "minecraft:zombie_villager", "name": "Zombie Villager", "level": 1, "health": 120, "damage": 24, "xp": 7, "coins": 1,
     "max": 6, "every": 5, "time": "night", "from": list(FIELD[0]), "to": list(FIELD[1])},
])
area.save()
