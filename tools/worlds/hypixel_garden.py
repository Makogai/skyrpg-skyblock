#!/usr/bin/env python3
"""Hypixel's Garden (a capture of a fresh one, never committed: it's Hypixel's build) as our Garden's Barn plot:

    python tools/worlds/hypixel_garden.py "<garden save>" [--world-dir run/server/world]

Every profile's Garden is stamped from the structure garden/barn when it's first visited (Garden.build). Minecraft
looks for structures in the world's generated/ folder before the mod's own, so this writes Hypixel's middle plot
(x and z -48..47, its bedrock at y 66 to the top of the Barn) to world/generated/skyrpg_skyblock/structure/garden/
barn.nbt, its grass at our Garden's ground, and hypixel_garden.json beside it, which tells the mod to use Hypixel's
spots for the Desk, the Composter, SkyMart and the others (GardenLayout). Gardens built before keep their Barn; a
new profile (or /sbgarden rebuild) gets Hypixel's. Undo: delete both files.

Leaves are made persistent on the way (Hypixel never random-ticks; see hypixel_map.py), and block entities (heads,
signs) come along. Never open a capture in singleplayer first: its trees start decaying at once.
"""
import argparse
import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402
from hypixel_map import dimension_dir, world_dir  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
PLOT = (-48, 47)
BOTTOM = 66   # Hypixel's Garden: bedrock at 66, dirt to 69, grass at 70
TEMPLATE_GRASS = 4  # garden/barn's grass, local y (Garden.build places it at GROUND - 4)


def extract(world):
    """{(x, y, z): (palette entry, block entity or None)} for every block of the middle plot, and the data version."""
    source = dimension_dir(world)
    blocks = {}
    version = None
    for region in sorted((source / "region").glob("r.*.mca")):
        for index, when, raw in nbt.raw_chunks(region):
            _, root = nbt.parse_typed(raw)
            cx, cz = root["xPos"][1], root["zPos"][1]
            if cx * 16 + 15 < PLOT[0] or cx * 16 > PLOT[1] or cz * 16 + 15 < PLOT[0] or cz * 16 > PLOT[1]:
                continue
            if not root.get("Status", (8, b"full"))[1].endswith(b"full"):
                continue
            version = root.get("DataVersion", (3, version))[1]
            entities = {}
            for be in root.get("block_entities", (9, (10, [])))[1][1]:
                entities[(be["x"][1], be["y"][1], be["z"][1])] = be
            for section in root["sections"][1][1]:
                states = section.get("block_states")
                if not states or "palette" not in states[1]:
                    continue
                palette = states[1]["palette"][1][1]
                data = states[1].get("data", (12, None))[1]
                bits = max(4, (len(palette) - 1).bit_length())
                values = nbt.unpack(data, bits, 4096) if data else [0] * 4096
                sy = section["Y"][1]
                for i, v in enumerate(values):
                    entry = palette[v] if v < len(palette) else palette[0]
                    name = entry["Name"][1]
                    if name in (b"minecraft:air", b"minecraft:cave_air", b"minecraft:void_air"):
                        continue
                    x, y, z = cx * 16 + (i & 15), sy * 16 + (i >> 8), cz * 16 + ((i >> 4) & 15)
                    if PLOT[0] <= x <= PLOT[1] and PLOT[0] <= z <= PLOT[1] and y >= BOTTOM:
                        blocks[(x, y, z)] = (entry, entities.get((x, y, z)))
    return blocks, version


def structure(blocks, version):
    """A structure file's typed NBT: the plot, its bedrock at local y TEMPLATE_GRASS - 4."""
    shift = BOTTOM - (TEMPLATE_GRASS - 4)
    top = max(y for _, y, _ in blocks)
    palette, index, out = [], {}, []
    for (x, y, z), (entry, be) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        properties = entry.get("Properties")
        if properties and properties[1].get("persistent", (8, b""))[1] == b"false":
            properties[1]["persistent"] = (8, b"true")
        key = (entry["Name"][1], tuple(sorted((k, v[1]) for k, v in properties[1].items())) if properties else ())
        if key not in index:
            index[key] = len(palette)
            palette.append(entry)
        block = {"pos": (9, (3, [x - PLOT[0], y - shift, z - PLOT[0]])), "state": (3, index[key])}
        if be is not None:
            data = {k: v for k, v in be.items() if k not in ("x", "y", "z")}
            block["nbt"] = (10, data)
        out.append(block)
    size = [PLOT[1] - PLOT[0] + 1, top - shift + 1, PLOT[1] - PLOT[0] + 1]
    return {"DataVersion": (3, version), "size": (9, (3, size)), "palette": (9, (10, palette)), "blocks": (9, (10, out)),
            "entities": (9, (10, []))}, len(out), size


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("world")
    parser.add_argument("--world-dir", default=str(ROOT / "run/server/world"))
    args = parser.parse_args()
    if not (Path(args.world_dir) / "level.dat").exists():
        sys.exit(f"{args.world_dir} isn't a world folder")
    blocks, version = extract(world_dir(args.world))
    if not blocks:
        sys.exit("no Garden blocks found (x and z -48..47, y 66 up)")
    compound, count, size = structure(blocks, version)
    out = Path(args.world_dir) / "generated" / "skyrpg_skyblock" / "structure" / "garden"
    out.mkdir(parents=True, exist_ok=True)
    (out / "barn.nbt").write_bytes(gzip.compress(nbt.dump_typed(b"", compound)))
    (out / "hypixel_garden.json").write_text(json.dumps({"source": Path(args.world).name, "data_version": version,
                                                         "note": "Hypixel's Garden, from tools/worlds/hypixel_garden.py: never commit or redistribute it."},
                                                        indent=1) + "\n", encoding="utf-8")
    print(f"{out / 'barn.nbt'}: {count} blocks, size {size}")


if __name__ == "__main__":
    main()
