#!/usr/bin/env python3
"""The Crimson Isle, EAST region (box x 100..329, z -250..249): the Mage faction's side.

    Scarleton           walled arcane town (x 185..305, z -218..-112), Queen Nyx's palace, bank, auction, bazaar, workshop
    Mage Outpost        a wizard tower and tents at the north bridge's east end (x 112..152, z -100..-60)
    The Cathedral       gothic blackstone cathedral with a tall spire (x 251..289, z -97..-36)
    The Courtyard       walled paved yard in front of it (x 232..298, z -32..4)
    The Mystic Marsh    mycelium and podzol bog, lava puddles, giant mushrooms (x 108..324, z 0..120)
    The Dojo            Japanese-style dojo with a 31x31 arena (x 197..303, z 128..222)
    Odger's Hut         stilt hut and pier over the lava (x 141..159, z 197..249)

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python crimson_east.py
"""
import math
from crimson_lib import *   # noqa: F401,F403  (Area bits, constants, land, bridge_half, sign, MOBS, mob)

area = new_area("east")
G = 65                      # the towns' ground (players stand at 66)


# ================================================================ small helpers
def put(x, y, z, b):
    area.set(x, y, z, b)


def fill(x1, y1, z1, x2, y2, z2, b):
    area.fill(x1, y1, z1, x2, y2, z2, b)


def stair(b, f, half="bottom"):
    return f"{b}[facing={f},half={half}]"


def slab(b, t="bottom"):
    return f"{b}[type={t}]"


def axis(b, a="y"):
    return f"{b}[axis={a}]"


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def n01(x, z, seed, scale=1.0):
    return 0.5 + 0.5 * noise(x, z, seed, scale)


YAW = {"s": 0.0, "w": 90.0, "n": 180.0, "e": -90.0}
FACE = {"s": "south", "w": "west", "n": "north", "e": "east"}
RES = set()                 # columns the scatter (plants, fungi, mushrooms) must leave alone


def reserve(x1, z1, x2, z2, m=0):
    for x in range(min(x1, x2) - m, max(x1, x2) + m + 1):
        for z in range(min(z1, z2) - m, max(z1, z2) + m + 1):
            RES.add((x, z))


def pave(x1, z1, x2, z2, y, pick):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            put(x, y, z, pick(x, z) if callable(pick) else pick)
            area.top[(x, z)] = y


def door(x, y, z, facing, kind="crimson_door", hinge="left"):
    put(x, y, z, f"{kind}[facing={facing},half=lower,hinge={hinge}]")
    put(x, y + 1, z, f"{kind}[facing={facing},half=upper,hinge={hinge}]")


def lamp_post(x, y, z, lamp="lantern[hanging=false]", fence="nether_brick_fence", h=3):
    for i in range(1, h + 1):
        put(x, y + i, z, fence)
    put(x, y + h + 1, z, lamp)


def column_down(x, z, ytop, block, ybottom=60):
    """Fills from ytop down until the block below is solid (a tower standing on a sloped roof)."""
    y = ytop
    while y > ybottom:
        put(x, y, z, block)
        if area.solid(x, y - 1, z):
            break
        y -= 1


def ellipsoid(cx, cy, cz, rx, ry, rz):
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2 <= 1.0:
                    yield x, y, z


# ================================================================ terrain: heights, coast, roads
# Plateaus (x1, z1, x2, z2, y, margin): the ground is levelled to y inside, blending out over `margin` blocks.
PLATEAUS = [
    (170, -232, 320, -98, 65, 12),     # Scarleton
    (230, -106, 304, 8, 65, 10),       # Cathedral and Courtyard
    (112, -100, 152, -60, 66, 8),      # Mage Outpost (deck height at the bridge)
    (195, 126, 305, 226, 65, 8),       # the Dojo
]
CROSS = CROSSINGS[100]              # (-80, 110)

# Roads (points, width). Heights follow the terrain, smoothed; they start flat at y 66 at the bridge ends.
ROADS = [
    ("north", [(119, -80), (158, -80), (170, -110), (172, -150), (178, -165), (184, -165)], 5),
    ("south", [(119, 110), (150, 110), (190, 112), (222, 110), (239, 112), (239, 127)], 5),
    ("odger", [(160, 111), (158, 150), (152, 190), (150, 196)], 3),
    ("ns", [(245, -111), (234, -106), (224, -100), (222, -90), (222, -12), (222, 20), (222, 60), (222, 108)], 4),
    ("yardgate", [(222, -14), (232, -14)], 3),
    ("yardsouth", [(265, 5), (262, 30), (240, 60), (224, 66)], 3),
]

_bh_cache = {}


def plat(x, z):
    best, hy = 0.0, None
    for (a, b, c, d, y, m) in PLATEAUS:
        dd = math.hypot(max(a - x, 0, x - c), max(b - z, 0, z - d))
        w = 1.0 if dd == 0 else smooth(1 - dd / m)
        if w > best:
            best, hy = w, y
    return best, hy


def base_h(x, z):
    k = (x, z)
    if k in _bh_cache:
        return _bh_cache[k]
    t = 64.6 + 1.5 * noise(x, z, 11, 0.9) + 0.8 * noise(x, z, 5, 2.3)
    t = max(63.0, min(66.4, t))
    w, y = plat(x, z)
    h = t * (1 - w) + y * w if y is not None else t
    for cz in CROSS:
        wb = smooth(1 - (x - 108) / 22) * smooth(1 - max(0, abs(z - cz) - 6) / 8)
        h = h * (1 - wb) + BRIDGE * wb
    _bh_cache[k] = (h, w)
    return h, w


def samples(pts):
    out = []
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(z2 - z1), 1)
        for i in range(n):
            out.append((x1 + (x2 - x1) * i / n, z1 + (z2 - z1) * i / n))
    out.append(pts[-1])
    return out


road_ov = {}                # (x, z) -> (height, weight)
road_cells = {}             # (x, z) -> road name (the cells that get paving)
for name, pts, wd in ROADS:
    sm = samples(pts)
    hs = [base_h(int(round(x)), int(round(z)))[0] for x, z in sm]
    avg = []
    for i in range(len(hs)):
        lo, hi = max(0, i - 8), min(len(hs), i + 9)
        avg.append(sum(hs[lo:hi]) / (hi - lo))
    R = wd / 2 + 3.5
    for (sx, sz), h in zip(sm, avg):
        for x in range(int(sx - R) - 1, int(sx + R) + 2):
            for z in range(int(sz - R) - 1, int(sz + R) + 2):
                d = math.hypot(x - sx, z - sz)
                if d > R:
                    continue
                if d <= wd / 2 + 0.3:
                    road_cells[(x, z)] = name
                wg = 1.0 if d <= wd / 2 + 0.5 else smooth(1 - (d - wd / 2 - 0.5) / 3.0)
                if (x, z) not in road_ov or wg > road_ov[(x, z)][1]:
                    road_ov[(x, z)] = (h, wg)


def coast_d(x, z):
    cz_c = min(abs(z - c) for c in CROSS)
    f = min(1.0, max(0.0, (cz_c - 6) / 8))
    xw = 107 + f * (2 + 4 * n01(3, z, 3, 1.3))
    xe = 320 + 5 * n01(x * 0 + 7, z, 4, 1.1)
    zn = -235 + 6 * n01(x, 1, 5, 1.2)
    zs = 244 - 44 * math.exp(-((x - 150) / 30.0) ** 2) + 3 * noise(x, 9, 6, 1.4)
    return min(x - xw, xe - x, z - zn, zs - z)


def final_h(x, z):
    d = coast_d(x, z)
    if d < 0:
        return None
    h, pw = base_h(x, z)
    if (x, z) in road_ov and pw < 1:
        rh, wg = road_ov[(x, z)]
        h = h + (rh - h) * wg
    top = int(round(h))
    t = min(top, 60 + int(d))
    cz_c = min(abs(z - c) for c in CROSS)
    cf = max(0.0, min(1.0, (9 - cz_c) / 4)) * (1.0 if x <= 128 else 0.0)
    if cf > 0:
        t = int(round(t * (1 - cf) + top * cf))
    return t


MARSH_LO, MARSH_HI = 0, 120


def in_marsh(x, z):
    lo = MARSH_LO + 4 * noise(x, 2, 8, 0.7)
    hi = MARSH_HI + 5 * noise(x, 3, 9, 0.7)
    if not (108 <= x <= 323 and lo <= z <= hi):
        return False
    return base_h(x, z)[1] < 0.5


def in_dojo(x, z):
    return 197 <= x <= 303 and 128 <= z <= 224


nether = area.mix({"crimson_nylium": 6, "netherrack": 2, "warped_nylium": 1})
nether_under = area.mix({"netherrack": 5, "crimson_nylium": 1})
nether_body = area.mix({"netherrack": 6, "blackstone": 3, "cobbled_deepslate": 1})
marsh_top = area.mix({"mycelium": 5, "podzol": 4, "mud": 1})
marsh_under = area.mix({"dirt": 3, "mud": 2, "coarse_dirt": 1})
marsh_body = area.mix({"netherrack": 5, "blackstone": 2, "mud": 1})
beach_top = area.mix({"soul_soil": 3, "blackstone": 2, "netherrack": 1})
dojo_top = area.mix({"grass_block": 5, "moss_block": 2, "coarse_dirt": 1})
dojo_under = area.mix({"dirt": 4, "coarse_dirt": 1})

HT = {}
KIND = {}
for x in range(106, 330):
    for z in range(-250, 250):
        t = final_h(x, z)
        if t is not None:
            HT[(x, z)] = t
for (x, z), t in HT.items():
    if t <= 62:
        kind = "beach"
        land(area, x, z, t, beach_top, "netherrack", nether_body)
    elif in_dojo(x, z) and base_h(x, z)[1] > 0.99:
        kind = "dojo"
        land(area, x, z, t, dojo_top, dojo_under, nether_body)
    elif in_marsh(x, z):
        kind = "marsh"
        land(area, x, z, t, marsh_top, marsh_under, marsh_body)
    else:
        kind = "wild"
        land(area, x, z, t, nether, nether_under, nether_body)
    KIND[(x, z)] = kind


def top_at(x, z):
    return HT.get((x, z))


# Roads: paving on the surface.
roadmix = area.mix({"polished_blackstone_bricks": 4, "blackstone": 3, "cracked_polished_blackstone_bricks": 1,
                    "polished_blackstone": 2})
marsh_road = area.mix({"packed_mud": 4, "mud_bricks": 3, "coarse_dirt": 1})
for (x, z), name in road_cells.items():
    t = HT.get((x, z))
    if t is None or t <= 61:
        continue
    pw = base_h(x, z)[1]
    if pw >= 1 and (180 <= x <= 310 and -220 <= z <= -110):
        continue                    # Scarleton's own streets are paved by the town
    b = (marsh_road if KIND.get((x, z)) == "marsh" else roadmix)(x, t, z, 4)
    put(x, t, z, b)
    RES.add((x, z))


