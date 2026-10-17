#!/usr/bin/env python3
"""Khazad-dum (hollows_khazad_dum, x -40..40, z 120..200, y 20..64 of the Crystal Hollows): Bal's lair in the Magma Fields,
a vast dark dwarven hall carved out of the rock: deepslate-tile floors, basalt and blackstone pillars, lava channels and a
lava river with a broken bridge, a huge central arena ringed by a lava moat (Bal appears there), a throne dais at the far
end, two colossi at the entrance, a side alcove with a Fairy Soul and tunnels out to the four box faces.

    python tools/builds/hub_areas/hollows_khazad_dum.py
"""
import math
import numpy as np
from kit import Area, hash01, noise

OX, OY, OZ = -40, 20, 120
NX, NY, NZ = 80, 44, 80
area = Area("hollows_khazad_dum", (OX, OY, OZ), (NX, NY, NZ), version=1,
            dimension="skyrpg_skyblock:crystal_hollows", island="Crystal Hollows", shell=2)
# ---------------------------------------------------------------- the box: rock, carved, built
X1, Y1, Z1 = OX + NX, OY + NY, OZ + NZ          # exclusive maxima
AIR = "minecraft:air"
rock = np.ones((NX, NY, NZ), dtype=bool)         # True = natural rock (Hard Stone), False = open
F = {}                                           # (x, y, z) -> block: everything built (overrides the rock)
SIGNS = {}                                       # (x, y, z) -> (block, data)
XS = np.arange(OX, X1)[:, None, None]
YS = np.arange(OY, Y1)[None, :, None]
ZS = np.arange(OZ, Z1)[None, None, :]


def inb(x, y, z):
    return OX <= x < X1 and OY <= y < Y1 and OZ <= z < Z1


def put(x, y, z, block):
    if inb(x, y, z):
        F[(x, y, z)] = block if ":" in block.split("[")[0] else "minecraft:" + block


def fill(x1, y1, z1, x2, y2, z2, block):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                put(x, y, z, block)


def air(x, y, z):
    if inb(x, y, z):
        rock[x - OX, y - OY, z - OZ] = False
        F.pop((x, y, z), None)


def air_box(x1, y1, z1, x2, y2, z2):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                air(x, y, z)


def is_rock(x, y, z):
    return inb(x, y, z) and rock[x - OX, y - OY, z - OZ] and (x, y, z) not in F


def blk(x, y, z):
    b = F.get((x, y, z))
    if b is not None:
        return b
    if not inb(x, y, z):
        return "minecraft:stone"
    return "minecraft:stone" if rock[x - OX, y - OY, z - OZ] else AIR


