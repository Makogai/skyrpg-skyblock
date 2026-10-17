"""Multi-cell Catacombs rooms: three 'two', two 'three', one 'four', two 'square', two 'l'.

    python tools\\builds\\catacombs\\rooms_multi.py        (from the repo root)

Every room is seeded by its path, so reruns give the same room. Besides room_kit's own check, DR.verify() walks the
rooms: every door spot reaches every other, every star mob can reach (and get back to) the doors, secrets are reachable,
falling blocks have support and water is only still source blocks in enclosed basins.
"""
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import random  # noqa: E402
from room_kit import Room, FLOOR, HEIGHT  # noqa: E402

AIR = "minecraft:air"
SB = "minecraft:stone_bricks"
CRK = "minecraft:cracked_stone_bricks"
MOSS = "minecraft:mossy_stone_bricks"
CHI = "minecraft:chiseled_stone_bricks"
DB = "minecraft:deepslate_bricks"
DBC = "minecraft:cracked_deepslate_bricks"
DT = "minecraft:deepslate_tiles"
DTC = "minecraft:cracked_deepslate_tiles"
PB = "minecraft:polished_blackstone_bricks"
PBC = "minecraft:cracked_polished_blackstone_bricks"
AND = "minecraft:andesite"
PAND = "minecraft:polished_andesite"
STONE = "minecraft:stone"
COB = "minecraft:cobblestone"
MCOB = "minecraft:mossy_cobblestone"
GRAVEL = "minecraft:gravel"
CDIRT = "minecraft:coarse_dirt"
BARS = "minecraft:iron_bars"
CHAIN = "minecraft:iron_chain[axis=y]"
LAN = "minecraft:lantern[hanging=true]"
SLAN = "minecraft:soul_lantern[hanging=true]"
GLOW = "minecraft:shroomlight"
WEB = "minecraft:cobweb"
WATER = "minecraft:water"
BONE = "minecraft:bone_block"
LOG = "minecraft:dark_oak_log[axis=y]"
MOSSB = "minecraft:moss_block"
WALK = {"minecraft:iron_bars", "minecraft:dark_oak_fence"}
WEATHER = {SB: [(CRK, .14), (MOSS, .10)], DB: [(DBC, .14)], DT: [(DTC, .12)], PB: [(PBC, .15)],
           AND: [(STONE, .18), (PAND, .07)]}
FLOORS = {SB, CRK, MOSS, DB, DBC, DT, DTC, STONE, AND, PB, COB}

PASS_IDS = {"minecraft:air", "minecraft:cave_air", "minecraft:rail", "minecraft:short_grass", "minecraft:fern",
            "minecraft:dead_bush", "minecraft:moss_carpet"}
_KIND = {}


def kind(i):
    """0 = walk through, 1 = solid (stand on it), 2 = blocks you but can't be stood on (water, web, candle...)."""
    k = _KIND.get(i)
    if k is None:
        if i in PASS_IDS or i.endswith("_carpet"):
            k = 0
        elif i in (WATER, "minecraft:lava", WEB, "minecraft:lantern", "minecraft:soul_lantern", "minecraft:iron_chain") \
                or i.endswith("candle") or i.endswith("_skull") or i.endswith("_head"):
            k = 2
        else:
            k = 1
        _KIND[i] = k
    return k


FACING = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
RED = "minecraft:red_carpet"
GRAY = "minecraft:gray_carpet"


