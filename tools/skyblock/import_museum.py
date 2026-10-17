#!/usr/bin/env python3
"""Hypixel's Museum, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_museum.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/museum/museum.json (generated: overwritten):

    {"categories": [{"id": "combat", "entries": [{"id": "ROGUE_SWORD", "xp": 1, "stage": "STARTER", "items": ["ROGUE_SWORD"],
                                                  "child": "..."}, ...]}, ...]}

Source: constants/museum.json: each category's donations (an item, or an armor set by its name with its pieces),
their SkyBlock XP and game stage, and which lower donation a higher one counts for ("children"). Entries whose items we
don't have are left out.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
ITEMS = DATA / "items"
OUT = DATA / "museum"
NEU = "NotEnoughUpdates-REPO-master/"
ORDER = ["combat", "farming", "mining", "fishing", "foraging", "dungeoneering", "hunting", "special"]


def exists(item_id):
    return (ITEMS / (item_id.lower() + ".json")).exists()


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    museum = json.loads(neu.read(NEU + "constants/museum.json"))
    sets = museum["sets_to_items"]
    categories = []
    kept = skipped = 0
    for cat in ORDER:
        entries = []
        for entry in museum["items"].get(cat, []):
            pieces = sets.get(entry, [entry])
            if not all(exists(p) for p in pieces):
                skipped += 1
                continue
            out = {"id": entry, "xp": museum["itemToXp"].get(entry, 0), "stage": museum["itemToStage"].get(entry, ""), "items": pieces}
            if entry in museum["children"]:
                out["child"] = museum["children"][entry]
            entries.append(out)
            kept += 1
        categories.append({"id": cat, "entries": entries})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "museum.json").write_text(json.dumps({"categories": categories}, indent=1) + "\n", encoding="utf-8")
    print(f"{kept} museum donations ({skipped} left out) -> {OUT / 'museum.json'}")


if __name__ == "__main__":
    main()
