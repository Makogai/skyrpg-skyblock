"""The Garden's Barn plot: a cosy farmstead around a big red barn (see garden_kit.py for THE RULES)."""
import random

from garden_kit import (Barn, GROUND, ARRIVAL, DESK, DESK_BLOCK, COMPOSTER_BOX, COMPOSTER, SKYMART, PHILLIP, VISITORS)

barn = Barn()
rng = random.Random(1126)
G = GROUND          # 4: grass level
F = GROUND + 1      # 5: standing level
CHAIN = "iron_chain[axis=y]"
LANTERN = "lantern[hanging=false]"
HANGING = "lantern[hanging=true]"
AIR = "air"


def box(x1, y1, z1, x2, y2, z2, block):
    barn.fill(x1, y1, z1, x2, y2, z2, block)


def hang(x, y, z, beam_y):
    """A lantern on a chain from a ceiling at beam_y (the lantern at y)."""
    for yy in range(y + 1, beam_y):
        barn.set(x, yy, z, CHAIN)
    barn.set(x, y, z, HANGING)


def post_lamp(x, z, h=2):
    for i in range(h):
        barn.set(x, F + i, z, "oak_fence")
    barn.set(x, F + h, z, LANTERN)


def path(x1, z1, x2, z2, mix=("gravel", "gravel", "coarse_dirt", "dirt_path", "gravel")):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            barn.set(x, G, z, rng.choice(mix))


def tree(x, z, kind="oak", h=5):
    log, leaves = f"{kind}_log", f"{kind}_leaves[persistent=true]"
    for dy in range(h - 2, h + 2):
        r = 2 if dy < h else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and (dy >= h or rng.random() < 0.5):
                    continue
                barn.set(x + dx, F + dy, z + dz, leaves)
    for dy in range(h):
        barn.set(x, F + dy, z, log)


def haystack(x, z, w=2, d=2, h=2):
    for dx in range(w):
        for dz in range(d):
            for dy in range(h if (dx + dz) % 2 == 0 or h == 1 else h - 1):
                barn.set(x + dx, F + dy, z + dz, "hay_bale[axis=%s]" % rng.choice("xz"))


def flower_bed(x1, z1, x2, z2, flowers):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            barn.set(x, G, z, "grass_block")
            barn.set(x, F, z, rng.choice(flowers))


def fence_line(x1, z1, x2, z2, y=F, block="oak_fence"):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            barn.set(x, y, z, block)


# ======================================================================================================================
# 1. THE BARN  x 35..61, z 14..34, walls y5..10, gambrel roof along z
# ======================================================================================================================
BX1, BX2, BZ1, BZ2, CX = 35, 61, 14, 34, 48
WALL_TOP = 10

# foundation + floor
box(BX1, G, BZ1, BX2, G, BZ2, "cobblestone")
box(BX1 + 1, G, BZ1 + 1, BX2 - 1, G, BZ2 - 1, "oak_planks")
# hay-strewn floor patches
for _ in range(60):
    barn.set(rng.randint(37, 59), G, rng.randint(24, 33), "spruce_planks")

# walls
for x in range(BX1, BX2 + 1):
    for y in range(F, WALL_TOP + 1):
        barn.set(x, y, BZ1, "red_terracotta")
        barn.set(x, y, BZ2, "red_terracotta")
for z in range(BZ1, BZ2 + 1):
    for y in range(F, WALL_TOP + 1):
        barn.set(BX1, y, z, "red_terracotta")
        barn.set(BX2, y, z, "red_terracotta")
# interior air
box(BX1 + 1, F, BZ1 + 1, BX2 - 1, 20, BZ2 - 1, AIR)
# base course and corner posts, white posts between windows
for x in range(BX1, BX2 + 1):
    barn.set(x, F, BZ1, "polished_andesite")
    barn.set(x, F, BZ2, "polished_andesite")
for z in range(BZ1, BZ2 + 1):
    barn.set(BX1, F, z, "polished_andesite")
    barn.set(BX2, F, z, "polished_andesite")
for (x, z) in [(BX1, BZ1), (BX1, BZ2), (BX2, BZ1), (BX2, BZ2)]:
    box(x, F, z, x, WALL_TOP, z, "quartz_pillar[axis=y]")
for z in (17, 21, 25, 29):
    for x in (BX1, BX2):
        box(x, F, z, x, WALL_TOP, z, "stripped_birch_log[axis=y]")
for x in (39, 43, 53, 57):
    box(x, F, BZ2, x, WALL_TOP, BZ2, "stripped_birch_log[axis=y]")
    box(x, F, BZ1, x, WALL_TOP, BZ1, "stripped_birch_log[axis=y]")
# white trim band under the roof
for x in range(BX1, BX2 + 1):
    barn.set(x, WALL_TOP, BZ1, "white_terracotta")
    barn.set(x, WALL_TOP, BZ2, "white_terracotta")
for z in range(BZ1, BZ2 + 1):
    barn.set(BX1, WALL_TOP, z, "white_terracotta")
    barn.set(BX2, WALL_TOP, z, "white_terracotta")
# windows
for z in (19, 23, 27, 31):
    for x in (BX1, BX2):
        barn.set(x, 6, z, "birch_planks")
        barn.set(x, 7, z, "glass_pane")
        barn.set(x, 8, z, "glass_pane")
        barn.set(x, 9, z, "birch_planks")
