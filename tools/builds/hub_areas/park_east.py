#!/usr/bin/env python3
"""The Park, east half (Hypixel's Foraging island): the Dark Thicket (dark oak, the Campfire Cult), the Savanna Woodland
(acacia, a camp and a rock outcrop) and Jungle Island (tall jungle trees, vines, a ruined temple). The west half's
Birch Park bridges in at (45, 40). No mobs; logs and leaves can be chopped (and regrow).

    python tools/builds/hub_areas/park_east.py
"""
import math
from kit import Area, hash01

G = 64
AIR = (None, "minecraft:air")
area = Area("park_east", (0, 20, -150), (260, 120, 300), version=1, dimension="skyrpg_skyblock:the_park", island="The Park")

# ---------------------------------------------------------------- the three islands
TC, TR = (75, 45), 50       # Dark Thicket
SC, SR = (160, -45), 58     # Savanna Woodland
JC, JR = (205, 85), 50      # Jungle Island
thicket = area.mix({"podzol": 5, "moss_block": 3, "coarse_dirt": 2, "grass_block": 1, "rooted_dirt": 1})
savanna = area.mix({"grass_block": 6, "coarse_dirt": 3, "dirt": 1})
jungle = area.mix({"grass_block": 7, "podzol": 2, "moss_block": 2, "mud": 0.5})
cols_t = area.island(TC[0], TC[1], TR, top=G, seed=31, surface=thicket, hills=1.8, depth=36, wobble=0.15,
                     stone=area.mix({"tuff": 4, "andesite": 3, "cobblestone": 2, "mossy_cobblestone": 1}))
cols_s = area.island(SC[0], SC[1], SR, top=G, seed=37, surface=savanna, hills=2.5, depth=40, wobble=0.12,
                     stone=area.mix({"andesite": 4, "granite": 3, "tuff": 2, "cobblestone": 1}))
cols_j = area.island(JC[0], JC[1], JR, top=G, seed=43, surface=jungle, hills=2.2, depth=36, wobble=0.15,
                     stone=area.mix({"mossy_cobblestone": 3, "andesite": 3, "tuff": 3, "cobblestone": 1}))
cols = cols_t | cols_s | cols_j


def landing(px, pz, r, surf="minecraft:grass_block"):
    """Makes sure an island reaches a bridge end: a small round landing at y 64 where the island is missing."""
    for x in range(px - r, px + r + 1):
        for z in range(pz - r, pz + r + 1):
            if (x - px) ** 2 + (z - pz) ** 2 > r * r or (x, z) in cols:
                continue
            area.set(x, G, z, surf)
            for y in range(G - 1, G - 4, -1):
                area.set(x, y, z, "minecraft:dirt")
            depth = 3 + int((r - math.hypot(x - px, z - pz)) * 1.5)
            for y in range(G - 4, G - 4 - depth, -1):
                area.set(x, y, z, "minecraft:andesite" if hash01(x, y, z, 2) < 0.6 else "minecraft:tuff")
            cols.add((x, z))
            area.top[(x, z)] = G


# bridge ends: west (Birch Park), thicket->savanna, thicket->jungle, savanna->jungle
W_END = (45, 40)
TS_A, TS_B = (100, 15), (135, -25)
TJ_A, TJ_B = (115, 60), (170, 75)
SJ_A, SJ_B = (185, -2), (200, 45)
ENDS = [W_END, TS_A, TS_B, TJ_A, TJ_B, SJ_A, SJ_B]
for p in ENDS:
    landing(p[0], p[1], 6)
    area.flatten(p[0] - 5, p[1] - 5, p[0] + 5, p[1] + 5, G)

# flat spots
CAMPFIRE = (70, 55)          # the Campfire Cult's clearing
area.flatten(CAMPFIRE[0] - 9, CAMPFIRE[1] - 9, CAMPFIRE[0] + 9, CAMPFIRE[1] + 9, G, "minecraft:podzol")
CAMP = (150, -35)            # the Savanna camp
area.flatten(CAMP[0] - 12, CAMP[1] - 11, CAMP[0] + 12, CAMP[1] + 11, G, "minecraft:coarse_dirt")
ROCK = (190, -72)            # rock outcrop
JCL = (190, 74)              # jungle clearing (Molbert and Juliette)
area.flatten(JCL[0] - 9, JCL[1] - 8, JCL[0] + 9, JCL[1] + 8, G, "minecraft:grass_block")
TEMPLE = (228, 106)          # temple centre
area.flatten(TEMPLE[0] - 9, TEMPLE[1] - 9, TEMPLE[0] + 9, TEMPLE[1] + 9, G, "minecraft:mossy_cobblestone")

