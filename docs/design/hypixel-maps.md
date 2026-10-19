# Hypixel's maps

Some islands run on Hypixel's own maps instead of our builds: world downloads of each island (the "Hypixel SkyBlock
World Downloads" thread on hypixel.net, saved with World Downloader in October 2022, Minecraft 1.16.4).

**The maps are Hypixel's builds. They never go in the repo or ship with the mod:** each world gets them installed by
hand from a local copy (the owners keep theirs in `Downloads/HypixelWorlds`). The repo only has the tools and our data
placed to fit them (NPC positions, zones, pads, warps, fairy souls).

| Island | Our dimension | Map | Status |
| --- | --- | --- | --- |
| The Hub | `skyrpg_hub:hub` | The Hub.zip | in use |
| The Park | `skyrpg_skyblock:the_park` | The Park.zip | in use |
| The End | `skyrpg_skyblock:the_end` | The End.zip | in use |
| The Dungeon Hub | `skyrpg_skyblock:dungeon_hub` | The Dungeon Hub.zip | in use |
| Galatea | `skyrpg_skyblock:galatea` | a WorldTools capture | waiting for a clean capture |
| The Garden's Barn plot | the `garden/barn` structure | a WorldTools capture of a fresh Garden | in use (`hypixel_garden.py`) |
| The Park (2025 rework) | `skyrpg_skyblock:the_park` | a WorldTools capture | waiting for a clean capture |
| The Catacombs' rooms and boss arenas | the dungeon templates | [Dungeons] The Entrance, Floor I-VI | in use (`hypixel_dungeons.py`) |

The 2022 downloads miss islands Hypixel added since (Galatea, the Garden) and islands it rebuilt: the current Park is a
ring of separate floating islands (a snowy spruce island, a jungle island reached by a bridge), not the 2022 single
island. Those come from captures: joining Hypixel with **WorldTools** (a Fabric mod) in a 26.1 client and flying over
the whole island so every chunk loads. Hypixel's NPCs (fake players) aren't saved; their name holograms are.

**Never open a capture in singleplayer.** Hypixel never random-ticks its islands, so their trees are full of leaves
with no log within reach (`persistent=false`, distance 7): Minecraft starts decaying them the moment the world runs, and
whatever stands on leaves (big dripleaf, azalea) drops with them. A minute of singleplayer cost the first Galatea
capture thousands of leaves (its dropped saplings, sticks and dripleaf were still falling into the void), and the game
also half-generates chunks around it and spawns animals. A capture that was opened shows it: chunks saved as
`minecraft:full` (WorldTools writes `full`), chunks stuck at `minecraft:structure_starts` and the like, and item
entities. Look at a copy if you must.

## Installing (`tools/worlds/hypixel_map.py`)

    python tools/worlds/hypixel_map.py install "<map .zip or folder>" skyrpg_hub:hub [--world-dir run/server/world]

With the server stopped. The island's region, entities and poi folders go into `backup-<time>/` beside them (deleted
instead when the island already had a Hypixel map), the map's chunks are copied in (Minecraft upgrades them from 1.16
as they load) and a `hypixel_map.json` marker is left there. On the way, the download's entities are sorted: decor
stays (item frames, paintings, armour stands that hold or wear something); Hypixel's mobs, dropped items and the name
holograms over its NPCs go. The holograms' names and places are kept in `hypixel_npcs.json` beside the marker.

Both kinds of save install the same way: the 2022 downloads (1.16, `region/` at the top, entities inside the chunks,
names as JSON) and WorldTools captures (26.1: `dimensions/minecraft/overworld/`, entities in their own region files,
names as NBT text components, armour stands' items in an `equipment` compound). Item and block displays count as decor
too. Every leaf is made persistent on the way, and chunks the game had only begun generating are left out (ours
generates its own void there). On our side, leaves never decay on an island with Hypixel's map
(`OfficialLeavesMixin`), whatever their state: the 2022 maps' leaves were already all persistent, the captures' weren't.

`render` draws a map from above with NEU's NPCs on it, to find our way around:

    python tools/worlds/hypixel_map.py render "<map>" hub.png --island hub --scale 2

## What the marker changes (`HubMaps`)

On an island with the marker our own builds aren't placed (the hub build, the hub areas, the hub's decor and
podiums), and the Hub takes hub.json's `"official"` settings: Hypixel's spawn on the village square (-2.5 70 -69.5).

End and nether portal blocks on the public islands never take anyone to vanilla's End or Nether (`HubPortals`,
`IslandPortalsMixin`): the SkyBlock mod sends the player where the portal goes. The Hub's fountain (and the old portal
hidden in the Mountain) go to the Dungeon Hub.

## Placing our NPCs (`tools/worlds/place_npcs.py`)

    python tools/worlds/place_npcs.py skyrpg_hub:hub --island hub "<map>" [--write]

