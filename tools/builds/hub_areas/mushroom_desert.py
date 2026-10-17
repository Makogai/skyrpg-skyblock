#!/usr/bin/env python3
"""The Mushroom Desert (Hypixel's Farming Islands, east of the Barn): a floating island of sand and red sand dunes, cactus
fields, Sugar Cane along water channels, an oasis with palms that bear Cocoa Beans, a mycelium field with a big mushroom
cave and giant mushrooms, a small desert settlement (Jake's shop, market stalls, Farmer Jon), Trevor the Trapper's den
and a sheep pen with the Shepherd. Rabbits, Sheep and Mooshrooms.

    python tools/builds/hub_areas/mushroom_desert.py
"""
import math

from kit import Area, hash01, noise

G = 64                      # surface; players stand at 65
CX, CZ = 232, 0             # island centre
area = Area("mushroom_desert", (112, 20, -125), (240, 100, 250), version=1,
            dimension="skyrpg_skyblock:farming_islands", island="The Farming Islands")


def bump(x, z):   # stretch the west edge so the island meets the bridge end (142, 0)
    return 1 + 0.10 * math.exp(-((x - 140) ** 2 + (z - 0) ** 2) / (2 * 14 ** 2))


def dunes(x, y, z, s=0):
    n = noise(x, z, 3, 1.3)
    if n > 0.12:
        return "minecraft:sand"
    r = hash01(x, 5, z, 2)
    if n < -0.45 and r < 0.25:
        return "minecraft:terracotta" if r < 0.12 else "minecraft:orange_terracotta"
    return "minecraft:red_sand"


stone_mix = area.mix({"sandstone": 4, "red_sandstone": 3, "terracotta": 2, "orange_terracotta": 2, "brown_terracotta": 1,
                      "andesite": 2, "tuff": 1})
cols = area.island(CX, CZ, 90, top=G, seed=31, surface=dunes, hills=3.5, depth=36, wobble=0.08, shape=bump,
                   dirt="sandstone", stone=stone_mix)


def flat(x1, z1, x2, z2, y, block=None):
    """Like area.flatten but fills dips with sandstone; `block` may be a string or a picker (x, y, z) -> block."""
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            old = area.top.get((x, z))
            if old is None:
                continue
            surf = area.get(x, old, z)
            for yy in range(y + 1, max(old, y) + 1):
                area.set(x, yy, z, "minecraft:air")
            for yy in range(old, y):
                area.set(x, yy, z, "minecraft:sandstone")
            b = block(x, y, z) if callable(block) else block
            area.set(x, y, z, b or surf or "minecraft:red_sand")
            area.top[(x, z)] = y


