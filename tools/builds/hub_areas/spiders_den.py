#!/usr/bin/env python3
"""The Spider's Den (Hypixel's spider island, its own void dimension): a dark floating island of coarse dirt, podzol,
gravel and moss, dead trees and cobwebbed giant trunks, a camp near the arrival (Grandma Wolf, Haymitch, Rick at the gravel
pit, Shaggy's stall, the Archaeologist's dig), the huge Spider Mound with a winding ramp to its web-covered top, and
Arachne's Sanctuary in a chamber under the island, reached by a long stair from the mound's foot.

    python tools/builds/hub_areas/spiders_den.py
"""
import math
from kit import Area, hash01, noise

G = 64                       # surface; players stand at 65
MX, MZ = 0, -10              # the mound's centre
TOP = 135                    # the mound's plateau surface (players stand at 136)
SX, SZ, SR = 0, 10, 22       # Arachne's Sanctuary: centre and radius of the round chamber
SFLOOR = 30                  # its floor (players stand at 31)
area = Area("spiders_den", (-130, 20, -130), (260, 130, 260), version=2,
            dimension="skyrpg_skyblock:spiders_den", island="Spider's Den")

dark = area.mix({"coarse_dirt": 5, "podzol": 4, "moss_block": 2, "rooted_dirt": 1, "gravel": 1})
body = area.mix({"andesite": 4, "tuff": 4, "cobbled_deepslate": 2, "cobblestone": 1})
cols = area.island(0, 0, 95, top=G, seed=5, surface=dark, hills=1.5, depth=42, wobble=0.12, dirt="coarse_dirt", stone=body,
                   shape=lambda x, z: 1 + 0.16 * max(0.0, z / 95.0))
assert (0, 100) in cols and (0, 104) in cols and (-14, 97) in cols, "arrival must be on the island"


# ---------------------------------------------------------------- helpers
def ellipsoid(cx, cy, cz, rx, ry, rz):
    for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for z in range(int(cz - rz) - 1, int(cz + rz) + 2):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2 <= 1.0:
                    yield x, y, z