for x in (37, 41, 55, 59):
    for z in (BZ2,):
        barn.set(x, 6, z, "birch_planks")
        barn.set(x, 7, z, "glass_pane")
        barn.set(x, 8, z, "glass_pane")
        barn.set(x, 9, z, "birch_planks")
for x in (41, 45, 51, 55):
    barn.set(x, 7, BZ1, "glass_pane")
    barn.set(x, 8, BZ1, "glass_pane")

# gambrel roof profile: d (distance from the ridge) -> (y, kind)
PROFILE = {13: (11, "stair"), 12: (12, "stair"), 11: (13, "stair"), 10: (14, "stair"), 9: (15, "stair"),
           8: (16, "bottom"), 7: (16, "top"), 6: (17, "bottom"), 5: (17, "top"), 4: (18, "bottom"),
           3: (18, "top"), 2: (19, "bottom"), 1: (19, "top"), 0: (20, "bottom")}
for z in range(BZ1, BZ2 + 1):
    edge = z in (BZ1, BZ2)
    mat = "spruce" if edge else "dark_oak"
    for x in range(BX1 - 1, BX2 + 2):
        d = abs(x - CX)
        facing = "west" if x < CX else "east"
        if d == 14:
            barn.set(x, WALL_TOP, z, f"{mat}_stairs[facing={facing},half=bottom]")
            continue
        y, kind = PROFILE[d]
        if kind == "stair":
            barn.set(x, y, z, f"{mat}_stairs[facing={facing},half=bottom]")
        else:
            barn.set(x, y, z, f"{mat}_slab[type={kind}]")
        # gable fill under the roof on the two ends
        if edge:
            top = y - 1 if kind == "stair" or kind == "top" else y - 1
            for yy in range(WALL_TOP + 1, y):
                barn.set(x, yy, z, "red_terracotta")
            if y - 1 >= WALL_TOP + 1:
                barn.set(x, y - 1, z, "birch_planks")
# roof sealing: slabs-top leave a gap under them; fine (interior)

# south gable: hayloft door frame
for x in range(46, 51):
    for y in range(12, 16):
        barn.set(x, y, BZ2, "birch_planks")
for x in (47, 48, 49):
    for y in (13, 14):
        barn.set(x, y, BZ2, AIR)
barn.set(48, 13, BZ2 - 1, "hay_bale[axis=x]")
barn.set(47, 13, BZ2 - 1, "hay_bale[axis=x]")
barn.set(49, 13, BZ2 - 1, "hay_bale[axis=x]")
barn.set(48, 16, BZ2 + 1, "iron_bars")   # hay hook
barn.set(48, 15, BZ2 + 1, "iron_bars")

# big double doors at the south wall
box(44, F, BZ2, 44, 9, BZ2, "stripped_birch_log[axis=y]")
box(52, F, BZ2, 52, 9, BZ2, "stripped_birch_log[axis=y]")
box(44, WALL_TOP, BZ2, 52, WALL_TOP, BZ2, "birch_planks")
box(47, F, BZ2, 49, 9, BZ2, AIR)
for x0, x1 in ((45, 46), (50, 51)):
    box(x0, F, BZ2 + 1, x1, 9, BZ2 + 1, "spruce_planks")   # door leaves stand slightly proud of the wall
    for x in (x0, x1):
        barn.set(x, F, BZ2 + 1, "birch_planks")
        barn.set(x, 9, BZ2 + 1, "birch_planks")
    barn.set(x0, 7, BZ2 + 1, "birch_planks")
    barn.set(x1, 7, BZ2 + 1, "birch_planks")
    box(x0, F, BZ2, x1, 9, BZ2, "spruce_planks")
# X-braces
for i in range(5):
    barn.set(45, F + i, BZ2 + 1, "spruce_planks")
for i in range(5):
    xs = 45 + round(i * 1 / 4)
    barn.set(xs, F + i, BZ2 + 1, "stripped_birch_log[axis=y]")
    xs2 = 51 - round(i * 1 / 4)
    barn.set(xs2, F + i, BZ2 + 1, "stripped_birch_log[axis=y]")
# door handles and lanterns beside the doors
barn.set(46, 6, BZ2 + 1, "iron_bars")
barn.set(50, 6, BZ2 + 1, "iron_bars")
for x in (43, 53):
    barn.set(x, F, BZ2 + 1, "oak_fence")
    barn.set(x, 6, BZ2 + 1, "oak_fence")
    barn.set(x, 7, BZ2 + 1, LANTERN)

# interior: tie beams with hanging lanterns
for z in (25, 29):
    box(BX1 + 1, WALL_TOP, z, BX2 - 1, WALL_TOP, z, "dark_oak_log[axis=x]")
    for x in (40, 56):
        hang(x, 8, z, WALL_TOP)
    hang(48, 8, z, WALL_TOP) if z == 25 else None
# hayloft: floor y10 over z15..22, joists, railing, ladder
box(36, WALL_TOP, 15, 60, WALL_TOP, 22, "spruce_planks")
for x in (36, 42, 48, 54, 60):
    box(x, WALL_TOP, 15, x, WALL_TOP, 22, "dark_oak_log[axis=z]")
box(36, WALL_TOP, 22, 60, WALL_TOP, 22, "dark_oak_log[axis=x]")
fence_line(37, 22, 59, 22, y=11)
for x in (40, 48, 56):
    barn.set(x, 12, 22, LANTERN)
    barn.set(x, 11, 22, "oak_fence")
    barn.set(x, 12, 22, LANTERN)
