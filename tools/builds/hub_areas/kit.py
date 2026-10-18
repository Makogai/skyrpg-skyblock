"""Building blocks for the Hub's areas (Hypixel's Graveyard, Coal Mine, Farm, Forest...): floating islands around
Skyhaven, joined to it by sky bridges. Each area script builds one structure in world coordinates and writes:

    hub/src/main/resources/data/skyrpg_hub/structure/areas/<id>.nbt     the build
    hub/src/main/resources/data/skyrpg_hub/skyrpg/hub_areas/<id>.json   where it goes and its version (HubAreas)
    skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/zones/<id>.json   what can be mined/chopped, the mobs
    tools/builds/hub_areas/out/<id>_spots.json                         NPC spots and Fairy Souls, for later steps
    tools/builds/hub_areas/out/<id>_top.png, <id>_side.png             previews (top-down and from the south)

Usage from an area script:

    from kit import Area
    area = Area("graveyard", (-240, 24, -110), (150, 96, 150), version=1)
    area.island(-165, -30, 50, top=64, seed=3, surface=area.mix({"podzol": 3, "coarse_dirt": 2, "moss_block": 1}))
    area.bridge((-57, -16), (-112, -22), y=64)
    ...
    area.zone("Graveyard", blocks={}, mobs=[...])
    area.save()

Coordinates are world coordinates; anything outside the box is skipped. The village's own build (-96..95 in x and z)
is never overwritten except where a bridge starts on its edge (see HubAreas).
"""
import json
import math
import re
import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE.parent))
import dungeon_kit  # noqa: E402
import nbt  # noqa: E402

STRUCTURES = ROOT / "hub/src/main/resources/data/skyrpg_hub/structure/areas"
AREAS = ROOT / "hub/src/main/resources/data/skyrpg_hub/skyrpg/hub_areas"
ZONES = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/zones"
OUT = HERE / "out"
DIM = "skyrpg_hub:hub"
VILLAGE = ((-96, -96), (95, 95))   # x/z of the village build; areas only add bridge ends inside it


# Ids that changed name in 26.x: the old one would place nothing.
RENAMED = {"minecraft:chain": "minecraft:iron_chain", "minecraft:grass": "minecraft:short_grass"}
GAME_JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-merged.jar"


def mc(block):
    block = block if ":" in block.split("[")[0] else "minecraft:" + block
    name, bracket, rest = block.partition("[")
    return RENAMED[name] + bracket + rest if name in RENAMED else block


def known_blocks():
    """Every block id the game has (from its blockstates), cached in out/blocks.txt; None if the jar isn't there."""
    cache = OUT / "blocks.txt"
    if cache.exists():
        return set(cache.read_text(encoding="utf-8").split())
    if not GAME_JAR.exists():
        return None
    import zipfile
    with zipfile.ZipFile(GAME_JAR) as jar:
        ids = sorted("minecraft:" + n[len("assets/minecraft/blockstates/"):-5] for n in jar.namelist()
                     if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json"))
    OUT.mkdir(parents=True, exist_ok=True)
    cache.write_text("\n".join(ids) + "\n", encoding="utf-8")
    return set(ids)


def noise(x, z, seed=0, scale=1.0):
    """Smooth-ish value noise in -1..1 from a few sines (no dependencies)."""
    s = seed * 1.7
    return (math.sin(x * 0.083 * scale + s) * math.cos(z * 0.071 * scale + s * 0.6)
            + 0.6 * math.sin((x + z) * 0.047 * scale + s * 1.3)
            + 0.4 * math.sin(x * 0.19 * scale - z * 0.17 * scale + s * 2.1)
            + 0.25 * math.cos(x * 0.31 * scale + z * 0.29 * scale - s)) / 2.25


def hash01(x, y, z, seed=0):
    h = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * 2654435761) ^ 0x5bd1e995) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 0x5bd1e995) & 0xFFFFFFFF
    return ((h ^ (h >> 15)) & 0xFFFF) / 0xFFFF


