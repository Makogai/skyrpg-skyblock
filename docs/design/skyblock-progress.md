# SkyBlock build: progress

The working checklist for `skyblock.md` (the plan). Tick items as they land; note decisions and gotchas under each
phase so the next session can pick up where this one stopped.

## Phase 0: Foundation

- [x] New repo `Makogai/skyrpg-skyblock` (own dev ports, pack and wiki URLs), seeded from SkyRPG.
- [x] Catharsis + FurfSky Reborn on the dev client. Verified: `custom_data {id:"ASPECT_OF_THE_END"}` on any base item
      gets FurfSky's texture on our server (2026-10-16).
- [x] **SkyBlock items**: an item definition can be a vanilla `base` item + `skyblock_id`, created as that item with
      `custom_data {id, uuid}`; lookups by SkyBlock id first. Definitions sync to clients gzipped in parts (1 MB limit).
- [x] Hypixel's stats in `StatTypes` (49 new: ferocity, attack speed, ability damage, true defense, pet luck, breaking
      power, pristine, the crop and ore fortunes, resistances, rift stats...), with Hypixel's symbols. Wisdom naming.
- [x] Hypixel's rarities (Divine, Very Special, Ultimate, Admin; `SUPREME` reads as Divine, `UNOBTAINABLE` as Special).
- [x] `skyblock` module + `tools/skyblock/import_items.py`: Hypixel API `resources/skyblock/items` (5,680 items:
      base material, name, rarity, category, stats, lore) into our definitions; the other Hypixel fields (sell price,
      gemstone slots, stars, museum, minion generator...) kept under `"hypixel"`. All 327 legacy materials mapped.
- [x] `/sbitem <id>|give|search`; Hypixel-style tooltips (stat order, lore codes, bold rarity line); SkyBlock items
      don't place, hatch or get eaten as their base. Verified with FurfSky in SkyCam (2026-10-16).
- [x] Item browser: the SkyBlock Items page in the K menu (grid, search, tooltips; admins click to get one).
- [x] Profiles and co-ops: up to 5 fruit-named profiles; player data saved/loaded per profile
      (`<world>/skyrpg_profiles/<profile>/<player>.dat`, PlayerDataStorageMixin); switching reconnects through the
      transfer packet (`accepts-transfers=true`). `/profiles`, Profile Management page (K menu), `/coopadd`,
      `/coop accept`. Existing player data becomes the first profile.
- [x] Islands dimension `skyrpg_skyblock:islands` (void, no spawns), starter island (grass, tree, Hub portal), build
      area ±80 for members only, guests can't touch, `/is` `/island` `/warp home`, `/visit` (online players), hub gate
      leads home, dying on an island respawns you there. Hypixel deaths: keepInventory, half the purse lost.
- [x] SkyBlock Menu: the K menu's first page, Hypixel's tiles (grey = not built yet: Recipe Book, SkyBlock
      Leveling, Personal Bank).
- [x] SkyBlock time and calendar on Hypixel's epoch (the date matches Hypixel's), the sky synced to it,
      Calendar and Events page, the SkyBlock sidebar HUD (date, time, ⏣ area, purse), area names from the server.
- [x] Public-island framework: zones (`data/<ns>/skyrpg/zones/*.json`: box, breakable blocks that become a stand-in
      and regrow, mob spawns up to a cap, area name), synced to clients; the Hub's protection asks them (Hub.BreakRule,
      Hub.CombatRule). Fast Travel: `/warp <place>`, the Fast Travel page, warps from `skyrpg/warps/*.json` unlocked
      on first visit to their area. Tested with a dev-only zone (run/server/world/datapacks/sbtest).
