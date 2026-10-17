#!/usr/bin/env python3
"""The Forest (Hypixel's Hub, south-west of the village): the first Foraging area. A lush island of oak woods with
2x2-trunk giants, the Lumber Jack's cabin by the path from the bridge, and the launch pad to The Park on the west side.
No mobs; oak logs and leaves can be chopped (and regrow).

    python tools/builds/hub_areas/forest.py
"""
import math
from kit import Area, hash01

G = 64
CX, CZ = -168, 110
area = Area("forest", (-240, 20, 36), (144, 100, 150), version=2)
grass = area.mix({"grass_block": 9, "podzol": 2, "moss_block": 1})
cols = area.island(CX, CZ, 58, top=G, seed=23, surface=grass, hills=2.0, depth=42, wobble=0.15,
                   stone=area.mix({"andesite": 5, "tuff": 3, "cobblestone": 2, "dripstone_block": 1}))

BA, BB = (-117, 84), (-222, 72)   # where the two bridges meet this island
AIR = (None, "minecraft:air")


def landing(px, pz, r):
    """Makes sure the island reaches a bridge end: a small round landing at y 64 where the island is missing."""
    for x in range(px - r, px + r + 1):
        for z in range(pz - r, pz + r + 1):
            if (x - px) ** 2 + (z - pz) ** 2 > r * r or (x, z) in cols:
                continue
            area.set(x, G, z, "minecraft:grass_block")
            for y in range(G - 1, G - 4, -1):
                area.set(x, y, z, "minecraft:dirt")
            depth = 3 + int((r - math.hypot(x - px, z - pz)) * 1.5)
            for y in range(G - 4, G - 4 - depth, -1):
                area.set(x, y, z, "minecraft:andesite" if hash01(x, y, z, 2) < 0.6 else "minecraft:tuff")
            cols.add((x, z))
            area.top[(x, z)] = G


for p, r in ((BA, 6), (BB, 7)):
    landing(p[0], p[1], r)
    area.flatten(p[0] - r - 2, p[1] - r - 2, p[0] + r + 2, p[1] + r + 2, G, "minecraft:grass_block")

# Level spots: the cabin yard and the launch pad.
CAB = (-146, -136, 98, 106)       # x1, x2, z1, z2
area.flatten(CAB[0] - 6, CAB[2] - 4, CAB[1] + 8, CAB[3] + 8, G, "minecraft:grass_block")
LX, LZ = -212, 118
area.flatten(LX - 5, LZ - 5, LX + 5, LZ + 5, G, "minecraft:polished_andesite")

# ---------------------------------------------------------------- paths
pm = area.mix({"dirt_path": 5, "coarse_dirt": 2, "gravel": 1})
pcells = set()
pcells |= area.path([BA, (-128, 88), (-141, 91), (-165, 93), (-190, 90), (-206, 80), BB], width=3, blocks=pm)
pcells |= area.path([(-141, 91), (-141, 97)], width=2, blocks=pm)
pcells |= area.path([(-190, 90), (-198, 104), (-210, 114), (LX, LZ)], width=3, blocks=pm)
pcells |= area.path([(-165, 93), (-165, 125), (-150, 150)], width=2, blocks=area.mix({"coarse_dirt": 2, "dirt_path": 3}))
pcells |= area.path([(-165, 125), (-190, 140)], width=2, blocks=area.mix({"coarse_dirt": 2, "dirt_path": 3}))

# ---------------------------------------------------------------- bridges (same lines as the village and Ruins halves)
area.bridge((-50, 46), (-117, 84), y=G)
area.bridge((-222, 72), (-262, 40), y=G)