# Generated worlds: unset cells in an area's box would keep the generated terrain, so they're saved as air.
FILL_AIR = {"skyrpg_skyblock:crystal_hollows", "skyrpg_skyblock:glacite_tunnels"}


class Area:
    def __init__(self, area_id, origin, size, version=1, shell=1, dimension=DIM, island=""):
        self.id = area_id
        self.dimension = dimension   # another public island: its own dimension (the Hub's type), e.g. skyrpg_skyblock:the_park
        self.island_name = island    # its name outside the zones ("The Farming Islands")
        self.arrival_spot = None
        self.shell = shell     # how deep under any surface blocks are kept (hollow); 0 keeps everything
        self.origin = origin
        self.size = size
        self.version = version
        self.piece = dungeon_kit.Piece("areas/" + area_id, *size)
        self.top = {}          # (x, z) -> surface y (the block players stand on top of)
        self.spots = {"dimension": dimension, "island": island or "Hub", "npcs": {}, "fairy_souls": [], "warps": {}, "pads": [],
                      "notes": []}
        self.zone_json = None
        self.zones = []           # every zone this area defines (the first is zone_json)

    # ---------------------------------------------------------------- blocks (world coordinates)
    def inside(self, x, y, z):
        ox, oy, oz = self.origin
        return self.piece.inside(x - ox, y - oy, z - oz)

    def set(self, x, y, z, block, data=None):
        ox, oy, oz = self.origin
        if self.piece.inside(x - ox, y - oy, z - oz):
            self.piece.set(x - ox, y - oy, z - oz, mc(block), data)

    def get(self, x, y, z):
        ox, oy, oz = self.origin
        if not self.piece.inside(x - ox, y - oy, z - oz):
            return None
        return self.piece.get(x - ox, y - oy, z - oz)

    def clear(self, x, y, z):
        """Removes a block this build set (so it isn't placed at all)."""
        ox, oy, oz = self.origin
        self.piece.cells.pop((x - ox, y - oy, z - oz), None)

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, block)

    def solid(self, x, y, z):
        b = self.get(x, y, z)
        return b is not None and b != "minecraft:air" and not b.endswith(("_leaves", "short_grass", "fern", "flower", "vine"))

    def mix(self, weights):
        """A block picker from weights: {"podzol": 3, "coarse_dirt": 1} -> f(x, y, z)."""
        items = [(mc(k), w) for k, w in weights.items()]
        total = sum(w for _, w in items)

        def pick(x, y, z, seed=0):
            r = hash01(x, y, z, seed + 17) * total
            for block, w in items:
                r -= w
                if r <= 0:
                    return block
            return items[-1][0]
        return pick

    # ---------------------------------------------------------------- terrain
    def island(self, cx, cz, radius, top=64, seed=0, surface=None, hills=3.0, depth=None, wobble=0.22,
               dirt="dirt", stone=None, edge_drop=1, shape=None):
        """A floating island: a noisy round top at about `top` (hills up to `hills` blocks), dirt under the surface,
        and a rocky underside tapering down `depth` blocks (default radius * 0.7). `shape` (x, z) -> radius factor
        lets a script stretch it. Returns the set of columns it covers."""
        surface = surface or (lambda x, y, z, s=0: "minecraft:grass_block")
        stone = stone or self.mix({"stone": 6, "andesite": 3, "tuff": 2, "cobblestone": 1})
        depth = depth or radius * 0.7
        cols = set()
        r_max = int(radius * (1 + wobble) + 3)
        for x in range(cx - r_max, cx + r_max + 1):
            for z in range(cz - r_max, cz + r_max + 1):
                angle = math.atan2(z - cz, x - cx)
                r = radius * (1 + wobble * (0.6 * math.sin(3 * angle + seed) + 0.4 * math.sin(5 * angle + seed * 2.3))
                              + 0.08 * noise(x, z, seed, 2))
                if shape:
                    r *= shape(x, z)
                d = math.hypot(x - cx, z - cz) / max(1e-6, r)
                if d > 1:
                    continue
                cols.add((x, z))
                rise = hills * (noise(x, z, seed + 5, 0.9) * 0.5 + 0.5) * min(1.0, (1 - d) * 3)
                y_top = int(round(top + rise - (edge_drop if d > 0.93 else 0)))
                self.set(x, y_top, z, surface(x, y_top, z))
                for y in range(y_top - 1, y_top - 4, -1):
                    self.set(x, y, z, mc(dirt))
                bottom = y_top - 4 - int(depth * (1 - d * d) ** 0.55 + 3 * noise(x, z, seed + 9, 1.7) * (1 - d))
                for y in range(y_top - 4, bottom - 1, -1):
                    deep = (y_top - y) / max(1.0, depth)
                    if deep > 0.75:
                        block = "minecraft:deepslate" if hash01(x, y, z, seed) < 0.6 else "minecraft:tuff"
                    else:
                        block = stone(x, y, z)
                    self.set(x, y, z, block)
                # Stalactites under the thick middle, roots and vines at the rim.
                if d < 0.6 and hash01(x, 0, z, seed + 3) < 0.035:
                    length = 2 + int(hash01(x, 1, z, seed) * 5)
                    for i in range(length):
                        self.set(x, bottom - 1 - i, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" +
                                 ("tip" if i == length - 1 else "frustum" if i == length - 2 else "middle" if i else "base") + "]")
                elif d > 0.85 and hash01(x, 2, z, seed + 4) < 0.08:
                    for i in range(1, 2 + int(hash01(x, 3, z, seed) * 4)):
                        if self.get(x, y_top - 3 - i, z) in (None, "minecraft:air"):
                            self.set(x, y_top - 3 - i, z, "minecraft:hanging_roots")
                            break
                self.top[(x, z)] = y_top
        return cols

    def surface_y(self, x, z):
        return self.top.get((x, z))

    def ground(self, x, z):
        """The y a player stands at on this column (one above the surface), or None."""
        y = self.top.get((x, z))
        return None if y is None else y + 1

    def flatten(self, x1, z1, x2, z2, y, block=None):
        """Levels a rectangle of the island to surface y (cuts hills, fills dips)."""
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                old = self.top.get((x, z))
                if old is None:
                    continue
                surf = self.get(x, old, z)
                for yy in range(y + 1, old + 1):
                    self.set(x, yy, z, "minecraft:air")
                for yy in range(old, y):
                    self.set(x, yy, z, "minecraft:dirt")
                self.set(x, y, z, block or surf or "minecraft:grass_block")
                self.top[(x, z)] = y

    def scatter(self, cols, chance, pick, seed=0, avoid=None):
        """Small things on the surface (grass, flowers, bushes): pick(x, z) -> block or None."""
        for (x, z) in sorted(cols):
            if hash01(x, 7, z, seed) >= chance or (avoid and avoid(x, z)):
                continue
            y = self.top.get((x, z))
            if y is None or self.get(x, y + 1, z) not in (None, "minecraft:air"):
                continue
            surf = self.get(x, y, z) or ""
            if not surf.endswith(("grass_block", "podzol", "coarse_dirt", "moss_block", "dirt", "rooted_dirt", "mud", "mycelium")):
                continue
            block = pick(x, z)
            if block:
                self.set(x, y + 1, z, block)

    # ---------------------------------------------------------------- paths and bridges
    def path(self, points, width=3, blocks=None, seed=0):
        """A path over the surface through the points [(x, z), ...]."""
        blocks = blocks or self.mix({"dirt_path": 5, "coarse_dirt": 1, "gravel": 1})
        done = set()
        for (x1, z1), (x2, z2) in zip(points, points[1:]):
            steps = max(abs(x2 - x1), abs(z2 - z1), 1)
            for i in range(steps + 1):
                px = x1 + (x2 - x1) * i / steps
                pz = z1 + (z2 - z1) * i / steps
                half = width / 2
                for dx in range(-int(half) - 1, int(half) + 2):
                    for dz in range(-int(half) - 1, int(half) + 2):
                        if dx * dx + dz * dz > half * half + 0.3:
                            continue
                        x, z = int(round(px + dx)), int(round(pz + dz))
                        if (x, z) in done:
                            continue
                        y = self.top.get((x, z))
                        if y is None:
                            continue
                        done.add((x, z))
                        self.set(x, y, z, blocks(x, y, z, seed))
                        above = self.get(x, y + 1, z)
                        if above and above != "minecraft:air" and not above.endswith(("_log", "_wall", "fence", "_planks")):
                            self.set(x, y + 1, z, "minecraft:air")
        return done

    def bridge(self, a, b, y, width=3, deck="spruce_planks", rail="spruce_fence", beam="stripped_spruce_log", lantern=8,
               land=4):
        """A sky bridge from a to b ((x, z) points) with its deck at y: planks, log edges, fence rails, lanterns on
        posts every `lantern` blocks, chains hanging underneath. `land` blocks at each end blend into the ground."""
        (x1, z1), (x2, z2) = a, b
        length = max(1, math.hypot(x2 - x1, z2 - z1))
        dx, dz = (x2 - x1) / length, (z2 - z1) / length
        px, pz = -dz, dx                      # sideways
        half = width // 2
        axis = "x" if abs(dx) >= abs(dz) else "z"
        for i in range(int(length) + 1):
            cx, cz = x1 + dx * i, z1 + dz * i
            for s in range(-half - 1, half + 2):
                x, z = int(round(cx + px * s)), int(round(cz + pz * s))
                edge = abs(s) == half + 1
                if edge:
                    self.set(x, y, z, f"{beam}[axis={axis}]")
                    self.set(x, y + 1, z, rail)
                    if i % lantern == 0 and land < i < length - land:
                        self.set(x, y + 1, z, "minecraft:stripped_spruce_log[axis=y]")
                        self.set(x, y + 2, z, rail)
                        self.set(x, y + 3, z, "minecraft:lantern[hanging=false]")
                        for c in range(1, 4):
                            self.set(x, y - c, z, "minecraft:chain[axis=y]")
                else:
                    self.set(x, y, z, deck)
                    for yy in range(y + 1, y + 4):
                        if self.get(x, yy, z) not in (None, "minecraft:air", "minecraft:lantern[hanging=false]"):
                            self.set(x, yy, z, "minecraft:air")
        return [(int(round(x1 + dx * i)), int(round(z1 + dz * i))) for i in range(int(length) + 1)]

    def stairs_down(self, x, y, z, steps, facing, block="stone_brick_stairs", width=3, solid="stone_bricks"):
        """A stair run going down `steps` from (x, y, z) towards `facing` (north/south/east/west)."""
        d = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        side = (d[1], d[0])
        opposite = {"north": "south", "south": "north", "east": "west", "west": "east"}[facing]
        for i in range(steps):
            for s in range(-(width // 2), width // 2 + 1):
                bx, bz = x + d[0] * i + side[0] * s, z + d[1] * i + side[1] * s
                self.set(bx, y - i, bz, f"minecraft:{block}[facing={opposite},half=bottom]")
                self.set(bx, y - i - 1, bz, solid)
                for h in range(1, 4):
                    self.set(bx, y - i + h, bz, "minecraft:air")

    # ---------------------------------------------------------------- trees
    def oak(self, x, y, z, height=6, radius=3, log="oak_log", leaves="oak_leaves", seed=0):
        for h in range(height):
            self.set(x, y + h, z, f"minecraft:{log}[axis=y]")
        top = y + height
        for dy in range(-2, 2):
            r = radius - (1 if dy == 1 else 0) - (1 if dy == -2 else 0)
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if dx * dx + dz * dz <= r * r + 1 and hash01(x + dx, top + dy, z + dz, seed) > 0.1:
                        if self.get(x + dx, top + dy, z + dz) in (None, "minecraft:air"):
                            self.set(x + dx, top + dy, z + dz, f"minecraft:{leaves}[persistent=true]")
        self.set(x, top, z, f"minecraft:{leaves}[persistent=true]")

    def spruce(self, x, y, z, height=9, log="spruce_log", leaves="spruce_leaves", seed=0, bare=False):
        for h in range(height):
            self.set(x, y + h, z, f"minecraft:{log}[axis=y]")
        if bare:
            return
        for h in range(2, height + 1):
            r = max(0, int((height - h) / 2.6)) + (1 if h % 2 == 0 else 0)
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= r + 1 and (dx or dz or h == height) and hash01(x + dx, y + h, z + dz, seed) > 0.12:
                        if self.get(x + dx, y + h, z + dz) in (None, "minecraft:air"):
                            self.set(x + dx, y + h, z + dz, f"minecraft:{leaves}[persistent=true]")
        self.set(x, y + height, z, f"minecraft:{leaves}[persistent=true]")

    def dead_tree(self, x, y, z, height=6, log="spruce_log", seed=0):
        """A bare, crooked trunk with a few branches (the Graveyard's trees)."""
        cx, cz = x, z
        for h in range(height):
            self.set(cx, y + h, cz, f"minecraft:{log}[axis=y]")
            if h > 2 and hash01(x, h, z, seed) < 0.35:
                dx, dz = [(1, 0), (-1, 0), (0, 1), (0, -1)][int(hash01(x, h, z, seed + 1) * 4) % 4]
                axis = "x" if dx else "z"
                for i in range(1, 3 + int(hash01(x, h, z, seed + 2) * 2)):
                    self.set(cx + dx * i, y + h + (i // 2), cz + dz * i, f"minecraft:{log}[axis={axis}]")
        self.set(cx, y + height, cz, f"minecraft:{log}[axis=y]")

    # ---------------------------------------------------------------- outputs
    def npc(self, name, x, y, z, yaw=0.0, note=""):
        self.spots["npcs"][name] = {"pos": [x + 0.5, float(y), z + 0.5], "yaw": yaw, "note": note, "dimension": self.dimension}

    def arrival(self, x, y, z, yaw=0.0):
        """Where players land on this island (warps, launch pads) and come back to from the void."""
        self.arrival_spot = {"pos": [x + 0.5, float(y), z + 0.5], "yaw": yaw}

    def pad(self, name, x1, y1, z1, x2, y2, z2, warp=None, skill=None, level=0, destination=None):
        """A launch pad: standing in this box sends you to `warp` (a Fast Travel id), if you have `skill` at `level`; or to
        `destination` = (dimension, (x, y, z), yaw), a spot (the pads back to the Hub)."""
        entry = {"name": name, "dimension": self.dimension, "from": [x1, y1, z1], "to": [x2, y2, z2]}
        if warp:
            entry["warp"] = warp
        if destination:
            entry["destination"] = {"dimension": destination[0], "pos": list(destination[1]), "yaw": destination[2]}
        if skill:
            entry["requires"] = {"skill": skill if ":" in skill else "skyrpg_skills:" + skill, "level": level}
        self.spots["pads"].append(entry)

    def fairy_soul(self, x, y, z):
        self.spots["fairy_souls"].append([x, y, z])

    def warp(self, name, x, y, z, yaw=0.0):
        self.spots["warps"][name] = {"pos": [x + 0.5, float(y), z + 0.5], "yaw": yaw}

    def zone(self, name, box=None, blocks=None, mobs=None, extra=None):
        """A zone: the whole area's (or `box` = ((x1, y1, z1), (x2, y2, z2))). Call it once per named part (the Park's
        Birch Park, Spruce Woods...); zones must not overlap."""
        if box is None:
            xs = [x for x, _ in self.top] or [self.origin[0]]
            zs = [z for _, z in self.top] or [self.origin[2]]
            ys = list(self.top.values()) or [self.origin[1]]
            box = ((min(xs), min(ys) - 30, min(zs)), (max(xs), max(ys) + 40, max(zs)))
        zone = {"name": name, "dimension": self.dimension, "from": list(box[0]), "to": list(box[1])}
        if blocks:
            zone["blocks"] = blocks
        if mobs:
            zone["mobs"] = mobs
        if extra:
            zone.update(extra)
        if self.zone_json is None:
            self.zone_json = zone
        self.zones.append(zone)
        return zone

    def fill_unset_with_air(self):
        """In a generated world (the Crystal Hollows) a cell this build never set would keep the generated rock: every
        unset cell in the box becomes air, so carved rooms are empty. (Hidden rock that hollow() drops afterwards is left
        to the generator, which is fine: nobody sees it.)"""
        nx, ny, nz = self.size
        cells = self.piece.cells
        air = None
        for x in range(nx):
            for y in range(ny):
                for z in range(nz):
                    if (x, y, z) not in cells:
                        if air is None:
                            self.piece.set(x, y, z, mc("minecraft:air"))
                            air = cells[(x, y, z)]
                        else:
                            cells[(x, y, z)] = air

    def hollow(self):
        """Drops blocks nobody can see: full blocks more than `shell` blocks from air or anything see-through. Blocks the
        zone lets players break, and blocks with data, are always kept (so mining never opens onto a hidden hole). The
        Hub has no natural spawns, so the hidden pockets stay empty. Cuts a floating island's file to a fraction."""
        if self.shell <= 0:
            return 0
        cells = self.piece.cells
        breakable = set()
        for zone in self.zones:
            breakable |= set(zone.get("blocks", {}).keys())
        full = {pos for pos, (entry, data) in cells.items() if data is None and _full(entry["id"])}
        # Distance from the nearest non-full cell (air outside the build counts), up to the shell.
        frontier = {pos for pos in full if any((pos[0] + dx, pos[1] + dy, pos[2] + dz) not in full for dx, dy, dz in _SIDES)}
        keep = set(frontier)
        for _ in range(self.shell - 1):
            nxt = set()
            for (x, y, z) in frontier:
                for dx, dy, dz in _SIDES:
                    n = (x + dx, y + dy, z + dz)
                    if n in full and n not in keep:
                        nxt.add(n)
            keep |= nxt
            frontier = nxt
        # Sand and gravel fall into a hidden hole the moment the area is placed: keep what's under them, down to the
        # first block that doesn't fall.
        for pos in [pos for pos in keep if _falls(cells[pos][0]["id"])]:
            below = (pos[0], pos[1] - 1, pos[2])
            while below in full and below not in keep:
                keep.add(below)
                if not _falls(cells[below][0]["id"]):
                    break
                below = (below[0], below[1] - 1, below[2])
        dropped = 0
        keep_ids = getattr(self, "keep_ids", set())     # blocks the mod changes by hand (the Half-Eaten Cave's hay)
        for pos in list(full):
            if pos not in keep and cells[pos][0]["id"] not in breakable and cells[pos][0]["id"] not in keep_ids:
                del cells[pos]
                dropped += 1
        return dropped

    def prop_up(self):
        """Puts a block under every sand, gravel or concrete powder block that has air below it (it would fall)."""
        cells = self.piece.cells
        added = 0
        for (x, y, z), (entry, _) in list(cells.items()):
            if not _falls(entry["id"]):
                continue
            below = cells.get((x, y - 1, z))
            if below is None or below[0]["id"] in ("minecraft:air", "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
                                                   "minecraft:dead_bush", "minecraft:water", "minecraft:cave_air"):
                if self.piece.inside(x, y - 1, z):
                    self.piece.set(x, y - 1, z, _support(entry["id"]))
                    added += 1
        return added

    def save(self):
        for entry, data in self.piece.cells.values():
            if entry["id"] in RENAMED:
                entry["id"] = RENAMED[entry["id"]]
            # Sign lines are text components in 26.x: '"Gold Mine"' would show its quotes, so plain strings go in bare.
            for side in ("front_text", "back_text"):
                if isinstance(data, dict) and isinstance(data.get(side), dict) and "messages" in data[side]:
                    data[side]["messages"] = [_plain(m) for m in data[side]["messages"]]
        known = known_blocks()
        if known:
            unknown = sorted({entry["id"] for entry, _ in self.piece.cells.values()} - known - {"minecraft:air"})
            if unknown:
                raise SystemExit(f"{self.id}: unknown block ids (they would place nothing): {', '.join(unknown)}")
        if self.dimension in FILL_AIR:
            self.fill_unset_with_air()
        dropped = self.hollow()
        propped = self.prop_up()
        STRUCTURES.mkdir(parents=True, exist_ok=True)
        AREAS.mkdir(parents=True, exist_ok=True)
        ZONES.mkdir(parents=True, exist_ok=True)
        OUT.mkdir(parents=True, exist_ok=True)
        nbt.write_file(STRUCTURES / f"{self.id}.nbt", self.piece.to_nbt())
        area_json = {"structure": f"skyrpg_hub:areas/{self.id}", "origin": list(self.origin), "version": self.version}
        if self.dimension != DIM:
            area_json["dimension"] = self.dimension
        if self.island_name:
            area_json["island"] = self.island_name
        if self.arrival_spot:
            area_json["arrival"] = self.arrival_spot
            self.spots["arrival"] = self.arrival_spot
        (AREAS / f"{self.id}.json").write_text(json.dumps(area_json, indent=2) + "\n", encoding="utf-8")
        used = set()
        for i, zone in enumerate(self.zones):
            name = self.id if i == 0 else self.id + "_" + re.sub(r"[^a-z0-9]+", "_", zone["name"].lower()).strip("_")
            if name in used:
                name += f"_{i}"    # the same zone name over several boxes
            used.add(name)
            (ZONES / f"{name}.json").write_text(json.dumps(zone, indent=2) + "\n", encoding="utf-8")
        (OUT / f"{self.id}_spots.json").write_text(json.dumps(self.spots, indent=2) + "\n", encoding="utf-8")
        self.preview()
        print(f"{self.id}: {len(self.piece.cells)} blocks ({dropped} hidden ones left out), box {self.origin} + {self.size}, "
              f"{len(self.spots['fairy_souls'])} fairy souls, {len(self.spots['npcs'])} NPC spots"
              + (f", {propped} supports under sand/gravel" if propped else ""))

    def preview(self):
        """Top-down and side (from the south) PNGs, to check a build without the game."""
        try:
            from PIL import Image
        except ImportError:
            return
        w, h, d = self.size
        tops, sides = {}, {}
        for (x, y, z), (entry, _) in self.piece.cells.items():
            name = entry["id"]
            if name == "minecraft:air":
                continue
            if (x, z) not in tops or y > tops[(x, z)][0]:
                tops[(x, z)] = (y, name)
            if (x, y) not in sides or z > sides[(x, y)][0]:
                sides[(x, y)] = (z, name)
        scale = 3
        top_img = Image.new("RGB", (w * scale, d * scale), (24, 32, 58))
        side_img = Image.new("RGB", (w * scale, h * scale), (24, 32, 58))
        for (x, z), (y, name) in tops.items():
            c = _color(name, 0.55 + 0.6 * y / max(1, h))
            for i in range(scale):
                for j in range(scale):
                    top_img.putpixel((x * scale + i, z * scale + j), c)
        for (x, y), (z, name) in sides.items():
            c = _color(name, 0.55 + 0.6 * z / max(1, d))
            for i in range(scale):
                for j in range(scale):
                    side_img.putpixel((x * scale + i, (h - 1 - y) * scale + j), c)
        top_img.save(OUT / f"{self.id}_top.png")
        side_img.save(OUT / f"{self.id}_side.png")


_SIDES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
_NOT_FULL = ("air", "leaves", "glass", "pane", "slab", "stairs", "fence", "wall", "door", "bars", "chain", "lantern", "torch", "sign",
             "button", "plate", "carpet", "flower", "fern", "bush", "sapling", "vine", "roots", "dripstone", "rail", "ladder", "water",
             "lava", "cobweb", "candle", "head", "skull", "pot", "campfire", "bed", "chest", "barrel", "petals", "lichen", "lily",
             "kelp", "seagrass", "mushroom", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower",
             "rod", "cluster", "bud", "farmland", "path", "wheat", "carrots", "potatoes", "beetroots", "cactus", "cane", "bamboo",
             "scaffolding", "anvil", "bell", "grindstone", "lectern", "composter", "cauldron", "hopper", "azalea", "moss_carpet",
             "snow", "pickle", "egg", "amethyst_cluster", "conduit", "banner", "frame", "honey", "slime", "ice", "shulker",
             "brewing", "enchanting", "table", "beacon", "spawner", "leaf", "hay", "light", "pumpkin", "melon", "grass")


def _plain(line):
    """A sign line as plain text: '"Gold Mine"' (the old JSON form) becomes 'Gold Mine'."""
    if not (isinstance(line, str) and len(line) >= 2 and line[0] == line[-1] == '"'):
        return line
    try:
        return json.loads(line)
    except ValueError:
        return line[1:-1].replace("\\'", "'")


def _falls(block_id):
    """Sand, gravel, concrete powder and anvils fall when there's nothing under them."""
    name = block_id.split(":")[1].split("[")[0]
    return name in ("sand", "red_sand", "gravel", "suspicious_sand", "suspicious_gravel", "dragon_egg") or \
        name.endswith("concrete_powder") or name.endswith("anvil")


def _support(block_id):
    name = block_id.split(":")[1].split("[")[0]
    if name.endswith("concrete_powder"):
        return "minecraft:" + name[:-len("_powder")]
    return {"sand": "minecraft:sandstone", "suspicious_sand": "minecraft:sandstone",
            "red_sand": "minecraft:red_sandstone"}.get(name, "minecraft:tuff")


def _full(block_id):
    """Whether a block is a plain opaque cube (so it hides what's behind it)."""
    name = block_id.split(":")[1].split("[")[0]
    if name in ("grass_block", "snow_block", "slime_block", "honey_block", "hay_block", "packed_ice", "blue_ice"):
        return name == "grass_block" or name == "snow_block" or name == "hay_block"
    return not any(part in name for part in _NOT_FULL)


def _color(name, k):
    n = name.split(":")[1].split("[")[0]
    table = [("water", (50, 90, 200)), ("lava", (230, 110, 30)), ("leaves", (40, 115, 45)), ("grass", (80, 160, 65)),
             ("moss", (90, 130, 50)), ("podzol", (110, 80, 45)), ("mycelium", (120, 100, 120)), ("path", (160, 130, 85)),
             ("dirt", (125, 95, 65)), ("farmland", (100, 70, 40)), ("wheat", (210, 190, 90)), ("carrot", (230, 140, 40)),
             ("potato", (200, 170, 90)), ("sand", (215, 205, 150)), ("gravel", (130, 125, 120)), ("coal", (40, 40, 40)),
             ("log", (110, 80, 50)), ("plank", (160, 120, 75)), ("fence", (140, 105, 65)), ("snow", (240, 245, 250)),
             ("deepslate", (75, 75, 80)), ("tuff", (105, 105, 95)), ("andesite", (135, 135, 135)), ("stone", (125, 125, 125)),
             ("cobble", (110, 110, 110)), ("brick", (120, 120, 125)), ("lantern", (250, 210, 120)), ("glass", (180, 220, 240))]
    base = (150, 150, 150)
    for key, col in table:
        if key in n:
            base = col
            break
    return tuple(max(0, min(255, int(c * k))) for c in base)
