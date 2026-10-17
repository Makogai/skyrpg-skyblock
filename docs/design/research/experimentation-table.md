# Experimentation Table research (Hypixel SkyBlock)

Sources: community wiki mirror (pages fetched through a summarising fetcher, so table columns were sometimes mis-aligned; those spots are marked (?)) and the NEU repo zip. Nothing here was verified in-game.
(?) = uncertain or conflicting between fetches.

Wiki base: https://hypixelskyblock.minecraft.wiki/w/
- Experimentation_Table, Experiments, Superpairs, Chronomatron, Ultrasequencer, Metaphysical_Serum

Local: D:\code\skyrpg-skyblock\tools\skyblock\cache\neu-repo.zip
- items/EXPERIMENTATION_TABLE.json, items/METAPHYSICAL_SERUM.json, items/EXPERIMENT_THE_FISH.json, items/GUARDIAN;5.json
- NEU has NO experiment data constants (no tier tables); only item definitions.

## 1. The table: get, place, use, cost, daily limits
Source: https://hypixelskyblock.minecraft.wiki/w/Experimentation_Table , /w/Experiments , NEU EXPERIMENTATION_TABLE.json

| Fact | Value |
|---|---|
| Item | Experimentation Table, RARE, player-head block, tradeable/auctionable, not NPC-sellable, not museum |
| Lore | "Place this item on your island! Conduct wild enchanting experiments to gain bonus Enchanting Exp and items!" |
| Recipe | Unlock: Enchanting X (level 10). NEU recipe: 8 Oak Log ring around 1 Experience Bottle (3x3, bottle in centre) |
| Placement | Private Island only, max 1 table at a time |
| Use | Right-click the table; menu shows Chronomatron, Ultrasequencer, Superpairs, each with dye-coloured stake levels (Lime to Magenta) |
| Reward types | Enchanting XP, high-level enchanted books, Endcap (ultra-rare) enchantments, Experience Bottles (normal/Grand/Titanic) |

Start cost: each run costs vanilla **XP levels** (see Superpairs table). (?) One fetch showed 25/50/75/100/200/350 as "Starting Cost" and 50,000-150,000 elsewhere; another had them in a different order. Best reading: start cost = 25/50/75/100/200/350 levels; the 3,500-150,000 figures are Enchanting XP rewards.

Daily limits:
| Rule | Value |
|---|---|
| Charges | 3 charges. (?) "Games reset 24 hours after consuming the last charge" vs "one charge renews every 24 hours" - fetches conflicted |
| Extra charges | Purchasable with Bits AND XP levels, escalating |
| Purchase counter reset | Daily at 12:00 AM GMT (7 PM EST / 8 PM EDT) |
| Order | Chronomatron and Ultrasequencer are "add-ons"; do them before Superpairs to bank bonus clicks |

Charge purchase costs (consistent in all fetches):
| Reset # | XP levels | Bits |
|---|---|---|
| 1st | 50 | 150 |
| 2nd | 100 | 300 |
| 3rd | 200 | 500 |

## 2. Superpairs (main experiment)
Source: https://hypixelskyblock.minecraft.wiki/w/Superpairs , /w/Experiments. The fetcher scrambled columns; values below are best reconciliation.

Rules: a board of face-down tiles, each labelled with a reward. Click two tiles; if two identical pieces are clicked consecutively you get the reward. Fixed time limit and click limit. Metaphysical time limit 64 s (?). Power-ups hidden in tiles: Enchanted Lapis Block (+1 click and bonus Experience), Feather (+3 clicks), Diamond (instant find: next click matches).

| Stake | Grid (tiles) | Ench. level req. | Start cost (levels) | Base clicks (?) | Completion Ench. XP range | Bottles | Other |
|---|---|---|---|---|---|---|---|
| Beginner | 7x2 (14) | X (10) | 25 | 10 (?) | 3,500-50,000 | Normal 10-30 | Enchanted books, Guardian pet |
| High | 5x4 (20) | XX (20) | 50 | 12 (?) | 5,000-60,000 | Normal 10-30, Grand 5-20, Titanic 1 | Books, Guardian |
| Grand | 5x4 (20) | wiki says "II (2)" - surely a typo (?) | 75 | 12-14 (?) | 5,000-90,000 | Grand 5-20, Titanic 1 | Books, Guardian, Metaphysical Serum |
| Supreme | 7x4 (28) | XXX (30) | 100 | 14-16 (?) | 5,000-120,000 | Grand 5-20, Titanic 1 | + Nadeshiko Dye, ultra-rare books/Endcap |
| Transcendent | 7x4 (28) | XL (40) | 200 | 16 (?) | 25,000-130,000 | Grand 5-20, Titanic 1-2 | + Dye (rarer) |
| Metaphysical | 7x4 (28) | L (50) | 350 | "12 and 2x clicks from add-ons" | 25,000-150,000 | Grand 5-20, Titanic 1-2 | + Experiment the Fish, ~1% ultra-rare book chance |

Click counts conflict heavily: fetch A gave 10,12,12,14,16,12+2x; fetch B gave 10,25,50,75,100,200; fetch C gave 25,50,75,100,200,350 (that equals the cost column, so it is a misread). Only the Metaphysical quote ("12 and 2x Clicks from Add-On Experiments") was quoted directly from the page. All base clicks are (?).
Enchanted books: pool available from Beginner up; distinct-book counts per tier not reliably extracted (a fetch showed 25/50/75/100/200/350 under "Enchanted Books" but that is the cost column again) (?).

Pairs/rewards: each pair position is one reward (XP bottle size, book, pet, serum, dye...). "Superpairs XP bottles represent rewards per pair, not total pairs obtainable in one session."

