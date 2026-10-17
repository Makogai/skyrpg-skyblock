# Fishing research

Source note: the official Hypixel wiki is gone and Fandom returns HTTP 402. Everything comes from the community mirror
https://hypixelskyblock.minecraft.wiki/w/<Page> (cited per section), read through a lossy summarizer that sometimes mixes old and
current values (e.g. Squid HP 50 on its page vs 750 in a search snippet). Items marked (?) are unsure, conflicting, or my recollection.
Per-creature numbers are "good, not certified". Not found = flagged.

---

## 1. How fishing works

Sources: /w/Fishing, /w/Fishing_Speed, /w/Sea_Creature_Chance, /w/Treasure, /w/Fishing_Wisdom, /w/Fishing_Skill, /w/Fishing_Rod

### 1a. Bite time (Fishing Speed)

| Step | Rule |
|---|---|
| Wait phase | `BaseTicks = random(200 .. 400 - 200 * LureCatchTimeReduction)` ticks (10-20 s with no lure reduction). "Lure" reduction comes from bait/hook/enchant (exact per-source values not found (?)) |
| Speed reduction | `Ticks = BaseTicks - (FishingSpeed / FishingSpeedCap) * BaseTicks`; if FishingSpeed >= cap, Ticks = 0 |
| Fishing Speed cap | 300 default; Backwater Bayou 200; Lotus Atoll 250; Crimson Isle 350 (Fishing page says "200-350, default 300") |
| Hook phase | After the wait, a fish approaches the bobber for random 1-4 s (20-80 ticks) before you can reel in; NOT affected by Fishing Speed; Quick Bite enchant can cut it by up to 25% |
| Rod damage | Fishing rods deal double damage to sea creatures |
| Killing on Private Island | gives no Fishing XP |

### 1b. What you catch (roll order)

| Order | Roll | Rule |
|---|---|---|
| 1 | Sea Creature | Chance = Sea Creature Chance %. Base 20%. At >= 100% a creature is guaranteed. No cap. Divided by 4 on Private Island / Garden (non-Stranded) |
| 2 | Trophy fish (Lotus Atoll, Crimson Isle only) | Trophy Chance. Later |
| 3 | Treasure | Treasure Chance % (base 0, +0.1 per Fishing level, max +5 from the skill; ~53.9% max total). If it hits, tier is Good 89% / Great 10% / Outstanding 1% |
| 4 | Otherwise | Normal item catch (Raw Cod / Salmon / etc.; see section 2) |

| Stat | Effect |
|---|---|
| Sea Creature Chance | % chance of sea creature on a bite. Sources: rods +1 (basic) to +14 (Hellfire), armors (Thunder +16, Magma Lord +18 total), pets (Dolphin/Ammonite +15 each, Megalodon +10) |
| Treasure Chance | Chance of treasure when no creature; Treasure Hook / Bronze Hunter armor disable sea creatures (they are rolled first) |
| Double Hook Chance | Chance to fish up TWO sea creatures at once; Giant Fishing Rod +10% (?) |
| Weight bonus | Whale/Light/Dark bait multiply the weight of rare creatures by 1.25x (see section 4/8) |
| Blessed Bait | +50% chance that treasure is Great/Outstanding; Blessing enchant +12%, Hermit Crab pet +10% (?) |

### 1c. Fishing XP per catch and Wisdom

| Catch type | XP | Notes |
|---|---|---|
| Sea creature | The creature's XP (25 at level-1 up to 7,800; see section 4/7) multiplied by Wisdom | Awarded on KILL (not on catch), none on Private Island |
| Item / treasure | Not found. From memory, item catches give a small XP (roughly 1-10) (?) and sea creature kills give far more | Sea creatures are clearly the bigger XP source |

Fishing Wisdom: `XP = base * (1 + Wisdom/100)`. Sources: Marina mayor +50 (public islands), Legendary Squid Pet +30, Booster Cookie +25,
Expertise enchant (multiplicative, +20 at X). Max ~185 before Expertise.

---

## 2. Normal catches (water)

Sources: /w/Treasure/Loot/Junk (weights of the normal-catch rows), /w/Treasure/Loot/Water, /w/Fishing_Rod, /w/Prismarine_Rod, /w/Sponge_Rod

