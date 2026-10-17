#!/usr/bin/env python3
"""The Ruins (Hypixel's Hub, far west): a floating island with the ruins of an ancient castle, a broken east gatehouse,
a ruined great hall, crumbling towers, a wolf den, and the Lonely Philosopher on a tower. Wolves and Old Wolves roam
the courtyard (Wolf Slayer).

    python tools/builds/hub_areas/ruins.py
"""
import math
from kit import Area, hash01, noise

G = 64
CX, CZ = -312, 25
area = Area("ruins", (-385, 20, -45), (144, 100, 145), version=2)

grass = area.mix({"grass_block": 7, "coarse_dirt": 2, "moss_block": 2, "gravel": 1, "rooted_dirt": 1})
cols = area.island(CX, CZ, 58, top=G, seed=23, surface=grass, hills=1.5, depth=36, wobble=0.15,
                   shape=lambda x, z: 1.0 + 0.14 * max(0.0, (x - CX) / 58.0),
                   stone=area.mix({"andesite": 5, "tuff": 3, "cobblestone": 3, "mossy_cobblestone": 1, "cobbled_deepslate": 2}))

# ---------------------------------------------------------------- the way in: the sky bridge from the Forest
area.bridge((-222, 72), (-262, 40), y=G)

BRICKS = area.mix({"stone_bricks": 4, "mossy_stone_bricks": 4, "cracked_stone_bricks": 3, "cobblestone": 1,
                   "mossy_cobblestone": 2, "andesite": 1})
RUBBLE = area.mix({"cobblestone": 3, "mossy_cobblestone": 3, "andesite": 2, "cracked_stone_bricks": 1, "mossy_stone_bricks": 1})


def put(x, y, z, block):
    if (x, z) in area.top:
        area.set(x, y, z, block)


def brick(x, y, z, seed=0):
    return BRICKS(x, y, z, seed)


# Level the castle ground.
area.flatten(-346, -4, -268, 56, G)
GROUND = area.mix({"grass_block": 6, "moss_block": 3, "coarse_dirt": 2, "gravel": 1, "mossy_cobblestone": 1})
for x in range(-346, -267):
    for z in range(-4, 57):
        if (x, z) in area.top:
            area.set(x, G, z, GROUND(x, G, z, 31))


def wall_run(pts, h, seed, thick=(0, 1), axis="x", skip=None):
    """pts = (a, b, fixed): wall along `axis` from a..b at the fixed other coord, `thick` offsets."""
    a, b, fixed = pts
    for i in range(min(a, b), max(a, b) + 1):
        for t in thick:
            x, z = (i, fixed + t) if axis == "x" else (fixed + t, i)
            if skip and skip(x, z):
                continue
            v = noise(i, fixed, seed, 3.2) * 0.5 + 0.5
            hh = max(1, int(h * (0.3 + 0.7 * v)))
            if hash01(x, 4, z, seed) < 0.12:
                hh = 1 + int(hash01(x, 5, z, seed) * 2)
            for y in range(1, hh + 1):
                if hash01(x, y, z, seed + 9) < 0.07 and y > 2:
                    continue
                put(x, G + y, z, brick(x, y, z, seed))
            if hash01(x, 6, z, seed) < 0.45:
                put(x, G + hh + 1, z, "stone_brick_slab[type=bottom]" if hash01(x, 7, z, seed) < 0.6 else "mossy_stone_brick_slab[type=bottom]")


