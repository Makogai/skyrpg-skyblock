#!/usr/bin/env python3
"""The Farm (Hypixel's Hub, north-east of the village): a gentle green island with five big Wheat fields that regrow
(floating amethyst crystals keep them growing), the Farmer and Shania in a rustic shack, Arthur by a Wheat Minion
display, a windmill, a well, scarecrows, and the launch pad to The Barn.

    python tools/builds/hub_areas/farm.py
"""
import math

from kit import Area, hash01

G = 64
CX, CZ = 140, -170
area = Area("farm", (72, 20, -240), (138, 80, 143), version=2)


def bump(x, z):   # stretch the island's south-west edge so it reaches past the bridge end
    return 1 + 0.12 * math.exp(-((x - 102) ** 2 + (z + 118) ** 2) / (2 * 16 ** 2))


grass = area.mix({"grass_block": 15, "coarse_dirt": 1})
cols = area.island(CX, CZ, 58, top=G, seed=21, surface=grass, hills=1.5, depth=38, wobble=0.1, shape=bump,
                   stone=area.mix({"andesite": 4, "tuff": 3, "cobblestone": 2, "dripstone_block": 1}))

# ---------------------------------------------------------------- layout
FIELDS = [(98, -200), (118, -208), (152, -208), (170, -176), (98, -172)]   # (x1, z1); each 13 wide, 17 deep
FW, FD = 13, 17
SX1, SX2, SZ1, SZ2 = 130, 142, -170, -160       # the shack
PADX, PADZ = 186, -198
WX, WZ = 152, -146                              # windmill
BRIDGE_END = (108, -124)

area.flatten(94, -212, 188, -114, G)            # one level farm
area.flatten(PADX - 5, PADZ - 5, PADX + 5, PADZ + 5, G)
area.flatten(BRIDGE_END[0] - 10, BRIDGE_END[1] - 8, BRIDGE_END[0] + 10, BRIDGE_END[1] + 10, G)
# keep the island's flat surface for the whole farm; recompute for later columns
area.bridge((80, -27), BRIDGE_END, y=G)

reserved = [(x1 - 4, x2 + 4, z1 - 4, z2 + 4) for x1, z1 in FIELDS for x2, z2 in [(x1 + FW - 1, z1 + FD - 1)]]
reserved += [(SX1 - 4, SX2 + 8, SZ1 - 4, SZ2 + 6), (WX - 6, WX + 6, WZ - 5, WZ + 8), (PADX - 7, PADX + 7, PADZ - 7, PADZ + 7),
             (BRIDGE_END[0] - 16, BRIDGE_END[0] + 16, BRIDGE_END[1] - 20, BRIDGE_END[1] + 12), (108, 160, -160, -140)]


def in_reserved(x, z):
    return any(a <= x <= b and c <= z <= d for a, b, c, d in reserved)


def flowers(x, z):
    r = hash01(x, 31, z)
    if in_reserved(x, z):
        return "short_grass" if r < 0.35 else None
    return ("short_grass" if r < 0.45 else "dandelion" if r < 0.55 else "poppy" if r < 0.62 else "cornflower" if r < 0.69 else
            "oxeye_daisy" if r < 0.76 else "azure_bluet" if r < 0.82 else "hay_block[axis=y]" if r < 0.835 else
            "sunflower[half=lower]" if r < 0.87 else "fern")


area.scatter(cols, 0.22, flowers, seed=9)

# ---------------------------------------------------------------- wheat fields
crystals = []


def crystal(cx, cz, y):
    a = area
    a.set(cx, y - 1, cz, "amethyst_cluster[facing=down]")
    a.set(cx, y, cz, "budding_amethyst")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        a.set(cx + dx, y, cz + dz, "amethyst_block")
        a.set(cx + dx, y - 1, cz + dz, "small_amethyst_bud[facing=down]")
        a.set(cx + dx, y + 1, cz + dz, "amethyst_cluster[facing=up]")
    a.set(cx, y + 1, cz, "amethyst_block")
    a.set(cx, y + 2, cz, "amethyst_block")
    a.set(cx, y + 3, cz, "amethyst_cluster[facing=up]")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        a.set(cx + dx, y + 1, cz + dz, "end_rod[facing=up]")
        a.set(cx + 2 * dx, y - 1, cz + 2 * dz, "end_rod[facing=down]")


