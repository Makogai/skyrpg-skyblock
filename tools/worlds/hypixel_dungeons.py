#!/usr/bin/env python3
"""Hypixel's dungeon rooms and boss arenas, from world downloads of real runs (the hypixel.net thread's "[Dungeons]
Floor I".."Floor VI" and "The Entrance" zips; never committed, they're Hypixel's builds):

    python tools/worlds/hypixel_dungeons.py survey "<capture>"... --sheet rooms.png --json rooms.json
    python tools/worlds/hypixel_dungeons.py install "<capture>"... --types types.json [--world-dir run/server/world]

A capture is one run's map: rooms on Hypixel's grid of 32-block cells from (-200, -200) (a room is its cells x 32 - 1
wide, the 1-block gaps between cells of one room built over), bedrock at y 66, the floor's top at y 68, walls to y 99,
basements down to y 30 or so; doorways 3 wide and 5 tall in the middle of each cell edge; the boss arena south-east.

survey finds every room (cells, shape), tells copies of one room apart from the rest by their block counts (the same
whichever way it's turned), and draws a contact sheet of the different ones to say which are special. install writes
each different room as a structure under world/generated/skyrpg_skyblock/structure/catacombs/rooms/hypixel/<category>/
(turned to our rooms' orientation: 1xN along x, the L missing its +x+z cell), its doorways walled (the mod cuts the
ones it uses), with our markers: a secret chest on each chest, star mobs on open floor (and where the capture's star
mobs stood), a key spot. types.json says which room ids are special (entrance, blood, fairy, puzzle:<kind>, trap) or
to skip; the rest are normal rooms by shape.
"""
import argparse
import collections
import gzip
import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402
from hypixel_map import color, dimension_dir, world_dir, _plain, _text  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ORIGIN = -200      # the grid's first cell
CELL = 32
GRID = 6
BEDROCK_Y = 66
FLOOR_Y = 68       # the floor's top
TOP_Y = 99         # the ceiling
LOW_Y = 30         # templates start here (basements)
OUR_FLOOR = 8      # our rooms' floor, local y
AIR = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")


class World:
    """Block states of a capture (palette entries as plain dicts), chunk by chunk, with block entities and entities."""

    def __init__(self, capture):
        self.world = world_dir(capture)
        self.label = Path(capture).name
        self.sections = {}
        self.block_entities = {}
        self.entities = []
        for region in sorted((dimension_dir(self.world) / "region").glob("r.*.mca")):
            for lx, lz, chunk in nbt.chunks(region):
                level = chunk.get("Level", chunk)
                cx, cz = level["xPos"], level["zPos"]
                for s in level.get("Sections", level.get("sections", [])):
                    palette = s.get("Palette") or (s.get("block_states") or {}).get("palette")
                    states = s.get("BlockStates") or (s.get("block_states") or {}).get("data")
                    if palette:
                        bits = max(4, (len(palette) - 1).bit_length())
                        self.sections[(cx, s["Y"], cz)] = (palette, nbt.unpack(states, bits, 4096) if states else [0] * 4096)
                for be in level.get("TileEntities", level.get("block_entities", [])):
                    self.block_entities[(be["x"], be["y"], be["z"])] = be
                for e in level.get("Entities", []):
                    self.entities.append(e)

    def state(self, x, y, z):
        section = self.sections.get((x >> 4, y >> 4, z >> 4))
        if section is None:
            return {"Name": "minecraft:air"}
        palette, data = section
        i = data[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]
        entry = palette[i] if i < len(palette) else {"Name": "minecraft:air"}
        if isinstance(entry, str):
            return {"Name": entry}
        return entry if "Name" in entry else {"Name": entry.get("", "minecraft:air")}

    def name(self, x, y, z):
        return self.state(x, y, z)["Name"]


# ---------------------------------------------------------------- rooms


def cells_of(world):
    """The grid cells with a room in them."""
    out = set()
    for i in range(GRID):
        for j in range(GRID):
            x0, z0 = ORIGIN + CELL * i, ORIGIN + CELL * j
            # a room's walls run along its cell's edges (doorways aside)
            edge = [(x0 + k, z0) for k in (3, 8, 22, 27)] + [(x0 + k, z0 + 30) for k in (3, 8, 22, 27)]                 + [(x0, z0 + k) for k in (3, 8, 22, 27)] + [(x0 + 30, z0 + k) for k in (3, 8, 22, 27)]
            hits = sum(1 for x, z in edge for y in (FLOOR_Y + 4, FLOOR_Y + 12) if world.name(x, y, z) not in AIR)
            if hits >= 20:
                out.add((i, j))
    return out


