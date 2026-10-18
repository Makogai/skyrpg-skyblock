#!/usr/bin/env python3
"""The Rift, Colosseum (x 70..199, z -240..-121): a ruined, slime-stained amphitheatre (the Bacte boss arena) with
tiered seating, a colonnade on its back wall, four gates, an emperor's box with the Enigma Soul, a forum of broken
columns and a slime-covered fountain to its south, and Gooey's corner (a Porhtal eye) in the south-east.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_colosseum.py

Neighbours: the Living Cave along x = 70 (crossing z -180), the Photon Pathway along z = -121 (crossing x 140) and
Stillgore Chateau along x = 199 (crossing z -180); the surface keeps y 68 near all three. The north side (z -240) is the
Rift's outer edge: broken cliffs and a hanging underside.
"""
import math

from kit import hash01, noise
from rift_lib import new_area, land, GROUND, SEAM, CROSSINGS, LAND_BOTTOM

area = new_area("colosseum", version=1)
area.shell = 6
S = area.set
G = GROUND
X0, X1, Z0, Z1 = 70, 199, -240, -121
CX, CZ, A, B = 135, -190, 20, 15            # the arena oval (40 x 30)
K = 17.5                                    # blocks per unit of oval distance
PODIUM_TOP = 70
BACK = 77                                   # first y of the back wall / colonnade
FX, FZ = 135, -146                          # the fountain
GOO = (176, 192, -142, -126)                # Gooey's courtyard x1, x2, z1, z2
FORUM = (96, 174, -162, -129)               # paved forum x1, x2, z1, z2
CROSS_W = CROSSINGS[("living_cave", "colosseum")][2]       # -180
CROSS_S = CROSSINGS[("photon_pathway", "colosseum")][2]    # 140
CROSS_E = CROSSINGS[("colosseum", "chateau")][2]           # -180


def sm(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def dd(x, z):
    return math.hypot((x - CX) / A, (z - CZ) / B)


def off(x, z):
    """Blocks outside the arena wall (negative = inside the arena)."""
    return (dd(x, z) - 1) * K


def segd(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    l2 = dx * dx + dz * dz
    t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / l2))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


def rectd(x, z, r):
    dx = max(r[0] - x, 0, x - r[1])
    dz = max(r[2] - z, 0, z - r[3])
    return math.hypot(dx, dz)


def jag(a, b, s):
    return 5 * noise(a, b, s, 1.2) + 2.5 * (hash01(int(a) // 2, 1, int(b) // 2, s) - 0.5) + 1.2 * (hash01(int(a), 2, int(b), s) - 0.5)


def edge(x, z):
    """Distance to the Rift's outer (north) edge."""
    return z - (-235 + jag(x, 0, 1))


# ---------------------------------------------------------------- paths
PATHS = {
    "cross_w": ([(70, CROSS_W), (97, CROSS_W)], 5),
    "cross_e": ([(199, CROSS_E), (173, CROSS_E)], 5),
    "cross_s": ([(CROSS_S, -121), (CROSS_S, -131)], 5),
    "br_w": ([(90, CROSS_W), (98, -168), (104, -160)], 3),
    "br_e": ([(180, CROSS_E), (172, -168), (166, -160)], 3),
    "north": ([(CX, -219), (CX, -230)], 5),
}
path_cols = {}
for name, (pts, w) in PATHS.items():
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n + 1):
            px, pz = x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n
            r = w / 2.0
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    if dx * dx + dz * dz <= r * r + 0.3:
                        path_cols.setdefault((int(round(px)) + dx, int(round(pz)) + dz), name)


def path_dist(x, z):
    return min(segd(x, z, a[0], a[1], b[0], b[1]) for pts, _ in PATHS.values() for a, b in zip(pts, pts[1:]))


def in_forum(x, z):
    d = rectd(x, z, FORUM)
    return d < 0.5 - 1.2 * abs(noise(x, z, 3, 2.5)) + (0 if d == 0 else 0) and not (in_goo(x, z))


def in_goo(x, z):
    return GOO[0] <= x <= GOO[1] and GOO[2] <= z <= GOO[3]


def terrain_top(x, z):
    """Surface y: 68 near the seams, the colosseum, the forum, Gooey's corner and the paths; gentle hills elsewhere."""
    m = min(x - X0, X1 - x, Z1 - z)
    mask = sm((m - SEAM - 4) / 10.0)
    mask = min(mask, sm((off(x, z) - 17) / 8.0), sm((rectd(x, z, FORUM) - 4) / 6.0),
               sm((rectd(x, z, (GOO[0] - 3, GOO[1] + 3, GOO[2] - 3, GOO[3] + 3)) - 2) / 6.0), sm((path_dist(x, z) - 4) / 5.0))
    n = noise(x, z, 5, 2.0) * 0.6 + noise(x, z, 9, 4.0) * 0.4
    hill = max(0.0, n + 0.15) * 9
    return G + int(round(hill * mask))


# ---------------------------------------------------------------- block pickers
BODY = area.mix({"tuff": 6, "andesite": 3, "deepslate": 3, "calcite": 1, "mud": 2, "packed_mud": 1})
UNDER = area.mix({"rooted_dirt": 3, "dirt": 3, "tuff": 2, "mud": 1})
UNDERSIDE = area.mix({"tuff": 6, "andesite": 3, "deepslate": 3, "calcite": 1, "mud": 2})
SURF_PATH = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3, "packed_mud": 1})
PAVE = area.mix({"stone_bricks": 5, "mossy_stone_bricks": 3, "cracked_stone_bricks": 2, "tuff_bricks": 3, "calcite": 1,
                 "polished_andesite": 1})
