"""Shared layout for the Crimson Isle area scripts (crimson_west.py, crimson_center.py, crimson_east.py).

The Crimson Isle (Hypixel's nether island, Combat 22) lives in its own dimension, skyrpg_skyblock:crimson_isle, over a
generated lava sea: every column of the dimension is lava from y 1 to y LAVA (59). The island is three landmasses split
by lava rivers along x = -100 and x = 100, joined by four stone bridges at the CROSSINGS:

    west   x -330..-101  the Barbarian side: Dragontail, the Barbarian Outpost, The Dukedom, the Smoldering Tomb,
                         The Wasteland, the Burning Desert, the Forgotten Skull (Kuudra's entrance)
    center x -100..99    the arrival (from the Spider's Den), the Crimson Fields, the Ruins of Ashfang, the Stronghold,
                         the Blazing Volcano and the Magma Chamber under it
    east   x 100..329    the Mage side: Scarleton, the Mage Outpost, the Cathedral and its Courtyard, the Mystic Marsh,
                         the Dojo, Odger's Hut

North is -z. Each script builds only inside its own BOX (cells outside it are dropped).

Ground rules:
- Land: build every land column with `land(area, x, z, top, surface, body)`: the surface block at `top`, a few layers of
  `under` below it and `body` down to y LAND_BOTTOM (56), just under the lava's surface, so the land rises straight out of
  the lava with nothing floating. Coasts slope down to a beach at y 60-62.
- Keep land at least RIVER (7) blocks away from the shared boundaries (x = -100 and x = 100), so a lava river 14 wide
  runs between the regions, except at the bridges.
- Bridges: at each crossing build your half of a 7-wide bridge (z = cz-3 .. cz+3) with its deck at y = BRIDGE (66) from
  your land up to your box's edge at the boundary; railings (nether brick walls/fences) on both sides, lanterns every
  ~8 blocks, a pillar down into the lava every ~10 blocks. The road on your side climbs or descends from y 66 to your
  terrain within ~15 blocks of the bridge end.
- Nothing carved below y 61 (the generated lava would fill it): caves and chambers keep their floor at y >= 61.
- Players stand at surface + 1. NPC spots, warps and souls are where players stand.
- Spots for things the mod spawns (minibosses, geysers, the Kuudra entrance) are NPC spots with names that start with
  "spawn:" (e.g. area.npc("spawn:Bladesoul", x, y, z, yaw, "the Stronghold's courtyard")).
"""
import math

from kit import Area, hash01, noise  # noqa: F401

DIM = "skyrpg_skyblock:crimson_isle"
ISLAND = "Crimson Isle"
LAVA = 59            # the lava sea's top block (generated)
LAND_BOTTOM = 56     # land is solid down to here
BRIDGE = 66          # bridge decks (players walk at 67)
RIVER = 7            # land keeps this far from a shared boundary
Y0, Y1 = 40, 190
BOXES = {
    "west": ((-330, Y0, -250), (230, Y1 - Y0, 500)),
    "center": ((-100, Y0, -250), (200, Y1 - Y0, 500)),
    "east": ((100, Y0, -250), (230, Y1 - Y0, 500)),
}
# Boundary x -> the z of each bridge's centre line.
CROSSINGS = {-100: (-80, 110), 100: (-80, 110)}
# The arrival from the Spider's Den (centre region, south coast).
ARRIVAL = (0, 67, 205)
# Where the pad home lands you in the Spider's Den (next to its Crimson Isle pad):
# area.pad("the Spider's Den", ..., destination=SPIDERS_DEN_BACK)
SPIDERS_DEN_BACK = ("skyrpg_skyblock:spiders_den", (79.5, 65.0, 18.5), 90.0)


def new_area(region, version=1):
    origin, size = BOXES[region]
    return Area("crimson_" + region, origin, size, version=version, shell=1, dimension=DIM, island=ISLAND)


def land(area, x, z, top, surface, under, body):
    """One land column: `surface` at y top, three layers of `under`, then `body` down to LAND_BOTTOM. `surface`,
    `under` and `body` are block ids or pickers from area.mix()."""
    def pick(b, y):
        return b(x, y, z) if callable(b) else b
    area.set(x, top, z, pick(surface, top))
    for y in range(top - 1, top - 4, -1):
        if y >= LAND_BOTTOM:
            area.set(x, y, z, pick(under, y))
    for y in range(top - 4, LAND_BOTTOM - 1, -1):
        area.set(x, y, z, pick(body, y))
    area.top[(x, z)] = top


def bridge_half(area, x1, x2, cz, deck="nether_bricks", rail="nether_brick_wall", pillar="nether_bricks"):
    """Your half of a crossing's bridge: a 7-wide deck at y BRIDGE from x1 to x2 (inclusive; run it from your land to
    the boundary, cells outside your box are dropped), railings, lanterns every 8 blocks, pillars into the lava every 10."""
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for dz in range(-3, 4):
            area.set(x, BRIDGE, cz + dz, deck)
            area.set(x, BRIDGE - 1, cz + dz, deck)
        area.set(x, BRIDGE + 1, cz - 3, rail)
        area.set(x, BRIDGE + 1, cz + 3, rail)
        if x % 8 == 0:
            area.set(x, BRIDGE + 2, cz - 3, "lantern[hanging=false]")
            area.set(x, BRIDGE + 2, cz + 3, "lantern[hanging=false]")
        if x % 10 == 0:
            for y in range(LAND_BOTTOM, BRIDGE - 1):
                for dz in (-2, 2):
                    area.set(x, y, cz + dz, pillar)


