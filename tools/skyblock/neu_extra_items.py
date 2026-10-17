#!/usr/bin/env python3
"""SkyBlock items Hypixel's items API leaves out but the game needs, from NEU's item files (tools/skyblock/cache/neu-repo.zip):
the five Kuudra Keys.

    python tools/skyblock/neu_extra_items.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items/<id>.json (name, rarity, the head's skin,
description from the lore, co-op soulbound). FurfSky keys its looks on the id.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
NEU = "NotEnoughUpdates-REPO-master/items/"
IDS = ["KUUDRA_TIER_KEY", "KUUDRA_HOT_TIER_KEY", "KUUDRA_BURNING_TIER_KEY", "KUUDRA_FIERY_TIER_KEY", "KUUDRA_INFERNAL_TIER_KEY"]
RARITIES = ("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")


def strip(text):
    return re.sub("§.", "", text)


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    for ident in IDS:
        data = json.loads(neu.read(NEU + ident + ".json"))
        lore = [strip(line) for line in data["lore"]]
        rarity = next((line.split()[0] for line in reversed(lore) if line.split() and line.split()[0] in RARITIES), "RARE")
        text = []
        for line in lore:
            if not line or line.startswith("Right-click") or line.startswith("*") or line.split()[0] in RARITIES:
                if text and not line:
                    break
                continue
            text.append(line)
        item = {"skyblock_id": ident, "base": "minecraft:player_head", "name": strip(data["displayname"]), "rarity": rarity.lower(),
                "type": "", "description": "§7" + " ".join(text)}
        value = re.search(r'Value:\\?"([A-Za-z0-9+/=]+)\\?"', data["nbttag"])
        if value:
            item["skin"] = {"value": value.group(1)}
        item["hypixel"] = {"soulbound": "COOP"}
        (ITEMS / (ident.lower() + ".json")).write_text(json.dumps(item, indent=1) + "\n", encoding="utf-8")
        print(ident, item["name"], item["rarity"], "skin" if value else "no skin")


if __name__ == "__main__":
    main()
