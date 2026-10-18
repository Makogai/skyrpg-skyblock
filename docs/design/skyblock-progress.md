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
      Backpacks in Storage (2026-10-18): Hypixel's 18 backpack slots; right-click a SkyBlock backpack (Small 9 to Jumbo
      45, any colour) to store it, open it from the Storage page as a page of its own size, take it out once empty
      (decided, so nothing nests). The old survival backpacks still have their inventory slots.
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
- [x] **Dwarven Mines / Mining 2** (Hypixel's): research in `docs/design/research/dwarven-mines.md` (the official wiki is
      gone; a community mirror). The island: dimension `skyrpg_skyblock:dwarven_mines` (type `skyrpg_hub:public_mines`: no
      sky light, cave sky and music; `Hub.isPublic`), four area scripts from `docs/design/dwarven-mines-build.md`
      (`mines_village`, `mines_west`, `mines_center`, `mines_east`: 18 zones, 9 doorways between them, ~1.25M blocks), 11
      Fairy Souls, 40 NPCs (`npcs.py`: Emissaries and the King open Commissions, the Forger the Forge, Bubu/Bulvar/
      Brynmor shops, Banker Broadjaw, the Blacksmith), `/warp mines` and `/warp forge`, a Mining XII pad from the Obsidian
      Sanctuary (deep_caverns v2). **Mining 2 rules** (`SkyMining`, core `BlockStrength` hook in the `getDestroyProgress`
      mixin, both sides): on public islands but the Hub, Hypixel's block strength and Breaking Power (too little BP: no
      break and a message), ticks = round(30 × strength / Mining Speed), 4-tick floor, instamine over 30× (60× for ores,
      pure ores, obsidian, metals). Mithril (3 tiers) and Titanium handled in a late break phase: Hypixel's items with
      Mining Fortune, Mining XP, Mithril Powder in the Mines, 0.5% Titanium spawns (Titanium Insanium), `Zones.deplete`.
      **Heart of the Mountain** (`Hotm`, `HotmTree`, `HotmMenu` + `HotmScreen`, `/hotm`, `/sbhotm`): NEU's tree via
      `tools/skyblock/import_hotm.py` (a small Lisp evaluator turns its cost/stat formulas into per-level tables),
      tiers 1-10 by HotM XP, tokens 1/2/2/2/2/2/3/2/2/2 + Core of the Mountain's, unlock next to an owned perk, powder
      upgrades (shift ×10), right-click disables, abilities selected; stats (Mining Speed, Mining Fortune), Mining Speed
      Boost on right-click with a pickaxe (2 min cooldown). **Commissions** (`Commissions`, `CommissionsMenu` +
      `CommissionsScreen`): 15 Dwarven Mines tasks (Mithril/Titanium anywhere or per area, Goblins, Glacite Walkers,
      Treasure Hoarder hits), 2 slots (+1 at 100, +1 Core 3), claims at the King/Emissaries: HotM XP 100/200/400, 500
      Mithril Powder, 5,000 Mining XP, +900 HotM XP for the first 4 a day. **The Forge** (`Forge`, `ForgeMenu` +
      `ForgeScreen`): NEU's 123 forge recipes (`import_forge.py`), slots by HotM (2 at HotM 2 to 6 at 6, +1 Core 2),
      real-time jobs, Quick Forge, ready notices, `/sbforge [finish]`. `MinesMobs`: Ghosts are charged invisible creepers
      that never explode and hit on contact.
      Not yet: the King's rotation, mining events, Golden/Diamond Goblins, Powder Ghasts, daily Puzzler/Fetchur, Ticket
      Masters, most perks' effects, Pickobulus and other abilities, drills (fuel, parts), the Crystal Hollows and Glacite.
