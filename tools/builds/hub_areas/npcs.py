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
    # ---- The End
    "Pearl Dealer": ("pearl_dealer", "Pearl Dealer", "Merchant", "#55FF55", "Shop", skin("efe"), False,
                     "minecraft:entity.enderman.ambient", ["End Stone, Obsidian, pearls. All fresh.",
                                                           "A Remnant of the Eye keeps you safe out here."],
                     shop("pearl_dealer")),
    "Gregory": ("gregory", "Gregory", "Merchant", "#AA00AA", "Shop", skin("ari"), False, "minecraft:entity.villager.trade",
                ["Pets go further out here, you know.", "Have a look, take your time."], shop("gregory")),
    "Guber": ("guber", "Guber", "The End", "#AAAAAA", "Talk", skin("steve"), False, "minecraft:entity.enderman.stare",
              ["Don't look them in the eye. Trust me.", "The Zealots carry something special now and then."], None),
    "Lone Adventurer": ("lone_adventurer", "Lone Adventurer", "The End", "#FFAA00", "Talk", skin("kai"), False,
                        "minecraft:block.campfire.crackle",
                        ["Eight Summoning Eyes on the altar, and the dragon comes.",
                         "The more eyes you place, the better your loot."], None),
    "Tyzzo": ("tyzzo", "Tyzzo", "The End", "#55FFFF", "Talk", skin("makena"), False, "minecraft:block.end_portal_frame.fill",
              ["The pillars hum at night. Well, it's always night here.", "Seen any Special Zealots? They carry a frame."],
              None),
    # ---- Dwarven Mines
    **{f"Emissary {who}": (f"emissary_{who.lower()}", f"Emissary {who}", "Commissions", "#55FFFF", "Commissions",
                           skin(look, look in ("alex", "zuri")), look in ("alex", "zuri"), "minecraft:block.anvil.land",
                           [line, "Finished a commission? I'll see you're paid."], {"type": "commissions"})
       for who, look, line in [("Eliza", "alex", "The King sends his regards. And his commissions."),
                               ("Braum", "steve", "Welcome to the Mines! Mind the Goblins."),
                               ("Fraiser", "kai", "The Upper Mines are rich, if you watch for Hoarders."),
                               ("Carlton", "ari", "Rampart's Quarry never runs dry."),
                               ("Ceanna", "zuri", "The Veins go down further than you'd think."),
                               ("Lilith", "noor", "Lava Springs. Warm, isn't it?"),
                               ("Wilson", "efe", "The Royal Mines, finest ore in the kingdom.")]},
    "King": ("king", "King", "Royal Palace", "#FFAA00", "Commissions", skin("steve"), False, "minecraft:block.bell.use",
             ["Today the King is here, and he has work for you.", "Serve the Mines well and they'll serve you."],
             {"type": "commissions"}),
    "Queen Mismyla": ("queen_mismyla", "Queen Mismyla", "Royal Palace", "#FF55FF", "Talk", skin("alex", True), True,
                      "minecraft:block.amethyst_block.chime", ["The King changes every twenty minutes. I don't.",
                                                               "Have you met every King yet?"], None),
    "Bubu": ("bubu", "Bubu", "Merchant", "#55FF55", "Shop", skin("kai"), False, "minecraft:block.anvil.use",
             ["Pickaxes! Proper dwarven pickaxes!", "Mithril needs Breaking Power. Get a better pick."], shop("bubu")),
    "Bulvar": ("bulvar", "Bulvar", "Merchant", "#55FF55", "Shop", skin("efe"), False, "minecraft:entity.villager.trade",
               ["Tier twelve minions. Nobody else sells them.", "Upgrade your minions, upgrade your life."], shop("bulvar")),
    "Banker Broadjaw": ("banker_broadjaw", "Banker Broadjaw", "Bank", "#FFAA00", "Bank", skin("steve"), False,
                        "minecraft:entity.experience_orb.pickup", ["Your coins are safe with the dwarves.",
                                                                   "Deposit, withdraw, it's all the same to me."],
                        {"type": "open_bank"}),
    "Blacksmith": ("dwarven_blacksmith", "Blacksmith", "Reforging", "#FFAA00", "Reforge", skin("efe"), False,
                   "minecraft:block.anvil.use", ["Bring me your gear and some coins.", "Dwarven steel takes a reforge well."],
                   {"type": "reforge"}),
    "Station Master": ("station_master", "Station Master", "Dwarven Mines", "#AAAAAA", "Talk", skin("ari"), False,
                       "minecraft:block.rail.place", ["The rails will run again soon.", "Mind the gap."], None),
    "Old Man Garry": ("old_man_garry", "Old Man Garry", "Dwarven Mines", "#AAAAAA", "Talk", skin("steve"), False,
                      "minecraft:entity.villager.ambient", ["Something's always happening in the Mines.",
                                                            "Goblin Raids, Fallen Stars... keep your eyes open."], None),
    "Don Expresso": ("don_expresso", "Don Expresso", "Dwarven Mines", "#C98A4B", "Talk", skin("noor"), False,
                     "minecraft:block.brewing_stand.brew", ["Mithril and coffee. That's all a dwarf needs.",
                                                            "Tasty Mithril? Never heard of it."], None),
    "Fragilis": ("fragilis", "Fragilis", "Dwarven Mines", "#55FFFF", "Talk", skin("zuri", True), True,
                 "minecraft:block.amethyst_block.hit", ["Every block has a strength. Every tool a Breaking Power.",
                                                        "Mithril needs 4, Titanium 5."], None),
    "Rhys": ("dwarven_rhys", "Rhys", "Dwarven Mines", "#AAAAAA", "Talk", skin("kai"), False, "minecraft:block.stone.break",
             ["There are dwarves! I told you!", "The Heart of the Mountain grows with every commission."], None),
    "Brynmor": ("brynmor", "Brynmor", "Merchant", "#55FF55", "Shop", skin("efe"), False, "minecraft:entity.generic.drink",
                ["A Powder Pie for the road?", "Best tavern under the mountain."], shop("brynmor")),
    **{who: (who.lower(), who, "Dwarven Tavern", "#AAAAAA", "Talk", skin(look), False, "minecraft:entity.generic.drink",
             [line, "*hic*"], None)
       for who, look, line in [("Gimley", "steve", "Another round!"), ("Bomin", "ari", "Did you hear the one about the Goblin?"),
                               ("Hornum", "kai", "My pick's sharper than yours."), ("Sargwyn", "efe", "The King owes me a drink."),
                               ("Tarwen", "noor", "Mithril, mithril, mithril.")]},
    "Forger": ("forger", "Forger", "The Forge", "#FF8A2A", "Forge", skin("efe"), False, "minecraft:block.anvil.use",
               ["What shall we forge today?", "Good things take time. Some take days."], {"type": "forge"}),
    "Fred": ("fred", "Fred", "The Forge", "#AAAAAA", "Talk", skin("steve"), False, "minecraft:block.furnace.fire_crackle",
             ["The Forge never sleeps.", "Give me a ring when you need something."], None),
    "Jotraeline Greatforge": ("jotraeline_greatforge", "Jotraeline Greatforge", "The Forge", "#FF8A2A", "Talk",
                              skin("alex", True), True, "minecraft:block.anvil.land",
                              ["Drills are my speciality.", "Bring me an engine and I'll make it sing."], None),
    **{who: (who.lower(), who, "Cult of the Fallen Star", "#AA00AA", "Talk", skin(look), False,
             "minecraft:block.amethyst_block.resonate", [line, "The stars fall for those who wait."], None)
       for who, look, line in [("Brarnas", "ari", "We await the next Fallen Star."), ("Dalir", "kai", "Starfall is a gift."),
                               ("Thondin", "steve", "Have you seen a star fall?"), ("Silnar", "efe", "Join us, miner.")]},
    "Bylma": ("bylma", "Bylma", "Divan's Gateway", "#AAAAAA", "Talk", skin("zuri", True), True, "minecraft:block.chain.step",
              ["Divan's Gateway. Beyond it, the Ice Wall.", "Don't go down into the Mist alone."], None),
    "Marigold": ("marigold", "Marigold", "Royal Mines", "#FFAA00", "Talk", skin("alex", True), True,
                 "minecraft:block.amethyst_block.chime", ["Gold Essence from a Golden Goblin? I can use that.",
                                                          "The Royal Mines glitter, don't they?"], None),
    "Dalbrek": ("dalbrek", "Dalbrek", "Grand Library", "#AAAAAA", "Talk", skin("steve"), False, "minecraft:item.book.page_turn",
                ["Every dwarf's story is in these books.", "Quiet in the library, please."], None),
    "Tal Ker": ("tal_ker", "Tal Ker", "Grand Library", "#AAAAAA", "Talk", skin("kai"), False, "minecraft:item.book.page_turn",
                ["The Kings rotate. The books don't.", "Looking for something?"], None),
    "Puzzler": ("puzzler", "Puzzler", "Royal Palace", "#FF55FF", "Talk", skin("noor"), False, "minecraft:block.note_block.bell",
                ["A puzzle a day keeps the Goblins away.", "Come back tomorrow for a new one."], None),
    "Castle Guard": ("castle_guard", "Castle Guard", "Palace Bridge", "#AAAAAA", "Talk", skin("steve"), False,
                     "minecraft:item.armor.equip_iron", ["The Royal Palace is just ahead.", "Keep moving, miner."], None),
    "Castle Guard 2": ("castle_guard_2", "Castle Guard", "Palace Bridge", "#AAAAAA", "Talk", skin("ari"), False,
                       "minecraft:item.armor.equip_iron", ["Long live the King. Whoever it is today.", "No loitering."], None),
    "Gwendolyn": ("gwendolyn", "Gwendolyn", "Crystal Hollows", "#FF55FF", "Travel", skin("alex", True), True,
                  "minecraft:entity.minecart.riding", ["The Crystal Hollows lie below. A pass lasts six hours.",
                                                       "Mining XII and HotM IV, then I'll take you down."],
                  {"type": "hollows_pass"}),
    "Ticket Master": ("ticket_master", "Ticket Master", "Minecart Station", "#55FFFF", "Travel", skin("efe"), False,
                      "minecraft:entity.minecart.riding", ["The railroad runs down to the Dwarven Base Camp.",
                                                           "Heart of the Mountain 7 and a Secret Railroad Pass, then all aboard."],
                      {"type": "base_camp"}),
    # ---- Glacite Tunnels (the Dwarven Base Camp)
    "Ticket Master (Base Camp)": ("ticket_master_base_camp", "Ticket Master", "Minecart Station", "#55FFFF", "Travel", skin("efe"),
                                  False, "minecraft:entity.minecart.riding", ["Back up to the Dwarven Mines?", "Mind the Cold out there."],
                                  {"type": "leave_base_camp"}),
    "Emissary Lissandra": ("emissary_lissandra", "Emissary Lissandra", "Commissions", "#55FFFF", "Commissions", skin("alex", True), True,
                           "minecraft:block.anvil.land", ["The Tunnels have work for you.", "Glacite, Tungsten, Umber. Bring them up."],
                           {"type": "commissions"}),
    "Cold Enjoyer": ("cold_enjoyer", "Cold Enjoyer", "Dwarven Base Camp", "#55FFFF", "Talk", skin("kai"), False,
                     "minecraft:block.powder_snow.step", ["The Cold builds the longer you're out there. Cold Resistance slows it.",
                                                          "At 100 Cold you freeze. Come back to the campfire to warm up."], None),
    "Dulin": ("dulin", "Dulin", "Dwarven Base Camp", "#AAAAAA", "Talk", skin("steve"), False, "minecraft:block.stone.break",
              ["Glacite needs Breaking Power 9. So do Tungsten and Umber.", "The deeper tunnels hide gemstones of their own."], None),
    "Dr. Stone": ("dr_stone", "Dr. Stone", "Fossil Research Center", "#FFAA00", "Donate fossils", skin("noor"), False,
                  "minecraft:item.brush.brushing.generic", ["Fossils! Bring me fossils.", "One of each kind, and I'll make it worth your while."],
                  {"type": "fossil_donate"}),
    "Researcher Beryl": ("researcher_beryl", "Researcher Beryl", "Fossil Research Center", "#55FF55", "Trade", skin("zuri", True), True,
                         "minecraft:item.book.page_turn", ["Suspicious Scrap turns up in the mineshafts.",
                                                           "Fossil Essence buys more scrap. I'll salvage what you don't need."],
                         {"type": "fossil_research"}),
    "Fossil Excavator": ("fossil_excavator", "Fossil Excavator", "Fossil Research Center", "#FFAA00", "Excavate", skin("efe"), False,
                         "minecraft:item.brush.brushing.gravel", ["A chisel and a Suspicious Scrap. Let's see what's in there.",
                                                                  "Careful now. Fossils crumble."],
                         {"type": "fossil_excavator"}),
    "Scout Plinius": ("scout_plinius", "Scout Plinius", "Glacite Tunnels", "#AAAAAA", "Talk", skin("ari"), False,
                      "minecraft:block.snow.step", ["Aquamarine, Citrine, Onyx and Peridot grow out in the Tunnels.",
                                                    "Don't stay out too long. The Cold gets everyone."], None),
    # ---- Dungeon Hub
    "Mort": ("mort", "Mort", "The Catacombs", "#FFFF55", "Catacombs", skin("steve"), False, "minecraft:entity.wither_skeleton.ambient",
             ["Ready to go down into the Catacombs?", "Pick your class, then your floor. Bring your party."], {"type": "dungeon_mort"}),
    "Ophelia": ("ophelia", "Ophelia", "Dungeon gear", "#55FFFF", "Shop", skin("zuri", True), True, "minecraft:entity.villager.trade",
                ["Everything you need to survive down there.", "Spirit Leaps, Wither Goggles... take a look."], shop("ophelia")),
    "Malik": ("malik", "Malik", "Dungeon stars", "#FF55FF", "Upgrade", skin("kai"), False, "minecraft:block.brewing_stand.brew",
              ["Essence is the dungeon's true currency.", "Hold a dungeon item and I'll add a star to it."], {"type": "dungeon_malik"}),
    "Croesus": ("croesus", "Croesus", "Dungeon chests", "#FFAA00", "Talk", skin("makena", True), True, "minecraft:entity.experience_orb.pickup",
                ["Chests you leave behind come to me.", "I keep them safe... for a while."], None),
    "Adventurer Saul": ("adventurer_saul", "Adventurer Saul", "Adventurer", "#AAAAAA", "Talk", skin("ari"), False,
                        "minecraft:block.campfire.crackle", ["I've been down there more times than I can count.",
                                                             "Clear every room, find the secrets, and don't die. That's the score."], None),
    # ---- Crystal Hollows
    "Emissary Sisko": ("emissary_sisko", "Emissary Sisko", "Commissions", "#55FFFF", "Commissions", skin("kai"), False,
                       "minecraft:block.anvil.land", ["The Nucleus hums. Bring me the crystals.", "I have work for you down here too."],
                       {"type": "commissions"}),
    "Gemma": ("gemma", "Gemma", "Gemstone Grinder", "#FF55FF", "Gemstones", skin("zuri", True), True, "minecraft:block.amethyst_block.chime",
              ["Ruby, Amethyst, Jade, Amber, Sapphire, Topaz. Collect them all!", "Bring me gear and gemstones, I'll set them."],
              {"type": "gemstone_grinder"}),
    "Geonathan Greatforge": ("geonathan_greatforge", "Geonathan Greatforge", "Crystal Nucleus", "#FF8A2A", "Talk", skin("efe"), False,
                             "minecraft:block.anvil.use", ["My cousin forges in the Mines. I forge here.",
                                                           "Gemstones need the right setting."], None),
    "Kalhuiki Door Guardian": ("kalhuiki_door_guardian", "Kalhuiki Door Guardian", "Jungle Temple", "#55FF55", "Talk", skin("noor"),
                               False, "minecraft:block.wood.hit", ["Only those with a Jungle Key may pass.",
                                                                   "The temple keeps its treasure well."], None),
    "Odawa": ("odawa", "Odawa", "Jungle", "#55FF55", "Talk", skin("ari"), False, "minecraft:entity.parrot.ambient",
              ["Don't hurt the younglings.", "The Sludges grow big down here."], None),
    **{f"Keeper of {gem}": (f"keeper_of_{gem.lower()}", f"Keeper of {gem}", "Mines of Divan", color, "Talk", skin("steve"), False,
                            "minecraft:block.stone.place", [f"I guard Divan's {gem.lower()} part.", "Bring what was lost."], None)
       for gem, color in [("Diamond", "#55FFFF"), ("Gold", "#FFAA00"), ("Emerald", "#55FF55"), ("Lapis", "#5555FF")]},
    "Mines of Divan Forger": ("divan_forger", "Mines of Divan Forger", "Mines of Divan", "#FF8A2A", "Talk", skin("efe"), False,
                              "minecraft:block.anvil.use", ["Divan's drill was a marvel.", "Find the parts and I'll show you."], None),
    "King Yolkar": ("king_yolkar", "King Yolkar", "Goblin Holdout", "#FFAA00", "Talk", skin("kai"), False,
                    "minecraft:entity.zombie.ambient", ["Bring me a Goblin Egg and I'll give you my scent.",
                                                        "The Queen guards the Amber Crystal."], None),
    "Professor Robot": ("professor_robot", "Professor Robot", "Lost Precursor City", "#AAAAAA", "Talk", skin("steve"), False,
                        "minecraft:block.piston.extend", ["Beep. Six parts are missing.", "The Automatons carry them."], None),
    # ---- the Crimson Isle
    "Elle": ("elle", "Elle", "Crimson Isle", "#FFAA00", "Talk", skin("alex", True), True, "minecraft:entity.villager.ambient",
             ["Welcome to the Crimson Isle!", "Two factions, one island. Choose well."], {"type": "crimson_elle"}),
    "Queen Nyx": ("queen_nyx", "Queen Nyx", "Mage Faction", "#AA55FF", "Faction", skin("noor", True), True,
                  "minecraft:block.amethyst_block.chime", ["Knowledge is the only real power.", "The Barbarians understand nothing."],
                  {"type": "crimson_leader", "params": {"faction": "mage"}}),
    "Chief Scorn": ("chief_scorn", "Chief Scorn", "Barbarian Faction", "#FF7733", "Faction", skin("efe"), False,
                    "minecraft:entity.ravager.roar", ["Strength wins wars. Not spells.", "The Mages hide behind their books."],
                    {"type": "crimson_leader", "params": {"faction": "barbarian"}}),
    "Alwin": ("alwin", "Alwin", "Mage Blacksmith", "#AA55FF", "Reforge", skin("ari"), False, "minecraft:block.anvil.use",
              ["Enchanted steel holds a reforge best.", "Bring me your gear and some coins."], {"type": "reforge"}),
    "Igor": ("igor", "Igor", "Barbarian Blacksmith", "#FF7733", "Reforge", skin("steve"), False, "minecraft:block.anvil.use",
             ["Hit it hard. Then hit it again.", "Bring me your gear and some coins."], {"type": "reforge"}),
    "Mage Emissary": ("mage_emissary", "Mage Emissary", "Kuudra Keys", "#AA55FF", "Keys", skin("zuri", True), True,
                      "minecraft:entity.villager.trade", ["Kuudra sleeps beneath the Forgotten Skull.", "Keys open its chests."],
                      {"type": "kuudra_emissary"}),
    "Barbarian Emissary": ("barbarian_emissary", "Barbarian Emissary", "Kuudra Keys", "#FF7733", "Keys", skin("kai"), False,
                           "minecraft:entity.villager.trade", ["Kuudra sleeps beneath the Forgotten Skull.", "Keys open its chests."],
                           {"type": "kuudra_emissary"}),
    "Rollim": ("rollim", "Rollim", "Mage Duelist", "#AA55FF", "Talk", skin("sunny"), False, "minecraft:item.trident.throw",
               ["A duel a day keeps the rust away.", "Come back when you're stronger."], None),
    "Kaus": ("kaus", "Kaus", "Barbarian Fighter", "#FF7733", "Talk", skin("makena", True), True, "minecraft:entity.player.attack.strong",
             ["Fight me! Tomorrow, maybe.", "The ring is always open."], None),
    "Pablo": ("pablo", "Pablo", "Mage", "#AA55FF", "Talk", skin("kai"), False, "minecraft:block.grass.place",
              ["My garden needs mushrooms.", "Red ones, from the Mystic Marsh."], {"type": "crimson_npc", "params": {"name": "Pablo"}}),
    "Avorius": ("avorius", "Avorius", "Mage", "#AA55FF", "Talk", skin("noor"), False, "minecraft:item.bottle.fill",
                ["A Cup of Blood. For research.", "Don't ask what kind."], {"type": "crimson_npc", "params": {"name": "Avorius"}}),
    "Mollim": ("mollim", "Mollim", "Mage", "#AA55FF", "Talk", skin("ari"), False, "minecraft:block.fungus.place",
               ["Mycelium makes the best reagents.", "The Marsh is full of it."], {"type": "crimson_npc", "params": {"name": "Mollim"}}),
    "Pomtair": ("pomtair", "Pomtair", "Barbarian", "#FF7733", "Talk", skin("steve"), False, "minecraft:item.bone_meal.use",
                ["Enchanted Bone Meal. My crops need it.", "Nothing grows in netherrack otherwise."],
                {"type": "crimson_npc", "params": {"name": "Pomtair"}}),
    "Sirih": ("sirih", "Sirih", "Barbarian", "#FF7733", "Talk", skin("zuri", True), True, "minecraft:block.sponge.place",
              ["Sulphur, from the Wasteland and the desert.", "Barbarian powder needs plenty."],
              {"type": "crimson_npc", "params": {"name": "Sirih"}}),
    "Suus": ("suus", "Suus", "Barbarian", "#FF7733", "Talk", skin("sunny", True), True, "minecraft:entity.magma_cube.squish",
             ["Magma Cream keeps the forges hot.", "The Crimson Fields are full of cubes."],
             {"type": "crimson_npc", "params": {"name": "Suus"}}),
    "Vulcan": ("vulcan", "Vulcan", "Crimson Stars", "#FF5555", "Upgrade", skin("efe"), False, "minecraft:block.fire.ambient",
               ["Crimson Essence makes Kuudra's armour stronger.", "Hold a piece and I'll add a star."], {"type": "vulcan"}),
    "Kuudra Gatekeeper": ("kuudra_gatekeeper", "Kuudra Gatekeeper", "Kuudra's Hollow", "#FF5555", "Kuudra", skin("steve"), False,
                          "minecraft:entity.elder_guardian.ambient", ["Kuudra sleeps. For now.", "Only faction members may pass."],
                          {"type": "kuudra"}),
    "Master Tao": ("master_tao", "Master Tao", "The Dojo", "#FFFF55", "Dojo", skin("ari"), False, "minecraft:block.bamboo.place",
                   ["Discipline. Patience. Then strength.", "Seven tests. Master them all for the Black Belt."], {"type": "dojo"}),
    "Odger": ("odger", "Odger", "Trophy Fishing", "#55FFFF", "Trophy Fish", skin("steve"), False, "minecraft:entity.fishing_bobber.splash",
              ["Fish the lava with a lava rod for Trophy Fish.", "Bring them to me: I'll fillet them for Magmafish."],
              {"type": "odger"}),
}
PORHTAL_EYES = {"The Intruder", "The Gill-Man", "The Baba Yaga", "The Bankster", "Gooey", "The Prince", "The 7th Sin"}


