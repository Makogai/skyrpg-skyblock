#!/usr/bin/env python3
"""Hypixel's Frozen Corpses (Glacite Mineshafts), from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_corpses.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/glacite/corpses.json (generated: overwritten each run):

    {"corpses": {"LAPIS": {"name": "Lapis Corpse", "key": "", "rolls": [3, 6], "armor": ["LAPIS_ARMOR_HELMET", ...],
                           "loot": [{"item": "FLAWED_ONYX_GEM", "count": 20, "weight": 5.97}, ...]}, ...}}

Each roll is one weighted pick from the whole table (Hypixel's percentages add up to about 100 a roll). The keys are
Hypixel's (Umber Key, Tungsten Key, Skeleton Key; the Lapis Corpse needs none); the look is NEU's render (an invisible
armor stand in the corpse's armor). Items we don't have are left out.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
ITEMS = DATA / "items"
OUT = DATA / "glacite"
NEU = "NotEnoughUpdates-REPO-master/"
KEYS = {"LAPIS": "", "UMBER": "UMBER_KEY", "TUNGSTEN": "TUNGSTEN_KEY", "VANGUARD": "SKELETON_KEY"}


def strip(text):
    return re.sub("§.", "", text)


def exists(item):
    return (ITEMS / (item.lower().replace(":", "-") + ".json")).exists()


def entry(drop):
    ident = drop["id"]
    count = 1
    m = re.match(r"^(.*):(\d+)$", ident)
    if m:
        ident, count = m.group(1), int(m.group(2))
    if ";" in ident:
        # An enchanted book: ICE_COLD;1.
        enchant, _, level = ident.partition(";")
        if not (DATA / "skyblock_enchants" / (enchant.lower() + ".json")).exists():
            return None
        return {"item": f"book:{enchant.lower()}:{level}", "count": count, "weight": float(drop["chance"].rstrip("%"))}
    item = re.sub(r"-(\d+)$", r":\1", ident)
    if not exists(item):
        return None
    return {"item": item, "count": count, "weight": float(drop["chance"].rstrip("%"))}


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    corpses = {}
    skipped = set()
    for kind, key in KEYS.items():
        data = json.loads(neu.read(f"{NEU}items/SKYBLOCK_CORPSE_{kind}.json"))
        recipe = data["recipes"][0]
        rolls = [3, 6]
        for line in recipe.get("extra", []):
            m = re.search(r"roll (\d+) to (\d+) times", strip(line))
            if m:
                rolls = [int(m.group(1)), int(m.group(2))]
        loot = []
        for drop in recipe["drops"]:
            for option in [drop] + drop.get("alternatives", []):
                e = entry(option)
                if e:
                    loot.append(e)
                else:
                    skipped.add(option["id"])
        render = json.loads(neu.read(NEU + recipe["render"][len("@neurepo:"):]))
        armor = []
        for mod in render.get("modifiers", []):
            if mod.get("type") == "equipment":
                armor = [mod.get(slot, "") for slot in ("helmet", "chestplate", "leggings", "feet")]
        # NEU's own corpse head (the Vanguard's) isn't an item we have: a skeleton skull in its place.
        armor = [a if not a or exists(a.split("#")[0]) or a.startswith("LEATHER_") else "SKELETON_SKULL" for a in armor]
        corpses[kind] = {"name": strip(recipe["name"]), "key": key, "rolls": rolls, "armor": armor, "loot": loot}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "corpses.json").write_text(json.dumps({"corpses": corpses}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(corpses)} corpses, {sum(len(c['loot']) for c in corpses.values())} loot entries -> {OUT / 'corpses.json'}")
    if skipped:
        print("left out:", sorted(skipped))


if __name__ == "__main__":
    main()