Each of our NPCs on the island goes, best first: where the map's own hologram for it floats (the map's year, so it
fits its buildings); NEU's position (Hypixel's current one) if a player can stand there on this map; our override
(the script's `OVERRIDES`); else the nearest spot a player can stand within 8 blocks of NEU's. Facing: towards the
island's middle unless overridden (they look at players walking past anyway).

Decided for the Hub: the Fear Mongerer and Oringo stand where the map's "Spooky Man" handed out Halloween rewards in
October 2022 (7.5 70 -85.5); Diana where the map's Mayor stands, by the elections hall; the Banker in the Bank (the gold
rotunda); our Seraphine Vael by the Library's enchanting table; Amelia and Christopher on the square (NEU's spots are
on the Bank's roof on this map); our Rift Wizard on the Wizard Tower's top floor (y 131), beside the Rift portal.

## The Hub's data on Hypixel's map

- **Zones**: drawn on the map (no overlaps; an area in two boxes is two files with the same name): Village (three
  boxes), Coal Mine (north: the coal veins, from the Blacksmith's street to the Gold Mine pad), Farm (north-east: the
  wheat), Graveyard (north-west: the map's End-biome land), Crypts (under the Graveyard, y 38-57: the tunnels with
  cobwebs and mossy bricks), Forest (west), Ruins (far west), Mountain, Wizard Tower (the round tower at 39 69),
  Fisherman's Hut (east), Wilderness (two boxes) and Colosseum. The Mountain's night Skeletons are gone (Hypixel's
  "High Level" isn't on this map).
- **Launch pads**: the map's slime pads: the Park (-225 72 -16), the Spider's Den (-163 72 -162), the Gold Mine (-10 63
  -231), The Barn (78 71 -184); arrivals 4-9 blocks off each pad, facing the village. (The slime under the south
  path and in the Fishing Outpost's yard is decor.)
- **Warps**: crypt (in the Crypts), castle (the Ruins), da (by Sirius), wizard (the tower's door); the Rift portal on
  the tower's top floor.
- **Fairy souls**: Hypixel's 79 of October 2022 (NEU's repo at that date): 76 sit exactly in the map's open air, the
  other three in blocks (heads set into walls).
- **Spooky Festival**: Spooky mobs in the Graveyard's grounds and the Wilderness by the Fairy Pond; the Bat Firework
  works in the Village's zones.

The markers are found by looking through the world's `dimensions/` folder when the server starts (the levels don't
exist yet then).

## The Park on Hypixel's map

- **Zones** from where each wood grows on the map: Birch Park (east, the entrance), Spruce Woods (south), Dark Thicket
  (the middle, above y 73), the Howling Cave (under it: its own mob nameplates on the map put the wolves at x -390 to
  -345, y 56-72, z -52 to -4), Savanna Woodland (west, y 108-116) and Jungle Island (north-west, the top, y 119-129).
- **Jump pads** (a new kind of launch pad, `"jump": [x, y, z]`): Hypixel's Park climbs west on slime pads, each
  throwing you onto a slime ledge above: (-328 81 -16) to (-334 88), (-354 88 -13) to (-354 102 -20), (-372 102 -20) to
  (-377 109), (-407 109 -29) to the Jungle Island's top (-407 119 -37). The throw is worked out from Minecraft's air
  drag and gravity, peaking 2.5 blocks above the ledge so it clears its edge.
- **Launch pads**: the slime pad by Charlie at the entrance (-274 81 -14) back to the Hub (arriving at the Forest's
  pad); the warp in arrives just west of it. Decided: Galatea's pad (not on Hypixel's 2022 map) at the Jungle Island's
  far west edge (-440 127 -106), and arrivals from Galatea (and its Foraging gate) 6 blocks east of it.
