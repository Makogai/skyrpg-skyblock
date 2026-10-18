# The Rift research

Source note: numbers come from the NEU repo (`tools/skyblock/cache/neu-repo.zip`, MIT; constants/rift_guide.json, items/*.json lore, recipes, NPC shop recipes and mob drop recipes for the RIFT items, `*_RIFT_NPC`, `RIFTSTALKER_BLOODFIEND_*_BOSS`, `*_MONSTER` with panorama "rift") and the community mirror https://hypixelskyblock.minecraft.wiki/w/<Page> (pages fetched through a lossy summarizer: The_Rift, Rift_Time, Motes, Enigma_Souls, Timecharm, Wyld_Woods, Black_Lagoon, West_Village, Dreadfarm, Village_Plaza, Mirrorverse, Living_Cave, Stillgore_Château, The_Mountaintop, Barrier_Street, Vampire_Slayer, Riftstalker_Bloodfiend, Wizard_Tower, Rift_Gallery, Enigma_Cloak, Rift_Damage, Hearts, Porhtal, Ubik's_Cube, Motes_Grubber, Tel_Kar, Montezuma, Bacte, Leech_Supreme, Dimensional_Infusion, Living_Metal; Colosseum returned the Hub Colosseum instead of the Rift one, and Montezuma_Soul_Pieces, Rift_Transfer_Chest, Wither_Cage 404'd). The summarizer sometimes contradicted itself (area soul counts, soul total); conflicts are called out inline rather than silently resolved.
Where a line comes from general game knowledge rather than a fetched page or the NEU data it is marked (?). Anything marked (?) must be verified before being coded as a hard number.
NEU data lags the live game: NEU says the Enigma Cloak has 42 souls and the Rift Necklace needs 8 timecharms; the wiki says 52 souls. The wiki is treated as current for counts; NEU for item stats and recipes.
Coordinates are Rift-dimension world coordinates from the wiki "Location" fields (x, y, z); they are the only layout data found, there are no size/footprint figures anywhere in the sources.
Format follows docs/design/research/garden.md.

---

## 1. Entering, leaving, and Rift Time

Sources: wiki The_Rift, Rift_Time, Dimensional_Infusion, Wizard_Tower, Rift_Gallery; NEU RIFT_PRISM, BINGO_RIFT_TALISMAN, RIFT_WIZARD_TOWER_PORTAL, RIFT_WIZARD_TOWER_TRAVEL_SCROLL, CADUCOUS_LEGUME

| Rule | Value |
|---|---|
| Requirement | SkyBlock Level 12 (wiki, every Rift location page says "SkyBlock Level 12"); no quest |
| Entrance | Wizard Portal on the 7th floor of the Wizard Tower, Hub, at about (-47, 119, 93) (wiki). The Hub Wizard Tower was redesigned Feb 2026 |
| Reach the tower | Travel Scroll to the Wizard Tower from Grumblefoot, 11,111 coins (wiki); Travel Scroll to The Rift (NEU RIFT_WIZARD_TOWER_TRAVEL_SCROLL, "Rift-Transferable", teleports to the Rift Wizard Tower) is bought from Roger (Rift NPC) for 5,000 Motes (NEU) |
| Portal item | `RIFT_WIZARD_TOWER_PORTAL`, COMMON, placed on the private island creates a portal to the Rift (NEU) |
| Entry fee | One Dimensional Infusion (wiki), talk to the Wizard to buy one |
| Infusions | Free 1 per 4 hours, max 3 stored; or 32 Grand Experience Bottles; or 200 Bits (auto-buy toggle); or hold a Rift Prism in inventory/Accessory Bag (free entry, Erihann in the Wizard Tower consumes the accessory to imbue it); or the Tal Ker Garden visitor offer (wiki) |
| Bingo | Bingo Rift Talisman: free entry while on a Bingo profile (NEU) |
| Infusion consumption | NOT consumed on entry, only when you leave the Rift Wizard Tower; auto-consumed after joining 3 Rift instances with it ("INFUSION GONE"); refunded if the server reboots while you are in the Rift (wiki) |
| Starting Rift Time | 480 (8:00); 1 Rift Time = 1 second (wiki) |
| Drain | 1 per second in all normal areas |
| No drain | Wizard Tower (except the bottom floor), Rift Gallery, Mirrorverse (wiki) |
| Half drain | Colosseum, 0.5 per second (wiki Rift_Time) |
| Double drain | Time Chamber (Mountaintop), 2 per second (wiki Rift_Time) |
| Fall damage | none in the Rift (wiki) |
| Warning | at 10 s left: "THE RIFT IS COLLAPSING. You only have 10 seconds left before you are sent back." You are immune to time penalties in the last 10 s (wiki) |
| At 0 | removed from the Rift and teleported to the Wizard Tower on the Hub island (wiki) |
| Leaving early | walk back to the Rift Wizard Tower portal (the infusion is consumed then) (wiki, ?: exact exit mechanic not stated) |
| Orbs | pink orb particles spawn everywhere: +25 Motes and +2 Rift Time on contact; in Enigma's Crib +10 Motes and +0 time. Max 100 orbs at once, none within 20 blocks of another, vanish on pickup (wiki) |
| Kills | monsters give time back on death (NEU Wyld Helmet note "Monsters already grant time on death"); exact amount not found. Monster melee hits drain Rift Time (NEU LEECH_BELT "Monster melee attacks reduce 1 less Rift Time"; wiki: Leech Supreme attacks drain time) (?: per-kill gain) |
| Deadlines per area | see section 4 "Access cost": entering an area needs at least N Rift Time remaining |
| Max Rift Time | wiki computes a maximum of 4919.103 (81:59) from base 480 + all bonuses, multipliers Golden Belt x1.05 and 2 IQ Points +2% |
| Death | Hearts hit 0 in combat zones (Stillgore Château, Time Chamber): sent to the Oubliette prison, or fail the fight (wiki Hearts). Elsewhere damage is not the threat, time is (?) |

Rift Time that applies only inside the Rift (wiki The_Rift "Retained stats in Rift only"): Rift Time, Rift Damage, Speed, Intelligence, Magic Find, Mana Regen, Hearts. Everything else (Health, Defense, Strength, crit) is ignored in the Rift (?: inferred; wiki only lists what is retained).

Items brought in: only the 52 "Rift-Transferable" items (the lore line "X Rift-Transferable X") can move between dimensions, through Inverted Sirius' transfer service behind his shack in the Wyld Woods (wiki Wyld_Woods; Rift Transfer Chest page 404'd). Seven items are "Rift-Exportable" but lose their Motes value when crossing: Bubba Blister, Fang-tastic Chocolate Chip, Quantum Enchanted Book Bundle, Guardian Lucky Block, Highlite, Discrite, Spotlite (wiki The_Rift; NEU lore "Loses motes value when crossing dimensions"). Inventory handling on entry (separate inventory vs carried items) is not stated in any source (?): the live game keeps your own gear, but only Rift stats apply.

## 2. Rift stats

Sources: wiki Rift_Damage, Hearts, Rift_Time, Enigma_Cloak; NEU item lore

| Stat | Base | Notes |
|---|---|---|
| Rift Time | 480 | Total seconds you can stay; + gear, see below |
| Rift Damage | 20 (wiki Rift_Damage) | max shown 38, or 55 vs vampires with all six Blood Effigies (+2 each) |
| Hearts | 10 (wiki Hearts) | max 15; +1 each from Anti-Bite Scarf / Wizard Face variants / Lively Sepulture Chestplate / Leggings of the Coven / Wizardman Leggings, Rift Necklace, Vampire Dentist Relic; only matters in combat zones; some attacks reduce max Hearts permanently for the fight |
| Mana Regen | % bonus | Enigma Cloak +4% per 4 souls (wiki), Rift Necklace +25% (NEU), Montezuma +2 per piece at EPIC, Leggings of the Coven +6%, Hocus-Pocus Cipher +5 |
| Speed | vanilla | Enigma Cloak +2 per 4 souls |
| Intelligence | 0 | gear |
| Magic Find | 0 | gear |
| Motes | no stat | Motes are a currency item, boosted by McGrubber's Burger |

Rift Time sources (everything is flat seconds, NEU unless marked):

| Source | Rift Time |
|---|---|
| Enigma Cloak | +20 per soul found (wiki), max 52 x 20 = 1,040 |
| Rift Necklace (inside) | +380 s with all 8 timecharms (NEU RIFT_NECKLACE_INSIDE) |
| Crux accessories | Talisman +10, Ring +20, Artifact +35, Relic +55, Heirloom +70, Chronomicon +100 (+5 per 10-kill milestone, see section 8) |
| Wyld armor | Boots/Chest/Leggings/Helmet +15 each, Helmet +10 Int |
| Not-Dead-Gehog Mask | +30 s +15 Int |
| Agaricus Cap Cap | +50 s +15 Int |
| Chicken Leggs | +35 s |
| Gunther's Sneakers | +30 s +4 Speed |
| Exceedingly Comfy Sneakers | +45 s +5 Speed |
| Disinfestor Gloves | +30 s |
| Leech Belt | +80 s +5 Int +3 Speed |
| Leggings of the Coven | +80 s +1 Heart +6% Mana Regen (needs Vampire Slayer 3) |
| Montezuma pet | +15 per soul piece (RARE +25 shown at 1 piece; EPIC +100 at 6 pieces (NEU shows +100s at 6/9)) |
| Hocus-Pocus Cipher | +65 s +5 Int +5 Mana Regen, EPIC accessory |
| Porhtal eyes (permanent) | +10/+20/+30/+40/+50/+60/+70 for eyes 1-7 (wiki Porhtal), +280 total |
| Vampire Slayer levels | +20/+30/+45/+60/+80 for levels 1-5 (wiki Vampire_Slayer) |
| McGrubber's Burger | +12 per stack, max 5 stacks, permanent |
| Caducous Legume | +90, once per visit, consumed automatically on joining |
| Discrite | temporary +Health and +Rift Time for 60 min, up to +150 each |
| Spotlite | +300 Health and +300 Rift Time for 60 min; consuming also gives permanent +5 Health and +25 Rift Time; max 5 consumes |
| Respiration Talisman/Ring/Artifact | delay the loss of Rift Time on entry by 10/20/30 s (+5/+10/+15 Respiration) |
| Golden Belt | x1.05 multiplier (from Shen Magnum Opus) |
| 2 IQ Points | +2% (multiplier) |
| Orbs | +2 per orb (not in Enigma's Crib) |

## 3. Motes

Sources: wiki Motes, Motes_Grubber, Mirrorverse, Ubik's_Cube, Bacte, Leech_Supreme; NEU mob drop recipes, `*_RIFT_NPC` shop recipes (`SKYBLOCK_MOTE` is an item), MCGRUBBER_BURGER

| Rule | Value |
|---|---|
| What | currency item `SKYBLOCK_MOTE`, only usable in the Rift |
| Earn: orbs | 25 each (10 in Enigma's Crib) |
| Earn: mob kills | each mob drops Motes directly, table in section 4 (NEU drops, 100% chance) |
| Earn: Motes Grubber | NPC "eats rift items" and pays Motes; four Grubbers: Wyld Woods (-92, 65, 157), West Village (-93, 70.5, -73), Stillgore Château (164.5, 77, 125.5), Mountaintop (1.4, 115, 21) (wiki) |
| Grubber payout boost | McGrubber's Burger: +5% per stack, max 5 stacks = +25%; also +40 Motes on orb pickup per stack and +12 Rift Time per stack (NEU: "+5% Motes from Motes Grubber, +40 Motes on Orb pickup") |
| One-off: Dr. Emmett | 60,000 Motes on finishing the Mirrorverse (wiki) |
| One-off: Split or Steal | Ubik's Cube every 2 hours, 25,000 to 45,000 Motes depending on rounds won (wiki Motes; Cube page confirms 2 hr cooldown) |
| Boss rewards | Leech Supreme 3,000; Bacte 6,000 (wiki Motes) but Bacte page says 10,000 total with stage bonuses (conflict) |
| Kay (Lagoon Hut) | trades 1 Lil' Pad for 10 Motes (NEU `KAY_RIFT_NPC`) |
| Selling items | Motes Grubber sell values per item are not on any fetched page. Known: Living Metal sells for 5 Motes (wiki), Enchanted Vampiric Melon "worth more Motes than the sum of its parts" (21,000 per 32+ Thrall/Fledgling farm via Enchanted Vampiric Melons, wiki), Highlite 25,000 (wiki) (?: rest unknown) |
| Value loss | Rift-Exportable items lose their Motes value when exported (NEU) |
| Limits | no cap found (?) |

Motes sinks (NEU shop recipes; cost in Motes unless noted):

| NPC (area) | Item | Cost |
|---|---|---|
| Argofay Trafficker 1-4 (Wyld Woods) | Wyld Boots 50; Wyld Leggings 1,000; Wyld Helmet 1,000; Wyld Chain Top 2,000 | Motes only |
| Argofay Threebrothers (Wyld Woods) | Wyld Sword (one NPC each, same price) | 333 + 3 Shy Crux |
| Argofay Bugshopper (Wyld Woods) | Larva Hook | 6 Shy Crux |
| | Empty Odonata Bottle | 2 Shy Crux + 400 |
| | Harmonious Surgery Toolkit | 1 Bottled Odonata + 2,000 |
| Enigma (Wyld Woods) | Enigma Cloak | 100 Motes (wiki Enigma_Cloak) |
| Roger | Travel Scroll to the Rift | 5,000 |
| Roy (Lagoon Hut) | Enigma Soul | 4,000 + 8 Deadgehog Spine |
| Cryptosis (Village Plaza) | Enigma Soul | 15,000 + 10 Scribe Crux |
| Fairylosopher (Fairylosophy Tower, Château) | Enigma Soul | 10,000 + 32 Splatter Crux |
| Plumber Joe (West Village) | Plumber's Bucket 1,000; Super Scorcher 5,000 | |
| Shifted (Wyld Woods, Shifted Tavern) | Speed Elixir 10,000; Jump Elixir 5,000; Strength Elixir 12,000 | each + 5 Shy Crux |
| Finplex (West Village) | Zoop the Fish | 99,999 |
| Seraphine | Detransfigured Mask | 1,500 + Agaricus Cap Cap |
| Seymour (Plaza) | Exceedingly Comfy Sneakers | 49,999 + Gunther's Sneakers |
| Chipper McPickaxe | Anti-Sentient Pickaxe | 24,999 |
| Master Tactician Fink | Tactician Murder Weapon | Leech Sword + 500 |
| Dr. Emmett / Ttemme RD | Mrahcemit Esrevrorrim timecharm 10,000 + 10 Shadow Crux; Reflection Book Bundle 5,000 + 16 Shadow Crux | |
| Madame Eleanor X | Shen's Ringalia | 25,000 + 64 Puff Crux + 20 Youngite/Timite/Obsolite + Disinfestor Gloves |
| Kat (Rift, Infested House) | Fake Neuroscience Degree 5,000 + 10 Volt + 10 Shadow Crux + 30 Fly/40 Spider/60 Silverfish; Disinfestor Gloves, Vermin Belt, Rift Ferret, Key to Kat Soul for pests | |
| Vampire Slayer | Tier costs 2,000 / 4,000 / 5,000 / 7,000 / 10,000 | section 7 |
| Shen's Auction (Barrier Street) | bids in Motes | section 4 |
| Gravity I potion (Mountaintop vending machine) | 1,000 | wiki |
| Sun Gecko essence shop | Enigma Soul 25 essence | NEU (Sun Gecko Essence Shop) |

## 4. Areas

Sources: wiki area pages, NEU rift_guide.json (per-area task list), NEU `*_RIFT_NPC`. 9 main areas plus Mirrorverse, Wizard Tower, Rift Gallery, Barrier Street, Time Chamber.

Unlock chain (wiki The_Rift, Timecharm, area pages; the summarizer's list disagrees in places so the per-area pages win):

| Area | Gate to enter | Min Rift Time left | Rift coordinates |
|---|---|---|---|
| Wizard Tower (Rift) | portal from Hub | none, no drain except bottom floor | the start |
| Wyld Woods | start | none | (-78, 69, 114) |
| Black Lagoon | from Wyld Woods; Tel Kar dialogue / Dr. Edwin portal | 4:00 | (-119, 69, 28) |
| West Village | Supreme Timecharm | 3:00 | (-80, 70, -56) |
| Dreadfarm | Supreme Timecharm; connects West Village and Village Plaza and Déjà Vu Alley | none stated | (-53, 71, -142) |
| Mirrorverse | Supreme Timecharm (entrance in West Village) | 3:00, then time frozen | separate instance (?) |
| Village Plaza | Mirrorverse timecharm (mrahcemiT esrevrorriM) + Chicken N Egg Timecharm | 8:00 | (-2, 70, -73) |
| Barrier Street | Globulate Timecharm | 10:00 | (30, 72, -90) |
| Living Cave | SkyBlock Citizen Timecharm | 5:00 | (38..3, 63..68, -197..-203), via Déjà Vu Alley from the Plaza |
| Colosseum | Living Timecharm | 15:00, halved drain inside | not found (?) |
| Stillgore Château | talk to Deer after crossing the Photon Pathway | none stated | (197, 88, 77); Photon Pathway about (152, 80, -13) |
| The Mountaintop | Vampiric Timecharm | 9:00 | (29, 134, 18) |
| Wizardman Bureau | Celestial Timecharm | none | Mountaintop sub-area |

Rough relative layout (derived from the coordinates; +x east, -z north, Wyld Woods z positive): the Rift is a long N-S strip. From the Wizard Tower/Wyld Woods (z about +110) the path goes north-west to the Black Lagoon (z +28, x -119), then north to West Village (z -56), Dreadfarm (z -142) and Village Plaza (z -73, x -2, central). Barrier Street sits next to the Plaza (30, -90). Living Cave is far north (z -200) beyond Déjà Vu Alley. The Château is far east (x +197) across the Photon Pathway (x +150, z -13). The Mountaintop (y 134) sits above the centre (29, 18), the highest point, reached via the Vampiric Timecharm. Rift Gallery is at roughly (0, 56, 63), reached by a telepad in the Wizard Tower (wiki), so it is a separate pocket. (?: all inter-area directions are inferred from coordinates only.)

### 4.1 Wyld Woods (-78, 69, 114)

Sources: wiki Wyld_Woods, Tel_Kar, Motes_Grubber; NEU rift_guide `wyld_woods`, shops

| Item | Detail |
|---|---|
| Theme | network of giant interconnected trees with treehouses; slime blocks to launch, pressure plates, glass blocks with items. Sub-locations: Broken Cage, Enigma's Crib, Pumpgrotto, Shifted Tavern, The Bastion, Wizard Tower |
| NPCs | 36 NPCs (wiki). Tel Kar (-113, 69, 62; needs more than 2:30 Rift Time left; full dialogue gives an Enigma Soul), Enigma (cloak, base of Wizard Tower), Porhtal (Broken Cage, teleports), Sad Jacquelle (on a branch next to her house; lost cat, Montezuma pet), Argofay Bughunter (collect Odonata; rewards Silkwire Stick, Silkrider Safety Belt), Bugshopper, Threebrothers (spare weapon in trees), four Traffickers (Wyld armor in treehouses), Inverted Sirius (behind his shack, item transfer), Shifted (elixirs, Shifted Tavern), Motes Grubber, Reed's boat part |
| Mobs | Larva (3 HP, drops Larva Silk, harvest with Larva Hook, 8 per hook), Odonata (bottle with Empty Odonata Bottle: hold and look at it for 3 s close by), Shy (110 HP, level 1, 6 combat XP, 100 Motes, 100% Shy Crux) |
| Timecharm | none here; Supreme Timecharm recipe uses Wyld Woods/Black Lagoon materials (section 6) |
| Montezuma | piece 1 under a tree (-93.5, 69, 112.5) |
| Enigma Souls | 9: Two Plates (pressure plates), Up in the Sky (slime launch), Between Branches (glass block + Larva Silk), Wither Cage (Wither Skull in Broken Cage), Tough Bark (20 Muted Bark on a tree; Barking Wolves 150 HP drop Muted Bark), Woods Flower Pot (flower pot chain), Fleespook (follow the mob), Pressurized (jump in the lake to Pumpgrotto), Next to Enigma (water pool) |
| Rift Guide tasks | Crux Talisman, Reed's Boat: Powerful Glue, Help Bughunter, Montezuma 1 and 2, Eye: The Intruder (Wizard Tower), Help Inverted Sirius, Sad Jacquelle, Porhtal, Wear Enigma's Cloak, Threebrothers, Wyld Armor Set |

### 4.2 Black Lagoon (-119, 69, 28)

Sources: wiki Black_Lagoon, Leech_Supreme; NEU rift_guide `black_lagoon`

| Item | Detail |
|---|---|
| Theme | swamp; water channels with strong currents; red and brown giant mushrooms that grow into protective structures when you approach; crossing needs Lil' Pads (swimming spawns Lagoon Leeches). Sub-locations: Lagoon Cave, Lagoon Hut, Leeches Lair |
| NPCs | Kay (Lil' Pad for 10 Motes), Reed (stranded in Lagoon Hut, sells boats; the boat is a 5-part quest needing items from each area, section 8), Roy (Enigma Soul for 4,000 Motes + 8 Deadgehog Spine), Dr. Edwin (portal provider), Dr. Phear (Leeches Lair, return transport), Mushroom Guy (puzzle guide; disables fast travel nearby) |
| Mobs | Deadgehog (150 HP, level 1, 6 combat XP; arrows 9 damage, melee 2; drops 150 Motes, 3 Deadgehog Spine, 50% +1 spine), Lagoon Leech (80 HP, level 2, 6 XP; drops 6 Lil' Pad, two 50% rolls of 3) |
| Miniboss | Leech Supreme in Leeches Lair: 800 HP, level 10, 4 phases at 640/480/320/160 HP: Slime Pound (shockwaves, 7 attacks), Wicked Bombs (marked blast radii), Leech Swarm (scales with players), Mortiferous Lazer (jump-and-crouch laser, 3.5 cycles); all attacks drain Rift Time; respawns after 40 s; rewards 8 Leech Supreme Fragment (100%), 3,000 Motes, teleport to the Lagoon Hut. Spot (-146.5, 31, 71.5) negates two phases. Needs Dr. Edwin's quest |
| Montezuma | piece 3 behind a waterfall in Leeches Lair (-139.7, 45, 15) |
| Enigma Souls | 5: mushroom puzzle, spinning runes, Lagoon Cave parkour, Roy purchase, one dialogue-based (wiki attributes the Tel Kar dialogue soul to Black Lagoon here, but the Tel Kar page puts him in Wyld Woods) |
| Gear | Not-Dead-Gehog Mask (Wyld Helmet + 10 Deadgehog Spine), Leech Sword, Leech Belt, Aspect of the Leech (section 8) |
| Porhtal eye | The Gill-Man in Lagoon Hut |

### 4.3 West Village (-80, 70, -56)

Sources: wiki West_Village; NEU rift_guide `west_village`

| Item | Detail |
|---|---|
| Theme | village with rooftops. Sub-locations: Cake House (two floors), Infested House, Dolphin Trainer; Mirrorverse entrance |
| NPCs | Gunther (Rift Race parkour, 10 checkpoints; under 60 s gives Dark Pebble; under 35 s gives Gunthesizer Lichen, which upgrades Wyld Boots to Gunther's Sneakers), Chef and Joey McPizza (hot dog contest: 50 hot dogs in 60 s gives an Enigma Soul), Unhinged Kloon (Retro-Encabulating Visor; hack all terminals to rainbow, reward Defective Monitor), Kat (Rift) in the Infested House (vermin vacuum), Sorcerer Okron (glyph warding puzzle, Warding Trinket), Alchemist, Finplex, Plumber Joe, Dr. Emmett (Mirrorverse), Motes Grubber |
| Mobs | Shadow (125 HP, level 3, 6 XP; drops 150 Motes + 100% Shadow Crux) |
| Montezuma | piece 4 on a roof (-62.5, 88, -81.5) |
| Enigma Souls | 7: Hot Dog Contest, a two-player puzzle, between rooftops (Larva Silk string), Infested House (key-locked: Key to Kat Soul = 40 Silverfish), Cake House floors 1 and 2, Dolphin Trainer parkour end |
| Timecharms/tasks | Supreme Timecharm is crafted from stuff gathered here and in Dreadfarm/Black Lagoon; Crux Ring (Shadows) |

### 4.4 Dreadfarm (-53, 71, -142)

Sources: wiki Dreadfarm; NEU rift_guide `dreadfarm`

| Item | Detail |
|---|---|
| Theme | twisted mirror of the Hub farm; small crop plots, a Great Beanstalk, livestock |
| NPCs | Shania (farming requests, completes when you wear Chicken Leggs, gives Proto-Chicken), Sick Farmer, chickens/cows/sheep; Porhtal eye The Baba Yaga (defeat for permanent +30 Rift Time) |
| Crops | Agaricus Cap (look at brown mushrooms until red, harvest with Wand of Farming), Caducous Stem (click rose bushes from top to bottom), Wilted Berberis (break dead bushes with purple particles). Wilted Berberis farming uses Wand of Farming (NEU "Allows you to farm the peculiar crops from the rift while held") |
| Mobs | Volt (160 HP, level 4, 6 XP; 155 Motes + 100% Volt Crux); Not Chicken (3 HP; shoot with Berberis Blowgun midair to drop Metaphoric Egg) |
| Montezuma | piece 5 behind a cracked stone wall (-24.5, 72, -198.5) breached with the Horsezooka (from Cowboy Nick) |
| Enigma Souls | 4: Farm Flower Pot chain, Buttons (press wooden buttons in West Village and Dreadfarm), Farm Balloons (Berberis Blowgun), Beanstalk top |

### 4.5 Village Plaza (-2, 70, -73)

Sources: wiki Village_Plaza, Barrier_Street; NEU rift_guide `village_plaza`

| Item | Detail |
|---|---|
| Theme | modest town plaza; sub-locations Barry Center/HQ, Murder House, Taylor's, Lonely Terrace, Half-Eaten Cave, Book in a Book, Déjà Vu Alley (floating islets) |
| NPCs | 24: Barry (protesters stressing him; Barry's Montgray Pen for the Citizen Timecharm), the seven protesters, Cowboy Nick (feed rabbits carrots, gives Horsezooka), Detective Amog (reverse-murder part 1; parts 2 and 3 on the Mountaintop, Wallet item), Seymour (shoes), Seraphine (detransfigured, needs Anti-Morph Potion; requires finishing Barry's quest), Cryptosis, Damia (Barrier Street auctions), Motes Grubber (West Village variant listed elsewhere) |
| Mobs | Scribe (130 HP, level 5, 6 XP; 100 Motes + 100% Scribe Crux) |
| Montezuma | piece 6 in a book inside the flower shop (2.5, 63, -16.5); piece 7 floating island in Déjà Vu Alley (38.5, 65, -117.5) |
| Enigma Souls | wiki summary says 8 (rabbit hat puzzle, Fleespook, balloons with the Blowgun, Larva Silk exploration, sitting with Lonely Ävaeìkx, location puzzles) while the Enigma_Souls page lists 5 (conflict, ?) |
| Porhtal eye | The Bankster |

### 4.6 Barrier Street (30, 72, -90)

Sources: wiki Barrier_Street

| Item | Detail |
|---|---|
| Gate | Globulate Timecharm, 10:00 Rift Time |
| Feature | Shen's Auctions run by Damia (30.5, 72, -94.5); a new auction every SkyBlock year, bids in Motes; items: Dead Cat Food (60 winners), Flex Helmet (15), Placeable Fairy Soul (15), Punchcard Artifact (25), Rift Prism (25), Ancient Elevator (15) |
| Enigma Soul | on top of a Barrier Street building, hidden in a Gold Block until the Plaza Fleespook is finished |

### 4.7 Mirrorverse

Sources: wiki Mirrorverse; NEU rift_guide

| Item | Detail |
|---|---|
| Gate | Supreme Timecharm, 3:00 Rift Time left, then Rift Time is frozen inside |
| Theme | 8 sequential rooms, mirror-based puzzles; Dr. Emmett's story and experiments |
| Rooms | 1 Levers (match mirror lever positions, iron door); 2 Lava Maze (iron-block path visible only in the mirror); 3 Crafting (kill baby zombies/cave spiders/slimes according to mirror positions, craft the remmaH yniT); 4 Parkour over lava (hard mode gives Test Bucket Please Ignore); 5 Mirror switching across 4 rooms (optional 4th room gives Big Brain Talisman); 6 Dance sequence (Move, Sneak, Stand, Jump, Punch on coloured blocks; gives Tiny Dancer); 7 Vertical invisible-platform parkour, Emmett Pointer shows green lasers (no checkpoints gives Miniaturized Tubulator); 8 Reward room |
| Rewards | 60,000 Motes once; shop; Dr. Emmett/Ttemme RD sells the mrahcemiT esrevrorriM timecharm (10,000 Motes + 10 Shadow Crux) |

### 4.8 Living Cave and Living Stillness (38..3, 63..68, -197..-203)

Sources: wiki Living_Cave, Living_Metal; NEU rift_guide `living_cave`

| Item | Detail |
|---|---|
| Gate | SkyBlock Citizen Timecharm, 5:00; reached from Village Plaza via Déjà Vu Alley |
| Theme | blue-tinted cavern; Living Stillness sub-area below is a snowy frozen cave |
| Mechanic: Living Metal | Living Stillness inflicts Frostbite and Rift Time drain unless armor has Living Metal applied (4 armor spawn variants: cap, chest, pants, boots; "Juice up an armor piece": Living Metal crafts a Null mob spawn, kill it to juice the armor; max out all pieces for the Living Metal Suit bonus) |
| Living Metal | mined from Lapis Ore with the Self-Recursive Pickaxe: every 10 blocks mined raises the multiplier by 1 (1x to 5x), up to 20 Living Metal at 5x, doubled in Living Stillness; sells for 5 Motes |
| Living Metal snakes | mobile chains of blue stained glass with lapis heads; stopped with a Frozen Water Pungi, mined from the tail; head destroyed drops Living Metal Heart |
| NPCs | Rock (rock denizens give the lead for Living Metal Hearts), Chipper McPickaxe (Anti-Sentient Pickaxe 24,999 Motes) |
| Mobs | Autonull (155 HP, level 5, 6 XP; 400 Motes + Nullified Metal; Nullified Metal upgrades a sword into the Self-Recursive Pickaxe), Frozille (135 HP, level 5, 6 XP; 150 Motes + Frosty Crux); Autoboots 500 Motes (wiki Motes) |
| Timecharm | Living Timecharm (section 6) |
| Montezuma | piece 8 under a breakable snow block (22.5, 43, -219.5) |
| Enigma Souls | 2: (38, 63, -197) and (3, 68, -203) |
| Lights puzzle | a "Living Cave lights" puzzle was not found in any source (?) |

### 4.9 Colosseum

Sources: NEU rift_guide `colosseum`; wiki Bacte, Timecharm. The wiki Colosseum page was the Hub one, so no Rift layout, no coordinates.

| Item | Detail |
|---|---|
| Gate | Living Timecharm, 15:00 Rift Time left; Rift Time drains at half speed inside |
| Boss | Bacte: "second boss in the Rift", 1,000 HP in 5 phases at 1000/800/600/400/200; one letter of "Bacte" per phase. P1 no attacks, 1 tentacle; P2 spike attacks, 2 tentacles; P3 slice + spike, 3; P4 all three attacks, 4 tentacles with reduced health; P5 arena becomes slime blocks (bouncing), 6 tentacles with 20 HP each. Punch Globowls into tentacles; Blobbercysts take 100 damage from a pushed Globowl. Rewards 8 Bacte Fragment (100%), 0.01% Celadon Dye, 10,000 Motes total (stage + mob bonuses; wiki Motes says 6,000, conflict) |
| Other | Porhtal eye Gooey (Colosseum), 1 Enigma Soul, Reed's Boat part 5 (Powerful Glue), Barrier Street / Globulate Timecharm come from Bacte Fragments |
| Mobs | Autoboots 500 Motes (?: area unclear) |

### 4.10 Stillgore Château (197, 88, 77)

Sources: wiki Stillgore_Château, Hearts, Rift_Damage; NEU rift_guide `stillgore_chateau`, mobs

| Item | Detail |
|---|---|
| Gate | talk to Deer after crossing the Photon Pathway; Porhtal eye The Prince |
| Theme | gothic vampire castle, several towers incl. Fairylosophy Tower, holes, glass structures, flower-pot trails |
| Gimmick: Hearts | here monsters hit in Hearts, not Rift Time; dying sends you to the Oubliette prison where Oubliette Guards must be beaten to escape (NPC Julian is a prisoner) |
| Gimmick: Healing Melon | Vampire drops Vampiric Melon; 2 melons (recipe above) craft a Healing Melon (heal 7 Hearts, 50 mana); Juicy Healing Melon (9, Vampire Slayer 2); Luscious (12, Vampire Slayer 4) |
| Gimmick: Blood Effigies | 6 effigies; break 3 Redstone blocks top to bottom to gain +2 Rift Damage for 20 min (bedrock resets timers) |
| NPCs | Maddox the Slayer (starts Vampire Slayer), Fairylosopher (Enigma Soul 10,000 + 32 Splatter Crux), Motes Grubber (164.5, 77, 125.5), Julian |
| Mobs | Thrall (190 HP, level 6, 30 XP; 200 Motes, 100% Hemovibe, 100% Vampiric Melon); Fledgling (210 HP, level 8, 20 XP; 250 Motes, Hemovibe x1 100% + x1 50%, Vampiric Melon); Splatter (135 HP, level 6, 30 XP; 150 Motes, Splatter Crux, Hemovibe). Wiki Vampire_Slayer lists combat XP Thrall 30, Fledgling 40, Splatter 40 (differs from NEU, ?) |
| Miniboss | Clotgoyle (the only Vampire slayer miniboss; Stonewrath spawns 3, each lowers max Hearts by 1) |
| Montezuma | piece 9 second floor of the end house on the Photon Pathway (152.5, 80, -13.5) |
| Enigma Souls | 5-6 (wiki Enigma_Souls 5, Château page 6): puzzles, Fleespook, flower-pot trail, balloons |
| Fairy Soul | 1 (defeating the Fairylosopher) |

### 4.11 The Mountaintop (29, 134, 18)

Sources: wiki The_Mountaintop, Ubik's_Cube, Porhtal; NEU rift_guide `mountaintop`

| Item | Detail |
|---|---|
| Gate | Vampiric Timecharm, 9:00 Rift Time left |
| Sub-locations | Continuum, Time Chamber (2x drain, Sun Gecko boss via Oruo the Almighty), Time-Torn Isles, Rose's End, Walk of Fame, Cerebral Citadel, The Vents, Wizardman Bureau, Wizard Brawl, Trial Grounds |
| Wizardman Trials | Temporal Race etc. (NPC Plate), three minigame fights; Wizardman Bureau needs the Celestial Timecharm |
| Ubik Von Neumann | three games (Three Options, Averages, Tit for Tat), each gives an Enigma Soul; later Ubik's Cube (Split or Steal, 2 hr cooldown) |
| Shen | five Shen NPCs in the Cerebral Citadel (Albedo, Chaos, Citrinitas, Magnum Opus, Rubedo): Citrinitas gives the Celestial Timecharm; Magnum Opus gives the Golden Belt; "The Richest Man in the Multiverse" puzzles |
| Eleanors | six Madame Eleanor Q. Goldsworth NPCs (I, II, V, VII, IX, X in NEU): skill quests (Mining, Fishing, Farming, Foraging, Combat) outside the Rift that give Eleanor's Cap/Tunic/Trousers/Slippers; X sells Shen's Ringalia |
| Detective Amog | parts 2 and 3 (Detective's Wallet) |
| Old Timer | Timite collection (Timite, Youngite, Obsolite, Time Gun); Highlite, Discrite, Spotlite crafted from them (section 8) |
| Mobs | Puff (100 HP, level 7, 6 XP; 50 Motes, 10% Puff Crux; Gravity I potion 1,000 Motes raises spawns; Berberis Blowgun damages Puffs) |
| Misc | Close the Breach (task; the Breach Close event gave the "There and Back Again" memento, Dec 10 2024), Porhtal eye The 7th Sin, Celestial Starstone from Puff Crux |
| Enigma Souls | 8-10 (wiki Enigma_Souls 8, Mountaintop page 10): balloons, NPC quests, floating platforms, puzzle games, essence purchase (Sun Gecko shop) |

### 4.12 Wizard Tower (Rift) and Rift Gallery

Sources: wiki Wizard_Tower, Rift_Gallery; NEU rift_guide

| Item | Detail |
|---|---|
| Rift Wizard Tower | spawn point, no drain except the bottom floor; Enigma at its base, Porhtal eye The Intruder, Elise (gallery manager, gives Rift Necklace after the Supreme Timecharm is donated) |
| Rift Gallery | counterpart of the Museum; telepad in the Wizard Tower after giving Elise the Supreme Timecharm; no time drain; 8 timecharm slots, each secured charm +100 SkyBlock XP and unlocks zones; Montezuma piece 2 at (0, 56, 63.5) (listed as West Village in one place, Rift Gallery in the list) |

## 5. Enigma Souls

Sources: wiki Enigma_Souls, Enigma_Cloak; NEU ENIGMA_CLOAK, shop recipes

| Rule | Value |
|---|---|
| Total | 52 (wiki Enigma_Cloak and The_Rift). NEU cloak lore says 0/42, the Enigma_Souls summary says 47 (stale; do not use) |
| Enigma Cloak | EPIC cloak, claim from Enigma for 100 Motes (wiki); +20 Rift Time per soul found (NEU/wiki) |
| Every 4 souls | go back to Enigma and buy an upgrade with Motes (amount not found, ?): +2 Speed and +4% Mana Regen per upgrade, 13 upgrades = +26 Speed, +52% Mana Regen at 52 souls |
| Per-area counts | Wyld Woods 9; Black Lagoon 5; West Village 7; Dreadfarm 4; Village Plaza 5-8; Living Cave 2; Colosseum 1; Château 5-6; Mountaintop 8-10; The Bastion 4 (probably inside the Wyld Woods group, ?); Other 5. Sums do not agree with 52, so per-area counts are not reliable (?) |
| Kinds of soul | follow a Fleespook mob; flower-pot chains (trail of pots); balloons popped with the Berberis Blowgun; parkour end points; NPC dialogue (Tel Kar), puzzles (two players), purchases (Roy, Cryptosis, Fairylosopher, Sun Gecko essence), mini-games (hot dog contest, Ubik games), pressure plates, hidden behind breakable blocks/gold block |
| Souls placed | the Enigma Soul item can be bought three ways; found souls are unique per profile and each is collected once |

## 6. Timecharms

Sources: NEU RIFT_TROPHY_* items and recipes; wiki Timecharm, Rift_Gallery, area pages

| Timecharm | Obtained by | Gate it unlocks |
|---|---|---|
| Supreme (`RIFT_TROPHY_WYLDLY_SUPREME`) | craft (NEU grid): 4 Bottled Odonata (corners), 2 stacks of 16 Lil' Pad (32), 2 stacks of 12 Deadgehog Spine (24), 4 Leech Supreme Fragment (centre) | West Village, Dreadfarm, Mirrorverse; delivered to Elise first gives Gallery access and Rift Necklace |
| Chicken N Egg (`RIFT_TROPHY_CHICKEN_N_EGG`) | craft: Caducous Stem Bunch 1, Metaphoric Egg x2 x4 (8), Agaricus Cap Bunch 1, Wilted Berberis Bunch x2, Proto-Chicken centre | Village Plaza (with the Mirror charm) |
| mrahcemiT esrevrorriM (`RIFT_TROPHY_MIRRORED`) | buy from Dr. Emmett / Ttemme RD: 10,000 Motes + 10 Shadow Crux | Village Plaza (with Chicken N Egg) |
| SkyBlock Citizen (`RIFT_TROPHY_CITIZEN`) | trade at Barry: 16 Scribe Crux + Barry's Montgray Pen (wiki) | Living Cave |
| Living (`RIFT_TROPHY_LAZY_LIVING`) | craft: Living Metal 20 x8 = 160, Frosty Crux 20 centre | Colosseum |
| Globulate (`RIFT_TROPHY_SLIME`) | craft: Bacte Fragment x8 + Shy Crux 20 centre (wiki says 6 fragments, NEU 8 positions) | Barrier Street |
| Vampiric (`RIFT_TROPHY_VAMPIRIC`) | craft: Bloodbadge x6 + Wilted Berberis Bunch x3 | The Mountaintop |
| Celestial (`RIFT_TROPHY_MOUNTAIN`) | Shen Citrinitas (Cerebral Citadel) | Wizardman Bureau |

Effects: each secured timecharm gives +100 SkyBlock XP and scales the Rift Necklace; the Necklace shows "Secured: N/8 Timecharms" and the stat table by N is not on any page (?). Fully secured: inside the Rift +380 Rift Time, +1 Heart, +1 Rift Damage, +10 Int, +6 Speed, +25% Mana Regen, MYTHIC; outside +150 Health, +30 Defense, +25 Strength, +85 Int, +8 Magic Find, +1 Combat/Farming/Enchanting Wisdom, LEGENDARY (NEU). Soulbound.

## 7. Vampire Slayer: Riftstalker Bloodfiend

Sources: wiki Riftstalker_Bloodfiend, Vampire_Slayer; NEU RIFTSTALKER_BLOODFIEND_*_BOSS

| Rule | Value |
|---|---|
| Start NPC | Maddox the Slayer in Stillgore Château (wiki) |
| Boss progress | kill vampires in the Château to fill the bar (?: not stated) |
| Tier I | 625 HP, 0.5 Hearts/s, cost 2,000 Motes, 10 slayer XP, needs Wolf (Sven) 3 (wiki); 3 abilities over 3 phases (NEU) |
| Tier II | 1,100 HP, 1 Heart/s, 4,000 Motes, 25 XP, needs Tier I; 5 abilities, 3 phases |
| Tier III | 1,800 HP, 1/s, 5,000 Motes, 60 XP, needs Tier II; 7 abilities, 5 phases |
| Tier IV | 2,400 HP, 1/s, 7,000 Motes, 120 XP, needs Tier III + Combat 15; 7 abilities, 5 phases |
| Tier V | 3,000 HP, 1.5/s, 10,000 Motes, 150 XP, needs Tier IV + Combat 25; 8 abilities, 7 phases |
| Abilities (wiki) | Coagulation (periodic damage 0.5/1/1/1/1.5 Hearts per second; with active Blood Ichor 3/tick at T5). Spectral Form (turns into a bat, teleports). Mania (boss immune 26 s, red/green block rings, wrong ring deals 2/3/5/6/15 Hearts; T2 at 60% HP, T3 at 75% and 40%). Twinclaws (every 7 s two giant claws, frontal claw 3/5/6/8 Hearts at T2/T3/T4/T5-ish). Stonewrath (T2+: spawns 3 Clotgoyles, each reduces max Hearts by 1; boss flies up and slams a location after 2 s). Killer Spring (T3: click-compact within 15 s alternating left/right click). Impel (T3+: orders CLICK UP/DOWN, JUMP, SNEAK within 2.5 s at T3, 2 s at T4; 5 Hearts damage at T3, 20 at T5). Blood Ichor (T5: spawns an ichor to destroy within 25 s) |
| Steak Stake | an item that executes the boss at a health threshold, +1000 Rift Damage (wiki) |
| Stats of executions | Steak Stake damage "+1000 Rift Damage" per use at the per-tier threshold; thresholds not listed (?) |
| Net cost | Motes earned from killing the boss can pay for the start cost (wiki) |
| Slayer XP levels | L1 20 XP, L2 75, L3 240, L4 840, L5 2,400 (wiki). Rewards: L1 rank "Noob" +20 Rift Time +15 SB XP; L2 "Novice" +30, +25; L3 "Alum" +45, +35; L4 "Heartpiercer" +60, +50; L5 "Lord of Night" +80, +75 (wiki). Only 5 levels shown here; a "level 6-7" is not on the page (?) |
| Level unlocks | Slayer 2: Juicy Healing Melon; 3: Bloodbadge, Leggings of the Coven, Perma-Jelled Garlic-Flavored Gummy Bear; 4: Luscious Healing Melon, Weirder Tuba; 5: Vampire Dentist Relic; level 10 (Soultwist Rune requirement, ?: separate cosmetic gate) (NEU "Requires X Slayer N" lines) |
| Drops | T1: Coven Seal 8-10 (100%), Sangria Dye 0.001%. T2: Seal 10-16, Quantum bundle 13.33%, Soultwist Rune 11.76%, Bubba Blister 10%, Fang-tastic Chocolate Chip 10%, Dye 0.00125%. T3: Seal 28-48, Quantum 12.5%, Rune 12.5%, Bubba 9.37%, Chip 9.37%, Guardian Lucky Block 6.25%, Dye 0.00167%. T4: Seal 52-64, Quantum 12.20%, Rune 10.87%, Bubba 9.15%, Chip 9.15%, Lucky Block 6.10%, McGrubber's Burger 1.22%, Unfanged Vampire Part 1.22%, Dye 0.0025%. T5: Seal 64-128, Quantum 11.98%, Rune 10.70%, Bubba 8.98%, Chip 8.98%, Lucky Block 5.99%, Burger 1.20%, Unfanged Part 1.20%, The One IV bundle 1.80%, Dye 0.01% |
| Drop items | Bubba Blister and Fang-tastic Chocolate Chip are 9-stack power stones (Thaumaturgist, Hub); Guardian Lucky Block upgrades Guardian pets to mythic at Kat; Unfanged Vampire Part crafts the Vampire Dentist Relic; Sangria Dye is a LEGENDARY dye |
| Boss skin | player-model mob, skin `riftwalker_bloodfiend.png` (NEU mobs/riftstalker_bloodfiend.json) |

Hub-side vampire gear from the slayer: Vampire Mask (RARE helmet, Defense 111, Rejuvenate: drains 5 health/s from monsters within 8 blocks), Vampire Witch Mask (EPIC, Health 132, Defense 222, Bat Swarm ability 3,000 damage); Vampiric Vitality enchant (Kuudra/Bazaar).

## 8. Rift-only items, accessories, and pets

Sources: NEU items (stats and recipes quoted above); wiki lore pages

| Group | Items |
|---|---|
| Armor/helmets | Wyld armor set (4 pieces), Wyld Sword (Rift Damage +2), Not-Dead-Gehog Mask (Wyld Helmet + 10 Deadgehog Spine, NEU grid), Agaricus Cap Cap (Not-Dead-Gehog Mask + 50 Agaricus Cap), Chicken Leggs (Wyld Leggings + 16 Volt Crux + 32 Metaphoric Egg), Gunther's Sneakers (Wyld Boots + 8 Shadow Crux + Gunthesizer Lichen), Exceedingly Comfy Sneakers (49,999 Motes + Gunther's Sneakers), Disinfestor Gloves, Leech Belt, Anti-Bite Scarf, Wizard Face, Lively Sepulture Chestplate, Wizardman Leggings, Detransfigured Mask, Leggings of the Coven, Living Metal armor set |
| Crux accessories | Crux Talisman 8 Shy Crux, then upgrades chain: Ring (16 Shadow Crux), Artifact (16 Volt Crux), Relic (16 Scribe), Heirloom (24 Frosty), Chronomicon (32 Splatter), Celestial Starstone (128 Puff Crux, Divine, +28 Accessory Power). Kill milestones every 10 kills of the matching mob give +5 Rift Time and +5 Crux Fortune (bonus crux chance) |
| Other accessories | Respiration Talisman/Ring/Artifact (Puff Crux), Blood Donor Talisman/Ring/Artifact (Hemovibe to Hemoglass to Hemobomb, Health Regen +1/+2/+3), Bluetooth Ring (Living Metal Heart), Big Brain Talisman (Int +1), 2 IQ Points (9 IQ Points), IQ Point (+1% Int), Tiny Dancer (Crit Damage +1%), Test Bucket Please Ignore (True Defense +0.5), Miniaturized Tubulator (Health +1), Hocus-Pocus Cipher (+65 Rift Time), Future Calories Talisman (Tracking), Scarf's Studies/Thesis/Grimoire (Dungeon class XP +2/4/6%), Vampire Dentist Relic, Rift Prism, Bingo Rift Talisman, Perma-Jelled Gummy Bear (+5 combat XP in the Rift) |
| Weapons/tools | Berberis Blowgun (20 Wilted Berberis x3 in a column; arrow, no damage except to chickens and Puffs), Horsezooka, Wand of Farming, Wizard's Breadcrumbs, Silkwire, Larva Hook, Dead Cat Detector ("Meownar" ping, 9s cooldown), Aspect of the Leech (teleport 3/4/5 blocks, mana 60/60/50), Leech Sword (regens 3 mana on hit), Time Gun, Steak Stake |
| Consumables | Speed Elixir (Speed I 5:00), Jump Elixir (Jump Boost I 0:20), Strength Elixir (Strength 10:00, +3 Rift Damage), Stability Elixir (stays integral when inverted), Gravity I splash (10:00, more Puffs), Agaricus Soup (Speed I 60s), Caducous Legume (+90 Rift Time), Healing Melons, Anti-Morph Potion, Plumber's Bucket (infinite water), Riftwart Roots (Kat pet item) |
| Materials | Odonata bottle, Lil' Pad, Deadgehog Spine, Muted Bark, Larva Silk, Wilted Berberis (Bunch=4), Caducous Stem (Bunch=4), Agaricus Cap (Bunch=4), Lush Berberis, Metaphoric Egg, Proto-Chicken, Living Metal, Living Metal Heart, Nullified Metal, Null Sphere/Ovoid/Atom/Edge/Blade chain, Frosty/Shy/Shadow/Volt/Scribe/Splatter/Puff Crux, Hemovibe/Hemoglass/Hemobomb, Vampiric Melon (Enchanted = 160), Coven Seal, Bacte Fragment, Leech Supreme Fragment, Ectoplasm, Bloodbadge, Cruxmotion (one of each crux) |
| Timite line | Timite/Youngite/Obsolite (RARE/UNCOMMON/EPIC collection items): Highlite (16 each of Youngite x2, Timite x2, Obsolite centre, sells for 25,000 Motes), Discrite (64 each), Spotlite (see section 2) |
| Rift export goods | Exportable Carrots (3,000 to Carrolyn in Scarleton: +5 Health, +12 Carrot Fortune), Berberis Fuel Injector (minion upgrade), Caducous Feeder (1,650,000 pet XP, 2 charges, 20 h reload), Wriggling Larva, Nothing-in-a-Bottle (Scatha), Placeable Fairy Soul, Dead Cat Food, Zoop the Fish, Slices of cake (event) |
| Pets | **Montezuma** (Fractured Soul pet): RARE at Level 100 from Sad Jacquelle after the first piece; becomes EPIC at 6 pieces. Nine Lives: +15 Rift Time per piece (RARE with 1/9: +25s per NEU lore); Trickery (EPIC): +2 Mana Regen per piece. EPIC with 6 pieces: +100 Rift Time, +12% Mana Regen. Pieces: Wyld Woods (-93.5, 69, 112.5), Rift Gallery (0, 56, 63.5), Leeches Lair behind waterfall (-139.7, 45, 15), West Village rooftop (-62.5, 88, -81.5), Dreadfarm behind wall (Horsezooka) (-24.5, 72, -198.5), Village Plaza bookshelf in flower shop (2.5, 63, -16.5), Déjà Vu Alley floating island (38.5, 65, -117.5), Living Stillness under snow (22.5, 43, -219.5), Photon Pathway second floor (152.5, 80, -13.5). **Rift Ferret** (EPIC combat pet from Kat: Riftwart Roots 12 + 20 Fly + 10 Spider + 20 Silverfish): -Intelligence, +Speed, "Orbs are Fun": +10% XP from XP orbs. Bat Vampire pet skin; Vampire Fang (mythic Bat pets at Kat) |

## 9. Other systems a 1:1 clone needs

Sources: NEU, wiki

| System | What is known |
|---|---|
| Rift Guide | menu with per-area task lists (NEU `rift_guide.json`: 9 areas, ids like `rift_accessory_1`, `rift_boat_1`, `rift_dead_cat_N`, `rift_eye_N`, `rift_cave_N`; each has name, description, wiki link) plus an "Enigma Souls" row and an "Eleanor" row |
| Reed's Boat | 5-part quest: Powerful Glue (Wyld Woods), Replacement Wood (Dreadfarm), Tasty Provisions (Village Plaza), Living Anchor (Living Cave, Living Metal Anchor = 128 Living Metal + 9 Living Metal Hearts), Powerful Glue (Colosseum) (NEU). Boat item gives Rift travel on water (?) |
| Porhtal eyes | seven eyes, each calmed by 300 damage; permanent Rift Time and fast travel: Intruder (Wizard Tower, +10, 5 s trip), Gill-Man (Lagoon Hut, +20, 10 s), Baba Yaga (Dreadfarm, +30, 10 s), Bankster (Village Plaza, +40, 15 s), Gooey (Colosseum, +50, 20 s), Prince (Château, +60, 25 s), 7th Sin (Mountaintop, +70, 30 s). Trips cost Rift Time (5 s per warp, summed if routed) (wiki) |
| Rift Transfer | Inverted Sirius moves transferable items in and out; 52 transferable, 7 exportable-with-loss (section 1) |
| Auction | Shen's Auctions at Barrier Street, yearly, Motes bids |
| Mayors | Diaz and Aura perks touch the Rift (wiki lists the names only, ?) |
| Events | Breach Close event, Dec 10 2024 (memento) (NEU); calendar events inside the Rift not found (?) |
| Wither Cage | Wyld Woods Enigma Soul at Broken Cage: interact with the Wither Skull (wiki); the page itself 404'd |
| Mob Hearts | Château mobs deal Hearts damage; Rift Time loss otherwise |
| SkyBlock XP | each timecharm +100; Vampire levels 15/25/35/50/75; Rift SkyBlock Level 12 gate |
| Collections | Timite, Wilted Berberis, Caducous Stem, Agaricus Cap, Living Metal Heart, Hemovibe etc. are collection items (NEU "Collection Item") |
| Cosmetic furniture | Vampire Bed, Candelabra, Sofa, Table, Throne, Generators 1-11 (Halloween) |

## 10. Open questions

- Exact Enigma Soul total and per-area counts (wiki says 52 in places, 47 in one summary, NEU says 42; area sums did not match). Verify the real count before coding.
- Rift Time gained per monster kill and per kill drained by melee hits; the Rift Time penalty tables per mob.
- Mote sell prices per item at the Motes Grubber (only Living Metal 5, Highlite 25,000 and the Vampiric Melon/Enchanted values found).
- Enigma upgrade cost in Motes per 4 souls.
- Colosseum Rift: layout, coordinates, Autoboots stats, how much of Bacte's 6,000 vs 10,000 Motes is right.
- Area footprints/sizes; only a single anchor coordinate per area; Mirrorverse position; Rift Gallery contents; Time Chamber layout; Colosseum position.
- Rift Necklace stats by timecharm count and the exact cost of the Enigma Cloak upgrades.
- Vampire Slayer: boss spawn mechanic (kills needed), Steak Stake thresholds per tier, the full Twinclaws/Mania numbers per tier, level 6 and 7 rewards, Clotgoyle stats.
- Per-mob damage values (only Deadgehog 9 arrow / 2 melee found); Shy/Shadow/Volt/Scribe/Frozille/Puff attack damage.
- Starting inventory rules on entry (does the player keep their armor/hotbar), what happens on death outside the Château, the exit mechanic.
- Rift calendar events, mayor perks (Diaz, Aura), Wither Cage details, Rift Transfer Chest rules (pages 404'd).
- Living Cave "lights" puzzle: not found anywhere.
- Hub Wizard Tower coordinates are from the post-Feb-2026 redesign; the Hub in this mod may differ.