def tower(cx, cz, half, h, seed, floor=None, ramp_dir=None, opening=True):
    """A square, hollow, crumbling tower; `floor` = a floor at G+floor; ramp_dir='east' for a rubble ramp up to it."""
    for dx in range(-half, half + 1):
        for dz in range(-half, half + 1):
            x, z = cx + dx, cz + dz
            ring = max(abs(dx), abs(dz)) == half
            corner = abs(dx) == half and abs(dz) == half
            put(x, G, z, RUBBLE(x, G, z, seed) if hash01(x, 1, z, seed) < 0.6 else "mossy_stone_bricks")
            if not ring:
                if floor:
                    for y in range(1, floor):
                        put(x, G + y, z, "air")
                    put(x, G + floor, z, brick(x, floor, z, seed))
                continue
            damage = hash01(x, 8, z, seed)
            hh = h if corner else int(h * (0.55 + 0.45 * (noise(x, z, seed, 2.5) * 0.5 + 0.5)))
            if damage < 0.18 and not corner:
                hh = max(2, hh - 4)
            for y in range(1, hh + 1):
                if y > 2 and hash01(x, y, z, seed + 3) < 0.07:
                    continue
                if y in (4, 5) and not corner and (dx % 3 == 0 if abs(dz) == half else dz % 3 == 0) and y == 4:
                    put(x, G + y, z, "air")      # arrow slit
                    continue
                put(x, G + y, z, brick(x, y, z, seed))
            if hash01(x, 9, z, seed) < 0.4:
                put(x, G + hh + 1, z, "stone_brick_slab[type=bottom]")
    if floor and ramp_dir == "east":
        fy = G + floor
        for d in range(1, floor + 1):
            top = fy - d + 1
            for dz in (-1, 0, 1):
                x, z = cx + half + d, cz + dz
                for y in range(G + 1, top):
                    put(x, y, z, RUBBLE(x, y, z, seed))
                put(x, top, z, "stone_brick_stairs[facing=west,half=bottom]" if hash01(x, 0, z, 4) < 0.7 else "mossy_cobblestone")
        # the doorway through the east wall
        for dz in (-1, 0, 1):
            for y in range(1, 4):
                put(cx + half, fy + y, cz + dz, "air")
            put(cx + half, fy, cz + dz, "mossy_stone_bricks")
        # the rubble ramp meets the doorway at floor level
        for dz in (-1, 0, 1):
            put(cx + half + 1, fy, cz + dz, "cobblestone")
        # the parapet around the platform is thin and broken
        for y in range(1, 3):
            for dx in range(-half, half + 1):
                for dz in range(-half, half + 1):
                    if max(abs(dx), abs(dz)) == half and hash01(cx + dx, fy + y, cz + dz, seed) < 0.55 and not (dx == half and abs(dz) <= 1):
                        put(cx + dx, fy + y, cz + dz, brick(cx + dx, y, cz + dz, seed))


def pillar(x, z, h, seed, base=None):
    for y in range(1, h + 1):
        put(x, G + y, z, "chiseled_stone_bricks" if y == 1 else "mossy_stone_bricks" if hash01(x, y, z, seed) < 0.4 else "stone_bricks")
    if hash01(x, 33, z, seed) < 0.6:
        put(x, G + h + 1, z, "stone_brick_stairs[facing=%s,half=bottom]" % ["north", "south", "east", "west"][int(hash01(x, 44, z, seed) * 4) % 4])


def rubble(cx, cz, r, seed, density=0.65):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d > r or hash01(cx + dx, 2, cz + dz, seed) > density:
                continue
            hh = 1 + int((1 - d / (r + 0.5)) * 2.2 * (0.5 + hash01(cx + dx, 3, cz + dz, seed)))
            for y in range(1, hh + 1):
                put(cx + dx, G + y, cz + dz, RUBBLE(cx + dx, y, cz + dz, seed))
            if hash01(cx + dx, 4, cz + dz, seed) < 0.3:
                put(cx + dx, G + hh + 1, cz + dz, "cobblestone_slab[type=bottom]")


def arch(cx, cz, axis, span, h, seed, broken=1):
    """Two pillars `span` either side of (cx, cz) with a (partly fallen) arch over; broken: -1/0/+1 side missing top."""
    for s in (-span, span):
        x, z = (cx + s, cz) if axis == "x" else (cx, cz + s)
        hh = h - (3 if broken == (1 if s > 0 else -1) else 0)
        pillar(x, z, hh, seed)
        # a thick pillar: second block beside
        x2, z2 = (x, z + 1) if axis == "x" else (x + 1, z)
        pillar(x2, z2, hh, seed + 1)
    for w in (0, 1):
        for s in range(-span, span + 1):
            x, z = (cx + s, cz + w) if axis == "x" else (cx + w, cz + s)
            if broken * s > 0 and abs(s) > 1:
                continue
            if abs(s) >= span - 1 and abs(s) != span or abs(s) < span - 1:
                curve = h - (1 if abs(s) >= span - 1 else 0)
                if abs(s) <= 1 and hash01(x, 5, z, seed) < 0.5:
                    continue
                put(x, G + curve + 1, z, brick(x, curve, z, seed))


