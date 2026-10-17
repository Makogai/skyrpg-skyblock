#!/usr/bin/env python3
"""Hypixel's potions and brewing, for SkyBlock's brewing stands:

    python tools/skyblock/import_potions.py

Writes (generated: overwritten each run) skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_potions/potions.json:

    {"potions": {"speed": {"name": "Speed", "max": 8, "color": "#7CAFC6",
                           "levels": {"1": {"ticks": 3600, "text": ["Grants +5 Speed."], "stats": {"speed": 5}, "special": {}}}}},
     "ingredients": {"SUGAR": {"potion": "speed", "level": 1}, ...},
     "modifiers": {"GLOWSTONE_DUST": {"levels": 1}, "REDSTONE": {"minutes": 8}, "SULPHUR": {"splash": 0.5}, ...},
     "awkward": ["NETHER_STALK"]}

Sources: NEU's POTION_<TYPE>;<LEVEL> items (tools/skyblock/cache/neu-repo.zip, MIT) for each level's text and base duration;
the Hypixel wiki mirror (docs/design/research/alchemy.md) for Speed, Strength, Regeneration and Healing's ingredients and the
modifiers. The other ingredients are our decision (each is a Brewing Ingredient in NEU, and the Alchemy XP Hypixel gives for
it fits the tier), written down in skyblock-progress.md. Each level's stats and special effects are read from its text.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_potions"
NEU = "NotEnoughUpdates-REPO-master/items/"

# Potions you can brew (event, dungeon and Rift ones are left out).
BREWABLE = ["SPEED", "STRENGTH", "REGENERATION", "HEALING", "RESISTANCE", "MANA", "ABSORPTION", "ADRENALINE", "CRITICAL", "AGILITY",
            "ARCHERY", "BURNING", "DODGE", "EXPERIENCE", "KNOCKBACK", "STUN", "VENOMOUS", "WEAKNESS", "POISON", "FIRE_RESISTANCE",
            "NIGHT_VISION", "WATER_BREATHING", "RABBIT", "JUMP_BOOST", "PET_LUCK", "HASTE", "SPELUNKER", "COLD_RESISTANCE",
            "TRUE_DEFENSE", "BLINDNESS", "WOUNDED", "SLOWNESS", "DAMAGE", "MAGIC_FIND", "SPIRIT", "STAMINA",
            "ALCHEMY_XP_BOOST", "COMBAT_XP_BOOST", "ENCHANTING_XP_BOOST", "FARMING_XP_BOOST", "FISHING_XP_BOOST",
            "FORAGING_XP_BOOST", "MINING_XP_BOOST", "INVISIBILITY"]

# The ingredient that starts each potion, at level I, III or V (wiki: Speed, Strength, Regeneration, Healing; the rest decided).
INGREDIENTS = {
    "SUGAR": ("speed", 1), "ENCHANTED_SUGAR": ("speed", 3), "ENCHANTED_SUGAR_CANE": ("speed", 5),
    "BLAZE_POWDER": ("strength", 1), "ENCHANTED_BLAZE_POWDER": ("strength", 3), "ENCHANTED_BLAZE_ROD": ("strength", 5),
    "GHAST_TEAR": ("regeneration", 1), "ENCHANTED_GHAST_TEAR": ("regeneration", 5),
    "SPECKLED_MELON": ("healing", 1), "ENCHANTED_MELON": ("healing", 3), "ENCHANTED_GLISTERING_MELON": ("healing", 5),
    "CACTUS": ("resistance", 1), "ENCHANTED_CACTUS_GREEN": ("resistance", 3), "ENCHANTED_CACTUS": ("resistance", 5),
    "MUTTON": ("mana", 1), "ENCHANTED_MUTTON": ("mana", 3), "ENCHANTED_COOKED_MUTTON": ("mana", 5),
    "GOLD_INGOT": ("absorption", 1), "ENCHANTED_GOLD": ("absorption", 3), "ENCHANTED_GOLD_BLOCK": ("absorption", 5),
    "INK_SACK:3": ("adrenaline", 1), "ENCHANTED_COCOA": ("adrenaline", 3), "ENCHANTED_COOKIE": ("adrenaline", 5),
    "FLINT": ("critical", 1), "ENCHANTED_CAKE": ("agility", 1), "FEATHER": ("archery", 1), "SAND:1": ("burning", 1),
    "RAW_FISH:1": ("dodge", 1), "INK_SACK:4": ("experience", 1), "SLIME_BALL": ("knockback", 1), "OBSIDIAN": ("stun", 1),
    "POISONOUS_POTATO": ("venomous", 1),
    "FERMENTED_SPIDER_EYE": ("weakness", 1), "ENCHANTED_FERMENTED_SPIDER_EYE": ("weakness", 5),
    "SPIDER_EYE": ("poison", 1), "ENCHANTED_SPIDER_EYE": ("poison", 3),
    "MAGMA_CREAM": ("fire_resistance", 1), "GOLDEN_CARROT": ("night_vision", 1),
    "RAW_FISH:3": ("water_breathing", 1), "ENCHANTED_PUFFERFISH": ("water_breathing", 3),
    "RABBIT_FOOT": ("rabbit", 1), "ENCHANTED_RABBIT_FOOT": ("rabbit", 3), "RABBIT": ("jump_boost", 1),
    "ENCHANTED_RABBIT_HIDE": ("pet_luck", 1), "COAL": ("haste", 1), "MITHRIL_ORE": ("spelunker", 1),
    "ENCHANTED_GLACITE": ("cold_resistance", 1), "TRUE_ESSENCE": ("true_defense", 1),
    "INK_SACK": ("blindness", 1), "ENCHANTED_INK_SACK": ("blindness", 3), "NETHERRACK": ("wounded", 1),
}
MODIFIERS = {
    "GLOWSTONE_DUST": {"levels": 1}, "ENCHANTED_GLOWSTONE_DUST": {"levels": 2}, "ENCHANTED_GLOWSTONE": {"levels": 3},
    "REDSTONE": {"minutes": 8}, "ENCHANTED_REDSTONE": {"minutes": 16}, "ENCHANTED_REDSTONE_BLOCK": {"minutes": 40},
    "ENCHANTED_REDSTONE_LAMP": {"levels": 3, "minutes": 16},
    "SULPHUR": {"splash": 0.5}, "ENCHANTED_GUNPOWDER": {"splash": 1.0},
}
COLORS = {"speed": "#7CAFC6", "strength": "#932423", "regeneration": "#CD5CAB", "healing": "#F82423", "resistance": "#99453A",
          "mana": "#3F76E4", "absorption": "#2552A5", "adrenaline": "#E4C83F", "critical": "#C93B3B", "agility": "#5A8F3A",
          "archery": "#8FD0FF", "burning": "#E46F3F", "dodge": "#9FE0B0", "experience": "#7FFF5F", "knockback": "#A0A0A0",
          "stun": "#3A3A3A", "venomous": "#4E9331", "weakness": "#484D48", "poison": "#4E9331", "fire_resistance": "#E49A3A",
          "night_vision": "#1F1FA1", "water_breathing": "#2E5299", "rabbit": "#C9A97B", "jump_boost": "#22FF4C",
          "pet_luck": "#FF7AD9", "haste": "#D9C043", "spelunker": "#5CE1E6", "cold_resistance": "#BFE6FF", "true_defense": "#EDEDED",
          "blindness": "#1F1F23", "wounded": "#7A1F1F", "slowness": "#5A6C81", "damage": "#430A09", "magic_find": "#55FFFF",
          "spirit": "#A0FFFF", "stamina": "#FFD27A", "invisibility": "#7F8392"}
STATS = {"Speed": "speed", "Strength": "strength", "Defense": "defense", "Health Regen": "health_regen", "Mining Speed": "mining_speed",
         "Mining Fortune": "mining_fortune", "True Defense": "true_defense", "Cold Resistance": "cold_resistance",
         "Crit Chance": "crit_chance", "Crit Damage": "crit_damage", "Farming Fortune": "farming_fortune", "Fishing Speed": "fishing_speed",
         "Alchemy Wisdom": "alchemy_wisdom", "Combat Wisdom": "combat_wisdom", "Enchanting Wisdom": "enchanting_wisdom",
         "Farming Wisdom": "farming_wisdom", "Fishing Wisdom": "fishing_wisdom", "Foraging Wisdom": "foraging_wisdom",
         "Mining Wisdom": "mining_wisdom"}
NAMES = "|".join(sorted(map(re.escape, STATS), key=len, reverse=True))


def clean(line):
    return re.sub(r"[-]", "", re.sub("§.", "", line)).replace("  ", " ").strip()


def effects(kind, text):
    """Stats and special effects in one level's text (lines joined)."""
    stats, special = {}, {}
    for num, name in re.findall(rf"(?:\+|Grants |Gain |by \+?)(-?\d+(?:\.\d+)?)%? ?☯? ?({NAMES})", text):
        stats[STATS[name]] = float(num)
    for name, num in re.findall(rf"Increases ({NAMES}) by \+?(\d+(?:\.\d+)?)", text):
        stats[STATS[name]] = float(num)
    m = re.search(r"Removes (\d+(?:\.\d+)?) Speed", text) or re.search(r"Grants -(\d+(?:\.\d+)?) Speed", text)
    if m:
        stats["speed"] = -float(m.group(1))
    m = re.search(r"Crit Chance by (\d+(?:\.\d+)?)% and Crit Damage by (\d+(?:\.\d+)?)%", text)
    if m:
        stats["crit_chance"], stats["crit_damage"] = float(m.group(1)), float(m.group(2))
    m = re.search(r"\+(\d+(?:\.\d+)?)% chance to find rare items", text)
    if m:
        stats["magic_find"] = float(m.group(1))
    m = re.search(r"\+(\d+(?:\.\d+)?)% chance to find pets", text)
    if m:
        stats["pet_luck"] = float(m.group(1))
    m = re.search(r"\+(\d+(?:\.\d+)?) of absorption", text)
    if m:
        special["absorption"] = float(m.group(1))
    m = re.search(r"Heals for \+(\d+(?:\.\d+)?) every second", text)
    if m:
        special["heal_per_second"] = float(m.group(1))
    m = re.search(r"Jump Boost (I+)", text)
    if m:
        special["jump"] = len(m.group(1))
    if kind == "jump_boost":
        special["jump"] = 0
    m = re.search(r"chance for mob attacks to miss by (\d+(?:\.\d+)?)%|Mob attacks have a (\d+(?:\.\d+)?)% chance to miss", text)
    if m:
        special["miss"] = float(m.group(1) or m.group(2))
    m = re.search(r"bow damage by (\d+(?:\.\d+)?)%", text)
    if m:
        special["bow_damage"] = float(m.group(1))
    m = re.search(r"(\d+(?:\.\d+)?)% reduced damage from fire and lava", text)
    if m:
        special["fire_reduction"] = float(m.group(1))
    m = re.search(r"(\d+(?:\.\d+)?)% chance of not taking drowning damage", text)
    if m:
        special["drown_chance"] = float(m.group(1))
    m = re.search(r"Grants \+(\d+) ?✎? Mana per second", text)
    if m:
        special["mana_per_second"] = float(m.group(1))
    m = re.search(r"Instantly heals for \+(\d+(?:\.\d+)?)", text)
    if m:
        special["instant_heal"] = float(m.group(1))
    for vanilla in ("night_vision", "invisibility", "blindness"):
        if kind == vanilla:
            special[vanilla] = 1
    return stats, special


