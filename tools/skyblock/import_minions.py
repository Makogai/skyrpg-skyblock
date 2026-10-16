"""Builds every Hypixel minion's data: what it does, what it drops, and each tier's speed, storage and upgrade cost.

    python tools/skyblock/import_minions.py [--fetch]

Sources (cached in tools/skyblock/cache/, gitignored):
- the NotEnoughUpdates repo (MIT, github.com/NotEnoughUpdates/NotEnoughUpdates-REPO): neu-repo.zip, for each tier's
  "Time Between Actions" and "Max Storage" (from the item lore) and its crafting recipe (the upgrade cost);
- the Hypixel SkyBlock fandom wiki's minion pages (wiki/<type>_generator.json; --fetch downloads missing ones),
  for what each minion collects ("collects" in its infobox).
Writes minions/src/main/resources/data/skyrpg_minions/skyrpg/minions/<type>.json (a generated folder: wiped each run).
"""
import argparse
import json
import re
import shutil
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
OUT = ROOT / "minions/src/main/resources/data/skyrpg_minions/skyrpg/minions"
FUELS = ROOT / "minions/src/main/resources/data/skyrpg_minions/skyrpg/minion_fuels"
NEU = "NotEnoughUpdates-REPO-master/"
CELLS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"}
ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]

# How each minion works. "block": places that block in its area and mines it. "crop": plants on soil and harvests.
# "tree": plants saplings and chops the trees. "mob": spawns the mob nearby and kills it. "fishing": fishes in water.
BLOCKS = {"COBBLESTONE": "cobblestone", "OBSIDIAN": "obsidian", "GLOWSTONE": "glowstone", "GRAVEL": "gravel", "SAND": "sand",
          "RED_SAND": "red_sand", "ICE": "ice", "SNOW": "snow_block", "COAL": "coal_ore", "IRON": "iron_ore", "GOLD": "gold_ore",
          "DIAMOND": "diamond_ore", "LAPIS": "lapis_ore", "REDSTONE": "redstone_ore", "EMERALD": "emerald_ore",
          "QUARTZ": "nether_quartz_ore", "ENDER_STONE": "end_stone", "MITHRIL": "light_blue_wool", "HARD_STONE": "stone",
          "MYCELIUM": "mycelium", "CLAY": "clay"}
CROPS = {"WHEAT": "wheat", "CARROT": "carrots", "POTATO": "potatoes", "PUMPKIN": "pumpkin", "MELON": "melon",
         "MUSHROOM": "red_mushroom", "COCOA": "cocoa", "CACTUS": "cactus", "SUGAR_CANE": "sugar_cane",
         "NETHER_WARTS": "nether_wart", "FLOWER": "poppy", "SUNFLOWER": "sunflower"}
TREES = {"OAK": "oak", "SPRUCE": "spruce", "BIRCH": "birch", "DARK_OAK": "dark_oak", "ACACIA": "acacia", "JUNGLE": "jungle"}
MOBS = {"ZOMBIE": "zombie", "SKELETON": "skeleton", "SPIDER": "spider", "CAVESPIDER": "cave_spider", "CREEPER": "creeper",
        "ENDERMAN": "enderman", "GHAST": "ghast", "SLIME": "slime", "BLAZE": "blaze", "MAGMA_CUBE": "magma_cube",
        "CHICKEN": "chicken", "COW": "cow", "PIG": "pig", "SHEEP": "sheep", "RABBIT": "rabbit", "REVENANT": "zombie",
        "TARANTULA": "spider", "VOIDLING": "enderman", "INFERNO": "blaze", "VAMPIRE": "zombie"}
FISHING = {"FISHING", "LILY_PAD"}
# Drop names that don't match an item's name.
ALIASES = {"Raw Cod": "RAW_FISH", "Raw Salmon": "RAW_FISH:1", "Tropical Fish": "RAW_FISH:2", "Pufferfish": "RAW_FISH:3",
           "Seeds": "SEEDS", "White Wool": "WOOL", "Flower": "RED_ROSE", "Sunflower": "DOUBLE_PLANT", "Snowball": "SNOW_BALL",
           "Nether Quartz": "QUARTZ", "Redstone Dust": "REDSTONE", "Lapis Lazuli": "INK_SACK:4", "Melon Slice": "MELON",
           "Cocoa Beans": "INK_SACK:3", "Mithril": "MITHRIL_ORE", "Rabbit's Foot": "RABBIT_FOOT", "Raw Porkchop": "PORK",
           "Raw Beef": "RAW_BEEF", "Raw Chicken": "RAW_CHICKEN", "Raw Mutton": "MUTTON", "Raw Rabbit": "RABBIT",
           "Ender Pearl": "ENDER_PEARL", "Gunpowder": "SULPHUR", "Nether Wart": "NETHER_STALK", "Slimeball": "SLIME_BALL",
           "End Stone": "ENDER_STONE", "Clay Ball": "CLAY_BALL", "Glowstone Dust": "GLOWSTONE_DUST"}