# ---------------------------------------------------------------- paths and bridges
pm_dark = area.mix({"coarse_dirt": 4, "dirt_path": 2, "podzol": 2})
pm_sav = area.mix({"dirt_path": 4, "coarse_dirt": 3, "gravel": 1})
pm_jun = area.mix({"dirt_path": 3, "coarse_dirt": 2, "mud": 1, "gravel": 1})
pcells = set()
pcells |= area.path([W_END, (55, 44), (62, 52), CAMPFIRE], width=3, blocks=pm_dark)
pcells |= area.path([CAMPFIRE, (85, 40), (95, 25), TS_A], width=3, blocks=pm_dark)
pcells |= area.path([CAMPFIRE, (95, 58), TJ_A], width=3, blocks=pm_dark)
pcells |= area.path([TS_B, (142, -28), CAMP], width=3, blocks=pm_sav)
pcells |= area.path([CAMP, (170, -50), (182, -62), ROCK], width=2, blocks=pm_sav)
pcells |= area.path([CAMP, (165, -20), (175, -10), SJ_A], width=3, blocks=pm_sav)
pcells |= area.path([TJ_B, (180, 74), JCL], width=3, blocks=pm_jun)
pcells |= area.path([SJ_B, (198, 58), JCL], width=3, blocks=pm_jun)
pcells |= area.path([JCL, (212, 90), (222, 100), (TEMPLE[0] - 6, TEMPLE[1] - 4)], width=3, blocks=pm_jun)

area.bridge((-20, 40), (45, 40), y=G)          # the same line as the west Park helper
area.bridge(TS_A, TS_B, y=G)
area.bridge(TJ_A, TJ_B, y=G)
area.bridge(SJ_A, SJ_B, y=G)


# ---------------------------------------------------------------- helpers
def disc(cx, y, cz, r, block, skip=0.0, seed=0, corners=True):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if not corners and abs(dx) == r and abs(dz) == r:
                continue
            if dx * dx + dz * dz > r * r + r:
                continue
            if skip and hash01(cx + dx, y, cz + dz, seed) < skip:
                continue
            if area.get(cx + dx, y, cz + dz) in AIR:
                area.set(cx + dx, y, cz + dz, block)


