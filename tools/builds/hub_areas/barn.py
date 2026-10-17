#!/usr/bin/env python3
"""The Barn (Hypixel's first Farming Island, in the Farming Islands dimension): a big pastoral floating island with a red
barn and a windmill, Wheat, Carrot and Potato fields, a Pumpkin and a Melon patch, a cow pasture, a pig pen and a chicken
coop, the Farmhand and the Windmill Operator, the launch pad back to the Hub and the sky bridge (behind a Farming V gate)
to the Mushroom Desert.

    python tools/builds/hub_areas/barn.py
"""
import math

from kit import Area, hash01

G = 64
area = Area("barn", (-120, 20, -120), (232, 100, 240), version=1, dimension="skyrpg_skyblock:farming_islands",
            island="The Farming Islands")


def bump(x, z):   # a lobe to the south (arrival) and a trim at the east edge so the bridge meets it at x 86
    f = 1 + 0.2 * math.exp(-((x) ** 2 + (z - 92) ** 2) / (2 * 22 ** 2))
    return f * EAST.get("k", 1.0)


EAST = {"k": 1.0}
grass = area.mix({"grass_block": 14, "coarse_dirt": 1})
STONE = area.mix({"andesite": 4, "tuff": 3, "cobblestone": 2, "dripstone_block": 1, "coarse_dirt": 1})


def build_island():
    return area.island(0, 0, 85, top=G, seed=33, surface=grass, hills=2.5, depth=38, wobble=0.07, shape=bump, stone=STONE)


cols = build_island()

# ---------------------------------------------------------------- layout
BX1, BX2, BZ1, BZ2 = -22, -4, -72, -48      # the barn (door in the south gable at x -14..-12)
WX, WZ = 30, -40                            # windmill centre
FIELDS = [("Wheat", "wheat", -64, -26), ("Carrot", "carrots", -64, -8), ("Potato", "potatoes", -64, 10)]
FW, FD = 19, 14
PUMP = (-40, -26, -26, -12)                 # x1, x2, z1, z2
MEL = (-40, -26, 4, 18)
COW = (14, 8, 48, 30)
PIG = (22, 38, 36, 52)
COOP = (44, 38, 58, 52)
HUBPAD = (-10, 88)
GATE_X = 82

# a level island centre, hills only towards the rim
for x, z in sorted(cols):
    if math.hypot(x, z - 5) < 74 or (60 <= x <= 92 and abs(z) <= 14) or (abs(x) <= 16 and 70 <= z <= 100):
        area.flatten(x, z, x, z, G)

reserved = [(BX1 - 4, BX2 + 4, BZ1 - 4, BZ2 + 5), (WX - 7, WX + 7, WZ - 5, WZ + 10),
            (-68, -42, -29, 26), (PUMP[0] - 2, PUMP[1] + 2, PUMP[2] - 2, PUMP[3] + 2), (MEL[0] - 2, MEL[1] + 2, MEL[2] - 2, MEL[3] + 2),
            (COW[0] - 2, COW[2] + 2, COW[1] - 2, COW[3] + 4), (PIG[0] - 2, PIG[2] + 2, PIG[1] - 4, PIG[3] + 2),
            (COOP[0] - 2, COOP[2] + 2, COOP[1] - 4, COOP[3] + 2), (HUBPAD[0] - 7, HUBPAD[0] + 7, HUBPAD[1] - 7, HUBPAD[1] + 7),
            (-4, 4, -100, 100), (56, 92, -8, 8), (-8, 8, 30, 40)]


def in_reserved(x, z):
    return any(a <= x <= b and c <= z <= d for a, b, c, d in reserved)


def flowers(x, z):
    r = hash01(x, 31, z)
    if in_reserved(x, z):
        return "short_grass" if r < 0.3 else None
    return ("short_grass" if r < 0.45 else "dandelion" if r < 0.54 else "poppy" if r < 0.6 else "cornflower" if r < 0.66 else
            "oxeye_daisy" if r < 0.72 else "azure_bluet" if r < 0.77 else "hay_block[axis=y]" if r < 0.785 else
            "sunflower[half=lower]" if r < 0.82 else "fern")


