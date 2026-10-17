# Crystal Hollows research

Source note: same as dwarven-mines.md. The official wiki is gone and Fandom blocks us. All wiki data comes from the community mirror
https://hypixelskyblock.minecraft.wiki/w/<Page> read through a lossy summarizer, so values are "good, not certified". (?) = unsure or my own recollection.
The gemstone stat tables and slot costs in section 9 are exact: generated from the NEU repo cache (constants/gemstones.json, constants/gemstonecosts.json).
Coordinates are in-game x,y,z. Mining-speed formula, Breaking Power and block strengths: see dwarven-mines.md sections 1-3 (reused, not repeated).

---

## 1. Access

Sources: https://hypixelskyblock.minecraft.wiki/w/Crystal_Hollows, /w/Gwendolyn, /w/Heart_of_the_Mountain

| Item | Value |
|---|---|
| Requirements | Mining level 12 and Heart of the Mountain tier IV (raised from III to IV on 2024-08-20) |
| Gwendolyn NPC | Dwarven Mines, at 88.5, 198, -98.5; sells the pass |
| Pass | 10,000 coins for a 6 hour pass |
| Free pass | after reaching HotM VII and helping Dulin (meet him at the Hanging Court near the Royal Palace) the pass is free (changed April 2024) |
| Route | pass from Gwendolyn, then the minecart/track from the Dwarven Mines drops you into a Hollows lobby (exact rail location not found) |
| Warps | a `/warp` for the Hollows existed; exact name not found on the mirror (?) |
| Instance | a lobby per server, randomly generated layout each time |
| Lobby lifecycle | "Day" 0 lobby opens; day 19 lobby locks to newcomers (and shuts down if fewer than 4 players); day 35 forced shutdown (36 day lifespan). Length of a "day" not found (?) |
| Size | resized from 1024x256x1024 to 621 x 157 x 621 blocks (y about 31..188 and x/z about 202..823 from memory (?)) |
| Broken blocks | not replaced by bedrock: they become air (nothing regenerates) |
| Smelting | Iron/Gold ore auto-smelt without Smelting Touch |
| Base ore yields | Coal 4-6; Lapis 9-14; Redstone 9-14; Emerald 4-6; Diamond 4-6 |

Implementation hint: a private instance per player (copy of a generated world) is the easy 1:1; keep the day-19 / day-35 timers as a simple real-time despawn (my suggestion).

---

## 2. Layout

Sources: /w/Crystal_Hollows, /w/Jungle, /w/Mithril_Deposits, /w/Goblin_Holdout, /w/Precursor_Remnants, /w/Magma_Fields, /w/Crystal_Nucleus