The mirror does not give a per-fishing-level item table; in current SkyBlock the normal catch is the same for every level/rod. The only
"normal" weights found (from the Junk pool's normal rows) are Raw Cod 220 / Raw Salmon 150 / Tropical Fish 100 / Pufferfish 80
(total 550 before junk items). I recommend a simple pool:

| Item | Amount | Weight (derived from the junk pool normal rows) |
|---|---|---|
| Raw Cod | 1 | 220 (40%) |
| Raw Salmon | 1 | 150 (27.3%) |
| Tropical Fish (Clownfish) | 1 | 100 (18.2%) |
| Pufferfish | 1 | 80 (14.5%) |
| Ink Sac, Lily Pad | - | not in the item pool: these come from Squid / sea creature drops (section 4) (?) |
| Prismarine Shard/Crystals, Sponge, Clay | - | in the Good Catch treasure pool (section 3) |

Older versions had a tiered level-based pool (Prismarine, Sponge, Ink at higher levels). The Prismarine Rod (+25% chance of Prismarine
Shards/Crystals) and Sponge Rod (+20% Sponges) abilities are legacy. Backwater Bayou with a Junk Sinker adds junk: Old Leather Boot 25,
Moby-Duck 5, Busted Belt Buckle 20, Rusty Coin 10.

---

## 3. Treasure

Sources: https://hypixelskyblock.minecraft.wiki/w/Treasure, /w/Treasure/Loot/Water, /w/Treasure/Loot/Winter

Tier odds when treasure triggers: Good 89%, Great 10%, Outstanding 1% (additive modifiers: Blessed Bait, Blessing, Hermit Crab).
Weights are shown as % of the pool.

### Default water pool

| Tier | Item | Amount | Weight % |
|---|---|---|---|
| Good | Raw Cod | 16 | 22.00 |
| Good | Raw Salmon | 16 | 15.00 |
| Good | Tropical Fish | 12 | 10.00 |
| Good | Clay Ball | 32 | 10.00 |
| Good | Sponge | 16 | 10.00 |
| Good | Pufferfish | 12 | 8.00 |
| Good | Prismarine Shard | 8 | 5.00 |
| Good | Prismarine Crystals | 8 | 5.00 |
| Good | Grand Experience Bottle | 4 | 5.00 |
| Good | Coins | 25,000-50,000 | 10.00 |
| Great | Enchanted Raw Cod | 4 | 19.64 |
| Great | Enchanted Raw Salmon | 4 | 13.39 |
| Great | Enchanted Tropical Fish | 3 | 8.93 |
| Great | Titanic Experience Bottle | 1 | 8.93 |
| Great | Common Squid Pet | 1 | 6.70 |
| Great | Coins | 100,000-250,000 | 8.93 |
| Outstanding | Enchanted Raw Cod | 32 | 17.39 |
| Outstanding | Epic Squid Pet | 1 | 15.81 |
| Outstanding | Enchanted Raw Salmon | 32 | 11.86 |
| Outstanding | Legendary Squid Pet | 1 | 7.91 |
| Outstanding | Coins | 500,000-1,000,000 | 7.91 |

Note: the Great and Outstanding lists above are incomplete (weights sum to ~66% and ~61%); rows were dropped by the summarizer, probably
Enchanted Pufferfish, Enchanted Clay Ball, Enchanted Sponge, Titanic/other XP bottles, Fishing Exp Boost etc. (?). Fishing Exp Boost
(Booster) exists as a rare drop (0.05% from Inkling hotspot).

### Winter (Jerry's Workshop) pool, in brief

| Tier | Items |
|---|---|
| Good | Packed Ice 24, Enchanted Ice 4, Enchanted Snow Block 1, coins 25k-50k |
| Great | Enchanted Ice 8, Enchanted Snow Block 8, Ice Essence 25, coins 100k-250k |
| Outstanding | Enchanted Packed Ice, Enchanted Snow Block, Hunk of Blue Ice, coins 500k-1M, Ice Essence, gifts; Flake the Fish ~1 in 154,000 catches |

Other location pools exist (Lotus Atoll, Moonglade Marsh, Torrhus Canyon, Fairy Pond, Lava, Crimson Isle) - not extracted.

---

## 4. Water sea creatures

Sources: /w/Sea_Creatures (weights/levels), individual pages /w/<Creature_Name> (stats). "Lvl" = mob level, FL = Fishing level required.
Weights: probability among creatures in that pool = weight / pool total. Pool totals shown. Bait: Whale/Light/Dark bait x1.25 weight on rare ones.
Where stats disagree between the pool table and the creature page, the creature page is used and (?) flagged.

### 4a. Basic water (any island, no special zone)

Pool total about 4,300 (weights 1200/800/700/550/400/250/160/130/88/18; base catch % listed on the mirror).

| Creature | FL | Weight | Base % | Lvl | HP | Dmg | XP | Look | Catch message |
|---|---|---|---|---|---|---|---|---|---|
| Squid | 1 | 1200 | 27.93 | 1 | 50 (750 in a snippet?) | - | 25 (41 in a snippet?) | Squid | "A Squid appeared." |
| Sea Walker | 1 (2 in a snippet?) | 800 | 18.62 | 1 (?) | 100 (1000?) | 10 (?) | 25 (68?) | Zombie, faster, diamond sword + bow | "You caught a Sea Walker." |
| Night Squid | 3 (6?) | - | - | 6 | 4000 | - | 270 | Squid | "Pitch darkness reveals a Night Squid." |
| Sea Witch | 7 | 700 | 16.29 | 35 | 6,000 | 150 | 350 | Witch, throws harming potions | "It looks like you've disrupted the Sea Witch's brewing session. Watch out, she's furious!" |
| Sea Archer | 9 | 550 | 12.80 | 36 | 7,000 | 140 | 360 | Skeleton | "You reeled in a Sea Archer." |
| Rider of the Deep | 11 | 400 | 9.31 | 54 | 20,000 | 225 | 540 | Zombie-ish (aquatic undead animal) | "The Rider of the Deep has emerged." |
| Catfish | 13 | 250 | 5.82 | 59 | 26,000 | 250 | 737 | Ocelot | "Huh? A Catfish!" |
| Sea Leech | 16 | 160 | 3.72 | 74 | 60,000 | 300 | 925 | Silverfish | "Gross! A Sea Leech!" |
| Guardian Defender | 17 | 130 | 3.03 | 75 | 76,000 | 250 | 1,125 | Guardian | "You've discovered a Guardian Defender of the sea." |
| Deep Sea Protector | 18 | 88 | 2.05 | 90 | 150,000 | 300 | 1,350 | Iron Golem (throws players up) | "You have awoken the Deep Sea Protector, prepare for a battle!" |
| Water Hydra | 19 | 18 | 0.42 | 119 | 500,000 | 400 | 2,380 | Elusive humanoid | "The Water Hydra has come to test your strength." |

Night Squid: only at night with Dark Bait (and in the Park in rain); Squid page notes Squid also spawn in The Park during rain.
"Sea Emperor": the mirror's /w/Sea_Emperor redirects to The Loch Emperor (Moonglade Marsh); the classic Sea Emperor (Lvl ~1000+, 800k HP,
drops Shredder 2%) was not found as a page (?). Not in the current basic pool table.

