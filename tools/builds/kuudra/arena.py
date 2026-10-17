"""Kuudra's Hollow: builds structure/kuudra/arena.nbt (see kuudra_kit.py for THE RULES).

    cd tools/builds/kuudra && PYTHONIOENCODING=utf-8 python arena.py

Layout: crater wall ring (r 55..64+, y 0..47), Elle's platform (r 13, top y 18) with the ballista aimed east, six
islets (r ~6, top y 16) each joined by a railed bridge, a few half-sunk spikes east around Kuudra's pit.
"""
import math
import random

from kuudra_kit import (Arena, ARRIVAL, CENTER, CHEST, ISLETS, ISLET_R, ISLET_TOP, KUUDRA, KUUDRA_R, LAVA, PLATFORM,
                        PLATFORM_R, SIZE, HEIGHT, dist)

arena = Arena()
CX, CZ = CENTER
rng = random.Random(2610)
TAU = math.tau


# -- noise ---------------------------------------------------------------------------------------------------------
def _mix(h):
    h &= 0xFFFFFFFF
    h ^= h >> 16
    h = (h * 0x7FEB352D) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x846CA68B) & 0xFFFFFFFF
    h ^= h >> 16
    return h


def hv(x, y, z, seed=0):
    """Deterministic hash -> [0, 1)."""
    h = _mix(x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ (seed * 2654435761))
    return h / 4294967296.0


def _smooth(f):
    return f * f * (3 - 2 * f)


def noise2(u, v, seed=0, period=None):
    """Value noise in [-1, 1]; period wraps u (for angles)."""
    i, j = math.floor(u), math.floor(v)
    fu, fv = _smooth(u - i), _smooth(v - j)

    def c(a, b):
        if period:
            a %= period
        return hv(a, b, 7, seed) * 2 - 1

    top = c(i, j) * (1 - fu) + c(i + 1, j) * fu
    bot = c(i, j + 1) * (1 - fu) + c(i + 1, j + 1) * fu
    return top * (1 - fv) + bot * fv


def ang_noise(theta, y, count, yscale, seed):
    return noise2((theta + math.pi) / TAU * count, y / yscale, seed, period=count)


def put(x, y, z, block):
    arena.set(x, y, z, block)


def unit(a):
    return math.cos(a), math.sin(a)


BRIDGE_ANGLES = [math.atan2(iz - CZ, ix - CX) for ix, iz in ISLETS]

# -- palettes -----------------------------------------------------------------------------------------------------
ROCK = ["blackstone", "blackstone", "blackstone", "basalt[axis=y]", "smooth_basalt", "netherrack", "polished_blackstone"]


def pick(options, h):
    return options[int(h * len(options)) % len(options)]


# =====================================================================================================================
# 1. Crater wall
# =====================================================================================================================
wall_cells = set()
VEINS = []
for i in range(16):
    VEINS.append((rng.uniform(-math.pi, math.pi), rng.uniform(0, TAU), rng.uniform(1.0, 2.0), rng.randint(24, 45)))


def angdiff(a, b):
    d = (a - b + math.pi) % TAU - math.pi
    return d


def wall_params(theta, y):
    rin = (59.4 - 2.4 * (y / 47.0) + 3.2 * ang_noise(theta, y, 54, 7, 11) + 1.3 * ang_noise(theta, y, 150, 3, 12))
    return max(55.2, min(63.5, rin))


