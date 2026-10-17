# The Crimson Isle

Hypixel's nether island (Phase 4). Research: `docs/design/research/crimson-isle.md`. What's built, what's decided, what's left.

## The island

- Dimension `skyrpg_skyblock:crimson_isle`, type `skyrpg_hub:public_crimson` (public: protected like the Hub; red fog, no sky,
  crimson spores, the crimson forest's music). A flat generator fills every column with lava up to y 59: the island sits
  in a lava sea.
- Built by three scripts sharing `tools/builds/hub_areas/crimson_lib.py`: `crimson_west.py` (x -330..-101, the Barbarian
  side), `crimson_center.py` (x -100..99), `crimson_east.py` (x 100..329, the Mage side). Lava rivers 14 wide run along
  x = -100 and x = 100, crossed by four nether-brick bridges (z -80 and z 110, deck y 66).
- West: Dragontail (Barbarian town), the Barbarian Outpost, the Dukedom, the Smoldering Tomb, the Wasteland, the Burning
  Desert, the Forgotten Skull (Kuudra's entrance). Centre: the arrival (south coast), the Crimson Fields, the Ruins of
  Ashfang, the Stronghold, the Blazing Volcano and the Magma Chamber under it. East: Scarleton (Mage town), the Mage
  Outpost, the Cathedral and its Courtyard, the Mystic Marsh, the Dojo, Odger's Hut.
- Getting there: a launch pad on the Spider's Den's east edge (Combat XXII), and the warps `crimson`, `dragontail`,
  `scarleton`, `smoldering`, `kuudra` once visited. The arrival's pad goes back to the Spider's Den.
- The lava sea: a player who falls in is thrown back to the nearest warp after a second and a half (Hypixel's lava burns
  you; on a protected island nothing burns, so this keeps people from swimming forever).
- Mobs (Hypixel's levels and health from NEU; damage, XP and coins decided to match): Blazes, Mutated Blazes, Bezal and
  Wither Skeletons in the Stronghold; Magma Cubes, Pack Magma Cubes and Riders in the Crimson Fields; Smoldering and
  Millennia-Aged Blazes, Flares and Ghasts on the volcano; Kada Knights in the Magma Chamber, the Dukedom and the
  Wasteland; Wither Spectres and Skeletons in the Smoldering Tomb; Flaming Spiders in the Burning Desert; Mushroom Bulls in
  the Mystic Marsh. Drops: Blaze Rods, Magma Cream, Ghast Tears and so on, plus Elle's quest items (Magma Chunks from
  cubes, Blaze Ashes from Blazes, Spectre Dust from Spectres, 25-35%).
- Breakable: red sand (Burning Desert), Sulphur ore (sponge, Wasteland and Desert), mycelium (Mystic Marsh).

## Factions (`crimson/Factions.java`)

- Two factions, the Mages (Queen Nyx, Scarleton) and the Barbarians (Chief Scorn, Dragontail). Reputation is kept per
  faction; switching (at the other leader) keeps both numbers. Ranks Neutral 0, Friendly 1,000, Trusted 3,000, Honored
  7,000, Hero 12,000, with 5 / 10 / 20 / 40 SkyBlock XP on reaching them.
- Elle's questline, shortened from Hypixel's: Elle (Stronghold gate) wants 4 Magma Chunks, 3 Blaze Ashes and 2 Spectre
  Dust; then meet both leaders; then either leader takes you in. Hypixel's longer faction questline (the Batphone, the
  stolen research) is left out: decided, the daily quests carry the faction loop.
- Daily quests (`FactionQuests.java`): five a day, new at midnight UTC: two hunts (10-30 of an Isle mob, 80-90 rep), a
  miniboss (120, Magma Boss 150) and two errands for your faction's people (100 each): Pablo 32 Red Mushrooms, Avorius a Cup
  of Blood, Mollim 32 Mycelium; Pomtair an Enchanted Bone Meal, Sirih 16 Sulphur, Suus 16 Magma Cream. The leader's screen
  shows rank, reputation and the day's quests.
- Every miniboss kill: +20 reputation (Hypixel's number).

## Minibosses (`crimson/Minibosses.java`)

| Miniboss | Where | Level | Health | Essence |
|---|---|---|---|---|
| Bladesoul | the Stronghold's courtyard | 477 | 50M | 25 |
| Mage Outlaw | the Courtyard | 515 | 70M | 25 |
| Barbarian Duke X | the Dukedom | 486 | 70M | 25 |
| Ashfang | the Ruins of Ashfang | 508 | 50M | 50 |
| Magma Boss | the Magma Chamber | 523 | 200M | 50 |

Each returns two minutes after it dies, while a player is within 96 blocks of its spot (a boss bar within 48). Anyone who
dealt at least 0.5% of its health gets the Crimson Essence, Combat XP, coins and the reputation. The Magma Boss also gives
20-35 Magma Cream, a 50% Enchanted Magma Cream, Compact Ooze and a Rekindled Ember Fragment. Moves every 5-8 seconds: a
sweep (Bladesoul, the Duke), a fire burst (the Outlaw, Ashfang), a slam (the Magma Boss). Hypixel's Damage Soaked meter on
the Magma Boss is left out for now.

## Trophy Fishing (`fishing/TrophyFishing.java`)

Lava rods only on the Isle. 18 fish, four tiers, Hypixel's conditions and odds; the volcano erupts every 20 minutes
(decided: Gushers bite for 10 minutes after); the Golden Fish needs 8 minutes of fishing without a 2-minute break, then
5% a catch (decided; Hypixel's "varies"); Steaming-Hot Flounders bite within 3 blocks of a geyser pool. Odger fillets
into Magmafish and gives the ranks' armour, teeth and sacks. Decided: the lava sea creatures' Fishing levels, weights,
Fishing XP and damage (the wiki pages weren't fetched).

## Kuudra (`crimson/Kuudra.java`, `KuudraRun.java`, `KuudraMenu.java`)

Dimension `skyrpg_skyblock:kuudra` (lava up to y 59), 32 slots 1,024 apart; the arena `structure/kuudra/arena.nbt`
(`tools/builds/kuudra/arena.py` on `kuudra_kit.py`: platform, ballista, six islets on bridges, crater; only solid blocks)
is placed once per slot. Decided, since the wiki's Kuudra pages gave no numbers or rules for most of it:
- Phases: supplies (six crates on the islets, carried slowly to the ballista), build (stand by it: 1.5 + 1 per builder %
  a second), Kuudra (fuel cells two at a time; each is a ballista shot: 10% of its health and a 10-second stun beside
  the platform, the only time it can be hurt). Hypixel's stomach and lair phases are folded into tier extras: tentacles
  that block the ballista from Burning, healing from Fiery, an enrage below 25% in Infernal.
- Kuudra's health 100k / 500k / 3M / 15M / 240M (the research's recollection); waves every 35 / 30 / 25 / 20 / 15 s of
  Kuudra Followers, Knockers, Magma Followers, Wandering Blazes (and Berserkers from Hot); a slam every 8 s (4 enraged).
- Down for 10 seconds, then back on the platform; everyone down at once loses. The lava throws you back too.
- Rewards: reputation 10-50 and first-clear SkyBlock XP 20-100 (Hypixel's); free chest 60 / 120 / 250 / 500 / 1,000
  Crimson Essence; paid chest (the tier's key): twice that (+2,000 in Infernal), one Crimson/Aurora/Terror/Hollow/
  Fervor piece of the tier, Kuudra Teeth (Fiery 1-2, Infernal 3-4), a 25% chunk, a 2% core from Burning.
- Keys at the Emissaries: Hypixel's coins, materials and reputation; 2 Corrupted Nether Stars each.
- Not yet: Kuudra's class perks and tokens, the Kuudra pet drop, attribute shards, Croesus buying back chests.

## The Dojo (`crimson/Dojo.java`) and Vulcan

- Master Tao's seven tests in the 31x31 arena (spawn:Dojo), one player at a time, 60 s each (Mastery 120). Hypixel's
  platform is over lava; ours is walled, so decided: Force knocks zombies into the wall ring, Stamina's moving walls are
  sweeping fire lines with a gap, Tenacity's ghast fireballs are marked fire rings. Points: Force 10/20/30, -15 leather;
  Stamina 17/s, -50 hit; Mastery 8/16/32; Discipline +25/-10; Swiftness +-10 per half second; Control 1 a tick within
  10 degrees; Tenacity 17/s, -100 hit. Grades S 1000, A 800, B 600, C 400, D 200 (decided). Belts by the total of bests:
  White (any test), Yellow 1000 ... Black 7000 with Hypixel's Combat XP. First S per test: 5 SkyBlock XP (decided).
  The bow, arrow and swords are tagged and taken back when the test ends.
- Vulcan adds Crimson stars (the items' own Crimson `upgrade_costs`: 10, 15 on Infernal pieces; belts too) on Malik's
  screen; +2% of the piece's stats a star (decided). Not yet: his Crimson Essence perk shop.

## Still to build

- Inferno Demonlord (Blaze Slayer): a slayer line with Immolate, Demonsplit, Hellion Shield attunements, fire pillars.
- Crimson armour's essence stars and attributes; Kuudra's perks, tokens and the pet.
- Vulcan's Crimson Essence perks; attributes.
- Rollim's and Kaus's daily duels, geysers on the Blazing Volcano, the Vanquisher.
