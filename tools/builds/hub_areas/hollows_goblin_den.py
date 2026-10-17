#!/usr/bin/env python3
"""The Goblin Queen's Den and King Yolkar's tower (Crystal Hollows, Goblin Holdout), in a rock-filled box
(x -180..-110, y 90..140, z 110..180). A big goblin yard cavern with a spiral-stair spruce tower (King Yolkar on top),
four huts, barricades and camp fires; a stair and gate lead north up into the Queen's den (hay, gold and loot, the
throne) with a fairy soul niche at its west end. Tunnels: west, east and south from the yard, north from the den.

    python tools/builds/hub_areas/hollows_goblin_den.py
"""
import math

from hollows_lib import Builder, sq, hash01, noise

B = Builder("hollows_goblin_den", (-180, 90, 110), (70, 50, 70))
put, fill, carve, pick, column = B.put, B.fill, B.carve, B.pick, B.column

CX, CZ, YF = -145, 153, 95            # yard centre and floor block y
DF = 99                               # the den's floor block y
YARD_FLOOR = [("coarse_dirt", 6), ("rooted_dirt", 2), ("packed_mud", 2), ("gravel", 1), ("podzol", 1)]
DEN_FLOOR = [("coarse_dirt", 5), ("rooted_dirt", 2), ("packed_mud", 2), ("gravel", 1)]

# ============================================================ the yard cavern
yard = set()
for x in range(CX - 31, CX + 32):
    for z in range(CZ - 22, CZ + 23):
        u = ((x - CX) / 30) ** 2 + ((z - CZ) / 21) ** 2 + 0.05 * noise(x, z, 6, 1.4)
        if u < 1:
            carve(x, YF + 1, z, x, YF + 8 + int(28 * sq(1 - u)), z)
            put(x, YF, z, pick(x, YF, z, YARD_FLOOR, 2))
            yard.add((x, z))

# ============================================================ King Yolkar's tower (13 x 13, spiral stair inside)


def ring_p(dx, dz):
    """Position 0..23 along the 3-wide spiral ring (rises one block per step, starts by the east door)."""
    if abs(dx) >= abs(dz):
        s = (dz / dx + 1) / 2 if dx > 0 else 2 + (1 - dz / abs(dx)) / 2
    else:
        s = 1 + (1 - dx / dz) / 2 if dz > 0 else 3 + (dx / abs(dz) + 1) / 2
    return min(23, int(s * 6))


TOP = 119                                        # the platform's floor block y
ring_floor = {}
for dx in range(-5, 6):
    for dz in range(-5, 6):
        if 3 <= max(abs(dx), abs(dz)) <= 5:
            ring_floor[(dx, dz)] = 96 + ring_p(dx, dz)
for dx in range(-6, 7):
    for dz in range(-6, 7):
        x, z = CX + dx, CZ + dz
        ch = max(abs(dx), abs(dz))
        if ch <= 2:                              # the solid core
            for y in range(YF + 1, TOP):
                put(x, y, z, "spruce_log[axis=y]" if ch == 2 and abs(dx) == abs(dz) else pick(x, y, z, [("dark_oak_planks", 3), ("spruce_planks", 1)], 3))
            put(x, TOP, z, "spruce_planks" if (dx + dz) % 2 else "dark_oak_planks")
        elif ch <= 5:
            fy = ring_floor[(dx, dz)]
            for y in range(YF + 1, fy):
                put(x, y, z, "dark_oak_planks")
            put(x, fy, z, "spruce_planks" if (dx + dz) % 2 else "dark_oak_planks")
        else:                                    # the wall, y95..123
            for y in range(YF, 124):
                k = y - YF
                if abs(dx) == 6 and abs(dz) == 6:
                    b = "spruce_log[axis=y]"
                elif k in (5, 11, 17, 23):
                    b = "stripped_dark_oak_log[axis=%s]" % ("x" if abs(dz) == 6 else "z")
                else:
                    b = "dark_oak_planks" if hash01(x, y, z, 2) < 0.8 else "spruce_planks"
                put(x, y, z, b)
            off = dx if abs(dz) == 6 else dz
            if abs(off) == 0 and (YF + 9 <= 104 or True):
                for y in (104, 105, 112, 113):
                    put(x, y, z, "iron_bars")
            if abs(off) <= 1:
                for y in (121, 122):
                    put(x, y, z, "iron_bars")
