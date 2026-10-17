"""Rooms for the Catacombs (Hypixel's Dungeons): one template per room, placed by the skyblock mod's dungeon code.

    from room_kit import Room

    room = Room("one/crypt_hall", "one")           # path under structure/catacombs/rooms/, and the room's shape
    room.shell()                                    # outer walls, floor, ceiling; everything else air
    room.fill(10, FLOOR + 1, 10, 12, FLOOR + 3, 12, "minecraft:chiseled_stone_bricks")
    room.mob(15, FLOOR + 1, 8, "zombie", star=True)
    room.secret_chest(4, FLOOR + 1, 4, "south")
    room.save()                                     # checks the rules below, then writes the .nbt

Writes skyblock/src/main/resources/data/skyrpg_skyblock/structure/catacombs/rooms/<path>.nbt.

THE RULES (save() checks them):

* A cell is 32 blocks; a room is its cells × 32 - 1 wide (a 1×1 room is 31 × 31). Height is 32 (y 0..31). Shapes, drawn
  in this orientation (the mod turns them to fit the map):
      one    1×1  31 × 31          two  1×2  63 × 31 (long along x)     three 1×3  95 × 31     four 1×4  127 × 31
      square 2×2  63 × 63          l    L    63 × 63, cells (0,0), (1,0), (0,1): x 32..62 / z 32..62 is NOT part of it
  Special rooms are 1×1: entrance, blood, fairy (and later puzzle_*, trap_*).
* FLOOR = 8: the floor's top block is at y 8, players walk at y 9. Rooms may go down to y 1 (pits) and up to y 30.
* The outside of the room (x 0, x max, z 0, z max of each cell edge on the room's boundary) is wall from y 0 to 31,
  and y 31 is the ceiling. y 0 is solid everywhere.
* Door spots: the middle of every cell edge on the boundary, 3 wide (cells 14, 15, 16 of that edge) and 4 tall
  (y 9..12). Leave them as wall: the mod cuts the doors it uses. In front of each door spot, inside the room, keep a
  3 × 4 opening clear for 3 blocks, with floor under it at y 8.
* Every block of the room is set (anything not built becomes air), so a new room replaces whatever was there.
* Markers (invisible; the mod reads them): mob(), secret_chest(), secret_item(), secret_bat(), key_spot(), and the
  special rooms' own (entrance: spawn(); blood: watcher(); fairy: fairy()).
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import nbt  # noqa: E402
from dungeon_kit import Piece, state, _nbt_value  # noqa: E402

ROOT = HERE.parents[2]
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/structure/catacombs/rooms"
FLOOR = 8
HEIGHT = 32
CELL = 32
TAG = "skyrpg_catacombs"
SHAPES = {
    "one": [(0, 0)], "two": [(0, 0), (1, 0)], "three": [(0, 0), (1, 0), (2, 0)], "four": [(0, 0), (1, 0), (2, 0), (3, 0)],
    "square": [(0, 0), (1, 0), (0, 1), (1, 1)], "l": [(0, 0), (1, 0), (0, 1)],
    "entrance": [(0, 0)], "blood": [(0, 0)], "fairy": [(0, 0)],
}
# What a mob marker's role becomes depends on the floor (the mod picks the mob): zombie (melee undead), skeleton
# (archer), crypt (crypt undead, melee), spider, elite (a stronger one, at most one a room).
ROLES = ("zombie", "skeleton", "crypt", "spider", "elite")


class Room(Piece):
    def __init__(self, path, shape):
        if shape not in SHAPES:
            raise ValueError(f"unknown shape {shape}")
        self.shape = shape
        self.cells_of = SHAPES[shape]
        w = max(c[0] for c in self.cells_of) + 1
        d = max(c[1] for c in self.cells_of) + 1
        super().__init__(path, w * CELL - 1, HEIGHT, d * CELL - 1)
        self.special = shape in ("entrance", "blood", "fairy")
        self.markers = []

    # -- where things are -------------------------------------------------------------------------------------------
    def in_room(self, x, z):
        """Inside the room: a cell's 31 × 31, or the 1-block gap between two of its own cells (the L's corner isn't)."""
        if not (0 <= x < self.size[0] and 0 <= z < self.size[2]):
            return False
        cx, rx = divmod(x, CELL)
        cz, rz = divmod(z, CELL)
        xs = [cx] if rx < CELL - 1 else [cx, cx + 1]
        zs = [cz] if rz < CELL - 1 else [cz, cz + 1]
        return all((a, b) in self.cells_of for a in xs for b in zs)

    def boundary(self, x, z):
        """True on the room's outer wall."""
        if not self.in_room(x, z):
            return False
        return any(not self.in_room(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    def door_spots(self):
        """Every door spot: (cells of the opening as (x, z) list, inward direction (dx, dz))."""
        spots = []
        for cx, cz in self.cells_of:
            x0, z0 = cx * CELL, cz * CELL
            for (dx, dz) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                if (cx + dx, cz + dz) in self.cells_of:
                    continue
                if dz == -1:
                    cells, inward = [(x0 + i, z0) for i in (14, 15, 16)], (0, 1)
                elif dz == 1:
                    cells, inward = [(x0 + i, z0 + 30) for i in (14, 15, 16)], (0, -1)
                elif dx == -1:
                    cells, inward = [(x0, z0 + i) for i in (14, 15, 16)], (1, 0)
                else:
                    cells, inward = [(x0 + 30, z0 + i) for i in (14, 15, 16)], (-1, 0)
                spots.append((cells, inward))
        return spots

    # -- building ---------------------------------------------------------------------------------------------------
    def shell(self, wall="minecraft:stone_bricks", floor="minecraft:stone_bricks", ceiling="minecraft:stone_bricks",
              base="minecraft:bedrock"):
        """The outer wall, the floor (y 1..8), the ceiling (y 31) and air inside."""
        w, h, d = self.size
        for x in range(w):
            for z in range(d):
                if not self.in_room(x, z):
                    continue
                self.set(x, 0, z, base)
                for y in range(1, HEIGHT):
                    if self.boundary(x, z) or y == HEIGHT - 1:
                        block = wall if y < HEIGHT - 1 else ceiling
                    elif y <= FLOOR:
                        block = floor
                    else:
                        block = "minecraft:air"
                    self.set(x, y, z, block)

    def set(self, x, y, z, block, nbt_data=None):
        if not self.in_room(x, z):
            raise ValueError(f"{self.path}: ({x}, {y}, {z}) is outside the room's cells")
        super().set(x, y, z, block, nbt_data)

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    if self.in_room(x, z):
                        self.set(x, y, z, block)

    def _marker(self, x, y, z, kind, **data):
        if not self.in_room(x, z) or not 0 <= y < HEIGHT:
            raise ValueError(f"{self.path}: {kind} at ({x}, {y}, {z}) is outside the room")
        payload = {"catacombs": kind}
        payload.update(data)
        pos = [x + 0.5, float(y), z + 0.5]
        self.entities.append({"pos": pos, "blockPos": [x, y, z],
                              "nbt": {"id": "minecraft:marker", "Pos": list(pos), "Tags": [TAG], "data": _nbt_value(payload)}})
        self.markers.append((kind, x, y, z, data))

    def mob(self, x, y, z, role, star=True):
        """A dungeon mob (role: zombie, skeleton, crypt, spider, elite). Star mobs must die for the room to be cleared."""
        if role not in ROLES:
            raise ValueError(f"role {role} isn't one of {ROLES}")
        self._marker(x, y, z, "mob", role=role, star=bool(star))

    def secret_chest(self, x, y, z, facing):
        """A secret chest (a chest block here, and a marker): finding it counts for the score."""
        self.set(x, y, z, f"minecraft:chest[facing={facing}]")
        self._marker(x, y, z, "secret_chest")

    def secret_item(self, x, y, z):
        """A secret item lying here (the mod puts it there)."""
        self._marker(x, y, z, "secret_item")

    def secret_bat(self, x, y, z):
        """A secret bat flutters here."""
        self._marker(x, y, z, "secret_bat")

    def key_spot(self, x, y, z):
        """Where a Wither or Blood Key appears in this room, if it has one (else the middle of the room)."""
        self._marker(x, y, z, "key")

    def spawn(self, x, y, z):
        """Entrance only: where the party arrives."""
        self._marker(x, y, z, "spawn")

    def watcher(self, x, y, z):
        """Blood room only: where the Watcher floats."""
        self._marker(x, y, z, "watcher")

    def fairy(self, x, y, z):
        """Fairy room only: where the fairy is."""
        self._marker(x, y, z, "fairy")

    # -- checking and saving ----------------------------------------------------------------------------------------
    def check(self):
        problems = []
        w, h, d = self.size
        for x in range(w):
            for z in range(d):
                if not self.in_room(x, z):
                    continue
                if self.get(x, 0, z) in (None, "minecraft:air"):
                    problems.append(f"no solid base at ({x}, 0, {z})")
                    break
        for cells, (ix, iz) in self.door_spots():
            for (x, z) in cells:
                for y in range(FLOOR + 1, FLOOR + 5):
                    if self.get(x, y, z) in (None, "minecraft:air"):
                        problems.append(f"door spot ({x}, {y}, {z}) is open: leave door spots as wall")
                for step in (1, 2, 3):
                    fx, fz = x + ix * step, z + iz * step
                    if self.get(fx, FLOOR, fz) in (None, "minecraft:air"):
                        problems.append(f"no floor in front of the door at ({fx}, {FLOOR}, {fz})")
                    for y in range(FLOOR + 1, FLOOR + 5):
                        if self.get(fx, y, fz) not in (None, "minecraft:air"):
                            problems.append(f"({fx}, {y}, {fz}) blocks the way in from a door")
        kinds = [m[0] for m in self.markers]
        if self.shape == "entrance" and "spawn" not in kinds:
            problems.append("the entrance needs a spawn()")
        if self.shape == "blood" and "watcher" not in kinds:
            problems.append("the blood room needs a watcher()")
        if self.shape == "fairy" and "fairy" not in kinds:
            problems.append("the fairy room needs a fairy()")
        if not self.special and not any(m[0] == "mob" and m[4].get("star") for m in self.markers):
            problems.append("a normal room needs at least one star mob")
        if sum(1 for m in self.markers if m[0] == "mob" and m[4].get("role") == "elite") > 1:
            problems.append("at most one elite a room")
        return problems

    def save(self):
        # Anything not built is air, so the room replaces whatever was there before.
        w, h, d = self.size
        for x in range(w):
            for z in range(d):
                if self.in_room(x, z):
                    for y in range(h):
                        if self.get(x, y, z) is None:
                            Piece.set(self, x, y, z, "minecraft:air")
        problems = self.check()
        if problems:
            raise SystemExit(f"{self.path}: " + "; ".join(problems[:12]) + (f" (+{len(problems) - 12} more)" if len(problems) > 12 else ""))
        out = OUT / (self.path + ".nbt")
        out.parent.mkdir(parents=True, exist_ok=True)
        nbt.write_file(out, self.to_nbt())
        print(f"{self.path}: {self.shape} {w}x{h}x{d}, {len(self.markers)} markers -> {out.relative_to(ROOT)}")
        return out
