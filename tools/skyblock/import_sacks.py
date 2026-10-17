#!/usr/bin/env python3
"""Hypixel's Sacks, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_sacks.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/sacks/sacks.json (generated: overwritten each run):

    {"sacks": [{"type": "Mining", "icon": "LARGE_MINING_SACK", "items": ["COBBLESTONE", ...],
                "sizes": {"SMALL_MINING_SACK": 2240, "MEDIUM_MINING_SACK": 6720, "LARGE_MINING_SACK": 20160, ...}}]}

Sources: constants/sacks.json (each sack type, its biggest sack item and what it holds) and the sack items' lore
("Capacity: 2,240 of each item"). A sack item belongs to the type whose biggest sack has the same name without its
size word (SMALL_ / MEDIUM_ / LARGE_ / EXTRA_LARGE_ / BEGINNER_). Items we don't have are left out of the contents.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
ITEMS = DATA / "items"
OUT = DATA / "sacks"
NEU = "NotEnoughUpdates-REPO-master/"
FORMATTING = re.compile("§.")
SIZES = ("EXTRA_LARGE_", "LARGE_", "MEDIUM_", "SMALL_", "BEGINNER_")


def base(item_id):
    for size in SIZES:
        if item_id.startswith(size):
            return item_id[len(size):]
    return item_id


def capacity(neu, item_id):
    name = f"{NEU}items/{item_id}.json"
    if name not in neu.namelist():
        return None
    for line in json.loads(neu.read(name)).get("lore", []):
        match = re.search(r"Capacity: ([\d,]+)", FORMATTING.sub("", line))
        if match:
            return int(match.group(1).replace(",", ""))
    return None


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    sacks = json.loads(neu.read(NEU + "constants/sacks.json"))["sacks"]
    sack_items = [n[len(NEU) + 6:-5] for n in neu.namelist() if n.startswith(NEU + "items/") and n.endswith("_SACK.json")]
    out = []
    for name, sack in sorted(sacks.items()):
        key = base(sack["item"])
        sizes = {}
        for item_id in sack_items:
            if base(item_id) == key and (ITEMS / (item_id.lower() + ".json")).exists():
                cap = capacity(neu, item_id)
                if cap:
                    sizes[item_id] = cap
        items = [i for i in sack["contents"] if (ITEMS / (i.lower() + ".json")).exists()]
        if not sizes or not items:
            print(f"  skipped {name}: {len(sizes)} sack items, {len(items)} contents")
            continue
        out.append({"type": name, "icon": sack["item"] if sack["item"] in sizes else max(sizes, key=sizes.get), "items": items,
                    "sizes": dict(sorted(sizes.items(), key=lambda kv: kv[1]))})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "sacks.json").write_text(json.dumps({"sacks": out}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(out)} sack types, {sum(len(s['sizes']) for s in out)} sack items -> {OUT / 'sacks.json'}")


if __name__ == "__main__":
    main()