Drops (id: chance):

| Creature | Drops |
|---|---|
| Squid | Ink Sac 2-3, Lily Pad 1-2, Squid Boots 1%, Enchanted Ink Sac 0.5%, Aquamarine Dye 1/5M |
| Sea Walker | Rotten Flesh 5-9, Raw Cod 2, Lily Pad 1 (all 100%), Aquamarine Dye 1/5M |
| Night Squid | Ink Sac 6-17, Lily Pad 1, Squid Boots ~8% (?) |
| Sea Witch | Raw Salmon 100%, Lily Pad 100%, Tropical Fish 20%, Fairy armor piece 5%, Aquamarine Dye 1/5M |
| Sea Archer | Bone 8-13, Raw Cod 2, Lily Pad 1, Enchanted Bone 1% |
| Rider of the Deep | Lily Pad 2, Dark Bait 50% (0-1), Enchanted Book Magnet VI 2% |
| Catfish | Raw Cod 20, Raw Salmon 10, Pufferfish 5, Enchanted Book Frail VI 1% |
| Sea Leech | Raw Cod 10, Lily Pad 3-4, Tropical Fish 2, Enchanted Book Spiked Hook VI 2% |
| Guardian Defender | Raw Cod 15, Lily Pad 5-7, Sponge 1, Enchanted Prismarine 50-60%, Aquamarine Dye 1/2.5M |
| Deep Sea Protector | Lily Pad 12-15, Tropical Fish 2-3, Enchanted Iron Ingot 2, Sponge 1, Angler VI book (rare) |
| Water Hydra | Raw Cod 20, Lily Pad 16-18, Water Hydra Head 14%, Fish Affinity Talisman 30% |

