#!/usr/bin/env python3
"""The Crimson Isle, centre region (x -100..99): the arrival from the Spider's Den, the Crimson Fields, the Ruins of
Ashfang, the Stronghold, the Blazing Volcano and the Magma Chamber under it.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python crimson_center.py
"""
import json
import math
import re

from kit import ZONES
from crimson_lib import (new_area, land, bridge_half, sign, mob, hash01, noise, BRIDGE, ARRIVAL, SPIDERS_DEN_BACK)

area = new_area("center")
S, F = area.set, area.fill

SG = 64                              # the Stronghold's / ruins' ground (surface block y)
VCX, VCZ, VR = 0, -165, 72           # the volcano's centre and base radius
RIM_R, LAKE_R = 16, 10.2             # crater rim radius, lava lake radius
LAKE_FLOOR, LAVA_TOP = 138, 142
BRZ = (-80, 110)                     # bridge centre lines
CF = 63                              # Magma Chamber floor (players stand at 64)


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def rectw(x, z, x1, x2, z1, z2, fade):
    d = math.hypot(max(x1 - x, 0, x - x2), max(z1 - z, 0, z - z2))
    return 0.0 if d >= fade else smooth(1 - d / fade)


def volc_R(ang):
    return VR * (1 + 0.04 * math.sin(3 * ang + 0.8) + 0.03 * math.sin(5 * ang + 2.0))


def vol_params(x, z):
    dx, dz = x - VCX, z - VCZ
    return math.hypot(dx, dz), volc_R(math.atan2(dz, dx)), math.atan2(dz, dx)


def bridge_w(x, z):
    best = 0.0
    for c in BRZ:
        d = abs(z - c)
        wz = 1.0 if d <= 5 else smooth((12 - d) / 7)
        for s in (x + 93, 92 - x):
            wx = 1.0 if s <= 9 else smooth((28 - s) / 19)
            best = max(best, wz * wx)
    return best


def edge(x, z):
    d = min(abs(z - c) for c in BRZ)
    tongue = 14 * max(0.0, min(1.0, (11 - d) / 3))
    ex = min(x + 94, 93 - x) + tongue
    ez = min(226 - z, z + 247)
    wob = 2.5 * (noise(x, z, 31, 1.6) + 1)
    return min(ex, ez) - wob


def height(x, z, e):
    n = noise(x, z, 11, 1.0) * 0.55 + noise(x, z, 12, 2.4) * 0.45
    h = 63 + 3.0 * n
    r, R, ang = vol_params(x, z)
    if r < R:
        if r <= LAKE_R:
            return LAKE_FLOOR
        if r < RIM_R:
            return int(145 + (r - LAKE_R) * 0.85)
        t = (R - r) / (R - RIM_R)
        h = (63 + 0.6 * n + 87 * min(1.0, t) ** 1.25 + 2.5 * noise(x, z, 41, 1.8) * min(1, t * 4) * (1 if t < 1 else 0)
             + 2.2 * math.sin(7 * ang + 1.1) * min(1, t * 2))
    for rect, y in (((-58, 58, -68, 38, 14), SG), ((44, 80, 42, 98, 10), SG), ((-22, 22, 190, 212, 8), 66),
                    ((-14, 14, -106, -92, 9), 63)):
        w = rectw(x, z, *rect[:4], rect[4])
        if w:
            h += (y - h) * w
    wb = bridge_w(x, z)
    if wb:
        h += (66 - h) * wb
    if e < 14:
        h = min(h, 60 + 0.75 * e)
    return max(60, int(round(h)))


# ---------------------------------------------------------------- terrain heightfield
H = {}
for x in range(-93, 93):
    for z in range(-246, 227):
        e = edge(x, z)
        if e > 0:
            H[(x, z)] = height(x, z, e)

STREAKS = [(0.35 + k * math.tau / 6) for k in range(6)]


def volc_surface(x, z, top, r, R, ang):
    s = max(abs(H.get((x + 1, z), top) - H.get((x - 1, z), top)), abs(H.get((x, z + 1), top) - H.get((x, z - 1), top)))
    h = hash01(x, z, 9, 7)
    if r < RIM_R:
        if r <= LAKE_R:
            return "polished_basalt"
        if r < 12.5:
            return "magma_block" if h < 0.6 else "blackstone"
        return "blackstone" if h < 0.5 else "basalt" if h < 0.8 else "smooth_basalt"
    if r < R and 18 < r < 64:
        w = 1.9 * (1 - (r - 17) / 55)
        for k, a0 in enumerate(STREAKS):
            a = a0 + 0.22 * math.sin(r * 0.11 + k * 1.7)
            da = abs((ang - a + math.pi) % math.tau - math.pi) * r
            if da < w:
                return "shroomlight" if h > 0.95 else "magma_block"
    if r < R + 1:
        if s >= 2.5:
            return ("basalt" if h < 0.3 else "blackstone" if h < 0.55 else "tuff" if h < 0.75 else "smooth_basalt" if h < 0.9
                    else "polished_blackstone")
        return ("polished_basalt" if h < 0.2 else "blackstone" if h < 0.5 else "basalt" if h < 0.7 else "tuff" if h < 0.85
                else "smooth_basalt")
    return ("blackstone" if h < 0.35 else "basalt" if h < 0.65 else "smooth_basalt" if h < 0.75 else "tuff" if h < 0.85
            else "polished_blackstone" if h < 0.97 else "magma_block")


def field_surface(x, z, top):
    h = hash01(x, z, 9, 3)
    n1 = noise(x, z, 21, 2.0)
    n2 = noise(x, z, 22, 3.1)
    if n2 > 0.55:
        return "nether_wart_block" if h < 0.7 else "crimson_nylium"
    if n1 < -0.3:
        return ("netherrack" if h < 0.7 else "blackstone" if h < 0.85 else "crimson_nylium" if h < 0.98 else "magma_block")
    return "crimson_nylium" if h < 0.86 else "netherrack" if h < 0.97 else "magma_block"


def body_field(x, y, z):
    return "netherrack" if hash01(x, y, z, 5) < 0.85 else "blackstone"


def body_vol(x, y, z):
    h = hash01(x, y, z, 5)
    return "blackstone" if h < 0.5 else "basalt" if h < 0.75 else "tuff" if h < 0.9 else "smooth_basalt"


for (x, z), top in H.items():
    r, R, ang = vol_params(x, z)
    if r < R + 14:
        land(area, x, z, top, volc_surface(x, z, top, r, R, ang), "blackstone", body_vol)
    elif z < -62 and noise(x, z, 51, 1.3) > -0.25:
        land(area, x, z, top, volc_surface(x, z, top, r, R, ang), "blackstone", body_vol)
    else:
        sf = field_surface(x, z, top)
        land(area, x, z, top, sf, "netherrack", body_field)

# the lava lake: enclosed (floor at y 138, walls from y 145 up, lava top at 142)
for x in range(VCX - 11, VCX + 12):
    for z in range(VCZ - 11, VCZ + 12):
        if math.hypot(x - VCX, z - VCZ) <= LAKE_R:
            for y in range(LAKE_FLOOR + 1, LAVA_TOP + 1):
                S(x, y, z, "lava")


def surf(x, z):
    return H.get((x, z))