- [x] Storage: Ender Chest pages (45 slots, 2 of 9 unlocked at the start; more with SkyBlock Leveling later,
      `/storage unlock` for admins), the Storage page in the K menu (previews), `/storage`, `/ec [page]`; ender chests
      open it; the old 27-slot ender chest moves into page 1. Wardrobe: 9 sets, Hypixel's layout and rules, `/wardrobe`.
      Backpacks in Storage come later (our backpacks still live in the inventory's backpack slot).
- [x] Pack: Catharsis (client mod) and FurfSky Reborn (resource pack, `game_versions: ["26.2"]`, saved as
      `FurfSkyReborn.cats.zip`) in tools/pack/modrinth.json, downloaded from Modrinth by each launcher; a Settings toggle
      (Interface, "FurfSky Reborn item looks", on) keeps it enabled. Not published yet.
- [ ] **Owners:** ask the FurfSky Reborn team (their Discord) that listing it in our pack is fine, before publishing.

## Phase 1: Day One

- [x] **Minions** (`minions` module): all 61 types from `tools/skyblock/import_minions.py` (NEU repo: tier speeds,
      storage, upgrade recipes; fandom wiki: what each collects). Minion entity (small armour stand: tier head, tool),
      5 by 5 work area (block / crop / tree / mob / fishing), real-time offline catch-up, storage grid with "storage
      full" status, our own minion screen (3D minion, action timer, storage, Collect All, Upgrade with recipe costs,
      Pick Up), place on your own island only, 5 per profile. Known: the leather outfit doesn't render on the minion
      entity (vanilla armour stands show it; only the head slot renders) - cosmetic, look into later.
- [x] Minions: fuel, shipping and two upgrade slots. 17 fuels (`skyrpg/minion_fuels`, from NEU lore: speed, real-time
      duration, Catalyst output multipliers), burning item by item in real time, offline too (catch-up runs in stretches
      between fuel items); only fuel that lasts forever comes back out. Upgrades in `MinionUpgrade`: Auto Smelter,
      Compactor / Super Compactor 3000 / Dwarven (rules from the imported recipes, see `Recipes.block/enchanted`),
      Diamond (1 per 10 items) / Potato (1 per 20, our estimate) spreading, Krampus Helmet (0.0045% per item), Minion
      Expander (+1 reach, +5%), Flycatcher (+20%), Berberis Fuel Injector (+15%, farming), Solar Panel (+25% by SkyBlock
      day), Enchanted Egg, Flint Shovel, Enchanted Shears, Soulflow Engines (half output, Raw Soulflow every 3m / 90s).
      Budget / Enchanted Hopper sell overflow at 50% / 70% of `npc_sell_price`, coins held until Collect All.
      Not yet: Corrupt Soil, Sleepy Hollow, the "only ONE per profile" limits, slayer requirements on upgrades.
- [x] Recipes: `tools/skyblock/import_recipes.py`, 2,468 crafting grids from NEU with what unlocks each (collection
      tier, slayer level), loaded by `skyblock` `Recipes`.
- [x] Minion slots from unique crafts (`MinionSlots`, Hypixel's table 5 to 26; crafting and upgrading count, per
      profile via player data). Crafting minions through the Recipe Book / crafting table.
- [x] **Collections 1:1**: `tools/skyblock/import_collections.py` writes Hypixel's 83 collections (Rift left out) in
      the progress mod's format plus `"skyblock"` ids (Mushroom counts red + brown; Gemstone the rough gems) and
      Hypixel's `"unlocks"` lines; matched by SkyBlock id so an Enchanted Cobblestone isn't 160 Cobblestone. The 63
      survival collections are gone. Minion collecting counts (Collect All, taking items, Pick Up). Tier rewards: the
      unlock list in chat + 4 SkyBlock XP (into SkyRpgLevel for now). Not yet: the "+N Skill Experience" unlock lines
      grant nothing, co-op shared collections, the page's icons for skinned items (player heads).
- [x] **Recipe Book** (SkyBlock Menu tile, `RecipeBookPage`): every output, search, All / Unlocked / Craftable,
      locked dimmed, recipe grid with have/need, requirement + your tier, Supercraft (shift = up to 64). Design call:
      locked recipes still show their ingredients (Hypixel hides them) because it's more useful to plan.