### 4b. Jerry's Workshop (winter; Season of Jerry / winter water)

Pool weights 5000/2875/1500/500/100/25 (total 10,000: percentages direct).

| Creature | FL | Weight % | Lvl | HP | Dmg | XP | Catch message | Drops |
|---|---|---|---|---|---|---|---|---|
| Frozen Steve | 1 | 50 | 1 | 150 | 10 | 25 | "Frozen Steve fell into the pond long ago, never to resurface...until now!" | Ice 2-4, Ice Essence 1, Icy Sinker 1%, Iceberg Dye 1/2.5M |
| Frosty | 1 | 28.75 | 3 | 250 | 15 | 30 | "It's a snowman! He looks harmless." | Ice 16-32, Snow Block 16-32, Ice Essence 2-4, Hunk of Ice 1 |
| Grinch | 13 | 15 | 25 | dies in 3 hits | 1 | 312 | "The Grinch stole Jerry's gifts... Get them back!" | Ice 32-64, Ice Essence 4-8, White Gift 1, Red Gift 10% |
| Nutcracker | 18 | 5 | 115 | 400,000 | 400 | 1,725 | "You found a forgotten Nutcracker laying beneath the ice." | Ice Essence 12-24, Red Gift, Enchanted Ice, Walnut 10%, Iceberg Dye 1/500k; summons 500k HP Toy Soldiers |
| Yeti | 25 | 1 | 175 | 2,000,000 | 1,000 | 3,500 | "What is this creature!?" | Ice Essence 100, Enchanted Ice 32-64, Red Gift 1-2, True Ice 1%, Baby Yeti Pet 2% |
| Reindrake | 35 | 0.25 | 186 | 2,500 hits (damage 1 per hit) | - | 4,650 | "A Reindrake forms from the depths." | Prosperity I book, dyes, gifts (65% white, 25% green, 10% red per hit) |

Frosty, Grinch and Reindrake are scripted "hit count" creatures.

### 4c. Spooky Festival (any island; 1 h before through 1 h after)

Pool weights 5000/2875/1500/500/100/25.

| Creature | FL | Weight % | Lvl | HP | Dmg | XP | Look | Catch message | Drops |
|---|---|---|---|---|---|---|---|---|---|
| Jumpin' Jack | 6 | 50 | 28 | 3,500 | 125 | 210 | Snow Golem, jumps like slime, explodes | "Watch out! It's Jumpin' Jack." | Pumpkin 2-4, Green Candy 1-2, Enchanted Pumpkin 1%, Midnight Dye 1/1M |
| Scarecrow | 9 | 28.75 | 32 | 4,500 | 150 | 320 | Snow Golem | "Phew! It's only a Scarecrow." | Pumpkin 4-8, Green Candy 2-4, Purple Candy 0-1, Enchanted Pumpkin 0-1 |
| Nightmare | 14 | 15 | 77 | 35,000 | 600 | 962 | Horse-ish undead | "You hear trotting from beneath the waves, you caught a Nightmare." | Pumpkin 8-16, Green Candy 4-8, Purple Candy 0-1, Ench. Pumpkin 0-1, Lucky Hoof 0-1 |
| Werewolf | 17 | 5 | 69 | 50,000 | 250 (+20%/s up to 300%) | 1,035 | Wolf/zombie | "It must be a full moon, a Werewolf appears." | Pumpkin 16-32, Purple Candy, Werewolf Skin |
| Phantom Fisher | 21 | 1 | 157 | 1,000,000 | 1,000 | 3,140 | Phantom | "The spirit of a long lost Phantom Fisher has come to haunt you." | Purple Candy 2-4, Ench. Pumpkin 1-2, Spooky Hook 1%, Midnight Dye 1/50k |
| Grim Reaper | 26 | 0.25 | 312 | 3,000,000 | 2,100 | 7,800 | Reaper | "This can't be! The manifestation of death himself!" | Purple Candy 8-16, Ench. Pumpkin 4-8, Soul Fragment 1, dyes 1/50k |

Nightmare also needs the Fear Mongerer in the village (?).