def flat_patch(cx, cz, rad, y0, block, under="blackstone"):
    """Level a disc to y0 (cut above, fill below)."""
    for x in range(cx - rad, cx + rad + 1):
        for z in range(cz - rad, cz + rad + 1):
            if (x, z) not in H or math.hypot(x - cx, z - cz) > rad + 0.3:
                continue
            old = H[(x, z)]
            for y in range(y0 + 1, old + 6):
                S(x, y, z, "air")
            for y in range(min(old, y0), y0):
                S(x, y, z, under)
            S(x, y0, z, block)
            H[(x, z)] = y0
            area.top[(x, z)] = y0


# ---------------------------------------------------------------- the Magma Chamber (inside the volcano, y >= 63)
floor_pick = area.mix({"polished_blackstone_bricks": 4, "blackstone": 3, "polished_basalt": 2, "basalt": 2,
                       "cracked_polished_blackstone_bricks": 1})


def carve_dome(cx, cz, rad, floor, hmax, power=4):
    for x in range(cx - rad - 1, cx + rad + 2):
        for z in range(cz - rad - 1, cz + rad + 2):
            r = math.hypot(x - cx, z - cz)
            if r > rad:
                continue
            c = int(hmax * math.sqrt(max(0.0, 1 - (r / rad) ** power))) + (1 if r < rad - 2 else 0)
            S(x, floor, z, floor_pick(x, floor, z))
            for y in range(floor + 1, floor + 1 + c):
                S(x, y, z, "air")


def carve_passage(a, b, floor, rad=3.0, height=6):
    (x1, z1), (x2, z2) = a, b
    steps = int(max(abs(x2 - x1), abs(z2 - z1))) * 2
    cells = set()
    for i in range(steps + 1):
        px, pz = x1 + (x2 - x1) * i / steps, z1 + (z2 - z1) * i / steps
        for dx in range(-int(rad) - 1, int(rad) + 2):
            for dz in range(-int(rad) - 1, int(rad) + 2):
                d = math.hypot(dx, dz)
                if d <= rad + 0.2:
                    cells.add((int(round(px)) + dx, int(round(pz)) + dz, d))
    for x, z, d in cells:
        S(x, floor, z, floor_pick(x, floor, z))
        for y in range(floor + 1, floor + 1 + height - int(d * 0.8)):
            S(x, y, z, "air")


CCX, CCZ, CR = 0, -170, 24
carve_dome(CCX, CCZ, CR, CF, 22)
# side caves: north, south-west, south-east
carve_passage((0, -192), (0, -204), CF, 3.2, 7)
carve_dome(0, -212, 10, CF, 10, 3)
carve_passage((-20, -158), (-27, -153), CF, 2.8, 6)
carve_dome(-31, -150, 9, CF, 9, 3)
carve_passage((20, -158), (27, -153), CF, 2.8, 6)
carve_dome(31, -150, 9, CF, 9, 3)
# the south tunnel, from the cave to the volcano's south foot
TUN_Z0, TUN_Z1 = -147, -97
for z in range(TUN_Z0, TUN_Z1 + 1):
    for dx in range(-5, 6):
        S(dx, CF, z, "polished_blackstone_bricks" if abs(dx) < 2 and z % 4 else floor_pick(dx, CF, z))
        for y in range(CF + 1, CF + 7 + (5 - abs(dx)) // 2 + 1):
            S(dx, y, z, "air")
    if z % 6 == 0:
        for dx in (-6, 6):
            S(dx, CF + 4, z, "shroomlight")
            S(dx, CF + 3, z, "polished_blackstone_bricks")
            S(dx, CF + 5, z, "polished_blackstone_bricks")
# make sure the tunnel is open at its south end (the foot is low there)
for z in range(-100, -88):
    for dx in range(-5, 6):
        S(dx, CF, z, "polished_blackstone_bricks" if abs(dx) < 2 else "blackstone")
        for y in range(CF + 1, CF + 9):
            S(dx, y, z, "air")
        H[(dx, z)] = CF
        area.top[(dx, z)] = CF

# the lava pool (r 6.5, lava y 61-62, floor around at 63): enclosed by solid cells all around and below
POOL = (CCX, CCZ + 2)
for x in range(POOL[0] - 10, POOL[0] + 11):
    for z in range(POOL[1] - 10, POOL[1] + 11):
        d = math.hypot(x - POOL[0], z - POOL[1])
        if d <= 6.5:
            S(x, 60, z, "blackstone")
            S(x, 61, z, "lava")
            S(x, 62, z, "lava")
            S(x, CF, z, "air")
        elif d <= 8.6:
            S(x, CF, z, "polished_blackstone_bricks" if d > 7.5 else "magma_block")
            if d > 7.5:
                S(x, CF + 1, z, "polished_blackstone_brick_slab[type=bottom]")
# rim: magma ring is at d 6.5..7.5 (fake glow), slab wall outside it
for x in range(POOL[0] - 10, POOL[0] + 11):
    for z in range(POOL[1] - 10, POOL[1] + 11):
        d = math.hypot(x - POOL[0], z - POOL[1])
        if 8.6 < d <= 10.5 and (x + z) % 3 == 0:
            S(x, CF, z, "chiseled_polished_blackstone")
# pillars, lights, stalagmites in the chamber
for k in range(10):
    a = k / 10 * math.tau + 0.3
    x, z = int(round(CCX + math.cos(a) * 17)), int(round(CCZ + math.sin(a) * 17))
    r = 17
    ceil_y = CF + int(22 * math.sqrt(1 - (r / CR) ** 4)) + 1
    for y in range(CF + 1, ceil_y + 1):
        S(x, y, z, "polished_basalt" if y % 4 else "chiseled_polished_blackstone")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, CF + 1, z + dz, "polished_blackstone_brick_stairs[facing=%s,half=bottom]" % {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}[(dx, dz)])
    S(x, CF + 7, z + 1, "air")
for k in range(36):
    a = k / 36 * math.tau
    x, z = int(round(CCX + math.cos(a) * 22.5)), int(round(CCZ + math.sin(a) * 22.5))
    for y in (CF + 3, CF + 4):
        S(x, y, z, "shroomlight" if k % 3 == 0 else "magma_block")
for k in range(24):
    a = hash01(k, 1, 0, 61) * math.tau
    rr = 9 + hash01(k, 2, 0, 61) * 12
    x, z = int(round(CCX + math.cos(a) * rr)), int(round(CCZ + math.sin(a) * rr))
    if hash01(k, 3, 0, 61) < 0.6 and math.hypot(x - POOL[0], z - POOL[1]) > 11:
        for y in range(CF + 1, CF + 2 + int(hash01(k, 4, 0, 61) * 3)):
            S(x, y, z, "basalt[axis=y]")
# the side caves: dark and lit
for (cx, cz) in ((0, -212), (-31, -150), (31, -150)):
    for k in range(6):
        a = k / 6 * math.tau
        x, z = int(round(cx + math.cos(a) * 7)), int(round(cz + math.sin(a) * 7))
        S(x, CF + 1, z, "polished_blackstone_wall")
        S(x, CF + 2, z, "soul_lantern[hanging=false]")
    S(cx, CF + 1, cz, "polished_blackstone_bricks")
    S(cx, CF + 2, cz, "chiseled_polished_blackstone")
    S(cx, CF + 3, cz, "soul_campfire[lit=true]") if False else None

# the tunnel's mouth: a gate of polished blackstone
MZ0 = -101
for dx in (-8, -7, 7, 8):
    for y in range(CF + 1, CF + 13):
        S(dx, y, MZ0, "polished_blackstone_bricks" if y % 4 else "chiseled_polished_blackstone")
        S(dx, y, MZ0 + 1, "polished_blackstone_bricks")
