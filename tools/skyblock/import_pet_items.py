#!/usr/bin/env python3
"""Hypixel's pet items, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_pet_items.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_pet_items/pet_items.json (generated):

    {"PET_ITEM_BIG_TEETH_COMMON": {"text": "Grants +5 Crit Chance.", "stats": {"crit_chance": 5}},
     "PET_ITEM_ALL_SKILLS_BOOST_COMMON": {"text": "...", "exp": {"ALL": 10}},
     "PET_ITEM_TEXTBOOK": {"text": "...", "multiply": {"intelligence": 100}},
     "PET_ITEM_TIER_BOOST": {"text": "...", "tier_boost": true}, ...}

Each item's effect is read from its NEU lore, as Hypixel words it: "Grants +5 Crit Chance and +20 Speed.", "Gives +20%
pet exp for Combat.", "Increases the pet's Intelligence by 100%.", "Boosts the rarity of your pet by 1 tier". Items whose
effect isn't one of those keep only their text (shown, not working yet).
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_pet_items"
NEU = "NotEnoughUpdates-REPO-master/"
FORMATTING = re.compile("§.")
ICONS = re.compile("[-]")
SKIP = ("give it this item", "Consumed on use", "powerful ways", "any time!", "must be visible", "Right-click", "PET ITEM", "Pet items", "Pet Item")
STAT = re.compile(r"\+(\d+(?:\.\d+)?)\s+([A-Z][A-Za-z ]+?)(?=\s+and\b|[.,]|$| while)")
EXP = re.compile(r"Gives \+(\d+)% pet exp for ([A-Za-z ]+?)\.")
MULTIPLY = re.compile(r"Increases the pet's\s+([A-Za-z ]+?)\s+by\s+(\d+)%")


def stat_id(name):
    return name.strip().lower().replace(" ", "_")


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    out = {}
    for name in sorted(neu.namelist()):
        if "/items/PET_ITEM_" not in name or not name.endswith(".json"):
            continue
        item = json.loads(neu.read(name))
        lines = [ICONS.sub("", FORMATTING.sub("", l)).strip() for l in item.get("lore", [])]
        body = [l for l in lines if l and not any(s in l for s in SKIP) and not l.endswith("PET ITEM")
                and l not in ("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL")]
        text = re.sub(r"\s+", " ", " ".join(body)).strip()
        effect = {"text": text}
        exp = EXP.search(text)
        if exp:
            skill = exp.group(2).strip()
            effect["exp"] = {"ALL" if skill == "all skills" else skill.upper(): float(exp.group(1))}
        if "Increases the pet's" in text:
            # "Increases the pet's Crit Damage by 40% and Crit Chance by 40%."
            clause = text.split("Increases the pet's", 1)[1].split(".")[0]
            parts = [re.match(r"\s*([A-Za-z ]+?)\s+by\s+(\d+)%", part) for part in clause.split(" and ")]
            multiply = {stat_id(m.group(1)): float(m.group(2)) for m in parts if m}
            if multiply:
                effect["multiply"] = multiply
        if "rarity of your pet by 1" in text:
            effect["tier_boost"] = True
        if text.startswith("Grants"):
            stats = {stat_id(m.group(2)): float(m.group(1)) for m in STAT.finditer(text.split(".")[0])}
            if stats:
                effect["stats"] = stats
        out[item["internalname"]] = effect
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "pet_items.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    working = sum(1 for e in out.values() if len(e) > 1)
    print(f"{len(out)} pet items ({working} with an effect we apply) -> {OUT / 'pet_items.json'}")


if __name__ == "__main__":
    main()
