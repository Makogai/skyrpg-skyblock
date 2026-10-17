#!/usr/bin/env python3
"""The Dwarven Base Camp (glacite_base_camp, x -24..24, z -128..-88, y 116..150 of the Glacite Tunnels): a cozy outpost in an
icy cavern. A big campfire with seating in the middle, four tents, crates, barrels and lanterns, a minecart station at the end of
a track that runs out east (Ticket Master: back to the Dwarven Mines), the Fossil Research Center hut, and the NPCs. Three
wide openings join the generated tunnels: two in the south face (z -88) and one in the east face (x 24, where the track
leaves).

    python tools/builds/hub_areas/glacite_base_camp.py
"""
import math
from collections import deque
import numpy as np
from kit import Area, hash01, noise, _full

OX, OY, OZ = -24, 116, -128
NX, NY, NZ = 49, 35, 41
area = Area("glacite_base_camp", (OX, OY, OZ), (NX, NY, NZ), version=1,
            dimension="skyrpg_skyblock:glacite_tunnels", island="Glacite Tunnels", shell=2)
X1, Y1, Z1 = OX + NX, OY + NY, OZ + NZ          # exclusive maxima
AIR = "minecraft:air"
rock = np.ones((NX, NY, NZ), dtype=bool)         # True = natural rock, False = open
F = {}                                           # (x, y, z) -> block: everything built (overrides the rock)
SIGNS = {}

