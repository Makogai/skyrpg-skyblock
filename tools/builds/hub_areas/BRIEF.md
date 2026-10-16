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
- Block ids are vanilla 26.x (1.21.x names): `minecraft:short_grass` (not `grass`), `minecraft:chain`, `pointed_dripstone`,
  stairs/slabs with properties like `oak_stairs[facing=north,half=bottom]`. Unknown ids break the build, so stick to
  common blocks.
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
