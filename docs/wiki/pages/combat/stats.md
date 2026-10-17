---
title: Stats
category: combat
order: 1
icon: minecraft:iron_chestplate
summary: Every stat in SkyRPG SkyBlock, which ones do something today, where they come from and the formulas for damage, crits, defense and mana.
---
Stats are the numbers that make your character stronger. Gear, skill levels, slayer levels, pets and your SkyBlock Level add to them, and the game adds them up twice a second. This page lists the stats, what each does in the game today, and the maths behind combat. Open **Stats** in the menu to see your own totals; hover a stat to see how much each source adds.

## How stats work

- Every player starts with a base value for each stat (the tables below). Sources add to it.
- Stats come from your worn armour and held item, from your skill levels, slayer levels and SkyBlock Level, and from an active pet. See [Skills overview](skills-overview), [Armour sets](armour-sets), [Weapons](weapons), [Slayers](slayers) and [SkyBlock Leveling](skyblock-leveling).
- Most stats are plain numbers. **Crit Chance** is a percentage.
- Health and damage use SkyBlock's scale: **5 SkyBlock health is 1 vanilla health point**. 100 Health is 20 vanilla points, which is 10 hearts. Damage numbers, mob nameplates and boss bars all use the same scale.
- Hypixel's items carry about 70 stats, and all of them exist here with Hypixel's symbols. Only some have a gameplay effect so far; the tables say which.

## Combat stats

| Stat | Starts at | What it does |
| --- | --- | --- |
| Health | 100 | Your maximum health. 100 Health is 10 hearts. |
| Defense | 0 | Cuts the damage you take. 100 Defense halves it. |
| Strength | 0 | Each point adds 1% to your melee damage. |
| Crit Chance | 30% | The chance that a melee hit is a critical hit. |
| Crit Damage | 50% | The extra damage a critical hit does. |
| Speed | 100 | How fast you walk. 100 is normal. |
| Intelligence | 0 | Each point adds 1 to your maximum mana. |
| True Defense | 0 | Cuts True Damage the way Defense cuts the rest. |
| Vitality | 100 | How much healing you get. 100 is normal; 50 heals half as much. |
| Damage | none | Only weapons have it. It is the weapon's base damage. |

### Damage and crits

Only your own melee hits use Strength and crits. Arrows and abilities do not (see [Weapons](weapons)).

1. Start with the weapon's **Damage** stat. A weapon with 120 Damage deals 120 on a fully charged swing (internally, 5 Damage is 1 vanilla damage point). If you swing before your weapon has recharged, the hit is weaker, as in vanilla.
2. Multiply by `1 + Strength / 100`. Strength 100 doubles the hit.
3. Roll for a crit. If a random number from 0 to 100 is below your Crit Chance, multiply by `1 + Crit Damage / 100`. Crits show a gold, bold number between two sparkles and play a crit sound.
4. Against mobs, multiply by the Combat skill's **Warrior** bonus: `1 + (4% x Combat level, +1% a level after 50)`.
5. Other multipliers apply on top, such as the damage reductions slayer bosses have. See [Slayers](slayers).

Worked example: a 120 Damage weapon, 100 Strength, 30% Crit Chance and 50% Crit Damage, against a mob (Combat level 0).

| Hit | Damage |
| --- | --- |
| Normal | 120 x 2.0 = 240 |
| Critical | 240 x 1.5 = 360 |
| Average over many hits | 240 x (1 + 0.30 x 0.50) = 276 |

> [!WARNING]
> This is simpler than Hypixel's formula. Hypixel adds 5 to the weapon's damage and part of your Strength before multiplying, and adds its other bonuses together. Here Damage is only multiplied by Strength, crits and Warrior, so the numbers will not match Hypixel's exactly.

Crit Chance above 100% is wasted: you already crit every time. Crit Damage is then worth more than more Crit Chance.

> [!TIP]
> Crit Chance and Crit Damage multiply each other, so the lower one is worth more per point. With the starting 30% and 50%, a point of Crit Chance is the better buy.

### Defense

Every time something hurts you, the damage is multiplied by `100 / (100 + Defense)`.

| Defense | Damage you take | Same as having |
| --- | --- | --- |
| 0 | 100% | your Health |
| 50 | 67% | 1.5 x your Health |
| 100 | 50% | 2 x your Health |
| 200 | 33% | 3 x your Health |
| 300 | 25% | 4 x your Health |

So "effective health" is `Health x (1 + Defense / 100)`. Defense applies to mobs and bosses. It does not apply to damage that ignores invulnerability, such as the void. Armour pieces give Defense directly (they have no vanilla armour points).

### True Damage and True Defense