area.scatter(cols, 0.22, flowers, seed=9)


def sign(x, y, z, lines, rot=0):
    area.set(x, y, z, f"oak_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": [f'"{t}"' if t else '""' for t in lines], "color": "white",
                                                     "has_glowing_text": 1},
              "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


# ---------------------------------------------------------------- paths
mixp = area.mix({"dirt_path": 6, "coarse_dirt": 1, "gravel": 1})
area.path([(0, 92), (0, 34), (0, 0)], width=3, blocks=mixp)
area.path([(0, 0), (40, 0), (86, 0)], width=5, blocks=mixp)
area.path([(0, 0), (0, -18), (-13, -26), (-13, -46)], width=3, blocks=mixp)
area.path([(0, -18), (30, -18), (30, -34)], width=3, blocks=mixp)
area.path([(0, -4), (-44, -4)], width=3, blocks=mixp)
area.path([(-44, -27), (-44, 24)], width=3, blocks=mixp)
area.path([(0, 36), (52, 36)], width=3, blocks=mixp)
area.path([(30, 36), (30, 31)], width=3, blocks=mixp)
area.path([(0, 89), (HUBPAD[0] + 3, 89)], width=3, blocks=mixp)
area.path([(0, 56), (-14, 56), (-20, 66)], width=2, blocks=mixp)

# ---------------------------------------------------------------- crop fields
for name, crop, fx, fz in FIELDS:
    fx2, fz2 = fx + FW - 1, fz + FD - 1
    for x in range(fx, fx2 + 1):
        for z in range(fz, fz2 + 1):
            rel = x - fx
            if rel % 5 == 4:
                area.set(x, G, z, "water")
                area.set(x, G - 1, z, "dirt")
                area.set(x, G + 1, z, "air")
            else:
                area.set(x, G, z, "farmland[moisture=7]")
                area.set(x, G + 1, z, f"{crop}[age=7]")
    for dx, dz in ((-1, -1), (FW, -1), (-1, FD), (FW, FD)):
        area.fill(fx + dx, G + 1, fz + dz, fx + dx, G + 2, fz + dz, "oak_fence")
        area.set(fx + dx, G + 3, fz + dz, "lantern[hanging=false]")
    # scarecrow at the field's north edge
    sx, sz = fx + 9, fz - 1
    area.set(sx, G + 1, sz, "oak_fence")
    area.set(sx, G + 2, sz, "hay_block[axis=y]")
    area.set(sx, G + 3, sz, "carved_pumpkin[facing=south]")
    area.set(sx - 1, G + 2, sz, "oak_fence")
    area.set(sx + 1, G + 2, sz, "oak_fence")
    sign(-42, G + 1, fz, ["", name, "Field", ""], 4)


