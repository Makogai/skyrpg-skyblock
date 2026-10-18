#!/usr/bin/env python3
"""The Rift, the Mirrorverse (Hypixel's): Dr. Emmett's eight rooms, a pocket far west of the Rift (x -604..-410, y 90..141,
z -130..-110) reached through the door in West Village's courtyard (the mod teleports you; Rift Time stands still inside).

Every room is split down its length by a glass "mirror" at z = ZC: you play on the north half (z < ZC), the south half
(z > ZC) is the reflection, and the reflection shows what your side hides. The mod reads the answers by reflecting your
position across the glass (z -> 2 * ZC - z):

    1 levers     flip your four levers to match the mirror's
    2 lava       your floor is blank; the mirror shows the lava and the iron path through it
    3 crafting   the room's mobs are invisible on your side; the mirror shows them (and the crafting table, invisible
                 on your side too). Their drops craft the remmaH yniT, which opens the door
    4 platforms  crimson plank platforms over lava: the mirror shows only the safe ones (the others vanish under you)
    5 switching  click the glass to swap sides; walls and lava block one side or the other. A lava pit near the end
                 hides the Big Brain Talisman in a pocket on the mirror side (switch mid-jump)
    6 dance      four coloured tiles; do what Dr. Emmett calls (7 steps open the door, 12 earn the Tiny Dancer)
    7 climb      an invisible staircase on your side (barriers), planks in the mirror; three checkpoints (levers at
                 the bottom) and a Miniaturized Tubulator for climbing it in one go
    8 finale     Dr. Emmett and his reflection, Ttemme .rD, behind the broken mirror; the portal home

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_mirrorverse.py
"""
import random

from kit import hash01
from rift_lib import new_area

area = new_area("mirrorverse", version=2)
area.shell = 0
S = area.set

ZC = -120                   # the mirror's plane
F = 100                     # floor y (players stand at F + 1)
HALF = 7                    # each side is 7 wide: z ZC-7..ZC-1 (yours) and ZC+1..ZC+7 (the mirror's)
X_START = -600
ROOMS = [("levers", 14, 8), ("lava", 22, 8), ("crafting", 16, 8), ("platforms", 24, 9), ("switching", 40, 8),
         ("dance", 15, 9), ("climb", 13, 38), ("finale", 15, 9)]

RX = []
x = X_START
for _, length, _ in ROOMS:
    RX.append(x)
    x += length + 4


def mz(z):
    """The mirror image of z."""
    return 2 * ZC - z


def both(x, y, d, block, mirror_block=None):
    """A block at depth d (1..7) from the glass on your side, and its reflection."""
    S(x, y, ZC - d, block)
    S(x, y, ZC + d, mirror_block or block)


def shell(i):
    name, length, height = ROOMS[i]
    rx = RX[i]
    x0, x1 = rx - 1, rx + length
    for x in range(x0, x1 + 1):
        for z in range(ZC - HALF - 1, ZC + HALF + 2):
            wall_x = x in (x0, x1)
            wall_z = z in (ZC - HALF - 1, ZC + HALF + 1)
            S(x, F - 1, z, "minecraft:white_concrete")
            S(x, F, z, "minecraft:quartz_block" if z == ZC or wall_x or wall_z else
              ("minecraft:smooth_quartz" if (x + z) % 2 else "minecraft:quartz_bricks"))
            ceiling = F + height
            lamp = (x - rx) % 4 == 2 and abs(z - ZC) == 4
            S(x, ceiling, z, "minecraft:sea_lantern" if lamp else "minecraft:white_concrete")
            for y in range(F + 1, ceiling):
                if wall_x or wall_z:
                    if y == F + 1:
                        S(x, y, z, "minecraft:light_gray_concrete")
                    elif y == ceiling - 1:
                        S(x, y, z, "minecraft:cyan_concrete")
                    else:
                        S(x, y, z, "minecraft:white_concrete")
                elif z == ZC:
                    # the mirror: glass between quartz pillars
                    S(x, y, z, "minecraft:quartz_pillar[axis=y]" if (x - rx) % 6 == 0 else "minecraft:glass")
                else:
                    area.clear(x, y, z)
    # the exit door (east wall, your side) and the way in (west wall)
    for z in (ZC - 5, ZC - 3):
        for y in range(F + 1, F + 4):
            S(x1, y, z, "minecraft:quartz_pillar[axis=y]")
    S(x1, F + 1, ZC - 4, "minecraft:iron_door[half=lower,facing=west,hinge=left,open=false]")
    S(x1, F + 2, ZC - 4, "minecraft:iron_door[half=upper,facing=west,hinge=left,open=false]")
    S(x1, F + 4, ZC - 4, "minecraft:cyan_stained_glass")
    # the door's reflection
    S(x1, F + 1, ZC + 4, "minecraft:iron_door[half=lower,facing=west,hinge=left,open=false]")
    S(x1, F + 2, ZC + 4, "minecraft:iron_door[half=upper,facing=west,hinge=left,open=false]")
    area.npc(f"spawn:mv_start_{i + 1}", rx + 1, F + 1, ZC - 4, -90.0, f"room {i + 1} ({name}): where you start")
    area.npc(f"spawn:mv_end_{i + 1}", rx + length - 1, F + 1, ZC - 4, -90.0, f"room {i + 1}: in front of the exit door")
    if i < 7:
        area.npc(f"Dr. Emmett (Mirrorverse {i + 1})", rx, F + 1, ZC - HALF, -45.0, f"room {i + 1}'s corner by the way in")


