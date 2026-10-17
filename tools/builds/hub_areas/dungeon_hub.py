#!/usr/bin/env python3
"""The Dungeon Hub (Hypixel's gloomy graveyard town, its own void dimension): an arrival plaza on the south side, a central
square with Ophelia's stall, Malik's hut, Croesus' vault and Adventurer Saul's campfire, and at the north the Catacombs
crypt (wide steps, a dark archway, a dead-end tunnel with a soul-fire glow) with Mort in front. A graveyard in the south-west,
dead trees, lanterns on posts, and a race-course path loop around the island edge.

    python tools/builds/hub_areas/dungeon_hub.py
"""
import math
from kit import Area, hash01

G = 64                       # surface; players stand at 65
area = Area("dungeon_hub", (-90, 10, -90), (180, 100, 180), version=1,
            dimension="skyrpg_skyblock:dungeon_hub", island="Dungeon Hub")

ground = area.mix({"podzol": 4, "coarse_dirt": 3, "moss_block": 1, "grass_block": 2})
body = area.mix({"deepslate": 4, "cobbled_deepslate": 3, "tuff": 2, "blackstone": 1})
cols = area.island(0, 0, 78, top=G, seed=7, surface=ground, hills=0.0, depth=44, wobble=0.06, dirt="coarse_dirt",
                   stone=body, edge_drop=0)

pave_mix = area.mix({"cobbled_deepslate": 3, "deepslate_bricks": 3, "polished_deepslate": 2, "cracked_deepslate_bricks": 1,
                     "stone_bricks": 1})
road_mix = area.mix({"deepslate_bricks": 4, "cobbled_deepslate": 2, "cracked_deepslate_bricks": 1, "polished_deepslate": 1})
yard_mix = area.mix({"gravel": 3, "cobblestone": 3, "cobbled_deepslate": 2, "mossy_cobblestone": 1})
ring_mix = area.mix({"gravel": 3, "cobblestone": 2, "cobbled_deepslate": 2, "stone": 1})
conn_mix = area.mix({"cobblestone": 3, "cobbled_deepslate": 3, "gravel": 2, "mossy_cobblestone": 1})
crypt_mix = area.mix({"deepslate_bricks": 6, "cracked_deepslate_bricks": 2, "deepslate_tiles": 1})

reserved = []        # (x1, z1, x2, z2) rectangles nothing random goes on
blocked = set()      # (x, z) path cells


def rsv(x1, z1, x2, z2, m=0):
    reserved.append((min(x1, x2) - m, min(z1, z2) - m, max(x1, x2) + m, max(z1, z2) + m))


def is_reserved(x, z):
    if (x, z) in blocked:
        return True
    return any(a <= x <= c and b <= z <= d for a, b, c, d in reserved)


def pave(x1, z1, x2, z2, pick, seed=0):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            if (x, z) in cols:
                area.set(x, G, z, pick(x, G, z, seed))
    rsv(x1, z1, x2, z2)


def box(x1, y1, z1, x2, y2, z2, b):
    area.fill(x1, y1, z1, x2, y2, z2, b)


def S(x, y, z, b):
    area.set(x, y, z, b)


def post(x, z, h=3, soul=False, y0=G + 1):
    """A lantern post: wall posts with a lantern on top."""
    box(x, y0, z, x, y0 + h - 1, z, "deepslate_tile_wall")
    S(x, y0 + h, z, "soul_lantern[hanging=false]" if soul else "lantern[hanging=false]")


