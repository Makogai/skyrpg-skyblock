#!/usr/bin/env python3
"""The Rift: the Photon Pathway (x 70..199, z -120..-31), Hypixel's light-themed route to Stillgore Chateau.

Almost all void: a winding path of glowing walkways (stained glass over sea lanterns, end rods, quartz and pane rails)
joins the four crossings, with a few gaps to jump, floating islets of calcite and white concrete with amethyst/end-rod
spires, beacon-like light beams and light arches. The end house (two storeys, quartz) holds Montezuma's ninth piece.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_photon_pathway.py

Neighbours: the Village Plaza (west; its east edge x 69 is a cliff, a bridge of light starts at x 70, z -60 at y 68),
the Colosseum (north, crossing x 140 at z -120), Stillgore Chateau (east, crossing z -80 at x 199), the Mountaintop
(south, crossing x 130 at z -31). Every crossing has a landing at y 68 (seam).
"""
import math

from kit import hash01, noise
from rift_lib import new_area, GROUND, SEAM, CROSSINGS

area = new_area("photon_pathway", version=1)
area.shell = 7
S = area.set
G = GROUND
X0, X1, Z0, Z1 = 70, 199, -120, -31
assert CROSSINGS[("village_plaza", "photon_pathway")] == ("x", 70, -60)
assert CROSSINGS[("photon_pathway", "colosseum")] == ("z", -121, 140)
assert CROSSINGS[("photon_pathway", "chateau")] == ("x", 200, -80)
assert CROSSINGS[("photon_pathway", "mountaintop")] == ("z", -30, 130)
GLASS = ["white", "light_blue", "white", "yellow"]


def segd(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    l2 = dx * dx + dz * dz
    t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / l2))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


# ---------------------------------------------------------------- islets
ISLET_COLS = {}      # (x, z) -> islet id
ISLET_SURFACE = area.mix({"white_concrete": 5, "calcite": 4, "quartz_block": 1, "smooth_quartz": 2})
ISLET_UNDER = area.mix({"calcite": 5, "white_concrete": 2, "tuff": 1})
ISLET_BODY = area.mix({"calcite": 5, "tuff": 3, "smooth_basalt": 1, "amethyst_block": 1, "white_concrete": 2})


def islet(cx, cz, r, top=G, depth=None, seed=0, name=None):
    depth = depth or int(r * 3.6 + 8)
    name = name or (cx, cz)
    cols = []
    R = int(r * 1.4 + 3)
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            ang = math.atan2(z - cz, x - cx)
            rr = r * (1 + 0.18 * math.sin(3 * ang + seed) + 0.1 * math.sin(5 * ang + seed * 2.3))
            d = math.hypot(x - cx, z - cz) / rr
            if d > 1:
                continue
            cols.append((x, z))
            ISLET_COLS[(x, z)] = name
            S(x, top, z, ISLET_SURFACE(x, top, z))
            for y in range(top - 1, top - 4, -1):
                S(x, y, z, ISLET_UNDER(x, y, z))
            bottom = top - 4 - int(depth * (1 - d * d) ** 0.6 + 2 * noise(x, z, seed, 1.5) * (1 - d))
            for y in range(top - 4, bottom - 1, -1):
                S(x, y, z, ISLET_BODY(x, y, z))
            if d < 0.5 and hash01(x, 0, z, seed + 3) < 0.05:
                for i in range(2 + int(hash01(x, 1, z, seed) * 4)):
                    S(x, bottom - 1 - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" +
                      ("tip" if i == 0 and False else "middle") + "]")
            if d > 0.8 and hash01(x, 5, z, seed + 9) < 0.07:
                S(x, top - 1, z, "minecraft:amethyst_block")
    return cols


