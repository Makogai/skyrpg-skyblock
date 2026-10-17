"""Catacombs boss arenas, set B: Livid (F5), Sadan (F6), Necron (F7).  Run from the repo root:

    python tools\\builds\\catacombs\\arenas_b.py

Everything is seeded per arena, so reruns give identical files.  The fight area (x/z 5..55, y 9..22) is never touched:
decoration lives in the 4-block ring, on the floor surface (y 8) and above y 22.
"""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from arena_kit import Arena, FLOOR, MID, SIZE, HEIGHT  # noqa: E402

M = "minecraft:"
N = SIZE - 1          # 60
TOP = HEIGHT - 2      # 28, the lining under the solid ceiling at y 29
CHAIN = M + "iron_chain"
GRID = (10, 20, 30, 40, 50)
RING_Y1, RING_Y2 = FLOOR + 1, TOP


# -- helpers --------------------------------------------------------------------------------------------------------
def paint_floor(a, fn):
    for x in range(1, N):
        for z in range(1, N):
            a.set(x, FLOOR, z, fn(x, z))


def paint_ceiling(a, fn):
    for x in range(1, N):
        for z in range(1, N):
            a.set(x, TOP, z, fn(x, z))


def paint_walls(a, fn):
    """Lines the inside of the four outer walls (the layer next to the solid outer face), y 9..28."""
    for y in range(RING_Y1, TOP + 1):
        for i in range(1, N):
            for x, z in ((1, i), (N - 1, i), (i, 1), (i, N - 1)):
                a.set(x, y, z, fn(x, y, z, i))


def hang(a, x, z, length, lantern="lantern", top=TOP):
    """A chain from `top` down `length` blocks with a hanging lantern below it (the lantern stays at y >= 23)."""
    for y in range(top, top - length, -1):
        a.set(x, y, z, CHAIN)
    a.set(x, top - length, z, f"{M}{lantern}[hanging=true]")


def hang_grid(a, lantern="lantern", beam=None, beam_block=None):
    """Beams across the ceiling (y 27..28) on the grid lines 10/30/50, lanterns hanging at every grid crossing/midpoint."""
    if beam_block:
        for line in (10, 30, 50):
            for i in range(1, N):
                for y in (TOP - 1, TOP):
                    a.set(i, y, line, beam_block)
                    a.set(line, y, i, beam_block)
    for x in GRID:
        for z in GRID:
            if x in (10, 30, 50) or z in (10, 30, 50):
                hang(a, x, z, 3, lantern, top=TOP - 2)


def floor_lights(a, block, skip=((MID, MID),)):
    for x in GRID:
        for z in GRID:
            if (x, z) not in skip:
                a.set(x, FLOOR, z, block)


def column(a, cx, cz, y1, y2, fn, half=1):
    """A (2*half+1)-square pillar centred on cx, cz; fn(y, dx, dz) picks each block."""
    for y in range(y1, y2 + 1):
        for dx in range(-half, half + 1):
            for dz in range(-half, half + 1):
                a.set(cx + dx, y, cz + dz, fn(y, dx, dz))


def stairs(block, facing, half="bottom"):
    return f"{M}{block}[facing={facing},half={half}]"


def skull(rot, kind="wither_skeleton_skull"):
    return f"{M}{kind}[rotation={rot}]"


