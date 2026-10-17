"""Shared helpers for the Crystal Hollows area scripts (hollows_mines_of_divan, hollows_goblin_den, hollows_precursor_city):
a rock-filled box that is carved with numpy masks, dressed with a dict of blocks, checked for walkability (every NPC,
fairy soul and tunnel end must be reachable from the arrival), lit, and written through the kit.
"""
import math
from collections import deque

import numpy as np

from kit import Area, hash01, noise, _full  # noqa: F401

HOLLOWS = "skyrpg_skyblock:crystal_hollows"


def sq(v):
    return math.sqrt(max(0.0, v))


def bn(block):
    return block if ":" in block.split("[")[0] else "minecraft:" + block


_NONSOLID = {"air", "cave_air", "light", "lantern", "soul_lantern", "iron_chain", "torch", "wall_torch", "soul_torch", "vine",
             "cobweb", "lever", "amethyst_cluster", "end_rod", "ladder", "short_grass", "fern", "lightning_rod",
             "pointed_dripstone", "redstone_wire", "tripwire", "snow"}
_NONSOLID_END = ("_carpet", "_banner", "_sign", "_wall_sign", "_button", "_pressure_plate", "_head", "_skull", "candle", "rail")
_NOFLOOR_END = ("_wall", "fence", "_fence_gate", "iron_bars", "_pane")
_NOFLOOR = {"lava", "campfire", "cauldron", "lava_cauldron", "barrel", "chest", "anvil", "amethyst_cluster", "tinted_glass_x"}
_LIGHTS = {"lantern", "glowstone", "sea_lantern", "shroomlight", "campfire", "end_rod", "wall_torch", "torch", "lava"}


def _solid(name):
    if name in _NONSOLID:
        return False
    return not name.endswith(_NONSOLID_END)


def _nofloor(name):
    return name in _NOFLOOR or name.endswith(_NOFLOOR_END)