Some boss attacks deal **True Damage**, which Defense does nothing against: Sven Packmaster's bites, the slam-dunk every slayer boss does to a player who stands still, the Atoned Horror's TNT and blast, and the Voidgloom Seraph's glyphs and beams. Only **True Defense** cuts it, by the same `100 / (100 + True Defense)`. See [Slayers](slayers).

### Vitality

Every bit of healing you get is multiplied by `Vitality / 100`. It starts at 100. The Tarantula Broodfather's Noxious Paralysis and the Voidgloom Seraph's beams lower it during the fight.

### Speed

Speed 100 is normal walking. Walking speed is multiplied by `Speed / 100`, so 150 Speed walks 50% faster.

### Intelligence and mana

You have `100 + Intelligence` maximum mana. Mana regenerates by **2% of your maximum every second**, so an empty bar takes about 50 seconds to refill whatever its size. The mana bar is on your HUD. Nothing in SkyBlock spends mana yet, because Hypixel's item abilities are not built. If your maximum drops (you take off a piece of gear), your current mana is cut to fit.

## Gathering and fishing stats

| Stat | Starts at | What it does |
| --- | --- | --- |
| Mining Speed | 0 | How fast you break blocks. 100 makes it twice as fast. |
| Mining Fortune | 0 | Extra drops from ores. |
| Farming Fortune | 0 | Extra drops from crops. |
| Foraging Fortune | 0 | Extra drops from logs. |
| Fishing Speed | 0 | Fish bite sooner. Every 25 is one more level of Lure, up to four. |

### Fortune

When you break a block that counts (ores, grown crops, logs), it drops its normal drops again once for every full 100 Fortune, plus a chance for one more from the remainder. With 250 Fortune you get 2 extra copies and a 50% chance of a third. Crops only count when fully grown. See [Mining](mining), [Farming](farming) and [Foraging](foraging).

## Wisdom and Magic Find

| Stat | What it does |
| --- | --- |
| Farming Wisdom | Each point is 1% more Farming XP. |
| Mining Wisdom | Each point is 1% more Mining XP. |
| Foraging Wisdom | Each point is 1% more Foraging XP. |
| Combat Wisdom | Each point is 1% more Combat XP. |
| Fishing Wisdom | Each point is 1% more Fishing XP. |
| Alchemy Wisdom | Each point is 1% more Alchemy XP. |
| Magic Find | Better odds for rare drops. |

Wisdom multiplies the XP you earn by `1 + Wisdom / 100`. **Magic Find** multiplies the chance of rare drops by `1 + Magic Find / 100`; for slayer bosses it helps drops under 5%. See [Slayers](slayers).

## Stats that do nothing yet

Hypixel's items list many more stats. They show on tooltips and add up in your totals, but the game has no code for them yet:

- **Combat:** Bonus Attack Speed, Ferocity, Ability Damage, Weapon Ability Damage, Swing Range, Sweep, Health Regen, Mending, Fear.
- **Mining:** Breaking Power, Pristine, Mining Spread, and Ore, Block, Dwarven Metal and Gemstone Fortune.
- **Farming and foraging:** the crop Fortunes (Wheat, Carrot, Potato, Pumpkin, Melon, Mushroom, Cactus, Sugar Cane, Nether Wart, Cocoa Beans) and Fig, Mangrove and Helix Fortune.
- **Fishing:** Sea Creature Chance (starts at 5%, but sea creatures do not spawn in SkyBlock yet), Fishing Fortune (it only boosted the survival world's Tidescales), Double Hook Chance, Treasure Chance and Trophy Fish Chance.
- **Other:** Pet Luck, Bonus Pest Chance, Heat, Cold and Pressure Resistance, Respiration, Tracking, Pull, and the Rift stats.

They start working as their systems land.

## Where the stats come from

Skills give a fixed amount per level. The full table is on [Skills overview](skills-overview); in short, Farming gives Farming Fortune and Health, Mining gives Mining Fortune and Defense, Combat gives Crit Chance and the Warrior perk, Foraging gives Foraging Fortune and Strength, Fishing gives Health, and Enchanting and Alchemy give Intelligence.

Other sources:

- **SkyBlock Level:** +5 Health every level and +1 Strength every 5 levels. See [SkyBlock Leveling](skyblock-leveling).
- **Slayer levels:** a small stat per level for each boss. See [Slayers](slayers).
- **Gear:** the armour you wear and the item in your hand, with their reforges and potato books. See [Reforging](reforging).
- **An active pet.** Hypixel's pets are not built; see [Pets](pets).

> [!NOTE]
> Weapon Damage is not a player stat. Only the weapon you hold carries it, and nothing can add to it.

## See also

- [Combat basics](combat-basics): mobs, rewards, the combat tag.
- [Slayers](slayers): slayer levels and bosses.
- [Skills overview](skills-overview)
- [Weapons](weapons) and [Armour sets](armour-sets)
- [Accessories](accessories)
