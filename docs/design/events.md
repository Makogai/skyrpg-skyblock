# Events: the calendar's festivals

Hypixel's yearly festivals, played out in the game (`skyblock/.../festival/`). The schedule lives in `CalendarEvents`;
this is what happens while an event runs. Mayor events (the Fishing Festival, the Mythological Ritual, the Mining
Fiesta) are in `mayors.md`.

## Event NPCs: `when`

An NPC's JSON can say when it's out: `"when": "event:Spooky Festival"` (while the event runs), `"near:Spooky Festival"`
(from a real hour before it starts to a real hour after it ends: Hypixel's Fear Mongerer), or `"perk:mythological_ritual"`
(while a Mayor's perk is in effect: Diana). Core's `Conditions` answers these; the SkyBlock mod installs the answers
(`Mayors.init`). An NPC that isn't out isn't spawned, leaves within two seconds when its time is up, and isn't on
anyone's map (the NPC map is sent again whenever the set of event NPCs changes).

The plaza's corners: Clerk Seraphine (north-west), Diana (north-east, during the Mythological Ritual), the Baker
(south-west, New Year), and the event stand (south-east, 7.5 65 8.5): the Fear Mongerer and Oringo, whose visits never
overlap (Hypixel puts both at its Event Stand too). On Hypixel's Hub map (`hypixel-maps.md`) the event stand is the map's
own Halloween stand on the square (7.5 70 -85.5), and Diana stands where the map's Mayor does.

## Admin

- `/sbevent start <event>` starts any calendar event now for its usual length; `/sbevent stop <event>` ends one started
  that way (it ends as a normal one does: results, NPCs staying their hour). In memory only: a restart forgets it.
- `/sbspooky score <green> <purple>` sets your Candy Contest score; `/sbspooky chest` puts a Trick or Treat Chest in
  front of you.

## The Spooky Festival (Autumn 29th to 31st)

`SpookyFestival`. Hypixel's numbers:

- **Candy on every kill**, anywhere in SkyBlock: 10% Green Candy, of which 10% (+0.2% per mob level, +5% with a full
  Spooky set, +5% with a full Bat Person set) is Purple instead. +5/10/15/20% from the Candy Talisman/Ring/Artifact/
  Relic (the best one), +5% a piece of Spooky or Bat Person armour, +30% with Foxy's Sweet Benevolence.
- **Candy Score**: Green 1, Purple 5, from mob candy only (not chests). The sidebar shows the festival's time left and
  your candy ("Your Candy: 12 Green, 3 Purple (27 pts.)").
- **Spooky mobs** at night in the Graveyard and the Wilderness, 10 to an area while someone's near: Crazy Witch (Lv102,
  17 HP), Wraith (99, 15), Scary Jerry (95, 12), Wither Gourd (94, 11), Phantom Spirit (92, 10), Trick or Treater (92,
  10); 50 damage, Hypixel's coins and Combat XP, their own heads (from the Bestiary) and dyed leather. **Shielded**:
  every hit does exactly 1 damage (2 with the Ghoul Buster), whatever the weapon; no armour points. Drops: 1-2 Green
  Candy (Trick or Treater 1-3), 5% a Purple, 10% Ectoplasm (Wraith, Phantom Spirit); the Wither Gourd drops 2-4 Blast
  o' Lanterns instead. They leave at dawn and when the festival ends.
- **Trick or Treat Chests**: 1% where a mob dies (3% a Spooky mob). Only its killer can open it (click it); it waits
  a minute. 75% TREAT: 1 to 3 different kinds of loot from Hypixel's table (Green Candy 4-8, Purple 1-4, Ectoplasm,
  Blast o' Lantern, Pumpkin Guts, Candy Corn, Rock Candy, Spooky Cupcake, Bat Person Talisman, Vampirism VI, Candy the
  Fish). 25% TRICK: a Spooky mob jumps out (Hypixel's weights). A chest is display entities, never a block, so nothing
  stays in the Hub build.
- **The Candy Contest**: when the festival ends, everyone's ranked and their best bracket waits at the Fear Mongerer
  (or `/spookyfestival`, the chat's [Claim your rewards]): Wood 100 (25k coins, Spooky Pie, 5 Carnival Tickets), Stone
  1,000 (50k, + Spooky Disc, 10), Iron 1,500 (100k, + Vampirism VI, 15), Gold 1,750 (200k, 20), Diamond 2,000 (500k, +
  Witch Mask, 25), Emerald 5,000 (1M, + Skeleton Horse pet, 35). The Spooky Pie is stamped with the festival, your
  place and score.
- **The Fear Mongerer** (event stand, from an hour before to an hour after): the SPOOKY-themed card menu (rewards
  waiting first, your Candy Score and the next bracket, the shop, the top five), and his shop (`fear_mongerer.json`).
  Shops take candy from your sacks too.
- **The Trick or Treat Bag** is a candy-only sack (Green and Purple Candy, 100,000 each): right-click it to add it.
- Start and end messages are Hypixel's.

Decided where Hypixel's don't carry over: the "Top 50 of all of SkyBlock" bracket is the server's #1 of the festival
with at least 5,000 (1M, Skeleton Horse, Witch Mask, Vampirism VI, Disc, Pie, 50 Tickets; no Golden Treat, which we
don't have); chest candy doesn't count for the score; a Trick or Treat Chest waits a minute.

Part 2 (`SpookyBats`, `HeadlessHorseman`, `SkySeaCreatures`):

- **Bat Firework**: hit the floor or a wall (or right-click) in the Village (its zones on Hypixel's Hub map);
  needs a Trick or Treat Bag or Spooky Sack; 30s cooldown. 8-12 invulnerable Bat Piñatas burst out 14 blocks up and
  roam a minute (decided), each dropping a Green Candy on a player within 12 blocks now and then, at most 25 a player a
  firework, not scored. The launcher: 5/10/15/20/25/64 Purple Candy by weights 40/40/20/10/5/1.
- **Mega Bat** (Lv65, 100 HP, Shielded, scale 3): during the festival, one an island at a time, near a random player
  (decided: a 1-in-9 chance every 20s, so about every three minutes). Every hit drops a candy (10% Purple), scored;
  21 coins and 97 Combat XP when it dies. The **Echolocator**, held: "Mega Bat ← 27m" on the action bar.
- **Headless Horseman**: the Horseman's Candle at night in the Graveyard or Wilderness, one per summoner. A zombie in a
  jack o'lantern and black leather on a saddled skeleton horse (the horse can't be hurt); Lv259, 3,000,000 HP, 600
  damage, 1,942 Combat XP, 647 coins to the killer. Projectiles don't hurt him: they reel their shooter in and blind
  them 3s. Every 15s in turn: Headless Zombie Swarm (4 baby zombies, 20,000 HP; decided Lv100, 300 damage) and Bat Swarm
  (5 bats for 10s, each throwing a 10-damage potion at the nearest player every 1.5s). Loot to everyone who hit him:
  0-2 Green and 0-2 Purple Candy; a Spooky Fragment to the summoner and the top damager, 50% to the second (decided:
  Hypixel's pages disagree on the summoner's 4). Chat names the killer and the top three. Decided: he rides off after 10
  minutes; no Wounded effect yet; the ground isn't turned to obsidian (the Hub build stays as it is).
- **Spooky fishing**: from an hour before to an hour after the festival, 15% (+15% Spooky Bait, at most 75%) of water
  sea creatures come from the spooky pool, by weight: Scarecrow 2,875, Nightmare 1,500, Werewolf 500, Phantom Fisher
  100, Grim Reaper 25 (Light/Dark/Whale Bait: the last two 1.25x). Hypixel's levels, health, damage, XP, coins, drops
  and catch messages (`import_sea_creatures.py`).

Not yet: the Spooky Hook (rod parts), the Bat pet's candy bonus, Doug's perks, Foxy's Party Chests and Extra Event,
natural Bat Piñatas on private islands, Runic Horsemen, reward expiry.

## The Traveling Zoo (Early Summer and Early Winter 1st to 3rd)

`TravelingZoo`. Oringo at the event stand sells three of six pets at level 1: Blue Whale, Lion, Tiger, Giraffe,
Elephant, Monkey. One is Legendary, in turn; the other two random at Common to Epic. Hypixel's prices (coins and the
animal's goods, from the inventory or sacks), e.g. a Common Lion 10,000 coins + 64 Raw Beef, a Legendary Monkey 18M +
512 Enchanted Jungle Logs. Hypixel's chat at the start (in the Hub, and elsewhere with CLICK HERE to warp).

Decided: Hypixel's cycle table wasn't readable, so the Legendary goes round the six in order, one visit after another
(two visits a year), and the other two are seeded by the visit so everyone sees the same three.

## The New Year Celebration (Late Winter 29th to 31st)

`NewYear`. The Baker (plaza, south-west) gives everyone one **New Year Cake (Year N)** a year (SPECIAL, "Given to every
player as a celebration of the Nth SkyBlock year!") and sells the New Year Cake Bag (250,000 coins).