def sign(x, y, z, rot, lines):
    msgs = ['""'] + ['"%s"' % t.replace("'", "\'") for t in lines] + ['""'] * 3
    SIGNS[(x, y, z)] = ("dark_oak_sign[rotation=%d]" % rot,
                        {"id": "minecraft:sign", "front_text": {"messages": msgs[:4], "color": "white", "has_glowing_text": 1},
                         "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def rot_facing(fx, fz):
    """Sign rotation (0..15) for a sign whose front faces the direction (fx, fz)."""
    return int(round((math.atan2(-fx, fz) % math.tau) / (math.tau / 16))) % 16


def corridor(p, q, fy, rad=1.9, lamp=6, floor=None, h=4, lamp_block="minecraft:sea_lantern"):
    """A tunnel from p to q ((x, z) points), floor blocks at y fy, h of headroom, lamps in the ceiling. Clipped to the box."""
    floor = floor or CORR_FLOOR
    (x1, z1), (x2, z2) = p, q
    n = max(2, int(math.hypot(x2 - x1, z2 - z1) * 2))
    seen = set()
    for i in range(n + 1):
        cx, cz = x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                x, z = int(round(cx)) + dx, int(round(cz)) + dz
                if (x, z) in seen or math.hypot(x - cx, z - cz) > rad + 0.4:
                    continue
                seen.add((x, z))
                if not inb(x, fy, z):
                    continue
                put(x, fy, z, floor(x, fy, z))
                for y in range(fy + 1, fy + h + 1):
                    air(x, y, z)
        if i % (lamp * 2) == lamp:
            lx, lz = int(round(cx)), int(round(cz))
            if is_rock(lx, fy + h + 1, lz):
                put(lx, fy + h + 1, lz, lamp_block)


def nonsolid(b):
    n = b.split(":")[1].split("[")[0]
    return n == "air" or n.endswith(("lantern", "rod", "vine", "sign", "plate", "carpet", "button", "torch", "fern", "grass", "flower",
                                     "cluster", "candle", "banner", "lichen", "head", "roots", "petals", "chain", "ladder")) and \
        not n.endswith("sea_lantern")


def standable(x, y, z):
    b0 = blk(x, y - 1, z)
    return nonsolid(blk(x, y, z)) and nonsolid(blk(x, y + 1, z)) and not nonsolid(b0) and "lava" not in b0 and "iron_bars" not in b0


def reach(start, limit=2_000_000):
    """Walkable cells reachable from start = (x, y, z) feet position (steps up/down of 1, drops of up to 1)."""
    from collections import deque
    seen = {start}
    dq = deque([start])
    while dq and len(seen) < limit:
        x, y, z = dq.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                n = (x + dx, y + dy, z + dz)
                if n in seen or not standable(*n):
                    continue
                if dy == 1 and not nonsolid(blk(x, y + 2, z)):
                    continue
                seen.add(n)
                dq.append(n)
            for dist, dys in ((2, (0, -1, 1)), (3, (0, -1))):      # jumps over 1-2 block gaps in a straight line
                for dy in dys:
                    n = (x + dx * dist, y + dy, z + dz * dist)
                    if n in seen or not standable(*n):
                        continue
                    if all(nonsolid(blk(x + dx * k, y + yy, z + dz * k)) for k in range(1, dist) for yy in (0, 1, 2 if dy == 1 else 1)):
                        seen.add(n)
                        dq.append(n)
    return seen


ROCK = area.mix({"stone": 72, "andesite": 14, "tuff": 10, "cobblestone": 0, "calcite": 0})
_ents = {}


def write_all():
    """Rock where it's still rock, built blocks over it, air elsewhere; signs with their data; then the kit's save."""
    import dungeon_kit
    from kit import mc

    def ent(b):
        e = _ents.get(b)
        if e is None:
            e = _ents[b] = dungeon_kit.state(mc(b))
        return e
    cells = area.piece.cells
    air_e = ent(AIR)
    for xi in range(NX):
        x = OX + xi
        for zi in range(NZ):
            z = OZ + zi
            col = rock[xi, :, zi]
            for yi in range(NY):
                y = OY + yi
                b = F.get((x, y, z))
                if b is None:
                    cells[(xi, yi, zi)] = (ent(ROCK(x, y, z)) if col[yi] else air_e, None)
                else:
                    cells[(xi, yi, zi)] = (ent(b), None)
    for (x, y, z), (b, data) in SIGNS.items():
        area.set(x, y, z, b, data)

FL = 24                                    # the hall's floor block (players stand at 25)
HX, HZ = 0, 160                            # the hall's and arena's centre
AR = 15                                    # the arena's radius
TILES = area.mix({"deepslate_tiles": 5, "polished_deepslate": 2, "cracked_deepslate_tiles": 2, "deepslate_bricks": 1})
DARK = area.mix({"polished_blackstone_bricks": 5, "blackstone": 2, "cracked_polished_blackstone_bricks": 2, "polished_blackstone": 1})
CORR_FLOOR = DARK
ROCK = area.mix({"stone": 62, "andesite": 10, "tuff": 8, "blackstone": 12, "basalt": 8})


def ceil_y(x, z):
    q = (x - HX) ** 2 / 35.0 ** 2 + (z - HZ) ** 2 / 36.0 ** 2
    return int(44 + 14 * math.sqrt(max(0.0, 1 - q)) + 1.5 * noise(x, z, 5, 2.0))


# ---------------------------------------------------------------- the hall
for x in range(HX - 36, HX + 37):
    for z in range(HZ - 37, HZ + 38):
        sx, sz = abs(x - HX) / 35.0, abs(z - HZ) / 36.0
        if sx ** 6 + sz ** 6 > 1.0:
            continue
        put(x, FL, z, TILES(x, FL, z, 2))
        for y in range(FL + 1, ceil_y(x, z) + 1):
            air(x, y, z)


def hall(x, z):
    return (abs(x - HX) / 35.0) ** 6 + (abs(z - HZ) / 36.0) ** 6 <= 1.0


def lava_cell(x, z):
    for y in (FL - 3, FL - 2, FL - 1):
        put(x, y, z, "minecraft:lava")
    put(x, FL - 4, z, "minecraft:blackstone")


# ---------------------------------------------------------------- the arena: tiles, rings, spokes, moat and four crossings
for x in range(HX - AR - 8, HX + AR + 9):
    for z in range(HZ - AR - 8, HZ + AR + 9):
        dx, dz = x - HX, z - HZ
        r = math.hypot(dx, dz)
        ang = math.degrees(math.atan2(dz, dx)) % 360
        if r <= AR:
            b = TILES(x, FL, z, 4)
            if r <= 2.6:
                b = "minecraft:gilded_blackstone" if r > 1.4 else "minecraft:crying_obsidian"
            elif abs(r - 6) < 0.6 or abs(r - 11) < 0.6:
                b = "minecraft:chiseled_deepslate"
            elif any(abs((ang - a + 180) % 360 - 180) < 3.5 for a in range(0, 360, 45)) and r > 3:
                b = "minecraft:polished_blackstone_bricks"
            elif r > AR - 1.2:
                b = "minecraft:chiseled_polished_blackstone"
            put(x, FL, z, b)
        elif r <= AR + 3.2:
            cross = min(abs(dx), abs(dz)) <= 2.2
            if cross:
                put(x, FL, z, "minecraft:polished_blackstone_bricks" if min(abs(dx), abs(dz)) < 2 else "minecraft:chiseled_polished_blackstone")
            else:
                lava_cell(x, z)
                put(x, FL, z, "minecraft:air")
                air(x, FL, z)
        elif r <= AR + 4.6 and hall(x, z):
            put(x, FL, z, "minecraft:polished_basalt[axis=y]" if hash01(x, 1, z, 3) < 0.6 else "minecraft:basalt[axis=y]")
for k in range(8):                                  # floor lights in the arena
    a = k * math.pi / 4 + math.pi / 8
    put(HX + int(round(math.cos(a) * 9)), FL, HZ + int(round(math.sin(a) * 9)), "minecraft:shroomlight")
    put(HX + int(round(math.cos(a) * 13)), FL, HZ + int(round(math.sin(a) * 13)), "minecraft:shroomlight")
# low walls on the moat's rims, open at the crossings
for k in range(0, 360, 2):
    a = math.radians(k)
    for rr, b in ((AR + 0.6, None), (AR + 3.6, None)):
        x, z = HX + int(round(math.cos(a) * rr)), HZ + int(round(math.sin(a) * rr))
        if min(abs(x - HX), abs(z - HZ)) <= 3:
            continue
        put(x, FL + 1, z, "minecraft:blackstone_wall")
area.spots["arena"] = [HX, FL, HZ]
area.spots["notes"].append(f"arena: centre top block ({HX}, {FL}, {HZ}) (crying obsidian), floor radius {AR}, lava moat r{AR + 1}..{AR + 3}, crossings north/south/east/west")

# ---------------------------------------------------------------- pillars (basalt and blackstone) around the arena and along the walls
PILLARS = []
for k in range(8):
    a = k * math.pi / 4 + math.pi / 8
    px, pz = HX + int(round(math.cos(a) * 25)), HZ + int(round(math.sin(a) * 25))
    PILLARS.append((px, pz))
for (px, pz) in PILLARS + [(HX - 26, HZ - 12), (HX + 26, HZ - 12), (HX - 26, HZ + 14), (HX + 26, HZ + 14), (HX - 10, HZ + 28), (HX + 10, HZ + 28)]:
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            r = math.hypot(dx, dz)
            if r > 2.3:
                continue
            x, z = px + dx, pz + dz
            y = FL + 1
            while y < Y1 and not is_rock(x, y, z) and (x, y, z) not in F:
                if r <= 1.2:
                    b = "minecraft:polished_blackstone_bricks"
                elif y == FL + 1 or y % 12 == 0:
                    b = "minecraft:chiseled_polished_blackstone"
                elif y % 12 in (9, 10) and r > 1.5:
                    b = "minecraft:shroomlight" if y % 12 == 10 else "minecraft:polished_basalt[axis=y]"
                else:
                    b = "minecraft:basalt[axis=y]" if hash01(x, y, z, 2) < 0.7 else "minecraft:blackstone"
                put(x, y, z, b)
                y += 1
    for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):          # braziers at the foot
        put(px + dx, FL + 1, pz + dz, "minecraft:soul_campfire[lit=true]" if hash01(px, dx, pz, 1) < 0.5 else "minecraft:campfire[lit=true]")

# ---------------------------------------------------------------- the lava river with the broken bridge (north), and side channels
RZ1, RZ2 = 133, 138
for x in range(HX - 36, HX + 37):
    for z in range(RZ1, RZ2 + 1):
        if hall(x, z):
            lava_cell(x, z)
            air(x, FL, z)
for x in range(HX - 37, HX + 38):                   # the rims of the river
    for z in (RZ1 - 1, RZ2 + 1):
        if hall(x, z) and abs(x - HX) > 5:
            put(x, FL, z, "minecraft:polished_blackstone_bricks")
            put(x, FL + 1, z, "minecraft:blackstone_wall") if hash01(x, 0, z, 2) < 0.7 else None
# the bridge, 7 wide, broken in the middle: the outer columns are gone for a stretch and its edges are chewed
for x in range(HX - 3, HX + 4):
    for z in range(RZ1 - 1, RZ2 + 2):
        broken = abs(x - HX) >= 2 and RZ1 + 1 <= z <= RZ2 - 1
        chewed = abs(x - HX) == 1 and z in (RZ1 + 2, RZ2 - 2) and hash01(x, 3, z, 3) < 0.5
        if broken or chewed:
            continue
        air(x, FL, z)
        put(x, FL, z, "minecraft:cracked_polished_blackstone_bricks" if (z + x) % 5 == 0 else "minecraft:polished_blackstone_bricks" if abs(x - HX) < 3 else "minecraft:chiseled_polished_blackstone")
        if abs(x - HX) == 3 and (z <= RZ1 + 1 or z >= RZ2 - 1 or hash01(x, 2, z, 1) < 0.3) and not (RZ1 + 1 <= z <= RZ2 - 1 and hash01(x, 2, z, 7) < 0.6):
            put(x, FL + 1, z, "minecraft:blackstone_wall")
for (bx, bz, bh) in ((-4, RZ1 + 1, 3), (4, RZ2 - 1, 2), (-5, RZ2 - 2, 1)):   # fallen blocks sticking out of the lava
    put(HX + bx, FL - 3, bz, "minecraft:blackstone")
    put(HX + bx, FL - 2, bz, "minecraft:blackstone")
    if bh > 1:
        put(HX + bx, FL - 1, bz, "minecraft:cracked_polished_blackstone_bricks")
for sgn in (-1, 1):                                  # the ruined gate on the north bank
    for y in range(FL + 1, FL + 13):
        for dx in range(0, 2):
            put(HX + sgn * 6 + dx * sgn, y, RZ1 - 3, "minecraft:chiseled_polished_blackstone" if y % 4 == 0 else "minecraft:polished_blackstone_bricks")
    put(HX + sgn * 6, FL + 13, RZ1 - 3, "minecraft:shroomlight")
for dx in range(-6, -1):
    put(HX + dx, FL + 12, RZ1 - 3, "minecraft:polished_blackstone_brick_slab[type=bottom]")
for dx in range(3, 6):
    put(HX + dx, FL + 12, RZ1 - 3, "minecraft:polished_blackstone_brick_slab[type=bottom]") if dx != 4 else None
# colossi guarding the entrance
for sgn in (-1, 1):
    cx, cz = HX + sgn * 10, 128
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            put(cx + dx, FL + 1, cz + dz, "minecraft:polished_blackstone_bricks")
            for y in range(FL + 2, FL + 8):
                put(cx + dx, y, cz + dz, "minecraft:deepslate_bricks" if y % 3 else "minecraft:chiseled_deepslate")
    for dx in range(-3, 4):
        for y in (FL + 8, FL + 9):
            put(cx + dx, y, cz, "minecraft:polished_deepslate")
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            for y in range(FL + 10, FL + 13):
                put(cx + dx, y, cz + dz, "minecraft:chiseled_polished_blackstone" if y == FL + 11 else "minecraft:polished_deepslate")
    put(cx - 1, FL + 11, cz + 2, "minecraft:shroomlight")
    put(cx + 1, FL + 11, cz + 2, "minecraft:shroomlight")
    for dz in (-1, 1):
        for y in range(FL + 3, FL + 9):
            put(cx + sgn * 4, y, cz + dz, "minecraft:polished_deepslate")
    put(cx + sgn * 4, FL + 9, cz, "minecraft:soul_lantern[hanging=false]")
# side channels with a gap for the east/west tunnels and the alcove
for sgn in (-1, 1):
    for (z1, z2) in ((144, 151), (169, 190)):
        for x in range(HX + sgn * 30, HX + sgn * 32 + (1 if sgn > 0 else -1), (1 if sgn > 0 else -1)):
            for z in range(z1, z2 + 1):
                if hall(x, z) and math.hypot(x - HX, z - HZ) > AR + 8:
                    lava_cell(x, z)
                    air(x, FL, z)
        for x in (HX + sgn * 29, HX + sgn * 33):
            for z in range(z1, z2 + 1):
                if hall(x, z):
                    put(x, FL, z, "minecraft:polished_blackstone_bricks")
                    put(x, FL + 1, z, "minecraft:blackstone_wall") if z % 3 else None

# ---------------------------------------------------------------- the throne dais (south end)
TZ0 = 188
for x in range(HX - 9, HX + 10):
    for z in range(TZ0, TZ0 + 9):
        if not hall(x, z):
            continue
        t = max(abs(x - HX) - 3, 0)
        h = 1 if z < TZ0 + 3 else 2 if z < TZ0 + 6 else 3
        if abs(x - HX) > 8 - h + 1:
            h = max(0, h - 1)
        for y in range(FL + 1, FL + 1 + h):
            put(x, y, z, "minecraft:polished_blackstone_bricks" if y != FL + h else "minecraft:gilded_blackstone" if hash01(x, y, z, 2) < 0.12 else "minecraft:chiseled_polished_blackstone")
for dx in range(-3, 4):                              # the front stairs
    put(HX + dx, FL + 1, TZ0 - 1, "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom]")
tz = TZ0 + 6
put(HX, FL + 4, tz, "minecraft:polished_blackstone_brick_stairs[facing=north,half=bottom]")
for y in range(FL + 4, FL + 12):
    for dx in range(-2, 3):
        put(HX + dx, y, tz + 2, "minecraft:blackstone" if y < FL + 10 else "minecraft:chiseled_polished_blackstone")
    put(HX - 2, y, tz + 1, "minecraft:polished_blackstone_bricks")
    put(HX + 2, y, tz + 1, "minecraft:polished_blackstone_bricks")
put(HX, FL + 8, tz + 1, "minecraft:shroomlight")
for dx in (-6, 6):
    put(HX + dx, FL + 4, TZ0 + 4, "minecraft:soul_campfire[lit=true]")

# ---------------------------------------------------------------- the Fairy Soul alcove (west, in the gap between the channels)
ASZ = 154
air_box(HX - 39, FL + 1, ASZ - 2, HX - 33, FL + 4, ASZ + 2)
for x in range(HX - 39, HX - 32):
    for z in range(ASZ - 2, ASZ + 3):
        put(x, FL, z, "minecraft:polished_blackstone_bricks" if (x + z) % 2 == 0 else "minecraft:chiseled_polished_blackstone")
for z in (ASZ - 2, ASZ + 2):
    for y in range(FL + 1, FL + 5):
        put(HX - 36, y, z, "minecraft:chiseled_polished_blackstone")
put(HX - 38, FL + 5, ASZ, "minecraft:shroomlight")
put(HX - 37, FL + 5, ASZ, "minecraft:sea_lantern")
put(HX - 38, FL, ASZ, "minecraft:gold_block")
area.fairy_soul(HX - 38, FL + 1, ASZ)

# ---------------------------------------------------------------- tunnels to the four faces
for (p, q, nm) in (((HX, 128), (HX, 116), "north"), ((HX, 192), (HX, 204), "south"), ((HX + 34, HZ), (HX + 44, HZ), "east"), ((HX - 34, HZ), (HX - 44, HZ), "west")):
    corridor(p, q, FL, rad=1.9, lamp=5, lamp_block="minecraft:shroomlight")
    area.spots["notes"].append(f"tunnel {nm}: floor y{FL}, 5 wide x 4 tall, from the hall to the box face")
sign(HX + 2, FL + 1, 123, 0, ["Khazad-dum", "Bal's lair"])

# ---------------------------------------------------------------- walls and ceiling: dark dressing, lights, hanging lanterns
adj = np.zeros_like(rock)
for ax in range(3):
    for sgn in (1, -1):
        adj |= np.roll(~rock, sgn, axis=ax)
adj &= rock
lights = 0
for xi, yi, zi in np.argwhere(adj):
    x, y, z = int(xi) + OX, int(yi) + OY, int(zi) + OZ
    if (x, y, z) in F or y <= FL:
        continue
    h = hash01(x, y, z, 9)
    if h < 0.028:
        put(x, y, z, "minecraft:shroomlight")
        lights += 1
    elif h < 0.042:
        put(x, y, z, "minecraft:glowstone")
        lights += 1
    elif h < 0.082:
        put(x, y, z, "minecraft:magma_block")
        lights += 1
    elif h < 0.095:
        put(x, y, z, "minecraft:gilded_blackstone")
    elif h < 0.30:
        put(x, y, z, "minecraft:blackstone")
    elif h < 0.40:
        put(x, y, z, "minecraft:polished_basalt[axis=y]")
for k in range(18):                                  # hanging soul lanterns on chains
    a = k / 18 * math.tau
    rr = 20 + 6 * (k % 2)
    x, z = HX + int(round(math.cos(a) * rr)), HZ + int(round(math.sin(a) * rr))
    top = None
    for y in range(Y1 - 2, FL + 8, -1):
        if not rock[x - OX, y - OY, z - OZ]:
            top = y
            break
    if top is None or (x, top, z) in F:
        continue
    ln = 5 + k % 4
    for i in range(ln):
        put(x, top - i, z, "minecraft:iron_chain[axis=y]")
    put(x, top - ln, z, "minecraft:soul_lantern[hanging=true]")
print("lights", lights)

# ---------------------------------------------------------------- check, write, save
seen = reach((HX, FL + 1, 123))
targets = {"arena": [(HX, FL + 1, HZ), (HX + 12, FL + 1, HZ), (HX, FL + 1, HZ - 12)], "soul": [(HX - 38, FL + 1, ASZ)],
           "throne": [(HX, FL + 1, TZ0 + 4)], "tunnels": [(HX, FL + 1, 121), (HX, FL + 1, 198), (HX + 38, FL + 1, HZ), (HX - 38, FL + 1, HZ)]}
ok = True
for name, pts in targets.items():
    for p in pts:
        if not any((p[0] + dx, p[1] + dy, p[2] + dz) in seen for dx in (-1, 0, 1) for dz in (-1, 0, 1) for dy in (-1, 0, 1)):
            ok = False
            print("NOT REACHABLE:", name, p)
print("walkable cells reachable:", len(seen), "all targets ok" if ok else "PROBLEMS")
write_all()
area.save()
print("arena", area.spots["arena"], "soul", area.spots["fairy_souls"])
