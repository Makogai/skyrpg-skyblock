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
- [ ] Item browser screen to see them all (a creative-style grid with search).
- [ ] Profiles and co-ops (switch = save player data, transfer packet, load the other profile's data).
- [ ] Islands dimension, starter island, permissions, `/visit`, spawn on your island.
- [ ] SkyBlock Menu (the K menu reorganised).
- [ ] SkyBlock time and calendar.
- [ ] Public-island framework: block regrowth zones, mob spawn zones, Fast Travel and `/warp`.
- [ ] Storage (ender chest pages, backpacks) and Wardrobe.
- [ ] Pack: Catharsis + FurfSky as Modrinth entries (fetched by each launcher); ask the FurfSky team.

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
- The wiki generates a page per SkyBlock item (6,142 generated pages; `build.py --check` takes about a minute).
