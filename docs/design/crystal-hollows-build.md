# Crystal Hollows: structures brief

The Crystal Hollows are a **generated** cave world (`skyrpg_skyblock:crystal_hollows`, from
`tools/skyblock/gen_hollows.py`): 512 x 512 blocks around 0, 0 (x and z from -256 to 256), y 0 to 192, Hard Stone
(plain `minecraft:stone`) caverns, a lava sea up to y 24, stained-glass gemstone veins. Quadrants: Jungle north-west
(x < 0, z < 0; Amethyst = purple glass, moss floors, lush vegetation), Mithril Deposits north-east (Jade = lime glass,
Mithril, Titanium), Goblin Holdout south-west (Amber = orange glass, coarse dirt floors), Precursor Remnants south-east
(Sapphire = light blue glass, cracked stone bricks), the Magma Fields everywhere below y 64 (Topaz = yellow glass,
blackstone floors, basalt, magma); the Crystal Nucleus in the middle (|x| < 40, |z| < 40, above y 64). Research:
`docs/design/research/crystal-hollows.md`.

The handmade parts are area scripts (the kit `tools/builds/hub_areas/kit.py`; examples `the_end.py`, `mines_center.py`)
with `dimension="skyrpg_skyblock:crystal_hollows"`, `island="Crystal Hollows"`, `shell=2`. The game clears each area's box
and places it over the generated caves, so: **fill the whole box with rock first** (`minecraft:stone` mostly, some
andesite/tuff for texture; it's Hard Stone players can mine), then carve the structure inside it, and leave 2-4
tunnels (3 wide, 3 tall) from the structure out to the box's faces so players can reach it from the caves (the caves
outside may or may not meet the tunnel: players dig the rest). Lighting: the dimension has no sky light, so light every
room (lanterns, glowstone, shroomlight, sea lanterns, lava).

**Never use** as decoration: `stone` in structures' walls (that's mineable Hard Stone - use stone bricks, polished
andesite, deepslate tiles, etc.), stained glass or panes of red/purple/lime/orange/light blue/yellow/magenta (those are
gemstones), gray wool, prismarine, light blue wool, polished diorite (Mithril/Titanium). Use tinted glass, plain glass,
white/black/gray/brown/blue/cyan/green/pink stained glass for windows.

Walkable inside (3+ wide paths, 2+ headroom, stairs, no 2-block steps), Hypixel-looking, and lit.

## The areas (world coordinates; boxes are [min, max))

| Area id | Box x | Box z | Box y | What |
| --- | --- | --- | --- | --- |
| `hollows_nucleus` | -40 .. 40 | -40 .. 40 | 64 .. 186 | The Crystal Nucleus |
| `hollows_jungle_temple` | -170 .. -110 | -170 .. -110 | 90 .. 140 | The Jungle Temple (Jungle) |
| `hollows_khazad_dum` | -40 .. 40 | 120 .. 200 | 20 .. 64 | Khazad-dûm, Bal's lair (Magma Fields, under the Goblin/Precursor line) |
| `hollows_mines_of_divan` | 110 .. 180 | -180 .. -110 | 90 .. 140 | The Mines of Divan (Mithril Deposits) |
| `hollows_goblin_den` | -180 .. -110 | 110 .. 180 | 90 .. 140 | The Goblin Queen's Den and King Yolkar's tower (Goblin Holdout) |
| `hollows_precursor_city` | 100 .. 190 | 100 .. 190 | 80 .. 150 | The Lost Precursor City (Precursor Remnants) |

### The Crystal Nucleus (`hollows_nucleus`)
Where players arrive. A great open spherical cavern in the middle of the Hollows: a central platform at y ~110 with
**five crystal pedestals** in a ring (record each pedestal's top block as `area.spots["crystals"] = {"jade": [x,y,z],
"amber": [...], "topaz": [...], "sapphire": [...], "amethyst": [...]}`) around a big crystal sculpture (white/pink/
cyan stained glass, amethyst blocks, end rods), bridges out to the four directions ending at the box faces as tunnels
(one per quadrant: north-west, north-east, south-west, south-east diagonals or the four sides), and a way down to the
Magma Fields (a long spiral stair down to the box floor at y 64 opening out). `area.arrival(...)` and
`area.warp("Crystal Hollows", ...)` on the platform. A launch pad (3x3 slime blocks, sign "Dwarven Mines") back to the
Dwarven Mines: `area.pad("the Dwarven Mines", ..., destination=("skyrpg_skyblock:dwarven_mines", (100.5, 201.0, -99.5), 90.0))`.
NPCs: **Emissary Sisko** (commissions), **Gemma** (gemstones), **Geonathan Greatforge**. Zones are already defined by the
generator (don't add zones in this script). 2 Fairy Souls hidden in the Nucleus (reachable without breaking anything).

### The Jungle Temple (`hollows_jungle_temple`)
A mossy stone-brick and chiseled temple with vines and jungle leaves inside a big cavern: an entrance hall with a door
(the **Kalhuiki Door Guardian** NPC stands by it), a few rooms with simple parkour (wide, safe - nothing deadly) and an
inner sanctum with a gold altar (record it as `area.spots["altar"]`). NPC **Odawa** outside the temple. 1 Fairy Soul.

### Khazad-dûm (`hollows_khazad_dum`)
A vast dark dwarven hall in the Magma Fields: basalt and blackstone pillars, lava channels, a huge central arena (record
its centre as `area.spots["arena"]`) where Bal (a boss) will appear, a broken bridge, deepslate tile floors. 1 Fairy Soul.

### The Mines of Divan (`hollows_mines_of_divan`)
An ancient dwarven mine complex: gold and deepslate halls, rails, four **Keeper** statues (NPCs: **Keeper of Diamond**,
**Keeper of Gold**, **Keeper of Emerald**, **Keeper of Lapis**), a central chamber with a pedestal (record as
`area.spots["jade_pedestal"]`), and a **Mines of Divan Forger** NPC at an anvil. Gold ore, emerald ore, diamond ore and redstone ore veins in the walls (scenery). 1 Fairy Soul.

### The Goblin Queen's Den (`hollows_goblin_den`)
A goblin fortress: crude spruce/dark oak walls, banners, a tall tower (**King Yolkar** NPC at its top), and the Queen's
den deeper inside: a cavern of hay, gold and loot with a throne (record as `area.spots["queen"]`). 1 Fairy Soul.

### The Lost Precursor City (`hollows_precursor_city`)
Ruins of an ancient robot city: tall cracked stone-brick and polished andesite buildings, copper and iron details,
broken machinery (pistons, observers, iron bars, lightning rods), streets, a central tower with **Professor Robot** (NPC)
and six empty part slots (record as `area.spots["robot"]`). 1 Fairy Soul.

## Rules for the helpers

- Python only: write `tools/builds/hub_areas/<area id>.py`, run it with `python`, fix what it reports. Don't run gradle,
  the game, `hotswap.py`, the minecraft MCP tools or staging; don't touch other areas' files.
- Check block ids exist in 26.x (the kit checks on save; `minecraft:chain` is `minecraft:iron_chain`).
- Don't read secret files (`.env`, `tools/.discord_webhook`, `server.properties`, `accounts.json`).
- Report: file, block count, arrival/warp (Nucleus), every NPC spot, the recorded spots, fairy souls, the tunnels you made,
  and anything you weren't sure about.
