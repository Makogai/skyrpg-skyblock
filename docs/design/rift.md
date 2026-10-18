# The Rift

Hypixel's Rift (Phase 4). Research: `docs/design/research/rift.md`. What's built and what's decided.

## Getting in and out (`rift/Rift.java`)

- The Hub's **Wizard Tower** (`tools/builds/hub_areas/wizard_tower.py`, an island south of the Wilderness, `/warp wizard`):
  7 floors, the Rift portal on the top floor next to the Wizard, Grumblefoot's scroll shop on the ground floor.
- Walking into the portal: SkyBlock Level 12 and a **Dimensional Infusion** (one free every 4 hours, up to 3; or 32
  Grand Experience Bottles at the Wizard). You arrive in the Rift's Wizard Tower. The infusion is used when you first
  leave that tower, so a look inside is free (Hypixel's rule).
- **Rift Time**: 8:00 plus your gear's Rift Time (Wyld armour, the Enigma Cloak...), shown on the health bar and the
  sidebar. It runs down 1 a second everywhere but the tower. At 10 seconds: "THE RIFT IS COLLAPSING"; at 0 you're sent
  to the Hub tower's door. The tower's exit portal leaves early. Falling into the void puts you back at the tower for 10
  seconds. Logging out in the Rift: you wake at the Hub's tower.
- Combat: nothing hurts you in the Rift. A monster's hit takes half its damage in seconds of Rift Time, at most one hit
  every half second (decided: Hypixel's per-mob figures weren't found). Your hits deal your **Rift Damage** (20 plus your
  gear's), whatever you hold (`IncomingDamage.registerOverride`).

## Motes (`Motes.java`, `Orbs.java`)

- A currency kept with you (Hypixel's), shown on the Rift's sidebar instead of the purse.
- Mobs: a Rift zone mob's "coins" are Motes. Shy 100 + 1 Shy Crux; Larva 5 + 50% Larva Silk; Barking Wolf 40 + 1-2
  Muted Bark; Deadgehog 150 + 3 Deadgehog Spine (+50% one more); Lagoon Leech 40 + two 50% rolls of 3 Lil' Pads
  (Hypixel's where found; the Larva, Wolf and Leech Motes are ours).
- Mote orbs: up to 3 pink orbs around you at a time, 6-16 blocks away, each lasting a minute: 25 Motes and 2 seconds.
- The Motes Grubber eats Rift items: decided prices (Larva Silk 8, Muted Bark 15, Deadgehog Spine 20, Lil' Pad 4, Shy
  Crux 25, Bottled Odonata 60, Leech Supreme Fragment 150).

## Enigma Souls (`EnigmaSouls.java`)

- The builds' `enigma:` spots (9 in the Wyld Woods, 4 in the Black Lagoon so far) plus NPC souls: Tel Kar's (hear his
  whole story) and Roy's (4,000 Motes + 8 Deadgehog Spines). Violet souls; right-click to take one.
- Enigma: the Enigma Cloak for 100 Motes; with it every soul is +20 Rift Time. Every 4 souls a cloak upgrade, +2 Speed
  and +4% Mana Regen in the Rift, for 250 Motes times its number (decided).

## Areas so far (`tools/builds/hub_areas/rift_*.py` on `rift_lib.py`)

- The dimension `skyrpg_skyblock:rift` (void, violet sky, lavender grass; `skyrpg_hub:public_rift`). Hypixel's coordinates
  are kept; `rift_lib.py` reserves a box for every area.
- **Wyld Woods** with the **Rift Wizard Tower**: 13 giant trees with walkways and treehouses, the Broken Cage, Enigma's
  Crib, the Pumpgrotto, the Shifted Tavern, The Bastion. Mobs: Shy, Larva, Barking Wolf.
- **Black Lagoon**: channels and islets, giant mushrooms, the Lagoon Hut, the Lagoon Cave, the Leeches Lair under it.
  Mobs: Deadgehog, Lagoon Leech.
- Shops (`RiftNpcs.java`, Motes, NEU prices): the four Argofay Traffickers (Wyld Boots 50, Leggings 1,000, Helmet 1,000,
  Chestplate 2,000), Threebrothers (Wyld Sword 333 + 3 Shy Crux), the Bugshopper (Larva Hook, 6 Shy Crux), Shifted's
  elixirs, Kay's Lil' Pads (10). Everyone else talks.
- Screens: the RIFT theme (crying obsidian with glitches, a torn magenta seam, amethyst buttons) on the card menus.

## Part 2 (`RiftWorld.java`, `LeechSupreme.java`, `Timecharms.java`)

- **Gates** (Hypixel's, checked when you walk into a zone; you're put back where you stood): the Black Lagoon needs
  4:00 of Rift Time left; West Village 3:00 and the Supreme Timecharm; the Dreadfarm the Supreme Timecharm; the Village
  Plaza 8:00, Barrier Street 10:00 and Deja Vu Alley 5:00. Decided: the Plaza side waits on the Supreme Timecharm until
  the Mirrorverse and Chicken N Egg charms exist (Hypixel needs those two).
- **Timecharms**: the eight NEU trophies are items now (`neu_extra_items.py`), with NEU's recipes (the Supreme: 4 Bottled
  Odonata, 32 Lil' Pads, 24 Deadgehog Spines, 4 Leech Supreme Fragments). **Elise** in the Rift's Wizard Tower secures
  them: +100 SkyBlock XP each; the first gives the Rift Necklace.
- **The Leech Supreme** in the Leeches Lair: 800 health, a boss bar, four phases (a fifth of its health each): Slime
  Pound (a shockwave, 8 s), then Wicked Bombs (three marked circles, 10 s), the Leech Swarm and the Mortiferous Lazer
  (sneak, 2 s a hit). Drops 8 Leech Supreme Fragments and 3,000 Motes to everyone who hit it; back 40 s later. Decided:
  the attacks' timings and costs.
- **Odonata**: dragonflies (bees) over the Pumpgrotto lake and Enigma's Crib pool. Hold an Empty Odonata Bottle (the
  Bugshopper: 400 Motes + 2 Shy Crux) and look at one for 3 seconds to bottle it.
- **Lil' Pads**: right-click water for a lily pad that lasts 10 seconds. **Slime launchers** in the Wyld Woods shoot you
  up to their platforms.
- New areas (helper-built): **West Village** (Shadows), the **Dreadfarm** (Volts, Not Chickens, the Great Beanstalk),
  the **Village Plaza** with Barrier Street and Deja Vu Alley (Scribes). Cryptosis sells an Enigma Soul (15,000 Motes +
  10 Scribe Crux), Seymour the Exceedingly Comfy Sneakers (49,999 Motes + Gunther's Sneakers).

## Part 3 (`Porhtal.java`, `Montezuma.java`, `RiftFlags.java`; the areas helper-built)

- Layout decided for reachability (every area meets another along an edge): the **Living Cave** north of the Plaza
  (Deja Vu Alley's islets lead on), the **Colosseum** east of it, the **Photon Pathway** east of the Plaza, **Stillgore
  Chateau** east of the Pathway, the **Mountaintop** south of the Plaza and the Pathway (around Hypixel's peak).
- Gates (Hypixel's): the Living Cave 5:00 + SkyBlock Citizen Timecharm; the Colosseum 15:00 + Living Timecharm (Rift Time
  runs at half speed inside); the Chateau: talk to Deer at the Pathway's end; the Mountaintop 9:00 + Vampiric Timecharm
  (the Time Chamber drains double); Barrier Street 10:00 + Globulate Timecharm.
- **Stillgore Chateau**: monsters hit **Hearts** (10 plus Rift Health), an eighth of their damage in half hearts
  (decided); out of Hearts you're dragged to the **Oubliette**. Healing Melons heal 7 / 9 / 12 Hearts there.
- **Porhtal's eyes**: The Intruder (the Rift Wizard Tower), The Gill-Man, The Baba Yaga, The Bankster, Gooey, The Prince,
  The 7th Sin. Hit an eye for 300 Rift Damage to calm it: +10 to +70 Rift Time for good and a stop on Porhtal's fast
  travel (5 seconds of Rift Time a trip).
- **Montezuma**: nine soul pieces around the Rift; bring them to Sad Jacquelle. Decided until a Montezuma pet exists:
  +15 Rift Time a piece, and from 6 pieces +2 Mana Regen a piece in the Rift.
- Mobs: Autonull, Frozille (Living Cave), Autoboots (Colosseum), Thrall, Fledgling, Splatter (Chateau; Hemovibe and
  Vampiric Melons), Puff (Mountaintop). The Fairylosopher sells an Enigma Soul (10,000 Motes + 32 Splatter Crux).

## Part 4 (`RiftQuests.java`, `Bacte.java`, `VampireSlayer.java`): the ways to the later Timecharms

- **Living Metal**: lapis ore in the Living Cave breaks for Living Metal with the Self-Recursive Pickaxe (1 a block, 2 in
  Living Stillness, times a multiplier that climbs 1x to 5x every 10 blocks; it resets when you leave the Rift). The
  Living Timecharm is NEU's recipe (8 x 20 Living Metal around 20 Frosty Crux).
- **Barry**: talk his seven protesters down, he gives you his Montgray Pen; the pen and 16 Scribe Crux buy the SkyBlock
  Citizen Timecharm (Hypixel's trade).
- **Bacte** in the Colosseum: 1,000 health, five phases (its name's letters on the bar), tentacles each phase (1, 2, 3,
  4, 6; 60 health, 20 from phase 4) that must die before it can be hurt, spikes from phase 2, a slice from phase 3, a
  bouncy floor in phase 5. 8 Bacte Fragments and 6,000 Motes; back after a minute. Decided: hitting tentacles stands in
  for punching Globowls. The Globulate Timecharm is NEU's recipe (8 Bacte Fragments around 20 Shy Crux).
- **The Vampire Slayer** (Maddox in the Chateau's great hall): tiers I-V for 2,000 / 4,000 / 5,000 / 7,000 / 10,000
  Motes (Hypixel's), II-V each after the one before, IV Combat 15, V Combat 25. The quest fills from the Chateau's
  vampires (Thrall 30, Fledgling 20, Splatter 30; decided 300 / 600 / 1,000 / 1,500 / 2,400). The Riftstalker
  Bloodfiend (625 / 1,100 / 1,800 / 2,400 / 3,000 health): Coagulation takes 0.5 / 1 / 1 / 1 / 1.5 Hearts a second,
  Twinclaws (II+) 3 / 5 / 6 / 8 Hearts in front of it every 7 s, Mania (II+ at 60%, III+ also 75% and 40%) 8 s
  untouchable. Below 20% only a **Steak Stake** finishes it (Maddox sells them, 800 Motes, decided). Rewards: slayer XP
  10 / 25 / 60 / 120 / 150 (levels at 20, 75, 240, 840, 2,400: +20 / 30 / 45 / 60 / 80 Rift Time, +15 / 25 / 35 / 50 / 75
  SkyBlock XP), Hypixel's Coven Seals and rare drops, and Motes (decided: the fee plus a quarter). Its level unlocks the
  recipes that need it (the Bloodbadge at 3, for the Vampiric Timecharm) through `SlayerLevels.installKey("vampire")`.

## Part 5 (`Dreadfarm.java`, `Mirrorverse.java`, `WestVillage.java`; `rift_mirrorverse.py`)

- **The Dreadfarm's crops** (Hypixel's): Shania hands out the **Wand of Farming** (free) and, for 2 bunches each of
  Caducous Stem, Wilted Berberis and Agaricus Cap (20 a bunch), a **Proto-Chicken** (reclaimable if lost); her task ends
  when you wear Chicken Leggs. With the wand held: **Agaricus Cap** (look at a brown mushroom 3 s until it turns red,
  then click it; it turns back after 30 s), **Caducous Stem** (click a rose bush's top, then its bottom within 5 s; the
  bush rests 10 s for you), **Wilted Berberis** (8 of the farm's dead bushes sparkle purple; take one and the sparkle
  moves to another nearby). Decided: 1 crop a harvest (Hypixel's yield wasn't published).
- **The Berberis Blowgun**'s Swhooomp (5 mana) shoots an arrow that hurts only chickens and Puffs. A punched Not
  Chicken flies up and floats down (Slow Falling); shot mid-air it dies for a **Metaphoric Egg** and 50 Motes. The
  arrow also pops balloons (wool clusters near a balloon soul; popped on your screen only) and presses buttons.
- **The Rift's collections** (Wilted Berberis, Caducous Stem, Agaricus Cap, Living Metal Heart, Hemovibe, Half-Eaten
  Carrot, Timite) are imported now; they count only the SkyBlock items, and the Dreadfarm's harvests count. The Blowgun
  recipe needs Wilted Berberis II, as on Hypixel. NEU's Mirrored Leash recipe asks for a plain Slime Ball; decided: the
  Mirrored Slime Ball (`FIXES` in import_recipes.py), since nothing else can be had in the Rift.
- **Enigma Souls behind puzzles**: the Hot Dog Contest's soul comes from winning it; Buttons needs Shania's harvest and
  every button on the Dreadfarm's wall pressed; the four balloon souls need every balloon there popped (Farm and Plaza
  also need Shania's harvest, Hypixel's "requires Harvest for Shania").
- **The Mirrorverse** (Supreme Timecharm, 3:00 left; time stands still and nothing hurts inside): eight rooms in a
  pocket far west of the Rift, each split by a glass mirror (your half north, the reflection south). Rooms: levers
  (match the mirror's), the lava path (your floor is blank, the mirror shows the iron path; a wrong step sends you
  back), crafting (baby zombies, cave spiders and slimes are invisible on your side and visible in the mirror; their
  mirrored drops craft the remmaH yniT, which opens the door; the table is invisible on your side too), platforms (the
  mirror shows only the safe ones; the others drop you into the lava and come back after 3 s), switching (click the
  glass to step through it; walls and lava block one side; switching mid-jump over the last pit reaches the **Big
  Brain Talisman**), the dance (12 calls of Move/Sneak/Stand/Jump/Don't jump/Punch, one every 2.8 s; 7 open the door,
  12 earn the **Tiny Dancer**), the invisible climb (barriers on your side, planks in the mirror; three checkpoints with
  levers at the bottom; the **Emmett Pointer**'s lazer marks the invisible steps; climbing without the levers earns the
  **Miniaturized Tubulator**), and the finale: Dr. Emmett pays **60,000 Motes** once, and ttemme .rD sells the
  **mrahcemiT esrevrorriM** (10,000 Motes + 10 Shadow Crux), etalptsehC norI (Wyld Chestplate + 12 Shadow Crux) and the
  Reflection bundle (5,000 Motes + 16 Shadow Crux). Decided: the dance's exact calls and timing, the hard-mode lava
  room (Test Bucket Please Ignore) is left out, and the reflection prices for the chestplate and bundle (Hypixel's weren't
  found). A Dr. Emmett in every room fast-travels to rooms you've reached.
- **The Plaza's gate** is Hypixel's now: the mrahcemiT esrevrorriM and the Chicken N Egg Timecharm (and 8:00 left).
- **Gunther's race**: ten checkpoints round West Village and back to the start line; under a minute earns the Dark
  Pebble, under 35 s the Gunthesizer Lichen (Gunther's Sneakers: Wyld Boots, the Lichen and 8 Shadow Crux).
- **The hot dog contest** (the Chef): for 60 s the stall flings hot dogs over its counter (one every half second,
  only for you); carry them to Joey McPizza; 50 earns the Enigma Soul.
- Fixed: the Rift's flags and secured Timecharms were dropped when a player logged in after a restart (their
  attachment types were made too late); `RiftFlags.init()` and `Timecharms.init()` now load them at startup.

## Still to build

Reed's boat, the Larva Hook, the Rift Transfer, the other area quests (Cowboy Nick, Detective Amog, Kloon, Okron,
Kat's Infested House), growing the Great Beanstalk (the stalk is built full-grown), Shen's Celestial Timecharm, Shen's
auctions, the Sun Gecko, Ubik's games, the Wizardman Trials, the Rift Gallery itself.
