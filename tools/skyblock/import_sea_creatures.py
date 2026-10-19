#!/usr/bin/env python3
"""Hypixel SkyBlock fishing: sea creatures, catches, treasure and baits.

    python tools/skyblock/import_sea_creatures.py

Writes (generated: overwritten each run) skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_fishing/:

- sea_creatures.json: {"creatures": [{"id", "name", "rarity", "fluid": "water"|"lava", "fishing_level", "weight",
  "where": {"dimension"?, "zones"?}, "condition"?, "level", "health", "damage", "fishing_xp", "combat_xp", "coins",
  "message", "look": {"entity", "baby"?, "variant"?, "mount"?, "hand"?, "head"?, "chest"?, "legs"?, "feet"?},
  "drops": [{"item", "chance", "min", "max"}]}]}
- catches.json: the normal catches (water and lava), the treasure pools (Good/Great/Outstanding) and the baits.

Sources:
- The hand table below: which pool a creature is in, its Fishing level, weight, Fishing XP, damage and catch message,
  from the Hypixel SkyBlock wiki mirror (docs/design/research/fishing.md). Our own calls are marked "decision".
- NEU's "<ID>_SC" items (tools/skyblock/cache/neu-repo.zip, MIT): level, health, rarity, combat XP, coins, drops,
  and the look (entity, equipment, head skin) from its mobs/*.json render.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
OUT = DATA / "skyblock_fishing"
ITEMS = DATA / "items"
PETS = DATA / "skyblock_pets"
NEU = "NotEnoughUpdates-REPO-master/"
HOLLOWS = "skyrpg_skyblock:crystal_hollows"
MINES = "skyrpg_skyblock:dwarven_mines"
CRIMSON = "skyrpg_skyblock:crimson_isle"

# id: (name, fluid, fishing level, weight, fishing xp, damage, where, condition, message)
# Weights are Hypixel's basic water pool (1200 Squid ... 18 Water Hydra); regional creatures add to it where they live.
CREATURES = {
    "SQUID": ("Squid", "water", 1, 1200, 25, 0, None, None, "A Squid appeared."),
    "SEA_WALKER": ("Sea Walker", "water", 1, 800, 25, 10, None, None, "You caught a Sea Walker."),
    # decision: Hypixel gives no weight for the Night Squid (Dark Bait at night only): 600.
    "NIGHT_SQUID": ("Night Squid", "water", 3, 600, 270, 0, None, "dark_bait_night", "Pitch darkness reveals a Night Squid."),
    "SEA_WITCH": ("Sea Witch", "water", 7, 700, 350, 150, None, None,
                  "It looks like you've disrupted the Sea Witch's brewing session. Watch out, she's furious!"),
    "SEA_ARCHER": ("Sea Archer", "water", 9, 550, 360, 140, None, None, "You reeled in a Sea Archer."),
    "RIDER_OF_THE_DEEP": ("Rider of the Deep", "water", 11, 400, 540, 225, None, None, "The Rider of the Deep has emerged."),
    "CATFISH": ("Catfish", "water", 13, 250, 737, 250, None, None, "Huh? A Catfish!"),
    "SEA_LEECH": ("Sea Leech", "water", 16, 160, 925, 300, None, None, "Gross! A Sea Leech!"),
    "GUARDIAN_DEFENDER": ("Guardian Defender", "water", 17, 130, 1125, 250, None, None,
                          "You've discovered a Guardian Defender of the sea."),
    "DEEP_SEA_PROTECTOR": ("Deep Sea Protector", "water", 18, 88, 1350, 300, None, None,
                           "You have awoken the Deep Sea Protector, prepare for a battle!"),
    "WATER_HYDRA": ("Water Hydra", "water", 19, 18, 2380, 400, None, None, "The Water Hydra has come to test your strength."),
    # Carrot Bait: 5% of the sea creatures are the Carrot King (weight unused).
    "CARROT_KING": ("Carrot King", "water", 15, 0, 787, 275, None, "carrot_bait", "Is that even a fish? It's the Carrot King!"),
    "OASIS_SHEEP": ("Oasis Sheep", "water", 10, 700, 40, 0, {"zones": ["Mushroom Desert"]}, None, "An Oasis Sheep appears from the water."),
    "OASIS_RABBIT": ("Oasis Rabbit", "water", 10, 300, 40, 0, {"zones": ["Mushroom Desert"]}, None, "An Oasis Rabbit appears from the water."),
    "MITHRIL_GRUBBER": ("Mithril Grubber", "water", 6, 385, 290, 150, {"dimension": MINES}, None,
                        "A leech of the mines surfaces... you've caught a Mithril Grubber."),
    "WATER_WORM": ("Water Worm", "water", 15, 300, 887, 300, {"dimension": HOLLOWS, "zones": ["Goblin Holdout"]}, None,
                   "A Water Worm surfaces!"),
    "POISONED_WATER_WORM": ("Poisoned Water Worm", "water", 17, 300, 1037, 400, {"dimension": HOLLOWS, "zones": ["Goblin Holdout"]},
                            None, "A Poisoned Water Worm surfaces!"),
    "ZOMBIE_MINER": ("Abyssal Miner", "water", 24, 90, 3640, 1300, {"dimension": HOLLOWS}, None, "An Abyssal Miner breaks out of the water!"),
    # Lava in the Crystal Hollows. decision: Hypixel's weights and Fishing XP for these weren't found; set from their health.
    # decision: the Flaming Worm also comes up in the Magma Fields (our Precursor Remnants has no lava pools).
    "FLAMING_WORM": ("Flaming Worm", "lava", 19, 300, 1100, 350, {"dimension": HOLLOWS, "zones": ["Precursor Remnants", "Magma Fields"]}, None,
                     "A Flaming Worm surfaces from the depths!"),
    "LAVA_BLAZE": ("Lava Blaze", "lava", 20, 200, 2000, 700, {"dimension": HOLLOWS, "zones": ["Magma Fields"]}, None,
                   "A Lava Blaze has surfaced from the depths!"),
    "LAVA_PIGMAN": ("Lava Pigman", "lava", 22, 200, 2100, 750, {"dimension": HOLLOWS, "zones": ["Magma Fields"]}, None,
                    "A Lava Pigman arose from the depths!"),
    # The Crimson Isle's lava (docs/design/research/crimson-isle.md). decision: the wiki pages with their weights, damage
    # and Fishing XP weren't fetched; Fishing levels are Hypixel's as remembered (XXVII Magma Slug to XLV Lord Jawbus),
    # weights fall as they get rarer, Fishing XP and damage grow with health.
    "FRIED_CHICKEN": ("Fried Chicken", "lava", 14, 5000, 300, 400, {"dimension": CRIMSON}, "hotspot", "Smells of burning. But Chicken."),
    "MAGMA_SLUG": ("Magma Slug", "lava", 27, 450, 730, 1200, {"dimension": CRIMSON}, None, "From beneath the lava appears a Magma Slug."),
    "MOOGMA": ("Moogma", "lava", 28, 400, 950, 1500, {"dimension": CRIMSON}, None, "You hear a faint Moo from the lava... A Moogma appears."),
    "LAVA_LEECH": ("Lava Leech", "lava", 30, 300, 1400, 2000, {"dimension": CRIMSON}, None, "A small but fearsome Lava Leech emerges."),
    "PYROCLASTIC_WORM": ("Pyroclastic Worm", "lava", 31, 300, 1650, 2200, {"dimension": CRIMSON}, None,
                         "You feel the heat radiating as a Pyroclastic Worm surfaces."),
    "LAVA_FLAME": ("Lava Flame", "lava", 33, 250, 1800, 2500, {"dimension": CRIMSON}, None, "A Lava Flame flies out from beneath the surface."),
    "FIRE_EEL": ("Fire Eel", "lava", 34, 200, 2200, 3000, {"dimension": CRIMSON}, None, "A Fire Eel slithers out from the depths."),
    "TAURUS": ("Taurus", "lava", 35, 150, 2700, 3500, {"dimension": CRIMSON}, None, "Taurus and his steed emerge."),
    "THUNDER": ("Thunder", "lava", 36, 18, 8000, 8000, {"dimension": CRIMSON}, None, "You hear a massive rumble as Thunder emerges."),
    "FIERY_SCUTTLER": ("Fiery Scuttler", "lava", 37, 100, 10000, 10000, {"dimension": CRIMSON}, "hotspot",
                       "A Fiery Scuttler inconspicuously waddles up to you, friends in tow."),
    "LORD_JAWBUS": ("Lord Jawbus", "lava", 45, 8, 40000, 15000, {"dimension": CRIMSON}, None,
                    "You have angered a legendary creature... Lord Jawbus has arrived."),
    "RAGNAROK": ("Ragnarok", "lava", 47, 25, 50000, 20000, {"dimension": CRIMSON}, "hotspot", "The sky darkens and the air thickens. Ragnarok has arrived."),
    # Marina's Fishing Festival: sharks replace 15% of the water's sea creatures (weights within the shark pool, Hypixel's).
    "NURSE_SHARK": ("Nurse Shark", "water", 5, 2875, 220, 80, None, "fishing_festival",
                    "A tiny fin emerges from the water, you've caught a Nurse Shark."),
    "BLUE_SHARK": ("Blue Shark", "water", 10, 1500, 650, 150, None, "fishing_festival",
                   "You spot a fin as blue as the water it came from, it's a Blue Shark."),
    "TIGER_SHARK": ("Tiger Shark", "water", 18, 500, 1485, 300, None, "fishing_festival", "A striped beast bounds from the depths, the wild Tiger Shark!"),
    "GREAT_WHITE_SHARK": ("Great White Shark", "water", 24, 100, 3200, 750, None, "fishing_festival",
                          "Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!"),
    # The Spooky Festival (from an hour before to an hour after): 15% of the water's sea creatures are these instead
    # (Spooky Bait +15%); weights within the spooky pool, Hypixel's (wiki, Oct 2026).
    "SCARECROW": ("Scarecrow", "water", 9, 2875, 320, 150, None, "spooky_festival", "Phew! It's only a Scarecrow."),
    "NIGHTMARE": ("Nightmare", "water", 14, 1500, 962, 600, None, "spooky_festival",
                  "You hear trotting from beneath the waves, you caught a Nightmare."),
    "WEREWOLF": ("Werewolf", "water", 17, 500, 1035, 250, None, "spooky_festival", "It must be a full moon, a Werewolf appears."),
    "PHANTOM_FISHER": ("Phantom Fisher", "water", 21, 100, 3140, 1000, None, "spooky_festival",
                       "The spirit of a long lost Phantom Fisher has come to haunt you."),
    "GRIM_REAPER": ("Grim Reaper", "water", 26, 25, 7800, 2100, None, "spooky_festival", "This can't be! The manifestation of death himself!"),
    # Fishing Hotspots (the wiki's "Sea Creatures/List/Hotspot": Fishing level and weight; the lava ones also above). They only
    # come up in a hotspot, 15% of the sea creature bites there (more with Hotspot Bait and Hook).
    # decision: Hypixel's Fishing XP and damage weren't found; they grow with the creature's rarity.
    "FROG_MAN": ("Frog Man", "water", 5, 5000, 400, 150, None, "hotspot", "A Frog Man leaps out of the water."),
    "INKLING": ("Inkling", "water", 7, 2875, 700, 200, None, "hotspot", "An Inkling squirts out of the water."),
    "SNAPPING_TURTLE": ("Snapping Turtle", "water", 10, 1500, 1000, 300, None, "hotspot", "A Snapping Turtle snaps at your line."),
    "MANTA_RAY": ("Manta Ray", "water", 19, 500, 2000, 500, None, "hotspot", "A Manta Ray glides up from the deep."),
    "BLUE_RINGED_OCTOPUS": ("Blue Ringed Octopus", "water", 35, 100, 6000, 1500, None, "hotspot",
                            "A Blue Ringed Octopus reaches up from the water, careful of its sting!"),
    "WIKI_TIKI": ("Wiki Tiki", "water", 40, 25, 20000, 3000, None, "hotspot", "Wiki Tiki rises from the depths, and he's angry."),
    "VOLCANIC_SNAIL": ("Volcanic Snail", "lava", 17, 2875, 1500, 1800, {"dimension": CRIMSON}, "hotspot", "A Volcanic Snail slowly crawls out of the lava."),
    "FIREPROOF_WITCH": ("Fireproof Witch", "lava", 22, 1500, 2000, 2200, {"dimension": CRIMSON}, "hotspot", "A Fireproof Witch rises from the lava."),
    "MAGMA_PILLAR": ("Magma Pillar", "lava", 31, 500, 3500, 3000, {"dimension": CRIMSON}, "hotspot", "A Magma Pillar erupts from the lava."),
    # The Agarimoo: only near an Empty Chumcap Bucket you placed (15% of the sea creature bites there).
    "AGARIMOO": ("Agarimoo", "water", 15, 0, 1012, 500, None, "agarimoo", "Your Chumcap Bucket trembles, it's an Agarimoo."),
}
# What NEU's render can't show as a mob (an invisible armour stand wearing the creature): decision.
LOOK_OVERRIDE = {"FIRE_EEL": {"entity": "minecraft:silverfish"}, "FIERY_SCUTTLER": {"entity": "minecraft:spider"},
                 # the sharks: NEU's render is an old zombie stand-in; decision: dolphins
                 "NURSE_SHARK": {"entity": "minecraft:dolphin"}, "BLUE_SHARK": {"entity": "minecraft:dolphin"},
                 "TIGER_SHARK": {"entity": "minecraft:dolphin"}, "GREAT_WHITE_SHARK": {"entity": "minecraft:dolphin"},
                 # NEU's render holds a "phantom_rod", which isn't an item
                 "PHANTOM_FISHER": {"entity": "minecraft:zombie", "hand": {"item": "minecraft:fishing_rod"}}}
# Where NEU's sea creature data is out of date (the sharks: Hypixel's wiki, Oct 2026): level, Combat XP, coins.
STATS_OVERRIDE = {"INKLING": (25, 30, 30), "MANTA_RAY": (124, 150, 186), "AGARIMOO": (81, 106, 101), "NURSE_SHARK": (22, 26, 22), "BLUE_SHARK": (52, 67, 65), "TIGER_SHARK": (99, 149, 148), "GREAT_WHITE_SHARK": (160, 401, 320),
                  "SCARECROW": (32, 39, 32), "NIGHTMARE": (77, 100, 96), "WEREWOLF": (69, 103, 103), "PHANTOM_FISHER": (157, 393, 314),
                  "GRIM_REAPER": (312, 1564, 1560)}
# NEU's lore has "1.200,000" for the Pyroclastic Worm.
HEALTH_OVERRIDE = {"MANTA_RAY": 500_000, "VOLCANIC_SNAIL": 400_000, "MAGMA_PILLAR": 5_000_000, "PYROCLASTIC_WORM": 1_200_000, "SCARECROW": 4_500, "NIGHTMARE": 35_000, "WEREWOLF": 50_000,
                   "PHANTOM_FISHER": 1_000_000, "GRIM_REAPER": 3_000_000}

# NEU has no recipe for the plain Squid: its drops from the wiki.
SQUID = {"level": 1, "coins": 1, "xp": 3, "drops": [
    {"item": "INK_SACK", "chance": 1, "min": 2, "max": 3}, {"item": "WATER_LILY", "chance": 1, "min": 1, "max": 2},
    {"item": "SQUID_BOOTS", "chance": 0.01, "min": 1, "max": 1}, {"item": "ENCHANTED_INK_SACK", "chance": 0.005, "min": 1, "max": 1}]}

ENTITIES = {"Zombie": "minecraft:zombie", "Skeleton": "minecraft:skeleton", "Witch": "minecraft:witch", "Squid": "minecraft:squid",
            "Guardian": "minecraft:guardian", "Silverfish": "minecraft:silverfish", "Eisengolem": "minecraft:iron_golem",
            "Ocelot": "minecraft:ocelot", "Chicken": "minecraft:chicken", "Rabbit": "minecraft:rabbit", "Sheep": "minecraft:sheep",
            "Endermite": "minecraft:endermite", "Blaze": "minecraft:blaze", "Pigman": "minecraft:zombified_piglin",
            "Player": "minecraft:zombie", "MagmaCube": "minecraft:magma_cube", "Mooshroom": "minecraft:mooshroom",
            "Pig": "minecraft:pig", "Phantom": "minecraft:phantom", "Horse": "minecraft:horse"}

NORMAL = {
    "water": [{"item": "RAW_FISH", "weight": 220}, {"item": "RAW_FISH:1", "weight": 150},
              {"item": "RAW_FISH:2", "weight": 100}, {"item": "RAW_FISH:3", "weight": 80}],
    # decision: Hypixel's Crystal Hollows lava catches weren't found; the lava items Hypixel gives Fishing XP for.
    "lava": [{"item": "NETHERRACK", "weight": 30}, {"item": "MAGMA_CREAM", "weight": 20}, {"item": "BLAZE_POWDER", "weight": 15},
             {"item": "GLOWSTONE_DUST", "weight": 15}, {"item": "QUARTZ", "weight": 10}, {"item": "NETHER_STALK", "weight": 10}],
}

# Hypixel's water treasure. The wiki mirror's Great and Outstanding lists came back short; the rows marked "filled" are
# our decision (Hypixel's enchanted fishing items), so each tier adds up to 100.
TREASURE = {
    "good": [("RAW_FISH", 16, 22), ("RAW_FISH:1", 16, 15), ("RAW_FISH:2", 12, 10), ("CLAY_BALL", 32, 10), ("SPONGE", 16, 10),
             ("RAW_FISH:3", 12, 8), ("PRISMARINE_SHARD", 8, 5), ("PRISMARINE_CRYSTALS", 8, 5), ("GRAND_EXP_BOTTLE", 4, 5),
             ("coins:25000-50000", 1, 10)],
    "great": [("ENCHANTED_RAW_FISH", 4, 19.64), ("ENCHANTED_RAW_SALMON", 4, 13.39), ("ENCHANTED_CLOWNFISH", 3, 8.93),
              ("TITANIC_EXP_BOTTLE", 1, 8.93), ("pet:SQUID:COMMON", 1, 6.70), ("coins:100000-250000", 1, 8.93),
              ("ENCHANTED_PUFFERFISH", 3, 8.93), ("ENCHANTED_CLAY_BALL", 4, 8.93), ("ENCHANTED_SPONGE", 1, 8.93),
              ("ENCHANTED_INK_SACK", 2, 6.70)],  # the last four: filled
    "outstanding": [("ENCHANTED_RAW_FISH", 32, 17.39), ("pet:SQUID:EPIC", 1, 15.81), ("ENCHANTED_RAW_SALMON", 32, 11.86),
                    ("pet:SQUID:LEGENDARY", 1, 7.91), ("coins:500000-1000000", 1, 7.91),
                    ("ENCHANTED_PUFFERFISH", 32, 11.86), ("ENCHANTED_CLOWNFISH", 24, 11.86), ("ENCHANTED_SPONGE", 8, 7.91),
                    ("ENCHANTED_PRISMARINE_CRYSTALS", 8, 7.49)],  # the last four: filled
}
TIERS = {"good": 89, "great": 10, "outstanding": 1}

# Hypixel's baits: Fishing Speed, Sea Creature Chance, Treasure Chance and what else they do.
BAITS = {
    "MINNOW_BAIT": {"speed": 25}, "FISH_BAIT": {"speed": 45}, "LIGHT_BAIT": {"rare": "day"}, "DARK_BAIT": {"rare": "night"},
    "SPIKED_BAIT": {"sea_creature_chance": 6}, "SPOOKY_BAIT": {"speed": 25, "spooky": 0.15}, "CARROT_BAIT": {"carrot_king": 0.05},
    "BLESSED_BAIT": {"blessed": 0.5}, "ICE_BAIT": {"speed": 25, "sea_creature_chance": 4},
    "GLOWY_CHUM_BAIT": {"speed": 25, "sea_creature_chance": 3}, "HOT_BAIT": {"speed": 15, "trophy_chance": 5}, "WORM_BAIT": {"speed": 60, "hollows": True},
    "GOLDEN_BAIT": {"treasure_chance": 4}, "FROZEN_BAIT": {"speed": 50, "sea_creature_chance": 8},
    "WHALE_BAIT": {"speed": 30, "rare": "always"}, "TREASURE_BAIT": {"speed": 10, "treasure_chance": 2},
    "SHARK_BAIT": {"shark": 0.2}, "HOTSPOT_BAIT": {"hotspot": 0.2}, "CORRUPTED_BAIT": {}, "WOODEN_BAIT": {},
}


def strip(text):
    return re.sub("§.", "", text)


def number(text):
    return float(re.sub(r"[^0-9]", "", text.split()[0]) or 0)


def item_exists(item_id):
    return (ITEMS / (item_id.lower().replace(":", "-") + ".json")).exists()


def drop(entry):
    """A NEU drop: "RAW_FISH-2:2" is two Clownfish, "MAGNET;6" a Magnet VI book, "SQUID;3" an Epic Squid pet."""
    ident = entry["id"]
    count = 1
    m = re.match(r"^(.*):(\d+)$", ident)
    if m:
        ident, count = m.group(1), int(m.group(2))
    low = high = count
    for extra in entry.get("extra", []):
        m = re.search(r"x(\d+)(?:-(\d+))?", strip(extra))
        if m:
            low, high = int(m.group(1)), int(m.group(2) or m.group(1))
    chance = float(entry.get("chance", "100%").rstrip("%")) / 100
    if ident.startswith("ATTRIBUTE_SHARD"):
        return None
    if ";" in ident:
        base, _, tier = ident.partition(";")
        if (PETS / (base.lower() + ".json")).exists():
            item = f"pet:{base}:{['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC'][int(tier)]}"
        else:
            item = f"book:{base.lower()}:{tier}"
    else:
        item = re.sub(r"-(\d+)$", r":\1", ident)
        if not item_exists(item):
            return None
    return {"item": item, "chance": round(chance, 10), "min": low, "max": high}


def look(neu, render, name):
    out = {}
    if render.startswith("@neurepo:"):
        spec = json.loads(neu.read(NEU + render[len("@neurepo:"):]))
    else:
        spec = {"entity": render, "modifiers": []}
    entity = spec.get("entity", "Zombie")
    for mod in spec.get("modifiers", []):
        if mod.get("type") == "riding":
            # The creature rides its mount (Rider of the Deep: a baby zombie on a chicken).
            out["mount"] = ENTITIES[entity]
            entity = mod["entity"]
            for inner in mod.get("modifiers", []):
                apply(neu, out, inner)
        else:
            apply(neu, out, mod)
    out["entity"] = ENTITIES.get(entity, "minecraft:zombie")
    if name == "Carrot King":
        out["variant"] = "evil"
    return out


def apply(neu, out, mod):
    if mod.get("type") == "age" and str(mod.get("baby")).lower() == "true":
        out["baby"] = True
    if mod.get("type") != "equipment":
        return
    for key, slot in (("hand", "hand"), ("helmet", "head"), ("chestplate", "chest"), ("leggings", "legs"), ("feet", "feet")):
        value = mod.get(key)
        if not value:
            continue
        ident, _, color = value.partition("#")
        if ident.endswith("_SC") or not re.match(r"^[A-Z_]+$", ident):
            try:
                tag = json.loads(neu.read(NEU + "items/" + ident + ".json"))["nbttag"]
                m = re.search(r'Value:\\?"([A-Za-z0-9+/=]+)\\?"', tag)
                if m:
                    out[slot] = {"skin": m.group(1)}
            except KeyError:
                pass
            continue
        entry = {"item": "minecraft:" + ident.lower()}
        if color:
            entry["color"] = color
        out[slot] = entry


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    creatures = []
    for ident, (name, fluid, fl, weight, fxp, damage, where, condition, message) in CREATURES.items():
        data = json.loads(neu.read(NEU + "items/" + ident + "_SC.json"))
        lore = [strip(line) for line in data.get("lore", [])]
        health = HEALTH_OVERRIDE.get(ident) or next((number(line.split(":")[-1].strip()) for line in lore if "Health" in line), 100)
        rarity = next((line.strip() for line in lore if line.strip() in ("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")),
                      "COMMON")
        recipe = (data.get("recipes") or [{}])[0]
        if ident == "SQUID":
            recipe = dict(SQUID, render="Squid")
            drops = SQUID["drops"]
        else:
            drops = [d for d in (drop(e) for e in recipe.get("drops", [])) if d]
        creature = {"id": ident, "name": name, "rarity": rarity, "fluid": fluid, "fishing_level": fl, "weight": weight,
                    "level": recipe.get("level", 1), "health": health, "damage": damage, "fishing_xp": fxp,
                    "combat_xp": recipe.get("xp") or 0, "coins": recipe.get("coins") or 0, "message": message,
                    "look": LOOK_OVERRIDE.get(ident) or look(neu, recipe.get("render", "Zombie"), name), "drops": drops}
        if ident == "INKLING":
            # decision: NEU has no recipe for the Inkling: ink, as its name says.
            creature["drops"] = [{"item": "INK_SACK", "chance": 1, "min": 2, "max": 4}, {"item": "ENCHANTED_INK_SACK", "chance": 0.05, "min": 1, "max": 1}]
        if ident in STATS_OVERRIDE:
            creature["level"], creature["combat_xp"], creature["coins"] = STATS_OVERRIDE[ident]
        if where:
            creature["where"] = where
        if condition:
            creature["condition"] = condition
        creatures.append(creature)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "sea_creatures.json").write_text(json.dumps({"creatures": creatures}, indent=1) + "\n", encoding="utf-8")

    missing = [e["item"] for pool in NORMAL.values() for e in pool if not item_exists(e["item"])]
    treasure = {}
    for tier, rows in TREASURE.items():
        treasure[tier] = []
        for item, count, weight in rows:
            if not (item.startswith(("coins:", "pet:")) or item_exists(item)):
                missing.append(item)
                continue
            treasure[tier].append({"item": item, "count": count, "weight": weight})
    catches = {"normal": NORMAL, "treasure": treasure, "tiers": TIERS, "baits": BAITS}
    (OUT / "catches.json").write_text(json.dumps(catches, indent=1) + "\n", encoding="utf-8")
    print(f"{len(creatures)} sea creatures, {sum(len(t) for t in treasure.values())} treasure rows -> {OUT}")
    if missing:
        print("missing items:", missing)


if __name__ == "__main__":
    main()
