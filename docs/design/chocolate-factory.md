# The Chocolate Factory and Hoppity's Hunt

Hypixel's Chocolate Factory (`/cf`) and the spring event that feeds it, with Hypixel's numbers (the official wiki's
"Chocolate Factory", "Chocolate Rabbits/List", "Hoppity's Hunt" and "Chocolate Eggs" pages, October 2026). Code:
`skyblock/.../chocolate/` (`Factory` the numbers, `ChocolateFactory` the attachment, rabbits found, milestones and
commands, `FactoryMenu` the screens, `HoppitysHunt` the eggs, `Hoppity` his shop, `Rabbits` the rabbit list and rolls).

## Rabbits

All 577 of Hypixel's rabbits (`tools/skyblock/import_rabbits.py` reads the wiki's list into
`skyrpg/chocolate/rabbits.json`): name, rarity, where they come from (eggs, milestones, the shop, duplicates,
collections, a requirement) and, for island residents, their island. A rabbit counts once you have it:

| Rarity | Chocolate a second | Multiplier | A duplicate is worth | Egg weight | Needs Factory |
| --- | --- | --- | --- | --- | --- |
| Common | +1 | +0.002x | 150 s of production | 608 | I |
| Uncommon | +2 | +0.003x | 300 s | 250 | I |
| Rare | +4 | +0.004x | 750 s | 100 | II |
| Epic | +10 | +0.005x | 1,500 s | 30 | III |
| Legendary | | +0.02x | 4,000 s | 10 | IV |
| Mythic | (its own perk) | | 8,000 s | 4 | V |
| Divine | | +0.025x | 15,000 s | 1 | VI |

A duplicate is worth 10% more for each earlier duplicate of it, up to ten (King: +30%). With a full Rabbit Barn a new
rabbit is paid like a duplicate. Every 250th egg past the last Mythic is a Mythic, every 1,250th a Divine (pity).
Island residents only hatch on their island. The Rabbit Shrine makes Uncommon and better 2% likelier a level, Napoleon
Rare and better. Mythic perks in the code: Dante +50/s, Zest Zephyr +30/s and +0.03x, Sigma +5/s per Mythic, Galaxy
+0.02x, Mu +0.7x on the Time Tower, Einstein a charge every 7 hours, King duplicates +30%, Zeta employees 1% cheaper.

## The factory

Production a second = (employees + rabbits) x (the Factory level's multiplier + rabbits + Coach Jackrabbit + the Time
Tower while it's on). It's made whether you're online or not: opening the factory adds what was made since last time.

- **Employees**: Rabbit Bro +1 a second a level, Cousin +2, Sis +3, Daddy +4, Granny +5, Uncle +6, Dog +7; each
  unlocks at level 20 of the one before. Costs: Bro's first ten levels (30 + 20 x Factory level) x 1, 2, 4, 6, 8, 9,
  9.5, 10, 10.5, 11; after that (and every other employee) (216 + 144 x level) x n² x 1.05^current level. Max level
  120 + 20 a Factory level past I, +1 a Divine rabbit.
- **Hand-Baked Chocolate**: clicking the chocolate gives its level (1 to 10; 500 x level to upgrade).
- **Rabbit Barn**: room for 20 rabbits, +2 a level (5,000 x 1.05^level). Kept when you prestige.
- **Time Tower** (Factory II): three charges (one every 8 hours); a charge gives +0.1x a level for an hour. Upgrades
  (4.5M + 0.5M x level) x 1, 2, 3, 4, 6, 8, 10, 12, 14, 16, 20, 24, 30, 40.
- **Rabbit Shrine** (Factory III) and **Coach Jackrabbit** (Factory IV, +0.01x a level): 20 levels each at Hypixel's
  prices (10M to 700M, 2.2M to 870M).
- **Prestige**: Factory II to VI need 150M, 1B, 4B, 10B and 30B made since the last prestige; multipliers 1, 1.1,
  1.25, 1.5, 2, 2.5; chocolate caps 500M to 100B. Everything but the Barn and the rabbits starts over.
- **Milestones**: 26 all-time milestones (1k to 700B) give the alphabet rabbits (Almond Amaretto to Zest Zephyr), 24
  shop milestones (2M to 800B spent) the Greek ones; 100, 500, 1,000 and 2,000 duplicates Crush to Implode; unique
  rabbit counts give Average, Mediocrity, Modest, Prudent, Favor, Saga and Extra; Factory III gives Prestige; finding 15
  egg spots on an island gives its own rabbit.
- **Stray rabbits**: while the factory's open, 20% every 30 seconds one runs through for 15 seconds (in the
  production card's place, so no card moves under the mouse): catch it for 5 to 50 seconds of production by rarity,
  double during the Hunt.
- **The Chocolate Shop**: Supreme Chocolate Bars, the Egglocator, the chocolate Rift-time items (Nibble to Prestige
  Chocolate Realm, each needing the one before), Dark Cacao Truffles, the Barn skin, the Chocolate Syringe and Hot
  Chocolate Mixins, at Hypixel's prices and Factory levels, with Hypixel's yearly stock.

The screens use the CHOCOLATE card theme: every card is its own button (the action says itself at its top right:
Bake, Hire, Promote, Upgrade, Activate, Prestige).

## Hoppity's Hunt

All spring (Early Spring 1st to Late Spring 31st, the SkyBlock calendar). Eggs come at 7am, 2pm and 9pm: Breakfast,
Lunch and Dinner one day, Brunch, Déjeuner and Supper the next (Hypixel's names). The six newest are out, each on every
island at once at one of that island's 15 spots; each player finds each egg once, and an egg holds a rabbit
(the rarity weights above, residents on their island). Eggs are only spawned near players (48 blocks). Spawns are
numbered in a row (day x 3 + meal) and step through the island's spots by a stride sharing no factor with their count,
so the six eggs out at once are always at six different spots. An egg's click box is a whole block, so the grass it
often sits in doesn't take the click.

Decided where Hypixel's spots don't fit our islands: each island's 15 spots are picked once, on open ground in its
zones, at least 8 blocks apart, and kept in the world. The Egglocator points a particle trail at the nearest egg you
haven't found.

**Hoppity** (in the Hub) sells seven rabbits a year, yours alone, for coins during the Hunt: the first two
Common, the later ones likelier rarer (Hypixel's slot odds), 250k to 5M coins by rarity.

Admins: `/sbchocolate give <amount>`, `/sbchocolate stray`, `/sbchocolate eggs` (the island's spots and where the eggs
are), `/sbevent start Hoppity's Hunt`.
