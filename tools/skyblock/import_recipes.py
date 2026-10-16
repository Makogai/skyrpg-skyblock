"""Imports Hypixel SkyBlock's crafting recipes and what unlocks each one.

    python tools/skyblock/import_recipes.py

Source: the NotEnoughUpdates repo (MIT, cached as tools/skyblock/cache/neu-repo.zip): every item's crafting grid and
its "Requires: Iron Ingot IV" line, which names the collection tier (or slayer level) that unlocks it. Collection names
are matched to their ids with the Hypixel collections API (cached as tools/skyblock/cache/collections.json).
Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_recipes/<output>.json (generated: wiped each run):

    {"output": "ENCHANTED_COBBLESTONE", "count": 1,
     "grid": ["", "COBBLESTONE:32", "", "COBBLESTONE:32", ...],      # 9 cells, rows top to bottom
     "requires": {"collection": "COBBLESTONE", "tier": 4}}           # or {"slayer": "zombie", "level": 3}, or none
"""
import json
import re
import shutil
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_recipes"
NEU = "NotEnoughUpdates-REPO-master/items/"
CELLS = ["A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"]
ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10, "XI": 11, "XII": 12,
         "XIII": 13, "XIV": 14, "XV": 15, "XVI": 16, "XVII": 17, "XVIII": 18, "XIX": 19, "XX": 20}


def unlocks(data, names_to_ids):
    """What each collection tier unlocks, by item: "Enchanted Cobblestone Recipe" at Cobblestone IV, and
    "Lapis Minion Recipes" (every tier of the Lapis Minion) at Lapis Lazuli I."""
    out = {}
    for category in data["collections"].values():
        for cid, info in category["items"].items():
            for tier in info["tiers"]:
                for line in tier.get("unlocks", []):
                    m = re.match(r"(.+?) Recipes?$", line)
                    if not m:
                        continue
                    for item in names_to_ids.get(m.group(1).lower(), []):
                        out.setdefault(item, {"collection": cid, "tier": tier["tier"]})
    return out


def collections():
    path = CACHE / "collections.json"
    if not path.exists():
        request = urllib.request.Request("https://api.hypixel.net/v2/resources/skyblock/collections",
                                         headers={"User-Agent": "skyrpg-skyblock-importer"})
        path.write_text(urllib.request.urlopen(request, timeout=60).read().decode("utf-8"), encoding="utf-8")
    data = json.loads(path.read_text(encoding="utf-8"))
    names = {"_data": data}
    for category in data["collections"].values():
        for cid, info in category["items"].items():
            names[info["name"].lower()] = cid
    return names


def requirement(text, by_collection):
    m = re.match(r"Requires:\s*(.+?)\s+([IVXL]+|\d+)$", text.strip())
    if not m:
        return None
    what, level = m.group(1), m.group(2)
    number = ROMAN.get(level) if not level.isdigit() else int(level)
    if what.lower().endswith(" slayer"):
        return {"slayer": what[:-len(" slayer")].lower(), "level": number}
    cid = by_collection.get(what.lower())
    if cid:
        return {"collection": cid, "tier": number}
    return {"text": text.strip()}


def slayer_requirement(req):
    """NEU's "ZOMBIE_3": Zombie Slayer 3, for recipes whose crafttext doesn't say it."""
    m = re.match(r"([A-Z]+)_(\d+)$", req or "")
    return {"slayer": m.group(1).lower(), "level": int(m.group(2))} if m else None


def hypixel_id(neu_id, known):
    """NEU writes damage-value items with a '-' ("INK_SACK-4"), Hypixel with a ':' ("INK_SACK:4")."""
    colon = neu_id.replace("-", ":")
    return colon if colon in known else neu_id


def cell_of(text, known):
    """A grid cell as "ID:count" ("" when empty, None when the item isn't a SkyBlock item). NEU leaves the count out
    when it's 1."""
    if not text:
        return ""
    item, _, count = text.rpartition(":") if ":" in text else (text, "", "1")
    item = hypixel_id(item, known)
    return f"{item}:{int(count)}" if item in known else None


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    known = set()
    names_to_ids = {}
    for f in ITEMS.glob("*.json"):
        d = json.loads(f.read_text(encoding="utf-8"))
        known.add(d["skyblock_id"])
        names_to_ids.setdefault(d["name"].lower(), []).append(d["skyblock_id"])
        # "Cobblestone Minion I".."XII" are all "Cobblestone Minion" for unlocks.
        m = re.match(r"(.+ Minion) [IVXL]+$", d["name"])
        if m:
            names_to_ids.setdefault(m.group(1).lower(), []).append(d["skyblock_id"])
    by_collection = collections()
    unlocked_by = unlocks(by_collection.pop("_data"), names_to_ids)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    written = skipped = 0
    for name in neu.namelist():
        if not name.startswith(NEU) or not name.endswith(".json"):
            continue
        item = json.loads(neu.read(name))
        output = hypixel_id(item["internalname"], known)
        if output not in known:
            continue
        grids = []
        if item.get("recipe"):
            grids.append((item["recipe"], 1))
        for recipe in item.get("recipes", []):
            if recipe.get("type") == "crafting":
                grids.append((recipe, recipe.get("count", 1)))
        req = requirement(item.get("crafttext", ""), by_collection) or slayer_requirement(item.get("slayer_req", "")) \
            or unlocked_by.get(output)
        for index, (grid, count) in enumerate(grids):
            cells = [cell_of(grid.get(cell, "") or "", known) for cell in CELLS]
            if any(cell is None for cell in cells):
                skipped += 1
                continue
            out = {"output": output, "count": int(count or 1), "grid": cells}
            if req:
                out["requires"] = req
            path = output.lower().replace(":", "-") + ("" if index == 0 else f"_{index + 1}")
            (OUT / (path + ".json")).write_text(json.dumps(out) + "\n", encoding="utf-8")
            written += 1
    print(f"Wrote {written} recipes ({skipped} skipped: an ingredient isn't a SkyBlock item) to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
