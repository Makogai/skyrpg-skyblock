#!/usr/bin/env python3
"""The Mines of Divan (Crystal Hollows, Mithril Deposits): an ancient dwarven mine complex carved into a rock-filled box
(x 110..180, y 90..140, z -180..-110). A domed central chamber with the Jade pedestal, four Keeper halls (north, east,
south, west) with their statues' plinths, the Forger's workshop off the south hall, a fairy soul niche off the west hall
and a mine tunnel (3 x 3, with rails) from every hall out through the box faces.

    python tools/builds/hub_areas/hollows_mines_of_divan.py
"""
import math

from hollows_lib import Builder, sq, hash01

B = Builder("hollows_mines_of_divan", (110, 90, -180), (70, 50, 70))
put, fill, carve, pick, column = B.put, B.fill, B.carve, B.pick, B.column

CX, CZ, FL = 145, -145, 100          # chamber centre; floor block y (players stand at 101)
DEEP = [("polished_deepslate", 4), ("deepslate_bricks", 4), ("deepslate_tiles", 3), ("cracked_deepslate_bricks", 1)]
BLACK = [("polished_blackstone_bricks", 6), ("blackstone", 1), ("cracked_polished_blackstone_bricks", 1)]
R = 13


def H(r):
    return 6 + int(16 * sq(1 - (r / 13.5) ** 2))


# ============================================================ the central chamber
for x in range(CX - R, CX + R + 1):
    for z in range(CZ - R, CZ + R + 1):
        r = math.hypot(x - CX, z - CZ)
        if r > R + 0.3:
            continue
        carve(x, FL + 1, z, x, FL + H(r), z)
        ang = math.atan2(z - CZ, x - CX)
        if r < 5.5:
            b = pick(x, FL, z, BLACK, 1)
        elif r < 6.6:
            b = "gold_block" if int((ang + math.pi) / (math.pi / 8)) % 2 == 0 else "polished_blackstone"
        elif r < 10.6:
            b = "deepslate_tiles" if (x + z) % 2 else "polished_deepslate"
        elif r < 11.8:
            b = "gold_block" if (x + z) % 3 == 0 else "polished_blackstone_bricks"
        else:
            b = "deepslate_bricks"
        put(x, FL, z, b)
        # lining of the dome wall (leave the four hall mouths open)
        if r >= 12.0 and abs(x - CX) > 5 and abs(z - CZ) > 5:
            for y in range(FL + 1, FL + H(r) + 1):
                if (y - FL) % 5 == 0:
                    b = "gold_block" if (x + z) % 2 == 0 else "polished_blackstone_bricks"
                elif hash01(x, y, z, 3) < 0.07 and y - FL < 9:
                    b = "shroomlight"
                else:
                    b = pick(x, y, z, DEEP, 2)
                put(x, y, z, b)

# the dais and the Jade pedestal
for dx in range(-2, 3):
    for dz in range(-2, 3):
        put(CX + dx, FL + 1, CZ + dz, "polished_blackstone_bricks" if max(abs(dx), abs(dz)) == 2 else "chiseled_polished_blackstone")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        put(CX + dx, FL + 2, CZ + dz, "gilded_blackstone" if dx == 0 and dz == 0 else "polished_blackstone_bricks")
put(CX, FL + 3, CZ, "gold_block")
for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
    put(CX + dx, FL + 3, CZ + dz, "end_rod[facing=up]")
for dx, dz in ((-4, -4), (4, -4), (-4, 4), (4, 4)):      # braziers
    put(CX + dx, FL + 1, CZ + dz, "gold_block")
    put(CX + dx, FL + 2, CZ + dz, "campfire[lit=true,facing=north]")

# the four pillars
for sx in (-1, 1):
    for sz in (-1, 1):
        px, pz = CX + sx * 7, CZ + sz * 7
        ty = B.top_air(px, pz)
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                corner = abs(dx) == 1 and abs(dz) == 1
                for y in range(FL + 1, ty + 1):
                    k = y - FL
                    if k <= 2:
                        b = "polished_blackstone_bricks"
                    elif k % 6 == 5:
                        b = "gold_block"
                    elif corner:
                        b = "deepslate_bricks"
                    elif k % 6 == 2 and (dx == 0 or dz == 0):
                        b = "shroomlight"
                    else:
                        b = "polished_deepslate"
                    put(px + dx, y, pz + dz, b)