for x in range(SIZE):
    for z in range(SIZE):
        dx, dz = x - CX, z - CZ
        d = math.hypot(dx, dz)
        if d < 54.5 or d > 72:
            continue
        theta = math.atan2(dz, dx)
        htop = 41 + 4.8 * ang_noise(theta, 0.5, 26, 1000, 21) + 2.2 * ang_noise(theta, 0.5, 95, 1000, 22)
        htop = int(min(47, htop))
        colmask = ang_noise(theta, 0.5, 157, 1000, 31)
        for y in range(0, HEIGHT):
            if y > htop - 3 * max(0.0, d - 64):
                break
            rin = wall_params(theta, y)
            if d < rin:
                continue
            depth = d - rin
            h = hv(x, y, z, 5)
            block = None
            # fake lava flows on the inner face
            if depth < 1.7 and y >= LAVA:
                for tc, ph, wd, ytop in VEINS:
                    if y > ytop:
                        continue
                    c = tc + 0.06 * math.sin(y * 0.27 + ph) + 0.03 * math.sin(y * 0.71 + ph * 2)
                    width = wd * (1.0 + 0.5 * math.sin(y * 0.4 + ph)) + (1.5 if y < 14 else 0)
                    if abs(angdiff(theta, c)) * d < width:
                        block = "shroomlight" if h < 0.16 else ("glowstone" if h < 0.20 else "magma_block")
                        break
            if block is None:
                if colmask > 0.35:
                    block = "basalt[axis=y]" if h < 0.9 else "smooth_basalt"
                elif colmask < -0.55:
                    block = "smooth_basalt" if h < 0.6 else "polished_basalt[axis=y]"
                else:
                    if y <= 13 and h < 0.10:
                        block = "magma_block"
                    elif h < 0.015:
                        block = "gilded_blackstone"
                    elif h < 0.10:
                        block = "netherrack"
                    elif h < 0.17:
                        block = "basalt[axis=y]"
                    elif h < 0.30:
                        block = "polished_blackstone"
                    else:
                        block = "blackstone"
            put(x, y, z, block)
            wall_cells.add((x, y, z))

# lights scattered on the wall's visible faces
for (x, y, z) in sorted(wall_cells):
    if y < LAVA + 1:
        continue
    cur = arena.get(x, y, z)
    if cur in ("minecraft:shroomlight", "minecraft:glowstone", "minecraft:magma_block"):
        continue
    exposed = any((x + a, y + b, z + c) not in wall_cells for a, b, c in
                  ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))
    if exposed:
        h = hv(x, y, z, 77)
        if h < 0.010:
            put(x, y, z, "shroomlight")
        elif h < 0.014:
            put(x, y, z, "glowstone")

# =====================================================================================================================
# 2. Central platform
# =====================================================================================================================
EDGE_SHELL = ["polished_blackstone_bricks", "polished_blackstone_bricks", "blackstone_bricks", "cracked_polished_blackstone_bricks"]


def floor_block(x, z, dx, dz, d):
    spoke = False
    for a in BRIDGE_ANGLES:
        ux, uz = unit(a)
        t = dx * ux + dz * uz
        s = abs(-dx * uz + dz * ux)
        if t > 5 and s <= 1.2:
            spoke = True
    h = hv(x, 0, z, 3)
    if d <= 4.6:
        if d < 1.5:
            return "chiseled_polished_blackstone"
        if 3.6 <= d <= 4.6:
            return "nether_bricks" if (x + z) % 2 == 0 else "red_nether_bricks"
        return "polished_blackstone_bricks" if h > 0.1 else "cracked_polished_blackstone_bricks"
    if d >= 11.2:
        return "red_nether_bricks" if (x + z) % 3 else "nether_bricks"
    if 7.3 <= d <= 8.6:
        return "nether_bricks" if h > 0.15 else "chiseled_nether_bricks"
    if spoke:
        return "nether_bricks" if h > 0.2 else "cracked_nether_bricks"
    if (x + z) % 2 == 0:
        return "polished_blackstone" if h > 0.12 else "cracked_polished_blackstone_bricks"
    return "blackstone_bricks"


for x in range(CX - 15, CX + 16):
    for z in range(CZ - 15, CZ + 16):
        dx, dz = x - CX, z - CZ
        d = math.hypot(dx, dz)
        if d > PLATFORM_R:
            continue
        for y in range(0, PLATFORM):
            if d > 11.8:
                h = hv(x, y, z, 9)
                if y <= 11 and h < 0.18:
                    block = "magma_block"
                elif y % 5 == 3:
                    block = "nether_bricks" if h > 0.2 else "cracked_nether_bricks"
                else:
                    block = pick(EDGE_SHELL, h)
            elif y < 10:
                block = "blackstone"
            else:
                block = "polished_blackstone" if hv(x, y, z, 4) > 0.15 else "blackstone"
            put(x, y, z, block)
        put(x, PLATFORM, z, floor_block(x, z, dx, dz, d))