for x in (37, 43, 53, 59):
    box(x, F, 22, x, 9, 22, "dark_oak_log[axis=y]")
# ladder up through the loft floor at the north wall
for y in range(F, 12):
    barn.set(40, y, 15, "ladder[facing=south]")
# loft hay
for (x, z, h) in [(37, 16, 3), (38, 16, 2), (37, 17, 2), (52, 16, 3), (53, 16, 2), (54, 16, 2), (55, 16, 3),
                  (56, 17, 2), (58, 18, 2), (58, 19, 3), (59, 19, 2), (45, 20, 1), (46, 20, 2), (46, 21, 1),
                  (43, 17, 1), (50, 21, 1), (50, 20, 2), (51, 21, 1)]:
    for dy in range(h):
        barn.set(x, 11 + dy, z, "hay_bale[axis=%s]" % rng.choice("xz"))
for x, z in ((44, 16), (47, 17), (49, 16)):
    barn.set(x, 11, z, "barrel[facing=up]")
barn.set(41, 11, 20, LANTERN)
barn.set(57, 11, 21, LANTERN)
# under the loft: storage corner
for x in range(37, 47):
    barn.set(x, F, 15, "barrel[facing=south]")
for x in (38, 39, 41, 43, 44):
    barn.set(x, 6, 15, "barrel[facing=south]")
for x in range(37, 40):
    barn.set(x, F, 21, "hay_bale[axis=x]")
barn.set(49, F, 15, "crafting_table")
barn.set(50, F, 15, "smithing_table")
barn.set(51, F, 15, "fletching_table")
barn.set(52, F, 15, "loom[facing=south]")
barn.set(53, F, 15, "chest[facing=south]")
barn.set(54, F, 15, "chest[facing=south]")
for x in range(56, 60):
    barn.set(x, F, 15, "bookshelf")
    barn.set(x, 6, 15, "bookshelf")
barn.set(55, F, 15, "grindstone[face=floor,facing=south]")
barn.set(44, F, 21, "barrel[facing=up]")
barn.set(45, F, 21, "barrel[facing=up]")
barn.set(45, 6, 21, "barrel[facing=up]")
# west shelving
for z in range(24, 33):
    barn.set(36, F, z, "bookshelf")
    barn.set(36, 6, z, "bookshelf")
barn.set(36, 7, 28, "bookshelf")
barn.set(37, F, 24, "barrel[facing=up]")
barn.set(37, F, 25, "barrel[facing=up]")
barn.set(37, F, 31, "crafting_table")
barn.set(37, F, 32, "composter[level=4]")
barn.set(37, F, 33, "cauldron")
barn.set(38, F, 33, "barrel[facing=up]")
barn.set(37, 6, 24, LANTERN)
# east stalls
fence_line(55, 24, 55, 27)
fence_line(55, 29, 55, 33)
barn.set(55, F, 28, "oak_fence_gate[facing=east,open=false]")
fence_line(56, 28, 60, 28)
fence_line(56, 33, 60, 33)
fence_line(56, 24, 60, 24)
for (x, z) in ((59, 25), (60, 25), (60, 26), (59, 31), (60, 31), (60, 30), (59, 32)):
    barn.set(x, F, z, "hay_bale[axis=x]")
barn.set(57, F, 26, "oak_trapdoor[facing=north,half=bottom,open=false]")
barn.set(57, F, 31, "oak_trapdoor[facing=north,half=bottom,open=false]")
barn.set(56, F, 26, "cauldron")
barn.set(56, F, 31, "cauldron")
for x in (59, 60):
    barn.set(x, 6, 25, LANTERN) if x == 60 else None

# the Desk: platform, desk block, shelves behind, rug in front
box(44, G, 28, 52, G, 31, "dark_oak_planks")
for x in (44, 52):
    for z in (28, 31):
        barn.set(x, F, z, "oak_fence")
        barn.set(x, 6, z, LANTERN)
for x in range(46, 51):
    box(x, F, 28, x, 7, 28, "bookshelf")
for x in (46, 47, 49, 50):
    barn.set(x, F, 29, "barrel[facing=south]")
barn.set(*DESK_BLOCK, "cartography_table")
barn.set(48, 6, 30, LANTERN)
barn.set(47, F, 30, "lectern[facing=south]")
barn.set(49, F, 30, "lectern[facing=south]")
barn.set(46, F, 30, "oak_trapdoor[facing=east,half=bottom,open=false]")
barn.set(50, F, 30, "oak_trapdoor[facing=west,half=bottom,open=false]")
box(47, G, 31, 49, G, 33, "red_wool")
box(48, G, 31, 48, G, 33, "white_wool")
# keep the spot clear
for y in range(F, F + 3):
    barn.set(DESK[0], y, DESK[2], AIR)

# lanterns by the barn door inside
for x in (45, 51):
    barn.set(x, F, 33, "oak_fence")
    barn.set(x, 6, 33, "oak_fence")
    barn.set(x, 7, 33, LANTERN)

# ======================================================================================================================
# 2. THE COMPOSTER  box (64,5,40)..(68,8,44)
# ======================================================================================================================
(cx1, cy1, cz1), (cx2, cy2, cz2) = COMPOSTER_BOX
box(cx1, cy1, cz1, cx2, cy2, cz2, "waxed_copper_block")
# barrel base
for x in range(cx1, cx2 + 1):
    for z in range(cz1, cz2 + 1):
        if x in (cx1, cx2) or z in (cz1, cz2):
            barn.set(x, cy1, z, "barrel[facing=up]" if (x + z) % 2 == 0 else "waxed_cut_copper")