def sign(x, y, z, rot, lines):
    msgs = ['""'] + ['"%s"' % t.replace("'", "\\'") for t in lines] + ['""'] * 3
    area.set(x, y, z, f"dark_oak_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": msgs[:4], "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


def hut(x1, z1, x2, z2, wall, corner, stair, slab, door_x, floor="spruce_planks", h=4, gable=None):
    """A small hut with a gabled roof along x, door on the south side."""
    gable = gable or wall
    area.fill(x1, G, z1, x2, G, z2, floor)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            on_wall = x in (x1, x2) or z in (z1, z2)
            is_corner = x in (x1, x2) and z in (z1, z2)
            for y in range(G + 1, G + 1 + h):
                if is_corner:
                    area.set(x, y, z, corner)
                elif on_wall:
                    win = y == G + 2 and ((z in (z1, z2) and (x - x1) % 4 == 2 and x != door_x) or (x in (x1, x2) and (z - z1) % 4 == 2))
                    area.set(x, y, z, "glass_pane" if win else wall)
                else:
                    area.set(x, y, z, "air")
    area.set(door_x, G + 1, z2, "spruce_door[facing=south,half=lower,hinge=left]")
    area.set(door_x, G + 2, z2, "spruce_door[facing=south,half=upper,hinge=left]")
    area.set(door_x - 1, G + 3, z2 - 1, "air")
    for i in range(0, (z2 - z1) // 2 + 2):
        zl, zr = z1 - 1 + i, z2 + 1 - i
        if zl > zr:
            break
        for x in range(x1 - 1, x2 + 2):
            if zl == zr:
                area.set(x, G + h + 1 + i, zl, slab)
            else:
                area.set(x, G + h + 1 + i, zl, f"{stair}[facing=south]")
                area.set(x, G + h + 1 + i, zr, f"{stair}[facing=north]")
                if x in (x1, x2):
                    for z in range(zl + 1, zr):
                        area.set(x, G + h + 1 + i, z, gable)


def giant_trunk(x, z, height, seed):
    """A huge dead trunk, three wide, tapering and broken at the top, with roots and cobwebs."""
    y0 = area.surface_y(x, z)
    if y0 is None:
        return
    log = "dark_oak_log" if hash01(x, 1, z, seed) < 0.6 else "stripped_dark_oak_log"
    for h in range(1, height + 1):
        r = 1 if h < height * 0.6 else 0
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if r == 1 and abs(dx) + abs(dz) == 2 and hash01(x + dx, h, z + dz, seed) < 0.3:
                    continue
                area.set(x + dx, y0 + h, z + dz, f"minecraft:{log}[axis=y]")
    for dx, dz, ax in ((2, 0, "x"), (-2, 0, "x"), (0, 2, "z"), (0, -2, "z")):
        area.set(x + dx, y0 + 1, z + dz, f"minecraft:{log}[axis={ax}]")
        area.set(x + dx * 2, y0, z + dz * 2, f"minecraft:{log}[axis={ax}]")
        for hh in range(3, height, 3):
            if hash01(x + dx, hh, z + dz, seed) < 0.6:
                area.set(x + dx * 2, y0 + hh, z + dz * 2, "cobweb")
        if hash01(x, dx, z + dz, seed) < 0.6:
            for i in range(1, 4):
                area.set(x + dx * (1 + i), y0 + height - 2 + i // 2, z + dz * (1 + i), f"minecraft:{log}[axis={ax}]")
    area.set(x + 2, y0 + height, z, "cobweb")
    area.set(x, y0 + height + 1, z, "cobweb")


def egg_sac(x, z, seed=0):
    y = area.surface_y(x, z)
    if y is None:
        return
    for (dx, dy, dz) in ((0, 1, 0), (1, 1, 0), (0, 1, 1), (0, 2, 0), (1, 2, 1) if hash01(x, 5, z, seed) < 0.5 else (0, 1, -1)):
        area.set(x + dx, y + dy, z + dz, "white_wool" if hash01(x + dx, dy, z + dz, seed) < 0.75 else "light_gray_wool")
    for dx, dz in ((-1, 0), (2, 0), (0, -1), (0, 2), (1, 1)):
        if hash01(x + dx, 6, z + dz, seed) < 0.6:
            area.set(x + dx, y + 1, z + dz, "cobweb")
    area.set(x, y + 3, z, "cobweb")


def ribcage(x, z, rot):
    y = area.surface_y(x, z)
    if y is None:
        return
    ax = "z" if rot else "x"
    for i in range(-3, 4):
        px, pz = (x, z + i) if rot else (x + i, z)
        area.set(px, y + 1, pz, f"minecraft:bone_block[axis={ax}]")
        if i % 2 == 0:
            for s in (-1, 1):
                qx, qz = (x + s, pz) if rot else (px, z + s)
                area.set(qx, y + 1, qz, "bone_block[axis=y]")
                area.set(qx, y + 2, qz, "bone_block[axis=y]")
    area.set(x + (0 if rot else 4), y + 1, z + (4 if rot else 0), "skeleton_skull[rotation=%d]" % (4 if rot else 12))


# ---------------------------------------------------------------- the mound (a heightfield on top of the island)
def mound_h(x, z):
    dx, dz = x - MX, z - MZ
    ang = math.atan2(dz, dx)
    R = 55 * (1 + 0.16 * math.sin(3 * ang + 1.3) + 0.10 * math.sin(5 * ang + 0.4)) + 3 * noise(x, z, 7, 1.5)
    d = math.hypot(dx, dz * 1.05) / R
    if d >= 1:
        return None
    t = 1 - d
    h = 64 + 90 * t ** 0.9 + 3.5 * noise(x, z, 3, 2.2) * min(1, t * 4) + 1.2 * math.sin(t * 24 + noise(x, z, 1, 3) * 3) * min(1, t * 5)
    return int(round(min(float(TOP), h)))


mound_cols = set()
for x in range(MX - 70, MX + 71):
    for z in range(MZ - 70, MZ + 71):
        h = mound_h(x, z)
        if h is None or (x, z) not in cols:
            continue
        old = area.top[(x, z)]
        if h <= old:
            continue
        mound_cols.add((x, z))
        slope = max(abs((mound_h(x + 1, z) or 64) - (mound_h(x - 1, z) or 64)), abs((mound_h(x, z + 1) or 64) - (mound_h(x, z - 1) or 64)))
        for y in range(old, h):
            area.set(x, y, z, "minecraft:andesite" if hash01(x, y, z, 2) < 0.5 else "minecraft:tuff")
        r = hash01(x, 9, z, 6)
        if h >= TOP:
            surf = "coarse_dirt" if r < 0.45 else "tuff" if r < 0.8 else "moss_block"
        elif slope >= 2:
            surf = "gravel" if r < 0.45 else "tuff" if r < 0.7 else "coarse_dirt" if r < 0.9 else "andesite"
        else:
            surf = "coarse_dirt" if r < 0.4 else "podzol" if r < 0.6 else "gravel" if r < 0.75 else "moss_block" if r < 0.85 else "tuff"
        # Gravel only as a skin over solid rock, never the only block holding anything up.
        area.set(x, h, z, surf)
        if surf == "gravel":
            area.set(x, h - 1, z, "tuff")
            area.set(x, h - 2, z, "andesite")
        area.top[(x, z)] = h

# Outcrops and overhangs sticking out of the slopes, with dripstone under them.
for i in range(26):
    ang = hash01(i, 1, 0, 31) * math.tau
    rad = 24 + hash01(i, 2, 0, 31) * 24
    x, z = int(MX + math.cos(ang) * rad), int(MZ + math.sin(ang) * rad)
    h = mound_h(x, z)
    if h is None or h > 125 or (x, z) not in cols:
        continue
    cy = h + 2
    for (ex, ey, ez) in ellipsoid(x, cy, z, 5 + hash01(i, 3, 0, 31) * 3, 2.5, 4 + hash01(i, 4, 0, 31) * 3):
        if area.get(ex, ey, ez) in (None, "minecraft:air"):
            area.set(ex, ey, ez, "minecraft:tuff" if hash01(ex, ey, ez, 3) < 0.5 else "minecraft:andesite")
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            if area.get(x + dx, cy - 1, z + dz) in ("minecraft:tuff", "minecraft:andesite") and area.get(x + dx, cy - 2, z + dz) in (None, "minecraft:air"):
                if hash01(x + dx, 3, z + dz, 9) < 0.3:
                    area.set(x + dx, cy - 2, z + dz, "cobweb" if hash01(x + dx, 4, z + dz, 9) < 0.7 else
                             "pointed_dripstone[vertical_direction=down,thickness=tip]")

# Caves in the slopes: a rounded room behind a low mouth, webs and a few bones inside.
caves = []
for i in range(9):
    ang = hash01(i, 5, 0, 77) * math.tau
    rad = 26 + hash01(i, 6, 0, 77) * 10
    x, z = int(MX + math.cos(ang) * rad), int(MZ + math.sin(ang) * rad)
    h = mound_h(x, z)
    if h is None or h < 72 or h > 112:
        continue
    cx, cz = x - math.cos(ang) * 3, z - math.sin(ang) * 3          # a little deeper into the hill
    cy = h - 3
    for (ex, ey, ez) in ellipsoid(cx, cy, cz, 5, 3.2, 5):
        area.set(ex, ey, ez, "air")
    for (ex, ey, ez) in ellipsoid(cx, cy - 3, cz, 5, 1, 5):
        if ey == int(cy - 3):
            area.set(ex, ey, ez, "coarse_dirt")
    for k in range(7):
        ex, ez = int(cx + (hash01(i, k, 1, 5) - 0.5) * 7), int(cz + (hash01(i, k, 2, 5) - 0.5) * 7)
        area.set(ex, int(cy) - 2, ez, "cobweb")
        if k % 3 == 0:
            area.set(ex, int(cy) - 2, ez + 1, "bone_block[axis=x]")
    area.set(int(cx), int(cy) - 2, int(cz), "candle[candles=2,lit=false]")
    caves.append((int(cx), int(cy) - 2, int(cz)))

# ---------------------------------------------------------------- the ramp winding up the mound
ramp = {}                    # (x, z) -> surface y
prev_r, theta = 54.0, math.radians(58)
ramp_pts = []
for yb in range(G + 1, TOP + 1):
    r = prev_r
    found = None
    rr = prev_r + 4
    while rr > 5:
        px, pz = MX + rr * math.cos(theta), MZ + rr * math.sin(theta) / 1.05
        h = mound_h(int(round(px)), int(round(pz)))
        if h is not None and h >= yb:
            found = rr
            break
        rr -= 0.5
    if found is not None:
        r = max(min(found, prev_r + 1.5), prev_r - 3.0)
    prev_r = min(r, prev_r + 1.0) if yb < TOP else r
    px, pz = MX + prev_r * math.cos(theta), MZ + prev_r * math.sin(theta) / 1.05
    ramp_pts.append((px, pz, yb))
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if dx * dx + dz * dz <= 6.3:
                c = (int(round(px)) + dx, int(round(pz)) + dz)
                ramp[c] = yb
    theta += 2.4 / max(8.0, prev_r)
for (x, z), yb in ramp.items():
    if (x, z) not in cols:
        continue
    for y in range(yb + 1, yb + 6):
        area.set(x, y, z, "air")
    y = yb - 1
    while y > yb - 14 and not area.solid(x, y, z):
        area.set(x, y, z, "tuff")
        y -= 1
    r = hash01(x, 4, z, 8)
    area.set(x, yb, z, "coarse_dirt" if r < 0.35 else "dirt_path" if r < 0.55 else "packed_mud" if r < 0.75 else "tuff" if r < 0.9 else "podzol")
    if area.get(x, yb, z) == "minecraft:dirt_path":
        area.set(x, yb - 1, z, "coarse_dirt")
    area.top[(x, z)] = yb
# Posts with soul lanterns on the outer edge, now and then; webs over the way.
for k, (px, pz, yb) in enumerate(ramp_pts):
    if k % 9 == 4:
        ang = math.atan2(pz - MZ, px - MX)
        ox, oz = int(round(px + math.cos(ang) * 3)), int(round(pz + math.sin(ang) * 3))
        if (ox, oz) in cols and (ox, oz) not in ramp:
            y = area.surface_y(ox, oz)
            if y is not None:
                area.fill(ox, y + 1, oz, ox, y + 2, oz, "stripped_dark_oak_log[axis=y]")
                area.set(ox, y + 3, oz, "soul_lantern[hanging=false]")
    if k % 11 == 6:
        area.set(int(round(px)), yb + 4, int(round(pz)), "cobweb")

# Cobweb blankets and ground bits over the mound.
for (x, z) in sorted(mound_cols):
    y = area.top[(x, z)]
    if (x, z) in ramp or area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    r = hash01(x, 31, z, 12)
    if r < 0.2:
        area.set(x, y + 1, z, "cobweb")
    elif r < 0.215:
        area.set(x, y + 1, z, "bone_block[axis=x]")

# The plateau on top: a nest of webs, bones, egg sacs, a ring of dead trunks.
for x in range(MX - 16, MX + 17):
    for z in range(MZ - 16, MZ + 17):
        if math.hypot(x - MX, z - MZ) < 14 and area.surface_y(x, z) == TOP and (x, z) not in ramp:
            r = hash01(x, 41, z, 3)
            if r < 0.4:
                area.set(x, TOP + 1, z, "cobweb")
            elif r < 0.43:
                area.set(x, TOP + 1, z, "bone_block[axis=z]")
for k in range(7):
    ang = k / 7 * math.tau + 0.4
    x, z = int(MX + math.cos(ang) * 11), int(MZ + math.sin(ang) * 11)
    if (x, z) not in ramp:
        area.dead_tree(x, TOP + 1, z, height=6 + k % 4, log="dark_oak_log", seed=k)
        area.set(x + 1, TOP + 4, z, "cobweb")
        area.set(x, TOP + 6 + k % 4, z + 1, "cobweb")
for (x, z) in ((MX + 4, MZ + 3), (MX - 5, MZ + 2), (MX + 1, MZ - 5), (MX - 3, MZ - 3)):
    egg_sac(x, z, seed=x)
# The nest's heart: a bone ring around the spot where the Fairy Soul waits, under a canopy of webs.
for (dx, dz) in ((-1, 0), (1, 0), (0, 1), (0, -1)):
    area.set(MX + dx, TOP + 1, MZ + dz, "bone_block[axis=y]")
    area.set(MX + dx, TOP + 2, MZ + dz, "bone_block[axis=y]")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(MX + dx, TOP + 4, MZ + dz, "cobweb")
area.set(MX, TOP + 1, MZ, "air")
area.set(MX, TOP + 2, MZ, "air")

# ---------------------------------------------------------------- the camp near the arrival
area.flatten(-36, 56, 38, 108, G)
area.flatten(-62, 46, -37, 76, G)
camp_ground = area.mix({"coarse_dirt": 4, "dirt_path": 3, "podzol": 2, "tuff": 1})
for x in range(-34, 37):
    for z in range(58, 107):
        if area.surface_y(x, z) == G:
            area.set(x, G, z, camp_ground(x, G, z, 3))
# Main paths: arrival -> camp square -> the mound's foot (and the sanctuary stairs), the pad, the ramp's start.
mix_path = area.mix({"dirt_path": 4, "coarse_dirt": 2, "tuff": 1})
area.path([(0, 102), (0, 86), (0, 70), (0, 56), (0, 47)], width=4, blocks=mix_path)
area.path([(0, 94), (-12, 94), (-14, 97)], width=3, blocks=mix_path)
area.path([(0, 56), (14, 52), (26, 45)], width=3, blocks=mix_path)
area.path([(0, 76), (-27, 76)], width=2, blocks=mix_path)
area.path([(0, 76), (24, 76)], width=2, blocks=mix_path)
area.path([(14, 66), (30, 64)], width=2, blocks=mix_path)
area.path([(-10, 64), (-42, 64)], width=2, blocks=mix_path)
area.path([(6, 84), (10, 86)], width=2, blocks=mix_path)

# Arrival plaza with a signpost and soul lanterns.
for dx, dz in ((-3, -2), (3, -2), (-3, 2), (3, 2)):
    area.set(0 + dx, G + 1, 100 + dz, "cobblestone_wall")
    area.set(0 + dx, G + 2, 100 + dz, "cobblestone_wall")
    area.set(0 + dx, G + 3, 100 + dz, "soul_lantern[hanging=false]")
    area.set(0 + dx + (1 if dx < 0 else -1), G + 3, 100 + dz, "cobweb")
sign(4, G + 1, 98, 8, ["Spider's Den", "Beware the webs", ""])

# Launch pad back to the Hub.
LX, LZ = -14, 97
area.flatten(LX - 4, LZ - 4, LX + 4, LZ + 4, G, "polished_blackstone_bricks")
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(LX + dx, G, LZ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "polished_blackstone")
area.set(LX, G + 1, LZ, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(LX + dx, G + 1, LZ + dz, "polished_blackstone_wall")
    area.set(LX + dx, G + 2, LZ + dz, "soul_lantern[hanging=false]")
sign(LX + 4, G + 1, LZ - 2, 4, ["the Hub", ""])
area.spots["notes"].append(f"Hub launch pad at ({LX}, {G + 1}, {LZ}), box x{LX - 1}..{LX + 1} y{G + 1}..{G + 2} z{LZ - 1}..{LZ + 1}")

# Launch pad to The End on the west edge (Combat XII).
EX, EZ = -84, 0
area.flatten(EX - 4, EZ - 4, EX + 4, EZ + 4, G, "polished_blackstone_bricks")
assert all((EX + dx, EZ + dz) in cols for dx in (-4, 4) for dz in (-4, 4)), "The End pad must be on the island"
for dx in range(-2, 3):
    for dz in range(-2, 3):
        area.set(EX + dx, G, EZ + dz, "slime_block" if abs(dx) < 2 and abs(dz) < 2 else "polished_blackstone")
area.set(EX, G + 1, EZ, "heavy_weighted_pressure_plate")
for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
    area.set(EX + dx, G + 1, EZ + dz, "polished_blackstone_wall")
    area.set(EX + dx, G + 2, EZ + dz, "soul_lantern[hanging=false]")
sign(EX + 4, G + 1, EZ - 2, 4, ["The End", "Combat XII"])
area.spots["notes"].append(f"The End launch pad at ({EX}, {G + 1}, {EZ}), box x{EX - 1}..{EX + 1} y{G + 1}..{G + 2} z{EZ - 1}..{EZ + 1}; "
                           f"landing from The End at ({EX + 4}.5, {G + 1}, {EZ}.5)")

# Grandma Wolf's cottage (west): spruce and cobblestone, a wolf-grey roof, a well-kept little garden of dead bushes.
hut(-34, 70, -24, 79, "spruce_planks", "stripped_spruce_log[axis=y]", "stone_brick_stairs", "stone_brick_slab[type=bottom]", -29,
    floor="spruce_planks", gable="spruce_planks")
area.set(-33, G + 1, 71, "bed[facing=north,part=foot,occupied=false]") if False else None
for x, z, b in ((-33, 71, "barrel[facing=up]"), (-32, 71, "crafting_table"), (-26, 71, "furnace[facing=south,lit=false]"),
                (-25, 71, "barrel[facing=up]"), (-25, 75, "white_carpet")):
    area.set(x, G + 1, z, b)
area.set(-30, G + 4, 74, "lantern[hanging=true]")
area.set(-30, G + 5, 74, "chain[axis=y]")
area.fill(-31, G, 73, -27, G, 76, "white_wool")
area.npc("Grandma Wolf", -29, G + 1, 75, yaw=180.0, note="inside the cottage; sells Spider's Den goods")
for x, z in ((-35, 72), (-35, 77), (-23, 74)):
    area.set(x, G + 1, z, "dead_bush")
area.set(-36, G + 1, 80, "cobweb")

# Haymitch's hut (east): mud bricks, rough roof.
hut(22, 70, 31, 78, "mud_bricks", "stripped_dark_oak_log[axis=y]", "mud_brick_stairs", "mud_brick_slab[type=bottom]", 26,
    floor="packed_mud", gable="mud_bricks")
for x, z, b in ((23, 71, "barrel[facing=up]"), (24, 71, "barrel[facing=up]"), (29, 71, "crafting_table"),
                (30, 71, "chest[facing=south]"), (30, 75, "hay_block")):
    area.set(x, G + 1, z, b)
area.set(26, G + 4, 74, "lantern[hanging=true]")
area.set(26, G + 5, 74, "chain[axis=y]")
area.npc("Haymitch", 27, G + 1, 74, yaw=180.0, note="inside the hut by the camp square")

# Rick by the gravel pit (east): a camp seat, a fire, crates; the pit itself is dug below.
PITX, PITZ = 46, 64
for x in range(PITX - 12, PITX + 13):
    for z in range(PITZ - 12, PITZ + 13):
        d = math.hypot(x - PITX, z - PITZ)
        if (x, z) not in cols or d > 11:
            continue
        sy = G if d >= 9 else G - 1 if d >= 7 else G - 2 if d >= 4 else G - 3
        old = area.top[(x, z)]
        for y in range(sy + 1, old + 1):
            area.set(x, y, z, "air")
        if d < 9:
            area.set(x, sy, z, "gravel")
            area.set(x, sy - 1, z, "coarse_dirt")
            if sy + 1 <= G and hash01(x, 2, z, 4) < 0.07:
                area.set(x, sy + 1, z, "bone_block[axis=x]")
        else:
            area.set(x, sy, z, "coarse_dirt" if hash01(x, 3, z, 4) < 0.7 else "gravel")
            area.set(x, sy - 1, z, "coarse_dirt")
        area.top[(x, z)] = sy
for x, z in ((PITX - 9, PITZ - 6), (PITX - 9, PITZ + 6), (PITX + 9, PITZ - 6), (PITX + 9, PITZ + 6), (PITX, PITZ - 10), (PITX, PITZ + 10)):
    area.fill(x, G + 1, z, x, G + 2, z, "oak_fence")
    area.set(x, G + 3, z, "lantern[hanging=false]")
area.set(PITX - 12, G + 1, PITZ, "campfire[lit=true,facing=east]")
area.set(PITX - 13, G + 1, PITZ - 1, "hay_block[axis=x]")
area.set(PITX - 13, G + 1, PITZ + 1, "barrel[facing=up]")
area.set(PITX - 11, G + 1, PITZ + 2, "oak_stairs[facing=east,half=bottom]")
area.set(PITX - 11, G + 1, PITZ - 2, "oak_stairs[facing=east,half=bottom]")
area.set(PITX - 12, G + 1, PITZ + 3, "iron_bars")
area.path([(30, 64), (PITX - 14, PITZ)], width=2, blocks=mix_path)
area.npc("Rick", PITX - 13, G + 1, PITZ, yaw=-90.0, note="by the campfire at the west edge of the gravel pit")

# Shaggy's stall (south of the square): an open stall with a striped awning and crates.
SX1, SX2, SZ1, SZ2 = 5, 13, 83, 88
area.fill(SX1, G, SZ1, SX2, G, SZ2, "spruce_planks")
for x in (SX1, SX2):
    for z in (SZ1, SZ2):
        area.fill(x, G + 1, z, x, G + 4, z, "spruce_log[axis=y]")
for x in range(SX1 - 1, SX2 + 2):
    for z in range(SZ1 - 1, SZ2 + 2):
        area.set(x, G + 5, z, "white_wool" if (x // 2) % 2 == 0 else "gray_wool")
    area.set(x, G + 4, SZ2 + 1, "cobweb") if hash01(x, 4, 9) < 0.4 else None
for x in range(SX1 + 1, SX2):
    area.set(x, G + 1, SZ1 + 1, "spruce_slab[type=top]")      # the counter
area.set(SX1 + 1, G + 1, SZ1 + 1, "barrel[facing=south]")
area.set(SX2 - 1, G + 1, SZ1 + 1, "barrel[facing=south]")
area.set(SX1 + 1, G + 1, SZ2 - 1, "chest[facing=east]")
area.set(SX2 - 1, G + 1, SZ2 - 1, "hay_block[axis=x]")
area.set(SX1 + 4, G + 4, SZ1 + 1, "lantern[hanging=true]")
area.set(SX1 + 1, G + 2, SZ2, "string") if False else None
sign(SX1 + 4, G + 2, SZ1 + 1, 0, ["Shaggy", "Strings & Eyes"])
area.npc("Shaggy", SX1 + 4, G + 1, SZ1 + 2, yaw=0.0, note="behind the stall counter, shop; face south to the path")

# The Archaeologist's dig site (west): trenches with string lines, a tent, finds.
DX1, DX2, DZ1, DZ2 = -54, -40, 55, 68
for x in range(DX1, DX2 + 1):
    for z in range(DZ1, DZ2 + 1):
        if (x, z) not in cols:
            continue
        old = area.top[(x, z)]
        for y in range(G + 1, old + 1):
            area.set(x, y, z, "air")
        trench = (z - DZ1) % 4 in (1, 2) and DX1 + 1 < x < DX2 - 3
        area.set(x, G, z, "coarse_dirt" if hash01(x, 1, z, 5) < 0.6 else "dirt")
        if trench:
            area.set(x, G, z, "air")
            area.set(x, G - 1, z, "coarse_dirt")
            if hash01(x, 2, z, 5) < 0.08:
                area.set(x, G, z, "bone_block[axis=x]")
        area.top[(x, z)] = G - 1 if trench else G
for x in (DX1, DX2):
    for z in (DZ1, DZ2):
        area.fill(x, G + 1, z, x, G + 3, z, "oak_fence")
        area.set(x, G + 4, z, "lantern[hanging=false]")
for x in range(DX1 + 1, DX2):
    for z in (DZ1, DZ2):
        area.set(x, G + 1, z, "oak_fence") if x % 3 else None
# the tent
for x in range(DX2 - 3, DX2 + 3):
    for i in range(0, 3):
        area.set(x, G + 1 + i, DZ2 - 6 + i, "brown_wool")
        area.set(x, G + 1 + i, DZ2 - 2 - i, "brown_wool")
    area.set(x, G + 3, DZ2 - 4, "brown_wool")
    for z in range(DZ2 - 5, DZ2 - 2):
        area.set(x, G + 1, z, "air") if x in range(DX2 - 2, DX2 + 2) and z in (DZ2 - 4, DZ2 - 3) else None
area.set(DX2 - 1, G + 1, DZ2 - 4, "chest[facing=west]")
area.set(DX2, G + 1, DZ2 - 3, "barrel[facing=up]")
for x, z in ((DX1 + 4, DZ1 - 2), (DX1 + 9, DZ2 + 2), (DX1 + 2, DZ1 + 5)):
    area.set(x, G + 1, z, "skeleton_skull[rotation=%d]" % (int(hash01(x, 0, z) * 16)))
    area.set(x + 1, G + 1, z, "bone_block[axis=x]")
area.npc("Archaeologist", DX2 + 2, G + 1, DZ1 + 5, yaw=-90.0, note="next to the tent at the dig site's east end")

# ---------------------------------------------------------------- Arachne's Sanctuary (a chamber under the island)
for x in range(SX - SR - 1, SX + SR + 2):
    for z in range(SZ - SR - 1, SZ + SR + 2):
        r = math.hypot(x - SX, z - SZ)
        if r > SR:
            continue
        ceil = SFLOOR + 1 + int(27 * math.sqrt(max(0.0, 1 - (r / SR) ** 2))) + int(2 * noise(x, z, 4, 3))
        ceil = min(ceil, 59)
        area.set(x, SFLOOR, z, "polished_blackstone_bricks" if r < 17 and hash01(x, 7, z, 1) < 0.5 else
                 "polished_deepslate" if r < 17 else "cobbled_deepslate")
        for y in range(SFLOOR + 1, ceil + 1):
            area.set(x, y, z, "air")
# a thin ring of dark floor inlays and cracks
for x in range(SX - 18, SX + 19):
    for z in range(SZ - 18, SZ + 19):
        r = math.hypot(x - SX, z - SZ)
        if 8 <= r <= 9 or 15 <= r <= 15.7:
            area.set(x, SFLOOR, z, "crying_obsidian" if hash01(x, 3, z, 1) < 0.15 else "chiseled_polished_blackstone")
# the entrance stair: wide, long, coming down from the mound's foot through the chamber's south wall
area.stairs_down(SX, G + 1, 44, G + 1 - SFLOOR, "north", block="polished_blackstone_brick_stairs", width=5,
                 solid="polished_blackstone_bricks")
for i in range(G + 1 - SFLOOR):          # solid under the stair where it crosses the open chamber
    for dx in range(-2, 3):
        for y in range(SFLOOR + 1, G + 1 - i - 1):
            if area.get(SX + dx, y, 44 - i) in (None, "minecraft:air", "minecraft:cobweb") and 44 - i < SZ + SR:
                area.set(SX + dx, y, 44 - i, "polished_blackstone_bricks")
for z in range(41, 47):
    for dx in (-3, 3):
        area.fill(SX + dx, G + 1, z, SX + dx, G + 4, z, "deepslate_brick_wall")
for dx in (-3, 3):
    area.fill(SX + dx, G + 1, 41, SX + dx, G + 6, 41, "polished_deepslate")
    area.set(SX + dx, G + 7, 41, "soul_lantern[hanging=false]")
area.fill(SX - 3, G + 7, 41, SX + 3, G + 7, 41, "deepslate_brick_slab[type=bottom]")
for dx in range(-2, 3):
    if dx % 2:
        area.set(SX + dx, G + 6, 41, "cobweb")
area.set(SX - 1, G + 6, 41, "cobweb")
sign(SX + 4, G + 1, 47, 0, ["Arachne's", "Sanctuary"])
# pillars, wall webs, lights
for k in range(8):
    ang = k / 8 * math.tau + math.pi / 8
    x, z = int(round(SX + math.cos(ang) * 13)), int(round(SZ + math.sin(ang) * 13))
    for y in range(SFLOOR + 1, 55):
        if area.get(x, y, z) in (None, "minecraft:air") or y < SFLOOR + 20:
            area.set(x, y, z, "polished_deepslate" if y % 5 else "chiseled_deepslate")
    area.set(x, SFLOOR + 1, z, "polished_blackstone_bricks")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if hash01(x + dx, 11 + k, z + dz, 4) < 0.7:
            area.set(x + dx, SFLOOR + 6 + k % 3, z + dz, "cobweb")
        area.set(x + dx, SFLOOR + 1, z + dz, "soul_lantern[hanging=false]") if (dx, dz) == (0, 1) and k % 2 == 0 else None
for x in range(SX - SR, SX + SR + 1):
    for y in range(SFLOOR + 1, 60):
        for z in range(SZ - SR, SZ + SR + 1):
            if area.get(x, y, z) == "minecraft:air" and hash01(x, y, z, 21) < 0.06:
                if any(area.solid(x + dx, y + dy, z + dz) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0))):
                    area.set(x, y, z, "cobweb")
for x, z in ((SX - 19, SZ), (SX + 19, SZ), (SX, SZ - 19), (SX - 14, SZ + 14), (SX + 14, SZ + 14), (SX - 14, SZ - 14), (SX + 14, SZ - 14)):
    area.fill(x, SFLOOR + 4, z, x, SFLOOR + 6, z, "air")
    area.set(x, SFLOOR + 5, z, "soul_lantern[hanging=false]") if area.solid(x, SFLOOR + 4, z) else None
    y = SFLOOR + 1
    while area.get(x, y, z) == "minecraft:air" and y < SFLOOR + 14:
        y += 1
    area.set(x, y - 1, z, "soul_lantern[hanging=true]") if False else None
# the altar at the far (north) end
AX, AZ = SX, SZ - 15
for dx in range(-3, 4):
    for dz in range(-3, 4):
        d = max(abs(dx), abs(dz))
        area.set(AX + dx, SFLOOR + 1, AZ + dz, "polished_blackstone_bricks") if d == 3 else None
        if d <= 2:
            area.set(AX + dx, SFLOOR + 1, AZ + dz, "polished_blackstone_bricks")
        if d <= 1:
            area.set(AX + dx, SFLOOR + 2, AZ + dz, "chiseled_polished_blackstone")
for dx in (-1, 1):
    area.set(AX + dx, SFLOOR + 3, AZ, "candle[candles=4,lit=true]")
area.set(AX, SFLOOR + 3, AZ, "crying_obsidian")
area.set(AX, SFLOOR + 4, AZ, "skeleton_skull[rotation=0]")
for dx in (-3, 3):
    for dz in (-3, 3):
        area.fill(AX + dx, SFLOOR + 2, AZ + dz, AX + dx, SFLOOR + 4, AZ + dz, "bone_block[axis=y]")
        area.set(AX + dx, SFLOOR + 5, AZ + dz, "soul_lantern[hanging=false]")
for x in range(AX - 4, AX + 5):
    area.set(x, SFLOOR + 6, AZ - 4, "cobweb")
for dx in (-2, -1, 0, 1, 2):
    area.set(AX + dx, SFLOOR + 1, AZ + 4, "polished_blackstone_brick_stairs[facing=north,half=bottom]")
area.spots["notes"].append(f"Arachne's Sanctuary: chamber centre ({SX}, {SFLOOR + 1}, {SZ}), radius {SR}, floor y{SFLOOR}, "
                           f"altar at ({AX}, {SFLOOR + 3}, {AZ}); entrance stair from ({SX}, {G + 1}, 44) at the mound's south foot "
                           f"(boss spawn: the altar, arena = the chamber floor)")

# ---------------------------------------------------------------- dead trees, huge trunks, bones, egg sacs, bits
def free(x, z):
    if (x, z) not in cols or (x, z) in mound_cols:
        return False
    if math.hypot(x - MX, z - MZ) < 64:
        return False
    if -62 <= x <= 62 and 50 <= z <= 112:
        return False
    if math.hypot(x - PITX, z - PITZ) < 16 or math.hypot(x - EX, z - EZ) < 12:
        return False
    return True


trunks = 0
for (x, z) in sorted(cols):
    if not free(x, z) or hash01(x, 51, z, 9) > 0.0016:
        continue
    giant_trunk(x, z, 11 + int(hash01(x, 52, z) * 8), trunks)
    trunks += 1
trees = 0
for (x, z) in sorted(cols):
    if not free(x, z) or hash01(x, 11, z, 4) > 0.01:
        continue
    y = area.surface_y(x, z)
    if area.get(x, y + 1, z) not in (None, "minecraft:air"):
        continue
    ht = 5 + int(hash01(x, 12, z) * 5)
    area.dead_tree(x, y + 1, z, height=ht, log="dark_oak_log" if hash01(x, 13, z) < 0.4 else "spruce_log", seed=trees)
    if hash01(x, 14, z) < 0.6:
        area.set(x + 1, y + ht - 1, z, "cobweb")
        area.set(x, y + ht + 1, z + 1, "cobweb")
    trees += 1
sacs = 0
for (x, z) in sorted(cols):
    if (not free(x, z) and not (math.hypot(x - MX, z - MZ) < 66 and (x, z) not in mound_cols and z < 50)) or hash01(x, 61, z, 2) > 0.0022:
        continue
    egg_sac(x, z, seed=sacs)
    sacs += 1
for (x, z) in sorted(cols):
    if free(x, z) and hash01(x, 71, z, 3) < 0.0012:
        ribcage(x, z, int(hash01(x, 72, z) * 2))
# the mound's ring of sacs at the foot
for k in range(8):
    ang = k / 8 * math.tau + 0.2
    egg_sac(int(MX + math.cos(ang) * 58), int(MZ + math.sin(ang) * 58) if MZ + math.sin(ang) * 58 < 40 else 40, seed=k)


def ground_bits(x, z):
    r = hash01(x, 21, z)
    return ("dead_bush" if r < 0.3 else "fern" if r < 0.45 else "cobweb" if r < 0.7 else
            "bone_block[axis=x]" if r < 0.74 else "brown_mushroom" if r < 0.86 else "red_mushroom" if r < 0.9 else None)


area.scatter(cols, 0.16, ground_bits, seed=6, avoid=lambda x, z: (-62 <= x <= 62 and 52 <= z <= 112) or (x, z) in ramp or math.hypot(x - EX, z - EZ) < 7)

# ---------------------------------------------------------------- Fairy Souls
area.fairy_soul(MX, TOP + 1, MZ)                 # the nest on the mound's top
area.fairy_soul(AX, SFLOOR + 1, AZ - 6)          # behind the altar in Arachne's Sanctuary
if caves:
    c = caves[len(caves) // 2]
    area.fairy_soul(c[0] + 2, c[1], c[2])        # in a web-choked cave on the mound
else:
    area.fairy_soul(MX + 30, 100, MZ)
area.fairy_soul(DX1 + 3, G + 1, DZ1 - 6)         # outside the dig site's fence, in the corner behind a dead bush
area.warp("Spider's Den", 0, G + 1, 100, yaw=180.0)
area.arrival(0, G + 1, 100, 180.0)
area.pad("the Hub", LX - 1, G + 1, LZ - 1, LX + 1, G + 2, LZ + 1, destination=("skyrpg_hub:hub", (-213.5, 65.0, -36.5), -90.0))
area.pad("The End", EX - 1, G + 1, EZ - 1, EX + 1, G + 2, EZ + 1, warp="end", skill="combat", level=12)

# ---------------------------------------------------------------- zones (no overlap: split by height, the mound's top on its own)
GRAVEL = {"minecraft:gravel": {"becomes": "minecraft:bedrock", "regrow": 6}}


def mob(kind, name, level, health, damage, xp, coins, mx, every, a, b):
    return {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx, "every": every, "from": list(a), "to": list(b)}


area.zone("Spider's Den", box=((-130, 63, -130), (129, 99, 129)), blocks=GRAVEL, mobs=[
    mob("minecraft:spider", "Splitter Spider", 2, 180, 30, 9, 2, 6, 5, (-70, 63, -20), (-48, 85, 30)),
    mob("minecraft:spider", "Weaver Spider", 3, 160, 35, 9, 2, 6, 5, (48, 63, -20), (70, 85, 30)),
    mob("minecraft:spider", "Dasher Spider", 4, 160, 55, 10, 2, 4, 6, (-25, 63, -76), (25, 90, -60)),
    mob("minecraft:cave_spider", "Voracious Spider", 10, 1000, 100, 10, 2, 4, 8, (25, 63, -55), (50, 95, -35)),
    mob("minecraft:skeleton", "Gravel Skeleton", 2, 100, 35, 6, 1, 4, 8, (PITX - 10, G - 4, PITZ - 10), (PITX + 10, G + 6, PITZ + 10)),
])
area.zone("Spider Mound", box=((-130, 100, -130), (129, 149, 129)), blocks=GRAVEL, mobs=[
    mob("minecraft:spider", "Splitter Spider", 42, 9000, 850, 36, 15, 4, 8, (-35, 100, -50), (35, TOP + 3, 25)),
    mob("minecraft:spider", "Weaver Spider", 42, 9100, 650, 36, 15, 4, 8, (-35, 100, -50), (35, TOP + 3, 25)),
    mob("minecraft:cave_spider", "Voracious Spider", 42, 9000, 700, 36, 15, 3, 10, (-35, 100, -50), (35, TOP + 3, 25)),
])
area.zone("Arachne's Sanctuary", box=((-130, 20, -130), (129, 62, 129)))
area.save()
print("arrival top:", area.surface_y(0, 100), "mound plateau:", area.surface_y(MX, MZ), "caves:", caves, "trunks", trunks, "trees", trees, "sacs", sacs)
