"""Shared layout for Galatea's area scripts (galatea_north.py, galatea_south.py).

Galatea (Hypixel's, the 2025 Foraging update) is an archipelago in its own void dimension, skyrpg_skyblock:galatea. Its
first island, **Moonglade Marsh**, is a lush, misty marsh-forest under a moonlit sky: fig groves on high plateaus, a
great dark lake (Murkwater Loch) ringed with mangroves standing in shallow water, wetlands of reeds and lily pads, mossy
ruins and a timber village (Tangleburg). Hypixel's map isn't copied block for block; the zones, their purpose and their
feel are. North is -z.

    galatea_north   x -150..149, z -160..-1   Tangleburg (the arrival), the Evergreen Plateau (fig groves),
                                              the North and West Reaches (forest), the Forest Temple, the Dragon's Lair
    galatea_south   x -150..149, z    0..159  Murkwater Loch (the lake and its mangroves), Murkwater Shallows (under
                                              the water), Murkwater Outpost, the South Reaches and South Wetlands,
                                              the Squid Cave

Ground rules:
- Ground level: the surface is at y 68 near the shared boundary z = 0 (players walk at y 69). Inside your box terrain
  may rise and fall (y 60..120; the plateau goes high), but within SEAM (6) blocks of z = 0 keep the surface at y 68.
- Crossings: the two halves meet along z = 0; main paths cross it at x -60 and x 60 (5 wide). Join your paths to them.
- The outer edges (x -150 / 149, z -160 / 159) break off into the void as a cliff with a hanging underside, like any
  of our islands (land solid down to LAND_BOTTOM, then a jagged underside).
- Look: dark oak, spruce, mangrove and pale oak woods for buildings; moss, rooted dirt, mud, podzol, grass; dripleaf,
  lily pads, ferns, azalea bushes, glow lichen, fireflies (light blocks under leaves are fine), lanterns and soul
  lanterns. No plain minecraft:stone (andesite, tuff, deepslate, mossy cobblestone, calcite instead).

THE TREES (the mod chops them, so follow these exactly):
- **Fig trees** grow on the Evergreen Plateau and in the Reaches (galatea_north) and here and there in the south. A fig
  tree is a trunk of `minecraft:jungle_log[axis=y]` (5-9 tall, 1 wide, or 2x2 for big ones) with branches of
  `minecraft:jungle_log[axis=x]` / `[axis=z]` reaching out 1-4 blocks, and a canopy of `minecraft:azalea_leaves` and
  `minecraft:flowering_azalea_leaves`. 12-35 logs a tree.
- **Mangrove trees** ring Murkwater Loch (galatea_south) and a few stand in the wetlands: a trunk of
  `minecraft:mangrove_log[axis=y]` (6-12 tall) rising out of `minecraft:mangrove_roots` (a root skirt of 8-20 blocks, in
  and just above the water, `minecraft:muddy_mangrove_roots` below the water line), branches of
  `minecraft:mangrove_log[axis=x/z]`, a canopy of `minecraft:mangrove_leaves` (moss carpet and hanging propagules fine).
  20-50 logs a tree.
- Use jungle_log, mangrove_log and mangrove_roots for NOTHING else on Galatea (buildings use other woods), and keep
  every tree's logs and roots at least 2 blocks clear of any other tree's (the mod finds a tree by its connected logs).
- Record nothing for the trees: the mod finds them. Aim for 30-45 fig trees (north) and 18-28 mangroves (south).

Spots the mod uses are NPC spots, as on our other islands ("spawn:<name>" for things the mod places; plain names for
NPCs). Each script records 6 Fairy Souls (`area.fairy_soul`), some hidden.
"""
from kit import Area

DIM = "skyrpg_skyblock:galatea"
ISLAND = "Galatea"
GROUND = 68
SEAM = 6
LAND_BOTTOM = 40

BOXES = {
    "north": ((-150, 0, -160), (300, 200, 160)),
    "south": ((-150, 0, 0), (300, 200, 160)),
}
CROSSINGS = [-60, 60]
# Where players land on Galatea (Tangleburg's square, galatea_north): warps and the void put you here.
ARRIVAL = (0.5, 69.0, -40.5)


def new_area(region, version=1):
    origin, size = BOXES[region]
    area = Area("galatea_" + region, origin, size, version=version, shell=1, dimension=DIM, island=ISLAND)
    # Chopped trees vanish for a minute: hollowing keeps the ground under and around them.
    area.gone_ids = {"minecraft:jungle_log", "minecraft:mangrove_log", "minecraft:mangrove_roots", "minecraft:muddy_mangrove_roots"}
    return area


def land(area, x, z, top, surface, under, body):
    """One land column: `surface` at y top, three layers of `under`, then `body` down to LAND_BOTTOM (each a block id or
    a picker from area.mix())."""
    def pick(b, y):
        return b(x, y, z) if callable(b) else b
    area.set(x, top, z, pick(surface, top))
    for y in range(top - 1, top - 4, -1):
        if y >= LAND_BOTTOM:
            area.set(x, y, z, pick(under, y))
    for y in range(top - 4, LAND_BOTTOM - 1, -1):
        area.set(x, y, z, pick(body, y))