class Builder:
    def __init__(self, area_id, origin, size):
        self.id = area_id
        self.O = origin
        self.N = size
        self.E = tuple(o + n for o, n in zip(origin, size))
        self.area = Area(area_id, origin, size, version=1, shell=2, dimension=HOLLOWS, island="Crystal Hollows")
        self.rock = np.ones(size, dtype=bool)
        self.F = {}
        self.npcs = {}
        self.fairy = []
        self.arrival = None
        self.must = {}

    # ------------------------------------------------------------ primitives
    def inb(self, x, y, z):
        (ox, oy, oz), (x1, y1, z1) = self.O, self.E
        return ox <= x < x1 and oy <= y < y1 and oz <= z < z1

    def carve(self, x1, y1, z1, x2, y2, z2):
        (ox, oy, oz), (ex, ey, ez) = self.O, self.E
        x1, x2 = max(min(x1, x2), ox), min(max(x1, x2), ex - 1)
        y1, y2 = max(min(y1, y2), oy), min(max(y1, y2), ey - 1)
        z1, z2 = max(min(z1, z2), oz), min(max(z1, z2), ez - 1)
        if x1 > x2 or y1 > y2 or z1 > z2:
            return
        self.rock[x1 - ox:x2 - ox + 1, y1 - oy:y2 - oy + 1, z1 - oz:z2 - oz + 1] = False

    def put(self, x, y, z, block):
        if self.inb(x, y, z):
            self.F[(x, y, z)] = bn(block)

    def fill(self, x1, y1, z1, x2, y2, z2, block):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.put(x, y, z, block)

    def column(self, x, z, y1, y2, block):
        for y in range(y1, y2 + 1):
            self.put(x, y, z, block)

    def pick(self, x, y, z, table, seed=0):
        total = sum(w for _, w in table)
        r = hash01(x, y, z, seed + 31) * total
        for block, w in table:
            r -= w
            if r <= 0:
                return block
        return table[-1][0]

    def top_air(self, x, z):
        if not (self.O[0] <= x < self.E[0] and self.O[2] <= z < self.E[2]):
            return None
        idx = np.nonzero(~self.rock[x - self.O[0], :, z - self.O[2]])[0]
        return int(idx.max()) + self.O[1] if len(idx) else None

    def chain_lamp(self, x, z, y_lantern):
        """A lantern on a chain from the ceiling above (x, z), hanging at y_lantern."""
        ty = self.top_air(x, z)
        if ty is None or ty <= y_lantern:
            return
        for y in range(y_lantern + 1, ty + 1):
            self.put(x, y, z, "iron_chain[axis=y]")
        self.put(x, y_lantern, z, "lantern[hanging=true]")

    def stalactites(self, cols, chance, seed=0):
        for (x, z) in cols:
            if hash01(x, 5, z, seed + 8) >= chance:
                continue
            ty = self.top_air(x, z)
            if ty is None or (x, ty, z) in self.F:
                continue
            n = 1 + int(hash01(x, 6, z, seed + 9) * 4)
            for i in range(n):
                kind = "tip" if i == n - 1 else ("frustum" if i == n - 2 else "middle" if i else "base")
                if n == 1:
                    kind = "tip"
                self.put(x, ty - i, z, f"pointed_dripstone[vertical_direction=down,thickness={kind}]")

    def tunnel(self, axis, fixed, y, a, b, floor=None, rail=False, lamp_every=8, height=3, half=1):
        """A tunnel (3 wide, 3 tall) along `axis` ("x" or "z") through the box face, from a to b, floor block at y."""
        floor = floor or [("stone_bricks", 5), ("cracked_stone_bricks", 1), ("polished_andesite", 2)]
        lo, hi = min(a, b), max(a, b)
        if axis == "x":
            self.carve(lo, y + 1, fixed - half, hi, y + height, fixed + half)
        else:
            self.carve(fixed - half, y + 1, lo, fixed + half, y + height, hi)
        for t in range(lo, hi + 1):
            for l in range(-half, half + 1):
                x, z = (t, fixed + l) if axis == "x" else (fixed + l, t)
                self.put(x, y, z, self.pick(x, y, z, floor, 5))
            x, z = (t, fixed) if axis == "x" else (fixed, t)
            if rail:
                self.put(x, y + 1, z, "rail[shape=%s]" % ("east_west" if axis == "x" else "north_south"))
            if (t - lo) % lamp_every == 4 and height >= 3:
                self.put(x, y + height, z, "lantern[hanging=true]")

    def npc(self, name, x, y, z, yaw, note):
        self.npcs[name] = (x, y, z, yaw, note)
        self.must["NPC " + name] = (x, y, z)

    def fairy_soul(self, x, y, z):
        self.fairy.append((x, y, z))
        self.must[f"fairy {x},{y},{z}"] = (x, y, z)

    # ------------------------------------------------------------ connections (fences, walls, bars)
    def _connect(self):
        dirs = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}
        ox, oy, oz = self.O
        for (x, y, z), b in list(self.F.items()):
            name = b.split(":")[1]
            if "[" in name:
                continue
            if name.endswith("_wall"):
                kind = "wall"
            elif name.endswith("fence") or name == "iron_bars" or name.endswith("_pane"):
                kind = "bars"
            else:
                continue
            hit = {}
            for face, (dx, dz) in dirs.items():
                p = (x + dx, y, z + dz)
                nb = self.F.get(p)
                if nb is None:
                    h = self.inb(*p) and bool(self.rock[p[0] - ox, p[1] - oy, p[2] - oz])
                else:
                    nn = nb.split(":")[1].split("[")[0]
                    if kind == "wall":
                        h = nn.endswith("_wall") or _full(nb)
                    else:
                        h = nn.endswith("fence") or nn == "iron_bars" or nn.endswith("_pane") or _full(nb)
                hit[face] = h
            if kind == "wall":
                s = {f: ("low" if v else "none") for f, v in hit.items()}
                straight = (s["east"] == s["west"] == "low" and s["north"] == s["south"] == "none") or \
                           (s["north"] == s["south"] == "low" and s["east"] == s["west"] == "none")
                self.F[(x, y, z)] = (f"{b}[east={s['east']},north={s['north']},south={s['south']},"
                                     f"up={'false' if straight else 'true'},west={s['west']}]")
            else:
                t = {f: ("true" if v else "false") for f, v in hit.items()}
                self.F[(x, y, z)] = f"{b}[east={t['east']},north={t['north']},south={t['south']},west={t['west']}]"

    # ------------------------------------------------------------ walkability
    def _arrays(self):
        S = self.rock.copy()
        FLR = self.rock.copy()
        ox, oy, oz = self.O
        for (x, y, z), b in self.F.items():
            name = b.split(":")[1].split("[")[0]
            s = _solid(name)
            S[x - ox, y - oy, z - oz] = s
            FLR[x - ox, y - oy, z - oz] = s and not _nofloor(name)
        return S, FLR

    def finish(self, rock_block, ores=(), ore_chance=0.04, spots=None, tunnel_ends=()):
        ox, oy, oz = self.O
        NX, NY, NZ = self.N
        X1, Y1, Z1 = self.E
        self._connect()
        S, FLR = self._arrays()
        Sb = S.reshape(-1).tobytes()
        Fb = FLR.reshape(-1).tobytes()

        def ix(x, y, z):
            return ((x - ox) * NY + (y - oy)) * NZ + (z - oz)

        def valid(x, y, z):
            if not (ox <= x < X1 and oy + 1 <= y < Y1 - 2 and oz <= z < Z1):
                return False
            i = ix(x, y, z)
            return (not Sb[i]) and (not Sb[i + NZ]) and bool(Fb[i - NZ])

        start = self.arrival
        seen = {start}
        q = deque([start])
        while q:
            x, y, z = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not (ox <= nx < X1 and oz <= nz < Z1):
                    continue
                for dy in (0, 1, -1, -2, -3):
                    ny = y + dy
                    if (nx, ny, nz) in seen or not valid(nx, ny, nz):
                        continue
                    if dy == 1 and Sb[ix(x, y + 2, z)]:
                        continue
                    if dy < 0 and (Sb[ix(nx, y, nz)] or Sb[ix(nx, y + 1, nz)]):
                        continue
                    if dy < -1 and any(Sb[ix(nx, y + h, nz)] for h in range(dy, 2)):
                        continue
                    seen.add((nx, ny, nz))
                    q.append((nx, ny, nz))
                    break
        reach = seen
        for name, p in tunnel_ends:
            self.must[name] = p
        bad = [n for n, p in self.must.items() if tuple(p) not in reach]
        print("reachable standing spots:", len(reach))
        if bad:
            raise SystemExit("NOT REACHABLE: " + ", ".join(f"{n} {self.must[n]}" for n in bad))

        # ---- lights
        lights = []
        for (x, y, z), b in self.F.items():
            name = b.split(":")[1].split("[")[0]
            if name in _LIGHTS or (name in ("furnace", "blast_furnace", "redstone_lamp", "copper_bulb") and "lit=true" in b):
                lights.append((x, y, z))
        buckets = {}

        def bkey(x, y, z):
            return (x // 6, y // 5, z // 6)

        for p in lights:
            buckets.setdefault(bkey(*p), []).append(p)

        def lit(x, y, z, r=8):
            kx, ky, kz = bkey(x, y, z)
            for a in range(-2, 3):
                for b in range(-2, 3):
                    for c in range(-2, 3):
                        for (lx, ly, lz) in buckets.get((kx + a, ky + b, kz + c), ()):
                            if abs(lx - x) + abs(ly - y) + abs(lz - z) <= r:
                                return True
            return False

        glow = [("glowstone", 3), ("shroomlight", 3), ("sea_lantern", 2)]
        n_embed = n_invis = 0
        for (x, y, z) in sorted(reach, key=lambda p: (p[1], p[0], p[2])):
            if lit(x, y + 1, z):
                continue
            placed = None
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                wx, wz = x + dx, z + dz
                if ox < wx < X1 - 1 and oz < wz < Z1 - 1 and self.inb(wx, y + 1, wz) and self.rock[wx - ox, y + 1 - oy, wz - oz] \
                        and (wx, y + 1, wz) not in self.F:
                    placed = (wx, y + 1, wz)
                    break
            if placed:
                self.put(*placed, self.pick(*placed, glow, 8))
                n_embed += 1
            elif (x, y + 2, z) not in self.F and not Sb[ix(x, y + 2, z)]:
                placed = (x, y + 2, z)
                self.put(*placed, "light[level=14]")
                n_invis += 1
            else:
                continue
            lights.append(placed)
            buckets.setdefault(bkey(*placed), []).append(placed)
        print(f"lights: {len(lights)} sources, {n_embed} wall-embedded, {n_invis} invisible")

        # ---- the rock: only what is within 2 blocks of air
        air = ~self.rock
        for _ in range(2):
            p = np.pad(air, 1, constant_values=True)
            air = air | p[:-2, 1:-1, 1:-1] | p[2:, 1:-1, 1:-1] | p[1:-1, :-2, 1:-1] | p[1:-1, 2:, 1:-1] | p[1:-1, 1:-1, :-2] | p[1:-1, 1:-1, 2:]
        emit = self.rock & air
        pr = np.pad(self.rock, 1, constant_values=True)
        exposed = self.rock & ~(pr[:-2, 1:-1, 1:-1] & pr[2:, 1:-1, 1:-1] & pr[1:-1, :-2, 1:-1] & pr[1:-1, 2:, 1:-1]
                                & pr[1:-1, 1:-1, :-2] & pr[1:-1, 1:-1, 2:])
        n_ore = 0
        for (i, j, k) in np.argwhere(emit):
            x, y, z = int(i) + ox, int(j) + oy, int(k) + oz
            if (x, y, z) in self.F:
                continue
            b = rock_block(x, y, z)
            if ores and exposed[i, j, k] and hash01(x, y, z, 77) < ore_chance:
                b = self.pick(x, y, z, ores, 78)
                n_ore += 1
            self.area.set(x, y, z, b)
        for (x, y, z), b in self.F.items():
            self.area.set(x, y, z, b)
        for (x, y, z) in self.fairy:
            self.area.fairy_soul(x, y, z)
        for name, (x, y, z, yaw, note) in self.npcs.items():
            self.area.npc(name, x, y, z, yaw, note)
        self.area.arrival(*self.arrival, 0.0)
        if spots:
            self.area.spots.update(spots)
        self.area.save()
        print(f"ore blocks in the walls: {n_ore}, built blocks: {len(self.F)}")