def spire(x, z, base, h, seed=0):
    """A crystal spire: amethyst with clusters and an end rod tip."""
    for i in range(h):
        S(x, base + i, z, "minecraft:amethyst_block" if i % 3 else "minecraft:calcite")
        if i and i < h - 1:
            for dx, dz, f in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
                if hash01(x + dx, base + i, z + dz, seed) < 0.28:
                    S(x + dx, base + i, z + dz, f"minecraft:amethyst_cluster[facing={f}]")
    S(x, base + h, z, "minecraft:end_rod[facing=up]")


def beam(x, z, base, h):
    """A beacon-looking light pillar: a quartz plinth, a glass column with sea lanterns, end rods around the foot."""
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            S(x + dx, base, z + dz, "minecraft:smooth_quartz")
            if abs(dx) + abs(dz) == 2:
                S(x + dx, base + 1, z + dz, "minecraft:quartz_pillar[axis=y]")
            elif dx or dz:
                S(x + dx, base + 1, z + dz, "minecraft:end_rod[facing=up]")
    for i in range(h):
        y = base + 1 + i
        S(x, y, z, "minecraft:sea_lantern" if i % 7 == 6 else ("minecraft:light_blue_stained_glass" if i % 2 else "minecraft:white_stained_glass"))
        if i % 7 == 6:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                S(x + dx, y, z + dz, "minecraft:white_stained_glass")
    S(x, base + 1 + h, z, "minecraft:end_rod[facing=up]")


def arch(cx, cz, axis, span, height, y0):
    """A light arch over a path: quartz pillars, a glass arc lined with end rods. axis = the path's direction."""
    half = span // 2
    for t in range(-half, half + 1):
        yy = y0 + int(round(height * (1 - (t / half) ** 2) ** 0.7))
        x, z = (cx + t, cz) if axis == "z" else (cx, cz + t)
        if abs(t) == half:
            for y in range(y0, yy + 1):
                S(x, y, z, "minecraft:quartz_pillar[axis=y]")
            S(x, yy + 1, z, "minecraft:sea_lantern")
        else:
            S(x, yy + 1, z, "minecraft:light_blue_stained_glass" if t % 2 else "minecraft:white_stained_glass")
            S(x, yy, z, "minecraft:end_rod[facing=down]")
            S(x, yy + 2, z, "minecraft:end_rod[facing=up]") if t % 3 == 0 else None


# ---------------------------------------------------------------- walkways
PATH_COLS = {}       # (x, z) -> kind