class DR(Room):
    def __init__(self, path, shape, ceil=18, wall=SB, floor=SB, top=DB):
        super().__init__(path, shape)
        self.ceil = ceil
        self.rng = random.Random(path)
        self.shell(wall=wall, floor=floor, ceiling=top)
        w, h, d = self.size
        self.inner = [(x, z) for x in range(w) for z in range(d) if self.in_room(x, z) and not self.boundary(x, z)]
        self.edge = [(x, z) for x in range(w) for z in range(d) if self.boundary(x, z)]
        for x, z in self.inner:
            for y in range(ceil, HEIGHT - 1):
                self.set(x, y, z, top)
        self.cls = None

    # -- queries -------------------------------------------------------------------------------------------------------
    def bid(self, x, y, z):
        if not self.in_room(x, z) or not 0 <= y < HEIGHT:
            return "minecraft:stone"
        c = self.cells.get((x, y, z))
        return AIR if c is None else c[0]["id"]

    def is_air(self, x, y, z):
        return self.bid(x, y, z) == AIR

    # -- decoration helpers ----------------------------------------------------------------------------------------------
    def hang(self, x, z, drop=2, soul=None):
        top = self.ceil - 1
        if any(self.bid(x, y, z) != AIR for y in range(top - drop, top + 1)):
            return False
        if soul is None:
            soul = self.rng.random() < .3
        for y in range(top - drop + 1, top + 1):
            self.set(x, y, z, CHAIN)
        self.set(x, top - drop, z, SLAN if soul else LAN)
        return True

    def lights(self, sx, sz, ox, oz, drop=2, rect=None):
        w, h, d = self.size
        for x in range(ox, w, sx):
            for z in range(oz, d, sz):
                if not self.in_room(x, z) or self.boundary(x, z):
                    continue
                if rect and not (rect[0] <= x <= rect[2] and rect[1] <= z <= rect[3]):
                    continue
                self.hang(x, z, drop)

    def glow(self, step=7):
        y = max(13, self.ceil - 4)
        for x, z in self.edge:
            zwall = not self.in_room(x, z - 1) or not self.in_room(x, z + 1)
            key = x % 32 if zwall else z % 32
            if key % step == 3 and abs(key - 15) > 1 and key != 31:
                self.set(x, y, z, GLOW)

    def webs(self, n):
        top = self.ceil - 1
        cands = []
        for x, z in self.inner:
            if self.is_air(x, top, z):
                solid = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if kind(self.bid(x + dx, top, z + dz)) == 1)
                if solid >= 2:
                    cands.append((x, z))
        self.rng.shuffle(cands)
        for x, z in cands[:n]:
            self.set(x, top, z, WEB)

    def patch(self, block, count, radius, rect, y=FLOOR):
        x1, z1, x2, z2 = rect
        for _ in range(count):
            cx, cz = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            for x in range(cx - radius, cx + radius + 1):
                for z in range(cz - radius, cz + radius + 1):
                    if (x - cx) ** 2 + (z - cz) ** 2 > radius * radius or self.rng.random() < .3:
                        continue
                    if self.in_room(x, z) and self.bid(x, y, z) in FLOORS and self.bid(x, y + 1, z) == AIR \
                            and kind(self.bid(x, y - 1, z)) == 1:
                        self.set(x, y, z, block)

    def rubble(self, n, rect, y=FLOOR + 1):
        x1, z1, x2, z2 = rect
        for _ in range(n):
            x, z = self.rng.randint(x1, x2), self.rng.randint(z1, z2)
            if self.in_room(x, z) and self.bid(x, y, z) == AIR and kind(self.bid(x, y - 1, z)) == 1:
                self.set(x, y, z, self.rng.choice([COB, MCOB, COB, GRAVEL, "minecraft:cobblestone_slab[type=bottom]"]))

    def protect(self):
        for cells, (ix, iz) in self.door_spots():
            for x, z in cells:
                for y in range(FLOOR + 1, FLOOR + 5):
                    if self.bid(x, y, z) == AIR:
                        self.set(x, y, z, SB)
                for s in (1, 2, 3):
                    fx, fz = x + ix * s, z + iz * s
                    if kind(self.bid(fx, FLOOR, fz)) != 1:
                        self.set(fx, FLOOR, fz, SB)
                    for y in range(FLOOR + 1, FLOOR + 5):
                        self.set(fx, y, fz, AIR)

    def dress(self, extra=None, webs=10):
        ch = dict(WEATHER)
        ch.update(extra or {})
        self.weather("w", ch)
        self.glow()
        self.webs(webs)
        self.protect()

    # -- the walker ----------------------------------------------------------------------------------------------------
    def prep(self):
        w, h, d = self.size
        cls = bytearray([1]) * (w * h * d)
        for x in range(w):
            for z in range(d):
                if self.in_room(x, z):
                    for y in range(h):
                        cls[(x * h + y) * d + z] = 0
        for (x, y, z), (entry, _) in self.cells.items():
            cls[(x * h + y) * d + z] = kind(entry["id"])
        self.cls = cls

    def door_nodes(self):
        out = []
        for cells, (ix, iz) in self.door_spots():
            cx, cz = cells[1]
            out.append((cx + ix * 2, FLOOR + 1, cz + iz * 2))
        return out

    def stand_ok(self, x, y, z):
        w, h, d = self.size
        c = self.cls
        return c[(x * h + y) * d + z] == 0 and c[(x * h + y + 1) * d + z] == 0 and c[(x * h + y - 1) * d + z] == 1

    def reach(self, start):
        w, h, d = self.size
        c = self.cls
        seen = {start}
        dq = deque([start])
        while dq:
            x, y, z = dq.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if not (0 <= nx < w and 0 <= nz < d):
                    continue
                base = nx * h * d + nz
                ny = None
                if c[base + y * d] == 0 and c[base + (y + 1) * d] == 0:
                    if c[base + (y - 1) * d] == 1:
                        ny = y
                    else:
                        yy = y - 1
                        while yy >= 1 and c[base + yy * d] == 0:
                            yy -= 1
                        if c[base + yy * d] == 1 and y - (yy + 1) <= 8:
                            ny = yy + 1
                elif c[base + y * d] == 1 and c[base + (y + 1) * d] == 0 and c[base + (y + 2) * d] == 0 \
                        and c[(x * h + y + 2) * d + z] == 0:
                    ny = y + 1
                if ny is not None and (nx, ny, nz) not in seen:
                    seen.add((nx, ny, nz))
                    dq.append((nx, ny, nz))
        return seen

    def scatter(self, specs, spacing=5, margin=6):
        """specs: (x1, z1, x2, z2, [roles], ymin, ymax); a role may be (role, star)."""
        self.protect()
        self.prep()
        w, h, d = self.size
        nodes = self.door_nodes()
        F = self.reach(nodes[0])
        placed = []
        for spec in specs:
            x1, z1, x2, z2, roles = spec[:5]
            ymin = spec[5] if len(spec) > 5 else 0
            ymax = spec[6] if len(spec) > 6 else 99
            cands = sorted(p for p in F if x1 <= p[0] <= x2 and z1 <= p[2] <= z2 and ymin <= p[1] <= ymax
                           and self.cls[(p[0] * h + p[1] + 2) * d + p[2]] == 0
                           and self.bid(p[0], p[1] - 1, p[2]) not in WALK
                           and all(abs(p[0] - n[0]) + abs(p[2] - n[2]) > margin for n in nodes))
            self.rng.shuffle(cands)
            for role in roles:
                star = True
                if isinstance(role, tuple):
                    role, star = role
                pick = None
                for sp in (spacing, 3, 1):
                    pick = next((p for p in cands if all(max(abs(p[0] - q[0]), abs(p[2] - q[2])) >= sp or abs(p[1] - q[1]) >= 4
                                                         for q in placed)), None)
                    if pick:
                        break
                if pick is None:
                    raise SystemExit(f"{self.path}: no spot for a {role} in {spec[:4]}")
                placed.append(pick)
                self.mob(pick[0], pick[1], pick[2], role, star=star)

    def verify(self):
        self.prep()
        nodes = self.door_nodes()
        for n in nodes:
            if not self.stand_ok(*n):
                raise SystemExit(f"{self.path}: nobody can stand in front of the door at {n}")
        F = None
        for i, n in enumerate(nodes):
            R = self.reach(n)
            if i == 0:
                F = R
            missing = [m for m in nodes if m not in R]
            if missing:
                raise SystemExit(f"{self.path}: door {n} can't reach doors {missing}")
        for kind_, x, y, z, data in self.markers:
            if kind_ == "mob":
                if (x, y, z) not in F:
                    raise SystemExit(f"{self.path}: {data['role']} at {(x, y, z)} can't be reached on foot")
                if nodes[0] not in self.reach((x, y, z)):
                    raise SystemExit(f"{self.path}: from the {data['role']} at {(x, y, z)} there is no way back to the doors")
            elif kind_ == "secret_chest":
                if not any((x + dx, y, z + dz) in F for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    raise SystemExit(f"{self.path}: chest at {(x, y, z)} can't be reached")
                st = self.cells[(x, y, z)][0]["properties"]["facing"]
                fx, fz = next(k for k, v in FACING.items() if v == st)
                if kind(self.bid(x + fx, y, z + fz)) != 0:
                    raise SystemExit(f"{self.path}: chest at {(x, y, z)} is blocked in front")
            elif kind_ == "secret_item":
                if not any((x + dx, y, z + dz) in F for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1))):
                    raise SystemExit(f"{self.path}: item at {(x, y, z)} can't be reached")
        for (x, y, z), (entry, _) in self.cells.items():
            i = entry["id"]
            if i in ("minecraft:gravel", "minecraft:sand", "minecraft:red_sand") or i.endswith("concrete_powder"):
                if kind(self.bid(x, y - 1, z)) != 1:
                    raise SystemExit(f"{self.path}: falling block {i} at {(x, y, z)} has no support")
            if i == WATER:
                for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                    nb = self.bid(x + dx, y + dy, z + dz)
                    if nb != WATER and kind(nb) != 1:
                        raise SystemExit(f"{self.path}: water at {(x, y, z)} would flow")
            if i in ("minecraft:lava", "minecraft:sand") and False:
                pass

    def done(self):
        self.verify()
        stars = sum(1 for m in self.markers if m[0] == "mob" and m[4].get("star"))
        secrets = sum(1 for m in self.markers if m[0].startswith("secret"))
        self.info = (stars, secrets)
        self.save()
        print(f"   -> {stars} star mobs, {secrets} secrets")


