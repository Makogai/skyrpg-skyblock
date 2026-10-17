#!/usr/bin/env python3
"""The Crystal Nucleus (hollows_nucleus, x -40..40, z -40..40, y 64..186 of the Crystal Hollows): a great spherical cavern
in the rock with a floating platform at y 110, the big crystal sculpture and the five crystal pedestals, the arrival and
the warp, the pad back to the Dwarven Mines, the NPCs (Emissary Sisko, Gemma, Geonathan Greatforge), four bridges and
tunnels out to the box's corners (one per quadrant), a landing with a spiral stair down to y 64 and tunnels out there.

    python tools/builds/hub_areas/hollows_nucleus.py
"""
import math
import numpy as np
from kit import Area, hash01, noise

OX, OY, OZ = -40, 64, -40
NX, NY, NZ = 80, 122, 80
area = Area("hollows_nucleus", (OX, OY, OZ), (NX, NY, NZ), version=1,
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

PY = 110                      # the platform's top block (players stand at 111)
CY = 118                      # the cavern's centre height
PR = 15.2                     # platform radius
LX, LZ = 0, 23                # the landing / spiral shaft centre
SY = 64                       # the lower hall's floor
PLATE = area.mix({"stone_bricks": 5, "cracked_stone_bricks": 1, "polished_andesite": 3})
DEEP = area.mix({"deepslate_bricks": 4, "deepslate_tiles": 3, "polished_deepslate": 2, "cracked_deepslate_bricks": 1})
CORR_FLOOR = area.mix({"polished_andesite": 4, "stone_bricks": 3, "polished_deepslate": 2, "cracked_stone_bricks": 1})

# ---------------------------------------------------------------- the cavern
d = np.sqrt(XS ** 2 + (YS - CY) ** 2 + ZS ** 2)
lim = 33.5 + 2.0 * np.sin(XS * 0.23 + YS * 0.17) * np.cos(ZS * 0.21 - YS * 0.13) + 1.0 * np.sin((XS + ZS) * 0.37 + YS * 0.2)
rock &= ~(d <= lim)

# ---------------------------------------------------------------- the platform, its pillar and the rim
ANG = {"amethyst": 234, "jade": 306, "sapphire": 18, "topaz": 90, "amber": 162}   # degrees, 0 = +x, 90 = +z
BRIDGES = (45, 135, 225, 315, 90)


def angdiff(a, b):
    return abs((a - b + 180) % 360 - 180)


def platform_top(x, z, r, ang):
    if r > 14.2:
        return "minecraft:polished_blackstone_bricks"
    if r > 13.0:
        return "minecraft:chiseled_polished_blackstone" if int(ang / 7.5) % 2 == 0 else "minecraft:polished_blackstone"
    if r > 11.4:
        return DEEP(x, PY, z, 3)
    if r < 5.6:
        return "minecraft:chiseled_stone_bricks" if 4.4 < r else "minecraft:smooth_quartz"
    if any(angdiff(ang, a) < 4 for a in ANG.values()):
        return "minecraft:polished_andesite" if r > 6.5 else "minecraft:quartz_bricks"
    if 8.2 < r < 9.2:
        return "minecraft:chiseled_stone_bricks"
    return PLATE(x, PY, z, 2)


for x in range(-16, 17):
    for z in range(-16, 17):
        r = math.hypot(x, z)
        if r > PR:
            continue
        ang = math.degrees(math.atan2(z, x)) % 360
        dep = 2 + int(6 * math.sqrt(max(0.0, 1 - (r / PR) ** 2)))
        for y in range(PY - dep, PY):
            put(x, y, z, "minecraft:polished_blackstone_bricks" if y == PY - dep and r > 3.6 else DEEP(x, y, z, 1))
        put(x, PY, z, platform_top(x, z, r, ang))
for k in range(8):                       # sea lanterns under the rim
    a = k * math.pi / 4 + 0.39
    x, z = int(round(math.cos(a) * 11)), int(round(math.sin(a) * 11))
    dep = 2 + int(6 * math.sqrt(max(0.0, 1 - (math.hypot(x, z) / PR) ** 2)))
    put(x, PY - dep, z, "minecraft:sea_lantern")
for x in range(-4, 5):                   # the pillar under the platform
    for z in range(-4, 5):
        r = math.hypot(x, z)
        if r <= 3.6:
            for y in range(76, PY - 4):
                put(x, y, z, "minecraft:chiseled_stone_bricks" if y % 6 == 0 else
                    "minecraft:polished_blackstone_bricks" if r > 2.6 else "minecraft:deepslate_bricks")
for y in range(80, PY - 7, 6):
    for dx, dz in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        put(dx, y + 3, dz, "minecraft:sea_lantern")
for k in range(0, 360, 2):               # the rim wall, open at the bridges
    a = math.radians(k)
    x, z = int(round(math.cos(a) * 14.6)), int(round(math.sin(a) * 14.6))
    if any(angdiff(k, b) < 9 for b in BRIDGES) or abs(x) <= 2 and z < -10:
        continue
    put(x, PY + 1, z, "minecraft:stone_brick_wall")
for k in range(0, 360, 20):              # lantern posts
    a = math.radians(k + 5)
    x, z = int(round(math.cos(a) * 14.6)), int(round(math.sin(a) * 14.6))
    if any(angdiff(k + 5, b) < 12 for b in BRIDGES) or abs(x) <= 3 and z < -10:
        continue
    fill(x, PY + 1, z, x, PY + 2, z, "minecraft:stone_brick_wall")
    put(x, PY + 3, z, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- the crystal sculpture
for x in range(-5, 6):
    for z in range(-5, 6):
        r = math.hypot(x, z)
        if r <= 4.6:
            put(x, PY + 1, z, "minecraft:quartz_bricks" if r > 3.2 else "minecraft:chiseled_quartz_block")
        if r <= 3.3:
            put(x, PY + 2, z, "minecraft:smooth_quartz" if r > 2.2 else "minecraft:chiseled_quartz_block")
CCY, CA, CB = 125, 7.0, 11.5
GLASS = ("minecraft:white_stained_glass", "minecraft:pink_stained_glass", "minecraft:cyan_stained_glass", "minecraft:tinted_glass",
         "minecraft:white_stained_glass", "minecraft:pink_stained_glass")
for x in range(-8, 9):
    for z in range(-8, 9):
        for y in range(CCY - 13, CCY + 14):
            v = (abs(x) + abs(z)) / CA + abs(y - CCY) / CB
            if v > 1.0:
                continue
            if v <= 0.28:
                put(x, y, z, "minecraft:sea_lantern")
            elif v <= 0.8:
                put(x, y, z, "minecraft:amethyst_block")
            else:
                put(x, y, z, GLASS[int(hash01(x, y, z, 4) * len(GLASS)) % len(GLASS)])
for y in range(CCY + 12, CCY + 17):
    put(0, y, 0, "minecraft:end_rod[facing=up]")
for y in range(PY + 3, CCY - 11):        # a slim support under the crystal
    put(0, y, 0, "minecraft:end_rod[facing=up]")
for k in range(4):                       # amethyst shards around the base
    a = k * math.pi / 2 + 0.78
    put(int(round(math.cos(a) * 3)), PY + 3, int(round(math.sin(a) * 3)), "minecraft:amethyst_cluster[facing=up]")

# ---------------------------------------------------------------- the five crystal pedestals
GEM = {"jade": "minecraft:emerald_block", "amber": "minecraft:honeycomb_block", "topaz": "minecraft:gold_block",
       "sapphire": "minecraft:lapis_block", "amethyst": "minecraft:amethyst_block"}
crystals = {}
for name, deg in ANG.items():
    a = math.radians(deg)
    px, pz = int(round(math.cos(a) * 10)), int(round(math.sin(a) * 10))
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            put(px + dx, PY + 1, pz + dz, "minecraft:polished_blackstone_bricks" if dx or dz else "minecraft:chiseled_polished_blackstone")
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        put(px + dx, PY + 2, pz + dz, "minecraft:lantern[hanging=false]")
    put(px, PY + 2, pz, "minecraft:chiseled_stone_bricks")
    put(px, PY + 3, pz, GEM[name])
    crystals[name] = [px, PY + 3, pz]
area.spots["crystals"] = crystals

# ---------------------------------------------------------------- arrival, the pad home, the NPCs
area.arrival(0, PY + 1, -7, 0.0)
area.warp("Crystal Hollows", 0, PY + 1, -7, yaw=0.0)
for dx in range(-2, 3):
    for dz in range(-2, 3):
        put(dx, PY, -12 + dz, "minecraft:slime_block" if abs(dx) < 2 and abs(dz) < 2 else "minecraft:polished_blackstone")
put(0, PY + 1, -12, "minecraft:heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    put(dx, PY + 1, -12 + dz, "minecraft:polished_blackstone_wall")
    put(dx, PY + 2, -12 + dz, "minecraft:lantern[hanging=false]")
sign(4, PY + 1, -10, 0, ["Dwarven Mines", ""])
area.pad("the Dwarven Mines", -1, PY + 1, -13, 1, PY + 2, -11,
         destination=("skyrpg_skyblock:dwarven_mines", (100.5, 201.0, -99.5), 90.0))
area.spots["notes"].append("Dwarven Mines pad: centre (0, 111, -12) (slime 3x3 at y110), pad box x-1..1 y111..112 z-13..-11")
# Emissary Sisko (west): a desk with a lectern
for dz in range(-6, -1):
    put(-14, PY + 1, dz, "minecraft:stone_brick_wall")
put(-14, PY + 2, -4, "minecraft:lantern[hanging=false]")
put(-13, PY + 1, -3, "minecraft:lectern[facing=east]")
put(-13, PY + 1, -5, "minecraft:barrel[facing=up]")
put(-13, PY + 2, -5, "minecraft:lantern[hanging=false]")
area.npc("Emissary Sisko", -12, PY + 1, -4, yaw=270.0, note="west side of the platform, by the desk; commissions")
# Gemma (east): a quartz counter with gemstone blocks on display
for dz in range(-6, -1):
    put(13, PY + 1, dz, "minecraft:smooth_quartz")
put(13, PY + 2, -5, "minecraft:amethyst_cluster[facing=up]")
put(13, PY + 2, -4, "minecraft:lantern[hanging=false]")
put(13, PY + 2, -3, "minecraft:amethyst_cluster[facing=up]")
put(13, PY + 1, -3, "minecraft:emerald_block")
put(13, PY + 2, -6, "minecraft:lapis_block")
area.npc("Gemma", 12, PY + 1, -4, yaw=90.0, note="east side of the platform, behind the quartz counter; gemstones")
# Geonathan Greatforge (south-east): a little forge
for p, b in (((9, 10), "minecraft:anvil[facing=east]"), ((6, 12), "minecraft:blast_furnace[facing=north]"),
             ((8, 12), "minecraft:smithing_table"), ((7, 13), "minecraft:lava_cauldron")):
    put(p[0], PY + 1, p[1], b)
put(6, PY + 2, 12, "minecraft:lantern[hanging=false]")
area.npc("Geonathan Greatforge", 7, PY + 1, 11, yaw=0.0, note="south-east of the platform, by his forge (anvil, blast furnace)")

# ---------------------------------------------------------------- bridges (platform to the cavern wall) and tunnels
TUN_END = {45: "SE", 135: "SW", 225: "NW", 315: "NE"}
NAMES = {45: ["Precursor", "Remnants"], 135: ["Goblin", "Holdout"], 225: ["Jungle", ""], 315: ["Mithril", "Deposits"]}
for deg in BRIDGES:
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    a0, a1 = (PR - 1.5, 36) if deg != 90 else (PR - 1.5, LZ - 4.5)
    for x in range(-40, 40):
        for z in range(-40, 40):
            al = x * c + z * s
            pe = abs(-x * s + z * c)
            if not (a0 <= al <= a1 and pe <= 2.7):
                continue
            edge = pe > 1.8
            put(x, PY, z, "minecraft:polished_blackstone_bricks" if edge else PLATE(x, PY, z, 5))
            put(x, PY - 1, z, "minecraft:stone_bricks" if pe < 1.5 else "minecraft:polished_blackstone_bricks")
            if pe < 0.8:
                put(x, PY - 2, z, "minecraft:deepslate_bricks")
            if edge and al > PR + 0.5:
                put(x, PY + 1, z, "minecraft:stone_brick_wall")
            for y in range(PY + 2, PY + 5):
                air(x, y, z)
    for t in range(int(PR) + 4, int(a1), 6):         # lantern posts
        for sg in (-1, 1):
            x, z = int(round(c * t - s * 2.3 * sg)), int(round(s * t + c * 2.3 * sg))
            fill(x, PY + 1, z, x, PY + 2, z, "minecraft:stone_brick_wall")
            put(x, PY + 3, z, "minecraft:lantern[hanging=false]")
    if deg != 90:
        corridor((c * 30, s * 30), (c * 60, s * 60), PY, rad=1.9)
        sx, sz = int(round(c * 29 - s * 0.9)), int(round(s * 29 + c * 0.9))
        sign(sx, PY + 1, sz, rot_facing(-c, -s), NAMES[deg])
        area.spots["notes"].append(f"tunnel {TUN_END[deg]}: bridge from the platform at {deg} deg, corridor floor y{PY} to the box corner (sign '{' '.join(NAMES[deg]).strip()}')")
# niches with the fairy souls, off the NW and SE tunnels
SOULS = []
for deg, (nx, nz), (jx, jz) in ((225, (-28, -36), (-31, -31)), (45, (28, 36), (31, 31))):
    air_box(nx - 2, PY + 1, nz - 2, nx + 2, PY + 4, nz + 2)
    corridor((jx, jz), (nx, nz), PY, rad=1.0, lamp=40)
    for x in range(nx - 2, nx + 3):
        for z in range(nz - 2, nz + 3):
            put(x, PY, z, "minecraft:polished_blackstone_bricks" if (abs(x - nx) == 2 or abs(z - nz) == 2) else "minecraft:chiseled_stone_bricks")
    put(nx, PY + 5, nz, "minecraft:sea_lantern")
    for dx, dz in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
        put(nx + dx, PY + 1, nz + dz, "minecraft:amethyst_cluster[facing=up]")
    put(nx, PY, nz, "minecraft:chiseled_quartz_block")
    SOULS.append((nx, PY + 1, nz))
    area.spots["notes"].append(f"fairy soul niche at ({nx}, {PY + 1}, {nz}), reached by a side branch off the {TUN_END[deg]} tunnel")
for sp in SOULS:
    area.fairy_soul(*sp)

# ---------------------------------------------------------------- the landing and the spiral stair down to y 64
for x in range(LX - 7, LX + 8):
    for z in range(LZ - 7, LZ + 8):
        r = math.hypot(x - LX, z - LZ)
        if r <= 6.4:
            for y in range(PY + 1, PY + 5):
                air(x, y, z)
            for y in range(PY - 2, PY):
                put(x, y, z, "minecraft:polished_blackstone_bricks")
            put(x, PY, z, "minecraft:chiseled_polished_blackstone" if 5.4 < r else DEEP(x, PY, z, 6))
for k in range(0, 360, 3):               # the rail round the opening, gap at the bridge (north)
    a = math.radians(k)
    x, z = LX + int(round(math.cos(a) * 5.0)), LZ + int(round(math.sin(a) * 5.0))
    if angdiff(k, 270) < 32:
        continue
    put(x, PY + 1, z, "minecraft:iron_bars")
for k in range(0, 360, 60):
    a = math.radians(k + 30)
    x, z = LX + int(round(math.cos(a) * 6.0)), LZ + int(round(math.sin(a) * 6.0))
    if angdiff(k + 30, 270) < 40:
        continue
    fill(x, PY + 1, z, x, PY + 2, z, "minecraft:stone_brick_wall")
    put(x, PY + 3, z, "minecraft:lantern[hanging=false]")
sign(LX + 3, PY + 1, LZ - 5, 0, ["Magma Fields", "stairs down"])
STEPS = 45
for y in range(SY + 1, PY + 1):
    for x in range(LX - 6, LX + 7):
        for z in range(LZ - 6, LZ + 7):
            r = math.hypot(x - LX, z - LZ)
            if r <= 4.7:
                air(x, y, z)
            elif r <= 5.8 and SY + 5 <= y < PY:
                if y % 5 == 0 and int((math.degrees(math.atan2(z - LZ, x - LX)) % 360) / 22.5) % 4 == 0 and r < 5.2:
                    put(x, y, z, "minecraft:sea_lantern")
                else:
                    put(x, y, z, "minecraft:stone_bricks" if hash01(x, y, z, 5) < 0.8 else "minecraft:cracked_stone_bricks")
air_box(LX - 4, PY, LZ - 4, LX + 4, PY, LZ + 4)
for x in range(LX - 5, LX + 6):                  # the hole is round: put the platform floor back outside r 4.7
    for z in range(LZ - 5, LZ + 6):
        r = math.hypot(x - LX, z - LZ)
        if 4.7 < r <= 6.4:
            put(x, PY, z, "minecraft:chiseled_polished_blackstone" if 5.4 < r else DEEP(x, PY, z, 6))
for y in range(SY + 1, PY):
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            edge_mid = (abs(dx) + abs(dz)) == 1
            put(LX + dx, y, LZ + dz, "minecraft:sea_lantern" if edge_mid and y % 4 == 0 else
                "minecraft:chiseled_stone_bricks" if y % 8 == 0 else "minecraft:polished_andesite")
for x in range(LX - 5, LX + 6):
    for z in range(LZ - 5, LZ + 6):
        dx, dz = x - LX, z - LZ
        if max(abs(dx), abs(dz)) <= 1 or math.hypot(dx, dz) > 4.7:
            continue
        th = math.atan2(dx, -dz) % math.tau
        sec = int(th / (math.tau / 16)) % 16
        c, s = math.cos(th), math.sin(th)
        face = ("east" if c < 0 else "west") if abs(c) > abs(s) else ("south" if s < 0 else "north")
        for rev in range(3):
            k = sec + 16 * rev
            if k >= STEPS:
                continue
            y = PY - 1 - k
            put(x, y, z, "minecraft:stone_brick_stairs[facing=%s,half=bottom]" % face)
            put(x, y - 1, z, "minecraft:polished_andesite")
# the lower hall at the bottom
for x in range(LX - 10, LX + 11):
    for z in range(LZ - 10, LZ + 11):
        r = math.hypot(x - LX, z - LZ)
        if r <= 9.4:
            for y in range(SY + 1, SY + 7):
                if r > 4.7 and (r > 5.8 or y < SY + 5):
                    air(x, y, z)
            if r > 5.8:
                put(x, SY, z, "minecraft:chiseled_polished_blackstone" if 7.0 < r < 8.0 else DEEP(x, SY, z, 8))
            else:
                put(x, SY, z, "minecraft:polished_blackstone_bricks")
for k in range(8):
    a = k * math.pi / 4 + 0.39
    x, z = LX + int(round(math.cos(a) * 8.2)), LZ + int(round(math.sin(a) * 8.2))
    fill(x, SY + 1, z, x, SY + 5, z, "minecraft:polished_blackstone_bricks")
    put(x, SY + 6, z, "minecraft:sea_lantern")
    put(x, SY + 1, z, "minecraft:chiseled_polished_blackstone")
for k in range(0, 360, 20):
    a = math.radians(k)
    x, z = LX + int(round(math.cos(a) * 6.0)), LZ + int(round(math.sin(a) * 6.0))
    put(x, SY + 7, z, "minecraft:glowstone")
sign(LX + 3, SY + 1, LZ - 4, 0, ["Magma Fields", "Khazad-dum below"])
LOWER = lambda x, y, z: "minecraft:polished_blackstone_bricks" if hash01(x, y, z, 3) < 0.7 else "minecraft:cracked_polished_blackstone_bricks"
for (p, q, nm) in (((LX, LZ + 8), (LX, 42), "south"), ((LX + 8, LZ), (42, LZ), "east"), ((LX - 8, LZ), (-42, LZ), "west")):
    corridor(p, q, SY, rad=1.9, lamp=5, floor=LOWER)
    area.spots["notes"].append(f"lower tunnel {nm}: floor y{SY} from the stair hall ({LX}, {SY}, {LZ}) to the box face")

# ---------------------------------------------------------------- lights in the walls and ceiling
adj = np.zeros_like(rock)
for ax in range(3):
    for sgn in (1, -1):
        adj |= np.roll(~rock, sgn, axis=ax)
adj &= rock
lights = gems = 0
for xi, yi, zi in np.argwhere(adj):
    x, y, z = int(xi) + OX, int(yi) + OY, int(zi) + OZ
    if (x, y, z) in F or y < 80:
        continue
    h = hash01(x, y, z, 9)
    if h < 0.016:
        put(x, y, z, "minecraft:glowstone" if hash01(x, y, z, 2) < 0.55 else "minecraft:sea_lantern")
        lights += 1
    elif h < 0.045:
        put(x, y, z, "minecraft:amethyst_block" if hash01(x, y, z, 3) < 0.6 else "minecraft:calcite")
        gems += 1
for k in range(14):                      # hanging lamps from the dome
    a = k / 14 * math.tau + 0.2
    rr = 8 + 9 * (k % 2)
    x, z = int(round(math.cos(a) * rr)), int(round(math.sin(a) * rr))
    top = None
    for y in range(CY + 34, CY, -1):
        if not rock[x - OX, y - OY, z - OZ]:
            top = y
            break
    if top is None:
        continue
    ln = 5 + k % 4
    for i in range(ln):
        put(x, top - i, z, "minecraft:iron_chain[axis=y]")
    put(x, top - ln, z, "minecraft:lantern[hanging=true]")
print("wall lights", lights, "gems", gems)

# ---------------------------------------------------------------- check, write, save
seen = reach((0, PY + 1, -7))
targets = {"crystals": [(p[0], p[1] - 1, p[2]) for p in crystals.values()], "souls": SOULS,
           "sisko": [(-12, PY + 1, -4)], "gemma": [(12, PY + 1, -4)], "geo": [(7, PY + 1, 11)],
           "stair_bottom": [(LX + 5, SY + 1, LZ), (LX, SY + 1, 38), (39, SY + 1, LZ), (-40, SY + 1, LZ)],
           "tunnel_ends": [(int(math.cos(math.radians(d)) * 36), PY + 1, int(math.sin(math.radians(d)) * 36)) for d in (45, 135, 225, 315)]}
ok = True
for name, pts in targets.items():
    for p in pts:
        hit = any((p[0] + dx, p[1] + dy, p[2] + dz) in seen for dx in (-1, 0, 1) for dz in (-1, 0, 1) for dy in (-1, 0, 1))
        if not hit:
            ok = False
            print("NOT REACHABLE:", name, p)
print("walkable cells reachable:", len(seen), "all targets ok" if ok else "PROBLEMS")
write_all()
area.save()
print("crystals", crystals, "souls", SOULS)
