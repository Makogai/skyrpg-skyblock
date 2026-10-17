#!/usr/bin/env python3
"""The Lost Precursor City (Crystal Hollows, Precursor Remnants), in a rock-filled box (x 100..190, y 80..150,
z 100..190): a huge cavern holding an ancient robot city. Two crossing avenues, a round plaza with the central tower
(Professor Robot in the lab at its foot, six empty part slots), twelve tall cracked stone-brick / polished andesite /
copper buildings (half of them ruined, with broken machinery), four plaza sculptures, street lamps, a maintenance bay
with the fairy soul, and a tunnel out of every face along the avenues.

    python tools/builds/hub_areas/hollows_precursor_city.py
"""
import math

from hollows_lib import Builder, sq, hash01, noise

B = Builder("hollows_precursor_city", (100, 80, 100), (90, 70, 90))
put, fill, carve, pick, column = B.put, B.fill, B.carve, B.pick, B.column

CX = CZ = 145
FL = 90                                           # street floor block y (players stand at 91)
BRICK = [("stone_bricks", 4), ("cracked_stone_bricks", 3), ("polished_andesite", 3)]
STREET = [("stone_bricks", 5), ("cracked_stone_bricks", 2), ("polished_andesite", 4)]
COPPER = [("cut_copper", 2), ("exposed_cut_copper", 2), ("weathered_cut_copper", 3), ("oxidized_cut_copper", 4)]

