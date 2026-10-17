---
title: Controls and keys
category: getting-started
order: 3
icon: minecraft:comparator
summary: Every key, mouse action and chat command a player uses in SkyRPG.
---
SkyRPG adds a handful of keys on top of vanilla Minecraft, a lot of mouse actions inside its screens, and a set of chat commands. This page lists all of them in one place. Keys can be changed in Minecraft's Controls screen, under the **SkyRPG** category. In the game's own messages the key is shown as whatever you bound it to; this page names the defaults.

## Keys

| Default key | Action | What it does |
| --- | --- | --- |
| **K** | Open SkyBlock Menu | The menu opens on the SkyBlock Menu, with your Skills, Collections, Storage, Fast Travel and more. Press again to close. See [SkyBlock Menu](skyblock-menu) and [K menu](k-menu). |
| **M** | Open World Map | Everything you have explored in this dimension. Press again to close. See [HUD and minimap](hud-and-minimap). |
| **=** | Minimap Zoom In | Zooms the minimap in, up to 4 pixels per block. |
| **-** | Minimap Zoom Out | Zooms the minimap out, down to a quarter pixel per block. |
| **B** | Open Backpack | Opens the backpack in your first backpack slot. See [Backpacks](backpacks). |
| **J** | Brainrot album | Opens your brainrot album. See [Brainrots](brainrots). |
| **H** | Look up in Wiki | Hover an item in any inventory and press this to open its wiki entry. |
| **Tab** (hold) | Player list | Shows the SkyRPG player list with heads, ping and info panels. Can be switched off in [Settings](settings). |

Vanilla keys keep their normal job.

### Keys from the pack's extra mods

These come from the performance and comfort mods the pack installs (see [Mods and shaders](mods-and-shaders)). They sit in the Controls screen under their own mod names.

| Default key | Mod | What it does |
| --- | --- | --- |
| **C** (hold) | Zoomify | Zoom in like a spyglass. Scroll while holding it to zoom more or less. Vanilla's "save hotbar" key (also C) is unbound once on first launch so the two don't clash. |
| **F6** (hold) | Zoomify | A stronger zoom that also hides the HUD. |
| **I** | Iris | Open the shader pack list. |
| **U** | Iris | Switch shaders on or off. SkyRPG moves this off **K** the first time you play, so it does not clash with the menu. |
| **R** | Iris | Reload the shader pack. |
| Not set | Mod Menu | Open the mod list. Bind a key in Controls if you want one. |

## Inside the SkyBlock Menu

| Input | Action |
| --- | --- |
| Click a tile | Open that page |
| Up and Down arrows | Move to the previous or next page |
| **1** to **9** | Jump to the first nine pages in the sidebar |
| **K** or **Esc** | Close the menu. Esc first steps back inside a page, for example clearing a search. |
| Scroll wheel | Scrolls the page |

Pages with a text field (the Settings search, the Bazaar and Auction House searches, the Bank) take digits and letters first, so numbers type instead of switching pages.

## In shops

| Input | Action |
| --- | --- |
| Click an offer | Buy one |
| Shift-click an offer | Buy a stack |
| Click an item in the sell panel | Sell the whole stack |
| Click a sold item in the buyback row | Take back one of your last nine sales for the same coins |

See [NPC Shops](npc-shops).

## At the Bank

| Input | Action |
| --- | --- |
| Type an amount, **Enter** | Deposit |
| **Shift+Enter** | Withdraw |
| **All** and **Half** buttons | Move all or half of the coins |

Amounts accept `k`, `m` and `b`, so `2.5k` is 2,500 and `1m` is 1,000,000. See [Coins and the bank](coins-and-bank).

## On the world map and HUD editor

| Input | Action |
| --- | --- |
| Drag | Move the map |
| Scroll | Zoom towards the mouse |
| **C** | Follow yourself |
| Right-click the map | Drop a waypoint |
| Click or right-click an icon | Track, share or remove it |
| Drag a box (HUD editor) | Move it |
| Scroll over a box (HUD editor) | Resize it, 0.5x to 2x |
| Right-click a box (HUD editor) | Hide or show it |
| **R** (HUD editor) | Reset the box under the mouse, or every box if the mouse is on none |

See [Waypoints](waypoints) and [HUD and minimap](hud-and-minimap).

## In the world

