# Crimson Isle and Kuudra research

Source note: numbers come from the community mirror https://hypixelskyblock.minecraft.wiki/w/<Page> (read through a lossy summarizer, so exact figures can be wrong) and from the NEU repo
(`tools/skyblock/cache/neu-repo.zip`: constants/trophyfish.json, essencecosts.json, bestiary.json, attribute_shards.json, zones.json, islands.json; items/*.json lore).
Several wiki pages 404'd (Crimson_Isle_Quests, Dojo test sub-pages) and the Kuudra page had no health numbers. Where I filled a gap from general game knowledge the line is marked (?).
Anything marked (?) must be verified before being coded as a hard number. Format follows docs/design/research/dungeons.md.

---

## 1. Getting there and the island

Sources: https://hypixelskyblock.minecraft.wiki/w/Crimson_Isle , /w/Elle ; NEU constants/zones.json, islands.json

| Rule | Value |
|---|---|
| Island name | "Crimson Isle" (NEU island id `crimson_isle`); the Kuudra fight is a separate instance "Kuudra's Hollow" (id `kuudra`) |
| Combat level to enter | Combat 22 (wiki) |
| Way in | Launch Pad in Spider's Den (wiki). The older boat/Nether-portal route from the Hub is not confirmed on any fetched page (?) |
| Travel scrolls | Crimson Isle (hub), Kuudra Passage, Smoldering Tomb, Dragontail, Scarleton, and a "Dusty Travel Scroll to the Kuudra Skull" (Forgotten Skull) |
| Home portal | item KUUDRA_SKULL_PORTAL: placed on your private island, makes a portal to Forgotten Skull |
| Sub-locations | 21 listed: Barbarian Outpost, The Bastion, Blazing Volcano, Burning Desert, Cathedral, Crimson Fields, Dojo, Dragontail, Forgotten Skull, Kuudra's Hollow, Mage Outpost, Magma Chamber, Mystic Marsh, Odger's Hut, Ruins of Ashfang, Scarleton, Stronghold, The Wasteland, Matriarch's Lair, Belly of the Beast, Aura's Lab |
| Lava fishing | Fishing level X (10) wanted for the first lava rod; fishing-speed cap is 350 here (vs 300 elsewhere); starter rod "Starter Lava Rod" |
| Layout | Barbarians on the left side with hub Dragontail, Mages on the right side with hub Scarleton; Stronghold and Forgotten Skull in between (?) |
| Settlements | Dragontail and Scarleton each have Bank, Auction House, Bazaar, Blacksmith, Minion Shop (NEU zones.json) |
| Resources | Netherrack, Nether Quartz, Nether Wart, Red Sand, Sulphur ore, Mycelium, Rough Opal gemstone |
| Wiki counts | 130+ NPCs, 80+ mobs documented |

### Areas and their mobs

Levels come from the NEU bestiary ids (the number at the end of each mob id is its level); HP numbers from wiki search snippets. Area attribution is partly from general knowledge, marked (?).

| Area | Mobs (level) | Notes |
|---|---|---|
| Crimson Fields (spawn region) | Magma Cube (75, ~1M HP), Pack Magma Cube (90, ~1.5M HP), Fireball Magma Cube (75), Magma Cube Rider (90) (?) | open starting field |
| Stronghold / Barbarian side | Blaze (25, 70), Bezal (80, ~1M HP), Mutated Blaze (70), Wither Skeleton (70), Wither Spectre (70) (?) | Elle first meets you here fighting Blazes; Bladesoul miniboss |
| Mage side (Scarleton, Mage Outpost, Cathedral, Courtyard) | Mage Outlaw miniboss | Courtyard |
| Barbarian side (Dragontail, Barbarian Outpost, The Dukedom) | Barbarian Duke X miniboss | The Dukedom |
| Burning Desert | Flaming Spider (80), Kada Knight (90, ~2M HP) (?) | Skeleton Fish zone (with Dragontail) |
| Blazing Volcano | Millennia-Aged Blaze (110), Smoldering Blaze (95), Flare (90), Ghast (85), Dive Ghast (90) (?) | geysers and eruptions; best lava fishing zone |
| Mystic Marsh | Mushroom Bull (charging mushroom cow, level 80, ~2.5M HP) | Moldfin zone (with Scarleton) |
| Smoldering Tomb | Wither Spectre (70), Wither Skeleton (70), Matcho (100), Tentacle/Hellwisp (100) (?) | crypt area; Vanquisher can roam anywhere |
| Magma Chamber | Magma Boss; Kada Knights in the caves north and south (level 90, 2M HP) | boss at the central lava pool |
| Ruins of Ashfang | Ashfang miniboss | |
| Forgotten Skull | no mobs of note | hub for the Kuudra entrance, Vulcan shop |
| Odger's Hut / Dojo | NPCs only | Dojo: Karate Fish zone |
| The Wasteland, Matriarch's Lair, Belly of the Beast, Aura's Lab | no mob data fetched | the last three belong to the Kuudra fight (?) |

### Minibosses and bosses

Sources: /w/Crimson_Isle, /w/Magma_Boss, /w/Crimson_Essence

| Boss | Location | Level | Health | Spawn | Essence |
|---|---|---|---|---|---|
| Bladesoul | Stronghold | 477 | 50,000,000 | every 2 min (island page) | 25 |
| Mage Outlaw | Courtyard | 515 | 70,000,000 | every 2 min | 25 |
| Barbarian Duke X | The Dukedom | 486 | 70,000,000 | every 2 min | 25 |
| Ashfang | Ruins of Ashfang | 508 | 50,000,000 | every 2 min | 50 |
| Magma Boss | Magma Chamber | 523 | 160M (boss page) or 200M (island page) | every 2 or 5 min (conflict) | 50 |
| Vanquisher | anywhere, random | 100 | 5,000,000 | random; chance raised by Kuudra pet "Wither Bait" and the Wither Piper perk | not stated |

Magma Boss details (boss page): damage 2,000; Damage Soaked meter (yellow +20%, orange +25%, red +35% boss damage); at least 250,000 damage needed for drops; combat XP 3,922; coins 1,307; 1,569 orbs; spawn time synced across servers.
Drops (100% unless noted): Compact Ooze, Rekindled Ember Fragment, 50 Crimson Essence, 20-35 Magma Cream; up to 50% Enchanted Magma Cream; Epic Magma Cube Pet 1.5%, Legendary 0.4%, Fire Fury Staff 0.5%, Molten Cube 0.5%.
Island mob drops in general: Blaze Rods, Magma Cream, enchanted books, Fire Veil Wand, Fire Fury Staff, armour pieces, essences, trophy fish.

### Bestiary brackets

Source: NEU constants/bestiary.json (keys `crimson_isle` and `kuudra`). Kills needed for bestiary levels 1..10 per bracket:

| Bracket | Kills for levels 1..10 | Mobs here |
|---|---|---|
| 2 | 5, 10, 15, 25, 50, 100, 200, 350, 500, 750 (cap 25,000) | Kuudra Follower |
| 3 | 4, 8, 12, 16, 20, 40, 80, 140, 200, 300 (cap 10,000) | Flaming Spider, Flare, Smoldering Blaze, Kuudra Berserker/Knocker/Landmine, Blight, Inferno Magma Cube |
| 4 | 2, 4, 6, 10, 15, 20, 25, 35, 50, 75 (cap 1,000-3,000) | Blaze, Kada Knight, Magma Cube, Magma Cube Rider, Millennia-Aged Blaze, Mushroom Bull, Wither Spectre, Explosive Imp, Wandering Blaze |
| 5 | 1, 2, 3, 5, 7, 10, 15, 20, 25, 30 (cap 1,000) | Ghast, Wither Skeleton |
| 6 | 1, 2, 3, 5, 7, 9, 14, 17, 21, 25 (cap 250) | Matcho, Tentacle, Vanquisher, Kuudra Slasher, Magma Follower, Wither Sentry, Blazing Golem, Dropship |
| 7 | 1, 2, 3, 5, 7, 9, 11, 14, 17, 20 (cap 100) | Ashfang, Barbarian Duke X, Bladesoul, Mage Outlaw, Magma Boss |

---

## 2. Factions

Sources: https://hypixelskyblock.minecraft.wiki/w/Factions , /w/Mage_Faction , /w/Barbarian_Faction , /w/Elle

| Rule | Value |
|---|---|
| Choice | partially complete the main questline with Elle; talk to NPCs of both factions, then pick one |
| Mage | right side of the island, leader Queen Nyx, blacksmith Alwin, hub Scarleton |
| Barbarian | left side, leader Chief Scorn, blacksmith Igor, hub Dragontail |
| Switching | "can swap at any time" (wiki); killing members of the opposing faction costs 5 reputation per kill |
| Display | faction icon next to your name in the tab list |
| Per faction | own Bank, Auction House, Bazaar, Blacksmith, Minion shop, guard-patrolled town, Community Centre |
| Reputation storage | (?) whether reputation is kept separately per faction when you switch is not confirmed |

### Reputation ranks

| Rank | Reputation | SkyBlock XP on reaching it |
|---|---|---|
| Neutral | 0 | - |
| Friendly | 1,000 | 5 |
| Trusted | 3,000 | 10 |
| Honored | 7,000 | 20 |
| Hero | 12,000 | 40 |

| Unlock | Reputation needed |
|---|---|
| Hot Kuudra key purchase | 1,000 |
| Burning Kuudra key | 3,000 |
| Fiery Kuudra key | 7,000 |
| Infernal Kuudra key | 12,000 |
| Faction NPC shop items (rank-gated gear, cosmetics) | (?) Friendly to Hero, specifics not fetched |

---

## 3. Faction quests and reputation sources

Sources: /w/Factions, /w/Mage_Faction, /w/Barbarian_Faction (the page /Crimson_Isle_Quests 404'd)

Main questline (Elle): 1) in the Stronghold she wants 4 Magma Chunks, 3 Blaze Ashes, 2 Spectre Dust; 2) talk to both factions' NPCs; 3) faction path:
Mage path: obtain a Maddox Batphone for the Dean, then recover stolen research from the Barbarian Bastion. Barbarian path: kill a Corrupted Flaming Spider for Sulphur, rescue Ugo, recover research from the Mage Cathedral.
4) both paths end at the Forgotten Skull where Elle takes you to Kuudra (Basic tier). The full faction questline is required before Elle takes you.

### Daily and repeating reputation sources

| Source | Faction | Reputation | Frequency |
|---|---|---|---|
| Fight Rollim (combat daily) | Mage | +25 | daily (wiki wording hints at a 7-day cycle of fights) |
| Fight Kaus (combat daily) | Barbarian | +25 | daily |
| Pablo flower quest | Mage | +25 | every 2 days |
| Avorius, Cup of Blood | Mage | +5 | every 3 days |
| Pomtair, Enchanted Bone Meal | Barbarian | +50 | daily |
| Sirih, Sulphur | Barbarian | (?) | every 2 days |
| Suus, toy recovery | Barbarian | (?) | every 5 days |
| Mollim | Mage | one-time Titanic Experience Bottle bonus | once |
| Kill a miniboss | both | +20 each | daily per miniboss (?) |
| Kuudra clear | both | +10 / 20 / 30 / 40 / 50 (Basic to Infernal) | daily per tier (?) |

| Quest type asked about | Evidence |
|---|---|
| Kill mobs / miniboss | confirmed (+20 per miniboss, Rollim/Kaus duels) |
| Fetch items | confirmed (Bone Meal, flowers, Cup of Blood, Sulphur) |
| Kuudra | confirmed (+10..+50 per tier) |
| Fish | (?) not confirmed from fetched pages |
| Dojo | (?) not confirmed |
| Coins and XP per quest | not stated; rewards are reputation (SkyBlock XP only at rank-ups) |
| Number of dailies per day | not stated (?) |

---

## 4. The Dojo (Master Tao)

Sources: https://hypixelskyblock.minecraft.wiki/w/Dojo ; NEU items DOJO_*_BELT

| Rule | Value |
|---|---|
| NPC | Master Tao (wiki; the name "Chiyeda" was not found) |
| Entry | empty inventory and no armour equipped (the arena gives you the items); pets disabled |
| Scoring | each challenge produces a score; 1000+ points = rank S and rewards SkyBlock XP; full grade scale (A/B/C/D/F thresholds) not fetched (?) |
| Belt progress | total points across the best result of each of the 7 tests |

### The 7 challenges

| Test | Rules | Scoring notes |
|---|---|---|
| Force | knock zombies into lava; helmets show value; avoid negative-point mobs | iron helmet 10, gold 20, diamond (baby) 30; negative mobs subtract |
| Stamina | jump through open holes in moving walls; do not be pushed into lava | points for time survived on the platform |
| Mastery | shoot targets with a bow before they disappear | green 8, yellow 16, red 32; 2 minutes |
| Discipline | hit each zombie with the sword matching its helmet; new sword type every 30 s | wood, iron, gold, diamond sword to helmet colour; penalty for wrong sword (?) |
| Swiftness | temporary blocks spawn; stand on the green wool, avoid red | points over time on safe blocks |
| Control | stare at a moving skeleton; accuracy gives points and bonus time | accuracy-based |
| Tenacity | ghasts shoot fireballs, avoid them; up to four ghasts spawn over time | points for survival time |

Run durations other than Mastery's 2 minutes: (?).

### Belts

| Belt | Total points | Combat XP reward | Belt stats (NEU lore) |
|---|---|---|---|
| White | start | - | +10 Health, +5 Defense, -0.5% damage taken |
| Yellow | 1,000 | 80,000 | (?) |
| Green | 2,000 | 100,000 | (?) |
| Blue | 4,000 | 120,000 | (?) |
| Brown | 6,000 | 140,000 | (?) |
| Black | 7,000 | 160,000 | +35 Health, +25 Defense, -6% damage taken, LEGENDARY |

Belts are right-click equipped, reforgeable, soulbound. Master Tao shop items and prices: not on the page (?). Karate Fish (trophy) is caught at the Dojo.

---

## 5. NPCs and shops

Sources: NEU items (*_NPC.json; most have empty lore), /w/Elle, /w/Odger, /w/Factions, /w/Kuudra_Key

| NPC | Where | What |
|---|---|---|
| Elle | Stronghold, later Forgotten Skull | guide NPC, runs the main questline, takes you into Kuudra; Elle's Lava Rod (NEU: Damage +50, Strength +50, Fishing Speed +8) and Elle's Pickaxe exist as items (reward source (?)) |
| Odger | Odger's Hut | introduces Trophy Fish, tracks them, fillets them into Magmafish, rank rewards, shop, Abiphone contact |
| Barbarian Emissary / Mage Emissary | faction hubs | sell the Kuudra Keys (section 8) |
| Queen Nyx / Chief Scorn | Scarleton / Dragontail | faction leaders, quest chain |
| Alwin / Igor | Scarleton / Dragontail | faction blacksmiths |
| Master Tao | Dojo | challenges, belts |
| Vulcan | Forgotten Skull (and Dungeon Hub) | Crimson Essence shop: Strongarm 1,000-2,000, Fresh Tools 200-1,000 per tier, Crimson Training 250-5,000, Wither Piper 500-3,000 |
| Rollim / Kaus | Mage / Barbarian | combat dailies, +25 rep |
| Pablo, Avorius, Mollim | Mage side | flower, Cup of Blood, one-time bonus quests |
| Pomtair, Sirih, Suus | Barbarian side | Bone Meal, Sulphur, toy quests |
| Bubu | Crimson Isle NPC (NEU item, empty lore) | role not confirmed (?) |
| Aranya | Crimson Isle NPC (NEU item, empty lore) | role not confirmed (?) |
| Edwin | not found in the NEU repo | role not confirmed (?) |
| Kuudra Gatekeeper, Believer, Loremaster, Archaeologist | Forgotten Skull / hollow | NEU items exist without text; likely lore/entrance NPCs (?) |
| Weird Sailor | listed on the Kuudra's Hollow page | (?) |
| Croesus | Dungeon Hub | re-open unclaimed Kuudra chests (shared with dungeons.md) |
| Chantelle | Crimson Isle | only during the Carnival event with Foxy as mayor; out of scope |

---

## 6. Crimson Isle fishing and Trophy Fish

Sources: https://hypixelskyblock.minecraft.wiki/w/Trophy_Fishing , /w/Odger ; NEU constants/trophyfish.json, items ODGERS_*_TOOTH

### Basics

| Rule | Value |
|---|---|
| Fluid | lava only, needs a Lava Rod (Starter Lava Rod, Elle's Lava Rod, Inferno Rod ...) |
| Fishing level | X (10) |
| Fishing speed cap | 350 on the Isle |
| Lava sea creatures | 14 species, Fishing X to XLV; names not fetched (?) |
| Trophy fish | a catch can turn into a trophy fish instead of a sea creature; Trophy Chance stat (Odger's teeth) raises it |
| Tier odds | Bronze 73.353%, Silver 24.451%, Gold 1.996%, Diamond 0.2% (with Charm VI: 70.23 / 27.31 / 2.235 / 0.224) |
| Pity | gold guaranteed after 100 catches of the same fish, diamond after 600 |

### Trophy fish list (18 entries)

| Fish | Rarity | Base chance | How or where |
|---|---|---|---|
| Blobfish | Common | 25% | anywhere |
| Gusher | Common | 20% | Blazing Volcano, 10-15 min after an eruption |
| Obfuscated 1 | Common | 25% | Corrupted Bait or corrupted sea creatures |
| Steaming-Hot Flounder | Common | 20% | Blazing Volcano, bobber within 2 blocks of a geyser |
| Sulphur Skitter | Common | 30% | within 4 blocks of Sulphur Ore |
| Flyfish | Uncommon | 8% | Blazing Volcano, bobber at least 8 blocks below the player |
| Obfuscated 2 | Uncommon | 20% | using Obfuscated 1 as bait |
| Slugfish | Uncommon | 15% | bobber active at least 20 s |
| Lavahorse | Rare | 4% | anywhere |
| Mana Ray | Rare | variable | at least 1,200 Mana |
| Obfuscated 3 | Rare | 10% | using Obfuscated 2 as bait |
| Vanille | Rare | 8% | Starter Lava Rod with no enchantments |
| Volcanic Stonefish | Rare | 4% | Blazing Volcano |
| Karate Fish | Epic | 2% | Dojo |
| Moldfin | Epic | 2% | Mystic Marsh or Scarleton |
| Skeleton Fish | Epic | 2% | Burning Desert or Dragontail |
| Soul Fish | Epic | 2% | Stronghold |
| Golden Fish | Legendary | varies | chance to surface after 8 minutes of continuous fishing |

### Filleting: Magmafish per fish (bronze / silver / gold / diamond)

Source: NEU constants/trophyfish.json (the wiki table lists Lavahorse diamond as 24, NEU says 40 (?)).

| Fish | Bronze | Silver | Gold | Diamond |
|---|---|---|---|---|
| Blobfish | 4 | 8 | 12 | 16 |
| Lavahorse | 12 | 16 | 20 | 40 (wiki 24) |
| Steaming-Hot Flounder, Volcanic Stonefish | 20 | 28 | 40 | 60 |
| Gusher, Flyfish, Moldfin, Skeleton Fish, Soul Fish | 32 | 48 | 64 | 96 |
| Obfuscated 1 | 16 | 24 | 32 | 48 |
| Obfuscated 2, Sulphur Skitter, Slugfish, Mana Ray, Karate Fish | 40 | 60 | 80 | 120 |
| Vanille | 80 | 120 | 160 | 240 |
| Obfuscated 3, Golden Fish | 400 | 700 | 1000 | 1300 |

### Odger's rewards

| Trophy Fisher rank | Requirement | Reward |
|---|---|---|
| Novice | 15 Bronze-or-better trophy fish | Bronze Hunter Armor, Odger's Bronze Tooth, Trophy Fishing Sack, 20 Magmafish variants |
| Adept | Silver or better of all fish | Silver set and Silver Tooth |
| Expert | Gold or better of all fish | Gold set and Gold Tooth |
| Master | all tiers of all fish | Diamond set, Diamond Tooth, "Diamond the Fish" pet |

Teeth are accessories (Diamond Tooth: Sea Creature Chance +2%, Trophy Chance +4%, Accessory Power 12, needs Master Trophy Fisher). Stats of the other teeth, Hunter armour stats and Magmafish uses are not fetched (?).

---

## 7. Inferno Demonlord (Blaze slayer)

Sources: https://hypixelskyblock.minecraft.wiki/w/Inferno_Demonlord ; NEU items INFERNO_DEMONLORD_1..4_BOSS, DEMONLORD_GAUNTLET

| Tier | Boss level (wiki) | Health | Cost (coins) | Slayer XP | Demons health (NEU) | Demons dmg/s (NEU) |
|---|---|---|---|---|---|---|
| I | 436 | 2.5M | 10,000 | 5 | 500,000 | 2,500 |
| II | 551 | 10M | 25,000 | 25 | (?) | (?) |
| III | 695 | 45M | 60,000 | 100 | (?) | (?) |
| IV | 825 | 150M | 150,000 | 500 | 10,000,000 | 30,000 |

Requirements: the first tier needs Voidgloom Seraph Tier III completed (wiki); each next tier needs the previous Demonlord tier. The per-tier numbers the wiki summary labelled "combat XP req" (6,000 / 10,000 / 14,000 / 28,000) are unclear (?).
Demonlord Gauntlet (NEU): Health +40, Defense +20, True Defense +20, Crit Damage +25%, 1.15x damage vs Infernal mobs, needs Blaze Slayer 4.

### Mechanics

| Mechanic | Detail |
|---|---|
| Immolate (passive) | true damage per second to you: T1 "100 + 12% of your max HP", T4 "100 + 50% of your max HP" (NEU) |
| Demonsplit | T1 at 50% HP splits into Quazii and Typhoeus; T3/T4 at 66% and 33% |
| Hellion Shield (T2+) | boss takes 99% less damage unless your dagger attunement matches; attunements cycle Ashen, Spirit, Auric, Crystal; the shield changes every 8 correct hits |
| Fire Pillars (from 66% HP) | destroy within 7 s or take lethal damage |
| DDR Apocalypse (from 33% HP) | fire pits to dodge; 950 true damage/s in T3, 2,865 in T4 |
| Extras | T3/T4: Quazii has a ROFLcopter beam, Typhoeus has Trail of Fire |

Wiki Immolate numbers (100% / 66% / 33% HP): I 45/65/115, II 65/130/190, III 80/160/320, IV 385/575/955. They conflict in form with the NEU lore (percent of max HP), so they are probably values at a default HP (?).

### Drops

| Drop | Notes |
|---|---|
| Derelict Ashe | guaranteed; amounts 6-8 (T1) up to 110-155 (T4) |
| Inferno Demonlord Shard, Archfiend Dice, Kelvin Inverter, Gabagool Distillate | rare drops, chances not fetched (?) |
| Flame Dye | 1 in 10M (T1) down to 1 in 500k (T4) |
| Inferno minion fuels and Inferno Apex | NEU items INFERNO_FUEL_*, HEAVY_*, HYPERGOLIC_*, INFERNO_APEX (made by Inferno minions on Hypergolic fuel) |

Blaze Slayer level XP thresholds and level perks: not fetched (?). Where the boss spawns (which area) and the quest-trigger kill count: not fetched (?).

---

## 8. Kuudra

Sources: https://hypixelskyblock.minecraft.wiki/w/Kuudra , /w/Kuudra_Key , /w/Elle , Crimson Armor pages; NEU items KUUDRA_*, bestiary `kuudra`

### Entering

| Rule | Value |
|---|---|
| Unlock | complete your faction's questline; Elle takes you from the Forgotten Skull to the Basic tier |
| Party | 4 players (Crimson_Isle page "4-player"); solo allowed (?) |
| Key | the paid chest needs the matching Kuudra Key; the free chest needs none |
| Reputation | tiers above Basic need faction reputation at the rank thresholds |
| SkyBlock XP | per clear 20 / 40 / 60 / 80 / 100 |
| Time limit | not on the wiki page (?) |
| Instance zones | Kuudra's Hollow; Matriarch's Lair, Belly of the Beast, Aura's Lab are listed zones (?) |

### Tiers

| Tier | Reputation needed | SkyBlock XP | Seconds between mob waves | Phases used | Crimson armour requirement |
|---|---|---|---|---|---|
| Basic | 0 | 20 | 35 | 1-3 | Combat 22 and Basic clear |
| Hot | 1,000 | 40 | 30 | 1-3 | Combat 27 and Hot clear |
| Burning | 3,000 | 60 | 25 | 1-4 | Combat 32 |
| Fiery | 7,000 | 80 | 20 | 1-4 | Combat 37 |
| Infernal | 12,000 | 100 | 15 | 1-5 | Combat 42 |

Kuudra health per tier: not on the wiki page. My recollection (all (?)): Basic ~100k, Hot ~500k, Burning ~3M, Fiery ~15M, Infernal ~240M. Kuudra mobs in the bestiary come in five level steps (100/200/300/400/500, Basic to Infernal).

### Keys

Sold by the Barbarian Emissary (Enchanted Red Sand) or Mage Emissary (Enchanted Mycelium) in your own faction's town. Keys are co-op soulbound. Each key can also be crafted from 4 of the previous tier.

| Key | Enchanted Red Sand / Mycelium | Nether Stars | Coins | Reputation | Craft alternative |
|---|---|---|---|---|---|
| Basic (Rare) | 2 | 2 | 200,000 | none | - |
| Hot (Epic) | 6 | 2 | 400,000 | 1,000 | 4 Basic keys |
| Burning (Epic) | 20 | 2 | 750,000 | 3,000 | 4 Hot keys |
| Fiery (Epic) | 60 | 2 | 1,500,000 | 7,000 | 4 Burning keys |
| Infernal (Legendary) | 120 | 2 | 3,000,000 | 12,000 | 4 Fiery keys |

### Phases

| Phase | Name | Tiers | What happens |
|---|---|---|---|
| 1 | Supplies (crates) | all | fish supply crates out of lava with a lava rod, carry them to the centre; Kuudra mobs attack in waves on the timer above |
| 2 | Build (ballista) | all | sneak plus left-click supplies to build the ballista, progress meter to 100% |
| 3 | Fuel | all | fish up 4 fuel cells, mount the ballista and shoot Kuudra |
| 4 | Stomach | Burning and up | players are fired into Kuudra's stomach, break orbs with a pickaxe to stun it |
| 5 | Lair | Infernal | starts when Kuudra reaches 25% HP; dodge tentacles, shoot the head until it dies |

Class perks (wiki): Specialist (Steady Hands, Ballista Mechanic, Bomberman, Mining Frenzy), Berserk (Sweeping Edge, Freezing Touch, Bonus Damage, Antibiotic, Blight Slayer), Mage (Accelerated Shot, Blast Radius, Cannon Proficiency, Multi-Shot, Rapid Fire, Steady Aim), Support (Healing Aura, Mana Aura, Protective Aura, Faster Respawn). Human Cannonball costs 40-80 tokens, Revive perks 500-750 tokens; other prices (?).

Mobs (NEU bestiary `kuudra`): Blazing Golem, Blight, Dropship, Explosive Imp, Inferno Magma Cube, Kuudra Berserker, Kuudra Follower, Kuudra Knocker, Kuudra Landmine, Kuudra Slasher, Magma Follower, Wandering Blaze, Wither Sentry.

### Rewards

| Chest | Contents |
|---|---|
| Free chest | Crimson Essence only, amount scales by tier |
| Paid chest slot 1 | armour pieces (Crimson, Aurora, Terror, Hollow, Fervor), weapon shards, accessories |
| Paid chest slot 2 | enchanted books, pet shards, materials |
| Guaranteed (Infernal paid) | 2,000 Crimson Essence, 3-4 Kuudra Teeth, 1-2 Kraken Shards (wiki summary) |
| Attributes | paid chest guarantees attribute shards; gear can roll attributes (section 10) |
| Chest buy-back | Croesus (Dungeon Hub) for unclaimed chests; time window (?) |
| Other Kuudra items (NEU) | Kuudra Teeth (Mandibles), Kuudra Chunks (Uncommon/Rare/Epic/Legendary), Tentacle, Heart/Lung/Kidney, Cavity, Follower armour, Follower Relic and Artifact, Kuudra Relic, Kuudra Washing Machine, Kuudra pet, Burning/Fiery/Infernal Kuudra Core |

Item stats (NEU lore): Kuudra's Heart accessory: Accessory Power 16, picks up crates 30% faster, builds the ballista 15% faster. Kuudra Follower Relic: Power 16, 65% less damage from Kuudra Followers.
Kuudra pet: Crimson perk (extra Crimson Essence), Wither Bait (more Vanquisher chance), perks work while not summoned, auto Exp Share, gains XP only on the Crimson Isle.
Score ranks (S/A/B/C/D), loot weights and per-chest drop rates are not on any fetched page (?).

### Armour sets

Sources: NEU items (Crimson set lore), NEU essencecosts.json; wiki search results

| Set | Notes |
|---|---|
| Crimson | melee set: Dominus tiered bonus (melee hits every 1.5 s give a stack, lost after 4 s; stacks give swing range, at 10 stacks a swipe; Infernal adds Ferocity and damage). Basic helmet: HP 160, Def 50, Str 30, Crit Dmg 20%, Int 15, 2 gem slots |
| Aurora | (?) mage/caster set |
| Terror | (?) crit/ranged set |
| Fervor | (?) |
| Hollow | (?) mage/ranged set |
| Kuudra Follower | separate drop set from Kuudra Followers |

Crimson stats by tier (NEU lore): Basic helmet 160 HP / 50 Def / 30 Str / 20% CD / 15 Int; Hot helmet 202 / 63 / 38 / 25% / 19; Infernal chestplate 580 HP / 164 Def / 76 Str / 50% CD / 13 Int. Tier prefixes Hot, Burning, Fiery, Infernal. Requirement lines per tier in section 8 tier table. Bonus details for Aurora, Terror, Fervor, Hollow not fetched (?).

---

## 9. Crimson Essence and star upgrades

Sources: https://hypixelskyblock.minecraft.wiki/w/Crimson_Essence ; NEU constants/essencecosts.json, essenceshops.json

### Sources

| Source | Amount |
|---|---|
| Ashfang, Magma Boss | 50 each |
| Barbarian Duke X, Bladesoul, Mage Outlaw | 25 each |
| Kuudra free chest | scales by tier (numbers (?)) |
| Infernal paid chest | 2,000 guaranteed |
| Salvaging crimson-starred gear | refund (?) |
| Boosts | Kuudra pet +15-20%, Lava Leech shard +1-13%, Essence of Crimson attribute shard (+1% per level), Komodo Dragon and Tiamat shards |

### Star costs (Crimson Essence per star, per piece)

NEU: every Kuudra armour piece uses essence type Crimson; 10 stars for Basic to Fiery, 15 stars for Infernal (stars 11-15 Infernal only).

| Tier | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Basic (all 5 sets) | 30 | 35 | 40 | 45 | 50 | 55 | 60 | 70 | 80 | 90 | | | | | |
| Hot | 170 | 190 | 215 | 240 | 270 | 300 | 340 | 390 | 440 | 500 | | | | | |
| Burning | 900 | 1,000 | 1,125 | 1,270 | 1,450 | 1,650 | 1,850 | 2,100 | 2,350 | 2,650 | | | | | |
| Fiery | 5,000 | 5,600 | 6,300 | 7,000 | 8,000 | 9,000 | 10,200 | 11,500 | 13,000 | 14,500 | | | | | |
| Infernal | 30,000 | 35,000 | 41,000 | 48,000 | 56,000 | 65,500 | 76,000 | 89,000 | 105,000 | 120,000 | 140,000 | 165,000 | 192,000 | 225,000 | 265,000 |

Extra materials from star 4 on (same for every tier, pearls differ for Basic):

| Star | Coins | Heavy Pearls (Basic / Hot and up) | Kuudra Teeth |
|---|---|---|---|
| 4 | 10,000 | - | - |
| 5 | 25,000 | - | - |
| 6 | 50,000 | - | - |
| 7 | 100,000 | - | - |
| 8 | 250,000 | 2 / 3 | - |
| 9 | 500,000 | 3 / 4 | - |
| 10 | 1,000,000 | 4 / 5 | - |
| 11 | 2,500,000 | 6 | 20 |
| 12 | 5,000,000 | 7 | 40 |
| 13 | 10,000,000 | 8 | 60 |
| 14 | 25,000,000 | 9 | 80 |
| 15 | 50,000,000 | 10 | 100 |

Stat bonus per star: not in the fetched data (?); assume a flat percent of base stats per star like Dungeons (?).

### Vulcan's shop (perks bought with Crimson Essence)

Strongarm (fishing) 1,000-2,000, Fresh Tools 200-1,000 per tier, Crimson Training (combat wisdom) 250-5,000, Wither Piper (Vanquisher chance) 500-3,000. Full list sits in NEU essenceshops.json under ESSENCE_CRIMSON, not dumped here (?).

---

## 10. Attributes on Crimson gear

Sources: https://hypixelskyblock.minecraft.wiki/w/Attribute (general; nothing Kuudra-specific) ; NEU constants/attribute_shards.json, items ATTRIBUTE_SHARD_*, KUUDRA_WASHING_MACHINE

| Topic | Detail |
|---|---|
| Where from | Kuudra and Dungeons chests, hunting, Huntraps, fusions, Tree Gifts, NPC shops |
| Levels | I to X (1-10) |
| Fusion | most attributes need 5 shards to fuse (Reptilian, Elemental, Amphibian need 2); rarity gated by Hunting level: Common 0, Uncommon 5, Rare 10, Epic 15, Legendary 20 |
| Washing | Kuudra Washing Machine: combine in an anvil with any item to remove all attributes |
| Crimson-relevant shards (NEU) | Essence of Crimson (+1% Crimson Essence per level, Rare), Crimson Serendipity, Kuudra's Box (+5% chance of an extra Kuudra Teeth in the paid chest, Rare), Infernal Ruler (+3% damage vs Infernal mobs, Epic), Blazing Fortune, Blazing Resistance |
| Typical armour attributes | Mana Pool, Mana Regeneration, Vitality, Mending, Magic Find, Lifeline, Dominance, Speed, Veteran, Breeze, Fortitude (?) |
| Per-level numbers | not fetched (?) |
| Combining gear attributes | merging two items with the same attribute to raise its level: cost and rules not fetched (?) |
| Attributes per chest item | not confirmed (?) |

Shards needed per attribute level (NEU attribute_levelling, levels 1..10):

| Shard rarity | L1 | L2 | L3 | L4 | L5 | L6 | L7 | L8 | L9 | L10 |
|---|---|---|---|---|---|---|---|---|---|---|
| Common | 1 | 3 | 5 | 6 | 7 | 8 | 10 | 14 | 18 | 24 |
| Uncommon | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 12 | 16 |
| Rare | 1 | 2 | 3 | 3 | 4 | 4 | 5 | 6 | 8 | 12 |
| Epic | 1 | 1 | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 7 |
| Legendary | 1 | 1 | 1 | 2 | 2 | 2 | 3 | 3 | 4 | 5 |

---

## Biggest uncertainties

1. Kuudra health per tier, damage, time limits, party-size limits and the S/A/B/C/D score maths were not on any fetched page; the health numbers above are weak recollection (?).
2. Which mobs live in which area (Burning Desert, Smoldering Tomb, Stronghold) and their exact HP/damage/XP: the bestiary gives names and levels only.
3. Magma Boss health (160M vs 200M) and spawn interval (2 vs 5 minutes) conflict between two wiki pages.
4. Daily quest structure: count per day, fishing and Dojo dailies, coins/XP rewards, Sirih/Suus rewards, faction shop contents per rank.
5. Dojo: grade thresholds per test, run durations, Master Tao shop, middle belt stats, Discipline penalty.
6. NPC roles for Bubu, Aranya, Edwin, the Kuudra lore NPCs, Weird Sailor.
7. Armour set bonuses for Aurora, Terror, Fervor, Hollow; per-star stat bonus.
8. Attribute numbers per level, which attributes each Kuudra piece can roll, item-merge costs.
9. Inferno Demonlord: T2/T3 demon stats, drop chances, Blaze Slayer XP levels, meaning of the wiki true-damage table, where it spawns.
10. Travel: exact Spider's Den launch pad route vs any older Hub portal/boat, which scroll goes where.
11. Lavahorse diamond Magmafish: wiki 24 vs NEU 40.
12. Lava sea creature list (14 species) not fetched.
