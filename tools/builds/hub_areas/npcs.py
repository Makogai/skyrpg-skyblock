#!/usr/bin/env python3
"""The public islands' NPCs, warps, Fairy Souls and launch pads, from the spots the area scripts recorded
(out/<area>_spots.json).

    python tools/builds/hub_areas/npcs.py

Writes npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs/<id>.json (who they are, where they stand, what they do),
the Hypixel warps (skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/warps/<id>.json), each island's Fairy Souls
(.../fairy_souls/<island>.json), every launch pad (.../launch_pads/<area>_<n>.json) and the Deep Caverns' lift
(.../lifts/deep_caverns.json). Skins are vanilla's default
player skins until the owners paint their own.
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NPCS = ROOT / "npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
WARPS = DATA / "warps"
SOULS = DATA / "fairy_souls"
PADS = DATA / "launch_pads"
LIFTS = DATA / "lifts"
ZONES = DATA / "zones"
HUB = "skyrpg_hub:hub"


def skin(name, slim=False):
    return f"minecraft:entity/player/{'slim' if slim else 'wide'}/{name}"


def shop(npc):
    return {"type": "skyblock_shop", "params": {"shop": npc}}


# spot name -> (file id, display name, role, colour, hint, skin, slim, sound, greetings, action)
PEOPLE = {
    # ---- the Hub
    "Pat": ("pat", "Pat", "Gravedigger", "#AAAAAA", "Shop", skin("steve"), False, "minecraft:item.shovel.flatten",
            ["Flint and gravel, fresh from the graves.", "Mind the zombies. They don't mind you."], shop("pat")),
    "Farmer": ("hub_farmer", "Farmer", "Farm", "#F2C14E", "Talk", skin("kai"), False, "minecraft:item.hoe.till",
               ["Wheat grows back fast here. Harvest all you like.", "Every field is yours to work."], None),
    "Arthur": ("arthur", "Arthur", "Minions", "#55FFFF", "Talk", skin("efe"), False, "minecraft:block.note_block.chime",
               ["That's a Wheat Minion. It works while you're away.", "Give a minion a Compactor and it packs its own items."],
               None),
    "Lumber Jack": ("lumber_jack", "Lumber Jack", "Forest", "#C98A4B", "Talk", skin("makena"), False,
                    "minecraft:item.axe.strip", ["Chop away. The trees grow back.", "Oak's the best wood for a beginner."],
                    None),
    "Lonely Philosopher": ("lonely_philosopher", "Lonely Philosopher", "Merchant", "#FFAA00", "Shop", skin("noor"), False,
                           "minecraft:entity.villager.ambient",
                           ["The wolves never ask what any of it means.", "A scroll home, for when the walk gets long."],
                           shop("lonely_philosopher")),
    "Scoop": ("scoop", "Scoop", "Merchant", "#FFFFFF", "Shop", skin("sunny"), False, "minecraft:block.snow.break",
              ["Snow up here all year round.", "A good shovel makes short work of it."], shop("scoop")),
    "Amelia": ("amelia", "Amelia", "Balloonist", "#FF5555", "Talk", skin("alex", True), True, "minecraft:block.wool.place",
               ["The view from the balloon is worth the climb.", "Christopher keeps the burner going."], None),
    "Christopher": ("christopher", "Christopher", "Balloonist", "#FFAA00", "Talk", skin("ari"), False,
                    "minecraft:block.campfire.crackle", ["Hold on to the ropes!", "One day we'll fly all the way round."],
                    None),
    "Tia the Fairy": ("tia_the_fairy", "Tia the Fairy", "Fairy Souls", "#FF55FF", "Talk", skin("zuri", True), True,
                      "minecraft:block.amethyst_block.chime",
                      ["Fairy Souls are hidden on every island.", "Bring me the souls you find."],
                      {"type": "fairy_exchange", "params": {}}),
    "Fisherman Gerald": ("fisherman_gerald", "Fisherman Gerald", "Fishing", "#55AAFF", "Talk", skin("steve", True), True,
                         "minecraft:entity.fishing_bobber.splash",
                         ["Something big lives in this lake.", "Cast near the dock. They bite more there."], None),
    "Shifty": ("shifty", "Shifty", "Brews", "#AA00AA", "Shop", skin("kai", True), True, "minecraft:entity.witch.drink",
               ["Brews for the brave. Don't ask what's in them.", "Psst. Over here."], shop("shifty")),
    "Lucius": ("lucius", "Lucius", "Merchant", "#AAAAAA", "Shop", skin("efe", True), True,
               "minecraft:block.chain.place", ["A ring for the shady ones.", "Every deal has a price."], shop("lucius")),
    "Sirius": ("sirius", "Sirius", "Dark Auction", "#5555FF", "Talk", skin("noor", True), True,
               "minecraft:block.beacon.ambient", ["The Dark Auction opens every few days.", "Bring coins. Lots of them."],
               None),
    # ---- The Farming Islands: The Barn
    "Farmhand": ("farmhand", "Farmhand", "The Barn", "#F2C14E", "Talk", skin("makena", True), True,
                 "minecraft:item.crop.plant",
                 ["Pumpkins on one side, melons on the other. Don't mix them up.", "The cows wander, but they never leave."],
                 None),
    "Windmill Operator": ("windmill_operator", "Windmill Operator", "Merchant", "#FFFFFF", "Shop", skin("ari", True), True,
                          "minecraft:block.wooden_door.open",
                          ["The sails never stop. Neither do the seeds.", "Need something to plant?"],
                          shop("windmill_operator")),
    # ---- The Farming Islands: Mushroom Desert
    "Trevor": ("trevor", "Trevor", "Trapper", "#C98A4B", "Talk", skin("kai"), False, "minecraft:entity.rabbit.ambient",
               ["Tracks everywhere out here. Something's always running.", "A good trapper waits. Then waits some more."],
               None),
    "Jake": ("jake", "Jake", "Merchant", "#FFAA00", "Shop", skin("sunny"), False, "minecraft:entity.villager.trade",
             ["Hot out there. Buy something cool.", "Everything a desert farmer needs."], shop("jake")),
    "Shepherd": ("shepherd", "Shepherd", "Mushroom Desert", "#FFFFFF", "Talk", skin("efe"), False,
                 "minecraft:entity.sheep.ambient", ["Count them with me. One, two... no, that one moved.",
                                                    "Wool grows back. Sheep don't mind."], None),
    "Farmer Jon": ("farmer_jon", "Farmer Jon", "Mushroom Desert", "#55FF55", "Talk", skin("steve"), False,
                   "minecraft:block.grass.place", ["Cactus doesn't need much. Just room.", "Mind the spines."], None),
    # ---- The Park
    "Charlie": ("charlie", "Charlie", "The Park", "#55FF55", "Talk", skin("noor"), False, "minecraft:block.wood.place",
                ["Birch is light and clean. Good wood.", "Welcome to the Park. Mind the squirrels."], None),
    "Vanessa": ("vanessa", "Vanessa", "The Park", "#FF55FF", "Talk", skin("alex", True), True,
                "minecraft:entity.fishing_bobber.splash", ["The lake is calm today.", "I come here to think."], None),
    "Kelly": ("kelly", "Kelly", "Spruce Woods", "#55AAFF", "Talk", skin("zuri", True), True,
              "minecraft:block.campfire.crackle", ["The vikings tell a story every night.", "Spruce burns slow and warm."],
              None),
    "Gustave": ("gustave", "Gustave", "Viking", "#AA5500", "Talk", skin("makena"), False, "minecraft:item.axe.strip",
                ["Raiders need good axes.", "Sit by the fire, traveller."], None),
    "Melancholic Viking": ("melancholic_viking", "Melancholic Viking", "Viking", "#5555FF", "Talk", skin("efe"), False,
                           "minecraft:block.note_block.didgeridoo",
                           ["Once I sailed the far seas. Now I sit.", "Even vikings get tired."], None),
    "Old Shaman Nyko": ("old_shaman_nyko", "Old Shaman Nyko", "Merchant", "#AA00AA", "Shop", skin("ari"), False,
                        "minecraft:entity.wolf.ambient_baby",
                        ["The spirits of the wolves walk in that cave.", "Their howls carry far at night."],
                        shop("old_shaman_nyko")),
    "Ryan": ("ryan", "Ryan", "Campfire Cult", "#FF5555", "Talk", skin("kai", True), True, "minecraft:block.campfire.crackle",
             ["The fire never goes out here.", "Bring wood. The fire is always hungry."], None),
    "Campfire Adept": ("campfire_adept", "Campfire Adept", "Campfire Cult", "#FFAA00", "Talk", skin("sunny", True), True,
                       "minecraft:block.fire.ambient", ["We keep the flame. The flame keeps us.", "Sit. Warm up."], None),
    "Melody": ("melody", "Melody", "The Park", "#FF55FF", "Talk", skin("noor", True), True,
               "minecraft:block.note_block.harp", ["Play me a song?", "The savanna has the best acoustics."], None),
    "Master Tactician Funk": ("master_tactician_funk", "Master Tactician Funk", "Merchant", "#FFAA00", "Shop",
                              skin("steve"), False, "minecraft:item.armor.equip_iron",
                              ["A good plan beats a big sword.", "Study the field before you fight on it."],
                              shop("master_tactician")),
    "Worker Xavier": ("worker_xavier", "Worker Xavier", "The Park", "#AAAAAA", "Talk", skin("ari"), False,
                      "minecraft:block.wood.hit", ["Acacia is tough wood. Good for building.", "Back to work."], None),
    "Molbert": ("molbert", "Molbert", "Jungle Island", "#55FF55", "Talk", skin("efe", True), True,
                "minecraft:block.vine.place", ["The jungle swallows everything eventually.", "Mind the vines."], None),
    "Juliette": ("juliette", "Juliette", "Jungle Island", "#FF55FF", "Talk", skin("makena", True), True,
                 "minecraft:block.cocoa.place", ["The old temple has secrets.", "Have you seen the view from the top?"],
                 None),
    # ---- Gold Mine
    "Rusty": ("rusty", "Rusty", "Gold Mine", "#AA5500", "Talk", skin("steve"), False, "minecraft:block.anvil.land",
              ["I clean up after the miners. Somebody has to.", "Find anything shiny down there?"], None),
    "Iron Forger": ("iron_forger", "Iron Forger", "Merchant", "#FFFFFF", "Shop", skin("kai"), False,
                    "minecraft:block.anvil.use", ["Iron for those who earned it.", "The forge is hot. Step back."],
                    shop("iron_forger")),
    "Gold Forger": ("gold_forger", "Gold Forger", "Merchant", "#FFAA00", "Shop", skin("sunny"), False,
                    "minecraft:block.anvil.use", ["Gold, polished and ready.", "Shines brighter than iron, doesn't it?"],
                    shop("gold_forger")),
    "Lazy Miner": ("lazy_miner", "Lazy Miner", "Gold Mine", "#AAAAAA", "Talk", skin("noor"), False,
                   "minecraft:entity.player.burp", ["Five more minutes.", "The gold isn't going anywhere."], None),
    # ---- Deep Caverns
    "Walter": ("walter", "Walter", "Merchant", "#55FFFF", "Shop", skin("ari"), False, "minecraft:entity.villager.trade",
               ["Going deeper? You'll want supplies.", "Don't forget the way back up."], shop("walter")),
    "Lapis Miner": ("lapis_miner", "Lapis Miner", "Deep Caverns", "#5555FF", "Talk", skin("efe"), False,
                    "minecraft:block.stone.break", ["Blue as far as the eye can see.", "The zombies down here glow blue."],
                    None),
    "Redstone Miner": ("redstone_miner", "Redstone Miner", "Deep Caverns", "#FF5555", "Talk", skin("kai"), False,
                       "minecraft:block.stone.break", ["The pigmen don't like visitors.", "Redstone hums if you listen."],
                       None),
    "Rhys": ("rhys", "Rhys", "Deep Caverns", "#AA00AA", "Talk", skin("makena"), False, "minecraft:block.beacon.ambient",
             ["You made it all the way down.", "The obsidian here is older than the islands."], None),
    # ---- Spider's Den
    "Grandma Wolf": ("grandma_wolf", "Grandma Wolf", "Spider's Den", "#AAAAAA", "Talk", skin("zuri", True), True,
                     "minecraft:entity.wolf.ambient_baby", ["Come in, dear. Mind the webs.", "The spiders leave me be."],
                     None),
    "Haymitch": ("haymitch", "Haymitch", "Spider's Den", "#C98A4B", "Talk", skin("steve"), False,
                 "minecraft:entity.spider.ambient", ["The mound gets meaner the higher you climb.", "String, string everywhere."],
                 None),
    "Rick": ("rick", "Rick", "Spider's Den", "#AAAAAA", "Talk", skin("sunny"), False, "minecraft:block.gravel.break",
             ["Gravel pit's all yours. Bring a shovel.", "Flint's in there somewhere."], None),
    "Shaggy": ("shaggy", "Shaggy", "Merchant", "#55FF55", "Shop", skin("noor"), False, "minecraft:entity.villager.trade",
               ["Like, totally spooky up here.", "Got snacks? I got stuff."], shop("shaggy")),
    "Archaeologist": ("archaeologist", "Archaeologist", "Spider's Den", "#FFAA00", "Talk", skin("alex", True), True,
                      "minecraft:item.brush.brushing.gravel",
                      ["Every layer of gravel tells a story.", "Careful where you dig."], None),
}
LIFT_OPERATOR = ("Lift Operator", "Deep Caverns", "#FFFF55", "Talk", skin("steve"), False, "minecraft:block.chain.step",
                 ["Going down? Hold on to the rail.", "The lift stops at every layer you've been to."],
                 {"type": "lift", "params": {"lift": "deep_caverns"}})

# The Hypixel warps inside the Hub: /warp castle, /warp crypt, /warp da.
HUB_WARPS = {
    "castle": ("ruins", "Ruins", "Castle", "The Hub's ancient ruins and their wolves", "minecraft:mossy_stone_bricks", 40),
    "crypt": ("coal_mine", None, "Crypt", "The Hub Crypts, under the Coal Mine", "minecraft:skeleton_skull", 41),
    "da": ("wilderness", None, "Dark Auction", "Sirius's Dark Auction, in the Wilderness", "minecraft:wither_skeleton_skull", 42),
}
FIXED = {
    # Spots the area scripts don't record as warps.
    "crypt": {"pos": [-9.5, 45.0, -167.5], "yaw": 0.0, "area": "Coal Mine"},
    "da": {"pos": [178.5, 65.0, 1.5], "yaw": 180.0, "area": "Wilderness"},
}
# The other islands' warps: id -> (area, warp spot name, name, description, icon, order). Visiting the zone the warp
# stands in unlocks it.
ISLAND_WARPS = {
    "barn": ("barn", "The Barn", "The Barn", "Wheat, carrots, potatoes, pumpkins and melons", "minecraft:wheat", 20),
    "desert": ("mushroom_desert", "Mushroom Desert", "Mushroom Desert", "Cactus, sugar cane, cocoa and mushrooms",
               "minecraft:red_mushroom", 21),
    "park": ("park_west", "The Park", "The Park", "The Foraging island: birch, spruce, dark oak, acacia and jungle",
             "minecraft:birch_sapling", 22),
    "gold": ("gold_mine", "Gold Mine", "Gold Mine", "Gold, iron and coal in an open pit", "minecraft:gold_ingot", 23),
    "deep": ("deep_caverns", "Deep Caverns", "Deep Caverns", "Six layers down: lapis, redstone, emerald, diamond, obsidian",
             "minecraft:diamond", 24),
    "spider": ("spiders_den", "Spider's Den", "Spider's Den", "The Spider Mound and its spiders", "minecraft:spider_eye", 25),
}


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower().replace("'", "")).strip("_")


def person(name):
    if name in PEOPLE:
        return PEOPLE[name]
    if name.startswith("Lift Operator"):
        return (slug(name),) + LIFT_OPERATOR
    return None


def zone_at(dimension, pos):
    """The zone a spot stands in (its name is the area name Fast Travel unlocks on)."""
    for file in ZONES.glob("*.json"):
        zone = json.loads(file.read_text(encoding="utf-8"))
        a, b = zone.get("from"), zone.get("to")
        if zone.get("dimension", HUB) == dimension and a and b and all(
                min(a[i], b[i]) <= pos[i] <= max(a[i], b[i]) + 1 for i in range(3)):
            return zone["name"]
    return None


def write(path, data, indent=2):
    path.write_text(json.dumps(data, indent=indent) + "\n", encoding="utf-8")


def main():
    for d in (NPCS, WARPS, SOULS, PADS, LIFTS):
        d.mkdir(parents=True, exist_ok=True)
    all_spots = {f.name[:-len("_spots.json")]: json.loads(f.read_text(encoding="utf-8"))
                 for f in sorted((HERE / "out").glob("*_spots.json"))}
    made = []
    for area_id, spots in all_spots.items():
        for name, spot in spots.get("npcs", {}).items():
            who = person(name)
            if who is None:
                print(f"  no NPC set up for {name} ({area_id})")
                continue
            file_id, display, role, color, hint, skin_id, slim, sound, greetings, action = who
            if name.startswith("Lift Operator"):
                display = name
            npc = {"name": display, "role": role, "color": color, "hint": hint, "skin": skin_id,
                   "model": "slim" if slim else "wide", "dimension": spot.get("dimension", spots.get("dimension", HUB)),
                   "pos": spot["pos"], "yaw": spot.get("yaw", 0.0), "sound": sound, "greetings": greetings}
            if action:
                npc["action"] = action
            write(NPCS / f"{file_id}.json", npc)
            made.append(display)

    for warp_id, (area_id, spot_name, name, description, icon, order) in HUB_WARPS.items():
        if warp_id in FIXED:
            spot = FIXED[warp_id]
            area_name = spot["area"]
        else:
            spot = all_spots[area_id]["warps"][spot_name]
            area_name = spot_name
        write(WARPS / f"{warp_id}.json", {"name": name, "description": description, "icon": icon, "dimension": HUB,
                                          "pos": spot["pos"], "yaw": spot.get("yaw", 0.0), "area": area_name, "order": order})
    warps = len(HUB_WARPS)
    for warp_id, (area_id, spot_name, name, description, icon, order) in ISLAND_WARPS.items():
        spots = all_spots.get(area_id)
        if spots is None or spot_name not in spots.get("warps", {}):
            print(f"  warp {warp_id}: {area_id} isn't built yet")
            continue
        spot = spots["warps"][spot_name]
        dimension = spots.get("dimension", HUB)
        area_name = zone_at(dimension, spot["pos"]) or spots.get("island") or name
        write(WARPS / f"{warp_id}.json", {"name": name, "description": description, "icon": icon, "dimension": dimension,
                                          "pos": spot["pos"], "yaw": spot.get("yaw", 0.0), "area": area_name, "order": order})
        warps += 1

    # Each island's Fairy Souls in its own file.
    islands = {}
    for spots in all_spots.values():
        island = spots.get("island", "Hub")
        entry = islands.setdefault(island, {"island": island, "dimension": spots.get("dimension", HUB), "souls": []})
        entry["souls"] += spots.get("fairy_souls", [])
    for old in SOULS.glob("*.json"):
        old.unlink()
    for island, entry in islands.items():
        write(SOULS / f"{slug(island)}.json", entry, indent=1)

    # Every launch pad (generated: the folder is rewritten each run).
    for old in PADS.glob("*.json"):
        old.unlink()
    pads = 0
    for area_id, spots in all_spots.items():
        for i, pad in enumerate(spots.get("pads", [])):
            write(PADS / f"{area_id}_{i + 1}.json", pad)
            pads += 1

    # The Deep Caverns' lift: a stop at every Lift Operator, top to bottom; you land outside the cage's open side.
    stops = []
    deep = all_spots.get("deep_caverns", {})
    for name, spot in deep.get("npcs", {}).items():
        if name.startswith("Lift Operator"):
            x, y, z = spot["pos"]
            area = zone_at(deep["dimension"], spot["pos"]) or name
            stops.append({"name": area, "area": area, "pos": [x, y, z + 4], "yaw": 0.0})
    if stops:
        stops.sort(key=lambda s: -s["pos"][1])
        write(LIFTS / "deep_caverns.json", {"name": "Deep Caverns Lift", "dimension": deep["dimension"], "stops": stops})

    souls = ", ".join(f"{island} {len(e['souls'])}" for island, e in islands.items())
    print(f"{len(made)} NPCs; {warps} warps; {pads} launch pads; {len(stops)} lift stops; Fairy Souls: {souls}")


if __name__ == "__main__":
    main()