def rift(name, file_id, role, colour, hint, skin_id, slim, sound, greetings, action=None):
    PEOPLE[name] = (file_id, re.sub(r" \(.*\)$| \d+$", "", name), role, colour, hint, skin(skin_id, slim), slim, sound, greetings,
                    action or {"type": "rift_npc", "params": {"name": name}})


# ---- the Wizard Tower (Hub) and the Rift
rift("Wizard", "wizard", "The Rift", "#AA55FF", "Rift", "zuri", False, "minecraft:block.amethyst_block.chime",
     ["The Rift is open to those who are ready.", "A Dimensional Infusion lets you step through."], {"type": "rift_wizard"})
rift("Grumblefoot", "grumblefoot", "Travel Scrolls", "#FFAA00", "Shop", "makena", False, "minecraft:entity.villager.trade",
     ["Scrolls for the weary traveller.", "Why walk when you can read?"])
rift("Enigma", "enigma", "Enigma Souls", "#FF55FF", "Souls", "noor", False, "minecraft:block.amethyst_block.resonate",
     ["Souls are scattered all over the Rift.", "Wear my cloak; it grows with every soul you find."], {"type": "enigma"})
for _n in range(1, 5):
    rift(f"Argofay Trafficker {_n}", f"argofay_trafficker_{_n}", "Wyld armour", "#55FF55", "Shop", "kai", False,
         "minecraft:entity.villager.trade", ["Wyld armour, freshly woven.", "Motes only, friend."])