- **NPCs**: Charlie from the map's hologram; Gustave, Ryan, Vanessa, the vikings and Master Tactician Funk from NEU;
  Old Shaman Nyko in the hollow under NEU's spot (which is in rock); Melody at the ground nearest NEU's spot (off this
  map's edge); ours beside their neighbours (Kelly with Gustave, the Campfire Adept with Ryan, Worker Xavier with Funk,
  Juliette and Molbert by Galatea's pad).
- **Fairy souls**: Hypixel's 11 of October 2022.

## The End on Hypixel's map

- **The altar**: the ring of 8 End Portal Frames at y 9 around (-671, -276), at the bottom of the Dragon's Nest (the
  map's other frames are decor). `DragonAltar` now finds it around the spawn spot `dragon_altar`
  (spawn_spots/the_end.json; 0, 0 when there's none, as on our own End); the fight centres there too.
- **Zones** (Hypixel's wiki: the Dragon's Gate at -600 22 -275, the Void Sepulture's teleport pad at -562 7 -314): The
  End east of the Gate (y 15 up), the Void Sepulture its depths below (y 0-14), the Dragon's Nest west of the Gate, and,
  decided (Hypixel added it after 2022), the Zealot Bruiser Hideout in the Nest's north-west. Mobs as before, their
  boxes moved.
- **Arrival and pads**: `/warp end` and the pad from the Spider's Den arrive on Hypixel's platform (-502.5 101 -274.5);
  the slime pad at its east end (-496 100 -277) goes back to the Spider's Den.
- **NPCs**: from NEU (the Pearl Dealer at the arrival, Guber above it, Gregory and the Lone Adventurer at the Gate);
  Tyzzo beside the Lone Adventurer.
- **Fairy souls**: Hypixel's 12 of October 2022.

## The Dungeon Hub on Hypixel's map

- **Arrival** (`/warp dungeon_hub`, the Hub's fountain, back from a run): the courtyard at (-30.5 121 0.5), facing
  Mort at the great gate.
- **NPCs** from NEU: Mort, Ophelia, Malik, Adventurer Saul, Croesus. (The map's holograms are its parkour's best
  times.)
- **Fairy souls**: Hypixel's 7 of October 2022.

## The Garden's Barn plot (`tools/worlds/hypixel_garden.py`)

    python tools/worlds/hypixel_garden.py "<capture of a fresh Garden>" [--world-dir run/server/world]

Every profile's Garden is stamped from the structure `skyrpg_skyblock:garden/barn` on its first visit. Minecraft looks
in the world's `generated/<namespace>/structure/` folder before the mod's own structures, so the tool writes Hypixel's
middle plot there (x and z -48..47 around the Garden's middle; its bedrock at y 66, dirt to 69, ground at 70, moved
down 6 to our ground at 64), with `hypixel_garden.json` beside it. Block entities (the heads, signs, chests) come
along, leaves made persistent. Gardens already built keep their Barn; `/sbgarden rebuild` (admins) clears your Barn plot
and stamps it again. Undo by deleting both files.

The marker switches the Garden to Hypixel's spots (`GardenLayout.HYPIXEL`): you arrive on the path 12 blocks south of
the Barn's door, facing it; the Desk is the corner inside the Barn with the crafting table and chests (-8..-3, -30..-25
from the middle); the Composter the cauldron pen under the hay roof east of the Barn (22..32, -44..-23); SkyMart in
the Barn (NEU's spot), Pesthunter Phillip by the shed to its west (NEU's), the five visitors on the lawn either side
of the door, Jacob and Anita down the path (decided: Hypixel has them in the Hub). NPCs and the arrival stand on the
nearest ground to their spot, as the plot isn't flat. The other 24 plots are still ours (fenced grass, wild growth when
unlocked): the first capture only reached the plots beside the Barn.

## Galatea (`foraging_2` in NEU)

The first capture (October 2026) covers the main island (x -775..-408, z -127..212; NEU's 28 NPCs all on it), part of
the south and a small island at (-480, -90), but it had been opened in singleplayer: it waits for a clean capture.
Its holograms include the mobs' nameplates (Chillshot, Chillblade, Phanpyre with their levels), which say where
Galatea's mobs live.

## The Catacombs: Hypixel's rooms and boss arenas (`tools/worlds/hypixel_dungeons.py`)

The thread's "[Dungeons] The Entrance" and "Floor I" to "Floor VI" downloads are seven real runs (October 2022): each a
grid of rooms on Hypixel's 32-block cells from (-200, -200), bedrock at y 66, the floor at y 68, walls to y 99,
basements to y 30 or so; and the floor's boss arena south-east of the rooms.

    python tools/worlds/hypixel_dungeons.py survey "<capture>"... --sheet rooms.png --json rooms.json
    python tools/worlds/hypixel_dungeons.py install "<capture>"... --types types.json
    python tools/worlds/hypixel_dungeons.py arenas "<capture>"...

- **survey** finds the rooms (cells of one room have their gap built over; between rooms the gap is empty air, all the
  way down), their shapes, and the copies of one room (the same block counts whichever way it's turned): 65 different
  rooms in the seven runs, every shape. A contact sheet shows them, to say which are special.
- **install** writes each different room under `world/generated/skyrpg_skyblock/structure/catacombs/rooms/hypixel/
  <category>/` (never committed): turned to our orientation (1xN along x, the L missing its +x+z cell; block states
  turned too), y 30 to 98 (its floor at 38), the doorways walled up (the mod cuts the ones it uses), and our markers: a
  secret chest on every chest, star mobs where the capture's own star mobs stood and on open floor (three a cell, an
  elite in rooms of 3+ cells), a key spot; the Entrance's spawn, the Blood room's Watcher, the Fairy room's fairy.
  Decided from the rooms' looks and how often they come up (every run has one Fairy and one Blood room): two Entrance
  rooms, one Blood, one Fairy, two trap rooms (dispensers, TNT); the puzzle rooms (Teleport Maze, Ice Path, Ice Fill,
  Water Board, Boulder, and the Quiz/Blaze/Creeper-looking ones) and the boss arenas' doorways are left out (our three
  puzzle rooms stay ours until those puzzles are in). 53 rooms in all.
- `RoomLibrary` takes Hypixel's rooms first for every category that has some; `Run` builds them 30 blocks deeper
  (`HYPIXEL_DROP`) so their floor lines up with ours, and reads their markers from the basement up.
- **arenas** writes each floor's arena (everything built outside the rooms, the biggest piece) as
  `catacombs/boss/hypixel/<boss>.nbt` with two markers: its middle (the middle of the floor under its roof) and its way
  in (the floor spot nearest the rooms). `BossFight` builds Hypixel's arena when it's there, takes those two from it,
  and puts each boss's spots on the nearest standing ground (Hypixel's arenas aren't our 61-block square).