def joined(world, a, b):
    """Whether two neighbouring cells are one room: the gap between them built over at the floor (between two rooms it's
    empty air, doorways included, all the way down)."""
    (i1, j1), (i2, j2) = sorted([a, b])
    built = 0
    for k in range(1, 30):
        if i1 != i2:
            x, z = ORIGIN + CELL * i1 + 31, ORIGIN + CELL * j1 + k
        else:
            x, z = ORIGIN + CELL * i1 + k, ORIGIN + CELL * j1 + 31
        built += any(world.name(x, y, z) not in AIR for y in range(LOW_Y, TOP_Y))
    return built > 29 * 0.6


def rooms_of(world):
    cells = cells_of(world)
    seen, rooms = set(), []
    for c in sorted(cells):
        if c in seen:
            continue
        group, todo = [], [c]
        while todo:
            cur = todo.pop()
            if cur in seen:
                continue
            seen.add(cur)
            group.append(cur)
            i, j = cur
            for n in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)):
                if n in cells and n not in seen and joined(world, cur, n):
                    todo.append(n)
        rooms.append(sorted(group))
    return rooms


def shape_of(cells):
    """Our shape name and the quarter turns (clockwise, seen from above) that bring the cells to our orientation."""
    i0 = min(i for i, _ in cells)
    j0 = min(j for _, j in cells)
    rel = sorted((i - i0, j - j0) for i, j in cells)
    w = max(i for i, _ in rel) + 1
    d = max(j for _, j in rel) + 1
    n = len(rel)
    if n == 1:
        return "one", 0
    if n == 4 and w == 2 and d == 2:
        return "square", 0
    if n in (2, 3, 4) and (w == 1 or d == 1):
        return {2: "two", 3: "three", 4: "four"}[n], 0 if d == 1 else 1
    if n == 3 and w == 2 and d == 2:
        missing = ({(0, 0), (1, 0), (0, 1), (1, 1)} - set(rel)).pop()
        # turns that move the missing cell to (1, 1)
        return "l", {(1, 1): 0, (0, 1): 3, (0, 0): 2, (1, 0): 1}[missing]
    return None, 0


def box_of(cells):
    i0 = min(i for i, _ in cells)
    j0 = min(j for _, j in cells)
    i1 = max(i for i, _ in cells)
    j1 = max(j for _, j in cells)
    return ORIGIN + CELL * i0, ORIGIN + CELL * j0, ORIGIN + CELL * i1 + 30, ORIGIN + CELL * j1 + 30


def in_room(cells, x, z):
    """Inside one of the room's cells, or the built-over gap between two of them."""
    i, j = (x - ORIGIN) // CELL, (z - ORIGIN) // CELL
    ox, oz = (x - ORIGIN) % CELL, (z - ORIGIN) % CELL
    if ox == 31 and oz == 31:
        return (i, j) in cells and (i + 1, j) in cells and (i, j + 1) in cells and (i + 1, j + 1) in cells
    if ox == 31:
        return (i, j) in cells and (i + 1, j) in cells
    if oz == 31:
        return (i, j) in cells and (i, j + 1) in cells
    return (i, j) in cells


def fingerprint(world, cells):
    counts = collections.Counter()
    x0, z0, x1, z1 = box_of(cells)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if in_room(cells, x, z):
                for y in range(LOW_Y, TOP_Y):
                    n = world.name(x, y, z)
                    if n not in AIR:
                        counts[n] += 1
    return counts


def similar(a, b):
    keys = set(a) | set(b)
    same = sum(min(a[k], b[k]) for k in keys)
    total = sum(max(a[k], b[k]) for k in keys)
    return same / max(1, total)


