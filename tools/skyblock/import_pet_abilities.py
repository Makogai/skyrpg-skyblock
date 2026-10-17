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
    "Enchanting Wisdom": "enchanting_wisdom", "Taming Wisdom": "taming_wisdom", "Damage": "damage", "Attack Speed": "bonus_attack_speed",
    "Gemstone Fortune": "gemstone_fortune", "Block Fortune": "block_fortune", "Cold Resistance": "cold_resistance",
}
COMBAT = ["health", "defense", "strength", "intelligence", "crit_chance", "crit_damage", "ferocity", "bonus_attack_speed",
          "true_defense", "health_regen", "vitality", "mending"]
PLACES = {"Public Islands": "public", "the Crystal Hollows": "crystal_hollows", "Crystal Hollows": "crystal_hollows",
          "the Magma Fields": "magma_fields", "Mining Islands": "mining_islands", "The Park": "the_park",
          "the Dwarven Mines": "dwarven_mines", "your island": "private_island", "your Private Island": "private_island",
          "the Crimson Isle": "crimson_isle", "Jerry's Workshop": "jerrys_workshop", "The Garden": "the_garden",
          "the Glacite Mineshafts": "glacite_mineshafts", "the Glacite Tunnels": "glacite_tunnels", "the Mines of Divan": "mines_of_divan",
          "Foraging Islands": "foraging_islands"}
MOBS = {"Ender mobs": "ender", "⊙ Ender mobs": "ender", "slimes": "slimes", "Pests": "pests", "Undead mobs": "undead",
        "Wither mobs": "wither", "Skeletal mobs": "skeletal", "wolves": "wolves", "zombies": "zombies", "Ender": "ender"}
STAT_NAMES = "|".join(sorted((re.escape(k) for k in STATS), key=len, reverse=True))
N = r"\{(\d+)\}"


def stat(name):
    return STATS.get(name.strip())


def place(text):
    return PLACES.get(text.strip().rstrip("."))


def effects(text):
    """The effects in one ability's text (color codes stripped, lines joined), or []."""
    t = re.sub(r"\s+", " ", text).strip()
    out = []

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
    m = re.match(rf"(?:Grants|Gain|Gives|Passively grants) \+?{N} ?(?:☯ ?)?({STAT_NAMES})(?:\.|, which.*)?$", t)
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
    m = re.match(rf"Deal \+?{N}% (?:more )?damage (?:to|against) (.+?)\.?$", t)
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
