#!/usr/bin/env python3
"""Hypixel's reforges, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_reforges.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/reforges/<name>.json (generated: wiped each run):

    {"name": "Fierce", "modifier": "fierce", "types": ["SWORD", ...], "rarities": ["COMMON", ...],
     "stats": {"COMMON": {"strength": 2, "crit_chance": 2, ...}, ...}}

Blacksmith reforges (constants/reforges.json) are the random ones the Blacksmith rolls; reforge stones
(constants/reforgestones.json) add "stone": "<SKYBLOCK_ID>", "costs": {rarity: coins} and sometimes "ability" (Hypixel's
lore for the reforge's special effect) or "items" (the only items it fits, like Warped for the Aspect of the End).
"types" are Hypixel item categories: ARMOR stands for every armour piece, EQUIPMENT for necklaces, cloaks, belts,
gloves and bracelets, SWORD/ROD for both.
"""
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/reforges"
NEU = "NotEnoughUpdates-REPO-master/constants/"

# NEU's reforge item types -> Hypixel item categories (an item's "category").
TYPES = {
    "ARMOR": ["HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"],
    "EQUIPMENT": ["NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET"],
    "SWORD": ["SWORD", "LONGSWORD"],
    "SWORD/ROD": ["SWORD", "LONGSWORD", "FISHING_ROD", "FISHING_WEAPON"],
    "ROD": ["FISHING_ROD", "FISHING_WEAPON"],
    "PICKAXE": ["PICKAXE", "DRILL", "GAUNTLET"],
    "AXE": ["AXE"],
    "FARMING_TOOL": ["FARMING_TOOL"],
}
# NEU stat names -> our StatTypes ids, where they differ; None = a stat we don't have yet (left out).
STATS = {"trophy_chance": "trophy_fish_chance", "hunting_fortune": None, "hunting_wisdom": None, "overbloom": None}
RARITIES = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"]


def stats_of(raw):
    out = {}
    for rarity, stats in (raw or {}).items():
        mapped = {}
        for key, value in stats.items():
            stat = STATS.get(key, key)
            if stat is not None and value:
                mapped[stat] = value
        if mapped:
            out[rarity] = mapped
    return out


def types_of(raw):
    if isinstance(raw, dict):
        return [], list(raw.get("internalName", []))
    return TYPES.get(raw, [raw]), []


def modifier(entry):
    """The id Hypixel stores on a reforged item ("fierce", or "aote_stone" for Warped)."""
    return entry.get("nbtModifier") or entry["reforgeName"].lower().replace(" ", "_")


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    basic = json.loads(neu.read(NEU + "reforges.json"))
    stones = json.loads(neu.read(NEU + "reforgestones.json"))
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    written = {}
    for entry in basic.values():
        types, items = types_of(entry.get("itemTypes"))
        reforge = {"name": entry["reforgeName"], "modifier": modifier(entry), "types": types,
                   "rarities": entry.get("requiredRarities", RARITIES), "stats": stats_of(entry.get("reforgeStats"))}
        if items:
            reforge["items"] = items
        written[slug(entry["reforgeName"])] = reforge
    stone_count = 0
    for stone_id, entry in stones.items():
        types, items = types_of(entry.get("itemTypes"))
        reforge = {"name": entry["reforgeName"], "modifier": modifier(entry), "types": types,
                   "rarities": entry.get("requiredRarities", RARITIES), "stats": stats_of(entry.get("reforgeStats")), "stone": stone_id,
                   "costs": entry.get("reforgeCosts", {})}
        if items:
            reforge["items"] = items
        if entry.get("reforgeAbility"):
            ability = entry["reforgeAbility"]
            if isinstance(ability, dict):        # some differ by rarity: keep them all
                reforge["ability_by_rarity"] = ability
            else:
                reforge["ability"] = ability
        key = slug(entry["reforgeName"])
        if key in written and "stone" not in written[key]:
            key += "_stone"
        written[key] = reforge
        stone_count += 1
    for key, reforge in sorted(written.items()):
        (OUT / f"{key}.json").write_text(json.dumps(reforge, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(written)} reforges ({len(written) - stone_count} from the Blacksmith, {stone_count} from reforge stones) -> {OUT}")


if __name__ == "__main__":
    main()