for dx in range(-8, 9):
    for y in (CF + 11, CF + 12):
        S(dx, y, MZ0, "polished_blackstone_bricks")
        S(dx, y, MZ0 + 1, "polished_blackstone_bricks")
    S(dx, CF + 13, MZ0, "polished_blackstone_brick_slab[type=bottom]")
for dx in range(-6, 7):
    if abs(dx) > 5 or dx % 2:
        continue
S(-8, CF + 13, MZ0 - 1, "lantern[hanging=false]")
S(8, CF + 13, MZ0 - 1, "lantern[hanging=false]")
for dx in (-8, 8):
    S(dx, CF + 13, MZ0, "polished_blackstone_wall")
    S(dx, CF + 14, MZ0, "lantern[hanging=false]")
for y in range(CF + 9, CF + 11):
    for dx in range(-4, 5):
        S(dx, y, MZ0 + 1, "iron_bars") if dx % 2 == 0 else None
sign(area, 9, CF + 1, MZ0 + 3, 0, ["Magma Chamber", "Beware the boss"], wood="crimson")
sign(area, -9, CF + 1, MZ0 + 3, 0, ["Blazing Volcano", "Kada Knights below"], wood="crimson")

# ---------------------------------------------------------------- roads
road_cells = {}
road_mat = area.mix({"blackstone": 4, "polished_blackstone": 3, "cracked_polished_blackstone_bricks": 2, "basalt": 1,
                     "polished_blackstone_bricks": 2})


def road(pts, width=5, mat=road_mat):
    cells = set()
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        steps = int(max(abs(x2 - x1), abs(z2 - z1), 1) * 2)
        for i in range(steps + 1):
            px, pz = x1 + (x2 - x1) * i / steps, z1 + (z2 - z1) * i / steps
            hw = width / 2
            for dx in range(-int(hw) - 1, int(hw) + 2):
                for dz in range(-int(hw) - 1, int(hw) + 2):
                    if dx * dx + dz * dz <= hw * hw + 0.3:
                        cells.add((int(round(px)) + dx, int(round(pz)) + dz))
    for c in cells:
        if c not in H or c in road_cells:
            continue
        x, z = c
        y = H[c]
        if y == CF and abs(x) < 6 and -148 < z < -88:
            continue
        S(x, y, z, mat(x, y, z))
        road_cells[c] = y
    return cells


def wiggle(pts, amp=3.0):
    out = []
    for i, (x, z) in enumerate(pts):
        out.append((x + int(amp * math.sin(i * 1.7 + x * 0.1)) if 0 < i < len(pts) - 1 else x, z))
    return out


SPINE = [(0, 214), (2, 190), (-6, 162), (0, 134), (4, 104), (0, 72), (0, 44), (0, 29)]
R_ALL = []
R_ALL.append(road(SPINE, 5))
R_ALL.append(road([(0, 110), (-30, 113), (-60, 108), (-93, 110)], 5))
R_ALL.append(road([(0, 110), (30, 113), (60, 108), (92, 110)], 5))
R_ALL.append(road([(3, 86), (28, 80), (46, 72)], 4))
R_ALL.append(road([(-93, -80), (-60, -83), (-30, -80), (0, -80), (30, -80), (60, -83), (92, -80)], 5))
R_ALL.append(road([(0, -56), (0, -80), (0, -100)], 5))
R_ALL.append(road([(0, -80), (-30, -106), (-47, -116)], 4))     # to the foot of the volcano path
R_ALL.append(road([(-52, -80), (-52, 28), (-52, 110)], 3))
R_ALL.append(road([(52, -80), (52, 28), (52, 110)], 3))