def big_tree(x, z, h, rx, ry, log, leaves, seed, vines=False, cocoa=False, lift=2):
    """A 2x2-trunk tree; (x, z) is the north-west trunk block. Canopy is an ellipsoid at the top."""
    base = max(area.top.get((x + a, z + b), G) for a in (0, 1) for b in (0, 1))
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(area.top.get((x + dx, z + dz), G) + 1, base + h + 1):
                area.set(x + dx, y, z + dz, f"minecraft:{log}[axis=y]")
    for dx, dz in ((-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):   # root flare
        if hash01(x + dx, base, z + dz, seed) < 0.45 and area.top.get((x + dx, z + dz)) == base:
            area.set(x + dx, base + 1, z + dz, f"minecraft:{log}[axis=y]")
    cx, cz, cy = x + 0.5, z + 0.5, base + h - lift
    for k in range(3):
        ang = hash01(x, k, z, seed) * 6.283
        for i in range(1, 4):
            bx, bz = int(round(cx + math.cos(ang) * i)), int(round(cz + math.sin(ang) * i))
            ax = "x" if abs(math.cos(ang)) > abs(math.sin(ang)) else "z"
            area.set(bx, cy + i // 2, bz, f"minecraft:{log}[axis={ax}]")
    for dx in range(-rx - 1, rx + 3):
        for dz in range(-rx - 1, rx + 3):
            for dy in range(-ry - 1, ry + 2):
                d = ((dx - 0.5) ** 2 + (dz - 0.5) ** 2) / (rx * rx) + (dy * dy) / (ry * ry)
                lump = 0.3 * (hash01(x + dx, cy + dy, z + dz, seed + 3) - 0.5)
                if d + lump <= 1 and hash01(x + dx, cy + dy, z + dz, seed) > 0.07:
                    px, py, pz = x + dx, cy + dy, z + dz
                    if area.get(px, py, pz) in AIR:
                        area.set(px, py, pz, f"minecraft:{leaves}[persistent=true]")
    if vines:
        for yy in range(base + 2, base + h):
            for dx, dz, face in ((-1, 0, "east"), (-1, 1, "east"), (2, 0, "west"), (2, 1, "west"),
                                 (0, -1, "south"), (1, -1, "south"), (0, 2, "north"), (1, 2, "north")):
                if hash01(x + dx, yy, z + dz, seed + 8) < 0.16 and area.get(x + dx, yy, z + dz) in AIR:
                    for k in range(1 + int(hash01(x + dx, yy, z + dz, seed + 9) * 3)):
                        if area.get(x + dx, yy - k, z + dz) in AIR:
                            area.set(x + dx, yy - k, z + dz, f"minecraft:vine[{face}=true]")
    if cocoa:
        for dx, dz, face in ((-1, 0, "east"), (2, 1, "west"), (0, -1, "south"), (1, 2, "north")):
            if hash01(x, dz, z, seed + 11) < 0.5:
                yy = base + 2 + int(hash01(x + dx, 3, z, seed) * 3)
                if area.get(x + dx, yy, z + dz) in AIR:
                    area.set(x + dx, yy, z + dz, f"minecraft:cocoa[age={int(hash01(x, yy, z) * 3)},facing={face}]")
    return cy + ry


def acacia(x, y, z, seed):
    """The crooked, flat-topped acacia: a leaning trunk that forks, each fork ending in a flat leaf plate."""
    h = 4 + int(hash01(x, 1, z, seed) * 3)
    dx, dz = [(1, 0), (-1, 0), (0, 1), (0, -1)][int(hash01(x, 2, z, seed) * 4) % 4]
    cx, cz = x, z
    kink = 2 + int(hash01(x, 3, z, seed) * 2)
    for i in range(h):
        if i == kink:
            cx, cz = cx + dx, cz + dz
        area.set(cx, y + i, cz, "minecraft:acacia_log[axis=y]")
    plates = [(cx, y + h, cz)]
    if hash01(x, 4, z, seed) < 0.75:   # a second limb off the kink, leaning the other way
        bx, bz, by = x, z, y + kink - 1
        for j in range(1, 3 + int(hash01(x, 5, z, seed) * 2)):
            bx, bz, by = bx - dx, bz - dz, by + 1
            area.set(bx, by, bz, "minecraft:acacia_log[axis=y]")
        plates.append((bx, by + 1, bz))
    for (px, py, pz) in plates:
        area.set(px, py, pz, "minecraft:acacia_log[axis=y]")
        disc(px, py, pz, 2, "minecraft:acacia_leaves[persistent=true]", 0.08, seed, corners=False)
        disc(px, py + 1, pz, 1, "minecraft:acacia_leaves[persistent=true]", 0.0, seed)
        disc(px, py - 1, pz, 1, "minecraft:acacia_leaves[persistent=true]", 0.3, seed + 2)


def dilate(cells, r):
    out = set()
    for (x, z) in cells:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                out.add((x + dx, z + dz))
    return out


near_path = dilate(pcells, 2)
NO_TREE = [(p[0], p[1], 8) for p in ENDS]       # (x, z, r)
NO_TREE += [(CAMPFIRE[0], CAMPFIRE[1], 13), (CAMP[0], CAMP[1], 17), (ROCK[0], ROCK[1], 12), (JCL[0], JCL[1], 13),
            (TEMPLE[0], TEMPLE[1], 14), (92, 78, 8)]


def blocked(x, z):
    if (x, z) in near_path:
        return True
    return any(math.hypot(x - cx, z - cz) < r for (cx, cz, r) in NO_TREE)


# ---------------------------------------------------------------- the Campfire Cult's clearing (Dark Thicket)
cx, cz = CAMPFIRE
area.set(cx, G + 1, cz, "minecraft:campfire[lit=true,facing=north]")
for i in range(8):
    ang = i * math.pi / 4
    lx, lz = int(round(cx + math.cos(ang) * 4)), int(round(cz + math.sin(ang) * 4))
    area.set(lx, G + 1, lz, "minecraft:stripped_dark_oak_log[axis=x]" if i % 2 == 0 else "minecraft:dark_oak_stairs[facing=south,half=bottom]")
for dx, dz in ((-7, -2), (7, 2), (-2, 7), (3, -7)):
    area.set(cx + dx, G + 1, cz + dz, "minecraft:dark_oak_fence")
    area.set(cx + dx, G + 2, cz + dz, "minecraft:soul_lantern[hanging=false]")
for dx, dz in ((-6, 5), (6, -5), (5, 6)):
    area.set(cx + dx, G + 1, cz + dz, "minecraft:soul_campfire[lit=true,facing=north]")
for dx, dz in ((-5, -5), (5, 5), (-6, 0)):       # cult candles
    area.set(cx + dx, G + 1, cz + dz, "minecraft:stripped_dark_oak_log[axis=y]")
    area.set(cx + dx, G + 2, cz + dz, "minecraft:candle[candles=3,lit=true]")
area.npc("Ryan", cx - 2, G + 1, cz - 2, yaw=-45.0, note="Campfire Cult; by the fire, faces it")
area.npc("Campfire Adept", cx + 2, G + 1, cz + 2, yaw=135.0, note="Campfire Cult's adept, opposite the fire")

# ---------------------------------------------------------------- the Savanna camp
cx, cz = CAMP


def tent(tx, tz, wool):
    """An A-frame tent of wool running along x, open at both ends."""
    for i in range(-2, 3):
        for j in (-2, -1, 0, 1, 2):
            height = 3 - abs(j)
            area.set(tx + i, G + height, tz + j, f"minecraft:{wool}_wool")
            for y in range(G + 1, G + height):
                area.set(tx + i, y, tz + j, "minecraft:air")
            if abs(i) == 2 and abs(j) == 2:
                area.set(tx + i, G + 1, tz + j, f"minecraft:{wool}_wool")
        if abs(i) == 2:                 # closed back wall at the west end only
            pass
    for i in (-2, 2):
        for y in (G + 1, G + 2):
            area.set(tx + i, y, tz, "minecraft:air")


tent(cx - 8, cz - 7, "orange")
tent(cx + 7, cz - 7, "white")
area.set(cx - 8, G + 1, cz - 7, "minecraft:red_bed[facing=east,part=foot]")
area.set(cx - 7, G + 1, cz - 7, "minecraft:red_bed[facing=east,part=head]")
area.set(cx + 7, G + 1, cz - 7, "minecraft:white_bed[facing=east,part=foot]")
area.set(cx + 8, G + 1, cz - 7, "minecraft:white_bed[facing=east,part=head]")
area.set(cx, G + 1, cz, "minecraft:campfire[lit=true,facing=north]")
for dx, dz in ((-2, 0), (2, 0), (0, 2), (0, -2)):
    area.set(cx + dx, G + 1, cz + dz, f"minecraft:stripped_acacia_log[axis={'z' if dx else 'x'}]")
# a shop stall for Master Tactician Funk
sx, sz = cx + 6, cz + 4
for dx in (-2, 2):
    for y in (1, 2, 3):
        area.set(sx + dx, G + y, sz - 1, "minecraft:acacia_fence")
        area.set(sx + dx, G + y, sz + 1, "minecraft:acacia_fence")
for dx in range(-3, 4):
    for dz in range(-2, 2):
        area.set(sx + dx, G + 4, sz + dz, "minecraft:red_carpet" if (dx + dz) % 2 else "minecraft:white_carpet")
for dx in range(-1, 2):
    area.set(sx + dx, G + 1, sz + 1, "minecraft:barrel[facing=up]" if dx else "minecraft:acacia_planks")
    area.set(sx + dx, G + 2, sz + 1, "minecraft:acacia_slab[type=bottom]")
area.set(sx - 2, G + 1, sz - 1, "minecraft:hay_block[axis=y]")
area.set(sx + 2, G + 1, sz - 2, "minecraft:lantern[hanging=false]")
area.npc("Master Tactician Funk", sx, G + 1, sz - 1, yaw=0.0, note="shop; behind his stall in the camp's south-east, faces south")
area.npc("Melody", cx - 2, G + 1, cz + 4, yaw=180.0, note="plays by the camp fire")
area.set(cx - 4, G + 1, cz + 4, "minecraft:note_block")
area.set(cx - 4, G + 1, cz + 5, "minecraft:jukebox")
# Worker Xavier at a half-built watchtower
wx, wz = cx - 9, cz + 6
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    for y in range(1, 6):
        area.set(wx + dx, G + y, wz + dz, "minecraft:acacia_log[axis=y]")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2:
            area.set(wx + dx, G + 6, wz + dz, "minecraft:acacia_planks")
area.set(wx, G + 1, wz + 3, "minecraft:scaffolding[bottom=false]")
area.set(wx + 3, G + 1, wz, "minecraft:barrel[facing=up]")
area.set(wx + 3, G + 2, wz, "minecraft:crafting_table")
area.npc("Worker Xavier", wx, G + 1, wz, yaw=90.0, note="inside the half-built watchtower")

# ---------------------------------------------------------------- the rock outcrop (Savanna)
rx0, rz0 = ROCK
best, rock_soul = -1, None
for x in range(rx0 - 8, rx0 + 9):
    for z in range(rz0 - 8, rz0 + 9):
        d = math.hypot(x - rx0, z - rz0) + 2.5 * (hash01(x, 91, z) - 0.5)
        if d > 7.5 or area.top.get((x, z)) is None:
            continue
        hgt = int(max(0, (7.5 - d) * 1.2) + 2 * hash01(x, 92, z))
        base = area.top[(x, z)]
        for y in range(base, base + hgt + 1):
            r = hash01(x, y, z, 93)
            area.set(x, y, z, "minecraft:andesite" if r < 0.4 else "minecraft:granite" if r < 0.7 else
                     "minecraft:tuff" if r < 0.85 else "minecraft:cobblestone")
        area.top[(x, z)] = base + hgt
        if base + hgt > best and math.hypot(x - rx0, z - rz0) < 5:
            best, rock_soul = base + hgt, (x, z)

# ---------------------------------------------------------------- Jungle clearing: Molbert and Juliette
jx, jz = JCL
for dx in range(-3, 4):
    for dz in range(-3, 4):
        area.set(jx + dx, G, jz + dz, "minecraft:jungle_planks" if (dx + dz) % 2 else "minecraft:stripped_jungle_log[axis=x]")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(jx + dx, G + 1, jz + dz, "minecraft:jungle_fence")
    area.set(jx + dx, G + 2, jz + dz, "minecraft:lantern[hanging=false]")
area.set(jx, G + 1, jz, "minecraft:campfire[lit=true,facing=north]")
area.set(jx - 3, G + 1, jz, "minecraft:jungle_stairs[facing=east,half=bottom]")
area.set(jx + 3, G + 1, jz, "minecraft:jungle_stairs[facing=west,half=bottom]")
area.npc("Molbert", jx - 2, G + 1, jz + 2, yaw=-135.0, note="by the jungle camp's fire")
area.npc("Juliette", jx + 2, G + 1, jz + 2, yaw=135.0, note="across the fire from Molbert")

# ---------------------------------------------------------------- the ruined temple corner (Jungle)
tx, tz = TEMPLE
BRK = ["minecraft:mossy_stone_bricks", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_cobblestone"]
for x in range(tx - 7, tx + 8):
    for z in range(tz - 7, tz + 8):
        area.set(x, G, z, "minecraft:mossy_stone_bricks" if hash01(x, 0, z, 4) < 0.6 else "minecraft:cracked_stone_bricks")
        for y in range(G + 1, G + 8):
            area.set(x, y, z, "minecraft:air")
        wall = max(abs(x - tx), abs(z - tz)) == 6
        corner = abs(x - tx) == 6 and abs(z - tz) == 6
        pillar = abs(x - tx) == 3 and abs(z - tz) == 3
        if not (wall or pillar):
            continue
        hmax = 6 if corner else 4 if pillar else 3 + int(hash01(x, 5, z, 6) * 4)
        if wall and abs(x - tx) == 6 and abs(z - tz) <= 1:
            hmax = 0                                         # a doorway on the west
        if wall and abs(z - tz) == 6 and x - tx > 2:
            hmax = 1 + int(hash01(x, 6, z, 7) * 2)           # the south-east wall has fallen
        for y in range(1, hmax + 1):
            if hash01(x, y, z, 8) < 0.1 + 0.05 * y:
                continue
            blk = "minecraft:chiseled_stone_bricks" if (corner and y == hmax) else BRK[int(hash01(x, y, z, 9) * 4) % 4]
            area.set(x, G + y, z, blk)
area.set(tx, G + 1, tz, "minecraft:chiseled_stone_bricks")
for x in range(tx - 9, tx + 10):                              # rubble and vines
    for z in range(tz - 9, tz + 10):
        if hash01(x, 12, z, 3) < 0.07 and area.get(x, G + 1, z) in AIR and area.top.get((x, z)) == G:
            area.set(x, G + 1, z, "minecraft:mossy_cobblestone_slab[type=bottom]" if hash01(x, 13, z) < 0.5 else "minecraft:mossy_cobblestone")
        for dx, dz, f in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
            if hash01(x, 14, z, 2) < 0.08:
                for y in range(G + 1, G + 5):
                    b = area.get(x + dx, y, z + dz)
                    if area.get(x, y, z) in AIR and b and "bricks" in b:
                        area.set(x, y, z, f"minecraft:vine[{f}=true]")
area.set(tx, G + 1, tz + 2, "minecraft:moss_carpet")
temple_soul = (tx, G + 1, tz + 1)


# ---------------------------------------------------------------- trees
def place_trees(colset, kind, count, spacing, seed):
    placed = []
    for (x, z) in sorted(colset, key=lambda p: hash01(p[0], 31, p[1], seed)):
        if len(placed) >= count:
            break
        y = area.top.get((x, z))
        if y is None or blocked(x, z) or blocked(x + 1, z + 1):
            continue
        if not all((x + a, z + b) in cols for a in (0, 1) for b in (0, 1)):
            continue
        if any(math.hypot(q[0] - x, q[1] - z) < spacing for q in placed):
            continue
        if area.get(x, y + 1, z) not in AIR:
            continue
        s = x * 7 + z
        if kind == "dark":
            top = big_tree(x, z, 7 + int(hash01(x, 33, z) * 4), 5 + int(hash01(x, 34, z) * 2), 3,
                           "dark_oak_log", "dark_oak_leaves", seed=s, lift=2)
        elif kind == "jungle":
            top = big_tree(x, z, 14 + int(hash01(x, 33, z) * 9), 4 + int(hash01(x, 34, z) * 2), 3,
                           "jungle_log", "jungle_leaves", seed=s, vines=True, cocoa=True, lift=2)
        else:
            acacia(x, y + 1, z, seed=s)
            top = y + 8
        placed.append((x, z, top))
    return placed


trees_t = place_trees(cols_t, "dark", 400, 5.2, 5)
trees_j = place_trees(cols_j, "jungle", 120, 6.0, 6)
for (x, z) in sorted(cols_j, key=lambda p: hash01(p[0], 41, p[1], 7)):     # thin jungle trees between the giants
    y = area.top.get((x, z))
    if y is None or blocked(x, z) or hash01(x, 42, z, 3) > 0.2 or area.get(x, y + 1, z) not in AIR:
        continue
    if any(math.hypot(q[0] - x, q[1] - z) < 5 for q in trees_j):
        continue
    h = 7 + int(hash01(x, 44, z) * 6)
    for hh in range(h):
        area.set(x, y + 1 + hh, z, "minecraft:jungle_log[axis=y]")
    for dy in range(-1, 3):
        disc(x, y + h + dy, z, 3 - (1 if dy == 2 else 0), "minecraft:jungle_leaves[persistent=true]", 0.1, x + z)
    trees_j.append((x, z, y + h + 3))
trees_s = place_trees(cols_s, "acacia", 160, 8.5, 7)
print("trees:", len(trees_t), len(trees_j), len(trees_s))


# Keep walkways open: no leaves hanging into the path, bridge ends and clearings at head height.
keep_open = set(pcells)
for (cx0, cz0, r0) in ((CAMPFIRE[0], CAMPFIRE[1], 9), (CAMP[0], CAMP[1], 12), (JCL[0], JCL[1], 9), (TEMPLE[0], TEMPLE[1], 8)) +         tuple((p[0], p[1], 6) for p in ENDS):
    for x in range(cx0 - r0, cx0 + r0 + 1):
        for z in range(cz0 - r0, cz0 + r0 + 1):
            if math.hypot(x - cx0, z - cz0) <= r0:
                keep_open.add((x, z))
for (x, z) in keep_open:
    y = area.top.get((x, z))
    if y is None:
        continue
    for yy in range(y + 1, y + 5):
        b = area.get(x, yy, z)
        if b and b.endswith("_leaves"):
            area.set(x, yy, z, "minecraft:air")


# ---------------------------------------------------------------- ground cover
def dark_floor(x, z):
    r = hash01(x, 61, z)
    return ("fern" if r < 0.22 else "short_grass" if r < 0.32 else "brown_mushroom" if r < 0.46 else "red_mushroom" if r < 0.52 else
            "moss_carpet" if r < 0.62 else "dark_oak_sapling[stage=0]" if r < 0.66 else "dead_bush" if r < 0.68 else
            "dark_oak_leaves[persistent=true]" if r < 0.72 else None)


def sav_floor(x, z):
    r = hash01(x, 62, z)
    return ("short_grass" if r < 0.55 else "dead_bush" if r < 0.68 else "fern" if r < 0.74 else
            "acacia_sapling[stage=0]" if r < 0.77 else "dandelion" if r < 0.80 else "orange_tulip" if r < 0.83 else None)


def jun_floor(x, z):
    r = hash01(x, 63, z)
    return ("fern" if r < 0.34 else "short_grass" if r < 0.56 else "jungle_leaves[persistent=true]" if r < 0.63 else
            "jungle_sapling[stage=0]" if r < 0.67 else "melon" if r < 0.69 else "azalea" if r < 0.72 else
            "brown_mushroom" if r < 0.75 else "lily_of_the_valley" if r < 0.78 else None)


area.scatter(cols_t, 0.55, dark_floor, seed=9)
area.scatter(cols_s, 0.5, sav_floor, seed=10)
area.scatter(cols_j, 0.6, jun_floor, seed=11)

# ---------------------------------------------------------------- Fairy Souls (4)
# 1: Dark Thicket: a leaf-walled hollow in the woods (south-east), reached by a gap.
HX, HZ = 92, 78
area.flatten(HX - 4, HZ - 4, HX + 4, HZ + 4, G, "minecraft:moss_block")
area.fill(HX - 3, G + 1, HZ - 3, HX + 3, G + 5, HZ + 3, "air")
for dx in range(-4, 5):
    for dz in range(-4, 5):
        edge = max(abs(dx), abs(dz))
        if edge == 4 or (edge == 3 and hash01(HX + dx, 81, HZ + dz) < 0.5):
            for y in range(G + 1, G + 5):
                area.set(HX + dx, y, HZ + dz, "minecraft:dark_oak_leaves[persistent=true]")
        area.set(HX + dx, G + 5, HZ + dz, "minecraft:dark_oak_leaves[persistent=true]")
for y in (G + 1, G + 2, G + 3):
    area.set(HX, y, HZ - 4, "air")
    area.set(HX, y, HZ - 3, "air")
area.set(HX + 1, G + 1, HZ + 1, "minecraft:red_mushroom")
area.set(HX - 1, G + 1, HZ + 1, "minecraft:brown_mushroom")
area.fairy_soul(HX, G + 1, HZ)
# 2: Savanna: on top of the rock outcrop.
area.fairy_soul(rock_soul[0], best + 1, rock_soul[1])
# 3: Jungle: in the temple.
area.fairy_soul(*temple_soul)
# 4: Jungle: on top of the tallest jungle tree.
tt = max(trees_j, key=lambda t: t[2])
area.set(tt[0], tt[2] + 1, tt[1], "minecraft:jungle_leaves[persistent=true]")
area.fill(tt[0], tt[2] + 2, tt[1], tt[0], tt[2] + 3, tt[1], "air")
area.fairy_soul(tt[0], tt[2] + 2, tt[1])

area.spots["notes"].append("Bridge to Birch Park (west half) meets this island at (45, 64, 40)")
area.spots["notes"].append(f"Campfire Cult clearing {CAMPFIRE}; Savanna camp {CAMP}; rock outcrop {ROCK}; "
                           f"Jungle clearing {JCL}; ruined temple {TEMPLE}")

# ---------------------------------------------------------------- zones (boxes separated in x/z, no overlap)
area.zone("Dark Thicket", box=((0, 40, 0), (130, 115, 110)),
          blocks={"minecraft:dark_oak_log": {"becomes": "minecraft:air", "regrow": 20},
                  "minecraft:dark_oak_leaves": {"becomes": "minecraft:air", "regrow": 15}})
area.zone("Savanna Woodland", box=((100, 40, -110), (225, 115, -1)),
          blocks={"minecraft:acacia_log": {"becomes": "minecraft:air", "regrow": 20},
                  "minecraft:acacia_leaves": {"becomes": "minecraft:air", "regrow": 15}})
area.zone("Jungle Island", box=((140, 40, 20), (259, 125, 140)),
          blocks={"minecraft:jungle_log": {"becomes": "minecraft:air", "regrow": 20},
                  "minecraft:jungle_leaves": {"becomes": "minecraft:air", "regrow": 15}})
area.save()