def fetch_wiki(types, names):
    folder = CACHE / "wiki"
    folder.mkdir(parents=True, exist_ok=True)
    for gen in types:
        out = folder / (gen.lower() + ".json")
        if out.exists():
            continue
        query = urllib.parse.urlencode({"action": "parse", "page": names[gen], "prop": "wikitext", "format": "json", "redirects": 1})
        request = urllib.request.Request("https://hypixel-skyblock.fandom.com/api.php?" + query,
                                         headers={"User-Agent": "skyrpg-skyblock-data/1.0 (game data for a fan project)"})
        try:
            data = json.loads(urllib.request.urlopen(request, timeout=30).read())
            out.write_text(json.dumps({"page": names[gen], "wikitext": data["parse"]["wikitext"]["*"]}), encoding="utf-8")
        except Exception as e:  # noqa: BLE001
            print("  could not fetch", names[gen], e)
        time.sleep(1.0)


def item_names():
    by_name = {}
    for f in ITEMS.glob("*.json"):
        d = json.loads(f.read_text(encoding="utf-8"))
        by_name.setdefault(d["name"], d["skyblock_id"])
    return by_name


def parse_drops(text, by_name, gen):
    """'*1 Raw Cod %50%% *2-4 Carrot' -> [{item, min, max, chance}]."""
    drops = []
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\{\{ID\|([^}|]*)[^}]*\}\}", r"\1", text)
    for part in re.split(r"\*", text):
        part = part.strip()
        if not part:
            continue
        chance = 1.0
        m = re.search(r"%\s*([\d.]+)\s*%+%?", part)
        if m:
            chance = float(m.group(1)) / 100
            part = part[:m.start()].strip()
        part = re.sub(r"%.*", "", part).strip()
        m = re.match(r"([\d.]+)(?:\s*-\s*([\d.]+))?x?\s+(.*)", part)
        if m:
            low, high, name = float(m.group(1)), float(m.group(2) or m.group(1)), m.group(3).strip()
        else:
            low = high = 1.0
            name = part
        item = ALIASES.get(name) or by_name.get(name)
        if not item:
            print(f"  {gen}: no item named {name!r}")
            continue
        drops.append({"item": item, "min": low, "max": high, "chance": round(chance, 6)})
    return drops


def kind_of(minion):
    if minion in BLOCKS:
        return {"kind": "block", "block": "minecraft:" + BLOCKS[minion]}
    if minion in CROPS:
        return {"kind": "crop", "block": "minecraft:" + CROPS[minion]}
    if minion in TREES:
        return {"kind": "tree", "wood": TREES[minion]}
    if minion in MOBS:
        return {"kind": "mob", "mob": "minecraft:" + MOBS[minion]}
    if minion in FISHING:
        return {"kind": "fishing"}
    return {"kind": "block", "block": "minecraft:stone"}


def fuels(neu, known):
    """Every minion fuel with a plain effect: +speed% for a time (or for good), or a resource multiplier.
    Fuels with special effects (Inferno fuels, Dayswitch...) only get their speed for now."""
    if FUELS.exists():
        shutil.rmtree(FUELS)
    FUELS.mkdir(parents=True)
    written = 0
    for name in neu.namelist():
        if not name.startswith(NEU + "items/") or not name.endswith(".json"):
            continue
        item = json.loads(neu.read(name))
        lore = " ".join(re.sub("§.", "", line) for line in item.get("lore", []))
        if "Minion Fuel" not in lore or item["internalname"] not in known or item["internalname"].startswith("INFERNO_"):
            continue
        speed = re.search(r"speed of your minion by (\d+)%", lore)
        multiplier = re.search(r"resources created by (\d+)x for (\d+) hours", lore)
        duration = re.search(r"for (\d+) (minutes|hours|days)", lore)
        minutes = 0
        if duration:
            minutes = int(duration.group(1)) * {"minutes": 1, "hours": 60, "days": 1440}[duration.group(2)]
        if multiplier:
            minutes = int(multiplier.group(2)) * 60
        if not speed and not multiplier:
            continue
        out = {"item": item["internalname"], "speed": int(speed.group(1)) if speed else 0,
               "multiplier": int(multiplier.group(1)) if multiplier else 1, "minutes": minutes}
        (FUELS / (item["internalname"].lower() + ".json")).write_text(json.dumps(out) + "\n", encoding="utf-8")
        written += 1
    print(f"Wrote {written} minion fuels")


def hypixel_id(neu_id, known):
    """NEU writes damage-value items with a '-' ("INK_SACK-4"), Hypixel with a ':' ("INK_SACK:4")."""
    colon = neu_id.replace("-", ":")
    return colon if colon in known else neu_id