# ---------------------------------------------------------------- curtain walls (with a gap for the gate on the east)
NX1, NX2, WZ1, WZ2 = -338, -274, 2, 50
wall_run((NX1 + 4, NX2 - 1, WZ1), 8, 41, (0, 1), "x")                   # north
wall_run((NX1 + 4, NX2 - 1, WZ2 - 1), 8, 42, (0, 1), "x")               # south
wall_run((WZ1 + 4, WZ2 - 4, NX1), 9, 43, (0, 1), "z")                   # west
wall_run((WZ1 + 4, 17, NX2 - 1), 8, 44, (0, 1), "z")                    # east, north of the gate
wall_run((33, WZ2 - 4, NX2 - 1), 8, 45, (0, 1), "z")                    # east, south of the gate

# ---------------------------------------------------------------- towers
tower(-338, 2, 4, 14, 51, floor=9, ramp_dir="east")        # NW: the Lonely Philosopher's tower
tower(-338, 50, 4, 12, 52, floor=6, ramp_dir="east")       # SW: a fairy soul on the floor
tower(-274, 50, 3, 7, 53)                                  # SE: a low stump
tower(-274, 2, 3, 9, 54)                                   # NE: half a tower
# the gatehouse: two flanking towers, a broken lintel, the way through paved
tower(-274, 21, 2, 11, 55)
tower(-274, 29, 2, 10, 56)
for z in range(23, 28):
    for x in range(-277, -271):
        for y in range(1, 6):
            put(x, G + y, z, "air")
        put(x, G, z, "cobblestone" if hash01(x, 0, z, 2) < 0.6 else "mossy_cobblestone")
for z in (24, 25, 26):
    for x in (-276, -275, -274, -273):
        if not (z == 26 and x in (-276, -275)):
            put(x, G + 6, z, brick(x, 6, z, 7))
            if hash01(x, 7, z, 7) < 0.7:
                put(x, G + 7, z, "stone_brick_slab[type=bottom]")
for z, face in ((23, "south"), (27, "north")):
    put(-275, G + 5, z, "stone_brick_stairs[facing=%s,half=top]" % face)
    put(-274, G + 5, z, "stone_brick_stairs[facing=%s,half=top]" % face)
rubble(-270, 27, 3, 61)

# ---------------------------------------------------------------- the ruined great hall
HX1, HX2, HZ1, HZ2 = -336, -312, 12, 38
for x in range(HX1, HX2 + 1):
    for z in range(HZ1, HZ2 + 1):
        if (x, z) not in area.top:
            continue
        r = hash01(x, 0, z, 71)
        put(x, G, z, "polished_andesite" if r < 0.3 else "mossy_stone_bricks" if r < 0.6 else "stone_bricks" if r < 0.8 else
            "cracked_stone_bricks" if r < 0.92 else "moss_block")
wall_run((HX1 + 2, HX2, HZ1), 7, 72, (0, 1), "x")
wall_run((HX1 + 2, HX2 - 3, HZ2 - 1), 7, 73, (0, 1), "x")
wall_run((HZ1, HZ2, HX1), 10, 74, (0, 1), "z", skip=lambda x, z: 22 <= z <= 28 and False)
wall_run((HZ1 + 3, HZ2 - 3, HX2 - 1), 5, 75, (0, 1), "z", skip=lambda x, z: 22 <= z <= 28)
for x in range(HX1 + 3, HX2 - 3, 5):
    for z in (HZ1 + 5, HZ2 - 5):
        pillar(x, z, 3 + int(hash01(x, 1, z, 76) * 6), 76)
# the soul pillar (height 3) with its rubble steps
pillar(-324, 33, 3, 77)
for dz in (0, 1):
    put(-323, G + 1, 33 - dz * 0, "mossy_cobblestone")
put(-323, G + 2, 33, "mossy_cobblestone")
put(-322, G + 1, 33, "cobblestone")
# a dais and a broken throne at the west end
for x in range(HX1 + 2, HX1 + 6):
    for z in range(23, 28):
        put(x, G + 1, z, "stone_brick_slab[type=bottom]" if x > HX1 + 3 else "stone_bricks")
put(HX1 + 2, G + 2, 25, "chiseled_stone_bricks")
put(HX1 + 2, G + 2, 24, "stone_brick_stairs[facing=east,half=bottom]")
put(HX1 + 2, G + 2, 26, "stone_brick_stairs[facing=east,half=bottom]")
put(HX1 + 2, G + 3, 25, "mossy_stone_brick_stairs[facing=east,half=bottom]")
# a fallen column, a collapsed corner, a fallen arch in the courtyard
for i in range(7):
    put(-322 + i, G + 1, 20, "stone_bricks" if i % 2 else "mossy_stone_bricks")