# -- shared pieces -------------------------------------------------------------------------------------------------------
def pillar(r, x, z, y0, y1, w=1, shaft=SB):
    r.fill(x, y0, z, x + w - 1, y1, z + w - 1, shaft)
    r.fill(x, y0, z, x + w - 1, y0, z + w - 1, CHI)
    r.fill(x, y1, z, x + w - 1, y1, z + w - 1, CHI)


def skull(r, x, y, z, wither=False):
    r.set(x, y, z, f"minecraft:{'wither_skeleton_skull' if wither else 'skeleton_skull'}[rotation={r.rng.randint(0, 15)}]")


def candle(r, x, y, z, n=3):
    r.set(x, y, z, f"minecraft:candle[candles={n},lit=true]")


def sarcophagus(r, x, z, length=3, width=2, along_x=True, decor=True):
    x2, z2 = (x + length - 1, z + width - 1) if along_x else (x + width - 1, z + length - 1)
    r.fill(x, 9, z, x2, 9, z2, SB)
    r.fill(x, 9, z, x2, 9, z2, CHI) if False else None
    if decor:
        roll = r.rng.random()
        if roll < .4:
            skull(r, x, 10, z, wither=r.rng.random() < .4)
        elif roll < .8:
            candle(r, x2, 10, z2, r.rng.randint(1, 4))
        else:
            r.set(x, 10, z, "minecraft:cobblestone_slab[type=bottom]")


def ramp(r, x, z, dx, dz, n, width, y0=FLOOR + 1, block=SB):
    """Step i (0..n-1) is a column from y0 to y0+i; the first step is at (x, z) and they go off in (dx, dz)."""
    for i in range(n):
        for k in range(width):
            px = x + dx * i + (k if dx == 0 else 0)
            pz = z + dz * i + (k if dz == 0 else 0)
            r.fill(px, y0, pz, px, y0 + i, pz, block)


def roles_mix(r, n, pool=("zombie", "skeleton", "crypt", "spider", "zombie", "skeleton", "crypt")):
    return [pool[i % len(pool)] for i in range(n)]


# =====================================================================================================================
# two: 63 x 31
# =====================================================================================================================
def tomb_gallery():
    r = DR("two/tomb_gallery", "two", ceil=19)
    for x in (5, 9, 20, 25, 35, 40, 52, 57):
        for z in (4, 25):
            sarcophagus(r, x, z, 3, 2)
    for x in (8, 20, 31, 43, 55):
        for z in (11, 19):
            pillar(r, x, z, 9, 18)
    r.fill(4, 9, 15, 28, 9, 15, GRAY)
    r.fill(34, 9, 15, 58, 9, 15, GRAY)
    r.fill(29, 9, 14, 33, 10, 16, CHI)
    r.fill(30, 10, 15, 32, 10, 15, SB)
    skull(r, 31, 11, 15, wither=True)
    candle(r, 29, 11, 14)
    candle(r, 33, 11, 16)
    # secret: a ledge on the north wall, up three steps
    r.fill(29, 9, 1, 33, 11, 2, SB)
    ramp(r, 30, 5, 0, -1, 3, 3)
    r.secret_item(31, 12, 1)
    skull(r, 29, 12, 1)
    candle(r, 33, 12, 1, 2)
    # secret: chest behind rubble in the south-east corner
    r.set(60, 9, 29, MCOB)
    r.set(60, 10, 29, COB)
    r.secret_chest(61, 9, 29, "north")
    r.secret_bat(40, 15, 15)
    r.dress(webs=14)
    r.patch(GRAVEL, 5, 3, (2, 2, 60, 28))
    r.patch(CDIRT, 4, 3, (2, 2, 60, 28))
    r.rubble(14, (2, 2, 60, 28))
    r.lights(8, 8, 4, 8, drop=2)
    r.scatter([(1, 1, 61, 29, roles_mix(r, 9))])
    r.done()


