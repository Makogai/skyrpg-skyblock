#!/usr/bin/env python3
"""Hypixel's pets, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_pets.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_pets/<pet>.json (generated: wiped each run):

    {"name": "Bee", "type": "FARMING", "skin": "<head texture>", "max_level": 100,
     "rarities": {"COMMON": {"lore": ["§7Strength: §c+{STRENGTH}", ...], "stats": [{lvl 1}, {lvl 100}],
                             "numbers": [[lvl 1], [lvl 100]]}, ...}}

and .../skyblock_pet_levels/levels.json: the XP for each level, the rarity offsets (a Legendary pet starts 20 entries
into the table) and the pets that level differently (Golden Dragon to 200).

Sources: each pet's NEU items <PET>;<0-5>.json (COMMON to MYTHIC: the name, the head's texture and the lore, with
{LVL}, {STRENGTH}... and {0}, {1}... for its ability numbers); constants/petnums.json (stats and ability numbers at level
1 and 100 for each rarity; Hypixel goes in a straight line between them); constants/pets.json (the XP table, rarity
offsets, skill types).
"""
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
OUT = DATA / "skyblock_pets"
LEVELS = DATA / "skyblock_pet_levels"
NEU = "NotEnoughUpdates-REPO-master/"
RARITIES = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"]
TEXTURE = re.compile(r'Value:\\?"([A-Za-z0-9+/=]+)\\?"')
FORMATTING = re.compile("§.")
# Hypixel's own font icons (private-use characters) draw as boxes here: dropped.
ICONS = re.compile("[\ue000-\uf8ff] ?")


def lore_template(lore):
    """The lines worth keeping: after the "Farming Pet" line, up to the "Right-click to add" hint."""
    out = []
    for line in lore[1:]:
        if "Right-click" in line or "to add this pet" in line:
            break
        out.append(ICONS.sub("", line))
    while out and not FORMATTING.sub("", out[-1]).strip():
        out.pop()
    while out and not FORMATTING.sub("", out[0]).strip():
        out.pop(0)
    return out


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    nums = json.loads(neu.read(NEU + "constants/petnums.json"))
    consts = json.loads(neu.read(NEU + "constants/pets.json"))
    types = consts["pet_types"]
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    written = 0
    for pet, by_rarity in sorted(nums.items()):
        out = {"name": consts["id_to_display_name"].get(pet, pet.replace("_", " ").title()), "type": types.get(pet, "COMBAT"),
               "max_level": 200 if consts["custom_pet_leveling"].get(pet, {}).get("max_level") == 200 else 100, "rarities": {}}
        for index, rarity in enumerate(RARITIES):
            name = f"{NEU}items/{pet};{index}.json"
            if rarity not in by_rarity or name not in neu.namelist():
                continue
            item = json.loads(neu.read(name))
            display = FORMATTING.sub("", item.get("displayname", "")).replace("[Lvl {LVL}]", "").strip()
            if display:
                out["name"] = display
            if "skin" not in out:
                match = TEXTURE.search(item.get("nbttag", ""))
                if match:
                    out["skin"] = match.group(1)
            first, last = by_rarity[rarity].get("1", {}), by_rarity[rarity].get("100", {})
            out["rarities"][rarity] = {
                "lore": lore_template(item.get("lore", [])),
                "stats": [{k.lower(): v for k, v in first.get("statNums", {}).items()},
                          {k.lower(): v for k, v in last.get("statNums", {}).items()}],
                "numbers": [first.get("otherNums", []), last.get("otherNums", [])],
            }
        if not out["rarities"]:
            continue
        (OUT / f"{pet.lower()}.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        written += 1
    if LEVELS.exists():
        shutil.rmtree(LEVELS)
    LEVELS.mkdir(parents=True)
    custom = {pet: {"max_level": c.get("max_level", 100), "levels": c.get("pet_levels", [])}
              for pet, c in consts["custom_pet_leveling"].items()}
    (LEVELS / "levels.json").write_text(json.dumps({"levels": consts["pet_levels"], "offsets": consts["pet_rarity_offset"],
                                                    "custom": custom}, indent=1) + "\n", encoding="utf-8")
    print(f"{written} pets -> {OUT}")


if __name__ == "__main__":
    main()