rift("Argofay Threebrothers", "argofay_threebrothers", "Wyld Sword", "#55FF55", "Shop", "efe", False, "minecraft:entity.villager.trade",
     ["My brothers lost their swords in the trees.", "One sword for Motes and a few Shy Crux."])
rift("Argofay Bughunter", "argofay_bughunter", "Odonata", "#55FF55", "Talk", "sunny", True, "minecraft:entity.bee.loop",
     ["Have you seen the Odonata? Beautiful bugs.", "Bottle them for me and I'll reward you."])
rift("Argofay Bugshopper", "argofay_bugshopper", "Bug gear", "#55FF55", "Shop", "ari", True, "minecraft:entity.villager.trade",
     ["Larva Hooks and bottles for the bug-minded.", "Shy Crux is the currency of the woods."])
rift("Sad Jacquelle", "sad_jacquelle", "Lost cat", "#AAAAAA", "Talk", "alex", True, "minecraft:entity.cat.ambient",
     ["Have you seen my cat? His soul is all in pieces...", "Montezuma was such a good boy."])
rift("Inverted Sirius", "inverted_sirius", "Rift Transfer", "#5555FF", "Transfer", "steve", False, "minecraft:block.chest.open",
     ["!thgir tsuj si ti ,gnorw kool t'noD", "Rift-Transferable items pass through me."])