def patch(box, stem, fruit, label):
    x1, x2, z1, z2 = box
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            rel = (z - z1) % 3
            if rel == 2:
                area.set(x, G, z, "water")
                area.set(x, G - 1, z, "dirt")
                area.set(x, G + 1, z, "air")
                continue
            area.set(x, G, z, "farmland[moisture=7]")
            m = (x - x1) % 3
            if m == 0:
                area.set(x, G + 1, z, f"minecraft:attached_{stem}_stem[facing=east]")
            elif m == 1:
                area.set(x, G + 1, z, fruit)
            else:
                area.set(x, G + 1, z, "air")
    # water would stop at the patch edge: fence it
    for x in range(x1 - 1, x2 + 2):
        for z in (z1 - 1, z2 + 1):
            area.set(x, G + 1, z, "oak_fence")
    for z in range(z1, z2 + 1):
        for x in (x1 - 1, x2 + 1):
            area.set(x, G + 1, z, "oak_fence")
    area.set(x1 - 1, G + 1, (z1 + z2) // 2, "oak_fence_gate[facing=east,open=false]")
    area.set(x1 - 1, G + 2, z1 - 1, "lantern[hanging=false]")
    sign(x1 - 2, G + 1, z1 + 1, ["", label, "", ""], 4)


patch(PUMP, "pumpkin", "pumpkin", "Pumpkin Patch")
patch(MEL, "melon", "melon", "Melon Patch")
# hay and wheelbarrow-ish clutter near the fields
for (x, z, ax) in ((-68, -30, "x"), (-67, -30, "x"), (-68, -29, "x"), (-68, 28, "z"), (-68, 29, "z"), (-67, 28, "y")):
    area.set(x, G + 1, z, f"hay_block[axis={ax}]")

# the Farmhand's spot: a little stall by the fields
FHX, FHZ = -44, -8
area.set(FHX - 3, G + 1, FHZ - 1, "barrel[facing=up]")
area.set(FHX - 3, G + 1, FHZ + 1, "composter[level=5]")
area.set(FHX + 3, G + 1, FHZ, "hay_block[axis=z]")
area.npc("Farmhand", FHX, G + 1, FHZ, yaw=90.0, note="on the path between the fields and the patches, by the carrot field")

# ---------------------------------------------------------------- the barn
wallb = "red_terracotta"
trim = "stripped_dark_oak_log"
for x in range(BX1, BX2 + 1):
    for z in range(BZ1, BZ2 + 1):
        area.set(x, G, z, "dark_oak_planks" if hash01(x, 5, z) < 0.8 else "spruce_planks")
        for y in range(G + 1, G + 7):
            area.set(x, y, z, "air")
        edge = x in (BX1, BX2) or z in (BZ1, BZ2)
        if not edge:
            continue
        corner = x in (BX1, BX2) and z in (BZ1, BZ2)
        post = (x in (BX1, BX2) and (z - BZ1) % 6 == 0) or (z in (BZ1, BZ2) and (x - BX1) % 6 == 0)
        for y in range(G + 1, G + 7):
            if corner or post:
                area.set(x, y, z, f"{trim}[axis=y]")
            elif y == G + 1:
                area.set(x, y, z, "dark_oak_planks")
            elif y == G + 4 or y == G + 6:
                area.set(x, y, z, "dark_oak_planks" if y == G + 6 else wallb)
            else:
                area.set(x, y, z, wallb)
        # windows on the long walls
        if x in (BX1, BX2) and not post and (z - BZ1) % 6 == 3:
            area.set(x, G + 3, z, "glass_pane")
            area.set(x, G + 2, z, "glass_pane")
# big south door (x -14..-12), frame and white braces
for x in (-15, -11):
    area.fill(x, G + 1, BZ2, x, G + 6, BZ2, f"{trim}[axis=y]")
for x in range(-14, -11):
    area.fill(x, G + 1, BZ2, x, G + 5, BZ2, "air")
    area.set(x, G + 5, BZ2 + 0, "dark_oak_planks")
    area.set(x, G + 6, BZ2, "white_terracotta")
for i in range(0, 4):
    for side in (-1, 1):
        cx = -13 + side * 3
        area.set(cx + side * 0, G + 1 + i, BZ2, "white_terracotta" if (i % 2 == 0) else "dark_oak_planks")
area.fill(-14, G + 1, BZ2, -12, G + 4, BZ2, "air")
for x in (-14, -12):                           # lanterns each side outside
    area.set(x - 3 if x < -13 else x + 3, G + 1, BZ2 + 1, "oak_fence")
    area.set(x - 3 if x < -13 else x + 3, G + 2, BZ2 + 1, "lantern[hanging=false]")
# roof along z (ridge along z), dark oak stairs
RIDGE = G + 7 + 10
for i in range(0, 11):
    xl, xr = BX1 - 1 + i, BX2 + 1 - i
    for z in range(BZ1 - 1, BZ2 + 2):
        if xl == xr:
            area.set(xl, G + 7 + i, z, "dark_oak_planks")
        else:
            area.set(xl, G + 7 + i, z, "dark_oak_stairs[facing=east,half=bottom]")
            area.set(xr, G + 7 + i, z, "dark_oak_stairs[facing=west,half=bottom]")
    if xl < xr:
        for x in range(xl + 1, xr):
            for z in (BZ1, BZ2):
                area.set(x, G + 7 + i, z, wallb if (x + i) % 5 else "white_terracotta")
for x in range(-14, -11):                      # hayloft opening in the south gable
    pass
area.fill(-13, G + 9, BZ2, -13, G + 11, BZ2, "air")
area.set(-14, G + 9, BZ2, "dark_oak_trapdoor[facing=west,half=bottom,open=true]")
area.set(-12, G + 9, BZ2, "dark_oak_trapdoor[facing=east,half=bottom,open=true]")
area.set(-13, G + 8, BZ2 + 1, "hay_block[axis=x]")
# loft (back half) and ladder
for x in range(BX1 + 1, BX2):
    for z in range(BZ1 + 1, -58):
        if (x, z) != (BX1 + 1, -60):
            area.set(x, G + 4, z, "dark_oak_planks")
area.fill(BX1 + 1, G + 1, -60, BX1 + 1, G + 5, -60, "ladder[facing=east]")
area.set(BX1 + 1, G + 4, -60, "ladder[facing=east]")
for (x, z) in ((-18, -70), (-17, -70), (-18, -69), (-8, -70), (-7, -70), (-7, -69), (-12, -64), (-13, -64), (-12, -63), (-6, -61),
               (-19, -62)):
    area.set(x, G + 5, z, "hay_block[axis=x]")
for (x, z) in ((-17, -70), (-7, -70), (-13, -64)):
    area.set(x, G + 6, z, "hay_block[axis=z]")
for x in (-12, -8):
    pass
# interior: stalls along the west wall, hay on the floor, lanterns on beams
for z in range(-56, -49):
    area.set(BX1 + 3, G + 1, z, "dark_oak_fence")
    area.set(BX2 - 3, G + 1, z, "dark_oak_fence")
for z in (-56, -53, -50):
    area.set(BX1 + 3, G + 1, z, "dark_oak_fence_gate[facing=east,open=false]" if z == -53 else "dark_oak_fence")
    area.set(BX1 + 1, G + 1, z, "hay_block[axis=z]")
    area.set(BX2 - 1, G + 1, z, "hay_block[axis=z]")
area.set(BX1 + 1, G + 1, -53, "water_cauldron[level=3]")
area.set(BX2 - 1, G + 1, -53, "water_cauldron[level=3]")
for (x, z) in ((-18, -66), (-17, -66), (-8, -62), (-8, -61), (-10, -57), (-16, -57), (-17, -57)):
    area.set(x, G + 1, z, "hay_block[axis=x]")
area.set(-8, G + 1, -66, "barrel[facing=up]")
area.set(-9, G + 1, -66, "barrel[facing=up]")
area.set(-17, G + 1, -51, "composter[level=4]")
for z in (-68, -62, -54, -50):
    for x in range(BX1 + 1, BX2):
        area.set(x, G + 6, z, "dark_oak_log[axis=x]")
    area.set(-13, G + 5, z, "lantern[hanging=true]")
    area.set(-9, G + 5, z, "lantern[hanging=true]")
# a hanging wooden sign over the door
sign(-16, G + 1, BZ2 + 2, ["", "The Barn", "", ""], 0)
area.fairy_soul(-13, RIDGE + 1, -60)           # on the barn's roof ridge
area.set(-13, RIDGE, -60, "hay_block[axis=y]")
area.set(-13, RIDGE + 1, -60, "air")
# a cart and haystack by the barn
for dx in range(0, 4):
    for dz in range(0, 2):
        area.set(-6 + dx, G + 1, -43 + dz, "spruce_planks")
        edge = dx in (0, 3) or dz in (0, 1)
        area.set(-6 + dx, G + 2, -43 + dz, "spruce_slab[type=bottom]" if edge else "air")
area.set(-5, G + 2, -43, "hay_block[axis=x]")
area.set(-4, G + 2, -43, "pumpkin")
area.set(-5, G + 2, -42, "hay_block[axis=x]")
for dx in (0, 3):
    area.set(-6 + dx, G + 1, -44, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    area.set(-6 + dx, G + 1, -41, "spruce_trapdoor[facing=north,half=bottom,open=true]")

# ---------------------------------------------------------------- the windmill
R = 3
H = 14
for x in range(WX - R, WX + R + 1):
    for z in range(WZ - R, WZ + R + 1):
        wall = x in (WX - R, WX + R) or z in (WZ - R, WZ + R)
        corner = x in (WX - R, WX + R) and z in (WZ - R, WZ + R)
        area.set(x, G, z, "stone_bricks")
        for y in range(G + 1, G + H + 1):
            if corner:
                area.set(x, y, z, "spruce_log[axis=y]")
            elif wall:
                area.set(x, y, z, "stone_bricks" if y <= G + 2 else "white_terracotta" if y % 4 else "spruce_planks")
            else:
                area.set(x, y, z, "air")
for i, s in enumerate((R + 1, R, R - 1, R - 2)):   # a pointed cap
    for x in range(WX - s, WX + s + 1):
        for z in range(WZ - s, WZ + s + 1):
            area.set(x, G + H + 1 + i, z, "spruce_planks" if i % 2 == 0 else "dark_oak_planks")
area.set(WX, G + H + 5, WZ, "spruce_fence")
area.fill(WX, G + 1, WZ + R, WX, G + 2, WZ + R, "air")
area.set(WX, G + 1, WZ + R, "spruce_door[facing=south,half=lower,hinge=left]")
area.set(WX, G + 2, WZ + R, "spruce_door[facing=south,half=upper,hinge=left]")
for dx in (-1, 1):
    area.set(WX + dx, G + 3, WZ + R, "glass_pane")
for dy in (7, 11):
    for dx in (-3, 3):
        area.set(WX + dx, G + dy, WZ, "glass_pane")
        area.set(WX + dx, G + dy + 1, WZ, "glass_pane")
for y in range(G + 1, G + H):                  # a ladder inside, grain sacks at the foot
    area.set(WX, y, WZ - R + 1, "ladder[facing=south]")
area.set(WX - 2, G + 1, WZ + 1, "barrel[facing=up]")
area.set(WX + 2, G + 1, WZ + 1, "hay_block[axis=x]")
area.set(WX - 2, G + 1, WZ - 1, "composter[level=6]")
area.set(WX + 1, G + 3, WZ + R - 1, "lantern[hanging=false]") if False else None
area.set(WX, G + 4, WZ + R - 1, "lantern[hanging=true]") if False else None
HUBY = G + 11
for z in (WZ + R + 1, WZ + R + 2):
    area.set(WX, HUBY, z, "oak_log[axis=z]")
SZ_ = WZ + R + 2
for dx, dy, sx_, sy_ in ((1, 0, 0, 1), (-1, 0, 0, -1), (0, 1, -1, 0), (0, -1, 1, 0)):
    for i in range(1, 9):
        bx, by = WX + dx * i, HUBY + dy * i
        area.set(bx, by, SZ_, "spruce_fence")
        if i >= 2:
            for k in (1, 2, 3):
                if k < 3 or i >= 4:
                    area.set(bx + k * sx_, by + k * sy_, SZ_, "white_wool")
area.fill(WX, G + 1, WZ + R + 1, WX, G + 2, WZ + R + 1, "air")
# little yard: hay, barrels, a lantern post at the door
area.set(WX - 2, G + 1, WZ + R + 2, "barrel[facing=up]")
area.set(WX + 2, G + 1, WZ + R + 2, "hay_block[axis=x]")
area.set(WX + 2, G + 1, WZ + R + 3, "hay_block[axis=z]")
area.fill(WX + 3, G + 1, WZ + R + 1, WX + 3, G + 2, WZ + R + 1, "oak_fence")
area.set(WX + 3, G + 3, WZ + R + 1, "lantern[hanging=false]")
sign(WX - 3, G + 1, WZ + R + 1, ["", "Windmill", "Operator", ""], 0)
area.npc("Windmill Operator", WX, G + 1, WZ + R + 3, yaw=180.0, note="at the windmill's south door, under the sails; shop")
# behind the windmill a hay nook with a soul
area.fairy_soul(WX, G + 1, WZ - R - 4)
for dx in (-1, 1):
    area.set(WX + dx, G + 1, WZ - R - 4, "hay_block[axis=x]")
area.set(WX, G + 1, WZ - R - 5, "hay_block[axis=x]")
area.set(WX, G, WZ - R - 4, "hay_block[axis=y]")

# ---------------------------------------------------------------- pens


def pen(x1, z1, x2, z2, gates):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not (x in (x1, x2) or z in (z1, z2)):
                continue
            g = gates.get((x, z))
            if g:
                area.set(x, G + 1, z, f"oak_fence_gate[facing={g},open=false]")
            elif (x in (x1, x2) and z in (z1, z2)) or (x - x1) % 6 == 0 and z in (z1, z2) or (z - z1) % 6 == 0 and x in (x1, x2):
                area.fill(x, G + 1, z, x, G + 2, z, "oak_fence")
            else:
                area.set(x, G + 1, z, "oak_fence")
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        area.set(x, G + 3, z, "lantern[hanging=false]")
    area.fill(x1 + 1, G + 1, z1 + 1, x2 - 1, G + 2, z2 - 1, "air") if False else None


# cow pasture
cx1, cz1, cx2, cz2 = COW
pen(cx1, cz1, cx2, cz2, {(30, cz2): "south"})
area.set(cx1 + 3, G + 1, cz1 + 3, "water_cauldron[level=3]")
area.set(cx1 + 4, G + 1, cz1 + 3, "water_cauldron[level=3]")
for x in range(cx2 - 6, cx2 - 2):
    area.set(x, G + 1, cz1 + 3, "hay_block[axis=x]")
area.set(cx2 - 4, G + 2, cz1 + 3, "hay_block[axis=x]")
sign(30, G + 1, cz2 + 2, ["", "Cow Pasture", "", ""], 0)
# pig pen: mud and podzol
px1, pz1, px2, pz2 = PIG
pen(px1, pz1, px2, pz2, {(29, pz1): "north"})
for x in range(px1 + 1, px2):
    for z in range(pz1 + 1, pz2):
        r = hash01(x, 61, z)
        area.set(x, G, z, "mud" if r < 0.5 else "podzol" if r < 0.8 else "coarse_dirt")
area.set(px1 + 2, G + 1, pz2 - 2, "water_cauldron[level=2]")
area.set(px2 - 2, G + 1, pz2 - 2, "hay_block[axis=x]")
area.set(px2 - 3, G + 1, pz2 - 2, "hay_block[axis=x]")
area.set(px1 + 2, G + 1, pz1 + 2, "composter[level=3]")
sign(29, G + 1, pz1 - 2, ["", "Pig Pen", "", ""], 0)
# chicken coop: hut + yard
kx1, kz1, kx2, kz2 = COOP
pen(kx1, kz1, kx2, kz2, {(kx1, 45): "west"})
HX1, HX2, HZ1, HZ2 = 47, 53, 40, 44
for x in range(HX1, HX2 + 1):
    for z in range(HZ1, HZ2 + 1):
        area.set(x, G, z, "spruce_planks")
        wall = x in (HX1, HX2) or z in (HZ1, HZ2)
        corner = x in (HX1, HX2) and z in (HZ1, HZ2)
        for y in range(G + 1, G + 4):
            area.set(x, y, z, "spruce_log[axis=y]" if corner else "oak_planks" if wall else "air")
        if wall and not corner and y == G + 3 and False:
            pass
for x in (49, 51):
    area.set(x, G + 2, HZ2, "glass_pane")
area.fill(50, G + 1, HZ2, 50, G + 2, HZ2, "air")
area.set(50, G + 1, HZ2, "spruce_door[facing=south,half=lower,hinge=left]")
area.set(50, G + 2, HZ2, "spruce_door[facing=south,half=upper,hinge=left]")
for i in range(0, 4):
    zl, zr = HZ1 - 1 + i, HZ2 + 1 - i
    for x in range(HX1 - 1, HX2 + 2):
        if zl == zr:
            area.set(x, G + 4 + i, zl, "spruce_slab[type=bottom]")
        else:
            area.set(x, G + 4 + i, zl, "spruce_stairs[facing=south,half=bottom]")
            area.set(x, G + 4 + i, zr, "spruce_stairs[facing=north,half=bottom]")
    if zl < zr:
        for z in range(zl + 1, zr):
            for x in (HX1, HX2):
                area.set(x, G + 4 + i, z, "oak_planks")
for x in range(HX1 + 1, HX2):                  # nest boxes along the back wall
    area.set(x, G + 1, HZ1 + 1, "hay_block[axis=x]" if x % 2 else "oak_trapdoor[facing=south,half=bottom,open=true]")
area.set(48, G + 2, HZ1 + 1, "hay_block[axis=x]")
area.set(HX2 - 1, G + 1, HZ2 - 1, "barrel[facing=up]")
area.set(50, G + 3, 42, "lantern[hanging=true]") if False else area.set(50, G + 3, 43, "lantern[hanging=false]")
area.fairy_soul(48, G + 3, HZ1 + 1)            # on a hay stack inside the coop
for x in range(kx1 + 2, kx1 + 6):
    area.set(x, G + 1, kz2 - 1, "hay_block[axis=x]")
area.set(kx2 - 2, G + 1, kz2 - 2, "water_cauldron[level=3]")
sign(kx1 - 2, G + 1, 47, ["", "Chicken", "Coop", ""], 4)

# ---------------------------------------------------------------- the launch pad back to the Hub
LX, LZ = HUBPAD
for dx in range(-4, 5):
    for dz in range(-4, 5):
        area.set(LX + dx, G, LZ + dz, "polished_andesite" if max(abs(dx), abs(dz)) > 2 else "oak_planks")
        area.fill(LX + dx, G + 1, LZ + dz, LX + dx, G + 3, LZ + dz, "air")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "hay_block[axis=y]")
area.set(LX, G + 1, LZ, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.fill(LX + dx, G + 1, LZ + dz, LX + dx, G + 2, LZ + dz, "oak_fence")
    area.set(LX + dx, G + 3, LZ + dz, "lantern[hanging=false]")
sign(LX - 4, G + 1, LZ - 2, ["", "the Hub", "", ""], 4)
area.pad("the Hub", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1, destination=("skyrpg_hub:hub", (182.5, 65.0, -194.5), 60.0))
area.spots["notes"].append(f"Hub launch pad at ({LX}, {G + 1}, {LZ})")

# ---------------------------------------------------------------- arrival
area.fill(-3, G + 1, 88, 3, G + 3, 95, "air")
area.arrival(0, 65, 92, 180)
area.warp("The Barn", 0, 65, 92, 180)
sign(2, G + 1, 91, ["", "The Barn", "Farming I", ""], 4)
for dz in (85, 91):
    for dx in (-3, 3):
        area.fill(dx, G + 1, dz, dx, G + 2, dz, "oak_fence")
        area.set(dx, G + 3, dz, "lantern[hanging=false]")

# ---------------------------------------------------------------- the gate to the Mushroom Desert (Farming V)
for z in (-3, 3):
    for x in (GATE_X, GATE_X + 1):
        area.fill(x, G + 1, z, x, G + 5, z, "stripped_dark_oak_log[axis=y]")
    area.set(GATE_X - 1, G + 1, z, "dark_oak_fence")
    area.set(GATE_X - 1, G + 2, z, "lantern[hanging=false]")
for z in range(-3, 4):
    for x in (GATE_X, GATE_X + 1):
        area.set(x, G + 6, z, "dark_oak_planks")
    area.set(GATE_X - 1 + 1, G + 7, z, "dark_oak_slab[type=bottom]")
for z in (-2, 2):
    area.set(GATE_X, G + 5, z, "chain[axis=y]")
    area.set(GATE_X, G + 4, z, "lantern[hanging=true]")
sign(GATE_X, G + 5, 0, ["The Mushroom", "Desert", "needs Farming V", ""], 4) if False else None
# the sign hangs on a post in the arch's middle: wall sign under the beam
area.set(GATE_X - 1, G + 5, 0, "oak_wall_sign[facing=west]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"the Mushroom Desert"', '"needs Farming V"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.set(GATE_X - 1, G + 6, 0, "dark_oak_planks")
for z in range(-2, 3):                         # pad floor: hay, the path's full width
    for x in (GATE_X - 2, GATE_X - 1):
        area.set(x, G, z, "hay_block[axis=z]")
        area.fill(x, G + 1, z, x, G + 3, z, "air")
area.set(GATE_X - 1, G + 5, 0, "oak_wall_sign[facing=west]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"the Mushroom Desert"', '"needs Farming V"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
PAD_BOX = (GATE_X - 2, G + 1, -2, GATE_X - 1, G + 2, 2)
area.pad("the Mushroom Desert", *PAD_BOX, warp="desert", skill="farming", level=5)

# the bridge to the desert (the other helper builds x >= 112)
area.bridge((86, 0), (142, 0), y=G)

# ---------------------------------------------------------------- the fourth soul: the island's west rim
ex = min(x for x, z in cols if z == 0)
area.fairy_soul(ex + 4, G + 1, 0)
area.set(ex + 4, G, 0, "hay_block[axis=y]")
area.set(ex + 3, G + 1, 0, "hay_block[axis=x]")
area.set(ex + 4, G + 1, 1, "hay_block[axis=x]")
area.set(ex + 4, G + 1, 0, "air")
area.set(ex + 4, G + 2, 0, "air")

# ---------------------------------------------------------------- trees near the rim
trees = 0
for (x, z) in sorted(cols):
    if in_reserved(x, z) or hash01(x, 11, z, 4) > 0.01:
        continue
    d = math.hypot(x, z)
    if d < 48:
        continue
    y = area.surface_y(x, z)
    if y is None or area.get(x, y, z) != "minecraft:grass_block":
        continue
    above = area.get(x, y + 1, z)
    if above not in (None, "minecraft:air") and not above.endswith(("grass", "dandelion", "poppy", "fern")):
        continue
    area.oak(x, y + 1, z, height=5 + int(hash01(x, 12, z) * 3), radius=3, seed=trees)
    trees += 1

# ---------------------------------------------------------------- the zone
area.zone("The Barn", blocks={
    "minecraft:wheat": {"becomes": "minecraft:wheat", "regrow": 8},
    "minecraft:carrots": {"becomes": "minecraft:carrots", "regrow": 8},
    "minecraft:potatoes": {"becomes": "minecraft:potatoes", "regrow": 8},
    "minecraft:pumpkin": {"becomes": "minecraft:air", "regrow": 10},
    "minecraft:melon": {"becomes": "minecraft:air", "regrow": 10}},
    mobs=[
        {"type": "minecraft:cow", "name": "Cow", "level": 1, "health": 100, "max": 6, "every": 8,
         "from": [COW[0] + 1, 65, COW[1] + 1], "to": [COW[2] - 1, 67, COW[3] - 1]},
        {"type": "minecraft:pig", "name": "Pig", "level": 1, "health": 100, "max": 6, "every": 8,
         "from": [PIG[0] + 1, 65, PIG[1] + 1], "to": [PIG[2] - 1, 67, PIG[3] - 1]},
        {"type": "minecraft:chicken", "name": "Chicken", "level": 1, "health": 80, "max": 6, "every": 8,
         "from": [COOP[0] + 1, 65, 46], "to": [COOP[2] - 1, 67, COOP[3] - 1]}])
area.save()

# ---------------------------------------------------------------- checks
print("east edge at z=0:", max(x for x, z in cols if z == 0), "| west edge:", ex)
print("arrival column ok:", [(0, z) in cols for z in (88, 92, 95)], "surface", area.surface_y(0, 92))
print("bridge start col in island:", (86, 0) in cols, (85, 0) in cols, (87, 0) in cols, "trees:", trees)
print("hub pad:", all((LX + dx, LZ + dz) in cols for dx in (-4, 4) for dz in (-4, 4)))
for nm, b in (("cow", COW), ("pig", PIG), ("coop", COOP)):
    print(nm, all((x, z) in cols for x in (b[0], b[2]) for z in (b[1], b[3])))
print("gate pad box", PAD_BOX, "extent", min(x for x, z in cols), max(x for x, z in cols), min(z for x, z in cols), max(z for x, z in cols))
