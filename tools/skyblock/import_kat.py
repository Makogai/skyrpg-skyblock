#!/usr/bin/env python3
"""Kat's pet upgrades, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_kat.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/kat/upgrades.json (generated: overwritten each run):

    {"upgrades": [{"pet": "ARMADILLO", "from": 0, "to": 1, "coins": 10000, "seconds": 10800, "items": ["X:2"]}, ...]}

Source: every NEU item's "katgrade" recipes (Hypixel's Kat: a pet goes up one rarity for coins, sometimes items, after a
wait). Pets we don't have are left out.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
PETS = DATA / "skyblock_pets"
OUT = DATA / "kat"
NEU = "NotEnoughUpdates-REPO-master/"


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    upgrades, seen = [], set()
    for name in sorted(neu.namelist()):
        if not (name.startswith(NEU + "items/") and name.endswith(".json")):
            continue
        try:
            item = json.loads(neu.read(name))
        except ValueError:
            continue
        for recipe in item.get("recipes", []):
            if recipe.get("type") != "katgrade":
                continue
            pet, _, low = recipe["input"].partition(";")
            _, _, high = recipe["output"].partition(";")
            key = (pet, low, high)
            if key in seen or not (PETS / (pet.lower() + ".json")).exists():
                continue
            seen.add(key)
            items = []
            for entry in recipe.get("items", []):
                ident, sep, count = entry.rpartition(":")
                items.append(f"{ident}:{int(float(count))}" if sep else f"{entry}:1")
            upgrades.append({"pet": pet, "from": int(low), "to": int(high), "coins": int(recipe.get("coins", 0)),
                             "seconds": int(recipe.get("time", 0)), "items": items})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "upgrades.json").write_text(json.dumps({"upgrades": upgrades}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(upgrades)} Kat upgrades -> {OUT / 'upgrades.json'}")


if __name__ == "__main__":
    main()