def chasm_bridge():
    r = DR("two/chasm_bridge", "two", ceil=21)
    for x in range(21, 42):
        r.fill(x, 4, 1, x, 8, 29, AIR)
    for x in range(21, 42):
        for z in range(1, 30):
            roll = r.rng.random()
            if roll < .10:
                r.set(x, 3, z, BONE)
            elif roll < .30:
                r.set(x, 3, z, GRAVEL)
            elif roll < .38:
                r.set(x, 3, z, CDIRT)
    # the bridge
    r.fill(21, 8, 13, 41, 8, 17, PB)
    r.fill(22, 9, 13, 40, 9, 13, BARS)
    r.fill(22, 9, 17, 40, 9, 17, BARS)
    for x in (24, 38):
        for z in (13, 17):
            pillar(r, x, z, 4, 7)
    for z in (13, 17):
        pillar(r, 31, z, 4, 14)
    r.fill(31, 15, 13, 31, 15, 17, CHI)
    r.set(31, 14, 15, LAN)
    r.set(31, 14, 14, SLAN) if False else None
    # the way back up from the chasm: a stair of blocks on each side
    for i in range(5):
        r.fill(21 + i, 4, 24, 21 + i, 8 - i, 29, SB)
        r.fill(41 - i, 4, 1, 41 - i, 8 - i, 6, SB)
    # lights in the chasm walls
    for z in (9, 13, 17, 21):
        r.set(20, 6, z, GLOW)
        r.set(42, 6, z, GLOW)
    # plateaus
    for x, z in ((8, 6), (8, 24), (54, 6), (54, 24), (14, 9), (14, 21), (48, 9), (48, 21)):
        pillar(r, x, z, 9, 20)
    sarcophagus(r, 4, 3, 3, 2)
    sarcophagus(r, 56, 25, 3, 2)
    r.secret_chest(31, 4, 1, "south")
    r.set(30, 4, 1, DB)
    r.set(32, 4, 1, DB)
    r.secret_item(22, 8, 27)
    r.secret_bat(31, 17, 6)
    r.dress(webs=10)
    r.patch(GRAVEL, 3, 3, (2, 2, 19, 28))
    r.patch(CDIRT, 3, 3, (43, 2, 60, 28))
    r.lights(8, 9, 4, 5, drop=2)
    r.scatter([(1, 1, 19, 29, ["zombie", "skeleton", "crypt"]),
               (43, 1, 61, 29, ["skeleton", "elite", "crypt"]),
               (21, 1, 41, 29, ["spider", "zombie", "spider", "skeleton"], 0, 4)])
    r.done()


