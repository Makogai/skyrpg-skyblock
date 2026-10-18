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

## Still to build

Pests (spawning, the Farming Fortune penalty, vacuums, the Sprayonator, Phillip's shop), Jacob's Farming Contests and
Anita, crop upgrades, Garden Chips, the Greenhouse, Barn skins, plot presets.
