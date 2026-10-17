#!/usr/bin/env python3
"""Hypixel's Accessory Powers (Maxwell) and accessory families, for the Accessory Bag:

    python tools/skyblock/import_powers.py

Sources: the fandom wiki's "Accessory Powers/List of Accessory Powers" and "Power Stones/List of Power Stones" (cached
in tools/skyblock/cache/wiki/): each power's base stats (multiplied by the Magical Power formula in game), its unique
bonus, the Combat level it needs and, for stone powers, its Power Stone; and NEU's constants/misc.json
"talisman_upgrades" (a lower accessory stops counting once you have a higher one of its family).

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/accessory_powers/<id>.json (generated: wiped each run):

    {"name": "Forceful", "stone": "ACACIA_BIRDHOUSE", "combat": 20, "stats": {"health": 1.7, ...}, "unique": {"ferocity": 4}}

and skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/accessory_families/families.json:
{"upgrades": {"SPEED_TALISMAN": ["SPEED_RING", ...]}, "ignored": [...]}.
"""
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
OUT = DATA / "accessory_powers"
STATS = {"hp": "health", "def": "defense", "str": "strength", "cc": "crit_chance", "cd": "crit_damage", "spd": "speed",
         "int": "intelligence", "as": "attack_speed", "bas": "attack_speed", "fer": "ferocity", "tdef": "true_defense", "td": "true_defense",
         "mf": "magic_find", "cw": "combat_wisdom", "vit": "vitality", "vt": "vitality", "mend": "mending", "md": "mending",
         "ad": "ability_damage", "scc": "sea_creature_chance", "fs": "fishing_speed", "pl": "pet_luck", "hpr": "health_regen",
         "ms": "mining_speed", "mfort": "mining_fortune", "ff": "farming_fortune", "msw": "mining_wisdom", "fsw": "fishing_wisdom"}
ROMAN = {"I": 1, "V": 5, "X": 10, "L": 50}
TA = re.compile(r"\{\{TA\|([^}|]+)")
COMBAT = re.compile(r"\{\{Skl\|Combat\|([IVXL]+)\}\}")
LIST_LINE = re.compile(r"\*\s*([+-])\s*([\d.]+)\s+([a-z]+)", re.I)
STAT = re.compile(r"\{\{Stat\|short=y[^|]*\|([A-Za-z]+)\|([+-]?[\d.]+)\}\}")
# A cell starts at a line that begins with "| " (a Stats List's own "|short = y" line has no space after the bar).
CELL = re.compile(r"(?:^|\n)\|\s")


def roman(text):
    total = 0
    for i, c in enumerate(text):
        v = ROMAN.get(c, 0)
        total += -v if i + 1 < len(text) and ROMAN.get(text[i + 1], 0) > v else v
    return total


def wikitext(page):
    f = CACHE / "wiki" / ("page_" + page.replace("/", "_").replace(" ", "_") + ".json")
    return json.loads(f.read_text(encoding="utf-8"))["parse"]["wikitext"]["*"]


def stats_of(cell, unknown):
    """A cell's stats: a {{Stats List|*+N key...}} or single {{Stat|short=y|key|+N}} templates."""
    out = {}
    pairs = [(s + n, k) for s, n, k in LIST_LINE.findall(cell)] + [(v, k) for k, v in STAT.findall(cell)]
    for value, key in pairs:
        stat = STATS.get(key.lower())
        if stat is None:
            unknown.add(key)
            continue
        out[stat] = round(out.get(stat, 0) + float(value), 4)
    return out


def rows(text):
    return [r for r in re.split(r"\n\|-\s*\n", text) if "{{TA|" in r]


def cells_of(row):
    return [c.strip() for c in CELL.split(row.strip()) if c.strip()]


def main():
    names = {}
    for f in (DATA / "items").glob("*.json"):
        item = json.loads(f.read_text(encoding="utf-8"))
        names.setdefault(re.sub("§.", "", item.get("name", "")).lower(), item["skyblock_id"])
    unknown = set()
    powers = {}
    for row in rows(wikitext("Accessory Powers/List of Accessory Powers")):
        cells = cells_of(row)        # power, type, base stats, stats at some Magical Power, requirement
        name = TA.search(row).group(1).strip()
        combat = COMBAT.search(row)
        starter = "Starter" in cells[1]
        # The non-starter ones need Combat XV (the wiki says so above its table, not in it).
        powers[name] = {"name": name, "starter": starter, "combat": roman(combat.group(1)) if combat else 0 if starter else 15,
                        "stats": stats_of(cells[2], unknown)}
    for row in rows(wikitext("Power Stones/List of Power Stones")):
        cells = cells_of(row)        # slot, stone, power, base stats, stats at some MP, unique bonus, obtaining, requirement
        name = TA.search(row).group(1).strip()
        stone_name = re.search(r"\[\[([^\]|]+)", cells[1]).group(1).strip()
        combat = COMBAT.search(row)
        stone = names.get(stone_name.lower())
        if stone is None:
            print("  no item for stone", stone_name)
        powers[name] = {"name": name, "starter": False, "stone": stone, "combat": roman(combat.group(1)) if combat else 0,
                        "stats": stats_of(cells[3], unknown), "unique": stats_of(cells[5], unknown)}
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    for name, power in sorted(powers.items()):
        power = {k: v for k, v in power.items() if v not in (None, {}, False) or k in ("combat", "stats")}
        (OUT / (re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_") + ".json")).write_text(
            json.dumps(power, indent=1) + "\n", encoding="utf-8")
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    misc = json.loads(neu.read("NotEnoughUpdates-REPO-master/constants/misc.json"))
    (DATA / "accessory_families").mkdir(exist_ok=True)
    (DATA / "accessory_families" / "families.json").write_text(
        json.dumps({"upgrades": misc["talisman_upgrades"], "ignored": misc.get("ignored_talisman", [])}, indent=1) + "\n",
        encoding="utf-8")
    stones = sum(1 for p in powers.values() if p.get("stone"))
    print(f"{len(powers)} powers ({stones} from Power Stones); unknown stat keys: {sorted(unknown)}")


if __name__ == "__main__":
    main()