Ultra-rare books / Endcap enchants: Supreme and above; base 1% at Metaphysical (individual item 0.027% = 1 in 3,700 (?)). Boosts: Mythic Guardian pet +7%, Enderman Slayer VIII +15%.

RNG Meter: every +1,000 Enchanting XP from Superpairs = 1 RNG meter XP. Targets: Titanic XP Bottle 15,000; Experiment the Fish 50,000; Metaphysical Serum 50,000; Legendary Guardian pet 150,000; ultra-rare books 150,000-500,000; Nadeshiko Dye up to 2,500,000 (?). Exact per-item list not captured.

## 3. Chronomatron (add-on)
Source: https://hypixelskyblock.minecraft.wiki/w/Chronomatron , /w/Experiments (all fetches agreed on these numbers)

Rules: a chain of coloured "notes" (tone plus lit block) plays; recite it by clicking the blocks in order; on success the same chain replays with one new note added. Ends on a mistake, or at 49 chains. Rewards cap at chain 15.

| Stake | Colours | Ench. lvl | Restart cost (levels) | XP per note (up to 15) | Max XP | Superpairs bonus clicks |
|---|---|---|---|---|---|---|
| High | 3 | XX (20) | 5 | 1,500 | 22,500 | +1 at chain 5, +2 at chain 9 |
| Grand | 5 | XXV (25) | 10 | 2,500 | 37,500 | same |
| Supreme | 7 | XXX (30) | 15 | 3,500 | 52,500 | same |
| Transcendent | 8 | XXXV (35) | 20 | 4,500 | 67,500 | same |
| Metaphysical | 10 | XL (40) | 40 | 6,000 | 90,000 | +1 at 5, +2 at 9, +3 at 12 |

XP = longest chain (max 15) x XP per note. Reward is Enchanting XP only. One fetch listed "clicks 1,2,3,4,5" per tier (?) - likely a misread.

## 4. Ultrasequencer (add-on)
Source: https://hypixelskyblock.minecraft.wiki/w/Ultrasequencer , /w/Experiments

Rules: numbers appear on a grid for a short time; memorise, then click the squares in number order. Ends when a number is missed or the round fills all slots. Reward capped at sequence length 20.

| Stake | Grid | Ench. lvl | Restart cost (levels) | XP per number (up to 20) | Max XP | Superpairs bonus clicks |
|---|---|---|---|---|---|---|
| Supreme | 7x3 | XXV (25) | 15 | 3,500 | 70,000 | +1 at seq 5, +2 at seq 7 |
| Transcendent | 7x4 | XXX (30) | 20 | 5,000 | 100,000 | same |
| Metaphysical | 9x4 | XL (40) | 40 | 7,000 | 140,000 | +1 at 5, +2 at 7, +3 at 9 |

No High/Grand tier. Hard round cap and per-round display time unknown (?). One fetch listed "clicks 7,10,15" per tier (?) - likely a misread.

## 5. Add-ons, bonus clicks, restarts
- Completing Chronomatron / Ultrasequencer grants extra Superpairs clicks by milestone (bonus columns above).
- Metaphysical Superpairs: "12 and 2x Clicks from Add-On Experiments" - wording unclear (?).
- Restart cost (XP levels): Chronomatron 5/10/15/20/40; Ultrasequencer 15/20/40.
- Whole-table extra charges: section 1 (Bits + levels).
- One fetch claimed "+3 clicks from both add-ons combined" at Metaphysical (?). Uncorroborated.

## 6. Metaphysical Serum and rule-changers
Source: https://hypixelskyblock.minecraft.wiki/w/Metaphysical_Serum , NEU METAPHYSICAL_SERUM.json

| Item | Effect |
|---|---|
| Metaphysical Serum | EPIC permanent consumable. Drops from Grand+ Superpairs, rate 1.0638% (?). Shortens the chain required for Superpairs add-on experiments by 1 per dose. Max 3 doses. SkyBlock XP per dose +5/+10/+15 (?). After all 3: +2 Superpairs clicks (?). 4th dose: "YUCKY! This serum seems to have lost it's flavor!". AH-tradeable, not NPC-sellable, not museum-donatable |
| NEU lore | "Drinking this serum shortens the chain required for Superpairs add-on experiments by 1! You may drink up to 3 doses of the serum before the taste becomes too abstract." |
| Experiment the Fish | SPECIAL item, Metaphysical Superpairs drop only; lore "Fish me once, shame on you. Fish me twice, shame on me." |
| Guardian pet | Superpairs-related (NEU lore mentions books in Superpairs); Mythic gives +7% ultra-rare chance |
| Enderman Slayer VIII | +15% ultra-rare chance |
| Nadeshiko Dye | Supreme+ reward, tiny chance |

(?) Meaning of "chain reduced by 1": probably lowers the milestone thresholds (5/9/12 etc.) by 1 per dose.

## Biggest uncertainties
1. Superpairs base click count per stake (conflicting readings).
2. Start cost 25/50/75/100/200/350 levels, and Grand's Enchanting requirement (page says "II (2)", surely wrong).
3. Exact charge semantics (3 charges, 24h per charge vs after last) and Bits-vs-levels pairing.
4. Enchanted book pool, per-pair reward weights/counts per tier, RNG meter full list (not captured).
5. Serum specifics (bonus clicks after 3 doses, SkyBlock XP, chain reduction semantics, drop rate).
6. Ultrasequencer hard round cap and display timings, Chronomatron note speed, Superpairs time limits (only 64 s Metaphysical seen).
7. Metaphysical add-on click wording ("12 and 2x").

Recommended: re-fetch the Superpairs table as raw wikitext (e.g. /w/Superpairs?action=raw) to resolve items 1-4.