def mine_tunnel():
    r = DR("two/mine_tunnel", "two", ceil=15, wall=AND, floor=STONE, top=DB)
    masses = [(1, 1, 12, 11), (18, 1, 44, 11), (50, 1, 61, 11), (1, 19, 12, 29), (18, 19, 44, 29), (50, 19, 61, 29)]
    for x1, z1, x2, z2 in masses:
        r.fill(x1, 9, z1, x2, 14, z2, AND)
    chambers = [(23, 5, 27, 11), (33, 6, 38, 11), (4, 5, 9, 11), (53, 5, 58, 11),
                (20, 19, 24, 25), (36, 19, 41, 25), (4, 19, 9, 25), (53, 19, 58, 25)]
    for x1, z1, x2, z2 in chambers:
        r.fill(x1, 9, z1, x2, 12, z2, AIR)
    # a ledge in the second chamber, and chests tucked behind rubble
    ramp(r, 34, 6, 1, 0, 2, 2, block=AND)
    r.fill(36, 9, 6, 38, 10, 7, AND)
    r.secret_item(37, 11, 6)
    r.set(24, 9, 5, COB)
    r.set(26, 9, 5, MCOB)
    r.set(25, 10, 5, COB)
    r.secret_chest(25, 9, 5, "south")
    r.set(56, 9, 25, COB)
    r.set(58, 9, 25, MCOB)
    r.secret_chest(57, 9, 25, "north")
    # rough rock
    for x1, z1, x2, z2 in masses:
        for y in range(9, 15):
            for x in range(x1, x2 + 1):
                for z in range(z1, z2 + 1):
                    if r.bid(x, y, z) != AND:
                        continue
                    roll = r.rng.random()
                    if roll < .24:
                        r.set(x, y, z, STONE)
                    elif roll < .34:
                        r.set(x, y, z, COB)
                    elif roll < .37:
                        r.set(x, y, z, "minecraft:coal_ore")
                    elif roll < .39:
                        r.set(x, y, z, "minecraft:iron_ore")
                    elif roll < .43 and kind(r.bid(x, y - 1, z)) == 1:
                        r.set(x, y, z, GRAVEL)
                    elif roll < .48:
                        r.set(x, y, z, PAND)
    # the rails, only decoration, and the timber frames
    r.fill(2, 9, 15, 60, 9, 15, "minecraft:rail[shape=east_west]")
    for x in (8, 22, 30, 38, 54):
        for z in (12, 18):
            r.fill(x, 9, z, x, 13, z, LOG)
        r.fill(x, 14, 12, x, 14, 18, "minecraft:dark_oak_log[axis=z]")
        r.set(x, 13, 15, LAN)
    for x1, z1, x2, z2 in chambers:
        r.set((x1 + x2) // 2, 12, (z1 + z2) // 2, "minecraft:soul_lantern[hanging=true]")
    for _ in range(26):
        x, z = r.rng.randint(2, 60), r.rng.choice([12, 13, 17, 18, 12, 18])
        if r.bid(x, 9, z) == AIR:
            r.set(x, 9, z, r.rng.choice([COB, MCOB, GRAVEL, AND]))
    r.secret_bat(34, 13, 15)
    r.dress(extra={AND: [(STONE, .0)]}, webs=12)
    r.scatter([(1, 1, 61, 29, roles_mix(r, 9))], spacing=6)
    r.done()


# =====================================================================================================================
# three: 95 x 31
# =====================================================================================================================
def great_hall():
    r = DR("three/great_hall", "three", ceil=24)
    for x in range(8, 90, 12):
        for z in (7, 23):
            pillar(r, x, z, 9, 23, w=2)
    # the raised throne
    r.fill(38, 9, 9, 56, 9, 21, SB)
    r.fill(42, 10, 11, 52, 10, 19, DB)
    r.fill(52, 11, 13, 52, 14, 17, PB)
    r.fill(51, 11, 14, 51, 11, 14, PB)
    r.fill(51, 11, 16, 51, 11, 16, PB)
    r.set(51, 11, 15, "minecraft:polished_blackstone_brick_stairs[facing=west,half=bottom]")
    skull(r, 52, 15, 15, wither=True)
    for z in (12, 18):
        pillar(r, 43, z, 11, 13)
        r.set(43, 14, z, "minecraft:soul_lantern[hanging=false]")
    r.fill(4, 9, 15, 37, 9, 15, RED)
    r.fill(57, 9, 15, 90, 9, 15, RED)
    # secrets: a corner chest behind rubble, a balcony ledge
    for p in ((1, 9, 28), (2, 9, 28), (2, 10, 28), (1, 10, 28)):
        r.set(*p, COB if p[0] == 1 else MCOB)
    r.secret_chest(1, 9, 29, "east")
    r.fill(65, 9, 1, 75, 11, 4, SB)
    ramp(r, 63, 1, 1, 0, 2, 4)
    r.secret_item(70, 12, 2)
    skull(r, 66, 12, 1)
    candle(r, 75, 12, 3)
    r.secret_bat(47, 16, 15)
    r.dress(webs=16)
    r.patch(GRAVEL, 6, 3, (2, 2, 92, 28))
    r.patch(CDIRT, 5, 3, (2, 2, 92, 28))
    r.rubble(24, (2, 2, 92, 28))
    r.lights(9, 8, 4, 4, drop=5)
    r.scatter([(1, 1, 30, 29, roles_mix(r, 5)), (32, 1, 62, 29, roles_mix(r, 5, ("skeleton", "crypt", "zombie", "spider"))),
               (64, 1, 93, 29, roles_mix(r, 4, ("crypt", "zombie", "skeleton", "spider"))),
               (43, 1, 51, 29, ["elite"], 11, 11)], spacing=6)
    r.done()


def library():
    r = DR("three/library", "three", ceil=22, wall=SB, floor=SB)
    for x1, x2, z in ((3, 12, 11), (18, 29, 11), (33, 44, 11), (50, 60, 11), (64, 76, 11), (82, 86, 11),
                      (10, 12, 19), (18, 29, 19), (33, 44, 19), (50, 60, 19), (64, 76, 19), (82, 91, 19)):
        r.fill(x1, 9, z, x2, 11, z, "minecraft:bookshelf")
    # reading tables
    for x in (9, 22, 38, 55, 70, 86):
        r.set(x, 9, 15, "minecraft:dark_oak_fence")
        r.set(x, 10, 15, "minecraft:dark_oak_planks")
        candle(r, x, 11, 15, 2)
    # the gallery (the second floor)
    r.fill(1, 14, 1, 93, 14, 8, "minecraft:spruce_planks")
    r.fill(1, 14, 22, 93, 14, 29, "minecraft:spruce_planks")
    for x in range(10, 90, 8):
        r.fill(x, 9, 8, x, 13, 8, LOG)
        r.fill(x, 9, 22, x, 13, 22, LOG)
    ramp(r, 88, 13, 0, -1, 5, 3, block=SB)
    ramp(r, 5, 17, 0, 1, 5, 3, block=SB)
    for x in range(3, 94):
        if 88 <= x <= 90:
            continue
        r.set(x, 15, 8, "minecraft:dark_oak_fence")
        if 5 <= x <= 7:
            continue
        r.set(x, 15, 22, "minecraft:dark_oak_fence")
    for x in range(10, 90, 8):
        r.set(x, 16, 8, "minecraft:lantern[hanging=false]")
        r.set(x + 4, 16, 22, "minecraft:soul_lantern[hanging=false]")
    for x in range(3, 92):
        if x == 30:
            continue
        r.fill(x, 15, 1, x, 17, 1, "minecraft:bookshelf")
    for x in range(8, 92):
        if x in (70, 71, 72):
            continue
        r.fill(x, 15, 29, x, 17, 29, "minecraft:bookshelf")
    r.secret_chest(30, 15, 1, "south")
    r.secret_item(70, 15, 28)
    candle(r, 69, 15, 29 - 1 + 1) if False else None
    r.secret_bat(47, 19, 15)
    r.dress(webs=14)
    r.patch(CDIRT, 4, 3, (2, 9, 92, 20))
    r.rubble(10, (2, 9, 92, 20))
    r.lights(8, 8, 4, 4, drop=3)
    r.scatter([(1, 1, 30, 29, ["skeleton", "zombie", "crypt", "skeleton", "spider"]),
               (32, 1, 62, 29, ["crypt", "skeleton", "zombie", "skeleton", "spider"]),
               (64, 1, 93, 29, ["zombie", "skeleton", "crypt", "skeleton", "crypt"])], spacing=6)
    r.done()


# =====================================================================================================================
# four: 127 x 31
# =====================================================================================================================
def bone_road():
    r = DR("four/bone_road", "four", ceil=20)
    # A: entry hall
    for x in (8, 22):
        for z in (7, 23):
            pillar(r, x, z, 9, 19)
    r.fill(4, 9, 15, 28, 9, 15, GRAY)
    sarcophagus(r, 3, 3, 3, 2)
    sarcophagus(r, 25, 25, 3, 2)
    for p in ((1, 9, 28), (2, 9, 28), (2, 10, 28)):
        r.set(*p, COB)
    r.secret_chest(1, 9, 29, "east")
    # B: four bone pits between causeways
    pits = [(37, 5, 44, 12, "w"), (50, 5, 57, 12, "e"), (37, 18, 44, 25, "w"), (50, 18, 57, 25, "e")]
    for x1, z1, x2, z2, side in pits:
        r.fill(x1, 4, z1, x2, 8, z2, AIR)
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                roll = r.rng.random()
                r.set(x, 3, z, BONE if roll < .3 else (GRAVEL if roll < .4 else DB))
        rz1 = z1 if z1 < 15 else z2 - 3
        for i in range(5):
            x = x1 + i if side == "w" else x2 - i
            r.fill(x, 4, rz1, x, 8 - i, rz1 + 3, SB)
        for _ in range(7):
            x, z = r.rng.randint(x1, x2), r.rng.randint(z1, z2)
            if r.bid(x, 4, z) == AIR and r.bid(x, 3, z) == BONE:
                skull(r, x, 4, z, wither=r.rng.random() < .3)
    for z in (8, 22):
        r.set(45, 6, z, GLOW)
        r.set(49, 6, z, GLOW)
    for x in (40, 54):
        r.set(x, 6, 13, GLOW)
        r.set(x, 6, 17, GLOW)
    r.fill(45, 9, 5, 45, 9, 12, BARS)
    r.fill(49, 9, 5, 49, 9, 12, BARS)
    r.fill(45, 9, 18, 45, 9, 25, BARS)
    r.fill(49, 9, 18, 49, 9, 25, BARS)
    r.fill(37, 9, 13, 44, 9, 13, BARS)
    r.fill(37, 9, 17, 44, 9, 17, BARS)
    r.fill(50, 9, 13, 57, 9, 13, BARS)
    r.fill(50, 9, 17, 57, 9, 17, BARS)
    for x in (45, 49):
        for z in (13, 17):
            pillar(r, x, z, 9, 14)
    r.fill(45, 15, 13, 49, 15, 13, CHI)
    r.fill(45, 15, 17, 49, 15, 17, CHI)
    r.fill(45, 15, 13, 45, 15, 17, CHI)
    r.fill(49, 15, 13, 49, 15, 17, CHI)
    r.secret_chest(44, 4, 25, "west")
    r.secret_bat(47, 13, 15)
    # C: sunken courtyard
    r.fill(68, 8, 6, 90, 8, 24, AIR)
    r.fill(69, 7, 7, 89, 8, 23, AIR)
    r.fill(77, 5, 13, 81, 6, 17, WATER)
    r.fill(79, 5, 15, 79, 8, 15, SB)
    r.set(79, 9, 15, "minecraft:soul_lantern[hanging=false]")
    for tx, tz in ((72, 10), (86, 20), (72, 20), (86, 10)):
        r.fill(tx, 7, tz, tx, 11, tz, LOG)
        r.set(tx + 1, 10, tz, "minecraft:dark_oak_log[axis=x]")
        r.set(tx, 12, tz, "minecraft:dark_oak_log[axis=y]")
    for x in range(69, 90):
        for z in range(7, 24):
            if r.bid(x, 6, z) in FLOORS and r.bid(x, 7, z) == AIR:
                roll = r.rng.random()
                if roll < .28:
                    r.set(x, 6, z, MOSSB)
                    if r.rng.random() < .4:
                        r.set(x, 7, z, r.rng.choice(["minecraft:short_grass", "minecraft:fern", "minecraft:moss_carpet"]))
                elif roll < .38:
                    r.set(x, 6, z, CDIRT)
                    if r.rng.random() < .3:
                        r.set(x, 7, z, "minecraft:dead_bush")
    for x in (70, 74, 84, 88):
        pillar(r, x, 4, 9, 19)
        pillar(r, x, 26, 9, 19)
    # D: the vault
    r.fill(103, 9, 11, 119, 9, 19, SB)
    r.fill(108, 10, 14, 114, 11, 16, CHI)
    skull(r, 111, 12, 15, wither=True)
    candle(r, 108, 12, 14)
    candle(r, 114, 12, 16)
    for x in (100, 104, 116, 120):
        sarcophagus(r, x, 4, 3, 2)
    for x in (104, 116, 120):
        sarcophagus(r, x, 25, 3, 2)
    for x in (101, 121):
        for z in (11, 19):
            pillar(r, x, z, 9, 19)
    r.fill(96, 9, 24, 99, 11, 29, SB)
    ramp(r, 101, 25, -1, 0, 2, 3)
    r.secret_item(97, 12, 26)
    skull(r, 96, 12, 24)
    r.dress(webs=22)
    r.patch(GRAVEL, 4, 3, (2, 2, 30, 28))
    r.patch(CDIRT, 4, 3, (2, 2, 30, 28))
    r.patch(GRAVEL, 4, 3, (97, 2, 124, 22))
    r.rubble(20, (2, 2, 30, 28))
    r.rubble(20, (97, 2, 124, 28))
    r.lights(8, 8, 4, 4, drop=2)
    r.scatter([(1, 1, 30, 29, ["zombie", "skeleton", "crypt", "skeleton"]),
               (37, 5, 44, 12, ["spider"], 0, 4), (50, 5, 57, 12, ["zombie"], 0, 4),
               (37, 18, 44, 25, ["crypt"], 0, 4), (50, 18, 57, 25, ["spider"], 0, 4),
               (32, 1, 62, 29, ["skeleton", "crypt"], 9, 9),
               (68, 6, 90, 24, ["zombie", "skeleton", "crypt", "spider", "skeleton"], 7, 7),
               (96, 1, 125, 29, ["crypt", "zombie", ("skeleton", False)], 9, 9),
               (104, 12, 118, 18, ["elite"], 10, 10)], spacing=6)
    r.done()


# =====================================================================================================================
# square: 63 x 63
# =====================================================================================================================
def maze_crypt():
    r = DR("square/maze_crypt", "square", ceil=14, wall=SB, floor=SB)
    N = 15
    rng = r.rng
    r.fill(1, 9, 1, 61, 13, 61, SB)
    visited, stack, edges = {(0, 0)}, [(0, 0)], set()
    while stack:
        k, j = stack[-1]
        nb = [(k + a, j + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))
              if 0 <= k + a < N and 0 <= j + b < N and (k + a, j + b) not in visited]
        if not nb:
            stack.pop()
            continue
        n = rng.choice(nb)
        visited.add(n)
        edges.add(tuple(sorted(((k, j), n))))
        stack.append(n)
    tree = set(edges)
    for k in range(N):
        for j in range(N):
            for a, b in ((1, 0), (0, 1)):
                n = (k + a, j + b)
                if n[0] < N and n[1] < N and tuple(sorted(((k, j), n))) not in edges and rng.random() < .12:
                    edges.add(tuple(sorted(((k, j), n))))
    for k in range(N):
        for j in range(N):
            r.fill(2 + 4 * k, 9, 2 + 4 * j, 4 + 4 * k, 13, 4 + 4 * j, AIR)
    for (k1, j1), (k2, j2) in edges:
        if k1 != k2:
            r.fill(5 + 4 * k1, 9, 2 + 4 * j1, 5 + 4 * k1, 13, 4 + 4 * j1, AIR)
        else:
            r.fill(2 + 4 * k1, 9, 5 + 4 * j1, 4 + 4 * k1, 13, 5 + 4 * j1, AIR)
    for x in range(1, 62, 4):
        for z in range(1, 62, 4):
            if r.bid(x, 11, z) == SB and rng.random() < .35:
                r.set(x, 11, z, CHI)
    # nodes
    deg = {}
    for a, b in edges:
        for n in (a, b):
            deg[n] = deg.get(n, 0) + 1
    doors = [(3, 0), (11, 0), (3, 14), (11, 14), (0, 3), (0, 11), (14, 3), (14, 11)]
    far = [n for n in deg if deg[n] == 1 and min(abs(n[0] - d[0]) + abs(n[1] - d[1]) for d in doors) > 3]
    rng.shuffle(far)
    placed = 0
    for n in far:
        if placed >= 2:
            break
        a = [e for e in edges if n in e][0]
        o = a[0] if a[1] == n else a[1]
        dx, dz = o[0] - n[0], o[1] - n[1]
        cx, cz = 3 + 4 * n[0], 3 + 4 * n[1]
        r.secret_chest(cx - dx, 9, cz - dz, FACING[(dx, dz)])
        skull(r, cx - dx + (1 if dx == 0 else 0), 9, cz - dz + (1 if dz == 0 else 0))
        placed += 1
    if placed < 2:
        raise SystemExit("maze_crypt: not enough dead ends")
    n = far[placed]
    r.secret_item(3 + 4 * n[0], 9, 3 + 4 * n[1])
    n = far[placed + 1] if len(far) > placed + 1 else (7, 7)
    r.secret_bat(3 + 4 * n[0], 11, 3 + 4 * n[1])
    for k in range(N):
        for j in range(N):
            if (k + j) % 2 == 0:
                r.hang(3 + 4 * k, 3 + 4 * j, drop=0, soul=((k * 7 + j * 3) % 5 == 0))
    r.dress(webs=40)
    r.patch(GRAVEL, 10, 3, (2, 2, 60, 60))
    r.patch(CDIRT, 10, 3, (2, 2, 60, 60))
    r.scatter([(1, 1, 61, 61, roles_mix(r, 17, ("zombie", "skeleton", "crypt", "spider", "crypt")))], spacing=5, margin=5)
    r.done()


