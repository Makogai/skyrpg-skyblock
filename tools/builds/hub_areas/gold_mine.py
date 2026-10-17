#!/usr/bin/env python3
"""The Gold Mine (Hypixel's second Mining island, Mining I): a big rocky floating island with a terraced open-pit gold
mine in the middle (ramps, scaffolding, rails, lanterns), tunnels full of iron off it, a mining camp at the south edge
(Rusty, the Iron Forger, the Gold Forger, the Lazy Miner), a launch pad back to the Hub and, at the bottom of the pit,
the big mine shaft down to the Deep Caverns.

    python tools/builds/hub_areas/gold_mine.py
"""
import math
from kit import Area, hash01, noise

G = 64                       # island top; players stand at 65
area = Area("gold_mine", (-120, 10, -120), (240, 130, 240), version=1, dimension="skyrpg_skyblock:gold_mine",
            island="Gold Mine", shell=3)

surface = area.mix({"gravel": 4, "coarse_dirt": 3, "cobblestone": 2, "andesite": 2, "tuff": 1})
rock = area.mix({"andesite": 5, "tuff": 3, "cobblestone": 2, "granite": 2, "calcite": 1})


def lobe(x, z):
    """Stretches the island south so the camp and the arrival have solid ground."""
    return 1 + (0.33 * math.exp(-(x * x) / (2 * 34.0 ** 2)) if z > 0 else 0.0)


cols = area.island(0, 0, 90, top=G, seed=7, surface=surface, hills=0, depth=48, stone=rock, wobble=0.12,
                   dirt="coarse_dirt", shape=lobe)

# ---------------------------------------------------------------- the open pit
PCX, PCZ, RX, RZ = 0, -8, 42, 36
TH = [1.0, 0.85, 0.70, 0.55, 0.40, 0.25, 0.0]      # band i is [TH[i+1], TH[i]]; its floor is 60 - 5 * i (core: 35)
FLOORS = [60 - 5 * i for i in range(6)]


def e_plain(x, z):
    return math.hypot((x - PCX) / RX, (z - PCZ) / RZ)


def e_of(x, z):
    return e_plain(x, z) + 0.022 * noise(x, z, 11, 2.2)


def band(e):
    for i in range(6):
        if e >= TH[i + 1]:
            return i
    return 5


