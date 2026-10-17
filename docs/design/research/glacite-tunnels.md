# Glacite Tunnels, Dwarven Base Camp and Glacite Mineshafts research

Source note: the official wiki is gone; numbers come from the community mirror https://hypixelskyblock.minecraft.wiki/w/<Page> (read via a lossy summarizer, so "good, not certified")
and from the NEU repo (`tools/skyblock/cache/neu-repo.zip`, MIT; constants/glacite_tunnel_waypoints.json, items/*.json). Where the two disagree both are shown.
Items marked (?) are uncertain. Coordinates are x,y,z. Format follows docs/design/research/dwarven-mines.md (mining speed formula and Breaking Power rules live there).

---

## 1. Layout and access

Sources: https://hypixelskyblock.minecraft.wiki/w/Glacite_Tunnels , /w/Dwarven_Base_Camp , NEU constants

| Rule | Value |
|---|---|
| Parent zone | Dwarven Mines (NEU islands.json only lists the `mineshaft` island as "Glacite Mineshafts") |
| Entry requirement | Heart of the Mountain tier 7 minimum (HotM 7 = 150,000 HotM XP for the tier, 347,000 cumulative) |
| Secret Railroad Pass | Talk to Dulin (Hanging Court, Dwarven Mines); forge at The Forge: 1 Flawless Ruby Gemstone + 2 Refined Mithril + 8 Corleonite, 30 s (?, one page only) |
| Using the pass | Hand it to the Ticket Master / Station Master at the minecart station; this unlocks the Base Camp. Afterwards a Base Camp travel scroll / Base Camp Portal (NEU items BASE_CAMP_TRAVEL_SCROLL, BASE_CAMP_PORTAL) go there |
| Mining skill level | no Mining skill requirement found (?) |
| Wiki "recommended" | Mining Speed 3,750+, Armor of Divan, Silverfish pet, Titanium Drill DR-X555 or better; HotM 8 preferred. Glacite/Tungsten/Umber need Breaking Power 9, so a Divan-tier drill is effectively required |
| Tunnels reference coord | 9, 121, 247 (zone entry point on the wiki) |

### Areas and landmarks

| Place | Coords | Notes |
|---|---|---|
| Dwarven Base Camp | Ticket Master at 23.5, 128, 207.5 | safe hub: Cold resets to 0 on entry; campfire removes Cold at 20/s (not instantly) |
| Fossil Research Center | inside Base Camp | Dr. Stone, Researcher Beryl, Researcher Jade, Fossil Excavator |
| Glacite Tunnels | roughly x -100..+110, y 120..160, z 250..460 (derived from the gemstone waypoints below) | cave network running south (+z) from the camp |
| Grandpa Wolf's Cave | Grandpa Wolf NPC at -55.5, 125, 331.5 | Frozen Corpse milestones |
| Fossil Muncher | -26.5, 125, 299.5 | daily fossil trade |
| Great Glacite Lake | Fairy Soul DM-14 at -19, 121, 397 | water zone, +3 Cold vulnerability |
| Fairy Souls | DM-14 (-19,121,397) behind a cracked wall under the lake; DM-15 (111,160,412) behind Glacite blocks | |

Size/depth: the walkable network spans about 200 x 210 blocks horizontally and y 120-160 (from the waypoints); the wiki gives no official size (?).

### Base Camp NPCs (https://hypixelskyblock.minecraft.wiki/w/Dwarven_Base_Camp)

| NPC | Role |
|---|---|
| Cold Enjoyer | explains Cold and Cold Resistance |
| Dr. Stone | takes fossils for HotM XP, SkyBlock XP, Mining XP, Glacite Powder, forge recipes |
| Dulin | Secret Railroad Pass, talks about the Tunnels |
| Emissary Lissandra | Tunnels commission access |
| Researcher Beryl | the only shop: perks/upgrades for Fossil Essence (Suspicious Scrap costs 100 Fossil Essence) |
| Researcher Jade | Lotus Atoll info (flavour) |
| Scout Plinius | Tunnels and Cold tips |
| Scout Scardius | tips on damaging Glacite Maniacs (Breaking Power) |
| Sor'Hen | introduces Suspicious Scrap |
| Ticket Master | travel hub within the Dwarven Mines |
| Fossil Muncher (in Tunnels) | daily: fossils for 25,000 Glacite Powder + 100 Fossil Essence (?) |
| Grandpa Wolf (in Tunnels) | Frozen Corpse milestones |

### Gemstone mine waypoints (NEU constants/glacite_tunnel_waypoints.json, verbatim)

| Gemstone | Waypoints |
|---|---|
| Onyx | -2,142,437; -68,133,407; 76,125,411 |
| Aquamarine | 51,120,302; -17,137,394; 11,140,366; 23,141,386; 9,137,412 |
| Peridot | -76,123,282; 91,125,397; -62,150,300; -74,125,458; 68,147,285; 95,157,287 |
| Citrine | -95,148,259; -58,147,421; 37,122,386; -47,130,411; 74,154,388; 94,154,327 |

---

## 2. Cold

Sources: https://hypixelskyblock.minecraft.wiki/w/Cold , /w/Cold_Resistance

| Rule | Value |
|---|---|
| Unit | Cold shown as negative (0 to -100) |
| Buildup | 1 Cold every `x = 5 * (1 + ColdResistance) / (1 + ColdVulnerability)` seconds as written by the wiki; each 1 Cold Resistance adds 0.01 to the numerator, so `5 * (1 + 0.01*CR) / (1 + Vuln)` s (?). Base: 1 Cold per 5 s |
| Vulnerability | water zones (Icy Biome, Great Glacite Lake) +3.00; Glacite Mineshafts +0.25 every 150 s spent inside; each enemy attack in a mineshaft is -1 Cold flat (?) |
| Recovery outside cold zones | +1 per 1 s, ignores Cold Resistance |
| Mining speed penalty | `(Cold - ColdResistance) / 2` percent of Mining Speed lost (-0.5% per point); none if CR >= Cold |
| Death | at -100: "You froze to death", sent back to Dwarven Base Camp. Phoenix Pet Rekindle prevents it and teleports to Dwarven Mines spawn |
| Resets to 0 | entering Dwarven Base Camp, changing worlds, fainting in Critter Safari |
| Campfire at Base Camp | removes Cold at 20 per second |

Thresholds (messages, wiki):

| Cold | Message |
|---|---|
| -25 | "BRRR! It's getting really cold in here!" |
| -50 | "BRRR! It's so cold that you can barely feel your fingers." |
| -75 | "BRRR! Your movement slows to a crawl..." |
| -90 | "BRRR! You're freezing!" plus FROSTBITE title |

The wiki also says "-25% speed for every 25 Cold", which does not match -0.5% per point (25 x 0.5 = 12.5%); one is wrong (?). Movement slowdown at -25/-50/-75/-90 is progressive and separate.
Extras: Glacial-reforged tools give +0.5 up to +2 Mining Fortune per -1 Cold; the Fortunate Freezing event gives +2 Mining Fortune per -1 Cold.

### Cold Resistance sources

| Source | Value |
|---|---|
| Dwarven Handwarmers (gloves) | 5 |
| Pendant of Divan (necklace) | 10 |
| Safari Belt (Icy Biome milestones) | 0.5-10 |
| Ice Cold enchant (per armor piece) | 1-5 (levels I-V) |
| Cold Resistance potion I/II/III/IV | 2.5 / 5 / 7.5 / 10 (NEU POTION_COLD_RESISTANCE;1-4) |
| Pets at lvl 100 | Baby Yeti, Glacite Golem, Mammoth 0.1-10; Penguin 0.15-15 (+ up to 10 from nearby players) |
| HotM Warm Heart (powder perk) | 0.4-20 |
| HotM Mineshaft Mayhem | 10 (mineshafts only, random buff) |
| Accessories | Fried Frozen Chicken 1; Organ Donor Talisman / Ring / Artifact 0.5 / 1 / 1.5 |
| Chimera ultimate (sword) | copies 20-100% of pet stats |
| Cold Resistance attribute shard (NEU) | values not read (?) |

---

## 3. Blocks to mine

Sources: https://hypixelskyblock.minecraft.wiki/w/Glacite , /w/Tungsten , /w/Umber , /w/Aquamarine (gemstone table)

| Resource | Block types (drop multiplier) | Strength | Breaking Power | NPC sell |
|---|---|---|---|---|
| Glacite | Packed Ice (1 per block) | 6,000 (highest of any mineable block) | 9 | 12 coins, not auctionable |
| Tungsten | Cobblestone 1x, Cobblestone Slab 0.5x, Cobblestone Stairs 0.5x, Clay 3x | 5,600 | 9 | not found |
| Umber | Hardened Clay 1x, Brown Hardened Clay 2x, Red Sandstone 3x | 5,600 | 9 | 10 coins |
| Mithril, Titanium, Hard Stone | also present in the Tunnels, standard Dwarven Mines values (see dwarven-mines.md) | | | |
| Aquamarine / Onyx / Citrine / Peridot | stained glass: Aquamarine blue, Onyx black, Citrine brown, Peridot green | not read (?) | 9 each | |

Glacite mining time (wiki): 3 ticks at 2,976 Mining Speed; instamine point cited as 360,001 Mining Speed (consistent with MS > 60 x strength).
Gemstone drops: 3-5 Rough gems per block by default; the Pristine stat upgrades drops to Flawed. Gemstone Crystals appear in mineshafts (section 6).
Powder per block: not found. Core of the Mountain level 8 gives +3 powder per Glacite mined (?).
All three metals are COMMON DWARVEN METAL collection items (NEU). Forms: ENCHANTED_GLACITE/TUNGSTEN/UMBER, REFINED_TUNGSTEN/UMBER, GLACITE_AMALGAMATION. Tungsten/Umber Key forge: 192 Enchanted Tungsten/Umber + 1 Bejeweled Handle, 30 s, requires Tungsten/Umber collection IV and HotM 7 (NEU).
Crafted gear: Umber Plate, Tungsten Plate, Umberella, Tungsten Keychain, Glacite armor (also walker drops).

---

## 4. Glacite Powder

Sources: https://hypixelskyblock.minecraft.wiki/w/Glacite_Powder , /w/Heart_of_the_Mountain

Earned from: mining Glacite, Tunnels commissions, Fossil Excavator, Frozen Corpses, killing Maniacs (50 each) and Littlefoot, Fossil Muncher daily, Daily Powder / Daily Grind perks, one-time milestones. Cap 2 billion. SkyBlock XP: +1 per 2,500 powder up to 600,000 powder, then diminishing to 20 million (?).

HotM tiers 8-10 (Core of the Mountain levels) need Glacite Powder; wiki HotM XP table:

| Tier | HotM XP | Cumulative | "Tokens" | Forge slots | SB XP |
|---|---|---|---|---|---|
| 7 | 150,000 | 347,000 | 7 | +3 | 130 |
| 8 | 210,000 | 557,000 | +180 (?) | +2 | 16 |
| 9 | 290,000 | 847,000 | +210 (?) | +2 | 18 |
| 10 | 400,000 | 1,247,000 | +240 (?) | +2 | 20 |

The "tokens" column for 8-10 looks wrong (?). Powder cost per tier not found.

Powder perks (cost to reach next level = `floor((nextLevel+1)^p)` powder):

| Perk | Max lvl | p | Effect |
|---|---|---|---|
| Scrap Collector | 51 | 3.05 | +0.5% Suspicious Scrap chance in mineshafts (per level? max not clear (?)) |
| Strong Arm | 101 | 2.3 | mining speed on dwarven metals |
| Gemstone Spread | 101 | 2.6 | gemstone spread in mineshafts |
| Cold Resistance (Warm Heart) | 51 | 3.1 | 0.4-20.4 CR |
| Mineshaft Opportunist | 21 | 4 | +0.75-15.75% mineshaft chance |
| Mineshaft Mayhem | 1 | - | random buff on entering a mineshaft (10 CR, 5% scrap, ...) |
| Dwarven Metal Fortune | 21 | ? | 5-105 Dwarven Metal Fortune |
| Mineshaft Fortune | 51 | 3.05 | 4-204 Mining Fortune in mineshafts |
| Mineshaft Speed | 101 | 2.3 | 4-404 Mining Speed in mineshafts |
| Crystalline | 51 | 3.3 | +0.5-25.5% crystal spawn chance |
| Gifts from the Departed | 101 | 2.45 | +0.2-20% corpse loot |
| Dead Man's Chest | 11 | (n+7)^5 | 0.1-1.1 Pristine |
| Eager Adventurer | 51 | 3.2 | +0.5-25.5% extra corpse spawn |
| Vanguard Seeker | 51 | 3.1 | +1-51% Vanguard corpse chance |
| Daily Powder / Daily Grind | - | - | +500 powder per level, first Glacite mined / first commission each day |
| Sky Mall | - | - | 15% powder-related chance per SkyBlock day (?) |
| Powder Buff | - | - | +1-51% all powder |
| No Stone Unturned | - | - | +0.5-25% scrap chance |

HotM tier each perk sits in is not captured (?).

---

## 5. Mobs

Sources: https://hypixelskyblock.minecraft.wiki/w/Glacite_Mutt (and Mage / Bowman / Caver / Glacite_Maniac), NEU items/*_MONSTER.json
All are Glacial (immune to normal damage, hurt only by Breaking Power / Huntaxes, about 150x per BP point (?)), defense 1,000, and Maniacs do not respawn after death.

Wiki value first, NEU in brackets (NEU is older/inconsistent; prefer the wiki):

| Mob | Type | Level | HP | Damage | Combat XP | Coins | Behaviour |
|---|---|---|---|---|---|---|---|
| Glacite Mutt | wolf, Animal | 173 [180] | 750,000 [750,000] | 2,500 [6,000] | 259 [60] | 57 [95] | melee rush |
| Glacite Bowman | humanoid | 174 [165] | 750,000 [750,000] | 2,550 [6,250] | 261 [60] | 58 [95] | approaches and shoots arrows |
| Glacite Caver | humanoid | 175 [200] | 800,000 [800,000] | 2,500 [6,500] | 262 [85] | 58 [125] | melee at short range |
| Glacite Mage | humanoid | 177 [165] | 825,000 [625,000] | 2,550 [6,250] | 265 [70] | 59 [100] | ranged magic, strafes |

Orbs: Mutt 190, Bowman 191, Caver 192, Mage 194. Shared drop table:

| Drop | Chance |
|---|---|
| Glacite Powder x50 | 100% |
| Enchanted Glacite x1 (NEU: plain Glacite x1) | 100% |
| Fine Citrine / Aquamarine / Onyx / Peridot Gemstone | 5% each |
| Glacite Jewel | 4% |
| Glacite Chunk (legendary; NEU GLACITE_SHARD) | 0.1% |

Spawn: only inside Glacite Mineshafts. Littlefoot: no stats found (?); "Littlefoot's Den" is a mineshaft type.

Glacite Walker (https://hypixelskyblock.minecraft.wiki/w/Glacite_Walker): lives at the Great Ice Wall in the Dwarven Mines (not the Tunnels). Wiki Lv 24 [NEU 45], HP 888, defense 1,000, damage 300, 8 coins, Combat XP 36 [40], 26 orbs. 16 spawn every 11 s, max one per spawn spot. Drops: Glacite Jewel 0.5%, each Glacite armor piece 0.5%, Walker shard (Hunting); NEU adds Ice Essence shard (Pocket Black Hole / Salts).

---

## 6. Glacite Mineshafts

Sources: https://hypixelskyblock.minecraft.wiki/w/Glacite_Mineshaft , /w/Frozen_Corpse , /w/Suspicious_Scrap

### Entry

| Rule | Value |
|---|---|
| Trigger | mining blocks in the Glacite Tunnels |
| Base chance | 0.05% per block (1 in 2,000) |
| Pity | counter starts at 2,000; each block subtracts Mithril 2; Umber/Tungsten/Glacite 4; gemstones and Titanium 8; at 0 a mineshaft is guaranteed (added in 0.20.1) |
| Result | "Mineshaft! A mineshaft portal spawned nearby!" with screen title and purple/white particles; the portal lasts 30 s |
| Boost | Mineshaft Opportunist +0.75-15.75% |
| Instance | separate instanced world (NEU island key `mineshaft`) |

### Types (17)

Titanium, Tungsten, Umber, Jade, Amber, Topaz, Sapphire, Amethyst, Jasper, Ruby, Opal, Onyx, Peridot, Citrine, Aquamarine, Fairy, Littlefoot's Den (?, list as given).
Each type 1/17 = 5.88% baseline; mining a matching block raises that type to 33.33% (?, stacking unclear). Gemstone-type shafts: 25% base chance of crystal variants, +0.5% per Crystalline level.
Contents: ore veins of the type, Maniacs (Mutt, Bowman, Caver, Mage), Frozen Corpses, Suspicious Scrap, crystals. Cold keeps rising (+0.25 vulnerability per 150 s). The Fairy shaft holds Vanguard corpses.

### Frozen Corpses

| Type | Key | Spawn share | Rolls | Loot highlights | RNG meter XP |
|---|---|---|---|---|---|
| Lapis | none | 50% | 3-6 (avg 4.5) | Flawed/Fine gems, Goblin Eggs, Enchanted metals, 1 scrap at 2.99% | 500 |
| Umber | Umber Key | 25% | 4-7 (avg 5.5) | Fine/Flawless gems, scrap, Ice Cold books, plates | 2,500 |
| Tungsten | Tungsten Key | 25% | 4-7 (avg 5.5) | as Umber; 2 scraps 10.7%, 4 scraps 5.35% | 2,500 |
| Vanguard | Skeleton Key | Fairy shaft only (100% there) | 5-8 (avg 6.5) | Flawless gems, Refined metals, Glacite Amalgamations, Skeleton Key, Shattered Locket; 8 scraps 4.55%, 16 scraps 2.28% | 25,000 |

1-3 corpses per shaft, up to 4 with perks.
Lapis loot per roll (NEU SKYBLOCK_CORPSE_LAPIS): Flawed gem of each of the 4 types 5.97% each (Fine 1.49%, Fine x2 0.74%); Enchanted Umber / Tungsten / Glacite 2.98% each (Refined / Amalgamation 0.14%); Glacite Jewel 2.98%; Suspicious Scrap 2.98%; Goblin Egg 4.77%; Green / Yellow / Red Goblin Egg 4.17% each; Umber Key / Tungsten Key 0.14%; Bejeweled Handle 0.59%.
RNG meter: looting corpses adds meter XP toward guaranteed rare items such as Frostbitten Dye. Corpse milestones via Grandpa Wolf.

### Scrap, duration and leaving

Suspicious Scrap while mining in shafts: 0.5% per ore block (1 in 200), halved after each scrap found (200 x 2^n); buffs Paleontologist I-V 2.5-12.5%, No Stone Unturned 0.5-25%, Mineshaft Mayhem 5%, Scrap Chance attribute 1-10%.
Instance duration: not found (?). Dying to Cold ends the visit. Leaving gives a bonus: 1,000 Glacite Powder base, +500 per scrap found, +250 per corpse looted, +10 per Cold endured (?, probably per Cold point).

---

## 7. Fossils and the Fossil Excavator

Sources: https://hypixelskyblock.minecraft.wiki/w/Fossil_Excavator , /w/Fossils , /w/Glacite_Chisel , NEU items

| Rule | Value |
|---|---|
| Inputs | Chisel (kept) + Suspicious Scrap (consumed per dig) at the Fossil Excavator NPC in the Research Center |
| Chisel line | Chisel -> Reinforced Chisel -> Glacite-Plated Chisel (2 gem slots; HotM 9; forge 30 s: Reinforced Chisel + Mithril Plate + 8 Glacite Amalgamation + Bejeweled Handle) -> Perfect Chisel |
| Gem slot effects | Aquamarine: extra chisel charge chance 30-100%; Citrine: highlight treasure; Onyx: extra treasure; Peridot: more Fossil Essence |
| Each dig | 10-14 random items from a weighted table (mini-game grid not described (?)) |
| Fossil chance | 10% overall; six excavation fossils equally likely (about 1.66% each) |
| Other rewards | Fossil Essence 10-40, Glacite Powder 1,000-10,000, Enchanted Umber/Tungsten/Glacite, gemstones, Paleontologist book, Fossil the Fish (rare) |
| Scrap sources | mining in shafts, corpses, 100 Fossil Essence at Beryl; salvage a scrap for 5 Fossil Essence |

Fossils: Clubbed, Footprint, Spine, Tusk, Ugly, Webbed (excavator); Helix and Claw only from the Crystal Nucleus.
Dr. Stone donation (same for every fossil): 25,000 Mining XP, 25,000 Glacite Powder, 5,000 HotM XP, 10 SkyBlock XP; donating 8 fossils adds +50,000 Glacite Powder and +10,000 HotM XP. Salvage: Helix/Claw 25 Fossil Essence, others 50. Sells for 10,000 coins; Legendary; machine fuel 50,000.
Fossil Dust (NEU SKYBLOCK_FOSSIL_DUST, "used for NPC shops"): wiki source not found (?). Beryl's stock beyond Suspicious Scrap not retrieved (?).

---

## 8. Commissions and other mechanics

Source: https://hypixelskyblock.minecraft.wiki/w/Commissions

| Commission | Requirement |
|---|---|
| Aquamarine / Citrine / Glacite / Onyx / Peridot / Tungsten / Umber Collector | collect 1,500 of the resource |
| Mineshaft Explorer | discover 1 mineshaft |
| Corpse Looter | loot 2 Frozen Corpses |
| Scrap Collector | obtain 1 Suspicious Scrap |
| Maniac Slayer | slay 10 Maniacs in the mineshafts |
| Glacite Walker Slayer | kill 50 Glacite Walkers (Dwarven Mines, Great Ice Wall) |

Rewards per Tunnels commission: Mining XP 12.5k-25k, HotM XP 400 (750 at HotM 10), Glacite Powder 1,500 (3,000 at HotM 10). Issued by Emissary Lissandra.
Other: Fossil Muncher daily, Fairy Souls, Glacite Golem pet and skins, NEU attribute shards (Cold Resistance, Cavern Wisdom, Essence of Fossils, Cavernous Tracker, Atomized Glacite).

---

## Biggest uncertainties

1. Exact Cold formula (resistance units, 5 s base) and the "-25% per 25 Cold" contradiction.
2. Mob stats: wiki and NEU disagree on level, damage, XP, coins; prefer the wiki.
3. Powder cost of HotM tiers 8-10, per-block powder amounts, the "tokens" column.
4. Mineshaft instance duration, exit mechanics, exact leaving bonus.
5. Excavator mini-game (grid, charges), full weighted loot table, Fossil Dust use, Beryl's stock.
6. Mineshaft type odds when several block types are mined; whether the 17 types are right.
7. Gemstone block strength in the Tunnels, Mining level requirement, Secret Railroad Pass recipe (single source).
8. Littlefoot stats, Maniac counts per shaft, mineshaft layout, Tunnel size.