def ensure(x1, z1, x2, z2):
    """Makes sure every column in a rectangle exists (so nothing hangs in the void)."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if (x, z) not in area.top:
                for yy in range(G, G - 8, -1):
                    area.set(x, yy, z, "minecraft:sandstone")
                area.top[(x, z)] = G
                cols.add((x, z))


reserved = []


def reserve(x1, z1, x2, z2):
    reserved.append((x1, x2, z1, z2))


def in_reserved(x, z):
    return any(a <= x <= b and c <= z <= d for a, b, c, d in reserved)


ground_mix = area.mix({"sandstone": 3, "red_sandstone": 1, "cut_sandstone": 1})
path_mix = area.mix({"smooth_sandstone": 3, "cut_sandstone": 2, "red_sandstone": 1})
sign_text = lambda a, b: {"id": "minecraft:sign", "front_text": {"messages": ['""', '"%s"' % a, '"%s"' % b, '""'],
                                                                 "color": "white", "has_glowing_text": 1},
                          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0},
                          "is_waxed": 1}

# ---------------------------------------------------------------- arrival plaza (west edge, past the bridge)
ensure(138, -9, 160, 9)
flat(139, -10, 162, 10, G, path_mix)
reserve(138, -12, 166, 12)
for dz in (-4, 4):
    area.fill(156, G + 1, dz, 156, G + 4, dz, "chiseled_sandstone")
    area.set(156, G + 5, dz, "lantern[hanging=false]")
area.fill(156, G + 5, -3, 156, G + 5, 3, "sandstone_slab[type=bottom]")
area.fill(156, G + 4, -3, 156, G + 4, 3, "air")
area.set(156, G + 4, 0, "chain[axis=y]")
area.set(156, G + 3, 0, "lantern[hanging=true]")
for dz in (-8, 8):
    area.fill(150, G + 1, dz, 150, G + 2, dz, "sandstone_wall")
    area.set(150, G + 3, dz, "lantern[hanging=false]")
area.set(152, G + 1, -7, "jungle_sign[rotation=4]", sign_text("Mushroom Desert", "Farming Islands"))

# ---------------------------------------------------------------- the desert settlement
flat(164, -26, 212, 26, G, ground_mix)
reserve(160, -28, 214, 28)
for pts, w in (([(156, 0), (186, 0), (212, 0)], 3), ([(186, -24), (186, 24)], 3), ([(148, 0), (158, 0)], 3)):
    area.path(pts, width=w, blocks=path_mix)
area.path([(212, 0), (232, 0), (258, -8)], width=3, blocks=path_mix)      # on to the oasis


def house(x1, z1, x2, z2, side, dx, h=4, wall="cut_sandstone", door="acacia_door"):
    """A flat-roofed sandstone house; the door is on `side` ('n','s','e','w') at offset dx along that wall."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            corner = x in (x1, x2) and z in (z1, z2)
            area.set(x, G, z, "smooth_sandstone")
            for y in range(G + 1, G + h + 1):
                if corner:
                    area.set(x, y, z, "sandstone" if y < G + h else "chiseled_sandstone")
                elif edge:
                    win = y == G + 2 and ((x - x1 + z - z1) % 4 == 2)
                    area.set(x, y, z, "glass_pane" if win else wall)
                else:
                    area.set(x, y, z, "air")
            area.set(x, G + h + 1, z, "sandstone_slab[type=bottom]" if not edge else "sandstone")
            if edge and not corner:
                area.set(x, G + h + 2, z, "sandstone_wall")
    if side == "s":
        dpos, face = (x1 + dx, z2), "south"
    elif side == "n":
        dpos, face = (x1 + dx, z1), "north"
    elif side == "e":
        dpos, face = (x2, z1 + dx), "east"
    else:
        dpos, face = (x1, z1 + dx), "west"
    area.set(dpos[0], G + 1, dpos[1], f"minecraft:{door}[facing={face},half=lower,hinge=left]")
    area.set(dpos[0], G + 2, dpos[1], f"minecraft:{door}[facing={face},half=upper,hinge=left]")
    area.set((x1 + x2) // 2, G + h, (z1 + z2) // 2, "lantern[hanging=true]")
    area.set((x1 + x2) // 2, G + h + 1, (z1 + z2) // 2, "sandstone")


def awning(x1, z1, x2, z2, y):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            area.set(x, y, z, "orange_wool" if (x + z) % 2 == 0 else "white_wool")


def stall(x, z, facing, goods):
    """A market stall: 5 wide (along x), 3 deep, counter at the front, striped awning."""
    for dx in (0, 4):
        for dz in (0, 2):
            area.fill(x + dx, G + 1, z + dz, x + dx, G + 3, z + dz, "jungle_fence")
    awning(x - 1, z - 1, x + 5, z + 3, G + 4)
    front = z + 2 if facing == "s" else z
    for i in range(1, 4):
        area.set(x + i, G + 1, front, "smooth_sandstone_slab[type=top]" if i != 2 else goods)
    back = z if facing == "s" else z + 2
    for i in range(1, 4):
        area.set(x + i, G + 1, back, ["barrel[facing=up]", "hay_block[axis=x]", "barrel[facing=up]"][i - 1])
    area.set(x + 2, G + 3, z + 1, "lantern[hanging=true]")


# Jake's shop (north of the main street), a bakery-ish house, two houses on the south side
house(170, -17, 181, -7, "s", 5)
house(192, -17, 202, -9, "s", 5)
house(170, 7, 180, 17, "n", 5)
house(195, 7, 205, 16, "n", 5)
# Jake's shop details: counter and shelves
for x in range(172, 180):
    area.set(x, G + 1, -14, ["barrel[facing=up]", "smooth_sandstone_slab[type=top]", "smooth_sandstone_slab[type=top]", "crafting_table",
                              "smooth_sandstone_slab[type=top]", "smooth_sandstone_slab[type=top]", "barrel[facing=up]", "composter[level=4]"][x - 172])
for x in (171, 181):
    area.set(x if x == 171 else 180, G + 1, -9, "barrel[facing=up]")
area.set(173, G + 2, -16, "flower_pot")
area.set(177, G + 3, -16, "lantern[hanging=true]")
area.fill(173, G + 1, -16, 178, G + 1, -16, "barrel[facing=up]")
awning(172, -6, 179, -5, G + 4)       # awning over the entrance
for x in (172, 179):
    area.fill(x, G + 1, -5, x, G + 3, -5, "jungle_fence")
area.npc("Jake", 175, G + 1, -9, yaw=0.0, note="inside his shop on the north side of the main street; sells desert goods")
area.set(171, G + 1, -6, "jungle_sign[rotation=0]", sign_text("Jake's", "Shop"))
# House interiors, a bit of furniture
for (x, z) in ((194, -11), (172, 9), (197, 9)):
    area.set(x, G + 1, z, "red_bed[facing=north,part=foot]")
    area.set(x, G + 1, z - 1, "red_bed[facing=north,part=head]")
area.set(200, G + 1, -11, "barrel[facing=up]")
area.set(178, G + 1, 9, "crafting_table")
area.set(203, G + 1, 9, "barrel[facing=up]")
# stalls
stall(160, -8, "s", "melon")
stall(160, 6, "n", "pumpkin")
stall(206, -8, "s", "hay_block[axis=x]")
stall(206, 6, "n", "melon")
# the well at the crossroads' corner
for dx in range(-1, 2):
    for dz in range(-1, 2):
        area.set(189 + dx, G, 4 + dz, "sandstone")
        if dx or dz:
            area.set(189 + dx, G + 1, 4 + dz, "sandstone_wall" if dx and dz else "sandstone_slab[type=bottom]")
area.set(189, G, 4, "water")
area.set(189, G - 1, 4, "water")
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    area.fill(189 + dx, G + 2, 4 + dz, 189 + dx, G + 3, 4 + dz, "jungle_fence")
area.fill(188, G + 4, 3, 190, G + 4, 5, "sandstone_slab[type=bottom]")
# lamp posts along the main street
for x in (166, 178, 198, 210):
    for z in (-3, 3):
        area.fill(x, G + 1, z, x, G + 2, z, "jungle_fence")
        area.set(x, G + 3, z, "lantern[hanging=false]")

# ---------------------------------------------------------------- Trevor the Trapper's den
TX1, TX2, TZ1, TZ2 = 195, 205, -61, -52
flat(186, -70, 216, -44, G, area.mix({"brown_terracotta": 3, "coarse_dirt": 2, "red_sandstone": 3}))
reserve(184, -72, 218, -42)
area.path([(200, -42), (200, -26), (200, -50)], width=3, blocks=path_mix)
for x in range(TX1, TX2 + 1):
    for z in range(TZ1, TZ2 + 1):
        edge = x in (TX1, TX2) or z in (TZ1, TZ2)
        corner = x in (TX1, TX2) and z in (TZ1, TZ2)
        area.set(x, G, z, "dark_oak_planks")
        for y in range(G + 1, G + 5):
            if corner:
                area.set(x, y, z, "jungle_log[axis=y]")
            elif edge:
                win = y == G + 2 and ((z == TZ1 and x in (198, 202)) or (x in (TX1, TX2) and z == -57))
                area.set(x, y, z, "glass_pane" if win else ("brown_wool" if hash01(x, y, z, 6) < 0.35 else "brown_terracotta"))
            else:
                area.set(x, y, z, "air")
# a tent-like gabled roof along x: brown wool with log trim
for i in range(0, 6):
    zl, zr = TZ1 - 1 + i, TZ2 + 1 - i
    for x in range(TX1 - 1, TX2 + 2):
        if zl == zr:
            area.set(x, G + 5 + i, zl, "brown_wool")
        elif zl < zr:
            area.set(x, G + 5 + i, zl, "jungle_stairs[facing=south,half=bottom]")
            area.set(x, G + 5 + i, zr, "jungle_stairs[facing=north,half=bottom]")
    if zl < zr:
        for z in range(zl + 1, zr):
            for x in (TX1, TX2):
                area.set(x, G + 5 + i, z, "brown_terracotta")
area.set(200, G + 1, TZ2, "jungle_door[facing=south,half=lower,hinge=left]")
area.set(200, G + 2, TZ2, "jungle_door[facing=south,half=upper,hinge=left]")
area.set(201, G + 2, TZ2 + 1, "lantern[hanging=false]")
area.set(201, G + 1, TZ2 + 1, "jungle_fence")
# inside: furs on the floor, a bed of hides, trapper's table, bones
for x in range(197, 204):
    for z in range(-60, -56):
        area.set(x, G + 1, z, "brown_carpet" if (x + z) % 2 else "white_carpet")
area.set(196, G + 1, -60, "red_bed[facing=east,part=foot]")
area.set(197, G + 1, -60, "red_bed[facing=east,part=head]")
area.set(204, G + 1, -60, "barrel[facing=up]")
area.set(204, G + 1, -59, "crafting_table")
area.set(204, G + 1, -58, "cauldron")
area.set(196, G + 1, -53, "barrel[facing=up]")
area.set(196, G + 1, -54, "bone_block[axis=y]")
area.set(204, G + 1, -53, "campfire[lit=false,facing=south]")
area.set(200, G + 4, -56, "chain[axis=y]")
area.set(200, G + 3, -56, "lantern[hanging=true]")
area.npc("Trevor", 200, G + 1, -55, yaw=0.0, note="inside his den; Trevor the Trapper hands out animal trapping quests")
area.spots["notes"].append("Trevor the Trapper's den: hut x 195..205, z -61..-52, door on the south side (200, 65, -52)")
# drying racks with hides (brown wool stretched between fence posts) east of the den
for z in (-66, -62, -58):
    for x in (209, 214):
        area.fill(x, G + 1, z, x, G + 4, z, "jungle_fence")
    for x in range(210, 214):
        area.set(x, G + 4, z, "jungle_fence")
        for y in (G + 1, G + 2, G + 3):
            area.set(x, y, z, "brown_wool" if (x + y) % 2 == 0 else "white_wool" if hash01(x, y, z) < 0.3 else "brown_terracotta")
        area.set(x, G + 1, z, "air")          # the hides hang free of the ground
# a trap pen: fenced square with hay and a campfire
for x in range(207, 216):
    for z in range(-52, -45):
        if x in (207, 215) or z in (-52, -46):
            if not (x == 207 and z == -49):
                area.set(x, G + 1, z, "jungle_fence")
        elif hash01(x, 0, z, 9) < 0.18:
            area.set(x, G + 1, z, "hay_block[axis=x]")
area.set(211, G + 1, -49, "campfire[lit=true,facing=north]")
area.set(207, G + 1, -49, "jungle_fence_gate[facing=west,open=false]")
for (bx, bz) in ((190, -64), (191, -48), (193, -66)):
    area.set(bx, G + 1, bz, "bone_block[axis=x]")
for x in (197, 203):
    area.fill(x, G + 1, -50, x, G + 2, -50, "jungle_fence")
    area.set(x, G + 3, -50, "lantern[hanging=false]")
area.fairy_soul(200, G + 1, -64)          # behind the den, tucked under the roof's back eave
area.fill(198, G + 1, -64, 202, G + 3, -64, "air")
area.set(199, G + 1, -64, "hay_block[axis=x]")
area.set(201, G + 1, -64, "hay_block[axis=x]")
area.set(200, G + 1, -65, "hay_block[axis=x]")

# ---------------------------------------------------------------- the sheep pen and the Shepherd
SPX1, SPX2, SPZ1, SPZ2 = 220, 240, -42, -24
flat(SPX1 - 3, SPZ1 - 3, SPX2 + 3, SPZ2 + 3, G, area.mix({"coarse_dirt": 3, "dirt": 1, "red_sandstone": 1}))
reserve(SPX1 - 4, SPZ1 - 4, SPX2 + 4, SPZ2 + 4)
for x in range(SPX1, SPX2 + 1):
    for z in range(SPZ1, SPZ2 + 1):
        if x in (SPX1, SPX2) or z in (SPZ1, SPZ2):
            if x in (SPX1, SPX2) and z in (SPZ1, SPZ2):
                area.fill(x, G + 1, z, x, G + 2, z, "jungle_fence")
                area.set(x, G + 3, z, "lantern[hanging=false]")
            elif z == SPZ2 and x == 230:
                area.set(x, G + 1, z, "jungle_fence_gate[facing=south,open=false]")
            else:
                area.set(x, G + 1, z, "jungle_fence")
        else:
            r = hash01(x, 3, z, 5)
            area.set(x, G, z, "grass_block" if r < 0.5 else "coarse_dirt")
            if r < 0.12:
                area.set(x, G + 1, z, "short_grass")
for (hx, hz) in ((222, -40), (223, -40), (238, -40), (238, -39)):
    area.set(hx, G + 1, hz, "hay_block[axis=x]")
area.set(232, G + 1, -40, "cauldron")
area.path([(230, -23), (230, -8), (232, 0)], width=3, blocks=path_mix)
area.npc("Shepherd", 230, G + 1, -22, yaw=180.0, note="outside the sheep pen's south gate")
area.set(228, G + 1, -22, "hay_block[axis=x]")
area.set(232, G + 1, -22, "jungle_sign[rotation=8]", sign_text("Sheep Pen", "no biting"))

# ---------------------------------------------------------------- cactus fields (Farmer Jon)
CF = (190, 30, 232, 60)
flat(CF[0] - 3, CF[1] - 3, CF[2] + 3, CF[3] + 3, G, "minecraft:sand")
reserve(CF[0] - 5, CF[1] - 5, CF[2] + 5, CF[3] + 5)
cactus = 0
for x in range(CF[0], CF[2] + 1):
    for z in range(CF[1], CF[3] + 1):
        if (x, z) not in area.top:
            continue
        walk = (x - CF[0]) % 12 in (10, 11) or (z - CF[1]) % 15 in (13, 14)
        if walk:
            area.set(x, G, z, path_mix(x, G, z))
            continue
        area.set(x, G, z, "minecraft:sand")
        if x % 2 == 0 and z % 2 == 0 and hash01(x, 8, z, 3) < 0.88:
            for h in range(2 + int(hash01(x, 9, z, 3) * 1.99)):
                area.set(x, G + 1 + h, z, "cactus[age=0]")
            cactus += 1
for (x, z) in ((CF[0] - 3, CF[1] - 3), (CF[2] + 3, CF[1] - 3), (CF[0] - 3, CF[3] + 3), (CF[2] + 3, CF[3] + 3)):
    area.fill(x, G + 1, z, x, G + 3, z, "chiseled_sandstone")
    area.set(x, G + 4, z, "lantern[hanging=false]")
area.path([(212, 26), (212, 36)], width=2, blocks=path_mix)
area.path([(186, 24), (186, 30), (190, 30)], width=2, blocks=path_mix)
# Farmer Jon's stand at the field's north-west corner
for dx in (0, 4):
    for dz in (0, 3):
        area.fill(180 + dx, G + 1, 31 + dz, 180 + dx, G + 3, 31 + dz, "jungle_fence")
area.fill(179, G + 4, 30, 185, G + 4, 35, "acacia_slab[type=bottom]")
area.fill(181, G + 1, 34, 183, G + 1, 34, "barrel[facing=up]")
area.set(182, G + 2, 34, "flower_pot")
area.set(181, G + 1, 32, "hay_block[axis=x]")
area.set(183, G + 1, 32, "hay_block[axis=x]")
area.set(182, G + 3, 33, "lantern[hanging=true]")
for (hx, hz) in ((186, 33), (186, 34)):
    area.set(hx, G + 1, hz, "hay_block[axis=y]")
area.npc("Farmer Jon", 182, G + 1, 32, yaw=0.0, note="in his stand by the cactus field's north-west corner")
# scarecrows in the field's walkways
for (sx, sz) in ((CF[0] + 10, CF[1] + 7), (CF[0] + 22, CF[1] + 22), (CF[0] + 34, CF[1] + 7)):
    area.fill(sx, G, sz, sx, G, sz, "sandstone")
    area.set(sx, G + 1, sz, "jungle_fence")
    area.set(sx, G + 2, sz, "hay_block[axis=y]")
    area.set(sx, G + 3, sz, "carved_pumpkin[facing=south]")
    area.set(sx - 1, G + 2, sz, "jungle_fence")
    area.set(sx + 1, G + 2, sz, "jungle_fence")

# ---------------------------------------------------------------- Sugar Cane along water channels
SC = (240, 28, 292, 56)
flat(SC[0] - 3, SC[1] - 3, SC[2] + 3, SC[3] + 3, G, area.mix({"red_sand": 4, "coarse_dirt": 2, "red_sandstone": 1}))
reserve(SC[0] - 5, SC[1] - 5, SC[2] + 5, SC[3] + 5)
canes = 0
for k in range(7):
    wx = SC[0] + 2 + k * 8
    for z in range(SC[1], SC[3] + 1):
        area.set(wx, G, z, "water")
        area.set(wx, G - 1, z, "water")
        area.set(wx, G - 2, z, "sandstone")
        for bx in (wx - 1, wx + 1):
            area.set(bx, G, z, "red_sand")
            area.set(bx, G - 1, z, "sandstone")
            if hash01(bx, 4, z, 7) < 0.8 and (z - SC[1]) % 3 != 2:
                for h in range(2 + int(hash01(bx, 5, z, 7) * 1.99)):
                    area.set(bx, G + 1 + h, z, "sugar_cane[age=0]")
                canes += 1
    for z in (SC[1] - 1, SC[3] + 1):
        area.set(wx, G, z, "sandstone")
for x in range(SC[0] - 2, SC[2] + 3):
    if (x - SC[0] - 2) % 8 in (3, 4, 5):
        for z in (SC[1] + 14,):
            area.set(x, G, z, path_mix(x, G, z))
area.path([(256, 24), (256, 8), (262, 0)], width=2, blocks=path_mix)
area.path([(258, 26), (232, 26), (212, 26)], width=2, blocks=path_mix)

# ---------------------------------------------------------------- the oasis: a pond with palms and Cocoa Beans
OX, OZ = 264, -6
flat(OX - 20, OZ - 20, OX + 20, OZ + 20, G, area.mix({"red_sand": 3, "sand": 2, "sandstone": 1}))
reserve(OX - 20, OZ - 20, OX + 20, OZ + 20)
for x in range(OX - 19, OX + 20):
    for z in range(OZ - 19, OZ + 20):
        if (x, z) not in area.top:
            continue
        ang = math.atan2(z - OZ, x - OX)
        r = 9 + 1.6 * math.sin(3 * ang + 1) + 0.8 * math.sin(5 * ang)
        d = math.hypot(x - OX, z - OZ)
        if d < r:
            area.set(x, G, z, "water")
            area.set(x, G - 1, z, "water")
            if d < r - 3:
                area.set(x, G - 2, z, "water")
                area.set(x, G - 3, z, "sandstone")
            else:
                area.set(x, G - 2, z, "sandstone")
        elif d < r + 4:
            q = hash01(x, 12, z, 4)
            area.set(x, G, z, "grass_block" if q < 0.6 else "coarse_dirt" if q < 0.7 else "moss_block" if q < 0.78 else "red_sand")
            if q < 0.3:
                area.set(x, G + 1, z, "short_grass" if q < 0.2 else "fern")
            elif q > 0.985:
                area.set(x, G + 1, z, "dandelion")
FACE_TO_LOG = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}


def palm(x, z, height, lean, seed):
    """A palm: a leaning jungle trunk, fronds on top, Cocoa Beans on the upper trunk."""
    logs = []
    for i in range(height):
        s = i // 3
        lx, lz = x + lean[0] * s, z + lean[1] * s
        area.set(lx, G + 1 + i, lz, "jungle_log[axis=y]")
        logs.append((lx, G + 1 + i, lz))
    tx, ty, tz = logs[-1]
    area.set(tx, ty + 1, tz, "jungle_leaves[persistent=true]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        area.set(tx + dx, ty, tz + dz, "jungle_leaves[persistent=true]")
        area.set(tx + dx * 2, ty, tz + dz * 2, "jungle_leaves[persistent=true]")
        area.set(tx + dx * 3, ty - 1, tz + dz * 3, "jungle_leaves[persistent=true]")
        area.set(tx + dx * 4, ty - 2, tz + dz * 4, "jungle_leaves[persistent=true]")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        area.set(tx + dx, ty, tz + dz, "jungle_leaves[persistent=true]")
        area.set(tx + dx * 2, ty - 1, tz + dz * 2, "jungle_leaves[persistent=true]")
        area.set(tx + dx * 2, ty - 2, tz + dz * 2, "jungle_leaves[persistent=true]")
    beans = 0
    for (lx, ly, lz) in logs[-4:-1]:
        for (dx, dz), face in FACE_TO_LOG.items():
            cell = area.get(lx + dx, ly, lz + dz)
            if cell in (None, "minecraft:air") and hash01(lx + dx, ly, lz + dz, seed) < 0.55:
                area.set(lx + dx, ly, lz + dz, f"cocoa[age=2,facing={face}]")
                beans += 1
    return logs, beans


palms = 0
beans = 0
palm_logs = []
for i in range(8):
    ang = i * math.pi / 4 + 0.3
    px, pz = int(round(OX + 13.5 * math.cos(ang))), int(round(OZ + 13.5 * math.sin(ang)))
    to_pond = (-1 if px > OX else 1, 0) if abs(px - OX) > abs(pz - OZ) else (0, -1 if pz > OZ else 1)
    logs, b = palm(px, pz, 7 + i % 3, to_pond, i)
    palm_logs.append(logs)
    palms += 1
    beans += b
# the palm with the highest crown: a soul on its fronds
top_logs = palm_logs[2]
area.fairy_soul(top_logs[-1][0], top_logs[-1][1] + 2, top_logs[-1][2])
area.set(top_logs[-1][0], top_logs[-1][1] + 1, top_logs[-1][2], "jungle_leaves[persistent=true]")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
    area.set(top_logs[-1][0] + dx, top_logs[-1][1] + 1, top_logs[-1][2] + dz, "jungle_leaves[persistent=true]")
# a few sugar canes and a bench at the pond, a little shade hut
for k, (cx_, cz_) in enumerate(((OX - 7, OZ + 8), (OX + 6, OZ + 9), (OX - 9, OZ - 3))):
    area.set(cx_, G + 1, cz_, "sugar_cane[age=0]") if area.get(cx_, G, cz_) == "minecraft:water" else None
for dx in (-2, 2):
    area.fill(OX + dx, G + 1, OZ + 15, OX + dx, G + 3, OZ + 15, "jungle_fence")
area.fill(OX - 3, G + 4, OZ + 14, OX + 3, G + 4, OZ + 16, "acacia_slab[type=bottom]")
area.set(OX, G + 1, OZ + 15, "acacia_stairs[facing=north,half=bottom]")
area.set(OX - 1, G + 1, OZ + 15, "acacia_stairs[facing=north,half=bottom]")
area.set(OX + 1, G + 1, OZ + 15, "acacia_stairs[facing=north,half=bottom]")

# ---------------------------------------------------------------- mushroom field and cave
MX, MZ = 282, -54            # cave centre
flat(MX - 28, MZ - 28, MX + 28, MZ + 28, G)
reserve(MX - 30, MZ - 30, MX + 30, MZ + 30)
myc = set()
for x in range(MX - 28, MX + 29):
    for z in range(MZ - 28, MZ + 29):
        if (x, z) not in area.top:
            continue
        d = math.hypot(x - MX, z - MZ) + 3 * noise(x, z, 8, 1.5)
        if d < 26:
            area.set(x, G, z, "mycelium" if hash01(x, 1, z, 6) < 0.92 else "podzol")
            myc.add((x, z))
R, H = 13, 9
for dx in range(-R - 3, R + 4):
    for dz in range(-R - 3, R + 4):
        for dy in range(0, H + 4):
            x, y, z = MX + dx, G + 1 + dy, MZ + dz
            outer = (dx * dx + dz * dz) / (R + 2) ** 2 + (dy * dy) / (H + 2) ** 2
            inner = (dx * dx + dz * dz) / (R * R) + (dy * dy) / (H * H)
            if inner <= 1:
                area.set(x, y, z, "air")
            elif outer <= 1:
                q = hash01(x, y, z, 12)
                area.set(x, y, z, "tuff" if q < 0.3 else "andesite" if q < 0.55 else "brown_terracotta" if q < 0.8 else
                         "terracotta" if q < 0.92 else "smooth_basalt")
# the cave floor and its entrance (south side), plus a doorway arch
for dx in range(-R, R + 1):
    for dz in range(-R, R + 1):
        if dx * dx + dz * dz <= R * R:
            area.set(MX + dx, G, MZ + dz, "mycelium" if hash01(MX + dx, 2, MZ + dz, 5) < 0.9 else "podzol")
            myc.add((MX + dx, MZ + dz))
for dz in range(R - 3, R + 4):
    for dx in range(-2, 3):
        for y in range(G + 1, G + 5):
            area.set(MX + dx, y, MZ + dz, "air" if abs(dx) < 2 or y < G + 4 else "brown_terracotta")
        area.set(MX + dx, G, MZ + dz, "mycelium")
for dx in (-2, 2):
    for y in range(G + 1, G + 5):
        area.set(MX + dx, y, MZ + R + 3, "stripped_jungle_log[axis=y]" if y < G + 5 else "air")
area.set(MX - 2, G + 4, MZ + R + 3, "mushroom_stem")
area.set(MX + 2, G + 4, MZ + R + 3, "mushroom_stem")
# a second, narrow opening on the east flank, for the Mooshrooms to wander out
for dx in range(R - 2, R + 4):
    for dz in range(-1, 2):
        for y in range(G + 1, G + 4):
            area.set(MX + dx, y, MZ + dz, "air")
        area.set(MX + dx, G, MZ + dz, "mycelium")
area.path([(MX, MZ + R + 4), (MX, MZ + 40)], width=3, blocks=area.mix({"coarse_dirt": 3, "podzol": 2, "gravel": 1}))
area.path([(MX, MZ + 38), (270, -26), (262, -16)], width=2, blocks=area.mix({"coarse_dirt": 3, "podzol": 2}))


def giant(x, z, height, kind, big=2):
    cap = "red_mushroom_block" if kind == "red" else "brown_mushroom_block"
    for y in range(G + 1, G + 1 + height):
        area.set(x, y, z, "mushroom_stem")
    top = G + 1 + height
    if kind == "red":
        for dx in range(-big, big + 1):
            for dz in range(-big, big + 1):
                if abs(dx) == big and abs(dz) == big:
                    continue
                area.set(x + dx, top, z + dz, cap)
        for dx in range(-big + 1, big):
            for dz in range(-big + 1, big):
                area.set(x + dx, top + 1, z + dz, cap)
        area.set(x, top + 2, z, cap)
    else:
        for dx in range(-big - 1, big + 2):
            for dz in range(-big - 1, big + 2):
                if abs(dx) == big + 1 and abs(dz) == big + 1:
                    continue
                area.set(x + dx, top, z + dz, cap)
        for dx in range(-big, big + 1):
            for dz in range(-big, big + 1):
                area.set(x + dx, top + 1, z + dz, cap)


# inside the cave
giant(MX - 5, MZ - 3, 4, "red")
giant(MX + 6, MZ + 2, 5, "red", big=2)
giant(MX + 1, MZ - 8, 3, "brown")
giant(MX - 7, MZ + 6, 3, "brown")
# glowing ceiling and floor lights
for dx, dz in ((0, 0), (-6, 4), (6, -5), (-4, -7), (5, 7), (0, 8), (-9, -1), (9, 1)):
    for dy in range(H, 0, -1):
        if (dx * dx + dz * dz) / (R * R) + (dy * dy) / (H * H) > 0.78 and area.get(MX + dx, G + 1 + dy, MZ + dz) != "minecraft:air":
            area.set(MX + dx, G + dy, MZ + dz, "shroomlight")
            break
# small mushrooms over the cave and the field
count = 0
for (x, z) in sorted(myc):
    y = area.top.get((x, z))
    if y is None or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    d_cave = math.hypot(x - MX, z - MZ)
    inside_cave = d_cave < R - 1
    if d_cave < R + 4 and d_cave > R - 1:
        continue
    r = hash01(x, 31, z, 8)
    if r < (0.22 if inside_cave else 0.1):
        area.set(x, G + 1, z, "red_mushroom" if r % 0.02 < 0.01 else "brown_mushroom")
        count += 1
# giants out on the field
for (gx, gz, h, kind) in ((MX + 20, MZ - 12, 6, "red"), (MX - 20, MZ - 8, 7, "brown"), (MX - 12, MZ + 21, 5, "red"),
                          (MX + 14, MZ + 18, 6, "brown"), (MX + 3, MZ - 22, 6, "red")):
    if (gx, gz) in area.top and math.hypot(gx - MX, gz - MZ) > R + 6:
        giant(gx, gz, h, kind, big=2)
area.fairy_soul(MX - 5, G + 1, MZ + 1)       # inside the cave, behind the big red mushroom's stem
area.set(MX - 5, G + 1, MZ, "mushroom_stem")
area.set(MX - 6, G + 1, MZ + 1, "brown_mushroom_block")
area.set(MX - 4, G + 1, MZ + 1, "red_mushroom_block")
area.set(MX - 5, G + 1, MZ + 2, "red_mushroom_block")
area.set(MX - 5, G + 2, MZ + 1, "brown_mushroom_block")
area.spots["notes"].append(f"Mushroom cave: centre ({MX}, {G + 1}, {MZ}), radius {R}, entrance on the south side")

# ---------------------------------------------------------------- the bridge from the Barn (same call as the Barn's helper)
area.bridge((86, 0), (142, 0), y=G)

# ---------------------------------------------------------------- dunes: outcrops, dead bushes, wild cactus, a soul spire
for (x, z) in sorted(cols):
    if in_reserved(x, z):
        continue
    d = math.hypot(x - CX, z - CZ)
    if d > 84 or hash01(x, 51, z, 2) > 0.0035:
        continue
    y = area.top[(x, z)]
    r = 2 + int(hash01(x, 52, z) * 3)
    hh = 3 + int(hash01(x, 53, z) * 4)
    block = "sandstone" if hash01(x, 54, z) < 0.5 else "red_sandstone"
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            dist = math.hypot(dx, dz)
            if dist > r or (x + dx, z + dz) not in area.top or in_reserved(x + dx, z + dz):
                continue
            base = area.top[(x + dx, z + dz)]
            top = int(round(hh * (1 - dist / (r + 0.5)))) + (1 if hash01(x + dx, 55, z + dz) < 0.3 else 0)
            for yy in range(base + 1, base + 1 + top):
                area.set(x + dx, yy, z + dz, block if hash01(x + dx, yy, z + dz, 3) < 0.7 else "cut_" + block)
            if top > 0:
                area.set(x + dx, base, z + dz, block)
SPX, SPZ = 304, 8
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) + abs(dz) <= 3 and (SPX + dx, SPZ + dz) in area.top:
            hgt = 9 - 2 * max(abs(dx), abs(dz))
            for yy in range(G + 1, G + 1 + hgt):
                area.set(SPX + dx, yy, SPZ + dz, "red_sandstone" if yy % 3 else "cut_red_sandstone")
for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
    area.set(SPX + dx, G + 9, SPZ + dz, "red_sandstone")
area.fairy_soul(SPX, G + 10, SPZ)
# reach the spire's top by a flight of steps from the west
for i in range(8):
    area.fill(SPX - 8 + i, G + 1, SPZ, SPX - 8 + i, G + 1 + i, SPZ, "cut_red_sandstone")
    area.fill(SPX - 8 + i, G + 1, SPZ + 1, SPX - 8 + i, G + 1 + i, SPZ + 1, "cut_red_sandstone")

dead = 0
cact_wild = 0
for (x, z) in sorted(cols):
    if in_reserved(x, z):
        continue
    y = area.top[(x, z)]
    if area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    surf = area.get(x, y, z) or ""
    r = hash01(x, 61, z, 5)
    if surf.endswith(("red_sand", "sand")) and r < 0.05:
        area.set(x, y + 1, z, "dead_bush")
        dead += 1
    elif surf == "minecraft:sand" and r < 0.058 and all(area.get(x + dx, y + 1, z + dz) in (None, "minecraft:air") for dx, dz in
                                                           ((1, 0), (-1, 0), (0, 1), (0, -1))):
        for h in range(2 + int(hash01(x, 62, z) * 1.99)):
            area.set(x, y + 1 + h, z, "cactus[age=0]")
        cact_wild += 1

# ---------------------------------------------------------------- warp, arrival, souls
area.arrival(150, G + 1, 0, -90.0)
area.warp("Mushroom Desert", 150, G + 1, 0, -90.0)

# ---------------------------------------------------------------- the zone
RAB = ((166, G - 1, -46), (190, G + 10, -28))
SHEEP = ((SPX1 + 1, G - 1, SPZ1 + 1), (SPX2 - 1, G + 6, SPZ2 - 1))
MOO = ((MX - 26, G - 1, MZ - 26), (MX + 26, G + 14, MZ + 26))
area.zone("Mushroom Desert", blocks={
    "minecraft:sand": {"becomes": "minecraft:bedrock", "regrow": 6},
    "minecraft:cactus": {"becomes": "minecraft:air", "regrow": 10},
    "minecraft:sugar_cane": {"becomes": "minecraft:air", "regrow": 10},
    "minecraft:cocoa": {"becomes": "minecraft:cocoa", "regrow": 10},
    "minecraft:red_mushroom": {"becomes": "minecraft:air", "regrow": 15},
    "minecraft:brown_mushroom": {"becomes": "minecraft:air", "regrow": 15}}, mobs=[
    {"type": "minecraft:rabbit", "name": "Rabbit", "level": 1, "health": 50, "max": 6, "every": 10,
     "from": list(RAB[0]), "to": list(RAB[1])},
    {"type": "minecraft:sheep", "name": "Sheep", "level": 1, "health": 100, "max": 6, "every": 10,
     "from": list(SHEEP[0]), "to": list(SHEEP[1])},
    {"type": "minecraft:mooshroom", "name": "Mooshroom", "level": 1, "health": 100, "max": 4, "every": 12,
     "from": list(MOO[0]), "to": list(MOO[1])}])
area.save()

# ---------------------------------------------------------------- checks
print("west edge columns (138..146, 0):", [(x, 0) in cols for x in range(138, 147)], "bridge end surface", area.surface_y(143, 0))
print("arrival ground:", area.get(150, G, 0), "| cactus", cactus, "wild", cact_wild, "| cane", canes, "| palms", palms,
      "cocoa", beans, "| mushrooms", count, "| dead bushes", dead)
print("rabbit box on land:", all((x, z) in cols for x in (RAB[0][0], RAB[1][0]) for z in (RAB[0][2], RAB[1][2])))
