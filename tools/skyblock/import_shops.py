"""Imports Hypixel SkyBlock's NPC shops: what every merchant sells and for what.

    python tools/skyblock/import_shops.py

Source: the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip): every <NAME>_NPC item's "npc_shop"
recipes (cost: coins as SKYBLOCK_COIN and/or items; result: the item and how many). Writes
skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_shops/<npc>.json (generated: wiped each run):

    {"name": "Farm Merchant", "island": "hub",
     "offers": [{"item": "WHEAT", "count": 3, "coins": 7}, {"item": "X", "count": 1, "coins": 0, "costs": {"Y": 4}}]}

Offers whose item or costs aren't SkyBlock items here (enchanted books, Bits, Gems, North Stars...) are left out,
as are shops with nothing left and the Rift's.
"""
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_shops"
NEU = "NotEnoughUpdates-REPO-master/items/"


def hypixel_id(neu_id, known):
    colon = neu_id.replace("-", ":")
    return colon if colon in known else neu_id


def split(entry):
    """"WHEAT:3" -> ("WHEAT", 3); "INK_SACK-4:32" keeps its id; a bare id is 1."""
    item, _, count = entry.rpartition(":") if ":" in entry else (entry, "", "1")
    try:
        return item, int(float(count))
    except ValueError:
        return entry, 1


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    known = {json.loads(f.read_text(encoding="utf-8"))["skyblock_id"] for f in ITEMS.glob("*.json")}
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    shops = offers_total = skipped = 0
    for name in neu.namelist():
        if not name.startswith(NEU) or not name.endswith("_NPC.json"):
            continue
        data = json.loads(neu.read(name))
        island = data.get("island", "")
        if island == "rift":
            continue
        offers = []
        for recipe in data.get("recipes", []):
            if recipe.get("type") != "npc_shop" or not recipe.get("result"):
                continue
            item, count = split(recipe["result"])
            item = hypixel_id(item, known)
            coins = 0
            costs = {}
            ok = item in known
            for entry in recipe.get("cost", []):
                part, amount = split(entry)
                if part == "SKYBLOCK_COIN":
                    coins += amount
                    continue
                part = hypixel_id(part, known)
                if part not in known:
                    ok = False
                costs[part] = costs.get(part, 0) + amount
            if not ok:
                skipped += 1
                continue
            offer = {"item": item, "count": count, "coins": coins}
            if costs:
                offer["costs"] = costs
            offers.append(offer)
        if not offers:
            continue
        npc = data["internalname"].removesuffix("_NPC").lower()
        title = re.sub(r"§.", "", data.get("displayname", npc)).replace(" (NPC)", "").strip()
        out = {"name": title, "island": island, "offers": offers}
        (OUT / (npc + ".json")).write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        shops += 1
        offers_total += len(offers)
    print(f"Wrote {shops} shops ({offers_total} offers, {skipped} left out) to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