The Nucleus is at 495.5, 106, 556.5 (from Emissary Sisko's entry). Exact zone bounds/sizes are not on the mirror (zones are "randomly generated" within the quadrants).
Magma Fields is described as "at or below Y=64". From memory (?): the map is split into 4 quadrants plus the Nucleus in the centre; lava lake layer underneath.

```
 N (-z)
 +-------------------------+-------------------------+
 | JUNGLE (NW)             | MITHRIL DEPOSITS (NE)   |
 |  Jungle Temple          |  Mines of Divan         |
 |  Odawa, Kalhuiki        |  Forger, 4 Keepers      |
 |  amethyst               |  jade, mithril, titan.  |
 |            +------------+-----------+             |
 |            |   CRYSTAL NUCLEUS      |             |
 |            |   (495,106,556)        |             |
 |            +------------+-----------+             |
 | GOBLIN HOLDOUT (SW)     | PRECURSOR REMNANTS (SE) |
 |  Goblin Queen's Den     |  Lost Precursor City    |
 |  King Yolkar, amber     |  Prof. Robot, sapphire  |
 +-------------------------+-------------------------+
 | MAGMA FIELDS (y <= 64, bottom layer, reaches Nucleus)|
 |  Khazad-dum (Bal), topaz, heat                       |
 +------------------------------------------------------+
 random: 3 Fairy Grottos (jasper), Dragon's Lair, Deep Caverns replica, Spider's Den skull, Dark Auction copy, Forge miniature, Tavern
```

### 2a. Zones

| Zone | Main gemstone | Look | Mobs | First-visit objective / notes |
|---|---|---|---|---|
| Jungle | Amethyst (also Jade/Sapphire mentioned as mineable) | lush jungle caves, vines, leaves, water | Sludge, Kalhuiki Tribe (Youngling, Elder), Key Guardian, Thyst, Abyssal Miner (fished) | "Fight Sludges", "Mine Amethyst Gemstones", "Meet the local tribe!" |
| Mithril Deposits | Jade | farmland-like ground with minecart tracks; mithril veins | Team Treasurite (Grunt, Executives), Boss Corleone, Crypt Undead | Forger NPC, Keepers |
| Goblin Holdout | Amber | rocky terrain, goblin camp; fixed pond at 358, 167, 534; rare Nether Quartz Ore/Netherrack (XP only) | Goblins (higher tiers by King Yolkar's Tower and the Deep Goblin Lair), Water Worm, Poisoned Water Worm | "rich with Amber"; added 2021-06-30 |
| Precursor Remnants | Sapphire | ancient machine ruins | Automaton, Flaming Worm | "Mine Sapphire Gemstones. Look out for Automatons!" |
| Magma Fields | Topaz (+ Ruby) | lava cave at y <= 64, heat | Yog, Bal, Lava Blaze, Lava Pigman, Fire Bat, Bald Blaze | Heat meter: "You are heating up!" -> "You are melting!" -> "Time to leave!"; at +100 heat "You burned to death." |
| Everywhere | Ruby | | Stoneworm/Scatha, Sneaky Creeper, Lapis Zombie, Redstone Pigman, Emerald Slime, Miner | |

Chunk (Precursor): 797.5, 128.5, 708.5, sells Superboom TNT 2,500 coins.

### 2b. Structures

| Structure | Zone | Description / what happens |
|---|---|---|
| Jungle Temple | Jungle (random spot) | give a Jungle Key to the Kalhuiki Door Guardian; 8 simple parkours with traps; lava fall returns you to the entrance; jump boost and bonus speed are removed inside. End: Amethyst Crystal + 2 loot chests (better Jungle Heart chance). Claim again only with another Jungle Key. |
| Mines of Divan | Mithril Deposits | Metal Detector treasure hunt, 4 Keepers, Jade Crystal; plenty of Jade ore and gold/diamond/emerald/redstone ore |
| Goblin Queen's Den | Goblin Holdout | needs King's Scent (1 Goblin Egg to King Yolkar); Amber Crystal; a Wishing Compass with King's Scent points to it; claiming removes the buff (since 0.20.6). Stealth: avoid/kill goblins (details not found) |
| King Yolkar's Tower / Deep Goblin Lair | Goblin Holdout | King Yolkar NPC; higher level goblins |
| Lost Precursor City | Precursor Remnants | ancient robot city; Professor Robot (roams) takes the 6 parts; doors can be right-clicked to vanish and return after the spawned Automatons die |
| Khazad-dum | Magma Fields | arrival text "Deep below the Misty Mountains lies the shadow and flame guarding ancient treasure..."; Bal spawns centrally (about every minute while players are near); Topaz Crystal spawns nearby after victory; Wishing Compass in Magma Fields points here; Eternal Flame Ring thrown into its lava gives the achievement "The Ring" |
| Fairy Grotto | random (3 per world) | Jasper gemstones (details not found) |
| Dragon's Lair | random | Golden Dragon NPC sells the Golden Dragon Pet |
| Replicas | random | Deep Caverns replica, Spider's Den skull, Dark Auction copy, Forge miniature, pre-Hub Tavern |
| Crystal Nucleus | centre | five crystal placements; Emissary Sisko, Gemma, Geonathan Greatforge |

---

## 3. Blocks

Sources: /w/Gemstone, /w/Hard_Stone, /w/Block_Strength (outdated), /w/Mithril, /w/Titanium, /w/Gemstone_Powder. Strengths and min mining speed reused from dwarven-mines.md 2.

### 3a. Hard Stone

| Property | Value |
|---|---|
| Block | stone, strength 50, Breaking Power 4; cyan terracotta variant strength 100 (Hollows only) |
| Instamine | mining speed 1,501 (stone) / 3,001 (cyan terracotta) |
| Drop | 1 Hard Stone (Mining Fortune applies) |
| Collection | 7 tiers: I Hard Stone Minion recipe, II Heat Armor recipes, III Enchanted Hard Stone (576 Hard Stone), higher tiers Flamebreaker Armor and Silex |
| Regeneration | none in the Hollows: the block becomes air |
| Side effects per mined block | 0.2% dug treasure chest (section 4); 0.167% (1 in 600) Stoneworm spawn, which is a Scatha 20% of the time; 30 s cooldown after each spawn |
| HotM | tier 8 perk gives bonus mining speed when not on solid ground (hidden Hard Stone buff) |

### 3b. Gemstone blocks

Each gemstone is a stained glass block plus a stained glass pane of the same colour (mined together). Breaking Power and base yield from /w/Gemstone; strength from dwarven-mines.md 2c.

| Gemstone | Zone | Glass colour | Strength | Req. BP | Min MS to instamine | Drop |
|---|---|---|---|---|---|---|
| Ruby | everywhere | red | 2,300 | 6 | 138,001 | 3-5 Rough Ruby |
| Amethyst | Jungle | purple | 3,000 | 7 | 180,001 | 3-5 Rough |
| Jade | Mithril Deposits | lime | 3,000 | 7 | 180,001 | 3-5 Rough |
| Amber | Goblin Holdout | orange | 3,000 | 7 | 180,001 | 3-5 Rough |
| Sapphire | Precursor Remnants | light blue | 3,000 | 7 | 180,001 | 3-5 Rough |
| Opal | Smoldering Tomb (Crimson Isle), not the Hollows | white | 3,000 | 7 | 180,001 | 3-5 Rough |
| Topaz | Magma Fields | yellow | 3,800 | 8 | 228,001 | 3-5 Rough |
| Jasper | Fairy Grottos | magenta (wiki says pink) | 4,800 | 9 | 288,001 | 3-5 Rough |
| Aquamarine / Citrine / Onyx / Peridot | Glacite Tunnels and Mineshafts only | light blue, brown, black, green (?) | 5,200 | 9 | 312,001 | 3-5 Rough |

- The Pristine stat (Topaz) can upgrade drops from Rough to Flawed.
- Gemstone Powder per block: the mirror gives no per-block numbers; the only quantified perk is Core of the Mountain "+2 Gemstone Powder per Gemstone mined". Design call: powder per block = the number of rough gems dropped, plus perks, doubled during 2x Powder (my suggestion, not verified).
- Mining XP per gemstone block: not found (?).
- Thyst (Jungle) spawns with 20% chance when an Amethyst is broken.

### 3c. Mithril and Titanium in the Mithril Deposits

| Block | MC block | Strength | BP | Drops | Powder | Notes |
|---|---|---|---|---|---|---|
| Mithril tier 1 | gray wool, cyan terracotta | 500 | 4 | 1 Mithril | 1 | |
| Mithril tier 2 | prismarine variants | 800 | 4 | 2 Mithril | 2 | |
| Mithril tier 3 | light blue wool | 1,500 | 4 | 5 Mithril | 5 | |
| Titanium | polished diorite | 2,000 | 5 | 2 Titanium | none known | 0.5% base chance to replace a mined mithril block (2% Titanium Insanium) |
| Pure Gold block | gold block | 600 | 3 (?) | Gold | | present in the Mithril Deposits |

Nothing respawns in the Hollows (blocks become air).

---

## 4. Treasure chests

Source: https://hypixelskyblock.minecraft.wiki/w/Treasure_Chest

| Rule | Value |
|---|---|
| Dug chest base chance | 0.2% (1 in 500) per Hard Stone mined |
| Final chance | base + Great Explorer bonus + Scatha pet Burrowing perk; maximum 1.7% (1 in 58.82) |
| Great Explorer | dev formula bonus = (20 + 4 x (level - 1)) / 100, i.e. +20% at level 1 to +100% at level 20 (tooltip shows lower numbers); also cuts the number of locks from 5 down to 0 (instant open) |
| Lockpick | aim at the particle "locks" on a face of the chest 5 times in a row; sound cue per lock |
| Rolls | 3-6 loot rolls (avg 4.5); Bigger Box attribute +5%..+50% extra-roll chance |
| Powder modifiers | drills +5%..+20% gemstone powder; legendary Scatha pet +0.2%..+20% |
| Chest kinds | Dug (Hard Stone), Structure (in structures), Metal Detector (Mines of Divan), Nucleus chests |
| Loot | Gemstone Powder, Mithril Powder, Rough gemstones (68% of Divan chest loot), Scavenged items (18% in Divan chests), Pickonimbus 2000 (0.06% in Divan chests) |
| Loot weights | exact table not found (?) |

---

## 5. Mobs

Sources: https://hypixelskyblock.minecraft.wiki/w/ Automaton, Yog, Sludge, Thyst, Boss_Corleone, Team_Treasurite, Bal, Scatha, Goblin

| Mob | MC look | Lvl | Health | Damage | Combat XP | Coins | Drops | Where |
|---|---|---|---|---|---|---|---|---|
| Sludge small | slime | 47 | 5,000 | 500 | 70 | 15 | Sludge Juice 100% | Jungle, Temple |
| Sludge medium | slime | 60 | 10,000 | 700 | 90 | 20 | splits into 2 small | Jungle |
| Sludge large | slime | 78 | 25,000 | 900 | 117 | 26 | splits into 2 medium | Jungle |
| Thyst | endermite | 39 | 5,000 | 250 | 58 | 13 | 4-7 Rough Amethyst | Jungle; 20% on breaking Amethyst |
| Kalhuiki Tribe Member / Youngling / Elder | (?) | (?) | (?) | (?) | (?) | (?) | do not attack the Younglings (Odawa warns) | Jungle |
| Key Guardian | (?) | (?) | (?) | (?) | (?) | (?) | Jungle Key | Jungle |
| Abyssal Miner | fished | (?) | (?) | (?) | (?) | (?) | (?) | Jungle water |
| Grunt | humanoid | 54 | 15,000 | 300 | (?) | (?) | 8-15 Rough Ruby or Jade, 0-1 Treasurite | Mithril Deposits, 76.34% of spawns |
| Executive Sebastian / Wendy / Viper | humanoid | 62 | 18,000 | 450 | (?) | (?) | same as Grunt | 7.63% each |
| Boss Corleone | humanoid boss | 195 | 1,000,000 | 4,000 | 292 | 65 | 18-41 Rough Jade, 15-27 Rough Ruby, Corleonite 25% (everyone who attacked gets it) | Mithril Deposits, 2 spawn rooms; 0.76% of spawns; every 60-120 s there |
| Smog (summon) | small wither | (?) | 10,000 | 1,500 | | | | with Corleone |
| Silvo, Belle, Cavitak, Vittomite | thrown-ball summons ("[mob] go!") | (?) | | | | | | Team Treasurite |
| Goblin | zombie, head tilted down | 23 | 800 | 300 | 34 | 7 | coin bag 5% (500-10,000), Goblin Egg 1%, armor pieces 0.3% each | Holdout (Dwarven Mines too) |
| Goblin Knifethrower, Weakling, Goblin Queen | | (?) | (?) | (?) | (?) | (?) | not found | Holdout |
| Water Worm / Poisoned Water Worm | worm | (?) | (?) | (?) | (?) | (?) | Worm Membrane | Holdout |
| Automaton | robot with laser eyes (pass through walls) | 67 | 15,000 | 750 | 100 | 22-23 | 8-18 Rough Sapphire; one Automaton Part (exclusive) | Precursor Remnants |
| Advanced Automaton | robot | 71 | 20,000 | 750 | 106 | 22-23 | 10 Rough Sapphire; one part | Lost Precursor City |
| Flaming Worm | worm | (?) | (?) | (?) | (?) | (?) | (?) | Precursor Remnants |
| Yog | magma cube | 93 | 35,000 | 1,500 | 139 | 31 | 8-15 Rough Topaz (8 guaranteed), Yoggie 6%; red bar then death explosion after about 4 s (up to 10,000 dmg) | Magma Fields, Khazad-dum |
| Bal | magma cube boss | 263 | (?) the page says "200"/"100 damage", surely a mis-summary | magma-cube-like contact + fireballs | 1,972 | 657 | Yoggie, Bal Pet (epic/legendary), Flawed gems, Topaz Crystal | Khazad-dum; spawns about each minute; 0.3 s invulnerability frames; summons Fire Bats and Bald Blazes |
| Fire Bat, Bald Blaze | bat, blaze | (?) | | | | | | Bal minions |
| Lava Blaze, Lava Pigman | blaze, pigman | (?) | | | | | Magma Core | Magma Fields |
| Stoneworm | segmented worm | (?) | | | | | | spawned by Hard Stone |
| Scatha | segmented worm (10 segments) | 181 | 10 (one per segment hit) | 2,500 | 271 | 60 | 4 Worm Membrane, 1 Fine Topaz, other Fine gems; pet 0.24% rare / 0.12% epic / 0.04% legendary | Hard Stone 0.167% x 20%, 30 s cooldown; message "You hear the sound of something approaching..." |
| Sneaky Creeper, Lapis Zombie, Redstone Pigman, Emerald Slime, Miner | vanilla-ish | (?) | | | | | ore-themed | everywhere |

Bestiary (Hollows families, tier X): +25 Strength, +25 Magic Find, +20% coins, +10 SkyBlock XP.

---

## 6. The Crystal Nucleus

Sources: /w/Crystal_Nucleus, /w/Mines_of_Divan, /w/Lost_Precursor_City, /w/Jungle_Temple, /w/Goblin_Queen%27s_Den, /w/Khazad-d%C3%BBm

| Crystal | Where | How |
|---|---|---|
| Jade | Mines of Divan | get a Metal Detector from any Keeper; find treasure chests for 4 scavenged items and hand each to its Keeper: Scavenged Diamond Axe (Keeper of Diamond), Emerald Hammer (Emerald), Golden Hammer (Gold), Lapis Sword (Lapis); then claim. Average 22.22 treasures (no duplicates). |
| Amber | Goblin Queen's Den | 1 Goblin Egg to King Yolkar for King's Scent, enter the den, claim; the buff is removed |
| Topaz | Khazad-dum | kill Bal; the crystal spawns nearby and is claimable |
| Sapphire | Lost Precursor City | Professor Robot takes 6 Automaton Parts (Electron Transmitter, FTX 3070, Robotron Reflector, Superlite Motor, Control Switch, Synthetic Heart) or a Precursor Apparatus, then opens the crystal chamber |
| Amethyst | Jungle Temple | Jungle Key (Odawa for Sludge Juice, or Key Guardian drop) to the Kalhuiki Door Guardian, then 8 parkours |

Nucleus rules: crystals stay placed when you leave; cannot be picked up; no duplicate crystals until all 5 are placed.
Run reward: a loot bundle of 14-19 random pieces plus guaranteed items, including 1 of every Fine gemstone found in the Hollows; HotM XP 800 below HotM level 10, and at max level 2,000-7,000 Mithril or Gemstone Powder instead.
RNG meter: placing all crystals gives 1,000 Nucleus XP; Divan's Alloy needs 1,000,000, Pickonimbus 2000 needs 4,000. Other loot: Quick Claw, fossils, enchanted books, Divan Fragments, mixtures, Jade Dye (0.0002% per roll).
Note: the "4 Divan parts" in the brief are the four Keeper items above (scavenged tools); Divan Fragments and Alloy are separate Nucleus rewards.

---

## 7. NPCs

Sources: /w/Gwendolyn, /w/Odawa, /w/Crystal_Hollows, /w/Mines_of_Divan, /w/Goblin_Holdout, /w/Precursor_Remnants

| NPC | Where | Role |
|---|---|---|
| Gwendolyn | Dwarven Mines 88.5, 198, -98.5 | sells the 6 h pass (10,000 coins) |
| Dulin | Hanging Court, Royal Palace | quest that makes the pass free |
| Emissary Sisko | Nucleus | commissions |
| Gemma | Nucleus | Diamond Essence shop |
| Geonathan Greatforge | Nucleus | drill upgrades |
| Odawa | Jungle | trades for Sludge Juice: Kalhuiki Mask, Jungle Amulet, Tribal Spear, Jungle Pickaxe, Jungle Key, Rough Amethyst (prices not found) |
| Kalhuiki Door Guardian | Jungle Temple | takes a Jungle Key |
| Sludge (NPC) | Jungle | rewards treasure chests after rescue |
| Forger | Mithril Deposits | The Forge |
| Keepers of Diamond/Emerald/Gold/Lapis | Mines of Divan | Metal Detector; trade scavenged tools for the Jade Crystal |
| Professor Robot | Lost Precursor City | Automaton Parts for the Sapphire Crystal |
| Chunk | 797.5, 128.5, 708.5 | Superboom TNT 2,500 coins |
| King Yolkar | Goblin Holdout | King's Scent for 1 Goblin Egg |
| Xalx | Goblin Holdout | Mysterious Meat |
| Golden Dragon | Dragon's Lair | Golden Dragon Pet |
| Geo | gemstone grinder (location not found) | apply/remove gemstones |
| Hoppity, Bal (NPC), Tom, Lumina | not found | n/a |

---

## 8. Commissions

Source: /w/Commissions (partial summary)

| Commission | Objective | Amount |
|---|---|---|
| Goblin Slayer | kill Goblins | (?) |
| Automaton Slayer | kill Automatons | (?) |
| Sludge Slayer | kill Sludges | (?) |
| Yog Slayer | kill Yogs | (?) |
| Gemstone / crystal tasks (mine X gemstones, find crystals) | mining | (?) |

Rewards (Hollows tier): 10-20k Mining XP, 400 HotM XP, 1,000-2,000 Gemstone Powder. For comparison Dwarven Mines: 5-10k XP, 100-400 HotM, 500-1,000 Mithril Powder. The first four daily commissions give extra bonuses until HotM maxes out.

---

## 9. Gemstones as items

Sources: /w/Gemstone, /w/Gemstone_Slot, NEU constants (exact)

| Rule | Value |
|---|---|
| Item ids | ROUGH_/FLAWED_/FINE_/FLAWLESS_/PERFECT_<TYPE>_GEM (NEU form, e.g. FINE_JADE_GEM) |
| Upgrade | 80 of a tier craft 1 of the next (Rough, Flawed, Fine, Flawless); Perfect = 5 Flawless + the matching Gemstone Crystal (forged) |
| Types | Ruby, Amber, Topaz, Jade, Sapphire, Amethyst, Jasper, Opal, Aquamarine, Citrine, Onyx, Peridot |
| Stat | Ruby Health, Amethyst Defense, Jade Mining Fortune, Amber Mining Speed, Sapphire Intelligence, Topaz Pristine, Jasper Strength, Opal True Defense, Aquamarine Fishing Speed (not Sea Creature Chance), Onyx Crit Damage, Citrine Foraging Fortune, Peridot Farming Fortune |
| Rarity columns | Common..Mythic for all; Divine column exists for Amber, Topaz, Jade (and others where listed below) |
| Unlock | per slot cost (coins plus gems or a Gemstone Chamber); items made before 2021-09-13 have all slots unlocked |
| Apply / remove | gemstone grinder by Geo; applying is free after unlock |
| Removal cost | Rough 1, Flawed 100, Fine 10,000, Flawless 100,000, Perfect 500,000 coins |
| Chisel % | Rough 30, Flawed 40, Fine 50, Flawless 60, Perfect 100 (Fossil Excavator chisel slots) |
| Slot kinds | single type, category (Combat, Defensive, Mining), Universal, Chisel |
| Gemstone collection rewards | Small Gemstone Sack (I), Medium (III), Large (VI), Gemstone Gauntlet (XI) |

gemstonecosts.json structure: 499 item ids; each value is { "<SLOTTYPE>_<index>": [ "SKYBLOCK_COIN:n", "<TIER>_<TYPE>_GEM:n" or "GEMSTONE_CHAMBER:1" ] }, an empty list = free slot.
Typical costs: early slots 5,000 coins + 1 Fine gem up to 50,000 coins + 20 Fine gems; late slots 100k-250k coins + Flawless gems; Combat slots usually 250,000 coins + 1 Flawless each of Jasper, Sapphire, Ruby, Amethyst.

### 9a. Stat by gem tier and item rarity (NEU gemstones.json, exact)

Columns = rarity of the item, rows = gem tier.

#### RUBY (Health)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 1 | 2 | 3 | 4 | 5 | 7 | - |
| FLAWED | 3 | 4 | 5 | 6 | 8 | 10 | - |
| FINE | 4 | 5 | 6 | 8 | 10 | 14 | - |
| FLAWLESS | 5 | 7 | 10 | 14 | 18 | 22 | - |
| PERFECT | 6 | 9 | 13 | 18 | 24 | 30 | - |

#### AMBER (Mining Speed)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 4 | 8 | 12 | 16 | 20 | 24 | 28 |
| FLAWED | 6 | 10 | 14 | 18 | 24 | 30 | 36 |
| FINE | 10 | 14 | 20 | 28 | 36 | 45 | 54 |
| FLAWLESS | 14 | 20 | 30 | 44 | 58 | 75 | 92 |
| PERFECT | 20 | 28 | 40 | 60 | 80 | 100 | 120 |

#### TOPAZ (Pristine)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.4 | 0.5 |
| FLAWED | 0.8 | 0.8 | 0.8 | 0.8 | 0.8 | 0.8 | 0.9 |
| FINE | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 | 1.2 | 1.3 |
| FLAWLESS | 1.6 | 1.6 | 1.6 | 1.6 | 1.6 | 1.6 | 1.8 |
| PERFECT | 2 | 2 | 2 | 2 | 2 | 2 | 2.2 |

#### JADE (Mining Fortune)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 2 | 4 | 6 | 8 | 10 | 12 | 14 |
| FLAWED | 3 | 5 | 7 | 10 | 14 | 18 | 22 |
| FINE | 5 | 7 | 10 | 15 | 20 | 25 | 30 |
| FLAWLESS | 7 | 10 | 15 | 20 | 27 | 35 | 44 |
| PERFECT | 10 | 14 | 20 | 30 | 40 | 50 | 60 |

#### SAPPHIRE (Intelligence)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 2 | 3 | 4 | 5 | 6 | 7 | - |
| FLAWED | 5 | 5 | 6 | 7 | 8 | 10 | - |
| FINE | 7 | 8 | 9 | 10 | 11 | 12 | - |
| FLAWLESS | 10 | 11 | 12 | 14 | 17 | 20 | - |
| PERFECT | 12 | 14 | 17 | 20 | 24 | 30 | - |

#### AMETHYST (Defense)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 1 | 2 | 3 | 4 | 5 | 7 | - |
| FLAWED | 3 | 4 | 5 | 6 | 8 | 10 | - |
| FINE | 4 | 5 | 6 | 8 | 10 | 14 | - |
| FLAWLESS | 5 | 7 | 10 | 14 | 18 | 22 | - |
| PERFECT | 6 | 9 | 13 | 18 | 24 | 30 | - |

#### JASPER (Strength)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 1 | 1 | 1 | 2 | 3 | 4 | - |
| FLAWED | 2 | 2 | 3 | 4 | 4 | 5 | - |
| FINE | 3 | 3 | 4 | 5 | 6 | 7 | - |
| FLAWLESS | 5 | 6 | 7 | 8 | 10 | 12 | - |
| PERFECT | 6 | 7 | 9 | 11 | 13 | 16 | - |

#### OPAL (True Defense)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 1 | 1 | 1 | 2 | 2 | 3 | - |
| FLAWED | 2 | 2 | 2 | 3 | 3 | 4 | - |
| FINE | 3 | 3 | 3 | 4 | 4 | 5 | - |
| FLAWLESS | 4 | 4 | 5 | 6 | 8 | 9 | - |
| PERFECT | 5 | 6 | 7 | 9 | 11 | 13 | - |

#### AQUAMARINE (Fishing Speed)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 0.5 | 0.5 | 1 | 1 | 1.5 | 2 | - |
| FLAWED | 1 | 1 | 1.5 | 1.5 | 2 | 2.5 | - |
| FINE | 1.5 | 1.5 | 2 | 2 | 2.5 | 3 | - |
| FLAWLESS | 2 | 2 | 2.5 | 3 | 3.5 | 4 | - |
| PERFECT | 2.5 | 2.5 | 3.5 | 4 | 4.5 | 5 | - |

#### CITRINE (Foraging Fortune)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 0.5 | 1 | 1.5 | 2 | 2.5 | 3 | - |
| FLAWED | 1 | 1.5 | 2 | 2.5 | 3 | 4 | - |
| FINE | 1.5 | 2 | 3 | 4 | 5 | 6 | - |
| FLAWLESS | 2 | 3 | 4 | 5 | 6 | 8 | - |
| PERFECT | 3 | 4 | 5 | 6 | 8 | 10 | - |

#### ONYX (Crit Damage)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 1 | 1 | 2 | 2 | 3 | 4 | - |
| FLAWED | 2 | 2 | 3 | 3 | 4 | 6 | - |
| FINE | 3 | 3 | 4 | 5 | 6 | 8 | - |
| FLAWLESS | 4 | 5 | 6 | 7 | 8 | 10 | - |
| PERFECT | 5 | 6 | 7 | 8 | 10 | 12 | - |

#### PERIDOT (Farming Fortune)

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic | Divine |
|---|---|---|---|---|---|---|---|
| ROUGH | 0.5 | 1 | 1.5 | 2 | 2.5 | 3 | - |
| FLAWED | 1 | 1.5 | 2 | 2.5 | 3 | 4 | - |
| FINE | 1.5 | 2 | 3 | 4 | 5 | 6 | - |
| FLAWLESS | 2 | 3 | 4 | 5 | 6 | 8 | - |
| PERFECT | 3 | 4 | 5 | 6 | 8 | 10 | - |

#### Most common unlock costs per slot type (NEU)

| Slot type | cost (count of item slots) |
|---|---|
| AQUAMARINE | `["FINE_AQUAMARINE_GEM:1", "SKYBLOCK_COIN:50000"]` x55; `["FINE_AQUAMARINE_GEM:4", "SKYBLOCK_COIN:100000"]` x10; `["FINE_AQUAMARINE_GEM:40", "SKYBLOCK_COIN:250000"]` x4 |
| CITRINE | `["FINE_CITRINE_GEM:20", "SKYBLOCK_COIN:50000"]` x14; `["FINE_CITRINE_GEM:40", "SKYBLOCK_COIN:100000"]` x8; `["FINE_CITRINE_GEM:40", "SKYBLOCK_COIN:250000"]` x4 |
| DEFENSIVE | `[]` x5; `["SKYBLOCK_COIN:50000", "FINE_RUBY_GEM:10", "FINE_AMETHYST_GEM:10"]` x4; `["SKYBLOCK_COIN:250000", "FLAWLESS_RUBY_GEM:2", "FLAWLESS_AMETHYST_GEM:2"]` x4 |
| PERIDOT | `["FINE_PERIDOT_GEM:20", "SKYBLOCK_COIN:50000"]` x56; `["FINE_PERIDOT_GEM:40", "SKYBLOCK_COIN:100000"]` x45; `["FLAWLESS_PERIDOT_GEM:1", "SKYBLOCK_COIN:250000"]` x24 |
| AMBER | `[]` x22; `["GEMSTONE_CHAMBER:1"]` x9; `["SKYBLOCK_COIN:5000", "FINE_AMBER_GEM:1"]` x8 |
| JADE | `["GEMSTONE_CHAMBER:1"]` x9; `["SKYBLOCK_COIN:20000", "FINE_JADE_GEM:4"]` x8; `["SKYBLOCK_COIN:50000", "FINE_JADE_GEM:20"]` x7 |
| UNIVERSAL | `[]` x4; `["SKYBLOCK_COIN:250000", "FINE_JADE_GEM:20", "FINE_AMBER_GEM:20", "FINE_TOPAZ_GEM:20", "FINE_RUBY_GEM:20", "FINE_AMETHYST_GEM:20", "FINE_SAPPHIRE_GEM:20", "FINE_JASPER_GEM:20"]` x4 |
| JASPER | `["SKYBLOCK_COIN:50000", "FINE_JASPER_GEM:20"]` x33; `["SKYBLOCK_COIN:100000", "FINE_JASPER_GEM:40"]` x5; `["SKYBLOCK_COIN:250000", "FLAWLESS_JASPER_GEM:2"]` x5 |
| SAPPHIRE | `[]` x20; `["SKYBLOCK_COIN:50000", "FINE_SAPPHIRE_GEM:20"]` x12; `["SKYBLOCK_COIN:250000", "FLAWLESS_SAPPHIRE_GEM:4"]` x5 |
| COMBAT | `["SKYBLOCK_COIN:250000", "FLAWLESS_JASPER_GEM:1", "FLAWLESS_SAPPHIRE_GEM:1", "FLAWLESS_RUBY_GEM:1", "FLAWLESS_AMETHYST_GEM:1"]` x225; `[]` x40; `["SKYBLOCK_COIN:50000", "FINE_JASPER_GEM:5", "FINE_SAPPHIRE_GEM:5", "FINE_RUBY_GEM:5", "FINE_AMETHYST_GEM:5"]` x19 |
| OPAL | `["SKYBLOCK_COIN:500000"]` x4; `[]` x1 |
| TOPAZ | `["GEMSTONE_CHAMBER:1"]` x4; `[]` x3 |
| MINING | `["SKYBLOCK_COIN:250000", "FLAWLESS_JADE_GEM:1", "FLAWLESS_AMBER_GEM:1", "FLAWLESS_TOPAZ_GEM:1"]` x2 |
| RUBY | `[]` x22; `["SKYBLOCK_COIN:50000", "FINE_RUBY_GEM:20"]` x5; `["SKYBLOCK_COIN:5000", "FINE_RUBY_GEM:1"]` x4 |
| AMETHYST | `[]` x160; `["SKYBLOCK_COIN:50000", "FINE_AMETHYST_GEM:20"]` x4; `["SKYBLOCK_COIN:100000", "FINE_AMETHYST_GEM:40"]` x1 |
| CHISEL | `[]` x6 |
| ONYX | `[]` x1 |

---

## 10. Events

Source: https://hypixelskyblock.minecraft.wiki/w/Mining_Event (excerpt covered Dwarven Mines; Hollows-specific list not retrieved, those marked (?))

| Event | Where | Effect |
|---|---|---|
| 2x Powder | both | Mithril, Gemstone and Glacite Powder doubled, 20 min |
| Better Together | both | speed and fortune bonus when grouped with up to 5 others in the zone, 20 min |
| Gone with the Wind | Crystal Hollows (?) | wind-driven mining modifier (?exact effect not found) |
| Mithril Gourmand | Dwarven Mines | mine the special ore, contribute 10+ for rewards, 20 min |
| Raffle | Dwarven Mines | collect tickets, bigger deposit = better prize odds, 20 min |
| Goblin Raid | Dwarven Mines | kill goblin hordes within 5 min |
| Fallen Star | Dwarven Mines | more ore drops at a random spot, special mob |
| Powder Ghast | Dwarven Mines (?) | shoot the flying ghast for powder |

---

## Biggest uncertainties

- Zone bounds/sizes, /warp name, length of a lobby "day": not on the mirror.
- Powder and Mining XP per gemstone block, chest loot weights, commission amounts, Goblin variant / Kalhuiki / Key Guardian / Worm stats: not found.
- Bal health/damage (summary said 200/100, surely wrong); Hollows event list incomplete.