# the door in the east wall (by the start of the stair) and its frame
for dz in (-5, -4, -3):
    for y in (96, 97, 98):
        put(CX + 6, y, CZ + dz, "air")
    put(CX + 6, 99, CZ + dz, "spruce_log[axis=z]")
for dz in (-6, -2):
    for y in range(96, 100):
        put(CX + 6, y, CZ + dz, "spruce_log[axis=y]")
put(CX + 7, 99, CZ - 4, "lantern[hanging=false]")
# fences where a fall would hurt: the core edge above the lower flights and the seam
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if max(abs(dx), abs(dz)) != 2:
            continue
        for ex, ez in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = ring_floor.get((dx + ex, dz + ez))
            if n is not None and n < TOP - 3:
                put(CX + dx, TOP + 1, CZ + dz, "spruce_fence")
for (dx, dz), fy in ring_floor.items():
    for ex, ez in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = ring_floor.get((dx + ex, dz + ez))
        if n is not None and fy - n > 2:
            put(CX + dx, fy + 1, CZ + dz, "spruce_fence")
# wall torches along the outer wall of the stair
for (dx, dz), fy in ring_floor.items():
    if max(abs(dx), abs(dz)) == 5 and min(abs(dx), abs(dz)) < 5 and ring_p(dx, dz) % 2 == 1 and fy < TOP - 1:
        face = ("west" if dx > 0 else "east") if abs(dx) == 5 else ("north" if dz > 0 else "south")
        put(CX + dx, fy + 2, CZ + dz, f"wall_torch[facing={face}]")
# the roof and the spire
for i, (half, y) in enumerate(((7, 124), (5, 125), (3, 126), (1, 127))):
    for dx in range(-half, half + 1):
        for dz in range(-half, half + 1):
            put(CX + dx, y, CZ + dz, "spruce_planks" if i % 2 == 0 else "dark_oak_planks")
put(CX, 128, CZ, "dark_oak_fence")
put(CX, 129, CZ, "lantern[hanging=false]")
for dx, dz in ((-4, -4), (4, -4), (-4, 4), (4, 4), (0, 4), (0, -4), (4, 0), (-4, 0)):
    put(CX + dx, 123, CZ + dz, "lantern[hanging=true]")
for dx, dz, face in ((0, 7, "south"), (0, -7, "north"), (-7, 0, "west")):
    for off in (-3, 3):
        x, z = CX + dx + (off if dz else 0), CZ + dz + (off if dx else 0)
        put(x, 108, z, f"{'red' if off > 0 else 'black'}_wall_banner[facing={face}]")
for off in (-1, 1):
    put(CX + 7, 108, CZ + off * 3 + 1 if False else CZ + off * 2 + (1 if off > 0 else -1) * 2, "red_wall_banner[facing=east]")
B.npc("King Yolkar", CX, TOP + 1, CZ, 180.0, "quest: King Yolkar at the top of his tower")

# ============================================================ huts, camps, barricades in the yard


def stair(x, y, z, facing):
    put(x, y, z, f"spruce_stairs[facing={facing},half=bottom]")


