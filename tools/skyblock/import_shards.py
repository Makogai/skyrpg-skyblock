"""Imports Hypixel SkyBlock's Hunting shards (the 2025 Galatea update) from the NEU repo cache.

    python tools/skyblock/import_shards.py

Reads tools/skyblock/cache/neu-repo.zip: constants/attribute_shards.json (325 shards: name, attribute, rarity,
alignment, family, id) and each shard item's lore for its level-I effect. Writes
skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/hunting/shards.json:

    {"levelling": {"COMMON": [1, 3, ...], ...}, "unconsumable": [...],
     "shards": [{"id": "C1", "key": "GROVE", "name": "Grove", "attribute": "Nature Elemental", "rarity": "COMMON",
                 "alignment": "Forest", "family": ["Elemental"], "effect": "Grants +2 Health.",
                 "stat": "health", "per_level": 2.0}, ...]}

"stat"/"per_level" are filled when the effect is a plain "Grants +N <Stat>." the mod knows (the stat's id), so those
attributes work as stats; the rest are listed with their effect text.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ZIP = ROOT / "tools/skyblock/cache/neu-repo.zip"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/hunting/shards.json"
NEU = "NotEnoughUpdates-REPO-master/"
# Stat words in the lore -> our stat ids (core StatTypes).
STATS = {"Health": "health", "Defense": "defense", "Strength": "strength", "Intelligence": "intelligence", "Speed": "speed",
         "Crit Chance": "crit_chance", "Crit Damage": "crit_damage", "Ferocity": "ferocity", "Magic Find": "magic_find",
         "Pet Luck": "pet_luck", "True Defense": "true_defense", "Sweep": "sweep", "Hunting Fortune": "hunting_fortune",
         "Charm Chance": "charm_chance", "Hunting Wisdom": "hunting_wisdom", "Foraging Fortune": "foraging_fortune",
         "Mining Fortune": "mining_fortune", "Farming Fortune": "farming_fortune", "Fishing Speed": "fishing_speed",
         "Sea Creature Chance": "sea_creature_chance", "Vitality": "vitality", "Mending": "mending",
         "Attack Speed": "bonus_attack_speed", "Mining Speed": "mining_speed", "Fig Fortune": "fig_fortune",
         "Mangrove Fortune": "mangrove_fortune", "Foraging Wisdom": "foraging_wisdom", "Combat Wisdom": "combat_wisdom",
         "Mining Wisdom": "mining_wisdom", "Farming Wisdom": "farming_wisdom", "Fishing Wisdom": "fishing_wisdom",
         "Respiration": "respiration", "Pressure Resistance": "pressure_resistance", "Heat Resistance": "heat_resistance",
         "Cold Resistance": "cold_resistance", "Ability Damage": "ability_damage", "Health Regen": "health_regen"}


def plain(text):
    text = re.sub(r"§.", "", text)
    return re.sub(r"[-]\s?", "", text).strip()


def main():
    z = zipfile.ZipFile(ZIP)
    data = json.loads(z.read(NEU + "constants/attribute_shards.json"))
    shards = []
    for a in data["attributes"]:
        effect = ""
        try:
            item = json.loads(z.read(NEU + "items/" + a["internalName"] + ".json"))
            lines = [plain(l) for l in item.get("lore", [])]
            body = []
            for line in lines[1:]:
                if not line:
                    break
                body.append(line)
            effect = " ".join(body)
        except KeyError:
            pass
        entry = {"id": a["shardId"], "key": a["bazaarName"].removeprefix("SHARD_"), "name": a["displayName"],
                 "attribute": a["abilityName"], "rarity": a["rarity"], "alignment": a["alignment"], "family": a.get("family", []),
                 "item": a["internalName"], "effect": effect}
        m = re.fullmatch(r"Grants \+([\d.]+)%? ([A-Za-z ]+?)\.", effect)
        if m and m.group(2) in STATS:
            entry["stat"] = STATS[m.group(2)]
            entry["per_level"] = float(m.group(1))
        shards.append(entry)
    order = {"C": 0, "U": 1, "R": 2, "E": 3, "L": 4}
    shards.sort(key=lambda s: (order[s["id"][0]], int(s["id"][1:])))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"levelling": data["attribute_levelling"], "unconsumable": data.get("unconsumable_attributes", []),
                               "shards": shards}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    stats = sum(1 for s in shards if "stat" in s)
    print(f"Wrote {len(shards)} shards ({stats} plain stat attributes) to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