def walkway(points, width=3, wide=False):
    half = width / 2.0
    cells = {}
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        for x in range(min(ax, bx) - 4, max(ax, bx) + 5):
            for z in range(min(az, bz) - 4, max(az, bz) + 5):
                d = segd(x, z, ax, az, bx, bz)
                if d <= half - 0.2 + 0.05:
                    cells[(x, z)] = min(d, cells.get((x, z), 9))
    for (x, z), d in cells.items():
        if not (X0 <= x <= X1 and Z0 <= z <= Z1):
            continue
        PATH_COLS[(x, z)] = "wide" if wide else ("edge" if d > half - 1.0 else "deck")
        col = GLASS[((x + z) // 4) % 4]
        S(x, G, z, f"minecraft:{col}_stained_glass")
        S(x, G - 1, z, "minecraft:sea_lantern" if d <= half - 1.0 or wide else "minecraft:smooth_quartz")
        if wide:
            S(x, G - 1, z, "minecraft:sea_lantern" if (x + z) % 3 == 0 else "minecraft:smooth_quartz")
        if (x + z) % 6 == 0 and d <= half - 1.0:
            S(x, G - 2, z, "minecraft:end_rod[facing=down]")
        if (x * 7 + z * 3) % 11 == 0:
            S(x, G - 3, z, "minecraft:light[level=12]")


def rails():
    for (x, z), kind in list(PATH_COLS.items()):
        if kind != "edge" or (x, z) in ISLET_COLS:
            continue
        if any(PATH_COLS.get((x + dx, z + dz)) == "wide" for dx in range(-2, 3) for dz in range(-2, 3)):
            continue
        S(x, G + 1, z, "minecraft:end_rod[facing=up]" if (x + z) % 5 == 0 else
          "minecraft:light_blue_stained_glass_pane" if (x + z) % 2 else "minecraft:white_stained_glass_pane")


def landing(x1, z1, x2, z2, toward):
    """A crossing landing: coarse dirt / rooted dirt / mud bricks at y 68 like the other areas' crossings, a small shard under."""
    mixp = area.mix({"coarse_dirt": 4, "rooted_dirt": 3, "mud_bricks": 3})
    cx, cz = (x1 + x2) / 2, (z1 + z2) / 2
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not (X0 <= x <= X1 and Z0 <= z <= Z1):
                continue
            ISLET_COLS[(x, z)] = "landing"
            S(x, G, z, mixp(x, G, z))
            for y in range(G - 1, G - 4, -1):
                S(x, y, z, "minecraft:mud" if hash01(x, y, z, 4) < 0.5 else "minecraft:packed_mud")
            far = {"north": z - z1, "south": z2 - z, "east": x2 - x, "west": x - x1}[toward]   # 0 at the seam, grows inland
            side = abs(x - cx) if toward in ("north", "south") else abs(z - cz)
            depth = 10 - 2 * far - 2 * int(side) + int(3 * hash01(x, 9, z, 6))
            for y in range(G - 4, G - 4 - max(depth, 2), -1):
                S(x, y, z, "minecraft:tuff" if hash01(x, y, z, 5) < 0.6 else "minecraft:calcite")
            if far >= 3 and side >= 2 and hash01(x, 3, z, 2) < 0.3:
                S(x, G + 1, z, "minecraft:pink_petals[flower_amount=1,facing=north]" if hash01(x, 4, z, 1) < 0.5 else "minecraft:glow_lichen[down=true]")


# ---------------------------------------------------------------- the plan
ISLETS = [  # cx, cz, r, seed
    (104, -60, 10, 1),     # C1: where the bridge from the Plaza arrives
    (130, -50, 10, 2),     # C2: the junction (south crossing, east path, house branch)
    (125, -92, 6, 3),     # N1: on the way north
    (182, -78, 8, 4),     # D: before the Chateau
    (176, -44, 11, 5),    # H: the end house
]
for cx, cz, r, sd in ISLETS:
    islet(cx, cz, r, seed=sd)

DECOR = [(150, -100, 8, 21, 7), (90, -85, 6, 22, 5), (92, -42, 6, 23, 4), (160, -100, 6, 24, 6), (186, -108, 6, 25, 5),
         (112, -112, 6, 26, 5), (148, -72, 5, 27, 4), (192, -52, 5, 28, 4), (74, -86, 6, 29, 6), (77, -104, 4, 30, 3),
         (166, -118 + 4, 5, 31, 4), (112, -38, 4, 32, 3), (150, -38, 4, 33, 3)]
for cx, cz, r, sd, sh in DECOR:
    beamed = (cx, cz) in ((150, -100), (74, -86))
    t = G if beamed else G + (sd % 3) - 1
    islet(cx, cz, r, top=t, seed=sd)
    if not beamed:
        spire(cx, t + 1, cz, sh, seed=sd)
    if r >= 6 and sd % 2:
        spire(cx + 2, t + 1, cz - 2, max(2, sh - 2), seed=sd + 50)
beam(150, -100, G + 1, 30)
beam(74, -86, G + 1, 22)

# Landings at the crossings (north z -120, east x 199, south z -31) and a stub under the bridge's start at the Plaza.
landing(137, -120, 143, -113, "north")
landing(192, -84, 199, -76, "east")
landing(127, -38, 133, -31, "south")
for x in range(70, 76):
    for z in range(-63, -56):
        for y in range(G - 1, G - 6 - (x - 70) // 2 * -1 - 2, -1):
            pass
        S(x, G - 1, z, "minecraft:calcite")
        S(x, G - 2, z, "minecraft:tuff")
        if x < 73:
            S(x, G - 3, z, "minecraft:calcite")

WALKWAYS = [
    ([(70, -60), (76, -60)], 5, True),
    ([(76, -60), (100, -60)], 3, False),
    ([(106, -66), (110, -76)], 3, False),                        # north, gap of 2
    ([(113, -80), (121, -87)], 3, False),
    ([(128, -97), (134, -106), (138, -112)], 3, False),
    ([(138, -112), (140, -113)], 5, True),
    ([(110, -58), (116, -54), (124, -50)], 3, False),            # C1 -> C2
    ([(130, -42), (130, -37)], 3, False),                        # south
    ([(130, -37), (130, -32)], 5, True),
    ([(137, -52), (148, -56), (156, -60)], 3, False),            # east
    ([(160, -63), (166, -68), (174, -75)], 3, False),            # gap of 2
    ([(188, -80), (192, -80)], 3, False),
    ([(192, -80), (199, -80)], 5, True),
    ([(137, -47), (150, -45), (164, -44)], 3, False),            # to the end house
    ([(100, -112), (110, -112), (120, -108), (124, -97)], 3, False),   # return path from the jump section's top
]
# The last one is a quiet loop back so nobody has to fall: it starts beside the soul's islet and ends on islet N1.
WALKWAYS.pop()
for pts, w, wide in WALKWAYS:
    walkway(pts, w, wide)

# The hard jump section (to the Enigma Soul): 2x2 glass stones on sea lanterns, rising, with 2-3 block gaps, off islet N1.
STONES = [(116, -97, 68), (112, -99, 69), (108, -101, 70), (104, -102, 71), (100, -104, 72), (96, -105, 72)]
for sx, sz, sy in STONES:
    for dx in range(2):
        for dz in range(2):
            S(sx + dx, sy, sz + dz, "minecraft:light_blue_stained_glass" if (dx + dz) % 2 else "minecraft:yellow_stained_glass")
            S(sx + dx, sy - 1, sz + dz, "minecraft:sea_lantern")
            PATH_COLS[(sx + dx, sz + dz)] = "stone"
    S(sx, sy - 2, sz, "minecraft:end_rod[facing=down]")
SOUL_ISLET = (88, -107, 4)
islet(SOUL_ISLET[0], SOUL_ISLET[1], SOUL_ISLET[2], top=72, depth=10, seed=8)
for x in range(SOUL_ISLET[0] - 5, SOUL_ISLET[0] + 6):          # a pocket of light: the crystal heart
    for z in range(SOUL_ISLET[1] - 5, SOUL_ISLET[1] + 6):
        if ISLET_COLS.get((x, z)) == (88, -107) and math.hypot(x - 88, z - -107) < 1.6:
            S(x, 72, z, "minecraft:sea_lantern")
spire(86, -109, 73, 5, seed=3)
spire(90, -105, 73, 3, seed=4)
SOUL = (88, 73, -108)
S(88, 72, -108, "minecraft:glowstone")
S(88, 71, -108, "minecraft:sea_lantern")

# Rails after every walkway and islet exists.
rails()

# ---------------------------------------------------------------- spires, beams and arches on the islets
spire(102, -58, 69, 5, seed=1)
spire(106, -63, 69, 7, seed=2)
spire(133, -53, 69, 6, seed=3)
spire(126, -47, 69, 4, seed=5)
spire(123, -94, 69, 5, seed=6)
spire(127, -90, 69, 3, seed=7)
spire(185, -76, 69, 6, seed=8)
spire(180, -81, 69, 4, seed=9)
spire(186, -50, 69, 4, seed=10)
spire(168, -38, 69, 5, seed=11)
beam(130, -50, G + 1, 24)
beam(182, -78, G + 1, 28)
arch(74, -60, "x", 0, 0, 0) if False else None
# Light arches over the entrances: the Plaza bridge (axis x: pillars at z -64 and -56), the Chateau and Colosseum crossings.
for t in range(-4, 5):
    pass


def arch_x(x, zc, span, height, y0):
    half = span // 2
    for t in range(-half, half + 1):
        yy = y0 + int(round(height * (1 - (t / half) ** 2) ** 0.7))
        z = zc + t
        if abs(t) == half:
            for y in range(y0, yy + 1):
                S(x, y, z, "minecraft:quartz_pillar[axis=y]")
            S(x, yy + 1, z, "minecraft:sea_lantern")
        else:
            S(x, yy + 1, z, "minecraft:light_blue_stained_glass" if t % 2 else "minecraft:white_stained_glass")
            S(x, yy, z, "minecraft:end_rod[facing=down]")


arch_x(78, -60, 8, 7, G + 1)
arch_x(194, -80, 8, 7, G + 1)
arch(140, -117, "z", 8, 7, G + 1)
arch(130, -34, "z", 8, 7, G + 1)

# Photons: loose end rods and sea lanterns drifting in the void, away from every walkway.
motes = 0
for i in range(420):
    x = X0 + int(hash01(i, 1, 0, 77) * (X1 - X0 + 1))
    z = Z0 + int(hash01(i, 2, 0, 77) * (Z1 - Z0 + 1))
    y = 52 + int(hash01(i, 3, 0, 77) * 44)
    if any(abs(x - px) <= 3 and abs(z - pz) <= 3 for px, pz in list(PATH_COLS)[::1] if abs(y - G) < 14 and False):
        continue
    near = any((x + dx, z + dz) in PATH_COLS or (x + dx, z + dz) in ISLET_COLS for dx in range(-4, 5) for dz in range(-4, 5))
    if near and abs(y - G) < 16:
        continue
    if area.get(x, y, z) is not None:
        continue
    S(x, y, z, "minecraft:end_rod[facing=up]" if hash01(i, 4, 0, 77) < 0.7 else "minecraft:sea_lantern")
    motes += 1

# ---------------------------------------------------------------- the end house on islet H (176, -44)
HX0, HX1, HZ0, HZ1 = 173, 179, -47, -41     # walls (7x7), interior x 174..178, z -46..-42
F1, F2, ROOF = G, G + 4, G + 8              # ground floor block y 68; second floor block y 72; roof block y 76
for x in range(HX0 - 1, HX1 + 2):
    for z in range(HZ0 - 1, HZ1 + 2):
        S(x, G, z, "minecraft:smooth_quartz")
        wall = x in (HX0, HX1) or z in (HZ0, HZ1)
        inside = not wall and HX0 < x < HX1 and HZ0 < z < HZ1
        if inside:
            S(x, G, z, "minecraft:white_concrete" if (x + z) % 2 else "minecraft:smooth_quartz")
            S(x, F2, z, "minecraft:smooth_quartz")
            S(x, ROOF, z, "minecraft:quartz_slab[type=bottom]")
        for y in range(G + 1, ROOF):
            if wall and (HX0 <= x <= HX1 and HZ0 <= z <= HZ1):
                corner = x in (HX0, HX1) and z in (HZ0, HZ1)
                window = (y in (G + 2, G + 6)) and not corner and ((x + z) % 2 == 0)
                if corner:
                    S(x, y, z, "minecraft:quartz_pillar[axis=y]")
                elif y == F2:
                    S(x, y, z, "minecraft:chiseled_quartz_block")
                elif window:
                    S(x, y, z, "minecraft:white_stained_glass")
                else:
                    S(x, y, z, "minecraft:quartz_block")
            elif inside and y != F2:
                S(x, y, z, "minecraft:air")
        if HX0 - 1 <= x <= HX1 + 1 and HZ0 - 1 <= z <= HZ1 + 1:
            if not (HX0 <= x <= HX1 and HZ0 <= z <= HZ1):
                S(x, ROOF, z, "minecraft:quartz_slab[type=bottom]") if False else None
for x in range(HX0 - 1, HX1 + 2):                          # roof with an overhang and a crown of end rods
    for z in range(HZ0 - 1, HZ1 + 2):
        S(x, ROOF, z, "minecraft:smooth_quartz_slab[type=bottom]")
for x in range(HX0, HX1 + 1):
    for z in range(HZ0, HZ1 + 1):
        S(x, ROOF, z, "minecraft:smooth_quartz")
for dx, dz in ((0, 0), (6, 0), (0, 6), (6, 6)):
    S(HX0 + dx, ROOF + 1, HZ0 + dz, "minecraft:end_rod[facing=up]")
S(176, ROOF + 1, -44, "minecraft:sea_lantern")
S(176, ROOF + 2, -44, "minecraft:end_rod[facing=up]")
# Door on the west side (the path arrives there) and a lamp above it.
for y in (G + 1, G + 2):
    S(HX0, y, -44, "minecraft:air")
S(HX0 - 1, G + 3, -44, "minecraft:sea_lantern")
# Stairs up: x 174, z -42 .. -45 ascending north, with the floor opened over them.
for i, z in enumerate((-42, -43, -44, -45)):
    S(174, G + 1 + i, z, "minecraft:quartz_stairs[facing=north,half=bottom]")
    if i < 3:
        S(174, F2, z, "minecraft:air")
        S(174, F2 + 1, z, "minecraft:air")
for z in (-42, -43, -44):
    S(174, F2 + 1, z, "minecraft:air")
# Ground floor: a lectern, a bookshelf wall; upstairs: a bookshelf and a lectern at the piece's nook.
S(178, G + 1, -45, "minecraft:lectern[facing=west]")
for z in (-46, -45, -44):
    S(178, G + 1, z - 0, "minecraft:bookshelf") if z != -45 else None
S(176, G + 3, -44, "minecraft:sea_lantern")
for z in range(-46, -41):
    S(178, F2 + 1, z, "minecraft:bookshelf")
    S(178, F2 + 2, z, "minecraft:bookshelf") if z % 2 else None
S(176, F2 + 4, -44, "minecraft:sea_lantern")
S(176, F2 + 1, -46, "minecraft:lectern[facing=south]")
S(176, F2 + 4, -44, "minecraft:sea_lantern")
MONTEZUMA = (176, F2 + 1, -44)

# ---------------------------------------------------------------- NPC spots, souls, zone
area.npc("Deer", 194, G + 1, -82, 90.0, "where the path reaches the Chateau crossing (east), just north of the crossing path")
area.npc("Photon Keeper", 127, G + 1, -53, 0.0, "mid-path, on the central junction islet beside the light beam")
area.npc("enigma:Photon Pathway", *SOUL, 0.0, "end of the hard jump section: six 2x2 glass stones rising off the north islet (125, -92), 2-3 block gaps, onto the crystal islet at (88, -107)")
area.npc("spawn:montezuma_9", MONTEZUMA[0], MONTEZUMA[1], MONTEZUMA[2], 0.0, "second floor of the end house at (176, 73, -44); a stair at x 174 climbs to it")
area.zone("Photon Pathway", box=((70, 0, -120), (199, 200, -31)), blocks={})
area.save()
print("motes", motes, "islets", len(ISLETS) + 1, "path columns", len(PATH_COLS))
print("seams: west", {area.surface_y(70, z) for z in range(-62, -57)}, "north", {area.get(x, G, -120) is not None for x in range(138, 143)},
      "east", {area.get(199, G, z) is not None for z in range(-82, -77)}, "south", {area.get(x, G, -31) is not None for x in range(128, 133)})