# shroomlights set into the floor edge
for k in range(12):
    a = math.radians(30 * k)
    put(round(CX + 11.7 * math.cos(a)), PLATFORM, round(CZ + 11.7 * math.sin(a)), "shroomlight")
for k in range(6):
    a = math.radians(60 * k + 30)
    put(round(CX + 8.0 * math.cos(a)), PLATFORM, round(CZ + 8.0 * math.sin(a)), "shroomlight")

# parapet at radius 13 only (12 < d <= 13), with gaps at the bridges
lantern_cells = set()
for k in range(12):
    a = math.radians(30 * k + 15)
    best = None
    for x in range(CX - 14, CX + 15):
        for z in range(CZ - 14, CZ + 15):
            d = math.hypot(x - CX, z - CZ)
            if 12 < d <= 13:
                diff = abs(angdiff(math.atan2(z - CZ, x - CX), a))
                if best is None or diff < best[0]:
                    best = (diff, x, z)
    lantern_cells.add((best[1], best[2]))

for x in range(CX - 14, CX + 15):
    for z in range(CZ - 14, CZ + 15):
        dx, dz = x - CX, z - CZ
        d = math.hypot(dx, dz)
        if not (12 < d <= 13):
            continue
        gap = False
        for a in BRIDGE_ANGLES:
            ux, uz = unit(a)
            t = dx * ux + dz * uz
            s = abs(-dx * uz + dz * ux)
            if t > 0 and s <= 3.4:
                gap = True
        if gap:
            continue
        h = hv(x, 19, z, 6)
        put(x, 19, z, "chiseled_polished_blackstone" if h < 0.12 else ("blackstone_bricks" if h < 0.5 else "polished_blackstone_bricks"))
        if (x, z) in lantern_cells:
            put(x, 20, z, "lantern")
        elif (x + z) % 3 == 0:
            put(x, 20, z, "polished_blackstone_brick_slab[type=bottom]")

# ARRIVAL / CHEST pads (floor only: they must stay clear above)
put(ARRIVAL[0], PLATFORM, ARRIVAL[2], "gilded_blackstone")
put(CHEST[0], PLATFORM, CHEST[2], "gilded_blackstone")

# =====================================================================================================================
# 3. The ballista (within radius 4 of the centre, up to y 26), aimed east (+x)
# =====================================================================================================================


def bl(dx, dy, dz, block):
    assert math.hypot(dx, dz) <= 4.0 and 19 + dy <= 26, (dx, dy, dz)
    put(CX + dx, 19 + dy, CZ + dz, block)


# turntable (y19) and a circular track (y20)
for dx in range(-4, 5):
    for dz in range(-4, 5):
        d = math.hypot(dx, dz)
        if d <= 3.7:
            if d <= 1.6:
                bl(dx, 0, dz, "iron_block")
            elif d <= 2.6:
                bl(dx, 0, dz, "spruce_planks")
            else:
                bl(dx, 0, dz, "polished_blackstone_bricks")
        if 3.0 <= d <= 3.8:
            bl(dx, 1, dz, "dark_oak_slab[type=bottom]")
# pivot and four legs, the deck, the anvil mechanism
for dy in (1, 2):
    bl(0, dy, 0, "iron_block")
for dx in (-1, 1):
    for dz in (-1, 1):
        for dy in (1, 2):
            bl(dx, dy, dz, "stripped_dark_oak_log[axis=y]")
for dx in range(-1, 2):
    for dz in range(-1, 2):
        bl(dx, 3, dz, "dark_oak_planks")
bl(0, 3, 0, "anvil[facing=north]")
# braces
for dz in (-2, 2):
    bl(0, 1, dz, "spruce_log[axis=y]")
    bl(0, 2, dz, "spruce_fence")
    bl(0, 3, dz, "dark_oak_slab[type=bottom]")
# the stock (a long beam along x) with side wings and a winch at the back
for dx in range(-3, 3):
    bl(dx, 4, 0, "dark_oak_log[axis=x]")
for dx in (-3, -2, -1, 0):
    for dz in (-1, 1):
        bl(dx, 4, dz, "spruce_slab[type=bottom]")
