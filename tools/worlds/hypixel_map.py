#!/usr/bin/env python3
"""Hypixel's own island maps (world downloads you have locally; never committed, they're Hypixel's builds):

    python tools/worlds/hypixel_map.py render "<world folder or .zip>" out.png [--island hub]
    python tools/worlds/hypixel_map.py install "<world folder or .zip>" skyrpg_hub:hub [--world run/server/world]

render draws the map from above (top blocks, shaded by height), with a 50-block grid and, with --island, where NEU
says each of that island's NPCs stands, to find our way around it.

install puts the map's chunks in place of one of our islands (stop the server first): the island's current region,
entities and poi folders are moved into a backup folder beside them (or deleted, if they were a Hypixel map already),
the map's chunks are copied in (Minecraft upgrades them as they load) and a hypixel_map.json marker tells the mod not
to place our own builds there (HubMaps). Undo by deleting region/ and hypixel_map.json and moving the backup back.
The download's entities are sorted on the way: decor stays (item frames, paintings, armour stands holding something),
the rest goes (Hypixel's mobs, dropped items, and the name holograms over its NPCs, whose names and places are kept
in hypixel_npcs.json beside the marker, for putting our NPCs where Hypixel's stood).
"""
import argparse
import colorsys
import datetime
import shutil
import hashlib
import json
import re
import sys
import tempfile
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
NEU_ZIP = ROOT / "tools/skyblock/cache/neu-repo.zip"

COLORS = {
    "grass_block": (98, 150, 60), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "podzol": (91, 63, 24), "farmland": (110, 75, 45),
    "grass_path": (148, 122, 65), "dirt_path": (148, 122, 65), "sand": (219, 207, 163), "red_sand": (190, 102, 33), "gravel": (136, 126, 126),
    "stone": (125, 125, 125), "cobblestone": (110, 110, 110), "andesite": (136, 136, 137), "diorite": (188, 188, 188), "granite": (149, 103, 85),
    "water": (52, 92, 200), "lava": (207, 92, 15), "snow": (240, 250, 250), "snow_block": (240, 250, 250), "ice": (145, 183, 253),
    "packed_ice": (141, 180, 250), "oak_leaves": (60, 110, 40), "spruce_leaves": (50, 85, 50), "birch_leaves": (90, 130, 60),
    "jungle_leaves": (48, 130, 30), "acacia_leaves": (80, 120, 30), "dark_oak_leaves": (40, 90, 25), "obsidian": (20, 18, 30),
    "end_stone": (219, 222, 158), "netherrack": (110, 50, 50), "clay": (160, 166, 179), "mycelium": (111, 99, 105),
}


def color(name, y, lowest, highest):
    base = name.split(":")[-1]
    rgb = COLORS.get(base)
    if rgb is None:
        if "leaves" in base:
            rgb = (60, 110, 40)
        elif any(k in base for k in ("planks", "log", "wood", "stairs", "slab", "fence")):
            rgb = (150, 115, 70) if "dark_oak" not in base and "spruce" not in base else (90, 65, 40)
        elif "wool" in base or "terracotta" in base or "concrete" in base:
            h = int(hashlib.md5(base.encode()).hexdigest()[:2], 16) / 255
            rgb = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h, 0.55, 0.8))
        else:
            h = int(hashlib.md5(base.encode()).hexdigest()[:2], 16) / 255
            rgb = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h, 0.15, 0.65))
    shade = 0.6 + 0.6 * (y - lowest) / max(1, highest - lowest)
    return tuple(max(0, min(255, int(c * shade))) for c in rgb)


def world_dir(source):
    source = Path(source)
    if source.is_dir():
        return source
    tmp = Path(tempfile.mkdtemp(prefix="hypixel_map_"))
    with zipfile.ZipFile(source) as z:
        z.extractall(tmp)
    return next(p.parent for p in tmp.rglob("level.dat"))