def npc_trades(neu):
    """What NPCs sell, by what they sell: "COBBLESTONE_GENERATOR_12" -> ["SKYBLOCK_COIN:2000000", ...]."""
    out = {}
    for name in neu.namelist():
        if not name.startswith(NEU + "items/") or not name.endswith(".json"):
            continue
        data = json.loads(neu.read(name))
        for recipe in data.get("recipes", []):
            if recipe.get("type") == "npc_shop" and recipe.get("result"):
                out.setdefault(recipe["result"].split(":")[0], recipe.get("cost", []))
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch", action="store_true", help="download wiki pages that aren't cached yet")
    args = parser.parse_args()
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    misc = json.loads(neu.read(NEU + "constants/misc.json"))
    by_name = item_names()
    known = {json.loads(f.read_text(encoding="utf-8"))["skyblock_id"] for f in ITEMS.glob("*.json")}
    trades = npc_trades(neu)
    names = {}
    for gen in misc["minions"]:
        f = ITEMS / (gen.lower() + "_1.json")
        if f.exists():
            names[gen] = re.sub(r"\s+[IVXL]+$", "", json.loads(f.read_text(encoding="utf-8"))["name"])
    if args.fetch:
        fetch_wiki(list(names), names)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    written = 0
    for gen, top in misc["minions"].items():
        if gen not in names:
            continue
        minion = gen.removesuffix("_GENERATOR")
        wiki = CACHE / "wiki" / (gen.lower() + ".json")
        category, drops = "other", []
        if wiki.exists():
            text = json.loads(wiki.read_text(encoding="utf-8"))["wikitext"]
            m = re.search(r"\|category\s*=\s*(.*?)\n", text)
            if m:
                category = re.sub(r"[\[\]]", "", m.group(1)).split()[0].lower()
            m = re.search(r"\|collects\s*=\s*(.*?)\n\|", text, re.S)
            if m:
                drops = parse_drops(m.group(1).replace("\n", " "), by_name, gen)
        tiers = []
        for tier in range(1, top + 1):
            name = f"{gen}_{tier}"
            try:
                item = json.loads(neu.read(NEU + f"items/{name}.json"))
            except KeyError:
                print("  missing", name)
                continue
            lore = [re.sub("§.", "", line) for line in item.get("lore", [])]
            action = next((float(re.findall(r"\d[\d,]*(?:\.\d+)?", l)[0].replace(",", "")) for l in lore if "Time Between Actions" in l), None)
            storage = next((int(re.findall(r"\d[\d,]*", l)[0].replace(",", "")) for l in lore if "Max Storage" in l), None)
            cost = {}
            grid = item.get("recipe") or next((r for r in item.get("recipes", []) if r.get("type") == "crafting"), None) or {}
            for slot, value in grid.items():
                if slot not in CELLS or not value:
                    continue
                part, count = value.split(":") if ":" in value else (value, "1")
                if part == f"{gen}_{tier - 1}":
                    continue  # the minion being upgraded
                if "UPGRADE_STONE" in part and part in trades:
                    # an upgrade stone an NPC sells: what the stone costs
                    for entry in trades[part]:
                        stone_part, _, stone_count = entry.rpartition(":") if ":" in entry else (entry, "", "1")
                        stone_part = "COINS" if stone_part == "SKYBLOCK_COIN" else hypixel_id(stone_part, known)
                        cost[stone_part] = cost.get(stone_part, 0) + int(float(stone_count)) * int(count)
                    continue
                part = hypixel_id(part, known)
                cost[part] = cost.get(part, 0) + int(float(count))
            if not cost and name in trades:
                # Tier XII comes from an NPC (Bulvar, Tony...): coins, materials and the tier XI minion.
                for entry in trades[name]:
                    part, _, count = entry.rpartition(":") if ":" in entry else (entry, "", "1")
                    if part == f"{gen}_{tier - 1}":
                        continue
                    part = "COINS" if part == "SKYBLOCK_COIN" else hypixel_id(part, known)
                    cost[part] = cost.get(part, 0) + int(float(count))
            tiers.append({"tier": tier, "item": name, "action": action, "storage": storage, "cost": cost})
        if not drops:
            # No wiki page (newer minions): it collects the item it's named after.
            item = by_name.get(names[gen].removesuffix(" Minion"))
            if item:
                drops = [{"item": item, "min": 1.0, "max": 1.0, "chance": 1.0}]
            else:
                print(f"  {gen}: no drops known")
        out = {"type": minion, "name": names[gen], "category": category, **kind_of(minion), "drops": drops, "tiers": tiers}
        (OUT / (minion.lower() + ".json")).write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        written += 1
    print(f"Wrote {written} minions to {OUT.relative_to(ROOT)}")
    fuels(neu, known)


if __name__ == "__main__":
    main()