def cistern():
    r = DR("square/cistern", "square", ceil=18)
    pools = [(4, 4, 10, 10), (52, 4, 58, 10), (4, 52, 10, 58), (52, 52, 58, 58),
             (19, 19, 29, 29), (33, 19, 43, 29), (19, 33, 29, 43), (33, 33, 43, 43)]
    for x1, z1, x2, z2 in pools:
        r.fill(x1, 5, z1, x2, 8, z2, WATER)
        r.fill(x1, 4, z1, x2, 4, z2, MOSS)
        for x in range(x1 + 1, x2, 4):
            for z in range(z1 + 1, z2, 4):
                r.set(x, 4, z, "minecraft:sea_lantern")
    # the island in the middle
    r.fill(29, 9, 29, 33, 9, 33, SB)
    r.fill(30, 9, 30, 32, 9, 32, CHI)
    for x, z in ((29, 29), (33, 29), (29, 33), (33, 33)):
        pillar(r, x, z, 10, 17)
    r.secret_chest(31, 10, 29, "south")
    for cx, cz in ((15, 15), (47, 15), (15, 47), (47, 47)):
        for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            pillar(r, cx + dx, cz + dz, 9, 17)
    # bars as sluices along the causeways
    for z in (22, 40):
        r.fill(17, 9, z, 17, 10, z, BARS)
        r.fill(45, 9, z, 45, 10, z, BARS)
    # a ledge for the item
    r.fill(22, 9, 1, 26, 11, 5, SB)
    ramp(r, 22, 7, 0, -1, 2, 5)
    r.secret_item(24, 12, 3)
    candle(r, 22, 12, 1)
    skull(r, 26, 12, 1, wither=True)
    r.secret_bat(31, 14, 15)
    r.dress(webs=16)
    r.patch(GRAVEL, 6, 3, (12, 1, 50, 18))
    r.patch(CDIRT, 6, 3, (12, 44, 50, 61))
    r.rubble(24, (1, 1, 61, 61))
    r.lights(8, 8, 4, 4, drop=2)
    for x1, z1, x2, z2 in ():
        pass
    r.scatter([(1, 1, 61, 17, roles_mix(r, 5)), (1, 18, 20, 44, roles_mix(r, 3, ("crypt", "zombie", "skeleton"))),
               (44, 18, 61, 44, roles_mix(r, 3, ("skeleton", "spider", "zombie"))),
               (1, 45, 61, 61, roles_mix(r, 5, ("zombie", "crypt", "skeleton", "spider", "crypt"))),
               (28, 28, 34, 34, ["elite"], 10, 10), (20, 30, 42, 32, ["skeleton", "spider"], 9, 9)], spacing=5)
    r.done()


