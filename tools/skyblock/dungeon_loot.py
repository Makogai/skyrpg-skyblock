#!/usr/bin/env python3
"""The Catacombs' reward chest loot, by floor (Hypixel's drops from the RNG meter pools, our chances):

    python tools/skyblock/dungeon_loot.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/dungeon_loot/loot.json (generated):
    {"floors": {"1": [{"item": "BONZO_FRAGMENT", "chances": [Wood, Gold, Diamond, Emerald, Obsidian, Bedrock], "min": 1, "max": 2}, ...]}}

Each entry's chance is per chest tier by its rarity band; a better rank multiplies them (Rewards.java).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/dungeon_loot/loot.json"
BANDS = {
    "common": [0.3, 0.5, 0.7, 0.8, 0.9, 1.0],
    "uncommon": [0.08, 0.15, 0.2, 0.25, 0.3, 0.35],
    "rare": [0, 0.05, 0.08, 0.1, 0.12, 0.15],
    "epic": [0, 0.015, 0.03, 0.04, 0.05, 0.06],
    "legendary": [0, 0.005, 0.01, 0.015, 0.02, 0.03],
    "mythic": [0, 0, 0.003, 0.006, 0.01, 0.015],
}
EVERY = [("HOT_POTATO_BOOK", "uncommon"), ("RECOMBOBULATOR_3000", "epic"), ("FUMING_POTATO_BOOK", "epic"),
         ("KISMET_FEATHER", "legendary"), ("DUNGEON_CHEST_KEY", "legendary")]
FLOORS = {
    0: [],
    1: [("BONZO_FRAGMENT", "common", 1, 2), ("RED_NOSE", "rare"), ("BALLOON_SNAKE", "rare"), ("BONZO_MASK", "epic"),
        ("book:ultimate_bank:1", "epic"), ("BONZO_STAFF", "epic")],
    2: [("SCARF_FRAGMENT", "common", 1, 2), ("SCARF_STUDIES", "rare"), ("ADAPTIVE_BELT", "rare"), ("STONE_BLADE", "epic"),
        ("RED_SCARF", "epic"), ("book:ultimate_bank:1", "epic"), ("SCARF_THESIS", "legendary")],
    3: [("ADAPTIVE_HELMET", "rare"), ("ADAPTIVE_BOOTS", "rare"), ("SUSPICIOUS_VIAL", "rare"), ("ADAPTIVE_CHESTPLATE", "epic"),
        ("ADAPTIVE_LEGGINGS", "epic"), ("SCARF_GRIMOIRE", "legendary")],
    4: [("THORN_FRAGMENT", "common", 1, 2), ("SPIRIT_WING", "rare"), ("SPIRIT_BONE", "rare"), ("SPIRIT_DECOY", "epic"),
        ("ITEM_SPIRIT_BOW", "epic"), ("SPIRIT_SWORD", "epic"), ("THORNS_BOOTS", "epic"), ("book:ultimate_rend:1", "epic")],
    5: [("LIVID_FRAGMENT", "common", 1, 2), ("DARK_ORB", "rare"), ("SHADOW_ASSASSIN_HELMET", "epic"), ("SHADOW_ASSASSIN_CHESTPLATE", "epic"),
        ("book:lethality:6", "epic"), ("LIVID_DAGGER", "legendary"), ("AOTE_STONE", "legendary"), ("SHADOW_FURY", "mythic"),
        ("LAST_BREATH", "mythic")],
    6: [("GIANT_FRAGMENT_DIAMOND", "common"), ("GIANT_FRAGMENT_LASER", "common"), ("SADAN_BROOCH", "rare"), ("FEL_SKULL", "rare"),
        ("SOULWEAVER_GLOVES", "epic"), ("NECROMANCER_SWORD", "epic"), ("SUMMONING_RING", "legendary"),
        ("NECROMANCER_LORD_CHESTPLATE", "legendary"), ("GIANTS_SWORD", "mythic"), ("PRECURSOR_EYE", "mythic")],
    7: [("WITHER_BLOOD", "rare"), ("WITHER_CLOAK", "rare"), ("WITHER_CATALYST", "rare"), ("PRECURSOR_GEAR", "rare"),
        ("WITHER_HELMET", "epic"), ("WITHER_BOOTS", "epic"), ("WITHER_LEGGINGS", "epic"), ("WITHER_CHESTPLATE", "legendary"),
        ("AUTO_RECOMBOBULATOR", "legendary"), ("book:ultimate_one_for_all:1", "legendary"), ("IMPLOSION_SCROLL", "mythic"),
        ("SHADOW_WARP_SCROLL", "mythic"), ("WITHER_SHIELD_SCROLL", "mythic"), ("NECRON_HANDLE", "mythic"),
        ("STORM_THE_FISH", "mythic"), ("MAXOR_THE_FISH", "mythic"), ("GOLDOR_THE_FISH", "mythic")],
}


def entry(spec):
    item, band = spec[0], spec[1]
    lo, hi = (spec[2], spec[3]) if len(spec) > 2 else (1, 1)
    return {"item": item, "chances": BANDS[band], "min": lo, "max": hi}


def main():
    floors = {}
    for floor, specs in FLOORS.items():
        floors[str(floor)] = [entry(s) for s in specs] + ([entry(s) for s in EVERY] if floor > 0 else [entry(EVERY[0])])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"floors": floors}, indent=1) + "\n", encoding="utf-8")
    print(f"{sum(len(v) for v in floors.values())} entries -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
