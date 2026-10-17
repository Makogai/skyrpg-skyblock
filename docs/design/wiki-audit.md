# Wiki audit for SkyBlock (2026-10-17)

Scope: every hand-written page outside the `skyblock` category (66 pages) plus the generated reference pages. Sources: `docs/design/skyblock.md`, `skyblock-progress.md`, `retireSurvival` in `SkyRpgSkyblockClient`, `Features`, and the code and data named in the notes. Nothing was edited except this report.

Totals: KEEP 0, UPDATE 19, REWRITE 16, RETIRE 31.

## Biggest findings

1. The wiki still mixes two games: gear, dungeons, world and companions (and most of combat, skills, skyhaven, economy) describe the survival pack. No audited page is KEEP; only 19 are small UPDATEs.
2. SkyHelper answers from the built wiki bundle (`tools/skyhelper/knowledge.py`), so stale survival pages are quoted back to players as fact. Retiring pages matters for the assistant, not just readers.
3. The generated item reference pages are wrong for SkyBlock: `gamedata.item_page` routes by survival item kinds, so about 5,600 Hypixel items land in `items-materials` (2.1 MB, ~11,000 item refs) and none in `items-weapons`/`items-armour`/`items-accessories`, which still list the 257 survival items.
4. Survival currencies still drop with nowhere to spend them: `MiningDrops` (Glimmer), `BonusDrops` (Dew), `ForageDrops` (Heartwood) and fishing Tidescales are not gated by dimension, while the Masters' shops are gone.
5. Survival routes into SkyBlock remain live in code: `/survival` (works from the Hub), `/pay`, `/quest`, `/title`, `/contracts`, `AutoEvents` random events, spell-wheel keybindings. Retired pages and these commands should agree.

## Getting started

