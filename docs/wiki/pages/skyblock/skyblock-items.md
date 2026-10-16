---
title: SkyBlock items
category: skyblock
order: 1
icon: skyrpg_skyblock:aspect_of_the_end
summary: Every Hypixel SkyBlock item is in the game with its real name, SkyBlock ID, rarity and stats. With FurfSky Reborn installed, each one looks the way it does on Hypixel.
---
SkyRPG SkyBlock is being built as a 1:1 copy of Hypixel SkyBlock. The first piece is in: **all 5,680 SkyBlock items**, from {{item:skyrpg_skyblock:enchanted_diamond}} to {{item:skyrpg_skyblock:hyperion}}. Each one has Hypixel's name, SkyBlock ID, rarity, category and stats.

Where an item comes from (drops, recipes, shops, minions) arrives with the systems that use it, phase by phase. Until then, admins hand items out with `/sbitem`.

## How an item looks

A SkyBlock item is a normal Minecraft item underneath, like on Hypixel: the {{item:skyrpg_skyblock:aspect_of_the_end}} is a diamond sword, and the {{item:skyrpg_skyblock:summoning_ring}} is a player head. The item also carries its **SkyBlock ID**, the same one Hypixel uses.

The pack uses the **FurfSky Reborn** texture pack, through the **Catharsis** mod. Both read that ID and give each item its proper look: the Hyperion, Terminator and Necron's armour look like they do on Hypixel. Without the pack you see the base item instead, so a Hyperion is an iron sword. Heads always show their own skin, and leather armour keeps its colour.

FurfSky Reborn belongs to its own team. We never host it; your launcher downloads it from Modrinth.

## Tooltips

SkyBlock items use Hypixel's tooltip layout in SkyRPG's frame:

- the name in its rarity colour;
- the stats in Hypixel's order (Damage, Strength, Crit Chance, Crit Damage... then Health, Defense, Speed, Intelligence...);
- the item's lore, in Hypixel's colours;
- the rarity line in bold, like **LEGENDARY HELMET**.

## Rarities

Rarities run in Hypixel's order: Common, Uncommon, Rare, Epic, Legendary, Mythic, **Divine** (aqua), Special, **Very Special**, **Ultimate** and **Admin**. A Recombobulator (when it arrives) raises an item by one step, up to Divine.

## Stats

Everything Hypixel puts on items is a SkyRPG stat now, with Hypixel's symbol. On top of the ones SkyRPG already had, these are new:

- **Combat:** Bonus Attack Speed, Ferocity, Ability Damage, True Defense, Swing Range, Health Regen, Vitality and Mending.
- **Mining:** Breaking Power, Pristine, Mining Spread, and Ore, Block, Dwarven Metal and Gemstone Fortune.
- **Farming and foraging:** a Fortune for each crop, plus Fig, Mangrove and Helix Fortune.
- **Fishing:** Double Hook Chance, Treasure Chance and Trophy Fish Chance.
- **Other:** Pet Luck, Bonus Pest Chance, Heat, Cold and Pressure Resistance, Respiration, Fear, Alchemy Wisdom, Tracking and Pull, and the Rift stats.

The skill XP stats are called **Wisdom** now, as on Hypixel: Farming Wisdom, Mining Wisdom, Foraging Wisdom, Combat Wisdom and Fishing Wisdom.

Some of these stats only do something once their system lands (the Rift, the Garden, the Crystal Hollows).

## Items stay items

A SkyBlock item doesn't act like the vanilla item it's made from:

- block items (enchanted blocks, heads, wool) don't place, unless Hypixel lets you place that item;
- spawn-egg items (Decoys, Training Dummies) don't spawn their mob;
- food, potions and throwables don't get eaten, drunk or thrown just because their base could.

Bows, crossbows, fishing rods, ender pearls and wearable items work as normal. Nothing wears out.

## Commands (admins)

| Command | What it does |
| --- | --- |
| `/sbitem <id> [count]` | Gives you an item. Type its SkyBlock ID in lower case (`/sbitem aspect_of_the_end`), with `-` for a `:` (`wool-6` is `WOOL:6`). |
| `/sbitem give <players> <id> [count]` | Gives items to other players. |
| `/sbitem search <text>` | Lists up to 40 items whose name or ID contains the text. Hover to see one, click to get it. |
