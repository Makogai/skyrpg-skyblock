# Dwarven Mines: build brief

Hypixel's Dwarven Mines, built as four area scripts in one dimension, `skyrpg_skyblock:dwarven_mines` (type
`skyrpg_hub:public_mines`: no sky light, a dark cave sky, ambient light 0.35, y 0..255). Research:
`docs/design/research/dwarven-mines.md` (areas, mobs, NPCs, coordinates). Examples of the kit in use:
`tools/builds/hub_areas/the_end.py`, `deep_caverns.py`, `spiders_den.py`, `gold_mine.py`; the kit is
`tools/builds/hub_areas/kit.py`.

Every area: `Area("mines_<part>", origin, size, version=1, shell=2, dimension="skyrpg_skyblock:dwarven_mines",
island="Dwarven Mines")`, `area.arrival(...)` somewhere walkable in it, `area.save()`, run the script, check
`area.preview()` / the printed counts.

## It's a cave

The Dwarven Mines are a huge cavern world. Each area fills its whole box with rock (stone, andesite, tuff, cobbled
deepslate and some deepslate; no dirt or grass) and carves its spaces out of it: big open caverns with high ceilings,
quarries, tunnels, shafts, ledges, bridges and stairs, buildings inside the caverns. Nothing is open to the sky: the
top of every box is rock (a ceiling 4+ blocks thick), and so are its outer walls. **Light everything**: the dimension
has no sky light, so caverns need lanterns, glowstone and shroomlight in the ceiling, lava falls, sea lanterns in
walls; a player should never stand in darkness outside The Mist and the Goblin Burrows' tunnels (dim, not black).

Players can only break zone blocks (below); everything else is scenery. Every walkway must be at least 3 wide with
2+ blocks of headroom; slopes are stairs or ramps a player can walk (no 2-block steps, no parkour), and there must be
no holes a player can fall into and get stuck. Keep it walkable end to end.

## The four boxes (world coordinates, x/z ranges are [min, max) )

| Area id | x | z | y | Holds |
| --- | --- | --- | --- | --- |
| `mines_village` | -60 .. 130 | -175 .. -75 | 100 .. 235 | Dwarven Village, Dwarven Tavern, The Lift (the arrival, east end) |
| `mines_west` | -180 .. -60 | -175 .. 175 | 100 .. 235 | Upper Mines, Rampart's Quarry, Far Reserve, Goblin Burrows |
| `mines_center` | -60 .. 60 | -75 .. 175 | 95 .. 235 | Gates to the Mines, The Forge, Cliffside Veins, Divan's Gateway, The Mist, Great Ice Wall |
| `mines_east` | 60 .. 220 | -75 .. 175 | 100 .. 235 | Lava Springs, Palace Bridge, Royal Mines, Royal Palace, Aristocrat Passage |

Boxes must not overlap and an area must not put blocks outside its box (the box edge is where the next area starts).

## Doorways between areas

Openings in the shared walls, 5 wide and 5 tall, floor at the given y, centred on the given spot. **Both** areas carve
the opening (each on its own side of the line) and lead a walkable passage from it into their spaces, with the floor
at exactly that y for the 3 blocks next to the line.

| # | Between | Line | Centre | Floor y |
| --- | --- | --- | --- | --- |
| 1 | village / center | z = -75 | x = 0 | 182 |
| 2 | village / west | x = -60 | z = -110 | 176 |
| 3 | village / east | z = -75 | x = 95 | 197 |
| 4 | west / center | x = -60 | z = -45 | 175 |
| 5 | west / center | x = -60 | z = 60 | 140 |
| 6 | west / center | x = -60 | z = 150 | 128 |
| 7 | center / east | x = 60 | z = -40 | 197 |
| 8 | center / east | x = 60 | z = 40 | 140 |
| 9 | center / east | x = 60 | z = 150 | 128 |

## The areas (Hypixel's, see the research file for more)

**mines_village**
- **Dwarven Village** (around x 0, z -125, floor y ~200): the big cavern hub. Stone-brick and spruce dwarven houses with
  lanterns, a central square, a bank, shops. NPCs: Emissary Eliza, Bubu (pickaxes), Bulvar (minions), Banker Broadjaw,
  the Blacksmith, the Station Master, Old Man Garry, Don Expresso, Fragilis, Rhys.
- **Dwarven Tavern** (north-east of the square, ~x 32, z -150): a tavern hall with a bar. Brynmor (shop) behind the bar;
  Gimley, Bomin, Hornum, Sargwyn and Tarwen at the tables.
- **The Lift** (east end, ~x 105, z -100, floor y ~200): where players arrive. A lift shaft with a cage and rails going
  up out of sight; Braum the Emissary nearby; the Lift Operator in the cage. `area.arrival` and `area.warp("Dwarven
  Mines", ...)` here, facing west into the village. A launch pad (3x3 slime blocks with a sign "Deep Caverns") back to the
  Deep Caverns: `area.pad("the Deep Caverns", ..., destination=("skyrpg_skyblock:deep_caverns", (x, y, z), yaw))` - see the
  Deep Caverns task below for the landing spot.