def hut(hx, hz, door):
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            x, z = hx + dx, hz + dz
            ch = max(abs(dx), abs(dz))
            put(x, YF, z, "spruce_planks")
            for y in range(YF + 1, YF + 4):
                put(x, y, z, "air")
            if ch == 3:
                for y in range(YF + 1, YF + 4):
                    b = "spruce_log[axis=y]" if abs(dx) == 3 and abs(dz) == 3 else \
                        ("dark_oak_planks" if hash01(x, y, z, 5) < 0.75 else "spruce_planks")
                    put(x, y, z, b)
                if min(abs(dx), abs(dz)) == 0 and (dx, dz) != door:
                    put(x, YF + 2, z, "iron_bars")
    for dz in (-1, 0, 1):                         # the door
        if door[0]:
            for y in (YF + 1, YF + 2):
                put(hx + door[0] * 3, y, hz + dz, "air")
            put(hx + door[0] * 3, YF + 3, hz + dz, "spruce_log[axis=z]")
        else:
            for y in (YF + 1, YF + 2):
                put(hx + dz, y, hz + door[1] * 3, "air")
            put(hx + dz, YF + 3, hz + door[1] * 3, "spruce_log[axis=x]")
    for half, y in ((4, YF + 4), (3, YF + 5), (2, YF + 6)):    # stepped roof with stair edges
        for dx in range(-half, half + 1):
            for dz in range(-half, half + 1):
                ch = max(abs(dx), abs(dz))
                x, z = hx + dx, hz + dz
                if ch == half and abs(dx) != abs(dz):
                    face = ("west" if dx > 0 else "east") if abs(dx) > abs(dz) else ("north" if dz > 0 else "south")
                    stair(x, y, z, face)
                else:
                    put(x, y, z, "dark_oak_planks" if hash01(x, y, z, 6) < 0.6 else "spruce_planks")
    put(hx, YF + 7, hz, "spruce_log[axis=y]")
    put(hx, YF + 8, hz, "lantern[hanging=false]")
    # inside: hay bed, barrel, chest, a camp fire
    back = (-door[0], -door[1])
    put(hx + back[0] * 2, YF + 1, hz + back[1] * 2, "hay_block[axis=y]")
    put(hx + back[0] * 2 + back[1], YF + 1, hz + back[1] * 2 + back[0], "hay_block[axis=y]")
    put(hx + back[0] * 2 - back[1], YF + 1, hz + back[1] * 2 - back[0], "barrel[facing=up]")
    put(hx, YF + 1, hz, "campfire[lit=true,facing=north]")


for hx, hz, door in ((CX - 19, CZ - 8, (1, 0)), (CX + 19, CZ - 8, (-1, 0)), (CX - 19, CZ + 8, (1, 0)), (CX + 19, CZ + 8, (-1, 0))):
    hut(hx, hz, door)


def camp(x, z):
    put(x, YF + 1, z, "campfire[lit=true,facing=north]")
    for dx, dz, ax in ((2, 0, "z"), (-2, 0, "z"), (0, 2, "x"), (0, -2, "x")):
        put(x + dx, YF + 1, z + dz, f"spruce_log[axis={ax}]")
    put(x + 2, YF + 1, z + 2, "hay_block[axis=y]")
    put(x - 2, YF + 1, z - 2, "barrel[facing=up]")


camp(CX - 12, CZ + 14)
camp(CX + 12, CZ + 14)
camp(CX - 10, CZ - 15)
camp(CX + 10, CZ - 15)


def barricade(x, z, axis, gap, length):
    """A line of spruce log posts and fences across the way, with a gap `gap` each side of the centre."""
    for l in range(-length, length + 1):
        if abs(l) < gap:
            continue
        px, pz = (x + l, z) if axis == "x" else (x, z + l)
        if abs(l) == gap or (abs(l) - gap) % 4 == 0:
            for y in range(YF + 1, YF + 5 if abs(l) == gap else YF + 4):
                put(px, y, pz, "spruce_log[axis=y]")
            put(px, YF + (5 if abs(l) == gap else 4), pz, "lantern[hanging=false]" if abs(l) == gap else "spruce_fence")
        else:
            put(px, YF + 1, pz, "spruce_fence")
            put(px, YF + 2, pz, "spruce_fence")


barricade(CX - 26, CZ, "z", 3, 9)
barricade(CX + 26, CZ, "z", 3, 9)
barricade(CX, CZ + 17, "x", 3, 9)

# ============================================================ the stair and gate up to the Queen's den
PX0, PX1 = CX - 3, CX + 3                       # the passage, x -148..-142
carve(PX0, YF + 1, 124, PX1, DF + 6, 136)
for x in range(PX0, PX1 + 1):
    for z in range(124, 131):
        put(x, DF, z, "stone_bricks" if (x + z) % 2 else "polished_andesite")
    for z, y in ((131, 99), (132, 98), (133, 97), (134, 96)):
        for yy in range(YF, y):
            put(x, yy, z, "spruce_planks")
        stair(x, y, z, "north")
    put(x, YF, 135, pick(x, YF, 135, YARD_FLOOR, 2))