def tops(world):
    """{(x, z): (y, block)} for every column with a block."""
    out = {}
    for region in sorted((world / "region").glob("r.*.mca")):
        _, rx, rz, _ = region.name.split(".")
        for lx, lz, chunk in nbt.chunks(region):
            level = chunk.get("Level", chunk)
            cx, cz = level["xPos"], level["zPos"]
            sections = {}
            for s in level.get("Sections", level.get("sections", [])):
                palette = s.get("Palette") or (s.get("block_states") or {}).get("palette")
                states = s.get("BlockStates") or (s.get("block_states") or {}).get("data")
                if not palette:
                    continue
                sections[s["Y"]] = (palette, states)
            decoded = {}

            def block(x, y, z):
                sec = y >> 4
                if sec not in sections:
                    return "minecraft:air"
                if sec not in decoded:
                    palette, states = sections[sec]
                    if states is None:
                        decoded[sec] = [0] * 4096
                    else:
                        bits = max(4, (len(palette) - 1).bit_length())
                        decoded[sec] = nbt.unpack(states, bits, 4096)
                palette = sections[sec][0]
                idx = decoded[sec][((y & 15) << 8) | (z << 4) | x]
                return palette[idx]["Name"] if idx < len(palette) else "minecraft:air"

            ys = sorted(sections)
            if not ys:
                continue
            top_y = ys[-1] * 16 + 15
            for x in range(16):
                for z in range(16):
                    for y in range(top_y, ys[0] * 16 - 1, -1):
                        name = block(x, y, z)
                        if not name.endswith(("air", "barrier", "light", "structure_void")):
                            out[(cx * 16 + x, cz * 16 + z)] = (y, name)
                            break
    return out


def neu_npcs(island):
    out = []
    with zipfile.ZipFile(NEU_ZIP) as z:
        for n in z.namelist():
            if "/items/" in n and n.endswith("_NPC.json"):
                d = json.loads(z.read(n))
                if d.get("island") == island and d.get("x") is not None:
                    name = re.sub("§.", "", d.get("displayname", "")).replace(" (NPC)", "")
                    out.append((name, d["x"], d["y"], d["z"]))
    return out


