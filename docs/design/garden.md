# The Garden

Hypixel's Garden (Phase 4). Research: `docs/design/research/garden.md`. What's built and what's decided.

## The island (`garden/Garden.java`, `GardenRules.java`)

- Dimension `skyrpg_skyblock:garden` (the private islands' type; flat grass with its top at y 64). Each profile's
  Garden is in its island's cell (`Islands.center(profile.island())`), 480 x 480: a 5 x 5 grid of 96-block plots.
- The Barn plot (the middle) is a template, `structure/garden/barn.nbt` (`tools/builds/garden/barn.py` on
  `garden_kit.py`, helper-built): the Desk (a cartography table), the Composter machine, SkyMart, Phillip's shed, the
  visitor yard, the arrival plaza. Placed on the first visit (`/warp garden`, SkyBlock Level 5), with oak fences round
  the 24 locked plots.
- Plots unlock in Hypixel's order for Hypixel's costs (Compost, then Compost Bundles = Enchanted Compost). Decided: the
  wiki's level groups as gates (1-4 I, 5-8 III, 9-12 V, 13-24 VII). A fresh plot is overgrown (grass, flowers, mossy
  rocks, trees); decided: clearing it isn't tracked. +3 Farming Fortune per plot applies in the Garden; 5 SkyBlock XP.
- Members build and break only on unlocked plots; the Barn plot is fixed; visitors (other players) can't change
  anything. Leaving the 480 x 480 sends you back to the arrival. Costs take from the inventory, then the sacks
  (`Sacks.countAnywhere` / `takeAnywhere`).
- State per profile in `GardenData` (saved with the overworld): plots, Garden XP, Copper, crops harvested, the
  Composter, visitors.

## Levels and milestones

- Garden XP table: NEU's (`LEVEL_XP`), I-XV, 10 SkyBlock XP each.
- Crop milestones: NEU's thresholds for the ten classic crops (Sunflower, Moonflower and Wild Rose left out: no blocks
  for them yet). A harvested crop counts Hypixel's base drop times (1 + Farming Fortune / 100). Decided: each milestone
  level gives 10 x level Garden XP and 1 SkyBlock XP (Hypixel's per-level rewards weren't found).

## Crop upgrades and crop Fortune

- NEU's `crop_upgrades`: 9 tiers (5 ... 4,000 Copper), +5 of the crop's own Fortune stat a tier, 1 SkyBlock XP each;
  decided: no Garden-level gate (the wiki's gates came through garbled). They apply everywhere (a stat source).
- Crop Fortune stats (`wheat_fortune` ... `nether_stalk_fortune`, also from the Turbo enchantments) now add to Farming
  Fortune on that crop's blocks (`stats/Fortune.java`) and in milestone/contest counts.

## Visitors (`Visitors.java`)

Decided where Hypixel's numbers weren't found: one every 15 minutes while someone in the profile is online, up to 5;
rarity Uncommon 60%, Rare 30%, Legendary 8%, Mythic 2% (Garden XIV+); names from NEU's roster. Requests: one crop (two
from Garden V, 50%) among the crops your Garden level has opened (Wheat at I ... Nether Wart at X), 200 x (1 + level)
raw crops x rarity size (1 / 1.5 / 3 / 6) +-25%, as enchanted crops from 320. Rewards: Copper = raw / 1000 x 2 x (1, 1,
5, 5), 2 to 100; Garden XP = raw / 40, at least 30; Farming XP = 1.5 x raw; 1 SkyBlock XP for each new visitor served.
Visitors, SkyMart and Phillip are Mannequins kept while someone is in that Garden.

## The Composter (`Composter.java`) and SkyMart

- 4,000 Organic Matter + 2,000 Fuel -> 1 Compost every 10 minutes, worked out lazily (offline too). Fuel values from
  NEU (Biofuel 3,000, Oil Barrel and Volta 10,000). Decided: Organic Matter per crop (Wheat 1 ... Melon Slice 0.2,
  enchanted x160), base caps 40,000 Organic Matter and 100,000 Fuel.
- Upgrades: NEU's Copper table and per-branch items (25 tiers); the Cropie/Squash/Fermento extras on later tiers are left
  out for now.
- SkyMart: NEU's `skymart.json` prices, the items we have.

## Pests (`Pests.java`)

- Hypixel's ten pests, one per crop, each from its Garden level (V for the first five, Earthworm VI ... Beetle X). A
  broken Garden crop has a 0.2% chance (x (1 + Bonus Pest Chance / 100)) to bring its pest once the profile's 5-minute
  pest cooldown is over (Pest Repellent / MAX: /2 / /4 for an hour). Decided: no Sprayonator sprays yet.
- Pests are NoAI glowing endermites/silverfish drifting over the plots, 600 health; only a vacuum hurts them (the
  vacuum ability: 0.2 x the item's Damage, five uses a second, at the pest nearest your aim within 10 blocks and 45
  degrees). Reward: 1,000 coins, 1,500 Farming XP, 1 enchanted crop + 1 per 50 Farming Fortune.
- Farming Fortune penalty in the Garden with 4+ alive (one more allowed per 100 Bonus Pest Chance): -5 / 15 / 30 / 50 /
  75%.
- Pests killed are Pesthunter Phillip's currency (`GardenData.pests`): the Pesthunter Badge 25, the four Pesthunter
  equipment pieces 40 each (decided: our prices).

## Jacob's Farming Contests and Anita (`Contests.java`)

- On the calendar's "Jacob's Farming Contest" (every third SkyBlock day, 20 minutes). Three crops from a seed of the
  contest's start, so Jacob's menu can show the next contest's crops. Farming X to enter; the first contest crop you
  harvest on your Garden is your entry; the score counts crops as harvested (Fortune included).
- Brackets: Hypixel's standing shares (Diamond 2%, Platinum 5%, Gold 10%, Silver 30%, Bronze 60%) with 10+ in a crop;
  decided for small servers: also a score floor per bracket (100 / 5,000 / 15,000 / 40,000 / 70,000 / 100,000 Wheat;
  times the crop's base drop), the better of the two counts. Tickets 1 / 10 / 15 / 25 / 30 / 35; medals Bronze, Silver,
  Gold (Platinum adds a Bronze, Diamond a Silver).
- Rewards wait in the `jacob_medals` attachment until claimed at Jacob. Decided: a contest running when the server
  stops is dropped (scores live in memory).
- Anita: NEU's prices for the ten items we have, and medal trades down (1 Gold -> 2 Silver, 1 Silver -> 2 Bronze).
- Sidebar: time left, then the three crops, or your crop and "<Bracket> with <score>" (Hypixel's bracket colours).
- Admin: `/sbgarden contest start|end|score <n>`, `/sbgarden pest`, `/sbgarden xp <n>`, `/sbgarden copper <n>`.

## Still to build

The Sprayonator and sprays, Garden Chips, the Greenhouse, Barn skins, plot presets.
