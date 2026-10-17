#!/usr/bin/env python3
"""Hypixel's gemstones on items, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_gemstones.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/gemstones/gemstones.json (generated: overwritten):

    {"types": {"RUBY": {"stat": "health", "values": {"ROUGH": {"COMMON": 1, ...}, ...}}, ...},
     "categories": {"COMBAT": ["RUBY", "AMETHYST", ...], ...},
     "removal": {"ROUGH": 1, ...},
     "slots": {"DIVAN_DRILL": {"AMBER_0": {"coins": 0, "items": []}, "JADE_0": {"coins": 50000, "items": ["FINE_JADE_GEM:20"]}}}}

Sources: constants/gemstones.json (the stat each gemstone gives, by gem tier and the item's rarity; removal costs) and
constants/gemstonecosts.json (each item's slots and what unlocking each costs). Which gemstones a category slot takes is
Hypixel's (Combat, Offensive, Defensive, Mining, Universal, Chisel).
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/gemstones"
NEU = "NotEnoughUpdates-REPO-master/"
STATS = {"Health": "health", "Mining Speed": "mining_speed", "Pristine": "pristine", "Mining Fortune": "mining_fortune",
         "Intelligence": "intelligence", "Defense": "defense", "Strength": "strength", "True Defense": "true_defense",
         "Fishing Speed": "fishing_speed", "Foraging Fortune": "foraging_fortune", "Crit Damage": "crit_damage",
         "Farming Fortune": "farming_fortune"}
CATEGORIES = {
    "COMBAT": ["RUBY", "AMETHYST", "SAPPHIRE", "JASPER", "ONYX"],
    "OFFENSIVE": ["SAPPHIRE", "JASPER"],
    "DEFENSIVE": ["RUBY", "AMETHYST", "OPAL"],
    "MINING": ["JADE", "AMBER", "TOPAZ"],
    "CHISEL": ["CITRINE", "AQUAMARINE", "PERIDOT", "ONYX"],
    "UNIVERSAL": ["RUBY", "AMETHYST", "SAPPHIRE", "JASPER", "OPAL", "JADE", "AMBER", "TOPAZ", "CITRINE", "AQUAMARINE", "PERIDOT", "ONYX"],
}


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    gems = json.loads(neu.read(NEU + "constants/gemstones.json"))
    costs = json.loads(neu.read(NEU + "constants/gemstonecosts.json"))
    types = {}
    for gem, data in gems["gemstoneTypes"].items():
        types[gem] = {"stat": STATS.get(data["statName"], data["statName"].lower().replace(" ", "_")), "values": data["stats"]}
    slots = {}
    for item, item_slots in costs.items():
        out = {}
        for slot, cost in item_slots.items():
            coins = 0
            items = []
            for entry in cost:
                ident, _, count = entry.rpartition(":")
                if ident == "SKYBLOCK_COIN":
                    coins += int(count)
                else:
                    items.append(f"{ident}:{count}")
            out[slot] = {"coins": coins, "items": items}
        slots[item] = out
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "gemstones.json").write_text(json.dumps({"types": types, "categories": CATEGORIES, "removal": gems["removalCosts"],
                                                    "slots": slots}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(types)} gemstones, {len(slots)} items with slots -> {OUT / 'gemstones.json'}")


if __name__ == "__main__":
    main()