rift("Shifted", "shifted", "Elixirs", "#AA00AA", "Shop", "zuri", True, "minecraft:entity.witch.ambient",
     ["Elixirs! Speed, jump, strength!", "Mind the step, the tavern leans."])
rift("Motes Grubber", "motes_grubber", "Eats Rift items", "#FF55FF", "Sell", "makena", True, "minecraft:entity.generic.eat",
     ["I eat Rift things and pay in Motes.", "Hungry, always hungry."])
rift("Porhtal", "porhtal", "Eyes", "#AA00AA", "Talk", "noor", True, "minecraft:block.portal.ambient",
     ["Calm my seven eyes and I'll carry you across the Rift.", "Each eye sees a different place."])
rift("Tel Kar", "tel_kar", "Wanderer", "#FFFF55", "Talk", "kai", False, "minecraft:entity.villager.ambient",
     ["Time is short here. Listen closely.", "I've walked these woods longer than the trees."])
rift("Kay", "kay", "Lil' Pads", "#55FFFF", "Shop", "sunny", True, "minecraft:entity.frog.ambient",
     ["Lil' Pads! Ten Motes each!", "The channels are rough without one."])
rift("Reed", "reed", "Boatbuilder", "#AA5500", "Talk", "steve", False, "minecraft:entity.boat.paddle_water",
     ["My boat's in pieces all over the Rift.", "Bring me the parts and we'll sail."])