def sign(area, x, y, z, rot, lines, wood="crimson"):
    msgs = ['""'] + ['"%s"' % t.replace("'", "\\'") for t in lines] + ['""'] * 3
    area.set(x, y, z, f"{wood}_sign[rotation={rot}]",
             {"id": "minecraft:sign", "front_text": {"messages": msgs[:4], "color": "white", "has_glowing_text": 1},
              "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": 0}, "is_waxed": 1})


# ---------------------------------------------------------------- mobs (Hypixel's Crimson Isle stats)
# name -> (entity type, level, health, damage, combat xp, coins)
MOBS = {
    "Blaze": ("minecraft:blaze", 70, 700_000, 3_000, 60, 15),
    "Mutated Blaze": ("minecraft:blaze", 70, 1_500_000, 4_000, 90, 20),
    "Bezal": ("minecraft:blaze", 80, 1_000_000, 3_500, 100, 20),
    "Smoldering Blaze": ("minecraft:blaze", 95, 2_000_000, 5_000, 200, 30),
    "Millennia-Aged Blaze": ("minecraft:blaze", 110, 12_000_000, 10_000, 1_000, 100),
    "Flare": ("minecraft:blaze", 90, 5_000_000, 6_000, 400, 50),
    "Ghast": ("minecraft:ghast", 85, 1_000_000, 5_000, 120, 25),
    "Magma Cube": ("minecraft:magma_cube", 75, 400_000, 2_500, 50, 12),
    "Pack Magma Cube": ("minecraft:magma_cube", 90, 1_500_000, 4_000, 120, 25),
    "Magma Cube Rider": ("minecraft:magma_cube", 90, 1_200_000, 4_000, 110, 25),
    "Flaming Spider": ("minecraft:spider", 80, 1_200_000, 3_500, 80, 20),
    "Kada Knight": ("minecraft:piglin_brute", 90, 2_000_000, 6_000, 150, 30),
    "Mushroom Bull": ("minecraft:mooshroom", 80, 1_000_000, 3_000, 100, 20),
    "Wither Skeleton": ("minecraft:wither_skeleton", 70, 300_000, 3_000, 60, 12),
    "Wither Spectre": ("minecraft:wither_skeleton", 70, 300_000, 3_500, 70, 15),
}


def _d(item, chance, lo=1, hi=1):
    return {"item": item, "chance": chance, "min": lo, "max": hi}


# name -> drops (SkyBlock items, into the killer's inventory). Magma Chunks, Blaze Ashes and Spectre Dust are Elle's
# first quest's items.
DROPS = {
    "Blaze": [_d("BLAZE_ROD", 1.0, 1, 2), _d("BLAZE_ASHES", 0.25)],
    "Mutated Blaze": [_d("BLAZE_ROD", 1.0, 2, 3), _d("BLAZE_ASHES", 0.35)],
    "Bezal": [_d("BLAZE_ROD", 1.0, 2, 3), _d("BLAZE_ASHES", 0.35)],
    "Smoldering Blaze": [_d("BLAZE_ROD", 1.0, 2, 4)],
    "Millennia-Aged Blaze": [_d("BLAZE_ROD", 1.0, 6, 10), _d("ENCHANTED_BLAZE_POWDER", 0.2)],
    "Flare": [_d("BLAZE_ROD", 1.0, 3, 5)],
    "Ghast": [_d("GHAST_TEAR", 1.0, 1, 2)],
    "Magma Cube": [_d("MAGMA_CREAM", 1.0, 1, 2), _d("MAGMA_CHUNK", 0.25)],
    "Pack Magma Cube": [_d("MAGMA_CREAM", 1.0, 2, 3), _d("MAGMA_CHUNK", 0.35)],
    "Magma Cube Rider": [_d("MAGMA_CREAM", 1.0, 2, 3), _d("MAGMA_CHUNK", 0.35)],
    "Flaming Spider": [_d("STRING", 1.0, 1, 2), _d("SPIDER_EYE", 0.5)],
    "Kada Knight": [_d("MAGMA_CREAM", 1.0, 1, 3), _d("KADA_LEAD", 0.02)],
    "Mushroom Bull": [_d("RED_MUSHROOM", 1.0, 1, 2), _d("BROWN_MUSHROOM", 1.0, 1, 2), _d("MYCEL", 0.5, 1, 3)],
    "Wither Skeleton": [_d("COAL", 1.0, 1, 2), _d("BONE", 1.0, 1, 2)],
    "Wither Spectre": [_d("COAL", 1.0, 1, 2), _d("SPECTRE_DUST", 0.25)],
}


def mob(name, mx, every, a, b):
    """A zone mob from MOBS: at most `mx` alive, one every `every` seconds, inside the box a..b (with its DROPS)."""
    kind, level, health, damage, xp, coins = MOBS[name]
    spec = {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": mx, "every": every, "from": list(a), "to": list(b)}
    if name in DROPS:
        spec["drops"] = DROPS[name]
    return spec


def dist(x1, z1, x2, z2):
    return math.hypot(x1 - x2, z1 - z2)