# mid band of weathered copper
box(cx1, cy1 + 1, cz1, cx2, cy1 + 1, cz2, "waxed_weathered_copper")
# front: three composters in a window, plus copper grates
for x in (65, 66, 67):
    barn.set(x, 6, cz2, "composter[level=8]")
    barn.set(x, 7, cz2, "waxed_copper_grate")
barn.set(64, 7, cz2, "waxed_oxidized_copper")
barn.set(68, 7, cz2, "waxed_oxidized_copper")
barn.set(66, 7, cz2, "composter[level=6]")
# corners oxidised, top ring
for (x, z) in ((cx1, cz1), (cx1, cz2), (cx2, cz1), (cx2, cz2)):
    box(x, cy1, z, x, cy2, z, "waxed_oxidized_copper")
box(cx1, cy2, cz1, cx2, cy2, cz2, "waxed_oxidized_cut_copper")
for x in range(65, 68):
    for z in range(41, 44):
        barn.set(x, cy2, z, "composter[level=%d]" % rng.choice((5, 6, 7, 8)))
# hopper chute and pipes on top
barn.set(66, 9, 42, "waxed_copper_block")
barn.set(66, 10, 42, "hopper[facing=down]")
barn.set(66, 11, 42, "hopper[facing=down]")
for (x, z) in ((cx1, cz1), (cx2, cz1)):
    barn.set(x, 9, z, "lightning_rod[facing=up]")
    barn.set(x, 10, z, "lightning_rod[facing=up]")
box(66, 9, 40, 66, 9, 41, "waxed_copper_block")
barn.set(66, 10, 40, "waxed_copper_block")
box(66, 10, 41, 66, 10, 41, "waxed_copper_block")
# side pipes (west and east) out of the box
for z in range(cz1, cz1 + 3):
    barn.set(cx1 - 1, 6, z, "waxed_copper_block")
    barn.set(cx2 + 1, 6, z, "waxed_copper_block")
box(cx1 - 1, 6, cz1, cx1 - 1, 8, cz1, "waxed_copper_block")
box(cx2 + 1, 6, cz1, cx2 + 1, 8, cz1, "waxed_copper_block")
barn.set(cx1 - 1, 8, cz1, "lightning_rod[facing=up]")
barn.set(cx2 + 1, 8, cz1, "lightning_rod[facing=up]")
# feed barrels and hay beside it
for (x, z) in ((70, 42), (70, 43), (70, 44)):
    barn.set(x, F, z, "barrel[facing=up]")
barn.set(70, 6, 43, "barrel[facing=up]")
haystack(60, 46, 2, 2, 2)
barn.set(63, F, 45, "oak_fence")
barn.set(63, 6, 45, "oak_fence")
barn.set(63, 7, 45, LANTERN)
barn.set(69, F, 45, "oak_fence")
barn.set(69, 6, 45, "oak_fence")
barn.set(69, 7, 45, LANTERN)
barn.sign(66, 8, cz2 + 1, ["Composter", "Organic Matter", "", ""], block="minecraft:dark_oak_wall_sign[facing=south]")

# ======================================================================================================================
# 3. SKYMART STALL  x 26..34, z 42..50, NPC at (30, 5, 46)
# ======================================================================================================================
box(26, G, 42, 34, G, 50, "spruce_planks")
box(27, G, 43, 33, G, 49, "oak_planks")
# back wall with shelves
for x in range(26, 35):
    for y in (F, 6, 7):
        barn.set(x, y, 42, "spruce_planks")
for x in range(27, 34):
    barn.set(x, 6, 43, "oak_slab[type=top]")
    barn.set(x, F, 43, "barrel[facing=south]" if x % 2 == 1 else "hay_bale[axis=x]")
for x in (27, 29, 31, 33):
    barn.set(x, 7, 43, "pumpkin" if x % 4 == 1 else "melon")
for y in (F, 6, 7):
    barn.set(26, y, 44, "spruce_log[axis=y]")
    barn.set(34, y, 44, "spruce_log[axis=y]")
for z in (45, 46, 47):
    for x in (26, 34):
        barn.set(x, F, z, "oak_fence")
# posts
for (x, z) in ((26, 42), (34, 42), (26, 50), (34, 50)):
    box(x, F, z, x, 7, z, "spruce_log[axis=y]")
# awning: striped wool, sloping down toward the front
for x in range(25, 36):
    wool = "red_wool" if x % 2 else "white_wool"
    for z in range(41, 52):
        barn.set(x, 8, z, wool)
    barn.set(x, 7, 51, wool)
    barn.set(x, 7, 41, wool)
# counter (front, with a spot to talk over)
for x in range(26, 35):
    barn.set(x, F, 49, "spruce_planks" if x % 2 else "oak_planks")
barn.set(26, 6, 49, "barrel[facing=up]")
barn.set(34, 6, 49, "barrel[facing=up]")
for x, item in ((27, "melon"), (28, "pumpkin"), (29, "hay_bale[axis=x]"), (31, "hay_bale[axis=x]"),
                (32, "pumpkin"), (33, "melon")):
    barn.set(x, 6, 49, item)
