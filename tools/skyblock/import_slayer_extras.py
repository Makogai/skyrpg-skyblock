#!/usr/bin/env python3
"""The slayer drops Hypixel's older tables leave out: runes, enchanted books and dyes.

    python tools/skyblock/import_slayer_extras.py

Makes the item files for the runes and dyes the five bosses drop (from NEU's item files, tools/skyblock/cache/neu-repo.zip:
name, rarity, the head's skin, the description), then adds them to each line's drops in
combat/src/main/resources/data/skyrpg_combat/skyrpg/slayers/<line>.json. Run it after import_slayers.py (which rewrites those
files). The chances come from Hypixel's wiki (the bosses' drop tables, 2026): where it shows weights, the chance is the weight
over the pool (token + main + extra for an extra drop, token + main for a main one); dyes roll on their own. Books are
SkyBlock's "ENCHANTMENT_<NAME>_<LEVEL>" ids. Runes are the first level only (Duplex I isn't here: we have no such enchant).
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
SLAYERS = ROOT / "combat/src/main/resources/data/skyrpg_combat/skyrpg/slayers"
NEU = "NotEnoughUpdates-REPO-master/items/"
RARITIES = ("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

# NEU id -> our SkyBlock id (the ";" of a level becomes "_").
RUNES = {"ZOMBIE_SLAYER_RUNE;1": "PESTILENCE_RUNE_1", "SNAKE_RUNE;1": "SNAKE_RUNE_1", "BITE_RUNE;1": "BITE_RUNE_1",
         "DARKNESS_WITHIN_RUNE;1": "DARKNESS_WITHIN_RUNE_1", "ENDERSNAKE_RUNE;1": "ENDERSNAKE_RUNE_1", "DRAGON_RUNE;1": "END_RUNE_1",
         "ENCHANT_RUNE;1": "ENCHANT_RUNE_1", "SPIRIT_RUNE;1": "SPIRIT_RUNE_1", "COUTURE_RUNE;1": "COUTURE_RUNE_1",
         "LAVATEARS_RUNE;1": "LAVATEARS_RUNE_1", "FIERY_BURST_RUNE;1": "FIERY_BURST_RUNE_1", "SOULTWIST_RUNE;1": "SOULTWIST_RUNE_1"}
DYES = {"DYE_MATCHA": "DYE_MATCHA", "DYE_BRICK_RED": "DYE_BRICK_RED", "DYE_BYZANTIUM": "DYE_BYZANTIUM", "DYE_CELESTE": "DYE_CELESTE",
        "DYE_FLAME": "DYE_FLAME", "DYE_SANGRIA": "DYE_SANGRIA"}

# Every tier's dyes: 1 in 10M, 2.5M, 1M, 500k (and 250k at tier V), rolled on their own.
DYE_ODDS = [1e-7, 4e-7, 1e-6, 2e-6, 4e-6]

# line -> [(item, slayer level, {tier: chance})]
DROPS = {
    "revenant_horror": [
        ("PESTILENCE_RUNE_1", 2, {2: 0.0067, 3: 0.0259, 4: 0.0627, 5: 0.0536}),
        ("ENCHANTMENT_SMITE_6", 4, {3: 0.004, 4: 0.004, 5: 0.0034}),
        ("SNAKE_RUNE_1", 6, {4: 0.0015, 5: 0.0013}),
        ("DYE_MATCHA", 0, dict(zip(range(1, 6), DYE_ODDS))),
    ],
    "tarantula_broodfather": [
        ("BITE_RUNE_1", 0, {2: 0.0069, 3: 0.0267, 4: 0.0645, 5: 0.0552}),
        ("ENCHANTMENT_BANE_OF_ARTHROPODS_6", 4, {3: 0.0021, 4: 0.0042, 5: 0.0035}),
        ("DARKNESS_WITHIN_RUNE_1", 0, {4: 0.0039, 5: 0.0066}),
        ("DYE_BRICK_RED", 0, dict(zip(range(1, 6), DYE_ODDS))),
    ],
    "sven_packmaster": [
        ("SPIRIT_RUNE_1", 2, {2: 0.0068, 3: 0.0266, 4: 0.0637}),
        ("ENCHANTMENT_CRITICAL_6", 4, {3: 0.0041, 4: 0.0081}),
        ("COUTURE_RUNE_1", 6, {4: 0.0024}),
        ("DYE_CELESTE", 0, dict(zip(range(1, 5), DYE_ODDS))),
    ],
    "voidgloom_seraph": [
        ("ENCHANTMENT_MANA_STEAL_1", 2, {3: 0.0447, 4: 0.0423}),
        ("ENDERSNAKE_RUNE_1", 0, {3: 0.0242, 4: 0.053}),
        ("ENCHANTMENT_SMARTY_PANTS_1", 5, {4: 0.0176}),
        ("END_RUNE_1", 5, {4: 0.0066}),
        ("ENCHANT_RUNE_1", 7, {4: 0.00046}),
        ("DYE_BYZANTIUM", 0, dict(zip(range(1, 5), DYE_ODDS))),
    ],
    "inferno_demonlord": [
        ("LAVATEARS_RUNE_1", 1, {3: 0.0115, 4: 0.0231}),
        ("ENCHANTMENT_FIRE_ASPECT_3", 5, {4: 0.0132}),
        ("FIERY_BURST_RUNE_1", 5, {4: 0.0021}),
        ("DYE_FLAME", 0, dict(zip(range(1, 5), DYE_ODDS))),
    ],
}
# The Rift's Vampire slayer: Soultwist Rune I (extra, 11-12%) and Sangria Dye (see rift/VampireSlayer.java).
EXTRA_ITEMS = ["SOULTWIST_RUNE;1"]


def strip(text):
    return re.sub("§.", "", text)


def make_items():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    for neu_id, ident in {**RUNES, **DYES}.items():
        data = json.loads(neu.read(NEU + neu_id + ".json"))
        lore = [strip(line) for line in data["lore"]]
        rarity = next((line.split()[0] for line in reversed(lore) if line.split() and line.split()[0] in RARITIES), "RARE")
        text = []
        for line in lore:
            if not line or line.startswith("*") or line.split()[0] in RARITIES:
                if text and not line:
                    break
                continue
            text.append(line)
        name = strip(data["displayname"]).replace("◆", "").strip()
        item = {"skyblock_id": ident, "base": "minecraft:player_head", "name": name, "rarity": rarity.lower(), "category": "COSMETIC",
                "type": "Cosmetic", "description": "§7" + " ".join(text)}
        value = re.search(r'Value:\\?"([A-Za-z0-9+/=]+)\\?"', data["nbttag"])
        if value:
            item["skin"] = {"value": value.group(1)}
        item["hypixel"] = {"npc_sell_price": 1}
        (ITEMS / (ident.lower() + ".json")).write_text(json.dumps(item, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        print("item", ident, name, rarity.lower(), "skin" if value else "no skin")


def add_drops():
    for line, extras in DROPS.items():
        path = SLAYERS / (line + ".json")
        data = json.loads(path.read_text(encoding="utf-8"))
        ids = {e[0] for e in extras}
        data["drops"] = [d for d in data["drops"] if d["item"] not in ids]
        for item, level, tiers in extras:
            data["drops"].append({"item": item, "level": level, "odds": "",
                                  "tiers": {str(t): [1, 1, c] for t, c in tiers.items()}})
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        print(line, "+", len(extras), "drops")


if __name__ == "__main__":
    make_items()
    add_drops()