| page | verdict | notes |
| --- | --- | --- |
| `welcome` | REWRITE | Install, Prism and join steps stay valid. The rest is survival: "The two worlds" (Skyhaven + survival world via the Wildgate or `/survival`; the hub gate now sends you to your Private Island, `SkyRpgSkyblock` `Hub.setGate(... Islands::sendHome)`); "seven skills" (Hypixel's 9 are in); Hollis questline, Journal, contracts, talents, dungeons, danger regions, griffins, Delving, Artisan Bench in "What is in the pack"; "coins are kept on death / you wake up in Skyhaven" (now keepInventory, half the purse lost, respawn on your island: `Deaths.java`). SkyBlock version: Hub + Private Island + public islands, profiles/co-ops, minions, collections, Bazaar/Auction House, slayers; link the skyblock pages. |
| `skyrpg-mods` | UPDATE | Says 29 mods; settings.gradle has 31 (missing rows: `skyblock`, `minions`). Wrong rows: Hub (now Skyhaven + 7 floating areas + public islands), NPCs ("quest givers"), Journal ("Chronicle questline": quests are off, the progress mod is now collections, SkyBlock Level, Fairy Souls), Items ("Artisan Bench"), Mining/Farming/Foraging (Glimmer, planters, Heartwood are survival), Magic (wand/spell wheel retired), World (dungeons, danger regions, waystones), Mounts (retired), Combat (Elites, world bosses), Events. Links to retired pages. The picture `skyrpg-mods.png` needs regenerating. |
| `first-launch` | UPDATE | Title screen and the six setup steps are the welcome mod and unchanged. Fix: step 4 (Controls) names the spell wheel key and backpack key as the SkyRPG keys (spell wheel is retired); step 1 "what SkyRPG is in two lines" should be re-read for survival wording; mention that FurfSky Reborn item looks are always on (no setting, `ToggledPacks.alwaysOn`). |
| `first-steps` | REWRITE | All of it is the survival start: Skyhaven Chronicle chapters 1-2 (Hollis, Journal, contracts), the Wildgate walk, Artisan Bench, survival tools, Provisioner coins. SkyBlock version: you start on your Private Island (`/is`, `Islands.sendHome`), open the SkyBlock Menu (`SkyBlockMenuPage`), walk through the Hub gate and the sky bridges to the Farm / Forest / Coal Mine / Graveyard (`HubAreas`, launch pads need Farming/Foraging/Combat/Mining I), collect, unlock collections and the first minion, meet Tia for Fairy Souls, sell at NPC shops, Bazaar at SkyBlock Level 7. |
| `controls-and-keys` | UPDATE | Remove or fix: K row "Profile, Skills, Journal" (now SkyBlock Menu first); the spell wheel keys (`key.skyrpg_magic.wheel`, `wheel_toggle`) and "Casting with a wand" (wheel/HUD retired); "Riding a griffin" section and sneak/jump steering note; "At the bank and on the Pay page" and `/pay` row (Pay page retired; the `/pay` command itself is still registered in `EconomyCommands`, so also decide whether to switch it off); `/survival` row (still registered in `HubCommands`, hub-only, an archive route not part of SkyBlock); `/quest`, `/title`, `/contracts`, `/region`, `/layer` rows (quests and contracts off, titles/regions survival). Missing commands to add: `/is` `/island`, `/warp`, `/visit`, `/profiles`, `/coopadd`, `/coop accept`, `/storage`, `/ec`, `/wardrobe`, `/craft`, `/bz`, `/ah`, `/sblevel` (and admin `/fairysouls`, `/sbitem`). Keys that stay: K menu, M, = and -, B backpack (backpack slot still exists), J brainrot album, H wiki look-up, Tab. |
| `mods-and-shaders` | UPDATE | Mostly valid (client utilities). Fix line 10 ("the Ender Wyrm and the griffins" as tested features) and line 68 ("spell and boss particles"); the Resource packs section should add that Catharsis (client mod) and FurfSky Reborn (resource pack, only its item folders load, `FurfSkyFilter`) ship in the pack from Modrinth (`tools/pack/modrinth.json`) and are always on. |
| `progression-guide` | REWRITE | Eight survival stages from Skyhaven to the Ender Wyrm: Delving, danger regions, the Deep, Wildgate, Artisan Bench, mob level ladder by distance from spawn (`MobLevels` skips hub; zone mobs have their own levels). SkyBlock version: the Phase 1 loop from docs/design/skyblock.md: Private Island, Hub areas, Farming Islands (Barn then Mushroom Desert, Farming V pad), Park, Gold Mine, Deep Caverns (Mining V pad), Spider's Den, minions, collections, Slayers (Revenant, Tarantula, Sven, Voidgloom), Bazaar and Auction House, SkyBlock Level unlocks. Gates and levels come from `skyrpg/launch_pads/*.json` and the zone files. |

## Skyhaven

| page | verdict | notes |
| --- | --- | --- |
| `skyhaven` | UPDATE | The village build and NPC spots are unchanged, but: "separate from the survival world", the Wildgate section and `/survival` line (the gate now sends you to your Private Island, message "Warping you to your island..."); "nobody can fight, break blocks, place blocks" (the 7 floating areas allow regrowing blocks and have hostile zone mobs: `Hub.BreakRule`, `Hub.CombatRule`); every NPC name (Hollis, Cassius, Brannoc, Petra, Kestrel, Odessa, Pip, Dorrin, Marina, Morwen, Tamsin, Mabel, Oswin are now Bartender, Banker, Rosetta, Wool Weaver, Weaponsmith, Librarian, Alchemist, Combat Merchant, Fishing Merchant, Maddox the Slayer, Mine Merchant, Farm Merchant, Lumber Merchant); "public Artisan Bench in the Emberforge yard" (benches are survival, `/craft` and crafting tables open the SkyBlock grid); Stargazer advancement/500 coins (advancement messages are off, check it still pays); the Quick map misses the areas around it (link `the-hub`) and Tia's Fairy Souls. Verify the 60% hub speed still applies (`hub.json` speed config). |
| `npcs` | REWRITE | 14 survival NPCs and their quests; SkyBlock version is 65 NPC definitions in `npcs/src/main/resources/data/skyrpg_npcs/skyrpg/npcs/*.json`: Hub merchants (action `skyblock_shop`: Farm, Lumber, Fishing, Mine Merchant, Weaponsmith, Combat Merchant, Wool Weaver, Bartender, Alchemist, Librarian, Rosetta, Pat, Scoop, Shifty, Lucius, Lonely Philosopher), Banker (`open_bank`), Seraphine (still `open_enchanting`), Maddox (`open_slayer`), Tia (`fairy_exchange`), Lift Operators (`lift`), plus island NPCs (Jake, Windmill Operator, Old Shaman Nyko, Master Tactician Funk, Iron/Gold Forger, Walter, Shaggy). Quest markers (`!`/`?`), the five Masters and their currencies (Glimmer, Dew...) no longer exist. |
| `quests-and-journal` | RETIRE | `Features.QUESTS` off (`SkyRpgSkyblock`), Journal page and quest tracker HUD retired in `retireSurvival`. (`/quest` is still registered but nothing gives quests.) |
| `events` | RETIRE | Owner call: the engine is alive (`AutoEvents` still starts a random Timber Rush / Monster Hunt / Harvest Rush / Glimmer Rush / Fishing Frenzy every 60-90 min with 2+ players) but every event is a survival-world competition, and the page tells players to go to the survival world. SkyBlock events are the calendar (see `calendar-and-events`). Either switch AutoEvents off with a `Features` flag and retire this page, or keep it as an extra and fix the survival wording. |

## Economy

| page | verdict | notes |
| --- | --- | --- |
| `coins-and-bank` | UPDATE | Bank works (Banker NPC `open_bank`, `Bank.java`) but it is the survival bank: 1% daily interest capped at 5,000 (Hypixel pays 2% per 31 hours with higher caps; the Personal Bank tile in the SkyBlock Menu is still grey). Fix: Cassius Vellum is now the Banker; "Paying other players / Pay page / command" (Pay page retired; `/pay` still registered); "coins come from quests, contracts, events"; "kept when you die" (half the purse is lost now); links to contracts, quests, events. |
| `contracts` | RETIRE | `Features.CONTRACTS` off, Contracts page and HUD retired. |
| `selling-and-shops` | RETIRE | 26 KB of survival shop lists (Ashforge, Quarrystone, Quill, Glimmer prices; economy `skyrpg/shops` "no longer used by the Hub"). Superseded by `skyblock/npc-shops` (NEU's 88 shops, `SkyShops`). Move the useful bits (selling by clicking a stack, buyback) there; `/sell`, `/worth` commands still exist in `EconomyCommands`, mention them in npc-shops if they are meant to stay. Note `reference/sell-prices` links here. |

## Skills

| page | verdict | notes |
| --- | --- | --- |
| `skills-overview` | UPDATE | Already Hypixel's (9 skills; Runecrafting, Social, Hunting are not built and the page says nine, fine). One wrong line: "Some collection tiers also give skill XP (+1,000 Mining Experience)": progress notes say those unlock lines grant nothing yet. Alchemy XP source is still missing (page says so). |
| `mining` | REWRITE | SkyBlock version: Mining XP from `skill_xp/mining.json` on Coal Mine / Gold Mine / Deep Caverns zone blocks (`Zones`, regrowing stand-ins), Mine Merchant (`mine_merchant` shop), Hypixel stats (Mining Speed/Fortune, Breaking Power, Spread), minions; Dwarven Mines are not built. Survival-only content to drop: Glimmer Shards/Crystals (`MiningDrops` still drops them on any block break in any dimension, with no shop left to spend them in), Prism Fragment/Heartstone rare finds, ore nodes and `NodeBlock` ("buy from the Mining Master"), Tamsin's shop, pickaxe ladder (Skyiron...), mining armour, the Talents table (page retired), Mining enchants, Glimmer Rush event. "Level 50 is the cap" is wrong (60). |
| `farming` | REWRITE | SkyBlock version: Farming XP from the Hub Farm (Wheat), Barn and Mushroom Desert zones, Farm Merchant (`farm_merchant`), Jake / Windmill Operator shops, Hypixel crop fortune stats (`wheat_fortune` etc. in `StatTypes`), minions; Garden and Jacob's contests are not built. Survival-only: special crops, planters, sprinklers, Dew/Dewdrops (`BonusDrops` still drops them), Rich Compost, meals, scythe and armour ladder, Mabel's shop, Talents, Harvest Rush. "Level 50 cap" is wrong (60). |
| `foraging` | REWRITE | SkyBlock version: Foraging XP from the Hub Forest and The Park zones (logs regrow), Lumber Merchant (`lumberjack` NPC, `lumber_merchant` shop), Foraging Fortune, minions. Survival-only: five hard woods and `SkyTreeFeature`/`ForagingWorldgen`, stumps, Heartwood Splinters (`ForageDrops` still drops them with no shop), Oswin's shop, Treefeller axe ladder, armour, Talents, Timber Rush. |
| `fishing` | REWRITE | SkyBlock version: Fishing XP, Fishing Merchant (`fish_merchant`), Fisherman Gerald, Fishing Speed/Sea Creature Chance stats. The `fishing` mod engine (bite cues, bait, hotspots, sea creatures, lava fishing) is a good base but its content is survival: Tidescales/Plates, Marina's shop, survival sea creatures, rods and armour sets, Fishing Frenzy, Nether lava fishing. Note `Catching.trySeaCreature` only blocks sea creatures in the `skyrpg_hub` namespace, so public islands (`skyrpg_skyblock`) would spawn them. |
| `enchanting` | REWRITE | Today's system is still the survival one: Seraphine's Arcane Enchanting screen (`open_enchanting`), 72 custom enchants, tomes and the Codex (`enchants` mod). SkyBlock version is Hypixel enchanting (table with XP levels, books, anvil, ultimates, Experimentation Table), planned for Phase 2 (docs/design/skyblock.md: "engine reuse"). Wrong now: "Level 50" skill cap, tome sources (world bosses, dungeon bosses/champions, Stormcaller...), Talents table, links to world-bosses and dungeons. Write the page when the Hypixel enchant import lands; until then trim to what a player can reach. |
| `crafting` | REWRITE | Describes the Artisan Bench (`items/crafting/Bench.java`, `Features.ARTISAN_BENCH` off, was bought from Dorrin who no longer sells it; recipes locked by survival skill levels). SkyBlock version: Recipe Book + crafting table: `Recipes` (2,468 NEU grids with collection/slayer unlocks), `Crafting`, `CraftMenu`, `CraftScreen`, `/craft`, Supercraft; collections unlock recipes. Overlaps `skyblock/recipe-book`: either fold into it or make this the short "how crafting works" page. Gear ladders and tool tiers (Skyiron...) are survival. |
| `delving` | RETIRE | Delving skill was removed from the skill list (progress notes: "Survival Delving ... gone"); its replacement is the Catacombs skill with the Catacombs (Phase 3, not built). |

## Gear

| page | verdict | notes |
| --- | --- | --- |
| `weapons` | REWRITE | Documents survival sword lines (Skyiron, region, dungeon, slayer weapons, Mending Wand). SkyBlock version: Hypixel weapons imported by `tools/skyblock/import_items.py` into `skyblock/.../skyrpg/items` (156 swords, 44 bows, 26 wands, abilities with mana, reforges in progress: untracked `import_reforges.py`); damage uses Hypixel stats; every item has its own generated page. Page should explain the Hypixel weapon model (category, rarity, stats, ability, reforge) and link the item browser. |
| `armour-sets` | REWRITE | 36 KB listing survival sets (Skyiron...Wyrmscale). SkyBlock version: Hypixel armour (269 helmets, 191 chestplates...) with set bonuses from the item data and FurfSky looks. Drop the skill/combat/slayer/dungeon/region/limited set tables and the 3D looks of survival sets. |
| `tools` | REWRITE | Pickaxe/axe/hoe/scythe/rod ladders and durability/repair are survival (SkyBlock items never wear out, always unbreakable). SkyBlock version: Hypixel tools (24 pickaxes, 29 axes, 48 farming tools, 25 rods) with Breaking Power/Mining Speed and abilities; item pages are generated. |
| `accessories` | REWRITE | Four slots (amulet, cloak, belt, bracers) and 8 survival accessories sold by Odessa Quill (the Librarian now). SkyBlock version is the Accessory Bag, Magical Power, Power Stones and about 430 accessories (Phase 2, not built; the items exist with category Accessory/Necklace/Cloak/Belt). Write the interim state or wait for the bag. |
| `backpacks` | UPDATE | Backpack slots and sizes still work (progress notes: "our backpacks still live in the inventory's backpack slot"). Fix: Artisan Bench recipes, Dorrin's shop and the upgrade route are gone; Hypixel backpacks will live in Storage (Phase 2). Mark as interim. |
| `shields` | RETIRE | Seven survival shields; Hypixel has no shields and none are in the import. |
| `bows` | RETIRE | Seven survival bows (Skyiron Longbow, Ember Bow...) and survival enchants. Hypixel bows (44 imported) belong in the rewritten `weapons` page. |
| `wands` | RETIRE | Survival mage build: wands, spell slots, scrolls (`magic` mod). The spell wheel is retired for SkyBlock (`retireSurvival`, decision 3A); Hypixel's wands/staffs are ordinary items with abilities. |

## Combat

| page | verdict | notes |
| --- | --- | --- |
| `stats` | UPDATE | Engine is current but: lists about 10 stats; `StatTypes` now has 70+ (Ferocity, Bonus Attack Speed, Ability Damage, True Defense, Pet Luck, Health Regen, Vitality, Mending, Swing Range, Sweep, Fear, Breaking Power, Pristine, Mining Spread, ore/crop fortunes, resistances, Rift stats...), only some have effects (True Defense, Vitality work; Ferocity, Attack Speed, Ability Damage have no gameplay code yet). The "Insight" stats were renamed Wisdom (Farming/Mining/Foraging/Combat/Fishing/Alchemy Wisdom). Intro and sources mention talents (retired). The damage formula (weapon damage x (1+Strength/100) x crit) skips Hypixel's +5 base and additive terms: check against `combat` before calling it 1:1. |
| `combat-basics` | REWRITE | Mob levels by distance from spawn, Elites, Valor Marks, Dread Fangs, Nightmare Cores, Training Dummy are survival-world (`MobLevels` skips only the `skyrpg_hub` namespace; zone mobs on islands carry their own level/health/damage/XP/coins from `skyrpg/zones/*.json`). SkyBlock version: zone mobs with Hypixel stats, Combat XP and coins paid on kill, Combat skill (Warrior bonus), slayer XP, Magic Find drop rolls, combat tag (`core/combat/CombatTag`, still active), damage numbers and nameplates (`DamageNumbers`). |
| `mobs` | RETIRE | Every custom survival mob (Glimmer Crawler, Bog Hag, Frost Revenant, Ember Imp, Iron Thane...); SkyBlock mobs are the zone specs listed on `the-hub` / `the-islands`. |
| `spells` | RETIRE | Twelve wand spells and scroll drops (`magic` mod); wheel and spell HUD retired. |
| `world-bosses` | RETIRE | Hollow King / Mother of Thorns rise in the overworld only (`WorldBoss` filters Level.OVERWORLD); region bosses are survival. |

## Dungeons

| page | verdict | notes |
| --- | --- | --- |
| `dungeons-overview` | RETIRE | The 8 handmade world dungeons (generated in the overworld by the `world` mod) are not part of SkyBlock; Hypixel's Dungeons/Catacombs are Phase 3 (instanced, five classes, floors F0-F7). Write that page when built. |
| `forgotten-crypt` | RETIRE | Survival world dungeon (overworld). |
| `spider-hollow` | RETIRE | Survival world dungeon (overworld). Not Spider's Den. |
| `drowned-temple` | RETIRE | Survival world dungeon (ocean floor). |
| `dwarven-halls` | RETIRE | Survival world dungeon. Not the Dwarven Mines. |
| `infernal-citadel` | RETIRE | Survival Nether dungeon. |
| `ruined-citadel` | RETIRE | Survival mountain dungeon. |
| `catacombs` | RETIRE | Survival dungeon under the Magma Rifts. Name clash with Hypixel's Catacombs (Phase 3): retire so the id and the wiki search do not mislead, then create a new page for the real thing. |
| `astral-observatory` | RETIRE | Survival outer-End dungeon. |

## World

| page | verdict | notes |
| --- | --- | --- |
| `survival-world` | RETIRE | Describes the Wildgate and `/survival`; the gate now goes to your island, and decision 1A keeps the survival world only as an archive. If the owners want a pointer, a 5-line stub is enough. (Gap: `/survival` is still registered in `HubCommands` and works from the Hub.) |
| `waystones-and-travel` | RETIRE | Waystones/Wayshrines are survival world features; SkyBlock travel is Fast Travel (`FastTravel`, `/warp <place>`, `skyrpg/warps/*.json`), launch pads and lifts (`LaunchPads`, `Lifts`), already covered in `skyblock-menu` and `the-islands`. |
| `builder-station` | UPDATE | Kept extra (decision 3A) and `Stations` only refuses the hub dimension, so it can work on a Private Island. Fix: "place anywhere in the survival world" and the hub message (`Stations.java` still says "Use one in the survival world"); the only recipe is an Artisan Bench recipe (Foraging 5; benches are off), so players cannot get one: decide a route (Wool Weaver shop, or a Hypixel-style recipe). |
| `brainrots` | UPDATE | Kept extra (decision 3A) but unreachable: `Brainrots` spawns only for players in `server.overworld()`; the Hub podium still shows the leaderboard and the album page (J, K menu) is still registered. Page says they "wander the survival world". Owners decide where they spawn (Hub? Private Island?), then fix the text. |
| `crates` | UPDATE | Kept extra (decision 3A), seasonal until Nov 8, but `CrateDrops` only drops in `Level.OVERWORLD` (and during a Blood Moon), so nobody in SkyBlock gets a crate. Page text "drops from monsters" needs the new source; the Jack o'Lantern pet and scythes are survival items outside the Hypixel set. |
| `danger-regions` | RETIRE | Overworld region system (`/region`), survival only. |
| `region-hearts` | RETIRE | Region bosses and sigils, survival only. |
| `blood-moon` | RETIRE | Retired on purpose (HUD widget `skyrpg_world:blood_moon` hidden); overworld only. |
| `the-deep` | RETIRE | Survival underground layers (`/layer`). Not the Deep Caverns island (separate dimension `skyrpg_skyblock:deep_caverns`, six layers); the name clash is dangerous for the assistant. |
| `the-nether` | RETIRE | Survival Nether rework (its biomes are switched off, progress notes). Hypixel's Crimson Isle is Phase 4. |
| `the-end` | RETIRE | Survival outer End and Astral Observatory. Hypixel's The End (Zealots, dragons) is Phase 2. |
| `ender-wyrm` | RETIRE | Survival final boss (`EnderWyrm`, Void Sigil). Hypixel dragons are Phase 2. |

## Companions

| page | verdict | notes |
| --- | --- | --- |
| `pets` | REWRITE | Five survival pets + Jack o'Lantern, eggs bought from Tamsin/Mabel/Oswin/Kestrel/Marina for Glimmer/Dew/Heartwood/Valor Marks/Tidescales (none of those shops exist now), Golden Pet Egg from world bosses/dungeons, level cap 50. SkyBlock version: Hypixel pets (about 80, level 100, pet items, Kat, Pet Luck, Taming skill, Auction House category), Phase 2. Still true: `pets` mod engine, Pets page in the K menu, `/pet`, `/pet storage`, Taming XP = active pet's XP. Right now pets are only obtainable by admins. |
| `mounts` | RETIRE | Griffins (wild nests in the overworld, whistles, stable, stamina). Mounts page and stamina HUD retired; Hypixel has no griffin mounts (decision 3A keeps the code). |

## Interface

| page | verdict | notes |
| --- | --- | --- |
| `k-menu` | UPDATE | Usage section is right. The page table is wrong: lists Talents, Contracts, Journal, Mounts, Titles, Pay (all retired via `SkyRpgMenu.retirePage`); missing the SkyBlock Menu (first page), Profile Management, Fast Travel, Calendar, SkyBlock Items browser, Recipe Book, SkyBlock Leveling, Bazaar, Auction House, Storage. "Collections 11 ... and the SkyBlock Level" and the intro ("quests, pets") need fixing. Overlaps `skyblock/skyblock-menu`: keep this as the generic key/usage page. |
| `chat` | UPDATE | Level badge is already the SkyBlock Level. Fix: the Titles bullet (titles retired); the `chest` showcase token row says dungeon chest; SkyHelper section refers to survival questions. |
| `hud-and-minimap` | UPDATE | HUD table rows to remove: Contracts, Quest Tracker, Flight stamina, Blood Moon (all retired in `retireSurvival`). Add the SkyBlock Sidebar (`SidebarWidget`: date, time, area name, purse) and note that the old Sidebar/Events boxes only show for admin events. Still valid: Purse, Skill progress, Health/Mana/Defense, Minimap, Info, Look-at, Waypoint ribbon, Slayer Quest, Fishing Bait, Party, Brainrot catches, Combat tag. Tab list unchanged. |
| `waypoints` | UPDATE | Fix: "back to a dungeon" intro, "dungeon and waystone icons", the Journal Track button paragraph (line 59) and the dungeon-entrances tip. Everything else (commands, sharing, compass) is valid. |
| `party` | UPDATE | Dimension tag section: `PartyClient` only knows Hub, Nether, The End, Overworld and falls back to a tidied id for `skyrpg_skyblock:*` islands; list the real tags or add proper island names. Rest (party chat, overlay, no friendly fire) is valid; Phase 3 will add dungeon parties. |
| `skyhelper` | UPDATE | Mechanism valid, but it answers from the wiki bundle (`tools/skyhelper/knowledge.py` reads the built wiki), so every stale survival page is quoted back to players (griffins, Hollis, dungeons). Page examples ("tame a griffin", "where a dungeon is") and "it sees where you are (hub or survival world, danger region) and the quest in your journal" need fixing; check `eval.py` (29/29) questions for survival content. |
| `titles` | RETIRE | `skyrpg_progress:titles` page retired; 32 survival titles earned from quests, dungeons, griffins, Blood Moon. |
| `settings` | UPDATE | Gameplay and Brainrots categories are still in the code but the page should note removed or irrelevant ones (check the Gameplay options for mounts/spells/talents; FurfSky has no toggle any more), and the Brainrots section is an extra. Verify each category against the Settings page registry. |

## Reference pages

Built by `tools/wiki/refpages.py` from game data (`gamedata.py` reads `data/*/skyrpg/*` and a few Java files). DROP = remove from `PAGE_LIST["reference"]`; SWITCH = rebuild from SkyBlock data; KEEP = leave.

| page | verdict | notes |
| --- | --- | --- |
| `items-weapons` | SWITCH | Holds only the 78 survival weapon refs. Route by the Hypixel `category` field (Sword, Bow, Wand...) in `item_page()`; drop the 257 legacy `skyrpg_items` entries or move them to an archive page. |
| `items-armour` | SWITCH | Survival sets only (378 refs). Hypixel Helmet/Chestplate/Leggings/Boots (about 830) are not here. |
| `items-tools` | SWITCH | Mixed (156 SkyBlock refs only because `type` matches Pickaxe/Axe); route by `category` Pickaxe, Axe, Farming Tool, Fishing Rod. |
| `items-accessories` | SWITCH | Survival amulets/cloaks/belts/bracers only. Hypixel has about 430 Accessory plus Necklace/Cloak/Belt items; split by family or rarity. |
| `items-materials` | SWITCH | The dump: about 5,600 SkyBlock items (2.1 MB JSON, 11,110 refs) because anything without a survival kind falls through to materials. Split by Hypixel category (Reforge Stone, Pet Item, Sack, Cosmetic, Portal, Travel Scroll, Mutation...) and cap page size. |
| `items-consumables` | SWITCH | Survival meals/bait/tomes plus 66 SkyBlock refs; route Consumable, Bait by `category`. |
| `items-other` | SWITCH | Backpacks, planters, spawners, region sigils, pet eggs are survival. |
| `enchant-list` | KEEP for now | Accurate for the enchants engine players can reach (Seraphine) but all survival (72 enchants, "Very rare drop: the Broodmother"). Replace when Hypixel enchanting lands (Phase 2). |
| `mob-list` | DROP, then SWITCH | Survival mobs (Glimmer Crawler, Bog Hag...). Build from zone specs (`skyrpg_skyblock/skyrpg/zones/*.json`: name, level, health, damage, xp, coins). Its dungeon links (`refpages.py` ~line 622) die. |
| `boss-list` | SWITCH | Dungeon, world, region bosses are survival; keep only the slayer section if `build_bosses` reads the new `import_slayers.py` data. Links `ender-wyrm` (line 681). |
| `pet-list` | DROP until Hypixel pets | Six survival pets, level 50 perks (Jack o'Lantern). |
| `mount-list` | DROP | Griffins. |
| `collection-list` | UPDATE | Data is already Hypixel's 83 collections, but the renderer still prints "Rewards for all tiers: 0 coins"; show the `unlocks` lines instead and group by Hypixel category. |
| `quest-list` | DROP | Quests are off; text points to the Journal and Hollis. |
| `sell-prices` | SWITCH | Lists survival `skyrpg_items` plus vanilla; SkyBlock sells at `hypixel.npc_sell_price` in each item JSON. Better folded into each item's generated page, then drop the list. Links `selling-and-shops` (retired). |
| `region-list` | DROP | Danger regions. |
| `skyrpg-level-max` | DROP or rewrite | Computed from quests and dungeon/region/world boss first kills (survival XP sources) and titled "SkyRPG Level"; SkyBlock Level now comes from skills, collections, slayers, Fairy Souls (`SkyBlockLevels`). Recompute from those or drop. |

Generated per-item pages (6,142 total) include 257 survival items (`item-skyrpg_items-*`): drop them with the survival items and keep the 5,680 `skyrpg_skyblock` ones. After any change rebuild with `python tools/wiki/build.py` (website in `dist/wiki`, in-game bundle in `wiki/src/main/resources/assets/skyrpg_wiki/wiki/`).

## Links that would break

Pages (any category) that link to a RETIRE page, not counting links between retired pages. Fix these links when retiring.

- `contracts` is linked from: `coins-and-bank`, `combat-basics`, `farming`, `first-steps`, `fishing`, `foraging`, `hud-and-minimap`, `k-menu`, `mining`, `progression-guide`, `welcome`
- `events` is linked from: `coins-and-bank`, `farming`, `first-steps`, `fishing`, `foraging`, `hud-and-minimap`, `mining`, `skyhaven`, `skyrpg-mods`, `welcome`
- `quests-and-journal` is linked from: `coins-and-bank`, `first-steps`, `hud-and-minimap`, `k-menu`, `npcs`, `progression-guide`, `skyhaven`, `skyrpg-mods`, `waypoints`, `welcome`
- `selling-and-shops` is linked from: `backpacks`, `coins-and-bank`, `crates`, `farming`, `first-steps`, `k-menu`, `npcs`, `skyhaven`
- `danger-regions` is linked from: `accessories`, `armour-sets`, `combat-basics`, `crafting`, `progression-guide`, `weapons`
- `mounts` is linked from: `controls-and-keys`, `hud-and-minimap`, `k-menu`, `pets`, `skyrpg-mods`, `welcome`
- `survival-world` is linked from: `brainrots`, `combat-basics`, `foraging`, `skyrpg-mods`, `welcome`
- `blood-moon` is linked from: `accessories`, `combat-basics`, `crates`, `hud-and-minimap`
- `delving` is linked from: `accessories`, `armour-sets`, `crafting`, `weapons`
- `dungeons-overview` is linked from: `combat-basics`, `pets`, `stats`, `welcome`
- `spells` is linked from: `controls-and-keys`, `npcs`, `skyrpg-mods`, `stats`
- `ender-wyrm` is linked from: `armour-sets`, `progression-guide`, `weapons`
- `shields` is linked from: `armour-sets`, `combat-basics`, `weapons`
- `the-nether` is linked from: `armour-sets`, `fishing`, `mining`
- `titles` is linked from: `chat`, `controls-and-keys`, `k-menu`
- `wands` is linked from: `controls-and-keys`, `npcs`, `stats`
- `waystones-and-travel` is linked from: `combat-basics`, `skyhaven`, `waypoints`
- `world-bosses` is linked from: `combat-basics`, `enchanting`, `pets`
- `bows` is linked from: `combat-basics`, `weapons`
- `the-deep` is linked from: `combat-basics`, `mining`
- `infernal-citadel` is linked from: `fishing`
- `mobs` is linked from: `combat-basics`
- Generated pages in `tools/wiki/refpages.py`: `sell-prices` links `selling-and-shops`; `boss-list` links `ender-wyrm`; `mob-list` links each dungeon page; `quest-list` and `skyrpg-level-max` link each other's lists. `PAGE_LIST` in `tools/wiki/model.py` must also lose every retired id.
- Outside the wiki: grep `wiki/src`, `helper/` and `tools/skyhelper` for page ids (command buttons, `/wiki <page>`) before deleting `quests-and-journal`, `contracts`, `titles`.

## Other things found while auditing

- `docs/design/skyblock-progress.md` already has the open item "Wiki: survival pages are still there; rewrite or retire them": this audit is its list.
- The skyblock-category pages link outside their category only to `skills-overview` and `hud-and-minimap`, both UPDATE, neither RETIRE.
- Runecrafting, Social and Hunting are not implemented (`skills/.../skyrpg/skills/` has nine files), so "Hypixel's 12 skills" is not yet true; `skills-overview` is honest about nine.
- Public islands live in the `skyrpg_skyblock` namespace, but `MobLevels`, `Elites`, `Catching` and `Brainrots` special-case `skyrpg_hub` only; survival mob levels, Elites and sea creatures can apply on islands. Check before writing the combat and fishing pages.
- The survival world stays reachable as an archive (decision 1A) through `/survival`, but its wiki pages present it as the main game; if the archive should be hidden, gate `/survival`.