for i in range(len(ROOMS)):
    shell(i)
area.npc("spawn:mv_plane", RX[0] + 1, F + 1, ZC, 0.0, "on the mirror's plane (z = ZC)")

# ---------------------------------------------------------------- 1 levers
rx = RX[0]
PATTERN = [True, False, True, True]
for k, on in enumerate(PATTERN):
    x = rx + 3 + 2 * k
    S(x, F + 2, ZC - HALF - 1, "minecraft:polished_andesite")
    S(x, F + 2, ZC + HALF + 1, "minecraft:polished_andesite")
    S(x, F + 2, ZC - HALF, "minecraft:lever[face=wall,facing=south,powered=false]")
    S(x, F + 2, ZC + HALF, f"minecraft:lever[face=wall,facing=north,powered={'true' if on else 'false'}]")
    both(x, F + 4, HALF + 1, "minecraft:cyan_terracotta")

# ---------------------------------------------------------------- 2 lava: the path only the mirror shows
rx, length = RX[1], ROOMS[1][1]
rng = random.Random(7)
path = set()
d, x = 4, rx + 3
while x <= rx + length - 4:
    path.add((x, d))
    if rng.random() < 0.5 and x < rx + length - 5:
        nd = rng.choice([v for v in range(1, HALF + 1) if abs(v - d) >= 2])
        step = 1 if nd > d else -1
        for dd in range(d, nd + step, step):
            path.add((x, dd))
        d = nd
    x += 1
for x in range(rx + 3, rx + length - 3):
    for d in range(1, HALF + 1):
        S(x, F, ZC + d, "minecraft:iron_block" if (x, d) in path else "minecraft:lava")
        S(x, F, ZC - d, "minecraft:smooth_quartz")
for d in range(1, HALF + 1):              # the safe strips at each end
    for x in (rx, rx + 1, rx + 2, rx + length - 3, rx + length - 2, rx + length - 1):
        both(x, F, d, "minecraft:polished_diorite")

# ---------------------------------------------------------------- 3 crafting: an invisible table
rx, length = RX[2], ROOMS[2][1]
S(rx + 12, F + 1, ZC + 3, "minecraft:crafting_table")
S(rx + 12, F + 1, ZC - 3, "minecraft:barrier")
area.npc("spawn:mv_table", rx + 12, F + 1, ZC - 3, 0.0, "the crafting table: invisible (a barrier) on your side")
for x in range(rx + 1, rx + length - 1, 3):          # shelves along the outer walls, on both sides
    for y in (F + 1, F + 2):
        both(x, y, HALF, "minecraft:bookshelf")
    both(x, F + 3, HALF, "minecraft:white_candle[candles=3,lit=true]")
area.npc("spawn:mv_mobs", rx + 6, F + 1, ZC - 4, 0.0, "the middle of the room: the invisible mobs wander here")

# ---------------------------------------------------------------- 4 platforms over lava
rx, length = RX[3], ROOMS[3][1]
SAFE = [0, 1, 1, 0, 1, 0]                 # which row is safe in each column (0: next to the outer wall, 1: by the glass)
ROWS = [(6, 7), (2, 3)]
for x in range(rx + 3, rx + length - 3):
    for d in range(1, HALF + 1):
        both(x, F, d, "minecraft:lava")
