#!/usr/bin/env python3
"""Hypixel's Sacks, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_sacks.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/sacks/sacks.json (generated: overwritten each run):

    {"sacks": [{"type": "Mining", "icon": "LARGE_MINING_SACK", "items": ["COBBLESTONE", ...],
                "sizes": {"SMALL_MINING_SACK": 2240, "MEDIUM_MINING_SACK": 6720, "LARGE_MINING_SACK": 20160, ...}}]}

Sources: constants/sacks.json (each sack type, its biggest sack item and what it holds) and the sack items' lore
("Capacity: 2,240 of each item"). A sack item belongs to the type whose biggest sack has the same name without its
size word (SMALL_ / MEDIUM_ / LARGE_ / EXTRA_LARGE_ / BEGINNER_). Items we don't have are left out of the contents. The
Gold and Diamond Trophy Fishing Sacks and the Rune Sack aren't in NEU's list: they're added from the items we have.
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
    # The Fear Mongerer's Trick or Treat Bag isn't a sack in NEU's list: on Hypixel it holds your candy, so here it's a
    # candy-only sack (right-click it to add it, like the others).
    if (ITEMS / "trick_or_treat_bag.json").exists():
        out.append({"type": "Candy", "icon": "TRICK_OR_TREAT_BAG", "items": ["GREEN_CANDY", "PURPLE_CANDY"],
                    "sizes": {"TRICK_OR_TREAT_BAG": 100000}})
    # Hypixel's Trophy Fishing Sacks come in four tiers (NEU lists the Bronze and Silver ones): each holds 512 of its tier's
    # fish, per fish (the 18 trophy fish, the three obfuscated ones included).
    bronze = next((s for s in out if s["type"] == "Bronze Trophy Fishing"), None)
    if bronze:
        for tier in ("Gold", "Diamond"):
            sack = f"{tier.upper()}_TROPHY_FISHING_SACK"
            fish = [i.replace("_BRONZE", "_" + tier.upper()) for i in bronze["items"]]
            fish = [i for i in fish if (ITEMS / (i.lower() + ".json")).exists()]
            if (ITEMS / (sack.lower() + ".json")).exists() and fish:
                out.append({"type": f"{tier} Trophy Fishing", "icon": sack, "items": fish, "sizes": {sack: 512}})
    # The Rune Sack (14k bits at Elizabeth) holds 64 of each rune, every tier of it a rune of its own.
    runes = sorted(f.stem.upper() for f in ITEMS.glob("*_rune_[0-9]*.json"))
    if (ITEMS / "rune_sack.json").exists() and runes:
        out.append({"type": "Rune", "icon": "RUNE_SACK", "items": runes, "sizes": {"RUNE_SACK": 64}})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "sacks.json").write_text(json.dumps({"sacks": out}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(out)} sack types, {sum(len(s['sizes']) for s in out)} sack items -> {OUT / 'sacks.json'}")


if __name__ == "__main__":
    main()
