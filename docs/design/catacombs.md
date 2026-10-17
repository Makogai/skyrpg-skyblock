# The Catacombs (Phase 3)

How Hypixel's Dungeons are built here. Hypixel's rules and numbers are in `research/dungeons.md`; this is our design.

## Pieces

| Piece | Where | What |
| --- | --- | --- |
| Dungeon Hub | public island `skyrpg_skyblock:dungeon_hub` (a hub area build) | Mort (starts runs), Ophelia (shop), Malik, the Catacombs gate, essence shops |
| Catacombs world | `skyrpg_skyblock:catacombs`, empty, dark, fixed night | every run is built here in its own slot |
| Room library | `skyblock/.../structure/catacombs/rooms/*.nbt` + markers | handmade rooms (Python, `tools/builds/catacombs/`), placed with rotation |
| Run | `dungeon/Run.java` | one party's run: layout, rooms, doors, mobs, score, timer, deaths |
| Layout | `dungeon/Layout.java` | the room grid of a floor: shapes, types, doors |
| Classes | `dungeon/Classes.java` | Healer, Mage, Berserk, Archer, Tank: passives, orbs, ultimates, class XP |
| Bosses | `dungeon/boss/*.java` | one per floor, scripted phases |
| Rewards | `dungeon/Rewards.java` + `RewardsMenu`/screen | score, the chests, Catacombs and class XP |
| Catacombs skill | `skyrpg_skills:catacombs` (a skill file) | NEU's XP table, the dungeon stat bonus |

## The world and slots

One dimension for every run, like the Glacite Mineshafts: slot `i` is at x = i × 2048, y 70, z 0. A floor is at most
6 × 6 rooms of 32 blocks (192 × 192) plus the boss room beside it, so a 2048 slot never touches the next. A slot is
cleared (blocks and entities) when its run ends or is left empty for a minute, and on server stop everyone inside is
sent to the Dungeon Hub and every slot cleared (the Mineshafts' pattern).

## The grid and the rooms

Hypixel's map: a square grid of cells, 32 blocks each (31-wide rooms and a 1-block gap where doors sit). Floor sizes
from the research (Entrance F1 4 × 4 ... F7 6 × 6). Room shapes: 1×1, 1×2, 1×3, 1×4, 2×2 and L (3 cells). Room types:
Entrance (green), normal (brown), Puzzle (purple), Trap (orange), Miniboss (yellow), Fairy (pink), Blood (red).

Generation (`Layout`):

1. Place the Entrance and the Blood room far apart (opposite sides), then the Fairy room, the puzzles (by floor) and
   the trap (F3+) and miniboss rooms (F4+) as 1×1 cells.
2. Fill the rest with normal rooms: random shapes that fit, largest first.
3. Connect: a random spanning tree over neighbouring rooms, plus a few extra links for loops. Each link is a door.
4. Doors on the path from the Entrance to the Blood room through... : a few become Wither doors (need a Wither Key,
   found on a marked mob in a room); the door into the Blood room is the Blood door (needs the Blood Key).

Every room template is drawn for one orientation; `Layout` rotates it (0/90/180/270) to fit, and its markers rotate
with it. A template's size is its shape × 32 - 1 (a 1×1 room is 31 × 31), floor at y 0, walls up to y 32.

## Markers in a room

Invisible `minecraft:marker` entities in the template (dungeon_kit's `Piece.marker`), read when the room is placed:

| Marker | Data | What happens |
| --- | --- | --- |
| `mob` | `type` (zombie_commander...), `star` (bool) | a dungeon mob spawns here when the room is first entered; star mobs count for the room |
| `secret_chest`, `secret_item`, `secret_bat`, `secret_essence` | `loot` | a secret: counts for score; chest/item/essence give loot |
| `lever`, `wall` | `group` | levers that open a secret wall |
| `crypt` | | a crypt: break it for bonus score (a zombie comes out) |
| `puzzle` | `kind` + kind fields | the puzzle's pieces |
| `trap` | `kind` | trap parts |
| `miniboss` | `type` | a miniboss (Shadow Assassin, Angry Archaeologist...) |
| `blessing` | | where a blessing can be (on a puzzle's or miniboss's completion) |
| `fairy` | | the revive spot in the Fairy room |
| `watcher` | | the Watcher's spot in the Blood room |
| `entrance` | | where the party starts |

A room is **cleared** when every star mob in it is dead (or its puzzle solved). The map shows cleared rooms with a
white tick, all secrets found with a green one.

## A run

1. In the Dungeon Hub, the party leader talks to Mort (or walks into the gate) and picks a floor; each member picks a
   class (Mort's screen). Everyone must meet the floor's Catacombs requirement.
2. The run is built in a free slot; the party arrives in the Entrance. A short countdown, then the timer starts.
3. Clear rooms, find keys and open doors, solve puzzles, open the Blood room: the Watcher spawns the blood mobs; when
   they're all dead the Watcher opens the portal to the boss.
4. Beat the boss: the score is worked out and shown; the reward room has the chests (`RewardsMenu`).
5. Leaving (or `/warp dungeon_hub`) takes you back to the Dungeon Hub.

Death: you become a ghost (spectator-like, can fly through the run) until revived by a Healer, a Revive Stone, the
Fairy room's fairy or the boss room. Each death costs score.

## Score

Skill + Explore + Speed + Bonus, each out of 100 (Hypixel's formulas, research section 3). S+ at 300.

## Classes

Picked before the run (Mort's screen), one per player (duplicates allowed). Passives and abilities from the research;
the ultimate is the drop key (`key.drop`, Hypixel's) with a cooldown. Class XP goes to the class played.

## What comes first (Phase 3 part 1)

Floor 1 end to end: the Dungeon Hub, Mort, the Catacombs skill, the instance world, the layout generator, a first
library of rooms (Entrance, Blood, Fairy, ~10 normal rooms in the shapes), star mobs, doors and keys, the Watcher and
blood mobs, Bonzo, score, chests. Then: puzzles, traps, minibosses, secrets, classes' abilities, F2-F7 bosses, Master
Mode, dungeon item stats, stars and essence.
