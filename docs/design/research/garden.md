# The Garden research

Source note: numbers come from the NEU repo (`tools/skyblock/cache/neu-repo.zip`, MIT; constants/garden.json, constants/skymart.json, constants/bestiary.json, items/*.json lore and NPC shop recipes) and the community mirror https://hypixelskyblock.minecraft.wiki/w/<Page> (pages fetched through a lossy summarizer: The_Garden, Garden_Level, Plots, Visitors, Garden_Visitors, Composter, Crop_Milestones, Garden_Milestones, Pests, Jacob's_Farming_Contest, Anita, Copper, Greenhouse, Crop_Fortune; Desk_(Garden) 404'd, the fandom backup returned 402).
Where a line comes from general game knowledge rather than a fetched page or the NEU data it is marked (?). Anything marked (?) must be verified before being coded as a hard number.
NEU data is current-meta: the Garden now has 13 crops (adds Sunflower, Moonflower, Wild Rose), a Greenhouse with mutations, Garden Chips and a Crop Analyzer, so older "10 crop" descriptions are out of date.
Format follows docs/design/research/dungeons.md.

---

## 1. Getting the Garden and Garden levels

Sources: wiki The_Garden, Garden_Level, Desk (disambiguation only); NEU garden.json `garden_exp`

| Rule | Value |
|---|---|
| Requirement | SkyBlock Level 5; Garden I is unlocked automatically on the first visit after that |
| Reached by | Garden portal / warp (?); the Barn is the centre of the island |
| Island | 5x5 grid of plots around the Barn, 24 usable plots, each 96x96 blocks (about 480x480 in total) |
| Desk | menu in the Barn (`/desk`): plots, crop upgrades, SkyMart, crop milestones, visitors, Garden stats |
| Levels menu | `/gardenlevels` |
| Max Garden level | 15 (Roman numerals I-XV) |
| Garden XP sources | crop milestones and accepting visitor offers (wiki) |
| SkyBlock XP per Garden level | +10 each level (wiki) |
| Crop Growth | +10 Crop Growth per Garden level from level 2 (wiki); each 1 Crop Growth = +1% growth speed, max +210 (wiki) (?: the pages disagree, one table shows +140 at level 15) |
| Dry soil | 40% growth debuff (wiki) |

Garden XP per level (NEU `garden_exp`, index = level; level 1 costs 0). Cumulative total at level 15 = 60,120, which matches the wiki's 60,120. (The wiki summary showed "47" for level 1 and a total of 60,187; treat the NEU table as authoritative.)

| Level | XP to reach | Cumulative | Unlocks (wiki Garden_Level / Pests / Visitors; crop-unlock levels marked (?) because the summarizer mixed rows) |
|---|---|---|---|
| 1 | 0 | 0 | Wheat |
| 2 | 70 | 70 | Carrot (?) |
| 3 | 70 | 140 | Potato (?); Medieval barn skin |
| 4 | 140 | 280 | Pumpkin (?) |
| 5 | 240 | 520 | Sugar Cane (?); 5 visitors; Pests (Fly, Cricket, Locust, Rat, Mosquito, Field Mouse, Lunar Moth); Sprayonator and vacuums need Garden 5 |
| 6 | 600 | 1,120 | Melon (?); Earthworm pest; Sunny barn skin |
| 7 | 1,500 | 2,620 | Cactus (?); Mite pest; Greenhouse (with Carpenter visitor) |
| 8 | 2,000 | 4,620 | Cocoa Beans (?); Moth pest; Red barn skin |
| 9 | 2,500 | 7,120 | Mushroom (?); Slug pest |
| 10 | 3,000 | 10,120 | Nether Wart (?); Beetle pest |
| 11 | 10,000 | 20,120 | Sunflower, Moonflower (?); Dragonfly, Firefly pests; Cabin barn skin |
| 12 | 10,000 | 30,120 | Wild Rose (?); Praying Mantis pest |
| 13 | 10,000 | 40,120 | - |
| 14 | 10,000 | 50,120 | Taylor (mythic visitor) needs XIV |
| 15 | 10,000 | 60,120 | Mansion Heights barn skin; Spaceman and Ludleth visitors need XV |

Crop upgrade tier caps rise with level, Tier I at level 2 up to Tier IX at 15; wiki listed L2 I, L4 II, L6 III, L8 IV, L10 V, L12 VI, L13 VII, L14 VIII, L15 IX (?: garbled in the fetch).

## 2. Plots

Sources: wiki Plots, The_Garden; NEU garden.json `plots`, `plot_costs`, `barn`; items PLOT_ERASER, GARDEN_SCYTHE, SAM_SCYTHE

| Rule | Value |
|---|---|
| Plots | 24 in a 5x5 grid, Barn in the centre cell (2,2); each 96x96 blocks |
| Unlock | sequential, plot 1 to 24, paid at the Desk with Compost then Compost Bundles |
| Per plot | +3 Farming Fortune and +5 SkyBlock XP on unlock (max +72 FF) |
| Cleaning | a fresh plot is covered in dirt, stone, grass, flowers, wood and leaves; clean by breaking them; progress is tracked against a 70% threshold, after which leaves decay (wiki) |
| Tools | Plot Eraser (RARE consumable, instantly cleans the plot you stand in), Sam's Scythe (3x3, 2,000 coins at SkyMart), Garden Scythe (5x5, 20 Copper) |
| Barriers | locked plots are fenced off (?) |
| Presets | Super Flat, Wheat, Carrot, Potato, Pumpkin, Sugar Cane, Melon, Cactus, Cocoa Beans, Mushroom, Nether Wart, Sunflower, Wild Rose (wiki) |

Unlock order, grid position (x = column, y = row, 0..4 as in NEU) and cost (NEU `plots` + `plot_costs`; CB = Compost Bundle = ENCHANTED_COMPOST):

| Plot # | Grid (x,y) | Tier | Cost |
|---|---|---|---|
| 1 | (2,1) | beginner | 1 Compost |
| 2 | (1,2) | beginner | 2 Compost |
| 3 | (3,2) | beginner | 4 Compost |
| 4 | (2,3) | beginner | 8 Compost |
| 5 | (1,1) | intermediate | 16 Compost |
| 6 | (3,1) | intermediate | 24 Compost |
| 7 | (1,3) | intermediate | 32 Compost |
| 8 | (3,3) | intermediate | 48 Compost |
| 9 | (2,0) | advanced | 64 Compost |
| 10 | (0,2) | advanced | 96 Compost |
| 11 | (4,2) | advanced | 128 Compost |
| 12 | (2,4) | advanced | 1 CB |
| 13 | (1,0) | advanced | 1 CB |
| 14 | (3,0) | advanced | 2 CB |
| 15 | (0,1) | advanced | 2 CB |
| 16 | (4,1) | advanced | 3 CB |
| 17 | (0,3) | advanced | 3 CB |
| 18 | (4,3) | advanced | 4 CB |
| 19 | (1,4) | advanced | 5 CB |
| 20 | (3,4) | advanced | 7 CB |
| 21 | (0,0) | expert | 8 CB |
| 22 | (4,0) | expert | 10 CB |
| 23 | (0,4) | expert | 12 CB |
| 24 | (4,4) | expert | 15 CB |

Cumulative: plots 1-4 = 15 Compost, 5-8 = +120 (135 total), 9-11 = +288 Compost, plots 12-24 = 73 CB in total (computed). The wiki quotes 583 Compost for plot 12 and 12,103 Compost for plot 24, which implies 1 CB = 160 Compost (?).
Wiki grouping by Garden level: plots 1-4 (Garden I), 5-8 (III), 9-12 (V), 13-24 (VII) (?: level gating from the summarizer).

Barn skins (NEU `barn`; wiki unlock levels L3 Medieval, L6 Sunny, L8 Red, L11 Cabin, L15 Mansion Heights; Default free):
- Free/level: Default, Red, Medieval, Sunny, Cabin, Mansion Heights.
- SkyMart Copper: Trading Post 1,000; Autumn Hut 2,000; Bamboo 7,500; Hive 10,000; Castle 15,000.
- SkyMart Gems: Cube, Cozy Cottage, Windmill, Tavern 250; Melon, Lucky 950.
- Pests: Pesthunter's Lair 1,500 Pests at Pesthunter Phillip.
- Others exist as items (source not verified (?)): Frog, Jerry, Pinwheel House, Mushroom, The End, Town Hall, Winter Homestead, Enchanted Nook, Chocolate Factory, Sand Castle, Winter Barn, Beach Ball, Main Street, Spring, 6th Anniversary, Harvest, Gingerbread Village, Safari, Yacht, Lovely Picnic, Pina Colada, Fast Food, Flower Pot, Beautifall Cabin, Beachside Resort, Butterfly, Turkey.

## 3. Crops and crop milestones

Sources: NEU garden.json `crop_milestones`, `crop_upgrades`; wiki Garden_Level, Pests, Crop_Fortune

13 crops (NEU) with their pest; "Garden level" is where that pest unlocks (wiki Pests). The task brief listed 10 crops, NEU has 13.

| Crop | Pest | Garden level (pest unlock) |
|---|---|---|
| Wheat | Fly | 5 |
| Carrot | Cricket | 5 |
| Potato | Locust | 5 |
| Pumpkin | Rat | 5 |
| Sugar Cane | Mosquito | 5 |
| Melon Slice | Earthworm | 6 |
| Cactus | Mite | 7 |
| Cocoa Beans | Moth | 8 |
| Mushroom | Slug | 9 |
| Nether Wart | Beetle | 10 |
| Sunflower | Dragonfly | 11 |
| Moonflower | Firefly | 11 |
| Wild Rose | Praying Mantis | 12 |

Crop milestones: 46 levels per crop in NEU, thresholds are total crops harvested. Crops share a few base curves. Levels 1-25 (every curve is flat at its level-25 value for levels 26-46):

| Level | WHEAT/PUMPKIN/MUSHROOM/SUNFLOWER | CARROT/POTATO | MELON | SUGAR_CANE/CACTUS/WILD_ROSE | COCOA_BEANS/NETHER_WART | MOONFLOWER |
|---|---|---|---|---|---|---|
| 1 | 30 | 100 | 150 | 60 | 90 | 30 |
| 2 | 50 | 150 | 250 | 100 | 150 | 50 |
| 3 | 80 | 250 | 400 | 160 | 240 | 80 |
| 4 | 200 | 500 | 1,000 | 400 | 600 | 200 |
| 5 | 350 | 1,000 | 1,750 | 700 | 1,050 | 700 |
| 6 | 700 | 2,000 | 3,500 | 1,400 | 2,100 | 700 |
| 7 | 1,500 | 4,500 | 7,500 | 3,000 | 4,500 | 1,500 |
| 8 | 2,500 | 9,000 | 12,500 | 5,000 | 7,500 | 2,500 |
| 9 | 3,500 | 12,000 | 17,500 | 7,000 | 10,500 | 3,500 |
| 10 | 5,000 | 15,000 | 25,000 | 10,000 | 15,000 | 5,000 |
| 11 | 6,500 | 20,000 | 32,500 | 13,000 | 19,500 | 6,500 |
| 12 | 8,000 | 25,000 | 40,000 | 16,000 | 24,000 | 8,000 |
| 13 | 10,000 | 35,000 | 50,000 | 20,000 | 30,000 | 10,000 |
| 14 | 20,000 | 70,000 | 100,000 | 40,000 | 60,000 | 20,000 |
| 15 | 35,000 | 120,000 | 175,000 | 70,000 | 105,000 | 35,000 |
| 16 | 50,000 | 180,000 | 250,000 | 100,000 | 150,000 | 50,000 |
| 17 | 75,000 | 250,000 | 375,000 | 150,000 | 225,000 | 75,000 |
| 18 | 100,000 | 350,000 | 500,000 | 200,000 | 300,000 | 100,000 |
| 19 | 175,000 | 600,000 | 875,000 | 350,000 | 525,000 | 175,000 |
| 20 | 250,000 | 850,000 | 1,250,000 | 500,000 | 750,000 | 250,000 |
| 21 | 375,000 | 1,100,000 | 1,875,000 | 750,000 | 1,125,000 | 375,000 |
| 22 | 400,000 | 1,400,000 | 2,000,000 | 800,000 | 1,200,000 | 400,000 |
| 23 | 450,000 | 1,800,000 | 2,250,000 | 900,000 | 1,350,000 | 450,000 |
| 24 | 650,000 | 2,200,000 | 3,250,000 | 1,300,000 | 1,950,000 | 650,000 |
| 25 | 800,000 | 2,600,000 | 4,000,000 | 1,600,000 | 2,400,000 | 800,000 |

Moonflower differs from Sunflower only at level 5 (700 vs 350), probably a NEU typo (?).
Reward per milestone level: the wiki says each level grants Garden XP, SkyBlock XP and Farming XP, but per-level values were not on the fetched pages (?). Wiki Crop_Fortune says milestones scale the Dedication enchant ("multiplied by the Garden milestone for that crop, up to 92") (?: 92 conflicts with the 46 NEU levels).

Crop upgrades (Desk, paid in Copper): NEU `crop_upgrades` = 9 tiers costing 5, 10, 20, 50, 100, 500, 1,000, 2,000, 4,000 Copper = 7,685 per crop. Each tier +5 crop Fortune (+45 at Tier IX) and +1 SkyBlock XP (wiki). All 13 crops: 99,905 Copper (wiki Copper).

Garden Chips (NEU items, redeem duplicates to raise chip level): Cropshot +3 Farming Fortune per level; Sowledge +1 Farming Wisdom; Mechamind +1.5% farming tool XP; Overdrive +5 crop Fortune for the active crop in a Contest; Quickdraw visitors arrive 1.5% sooner; Rarefinder +1.5 Overbloom; Synthesis +1% Crop Analyzer Copper; Evergreen +2% base crops in the Greenhouse; Hypercharge temporary Fortune buffs 3% stronger; Vermin Vaporizer +3 Bonus Pest Chance. Each is per chip level.

Farming tools sold at SkyMart for 250 Copper each (Mk. I, need Farming 10): Euclid's Wheat/Carrot/Potato/Cane/Warts/Sunflower/Wild Rose sickles (THEORETICAL_HOE_*), Pumpkin Dicer, Melon Dicer, Cactus Knife, Coco Chopper, Fungi Cutter.

## 4. Visitors

Sources: wiki Visitors, Garden_Visitors, Copper, The_Garden; NEU garden.json `visitors`, items COPPER_TALISMAN, LOTUS_BRACELET

| Rule | Value |
|---|---|
| Max at once | 5 in the Garden; a 6th queues hidden until one is accepted or declined (wiki) |
| Arrival timing | not on any fetched page (?); Quickdraw chip cuts arrival time 1.5% per level (NEU) and arrival is triggered by harvesting (NEU chip text: "when harvesting crops") |
| Offer | each visitor has one offer: items for Copper, Garden XP, Farming XP and sometimes Bits or a unique item; accept or decline; visitors repeat (wiki) |
| Requirements | most need a minimum Garden level (I to XV) plus extras (NPC quest, reputation, event) (wiki) |
| Rarities | Uncommon, Rare, Legendary, Mythic, Special (NEU); spawn percentages not found (?) |
| Copper formula | `Copper = BaseItems x 2000 x ItemQuantityMultiplier x RarityMultiplier x 1.3`; rarity multiplier 1 for Uncommon/Rare, 5 for Legendary/Mythic/Special (wiki Copper) (?: verify the 2000 constant) |
| Copper boosters | Rose Dragon pet +10.2-20%, Synthesis Chip 1-40% (Crop Analyzer only), Mayor Finnegan +10%, Ladybug shard +1-10% |
| Garden XP per offer | base 30-4,080 (wiki) |
| Farming XP per offer | base 500-614,400 (wiki) |
| Copper per offer | base 2-100 (wiki; examples Jerry 5, Spaceman 100) |
| Copper Talisman | RARE and above visitors have a 4% higher chance to arrive (NEU) |
| Peony (Lotus) set | Salesperson piece bonus: completing offers grants Farming Fortune (NEU) |
| Unique rewards | Hypercharge Chip 3%, Quickdraw Chip 3%, Flowering Bouquet 2%, Overgrown Grass 0.1%, Green Bandana 0.4% (wiki The_Garden) |
| Special | "Charming" gives exclusive rewards; seasonal visitors (Season of Jerry, Carnival, Spooky Festival); Mayor visitors when that mayor is elected |

Visitor milestones (wiki Visitors, per unique visitor served; the table was truncated): tier 1 at 1 visitor +500 Farming XP, +30 Garden XP, +3 SkyBlock XP; tier 5 at 5 visitors +1,500 Farming XP, +90 Garden XP, +6 SkyBlock XP; tier 16 at 140 visitors +125,500 Farming XP, +4,080 Garden XP, +48 SkyBlock XP (?: which rows are cumulative); tier 26 at 240 unique visitors +38,000 Farming XP, +780 Garden XP, +3 SkyBlock XP. Spaceman needs 75 unique visitors served, Ludleth 100.

Notable visitors (wiki):
- Jerry (Legendary): Bread on first visit, +500 Farming XP, 5 Copper at Garden I.
- Spaceman (Special): Garden XV and 75 unique visitors, any item except Seeds, Space Helmet + 100,000 Farming XP + 100 Copper.
- Taylor (Mythic): Garden XIV, Deepfried / Salted Sunflower Seeds / Enchanted Baked Potato variants, 614.4k-1.5M Farming XP, Greenhouse skins.
- Ludleth (Mythic): Garden XV and 100 unique, Compacted Sunflower.
- Carpenter (Rare): Garden VII, Melon Slice or Cocoa Beans, unlocks the Greenhouse.

Visitor roster and rarity (NEU `visitors`, 131 names). Legendary: Baker, Beth, Bruuh, Carrot King, Chief Scorn, Dante Goon, Disguised Rats, Dulin Tunnels, Elle, Hoppity, Jerry, Madame Eleanor, Mayors (Aatrox, Cole, Diana, Diaz, Finnegan, Foxy, Marina, Paul), Queen Nyx, Seraphine, Sirius, Tal Ker, Vargul, Vincent. Mythic: Ludleth, Maeve, Ravenous Rhino, Taylor. Special: Spaceman. Rare: Archaeologist, Artist, Bartender, Bear Pete, Bednom, Carpenter, Chantelle, Cold Enjoyer, Dalbrek, Dragon Ritualist, Duncan, Erihann, Farm Merchant, Fragilis, Frozen Alex, Gemma, Gold Forger, Grandma Wolf, Gwendolyn, Hendrik, Iron Forger, Lazy Miner, Lumina, Mage Alchemist, Marigold, Master Tactician, Moby, Old Man Garry, Ophelia, Pet Trainer, Puzzler, Queen Mismyla, Romero, Rusty, Seymour, Sherry, Shifty, Snowmaker, Spider Tamer, St Jerry, Tammy, Tia, Tomioka, Trinity, Tyashoi, Vinyl Collector, Zog, Royal Resident (reward), Emissary Sisko, Farm Merchant. Uncommon: everyone else (Adventurer, Alchemist, An, Andrew, Anita, Arthur, Banker Broadjaw, Chunk, Duke, Dusk, Emissaries Carlton/Ceanna/Fraiser/Wilson, End Dealer, Farmer Jon, Farmhand, Fear Mongerer, Felix, Fire Guy, Fisherman, Friendly Hiker, Geonathan and Jotraeline Greatforge, Gimley, Guy, Hornum, Hungry Hiker, Jack, Jacob, Jacobus, Jamie, Leo, Liam, Librarian, Lift Operator, Lumberjack, Lynn, Mason, Odawa, Oringo, Pest Wrangler, Plumber Joe, Rhys, Ryu, Sargwyn, Scardius, Shaggy, Stella, Tarwen, Terry, Tom, Trevor, Vex, Weaponsmith, Wizard, Wolf Shaman, Xalx, royal residents).
Per-visitor requests are not in NEU; the fetched wiki text only exposed the five above.

## 5. The Composter

Sources: wiki Composter; NEU garden.json `composter_upgrades`, items COMPOST, ENCHANTED_COMPOST, BIOFUEL, OIL_BARREL, VOLTA

| Rule | Value |
|---|---|
| Output | 1 Compost every 10 minutes at base speed |
| Cost per Compost | 4,000 Organic Matter + 2,000 Fuel |
| Organic Matter | crop items, value per item varies: Wheat/Seeds 1, Carrot 0.29, Melon Slice 0.2, Box of Seeds 25,600, Condensed Fermento 180,000, Condensed Helianthus 270,000 (wiki); enchanted versions are 160x base (?) |
| Fuel | Biofuel +3,000, Oil Barrel +10,000, Volta +10,000 (NEU lore) |
| Compost Bundle | Enchanted Compost, 160 Compost (?) |
| Uses | plot unlocks; Sprayonator / Pest Trap bait (attracts Earthworm, Mosquito) |
| Fully upgraded | about 63 Compost per hour using 108,000 OM and 54,000 Fuel per hour, runs 5 h before depletion (wiki) |
| Total upgrade cost | 178,250 Copper; 945 Cropie; 945 Squash; 105 Fermento; 70 Condensed Fermento; plus enchanted crops (wiki) |

Upgrade branches (25 tiers each, effect per tier): Speed +20% (max +500%); Multi Drop +3% extra compost chance (max 75%); Fuel Cap +30,000 starting at 130,000 at tier 1 (max 850,000); Organic Matter Cap +30,000 starting at 70,000 at tier 1 (max 790,000); Cost Reduction -1% (max 25%, giving 3,000 OM and 1,500 Fuel).

Copper cost per tier is the same for all five branches (NEU):

| Tier | Copper |
|---|---|
| 1 | 100 |
| 2 | 150 |
| 3 | 200 |
| 4 | 250 |
| 5 | 300 |
| 6 | 350 |
| 7 | 400 |
| 8 | 500 |
| 9 | 600 |
| 10 | 700 |
| 11 | 800 |
| 12 | 900 |
| 13 | 1,000 |
| 14 | 1,200 |
| 15 | 1,400 |
| 16 | 1,600 |
| 17 | 1,800 |
| 18 | 2,000 |
| 19 | 2,250 |
| 20 | 2,500 |
| 21 | 2,750 |
| 22 | 3,000 |
| 23 | 3,300 |
| 24 | 3,600 |
| 25 | 4,000 |

Sum per branch = 35,650 Copper (x5 = 178,250).

Item costs per tier (NEU, besides the Copper). Tiers 8-13 add Cropie 3,6,12,24,48,96; tiers 14-19 add Squash 3,6,12,24,48,96; tiers 20-22 add Fermento 3,6,12; tiers 23-25 add Condensed Fermento 2,4,8 (same in every branch). The remaining item, per branch:

- speed: T1 128 enchanted_wheat; T2 2 enchanted_golden_carrot; T3 256 enchanted_wheat; T4 4 enchanted_golden_carrot; T5 512 enchanted_wheat; T6 8 enchanted_golden_carrot; T7 8 enchanted_hay_bale; T8 16 enchanted_golden_carrot; T9 12 enchanted_hay_bale; T10 32 enchanted_golden_carrot; T11 16 enchanted_hay_bale; T12 40 enchanted_golden_carrot; T13 24 enchanted_hay_bale; T14 48 enchanted_golden_carrot; T15 32 enchanted_hay_bale; T16 64 enchanted_golden_carrot; T17 40 enchanted_hay_bale; T18 96 enchanted_golden_carrot; T19 48 enchanted_hay_bale; T20 128 enchanted_golden_carrot; T21 64 enchanted_hay_bale; T22 192 enchanted_golden_carrot; T23 96 enchanted_hay_bale; T24 256 enchanted_golden_carrot; T25 128 enchanted_hay_bale
- multi_drop: T1 1 enchanted_baked_potato; T2 64 enchanted_pumpkin; T3 2 enchanted_baked_potato; T4 1 polished_pumpkin; T5 4 enchanted_baked_potato; T6 2 polished_pumpkin; T7 8 enchanted_baked_potato; T8 4 polished_pumpkin; T9 16 enchanted_baked_potato; T10 8 polished_pumpkin; T11 32 enchanted_baked_potato; T12 16 polished_pumpkin; T13 48 enchanted_baked_potato; T14 24 polished_pumpkin; T15 64 enchanted_baked_potato; T16 32 polished_pumpkin; T17 80 enchanted_baked_potato; T18 48 polished_pumpkin; T19 112 enchanted_baked_potato; T20 64 polished_pumpkin; T21 144 enchanted_baked_potato; T22 96 polished_pumpkin; T23 176 enchanted_baked_potato; T24 128 polished_pumpkin; T25 224 enchanted_baked_potato
- fuel_cap: T1 1 enchanted_sugar_cane; T2 4 enchanted_melon_block; T3 2 enchanted_sugar_cane; T4 8 enchanted_melon_block; T5 4 enchanted_sugar_cane; T6 16 enchanted_melon_block; T7 8 enchanted_sugar_cane; T8 32 enchanted_melon_block; T9 16 enchanted_sugar_cane; T10 40 enchanted_melon_block; T11 32 enchanted_sugar_cane; T12 48 enchanted_melon_block; T13 40 enchanted_sugar_cane; T14 64 enchanted_melon_block; T15 48 enchanted_sugar_cane; T16 96 enchanted_melon_block; T17 64 enchanted_sugar_cane; T18 128 enchanted_melon_block; T19 96 enchanted_sugar_cane; T20 192 enchanted_melon_block; T21 144 enchanted_sugar_cane; T22 288 enchanted_melon_block; T23 192 enchanted_sugar_cane; T24 384 enchanted_melon_block; T25 256 enchanted_sugar_cane
- organic_matter_cap: T1 1 enchanted_cactus; T2 3 enchanted_cookie; T3 2 enchanted_cactus; T4 6 enchanted_cookie; T5 4 enchanted_cactus; T6 12 enchanted_cookie; T7 7 enchanted_cactus; T8 24 enchanted_cookie; T9 10 enchanted_cactus; T10 48 enchanted_cookie; T11 16 enchanted_cactus; T12 64 enchanted_cookie; T13 24 enchanted_cactus; T14 96 enchanted_cookie; T15 32 enchanted_cactus; T16 128 enchanted_cookie; T17 40 enchanted_cactus; T18 160 enchanted_cookie; T19 48 enchanted_cactus; T20 208 enchanted_cookie; T21 64 enchanted_cactus; T22 256 enchanted_cookie; T23 96 enchanted_cactus; T24 320 enchanted_cookie; T25 128 enchanted_cactus
- cost_reduction: T1 32 enchanted_brown_mushroom; T2 1 mutant_nether_stalk; T3 2 enchanted_huge_mushroom_2; T4 2 mutant_nether_stalk; T5 4 enchanted_huge_mushroom_1; T6 4 mutant_nether_stalk; T7 8 enchanted_huge_mushroom_2; T8 8 mutant_nether_stalk; T9 16 enchanted_huge_mushroom_1; T10 16 mutant_nether_stalk; T11 24 enchanted_huge_mushroom_2; T12 32 mutant_nether_stalk; T13 32 enchanted_huge_mushroom_1; T14 48 mutant_nether_stalk; T15 48 enchanted_huge_mushroom_2; T16 64 mutant_nether_stalk; T17 64 enchanted_huge_mushroom_1; T18 80 mutant_nether_stalk; T19 96 enchanted_huge_mushroom_2; T20 112 mutant_nether_stalk; T21 128 enchanted_huge_mushroom_1; T22 160 mutant_nether_stalk; T23 160 enchanted_huge_mushroom_2; T24 224 mutant_nether_stalk; T25 192 enchanted_huge_mushroom_1

## 6. SkyMart (Copper shop)

Sources: NEU SKYMART_NPC recipes and constants/skymart.json; wiki Copper. Currency Copper unless stated. NEU's two files disagree on a few prices, see below the table.

| Item | Price (skymart.json) |
|---|---|
| Biofuel | 20,000 coins |
| Sam's Scythe | 2,000 coins |
| Garden Scythe | 20 copper |
| Builder's Ruler | 20 copper |
| Sunder I | 10 copper |
| Dedication I | 250 copper |
| Green Thumb I | 1,500 copper |
| Lotus Bracelet | 50 copper |
| Lotus Belt | 100 copper |
| Lotus Necklace | 200 copper |
| Lotus Cloak | 500 copper |
| Basic Gardening Hoe | 5 copper |
| Basic Gardening Axe | 5 copper |
| SkyMart Brochure | 100 copper |
| Blossom Bracelet | 2,500 copper |
| Blossom BELT | 5,000 copper |
| Blossom Necklace | 7,500 copper |
| Blossom Cloak | 10,000 copper |
| Advanced Gardening Hoe | 25 copper |
| Advanced Gardening Axe | 25 copper |
| Large Walnut | 150 copper |
| Copper Talisman | 5,000 copper |
| Copper Ring | 25,000 copper |
| Copper Artifact | 750,000 copper |
| Yellow Bandana | 300 copper |
| Sowledge Chip | 200 copper |
| Cropshot Chip | 500 copper |
| HydroCan™ 1000 | 5,000 coins |
| HydroCan™ Turbo 2000 | 200 copper |
| HydroCan™ Turbo 2000 | 500 copper |
| Aquamaster™ X | 1,000 copper |
| Aquamaster™ Hydromax | 2,500 copper |
| Plant Diagnostics Tool | 50 copper |
| Euclid Wheat Hoe Mk. I | 250 copper |
| Gauss Carrot Hoe Mk. 1 | 250 copper |
| Pythagorean Potato Hoe Mk. 1 | 250 copper |
| Pumpkin Dicer Mk. 1 | 250 copper |
| Turing Sugar Cane Hoe Mk. 1 | 250 copper |
| Melon Dicer Mk. 1 | 250 copper |
| Cactus Knife Mk. 1 | 250 copper |
| Cocoa Chopper Mk. 1 | 250 copper |
| Fungi Cutter Mk. 1 | 250 copper |
| Newton Nether Wart Hoe Mk. 1 | 250 copper |
| Eclipse Hoe Mk. 1 | 250 copper |
| Wild Rose Hoe Mk. 1 | 250 copper |
| Farming Toolkit | 500 copper |
| Trading Post Barn Skin | 1,000 copper |
| Autumn Hut Barn Skin | 2,000 copper |
| Bamboo Barn Skin | 7,500 copper |
| Hive Barn Skin | 10,000 copper |
| Castle Barn Skin | 15,000 copper |
| Cube Barn Skin | 250 gems |
| Cozy Cottage Barn Skin | 250 gems |
| Windmill Barn Skin | 250 gems |
| Tavern Barn Skin | 250 gems |
| Melon Barn Skin | 950 gems |
| Lucky Barn Skin | 950 gems |
| Country Greenhouse Skin | 250 gems |
| Moon Greenhouse Skin | 400 gems |
| Orchard Greenhouse Skin | 3,000 copper |
| Botanical Greenhouse Skin | 5,000 copper |
| Portable Washer | 20,000 coins |
| SkyMart Vacuum | 5,000 coins |
| SkyMart Turbo Vacuum | 200 copper |
| SkyMart Hyper Vacuum | 500 copper |
| InfiniVacuum™ | 1,000 copper |
| InfiniVacuum™ Hooverius | 2,500 copper |
| Sprayonator | 25 copper |
| Pest Repellent | 15 copper |
| Pest Repellent MAX | 25 copper |

Disagreements: the NPC shop recipe lists Biofuel at 5,000 coins vs 20,000 in skymart.json; Copper Ring 20,000 vs 25,000 Copper; Copper Artifact 50,000 vs 750,000; Advanced Gardening Hoe/Axe 20 vs 25 Copper; Garden Scythe 20 Copper plus a Sam's Scythe vs 20 Copper; SkyMart Vacuum 5,000 coins in both. Prefer skymart.json (?).
Upgrade chains: SkyMart Vacuum -> Turbo (+200 Copper) -> Hyper (+500) -> InfiniVacuum (+1,000) -> Hooverius (+2,500 and a Chirping Stereo); Sprayonator 25 Copper -> Juicy (+Juicy Nozzle, 1,000) -> Salty (+Salty Nozzle, 10,000); Pest Repellent 15, Max 25 (consumes a Repellent); Portable Washer 20,000 coins (removes all active Sprayonator effects); Crop Analyzer Copper rewards (see section 9).
Extras: Sunder I 10, Dedication I 250, Green Thumb I 1,500, Skymart Brochure (Bustling reforge) 100, Large Walnut 150, Yellow Bandana 300, Builder's Ruler 20, Farming Toolkit 500, Plant Diagnostics Tool 50; Greenhouse skins Country 250 Gems, Moon 400 Gems, Orchard 3,000 Copper, Botanical 5,000 Copper; Peony Bracelet/Belt/Necklace/Cloak 50/100/200/500, Blossom upgrades 2,500/5,000/7,500/10,000.
NPC locations (NEU): SkyMart at Garden (-6,73,-25); Pesthunter Phillip (-26,72,-14).

## 7. Pests

Sources: wiki Pests; NEU items PEST_*_MONSTER, SPRAYONATOR family, PEST_REPELLENT*, vacuums, PEST_TRAP family, PEST_VEST, PESTHUNTERS_*, PESTERMINATOR, bestiary.json garden, PESTHUNTER_PHILLIP_NPC shop

| Rule | Value |
|---|---|
| Pest types | 15 (13 crop pests above + Lunar Moth + Field Mouse); every pest 600 HP (NEU lore) |
| Spawn chance | 0.2% (1 in 500) per crop broken while off cooldown (wiki) |
| Spawn cooldown | base 5 min; Finnegan -20%; Pesthunter Necklace/Cloak/Belt/Gloves -10% each; Pest Vest -15%; Squeaky reforge -2.5% per item; min 1 min 10 s (with Finnegan) or 2 min 10 s (without), max 20 min (wiki) |
| Bonus Pest Chance | every 100 lets you carry 1 more Pest before Fortune is cut (wiki) |
| Fortune penalty | with 0-99 chance: 4 or more pests triggers -5%, then -15%, -30%, -50%, -75% (max -75%); 100-199 chance: penalty starts at 5 pests; 500+ chance: 6 pests (wiki) |
| Offline | 1 Pest per hour while the Garden is unloaded; Pest Traps catch about 1 per 15 min and use bait (wiki) |
| Killing | only Vacuums damage Pests (hold right click, per-second damage, nearby); left click = Pest Tracker; Vacuum Bag counts caught Pests |
| Pest Repellent | 60 min, Pests take 2x as long to spawn; MAX variant 4x (NEU) |
| Pest Vest | +10% Bonus Pest Chance, spawn cooldown -15% (NEU) |
| Pesthunter gear | Necklace +5% Bonus Pest Chance, -10% cooldown; Cloak, Belt, Gloves each -10% (wiki); Badge +20% Bonus Pest Chance and Accessory Power +5 (NEU) |
| Pesterminator | armor enchant: per level +2 Farming Fortune and +1 Bonus Pest Chance (NEU); sources Beetle pest (I), Bazaar (I-V), A Beginner's Guide to Pesthunting (VI) |
| Pesthunter Phillip | wiki: up to 40 Pests exchange for 5 Farming Fortune for 30 min per Pest |

Vacuums (NEU, all need Garden 5): SkyMart 100 dmg/s +5 FF (Common); Turbo 150, +10 (Uncommon); Hyper 200, +15 (Rare); InfiniVacuum 300, +20 (Epic); Hooverius higher, about 2,200 effective with a perfect setup (wiki).
Sprayonator (NEU): Basic nozzle +25 Bonus Pest Chance, Pests 1.5x as likely to spawn in the sprayed plot, lasts 30 min; Juicy +50, 2x, 45 min; Salty stats not seen (?); 3 s cooldown; needs Garden 5.
Bait to Pest map (wiki + NEU lore): Dung = Fly, Beetle; Honey Jar = Cricket, Moth; Plant Matter = Locust, Slug (NEU lore also says Rat for the Rat monster); Tasty Cheese = Rat, Mite; Compost = Mosquito, Earthworm; Jelly = Dragonfly, Firefly, Praying Mantis; Moondew = Lunar Moth.
Traps: Pest Trap holds 2, Mouse Trap 3 (Field Mouse x3), Vermin Trap 4 (x4); max 3 traps; Field Mouse catch chance 2.78%, 7.89%, 10.26% (wiki). Lunar Moth always spawns in a trap with Moondew.
Drops: every pest except Field Mouse gives 1,000 coins, 1,500 Farming XP, 10 XP and one of six fertilizer types (10% each), plus enchanted crops of its type (Wheat pest 1 Enchanted Wheat base +1 per 52.5 Fortune; Melon pest 5 Enchanted Melon Slice +1 per 10.5). Field Mouse: 10,000 coins, 6,000 Farming XP, 50 XP, all six fertilizers, an enchanted crop, Squeaky Toy or Mousemat. Rare drops: Fly Beady Eyes 3%, Rat pet 0.4% Legendary, Slug pet 1% Epic / 0.2% Legendary, Beetle Pesterminator I 7%. Pet drop chance = base x (1 + Overbloom/100 + Pet Luck/600).
Pest currency (killed Pests, item SKYBLOCK_PEST) at Pesthunter Phillip (NEU): Pest Trap 50; Mouse Trap 75 (+Pest Trap); Vermin Trap 200 (+Mouse Trap + Fire in a Bottle); Pesthunter's Necklace/Cloak/Belt/Gloves 40 each; Pesthunter Badge 25 -> Ring 50 -> Artifact 100 -> Relic 200; Pesthunter's Lair barn skin 1,500; A Beginner's Guide to Pesthunting 500; Hedgehog pet 500; Biohazard armor 40/50/70/80 (needs the mushroom armor pieces).
Pest bestiary (NEU `bestiary.json` garden): Beetle, Cricket, Earthworm, Dragonfly, Fly, Locust, Mite, Mosquito, Moth, Praying Mantis, Firefly, Rat, Slug use bracket 6 with cap 250 kills; Field Mouse, Lunar Moth, Zombuddy bracket 7, cap 100; Timestalk Clone bracket 7, cap 20. Bracket 6 kill thresholds: [1,2,3,5,7,9,14,17,21,25,50,80,125,175,250,325,...] up to the cap (NEU `brackets` entry 6) (?: only bracket 6 was printed).
Vinyls (Chirping Stereo) attract pests, one per pest type (Pretty Fly = Fly; Beetle, Buzzin Beats, Cicada Symphony, Cricket Choir, Dynamites, Earthworm Ensemble, Firefly, Imagine Dragonflies, Pray For Me, Rodent Revolution, Slow and Groovy, Wings of Harmony exist as items) (?: mapping beyond Fly).

## 8. Jacob's Farming Contest

Sources: wiki Jacob's_Farming_Contest, Anita, Crop_Fortune; NEU ANITA_NPC recipes, items JACOBS_TICKET, medals, JACOBS_PARTICIPATION_MEDAL, SUPER_JACOB_SYSTEM

| Rule | Value |
|---|---|
| Frequency | every 3 SkyBlock days (1 real hour) |
| Duration | 1 SkyBlock day = 20 real minutes |
| Crops | 3 random crops per contest |
| Participation | Farming level X (10); at least 100 crops to earn rewards |
| NPCs | Jacob in the Hub (46,73,-129), Anita (53,73,-129), Jacobus (-50,70,-61) (NEU) |
| Participation reward | 1 Jacob's Ticket |

Brackets (wiki; percent = best share of all participants):

| Bracket | Top % (no perk) | Top % (Finnegan GOATed) | Tickets | Carnival Tickets | Bits | Other |
|---|---|---|---|---|---|---|
| Bronze | 60% | 70% | 10 | 1 | 40 | Turbo-Crop I |
| Silver | 30% | 40% | 15 | 1 | 50 | Turbo-Crop I |
| Gold | 10% | 20% | 25 | 2 | 60 | Turbo-Crop I |
| Platinum | 5% | 10% | 30 | 2 | 70 | Turbo-Crop I |
| Diamond | 2% | 5% | 35 | 3 | 80 | Turbo-Crop I |

Gold medal also raises the Farming level cap by 1 per crop (wiki). Medal items in NEU are only Bronze, Silver, Gold, so Platinum/Diamond probably award a Gold medal (?); the wiki medal column was garbled.

Anita's shop (NEU recipes; B/S/G = Bronze/Silver/Gold medal):

| Item | Cost |
|---|---|
| InfiniDirt Wand | 1 ticket |
| Prismapump x4 | 1 B + 2 tickets |
| Hoe of Great Tilling | 1 B + 5 tickets |
| Hoe of Greater Tilling | 1 S + 10 tickets |
| Hoe of Greatest Tilling | 2 S + 20 tickets |
| Basket of Seeds / Nether Wart Pouch | 2 S + 30 tickets |
| Delicate V | 2 S + 32 tickets |
| Anita's Talisman | 2 B + 32 tickets |
| Anita's Ring | 2 S + 64 tickets + Talisman |
| Anita's Artifact | 2 G + 100 tickets + Ring |
| Mechamind Chip | 1 G |
| Overdrive Chip | 2 G |
| Medal exchanges | 1 S = 2 B (and 2 B = 1 S); 1 G = 4 S (and 4 S = 1 G); 1 B = 2 tickets; 1 S = 4 tickets; 1 G = 16 tickets |

Other Anita offers (wiki): +1 Farming level cap item, Extra Farming Fortune (15 tiers, +4 to +60 FF, costing from 1 Gold medal and 0 tickets up to 10 Gold and 1,000 tickets), Personal Bests (Crop Fortune from your best Contest harvest, capped at +100 per crop), Abiphone contact for Anita (needs a Gold medal).
Ticket minions: Super Jacob Entertainment System makes 1 ticket per 3 h in one farming minion; Jacob's Participation Medal (legacy) fuels one farming minion for 3 days (NEU).

## 9. Desk, Greenhouse and the rest

Sources: wiki Greenhouse, Copper, The_Garden, Crop_Fortune; NEU items

| Topic | Value |
|---|---|
| Greenhouse unlock | Garden VII, complete the Carpenter visitor offer, Greenhouse Blueprint (wiki) |
| Growth | 4 hours per growth stage base, minimum 1 h 45 m 1 s with every bonus; water loss 2-3 per stage; T = 14400 / (1 + 0.025c + 0.0025g + 0.001a + upgrades) (wiki) (?: meaning of c, g, a) |
| Crop effects | orthogonal neighbours only; buffs Harvest Boost +20%/+30%, XP Boost +20%/+30%, Water Retain +50%/+100%, Immunity, Effect Spread, Bonus Drops; debuffs Harvest Loss -20%, XP Loss -20%, Water Drain -30% |
| Extra Greenhouses | 2nd = 100 Ethereal Vine + 10 Compost Bundle; 3rd = 150 Ethereal Vine + 20 Compost Bundle |
| Base yields | Wheat 72, Sunflower 232, Potato 150, Nether Wart 108 |
| Bonus drops | Ethereal Vine 5%, Evergreen Chip 3%, Synthesis Chip 3%, Burrowing Spores 0.2%, Iridium 0.2%, Overclocker 3000 0.05% |
| Greenhouse upgrades | 31,700 Copper in total; all Copper upgrades together 286,800 (wiki) |
| Plant Diagnostics Tool | right click analyses a plant, left click shows possible Mutations (50 Copper) (NEU) |
| Crop Analyzer | analysing a mutation yields Copper (Synthesis chip boosts it) (wiki, NEU) |
| Garden Sack | 20,160 capacity per item, pickups go straight in (NEU) |
| Fortune sources | plots +3 each (max +72), crop upgrades +5 per tier (+45), Dedication enchant, Personal Bests, chips (Cropshot +3 per level), Pesterminator, vacuums, Peony/Blossom sets |
| Pest FF penalty | up to -75% Farming Fortune, see section 7 |
| Not related | FARMING_ISLAND is the old private-world farming island (needs Wheat VI), not the Garden (NEU) |

## Biggest uncertainties

1. Visitor arrival interval, rarity percentages and quantity-scaling formulas were not on any fetched page.
2. Per-level crop milestone rewards (Garden XP, SkyBlock XP, Farming XP) were not extracted; the wiki cap of 92 contradicts NEU's 46 levels.
3. The full visitor milestone table (only tiers 1, 5, 16, 26 seen) and per-visitor requests.
4. The summarizer garbled the Garden level unlock mapping and showed level 1 as 47 XP; NEU's table was used instead.
5. Compost Bundle = 160 Compost, enchanted organic-matter multipliers and per-item OM values beyond a few examples.
6. Salty Sprayonator and Hooverius stats, Pesterminator scaling, Fortune penalty steps in the 100-499 Bonus Pest Chance range.
7. SkyMart price conflicts between NEU npc_shop recipes and constants/skymart.json.
8. Jacob's medal mapping for Platinum/Diamond and the bracket percentages (from one summarized fetch).
9. The Desk (Garden) page 404'd, so the menu layout and the crop-upgrade level gating are partly inferred.