STAIN = area.mix({"moss_block": 5, "lime_terracotta": 2, "slime_block": 1, "green_terracotta": 1})
SEAT_A = area.mix({"stone_bricks": 6, "mossy_stone_bricks": 3, "cracked_stone_bricks": 2})
SEAT_B = area.mix({"mossy_stone_bricks": 5, "stone_bricks": 3, "tuff_bricks": 2, "cracked_stone_bricks": 2})
FLOOR = area.mix({"smooth_stone": 6, "calcite": 4, "polished_diorite": 1})
GRASS = area.mix({"grass_block": 8, "moss_block": 2, "rooted_dirt": 1})


def paving(x, y, z):
    n = noise(x, z, 17, 1.8)
    if n > 0.4:
        return STAIN(x, y, z)
    if abs(noise(x, z, 31, 1.6)) < 0.03 and hash01(x, 5, z, 2) < 0.4:
        return "minecraft:slime_block" if hash01(x, 6, z, 3) < 0.35 else "minecraft:cracked_stone_bricks"
    return PAVE(x, y, z)


def arena_floor(x, y, z):
    r = math.hypot((x - CX) / A, (z - CZ) / B) * 17.5
    if r < 4.5:
        return "minecraft:slime_block" if r < 2.2 and hash01(x, 1, z, 9) < 0.7 else "minecraft:moss_block"
    if abs(noise(x, z, 31, 1.6)) < 0.035:
        return "minecraft:slime_block" if hash01(x, 6, z, 3) < 0.4 else "minecraft:cracked_stone_bricks"
    n = noise(x, z, 17, 1.8) + 0.5 * noise(x, z, 23, 3.0)
    if n > 0.45:
        return STAIN(x, y, z)
    if abs(r - 15) < 0.7 or abs(r - 9) < 0.5:
        return "minecraft:polished_andesite"
    return FLOOR(x, y, z)


def outside_surface(x, y, z):
    if (x, z) in path_cols:
        return SURF_PATH(x, y, z)
    if edge(x, z) < 2.5:
        return "minecraft:tuff" if hash01(x, 8, z, 1) < 0.5 else "minecraft:mud"
    n = noise(x, z, 41, 2.6)
    h = hash01(x, 2, z, 9)
    if n > 0.35 and h < 0.7:
        return "minecraft:moss_block"
    if h < 0.06:
        return "minecraft:rooted_dirt"
    if h < 0.1:
        return "minecraft:tuff"
    return GRASS(x, y, z)


def under_depth(x, z, e):
    t = sm(e / 55.0)
    nn = (noise(x, z, 51, 2.0) + 1) / 2
    d = 2 + 3 * sm(e / 8.0) + 22 * t ** 1.1 * (0.45 + 0.55 * nn) + 4 * nn * sm(e / 20.0)
    if hash01(x, 4, z, 6) < 0.05 and e < 70:
        d += 4 + 6 * hash01(x, 5, z, 7)
    return int(min(d, 29))


# ---------------------------------------------------------------- land
HEIGHT = {}
for x in range(X0, X1 + 1):
    for z in range(Z0, Z1 + 1):
        e = edge(x, z)
        if e < 0:
            continue
        top = terrain_top(x, z)
        if e < 6:
            top = max(G - 3, min(top, G - int(3 * (1 - e / 6.0)) + int(2 * noise(x, z, 4, 3))))
        HEIGHT[(x, z)] = top
        o = off(x, z)
        if o < 0:
            surf = arena_floor
            top = G
            HEIGHT[(x, z)] = G
        elif in_forum(x, z) or in_goo(x, z) or o < 15.5:
            surf = paving if (x, z) not in path_cols else SURF_PATH
            top = G
            HEIGHT[(x, z)] = G
        else:
            surf = outside_surface
        land(area, x, z, top, surf, UNDER if surf is outside_surface else BODY, BODY)
        area.top[(x, z)] = top
        d = under_depth(x, z, e)
        for k in range(1, d + 1):
            y = LAND_BOTTOM - k
            r = hash01(x, y, z, 12)
            if e < 14 and r < 0.04:
                b = "minecraft:crying_obsidian"
            elif r < 0.035:
                b = "minecraft:amethyst_block"
            else:
                b = UNDERSIDE(x, y, z)
            S(x, y, z, b)
        if e < 30 and hash01(x, 10, z, 13) < 0.012:
            ln = 3 + int(hash01(x, 11, z, 14) * 8)
            yy = LAND_BOTTOM - d
            for i in range(ln):
                S(x, yy - 1 - i, z, "minecraft:crying_obsidian" if hash01(x, i, z, 15) < 0.4 else "minecraft:amethyst_block")
            S(x, yy - 1 - ln, z, "minecraft:amethyst_cluster[facing=down]")