def field(x1, z1):
    x2, z2 = x1 + FW - 1, z1 + FD - 1
    gate_x = x1 + 6
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            border = x in (x1, x2) or z in (z1, z2)
            if border:
                area.set(x, G, z, "grass_block")
                corner = x in (x1, x2) and z in (z1, z2)
                if corner:
                    area.fill(x, G + 1, z, x, G + 2, z, "oak_fence")
                    area.set(x, G + 3, z, "lantern[hanging=false]")
                elif x == gate_x and z in (z1, z2):
                    area.set(x, G + 1, z, "oak_fence_gate[facing=south,open=false]")
                else:
                    area.set(x, G + 1, z, "oak_fence")
                continue
            rel = x - x1
            if rel in (3, 8):
                area.set(x, G, z, "water")
                area.set(x, G - 1, z, "dirt")
            else:
                area.set(x, G, z, "farmland[moisture=7]")
                area.set(x, G + 1, z, "wheat[age=7]")
    # scarecrow in the middle of the field
    sx, sz = x1 + 5, z1 + 8
    area.set(sx, G + 1, sz, "oak_fence")
    area.set(sx, G + 2, sz, "hay_block[axis=y]")
    area.set(sx, G + 3, sz, "carved_pumpkin[facing=south]")
    area.set(sx - 1, G + 2, sz, "oak_fence")
    area.set(sx + 1, G + 2, sz, "oak_fence")
    # the crystal that keeps it growing
    crystal(x1 + 6, z1 + 8, G + 7)
    crystals.append((x1 + 6, z1 + 8))


for fx, fz in FIELDS:
    field(fx, fz)

# ---------------------------------------------------------------- paths
mixp = area.mix({"dirt_path": 6, "coarse_dirt": 1, "gravel": 1})
area.path([(BRIDGE_END[0], BRIDGE_END[1] - 3), (114, -140), (114, -152), (114, -186)], width=3, blocks=mixp)
area.path([(114, -152), (126, -152), (136, -152), (136, -158)], width=3, blocks=mixp)
area.path([(104, -177), (104, -173)], width=2, blocks=mixp)
area.path([(104, -183), (104, -178), (114, -178)], width=2, blocks=mixp)
area.path([(114, -186), (176, -186)], width=3, blocks=mixp)
area.path([(124, -186), (124, -191)], width=2, blocks=mixp)
area.path([(158, -186), (158, -191)], width=2, blocks=mixp)
area.path([(176, -186), (176, -177)], width=2, blocks=mixp)
area.path([(176, -186), (184, -192), (PADX - 1, PADZ + 3)], width=3, blocks=mixp)
area.path([(136, -152), (152, -152), (152, -144)], width=3, blocks=mixp)
area.path([(152, -152), (170, -152), (176, -158)], width=2, blocks=mixp)
area.path([(PADX - 8, PADZ), (PADX - 4, PADZ)], width=2, blocks=mixp)

# ---------------------------------------------------------------- the shack (the Farmer and Shania)
area.fill(SX1, G, SZ1, SX2, G, SZ2, "spruce_planks")
for x in range(SX1, SX2 + 1):
    for z in range(SZ1, SZ2 + 1):
        wall = x in (SX1, SX2) or z in (SZ1, SZ2)
        corner = x in (SX1, SX2) and z in (SZ1, SZ2)
        for y in range(G + 1, G + 5):
            if corner:
                area.set(x, y, z, "spruce_log[axis=y]")
            elif wall:
                win = y in (G + 2, G + 3) and ((z == SZ2 and x in (131, 132, 139, 140)) or (z == SZ1 and x in (134, 138)) or
                                                (x == SX1 and z == -165) or (x == SX2 and z == -165))
                if win and y == G + 2:
                    area.set(x, y, z, "glass_pane")
                elif win:
                    area.set(x, y, z, "oak_planks")
                else:
                    area.set(x, y, z, "stripped_oak_log[axis=y]" if y == G + 1 and False else "oak_planks")
            else:
                area.set(x, y, z, "air")
