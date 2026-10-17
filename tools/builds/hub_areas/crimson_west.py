#!/usr/bin/env python3
"""The Crimson Isle's WEST region, the Barbarian faction's side (box x -330..-101, z -250..249):

    Dragontail (walled Barbarian town, NW), the Barbarian Outpost (the north bridge's west end), The Dukedom (a ruined
    keep on the west coast), the Smoldering Tomb (a sunken crypt), The Wasteland (ash plains with sulphur), the Burning
    Desert (red dunes, mesas, sulphur) and the Forgotten Skull (Kuudra's entrance, south), joined by roads.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python crimson_west.py
"""
import math
from kit import hash01, noise
from crimson_lib import new_area, land, bridge_half, sign, mob, LAND_BOTTOM, BRIDGE

area = new_area("west")
S = area.set
F = area.fill


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def rect_w(x, z, x1, z1, x2, z2, blend):
    d = math.hypot(max(x1 - x, 0, x - x2), max(z1 - z, 0, z - z2))
    return smooth(1 - d / blend)


def circ_w(x, z, cx, cz, r_in, r_out):
    return smooth((r_out - math.hypot(x - cx, z - cz)) / (r_out - r_in))


def air(x1, y1, z1, x2, y2, z2):
    F(x1, y1, z1, x2, y2, z2, "air")


# ================================================================ constants (world coordinates)
TOWN_Y = 72            # Dragontail's plateau surface (players stand at 73)
TOMB_Y = 72
DUKE_Y = 76
OUT_Y = 66
SKULL_Y = 67
TOWN_WALL = (-286, -206, -196, -124)          # x1, z1, x2, z2 of Dragontail's wall ring (3 thick)
TOWN_C = (-240, -165)                          # the square
DUKE_C = (-275, -18)
SKX, SKZ = -235, 207                           # the skull's centre (it faces north)
BRIDGES = (-80, 110)
MESAS = [(-215, 92, 13, 16), (-262, 80, 12, 18), (-272, 128, 15, 24), (-160, 140, 12, 14), (-302, 96, 11, 15),
         (-250, 150, 12, 17)]
SOUL_MESA = 2

# ================================================================ terrain
def coast(x, z):
    cx, cz, hx, hz, r = -216, 0, 110, 240, 50
    qx, qz = abs(x - cx) - (hx - r), abs(z - cz) - (hz - r)
    sd = math.hypot(max(qx, 0), max(qz, 0)) + min(max(qx, qz), 0) - r
    e = -sd + 11 * noise(x, z, 3, 1.2) + 5 * noise(x, z, 8, 2.5)
    for bx, bz, br in ((-330, -130, 34), (-326, 40, 28), (-110, 35, 30), (-112, -175, 38), (-300, 244, 35), (-318, 190, 26)):
        e -= 32 * smooth(1 - math.hypot(x - bx, z - bz) / br)
    e = max(e, 40 * rect_w(x, z, -158, -98, -107, -62, 10))          # the Outpost's headland
    e = max(e, 40 * rect_w(x, z, -135, 94, -107, 126, 8))            # the south bridge head
    return e


def raw_height(x, z):
    h = 64 + 3.0 * noise(x, z, 1, 1.1)
    w = circ_w(x, z, -245, -165, 75, 135)
    h += w * (11 * (0.5 + 0.5 * noise(x, z, 11, 1.3)) + 4 * abs(noise(x, z, 12, 3.0)))        # Dragontail's hills
    wd = rect_w(x, z, -325, 66, -108, 166, 14)
    ridge = 1 - abs(math.sin(x * 0.058 + z * 0.034 + 3.0 * noise(x, z, 22, 0.7)))
    h += wd * (8.0 * ridge ** 1.6 - 1)                                                          # dunes
    ww = rect_w(x, z, -262, -60, -108, 60, 10) * (1 - wd)
    h = h * (1 - ww) + (64 + 2.0 * noise(x, z, 31, 2.2)) * ww                                   # the ash plains
    for i, (mx, mz, mr, mh) in enumerate(MESAS):
        d = math.hypot(x - mx, z - mz) / (mr * (1 + 0.2 * noise(x, z, 40 + i, 2.0)))
        if d < 1:
            h = max(h, 66 + min(mh, (1 - d) * mr * 5.0))
    dk = circ_w(x, z, DUKE_C[0], DUKE_C[1], 10, 46)
    h += 12 * dk
    for (cx, cz, r_in, r_out, level) in ((DUKE_C[0], DUKE_C[1], 21, 29, DUKE_Y),):
        k = circ_w(x, z, cx, cz, r_in, r_out)
        h = h * (1 - k) + level * k
    for (x1, z1, x2, z2, bl, level) in ((-292, -212, -190, -118, 18, TOWN_Y), (-232, -58, -158, 8, 16, TOMB_Y),
                                        (-156, -100, -108, -60, 10, OUT_Y), (-280, 166, -190, 226, 12, SKULL_Y)):
        k = rect_w(x, z, x1, z1, x2, z2, bl)
        h = h * (1 - k) + level * k
    k = circ_w(x, z, -107, 110, 14, 30)
    h = h * (1 - k) + OUT_Y * k
    return h


# ---------------------------------------------------------------- roads
ROADS = {
    "A": [(-108, -80), (-130, -80), (-150, -80), (-166, -92), (-176, -120), (-188, -150), (-193, -165), (-199, -165)],
    "B": [(-108, 110), (-125, 110), (-150, 112), (-175, 120), (-196, 130), (-215, 148), (-230, 162), (-235, 172)],
    "C": [(-166, -92), (-158, -70), (-152, -45), (-152, -15), (-156, 15), (-168, 42), (-186, 70), (-194, 100), (-196, 130)],
    "D": [(-168, 42), (-185, 39), (-195, 37), (-215, 33), (-240, 26), (-258, 10), (-268, -2), (-275, -3)],
    "E": [(-195, 37), (-195, 22), (-195, 12), (-195, 9)],
}


def densify(poly):
    pts = []
    for (x1, z1), (x2, z2) in zip(poly, poly[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1))
        for i in range(n):
            pts.append((x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n))
    pts.append(poly[-1])
    return pts


RAW, E = {}, {}
for x in range(-329, -106):
    for z in range(-249, 249):
        e = coast(x, z)
        if e > 0:
            RAW[(x, z)] = raw_height(x, z)
            E[(x, z)] = e

ROADD = {}                           # (x, z) -> (distance to the road line, weight)
road_pts = []
for name, poly in ROADS.items():
    pts = densify(poly)
    prof = [raw_height(int(round(px)), int(round(pz))) for px, pz in pts]
    sm = []
    for i in range(len(prof)):
        win = prof[max(0, i - 4):i + 5]
        sm.append(sum(win) / len(win))
    for _ in range(2):
        for i in range(1, len(sm)):
            sm[i] = max(sm[i - 1] - 0.75, min(sm[i - 1] + 0.75, sm[i]))
        for i in range(len(sm) - 2, -1, -1):
            sm[i] = max(sm[i + 1] - 0.75, min(sm[i + 1] + 0.75, sm[i]))
    for (px, pz), ph in zip(pts, sm):
        road_pts.append((px, pz, name))
        for dx in range(-7, 8):
            for dz in range(-7, 8):
                x, z = int(round(px)) + dx, int(round(pz)) + dz
                if (x, z) not in RAW:
                    continue
                d = math.hypot(x - px, z - pz)
                if d > 7:
                    continue
                w = 1.0 if d <= 2.3 else smooth(1 - (d - 2.3) / 4.7)
                old = ROADD.get((x, z))
                if old is None or d < old[0]:
                    ROADD[(x, z)] = (d, w, ph)
for (x, z), (d, w, ph) in ROADD.items():
    RAW[(x, z)] = RAW[(x, z)] * (1 - w) + ph * w

TOP = {}
for (x, z), h in RAW.items():
    if x > -107:
        continue
    TOP[(x, z)] = max(60, min(int(round(h)), 60 + int(E[(x, z)] / 1.25)))

# ---------------------------------------------------------------- what the ground is made of
def mesa_of(x, z):
    for i, (mx, mz, mr, mh) in enumerate(MESAS):
        if abs(x - mx) > mr * 1.3 or abs(z - mz) > mr * 1.3:
            continue
        d = math.hypot(x - mx, z - mz) / (mr * (1 + 0.2 * noise(x, z, 40 + i, 2.0)))
        if d < 1 and TOP.get((x, z), 0) > 70:
            return i
    return None


def region(x, z):
    zz = z + 5 * noise(x, z, 5, 3.0)
    if -232 <= x <= -158 and -58 <= z <= 8:
        return "tomb"
    if zz >= 168:
        return "skull"
    if zz >= 62:
        return "desert"
    if math.hypot(x - DUKE_C[0], z - DUKE_C[1]) < 34 or (x < -262 and -64 < z < 24):
        return "duke"
    if zz < -62:
        return "dragon"
    return "waste"


SURF = {
    "dragon": area.mix({"crimson_nylium": 5, "netherrack": 2, "blackstone": 3, "basalt": 1}),
    "waste": area.mix({"soul_soil": 5, "soul_sand": 1, "gravel": 2, "basalt": 2, "blackstone": 2, "gray_concrete_powder": 2}),
    "duke": area.mix({"blackstone": 4, "cobbled_deepslate": 2, "crimson_nylium": 1, "gravel": 1, "basalt": 1}),
    "tomb": area.mix({"soul_soil": 5, "soul_sand": 2, "blackstone": 2, "basalt": 1}),
    "skull": area.mix({"blackstone": 4, "basalt": 2, "gravel": 2, "soul_soil": 2, "bone_block": 1}),
    "desert": area.mix({"red_sand": 14, "red_sandstone": 1, "terracotta": 1}),
}
UNDER = {
    "desert": area.mix({"red_sandstone": 4, "smooth_red_sandstone": 1}),
}
UNDER_DEF = area.mix({"blackstone": 3, "basalt": 2, "netherrack": 2, "soul_soil": 1})
BODY_DEF = area.mix({"blackstone": 4, "basalt": 3, "netherrack": 3, "cobbled_deepslate": 1})
BEACH = area.mix({"smooth_basalt": 3, "blackstone": 3, "gravel": 2, "basalt": 1})
BANDS = ["red_terracotta", "orange_terracotta", "terracotta", "brown_terracotta", "white_terracotta", "yellow_terracotta",
         "red_sandstone", "orange_terracotta"]