barn.set(30, 6, 49, "oak_slab[type=bottom]")
# sign hanging below the awning on the counter front
barn.sign(30, 6, 50, ["SkyMart", "Buy & Sell", "", ""], block="minecraft:oak_wall_sign[facing=south]")
# crates of produce in front
for (x, z, kind) in ((28, 52, "barrel[facing=up]"), (29, 52, "hay_bale[axis=x]"), (32, 52, "barrel[facing=up]"),
                     (33, 52, "melon"), (33, 53, "pumpkin")):
    barn.set(x, F, z, kind)
for x in (28, 32):
    barn.set(x, 7, 45, HANGING)
for z in (44, 47):
    barn.set(25, F, z, "composter[level=3]") if z == 44 else None
barn.set(25, F, 47, "barrel[facing=up]")
for y in range(F, F + 3):
    barn.set(*(SKYMART[0], y, SKYMART[2]), AIR)

# ======================================================================================================================
# 4. PHILLIP'S SHED  x 30..38, z 54..60 (open to the south), Phillip at (34, 5, 58)
# ======================================================================================================================
box(30, G, 54, 38, G, 60, "oak_planks")
for x in range(30, 39):
    for y in (F, 6, 7):
        barn.set(x, y, 54, "spruce_planks")
for z in range(54, 61):
    for y in (F, 6, 7):
        barn.set(30, y, z, "spruce_planks")
        barn.set(38, y, z, "spruce_planks")
for x in range(29, 40):
    barn.set(x, 8, 53 + 0, "spruce_slab[type=bottom]")
for z in range(53, 62):
    # sloped-ish roof: slab rows
    for x in range(29, 40):
        barn.set(x, 8, z, "spruce_slab[type=bottom]")
for x in range(29, 40):
    barn.set(x, 8, 53, "spruce_stairs[facing=south,half=bottom]")
    barn.set(x, 8, 61, "spruce_stairs[facing=north,half=bottom]")
box(31, 8, 55, 37, 8, 59, "spruce_planks")
for (x, z) in ((30, 60), (38, 60)):
    box(x, F, z, x, 7, z, "stripped_spruce_log[axis=y]")
box(30, 7, 60, 38, 7, 60, "spruce_planks")
# window and nets (cobwebs) and traps
barn.set(30, 6, 57, "glass_pane")
barn.set(38, 6, 57, "glass_pane")
for x in (32, 33, 35, 36):
    barn.set(x, 6, 55, "cobweb")
    barn.set(x, 7, 55, "cobweb")
barn.set(31, 7, 55, "cobweb")
barn.set(37, 7, 55, "cobweb")
for x in (32, 36):
    barn.set(x, F, 55, "iron_bars")
barn.set(34, F, 55, "barrel[facing=south]")
barn.set(34, 6, 55, "iron_bars")
for z in (55, 56, 57):
    barn.set(31, 6, z, "spruce_trapdoor[facing=east,half=bottom,open=true]")
barn.set(37, 6, 56, "spruce_trapdoor[facing=west,half=bottom,open=true]")
barn.set(31, F, 56, "iron_bars")
barn.set(31, F, 57, "iron_bars")
barn.set(37, F, 56, "iron_bars")
barn.set(37, F, 57, "iron_bars")
barn.set(31, F, 59, "barrel[facing=up]")
barn.set(37, F, 59, "chest[facing=west]")
barn.set(37, F, 58, "crafting_table")
barn.set(31, F, 58, "fletching_table")
barn.set(33, 7, 57, HANGING)
barn.set(35, 7, 57, HANGING)
barn.set(29, F, 60, "oak_fence")
barn.set(29, 6, 60, LANTERN)
barn.set(39, F, 60, "oak_fence")
barn.set(39, 6, 60, LANTERN)
# traps outside
barn.set(28, F, 56, "iron_bars")
barn.set(28, F, 57, "iron_bars")
barn.set(28, 6, 56, "iron_bars")
barn.set(28, 6, 57, "iron_bars")
barn.set(28, 7, 56, "iron_bars")
barn.set(28, 7, 57, "iron_bars")
barn.set(40, F, 56, "cobweb")
barn.set(40, F, 57, "hay_bale[axis=x]")
barn.set(41, F, 56, "barrel[facing=up]")
for y in range(F, F + 3):
    barn.set(PHILLIP[0], y, PHILLIP[2], AIR)

# ======================================================================================================================
# 5. THE VISITOR YARD  x 36..60, z 62..76: fenced grass, benches, flowers
# ======================================================================================================================
YX1, YX2, YZ1, YZ2 = 36, 60, 62, 76
box(YX1, G, YZ1, YX2, G, YZ2, "grass_block")
fence_line(YX1, YZ1, YX2, YZ1)
fence_line(YX1, YZ2, YX2, YZ2)
fence_line(YX1, YZ1, YX1, YZ2)
fence_line(YX2, YZ1, YX2, YZ2)
for z in (YZ1, YZ2):
    for x in (47, 48, 49):
        barn.set(x, F, z, AIR)
    barn.set(48, F, z, "oak_fence_gate[facing=%s,open=false]" % ("south" if z == YZ1 else "north"))
    barn.set(47, F, z, "oak_fence_gate[facing=south,open=true]")
    barn.set(49, F, z, "oak_fence_gate[facing=south,open=true]")
barn.set(48, F, YZ1, AIR)
barn.set(48, F, YZ2, AIR)
barn.set(47, F, YZ1, "oak_fence_gate[facing=south,open=false]")
barn.set(49, F, YZ1, "oak_fence_gate[facing=south,open=false]")
for z in (YZ1, YZ2):
    for x in (46, 50):
        barn.set(x, F, z, "oak_fence")
        barn.set(x, 6, z, "oak_fence")
        barn.set(x, 7, z, LANTERN)
