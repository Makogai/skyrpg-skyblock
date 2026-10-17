# Research: Alchemy skill, brewing and potions (Hypixel SkyBlock)

Status: research only. Date 2026-10-17. Marker (?) = uncertain or from memory, not confirmed by a source we could read.

Sources actually read:
- W = https://hypixelskyblock.minecraft.wiki/w/<Page>. Fetched OK: Brewing, Potions, Brewing_Stand, Alchemy_Skill, Speed_Potion, Strength_Potion, Regeneration_Potion, Healing_Potion, God_Potion, Potion_Bag. 404: /w/Alchemy, /w/Potion_Effects (not tried further). No bot check was hit. Note: the fetch tool summarises pages, so some detail (full ingredient lists per potion, per-level duration tables) was lost.
- N = NEU repo zip (tools/skyblock/cache/neu-repo.zip), items/POTION_<TYPE>;<LEVEL>.json lore (effect text and base duration per level). No brewing recipe or constants file exists in it for potions.
- D = our item data (skyrpg/items/*.json, key `hypixel.experience.alchemy.INGREDIENT_BREW`), 57 items carry it.

## 1. How brewing works

Source: W /w/Brewing, /w/Brewing_Stand.

| Rule | Value |
|---|---|
| Brewing Stand | Buy for 30 coins, or craft 3 Cobblestone + 1 Blaze Rod (W Brewing) |
| Custom UI | 3 bottom slots (water bottles / Awkward / potions), 1 top slot (ingredient or modifier). Light-blue glass panes and barriers as filler |
| Brew time | 20 seconds for ANY brewing action (W Brewing_Stand) |
| Speed-up | Witch Pet can cut brewing time by up to half (W Brewing_Stand). Exact per-level % (?) |
| Fuel | None. No Blaze Powder needed (W Brewing) |
| Hoppers | Work like vanilla, but only vanilla potions can be brewed that way (W Brewing_Stand) |
| Water Bottle | Base. Nether Wart on Water Bottle gives Awkward Potion (id POTION_AWKWARD) |
| Awkward Potion | Required base for every main ingredient. NEU lore: "Use Awkward Potions in Brewing Stands with items labeled as Brewing Ingredients". Awkward carries `should_give_alchemy_exp:1b` and `last_potion_ingredient:NETHER_STALK` (N) |
| Order | 1) Nether Wart -> Awkward. 2) Main ingredient (picks type and base level). 3) Modifiers (each is a separate 20 s brew). Modifier order is flexible (?) |
| Ingredients in this stage | Items marked "Brewing Ingredient" in lore. Modifiers are also labelled (Redstone, Glowstone, Gunpowder) |
| Stacking modifiers | One Glowstone-type and one Redstone-type can both be applied (W Brewing). Can a second Redstone be added? Likely not (?) |
| Skill-XP potions | Modifiers apparently work only on unmodified-skill-XP potions; adding modifiers alone to them will not start the stand (W Potions, vague) |
| Storage | Potion Bag (Small 9, Medium 18, Large 27, Giant 36, Massive 45 slots) unlocked via Nether Wart collection II/V/VIII/X/XII (W Potion_Bag). Holds Potions and Mixins |

Collecting: potions are taken out of the stand; Alchemy XP is granted when the potion is removed from the stand (W Alchemy_Skill).

## 2. Potion types

Sources: N lore (max level, effect, base duration), W for ingredients where fetched. Durations are base (no modifier, no level perk). All "3:00" unless stated.

Level to rarity (W Potions): I-IV Common..Epic, V+ Epic or higher (?), God Potion is Special.

### 2.1 Ingredients that set the base level (confirmed on W)

| Potion | Level I | Level III | Level V | Source |
|---|---|---|---|---|
| Speed | Sugar | Enchanted Sugar | Enchanted Sugar Cane | W Speed_Potion |
| Strength | Blaze Powder | Enchanted Blaze Powder | Enchanted Blaze Rod | W Strength_Potion |
| Regeneration | Ghast Tear | (?) | Enchanted Ghast Tear | W Regeneration_Potion |
| Healing | Glistering Melon Slice | Enchanted Melon Slice (?) | Enchanted Glistering Melon Slice | W Healing_Potion |

Pattern (W): main ingredient gives I, enchanted tier gives III, top tier gives V; Glowstone modifiers add the rest (up to VIII). Levels II and IV, VI-VIII come from Glowstone modifiers (section 3).

### 2.2 Other potions: ingredient mapping (NOT confirmed, from XP list D and memory)

| Potion | Ingredient I (?) | Notes |
|---|---|---|
| Fire Resistance | Magma Cream | max I, flat 10% fire/lava reduction (N) |
| Night Vision | Golden Carrot | max II |
| Water Breathing | Pufferfish (raw fish-3) | max VI |
| Jump Boost / Rabbit | Rabbit Foot | |
| Haste | Cactus Green (ink_sack-2?) / Cactus (?) | |
| Resistance | Cobblestone? / Obsidian (?) | |
| Weakness | Fermented Spider Eye | negative effect |
| Poison / Venomous | Spider Eye | |
| Slowness | Fermented Spider Eye + ... (?) | |
| Damage | Spider Eye then Fermented (?) | instant |
| Mana | Lapis / Enchanted Lapis (?) | |
| Absorption | Enchanted Gold / Gold (?) | |
| Stamina, Spelunker, Magic Find, Pet Luck, etc. | not found | Likely tied to collection items, (?) |

Treat all rows in 2.2 as unverified. Recommend a second research pass on each /w/<X>_Potion page.

### 2.3 Effect per level (from N lore of level 1 and max, W where noted)

| Potion | Max | Base duration | Effect at I -> max |
|---|---|---|---|
| Speed | VIII | 3:00 | +5 per level: +5 .. +40 Speed (W) |
| Strength | VIII | 3:00 | +5, +12.5, +20, +30, +40, +50, +60, +75 (W) |
| Regeneration | IX | 0:45 at I .. 2:30 at IX (N) | Health Regen +2.5, 5, 7.5, 10, 12.5, 15, 20, 25, 30 (W). IX is God Potion only (?) |
| Healing | VIII (IX god only) | 0:05 | +5 HP/s per level: 5..40, IX = 45 (W) |
| Resistance | VIII | 3:00 | Defense +5 .. +60 |
| Mana | VIII | 3:00 | +1 .. +8 Mana per second |
| Absorption | VIII | 3:00 | +20 .. +300 absorption |
| Adrenaline | VIII | 3:00 | +20..+300 absorption and +5..+40 Speed |
| Damage | VIII | instant | 20 .. 500 damage |
| Slowness | VIII | 3:00 | -20 .. -500 Speed |
| Weakness | VIII | 3:00 | -5 .. -50 damage dealt |
| Haste | IV | 3:00 | +50 .. +200 Mining Speed |
| Agility | IV | 3:00 | +10..+40 Speed and mob miss chance |
| Archery | IV | 3:00 | Bow damage +12.5% .. +75% |
| Burning | IV | 3:00 | Longer fire damage on enemies |
| Cold Resistance | IV | 3:00 | +2.5 .. +10 |
| Critical | IV | 3:00 | Crit Chance +10..+25%, Crit Damage +10..+40% |
| Dodge | IV | 3:00 | 10% .. 40% mob miss |
| Experience | IV | 3:00 | +5% .. +20% orb XP |
| Jump Boost | IV | 3:00 | Jump 1.9 .. 4.1 blocks |
| Knockback | IV | 3:00 | +20% .. +80% |
| Magic Find | IV | 3:00 | +10% .. +75% (not brewable per W Potions; bits/event) |
| Pet Luck | IV | 3:00 | +5% .. +20% |
| Poison | IV | 3:00 | 10 .. 60 dmg/s |
| Rabbit | VI | 3:00 | Jump Boost I..III and +10..+60 Speed |
| Spirit | IV | 3:00 | +10..+40 Speed, +10..+40% Crit Dmg (not brewable) |
| Stamina | IV | instant | Heal +150..+515, +50..+200 Mana |
| Stun | IV | 3:00 | 10% .. 40% stun on hit |
| True Resistance | IV | 3:00 | +5 .. +20 True Defense |
| Venomous | IV | 3:00 | -5..-20 Speed, 5..20 dmg/s |
| Wounded | IV | 3:00 | -25% .. -100% healing |
| Spelunker | V | 3:00 | +5 .. +25 Mining Fortune |
| Water Breathing | VI | 3:00 | 15% .. 100% no drowning damage |
| Blindness | III | 0:03 | blindness |
| Night Vision | II | 3:00 | visibility |
| Fire Resistance | I | 3:00 | -10% fire/lava damage |
| Invisibility | I | 3:00 | invisible to players and mobs |
| Skill XP Boost (Alchemy, Combat, Enchanting, Farming, Fishing, Foraging, Mining) | III | 12:00 / 24:00 (?) / 36:00 | Wisdom +5 / +10 (?) / +20 |

Per-level durations for non-base-3:00 potions beyond Regeneration are only partly known (N shows only the first and last level); (?).

## 3. Modifiers

Source: W /w/Brewing, /w/Speed_Potion, /w/Regeneration_Potion.

### 3.1 Duration

| Item | Effect | Notes |
|---|---|---|
| Redstone Dust | +8:00 | |
| Enchanted Redstone Dust | +16:00 | |
| Enchanted Redstone Block | +40:00 on W Brewing, +16:00 on W Speed_Potion | CONFLICT, (?) |
| Enchanted Redstone Lamp | +16:00 and +3 levels | both at once |
| Gunpowder | Splash version, duration x0.5 | W Brewing, Speed_Potion |
| Enchanted Gunpowder | Splash only, no duration penalty | W Speed_Potion |

Default drinkable duration is 3:00 (W Brewing). Redstone on 0:45/0:05-base potions: also adds fixed minutes (?).

### 3.2 Level

| Item | Levels |
|---|---|
| Glowstone Dust | +1 |
| Enchanted Glowstone Dust | +2 |
| Enchanted Glowstone | +3 |
| Enchanted Redstone Lamp | +3 (plus duration) |

Caps: each potion has its own max (table 2.3). Adding past the cap is rejected or wasted (?). Level VIII needs e.g. V + 3 (?) or I + enchanted glowstone chain.

### 3.3 Other duration boosts (from W Speed_Potion)

| Source | Duration |
|---|---|
| Alchemy level perk | +1% per Alchemy level, max +50% at 50 (W Alchemy_Skill) |
| Potion Affinity Talisman | +10% (N lore: "Increases the duration of any consumed Potion by 10%") |
| Potion Affinity Ring | +25% |
| Potion Affinity Artifact | +50% |
| Parrot Pet | +40% (Legendary, God Potion too) |
| Additive or multiplicative with each other | (?) |

## 4. Alchemy XP

Sources: D (item data), W /w/Alchemy_Skill.

Rule (W): XP is awarded when the potion is removed from the stand. Glowstone Dust, Enchanted Glowstone Dust and Enchanted Glowstone modifiers give NO XP (W). Awkward Potion carries a `should_give_alchemy_exp` flag (N), meaning XP is a per-brew event, with the potion marking whether it counts (so exploiting brewed-then-rebrewed is blocked) (?).
Per-brew XP is the table below (INGREDIENT_BREW of the ingredient used). The amount per bottle vs per stand action (x1 vs x3) is (?).

| XP | Ingredients (D) |
|---|---|
| 5 | coal, glowstone_dust, gold_ingot, lapis (ink_sack-4), cocoa beans (ink_sack-3), mutton, nether_stalk (Nether Wart), netherrack, redstone, sugar, sand-1 (red sand) |
| 10 | blaze_powder, cactus, feather, fermented_spider_eye, flint, golden_carrot, magma_cream, mithril_ore, slime_ball, speckled_melon, spider_eye, sulphur (gunpowder) |
| 12 | rabbit_foot, raw_fish-3 (pufferfish) |
| 15 | obsidian |
| 20 | poisonous_potato, raw_fish-1 (salmon) |
| 30 | ghast_tear |
| 250 | enchanted_cactus_green, enchanted_melon |
| 300 | enchanted_cocoa, enchanted_glacite, enchanted_glowstone_dust, enchanted_gold, enchanted_mutton, enchanted_redstone, enchanted_sugar |
| 400 | enchanted_cake, foul_flesh |
| 500 | enchanted_blaze_powder, enchanted_cactus, enchanted_glistering_melon, enchanted_gunpowder, enchanted_spider_eye |
| 550 | enchanted_rabbit_hide |
| 600 | enchanted_pufferfish, enchanted_rabbit_foot |
| 100 | enchanted_ghast_tear |
| 12,000 | enchanted_cookie |
| 15,000 | enchanted_cooked_mutton, enchanted_fermented_spider_eye, enchanted_glowstone, enchanted_gold_block, enchanted_redstone_lamp, enchanted_sugar_cane |
| 23,000 | enchanted_blaze_rod |

Warnings on D: `enchanted_glowstone_dust` (300) and `enchanted_glowstone` (15000) are listed in our data although W says glowstone modifiers give no XP; the field may be used when those items are the main ingredient of other potions. Also enchanted_ghast_tear = 100 looks low next to ghast_tear 30 (?) and enchanted_cookie/cooked_mutton values are huge; verify against W per-item pages. Names above are our internal ids.

### 4.1 Levels (W Alchemy_Skill, summary only)

| Item | Value |
|---|---|
| Max level | 50 |
| Level 1 | 50 XP |
| Level 10 | 9,925 cumulative |
| Level 20 | 522,425 cumulative |
| Level 50 | 55,172,425 cumulative |
| Per-level XP table | Standard SkyBlock skill table (50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, ...) (?) from memory; consistent with the cumulative level 10 above |
| Potion duration | +1% per level (+50% at 50) |
| Intelligence | +1 per level up to 25 (?), +2 per level after (?); +86 total at 50 per W (that sum implies this split is wrong; recheck) |
| SkyBlock XP | +5 .. +20 per level, +700 total at 50 |
| Coins | 100 coins up to 1,000,000 per level |
| Perk name | "Brewer" (?) |

Per-level reward lines (exactly which levels give which coin amounts) not captured: needs the full table.

## 5. Stacking, death, display, splash

Source: W /w/Potions, /w/God_Potion.

| Topic | Rule |
|---|---|
| Same effect | Potions do NOT stack. Drinking a potion sets both timer and level of that effect to the new potion's values (W Potions) |
| Exception 1 | A lower-level potion does not override a higher-level active effect |
| Exception 2 | God Potion effects take precedence over all other buffs |
| Different effects | Run in parallel, each with its own timer |
| Death | Normal potion effects: lost on death (?) (W did not say). God Potion effect does NOT dissipate when dying (W God_Potion) |
| Pausing | God Potion pauses on Private Island and inside Catacombs (W God_Potion). Other potions likewise pause in Catacombs? (?) |
| Where shown | "Active effects" in an equipment-style menu showing remaining time and level (W Potions). In real SkyBlock this is `/effects` and a SkyBlock Menu button; also tab list lines (?) |
| Splash | Same effect as drinkable but affects nearby players when thrown. Radius, falloff, and whether the thrower gets the effect: (?). Duration x0.5 for gunpowder-made splash |
| Instant potions | Healing, Damage, Stamina etc. are instants/short; Damage and Healing show only in splash form in practice (?) |

## 6. Special potions (all later)

| Item | Notes | Phase |
|---|---|---|
| God Potion (GOD_POTION, GOD_POTION_2) | 1,500 Bits from Elizabeth (Community Center). 12 h base, +0.24 h per Alchemy level (so 24 h at 50 with the formula 12+0.24*level -> 23.9 (?)), Parrot pet boosts. Doses add duration, cap 192 h ("You can't handle that much Godly power at once!"). Grants Critical IV, Regeneration IX, Strength VIII, Agility IV, Healing IX, Night Vision, Water Breathing, many XP boosts (full list not fetched). Soulbound COOP in our data | later |
| Dungeon potions (POTION_DUNGEON;1-7) | N: Regeneration I at 20:00 .. Regeneration IV at 40:00 etc. Dropped in dungeons, not brewable | later |
| Mixins | Item type stored in Potion Bag, used with God Potion / Great Spook Potion (?) | later |
| Great Spook Potion (GREAT_SPOOK_POTION) | event item in NEU | later |
| Harvest Harbinger V (25:00, +50 Farming Fortune) | Garden/Bazaar item | later |
| Mushed Glowy Tonic (1:00:00, +30 Fishing Speed) | | later |
| Stinky Cheese (1:00:00, +20 Pest Chance) | Garden | later |
| Wisp's Ice-Flavored Water (5:00, +10 Regen, +25 True Defense) | Rift/Dwarven | later |
| Rift Gravity, Secrets potion | Rift | later |
| Recall Potion, Anti-Morph Potion, Alchemist's Staff | items in our data/NEU | later |
| Potion Affinity Talisman/Ring/Artifact | Accessories (+10/25/50% duration), see 3.3 | with accessories |

## Biggest uncertainties

1. Enchanted Redstone Block duration (+40:00 on Brewing page vs +16:00 on Speed_Potion).
2. Ingredient map for all non-base potions (2.2) is memory; only Speed/Strength/Regeneration/Healing are confirmed.
3. Exact level-to-ingredient chain for II, IV, VI-VIII (how Glowstone modifiers cap at VIII; does Level IX exist outside God Potion).
4. Per-level duration for Regeneration (0:45..2:30), Healing, skill-XP boosts (12/24/36 min?), and whether Redstone adds the same minutes to them.
5. Alchemy XP: per bottle vs per brew action, whether modifiers reward XP (W says glowstone does not), and suspect values in our data (enchanted_ghast_tear 100, enchanted_glowstone 15000).
6. Alchemy per-level rewards table and XP-per-level numbers; Intelligence per level split.
7. Death behaviour of normal potions and active-effect UI (/effects).
8. Splash radius and falloff, and who receives the effect.
9. Witch Pet speed-up percentages per rarity/level, Parrot +40% stacking with Affinity items.
10. W pages were summarised by the fetch tool; exact tables may differ from what is written here.
