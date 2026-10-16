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
- [ ] Minion slots from unique crafts, crafting minions (with the Recipe Book).
- [ ] Collections 1:1 with the Recipe Book and enchanted materials.
- [ ] Skills on Hypixel's XP tables and level rewards (NEU constants/leveling.json).
- [ ] Islands: the Hub, Farming Islands, The Park, Gold Mine, Deep Caverns, Spider's Den.
- [ ] Bazaar and Auction House.
- [ ] Slayers: Revenant, Tarantula, Sven.
- [ ] Fairy Souls and SkyBlock XP.

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