for c, safe in enumerate(SAFE):
    x0 = rx + 3 + 3 * c
    for r, (da, db) in enumerate(ROWS):
        for x in (x0, x0 + 1):
            for d in (da, db):
                # crimson planks: lava sets oak on fire
                S(x, F, ZC - d, "minecraft:crimson_planks")
                if r == safe:
                    S(x, F, ZC + d, "minecraft:crimson_planks")
for d in range(1, HALF + 1):
    for x in (rx, rx + 1, rx + 2, rx + length - 3, rx + length - 2, rx + length - 1):
        both(x, F, d, "minecraft:polished_diorite")

# ---------------------------------------------------------------- 5 switching
rx, length, height = RX[4], ROOMS[4][1], ROOMS[4][2]
SIDE = {"P": -1, "M": 1}


def side_wall(xo, side):
    for d in range(1, HALF + 1):
        for y in range(F + 1, F + height):
            S(rx + xo, y, ZC + SIDE[side] * d, "minecraft:light_gray_concrete" if (y + d) % 3 else "minecraft:cyan_terracotta")


def side_lava(xa, xb, side):
    for xo in range(xa, xb + 1):
        for d in range(1, HALF + 1):
            S(rx + xo, F, ZC + SIDE[side] * d, "minecraft:lava")


side_wall(6, "P")
side_wall(11, "M")
side_lava(16, 18, "P")
side_wall(22, "M")
side_wall(27, "P")
side_lava(31, 33, "M")
side_lava(36, 38, "P")                    # jump it to reach the door, or switch mid-jump into the pocket below
side_wall(35, "M")
side_wall(39, "M")
S(rx + 37, F + 1, ZC + 4, "minecraft:gold_block")
area.npc("spawn:mv_brain", rx + 37, F + 2, ZC + 4, 0.0, "the Big Brain Talisman: on a gold block in the mirror's pocket")
area.npc("spawn:mv_after_pit", rx + 39, F + 1, ZC - 4, -90.0, "your side, past the last lava pit")

# ---------------------------------------------------------------- 6 dance floor
rx, length, height = RX[5], ROOMS[5][1], ROOMS[5][2]
TILES = {"blue": (3, 5), "red": (8, 5), "pink": (3, 1), "lime": (8, 1)}     # colour: (x offset, first depth)
for colour, (xo, d0) in TILES.items():
    for x in range(rx + xo, rx + xo + 3):
        for d in range(d0, d0 + 3):
            both(x, F, d, f"minecraft:{colour}_concrete")
for x in range(rx, rx + length):
    for z in range(ZC - HALF, ZC + HALF + 1):
        if z != ZC and hash01(x, 7, z, 3) < 0.25:
            S(x, F + height, z, f"minecraft:{['magenta', 'light_blue', 'yellow', 'lime'][int(hash01(x, 8, z, 3) * 4)]}_stained_glass")
area.npc("spawn:mv_dance", rx + 7, F + 1, ZC - 4, -90.0, "the middle of the dance floor")

# ---------------------------------------------------------------- 7 the invisible climb
rx, length, height = RX[6], ROOMS[6][1], ROOMS[6][2]
rng = random.Random(11)
TOP = F + 31
steps = []
x, d, y = rx + 3, 4, F + 1
used = set()
while y < TOP:
    steps.append((x, y, d))
    used.add((x, d))
    y += 1
    for _ in range(80):
        dx, dd = rng.choice([(2, 0), (-2, 0), (0, 2), (0, -2), (1, 1), (1, -1), (-1, 1), (-1, -1), (2, 1), (1, 2), (-2, 1), (2, -1)])
        nx, nd = x + dx, d + dd
        # stay inside, never straight above an earlier step within 3 blocks (your head), drift east near the top
        if not (rx + 1 <= nx <= rx + length - 4 and 1 <= nd <= HALF):
            continue
        if any(sx == nx and sd == nd and 0 < y - sy <= 3 for (sx, sy, sd) in steps):
            continue
        if y > TOP - 6 and nx < x:
            continue
        x, d = nx, nd
        break