# =====================================================================================================================
# l: 63 x 63 minus the south-east quadrant
# =====================================================================================================================
def ruined_chapel():
    r = DR("l/ruined_chapel", "l", ceil=22)
    r.fill(8, 9, 6, 22, 9, 11, SB)
    r.fill(13, 10, 7, 17, 10, 7, CHI)
    candle(r, 13, 11, 7, 4)
    candle(r, 17, 11, 7, 3)
    skull(r, 15, 11, 7)
    r.fill(8, 10, 6, 8, 12, 6, MOSS)
    # the broken colonnade
    for x in (11, 19):
        for z in (16, 22, 28, 38, 44, 50, 56):
            top = r.rng.choice([12, 14, 17, 21])
            pillar(r, x, z, 9, top)
            if top < 21:
                for dx, dz in ((1, 0), (-1, 0), (0, 1)):
                    if r.bid(x + dx, 9, z + dz) == AIR:
                        r.set(x + dx, 9, z + dz, r.rng.choice([COB, MCOB]))
    # pews
    for z in (20, 23, 26, 29, 34, 37, 40, 52, 55, 58):
        for x1, x2 in ((4, 9), (21, 26)):
            for x in range(x1, x2 + 1):
                if r.rng.random() > .18:
                    r.set(x, 9, z, "minecraft:spruce_slab[type=bottom]")
    # transept: a collapse and tombs
    r.fill(36, 9, 5, 42, 9, 9, COB)
    r.fill(37, 10, 6, 41, 10, 8, MCOB)
    sarcophagus(r, 34, 22, 3, 2)
    sarcophagus(r, 40, 22, 3, 2)
    sarcophagus(r, 52, 4, 3, 2)
    # the undercroft
    r.fill(52, 4, 20, 58, 8, 26, AIR)
    for x in range(52, 59):
        for z in range(20, 27):
            r.set(x, 3, z, r.rng.choice([DB, DB, BONE, GRAVEL]))
    for i in range(5):
        r.fill(52 + i, 4, 20, 52 + i, 8 - i, 22, SB)
    r.set(51, 6, 24, GLOW)
    r.set(59, 6, 23, GLOW)
    r.secret_chest(58, 4, 26, "west")
    skull(r, 57, 4, 25)
    # a choir loft for the item
    r.fill(1, 9, 1, 4, 11, 8, SB)
    ramp(r, 1, 10, 0, -1, 2, 4)
    r.secret_item(2, 12, 2)
    candle(r, 4, 12, 1)
    r.secret_bat(15, 16, 9)
    r.dress(webs=14)
    r.patch(GRAVEL, 5, 3, (2, 2, 60, 28))
    r.patch(CDIRT, 5, 3, (2, 33, 28, 60))
    r.rubble(20, (2, 2, 60, 60))
    r.lights(8, 8, 4, 4, drop=3)
    r.scatter([(1, 1, 30, 30, ["zombie", "skeleton", "crypt", "skeleton"]),
               (1, 32, 30, 61, ["crypt", "skeleton", "zombie", "spider", "crypt"]),
               (32, 1, 61, 29, ["zombie", "skeleton", "spider", "crypt"]),
               (52, 20, 58, 26, ["elite"], 0, 4)], spacing=5)
    r.done()