def render(args):
    world = world_dir(args.world)
    columns = tops(world)
    if not columns:
        sys.exit("no blocks found")
    xs = [x for x, _ in columns]
    zs = [z for _, z in columns]
    ys = sorted(y for y, _ in columns.values())
    lowest, highest = ys[len(ys) // 20], ys[-len(ys) // 50 - 1]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    scale = args.scale
    img = Image.new("RGB", ((x1 - x0 + 1) * scale, (z1 - z0 + 1) * scale), (12, 14, 20))
    px = img.load()
    for (x, z), (y, name) in columns.items():
        c = color(name, y, lowest, highest)
        for dx in range(scale):
            for dz in range(scale):
                px[(x - x0) * scale + dx, (z - z0) * scale + dz] = c
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default()
    for gx in range((x0 // 50) * 50, x1 + 1, 50):
        draw.line([((gx - x0) * scale, 0), ((gx - x0) * scale, img.height)], fill=(255, 255, 255, 60) if gx else (255, 80, 80), width=1)
        draw.text(((gx - x0) * scale + 2, 2), str(gx), fill=(255, 255, 255), font=font)
    for gz in range((z0 // 50) * 50, z1 + 1, 50):
        draw.line([(0, (gz - z0) * scale), (img.width, (gz - z0) * scale)], fill=(255, 255, 255, 60) if gz else (255, 80, 80), width=1)
        draw.text((2, (gz - z0) * scale + 2), str(gz), fill=(255, 255, 255), font=font)
    if args.island:
        for name, x, y, z in neu_npcs(args.island):
            cx, cz = (x - x0) * scale, (z - z0) * scale
            draw.ellipse([cx - 3, cz - 3, cx + 3, cz + 3], fill=(255, 220, 0), outline=(0, 0, 0))
            draw.text((cx + 4, cz - 5), name, fill=(255, 255, 160), font=font, stroke_width=1, stroke_fill=(0, 0, 0))
    img.save(args.out)
    print(f"{args.out}: x {x0}..{x1}, z {z0}..{z1}, ground y {lowest}..{highest} ({len(columns)} columns)")


def install(args):
    world = world_dir(args.world)
    namespace, path = args.dimension.split(":")
    target = Path(args.world_dir) / "dimensions" / namespace / path
    if not (Path(args.world_dir) / "level.dat").exists():
        sys.exit(f"{args.world_dir} isn't a world folder")
    regions = sorted((world / "region").glob("r.*.mca"))
    if not regions:
        sys.exit(f"{world} has no region files")
    target.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = target / f"backup-{stamp}"
    already = (target / "hypixel_map.json").exists()
    moved = []
    for name in ("region", "entities", "poi", "hypixel_map.json", "hypixel_npcs.json"):
        if (target / name).exists():
            if already:
                shutil.rmtree(target / name) if (target / name).is_dir() else (target / name).unlink()
                continue
            backup.mkdir(exist_ok=True)
            shutil.move(str(target / name), str(backup / name))
            moved.append(name)
    (target / "region").mkdir()
    kept, dropped, holograms = 0, 0, []
    for region in regions:
        out = {}
        for index, when, raw in nbt.raw_chunks(region):
            name, root = nbt.parse_typed(raw)
            level = root.get("Level", (10, root))[1]
            if "Entities" in level:
                _, (element, entities) = level["Entities"]
                keep = []
                for entity in entities:
                    if decor(entity):
                        keep.append(entity)
                        kept += 1
                    else:
                        dropped += 1
                        hologram = label(entity)
                        if hologram:
                            holograms.append(hologram)
                level["Entities"] = (9, (element, keep))
            out[index] = (when, nbt.dump_typed(name, root))
        nbt.write_region(target / "region" / region.name, out)
    (target / "hypixel_npcs.json").write_text(json.dumps(sorted(holograms, key=lambda h: h["name"]), indent=1, ensure_ascii=False) + "\n",
                                              encoding="utf-8")
    level = nbt.load(world / "level.dat")["Data"]
    marker = {"source": Path(args.world).name, "data_version": level.get("DataVersion"), "installed": stamp,
              "note": "Hypixel's map, installed by tools/worlds/hypixel_map.py: never commit or redistribute it."}
    (target / "hypixel_map.json").write_text(json.dumps(marker, indent=1) + "\n", encoding="utf-8")
    print(f"installed {len(regions)} region files into {target}" + (f" (old {', '.join(moved)} in {backup.name})" if moved else ""))
    print(f"entities: {kept} decor kept, {dropped} dropped; {len(holograms)} name holograms -> hypixel_npcs.json")


def _text(raw):
    """A JSON text component's plain text."""
    try:
        node = json.loads(raw)
    except (ValueError, TypeError):
        return str(raw)
    def walk(n):
        if isinstance(n, str):
            return n
        if isinstance(n, list):
            return "".join(walk(x) for x in n)
        return n.get("text", "") + "".join(walk(x) for x in n.get("extra", []))
    return re.sub("§.", "", walk(node)).strip()


def decor(entity):
    """Whether a saved entity is part of the build: frames, paintings, armour stands holding or wearing something."""
    kind = entity.get("id", (8, b""))[1].decode("utf-8", "replace")
    if kind in ("minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting"):
        return True
    if kind != "minecraft:armor_stand" or "CustomName" in entity:
        return False
    for key in ("ArmorItems", "HandItems"):
        for item in entity.get(key, (10, (10, [])))[1][1]:
            if item and item.get("id", (8, b""))[1] not in (b"", b"minecraft:air"):
                return True
    return False


def label(entity):
    """A named, invisible armour stand (a hologram): its text and where it floats."""
    kind = entity.get("id", (8, b""))[1].decode("utf-8", "replace")
    if kind != "minecraft:armor_stand" or "CustomName" not in entity:
        return None
    name = _text(entity["CustomName"][1].decode("utf-8", "replace"))
    pos = [round(v, 2) for v in entity["Pos"][1][1]]
    return {"name": name, "x": pos[0], "y": pos[1], "z": pos[2]} if name else None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("render")
    r.add_argument("world")
    r.add_argument("out")
    r.add_argument("--island", default="")
    r.add_argument("--scale", type=int, default=1)
    i = sub.add_parser("install")
    i.add_argument("world")
    i.add_argument("dimension", help="our island's dimension, e.g. skyrpg_hub:hub")
    i.add_argument("--world-dir", default=str(ROOT / "run/server/world"))
    args = parser.parse_args()
    if args.cmd == "render":
        render(args)
    else:
        install(args)


if __name__ == "__main__":
    main()
