"""The Garden's Barn plot: the middle plot of every player's Garden, placed by the skyblock mod (garden/Garden) in the
skyrpg_skyblock:garden dimension, whose flat generator makes plain grass with its top block at world y 64.

    from garden_kit import Barn, ...

    barn = Barn()               # structure/garden/barn.nbt
    barn.set(x, y, z, block)    # template coordinates
    barn.save()                 # checks THE RULES, then writes the .nbt

THE RULES (save() checks them; the mod puts things at these fixed spots):

* The template is SIZE x HEIGHT x SIZE (96 x 40 x 96), placed with its (0, 0, 0) at world (centre x - 48, 60, centre z - 48).
  GROUND = 4 is the grass level (world y 64); players stand at y 5. Below y 4 is dirt (the generator's); you may set
  blocks there (foundations, a cellar) but the plot's edge (x or z = 0 or 95) must stay plain grass at y 4 with nothing
  above it, so the plot meets the plots around it.
* Only set what you build; unset cells keep the generated dirt / grass / air. You may set air to carve.
* The fixed spots (players stand there; keep them clear 3 high and standable): ARRIVAL (where /warp garden lands),
  DESK (in front of the Desk block, which is at DESK_BLOCK), COMPOSTER (in front of the Composter machine, which fills
  COMPOSTER_BOX), SKYMART (the SkyMart NPC), PHILLIP (Pesthunter Phillip), and VISITORS (five spots where visitors
  stand, in a fenced yard; each clear 3 high).
* Nothing that flows (water, lava) unless fully enclosed; no entities.
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import nbt  # noqa: E402
from dungeon_kit import Piece  # noqa: E402

ROOT = HERE.parents[2]
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/structure/garden"
SIZE = 96
HEIGHT = 40
GROUND = 4
ARRIVAL = (48, 5, 88)
DESK_BLOCK = (48, 5, 30)
DESK = (48, 5, 32)
COMPOSTER_BOX = ((64, 5, 40), (68, 8, 44))
COMPOSTER = (66, 5, 46)
SKYMART = (30, 5, 46)
PHILLIP = (34, 5, 58)
VISITORS = [(40, 5, 66), (44, 5, 68), (48, 5, 69), (52, 5, 68), (56, 5, 66)]


class Barn(Piece):
    def __init__(self):
        super().__init__("garden/barn", SIZE, HEIGHT, SIZE)

    def set(self, x, y, z, block, data=None):
        if 0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE:
            Piece.set(self, x, y, z, block if ":" in block.split("[")[0] else "minecraft:" + block, data)

    def check(self):
        problems = []
        for (x, y, z), (entry, _) in self.cells.items():
            base = entry["id"]
            if base in ("minecraft:water", "minecraft:lava"):
                problems.append(f"({x}, {y}, {z}) {base}: no fluids")
            if (x in (0, SIZE - 1) or z in (0, SIZE - 1)) and y > GROUND and base != "minecraft:air":
                problems.append(f"({x}, {y}, {z}) {base} on the plot's edge")
        spots = [ARRIVAL, DESK, COMPOSTER, SKYMART, PHILLIP] + VISITORS
        for sx, sy, sz in spots:
            for y in range(sy, sy + 3):
                b = self.get(sx, y, sz)
                if b not in (None, "minecraft:air"):
                    problems.append(f"spot ({sx}, {sy}, {sz}) blocked at y {y} by {b}")
            below = self.get(sx, sy - 1, sz)
            if below in ("minecraft:air",):
                problems.append(f"nothing to stand on at ({sx}, {sy}, {sz})")
        if self.get(*DESK_BLOCK) in (None, "minecraft:air"):
            problems.append("no Desk block at DESK_BLOCK")
        return problems

    def save(self):
        problems = self.check()
        if problems:
            raise SystemExit("garden barn: " + "; ".join(problems[:12]) + (f" (+{len(problems) - 12} more)" if len(problems) > 12 else ""))
        OUT.mkdir(parents=True, exist_ok=True)
        out = OUT / "barn.nbt"
        nbt.write_file(out, self.to_nbt())
        print(f"garden barn: {len(self.cells)} blocks -> {out.relative_to(ROOT)}")
        return out