rift("Roy", "roy", "Enigma Soul", "#FF55FF", "Shop", "efe", False, "minecraft:entity.villager.trade",
     ["I found an Enigma Soul in the mud.", "4,000 Motes and some Deadgehog Spines, it's yours."])
rift("Dr. Edwin", "dr_edwin", "Portals", "#55FFFF", "Talk", "ari", False, "minecraft:block.portal.trigger",
     ["The Leech Supreme lurks below.", "I can open a way down, if you're brave."])
rift("Dr. Phear", "dr_phear", "Leeches Lair", "#55FFFF", "Talk", "kai", False, "minecraft:block.portal.travel",
     ["Done down here? I'll take you back up.", "Leeches everywhere. Horrible."])
rift("Mushroom Guy", "mushroom_guy", "Mushrooms", "#FF5555", "Talk", "makena", False, "minecraft:block.fungus.place",
     ["The mushrooms grow when you come close.", "Solve their puzzle and something waits at the top."])
rift("Elise", "elise", "Rift Gallery", "#FF55FF", "Timecharms", "alex", True, "minecraft:block.amethyst_block.chime",
     ["Timecharms keep the Rift from tearing further.", "Bring me one and I'll secure it in the Gallery."])
rift("Shania (Rift)", "shania_rift", "Dreadfarm", "#55FF55", "Talk", "sunny", True, "minecraft:entity.chicken.ambient",
     ["The crops won't grow right here.", "If only someone had a pair of proper Chicken Leggs..."])
rift("Sick Farmer", "sick_farmer", "Dreadfarm", "#AAAAAA", "Talk", "steve", False, "minecraft:entity.villager.hurt",
     ["*cough* Don't eat the berries.", "The Volts came with the storm."])
