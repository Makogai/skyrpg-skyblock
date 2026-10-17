"""The first set of Catacombs rooms: entrance, blood, fairy and five normal 1x1 rooms.

    python tools\\builds\\catacombs\\rooms_core.py        (from the repo root)

Everything is seeded per room path, so reruns give the same rooms. Before saving, each room is also checked here
for walkability (every door spot reaches every other door spot, every mob and secret, and back), for unsupported
falling blocks and for water that could flow out.
"""
import random
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from room_kit import Room, FLOOR  # noqa: E402

F = FLOOR


def M(n):
    return n if ":" in n else "minecraft:" + n


AIR = M("air")
SB, CSB, MSB, CHSB = M("stone_bricks"), M("cracked_stone_bricks"), M("mossy_stone_bricks"), M("chiseled_stone_bricks")
DB, DT = M("deepslate_bricks"), M("deepslate_tiles")
PBB = M("polished_blackstone_bricks")
RNB, NB = M("red_nether_bricks"), M("nether_bricks")
CHAIN = M("iron_chain[axis=y]")
WATER = M("water")
LANTERN, SOUL = M("lantern"), M("soul_lantern")
BAR = M("iron_bars")

AGE = {
    SB: [(CSB, .15), (MSB, .10)],
    M("stone_brick_stairs"): [(M("mossy_stone_brick_stairs"), .2)],
    M("stone_brick_slab"): [(M("mossy_stone_brick_slab"), .2)],
    DB: [(M("cracked_deepslate_bricks"), .15)],
    DT: [(M("cracked_deepslate_tiles"), .15)],
    PBB: [(M("cracked_polished_blackstone_bricks"), .2)],
    RNB: [(M("cracked_nether_bricks"), .12), (NB, .10)],
}

# ---------------------------------------------------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------------------------------------------------


def lane(x, z):
    """The 3-wide strips in front of the four door spots (kept clear)."""
    return (14 <= x <= 16 and (z <= 3 or z >= 27)) or (14 <= z <= 16 and (x <= 3 or x >= 27))


def is_air(r, x, y, z):
    return r.get(x, y, z) in (None, AIR)


def near_marker(r, x, z, d):
    return any(abs(m[1] - x) <= d and abs(m[3] - z) <= d for m in r.markers)


def make(path, shape, wall=SB, floor=SB, ceiling=None, ceil=None, plate=DB):
    r = Room(path, shape)
    r.shell(wall=wall, floor=floor, ceiling=ceiling or wall)
    r.rng = random.Random("core:" + path)
    r.ceil = ceil
    if ceil:
        r.fill(1, ceil, 1, 29, ceil, 29, plate)
    return r