### 4d. Fishing Festival (sharks; any island, during the festival)

Pool weights 2875/1500/500/100 (percent among sharks: 57.79 / 30.15 / 10.05 / 2.01 in the mirror table).

| Creature | FL | Lvl | HP | Dmg | XP | Look | Catch message | Drops |
|---|---|---|---|---|---|---|---|---|
| Nurse Shark | 5 | 22 | 2,500 | 80 | 220 | zombie-like | "A tiny fin emerges from the water, you've caught a Nurse Shark." | Shark Fin 2, Nurse Shark Tooth 10%, Carnival Ticket 0.1% |
| Blue Shark | 10 | 52 | 25,000 | 150 | 650 | zombie, faster | "You spot a fin as blue as the water it came from, it's a Blue Shark." | Shark Fin 4, Blue Shark Tooth 10%, Carnival Ticket 0.15% |
| Tiger Shark | 18 | 99 | 250,000 | 300 | 1,485 | zombie, shoots teeth | "A striped beast bounds from the depths, the wild Tiger Shark!" | Shark Fin 8, Tiger Shark Tooth 10%, Megalodon Pet (epic) 1%, Carnival Ticket 0.25% |
| Great White Shark | 24 | 160 | 1,500,000 | 750 | 3,200 | zombie, shoots teeth | "Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!" | Shark Fin 16, Great White Shark Tooth 10%, Megalodon Pet 1% |

### 4e. Other water sea creatures

| Creature | Where / condition | FL | Weight (pool) | Lvl | HP | Dmg | XP | Look | Catch message | Drops |
|---|---|---|---|---|---|---|---|---|---|---|
| Carrot King | Any water, Carrot Bait (5% override) | 14 | 5% | 63 | 32,000 | 275 (true KB) | 787 | evil Rabbit | "Is that even a fish? It's the Carrot King!" | Ench. Carrot, Ench. Rabbit Foot, Lily Pad 2-3, Rabbit Hat 25% |
| Agarimoo | Empty Chumcap Bucket you placed (any island) | 15 | 15% | 81 | 55,000 | 500 | 1,012 | Mooshroom | "Your Chumcap Bucket trembles, it's an Agarimoo." | Red Mushroom 35-55, Agarimoo Tongue 3, Aquamarine Dye 1/2.5M |
| Oasis Sheep | Mushroom Desert Oasis | 10 | 700 | 4 | 6,000 | 0 | 40 | Sheep | "An Oasis Sheep appears from the water" | Raw Mutton 1, Wool 1, Lily Pad 1-2, Ench. Raw Mutton 5%, Ench. Cooked Mutton 0.1% |
| Oasis Rabbit | Oasis | 10 | 300 | 4 | 6,000 | (arrows) | 40 | Rabbit | "An Oasis Rabbit appears from the water." | Raw Rabbit 1, Lily Pad 1-2, Ench. Raw Rabbit 0-15 |
| Water Worm | Crystal Hollows, Goblin Holdout | 15 | 300 | 71 | 50,000 | 300 | 887 | Silverfish | "A Water Worm surfaces!" | Rough Amber 10-17, Hard Stone 10, Worm Membrane 15% |
| Poisoned Water Worm | Crystal Hollows (Goblin Holdout) | 17 | 300 | 83 | 75,000 | 400 | 1,037 | Endermite | "A Poisoned Water Worm surfaces!" | Rough Amber 20-30, Hard Stone 10, Worm Membrane 20% |
| Abyssal Miner | Crystal Hollows zones | 24 | 90 | 182 | 2,000,000 | 1,300 | 3,640 | humanoid | "An Abyssal Miner breaks out of the water!" | Rough Sapphire/Jade/Amethyst 20 each, Flawed gems, Piscary VI book (rare) |
| Mithril Grubber | Dwarven Mines, Abandoned Quarry | 6 | 385 | 29-49 | 6k-15k | 75-200 | 290-490 | Silverfish | "A leech of the mines surfaces... you've caught a Mithril Grubber." | Mithril 8-96, Raw Cod 2-24, Plasma/Ench. Mithril rare |
| The Loch Emperor | Moonglade Marsh | 20 | 100 (of 10,000) | 101 | 800,000 | 100 | 2,020 | skeletal elusive | "The Loch Emperor arises from the depths." | Sea Lumies 32-64, Ench. Mangrove Log 8-16, Emperor's Skull 20%, Mangcore 1%, Flying Fish pet |
| Inkling (hotspot) | Water hotspots | 7 | 2,875 hotspot | 38 | 6,500 | 175 | 380 | baby Glow Squid | "You get an inkling you've caught... an Inkling!" | Clay 2-4, Ink Sac 2-4, Ench. Clay/Ink 1%, Ink Splat 4%, Fishing Exp Boost 0.05% |