# door and a lamp on the south side
area.set(134, G + 1, SZ2, "spruce_door[facing=south,half=lower,hinge=left]")
area.set(134, G + 2, SZ2, "spruce_door[facing=south,half=upper,hinge=left]")
area.set(135, G + 2, SZ2 + 1, "lantern[hanging=false]")
area.set(135, G + 1, SZ2 + 1, "oak_fence")
# gabled roof along x, spruce stairs
for i in range(0, 7):
    zl, zr = SZ1 - 1 + i, SZ2 + 1 - i
    for x in range(SX1 - 1, SX2 + 2):
        if zl == zr:
            area.set(x, G + 5 + i, zl, "spruce_slab[type=bottom]")
        else:
            area.set(x, G + 5 + i, zl, "spruce_stairs[facing=south,half=bottom]")
            area.set(x, G + 5 + i, zr, "spruce_stairs[facing=north,half=bottom]")
    if zl < zr:
        for z in range(zl + 1, zr):
            for x in (SX1, SX2):
                area.set(x, G + 5 + i, z, "oak_planks")
# loft beams inside
for x in range(SX1 + 1, SX2):
    area.set(x, G + 4, -165, "spruce_log[axis=x]")
# stall for Shania (east side), fenced
for z in range(SZ1 + 1, SZ2):
    area.set(137, G + 1, z, "oak_fence")
area.set(137, G + 1, -165, "oak_fence_gate[facing=east,open=false]")
for x in range(138, SX2):
    area.set(x, G, SZ1 + 1, "hay_block[axis=x]")
area.set(139, G + 1, SZ2 - 1, "cauldron")
area.set(141, G + 1, SZ2 - 1, "hay_block[axis=x]")
area.set(141, G + 1, SZ1 + 1, "hay_block[axis=x]")
area.set(140, G + 1, SZ1 + 1, "hay_block[axis=x]")
area.set(138, G + 1, SZ1 + 1, "oak_trapdoor[facing=south,half=bottom,open=true]")
area.set(139, G + 3, -165, "lantern[hanging=true]")
# the Farmer's side: counter, barrels, table, bed
for x in range(131, 136):
    area.set(x, G + 1, SZ1 + 1, ["barrel[facing=up]", "crafting_table", "composter[level=5]", "barrel[facing=up]", "hay_block[axis=x]"][x - 131])
area.set(131, G + 2, SZ1 + 1, "flower_pot")
area.set(135, G + 1, -163, "spruce_stairs[facing=east,half=bottom]")
area.set(131, G + 1, -162, "red_bed[facing=north,part=foot]")
area.set(131, G + 1, -163, "red_bed[facing=north,part=head]")
area.set(133, G + 3, -165, "lantern[hanging=true]")
area.set(134, G + 1, -166, "oak_pressure_plate")
area.npc("Farmer", 134, G + 1, -164, yaw=0.0, note="inside the shack, by the door; sells farming starter items")
area.npc("Shania", 140, G + 1, -166, yaw=90.0, note="Shania the cow NPC, in the stall at the shack's east end")

# outside the shack: composters, hay, a lamp post
for z in (-168, -166):
    area.set(SX2 + 2, G + 1, z, "composter[level=6]")
area.set(SX2 + 2, G + 1, -164, "hay_block[axis=z]")
area.set(SX2 + 2, G + 2, -164, "hay_block[axis=z]")
area.set(SX1 - 2, G + 1, -166, "barrel[facing=up]")
area.set(SX1 - 2, G + 1, -165, "barrel[facing=up]")
area.set(SX1 - 2, G + 2, -165, "hay_block[axis=y]")

# ---------------------------------------------------------------- Arthur and the Wheat Minion display
PCX, PCZ = 122, -156
for dx in range(-1, 2):
    for dz in range(-1, 2):
        area.set(PCX + dx, G, PCZ + dz, "polished_andesite")
        area.set(PCX + dx, G + 1, PCZ + dz, "air")
area.set(PCX, G + 1, PCZ, "stone_brick_wall")
area.set(PCX, G + 2, PCZ, "chiseled_stone_bricks")
area.set(PCX, G + 3, PCZ, "hay_block[axis=y]")
area.set(PCX, G + 4, PCZ, "carved_pumpkin[facing=south]")
for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    area.set(PCX + dx, G + 1, PCZ + dz, "oak_fence")
    area.set(PCX + dx, G + 2, PCZ + dz, "lantern[hanging=false]")