def pit_wall(x, y, z):
    c = hash01(x // 2, y // 2, z // 2, 5)
    h = hash01(x, y, z, 6)
    if c < 0.22 and h < 0.72:
        return "minecraft:gold_ore"
    if c < 0.30 and h < 0.6:
        return "minecraft:iron_ore"
    if c < 0.38 and h < 0.6:
        return "minecraft:coal_ore"
    return "minecraft:stone"


def tun_wall(x, y, z):
    c = hash01(x // 2, y // 2, z // 2, 15)
    h = hash01(x, y, z, 16)
    if c < 0.20 and h < 0.7:
        return "minecraft:iron_ore"
    if c < 0.27 and h < 0.6:
        return "minecraft:gold_ore"
    if c < 0.34 and h < 0.6:
        return "minecraft:coal_ore"
    return "minecraft:stone"


terrace = area.mix({"cobblestone": 4, "gravel": 3, "coarse_dirt": 1, "andesite": 1})
rim = area.mix({"gravel": 4, "cobblestone": 3, "coarse_dirt": 2})
PIT = {}
for x in range(-50, 51):
    for z in range(-54, 38):
        if (x, z) not in cols:
            continue
        e = e_of(x, z)
        if e >= 1.05:
            continue
        if e >= 1.0:
            for y in range(57, G):
                area.set(x, y, z, pit_wall(x, y, z))
            area.set(x, G, z, rim(x, G, z))
            continue
        i = band(e)
        f = FLOORS[i]
        PIT[(x, z)] = f
        for y in range(f + 1, G + 4):
            area.set(x, y, z, "minecraft:air")
        for y in range(f - 1, f - 7, -1):
            area.set(x, y, z, pit_wall(x, y, z))
        area.set(x, f, z, terrace(x, f, z))
        area.top[(x, z)] = f


def hr(e):
    if e >= 1.0:
        return 60 + min(4, int(round(4 * (e - 1.0) / 0.1)))
    i = band(e)
    if i == 5:
        return 35
    u = (TH[i] - e) / (TH[i] - TH[i + 1])
    return 60 - 5 * i - int(round(5 * u))


RAMPS = []          # (name, fixed coord, width, axis) for decor avoidance
ramp_cols = set()


def ramp(axis, c, outward, width):
    """A ramp running out of the pit along `axis` ('x' or 'z') at the other coordinate `c`, outward = +1/-1."""
    face = {("x", 1): "east", ("x", -1): "west", ("z", 1): "south", ("z", -1): "north"}[(axis, outward)]
    centre = PCX if axis == "x" else PCZ
    for k in range(0, 60):
        a = centre + outward * k
        e0 = e_plain(a, c) if axis == "x" else e_plain(c, a)
        if e0 >= 1.12:
            break
        h = hr(e0)
        e_in = e_plain(a - outward, c) if axis == "x" else e_plain(c, a - outward)
        stair = k > 0 and hr(e_in) == h - 1
        for s in range(-(width // 2), width // 2 + 1):
            x, z = (a, c + s) if axis == "x" else (c + s, a)
            if (x, z) not in cols:
                continue
            for y in range(h + 1, G + 5):
                area.set(x, y, z, "minecraft:air")
            for y in range(h - 1, h - 5, -1):
                area.set(x, y, z, pit_wall(x, y, z))
            if stair:
                area.set(x, h, z, f"minecraft:cobblestone_stairs[facing={face},half=bottom]")
            else:
                area.set(x, h, z, terrace(x, h, z))
            area.top[(x, z)] = h
            ramp_cols.add((x, z))
    RAMPS.append((axis, c, width))


ramp("z", 0, 1, 5)           # south, to the camp (the main way down)
ramp("x", -14, 1, 3)         # east
ramp("x", 2, -1, 3)          # west
ramp("z", 10, -1, 3)         # north


def near_ramp(x, z, d=3):
    for dx in range(-d, d + 1):
        for dz in range(-d, d + 1):
            if (x + dx, z + dz) in ramp_cols:
                return True
    return False


# ---------------------------------------------------------------- rocky outcrops on the far side (andesite and tuff only)
mound = area.mix({"andesite": 4, "tuff": 3, "granite": 2, "cobblestone": 2, "calcite": 1})
for (x, z) in sorted(cols):
    e = e_plain(x, z)
    if e < 1.25 or (z > 30 and abs(x) < 56):
        continue
    if z > 30:
        continue
    bump = int(max(0.0, noise(x, z, 3, 1.3) - 0.05) * 15 * min(1.0, (e - 1.25) / 0.3))
    if bump <= 0 or area.top.get((x, z)) != G:
        continue
    for y in range(G + 1, G + bump + 1):
        area.set(x, y, z, mound(x, y, z))
    area.top[(x, z)] = G + bump

# ---------------------------------------------------------------- the camp ground (level, cobbled)
camp_ground = area.mix({"cobblestone": 3, "gravel": 3, "coarse_dirt": 2, "andesite": 1})
for x in range(-52, 53):
    for z in range(36, 110):
        if (x, z) in cols:
            area.flatten(x, z, x, z, G, camp_ground(x, G, z))


def sign(x, y, z, rot, l2, l3):
    area.set(x, y, z, f"minecraft:spruce_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": ['""', f'"{l2}"', f'"{l3}"', '""'],
                                                     "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""', '""', '""', '""'], "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


# ---------------------------------------------------------------- the carving engine (tunnels, caverns, pockets)
AIR, FLOOR, FORCE = set(), {}, set()
SUPS, RAILS, CAVF = [], {}, []
mine_floor = area.mix({"cobblestone": 3, "gravel": 3, "andesite": 1, "coarse_dirt": 1})


def disc(half):
    return [(dx, dz) for dx in range(-half, half + 1) for dz in range(-half, half + 1) if dx * dx + dz * dz <= half * half + 1]


def carve(pts, half=2, h=4, floor=mine_floor, every=5, rails=False):
    seen = {}
    nxt = 0.0
    acc = 0.0
    for (x1, f1, z1), (x2, f2, z2) in zip(pts, pts[1:]):
        L = math.hypot(x2 - x1, z2 - z1)
        n = max(1, int(L * 2))
        ux, uz = (x2 - x1) / L, (z2 - z1) / L
        for i in range(n + 1):
            t = i / n
            cx, cy, cz = x1 + (x2 - x1) * t, f1 + (f2 - f1) * t, z1 + (z2 - z1) * t
            F, bx, bz = int(round(cy)), int(round(cx)), int(round(cz))
            for dx, dz in disc(half):
                seen.setdefault((bx + dx, bz + dz), F)
            if acc + L * t >= nxt:
                nxt += every
                SUPS.append((bx, bz, F, "z" if abs(ux) >= abs(uz) else "x", half, h, len(SUPS)))
            if rails and f1 == f2:
                RAILS[(bx, F + 1, bz)] = "north_south" if abs(uz) > abs(ux) else "east_west"
        acc += L
    for (x, z), F in seen.items():
        for y in range(F + 1, F + h + 1):
            AIR.add((x, y, z))
        FLOOR[(x, F, z)] = floor(x, F, z)


def cavern(cx, F, cz, rx, rz, hh, seed, pillars=3):
    cells = []
    for x in range(cx - rx - 2, cx + rx + 3):
        for z in range(cz - rz - 2, cz + rz + 3):
            r = math.hypot((x - cx) / rx, (z - cz) / rz) + 0.18 * noise(x, z, seed, 2.5)
            if r >= 1:
                continue
            hgt = int(4 + (hh - 4) * (1 - r * r) ** 0.7)
            for y in range(F + 1, F + hgt + 1):
                AIR.add((x, y, z))
            FLOOR[(x, F, z)] = mine_floor(x, F, z)
            cells.append((x, z, hgt, r))
    for p in range(pillars):
        a = hash01(p, 1, seed) * 6.28
        rr = 0.35 + 0.3 * hash01(p, 2, seed)
        px, pz = int(cx + math.cos(a) * rx * rr), int(cz + math.sin(a) * rz * rr)
        for dx in (0, 1):
            for dz in (0, 1):
                for y in range(F + 1, F + hh + 3):
                    AIR.discard((px + dx, y, pz + dz))
                    FORCE.add((px + dx, y, pz + dz))
    CAVF.extend((x, F, z, hgt, r) for (x, z, hgt, r) in cells)


def pocket(x1, z1, x2, z2, F, h=3):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(F + 1, F + h + 1):
                AIR.add((x, y, z))
            FLOOR[(x, F, z)] = mine_floor(x, F, z)


# Tunnels off the pit (iron-rich walls), each ending in a cavern.
TW = [(-20, 45, -8), (-38, 45, -8), (-48, 45, -16), (-54, 45, -28), (-52, 45, -40)]
TS = [(-38, 45, -8), (-40, 45, 6), (-34, 45, 22), (-28, 45, 34)]
TE = [(15, 40, -8), (34, 40, -6), (46, 40, 2), (56, 40, 14), (58, 40, 30)]
TN = [(0, 50, -30), (0, 50, -48), (-12, 50, -58), (-14, 50, -64)]
TD = [(0, 35, -14), (0, 35, -32)]         # the big shaft tunnel
for T, r, half, hh in ((TW, True, 2, 4), (TS, True, 2, 4), (TE, True, 2, 4), (TN, True, 2, 4), (TD, False, 3, 4)):
    carve(T, half=half, h=hh, rails=r)
cavern(-52, 45, -40, 9, 8, 12, 41, pillars=2)
cavern(-28, 45, 34, 8, 7, 10, 42, pillars=2)
cavern(58, 40, 30, 9, 8, 12, 43, pillars=3)
cavern(-14, 50, -66, 9, 8, 12, 44, pillars=2)
pocket(6, -68, 10, -64, 50)                 # a sealed pocket (fairy soul): mine through the stone to reach it
# a ledge niche high in the east cavern, reached by jumping up a few stones
pocket(66, 24, 68, 26, 44, 3)

for c in list(AIR):
    b = area.get(*c)
    if b is not None and b != "minecraft:air":
        area.set(*c, "minecraft:air")
done = set()
for (x, y, z) in AIR:
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, y + dy, z + dz)
                if n in done or n in AIR or n in FLOOR:
                    continue
                done.add(n)
                b = area.get(*n)
                if b is not None and b != "minecraft:air":
                    area.set(*n, tun_wall(*n))
for c in FORCE:
    area.set(*c, tun_wall(*c))
for c, b in FLOOR.items():
    if c not in AIR:
        area.set(*c, b)

# hidden crawl passage (1 wide, 2 tall, andesite/tuff walls) from the north cavern to the sealed pocket
for x in range(-6, 6):
    for y in (51, 52):
        area.set(x, y, -66, "minecraft:air")
    area.set(x, 50, -66, "minecraft:cobblestone")
    for (yy, zz) in ((50, -67), (51, -67), (52, -67), (50, -65), (51, -65), (52, -65), (53, -66)):
        if area.get(x, yy, zz) not in (None, "minecraft:air") or (x, yy, zz) not in AIR:
            area.set(x, yy, zz, "minecraft:andesite" if hash01(x, yy, zz, 3) < 0.6 else "minecraft:tuff")
for x in (-6, -5):
    area.set(x, 51, -66, "minecraft:cobweb")
    area.set(x, 52, -66, "minecraft:cobweb")
area.set(6, 51, -66, "minecraft:air")
area.set(6, 52, -66, "minecraft:air")

for (bx, bz, F, axis, half, h, k) in SUPS:
    if half == 3:
        continue
    for s in (-half, half):
        for y in range(F + 1, F + h):
            c = (bx, y, bz + s) if axis == "z" else (bx + s, y, bz)
            if c in AIR:
                area.set(*c, "minecraft:oak_log[axis=y]")
    for s in range(-half, half + 1):
        c = (bx, F + h, bz + s) if axis == "z" else (bx + s, F + h, bz)
        if c in AIR:
            area.set(*c, f"minecraft:spruce_log[axis={axis}]")
    if k % 2 == 0 and (bx, F + h - 1, bz) in AIR:
        area.set(bx, F + h - 1, bz, "minecraft:lantern[hanging=true]")
for (x, y, z), shape in RAILS.items():
    if (x, y, z) in AIR and (x, y - 1, z) in FLOOR:
        area.set(x, y, z, f"minecraft:rail[shape={shape}]")

for (x, F, z, hgt, r) in CAVF:
    k = hash01(x, 31, z, 2)
    if (x, F + 1, z) not in AIR:
        continue
    if k < 0.025 and r > 0.35:
        area.set(x, F + 1, z, "minecraft:cobblestone_slab[type=bottom]")
    elif k < 0.04 and r > 0.5:
        area.set(x, F + 1, z, "minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]")
    elif k < 0.05 and r > 0.5:
        area.set(x, F + 1, z, "minecraft:raw_iron_block")
    top = F + hgt
    if hash01(x, 32, z, 2) < 0.025 and (x, top, z) in AIR:
        area.set(x, top, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]")
    elif hash01(x, 33, z, 2) < 0.007 and (x, top, z) in AIR:
        area.set(x, top, z, "minecraft:lantern[hanging=true]")

# tunnel mouths: timber frames
for (x, F, z, axis) in ((-21, 45, -8, "x"), (16, 40, -8, "x"), (0, 50, -31, "z")):
    for s in (-2, 2):
        px, pz = (x, z + s) if axis == "x" else (x + s, z)
        area.fill(px, F + 1, pz, px, F + 3, pz, "minecraft:oak_log[axis=y]")
    for s in range(-2, 3):
        px, pz = (x, z + s) if axis == "x" else (x + s, z)
        area.set(px, F + 4, pz, f"minecraft:spruce_log[axis={'z' if axis == 'x' else 'x'}]")
    area.set(x, F + 3, z, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- pit dressing: scaffolding, rails, lanterns
def scaffold(x, f, z, hgt=7):
    """A little wooden scaffold tower: four posts, platforms, ladders."""
    for dx in (0, 3):
        for dz in (0, 3):
            area.fill(x + dx, f + 1, z + dz, x + dx, f + hgt, z + dz, "minecraft:oak_log[axis=y]")
    for y in (f + 3, f + hgt):
        for dx in range(0, 4):
            for dz in range(0, 4):
                if y == f + hgt or dx in (0, 3) or dz in (0, 3):
                    area.set(x + dx, y, z + dz, "minecraft:spruce_planks")
    for dx in range(0, 4):
        area.set(x + dx, f + hgt + 1, z, "minecraft:spruce_fence")
        area.set(x + dx, f + hgt + 1, z + 3, "minecraft:spruce_fence")
    for dz in (1, 2):
        area.set(x, f + hgt + 1, z + dz, "minecraft:spruce_fence")
        area.set(x + 3, f + hgt + 1, z + dz, "minecraft:spruce_fence")
    area.set(x + 1, f + hgt + 2, z + 1, "minecraft:lantern[hanging=false]")
    area.set(x + 1, f + hgt + 1, z + 1, "minecraft:barrel[facing=up]")
    for y in range(f + 1, f + hgt + 1):
        area.set(x + 1, y, z + 4, "minecraft:ladder[facing=south]") if False else None
    for y in range(f + 1, f + hgt):
        area.set(x + 1, y, z + 1, "minecraft:ladder[facing=south]") if False else None
        area.set(x + 1, y, z - 0, "minecraft:ladder[facing=south]")
    area.set(x + 1, f + hgt, z, "minecraft:air")
    for dx in (1, 2):
        for dz in range(1, 3):
            area.set(x + dx, f + 3, z + dz, "minecraft:air")


SC = [(-24, 55, -26), (21, 50, -29), (-29, 50, 14), (-18, 55, 24), (22, 55, 16), (-5, 60, -48), (30, 45, -4), (-13, 45, -22)]
placed = 0
for (x, f, z) in SC:
    ok = all(PIT.get((x + dx, z + dz)) == f for dx in (0, 3) for dz in (0, 3)) and not near_ramp(x, z, 5)
    if not ok:
        # find a nearby spot on this terrace
        for r in range(1, 8):
            for dx, dz in ((r, 0), (-r, 0), (0, r), (0, -r), (r, r), (-r, -r), (r, -r), (-r, r)):
                if all(PIT.get((x + dx + a, z + dz + b)) == f for a in (0, 3) for b in (0, 3)) and not near_ramp(x + dx, z + dz, 5):
                    x, z, ok = x + dx, z + dz, True
                    break
            if ok:
                break
    if ok:
        scaffold(x, f, z, 7 if f > 45 else 6)
        placed += 1

# lantern posts along every terrace edge
for ring in range(5):
    e_post = TH[ring + 1] + 0.015
    f = FLOORS[ring]
    for deg in range(0, 360, 20):
        a = math.radians(deg + 7 * ring)
        x = int(round(PCX + RX * e_post * math.cos(a)))
        z = int(round(PCZ + RZ * e_post * math.sin(a)))
        if PIT.get((x, z)) == f and not near_ramp(x, z, 2) and area.get(x, f + 1, z) in (None, "minecraft:air"):
            area.fill(x, f + 1, z, x, f + 2, z, "minecraft:spruce_fence")
            area.set(x, f + 3, z, "minecraft:lantern[hanging=false]")

# rails on two terraces, a ring of ore carts' worth of track on the pit floor
for (f, xr) in ((50, None), (40, None)):
    for z in range(-30, 12):
        zz = (z - PCZ) / RZ
        for sgn in (1, -1):
            lo, hi = TH[FLOORS.index(f) + 1], TH[FLOORS.index(f)]
            em = (lo + hi) / 2
            if abs(zz) >= em:
                continue
            x = int(round(PCX + sgn * RX * math.sqrt(em * em - zz * zz)))
            if PIT.get((x, z)) == f and not near_ramp(x, z, 2) and area.get(x, f + 1, z) in (None, "minecraft:air"):
                area.set(x, f + 1, z, "minecraft:rail[shape=north_south]")
for z in range(-17, 1):
    if PIT.get((0, z)) == 35 or PIT.get((1, z)) == 35:
        area.set(0, 36, z, "minecraft:rail[shape=north_south]")
for x in range(-8, 9):
    if PIT.get((x, -2)) == 35:
        area.set(x, 36, -2, "minecraft:rail[shape=east_west]")
area.set(0, 36, -2, "minecraft:rail[shape=east_west]")
for (x, z) in ((-8, -2), (8, -2)):
    if PIT.get((x, z)) == 35:
        area.set(x, 36, z, "minecraft:rail[shape=east_west]")
        area.fill(x, 36, z + 1, x, 37, z + 1, "minecraft:spruce_fence")
        area.set(x, 38, z + 1, "minecraft:lantern[hanging=false]")

# ore piles and barrels on the pit floor
for (x, z, blk) in ((-7, -4, "raw_gold_block"), (-6, -4, "raw_gold_block"), (-7, -3, "raw_iron_block"), (6, -3, "raw_gold_block"),
                    (7, -3, "gold_block"), (6, -2, "raw_gold_block"), (-3, -12, "raw_iron_block"), (3, -12, "raw_gold_block")):
    if PIT.get((x, z)) == 35:
        area.set(x, 36, z, f"minecraft:{blk}")
        if hash01(x, 3, z) < 0.5:
            area.set(x, 37, z, "minecraft:raw_gold_block")
for (x, z) in ((-8, -7), (8, -6), (-2, 1), (3, 1)):
    if PIT.get((x, z)) == 35:
        area.set(x, 36, z, "minecraft:barrel[facing=up]")

# ---------------------------------------------------------------- the headframe (winding tower) over the pit floor
for dx in (-4, 4):
    for dz in (-11, -5):
        area.fill(dx, 36, dz, dx, 48, dz, "minecraft:stripped_spruce_log[axis=y]")
for y in (44, 48):
    for x in range(-4, 5):
        for z in (-11, -5):
            area.set(x, y, z, "minecraft:spruce_log[axis=x]")
    for z in range(-11, -4):
        for x in (-4, 4):
            area.set(x, y, z, "minecraft:spruce_log[axis=z]")
for x in range(-4, 5):
    for z in range(-11, -4):
        area.set(x, 44, z, "minecraft:spruce_planks" if (x, z) != (-3, -5) else "minecraft:air")
for x in range(-4, 5, 2):
    area.set(x, 45, -11, "minecraft:spruce_fence")
    area.set(x, 45, -5, "minecraft:spruce_fence")
for z in range(-10, -5):
    area.set(-4, 45, z, "minecraft:spruce_fence")
    area.set(4, 45, z, "minecraft:spruce_fence")
for y in range(36, 45):
    area.set(-3, y, -5, "minecraft:ladder[facing=east]")
area.fill(0, 45, -8, 0, 47, -8, "minecraft:oak_fence")
area.set(0, 48, -8, "minecraft:iron_block")
for x in (-2, 2):
    area.set(x, 43, -8, "minecraft:chain[axis=y]")
    area.set(x, 42, -8, "minecraft:lantern[hanging=true]")
area.set(0, 43, -8, "minecraft:chain[axis=y]")
area.set(0, 42, -8, "minecraft:chain[axis=y]")
area.set(0, 41, -8, "minecraft:chain[axis=y]")
area.set(0, 40, -8, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- the Deep Caverns shaft: frame, lift, pad
for x in (-4, 4):
    area.fill(x, 36, -17, x, 39, -17, "minecraft:stripped_oak_log[axis=y]")
    area.fill(x, 36, -14, x, 38, -14, "minecraft:oak_log[axis=y]")
area.fill(-4, 40, -17, 4, 40, -17, "minecraft:spruce_log[axis=x]")
area.fill(-4, 40, -14, 4, 40, -14, "minecraft:spruce_log[axis=x]")
for z in (-16, -15):
    area.fill(-4, 40, z, 4, 40, z, "minecraft:spruce_planks")
for x in (-3, 3):
    area.set(x, 39, -17, "minecraft:spruce_stairs[facing=east,half=top]" if x < 0 else "minecraft:spruce_stairs[facing=west,half=top]")
for x in (-2, 2):
    area.set(x, 39, -17, "minecraft:lantern[hanging=true]")
for x in range(-3, 4):
    area.set(x, 39, -17, area.get(x, 39, -17) or "minecraft:air") if False else None
area.fill(-1, 39, -17, 1, 39, -17, "minecraft:chain[axis=y]") if False else None
sign(-5, 37, -18, 8, "Deep Caverns", "Mining V")
for z in range(-29, -26):
    for x in range(-1, 2):
        area.set(x, 35, z, "minecraft:gold_block" if (x + z) % 2 else "minecraft:iron_block")
for (x, z) in ((-2, -30), (2, -30), (-2, -26), (2, -26)):
    area.fill(x, 36, z, x, 38, z, "minecraft:stripped_spruce_log[axis=y]")
    area.set(x, 39, z, "minecraft:lantern[hanging=true]")
for x in (-1, 0, 1):
    area.set(x, 39, -31, "minecraft:chain[axis=y]")
    area.set(x, 38, -31, "minecraft:chain[axis=y]")
area.set(0, 36, -28, "minecraft:heavy_weighted_pressure_plate")
for x in (-3, 3):
    area.fill(x, 36, -29, x, 37, -29, "minecraft:iron_bars")
    area.fill(x, 36, -27, x, 37, -27, "minecraft:iron_bars")
area.pad("the Deep Caverns", -1, 36, -29, 1, 37, -27, warp="deep", skill="mining", level=5)
area.set(0, 36, -31, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- camp helpers
def walls(x1, z1, x2, z2, y1, y2, block):
    for y in range(y1, y2 + 1):
        for x in range(x1, x2 + 1):
            area.set(x, y, z1, block)
            area.set(x, y, z2, block)
        for z in range(z1, z2 + 1):
            area.set(x1, y, z, block)
            area.set(x2, y, z, block)


def clear_box(x1, z1, x2, z2, y1, y2):
    area.fill(x1, y1, z1, x2, y2, z2, "minecraft:air")


def gable(x1, z1, x2, z2, yb, stair, plank, gable_block):
    """Roof with the ridge along x, the eaves one block out."""
    j = 0
    while True:
        lo, hi = z1 - 1 + j, z2 + 1 - j
        y = yb + j
        if lo > hi:
            break
        for x in range(x1 - 1, x2 + 2):
            if lo == hi:
                area.set(x, y, lo, plank)
            else:
                area.set(x, y, lo, f"minecraft:{stair}[facing=south,half=bottom]")
                area.set(x, y, hi, f"minecraft:{stair}[facing=north,half=bottom]")
                for z in range(lo + 1, hi):
                    if z > lo and z < hi and j > 0:
                        pass
        for x in (x1, x2):
            for z in range(lo + 1, hi):
                area.set(x, y, z, gable_block)
        if hi - lo <= 1:
            for x in range(x1 - 1, x2 + 2):
                area.set(x, y + 1, lo, plank.replace("planks", "slab") + "[type=bottom]" if False else plank)
            return y + 1
        j += 1
    return yb + j


def tent(x1, z1, x2, z2, wool_a, wool_b):
    """An A-frame tent of wool, open at both ends along x."""
    j = 0
    while z1 + j <= z2 - j:
        y = G + 1 + j
        for x in range(x1, x2 + 1):
            w = wool_a if (x - x1) % 2 == 0 else wool_b
            area.set(x, y, z1 + j, f"minecraft:{w}")
            area.set(x, y, z2 - j, f"minecraft:{w}")
        j += 1
    return G + j


# the camp is a gravel yard; lanes in gravel/cobble
LANE = area.mix({"gravel": 3, "cobblestone": 2, "andesite": 2, "coarse_dirt": 1})
area.path([(0, 100), (0, 66), (0, 37)], width=5, blocks=LANE)
area.path([(0, 76), (-22, 76), (-28, 70)], width=3, blocks=LANE)
area.path([(0, 76), (22, 76), (24, 66)], width=3, blocks=LANE)
area.path([(6, 66), (28, 42)], width=3, blocks=LANE)

# ---- the gold-trimmed hall (Gold Forger)
HX1, HX2, HZ1, HZ2 = -38, -20, 52, 68
for x in range(HX1 - 1, HX2 + 2):
    for z in range(HZ1 - 1, HZ2 + 2):
        area.set(x, G, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:smooth_stone")
clear_box(HX1, HZ1, HX2, HZ2, G + 1, G + 12)
walls(HX1, HZ1, HX2, HZ2, G + 1, G + 5, "minecraft:stone_bricks")
for x in range(HX1, HX2 + 1):
    for z in range(HZ1, HZ2 + 1):
        if x in (HX1, HX2) or z in (HZ1, HZ2):
            if (x - HX1) % 4 == 0 and z in (HZ1, HZ2) or (z - HZ1) % 4 == 0 and x in (HX1, HX2):
                area.fill(x, G + 1, z, x, G + 5, z, "minecraft:gold_block")
            else:
                area.set(x, G + 5, z, "minecraft:chiseled_stone_bricks" if (x + z) % 2 == 0 else "minecraft:stone_bricks")
                area.set(x, G + 1, z, "minecraft:polished_andesite")
                if (x + z) % 4 == 1 and (x in (HX1, HX2) or z in (HZ1, HZ2)):
                    area.set(x, G + 3, z, "minecraft:glass_pane")
                    area.set(x, G + 2, z, "minecraft:glass_pane")
# the doorway (south, 3 wide, 3 tall)
for x in (-30, -29, -28):
    for y in range(G + 1, G + 4):
        area.set(x, y, HZ2, "minecraft:air")
    area.set(x, G + 4, HZ2, "minecraft:gold_block")
hall_peak = gable(HX1, HZ1, HX2, HZ2, G + 6, "spruce_stairs", "minecraft:spruce_planks", "minecraft:stone_bricks")
for x in range(HX1 - 1, HX2 + 2, 2):
    area.set(x, hall_peak, (HZ1 + HZ2) // 2, "minecraft:gold_block")
# interior: a long gold-topped counter, anvils, a vault of gold blocks, lanterns
for x in range(-34, -23):
    area.set(x, G + 1, 60, "minecraft:polished_andesite")
    area.set(x, G + 2, 60, "minecraft:gold_block" if x % 2 else "minecraft:raw_gold_block")
for x in (-36, -22):
    for z in (54, 66):
        area.fill(x, G + 1, z, x, G + 2, z, "minecraft:gold_block")
for (x, z) in ((-36, 56), (-36, 58), (-22, 56), (-22, 58)):
    area.set(x, G + 1, z, "minecraft:anvil[facing=east]")
for (x, z) in ((-35, 53), (-34, 53), (-33, 53), (-23, 53), (-24, 53)):
    area.set(x, G + 1, z, "minecraft:blast_furnace[facing=south,lit=false]")
for (x, z) in ((-27, 53), (-29, 53)):
    area.fill(x, G + 1, z, x, G + 2, z, "minecraft:gold_block")
area.set(-28, G + 1, 53, "minecraft:chest[facing=south]")
for (x, z) in ((-33, 63), (-23, 63), (-33, 57), (-23, 57)):
    area.set(x, G + 5, z, "minecraft:chain[axis=y]")
    area.set(x, G + 4, z, "minecraft:lantern[hanging=true]")
for x in range(-34, -23):
    area.set(x, G + 1, 66, "minecraft:red_carpet" if x % 2 else "minecraft:yellow_carpet")

# ---- the Iron Forger's smithy
IX1, IX2, IZ1, IZ2 = 16, 30, 52, 64
for x in range(IX1 - 1, IX2 + 2):
    for z in range(IZ1 - 1, IZ2 + 2):
        area.set(x, G, z, "minecraft:polished_andesite" if (x * 3 + z) % 3 else "minecraft:smooth_stone")
clear_box(IX1, IZ1, IX2, IZ2, G + 1, G + 12)
walls(IX1, IZ1, IX2, IZ2, G + 1, G + 4, "minecraft:cobblestone")
for x in range(IX1, IX2 + 1):
    for z in range(IZ1, IZ2 + 1):
        if x in (IX1, IX2) or z in (IZ1, IZ2):
            if (x - IX1) % 5 == 0 and z in (IZ1, IZ2) or (z - IZ1) % 4 == 0 and x in (IX1, IX2):
                area.fill(x, G + 1, z, x, G + 4, z, "minecraft:stripped_oak_log[axis=y]")
            elif (x + z) % 4 == 2:
                area.set(x, G + 3, z, "minecraft:iron_bars")
            elif x not in (IX1, IX2) and z == IZ1 and 20 <= x <= 26:
                pass
# open the front (south) wide: a forge porch
for x in range(21, 27):
    for y in range(G + 1, G + 4):
        area.set(x, y, IZ2, "minecraft:air")
area.fill(21, G + 4, IZ2, 26, G + 4, IZ2, "minecraft:spruce_log[axis=x]")
smithy_peak = gable(IX1, IZ1, IX2, IZ2, G + 5, "stone_brick_stairs", "minecraft:stone_bricks", "minecraft:cobblestone")
# chimney
area.fill(19, G + 5, 54, 20, G + 17, 55, "minecraft:stone_bricks")
area.fill(19, G + 17, 54, 20, G + 17, 55, "minecraft:stone_brick_slab[type=bottom]")
for y in range(G + 1, G + 7):
    area.set(19, y, 54, "minecraft:stone_bricks")
area.set(19, G + 18, 54, "minecraft:campfire[lit=true]")
area.set(20, G + 1, 55, "minecraft:blast_furnace[facing=south,lit=false]")
# forge row along the back wall: blast furnaces, anvils, lava cauldrons, grindstones
for x, blk in ((22, "blast_furnace[facing=south,lit=false]"), (23, "blast_furnace[facing=south,lit=false]"),
               (24, "lava_cauldron"), (25, "anvil[facing=south]"), (26, "lava_cauldron"), (27, "blast_furnace[facing=south,lit=false]"),
               (28, "grindstone[face=floor,facing=south]")):
    area.set(x, G + 1, 53, "minecraft:" + blk)
for x in range(21, 29):
    area.set(x, G + 2, 53, "minecraft:iron_block" if x % 2 else "minecraft:stone_bricks")
area.set(18, G + 1, 60, "minecraft:anvil[facing=north]")
area.set(18, G + 1, 58, "minecraft:smithing_table")
area.set(28, G + 1, 60, "minecraft:anvil[facing=east]")
for x in range(21, 27):
    area.set(x, G + 1, 58, "minecraft:iron_block" if x % 2 else "minecraft:polished_andesite")
    area.set(x, G + 2, 58, "minecraft:iron_bars") if False else None
for (x, z) in ((18, 55), (28, 56), (18, 62), (28, 62)):
    area.set(x, G + 4, z, "minecraft:chain[axis=y]") if False else None
    area.set(x, G + 3, z, "minecraft:lantern[hanging=false]") if False else None
for (x, z) in ((20, 62), (27, 62)):
    area.set(x, G + 1, z, "minecraft:barrel[facing=up]")
    area.set(x, G + 2, z, "minecraft:raw_iron_block")
# iron pile outside
for (dx, dz, k) in ((0, 0, 3), (1, 0, 2), (0, 1, 2), (-1, 0, 1)):
    area.fill(33 + dx, G + 1, 58 + dz, 33 + dx, G + k, 58 + dz, "minecraft:raw_iron_block")
area.set(33, G + 4, 58, "minecraft:iron_block")
area.set(34, G + 3, 58, "minecraft:lantern[hanging=false]") if False else None

# ---- Rusty's tent and stall
tent(-14, 70, -4, 76, "orange_wool", "white_wool")
clear_box(-13, 71, -5, 75, G + 1, G + 2)
for z in (70, 76):
    pass
for x in (-14, -4):
    area.fill(x, G + 1, 70, x, G + 3, 70, "minecraft:oak_log[axis=y]") if False else None
for x in range(-13, -4):
    area.set(x, G + 1, 72, "minecraft:hay_block") if x in (-12, -11) else None
area.set(-6, G + 1, 73, "minecraft:barrel[facing=up]")
area.set(-6, G + 1, 74, "minecraft:chest[facing=west]")
area.set(-12, G + 1, 74, "minecraft:anvil[facing=east]")
area.set(-9, G + 3, 73, "minecraft:lantern[hanging=true]")
for x in range(-13, -4):
    area.set(x, G + 3, 73, area.get(x, G + 3, 73) or "minecraft:air")
# stall frontage
for x in range(-14, -3):
    area.set(x, G + 1, 78, "minecraft:spruce_fence") if x % 2 else area.set(x, G + 1, 78, "minecraft:barrel[facing=up]")
area.fill(-14, G + 2, 78, -14, G + 3, 78, "minecraft:spruce_fence")
area.fill(-4, G + 2, 78, -4, G + 3, 78, "minecraft:spruce_fence")
area.set(-14, G + 4, 78, "minecraft:lantern[hanging=false]")
area.set(-4, G + 4, 78, "minecraft:lantern[hanging=false]")

# ---- Lazy Miner's lean-to by the pit rim
tent(26, 38, 34, 44, "brown_wool", "light_gray_wool")
clear_box(27, 39, 33, 43, G + 1, G + 2)
for x in (28, 29, 30):
    area.set(x, G + 1, 41, "minecraft:hay_block")
    area.set(x, G + 2, 41, "minecraft:red_carpet")
area.set(32, G + 1, 42, "minecraft:barrel[facing=up]")
area.set(32, G + 1, 40, "minecraft:lantern[hanging=false]")
area.set(27, G + 1, 42, "minecraft:oak_log[axis=x]")
area.set(27, G + 1, 40, "minecraft:oak_log[axis=x]")

# ---- camp dressing: ore carts' track, lantern posts, piles, a signpost
for z in range(40, 66):
    area.set(6, G + 1 if False else G + 1, z, "minecraft:air") if False else None
for (x, z) in ((-6, 92), (6, 92), (-6, 84), (6, 84), (-10, 66), (12, 66), (-4, 48), (6, 46), (-16, 44), (-42, 62), (36, 62),
               (-12, 100), (12, 100)):
    if area.surface_y(x, z) == G:
        area.fill(x, G + 1, z, x, G + 3, z, "minecraft:spruce_fence")
        area.set(x, G + 4, z, "minecraft:lantern[hanging=false]")
for (x, z, k) in ((-34, 46, 3), (-35, 46, 2), (-34, 47, 2), (36, 46, 3), (37, 46, 2), (-18, 80, 2), (-19, 80, 1)):
    area.fill(x, G + 1, z, x, G + k, z, "minecraft:raw_gold_block" if x < 0 else "minecraft:raw_iron_block")
# a minecart track from the camp to the pit rim
for z in range(40, 72):
    area.set(-6, G + 1, z, "minecraft:rail[shape=north_south]") if area.surface_y(-6, z) == G else None
area.set(-6, G + 1, 39, "minecraft:stone_brick_wall")
area.set(-6, G + 2, 39, "minecraft:lantern[hanging=false]")
# sunken pit rim fence with gaps at the ramps
for deg in range(0, 360, 3):
    a = math.radians(deg)
    x = int(round(PCX + RX * 1.1 * math.cos(a)))
    z = int(round(PCZ + RZ * 1.1 * math.sin(a)))
    if (x, z) in cols and area.surface_y(x, z) == G and not near_ramp(x, z, 3) and z < 34 and area.get(x, G + 1, z) in (None, "minecraft:air"):
        area.set(x, G + 1, z, "minecraft:spruce_fence")
        if deg % 12 == 0:
            area.set(x, G + 2, z, "minecraft:lantern[hanging=false]")

# ---------------------------------------------------------------- the launch pad back to the Hub, the arrival
for x in range(7, 12):
    for z in range(92, 97):
        area.set(x, G, z, "minecraft:stone_bricks" if (x in (7, 11) or z in (92, 96)) else "minecraft:slime_block")
        for y in range(G + 1, G + 5):
            area.set(x, y, z, "minecraft:air")
for x in range(8, 11):
    for z in range(93, 96):
        area.set(x, G, z, "minecraft:slime_block")
area.set(9, G + 1, 94, "minecraft:oak_pressure_plate")
for (x, z) in ((7, 92), (11, 92), (7, 96), (11, 96)):
    area.fill(x, G + 1, z, x, G + 2, z, "minecraft:stone_brick_wall")
    area.set(x, G + 3, z, "minecraft:lantern[hanging=false]")
sign(9, G + 1, 97, 0, "the Hub", "Hub launch pad")
sign(5, G + 1, 94, 8, "Gold Mine", "Mining I")
area.pad("the Hub", 8, 65, 93, 10, 66, 95, destination=("skyrpg_hub:hub", (-21.5, 72.0, -183.5), -90.0))
area.arrival(0, 65, 95, 180)
area.warp("Gold Mine", 0, 65, 95, 180)
# a gold-trimmed gate arch over the arrival
for x in (-4, 4):
    area.fill(x, G + 1, 99, x, G + 5, 99, "minecraft:stripped_spruce_log[axis=y]")
    area.set(x, G + 6, 99, "minecraft:gold_block")
area.fill(-4, G + 6, 99, 4, G + 6, 99, "minecraft:spruce_log[axis=x]")
for x in range(-3, 4):
    area.set(x, G + 7, 99, "minecraft:gold_block" if x % 2 == 0 else "minecraft:spruce_slab[type=bottom]")
for x in (-2, 2):
    area.set(x, G + 5, 99, "minecraft:lantern[hanging=true]")

# ---------------------------------------------------------------- NPC spots, Fairy Souls
area.npc("Rusty", -9, G + 1, 74, 0.0, "tent stall by the arrival lane")
area.npc("Iron Forger", 24, G + 1, 56, 0.0, "shop: Iron Forger, the smithy forge floor")
area.npc("Gold Forger", -28, G + 1, 58, 0.0, "shop: Gold Forger, behind the counter in the gold hall")
area.npc("Lazy Miner", 30, G + 1, 40, 90.0, "by the pit rim lean-to, napping")

area.fairy_soul(-29, hall_peak + 1, (HZ1 + HZ2) // 2)       # the gold hall's ridge
area.fairy_soul(8, 51, -66)                                 # sealed pocket in the north: mine through the stone
area.fairy_soul(-4, 45, -5)                                 # the headframe's top platform (climb the ladder)
area.fairy_soul(67, 45, 25)                                 # a ledge niche in the east cavern
# jump-up stones to the east ledge
for (x, y, z) in ((64, 41, 25), (65, 42, 25)):
    area.set(x, y, z, "minecraft:cobblestone")
area.set(66, 43, 25, "minecraft:cobblestone")

# ---------------------------------------------------------------- the zone
area.zone("Gold Mine", box=((-118, 12, -118), (118, 100, 118)), blocks={
    "minecraft:stone": {"becomes": "minecraft:bedrock", "regrow": 4},
    "minecraft:coal_ore": {"becomes": "minecraft:bedrock", "regrow": 6},
    "minecraft:iron_ore": {"becomes": "minecraft:bedrock", "regrow": 7},
    "minecraft:gold_ore": {"becomes": "minecraft:bedrock", "regrow": 8}})
area.spots["notes"].append(f"Pit centre (0,-8): terraces y60/55/50/45/40 around the core floor y35; ramps S x0, E z-14, W z2, N x10; "
                           f"Deep Caverns shaft entrance (0,35,-17) south to lift (0,36,-28); scaffolds placed {placed}")
print("arrival ground:", area.surface_y(0, 95), "pad ground:", area.surface_y(9, 94), "scaffolds", placed)
area.save()