bl(-3, 5, 0, "iron_block")
bl(-3, 5, -1, "spruce_fence")
bl(-3, 5, 1, "spruce_fence")
bl(-3, 6, -1, "iron_bars")
bl(-3, 6, 1, "iron_bars")
bl(-2, 5, 0, "dark_oak_fence")
bl(-2, 6, 0, "lantern")
# the prod (bow arms), curving forward
bl(1, 4, 0, "iron_block")
for dz in (-1, 1):
    bl(1, 4, dz, "iron_block")
    bl(1, 4, 2 * dz, "spruce_log[axis=z]")
    bl(2, 4, 2 * dz, "dark_oak_planks")
    bl(2, 4, 3 * dz, "dark_oak_log[axis=z]")
    bl(2, 5, 3 * dz, "iron_bars")
    bl(2, 3, 2 * dz, "spruce_fence")  # arm brace
# bowstring drawn back to the nock: iron bars V at y+5
for dz in (-1, 1):
    for (px, pz) in ((2, 3), (1, 2), (0, 1)):
        bl(px, 5, pz * dz, "iron_bars")
bl(-1, 5, 0, "iron_bars")
# the bolt: shaft, iron head, fletching
for dx in (0, 1, 2):
    bl(dx, 5, 0, "lightning_rod[facing=east]")
bl(3, 5, 0, "iron_block")
bl(4, 5, 0, "end_rod[facing=east]")
bl(-1, 6, 0, "iron_bars")

# =====================================================================================================================
# 4. Islets
# =====================================================================================================================