# ============================================================ the cavern and its floor
cav = set()
for x in range(CX - 41, CX + 42):
    for z in range(CZ - 41, CZ + 42):
        dx, dz = x - CX, z - CZ
        u = (abs(dx) / 41) ** 4 + (abs(dz) / 41) ** 4 + 0.05 * noise(x, z, 4, 1.4)
        if u < 1:
            carve(x, FL + 1, z, x, FL + 18 + int(34 * sq(1 - u)), z)
            cav.add((x, z))
            r = math.hypot(dx, dz)
            ax, az = abs(dx), abs(dz)
            if r <= 8.6:
                b = "polished_andesite"
            elif r <= 9.6:
                b = "oxidized_cut_copper"
            elif r <= 14:
                b = "stone_bricks" if (x // 2 + z // 2) % 2 else "polished_andesite"
            elif az <= 4 or ax <= 4:
                if az in (4,) or ax in (4,):
                    b = "polished_andesite"
                elif (az == 0 and az <= ax and (x % 8) < 2) or (ax == 0 and (z % 8) < 2):
                    b = "oxidized_cut_copper"
                else:
                    b = pick(x, FL, z, STREET, 2)
            else:
                b = pick(x, FL, z, BRICK, 3)
            put(x, FL, z, b)

# ============================================================ the central tower
TOWER_TOP = 130
SLOT_ANGLES = [math.radians(a) for a in range(0, 360, 60)]
for dx in range(-8, 9):
    for dz in range(-8, 9):
        r = math.hypot(dx, dz)
        if r > 7.6:
            continue
        x, z = CX + dx, CZ + dz
        ang = math.atan2(dz, dx)
        sector = int(((ang + math.pi) / (2 * math.pi)) * 12) % 12
        near_rib = abs(((ang + math.pi) / (2 * math.pi) * 12) % 1 - 0.5) > 0.38
        if r < 5.6:                                  # the lab
            ring = "oxidized_cut_copper" if r < 1.9 else "polished_andesite"
            if 2.5 < r < 3.6:
                ring = "cut_copper"
            elif 3.6 <= r < 4.6:
                ring = "stone_bricks" if (x + z) % 2 else "polished_andesite"
            elif r >= 4.6:
                ring = "weathered_cut_copper" if (x + z) % 2 else "polished_andesite"
            put(x, FL, z, ring)
            for y in range(FL + 9, TOWER_TOP - 1):
                put(x, y, z, "polished_andesite" if y % 6 else "weathered_cut_copper")
            continue
        for y in range(FL + 1, TOWER_TOP + 1):
            k = y - FL
            if r < 6.6 and k <= 8:                    # inner face of the lab wall
                b = "polished_andesite" if sector % 2 == 0 else "weathered_cut_copper"
                if k in (4, 5) and sector % 2 == 1:
                    b = "redstone_lamp[lit=true]"
            elif k % 8 == 0:
                b = "oxidized_cut_copper" if (x + z) % 3 else "iron_block"
            elif near_rib:
                b = pick(x, y, z, COPPER, 5)
            elif k % 8 in (3, 4, 5) and sector % 2 == 1 and k > 8:
                b = "tinted_glass" if hash01(x, y, z, 6) < 0.75 else "air"
            else:
                b = pick(x, y, z, BRICK, 7)
            put(x, y, z, b)
# the lab ceiling lamps, doors north and south
for a in range(0, 360, 60):
    put(CX + round(2.0 * math.cos(math.radians(a + 30))), FL + 9, CZ + round(2.0 * math.sin(math.radians(a + 30))), "sea_lantern")
for ang in range(0, 360, 45):
    put(CX + round(4.6 * math.cos(math.radians(ang))), FL + 9, CZ + round(4.6 * math.sin(math.radians(ang))), "redstone_lamp[lit=true]")
for sz in (-1, 1):
    for dx in range(-2, 3):
        for dz in range(5, 9):
            x, z = CX + dx, CZ + sz * dz
            for y in range(FL + 1, FL + 6):
                put(x, y, z, "air")
            put(x, FL + 6, z, "oxidized_cut_copper" if abs(dx) < 2 else "chiseled_stone_bricks")
    for dz in range(5, 8):
        for dx in (-3, 3):
            for y in range(FL + 1, FL + 7):
                put(CX + dx, y, CZ + sz * dz, "oxidized_cut_copper" if y % 2 else "polished_andesite")
    put(CX, FL + 7, CZ + sz * 7, "redstone_lamp[lit=true]")
# the spire
for y in range(TOWER_TOP + 1, TOWER_TOP + 3):
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                put(CX + dx, y, CZ + dz, "oxidized_cut_copper")
for y in range(TOWER_TOP + 3, TOWER_TOP + 8):
    put(CX, y, CZ, "lightning_rod[facing=up]" if y > TOWER_TOP + 3 else "sea_lantern")
for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    put(CX + dx, TOWER_TOP + 3, CZ + dz, "lightning_rod[facing=up]")
# Professor Robot's dais and the six part slots
for dx in range(-1, 2):
    for dz in range(-1, 2):
        put(CX + dx, FL + 1, CZ + dz, "chiseled_copper" if dx == 0 and dz == 0 else "oxidized_cut_copper")
SLOTS = []
for a in SLOT_ANGLES:
    sx, sz = CX + round(4.0 * math.cos(a)), CZ + round(4.0 * math.sin(a))
    put(sx, FL + 1, sz, "polished_andesite")
    put(sx, FL + 2, sz, "chiseled_copper")
    put(sx, FL + 3, sz, "copper_bulb[lit=false]")
    SLOTS.append([sx, FL + 3, sz])
B.npc("Professor Robot", CX, FL + 2, CZ, 0.0, "quest: Professor Robot, six empty part slots around him")
for dx, dz in ((-3, 0), (3, 0)):                     # consoles
    put(CX + dx, FL + 1, CZ + dz, "observer[facing=up,powered=false]")
    put(CX + dx, FL + 2, CZ + dz, "lightning_rod[facing=up]")

# ============================================================ gates at the four tunnel mouths
for (axis, sgn) in (("x", -1), ("x", 1), ("z", -1), ("z", 1)):
    for l in (-4, -3, 3, 4):
        for t in (37, 38):
            x, z = (CX + sgn * t, CZ + l) if axis == "x" else (CX + l, CZ + sgn * t)
            for y in range(FL + 1, FL + 9):
                put(x, y, z, "oxidized_cut_copper" if (y - FL) % 3 == 0 else "polished_andesite")
    for l in range(-4, 5):
        for t in (37, 38):
            x, z = (CX + sgn * t, CZ + l) if axis == "x" else (CX + l, CZ + sgn * t)
            put(x, FL + 7, z, "chiseled_stone_bricks")
            put(x, FL + 8, z, "oxidized_cut_copper")
    x, z = (CX + sgn * 37, CZ) if axis == "x" else (CX, CZ + sgn * 37)
    put(x, FL + 7, z, "redstone_lamp[lit=true]")
B.tunnel("x", CZ, FL, 100, 108, floor=STREET)
B.tunnel("x", CZ, FL, 182, 189, floor=STREET)
B.tunnel("z", CX, FL, 100, 108, floor=STREET)
B.tunnel("z", CX, FL, 182, 189, floor=STREET)

# ============================================================ buildings
RANGES = ((5, 17), (21, 33))
FAIRY = None


def building(x0, z0, h, seed, ruined, door_axis, bay=False):
    """A 13 x 13 building with its corner at (x0, z0), `h` tall. door_axis: 'x' or 'z' = the side facing the middle."""
    W = 13
    hc = {}
    for xi in range(W):
        for zi in range(W):
            if ruined:
                jag = int((noise(x0 + xi, z0 + zi, seed, 1.9) * 0.5 + 0.5) * min(h * 0.5, 12))
                jag += 4 if hash01(x0 + xi, 1, z0 + zi, seed) < 0.12 else 0
                hc[(xi, zi)] = max(6, h - jag)
            else:
                hc[(xi, zi)] = h
    for xi in range(W):
        for zi in range(W):
            x, z = x0 + xi, z0 + zi
            ex, ez = xi in (0, W - 1), zi in (0, W - 1)
            edge = ex or ez
            along = zi if ex else xi
            top = hc[(xi, zi)]
            for k in range(1, top + 1):
                y = FL + k
                if not edge:
                    b = "deepslate_bricks"
                elif ex and ez:
                    b = pick(x, y, z, COPPER, seed) if k > 3 else "polished_andesite"
                elif k <= 3:
                    b = "polished_andesite" if hash01(x, y, z, seed) < 0.7 else "stone_bricks"
                elif k % 7 == 6:
                    b = "weathered_cut_copper" if along % 4 else "iron_block"
                elif k % 7 in (2, 3, 4) and along % 4 == 2:
                    b = "tinted_glass" if hash01(x, y, z, seed + 1) < (0.55 if ruined else 0.9) else "iron_bars"
                elif k == 1 and along % 6 == 3:
                    b = "redstone_lamp[lit=true]"
                else:
                    b = pick(x, y, z, BRICK, seed)
                put(x, y, z, b)
            ty = FL + top
            if ruined:
                if edge and hash01(x, 3, z, seed + 2) < 0.30:
                    put(x, ty + 1, z, "iron_bars")
                elif hash01(x, 4, z, seed + 3) < 0.07:
                    put(x, ty + 1, z, "piston[facing=up,extended=false]")
                elif hash01(x, 5, z, seed + 4) < 0.05:
                    put(x, ty + 1, z, "observer[facing=north,powered=false]")
                put(x, ty, z, pick(x, ty, z, [("cracked_stone_bricks", 3), ("polished_andesite", 2), ("deepslate_bricks", 2)], seed))
            else:
                put(x, ty, z, "polished_andesite" if (xi + zi) % 2 else "stone_bricks")
                if edge:
                    put(x, ty + 1, z, "iron_bars" if not (ex and ez) else "oxidized_cut_copper")
    if not ruined:                                   # roof machinery
        for dx, dz in ((4, 4), (8, 8), (4, 8), (8, 4)):
            put(x0 + dx, FL + h + 1, z0 + dz, "iron_block")
            put(x0 + dx, FL + h + 2, z0 + dz, "copper_block")
            put(x0 + dx, FL + h + 3, z0 + dz, "lightning_rod[facing=up]")
        put(x0 + 6, FL + h + 1, z0 + 6, "sea_lantern")
    # the door on the side that faces the middle
    mid = 6
    cx_ = x0 + (0 if x0 > CX else W - 1) if door_axis == "x" else None
    cz_ = z0 + (0 if z0 > CZ else W - 1) if door_axis == "z" else None
    for dd in (-1, 0, 1):
        for depth in (0, 1):
            for y in range(FL + 1, FL + 5):
                if door_axis == "x":
                    put(cx_ + (depth if x0 > CX else -depth), y, z0 + mid + dd, "air")
                else:
                    put(x0 + mid + dd, y, cz_ + (depth if z0 > CZ else -depth), "air")
        if door_axis == "x":
            put(cx_, FL + 5, z0 + mid + dd, "chiseled_stone_bricks")
        else:
            put(x0 + mid + dd, FL + 5, cz_, "chiseled_stone_bricks")
    for dd in (-2, 2):
        for y in range(FL + 1, FL + 6):
            if door_axis == "x":
                put(cx_, y, z0 + mid + dd, "oxidized_cut_copper")
            else:
                put(x0 + mid + dd, y, cz_, "oxidized_cut_copper")
    if door_axis == "x":
        put(cx_, FL + 6, z0 + mid, "redstone_lamp[lit=true]")
    else:
        put(x0 + mid, FL + 6, cz_, "redstone_lamp[lit=true]")
    if bay:                                          # the maintenance bay: a room behind the door
        global FAIRY
        for u in range(2, 11):
            for v in range(2, 11):
                for y in range(FL + 1, FL + 6):
                    if door_axis == "x":
                        put(x0 + (u if x0 > CX else W - 1 - u), y, z0 + v, "air")
                    else:
                        put(x0 + v, y, z0 + (u if z0 > CZ else W - 1 - u), "air")
        for u in (3, 5, 7, 9):
            for v in (3, 9):
                if door_axis == "x":
                    put(x0 + (u if x0 > CX else W - 1 - u), FL + 6, z0 + v, "redstone_lamp[lit=true]")
                else:
                    put(x0 + v, FL + 6, z0 + (u if z0 > CZ else W - 1 - u), "redstone_lamp[lit=true]")
        junk = [(3, 2, "piston[facing=up,extended=false]"), (4, 2, "iron_block"), (5, 2, "copper_block"), (3, 10, "observer[facing=north,powered=false]"),
                (4, 10, "iron_block"), (9, 2, "iron_block"), (10, 2, "piston[facing=up,extended=false]"), (9, 10, "copper_bulb[lit=true]"),
                (10, 10, "iron_block"), (10, 6, "iron_block"), (10, 5, "copper_block"), (10, 7, "observer[facing=north,powered=false]")]
        for u, v, blk in junk:
            if door_axis == "x":
                put(x0 + (u if x0 > CX else W - 1 - u), FL + 1, z0 + v, blk)
            else:
                put(x0 + v, FL + 1, z0 + (u if z0 > CZ else W - 1 - u), blk)
        u, v = 7, 6
        if door_axis == "x":
            FAIRY = (x0 + (u if x0 > CX else W - 1 - u), FL + 1, z0 + v)
        else:
            FAIRY = (x0 + v, FL + 1, z0 + (u if z0 > CZ else W - 1 - u))
        B.fairy_soul(*FAIRY)


n_build = 0
for sx in (-1, 1):
    for sz in (-1, 1):
        for (a, b) in ((0, 1), (1, 0), (1, 1)):
            xr, zr = RANGES[a], RANGES[b]
            x0 = CX + xr[0] if sx > 0 else CX - xr[1]
            z0 = CZ + zr[0] if sz > 0 else CZ - zr[1]
            seed = 11 + n_build * 7
            ceil = min(B.top_air(x0 + i, z0 + j) or 100 for i in (0, 12) for j in (0, 12))
            h = 15 + int(hash01(x0, 0, z0, seed) * 17) + (4 if (a, b) == (1, 1) else 0)
            h = min(h, ceil - FL - 3)
            ruined = hash01(x0, 2, z0, seed) < 0.55
            bay = (sx, sz, a, b) == (-1, 1, 1, 1)
            axis = "z" if (a, b) == (1, 0) else "x"
            if bay:
                ruined, h = False, max(h, 18)
            building(x0, z0, h, seed, ruined, axis, bay)
            n_build += 1

# ============================================================ plaza sculptures (the four inner lots)
for sx in (-1, 1):
    for sz in (-1, 1):
        gx, gz = CX + sx * 11, CZ + sz * 11
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                put(gx + dx, FL + 1, gz + dz, "polished_andesite" if max(abs(dx), abs(dz)) == 2 else "stone_bricks")
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                put(gx + dx, FL + 2, gz + dz, "iron_block")
        for y in range(FL + 3, FL + 7):
            put(gx, y, gz, "oxidized_copper")
        put(gx, FL + 7, gz, "sea_lantern")
        put(gx, FL + 8, gz, "lightning_rod[facing=up]")
        for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            put(gx + dx, FL + 2, gz + dz, "piston[facing=up,extended=false]" if (dx + dz + sx) % 4 else "copper_bulb[lit=true]")
        for dx, dz in ((-2, 0), (2, 0)):
            put(gx + dx, FL + 2, gz + dz, "observer[facing=east,powered=false]" if dx > 0 else "observer[facing=west,powered=false]")

# ============================================================ street lamps along the avenues
for k in (10, 22, 34):
    for sgn in (-1, 1):
        for l in (-4, 4):
            for (px, pz) in ((CX + sgn * k, CZ + l), (CX + l, CZ + sgn * k)):
                if (px, pz) not in cav:
                    continue
                put(px, FL + 1, pz, "polished_andesite")
                for y in range(FL + 2, FL + 6):
                    put(px, y, pz, "iron_bars")
                put(px, FL + 6, pz, "sea_lantern")
                put(px, FL + 7, pz, "lightning_rod[facing=up]")
# lanterns hanging from the high cavern ceiling over the plaza and avenues
for dx, dz in ((0, 0), (12, 0), (-12, 0), (0, 12), (0, -12), (26, 0), (-26, 0), (0, 26), (0, -26)):
    if (dx, dz) != (0, 0):
        B.chain_lamp(CX + dx, CZ + dz, FL + 16)

B.arrival = (CX, FL + 1, CZ + 18)
GRAY = [("stone", 6), ("andesite", 2), ("tuff", 2), ("cobbled_deepslate", 1)]
B.finish(lambda x, y, z: B.pick(x, y, z, GRAY, 15), ores=[("iron_ore", 2), ("copper_ore", 3), ("deepslate_iron_ore", 1)], ore_chance=0.03,
         spots={"robot": [CX, FL + 2, CZ], "robot_slots": SLOTS},
         tunnel_ends=(("tunnel W", (100, FL + 1, CZ)), ("tunnel E", (189, FL + 1, CZ)), ("tunnel N", (CX, FL + 1, 100)),
                      ("tunnel S", (CX, FL + 1, 189))))