def sunken_courtyard():
    r = DR("l/sunken_courtyard", "l", ceil=20)
    r.fill(6, 8, 6, 56, 8, 24, AIR)
    r.fill(7, 7, 7, 55, 8, 23, AIR)
    r.fill(27, 5, 12, 35, 6, 18, WATER)
    r.fill(31, 5, 15, 31, 7, 15, SB)
    r.set(31, 8, 15, "minecraft:soul_lantern[hanging=false]")
    for tx, tz in ((12, 10), (50, 20), (14, 21), (46, 9)):
        r.fill(tx, 7, tz, tx, 12, tz, LOG)
        r.set(tx + 1, 10, tz, "minecraft:dark_oak_log[axis=x]")
        r.set(tx - 1, 12, tz, "minecraft:dark_oak_log[axis=x]")
    for x in range(7, 56):
        for z in range(7, 24):
            if r.bid(x, 6, z) in FLOORS and r.bid(x, 7, z) == AIR:
                roll = r.rng.random()
                if roll < .28:
                    r.set(x, 6, z, MOSSB)
                    if r.rng.random() < .4:
                        r.set(x, 7, z, r.rng.choice(["minecraft:short_grass", "minecraft:fern", "minecraft:moss_carpet"]))
                elif roll < .38:
                    r.set(x, 6, z, CDIRT)
                    if r.rng.random() < .3:
                        r.set(x, 7, z, "minecraft:dead_bush")
    # colonnade on the rim
    for x in range(8, 56, 6):
        if 12 <= x % 32 <= 18 or x in (30, 31, 32):
            continue
        pillar(r, x, 4, 9, 19)
    for x in range(8, 56, 6):
        if x in (30, 31, 32) or 44 <= x <= 50 or 12 <= x <= 18:
            continue
        pillar(r, x, 26, 9, 19)
    # the cloister below: ossuary niches, a closet with a chest
    for z in (36, 40, 44, 52, 56):
        for x in (2, 28):
            r.fill(x, 9, z, x, 10, z + 1, DB)
            skull(r, x, 11, z)
    r.fill(6, 9, 52, 6, 12, 52, SB)
    r.fill(6, 9, 54, 6, 12, 58, SB)
    r.fill(1, 9, 51, 5, 12, 51, SB)
    r.secret_chest(2, 9, 55, "east")
    candle(r, 2, 9, 57, 3)
    for z in (34, 46, 58):
        pillar(r, 10, z, 9, 19)
        pillar(r, 20, z, 9, 19)
    sarcophagus(r, 13, 38, 3, 2, along_x=False)
    sarcophagus(r, 16, 54, 3, 2, along_x=False)
    # the ledge for the item
    r.fill(54, 9, 1, 60, 11, 4, SB)
    ramp(r, 52, 1, 1, 0, 2, 4)
    r.secret_item(57, 12, 2)
    skull(r, 60, 12, 4, wither=True)
    r.secret_bat(31, 12, 20)
    r.dress(webs=14)
    r.patch(GRAVEL, 4, 3, (2, 2, 60, 5))
    r.patch(CDIRT, 5, 3, (2, 33, 28, 60))
    r.patch(GRAVEL, 4, 3, (2, 33, 28, 60))
    r.rubble(10, (2, 33, 28, 60))
    r.lights(8, 8, 4, 4, drop=2)
    r.scatter([(1, 1, 61, 29, ["zombie", "skeleton", "crypt", "spider", "skeleton"], 7, 7),
               (1, 1, 61, 29, ["crypt", "skeleton"], 9, 9),
               (1, 32, 30, 61, ["zombie", "skeleton", "crypt", "spider", "crypt", ("skeleton", False)], 9, 9)], spacing=5)
    r.done()


if __name__ == "__main__":
    for build in (tomb_gallery, chasm_bridge, mine_tunnel, great_hall, library, bone_road, maze_crypt, cistern,
                  ruined_chapel, sunken_courtyard):
        build()