rubble(-315, 14, 3, 81)
rubble(-330, 36, 3, 82)
arch(-322, 15, "x", 4, 7, 83, broken=1)              # a fallen arch in the hall's north end
arch(-290, 10, "x", 5, 7, 84, broken=-1)             # a broken arch in the courtyard
arch(-296, 44, "x", 4, 6, 85, broken=0)
# under the courtyard arch: a hidden nook for a Fairy Soul
put(-290, G + 1, 12, "moss_carpet")

# ---------------------------------------------------------------- the wolf den (SW outside the walls)
DX, DZ = -352, 38
area.flatten(DX - 8, DZ - 8, DX + 8, DZ + 8, G)
for dx in range(-8, 9):
    for dz in range(-8, 9):
        for dy in range(1, 8):
            d = dx * dx + dz * dz + (dy * 1.1) ** 2
            if d <= 40:
                x, z, y = DX + dx, DZ + dz, G + dy
                put(x, y, z, "mossy_cobblestone" if hash01(x, y, z, 91) < 0.3 else "andesite" if hash01(x, y, z, 92) < 0.4 else "cobblestone"
                    if hash01(x, y, z, 93) < 0.6 else "tuff")
for dx in range(-4, 5):
    for dz in range(-4, 5):
        for dy in range(1, 5):
            if dx * dx + dz * dz + dy * dy <= 13:
                put(DX + dx, G + dy, DZ + dz, "air")
        put(DX + dx, G, DZ + dz, "coarse_dirt" if hash01(DX + dx, 1, DZ + dz, 94) < 0.7 else "podzol")
for dx in range(3, 9):                       # the entrance faces east, toward the castle
    for dz in (-1, 0, 1):
        for dy in range(1, 3):
            put(DX + dx, G + dy, DZ + dz, "air")
        put(DX + dx, G, DZ + dz, "coarse_dirt")
for (dx, dz, b) in ((-2, -1, "bone_block[axis=x]"), (-2, 0, "bone_block[axis=z]"), (0, 2, "bone_block[axis=y]"),
                    (1, -2, "bone_block[axis=x]"), (-1, 2, "bone_block[axis=z]"), (2, 1, "bone_block[axis=x]")):
    put(DX + dx, G + 1, DZ + dz, b)
put(DX - 1, G + 1, DZ - 2, "skeleton_skull[rotation=4]")
put(DX + 1, G + 1, DZ + 1, "skeleton_skull[rotation=11]")
put(DX - 3, G + 1, DZ, "cobweb")
put(DX - 2, G + 1, DZ + 2, "brown_mushroom")
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if hash01(DX + dx, 12, DZ + dz, 95) < 0.22 and (dx, dz) not in ((0, 2),):
            put(DX + dx, G + 1, DZ + dz, "bone_block[axis=%s]" % "xz"[int(hash01(DX + dx, 13, DZ + dz, 96) * 2)]
                if hash01(DX + dx, 14, DZ + dz, 97) < 0.5 else "short_grass" if False else "bone_block[axis=y]")
put(DX - 2, G + 1, DZ - 3, "lantern[hanging=false]")      # a bit of light so the den can be found
area.set(DX, G + 1, DZ - 1, "air")

# ---------------------------------------------------------------- paths, rubble, bones
area.path([(-262, 40), (-268, 33), (-272, 25), (-285, 25), (-300, 25)], width=3,
          blocks=area.mix({"gravel": 3, "coarse_dirt": 3, "cobblestone": 1, "mossy_cobblestone": 1}))
area.path([(-300, 25), (-318, 25)], width=3, blocks=area.mix({"mossy_stone_bricks": 2, "gravel": 2, "coarse_dirt": 2}))
area.path([(-285, 25), (-300, 8), (-326, 6)], width=2, blocks=area.mix({"coarse_dirt": 3, "gravel": 2}))
for (x, z, r) in ((-296, 36, 2), (-284, 40, 2), (-304, 14, 2), (-282, 8, 1), (-292, 24, 1), (-306, 46, 2), (-346, 22, 2),
                  (-330, -4, 3), (-300, 55, 2), (-266, 12, 2)):
    rubble(x, z, r, int(hash01(x, 0, z, 9) * 1000))
for i, (x, z) in enumerate(((-292, 18), (-286, 33), (-298, 28), (-281, 20), (-304, 38), (-289, 44), (-340, 24), (-356, 28),
                            (-345, 44), (-300, 6))):
    put(x, G + 1, z, "bone_block[axis=%s]" % "xzy"[i % 3])
    put(x + 1, G + 1, z + (i % 2), "skeleton_skull[rotation=%d]" % (i * 3 % 16) if i % 3 == 0 else "bone_block[axis=x]")