for z in range(124, 136):
    for x in (PX0 - 1, PX1 + 1):
        for y in range(YF + 1, DF + 5):
            put(x, y, z, "dark_oak_planks" if (y + z) % 3 else "spruce_planks")
        put(x, DF + 5, z, "spruce_log[axis=z]")
    if z % 4 == 0:
        for x in (PX0 - 1, PX1 + 1):
            put(x, DF + 2, z, "lantern[hanging=false]") if False else None
for z in (126, 130):
    for x in (PX0 - 1, PX1 + 1):
        put(x, DF + 3, z, "shroomlight")
for x in range(PX0 - 1, PX1 + 2):                # the gate lintel
    for y in range(DF + 5, DF + 7):
        put(x, y, 124, "spruce_log[axis=x]")
        put(x, y, 125, "dark_oak_planks")
    put(x, DF + 4, 124, "iron_bars") if x in (PX0 + 1, PX0 + 3, PX1 - 1) else None
for x in (CX - 1, CX + 1):
    put(x, DF + 4, 125, "lantern[hanging=true]")
for x in (PX0 - 2, PX1 + 2):                     # gate posts and banners at the yard side
    for y in range(YF + 1, YF + 9):
        put(x, y, 137, "spruce_log[axis=y]")
    put(x, YF + 9, 137, "lantern[hanging=false]")
put(PX0 - 2, YF + 6, 138, "red_wall_banner[facing=south]")
put(PX1 + 2, YF + 6, 138, "black_wall_banner[facing=south]")

# ============================================================ the Queen's den
DCX, DCZ = -146, 121
den = set()
for x in range(DCX - 25, DCX + 26):
    for z in range(DCZ - 11, DCZ + 12):
        u = ((x - DCX) / 24) ** 2 + ((z - DCZ) / 10) ** 2 + 0.05 * noise(x, z, 9, 1.4)
        if u < 1:
            carve(x, DF + 1, z, x, DF + 6 + int(14 * sq(1 - u)), z)
            put(x, DF, z, pick(x, DF, z, DEN_FLOOR, 4))
            den.add((x, z))
# hay beds and the big table
for (x, z) in sorted(den):
    if hash01(x, 9, z, 12) < 0.045 and not (-150 <= x <= -128 and 117 <= z <= 125) and not (-149 <= x <= -141 and z >= 126):
        put(x, DF, z, "hay_block[axis=y]")


def pile(px, pz, r, seed):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = abs(dx) + abs(dz)
            if d > r:
                continue
            for h in range(r - d + 1):
                b = B.pick(px + dx, DF + 1 + h, pz + dz, [("gold_block", 5), ("raw_gold_block", 3), ("hay_block[axis=y]", 2), ("gold_ore", 1)], seed)
                put(px + dx, DF + 1 + h, pz + dz, b)
    put(px, DF + 2 + r, pz, "red_banner[rotation=0]") if r >= 2 else None


for px, pz, r, s in ((-137, 115, 2, 1), (-137, 127, 3, 2), (-152, 114, 2, 3), (-158, 128, 2, 4), (-164, 116, 3, 5),
                     (-132, 113, 1, 6), (-128, 128, 1, 7), (-166, 126, 2, 8), (-158, 114, 1, 9), (-149, 129, 1, 10)):
    pile(px, pz, r, s)
for cx_, cz_ in ((-141, 127), (-153, 115), (-162, 123), (-135, 114)):
    put(cx_, DF + 1, cz_, "chest[facing=south]")
    put(cx_ + 1, DF + 1, cz_, "barrel[facing=up]")
for cx_, cz_ in ((-155, 125), (-143, 116), (-148, 120)):
    put(cx_, DF + 1, cz_, "campfire[lit=true,facing=north]")
    for dx, dz, ax in ((1, 0, "z"), (-1, 0, "z"), (0, 1, "x"), (0, -1, "x")):
        put(cx_ + dx, DF + 1, cz_ + dz, f"spruce_log[axis={ax}]") if (cx_ + dx, cz_ + dz) not in ((-149, 120),) else None
