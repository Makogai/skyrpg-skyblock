#!/usr/bin/env python3
"""The End (Hypixel's End island, its own dimension with the vanilla End's black sky): floating end stone islands with
obsidian spikes, chorus plants and purpur ruins. The Dragon's Nest (the boss bowl, altar of 8 end portal frames exactly at
0,0) in the middle, the arrival platform south of it, the main End island (Endermen, the Zealot Bruiser Hideout in a hill
and cavern at its east end) south-east, and the Void Sepulture (dark island) west.

    python tools/builds/hub_areas/the_end.py
"""
import math
from kit import Area, hash01, noise

G = 66                       # surface; players stand at 67
AX, AZ = 0, 0                # the altar (the dragon circles this point and lands on the highest block here)
FLR = G - 8                  # the arena floor
DY = FLR + 3                 # the dais top (the highest block at 0,0)
FL = 38                      # the Zealot Bruiser Hideout's floor
area = Area("the_end", (-150, 10, -150), (300, 150, 300), version=1,
            dimension="skyrpg_skyblock:the_end", island="The End", shell=2)

END_BODY = area.mix({"end_stone_bricks": 8, "purpur_block": 1})
DARK_BODY = area.mix({"blackstone": 5, "polished_blackstone_bricks": 2, "deepslate": 2, "cobbled_deepslate": 1})
END_TOP = area.mix({"end_stone": 1})


