#!/usr/bin/env python3
"""The Jungle Temple (hollows_jungle_temple, x -170..-110, z -170..-110, y 90..140 of the Crystal Hollows): a jungle
cavern carved out of the rock (moss and grass floor, trees, vines, a pond) with a mossy stone-brick temple in it: an
entrance hall guarded by the Kalhuiki Door Guardian, a pit room and a stepped room with simple, safe parkour, and the
inner sanctum with a gold altar. Odawa's camp outside, a ruined shrine with a Fairy Soul, tunnels out to the box faces.

    python tools/builds/hub_areas/hollows_jungle_temple.py
"""
import math
import numpy as np
from kit import Area, hash01, noise

OX, OY, OZ = -170, 90, -170
NX, NY, NZ = 60, 50, 60
area = Area("hollows_jungle_temple", (OX, OY, OZ), (NX, NY, NZ), version=1,
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

FY = 96                                   # the floor block's y (players stand at 97)
CX, CZ = -140, -140                       # the cavern's and temple's centre
CR = 26.5                                 # the cavern's radius
BRICK = area.mix({"mossy_stone_bricks": 4, "stone_bricks": 3, "cracked_stone_bricks": 1, "mossy_cobblestone": 1})
FLOOR = area.mix({"moss_block": 5, "grass_block": 4, "podzol": 1})
CORR_FLOOR = area.mix({"mossy_stone_bricks": 4, "stone_bricks": 3, "moss_block": 2, "cracked_stone_bricks": 1})
PAVE = area.mix({"mossy_stone_bricks": 4, "stone_bricks": 4, "cracked_stone_bricks": 1, "chiseled_stone_bricks": 1})


def W(dx, dz):
    return CX + dx, CZ + dz


def pt(dx, y, dz, block):
    put(CX + dx, y, CZ + dz, block)


def pfill(dx1, y1, dz1, dx2, y2, dz2, block):
    fill(CX + dx1, y1, CZ + dz1, CX + dx2, y2, CZ + dz2, block)


def pair(dx1, y1, dz1, dx2, y2, dz2):
    air_box(CX + dx1, y1, CZ + dz1, CX + dx2, y2, CZ + dz2)


def ceil_y(x, z):
    r = math.hypot(x - CX, z - CZ)
    if r > CR:
        return FY
    return FY + int(7 + 27 * math.sqrt(max(0.0, 1 - (r / CR) ** 2)) + 1.6 * noise(x, z, 3, 2.5))


# ---------------------------------------------------------------- the cavern: flat jungle floor under a dome
for x in range(CX - 28, CX + 29):
    for z in range(CZ - 28, CZ + 29):
        if math.hypot(x - CX, z - CZ) > CR:
            continue
        put(x, FY, z, FLOOR(x, FY, z, 1))
        for y in range(FY - 3, FY):
            put(x, y, z, "minecraft:dirt" if hash01(x, y, z, 2) < 0.7 else "minecraft:mud")
        for y in range(FY + 1, ceil_y(x, z) + 1):
            air(x, y, z)

# ---------------------------------------------------------------- the temple
HALF = 12
for dx in range(-HALF, HALF + 1):
    for dz in range(-21, 16):
        for y in range(FY + 1, FY + 12):
            band = y in (FY + 6, FY + 11) and (abs(dx) == HALF or dz in (-21, 15) or True)
            if y == FY + 6 and (abs(dx) == HALF or dz in (-21, 15)):
                pt(dx, y, dz, "minecraft:chiseled_stone_bricks")
            else:
                pt(dx, y, dz, BRICK(CX + dx, y, CZ + dz, 3))
for dx in range(-8, 9):                            # the upper tier
    for dz in range(-17, 12):
        for y in range(FY + 12, FY + 17):
            edge = abs(dx) == 8 or dz in (-17, 11)
            pt(dx, y, dz, "minecraft:chiseled_stone_bricks" if (y == FY + 14 and edge) else BRICK(CX + dx, y, CZ + dz, 4))
for k in range(8):                                 # the stepped roof
    hx = 7 - k
    for dx in range(-hx, hx + 1):
        for dz in range(-16 + k, 11 - k + 1):
            if dz < -16 + k or dz > 11 - k:
                continue
            pt(dx, FY + 17 + k, dz, BRICK(CX + dx, FY + 17 + k, CZ + dz, 5))
for dz in (-12, -3, 6):
    pt(0, FY + 25, dz, "minecraft:ochre_froglight")
pt(0, FY + 24, 6, "minecraft:ochre_froglight")
for dz in range(-21, 16, 6):                       # buttresses along the long walls
    for sgn in (-1, 1):
        for y in range(FY + 1, FY + 10):
            pt(sgn * 13, y, dz, "minecraft:mossy_stone_bricks" if y % 3 else "minecraft:chiseled_stone_bricks")
        pt(sgn * 13, FY + 10, dz, "minecraft:chiseled_stone_bricks")
        pt(sgn * 13, FY + 11, dz, "minecraft:lantern[hanging=false]")
for sgn in (-1, 1):                                # corner towers
    for dz in (-22, 16):
        for a in (0, 1):
            for b in (0, 1):
                tz = dz + (b if dz < 0 else -b)
                for y in range(FY + 1, FY + 13):
                    pt(sgn * (13 + a), y, tz, "minecraft:chiseled_stone_bricks" if y % 4 == 0 else "minecraft:mossy_stone_bricks")
        pt(sgn * 13, FY + 13, dz, "minecraft:ochre_froglight")

# the entrance: portico, door frame and a half-raised portcullis
for sgn in (-1, 1):
    for y in range(FY + 1, FY + 10):
        pt(sgn * 5, y, 16, "minecraft:chiseled_stone_bricks" if y in (FY + 1, FY + 5, FY + 9) else "minecraft:mossy_stone_bricks")
        pt(sgn * 5, y, 17, "minecraft:mossy_stone_bricks")
    for y in range(FY + 1, FY + 6):
        pt(sgn * 3, y, 15, "minecraft:chiseled_stone_bricks")
    pt(sgn * 5, FY + 10, 16, "minecraft:lantern[hanging=false]")
for dx in range(-5, 6):
    pt(dx, FY + 10, 16, "minecraft:mossy_stone_brick_slab[type=bottom]")
    pt(dx, FY + 10, 17, "minecraft:mossy_stone_brick_slab[type=bottom]")
pair(-2, FY + 1, 14, 2, FY + 5, 16)
for dx in range(-2, 3):
    pt(dx, FY + 5, 15, "minecraft:iron_bars")
    pt(dx, FY + 6, 15, "minecraft:chiseled_stone_bricks")
for dx in range(-2, 3):                             # a paved way from the door south
    for dz in range(14, 30):
        pt(dx, FY, dz, PAVE(CX + dx, FY, CZ + dz, 2))
for dz in range(18, 30, 4):
    for sgn in (-1, 1):
        pt(sgn * 3, FY + 1, dz, "minecraft:mossy_stone_brick_wall")
        pt(sgn * 3, FY + 2, dz, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- the rooms
# Hall A (entrance hall), dx -4..4, dz 8..13
pair(-4, FY + 1, 8, 4, FY + 6, 13)
for dx in range(-4, 5):
    for dz in range(8, 14):
        pt(dx, FY, dz, "minecraft:chiseled_stone_bricks" if (abs(dx) + dz) % 2 == 0 and abs(dx) <= 2 else PAVE(CX + dx, FY, CZ + dz, 7))
for dx, dz in ((-4, 8), (4, 8), (-4, 13), (4, 13)):
    pfill(dx, FY + 1, dz, dx, FY + 6, dz, "minecraft:chiseled_stone_bricks")
    pt(dx, FY + 4, dz, "minecraft:lantern[hanging=false]") if False else None
for dx in (-3, 0, 3):
    pt(dx, FY + 7, 10, "minecraft:ochre_froglight")
    pt(dx, FY + 7, 12, "minecraft:shroomlight")
pair(-1, FY + 1, 7, 1, FY + 4, 7)                   # the doorway to the pit room
for dx in (-2, 2):
    pfill(dx, FY + 1, 7, dx, FY + 4, 7, "minecraft:chiseled_stone_bricks")
# Room B (the pit): dx -9..9, dz -4..6; ledge dz 4..6, pit dz -2..3, ledge dz -4..-3
pair(-9, FY + 1, -4, 9, FY + 7, 6)
pair(-9, FY - 3, -2, 9, FY, 3)
for dx in range(-9, 10):
    for dz in range(-4, 7):
        pit = -2 <= dz <= 3
        pt(dx, FY - 3 if pit else FY, dz, "minecraft:moss_block" if hash01(dx, 1, dz, 6) < 0.6 else "minecraft:mud" if pit else PAVE(CX + dx, FY, CZ + dz, 8))
for (cdx, cdz) in ((-5, 2), (-1, 0), (3, -2)):      # the three platforms
    for dx in range(cdx - 1, cdx + 2):
        for dz in range(cdz - 1, cdz + 2):
            for y in range(FY - 2, FY):
                pt(dx, y, dz, "minecraft:mossy_stone_bricks" if (dx, dz) != (cdx, cdz) else "minecraft:stone_bricks")
            pt(dx, FY, dz, "minecraft:chiseled_stone_bricks" if (dx, dz) == (cdx, cdz) else "minecraft:mossy_stone_bricks")
for i, dx in enumerate((6, 7, 8)):                  # the way back up out of the pit
    for dz in (-2, -1, 0):
        pt(dx, FY - 2 + i, dz, "minecraft:mossy_stone_brick_stairs[facing=east,half=bottom]")
        for y in range(FY - 3, FY - 2 + i):
            pt(dx, y, dz, "minecraft:mossy_stone_bricks")
for dx in range(-8, 9, 4):
    for dz in (-3, 5):
        pt(dx, FY + 8, dz, "minecraft:ochre_froglight")
for dx in (-9, 9):
    for dz in (-3, 1, 5):
        pt(dx, FY + 4, dz, "minecraft:lantern[hanging=false]") if False else None
for dx, dz in ((-8, -3), (8, -3), (-8, 5), (8, 5)):
    pfill(dx, FY + 1, dz, dx, FY + 7, dz, "minecraft:chiseled_stone_bricks")
    pt(dx, FY + 5, dz + (1 if dz < 0 else -1), "minecraft:lantern[hanging=false]") if False else None
pair(2, FY + 1, -5, 4, FY + 4, -5)                  # the doorway north (wall dz -5)
# Room C (rising pads): dx -8..8, dz -12..-6
pair(-8, FY + 1, -12, 8, FY + 9, -6)
for dx in range(-8, 9):
    for dz in range(-12, -5):
        pt(dx, FY, dz, PAVE(CX + dx, FY, CZ + dz, 9) if hash01(dx, 2, dz, 3) < 0.7 else "minecraft:moss_block")
for (cdx, cdz, h) in ((3, -8, 1), (-1, -8, 2), (-5, -10, 3)):
    for dx in range(cdx - 1, cdx + 2):
        for dz in range(cdz - 1, cdz + 2):
            for y in range(FY + 1, FY + h):
                pt(dx, y, dz, "minecraft:mossy_stone_bricks")
            pt(dx, FY + h, dz, "minecraft:chiseled_stone_bricks" if (dx, dz) == (cdx, cdz) else "minecraft:mossy_stone_bricks")
for dx in range(-8, 9, 4):
    pt(dx, FY + 10, -9, "minecraft:ochre_froglight")
    pt(dx, FY + 10, -11, "minecraft:shroomlight")
pair(-6, FY + 4, -14, -4, FY + 7, -13)              # the doorway to the sanctum (floor y FY+3 through the wall)
for dx in range(-6, -3):
    for dz in (-14, -13):
        pt(dx, FY + 3, dz, "minecraft:chiseled_stone_bricks")
# the Inner Sanctum: dx -7..7, dz -20..-15, floor block y FY+3
SF = FY + 3
pair(-7, SF + 1, -20, 7, SF + 7, -15)
for dx in range(-7, 8):
    for dz in range(-20, -14):
        pt(dx, SF, dz, "minecraft:chiseled_stone_bricks" if (abs(dx) + abs(dz)) % 2 == 0 else "minecraft:polished_andesite")
        if dx in (-7, 7) or dz in (-20, -15):
            pt(dx, SF, dz, "minecraft:mossy_stone_bricks")
pair(-6, SF + 1, -14, -4, SF + 4, -14)
for dx in range(-6, -3):
    pt(dx, SF, -14, "minecraft:chiseled_stone_bricks")
ALX, ALZ = 0, -18
for dx in range(-1, 2):
    for dz in range(-1, 2):
        pt(ALX + dx, SF + 1, ALZ + dz, "minecraft:gilded_blackstone" if abs(dx) + abs(dz) == 2 else "minecraft:polished_blackstone_bricks")
pt(ALX, SF + 1, ALZ, "minecraft:gold_block")
pt(ALX, SF + 2, ALZ, "minecraft:gold_block")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    pt(ALX + dx, SF + 2, ALZ + dz, "minecraft:cut_copper_slab[type=bottom]" if False else "minecraft:smooth_stone_slab[type=bottom]" if False else "minecraft:stone_brick_slab[type=bottom]")
for dx, dz in ((-5, -16), (5, -16), (-5, -19), (5, -19)):
    pfill(dx, SF + 1, dz, dx, SF + 5, dz, "minecraft:chiseled_stone_bricks")
    pt(dx, SF + 6, dz, "minecraft:ochre_froglight")
for dx in (-3, 0, 3):
    pt(dx, SF + 8, -17, "minecraft:ochre_froglight")
pt(ALX, SF + 3, ALZ, "minecraft:lantern[hanging=false]")
pt(-3, SF + 1, -19, "minecraft:chest[facing=south]")
pt(3, SF + 1, -19, "minecraft:chest[facing=south]")
for dx in (-3, 3):
    pt(dx, SF + 1, -16, "minecraft:moss_carpet")
altar = [CX + ALX, SF + 2, CZ + ALZ]
area.spots["altar"] = altar

# ---------------------------------------------------------------- the pond and the ruined shrine (Fairy Soul)
PX, PZ = -20, 2
for dx in range(PX - 6, PX + 7):
    for dz in range(PZ - 6, PZ + 7):
        r = math.hypot(dx - PX, dz - PZ) + 0.7 * noise(dx, dz, 2, 3)
        if r <= 4:
            pt(dx, FY - 1, dz, "minecraft:clay" if hash01(dx, 0, dz, 4) < 0.5 else "minecraft:mud")
            pt(dx, FY, dz, "minecraft:water")
            if r > 3 and hash01(dx, 1, dz, 5) < 0.4:
                pt(dx, FY + 1, dz, "minecraft:lily_pad")
        elif r <= 5:
            pt(dx, FY, dz, "minecraft:mud" if hash01(dx, 1, dz, 6) < 0.5 else "minecraft:moss_block")
SX, SZ = -15, 15
for dx in range(SX - 3, SX + 4):
    for dz in range(SZ - 3, SZ + 4):
        pt(dx, FY, dz, "minecraft:chiseled_stone_bricks" if (abs(dx - SX) <= 1 and abs(dz - SZ) <= 1) else PAVE(CX + dx, FY, CZ + dz, 10))
for i, (dx, dz) in enumerate(((-3, -3), (3, -3), (-3, 3), (3, 3))):
    for y in range(1, 3 + (i * 7) % 4):
        pt(SX + dx, FY + y, SZ + dz, "minecraft:mossy_stone_bricks" if y % 2 else "minecraft:chiseled_stone_bricks")
pt(SX, FY, SZ, "minecraft:ochre_froglight")
for dx, dz in ((1, 1), (-1, -1)):
    pt(SX + dx, FY + 1, SZ + dz, "minecraft:lantern[hanging=false]")
SOUL = (CX + SX, FY + 1, CZ + SZ)
area.fairy_soul(*SOUL)

# ---------------------------------------------------------------- Odawa's camp, the guardian, signs
for dx in range(5, 13):
    for dz in range(18, 24):
        if (dx, dz) not in ((5, 18), (12, 18), (5, 23), (12, 23)):
            pt(dx, FY, dz, "minecraft:coarse_dirt" if hash01(dx, 1, dz, 3) < 0.5 else "minecraft:podzol")
pt(9, FY + 1, 20, "minecraft:campfire[lit=true,facing=north]")
for dx, dz in ((6, 19), (6, 22), (12, 19), (12, 22)):
    pfill(dx, FY + 1, dz, dx, FY + 2, dz, "minecraft:jungle_fence")
    pt(dx, FY + 3, dz, "minecraft:lantern[hanging=false]")
for dx in (7, 8, 10, 11):
    pt(dx, FY + 1, 22, "minecraft:jungle_stairs[facing=south,half=bottom]")
pt(12, FY + 1, 21, "minecraft:barrel[facing=west]")
for y in range(1, 5):                              # a little totem
    pt(13, FY + y, 21, "minecraft:jungle_log[axis=y]" if y != 3 else "minecraft:carved_pumpkin[facing=west]")
pt(13, FY + 5, 21, "minecraft:lantern[hanging=false]")
area.npc("Odawa", CX + 8, FY + 1, CZ + 21, yaw=270.0, note="Odawa's camp in the temple yard, south-east of the temple door, by a campfire")
area.npc("Kalhuiki Door Guardian", CX + 3, FY + 1, CZ + 17, yaw=0.0, note="outside the temple door in the portico, east side of the entrance")
sign(CX - 4, FY + 1, CZ + 21, 0, ["Jungle Temple", "Kalhuiki Tribe"])

# ---------------------------------------------------------------- tunnels to the box faces: south, east, west
corridor((CX, CZ + 24), (CX, OZ + NZ + 3), FY, rad=1.9, lamp=5)
corridor((CX + 16, CZ + 20), (OX + NX + 3, CZ + 20), FY, rad=1.9, lamp=5)
corridor((CX - 16, CZ + 20), (OX - 3, CZ + 20), FY, rad=1.9, lamp=5)
for dz in range(-1, 2):                              # keep the yard path to the east/west tunnels
    pass

# ---------------------------------------------------------------- jungle: trees, bushes, grass
def tree(x, z, h, big=False):
    for y in range(FY + 1, FY + 1 + h):
        for a in ((0, 0), (1, 0), (0, 1), (1, 1)) if big else ((0, 0),):
            put(x + a[0], y, z + a[1], "minecraft:jungle_log[axis=y]")
    top = FY + h
    rad = 4 if big else 3
    for dy in range(-2, 3):
        r = rad - (1 if dy >= 1 else 0) - (1 if dy == 2 else 0) - (1 if dy == -2 else 0)
        for dx in range(-r - (1 if big else 0), r + 1 + (1 if big else 0)):
            for dz in range(-r - (1 if big else 0), r + 1 + (1 if big else 0)):
                if dx * dx + dz * dz <= (r + 0.6) ** 2 and hash01(x + dx, top + dy, z + dz, 7) > 0.12 and (x + dx, top + dy, z + dz) not in F:
                    put(x + dx, top + dy, z + dz, "minecraft:jungle_leaves[persistent=true]")
    if hash01(x, 3, z, 2) < 0.4:
        put(x + (2 if big else 1), top - 1, z, "minecraft:shroomlight")


def reserved(dx, dz):
    if -15 <= dx <= 15 and -24 <= dz <= 18:            # the temple and its portico
        return True
    if -5 <= dx <= 5 and dz > 12:                      # the yard path
        return True
    if abs(dz - 20) <= 4 and abs(dx) >= 12:            # the east/west tunnel approaches
        return True
    if math.hypot(dx - PX, dz - PZ) < 9 or math.hypot(dx - SX, dz - SZ) < 6 or (4 <= dx <= 16 and 16 <= dz <= 27):
        return True
    return False


placed = []
for x in range(CX - 25, CX + 26):
    for z in range(CZ - 25, CZ + 26):
        dx, dz = x - CX, z - CZ
        if math.hypot(dx, dz) > CR - 3 or reserved(dx, dz):
            continue
        h = hash01(x, 11, z, 3)
        if h > 0.12:
            continue
        if any(math.hypot(x - px, z - pz) < 5.5 for px, pz in placed):
            continue
        big = hash01(x, 12, z, 3) < 0.35
        th = (9 + int(hash01(x, 13, z, 3) * 7)) if big else (6 + int(hash01(x, 14, z, 3) * 6))
        if ceil_y(x, z) - FY < th + 5:
            th = ceil_y(x, z) - FY - 6
            big = False
        if th < 4:
            continue
        tree(x, z, th, big)
        placed.append((x, z))
for x in range(CX - 26, CX + 27):                    # bushes and ground cover
    for z in range(CZ - 26, CZ + 27):
        dx, dz = x - CX, z - CZ
        if math.hypot(dx, dz) > CR - 1 or reserved(dx, dz) or (x, FY + 1, z) in F or F.get((x, FY, z), "").endswith("water"):
            continue
        if F.get((x, FY, z), "") in ("minecraft:mud", "minecraft:clay"):
            continue
        h = hash01(x, 21, z, 4)
        if h < 0.02:
            for ddx, ddy, ddz in ((0, 1, 0), (1, 1, 0), (-1, 1, 0), (0, 1, 1), (0, 1, -1), (0, 2, 0)):
                if hash01(x + ddx, ddy, z + ddz, 8) < 0.85 and (x + ddx, FY + ddy, z + ddz) not in F:
                    put(x + ddx, FY + ddy, z + ddz, "minecraft:jungle_leaves[persistent=true]")
        elif h < 0.30:
            put(x, FY + 1, z, "minecraft:short_grass" if hash01(x, 22, z, 4) < 0.7 else "minecraft:fern")
        elif h < 0.325:
            put(x, FY + 1, z, "minecraft:azalea_leaves[persistent=true]" if False else "minecraft:melon" if hash01(x, 23, z, 4) < 0.25 else "minecraft:pink_petals[flower_amount=1,facing=north]" if False else "minecraft:fern")

# ---------------------------------------------------------------- walls: moss, lights and vines
adj = np.zeros_like(rock)
for ax in range(3):
    for sgn in (1, -1):
        adj |= np.roll(~rock, sgn, axis=ax)
adj &= rock
lights = 0
for xi, yi, zi in np.argwhere(adj):
    x, y, z = int(xi) + OX, int(yi) + OY, int(zi) + OZ
    if (x, y, z) in F or y <= FY:
        continue
    h = hash01(x, y, z, 9)
    if h < 0.02:
        put(x, y, z, "minecraft:shroomlight")
        lights += 1
    elif h < 0.04:
        put(x, y, z, "minecraft:ochre_froglight" if hash01(x, y, z, 2) < 0.6 else "minecraft:glowstone")
        lights += 1
    elif h < 0.20:
        put(x, y, z, "minecraft:moss_block")
    elif h < 0.28:
        put(x, y, z, "minecraft:mossy_cobblestone")
# ceiling lamps in the dome: a ring of chains with lanterns
for k in range(16):
    a = k / 16 * math.tau
    rr = 10 + 8 * (k % 2)
    x, z = CX + int(round(math.cos(a) * rr)), CZ + int(round(math.sin(a) * rr))
    top = ceil_y(x, z)
    ln = 4 + k % 3
    if (x, top, z) in F:
        continue
    for i in range(ln):
        put(x, top - i, z, "minecraft:iron_chain[axis=y]")
    put(x, top - ln, z, "minecraft:lantern[hanging=true]")
# vines on walls (rock and temple), a few strands hanging
VF = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}


def wall_solid(x, y, z):
    b = F.get((x, y, z))
    if b is not None:
        return not nonsolid(b) and "glass" not in b and "leaves" not in b
    return is_rock(x, y, z)


vines = 0
for x in range(OX + 1, X1 - 1):
    for z in range(OZ + 1, Z1 - 1):
        if math.hypot(x - CX, z - CZ) > CR + 2:
            continue
        for y in range(FY + 2, ceil_y(x, z) + 1):
            if (x, y, z) in F or rock[x - OX, y - OY, z - OZ]:
                continue
            if True:
                faces = [d for d, (ddx, ddz) in VF.items() if wall_solid(x + ddx, y, z + ddz)]
                if faces and hash01(x, y, z, 5) < 0.045:
                    ln = 2 + int(hash01(x, y, z, 6) * 6)
                    for i in range(ln):
                        yy = y - i
                        if (x, yy, z) in F and "air" not in F[(x, yy, z)]:
                            break
                        fcs = [d for d, (ddx, ddz) in VF.items() if wall_solid(x + ddx, yy, z + ddz)]
                        if not fcs or yy <= FY:
                            break
                        put(x, yy, z, "minecraft:vine[" + ",".join(f"{d}={'true' if d in fcs else 'false'}" for d in ("east", "north", "south", "west", "up")) + "]")
                        vines += 1
print("lights", lights, "vines", vines, "trees", len(placed))

# ---------------------------------------------------------------- check, write, save
seen = reach((CX, FY + 1, CZ + 25))
targets = {"guardian": [(CX + 3, FY + 1, CZ + 17)], "odawa": [(CX + 8, FY + 1, CZ + 21)], "soul": [SOUL],
           "altar": [(altar[0], altar[1] - 1, altar[2] + 2)], "hallA": [(CX, FY + 1, CZ + 10)], "pit_far": [(CX, FY + 1, CZ - 4)],
           "roomC": [(CX + 3, FY + 1, CZ - 7)], "sanctum": [(CX - 5, SF + 1, CZ - 15), (CX + 6, SF + 1, CZ - 19)],
           "tunnels": [(CX, FY + 1, OZ + NZ - 1), (OX + NX - 1, FY + 1, CZ + 20), (OX, FY + 1, CZ + 20)]}
ok = True
for name, pts in targets.items():
    for p in pts:
        if not any((p[0] + dx, p[1] + dy, p[2] + dz) in seen for dx in (-1, 0, 1) for dz in (-1, 0, 1) for dy in (-1, 0, 1)):
            ok = False
            print("NOT REACHABLE:", name, p)
print("walkable cells reachable:", len(seen), "all targets ok" if ok else "PROBLEMS")
write_all()
area.save()
print("altar", altar, "soul", SOUL)
