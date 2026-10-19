#!/usr/bin/env python3
"""Puts our NPCs where Hypixel's stand, on an island running on Hypixel's map (tools/worlds/hypixel_map.py install):

    python tools/worlds/place_npcs.py skyrpg_hub:hub --island hub "<the map's world folder or zip>" [--write]

Where each NPC goes, best first:
  1. the name hologram the map itself has over that NPC (hypixel_npcs.json, written when the map was installed): the
     map's own year, so it fits its buildings;
  2. NEU's position for the NPC, if a player can stand there on this map (NEU's are Hypixel's current ones, and some
     buildings have moved since a map was saved);
  3. our override below (NPCs of ours, event stands, places NEU doesn't know);
  4. else the nearest spot a player can stand within 8 blocks of NEU's (flagged "check").
Prints the plan; --write updates the NPC files' "pos" (and "yaw", towards the island's middle unless overridden).
"""
import argparse
import json
import math
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from blocks import Blocks, standable  # noqa: E402
from hypixel_map import world_dir  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
NPCS = ROOT / "npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs"
NEU_ZIP = ROOT / "tools/skyblock/cache/neu-repo.zip"
WORLD = ROOT / "run/server/world"

# Where NPCs face by default: the island's middle (the Hub: its village square).
MIDDLE = {"hub": (-2, -85), "foraging_1": (-350, -20)}
# Ours, or Hypixel's where neither the map nor NEU places them: [x, y, z, yaw] (feet; yaw None = face the middle), or
# ["near", x, y, z]: the nearest spot a player can stand there (within 16 blocks).
OVERRIDES = {
    "hub": {
        # the event stand: in October 2022 the map's "Spooky Man" handed out the Halloween rewards here
        "Fear Mongerer": [7.5, 70, -85.5, None],
        "Oringo": [7.5, 70, -85.5, None],
        # the Mayor stands by the elections hall ("Mayor Marina" on the map)
        "Diana": [6.5, 71, -110.5, None],
        # in the Bank, the gold rotunda by the square
        "Banker": ["near", -25, 70, -64],
        # ours: the enchanter waits by the Library's enchanting table
        "Seraphine Vael": ["near", -37, 69, -109],
        # NEU's spot (on the mountain) isn't standable on this map
        "Scoop": ["near", -54, 169, 70],
        # NEU's spots are on the Bank's roof on this map: on the square below it
        "Amelia": ["near", -12, 70, -72],
        "Christopher": ["near", -12, 70, -78],
        # ours (the Rift): on the Wizard Tower's top floor, facing its portal (the spawn spot rift_portal)
        "Wizard": [39.5, 131, 74.5, 180],
    },
    "foraging_1": {
        # NEU's spots are in rock (Nyko: the hollow 9 blocks under it) and off this map's edge (Melody: the nearest ground)
        "Old Shaman Nyko": [-371.5, 75, -65.5, None],
        "Melody": ["near", -412, 109, 52],
        # ours, beside their neighbours as in our build: Kelly with Gustave in the Spruce Woods, the Campfire Adept with
        # Ryan, Worker Xavier with Master Tactician Funk on the Savanna, Juliette and Molbert by Galatea's pad
        "Kelly": ["near", -381, 88, 54],
        "Campfire Adept": ["near", -361, 102, -88],
        "Worker Xavier": ["near", -444, 107, 21],
        "Juliette": ["near", -436, 127, -102],
        "Molbert": ["near", -436, 127, -109],
    },
}


# Overrides that win over NEU's position (NEU's is standable on this map, but wrong: a roof).
PREFER_OVERRIDE = {"Amelia", "Christopher", "Wizard"}


def neu_positions(island):
    out = {}
    with zipfile.ZipFile(NEU_ZIP) as z:
        for n in z.namelist():
            if "/items/" in n and n.endswith("_NPC.json"):
                d = json.loads(z.read(n))
                if d.get("island") == island and d.get("x") is not None:
                    name = re.sub("§.", "", d.get("displayname", "")).replace(" (NPC)", "")
                    out.setdefault(name, (d["x"], d["y"], d["z"]))
    return out


