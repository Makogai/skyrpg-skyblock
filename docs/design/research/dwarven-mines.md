# Dwarven Mines (Mining 2) research

Source note: the official Hypixel wiki (wiki.hypixel.net) was shut down in July 2026 and now redirects to a forum announcement
(https://hypixel.net/threads/end-of-the-official-hypixel-wiki-july-2026.6112020/). The Fandom wiki returns HTTP 402 to our fetcher.
Everything below comes from the community mirror https://hypixelskyblock.minecraft.wiki/w/<Page> (page URLs cited per section), read through a
lossy summarizer, so exact numbers should be treated as "good, not certified". Items marked (?) are unsure or my own recollection.
Coordinates are in-game x,y,z (+x east, +z south, -z north). Anything not found is explicitly flagged "not found".

---

## 1. Mining speed formula

Source: https://hypixelskyblock.minecraft.wiki/w/Mining_Speed

| Rule | Value |
|---|---|
| Ticks to break | `ticks = round(BlockStrength * 30 / MiningSpeed)` (1 tick = 0.05 s) |
| Softcap on break time | never faster than 4 ticks (0.2 s) unless instant-mined |
| Mining speed to reach the 4 tick floor | `ceil(20 * BlockStrength / 3)` |
| Instant mining (breaks in 1 tick, bypasses floor) | normal blocks: MiningSpeed > 30 * strength. Ores, obsidian, gemstones, dwarven metals: MiningSpeed > 60 * strength |
| Examples | Coal ore (30) needs 1,800 to instamine; obsidian (500) needs 30,001 |
| Hard cap on mining speed | none |

Breaking Power (https://hypixelskyblock.minecraft.wiki/w/Breaking_Power): an integer stat on the tool; a block with required BP above the tool's BP cannot be broken
at all. Base 0, not active on Private Island / Hub / Catacombs. Max theoretical 12 (Divan's Drill 10, Pocket Iceberg reforge +1, Maniac Miner ability +1).
Glacial mobs (Glacite Walker) take damage only from Breaking Power (1 BP = 150x "1 damage") (?exact multiplier).

Implementation hint: required-BP table in section 2; tick formula above; the wiki quotes "+1" in min mining speed examples (e.g. Netherrack 241 = 8*30+1) meaning the
instamine test is a strict ">".

---

## 2. Block table

Sources: https://hypixelskyblock.minecraft.wiki/w/Block_Strength (marked outdated by the wiki), /w/Breaking_Power, /w/Mithril, /w/Hard_Stone, /w/Titanium, /w/Glacite,
/w/Umber, /w/Gemstone, /w/Mining. "Min MS" = mining speed needed to instamine. Regen: see notes under the table.

### 2a. Vanilla-ish blocks

| Block | MC block id | Strength | Req. BP | Drops | Mining XP | Min MS instamine |
|---|---|---|---|---|---|---|
| Stone | stone | 15 | 0 | Cobblestone | 1 (?) | 451 |
| Cobblestone | cobblestone | 20 | 0 | Cobblestone | 1 | 601 |
| Coal Ore | coal_ore | 30 | 1 | Coal | 5 | 1,800 |
| Iron Ore | iron_ore | 30 | 2 | Iron Ingot (auto-smelted) | 5 | 1,800 |
| Gold Ore | gold_ore | 30 | 2 (?) | Gold Ingot | 6 | 1,800 |
| Lapis Ore | lapis_ore | 30 | 2 | Lapis (4-8) (?) | 5 (?) | 1,800 |
| Redstone Ore | redstone_ore | 30 | 2 (?) | Redstone | 5 (?) | 1,800 |
| Emerald Ore | emerald_ore | 30 | 3 (?) | Emerald | 8 (?) | 1,800 |
| Diamond Ore | diamond_ore | 30 | 3 | Diamond | 10 | 1,800 |
| Nether Quartz Ore | nether_quartz_ore | 30 | 3 (?) | Nether Quartz | 5 (?) | 1,800 |
| Pure Coal / Iron / Gold / Lapis / Redstone / Emerald / Diamond / Quartz block | coal_block, iron_block, gold_block, lapis_block, redstone_block, emerald_block, diamond_block, quartz_block | 600 | 3 (Pure Coal, Pure Emerald confirmed; others (?)) | 1 of the ore item (matching ore) | (?) | 36,000 |
| Sulphur Ore | sponge (wet? ) (?) | 500 | (?) | Sulphur | (?) | 30,000 |
| Obsidian | obsidian | 500 | 4 | Obsidian | (?) (15?) | 30,001 |
| End Stone | end_stone | 30 | 1 | End Stone | 3 (?) | 901 |
| Netherrack | netherrack | 8 | 1 | Netherrack | 1 (?) | 241 |
| Glowstone | glowstone | 2 (?) | 0-1 (?) | Glowstone Dust | (?) | - |
| Ice | ice | 2 (?) | 0 | Ice | (?) | - |
| Sand | sand | 0.5 (?) | 0 | Sand | 3 | - |
| Gravel | gravel | 0.6 (?) | 0 | Gravel | 4 | - |
| Hard Stone (stone variant) | stone (in Crystal Hollows/Glacite) | 50 | 4 | Hard Stone | (?) | 1,501 |
| Hard Stone (terracotta variant, CH only) | cyan_terracotta | 100 | 4 | Hard Stone | (?) | 3,001 |

Notes: Hard Stone does not exist in the Dwarven Mines proper (CH/Glacite only). Pure ore blocks are found in the Royal Mines (all except Quartz per the wiki)
and Pure Emerald on the Great Ice Wall.

### 2b. Dwarven metals

| Block | MC block id | Strength | Req. BP | Drops (base) | Mithril powder | Mining XP | Min MS |
|---|---|---|---|---|---|---|---|
| Mithril tier 1 | gray_wool, cyan_terracotta (hardened clay) | 500 | 4 | 1 Mithril | 1 | 45 (?) | 30,001 |
| Mithril tier 2 | prismarine, prismarine_bricks, dark_prismarine | 800 | 4 | 2 Mithril | 2 | 45 (?) | 48,001 |
| Mithril tier 3 | light_blue_wool | 1,500 | 4 | 5 Mithril | 5 | 45 (?) | 90,001 (cannot be instamined in CH; the Mines (?)) |
| Titanium | polished_diorite | 2,000 | 5 | 2 Titanium (Fortune applies) | (none known) | 100 (?) | 120,001 |
| Umber (Glacite) | terracotta, brown_terracotta, red_sandstone (1x/2x/3x yield) | 5,600 | 9 | Umber | (glacite powder) | (?) | 336,001 |
| Tungsten (Glacite) | stone/cobble variants, clay (?) | 5,600 | 9 | Tungsten | (glacite powder) | (?) | - |
| Glacite | packed_ice (?) | 6,000 | 9 | Glacite | (glacite powder) | (?) | 360,001 |

Mithril spawning detail: Titanium has a 0.5% base chance to replace a mined Mithril block, 2% with Titanium Insanium, up to ~7.1% with perks + food.
Gray wool / cyan clay can be instamined in the Dwarven Mines and Glacite Tunnels; the prismarine and light blue tiers cannot (per /w/Mithril).

### 2c. Gemstones (Crystal Hollows / Glacite, not Dwarven Mines; included for completeness)

Source: /w/Block_Strength, /w/Gemstone. Each gemstone is a stained glass block + stained glass pane pair. Breaking Power: Ruby 6 (?), Amber/Amethyst/Jade/Sapphire 7 (?), Topaz 8 (?),
Jasper 8 (?), Opal 7 (?), Aquamarine/Citrine/Onyx/Peridot 9 (?). Wiki states BP 6-9 for gemstone blocks. Drops 3-5 Rough gemstones (Pristine may upgrade to Flawed). Gemstone powder per block (?).

| Gemstone | Block strength | Min MS | MC block (?) |
|---|---|---|---|
| Ruby | 2,300 | 138,001 | red_stained_glass / pane |
| Amber | 3,000 | 180,001 | orange_stained_glass / pane |
| Amethyst | 3,000 | - | purple_stained_glass / pane |
| Jade | 3,000 | - | lime_stained_glass / pane |
| Opal | 3,000 | - | white_stained_glass / pane |
| Sapphire | 3,000 | - | light_blue_stained_glass / pane |
| Topaz | 3,800 | 228,001 | yellow_stained_glass / pane |
| Jasper | 4,800 | 288,001 | magenta_stained_glass / pane |
| Aquamarine | 5,200 | 312,001 | (?) |
| Citrine | 5,200 | - | brown_stained_glass (?) |
| Onyx | 5,200 | - | black_stained_glass |
| Peridot | 5,200 | - | green_stained_glass |

### 2d. Regeneration

The wiki states Mithril-type ores "respawn even if the chunk is unloaded". Exact regen seconds not found. From memory (?): mined ore blocks turn into
bedrock-like placeholder (Dwarven Mines: mined block becomes cobblestone/stone-ish "dead" block) and return after roughly 4-10 s for ores, longer for metals; real-time
Hypixel behaviour: Mithril/Titanium reappear in a few seconds to ~1 minute. Treat as design choice: ore 4 s, Mithril 8 s, Titanium 12 s (decide, document in progression.md).

---

## 3. Breaking Power of tools

Sources: https://hypixelskyblock.minecraft.wiki/w/Pickaxe, /w/Mithril_Pickaxe, /w/Breaking_Power

| Tool | Breaking Power | Mining Speed |
|---|---|---|
| Wooden Pickaxe | 1 | 70 |
| Stone Pickaxe | 2 | 110 |
| Iron Pickaxe | 3 | 160 |
| Golden Pickaxe | (?) 3 or 1 (not listed) | (?) |
| Diamond Pickaxe | 4 | 220 |
| Netherite Pickaxe | not found (?) | not found |
| Stonk (Epic) | 1 | 380 |
| Fractured Mithril Pickaxe | 3 | 200 |
| Bandaged Mithril Pickaxe | 4 | 250 |
| Mithril Pickaxe (needs HotM 2, 1 Bandaged + 64 Ench Mithril + Bejeweled Handle + 32 Ench Gold) | 5 | 300 (+7 Mining Fortune, +40 damage) |
| Refined Mithril Pickaxe | 6 | 350 |
| Rusty Titanium Pickaxe | 7 | 400 |
| Titanium / Polished Titanium Pickaxe | (?) 7-8 | (?) |
| Gemstone Gauntlet | 8 | 800 |
| Divan's Drill | 10 | (high) |
| Pocket Iceberg reforge | +1 | |
| Tools do not use durability | | |

Repo note: vanilla pickaxes in SkyBlock carry BP in the table above (gold and netherite not documented), so our vanilla-material pickaxes need `breaking_power`
values (wood 1, stone 2, iron 3, gold (?)=3, diamond 4, netherite (?)=5) and a mining speed stat added if we want them usable in the mines; I did not read the repo JSONs.

---

## 4. Access

Sources: https://hypixelskyblock.minecraft.wiki/w/Dwarven_Mines, /w/Heart_of_the_Mountain, /w/Gates_to_the_Mines

| Item | Detail |
|---|---|
| Route | Deep Caverns, Lift Operator at the Obsidian Sanctuary area -> The Lift -> Dwarven Village (entrance of the Mines) |
| Skill requirement | Mining level 12 (XII); Rhys's quest "There are Dwarves?" (bring 15 enchanted ore) which also unlocks HotM |
| Warps | `/warp forge` (the Forge) and `/warp dwarves`? (Dwarven Village/Mines) were common; exact names not verified (?). `/warp base` for Dwarven Base Camp (?) |
| Fast travel | Station Master (Dwarven Village) and Ticket Masters in many zones sell minecart rides between zones for coins |
| Forge access | needs Mining 15 for fast travel except during Bingo events (per /w/The_Forge) |
| Area discovery text | "NEW AREA DISCOVERED! Dwarven Mines - Mine rare ores." |

---

## 5. Map / areas

Sources: https://hypixelskyblock.minecraft.wiki/w/Dwarven_Mines and the individual area pages (Cliffside_Veins, Royal_Mines, Goblin_Burrows, Upper_Mines, Far_Reserve,
Great_Ice_Wall, The_Mist, Dwarven_Village, Rampart%27s_Quarry, Royal_Palace, Lava_Springs, Gates_to_the_Mines). Coordinates below are the wiki's listed spot per area (approximate centre).

| Area | x,y,z | Notes |
|---|---|---|
| Dwarven Village (spawn, top level) | ~0,205,-120 (extent x -38..38, y 200-213, z -154..-85) | y 200+ |
| Dwarven Tavern | ~32,202,-154 | inside village |
| Ironman's Guild | in village / Upper Mines link | |
| The Lift | village edge | |
| Braum (entrance NPC) | 89,198,-92 | east of village |
| Gates to the Mines | 4,182,-60 | south of village, overlooks the Forge |
| The Forge / Forge Basin | ~0,~190,~-30 (?) | below Lava Springs |
| Lava Springs | 72,197,-18 | north-east of Forge, up the lava channel; start of Palace Bridge |
| Palace Bridge | between Lava Springs and Royal Palace | roughly x 72 -> 129 |
| Royal Palace | 129,187,116 | far south-east |
| Royal Mines | 140,152,35 | below Palace Bridge |
| Cliffside Veins | 20,128,53 | south-central, deep |
| Rampart's Quarry | -23,175,-55 | west of Gates |
| Upper Mines | -126,175,-55 | above Rampart's, far west |
| Far Reserve | -96,142,111 | south-west |
| Goblin Burrows | -113,142,110 | south-west of Far Reserve |
| Great Ice Wall | 0,128,160 | far south |
| Divan's Gateway | between Cliffside Veins and Great Ice Wall; The Mist underneath | |
| The Mist | under Divan's Gateway (~15,~115,~100 (?)) | |
| Dwarven Base Camp | 2,121,237 | beyond Great Ice Wall (HotM 7+) |
| Hanging Court | past Royal Palace | link to Base Camp (HotM 7+) |
| Aristocrat Passage | links Royal Palace/Royal Mines and Great Ice Wall | |
| Abandoned Quarry | fishing / Pure Quartz (location (?)) | |

### Rough ASCII map (north up, east right; heights in parentheses)

```
                         N (-z)
   [Upper Mines (175)]--[Rampart's Quarry (175)]--[Gates to the Mines (182)]--Forge--[Lava Springs (197)]
          |                      |                       |                              |
   [Ironman's Guild]    [Dwarven Village (205) -- Tavern, Lift, Bank, Bubu]     [Palace Bridge]
                                                                                        |
   [Far Reserve (142)]----------------------[Cliffside Veins (128)]----[Royal Mines (152)]---[Royal Palace (187)]
          |                                         |                          |                   |
   [Goblin Burrows (142)]                    [Divan's Gateway]---(The Mist below)     [Hanging Court]
          \                                         |                          |
           `-------------------------------[Great Ice Wall (128)]----[Aristocrat Passage]
                                                    |
                                          [Dwarven Base Camp (121)]
                         S (+z)
```

### Area descriptions

| Area | Look / what is mined / mobs / NPCs |
|---|---|
| Dwarven Village | Stone-and-lantern village; bank (Banker Broadjaw), Blacksmith (reforge), Bubu pickaxes, Bulvar T12 minions, Station Master, Old Man Garry (events), Rhys, Fragilis, Emissary Eliza, Bednom, Don Expresso. No mobs. |
| Dwarven Tavern | Inside village; Brynmor (consumables), Gimley, Bomin, Hornum, Sargwyn, Tarwen; burping dwarves. |
| Ironman's Guild | Small guild house; Ian, Ivan, Ivy; Ironman mode flavour. |
| The Lift | Elevator exit/entrance to Deep Caverns; Lift Operator and 2 Royal Guards. |
| Gates to the Mines | Transitional gate hub connecting to Forge and Rampart's Quarry; no NPCs now (Ticket Master moved). |
| The Forge / Forge Basin | Large basin with lava falling into it; Forge stations (Forger, 5 anvil stations), Fred, Jotraeline Greatforge, Ticket Master; Powder Ghast can spawn at Forge Basin. |
| Lava Springs | Big lava channel above the Forge; Emissary Lilith; mine Mithril and Titanium; leads to Palace Bridge. |
| Palace Bridge | Long bridge to the Royal Palace; 2 Castle Guards; overlooks Royal Mines. |
| Royal Palace | Castle seat of the kings; King (rotating) and Queen Mismyla, 16 NPCs; lava waterfall with hidden King Talisman head at 129,191,151; fairy soul under lava; Raffle box. |
| Grand Library | Inside palace area; Dalbrek, Tal Ker; Puzzler's room next to it (181.5,196,135.5). |
| Barracks of Heroes / Royal Quarters | Palace sub-locations; castle guards, residents, lava pool, bookshelf. |
| Hanging Court | Palace passage with Dulin and Ticket Master; leads to Base Camp / Glacite (HotM 7 + "Beyond the Wall" quest). |
| Aristocrat Passage | Corridor linking Royal Mines/Palace to the Great Ice Wall; no NPCs. |
| Royal Mines | Below Palace Bridge; Mithril, Coal, Iron, Gold, Lapis, Redstone, Emerald, Diamond, plus Pure Ores (not Quartz); gold-vein lower chamber with rail network; Emissary Wilson, Marigold (Gold Essence shop, 181.5,150,60.5), Ticket Master. |
| Cliffside Veins | Deep vertical area for Mithril and Titanium (+ coal, cobble, iron); Cult of the Fallen Star cave in upper section (Brarnas, Dalir, Thondin, Silnar); Emissary Ceanna 42,134,22; connected to Lava Springs via cult cave at -25,198,40. |
| Rampart's Quarry | Open quarry, Mithril/Titanium farming, Fallen Stars and Goblin Raids; Emissary Carlton -72,153,-10; Ticket Master. |
| Upper Mines | Above Rampart's; mining, Treasure Hoarders, fishing pool in connected cave; Emissary Fraiser -132,174,-50; Ticket Master -134,173,-62. |
| Far Reserve | Mithril veins and wooden platforms; junction to Rampart's/Upper Mines/Goblin Burrows; Ticket Master; Raffle and Fallen Stars happen here. |
| Goblin Burrows | Gold-block passages opening into chambers; Goblins, Knifethrowers, Fireslingers; 3 fairy souls. |
| Great Ice Wall | Barrier to the Glacite Tunnels; 16 Glacite Walkers spawn every 11 s; Pure Emerald blocks; Ticket Master 46.5,131,173.5; overlooks Base Camp via Secret Railroad Pass. |
| Divan's Gateway | Bridge connecting Cliffside Veins to Great Ice Wall; NPC Bylma; Powder Ghast can spawn here. |
| The Mist | White glass / white wool / ice cavern under Divan's Gateway; Ghosts (invisible charged creepers); "DANGER - Powerful creatures reside in The Mist". |
| Abandoned Quarry | Fishing (Mithril Grubber) and Pure Quartz; Guard Gornum, Ticket Master. |
| Miner's Guild | listed but unmapped in the wiki. |
| Dwarven Base Camp | HotM 7+; Dulin, Emissary Lissandra (2.5,121,237), Scouts Plinius/Scardius, Cold Enjoyer, Sor'Hen; gateway to Fossil Research Center and Glacite Tunnels. |

---

## 6. Mobs

Sources: https://hypixelskyblock.minecraft.wiki/w/Goblin, /Weakling, /Knifethrower, /Fireslinger, /Murderlover, /Treasure_Hoarder, /Star_Sentry, /Ghost, /Glacite_Walker, /Powder_Ghast, /Golden_Goblin

| Mob | MC look | Lvl | Health | Damage | Combat XP | Coins | Drops (chance) | Spawn |
|---|---|---|---|---|---|---|---|---|
| Goblin | zombie (green skin, goblin skin) | 23 | 800 | 300 | 34 | 7 | Goblin Helmet/Chestplate/Leggings/Boots (0.3% each, up to 1%), Goblin Egg (0.5-1%) | Goblin Burrows |
| Knifethrower | zombie-like, looks straight down, random leather armour; throws knives | 23 | 800 | 300 | 34 | 7 | Goblin Egg 0.5%, armour pieces 0.3% each, 5% chance 500-10,000 coins | Goblin Burrows (+ CH Holdout) |
| Fireslinger (lvl 23) | goblin; throws fireballs, flees | 23 | 800 | 300 | 34 | 7 | armour 0.3% each, Goblin Egg 2% (?), 5% 500-10,000 coins | Goblin Burrows |
| Fireslinger (Goblin Raid) | same | 3 | 200 | 20 | 5 | 1 | 5% 500-10,000 coins | Goblin Raid |
| Weakling | goblin, slow, rarely attacks | 3 | 200 | 20 | 5 | 1 | - | Goblin Raid (+ CH Holdout) |
| Goblin Murderlover (lvl 47) | goblin with fishing rod | 47 | 50,000 | 50 | 70 | 15 | - | Goblin Raid |
| Goblin Murderlover (lvl 96) | same | 96 | 80,000 | 800 | 144 | 32 | Goblin Eggs: Goblin 0.6%, Green 0.25%, Yellow 0.1%, Red 0.04%, Blue 0.01% | Goblin Raid |
| Creeperlobber / Pitfighter / Superprotectron | goblin-types | not found | not found | | | | | Goblin Raid / event (?) |
| Golden Goblin | gold-tinted goblin, runs/jumps off cliffs | 99 | dies in 3 hits | 250 | 148 | 33 | 5 Mithril (100%), 2-3 Gold Essence (+1 at 30%), 500 Mithril Powder | 0.1% (1/1000) per ore mined; Goblin Egg throw 2-10%; despawns after 30 s |
| Diamond Goblin | diamond-tinted goblin | (?) | (?) | (?) | (?) | (?) | 1,000 Mithril Powder (5 Mithril? (?)) | 3.33% of Golden Goblin spawns |
| Treasure Hoarder | humanoid, thrown pickaxe 1,000 dmg (12 s cooldown) | 61 | 15,000 | 500 | 91 | 20 | 1-2 Starfall (100%), Kaleidoscopic Mineral 1%, Treasurite 0.5% | Upper Mines |
| Star Sentry | armour-stand-style sentry at the Fallen Star | 135 | 10 (takes exactly 1 per hit) | 400 | 202 | 45 | 5 Mithril (100%), Starfall 20% | Fallen Star event, all mining zones |
| Ghost | invisible charged creeper | 157 | 1,000,000 | 1,000 (+100 true) | 100 | 52 | Volta 0.2%, Sorrow 0.12%, Plasma 0.1%, Ghostly Boots 0.01%, 1,000,000 coins 0.01% | The Mist |
| Glacite Walker | glacial humanoid (wolf-like husk (?)) | 24 | 888 (def 1,000) | 300 | 36 | 8 | Glacite armour piece 0.5%, Glacite Jewel 0.5% | Great Ice Wall (16 spawn every 11 s) |
| Ice Walker | not found on mirror | (?) | (?) | (?) | (?) | (?) | (?) | Glacite Tunnels |
| Powder Ghast | ghast, only hurt by projectiles (melee/magic immune) | 55 | 50 (?) | 1 | 82 | 18 | 100-250 Mithril Powder per hit (~17,500 avg total), 60 xp orbs | 0.02% (1/5000) per Mithril mined, 10 min cooldown, spawns at Divan's Gateway/Rampart's/Cliffside/Forge Basin; despawns after 1 minute without hits |
| Bloated Mithril Grubber | fishing mob | | | | | | 5,000 Mithril Powder | Abandoned Quarry fishing |

---

## 7. NPCs and shops

Sources: https://hypixelskyblock.minecraft.wiki/w/King, /Emissary, /Bubu, /Brynmor, /Bulvar, /Marigold, /Fragilis, /Puzzler, /Dwarven_Mines

| NPC | Role | Location |
|---|---|---|
| King (rotating) | starts commissions; 7 dwarves rotate every 20 min, counter-clockwise: Brammor, Emkam, Redros, Erren, Thormyr, Emmor, Grandan. Meeting all 7 grants King Talisman + "A Royal Meeting" achievement. Others say "Today the King is X". | Royal Palace |
| Queen Mismyla | constant, Abiphone commission contact | Royal Palace |
| Emissaries (open commissions menu after Commissions Milestone I) | | Carlton Rampart's -72,153,-10; Ceanna Cliffside 42,134,22; Wilson Royal Mines 171,150,31; Lilith Lava Springs 58,198,-8; Fraiser Upper Mines -132,174,-50; Eliza Village -37,200,-131; Braum entrance 89,198,-92 (gives Crystal Hollows commissions); Sisko Crystal Nucleus 495,105,556; Lissandra Base Camp 2.5,121,237 |
| Rhys | intro quest "There are Dwarves?" | Obsidian Sanctuary / Village |
| Bubu | early pickaxe shop (and Biofuel) | Village -10.5,201,-103.5 |
| Bulvar | Tier XII mining minions | Village -15.5,201,-98.5 |
| Banker Broadjaw | bank | Village |
| Blacksmith | reforge | Village |
| Station Master / Ticket Masters | paid minecart fast travel | village + most zones |
| Old Man Garry | event info | Village |
| Don Expresso | Mithril Gourmand event | Village |
| Fragilis | Handy Block Guide (`/handyblockguide`, `/hbg`), no shop | 88,199,-108 |
| Brynmor | consumable shop | Tavern 32,202,-154.5 |
| Marigold | Gold Essence shop (must kill a Golden Goblin first, gives 25 Gold Essence) | Royal Mines 181.5,150,60.5 |
| Puzzler | daily puzzle: 2,500 Mithril Powder (24 h cooldown after completion) | next to Grand Library 181.5,196,135.5 |
| Fetchur | daily item turn-in: 2,500 Mithril Powder + 20,000 coins (as reported by the other mirror, ?) | (?) |
| Forger / Fred | Forge stations; Fred = Forge Abiphone contact | The Forge |
| Jotraeline Greatforge | drill upgrades | Forge Basin |
| Geo | gemstone application | (?) |
| Gwendolyn | Crystal Hollows pass (HotM IV (?), coins fee, 6 h) | (?) |
| Gimley, Bomin, Hornum, Sargwyn, Tarwen | tavern flavour | Tavern |
| Silnar / Brarnas / Dalir / Thondin | Cult of the Fallen Star | Cliffside Veins |
| Lumina | light sources | (?) |

### Shop offers

| Shop | Item | Price | Requirement |
|---|---|---|---|
| Bubu | Fractured, Bandaged, Mithril, Refined Mithril, Rusty Titanium, Titanium, Polished Titanium Pickaxe (crafting-style cost, e.g. Mithril Pickaxe = Bandaged + 64 Ench Mithril + Bejeweled Handle + 32 Ench Gold, HotM 2); Titanium Pickaxe = 4 Ench Titanium + Bejeweled Handle (+ lower pick (?)); Biofuel | coins not found | HotM tiers 2-6 |
| Brynmor | Powder Pie (+15% Mithril Powder, 60 min) | 50,000 | HotM 1 |
| Brynmor | Filet O' Fortune (+20 Mining Fortune, 60 min) | 100,000 | HotM 4 |
| Brynmor | Chilled Pristine Potato (+0.1 Pristine in Glacite Mineshafts) | 250,000 | HotM 7 |
| Brynmor | Fiesta Flask / Packrat Shard | 2.5M / 250k | Mining Fiesta mayor event |
| Bulvar | Tier XII Cobblestone, Obsidian, Coal, Iron, Gold, Diamond, Lapis, Emerald, Redstone, Mithril, Hard Stone minions; Lime Dye; Hoppers | prices not found | - |
| Marigold (Gold Essence) | Heart of Gold V (5,000), Treasure of the Earth V (3,000), Dwarven Training III (5,000), Unbreaking V (3,000), Eager Miner X (5,500, up to +100 mining speed on Mining Islands), Midas Lure X (11,000) | listed = max total cost | Golden Goblin kill |

---

## 8. Commissions

Source: https://hypixelskyblock.minecraft.wiki/w/Commissions (list is partial; per-commission weights not found)

Board mechanics:
- Start with 2 slots, +1 slot after 100 completed commissions (3 total), +1 slot from the HotM Core of the Mountain perk (level (?) 5 per wiki) = 4.
- Slots 1&3 and 2&4 share a quest pool and cannot repeat a type. Completed commissions are replaced.
- Opening the board needs Commissions Milestone I (via King, Emissaries, Royal Pigeon, Queen Mismyla's Abiphone).
- Milestones: 5 commissions = Royal Compass + 100k Mining XP; 100 = +1 slot; 500 = Royal Pigeon.

Dwarven Mines commissions:

| Commission | Objective |
|---|---|
| Goblin Slayer | Slay 100 Goblins |
| Glacite Walker Slayer | Slay 50 Glacite Walkers |
| Mithril Miner | Mine 350 Mithril Ore |
| `<Area>` Mithril | Mine 250 Mithril Ore in the area (area-specific: e.g. Lava Springs, Cliffside Veins, Rampart's Quarry, Upper Mines) |
| Titanium Miner | Mine 15 Titanium |
| `<Area>` Titanium | Mine 10 Titanium Ore in the area |
| Treasure Hoarder Puncher | Damage Treasure Hoarders 10 times |
| Star Sentry Puncher | Punch Star Sentries 10 times |
| Event: Goblin Raid, Goblin Raid Slayer, Elusive Goblin Slayer, Raffle, Lucky Raffle, 2x Mithril Powder Collector | only while the event runs; amounts not found |
| Starter (tutorial) | First Event, Mithril Miner (50/150), Titanium Miner (2), Mines Slayer |

Rewards:

| Location | Mining XP | HotM XP | Powder |
|---|---|---|---|
| Dwarven Mines | 5k-10k | 100 (HotM 1), 200 (HotM 2), 400 (HotM 3+ and 0 at HotM 10 per other note (?)) | 500 Mithril Powder |
| Crystal Hollows | 10-20k | 400 | 1,000 Gemstone |
| Glacite Tunnels | 12.5-25k | 750 | 1,500 Glacite |

Daily bonus: the first 4 commissions each day give +900 HotM XP (until HotM maxed; then extra powder instead).

---

## 9. Heart of the Mountain

Sources: https://hypixelskyblock.minecraft.wiki/w/Heart_of_the_Mountain

| Tier | XP for this tier | Cumulative | Tokens |
|---|---|---|---|
| 1 | 0 | 0 | 1 |
| 2 | 3,000 | 3,000 | 2 |
| 3 | 9,000 | 12,000 | 2 |
| 4 | 25,000 | 37,000 | 2 |
| 5 | 60,000 | 97,000 | 2 |
| 6 | 100,000 | 197,000 | 2 |
| 7 | 150,000 | 347,000 | 3 |
| 8 | 210,000 | 557,000 | 2 |
| 9 | 290,000 | 847,000 | 2 |
| 10 | 400,000 | 1,247,000 | 2 |

The XP list matches NEU ([0,3000,9000,25000,60000,100000,150000,210000,290000,400000]); cumulative 1,247,000 confirmed. The "Tokens" column above is the mirror's raw
output (it looked garbled: it printed +35/+45/... in a combined column), so token counts per tier are (?) - verify against the NEU hotmlayout (typical SkyBlock is 1 token at HotM 1 and 2 per tier, 3 at HotM 7 (?)). SkyBlock XP rewards per tier 1,3,5,7,9,11,14,16,18,20 (?).

Unlocks (conflicting sources, (?)): Forge at HotM 2; Mithril Pickaxe purchase at HotM 2; Crystal Hollows access HotM 4 per one page / 6 per the other (?); Base Camp + Glacite Tunnels HotM 7 (Dwarven page) vs 8 (HotM table) (?);
4th commission slot via Core of the Mountain 5 (?); emblems at tiers 5, 6, 10 (Mining Helix / Golden / Diamond). Tokens are spent in the perk tree; a perk can be bought when adjacent to an unlocked perk
and its row requirement (HotM level) is met (use NEU hotmlayout). Peak of the Mountain: the last (capstone) perk, upgradable with powder; the wiki summary on the "Core of the Mountain" says levels are kept on tree reset ("Tier and Core of the Mountain levels are kept").

XP sources: commissions (base 100/200/400 by tier, CH 400, Glacite 750), first 4 daily commissions +900, mining events, Crystal Nucleus runs (+800).
Pickaxe abilities unlocked by HotM: tiers 2, 6, 10 (Mining Speed Boost, Pickobolus, Tunnel Vision, Maniac Miner...).

---

## 10. Powder

Sources: https://hypixelskyblock.minecraft.wiki/w/Mithril_Powder, /w/Powder_Ghast, /w/Golden_Goblin, /w/Puzzler

| Source | Mithril Powder |
|---|---|
| Mithril tier 1 block | 1 |
| Mithril tier 2 block | 2 |
| Mithril tier 3 block | 5 |
| Fallen Star active | +2 per Mithril mined |
| Commission (Dwarven Mines) | 500 |
| Golden Goblin | 500 |
| Diamond Goblin | 1,000 |
| Bloated Mithril Grubber | 5,000 |
| Powder Ghast | 100-250 per hit |
| Puzzler (daily) | 2,500 |
| Fetchur (daily) | 2,500 (?) |
| HotM perks Daily Grind / Daily Powder | 500 per tier/level |
| Multipliers | Powder Buff perk, drills up to +40%, Powder Pie (+15%), 2x Powder event doubles all |

Gemstone Powder: from gemstone blocks and Crystal Hollows commissions (1,000). Glacite Powder: Glacite Tunnels (1,500 per commission).

---

## 11. The Forge

Sources: https://hypixelskyblock.minecraft.wiki/w/The_Forge, /w/Quick_Forge

- Requires Heart of the Mountain tier 2. You start with 2 slots at HotM 2; each further HotM tier up to 7 gives one more slot (max 7 slots). (The Quick Forge page printed a different "+2 per tier" table; I consider it garbled (?).)
- Slot unlocks cost nothing; they come only from HotM tier.
- Forging: choose a recipe at the Forger, deposit ingredients (and coins for some), the item is "forging" for its duration, you come back and claim. Forging cannot be cancelled. Items are processed in parallel by slot.
- Time reduction: Quick Forge perk (10.5% at level 1 to 30% at level 20, 21 levels, costs Mithril Powder), mayor Cole -25%, Gemstone Gauntlet ability -0.5 s per perfect gemstone.
- Staff: the Forger and Fred (Abiphone). Jotraeline Greatforge handles drill upgrades in the Forge Basin.
- NEU has the 124 recipes with durations.

---

## 12. Events

Source: https://hypixelskyblock.minecraft.wiki/w/Mining_Events (one event cycle every 20 minutes). Gone with the Wind not found on the mirror (probably a Crystal Hollows event (?)).

| Event | Type | Description |
|---|---|---|
| 2x Powder | Passive | all powder (mithril, gemstone, glacite) doubled |
| Better Together | Passive | extra Mining Speed and Mining Fortune when in the same zone as other players, up to 5 players |
| Gone with the Wind | Passive (?) | not documented on the mirror (?) |
| Goblin Raid | Active | hordes of goblins (Weakling, Fireslinger, Murderlover, ...) appear in a zone; defeat them within 5 minutes to claim rewards |
| Raffle | Active | raffle box spawns; collect tickets in the zone and deposit them; more tickets = better odds |
| Mithril Gourmand | Active | mining Mithril in the named area can drop Tasty Mithril; the community needs 200 total; 10+ personal for bonus; Don Expresso |
| Fallen Star | Random | star falls in a zone; increased Mithril/Titanium yield (+2 powder per Mithril), Star Sentries spawn, Starfall dropped |
| Powder Ghast | Random | ghast appears; shoot it repeatedly for Mithril Powder per hit |

---

## Biggest uncertainties

1. Official wiki is gone and Fandom is paywalled, so every number came from a summarized community mirror.
2. Mining XP, required BP for Gold/Lapis/Redstone/Emerald/Quartz, and regen times are (?).
3. HotM tokens per tier, Crystal Hollows/Glacite unlock tiers, Forge slot per tier.
4. Warp command names, Fetchur/Geo/Gwendolyn/Lumina locations, Gone with the Wind, Ice Walker, Diamond Goblin stats, Bubu prices, Bulvar prices, per-commission weights.
5. Minecraft block ids for gemstones, Tungsten, Glacite, Sulphur.