- **Right-click an NPC** to shop, bank, reforge, enchant, start slayer quests or ride a lift. See [NPCs](npcs).
- **Right-click a minion** to open its screen. **Right-click the top of a block** on your own island with a minion in your hand to place it. See [Minions](minions).
- **Right-click a crafting table**, or type `/craft`, to open the 3 by 3 crafting grid. **Right-click an ender chest** to open your Ender Chest pages. See [Recipe Book](recipe-book) and [Storage and Wardrobe](storage-and-wardrobe).
- **Right-click a weapon or tool** to use its ability, if it has one.
- **Right-click an accessory** to put it on. **Right-click a backpack** in its slot to open it. Backpack and accessory slots sit in strips beside your inventory and hide while the recipe book is open.
- **Right-click a pet item** to move it into your pet storage and equip it.
- **Step on a launch pad** to be thrown to the island it points at. See [The Islands](the-islands).
- **Click a Fairy Soul** to collect it. See [The Hub](the-hub#fairy-souls).

## Chat commands

Commands start with `/` and are typed in chat. Anyone can use these.

### Travel

| Command | What it does |
| --- | --- |
| `/is`, `/island` or `/warp home` | Go to your Private Island |
| `/hub` | Go to the Hub |
| `/warp <place>` | Go to an island or area you have visited: `hub`, `home`, `crypt`, `castle`, `da`, `barn`, `desert`, `park`, `gold`, `deep`, `spider` |
| `/visit <player>` | Visit the island of the profile a player is playing. You can look, not touch |
| `/waypoint ...` or `/wp ...` | Make and use waypoints. See [Waypoints](waypoints) |

Every warp has a short warm-up: stand still. You can't warp in combat, and running the command again cancels it. See [SkyBlock Menu](skyblock-menu#fast-travel).

### Profiles

| Command | What it does |
| --- | --- |
| `/profiles` | List your profiles, with buttons to switch or create one |
| `/profiles switch <name>` and `/profiles create` | Switch to a profile, or start a new one |
| `/coopadd <player>` | Invite a player to your co-op |
| `/coop accept <player>` | Accept a co-op invite |

See [Profiles and islands](profiles-and-islands).

### Menus

| Command | What it does |
| --- | --- |
| `/craft` | Open the crafting grid |
| `/storage` | Open Storage |
| `/ec [page]` | Open an Ender Chest page |
| `/wardrobe` | Open the Wardrobe |
| `/bz` or `/bazaar` | Open the Bazaar (SkyBlock Level 7) |
| `/ah` or `/auctionhouse` | Open the Auction House |
| `/sblevel` | Open SkyBlock Leveling |
| `/wiki [page]` | Open the in-game wiki |

### Money and info

| Command | What it does |
| --- | --- |
| `/coins [player]` | Show your purse and bank, or another player's |
| `/sell` or `/sell hand` | Sell the stack in your hand |
| `/sell all` | Sell every stack of the item in your hand |
| `/sell inventory` | Sell everything NPCs will buy |
| `/worth` | Show what the item in your hand sells for |
| `/skills [player]` | Show skills |
| `/stats [player]` | Show stats |
| `/pet`, `/pet away`, `/pet storage` | Show your pet, put it away, list your pet storage |
| `@skyhelper <question>` in chat, or `/ask <question>` | Ask SkyHelper, the assistant. `/ask` answers only you. See [SkyHelper](skyhelper) |
| `/party ...` or `/p ...` | Make and manage a party. See [Party](party) |

### Chat

| Command | What it does |
| --- | --- |
| `/msg`, `/tell`, `/w <player> <text>` | Whisper |
| `/r <text>` or `/reply <text>` | Reply to the last person who whispered you |
| `/me <text>` | Describe an action |
| `/pc <message>` | Send a message to your party |
| `/party chat` | Switch your chat to party chat, and back |
| `/emotes` | List the emotes. Click one to use it |
| `/stickers` | List the stickers. Click one to put it in your message |
| `/share screenshot [file]` | Show your newest screenshot, or a named one, to everyone |
| `/chat level [player]` | Show your SkyRPG chat level, or another player's |

See [Chat](chat).

> [!NOTE]
> Operators have more commands, such as `/sbitem`, `/sbshop`, `/fairysouls`, `/storage unlock`, `/coins give` and `/hubadmin`. They are not covered here.

## See also

- [SkyBlock Menu](skyblock-menu)
- [K menu](k-menu)
- [Settings](settings)
- [Chat](chat)
- [HUD and minimap](hud-and-minimap)