CHECKPOINTS = {8: 1, 16: 2, 24: 3}
for k, (x, y, d) in enumerate(steps):
    cells = [(x, d)]
    if k in CHECKPOINTS:
        cells += [(x + 1, d), (x, d + 1 if d < HALF else d - 1), (x + 1, d + 1 if d < HALF else d - 1)]
        area.npc(f"spawn:mv_cp_{CHECKPOINTS[k]}", x, y + 1, ZC - d, 0.0, f"checkpoint {CHECKPOINTS[k]} (on the climb)")
        S(x, y + 1, ZC + d, "minecraft:lantern[hanging=false]")
    for (cx, cd) in cells:
        S(cx, y, ZC - cd, "minecraft:barrier")
        S(cx, y, ZC + cd, "minecraft:spruce_planks" if k in CHECKPOINTS else "minecraft:oak_planks")
# the landing at the top, and the door out (moved up the east wall)
lx, ly, ld = steps[-1]
for x in range(rx + length - 3, rx + length):
    for d in range(1, HALF + 1):
        both(x, TOP, d, "minecraft:quartz_slab[type=top]")
for x in range(lx + 1, rx + length - 3):       # a bridge from the last step to the landing (if it falls short)
    S(x, TOP, ZC - ld, "minecraft:barrier")
    S(x, TOP, ZC + ld, "minecraft:oak_planks")
x1 = rx + length
for y in range(F + 1, F + 5):
    for z in (ZC - 5, ZC - 4, ZC - 3, ZC + 4):
        S(x1, y, z, "minecraft:white_concrete" if y > F + 1 else "minecraft:light_gray_concrete")
for z in (ZC - 5, ZC - 3):
    for y in range(TOP + 1, TOP + 4):
        S(x1, y, z, "minecraft:quartz_pillar[axis=y]")
S(x1, TOP + 1, ZC - 4, "minecraft:iron_door[half=lower,facing=west,hinge=left,open=false]")
S(x1, TOP + 2, ZC - 4, "minecraft:iron_door[half=upper,facing=west,hinge=left,open=false]")
area.npc("spawn:mv_top", rx + length - 1, TOP + 1, ZC - 4, -90.0, "room 7: the landing at the top, in front of the door")
for k, d in enumerate((2, 4, 6)):                  # the checkpoint levers, on the west wall at the bottom
    S(rx - 1, F + 2, ZC - d, "minecraft:polished_andesite")
    S(rx, F + 2, ZC - d, "minecraft:lever[face=wall,facing=east,powered=false]")
    S(rx, F + 3, ZC - d, f"minecraft:{['lime', 'yellow', 'red'][k]}_stained_glass")

# ---------------------------------------------------------------- 8 the finale
rx, length, height = RX[7], ROOMS[7][1], ROOMS[7][2]
for x in range(rx + 6, rx + 9):                    # the broken mirror: a gap you can walk through
    for y in range(F + 1, F + 4):
        area.clear(x, y, ZC)
    S(x, F + 4, ZC, "minecraft:glass")
for (x, y) in ((rx + 5, F + 1), (rx + 9, F + 2), (rx + 5, F + 3)):
    S(x, y, ZC, "minecraft:tinted_glass")
area.npc("Dr. Emmett (Mirrorverse)", rx + 7, F + 1, ZC - 4, 180.0, "the finale: in the middle of your side")
area.npc("ttemme .rD", rx + 7, F + 1, ZC + 4, 0.0, "his reflection, on the mirror's side (through the broken mirror)")
x1 = rx + length
for z in range(ZC - 6, ZC - 1):                     # the portal home in the east wall
    for y in range(F + 1, F + 6):
        frame = z in (ZC - 6, ZC - 2) or y == F + 5
        S(x1, y, z, "minecraft:crying_obsidian" if frame else "minecraft:purple_stained_glass")
area.npc("spawn:mv_portal", rx + length - 1, F + 1, ZC - 4, -90.0, "the portal home (east wall)")

area.zone("Mirrorverse", box=((X_START - 4, F - 8, ZC - HALF - 2), (RX[-1] + ROOMS[-1][1] + 2, F + 42, ZC + HALF + 2)))
area.save()
print("rooms", list(zip([r[0] for r in ROOMS], RX)), "lava path", len(path), "climb steps", len(steps), "top", (lx, ly, ld))
