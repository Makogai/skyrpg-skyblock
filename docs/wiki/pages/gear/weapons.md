---
title: Weapons
category: gear
order: 1
icon: skyrpg_skyblock:aspect_of_the_end
summary: Hypixel's swords, longswords, bows and wands: what each kind is, how Damage and the other stats work today, reforges, and where to get them.
---
Every weapon in SkyRPG SkyBlock is a Hypixel SkyBlock weapon, imported with its real name, rarity and stats. This page covers what the weapon kinds are, what works today, and how to get one. For the maths behind a hit, see [Stats](stats).

## The weapon kinds

| Kind | Items | Examples |
| --- | --- | --- |
| Sword | 156 | {{item:skyrpg_skyblock:fancy_sword}}, {{item:skyrpg_skyblock:aspect_of_the_end}}, {{item:skyrpg_skyblock:pooch_sword}}, {{item:skyrpg_skyblock:hyperion}} |
| Longsword | 6 | |
| Bow | 44 | {{item:skyrpg_skyblock:death_bow}}, {{item:skyrpg_skyblock:hurricane_bow}}, {{item:skyrpg_skyblock:juju_shortbow}}, {{item:skyrpg_skyblock:terminator}} |
| Wand | 26 | {{item:skyrpg_skyblock:starlight_wand}} |

Every one has its own page, and the SkyBlock Items page in the menu lists them all. See [SkyBlock items](skyblock-items) and [Items: weapons](items-weapons).

## What a weapon shows

A weapon's tooltip is Hypixel's layout: the name in its rarity colour, then its stats in Hypixel's order (**Damage**, Strength, Crit Chance, Crit Damage, Bonus Attack Speed, Intelligence...), its lore, and the rarity line in bold, such as **LEGENDARY SWORD**. Weapons from the dungeons carry their requirements in the data, but the dungeons are not built yet.

> [!NOTE]
> Hypixel's own item data has stats and a little lore, but no ability text and no set bonuses. So abilities such as Instant Transmission, Wither Impact or a wand's blast, and the gemstone slots, stars and Power Scrolls some items list, do not exist in the game yet. A weapon is its stats.

## Melee: swords and longswords

The held weapon's stats count while it is in your main hand. A sword's **Damage** is its base hit, on the same scale as mob health: a 100 Damage sword hits for 100 against a mob with 100 health (internally 5 Damage is 1 vanilla damage point). Then:

1. Damage is multiplied by `1 + Strength / 100`.
2. Roll Crit Chance. A crit multiplies by `1 + Crit Damage / 100`.
3. The Combat skill's Warrior perk multiplies it again against mobs: +4% a Combat level, +1% more a level after 50.

A hit you swing before the weapon has recharged is weaker, as in vanilla. Hits show a damage number: grey for a normal hit, gold and bold for a crit.

> [!WARNING]
> This is close to Hypixel's damage but not identical. Hypixel adds 5 to the weapon's damage and part of your Strength before multiplying, and other bonuses add together. SkyRPG multiplies Damage by Strength and crits only. Ferocity, Bonus Attack Speed, Ability Damage and Swing Range are shown on items but do nothing yet.

## Bows

Bows draw and shoot like vanilla bows. Hypixel's bow stats are on the item, but a Hypixel bow does not use them for its arrows yet: its Damage, Strength and Crit Damage do not change how hard an arrow hits. Held, a bow still adds its non-damage stats to you.

## Wands

Wands are on the list with their stats (mostly Intelligence and Weapon Ability Damage), but their abilities are not built, so a wand has no attack. Mana and the Intelligence stat work, and the mana bar shows on the HUD, but nothing in SkyBlock spends mana yet.

## Reforges, Recombobulators and Potato Books

Every weapon can be improved without changing what it is. The Blacksmith in the Hub gives a weapon a random reforge for coins (swords, bows and the others each have their own reforges), reforge stones give a chosen one, and the anvil takes a **Recombobulator 3000** (one rarity up) and **Hot Potato** and **Fuming Potato Books** (+2 Damage and +2 Strength each, up to 15 books). All of it is on [Reforging](reforging).

## Never wearing out

SkyBlock weapons never break and never need repairing. They are always unbreakable, as on Hypixel.

## Where to get one

There is no loot from dungeons or world bosses to find them in yet. What works today:

- **NPC shops** sell 36 weapons, the starter ones such as the {{item:skyrpg_skyblock:fancy_sword}}. See [NPC shops](npc-shops).
- **Crafting** makes 119 weapons from Hypixel's recipes. They show in the [Recipe Book](recipe-book), with what unlocks each (a collection tier or a slayer level).
- **Slayers** drop the items that the slayer weapon recipes need. See [Slayers](slayers).
- The **Auction House** lets players sell weapons to each other. See [Auction House](auction-house).
- Admins can give any item with `/sbitem`.

Together, 150 of the 232 weapons have a shop offer or a crafting recipe; the rest need sources that arrive with later systems.

## See also

- [Armour sets](armour-sets)
- [Tools](tools)
- [Reforging](reforging)
- [Stats](stats)
- [Combat basics](combat-basics)
- [SkyBlock items](skyblock-items)