def islet(idx, cx, cz):
    var = idx
    a_out = math.atan2(cz - CZ, cx - CX)
    a_in = a_out + math.pi
    ux, uz = unit(a_in)
    seed = 40 + idx
    cols = [
        ["blackstone", "basalt[axis=y]", "basalt[axis=y]", "smooth_basalt", "magma_block", "netherrack"],
        ["blackstone", "blackstone", "polished_blackstone", "basalt[axis=y]", "gilded_blackstone", "smooth_basalt"],
        ["magma_block", "blackstone", "basalt[axis=y]", "magma_block", "netherrack", "blackstone"],
        ["netherrack", "netherrack", "blackstone", "crimson_nylium", "netherrack", "nether_bricks"],
        ["basalt[axis=y]", "smooth_basalt", "blackstone", "soul_soil", "polished_basalt[axis=y]", "magma_block"],
        ["netherrack", "blackstone", "warped_nylium", "basalt[axis=y]", "netherrack", "smooth_basalt"],
    ][var]
    top_variants = {3: "crimson_nylium", 5: "warped_nylium"}
    # lantern post position: outer side, radius 3.5
    px = cx + round(3.5 * math.cos(a_out + (0.5 if var % 2 else -0.5)))
    pz = cz + round(3.5 * math.sin(a_out + (0.5 if var % 2 else -0.5)))
    # spire anchors on the rim, away from the bridge
    spires = []
    tries = 0
    while len(spires) < 3 + (var % 2) and tries < 200:
        tries += 1
        a = rng.uniform(0, TAU)
        if abs(angdiff(a, a_in)) < math.radians(55):
            continue
        r = rng.uniform(4.4, 5.4)
        sx, sz = cx + round(r * math.cos(a)), cz + round(r * math.sin(a))
        if all(math.hypot(sx - ox, sz - oz) > 3 for ox, oz, _ in spires):
            spires.append((sx, sz, rng.randint(4, 9)))

    for x in range(cx - 9, cx + 10):
        for z in range(cz - 9, cz + 10):
            pxl, pzl = x - cx, z - cz
            d = math.hypot(pxl, pzl)
            theta = math.atan2(pzl, pxl)
            R = 5.9 + 0.8 * ang_noise(theta, 0, 9, 1000, seed) + 0.35 * ang_noise(theta, 0, 20, 1000, seed + 9)
            q = pxl * ux + pzl * uz
            lat = abs(-pxl * uz + pzl * ux)
            corridor = q > 0 and lat < 4.4
            if d > R + 1.0:
                continue
            top = ISLET_TOP
            if d > 5.0 and not corridor:
                top = ISLET_TOP + round(2.4 * noise2(x / 2.1, z / 2.1, seed + 3) + 0.3)
                if d > R:
                    top = min(top, ISLET_TOP - 1 + (1 if hv(x, 0, z, 8) < 0.3 else 0))
            elif d >= 3.5 and not corridor and noise2(x / 1.7, z / 1.7, seed + 4) > 0.45:
                top = ISLET_TOP + 1 + (1 if noise2(x / 1.3, z / 1.3, seed + 5) > 0.7 else 0)
            if d > R + 0.4 and top >= ISLET_TOP:
                top = ISLET_TOP - 1
            if d > R and d <= 5.0:
                pass
            flare = 0.9 if d > R else 0
            for y in range(0, top + 1):
                if d > R + (0.8 if y <= 11 else 0.0):
                    continue
                if d > R and y > 11:
                    continue
                h = hv(x, y, z, 50 + idx)
                if y == top:
                    block = top_variants.get(var) if (var in top_variants and h < 0.55) else pick(cols, h)
                    if y == ISLET_TOP and d <= 2.2:
                        block = "blackstone" if h < 0.7 else "polished_blackstone"  # calm middle for crates
                elif y >= 10 and h < 0.22:
                    block = "magma_block" if var in (0, 2, 4) else pick(cols, h * 4)
                elif y <= 10:
                    block = "basalt[axis=y]" if h < 0.5 else "blackstone"
                else:
                    block = pick(cols, h)
                put(x, y, z, block)

    # spires
    for sx, sz, hgt in spires:
        for dy in range(hgt):
            r = 1.35 * (1 - dy / hgt) + 0.25
            for ax in range(-2, 3):
                for az in range(-2, 3):
                    if math.hypot(ax, az) <= r + 0.05 * hv(sx + ax, dy, sz + az, 61):
                        mat = {0: "basalt[axis=y]", 1: "blackstone", 2: "magma_block" if hv(sx + ax, dy, sz + az, 62) < 0.15 else "basalt[axis=y]",
                               3: "crimson_stem[axis=y]", 4: "basalt[axis=y]", 5: "warped_stem[axis=y]"}[var]
                        put(sx + ax, ISLET_TOP + dy, sz + az, mat)
        if var == 3:
            put(sx, ISLET_TOP + hgt, sz, "nether_wart_block")
            for ax, az in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(sx + ax, ISLET_TOP + hgt - 1, sz + az, "nether_wart_block")
        if var == 5:
            put(sx, ISLET_TOP + hgt, sz, "shroomlight")
            for ax, az in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(sx + ax, ISLET_TOP + hgt - 1, sz + az, "warped_wart_block")
    # broken pillar (variant 1 and 4), at the rim
    if var in (1, 4):
        a = a_in + math.pi + rng.uniform(-0.7, 0.7)
        bx, bz = cx + round(4.6 * math.cos(a)), cz + round(4.6 * math.sin(a))
        for dy in range(1, 7):
            for ax, az in ((0, 0), (1, 0), (0, 1), (1, 1)):
                blk = "chiseled_polished_blackstone" if dy == 3 else ("cracked_polished_blackstone_bricks" if hv(bx + ax, dy, bz + az, 90) < 0.3 else "polished_blackstone_bricks")
                if dy >= 5 and hv(bx + ax, dy, bz + az, 91) < 0.5:
                    continue
                put(bx + ax, ISLET_TOP + dy, bz + az, blk)
        put(bx, ISLET_TOP + 6, bz, "polished_blackstone_brick_stairs[facing=east,half=bottom]")
        put(bx + 2, ISLET_TOP + 1, bz - 1, "polished_blackstone_brick_slab[type=bottom]")
        put(bx - 1, ISLET_TOP + 1, bz + 2, "blackstone_stairs[facing=north,half=bottom]")
    # lantern post
    for dy in range(1, 4):
        put(px, ISLET_TOP + dy, pz, "nether_brick_fence")
    put(px, ISLET_TOP, pz, "polished_blackstone_bricks")
    put(px, ISLET_TOP + 4, pz, "lantern")
    # shroomlights set into the top
    for s_ in (-1, 1):
        a = a_out + s_ * 1.5
        put(cx + round(4.2 * math.cos(a)), ISLET_TOP, cz + round(4.2 * math.sin(a)), "shroomlight")


for i, (ix, iz) in enumerate(ISLETS):
    islet(i, ix, iz)

