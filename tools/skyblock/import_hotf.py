#!/usr/bin/env python3
"""Hypixel's Heart of the Forest tree (Galatea), from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_hotf.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/hotf/tree.json (generated: overwritten each run), in
import_hotm.py's format: {"levels": [...], "perks": [{"id", "name", "x", "y", "max", "powder", "cost", "values", "lore"}]}.
Row y = 7 is tier 1, y = 0 tier 8. "powder" is FOREST_WHISPERS or DESERT_WHISPERS.

NEU's constants/hotflayout.json leaves most costs out (only Center of the Forest's and Forest Fisher's are there): the
others are decided here as floor((level + 1) ^ k), k from Hypixel's few known curves (Sweep 3, Foraging Fortune 3.05,
Luck of the Forest 3.07, Collector 2.9) and, for the rest, steeper for short perks and gentler for the 100-level ones.
"""
import json
import math
import re
import zipfile
from pathlib import Path

from import_hotm import Lisp, number

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/hotf"
NEU = "NotEnoughUpdates-REPO-master/"
ICONS = re.compile("[-] ?")
# floor((level + 1) ^ k) Whispers for the upgrade from `level` (decided where NEU has no cost).
CURVES = {"sweep": 3.0, "foraging_fortune": 3.05, "luck_of_the_forest": 3.07, "collector": 2.9, "deep_waters": 3.0, "hunters_luck": 3.0,
          "galateas_might": 3.0, "iron_lungs": 3.0, "250_gifts": 3.07, "daily_wishes": 2.6, "strength_boost": 3.0, "starlyn_supreme": 2.6,
          "speed_boost": 3.0, "efficient_forager": 2.6, "half_empty": 3.2, "half_full": 3.2, "ricochet": 3.5, "forest_speed": 3.0,
          "essence_fortune": 3.0, "timber": 3.5, "two_for_one": 2.6, "forest_strength": 3.0}


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    layout = json.loads(neu.read(NEU + "constants/hotflayout.json"))
    leveling = json.loads(neu.read(NEU + "constants/leveling.json"))
    lisp = Lisp(layout["prelude"])
    perks = []
    for pid, perk in layout["hotf"]["perks"].items():
        top = perk["maxLevel"]
        keys = [k for k in perk if k == "stat" or (k.startswith("stat") and k[4:5].isupper())]
        out = {"id": pid, "name": perk["name"], "x": perk["x"], "y": perk["y"], "max": top}
        powders, costs = [], []
        for level in range(1, top):
            env = {"level": level, "level0": level, "maxLevel": top}
            powder = lisp.run(perk["powder"], env) if perk["powder"].startswith("(") else perk["powder"]
            powders.append(powder or "FOREST_WHISPERS")
            if perk.get("cost"):
                cost = lisp.run(perk["cost"], env)
            else:
                cost = (level + 1) ** CURVES.get(pid, 3.0)
            costs.append(int(math.floor(cost)))
        if top > 1:
            out["powder"] = powders if len(set(powders)) > 1 else powders[:1]
            out["cost"] = costs
        else:
            out["powder"] = [perk["powder"] if not perk["powder"].startswith("(") else "FOREST_WHISPERS"]
            out["cost"] = []
        values = {}
        for key in keys:
            name = "stat" if key == "stat" else key[4].lower() + key[5:]
            try:
                values[name] = [number(lisp.run(perk[key], {"level": lv, "level0": lv, "maxLevel": top})) for lv in range(1, top + 1)]
            except KeyError:
                pass
        out["values"] = values
        lore = []
        for entry in perk.get("lore", []):
            text = entry["text"] if isinstance(entry, dict) else entry
            line = ICONS.sub("", text)
            line = re.sub(r"\{stats?([A-Z]\w*)?\}", lambda m: "{" + (m.group(1)[0].lower() + m.group(1)[1:] if m.group(1) else "stat") + "}", line)
            if isinstance(entry, dict) and "onlyIf" in entry:
                first = next((lv for lv in range(0, top + 1) if lisp.run(entry["onlyIf"], {"level": lv, "level0": lv, "maxLevel": top})), top)
                lore.append({"text": line, "from": first})
            else:
                lore.append(line)
        out["lore"] = lore
        perks.append(out)
    perks.sort(key=lambda p: (-p["y"], p["x"]))
    OUT.mkdir(parents=True, exist_ok=True)
    data = {"levels": leveling["HOTF"], "perks": perks}
    (OUT / "tree.json").write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(perks)} perks -> {OUT / 'tree.json'}")


if __name__ == "__main__":
    main()