- [x] **Crafting table** (`CraftMenu` + `CraftScreen`): crafting tables and `/craft` open our 3 by 3 screen; shaped
      matching that can shift and mirror (`Recipes.match`), result card, Craft / Craft all, Recipe Book link.
      Recipes are sent to clients (`RecipesPayload`, gzipped JSON). Vanilla recipe / advancement / tutorial toasts
      are hidden (`ToastManagerMixin`).
- [x] **Wiki cleanup** (2026-10-17, from `docs/design/wiki-audit.md`): 31 survival pages retired (dungeons, the
      survival world's regions and bosses, mounts, spells, wands, shields, bows, Delving, quests, contracts, titles,
      events); Getting Started, Skyhaven NPCs, the gathering skills, crafting, enchanting, gear, combat and pets
      rewritten for SkyBlock; the interface pages updated; generated item lists sorted by Hypixel category (survival
      items out), dropped the mount/quest/region/pet/mob lists, sell prices from Hypixel's NPC prices. Categories "Extras"
      (Builder, brainrots, crates) and "Pets".
- [x] Minion tier XII costs: Hypixel sells them (Bulvar, Tony, Einary...): `import_minions.py` reads NEU's npc_shop
      trades (and upgrade stones' trades) into the XII cost, coins as "COINS"; the minion screen does the trade until the
      NPCs exist (design call). Snow/Ice XII want North Stars, farming XII Pelts: not items yet. Also fixed: NEU's
      "INK_SACK-4" ids in costs (Lapis upgrades never matched) and costs from NEU's `recipes` lists (Mushroom, Lily Pad).
- [x] Warrior (+4% mob damage a Combat level, +1% after 50) in `CombatStats`. Carpentry XP from crafting (3% of the
      ingredients' NPC price, Hypixel) and Taming XP = the active pet's XP (our reading), both without their own
      action-bar line (`SkillLevels.giveXpQuietly`). Alchemy waits for brewing.
- [x] **Skills 1:1**: `tools/skyblock/import_skills.py` writes Farming, Mining, Combat, Foraging, Fishing, Enchanting,
      Alchemy, Taming, Carpentry with NEU's XP table and caps (Farming 60 as with Anita's bonus), Hypixel's coins per
      level (wiki Mining table, same for every skill), SkyBlock XP (5/10/20/30) and each skill's stat brackets + perk.
      Level-ups list and pay them (`PlayerSkills.hypixelRewards`); the tooltip shows the perk, totals and the next
      level. SkyBlock XP goes through the new core hook `SkyBlockXp` (progress installs it; collections and unique
      minions use it too). Survival Delving and the enchants mod's own Enchanting definition are gone. Not yet:
      Warrior's +4% damage (no such stat), XP sources for Alchemy / Taming / Carpentry, Runecrafting and Social.
- [x] **The Hub's areas** (design call: floating islands around Skyhaven, joined by sky bridges, in Hypixel's directions):
      Graveyard (W), Coal Mine with the Hub Crypts (N), Farm (NE), Forest (SW), Ruins (far W), Mountain with High Level
      (S), Wilderness with the Fairy Pond, Tavern and Dark Auction house (E). Built by `tools/builds/hub_areas/` (a kit on
      the dungeon kit's structure writer; graveyard.py by hand, the rest by helpers from BRIEF.md) as their own
      structures, placed by hub `HubAreas` (`skyrpg/hub_areas/<id>.json`: structure, origin, version; `/hubadmin area
      <id> place|save`; clearing never touches the village's box, where `village_bridges` keeps the bridge ends). Zones:
      mineable Coal Ore/Stone, Wheat, Oak logs/leaves, Carrots (regrowing); Hypixel's mobs with their stats, Combat XP
      and coins (zone mob specs: name, level, health, damage, xp, coins, time, sub-box, indoors; NATURAL spawns, so they
      count for slayers; never burn; the Ruins' wolves are hostile). NPCs (`npcs.py`, vanilla default skins until owners
      paint theirs): Pat, Farmer, Arthur, Lumber Jack, Lonely Philosopher, Scoop, Amelia, Christopher, Tia, Fisherman
      Gerald, Shifty, Lucius, Sirius; shops for Pat, Scoop, Shifty, Lucius, the Lonely Philosopher. Warps crypt, castle,
      da. Not yet: Shania (a cow NPC), the Hub Crypts' link to the Graveyard, the Colosseum, the Wizard Tower, the Hub's
      other Hypixel spots (Museum, Fashion Shop...).
- [x] **The public islands** (design call: each in its own void dimension of the Hub's type, `skyrpg_skyblock:
      farming_islands|the_park|gold_mine|deep_caverns|spiders_den`; `Hub.isPublic` gives them the Hub's protection,
      feeding and a void rescue to the island's arrival): The Barn + Mushroom Desert (one dimension, a bridge with a
      Farming V gate pad), The Park (Birch Park, Spruce Woods, Howling Cave, Dark Thicket, Savanna Woodland, Jungle
      Island), Gold Mine, Deep Caverns (six layers), Spider's Den (Spider Mound, Arachne's Sanctuary). Built by helpers
      with the area kit (now with `dimension`, `island`, `arrival`, `pad`, several zones per area, and a check of every
      block id against the game's list: `minecraft:chain` is `iron_chain` in 26.x and placed nothing in four Hub areas).
      `LaunchPads` (`skyrpg/launch_pads/*.json`: box, warp or exact destination, skill requirement, push back with what's
      missing): the Hub's Farm/Forest/Graveyard pads and the Coal Mine's gold gate (Farming/Foraging/Combat/Mining I),
      pads back to the Hub, Barn to Desert (Farming V), Gold Mine to Deep Caverns (Mining V). Warps barn, desert, park,
      gold, deep, spider (unlock on the first visit or a pad landing). Hypixel's mobs and stats per zone (Howling Cave
      spirits, the six layers' mobs, the Den's spiders and the Mound's level 42s); neutral mobs in zones are hostile.
      `npcs.py` makes 38 more NPCs (shops for the Windmill Operator, Jake, Old Shaman Nyko, Master Tactician Funk, the
      Iron and Gold Forgers, Walter, Shaggy), per-island Fairy Souls (28 more, 52 in all) and the pads. Lift Operators
      run Hypixel's lift (`Lifts`, `skyrpg/lifts/*.json`, `LiftScreen`): ride to any layer you've walked into. Not yet:
      Arachne and her altar, Trevor's trapper quests, the Park's Rain/Melody's harp, Rhys's and the Lazy Miner's quests,
      the Barn's Shania, the Dwarven Mines from the Deep Caverns, squid in Birch Park's lake.
- [x] **NPC shops**: `tools/skyblock/import_shops.py` writes NEU's 88 shops (684 offers; offers with enchanted books,
      Bits, Gems, North Stars... left out) to `skyrpg/skyblock_shops`; `SkyShops` + `ShopMenu` + `ShopScreen`: buy
      (shift = a stack), sell any stack with an NPC price by clicking it, buyback of the last 9 sales (until logout).
      NPC action `skyblock_shop`; `/sbshop [id]` for admins. The Hub's survival NPCs became Hypixel's merchants
      (design call, same spots and skins): Farm, Lumber, Fishing and Mine Merchant, Weaponsmith, Combat Merchant
      (provisioner), Wool Weaver (builder), Bartender, Alchemist (florist), Librarian (curio dealer), Rosetta
      (blacksmith), and the Banker. The survival economy shops (`skyrpg/shops`) are no longer used by the Hub.
- [x] **Bazaar**: `tools/skyblock/import_bazaar.py` (Hypixel's public Bazaar API snapshot) writes 1,018 products in
      Hypixel's 5 tabs, grouped by the collection they come from through the recipes (Oddities by item kind / letters).
      `Bazaar` (instant buy/sell, buy orders, sell offers, cancel, claim; 1.25% tax; 71,680 max; prices in tenths),
      `BazaarOrders` (saved with the world, per profile), `BazaarPage` (tabs, groups, search, product view with order
      book, Manage Orders). Design call: a market maker quotes every product at the snapshot's prices so a small server
      always trades (orders never match each other, as on Hypixel). Opens at SkyBlock Level 7; `/bz` and a SkyBlock Menu
      tile until there's a Bazaar NPC. Refresh prices with `import_bazaar.py --fetch`.
- [x] **Auction House** (`AuctionHouse`, `AuctionData` saved with the world, `AuctionHousePage`): BIN and bidding
      (+15% per bid), Hypixel's fees (1% / 2% / 2.5% listing, 20-1,200 duration fee, 1% collect tax over 1M), 14
      auctions, 6 categories by item type, search / sort / BIN filter / pages, outbid refunds (straight back or to
      collect), Manage with collect and Collect all, sold / won / expired messages. `/ah` and a SkyBlock Menu tile
      until there's an Auction Master NPC. Not yet: pets and their own category, "Bids" history.
- [x] **Slayers 1:1** (`tools/skyblock/import_slayers.py`, from the fandom wiki's boss and slayer pages plus NEU's
      slayer XP): Revenant Horror (5 tiers), Tarantula Broodfather (5), Sven Packmaster (4), Voidgloom Seraph (4) replace
      the survival lines. Hypixel's costs, health, damage, levels, Combat XP to summon (the bar fills with each kill's
      Combat XP), requirements (Combat level, the previous tier or the previous boss slain), drops with amounts, chances,
      odds labels and the slayer level each needs, and the 9 levels' stats, SkyBlock XP and unlocks. Maddox the Slayer in
      the Hub (the survival Slayer Master's spot). One SLAYER QUEST COMPLETE panel per kill; drop rolls count this kill's
      slayer XP (a first T1 kill reaches level 1, which Revenant Flesh needs); Magic Find raises drops under 5%; RARE /
      VERY RARE / CRAZY RARE DROP banners by odds, RNGesus Incarnate announced to everyone. Bosses only rise in a zone
      with mobs or out in the world (core `FightAreas`, installed by `Zones`), never on islands. Sven is a new wolf
      boss (`SvenEntity`). Slayer-locked recipes (140, from NEU's `crafttext` and `slayer_req`) check the level through
      core `SlayerLevels` (server: loaded lines; client: the page data, sent on join too). Slayers page: Hypixel's
      level track (each level's stats, SkyBlock XP, unlocked items), tier cards with Beginner..Excruciating titles, what
      a locked tier needs, and the drop table as item slots (odds colour, amount, chance, level in the tooltip).
      **Boss abilities 1:1** (`HypixelBosses`, kinds revenant/tarantula/sven/voidgloom so the survival tricks stay out):
      DPS as ~1 hit a second, regeneration after 5 s without hurting anyone, the slam-dunk for standing still; Revenant
      (arrows heal it, Life Drain, Pestilence with a 25% Defense shred, Enrage's mad/enraged cycle, the Atoned Horror's
      Redemption, TNT circles and Thermonuclear); Tarantula (a cave spider rider, Backstab, Noxious Paralysis' Vitality
      and Speed debuffs, Web of Lies' webs and egg sacs, Bat Barrage, the Conjoined Brood); Sven (Agile, True Damage,
      Call the pups! with Protected); Voidgloom (Malevolent Hitshield with its DPS bonus, Dissonance, Yang Glyphs thrown
      from its hands, Nukekubi heads broken by looking, Broken Heart Radiation's beams). Numbers Hypixel doesn't publish
      are our calls (Life Drain 1.5x DPS, Pestilence 0.25x, Enrage x3, slam-dunk 2x DPS true, TNT 15-60% of health).
      Core gained `TrueDamage` (True Defense cuts it; ability hits skip invulnerability frames) and Vitality now scales
      all healing (stats mixin); `SlayerCurses` is the debuff stat source.
      Not yet: runes, enchanted books and dyes in the drops (not in our item set), Maddox's Batphone, the Slayer RNG
      meter, slayer minibosses (Revenant Sycophant etc.), Aatrox's slayer perks, Inferno Demonlord and Riftstalker.
- [x] **SkyBlock Level** (the survival SkyRPG Level, renamed everywhere): 100 XP a level, +5 Health a level, +1 Strength
      every 5 (`SkyBlockLevels`, a stat source in SkyRpgLevel), Hypixel's feature unlocks and prefix colours listed in the
      level-up panel; the Wardrobe opens at level 5. SkyBlock Leveling page (`SkyBlockLevelingPage`, `/sblevel` through
      the new core `OpenPage` payload). Survival rules (XP per skill level, coins every 10th level) are off
      (`Features.SURVIVAL_LEVEL`), as are the Artisan Bench unlock lines (`Features.ARTISAN_BENCH`).
- [x] **Reforging** (Phase 2 start): `tools/skyblock/import_reforges.py` writes NEU's 50 Blacksmith reforges and 86
      reforge-stone reforges (stats by rarity, item categories or items, stone costs, ability text) to
      `skyrpg/reforges`; items `Reforges` (loaded on the server, sent with the item definitions) and `ItemMods`: an item's
      `modifier`, `rarity_upgrades` and `hot_potato_count` in its custom data (Hypixel's keys) feed its stats
      (`ItemStatSource`), name, rarity colour, attack damage and tooltip (yellow potato and blue reforge "(+N)", the
      Recombobulator's flickering rarity marks, "This item can be reforged!"). The Blacksmith (Hub, by Rosetta; action
      `reforge`) opens Reforge Item (`ItemWorkMenu` + `ItemWorkScreen`): a random reforge of his for coins by rarity, or a
      stone's reforge for its price; any anvil opens Combine Items: Recombobulator 3000, Hot Potato Book (10), Fuming
      Potato Book (15). `/sbitemmod` for admins. Not yet: reforge abilities (shown, no effect), stars, gemstones,
      enchanted books on the anvil.
- [x] **Enchanting** (Hypixel's): `tools/skyblock/import_enchants.py` writes 145 enchantments (NEU: item categories,
      conflict pools, XP costs, table caps; each level's book lore and its number) to `skyrpg/skyblock_enchants`; items
      `Enchants` (synced with the item definitions), `ItemMods` keeps Hypixel's `enchantments` compound (conflicts and
      one ultimate handled), tooltips list them Hypixel's way, enchanted items glint. Enchantment Table (`EnchantMenu` +
      `EnchantScreen`; any enchanting table in SkyBlock, Seraphine's `skyblock_enchanting`): pick an enchant and level,
      pay XP levels, same level again removes it; Enchanting XP 3.5 × levels^1.5. Books (`Books`: `ENCHANTED_BOOK` +
      enchantments; Bazaar ids `ENCHANTMENT_<ID>_<N>` resolve to them, 414 book products in Oddities) on the anvil: book
      on item for XP levels, equal books combine (to the table's cap). `EnchantEffects`: stat enchants, additive damage
      (new core `DamageBonus`, summed with Warrior in `CombatStats`), on-hit and on-kill effects; Ultimate Wise in
      `Abilities`. `/sbenchant` for admins. Not yet: Looting/Luck/Experience, most ultimates and fishing/farming extras,
      Bookshelf Power and Enchanting-level requirements, the Experimentation Table, book rarity by level.
- [x] **Accessories** (Hypixel's): the Accessory Bag (`AccessoryBag`, `AccessoryBagMenu` + `AccessoryBagScreen`; pages of
      45, Redstone collection II and +6 slots a tier; SkyBlock Menu tile and `/accessories`), accessory stats from the bag
      and inventory (each id once, only the highest of a line: NEU's talisman_upgrades), Magical Power by rarity
      (recombobulated counts), Maxwell (`MaxwellMenu` + `MaxwellScreen`, Hub, by Seraphine; action `maxwell`): 31
      Accessory Powers from `tools/skyblock/import_powers.py` (the wiki's power and Power Stone lists: base stats ×
      29.97 × ln(0.0019 × MP + 1)^1.2, unique bonuses, Combat requirements), learned with 9 Power Stones. Equipment: the
      survival accessory slots are Hypixel's Necklace, Cloak, Belt and Gloves slots (by item category), with reforges.
      Not yet: Tuning Points, enrichments, extra bag slots (Redstone Miner, Jacobus), Hegemony/Abicase specials,
      accessory abilities, SkyBlock XP from Magical Power.
- [x] **Pets** (Hypixel's): `tools/skyblock/import_pets.py` writes 87 pets (NEU: each rarity's lore template and head
      skin, stats and ability numbers at levels 1 and 100, the XP table, rarity offsets, the Golden Dragon's 200 levels)
      to `skyrpg/skyblock_pets` + `skyblock_pet_levels`. `SkyBlockPets` (skyblock): Hypixel's PET item (custom data `id`
      and `petInfo`), name "[Lvl N] Bee" and lore filled in at its level; right-click adds it (an early UseItem phase, so
      the Hub's protection doesn't eat it); the active pet is the pets mod's synced `Pets.ACTIVE` (drawn as a floating head
      by `PetLayer`); stats by level in a straight line; pet XP from skill XP (own skill 1×, Mining/Fishing pets 1.5×,
      Alchemy/Enchanting 1/12, others 1/4), Taming XP the same, Zoologist +1%/Taming level. `PetsMenu` + `PetsScreen`
      (`/pets`, the SkyBlock Menu tile): summon, put away, take out. The survival Pets page is retired in SkyBlock.
      Pets are SkyBlock ids too (`PetCatalog`: "BEE;4" resolves through the new item-registry resolvers and stack
      factories, the client gets the pet list on join): 25 NEU pet recipes in the Recipe Book and crafting table.
      Not yet: abilities, pet items, Kat, pet drops and shops (admins: `/sbpet give`).
- [x] **The End** (Hypixel's): its own dimension `skyrpg_skyblock:the_end` (type `skyrpg_hub:public_end`: End sky and
      music, no vanilla dragon fight; `Hub.isPublic`), built by `tools/builds/hub_areas/the_end.py`: the arrival with the
      Pearl Dealer and Gregory, the main island (Endermen 42/45/50, Endermites, Guber, Lone Adventurer, Tyzzo), the Zealot
      Bruiser Hideout under it, the Dragon's Nest (Zealots, Obsidian Defenders, Watchers; altar of 8 frames at 0, 0) and
      the Void Sepulture (Voidling Fanatics and Extremists); End Stone and Obsidian regrow; 4 Fairy Souls; `/warp end`;
      reached by the Spider's Den's west pad (Combat XII). Zone mobs gained item `drops` (into the killer's inventory).
      Package `skyblock.end`: `SpecialZealots` (1/420 per Zealot, 1/210 per Bruiser; its kill gives the Summoning Eye),
      `DragonAltar` (place / take back eyes in the frames, an early UseBlock phase), `DragonFight` (the 8th eye: egg, then
      one of 7 dragons by Hypixel's chances, vanilla's dragon with toughness for its 7.5-15M health, boss bar, its hits
      scaled to Hypixel's damage, lightning for Wise/Unstable/Strong/Superior; on death Hypixel's Dragon Weight: eyes,
      damage rank and share, one major drop in Hypixel's order and chances, the rest as fragments and pearls).
      `Zones.FIGHTABLE_TAG` lets a feature's own mobs be fought outside zones; `Zones.dressAs` gives them Hypixel stats.
      Not yet: the dragons' own attacks beyond lightning, plain Endermen's rare drops (Ender Armor, Ender Gem...),
      Voidgloom Seraph (slayer), the Endstone Protector, and the Ender Dragon pet's drop chance boosts.
- [x] **Chat panels** (owner ask, 2026-10-16): a notice with lines under it goes out as ONE message (`Notice.Panel`,
      marker `skyrpg_chat.panel` with its colour); the chat mod paints it as a card (`ChatPanelLook`). Used by skill and
      SkyBlock level ups, collection tiers, unique minions, /pet and /pet storage. Use panels for every multi-line
      message from now on.
- [x] **Fairy Souls** (`FairySouls`, `skyrpg/fairy_souls/<island>.json`): 24 on the Hub's islands (3-4 each, from the
      area scripts' spots), a turning pink crystal with a click box, spawned while a player is near, sparkling for who
      hasn't found it; "SOUL! You found a Fairy Soul!" panel with island and total counts; per profile. Tia the Fairy
      (`fairy_exchange` action) trades every 5 for 10 SkyBlock XP (Hypixel's backpack slots wait for backpacks; the
      exchange count is kept). `/fairysouls give|reset` for admins.

### Owner directions (2026-10-16)

- **FurfSky Reborn = the item look, nothing else.** Always on (no setting); `FurfSkyFilter` keeps only its item, armour,
  helmet and equipment folders, held-item models and the hotbar selection border. Its GUI/theme/hotbar/block/font parts
  never load. Later the owners want to work on the weapon textures themselves (after the systems are done).
- **Every screen is our own.** No chest GUIs and no vanilla textures, also for item screens (Wardrobe, Storage, and
  whatever comes: Bazaar, Auction House, minions, crafting...). Custom MenuTypes + our screens (see SkyMenus,
  WardrobeScreen, EnderPageScreen, SkyScreens); make them look great, not "Minecraft because there's no choice".
- **Clean SkyBlock**: no Journal or Contracts (for now), retired with the other survival-only pages and HUD boxes
  (mounts, spells, talents, titles, /pay, blood moon) in SkyRpgSkyblockClient.retireSurvival; Contracts and Quests
  switched off server-side (core `Features`).
- **Key names, never "K"**: in-game text names the player's bound key (`Msg.menuKey()`, a keybind component).
- The K menu is fine as it is (configurable), the SkyBlock Menu grid was liked: keep making it more our own.

### Notes

- Hypixel API resources (public, no key): `https://api.hypixel.net/v2/resources/skyblock/{items,collections,skills}`.
  Items carry `material` (1.8 names, `durability` for data values), `skin` for heads, `color` for leather, `item_model`
  (Hypixel's own `hypixel_skyblock:` models), `stats` (UPPER_CASE ids), `tier`, `category`, `generator` +
  `generator_tier` for minions, `upgrade_costs` (stars), `gemstone_slots`, `requirements`, `museum_data`.
- Catharsis's bundled SkyBlock API crashes a dedicated server, so the dev build adds it to client runs only. It also
  builds vanilla's own registries, which broke while the survival Nether biomes were injected (now off).
- The dev server runs offline mode: whitelist off; `op` a player after they join (names resolve to offline uuids).
- Items: re-run `python tools/skyblock/import_items.py` (or `--cached`) to refresh from the API; the output folder is
  generated and wiped each run, so hand edits belong in a later overrides file, not there. Descriptions come with
  `%%colour%%` placeholders (converted to `§` codes). SkyBlock items never wear out (always unbreakable).
- Profiles: almost everything about a player is in their data (attachments), so a profile is just that file. Data
  kept in SavedData by player uuid (parties, the brainrot board) stays account-wide. Bank sharing for co-ops comes
  with the Bank. Later: `/visit` offline players, profile deletion, island upgrades (bigger build area), Jerry.
- Islands: a ServerTick loop that can warp players must iterate a copy of `level.players()` (crashed once).
- The wiki generates a page per SkyBlock item (6,142 generated pages; `build.py --check` takes about a minute).