# corner posts with lanterns
for (x, z) in ((YX1, YZ1), (YX2, YZ1), (YX1, YZ2), (YX2, YZ2)):
    barn.set(x, 6, z, "oak_fence")
    barn.set(x, 7, z, LANTERN)
# yard path
path(47, YZ1 - 1, 49, YZ2 + 1)
for z in (YZ1, YZ2):
    for x in (47, 48, 49):
        barn.set(x, G, z, "gravel")
# visitors' little dirt circles
for (vx, vy, vz) in VISITORS:
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if abs(dx) + abs(dz) <= 1:
                barn.set(vx + dx, G, vz + dz, rng.choice(("coarse_dirt", "dirt_path", "gravel")))
# benches
def bench(x, z, facing, n=3, axis="x"):
    for i in range(n):
        if axis == "x":
            barn.set(x + i, F, z, f"oak_stairs[facing={facing},half=bottom]")
        else:
            barn.set(x, F, z + i, f"oak_stairs[facing={facing},half=bottom]")
    # end posts
    if axis == "x":
        barn.set(x - 1, F, z, "oak_fence")
        barn.set(x + n, F, z, "oak_fence")
    else:
        barn.set(x, F, z - 1, "oak_fence")
        barn.set(x, F, z + n, "oak_fence")
bench(38, 74, "south", 3)
bench(55, 74, "south", 3)
bench(38, 64, "north", 3)
bench(56, 64, "north", 3)
bench(37, 69, "east", 2, axis="z")
bench(59, 69, "west", 2, axis="z")
# table and flowerpots
barn.set(46, F, 72, "oak_fence")
barn.set(46, 6, 72, "oak_pressure_plate")
barn.set(51, F, 72, "oak_fence")
barn.set(51, 6, 72, "oak_pressure_plate")
barn.set(54, F, 71, "barrel[facing=up]")
barn.set(41, F, 71, "barrel[facing=up]")
barn.set(42, F, 72, "potted_poppy")
barn.set(54, F, 72, "potted_oxeye_daisy")
# flowers
FLOW = ("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "red_tulip", "pink_tulip", "orange_tulip",
        "white_tulip", "allium")
for _ in range(110):
    x = rng.randint(YX1 + 1, YX2 - 1)
    z = rng.randint(YZ1 + 1, YZ2 - 1)
    if 46 <= x <= 50 and True:
        continue
    barn.set(x, F, z, rng.choice(FLOW)) if barn.get(x, F, z) is None else None
# flower tubs near the sign
for (x, z) in ((44, 63), (52, 63)):
    barn.set(x, F, z, "barrel[facing=up]")
    barn.set(x, 6, z, "azure_bluet")
# signpost "Visitors"
barn.set(45, F, 63, "oak_fence")
barn.set(45, 6, 63, "oak_fence")
barn.set(45, 7, 63, "oak_fence")
barn.sign(45, 6, 64, ["", "Visitors", "Welcome!", ""], block="minecraft:oak_wall_sign[facing=south]")
for (vx, vy, vz) in VISITORS:
    for y in range(vy, vy + 3):
        barn.set(vx, y, vz, AIR)

# ======================================================================================================================
# 6. ARRIVAL plaza and signpost
# ======================================================================================================================
AX, AZ = ARRIVAL[0], ARRIVAL[2]
for x in range(AX - 6, AX + 7):
    for z in range(AZ - 6, AZ + 7):
        if (x - AX) ** 2 + (z - AZ) ** 2 <= 38:
            r2 = (x - AX) ** 2 + (z - AZ) ** 2
            if r2 <= 3:
                barn.set(x, G, z, "stone_bricks")
            elif r2 <= 14:
                barn.set(x, G, z, rng.choice(("gravel", "gravel", "cobblestone", "andesite")))
            else:
                barn.set(x, G, z, rng.choice(("gravel", "coarse_dirt", "dirt_path", "gravel")))
barn.set(AX, G, AZ, "polished_andesite")
# signpost "Your Garden"
for (x, z) in ((53, 86), (43, 86)):
    barn.set(x, F, z, "oak_fence")
    barn.set(x, 6, z, "oak_fence")
    barn.set(x, 7, z, "oak_fence")
barn.sign(53, 6, 87, ["", "Your Garden", "", ""], block="minecraft:oak_wall_sign[facing=south]")
barn.sign(43, 6, 87, ["Welcome to", "the Barn", "", ""], block="minecraft:oak_wall_sign[facing=south]")
for (x, z) in ((AX - 5, AZ - 1), (AX + 5, AZ - 1), (AX - 5, AZ + 3), (AX + 5, AZ + 3)):
    post_lamp(x, z, 2)
barn.set(AX - 4, F, AZ + 5, "oak_stairs[facing=north,half=bottom]")
barn.set(AX + 4, F, AZ + 5, "oak_stairs[facing=north,half=bottom]")
barn.set(AX - 6, F, AZ, "barrel[facing=up]")
barn.set(AX + 6, F, AZ, "barrel[facing=up]")
for y in range(F, F + 3):
    barn.set(AX, y, AZ, AIR)

