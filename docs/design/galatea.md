# Galatea & Hunting

Hypixel's 2025 Foraging update: **Galatea**, an archipelago in its own dimension (`skyrpg_skyblock:galatea`), and the
**Hunting** skill (catching critters' **shards** and syphoning them into **attributes**). Part 1 is the first island,
**Moonglade Marsh**, its trees, the Hunting skill, the Hunting Box, the lasso, the fishing net and Charm Chance.

## Getting there

- A launch pad on **The Park's Jungle Island** (east of Molbert and Juliette's fire, `park_east.py` v2) needs
  **Foraging 12**; `/warp galatea` once you've been. You land in **Tangleburg**'s square (0.5, 69, -40.5).
- Anyone below Foraging 12 who gets onto Galatea some other way is sent back to the Park pad (creative players aren't).
- Tangleburg's pad goes back to the Park.

## The island (`galatea_lib.py`, `galatea_north.py`, `galatea_south.py`)

Two hub areas meet at z = 0 (crossings at x = -60 and x = 60), 300 x 320 in all.

| Zone | What's there |
| --- | --- |
| Tangleburg | the town: Hina, the Hunting Lodge (David, the Hunting Box, Auryon, Dax), the Fusion House (Kysha), the Bank (Banker Bonsai), Jaeger's pond, Kiara's stall, Sawyer's lumber yard, Oden's library, Alan |
| Evergreen Plateau | high ground and fig groves; Chillblades |
| North / West Reaches | woods and figs; critters |
| Forest Temple | a ruined temple (its altar spot is kept for Part 2's donations) |
| Dragon's Lair | the Jade Dragon's ledge |
| Murkwater Loch | the central lake, 24 mangroves, the pier and Agatha's contest pavilion |
| Murkwater Shallows | the sunken ruin, Sea Lumies (sea pickles); Nemo |
| Murkwater Outpost | stilted huts and docks; Rosemary |
| South Reaches / South Wetlands | channels, hummocks, figs and mangroves; Bogged, Phanpyres (night) |
| Squid Cave | a flooded cave; Lumisquids |
| SwampCut Inc. | the logging camp; Amaury |

Water zone mobs (Lumisquids, in the Squid Cave's pool) now spawn in water: `Zones.waterSpot` picks a source block with
water above for any mob whose category swims, so their spawn box reaches into the water.

## Trees (`GalateaTrees`)

Hypixel's numbers. Fig toughness 10, Mangrove 50. A chop with an axe takes the log plus the extra logs your **Sweep**
buys past the toughness (Hypixel's Sweep table, up to 20 extra a chop). Order matters: a Fig's trunk before its
branches; a Mangrove's branches, then trunk, then roots. Out of order, Sweep counts half. Each log drops a Fig/Mangrove
Log (Foraging Fortune + Fig/Mangrove Fortune) and 15 / 20 Foraging XP.

**Tree Gift** (decided split): when the last log falls, everyone who cut at least 10% gets a gift (x1.5 at 20%, x2 at
33%): Forest Essence (Fig 2, Mangrove 3 a log-share), 100 / 500 Foraging XP, a chance of Tender Wood / Vinesap. Trees
regrow 60 s later (unless someone stands inside one of their blocks; under the canopy is fine), a tree left half-cut
for 2 minutes grows back whole, and everything is restored if the server stops mid-chop. Hitting something that hangs on a log (a vine) chops that log. What hangs on a tree (vines,
glow lichen, cocoa, propagules, hanging lanterns...) goes and comes back with it, and tree blocks change without
neighbour updates so nothing pops off or drops. The builds mark the tree blocks `gone_ids` (`kit.Area.hollow`), so
hollowing keeps solid ground under and around every tree: a felled trunk never opens a hole into the island.

## Hunting

- **Skill**: Hunting (`SkillLevels.HUNTING`, `skills/hunting.json`): each level +1 Hunting Fortune, +0.04% Charm Chance.
- **Stats** (`StatTypes`): Hunting Fortune (extra shards: 100 = +1 guaranteed, the rest is a chance), Charm Chance (%),
  Hunting Wisdom.
- **Shards** (`tools/skyblock/import_shards.py` -> `skyrpg/hunting/shards.json`): all 325 from NEU with rarity, family,
  alignment and level-I effect; the 32 whose effect is a plain "Grants +N Stat" are real stats per attribute level.
- **Hunting XP** per shard by rarity: 75 / 150 / 300 / 500 / 1,000.
- **The Hunting Box** (`/huntingbox`, the barrel in the Lodge, or David Hunterborough): every caught shard lands
  there. Syphon shards into an attribute (Hypixel's per-rarity levelling costs); rarer shards need Hunting 5/10/15/20
  (decided gates). Switching attributes off (a negative level is stored as off) has no button yet.

### Catching

| Way | How | Decided numbers |
| --- | --- | --- |
| Lasso (forest critters) | right-click a critter within 14 blocks: it's leashed; its stamina drains; at 20% the title says **REEL!**, right-click within 2 s | drain 0.55 + 0.3 per lasso tier a tick (+ Pull%), divided by 1 + rarity; reel early, look away 1.5 s, let it get 16 blocks off or wait too long and it slips free |
| Fishing Net (water critters) | right-click the critter you look at, range 8 / 12 / 16 / 20 by net | 55% + 12% per tier - 8% per rarity; shy critters (Lumisquid, Salamander, Bullfrog, Joydive, Sepialot, Water Snake) need a Medium net or better |
| Charm Chance | a Galatea mob you kill whose name is a shard (Bogged, Chillblade -> Chill, Phanpyre, Lumisquid, Ent) | your Charm Chance % per kill |

The lasso and the net work whichever way the click lands: on the critter up close (an entity click, which also keeps
the lasso's lead from leashing any other mob), on a block (the pier's planks, the lake floor) or in the air. For that,
every ability item's use (`Abilities`) now runs in an early event phase, before the islands' protection, which turns
away block uses and item uses of block-like items (heads, cobwebs): that fixed abilities clicked at the ground nearby
on every public island, not only on Galatea.

**Items' looks**: Hypixel's Galatea items are plain paper with its own models, and FurfSky has none, so
`import_items.py` gives them fitting vanilla items (`LOOKS`): leads for lassos, cobwebs for nets, a slime ball for
Vinesap, a stick for Tender Wood, a bundle for the Hunting Toolkit, and so on.

**Critters** (`Critters`): up to 7 around each player within 32 blocks, spawned 8-24 blocks away in forest or water
zones, named after their shard and coloured by rarity; spawn weight by rarity 60/25/10/4/1. 16 forest critters
(Bunbun, Firefox, Honeybuzz, Crow, Kiwi, Treefrog, Groundhog, Blue Jay, Boreal Owl, Heron, Bambuleaf, Mochibear,
Field Mouse, Sparrow, Murkbat, Gecko) and 14 water ones (Cod, Salmon, Tadgang, Azure, Coralot, Newt, Birries,
Salamander, Mossybit, Lumisquid, Bullfrog, Joydive, Sepialot, Water Snake), each on a fitting vanilla mob. They
can't be hurt and vanish when nobody is near.

## People (`GalateaNpcs`, action `galatea_npc`)

| NPC | Part 1 |
| --- | --- |
| Hina | Galatea's welcome and how trees work |
| David Hunterborough | menu: the Hunting Box, your Hunting level, his shop (Hunting Toolkit) |
| Auryon | your first **Abysmal Lasso**, then lasso tips |
| Jaeger | your first **Basic Fishing Net**, then net tips |
| Amaury, Nemo, Sawyer, Agatha | their NEU shops (axes and Canopy armour; snorkeling gear; the Foraging Fortune Booster; coupon prizes) |
| Banker Bonsai | the bank |
| Dax, Kysha, Kiara, Oden, Alan, Jade Dragon, Rosemary | lines (their features are Part 2) |

Screens use the new **Galatea** theme (`tools/ui/more_themes.py`): mossy fig bark with vines, leaf corners, a moonlit
vine divider, mossy-plank buttons.

## Part 2 (next)

Rosemary's Forest Essence shop and perks, Agatha's Starlyn contests and coupons, the Heart of the Forest, Kysha's
fusion, Dax's Huntraps, the Pocket Black Hole, the Forest Temple's donations, the Jade Dragon, then Torrhus.