area.set(PCX + 2, G + 1, PCZ, "oak_sign[rotation=4]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"Wheat Minion"', '"display"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.npc("Arthur", 126, G + 1, PCZ, yaw=90.0, note="outside, east of the Wheat Minion display")
area.spots["notes"].append(f"Wheat Minion display pedestal at ({PCX}, {G + 2}, {PCZ}); the minion stands on the chiseled bricks")

# ---------------------------------------------------------------- the windmill
for x in range(WX - 2, WX + 3):
    for z in range(WZ - 2, WZ + 3):
        wall = x in (WX - 2, WX + 2) or z in (WZ - 2, WZ + 2)
        corner = x in (WX - 2, WX + 2) and z in (WZ - 2, WZ + 2)
        area.set(x, G, z, "stone_bricks")
        for y in range(G + 1, G + 9):
            if corner:
                area.set(x, y, z, "spruce_log[axis=y]")
            elif wall:
                area.set(x, y, z, "stone_bricks" if y <= G + 2 else "white_terracotta" if y < G + 8 else "oak_planks")
            else:
                area.set(x, y, z, "air")
        area.set(x, G + 9, z, "spruce_planks")
for x in range(WX - 1, WX + 2):
    for z in range(WZ - 1, WZ + 2):
        area.set(x, G + 10, z, "spruce_planks")
area.set(WX, G + 1, WZ + 2, "air")
area.set(WX, G + 2, WZ + 2, "air")
area.set(WX, G + 1, WZ + 2, "spruce_door[facing=south,half=lower,hinge=left]")
area.set(WX, G + 2, WZ + 2, "spruce_door[facing=south,half=upper,hinge=left]")
HUBY = G + 6
area.set(WX, HUBY, WZ + 3, "oak_log[axis=z]")
area.set(WX, HUBY, WZ + 4, "oak_log[axis=z]")
for dx, dy, sx_, sy_ in ((1, 0, 0, 1), (-1, 0, 0, -1), (0, 1, -1, 0), (0, -1, 1, 0)):
    for i in range(1, 6):
        bx, by = WX + dx * i, HUBY + dy * i
        area.set(bx, by, WZ + 4, "spruce_fence")
        if i >= 2:
            area.set(bx + sx_, by + sy_, WZ + 4, "white_wool")
            if i >= 3:
                area.set(bx + 2 * sx_, by + 2 * sy_, WZ + 4, "white_wool")
area.fairy_soul(WX, G + 11, WZ)                 # on the windmill roof

# ---------------------------------------------------------------- well
WLX, WLZ = 143, -145
for dx in range(-1, 2):
    for dz in range(-1, 2):
        area.set(WLX + dx, G, WLZ + dz, "cobblestone")
        if dx or dz:
            area.set(WLX + dx, G + 1, WLZ + dz, "cobblestone_wall" if (dx and dz) is False else "air")
area.set(WLX, G, WLZ, "water")
area.set(WLX, G - 1, WLZ, "water")
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    area.fill(WLX + dx, G + 1, WLZ + dz, WLX + dx, G + 3, WLZ + dz, "oak_fence")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        area.set(WLX + dx, G + 4, WLZ + dz, "spruce_slab[type=bottom]")
area.set(WLX, G + 3, WLZ, "chain[axis=y]")
area.set(WLX, G + 2, WLZ, "chain[axis=y]")
area.set(WLX, G + 1, WLZ, "air")

# ---------------------------------------------------------------- a cart
CRX, CRZ = 168, -150
for dx in range(0, 4):
    for dz in range(0, 2):
        area.set(CRX + dx, G + 1, CRZ + dz, "spruce_planks")
        edge = dx in (0, 3) or dz in (0, 1)
        area.set(CRX + dx, G + 2, CRZ + dz, "spruce_slab[type=bottom]" if edge else "air")
area.set(CRX + 1, G + 2, CRZ, "hay_block[axis=x]")
area.set(CRX + 2, G + 2, CRZ, "hay_block[axis=x]")
area.set(CRX + 1, G + 2, CRZ + 1, "hay_block[axis=x]")
area.set(CRX + 2, G + 2, CRZ + 1, "pumpkin")
for dx in (0, 3):
    area.set(CRX + dx, G + 1, CRZ - 1, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    area.set(CRX + dx, G + 1, CRZ + 2, "spruce_trapdoor[facing=north,half=bottom,open=true]")
area.set(CRX - 1, G + 1, CRZ, "spruce_fence")
area.set(CRX - 2, G + 1, CRZ, "spruce_fence")

# ---------------------------------------------------------------- hay bales, a pumpkin patch, a small orchard, hedges
for (x, z, ax) in ((120, -160, "x"), (121, -160, "x"), (120, -159, "x"), (118, -148, "z"), (118, -147, "z"), (160, -156, "x"),
                   (161, -156, "x"), (161, -155, "y"), (134, -150, "x"), (174, -150, "z"), (174, -149, "z"), (110, -146, "x"),
                   (111, -146, "x"), (111, -145, "y")):
    area.set(x, G + 1, z, f"hay_block[axis={ax}]")
for x in range(160, 168):
    for z in range(-142, -136):
        if hash01(x, 40, z) < 0.45 and (x + z) % 2 == 0:
            area.set(x, G, z, "grass_block")
            area.set(x, G + 1, z, "pumpkin" if hash01(x, 41, z) < 0.6 else "melon")
# hedges (leaves) along the shack path
for x in range(118, 130):
    area.set(x, G + 1, -161, "oak_leaves[persistent=true]")
for x in range(138, 150):
    area.set(x, G + 1, -138, "azalea_leaves[persistent=true]" if hash01(x, 0, 0) < 0.3 else "oak_leaves[persistent=true]")
    area.set(x, G + 2, -138, "oak_leaves[persistent=true]" if hash01(x, 1, 0) < 0.5 else "air")

# trees near the rim, away from the buildings
trees = 0
for (x, z) in sorted(cols):
    if in_reserved(x, z) or hash01(x, 11, z, 4) > 0.012:
        continue
    d = math.hypot(x - CX, z - CZ)
    if d < 36:
        continue
    y = area.surface_y(x, z)
    if y is None or area.get(x, y, z) != "minecraft:grass_block" or area.get(x, y + 1, z) not in (None, "minecraft:air") and \
            not area.get(x, y + 1, z).endswith(("grass", "dandelion", "poppy", "fern")):
        continue
    area.oak(x, y + 1, z, height=5 + int(hash01(x, 12, z) * 3), radius=3, seed=trees)
    trees += 1

# ---------------------------------------------------------------- the launch pad to The Barn
LX, LZ = PADX, PADZ
for dx in range(-4, 5):
    for dz in range(-4, 5):
        area.set(LX + dx, G, LZ + dz, "polished_andesite" if max(abs(dx), abs(dz)) > 2 else "oak_planks")
        area.set(LX + dx, G + 1, LZ + dz, "air")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "hay_block[axis=y]")
area.set(LX, G + 1, LZ, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.fill(LX + dx, G + 1, LZ + dz, LX + dx, G + 2, LZ + dz, "oak_fence")
    area.set(LX + dx, G + 3, LZ + dz, "lantern[hanging=false]")
area.set(LX - 4, G + 1, LZ - 2, "oak_sign[rotation=4]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"The Barn"', '"Farming I"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.pad("The Barn", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1, warp="barn", skill="farming", level=1)
area.spots["notes"].append(f"The Barn launch pad at ({LX}, {G + 1}, {LZ})")

# ---------------------------------------------------------------- Fairy Souls and the warp
area.fairy_soul(139, G + 1, SZ1 - 3)            # behind the shack, between the barrels
area.fairy_soul(LX + 6, G + 1, LZ - 6)          # tucked behind the pad
area.set(139, G + 1, SZ1 - 3, "air")
area.set(139, G + 1, SZ1 - 2, "hay_block[axis=x]")
area.set(LX + 6, G, LZ - 6, "hay_block[axis=y]")
area.set(LX + 6, G + 1, LZ - 6, "air")
area.warp("Farm", 111, G + 1, -131, yaw=-135.0)

# ---------------------------------------------------------------- the zone
area.zone("Farm", blocks={"minecraft:wheat": {"becomes": "minecraft:wheat", "regrow": 8}})
area.save()

# ---------------------------------------------------------------- checks
ex, ez = BRIDGE_END
print("bridge end column in island:", (ex, ez) in cols, "surface", area.surface_y(ex, ez),
      "| extra cols past it:", [(ex + i, ez + i) in cols for i in range(1, 5)])
for fx, fz in FIELDS:
    ok = all((x, z) in cols for x in (fx, fx + FW - 1) for z in (fz, fz + FD - 1))
    print("field", (fx, fz), "inside island:", ok)
print("pad inside:", all((LX + dx, LZ + dz) in cols for dx in (-4, 4) for dz in (-4, 4)), "trees:", trees)
