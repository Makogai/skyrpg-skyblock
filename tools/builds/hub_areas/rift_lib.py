"""Shared layout for the Rift's area scripts (rift_wyld_woods.py, rift_black_lagoon.py, and the later areas).

The Rift (Hypixel's, SkyBlock Level 12) lives in its own void dimension, skyrpg_skyblock:rift, under a violet sky with
lavender grass and violet leaves (the biome tints grass, vines and oak/dark oak/jungle/acacia/mangrove leaves). It is one
fractured landmass: every area is solid land that meets its neighbours along a shared boundary, and the outer edges of
the Rift break off into the void as jagged cliffs with a hanging underside (rift shards: crying obsidian, purpur, amethyst
and tuff spikes, the odd floating chunk). Hypixel's own coordinates are kept (north is -z), so the areas sit where the
wiki puts them:

    wyld_woods      x -160..-11,  z   60..199   giant interconnected trees, treehouses, the Rift Wizard Tower (arrival)
    black_lagoon    x -200..-61,  z  -30..59    a swamp of channels, giant mushrooms, the Lagoon Hut, the Leeches Lair
    west_village    x -140..-31,  z -120..-31   (north of the Black Lagoon, across a rope bridge)
    village_plaza   x  -30..69,   z -120..-31   (Barrier Street on its east side, Deja Vu Alley's islets at its north)
    dreadfarm       x -110..-1,   z -210..-121
    (part 3: placed so every area meets another along an edge; only the Mountaintop keeps Hypixel's spot)
    living_cave     x    0..69,   z -240..-121  north of the Plaza: Deja Vu Alley's islets lead on to it
    colosseum       x   70..199,  z -240..-121  east of the Living Cave, north of the Photon Pathway
    photon_pathway  x   70..199,  z -120..-31   east of the Plaza (its east side is a cliff: bridges cross)
    chateau         x  200..299,  z -240..-31   Stillgore Chateau, east of the Pathway and the Colosseum
    mountaintop     x  -10..199,  z  -30..59    the tall peak south of the Plaza and the Pathway (to y 170)

Ground rules:
- Ground level: the surface is at y 68 near the boundaries (players walk at y 69). Inside your box terrain may rise and
  fall (y 60..110 is fine; the Wizard Tower goes higher), but within SEAM (6) blocks of a boundary you share with a
  neighbour keep the surface at y 68, so both sides meet without a step.
- Land is solid down to LAND_BOTTOM (40): `land(area, x, z, top, surface, under, body)` fills one column. At the Rift's
  outer edge (a box side with no neighbour) end the land in a broken cliff and hang a jagged underside below it (y 40
  down to about y 10 at the deepest, thinner towards the edge), so it reads as a shard torn out of the world.
- Crossings: where two areas meet, a path crosses the boundary at the CROSSINGS below (a 5-wide path of coarse dirt,
  rooted dirt and mud bricks at y 68); keep the area's main path joined to it.
- Players stand at surface + 1. NPC spots, warps, souls and spawn spots are where players stand.
- Spots the mod uses are NPC spots whose names start with a prefix:
    "enigma:<name>"   an Enigma Soul (Hypixel names: "Two Plates", "Up in the Sky"...), placed where a player reaches it
    "spawn:<name>"    something the mod spawns or uses there (e.g. "spawn:exit_portal", "spawn:leech_supreme")
    plain names       NPCs (Hypixel's names: "Enigma", "Argofay Trafficker", "Kay"...)
- Blocks: no plain minecraft:stone (use tuff, andesite, deepslate, calcite, mud, packed mud). The Rift's look: lavender
  grass, rooted dirt and coarse dirt paths, mud, moss, violet-tinted leaves (dark oak and mangrove), pale oak and
  mangrove wood, amethyst, purpur, crying obsidian accents, glow lichen and pink petals, end rods and purple lanterns
  (sea lanterns behind purple stained glass) for light.
"""
from kit import Area

DIM = "skyrpg_skyblock:rift"
ISLAND = "The Rift"
GROUND = 68
SEAM = 6
LAND_BOTTOM = 40
Y0, Y1 = 0, 200

BOXES = {
    "wyld_woods": ((-160, Y0, 60), (150, Y1 - Y0, 140)),
    "black_lagoon": ((-200, Y0, -30), (140, Y1 - Y0, 90)),
    "west_village": ((-140, Y0, -120), (110, Y1 - Y0, 90)),
    "village_plaza": ((-30, Y0, -120), (100, Y1 - Y0, 90)),
    "dreadfarm": ((-110, Y0, -210), (110, Y1 - Y0, 90)),
    "living_cave": ((0, Y0, -240), (70, Y1 - Y0, 120)),
    "colosseum": ((70, Y0, -240), (130, Y1 - Y0, 120)),
    "photon_pathway": ((70, Y0, -120), (130, Y1 - Y0, 90)),
    "chateau": ((200, Y0, -240), (100, Y1 - Y0, 210)),
    "mountaintop": ((-10, Y0, -30), (210, Y1 - Y0, 90)),
}

# Where neighbouring areas meet: (boundary, the crossing path's centre). The Wyld Woods and the Black Lagoon share
# z = 60 for x -160..-61; their path crosses at x -105 (Tel Kar stands near it, at about (-113, 69, 62)).
CROSSINGS = {
    ("wyld_woods", "black_lagoon"): ("z", 60, -105),
    # The Black Lagoon's north edge is a cliff over the void: West Village reaches it with a rope bridge at x -90.
    ("black_lagoon", "west_village"): ("z", -31, -90),
    ("west_village", "village_plaza"): ("x", -30, -70),
    ("west_village", "dreadfarm"): ("z", -121, -70),
    ("village_plaza", "dreadfarm"): ("z", -121, -15),
    # Part 3. Where a neighbour's edge is a cliff, the new area builds a bridge from its side up to that cliff.
    ("village_plaza", "living_cave"): ("z", -121, 40),        # Deja Vu Alley's last islet (61, -109) area -> north
    ("living_cave", "colosseum"): ("x", 70, -180),
    ("village_plaza", "photon_pathway"): ("x", 70, -60),      # the Plaza's east cliff
    ("photon_pathway", "colosseum"): ("z", -121, 140),
    ("photon_pathway", "chateau"): ("x", 200, -80),
    ("colosseum", "chateau"): ("x", 200, -180),
    ("village_plaza", "mountaintop"): ("z", -30, 20),         # the Plaza's south cliff
    ("photon_pathway", "mountaintop"): ("z", -30, 130),
}
# Gates (the mod enforces them): West Village and Dreadfarm need the Supreme Timecharm; the Plaza needs the Mirrorverse
# and Chicken N Egg Timecharms (part 3). Put a gate arch where each crossing enters a gated area (decoration only).

# The arrival: inside the Rift Wizard Tower, where players appear from the Hub's portal.
ARRIVAL = (-45.5, 69.0, 110.5)


def new_area(region, version=1):
    origin, size = BOXES[region]
    return Area("rift_" + region, origin, size, version=version, shell=1, dimension=DIM, island=ISLAND)


def land(area, x, z, top, surface, under, body):
    """One land column: `surface` at y top, three layers of `under`, then `body` down to LAND_BOTTOM. Each may be a block
    id or a picker from area.mix()."""
    def pick(b, y):
        return b(x, y, z) if callable(b) else b
    area.set(x, top, z, pick(surface, top))
    for y in range(top - 1, top - 4, -1):
        if y >= LAND_BOTTOM:
            area.set(x, y, z, pick(under, y))
    for y in range(top - 4, LAND_BOTTOM - 1, -1):
        area.set(x, y, z, pick(body, y))