def floor_patches(r, n, blocks=None, avoid=None):
    blocks = blocks or [M("coarse_dirt"), M("gravel"), M("andesite"), M("cobblestone"), M("mossy_cobblestone"), CSB]
    for _ in range(n):
        cx, cz, rad = r.rng.randint(2, 28), r.rng.randint(2, 28), r.rng.randint(1, 3)
        main = r.rng.choice(blocks)
        for x in range(cx - rad, cx + rad + 1):
            for z in range(cz - rad, cz + rad + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 > rad * rad + 1 or not (1 <= x <= 29 and 1 <= z <= 29):
                    continue
                if avoid and avoid(x, z):
                    continue
                if r.get(x, F, z) == SB:
                    r.set(x, F, z, main if r.rng.random() < .8 else r.rng.choice(blocks))


def scatter(r, n, block, region=(1, 1, 29, 29), y=F + 1, on=None, avoid=None, tries=600, clear=0):
    x1, z1, x2, z2 = region
    placed = []
    for _ in range(tries):
        if len(placed) >= n:
            break
        x, z = r.rng.randint(x1, x2), r.rng.randint(z1, z2)
        if lane(x, z) or near_marker(r, x, z, clear) or (avoid and avoid(x, z)):
            continue
        if not is_air(r, x, y, z) or not solid(r, x, y - 1, z):
            continue
        if on and r.get(x, y - 1, z) not in on:
            continue
        r.set(x, y, z, block() if callable(block) else block)
        placed.append((x, z))
    return placed


def skulls(r, n, region=(1, 1, 29, 29), wither=.3, avoid=None):
    def pick():
        kind = "wither_skeleton_skull" if r.rng.random() < wither else "skeleton_skull"
        return M(f"{kind}[rotation={r.rng.randint(0, 15)}]")
    return scatter(r, n, pick, region=region, avoid=avoid)


def candles(r, n, region=(1, 1, 29, 29), kind="candle", avoid=None):
    return scatter(r, n, lambda: M(f"{kind}[candles={r.rng.randint(1, 4)},lit=true]"), region=region, avoid=avoid)


def cobwebs(r, n, ymin=10, ymax=15, depth=2):
    placed = 0
    for _ in range(n * 30):
        if placed >= n:
            break
        a = r.rng.randint(1, 29)
        off = r.rng.randint(0, depth - 1)
        y = r.rng.randint(ymin, ymax)
        x, z = [(a, 1 + off), (a, 29 - off), (1 + off, a), (29 - off, a)][r.rng.randint(0, 3)]
        if (lane(x, z) and y <= 12) or near_marker(r, x, z, 1) or not is_air(r, x, y, z):
            continue
        r.set(x, y, z, M("cobweb"))
        placed += 1


def ribs(r, pos, base, lamp, h=3):
    """Pilasters along the four walls, each topped with a standing lamp."""
    for p in pos:
        for (x, z) in ((p, 1), (p, 29), (1, p), (29, p)):
            if lane(x, z) or not all(is_air(r, x, y, z) for y in range(F + 1, F + h + 2)):
                continue
            r.fill(x, F + 1, z, x, F + h, z, base)
            r.set(x, F + h + 1, z, lamp)


def lamps(r, xs, zs, kinds=(LANTERN, SOUL), drop=(1, 2, 3)):
    """Chains and lanterns hanging from the inner ceiling plate."""
    top = r.ceil
    i = 0
    for x in xs:
        for z in zs:
            n, kind = drop[i % len(drop)], kinds[i % len(kinds)]
            i += 1
            if r.get(x, top, z) in (None, AIR):
                continue
            if not all(is_air(r, x, y, z) for y in range(top - n - 1, top)):
                continue
            for y in range(top - n, top):
                r.set(x, y, z, CHAIN)
            r.set(x, top - n - 1, z, kind + "[hanging=true]")


def glow(r, pts, block=M("shroomlight")):
    for x, z in pts:
        r.set(x, r.ceil, z, block)


def pillar(r, x, z, y0, y1, base=CHSB, shaft=SB, base_h=2):
    for y in range(y0, y1 + 1):
        r.set(x, y, z, base if y < y0 + base_h or y == y1 else shaft)


def pile(r, cx, cz, rad, low=None, high=None):
    low = low or [M("cobblestone"), M("mossy_cobblestone"), M("andesite"), M("gravel"), M("coarse_dirt")]
    high = high or [M("cobblestone"), M("andesite")]
    for x in range(cx - rad, cx + rad + 1):
        for z in range(cz - rad, cz + rad + 1):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 > rad * rad + 1 or not (1 <= x <= 29 and 1 <= z <= 29) or lane(x, z) or near_marker(r, x, z, 2):
                continue
            if not is_air(r, x, F + 1, z):
                continue
            r.set(x, F + 1, z, r.rng.choice(low))
            if d2 <= rad * rad // 2 and r.rng.random() < .8 and is_air(r, x, F + 2, z):
                r.set(x, F + 2, z, r.rng.choice(high))


def stairs(kind, facing, half="bottom"):
    """Stairs that rise toward `facing`."""
    return M(f"{kind}[facing={facing},half={half}]")


# ---------------------------------------------------------------------------------------------------------------------
# walkability / sanity checks (own, beyond room_kit's)
# ---------------------------------------------------------------------------------------------------------------------
NS_EXACT = {"air", "cave_air", "cobweb", "water", "lantern", "soul_lantern", "iron_chain", "lily_pad", "spore_blossom",
            "fern", "short_grass", "pink_petals", "glow_lichen", "allium", "cornflower", "azure_bluet", "dandelion",
            "poppy", "oxeye_daisy", "lily_of_the_valley", "blue_orchid", "wither_rose", "torchflower"}
NS_SUFFIX = ("_skull", "_head", "candle", "_carpet", "_tulip", "_torch", "_button", "_sign")


def solid(r, x, y, z):
    if y < 0 or y >= 32:
        return True
    i = r.get(x, y, z)
    if i is None:
        return False
    n = i.split(":")[1]
    return not (n in NS_EXACT or n.endswith(NS_SUFFIX))


def standable(r, x, y, z):
    return r.in_room(x, z) and solid(r, x, y - 1, z) and not solid(r, x, y, z) and not solid(r, x, y + 1, z)


def moves(r, x, y, z):
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if not r.in_room(nx, nz):
            continue
        if solid(r, nx, y, nz) or solid(r, nx, y + 1, nz):
            if solid(r, nx, y, nz) and not solid(r, nx, y + 1, nz) and not solid(r, nx, y + 2, nz) \
                    and not solid(r, x, y + 2, z):
                yield nx, y + 1, nz
        else:
            ny = y
            while ny > 1 and not solid(r, nx, ny - 1, nz):
                ny -= 1
            if y - ny <= 6 and solid(r, nx, ny - 1, nz):
                yield nx, ny, nz


def reach(r, start):
    seen = {start}
    queue = deque([start])
    while queue:
        cur = queue.popleft()
        for nxt in moves(r, *cur):
            if nxt not in seen:
                seen.add(nxt)
                queue.append(nxt)
    return seen


def verify(r):
    errs = []
    for (x, y, z), (e, _) in r.cells.items():
        n = e["id"].split(":")[1]
        if (n in ("gravel", "sand", "red_sand") or n.endswith("concrete_powder")) and not solid(r, x, y - 1, z):
            errs.append(f"falling block {n} unsupported at {(x, y, z)}")
        if n == "water":
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                nb = r.get(x + dx, y + dy, z + dz)
                if nb is None or nb == AIR:
                    errs.append(f"water at {(x, y, z)} can flow out")
                    break
    fronts = []
    for cells, (ix, iz) in r.door_spots():
        x, z = cells[1]
        fronts.append((x + ix * 2, F + 1, z + iz * 2))
    targets = []
    for kind, x, y, z, data in r.markers:
        if kind in ("mob", "spawn", "secret_item", "key"):
            if not standable(r, x, y, z):
                errs.append(f"{kind} at {(x, y, z)} isn't on walkable floor")
            else:
                targets.append((kind, x, y, z))
        elif kind == "secret_chest":
            spots = [(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if standable(r, x + dx, y, z + dz)]
            if not spots:
                errs.append(f"chest at {(x, y, z)} has nowhere to stand")
            else:
                targets.append(("chest", x, y, z, spots))
    home = reach(r, fronts[0])
    for f in fronts:
        rf = reach(r, f)
        for g in fronts:
            if g not in rf:
                errs.append(f"door front {f} can't reach {g}")
        for t in targets:
            pts = t[4] if t[0] == "chest" else [t[1:4]]
            if not any(tuple(p) in rf for p in pts):
                errs.append(f"door front {f} can't reach {t[0]} {t[1:4]}")
    for t in targets:
        pts = t[4] if t[0] == "chest" else [t[1:4]]
        back = set()
        for p in pts:
            back |= reach(r, tuple(p))
        if fronts[0] not in back:
            errs.append(f"{t[0]} {t[1:4]} can't get back to a door")
    if errs:
        raise SystemExit(f"{r.path}: " + "; ".join(errs[:10]))


def finish(r):
    r.weather("age", AGE)
    verify(r)
    return r.save()


LAMP_XS = (5, 11, 19, 25)

# ---------------------------------------------------------------------------------------------------------------------
# 1. entrance
# ---------------------------------------------------------------------------------------------------------------------


def entrance():
    r = make("special/entrance", "entrance", ceil=18, plate=PBB, ceiling=SB)
    r.spawn(15, F + 1, 15)
    # concentric floor rings around the arrival spot
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            d = max(abs(dx), abs(dz))
            block = {0: CHSB, 1: CHSB, 3: PBB, 5: DT, 6: M("polished_blackstone")}.get(d)
            if block:
                r.set(15 + dx, F, 15 + dz, block)
    # moss corners with a little greenery
    for cx, cz in ((24, 6), (6, 24), (24, 24)):
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                if (x - cx) ** 2 + (z - cz) ** 2 <= 10 and r.rng.random() < .85 and 1 <= x <= 29 and 1 <= z <= 29:
                    r.set(x, F, z, M("moss_block"))
    scatter(r, 36, lambda: r.rng.choice([M("short_grass"), M("fern"), M("moss_carpet"), M("fern"), M("short_grass")]),
            on=(M("moss_block"),))
    # the corner shrine
    r.fill(2, F, 2, 8, F, 8, PBB)
    r.fill(2, F, 8, 8, F, 8, M("polished_blackstone"))
    for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
        r.fill(x, F + 1, z, x, F + 4, z, CHSB)
    r.fill(3, F + 5, 3, 7, F + 5, 7, SB)
    r.set(5, F + 1, 5, CHSB)
    r.set(5, F + 2, 5, M("candle[candles=4,lit=true]"))
    r.set(5, F + 4, 5, SOUL + "[hanging=true]")
    for x, z in ((4, 5), (6, 5), (5, 4), (5, 6)):
        r.set(x, F + 1, z, M("candle[candles=2,lit=true]"))
    # lamp posts
    for x, z in ((21, 9), (9, 21), (21, 21)):
        r.fill(x, F + 1, z, x, F + 3, z, PBB)
        r.set(x, F + 4, z, SOUL)
    candles(r, 8, region=(10, 10, 20, 20))
    lamps(r, LAMP_XS, LAMP_XS, drop=(1, 2))
    glow(r, [(15, 8), (15, 22), (8, 15), (22, 15)])
    ribs(r, (6, 10, 20, 25), PBB, LANTERN)
    cobwebs(r, 4, 12, 16)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 2. blood room
# ---------------------------------------------------------------------------------------------------------------------


def blood():
    r = make("special/blood", "blood", wall=RNB, floor=PBB, ceiling=NB, ceil=20, plate=RNB)
    r.fill(14, F, 1, 16, F, 29, RNB)
    r.fill(1, F, 14, 29, F, 16, RNB)
    for t in range(1, 30):
        for x, z in ((13, t), (17, t), (t, 13), (t, 17)):
            if r.get(x, F, z) == PBB:
                r.set(x, F, z, M("polished_blackstone"))
    # blood splotches
    for _ in range(9):
        cx, cz, rad = r.rng.randint(3, 27), r.rng.randint(3, 27), r.rng.randint(1, 2)
        for x in range(cx - rad, cx + rad + 1):
            for z in range(cz - rad, cz + rad + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 <= rad * rad + 1 and 1 <= x <= 29 and 1 <= z <= 29 \
                        and r.rng.random() < .85:
                    r.set(x, F, z, M("red_concrete"))
    # the dais
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            if abs(dx) + abs(dz) <= 9:
                r.set(15 + dx, F + 1, 15 + dz, RNB)
    r.fill(12, F + 2, 12, 18, F + 2, 18, M("chiseled_nether_bricks"))
    r.set(15, F + 2, 15, M("redstone_block"))
    for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3), (0, -3), (0, 3), (-3, 0), (3, 0)):
        r.set(15 + dx, F + 2, 15 + dz, M("redstone_block") if dx and dz else M("crying_obsidian"))
    r.watcher(15, F + 3, 15)
    for x, z in ((11, 11), (19, 11), (11, 19), (19, 19), (10, 15), (20, 15), (15, 10), (15, 20)):
        r.set(x, F + 2, z, M("red_candle[candles=3,lit=true]"))
    # crimson pillars with glowing bands
    for x, z in ((6, 6), (24, 6), (6, 24), (24, 24)):
        r.fill(x, F + 1, z, x, r.ceil - 1, z, M("crimson_stem[axis=y]"))
        r.fill(x, F + 1, z, x, F + 2, z, PBB)
        r.set(x, F + 5, z, M("shroomlight"))
        r.set(x, F + 8, z, M("shroomlight"))
    outer = lambda x, z: 5 <= x <= 25 and 5 <= z <= 25  # noqa: E731
    scatter(r, 12, M("nether_wart_block"), avoid=outer)
    skulls(r, 10, wither=1.0, avoid=outer)
    candles(r, 6, kind="red_candle", avoid=outer)
    lamps(r, LAMP_XS, LAMP_XS, kinds=(SOUL, SOUL, LANTERN), drop=(1, 2, 3))
    glow(r, [(15, 5), (15, 25), (5, 15), (25, 15)], M("shroomlight"))
    ribs(r, (5, 10, 20, 25), M("chiseled_nether_bricks"), SOUL)
    cobwebs(r, 4, 13, 17)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 3. fairy room
# ---------------------------------------------------------------------------------------------------------------------


def fairy():
    r = make("special/fairy", "fairy", wall=M("pink_terracotta"), floor=SB, ceil=18, plate=M("cherry_planks"))
    for x in range(1, 30):
        for z in range(1, 30):
            d = ((x - 15) ** 2 + (z - 15) ** 2) ** .5
            if d <= 12 and r.rng.random() < .9:
                r.set(x, F, z, M("moss_block"))
            elif r.rng.random() < .15:
                r.set(x, F, z, M("pink_terracotta"))
    # pools: water source blocks only, in a basin of solid floor
    for x1, z1, x2, z2 in ((21, 20, 25, 23), (6, 6, 9, 8)):
        r.fill(x1, F, z1, x2, F, z2, WATER)
        for x, z in ((x1 + 1, z1 + 1), (x2 - 1, z2 - 1), (x1 + 2, z1), (x1, z2)):
            r.set(x, F + 1, z, M("lily_pad"))
    # centre platform
    r.fill(13, F + 1, 13, 17, F + 1, 17, M("pink_terracotta"))
    r.fill(14, F + 1, 14, 16, F + 1, 16, M("cherry_planks"))
    r.fairy(15, F + 2, 15)
    for x, z in ((13, 13), (17, 13), (13, 17), (17, 17)):
        r.set(x, F + 2, z, M("pink_candle[candles=3,lit=true]"))
    # cherry trees
    for cx, cz in ((24, 7), (7, 23), (25, 14)):
        r.fill(cx, F + 1, cz, cx, F + 5, cz, M("cherry_log[axis=y]"))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy, lim in ((5, 2), (6, 5), (7, 2)):
                    if dx * dx + dz * dz <= lim and is_air(r, cx + dx, F + dy, cz + dz) \
                            and r.in_room(cx + dx, cz + dz) and 1 <= cx + dx <= 29 and 1 <= cz + dz <= 29:
                        r.set(cx + dx, F + dy, cz + dz, M("cherry_leaves[persistent=true]"))
    flowers = [M("pink_tulip"), M("allium"), M("cornflower"), M("azure_bluet"), M("lily_of_the_valley"), M("fern"),
               M("short_grass"), M("moss_carpet"), M("flowering_azalea"),
               M("pink_petals[flower_amount=3,facing=north]"), M("pink_petals[flower_amount=2,facing=east]"),
               M("pink_petals[flower_amount=4,facing=south]")]
    scatter(r, 85, lambda: r.rng.choice(flowers), on=(M("moss_block"),), avoid=lambda x, z: 13 <= x <= 17 and 13 <= z <= 17)
    for _ in range(9):
        x, z = r.rng.randint(2, 28), r.rng.randint(2, 28)
        if is_air(r, x, r.ceil - 1, z) and not lane(x, z):
            r.set(x, r.ceil - 1, z, M("spore_blossom"))
    lamps(r, LAMP_XS, LAMP_XS, kinds=(LANTERN,), drop=(1, 2, 1))
    glow(r, [(8, 8), (22, 8), (8, 22), (22, 22), (15, 4), (15, 26)], M("glowstone"))
    ribs(r, (5, 10, 20, 25), M("cherry_log[axis=y]"), LANTERN)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 4. crypt hall
# ---------------------------------------------------------------------------------------------------------------------


def coffin(r, x, z):
    r.fill(x, F + 1, z, x + 1, F + 1, z + 2, M("polished_andesite"))
    r.fill(x, F + 2, z, x + 1, F + 2, z + 1, M("stone_slab[type=bottom]"))
    r.set(x, F + 2, z + 2, M(f"skeleton_skull[rotation={r.rng.choice((0, 8))}]"))
    r.set(x + 1, F + 2, z + 2, M("candle[candles=2,lit=true]"))


def crypt_hall():
    r = make("one/crypt_hall", "one", ceil=19, plate=DB)
    r.fill(13, F, 1, 17, F, 29, DT)
    r.fill(1, F, 13, 29, F, 17, DT)
    floor_patches(r, 14)
    for x, y, z, role, star in ((8, F + 1, 7, "crypt", True), (22, F + 1, 23, "crypt", True), (15, F + 1, 8, "zombie", True),
                                (22, F + 1, 7, "skeleton", True), (8, F + 1, 23, "skeleton", True),
                                (15, F + 1, 24, "zombie", False)):
        r.mob(x, y, z, role, star)
    r.secret_chest(1, F + 1, 27, "east")
    r.secret_item(29, F + 4, 5)
    r.secret_bat(15, 14, 15)
    for x in (10, 20):
        for z in (7, 12, 18, 23):
            pillar(r, x, z, F + 1, r.ceil - 1)
    for z in (4, 8, 20, 24):
        coffin(r, 5, z)
        coffin(r, 24, z)
    # the ledge: a staircase of 1-block jumps up the east wall
    r.set(26, F + 1, 5, stairs("deepslate_brick_stairs", "east"))
    r.set(27, F + 2, 5, stairs("deepslate_brick_stairs", "east"))
    r.set(28, F + 3, 5, stairs("deepslate_brick_stairs", "east"))
    r.set(27, F + 1, 5, SB)
    r.fill(28, F + 1, 5, 28, F + 2, 5, SB)
    r.fill(29, F + 1, 3, 29, F + 3, 7, DB)
    r.fill(28, F + 3, 3, 28, F + 3, 4, DB)
    r.fill(28, F + 3, 6, 28, F + 3, 7, DB)
    # the chest's corner
    for p in ((2, F + 1, 26), (2, F + 1, 27), (2, F + 2, 27), (3, F + 1, 27), (1, F + 2, 26)):
        r.set(*p, M("cobweb"))
    skulls(r, 18, wither=.35)
    for _ in range(5):
        x, z = r.rng.randint(2, 28), r.rng.randint(2, 28)
        if not lane(x, z) and not near_marker(r, x, z, 1) and is_air(r, x, F + 1, z) and is_air(r, x, F + 2, z):
            r.set(x, F + 1, z, M("bone_block[axis=y]"))
            r.set(x, F + 2, z, M(f"skeleton_skull[rotation={r.rng.randint(0, 15)}]"))
    candles(r, 8)
    lamps(r, LAMP_XS, LAMP_XS, drop=(1, 2, 3))
    glow(r, [(15, 4), (15, 26), (4, 15), (26, 15)])
    cobwebs(r, 14)
    ribs(r, (5, 9, 21, 25), CHSB, SOUL)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 5. collapsed library
# ---------------------------------------------------------------------------------------------------------------------


def table(r, x, z):
    r.set(x, F + 1, z, M("spruce_fence"))
    r.set(x, F + 2, z, M("spruce_slab[type=bottom]"))
    r.set(x, F + 3, z, M("candle[candles=1,lit=true]"))


def library():
    r = make("one/collapsed_library", "one", ceil=22, plate=DB)
    floor_patches(r, 10, blocks=[M("coarse_dirt"), M("gravel"), M("andesite"), M("cobblestone"), M("dark_oak_planks")])
    for x, y, z, role, star in ((12, F + 1, 8, "spider", True), (18, F + 1, 22, "spider", True),
                                (4, F + 1, 22, "skeleton", True), (24, F + 1, 24, "skeleton", True),
                                (15, F + 1, 12, "zombie", True), (26, F + 1, 12, "zombie", False)):
        r.mob(x, y, z, role, star)
    r.secret_chest(2, F + 1, 2, "east")
    r.secret_item(25, F + 3, 3)
    r.secret_bat(15, 18, 10)
    # the collapsed roof: a few holes
    for cx, cz in ((24, 14), (11, 24), (16, 5)):
        r.fill(cx - 1, r.ceil, cz - 1, cx + 1, r.ceil, cz + 1, AIR)
    # shelves
    for x in (9, 21):
        for z in list(range(4, 12)) + list(range(19, 27)):
            if x == 21 and z < 8:
                continue
            h = 3 if r.rng.random() < .7 else r.rng.choice((1, 2))
            for y in range(F + 1, F + 1 + h):
                roll = r.rng.random()
                if roll < .12:
                    continue
                r.set(x, y, z, M("bookshelf") if roll < .8 else M("chiseled_bookshelf[facing=east]"))
    # the reading nook (secret chest) behind a wall of shelves
    r.fill(7, F + 1, 1, 7, F + 3, 7, M("bookshelf"))
    r.fill(1, F + 1, 7, 6, F + 3, 7, M("bookshelf"))
    for z in (2, 3):
        for y in (F + 1, F + 2):
            r.set(7, y, z, M("cobweb"))
    table(r, 4, 5)
    # the mezzanine
    r.fill(22, F + 1, 1, 29, F + 1, 6, M("bookshelf"))
    r.fill(22, F + 2, 1, 29, F + 2, 6, M("spruce_planks"))
    r.set(21, F + 1, 4, stairs("spruce_stairs", "east"))
    for z in (1, 2, 3, 5, 6):
        r.set(22, F + 3, z, M("spruce_fence"))
    for x in range(23, 30):
        r.set(x, F + 3, 6, M("spruce_fence"))
    table(r, 12, 22)
    table(r, 18, 6)
    table(r, 5, 17)
    scatter(r, 7, M("bookshelf"), region=(4, 4, 26, 26), avoid=lambda x, z: 13 <= x <= 17 or 13 <= z <= 17, clear=1)
    pile(r, 25, 26, 2)
    pile(r, 24, 14, 2)
    pile(r, 11, 24, 2)
    candles(r, 6)
    skulls(r, 4)
    lamps(r, LAMP_XS, LAMP_XS, drop=(4, 5, 6))
    glow(r, [(15, 4), (15, 26), (4, 15), (26, 15)])
    cobwebs(r, 36, 10, 17, depth=3)
    scatter(r, 14, M("cobweb"), region=(2, 2, 28, 28), clear=1, avoid=lambda x, z: 13 <= x <= 17 or 13 <= z <= 17)
    ribs(r, (5, 10, 20, 25), M("dark_oak_planks"), LANTERN)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 6. pillar pit
# ---------------------------------------------------------------------------------------------------------------------


def pillar_pit():
    r = make("one/pillar_pit", "one", ceil=22, plate=DB)
    floor_patches(r, 12, avoid=lambda x, z: 7 <= x <= 23 and 7 <= z <= 23)
    r.fill(9, F - 3, 9, 21, F, 21, AIR)               # carve y5..y8
    r.fill(9, F - 4, 9, 21, F - 4, 21, DT)            # pit floor, top at y4
    for i, z in enumerate((21, 20, 19)):             # the way out: steps rising to the south
        top = F - 1 - i
        r.fill(14, F - 3, z, 16, top, z, SB)
        r.fill(14, top, z, 16, top, z, stairs("stone_brick_stairs", "south"))
    for x, z in ((10, 10), (12, 20), (20, 10), (18, 20), (10, 20), (20, 20)):
        if r.rng.random() < .4:
            r.set(x, F - 4, z, M("gravel"))
    for x, y, z, role, star in ((15, F - 3, 12, "elite", True), (11, F - 3, 15, "zombie", True),
                                (19, F - 3, 15, "crypt", True), (5, F + 1, 10, "skeleton", True),
                                (25, F + 1, 20, "skeleton", True), (5, F + 1, 24, "spider", True)):
        r.mob(x, y, z, role, star)
    r.secret_chest(9, F - 3, 9, "east")
    r.secret_item(1, F + 4, 6)
    r.secret_bat(15, 14, 15)
    for x, z in ((11, 11), (19, 11), (11, 19), (19, 19)):
        pillar(r, x, z, F - 3, r.ceil - 1)
    for x, z in ((5, 5), (5, 25), (25, 5), (25, 25), (10, 4), (20, 4), (10, 26), (20, 26)):
        pillar(r, x, z, F + 1, r.ceil - 1)
    # guard rails of bars around the pit, open to the west and at the stairs
    for t in range(8, 23):
        r.set(t, F + 1, 8, BAR)
        r.set(22, F + 1, t, BAR)
        if not 13 <= t <= 17:
            r.set(t, F + 1, 22, BAR)
    # soul lights on the pit floor
    for x, z in ((10, 13), (20, 13), (13, 10), (17, 10)):
        r.set(x, F - 3, z, SOUL)
    # a ledge of parkour steps up the west wall
    r.set(4, F + 1, 7, stairs("stone_brick_stairs", "west"))
    r.set(3, F + 2, 7, stairs("stone_brick_stairs", "west"))
    r.set(3, F + 1, 7, SB)
    r.fill(1, F + 3, 5, 2, F + 3, 8, SB)
    skulls(r, 14, avoid=lambda x, z: 9 <= x <= 21 and 9 <= z <= 21)
    scatter(r, 8, M("bone_block[axis=y]"), avoid=lambda x, z: 8 <= x <= 22 and 8 <= z <= 22)
    candles(r, 6, avoid=lambda x, z: 8 <= x <= 22 and 8 <= z <= 22)
    lamps(r, LAMP_XS, LAMP_XS, drop=(3, 4, 5))
    glow(r, [(15, 4), (15, 26), (4, 15), (26, 15)])
    cobwebs(r, 16, 10, 17)
    ribs(r, (6, 10, 20, 24), DB, LANTERN)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 7. balcony hall
# ---------------------------------------------------------------------------------------------------------------------


def balcony_hall():
    r = make("one/balcony_hall", "one", ceil=24, plate=DT)
    r.fill(13, F, 1, 17, F, 29, DT)
    floor_patches(r, 12)
    for x, y, z, role, star in ((10, F + 7, 4, "skeleton", True), (22, F + 7, 4, "skeleton", True),
                                (15, F + 1, 21, "elite", True), (8, F + 1, 24, "zombie", True),
                                (23, F + 1, 23, "crypt", True), (25, F + 1, 14, "spider", False)):
        r.mob(x, y, z, role, star)
    r.secret_chest(1, F + 1, 6, "east")
    r.secret_item(29, F + 10, 3)
    r.secret_bat(15, 17, 18)
    # the balcony and the stairs up to it
    r.fill(1, F + 6, 1, 29, F + 6, 9, DB)
    for k, z in enumerate(range(15, 9, -1)):
        top = F + 1 + k
        r.fill(5, F + 1, z, 7, top, z, DB)
        r.fill(5, top, z, 7, top, z, stairs("deepslate_brick_stairs", "north"))
    for x in range(1, 30):
        if not 5 <= x <= 7:
            r.set(x, F + 7, 9, BAR)
    for x in (3, 9, 13, 17, 21, 27):
        pillar(r, x, 9, F + 1, F + 5, base=CHSB, shaft=DB, base_h=1)
    # lamp posts on the balcony
    for x, z in ((10, 2), (20, 2), (3, 5)):
        r.fill(x, F + 7, z, x, F + 8, z, PBB)
        r.set(x, F + 9, z, SOUL)
    # under the balcony
    for x, z in ((6, 4), (12, 5), (21, 5), (26, 4), (3, 4)):
        r.set(x, F + 5, z, LANTERN + "[hanging=true]")
    # the ledge: 1-block jumps from the balcony's east end
    r.fill(24, F + 7, 3, 24, F + 7, 3, DB)
    r.fill(25, F + 7, 3, 25, F + 8, 3, DB)
    r.fill(26, F + 7, 3, 26, F + 9, 3, DB)
    r.fill(27, F + 7, 2, 29, F + 9, 4, DB)
    # the chest's corner
    for p in ((2, F + 1, 5), (2, F + 1, 7), (1, F + 2, 5), (1, F + 2, 7), (2, F + 2, 6)):
        r.set(*p, M("cobweb"))
    skulls(r, 14, wither=.3)
    candles(r, 8)
    lamps(r, LAMP_XS, (12, 19, 25), drop=(6, 7, 8))
    glow(r, [(15, 14), (15, 26), (4, 20), (26, 20)])
    cobwebs(r, 14, 10, 15)
    ribs(r, (11, 21, 25), SB, LANTERN)
    return finish(r)


# ---------------------------------------------------------------------------------------------------------------------
# 8. flooded ossuary
# ---------------------------------------------------------------------------------------------------------------------


def ossuary():
    r = make("one/flooded_ossuary", "one", ceil=20, plate=DB)
    floor_patches(r, 10, avoid=lambda x, z: 7 <= x <= 23 and 7 <= z <= 23)
    r.fill(8, F - 2, 8, 22, F, 22, WATER)             # water y6..y8, basin floor is y5
    r.fill(8, F - 3, 8, 22, F - 3, 22, DT)
    for x, z in ((11, 11), (19, 11), (11, 19), (19, 19), (15, 10), (15, 20), (10, 15)):
        r.set(x, F - 3, z, M("sea_lantern"))
    r.fill(14, F - 2, 17, 16, F - 1, 22, SB)          # submerged causeway south
    r.fill(8, F - 2, 14, 13, F - 1, 16, SB)           # submerged causeway west
    r.fill(14, F - 2, 14, 16, F, 16, SB)              # the island, dry
    for x, z in ((14, 14), (16, 14), (14, 16), (16, 16)):
        r.set(x, F + 1, z, M("bone_block[axis=y]"))
        r.set(x, F + 2, z, M(f"wither_skeleton_skull[rotation={r.rng.randint(0, 15)}]"))
    r.set(15, F + 1, 16, M("candle[candles=3,lit=true]"))
    for x, y, z, role, star in ((5, F + 1, 6, "skeleton", True), (25, F + 1, 6, "skeleton", True),
                                (5, F + 1, 24, "crypt", True), (25, F + 1, 24, "zombie", True),
                                (6, F + 1, 15, "spider", True), (15, F + 1, 26, "zombie", False)):
        r.mob(x, y, z, role, star)
    r.secret_chest(15, F + 1, 14, "south")
    r.secret_item(29, F + 4, 27)
    r.secret_bat(15, 13, 15)
    # the ledge in the south-east corner
    r.set(26, F + 1, 27, stairs("stone_brick_stairs", "east"))
    r.set(27, F + 2, 27, stairs("stone_brick_stairs", "east"))
    r.set(28, F + 3, 27, stairs("stone_brick_stairs", "east"))
    r.set(27, F + 1, 27, SB)
    r.fill(28, F + 1, 27, 28, F + 2, 27, SB)
    r.fill(29, F + 3, 25, 29, F + 3, 29, SB)
    for x, z in ((5, 5), (25, 5), (5, 25), (25, 25), (5, 15), (25, 15)):
        pillar(r, x, z, F + 1, r.ceil - 1, base=M("bone_block[axis=y]"), shaft=CHSB)
    # bone shelves along the edges of the ring
    for x in (3, 27):
        for z in (9, 10, 20, 21):
            r.fill(x, F + 1, z, x, F + 2, z, M("bone_block[axis=y]"))
            r.set(x, F + 3, z, M(f"skeleton_skull[rotation={r.rng.randint(0, 15)}]"))
    ring = lambda x, z: 8 <= x <= 22 and 8 <= z <= 22  # noqa: E731
    skulls(r, 22, wither=.3, avoid=ring)
    scatter(r, 10, M("bone_block[axis=y]"), avoid=ring)
    candles(r, 8, avoid=ring)
    lamps(r, LAMP_XS, LAMP_XS, drop=(1, 2, 3))
    glow(r, [(15, 4), (15, 26), (4, 15), (26, 15)])
    cobwebs(r, 12, 10, 16)
    ribs(r, (9, 21, 3), DB, SOUL)
    return finish(r)


if __name__ == "__main__":
    for build in (entrance, blood, fairy, crypt_hall, library, pillar_pit, balcony_hall, ossuary):
        build()