# ---------------------------------------------------------------- the Lumber Jack's cabin
x1, x2, z1, z2 = CAB
DOORX = -141
for x in range(x1, x2 + 1):
    for z in range(z1, z2 + 1):
        area.set(x, G, z, "spruce_planks" if (x + z) % 3 else "oak_planks")
        wall = x in (x1, x2) or z in (z1, z2)
        corner = x in (x1, x2) and z in (z1, z2)
        for y in range(G + 1, G + 5):
            if corner:
                area.set(x, y, z, "stripped_oak_log[axis=y]")
            elif wall:
                if y == G + 1:
                    area.set(x, y, z, "cobblestone" if hash01(x, y, z, 5) < 0.5 else "mossy_cobblestone")
                elif y == G + 2 and z in (z1, z2) and x in (x1 + 3, x2 - 3):
                    area.set(x, y, z, "glass_pane")
                elif y == G + 2 and x in (x1, x2) and z == (z1 + z2) // 2:
                    area.set(x, y, z, "glass_pane")
                else:
                    area.set(x, y, z, f"spruce_log[axis={'x' if z in (z1, z2) else 'z'}]")
            else:
                area.set(x, y, z, "air")
for y in (G + 1, G + 2, G + 3):
    area.set(DOORX, y, z1, "air")
area.set(DOORX, G + 1, z1, "spruce_door[facing=north,half=lower,hinge=left]")
area.set(DOORX, G + 2, z1, "spruce_door[facing=north,half=upper,hinge=left]")
area.set(DOORX, G + 3, z1, "oak_planks")
# Roof along x, gabled.
for i in range(0, 6):
    zl, zr = z1 - 1 + i, z2 + 1 - i
    for x in range(x1 - 1, x2 + 2):
        if zl == zr:
            area.set(x, G + 5 + i, zl, "spruce_slab[type=bottom]")
        else:
            area.set(x, G + 5 + i, zl, "spruce_stairs[facing=south,half=bottom]")
            area.set(x, G + 5 + i, zr, "spruce_stairs[facing=north,half=bottom]")
            if x in (x1, x2):
                for z in range(zl + 1, zr):
                    area.set(x, G + 5 + i, z, "spruce_planks")
# Fireplace and chimney in the back east corner.
FX, FZ = x2 - 1, z2 - 1
for y in range(G + 1, G + 11):
    area.set(FX, y, FZ, "cobblestone" if y < G + 9 else "stone_bricks")
area.set(FX - 1, G + 1, FZ, "cobblestone")
area.set(FX, G + 11, FZ, "campfire[lit=true,facing=north]")
area.set(FX - 1, G + 1, FZ - 1, "furnace[facing=west,lit=false]")
# Inside.
area.set(x1 + 1, G + 1, z2 - 1, "red_bed[facing=north,part=foot]")
area.set(x1 + 1, G + 1, z2 - 2, "red_bed[facing=north,part=head]")
area.set(x1 + 1, G + 1, z1 + 1, "crafting_table")
area.set(x1 + 2, G + 1, z1 + 1, "barrel[facing=up]")
area.set(x1 + 1, G + 1, z1 + 2, "barrel[facing=east]")
area.set(x1 + 5, G + 1, z2 - 1, "spruce_stairs[facing=east,half=bottom]")
area.set(x1 + 6, G + 1, z2 - 3, "spruce_fence")
area.set(x1 + 6, G + 2, z2 - 3, "spruce_pressure_plate")
area.set(x1 + 4, G + 4, z1 + 4, "lantern[hanging=true]")
area.set(x2 - 3, G + 4, z2 - 3, "lantern[hanging=true]")
# Lanterns by the door and a porch step.
for dx in (-2, 2):
    area.set(DOORX + dx, G + 1, z1 - 1, "spruce_fence")
    area.set(DOORX + dx, G + 2, z1 - 1, "lantern[hanging=false]")
area.set(DOORX, G, z1 - 1, "stripped_spruce_log[axis=x]")
# Wood piles behind the cabin.
for z in (z2 + 2, z2 + 3):
    for x in range(x1, x1 + 5):
        for y in (G + 1, G + 2):
            area.set(x, y, z, "stripped_oak_log[axis=x]" if (x + y) % 2 else "stripped_spruce_log[axis=x]")
        if x < x1 + 3:
            area.set(x, G + 3, z, "stripped_oak_log[axis=x]")
        else:
            area.set(x, G + 3, z, "spruce_slab[type=bottom]")
