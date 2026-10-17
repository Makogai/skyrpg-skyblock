#!/usr/bin/env python3
"""Hypixel's Trophy Fish: the 18 lava fish of the Crimson Isle in four tiers.

    python tools/skyblock/trophy_fish.py

Writes (generated):
- skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items/<fish>_<tier>.json: 72 items (BLOBFISH_BRONZE ...),
  their names and descriptions from NEU (tools/skyblock/cache/neu-repo.zip), soulbound. FurfSky keys their looks on the id.
- skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/trophy_fish/trophy_fish.json: each fish's rarity, its base
  chance and where it bites (docs/design/research/crimson-isle.md, the wiki's Trophy Fishing page), and the Magmafish it
  fillets into per tier (NEU constants/trophyfish.json).
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
NEU = "NotEnoughUpdates-REPO-master/"
TIERS = ["BRONZE", "SILVER", "GOLD", "DIAMOND"]

# id: (base chance %, condition, its parameters). Conditions are checked in skyblock fishing/TrophyFishing.java.
# decision: the Golden Fish's chance "varies" on Hypixel; 5% per catch once you've fished 8 minutes without a break.
FISH = {
    "BLOBFISH": (25, "anywhere", {}),
    "GUSHER": (20, "eruption", {"zones": ["Blazing Volcano"]}),
    "OBFUSCATED_FISH_1": (25, "bait", {"bait": "CORRUPTED_BAIT"}),
    "STEAMING_HOT_FLOUNDER": (20, "geyser", {"zones": ["Blazing Volcano"], "radius": 3}),
    "SULPHUR_SKITTER": (30, "near_block", {"block": "minecraft:sponge", "radius": 4}),
    "FLYFISH": (8, "below", {"zones": ["Blazing Volcano"], "blocks": 8}),
    "OBFUSCATED_FISH_2": (20, "carried", {"item": "OBFUSCATED_FISH_1"}),
    "SLUGFISH": (15, "slow", {"seconds": 20}),
    "LAVA_HORSE": (4, "anywhere", {}),
    "MANA_RAY": (5, "mana", {"mana": 1200}),
    "OBFUSCATED_FISH_3": (10, "carried", {"item": "OBFUSCATED_FISH_2"}),
    "VANILLE": (8, "plain_rod", {"rod": "STARTER_LAVA_ROD"}),
    "VOLCANIC_STONEFISH": (4, "zone", {"zones": ["Blazing Volcano"]}),
    "KARATE_FISH": (2, "zone", {"zones": ["Dojo"]}),
    "MOLDFIN": (2, "zone", {"zones": ["Mystic Marsh", "Scarleton"]}),
    "SKELETON_FISH": (2, "zone", {"zones": ["Burning Desert", "Dragontail"]}),
    "SOUL_FISH": (2, "zone", {"zones": ["Stronghold"]}),
    "GOLDEN_FISH": (5, "streak", {"minutes": 8}),
}


def strip(text):
    return re.sub("§.", "", text)


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    fillet = json.loads(neu.read(NEU + "constants/trophyfish.json"))
    items = DATA / "items"
    fish_out = []
    for fish, (chance, condition, params) in FISH.items():
        rarity = "common"
        name = fish
        description = ""
        for tier in TIERS:
            data = json.loads(neu.read(NEU + "items/" + fish + "_" + tier + ".json"))
            lore = [strip(line) for line in data["lore"]]
            rarity = next((line.split()[0].lower() for line in lore if line.endswith("TROPHY")), "common")
            name = strip(data["displayname"]).rsplit(" ", 1)[0]
            # The line(s) saying where it's caught: between the bait lines and "Bring this to Odger".
            text = []
            for line in lore:
                if line.startswith("Bring this") or line.startswith("*"):
                    break
                if line and line not in ("Fishing Bait", "Consumes on Catch"):
                    text.append(line)
            description = " ".join(text)
            item = {"skyblock_id": fish + "_" + tier, "base": "minecraft:paper", "name": name + " " + tier, "rarity": rarity,
                    "type": "Trophy Fish", "description": "§7" + description + " Bring it to Odger at his hut by the lava.",
                    "hypixel": {"soulbound": "SOLO"}}
            (items / (fish.lower() + "_" + tier.lower() + ".json")).write_text(json.dumps(item, indent=1) + "\n", encoding="utf-8")
        fish_out.append({"id": fish, "name": name, "rarity": rarity, "chance": chance, "condition": condition, "params": params,
                         "how": description, "fillet": fillet[fish]})
    out = DATA / "trophy_fish"
    out.mkdir(parents=True, exist_ok=True)
    (out / "trophy_fish.json").write_text(json.dumps({"tiers": {"bronze": 73.353, "silver": 24.451, "gold": 1.996, "diamond": 0.2},
                                                       "fish": fish_out}, indent=1) + "\n", encoding="utf-8")
    print(f"{len(fish_out)} trophy fish, {len(fish_out) * 4} items")


if __name__ == "__main__":
    main()