# ---------------------------------------------------------------- vines and moss on everything
BBOX = ((-352, G + 1, -6), (-266, G + 16, 58))
STONEY = ("stone_bricks", "stone_brick", "cobblestone", "andesite", "tuff", "polished_andesite", "chiseled", "mossy")
sides = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
for x in range(BBOX[0][0], BBOX[1][0] + 1):
    for z in range(BBOX[0][2], BBOX[1][2] + 1):
        for y in range(BBOX[0][1], BBOX[1][1] + 1):
            if area.get(x, y, z) not in (None, "minecraft:air"):
                continue
            if hash01(x, y, z, 99) > 0.2 * (1.4 - (y - G) / 14.0):
                continue
            attach = []
            for name, (dx, dz) in sides.items():
                nb = area.get(x + dx, y, z + dz)
                if nb and "air" not in nb and any(k in nb for k in STONEY) and "slab" not in nb and "stairs" not in nb:
                    attach.append(name)
            if attach:
                face = attach[int(hash01(x, y, z, 98) * len(attach)) % len(attach)]
                put(x, y, z, "vine[%s=true]" % face)
            elif y > G + 1 and area.get(x, y - 1, z) and any(k in area.get(x, y - 1, z) for k in ("mossy", "cobble", "bricks")) \
                    and "stairs" not in area.get(x, y - 1, z) and hash01(x, y, z, 55) < 0.3:
                put(x, y, z, "moss_carpet")

# ---------------------------------------------------------------- trees and ground cover
for (x, z) in ((-352, 6), (-356, 56), (-262, 8), (-264, 52), (-348, 10), (-300, 62), (-330, 66), (-292, -14), (-318, -12),
               (-358, 20)):
    y = area.surface_y(x, z)
    if y is not None and area.get(x, y + 1, z) in (None, "minecraft:air"):
        if hash01(x, 1, z, 12) < 0.5:
            area.oak(x, y + 1, z, height=5 + int(hash01(x, 2, z, 3) * 3), radius=3, seed=x)
        else:
            area.dead_tree(x, y + 1, z, height=6, log="oak_log", seed=x)
ruin_zone = lambda x, z: -346 <= x <= -270 and -4 <= z <= 54 and not (x > -318 and 4 <= z <= 48 and x < -276 and hash01(x, 1, z, 3) < 0.7)


def ground_bits(x, z):
    r = hash01(x, 21, z)
    return ("short_grass" if r < 0.55 else "fern" if r < 0.7 else "dead_bush" if r < 0.76 else "bone_block[axis=x]" if r < 0.8 else
            "brown_mushroom" if r < 0.84 else "moss_carpet" if r < 0.95 else "sweet_berry_bush[age=2]")


area.scatter(cols, 0.22, ground_bits, seed=6)

# ---------------------------------------------------------------- NPC, souls, warp
for y in (G + 4, G + 5):
    area.set(-324, y, 33, "air")
area.npc("Lonely Philosopher", -338, G + 10, 2, yaw=-90.0, note="on top of the NW tower, looking out over the castle")
area.fairy_soul(-338, G + 7, 50)         # SW tower floor
area.fairy_soul(-324, G + 4, 33)         # on the short column in the great hall
area.fairy_soul(DX - 3, G + 1, DZ + 1)   # deep in the wolf den
area.fairy_soul(-290, G + 1, 12)         # under the courtyard arch
area.warp("Ruins", -263, G + 1, 39, yaw=90.0)
area.spots["notes"].append("Wolf den entrance faces east at (-344, 65, 38); NW tower ramp starts at (-329, 65, 2)")

# ---------------------------------------------------------------- the zone and mobs
BOX = ((-308, G - 2, 4), (-279, G + 8, 47))
area.zone("Ruins", mobs=[
    {"type": "minecraft:wolf", "name": "Wolf", "level": 15, "health": 250, "damage": 90, "xp": 15, "coins": 1,
     "max": 12, "every": 3, "from": list(BOX[0]), "to": list(BOX[1])},
    {"type": "minecraft:wolf", "name": "Old Wolf", "level": 50, "health": 15000, "damage": 720, "xp": 40, "coins": 40,
     "max": 2, "every": 30, "from": list(BOX[0]), "to": list(BOX[1])},
])
area.save()