- [x] **Sacks** (Hypixel's): `tools/skyblock/import_sacks.py` writes NEU's 28 sack types (contents, every sack item's
      size from its lore: Beginner 640, Small 2,240, Medium 6,720, Large 20,160, Gemstone 128k-2.56M) to `skyrpg/sacks`.
      `Sacks` (skyblock): right-click a sack to add it (early UseItem and UseBlock phases), per-profile contents and owned
      sacks; gathered items (`ItemGains` GAINED: pickups, mining, farming...) that a sack holds move into it up to its
      size, "[Sacks] +N items. (Last 5s.)"; `SacksMenu` + `SacksScreen` (`/sacks`, a `sacks` action): take a stack or
      fill the inventory, insert inventory; `/gfs <item> <amount>`. Pets got the same UseBlock early phase (adding a pet
      while looking at a block was refused by the Hub).
      Not yet: Sack of Sacks, Pocket Sack-in-a-Sack, Trophy Fish and Rune sacks, crafting straight from sacks.
- [x] **Crystal Hollows** (Hypixel's): research `docs/design/research/crystal-hollows.md`. A **generated** world
      (`tools/skyblock/gen_hollows.py` writes the dimension, noise settings, material rule, 6 biomes, gem/metal vein features
      and the zones): 512 x 512 around 0, 0, y 0-192, vanilla's nether cave noise, walls from x/z gradients, lava sea at
      y 24, bedrock floor and roof; `HollowsBiomes` (a BiomeSource: Jungle NW, Mithril Deposits NE, Goblin Holdout SW,
      Precursor Remnants SE, Magma Fields under y 64, Nucleus in the middle) with Amethyst/Jade/Amber/Sapphire/Topaz veins,
      Ruby everywhere, rare Jasper, Mithril and Titanium in the Deposits, lush/basalt decoration. Mined blocks turn to air
      for good (zone `regrow` -1); `HollowsReset` makes the world anew a week after it was generated (or after
      `/hollowsreset`), before the worlds load, and `HubAreas.replaceAll` re-places its structures. Six structures by
      helpers (`hollows_*.py`, `hollows_lib.py`; `docs/design/crystal-hollows-build.md`): the Nucleus (arrival, 5 crystal
      pedestals, pad back), Jungle Temple, Khazad-dûm, Mines of Divan, Goblin Queen's Den, Lost Precursor City; the kit
      now saves unset cells as air in generated worlds (`FILL_AIR`). Mining (`SkyMining` Hollows tables): Hard Stone
      50/BP4, gems 2,300-4,800/BP6-9 giving Rough gems (Gemstone Fortune) and 2 Gemstone Powder; Mithril/Titanium with
      Dwarven Metal Fortune. `TreasureChests`: 1/500 Hard Stone (+Great Explorer), owner-only chest for a minute, 3-6 rolls
      (powder, the zone's Rough/Flawed gems, Treasurite, rares, Pickonimbus 1/200). Zone mobs (Sludge, Thyst, Grunt,
      Goblins, Automaton, Yog). `HollowsAccess`: Gwendolyn (The Lift) sells a 10,000-coin 6-hour pass (Mining XII, HotM
      IV), sends you to the Nucleus, takes you out when it runs out. Commissions got a second board for the Hollows
      (Emissary Sisko): Hard Stone, each gem, chests and mob slayers; 400 HotM XP, 1,500 Gemstone Powder, 10,000 Mining XP.
      Not yet: crystals and Nucleus loot, the structures' quests and bosses (Bal, Corleone, Goblin Queen, Professor
      Robot, Jungle Temple key), worms/Scatha, lockpicks, Fairy Grottos, the Glacite Tunnels.
- [x] **Gemstones on items**: `tools/skyblock/import_gemstones.py` (NEU gemstones.json + gemstonecosts.json: 12 gems'
      stats by tier and rarity, 499 items' slots and unlock costs, removal costs) to `skyrpg/gemstones`; items
      `Gemstones` (loaded on the server, sent with the item definitions): Hypixel's `gems` custom data, stats in
      `ItemMods.stats` (pink "(+N)" in tooltips), a slot row ` [⸕] [☘] [✦]` (tier-coloured brackets, dark when locked).
      `GemGrinderMenu` + `GemGrinderScreen` (Gemma in the Nucleus, `gemstone_grinder` action, `/gemstones` for admins):
      unlock a slot (coins and gems), set your best fitting gem, right-click to take it out for Hypixel's price.
      Not yet: Gemstone Chambers, chisel slots, the Divine column, Perfect gems' crystals.
- [x] **Item abilities** (Hypixel's): `import_items.py` reads each item's first ability from NEU's lore (name, trigger,
      description, Mana Cost, Cooldown; type = the name in snake case): 289 items. Tooltips show "Ability: <name>
      <TRIGGER>". `HypixelAbilities` (skyblock) implements the common ones with numbers read from the description:
      Instant Transmission (teleport N blocks, stop before walls, +Speed), Dragon Rage / Ice Spray / Molten Wave (cones),
      Giant's Slam, Showtime / Guided Bat / Ice Bolt / Rapid Fire / Fire Blast (shots with blasts or slowness), Speed
      Boost, the wands' heals; ability damage × (1 + Intelligence/100) × (1 + Ability Damage %). Temporary speed is a
      stat source. Not yet: the other ~270 abilities, scroll abilities (Hyperion's Wither Impact), sneak/left-click
      triggers, set bonuses.
- [x] **Bestiary**: `tools/skyblock/import_bestiary.py` (NEU bestiary.json: 255 families on 19 islands, heads, caps,
      brackets) to `skyrpg/bestiary`. `Bestiary` (skyblock): kills by family (the mob's name, Hypixel's; aliases for Goblin
      variants, Zealot Bruisers, the dragons; unnamed vanilla mobs by type), tiers from the bracket up to the cap. Decided
      (Hypixel's current reward numbers weren't in the data): +1 SkyBlock XP per family tier, a Milestone every 10 tiers
      for +2 Health (a stat source). `BestiaryMenu` + `BestiaryScreen` (`/bestiary`, `/be`, a `bestiary` action): islands,
      families with heads, tiers and bars, ??? until found. Not yet: per-family bonuses, Magic Find.
- [x] **Museum**: `tools/skyblock/import_museum.py` (NEU museum.json: 973 donations in 8 categories, armor sets with their
      pieces, SkyBlock XP, game stage, and the lower donation each higher one counts for) to `skyrpg/museum`. `Museum` +
      `MuseumMenu` + `MuseumScreen` (`/museum`, a `museum` action): category tabs, ready-to-donate items first (gold),
      donated (green), unknown (?); donating takes the item (or the whole set) for good, pays its SkyBlock XP and counts
      its child chain. Not yet: borrowing back, milestones, the Curator and building.
- [x] **Booster Cookie**: `BoosterCookie` (skyblock): eat one (right-click, early UseItem/UseBlock phases) for 4 real-time
      days of the Cookie Buff (stacking), per profile: +15 Magic Find and +5 Wisdom in every skill (a stat source), coins
      kept on death (`Deaths`); `/cookie`. Decided: no /ah or /bz gating (there are no Auction/Bazaar NPCs yet to fall back
      on). Not yet: Bits and the Community Shop.
- [x] **Pet items**: `tools/skyblock/import_pet_items.py` reads NEU's 42 pet items' lore into effects (flat stats,
      percent of the pet's own stats, pet XP boosts per skill or all, Tier Boost) to `skyrpg/skyblock_pet_items`.
      `PetItems`: right-click one with a pet out (early UseItem/UseBlock phases) to set its petInfo `heldItem` (the old
      one comes back); `SkyBlockPets` applies it to stats (boosted tier, multipliers, flats), pet XP and the tooltip
      ("Held Item", and lore numbers from the effective stats). Not yet: Flying Pig, Quick Claw, Exp Share and the other
      special ones.
- [x] **Kat**: `tools/skyblock/import_kat.py` (NEU's 216 "katgrade" recipes: pet, rarity from/to, coins, items, time) to
      `skyrpg/kat`. `Kat` + `KatMenu` + `KatScreen` (the `kat` action; Kat's NPC at a Hub market stall, `/kat [finish]`
      for admins): one pet at a time, taken from the menu or the one out, real-time wait, ready message, claim sets its
      tier (XP kept). Not yet: Kat Flowers and Bouquets.
- [x] **Fishing** (Hypixel's): research `docs/design/research/fishing.md`. `tools/skyblock/import_sea_creatures.py` writes
      `skyrpg/skyblock_fishing`: 21 sea creatures (the hand table from the wiki mirror: pool, Fishing level, weight,
      Fishing XP, damage, catch message; NEU's `*_SC` items: level, health, rarity, Combat XP, coins, drops with odds and
      counts, books and pets, the look from its mobs render with head skins and dyed armour, Rider of the Deep's chicken),
      the normal catches, Good/Great/Outstanding treasure (89/10/1) and 20 baits. The fishing mod got a SkyBlock hook
      (`Catching.installSkyBlock`: the wait and the whole catch in SkyBlock worlds; `LavaFishing.allowElsewhere`).
      `SkyFishing` (skyblock): Hypixel's wait (200-400 ticks × (1 - Fishing Speed / 300)), then a sea creature at Sea
      Creature Chance (base now 20, a quarter on private islands; Double Hook), else treasure at Treasure Chance, else a
      fish; items give Hypixel's own `FISH_ITEM` Fishing XP (from the items API); the first bait in the inventory counts
      and one is used per catch. `SkySeaCreatures`: by Fishing level, fluid, island/zone and condition (Dark Bait at night,
      Carrot Bait 5% Carrot King; Light/Dark/Whale Bait ×1.25 on the rare ones), surfaces at the bobber flung at you,
      dressed (`Zones.dressAs`), targets you; the killer gets coins, drops (rare ones announced) and the Fishing XP (none
      on private islands); rods hit them ×2; they leave after 6 minutes or when their fisher goes; max 20 each; no vanilla
      loot (`NoVanillaLootMixin`). Any rod fishes the Crystal Hollows' lava (Flaming Worm, Lava Blaze, Lava Pigman).
      `/sbfishing reel|creature` for admins. Survival enchant tomes no longer drop in SkyBlock.
      Decided: no weight on the wiki for the Night Squid (600) or the Hollows lava creatures (300/200/200, XP from their
      health); the Flaming Worm also in the Magma Fields (our Precursor Remnants has no lava); the Great and Outstanding
      treasure lists came back short, so enchanted Pufferfish/Clownfish/Clay/Sponge/Ink/Prismarine fill them to 100;
      treasure XP is the item's or 50/200/800 by tier; Hollows lava catches are lava items Hypixel gives Fishing XP for;
      the Sea Guardian isn't in today's pool.
      Not yet: trophy fish, hotspots, event sea creatures (Spooky, sharks, Jerry), Crimson Isle/Bayou/Galatea fishing,
      Agarimoo, Fishing Fortune, rod parts, Fishing Bag and bait sacks.
- [x] **Experimentation Table**: research `docs/design/research/experimentation-table.md` (the wiki mirror's raw pages sit
      behind a bot check, so the Superpairs table came back scrambled). Hand-written `skyrpg/experiments/experiments.json`.
      The table is Hypixel furniture: items with `hypixel.furniture` may be placed (`SkyblockItemUse`), the head keeps the
      item's data, right-click opens it, breaking it on your island gives the item back. `Experiments` (charges, banked
      bonus clicks, serum doses per profile; `/sbexperiments [peek|reset]` for admins) + `ExperimentMenu` (server-run
      games synced through data slots each tick) + `ExperimentScreen`: Superpairs (pairs of prizes, Feather +3 clicks,
      Diamond instant find, prizes and Enchanting XP at the end), Chronomatron (note chains, 3-10 colours), Ultrasequencer
      (numbers from 3 up on 7x3 to 9x4); add-on XP per step and milestone bonus clicks as the wiki gives them; Metaphysical
      Serum (3 doses, milestones one shorter each).
      Decided: Superpairs clicks 10/12/12/14/16/12 (Metaphysical doubles add-on clicks), Grand needs Enchanting 25, start
      costs 25/50/75/100/200/350 levels; 2 power-up tiles a board; prize weights per stake (XP pairs, bottles, table-max
      books, ultra-rare books one level higher from Supreme, Guardian pet by stake, Serum from Grand, Experiment the Fish
      at Metaphysical); 3 charges each back 24 h after use, add-ons need a charge but don't use one and cost their levels
      each play; charges bought back with levels only (no Bits yet); Ultrasequencer starts at 3 numbers shown 2 s + 0.25 s
      each. Not yet: Bits, RNG meter, dyes, Guardian/Enderman Slayer ultra-rare boosts, the serum's own SkyBlock XP.
- [x] **Pet abilities**: `tools/skyblock/import_pet_abilities.py` reads each ability's text (per rarity, numbers as {n})
      through pattern rules into effects, `skyrpg/skyblock_pet_abilities` (185 of 679 ability tiers on 48 pets): stats
      (in a place / at night / per nearby player / per skill or HotM level / per digit of a collection), percents of your
      own stats, stats from other stats, damage dealt to a mob kind or in a situation (close, far, missing health, alone,
      attacked you, mob level, bank), damage taken from a mob kind. `PetAbilities` (skyblock): a stat source and an
      `IncomingDamage` hit rule for the pet out, numbers at its level and stat rarity (`SkyBlockPets.number`); percents and
      conversions read the last totals minus the abilities' own share (no feedback). The skyblock module now depends on
      progress (collections). Decided: "per player within N blocks" counts you too; places we don't have yet never count.
      Not yet: the ~220 abilities with their own mechanics (Last Stand, Laserbeam, Stampede, wisdom towards a mob,
      minion speed, drop chances, dungeon/Crimson/Garden ones).
- [x] **Alchemy (brewing)**: research `docs/design/research/alchemy.md`. `tools/skyblock/import_potions.py` writes
      `skyrpg/skyblock_potions`: 44 brewable potions from NEU's POTION_<TYPE>;<LEVEL> items (each level's base time, rarity,
      text, stats and special effects read from it), 51 ingredients, the modifiers. In SkyBlock worlds a brewing stand
      runs `SkyBrewing` (mixin over `serverTick`; accessor for its timer): 20 s, no fuel, water + Nether Wart = Awkward,
      ingredient = potion I/III/V, one level and one time modifier, Redstone Lamp both, Gunpowder splash (x0.5 time,
      Enchanted Gunpowder x1); right-clicking opens `BrewMenu` + `BrewScreen` (the stand's own slots and timer). Hypixel's
      POTION item with its data (`Potions`: name, text with time, colour, rarity, splash model). Alchemy XP per ingredient
      (Hypixel's INGREDIENT_BREW) rides on the potion and is paid when it's taken out. `PotionEffects`: right-click to
      drink (splash: every player within 8 blocks), time x (1 + Alchemy level % + Potion Affinity 10/25/50%), counts
      down online, kept through death, a lower level doesn't replace a higher; stats source; absorption, healing and Mana
      per second, vanilla Night Vision / Invisibility / Blindness / Jump Boost; miss chance, bow damage, fire and drowning
      in `IncomingDamage`; `/effects`. Brewing stands can be placed on islands.
      Decided: ingredients other than Speed/Strength/Regeneration/Healing's (the wiki gave only those) from Hypixel's
      Brewing Ingredient items and their XP tier (Cactus=Resistance, Mutton=Mana, Gold=Absorption, Cocoa=Adrenaline,
      Flint=Critical, Enchanted Cake=Agility, Feather=Archery, Salmon=Dodge, Rabbit's Foot=Rabbit, Raw Rabbit=Jump Boost,
      Coal=Haste, Mithril=Spelunker, Enchanted Rabbit Hide=Pet Luck, True Essence=True Resistance, Enchanted Glacite=Cold
      Resistance, Ink Sac=Blindness, Netherrack=Wounded, Red Sand=Burning, Lapis=Experience, Slimeball=Knockback,
      Obsidian=Stun; Foul Flesh unused); Enchanted Redstone Block +40 min (the wiki disagreed with itself); drinking is
      instant (no bottle back); glowstone modifiers give the XP Hypixel's item data lists.
      Not yet: Witch pet speed, God/Dungeon potions, Mixins, Potion Bag, Stun/Knockback/Burning/Experience/Wounded/
      damage effects.
- [x] **Quiver**: `Quiver` (skyblock): per-profile 45-slot quiver (`/quiver`, `QuiverMenu` + `QuiverScreen`, saved on
      every change); arrows in the inventory move in every second; `PlayerQuiverMixin` makes `getProjectile` return the
      selected arrow type from the quiver when the inventory has none (on both sides: the arrow to shoot is a synced
      attachment, so the client draws the bow); `QuiverAmmoMixin` takes one out per shot (`useAmmo`; Infinite Quiver
      3%/level saves it). A SkyBlock bow's arrow (`ENTITY_LOAD`) is tagged with 5 + bow Damage + arrow Damage times the
      draw (`SkyArrows`, then Strength and crits as melee): SkyBlock bows did vanilla arrow damage before. Icy Arrow
      slows, Glue Arrow roots. Arrow Swapper cycles the type. `devtools useitem` added (it doesn't draw bows on the
      camera yet; tested with a summoned arrow owned by the camera player).
      Decided: 45 slots; selection only via the Arrow Swapper.
      Not yet: arrow requirements, Bouncy/Armorshred/Explosive/Nansorb passives, arrow poisons, the hotbar quiver slot.
- [x] **Glacite Tunnels** (part 1): research `docs/design/research/glacite-tunnels.md`. `tools/skyblock/gen_glacite.py`
      writes a generated world `skyrpg_skyblock:glacite_tunnels`: 256 x 256, rock y 96-176, winding tunnels (two crossed
      3D noises) and some caverns between y 104 and 170, snow floors, packed-ice ceilings, veins of Glacite (packed ice),
      Tungsten (cobblestone, clay), Umber (terracotta, brown terracotta, red sandstone), Mithril, Titanium and the four
      Tunnels gemstones (blue/black/brown/green stained glass), ice; zones (blocks to bedrock, back after 20 s) split
      around the Dwarven Base Camp (built by a helper, `tools/builds/hub_areas/glacite_base_camp.py`; NPCs in
      `npcs.py`; `/warp base`). `SkyMining` Tunnels table: Glacite 6,000 / Tungsten and Umber 5,600 / gems 5,200, all
      Breaking Power 9, Hard Stone 50/BP4; Glacite 2 and Tungsten/Umber 1 Glacite Powder, gems 4 Rough + 2 Gemstone Powder.
      `GlaciteTunnels`: Ticket Master (Dwarven Mines, by Gwendolyn) needs HotM 7 and, once, a Secret Railroad Pass; the
      Base Camp's Ticket Master goes back; Cold (synced attachment on the sidebar) builds 1 per 5 s x (1 + CR/100), x4
      in water, -0.5% Mining Speed per point over Cold Resistance (a stat source), warnings at 25/50/75/90, slowness at 75
      and 90, at 100 you freeze back to the Base Camp; the Base Camp and leaving reset it. Commissions refactored to an
      `Area` (Mines, Hollows, Tunnels): Emissary Lissandra's board (collect 1,500 of each resource or gem, 50 Glacite
      Walkers; 400/750 HotM XP, 1,500/3,000 Glacite Powder, 25,000 Mining XP). `/sbtunnels go|cold` for admins.
      Decided: our own map (Hypixel's layout isn't data we have); 20 s regrow; gems 4 Rough a block (Hypixel 3-5);
      powder per block numbers; freezing is a teleport, not a death; Cold Enjoyer & co. only talk.
      Not yet: fossils and the Fossil Excavator, Glacite Powder perks, the Great Glacite Lake, Grandpa Wolf, the Fossil
      Muncher.
- [x] **Glacite Mineshafts** (Glacite Tunnels part 2): `glacite/Mineshafts.java`. Mining in the Tunnels counts down a
      pity of 2,000 (Mithril 2, Glacite/Tungsten/Umber 4, gems and Titanium 8, Hard Stone 0) beside a 1 in 2,000 roll per
      block; a find opens a portal (Interaction + nether-portal block display + label, particles) for 30 s. Each shaft
      is built in its own 512-block slot of an empty world `skyrpg_skyblock:glacite_mineshafts` (32 slots): a 60-long
      tunnel with three side rooms, rock with 32% of the shaft's ore, timber frames, lanterns, a bedrock shell and a
      ladder exit. 16 kinds (Titanium, Tungsten, Umber, the 12 gemstones with Opal new, Fairy = every gem); the kind you
      last mined comes up a third of the time. 1-3 Frozen Corpses (invisible armour stands in NEU's corpse armour;
      `tools/skyblock/import_corpses.py` writes `skyrpg/glacite/corpses.json`, 160 loot entries): Lapis free, Umber and
      Tungsten need their keys, a Fairy shaft's Vanguards a Skeleton Key; rolls are weighted picks, merged in one panel.
      Three Glacite Maniacs (Mutt wolf, Bowman stray, Caver husk, Mage zombie; level 173-177, 750k-825k HP): only
      mining tools hurt them, 8% of max health a hit; drops 50 Glacite Powder, Enchanted Glacite, Fine Tunnels gems 5%,
      Glacite Jewel 4%, Glacite Shard 0.1%, their coins; no vanilla loot. Suspicious Scrap 1 in 200 per block mined,
      halving per scrap found. Cold keeps building, +0.25 vulnerability per 150 s inside. The exit pays 1,000 Glacite
      Powder +500 a scrap, +250 a corpse, +10 a point of Cold. An empty shaft is cleared after 60 s; the server stopping
      sends everyone inside to the Base Camp and clears every shaft. `/sbmineshaft portal <player>` for admins.
      Decided: our own shaft layout (Hypixel's rooms aren't data we have); no Littlefoot kind (no data for it); the
      Glacite Mage is a zombie; corpses stand (frozen upright) rather than lie down.
- [x] **Glacite part 3: perks, mineshaft commissions, the Fossil Research Center**. HotM stat source: Warm Heart (Cold
      Resistance), Metal Head (Dwarven Metal Fortune), Mining Master (Pristine), Miner's Blessing (+30 Magic Find on
      mining islands), Rags to Riches / Eager Adventurer (Fortune / Speed in mineshafts); Strong Arm through a synced
      `Hotm.STRONG_ARM` value read by `SkyMining.progress` on both sides (Dwarven Metals only). `Mineshafts`: Surveyor,
      No Stone Unturned, Gifts from the Departed (one more roll), Dead Man's Chest (one more corpse on first entry),
      Vanguard Seeker (Fairy kind), Mineshaft Mayhem (one of five buffs per visit, a stat source; the cooldown buff
      through `Mineshafts.cooldownFactor`). `Commissions`: Daily Grind (500 x HotM level powder for the day's first
      commission per island, a `daily_grind` attachment); Tunnels tasks Mineshaft Explorer (1), Corpse Looter (2),
      Scrap Collector (1), Maniac Slayer (10). `essence/Essence`: Hypixel's nine essences as a profile currency
      (`/essence`, `/sbessence give`). `glacite/Fossils` + `ExcavatorMenu` + `ExcavatorScreen`: the Fossil Excavator NPC
      (HotM 7) takes the best chisel (16/18/20/22 charges, highlight 25% / 60%) and a Suspicious Scrap; a 9 x 6 dig on
      the server, 8-11 treasures (Fossil Essence, Glacite Powder, Enchanted Glacite/Umber/Tungsten, Flawed/Fine gems,
      Paleontologist I, Fossil the Fish), a fossil one dig in ten (six shapes); uncovering dirt can highlight a treasure
      (or more of a found fossil); a whole fossil is kept, a part pays Fossil Essence; the rest of a missed fossil shows
      at the end. Dr. Stone takes each fossil once (25k Mining XP, 25k Glacite Powder, 5k HotM XP, 10 SkyBlock XP; all
      seven +50k / +10k). Researcher Beryl (chat buttons, Base Camp only): scrap for 100 Fossil Essence, salvage one scrap
      (5) or already-donated fossils (50, Claw 25). Admin: `/sbhotm perk|stats`.
      Decided: highlight chances (Hypixel's aren't documented beyond Reinforced's +60%); fossil shapes are ours; treasure
      weights are ours from the wiki's list; no Fossil Dust; chisel gem slots later.
      Not yet: Steady Hand, Crystalline, Gemstone Infusion, Sheer Force (Pristine and Mining Spread have no effect yet),
      the Fossil Muncher, the Great Glacite Lake, Grandpa Wolf.
- [ ] **Runecrafting and Social** (decided 2026-10-17: deferred). Both are cosmetic on Hypixel (runes are particle
      effects; Social barely gives anything), so they wait until the gameplay systems are in. NEU has the 147 rune items
      (type, tier, slot, Runecrafting level, text) for when they come.
- [x] **Chat panels** (owner ask, 2026-10-16): a notice with lines under it goes out as ONE message (`Notice.Panel`,
      marker `skyrpg_chat.panel` with its colour); the chat mod paints it as a card (`ChatPanelLook`). Used by skill and
      SkyBlock level ups, collection tiers, unique minions, /pet and /pet storage. Use panels for every multi-line
      message from now on.
- [x] **Fairy Souls** (`FairySouls`, `skyrpg/fairy_souls/<island>.json`): 24 on the Hub's islands (3-4 each, from the
      area scripts' spots), a turning pink crystal with a click box, spawned while a player is near, sparkling for who
      hasn't found it; "SOUL! You found a Fairy Soul!" panel with island and total counts; per profile. Tia the Fairy
      (`fairy_exchange` action) trades every 5 for 10 SkyBlock XP (Hypixel's backpack slots wait for backpacks; the
      exchange count is kept). `/fairysouls give|reset` for admins.

## Phase 3: Dungeons

- [x] **The Catacombs, part 1: Floor I end to end** (design `docs/design/catacombs.md`, research
      `docs/design/research/dungeons.md`). `dungeon/`: `Layout` (Hypixel's grid of 32-block cells: Entrance on an edge,
      Blood room farthest, Fairy and later puzzle/trap rooms as dead ends, normal rooms in 1x1..1x4, 2x2 and L shapes, a
      random spanning tree of doors plus loops, Wither doors on the way to Blood with keys in rooms before them, the Blood
      door), `RoomLibrary` (templates from `structure/catacombs/rooms/<category>/`), `Run` (a slot of the new
      `skyrpg_skyblock:catacombs` world, x = slot x 2048; built a room a tick with rotation, markers read and removed;
      gap walls; doors cut, Wither = coal, Blood = red terracotta; mobs, secret items and bats spawn on first entry;
      star mobs clear rooms; keys drop on clear and are picked up by walking in; locked doors opened by right-click
      with the key; the Watcher and 6 + 2 x floor blood mobs in waves, then a portal on his dais; ghosts; score and
      the end), `DungeonMobs` (roles zombie/skeleton/crypt/spider/elite -> Hypixel names, health and damage
      geometric between NEU's F1 and F7 numbers; ✯ star mobs; no vanilla loot), `BossFight` (Bonzo: 250,000, balloons,
      minions, gets back up once; other floors use it with their Hypixel health; a plain arena until boss templates),
      `Score` (Skill, Explore with the floor's secret share, Speed by the floor's allowance, ranks), `Rewards` +
      `RewardsMenu` + `DungeonRewardsScreen` (Wood/Gold/Diamond/Emerald/Obsidian/Bedrock by rank and floor price, F1
      loot table with rank luck, one chest a run), `DungeonXp` (Catacombs + 5 class levels on NEU's table, 20/40 SkyBlock
      XP a Catacombs level; classes 25% when not played), `DungeonMenu` + `DungeonScreen` (Mort: class and floor),
      `Catacombs` (start with the party up to 5 and level checks, ticks, clicks, ghosts, leaving on world change,
      cleanup, the synced sidebar HUD, `/sbdungeon leave|class|start|menu|debug|xp`), `Secrets` (essence or dungeon
      consumables). `essence/Essence` is the currency for it. Rooms: `tools/builds/catacombs/room_kit.py` (the rules
      and checks) with 18 rooms by helpers (`rooms_core.py`: Entrance, Blood, Fairy, five 1x1; `rooms_multi.py`: three
      1x2, two 1x3, one 1x4, two 2x2, two L). The Dungeon Hub (`tools/builds/hub_areas/dungeon_hub.py`, its own world,
      `/warp dungeon_hub`) with Mort, Ophelia (her shop), Malik, Croesus, Adventurer Saul. Fixed on the way: SkyBlock
      heads were named "'s Head" in chat (`PlayerHeadNameMixin`); an attachment type must be registered before a
      player's data loads (DungeonXp's wasn't, so its data was dropped).
      Decided: mobs spawn when a room is first entered (cheap); rooms overwrite the slot (no clearing); stale run
      entities are removed when they load; ghosts are flying, invisible, invulnerable players (no gamemode change); a
      full-party death fails the run (a quarter of the XP, no chests, back to the Dungeon Hub); keys are picked up by
      walking into them; F1 mob numbers from NEU's lowest values; F1 chest odds ours.
      Not yet: more puzzles, traps, minibosses, crypts, the Mimic, blessings, the Fairy's revive, class abilities and
      passives, the dungeon map item, boss arenas and F2-F7 bosses, Master Mode, dungeon item stats, stars, essence
      shops (Malik), Croesus, Saul's Recommendation, Party Finder.

- [x] **Puzzles, first three** (`dungeon/Puzzles`; rooms `tools/builds/catacombs/rooms_puzzles.py`, a `puzzle(kind)` marker and
      its clear space checked by the kit). Built when someone walks in. Three Weirdos: villagers with NoAI before three
      chests; statements generated (my chest / not my chest / X's chest / not X's chest) until exactly one is true for
      the answer and no other chest fits; the right chest solves it, a wrong one fails it. Higher Or Lower: ten NoAI
      blazes with SkyBlock health over their heads, ascending or descending (the sign); out of order fails it and the
      rest vanish. Tic Tac Toe: a 3 x 3 board of concrete floor tiles, the AI opens and plays minimax; a draw or win
      solves it, a loss fails it. Solving clears the room and gives a secret's reward; failed or unsolved puzzles cost
      14 Skill each. Also fixed: an L turned 180° didn't cover its own anchor cell, so a map cell could be left empty
      (crashed the build).
      Decided: Hypixel's Weirdos lines are fixed riddles; ours are generated with the same "one tells the truth" rule.

- [x] **Dungeon classes** (`dungeon/Classes`, `mixin/DungeonDropMixin`): in a run the drop key is the class ability and
      sneak + drop the ultimate (`ServerPlayer.drop` cancelled, the slot resynced); cooldowns; passives as a stat source
      (Mage +100 Int +3/level and -25% cooldowns, Berserk +20 Str +1/level, Archer +10 Crit Damage +1/level, Tank +100
      Health/+50 Defense + level; Healer's aura heals allies in 8 blocks 1%/s). Abilities: Healing Circle, Guided Sheep
      (blast), Throwing Axe (first mob in line), Explosive Shot (three blasts), Seismic Wave (scales with Defense);
      ultimates: Wish, Thunderstorm (visual lightning + damage), Ragnarok (+100 Str/Speed), Rapid Fire, Castle of Stone
      (-70% via `IncomingDamage`). Damage is indirect magic scaled by Int or Str like item abilities. Mort's class cards
      have a tooltip with all three.
      Decided: our numbers and cooldowns (Hypixel's class pages 404'd); drop = ability, sneak + drop = ultimate.
      Not yet: ghost abilities, Hypixel's full passive lists (Renew, Orbies, Bloodlust, Doubleshoot...), class milestones.

- [x] **Every floor's boss and loot** (`dungeon/BossFight` base: arena, spawning with Hypixel health, a boss bar, hits around
      a spot, phases via allowDamage/allowDeath/died/useEntity hooks; `dungeon/Bosses`): Bonzo (balloons, undead, gets
      back up), Scarf (shielded until his 4 undead die; blinks, burns, calls archers), The Professor (shielded behind 4
      Guardians; at half health 2 more), Thorn (a NoAI ghast hurt only by a Spirit Bow arrow, given by killing a Spirit
      Bear; 4 hits), Livid (9 coloured Livids, the real one's colour is the wool in the middle, hitting fakes hurts you),
      Sadan (60 s of Terracottas, 4 Giants of 25M that stomp, then Sadan 40M), Floor VII (Maxor shielded until both
      Energy Crystals reach their pads, Storm hurt for 10 s after a Pillar, Goldor's tick until 4 Terminals, Necron with
      frenzies at 80% and 5%; NoAI withers, their own bars). Loot by floor: `tools/skyblock/dungeon_loot.py` writes
      `skyrpg/dungeon_loot/loot.json` (Hypixel's drops from the RNG meter pools, our chances by rarity band and chest
      tier). Classes you didn't play get their quarter XP quietly. `/sbdungeon boss <player>` jumps to the boss.
      Decided: simplified mechanics (no terminals' mini-games, no pillars falling, no Livid's abilities, Giants move by
      stomping hops); all bosses fight in a plain arena until their templates exist.

- [x] **Dungeon items, stars, Malik, boss arenas**. `ItemMods`: stars (`upgrade_level`, shown as ✪ after the name),
      `dungeonItem` (Hypixel's `dungeon_item`). `dungeon/DungeonItems`: in the Catacombs a dungeon item's stats (held and
      armour) go up 10% a star and 1% a Catacombs level (a stat source); star upgrades from the item's Hypixel
      `upgrade_costs` (essence, coins, items). `MalikMenu` + `MalikScreen` (Malik, `dungeon_malik`): the held item, its
      stars, the next star's cost, your essence, "Add a star". Boss arenas: `tools/builds/catacombs/arena_kit.py` (61 x 30
      x 61, a fight area that stays open for the fights' fixed spots) with helpers' `arenas_a.py` (Bonzo, Scarf, The
      Professor, Thorn) and `arenas_b.py` (Livid, Sadan, the Wither Lords).
      Decided: +10% a star and +1% a Catacombs level (Hypixel's numbers weren't retrievable).

- [x] **Master Mode** (`Floors.MASTER`: Floor records with `master`: Catacombs/Combat 22 + 2n, 480 s, all secrets, double
      chest prices, 3x XP; codes F1/M1 on the sidebar and titles). Mobs: Floor VII numbers x 1.5^n; bosses: Hypixel's
      Master Mode health (`BossFight.hp`), minions x40, Thorn 6 hits, boss damage 12,000 x 1.5^n. Completions per floor
      in the XP store (`done_f7`...) gate Master Mode; Mort's screen has a Normal / Master switch once Floor VII is
      done. Loot: floors 101-107 in `loot.json` (Master Skulls, the five Master Stars on M3-M7, the Dark Claymore on
      M7). Master Stars 6-10 at Malik: the five items in order on a 5-star item, +5% stats each in Master Mode, shown as
      ➊-➎. `/sbdungeon master <floor> <player>` for admins.
      Decided: Master Mode mob scaling and boss damage are ours (no single Hypixel multiplier); no Withered Dragons.

- [x] **Blessings, minibosses, crypts, the Mimic** (`dungeon/Blessings`: Life, Power, Stone, Wisdom, Time; a stat source
      adding shares of your stats, one share per stat with last time's taken off so they don't compound; level 1 + floor/2,
      +1 in Master Mode; from solved puzzles, minibosses and 1 in 5 secret chests). `DungeonMobs.miniboss`: Lost
      Adventurer (F2+), Angry Archaeologist (F3+), Shadow Assassin (F5+, double health), Frozen Adventurer (F6+), King
      Midas (F7); 60k to 7M health; 40% of rooms with an elite spot from Floor II. Crypt Undead: 30% of normal rooms, up
      to 8 a run, +1 Bonus each (max 5). The Mimic (F6+): a random secret chest; opening it releases a baby zombie with a
      chest head (1M+, 150M in Master Mode); +2 Bonus. Sidebar: Crypts and Mimic ✔.
      Decided: crypts are mobs (Hypixel's are walls you blow up; our rooms have no crypt walls yet).

- [x] **Malik's essence shop, the fairy** (`dungeon/EssencePerks`: Undead Health/Defense/Strength/Intelligence/Critical
      Essence and Wither Forbidden Health/Defense/Strength/Intelligence/Speed/Blessing, NEU's costs, our per-level numbers,
      a stat source in the Catacombs; Forbidden Blessing boosts blessings; an `essence_perks` attachment; a second tab on
      Malik's screen). The Fairy room has an allay; when someone alive reaches it while others are ghosts, it revives
      them all, once a run.

- [x] **Party Finder** (`dungeon/PartyFinder` + `PartyFinderScreen`, a button on Mort's screen): leaders list their party
      for E/F1-F7/M1-M7; others join (the party mod's invite + accept); listings expire after 30 min, when full, or when
      the leader goes offline.

## Phase 4: Late game

- [x] **The Crimson Isle** (`docs/design/crimson-isle.md`): dimension `skyrpg_skyblock:crimson_isle` (type
      `skyrpg_hub:public_crimson`: red fog, no sky, crimson spores, nether music; a flat generator's lava sea up to y 59).
      Three build scripts on `crimson_lib.py` (helpers): `crimson_west.py` (Dragontail, the Barbarian Outpost, the
      Dukedom, the Smoldering Tomb, the Wasteland, the Burning Desert, the Forgotten Skull), `crimson_center.py` (the
      arrival, the Crimson Fields, the Ruins of Ashfang, the Stronghold, the Blazing Volcano, the Magma Chamber),
      `crimson_east.py` (Scarleton, the Mage Outpost, the Cathedral and Courtyard, the Mystic Marsh, the Dojo, Odger's
      Hut); about 985k blocks; lava rivers and four bridges between them; 24 Fairy Souls. A launch pad on the Spider's
      Den's east edge (Combat XXII); warps crimson, dragontail, scarleton, smoldering, kuudra. 15 mobs with NEU's health
      and levels (damage/XP/coins decided), their drops (Blaze Ashes, Magma Chunks, Spectre Dust for Elle). Red sand,
      Sulphur (sponge) and mycelium break and regrow. The lava sea throws you back to the nearest warp (`CrimsonIsle`);
      magma blocks don't burn; any rod fishes the lava. `npcs.py` exports `spawn:` NPC spots and the island's warps to
      `skyrpg/spawn_spots/<island>.json`.
- [x] **Factions** (`crimson/Factions`, `FactionQuests`, `FactionMenu` + `FactionScreen`): Elle's questline (her
      supplies, then meeting Queen Nyx and Chief Scorn), joining and switching at the leaders, reputation kept per
      faction, Hypixel's ranks and their SkyBlock XP. Five daily quests (midnight UTC): two hunts, a miniboss, two
      errands for the faction's people (Pablo, Avorius, Mollim / Pomtair, Sirih, Suus: talk to them with the items).
      Banker, Auction Master and Bazaar NPCs in both towns (`open_auction`, `open_bazaar` actions); Alwin and Igor reforge.
      Decided: Hypixel's longer faction questline (Batphone, the stolen research) is left out; the dailies carry it.
- [x] **Minibosses** (`crimson/Minibosses`): Bladesoul, the Mage Outlaw, Barbarian Duke X, Ashfang, the Magma Boss at
      their spots, back two minutes after dying (while someone alive is near and the chunk is loaded), boss bar within
      48 blocks, a move every 5-8 s; 0.5% of its health in damage earns the Crimson Essence (25/50), Combat XP, coins,
      +20 reputation and the quest. The Magma Boss's cream, ooze and ember fragment. Not yet: the Damage Soaked meter.
- [x] **Inferno Demonlord** (`combat/slayer/Demonlord`, the fifth line in `import_slayers.py`): 4 tiers, Hypixel's costs,
      health, quest XP, drops and 9 levels (wiki pages; levels and the boss's own DPS decided where the infobox is
      blank); quest from blazes and magma cubes. Immolate (the wiki's per-phase shares), Demonsplit (Quazii, Typhoeus
      with NEU's health and DPS), the Hellion Shield (dagger attunement, `ItemMods.attunement`, right-click swaps,
      tooltip line), Fire Pillars, Apocalypse fire pits.
- [x] **Kuudra** (`crimson/Kuudra`, `KuudraRun`, `KuudraMenu` + `KuudraScreen`; arena `tools/builds/kuudra/arena.py`
      on `kuudra_kit.py`, helper-built): five tiers at the Kuudra Gatekeeper (faction + previous tier), up to 4 players,
      supplies -> build -> fuel and ballista shots that stun Kuudra (the only damage window), waves, slams, tier extras
      (tentacles, healing, enrage), 10-second downs, lava rescue; free and paid chests (`/kuudra chests`), keys at the
      Emissaries (`neu_extra_items.py` adds the 5 key items). Numbers decided where Hypixel's weren't found
      (docs/design/crimson-isle.md).
      Not yet: class perks and tokens, the Kuudra pet, attributes, Crimson essence stars, Croesus.
- [x] **Trophy Fishing** (`fishing/TrophyFishing`, `OdgerMenu` + `OdgerScreen`; `tools/skyblock/trophy_fish.py` writes the
      72 items from NEU and the fish table): lava rods only on the Isle; each eligible fish rolls rarest first at its
      chance x (1 + Trophy Fish Chance); conditions (zones, eruption window, geyser pools, sulphur nearby, bobber below,
      20 s bobber, 1,200 mana, plain Starter Lava Rod, carried Obfuscated bait, 8-minute streak); tier odds with Charm;
      pity at 100/600. The volcano erupts every 20 min (Gusher). Odger: counts, fillet to Magmafish, the four ranks and
      their armour, teeth, sacks and Diamond the Fish. 12 Crimson lava sea creatures in `import_sea_creatures.py`
      (NEU health/drops; Fishing levels, weights and XP decided). The geysers are small lava pools now; the Magma
      Chamber zone is only the cave.
      Not yet: Plhlegblast and its pool, Golden Fish visuals, trophy SkyBlock XP.
- [x] **The Dojo** (`crimson/Dojo`, on `KuudraMenu`'s list screen): Master Tao's seven tests adapted to the walled arena,
      grades, belts with Combat XP. **Vulcan**: Crimson stars on Kuudra armour and belts (`DungeonItems.crimson`, Malik's
      screen as "Vulcan"), +2% stats a star.
- [x] **Crimson extras** (`Vanquisher`, `CrimsonPerks`, `Duels`): the Vanquisher (1 in 640 kills on the Isle, pity at
      640/960/1,280, a 5M-health Wither, 3 Corrupted Nether Stars), Vulcan's Crimson Essence shop (Hypixel's eight perks
      and costs; Crimson Training, the Fortunas, Wither Piper and the Kuudra perks wired to our Kuudra), Rollim's and
      Kaus's seven daily duels (25 reputation a win, a Bag of Gold on day 7). The faction sidebar line was already there.
- [ ] Kuudra's in-run token shop (Elle's 19 perks over four routes) with a fuller Kuudra fight.
- [x] **The Garden, part 1** (`garden/*`, `docs/design/garden.md`): the per-profile Garden dimension and 5 x 5 plots,
      the Barn plot (helper-built template), plot unlocks with Compost/Bundles and their Fortune, Garden levels (NEU),
      crop milestones (NEU thresholds), visitors with offers and Copper, the Composter and its upgrades, SkyMart; the
      Desk's tabbed screen (`GardenMenu` + `GardenScreen`); sidebar plot names; costs from sacks too.
- [x] **The Garden, part 2** (`Pests`, `Contests`): the ten pests (Garden V+, 0.2% a crop, 5-minute cooldown), vacuums,
      the Farming Fortune penalty, Pest Repellent, Pesthunter Phillip's shop; Jacob's Farming Contests on the calendar
      (Farming X, Hypixel's brackets plus score floors, tickets and medals claimed at Jacob, the sidebar), Anita's shop and
      medal trades.
- [x] **The Garden, part 3** (`Sprays`, `GardenChips`): the Sprayonator (materials, plot sprays, weighted pests), the ten
      Garden Chips (redeem, rarities, Sowdust levels, `/chips`; SkyMart, Anita, visitor and pest sources).
- [ ] The Garden, part 4: the Greenhouse (crops, growth, water, mutations), Barn skins.
- [x] **The Rift, part 1** (`rift/*`, `docs/design/rift.md`): the Hub's Wizard Tower and its portal, Dimensional
      Infusions, the Rift dimension, Rift Time on the health bar and sidebar, Rift Damage combat, Motes (mobs, orbs, the
      Motes Grubber), Enigma Souls and the Enigma Cloak, the Wyld Woods with the Rift Wizard Tower and the Black Lagoon
      (helper-built), the Argofay/Shifted/Kay/Roy shops; the RIFT screen theme.
- [x] **The Rift, part 2** (`RiftWorld`, `LeechSupreme`, `Timecharms`): area gates (Rift Time, Timecharms), the eight
      Timecharms as items with NEU's recipes, Elise securing them (+100 SkyBlock XP, the Rift Necklace), the Leech Supreme
      (four phases, a boss bar, fragments and Motes), Odonata bottling, Lil' Pads, the slime launchers; West Village, the
      Dreadfarm and the Village Plaza with Barrier Street and Deja Vu Alley (helper-built), Cryptosis's soul, Seymour.
- [x] **The Rift, part 3** (`Porhtal`, `Montezuma`, `RiftFlags`): the Living Cave and Living Stillness, the Colosseum,
      the Photon Pathway, Stillgore Chateau with the Oubliette and the Mountaintop with the Time Chamber (helper-built,
      laid out so each meets another); their gates and drain speeds; Hearts in the Chateau; Porhtal's seven eyes and fast
      travel; Montezuma's nine pieces; 51 Enigma Souls in all.
- [x] **The Rift, part 4** (`RiftQuests`, `Bacte`, `VampireSlayer`): Living Metal mining, Barry's protesters and pen
      (the Citizen Timecharm), Bacte in the Colosseum (fragments for the Globulate), the Vampire Slayer (tiers I-V, Maddox,
      Hearts, Mania, Twinclaws, the Steak Stake, levels and drops; Bloodbadges for the Vampiric).
- [x] **The Rift, part 5** (`Dreadfarm`, `Mirrorverse`, `WestVillage`): the Dreadfarm's crops (Wand of Farming,
      Agaricus Cap, Caducous Stem, Wilted Berberis), Shania and the Proto-Chicken, the Berberis Blowgun and Not Chickens,
      the Rift collections, the eight-room Mirrorverse (Dr. Emmett, ttemme .rD, the mirrored Timecharm, its talismans), the
      Plaza's two-charm gate, Gunther's race, the hot dog contest, puzzle-locked Enigma Souls (balloons, buttons).
- [x] **The Rift, part 6** (`Ubik`, `Reed`, `Kat`, `Mountaintop`): Ubik's three games, Reed's boat, Kat's Infested House
      (the Turbomax Vacuum, the Vermin Bin, her trades and the soul key), Timite (Youngite ore, the Time Gun, the
      Anti-Sentient/Eon/Chrono Pickaxes), Shen's Ringalia, Obo, Shen's Hive Mind (the Celestial Timecharm, the Golden Belt).
- [x] **The Rift, part 7** (`SunGecko`, `CowboyNick`, `ShenAuction`): the Sun Gecko (attacks, combo, seven modifiers,
      Sun Gecko Essence and Oruo's shop, the Blessing of Time), Cowboy Nick (the Horsezooka, the Half-Eaten Cave's hay,
      the rabbit and carrots), the Rift Necklace's stats, Shen's Auctions.
- [ ] The Rift, part 8: the Rift Gallery (done: `Gallery`, `rift_gallery.py`); still the Living Metal snakes, the
      Wizardman Trials, the Eleanors' outfit puzzles, Detective Amog, Kloon, Okron, Seraphine and the remaining souls.
- [x] **Galatea, part 1** (`galatea/`, `galatea_north.py`, `galatea_south.py`, design in `galatea.md`): Moonglade
      Marsh in its own dimension (Tangleburg, the Evergreen Plateau, the Reaches, the Forest Temple, the Dragon's Lair,
      Murkwater Loch, Shallows and Outpost, the Wetlands, the Squid Cave, SwampCut Inc.), the Park's Foraging XII pad,
      Fig and Mangrove trees (toughness, Sweep, cutting order, Tree Gifts by share, regrowth), the Hunting skill and its
      stats (Hunting Fortune, Charm Chance, Hunting Wisdom), all 325 shards from NEU, the Hunting Box and attributes,
      30 critters with the lasso and the fishing net, Charm Chance on mob kills, Galatea's NPCs and the Galatea theme.
- [x] **Galatea, part 2** (`Hotf`, `HotfMenu`, `HotfScreen`, `Rosemary`, `Starlyn`, `import_hotf.py`): the Heart of the
      Forest (NEU's 36 perks and tiers, Tokens of the Forest, Forest/Desert Whispers, axe abilities, thrown axes, the
      Timber stat), Rosemary's Forest Essence shop and perks, Agatha's Starlyn Contests with brackets, rewards and her
      coupon prize shop; Hina's Galatea menu.
- [x] **Galatea, part 3** (`Fusion`, `Huntraps`, `BlackHole`, `Resources`, `Temple`): Kysha's shard fusion, Dax's
      Huntraps (real-time catches, saved), the Pocket Black Holes, Lushlilac and Sea Lumies (picked, regrown, Charm
      Chance to eat), the Forest Temple's offerings, the Jade Dragon's pet sale.
- [ ] Galatea, part 4: Torrhus Canyon (Helix trees, Desert Whispers, Miria's contests, the Critter Safari), the
      Huntaxes' Absorptio, Lotus Atoll.
- [ ] Backwater Bayou.

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
