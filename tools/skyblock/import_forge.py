#!/usr/bin/env python3
"""Hypixel's Forge recipes, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_forge.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/forge/recipes.json (generated: overwritten each run):

    {"recipes": [{"output": "REFINED_MITHRIL", "count": 1, "inputs": [{"item": "ENCHANTED_MITHRIL", "count": 160}],
                  "coins": 0, "seconds": 21600, "hotm": 2}, ...]}

Sources: every NEU item's "forge" recipes (inputs "ID:count"; SKYBLOCK_COIN is the coin cost; duration in seconds).
"hotm" is the Heart of the Mountain tier the output needs (its HEART_OF_THE_MOUNTAIN requirement in our item data), at
least 2: the Forge opens at HotM 2. Outputs we don't have as items or pets are left out.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
ITEMS = DATA / "items"
PETS = DATA / "skyblock_pets"
OUT = DATA / "forge"
NEU = "NotEnoughUpdates-REPO-master/"


def known(item_id):
    if ";" in item_id:
        return (PETS / (item_id.split(";")[0].lower() + ".json")).exists()
    return (ITEMS / (item_id.lower() + ".json")).exists()


def hotm_tier(item_id):
    path = ITEMS / (item_id.lower() + ".json")
    if not path.exists():
        return 2
    data = json.loads(path.read_text(encoding="utf-8"))
    for req in data.get("hypixel", {}).get("requirements", []):
        if req.get("type") == "HEART_OF_THE_MOUNTAIN":
            return max(2, int(req.get("tier", 2)))
    return 2


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    recipes, skipped = [], []
    for name in sorted(neu.namelist()):
        if not (name.startswith(NEU + "items/") and name.endswith(".json")):
            continue
        item = json.loads(neu.read(name))
        for recipe in item.get("recipes", []):
            if recipe.get("type") != "forge":
                continue
            output = recipe.get("overrideOutputId", item["internalname"])
            inputs, coins, ok = [], 0, True
            for entry in recipe["inputs"]:
                ident, _, count = entry.rpartition(":")
                count = int(float(count))
                if ident == "SKYBLOCK_COIN":
                    coins += count
                    continue
                if not known(ident):
                    ok = False
                inputs.append({"item": ident, "count": count})
            if not ok or not known(output):
                skipped.append(output)
                continue
            recipes.append({"output": output, "count": int(float(recipe.get("count", 1))), "inputs": inputs, "coins": coins,
                            "seconds": int(recipe.get("duration", 0)), "hotm": hotm_tier(output)})
    recipes.sort(key=lambda r: (r["hotm"], r["output"]))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "recipes.json").write_text(json.dumps({"recipes": recipes}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(recipes)} forge recipes -> {OUT / 'recipes.json'}; skipped {len(skipped)}: {', '.join(sorted(set(skipped)))}")


if __name__ == "__main__":
    main()