# ---------------------------------------------------------------- helpers
def sign(x, y, z, rot, lines):
    msgs = ['""'] + ['"%s"' % t.replace("'", "\\'") for t in lines] + ['""'] * 3
    area.set(x, y, z, f"dark_oak_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": msgs[:4], "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def fix_island(wall, tuff_to, deep_to, deep_alt=None):
    """The kit makes dripstone, hanging roots, deepslate and tuff: turn them into end/dark materials."""
    cells = area.piece.cells
    for pos, (entry, data) in list(cells.items()):
        i = entry["id"]
        if i == "minecraft:pointed_dripstone":
            area.piece.set(*pos, wall)
        elif i == "minecraft:hanging_roots":
            del cells[pos]
        elif i == "minecraft:tuff" and tuff_to:
            area.piece.set(*pos, tuff_to)
        elif i == "minecraft:deepslate" and deep_to:
            alt = deep_alt and hash01(pos[0], pos[1], pos[2], 4) < 0.18
            area.piece.set(*pos, deep_alt if alt else deep_to)


def level(x1, z1, x2, z2, y, block, under="minecraft:end_stone_bricks"):
    """Levels a rectangle of an island to surface y."""
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for z in range(min(z1, z2), max(z1, z2) + 1):
            old = area.top.get((x, z))
            if old is None:
                continue
            for yy in range(y + 1, old + 8):
                if area.get(x, yy, z) not in (None, "minecraft:air"):
                    area.set(x, yy, z, "minecraft:air")
            for yy in range(y - 3, y):
                if area.get(x, yy, z) in (None, "minecraft:air"):
                    area.set(x, yy, z, under)
            area.set(x, y, z, block)
            area.top[(x, z)] = y


def on_island(x, z):
    return (x, z) in area.top


def causeway(axis, c, a, b, y, deck, edge, lamp="end_rod[facing=up]"):
    """A 5 wide bridge. axis 'z': runs along z at x=c from a to b; axis 'x': along x at z=c."""
    lo, hi = min(a, b), max(a, b)
    for t in range(lo, hi + 1):
        for s in range(-2, 3):
            x, z = (c + s, t) if axis == "z" else (t, c + s)
            area.set(x, y, z, edge if abs(s) == 2 else deck(x, y, z))
            for yy in range(y + 1, y + 5):
                area.set(x, yy, z, "minecraft:air")
            if abs(s) <= 1:
                area.set(x, y - 1, z, "minecraft:end_stone_bricks" if hash01(x, 0, z, 3) < 0.8 else "minecraft:purpur_block")
            if abs(s) == 0 and t % 3 == 0:
                area.set(x, y - 2, z, "minecraft:end_stone_bricks")
            if abs(s) == 2 and (t - lo) % 8 == 4:
                area.set(x, y + 1, z, "minecraft:purpur_pillar[axis=y]")
                area.set(x, y + 2, z, "minecraft:purpur_pillar[axis=y]")
                area.set(x, y + 3, z, lamp)
    return lo, hi


DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def chorus(x, z, seed):
    y0 = area.top.get((x, z))
    if y0 is None or area.get(x, y0, z) != "minecraft:end_stone" or area.get(x, y0 + 1, z) not in (None, "minecraft:air"):
        return False
    plants, flowers = {}, []

    def add(pos):
        plants.setdefault(pos, set())

    def link(a, d):
        b = (a[0] + DIRS[d][0], a[1] + DIRS[d][1], a[2] + DIRS[d][2])
        add(a); add(b)
        plants[a].add(d); plants[b].add(OPP[d])
        return b

    h = 3 + int(hash01(x, 1, z, seed) * 4)
    base = y0 + 1
    add((x, base, z))
    plants[(x, base, z)].add("down")
    cur = (x, base, z)
    for _ in range(h - 1):
        cur = link(cur, "up")
    flowers.append(cur)
    for k in range(1 + int(hash01(x, 2, z, seed) * 3)):
        lv = base + 1 + int(hash01(x, 3 + k, z, seed) * (h - 1))
        d = ["north", "south", "east", "west"][int(hash01(x, 7 + k, z, seed) * 4) % 4]
        cur = (x, lv, z)
        ok = True
        for _ in range(1 + int(hash01(x, 11 + k, z, seed) * 2)):
            nxt = (cur[0] + DIRS[d][0], cur[1], cur[2] + DIRS[d][2])
            if nxt in plants or area.get(*nxt) not in (None, "minecraft:air"):
                ok = False
                break
            cur = link(cur, d)
        if not ok and cur == (x, lv, z):
            continue
        for _ in range(1 + int(hash01(x, 15 + k, z, seed) * 2)):
            nxt = (cur[0], cur[1] + 1, cur[2])
            if nxt in plants or area.get(*nxt) not in (None, "minecraft:air"):
                break
            cur = link(cur, "up")
        flowers.append(cur)
    for pos, conns in plants.items():
        props = ",".join(f"{d}={'true' if d in conns else 'false'}" for d in ("down", "east", "north", "south", "up", "west"))
        area.set(*pos, f"chorus_plant[{props}]")
    for f in flowers:
        top = (f[0], f[1] + 1, f[2])
        if area.get(*top) in (None, "minecraft:air"):
            area.set(*f, "chorus_plant[" + ",".join(f"{d}={'true' if d in plants[f] or d == 'up' else 'false'}"
                                                   for d in ("down", "east", "north", "south", "up", "west")) + "]")
            area.set(*top, "chorus_flower[age=5]")
    return True


def spike(x, z, height, radius, seed=0):
    y0 = area.top.get((x, z))
    if y0 is None:
        return
    for h in range(1, height + 1):
        t = h / height
        r = max(0.6, radius * (1 - 0.55 * t))
        for dx in range(-int(r) - 1, int(r) + 2):
            for dz in range(-int(r) - 1, int(r) + 2):
                if dx * dx + dz * dz <= r * r + 0.3:
                    area.set(x + dx, y0 + h, z + dz, "minecraft:obsidian")
    # base skirt down into the island so it never floats on a slope
    for dx in range(-radius, radius + 1):
        for dz in range(-radius, radius + 1):
            if dx * dx + dz * dz <= radius * radius + 0.3:
                yy = area.top.get((x + dx, z + dz))
                if yy is not None:
                    for y in range(yy + 1, y0 + 1):
                        area.set(x + dx, y, z + dz, "minecraft:obsidian")
    if height >= 18:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if abs(dx) + abs(dz) == 2:
                    continue
        area.set(x, y0 + height + 1, z, "minecraft:end_rod[facing=up]")


def ruin_tower(cx, cz, w=5, h=10, seed=0):
    y0 = area.top.get((cx, cz))
    if y0 is None:
        return
    half = w // 2
    for dx in range(-half, half + 1):
        for dz in range(-half, half + 1):
            yy = area.top.get((cx + dx, cz + dz), y0)
            for y in range(yy + 1, y0 + 1):
                area.set(cx + dx, y, cz + dz, "minecraft:end_stone_bricks")
    for dy in range(1, h + 1):
        for dx in range(-half, half + 1):
            for dz in range(-half, half + 1):
                edge = abs(dx) == half or abs(dz) == half
                if not edge:
                    continue
                corner = abs(dx) == half and abs(dz) == half
                broken = dy > h * 0.45 and hash01(cx + dx, y0 + dy, cz + dz, seed) < 0.55 * (dy / h) and not (corner and dy < h * 0.8)
                door = dz == half and dx == 0 and dy <= 2
                window = dy in (4, 5) and not corner and (dx == 0 or dz == 0) and not door
                if broken or door or window:
                    continue
                area.set(cx + dx, y0 + dy, cz + dz, "minecraft:purpur_pillar[axis=y]" if corner else
                         "minecraft:end_stone_bricks" if hash01(cx + dx, y0 + dy, cz + dz, 1) < 0.7 else "minecraft:purpur_block")
    area.set(cx, y0 + 1, cz, "minecraft:end_rod[facing=up]")


def ruin_wall(cx, cz, length, axis, seed=0):
    for i in range(length):
        x, z = (cx + i, cz) if axis == "x" else (cx, cz + i)
        y0 = area.top.get((x, z))
        if y0 is None:
            continue
        hh = 1 + int(hash01(x, 5, z, seed) * 4)
        for y in range(1, hh + 1):
            area.set(x, y0 + y, z, "minecraft:end_stone_brick_wall" if y == hh and hh > 1 else
                     "minecraft:purpur_block" if y == 1 else "minecraft:end_stone_bricks")


def ruin_arch(cx, cz, axis, seed=0):
    y0 = area.top.get((cx, cz))
    if y0 is None:
        return
    for s in (-2, 2):
        x, z = (cx + s, cz) if axis == "x" else (cx, cz + s)
        for y in range(1, 6):
            area.set(x, y0 + y, z, "minecraft:purpur_pillar[axis=y]")
    for s in range(-2, 3):
        x, z = (cx + s, cz) if axis == "x" else (cx, cz + s)
        if hash01(x, 9, z, seed) < 0.85:
            area.set(x, y0 + 6, z, "minecraft:purpur_slab[type=bottom]" if abs(s) == 2 else "minecraft:end_stone_bricks")
    area.set(cx, y0 + 5, cz, "minecraft:end_rod[facing=down]")


def stall(x0, z0, deco):
    """An open stall x0..x0+5, z0..z0+4: posts, purpur slab roof, a counter at z0+3 and the NPC behind it (z0+1)."""
    for x in range(x0, x0 + 6):
        for z in range(z0, z0 + 5):
            area.set(x, G, z, "minecraft:polished_blackstone_bricks")
            area.set(x, G + 5, z, "minecraft:purpur_slab[type=bottom]")
    for x in (x0, x0 + 5):
        for z in (z0, z0 + 4):
            area.fill(x, G + 1, z, x, G + 4, z, "minecraft:purpur_pillar[axis=y]")
    for x in range(x0 + 1, x0 + 5):
        area.set(x, G + 1, z0 + 3, "minecraft:end_stone_brick_slab[type=top]")
    area.set(x0 + 1, G + 1, z0 + 3, "minecraft:barrel[facing=south]")
    area.set(x0 + 4, G + 1, z0 + 3, "minecraft:barrel[facing=south]")
    area.set(x0 + 1, G + 1, z0 + 1, "minecraft:barrel[facing=up]")
    area.set(x0 + 4, G + 1, z0 + 1, "minecraft:chest[facing=south]")
    for x in range(x0, x0 + 6):
        area.set(x, G + 3, z0, "minecraft:purpur_block")
        area.set(x, G + 4, z0, "minecraft:purpur_block")
    area.set(x0 + 2, G + 4, z0 + 2, "minecraft:end_rod[facing=down]")
    area.set(x0 + 3, G + 4, z0 + 2, "minecraft:end_rod[facing=down]")
    for x in (x0 + 1, x0 + 2, x0 + 3, x0 + 4):
        area.set(x, G + 2, z0, "minecraft:air")
    sign(x0 + 2, G + 2, z0 + 3, 0, deco)


# ---------------------------------------------------------------- the islands
# The Dragon's Nest island, centred on 0,0 (the altar is exactly there).
nest = area.island(0, 0, 60, top=G, seed=3, surface=END_TOP, hills=2.0, depth=42, wobble=0.06, dirt="end_stone_bricks",
                   stone=END_BODY)
# The main End island: long and low, south-east of the nest, with the Zealot Bruiser Hideout under its east end.
MCX, MCZ = 78, 104


def main_shape(x, z):
    a, b = 1.0, 0.58
    th = math.atan2(z - MCZ, x - MCX)
    return a * b / math.sqrt((b * math.cos(th)) ** 2 + (a * math.sin(th)) ** 2)


main = area.island(MCX, MCZ, 60, top=G, seed=8, surface=END_TOP, hills=3.0, depth=40, wobble=0.1, dirt="end_stone",
                   stone=END_BODY, shape=main_shape)
# Decorative islets (no zone: nothing there can be dug).
islets = []
for (ix, iz, ir, sd) in ((112, -60, 16, 21), (118, 8, 12, 22), (-36, -112, 12, 23), (46, -118, 10, 24), (-100, -88, 12, 25)):
    islets.append(area.island(ix, iz, ir, top=G + (sd % 3) * 3 - 3, seed=sd, surface=END_TOP, hills=2.0, depth=ir * 1.1, wobble=0.12,
                              dirt="end_stone_bricks", stone=END_BODY))
fix_island("minecraft:end_stone_brick_wall", "minecraft:end_stone_bricks", "minecraft:end_stone_bricks", "minecraft:purpur_block")

# ---------------------------------------------------------------- the arrival platform (south of the nest, west end of the main island)
PCX, PCZ, PR = 0, 122, 16
plat = set()
platform_top = area.mix({"end_stone_bricks": 5, "polished_blackstone": 3, "crying_obsidian": 1, "blackstone": 2})
for x in range(PCX - PR - 1, PCX + PR + 2):
    for z in range(PCZ - PR - 1, PCZ + PR + 2):
        r = math.hypot(x - PCX, z - PCZ)
        if r > PR:
            continue
        plat.add((x, z))
        depth = 3 + int(8 * (1 - (r / PR) ** 2) ** 0.6)
        for y in range(G - depth, G):
            area.set(x, y, z, "minecraft:end_stone_bricks" if hash01(x, y, z, 5) < 0.8 else "minecraft:purpur_block")
        if r >= PR - 1:
            area.set(x, G, z, "minecraft:purpur_block")
        elif 10.5 <= r <= 11.5:
            area.set(x, G, z, "minecraft:chiseled_polished_blackstone" if hash01(x, 1, z, 5) < 0.7 else "minecraft:crying_obsidian")
        else:
            area.set(x, G, z, platform_top(x, G, z, 2))
        area.top[(x, z)] = G
        for y in range(G + 1, G + 8):
            area.set(x, y, z, "minecraft:air")
# the rim: pillars with end rods
for k in range(14):
    ang = k / 14 * math.tau + 0.1
    x, z = int(round(PCX + math.cos(ang) * (PR - 1))), int(round(PCZ + math.sin(ang) * (PR - 1)))
    if z < PCZ - 8 and abs(x) < 5:
        continue
    area.fill(x, G + 1, z, x, G + 2, z, "minecraft:purpur_pillar[axis=y]")
    area.set(x, G + 3, z, "minecraft:end_rod[facing=up]")
area.arrival(0, G + 1, 120, 180.0)
area.warp("The End", 0, G + 1, 120, yaw=180.0)
# the signpost
sign(3, G + 1, 117, 0, ["The End", "Beware the Endermen"])

# stalls: Pearl Dealer (west) and Gregory (east), facing the arrival
stall(-12, 112, ["Pearl Dealer", "Ender Pearls"])
stall(7, 112, ["Gregory", "Odds and ends"])
area.npc("Pearl Dealer", -9, G + 1, 113, yaw=0.0, note="behind the west stall counter near the arrival; shop")
area.npc("Gregory", 10, G + 1, 113, yaw=0.0, note="behind the east stall counter near the arrival; shop")

# launch pad back to the Spider's Den (south of the arrival)
LX, LZ = 0, 131
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "minecraft:slime_block" if abs(dx) < 2 and abs(dz) < 2 else "minecraft:polished_blackstone")
area.set(LX, G + 1, LZ, "minecraft:heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(LX + dx, G + 1, LZ + dz, "minecraft:polished_blackstone_wall")
    area.set(LX + dx, G + 2, LZ + dz, "minecraft:soul_lantern[hanging=false]")
sign(LX + 4, G + 1, LZ - 2, 4, ["Spider's Den", ""])
SPIDER_LANDING = (-79.5, 65.0, 0.5)
area.pad("the Spider's Den", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1,
         destination=("skyrpg_skyblock:spiders_den", SPIDER_LANDING, 90.0))
area.spots["notes"].append(f"Spider's Den launch pad at ({LX}, {G + 1}, {LZ}), box x{LX - 1}..{LX + 1} y{G + 1}..{G + 2} z{LZ - 1}..{LZ + 1}")

# ---------------------------------------------------------------- the Dragon's Nest: bowl, dais, altar
NR = 46         # the bowl's radius at the rim
FLAT = 34       # the arena floor's radius
floor_mix = area.mix({"end_stone_bricks": 5, "polished_blackstone": 3, "blackstone": 2, "chiseled_polished_blackstone": 1, "purpur_block": 1})
for x in range(-NR - 1, NR + 2):
    for z in range(-NR - 1, NR + 2):
        r = math.hypot(x - AX, z - AZ)
        if r > NR or (x, z) not in area.top:
            continue
        if r <= FLAT:
            top = FLR
        else:
            t = (r - FLAT) / (NR - FLAT)
            top = FLR + int(round((G - FLR) * t ** 1.4))
        old = area.top[(x, z)]
        for y in range(top + 1, max(old, G) + 6):
            area.set(x, y, z, "minecraft:air")
        for y in range(top - 3, top):
            if area.get(x, y, z) in (None, "minecraft:air"):
                area.set(x, y, z, "minecraft:end_stone_bricks")
        if r > FLAT:
            area.set(x, top, z, "minecraft:end_stone_bricks" if hash01(x, 2, z, 4) < 0.5 else "minecraft:polished_blackstone" if hash01(x, 3, z, 4) < 0.5 else "minecraft:blackstone")
        else:
            area.set(x, top, z, floor_mix(x, top, z, 6))
        area.top[(x, z)] = top
# inlay rings in the arena floor
for x in range(-FLAT, FLAT + 1):
    for z in range(-FLAT, FLAT + 1):
        r = math.hypot(x, z)
        if abs(r - 17) < 0.6 or abs(r - 29) < 0.5:
            area.set(x, FLR, z, "minecraft:crying_obsidian" if hash01(x, 6, z, 1) < 0.2 else "minecraft:polished_blackstone_bricks")
# the dais: three obsidian layers with stairs on the four sides
LAYERS = ((11, FLR + 1), (9, FLR + 2), (7, FLR + 3))
for x in range(-12, 13):
    for z in range(-12, 13):
        r = math.hypot(x, z)
        for (rad, y) in LAYERS:
            if r <= rad + 0.3:
                for yy in range(FLR + 1, y + 1):
                    area.set(x, yy, z, "minecraft:obsidian" if hash01(x, yy, z, 8) < 0.82 else "minecraft:crying_obsidian")
                area.top[(x, z)] = max(area.top.get((x, z), 0), y)
for (rad, y) in LAYERS:
    for lat in (-1, 0, 1):
        area.set(rad, y, lat, "minecraft:end_stone_brick_stairs[facing=west,half=bottom]")
        area.set(-rad, y, lat, "minecraft:end_stone_brick_stairs[facing=east,half=bottom]")
        area.set(lat, y, rad, "minecraft:end_stone_brick_stairs[facing=north,half=bottom]")
        area.set(lat, y, -rad, "minecraft:end_stone_brick_stairs[facing=south,half=bottom]")
# the small platform in the middle and the ring of frames
for dx in range(-3, 4):
    for dz in range(-3, 4):
        if math.hypot(dx, dz) <= 2.7:
            area.set(dx, DY, dz, "minecraft:chiseled_polished_blackstone" if (abs(dx) + abs(dz)) % 2 == 0 else "minecraft:polished_blackstone_bricks")
area.set(0, DY, 0, "minecraft:crying_obsidian")
FRAMES = [(4, 0, "west"), (3, 3, "north"), (0, 4, "north"), (-3, 3, "north"), (-4, 0, "east"), (-3, -3, "south"), (0, -4, "south"), (3, -3, "south")]
frames = []
for (dx, dz, face) in FRAMES:
    area.set(AX + dx, DY + 1, AZ + dz, f"minecraft:end_portal_frame[eye=false,facing={face}]")
    frames.append([AX + dx, DY + 1, AZ + dz])
area.spots["altar"] = {"center": [AX, DY, AZ], "frames": frames}
area.spots["notes"].append(f"Dragon's Nest altar: centre ({AX}, {DY}, {AZ}) (top of the platform, highest block at 0,0), frames y{DY + 1}: " + ", ".join(f"({x}, {y}, {z})" for x, y, z in frames))
for dx, dz in ((6, 0), (-6, 0), (0, 6), (0, -6)):
    area.fill(dx, DY + 1, dz, dx, DY + 2, dz, "minecraft:purpur_pillar[axis=y]")
    area.set(dx, DY + 3, dz, "minecraft:end_rod[facing=up]")
# obsidian pillars around the bowl (the dragon's perches)
PILLARS = []
for k in range(10):
    ang = k / 10 * math.tau + 0.3
    px, pz = int(round(math.cos(ang) * 40)), int(round(math.sin(ang) * 40))
    height = 22 + int(hash01(k, 1, 0, 14) * 26)
    base = area.top.get((px, pz))
    if base is None:
        continue
    rad = 2
    for dx in range(-rad, rad + 1):
        for dz in range(-rad, rad + 1):
            if dx * dx + dz * dz <= rad * rad + 0.5:
                yy = area.top.get((px + dx, pz + dz), base)
                for y in range(yy + 1, base + height + 1):
                    area.set(px + dx, y, pz + dz, "minecraft:obsidian")
    area.set(px, base + height + 1, pz, "minecraft:end_rod[facing=up]")
    for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        area.set(px + dx, base + height, pz + dz, "minecraft:iron_bars")
    PILLARS.append((px, pz, base + height))
# the rim: purpur posts with end rods, ruined gates and chorus outside
for k in range(24):
    ang = k / 24 * math.tau
    x, z = int(round(math.cos(ang) * (NR + 1))), int(round(math.sin(ang) * (NR + 1)))
    if (x, z) not in area.top:
        continue
    y0 = area.top[(x, z)]
    area.fill(x, y0 + 1, z, x, y0 + 3, z, "minecraft:purpur_pillar[axis=y]")
    area.set(x, y0 + 4, z, "minecraft:end_rod[facing=up]")

# ---------------------------------------------------------------- causeways
nest_south = max(z for (x, z) in nest if x == 0)
deck_end = lambda x, y, z: "minecraft:end_stone_bricks" if hash01(x, 3, z, 2) < 0.75 else "minecraft:polished_blackstone_bricks"
causeway("z", 0, nest_south - 4, PCZ - PR + 3, G, deck_end, "minecraft:purpur_block")
main_west = min(x for (x, z) in main if z == PCZ)
if main_west > PCX + PR - 3:
    causeway("x", PCZ + 4, PCX + PR - 3, main_west + 3, G, deck_end, "minecraft:purpur_block")

# ---------------------------------------------------------------- the Void Sepulture (west, dark)
VX, VZ, VR = -112, 0, 28
sep = area.island(VX, VZ, VR, top=G, seed=31, surface=area.mix({"blackstone": 5, "polished_blackstone": 3, "crying_obsidian": 1, "gilded_blackstone": 1}),
                  hills=2.5, depth=30, wobble=0.08, dirt="blackstone", stone=DARK_BODY)
fix_island("minecraft:blackstone_wall", "minecraft:blackstone", None)
nest_west = min(x for (x, z) in nest if z == 0)
sep_east = max(x for (x, z) in sep if z == 0)
deck_dark = lambda x, y, z: "minecraft:polished_blackstone_bricks" if hash01(x, 3, z, 2) < 0.8 else "minecraft:crying_obsidian"
causeway("x", 0, sep_east - 3, nest_west + 3, G, deck_dark, "minecraft:blackstone", lamp="minecraft:soul_lantern[hanging=false]")
# the ziggurat in the middle, ringed by spires
for x in range(VX - 10, VX + 11):
    for z in range(VZ - 10, VZ + 11):
        d = max(abs(x - VX), abs(z - VZ))
        if d > 9:
            continue
        tier = 0 if d >= 7 else 1 if d >= 5 else 2 if d >= 3 else 3
        for y in range(G + 1, G + 1 + 1 + tier):
            area.set(x, y, z, "minecraft:polished_blackstone_bricks" if hash01(x, y, z, 2) < 0.7 else "minecraft:crying_obsidian")
        area.set(x, G + 1 + tier, z, "minecraft:chiseled_polished_blackstone" if (x + z) % 2 == 0 else "minecraft:polished_blackstone_bricks")
        area.top[(x, z)] = G + 1 + tier
for sx, sz in ((-9, -9), (9, -9), (-9, 9), (9, 9)):
    area.fill(VX + sx, G + 1, VZ + sz, VX + sx, G + 6, VZ + sz, "minecraft:polished_blackstone_bricks")
    area.set(VX + sx, G + 7, VZ + sz, "minecraft:soul_lantern[hanging=false]")
area.set(VX, G + 5, VZ, "minecraft:crying_obsidian")
area.set(VX, G + 6, VZ, "minecraft:soul_lantern[hanging=false]")
for k in range(8):
    ang = k / 8 * math.tau + 0.4
    x, z = int(round(VX + math.cos(ang) * 19)), int(round(VZ + math.sin(ang) * 19))
    if (x, z) not in area.top:
        continue
    y0 = area.top[(x, z)]
    h = 10 + int(hash01(k, 3, 1, 5) * 10)
    for y in range(1, h + 1):
        r = 1 if y < h * 0.6 else 0
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if r == 0 or abs(dx) + abs(dz) < 2:
                    area.set(x + dx, y0 + y, z + dz, "minecraft:crying_obsidian" if y % 4 == 0 else "minecraft:polished_blackstone_bricks")
    area.set(x, y0 + h + 1, z, "minecraft:soul_lantern[hanging=false]")
for k in range(12):
    ang = k / 12 * math.tau + 0.2
    x, z = int(round(VX + math.cos(ang) * 14)), int(round(VZ + math.sin(ang) * 14))
    if (x, z) in area.top and (x, z) not in sep or (x, z) not in area.top:
        continue
    y0 = area.top[(x, z)]
    area.set(x, y0 + 1, z, "minecraft:blackstone_wall")
    area.set(x, y0 + 2, z, "minecraft:soul_lantern[hanging=false]")

# ---------------------------------------------------------------- the main island: the soul tower, huts for the NPCs
def soul_tower(cx, cz):
    """A 7x7 purpur tower with a spiral of steps inside; the Fairy Soul waits on the last step. Returns the soul's spot."""
    level(cx - 4, cz - 4, cx + 4, cz + 4, G, "minecraft:end_stone_bricks")
    ring = [(x, 2) for x in range(0, -3, -1)] + [(-2, z) for z in range(1, -3, -1)] + [(x, -2) for x in range(-1, 3)] + \
           [(2, z) for z in range(-1, 3)] + [(x, 2) for x in range(1, 0, -1)]
    seen, order = set(), []
    for c in ring:
        if c not in seen:
            seen.add(c)
            order.append(c)
    order = order[:16]
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            edge = abs(dx) == 3 or abs(dz) == 3
            for y in range(1, 21):
                if edge:
                    corner = abs(dx) == 3 and abs(dz) == 3
                    door = dz == 3 and dx == 0 and y <= 2
                    slit = y % 6 == 4 and not corner and (dx == 0 or dz == 0) and not door
                    gap = y > 15 and hash01(cx + dx, y, cz + dz, 9) < 0.35
                    if door or slit or gap:
                        continue
                    area.set(cx + dx, G + y, cz + dz, "minecraft:purpur_pillar[axis=y]" if corner else
                             "minecraft:end_stone_bricks" if hash01(cx + dx, y, cz + dz, 3) < 0.7 else "minecraft:purpur_block")
                else:
                    area.set(cx + dx, G + y, cz + dz, "minecraft:air")
    for i, (dx, dz) in enumerate(order):
        for y in range(1, i + 2):
            area.set(cx + dx, G + y, cz + dz, "minecraft:end_stone_bricks" if y <= i else "minecraft:purpur_block")
    dx, dz = order[-1]
    area.set(cx, G + 12, cz, "minecraft:end_rod[facing=down]") if False else None
    for y in (4, 9, 14):
        area.set(cx, G + y, cz, "minecraft:end_rod[facing=up]")
    return (cx + dx, G + len(order) + 1, cz + dz)


TOWER = (128, 122)
soul_a = soul_tower(*TOWER)
area.spots["notes"].append(f"Fairy Soul tower at {TOWER}: spiral steps inside, door on the south side, soul on the top step {soul_a}")


def hut(x0, z0, w, d, door_x, wall="minecraft:purpur_block", roof="minecraft:purpur_slab[type=bottom]"):
    level(x0 - 1, z0 - 1, x0 + w, z0 + d, G, "minecraft:end_stone_bricks")
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + d):
            edge = x in (x0, x0 + w - 1) or z in (z0, z0 + d - 1)
            corner = x in (x0, x0 + w - 1) and z in (z0, z0 + d - 1)
            for y in range(1, 4):
                if corner:
                    area.set(x, G + y, z, "minecraft:purpur_pillar[axis=y]")
                elif edge:
                    door = z == z0 + d - 1 and x == door_x and y <= 2
                    win = y == 2 and ((z in (z0, z0 + d - 1) and (x - x0) % 3 == 1) or (x in (x0, x0 + w - 1) and (z - z0) % 3 == 1))
                    area.set(x, G + y, z, "minecraft:air" if door else "minecraft:magenta_stained_glass_pane" if win else wall)
                else:
                    area.set(x, G + y, z, "minecraft:air")
            area.set(x, G + 4, z, roof)
    area.set(x0 + 1, G + 1, z0 + 1, "minecraft:barrel[facing=up]")
    area.set(x0 + w - 2, G + 1, z0 + 1, "minecraft:crafting_table")
    area.set(x0 + w // 2, G + 3, z0 + d // 2, "minecraft:end_rod[facing=down]")


hut(40, 70, 7, 6, 43)
area.npc("Guber", 43, G + 1, 74, yaw=0.0, note="in the purpur hut on the main island's north shore; the Guber quest NPC")
hut(52, 130, 7, 6, 55)
area.set(57, G + 1, 128, "minecraft:campfire[lit=true,facing=north]")
area.npc("Lone Adventurer", 55, G + 1, 134, yaw=180.0, note="in a small purpur hut on the main island's south shore, by a campfire")
# Tyzzo: a ritual circle of end rods on the east side
TX, TZ = 126, 98
level(TX - 4, TZ - 4, TX + 4, TZ + 4, G, "minecraft:polished_blackstone_bricks")
for k in range(8):
    ang = k / 8 * math.tau
    x, z = int(round(TX + math.cos(ang) * 3.4)), int(round(TZ + math.sin(ang) * 3.4))
    area.fill(x, G + 1, z, x, G + 2, z, "minecraft:purpur_pillar[axis=y]")
    area.set(x, G + 3, z, "minecraft:end_rod[facing=up]")
area.set(TX, G + 1, TZ + 1, "minecraft:lectern[facing=north]")
area.npc("Tyzzo", TX, G + 1, TZ, yaw=90.0, note="inside the pillar ring on the main island's east side")

# ---------------------------------------------------------------- the Zealot Bruiser Hideout (a hill with a cavern and a stair)
HX, HZ, HR = 100, 104, 15
MOUND = 18
for x in range(HX - MOUND, HX + MOUND + 1):
    for z in range(HZ - MOUND, HZ + MOUND + 1):
        r = math.hypot(x - HX, z - HZ)
        if r > MOUND or (x, z) not in area.top:
            continue
        add = int(round(9 * math.cos(r / MOUND * math.pi / 2) ** 2 + 1.5 * noise(x, z, 4, 2.0)))
        old = area.top[(x, z)]
        if add <= 0:
            continue
        for y in range(old, old + add):
            area.set(x, y, z, "minecraft:end_stone_bricks" if y < old + add - 2 else "minecraft:end_stone")
        area.set(x, old + add, z, "minecraft:end_stone")
        area.top[(x, z)] = old + add
# ruins on the hill
ruin_wall(HX - 6, HZ - 4, 6, "x", seed=3)
ruin_wall(HX + 3, HZ + 5, 5, "x", seed=4)
# the cavern
for x in range(HX - HR - 1, HX + HR + 2):
    for z in range(HZ - HR - 1, HZ + HR + 2):
        r = math.hypot(x - HX, z - HZ)
        if r > HR:
            continue
        ceil = FL + 1 + int(13 * math.sqrt(max(0.0, 1 - (r / HR) ** 2))) + int(1.5 * noise(x, z, 6, 3))
        ceil = min(ceil, 52)
        area.set(x, FL, z, floor_mix(x, FL, z, 9))
        for y in range(FL + 1, max(ceil, FL + 2) + 1):
            area.set(x, y, z, "minecraft:air")
        for y in range(max(ceil, FL + 2) + 1, max(ceil, FL + 2) + 3):
            if area.get(x, y, z) in (None, "minecraft:air"):
                area.set(x, y, z, "minecraft:end_stone_bricks")
        if r > HR - 1.5:
            for y in range(FL + 1, FL + 3):
                if hash01(x, y, z, 2) < 0.5:
                    area.set(x, y, z, "minecraft:purpur_block")
# pillars and lamps
for k in range(8):
    ang = k / 8 * math.tau + 0.2
    x, z = int(round(HX + math.cos(ang) * 9)), int(round(HZ + math.sin(ang) * 9))
    y = FL + 1
    while area.get(x, y, z) in (None, "minecraft:air") and y < FL + 16:
        area.set(x, y, z, "minecraft:purpur_pillar[axis=y]" if y % 5 else "minecraft:chiseled_purpur" if False else "minecraft:purpur_pillar[axis=y]")
        y += 1
    area.set(x, FL + 1, z, "minecraft:polished_blackstone_bricks")
    area.set(x + 1, FL + 1, z, "minecraft:end_rod[facing=up]")
for k in range(10):
    ang = k / 10 * math.tau + 0.5
    x, z = int(round(HX + math.cos(ang) * 5)), int(round(HZ + math.sin(ang) * 5))
    y = FL + 1
    while area.get(x, y, z) in (None, "minecraft:air") and y < FL + 16:
        y += 1
    area.set(x, y - 1, z, "minecraft:end_rod[facing=down]")
# a throne dais on the east side (the "fortress" corner) and a hidden alcove for a Fairy Soul
for dx in range(-3, 4):
    for dz in range(-2, 3):
        area.set(HX + 9 + dx // 2 + 1, FL + 1, HZ + dz, "minecraft:polished_blackstone_bricks")
ALX, ALZ = HX, HZ - 14
for dx in (-1, 0, 1):
    for y in range(FL + 1, FL + 4):
        area.set(ALX + dx, y, ALZ, "minecraft:air")
        area.set(ALX + dx, y, ALZ - 1, "minecraft:air")
    area.set(ALX + dx, FL, ALZ - 1, "minecraft:polished_blackstone_bricks")
area.set(ALX, FL + 3, ALZ - 1, "minecraft:end_rod[facing=down]")
soul_d = (ALX, FL + 1, ALZ - 1)
# the stair from the surface (entrance on the west end of the mound)
SX0, SZ0 = 66, HZ
steps = G + 1 - (FL + 1) + 1
area.stairs_down(SX0, G + 1, SZ0, steps, "east", block="polished_blackstone_brick_stairs", width=5, solid="polished_blackstone_bricks")
for i in range(steps):
    x = SX0 + i
    for dz in range(-2, 3):
        for y in range(FL + 1, G + 1 - i - 1):
            if area.get(x, y, SZ0 + dz) in (None, "minecraft:air") and math.hypot(x - HX, SZ0 + dz - HZ) < HR + 1:
                area.set(x, y, SZ0 + dz, "minecraft:polished_blackstone_bricks")
# the gate over the stair's top
for dz in (-3, 3):
    area.fill(SX0 - 1, G + 1, SZ0 + dz, SX0 - 1, G + 6, SZ0 + dz, "minecraft:polished_blackstone_bricks")
    area.set(SX0 - 1, G + 7, SZ0 + dz, "minecraft:soul_lantern[hanging=false]")
area.fill(SX0 - 1, G + 7, SZ0 - 2, SX0 - 1, G + 7, SZ0 + 2, "minecraft:polished_blackstone_brick_slab[type=bottom]")
sign(SX0 - 3, G + 1, SZ0 - 4, 4, ["Zealot Bruiser", "Hideout"])
area.spots["notes"].append(f"Zealot Bruiser Hideout: stair from ({SX0}, {G + 1}, {SZ0}) heading east down to the cavern centre ({HX}, {FL + 1}, {HZ}), radius {HR}, floor y{FL}")

# ---------------------------------------------------------------- scatter: spikes, chorus, ruins
PADS = [(LX, LZ), (0, 120)]


def blocked(x, z):
    if math.hypot(x - PCX, z - PCZ) < PR + 4:
        return True
    if abs(x) < 6 and z > nest_south - 8:
        return True
    if 60 <= x <= 105 and abs(z - HZ) < 6:
        return True
    if math.hypot(x - HX, z - HZ) < MOUND + 3:
        return True
    for (nx, nz) in ((43, 74), (55, 134), (TX, TZ), TOWER):
        if math.hypot(x - nx, z - nz) < 9:
            return True
    if math.hypot(x - VX, z - VZ) < 24 or (-92 <= x <= -62 and abs(z) < 6) or (abs(z - 122) < 6 and 14 < x < 30):
        return True
    if math.hypot(x, z) < NR + 2 and (x, z) in nest:
        return True
    return False


def scatter_decor(cols, seed, spike_p, chorus_p, ruin_p, nest_island=False):
    placed = []
    for (x, z) in sorted(cols):
        if blocked(x, z) and not (nest_island and math.hypot(x, z) >= NR + 3 and not (abs(x) < 6 and z > nest_south - 8)):
            continue
        if nest_island and math.hypot(x, z) < NR + 3:
            continue
        if any(math.hypot(x - px, z - pz) < 7 for px, pz, _ in placed):
            continue
        r = hash01(x, 31, z, seed)
        if r < spike_p:
            spike(x, z, 8 + int(hash01(x, 32, z, seed) * 20), 1 + int(hash01(x, 33, z, seed) * 2.99), seed)
            placed.append((x, z, "s"))
        elif r < spike_p + ruin_p:
            k = int(hash01(x, 34, z, seed) * 3)
            if k == 0:
                ruin_tower(x, z, 5, 7 + int(hash01(x, 35, z, seed) * 6), seed)
            elif k == 1:
                ruin_wall(x, z, 5 + int(hash01(x, 36, z, seed) * 5), "x" if hash01(x, 37, z, seed) < 0.5 else "z", seed)
            else:
                ruin_arch(x, z, "x" if hash01(x, 38, z, seed) < 0.5 else "z", seed)
            placed.append((x, z, "r"))
        elif r < spike_p + ruin_p + chorus_p:
            if chorus(x, z, seed):
                placed.append((x, z, "c"))
    return placed


# keep the mob boxes free of the big stuff
MOB_BOXES = []   # filled below; checked by a second pass would be overkill: the boxes sit in clear ground chosen by hand
a = scatter_decor(main, 1, 0.0012, 0.0016, 0.0009)
b = scatter_decor(nest, 2, 0.0010, 0.0035, 0.0012, nest_island=True)
for k, islet in enumerate(islets):
    scatter_decor(islet, 5 + k, 0.004, 0.012, 0.005)
print("main decor:", len(a), "nest decor:", len(b))
# a handful of specific spikes on the main island, tall ones as landmarks
for (x, z, hgt, rad) in ((20, 80, 30, 3), (70, 70, 26, 3), (30, 118, 24, 2), (118, 128, 28, 3)):
    if (x, z) in main:
        spike(x, z, hgt, rad, 7)

# ---------------------------------------------------------------- Fairy Souls (4)
area.fairy_soul(*soul_a)                                  # the top step of the spiral tower
area.fairy_soul(-20, G + 1, -50)                          # tucked behind the purpur ruin on the nest's rim ledge (set below)
area.fairy_soul(VX + 2, G + 1, VZ + 12)                   # south of the Sepulture's ziggurat, between two spires
area.fairy_soul(*soul_d)                                  # the hideout's hidden alcove
# the nest soul's nook
sx, sz = -20, -50
y0 = area.top.get((sx, sz))
if y0 is None or math.hypot(sx, sz) < FLAT:
    raise SystemExit("nest soul spot needs adjusting")
ruin_wall(sx - 2, sz - 1, 5, "x", seed=11)
area.set(sx, y0 + 1, sz, "minecraft:air")
area.spots["fairy_souls"][1] = [sx, y0 + 1, sz]

# ---------------------------------------------------------------- zones (no overlap)
RES = {"minecraft:end_stone": {"becomes": "minecraft:bedrock", "regrow": 5},
       "minecraft:obsidian": {"becomes": "minecraft:bedrock", "regrow": 15}}


def mob(kind, name, level_, health, damage, xp, coins, mx, every, a_, b_, drops=None, indoors=False):
    m = {"type": kind, "name": name, "level": level_, "health": health, "damage": damage, "xp": xp, "coins": coins,
         "max": mx, "every": every, "from": list(a_), "to": list(b_)}
    if drops:
        m["drops"] = drops
    if indoors:
        m["indoors"] = True
    return m


def drop(item, chance, mn=1, mx=1):
    return {"item": item, "chance": chance, "min": mn, "max": mx}


import json
from pathlib import Path
ITEMS = Path(__file__).resolve().parents[3] / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
for needed in ("ENDER_PEARL", "ENCHANTED_ENDER_PEARL", "OBSIDIAN", "ARROW"):
    assert (ITEMS / (needed.lower() + ".json")).exists(), needed

area.zone("The End", box=((-69, 56, 71), (149, 159, 149)), blocks=RES, mobs=[
    mob("minecraft:enderman", "Enderman", 42, 4500, 500, 28, 10, 8, 4, (25, G - 1, 90), (45, G + 9, 118), [drop("ENDER_PEARL", 1, 1, 2)]),
    mob("minecraft:enderman", "Enderman", 45, 6000, 600, 34, 10, 5, 6, (60, G - 1, 72), (85, G + 9, 88), [drop("ENDER_PEARL", 1, 1, 2)]),
    mob("minecraft:enderman", "Enderman", 50, 9000, 700, 40, 10, 3, 8, (84, G - 1, 124), (112, G + 9, 136), [drop("ENDER_PEARL", 1, 1, 3)]),
    mob("minecraft:endermite", "Endermite", 37, 2000, 400, 8, 10, 3, 10, (84, G - 1, 70), (104, G + 9, 82)),
])
area.zone("Zealot Bruiser Hideout", box=((-69, 12, 71), (149, 55, 149)), mobs=[
    mob("minecraft:enderman", "Zealot Bruiser", 100, 65000, 2500, 300, 40, 8, 3, (HX - 10, FL + 1, HZ - 10), (HX + 10, FL + 9, HZ + 10),
        [drop("ENDER_PEARL", 1, 2, 4), drop("ENCHANTED_ENDER_PEARL", 0.04)], indoors=True)])
area.zone("Dragon's Nest", box=((-70, 12, -150), (70, 159, 70)), mobs=[
    mob("minecraft:enderman", "Zealot", 55, 13000, 1250, 40, 15, 8, 3, (-28, FLR, -18), (-13, FLR + 6, 18),
        [drop("ENDER_PEARL", 1, 2, 4), drop("ENCHANTED_ENDER_PEARL", 0.02)]),
    mob("minecraft:enderman", "Zealot", 55, 13000, 1250, 40, 15, 8, 3, (13, FLR, -18), (28, FLR + 6, 18),
        [drop("ENDER_PEARL", 1, 2, 4), drop("ENCHANTED_ENDER_PEARL", 0.02)]),
    mob("minecraft:wither_skeleton", "Obsidian Defender", 55, 10000, 200, 40, 15, 4, 8, (-28, FLR, -24), (-14, FLR + 6, -19),
        [drop("OBSIDIAN", 1, 2, 5)]),
    mob("minecraft:skeleton", "Watcher", 55, 9500, 500, 40, 15, 4, 8, (14, FLR, 19), (28, FLR + 6, 24),
        [drop("ARROW", 1, 2, 6)]),
])
area.zone("Void Sepulture", box=((-150, 12, -150), (-71, 159, 149)), mobs=[
    mob("minecraft:enderman", "Voidling Fanatic", 85, 1500000, 3500, 110, 20, 6, 6, (VX - 12, G - 1, VZ - 18), (VX + 12, G + 8, VZ - 12),
        [drop("ENDER_PEARL", 1, 4, 5), drop("ENCHANTED_ENDER_PEARL", 0.01)]),
    mob("minecraft:enderman", "Voidling Extremist", 100, 8000000, 13500, 1000, 100, 2, 20, (VX - 12, G - 1, VZ + 12), (VX + 12, G + 8, VZ + 18),
        [drop("ENCHANTED_ENDER_PEARL", 1, 1, 2)]),
])
area.save()
print("arrival top:", area.surface_y(0, 120), "altar top:", area.surface_y(0, 0), "nest_south", nest_south, "main_west", main_west,
      "nest_west", nest_west, "sep_east", sep_east)
print("souls:", area.spots["fairy_souls"], "pillars", PILLARS)