def ground(blocks, x, y, z, dys=(0, -1, 1, -2, 2, -3, 3)):
    for dy in dys:
        if standable(blocks, x, y + dy, z):
            return y + dy
    return None


def nearest(blocks, x, y, z, radius=8):
    best = None
    for r in range(1, radius + 1):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) != r:
                    continue
                gy = ground(blocks, x + dx, y, z + dz)
                if gy is not None:
                    d = dx * dx + dz * dz + (gy - y) ** 2
                    if best is None or d < best[0]:
                        best = (d, x + dx, gy, z + dz)
        if best:
            return best[1:]
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("dimension")
    parser.add_argument("--island", required=True, help="NEU's island id (hub, foraging_1, combat_3, dungeon_hub)")
    parser.add_argument("map", help="the map's world folder or zip (for its blocks)")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    namespace, path = args.dimension.split(":")
    holograms = {}
    hologram_file = WORLD / "dimensions" / namespace / path / "hypixel_npcs.json"
    if hologram_file.exists():
        for h in json.loads(hologram_file.read_text(encoding="utf-8")):
            holograms.setdefault(h["name"], h)
    blocks = Blocks(world_dir(args.map) / "region")
    neu = neu_positions(args.island)
    overrides = OVERRIDES.get(args.island, {})
    mx, mz = MIDDLE.get(args.island, (0, 0))
    plan = []
    for f in sorted(NPCS.glob("*.json")):
        d = json.loads(f.read_text(encoding="utf-8"))
        if d.get("dimension") != args.dimension:
            continue
        name = d["name"]
        pos, how = None, ""
        yaw = None
        if name in holograms:
            h = holograms[name]
            hx, hz = math.floor(h["x"]), math.floor(h["z"])
            gy = ground(blocks, hx, round(h["y"]), hz)
            if gy is not None:
                pos, how = [hx + 0.5, gy, hz + 0.5], "map hologram"
        if pos is None and name in neu and name not in PREFER_OVERRIDE:
            x, y, z = (int(v) for v in neu[name])
            gy = ground(blocks, x, y, z)
            if gy is not None:
                pos, how = [x + 0.5, gy, z + 0.5], "NEU"
        if pos is None and name in overrides:
            if overrides[name][0] == "near":
                _, x, y, z = overrides[name]
                spot = nearest(blocks, x, y, z, 16) if ground(blocks, x, y, z) is None else (x, ground(blocks, x, y, z), z)
                if spot:
                    pos, how = [spot[0] + 0.5, spot[1], spot[2] + 0.5], "override (nearest standable)"
            else:
                x, y, z, yaw = overrides[name]
                pos, how = [x, y, z], "override"
        if pos is None and name in neu:
            x, y, z = (int(v) for v in neu[name])
            spot = nearest(blocks, x, y, z)
            if spot:
                pos, how = [spot[0] + 0.5, spot[1], spot[2] + 0.5], "near NEU: check"
        if pos is None:
            plan.append((f.name, name, None, "NOT PLACED: no map, NEU or override position"))
            continue
        if yaw is None:
            dx, dz = mx - pos[0], mz - pos[2]
            yaw = round(math.degrees(math.atan2(-dx, dz)))
        plan.append((f.name, name, pos + [yaw], how))
        if args.write:
            raw = f.read_bytes().decode("utf-8")
            nl = "\r\n" if "\r\n" in raw else "\n"
            d["pos"] = [float(pos[0]), float(pos[1]), float(pos[2])]
            d["yaw"] = float(yaw)
            f.write_bytes((json.dumps(d, indent=2, ensure_ascii=False) + "\n").replace("\n", nl).encode("utf-8"))
    for file, name, pos, how in plan:
        print(f"{file:28} {name:24} {str(pos):34} {how}")


if __name__ == "__main__":
    main()