def sign(x, y, z, rot, lines):
    msgs = ['""'] + ['"%s"' % t.replace("'", "\\'") for t in lines] + ['""'] * 3
    area.set(x, y, z, f"dark_oak_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": msgs[:4], "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def roof(x1, z1, x2, z2, y, stair, block, layers=3):
    """A low pyramid roof over the rectangle (stairs on the rim of each layer, solid inside)."""
    for k in range(layers):
        if x1 > x2 or z1 > z2:
            break
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if x in (x1, x2) or z in (z1, z2):
                    f = "south" if z == z1 else "north" if z == z2 else "east" if x == x1 else "west"
                    S(x, y + k, z, f"{stair}[facing={f},half=bottom]")
                else:
                    S(x, y + k, z, block)
        x1, z1, x2, z2 = x1 + 1, z1 + 1, x2 - 1, z2 - 1


def bench(x, z, along, facing):
    """Three stairs in a row (along 'x' or 'z'), backs towards `facing`."""
    for i in range(-1, 2):
        bx, bz = (x + i, z) if along == "x" else (x, z + i)
        S(bx, G + 1, bz, f"dark_oak_stairs[facing={facing},half=bottom]")
    rsv(x - 1, z - 1, x + 1, z + 1) if along == "x" else rsv(x - 1, z - 1, x + 1, z + 1)


# ================================================================ paving, roads, the ring
SQ = (-26, -22, 26, 30)               # the central square
PL = (-16, 46, 16, 61)                # the arrival plaza
ring_pts = [(round(63 * math.cos(a * math.tau / 72)), round(63 * math.sin(a * math.tau / 72))) for a in range(73)]
blocked |= area.path(ring_pts, width=3, blocks=ring_mix, seed=2)
conns = [[(-20, 28), (-32, 36), (-44, 44)], [(20, 28), (32, 36), (44, 44)],
         [(-20, -20), (-32, -32), (-44, -44)], [(20, -20), (32, -32), (44, -44)]]
for pts in conns:
    blocked |= area.path(pts, width=3, blocks=conn_mix, seed=3)

pave(*PL, pave_mix, 1)
pave(*SQ, pave_mix, 2)
pave(-3, 30, 3, 47, road_mix, 4)                      # plaza -> square
pave(-3, -40, 3, -22, road_mix, 5)                    # square -> gate
pave(-11, -40, 11, -23, yard_mix, 6)                  # the approach to the Catacombs
pave(-3, -40, 3, -23, road_mix, 7)
pave(-4, 59, 4, 66, road_mix, 8)                      # plaza to the ring
# plaza: border and a ring round the arrival
for x in range(PL[0], PL[2] + 1):
    for z in range(PL[1], PL[3] + 1):
        edge = x in (PL[0], PL[2]) or z in (PL[1], PL[3])
        d = math.hypot(x, z - 54)
        if edge:
            S(x, G, z, "polished_blackstone_bricks")
        elif 2.5 <= d <= 3.6:
            S(x, G, z, "polished_blackstone")
        elif d < 2.5:
            S(x, G, z, "chiseled_polished_blackstone" if d < 1.2 else "polished_deepslate")
# square: border, an inlaid ring round the obelisk, a darker cross
OBX, OBZ = 0, 4
for x in range(SQ[0], SQ[2] + 1):
    for z in range(SQ[1], SQ[3] + 1):
        d = math.hypot(x - OBX, (z - OBZ) * 0.9)
        if x in (SQ[0], SQ[2]) or z in (SQ[1], SQ[3]):
            S(x, G, z, "polished_deepslate")
        elif 8 <= d <= 9:
            S(x, G, z, "polished_blackstone_bricks")
        elif 12.5 <= d <= 13.2:
            S(x, G, z, "chiseled_deepslate" if (x + z) % 4 == 0 else "polished_blackstone")
        elif d < 4.5:
            S(x, G, z, "polished_blackstone_bricks")

# the obelisk
box(OBX - 2, G + 1, OBZ - 2, OBX + 2, G + 1, OBZ + 2, "polished_blackstone_bricks")
box(OBX - 1, G + 2, OBZ - 1, OBX + 1, G + 2, OBZ + 1, "chiseled_polished_blackstone")
box(OBX, G + 3, OBZ, OBX, G + 8, OBZ, "polished_deepslate")
S(OBX, G + 5, OBZ, "chiseled_deepslate")
S(OBX, G + 9, OBZ, "soul_lantern[hanging=false]")
for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    S(OBX + dx, G + 2, OBZ + dz, "wither_skeleton_skull[rotation=%d]" % {(-2, -2): 10, (2, -2): 6, (-2, 2): 14, (2, 2): 2}[(dx, dz)])
    S(OBX + dx // 2 * 1, G + 2, OBZ + dz, "soul_lantern[hanging=false]") if dx == -2 and dz == 2 else None
for dx, dz in ((-6, 0), (6, 0)):
    pass
rsv(OBX - 2, OBZ - 2, OBX + 2, OBZ + 2)

# ================================================================ lanterns and fences
# plaza
for x, z, soul in ((-15, 47, False), (15, 47, False), (-15, 60, True), (15, 60, True), (-5, 60, False), (5, 60, False),
                   (-15, 53, True), (15, 53, True), (-5, 47, True), (5, 47, True)):
    post(x, z, 3, soul)
for z in range(47, 61):
    if not 52 <= z <= 57:
        for x in (-16, 16):
            if (x, z) not in ((-16, 47), (16, 47)):
                S(x, G + 1, z, "iron_bars")
for x in range(-16, 17):
    if abs(x) > 4:
        S(x, G + 1, 61, "iron_bars")
for x, z, along, f in ((-12, 58, "x", "south"), (12, 58, "x", "south"), (-14, 50, "z", "west"), (14, 50, "z", "east")):
    bench(x, z, along, f)
    S(x, G + 1, z, f"dark_oak_stairs[facing={f},half=bottom]")
# road posts every 8 blocks, alternating
for i, z in enumerate(range(34, 47, 6)):
    post(-4, z, 3, i % 2 == 0)
    post(4, z, 3, i % 2 == 1)
for i, z in enumerate(range(-24, -39, -7)):
    post(-4, z, 3, i % 2 == 0)
    post(4, z, 3, i % 2 == 1)
# square
for x, z, soul in ((-25, -21, False), (25, -21, True), (-25, 29, True), (25, 29, False), (-25, 3, False), (25, 3, False),
                   (-13, -21, True), (13, -21, True), (-13, 29, False), (13, 29, False), (-13, 4, True), (13, 4, True),
                   (-8, -10, False), (8, -10, False), (-8, 19, False), (8, 19, False)):
    post(x, z, 3, soul)
# the ring and connectors: posts just outside the path
for i in range(0, 72, 3):
    a = i * math.tau / 72
    x, z = round(66 * math.cos(a)), round(66 * math.sin(a))
    if (x, z) in cols and not is_reserved(x, z) and area.get(x, G + 1, z) in (None, "minecraft:air"):
        post(x, z, 2, (i // 3) % 2 == 0)
for pts in conns:
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
        ox, oz = (3, -3) if (x2 - x1) * (z2 - z1) > 0 else (3, 3)
        post(mx + ox, mz + oz, 3, False)
        post(mx - ox, mz - oz, 3, True)

# ================================================================ the Catacombs
CX1, CX2, CZ1, CZ2 = -17, 17, -56, -47      # crypt mass; facade on z = -47
# wide steps (x -7..7), four rows up to the platform
for i in range(4):
    z = -40 - i
    for x in range(-7, 8):
        box(x, G + 1, z, x, G + i, z, "deepslate_bricks") if i else None
        S(x, G + 1 + i, z, "deepslate_brick_stairs[facing=north,half=bottom]")
    for x in (-8, 8):                              # curbs
        box(x, G + 1, z, x, G + 2 + i, z, "deepslate_bricks")
        S(x, G + 3 + i, z, "deepslate_brick_slab[type=bottom]")
# platform y68 (standing 69)
for x in range(-12, 13):
    for z in range(-46, -43):
        box(x, G, z, x, G + 4, z, "polished_deepslate")
        S(x, G + 4, z, "polished_blackstone_bricks" if (x + z) % 3 else "chiseled_polished_blackstone")
        S(x, G + 5, z, "air")
        S(x, G + 6, z, "air")
rsv(-17, -56, 17, -40)
# the mass
for x in range(CX1, CX2 + 1):
    for z in range(CZ2 + 0, CZ1 + 0, -1) if False else range(CZ1, CZ2 + 1):
        for y in range(G + 1, G + 16):
            S(x, y, z, crypt_mix(x, y, z, 11))
for x in range(-10, 11):                           # second tier
    for z in range(-54, -46):
        for y in range(G + 16, G + 21):
            S(x, y, z, crypt_mix(x, y, z, 12))
for x in range(-5, 6):                             # third tier
    for z in range(-52, -46):
        for y in range(G + 21, G + 26):
            S(x, y, z, crypt_mix(x, y, z, 13))
for sx in (-1, 1):                                 # corner towers
    for x in range(13, 18):
        for z in range(-52, -46):
            for y in range(G + 16, G + 29):
                S(sx * x, y, z, crypt_mix(sx * x, y, z, 14))
    for x in range(14, 17):
        S(sx * x, G + 29, -47, "deepslate_brick_slab[type=bottom]")
    for z in range(-52, -46):
        for x in (13, 17):
            S(sx * x, G + 29, z, "polished_blackstone_brick_wall")
        S(sx * 14, G + 29, z, "polished_blackstone_brick_wall") if z in (-52,) else None
    for x in range(13, 18):
        S(sx * x, G + 29, -52, "polished_blackstone_brick_wall")
        S(sx * x, G + 29, -47, "polished_blackstone_brick_wall")
    S(sx * 15, G + 29, -49, "soul_lantern[hanging=false]")
    box(sx * 15, G + 30, -49, sx * 15, G + 31, -49, "polished_blackstone_brick_wall")
    S(sx * 15, G + 32, -49, "soul_lantern[hanging=false]")
# roofs: slab caps along the tiers
for x in range(CX1, CX2 + 1):
    if abs(x) <= 12 or abs(x) <= 17 and False:
        pass
for x in range(-13, 13):
    S(x, G + 16, -47, "polished_blackstone_brick_slab[type=top]") if abs(x) > 10 else None
for x in range(-11, 12):
    S(x, G + 21, -47, "polished_blackstone_brick_slab[type=top]") if abs(x) > 5 else None
for x in range(-6, 7):
    S(x, G + 26, -47, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
    S(x, G + 26, -48, "polished_blackstone_bricks") if False else None
for x in range(-5, 6):
    for z in range(-52, -46):
        S(x, G + 26, z, "deepslate_tile_slab[type=bottom]") if x in (-5, 5) or z in (-52, -47) else S(x, G + 26, z, "deepslate_tile_slab[type=bottom]")
# the cross-gable crest and a spire
for y in range(G + 27, G + 32):
    S(0, y, -49, "polished_blackstone_brick_wall")
S(0, G + 30, -49, "chiseled_polished_blackstone")
for dx in (-1, 1):
    S(dx, G + 29, -49, "polished_blackstone_brick_wall")
S(0, G + 32, -49, "wither_skeleton_skull[rotation=0]")
# facade bands and base
for x in range(CX1, CX2 + 1):
    S(x, G + 1, -47, "polished_blackstone_bricks")
    S(x, G + 2, -47, "polished_blackstone_bricks")
    S(x, G + 3, -47, "polished_blackstone")
    S(x, G + 6, -47, "chiseled_stone_bricks")
    S(x, G + 15, -47, "polished_blackstone_bricks")
for x in range(-10, 11):
    S(x, G + 16, -47, "chiseled_stone_bricks")
    S(x, G + 20, -47, "polished_blackstone_bricks")
# pillars on the platform, with wither skulls on their tops
for x in (-12, -8, -4, 4, 8, 12):
    for y in range(G + 5, G + 15):
        S(x, y, -46, "polished_blackstone" if y not in (G + 9, G + 10) else "chiseled_polished_blackstone")
    S(x, G + 15, -46, "polished_blackstone_brick_slab[type=top]")
    S(x, G + 16, -46, "wither_skeleton_skull[rotation=0]")
    S(x, G + 5, -46, "polished_blackstone_bricks")
# barred windows (a recess with a soul lantern behind)
for x in (-14, -10, -6, 6, 10, 14):
    for y in range(G + 8, G + 12):
        S(x, y, -47, "iron_bars")
        S(x, y, -48, "air")
    S(x, G + 8, -48, "soul_lantern[hanging=false]")
for sx in (-1, 1):
    for y in range(G + 19, G + 23):
        S(sx * 15, y, -47, "iron_bars")
        S(sx * 15, y, -48, "air")
    S(sx * 15, G + 19, -48, "soul_lantern[hanging=false]")
for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):   # rose window above the arch
    if abs(dx) + abs(dz) <= 2 and not (abs(dx) == 2 and False):
        S(dx, G + 19 + dz, -47, "iron_bars")
        S(dx, G + 19 + dz, -48, "air")
S(0, G + 19, -48, "soul_lantern[hanging=false]")
for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
    S(dx, G + 19 + dz, -47, "chiseled_stone_bricks")
# the archway: x -2..2 through the facade, y69..73; frame, lintel, wither skulls over it
for x in range(-2, 3):
    for y in range(G + 5, G + 10):
        for z in (-47, -48):
            S(x, y, z, "air")
for y in range(G + 5, G + 10):
    for x in (-3, 3):
        S(x, y, -47, "polished_blackstone_bricks")
        S(x, y, -48, "polished_blackstone_bricks")
for x in range(-3, 4):
    S(x, G + 10, -47, "chiseled_stone_bricks")
S(-2, G + 9, -47, "polished_blackstone_brick_stairs[facing=east,half=top]")
S(2, G + 9, -47, "polished_blackstone_brick_stairs[facing=west,half=top]")
S(-2, G + 9, -48, "polished_blackstone_brick_stairs[facing=east,half=top]")
S(2, G + 9, -48, "polished_blackstone_brick_stairs[facing=west,half=top]")
for x in (-2, 0, 2):
    S(x, G + 12, -46, "wither_skeleton_wall_skull[facing=south]")
for sx in (-1, 1):
    S(sx * 3, G + 5, -46, "soul_lantern[hanging=false]")
    S(sx * 2, G + 11, -47, "polished_blackstone_bricks")
# the tunnel: stairs down three steps, then flat to an iron-barred soul-fire niche
for x in range(-1, 2):
    for z in range(-55, -48):
        for y in range(G + 5, G + 9):
            S(x, y, z, "air")
    for i in range(3):
        z = -48 - i
        S(x, G + 4 - i, z, f"deepslate_brick_stairs[facing=south,half=bottom]")
        S(x, G + 3 - i, z, "deepslate_bricks")
        for y in range(G + 5 - i, G + 9):
            S(x, y, z, "air")
    for z in range(-51, -54, -1):
        S(x, G + 1, z, "polished_deepslate")
        for y in range(G + 2, G + 9):
            S(x, y, z, "air")
    S(x, G + 9, -51, "deepslate_bricks") if False else None
    # the niche behind the bars
    S(x, G + 1, -55, "soul_soil")
    S(x, G + 2, -55, "soul_fire")
    for y in range(G + 3, G + 9):
        S(x, y, -55, "air")
    for y in range(G + 2, G + 6):
        S(x, y, -54, "iron_bars")
for y in range(G + 1, G + 10):
    for x in (-2, 2):
        for z in range(-55, -48):
            S(x, y, z, crypt_mix(x, y, z, 15)) if y <= G + 4 or True else None
for x in range(-1, 2):
    for z in range(-55, -48):
        S(x, G + 9, z, crypt_mix(x, G + 9, z, 16))
for z in (-50, -53):
    for sx in (-1, 1):
        S(sx, G + 8, z, "soul_lantern[hanging=true]")
S(0, G + 8, -55, "soul_lantern[hanging=true]")
for sx in (-1, 1):
    S(sx, G + 3, -55, "soul_fire") if False else None
# make the tunnel's side walls and back solid chiseled
for y in range(G + 1, G + 9):
    for x in range(-2, 3):
        S(x, y, -56, "chiseled_deepslate" if y % 3 == 0 else "polished_deepslate")
# yard: braziers either side of the approach
for sx in (-1, 1):
    S(sx * 6, G + 1, -30, "soul_campfire[lit=true]")
    S(sx * 6, G + 1, -37, "soul_campfire[lit=true]")
    for z in (-30, -37):
        box(sx * 9, G + 1, z, sx * 9, G + 3, z, "polished_blackstone_brick_wall")
        S(sx * 9, G + 4, z, "soul_lantern[hanging=false]")
# iron fence either side of the approach
for sx in (-1, 1):
    for z in range(-39, -23):
        S(sx * 12, G + 1, z, "iron_bars") if z % 7 else S(sx * 12, G + 1, z, "polished_blackstone_brick_wall")
# Mort in front of the archway, facing the plaza
MORT = (0, G + 5, -45)
area.npc("Mort", *MORT, yaw=0.0, note="on the platform in front of the Catacombs archway, facing south to the plaza")
rsv(-17, -56, 17, -23, 2)

# ================================================================ buildings around the square
def mk(sgn):
    return lambda u: sgn * (27 + u)


def facing_in(sgn):
    return "east" if sgn < 0 else "west"


def facing_out(sgn):
    return "west" if sgn < 0 else "east"


# ---------------------------------------------------------------- Ophelia's equipment stall (west)
sgn = -1
X = mk(sgn)
IN, OUT = facing_in(sgn), facing_out(sgn)
for u in range(0, 9):
    for z in range(7, 22):
        S(X(u), G, z, "dark_oak_planks")
for u in range(0, 9):
    for z in (7, 21):
        if u in (0, 8):
            box(X(u), G + 1, z, X(u), G + 4, z, "stripped_dark_oak_log[axis=y]")
        else:
            box(X(u), G + 1, z, X(u), G + 4, z, "spruce_planks")
            if u in (3, 5):
                S(X(u), G + 3, z, "iron_bars")
for z in range(8, 21):
    box(X(8), G + 1, z, X(8), G + 4, z, "deepslate_bricks")
for u in (1, 7):
    pass
S(X(0), G + 4, 14, "dark_oak_planks")
for z in range(9, 20):
    S(X(2), G + 1, z, "dark_oak_planks")
    S(X(2), G + 2, z, "dark_oak_slab[type=bottom]") if z % 2 else None
for z, b in ((9, "anvil[facing=north]"), (19, "anvil[facing=north]"), (11, "grindstone[face=floor,facing=north]"),
             (17, "smithing_table"), (13, "barrel[facing=up]"), (15, "barrel[facing=up]")):
    S(X(7), G + 1, z, b)
S(X(7), G + 1, 10, f"blast_furnace[facing={IN},lit=false]")
S(X(7), G + 1, 18, f"blast_furnace[facing={IN},lit=false]")
for z in (10, 12, 16, 18):                          # weapon "racks": bars with chains and skulls on the back wall
    for y in (G + 2, G + 3):
        S(X(8), y, z, "iron_bars")
    S(X(7), G + 3, z, "chain[axis=y]")
for z in (9, 14, 19):
    S(X(7), G + 4, z, "lantern[hanging=true]")
    S(X(7), G + 5, z, "air")
S(X(4), G + 4, 11, "lantern[hanging=true]")
S(X(4), G + 4, 17, "lantern[hanging=true]")
S(X(5), G + 1, 8, "iron_block")
S(X(5), G + 1, 20, "iron_block")
S(X(6), G + 1, 20, "anvil[facing=east]")
S(X(4), G + 1, 8, "chest[facing=south]")
S(X(4), G + 1, 20, "chest[facing=north]")
sign(X(2), G + 3, 11, 12, ["Ophelia", "Equipment"]) if False else sign(X(2), G + 3, 11, 12 if sgn < 0 else 4, ["Ophelia", "Equipment"])
roof(X(8) if sgn > 0 else X(8) - 1, 6, X(-1) if sgn < 0 else X(-1), 22, G + 5, "dark_oak_stairs", "dark_oak_planks", 3) if False else None
x1, x2 = sorted((X(-1), X(9)))
roof(x1, 6, x2, 22, G + 5, "dark_oak_stairs", "dark_oak_planks", 3)
rsv(x1, 6, x2, 22, 1)
area.npc("Ophelia", X(5), G + 1, 14, yaw=-90.0 if sgn < 0 else 90.0, note="inside her equipment stall behind the counter, facing the square")

# ---------------------------------------------------------------- Adventurer Saul's campfire (west)
sgn = -1
X = mk(sgn)
IN, OUT = facing_in(sgn), facing_out(sgn)
for u in range(0, 10):
    for z in range(-14, -3):
        if hash01(X(u), 3, z, 21) < 0.8:
            S(X(u), G, z, "coarse_dirt" if hash01(X(u), 4, z, 22) < 0.6 else "podzol")
S(X(4), G + 1, -9, "campfire[lit=true,facing=north]")
for z, f in ((-12, "north"), (-6, "south")):
    S(X(4), G + 1, z, f"spruce_stairs[facing={f},half=bottom]")
    S(X(4), G + 1, z + (1 if f == "north" else -1), "spruce_log[axis=z]") if False else None
S(X(6), G + 1, -9, f"spruce_stairs[facing={OUT},half=bottom]")
S(X(3), G + 1, -11, "spruce_log[axis=x]")
S(X(3), G + 1, -7, "spruce_log[axis=x]")
for z in (-12, -6):
    box(X(2), G + 1, z, X(2), G + 2, z, "spruce_fence")
    S(X(2), G + 3, z, "lantern[hanging=false]")
S(X(8), G + 1, -12, "barrel[facing=up]")
S(X(8), G + 1, -11, "chest[facing=%s]" % IN)
S(X(8), G + 1, -6, "hay_block[axis=x]")
S(X(8), G + 1, -7, "red_carpet")
S(X(7), G + 1, -7, "red_carpet")
S(X(9), G + 1, -9, "dead_bush") if False else None
area.npc("Adventurer Saul", X(1), G + 1, -9, yaw=-90.0, note="at his campfire west of the square, between the logs and the paving")
rsv(X(9), -15, X(0), -2, 1)

# ---------------------------------------------------------------- Malik's essence alchemist hut (east)
sgn = 1
X = mk(sgn)
IN, OUT = facing_in(sgn), facing_out(sgn)
for u in range(0, 9):
    for z in range(7, 22):
        S(X(u), G, z, "purple_terracotta" if hash01(X(u), 5, z, 23) < 0.5 else "polished_deepslate")
for u in range(0, 9):
    for z in range(7, 22):
        edge = u in (0, 8) or z in (7, 21)
        if not edge:
            continue
        corner = u in (0, 8) and z in (7, 21)
        for y in range(G + 1, G + 5):
            if corner:
                S(X(u), y, z, "amethyst_block")
            elif y in (G + 2, G + 3) and ((z in (7, 21) and u in (2, 4, 6)) or (u == 8 and z in (10, 14, 18)) or
                                           (u == 0 and z in (10, 18))):
                S(X(u), y, z, "purple_stained_glass_pane")
            else:
                S(X(u), y, z, "polished_blackstone_bricks" if y < G + 4 else "chiseled_deepslate")
for y in (G + 1, G + 2):                           # the doorway
    S(X(0), y, 14, "air")
S(X(0), G + 3, 14, "polished_blackstone_brick_stairs[facing=%s,half=top]" % IN)
for z, b in ((9, "brewing_stand"), (11, "brewing_stand"), (17, "brewing_stand"), (19, "brewing_stand")):
    S(X(7), G + 1, z, "barrel[facing=up]")
    S(X(7), G + 2, z, b)
S(X(7), G + 1, 14, "cauldron")
S(X(7), G + 1, 13, "amethyst_block")
S(X(7), G + 2, 13, "amethyst_cluster[facing=up]")
S(X(7), G + 1, 15, "amethyst_block")
S(X(7), G + 2, 15, "amethyst_cluster[facing=up]")
for z in (9, 19):
    S(X(4), G + 1, z, "barrel[facing=up]")
    S(X(4), G + 2, z, "purple_candle[candles=3,lit=true]")
S(X(6), G + 1, 8, "budding_amethyst")
S(X(6), G + 2, 8, "amethyst_cluster[facing=up]")
S(X(6), G + 1, 20, "budding_amethyst")
S(X(6), G + 2, 20, "amethyst_cluster[facing=up]")
S(X(1), G + 1, 8, "chest[facing=south]")
S(X(1), G + 1, 20, "chest[facing=north]")
for z in (11, 17):
    S(X(4), G + 4, z, "soul_lantern[hanging=true]")
S(X(4), G + 4, 14, "soul_lantern[hanging=true]")
S(X(1), G + 4, 14, "soul_lantern[hanging=true]")
x1, x2 = sorted((X(-1), X(9)))
roof(x1, 6, x2, 22, G + 5, "deepslate_tile_stairs", "deepslate_tiles", 4)
for z in (7, 21):                                   # a purple glow stripe on the ridge
    S(X(4), G + 9, z - (1 if z == 7 else -1), "air") if False else None
sign(X(-1), G + 1, 10, 12 if sgn < 0 else 4, ["Malik", "Essence Alchemist"])
rsv(x1, 6, x2, 22, 1)
area.npc("Malik", X(2), G + 1, 14, yaw=90.0, note="inside the doorway of his alchemist hut, facing the square")

# ---------------------------------------------------------------- Croesus' treasure vault (east)
for u in range(0, 3):
    for z in range(-13, -4):
        S(X(u), G, z, "polished_blackstone_bricks")
for u in range(3, 10):
    for z in range(-16, -1):
        S(X(u), G, z, "polished_blackstone" if (u + z) % 2 else "polished_deepslate")
        edge = u in (3, 9) or z in (-16, -2)
        for y in range(G + 1, G + 6):
            if edge:
                corner = u in (3, 9) and z in (-16, -2)
                pillar = (z % 4 == 0 and u in (3,) and False)
                if corner or (u == 3 and z in (-13, -9, -5)):
                    S(X(u), y, z, "gilded_blackstone")
                elif u == 3 and y <= G + 4:
                    S(X(u), y, z, "iron_bars")
                elif y == G + 5:
                    S(X(u), y, z, "gilded_blackstone" if (z % 2 == 0) else "polished_blackstone_bricks")
                else:
                    S(X(u), y, z, "polished_blackstone_bricks")
        S(X(u), G + 6, z, "deepslate_tile_slab[type=bottom]")
for u in range(4, 9):
    for z in range(-15, -2):
        S(X(u), G + 6, z, "deepslate_tiles")
for z in range(-15, -2, 2):                         # gold stacked behind the bars
    for u in (4, 5):
        box(X(u), G + 1, z, X(u), G + 1 + (z % 3 == 0) + 1, z, "gold_block")
for z in (-14, -12, -10, -8, -6, -4):
    S(X(8), G + 1, z, "chest[facing=%s]" % IN)
for z in (-15, -3):
    box(X(7), G + 1, z, X(7), G + 3, z, "gold_block")
S(X(6), G + 1, -9, "gold_block")
S(X(6), G + 2, -9, "chest[facing=%s]" % IN)
for z in (-12, -9, -6):
    S(X(6), G + 5, z, "lantern[hanging=true]")
    S(X(4), G + 5, z, "lantern[hanging=true]")
box(X(0), G + 1, -13, X(0), G + 4, -13, "gilded_blackstone")
box(X(0), G + 1, -5, X(0), G + 4, -5, "gilded_blackstone")
for u in range(0, 3):
    for z in range(-13, -4):
        S(X(u), G + 5, z, "deepslate_tile_slab[type=bottom]")
S(X(0), G + 5, -9, "lantern[hanging=true]") if False else None
S(X(1), G + 4, -12, "lantern[hanging=true]")
S(X(1), G + 4, -6, "lantern[hanging=true]")
for z in range(-13, -4):
    S(X(0), G + 5, z, "gilded_blackstone" if z % 2 else "polished_blackstone_bricks")
sign(X(-1), G + 1, -12, 12 if sgn < 0 else 4, ["Croesus", "Treasure Vault"])
x1, x2 = sorted((X(-1), X(9)))
rsv(x1, -17, x2, -1, 1)
area.npc("Croesus", X(1), G + 1, -9, yaw=90.0, note="on the porch in front of the barred vault, facing the square")

# ================================================================ the graveyard (south-west) and a mausoleum
GX1, GZ1, GX2, GZ2 = -58, 22, -34, 54


def gy(x, z):
    return math.hypot(x, z) < 59 and (x, z) in cols


def tombstone(x, z, kind):
    y = G + 1
    if kind == 0:
        S(x, y, z, "stone_brick_wall")
        S(x, y + 1, z, "stone_brick_slab[type=bottom]")
    elif kind == 1:
        S(x, y, z, "mossy_cobblestone_wall")
        S(x, y + 1, z, "mossy_cobblestone_slab[type=bottom]")
    elif kind == 2:
        S(x, y, z, "cobbled_deepslate_wall")
        S(x, y + 1, z, "cobbled_deepslate_wall")
    else:
        S(x, y, z, "stone_brick_slab[type=bottom]")
    if hash01(x, 2, z, 31) < 0.7:
        S(x, G, z + 1, "coarse_dirt")


# a mausoleum: stone bricks and mossy bricks, a doorway to the south, a sarcophagus inside
MX1, MZ1, MX2, MZ2 = -46, 28, -40, 34
box(MX1, G + 1, MZ1, MX2, G + 4, MZ2, "stone_bricks")
box(MX1 + 1, G + 1, MZ1 + 1, MX2 - 1, G + 3, MZ2 - 1, "air")
for x in range(MX1, MX2 + 1):
    for z in range(MZ1, MZ2 + 1):
        S(x, G, z, "polished_andesite" if hash01(x, 1, z, 4) < 0.5 else "stone_bricks")
        if hash01(x, 9, z, 5) < 0.3:
            for y in range(G + 1, G + 5):
                if x in (MX1, MX2) or z in (MZ1, MZ2):
                    S(x, y, z, "mossy_stone_bricks" if hash01(x, y, z, 6) < 0.5 else "cracked_stone_bricks")
for y in (G + 1, G + 2):
    S(-43, y, MZ2, "air")
S(-43, G + 3, MZ2, "stone_brick_stairs[facing=north,half=top]") if False else None
for x in range(MX1 - 1, MX2 + 2):
    S(x, G + 5, MZ1 - 1, "stone_brick_stairs[facing=south,half=bottom]")
    S(x, G + 5, MZ2 + 1, "stone_brick_stairs[facing=north,half=bottom]")
for z in range(MZ1, MZ2 + 1):
    S(MX1 - 1, G + 5, z, "stone_brick_stairs[facing=east,half=bottom]")
    S(MX2 + 1, G + 5, z, "stone_brick_stairs[facing=west,half=bottom]")
for x in range(MX1, MX2 + 1):
    for z in range(MZ1, MZ2 + 1):
        S(x, G + 5, z, "stone_bricks")
box(-43, G + 6, 31, -43, G + 7, 31, "stone_brick_wall")
S(-43, G + 8, 31, "soul_lantern[hanging=false]")
S(-43, G + 1, 30, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
S(-43, G + 1, 31, "polished_blackstone")
S(-43, G + 1, 32, "polished_blackstone_brick_stairs[facing=north,half=bottom]")
S(-44, G + 3, 31, "soul_lantern[hanging=true]") if False else None
S(-42, G + 3, 30, "soul_lantern[hanging=true]") if False else None
S(-42, G + 1, 30, "soul_lantern[hanging=false]")
S(-44, G + 1, 30, "cobweb")
S(-45, G + 3, 33, "cobweb")
for z in range(MZ1, MZ2 + 1):
    for x in (MX1, MX2):
        if z in (31,):
            S(x, G + 2, z, "iron_bars")
rsv(MX1, MZ1, MX2, MZ2 + 4, 2)

# tombstones in loose rows, with a few dead trees and cobwebs
for x in range(GX1, GX2 + 1, 3):
    for z in range(GZ1, GZ2 + 1, 4):
        xx, zz = x + int(hash01(x, z, 1, 3) * 2), z + int(hash01(x, z, 2, 3) * 2)
        if not gy(xx, zz) or is_reserved(xx, zz) or hash01(x, z, 4, 3) < 0.25:
            continue
        if area.get(xx, G + 1, zz) not in (None, "minecraft:air"):
            continue
        tombstone(xx, zz, int(hash01(x, z, 5, 3) * 4))
# iron fence round the graveyard where it isn't path or island edge
for x in range(GX1 - 1, GX2 + 2):
    for z in (GZ1 - 1, GZ2 + 1):
        if gy(x, z) and (x, z) not in blocked and (x + z) % 9:
            S(x, G + 1, z, "iron_bars")
for z in range(GZ1 - 1, GZ2 + 2):
    for x in (GX1 - 1, GX2 + 1):
        if gy(x, z) and (x, z) not in blocked and (x + z) % 9:
            S(x, G + 1, z, "iron_bars")
for x, z in ((GX1 - 1, GZ1 - 1), (GX2 + 1, GZ1 - 1), (GX2 + 1, GZ2 + 1)):
    if gy(x, z):
        post(x, z, 3, True)
rsv(GX1 - 1, GZ1 - 1, GX2 + 1, GZ2 + 1, 0)

# ================================================================ trees and ground bits
def tree_ok(x, z, m=2):
    if (x, z) not in cols or area.surface_y(x, z) != G:
        return False
    for dx in range(-m, m + 1):
        for dz in range(-m, m + 1):
            if is_reserved(x + dx, z + dz):
                return False
    return area.get(x, G + 1, z) in (None, "minecraft:air")


n = 0
for (x, z) in sorted(cols):
    r = hash01(x, 71, z, 5)
    if r > 0.0085:
        continue
    gyard = GX1 <= x <= GX2 and GZ1 <= z <= GZ2
    if not tree_ok(x, z, 3 if not gyard else 2):
        continue
    h = 5 + int(hash01(x, 72, z) * 5)
    k = hash01(x, 73, z)
    if k < 0.55 or gyard:
        area.dead_tree(x, G + 1, z, height=h, log="spruce_log" if hash01(x, 74, z) < 0.6 else "dark_oak_log", seed=n)
        if hash01(x, 75, z) < 0.6:
            S(x + 1, G + h - 1, z, "cobweb")
            S(x, G + h + 1, z + 1, "cobweb")
    elif k < 0.8:
        area.spruce(x, G + 1, z, height=8 + int(hash01(x, 76, z) * 4), seed=n)
    else:
        area.oak(x, G + 1, z, height=5, radius=2, log="dark_oak_log", leaves="dark_oak_leaves", seed=n)
    n += 1


def bits(x, z):
    r = hash01(x, 21, z)
    return ("dead_bush" if r < 0.3 else "fern" if r < 0.45 else "short_grass" if r < 0.7 else "cobweb" if r < 0.78 else
            "brown_mushroom" if r < 0.88 else None)


area.scatter(cols, 0.2, bits, seed=9, avoid=is_reserved)
# a few lone tombstones and posts out in the dead woods
m = 0
for (x, z) in sorted(cols):
    if hash01(x, 81, z, 7) < 0.0012 and tree_ok(x, z, 2):
        tombstone(x, z, int(hash01(x, 82, z) * 4))
        m += 1

# ================================================================ the spots
ARR = (0, G + 1, 54)
area.arrival(*ARR, 180.0)
area.warp("Dungeon Hub", *ARR, yaw=180.0)

# ---------------------------------------------------------------- checks
def check(name, x, y, z):
    assert area.solid(x, y - 1, z), f"{name}: no solid block under ({x}, {y}, {z}): {area.get(x, y - 1, z)}"
    for dy in (0, 1):
        b = area.get(x, y + dy, z)
        assert b in (None, "minecraft:air"), f"{name}: not air at ({x}, {y + dy}, {z}): {b}"


check("arrival", *ARR)
for name, e in area.spots["npcs"].items():
    px, py, pz = e["pos"]
    check(name, int(math.floor(px)), int(py), int(math.floor(pz)))
for name, (x, z) in (("ring S", (0, 63)), ("ring N", (0, -63)), ("ring E", (63, 0)), ("ring W", (-63, 0))):
    assert (x, z) in cols, name
assert all((x, z) in cols for x, z in ((-30, 40), (30, 40), (-17, -56), (17, -56), (-36, 20), (36, 20), (0, -62)))
area.save()
print("arrival", ARR, "trees", n, "lone stones", m)
for name, e in area.spots["npcs"].items():
    print(name, e["pos"], e["yaw"])