for x in range(x2 - 2, x2 + 3):
    for y in (G + 1, G + 2):
        area.set(x, y, z2 + 3, "stripped_oak_log[axis=x]")
# Chopping block with a grindstone blade, and log seats round a cold campfire.
CBX, CBZ = x2 + 3, z1 + 3
area.set(CBX, G + 1, CBZ, "stripped_spruce_log[axis=y]")
area.set(CBX, G + 2, CBZ, "grindstone[face=floor,facing=east]")
area.set(CBX + 2, G + 1, CBZ + 3, "campfire[lit=true,facing=west]")
area.set(CBX + 2, G + 1, CBZ + 1, "stripped_oak_log[axis=z]")
area.set(CBX + 2, G + 1, CBZ + 5, "stripped_oak_log[axis=z]")
area.set(CBX, G + 1, CBZ + 3, "stripped_oak_log[axis=z]")
area.npc("Lumber Jack", DOORX, G + 1, z1 - 3, yaw=180.0, note="outside his cabin; sells axes, saplings and logs")
area.spots["notes"].append(f"Lumber Jack's cabin x {x1}..{x2}, z {z1}..{z2} (door north at x {DOORX}); chopping block at "
                           f"({CBX}, {G + 1}, {CBZ})")

# ---------------------------------------------------------------- the launch pad to The Park
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "stripped_oak_log[axis=x]")
area.set(LX, G + 1, LZ, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(LX + dx, G + 1, LZ + dz, "oak_fence")
    area.set(LX + dx, G + 2, LZ + dz, "lantern[hanging=false]")
area.set(LX + 4, G + 1, LZ - 2, "oak_sign[rotation=4]",
         {"id": "minecraft:sign", "front_text": {"messages": ['""', '"The Park"', '"Foraging I"', '""'],
                                                 "color": "white", "has_glowing_text": 1},
          "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})
area.pad("The Park", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1, warp="park", skill="foraging", level=1)
area.spots["notes"].append(f"The Park launch pad at ({LX}, {G + 1}, {LZ})")


# ---------------------------------------------------------------- the forest
def dilate(cells, r):
    out = set()
    for (x, z) in cells:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                out.add((x + dx, z + dz))
    return out


near2 = dilate(pcells, 2)
near7 = dilate(pcells, 7)
SX, SZ = CAB[0] + 1, CAB[3] + 8     # fairy nook behind the wood pile
HX, HZ = -170, 160                  # hidden glade


def blocked(x, z, big=False):
    if (x, z) in (near7 if big else near2):
        return True
    if CAB[0] - 7 <= x <= CAB[1] + 9 and CAB[2] - 6 <= z <= CAB[3] + 9:
        return True
    if math.hypot(x - LX, z - LZ) < (11 if big else 7):
        return True
    if math.hypot(x - SX, z - SZ) < 6 or math.hypot(x - HX, z - HZ) < 7:
        return True
    for p in (BA, BB):
        if math.hypot(x - p[0], z - p[1]) < (14 if big else 9):
            return True
    return False


def big_oak(x, y, z, height, rx, ry, seed):
    """A 2x2-trunk giant with a wide, lumpy canopy; (x, z) is the north-west trunk block."""
    for h in range(height):
        for dx in (0, 1):
            for dz in (0, 1):
                area.set(x + dx, y + h, z + dz, "minecraft:oak_log[axis=y]")
    for dx, dz in ((-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
        if hash01(x + dx, y, z + dz, seed) < 0.6 and area.top.get((x + dx, z + dz)) == y - 1:
            area.set(x + dx, y, z + dz, "minecraft:oak_log[axis=y]")
    cx, cz, cy = x + 0.5, z + 0.5, y + height - 1
    for k in range(4):
        ang = hash01(x, k, z, seed) * 6.283
        for i in range(1, 5):
            bx, bz = int(round(cx + math.cos(ang) * i)), int(round(cz + math.sin(ang) * i))
            by = cy - 3 + i // 2
            area.set(bx, by, bz, "minecraft:oak_log[axis=" + ("x" if abs(math.cos(ang)) > abs(math.sin(ang)) else "z") + "]")
    for dx in range(-rx - 1, rx + 2):
        for dz in range(-rx - 1, rx + 2):
            for dy in range(-ry - 1, ry + 2):
                d = (dx * dx + dz * dz) / (rx * rx) + (dy * dy) / (ry * ry)
                lump = 0.25 * (hash01(x + dx, y + dy, z + dz, seed + 3) - 0.5)
                if d + lump <= 1 and hash01(x + dx, y + dy, z + dz, seed) > 0.08:
                    px, py, pz = x + dx, cy + dy, z + dz     # (round() of .5 goes to even and skipped every other column)
                    if area.get(px, py, pz) in AIR:
                        area.set(px, py, pz, "minecraft:oak_leaves[persistent=true]")
    return cy + ry


placed = []          # (x, z, big)
tallest = None
for (x, z) in sorted(cols, key=lambda p: hash01(p[0], 31, p[1], 8)):
    if sum(1 for q in placed if q[2]) >= 26:
        break
    y = area.surface_y(x, z)
    if y is None or math.hypot(x - CX, z - CZ) > 49 or blocked(x, z, True) or blocked(x + 1, z + 1, True):
        continue
    if any(q[2] and math.hypot(q[0] - x, q[1] - z) < 15 for q in placed):
        continue
    if not all(area.top.get((x + a, z + b)) == y for a in (0, 1) for b in (0, 1)):
        continue
    h = 11 + int(hash01(x, 33, z) * 6)
    top = big_oak(x, y + 1, z, h, 6 + int(hash01(x, 34, z) * 3), 4, seed=x * 7 + z)
    placed.append((x, z, True))
    if tallest is None or top > tallest[0]:
        tallest = (top, x, z)

for (x, z) in sorted(cols, key=lambda p: hash01(p[0], 41, p[1], 9)):
    y = area.surface_y(x, z)
    if y is None or hash01(x, 42, z, 3) > 0.55 or blocked(x, z):
        continue
    if area.get(x, y + 1, z) not in AIR:
        continue
    if any(math.hypot(q[0] - x, q[1] - z) < (9 if q[2] else 4.2) for q in placed):
        continue
    if math.hypot(x - CX, z - CZ) > 55 and hash01(x, 43, z) < 0.7:
        continue
    h = 4 + int(hash01(x, 44, z) * 5)
    area.oak(x, y + 1, z, height=h, radius=2 + int(hash01(x, 45, z) * 2), seed=x * 3 + z)
    placed.append((x, z, False))
print("trees:", len(placed), "big:", sum(1 for q in placed if q[2]))

# Fallen logs and stumps.
for (x, z) in sorted(cols):
    y = area.surface_y(x, z)
    r = hash01(x, 51, z, 4)
    if y is None or r > 0.006 or blocked(x, z) or area.get(x, y + 1, z) not in AIR:
        continue
    if r < 0.003:
        ax = "x" if hash01(x, 52, z) < 0.5 else "z"
        n = 3 + int(hash01(x, 53, z) * 3)
        for i in range(n):
            px, pz = (x + i, z) if ax == "x" else (x, z + i)
            if area.top.get((px, pz)) == y and area.get(px, y + 1, pz) in AIR:
                area.set(px, y + 1, pz, f"minecraft:oak_log[axis={ax}]")
                if i % 2 == 0 and hash01(px, 54, pz) < 0.5:
                    area.set(px, y + 2, pz, "brown_mushroom" if hash01(px, 55, pz) < 0.5 else "red_mushroom")
    else:
        area.set(x, y + 1, z, "oak_log[axis=y]")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if hash01(x + dx, 57, z + dz) < 0.3 and area.top.get((x + dx, z + dz)) == y and \
                    area.get(x + dx, y + 1, z + dz) in AIR:
                area.set(x + dx, y + 1, z + dz, "brown_mushroom")


def under(x, z):
    r = hash01(x, 61, z)
    return ("short_grass" if r < 0.36 else "fern" if r < 0.54 else "oak_leaves[persistent=true]" if r < 0.62 else
            "azalea" if r < 0.67 else "flowering_azalea" if r < 0.70 else "brown_mushroom" if r < 0.76 else
            "red_mushroom" if r < 0.80 else "oak_sapling[stage=0]" if r < 0.86 else
            "dandelion" if r < 0.91 else "poppy" if r < 0.95 else "lily_of_the_valley")


area.scatter(cols, 0.5, under, seed=9)
# Taller bushes: little leaf clumps.
SOFT = (None, "minecraft:air", "minecraft:fern", "minecraft:short_grass")
for (x, z) in sorted(cols):
    y = area.surface_y(x, z)
    if y is None or hash01(x, 71, z, 2) > 0.012 or blocked(x, z):
        continue
    for dx, dz, dy in ((0, 0, 1), (0, 0, 2), (1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1)):
        if area.top.get((x + dx, z + dz)) == y and area.get(x + dx, y + dy, z + dz) in SOFT:
            area.set(x + dx, y + dy, z + dz, "oak_leaves[persistent=true]")

# ---------------------------------------------------------------- Fairy Souls
# 1: on top of the tallest giant's canopy.
top, tx, tz = tallest
area.set(tx, top + 1, tz, "oak_leaves[persistent=true]")
area.fill(tx, top + 2, tz, tx, top + 3, tz, "air")
area.fairy_soul(tx, top + 2, tz)
# 2: a leafy nook behind the Lumber Jack's wood pile.
area.flatten(SX - 3, SZ - 3, SX + 3, SZ + 3, G, "minecraft:grass_block")
area.fill(SX - 1, G + 1, SZ - 1, SX + 1, G + 3, SZ + 1, "air")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) == 2:
            for y in (G + 1, G + 2, G + 3):
                area.set(SX + dx, y, SZ + dz, "oak_leaves[persistent=true]")
area.fill(SX - 2, G + 4, SZ - 2, SX + 2, G + 4, SZ + 2, "oak_leaves[persistent=true]")
area.set(SX, G + 1, SZ - 2, "air")
area.set(SX, G + 2, SZ - 2, "air")
area.set(SX - 1, G + 1, SZ + 1, "brown_mushroom")
area.fairy_soul(SX + 1, G + 1, SZ)
# 3: a hidden glade at the far south of the island.
area.flatten(HX - 4, HZ - 4, HX + 4, HZ + 4, G, "minecraft:moss_block")
area.fill(HX - 3, G + 1, HZ - 3, HX + 3, G + 5, HZ + 3, "air")
for dx in range(-4, 5):
    for dz in range(-4, 5):
        edge = max(abs(dx), abs(dz))
        if edge == 4 or (edge == 3 and hash01(HX + dx, 81, HZ + dz) < 0.5):
            for y in range(G + 1, G + 5):
                area.set(HX + dx, y, HZ + dz, "oak_leaves[persistent=true]")
        area.set(HX + dx, G + 5, HZ + dz, "oak_leaves[persistent=true]")
for y in (G + 1, G + 2, G + 3):
    area.set(HX, y, HZ - 4, "air")
    area.set(HX, y, HZ - 3, "air")
area.set(HX + 1, G + 1, HZ + 1, "flowering_azalea")
area.set(HX - 1, G + 1, HZ + 1, "red_mushroom")
area.set(HX + 1, G + 1, HZ - 1, "lily_of_the_valley")
area.fairy_soul(HX, G + 1, HZ)

area.warp("Forest", -121, G + 1, 82, yaw=-60.0)

area.zone("Forest", blocks={"minecraft:oak_log": {"becomes": "minecraft:air", "regrow": 20},
                            "minecraft:oak_leaves": {"becomes": "minecraft:air", "regrow": 15}})
area.save()