FLOOR = 120                                      # the floor's top block (players stand at 121)
CX, CZ = 0, -108                                 # the camp's centre
RX, RZ = 22.0, 18.0                              # the cavern's half axes


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
    msgs = ['""'] + ['"%s"' % t for t in lines] + ['""'] * 3
    SIGNS[(x, y, z)] = ("dark_oak_sign[rotation=%d]" % rot,
                        {"id": "minecraft:sign", "front_text": {"messages": msgs[:4], "color": "white", "has_glowing_text": 1},
                         "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def rot_facing(fx, fz):
    """Sign rotation (0..15) for a sign whose front faces the direction (fx, fz)."""
    return int(round((math.atan2(-fx, fz) % math.tau) / (math.tau / 16))) % 16


def yaw_to(x, z, tx=CX, tz=CZ):
    """NPC yaw facing a point (0 = south, 90 = west, 180 = north, 270 = east)."""
    return round(math.degrees(math.atan2(-(tx - x), (tz - z))) % 360, 1)


def nonsolid(b):
    n = b.split(":")[1].split("[")[0]
    return n == "air" or n.endswith(("lantern", "rod", "vine", "sign", "plate", "carpet", "button", "torch", "fern", "grass", "flower",
                                     "cluster", "candle", "banner", "lichen", "head", "skull", "roots", "petals", "chain", "ladder",
                                     "rail", "snow")) and not n.endswith("sea_lantern") and n != "snow_block"


def standable(x, y, z):
    b0 = blk(x, y - 1, z)
    return nonsolid(blk(x, y, z)) and nonsolid(blk(x, y + 1, z)) and not nonsolid(b0) and "lava" not in b0 and "campfire" not in b0


def reach(start, limit=2_000_000):
    """Walkable cells reachable from start = (x, y, z) feet position (steps up/down of 1, drops of up to 1)."""
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
    return seen


_ents = {}


def write_all():
    """Rock where it's still rock, built blocks over it, air elsewhere; signs with their data."""
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
                    cells[(xi, yi, zi)] = (ent(ROCK(x, y, z, bool(col[yi]))) if col[yi] else air_e, None)
                else:
                    cells[(xi, yi, zi)] = (ent(b), None)
    for (x, y, z), (b, data) in SIGNS.items():
        area.set(x, y, z, b, data)


# ---------------------------------------------------------------- materials
FLOOR_MIX = area.mix({"snow_block": 9, "packed_ice": 1})
CAMP_FLOOR = area.mix({"gravel": 4, "coarse_dirt": 3, "cobblestone": 2, "andesite": 1})
WALL_MIX = area.mix({"stone": 60, "packed_ice": 22, "blue_ice": 6, "snow_block": 8, "calcite": 4})
CEIL_MIX = area.mix({"packed_ice": 62, "blue_ice": 12, "stone": 22, "snow_block": 4})
DEEP_MIX = area.mix({"stone": 1})
HUT_FLOOR = area.mix({"spruce_planks": 4, "dark_oak_planks": 1})


def ROCK(x, y, z, _r):
    kind = ADJ.get((x, y, z))
    if kind == "ceil":
        return CEIL_MIX(x, y, z)
    if kind == "wall":
        return WALL_MIX(x, y, z)
    return "minecraft:stone"


# ---------------------------------------------------------------- the cavern
for x in range(OX, X1):
    for z in range(OZ, Z1):
        d = math.hypot((x - CX) / RX, (z - CZ) / RZ) * (1 + 0.07 * noise(x, z, 3, 1.2))
        if d < 1.0:
            top = int(143 - 12 * d * d + 2.0 * noise(x, z, 5, 1.5))
            for y in range(FLOOR + 1, top + 1):
                air(x, y, z)

# ---------------------------------------------------------------- the openings and their corridors
OPENINGS = []            # (name, floor-level standing cell near the box edge)


def corridor_ns(x1, x2, z_from, z_to):
    air_box(x1, FLOOR + 1, z_from, x2, FLOOR + 5, z_to)


corridor_ns(-10, -7, -98, -88)            # south opening 1 (x -10..-7)
corridor_ns(8, 11, -98, -88)              # south opening 2 (x 8..11)
air_box(17, FLOOR + 1, -118, 24, FLOOR + 5, -115)       # east opening (z -118..-115), the track runs out through it
for (x1, x2) in ((-10, -7), (8, 11)):
    for x in range(x1, x2 + 1):
        for z in (-96, -92, -88):
            pass
    for z in (-96, -92):                                  # hanging lanterns in the corridor ceiling
        put(x1, FLOOR + 5, z, "minecraft:lantern[hanging=true]")
        put(x2, FLOOR + 5, z + 2, "minecraft:lantern[hanging=true]")
    put(x1 - 1, FLOOR + 1, -90, "minecraft:spruce_fence")
    put(x2 + 1, FLOOR + 1, -90, "minecraft:spruce_fence")
    put(x1 - 1, FLOOR + 2, -90, "minecraft:lantern[hanging=false]")
    put(x2 + 1, FLOOR + 2, -90, "minecraft:lantern[hanging=false]")
for z in (-117, -116, -115, -118):
    pass
put(21, FLOOR + 5, -117, "minecraft:lantern[hanging=true]")
put(23, FLOOR + 5, -116, "minecraft:lantern[hanging=true]")
# a timber frame where the track leaves the camp
for z in (-119, -114):
    fill(20, FLOOR + 1, z, 20, FLOOR + 4, z, "minecraft:stripped_spruce_log[axis=y]")
fill(20, FLOOR + 5, -119, 20, FLOOR + 5, -114, "minecraft:spruce_planks")
put(20, FLOOR + 4, -117, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- the floor
for x in range(OX, X1):
    for z in range(OZ, Z1):
        if not rock[x - OX, FLOOR + 1 - OY, z - OZ] and rock[x - OX, FLOOR - OY, z - OZ]:
            r = math.hypot(x + 0.5, z - CZ + 0.5)
            put(x, FLOOR, z, CAMP_FLOOR(x, FLOOR, z) if r < 6.2 else FLOOR_MIX(x, FLOOR, z))

# ---------------------------------------------------------------- the campfire and seating
FIRE = ((0, -108), (-1, -108), (0, -109), (-1, -109))
for (fx, fz) in FIRE:
    put(fx, FLOOR + 1, fz, "minecraft:campfire[lit=true,signal_fire=false,waterlogged=false,facing=north]")
for x in range(-5, 4):
    for z in range(-113, -104):
        r = math.hypot(x + 0.5, z + 108.5)
        if 1.9 <= r <= 2.9 and (x, z) not in FIRE:
            put(x, FLOOR + 1, z, "minecraft:stone_brick_slab[type=bottom]")
SEATS = []
for k in range(8):
    a = k * math.pi / 4 + 0.39
    sx, sz = int(round(-0.5 + math.cos(a) * 4.4)), int(round(-108.5 + math.sin(a) * 4.4))
    dx, dz = sx + 0.5, sz + 108.5 - 0.5
    face = ("east" if dx > 0 else "west") if abs(dx) > abs(dz) else ("south" if dz > 0 else "north")
    if k % 4 == 3:
        put(sx, FLOOR + 1, sz, "minecraft:hay_block[axis=x]")
    else:
        put(sx, FLOOR + 1, sz, "minecraft:spruce_stairs[facing=%s,half=bottom]" % face)
    SEATS.append((sx, sz))
for (bx, bz, f) in ((-1, -114, "up"), (1, -113, "up"), (-5, -108, "up")):
    put(bx, FLOOR + 1, bz, "minecraft:barrel[facing=%s]" % f)
for (lx, lz) in ((-5, -112), (4, -112), (-6, -104), (5, -104)):          # lantern posts round the fire
    fill(lx, FLOOR + 1, lz, lx, FLOOR + 3, lz, "minecraft:spruce_fence")
    put(lx, FLOOR + 4, lz, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- tents (wool A-frames with fence posts)


def tent(cx, cz, axis, open_sign, ca, cb, inside, bed=False):
    """A 6-long, 5-wide A-frame tent along `axis`; its entrance is on the `open_sign` end (+1 / -1)."""
    def pos(u, v):
        return (cx + u, cz + v) if axis == "x" else (cx + v, cz + u)
    for u in range(-3, 3):
        col = ca if (u % 2 == 0) else cb
        end_open = (u == (2 if open_sign > 0 else -3))
        end_closed = (u == (-3 if open_sign > 0 else 2))
        for v in range(-2, 3):
            h = {2: 2, 1: 3, 0: 4}[abs(v)]       # wall height at this offset: 2 high at the sides, then the slope
            for y in range(FLOOR + 1, FLOOR + 1 + h):
                wall = (abs(v) == 2 and y <= FLOOR + 2) or (abs(v) == 1 and y == FLOOR + 3) or (v == 0 and y == FLOOR + 4)
                x, z = pos(u, v)
                if end_closed and y <= FLOOR + 4 and (abs(v) <= 2) and y < FLOOR + 1 + h:
                    wall = True
                if wall and end_open and abs(v) == 2:
                    put(x, y, z, "minecraft:spruce_fence" if y <= FLOOR + 2 else col + "_wool")
                    continue
                if wall:
                    put(x, y, z, col + "_wool")
        for v in (-1, 0, 1):
            x, z = pos(u, v)
            if not end_closed:
                put(x, FLOOR + 1, z, inside + "_carpet")
    x, z = pos(0, 0)
    put(x, FLOOR + 3, z, "minecraft:lantern[hanging=true]")
    # fence posts and lanterns outside the door
    eu = 3 if open_sign > 0 else -4
    for v in (-2, 2):
        x, z = pos(eu, v)
        fill(x, FLOOR + 1, z, x, FLOOR + 2, z, "minecraft:spruce_fence")
        put(x, FLOOR + 3, z, "minecraft:lantern[hanging=false]")
    # the closed end: a barrel, a chest and a bedroll
    cu = -2 if open_sign > 0 else 1
    x, z = pos(cu, 1)
    put(x, FLOOR + 1, z, "minecraft:barrel[facing=up]")
    x, z = pos(cu, -1)
    put(x, FLOOR + 1, z, "minecraft:chest[facing=%s]" % (("east" if open_sign > 0 else "west") if axis == "x" else
                                                         ("south" if open_sign > 0 else "north")))
    if bed:
        fx, fz = pos(cu + open_sign, 0)
        hx, hz = pos(cu, 0)
        facing = (("west" if open_sign > 0 else "east") if axis == "x" else ("north" if open_sign > 0 else "south"))
        put(fx, FLOOR + 1, fz, "minecraft:red_bed[facing=%s,part=foot]" % facing)
        put(hx, FLOOR + 1, hz, "minecraft:red_bed[facing=%s,part=head]" % facing)


tent(-13, -100, "x", +1, "red", "white", "gray", bed=True)      # Dulin's tent (south-west)
tent(14, -100, "x", -1, "orange", "white", "brown", bed=True)   # south-east
tent(-14, -117, "x", +1, "light_blue", "white", "blue", bed=True)   # north-west
tent(-3, -121, "x", +1, "gray", "light_gray", "black")          # north, by the camp's stores

# ---------------------------------------------------------------- crates, barrels and supplies
for (sx, sz, n) in ((-8, -121, 3), (-9, -120, 2), (7, -123, 3), (21, -112, 2), (-19, -97, 2), (19, -95, 2)):
    for i in range(n):
        put(sx + i, FLOOR + 1, sz, "minecraft:barrel[facing=up]" if i % 2 == 0 else "minecraft:chest[facing=south]")
    put(sx, FLOOR + 2, sz, "minecraft:barrel[facing=up]")
    put(sx, FLOOR + 3, sz, "minecraft:lantern[hanging=false]")
for (sx, sz) in ((5, -102), (-6, -97), (-2, -95), (2, -96)):
    put(sx, FLOOR + 1, sz, "minecraft:hay_block[axis=y]")
    put(sx, FLOOR + 2, sz, "minecraft:white_carpet" if (sx + sz) % 2 else "minecraft:gray_carpet")
put(6, FLOOR + 1, -100, "minecraft:crafting_table")
put(7, FLOOR + 1, -100, "minecraft:smithing_table")
put(8, FLOOR + 1, -101, "minecraft:anvil[facing=east]")

# ---------------------------------------------------------------- the minecart station: track from the east, platform at its end
for x in range(6, 25):
    put(x, FLOOR + 1, -116, "minecraft:rail[shape=east_west]")
put(5, FLOOR + 1, -116, "minecraft:stone_brick_wall")
put(5, FLOOR + 2, -116, "minecraft:lantern[hanging=false]")
for x in range(6, 17):
    for z in range(-115, -112):
        put(x, FLOOR + 1, z, "minecraft:stone_bricks" if (x + z) % 5 else "minecraft:cracked_stone_bricks")
for x in range(6, 17):
    put(x, FLOOR + 1, -112, "minecraft:stone_brick_slab[type=bottom]")
    put(x, FLOOR + 1, -111, "minecraft:snow_block")
# the roof: a tarp on fence posts
for (px, pz) in ((6, -115), (16, -115), (6, -113), (16, -113), (11, -115)):
    fill(px, FLOOR + 2, pz, px, FLOOR + 4, pz, "minecraft:spruce_fence")
for x in range(5, 18):
    for z in range(-116, -112):
        put(x, FLOOR + 5, z, "minecraft:light_blue_wool" if (x // 2) % 2 else "minecraft:white_wool")
for (lx, lz) in ((6, -114), (11, -114), (16, -114)):
    put(lx, FLOOR + 4, lz, "minecraft:lantern[hanging=true]")
sign(9, FLOOR + 2, -115, 8, ["Minecart Station", "Dwarven Mines"])
sign(14, FLOOR + 2, -115, 8, ["Ticket Master", "Next minecart soon"])
put(8, FLOOR + 2, -113, "minecraft:barrel[facing=up]")
put(15, FLOOR + 2, -113, "minecraft:barrel[facing=up]")

# ---------------------------------------------------------------- the Fossil Research Center
HX1, HX2, HZ1, HZ2 = -20, -14, -111, -105
for x in range(HX1, HX2 + 1):
    for z in range(HZ1, HZ2 + 1):
        edge = x in (HX1, HX2) or z in (HZ1, HZ2)
        if edge:
            put(x, FLOOR + 1, z, "minecraft:stone_bricks")
            for y in (FLOOR + 2, FLOOR + 3):
                corner = x in (HX1, HX2) and z in (HZ1, HZ2)
                put(x, y, z, "minecraft:stripped_spruce_log[axis=y]" if corner else "minecraft:spruce_planks")
        else:
            put(x, FLOOR, z, HUT_FLOOR(x, FLOOR, z))
for z in (-109, -107):                                   # windows
    put(HX1, FLOOR + 2, z, "minecraft:glass_pane")
for x in (-18, -16):
    put(x, FLOOR + 2, HZ1, "minecraft:glass_pane")
    put(x, FLOOR + 2, HZ2, "minecraft:glass_pane")
for x in range(HX1, HX2 + 1):                            # the roof: stairs rising towards the middle, planks inside
    for z in range(HZ1, HZ2 + 1):
        if x in (HX1, HX2) or z in (HZ1, HZ2):
            face = ("east" if x == HX1 else "west") if x in (HX1, HX2) and not (z in (HZ1, HZ2) and x not in (HX1, HX2)) else \
                ("south" if z == HZ1 else "north")
            if x in (HX1, HX2) and z in (HZ1, HZ2):
                face = "east" if x == HX1 else "west"
            put(x, FLOOR + 4, z, "minecraft:spruce_stairs[facing=%s,half=bottom]" % face)
        else:
            put(x, FLOOR + 4, z, "minecraft:spruce_planks")
for x in range(HX1 + 2, HX2 - 1):
    for z in range(HZ1 + 2, HZ2 - 1):
        put(x, FLOOR + 5, z, "minecraft:spruce_slab[type=bottom]")
put(HX2, FLOOR + 1, -108, "minecraft:air")               # the door (east side), 2 high
put(HX2, FLOOR + 2, -108, "minecraft:air")
F.pop((HX2, FLOOR + 1, -108), None)
F.pop((HX2, FLOOR + 2, -108), None)
put(HX2, FLOOR + 3, -108, "minecraft:stripped_spruce_log[axis=x]")
sign(HX2 + 1, FLOOR + 1, -110, 4, ["Fossil", "Research Center"])
for z in (-110, -109, -107, -106):                       # shelves along the west wall
    put(HX1 + 1, FLOOR + 1, z, "minecraft:bookshelf")
    put(HX1 + 1, FLOOR + 2, z, "minecraft:bookshelf")
put(HX1 + 1, FLOOR + 1, -108, "minecraft:chiseled_bookshelf[facing=east]")
put(-18, FLOOR + 1, -110, "minecraft:lectern[facing=south]")      # the desk, on the north wall
put(-17, FLOOR + 1, -110, "minecraft:cartography_table")
put(-16, FLOOR + 1, -110, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=false]")
put(-15, FLOOR + 1, -110, "minecraft:barrel[facing=up]")
for (fx, fz) in ((-15, -106), (-16, -106), (-17, -106)):  # fossils on the south wall
    put(fx, FLOOR + 1, fz, "minecraft:bone_block[axis=x]")
put(-16, FLOOR + 2, -106, "minecraft:skeleton_skull[rotation=8]")
put(-15, FLOOR + 2, -106, "minecraft:lantern[hanging=false]")
put(-17, FLOOR + 3, -108, "minecraft:lantern[hanging=true]")
put(-15, FLOOR + 3, -108, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- icicles
for x in range(OX + 1, X1 - 1):
    for z in range(OZ + 1, Z1 - 1):
        if hash01(x, 5, z, 11) >= 0.045:
            continue
        top = None
        for y in range(FLOOR + 28, FLOOR + 4, -1):
            if inb(x, y, z) and not rock[x - OX, y - OY, z - OZ] and rock[x - OX, y + 1 - OY, z - OZ]:
                top = y
                break
        if top is None or top < 129:
            continue
        ln = 1 + int(hash01(x, 6, z, 12) * 4)
        for i in range(ln):
            if top - i >= 129 and (x, top - i, z) not in F:
                put(x, top - i, z, "minecraft:blue_ice" if hash01(x, i, z, 13) < 0.25 else "minecraft:packed_ice")

# ---------------------------------------------------------------- hanging lamps in the dome
LAMPS = 0
for x in range(-21, 22, 5):
    for z in range(-125, -90, 5):
        top = None
        for y in range(FLOOR + 28, FLOOR + 6, -1):
            if inb(x, y, z) and not rock[x - OX, y - OY, z - OZ] and rock[x - OX, y + 1 - OY, z - OZ]:
                top = y
                break
        if top is None or (x, top, z) in F:
            continue
        ly = min(top, 127)
        if any((x, y, z) in F for y in range(ly, top + 1)):
            continue
        for y in range(ly + 1, top + 1):
            put(x, y, z, "minecraft:iron_chain[axis=y]")
        put(x, ly, z, "minecraft:lantern[hanging=true]")
        LAMPS += 1

# ---------------------------------------------------------------- NPCs and the arrival
NPCS = {}


def npc(name, x, y, z, note, yaw=None):
    yaw = yaw_to(x, z) if yaw is None else yaw
    area.npc(name, x, y, z, yaw=yaw, note=note)
    NPCS[name] = (x, y, z)


# Lissandra: a small commissions desk at the north-east of the fire
put(3, FLOOR + 1, -113, "minecraft:lectern[facing=south]")
put(4, FLOOR + 1, -113, "minecraft:spruce_fence")
put(4, FLOOR + 2, -113, "minecraft:lantern[hanging=false]")
npc("Emissary Lissandra", 2, FLOOR + 1, -112, "by the campfire, north-east side, next to her lectern; commissions")
npc("Cold Enjoyer", -3, FLOOR + 1, -106, "by the campfire, south-west side")
npc("Dulin", -9, FLOOR + 1, -100, "outside the south-west tent")
npc("Dr. Stone", -17, FLOOR + 1, -107, "inside the Fossil Research Center", yaw=270.0)
npc("Researcher Beryl", -17, FLOOR + 1, -109, "inside the Fossil Research Center", yaw=270.0)
npc("Ticket Master (Base Camp)", 13, FLOOR + 2, -114, "on the minecart station platform; takes you back to the Dwarven Mines")
npc("Scout Plinius", -7, FLOOR + 1, -94, "at the south tunnel exit (x -10..-7)", yaw=yaw_to(-7, -94))
sign(-11, FLOOR + 1, -93, 12, ["Glacite Tunnels", "south"])
sign(12, FLOOR + 1, -93, 4, ["Glacite Tunnels", "south"])
AX, AY, AZ = 9, FLOOR + 2, -114
AYAW = yaw_to(AX, AZ)
area.arrival(AX, AY, AZ, AYAW)
area.warp("base_camp", AX, AY, AZ, AYAW)
area.spots["notes"].append("openings: south face z=-88 at x -10..-7 and x 8..11 (floor y120, 5 high); east face x=24 at z -118..-115 "
                           "(the minecart track, floor y120, 5 high)")

# ---------------------------------------------------------------- rock surfaces: ice in the walls and ceiling
ADJ = {}
openm = ~rock
for x_ in range(NX):
    pass
ceil = np.roll(openm, 1, axis=1) & rock
wall = np.zeros_like(rock)
for ax in (0, 2):
    for sgn in (1, -1):
        wall |= np.roll(openm, sgn, axis=ax)
wall &= rock
wall |= (np.roll(openm, -1, axis=1) & rock)
for xi, yi, zi in np.argwhere(ceil | wall):
    p = (int(xi) + OX, int(yi) + OY, int(zi) + OZ)
    ADJ[p] = "ceil" if ceil[xi, yi, zi] else "wall"
# glowing ice and a few embedded lamps in the walls
for (x, y, z), kind in list(ADJ.items()):
    if (x, y, z) in F or y < FLOOR + 1 or y > FLOOR + 9:
        continue
    h = hash01(x, y, z, 9)
    if h < 0.012:
        put(x, y, z, "minecraft:sea_lantern" if hash01(x, y, z, 2) < 0.5 else "minecraft:glowstone")
    elif h < 0.07 and kind == "wall":
        put(x, y, z, "minecraft:blue_ice")

# ---------------------------------------------------------------- light check (BFS, 15 at lamps, -1 per block)
SRC = ("lantern", "sea_lantern", "glowstone", "campfire", "shroomlight", "jack_o_lantern")


def opaque(p):
    b = blk(*p)
    if is_rock(*p):
        return True
    return _full(b) and b != AIR


def lights():
    lv = {}
    dq = deque()
    for p, b in F.items():
        n = b.split(":")[1].split("[")[0]
        if n in SRC and ("lit=false" not in b):
            lv[p] = 15
            dq.append(p)
    # lanterns in the rock count too: F already holds them. Level BFS.
    dq = deque(sorted(dq, key=lambda p: -lv[p]))
    while dq:
        p = dq.popleft()
        l = lv[p] - 1
        if l <= 0:
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if not inb(*q) or lv.get(q, 0) >= l or opaque(q):
                continue
            lv[q] = l
            dq.append(q)
    return lv


def floor_cells():
    cells = []
    for x in range(OX, X1):
        for z in range(OZ, Z1):
            p = (x, FLOOR + 1, z)
            if nonsolid(blk(*p)) and nonsolid(blk(x, FLOOR + 2, z)) and not nonsolid(blk(x, FLOOR, z)):
                cells.append(p)
    return cells


FLOORS = floor_cells()
extra = 0
for it in range(60):
    lv = lights()
    dark = [p for p in FLOORS if lv.get(p, 0) < 8]
    if not dark:
        break
    p = dark[len(dark) // 2]
    if blk(*p) == AIR:
        put(p[0], p[1], p[2], "minecraft:lantern[hanging=false]")
        extra += 1
    else:
        F[(p[0], p[1] + 1, p[2])] = "minecraft:lantern[hanging=false]" if blk(p[0], p[1] + 1, p[2]) == AIR else F.get((p[0], p[1] + 1, p[2]))
        break
lv = lights()
dark = [p for p in FLOORS if lv.get(p, 0) < 8]
print("dome lamps", LAMPS, "extra floor lanterns", extra, "floor cells", len(FLOORS), "darkest", min(lv.get(p, 0) for p in FLOORS),
      "dark(<8):", len(dark), dark[:8])

# ---------------------------------------------------------------- snow on the floor
for (x, y, z) in FLOORS:
    if (x, y, z) not in F and blk(x, y - 1, z) in ("minecraft:snow_block",) and hash01(x, y, z, 21) < 0.12:
        put(x, y, z, "minecraft:snow[layers=1]")

# ---------------------------------------------------------------- check, write, save
seen = reach((0, FLOOR + 1, -100))
targets = dict(NPCS)
targets["arrival"] = (AX, AY, AZ)
targets["south1"] = (-8, FLOOR + 1, -89)
targets["south2"] = (9, FLOOR + 1, -89)
targets["east"] = (23, FLOOR + 1, -117)
ok = True
for name, p in targets.items():
    if p not in seen:
        ok = False
        print("NOT REACHABLE:", name, p)
    if not (nonsolid(blk(*p)) and nonsolid(blk(p[0], p[1] + 1, p[2]))):
        ok = False
        print("BLOCKED:", name, p, blk(*p), blk(p[0], p[1] + 1, p[2]))
print("walkable cells reachable:", len(seen), "all targets ok" if ok else "PROBLEMS")
write_all()
area.save()
print("arrival", (AX, AY, AZ, AYAW))
for n, p in NPCS.items():
    print(n, p)