- Doorway 1 leads south down to the Gates to the Mines, doorway 2 west to the Upper Mines, doorway 3 south-east to Lava
  Springs.
- Also this task: **the Deep Caverns side**. In `tools/builds/hub_areas/deep_caverns.py`, add a launch pad to the
  Dwarven Mines on the Obsidian Sanctuary's floor (the bottom layer): `area.pad("the Dwarven Mines", ..., warp="mines",
  skill="mining", level=12)` with a sign "Dwarven Mines" / "Mining XII", and record a landing spot next to it (outside
  the pad box) for the pad back. Bump that area's `version` by one. Don't change anything else there.
- Zones: "Dwarven Village" (no breakable blocks), "The Lift" (none).
- Fairy souls: 3, hidden but reachable without breaking anything (a roof, behind a barrel stack, up a tower).

**mines_west**
- **Upper Mines** (north-west, ~x -130, z -100, floor y ~176): a high, wide cavern with Mithril and Titanium veins in the
  walls and floor outcrops; Treasure Hoarders. Emissary Fraiser. Doorway 2 comes in here.
- **Rampart's Quarry** (south of the Upper Mines, ~x -100, z -40, floor y ~170): an open quarry in terraces with wooden
  scaffolding, Mithril everywhere, Titanium here and there. Emissary Carlton. Doorway 4 leads east to the Gates.
- **Far Reserve** (~x -110, z 60, floor y ~142): Mithril veins and wooden platforms, mine-cart rails. Doorway 5 east to
  Cliffside Veins.
- **Goblin Burrows** (~x -130, z 130, floor y ~142, down to ~128): passages lined with gold blocks and gold ore opening into
  chambers; dim. Goblins, Knifethrowers and Fireslingers. Doorway 6 east to the Great Ice Wall.
- Zones: "Upper Mines", "Rampart's Quarry", "Far Reserve", "Goblin Burrows".
- Fairy souls: 3 (Hypixel has 3 in the Goblin Burrows alone: put at least 2 there).

**mines_center**
- **Gates to the Mines** (~x 0, z -60, floor y 182): a huge carved stone gate with dwarven statues, overlooking the Forge
  basin. Doorway 1 north to the village, 4 west to Rampart's Quarry.
- **The Forge** (~x 0, z -30, basin floor y ~175): a great hall around a lava fall that pours into a basin; anvils,
  furnaces, blast furnaces, chains. The Forger (forge action), Fred, Jotraeline Greatforge. `area.warp("The Forge",
  ...)` in front of the Forger. Doorway 7 east to Lava Springs.
- **Cliffside Veins** (~x 15, z 45, from y ~200 down to ~115): a deep vertical chasm with ledges and stairs down its
  sides, rich in Mithril and Titanium; the Cult of the Fallen Star's cave in the upper part (Brarnas, Dalir, Thondin,
  Silnar). Emissary Ceanna. Doorway 8 east to the Royal Mines, 5 west to the Far Reserve.
- **Divan's Gateway** (~x 0, z 105, y ~135): a great bridge/gate across a cavern, south of Cliffside. Bylma.
- **The Mist** (under Divan's Gateway, y ~100..125): a foggy ice cavern of white stained glass, white wool, packed ice
  and snow, reached by a stair down from the Gateway. Ghosts. Dark: light only by a few soul lanterns.
- **Great Ice Wall** (~x 0, z 155, floor y 128): a cavern against a huge wall of packed ice and blue ice to the south (the
  box edge), with some Pure Emerald (emerald_block) in it. Glacite Walkers. Doorways 6 and 9.
- Zones: "Gates to the Mines" (none), "The Forge" (none), "Cliffside Veins", "Divan's Gateway", "The Mist",
  "Great Ice Wall".
- Fairy souls: 3.

**mines_east**
- **Lava Springs** (~x 95, z -40, floor y ~197): a cavern of lava falls and streams in channels, basalt and magma, with
  Mithril and Titanium. Emissary Lilith. Doorways 3 and 7.
- **Palace Bridge** (from Lava Springs south over a chasm, ~x 120, z -10 .. 60, y ~190): a long stone bridge with
  railings, statues and lanterns, two Castle Guards, ending at the palace. It looks down into the Royal Mines.
- **Royal Mines** (below the bridge, ~x 140, z 35, floor y ~150): a big mine with rails and wooden supports: Mithril,
  Coal, Iron, Gold, Lapis, Redstone, Emerald and Diamond ore, and Pure Ores (coal_block, iron_block, gold_block,
  lapis_block, redstone_block, emerald_block, diamond_block) in a lower chamber. Emissary Wilson, Marigold. Doorway 8.
- **Royal Palace** (~x 150, z 125, floor y ~187): the dwarven king's castle hall: throne room with the King, Queen
  Mismyla, guards, a lava waterfall, banners, the Grand Library next to it (Dalbrek, Tal Ker) and the Puzzler by the
  library door.
- **Aristocrat Passage** (from the Royal Mines/Palace west down to doorway 9, floor y 128): a long elegant corridor.
- Zones: "Lava Springs", "Palace Bridge" (none), "Royal Mines", "Royal Palace" (none), "Aristocrat Passage" (none).
- Fairy souls: 2.

## Breakable blocks (zone `blocks`)

Mining zones list only the blocks they're for; use these blocks **only** as mineable veins inside those zones (never as
decoration anywhere):

| Block | What it is | Zone rule |
| --- | --- | --- |
| gray_wool, cyan_terracotta | Mithril, tier 1 (most common) | `{"becomes": "minecraft:bedrock", "regrow": 8}` |
| prismarine, prismarine_bricks, dark_prismarine | Mithril, tier 2 | same |
| light_blue_wool | Mithril, tier 3 (rare, in the deepest spots) | same |
| polished_diorite | Titanium (rare: a few per vein) | `{"becomes": "minecraft:bedrock", "regrow": 12}` |
| coal_ore, iron_ore, gold_ore, lapis_ore, redstone_ore, emerald_ore, diamond_ore | ores (Royal Mines, Goblin Burrows gold) | `{"becomes": "minecraft:bedrock", "regrow": 4}` |
| coal_block, iron_block, gold_block, lapis_block, redstone_block, emerald_block, diamond_block | Pure Ores (Royal Mines' lower chamber; emerald_block also in the Great Ice Wall) | `{"becomes": "minecraft:bedrock", "regrow": 30}` |

Mithril is everywhere in the mining zones (Upper Mines, Rampart's Quarry, Far Reserve, Cliffside Veins, Lava Springs,
Royal Mines, Divan's Gateway): veins and blobs of the three tiers mixed (about 70% tier 1, 25% tier 2, 5% tier 3) in
walls, floors and pillars players can walk up to; Titanium as single blocks in the veins (~2%). Plenty of it: a player
should always have Mithril within reach. The Goblin Burrows' gold blocks are scenery (not in its blocks), its gold_ore is
mineable.

## Mobs (zone `mobs`; see the_end.py's `mob()` helper; stats are Hypixel's)

| Name | type | level | health | damage | xp | coins | where (box inside the zone, on open floor) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Goblin | minecraft:zombie | 23 | 800 | 300 | 34 | 7 | Goblin Burrows, max 10 |
| Knifethrower | minecraft:zombie | 23 | 800 | 300 | 34 | 7 | Goblin Burrows, max 4 |
| Fireslinger | minecraft:skeleton | 23 | 800 | 300 | 34 | 7 | Goblin Burrows, max 3 |
| Treasure Hoarder | minecraft:husk | 61 | 15000 | 500 | 91 | 20 | Upper Mines, max 4 |
| Ghost | minecraft:creeper | 157 | 1000000 | 1000 | 100 | 52 | The Mist, max 8 |
| Glacite Walker | minecraft:stray | 24 | 888 | 300 | 36 | 8 | Great Ice Wall, max 16, every 11 |

Goblin drops: `[drop("GOBLIN_EGG", 0.005)]` if that item exists in `skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items/`
(check every drop id exists, as the_end.py does, and leave out any that doesn't). Treasure Hoarder: `drop("STARFALL", 1, 1, 2)`.
Glacite Walker: `drop("GLACITE_JEWEL", 0.005)`. All mob spawn boxes must be indoors spots: pass `indoors=True`.

## NPCs

`area.npc(name, x, y, z, yaw, note)` for every NPC named above, standing on a floor block with 2 blocks of air above,
facing the way a player would come. The note says what they are ("Emissary: commissions", "shop", "forge"...). Use
exactly these names: Emissary Eliza, Emissary Braum, Emissary Fraiser, Emissary Carlton, Emissary Ceanna, Emissary
Lilith, Emissary Wilson, King, Queen Mismyla, Bubu, Bulvar, Banker Broadjaw, Blacksmith, Station Master, Old Man Garry,
Don Expresso, Fragilis, Rhys, Brynmor, Gimley, Bomin, Hornum, Sargwyn, Tarwen, Lift Operator, Forger, Fred, Jotraeline
Greatforge, Brarnas, Dalir, Thondin, Silnar, Bylma, Marigold, Dalbrek, Tal Ker, Puzzler, Castle Guard (two: "Castle
Guard" and "Castle Guard 2").

## Rules for the helpers

- Python only: write `tools/builds/hub_areas/mines_<part>.py`, run it with `python`, fix what it reports. Don't run
  gradle, the game, `hotswap.py`, the minecraft MCP tools or staging, and don't touch other areas' files (the village task
  also edits `deep_caverns.py` as described).
- Check the blocks you use exist in 26.x (the kit's `save()` checks ids; `minecraft:chain` is `minecraft:iron_chain`).
- Don't read secret files (`.env`, `tools/.discord_webhook`, `server.properties`, `accounts.json`).
- When done, report: the file, block count, the arrival and warp spots, every doorway you made (centre and floor y),
  every NPC spot, fairy souls, zones with their boxes, and anything you weren't sure about.
