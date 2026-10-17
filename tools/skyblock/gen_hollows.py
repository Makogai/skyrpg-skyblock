#!/usr/bin/env python3
"""Hypixel's Crystal Hollows as a generated world: writes the worldgen data for skyrpg_skyblock:crystal_hollows.

    python tools/skyblock/gen_hollows.py

Writes (generated: overwritten each run) under skyblock/src/main/resources/data/skyrpg_skyblock/:

    dimension/crystal_hollows.json            noise generator + the Hollows biome source (skyblock.hollows.HollowsBiomes)
    worldgen/noise_settings/crystal_hollows.json
    worldgen/material_rule/crystal_hollows.json
    worldgen/biome/hollows_<zone>.json        the five zones and the Nucleus
    worldgen/feature/hollows/*.json + worldgen/placed_feature/hollows/*.json   gemstone veins, Mithril, decoration

The Hollows are 512 x 512 around 0, 0 (x and z from -256 to 256), y 0 to 192: Hard Stone caverns (vanilla's nether
cave noise), solid walls past the edges (gradients on x and z), bedrock floor and roof, a lava sea up to y 24. Quadrants:
Jungle (north-west, Amethyst), Mithril Deposits (north-east, Jade, Mithril, Titanium), Goblin Holdout (south-west,
Amber), Precursor Remnants (south-east, Sapphire); the Magma Fields under y 64 (Topaz); Ruby everywhere. The Nucleus
(the middle) is a placed structure. Gemstones are stained glass, as on Hypixel.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock"
NS = "skyrpg_skyblock"
HALF = 256
TOP = 192
SEA = 24
MAGMA_TOP = 64

ZONES = {
    "jungle": {"water": "#3fa34d", "fog": None},
    "mithril_deposits": {"water": "#3f76e4"},
    "goblin_holdout": {"water": "#6d8a3f"},
    "precursor_remnants": {"water": "#3fb6e4"},
    "magma_fields": {"water": "#e46f3f"},
    "nucleus": {"water": "#d43fe4"},
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def grad(axis, a, va, b, vb):
    return {"type": "minecraft:gradient", "axis": axis, "from_coordinate": a, "from_value": va, "to_coordinate": b, "to_value": vb}


def dmax(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def noise_settings():
    caves = {
        "type": "minecraft:lerp",
        "alpha": grad("y", 2, 0.0, 14, 1.0),
        "first": 2.5,
        "second": {
            "type": "minecraft:lerp",
            "alpha": grad("y", TOP - 22, 1.0, TOP - 6, 0.0),
            "first": 0.9375,
            "second": "minecraft:nether/base_3d_noise",
        },
    }
    base = {
        "type": "minecraft:squeeze",
        "input": {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8,
                  "input": {"type": "minecraft:mul", "left": {"type": "minecraft:blend_density", "input": caves}, "right": 0.64}},
    }
    # Solid past the edges: each wall rises from 0 at the edge to 4 eight blocks further out.
    walls = dmax(dmax(grad("x", -HALF - 8, 4.0, -HALF, 0.0), grad("x", HALF, 0.0, HALF + 8, 4.0)),
                 dmax(grad("z", -HALF - 8, 4.0, -HALF, 0.0), grad("z", HALF, 0.0, HALF + 8, 4.0)))
    final = dmax({"type": "minecraft:add", "left": base, "right": {"type": "minecraft:beardifier"}}, walls)
    return {
        "debug_functions": [],
        "default_block": "minecraft:stone",
        "default_fluid": "minecraft:lava",
        "disable_mob_generation": True,
        "legacy_random_source": False,
        "material_rule": f"{NS}:crystal_hollows",
        "noise": {"min_y": 0, "height": TOP},
        "noise_router": {"chunk_surface_level": 0.0, "continents": 0.0, "depth": 0.0, "erosion": 0.0, "final_density": final,
                         "ridges": 0.0, "temperature": 0.0, "vegetation": 0.0},
        "sea_level": SEA,
        "spawn_target": [],
    }


def floor_rule(biome, block):
    return {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": f"{NS}:hollows_{biome}"},
            "then_run": {"type": "minecraft:condition", "if_true": "minecraft:on_floor",
                         "then_run": {"type": "minecraft:block", "result_state": block}}}


def material_rule():
    return {"type": "minecraft:sequence", "sequence": [
        "minecraft:bedrock_roof",
        "minecraft:bedrock_floor",
        floor_rule("jungle", "minecraft:moss_block"),
        floor_rule("magma_fields", "minecraft:blackstone"),
        floor_rule("goblin_holdout", "minecraft:coarse_dirt"),
    ]}


def ore(name, block, size, count, low, high, biomes, exposed=0.0):
    """A vein feature (stone -> block) and where it goes; returns its placed feature id."""
    write(DATA / f"worldgen/feature/hollows/{name}.json", {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": exposed, "size": size,
        "targets": [{"state": block, "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:stone"}}],
    })
    write(DATA / f"worldgen/placed_feature/hollows/{name}.json", {
        "feature": f"{NS}:hollows/{name}",
        "placement": [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": low},
                                                                    "max_inclusive": {"absolute": high}}},
                      {"type": "minecraft:biome"}],
    })
    for b in biomes:
        FEATURES[b].append(f"{NS}:hollows/{name}")
    return f"{NS}:hollows/{name}"


FEATURES = {z: [] for z in ZONES}
DECOR = {z: [] for z in ZONES}


def biome(zone):
    features = [[] for _ in range(11)]
    features[6] = FEATURES[zone]          # underground_ores
    features[7] = DECOR[zone]             # underground_decoration / vegetation
    return {
        "attributes": {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 6000, "replace_current_music": True,
                                                             "sound": "minecraft:music.overworld.dripstone_caves"}},
            "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {
                c: [] for c in ("ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient",
                                "water_creature")}}, "modifier": "overlay"},
        },
        "carvers": [],
        "downfall": 0.4,
        "effects": {"water_color": ZONES[zone]["water"], "foliage_color": "#2f9e3a", "grass_color": "#4fbf3a"},
        "features": features,
        "has_precipitation": False,
        "temperature": 0.8,
    }


GEMS = ["red", "purple", "lime", "orange", "light_blue", "yellow", "magenta"]
ZONE_DIR = DATA / "skyrpg/zones"
EDGE = HALF - 6       # mining stops this far in from the walls
NUCLEUS = 40          # HollowsBiomes.NUCLEUS


def mob(kind, name, level, health, damage, xp, coins, most, every, drops=()):
    """A zone mob (Hypixel's stats; Zones spawns it on a cave floor anywhere in the zone)."""
    return {"type": kind, "name": name, "level": level, "health": health, "damage": damage, "xp": xp, "coins": coins,
            "max": most, "every": every, "indoors": True, "drops": [{"item": i, "chance": c, "min": lo, "max": hi} for i, c, lo, hi in drops]}


MOBS = {
    "jungle": [mob("minecraft:slime", "Sludge", 47, 5000, 500, 70, 15, 10, 4, [("SLUDGE_JUICE", 1, 1, 2)]),
               mob("minecraft:endermite", "Thyst", 39, 5000, 250, 58, 13, 5, 6, [("ROUGH_AMETHYST_GEM", 1, 4, 7)])],
    "mithril_deposits": [mob("minecraft:vindicator", "Grunt", 54, 15000, 300, 80, 18, 10, 4,
                             [("ROUGH_JADE_GEM", 1, 8, 15), ("TREASURITE", 0.3, 1, 1)])],
    "goblin_holdout": [mob("minecraft:zombie", "Goblin", 23, 800, 300, 34, 7, 10, 3, [("GOBLIN_EGG", 0.01, 1, 1)]),
                       mob("minecraft:zombie", "Knifethrower", 23, 800, 300, 34, 7, 4, 6),
                       mob("minecraft:zombie", "Weakling", 3, 200, 20, 5, 1, 4, 6)],
    "precursor_remnants": [mob("minecraft:iron_golem", "Automaton", 67, 15000, 750, 100, 22, 8, 5, [("ROUGH_SAPPHIRE_GEM", 1, 8, 18)])],
    "magma_fields": [mob("minecraft:magma_cube", "Yog", 93, 35000, 1500, 139, 31, 8, 5, [("ROUGH_TOPAZ_GEM", 1, 8, 15)])],
}


def write_zones():
    """The zones (Hypixel's names) with what can be mined: Hard Stone, gemstones, Mithril and Titanium all turn to air
    for good (regrow -1). Quadrants are split in two around the Nucleus so no zones overlap."""
    gone = {"becomes": "minecraft:air", "regrow": -1}
    rock = {"minecraft:stone": gone}
    rock.update({f"minecraft:{c}_stained_glass": gone for c in GEMS})
    rock.update({f"minecraft:{c}_stained_glass_pane": gone for c in GEMS})
    metal = dict(rock)
    metal.update({b: gone for b in ("minecraft:gray_wool", "minecraft:prismarine", "minecraft:light_blue_wool", "minecraft:polished_diorite")})
    for old in ZONE_DIR.glob("crystal_hollows_*.json"):
        old.unlink()
    upper_low, top = 64, TOP - 6

    def zone(file, name, x1, z1, x2, z2, y1, y2, blocks, mobs=None):
        data = {"name": name, "dimension": f"{NS}:crystal_hollows", "from": [x1, y1, z1], "to": [x2, y2, z2]}
        if blocks:
            data["blocks"] = blocks
        if mobs:
            data["mobs"] = mobs
        write(ZONE_DIR / f"crystal_hollows_{file}.json", data)

    # Each quadrant: the part beside the Nucleus's column, and the rest.
    quads = [("jungle", "Jungle", -1, -1), ("mithril_deposits", "Mithril Deposits", 1, -1),
             ("goblin_holdout", "Goblin Holdout", -1, 1), ("precursor_remnants", "Precursor Remnants", 1, 1)]
    for key, name, sx, sz in quads:
        blocks = metal if key == "mithril_deposits" else rock
        far_x = (-EDGE, -1) if sx < 0 else (0, EDGE)
        far_z = (-EDGE, -NUCLEUS) if sz < 0 else (NUCLEUS - 1, EDGE)
        near_x = (-EDGE, -NUCLEUS) if sx < 0 else (NUCLEUS - 1, EDGE)
        near_z = (-NUCLEUS + 1, -1) if sz < 0 else (0, NUCLEUS - 2)
        zone(key, name, far_x[0], far_z[0], far_x[1], far_z[1], upper_low, top, blocks, MOBS[key])
        zone(key + "_inner", name, near_x[0], near_z[0], near_x[1], near_z[1], upper_low, top, blocks)
    zone("nucleus", "Crystal Nucleus", -NUCLEUS + 1, -NUCLEUS + 1, NUCLEUS - 2, NUCLEUS - 2, upper_low, top, None)
    zone("magma_fields", "Magma Fields", -EDGE, -EDGE, EDGE, EDGE, 6, upper_low - 1, rock, MOBS["magma_fields"])


def main():
    upper =["jungle", "mithril_deposits", "goblin_holdout", "precursor_remnants"]
    # Ruby everywhere (not the Nucleus).
    ore("ruby", "minecraft:red_stained_glass", 10, 3, 8, TOP - 8, upper + ["magma_fields"])
    ore("amethyst", "minecraft:purple_stained_glass", 16, 7, MAGMA_TOP, TOP - 8, ["jungle"])
    ore("jade", "minecraft:lime_stained_glass", 16, 6, MAGMA_TOP, TOP - 8, ["mithril_deposits"])
    ore("amber", "minecraft:orange_stained_glass", 16, 7, MAGMA_TOP, TOP - 8, ["goblin_holdout"])
    ore("sapphire", "minecraft:light_blue_stained_glass", 16, 7, MAGMA_TOP, TOP - 8, ["precursor_remnants"])
    ore("topaz", "minecraft:yellow_stained_glass", 14, 7, 8, MAGMA_TOP, ["magma_fields"])
    # Jasper: small and rare, anywhere above the Magma Fields (Hypixel's Fairy Grottos, scattered).
    ore("jasper", "minecraft:magenta_stained_glass", 6, 1, MAGMA_TOP, TOP - 8, upper)
    # The Mithril Deposits: Mithril in three tiers and Titanium.
    ore("mithril_gray", "minecraft:gray_wool", 14, 14, MAGMA_TOP, TOP - 8, ["mithril_deposits"])
    ore("mithril_prismarine", "minecraft:prismarine", 9, 7, MAGMA_TOP, TOP - 8, ["mithril_deposits"])
    ore("mithril_blue", "minecraft:light_blue_wool", 5, 3, MAGMA_TOP, TOP - 8, ["mithril_deposits"])
    ore("titanium", "minecraft:polished_diorite", 3, 4, MAGMA_TOP, TOP - 8, ["mithril_deposits"])
    # A little of each zone's look in the rock.
    ore("magma_rock", "minecraft:magma_block", 20, 5, 8, MAGMA_TOP, ["magma_fields"], 0.3)
    ore("basalt", "minecraft:basalt", 24, 4, 8, MAGMA_TOP, ["magma_fields"])
    ore("gravel", "minecraft:gravel", 20, 3, MAGMA_TOP, TOP - 8, ["goblin_holdout"])
    ore("old_bricks", "minecraft:cracked_stone_bricks", 18, 5, MAGMA_TOP, TOP - 8, ["precursor_remnants"])
    ore("old_tiles", "minecraft:polished_andesite", 18, 4, MAGMA_TOP, TOP - 8, ["precursor_remnants"])
    DECOR["jungle"] += ["minecraft:lush_caves_ceiling_vegetation", "minecraft:cave_vines", "minecraft:lush_caves_vegetation",
                        "minecraft:spore_blossom", "minecraft:glow_lichen"]
    DECOR["magma_fields"] += ["minecraft:spring_lava", "minecraft:small_basalt_columns"]
    DECOR["precursor_remnants"] += ["minecraft:glow_lichen"]
    for zone in ZONES:
        write(DATA / f"worldgen/biome/hollows_{zone}.json", biome(zone))
    write(DATA / "worldgen/noise_settings/crystal_hollows.json", noise_settings())
    write(DATA / "worldgen/material_rule/crystal_hollows.json", material_rule())
    write_zones()
    write(DATA / "dimension/crystal_hollows.json", {
        "type": "skyrpg_hub:public_mines",
        "generator": {"type": "minecraft:noise", "settings": f"{NS}:crystal_hollows",
                      "biome_source": {"type": f"{NS}:hollows", **{zone: f"{NS}:hollows_{zone}" for zone in ZONES}}},
    })
    print(f"Crystal Hollows worldgen: {sum(len(v) for v in FEATURES.values())} vein placements, {len(ZONES)} biomes")


if __name__ == "__main__":
    main()
