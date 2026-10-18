"""Imports every Hypixel SkyBlock item from the public API into SkyRPG item definitions.

    python tools/skyblock/import_items.py            # fetch the API and write the definitions
    python tools/skyblock/import_items.py --cached   # use tools/skyblock/cache/items.json from the last fetch

Source: https://api.hypixel.net/v2/resources/skyblock/items (public, no key). Each item becomes
skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items/<id>.json: its vanilla "base" item (Hypixel still
names 1.8 materials, mapped here), name, rarity, stats in our stat ids, head skin or leather colour, and every other
Hypixel field kept under "hypixel" for the systems that use them later (gemstone slots, stars, museum, minions...).
Items with an ability get it from their NEU lore (tools/skyblock/cache/neu-repo.zip, MIT): "ability": {"type":
"instant_transmission", "name": "Instant Transmission", "trigger": "RIGHT CLICK", "mana": 50, "cooldown": 0,
"description": "..."}, its type the ability's name in snake case (the game implements some of them).
The output folder is generated: it's wiped and rewritten on every run.
"""
import argparse
import json
import re
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
CACHE = ROOT / "tools/skyblock/cache/items.json"
API = "https://api.hypixel.net/v2/resources/skyblock/items"
JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]
DYES = ["ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye",
        "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]

# 1.8 material (with data values where they pick a variant) -> modern item id.
BY_DATA = {
    "WOOL": [c + "_wool" for c in COLORS],
    "CARPET": [c + "_carpet" for c in COLORS],
    "STAINED_GLASS": [c + "_stained_glass" for c in COLORS],
    "STAINED_GLASS_PANE": [c + "_stained_glass_pane" for c in COLORS],
    "STAINED_CLAY": [c + "_terracotta" for c in COLORS],
    "BANNER": [c + "_banner" for c in reversed(COLORS)],
    "INK_SACK": DYES,
    "WOOD": [w + "_planks" for w in WOODS],
    "WOOD_STEP": [w + "_slab" for w in WOODS],
    "SAPLING": [w + "_sapling" for w in WOODS],
    "LOG": ["oak_log", "spruce_log", "birch_log", "jungle_log"],
    "LOG_2": ["acacia_log", "dark_oak_log"],
    "LEAVES": ["oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves"],
    "LEAVES_2": ["acacia_leaves", "dark_oak_leaves"],
    "COAL": ["coal", "charcoal"],
    "COOKED_FISH": ["cooked_cod", "cooked_salmon"],
    "RAW_FISH": ["cod", "salmon", "tropical_fish", "pufferfish"],
    "DIRT": ["dirt", "coarse_dirt", "podzol"],
    "DOUBLE_PLANT": ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"],
    "GOLDEN_APPLE": ["golden_apple", "enchanted_golden_apple"],
    "LONG_GRASS": ["dead_bush", "short_grass", "fern"],
    "PRISMARINE": ["prismarine", "prismarine_bricks", "dark_prismarine"],
    "QUARTZ_BLOCK": ["quartz_block", "chiseled_quartz_block", "quartz_pillar"],
    "RED_ROSE": ["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
                 "pink_tulip", "oxeye_daisy"],
    "RED_SANDSTONE": ["red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone"],
    "SAND": ["sand", "red_sand"],
    "SANDSTONE": ["sandstone", "chiseled_sandstone", "cut_sandstone"],
    "SKULL_ITEM": ["skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head"],
    "SMOOTH_BRICK": ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"],
    "SPONGE": ["sponge", "wet_sponge"],
    "STEP": ["smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab",
             "nether_brick_slab", "quartz_slab"],
    "STONE": ["stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite"],
}
RENAMED = {
    "ACACIA_DOOR_ITEM": "acacia_door", "BIRCH_DOOR_ITEM": "birch_door", "DARK_OAK_DOOR_ITEM": "dark_oak_door",
    "JUNGLE_DOOR_ITEM": "jungle_door", "SPRUCE_DOOR_ITEM": "spruce_door", "WOOD_DOOR": "oak_door",
    "BIRCH_WOOD_STAIRS": "birch_stairs", "JUNGLE_WOOD_STAIRS": "jungle_stairs", "SPRUCE_WOOD_STAIRS": "spruce_stairs",
    "WOOD_STAIRS": "oak_stairs", "SMOOTH_STAIRS": "stone_brick_stairs",
    "FENCE": "oak_fence", "FENCE_GATE": "oak_fence_gate", "NETHER_FENCE": "nether_brick_fence", "IRON_FENCE": "iron_bars",
    "BED": "red_bed", "BOAT": "oak_boat", "BOOK_AND_QUILL": "writable_book", "BREWING_STAND_ITEM": "brewing_stand",
    "BRICK": "bricks", "CLAY_BRICK": "brick", "CLAY": "clay", "CARROT_ITEM": "carrot", "POTATO_ITEM": "potato",
    "CARROT_STICK": "carrot_on_a_stick", "CAULDRON_ITEM": "cauldron", "FLOWER_POT_ITEM": "flower_pot",
    "COBBLE_WALL": "cobblestone_wall", "COMMAND": "command_block", "DIODE": "repeater",
    "REDSTONE_COMPARATOR": "comparator", "REDSTONE_LAMP_OFF": "redstone_lamp", "REDSTONE_TORCH_ON": "redstone_torch",
    "DIAMOND_BARDING": "diamond_horse_armor", "GOLD_BARDING": "golden_horse_armor", "IRON_BARDING": "iron_horse_armor",
    "DIAMOND_SPADE": "diamond_shovel", "GOLD_SPADE": "golden_shovel", "IRON_SPADE": "iron_shovel",
    "STONE_SPADE": "stone_shovel", "WOOD_SPADE": "wooden_shovel",
    "GOLD_AXE": "golden_axe", "GOLD_HOE": "golden_hoe", "GOLD_PICKAXE": "golden_pickaxe", "GOLD_SWORD": "golden_sword",
    "GOLD_HELMET": "golden_helmet", "GOLD_CHESTPLATE": "golden_chestplate", "GOLD_LEGGINGS": "golden_leggings",
    "GOLD_BOOTS": "golden_boots",
    "WOOD_AXE": "wooden_axe", "WOOD_HOE": "wooden_hoe", "WOOD_PICKAXE": "wooden_pickaxe", "WOOD_SWORD": "wooden_sword",
    "WOOD_BUTTON": "oak_button", "WOOD_PLATE": "oak_pressure_plate", "STONE_PLATE": "stone_pressure_plate",
    "GOLD_PLATE": "light_weighted_pressure_plate", "IRON_PLATE": "heavy_weighted_pressure_plate",
    "TRAP_DOOR": "oak_trapdoor", "SIGN": "oak_sign", "WORKBENCH": "crafting_table", "RAILS": "rail",
    "EMPTY_MAP": "map", "MAP": "filled_map", "ENCHANTMENT_TABLE": "enchanting_table",
    "ENDER_PORTAL_FRAME": "end_portal_frame", "ENDER_STONE": "end_stone", "EXP_BOTTLE": "experience_bottle",
    "EYE_OF_ENDER": "ender_eye", "EXPLOSIVE_MINECART": "tnt_minecart", "POWERED_MINECART": "furnace_minecart",
    "STORAGE_MINECART": "chest_minecart", "FIREWORK": "firework_rocket", "FIREWORK_CHARGE": "firework_star",
    "GRASS": "grass_block", "GRILLED_PORK": "cooked_porkchop", "PORK": "porkchop", "RAW_BEEF": "beef",
    "RAW_CHICKEN": "chicken", "HARD_CLAY": "terracotta", "HUGE_MUSHROOM_1": "brown_mushroom_block",
    "HUGE_MUSHROOM_2": "red_mushroom_block", "LEASH": "lead", "MELON": "melon_slice", "MELON_BLOCK": "melon",
    "MOB_SPAWNER": "spawner", "MONSTER_EGG": "pig_spawn_egg", "MUSHROOM_SOUP": "mushroom_stew", "MYCEL": "mycelium",
    "NETHER_BRICK": "nether_bricks", "NETHER_BRICK_ITEM": "nether_brick", "NETHER_STALK": "nether_wart",
    "PISTON_BASE": "piston", "PISTON_STICKY_BASE": "sticky_piston", "QUARTZ_ORE": "nether_quartz_ore",
    "SEEDS": "wheat_seeds", "SNOW_BALL": "snowball", "SPECKLED_MELON": "glistering_melon_slice",
    "SULPHUR": "gunpowder", "THIN_GLASS": "glass_pane", "WATCH": "clock", "WATER_LILY": "lily_pad", "WEB": "cobweb",
    "YELLOW_FLOWER": "dandelion",
    "GOLD_RECORD": "music_disc_13", "GREEN_RECORD": "music_disc_cat", "RECORD_3": "music_disc_blocks",
    "RECORD_4": "music_disc_chirp", "RECORD_5": "music_disc_far", "RECORD_6": "music_disc_mall",
    "RECORD_7": "music_disc_mellohi", "RECORD_8": "music_disc_stal", "RECORD_9": "music_disc_strad",
    "RECORD_10": "music_disc_ward", "RECORD_11": "music_disc_11", "RECORD_12": "music_disc_wait",
}

# Hypixel stat key (lower-cased) -> our StatTypes id, where they differ.
STATS = {
    "walk_speed": "speed", "critical_chance": "crit_chance", "critical_damage": "crit_damage",
    "health_regeneration": "health_regen", "ability_damage_percent": "ability_damage",
}
# Fields that become top-level definition keys; everything else goes under "hypixel".
TAKEN = {"id", "material", "durability", "name", "tier", "stats", "skin", "color", "glowing", "category",
         "category_display", "description", "unstackable", "item_model", "tiered_stats"}
FORMATTING = re.compile("§.|%%[a-z_]+%%")
# Hypixel's %%colour%% placeholders in descriptions -> Minecraft formatting codes.
CODES = {"black": "0", "dark_blue": "1", "dark_green": "2", "dark_aqua": "3", "dark_red": "4", "dark_purple": "5",
         "gold": "6", "gray": "7", "dark_gray": "8", "blue": "9", "green": "a", "aqua": "b", "red": "c",
         "light_purple": "d", "yellow": "e", "white": "f", "obfuscated": "k", "bold": "l", "strikethrough": "m",
         "underline": "n", "italic": "o", "reset": "r"}
# The order Hypixel lists stats in on an item (red combat stats, then the green ones, then the rest).
ORDER = ["damage", "strength", "crit_chance", "crit_damage", "attack_speed", "ability_damage", "weapon_ability_damage",
         "swing_range", "health", "defense", "speed", "intelligence", "magic_find", "pet_luck", "true_defense",
         "sea_creature_chance", "ferocity", "health_regen", "vitality", "mending", "mining_speed", "pristine",
         "mining_fortune", "ore_fortune", "block_fortune", "dwarven_metal_fortune", "gemstone_fortune", "mining_spread",
         "breaking_power", "farming_fortune", "foraging_fortune", "fishing_speed", "double_hook_chance",
         "treasure_chance", "trophy_fish_chance"]


def fetch(cached):
    if cached:
        return json.loads(CACHE.read_text(encoding="utf-8"))
    request = urllib.request.Request(API, headers={"User-Agent": "skyrpg-skyblock-importer"})
    raw = urllib.request.urlopen(request, timeout=60).read().decode("utf-8")
    data = json.loads(raw)
    if not data.get("success"):
        sys.exit("The API said no: " + raw[:200])
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text(raw, encoding="utf-8")
    return data


def vanilla_items():
    with zipfile.ZipFile(JAR) as jar:
        return {Path(n).stem for n in jar.namelist() if n.startswith("assets/minecraft/items/") and n.endswith(".json")}


# Galatea's items are plain paper in Hypixel's data (their look comes from Hypixel's own models) and FurfSky has none:
# a fitting vanilla item instead.
LOOKS = {"ABYSMAL_LASSO": "lead", "VINERIP_LASSO": "lead", "ENTANGLER_LASSO": "lead", "EVERSTRETCH_LASSO": "lead", "BASIC_FISHING_NET": "cobweb", "MEDIUM_FISHING_NET": "cobweb", "TURBO_FISHING_NET": "cobweb", "GIGANTIC_FISHING_NET": "cobweb", "VINESAP": "slime_ball", "TENDER_WOOD": "stick", "HUNTING_TOOLKIT": "bundle", "LUSHLILAC": "lilac", "SEA_LUMIES": "sea_pickle", "STARLYN_PRIZE": "nether_star", "CRITTER_CAPSULE": "ender_eye", "MOONGLADE_JEWEL": "emerald", "MANGROVE_VINE": "vine"}


def base_of(item):
    if item["id"] in LOOKS:
        return LOOKS[item["id"]]
    material, data = item["material"], item.get("durability", 0) or 0
    if material in BY_DATA:
        variants = BY_DATA[material]
        return variants[data] if 0 <= data < len(variants) else variants[0]
    return RENAMED.get(material, material.lower())


def stat_id(key):
    key = key.lower()
    return STATS.get(key, key)


def label(category):
    return " ".join(word.capitalize() for word in category.split("_"))


def path_of(skyblock_id):
    path = skyblock_id.lower().replace(":", "-")
    if not re.fullmatch(r"[a-z0-9_.\-]+", path):
        raise ValueError("can't make a file name of " + skyblock_id)
    return path


def convert(item, known_stats):
    out = {"skyblock_id": item["id"], "base": "minecraft:" + base_of(item),
           "name": FORMATTING.sub("", item["name"]).strip(), "rarity": (item.get("tier") or "COMMON").lower()}
    category = item.get("category")
    if category and category != "NONE":
        out["category"] = category
        out["type"] = item.get("category_display") or label(category)
    else:
        out["type"] = ""
    stats = dict(item.get("stats", {}))
    for key, values in item.get("tiered_stats", {}).items():
        stats.setdefault(key, values[0])
    if stats:
        mapped = {}
        for key, value in stats.items():
            sid = stat_id(key)
            if sid not in known_stats:
                print("  unknown stat", key, "on", item["id"])
                continue
            mapped[sid] = mapped.get(sid, 0) + value
        if mapped:
            out["stats"] = ordered(mapped)
    if item.get("description"):
        out["description"] = description(item["description"])
    if item.get("glowing"):
        out["glint"] = True
    if item.get("unstackable"):
        out["unstackable"] = True
    if "skin" in item:
        out["skin"] = item["skin"]
    if "color" in item:
        r, g, b = (int(part) for part in item["color"].split(","))
        out["color"] = "#%02X%02X%02X" % (r, g, b)
    rest = {k: v for k, v in item.items() if k not in TAKEN}
    if "tiered_stats" in item:
        rest["tiered_stats"] = item["tiered_stats"]
    if rest:
        out["hypixel"] = rest
    return out


NEU = ROOT / "tools/skyblock/cache/neu-repo.zip"
NEU_ITEMS = "NotEnoughUpdates-REPO-master/items/"
ICONS = re.compile("[\ue000-\uf8ff]")
ABILITY = re.compile(r"^(?:Item )?Ability: (.+?)\s{1,2}(RIGHT CLICK|LEFT CLICK|SNEAK RIGHT CLICK|SNEAK LEFT CLICK|SNEAK|RIGHT CLICK x2|HOLD RIGHT CLICK)?\s*$")
NUMBER = re.compile(r"([\d,.]+)")


def neu_abilities():
    """Item id -> its first ability from NEU's lore (name, trigger, description, mana cost, cooldown)."""
    if not NEU.exists():
        print("  no NEU repo cache: items get no abilities")
        return {}
    out = {}
    with zipfile.ZipFile(NEU) as neu:
        for name in neu.namelist():
            if not (name.startswith(NEU_ITEMS) and name.endswith(".json")):
                continue
            try:
                item = json.loads(neu.read(name))
            except ValueError:
                continue
            lore = [ICONS.sub("", FORMATTING.sub("", line)).rstrip() for line in item.get("lore", [])]
            for i, line in enumerate(lore):
                match = ABILITY.match(line.strip())
                if not match:
                    continue
                ability = {"name": match.group(1).strip(), "trigger": match.group(2) or "RIGHT CLICK"}
                words = []
                for rest in lore[i + 1:]:
                    text = rest.strip()
                    if not text or ABILITY.match(text):
                        break
                    if text.startswith("Mana Cost:"):
                        ability["mana"] = float(NUMBER.search(text).group(1).replace(",", "")) if NUMBER.search(text) else 0
                    elif text.startswith("Cooldown:"):
                        ability["cooldown"] = float(NUMBER.search(text).group(1).replace(",", "")) if NUMBER.search(text) else 0
                    elif text.startswith("Soulflow Cost:") or text.startswith("Health Cost:"):
                        continue
                    else:
                        words.append(text)
                ability["description"] = re.sub(r"\s+", " ", " ".join(words)).strip()
                ability["type"] = re.sub(r"[^a-z0-9]+", "_", ability["name"].lower()).strip("_")
                out[item.get("internalname", "")] = ability
                break
    return out


def description(text):
    """Hypixel's description text with formatting codes; lore is gray unless it says otherwise."""
    text = re.sub(r"%%([a-z_]+)%%", lambda m: ("§" + CODES[m.group(1)]) if m.group(1) in CODES else "", text)
    return text if text.startswith("§") else "§7" + text


def ordered(stats):
    rank = {sid: i for i, sid in enumerate(ORDER)}
    return dict(sorted(stats.items(), key=lambda kv: rank.get(kv[0], len(ORDER))))


def our_stats():
    source = (ROOT / "core/src/main/java/com/skyrpg/core/stats/StatTypes.java").read_text(encoding="utf-8")
    return set(re.findall(r'add\("([a-z_]+)"', source))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cached", action="store_true", help="use the last fetched API response")
    args = parser.parse_args()
    data = fetch(args.cached)
    vanilla = vanilla_items()
    known_stats = our_stats()
    abilities = neu_abilities()
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    missing = set()
    for item in data["items"]:
        definition = convert(item, known_stats)
        if item["id"] in abilities:
            definition["ability"] = abilities[item["id"]]
        base = definition["base"].removeprefix("minecraft:")
        if base not in vanilla:
            missing.add((item["material"], item.get("durability"), base))
            definition["base"] = "minecraft:stone"
        (OUT / (path_of(item["id"]) + ".json")).write_text(json.dumps(definition, ensure_ascii=False, indent=1) + "\n",
                                                           encoding="utf-8")
    for material, durability, base in sorted(missing, key=str):
        print("  no vanilla item", base, "for", material, durability)
    print(f"Wrote {len(data['items'])} items to {OUT.relative_to(ROOT)} ({sum(1 for i in data['items'] if i['id'] in abilities)} with abilities)")


if __name__ == "__main__":
    main()