# =====================================================================================================================
# 5. Bridges
# =====================================================================================================================
T_END = 41.0
for bi, (ix, iz) in enumerate(ISLETS):
    a = BRIDGE_ANGLES[bi]
    ux, uz = unit(a)
    for x in range(min(CX, ix) - 5, max(CX, ix) + 6):
        for z in range(min(CZ, iz) - 5, max(CZ, iz) + 6):
            dx, dz = x - CX, z - CZ
            d = math.hypot(dx, dz)
            t = dx * ux + dz * uz
            s = -dx * uz + dz * ux
            if d <= PLATFORM_R or t > T_END or t < 8 or abs(s) > 2.7:
                continue
            k = 34 + round(4 * max(0.0, min(1.0, (40.0 - t) / 27.0)))
            if k % 2 == 0:
                ytop, slab = k // 2 - 1, False
            else:
                ytop, slab = (k - 1) // 2, True
            h = hv(x, 0, z, 70 + bi)
            stripe = int(t) % 6 == 0
            if stripe:
                full, half = "polished_blackstone_bricks", "polished_blackstone_brick_slab[type=bottom]"
            elif h < 0.12:
                full, half = "cracked_nether_bricks", "nether_brick_slab[type=bottom]"
            else:
                full, half = "nether_bricks", "nether_brick_slab[type=bottom]"
            put(x, ytop, z, half if slab else full)
            put(x, ytop - 1, z, "polished_blackstone_bricks")
            if abs(s) > 1.7 and t <= T_END - 0.4:
                put(x, ytop + 1, z, "nether_brick_fence")
                if int(round(t)) % 8 == 4 and abs(s) > 2.2:
                    put(x, ytop + 2, z, "lantern")
    # supports every ~8 blocks, going down into the lava
    for t0 in (20.0, 28.0, 36.0):
        for x in range(min(CX, ix) - 5, max(CX, ix) + 6):
            for z in range(min(CZ, iz) - 5, max(CZ, iz) + 6):
                dx, dz = x - CX, z - CZ
                t = dx * ux + dz * uz
                s = -dx * uz + dz * ux
                if abs(t - t0) <= 0.75 and 1.5 < abs(s) <= 2.7:
                    k = 34 + round(4 * max(0.0, min(1.0, (40.0 - t) / 27.0)))
                    ytop = (k // 2 - 1) if k % 2 == 0 else (k - 1) // 2
                    for y in range(0, ytop - 1):
                        put(x, y, z, "blackstone_bricks" if (y % 4) else "nether_bricks")

# =====================================================================================================================
# 6. Half-sunk spikes around Kuudra's pit (east side only)
# =====================================================================================================================
for k, ad in enumerate(range(-60, 61, 20)):
    a = math.radians(ad)
    r = KUUDRA_R + 2.2 + (k % 2) * 1.6
    sx, sz = KUUDRA[0] + round(r * math.cos(a)), KUUDRA[1] + round(r * math.sin(a))
    tip = 11 + (k * 3) % 4
    for y in range(0, tip + 1):
        rad = 1.8 * (1 - (y - 4) / max(1, tip - 4)) if y > 4 else 2.0
        for ax in range(-2, 3):
            for az in range(-2, 3):
                x, z = sx + ax, sz + az
                if math.hypot(ax, az) <= rad and dist(x, z, *KUUDRA) > KUUDRA_R + 0.5:
                    if arena.get(x, y, z) is None:
                        put(x, y, z, "basalt[axis=y]" if hv(x, y, z, 13) < 0.7 else ("magma_block" if y <= 10 else "blackstone"))

# =====================================================================================================================
# 7. Connect fences / bars to their neighbours (structures keep the state they are saved with)
# =====================================================================================================================
for (x, y, z), (entry, _) in list(arena.cells.items()):
    base = entry["id"]
    if not (base.endswith("_fence") or base == "minecraft:iron_bars"):
        continue
    props = []
    for name, (ax, az) in (("east", (1, 0)), ("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0))):
        if arena.get(x + ax, y, z + az) == base:
            props.append(f"{name}=true")
    if props:
        arena.set(x, y, z, base + "[" + ",".join(props) + "]")

arena.save()
