#!/usr/bin/env python3
"""Hypixel's Glacite Tunnels as a generated world: writes the worldgen data for skyrpg_skyblock:glacite_tunnels.

    python tools/skyblock/gen_glacite.py

Writes (generated: overwritten each run) under skyblock/src/main/resources/data/skyrpg_skyblock/:

    dimension/glacite_tunnels.json             noise generator, one biome
    worldgen/noise_settings/glacite_tunnels.json
    worldgen/material_rule/glacite_tunnels.json
    worldgen/biome/glacite_tunnels.json
    worldgen/feature/glacite/*.json + worldgen/placed_feature/glacite/*.json   Glacite, Tungsten, Umber, Mithril, gems
    skyrpg/zones/glacite_*.json                the Glacite Tunnels (mining) and the Dwarven Base Camp (safe)

The Tunnels are 256 x 256 around 0, 0, rock from y 96 to 176 with winding tunnels between y 104 and 170 (two crossed 3D
noises: air where both are near zero) and a few caverns, snow on their floors. In the rock: Glacite (packed ice),
Tungsten (cobblestone, clay), Umber (terracotta, brown terracotta, red sandstone), Mithril, Titanium and the Tunnels'
gemstones as stained glass (Aquamarine blue, Onyx black, Citrine brown, Peridot green). The Dwarven Base Camp is a
placed structure at the north end (tools/builds/hub_areas/glacite_base_camp.py).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock"
NS = "skyrpg_skyblock"
HALF = 128
BOTTOM = 96
TOP = 176
LOW = 104
HIGH = 170
# Tunnel and cavern shape (tuned in game for about a quarter of the band to be open).
TUNNEL_XZ, TUNNEL_Y, TUNNEL_WIDTH = 1.4, 1.7, 0.17
CAVERN_XZ, CAVERN_Y, CAVERN_RARITY = 1.0, 1.4, 0.40
# The Base Camp's box (glacite_base_camp.py builds inside it): x -24..24, z -128..-88, y 116..150.
CAMP = (-24, 116, -128, 24, 150, -88)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def grad(axis, a, va, b, vb):
    return {"type": "minecraft:gradient", "axis": axis, "from_coordinate": a, "from_value": va, "to_coordinate": b, "to_value": vb}


def dmax(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def dmin(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def noise(name, xz, y):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def absolute(f):
    return {"type": "minecraft:abs", "input": f}


def noise_settings():
    # Tunnels: air where two noises are both near zero (the line where their zero surfaces cross).
    tunnels = add(dmax(absolute(noise("minecraft:spaghetti_3d_1", TUNNEL_XZ, TUNNEL_Y)),
                       absolute(noise("minecraft:spaghetti_3d_2", TUNNEL_XZ, TUNNEL_Y))), -TUNNEL_WIDTH)
    # Caverns where the cheese noise is low.
    caverns = add(noise("minecraft:cave_cheese", CAVERN_XZ, CAVERN_Y), CAVERN_RARITY)
    open_ = mul(dmin(tunnels, caverns), 4.0)
    # Solid above and below the tunnel band, and past the edges.
    band = dmax(grad("y", LOW - 8, 3.0, LOW, 0.0), grad("y", HIGH, 0.0, HIGH + 6, 3.0))
    walls = dmax(dmax(grad("x", -HALF - 8, 4.0, -HALF, 0.0), grad("x", HALF, 0.0, HALF + 8, 4.0)),
                 dmax(grad("z", -HALF - 8, 4.0, -HALF, 0.0), grad("z", HALF, 0.0, HALF + 8, 4.0)))
    density = {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 4, "input": dmax(dmax(open_, band), walls)}
    return {
        "debug_functions": [],
        "default_block": "minecraft:stone",
        "default_fluid": "minecraft:water",
        "disable_mob_generation": True,
        "legacy_random_source": False,
        "material_rule": f"{NS}:glacite_tunnels",
        "noise": {"min_y": BOTTOM, "height": TOP - BOTTOM},
        "noise_router": {"chunk_surface_level": 0.0, "continents": 0.0, "depth": 0.0, "erosion": 0.0, "final_density": density,
                         "ridges": 0.0, "temperature": 0.0, "vegetation": 0.0},
        "sea_level": BOTTOM - 10,
        "spawn_target": [],
    }


def material_rule():
    return {"type": "minecraft:sequence", "sequence": [
        "minecraft:bedrock_roof",
        "minecraft:bedrock_floor",
        {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": {"type": "minecraft:block", "result_state": "minecraft:snow_block"}},
        {"type": "minecraft:condition", "if_true": "minecraft:on_ceiling", "then_run": {"type": "minecraft:block", "result_state": "minecraft:packed_ice"}},
    ]}


FEATURES = []
DECOR = []


def ore(name, block, size, count, low=LOW - 4, high=HIGH + 4, exposed=0.0, target="minecraft:stone"):
    """A vein feature (stone -> block) placed `count` times a chunk between low and high."""
    write(DATA / f"worldgen/feature/glacite/{name}.json", {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": exposed, "size": size,
        "targets": [{"state": block, "target": {"predicate_type": "minecraft:block_match", "block": target}}],
    })
    write(DATA / f"worldgen/placed_feature/glacite/{name}.json", {
        "feature": f"{NS}:glacite/{name}",
        "placement": [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": low},
                                                                    "max_inclusive": {"absolute": high}}},
                      {"type": "minecraft:biome"}],
    })
    FEATURES.append(f"{NS}:glacite/{name}")


def biome():
    features = [[] for _ in range(11)]
    features[6] = FEATURES
    features[7] = DECOR
    return {
        "attributes": {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 6000, "replace_current_music": True,
                                                             "sound": "minecraft:music.overworld.frozen_peaks"}},
            "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {
                c: [] for c in ("ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient",
                                "water_creature")}}, "modifier": "overlay"},
        },
        "carvers": [],
        "downfall": 0.5,
        "effects": {"water_color": "#3d57d6", "foliage_color": "#60a17b", "grass_color": "#80b497"},
        "features": features,
        "has_precipitation": False,
        "temperature": -0.5,
    }


ZONE_DIR = DATA / "skyrpg/zones"
EDGE = HALF - 6
REGROW = 20


def write_zones():
    """The Glacite Tunnels: every mining block becomes bedrock and grows back after 20 seconds. Split around the Base
    Camp's box so no zones overlap; the Camp is safe (no mining; Cold goes away there)."""
    back = {"becomes": "minecraft:bedrock", "regrow": REGROW}
    mined = ["minecraft:stone", "minecraft:packed_ice", "minecraft:cobblestone", "minecraft:clay", "minecraft:terracotta",
             "minecraft:brown_terracotta", "minecraft:red_sandstone", "minecraft:gray_wool", "minecraft:cyan_terracotta",
             "minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:light_blue_wool",
             "minecraft:polished_diorite"]
    for color in ("blue", "black", "brown", "green"):
        mined += [f"minecraft:{color}_stained_glass", f"minecraft:{color}_stained_glass_pane"]
    blocks = {b: back for b in mined}
    for old in ZONE_DIR.glob("glacite_*.json"):
        old.unlink()
    x1, y1, z1, x2, y2, z2 = CAMP

    def zone(file, name, a, b, rules):
        data = {"name": name, "dimension": f"{NS}:glacite_tunnels", "from": list(a), "to": list(b)}
        if rules:
            data["blocks"] = rules
        write(ZONE_DIR / f"glacite_{file}.json", data)

    zone("tunnels", "Glacite Tunnels", (-EDGE, BOTTOM, z2 + 1), (EDGE, TOP, EDGE), blocks)
    zone("tunnels_west", "Glacite Tunnels", (-EDGE, BOTTOM, -EDGE), (x1 - 1, TOP, z2), blocks)
    zone("tunnels_east", "Glacite Tunnels", (x2 + 1, BOTTOM, -EDGE), (EDGE, TOP, z2), blocks)
    zone("base_camp", "Dwarven Base Camp", (x1, y1, z1 if z1 > -EDGE else -EDGE), (x2, y2, z2), None)