# ---------------------------------------------------------------- the seating
def sector_cut(x, z):
    """How many blocks the seating has collapsed at this column (0 = intact)."""
    th = math.atan2((z - CZ) / B, (x - CX) / A)
    n = noise(CX + 30 * math.cos(th), CZ + 30 * math.sin(th), 11, 1.6)
    return max(0.0, n - 0.3) * 16


SEAT_TOP = {}
for (x, z), _ in list(HEIGHT.items()):
    o = off(x, z)
    if o < 0 or o >= 15.5:
        continue
    if o < 1.5:
        top = PODIUM_TOP
        blk = "minecraft:stone_bricks" if hash01(x, 1, z, 4) < 0.7 else "minecraft:mossy_stone_bricks"
    elif o < 13.5:
        k = int((o - 1.5) // 2) + 1
        top = PODIUM_TOP + k
        row = int((o - 1.5) % 2)
        blk = SEAT_A(x, top, z) if row == 0 else SEAT_B(x, top, z)
    else:
        top = BACK + int(hash01(x, 2, z, 5) * 2)
        blk = "minecraft:tuff_bricks" if hash01(x, 3, z, 6) < 0.5 else "minecraft:mossy_stone_bricks"
    cut = sector_cut(x, z)
    if cut > 0 and o >= 1.5:
        top = max(G + 1, top - int(cut + hash01(x, 4, z, 7) * 3))
    SEAT_TOP[(x, z)] = top
    for y in range(G + 1, top + 1):
        if y == top:
            b = blk
        else:
            b = "minecraft:tuff_bricks" if (y + x + z) % 3 else "minecraft:andesite"
        S(x, y, z, b)
    if cut > 0 and o >= 1.5:
        h = hash01(x, 5, z, 8)
        if h < 0.18:
            S(x, top, z, "minecraft:slime_block")
        elif h < 0.3:
            S(x, top + 1, z, "minecraft:moss_carpet")
        elif h < 0.4:
            S(x, top + 1, z, "minecraft:tuff_slab")
    elif hash01(x, 6, z, 9) < 0.04 and o >= 1.5:
        S(x, top + 1, z, "minecraft:moss_carpet")
    area.top[(x, z)] = top

# fallen blocks (rubble) spilled from the collapsed sectors onto the arena floor and outside
for (x, z), top in SEAT_TOP.items():
    o = off(x, z)
    if 1.5 <= o < 13.5 and sector_cut(x, z) > 1.5 and hash01(x, 7, z, 10) < 0.05:
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                px, pz = x + dx, z + dz
                if (px, pz) in HEIGHT and off(px, pz) < 0 and hash01(px, 8, pz, 11) < 0.35 and off(px, pz) > -7:
                    S(px, G + 1, pz, hash01(px, 9, pz, 12) < 0.5 and "minecraft:mossy_cobblestone_slab" or "minecraft:tuff_slab")


# ---------------------------------------------------------------- gates (tunnels through the seating)
GATES = {
    "west": ("x", -1, CROSS_W),       # axis along which the tunnel runs, direction of the outside, fixed coordinate
    "east": ("x", 1, CROSS_E),
    "south": ("z", 1, CX),
    "north": ("z", -1, CX),
}
gate_cols = {}      # (x, z) -> (kind, lateral offset)
for gname, (axis, sgn, fixed) in GATES.items():
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if (x, z) not in HEIGHT:
                continue
            o = off(x, z)
            if o < -0.5 or o > 15.5:
                continue
            if axis == "x":
                lat, along = z - fixed, (x - CX) * sgn
            else:
                lat, along = x - fixed, (z - CZ) * sgn
            if along < 0 or abs(lat) > 3:
                continue
            gate_cols[(x, z)] = (gname, lat, o)

TUNNEL_FLOOR = area.mix({"polished_tuff": 4, "stone_bricks": 3, "mossy_stone_bricks": 2, "slime_block": 0})
for (x, z), (gname, lat, o) in gate_cols.items():
    if abs(lat) == 3:
        top = max(SEAT_TOP.get((x, z), G), 74)
        for y in range(G + 1, top + 1):
            S(x, y, z, "minecraft:stone_bricks" if hash01(x, y, z, 21) < 0.7 else "minecraft:mossy_stone_bricks")
        continue
    S(x, G, z, TUNNEL_FLOOR(x, G, z))
    for y in range(G + 1, 73):
        S(x, y, z, "minecraft:air")
    S(x, 73, z, "minecraft:tuff_bricks" if hash01(x, 1, z, 22) < 0.8 else "minecraft:mossy_stone_bricks")
    for y in range(74, SEAT_TOP.get((x, z), 74) + 1):
        pass
    if abs(lat) == 2 and o > 0:
        S(x, 72, z, "minecraft:stone_brick_stairs[facing=%s,half=top]" % ("south" if lat < 0 else "north")
          if GATES[gname][0] == "x" else "minecraft:stone_brick_stairs[facing=%s,half=top]" % ("east" if lat < 0 else "west"))
    # lights in the roof
    if lat == 0 and int(o) % 5 == 2:
        S(x, 73, z, "minecraft:sea_lantern")
        S(x, 72, z, "minecraft:purple_stained_glass")
    # wall banners on the tunnel walls
    if abs(lat) == 2 and int(o) % 6 == 3 and o > 1:
        if GATES[gname][0] == "x":
            S(x, 71, z, "%s_wall_banner[facing=%s]" % ("green" if int(o) % 12 == 3 else "lime", "south" if lat < 0 else "north"))
        else:
            S(x, 71, z, "%s_wall_banner[facing=%s]" % ("green" if int(o) % 12 == 3 else "lime", "east" if lat < 0 else "west"))


def pillar(x, z, y0, h, broken=False, seed=0):
    S(x, y0, z, "minecraft:calcite")
    for y in range(y0 + 1, y0 + h):
        S(x, y, z, "minecraft:quartz_pillar[axis=y]")
    if not broken:
        S(x, y0 + h, z, "minecraft:chiseled_quartz_block")
    else:
        S(x, y0 + h, z, "minecraft:quartz_stairs[facing=%s,half=bottom]" % ("north", "south", "east", "west")[int(hash01(x, z, 1, seed) * 4)])
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if hash01(x + dx, y0, z + dz, seed + 3) < 0.4:
                S(x + dx, y0 + 1, z + dz, "minecraft:quartz_slab" if hash01(x, y0, z, seed + 4) < 0.5 else "minecraft:tuff_slab")


# gate mouths: tall pillar pairs with a lintel, banners
for gname, (axis, sgn, fixed) in GATES.items():
    if axis == "x":
        mx = CX + sgn * 0
        # outer mouth: where the oval distance reaches 15 blocks out along z = fixed
        xs = [x for x in range(X0, X1 + 1) if abs(off(x, fixed) - 15) < 0.8 and (x - CX) * sgn > 0]
        ox = xs[0] if xs else (CX + sgn * 38)
        for lat in (-3, 3):
            pillar(ox, fixed + lat, G + 1, 10, seed=2)
        for lat in range(-3, 4):
            S(ox, 78, fixed + lat, "minecraft:tuff_bricks" if abs(lat) < 3 else "minecraft:chiseled_quartz_block")
            S(ox, 77, fixed + lat, "minecraft:stone_brick_slab[type=top]" if abs(lat) < 3 else "minecraft:stone_bricks")
        S(ox - sgn, 76, fixed - 3, "%s_wall_banner[facing=%s]" % ("green", "west" if sgn < 0 else "east"))
        S(ox - sgn, 76, fixed + 3, "%s_wall_banner[facing=%s]" % ("green", "west" if sgn < 0 else "east"))
        S(ox + sgn, 75, fixed - 3, "lime_wall_banner[facing=%s]" % ("west" if sgn < 0 else "east"))
        S(ox + sgn, 75, fixed + 3, "lime_wall_banner[facing=%s]" % ("west" if sgn < 0 else "east"))
    else:
        zs = [z for z in range(Z0, Z1 + 1) if abs(off(fixed, z) - 15) < 0.8 and (z - CZ) * sgn > 0]
        oz = zs[0] if zs else (CZ + sgn * 28)
        for lat in (-3, 3):
            pillar(fixed + lat, oz, G + 1, 10, seed=2)
        for lat in range(-3, 4):
            S(fixed + lat, 78, oz, "minecraft:tuff_bricks" if abs(lat) < 3 else "minecraft:chiseled_quartz_block")
            S(fixed + lat, 77, oz, "minecraft:stone_brick_slab[type=top]" if abs(lat) < 3 else "minecraft:stone_bricks")
        fa = "north" if sgn < 0 else "south"
        S(fixed - 3, 76, oz + sgn, "green_wall_banner[facing=%s]" % fa)
        S(fixed + 3, 76, oz + sgn, "green_wall_banner[facing=%s]" % fa)
GATE_MOUTH = {}

# ---------------------------------------------------------------- the colonnade on the back wall
slots = []
N_SLOT = 30
for i in range(N_SLOT):
    th = math.radians(i * 360 / N_SLOT + 6)
    ddv = 1 + 14.2 / K
    px, pz = int(round(CX + A * ddv * math.cos(th))), int(round(CZ + B * ddv * math.sin(th)))
    slots.append((px, pz, i))
tops = []
for (px, pz, i) in slots:
    if (px, pz) in gate_cols or any(abs(px - gx) < 4 and abs(pz - gz) < 4 for gx, gz in
                                    ((CX, CZ + 28), (CX, CZ - 28), (CX + 37, -180), (CX - 37, -180))):
        tops.append(None)
        continue
    cut = sector_cut(px, pz)
    base = max(SEAT_TOP.get((px, pz), BACK), G + 1)
    h = 4 + int(hash01(px, pz, 1, 30) * 8)
    broken = cut > 0.8 or hash01(px, pz, 2, 31) < 0.3
    if cut > 2.5 and hash01(px, pz, 3, 32) < 0.6:
        h = 1 + int(hash01(px, pz, 4, 33) * 2)
    pillar(px, pz, base + 1, h, broken, seed=i)
    tops.append((px, pz, base + 1 + h, broken))
    if not broken and h >= 7 and i % 3 == 0:
        S(px, base + 1 + h + 1, pz, "minecraft:end_rod[facing=up]")
# arches between neighbouring intact tall pillars
for i in range(N_SLOT):
    a, b = tops[i], tops[(i + 1) % N_SLOT]
    if a is None or b is None or a[3] or b[3] or hash01(i, 1, 2, 34) < 0.35:
        continue
    y = min(a[2], b[2])
    n = max(abs(a[0] - b[0]), abs(a[1] - b[1]))
    for s in range(1, n):
        px = int(round(a[0] + (b[0] - a[0]) * s / n))
        pz = int(round(a[1] + (b[1] - a[1]) * s / n))
        S(px, y, pz, "minecraft:tuff_bricks")
        if hash01(px, pz, 1, 35) < 0.2:
            S(px, y + 1, pz, "minecraft:moss_carpet")
        elif hash01(px, pz, 2, 35) < 0.5:
            S(px, y - 1, pz, "minecraft:stone_brick_slab[type=top]")

# banners hung on the podium wall facing the arena
for i in range(18):
    th = math.radians(i * 20 + 3)
    bx, bz = int(round(CX + A * 0.96 * math.cos(th))), int(round(CZ + B * 0.96 * math.sin(th)))
    nx, nz = math.cos(th) / A, math.sin(th) / B
    if abs(nx) > abs(nz):
        wx, wz = (1 if nx > 0 else -1), 0
        fa = "west" if nx > 0 else "east"
    else:
        wx, wz = 0, (1 if nz > 0 else -1)
        fa = "north" if nz > 0 else "south"
    if (bx + wx, bz + wz) in gate_cols or (bx, bz) in gate_cols or off(bx, bz) >= 0 or off(bx + wx, bz + wz) < 0:
        continue
    S(bx, 70, bz, "%s_wall_banner[facing=%s]" % (("green", "lime", "white")[i % 3], fa))

# lamps on the podium top
for i in range(12):
    th = math.radians(i * 30 + 15)
    lx, lz = int(round(CX + A * (1 + 0.7 / K) * math.cos(th))), int(round(CZ + B * (1 + 0.7 / K) * math.sin(th)))
    if (lx, lz) in gate_cols or off(lx, lz) < 0.2 or off(lx, lz) > 1.4 or SEAT_TOP.get((lx, lz)) != PODIUM_TOP:
        continue
    S(lx, 71, lz, "minecraft:stone_brick_wall")
    S(lx, 72, lz, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(lx + dx, 72, lz + dz, "minecraft:purple_stained_glass")
    S(lx, 73, lz, "minecraft:purple_stained_glass")

# ---------------------------------------------------------------- the emperor's box (north side, top tier): the Enigma Soul
BXC = 149
BOX_X1, BOX_X2, BOX_Z1, BOX_Z2 = 143, 155, -222, -215
BOXY = 79
for x in range(BOX_X1, BOX_X2 + 1):
    for z in range(BOX_Z1, BOX_Z2 + 1):
        if (x, z) not in HEIGHT:
            continue
        for y in range(G + 1, BOXY + 1):
            S(x, y, z, "minecraft:tuff_bricks")
        S(x, BOXY, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:calcite")
        for y in range(BOXY + 1, BOXY + 8):
            S(x, y, z, "minecraft:air")
for x in range(BOX_X1, BOX_X2 + 1):          # parapet on the back and sides
    for z in range(BOX_Z1, BOX_Z2 + 1):
        if x in (BOX_X1, BOX_X2) or z == BOX_Z1:
            S(x, BOXY + 1, z, "minecraft:stone_brick_wall")
for (px, pz) in ((BOX_X1, BOX_Z1), (BOX_X2, BOX_Z1), (BOX_X1, BOX_Z2), (BOX_X2, BOX_Z2)):
    pillar(px, pz, BOXY + 1, 6, False, seed=7)
for x in range(BOX_X1, BOX_X2 + 1):          # lintel over the back
    S(x, BOXY + 7, BOX_Z1, "minecraft:tuff_bricks")
for z in range(BOX_Z1, BOX_Z2 + 1):
    for x in (BOX_X1, BOX_X2):
        S(x, BOXY + 7, z, "minecraft:tuff_bricks")
for x in range(BOX_X1 + 1, BOX_X2):
    S(x, BOXY + 1, BOX_Z1 + 0, "minecraft:stone_brick_wall")
    if x % 3 == 0:
        S(x, BOXY + 2, BOX_Z1 + 1, "green_wall_banner[facing=south]") if False else None
for x in (BXC - 2, BXC + 2):
    S(x, BOXY + 3, BOX_Z1 + 1, "green_wall_banner[facing=south]")
S(BXC, BOXY + 3, BOX_Z1 + 1, "lime_wall_banner[facing=south]")
S(BXC, BOXY + 6, BOX_Z1 + 1, "minecraft:sea_lantern")
# a slime-stained throne
S(BXC, BOXY + 1, BOX_Z1 + 1, "minecraft:quartz_stairs[facing=south,half=bottom]")
S(BXC, BOXY + 2, BOX_Z1 + 1, "minecraft:quartz_stairs[facing=south,half=top]") if False else None
S(BXC - 1, BOXY + 1, BOX_Z1 + 1, "minecraft:slime_block")
for j in range(1, 4):                         # the stairs down into the seating
    zz = BOX_Z2 + j
    for x in range(BXC - 2, BXC + 3):
        for y in range(G + 1, BOXY - j):
            S(x, y, zz, "minecraft:tuff_bricks")
        S(x, BOXY - j, zz, "minecraft:stone_brick_stairs[facing=south,half=bottom]")
        for y in range(BOXY - j + 1, BOXY + 8):
            S(x, y, zz, "minecraft:air")
SOUL = (BXC, BOXY + 1, BOX_Z1 + 3)
S(SOUL[0] + 1, BOXY + 1, SOUL[2], "minecraft:green_candle[candles=3,lit=true]")

# ---------------------------------------------------------------- the forum
def lamp(x, y, z):
    for h in range(3):
        S(x, y + h, z, "minecraft:stone_brick_wall")
    S(x, y + 3, z, "minecraft:sea_lantern")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, y + 3, z + dz, "minecraft:purple_stained_glass")
    S(x, y + 4, z, "minecraft:purple_stained_glass")
    S(x, y + 5, z, "minecraft:end_rod[facing=up]")


occ = set()


def free(x, z, r=0):
    return all((x + dx, z + dz) not in occ for dx in range(-r, r + 1) for dz in range(-r, r + 1))


def reserve(cx, cz, r):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            occ.add((cx + dx, cz + dz))


for c in path_cols:
    occ.add(c)
for (x, z) in gate_cols:
    occ.add((x, z))
for x in range(X0, X1 + 1):                 # keep the approach lanes clear
    for z in range(Z0, Z1 + 1):
        if off(x, z) < 16 or in_goo(x, z) and False:
            occ.add((x, z))
for x in range(CROSS_S - 3, CROSS_S + 4):
    for z in range(FORUM[3] - 40, Z1 + 1):
        if z > FORUM[2]:
            occ.add((x, z))

# fountain: a round basin with a slime-covered pedestal
for dx in range(-6, 7):
    for dz in range(-6, 7):
        d = math.hypot(dx, dz)
        x, z = FX + dx, FZ + dz
        if d < 4.4:
            S(x, G - 1, z, "minecraft:mossy_stone_bricks")
            S(x, G, z, "minecraft:water")
            if hash01(x, 1, z, 40) < 0.25:
                S(x, G + 1, z, "minecraft:lily_pad")
        elif d < 5.6:
            S(x, G, z, "minecraft:stone_bricks")
            S(x, G + 1, z, "minecraft:moss_block" if hash01(x, 2, z, 41) < 0.35 else
              "minecraft:slime_block" if hash01(x, 3, z, 41) < 0.2 else "minecraft:mossy_stone_brick_slab")
        elif d < 7.0 and hash01(x, 4, z, 42) < 0.3:
            S(x, G, z, "minecraft:moss_block")
reserve(FX, FZ, 7)
for y in range(G, G + 4):
    S(FX, y, FZ, "minecraft:mossy_stone_bricks" if y < G + 2 else "minecraft:slime_block")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    S(FX + dx, G + 3, FZ + dz, "minecraft:slime_block")
    S(FX + dx * 2, G + 2, FZ + dz * 2, "minecraft:slime_block")
S(FX, G + 4, FZ, "minecraft:lime_stained_glass")
S(FX, G + 5, FZ, "minecraft:end_rod[facing=up]")

# broken columns: two rows along the forum sides and one across its south, plus fallen ones
col_count = 0
sites = []
for z in range(-158, -130, 7):
    sites += [(103, z), (167, z)]
for x in range(104, 170, 9):
    sites += [(x, -132)]
for (x, z) in sites:
    if not free(x, z, 1) or math.hypot(x - FX, z - FZ) < 9:
        continue
    r = hash01(x, z, 1, 50)
    if r < 0.45:
        pillar(x, z, G + 1, 6 + int(hash01(x, z, 2, 50) * 4), False, seed=5)
    elif r < 0.8:
        pillar(x, z, G + 1, 2 + int(hash01(x, z, 3, 50) * 4), True, seed=6)
    else:   # a fallen column
        ax = "x" if hash01(x, z, 4, 50) < 0.5 else "z"
        ln = 4 + int(hash01(x, z, 5, 50) * 2)
        for i in range(ln):
            S(x + (i if ax == "x" else 0), G + 1, z + (i if ax == "z" else 0), "minecraft:quartz_pillar[axis=%s]" % ax)
        S(x - 1, G + 1, z, "minecraft:calcite")
    reserve(x, z, 1)
    col_count += 1

# slime puddles and rubble in the forum
for x in range(FORUM[0], FORUM[1] + 1):
    for z in range(FORUM[2], FORUM[3] + 1):
        if not in_forum(x, z) or not free(x, z):
            continue
        h = hash01(x, 1, z, 60)
        if h < 0.012:
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    if hash01(x + dx, 2, z + dz, 61) < 0.7 and free(x + dx, z + dz):
                        S(x + dx, G, z + dz, "minecraft:slime_block")
        elif h < 0.03:
            S(x, G + 1, z, "minecraft:mossy_cobblestone_slab" if hash01(x, 3, z, 62) < 0.5 else "minecraft:tuff_slab")
        elif h < 0.045:
            S(x, G + 1, z, "minecraft:moss_carpet")
# forum lamps
for (lx, lz) in ((100, -160), (170, -160), (100, -132), (170, -132), (118, -146), (152, -146), (126, -134), (144, -134)):
    if free(lx, lz, 0):
        lamp(lx, G + 1, lz)
        reserve(lx, lz, 1)

# ---------------------------------------------------------------- Gooey's corner
gx1, gx2, gz1, gz2 = GOO
for x in range(gx1, gx2 + 1):
    for z in range(gz1, gz2 + 1):
        edge_x, edge_z = x in (gx1, gx2), z in (gz1, gz2)
        if edge_x or edge_z:
            if z == CROSS_E + 46 and False:
                continue
            gate = (x == gx1 and -136 <= z <= -132)
            if gate:
                continue
            hgt = 1 + int(hash01(x, z, 1, 70) * 3)
            for y in range(G + 1, G + 1 + hgt):
                S(x, y, z, "minecraft:mossy_stone_bricks" if hash01(x, y, z, 71) < 0.4 else "minecraft:stone_bricks")
        else:
            h = hash01(x, 2, z, 72)
            if h < 0.1:
                S(x, G, z, "minecraft:slime_block")
            elif h < 0.2:
                S(x, G, z, "minecraft:moss_block")
reserve((gx1 + gx2) // 2, (gz1 + gz2) // 2, 9)
for z in (-137, -131):
    pillar(gx1, z, G + 1, 6, False, seed=3)
for z in range(-137, -130):
    S(gx1, G + 7, z, "minecraft:tuff_bricks")
S(gx1 - 1, G + 5, -137, "green_wall_banner[facing=west]")
S(gx1 - 1, G + 5, -131, "green_wall_banner[facing=west]")
for x in (gx1 + 1, gx2 - 1):
    pass
GE = (gx2 - 3, G + 1, -134)
S(GE[0], G + 1, GE[2], "minecraft:chiseled_stone_bricks")         # pedestal
S(GE[0], G + 2, GE[2], "end_portal_frame[eye=true,facing=west]")
for dx, dz in ((0, 1), (0, -1), (1, 0)):
    S(GE[0] + dx, G + 1, GE[2] + dz, "minecraft:slime_block")
for z in (gz1 + 1, gz2 - 1):
    lamp(gx2 - 2, G + 1, z)
GOOEY = (GE[0] - 2, G + 1, GE[2])

# ---------------------------------------------------------------- scatter: plants, ruins and lamps outside
for (x, z), top in HEIGHT.items():
    if (x, z) in occ or (x, z) in SEAT_TOP or in_forum(x, z) or in_goo(x, z) or off(x, z) < 16:
        continue
    surf = area.get(x, top, z) or ""
    if not surf.endswith(("grass_block", "moss_block", "rooted_dirt")):
        continue
    h = hash01(x, 1, z, 80)
    if h < 0.1:
        S(x, top + 1, z, "minecraft:short_grass")
    elif h < 0.14:
        S(x, top + 1, z, "minecraft:fern")
    elif h < 0.16:
        S(x, top + 1, z, "minecraft:pink_petals[flower_amount=2,facing=north]")
    elif h < 0.165:
        S(x, top + 1, z, "minecraft:moss_carpet")
    elif h < 0.171 and free(x, z, 1):
        pillar(x, top + 1, z, 1 + int(hash01(x, 2, z, 81) * 3), True, seed=8)     # a stub of a column
        reserve(x, z, 1)

# lamps along the approaches
lamps = 0
for name, (pts, w) in PATHS.items():
    if name in ("north",):
        continue
    total = 0.0
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        length = math.hypot(x2 - x1, z2 - z1)
        n = int(length)
        for i in range(n):
            total += 1
            if total % 12 != 0:
                continue
            px, pz = x1 + (x2 - x1) * i / max(1, n), z1 + (z2 - z1) * i / max(1, n)
            nx, nz = -(z2 - z1) / length, (x2 - x1) / length
            side = 1 if (lamps % 2) else -1
            lx, lz = int(round(px + nx * (w / 2 + 1.5) * side)), int(round(pz + nz * (w / 2 + 1.5) * side))
            if (lx, lz) in HEIGHT and (lx, lz) not in path_cols and off(lx, lz) > 16 and not in_forum(lx, lz):
                lamp(lx, HEIGHT[(lx, lz)] + 1, lz)
                lamps += 1

# gate arches at the three crossings (decoration): a pair of tall pillars with a lintel where the path reaches the seam
for (x, z, ax) in ((X0 + 8, CROSS_W, "z"), (X1 - 8, CROSS_E, "z"), (CROSS_S, Z1 - 8, "x")):
    for lat in (-4, 4):
        px, pz = (x, z + lat) if ax == "z" else (x + lat, z)
        pillar(px, pz, G + 1, 7, hash01(px, pz, 1, 90) < 0.3, seed=9)

# ---------------------------------------------------------------- NPCs, souls, spawn spots
area.npc("Gooey", *GOOEY, 90.0, "a Porhtal eye in Gooey's corner (the walled courtyard in the south-east of the forum)")
area.npc("Colosseum Guard", CX - 5, G + 1, -161, 0.0, "beside the main (south) gate of the Colosseum")
area.npc("enigma:Colosseum", *SOUL, 0.0, "in the emperor's box, on the top tier at the north side (stairs up through the seating)")
area.npc("spawn:bacte", CX, G + 1, CZ, 0.0, "the Bacte boss spawns at the arena's centre")
for i in range(6):
    th = math.radians(i * 60 + 30)
    area.npc(f"spawn:bacte_tentacle_{i + 1}", int(round(CX + 12.5 * math.cos(th))), G + 1,
             int(round(CZ + 9.5 * math.sin(th))), 0.0, "a tentacle of the Bacte boss")
area.npc("spawn:colosseum_exit", CX, G + 1, -159, 0.0, "just outside the main gate, where players come out of the Colosseum")


# ---------------------------------------------------------------- zone and mobs
def mob(kind, name, level, health, damage, xp, coins, mx_, every, a, b, drops=None):
    """A Rift mob: `coins` are the Motes it drops (the mod pays Motes for kills in the Rift)."""
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx_, "every": every, "from": list(a), "to": list(b), "indoors": True}
    if drops:
        spec["drops"] = drops
    return spec


def drop(item, chance, lo, hi):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


area.zone("Colosseum", box=((X0, 56, Z0), (X1, 140, Z1)), blocks={}, mobs=[
    mob("minecraft:zombie", "Autoboots", 5, 150, 6, 6, 500, 5, 8, (98, 66, -160), (131, 80, -124)),
    mob("minecraft:zombie", "Autoboots", 5, 150, 6, 6, 500, 5, 8, (139, 66, -160), (173, 80, -124)),
])
area.save()
print("columns", col_count, "lamps", lamps, "soul", SOUL, "gooey", GOOEY)
