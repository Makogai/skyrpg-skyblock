"""Block lookups in a saved world's region folder (1.16+ chunks): Blocks(region_dir).at(x, y, z) -> "minecraft:stone"."""
from pathlib import Path

import nbt


class Blocks:
    def __init__(self, region_dir):
        self.dir = Path(region_dir)
        self.regions = {}
        self.sections = {}

    def _chunk(self, cx, cz):
        key = (cx >> 5, cz >> 5)
        if key not in self.regions:
            path = self.dir / f"r.{key[0]}.{key[1]}.mca"
            self.regions[key] = {}
            if path.exists():
                for lx, lz, chunk in nbt.chunks(path):
                    level = chunk.get("Level", chunk)
                    self.regions[key][(level["xPos"], level["zPos"])] = level
        return self.regions[key].get((cx, cz))

    def at(self, x, y, z):
        cx, cz = x >> 4, z >> 4
        key = (cx, cz, y >> 4)
        if key not in self.sections:
            self.sections[key] = None
            level = self._chunk(cx, cz)
            if level:
                for s in level.get("Sections", level.get("sections", [])):
                    if s.get("Y") == y >> 4:
                        palette = s.get("Palette") or (s.get("block_states") or {}).get("palette")
                        states = s.get("BlockStates") or (s.get("block_states") or {}).get("data")
                        if palette:
                            bits = max(4, (len(palette) - 1).bit_length())
                            data = nbt.unpack(states, bits, 4096) if states else [0] * 4096
                            self.sections[key] = (palette, data)
        section = self.sections[key]
        if section is None:
            return "minecraft:air"
        palette, data = section
        idx = data[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]
        return palette[idx]["Name"] if idx < len(palette) else "minecraft:air"


PASSABLE = ("air", "grass", "fern", "flower", "torch", "sign", "carpet", "button", "lever", "pressure_plate", "rail", "vine", "snow",
            "tulip", "dandelion", "poppy", "orchid", "allium", "bluet", "daisy", "cornflower", "lily_of_the_valley", "rose", "sapling",
            "mushroom", "wheat", "carrots", "potatoes", "beetroots", "sugar_cane", "banner", "tall_grass", "large_fern", "dead_bush",
            "cobweb", "redstone_wire", "tripwire", "lilac", "peony", "sunflower", "string", "light", "barrier_off")


def passable(name):
    base = name.split(":")[-1]
    return base == "air" or base == "cave_air" or base == "void_air" or any(p in base for p in PASSABLE[1:])


def standable(blocks, x, y, z):
    """Solid ground below, room for a player at y and y+1."""
    below = blocks.at(x, y - 1, z)
    return not passable(below) and "water" not in below and "lava" not in below and passable(blocks.at(x, y, z)) and passable(blocks.at(x, y + 1, z))