rift("The Baba Yaga", "the_baba_yaga", "Porhtal eye", "#AA00AA", "Eye", "zuri", True, "minecraft:entity.witch.ambient",
     ["Hee hee hee.", "The eye sees the fields."])
rift("Barry", "barry", "Barry Center", "#FFFF55", "Talk", "steve", False, "minecraft:entity.villager.no",
     ["Please, everyone, calm down!", "My pen... has anyone seen my Montgray Pen?"])
for _n in range(1, 8):
    rift(f"Protester {_n}", f"protester_{_n}", "Protester", "#FF5555", "Talk", ["kai", "efe", "noor", "sunny", "ari", "zuri", "makena"][_n - 1],
         _n % 2 == 0, "minecraft:entity.villager.no", ["Down with Barry!", "We want answers!"])
rift("Cowboy Nick", "cowboy_nick", "Rabbits", "#FFAA00", "Talk", "steve", False, "minecraft:entity.rabbit.ambient",
     ["Howdy, partner. My rabbits are hungry.", "Feed 'em carrots and I'll lend you my Horsezooka."])
rift("Detective Amog", "detective_amog", "Detective", "#AAAAAA", "Talk", "kai", False, "minecraft:entity.villager.ambient",
     ["There's been a murder. Or there will be. Or there was.", "Time runs strangely around the Murder House."])
rift("Seymour", "seymour_rift", "Shoes", "#55FF55", "Shop", "efe", False, "minecraft:entity.villager.trade",
     ["The comfiest sneakers in any dimension.", "Bring me Gunther's Sneakers and plenty of Motes."])
rift("Seraphine", "seraphine_rift", "Detransfigured", "#AA00AA", "Talk", "alex", True, "minecraft:entity.allay.ambient_without_item",
     ["I was... something else, once.", "An Anti-Morph Potion would set me right."])
rift("Cryptosis", "cryptosis", "Enigma Soul", "#FF55FF", "Shop", "zuri", False, "minecraft:block.amethyst_block.resonate",
     ["Souls, for the discerning collector.", "15,000 Motes and ten Scribe Crux."])
rift("Damia", "damia", "Shen's Auctions", "#FFAA00", "Auctions", "makena", True, "minecraft:entity.villager.trade",
     ["Shen's Auctions open once a SkyBlock year.", "Bids in Motes only."])
rift("Taylor", "taylor_rift", "Tailor", "#55FFFF", "Talk", "sunny", True, "minecraft:entity.villager.ambient",
     ["Hems, seams, rifts. I mend them all.", "Mind the needles."])
rift("Lonely Avaeikx", "lonely_avaeikx", "Lonely Terrace", "#AAAAAA", "Talk", "noor", False, "minecraft:entity.villager.ambient",
     ["Would you... sit with me a while?", "Nobody ever stays."])
rift("The Bankster", "the_bankster", "Porhtal eye", "#FFAA00", "Eye", "steve", False, "minecraft:entity.experience_orb.pickup",
     ["Interest compounds. So do eyes.", "The eye watches the vault."])
rift("Gunther", "gunther", "Rift Race", "#FFFF55", "Race", "kai", False, "minecraft:entity.horse.gallop",
     ["Fancy a race? Ten checkpoints round the village.", "Under a minute and I'll have something for you."])
rift("Chef", "chef_rift", "Hot dogs", "#FFAA00", "Talk", "steve", False, "minecraft:entity.generic.eat",
     ["Hot dogs! Fifty in a minute and you're a legend.", "Joey's got the buns."])
rift("Joey McPizza", "joey_mcpizza", "Buns", "#FFAA00", "Talk", "efe", False, "minecraft:entity.generic.eat",
     ["Buns, buns, buns.", "Chef says you look hungry."])
rift("Unhinged Kloon", "unhinged_kloon", "Terminals", "#55FFFF", "Talk", "ari", False, "minecraft:block.note_block.bit",
     ["The terminals! They're all the wrong colour!", "Rainbow. They should be rainbow."])
rift("Kat (Rift)", "kat_rift", "Infested House", "#FF55FF", "Talk", "alex", True, "minecraft:entity.cat.ambient",
     ["The vermin have taken over my house.", "Bring me Silverfish and I'll make you a key."])
rift("Sorcerer Okron", "sorcerer_okron", "Glyphs", "#AA00AA", "Talk", "zuri", False, "minecraft:block.enchantment_table.use",
     ["The glyphs ward the village. Mostly.", "Trace them right and the trinket is yours."])
rift("Alchemist (Rift)", "alchemist_rift", "Potions", "#AA00AA", "Talk", "makena", True, "minecraft:block.brewing_stand.brew",
     ["Potions brewed from things that shouldn't exist.", "Come back when I've got stock."])
rift("Finplex", "finplex", "Zoop the Fish", "#55FFFF", "Talk", "noor", False, "minecraft:entity.fish.swim",
     ["Zoop the Fish. Ninety-nine thousand nine hundred ninety-nine Motes.", "Worth every Mote."])
rift("Plumber Joe", "plumber_joe", "Buckets", "#5555FF", "Talk", "steve", False, "minecraft:item.bucket.fill",
     ["Leaky pipes everywhere in this place.", "A Plumber's Bucket never runs dry."])
rift("Dr. Emmett", "dr_emmett", "The Mirrorverse", "#55FFFF", "Talk", "kai", False, "minecraft:block.portal.ambient",
     ["The Mirrorverse lies through that door.", "Everything in there is backwards. Even me."])
# the Mirrorverse (rift_mirrorverse.py): Dr. Emmett in every room (fast travel back to rooms you've reached), the finale
for _n in range(1, 8):
    rift(f"Dr. Emmett (Mirrorverse {_n})", f"dr_emmett_mirrorverse_{_n}", "The Mirrorverse", "#55FFFF", "Talk", "kai", False,
         "minecraft:block.portal.ambient", ["Look in the mirror. It knows the way.", "I can take you back to any room you've seen."])
rift("Dr. Emmett (Mirrorverse)", "dr_emmett_mirrorverse", "The Mirrorverse", "#55FFFF", "Talk", "kai", False,
     "minecraft:block.portal.ambient", ["You made it through every mirror!", "My reflection runs the shop. Don't ask."])