def road_lamps(spacing=14, lamp="lantern[hanging=false]"):
    for name, pts, wd in ROADS:
        sm = samples(pts)
        for i in range(6, len(sm) - 3, spacing):
            sx, sz = sm[i]
            nx, nz = sm[min(len(sm) - 1, i + 2)]
            ax, az = nx - sx, nz - sz
            ln = math.hypot(ax, az) or 1
            px, pz = -az / ln, ax / ln
            side = 1 if (i // spacing) % 2 else -1
            x, z = int(round(sx + px * (wd / 2 + 1.2) * side)), int(round(sz + pz * (wd / 2 + 1.2) * side))
            t = HT.get((x, z))
            if t is None or t <= 61 or (x, z) in RES:
                continue
            kind = KIND.get((x, z))
            lamp_post(x, t, z, "shroomlight" if kind == "marsh" else lamp, "crimson_fence" if kind != "marsh" else "dark_oak_fence")
            RES.add((x, z))


# Bridge halves (north z -80, south z 110): from the land to the box edge x 100.
for cz in CROSS:
    bridge_half(area, 100, 118, cz)
    reserve(100, cz - 4, 126, cz + 4)


# ================================================================ building kit
def roof(x1, z1, x2, z2, y0, stair_b, slab_b, fillb, axis_="auto", over=1):
    """A gabled roof over the wall rectangle: the first row (the eaves) at y0, rising one per row; ridge along the
    longer side; gable ends filled with `fillb`."""
    if axis_ == "auto":
        axis_ = "x" if x2 - x1 >= z2 - z1 else "z"
    if axis_ == "x":
        lo, hi, a1, a2, e1, e2 = z1 - over, z2 + over, x1 - over, x2 + over, x1, x2
        lf, hf = "north", "south"
    else:
        lo, hi, a1, a2, e1, e2 = x1 - over, x2 + over, z1 - over, z2 + over, z1, z2
        lf, hf = "west", "east"
    yy = y0
    while lo <= hi:
        for a in range(a1, a2 + 1):
            def cell(c, b):
                if axis_ == "x":
                    put(a, yy, c, b)
                else:
                    put(c, yy, a, b)
            if lo == hi:
                cell(lo, slab_b)
            else:
                cell(lo, stair(stair_b, lf))
                cell(hi, stair(stair_b, hf))
                if a in (e1, e2):
                    for c in range(lo + 1, hi):
                        cell(c, fillb)
        lo += 1
        hi -= 1
        yy += 1
    return yy


def spire(cx, cz, y, half, step, block, band=None, band_every=6, rod=3):
    """A stepped pyramid: `step` layers per size, shrinking from `half` to 0, with an end rod on top."""
    k = 0
    for h in range(half, -1, -1):
        for _ in range(step):
            b = band if (band and k % band_every == band_every - 1) else block
            fill(cx - h, y + k, cz - h, cx + h, y + k, cz + h, b)
            k += 1
    for i in range(rod):
        put(cx, y + k + i, cz, "end_rod[facing=up]")
    return y + k + rod


def building(x1, z1, x2, z2, h, st, door_side="s", door_at=None, over=1, door_w=1, axis_="auto", floor=None,
             win=3, lamps=True):
    """A house: floor at G, walls G+1..G+h, flat ceiling at G+h, gable roof from G+h. `st` is a style dict."""
    floor = floor or st["floor"]
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            put(x, G, z, floor)
            area.top[(x, z)] = G
            edge = x in (x1, x2) or z in (z1, z2)
            corner = x in (x1, x2) and z in (z1, z2)
            for y in range(G + 1, G + h):
                if corner:
                    put(x, y, z, st["pillar"])
                elif edge:
                    put(x, y, z, st["wall"])
                else:
                    put(x, y, z, "air")
            put(x, G + h, z, st["wall"] if edge else st["ceil"])
    if door_side in ("n", "s"):
        dz = z1 if door_side == "n" else z2
        dx = door_at if door_at is not None else (x1 + x2) // 2
    else:
        dx = x1 if door_side == "w" else x2
        dz = door_at if door_at is not None else (z1 + z2) // 2
    dpos = (dx, dz)
    ys = (G + 2, G + 3) if h >= 5 else (G + 2,)
    for x in range(x1 + 1, x2):
        for z in (z1, z2):
            if (x - x1) % win == win // 2 + 1 and not (door_side in "ns" and z == dz and abs(x - dx) <= 1 + door_w // 2):
                for y in ys:
                    put(x, y, z, st["pane"])
    for z in range(z1 + 1, z2):
        for x in (x1, x2):
            if (z - z1) % win == win // 2 + 1 and not (door_side in "ew" and x == dx and abs(z - dz) <= 1 + door_w // 2):
                for y in ys:
                    put(x, y, z, st["pane"])
    if door_w == 1:
        door(dx, G + 1, dz, FACE[door_side], st.get("door", "crimson_door"))
    else:
        for o in range(-(door_w // 2), door_w // 2 + 1):
            for y in range(G + 1, G + 1 + min(h - 1, 5)):
                if door_side in ("n", "s"):
                    put(dx + o, y, dz, "air")
                else:
                    put(dx, y, dz + o, "air")
    roof(x1, z1, x2, z2, G + h, st["stair"], st["slab"], st["fill"], axis_, over)
    if lamps:
        nx = max(1, (x2 - x1) // 6)
        nz = max(1, (z2 - z1) // 6)
        for i in range(nx):
            for j in range(nz):
                lx = x1 + (x2 - x1) * (2 * i + 1) // (2 * nx)
                lz = z1 + (z2 - z1) * (2 * j + 1) // (2 * nz)
                put(lx, G + h - 1, lz, "lantern[hanging=true]")
    reserve(x1 - over, z1 - over, x2 + over, z2 + over, 1)
    return dpos


def tower(x1, z1, x2, z2, y0, h, wall, pillar, glass, top="open", roof_b=None, crenel="red_nether_brick_wall",
          ladder=None, door_at=None, floor="polished_blackstone_bricks", steep=1, windows=True):
    """A square tower from y0 (the ground) with hollow walls up to y0+h. top='open': a platform at y0+h with a
    crenellated parapet (and the ladder's hole); top='roof': a steep stair pyramid."""
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            edge = x in (x1, x2) or z in (z1, z2)
            corner = x in (x1, x2) and z in (z1, z2)
            put(x, y0, z, floor)
            for y in range(y0 + 1, y0 + h):
                put(x, y, z, pillar if corner else wall if edge else "air")
            put(x, y0 + h, z, wall if edge else floor)
            if top == "open" and edge and ((x + z) % 2 == 0 or corner):
                put(x, y0 + h + 1, z, crenel)
    if windows and glass:
        for y in range(y0 + 4, y0 + h - 2, 6):
            for (fx, fz) in ((cx, z1), (cx, z2), (x1, cz), (x2, cz)):
                put(fx, y, fz, glass)
                put(fx, y + 1, fz, glass)
    if door_at:
        dx, dz, f = door_at
        door(dx, y0 + 1, dz, f)
    if ladder:
        lx, lz, f = ladder
        for y in range(y0 + 1, y0 + h + 1):
            put(lx, y, lz, f"ladder[facing={f}]")
    for y in range(y0 + 5, y0 + h, 6):
        put(cx, y, cz, "lantern[hanging=false]") if False else None
    put(cx, y0 + 1, cz, "lantern[hanging=false]")
    if top == "roof":
        r1, r2, s1, s2 = x1 - 1, x2 + 1, z1 - 1, z2 + 1
        yy = y0 + h
        while r1 <= r2 and s1 <= s2:
            for _ in range(steep):
                for x in range(r1, r2 + 1):
                    for z in range(s1, s2 + 1):
                        ring = x in (r1, r2) or z in (s1, s2)
                        if not ring or (x in (r1, r2) and z in (s1, s2)):
                            put(x, yy, z, roof_b["fill"])
                        elif z == s1:
                            put(x, yy, z, stair(roof_b["stair"], "north"))
                        elif z == s2:
                            put(x, yy, z, stair(roof_b["stair"], "south"))
                        elif x == r1:
                            put(x, yy, z, stair(roof_b["stair"], "west"))
                        else:
                            put(x, yy, z, stair(roof_b["stair"], "east"))
                yy += 1
            r1 += 1
            r2 -= 1
            s1 += 1
            s2 -= 1
        for i in range(3):
            put(cx, yy + i, cz, "end_rod[facing=up]" if i else roof_b["fill"])
    reserve(x1 - 2, z1 - 2, x2 + 2, z2 + 2)


# ================================================================ Scarleton
STYLES = [
    dict(wall="quartz_bricks", pillar="stripped_crimson_stem[axis=y]", pane="purple_stained_glass_pane",
         stair="purpur_stairs", slab="purpur_slab[type=bottom]", fill="purpur_block", floor="smooth_quartz",
         ceil="crimson_planks"),
    dict(wall="red_nether_bricks", pillar="polished_blackstone", pane="magenta_stained_glass_pane",
         stair="red_nether_brick_stairs", slab="red_nether_brick_slab[type=bottom]", fill="red_nether_bricks",
         floor="polished_blackstone_bricks", ceil="crimson_planks"),
    dict(wall="smooth_quartz", pillar="amethyst_block", pane="purple_stained_glass_pane",
         stair="quartz_stairs", slab="quartz_slab[type=bottom]", fill="smooth_quartz", floor="quartz_bricks",
         ceil="purpur_block"),
    dict(wall="crimson_planks", pillar="crimson_stem[axis=y]", pane="magenta_stained_glass_pane",
         stair="crimson_stairs", slab="crimson_slab[type=bottom]", fill="crimson_planks", floor="dark_oak_planks",
         ceil="crimson_planks"),
]
PALACE = dict(wall="smooth_quartz", pillar="purpur_pillar[axis=y]", pane="magenta_stained_glass_pane",
              stair="purpur_stairs", slab="purpur_slab[type=bottom]", fill="purpur_block", floor="polished_diorite",
              ceil="purpur_block")

WX1, WX2, WZ1, WZ2 = 185, 305, -218, -112
PLAZA = (231, -179, 259, -151)
PCX, PCZ = 245, -165

# --- ground inside the walls: crimson nylium, dark paving on the streets
ground = area.mix({"crimson_nylium": 5, "netherrack": 1})
for x in range(WX1, WX2 + 1):
    for z in range(WZ1, WZ2 + 1):
        if HT.get((x, z)) is not None:
            put(x, G, z, ground(x, G, z, 2))
            area.top[(x, z)] = G
            for y in range(G - 3, G):
                put(x, y, z, "netherrack")

street_pick = area.mix({"polished_blackstone_bricks": 5, "polished_blackstone": 2, "cracked_polished_blackstone_bricks": 1})
STREETS = [   # (x1, z1, x2, z2), 5 wide
    (WX1 + 1, -167, PLAZA[0], -163),              # west avenue from the gate
    (PLAZA[2], -167, WX2 - 8, -163),              # east avenue
    (243, -190, 247, PLAZA[1]),                   # palace avenue
    (243, PLAZA[3], 247, WZ2),                    # south avenue to the south gate
    (205, WZ1 + 1, 209, WZ2 - 1),                 # west lane
    (281, WZ1 + 1, 285, WZ2 - 1),                 # east lane
    (WX1 + 1, -202, 213, -198),                   # north-west cross street
    (277, -202, WX2 - 1, -198),                   # north-east cross street
    (WX1 + 1, -133, WX2 - 1, -129),               # south cross street
]
for (a, b, c, d) in STREETS:
    pave(a, b, c, d, G, lambda x, z: street_pick(x, G, z, 3))
    reserve(a, b, c, d, 0)

# --- the plaza: purpur / smooth quartz tiles with an amethyst ring and a crystal fountain
for x in range(PLAZA[0], PLAZA[2] + 1):
    for z in range(PLAZA[1], PLAZA[3] + 1):
        r = math.hypot(x - PCX, z - PCZ)
        if 12.5 <= r <= 13.5:
            b = "amethyst_block"
        elif r < 5.5:
            b = "polished_diorite"
        else:
            b = "smooth_quartz" if ((x // 3) + (z // 3)) % 2 == 0 else "purpur_block"
        put(x, G, z, b)
        area.top[(x, z)] = G
reserve(PLAZA[0], PLAZA[1], PLAZA[2], PLAZA[3], 0)
for x in range(PCX - 4, PCX + 5):
    for z in range(PCZ - 4, PCZ + 5):
        d = max(abs(x - PCX), abs(z - PCZ))
        if d == 4:
            put(x, G + 1, z, "purpur_slab[type=bottom]")
        elif d == 3:
            put(x, G + 1, z, "purpur_block")
        elif d == 2:
            put(x, G + 1, z, "amethyst_block")
fill(PCX - 1, G + 1, PCZ - 1, PCX + 1, G + 3, PCZ + 1, "purpur_pillar[axis=y]")
for k in range(10):
    h = 1 if k < 4 else 0
    fill(PCX - h, G + 4 + k, PCZ - h, PCX + h, G + 4 + k, PCZ + h, "amethyst_block" if k % 3 else "budding_amethyst")
for dx, dz, f in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
    put(PCX + dx * 2, G + 2, PCZ + dz * 2, f"amethyst_cluster[facing={f}]")
put(PCX, G + 14, PCZ, "end_rod[facing=up]")
put(PCX, G + 15, PCZ, "end_rod[facing=up]")
for dx, dz in ((-8, -8), (8, -8), (-8, 8), (8, 8), (0, -11), (0, 11), (-11, 0), (11, 0)):
    lamp_post(PCX + dx, G, PCZ + dz, "soul_lantern[hanging=false]", "purpur_pillar[axis=y]", 3)

# --- walls: 6 high, a walkway on top, crenels outside
for x in range(WX1, WX2 + 1):
    for z in range(WZ1, WZ2 + 1):
        if not (x <= WX1 + 2 or x >= WX2 - 2 or z <= WZ1 + 2 or z >= WZ2 - 2):
            continue
        for y in range(G - 3, G + 6):
            put(x, y, z, "red_nether_bricks" if (x + y + z) % 7 else "polished_blackstone_bricks")
        put(x, G + 6, z, "polished_blackstone_bricks" if (x + z) % 3 else "chiseled_polished_blackstone")
        if (x in (WX1, WX2) or z in (WZ1, WZ2)) and (x + z) % 2 == 0:
            put(x, G + 7, z, "red_nether_brick_wall")
        area.top[(x, z)] = G + 6
for x in range(WX1 + 4, WX2 - 3, 8):
    for z in (WZ1, WZ2):
        put(x, G + 3, z, "purple_stained_glass")
        put(x, G + 4, z, "purple_stained_glass")
for z in range(WZ1 + 6, WZ2 - 3, 8):
    for x in (WX1, WX2):
        put(x, G + 3, z, "purple_stained_glass")
        put(x, G + 4, z, "purple_stained_glass")
for x in range(WX1 + 4, WX2 - 3, 6):
    put(x, G + 7, WZ1 + 2, "lantern[hanging=false]")
    put(x, G + 7, WZ2 - 2, "lantern[hanging=false]")
for z in range(WZ1 + 4, WZ2 - 3, 6):
    put(WX1 + 2, G + 7, z, "lantern[hanging=false]")
    put(WX2 - 2, G + 7, z, "lantern[hanging=false]")
# west gate (z -168..-162) between two gate towers; south gate (x 242..248)
for z in range(-168, -161):
    for x in range(WX1, WX1 + 3):
        for y in range(G + 1, G + 6):
            put(x, y, z, "air")
        put(x, G, z, street_pick(x, G, z, 3))
        for y in range(G + 6, G + 9):
            put(x, y, z, "polished_blackstone_bricks")
    for y in range(G + 4, G + 6):
        put(WX1 + 1, y, z, "iron_bars")
for x in range(242, 249):
    for z in range(WZ2 - 2, WZ2 + 1):
        for y in range(G + 1, G + 6):
            put(x, y, z, "air")
        put(x, G, z, street_pick(x, G, z, 3))
        for y in range(G + 6, G + 8):
            put(x, y, z, "polished_blackstone_bricks")
for dx in (-4, 4):
    lamp_post(245 + dx, G, WZ2 - 3, "soul_lantern[hanging=false]", "polished_blackstone_wall", 2)

TW = dict(stair="purpur_stairs", fill="purpur_block")
for (tx1, tz1, tx2, tz2) in ((WX1 - 2, WZ1 - 2, WX1 + 6, WZ1 + 6), (WX1 - 2, WZ2 - 6, WX1 + 6, WZ2 + 2),
                             (WX2 - 6, WZ2 - 6, WX2 + 2, WZ2 + 2)):
    tower(tx1, tz1, tx2, tz2, G, 15, "red_nether_bricks", "polished_blackstone", "purple_stained_glass",
          top="roof", roof_b=TW, steep=2)
# the NE tower: open top with a spire, a ladder inside to the Fairy Soul
tower(WX2 - 6, WZ1 - 2, WX2 + 2, WZ1 + 6, G, 15, "red_nether_bricks", "polished_blackstone", "purple_stained_glass",
      top="open", ladder=(WX2 - 2, WZ1 - 1, "south"), door_at=(WX2 - 3, WZ1 + 6, "south"))
spire(WX2 - 2, WZ1 + 2, G + 16, 1, 3, "purpur_block", "amethyst_block", 3)
for (tx1, tz1, tx2, tz2) in ((WX1 - 3, -178, WX1 + 5, -170), (WX1 - 3, -160, WX1 + 5, -152)):
    tower(tx1, tz1, tx2, tz2, G, 13, "red_nether_bricks", "polished_blackstone", "magenta_stained_glass",
          top="roof", roof_b=TW, steep=2)
put(WX1 - 1, G + 5, -168, "soul_lantern[hanging=true]")
put(WX1 - 1, G + 5, -162, "soul_lantern[hanging=true]")
for (rx, rz) in ((WX1 - 4, WZ1 - 4), (WX2 - 8, WZ1 - 4), (WX1 - 4, WZ2 - 8), (WX2 - 8, WZ2 - 8)):
    reserve(rx, rz, rx + 12, rz + 12)
reserve(WX1 - 4, WZ1 - 4, WX2 + 4, WZ1 + 1)
reserve(WX1 - 4, WZ2 - 2, WX2 + 4, WZ2 + 4)
reserve(WX1 - 4, WZ1 - 4, WX1 + 3, WZ2 + 4)
reserve(WX2 - 3, WZ1 - 4, WX2 + 4, WZ2 + 4)


# --- Queen Nyx's palace (north, facing the plaza)
PX1, PZ1, PX2, PZ2 = 215, -214, 275, -188
building(PX1, PZ1, PX2, PZ2, 11, PALACE, door_side="s", door_at=245, over=1, door_w=5, axis_="x", win=4)
for x in range(PX1 + 2, PX2 - 1, 6):
    for z in (PZ1, PZ2):
        if not (z == PZ2 and abs(x - 245) <= 3):
            fill(x, G + 4, z, x, G + 9, z, "magenta_stained_glass_pane")
for z in range(PZ1 + 4, PZ2 - 2, 6):
    for x in (PX1, PX2):
        fill(x, G + 4, z, x, G + 9, z, "purple_stained_glass_pane")
for (tx1, tz1, tx2, tz2) in ((PX1 - 3, PZ1 - 3, PX1 + 3, PZ1 + 3), (PX2 - 3, PZ1 - 3, PX2 + 3, PZ1 + 3),
                             (PX1 - 3, PZ2 - 3, PX1 + 3, PZ2 + 3), (PX2 - 3, PZ2 - 3, PX2 + 3, PZ2 + 3)):
    tower(tx1, tz1, tx2, tz2, G, 17, "quartz_bricks", "purpur_pillar[axis=y]", "magenta_stained_glass",
          top="roof", roof_b=dict(stair="purpur_stairs", fill="purpur_block"), steep=2)
# the great spire over the throne room
for x in range(241, 250):
    for z in range(-205, -196):
        column_down(x, z, G + 26, "purpur_block")
for x in range(241, 250):
    for z in range(-205, -196):
        if x in (241, 249) or z in (-205, -197):
            put(x, G + 27, z, "amethyst_block")
spire(245, -201, G + 27, 3, 5, "purpur_block", "amethyst_block", 5, rod=4)
# the hall inside: carpet, pillars, the dais and the throne
for z in range(PZ2 - 1, PZ1 + 1, -1):
    fill(244, G + 1, z, 246, G + 1, z, "red_carpet")
    fill(243, G + 1, z, 243, G + 1, z, "purple_carpet")
    fill(247, G + 1, z, 247, G + 1, z, "purple_carpet")
for x in (226, 236, 254, 264):
    for z in (-209, -202, -195):
        fill(x, G + 1, z, x, G + 10, z, "purpur_pillar[axis=y]")
        put(x, G + 1, z, "chiseled_quartz_block")
        put(x + 1, G + 6, z, "lantern[hanging=false]")
        put(x - 1, G + 6, z, "lantern[hanging=false]")
for x in range(238, 253):
    for z in range(-212, -205):
        put(x, G + 1, z, "quartz_bricks")
for x in range(241, 250):
    for z in range(-212, -208):
        put(x, G + 2, z, "chiseled_quartz_block")
fill(244, G + 3, -212, 246, G + 3, -212, "quartz_slab[type=bottom]")
put(245, G + 3, -212, stair("quartz_stairs", "south"))
fill(245, G + 4, -213, 245, G + 9, -213, "amethyst_block")
for dx in (-4, 4):
    put(245 + dx, G + 3, -211, "soul_lantern[hanging=false]")
for k in range(7):
    put(238 + k * 12 // 2 % 14, G + 8, -213, "air") if False else None
area.npc("Queen Nyx", 245, G + 3, -210, 0.0, "on the throne dais in her palace, facing the hall")
# palace forecourt: steps and two pillars
for x in range(240, 251):
    put(x, G, PZ2 + 1, "polished_diorite")
for dx in (-6, 6):
    fill(245 + dx, G + 1, PZ2 + 2, 245 + dx, G + 7, PZ2 + 2, "purpur_pillar[axis=y]")
    put(245 + dx, G + 8, PZ2 + 2, "soul_lantern[hanging=false]")
sign(area, 241, G + 1, PZ2 + 3, 0, ["Queen Nyx's", "Palace"])
reserve(PX1 - 3, PZ1 - 3, PX2 + 3, PZ2 + 2)

# --- the Bank
building(262, -176, 278, -158, 7, STYLES[0], door_side="w", door_at=-167, over=1, axis_="x")
fill(270, G + 1, -172, 270, G + 1, -162, "chiseled_quartz_block")
fill(270, G + 2, -172, 270, G + 2, -162, "quartz_slab[type=bottom]")
for z in range(-174, -160, 2):
    put(277, G + 1, z, "barrel[facing=west]")
    put(277, G + 2, z, "gold_block") if z % 4 == 0 else None
put(271, G + 3, -172, "lantern[hanging=false]")
put(271, G + 3, -162, "lantern[hanging=false]")
area.npc("Banker (Scarleton)", 273, G + 1, -167, 90.0, "behind the bank counter, facing the door (west)")
sign(area, 259, G + 1, -170, 4, ["Bank"])

# --- the Auction House
building(212, -176, 228, -158, 7, STYLES[2], door_side="e", door_at=-167, over=1, axis_="x")
for z in range(-174, -159, 2):
    put(214, G + 1, z, "lectern[facing=east]")
    put(214, G + 2, z, "amethyst_cluster[facing=up]") if z % 4 == 0 else None
fill(220, G + 1, -172, 220, G + 1, -162, "dark_oak_planks")
fill(220, G + 2, -172, 220, G + 2, -162, "dark_oak_slab[type=bottom]")
put(220, G + 3, -167, "bell[attachment=floor,facing=east]") if False else put(220, G + 3, -167, "bell[attachment=floor,facing=north]")
area.npc("Auction Master (Scarleton)", 217, G + 1, -167, -90.0, "behind the auction counter, facing the door (east)")
sign(area, 230, G + 1, -170, 12, ["Auction House"])

# --- the Bazaar
building(250, -147, 270, -135, 6, STYLES[3], door_side="n", door_at=260, over=1, axis_="x", win=4)
fill(254, G + 1, -141, 266, G + 1, -141, "barrel[facing=north]")
for x in range(254, 267, 3):
    put(x, G + 2, -141, "chest[facing=north]")
for x in (252, 268):
    for z in (-145, -142, -138):
        put(x, G + 1, z, "barrel[facing=up]")
        put(x, G + 2, z, "crimson_pressure_plate") if False else None
area.npc("Bazaar (Scarleton)", 260, G + 1, -139, 180.0, "behind the bazaar counter, facing the door (north)")
sign(area, 258, G + 1, -149, 8, ["Bazaar"])

# --- the blacksmith and enchanter workshop (Alwin)
building(220, -147, 240, -135, 6, STYLES[1], door_side="e", door_at=-141, over=1, axis_="x", win=4)
put(227, G + 1, -141, "anvil[facing=east]")
put(226, G + 1, -144, "grindstone[face=floor,facing=east]")
put(226, G + 1, -138, "smithing_table")
put(224, G + 1, -146, "blast_furnace[facing=south,lit=true]")
put(223, G + 1, -146, "blast_furnace[facing=south,lit=true]")
put(222, G + 1, -146, "lava_cauldron")
for (ex, ez) in ((235, -137), (235, -145), (231, -141)):
    put(ex, G + 1, ez, "enchanting_table")
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        pass
for x in range(232, 239):
    put(x, G + 1, -147 + 1, "bookshelf")
    put(x, G + 2, -147 + 1, "bookshelf")
area.npc("Alwin", 225, G + 1, -141, -90.0, "the blacksmith at his anvil in the workshop")
sign(area, 243, G + 1, -138, 12, ["Alwin's", "Workshop"])

# --- the Mage Emissary's pavilion (plaza, north-east)
for (ex, ez) in ((252, -178), (258, -178), (252, -172), (258, -172)):
    fill(ex, G + 1, ez, ex, G + 4, ez, "purpur_pillar[axis=y]")
    put(ex, G + 5, ez, "lantern[hanging=false]")
for x in range(251, 260):
    for z in range(-179, -171):
        put(x, G + 5, z, "purpur_slab[type=bottom]")
        if x in (251, 259) or z in (-179, -172):
            put(x, G + 5, z, "amethyst_block")
for x in range(253, 258):
    for z in range(-177, -174):
        put(x, G + 1, z, "red_carpet")
put(255, G + 4, -175, "lantern[hanging=true]")
put(255, G + 5, -175, "chain[axis=y]") if False else None
area.npc("Mage Emissary", 255, G + 1, -175, 0.0, "under the pavilion by the plaza; sells Kuudra keys")

# --- Rollim's sparring circle (west)
SCX, SCZ = 196, -183
for x in range(SCX - 8, SCX + 9):
    for z in range(SCZ - 8, SCZ + 9):
        r = math.hypot(x - SCX, z - SCZ)
        if r <= 7.4:
            b = "chiseled_quartz_block" if r < 1.6 else "polished_blackstone" if 3.5 <= r < 4.6 else \
                "amethyst_block" if 6.3 <= r else "smooth_quartz"
            put(x, G, z, b)
            area.top[(x, z)] = G
for k in range(8):
    a = k / 8 * math.tau
    px, pz = int(round(SCX + math.cos(a) * 8)), int(round(SCZ + math.sin(a) * 8))
    lamp_post(px, G, pz, "soul_lantern[hanging=false]", "polished_blackstone_wall", 3)
put(SCX - 6, G + 1, SCZ - 6, "grindstone[face=floor,facing=north]")
put(SCX + 6, G + 1, SCZ - 6, "anvil[facing=east]") if False else None
reserve(SCX - 8, SCZ - 8, SCX + 8, SCZ + 8)
area.npc("Rollim", SCX + 3, G + 1, SCZ, 90.0, "the duelist, at the edge of the sparring circle's inner ring")

# --- Pablo's garden (north-east): crimson fungi and wither roses
GX1, GZ1, GX2, GZ2 = 288, -197, 302, -170
for x in range(GX1, GX2 + 1):
    for z in range(GZ1, GZ2 + 1):
        if x in (GX1, GX2) or z in (GZ1, GZ2):
            if not (x == GX1 and GZ1 + 12 <= z <= GZ1 + 14):
                put(x, G + 1, z, "crimson_fence")
            continue
        cx_, cz_ = (GX1 + GX2) // 2, (GZ1 + GZ2) // 2
        if x == cx_ or z == cz_:
            put(x, G, z, "polished_blackstone_bricks")
            continue
        n = noise(x, z, 21, 1.8)
        r = hash01(x, 5, z, 3)
        if n > 0.15:
            put(x, G, z, "soul_soil")
            if r < 0.5:
                put(x, G + 1, z, "wither_rose")
            elif r < 0.6:
                put(x, G + 1, z, "soul_fire") if False else None
        elif n > -0.25:
            put(x, G, z, "crimson_nylium")
            if r < 0.4:
                put(x, G + 1, z, "crimson_fungus")
            elif r < 0.6:
                put(x, G + 1, z, "crimson_roots")
        else:
            put(x, G, z, "warped_nylium")
            if r < 0.4:
                put(x, G + 1, z, "warped_fungus")
            elif r < 0.55:
                put(x, G + 1, z, "warped_roots")
for (x, z) in ((GX1 + 3, GZ1 + 3), (GX2 - 3, GZ1 + 3), (GX1 + 3, GZ2 - 3), (GX2 - 3, GZ2 - 3)):
    put(x, G + 1, z, "air")
    lamp_post(x, G, z, "shroomlight", "crimson_fence", 3)
reserve(GX1, GZ1, GX2, GZ2, 1)
area.npc("Pablo", (GX1 + GX2) // 2, G + 1, (GZ1 + GZ2) // 2, 90.0, "at the crossing of the paths in his flower garden")
sign(area, GX1 - 2, G + 1, GZ1 + 12, 4, ["Pablo's Garden"])

# --- the Observatory (Avorius), south-east corner
tower(290, -127, 298, -119, G, 14, "quartz_bricks", "purpur_pillar[axis=y]", "purple_stained_glass", top="roof",
      roof_b=dict(stair="purple_glazed_terracotta" if False else "purpur_stairs", fill="purpur_block"), steep=2,
      door_at=(294, -119, "south"))
for dx, dz in ((-3, 2), (3, 2)):
    lamp_post(294 + dx, G, -116 + dz - 1, "soul_lantern[hanging=false]", "purpur_pillar[axis=y]", 2)
for k in range(8):
    a = k / 8 * math.tau
    put(int(round(294 + math.cos(a) * 3)), G, int(round(-115 + math.sin(a) * 3)), "amethyst_block")
area.npc("Avorius", 294, G + 1, -116, 180.0, "outside the observatory door, an astrologer and quest giver")
reserve(288, -129, 300, -113)

# --- Mollim by the west gate
fill(193, G + 1, -158, 193, G + 2, -158, "crimson_fence")
put(193, G + 3, -158, "lantern[hanging=false]")
sign(area, 195, G + 1, -159, 4, ["Quests", "ask Mollim"])
area.npc("Mollim", 194, G + 1, -161, -90.0, "by the west gate's guard post, quest giver")
reserve(190, -163, 197, -155)

# --- houses fill the rest of the blocks
STREETCELLS = set()
for (a_, b_, c_, d_) in STREETS:
    for x_ in range(a_, c_ + 1):
        for z_ in range(b_, d_ + 1):
            STREETCELLS.add((x_, z_))
hn = 0
for z1 in range(-214, -118, 3):
    x = 189
    while x < 296:
        w = 7 + int(hash01(x, z1, 0, 4) * 5)
        d = 7 + int(hash01(x, z1, 9, 4) * 3)
        x1, x2, zz1, zz2 = x, x + w - 1, z1, z1 + d - 1
        if x2 > WX2 - 4 or zz2 > WZ2 - 4:
            break
        if any((xx, zz) in RES for xx in range(x1 - 1, x2 + 2) for zz in range(zz1 - 1, zz2 + 2)):
            x += 2
            continue
        mid = (x1 + x2) // 2 + int(hash01(x, z1, 3) * 3) - 1
        side = None
        for sd, (cx_, cz_) in (("s", (mid, zz2)), ("n", (mid, zz1))):
            step = 1 if sd == "s" else -1
            if any((cx_, cz_ + step * k) in STREETCELLS for k in range(2, 14)):
                side = sd
                break
        if side is None:
            x += 2
            continue
        st = STYLES[(hn * 7 + int(hash01(x, z1, 1, 5) * 4)) % 4]
        hh = 5 + int(hash01(x, z1, 2, 6) * 3)
        building(x1, zz1, x2, zz2, hh, st, door_side=side, door_at=mid, over=1)
        step = 1 if side == "s" else -1
        dz0 = zz2 if side == "s" else zz1
        k = 1
        while (mid, dz0 + step * k) not in STREETCELLS and k < 16:
            put(mid, G, dz0 + step * k, street_pick(mid, G, dz0 + step * k, 3))
            k += 1
        fx = x1 + 2
        fz = zz1 + 2 if side == "s" else zz2 - 2
        put(fx, G + 1, fz, "crafting_table")
        put(fx + 1, G + 1, fz, "bookshelf")
        put(x2 - 2, G + 1, fz, "barrel[facing=up]")
        hn += 1
        x = x2 + 4
print("houses", hn)

# --- lamps along the streets
for (a, b, c, d) in STREETS:
    if c - a > d - b:
        for x in range(a + 3, c - 1, 11):
            for z, sd in ((b - 1, 0), (d + 1, 1)):
                if (x // 11 + sd) % 2 == 0 and (x, z) not in RES or True:
                    if area.get(x, G + 1, z) in (None, "minecraft:air"):
                        lamp_post(x, G, z, "lantern[hanging=false]", "crimson_fence", 3)
    else:
        for z in range(b + 3, d - 1, 11):
            for x, sd in ((a - 1, 0), (c + 1, 1)):
                if area.get(x, G + 1, z) in (None, "minecraft:air"):
                    lamp_post(x, G, z, "lantern[hanging=false]", "crimson_fence", 3)
area.warp("Scarleton", PCX, G + 1, PCZ + 8, 180.0)
area.fairy_soul(WX2 - 5, G + 16, WZ1 + 1)


def pyramid(x1, z1, x2, z2, y, stair_b, fillb, steep=1, rod=3):
    r1, r2, s1, s2 = x1, x2, z1, z2
    yy = y
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    while r1 <= r2 and s1 <= s2:
        for _ in range(steep):
            for x in range(r1, r2 + 1):
                for z in range(s1, s2 + 1):
                    ring = x in (r1, r2) or z in (s1, s2)
                    if not ring or (x in (r1, r2) and z in (s1, s2)):
                        put(x, yy, z, fillb)
                    elif z == s1:
                        put(x, yy, z, stair(stair_b, "north"))
                    elif z == s2:
                        put(x, yy, z, stair(stair_b, "south"))
                    elif x == r1:
                        put(x, yy, z, stair(stair_b, "west"))
                    else:
                        put(x, yy, z, stair(stair_b, "east"))
            yy += 1
        r1 += 1
        r2 -= 1
        s1 += 1
        s2 -= 1
    for i in range(rod):
        put(cx, yy + i, cz, "end_rod[facing=up]" if i else fillb)


# ================================================================ the Mage Outpost (north bridge, east end)
OG = 66
oc = area.mix({"polished_blackstone_bricks": 3, "blackstone": 3, "gravel": 0, "cobbled_deepslate": 1})
for x in range(115, 150):
    for z in range(-96, -63):
        if abs(z + 80) <= 2:
            continue
        r = hash01(x, 3, z, 9)
        put(x, OG, z, "polished_blackstone_bricks" if r < 0.5 else "blackstone" if r < 0.72 else
            "cracked_polished_blackstone_bricks" if r < 0.85 else "polished_blackstone")
        area.top[(x, z)] = OG
reserve(112, -100, 152, -60)
# the road through the camp is the north road (paved by the road pass); a plaza ring with a rune circle
OCX, OCZ = 128, -80
for x in range(OCX - 6, OCX + 7):
    for z in range(OCZ - 6, OCZ + 7):
        r = math.hypot(x - OCX, z - OCZ)
        if r <= 6.3:
            put(x, OG, z, "chiseled_polished_blackstone" if 4.6 <= r < 5.4 or r < 1.2 else
                "amethyst_block" if 3.3 <= r < 3.9 else "polished_blackstone_bricks")
put(OCX, OG + 1, OCZ, "enchanting_table")
for k in range(4):
    a = k * math.pi / 2 + math.pi / 4
    x, z = int(round(OCX + math.cos(a) * 4)), int(round(OCZ + math.sin(a) * 4))
    lamp_post(x, OG, z, "soul_lantern[hanging=false]", "polished_blackstone_wall", 2)
# the wizard tower (north of the road), open top with a roofed gazebo, a ladder up the inside
WT1X, WT1Z, WT2X, WT2Z = 137, -95, 145, -87
tower(WT1X, WT1Z, WT2X, WT2Z, OG, 26, "purpur_block", "purpur_pillar[axis=y]", "magenta_stained_glass", top="open",
      ladder=(141, WT1Z + 1, "south"), door_at=(141, WT2Z, "south"), crenel="polished_blackstone_wall")
for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
    px, pz = 141 + dx, -91 + dz
    fill(px, OG + 27, pz, px, OG + 30, pz, "purpur_pillar[axis=y]")
pyramid(WT1X - 1, WT1Z - 1, WT2X + 1, WT2Z + 1, OG + 31, "purpur_stairs", "purpur_block", steep=2, rod=4)
put(141, OG + 30, -91, "lantern[hanging=true]")
put(141, OG + 31, -91, "chain[axis=y]") if False else None
for z in range(WT2Z + 1, OCZ - 2):
    put(141, OG, z, "polished_blackstone_bricks")
    put(140, OG, z, "polished_blackstone_bricks")
    put(142, OG, z, "polished_blackstone_bricks")
area.fairy_soul(142, OG + 27, -92)
reserve(134, -98, 148, -84)


def tent(cx, cz, wool, length=6):
    x0 = cx - length // 2
    for x in range(x0, x0 + length):
        for dz in (-2, 2):
            fill(x, OG + 1, cz + dz, x, OG + 2, cz + dz, wool)
        for dz in (-1, 1):
            put(x, OG + 3, cz + dz, wool)
        put(x, OG + 4, cz, wool)
        for dz in (-1, 0, 1):
            put(x, OG, cz + dz, "red_carpet")
    for dz in (-1, 0, 1):                         # closed back
        fill(x0, OG + 1, cz + dz, x0, OG + 2 if dz else OG + 3, cz + dz, wool)
    put(x0 + length // 2, OG + 3, cz, "lantern[hanging=true]")
    put(x0 + 1, OG + 1, cz + 1, "barrel[facing=up]")
    put(x0 + 1, OG + 1, cz - 1, "chest[facing=east]")
    reserve(x0 - 1, cz - 3, x0 + length, cz + 3)


for (tx, tz, w) in ((121, -91, "purple_wool"), (121, -69, "magenta_wool"), (136, -68, "red_wool"), (147, -70, "purple_wool"),
                    (122, -99 + 4, "magenta_wool") if False else (150, -92, "red_wool")):
    tent(tx, tz, w)
# enchanting nooks with bookshelves
for (ex, ez) in ((131, -91), (133, -70)):
    put(ex, OG + 1, ez, "enchanting_table")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2 and (dx, dz) != (0, 2) and (dx, dz) != (0, -2):
                put(ex + dx, OG + 1, ez + dz, "bookshelf")
                put(ex + dx, OG + 2, ez + dz, "bookshelf") if (dx + dz) % 2 == 0 else None
    put(ex, OG + 4, ez, "lantern[hanging=false]") if False else None
    reserve(ex - 3, ez - 3, ex + 3, ez + 3)
# a campfire circle
for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
    put(OCX + 14 + dx, OG + 1, OCZ + 9 + dz, stair("dark_oak_stairs", {(-2, 0): "east", (2, 0): "west", (0, -2): "south", (0, 2): "north"}[(dx, dz)]))
put(OCX + 14, OG + 1, OCZ + 9, "campfire[lit=true,facing=south]")
sign(area, 114, OG + 1, -83, 4, ["Mage Outpost", "Scarleton ->"])
area.npc("spawn:Outpost Mage", OCX + 14, OG + 1, OCZ + 6, 0.0, "spare spot by the campfire (unused)") if False else None
area.warp("Mage Outpost", 124, OG + 1, -77, 90.0)

# ================================================================ the Cathedral
CG = 65
BR, BRS, BRL, BRW = ("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
                     "polished_blackstone_brick_wall")
cp_mix = area.mix({"blackstone": 4, "polished_blackstone": 3, "cracked_polished_blackstone_bricks": 2, "crimson_nylium": 2})
pave(238, -104, 302, -34, CG, lambda x, z: cp_mix(x, CG, z, 5))
Z1, Z2 = -96, -36
GL = ["magenta_stained_glass_pane", "purple_stained_glass_pane", "magenta_stained_glass_pane", "pink_stained_glass_pane"]
for x in range(254, 287):
    for z in range(Z1, Z2 + 1):
        put(x, CG, z, "polished_blackstone" if (x + z) % 2 else BR)
        area.top[(x, z)] = CG
# aisle outer walls with tall windows
for z in range(Z1, -44):
    for xw in (254, 286):
        for y in range(CG + 1, CG + 15):
            b = BR if (y % 5 or z % 2) else "chiseled_polished_blackstone"
            if (z - Z1) % 4 == 2 and CG + 4 <= y <= CG + 10:
                b = GL[((z - Z1) // 4) % 4]
            put(xw, y, z, b)
        # parapet
        put(xw, CG + 15, z, BRW)
    for x in range(255, 262):
        put(x, CG + 14, z, BR)
        put(x + 24, CG + 14, z, BR)
# north wall
for x in range(254, 287):
    for y in range(CG + 1, CG + 15):
        put(x, y, Z1, BR)
for x in range(262, 279):
    for y in range(CG + 15, CG + 27):
        put(x, y, Z1, BR)
for x in range(266, 275):
    for y in range(CG + 4, CG + 13):
        put(x, y, Z1, "magenta_stained_glass_pane" if x not in (267, 270, 273) else "polished_blackstone")
for x in (264, 276):
    for y in range(CG + 17, CG + 24):
        put(x, y, Z1, "purple_stained_glass_pane")
# the arcade between nave and aisles, and the clerestory
for z in range(Z1 + 1, -44):
    for xw in (262, 278):
        if (z - Z1) % 4 == 0:
            for y in range(CG + 1, CG + 27):
                put(xw, y, z, "polished_blackstone" if y % 6 else "chiseled_polished_blackstone")
        else:
            for y in range(CG + 1, CG + 11):
                put(xw, y, z, "air")
            for y in range(CG + 11, CG + 27):
                b = BR
                if (z - Z1) % 4 == 2 and CG + 17 <= y <= CG + 23:
                    b = GL[((z - Z1) // 4 + 1) % 4]
                put(xw, y, z, b)
            if (z - Z1) % 4 in (1, 3):
                put(xw, CG + 10, z, stair(BRS, "west" if (z - Z1) % 4 == 1 else "east", "top") if False else BR)
# the nave roof (deepslate tile) and the aisle roof rail
roof(262, Z1, 278, Z2, CG + 26, "deepslate_tile_stairs", "deepslate_tile_slab[type=bottom]", BR, "z", 1)
# flying buttresses and piers
for z in range(Z1, -44, 8):
    for sd in (0, 1):
        sx = (lambda d: 254 - d) if sd == 0 else (lambda d: 286 + d)
        for d, ht in ((1, 17), (2, 12), (3, 6)):
            for y in range(CG + 1, CG + 1 + ht):
                put(sx(d), y, z, BR if y % 4 else "polished_blackstone")
        put(sx(1), CG + 18, z, BR)
        put(sx(1), CG + 19, z, "soul_lantern[hanging=false]")
        for i in range(8):
            xi = 254 + i if sd == 0 else 286 - i
            y = CG + 17 + (i + 1) // 2
            put(xi, y, z, BR)
            put(xi, y - 1, z, BR) if i % 2 == 0 else put(xi, y - 1, z, BRS.replace("stairs", "stairs") + "[facing=%s,half=top]" % ("west" if sd == 0 else "east")) if False else None
        put(254 if sd == 0 else 286, CG + 16, z, BR)
# front: nave facade with the portal and a rose window; two towers
for x in range(262, 279):
    for y in range(CG + 1, CG + 27):
        put(x, y, Z2, BR if y % 5 else "chiseled_polished_blackstone")
for x in range(268, 273):
    for y in range(CG + 1, CG + 10):
        put(x, y, Z2, "air")
        put(x, y, Z2 + 1, "air")
for x in range(266, 275):
    put(x, CG + 10, Z2, "chiseled_polished_blackstone")
for x in (267, 273):
    for y in range(CG + 1, CG + 10):
        put(x, y, Z2, "polished_blackstone" if y % 4 else "chiseled_polished_blackstone")
RCX, RCY = 270, CG + 17
for dx in range(-6, 7):
    for dy in range(-6, 7):
        r = math.hypot(dx, dy)
        if r > 5.6:
            continue
        a = math.atan2(dy, dx)
        if r < 1.7:
            b = "shroomlight"
        elif 4.4 <= r:
            b = "chiseled_polished_blackstone"
        elif int((a + math.pi) / (math.pi / 4)) % 2 == 0:
            b = "magenta_stained_glass"
        else:
            b = "purple_stained_glass"
        put(RCX + dx, RCY + dy, Z2, b)
for (tx1, tx2, ladder, door_at) in ((254, 262, (260, -43, "south"), (256, -44, "north")), (278, 286, None, (282, -44, "north"))):
    tower(tx1, -44, tx2, Z2, CG, 36, BR, "polished_blackstone", "magenta_stained_glass", top="open", ladder=ladder,
          door_at=door_at, crenel=BRW, windows=False)
    cxx = (tx1 + tx2) // 2
    for y in (CG + 5, CG + 14, CG + 24):
        for (fx, fz) in ((cxx, -36), (tx1, -40), (tx2, -40)):
            fill(fx, y, fz, fx, y + 3, fz, "purple_stained_glass_pane" if fx == cxx else "magenta_stained_glass_pane")
    spire(cxx, -40, CG + 37, 2, 6, BR, "amethyst_block", 6, rod=3)
    for (px, pz) in ((tx1, -44), (tx2, -44), (tx1, Z2), (tx2, Z2)):
        put(px, CG + 38, pz, "soul_lantern[hanging=false]")
put(260, CG + 37, -43, "air")
area.fairy_soul(255, CG + 37, -39)
# the crossing tower and the great spire
for x in range(266, 275):
    for z in range(-74, -65):
        column_down(x, z, CG + 60, BR)
for y in range(CG + 38, CG + 54):
    for (fx, fz, ax_) in ((x, z, 0) for x in (269, 270, 271) for z in (-74, -66)):
        put(fx, y, fz, "magenta_stained_glass")
        put(fx, y, fz + (1 if fz == -74 else -1), "glowstone")
    for fz in (-71, -70, -69):
        for fx in (266, 274):
            put(fx, y, fz, "purple_stained_glass")
            put(fx + (1 if fx == 266 else -1), y, fz, "glowstone")
for x in range(266, 275):
    for z in range(-74, -65):
        if x in (266, 274) or z in (-74, -66):
            put(x, CG + 61, z, "polished_blackstone" if (x + z) % 2 else BRW)
for (px, pz) in ((266, -74), (274, -74), (266, -66), (274, -66)):
    fill(px, CG + 61, pz, px, CG + 64, pz, "polished_blackstone")
    put(px, CG + 65, pz, "soul_lantern[hanging=false]")
spire(270, -70, CG + 62, 3, 6, BR, "amethyst_block", 6, rod=4)
# the interior
for z in range(Z1 + 1, Z2):
    fill(269, CG + 1, z, 271, CG + 1, z, "red_carpet")
    put(268, CG + 1, z, "purple_carpet")
    put(272, CG + 1, z, "purple_carpet")
for z in range(-86, -48, 3):
    for x in (264, 265, 266, 274, 275, 276):
        put(x, CG + 1, z, stair("dark_oak_stairs", "north"))
for z in range(-90, -46, 6):
    put(267, CG + 1, z, "candle[candles=3,lit=true]")
    put(273, CG + 1, z, "candle[candles=3,lit=true]")
for z in (-90, -80, -62, -52):
    for y in range(CG + 31, CG + 35):
        put(270, y, z, "chain[axis=y]")
    put(270, CG + 30, z, "soul_lantern[hanging=true]")
for z in range(-92, -46, 6):
    for xx in (258, 282):
        put(xx, CG + 13, z, "soul_lantern[hanging=true]")
for x in range(263, 278):
    for z in range(-95, -89):
        put(x, CG + 1, z, BR)
for x in range(266, 275):
    for z in range(-95, -92):
        put(x, CG + 2, z, "chiseled_polished_blackstone")
put(270, CG + 3, -94, "chiseled_quartz_block")
for dx in (-1, 1):
    put(270 + dx, CG + 3, -94, "candle[candles=4,lit=true]")
for dx in (-4, 4):
    fill(270 + dx, CG + 3, -94, 270 + dx, CG + 6, -94, "amethyst_block")
    put(270 + dx, CG + 7, -94, "amethyst_cluster[facing=up]")
fill(270, CG + 4, -95, 270, CG + 9, -95, "crying_obsidian")
# outside lamps
for z in range(-100, -40, 10):
    for xx in (244, 296):
        lamp_post(xx, CG, z, "soul_lantern[hanging=false]", "polished_blackstone_wall", 3)
for xx in (246, 258, 282, 294):
    lamp_post(xx, CG, -33, "soul_lantern[hanging=false]", "polished_blackstone_wall", 3)
sign(area, 264, CG + 1, -33, 0, ["The Cathedral"])
reserve(238, -104, 302, -34)

# ================================================================ the Courtyard
YX1, YZ1, YX2, YZ2 = 232, -32, 298, 4
YCX, YCZ = 265, -14
for x in range(YX1, YX2 + 1):
    for z in range(YZ1, YZ2 + 1):
        r = math.hypot(x - YCX, z - YCZ)
        if r <= 8.4:
            b = "chiseled_polished_blackstone" if 7.0 <= r else "crying_obsidian" if 5.6 <= r < 6.2 else \
                "cracked_polished_blackstone_bricks" if hash01(x, 1, z, 2) < 0.4 else "blackstone"
        else:
            b = "polished_blackstone_bricks" if ((x // 2 + z // 2) % 2 == 0) else "polished_deepslate"
        put(x, CG, z, b)
        area.top[(x, z)] = CG
for x in range(YX1, YX2 + 1):
    for z in range(YZ1, YZ2 + 1):
        if x in (YX1, YX2) or z in (YZ1, YZ2):
            gate = (z == YZ1 and 266 <= x <= 274) or (x == YX1 and -17 <= z <= -11) or (z == YZ2 and 262 <= x <= 268)
            if gate:
                put(x, CG + 4, z, BR)
                put(x, CG + 5, z, "chiseled_polished_blackstone")
                continue
            for y in range(CG + 1, CG + 5):
                put(x, y, z, BR if (y + x + z) % 5 else "cracked_polished_blackstone_bricks")
            put(x, CG + 5, z, BRW if (x + z) % 2 == 0 else "polished_blackstone_slab[type=bottom]")
for (px, pz) in ((YX1, YZ1), (YX2, YZ1), (YX1, YZ2), (YX2, YZ2)):
    fill(px, CG + 1, pz, px, CG + 8, pz, "polished_blackstone")
    put(px, CG + 9, pz, "soul_lantern[hanging=false]")
for x in range(YX1 + 8, YX2 - 6, 8):
    for z in (YZ1 + 1, YZ2 - 1):
        if not (z == YZ1 + 1 and 264 <= x <= 276):
            lamp_post(x, CG, z, "soul_lantern[hanging=false]", "polished_blackstone_wall", 3)
for z in range(YZ1 + 8, YZ2 - 4, 8):
    for x in (YX1 + 1, YX2 - 1):
        lamp_post(x, CG, z, "soul_lantern[hanging=false]", "polished_blackstone_wall", 3)
for k in range(8):
    a = k / 8 * math.tau + 0.4
    px, pz = int(round(YCX + math.cos(a) * 12)), int(round(YCZ + math.sin(a) * 12))
    fill(px, CG + 1, pz, px, CG + 5, pz, "polished_blackstone")
    put(px, CG + 6, pz, "amethyst_block")
    put(px, CG + 7, pz, "end_rod[facing=up]")
# the hidden nook in the south-east corner
for z in range(0, 4):
    fill(294, CG + 1, z, 294, CG + 4, z, BR)
for x in range(294, 299):
    fill(x, CG + 1, 0, x, CG + 4, 0, BR)
for z in range(1, 3):
    put(294, CG + 1, z, BR)
put(294, CG + 1, 3, "air")
put(294, CG + 2, 3, "air")
put(296, CG + 4, 1, "soul_lantern[hanging=true]")
put(296, CG + 3, 1, "air")
area.fairy_soul(296, CG + 1, 2)
sign(area, 230, CG + 1, -19, 4, ["The Courtyard", "Beware the Mage Outlaw"])
area.npc("spawn:Mage Outlaw", YCX, CG + 1, YCZ, 0.0, "the middle of the Courtyard's rune circle (room for a big mob)")
reserve(YX1 - 2, YZ1 - 2, YX2 + 2, YZ2 + 2)


# ================================================================ the Mystic Marsh: giant mushrooms, lava puddles, fields
def giant(x, z, kind, h, R, thick=False, soul=False):
    t = HT[(x, z)]
    cells = [(dx, dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)] if thick else [(0, 0)]
    for dx, dz in cells:
        for y in range(t + 1, t + h + 1):
            put(x + dx, y, z + dz, "mushroom_stem")
    cy = t + h
    if kind == "red":
        ry = max(3, int(R * 0.7))
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                for dy in range(0, ry + 1):
                    if (dx * dx + dz * dz) / (R * R) + (dy / ry) ** 2 <= 1.0:
                        top = dy == ry or (dx * dx + dz * dz) / (R * R) + ((dy + 1) / ry) ** 2 > 1.0
                        b = "mushroom_stem" if top and hash01(x + dx, dy, z + dz, 4) < 0.07 else "red_mushroom_block"
                        put(x + dx, cy + dy, z + dz, b)
        for dx, dz in ((3, 3), (-3, -3), (3, -3), (-3, 3), (0, 4), (0, -4)):
            if abs(dx) < R - 1 and abs(dz) < R - 1:
                put(x + dx, cy - 1, z + dz, "shroomlight")
    else:
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                r = math.hypot(dx, dz)
                if r <= R:
                    put(x + dx, cy, z + dz, "brown_mushroom_block")
                    if r <= R - 1.5:
                        put(x + dx, cy + 1, z + dz, "brown_mushroom_block")
                    if r >= R - 1.2:
                        put(x + dx, cy - 1, z + dz, "brown_mushroom_block")
        for dx, dz in ((2, 2), (-2, -2), (2, -2), (-2, 2)):
            put(x + dx, cy - 1, z + dz, "shroomlight")
    if soul:
        ry = max(3, int(R * 0.7))
        for y in range(t + 1, cy + ry):
            put(x, y, z + 2, "air")
        for y in range(t + 1, cy + ry):
            put(x, y, z + 2, "ladder[facing=south]")
        return cy + int(ry * math.sqrt(1 - 9 / (R * R))) + 1
    return cy


# the Fairy Soul mushroom: a huge red cap with a ladder up its stem
SMX, SMZ = 276, 52
top_y = giant(SMX, SMZ, "red", 13, 9, thick=True, soul=True)
area.fairy_soul(SMX, top_y, SMZ + 3)
reserve(SMX - 11, SMZ - 11, SMX + 11, SMZ + 11)
GIANTS = [(SMX, SMZ)]

# lava puddles: shallow, in fully enclosed basins (neighbours all at y >= 64), the rim fake-flowing with magma blocks
puddle = set()
for gx in range(112, 322, 9):
    for gz in range(8, 116, 9):
        if hash01(gx, gz, 3, 3) > 0.22:
            continue
        cx = gx + int(hash01(gx, gz, 1, 3) * 7)
        cz = gz + int(hash01(gx, gz, 2, 3) * 7)
        r = 1.5 + hash01(gx, gz, 4, 3) * 1.5
        for x in range(int(cx - r) - 2, int(cx + r) + 3):
            for z in range(int(cz - r) - 2, int(cz + r) + 3):
                if ((x - cx) / (r * 1.25)) ** 2 + ((z - cz) / r) ** 2 <= 1.0 + 0.25 * noise(x, z, 3, 2.0):
                    if KIND.get((x, z)) == "marsh" and HT[(x, z)] >= 64:
                        puddle.add((x, z))
changed = True
while changed:
    changed = False
    for (x, z) in list(puddle):
        bad = False
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, z + dz)
                if n in puddle:
                    continue
                if (HT.get(n) is None or HT[n] < 64 or n in RES or KIND.get(n) != "marsh"):
                    bad = True
        if bad:
            puddle.discard((x, z))
            changed = True
for (x, z) in puddle:
    t = HT[(x, z)]
    for y in range(64, t + 1):
        put(x, y, z, "air")
    put(x, 63, z, "lava")
    put(x, 62, z, "magma_block")
    put(x, 61, z, "blackstone")
    area.top[(x, z)] = 63
    RES.add((x, z))
rim = set()
for (x, z) in puddle:
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            n = (x + dx, z + dz)
            if n not in puddle:
                rim.add(n)
for (x, z) in sorted(rim):
    t = HT[(x, z)]
    r = hash01(x, 8, z, 5)
    if r < 0.22:
        put(x, t, z, "magma_block")
    elif r < 0.30:
        put(x, t + 1, z, "shroomlight")
    RES.add((x, z))

# mushroom fields (open mycelium with small mushrooms, for the Mushroom Bulls)
FIELDS = [(150, 45, 17), (292, 40, 16), (190, 88, 15), (300, 100, 13), (132, 100, 12), (250, 85, 14)]


def in_field(x, z, m=0):
    return any(math.hypot(x - fx, z - fz) <= fr + m for fx, fz, fr in FIELDS)


for (fx, fz, fr) in FIELDS:
    for x in range(fx - fr, fx + fr + 1):
        for z in range(fz - fr, fz + fr + 1):
            if math.hypot(x - fx, z - fz) <= fr and KIND.get((x, z)) == "marsh" and (x, z) not in RES:
                put(x, HT[(x, z)], z, "mycelium")
for (x, z), kind in sorted(KIND.items()):
    if kind != "marsh" or (x, z) in RES:
        continue
    t = HT[(x, z)]
    r = hash01(x, 31, z, 6)
    inf = in_field(x, z)
    if inf and r < 0.16:
        put(x, t + 1, z, "red_mushroom" if r < 0.09 else "brown_mushroom")
    elif not inf and r < 0.045:
        put(x, t + 1, z, "red_mushroom" if hash01(x, 32, z, 6) < 0.5 else "brown_mushroom")
    elif not inf and r < 0.06:
        put(x, t + 1, z, "crimson_roots" if False else "brown_mushroom")
n_giants = 0
for (x, z), kind in sorted(KIND.items()):
    if kind != "marsh" or (x, z) in RES or in_field(x, z, 3) or HT[(x, z)] < 63:
        continue
    if hash01(x, 41, z, 7) > 0.0042:
        continue
    if any(math.hypot(x - gx, z - gz) < 13 for gx, gz in GIANTS):
        continue
    R = 4 + int(hash01(x, 42, z, 7) * 3)
    if any((x + dx, z + dz) in RES for dx in (-R, 0, R) for dz in (-R, 0, R)):
        continue
    if not (126 < x < 318):
        continue
    h = 6 + int(hash01(x, 43, z, 7) * 6)
    if hash01(x, 44, z, 7) < 0.55:
        giant(x, z, "red", h, R, thick=h > 9)
    else:
        giant(x, z, "brown", h - 1, R + 1, thick=h > 9)
    GIANTS.append((x, z))
    n_giants += 1
print("giant mushrooms", n_giants + 1, "puddle cells", len(puddle))

# ================================================================ the Dojo
DG = 65
DOJO = dict(wall="white_terracotta", pillar="stripped_dark_oak_log[axis=y]", pane="white_stained_glass_pane",
            stair="red_nether_brick_stairs", slab="red_nether_brick_slab[type=bottom]", fill="red_terracotta",
            floor="spruce_planks", ceil="dark_oak_planks", door="dark_oak_door")
stone_pick = area.mix({"stone_bricks": 3, "polished_andesite": 3, "cracked_stone_bricks": 1, "smooth_stone": 2})
# the path from the torii and the patio in front of the hall
pave(238, 128, 241, 148, DG, lambda x, z: stone_pick(x, DG, z, 2))
pave(237, 148, 242, 182, DG, "smooth_stone")
reserve(236, 126, 243, 184, 1)
# torii gates
for zt in (131, 140):
    for xp in (236, 243):
        fill(xp, DG + 1, zt, xp, DG + 7, zt, "red_concrete")
    fill(236, DG + 5, zt, 243, DG + 5, zt, "red_concrete")
    fill(235, DG + 7, zt, 244, DG + 7, zt, "black_concrete")
    fill(234, DG + 8, zt, 245, DG + 8, zt, "black_concrete") if False else None
    for xk in (234, 245):
        put(xk, DG + 8, zt, "black_concrete")
    for xk in (235, 244):
        put(xk, DG + 7, zt, "black_concrete")
    put(238, DG + 4, zt, "lantern[hanging=true]")
    put(241, DG + 4, zt, "lantern[hanging=true]")
    for xp in (236, 243):
        put(xp, DG + 8, zt, "red_concrete") if False else None
sign(area, 244, DG + 1, 128, 8, ["Dojo"])
for z in range(134, 184, 6):
    for xl in (237, 242):
        if z != 140:
            fill(xl, DG + 1, z, xl, DG + 2, z, "stone_brick_wall")
            put(xl, DG + 3, z, "lantern[hanging=false]")
# the hall (faces east, onto the patio and the arena)
building(218, 152, 236, 178, 7, DOJO, door_side="e", door_at=165, over=2, door_w=3, axis_="z", win=4, lamps=True)
for x in range(219, 236):
    for z in range(153, 178):
        if (x // 3 + z // 3) % 2 == 0 and (x < 231 or abs(z - 165) > 2):
            put(x, DG + 1, z, "green_carpet")
for z in (156, 162, 168, 174):
    for x in (219, 220):
        put(x, DG + 1, z, "dark_oak_fence")
        put(x, DG + 2, z, "dark_oak_fence")
        put(x, DG + 3, z, "iron_bars") if False else None
for z in (156, 160, 170, 174):
    put(222, DG + 1, z, "hay_block")
    put(222, DG + 2, z, "carved_pumpkin[facing=east]")
# veranda
for x in range(237, 241):
    pass
fill(236, DG + 1, 155, 236, DG + 1, 155, "dark_oak_fence") if False else None
area.npc("Master Tao", 238, DG + 1, 165, -90.0, "at the dojo hall's entrance, facing the arena (east)")
# the arena: exactly 31x31 (x 244..274, z 150..180), floor y 65, walls 4 high, open sky
AX1, AX2, AZ1, AZ2 = 244, 274, 150, 180
for x in range(AX1, AX2 + 1):
    for z in range(AZ1, AZ2 + 1):
        d = max(abs(x - 259), abs(z - 165))
        b = "smooth_stone" if d in (5, 6, 12, 13) else "polished_andesite" if (x + z) % 2 else "stone_bricks"
        if d <= 1:
            b = "chiseled_stone_bricks"
        put(x, DG, z, b)
for x in range(AX1 - 1, AX2 + 2):
    for z in range(AZ1 - 1, AZ2 + 2):
        if x in (AX1 - 1, AX2 + 1) or z in (AZ1 - 1, AZ2 + 1):
            if x == AX1 - 1 and 164 <= z <= 166:
                for y in range(DG + 1, DG + 5):
                    put(x, y, z, "air")
                continue
            corner = x in (AX1 - 1, AX2 + 1) and z in (AZ1 - 1, AZ2 + 1)
            for y in range(DG + 1, DG + 5):
                put(x, y, z, "dark_oak_log[axis=y]" if corner or (x + z) % 8 == 0 else "white_concrete")
            put(x, DG + 4, z, "dark_oak_slab[type=top]") if not corner else None
for (px, pz) in ((AX1 - 1, AZ1 - 1), (AX2 + 1, AZ1 - 1), (AX1 - 1, AZ2 + 1), (AX2 + 1, AZ2 + 1)):
    put(px, DG + 5, pz, "lantern[hanging=false]")
for z in (163, 167):
    fill(AX1 - 1, DG + 1, z, AX1 - 1, DG + 5, z, "dark_oak_log[axis=y]")
    put(AX1 - 1, DG + 6, z, "lantern[hanging=false]")
for k in range(4):                                       # lanterns on the outside of the arena wall, north and south
    for x in (250 + k * 8, 266 + k * 0):
        pass
for x in range(250, 272, 7):
    for z in (AZ1 - 2, AZ2 + 2):
        fill(x, DG + 1, z, x, DG + 2, z, "dark_oak_fence")
        put(x, DG + 3, z, "lantern[hanging=false]")
reserve(AX1 - 2, AZ1 - 2, AX2 + 2, AZ2 + 2)
area.npc("spawn:Dojo", 259, DG + 1, 165, 0.0, "the middle of the 31x31 dojo arena, where players stand")
# the sensei's house, the teahouse and the zen garden
building(222, 190, 234, 201, 6, DOJO, door_side="n", over=2, axis_="x")
reserve(218, 186, 238, 205)
TX1, TZ1, TX2, TZ2 = 244, 190, 258, 202
pave(TX1, TZ1, TX2, TZ2, DG, "spruce_planks")
for (px, pz) in ((TX1, TZ1), (TX2, TZ1), (TX1, TZ2), (TX2, TZ2), ((TX1 + TX2) // 2, TZ1), ((TX1 + TX2) // 2, TZ2)):
    fill(px, DG + 1, pz, px, DG + 3, pz, "dark_oak_log[axis=y]")
roof(TX1, TZ1, TX2, TZ2, DG + 3, "red_nether_brick_stairs", "red_nether_brick_slab[type=bottom]", "red_terracotta", "x", 1)
for x in range(TX1 + 3, TX2 - 2, 3):
    put(x, DG + 1, 196, "spruce_slab[type=bottom]")
    put(x - 1, DG + 1, 196, "red_carpet")
    put(x + 1, DG + 1, 196, "red_carpet")
for x in (TX1 + 2, TX2 - 2):
    put(x, DG + 3, 196, "lantern[hanging=true]")
reserve(TX1 - 2, TZ1 - 2, TX2 + 2, TZ2 + 2)
for x in range(262, 300):                                # raked gravel garden with rocks, south-east
    for z in range(188, 215):
        if (x, z) not in RES:
            put(x, DG, z, "gravel" if (x // 2) % 2 == 0 else "light_gray_concrete_powder" if False else "andesite")
            RES.add((x, z))
for (rx, rz) in ((270, 196), (278, 204), (288, 194), (292, 208), (266, 208)):
    for (dx, dz, hh) in ((0, 0, 2), (1, 0, 1), (0, 1, 1), (-1, 0, 1)):
        fill(rx + dx, DG + 1, rz + dz, rx + dx, DG + hh, rz + dz, "stone" if False else "dripstone_block" if hh == 2 else "andesite")
    put(rx, DG + 3, rz, "flowering_azalea" if hash01(rx, 1, rz) < 0.5 else "azalea")
for x in range(262, 300, 9):
    lamp_post(x, DG, 187, "lantern[hanging=false]", "dark_oak_fence", 3)
# bamboo groves (and the path to the hidden soul behind the hall)
BAMB = [(197, 128, 217, 186), (280, 128, 303, 186), (196, 205, 220, 224), (298, 190, 304, 224)]
corridor = set()
for x in range(211, 239):
    for z in (146, 147):
        corridor.add((x, z))
for z in range(146, 171):
    for x in (211, 212):
        corridor.add((x, z))
for x in range(209, 215):
    for z in range(165, 169):
        corridor.add((x, z))
for (x, z) in corridor:
    put(x, DG, z, "coarse_dirt" if hash01(x, 9, z) < 0.5 else "dirt_path")
    RES.add((x, z))
for (bx1, bz1, bx2, bz2) in BAMB:
    for x in range(bx1, bx2 + 1):
        for z in range(bz1, bz2 + 1):
            if (x, z) in RES or (x, z) in corridor:
                continue
            if x >= 217 and bx1 == 197 and 150 <= z <= 181:
                continue
            if hash01(x, 51, z, 3) < 0.30:
                hgt = 5 + int(hash01(x, 52, z, 3) * 5)
                for y in range(DG + 1, DG + hgt):
                    put(x, y, z, "bamboo[age=1,leaves=none,stage=0]")
                put(x, DG + hgt, z, "bamboo[age=1,leaves=large,stage=1]")
                put(x, DG + hgt - 1, z, "bamboo[age=1,leaves=small,stage=1]")
for x in (210, 213):
    lamp_post(x, DG, 163, "lantern[hanging=false]", "dark_oak_fence", 2)
area.fairy_soul(211, DG + 1, 167)

# ================================================================ Odger's Hut: stilts and a pier over the lava
OD = 64
dp = (141, 197, 159, 214)
for x in range(dp[0], dp[2] + 1):
    for z in range(dp[1], dp[3] + 1):
        put(x, OD, z, "spruce_planks" if (x + z) % 5 else "stripped_spruce_log[axis=x]")
        for y in range(OD - 1, 55, -1):
            if area.solid(x, y, z):
                break
            if (x - dp[0]) % 6 == 0 and (z - dp[1]) % 5 == 0:
                put(x, y, z, "stripped_spruce_log[axis=y]")
            elif y == OD - 1 and (x in (dp[0], dp[2]) or z == dp[3]):
                put(x, y, z, "spruce_slab[type=top]") if False else None
for x in range(dp[0], dp[2] + 1):
    for z in (dp[1], dp[3]):
        pass
# the hut
hx1, hz1, hx2, hz2 = 144, 200, 154, 209
for x in range(hx1, hx2 + 1):
    for z in range(hz1, hz2 + 1):
        edge = x in (hx1, hx2) or z in (hz1, hz2)
        corner = x in (hx1, hx2) and z in (hz1, hz2)
        put(x, OD, z, "spruce_planks")
        for y in range(OD + 1, OD + 5):
            put(x, y, z, "stripped_spruce_log[axis=y]" if corner else "spruce_planks" if edge else "air")
        put(x, OD + 5, z, "spruce_planks" if edge else "spruce_planks")
for x in range(hx1 + 2, hx2 - 1, 3):
    for z in (hz1, hz2):
        if not (z == hz2 and abs(x - 149) <= 1):
            put(x, OD + 2, z, "glass_pane")
            put(x, OD + 3, z, "glass_pane")
for z in (hz1 + 3, hz1 + 6):
    for x in (hx1, hx2):
        put(x, OD + 2, z, "glass_pane")
        put(x, OD + 3, z, "glass_pane")
door(149, OD + 1, hz2, "south", "spruce_door")
roof(hx1, hz1, hx2, hz2, OD + 4, "spruce_stairs", "spruce_slab[type=bottom]", "spruce_planks", "x", 1)
put(149, OD + 4, 205, "lantern[hanging=true]")
for (x, z, b) in ((145, 201, "barrel[facing=up]"), (146, 201, "barrel[facing=up]"), (153, 201, "chest[facing=west]"),
                  (145, 207, "crafting_table"), (153, 207, "cauldron"), (152, 201, "fletching_table")):
    put(x, OD + 1, z, b)
fill(147, OD + 1, 203, 151, OD + 1, 205, "blue_carpet")
for x in range(143, 156):
    put(x, OD + 1, hz2 + 1 if False else 211, "spruce_fence") if x % 2 == 0 and False else None
area.npc("Odger", 149, OD + 1, 206, 0.0, "inside the stilt hut, facing the door; lava fishing from the pier")
# railings round the deck, lanterns on posts
for x in range(dp[0], dp[2] + 1):
    for z in (dp[1] + 1, dp[3]):
        pass
for x in range(dp[0], dp[2] + 1):
    if not (147 <= x <= 151):
        put(x, OD + 1, dp[3], "spruce_fence")
for z in range(dp[1] + 2, dp[3] + 1):
    for x in (dp[0], dp[2]):
        put(x, OD + 1, z, "spruce_fence")
for (x, z) in ((dp[0], dp[3]), (dp[2], dp[3]), (dp[0], dp[1] + 4), (dp[2], dp[1] + 4), (dp[0], 206), (dp[2], 206)):
    put(x, OD + 2, z, "spruce_fence")
    put(x, OD + 3, z, "lantern[hanging=false]")
# the boardwalk from the shore and the pier (x 147..151, z 215..249)
for z in range(190, dp[1]):
    for x in range(148, 152):
        t = HT.get((x, z))
        if t is not None and t <= OD:
            put(x, OD, z, "spruce_planks")
            for y in range(t + 1, OD):
                put(x, y, z, "stripped_spruce_log[axis=y]") if (x in (148, 151) and z % 4 == 0) else None
for z in range(dp[3] + 1, 250):
    for x in range(147, 152):
        put(x, OD, z, "spruce_planks")
        if x in (147, 151):
            put(x, OD + 1, z, "spruce_fence") if not (226 <= z <= 230 and x == 151) else None
        if x in (147, 151) and z % 6 == 0:
            for y in range(OD - 1, 55, -1):
                put(x, y, z, "stripped_spruce_log[axis=y]")
            put(x, OD + 2, z, "spruce_fence")
            put(x, OD + 3, z, "lantern[hanging=false]")
        if x == 149 and z % 6 == 3:
            for y in range(OD - 1, 55, -1):
                put(x, y, z, "stripped_spruce_log[axis=y]")
for x in range(146, 153):                                  # the fishing platform at the end
    for z in range(243, 250):
        put(x, OD, z, "spruce_planks")
        if x in (146, 152) or z == 249:
            put(x, OD + 1, z, "spruce_fence")
put(146, OD + 2, 243, "lantern[hanging=false]")
put(152, OD + 2, 243, "lantern[hanging=false]")
for dz in (-1, 0, 1):
    pass
# a fishing rod rack and a sign on the pier
sign(area, 146, OD + 1, 219, 4, ["Odger's Pier", "Lava fishing"])
fill(152, OD + 1, 216, 152, OD + 2, 216, "spruce_fence")
put(152, OD + 3, 216, "lantern[hanging=false]")
# under the pier: a hidden platform with the Fairy Soul, reached by a trapdoor in the deck and a ladder down a post
for x in range(148, 153):
    for z in range(228, 233):
        put(x, 61, z, "blackstone")
        for y in range(56, 61):
            put(x, y, z, "blackstone")
for y in (62, 63):
    put(150, y, 229, "stripped_spruce_log[axis=y]")
for y in (62, 63, 64):
    put(150, y, 228, "ladder[facing=north]")
put(150, OD, 228, "air")
put(150, OD, 228, "spruce_trapdoor[facing=north,half=bottom,open=false]")
put(150, 62, 228, "ladder[facing=north]")
put(149, 62, 231, "soul_lantern[hanging=false]")
area.fairy_soul(150, 62, 231)
reserve(138, 190, 162, 249)


# ================================================================ the ruined shrine on the north coast (a hidden Fairy Soul)
SHX, SHZ = 270, -227
for x in range(SHX - 3, SHX + 4):
    for z in range(SHZ - 3, SHZ + 4):
        if HT.get((x, z)) is None:
            continue
        d = max(abs(x - SHX), abs(z - SHZ))
        put(x, G, z, "cracked_polished_blackstone_bricks" if d < 3 else "polished_blackstone_bricks")
        area.top[(x, z)] = G
for (dx, dz, hh) in ((-3, -3, 5), (3, -3, 3), (-3, 3, 2), (3, 3, 4), (-3, 0, 2), (3, 0, 1)):
    fill(SHX + dx, G + 1, SHZ + dz, SHX + dx, G + hh, SHZ + dz, "purpur_pillar[axis=y]")
put(SHX + 3, G + 5, SHZ + 3, "amethyst_cluster[facing=up]")
put(SHX, G + 1, SHZ + 2, "soul_lantern[hanging=false]")
put(SHX - 1, G + 1, SHZ - 2, "amethyst_cluster[facing=up]")
reserve(SHX - 5, SHZ - 5, SHX + 5, SHZ + 5)
area.fairy_soul(SHX, G + 1, SHZ)
assert HT.get((SHX, SHZ)) is not None, "shrine must be on land"


# ================================================================ the wilds: crimson roots, huge fungi, lamps along the roads
def huge_fungus(x, z, warped):
    t = HT[(x, z)]
    stem = "warped_stem" if warped else "crimson_stem"
    cap = "warped_wart_block" if warped else "nether_wart_block"
    h = 5 + int(hash01(x, 61, z, 2) * 6)
    R = 3 + int(hash01(x, 62, z, 2) * 2)
    for y in range(t + 1, t + h + 1):
        put(x, y, z, axis(stem))
    for dy, rr in enumerate((R, R, R - 1, R - 2)):
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if dx * dx + dz * dz <= rr * rr + 1 and hash01(x + dx, dy + 70, z + dz, 3) > 0.08:
                    if (dx or dz) or dy > 1:
                        b = "shroomlight" if hash01(x + dx, dy, z + dz, 8) < 0.1 and dy == 0 else cap
                        put(x + dx, t + h - 1 + dy, z + dz, b)


fungi = 0
for (x, z), kind in sorted(KIND.items()):
    if kind != "wild" or (x, z) in RES:
        continue
    if base_h(x, z)[1] > 0.02:
        continue
    t = HT[(x, z)]
    if t < 63 or coast_d(x, z) < 8:
        continue
    r = hash01(x, 81, z, 4)
    if r < 0.0034:
        if any((x + dx, z + dz) in RES for dx in (-4, 0, 4) for dz in (-4, 0, 4)):
            continue
        huge_fungus(x, z, hash01(x, 82, z, 4) < 0.25)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                RES.add((x + dx, z + dz))
        fungi += 1
for (x, z), kind in sorted(KIND.items()):
    if kind != "wild" or (x, z) in RES:
        continue
    t = HT[(x, z)]
    if area.get(x, t + 1, z) not in (None, "minecraft:air"):
        continue
    sb = area.get(x, t, z) or ""
    r = hash01(x, 91, z, 6)
    if sb.endswith("crimson_nylium"):
        if r < 0.10:
            put(x, t + 1, z, "crimson_roots")
        elif r < 0.115:
            put(x, t + 1, z, "crimson_fungus")
        elif r < 0.125:
            put(x, t + 1, z, "nether_sprouts")
    elif sb.endswith("warped_nylium"):
        if r < 0.10:
            put(x, t + 1, z, "warped_roots")
        elif r < 0.115:
            put(x, t + 1, z, "warped_fungus")
road_lamps(14)
for cz in CROSS:
    for x in (104, 112, 120):
        pass
sign(area, 122, BRIDGE + 1, -85, 12, ["Crimson Isle", "Mage side"])
print("huge fungi", fungi)

# ================================================================ spots: souls recorded above; notes
area.spots["notes"].append("Scarleton: walls x185..305 z-218..-112 (west gate z-168..-162, south gate x242..248); plaza centre (245,65,-165); "
                           "palace x215..275 z-214..-188; bank x262..278 z-176..-158; auction x212..228 z-176..-158; "
                           "bazaar x250..270 z-147..-135; workshop x220..240 z-147..-135")
area.spots["notes"].append("Bridge ends (box edge x=100): north deck z-83..-77, south deck z107..113, deck y66 (players at 67); "
                           "land and roads start at x=107")
area.spots["notes"].append("Courtyard rune circle centre (265,66,-14); Dojo arena x244..274 z150..180 floor y65; "
                           "Odger's pier x147..151 z215..249 deck y64; trapdoor to the soul at (150,64,228)")

# ================================================================ zones (no overlap: the region is split by boxes)
MARSH_BLOCKS = {"minecraft:mycelium": {"becomes": "minecraft:bedrock", "regrow": 15}}
Y_LO, Y_HI = 40, 189
area.zone("Scarleton", box=((160, Y_LO, -250), (329, Y_HI, -107)))
area.zone("Mage Outpost", box=((100, Y_LO, -140), (159, Y_HI, -45)))
area.zone("Cathedral", box=((225, Y_LO, -106), (329, Y_HI, -33)))
area.zone("Courtyard", box=((225, Y_LO, -32), (329, Y_HI, 5)))
area.zone("Mystic Marsh", box=((100, Y_LO, 6), (329, Y_HI, 126)), blocks=MARSH_BLOCKS, mobs=[
    mob("Mushroom Bull", 6, 8, (108, 62, 6), (322, 100, 122)),
])
area.zone("Dojo", box=((190, Y_LO, 127), (329, Y_HI, 249)))
area.zone("Odger's Hut", box=((100, Y_LO, 127), (189, Y_HI, 249)))
area.save()