def main():
    # Glacite (packed ice) everywhere, the most common.
    ore("glacite", "minecraft:packed_ice", 14, 14)
    # Tungsten: cobblestone, and clay (3 each).
    ore("tungsten", "minecraft:cobblestone", 12, 7)
    ore("tungsten_clay", "minecraft:clay", 8, 3)
    # Umber: terracotta, brown terracotta (2) and red sandstone (3).
    ore("umber", "minecraft:terracotta", 12, 6)
    ore("umber_brown", "minecraft:brown_terracotta", 9, 3)
    ore("umber_red", "minecraft:red_sandstone", 6, 2)
    # Mithril and Titanium, as in the Dwarven Mines.
    ore("mithril_gray", "minecraft:gray_wool", 10, 5)
    ore("mithril_prismarine", "minecraft:prismarine", 7, 3)
    ore("mithril_blue", "minecraft:light_blue_wool", 4, 2)
    ore("titanium", "minecraft:polished_diorite", 3, 2)
    # The Tunnels' gemstones.
    ore("aquamarine", "minecraft:blue_stained_glass", 12, 2)
    ore("onyx", "minecraft:black_stained_glass", 12, 2)
    ore("citrine", "minecraft:brown_stained_glass", 12, 2)
    ore("peridot", "minecraft:green_stained_glass", 12, 2)
    # Ice in the walls and blue ice on some floors, for the look.
    ore("ice", "minecraft:ice", 16, 4, exposed=0.4)
    ore("blue_ice", "minecraft:blue_ice", 10, 3, target="minecraft:snow_block")
    DECOR.append("minecraft:glow_lichen")
    write(DATA / "worldgen/biome/glacite_tunnels.json", biome())
    write(DATA / "worldgen/noise_settings/glacite_tunnels.json", noise_settings())
    write(DATA / "worldgen/material_rule/glacite_tunnels.json", material_rule())
    write_zones()
    write(DATA / "dimension/glacite_tunnels.json", {
        "type": "skyrpg_hub:public_glacite",
        "generator": {"type": "minecraft:noise", "settings": f"{NS}:glacite_tunnels",
                      "biome_source": {"type": "minecraft:fixed", "biome": f"{NS}:glacite_tunnels"}},
    })
    print(f"Glacite Tunnels worldgen: {len(FEATURES)} vein placements")


if __name__ == "__main__":
    main()
