#!/usr/bin/env python3
"""Fairy Souls for our own island builds, up to Hypixel's count for the island (Hypixel's spots are on Hypixel's maps,
which aren't ours):

    python tools/skyblock/place_fairy_souls.py gold_mine 12 [--world-dir run/server/world] [--write]

Reads the island's blocks from the world (the dev server's, where the island is built), samples places to stand inside
its zones (skyrpg/zones/*.json for that dimension), prefers ones tucked away (a roof over them: in buildings, caves,
under trees) or up high, and adds souls spread out as far as can be from each other and the island's souls already
placed (farthest-point picking), until the island has the count. Without --write it only says what it would add.
"""
import argparse
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/worlds"))
from blocks import Blocks  # noqa: E402

DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
PASS = ("air", "grass", "fern", "flower", "torch", "carpet", "button", "pressure_plate", "rail", "vine", "snow", "tulip", "dandelion",
        "poppy", "orchid", "allium", "bluet", "daisy", "cornflower", "lily_of_the_valley", "sapling", "mushroom", "sign", "banner", "bush",
        "lantern", "leaf_litter", "petals", "light")
LIQUID = ("water", "lava")


def passable(name):
    n = name.split(":")[-1]
    return any(p in n for p in PASS) and not any(l in n for l in LIQUID)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("island", help="the fairy_souls/<island>.json file")
    parser.add_argument("count", type=int, help="Hypixel's number of souls for the island")
    parser.add_argument("--world-dir", default=str(ROOT / "run/server/world"))
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    path = DATA / "fairy_souls" / f"{args.island}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    dimension = data["dimension"]
    have = [tuple(s) for s in data["souls"]]
    if len(have) >= args.count:
        print(f"{args.island}: already {len(have)} souls")
        return
    ns, dim = dimension.split(":")
    region = Path(args.world_dir) / "dimensions" / ns / dim / "region"
    if not region.is_dir():
        sys.exit(f"{region} isn't there (has the island been built in this world?)")
    blocks = Blocks(region)
    zones = []
    for f in sorted((DATA / "zones").glob("*.json")):
        z = json.loads(f.read_text(encoding="utf-8"))
        if z.get("dimension") == dimension:
            zones.append((z["name"], z["from"], z["to"]))
    if not zones:
        sys.exit(f"no zones for {dimension}")
    rng = random.Random(args.seed)
    candidates = []
    for _ in range(4000):
        name, a, b = rng.choice(zones)
        x = rng.randint(min(a[0], b[0]), max(a[0], b[0]))
        z = rng.randint(min(a[2], b[2]), max(a[2], b[2]))
        ylo, yhi = max(min(a[1], b[1]), -60), min(max(a[1], b[1]), 250)
        # every place to stand in the column (inside the zone's height)
        prev_solid = False
        spots = []
        for y in range(ylo, yhi):
            here = blocks.at(x, y, z)
            if passable(here) and prev_solid and passable(blocks.at(x, y + 1, z)):
                spots.append(y)
            prev_solid = not passable(here) and not any(l in here for l in LIQUID)
        for y in spots:
            roofed = any(not passable(blocks.at(x, yy, z)) for yy in range(y + 2, min(y + 12, yhi + 1)))
            candidates.append((x, y, z, roofed))
    if not candidates:
        sys.exit("no places to stand found (are the island's chunks saved?)")
    ys = sorted(c[1] for c in candidates)
    high = ys[int(len(ys) * 0.8)]
    # tucked away or up high counts double: they go in the pool twice
    pool = [c for c in candidates for _ in range(2 if c[3] or c[1] >= high else 1)]
    chosen = list(have)
    added = []
    while len(chosen) < args.count:
        best, best_d = None, -1
        for x, y, z, _ in rng.sample(pool, min(600, len(pool))):
            d = min(((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 for cx, cy, cz in chosen), default=10 ** 9)
            if d > best_d:
                best, best_d = (x, y, z), d
        chosen.append(best)
        added.append(best)
    print(f"{args.island}: {len(have)} -> {len(chosen)} souls; adding {added}")
    if args.write:
        data["souls"] = [list(s) for s in chosen]
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
