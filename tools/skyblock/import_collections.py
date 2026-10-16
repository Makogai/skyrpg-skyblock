"""Imports Hypixel SkyBlock's collections: every tier's amount and what it unlocks.

    python tools/skyblock/import_collections.py

Source: the Hypixel collections API (cached as tools/skyblock/cache/collections.json, shared with import_recipes.py).
Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/collections/<id>.json (generated: wiped each run), in
the progress mod's collection format plus the SkyBlock items it counts:

    {"category": "mining", "name": "Cobblestone", "icon": "minecraft:cobblestone",
     "skyblock": ["COBBLESTONE"], "items": ["minecraft:cobblestone"],      # plain vanilla stacks of the base count too
     "thresholds": [50, 100, ...],
     "tiers": [{"xp": 4, "unlocks": ["Cobblestone Minion Recipes"]}, ...]}

The Rift's collections are left out until the Rift exists.
"""
import json
import re
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/collections"
SKIP = {"RIFT"}
# Collections that count more than one item.
GROUPS = {
    "MUSHROOM_COLLECTION": ["RED_MUSHROOM", "BROWN_MUSHROOM"],
    "GEMSTONE_COLLECTION": ["ROUGH_RUBY_GEM", "ROUGH_AMBER_GEM", "ROUGH_TOPAZ_GEM", "ROUGH_JADE_GEM", "ROUGH_SAPPHIRE_GEM",
                            "ROUGH_AMETHYST_GEM", "ROUGH_JASPER_GEM", "ROUGH_OPAL_GEM", "ROUGH_AQUAMARINE_GEM",
                            "ROUGH_CITRINE_GEM", "ROUGH_ONYX_GEM", "ROUGH_PERIDOT_GEM"],
}


def item(skyblock_id):
    path = ITEMS / (skyblock_id.lower().replace(":", "-") + ".json")
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def plain(d):
    """A SkyBlock item that is just its vanilla material: plain vanilla stacks of it count."""
    return d and "skin" not in d and "color" not in d and not d.get("stats") and not d.get("ability")


def collections():
    path = CACHE / "collections.json"
    if not path.exists():
        request = urllib.request.Request("https://api.hypixel.net/v2/resources/skyblock/collections",
                                         headers={"User-Agent": "skyrpg-skyblock-importer"})
        path.write_text(urllib.request.urlopen(request, timeout=60).read().decode("utf-8"), encoding="utf-8")
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    data = collections()
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    written = 0
    for category, group in data["collections"].items():
        if category in SKIP:
            continue
        for cid, info in group["items"].items():
            counted = [i for i in GROUPS.get(cid, [cid]) if item(i)]
            if not counted:
                print("no item for", cid)
                continue
            own = item(cid) or item(counted[0])
            vanilla = [item(i)["base"] for i in counted if plain(item(i))]
            tiers = []
            for tier in info["tiers"]:
                xp = 0
                unlocks = []
                for line in tier.get("unlocks", []):
                    m = re.match(r"\+(\d+) SkyBlock XP$", line)
                    if m:
                        xp += int(m.group(1))
                    else:
                        unlocks.append(line)
                tiers.append({"xp": xp, "unlocks": unlocks})
            out = {"category": category.lower(), "name": info["name"], "icon": own["base"], "skyblock": counted,
                   "items": vanilla, "thresholds": [t["amountRequired"] for t in info["tiers"]], "tiers": tiers}
            name = cid.lower().replace(":", "-")
            (OUT / (name + ".json")).write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
            written += 1
    print(f"Wrote {written} collections to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