# ======================================================================================================================
# 7. PATHS
# ======================================================================================================================
path(47, 35, 49, 61)                  # barn doors -> yard gate
path(47, 77, 49, AZ - 6)              # yard south gate -> plaza
path(50, 46, 69, 48)                  # main -> composter (z46..48 a wide track)
path(62, 45, 70, 49)                  # composter pad
path(35, 51, 46, 53)                  # main -> SkyMart (z 51..53)
path(26, 51, 34, 54)                  # SkyMart front
path(31, 61, 46, 61)                  # Phillip front -> main
path(30, 61, 38, 62)
path(33, 52, 35, 53)
# connector to barn from composter area
path(50, 36, 62, 36)
path(63, 36, 63, 47)
# main road edge flowers + lamps
for z in range(40, 86, 6):
    if YZ1 - 1 <= z <= YZ2 + 1:
        continue
    post_lamp(46, z, 2)
    post_lamp(50, z + 3, 2)
for z in (40, 52, 58):
    pass

# ======================================================================================================================
# 8. FARMSTEAD DECOR: fields, scarecrows, haystacks, well, windmill, flower beds, trees
# ======================================================================================================================
def field(x1, z1, x2, z2, crops):
    box(x1, G, z1, x2, G, z2, "grass_block")
    for z in range(z1 + 1, z2):
        for x in range(x1 + 1, x2):
            row = (z - z1) % 4
            if row == 0:
                barn.set(x, G, z, "coarse_dirt")  # walkway
                continue
            crop = crops[((z - z1) // 4) % len(crops)]
            barn.set(x, G, z, "farmland[moisture=7]")
            barn.set(x, F, z, f"{crop}[age=7]")
    fence_line(x1, z1, x2, z1)
    fence_line(x1, z2, x2, z2)
    fence_line(x1, z1, x1, z2)
    fence_line(x2, z1, x2, z2)
    mid = (z1 + z2) // 2
    barn.set(x2, F, mid, "oak_fence_gate[facing=east,open=false]")
    barn.set(x1, F, mid, "oak_fence_gate[facing=east,open=false]")
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        barn.set(x, 6, z, "oak_fence")
        barn.set(x, 7, z, LANTERN)


def scarecrow(x, z):
    barn.set(x, G, z, "dirt")
    barn.set(x, F, z, "oak_fence")
    barn.set(x, 6, z, "hay_bale[axis=y]")
    barn.set(x - 1, 6, z, "oak_fence")
    barn.set(x + 1, 6, z, "oak_fence")
    barn.set(x, 7, z, "carved_pumpkin[facing=south]")
    barn.set(x, 8, z, "oak_slab[type=bottom]")


field(10, 16, 28, 34, ["wheat", "carrots", "potatoes"])
scarecrow(19, 24)
field(72, 14, 90, 32, ["wheat", "potatoes", "carrots"])
scarecrow(81, 22)
# vegetable beds near the stall
for z in (62, 64):
    for x in range(14, 24):
        barn.set(x, G, z, "farmland[moisture=7]")
        barn.set(x, F, z, rng.choice(("beetroots[age=3]", "carrots[age=7]", "wheat[age=7]")))
fence_line(13, 61, 24, 61)
fence_line(13, 65, 24, 65)
fence_line(13, 61, 13, 65)
fence_line(24, 61, 24, 65)
path(14, 63, 23, 63)

# haystacks and a hay cart near the barn
haystack(63, 17, 3, 2, 3)
haystack(30, 20, 2, 2, 2)
haystack(66, 28, 2, 2, 2)
haystack(30, 36, 2, 2, 2)
# a little cart

# dry well
for dx in range(-1, 2):
    for dz in range(-1, 2):
        if (dx, dz) != (0, 0):
            barn.set(24 + dx, F, 78 + dz, "mossy_cobblestone" if (dx + dz) % 2 else "cobblestone")
            barn.set(24 + dx, G, 78 + dz, "cobblestone")
barn.set(24, G, 78, "cobblestone")
barn.set(24, F, 78, AIR)
barn.set(24, G, 78, "dark_oak_planks")
barn.set(23, 6, 77, "oak_fence")
barn.set(25, 6, 77, "oak_fence")
barn.set(23, 7, 77, "oak_fence")
barn.set(25, 7, 77, "oak_fence")
for x in (22, 23, 24, 25, 26):
    barn.set(x, 8, 76, "spruce_stairs[facing=north,half=bottom]")
    barn.set(x, 8, 78, "spruce_stairs[facing=south,half=bottom]")
for z in (77,):
    for x in (22, 23, 24, 25, 26):
        barn.set(x, 9, z, "spruce_slab[type=bottom]")
for x in (23, 24, 25):
    barn.set(x, 8, 77, "spruce_planks")
barn.set(24, 7, 77, "iron_chain[axis=y]")
barn.set(24, 6, 77, "iron_chain[axis=y]")
barn.set(23, F, 77, "cobblestone")
barn.set(25, F, 77, "cobblestone")
barn.set(23, 6, 77, "oak_fence")
barn.set(25, 6, 77, "oak_fence")
barn.set(23, 7, 77, "oak_fence")
barn.set(25, 7, 77, "oak_fence")
barn.set(24, 5, 77, "cobblestone")
barn.set(26, F, 80, "barrel[facing=up]")
barn.set(27, F, 80, "hay_bale[axis=x]")

# windmill at x 74..78, z 58..62, with blades on its south face
WX, WZ = 76, 60
for y in range(F, 15):
    r = 2 if y < 11 else (1 if y < 14 else 1)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if y < 11:
                if abs(dx) == 2 and abs(dz) == 2:
                    barn.set(WX + dx, y, WZ + dz, "stripped_spruce_log[axis=y]")
                elif abs(dx) == 2 or abs(dz) == 2:
                    barn.set(WX + dx, y, WZ + dz, "white_terracotta" if y > F else "stone_bricks")
                else:
                    barn.set(WX + dx, y, WZ + dz, AIR)
            else:
                if abs(dx) <= 1 and abs(dz) <= 1:
                    if abs(dx) == 1 and abs(dz) == 1 and y < 14:
                        barn.set(WX + dx, y, WZ + dz, "spruce_planks")
                    elif y < 14 and (abs(dx) == 1 or abs(dz) == 1):
                        barn.set(WX + dx, y, WZ + dz, "spruce_planks")
                    elif y < 14:
                        barn.set(WX + dx, y, WZ + dz, AIR)
for dx in range(-2, 3):
    for dz in range(-2, 3):
        barn.set(WX + dx, 11, WZ + dz, "spruce_planks")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        barn.set(WX + dx, 14, WZ + dz, "spruce_planks")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) == 2 or abs(dz) == 2:
            barn.set(WX + dx, 12, WZ + dz, "spruce_stairs[facing=%s,half=bottom]" % (
                "west" if dx == -2 else "east" if dx == 2 else "north" if dz == -2 else "south"))
barn.set(WX, 15, WZ, "spruce_planks")
box(WX - 1, 15, WZ - 1, WX + 1, 15, WZ + 1, "spruce_slab[type=bottom]")
barn.set(WX, 16, WZ, "spruce_planks")
# door and windows
barn.set(WX - 2, F, WZ, "spruce_door[facing=west,half=lower,hinge=left,open=false]")
barn.set(WX - 2, 6, WZ, "spruce_door[facing=west,half=upper,hinge=left,open=false]")
barn.set(WX, 8, WZ + 2, "glass_pane")
barn.set(WX, 8, WZ - 2, "glass_pane")
barn.set(WX + 2, 8, WZ, "glass_pane")
barn.set(WX, F, WZ, AIR)
# blades on the south face (z = WZ + 3), hub at y 9
HZ, HY = WZ + 3, 9
barn.set(WX, HY, WZ + 2, "spruce_planks")
barn.set(WX, HY, HZ, "stripped_spruce_log[axis=z]")
for k in range(1, 6):
    barn.set(WX + k, HY, HZ, "oak_fence")
    barn.set(WX - k, HY, HZ, "oak_fence")
    barn.set(WX, HY + k, HZ, "oak_fence")
    barn.set(WX, HY - k, HZ, "oak_fence") if HY - k >= F else None
    if k >= 2:
        barn.set(WX + k, HY + 1, HZ, "white_wool")
        barn.set(WX - k, HY - 1, HZ, "white_wool")
        barn.set(WX - 1, HY + k, HZ, "white_wool")
        if HY - k >= F:
            barn.set(WX + 1, HY - k, HZ, "white_wool")
for k in range(2, 6):
    pass
haystack(70, 58, 2, 2, 2)
path(63, 48, 63, 60)
path(63, 60, 72, 62)
path(72, 58, 74, 60)
for z in range(52, 61, 4):
    post_lamp(65, z, 2)

# flower beds beside the barn and along paths
flower_bed(52, 36, 58, 37, ["poppy", "cornflower", "dandelion", "red_tulip", "oxeye_daisy"])
flower_bed(38, 36, 44, 37, ["allium", "azure_bluet", "pink_tulip", "white_tulip", "lilac[half=lower]"])
for x in range(38, 45):
    if barn.get(x, F, 36) == "minecraft:lilac":
        barn.set(x, F, 36, "poppy")
flower_bed(52, 82, 56, 84, ["dandelion", "poppy", "cornflower", "orange_tulip"])
flower_bed(38, 82, 42, 84, ["pink_tulip", "azure_bluet", "oxeye_daisy", "allium"])
flower_bed(52, 54, 56, 56, ["poppy", "dandelion", "cornflower"])
# edge the beds with fences
fence_line(52, 35, 58, 35)
fence_line(38, 35, 44, 35)
# stray flowers
for _ in range(60):
    x, z = rng.randint(4, 91), rng.randint(4, 91)
    if barn.get(x, G, z) is None and barn.get(x, F, z) is None:
        barn.set(x, F, z, rng.choice(FLOW))

# trees (oak and birch), kept inside the plot with plenty of margin
for (x, z, k) in [(8, 8, "oak"), (14, 6, "birch"), (30, 8, "birch"), (66, 8, "oak"), (84, 8, "birch"),
                  (88, 40, "oak"), (86, 52, "birch"), (84, 74, "oak"), (88, 86, "birch"), (72, 84, "oak"),
                  (8, 52, "oak"), (6, 70, "birch"), (10, 88, "oak"), (30, 86, "birch"), (68, 70, "birch"),
                  (74, 42, "oak"), (6, 38, "birch"), (20, 48, "oak")]:
    tree(x, z, k, h=rng.choice((4, 5, 6)))

# keep every fixed spot clear again at the end (nothing above may block them)
for (sx, sy, sz) in [ARRIVAL, DESK, COMPOSTER, SKYMART, PHILLIP] + VISITORS:
    for y in range(sy, sy + 3):
        barn.set(sx, y, sz, AIR)
for y in range(F, F + 3):
    barn.set(COMPOSTER[0], y, COMPOSTER[2], AIR)
barn.set(COMPOSTER[0], G, COMPOSTER[2], "gravel")

barn.save()