rift("ttemme .rD", "ttemme_rd", "pohS", "#55FFFF", "Shop", "kai", False, "minecraft:block.portal.ambient",
     ["!tnecsiro... ,olleH", ".sredro ni sesuoh srorrim ruoY"])
rift("Motes Grubber (West Village)", "motes_grubber_west_village", "Eats Rift items", "#FF55FF", "Sell", "makena", True,
     "minecraft:entity.generic.eat", ["I eat Rift things and pay in Motes.", "Hungry, always hungry."])
rift("The Intruder", "the_intruder", "Porhtal eye", "#AA00AA", "Eye", "noor", False, "minecraft:block.portal.ambient",
     ["The eye watches the tower.", "..."])
rift("Rock", "rock_rift", "Living Cave", "#AAAAAA", "Talk", "steve", False, "minecraft:block.stone.hit",
     ["We rocks hear the Living Metal hum.", "The snakes carry hearts of metal. Stop one, mine its tail."])
rift("Chipper McPickaxe", "chipper_mcpickaxe", "Pickaxes", "#55FFFF", "Talk", "kai", False, "minecraft:block.stone.break",
     ["The Anti-Sentient Pickaxe: 24,999 Motes.", "Lapis here grows Living Metal, if you know how to mine it."])
rift("Gooey", "gooey", "Porhtal eye", "#55FF55", "Eye", "sunny", True, "minecraft:entity.slime.squish",
     ["Blorp.", "The eye watches the arena."])
rift("Colosseum Guard", "colosseum_guard", "Colosseum", "#FFAA00", "Talk", "efe", False, "minecraft:item.armor.equip_iron",
     ["Bacte waits in the arena.", "Punch the Globowls into its tentacles."])
rift("Deer", "deer", "Photon Pathway", "#FFFFFF", "Talk", "alex", True, "minecraft:entity.fox.ambient",
     ["You made it across.", "The Chateau's doors open for those who reach me."])
rift("Photon Keeper", "photon_keeper", "Photon Pathway", "#FFFF55", "Talk", "zuri", False, "minecraft:block.beacon.ambient",
     ["The light holds the path together.", "Don't look down."])
rift("Maddox the Slayer (Rift)", "maddox_rift", "Vampire Slayer", "#FF5555", "Slayer", "steve", False, "minecraft:entity.wither.ambient",
     ["Vampires, friend. The Riftstalker Bloodfiend.", "Bring Motes and a stake."])
rift("Fairylosopher", "fairylosopher", "Enigma Soul", "#FF55FF", "Shop", "noor", False, "minecraft:block.amethyst_block.chime",
     ["I sell a soul to those who climb my tower.", "10,000 Motes and 32 Splatter Crux."])
rift("Motes Grubber (Chateau)", "motes_grubber_chateau", "Eats Rift items", "#FF55FF", "Sell", "makena", True, "minecraft:entity.generic.eat",
     ["Vampire leftovers taste best.", "Hungry, always hungry."])
rift("Julian", "julian", "Prisoner", "#AAAAAA", "Talk", "kai", False, "minecraft:entity.villager.hurt",
     ["Get me out of here!", "The guards change shifts... beat them and run."])
rift("The Prince", "the_prince", "Porhtal eye", "#AA0000", "Eye", "efe", False, "minecraft:entity.vex.ambient",
     ["Kneel.", "The eye watches the Chateau."])
rift("Oubliette Guard", "oubliette_guard_npc", "Guard", "#AAAAAA", "Talk", "steve", False, "minecraft:item.armor.equip_chain",
     ["No one leaves the Oubliette.", "Unless they beat us."])
rift("Ubik Von Neumann", "ubik", "Games", "#FFFF55", "Talk", "ari", False, "minecraft:block.note_block.chime",
     ["Three games, three souls.", "Split or steal? Choose wisely."])
rift("Old Timer", "old_timer", "Timite", "#AAAAAA", "Talk", "steve", False, "minecraft:block.bell.use",
     ["Timite grows older or younger depending on how you look at it.", "Bring me the Time Gun and we'll talk."])
rift("Plate", "plate", "Wizardman Trials", "#55FFFF", "Talk", "kai", False, "minecraft:entity.evoker.prepare_attack",
     ["The Trials test speed, wit and might.", "The Bureau opens for those with the Celestial charm."])
for _shen in ("Albedo", "Chaos", "Citrinitas", "Magnum Opus", "Rubedo"):
    rift(f"Shen {_shen}", "shen_" + _shen.lower().replace(" ", "_"), "Cerebral Citadel", "#FFAA00", "Talk", "zuri", False,
         "minecraft:block.enchantment_table.use", ["I am Shen. The richest man in the multiverse.", "Puzzles first. Riches after."])
rift("Madame Eleanor Q. Goldsworth I", "madame_eleanor", "Rose's End", "#FF55FF", "Talk", "alex", True, "minecraft:block.amethyst_block.resonate",
     ["Darling, the outside world needs your skills.", "Bring me proof of your talents."])
rift("Oruo the Almighty", "oruo", "Time Chamber", "#AA00AA", "Talk", "zuri", False, "minecraft:entity.warden.heartbeat",
     ["The Time Chamber drains twice as fast.", "The Sun Gecko waits inside."])
rift("The 7th Sin", "the_7th_sin", "Porhtal eye", "#AA0000", "Eye", "noor", False, "minecraft:entity.vex.charge",
     ["Greed. Pride. Time.", "The eye watches the summit."])
rift("Detective Amog (Mountaintop)", "detective_amog_mountaintop", "Detective", "#AAAAAA", "Talk", "kai", False,
     "minecraft:entity.villager.ambient", ["The case continues up here.", "Someone dropped a wallet. Or will."])
rift("The Gill-Man", "the_gill_man", "Porhtal eye", "#00AAAA", "Eye", "zuri", False, "minecraft:entity.guardian.ambient",
     ["Blub.", "The eye watches the lagoon."])


