# Writing a Hub area script (brief for helpers)

You are building one of the Hub's areas for a Minecraft 26.3 SkyBlock server: a floating island near the village of
Skyhaven (a floating town in the sky, centred on 0,64,0, main island roughly x -60..64, z -62..58), joined to it by a
sky bridge. Hypixel SkyBlock's Hub is the model: same purpose, mobs and resources, but in our floating-island style.

## Rules

- Write exactly one new file: `tools/builds/hub_areas/<id>.py`. Do not edit `kit.py`, `graveyard.py` or anything else;
  if you need a helper (tunnels, crops, a building), define it in your own script.
- Read `kit.py` (the API) and `graveyard.py` (a finished example) first. Use `Area`, `island`, `path`, `bridge`,
  `flatten`, `scatter`, `oak`/`spruce`/`dead_tree`, `npc`, `fairy_soul`, `warp`, `zone`, `save`.
- Run it from `tools/builds/hub_areas` with `PYTHONIOENCODING=utf-8 python <id>.py`. It writes the structure, the area
  file, the zone file, `out/<id>_spots.json` and previews `out/<id>_top.png` (top-down) and `out/<id>_side.png` (from
  the south). Look at both previews with the Read tool and iterate until the island reads well: no gaps between the
  bridge and the island, nothing floating by mistake, the bridge reaching the island's edge.
- Never run gradle, the game, `tools/hotswap.py`, `tools/mc.py` or any minecraft tool. Python and image reading only.
- Stay inside your box (cells outside it are dropped silently). Coordinates are world coordinates.
- Block ids are vanilla 26.x names: `minecraft:short_grass` (not `grass`), `minecraft:iron_chain` (not `chain`),
  `pointed_dripstone`, stairs/slabs with properties like `oak_stairs[facing=north,half=bottom]`. `save()` checks every id
  against the game's block list and stops on an unknown one.
- Keep it within roughly 150k to 450k blocks. The underside of the island is part of the look (the kit makes it).
- Plain `minecraft:stone` only where players are meant to mine it (if your zone lists stone); use andesite, tuff,
  cobblestone, deepslate, calcite and the like elsewhere, so nobody can dig the island apart.
- Paths: `area.path`. Players walk at surface + 1. Bridges: `area.bridge(a, b, y)` puts the deck at `y`, so players walk
  at y + 1; use the deck y given in your assignment so it lines up with the village side.
- Mobs in the zone use Hypixel's stats (given below). Put mobs in a sub-box (`from`/`to` on the mob) over open ground or
  cave floors where they make sense.
- Fairy Souls: record 3 or 4 with `area.fairy_soul(x, y, z)` (the spot a player stands in), some in fun hidden places.
- NPC spots: `area.npc(name, x, y, z, yaw, note)` where the NPC should stand (on the floor, with 2 blocks of air).
- A warp spot: `area.warp(name, x, y, z, yaw)` near where the bridge arrives.
- When you finish, report: the block count, where things are (coordinates of the main features, NPC spots, souls, the
  warp), the zone (blocks and mobs), and anything you weren't sure about.

## Other islands (not the Hub)

Hypixel's other public islands (The Farming Islands, The Park, the Gold Mine, the Deep Caverns, the Spider's Den) each
live in their own void dimension, reached by launch pads and warps. For those:

- Create the area with its dimension and island name:
  `Area("barn", origin, size, dimension="skyrpg_skyblock:farming_islands", island="The Farming Islands")`.
  There is no village there: you have the whole dimension, but stay inside your box.
- `area.arrival(x, y, z, yaw)`: where players land (warps, launch pads, falling into the void). Put it on solid
  ground with room, at the island's edge or main path.
- `area.warp(name, x, y, z, yaw)`: record the island's warp spot (usually the arrival).
- `area.pad(name, x1, y1, z1, x2, y2, z2, warp=..., skill=..., level=..., destination=(dimension, (x, y, z), yaw))`:
  a launch pad box; standing in it sends you to a Fast Travel warp, or to an exact destination (pads back to the Hub).
  Build the pad itself (a 3x3 of slime blocks with a pressure plate, a ring, lanterns, a sign naming where it goes) and
  make the pad box cover the 3x3 at standing height (y = surface + 1, two blocks tall).
- `area.zone(...)` once per named part of the island (zones must not overlap; each part's name is what the sidebar shows
  there). Breakable resources go in each zone's `blocks`, mobs in its `mobs`.
- Passive animals (cows, sheep...) are fine as zone mobs too: give them a name and level, no damage or xp.
- Neutral mobs (wolves, zombified piglins) in a zone with a name turn hostile to nearby players on their own.