# chandelier over the pedestal
for dx, dz in ((0, 0), (4, 0), (-4, 0), (0, 4), (0, -4)):
    B.chain_lamp(CX + dx, CZ + dz, FL + 11 if (dx or dz) else FL + 8)

# ============================================================ the four Keeper halls
THEMES = {
    "N": ((0, -1), "diamond_block", "deepslate_diamond_ore", "Keeper of Diamond", 0.0),
    "E": ((1, 0), "gold_block", "deepslate_gold_ore", "Keeper of Gold", 90.0),
    "S": ((0, 1), "emerald_block", "deepslate_emerald_ore", "Keeper of Emerald", 180.0),
    "W": ((-1, 0), "lapis_block", "deepslate_lapis_ore", "Keeper of Lapis", 270.0),
}
TUNNEL_ENDS = []


def W(d, t, l):
    px, pz = abs(d[1]), abs(d[0])
    return CX + d[0] * t + px * l, CZ + d[1] * t + pz * l


for key, (d, theme, ore, npc_name, yaw) in THEMES.items():
    for t in range(11, 32):
        for l in range(-5, 6):
            x, z = W(d, t, l)
            carve(x, FL + 1, z, x, FL + (10 if abs(l) <= 2 else 8), z)
    for t in range(11, 36):
        for l in range(-5, 6):
            if t >= 32 and abs(l) > 1:
                continue
            x, z = W(d, t, l)
            if abs(l) <= 1:
                b = "polished_blackstone_bricks" if t % 4 else "gold_block"
            else:
                b = "deepslate_tiles" if (t + l) % 2 else "polished_deepslate"
            put(x, FL, z, b)
    # side wall lining, ore insets, embedded lights
    for t in range(14, 32):
        for l in (-6, 6):
            for y in range(FL + 1, FL + 9):
                x, z = W(d, t, l)
                k = y - FL
                if t % 4 == 1 and k in (2, 3):
                    b = ore
                elif t % 5 == 1 and k == 5:
                    b = "shroomlight"
                elif k == 7:
                    b = "polished_blackstone_bricks"
                else:
                    b = pick(x, y, z, DEEP, 4)
                put(x, y, z, b)
    for l in range(-5, 6):                      # the end wall around the tunnel mouth
        if abs(l) >= 2:
            for y in range(FL + 1, FL + 9):
                x, z = W(d, 32, l)
                put(x, y, z, pick(x, y, z, DEEP, 6))
    # the tunnel out
    for t in range(32, 36):
        for l in (-1, 0, 1):
            x, z = W(d, t, l)
            carve(x, FL + 1, z, x, FL + 3, z)
        x, z = W(d, t, 0)
        put(x, FL + 1, z, "rail[shape=%s]" % ("north_south" if d[0] == 0 else "east_west"))
    for l in (-2, 2):                           # timber frame
        for y in range(FL + 1, FL + 4):
            x, z = W(d, 33, l)
            put(x, y, z, "oak_log[axis=y]")
    for l in (-1, 0, 1):
        x, z = W(d, 33, l)
        put(x, FL + 4, z, "oak_log[axis=%s]" % ("x" if d[0] == 0 else "z"))
    x, z = W(d, 34, 0)
    put(x, FL + 3, z, "lantern[hanging=true]")
    x, z = W(d, 32, 0)
    put(x, FL + 3, z, "lantern[hanging=true]")
    x, z = W(d, 35 if d[0] + d[1] < 0 else 34, 0)
    TUNNEL_ENDS.append((f"tunnel {key}", (x, FL + 1, z)))
    # the Keeper's plinth, arch and lanterns
    for a in range(-1, 2):
        for b in range(-1, 2):
            x, z = W(d, 24 + a, b)
            put(x, FL + 1, z, theme if (a == 0 and b == 0) or abs(a) + abs(b) == 2 and False else
                ("polished_blackstone_bricks" if abs(a) + abs(b) else theme))
    for l in (-3, 3):
        for y in range(FL + 1, FL + 6):
            x, z = W(d, 24, l)
            put(x, y, z, theme if y == FL + 1 else "deepslate_bricks")
    for l in range(-3, 4):
        x, z = W(d, 24, l)
        put(x, FL + 6, z, "polished_blackstone_bricks")
        if abs(l) <= 1:
            put(x, FL + 7, z, theme)
    x, z = W(d, 24, 0)
    put(x, FL + 5, z, "lantern[hanging=true]")
    for l in (-2, 2):
        x, z = W(d, 21, l)
        put(x, FL + 1, z, "gold_block")
        put(x, FL + 2, z, "campfire[lit=true,facing=north]")
    x, z = W(d, 24, 0)
    B.npc(npc_name, x, FL + 2, z, yaw, f"keeper: {npc_name}")