Hotspot creatures (Frog Man FL5 / Inkling FL7 / Snapping Turtle FL10 / Manta Ray FL19 / Blue Ringed Octopus FL35 / Wiki Tiki FL40; weights
5000/2875/1500/500/100/25) are listed but only Inkling was detailed.

Other regional pools (not detailed; Fishing-level requirement in brackets): Backwater Bayou (Trash Gobbler 5, Dumpster Diver 5, Banshee 10,
Bayou Sludge 15, Alligator 20, Titanoboa 30; weights 5000/2875/1500/500/100/25), Lotus Atoll (Atoll Croaker, Lotus Guardian, gorF,
Drowned Captain, Puddle Jumper, Frog Prince; all FL10), Moonglade Marsh (Bogged 5, Wetwing 7, Tadgang 9, Ent 12, Loch Emperor 20, Nessie 25),
Torrhus Canyon (Haggard, Brineling, Sprawl, Torrid, Silkbreeze, Giant Isopod; all FL15).

Not found: "Sea Guardian", "Sea Emperor" classic stats, "Carrot King" weight against other creatures (it is a flat 5% override).
Pre-0.25 versions had Night Squid / Sea Guardian in the basic pool; current pool above replaces them (?).

---

## 5. Lava sea creatures (Crimson Isle / Magma Fields)

Sources: /w/Fishing (examples), /w/Fishing_Rods. Mirror gave only examples:

| Creature | FL | Notes |
|---|---|---|
| Fried Chicken | 30 (the Fishing page says Lvl 30) (?) | common, Crimson Isle |
| Baby Magma Slug | low (?) | page exists /w/Baby_Magma_Slug; first lava creature |
| Ragnarok | Lvl 666, Mythic | top of the list |
| Fire Eel, Magma Slug, Moogma, Lava Leech, Pyroclastic Worm, Lava Flame, Fire Eel, Taurus, Thunder, Lord Jawbus, Plhlegblast ("Plhlegblast Pool") | (?) from memory | health values not fetched |

Lava fishing requires Fishing level 10, a lava rod (Starter Lava Rod etc.) and a pool of lava. Not researched further.

---

## 6. Rods

Sources: /w/Fishing_Rods, /w/Fishing_Rod, /w/Sponge_Rod, /w/Prismarine_Rod, /w/Speedster_Rod, /w/Farmer%27s_Rod, /w/Auger_Rod, /w/Shredder
Our item json ids (`skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items/`) exist for each rod below, so only the special rules are listed.

