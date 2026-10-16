"""Builds Hypixel SkyBlock's skills: XP per level, and what every level gives.

    python tools/skyblock/import_skills.py

Sources: the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip) for the XP table and level caps, and the
fandom wiki's Mining leveling rewards table (cached in tools/skyblock/cache/wiki/) for the coins every level pays,
which is the same for every skill. Each skill's stats per level and its perk are Hypixel's, written down below
(the wiki keeps them in a different table layout per skill). Writes
skills/src/main/resources/data/skyrpg_skills/skyrpg/skills/<skill>.json:

    {"name": "Mining", ..., "levels": [50, 125, ...],                    # XP for each level
     "perk": "Spelunker", "rewards": {"mining_fortune": 4},             # the perk, per level
     "level_rewards": [{"stats": {"defense": 1}, "coins": 100, "sbxp": 5}, ...]}
"""
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
OUT = ROOT / "skills/src/main/resources/data/skyrpg_skills/skyrpg/skills"


def brackets(*spans):
    """[(first, last, value), ...] as a per-level list from level 1."""
    out = []
    for first, last, value in spans:
        out.extend([value] * (last - first + 1))
    return out


# name, colour, icon, max level, perk, perk stats per level, perk words (when there's no stat), stats per level, how to level it.
SKILLS = {
    "farming": ("Farming", "yellow", "minecraft:golden_hoe", 60, "Farmhand", {"farming_fortune": 4}, "",
                {"health": brackets((1, 14, 2), (15, 19, 3), (20, 25, 4), (26, 60, 5))},
                "Harvest crops, and kill farm animals."),
    "mining": ("Mining", "aqua", "minecraft:stone_pickaxe", 60, "Spelunker", {"mining_fortune": 4}, "",
               {"defense": brackets((1, 14, 1), (15, 60, 2))},
               "Mine stone and ores. Rarer ores give more XP."),
    "combat": ("Combat", "red", "minecraft:stone_sword", 60, "Warrior", {}, "+4% damage to mobs a level (+1% after 50)",
               {"crit_chance": brackets((1, 60, 0.5))},
               "Kill mobs."),
    "foraging": ("Foraging", "dark_green", "minecraft:jungle_sapling", 50, "Logger", {"foraging_fortune": 4}, "",
                 {"strength": brackets((1, 14, 1), (15, 50, 2))},
                 "Chop down trees."),
    "fishing": ("Fishing", "blue", "minecraft:fishing_rod", 50, "Treasure Hunter", {"treasure_chance": 0.1}, "",
                {"health": brackets((1, 14, 2), (15, 19, 3), (20, 25, 4), (26, 50, 5))},
                "Fish, and kill sea creatures."),
    "enchanting": ("Enchanting", "light_purple", "minecraft:enchanting_table", 60, "Conjurer", {}, "+4% experience orbs",
                   {"intelligence": brackets((1, 14, 1), (15, 60, 2)), "ability_damage": brackets((1, 60, 0.5))},
                   "Enchant items at the enchanting table and anvil."),
    "alchemy": ("Alchemy", "gold", "minecraft:brewing_stand", 50, "Brewer", {}, "+1% potion duration",
                {"intelligence": brackets((1, 14, 1), (15, 50, 2))},
                "Brew potions."),
    "taming": ("Taming", "dark_aqua", "minecraft:bone", 60, "Zoologist", {}, "+1% pet XP",
               {"pet_luck": brackets((1, 60, 1))},
               "Level up your pets."),
    "carpentry": ("Carpentry", "dark_red", "minecraft:crafting_table", 50, "", {}, "",
                  {"health": brackets((1, 50, 1))},
                  "Craft items."),
}
ORDER = ["farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry"]


def coins_per_level():
    """The coins each level pays, from the wiki's Mining table ("100", "7.5k", "1M")."""
    path = CACHE / "wiki/skill_Mining.json"
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        url = "https://hypixel-skyblock.fandom.com/api.php?action=parse&page=Mining/Leveling_Rewards&prop=wikitext&format=json"
        request = urllib.request.Request(url, headers={"User-Agent": "skyrpg-skyblock-importer"})
        path.write_bytes(urllib.request.urlopen(request, timeout=60).read())
    text = json.loads(path.read_text(encoding="utf-8"))["parse"]["wikitext"]["*"]
    out = []
    for row in text.split("{{Romnum|")[1:]:
        m = re.search(r"\{\{c\|\+?([\d.,]+)([kKmM]?)\}\}", row)
        value = float(m.group(1).replace(",", "")) * {"": 1, "k": 1_000, "m": 1_000_000}[m.group(2).lower()]
        out.append(int(value))
    return out


def sbxp(level):
    return 5 if level <= 10 else 10 if level <= 25 else 20 if level <= 50 else 30


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    leveling = json.loads(neu.read("NotEnoughUpdates-REPO-master/constants/leveling.json"))
    xp = leveling["leveling_xp"]
    coins = coins_per_level()
    OUT.mkdir(parents=True, exist_ok=True)
    for index, key in enumerate(ORDER):
        name, color, icon, cap, perk, perk_stats, perk_text, stats, description = SKILLS[key]
        level_rewards = []
        for level in range(1, cap + 1):
            level_rewards.append({"stats": {stat: values[level - 1] for stat, values in stats.items()},
                                  "coins": coins[level - 1], "sbxp": sbxp(level)})
        out = {"name": name, "color": color, "order": index + 1, "icon": icon, "description": description,
               "levels": xp[:cap], "perk": perk, "rewards": perk_stats, "level_rewards": level_rewards}
        if perk_text:
            out["perk_text"] = perk_text
        (OUT / (key + ".json")).write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {len(ORDER)} skills to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
