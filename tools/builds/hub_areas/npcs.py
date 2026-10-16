#!/usr/bin/env python3
"""The Hub areas' NPCs and warps, from the spots the area scripts recorded (out/<area>_spots.json).

    python tools/builds/hub_areas/npcs.py

Writes npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs/<id>.json (who they are, where they stand, what they do)
and the Hypixel warps inside the Hub (skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/warps/<id>.json).
Skins are vanilla's default player skins until the owners paint their own.
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
NPCS = ROOT / "npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs"
WARPS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/warps"
SOULS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/fairy_souls"


def skin(name, slim=False):
    return f"minecraft:entity/player/{'slim' if slim else 'wide'}/{name}"


# spot name -> (file id, display name, role, colour, hint, skin, slim, sound, greetings, action)
PEOPLE = {
    "Pat": ("pat", "Pat", "Gravedigger", "#AAAAAA", "Shop", skin("steve"), False, "minecraft:item.shovel.flatten",
            ["Flint and gravel, fresh from the graves.", "Mind the zombies. They don't mind you."],
            {"type": "skyblock_shop", "params": {"shop": "pat"}}),
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
                           {"type": "skyblock_shop", "params": {"shop": "lonely_philosopher"}}),
    "Scoop": ("scoop", "Scoop", "Merchant", "#FFFFFF", "Shop", skin("sunny"), False, "minecraft:block.snow.break",
              ["Snow up here all year round.", "A good shovel makes short work of it."],
              {"type": "skyblock_shop", "params": {"shop": "scoop"}}),
    "Amelia": ("amelia", "Amelia", "Balloonist", "#FF5555", "Talk", skin("alex", True), True, "minecraft:block.wool.place",
               ["The view from the balloon is worth the climb.", "Christopher keeps the burner going."], None),
    "Christopher": ("christopher", "Christopher", "Balloonist", "#FFAA00", "Talk", skin("ari"), False,
                    "minecraft:block.campfire.crackle", ["Hold on to the ropes!", "One day we'll fly all the way round."],
                    None),
    "Tia the Fairy": ("tia_the_fairy", "Tia the Fairy", "Fairy Souls", "#FF55FF", "Talk", skin("zuri", True), True,
                      "minecraft:block.amethyst_block.chime",
                      ["Fairy Souls are hidden all over the Hub.", "Bring me the souls you find."],
                      {"type": "fairy_exchange", "params": {}}),
    "Fisherman Gerald": ("fisherman_gerald", "Fisherman Gerald", "Fishing", "#55AAFF", "Talk", skin("steve", True), True,
                         "minecraft:entity.fishing_bobber.splash",
                         ["Something big lives in this lake.", "Cast near the dock. They bite more there."], None),
    "Shifty": ("shifty", "Shifty", "Brews", "#AA00AA", "Shop", skin("kai", True), True, "minecraft:entity.witch.drink",
               ["Brews for the brave. Don't ask what's in them.", "Psst. Over here."],
               {"type": "skyblock_shop", "params": {"shop": "shifty"}}),
    "Lucius": ("lucius", "Lucius", "Merchant", "#AAAAAA", "Shop", skin("efe", True), True,
               "minecraft:block.chain.place", ["A ring for the shady ones.", "Every deal has a price."],
               {"type": "skyblock_shop", "params": {"shop": "lucius"}}),
    "Sirius": ("sirius", "Sirius", "Dark Auction", "#5555FF", "Talk", skin("noor", True), True,
               "minecraft:block.beacon.ambient", ["The Dark Auction opens every few days.", "Bring coins. Lots of them."],
               None),
}
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


def main():
    NPCS.mkdir(parents=True, exist_ok=True)
    WARPS.mkdir(parents=True, exist_ok=True)
    made = []
    for spots_file in sorted((HERE / "out").glob("*_spots.json")):
        spots = json.loads(spots_file.read_text(encoding="utf-8"))
        for name, spot in spots.get("npcs", {}).items():
            person = PEOPLE.get(name)
            if person is None:
                print(f"  no NPC set up for {name} ({spots_file.name})")
                continue
            file_id, display, role, color, hint, skin_id, slim, sound, greetings, action = person
            npc = {"name": display, "role": role, "color": color, "hint": hint, "skin": skin_id,
                   "model": "slim" if slim else "wide", "dimension": "skyrpg_hub:hub", "pos": spot["pos"],
                   "yaw": spot.get("yaw", 0.0), "sound": sound, "greetings": greetings}
            if action:
                npc["action"] = action
            (NPCS / f"{file_id}.json").write_text(json.dumps(npc, indent=2) + "\n", encoding="utf-8")
            made.append(display)
    for warp_id, (area_id, spot_name, name, description, icon, order) in HUB_WARPS.items():
        if warp_id in FIXED:
            spot = FIXED[warp_id]
            area_name = spot["area"]
        else:
            spots = json.loads((HERE / "out" / f"{area_id}_spots.json").read_text(encoding="utf-8"))
            spot = spots["warps"][spot_name]
            area_name = spot_name
        warp = {"name": name, "description": description, "icon": icon, "dimension": "skyrpg_hub:hub",
                "pos": spot["pos"], "yaw": spot.get("yaw", 0.0), "area": area_name, "order": order}
        (WARPS / f"{warp_id}.json").write_text(json.dumps(warp, indent=2) + "\n", encoding="utf-8")
    # Every area's Fairy Souls, as the Hub's.
    souls = []
    for spots_file in sorted((HERE / "out").glob("*_spots.json")):
        souls += json.loads(spots_file.read_text(encoding="utf-8")).get("fairy_souls", [])
    SOULS.mkdir(parents=True, exist_ok=True)
    (SOULS / "hub.json").write_text(json.dumps({"island": "Hub", "dimension": "skyrpg_hub:hub", "souls": souls}, indent=1) + "\n",
                                    encoding="utf-8")
    print(f"{len(made)} NPCs: {', '.join(made)}; {len(HUB_WARPS)} warps; {len(souls)} Fairy Souls")


if __name__ == "__main__":
    main()