REG = {}

for (x, z), t in TOP.items():
    reg = region(x, z)
    REG[(x, z)] = reg
    mi = mesa_of(x, z)
    if mi is not None:
        band = lambda xx, yy, zz, seed=0: "minecraft:" + BANDS[(yy // 2 + mi) % len(BANDS)]
        surf = "minecraft:red_sand" if hash01(x, 1, z, 4) < 0.85 else "minecraft:red_sandstone"
        land(area, x, z, t, surf, band, band)
    elif t <= 62:
        land(area, x, z, t, "minecraft:red_sand" if reg == "desert" else BEACH, "minecraft:red_sandstone" if reg == "desert" else "minecraft:blackstone", BODY_DEF)
    else:
        land(area, x, z, t, SURF[reg], UNDER.get(reg, UNDER_DEF), UNDER.get(reg, BODY_DEF) if reg == "desert" else BODY_DEF)
    low = min(TOP.get((x + 1, z), 55), TOP.get((x - 1, z), 55), TOP.get((x, z + 1), 55), TOP.get((x, z - 1), 55))
    for y in range(LAND_BOTTOM, low - 2):
        area.clear(x, y, z)

ROAD_MIX = area.mix({"gravel": 3, "blackstone": 3, "smooth_basalt": 2, "cobbled_deepslate": 1, "polished_blackstone_bricks": 1})
NEAR_ROAD = set()
for (x, z), (d, w, ph) in ROADD.items():
    if d <= 4.5:
        NEAR_ROAD.add((x, z))
    if d <= 2.0 and (x, z) in TOP:
        S(x, TOP[(x, z)], z, ROAD_MIX(x, TOP[(x, z)], z, 3))


def stand(x, z):
    return TOP[(x, z)] + 1


def reserved(x, z):
    if (x, z) in NEAR_ROAD:
        return True
    for (x1, z1, x2, z2) in ((-296, -216, -186, -114), (-160, -102, -106, -58), (-236, -62, -154, 12),
                             (-300, 164, -186, 236), (-140, 90, -106, 130)):
        if x1 <= x <= x2 and z1 <= z <= z2:
            return True
    if math.hypot(x - DUKE_C[0], z - DUKE_C[1]) < 26:
        return True
    for (mx, mz, mr, mh) in MESAS:
        if math.hypot(x - mx, z - mz) < mr * 1.4:
            return True
    return False


# ================================================================ helpers
def lamp_post(x, y, z, h=3, fence="crimson_fence", lamp="lantern[hanging=false]"):
    F(x, y, z, x, y + h - 1, z, fence)
    S(x, y + h, z, lamp)


def brazier(x, y, z, soul=False):
    S(x, y, z, "polished_blackstone_brick_wall")
    S(x, y + 1, z, "soul_campfire[lit=true,facing=north]" if soul else "campfire[lit=true,facing=north]")


def banner_pole(x, y, z, h=6, color="red", face="east", pole="stripped_crimson_stem[axis=y]"):
    F(x, y, z, x, y + h - 1, z, pole)
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[face]
    S(x + dx, y + h - 2, z + dz, f"{color}_wall_banner[facing={face}]")
    S(x + dx, y + h - 3, z + dz, f"{color}_wall_banner[facing={face}]") if False else None
    S(x, y + h, z, "lantern[hanging=false]" if h > 4 else pole)


def bed(x, y, z, facing, color="red"):
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[facing]
    S(x, y, z, f"{color}_bed[facing={facing},part=foot]")
    S(x + dx, y, z + dz, f"{color}_bed[facing={facing},part=head]")


def slab_ramp(path, y_base, slab, solid, width=3, clear=4, floor=None):
    """A ramp of slabs rising 0.5 per sample (walkable without jumping). path = [(px, pz, ux, uz)] (ux, uz: the sideways
    unit vector); the surface of sample k is y_base + 0.5 + 0.5 k. Fills solid under it down to `floor` (int or callable)."""
    for k, (px, pz, ux, uz) in enumerate(path):
        cell = y_base + k // 2
        typ = "bottom" if k % 2 == 0 else "top"
        for w in range(-(width // 2), width // 2 + 1):
            x, z = int(round(px + ux * w)), int(round(pz + uz * w))
            for y in range(cell + 1, cell + clear + 1):
                S(x, y, z, "air")
            S(x, cell, z, f"{slab}[type={typ}]")
            fl = floor(x, z) if callable(floor) else floor
            if fl is not None:
                for y in range(cell - 1, fl, -1):
                    if not area.solid(x, y, z):
                        S(x, y, z, solid)


def building(x1, z1, x2, z2, y0, h, wall, corner, stair, slab, gable, door, floor, *, doorwood="crimson", win="iron_bars",
             beam="stripped_crimson_stem", lamp="lantern", windows=True):
    """A walled hall with a gabled roof (ridge along its long side), a door (side, coordinate), windows, beams and lamps."""
    along_x = (x2 - x1) >= (z2 - z1)
    dc = None
    if door:
        side, pos = door
        dc = {"n": (pos, z1), "s": (pos, z2), "e": (x2, pos), "w": (x1, pos)}[side]
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            c = x in (x1, x2) and z in (z1, z2)
            S(x, y0, z, wall if edge else floor)
            for y in range(y0 + 1, y0 + h + 1):
                if c:
                    b = corner
                elif edge:
                    wcell = (x - x1) % 4 == 2 if z in (z1, z2) else (z - z1) % 4 == 2
                    b = win if (windows and y == y0 + 2 and wcell and (x, z) != dc and abs(x - (dc or (0, 0))[0]) + abs(z - (dc or (0, 0))[1]) > 1) else wall
                else:
                    b = "air"
                S(x, y, z, b)
    if door:
        face = {"n": "north", "s": "south", "e": "east", "w": "west"}[side]
        S(dc[0], y0 + 1, dc[1], f"{doorwood}_door[facing={face},half=lower,hinge=left]")
        S(dc[0], y0 + 2, dc[1], f"{doorwood}_door[facing={face},half=upper,hinge=left]")
        S(dc[0], y0, dc[1], floor)
        ox, oz = {"n": (0, -1), "s": (0, 1), "e": (1, 0), "w": (-1, 0)}[side]
        sx, sz = (1, 0) if side in "ns" else (0, 1)
        for s in (-2, 2):
            lamp_post(dc[0] + ox + sx * s, y0 + 1, dc[1] + oz + sz * s, 2, "nether_brick_fence", "lantern[hanging=false]")
    # gabled roof
    if along_x:
        u1, u2, v1, v2 = x1, x2, z1, z2
    else:
        u1, u2, v1, v2 = z1, z2, x1, x2
    lo_face, hi_face = ("south", "north") if along_x else ("east", "west")

    def put(u, v, y, b):
        if along_x:
            S(u, y, v, b)
        else:
            S(v, y, u, b)
    i = 0
    while True:
        vl, vh = v1 - 1 + i, v2 + 1 - i
        y = y0 + h + 1 + i
        if vl > vh:
            break
        if vl == vh:
            for u in range(u1 - 1, u2 + 2):
                put(u, vl, y, f"{slab}[type=bottom]")
            break
        for u in range(u1 - 1, u2 + 2):
            put(u, vl, y, f"{stair}[facing={lo_face},half=bottom]")
            put(u, vh, y, f"{stair}[facing={hi_face},half=bottom]")
        for u in (u1, u2):
            for v in range(vl + 1, vh):
                put(u, v, y, gable)
        i += 1
    ridge_y = y0 + h + 1 + i
    # beams and lamps
    ax = "z" if along_x else "x"
    for u in range(u1 + 2, u2 - 1, 4):
        for v in range(v1 + 1, v2):
            put(u, v, y0 + h, f"{beam}[axis={ax}]")
        for v in (v1 + (v2 - v1) // 4 + 1, (v1 + v2) // 2, v2 - (v2 - v1) // 4 - 1):
            put(u, v, y0 + h - 1, f"{lamp}[hanging=true]")
    return ridge_y


def tower(x1, z1, x2, z2, y0, h, block, accent, door_side="w", banner=True):
    """A hollow watch tower with a ladder to a crenellated platform (stand at y0 + h + 1)."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            for y in range(y0 + 1, y0 + h + 2):
                if edge:
                    corner = x in (x1, x2) and z in (z1, z2)
                    S(x, y, z, accent if corner and y % 3 else block)
                else:
                    S(x, y, z, "air")
            S(x, y0, z, block)
            if y0 + h < 400 and not edge:
                S(x, y0 + h, z, "dark_oak_planks")
            if edge and (x + z) % 2 == 0:
                S(x, y0 + h + 2, z, "polished_blackstone_brick_wall")
    S(x1 + 1, y0 + h, z1 + 1, "air")
    for y in range(y0 + 1, y0 + h + 2):
        S(x1 + 1, y, z1 + 1, "ladder[facing=east]")
    S(x1, y0 + h + 1, z1 + 1, block)
    mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
    dc = {"w": (x1, mz, "west"), "e": (x2, mz, "east"), "n": (mx, z1, "north"), "s": (mx, z2, "south")}[door_side]
    S(dc[0], y0 + 1, dc[1], f"crimson_door[facing={dc[2]},half=lower,hinge=left]")
    S(dc[0], y0 + 2, dc[1], f"crimson_door[facing={dc[2]},half=upper,hinge=left]")
    S(mx + 1, y0 + h - 1, mz, "lantern[hanging=true]")
    S(mx + 1, y0 + h, mz, "dark_oak_planks")
    S(x1 + 1, y0 + h, z1 + 1, "air")
    # lantern & brazier on the roof
    S(mx, y0 + h + 1, mz, "campfire[lit=true,facing=north]")
    if banner:
        banner_pole(x2 + 1, y0 + 2, mz, h=h - 1, face="east")


def ruin(x1, z1, x2, z2, y_from, y_to, p, seed=0):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(y_from, y_to + 1):
                if area.get(x, y, z) not in (None, "minecraft:air") and hash01(x, y, z, seed) < p * (0.4 + 0.6 * (y - y_from) / max(1, y_to - y_from)):
                    S(x, y, z, "air")


def spike_cluster(x, z, seed):
    y = TOP.get((x, z))
    if y is None or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        return
    n = 2 + int(hash01(x, z, 1, seed) * 3)
    for i in range(n):
        dx, dz = int(hash01(x, z, i + 2, seed) * 5) - 2, int(hash01(x, z, i + 9, seed) * 5) - 2
        yy = TOP.get((x + dx, z + dz))
        if yy is None:
            continue
        ht = 2 + int(hash01(x + dx, z + dz, 3, seed) * 4)
        for k in range(1, ht + 1):
            S(x + dx, yy + k, z + dz, "basalt[axis=y]")
        S(x + dx, yy + ht + 1, z + dz, "pointed_dripstone[vertical_direction=up,thickness=tip]")


def ribcage(x, z, rot):
    y = TOP.get((x, z))
    if y is None:
        return
    ax = "z" if rot else "x"
    for i in range(-3, 4):
        px, pz = (x, z + i) if rot else (x + i, z)
        S(px, y + 1, pz, f"bone_block[axis={ax}]")
        if i % 2 == 0:
            for s in (-1, 1):
                qx, qz = (x + s, pz) if rot else (px, z + s)
                S(qx, y + 1, qz, "bone_block[axis=y]")
                S(qx, y + 2, qz, "bone_block[axis=y]")
    S(x + (0 if rot else 4), y + 1, z + (4 if rot else 0), "skeleton_skull[rotation=%d]" % (4 if rot else 12))


def sign_at(x, y, z, rot, lines):
    sign(area, x, y, z, rot, lines)


# ================================================================ Dragontail
Y = TOWN_Y
X1, Z1, X2, Z2 = TOWN_WALL
for (x, z) in ((-240, -165), (-200, -165), (-270, -195), (-210, -135)):
    assert TOP[(x, z)] == Y, ("plateau", x, z, TOP[(x, z)])

TOWN_G = area.mix({"blackstone": 4, "gravel": 2, "coarse_dirt": 1, "cobbled_deepslate": 1, "basalt": 1})
STREET = area.mix({"polished_blackstone": 4, "polished_blackstone_bricks": 3, "cracked_polished_blackstone_bricks": 1})
WALLB = area.mix({"polished_blackstone_bricks": 5, "blackstone": 3, "cracked_polished_blackstone_bricks": 1, "red_nether_bricks": 1})
for x in range(X1 + 3, X2 - 2):
    for z in range(Z1 + 3, Z2 - 2):
        S(x, Y, z, TOWN_G(x, Y, z, 2))
        for y in range(Y + 1, Y + 6):
            S(x, y, z, "air")
        if (-167 <= z <= -163) or (-242 <= x <= -238):
            S(x, Y, z, STREET(x, Y, z, 5))
# the walls
for x in range(X1, X2 + 1):
    for z in range(Z1, Z2 + 1):
        dd = min(x - X1, X2 - x, z - Z1, Z2 - z)
        if dd > 2:
            continue
        for y in range(Y + 1, Y + 6):
            S(x, y, z, WALLB(x, y, z, 1))
        S(x, Y, z, "blackstone")
        if dd == 0:
            S(x, Y + 6, z, "polished_blackstone_brick_wall") if (x + z) % 2 == 0 else None
        elif dd == 2:
            S(x, Y + 5, z, "polished_blackstone_bricks")
            S(x, Y + 6, z, "air")
# gate (east wall)
air(X2 - 2, Y + 1, -168, X2, Y + 4, -162)
for z in (-169, -161):
    F(X2 - 2, Y + 1, z, X2, Y + 5, z, "chiseled_polished_blackstone")
F(X2 - 2, Y + 5, -168, X2, Y + 5, -162, "polished_blackstone_bricks")
for z in range(-168, -161):
    S(X2, Y + 4, z, "iron_bars")
for z in (-170, -160):
    brazier(X2 + 2, Y + 1, z)
    brazier(X2 - 4, Y + 1, z)
# stairs to the wall walk (inside, north & south walls)
for (sx, front, d) in ((-265, Z1 + 3, 1), (-216, Z1 + 3, 1), (-262, Z2 - 3, -1), (-216, Z2 - 3, -1)):
    for i in range(5):
        z = front + d * (4 - i) if False else front + d * i
        S(sx, Y + 5 - i, z, f"polished_blackstone_brick_stairs[facing={'north' if d == 1 else 'south'},half=bottom]")
        for y in range(Y + 1, Y + 5 - i):
            S(sx, y, z, "polished_blackstone_bricks")
        air(sx, Y + 6 - i, z, sx, Y + 9 - i, z)
# towers
tower(X2 - 5, -176, X2 + 1, -170, Y, 12, "polished_blackstone_bricks", "chiseled_polished_blackstone", "w")
tower(X2 - 5, -160, X2 + 1, -154, Y, 12, "polished_blackstone_bricks", "chiseled_polished_blackstone", "w")
for (tx1, tz1, tx2, tz2, ds) in ((X1, Z1, X1 + 6, Z1 + 6, "e"), (X2 - 6, Z1, X2, Z1 + 6, "w"), (X1, Z2 - 6, X1 + 6, Z2, "e"),
                                 (X2 - 6, Z2 - 6, X2, Z2, "w")):
    tower(tx1, tz1, tx2, tz2, Y, 9, "blackstone", "crimson_stem[axis=y]", ds, banner=False)
    cx, cz = (tx1 + tx2) // 2, (tz1 + tz2) // 2
    F(cx, Y + 12, cz, cx, Y + 15, cz, "crimson_fence")
    S(cx, Y + 16, cz, "red_banner[rotation=0]")
# spiked stakes outside the walls
for x in range(X1 - 3, X2 + 4):
    for z in range(Z1 - 3, Z2 + 4):
        if not (x in (X1 - 3, X2 + 3) or z in (Z1 - 3, Z2 + 3)):
            continue
        if (x + z) % 2 or (x == X2 + 3 and -171 <= z <= -159) or (x, z) not in TOP:
            continue
        if abs(TOP[(x, z)] - Y) > 1:
            continue
        y = TOP[(x, z)]
        F(x, y + 1, z, x, y + 2, z, "stripped_crimson_stem[axis=y]")
        S(x, y + 3, z, "pointed_dripstone[vertical_direction=up,thickness=tip]")
# the square
CX, CZ = TOWN_C
for x in range(CX - 13, CX + 14):
    for z in range(CZ - 13, CZ + 14):
        d = math.hypot(x - CX, z - CZ)
        if d > 13:
            continue
        if 5.5 <= d < 6.5 or 10.5 <= d < 11.5:
            S(x, Y, z, "red_nether_bricks" if (x + z) % 3 else "chiseled_nether_bricks")
        elif d < 4:
            S(x, Y, z, "polished_blackstone")
        else:
            S(x, Y, z, "polished_blackstone_bricks" if (x + z) % 4 else "cracked_polished_blackstone_bricks")
# the great pyre / monument
F(CX - 1, Y + 1, CZ - 1, CX + 1, Y + 1, CZ + 1, "blackstone")
F(CX, Y + 2, CZ, CX, Y + 7, CZ, "polished_blackstone_bricks")
S(CX, Y + 8, CZ, "chiseled_polished_blackstone")
S(CX, Y + 9, CZ, "campfire[lit=true,facing=north]")
for dx, dz, f in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
    S(CX + dx, Y + 6, CZ + dz, f"red_wall_banner[facing={f}]")
    S(CX + dx * 3, Y + 1, CZ + dz * 3, "magma_block")
    S(CX + dx * 2, Y + 1, CZ + dz * 2, "polished_blackstone_slab[type=bottom]")
for k in range(8):
    a = k / 8 * math.tau + 0.3
    brazier(int(round(CX + math.cos(a) * 9.5)), Y + 1, int(round(CZ + math.sin(a) * 9.5)))
for k in range(4):
    a = k / 4 * math.tau + 0.8
    banner_pole(int(round(CX + math.cos(a) * 12)), Y + 1, int(round(CZ + math.sin(a) * 12)), h=7,
                face="east" if math.cos(a) > 0 else "west", color="orange" if k % 2 else "red")
sign_at(CX + 3, Y + 1, CZ + 5, 0, ["Dragontail", "Barbarian Faction", ""])
area.warp("Dragontail", CX + 5, Y + 1, CZ + 2, yaw=90.0)

# building styles
ST_LONG = dict(wall="polished_blackstone_bricks", corner="stripped_crimson_stem[axis=y]", stair="crimson_stairs", slab="crimson_slab",
               gable="crimson_planks", floor="dark_oak_planks")
ST_BANK = dict(wall="polished_blackstone_bricks", corner="chiseled_polished_blackstone", stair="polished_blackstone_brick_stairs",
               slab="polished_blackstone_brick_slab", gable="polished_blackstone_bricks", floor="polished_blackstone")
ST_RED = dict(wall="red_nether_bricks", corner="stripped_crimson_stem[axis=y]", stair="nether_brick_stairs", slab="nether_brick_slab",
              gable="red_nether_bricks", floor="dark_oak_planks")
ST_WOOD = dict(wall="crimson_planks", corner="stripped_crimson_stem[axis=y]", stair="dark_oak_stairs", slab="dark_oak_slab",
               gable="dark_oak_planks", floor="dark_oak_planks")
ST_FORGE = dict(wall="blackstone", corner="chiseled_polished_blackstone", stair="blackstone_stairs", slab="blackstone_slab",
                gable="blackstone", floor="polished_blackstone")
ST_HOUSE = dict(wall="dark_oak_planks", corner="stripped_dark_oak_log[axis=y]", stair="red_nether_brick_stairs",
                slab="red_nether_brick_slab", gable="dark_oak_planks", floor="spruce_planks")


def B(x1, z1, x2, z2, h, style, door, **kw):
    s = style
    return building(x1, z1, x2, z2, Y, h, s["wall"], s["corner"], s["stair"], s["slab"], s["gable"], door, s["floor"], **kw)


# Chief's Longhouse
ridge = B(-262, -203, -218, -188, 6, ST_LONG, ("s", -240))
for x in (-254, -246, -234, -226):
    S(x, Y, -193, "blackstone")
    S(x, Y + 1, -193, "campfire[lit=true,facing=north]")
for x in range(-257, -224):
    if not -243 <= x <= -237:
        for z in (-197, -190):
            S(x, Y + 1, z, "dark_oak_slab[type=top]")
for x in (-257, -224):
    for z in (-197, -190):
        S(x, Y + 1, z, "barrel[facing=up]")
S(-240, Y + 1, -201, "crimson_stairs[facing=north,half=bottom]")
S(-241, Y + 1, -201, "crimson_slab[type=bottom]")
S(-239, Y + 1, -201, "crimson_slab[type=bottom]")
F(-240, Y + 2, -202, -240, Y + 4, -202, "crimson_planks")
for x in (-244, -236):
    S(x, Y + 3, -202, "red_wall_banner[facing=south]")
S(-240, Y + 5, -202, "wither_skeleton_skull[rotation=8]")
for x in range(-260, -219, 8):
    S(x, Y + 1, -201, "chest[facing=south]") if x % 16 == 0 else S(x, Y + 1, -201, "barrel[facing=up]")
area.npc("Chief Scorn", -240, Y + 1, -199, 0.0, "in the Chief's Longhouse, before his throne")
# the longhouse ridge: ladder up the east gable and the hidden Fairy Soul on the roof
for y in range(Y + 1, Y + 15):
    S(-217, y, -196, "ladder[facing=east]")
air(-217, Y + 15, -196, -217, Y + 15, -195)
S(-217, Y + 15, -196, "air")
area.fairy_soul(-222, ridge, -196)

# Bank
B(-283, -184, -267, -170, 5, ST_BANK, ("e", -177), win="iron_bars")
for z in range(-182, -171):
    S(-276, Y + 1, z, "polished_blackstone")
    S(-276, Y + 2, z, "iron_bars" if not -178 <= z <= -176 else "air")
for z in (-182, -180, -174, -172):
    S(-281, Y + 1, z, "chest[facing=east]")
F(-282, Y + 1, -181, -281, Y + 2, -181, "gold_block")
F(-282, Y + 1, -173, -281, Y + 2, -173, "gold_block")
S(-282, Y + 1, -177, "iron_block")
area.npc("Banker (Dragontail)", -279, Y + 1, -177, -90.0, "behind the bank counter")
sign_at(-264, Y + 1, -175, 4, ["Bank", "Dragontail"])
# Auction House
B(-224, -184, -208, -170, 5, ST_RED, ("w", -177))
for z in (-182, -180, -178, -176, -174, -172):
    S(-212, Y + 1, z, "polished_blackstone_slab[type=bottom]")
    S(-212, Y + 2, z, "gold_block") if z in (-180, -174) else None
S(-215, Y + 1, -177, "lectern[facing=west]")
for z in (-182, -172):
    S(-221, Y + 1, z, "barrel[facing=up]")
    S(-221, Y + 2, z, "red_banner[rotation=4]")
area.npc("Auction Master (Dragontail)", -213, Y + 1, -177, 90.0, "behind the auction podium")
sign_at(-226, Y + 1, -173, 4, ["Auction House"])
# Bazaar
B(-224, -160, -208, -148, 5, ST_WOOD, ("w", -154))
for z in (-158, -150):
    for x in range(-221, -211, 2):
        S(x, Y + 1, z, ["barrel[facing=up]", "hay_block[axis=y]", "chest[facing=south]", "composter[level=3]", "crafting_table"][((x + z) // 2) % 5])
for z in range(-157, -150):
    S(-212, Y + 1, z, "dark_oak_slab[type=top]")
area.npc("Bazaar (Dragontail)", -214, Y + 1, -154, 90.0, "behind the bazaar counter")
sign_at(-226, Y + 1, -150, 4, ["Bazaar"])
# the Blacksmith's forge
B(-283, -150, -265, -134, 6, ST_FORGE, ("n", -274))
for z in (-147, -145, -143):
    S(-281, Y + 1, z, "blast_furnace[facing=east,lit=true]")
S(-281, Y + 1, -141, "smithing_table")
S(-281, Y + 1, -139, "grindstone[face=floor,facing=east]")
S(-281, Y + 1, -137, "lava_cauldron")
S(-279, Y + 1, -137, "lava_cauldron")
for x in range(-280, -267, 2):
    S(x, Y + 1, -136, "water_cauldron" if False else "cauldron")
S(-276, Y + 1, -143, "anvil[facing=east]")
S(-278, Y + 1, -143, "anvil[facing=north]")
for x in range(-279, -270):
    for z in (-147, -146):
        S(x, Y, z, "magma_block" if hash01(x, z, 1, 7) < 0.3 else "polished_blackstone")
F(-282, Y + 1, -149, -281, Y + 14, -148, "blackstone")
S(-282, Y + 14, -148, "campfire[lit=true,facing=north]")
air(-282, Y + 1, -149, -282, Y + 5, -149)
area.npc("Igor", -276, Y + 1, -141, 180.0, "at the anvil in the forge")
sign_at(-272, Y + 1, -152, 8, ["Blacksmith", "Igor's Forge"])
# houses: Pomtair, Sirih, Suus
for name, x1, door_x in (("Pomtair", -258, -254), ("Sirih", -234, -230), ("Suus", -224, -220)):
    B(x1, -143, x1 + 8, -136, 4, ST_HOUSE, ("n", door_x))
    bed(x1 + 1, Y + 1, -138, "east")
    S(x1 + 7, Y + 1, -142, "barrel[facing=up]")
    S(x1 + 7, Y + 1, -141, "crafting_table")
    area.npc(name, door_x, Y + 1, -140, 180.0, "quest giver, in his house")
# barracks
for (bx1, bx2, dx_) in ((-283, -267, -275), (-214, -200, -207)):
    B(bx1, -203, bx2, -190, 5, ST_RED, ("s", dx_))
    for x in range(bx1 + 2, bx2 - 1, 3):
        bed(x, Y + 1, -202, "south")
        S(x + 1, Y + 1, -202, "chest[facing=south]") if x % 2 else None
    for x in range(bx1 + 2, bx2 - 2, 3):
        bed(x, Y + 1, -192, "north") if False else None
    S(bx1 + 2, Y + 1, -194, "barrel[facing=up]")
    S(bx2 - 2, Y + 1, -194, "hay_block[axis=y]")
# the emissary's booth (south-east of the square)
for (bx, bz) in ((-233, -157), (-229, -157), (-233, -153), (-229, -153)):
    F(bx, Y + 1, bz, bx, Y + 3, bz, "stripped_crimson_stem[axis=y]")
F(-234, Y + 4, -158, -228, Y + 4, -152, "red_wool")
for x in range(-234, -227):
    S(x, Y + 3, -158, "orange_carpet") if x % 2 else None
S(-231, Y + 1, -156, "barrel[facing=up]")
S(-231, Y + 3, -155, "lantern[hanging=true]")
area.npc("Barbarian Emissary", -231, Y + 1, -155, 180.0, "in the booth south-east of the square (sells Kuudra keys)")
# training ring
for x in range(-216, -199):
    for z in range(-141, -126):
        d = math.hypot(x + 208, z + 134)
        if d <= 6.2:
            S(x, Y, z, "coarse_dirt" if d < 5.2 else "red_nether_bricks")
            if d >= 5.2:
                S(x, Y + 1, z, "nether_brick_fence") if (x + z) % 2 == 0 else None
for (x, z) in ((-212, -134), (-204, -134), (-208, -138)):
    S(x, Y + 1, z, "hay_block[axis=y]")
    S(x, Y + 2, z, "hay_block[axis=y]")
    S(x, Y + 3, z, "carved_pumpkin[facing=south]")
    S(x - 1, Y + 2, z, "oak_fence")
    S(x + 1, Y + 2, z, "oak_fence")
for (x, z) in ((-214, -130), (-202, -130)):
    S(x, Y + 1, z, "grindstone[face=floor,facing=north]")
    lamp_post(x, Y + 1, z + 1, 3)
area.npc("Kaus", -208, Y + 1, -134, 0.0, "a fighter in the training ring")
# little extras: market crates and lamp posts along the streets
for x in range(-278, -200, 12):
    for z in (-168, -162):
        if (x - CX) ** 2 + (z - CZ) ** 2 > 18 ** 2:
            lamp_post(x, Y + 1, z, 3, "nether_brick_fence")
for z in range(-200, -128, 12):
    for x in (-243, -237):
        if (x - CX) ** 2 + (z - CZ) ** 2 > 16 ** 2 and not (-203 <= z <= -187):
            lamp_post(x, Y + 1, z, 3, "nether_brick_fence")

# ================================================================ the Barbarian Outpost
Y = OUT_Y
OX1, OX2, OZ1, OZ2 = -150, -116, -94, -68
assert all(TOP[(x, z)] == Y for x in (-150, -116, -133) for z in (-94, -68, -80))
CAMP = area.mix({"coarse_dirt": 4, "gravel": 2, "blackstone": 1, "dirt_path": 2})
for x in range(OX1, OX2 + 1):
    for z in range(OZ1, OZ2 + 1):
        edge = x in (OX1, OX2) or z in (OZ1, OZ2)
        if edge:
            gate = (x in (OX1, OX2) and -83 <= z <= -77)
            if gate:
                S(x, Y, z, ROAD_MIX(x, Y, z, 3))
                continue
            ht = 5 + (1 if hash01(x, 1, z, 3) < 0.3 else 0)
            F(x, Y + 1, z, x, Y + ht, z, "dark_oak_log[axis=y]")
            S(x, Y + ht + 1, z, "pointed_dripstone[vertical_direction=up,thickness=tip]")
        else:
            S(x, Y, z, CAMP(x, Y, z, 1) if not -82 <= z <= -78 else ROAD_MIX(x, Y, z, 3))
            air(x, Y + 1, z, x, Y + 6, z)
for x in (OX1, OX2):
    for z in (-84, -76):
        F(x, Y + 1, z, x, Y + 8, z, "stripped_crimson_stem[axis=y]")
        S(x, Y + 9, z, "campfire[lit=true,facing=north]")
        S(x + (1 if x == OX2 else -1), Y + 6, z, f"red_wall_banner[facing={'east' if x == OX2 else 'west'}]")
sign_at(OX2 + 2, Y + 1, -85, 4, ["Barbarian Outpost", "Dragontail 150m"])


def tent(x1, z1, color, y0, flip=False):
    for x in range(x1, x1 + 7):
        for i, (za, zb) in enumerate(((z1, z1 + 4), (z1 + 1, z1 + 3))):
            S(x, y0 + 1 + i, za, f"{color}_wool")
            S(x, y0 + 1 + i, zb, f"{color}_wool")
        S(x, y0 + 3, z1 + 2, f"{color}_wool")
        for z in range(z1 + 1, z1 + 4):
            S(x, y0 + 1, z, "air") if True else None
    for z in range(z1, z1 + 5):
        S(x1, y0 + 1 + min(z - z1, z1 + 4 - z), z, f"{color}_wool") if False else None
    S(x1 + 1, y0 + 1, z1 + 2, "chest[facing=south]")
    bed(x1 + 3, y0 + 1, z1 + 1, "east", "red")
    S(x1 + 5, y0 + 1, z1 + 3, "barrel[facing=up]")
    S(x1 + 3, y0 + 2, z1 + 2, "lantern[hanging=true]") if False else None
    banner_pole(x1 - 1, y0 + 1, z1 + 2, h=5, face="west", color="orange" if color == "red" else "red")


tent(-147, -91, "red", Y)
tent(-139, -91, "brown", Y)
tent(-131, -91, "red", Y)
tent(-147, -73, "brown", Y)
tent(-138, -73, "red", Y)
tent(-128, -73, "brown", Y)
for (x, z) in ((-133, -85), (-133, -75)):
    S(x, Y, z, "blackstone")
    S(x, Y + 1, z, "campfire[lit=true,facing=north]")
    S(x - 2, Y + 1, z, "hay_block[axis=x]")
    S(x + 2, Y + 1, z, "oak_stairs[facing=west,half=bottom]")
tower(-125, -93, -121, -89, Y, 12, "dark_oak_planks", "stripped_crimson_stem[axis=y]", "s")
tower(-146, -73, -142, -69, Y, 10, "dark_oak_planks", "stripped_crimson_stem[axis=y]", "n", banner=False)
area.fairy_soul(-122, Y + 12 + 1, -90)
for x in range(-146, -118, 7):
    S(x, Y + 1, -72, "barrel[facing=up]") if False else None
S(-120, Y + 1, -70, "barrel[facing=up]")
S(-120, Y + 2, -70, "barrel[facing=up]")
S(-122, Y + 1, -70, "chest[facing=north]")
for z in (-88, -86):
    S(-145, Y + 1, z, "fletching_table") if z == -88 else S(-145, Y + 1, z, "grindstone[face=floor,facing=east]")

# ================================================================ the Dukedom
Y = DUKE_Y
DX1, DX2, DZ1, DZ2 = -289, -261, -32, -4
assert all(TOP[(x, z)] == Y for x in (-289, -261, -275) for z in (-32, -4, -18))
DUKE_F = area.mix({"polished_blackstone_bricks": 4, "cracked_polished_blackstone_bricks": 3, "blackstone": 2, "gilded_blackstone": 1,
                   "cobbled_deepslate": 1})
for x in range(DX1, DX2 + 1):
    for z in range(DZ1, DZ2 + 1):
        S(x, Y, z, DUKE_F(x, Y, z, 6))
        air(x, Y + 1, z, x, Y + 8, z)
        if min(x - DX1, DX2 - x, z - DZ1, DZ2 - z) <= 1:
            hh = 2 + int(6 * hash01(x, 5, z, 8) ** 0.8)
            if hash01(x, 6, z, 8) < 0.1:
                hh = 1
            if z == DZ2 and -277 <= x <= -273:
                continue
            if z == DZ2 - 1 and -277 <= x <= -273:
                continue
            for y in range(Y + 1, Y + hh + 1):
                S(x, y, z, WALLB(x, y, z, 9))
ridge = building(-284, -30, -266, -19, Y, 6, "polished_blackstone_bricks", "stripped_crimson_stem[axis=y]",
                 "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_bricks",
                 ("s", -275), "polished_blackstone", windows=True)
ruin(-285, -31, -265, -18, Y + 2, Y + ridge - Y + 1, 0.55, seed=3)
S(-275, Y + 1, -28, "crimson_stairs[facing=north,half=bottom]")
S(-276, Y + 1, -28, "crimson_slab[type=bottom]")
S(-274, Y + 1, -28, "crimson_slab[type=bottom]")
F(-275, Y + 2, -29, -275, Y + 4, -29, "polished_blackstone_bricks")
for (cx, cz) in ((DX1, DZ1), (DX2 - 5, DZ1), (DX1, DZ2 - 5), (DX2 - 5, DZ2 - 5)):
    hh = 14 if (cx, cz) == (DX2 - 5, DZ1) else 8
    for x in range(cx, cx + 6):
        for z in range(cz, cz + 6):
            edge = x in (cx, cx + 5) or z in (cz, cz + 5)
            for y in range(Y + 1, Y + hh + 1):
                if edge:
                    if (cx, cz) == (DX2 - 5, DZ1) or hash01(x, y, z, 12) > 0.15 + 0.6 * (y - Y) / hh * 0.9:
                        S(x, y, z, WALLB(x, y, z, 9))
                else:
                    S(x, y, z, "air")
# the intact north-east tower gets a ladder and a floor
tx1, tz1 = DX2 - 5, DZ1
for y in range(Y + 1, Y + 14):
    S(tx1 + 1, y, tz1 + 1, "ladder[facing=east]")
F(tx1 + 1, Y + 14, tz1 + 1, tx1 + 4, Y + 14, tz1 + 4, "dark_oak_planks")
air(tx1 + 1, Y + 14, tz1 + 1, tx1 + 1, Y + 14, tz1 + 1)
S(tx1 + 3, Y + 15, tz1 + 3, "soul_campfire[lit=true,facing=north]")
S(tx1 + 5, Y + 1, tz1 + 3, "air")
S(tx1, Y + 4, tz1 + 3, "air")
S(tx1, Y + 5, tz1 + 3, "air")
area.fairy_soul(tx1 + 2, Y + 15, tz1 + 2)
# gate ruin and rubble, broken pillars, tattered banners
for x in (-278, -272):
    F(x, Y + 1, DZ2, x, Y + 9, DZ2, "chiseled_polished_blackstone")
S(-277, Y + 9, DZ2, "polished_blackstone_brick_stairs[facing=east,half=bottom]")
for k in range(22):
    x, z = int(DX1 + 2 + hash01(k, 1, 1, 21) * 24), int(DZ1 + 14 + hash01(k, 2, 1, 21) * 12)
    if -277 <= x <= -273 and z > -8:
        continue
    ht = 1 + int(hash01(k, 3, 1, 21) * 5)
    if k % 3 == 0:
        F(x, Y + 1, z, x, Y + ht, z, "polished_blackstone_bricks")
        S(x, Y + ht + 1, z, "polished_blackstone_brick_slab[type=bottom]")
    else:
        S(x, Y + 1, z, "cobbled_deepslate" if k % 2 else "blackstone")
for (x, z) in ((-281, -9), (-269, -9), (-285, -14), (-265, -14)):
    banner_pole(x, Y + 1, z, h=6, face="east" if x < -275 else "west")
for (x, z) in ((-282, -12), (-268, -12), (-275, -8)):
    S(x, Y + 1, z, "soul_campfire[lit=true,facing=north]")
    S(x, Y, z, "blackstone")
area.npc("spawn:Barbarian Duke X", -275, Y + 1, -12, 0.0, "the Duke's open yard (room for a big mob)")
sign_at(-279, Y + 1, -2, 12, ["The Dukedom", "Beware"])
for k in range(10):
    ang = k / 10 * math.tau
    x, z = int(DUKE_C[0] + math.cos(ang) * 22), int(DUKE_C[1] + math.sin(ang) * 22)
    if (x, z) in TOP and not (z > -8 and -280 < x < -270):
        spike_cluster(x, z, k)

# ================================================================ the Smoldering Tomb
FY = 62                       # crypt floor (players stand at 63)
CRYPT_W = area.mix({"polished_blackstone_bricks": 6, "cracked_polished_blackstone_bricks": 2, "blackstone": 2, "soul_soil": 1,
                    "chiseled_polished_blackstone": 1})
CRYPT_F = area.mix({"polished_blackstone": 3, "polished_blackstone_bricks": 3, "soul_soil": 1, "cracked_polished_blackstone_bricks": 1})


def crypt_room(x1, z1, x2, z2, h):
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            for y in range(FY, FY + h + 2):
                S(x, y, z, CRYPT_W(x, y, z, 4))
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            S(x, FY, z, CRYPT_F(x, FY, z, 5))
            air(x, FY + 1, z, x, FY + h, z)


def sarcophagus(x, z, along_x=True, skull=True):
    for i in range(3):
        px, pz = (x + i, z) if along_x else (x, z + i)
        S(px, FY + 1, pz, "polished_blackstone_slab[type=bottom]" if i else "polished_blackstone")
        S(px, FY + 2, pz, "polished_blackstone_slab[type=bottom]")
    if skull:
        px, pz = (x, z) if along_x else (x, z)
        S(px, FY + 3, pz, "wither_skeleton_skull[rotation=%d]" % (4 if along_x else 8))


HALL = (-213, -42, -177, -14)
crypt_room(HALL[0], HALL[1], HALL[2], HALL[3], 8)
for (x1, z1, x2, z2) in ((-229, -34, -219, -22), (-171, -34, -161, -22), (-209, -57, -199, -48), (-191, -57, -181, -48)):
    crypt_room(x1, z1, x2, z2, 6)
crypt_room(-218, -29, -214, -27, 4)
crypt_room(-176, -29, -172, -27, 4)
crypt_room(-205, -47, -203, -43, 4)
crypt_room(-187, -47, -185, -43, 4)
# join the corridors to the hall (the hall's wall cells at the openings)
air(-214, FY + 1, -29, -214, FY + 4, -27)
air(-176, FY + 1, -29, -176, FY + 4, -27)
air(-205, FY + 1, -43, -203, FY + 4, -43)
air(-187, FY + 1, -43, -185, FY + 4, -43)
air(-218, FY + 1, -29, -218, FY + 4, -27)
air(-172, FY + 1, -29, -172, FY + 4, -27)
# hall: pillars, lanterns, coffins, the dais
for x in (-205, -185):
    for z in (-38, -31, -24, -18):
        F(x, FY + 1, z, x, FY + 8, z, "polished_blackstone_bricks")
        S(x, FY + 1, z, "chiseled_polished_blackstone")
        S(x, FY + 8, z, "chiseled_polished_blackstone")
        S(x + 1, FY + 1, z, "soul_lantern[hanging=false]")
for x in (-211, -195, -179):
    for z in (-40, -34, -28, -22, -16):
        if area.get(x, FY + 8, z) in (None, "minecraft:air"):
            S(x, FY + 8, z, "soul_lantern[hanging=true]")
for z in range(-40, -15, 4):
    for x in (-212, -178):
        S(x, FY + 1, z, "polished_blackstone_slab[type=bottom]")
        S(x, FY + 1, z + 1, "polished_blackstone_slab[type=bottom]")
        S(x, FY + 2, z, "wither_skeleton_skull[rotation=%d]" % (4 if x < -195 else 12))
for dz in range(3):
    for x in range(-201, -190):
        S(x, FY + 1, -42 + dz, "polished_blackstone_brick_stairs[facing=south,half=bottom]" if dz == 2 else "polished_blackstone_bricks")
F(-199, FY + 2, -42, -193, FY + 2, -41, "polished_blackstone_bricks")
sarcophagus(-198, -41, True)
sarcophagus(-196, -42, True, False)
S(-197, FY + 3, -41, "crying_obsidian")
for (x, z) in ((-201, -41), (-191, -41)):
    S(x, FY + 2, z, "soul_campfire[lit=true,facing=south]")
    S(x, FY + 1, z, "blackstone")
for x in range(-210, -178):
    for z in range(-41, -15):
        if hash01(x, 33, z, 4) < 0.012 and area.get(x, FY + 1, z) == "minecraft:air":
            S(x, FY + 1, z, "cobweb")
        elif hash01(x, 34, z, 4) < 0.006 and area.get(x, FY + 1, z) == "minecraft:air":
            S(x, FY + 1, z, "bone_block[axis=x]")
# the entrance stair: a trench from the entrance building, then a tunnel into the hall's south end
for i in range(11):
    z, y = -4 - i, TOMB_Y - i
    for dx in range(-3, 4):
        x = -195 + dx
        for yy in range(y - 1, FY - 1, -1):
            if yy < y:
                S(x, yy, z, "polished_blackstone_bricks") if yy >= y - 1 else None
        S(x, y, z, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
        air(x, y + 1, z, x, max(y + 5, TOMB_Y + 3) if i < 5 else y + 6, z)
    for wx in (-199, -191):
        F(wx, FY, z, wx, TOMB_Y + 1, z, CRYPT_W(wx, y, z, 4))
        if i < 5:
            S(wx, TOMB_Y + 2, z, "nether_brick_fence")
    if i >= 5:
        for dx in range(-3, 4):
            S(-195 + dx, y + 7 if y + 7 <= TOMB_Y else TOMB_Y, z, "polished_blackstone_bricks") if y + 7 <= 71 else None
    if i in (6, 9):
        S(-198, y + 2, z, "soul_lantern[hanging=false]")
        S(-192, y + 2, z, "soul_lantern[hanging=false]")
        S(-198, y + 1, z, "polished_blackstone_brick_wall")
        S(-192, y + 1, z, "polished_blackstone_brick_wall")
for dx in (-4, 4):          # the arch where the trench meets the tunnel
    F(-195 + dx, TOMB_Y - 5 + 1, -9, -195 + dx, TOMB_Y + 3, -9, "chiseled_nether_bricks")
    S(-195 + dx, TOMB_Y + 4, -9, "soul_lantern[hanging=false]")
for dx in range(-4, 5):
    S(-195 + dx, TOMB_Y - 5 + 6 if False else TOMB_Y + 3, -9, "chiseled_nether_bricks")
air(-198, FY + 1, -14, -192, FY + 7, -13)
# the entrance building
Y = TOMB_Y
TOMB_B = dict(wall="polished_blackstone_bricks", corner="chiseled_nether_bricks", stair="polished_blackstone_brick_stairs",
              slab="polished_blackstone_brick_slab", gable="polished_blackstone_bricks", floor="polished_blackstone")
ridge = building(-203, -3, -187, 7, Y, 6, TOMB_B["wall"], TOMB_B["corner"], TOMB_B["stair"], TOMB_B["slab"], TOMB_B["gable"],
                 ("s", -195), TOMB_B["floor"], doorwood="warped", windows=False, lamp="soul_lantern")
air(-196, Y + 1, -3, -194, Y + 4, -3)
S(-195, Y + 5, -3, "chiseled_nether_bricks")
for x in (-200, -190):
    F(x, Y + 1, 0, x, Y + 3, 0, "chiseled_nether_bricks")
    S(x, Y + 4, 0, "wither_skeleton_skull[rotation=%d]" % (0 if x < -195 else 8))
for z in (1, 5):
    for x in (-201, -189):
        S(x, Y + 1, z, "soul_campfire[lit=true,facing=north]")
        S(x, Y, z, "soul_soil")
for x in range(-201, -188):
    for z in range(-2, 7):
        if hash01(x, 7, z, 14) < 0.12 and area.get(x, Y + 1, z) == "minecraft:air":
            S(x, Y, z, "soul_soil")
S(-195, Y + 1, 3, "skeleton_skull[rotation=0]")
S(-195, Y, 3, "chiseled_polished_blackstone")
sign_at(-192, Y + 1, 9, 8, ["Smoldering Tomb", "Wither Spectres within"])
for (x, z) in ((-205, 4), (-185, 4), (-205, -1), (-185, -1)):
    lamp_post(x, Y + 1, z, 3, "nether_brick_fence", "soul_lantern[hanging=false]")
area.warp("Smoldering Tomb", -195, stand(-195, 12), 12, yaw=180.0)
# the side tombs
for (x1, z1, x2, z2, towards) in ((-229, -34, -219, -22, "east"), (-171, -34, -161, -22, "west")):
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    sarcophagus(cx - 1, cz, True)
    for dz in (-3, 3):
        sarcophagus(cx - 1, cz + dz, True)
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        S(x, FY + 1, z, "soul_campfire[lit=true,facing=north]")
        S(x, FY, z, "blackstone")
    S(cx, FY + 1, z1, "chest[facing=south]")
    S(cx, FY + 6, cz, "soul_lantern[hanging=true]")
for (x1, z1, x2, z2) in ((-209, -57, -199, -48), (-191, -57, -181, -48)):
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    sarcophagus(cx - 1, cz, True)
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        S(x, FY + 1, z, "soul_lantern[hanging=false]")
    S(cx, FY + 6, cz + 1, "soul_lantern[hanging=true]")
    for x in range(x1, x2 + 1):
        S(x, FY + 5, z1, "cobweb") if x % 3 == 0 else None
area.fairy_soul(-186, FY + 1, -56)          # the north-east tomb's far corner, behind the sarcophagus

# ================================================================ the Forgotten Skull
Y = SKULL_Y
assert TOP[(SKX, 175)] == Y and TOP[(SKX, 195)] == Y, (TOP.get((SKX, 175)), TOP.get((SKX, 195)))
CY = 79
SK_B = area.mix({"bone_block": 14, "white_concrete": 3, "light_gray_concrete": 2, "calcite": 1})


def e_val(x, y, z, cx, cy, cz, rx, ry, rz):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2


for x in range(SKX - 32, SKX + 33):
    for y in range(Y - 4, CY + 24):
        for z in range(SKZ - 27, SKZ + 28):
            if e_val(x, y, z, SKX, CY, SKZ, 30, 22, 25) <= 1.0:
                if (x, z) not in TOP:
                    continue
                if e_val(x, y, z, SKX, CY, SKZ, 26, 18, 21) > 1.0 or y <= Y:
                    S(x, y, z, SK_B(x, y, z, 3) if y > Y or hash01(x, y, z, 8) < 0.5 else "blackstone")
                else:
                    S(x, y, z, "air")
# cheekbones and brow
for sx in (-1, 1):
    for x in range(SKX + sx * 24, SKX + sx * 5, -sx):
        for y in range(Y, Y + 14):
            for z in range(SKZ - 28, SKZ - 12):
                if e_val(x, y, z, SKX + sx * 14, 72, SKZ - 17, 9, 7, 8) <= 1.0 and area.get(x, y, z) in (None, "minecraft:air"):
                    S(x, y, z, SK_B(x, y, z, 4))
for x in range(SKX - 20, SKX + 21):
    for z in range(SKZ - 27, SKZ - 18):
        for y in (92, 93):
            if e_val(x, y, z, SKX, CY, SKZ, 30, 22, 25) <= 1.0 and e_val(x, y, z, SKX, CY, SKZ, 27, 19, 22) > 1.0 and area.get(x, y, z) in (None, "minecraft:air"):
                S(x, y, z, "bone_block[axis=x]")
# eye sockets (pierce the front into the hall) and the nose
EYE_Y = 87
for sx in (-1, 1):
    ex = SKX + sx * 12
    for x in range(ex - 6, ex + 7):
        for y in range(EYE_Y - 5, EYE_Y + 6):
            if ((x - ex) / 5.5) ** 2 + ((y - EYE_Y) / 4.5) ** 2 <= 1.0:
                for z in range(SKZ - 30, SKZ - 12):
                    if e_val(x, y, z, SKX, CY, SKZ, 26, 18, 21) <= 1.0 or e_val(x, y, z, SKX, CY, SKZ, 30, 22, 25) <= 1.0:
                        if area.get(x, y, z) not in (None,):
                            S(x, y, z, "air")
for x in range(SKX - 2, SKX + 3):
    for y in range(75, 80):
        if abs(x - SKX) <= (79 - y) // 2 + 0:
            for z in range(SKZ - 28, SKZ - 18):
                if area.get(x, y, z) not in (None, "minecraft:air"):
                    S(x, y, z, "air")
# the mouth (the entrance to Kuudra's Hollow)
for x in range(SKX - 6, SKX + 7):
    for y in range(Y + 1, Y + 10):
        for z in range(SKZ - 32, SKZ - 12):
            if area.get(x, y, z) not in (None, "minecraft:air") or (x, z) in TOP and z < SKZ - 20:
                S(x, y, z, "air")
for x in range(SKX - 7, SKX + 8):
    for y in range(Y + 1, Y + 11):
        if abs(x - SKX) == 7 or y == Y + 10:
            for z in range(SKZ - 28, SKZ - 22):
                if area.get(x, y, z) not in (None, "minecraft:air"):
                    S(x, y, z, "bone_block[axis=y]" if abs(x - SKX) == 7 else "bone_block[axis=x]")
# teeth
for x in range(SKX - 6, SKX + 7, 2):
    for z in range(SKZ - 26, SKZ - 23):
        F(x, Y + 8, z, x, Y + 9, z, "quartz_block")
        S(x, Y + 7, z, "quartz_stairs[facing=north,half=top]") if False else None
for x in (SKX - 6, SKX - 4, SKX + 4, SKX + 6):
    for z in range(SKZ - 26, SKZ - 24):
        F(x, Y + 1, z, x, Y + 3, z, "quartz_block")
        S(x, Y + 4, z, "quartz_slab[type=bottom]")
# the hall inside: floor, pillars, glow
for x in range(SKX - 24, SKX + 25):
    for z in range(SKZ - 22, SKZ + 22):
        if area.get(x, Y + 1, z) == "minecraft:air" and (x, z) in TOP:
            if abs(x - SKX) <= 3 and z < SKZ + 8:
                S(x, Y, z, "polished_blackstone_bricks")
            elif abs(x - SKX) == 4 and z < SKZ + 8:
                S(x, Y, z, "crying_obsidian" if hash01(x, 1, z, 3) < 0.3 else "polished_blackstone")
            else:
                S(x, Y, z, "blackstone" if hash01(x, 2, z, 3) < 0.6 else "magma_block" if hash01(x, 3, z, 3) < 0.15 else "basalt")
for z in range(SKZ - 18, SKZ + 10, 7):
    for sx in (-6, 6):
        F(SKX + sx, Y + 1, z, SKX + sx, Y + 6, z, "polished_blackstone_bricks")
        S(SKX + sx, Y + 7, z, "chiseled_polished_blackstone")
        S(SKX + sx + (1 if sx < 0 else -1), Y + 5, z, "soul_lantern[hanging=false]") if False else None
        S(SKX + sx + (1 if sx < 0 else -1), Y + 1, z, "soul_campfire[lit=true,facing=north]")
for sx in (-1, 1):
    S(SKX + sx * 12, EYE_Y - 1, SKZ - 18, "shroomlight") if sx > 0 else None
    for dy in (0, 1):
        S(SKX + 12, EYE_Y - 1 + dy, SKZ - 20, "shroomlight")
        S(SKX + 13, EYE_Y - 1 + dy, SKZ - 20, "shroomlight")
# the spiral stair up to the west eye (slabs, half a block per step)
path = []
theta = math.radians(-14)
for k in range(30):
    yk = 68 + k // 2
    sc = 0.78 * math.sqrt(max(0.05, 1 - ((yk - CY) / 18) ** 2))
    px, pz = SKX + 26 * sc * math.cos(theta), SKZ + 21 * sc * math.sin(theta)
    rr = math.hypot(px - SKX, pz - SKZ)
    ux, uz = (px - SKX) / rr, (pz - SKZ) / rr
    path.append((px, pz, ux, uz))
    theta -= 1.0 / max(8.0, rr)
slab_ramp(path, Y + 1, "quartz_slab", "bone_block", width=3, clear=4, floor=Y)
last = path[-1]
lx, lz = int(round(last[0])), int(round(last[1]))
for x in range(SKX - 15, SKX - 9):
    for z in range(SKZ - 21, SKZ - 12):
        if e_val(x, 83, z, SKX, CY, SKZ, 26, 18, 21) <= 1.0 or z < SKZ - 14:
            S(x, 82, z, "polished_blackstone_bricks" if (x + z) % 2 else "quartz_block")
            air(x, 83, z, x, 85, z) if (x, z) != (SKX - 12, SKZ - 19) else None
# the eye socket floor (so the soul can be reached) and a lantern
for x in range(SKX - 14, SKX - 10):
    for z in range(SKZ - 21, SKZ - 15):
        S(x, 82, z, "quartz_block")
S(SKX - 12, 83, SKZ - 16, "soul_lantern[hanging=false]")
area.fairy_soul(SKX - 12, 83, SKZ - 19)
area.spots["notes"].append(f"Forgotten Skull: centre ({SKX}, {SKZ}), faces north, mouth x{SKX - 6}..{SKX + 6} y{Y + 1}..{Y + 9}; "
                           f"spiral stair inside from the hall's east side to the west eye (platform y82); Fairy Soul at ({SKX - 12}, 83, {SKZ - 19})")
# plaza in front of the skull
PL = area.mix({"polished_blackstone_bricks": 6, "cracked_polished_blackstone_bricks": 2, "polished_blackstone": 2, "red_nether_bricks": 1})
for x in range(-266, -204):
    for z in range(167, 182):
        if (x, z) in TOP and TOP[(x, z)] == Y:
            S(x, Y, z, PL(x, Y, z, 2))
            air(x, Y + 1, z, x, Y + 6, z) if False else None
for x in range(-264, -207, 8):
    brazier(x, Y + 1, 168)
    brazier(x, Y + 1, 180) if abs(x - SKX) > 8 else None
for x in (-262, -208):
    banner_pole(x, Y + 1, 174, h=8, face="east" if x < SKX else "west", color="red")
# Vulcan's stall (Crimson Essence)
for (bx, bz) in ((-252, 170), (-246, 170), (-252, 174), (-246, 174)):
    F(bx, Y + 1, bz, bx, Y + 3, bz, "stripped_crimson_stem[axis=y]")
F(-253, Y + 4, 169, -245, Y + 4, 175, "nether_brick_slab[type=bottom]")
F(-251, Y + 1, 170, -247, Y + 1, 170, "polished_blackstone")
S(-249, Y + 1, 174, "blast_furnace[facing=north,lit=true]")
S(-251, Y + 1, 174, "anvil[facing=east]")
S(-247, Y + 1, 174, "lava_cauldron")
S(-249, Y + 3, 172, "lantern[hanging=true]")
S(-249, Y + 4, 172, "nether_brick_slab[type=bottom]")
area.npc("Vulcan", -249, Y + 1, 172, 180.0, "behind his stall on the plaza (Crimson Essence shop)")
area.npc("Kuudra Gatekeeper", SKX + 9, Y + 1, 179, 200.0, "by the mouth of the skull")
area.npc("spawn:Kuudra", SKX, Y + 1, SKZ - 22, 180.0, "standing in the mouth's entrance (the mod puts a portal there)")
area.warp("Kuudra Passage", SKX - 2, Y + 1, 173, yaw=0.0)
sign_at(SKX + 6, Y + 1, 171, 0, ["Forgotten Skull", "Kuudra's Hollow", "Beware"])

# ================================================================ the Wasteland, the Desert: sulphur, bones, spikes, trees
def free_cell(x, z):
    y = TOP.get((x, z))
    return y is not None and not reserved(x, z) and area.get(x, y + 1, z) in (None, "minecraft:air")


def sulphur(n, region_name, seed, box):
    placed = 0
    for i in range(n * 6):
        if placed >= n:
            break
        x = int(box[0] + hash01(i, 1, 0, seed) * (box[2] - box[0]))
        z = int(box[1] + hash01(i, 2, 0, seed) * (box[3] - box[1]))
        if REG.get((x, z)) != region_name or TOP.get((x, z), 0) < 63 or not free_cell(x, z):
            continue
        placed += 1
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dz * dz <= 4 and hash01(x + dx, z + dz, i, seed) < 0.7:
                    xx, zz = x + dx, z + dz
                    if (xx, zz) in TOP and not reserved(xx, zz) and TOP[(xx, zz)] > 62:
                        S(xx, TOP[(xx, zz)], zz, "sponge")
                        if hash01(xx, zz, i, seed + 1) < 0.5:
                            S(xx, TOP[(xx, zz)] + 1, zz, "sponge")
    return placed


n1 = sulphur(70, "waste", 5, (-254, 15, -112, 55))
n2 = sulphur(70, "desert", 7, (-316, 70, -116, 156))

for (x, z) in sorted(TOP):
    y = TOP[(x, z)]
    reg = REG[(x, z)]
    if reserved(x, z) or y < 63:
        continue
    r = hash01(x, 71, z, 9)
    if reg == "waste":
        if r < 0.0030:
            spike_cluster(x, z, 3)
        elif r < 0.0036:
            ribcage(x, z, int(hash01(x, 72, z) * 2))
        elif r < 0.0056 and free_cell(x, z):
            area.dead_tree(x, y + 1, z, height=4 + int(hash01(x, 73, z) * 4), log="dark_oak_log", seed=x)
        elif r < 0.0125 and free_cell(x, z):
            S(x, y + 1, z, "bone_block[axis=%s]" % ("x" if (x + z) % 2 else "z"))
        elif r < 0.0145 and free_cell(x, z):
            S(x, y + 1, z, "skeleton_skull[rotation=%d]" % int(hash01(x, 74, z) * 16))
    elif reg == "desert":
        if r < 0.05 and free_cell(x, z) and area.get(x, y, z) in ("minecraft:red_sand", "minecraft:terracotta"):
            S(x, y + 1, z, "dead_bush")
        elif 0.05 <= r < 0.0525 and free_cell(x, z):
            area.dead_tree(x, y + 1, z, height=4 + int(hash01(x, 75, z) * 3), log="stripped_dark_oak_log", seed=x)
        elif 0.0525 <= r < 0.0535:
            ribcage(x, z, int(hash01(x, 76, z) * 2))
        elif 0.0535 <= r < 0.0555 and free_cell(x, z):
            S(x, y + 1, z, "bone_block[axis=x]")
    elif reg == "dragon":
        if r < 0.0035 and free_cell(x, z):
            area.dead_tree(x, y + 1, z, height=5 + int(hash01(x, 77, z) * 5), log="crimson_stem", seed=x)
        elif r < 0.0055:
            spike_cluster(x, z, 5)
        elif r < 0.06 and free_cell(x, z) and area.get(x, y, z) == "minecraft:crimson_nylium":
            S(x, y + 1, z, "crimson_roots")
    elif reg in ("duke", "skull"):
        if r < 0.003:
            spike_cluster(x, z, 7)
        elif r < 0.0036:
            ribcage(x, z, int(hash01(x, 78, z) * 2))
        elif r < 0.012 and free_cell(x, z):
            S(x, y + 1, z, "bone_block[axis=x]")

# ---------------------------------------------------------------- mesas: the stair up the soul mesa, arches, obelisks
mx, mz, mr, mh = MESAS[SOUL_MESA]
msurf = TOP[(mx, mz)]
ang0 = math.radians(95)
K = int(2 * (msurf + 1 - (TOP[(mx, mz + int(mr * 1.1) + 3)] + 1.5))) + 2
mpath = []
th = ang0
for k in range(K):
    R = (mr * 1.1 + 3) - (mr * 0.5 + 3) * k / K
    px, pz = mx + R * math.cos(th), mz + R * math.sin(th)
    mpath.append((px, pz, math.cos(th), math.sin(th)))
    th += 1.0 / max(6.0, R)
x0, z0 = int(round(mpath[0][0])), int(round(mpath[0][1]))
slab_ramp(mpath, TOP[(x0, z0)] + 1, "red_sandstone_slab", "red_sandstone", width=3, clear=4,
          floor=lambda x, z: TOP.get((x, z), 60))
area.fairy_soul(mx, msurf + 1, mz)
area.spots["notes"].append(f"Soul mesa centre ({mx}, {mz}) top y{msurf}; a slab ramp spirals up it from its south side ({x0}, {z0})")
# an arch with a stair up it (Fairy Soul on the lintel)
ax_, az_ = -170, 84
g = TOP[(ax_, az_)]
for px in (ax_, ax_ + 6):
    F(px, g + 1, az_, px, g + 7, az_, "cut_red_sandstone")
    F(px, g + 1, az_ + 1, px, g + 7, az_ + 1, "cut_red_sandstone")
F(ax_ - 1, g + 7, az_, ax_ + 7, g + 7, az_ + 1, "chiseled_red_sandstone")
air(ax_ + 1, g + 1, az_, ax_ + 5, g + 6, az_ + 1)
for x in range(ax_ - 1, ax_ + 8):
    S(x, g + 8, az_, "red_sandstone_wall") if x in (ax_ - 1, ax_ + 7) else None
rp = [(ax_ - 14 + k, az_ + 2, 0, 1) for k in range(14)]
slab_ramp(rp, g + 1, "red_sandstone_slab", "red_sandstone", width=3, clear=4, floor=lambda x, z: TOP.get((x, z), 60))
F(ax_ - 1, g + 7, az_ + 2, ax_ + 7, g + 7, az_ + 3, "red_sandstone")
area.fairy_soul(ax_ + 3, g + 8, az_)
# a shrine in the Wasteland (basalt ring) and obelisks in the desert
shx, shz = -140, 40
sg = TOP[(shx, shz)]
for k in range(8):
    a = k / 8 * math.tau
    px, pz = int(round(shx + math.cos(a) * 5)), int(round(shz + math.sin(a) * 5))
    yy = TOP[(px, pz)]
    F(px, yy + 1, pz, px, yy + 6 + k % 2, pz, "basalt[axis=y]")
    S(px, yy + 7 + k % 2, pz, "soul_lantern[hanging=false]")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        S(shx + dx, sg, shz + dz, "chiseled_polished_blackstone" if dx or dz else "crying_obsidian")
S(shx, sg + 1, shz, "soul_campfire[lit=true,facing=north]")
area.fairy_soul(shx + 2, TOP[(shx + 2, shz)] + 1, shz + 2)
for (ox, oz) in ((-205, 120), (-240, 100), (-130, 80), (-285, 150), (-175, 155)):
    og = TOP.get((ox, oz))
    if og is None or reserved(ox, oz):
        continue
    for h_ in range(8):
        w_ = 1 if h_ < 4 else 0
        for dx in range(-w_, w_ + 1):
            for dz in range(-w_, w_ + 1):
                S(ox + dx, og + 1 + h_, oz + dz, "cut_red_sandstone" if h_ % 3 else "chiseled_red_sandstone")
    S(ox, og + 9, oz, "red_sandstone_wall")
    S(ox, og + 10, oz, "lantern[hanging=false]")

# ================================================================ lamp posts along the roads, bridges
for i in range(0, len(road_pts), 17):
    px, pz, name = road_pts[i]
    ix, iz = int(round(px)), int(round(pz))
    dx, dz = 0, 0
    nx = road_pts[min(i + 1, len(road_pts) - 1)]
    vx, vz = nx[0] - px, nx[1] - pz
    ln = math.hypot(vx, vz) or 1
    sx, sz = int(round(-vz / ln * 3)), int(round(vx / ln * 3))
    if i % 34 == 0:
        sx, sz = -sx, -sz
    lx_, lz_ = ix + sx, iz + sz
    if (lx_, lz_) in TOP and not (TOWN_WALL[0] - 4 <= lx_ <= TOWN_WALL[2] + 4 and TOWN_WALL[1] - 4 <= lz_ <= TOWN_WALL[3] + 4):
        yy = TOP[(lx_, lz_)]
        if area.get(lx_, yy + 1, lz_) in (None, "minecraft:air"):
            lamp_post(lx_, yy + 1, lz_, 3, "nether_brick_fence", "lantern[hanging=false]" if i % 3 else "soul_lantern[hanging=false]")

for cz in BRIDGES:
    bridge_half(area, -112, -101, cz)
    for dz in range(-3, 4):
        S(-101, BRIDGE + 1, cz + dz, "air") if False else None
sign_at(-108, BRIDGE + 1, -84, 4, ["North Bridge", "to the Centre"])
sign_at(-108, BRIDGE + 1, 105, 4, ["South Bridge", "to the Centre"])

# ================================================================ fairy souls already placed: skull eye, longhouse ridge, tomb,
# duke tower, outpost tower, soul mesa, desert arch, wasteland shrine
# ================================================================ zones
SULPHUR = {"minecraft:sponge": {"becomes": "minecraft:bedrock", "regrow": 30}}
SAND_SULPHUR = {"minecraft:red_sand": {"becomes": "minecraft:bedrock", "regrow": 8}, "minecraft:sponge": {"becomes": "minecraft:bedrock", "regrow": 30}}
LO, HI = 40, 189
area.zone("Dragontail", box=((-310, LO, -230), (-175, HI, -100)))
area.zone("Barbarian Outpost", box=((-152, LO, -97), (-112, HI, -63)))
area.zone("The Dukedom", box=((-325, LO, -62), (-259, HI, 22)), mobs=[
    mob("Kada Knight", 3, 15, (-288, DUKE_Y + 1, -30), (-262, DUKE_Y + 10, -6))])
area.zone("Smoldering Tomb", box=((-230, LO, -60), (-160, HI, 10)), mobs=[
    mob("Wither Spectre", 6, 8, (-212, FY + 1, -41), (-178, FY + 7, -15)),
    mob("Wither Skeleton", 6, 8, (-228, FY + 1, -56), (-162, FY + 6, -15))])
area.zone("The Wasteland", box=((-258, LO, 11), (-108, HI, 59)), blocks=SULPHUR, mobs=[
    mob("Kada Knight", 4, 12, (-250, 62, 15), (-115, 90, 55))])
area.zone("Burning Desert", box=((-325, LO, 60), (-108, HI, 160)), blocks=SAND_SULPHUR, mobs=[
    mob("Flaming Spider", 8, 6, (-310, 62, 70), (-120, 95, 155))])
area.zone("Forgotten Skull", box=((-300, LO, 161), (-170, HI, 245)))
area.save()

# ---------------------------------------------------------------- my own colour map (out/crimson_west_map.png)
try:
    from PIL import Image
    from kit import OUT
    COL = [("crimson_nylium", (160, 45, 55)), ("netherrack", (115, 45, 45)), ("blackstone", (50, 45, 55)), ("polished", (70, 65, 78)),
           ("basalt", (85, 85, 92)), ("soul_soil", (95, 70, 55)), ("soul_sand", (110, 85, 65)), ("gravel", (140, 135, 130)),
           ("red_sand", (205, 110, 45)), ("red_sandstone", (190, 100, 45)), ("terracotta", (165, 90, 60)), ("sponge", (235, 225, 70)),
           ("bone", (235, 230, 205)), ("concrete", (225, 225, 225)), ("quartz", (240, 235, 225)), ("planks", (110, 60, 50)),
           ("nether_brick", (80, 25, 30)), ("log", (90, 55, 40)), ("stem", (120, 40, 60)), ("wool", (190, 60, 50)),
           ("magma", (230, 100, 20)), ("campfire", (255, 160, 40)), ("lantern", (255, 210, 120)), ("coarse_dirt", (110, 85, 65)),
           ("deepslate", (60, 60, 65)), ("calcite", (225, 225, 220)), ("path", (150, 125, 80)), ("slab", (120, 110, 110)),
           ("stairs", (120, 110, 110)), ("wall", (90, 85, 90)), ("obsidian", (50, 30, 80))]
    w, _, d = area.size
    img = Image.new("RGB", (w * 3, d * 3), (225, 80, 20))
    tops = {}
    for (x, y, z), (entry, _) in area.piece.cells.items():
        n = entry["id"].split(":")[1]
        if n == "air":
            continue
        if (x, z) not in tops or y > tops[(x, z)][0]:
            tops[(x, z)] = (y, n)
    for (x, z), (y, n) in tops.items():
        base = (150, 150, 150)
        for key, c in COL:
            if key in n:
                base = c
                break
        k = 0.7 + 0.3 * (y - 56) / 50
        c = tuple(max(0, min(255, int(v * k))) for v in base)
        for i in range(3):
            for j in range(3):
                img.putpixel((x * 3 + i, z * 3 + j), c)
    img.save(OUT / "crimson_west_map.png")
except ImportError:
    pass
print("sulphur clusters:", n1, n2, "| road cells:", len(NEAR_ROAD))