# ============================================================ the Forger's workshop (off the south hall)
carve(154, FL + 1, -132, 166, FL + 6, -120)
for x in range(154, 167):
    for z in range(-132, -119):
        put(x, FL, z, "deepslate_tiles" if (x + z) % 2 else pick(x, FL, z, BLACK, 5))
carve(151, FL + 1, -127, 153, FL + 3, -125)
for x in range(151, 154):
    for z in range(-127, -124):
        put(x, FL, z, "polished_blackstone_bricks")
for z in range(-127, -124):
    for y in range(FL + 1, FL + 4):
        put(151, y, z, "air")
    put(151, FL + 4, z, "deepslate_bricks")
for x in range(154, 167):                       # workbench row in the north wall
    if x % 2 == 0:
        put(x, FL + 1, -133, "blast_furnace[facing=south,lit=true]")
    elif x % 4 == 1:
        put(x, FL + 1, -133, "smithing_table")
    else:
        put(x, FL + 1, -133, "furnace[facing=south,lit=true]")
    put(x, FL + 2, -133, "polished_blackstone_bricks")
for x in (157, 163):
    put(x, FL + 1, -120, "anvil[facing=east]")
put(160, FL + 1, -126, "anvil[facing=east]")
for z in (-132, -120):
    for x in (154, 166):
        put(x, FL + 1, z, "lava_cauldron")
for z in (-125, -124, -123):
    put(166, FL + 1, z, "barrel[facing=west]")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    pass
put(158, FL + 1, -128, "grindstone[face=floor,facing=north]")
for x, z in ((157, -129), (163, -129), (157, -123), (163, -123)):
    put(x, FL + 6, z, "lantern[hanging=true]")
B.npc("Mines of Divan Forger", 161, FL + 1, -126, 90.0, "forge: the Mines of Divan Forger at the anvil")

# ============================================================ fairy soul niche (off the west hall's north wall)
carve(116, FL + 1, -155, 118, FL + 3, -152)
for x in range(116, 119):
    for z in range(-155, -151):
        put(x, FL, z, "polished_blackstone_bricks")
for x in range(116, 119):
    for y in range(FL + 1, FL + 4):
        put(x, y, -151, "air")
    put(x, FL + 4, -151, "deepslate_bricks")
put(116, FL + 1, -155, "gold_block")
put(118, FL + 1, -155, "lantern[hanging=false]")
put(116, FL + 2, -155, "lantern[hanging=false]")
B.fairy_soul(117, FL + 1, -154)

B.arrival = (CX, FL + 1, CZ + 10)

DARK = [("stone", 6), ("andesite", 2), ("tuff", 2), ("deepslate", 1), ("cobbled_deepslate", 1)]


def rock_block(x, y, z):
    return pick(x, y, z, DARK, 15)


ORES = [("gold_ore", 3), ("deepslate_gold_ore", 3), ("emerald_ore", 1), ("deepslate_emerald_ore", 1), ("diamond_ore", 1),
        ("deepslate_diamond_ore", 1), ("redstone_ore", 2), ("deepslate_redstone_ore", 2)]
B.finish(rock_block, ores=ORES, ore_chance=0.05, spots={"jade_pedestal": [CX, FL + 3, CZ]}, tunnel_ends=TUNNEL_ENDS)