# the throne on its dais at the east end
for x in range(-133, -125):
    for z in range(117, 126):
        put(x, DF + 1, z, "polished_blackstone_bricks" if (x + z) % 2 else "gilded_blackstone" if hash01(x, 1, z, 3) < 0.15 else "dark_oak_planks")
for x in range(-131, -125):
    for z in range(119, 124):
        put(x, DF + 2, z, "dark_oak_planks" if (x + z) % 2 else "spruce_planks")
put(-127, DF + 3, 121, "spruce_stairs[facing=east,half=bottom]")
put(-127, DF + 3, 120, "dark_oak_slab[type=bottom]")
put(-127, DF + 3, 122, "dark_oak_slab[type=bottom]")
for z in (120, 121, 122):
    for y in range(DF + 3, DF + 9):
        put(-126, y, z, "dark_oak_planks" if y < DF + 8 else "gold_block")
for y in range(DF + 3, DF + 6):
    put(-126, y, 121, "gold_block" if y == DF + 4 else "dark_oak_planks")
put(-126, DF + 9, 121, "gold_block")
put(-127, DF + 6, 120, "red_wall_banner[facing=west]")
put(-127, DF + 6, 122, "red_wall_banner[facing=west]")
for z in (118, 124):
    put(-131, DF + 3, z, "campfire[lit=true,facing=north]")
    put(-128, DF + 3, z, "lantern[hanging=false]")
    put(-131, DF + 4, z, "air")
B.area.spots["queen"] = [-128, DF + 3, 121]
for x, z in ((-166, 121), (-160, 117), (-150, 113), (-140, 114), (-132, 114), (-160, 127), (-148, 128), (-136, 129)):
    pass
for px, pz in ((-162, 121), (-154, 118), (-154, 124), (-138, 119), (-138, 124), (-146, 127)):
    B.chain_lamp(px, pz, DF + 8)
B.stalactites(sorted(den), 0.05, 3)
B.stalactites(sorted(yard), 0.025, 4)
for px, pz in ((CX - 14, CZ - 13), (CX + 14, CZ - 13), (CX - 14, CZ + 13), (CX + 14, CZ + 13), (CX - 24, CZ), (CX + 24, CZ),
               (CX, CZ + 16), (CX - 8, CZ + 6), (CX + 8, CZ + 6)):
    B.chain_lamp(px, pz, YF + 12)

# the fairy soul niche at the den's west end
carve(-176, DF + 1, 120, -167, DF + 3, 122)
for x in range(-176, -166):
    for z in range(120, 123):
        put(x, DF, z, "gold_block" if x == -176 else pick(x, DF, z, DEN_FLOOR, 4))
put(-176, DF + 1, 120, "raw_gold_block")
put(-176, DF + 1, 122, "hay_block[axis=y]")
put(-175, DF + 1, 120, "lantern[hanging=false]")
B.fairy_soul(-174, DF + 1, 121)
B.must["queen spot"] = (-128, DF + 3, 121)

# ============================================================ tunnels
TFLOOR = [("coarse_dirt", 3), ("spruce_planks", 2), ("gravel", 1)]
B.tunnel("x", CZ, YF, -180, -172, floor=TFLOOR)
B.tunnel("x", CZ, YF, -118, -111, floor=TFLOOR)
B.tunnel("z", CX, YF, 172, 179, floor=TFLOOR)
B.tunnel("z", -155, DF, 110, 114, floor=TFLOOR, lamp_every=4)

B.arrival = (CX, YF + 1, CZ + 15)
STONE = [("stone", 6), ("andesite", 2), ("tuff", 2), ("coarse_dirt", 1), ("cobbled_deepslate", 1)]
B.finish(lambda x, y, z: B.pick(x, y, z, STONE, 15), ores=[("gold_ore", 3), ("deepslate_gold_ore", 2), ("coal_ore", 2), ("iron_ore", 1)],
         ore_chance=0.03, spots=None,
         tunnel_ends=(("tunnel W", (-180, YF + 1, CZ)), ("tunnel E", (-111, YF + 1, CZ)), ("tunnel S", (CX, YF + 1, 179)),
                      ("tunnel N", (-155, DF + 1, 110))))