def ticks(text, fallback):
    m = re.search(r"\((\d+):(\d+)(?::(\d+))?\)", text)
    if not m:
        return fallback
    parts = [int(p) for p in m.groups() if p is not None]
    seconds = parts[0] * 3600 + parts[1] * 60 + parts[2] if len(parts) == 3 else parts[0] * 60 + parts[1]
    return seconds * 20


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    potions = {}
    for kind in BREWABLE:
        levels = {}
        name = None
        for level in range(1, 10):
            path = f"{NEU}POTION_{kind};{level}.json"
            if path not in neu.namelist():
                continue
            item = json.loads(neu.read(path))
            lore = [clean(line) for line in item["lore"]]
            body = [line for line in lore[1:] if line and not re.fullmatch(r"(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|SPECIAL)", line)]
            head = lore[1] if len(lore) > 1 else ""
            name = name or re.sub(r" [IVX]+( \(.*\))?$", "", head)
            text = " ".join(body[1:])
            stats, special = effects(kind.lower(), text)
            rarity = next((line for line in reversed(lore) if re.fullmatch(r"(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|SPECIAL)", line)),
                          "COMMON")
            levels[str(level)] = {"ticks": ticks(head, 0), "rarity": rarity, "text": body[1:], "stats": stats, "special": special}
        if levels:
            potions[kind.lower()] = {"name": name, "max": max(int(k) for k in levels), "color": COLORS.get(kind.lower(), "#B98CFF"),
                                     "levels": levels}
    ingredients = {item: {"potion": potion, "level": level} for item, (potion, level) in INGREDIENTS.items() if potion in potions}
    OUT.mkdir(parents=True, exist_ok=True)
    data = {"potions": potions, "ingredients": ingredients, "modifiers": MODIFIERS, "awkward": ["NETHER_STALK"]}
    (OUT / "potions.json").write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(potions)} potions, {len(ingredients)} ingredients -> {OUT / 'potions.json'}")


if __name__ == "__main__":
    main()
