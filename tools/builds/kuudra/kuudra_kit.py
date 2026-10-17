"""Kuudra's Hollow: the arena template for the Kuudra fight, placed by the skyblock mod (kuudra/KuudraRun) in the
skyrpg_skyblock:kuudra dimension, whose flat generator fills every column with lava up to world y 59.

    from kuudra_kit import Arena, CENTER, ...

    arena = Arena()             # structure/kuudra/arena.nbt
    arena.set(x, y, z, block)   # template coordinates
    arena.save()                # checks THE RULES, then writes the .nbt

THE RULES (save() checks them; the fight puts things at these fixed spots):

* The template is SIZE x HEIGHT x SIZE (129 x 48 x 129) and is placed with its (0, 0, 0) at world (slot x - 64, 50, -64).
  Template y 9 is the lava's top (world 59). Only solid (non-air) blocks are written: unset cells keep the generated lava
  (y <= 9) or air (above). Never set air or any fluid. Nothing that falls (sand, gravel, concrete powder).
* The central platform: a disc of radius 13 around CENTER whose top is y PLATFORM (18); players stand at y 19. Solid
  from y 0 up to its top. The ballista stands on it within radius 4 of CENTER (decorative blocks, up to y 26); the ring
  between radius 5 and 12 on y 19..23 must stay clear (players walk and build there).
* Six islets (ISLETS): discs of radius 6, top y 16, solid from y 0. Their middles (radius 2, y 17..20) stay clear: the
  supply crates and fuel cells go there.
* A bridge from the platform to each islet, at least 3 wide, its deck rising from y 16 at the islet to y 18 at the
  platform (slabs/stairs are fine), with railings; nothing over the deck up to 3 blocks.
* KUUDRA's pit, east: nothing at all within radius 15 of KUUDRA (x, z) above y 9, from y 10 to the top. Kuudra rises
  there out of the lava.
* ARRIVAL (west edge of the platform) and CHEST (north part of the platform) must be standable and clear 3 high.
* A crater wall rings it all between radius 58 and 64 (the corners outside are yours too), y 0 up to 47.
* Light it (shroomlight, lanterns, glowstone on the platform, islets and crater wall).
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import nbt  # noqa: E402
from dungeon_kit import Piece  # noqa: E402

ROOT = HERE.parents[2]
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/structure/kuudra"
SIZE = 129
HEIGHT = 48
LAVA = 9
PLATFORM = 18
PLATFORM_R = 13
CENTER = (64, 64)
ISLET_TOP = 16
ISLET_R = 6
# The islets' middles (x, z), clockwise from the north-east (none due east: that's Kuudra's side).
ISLETS = [(round(64 + 46 * math.cos(math.radians(a))), round(64 + 46 * math.sin(math.radians(a)))) for a in (330, 30, 90, 150, 210, 270)]
KUUDRA = (100, 64)
KUUDRA_R = 15
ARRIVAL = (52, 19, 64)
CHEST = (64, 19, 54)
CRATER = (58, 64)


def dist(x1, z1, x2, z2):
    return math.hypot(x1 - x2, z1 - z2)


class Arena(Piece):
    def __init__(self):
        super().__init__("kuudra/arena", SIZE, HEIGHT, SIZE)

    def set(self, x, y, z, block, data=None):
        if not (0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE):
            return
        name = block if ":" in block.split("[")[0] else "minecraft:" + block
        if name.split("[")[0] in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
            raise ValueError(f"({x}, {y}, {z}): never set air (unset cells keep the lava or the sky)")
        Piece.set(self, x, y, z, name, data)

    def check(self):
        problems = []
        for (x, y, z), (entry, _) in self.cells.items():
            base = entry["id"]
            if base in ("minecraft:lava", "minecraft:water") or base.endswith(("sand", "gravel", "concrete_powder")):
                problems.append(f"({x}, {y}, {z}) {base} not allowed")
            if y > LAVA and dist(x, z, *KUUDRA) <= KUUDRA_R:
                problems.append(f"({x}, {y}, {z}) is in Kuudra's pit")
            d = dist(x, z, *CENTER)
            if 5 <= d <= 12 and 19 <= y <= 23:
                problems.append(f"({x}, {y}, {z}) blocks the platform's walkway")
            for ix, iz in ISLETS:
                if dist(x, z, ix, iz) <= 2 and 17 <= y <= 20:
                    problems.append(f"({x}, {y}, {z}) is on an islet's crate spot")
            for sx, sy, sz in (ARRIVAL, CHEST):
                if (x, z) == (sx, sz) and sy <= y <= sy + 2:
                    problems.append(f"({x}, {y}, {z}) blocks a spot")
        for x in range(SIZE):
            for z in range(SIZE):
                if dist(x, z, *CENTER) <= PLATFORM_R and self.get(x, PLATFORM, z) is None:
                    problems.append(f"platform hole at ({x}, {PLATFORM}, {z})")
                for ix, iz in ISLETS:
                    if dist(x, z, ix, iz) <= ISLET_R - 1 and self.get(x, ISLET_TOP, z) is None:
                        problems.append(f"islet hole at ({x}, {ISLET_TOP}, {z})")
        for sx, sy, sz in (ARRIVAL, CHEST):
            if self.get(sx, sy - 1, sz) is None:
                problems.append(f"nothing to stand on at {(sx, sy, sz)}")
        return problems

    def save(self):
        problems = self.check()
        if problems:
            raise SystemExit("kuudra arena: " + "; ".join(problems[:12]) + (f" (+{len(problems) - 12} more)" if len(problems) > 12 else ""))
        OUT.mkdir(parents=True, exist_ok=True)
        out = OUT / "arena.nbt"
        nbt.write_file(out, self.to_nbt())
        print(f"kuudra arena: {len(self.cells)} blocks -> {out.relative_to(ROOT)}")
        return out
