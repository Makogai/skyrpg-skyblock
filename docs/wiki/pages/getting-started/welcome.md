---
title: Welcome to SkyRPG
category: getting-started
order: 1
icon: minecraft:amethyst_cluster
summary: What SkyRPG is, how to install the pack and join the server, and what is inside it.
---
SkyRPG is a 1:1 clone of Hypixel SkyBlock for Minecraft Java Edition **26.3**, built on Fabric. You start on your own floating **Private Island**, level skills, collect resources, build minions, trade on the Bazaar and the Auction House, slay bosses and climb the SkyBlock Level, with Hypixel's items, numbers and look. Every gameplay system is our own mod. Other mods are only used for utilities.

This page tells you what you need to play, how to install it and join, and what the pack adds. Read [First steps](first-steps) next.

## Where you play

- **Your Private Island** is yours alone: a small island with a tree, a portal to the Hub and room to build. Everyone starts here, and `/is` brings you back from anywhere. Place your minions here. See [Profiles and islands](profiles-and-islands).
- **The Hub** is the village of **Skyhaven**, with the shops, the Bank, the Bazaar, the Auction House and Maddox the Slayer, and seven areas on islands around it: the Graveyard, Coal Mine, Farm, Forest, Ruins, Mountain and Wilderness. See [Skyhaven](skyhaven) and [The Hub](the-hub).
- **The public islands** are shared, and you reach them by launch pads and `/warp`: the Farming Islands, the Park, the Gold Mine, the Deep Caverns and the Spider's Den. Each needs a skill level to enter. See [The Islands](the-islands).

You play on a **profile**, and you can keep up to five, each with its own island, inventory, skills and coins. When you die you keep your items and lose half your purse, then wake up on your island. The old survival world is kept only as an archive: nothing in the pack sends you there.

## What you need

- Minecraft Java Edition **26.3** and [Prism Launcher](https://prismlauncher.org) (free).
- That is all. The SkyRPG instance brings Fabric Loader, Fabric API and every SkyRPG mod, and keeps them current.
- The same SkyRPG version as the server. The instance updates itself, so you always match it.

## Installing (once)

1. Install Prism Launcher and sign in with your Minecraft account.
2. Download the SkyRPG instance: [SkyRPG-Prism-Instance.zip](https://makogai.github.io/minecraft-skyrpg/download/SkyRPG-Prism-Instance.zip). Don't unpack it.
3. In Prism choose Add Instance, then Import, and pick the zip. An instance called SkyRPG appears.
4. Launch it. The first start downloads the mods, so it takes a moment. A small window shows the progress.

From then on the pack **updates itself from GitHub every time you launch**. New and changed mods are downloaded, removed ones are deleted. You never need a new zip again. If you are offline, the game starts with the mods you already have.

## Installing the old way (fallback)

If you cannot use the instance, the host can give you `SkyRPG-<version>.zip`. Unpack it. Inside is a `mods` folder and a `HOW-TO-INSTALL.txt`. You also need Fabric Loader 0.19.5 or newer and Fabric API 0.161.0+26.3 from [Modrinth](https://modrinth.com/mod/fabric-api). You must replace the zip after every update.

**Prism Launcher:**

1. Add an instance: Minecraft 26.3, mod loader Fabric (0.19.5 or newer).
2. Right-click the instance, choose Edit, then Mods, then Add file, and pick every jar from the `mods` folder.
3. Launch.

**Official launcher:**

1. Run the Fabric installer and pick Minecraft 26.3.
2. Copy every jar from `mods` into `%APPDATA%\.minecraft\mods`. Make the folder if it is missing.
3. Start the `fabric-loader-26.3` profile.

## Joining the server

1. Start the game, open Multiplayer and choose Add Server.
2. Type the address the host gave you.
3. Ask the host to add your name to the whitelist if you cannot get in.

The easy way is the **Play SkyRPG** button on the title screen, which joins for you. The server needs the group's **Radmin VPN**: connect to the VPN network first, or the game cannot reach it. See [First launch and setup](first-launch).

You appear on your Private Island with a short welcome in chat. Press **K** to open the SkyBlock Menu. [First steps](first-steps) walks you through what to do.

## What is in the pack

Every system below is its own small mod with its own icon: see [The SkyRPG Mods](skyrpg-mods) for all 31.

| System | What it does | Where to read more |
| --- | --- | --- |
| SkyBlock Menu | One board for your profile, skills, collections, storage, warps and more | [SkyBlock Menu](skyblock-menu) |
| Profiles and islands | Up to five profiles, each with a Private Island; visits and co-ops | [Profiles and islands](profiles-and-islands) |
| The Hub and the islands | Skyhaven, its seven areas and the public islands | [The Hub](the-hub), [The Islands](the-islands) |
| Skills | Hypixel's nine skills, with levels, stats and coins as rewards | [Skills overview](skills-overview) |
| Collections | 83 collections whose tiers unlock recipes and minions | [Collections](collections) |
| Minions | 61 minion types that work your island while you are away | [Minions](minions) |
| Recipe Book | Every recipe, with what unlocks it | [Recipe Book](recipe-book) |
| Economy | Purse, Bank, NPC shops, Bazaar and Auction House | [Coins and the bank](coins-and-bank), [NPC Shops](npc-shops), [Bazaar](bazaar), [Auction House](auction-house) |
| SkyBlock Level | XP from nearly everything you do, with unlocks along the way | [SkyBlock Leveling](skyblock-leveling) |
| Slayers | Four slayer bosses, from the Revenant Horror to the Voidgloom Seraph | [Slayers](slayers) |
| Storage | Ender Chest pages and the Wardrobe | [Storage and Wardrobe](storage-and-wardrobe) |
| Items and reforging | Hypixel's items and their stats, reforges at the Blacksmith | [SkyBlock Items](skyblock-items), [Reforging](reforging) |
| Gear and enchanting | Weapons, armour, accessories, enchants, backpacks | [Weapons](weapons), [Armour sets](armour-sets), [Enchanting](enchanting) |
| Calendar | The SkyBlock date and its events | [Calendar and Events](calendar-and-events) |
| Extra mods and shaders | Sodium, Iris shader support, Zoomify and other speed and comfort mods, installed for you | [Mods and shaders](mods-and-shaders) |
| Interface | The menu, themed chat, HUD, minimap, world map, SkyHelper | [K menu](k-menu), [Chat](chat), [SkyHelper](skyhelper) |

## Good to know before you start

- Your settings and HUD layout are saved on your own computer, not on the server.
- Your purse, Bank, skills, pets and collections are saved with your profile on the server.
- Item looks come from the FurfSky Reborn resource pack, which the pack installs and keeps on. See [Mods and shaders](mods-and-shaders).
- The server keeps a log of chat, so do not post anything private there.

> [!TIP]
> Lost? Press **K** and open the **SkyBlock Menu**. Every page of the game is one click away from it, and [First steps](first-steps) lists what to do first.

## See also

- [First steps](first-steps)
- [Controls and keys](controls-and-keys)
- [Progression guide](progression-guide)
- [Skyhaven](skyhaven)