# =====================================================================================================================
def livid():
    a = Arena("livid")
    rng = random.Random("livid-b")
    a.shell(wall=M + "deepslate_tiles", floor=M + "polished_blackstone", ceiling=M + "deepslate_tiles")

    # floor: dark checker, grey diamond + square rings, plain centre
    def floor(x, z):
        dx, dz = abs(x - MID), abs(z - MID)
        d, m = max(dx, dz), dx + dz
        if d == 0:
            return M + "black_concrete"            # the mod swaps (30, 8, 30) for coloured wool
        if d == 1:
            return M + "gray_concrete"
        if m == 18 or m == 19 or d in (12, 24):
            return M + "gray_concrete"
        if d in (13, 25) or (m in (9, 10) and d < 9):
            return M + "smooth_basalt"
        if (x + z) % 2:
            return M + "deepslate_tiles"
        return M + "polished_blackstone_bricks" if rng.random() > 0.1 else M + "cracked_polished_blackstone_bricks"
    paint_floor(a, floor)
    floor_lights(a, M + "sea_lantern")

    # walls: dark bands with grey stripes
    def wall(x, y, z, i):
        if y in (12, 20, 27):
            return M + "gray_concrete"
        if y in (11, 19, 26):
            return M + "black_concrete"
        return M + ("polished_blackstone_bricks" if (i // 3) % 2 else "deepslate_tiles")
    paint_walls(a, wall)

    paint_ceiling(a, lambda x, z: M + ("black_concrete" if (x // 5 + z // 5) % 2 else "deepslate_tiles"))
    hang_grid(a, "soul_lantern", beam_block=M + "blackstone")

    # nine coloured pillars around the ring
    spots = [("north", 12), ("north", 30), ("north", 48), ("east", 18), ("east", 42),
             ("south", 48), ("south", 12), ("west", 42), ("west", 18)]
    colors = ["red", "orange", "yellow", "lime", "cyan", "blue", "purple", "pink", "white"]
    for (side, pos), color in zip(spots, colors):
        cx, cz = {"north": (pos, 3), "south": (pos, 57), "west": (3, pos), "east": (57, pos)}[side]
        fx, fz = {"north": (0, 1), "south": (0, -1), "west": (1, 0), "east": (-1, 0)}[side]  # towards the arena

        def block(y, dx, dz, fx=fx, fz=fz, color=color):
            if y in (9, 10):
                return M + "polished_blackstone"
            if y in (TOP - 1, TOP):
                return M + "chiseled_polished_blackstone"
            if y % 6 == 0:
                return M + "chiseled_polished_blackstone"
            front = dx == fx and dz == fz if (fx or fz) else False
            if dx * fx + dz * fz == 1 and dx * fz == 0 and dz * fx == 0:   # the centre of the face towards the arena
                return f"{M}{color}_wool"
            return M + "polished_blackstone_bricks"
        column(a, cx, cz, 9, TOP, block)
        # glow at the head of the banner, a band of the colour near the top
        a.set(cx + fx, 22, cz + fz, f"{M}{color}_stained_glass")
        a.set(cx + fx, 23, cz + fz, M + "sea_lantern")
        a.set(cx + fx, 9, cz + fz, f"{M}{color}_concrete")
        # coloured cap under the ceiling
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx * fx + dz * fz >= 0 and not (dx == 0 and dz == 0):
                    pass
        a.set(cx, TOP - 2, cz, f"{M}{color}_concrete")

    a.weather("livid", {M + "polished_blackstone_bricks": [(M + "cracked_polished_blackstone_bricks", 0.1)],
                        M + "deepslate_tiles": [(M + "cracked_deepslate_tiles", 0.1)]})
    return a


# =====================================================================================================================
def sadan():
    a = Arena("sadan")
    rng = random.Random("sadan-b")
    a.shell(wall=M + "smooth_sandstone", floor=M + "cut_sandstone", ceiling=M + "cut_sandstone")
    cycle = [M + "yellow_terracotta", M + "orange_terracotta", M + "red_terracotta", M + "terracotta",
             M + "brown_terracotta", M + "white_terracotta", M + "light_gray_terracotta", M + "magenta_terracotta"]

    def floor(x, z):
        dx, dz = abs(x - MID), abs(z - MID)
        d, m = max(dx, dz), dx + dz
        if m <= 2:
            return M + "gold_block"
        if dx == dz:
            return M + "chiseled_sandstone"
        if dx == 0 or dz == 0:
            return M + "cut_sandstone"
        if d % 6 == 0:
            return M + "smooth_sandstone"
        return cycle[(d // 3) % len(cycle)]
    paint_floor(a, floor)
    floor_lights(a, M + "glowstone")

    def wall(x, y, z, i):
        if y in (12, 24):
            return M + "gold_block" if i % 6 == 0 else M + "chiseled_sandstone"
        if y in (11, 23, 28):
            return M + "cut_sandstone"
        return cycle[(i // 4 + y // 3) % len(cycle)]
    paint_walls(a, wall)

    paint_ceiling(a, lambda x, z: cycle[((x // 4) + (z // 4)) % len(cycle)] if (x + z) % 2 else M + "cut_sandstone")
    hang_grid(a, "lantern", beam_block=M + "smooth_sandstone")
    for line in (10, 30, 50):          # gold studs along the beams
        for i in range(5, N, 10):
            a.set(i, TOP - 1, line, M + "gold_block")
            a.set(line, TOP - 1, i, M + "gold_block")

    # sandstone columns with terracotta bands along the walls
    def col(y, dx, dz):
        if y in (9, 10):
            return M + "cut_sandstone"
        if y in (12, 24, TOP, TOP - 1):
            return M + "gold_block" if (dx == 0 or dz == 0) else M + "chiseled_sandstone"
        if y in (16, 17, 20, 21):
            return cycle[(y // 2) % 3]
        if abs(dx) + abs(dz) == 2:
            return M + "smooth_sandstone"
        return M + "chiseled_sandstone" if y % 4 == 0 else M + "cut_sandstone"
    for x in (14, 46):
        column(a, x, 3, 9, TOP, col)
        column(a, x, 57, 9, TOP, col)
    for z in (14, 30, 46):
        column(a, 3, z, 9, TOP, col)
        column(a, 57, z, 9, TOP, col)

    # four giant statues in the corner squares
    def statue(x0, z0, tint):
        ix = x0 + 3 if x0 < MID else x0
        iz = z0 + 3 if z0 < MID else z0
        for dx in range(4):
            for dz in range(4):
                x, z = x0 + dx, z0 + dz
                a.set(x, 9, z, M + "smooth_sandstone")
                a.set(x, 10, z, M + "chiseled_sandstone" if (dx in (0, 3) and dz in (0, 3)) else M + "cut_sandstone")
                for y in (11, 12):
                    a.set(x, y, z, M + "cut_sandstone")              # feet
                for y in range(13, 21):
                    a.set(x, y, z, tint if y != 14 else M + "gold_block")   # legs/torso with a gold belt
                for y in (21, 22):
                    a.set(x, y, z, M + "smooth_sandstone")           # shoulders
        for y in range(15, 21):                                     # gold chest plate towards the arena
            for k in (1, 2):
                a.set(ix, y, z0 + k, M + "gold_block")
                a.set(x0 + k, y, iz, M + "gold_block")
        for dx in (1, 2):                                           # head, eyes, crown
            for dz in (1, 2):
                for y in (23, 24, 25):
                    a.set(x0 + dx, y, z0 + dz, M + "chiseled_sandstone")
                a.set(x0 + dx, 26, z0 + dz, M + "gold_block")
                for y in (27, 28):
                    a.set(x0 + dx, y, z0 + dz, M + "gold_block" if (dx + dz) % 2 else M + "red_terracotta")
        for k in (1, 2):
            a.set(ix, 24, z0 + k, M + "shroomlight")
            a.set(x0 + k, 24, iz, M + "shroomlight")
        # spear: a gold shaft on the outer side
        for y in range(11, 21):
            a.set(x0 + (0 if x0 < MID else 3), y, z0 + (0 if z0 < MID else 3), M + "gold_block" if y % 4 == 0 else M + "chiseled_sandstone")
    statue(1, 1, M + "orange_terracotta")
    statue(56, 1, M + "red_terracotta")
    statue(1, 56, M + "yellow_terracotta")
    statue(56, 56, M + "brown_terracotta")

    # the throne at the north wall
    for x in range(22, 39):
        for z in range(1, 4):
            a.set(x, 9, z, M + "smooth_sandstone" if (x + z) % 2 else M + "cut_sandstone")
        a.set(x, 9, 4, stairs("cut_sandstone_stairs", "north"))
        a.set(x, 10, 4, M + "air")
    for x in range(22, 39):                                   # gold trim along the dais edge
        if x % 4 == 0:
            a.set(x, 9, 3, M + "gold_block")
    for y in range(10, 25):                                   # tall back of the throne
        half_w = 3 if y < 20 else (2 if y < 23 else 1)
        for x in range(MID - half_w, MID + half_w + 1):
            edge = abs(x - MID) == half_w
            a.set(x, y, 1, M + "gold_block" if edge or y in (14, 19) else M + "red_terracotta")
            a.set(x, y, 2, M + "cut_sandstone") if y >= 11 and abs(x - MID) >= 2 and y < 16 else None
    for x in (MID - 2, MID + 2):                              # armrests
        a.set(x, 10, 2, M + "gold_block")
        a.set(x, 11, 2, M + "soul_lantern")
    a.set(MID, 10, 2, stairs("red_sandstone_stairs", "north"))
    a.set(MID - 1, 10, 2, M + "red_terracotta")
    a.set(MID + 1, 10, 2, M + "red_terracotta")
    for x in (MID - 6, MID + 6):                              # flanking braziers
        column(a, x, 2, 10, 14, lambda y, dx, dz: M + ("gold_block" if y == 10 else "chiseled_sandstone"), half=0)
        a.set(x, 15, 2, M + "shroomlight")

    a.weather("sadan", {M + "cut_sandstone": [(M + "smooth_sandstone", 0.06)]})
    return a


# =====================================================================================================================
def necron():
    a = Arena("necron")
    rng = random.Random("necron-b")
    a.shell(wall=M + "nether_bricks", floor=M + "blackstone", ceiling=M + "polished_blackstone_bricks")
    pads = [(15, 15), (45, 15), (15, 45), (45, 45)]       # the four lords' spots

    def floor(x, z):
        dx, dz = abs(x - MID), abs(z - MID)
        r2 = dx * dx + dz * dz
        for px, pz in pads:
            pd = abs(x - px) + abs(z - pz)
            if pd == 0:
                return M + "gold_block"
            if pd <= 2:
                return M + "crying_obsidian"
            if pd == 3:
                return M + "chiseled_nether_bricks"
        if r2 <= 4:
            return M + "gold_block"
        if r2 <= 36:
            return M + ("soul_sand" if (x + z) % 2 else "soul_soil")
        if 64 <= r2 <= 81:
            return M + "crying_obsidian"
        if x % 10 == 0 or z % 10 == 0:
            return M + "soul_soil"
        if dx == dz:
            return M + "polished_blackstone_bricks"
        return M + ("blackstone" if (x + z) % 2 else ("nether_bricks" if rng.random() < 0.5 else "polished_blackstone_bricks"))
    paint_floor(a, floor)
    floor_lights(a, M + "shroomlight", skip=tuple(pads) + ((MID, MID),))

    def wall(x, y, z, i):
        if y in (14, 22):
            return M + "chiseled_nether_bricks"
        if y in (13, 21) and i % 5 == 0:
            return M + "crying_obsidian"
        return M + ("polished_blackstone_bricks" if (i // 4 + y // 4) % 3 == 0 else "nether_bricks")
    paint_walls(a, wall)

    paint_ceiling(a, lambda x, z: M + ("blackstone" if (x + z) % 3 else "polished_blackstone_bricks"))
    hang_grid(a, "soul_lantern", beam_block=M + "polished_blackstone_bricks")
    for line in (10, 30, 50):      # purple/gold studs on the beams
        for i in range(5, N, 10):
            a.set(i, TOP - 1, line, M + "crying_obsidian")
            a.set(line, TOP - 1, i, M + "gold_block")
    for _ in range(45):             # ragged chains hanging all over (above y 22 only)
        x, z = rng.randint(1, N - 1), rng.randint(1, N - 1)
        if a.get(x, TOP - 1, z) in (CHAIN.replace("minecraft:", "minecraft:"),) or (a.get(x, TOP - 1, z) or "").endswith("lantern"):
            continue
        if a.get(x, TOP - 1, z) not in (None, M + "air"):
            continue
        for y in range(TOP - 1, TOP - 1 - rng.randint(1, 4), -1):
            a.set(x, y, z, CHAIN)

    # four corner towers (towards the arena: ledge with wither skulls, soul lanterns under the overhang)
    def tower(x0, z0):
        ix = x0 + 3 if x0 < MID else x0
        iz = z0 + 3 if z0 < MID else z0
        outer_x = range(x0, x0 + 3) if x0 < MID else range(x0 + 1, x0 + 4)
        outer_z = range(z0, z0 + 3) if z0 < MID else range(z0 + 1, z0 + 4)
        gx = x0 + 2 if x0 < MID else x0 + 1
        gz = z0 + 2 if z0 < MID else z0 + 1
        for x in range(x0, x0 + 4):
            for z in range(z0, z0 + 4):
                a.set(x, 9, z, M + "polished_blackstone_bricks")
                for y in range(10, 17):
                    on_l = x == ix or z == iz
                    a.set(x, y, z, M + ("blackstone" if y in (10, 11) else "nether_bricks"))
                    if y == 16:
                        a.set(x, y, z, M + ("gold_block" if on_l else "chiseled_nether_bricks"))
                for y in range(17, 23):
                    if x in outer_x and z in outer_z:
                        a.set(x, y, z, M + "nether_bricks" if y % 3 else M + "chiseled_nether_bricks")
                for y in range(23, TOP + 1):
                    on_l = x == ix or z == iz
                    a.set(x, y, z, M + ("crying_obsidian" if (y == 23 and on_l) else "polished_blackstone_bricks"))
        for k in outer_z:                                         # windows in the narrow tier
            for y in (19, 20):
                a.set(gx, y, k, M + "purple_stained_glass")
        for k in outer_x:
            for y in (19, 20):
                a.set(k, y, gz, M + "purple_stained_glass")
        l_cells = [(ix, z) for z in range(z0, z0 + 4)] + [(x, iz) for x in range(x0, x0 + 4) if x != ix]
        for n, (x, z) in enumerate(l_cells):
            a.set(x, 17, z, skull((n * 5) % 16))
            a.set(x, 22, z, M + "soul_lantern[hanging=true]")
        for y in (12, 13):                                        # purple glow slits on the lower tier
            for k in (1, 2):
                a.set(ix, y, z0 + k, M + "purple_stained_glass")
                a.set(x0 + k, y, iz, M + "purple_stained_glass")
    tower(1, 1)
    tower(56, 1)
    tower(1, 56)
    tower(56, 56)

    # crumbling wall pillars of varying height, cracked, with skulls and rubble
    spots = [(x, 3) for x in (14, 30, 46)] + [(x, 57) for x in (14, 46)] + \
            [(3, z) for z in (14, 30, 46)] + [(57, z) for z in (14, 30, 46)]
    for cx, cz in spots:
        h = rng.randint(11, 19)
        top_y = 8 + h
        for y in range(9, top_y + 1):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if y >= top_y - 1 and rng.random() < 0.4:
                        continue
                    if y in (9, 10):
                        b = "polished_blackstone_bricks"
                    elif y % 5 == 0:
                        b = "gold_block" if (dx == 0 and dz == 0) or abs(dx) + abs(dz) == 1 else "chiseled_nether_bricks"
                    else:
                        b = rng.choice(("nether_bricks", "nether_bricks", "cracked_nether_bricks", "blackstone"))
                    a.set(cx + dx, y, cz + dz, M + b)
        if rng.random() < 0.7 and a.get(cx, top_y, cz):
            a.set(cx, top_y + 1, cz, skull(rng.randint(0, 15)))
        a.set(cx, 9, cz + (1 if cz < MID else -1) * 0, M + "polished_blackstone_bricks")
        # lantern shining from the pillar's arena face
        fx = 1 if cx == 3 else (-1 if cx == 57 else 0)
        fz = 1 if cz == 3 else (-1 if cz == 57 else 0)
        a.set(cx + fx, 8 + min(h, 9), cz + fz, M + "soul_lantern")

    a.weather("necron", {M + "nether_bricks": [(M + "cracked_nether_bricks", 0.2), (M + "red_nether_bricks", 0.05)],
                         M + "polished_blackstone_bricks": [(M + "cracked_polished_blackstone_bricks", 0.2)]})
    return a


if __name__ == "__main__":
    for build in (livid, sadan, necron):
        build().save()
