#!/usr/bin/env python3
"""Hypixel's enchantments, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_enchants.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_enchants/<id>.json (generated: wiped each run):

    {"name": "Sharpness", "categories": ["SWORD", "LONGSWORD", "GAUNTLET", ...], "table_max": 5, "max": 7,
     "xp": [10, 15, 20, 25, 30, 100, 200], "conflicts": ["smite", ...], "ultimate": false, "combinable": true,
     "levels": {"1": {"lore": ["§7Increases melee damage dealt by", "§a5%"], "value": 5, "rarity": "COMMON"}, ...}}

constants/enchants.json gives which item categories take each enchant, the conflict pools, the XP cost of every level
and how high the Enchantment Table goes ("table_max"; absent = books only). The enchanted book items (<ID>;<level>)
give each level's lore (what it does, with its number: "value" is the first number in it) and the book's rarity; the
highest book is "max". The id is the one Hypixel stores on items (four books are named differently: AIMING is
dragon_tracer, PROSECUTE is prosecute...).
"""
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_enchants"
NEU = "NotEnoughUpdates-REPO-master/"
# NEU's item kinds -> Hypixel item categories.
KINDS = {"FISHING ROD": "FISHING_ROD", "FISHING WEAPON": "FISHING_WEAPON", "FARMING TOOL": "FARMING_TOOL",
         "CARNIVAL MASK": "CARNIVAL_MASK"}
ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10}
NUMBER = re.compile(r"[-+]?\d+(?:[.,]\d+)?")
FORMATTING = re.compile("§.")
# Hypixel's own font icons (private-use characters, e.g. before "Crit Damage") draw as boxes here: dropped.
ICONS = re.compile("[-] ?")


def book_lore(lore):
    """The enchant's own lines on a book: after its name line, up to the first blank line."""
    out = []
    started = False
    for line in lore:
        plain = FORMATTING.sub("", line).strip()
        if not started:
            if line.startswith("§9") or line.startswith("§d"):      # the enchant's name line (blue, or pink for ultimates)
                started = True
            continue
        if not plain:
            break
        out.append(ICONS.sub("", line))
    return out


def value_of(lines):
    for line in lines:
        match = NUMBER.search(FORMATTING.sub("", line))
        if match:
            return float(match.group().replace(",", ""))
    return None


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    consts = json.loads(neu.read(NEU + "constants/enchants.json"))
    # Book ids that differ from the stored enchant id.
    book_name = {i.lower(): b.upper() for i, b in zip(consts["enchant_mapping_id"], consts["enchant_mapping_item"])}
    categories = {}
    for kind, ids in consts["enchants"].items():
        category = KINDS.get(kind, kind)
        for raw in ids:
            enchant = raw.lower()
            enchant = next((i for i, b in book_name.items() if b.lower() == enchant), enchant)
            categories.setdefault(enchant, set()).add(category)
    pools = [[e.lower() for e in pool] for pool in consts["enchant_pools"]]
    xp = {k.lower(): v for k, v in consts["enchants_xp_cost"].items()}
    table = {k.lower(): v for k, v in consts["max_xp_table_levels"].items()}

    books = {}
    for name in neu.namelist():
        match = re.fullmatch(NEU + r"items/([A-Z0-9_]+);(\d+)\.json", name)
        if match:
            books.setdefault(match.group(1), {})[int(match.group(2))] = json.loads(neu.read(name))

    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    written = 0
    for enchant in sorted(categories):
        levels_books = books.get(book_name.get(enchant, enchant.upper()), {})
        if not levels_books:
            print("  no books for", enchant)
            continue
        levels = {}
        display = None
        combinable = True
        for level, book in sorted(levels_books.items()):
            lore = book.get("lore", [])
            name_line = next((l for l in lore if l.startswith("§9") or l.startswith("§d")), "")
            if display is None and name_line:
                words = FORMATTING.sub("", name_line).split()
                display = " ".join(words[:-1]) if words and words[-1] in ROMAN else " ".join(words)
            if lore and "Combinable" not in FORMATTING.sub("", lore[0]):
                combinable = False
            description = book_lore(lore)
            rarity = FORMATTING.sub("", lore[-1]).strip().split(" ")[0] if lore else "COMMON"
            entry = {"lore": description, "rarity": rarity}
            value = value_of(description)
            if value is not None:
                entry["value"] = value
            levels[str(level)] = entry
        out = {"name": display or enchant.replace("_", " ").title(), "categories": sorted(categories[enchant]),
               "max": max(levels_books), "xp": xp.get(enchant, []), "conflicts": sorted({e for pool in pools if enchant in pool
                                                                                         for e in pool} - {enchant}),
               "ultimate": enchant.startswith("ultimate_"), "combinable": combinable, "levels": levels}
        if enchant in table:
            out["table_max"] = table[enchant]
        (OUT / f"{enchant}.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        written += 1
    print(f"{written} enchantments -> {OUT}")


if __name__ == "__main__":
    main()