| Rod (our id) | Rarity | Fishing lvl | Stats on the mirror | Special |
|---|---|---|---|---|
| Fishing Rod (`fishing_rod`) | Common | - | Dmg 15, Str 15, SCC +1, Speed 5 | 50 coins at Fishing Merchant; takes 3 rod parts (Hook, Line, Sinker) via Roddy |
| Prismarine Rod (`prismarine_rod`) | Common/Uncommon | 3 | Dmg 15, Str 10, Speed 15 | +25% chance for Prismarine Shard/Crystals. Legacy |
| Sponge Rod (`sponge_rod`) | Uncommon | 5 | Dmg 20, Str 15, Speed 30 | +20% chance for Sponges. Legacy. Recipe 3 Sponge + 2 String |
| Speedster Rod (`speedster_rod`) | Uncommon | 6 | Dmg 30, Str 15, Speed 45 | Hitting a mob gives a Speed burst. 96 Raw Salmon + 2 String. Legacy |
| Farmer's Rod (`farmer_rod`) | Uncommon | 8 | Dmg 50, Str 20, Speed 60 | Chance to fish up farm animals (sheep, pig, chicken, rabbit, cow, mooshroom). Legacy |
| Challenging Rod (`challenge_rod`) | Uncommon | 10 | Dmg 75, Str 75, Speed 35 | Unlocked at skill level 10 |
| Rod of Champions (`champ_rod`) | Rare | 15 | Dmg 100, Str 100, Speed 50 | |
| Rod of Legends (`legend_rod`) | Epic | 20 | Dmg 125, Str 125, Speed 65 | |
| Rod of the Sea (`rod_of_the_sea`) | Legendary | 24 | Dmg 150, Str 150, Speed 80 | |
| Shredder (`the_shredder`) | Legendary | 20 | Dmg 120, Ferocity 50, Speed 115 | +1 Str (max 15) per 50 bait, +1 Dmg (max 115) per 10 fish; 3 gem slots; 2% from Sea Emperor. Legacy |
| Auger Rod (`auger_rod`) | Legendary | 25 | Dmg 135, Str 90, Speed 110 | Breaks nearby ice, x3 winter creature chance. 1 Yeti Rod + 128 Ench. Packed Ice. Legacy |
| Yeti Rod (`yeti_rod`), Winter Rod (`winter_rod`), Ice Rod (`ice_rod`) | - | - | not fetched | Winter-creature rods (Jerry's Workshop) |
| Giant Fishing Rod (`giant_fishing_rod`) | Legendary | - | Double Hook Chance +10% (?) | |
| Dirt Rod (`dirt_rod`) | - | - | - | Lets you fish on dirt (Shen's Special) |
| Phantom Rod (`phantom_rod`), Chum Rod (`chum_rod`), Magma Rod, Starter Lava Rod, Inferno Rod, Hellfire Rod | - | - | - | Starter Lava Rod (uncommon, lvl 10, trophy +5%), Polished Topaz Rod (rare, 19), Magma Rod (rare, 27, +15% trophy), Inferno Rod (epic, 30), Hellfire Rod (legendary, 35, +14 SCC). Lava rods are for Crimson Isle |
| Bingo Rod, Carnival Fishing Rod, Mirrored Fishing Rod, Shiny Rod, Nearly Coherent Rod | - | - | event / special | skip |

Sea creature damage: rod damage is doubled against sea creatures.

---

## 7. Fishing XP table

Sources: /w/Fishing_Skill, individual creature pages.

### 7a. Skill levels

| Lv | XP to next | Lv | XP to next | Lv | XP to next |
|---|---|---|---|---|---|
| 1 | 50 | 11 | 5,000 | 26 | 800,000 |
| 2 | 125 | ... | ... | ... | ... |
| 10 | 3,500 | 25 | 700,000 | 50 | 4,000,000 |

Only the endpoints were retrieved: total to level 50 = 55,172,425 XP. The standard SkyBlock skill table (shared with Farming etc.) applies
(?): 50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000, 50000, 75000, 100000, 200000, 300000,
400000, 500000, 600000, 700000, 800000, 900000, 1000000, 1100000, 1200000, 1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000,
2000000, 2100000, 2200000, 2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000.

Per-level reward (Fishing Skill page): Health +2 to +5 per level (+192 total at 50), Treasure Chance +0.1 per level (max +5 at 50; "Treasure Hunter"),
SkyBlock XP +5..+20 per level, coins 100 at lvl 1 up to 1M at levels 45-50.
Unlocks: Lvl 5 Backwater armor and sinkers; Lvl 10 Common Hook, Challenging Rod, lava fishing; Lvl 20 Hotspot Hook, Trophy Line, Diver's Armor.

### 7b. XP per sea creature (selected, kill XP, then multiplied by Wisdom)

| Creature | XP | Creature | XP | Creature | XP |
|---|---|---|---|---|---|
| Squid | 25 | Water Hydra | 2,380 | Jumpin' Jack | 210 |
| Sea Walker | 25 | Frozen Steve | 25 | Scarecrow | 320 |
| Night Squid | 270 | Frosty | 30 | Nightmare | 962 |
| Sea Witch | 350 | Grinch | 312 | Werewolf | 1,035 |
| Sea Archer | 360 | Nutcracker | 1,725 | Phantom Fisher | 3,140 |
| Rider of the Deep | 540 | Yeti | 3,500 | Grim Reaper | 7,800 |
| Catfish | 737 | Reindrake | 4,650 | Nurse Shark | 220 |
| Sea Leech | 925 | Carrot King | 787 | Blue Shark | 650 |
| Guardian Defender | 1,125 | Agarimoo | 1,012 | Tiger Shark | 1,485 |
| Deep Sea Protector | 1,350 | Water/Poisoned Worm | 887 / 1,037 | Great White Shark | 3,200 |
| Oasis Sheep/Rabbit | 40 | Abyssal Miner | 3,640 | Loch Emperor | 2,020 |

Item catches: XP per item not found (?); I recommend a tiny value (e.g. 2 XP, treasure 5-20) so creature kills dominate (kills give 25 to
7,800).

---

## 8. Baits

Source: https://hypixelskyblock.minecraft.wiki/w/Bait (our ids exist: `fish_bait`, `minnow_bait`, `light_bait`, `dark_bait`, `spiked_bait`,
`spooky_bait`, `carrot_bait`, `corrupted_bait`, `blessed_bait`, `ice_bait`, `shark_bait`, `glowy_chum_bait`, `hot_bait`, `worm_bait`,
`golden_bait`, `frozen_bait`, `whale_bait`, `treasure_bait`, `hotspot_bait`, `wooden_bait`)

| Bait | Effect | Recipe / source |
|---|---|---|
| Minnow Bait | +25 Fishing Speed | 1 Raw Salmon (ench. tier I?) + 2 Raw Cod |
| Fish Bait | +45 Fishing Speed | 5 Raw Salmon + 3 Raw Cod |
| Light Bait | More rare sea creatures during day (weight x1.25 on rare creatures) | 2 Prismarine Crystals + Raw Cod |
| Dark Bait | More rare sea creatures at night; enables Night Squid | 2 Ink Sac + Raw Cod |
| Spiked Bait | +6 Sea Creature Chance | 5 Pufferfish + Raw Cod |
| Spooky Bait | +25 Fishing Speed, +15% spooky sea creatures | Lily Pad + Pumpkin + Raw Cod |
| Carrot Bait | Chance to fish up Carrot King | 3 Carrot + Raw Cod |
| Corrupted Bait | -50% Fishing Speed, lures special trophy fish | 5 Magmafish + Corrupted Fragment |
| Blessed Bait | +50% chance treasure is Great/Outstanding | 6 Prismarine Crystals + 9 Gold Ingot + Block of Gold + 2 Raw Cod |
| Ice Bait | +25 Fishing Speed, +4 SCC | 6 Ice + Raw Cod (or Glacial Cave) |
| Shark Bait | +20% shark catch chance in the Festival | 2 Shark Fin |
| Glowy Chum Bait | +25 Fishing Speed, +3 SCC, +2 chums | Moby vendor / Glacial Cave |
| Hot Bait | +15 Fishing Speed, +5 Trophy Chance | 2 Magmafish + Blaze Ashes |
| Worm Bait | +60 Fishing Speed (Crystal Hollows, worms only) | Worm Membrane + Clay Ball |
| Golden Bait | +4 Treasure Chance | Seal's Treat Bag |
| Frozen Bait | +50 Fishing Speed, +8 SCC | Glacial Cave treasure (Jerry's Workshop) |
| Whale Bait | +30 Fishing Speed, rare creature boost | many fish items |
| Treasure Bait | +10 Fishing Speed, +2 Treasure Chance | Junker Joel |
| Hotspot Bait | +20% hotspot sea creature chance | Ench. Pufferfish + Scorched Crab Stick |
| Wooden Bait | Guarantees a Rare Fishing Shard on Good Catch | Rosemary |

Bait is consumed per cast.

---

## 9. Trophy fish

Source: /w/Trophy_Fish. 18 trophy fish (Crimson Isle, Lotus Atoll later) in four tiers: Bronze, Silver, Gold, Diamond; rarities Common to
Legendary; each needs conditions (e.g. near Sulphur Ore for Sulphur Skitter, Dojo for Karate Fish). Trophy Chance is rolled after sea
creatures. Pity: Gold guaranteed after 100 catches, Diamond after 600 per fish type (?). Fillet at Odger for Magmafish currency. Later work.

---

## Biggest uncertainties

- No per-level normal item table and no item-catch XP on the mirror.
- Squid/Sea Walker/Night Squid stats conflict (HP 50 vs 750, XP 25 vs 41, level 1 vs 2 vs 3).
- Great/Outstanding treasure pools are incomplete.
- Sea Emperor classic stats and Sea Guardian are missing.
- Lava creature stats not fetched.
