#!/usr/bin/env python3
"""Hypixel's Bestiary, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_bestiary.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/bestiary/bestiary.json (generated: overwritten):

    {"brackets": {"1": [20, 40, ...], ...},
     "islands": [{"id": "hub", "name": "Hub", "icon": "<head texture>",
                  "families": [{"id": "crypt_ghoul", "name": "Crypt Ghoul", "texture": "...", "cap": 25000, "bracket": 2,
                                "mobs": ["unburried_zombie_30"]}]}]}

Source: constants/bestiary.json: each island's mob families with their head, the kills each tier needs (a bracket: the
kills for tiers 1 to 25, cut off at the family's cap) and Hypixel's internal mob ids. A kill counts for the family whose
name is the mob's name (our mobs carry Hypixel's names).
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/bestiary"
NEU = "NotEnoughUpdates-REPO-master/"
FORMATTING = re.compile("§.")


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    data = json.loads(neu.read(NEU + "constants/bestiary.json"))
    islands = []
    seen = set()
    for key, island in data.items():
        if not isinstance(island, dict) or "mobs" not in island:
            continue
        families = []
        for mob in island["mobs"]:
            name = FORMATTING.sub("", mob.get("name", "")).strip()
            fid = slug(name)
            if not name or fid in seen:
                continue
            seen.add(fid)
            families.append({"id": fid, "name": name, "texture": mob.get("texture", ""), "cap": mob.get("cap", 0),
                             "bracket": mob.get("bracket", 1), "mobs": mob.get("mobs", [])})
        if families:
            islands.append({"id": key, "name": FORMATTING.sub("", island.get("name", key)).strip(),
                            "icon": island.get("icon", {}).get("texture", ""), "families": families})
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "bestiary.json").write_text(json.dumps({"brackets": data["brackets"], "islands": islands}, indent=1) + "\n", encoding="utf-8")
    print(f"{sum(len(i['families']) for i in islands)} families on {len(islands)} islands -> {OUT / 'bestiary.json'}")


if __name__ == "__main__":
    main()
