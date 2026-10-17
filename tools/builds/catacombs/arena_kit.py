"""Boss arenas for the Catacombs: one template per floor's boss, placed beside the map by the skyblock mod (BossFight).

    from arena_kit import Arena, FLOOR, MID

    arena = Arena("bonzo")                 # structure/catacombs/boss/bonzo.nbt
    arena.shell()                          # walls, floor, ceiling, air inside
    arena.fill(...)                        # decoration (only where THE RULES allow)
    arena.save()                           # checks THE RULES, then writes the .nbt

THE RULES (save() checks them; the fights put things at fixed spots, so the middle must stay open):

* Size 61 x 30 x 61 (x, y, z). FLOOR = 8: the floor's top block is at y 8 everywhere in the fight area, players stand at 9.
* The fight area is x 5..55, z 5..55 (MID = 30 is the middle). In it, y 9..22 must be air (bosses, giants 12 high,
  floating withers and ghasts, crystals and pads all go there), and y 8 solid floor (any solid block, patterns welcome).
  Small things ON the floor (carpets, pressure-plate-like flat blocks) are fine only outside x/z 20..40.
* Players arrive at (30, 9, 55), facing north: keep (30, 9..11, 55) and around it clear and floored.
* Everything outside the fight area (the 5-block ring around it, and above y 22) is yours for walls, balconies, pillars,
  lights, banners, statues. The outer faces (x 0, x 60, z 0, z 60), y 0 and y 29 are solid.
* Light it well (the fight area too, from walls and ceiling: lanterns hung at y 23+, glowstone/shroomlight in the ceiling).
* Every block of the box is set (unset becomes air). No entities, no liquids that can flow, no redstone, no falling blocks.
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import nbt  # noqa: E402
from dungeon_kit import Piece  # noqa: E402

ROOT = HERE.parents[2]
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/structure/catacombs/boss"
SIZE = 61
HEIGHT = 30
FLOOR = 8
MID = 30
LOW, HIGH = 5, 55          # the fight area (inclusive)
OPEN_TOP = 22              # air from y 9 to this in the fight area
BOSSES = ("bonzo", "scarf", "professor", "thorn", "livid", "sadan", "necron")


class Arena(Piece):
    def __init__(self, boss):
        if boss not in BOSSES:
            raise ValueError(f"unknown boss {boss}; one of {BOSSES}")
        super().__init__(boss, SIZE, HEIGHT, SIZE)
        self.boss = boss

    def shell(self, wall="minecraft:polished_blackstone_bricks", floor="minecraft:polished_blackstone_bricks",
              ceiling="minecraft:polished_blackstone_bricks", base="minecraft:bedrock"):
        for x in range(SIZE):
            for z in range(SIZE):
                self.set(x, 0, z, base)
                edge = x in (0, SIZE - 1) or z in (0, SIZE - 1)
                for y in range(1, HEIGHT):
                    if edge or y == HEIGHT - 1:
                        block = ceiling if y == HEIGHT - 1 else wall
                    elif y <= FLOOR:
                        block = floor
                    else:
                        block = "minecraft:air"
                    self.set(x, y, z, block)

    def check(self):
        problems = []
        for x in range(LOW, HIGH + 1):
            for z in range(LOW, HIGH + 1):
                if self.get(x, FLOOR, z) in (None, "minecraft:air"):
                    problems.append(f"no floor at ({x}, {FLOOR}, {z})")
                for y in range(FLOOR + 1, OPEN_TOP + 1):
                    block = self.get(x, y, z)
                    if block in (None, "minecraft:air"):
                        continue
                    flat = block.endswith("_carpet") or block.endswith("_pressure_plate")
                    if not (flat and y == FLOOR + 1 and not (20 <= x <= 40 and 20 <= z <= 40)):
                        problems.append(f"({x}, {y}, {z}) {block} is in the fight area")
        for x in range(SIZE):
            for z in range(SIZE):
                for y in (0, HEIGHT - 1):
                    if self.get(x, y, z) in (None, "minecraft:air"):
                        problems.append(f"({x}, {y}, {z}) should be solid")
        return problems

    def save(self):
        for x in range(SIZE):
            for y in range(HEIGHT):
                for z in range(SIZE):
                    if self.get(x, y, z) is None:
                        Piece.set(self, x, y, z, "minecraft:air")
        problems = self.check()
        if problems:
            raise SystemExit(f"{self.boss}: " + "; ".join(problems[:12]) + (f" (+{len(problems) - 12} more)" if len(problems) > 12 else ""))
        out = OUT / (self.boss + ".nbt")
        out.parent.mkdir(parents=True, exist_ok=True)
        nbt.write_file(out, self.to_nbt())
        print(f"{self.boss}: arena -> {out.relative_to(ROOT)}")
        return out
