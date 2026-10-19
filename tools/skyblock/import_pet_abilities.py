#!/usr/bin/env python3
"""Hypixel's pet abilities as effects the game applies, read from each pet's ability text:

    python tools/skyblock/import_pet_abilities.py

Reads skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_pets/*.json (each rarity's lore, with {n} for
the numbers that grow from level 1 to 100) and writes (generated: overwritten each run)
skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/skyblock_pet_abilities/abilities.json:

    {"pets": {"BLACK_CAT": {"LEGENDARY": [{"ability": "Supernatural", "kind": "stat", "stat": "magic_find", "num": 2}, ...]}}}

Each effect's number is the pet's ability number {num} at its level, or a fixed "amount". Kinds:
- stat: +number to stat (optional "where": a place, "when": night / health_above_85).
- stat_pct: +number% of the player's own stat (or of each of "stats").
- convert: +number to stat for every "per" of stat "of" (above "above"); with "pct": number% of "of"; "cap" a maximum.
- damage_vs: deal number% more damage to "mobs" (a mob kind, or a condition like "close" / "far" / "missing_health").
- damage_from: take number% less damage from "mobs" (or "all", optionally "when": low_health).
- perk: a mechanic of its own that the game code hooks ("perk" names it, "arg" says what for; the numbers are "num",
  "num2", "amount"): wisdom_vs, coins_kill, minion_speed, tool_buff, stacks, last_stand, laserbeam... (see PetAbilities).
Abilities none of the rules know stay as text on the pet (they do nothing yet); the run prints how many matched.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
PETS = DATA / "skyblock_pets"
OUT = DATA / "skyblock_pet_abilities"

STATS = {
    "Strength": "strength", "Defense": "defense", "Health": "health", "Max Health": "health", "Intelligence": "intelligence",
    "Speed": "speed", "Crit Chance": "crit_chance", "Crit Damage": "crit_damage", "Ferocity": "ferocity", "Magic Find": "magic_find",
    "Pet Luck": "pet_luck", "Sea Creature Chance": "sea_creature_chance", "Double Hook Chance": "double_hook_chance",
    "Fishing Speed": "fishing_speed", "Treasure Chance": "treasure_chance", "Trophy Chance": "trophy_fish_chance",
    "Mining Speed": "mining_speed", "Mining Fortune": "mining_fortune", "Mining Spread": "mining_spread", "Pristine": "pristine",
    "Farming Fortune": "farming_fortune", "Foraging Fortune": "foraging_fortune", "Health Regen": "health_regen",
    "Bonus Pest Chance": "bonus_pest_chance", "Sweep": "sweep", "Combat Wisdom": "combat_wisdom", "Mining Wisdom": "mining_wisdom",
    "Farming Wisdom": "farming_wisdom", "Foraging Wisdom": "foraging_wisdom", "Fishing Wisdom": "fishing_wisdom",
    "Enchanting Wisdom": "enchanting_wisdom", "Taming Wisdom": "taming_wisdom", "Damage": "damage", "Attack Speed": "attack_speed",
    "Gemstone Fortune": "gemstone_fortune", "Block Fortune": "block_fortune", "Cold Resistance": "cold_resistance",
}
COMBAT = ["health", "defense", "strength", "intelligence", "crit_chance", "crit_damage", "ferocity", "attack_speed",
          "true_defense", "health_regen", "vitality", "mending"]
PLACES = {"Public Islands": "public", "the Crystal Hollows": "crystal_hollows", "Crystal Hollows": "crystal_hollows",
          "the Magma Fields": "magma_fields", "Mining Islands": "mining_islands", "The Park": "the_park",
          "the Dwarven Mines": "dwarven_mines", "your island": "private_island", "your Private Island": "private_island",
          "the Crimson Isle": "crimson_isle", "Jerry's Workshop": "jerrys_workshop", "The Garden": "the_garden",
          "the Glacite Mineshafts": "glacite_mineshafts", "the Glacite Tunnels": "glacite_tunnels", "the Mines of Divan": "mines_of_divan",
          "Foraging Islands": "foraging_islands"}
MOBS = {"Ender mobs": "ender", "⊙ Ender mobs": "ender", "slimes": "slimes", "Pests": "pests", "Undead mobs": "undead",
        "Wither mobs": "wither", "Skeletal mobs": "skeletal", "skeletal mobs": "skeletal", "wolves": "wolves", "zombies": "zombies", "Ender": "ender"}
STAT_NAMES = "|".join(sorted((re.escape(k) for k in STATS), key=len, reverse=True))
N = r"\{(\d+)\}"


# Hypixel's font symbols in the lore (☯ ⊙ ༕ 🦴 ❤ ❄ ᠅ ✯ and its private-use icons) mean nothing to a rule.
SYMBOLS = re.compile("[☯⊙༕🦴❤❄᠅✯-]")
WISDOM_MOBS = {"Blazes": "blaze", "Endermen": "enderman", "Zombies": "zombie", "Spiders": "spider", "Wolves": "wolf"}
# What a minion perk is for: a category ("category:farming") or minion ids ("type:slime,magma_cube").
MINION_KINDS = {"Slime and Magma Cube": "type:slime,magma_cube", "Foraging": "category:foraging", "Pig": "type:pig",
                "Farming": "category:farming", "Red Sand": "type:red_sand", "Spider, Cave Spider and Tarantula": "type:spider,cave_spider,tarantula"}
# The tool a "Buffs the ..." perk is for: "item:<SkyBlock id>", "tag:axes" or "contains:DRILL".
TOOLS = {"Aspect of the Dragons": "item:ASPECT_OF_THE_DRAGON", "Pigman Sword": "item:PIGMAN_SWORD", "Ink Wand": "item:INK_WAND",
         "Yeti Sword": "item:YETI_SWORD", "Aspect of the Jerry": "item:ASPECT_OF_THE_JERRY"}


def perk(name, arg="", **rest):
    return {"kind": "perk", "perk": name, "arg": arg, **rest}


def perks(t):
    """The mechanics of their own (see PetAbilities), or []."""
    # Wisdom towards one kind of mob, more Combat XP against one
    m = re.match(rf"Gain \+?{N} ?Combat Wisdom towards (\w+)", t)
    if m and m.group(2) in WISDOM_MOBS:
        return [perk("wisdom_vs", WISDOM_MOBS[m.group(2)], num=int(m.group(1)))]
    m = re.match(rf"Gain {N}x Combat XP against (\w+)", t)
    if m and m.group(2) in WISDOM_MOBS:
        return [perk("xp_mult_vs", WISDOM_MOBS[m.group(2)], num=int(m.group(1)))]
    # Coins
    m = re.match(rf"Gain \+?{N} coins per monster kill", t)
    if m:
        return [perk("coins_kill", num=int(m.group(1)))]
    m = re.match(rf"Gain {N} more coins per monster level on kill", t)
    if m:
        return [perk("coins_level", num=int(m.group(1)))]
    # Minions
    m = re.match(rf"(.+?) [mM]inions work {N}% faster while on your (?:Private )?[iI]sland", t)
    if m and m.group(1) in MINION_KINDS:
        return [perk("minion_speed", MINION_KINDS[m.group(1)], num=int(m.group(2)))]
    # More skill XP
    m = re.match(rf"Earn \+?{N}% more Exp when mining", t)
    if m:
        return [perk("skill_xp", "mining", num=int(m.group(1)))]
    m = re.match(rf"Gain {N}% more Skill Experience", t)
    if m:
        return [perk("skill_xp", "all", num=int(m.group(1)))]
    # Stats on one tool: "Buffs the Pigman Sword by {1} Damage and {2} Strength."
    m = re.match(rf"Buffs the (.+?)(?: sword)? by {N} Damage and {N} Strength", t)
    if m and m.group(1) in TOOLS:
        return [perk("tool_buff", TOOLS[m.group(1)], stat="damage", num=int(m.group(2))),
                perk("tool_buff", TOOLS[m.group(1)], stat="strength", num=int(m.group(3)))]
    m = re.match(rf"Buffs the Yeti Sword by {N} Damage and Intelligence", t)
    if m:
        return [perk("tool_buff", TOOLS["Yeti Sword"], stat="damage", num=int(m.group(1))),
                perk("tool_buff", TOOLS["Yeti Sword"], stat="intelligence", num=int(m.group(1)))]
    m = re.match(rf"Actually adds {N} damage to the Aspect of the Jerry", t)
    if m:
        return [perk("tool_buff", TOOLS["Aspect of the Jerry"], stat="damage", num=int(m.group(1)))]
    m = re.match(rf"Adds {N} ?Strength and {N} ?Speed to all your Axes", t)
    if m:
        return [perk("tool_buff", "tag:axes", stat="strength", num=int(m.group(1))), perk("tool_buff", "tag:axes", stat="speed", num=int(m.group(2)))]
    m = re.match(rf"Grants \+{N} ?Gemstone Fortune to Drills", t)
    if m:
        return [perk("tool_buff", "contains:DRILL", stat="gemstone_fortune", num=int(m.group(1)))]
    # Stacks from kills or bow hits
    m = re.match(rf"Mob kills grant a stack of \+{N} (Speed|Attack Speed) and \+{N} Strength for (\d+)s\. \(Max (\d+) stacks\)", t)
    if m:
        first = "speed" if m.group(2) == "Speed" else "attack_speed"
        return [perk("stacks", "kill", stat=first, num=int(m.group(1)), seconds=int(m.group(4)), cap=int(m.group(5))),
                perk("stacks", "kill", stat="strength", num=int(m.group(3)), seconds=int(m.group(4)), cap=int(m.group(5)))]
    m = re.match(rf"Gain a combo stack for every bow hit granting \+(\d+) Strength\. Max {N} stacks, stacks disappear after (\d+) seconds", t)
    if m:
        return [perk("stacks", "bow_hit", stat="strength", amount=int(m.group(1)), cap_num=int(m.group(2)), seconds=int(m.group(3)))]
    # Defence and hits
    m = re.match(rf"If you have any absorption active, gain \+{N}% Damage Reduction", t)
    if m:
        return [perk("absorption_reduction", num=int(m.group(1)))]
    if t.startswith("While at less than 20% HP, reduce incoming damage by 20%"):
        return [perk("last_stand")]
    m = re.match(rf"Your iron plating causes {N}% of attacks to ricochet", t)
    if m:
        return [perk("ricochet", num=int(m.group(1)))]
    if t.startswith("Every 5 hits, throw the enemy up into the air and deal 5x damage"):
        return [perk("toss")]
    m = re.match(rf"Every 5th hit deals {N}% of your final damage to enemies within 5 blocks", t)
    if m:
        return [perk("tail_slam", num=int(m.group(1)))]
    m = re.match(rf"Reduces fall damage by {N}%", t)
    if m:
        return [perk("fall_reduction", num=int(m.group(1)))]
    m = re.match(r"Grants permanent Jump Boost (II|IV)", t)
    if m:
        return [perk("jump_boost", amount=2 if m.group(1) == "II" else 4)]
    m = re.match(rf"After falling 20 or more blocks, absorb your fall damage and deal {N}% of your weapon's Damage for every block", t)
    if m:
        return [perk("trample", num=int(m.group(1)))]
    if t.startswith("Gain immunity to knockback"):
        return [perk("knockback_immune")]
    m = re.match(rf"Enemies within {N} blocks are slowed by 25% and deal {N}% less damage", t)
    if m:
        return [perk("blizzard", num=int(m.group(1)), num2=int(m.group(2)))]
    m = re.match(rf"Upon hitting a monster, it becomes slowed by {N}%", t)
    if m:
        return [perk("hit_slow", num=int(m.group(1)))]
    m = re.match(rf"Upon hitting an enemy inflict the wither effect for {N}% damage over 3 seconds", t)
    if m:
        return [perk("hit_wither", num=int(m.group(1)))]
    # Mana
    m = re.match(rf"Reduces the mana cost of abilities by {N}%", t)
    if m:
        return [perk("mana_cost", num=int(m.group(1)))]
    m = re.match(rf"Gives a {N} chance to not consume Mana when using an ability", t)
    if m:
        return [perk("mana_free", num=int(m.group(1)))]
    m = re.match(rf"Zaps your enemies for {N}x your Intelligence every 3s", t)
    if m:
        return [perk("laserbeam", num=int(m.group(1)))]
    # Drops
    m = re.match(rf"You have a {N}% chance to drop a Mushroom when farming crops", t)
    if m:
        return [perk("crop_drop", "minecraft:red_mushroom", num=int(m.group(1)))]
    m = re.match(rf"You have a {N}% chance to find Tasty Cheese or Dung when farming crops", t)
    if m:
        return [perk("crop_drop", "DUNG", num=int(m.group(1)))]
    m = re.match(rf"You have a {N}% chance to find Jelly or Plant Matter when farming crops", t)
    if m:
        return [perk("crop_drop", "JELLY,PLANT_MATTER", num=int(m.group(1)))]
    m = re.match(rf"You have a {N}% chance to find a Honey Jar when farming crops", t)
    if m:
        return [perk("crop_drop", "HONEY_JAR", num=int(m.group(1)))]
    m = re.match(rf"Gain more exp orbs for breaking end stone and gain a \+{N}% chance to get an extra block dropped", t)
    if m:
        return [perk("block_extra", "minecraft:end_stone", num=int(m.group(1)))]
    m = re.match(rf"Chickens always drop an Egg when killed\. Grants a {N}% chance for animals to drop an additional item", t)
    if m:
        return [perk("egg_drop"), perk("animal_extra", num=int(m.group(1)))]
    m = re.match(rf"Gain a {N}% chance to get double drops from squids", t)
    if m:
        return [perk("mob_double", "squid", num=int(m.group(1)))]
    m = re.match(rf"Grants \+{N} ?Magic Find on Mythological mobs", t)
    if m:
        return [perk("mf_vs", "mythological", num=int(m.group(1)))]
    # Stats near water or lava, per nearby wolf
    m = re.match(rf"Gives {N} Strength and Defense when near (water or lava|water)", t)
    if m:
        where = "near_fluid" if "lava" in m.group(2) else "near_water"
        return [{"kind": "stat", "stat": "strength", "num": int(m.group(1)), "where": where},
                {"kind": "stat", "stat": "defense", "num": int(m.group(1)), "where": where}]
    m = re.match(rf"Gain {N} Crit Damage for every nearby wolf monster Max (\d+) wolves", t)
    if m:
        return [{"kind": "stat", "stat": "crit_damage", "num": int(m.group(1)), "per_mob": "wolf", "per_player": 10, "max_players": int(m.group(2))}]
    # Powder
    m = re.match(rf"Grants \+{N}% Mithril Powder from all sources", t)
    if m:
        return [perk("powder", "mithril", num=int(m.group(1)))]
    m = re.match(rf"Earn \+{N}% Gemstone Powder from all sources", t)
    if m:
        return [perk("powder", "gemstone", num=int(m.group(1)))]
    m = re.match(rf"Gain {N}% more Glacite Powder from most sources", t)
    if m:
        return [perk("powder", "glacite", num=int(m.group(1)))]
    m = re.match(rf"Your skeleton shoots an arrow dealing {N}x your Crit Damage when a mob gets close to you", t)
    if m:
        return [perk("skeletal_defense", num=int(m.group(1)))]
    # The Phoenix
    if t.startswith("You may always fly on your private island and garden"):
        return [perk("fly_island")]
    if t.startswith("Don't lose coins from death"):
        return [perk("keep_coins")]
    m = re.match(rf"Before death, become immune and gain {N} Strength for {N} seconds", t)
    if m:
        return [perk("rekindle", num=int(m.group(1)), num2=int(m.group(2)))]
    m = re.match(rf"On 4th melee strike, ignite mobs, dealing {N}x your Crit Damage each second for {N} seconds", t)
    if m:
        return [perk("fourth_flare", num=int(m.group(1)), num2=int(m.group(2)))]
    return []


def stat(name):
    return STATS.get(name.strip())


def place(text):
    return PLACES.get(text.strip().rstrip("."))


def effects(text):
    """The effects in one ability's text (color codes stripped, lines joined), or []."""
    t = re.sub(r"\s+", " ", SYMBOLS.sub("", text)).strip()
    out = perks(t)
    if out:
        return out

    # "For each player within 25 blocks: +{0}Intelligence +{1}Strength +{2}Defense Max 15 players"
    m = re.match(r"For each player within (\d+) blocks: (.+?) Max (\d+) players", t)
    if m:
        for num, name in re.findall(rf"\+{N} ?({STAT_NAMES})", m.group(2)):
            out.append({"kind": "stat", "stat": STATS[name], "num": int(num), "per_player": int(m.group(1)), "max_players": int(m.group(3))})
        return out
    # "Grants +{0}Fishing Speed for each player within 30 blocks, up to 5 players."
    m = re.match(rf"Grants \+{N} ?({STAT_NAMES}) for each player within (\d+) blocks, up to (\d+) players", t)
    if m:
        return [{"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1)), "per_player": int(m.group(3)),
                 "max_players": int(m.group(4))}]
    # "Grants +{0}Farming Fortune per Farming level." / "... to your pet for each Heart of the Mountain level."
    m = re.match(rf"Grants \+{N} ?({STAT_NAMES}) (?:to your pet )?(?:per|for each) (Farming|Mining|Fishing|Combat|Foraging|Heart of the Mountain) level", t)
    if m:
        skill = "hotm" if m.group(3) == "Heart of the Mountain" else m.group(3).lower()
        e = {"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1)), "per_level": [skill]}
        if " while in " in t:
            e["where"] = place(t.split(" while in ", 1)[1]) or "unknown"
        return [e]
    # "Each Mining and Fishing level grants +{2}Fishing Speed, +{3}Speed and +{4}Defense."
    m = re.match(r"Each Mining and Fishing level grants (.+)$", t)
    if m:
        return [{"kind": "stat", "stat": STATS[name], "num": int(num), "per_level": ["mining", "fishing"]}
                for num, name in re.findall(rf"\+{N} ?({STAT_NAMES})", m.group(1))]
    # "Grants +11.1Strength and +2.2 Magic Find to your pet for each digit in your Gold Collection. (Max 100M collection)"
    m = re.match(rf"Grants \+([\d.]+) ?({STAT_NAMES}) and \+([\d.]+) ?({STAT_NAMES}) to your pet for each digit in your (\w+) Collection", t)
    if m:
        collection = {"Gold": "gold_ingot"}.get(m.group(5), m.group(5).lower())
        return [{"kind": "stat", "stat": stat(m.group(2)), "amount": float(m.group(1)), "per_digit": collection},
                {"kind": "stat", "stat": stat(m.group(4)), "amount": float(m.group(3)), "per_digit": collection}]
    # "Gain {3}% damage for every million coins in your bank. (Max {4}%)"
    m = re.match(rf"Gain {N}% damage for every million coins in your bank\. \(Max {N}%\)", t)
    if m:
        return [{"kind": "damage_vs", "mobs": "bank", "num": int(m.group(1)), "cap_num": int(m.group(2))}]
    # "Grants +{3} of each to your pet: Farming Fortune Foraging Fortune Mining Fortune"
    m = re.match(rf"Grants \+{N} of each to your pet: (.+)$", t)
    if m:
        for name in re.findall(STAT_NAMES, m.group(2)):
            out.append({"kind": "stat", "stat": STATS[name], "num": int(m.group(1))})
        return out
    # "Grants +{0}Farming Fortune while on Public Islands." / "Grants +{3}% Magic Find while on Mining Islands."
    m = re.match(rf"(?:Grants|Gain|Gives) \+?{N}%? ?(?:☯ ?)?({STAT_NAMES}) while (?:in|on) (.+?)\.?$", t)
    if m and stat(m.group(2)):
        where = place(m.group(3))
        return [{"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1)), "where": where or "unknown"}]
    # "Grants +{2}Magic Find." / "Grants +{3}☯ Fishing Wisdom." / "Passively grants +{1}☯ Taming Wisdom." / "Gain +{0}☯ ..."
    m = re.match(rf"(?:Grants|Gain|Gives|Passively grants) \+?{N} ?(?:☯ ?)?({STAT_NAMES})(?:\.| while mining Blocks\.?|, which.*)?$", t)
    if m:
        return [{"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1))}]
    # "Increases your Speed and speed cap by +{0}."
    m = re.match(rf"Increases your Speed and speed cap by \+{N}\.?$", t)
    if m:
        return [{"kind": "stat", "stat": "speed", "num": int(m.group(1))}]
    # "Grants +{2}Mining Speed and permanent Haste {3}."
    m = re.match(rf"Grants \+{N}({STAT_NAMES}) and permanent Haste", t)
    if m:
        return [{"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1))}]
    # "Adds +{0} Damage and +{1} Strength to your weapons."
    m = re.match(rf"Adds \+{N} ({STAT_NAMES}) and \+{N} ({STAT_NAMES}) to your weapons", t)
    if m:
        return [{"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1))},
                {"kind": "stat", "stat": stat(m.group(4)), "num": int(m.group(3))}]
    # "During night, gain {1} Intelligence, {2} Speed, and Night Vision."
    m = re.match(rf"During night, gain {N} ({STAT_NAMES}), {N} ({STAT_NAMES})", t)
    if m:
        return [{"kind": "stat", "stat": stat(m.group(2)), "num": int(m.group(1)), "when": "night"},
                {"kind": "stat", "stat": stat(m.group(4)), "num": int(m.group(3)), "when": "night"}]
    # "Gain +{2}% Max Health." / "Gain +{0}% Ferocity." / "Gain +{0}% Defense and an additional ..."
    m = re.match(rf"Gain \+{N}% ({STAT_NAMES})(?:\.| and)", t)
    if m:
        return [{"kind": "stat_pct", "stat": stat(m.group(2)), "num": int(m.group(1))}]
    # "Gain +{0}% Strength when above 85% Health."
    m = re.match(rf"Gain \+{N}% ({STAT_NAMES}) when above 85% Health", t)
    if m:
        return [{"kind": "stat_pct", "stat": stat(m.group(2)), "num": int(m.group(1)), "when": "health_above_85"}]
    # "Increases all Combat stats and Magic Find by {3}%." / "Increases all Combat Stats by +{1}% on Mining Islands."
    m = re.match(rf"Increases all Combat [Ss]tats( and Magic Find)? by \+?{N}%(?: (?:on|while on|in) (.+?))?\.?$", t)
    if m:
        stats = COMBAT + (["magic_find"] if m.group(1) else [])
        e = {"kind": "stat_pct", "stats": stats, "num": int(m.group(2))}
        if m.group(3):
            e["where"] = place(m.group(3)) or "unknown"
        return [e]
    # "Gain {1}Defense per 20 Max Health." / "Gain {0}Defense for every 100 Speed." / "Gain {1}Health for every 10 Defense."
    m = re.match(rf"Gain \+?{N} ?({STAT_NAMES}) (?:per|for every) (\d+) ({STAT_NAMES})(?:,|\.|$)", t)
    if m:
        return [{"kind": "convert", "stat": stat(m.group(2)), "num": int(m.group(1)), "per": int(m.group(3)), "of": stat(m.group(4))}]
    # "Gain {0}% of your Defense as Strength. (Max +500)"
    m = re.match(rf"Gain {N}% of your ({STAT_NAMES}) as ({STAT_NAMES})\.(?: \(Max \+(\d+)\))?", t)
    if m:
        e = {"kind": "convert", "stat": stat(m.group(3)), "num": int(m.group(1)), "pct": True, "of": stat(m.group(2))}
        if m.group(4):
            e["cap"] = int(m.group(4))
        return [e]
    # "Grants +{1}%Strength per 5 Magic Find. (Max +{2}%)"
    m = re.match(rf"Grants \+{N}% ?({STAT_NAMES}) per (\d+) ({STAT_NAMES})\. \(Max \+{N}%\)", t)
    if m:
        return [{"kind": "stat_pct", "stat": stat(m.group(2)), "num": int(m.group(1)), "per": int(m.group(3)), "of": stat(m.group(4)),
                 "cap_num": int(m.group(5))}]
    # "Grants +0.7Farming Fortune for every {1} Strength you have."
    m = re.match(rf"Grants \+([\d.]+) ?({STAT_NAMES}) for every {N} ({STAT_NAMES})", t)
    if m:
        return [{"kind": "convert", "stat": stat(m.group(2)), "amount": float(m.group(1)), "per_num": int(m.group(3)), "of": stat(m.group(4))}]
    # "Grants 1Foraging Fortune per 5 Sweep."
    m = re.match(rf"Grants ([\d.]+) ?({STAT_NAMES}) per (\d+) ({STAT_NAMES})\.?$", t)
    if m:
        return [{"kind": "convert", "stat": stat(m.group(2)), "amount": float(m.group(1)), "per": int(m.group(3)), "of": stat(m.group(4))}]
    # "Convert every 3 Speed you have above 100 into +{1}Farming Fortune." / "Convert every {1}Speed ... into +1Block Fortune."
    m = re.match(rf"Convert every (\d+|{N}) ?Speed you have above 100 into \+({N}|[\d.]+) ?({STAT_NAMES})", t)
    if m:
        e = {"kind": "convert", "stat": stat(m.group(5)), "of": "speed", "above": 100}
        if m.group(2):
            e["per_num"] = int(m.group(2))
        else:
            e["per"] = int(m.group(1))
        if m.group(4):
            e["num"] = int(m.group(4))
        else:
            e["amount"] = float(m.group(3))
        return [e]
    # "Gain +{1}Magic Find for every 100 Mining Fortune, doubled in ..."
    m = re.match(rf"Gain \+{N} ?({STAT_NAMES}) for every (\d+) ({STAT_NAMES})", t)
    if m:
        return [{"kind": "convert", "stat": stat(m.group(2)), "num": int(m.group(1)), "per": int(m.group(3)), "of": stat(m.group(4))}]
    # "Deal {0}% more damage to ⊙ Ender mobs." / "Deal {1}% more damage to slimes" / "Deal {1}% more damage to Undead mobs"
    m = re.match(rf"Deal \+?{N}% (?:more )?[dD]amage (?:to|against) (.+?)\.?$", t)
    if m:
        target = m.group(2).strip()
        mobs = MOBS.get(target)
        if mobs is None:
            if target.startswith("mobs that have attacked you"):
                mobs = "attacked_you"
            elif target.startswith("targets with no other mobs within 15 blocks"):
                mobs = "alone"
            elif target.startswith("enemies within 1.5 blocks"):
                mobs = "close"
        if mobs:
            return [{"kind": "damage_vs", "mobs": mobs, "num": int(m.group(1))}]
    # "Deal up to +{0}% Damage based on the enemy's missing health"
    m = re.match(rf"Deal up to \+{N}% Damage based on the enemy's missing health", t)
    if m:
        return [{"kind": "damage_vs", "mobs": "missing_health", "num": int(m.group(1))}]
    # "Increases your melee damage by {2}% if you are more than 3 blocks away from the target"
    m = re.match(rf"Increases your melee damage by {N}% if you are more than 3 blocks away", t)
    if m:
        return [{"kind": "damage_vs", "mobs": "far", "num": int(m.group(1))}]
    # "Increase arrow damage by {0}% which is doubled while in dungeons"
    m = re.match(rf"Increase arrow damage by {N}%", t)
    if m:
        return [{"kind": "damage_vs", "mobs": "arrows", "num": int(m.group(1))}]
    # "Deal +50% damage to monsters Level 50+ and +75% damage to monsters Level 100+."
    if t.startswith("Deal +50% damage to monsters Level 50+"):
        return [{"kind": "damage_vs", "mobs": "level_50", "amount": 50}, {"kind": "damage_vs", "mobs": "level_100", "amount": 75}]
    # "Take {0}% less damage from Ender mobs" / "Reduce the damage taken from zombies by {0}%."
    m = re.match(rf"Take {N}% less damage from (.+?)\.?$", t) or re.match(rf"Reduce the damage taken from (.+?) by {N}%", t)
    if m:
        num, target = (m.group(1), m.group(2)) if t.startswith("Take") else (m.group(2), m.group(1))
        mobs = MOBS.get(target.strip())
        if mobs:
            return [{"kind": "damage_from", "mobs": mobs, "num": int(num)}]
    # "Your freezing aura slows enemy attacks causing you to take {2}% reduced damage"
    m = re.search(rf"causing you to take {N}% reduced damage", t)
    if m:
        return [{"kind": "damage_from", "mobs": "all", "num": int(m.group(1))}]
    # "When under 40% maximum HP, you take {2}% less damage."
    m = re.search(rf"When under 40% maximum HP, you take {N}% less damage", t)
    if m:
        return [{"kind": "damage_from", "mobs": "all", "num": int(m.group(1)), "when": "low_health"}]
    return out


def strip(line):
    return re.sub("§.", "", line)


def abilities(lore):
    """(name, text) per ability: a gold title line after a blank line, then its lines."""
    out = []
    current = None
    start = lore.index("") if "" in lore else len(lore)
    for line in lore[start:]:
        if line == "":
            current = None
            continue
        if line.startswith("§6") and "{" not in line and current is None:
            current = [strip(line).strip(), []]
            out.append(current)
        elif current is not None:
            current[1].append(strip(line).strip())
    return [(name, " ".join(lines)) for name, lines in out]


def main():
    pets = {}
    matched = total = 0
    unmatched = set()
    for path in sorted(PETS.glob("*.json")):
        pet = json.loads(path.read_text(encoding="utf-8"))
        key = path.stem.upper()
        tiers = {}
        for tier, data in pet["rarities"].items():
            effects_here = []
            for name, text in abilities(data["lore"]):
                found = effects(text)
                total += 1
                if found:
                    matched += 1
                    for e in found:
                        e["ability"] = name
                    effects_here += found
                else:
                    unmatched.add(f"{pet['name']}: {name}")
            if effects_here:
                tiers[tier] = effects_here
        if tiers:
            pets[key] = tiers
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "abilities.json").write_text(json.dumps({"pets": pets}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{matched}/{total} ability tiers work, on {len(pets)} pets -> {OUT / 'abilities.json'}")
    print(f"{len(unmatched)} abilities stay text only")


if __name__ == "__main__":
    main()