# lamp posts along the roads
post_n = 0
for pts, w in (([(0, 214), (2, 190), (-6, 162), (0, 134), (4, 104), (0, 72), (0, 44)], 5),
               ([(-93, -80), (-30, -80), (0, -80), (30, -80), (92, -80)], 5),
               ([(-93, 110), (-60, 108), (-30, 113), (0, 110), (30, 113), (60, 108), (92, 110)], 5)):
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = int(math.hypot(x2 - x1, z2 - z1) // 14)
        for i in range(1, n + 1):
            px, pz = x1 + (x2 - x1) * i / (n + 1), z1 + (z2 - z1) * i / (n + 1)
            side = 1 if i % 2 else -1
            if abs(x2 - x1) > abs(z2 - z1):
                x, z = int(px), int(pz) + side * (w // 2 + 1)
            else:
                x, z = int(px) + side * (w // 2 + 1), int(pz)
            if (x, z) in H and (x, z) not in road_cells and not (-24 <= x <= 24 and 190 <= z <= 216):
                y = H[(x, z)]
                S(x, y + 1, z, "polished_blackstone_wall")
                S(x, y + 2, z, "polished_blackstone_wall")
                S(x, y + 3, z, "lantern[hanging=false]" if post_n % 3 else "soul_lantern[hanging=false]")
                post_n += 1

road_near = set()
for (x, z) in road_cells:
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            road_near.add((x + dx, z + dz))

# ---------------------------------------------------------------- the volcano path (a spiral from the SW foot to the rim)
path_cells = {}
TURN = math.radians(450)
TH0 = math.radians(125)
Y_START, Y_END = 64, 148
path_pts = []
NS = 4200
for i in range(NS + 1):
    u = i / NS
    yp = Y_START + (Y_END - Y_START) * u
    ang = TH0 + u * TURN
    R = volc_R(ang)
    t = min(1.0, max(0.0, (yp - 63) / 87)) ** 0.8
    rp = R - (R - RIM_R) * t
    if u > 0.97:
        rp = max(rp, 17.5)
    px, pz = VCX + rp * math.cos(ang), VCZ + rp * math.sin(ang)
    path_pts.append((px, pz, int(round(yp))))
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if dx * dx + dz * dz <= 6.3:
                c = (int(round(px)) + dx, int(round(pz)) + dz)
                if c not in path_cells:
                    path_cells[c] = int(round(yp))
path_mat = area.mix({"polished_blackstone_bricks": 4, "cracked_polished_blackstone_bricks": 2, "polished_blackstone": 2,
                     "blackstone": 1})
for (x, z), yb in path_cells.items():
    if (x, z) not in H:
        continue
    old = H[(x, z)]
    for y in range(yb + 1, max(old, yb) + 2):
        S(x, y, z, "air")
    for y in range(yb - 1, min(old, yb) - 1, -1):
        S(x, y, z, "blackstone")
    for y in range(yb - 1, yb - 12, -1):
        if y >= old - 1 and y < yb:
            S(x, y, z, "blackstone")
    S(x, yb, z, path_mat(x, yb, z))
    area.top[(x, z)] = yb
# the road from the south reaches the path's start
road([(-47, -116), (-40, -112), (-38, -111)], 4)
for k, (px, pz, yb) in enumerate(path_pts):
    if k % 150 == 75:
        a = math.atan2(pz - VCZ, px - VCX)
        ox, oz = int(round(px + math.cos(a) * 2.6)), int(round(pz + math.sin(a) * 2.6))
        yo = path_cells.get((ox, oz))
        if yo is not None:
            S(ox, yo + 1, oz, "polished_blackstone_wall")
            S(ox, yo + 2, oz, "polished_blackstone_wall")
            S(ox, yo + 3, oz, "lantern[hanging=false]")
    if k % 400 == 200:
        a = math.atan2(pz - VCZ, px - VCX)
        ox, oz = int(round(px - math.cos(a) * 2.6)), int(round(pz - math.sin(a) * 2.6))
        yo = path_cells.get((ox, oz))
        if yo is not None:
            S(ox, yo + 1, oz, "shroomlight")
path_near = set()
for (x, z) in path_cells:
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            path_near.add((x + dx, z + dz))

# the rim overlook where the path ends
ex, ez, ey = path_pts[-1]
EXX, EZZ = int(round(ex)), int(round(ez))
ang_end = math.atan2(ez - VCZ, ex - VCX)
for dx in range(-3, 4):
    for dz in range(-3, 4):
        c = (EXX + dx, EZZ + dz)
        if c in H and math.hypot(dx, dz) <= 3.2:
            S(c[0], ey, c[1], "polished_blackstone_bricks")
            for y in range(ey + 1, ey + 6):
                S(c[0], y, c[1], "air")
            S(c[0], ey - 1, c[1], "blackstone")
            path_cells[c] = ey
S(EXX, ey + 1, EZZ, "chiseled_polished_blackstone")
S(EXX, ey + 2, EZZ, "soul_campfire[lit=true]") if False else None
sign(area, EXX + 1, ey + 1, EZZ, 4, ["Blazing Volcano", "Crater rim", "mind the heat"])

# ---------------------------------------------------------------- geysers (vents on the slopes and at the base)
GEYSERS = [(-34, -108), (36, -112), (-46, -165), (47, -172), (2, -228)]
geyser_spots = []
for i, (gx, gz) in enumerate(GEYSERS, 1):
    if (gx, gz) not in H:
        continue
    y0 = H[(gx, gz)]
    flat_patch(gx, gz, 4, y0, "blackstone")
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            d = math.hypot(dx, dz)
            if (gx + dx, gz + dz) in H:
                if d <= 1.5 and (dx or dz):
                    S(gx + dx, y0, gz + dz, "magma_block")
                elif 1.5 < d <= 3.2:
                    S(gx + dx, y0, gz + dz, "basalt" if (dx + dz) % 2 else "polished_basalt")
    S(gx, y0, gz, "air")
    S(gx, y0 - 1, gz, "shroomlight")
    S(gx, y0 + 1, gz, "air")
    area.npc("spawn:geyser %d" % i, gx, y0, gz, 0.0, "a vent: ring of magma around a hole; the mod adds the eruption")
    geyser_spots.append((gx, y0, gz))

# ---------------------------------------------------------------- the Stronghold
wall_pick = area.mix({"nether_bricks": 10, "cracked_nether_bricks": 2, "red_nether_bricks": 2})
court_pick = area.mix({"polished_blackstone_bricks": 5, "nether_bricks": 3, "cracked_polished_blackstone_bricks": 2,
                       "red_nether_bricks": 1})
XW, XE, ZN, ZS = -44, 44, -58, 26


def solid_box(x1, y1, z1, x2, y2, z2, pick=wall_pick):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(y1, y2 + 1):
                S(x, y, z, pick(x, y, z))


# the courtyard floor, then the four walls
for x in range(XW - 5, XE + 6):
    for z in range(ZN - 5, ZS + 6):
        if (x, z) in H and H[(x, z)] == SG:
            S(x, SG, z, court_pick(x, SG, z))
solid_box(XW, 65, ZN, XW + 4, 72, ZS)
solid_box(XE - 4, 65, ZN, XE, 72, ZS)
solid_box(XW, 65, ZN, XE, 72, ZN + 4)
solid_box(XW, 65, ZS - 4, XE, 72, ZS)
# parapets: merlons on the outer column, a fence on the inner one, shroomlight in the outer face
for z in range(ZN, ZS + 1):
    for x, inner in ((XW, XW + 4), (XE, XE - 4)):
        if z % 4 != 3:
            S(x, 73, z, "nether_bricks")
            S(x, 74, z, "nether_bricks")
        S(inner, 73, z, "nether_brick_fence")
        if z % 8 == 0:
            S(x + (-1 if x == XW else 1) * 0, 69, z, "shroomlight")
for x in range(XW, XE + 1):
    for z, inner in ((ZN, ZN + 4), (ZS, ZS - 4)):
        if x % 4 != 3:
            S(x, 73, z, "nether_bricks")
            S(x, 74, z, "nether_bricks")
        S(x, 73, inner, "nether_brick_fence")
        if x % 8 == 0:
            S(x, 69, z, "shroomlight")
for x in range(XW, XE + 1, 8):               # chiseled band along the wall bases
    for z in (ZN, ZS):
        S(x, 66, z, "chiseled_nether_bricks")
for z in range(ZN, ZS + 1, 8):
    for x in (XW, XE):
        S(x, 66, z, "chiseled_nether_bricks")


def tower(cx, cz, h, top, spire=True, doors=()):
    x1, x2, z1, z2 = cx - h, cx + h, cz - h, cz + h
    solid_box(x1, 65, z1, x2, top, z2)
    F(x1 + 1, 73, z1 + 1, x2 - 1, top - 1, z2 - 1, "air")
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            S(x, 72, z, "red_nether_bricks")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            ring = x in (x1, x2) or z in (z1, z2)
            if ring:
                S(x, 72, z, "chiseled_nether_bricks")
                S(x, top, z, "chiseled_nether_bricks")
                S(x, top + 1, z, "nether_bricks")
                if (x + z) % 2 == 0:
                    S(x, top + 2, z, "nether_bricks")
    for (a, b, c, d, e2, f) in doors:
        F(a, b, c, d, e2, f, "air")
    for x in (cx - 1, cx, cx + 1):
        for (zz, ax) in ((z1, "z"), (z2, "z")):
            S(x, 76, zz, "iron_bars")
            S(x, 77, zz, "iron_bars")
    for z in (cz - 1, cz, cz + 1):
        for xx in (x1, x2):
            S(xx, 76, z, "iron_bars")
            S(xx, 77, z, "iron_bars")
    for y in range(73, top + 1):                          # ladder up to the roof
        S(x1 + 1, y, z1 + 1, "ladder[facing=east]")
    S(cx, top - 1, cz, "lantern[hanging=true]")
    if spire:
        F(cx, top + 1, cz, cx, top + 5, cz, "nether_brick_fence")
        S(cx, top + 6, cz, "lantern[hanging=false]")
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        S(x, top + 3, z, "lantern[hanging=false]")
        S(x, top + 2, z, "nether_brick_fence")


def corner_doors(cx, cz, h):
    sx = 1 if cx < 0 else -1
    zr = (cz + 1, cz + 3) if cz < 0 else (cz - 3, cz - 1)
    xr = (cx + 2, cx + 4) if cx < 0 else (cx - 4, cx - 2)
    xdoor = cx + sx * h
    zdoor = cz + (h if cz < 0 else -h)
    return [(xdoor, 73, zr[0], xdoor, 75, zr[1]), (xr[0], 73, zdoor, xr[1], 75, zdoor)]


for (cx, cz) in ((XW, ZN), (XE, ZN), (XW, ZS), (XE, ZS)):
    tower(cx, cz, 5, 81, spire=(cx, cz) != (XE, ZN), doors=corner_doors(cx, cz, 5))
for cz in (-16, 6):
    for cx in (XW, XE):
        wx = (cx + 1, cx + 3) if cx < 0 else (cx - 3, cx - 1)
        tower(cx, cz, 3, 78, spire=True, doors=[(wx[0] if cx < 0 else wx[0], 73, cz - 3, wx[1], 75, cz - 3),
                                                  (wx[0], 73, cz + 3, wx[1], 75, cz + 3)])

# the gatehouse on the south wall, facing south
solid_box(-10, 65, 19, 10, 79, 29)
F(-3, 65, 19, 3, 71, 29, "air")
F(-7, 73, 21, 7, 78, 27, "air")
for x in range(-7, 8):
    for z in range(21, 28):
        S(x, 72, z, "red_nether_bricks")
for (x, z0, z1) in ((-10, 23, 25), (10, 23, 25)):
    F(x - (0 if x > 0 else 0), 73, z0, x, 75, z1, "air")
F(-10, 73, 23, -8, 75, 25, "air")
F(8, 73, 23, 10, 75, 25, "air")
for x in range(-3, 4):
    for z in range(19, 30):
        S(x, SG, z, "red_nether_bricks" if (x + z) % 2 else "polished_blackstone_bricks")
for x in range(-4, 5):
    for y in (70, 71):
        S(x, y, 29, "iron_bars") if abs(x) <= 3 else None
    S(x, 72, 29, "chiseled_nether_bricks")
for y in range(66, 72):
    for x in (-4, 4):
        S(x, y, 29, "chiseled_nether_bricks" if y % 3 == 0 else "polished_blackstone_bricks")
        S(x, y, 30, "nether_bricks")
for x in range(-10, 11):
    S(x, 79, 29, "chiseled_nether_bricks")
    S(x, 79, 19, "chiseled_nether_bricks")
    for z in (19, 29):
        S(x, 80, z, "nether_bricks")
        if x % 2 == 0:
            S(x, 81, z, "nether_bricks")
for z in range(19, 30):
    for x in (-10, 10):
        S(x, 80, z, "nether_bricks")
        if z % 2 == 0:
            S(x, 81, z, "nether_bricks")
for x in (-9, 9):
    F(x, 80, 24, x, 84, 24, "nether_brick_fence")
    S(x, 85, 24, "lantern[hanging=false]")
for x in (-3, 3):
    S(x, 72, 30, "chiseled_nether_bricks")
    F(x, 73, 30, x, 74, 30, "nether_brick_fence")
    S(x, 75, 30, "lantern[hanging=false]")
S(-2, 71, 24, "air")
for x in (-2, 0, 2):
    S(x, 71, 21, "lantern[hanging=true]")
    S(x, 71, 27, "lantern[hanging=true]")
sign(area, 5, 65, 33, 0, ["The Stronghold", "Crimson Isle"])
# the north postern
F(-2, 65, ZN, 2, 69, ZN + 4, "air")
for x in range(-2, 3):
    for z in range(ZN, ZN + 5):
        S(x, SG, z, "red_nether_bricks")
    S(x, 70, ZN, "chiseled_nether_bricks")
    S(x, 69, ZN, "iron_bars") if x % 2 == 0 else None
for x in (-3, 3):
    for y in range(65, 71):
        S(x, y, ZN, "chiseled_nether_bricks" if y % 3 == 0 else "polished_blackstone_bricks")
    S(x, 74, ZN - 1, "lantern[hanging=false]")

# stairs up to the wall walks, from the courtyard
for x0, face in ((XW + 4, "east"), (XE - 4, "west")):
    for zc in (-30, 8):
        area.stairs_down(x0, 72, zc, 8, face, block="nether_brick_stairs", width=3, solid="nether_bricks")
# the arcade: pillars along the inner wall faces with lanterns
for z in range(-48, 19, 8):
    for x in (XW + 8, XE - 8):
        if any(abs(z - s) <= 3 for s in (-30, 8)):
            continue
        for y in range(65, 71):
            S(x, y, z, "nether_bricks" if y % 3 else "chiseled_nether_bricks")
        S(x, 71, z, "chiseled_nether_bricks")
        S(x, 72, z, "lantern[hanging=false]")
# the central inlay
CY = -17
for x in range(-11, 12):
    for z in range(CY - 11, CY + 12):
        d = math.hypot(x, z - CY)
        if d <= 1.6:
            S(x, SG, z, "shroomlight")
        elif 3.5 <= d <= 4.5:
            S(x, SG, z, "red_nether_bricks")
        elif 7.5 <= d <= 8.5:
            S(x, SG, z, "chiseled_nether_bricks" if (x + z) % 2 else "nether_bricks")
        elif 10 <= d <= 10.8:
            S(x, SG, z, "shroomlight") if (x + z) % 3 == 0 else None
# the road from the gate to the keep
for z in range(-34, 19):
    for x in range(-2, 3):
        S(x, SG, z, "red_nether_bricks" if abs(x) == 2 else "polished_blackstone_bricks" if z % 2 else "nether_bricks")
for z in range(-50, 19, 10):
    for x in (-16, 16):
        S(x, SG, z, "shroomlight")
# the keep, against the north wall
solid_box(-20, 65, -53, 20, 75, -33)
F(-18, 65, -52, 18, 73, -35, "air")
for x in range(-19, 20):
    for z in range(-52, -34):
        S(x, SG, z, "red_nether_bricks" if (x // 3 + z // 3) % 2 else "polished_blackstone_bricks")
F(-2, 65, -34, 2, 69, -33, "air")
for x in (-3, 3):
    for y in range(65, 71):
        S(x, y, -33, "chiseled_nether_bricks" if y % 3 == 0 else "polished_blackstone_bricks")
for x in range(-2, 3):
    S(x, 70, -33, "chiseled_nether_bricks")
for x in range(-19, 20, 6):
    S(x, 70, -53, "air")
    for z in (-53, -33):
        S(x, 70, z, "iron_bars") if abs(x) > 3 or z == -53 else None
        S(x, 71, z, "iron_bars") if abs(x) > 3 or z == -53 else None
for z in range(-51, -35, 5):
    for x in (-19, 19):
        S(x, 70, z, "iron_bars")
        S(x, 71, z, "iron_bars")
for x in range(-15, 16, 6):
    for z in (-48, -40):
        F(x, 65, z, x, 72, z, "polished_blackstone_bricks")
        S(x, 73, z, "chiseled_polished_blackstone")
        S(x, 66, z, "nether_bricks")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            S(x + dx, 72, z + dz, "lantern[hanging=true]") if dx == 1 and dz == 0 else None
for x in range(-13, 14, 6):
    S(x, 72, -44, "lantern[hanging=true]")
    S(x, 73, -44, "chain[axis=y]")
# throne and carpets
for dx in range(-3, 4):
    for dz in range(-3, 0):
        S(dx, 65, -51 + dz + 3, "nether_brick_slab[type=bottom]") if False else None
for x in range(-4, 5):
    S(x, 65, -50, "nether_brick_stairs[facing=north,half=bottom]")
    for y in (65, 66):
        S(x, y, -52, "red_nether_bricks")
for x in range(-1, 2):
    S(x, 67, -52, "nether_brick_stairs[facing=south,half=bottom]") if x == 0 else None
S(0, 65, -52, "air")
S(0, 66, -52, "nether_brick_stairs[facing=south,half=bottom]")
for z in range(-49, -34):
    S(0, 65, z, "red_carpet") if abs(z % 2) == 0 else S(0, 65, z, "red_carpet")
for z in (-51, -39):
    for x in (-8, 8):
        S(x, 65, z, "barrel[facing=up]")
for dx in (-6, 6):
    S(dx, 65, -50, "crimson_fence_gate[facing=south]") if False else None
# roof: battlement
for x in range(-20, 21):
    for z in (-53, -33):
        S(x, 76, z, "nether_bricks")
        if x % 2 == 0:
            S(x, 77, z, "nether_bricks")
for z in range(-53, -32):
    for x in (-20, 20):
        S(x, 76, z, "nether_bricks")
        if z % 2 == 0:
            S(x, 77, z, "nether_bricks")
for x in (-20, 20):
    for z in (-53, -33):
        F(x, 77, z, x, 81, z, "nether_brick_fence")
        S(x, 82, z, "lantern[hanging=false]")
F(-8, 76, -43, 8, 76, -43, "shroomlight") if False else None
# the Guard room hidden in the west wall (Fairy Soul)
F(-43, 65, -3, -41, 67, 3, "air")
F(-40, 65, -1, -40, 66, 1, "air")
for z in range(-3, 4):
    S(-43, 64, z, "red_nether_bricks")
S(-42, 67, 0, "lantern[hanging=true]")
S(-42, 65, -3, "barrel[facing=east]")
S(-42, 65, 3, "barrel[facing=east]")
# a few blaze braziers in the courtyard (campfires on netherrack bases)
for (x, z) in ((-26, -36), (26, -36), (-26, 6), (26, 6)):
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            S(x + dx, SG, z + dz, "netherrack" if dx == 0 and dz == 0 else "polished_blackstone_bricks")
            S(x + dx, 65, z + dz, "polished_blackstone_brick_slab[type=bottom]") if (dx or dz) else None
    S(x, 65, z, "soul_campfire[lit=true]")

# ---------------------------------------------------------------- the Ruins of Ashfang
pol = area.mix({"polished_blackstone_bricks": 6, "cracked_polished_blackstone_bricks": 3, "polished_blackstone": 2,
                "blackstone": 1})


def rubbled(x, y, z, p=0.07):
    return hash01(x, y, z, 77) < p


for x in range(46, 81):
    for z in range(50, 93):
        if (x, z) in H and not rubbled(x, 65, z, 0.06):
            S(x, 65, z, pol(x, 65, z))
for x in range(51, 76):
    for z in range(55, 88):
        if (x, z) in H and not rubbled(x, 66, z, 0.05):
            S(x, 66, z, pol(x, 66, z))
            S(x, 65, z, "polished_blackstone_bricks")
for x in range(56, 71):                                          # the open middle: a chiseled arena
    for z in range(61, 82):
        S(x, 66, z, "chiseled_polished_blackstone" if (x + z) % 4 == 0 else "polished_blackstone_bricks")
        for y in range(67, 75):
            S(x, y, z, "air")
AX, AZ = 63, 71
for x in range(56, 71):
    for z in range(61, 82):
        d = math.hypot(x - AX, (z - AZ) * 0.7)
        if 5.5 <= d <= 6.2:
            S(x, 66, z, "gilded_blackstone" if (x + z) % 3 else "crying_obsidian")
        if d <= 1.5:
            S(x, 66, z, "shroomlight") if (x + z) % 2 == 0 else S(x, 66, z, "soul_soil")
# stairs up from the west and the south
for z in range(66, 77):
    S(45, 65, z, "polished_blackstone_brick_stairs[facing=east,half=bottom]")
    S(50, 66, z, "polished_blackstone_brick_stairs[facing=east,half=bottom]")
for x in range(58, 69):
    S(x, 65, 93, "polished_blackstone_brick_stairs[facing=north,half=bottom]")
    S(x, 66, 88, "polished_blackstone_brick_stairs[facing=north,half=bottom]")
# broken pillars around the arena
pillars = []
for x in range(52, 75, 6):
    pillars += [(x, 56), (x, 86)]
for z in range(62, 84, 6):
    pillars += [(52, z), (74, z)]
for i, (px, pz) in enumerate(pillars):
    ht = 2 + int(hash01(px, pz, 1, 9) * 8)
    for y in range(67, 67 + ht):
        S(px, y, pz, "polished_blackstone_bricks" if y % 4 else "chiseled_polished_blackstone")
        if y == 67 + ht - 1 and ht < 8:
            S(px + (1 if i % 2 else -1), y, pz, "polished_blackstone_brick_slab[type=top]")
    if ht >= 8:
        S(px, 67 + ht, pz, "polished_blackstone_wall")
        S(px, 68 + ht, pz, "soul_lantern[hanging=false]")
    else:
        for dx, dz in ((1, 1), (-1, 0), (0, -1)):
            if hash01(px + dx, 3, pz + dz, 9) < 0.7:
                S(px + dx, 67, pz + dz, "blackstone" if hash01(px, dx, pz, 2) < 0.5 else "polished_blackstone_brick_slab[type=bottom]")
# the ruined back wall (north) with a broken gateway
for x in range(46, 81):
    for z in range(50, 53):
        for y in range(66, 78):
            if 60 <= x <= 66 and y < 74:
                continue
            lim = 0.15 + (y - 66) / 12 * 0.95
            if hash01(x, y, z, 31) > lim:
                S(x, y, z, "polished_blackstone_bricks" if hash01(x, y, z, 5) < 0.6 else "cracked_polished_blackstone_bricks"
                  if hash01(x, y, z, 6) < 0.5 else "chiseled_polished_blackstone")
for y in range(66, 72):
    for x in (59, 67):
        S(x, y, 51, "polished_blackstone_bricks" if y % 3 else "chiseled_polished_blackstone")
# braziers with soul fire (soil above the floor so the fire survives)
for (bx, bz) in ((57, 62), (69, 62), (57, 80), (69, 80), (63, 63)):
    S(bx, 67, bz, "soul_soil")
    S(bx, 68, bz, "soul_fire")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(bx + dx, 67, bz + dz, "polished_blackstone_brick_slab[type=bottom]")
for (px, pz) in ((47, 60), (47, 82), (78, 60), (78, 82)):
    for y in range(66, 71):
        S(px, y, pz, "polished_blackstone_wall")
    S(px, 71, pz, "soul_lantern[hanging=false]")
for x in range(47, 80, 5):                                      # rubble and loose lanterns on the outer platform
    for z in range(51, 92, 7):
        if hash01(x, z, 2, 3) < 0.6:
            S(x, 66, z, "blackstone")
            S(x, 67, z, "soul_lantern[hanging=false]") if hash01(x, z, 3, 3) < 0.3 else None
sign(area, 44, 65, 73, 4, ["Ruins of Ashfang", "Ashfang waits"], wood="crimson")

# ---------------------------------------------------------------- the arrival plaza
PZ = 202
for x in range(-20, 21):
    for z in range(192, 213):
        dx, dz = abs(x) - 14, abs(z - PZ) - 4
        if dx > 0 and dz > 0 and math.hypot(dx, dz) > 6:
            continue
        if (x, z) not in H:
            continue
        edge_cell = abs(x) >= 19 or z in (192, 212) or (dx > 0 and dz > 0 and math.hypot(dx, dz) > 5)
        d = math.hypot(x, z - 205)
        if edge_cell:
            b = "nether_bricks"
        elif d <= 1.5:
            b = "shroomlight"
        elif d <= 4.5:
            b = "red_nether_bricks" if int(d) % 2 else "chiseled_polished_blackstone"
        else:
            b = "polished_blackstone_bricks" if (x + z) % 2 == 0 else "blackstone" if hash01(x, z, 1, 1) < 0.4 else "polished_blackstone"
        S(x, 66, z, b)
        area.top[(x, z)] = 66
for (x, z) in ((-17, 195), (17, 195), (-17, 209), (17, 209), (-7, 193), (7, 193)):
    F(x, 67, z, x, 69, z, "polished_blackstone_bricks")
    S(x, 70, z, "chiseled_polished_blackstone")
    S(x, 71, z, "lantern[hanging=false]")
for x in range(-19, 20, 2):
    S(x, 67, 192, "polished_blackstone_wall") if abs(x) > 3 else None
sign(area, 4, 67, 198, 0, ["Crimson Isle", "Combat 22", "Follow the road north"])
# the launch pad back to the Spider's Den
PX, PZ_ = -11, 199
for dx in range(-2, 3):
    for dz in range(-2, 3):
        S(PX + dx, 66, PZ_ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "polished_blackstone")
S(PX, 67, PZ_, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    S(PX + dx, 66, PZ_ + dz, "polished_blackstone_bricks")
    S(PX + dx, 67, PZ_ + dz, "polished_blackstone_wall")
    S(PX + dx, 68, PZ_ + dz, "soul_lantern[hanging=false]")
sign(area, PX + 4, 67, PZ_ + 1, 4, ["Spider's Den", "back by pad"])
area.pad("the Spider's Den", PX - 1, 67, PZ_ - 1, PX + 1, 68, PZ_ + 1, destination=SPIDERS_DEN_BACK)
# a secret cellar under the plaza (Fairy Soul)
area.stairs_down(9, 66, 207, 5, "east", block="polished_blackstone_brick_stairs", width=3, solid="polished_blackstone_bricks")
F(14, 62, 203, 20, 64, 209, "air")
for x in range(14, 21):
    for z in range(203, 210):
        S(x, 61, z, "nether_bricks")
        S(x, 65, z, "polished_blackstone_bricks")
S(17, 64, 206, "lantern[hanging=true]")
for x in (14, 20):
    S(x, 62, 203, "barrel[facing=up]")
F(14, 62, 206, 14, 64, 206, "air")
for z in range(206, 207):
    S(13, 62, z, "air")

# ---------------------------------------------------------------- bridges
bridge_half(area, -100, -86, BRZ[0])
bridge_half(area, -100, -86, BRZ[1])
bridge_half(area, 86, 99, BRZ[0])
bridge_half(area, 86, 99, BRZ[1])
for cz in BRZ:
    for x in (-93, -92, 91, 92):
        for dz in (-3, 3):
            S(x, 67, cz + dz, "nether_brick_wall")

# ---------------------------------------------------------------- the Crimson Fields' scenery
reserved_rects = [(-62, 62, -72, 42), (40, 86, 44, 98), (-26, 26, 186, 220), (-18, 18, -114, -90)]


def reserved(x, z):
    if (x, z) in road_near or (x, z) in path_near:
        return True
    for (a, b, c, d) in reserved_rects:
        if a <= x <= b and c <= z <= d:
            return True
    for cz in BRZ:
        if abs(z - cz) <= 12 and (x <= -78 or x >= 78):
            return True
    return False


def hang_vines(x, z, y_from, y_ground, n):
    if (x, z) not in H:
        return
    y = y_from
    for i in range(n):
        if y <= y_ground + 1:
            break
        S(x, y, z, "weeping_vines_plant")
        y -= 1
    S(x, y + 1, z, "weeping_vines[age=25]") if y_from - y > 0 else None


def fungus_tree(x, z, seed):
    y0 = H[(x, z)]
    hgt = 6 + int(hash01(x, 1, z, seed) * 8)
    rr = 3 + int(hash01(x, 2, z, seed) * 2)
    for h in range(1, hgt + 1):
        S(x, y0 + h, z, "crimson_stem[axis=y]")
    if hash01(x, 3, z, seed) < 0.5:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            S(x + dx, y0 + 1, z + dz, "crimson_stem[axis=%s]" % ("x" if dx else "z"))
    top = y0 + hgt
    for dy, rad in ((-1, rr), (0, rr), (1, rr - 1), (2, rr - 2)):
        for dx in range(-rad, rad + 1):
            for dz in range(-rad, rad + 1):
                if dx * dx + dz * dz > rad * rad + 1 or (dx == 0 and dz == 0 and dy < 1) or (x + dx, z + dz) not in H:
                    continue
                if dy == -1 and hash01(x + dx, z + dz, 7, seed) < 0.15:
                    continue
                S(x + dx, top + dy, z + dz, "shroomlight" if hash01(x + dx, top + dy, z + dz, seed) < 0.06 else "nether_wart_block")
    S(x, top + 1, z, "nether_wart_block")
    for dx in range(-rr, rr + 1):
        for dz in range(-rr, rr + 1):
            d2 = dx * dx + dz * dz
            if rr * rr - 3 <= d2 <= rr * rr + 1 and hash01(x + dx, z + dz, 8, seed) < 0.6:
                hang_vines(x + dx, z + dz, top - 2, H.get((x + dx, z + dz), y0), 2 + int(hash01(x + dx, z + dz, 9, seed) * 6))


trees = []
for (x, z) in sorted(H):
    if z < -62 or reserved(x, z) or hash01(x, z, 11, 21) > 0.0042:
        continue
    r, R, _ = vol_params(x, z)
    if r < R + 6 or H[(x, z)] > 70:
        continue
    if any(abs(x - tx) < 8 and abs(z - tz) < 8 for tx, tz in trees):
        continue
    trees.append((x, z))
    fungus_tree(x, z, len(trees))
# roots on the nylium
for (x, z), y in H.items():
    if reserved(x, z) or z < -62:
        continue
    if G_ := area.get(x, y, z):
        if G_ == "minecraft:crimson_nylium" and hash01(x, z, 12, 5) < 0.12 and area.get(x, y + 1, z) in (None, "minecraft:air"):
            S(x, y + 1, z, "crimson_roots")
# basalt pillars and boulders
pill = 0
for (x, z), y in sorted(H.items()):
    if reserved(x, z):
        continue
    r, R, _ = vol_params(x, z)
    delta = (R < r < R + 20) or (z < -62 and r >= R)
    if hash01(x, z, 13, 5) < (0.008 if delta else 0.0016) and y < 72:
        ht = 2 + int(hash01(x, z, 14, 5) * (9 if delta else 6))
        for dx, dz in ((0, 0),) + (((1, 0), (0, 1), (1, 1)) if hash01(x, z, 15, 5) < 0.4 else ()):
            if (x + dx, z + dz) in H and not reserved(x + dx, z + dz):
                for h in range(1, ht - (dx + dz) * 2 + 1):
                    S(x + dx, H[(x + dx, z + dz)] + h, z + dz, "basalt[axis=y]" if h % 5 else "polished_basalt[axis=y]")
        pill += 1
for (x, z), y in sorted(H.items()):
    if reserved(x, z) or z < -62 and False:
        continue
    if hash01(x, z, 16, 5) < 0.0012 and y < 72:
        rad = 1 + int(hash01(x, z, 17, 5) * 2)
        for dx in range(-rad, rad + 1):
            for dz in range(-rad, rad + 1):
                for dy in range(0, rad + 1):
                    if dx * dx + dz * dz + dy * dy * 1.6 <= rad * rad + 1 and (x + dx, z + dz) in H and not reserved(x + dx, z + dz):
                        S(x + dx, H[(x + dx, z + dz)] + 1 + dy, z + dz, "blackstone" if hash01(x + dx, dy, z + dz, 3) < 0.7 else "basalt")
# a few enclosed lava pools (one block deep carve, lava 2 below the rim, stone all around)
POOLS = [(-62, 75, 5), (-30, 140, 6), (30, 150, 5), (70, 140, 6), (-70, 180, 5), (-76, -30, 4), (76, -34, 4)]
for (cx, cz, rad) in POOLS:
    if (cx, cz) not in H:
        continue
    y0 = H[(cx, cz)]
    for x in range(cx - rad - 3, cx + rad + 4):
        for z in range(cz - rad - 3, cz + rad + 4):
            if (x, z) not in H:
                continue
            d = math.hypot(x - cx, z - cz)
            if d > rad + 2.6:
                continue
            old = H[(x, z)]
            for y in range(y0 + 1, old + 7):
                S(x, y, z, "air")
            for y in range(min(old, y0), max(old, y0)):
                S(x, y, z, "netherrack")
            H[(x, z)] = y0
            area.top[(x, z)] = y0
            if d <= rad:
                S(x, y0, z, "air")
                S(x, y0 - 1, z, "lava")
                S(x, y0 - 2, z, "lava")
                S(x, y0 - 3, z, "blackstone")
            elif d <= rad + 1.3:
                S(x, y0, z, "blackstone" if (x + z) % 2 else "polished_blackstone_bricks")
            else:
                S(x, y0, z, "basalt" if (x + z) % 3 else "magma_block")
    # glowing sign of the pool: shroomlight under the surface
    S(cx + rad + 2, y0 + 1, cz, "polished_blackstone_wall")
    S(cx + rad + 2, y0 + 2, cz, "lantern[hanging=false]")

# ---------------------------------------------------------------- Fairy Souls, NPCs, warp, arrival
area.arrival(*ARRIVAL, 180.0)
area.warp("Crimson Isle", *ARRIVAL, 180.0)
area.npc("Elle", 0, 65, 16, 180.0, "just inside the Stronghold's south gate; the guide")
area.npc("spawn:Bladesoul", 0, 65, -17, 0.0, "the Stronghold's courtyard, on the shroomlight inlay (room for a big mob)")
area.npc("spawn:Ashfang", AX, 67, AZ, 0.0, "the middle of the ruins' arena (room for a big mob)")
area.npc("spawn:Magma Boss", POOL[0] + 11, CF + 1, POOL[1], 180.0, "beside the Magma Chamber's lava pool")

area.fairy_soul(17, 62, 206)                                 # the cellar under the arrival plaza
area.fairy_soul(-42, 65, 0)                                  # the guard room inside the Stronghold's west wall
area.fairy_soul(XE, 82, ZN)                                  # the Stronghold's north-east tower roof
sx_, sz_ = int(round(VCX + 17.5 * math.cos(ang_end + math.pi))), int(round(VCZ + 17.5 * math.sin(ang_end + math.pi)))
area.fairy_soul(sx_, H[(sx_, sz_)] + 1, sz_)                 # the crater rim, opposite the overlook
area.fairy_soul(0, CF + 1, -217)                             # the Magma Chamber's north Kada cave
area.fairy_soul(AX - 8, 67, 58)                              # behind a broken pillar in the ruins
FX, FZ = -72, 150
for dx in (-1, 0, 1):
    for dz in (-1, 0, 1):
        if (dx or dz) and (FX + dx, FZ + dz) in H and (dx, dz) != (0, 1):
            for h in range(1, 5):
                S(FX + dx, H[(FX + dx, FZ + dz)] + h, FZ + dz, "basalt[axis=y]")
S(FX, H[(FX, FZ)] + 1, FZ, "air")
S(FX, H[(FX, FZ)] + 3, FZ + 0, "air")
area.fairy_soul(FX, H[(FX, FZ)] + 1, FZ)                      # a ring of basalt pillars in the west fields
GX, GZ = -22, -236
flat_patch(GX, GZ, 3, H[(GX, GZ)], "polished_basalt")
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if 2 < math.hypot(dx, dz) <= 3.2 and dz != 3:
            for h in range(1, 5):
                S(GX + dx, H[(GX, GZ)] + h, GZ + dz, "basalt[axis=y]")
area.fairy_soul(GX, H[(GX, GZ)] + 1, GZ)                      # an ash grotto on the volcano's north foot

# ---------------------------------------------------------------- zones (split by box, no overlap)
GROUND = 40
TOPY = 189
area.zone("Crimson Fields", box=((-100, GROUND, 31), (41, TOPY, 249)), mobs=[
    mob("Magma Cube", 8, 6, (-85, 63, 40), (-20, 78, 100)),
    mob("Pack Magma Cube", 4, 10, (-85, 63, 118), (-20, 78, 185)),
])
area.zone("Crimson Fields B", box=((42, GROUND, 105), (99, TOPY, 249)), mobs=[
    mob("Magma Cube Rider", 3, 12, (46, 63, 120), (86, 78, 190)),
])
area.zone("Ruins of Ashfang", box=((42, GROUND, 31), (99, TOPY, 104)), mobs=[
    mob("Blaze", 4, 10, (48, 66, 52), (76, 90, 90)),
])
area.zone("Stronghold", box=((-100, GROUND, -59), (99, TOPY, 30)), mobs=[
    mob("Blaze", 8, 6, (-38, 65, -32), (38, 90, 20)),
    mob("Mutated Blaze", 4, 10, (-88, 64, -50), (-50, 90, 24)),
    mob("Bezal", 3, 12, (50, 64, -50), (88, 90, 24)),
    mob("Wither Skeleton", 4, 10, (-17, 65, -51), (17, 72, -36)),
])
area.zone("Blazing Volcano (west)", box=((-100, GROUND, -250), (-43, TOPY, -100)), mobs=[
    mob("Smoldering Blaze", 3, 8, (-90, 63, -230), (-46, 110, -110)),
    mob("Ghast", 3, 20, (-92, 115, -240), (-46, 150, -110)),
])
area.zone("Blazing Volcano (east)", box=((43, GROUND, -250), (99, TOPY, -100)), mobs=[
    mob("Smoldering Blaze", 3, 8, (46, 63, -230), (90, 110, -110)),
    mob("Flare", 3, 15, (46, 64, -200), (90, 120, -110)),
])
area.zone("Blazing Volcano (top)", box=((-42, 93, -232), (42, TOPY, -100)), mobs=[
    mob("Millennia-Aged Blaze", 2, 30, (-30, 128, -200), (30, 165, -140)),
])
area.zone("Blazing Volcano (north)", box=((-42, GROUND, -250), (42, TOPY, -233)))
area.zone("Blazing Volcano (south)", box=((-100, GROUND, -99), (99, TOPY, -60)))
area.zone("Magma Chamber", box=((-42, GROUND, -232), (42, 92, -100)), mobs=[
    mob("Kada Knight", 2, 15, (-9, CF + 1, -221), (9, CF + 8, -203)),
    mob("Kada Knight", 1, 15, (-38, CF + 1, -158), (-24, CF + 8, -142)),
    mob("Kada Knight", 1, 15, (24, CF + 1, -158), (38, CF + 8, -142)),
])
area.save()

# the extra boxes of one named zone share its display name: put it back in their files
for z in area.zones:
    m = re.match(r"^(.*?) (\(.*\)|B)$", z["name"])
    if not m:
        continue
    name = area.id + "_" + re.sub(r"[^a-z0-9]+", "_", z["name"].lower()).strip("_")
    path = ZONES / (name + ".json")
    data = json.loads(path.read_text(encoding="utf-8"))
    data["name"] = m.group(1)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
print("trees", len(trees), "pillars", pill, "geysers", geyser_spots, "overlook", (EXX, ey, EZZ))