for _town in ("Dragontail", "Scarleton"):
    _id = _town.lower()
    PEOPLE[f"Banker ({_town})"] = (f"banker_{_id}", "Banker", _town, "#FFAA00", "Bank", skin("steve"), False,
                                   "minecraft:entity.experience_orb.pickup", ["Your coins are safe here.", "Deposit, withdraw, any time."],
                                   {"type": "open_bank"})
    PEOPLE[f"Auction Master ({_town})"] = (f"auction_master_{_id}", "Auction Master", _town, "#FFFF55", "Auctions", skin("noor"), False,
                                           "minecraft:entity.villager.trade", ["Buy it now, or bid for it.", "Everything sells, eventually."],
                                           {"type": "open_auction"})
    PEOPLE[f"Bazaar ({_town})"] = (f"bazaar_{_id}", "Bazaar Agent", _town, "#FFAA00", "Bazaar", skin("kai"), False,
                                   "minecraft:entity.villager.trade", ["Instant buys, instant sells.", "Orders fill while you're away."],
                                   {"type": "open_bazaar"})
LIFT_OPERATOR = ("Lift Operator", "Deep Caverns", "#FFFF55", "Talk", skin("steve"), False, "minecraft:block.chain.step",
                 ["Going down? Hold on to the rail.", "The lift stops at every layer you've been to."],
                 {"type": "lift", "params": {"lift": "deep_caverns"}})

# The Hypixel warps inside the Hub: /warp castle, /warp crypt, /warp da.
HUB_WARPS = {
    "castle": ("ruins", "Ruins", "Castle", "The Hub's ancient ruins and their wolves", "minecraft:mossy_stone_bricks", 40),
    "crypt": ("coal_mine", None, "Crypt", "The Hub Crypts, under the Coal Mine", "minecraft:skeleton_skull", 41),
    "da": ("wilderness", None, "Dark Auction", "Sirius's Dark Auction, in the Wilderness", "minecraft:wither_skeleton_skull", 42),
    "wizard": ("wizard_tower", "Wizard Tower", "Wizard Tower", "The Wizard's tower: the way into the Rift", "minecraft:amethyst_cluster", 43),
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
    "dungeon_hub": ("dungeon_hub", "Dungeon Hub", "Dungeon Hub", "Mort and the Catacombs", "minecraft:wither_skeleton_skull", 30),
    "spider": ("spiders_den", "Spider's Den", "Spider's Den", "The Spider Mound and its spiders", "minecraft:spider_eye", 25),
    "end": ("the_end", "The End", "The End", "Endermen, Zealots and the Dragon's Nest", "minecraft:ender_eye", 26),
    "mines": ("mines_village", "Dwarven Mines", "Dwarven Mines", "Mithril, Titanium, Commissions and the Heart of the Mountain",
              "minecraft:iron_pickaxe", 27),
    "forge": ("mines_center", "The Forge", "The Forge", "The Dwarven Mines' Forge", "minecraft:anvil", 28),
    "base": ("glacite_base_camp", "base_camp", "Dwarven Base Camp", "The Glacite Tunnels: Glacite, Tungsten, Umber and the Cold",
             "minecraft:packed_ice", 29),
    "crimson": ("crimson_center", "Crimson Isle", "Crimson Isle", "The Stronghold, the Crimson Fields and the Blazing Volcano",
                "minecraft:magma_cream", 31),
    "dragontail": ("crimson_west", "Dragontail", "Dragontail", "The Barbarians' town on the Crimson Isle", "minecraft:red_nether_bricks", 32),
    "scarleton": ("crimson_east", "Scarleton", "Scarleton", "The Mages' town on the Crimson Isle", "minecraft:amethyst_block", 33),
    "smoldering": ("crimson_west", "Smoldering Tomb", "Smoldering Tomb", "The Crimson Isle's crypt of Wither Spectres",
                   "minecraft:wither_skeleton_skull", 34),
    "kuudra": ("crimson_west", "Kuudra Passage", "Kuudra Passage", "The Forgotten Skull, Kuudra's lair", "minecraft:bone_block", 35),
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
            if name.startswith("spawn:") or name.startswith("enigma:"):
                continue
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

    # Spots the mod spawns things at ("spawn:Bladesoul" NPC spots) and the island's warps, per island.
    spawn_dir = DATA / "spawn_spots"
    spawn_dir.mkdir(parents=True, exist_ok=True)
    for old in spawn_dir.glob("*.json"):
        old.unlink()
    spawn_islands = {}
    for spots in all_spots.values():
        found = {name[len("spawn:"):] if name.startswith("spawn:") else name: {"pos": spot["pos"], "yaw": spot.get("yaw", 0.0)}
                 for name, spot in spots.get("npcs", {}).items() if name.startswith("spawn:") or name.startswith("enigma:")}
        # Porhtal's eyes are NPCs whose places the mod also needs (fast travel to them).
        found.update({"eye:" + name: {"pos": spot["pos"], "yaw": spot.get("yaw", 0.0)}
                      for name, spot in spots.get("npcs", {}).items() if name in PORHTAL_EYES})
        if not found:
            continue
        entry = spawn_islands.setdefault(spots.get("island", "Hub"), {"dimension": spots.get("dimension", HUB), "spots": {}})
        entry["spots"].update(found)
    for spots in all_spots.values():
        island = spots.get("island", "Hub")
        if island in spawn_islands:
            for name, warp in spots.get("warps", {}).items():
                spawn_islands[island]["spots"]["warp:" + name] = {"pos": warp["pos"], "yaw": warp.get("yaw", 0.0)}
    for island, entry in spawn_islands.items():
        write(spawn_dir / f"{slug(island)}.json", entry)

    souls = ", ".join(f"{island} {len(e['souls'])}" for island, e in islands.items())
    print(f"{len(made)} NPCs; {warps} warps; {pads} launch pads; {len(stops)} lift stops; Fairy Souls: {souls}")


if __name__ == "__main__":
    main()
