---
title: Combat basics
category: combat
order: 2
icon: minecraft:iron_sword
summary: Zone mobs with Hypixel levels and stats, what you see in a fight, how damage and defense work, the combat tag, and what a kill pays: Combat XP, coins and slayer progress.
---
You fight in the **zones** of the Hub's areas and the public islands. Their mobs have Hypixel's names, levels, health and damage. A kill pays Combat XP and coins, and the right mobs fill a [slayer](slayers) quest. This page covers what you see in a fight and what a kill gives you. For the maths of a hit, see [Stats](stats).

## Where the mobs are

Mobs only spawn in a zone's fighting areas, up to a cap, and keep coming back. They do not burn in daylight, and neutral ones (the Ruins' wolves, the Deep Caverns' pigmen) attack anyone who comes near. Private Islands have no monsters.

| Zone | Mobs |
| --- | --- |
| Graveyard (Hub) | Graveyard Zombie (level 1), Zombie Villager (1) |
| Coal Mine (Hub) | Crypt Ghoul (30), Golden Ghoul (50) |
| Mountain (Hub) | Skeleton (6) |
| Ruins (Hub) | Wolf (15), Old Wolf (50) |
| Spider's Den | Splitter, Weaver, Dasher and Voracious Spiders (2 to 10), Gravel Skeleton (2); in the Spider Mound, level 42 spiders |
| The Park, Howling Cave | Pack Spirit (30), Howling Spirit (35), Soul of the Alpha (55) |
| Deep Caverns | Sneaky Creeper (3), Lapis Zombie (7), Redstone Pigman (10), Emerald Slime (15), Miner Zombies and Skeletons (15 and 20) |

Every mob with its health, damage, Combat XP and coins is listed on [The Hub](the-hub) and [The Islands](the-islands). The Barn's and the Mushroom Desert's cows, pigs, chickens, rabbits and sheep are farm animals, with a level but no attack and no reward.

## What you see in a fight

- **Nameplates.** A mob within 20 blocks shows `[Lv 30] Crypt Ghoul` with its health, on the same scale as your own. The level is green below 10, yellow below 20, gold below 30, red below 40 and dark red from 40. Switch them off in [Settings](settings) under HUD ("Mob nameplates").
- **Damage numbers.** Each hit pops a number over the mob for under a second. Normal hits are grey, critical hits are bold gold between two sparkles.
- **Boss bars** for slayer bosses, with their timer.

The level on a zone mob is for show: its health and damage are Hypixel's own numbers, not scaled by level.

## Damage you deal

A melee hit is the weapon's **Damage**, times `1 + Strength / 100`, times `1 + Crit Damage / 100` when it crits (Crit Chance is the chance), times the Combat skill's **Warrior** bonus against mobs (+4% a Combat level, +1% more a level after 50). Worked examples are on [Stats](stats#damage-and-crits). Bow damage and abilities are not built for Hypixel items yet; see [Weapons](weapons).

## Damage you take

A mob's damage is its Damage number, reduced by your Defense: the hit is multiplied by `100 / (100 + Defense)`, so 100 Defense halves it. Some slayer boss attacks are **true damage**, which only True Defense cuts. See [Stats](stats#defense). All healing you get is multiplied by your Vitality.

If you die you keep your items, lose half your purse and respawn, as on Hypixel.

## Combat tag

Fighting puts you **in combat** for **10 seconds**. A red **In combat** box with the seconds left shows at the top of the screen, and you cannot teleport (Fast Travel, `/warp`, `/hub`, `/is`, `/visit`) while it lasts. You are tagged when you deal damage to or take damage from a hostile mob or another player (an arrow counts for whoever fired it). Each hit restarts the 10 seconds. Fall damage, fire, the void and hitting passive animals do not tag you, and dying clears it.

## What a kill pays

Killing a zone mob gives:

- **Combat XP**: the mob's own number (a Graveyard Zombie 6, a Crypt Ghoul 36, a Golden Ghoul 50, a Spider Mound spider 36), raised by your Combat Wisdom. It levels the Combat skill, which gives Crit Chance, the Warrior perk, coins and SkyBlock XP. See [Skills overview](skills-overview).
- **Coins** straight into your purse (a Graveyard Zombie 1, a Crypt Ghoul 13, a Golden Ghoul 100, a Soul of the Alpha 50).
- The mob's normal drops. Hypixel's mob drop tables are not imported yet.
- **Slayer progress.** Each kill adds its Combat XP to a running [slayer quest](slayers) when the mob is one the boss wants.

Mobs from a spawner give only a quarter of the Combat XP and do not count for slayers. The zones' own mobs are natural spawns, so they count in full.

## Slayers

Maddox the Slayer in the Hub sells boss quests: Revenant Horror, Tarantula Broodfather, Sven Packmaster and Voidgloom Seraph, in up to five tiers. Kill the right mobs to fill the bar, then the boss rises. Its drops roll with **Magic Find**, rare ones are announced, and slayer levels give stats and unlock recipes. See [Slayers](slayers).

## See also

- [Stats](stats)
- [Slayers](slayers)
- [Weapons](weapons) and [Armour sets](armour-sets)
- [The Hub](the-hub) and [The Islands](the-islands)
- [Skills overview](skills-overview)
