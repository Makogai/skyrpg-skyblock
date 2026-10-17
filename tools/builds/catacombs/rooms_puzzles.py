"""The Catacombs puzzle rooms: Three Weirdos, Blaze and Tic Tac Toe (1x1 rooms with one puzzle() marker in the middle).

    python tools/builds/catacombs/rooms_puzzles.py        (from the repo root)

The mod builds each puzzle around the marker, so the clear space PUZZLES gives stays empty. Seeded per room path.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from room_kit import FLOOR  # noqa: E402
from rooms_core import (make, finish, lane, lamps, glow, skulls, candles, cobwebs, ribs, floor_patches, pillar,  # noqa: E402
                        M, F, SB, CHSB, DB, DT, PBB, NB, RNB, LANTERN, SOUL)

PB = M("polished_blackstone")
BS = M("blackstone")
CGB = M("gilded_blackstone")
MAGMA = M("magma_block")
RWOOL = M("red_wool")
GOLD = M("gold_block")


def in_sq(x, z, cx, cz, rad):
    return abs(x - cx) <= rad and abs(z - cz) <= rad


def nc(rad):
    return lambda x, z: in_sq(x, z, 15, 15, rad)


def corner_pillars(r, pts, h, base=CHSB, shaft=SB, lamp=SOUL):
    for x, z in pts:
        pillar(r, x, z, F + 1, F + h, base=base, shaft=shaft)
        r.set(x, F + h + 1, z, lamp)


def weirdos():
    r = make("puzzle/three_weirdos", "puzzle", ceil=18, plate=DB)
    r.puzzle(15, F + 1, 15, "weirdos")
    floor_patches(r, 8, avoid=lambda x, z: lane(x, z) or in_sq(x, z, 15, 15, 9))
    # stage: a patterned floor with a gilded border (floor level, so nothing is in the way)
    for x in range(9, 22):
        for z in range(9, 22):
            d = max(abs(x - 15), abs(z - 15))
            r.set(x, F, z, {6: CGB, 5: PB}.get(d, DT if (x + z) % 2 else PBB))
    # curtain along the north wall either side of the door, with trim and lanterns
    for x0, x1 in ((5, 12), (18, 25)):
        r.fill(x0, F + 1, 1, x1, F + 6, 1, RWOOL)
        r.fill(x0, F + 7, 1, x1, F + 7, 1, GOLD)
    for x0, x1 in ((5, 12), (18, 25)):
        for x in range(x0, x1 + 1, 2):
            r.set(x, F + 5, 2, DB)
            r.set(x, F + 4, 2, (LANTERN if x % 4 else SOUL) + "[hanging=true]")
    # banners as blocks on the west and east walls
    for z in (8, 22):
        for x in (1, 29):
            r.fill(x, F + 2, z - 1, x, F + 6, z + 1, M("purple_wool" if z == 8 else "red_wool"))
            r.set(x, F + 7, z, GOLD)
    corner_pillars(r, [(8, 8), (22, 8), (8, 22), (22, 22)], 5, lamp=LANTERN)
    r.secret_chest(2, F + 1, 28, "east")
    ribs(r, (7, 23), DB, SOUL, h=2)
    lamps(r, (5, 11, 19, 25), (5, 25))
    glow(r, [(15, 8), (15, 22)])
    skulls(r, 8, avoid=nc(10))
    candles(r, 6, avoid=nc(10))
    cobwebs(r, 18)
    return finish(r)


def blaze():
    r = make("puzzle/blaze", "puzzle", wall=PBB, floor=DB, ceiling=PBB, ceil=27, plate=PBB)
    r.puzzle(15, F + 1, 15, "blaze")
    # round-ish chamber: fill the four corners (octagon)
    for x in range(1, 30):
        for z in range(1, 30):
            if abs(x - 15) + abs(z - 15) > 22 and not lane(x, z):
                r.fill(x, F + 1, z, x, 26, z, BS if (x + z) % 3 else PBB)
    # floor: concentric rings, nether-brick accents and a few magma blocks at the edge (outside the 15 x 15 square)
    for x in range(1, 30):
        for z in range(1, 30):
            d = max(abs(x - 15), abs(z - 15))
            if lane(x, z):
                continue
            if d <= 7:
                r.set(x, F, z, {7: RNB, 6: NB}.get(d, DT if (x + z) % 2 else PBB))
            elif (x * 7 + z * 13) % 11 == 0 and not in_sq(x, z, 15, 15, 8):
                r.set(x, F, z, MAGMA)
    # soul fire on nether-brick pedestals at the walls
    for x, z in ((4, 8), (4, 22), (26, 8), (26, 22), (8, 4), (22, 4), (8, 26), (22, 26)):
        if r.get(x, F + 1, z) not in (None, M("air")) or lane(x, z) or in_sq(x, z, 15, 15, 7):
            continue
        r.set(x, F, z, NB)
        r.fill(x, F + 1, z, x, F + 1, z, NB)
        r.set(x, F + 2, z, M("soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"))
    ribs(r, (6, 24), NB, SOUL, h=5)
    lamps(r, (5, 15, 25), (5, 15, 25), kinds=(SOUL, LANTERN), drop=(2, 4, 3))
    glow(r, [(9, 9), (21, 9), (9, 21), (21, 21)])
    skulls(r, 6, wither=.7, avoid=nc(8))
    cobwebs(r, 10, ymin=10, ymax=14)
    return finish(r)


def tic_tac_toe():
    r = make("puzzle/tic_tac_toe", "puzzle", ceil=20, plate=DB)
    r.puzzle(15, F + 1, 15, "tictactoe")
    floor_patches(r, 8, avoid=lambda x, z: lane(x, z) or in_sq(x, z, 15, 15, 7))
    # board frame: chiseled border just outside the 3 x 3, a deepslate-tile apron beyond it
    for x in range(8, 23):
        for z in range(8, 23):
            d = max(abs(x - 15), abs(z - 15))
            if d == 4:
                r.set(x, F, z, CHSB)
            elif d in (5, 6):
                r.set(x, F, z, DT if d == 5 else PBB)
            elif d == 7:
                r.set(x, F, z, DB)
    # pillars outside the clear space
    corner_pillars(r, [(8, 8), (22, 8), (8, 22), (22, 22), (4, 15), (26, 15)], 6)
    r.secret_chest(28, F + 1, 2, "west")
    ribs(r, (7, 23), DB, LANTERN, h=2)
    lamps(r, (5, 11, 19, 25), (5, 25, 15), kinds=(LANTERN, SOUL))
    glow(r, [(15, 8), (15, 22), (8, 15), (22, 15)])
    skulls(r, 8, avoid=nc(4))
    candles(r, 5, avoid=nc(4))
    cobwebs(r, 18)
    return finish(r)


if __name__ == "__main__":
    weirdos()
    blaze()
    tic_tac_toe()