def slice_image(world, cells, scale=2):
    from PIL import Image
    x0, z0, x1, z1 = box_of(cells)
    img = Image.new("RGB", ((x1 - x0 + 1) * scale, (z1 - z0 + 1) * scale), (10, 10, 14))
    px = img.load()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not in_room(cells, x, z):
                continue
            for y in range(FLOOR_Y + 6, LOW_Y, -1):
                n = world.name(x, y, z)
                if n not in AIR:
                    c = color(n, y, FLOOR_Y - 10, FLOOR_Y + 6)
                    for dx in range(scale):
                        for dz in range(scale):
                            px[(x - x0) * scale + dx, (z - z0) * scale + dz] = c
                    break
    return img


def survey(args):
    from PIL import Image, ImageDraw, ImageFont
    found = []   # (id, capture, cells, shape, counts)
    for capture in args.captures:
        world = World(capture)
        for cells in rooms_of(world):
            shape, turns = shape_of(cells)
            counts = fingerprint(world, cells)
            same = next((f for f in found if f["shape"] == shape and similar(f["counts"], counts) > 0.92), None)
            if same:
                same["copies"].append({"capture": world.label, "cells": cells})
                continue
            rid = f"{shape}_{len([f for f in found if f['shape'] == shape]) + 1:02d}"
            found.append({"id": rid, "capture": world.label, "cells": cells, "shape": shape, "turns": turns, "counts": counts,
                          "copies": [], "world": world})
        print(f"{world.label}: {len(rooms_of(world))} rooms")
    print(f"{len(found)} different rooms:", collections.Counter(f["shape"] for f in found))
    # the contact sheet: every different room, labelled
    tiles = [(f["id"], slice_image(f["world"], f["cells"], 2)) for f in found]
    cols = 8
    tw = max(t.width for _, t in tiles)
    th = max(t.height for _, t in tiles) + 14
    sheet = Image.new("RGB", (cols * (tw + 6), math.ceil(len(tiles) / cols) * (th + 6)), (24, 24, 30))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for k, (rid, tile) in enumerate(tiles):
        x, y = (k % cols) * (tw + 6), (k // cols) * (th + 6)
        sheet.paste(tile, (x, y + 14))
        draw.text((x + 2, y + 1), rid, fill=(255, 255, 160), font=font)
    sheet.save(args.sheet)
    out = [{"id": f["id"], "capture": f["capture"], "cells": f["cells"], "shape": f["shape"], "copies": len(f["copies"]),
            "top": [k.split(":")[1] for k, _ in f["counts"].most_common(6)]} for f in found]
    Path(args.json).write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    print(f"sheet {args.sheet}, list {args.json}")


# ---------------------------------------------------------------- turning blocks

FACING = ["north", "east", "south", "west"]
RAIL = {"north_south": "east_west", "east_west": "north_south", "ascending_north": "ascending_east", "ascending_east": "ascending_south",
        "ascending_south": "ascending_west", "ascending_west": "ascending_north", "south_east": "south_west", "south_west": "north_west",
        "north_west": "north_east", "north_east": "south_east"}


def turn_state(entry, turns):
    """A palette entry turned clockwise (seen from above) a number of quarter turns."""
    props = dict(entry.get("Properties", {}))
    for _ in range(turns % 4):
        new = dict(props)
        for key, value in props.items():
            if key in ("facing", "horizontal_facing") and value in FACING:
                new[key] = FACING[(FACING.index(value) + 1) % 4]
            elif key == "axis" and value in ("x", "z"):
                new[key] = "z" if value == "x" else "x"
            elif key == "rotation":
                new[key] = str((int(value) + 4) % 16)
            elif key == "shape" and value in RAIL:
                new[key] = RAIL[value]
            elif key in FACING:
                # fences, walls, panes, vines, redstone: the side that was north is now east
                new[FACING[(FACING.index(key) + 1) % 4]] = value
        props = new
    out = {"Name": entry["Name"]}
    if props:
        out["Properties"] = props
    return out


def turn_xz(x, z, w, d, turns):
    """A spot in a w x d box after quarter turns clockwise (the box's size turns with it)."""
    for _ in range(turns % 4):
        x, z, w, d = d - 1 - z, x, d, w
    return x, z


def _typed(value):
    """Plain Python as tagged NBT (strings, ints, floats, dicts, lists)."""
    if isinstance(value, bool):
        return (1, int(value))
    if isinstance(value, int):
        return (3, value)
    if isinstance(value, float):
        return (6, value)
    if isinstance(value, str):
        return (8, value.encode("utf-8"))
    if isinstance(value, dict):
        return (10, {k: _typed(v) for k, v in value.items()})
    if isinstance(value, list):
        if not value:
            return (9, (0, []))
        items = [_typed(v) for v in value]
        return (9, (items[0][0], [v for _, v in items]))
    raise TypeError(type(value))


def _typed_raw(value):
    """A parsed (untyped) NBT value back to tagged NBT, guessing ints/doubles/strings/lists/compounds."""
    if isinstance(value, dict):
        return (10, {k: (11, list(v)) if k == "Id" and isinstance(v, list) and len(v) == 4 and all(isinstance(n, int) for n in v)
                     else _typed_raw(v) for k, v in value.items()})
    if isinstance(value, list):
        if not value:
            return (9, (0, []))
        items = [_typed_raw(v) for v in value]
        return (9, (items[0][0], [v for _, v in items]))
    if isinstance(value, bool):
        return (1, int(value))
    if isinstance(value, int):
        return (3, value)
    if isinstance(value, float):
        return (6, value)
    if isinstance(value, bytes):
        return (8, value)
    return (8, str(value).encode("utf-8"))


ROLES = (("zombie", "zombie"), ("skeleton", "skeleton"), ("crypt", "crypt"), ("spider", "spider"), ("fel", "elite"),
         ("assassin", "elite"), ("adventurer", "elite"), ("angry archaeologist", "elite"), ("frozen", "elite"))


def standable(world, x, z):
    return (world.name(x, FLOOR_Y, z) not in AIR and world.name(x, FLOOR_Y + 1, z) in AIR and world.name(x, FLOOR_Y + 2, z) in AIR
            and world.name(x, FLOOR_Y + 3, z) in AIR)


def install(args):
    types = json.loads(Path(args.types).read_text(encoding="utf-8")) if args.types else {}
    out_dir = Path(args.world_dir) / "generated" / "skyrpg_skyblock" / "structure" / "catacombs" / "rooms" / "hypixel"
    if not (Path(args.world_dir) / "level.dat").exists():
        sys.exit(f"{args.world_dir} isn't a world folder")
    if out_dir.exists():
        for old in out_dir.rglob("*.nbt"):
            old.unlink()
    found = []
    for capture in args.captures:
        world = World(capture)
        for cells in rooms_of(world):
            shape, turns = shape_of(cells)
            counts = fingerprint(world, cells)
            if any(f["shape"] == shape and similar(f["counts"], counts) > 0.92 for f in found):
                continue
            rid = f"{shape}_{len([f for f in found if f['shape'] == shape]) + 1:02d}"
            found.append({"id": rid, "shape": shape, "turns": turns, "counts": counts, "world": world, "cells": cells})
    written = collections.Counter()
    rng = random.Random(1)
    for f in found:
        kind = types.get(f["id"], f["shape"])
        if kind == "skip" or f["shape"] is None:
            continue
        category = {"entrance": "special/entrance", "blood": "special/blood", "fairy": "special/fairy"}.get(kind, kind)
        world, cells, turns = f["world"], f["cells"], f["turns"]
        x0, z0, x1, z1 = box_of(cells)
        w, d = x1 - x0 + 1, z1 - z0 + 1
        h = TOP_Y - LOW_Y
        doors = set()
        for i, j in cells:
            cx, cz = ORIGIN + CELL * i, ORIGIN + CELL * j
            for (di, dj) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                if (i + di, j + dj) in cells:
                    continue
                for k in (14, 15, 16):
                    doors.add({(0, -1): (cx + k, cz), (0, 1): (cx + k, cz + 30), (-1, 0): (cx, cz + k), (1, 0): (cx + 30, cz + k)}[(di, dj)]
                              + ((cx + 12, cz) if dj == -1 else (cx + 12, cz + 30) if dj == 1 else (cx, cz + 12) if di == -1 else (cx + 30, cz + 12),))
        palette, index, blocks = [], {}, []
        door_at = {(dx, dz): beside for dx, dz, beside in doors}
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not in_room(cells, x, z):
                    continue
                for y in range(LOW_Y, TOP_Y):
                    entry = world.state(x, y, z)
                    if (x, z) in door_at and FLOOR_Y + 1 <= y <= FLOOR_Y + 5:
                        bx, bz = door_at[(x, z)]
                        entry = world.state(bx, y, bz)
                        if entry["Name"] in AIR:
                            entry = {"Name": "minecraft:stone_bricks"}
                    entry = turn_state(entry, turns)
                    key = json.dumps(entry, sort_keys=True)
                    if key not in index:
                        index[key] = len(palette)
                        palette.append(entry)
                    lx, lz = turn_xz(x - x0, z - z0, w, d, turns)
                    block = {"pos": (9, (3, [lx, y - LOW_Y, lz])), "state": (3, index[key])}
                    be = world.block_entities.get((x, y, z))
                    # heads (their skins), signs and banners keep their data; a chest gets a fresh one (our loot)
                    if be is not None and str(be.get("id", "")).split(":")[-1] in ("skull", "sign", "banner"):
                        block["nbt"] = _typed_raw({k: v for k, v in be.items() if k not in ("x", "y", "z")})
                    blocks.append(block)
        tw, td = (d, w) if turns % 2 else (w, d)
        markers = []

        def marker(x, y, z, kind_, **data):
            lx, lz = turn_xz(x - x0, z - z0, w, d, turns)
            pos = [lx + 0.5, float(y - LOW_Y), lz + 0.5]
            payload = {"catacombs": kind_}
            payload.update(data)
            markers.append({"pos": (9, (6, pos)), "blockPos": (9, (3, [lx, y - LOW_Y, lz])),
                            "nbt": _typed({"id": "minecraft:marker", "Pos": pos, "Tags": ["skyrpg_catacombs"], "data": payload})})

        # secret chests where Hypixel's chests are
        for (bx, by, bz), be in world.block_entities.items():
            if x0 <= bx <= x1 and z0 <= bz <= z1 and in_room(cells, bx, bz) and LOW_Y <= by < TOP_Y \
                    and world.name(bx, by, bz) in ("minecraft:chest", "minecraft:trapped_chest"):
                marker(bx, by, bz, "secret_chest")
        # open floor away from the doorways, for mobs and the rest
        spots = [(x, z) for x in range(x0 + 2, x1 - 1) for z in range(z0 + 2, z1 - 1)
                 if in_room(cells, x, z) and standable(world, x, z) and all(abs(x - dx) + abs(z - dz) > 5 for dx, dz, _ in doors)]
        rng.shuffle(spots)
        mid = ((x0 + x1) // 2, (z0 + z1) // 2)
        centre = min(spots, key=lambda s: abs(s[0] - mid[0]) + abs(s[1] - mid[1])) if spots else mid
        if kind == "entrance":
            marker(centre[0], FLOOR_Y + 1, centre[1], "spawn")
        elif kind == "blood":
            marker(centre[0], FLOOR_Y + 3, centre[1], "watcher")
        elif kind == "fairy":
            marker(centre[0], FLOOR_Y + 1, centre[1], "fairy")
        else:
            # the capture's own star mobs where they stood, then open floor: three a cell, one elite in big rooms
            placed = []
            for e in world.entities:
                lab = label_of(e)
                if lab and lab["name"].startswith("\u272f") and in_room(cells, int(math.floor(lab["x"])), int(math.floor(lab["z"]))) \
                        and LOW_Y <= lab["y"] < TOP_Y:
                    low = lab["name"].lower()
                    role = next((r for k, r in ROLES if k in low), "zombie")
                    placed.append((int(math.floor(lab["x"])), int(math.floor(lab["y"])) , int(math.floor(lab["z"])), role))
            want = 3 * len(cells)
            for x, z in spots:
                if len(placed) >= want:
                    break
                if all(abs(x - px) + abs(z - pz) >= 5 for px, _, pz, _ in placed):
                    placed.append((x, FLOOR_Y + 1, z, "skeleton" if len(placed) % 3 == 2 else "zombie"))
            elite = len(cells) >= 3 and not any(r == "elite" for *_, r in placed)
            for k, (x, y, z, role) in enumerate(placed):
                marker(x, y, z, "mob", role="elite" if elite and k == 0 else role, star=True)
            if spots:
                kx, kz = spots[-1]
                marker(kx, FLOOR_Y + 1, kz, "key")
        compound = {"DataVersion": (3, 2586), "size": (9, (3, [tw, h, td])), "palette": (9, (10, [_typed(e)[1] for e in palette])),
                    "blocks": (9, (10, [b for b in blocks])), "entities": (9, (10, [m for m in markers]))}
        out = out_dir / category / (f["id"] + ".nbt")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(gzip.compress(nbt.dump_typed(b"", compound)))
        written[category] += 1
    (out_dir / "hypixel_dungeons.json").write_text(json.dumps({"rooms": dict(written), "note":
        "Hypixel's dungeon rooms, from tools/worlds/hypixel_dungeons.py: never commit or redistribute them."}, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {sum(written.values())} rooms to {out_dir}: {dict(written)}")


def label_of(entity):
    """A named armour stand's text and place (the capture's mob nameplates)."""
    if entity.get("id") != "minecraft:armor_stand" or "CustomName" not in entity:
        return None
    name = _text(entity["CustomName"])
    pos = entity.get("Pos", [0, 0, 0])
    return {"name": name, "x": pos[0], "y": pos[1], "z": pos[2]} if name else None


# ---------------------------------------------------------------- boss arenas

# the cells our room finder takes for rooms that are really the way into the arena
ARENA_CELLS = {"Floor I": [(4, 5)], "Floor II": [(5, 5)], "Floor III": [(5, 5)], "Floor IV": [(5, 5)]}
BOSSES = {"Floor I": "bonzo", "Floor II": "scarf", "Floor III": "professor", "Floor IV": "thorn", "Floor V": "livid", "Floor VI": "sadan"}


def arena_of(world):
    """The boss arena's box: everything built outside the rooms (the rooms are cut off from each other by empty gaps,
    so whatever's left once they're taken away is the arena and its way in)."""
    pieces_of_arena = ARENA_CELLS.get(next((k for k in ARENA_CELLS if world.label.endswith(k)), ""), [])
    rooms = [cells for cells in rooms_of(world) if not any(c in pieces_of_arena for c in cells)]
    # each room with the gap around it (the doorways between rooms are in the gaps)
    room_boxes = [(bx0 - 1, bz0 - 1, bx1 + 1, bz1 + 1) for bx0, bz0, bx1, bz1 in (box_of(cells) for cells in rooms)]
    columns = set()
    for (cx, cy, cz), (palette, data) in world.sections.items():
        if all(e["Name"] in AIR for e in palette):
            continue
        for i, v in enumerate(data):
            if palette[v]["Name"] not in AIR:
                columns.add((cx * 16 + (i & 15), cz * 16 + ((i >> 4) & 15)))
    # columns in a room that's really an arena piece (it touches the grid's edge) stay; the rooms' own go
    def in_rooms(x, z):
        return any(bx0 <= x <= bx1 and bz0 <= z <= bz1 for bx0, bz0, bx1, bz1 in room_boxes)
    left = {c for c in columns if not in_rooms(*c)}
    # the biggest piece (and what touches it)
    seen, pieces = set(), []
    for c in left:
        if c in seen:
            continue
        piece, todo = [], [c]
        seen.add(c)
        while todo:
            x, z = todo.pop()
            piece.append((x, z))
            for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
                if n in left and n not in seen:
                    seen.add(n)
                    todo.append(n)
        pieces.append(piece)
    pieces.sort(key=len, reverse=True)
    arena = pieces[0] if pieces and len(pieces[0]) >= 200 else []
    if not arena:
        return None
    x0, x1 = min(x for x, _ in arena), max(x for x, _ in arena)
    z0, z1 = min(z for _, z in arena), max(z for _, z in arena)
    ys = [y for (cx, cy, cz), (palette, data) in world.sections.items() for y in range(cy * 16, cy * 16 + 16)
          if x0 >> 4 <= cx <= x1 >> 4 and z0 >> 4 <= cz <= z1 >> 4 and not all(e["Name"] in AIR for e in palette)]
    y0, y1 = max(0, min(ys)), max(ys)
    return x0, y0, z0, x1, y1, z1, room_boxes


def arenas(args):
    out_dir = Path(args.world_dir) / "generated" / "skyrpg_skyblock" / "structure" / "catacombs" / "boss" / "hypixel"
    out_dir.mkdir(parents=True, exist_ok=True)
    for capture in args.captures:
        world = World(capture)
        boss = next((b for k, b in BOSSES.items() if Path(capture).name.endswith(k)), None)
        if boss is None:
            print(f"{world.label}: no boss for this capture")
            continue
        found = arena_of(world)
        if found is None:
            print(f"{world.label}: no arena found")
            continue
        x0, y0, z0, x1, y1, z1, room_boxes = found
        # trim empty layers top and bottom
        def layer_empty(y):
            return all(world.name(x, y, z) in AIR for x in range(x0, x1 + 1, 2) for z in range(z0, z1 + 1, 2))
        while y1 > y0 and layer_empty(y1):
            y1 -= 1
        while y0 < y1 and layer_empty(y0):
            y0 += 1
        palette, index, blocks = [], {}, []
        stand = []
        tops = {}
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if any(bx0 <= x <= bx1 and bz0 <= z <= bz1 for bx0, bz0, bx1, bz1 in room_boxes):
                    continue
                below_solid = False
                for y in range(y0, y1 + 1):
                    entry = world.state(x, y, z)
                    key = json.dumps(entry, sort_keys=True)
                    if key not in index:
                        index[key] = len(palette)
                        palette.append(entry)
                    block = {"pos": (9, (3, [x - x0, y - y0, z - z0])), "state": (3, index[key])}
                    be = world.block_entities.get((x, y, z))
                    if be is not None and str(be.get("id", "")).split(":")[-1] in ("skull", "sign", "banner"):
                        block["nbt"] = _typed_raw({k: v for k, v in be.items() if k not in ("x", "y", "z")})
                    blocks.append(block)
                    air = entry["Name"] in AIR
                    if air and below_solid and world.name(x, y + 1, z) in AIR:
                        stand.append((x, y, z))
                    below_solid = not air and entry["Name"] not in ("minecraft:water", "minecraft:lava")
                    if not air:
                        tops[(x, z)] = y
        # the main floor: the commonest standing height under a roof (not the top of the arena's roof); its middle, and the
        # spot nearest the rooms (the way in)
        stand = [(x, y, z) for x, y, z in stand if tops.get((x, z), y) > y + 1]
        floor_y = collections.Counter(y for _, y, _ in stand).most_common(1)[0][0]
        level = [(x, z) for x, y, z in stand if y == floor_y]
        cx = sum(x for x, _ in level) / len(level)
        cz = sum(z for _, z in level) / len(level)
        centre = min(level, key=lambda c: (c[0] - cx) ** 2 + (c[1] - cz) ** 2)
        def to_rooms(c):
            return min(max(bx0 - c[0], 0, c[0] - bx1) + max(bz0 - c[1], 0, c[1] - bz1) for bx0, bz0, bx1, bz1 in room_boxes)
        arrival = min(level, key=to_rooms)
        markers = []
        for kind, (mx, mz) in (("arena_center", centre), ("arena_arrival", arrival)):
            pos = [mx - x0 + 0.5, float(floor_y - y0), mz - z0 + 0.5]
            markers.append({"pos": (9, (6, pos)), "blockPos": (9, (3, [mx - x0, floor_y - y0, mz - z0])),
                            "nbt": _typed({"id": "minecraft:marker", "Pos": pos, "Tags": ["skyrpg_catacombs"], "data": {"catacombs": kind}})})
        size = [x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1]
        compound = {"DataVersion": (3, 2586), "size": (9, (3, size)), "palette": (9, (10, [_typed(e)[1] for e in palette])),
                    "blocks": (9, (10, blocks)), "entities": (9, (10, markers))}
        (out_dir / f"{boss}.nbt").write_bytes(gzip.compress(nbt.dump_typed(b"", compound)))
        print(f"{world.label}: {boss} arena {size}, floor y {floor_y} (local {floor_y - y0}), centre {centre}, way in {arrival}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("survey")
    s.add_argument("captures", nargs="+")
    s.add_argument("--sheet", default="rooms.png")
    s.add_argument("--json", default="rooms.json")
    i = sub.add_parser("install")
    i.add_argument("captures", nargs="+")
    i.add_argument("--types", default="")
    i.add_argument("--world-dir", default=str(ROOT / "run/server/world"))
    a = sub.add_parser("arenas")
    a.add_argument("captures", nargs="+")
    a.add_argument("--world-dir", default=str(ROOT / "run/server/world"))
    args = parser.parse_args()
    if args.cmd == "survey":
        survey(args)
    elif args.cmd == "arenas":
        arenas(args)
    else:
        install(args)


if __name__ == "__main__":
    main()
