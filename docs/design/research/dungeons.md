# Dungeons (The Catacombs) research

Source note: numbers come from the community mirror https://hypixelskyblock.minecraft.wiki/w/<Page> (read through a lossy summarizer; several pages 404'd, so "good, not certified")
and from the NEU repo (`tools/skyblock/cache/neu-repo.zip`, MIT; constants/leveling.json, essencecosts.json, essenceshops.json, rngscore.json, items/*.json lore).
Where I had to fill a gap from general game knowledge rather than a fetched page the line is marked (?). Anything marked (?) must be verified before being coded as a hard number.
Format follows docs/design/research/glacite-tunnels.md.

---

## 1. Dungeon Hub, access and party rules

Sources: https://hypixelskyblock.minecraft.wiki/w/Dungeon_Hub , /w/The_Catacombs , NEU items MORT_NPC, SAULS_RECOMMENDATION, KISMET_FEATHER

| Rule | Value |
|---|---|
| Island | "Dungeon Hub", reached through the portal at -34, 72, 23 in the Hub village (or by the fast-travel scroll HUB_CRYPTS_TRAVEL_SCROLL / HUB_CRYPTS_PORTAL once unlocked) |
| Safety | damage (incl. fall damage) disabled in the Dungeon Hub |
| Entering a run | talk to Mort (queue via Party Finder / party leader starts) or use the Catacombs entrance; whole party is warped into a fresh instance |
| Party size | 1-5 players. Wiki text says "2-5 teammates" by default; solo entry needs Saul's Recommendation (given by Adventurer Saul after Floor III completion, handed to Mort) (?: exact Floor III vs "Floor I" trigger) |
| Time limit | 60 minutes per run |
| Level gate | Combat level AND Catacombs level per floor (table below). Master Mode unlocks after beating Necron (F7) |
| Potions | only one potion consumable per run (Stamina / Healing / Mana exceptions) |
| Movement | Speed above 100 is multiplied by 0.67 inside dungeons (wiki) |
| Mana on hit | "5 + 1% of max Mana" per hit (wiki, left-click mage mechanic) |
| Anti-abuse | Dragon Tracer disabled, Vampirism healing reduced 10x, Venomous slowness reduced |

### Hub NPCs

| NPC | Role |
|---|---|
| Mort | queues you into Catacombs (Party Finder); needs Saul's Recommendation to go solo |
| Adventurer Saul | gives Saul's Recommendation after Floor III |
| Ophelia | shop of dungeon-assist items and gear: Wither Goggles, Giant Cleaver, Death Bow, Steel Chestplate, Mender Crown; also sells Spirit Leap, Trap, Revive Stone-type consumables (?) |
| Malik | essence shops (Undead and Wither), dungeonizing gear, applying reforges, item salvaging |
| Croesus | buy back unclaimed Dungeon Reward Chests (and Kuudra chests) for 72 hours; 60-run cap |
| Crag | Kuudra entrance (out of scope) |
| Vulcan | Crimson Essence shop (out of scope) |
| Guildford | Dungeon Hub racing minigame; 4 objectives per category give Cat (+1 Speed), Lynx (+2) and Cheetah (+3) Talisman |
| Dungeon Hub Selector | switches between Dungeon Hub servers |
| Bessie | (?) not confirmed on the fetched pages; likely a cow NPC tied to Dungeon Hub racing/flavour, ignore until verified |
| Professor Wynd, Lonely Philosopher, Cryptosis | flavour/lore NPCs with NEU items (PROFESSOR_WYND_NPC, LONELY_PHILOSOPHER_NPC, CRYPTOSIS_RIFT_NPC) |

### Floor requirements and map sizes

| Floor | Combat req | Catacombs req | Map (rooms) | Boss |
|---|---|---|---|---|
| Entrance | 15 | - | 4x4 | The Watcher |
| F1 | 1 | 1 | 4x5 | Bonzo |
| F2 | 3 | 3 | 5x5 | Scarf |
| F3 | 5 | 5 | 5x5 | The Professor |
| F4 | 9 | 9 | 6x5 | Thorn |
| F5 | 14 | 14 | 6x5 | Livid |
| F6 | 19 | 19 | 6x6 | Sadan |
| F7 | 24 | 24 | 6x6 | Maxor, Storm, Goldor, Necron |

Master Mode floors M1-M7: Combat/Catacombs level requirement is 24 for M1 and 36 for M7 (wiki), intermediate values roughly +2 per floor (?: 24/26/28/30/32/34/36 guess).

---

## 2. Run structure

Sources: https://hypixelskyblock.minecraft.wiki/w/Dungeon_Rooms , /w/The_Catacombs , /w/Blessings

### Layout

A run is a grid of 32x32-block room cells (?: cell size not on the wiki, widely documented as 32 blocks incl. 1-block door gap) with a 4x4..6x6 footprint (see map sizes above). Rooms are picked from a pool and
stitched together; the Entrance room is always at the edge, the Blood room is the last room before the boss door.

| Shape | Notes |
|---|---|
| 1x1 | most common |
| 1x2, 1x3, 1x4 | long rooms |
| 2x2 | big square |
| L | L-shaped three-cell room |

### Room types

| Type | Colour on map | Description |
|---|---|---|
| Entrance | green | start room; party setup |
| Normal ("Mobs") | grey | star-marked mobs (the mob name shows a star mark, the dungeon star glyph) must be killed for the room to count as cleared |
| Puzzle | purple | at least 2 per floor (wiki: "minimum 2"); failure costs 14 score each (see section 3); Architect's First Draft resets some |
| Trap | orange | generates Floor III-VII only; trap damage was nerfed in 2026 for both modes |
| Miniboss ("Yellow") | yellow | random miniboss (Angry Archaeologist, Lost Adventurer variants, King Midas, Shadow Assassin, Frozen Adventurer), drops a Wither key (?) |
| Fairy | pink | along the Blood room path; killing the fairies grants a revive-like help / revives dead players |
| Blood | red | last room before boss: the Watcher summons undead which must all be killed |
| Rare | (?) | e.g. Gold, Cobble, Buttons rooms etc. (?: names not fetched) |

### Doors and keys

| Door | Opens with | Notes |
|---|---|---|
| Normal door | walk through | between cleared rooms, no cost |
| Wither door (black) | Wither Key | one key per door; 2 Wither keys per floor are guaranteed from rooms (wiki). Keys are picked up by a player and consumed when the door opens |
| Blood door (red) | Blood Key | key drops from the last room before the Blood room (Watcher's room adjacent) |

Wither Keys come from mob/miniboss rooms; they are items that show in the picker's inventory (NEU has no item file for them: missing WITHER_KEY, BLOOD_KEY).

### The Watcher and the blood mobs

| Step | Detail |
|---|---|
| Watcher | stands in the Blood room, invulnerable, spawns waves of Undead from the room's mobs (Zombie/Skeleton/Crypt types, sized to the floor) |
| Win condition | kill every Watcher-summoned mob; the Watcher then despawns and the boss door opens (a teleport portal in later floors) |
| Entrance floor | the Watcher is the only "boss" (4x4 map) |

### Secrets

| Secret type | Detail |
|---|---|
| Chests | most common; some contain Mimic (F6+), some give essence or consumables |
| Items | dropped items found in the room (Dungeon Trap, Spirit Leap, Inflatable Jerry-like consumables, etc.) |
| Bats | killed bats (secret bat) drop a small reward and count as a secret |
| Essence | essence secret chests/ghosts: Wither Essence and Undead Essence amounts scale with floor |
| Per room | typical 0-3 (wiki shows 0, 1-2, 2-3 depending on room), average ~2 (?), whole run 15-30 secrets depending on floor size (?) |
| Dungeon Journals / Fairy souls | collectibles hidden in rooms (wiki) |
| Lonely Spiders | passive-ish spider mobs in secret nooks |

### Crypts, Mimic

| Thing | Detail |
|---|---|
| Crypts | small rooms behind breakable walls; killing the Crypt Undeads in "blown up" Crypts gives up to +5 bonus score (see section 3) |
| Mimic | rarely replaces a secret chest on F6 and above; 1M-2M health normal, up to 150M in Master Mode; drops Mimic Fragment; killing it gives +2 bonus score (message "Mimic Killed!") |

### Blessings

Pickups found in rooms; they stack additively by level (Level II + Level V = VII) and scale with floor.

| Blessing | Effect per level |
|---|---|
| Life | Health and Health Regen +3% (level x 3%) |
| Power | Strength +2% and +4 flat, plus Crit Damage |
| Stone | Defense +2% and +4 flat, plus 6 flat damage |
| Wisdom | Intelligence +2% and +4 flat, plus Speed |
| Time | Health, Defense, Strength and Intelligence +2% each and +4 flat |

Amplifiers: Paul mayor perk +25%, Forbidden Blessing shop upgrade up to +10% (Malik, Wither Essence shop), Floor III+ buffs +20%. Master Mode blessings are capped at Floor VII values (wiki).

### Deaths, revives, ghost mode

| Mechanic | Detail |
|---|---|
| Death | you become a ghost (spectator-like) for the rest of the run; each death costs 2 skill score (4 for solo? (?) not verified) |
| Ghost mode | ghosts can use class "Ghost Abilities" (see section 6), spirit-leap, but not open chests/doors |
| Revive Stone | item (LEGENDARY DUNGEON ITEM): right click to revive a dead player, one use, vanishes at end of dungeon; unlocked in the Undead Essence shop ("Help of the Fairies", 200,000 Undead Essence per NEU) |
| Healer revive | Healer passive "Revive": fairy companion revives fallen allies |
| Fairy Room | killing the fairies revives the dead (wiki) |
| Spirit Leap | RARE DUNGEON ITEM, right click teleports to any teammate, 5 s cooldown, 1 s immunity (cancelled on dealing damage) |
| Dungeon Trap | UNCOMMON DUNGEON ITEM, placed explosive trap, 20,000-250,000 damage depending on floor |

### Puzzles (one-line rules)

Source: https://hypixelskyblock.minecraft.wiki/w/Dungeon_Rooms (names only); rules from general game knowledge (?).

| Puzzle | Rule |
|---|---|
| Three Weirdos | three NPCs each make a statement, one chest holds the answer; open only the true one (?) |
| Water Board | pull levers in the right order so water flows to the doors and opens the exits (?) |
| Ice Fill | slide across the ice patch to cover every tile in one path (?) |
| Ice Path | slide a silverfish through an ice maze to the goal (?) |
| Creeper Beams | shoot the correct lanterns/beams between creepers so lights line up (?) |
| Teleport Maze | find the right teleport pad among many to reach the exit (?) |
| Blaze | kill blazes in order of health (lowest to highest or vice versa) (?) |
| Quiz | answer 3 questions about SkyBlock by stepping on the right pad (?) |
| Tic Tac Toe | win a game of tic tac toe against the AI (?) |
| Boulder | push boulders through a grid to open the way (?) |
| Bomb Defuse, Higher or Lower (newer) | (?) not in fetched list |

---

## 3. Score and rank

Sources: https://hypixelskyblock.minecraft.wiki/w/Dungeon_Score , /w/The_Catacombs

Total score = Skill + Explore + Speed + Bonus. Display cap is 300 (S+).

| Category | Max | How it is calculated |
|---|---|---|
| Skill | 100 | `floor(100 - 2*deaths - 14*failedPuzzles)` (wiki). Older formula 20 + 80*roomsCleared% - ... also circulates (?) |
| Explore | 100 | 60 x (percent of rooms cleared) + up to 40 for secrets (secrets found / required secrets, capped at 40) |
| Speed | 100 | full 100 until the floor's time allowance, then -1 per 12 s down to 90, -1 per 24 s down to 80, -1 per 30 s down to 70 (wiki). Floor time allowances not fetched (?) |
| Bonus | up to ~17 | up to +5 for Crypt Undeads killed in blown-up Crypts; +2 for killing the Mimic; +10 for Paul's EZPZ perk; attribute-based extras |

### Rank thresholds

| Rank | Score |
|---|---|
| D | 0-99 |
| C | 100-159 |
| B | 160-229 |
| A | 230-269 (269.4 on wiki) |
| S | 270-299 (269.5 rounding on wiki) |
| S+ | 300+ |

### Effect on loot

| Effect | Detail |
|---|---|
| Chests | rank gates reward chests: base runs get Wood/Gold/Diamond; A unlocks Emerald; S unlocks Obsidian; S+ on F5+ unlocks Bedrock |
| Chest quality | S+ gives a 1.05x quality multiplier |
| XP | higher score gives more Catacombs and class XP (exact scaling (?)) |
| Boss drops | the chest "quality" (see section 4) is roughly score x floor weight (?) |

---

## 4. Reward chests, Kismet, RNG meter

Sources: https://hypixelskyblock.minecraft.wiki/w/Dungeon_Chest (via summarizer), NEU items KISMET_FEATHER, DUNGEON_CHEST_KEY, constants/rngscore.json

| Chest | Cost (coins) | Unlock |
|---|---|---|
| Wood | free | always |
| Gold | 25,000-100,000 (floor dependent) | always |
| Diamond | 50,000-250,000 | always |
| Emerald | 100,000-500,000 | A rank (or any F7 completion per wiki) |
| Obsidian | 250,000-1,000,000 | S rank (or any F7 completion) |
| Bedrock | 2,000,000 | S+ on F5-F7 only |

Money is taken from the purse first, then bank. Only one chest per run, two with a Dungeon Chest Key (RARE DUNGEON ITEM). Unclaimed chests stay in Croesus for 72 hours (60-run cap).

| Loot mechanic | Detail |
|---|---|
| Loot roll | each chest has a "quality" value acting as currency; each item has a weight and a quality cost |
| Quality boosts | S+ 1.05x; Treasure Talismans/Rings/Artifacts up to 1.03x each (stacked twice); Boss Luck perk (Undead Essence shop: 100 / 1,000 / 10,000 / 100,000 per level); Catacombs Box attributes |
| Kismet Feather | RARE; keep in inventory/sack, rerolls 1 reward chest per run (1 reroll per run) |
| Necron's Ladder | consume after getting 3 Necron's Handles from chests: permanent +1 Health, +1 Intelligence, +1 Magic Find (once) |

### Notable chest loot per floor (from the RNG meter item pool, constants/rngscore.json)

Only items weighted into the RNG meter; the number is the meter score (S+ pool: 300 points needed; S pool: 189) required to be able to target that item, equal to the meter XP cost. (?: whether the number is the "cost to select".)

| Floor | Top meter entries (cost) |
|---|---|
| F1 | Bonzo's Staff 31,800; Ultimate Jerry I 31,800; Fuming Potato Book 15,900; Ultimate Bank I 15,900; Recombobulator 10,599; Bonzo's Mask 10,599; Red Nose 10,599; Balloon Snake 7,950 |
| F2 | Red Scarf 18,900; Recombobulator 9,450; Stone Blade 9,450; Ultimate Bank I 9,450; Adaptive Belt 6,300; Scarf Studies 4,725 |
| F3 | Adaptive Chestplate/Leggings 26,100; Ultimate Jerry II 26,100; Adaptive Helmet/Boots 13,050; Suspicious Vial 5,220 |
| F4 | Spirit Sword / Spirit Bow / Thorn's Boots / Spirit I-IV 24,899; Spirit Wing, Bone, Decoy 12,449; Ultimate Rend II 12,449 |
| F5 | Shadow Fury 114,300; Last Breath 114,300; Shadow Assassin Chestplate 57,150; AOTE Stone 57,150; Livid Dagger 28,575; Dark Orb 3,810 |
| F6 | Precursor Eye 266,699; Giant's Sword 160,020; Necromancer Lord Chestplate 100,012; Summoning Ring 80,010; Fel Skull 53,339; Necromancer Sword 40,005; Soulweaver Gloves 26,669; Sadan Brooch 20,002 |
| F7 | Storm/Maxor/Goldor the Fish 411,180; Necron's Handle 274,119; Shadow Warp / Wither Shield / Implosion scroll 205,590; Auto Recombobulator 51,397; Wither Chestplate 51,397; Ultimate One For All 51,397; Wither Leggings 12,849; Wither Cloak/Helmet/Boots/Blood 8,566 |
| M7 | adds Dye Necron 1,000,000; Dark Claymore 416,820; Thunderlord VII 208,410; Master Skull Tier 5 173,675; Fifth Master Star 138,940; Precursor Gear 3,473 |
| M1-M6 | same item families with the +master items (e.g. M6: Precursor Eye 270,800; Giant's Sword 135,400) |

### RNG meter

| Field | Value |
|---|---|
| Meter structure | per floor (F1-F7, M1-M7): a table of item -> required meter XP; you choose one item to target |
| Meter XP per run | score above the S threshold: the pool keys are `S+` = 300 and `S` = 189 (rngscore.json `rng_meter_dungeon_score`). Interpretation: S+ run gives 300-ish meter points, S gives 189 (?) |
| Spend | when a chest's selected item reaches its cost the item is guaranteed |
| Slayer meter | separate (not used here) |

---

## 5. Floors, bosses and Master Mode

Sources: https://hypixelskyblock.minecraft.wiki/w/Necron , NEU items *_BOSS lore, constants/rngscore.json for drops

Boss health below is from NEU item lore (normal / Master Mode). Phases from NEU lore plus general knowledge (?).

| Floor | Boss | HP normal / MM | Phases and mechanics |
|---|---|---|---|
| F1 | Bonzo ("New Necromancer") | 250,000 / 200,000,000 | 2 phases: first he flees until killed, then revives as a clown and attacks with balloons |
| F2 | Scarf ("Apprentice Necromancer") | 1,000,000 / 375,000,000 | 2 phases: barrier while 4 undead servants spawn; kill all 4, barrier drops, kill Scarf (3 minion types with Scarf's phase 2 (?)) |
| F3 | The Professor | 3,000,000 / 600,000,000 | 3 phases: 4 Guardians + barrier; after they die he attacks; at 25% HP he merges with the guardians |
| F4 | Thorn ("Shaman Necromancer", ghast) | 4 hits / 6 hits | does not attack; spawn Spirit animals, kill them to spawn a Spirit Bear, which drops the single-shot Spirit Bow, the only way to damage him |
| F5 | Livid | 7,000,000 / 600,000,000 | 1 phase: eight clones with different abilities; kill the real Livid (the clone with different name colour) |
| F6 | Sadan ("Necromancer Lord") | 40,000,000 / 800,000,000 | 3 phases: terracotta army survived for 100 s; 4 giants (Bigfoot, Sadan's giants L.A.S.R., Jolly Pink Giant, Diamante) spawn, kill them; then Sadan merges them and is fought |
| F7 | Maxor / Storm / Goldor / Necron ("Wither Lords") | see below | 4 phases, one lord each |

### F7 phases

| Phase | Boss | HP normal / MM | Defense (MM) | Mechanic |
|---|---|---|---|---|
| 1 | Maxor | 100,000,000 / 800,000,000 | 1,000 | frozen by Energy Crystals; carry crystals to him; he is damageable only while the crystal is placed (?) |
| 2 | Storm | 400,000,000 / 1,000,000,000 | 1,200 | crushed by pillars (crush him with the falling pillars to make him vulnerable) |
| 3 | Goldor | 750,000,000 / 1,200,000,000 | 1,800 | solve terminals/devices/levers in 4 sections before killing him (tick damage until done) |
| 4 | Necron | 1,000,000,000 / 1,400,000,000 | 2,100 | always vulnerable; frenzy at 80% and 5% HP (10 s explosion ring), wither skulls, fireballs, lightning, summoned Diamond Swords, rotating beam (25% of player HP true damage), lava ring platform at start |
| MM only | Withered Dragons (Wither King fight) | 900,000,000 | 3,700 | five dragons (Soul, Power, Ice, Flame, Apex) spawn after Necron in MM F7 |

Boss damage 7,200-300,000 (wiki, Necron), boss level 739-1,094 (wiki).

### Notable drops per floor (summary from the RNG pools above)

| Floor | Main drops |
|---|---|
| F1 | Bonzo's Staff, Bonzo's Mask, Red Nose, Balloon Snake, Ultimate Jerry I, Fuming Potato Book |
| F2 | Red Scarf, Stone Blade, Adaptive Belt, Scarf Studies/Thesis/Grimoire |
| F3 | Adaptive armor set (helmet/chestplate/leggings/boots), Suspicious Vial |
| F4 | Spirit Sword, Spirit Bow, Thorn's Boots, Spirit Wing/Bone/Decoy, Spirit enchant |
| F5 | Shadow Fury, Last Breath, Livid Dagger, Shadow Assassin armor, Dark Orb, AOTE Stone |
| F6 | Giant's Sword, Precursor Eye, Necromancer Lord armor, Necromancer Sword, Soulweaver Gloves, Fel Skull, Summoning Ring |
| F7 | Wither armor set pieces (Wither Chestplate/Leggings/Helmet/Boots/Cloak/Blood), Necron's Handle, Wither Catalyst, Implosion/Shadow Warp/Wither Shield scrolls (craft Hyperion/Astraea/Scylla/Valkyrie with Necron Blade), Storm/Maxor/Goldor "the Fish", Ultimate One For All, Soul Eater (M7) |
| Other | Dungeon Chest Key, Kismet Feather, enchant books (Lethality VI, Thunderlord VI) |

### Master Mode (wiki gives no multipliers)

| Aspect | Value |
|---|---|
| Unlock | defeat Necron on F7 |
| Levels | Combat/Catacombs 24 (M1) up to 36 (M7), floors named M1-M7 |
| Boss HP | see table above: e.g. Bonzo 250k -> 200M, Sadan 40M -> 800M, Necron 1B -> 1.4B (large, non-uniform jump) |
| Mob HP | NEU health ranges (see section 9) show MM mobs at ~40-70x normal F1 values; no single multiplier (?) |
| Extra drops | Master Skull heads (Diamond/Gold heads of each boss), Master Stars (item stars 6-10), Dye Necron, Thunderlord VII, Dark Claymore, Soul Eater, Precursor Gear, extra RNG pool entries |
| Mimic | up to 150M HP |
| Blessings | cap at Floor VII values |
| Other | MM F7 adds the Withered Dragon phase after Necron (wiki and NEU lore) |

---

## 6. Classes

Sources: https://hypixelskyblock.minecraft.wiki/w/Healer , /Tank , /Mage , /Berserk , /Archer

Each class has Class Passives, two Dungeon Orb ("Dungeon Stone") abilities (an ultimate-style pair), and Ghost abilities used while dead. Level 1-50 progress grants bonuses; XP table in section 7 style (class XP uses the same table (?)).

| Class | Role | Passives | Dungeon Orb abilities | Ghost abilities | Level 50 highlights |
|---|---|---|---|---|---|
| Healer | support | Renew (+50% heal), Healing Aura (8 blocks), Revive (fairy revives allies), Orbies (15% chance orb on kill), Soul Tether (30 blocks), Overheal (absorption shield) | Healing Circle (2 s CD), Wish (120 s CD: full heal + absorption 20% max HP) | (?) healing-related | healing percentages doubled, reduced cooldowns, higher shields |
| Mage | magic AoE | Mage Staff (melee becomes ranged: (30% + 0.9 x Int/1000) of melee dmg up to 10 blocks), Efficient Spells (-25% ability cooldowns) | Guided Sheep (30 s CD, breaks weak walls), Thunderstorm (10-block radius, 15 s) | Instant Wall (5x3 for 10 s), Fireball | +250 Intelligence (total 500), +10 ability damage (total 15), +25% CDR (total -50%) |
| Berserk | melee | Bloodlust (next hit stronger after a kill, 5 s), Lust For Blood (stacking damage), Indomitable (Strength to Defense), Weapon Master (hit 5 enemies in front cone) | Throwing Axe (10 s CD, damage = highest hit in past minute), Ragnarok (60 s CD: +100 attack speed, +400 speed, +50% damage, 15 s, 3 zombie minions) | Buff Potion (+30 Strength within 10 blocks), Ghost Axe | +77.5% melee dmg, +40% Bloodlust, +50% Throwing Axe, +34 Speed, 1100% Lust cap |
| Archer | ranged DPS | Doubleshoot (50% extra arrow), Bone Plating (damage reduction, 250 s CD), Bouncy Arrows (ricochet, 15 s CD) | Explosive Shot (3 arrows, 4-block blast = strongest recent bow hit), Rapid Fire (5 arrows/s for 4 s at 75% damage) | Healing Bow (10% HP + 50 Strength for 10 s), Stun Bow (5 s CD) | 101% extra arrow chance, +230% arrow dmg, 100% bounce, Explosive Shot CD -20 s, Rapid Fire 8 s |
| Tank | defence | baseline +100 Health, +50 Defense, +10 Vitality; Protective Barrier (+25% Defense, shield below 50% HP), Taunt (above 25% HP), Diversion (redirect 80% of teammates' damage within 30 blocks), Defensive Stance (knockback immune) | Seismic Wave (15 s CD, 20,000 + 10% per 50 Defense), Castle of Stone (150 s CD: 70% damage reduction 20 s, aggroes) | Stun Potion (10-block stun), Absorption Potion (+200 absorption for 3 s) | +51 Defense, +5.1 Vitality, +10% Defense |

### Class XP, selection and kits

| Rule | Value |
|---|---|
| Choice | pick a class in the starting room (entrance) before the run, 1 of each per party (duplicates allowed? (?)) |
| Class XP | gained on completing a run, scaling with score; the player's selected class gets full XP (split with other classes: 25% (?)) |
| Class level | 0-50, class XP table same shape as the Catacombs table (?) |
| Kits | no starting kit; players bring their own gear (?) |
| Accessory | Scarf's Grimoire: +6% dungeon class XP |

---

## 7. Catacombs skill

Sources: NEU constants/leveling.json (`catacombs` key); https://hypixelskyblock.minecraft.wiki/w/Catacombs_(skill) 404, so the bonus effects below are mostly (?)

The XP table in the NEU repo has 50 meaningful entries (50 to 116,250,000), followed by entries of 200,000,000 each for levels beyond 50 (the extended catacombs 51+ levels).

| Level | XP for this level | Cumulative XP |
|---|---|---|
| 1 | 50 | 50 |
| 2 | 75 | 125 |
| 3 | 110 | 235 |
| 4 | 160 | 395 |
| 5 | 230 | 625 |
| 6 | 330 | 955 |
| 7 | 470 | 1,425 |
| 8 | 670 | 2,095 |
| 9 | 950 | 3,045 |
| 10 | 1,340 | 4,385 |
| 11 | 1,890 | 6,275 |
| 12 | 2,665 | 8,940 |
| 13 | 3,760 | 12,700 |
| 14 | 5,260 | 17,960 |
| 15 | 7,380 | 25,340 |
| 16 | 10,300 | 35,640 |
| 17 | 14,400 | 50,040 |
| 18 | 20,000 | 70,040 |
| 19 | 27,600 | 97,640 |
| 20 | 38,000 | 135,640 |
| 21 | 52,500 | 188,140 |
| 22 | 71,500 | 259,640 |
| 23 | 97,000 | 356,640 |
| 24 | 132,000 | 488,640 |
| 25 | 180,000 | 668,640 |
| 26 | 243,000 | 911,640 |
| 27 | 328,000 | 1,239,640 |
| 28 | 445,000 | 1,684,640 |
| 29 | 600,000 | 2,284,640 |
| 30 | 800,000 | 3,084,640 |
| 31 | 1,065,000 | 4,149,640 |
| 32 | 1,410,000 | 5,559,640 |
| 33 | 1,900,000 | 7,459,640 |
| 34 | 2,500,000 | 9,959,640 |
| 35 | 3,300,000 | 13,259,640 |
| 36 | 4,300,000 | 17,559,640 |
| 37 | 5,600,000 | 23,159,640 |
| 38 | 7,200,000 | 30,359,640 |
| 39 | 9,200,000 | 39,559,640 |
| 40 | 12,000,000 | 51,559,640 |
| 41 | 15,000,000 | 66,559,640 |
| 42 | 19,000,000 | 85,559,640 |
| 43 | 24,000,000 | 109,559,640 |
| 44 | 30,000,000 | 139,559,640 |
| 45 | 38,000,000 | 177,559,640 |
| 46 | 48,000,000 | 225,559,640 |
| 47 | 60,000,000 | 285,559,640 |
| 48 | 75,000,000 | 360,559,640 |
| 49 | 93,000,000 | 453,559,640 |
| 50 | 116,250,000 | 569,809,640 |

| Rule | Value |
|---|---|
| Max normal level | 50 (total 569,809,640 XP) |
| Beyond 50 | extra levels at 200,000,000 XP each (NEU data), bonuses are small |
| Stat bonus | each level gives dungeon-only bonuses to base Health and weapon/armour stats; commonly cited as +5% per level up to level 50 total (?) |
| Where it applies | multiplies the dungeon-only base stats of every dungeon item |
| XP source | run completion: base XP for the floor, scaled by score (S+ gives most), not by kills; extra from secrets/Blood mobs (?) |
| Floor XP anchor | F7 S+ run roughly 20k-30k XP at a lower level (?) |

---

## 8. Dungeon items

Sources: NEU constants/essencecosts.json, constants/essenceshops.json, items lore; https://hypixelskyblock.minecraft.wiki/w/Dungeon_Stars (404)

| Concept | Detail |
|---|---|
| Dungeonized | many items have a normal and a dungeon version: inside dungeons dungeon-only base stats apply; Malik applies "dungeonization" outside |
| Quality | an item dropped from a dungeon chest gets Quality 0-50 (random), which adds 0-50% on top of base stats (Quality 50 = max) (?) |
| Floor tier | the floor where it dropped (F1..F7) multiplies stats by the floor's multiplier; item shows "Obtained in floor N" |
| Dungeon Item Level | Gear Score is shown (e.g. Felthorn Reaper: gear score 367) and sets stat power; items need floor completion (e.g. "Requires The Catacombs Floor VI Completion") |
| Stars 1-5 | upgrade at an anvil with essence + coins: stars 4 cost 10,000 coins, star 5 costs 25,000 coins |
| Master Stars 6-10 | five Master Star items (First to Fifth) combined in the anvil on a 5-star item, each +5% stats in Master Mode only; essence type of NEU ends at 5 stars for dungeon gear |
| Essence | Wither (most gear), Undead (soft), Dragon (e.g. Terminator), Gold (Midas Staff), Diamond, Spider, Ice, Crimson (Kuudra) etc. |

### Essence cost summary (essencecosts.json)

529 items; types: Crimson 179, Wither 95, Undead 94, Diamond 63, Dragon 38, Spider 31, Gold 15, Ice 11, Forest 3. 350 items have a 5-star ladder, 146 have 10 stars (Kuudra armors).

| Item | Type | Star 1 | 2 | 3 | 4 | 5 | Notes |
|---|---|---|---|---|---|---|---|
| Bonzo's Staff | Wither | 5 | 10 | 25 | 50 | 100 | star 4 +10,000 coins, 5 +25,000 coins |
| Spirit Sword | Wither | 20 | 50 | 100 | 200 | 300 | |
| Adaptive Chestplate | Wither | 20 | 50 | 100 | 200 | 300 | |
| Adaptive Boots | Wither | 10 | 25 | 50 | 100 | 150 | |
| Livid Dagger | Wither | 30 | 60 | 120 | 250 | 400 | |
| Shadow Assassin Chestplate | Wither | 35 | 75 | 150 | 300 | 500 | |
| Wither Goggles | Wither | 40 | 80 | 150 | 300 | 600 | |
| Giant's Sword | Wither | 75 | 150 | 300 | 600 | 1,200 | |
| Wither Chestplate (Power Wither) | Wither | 100 | 200 | 350 | 600 | 1,000 | |
| Hyperion / Scylla / Astraea / Necron's Blade / Dark Claymore | Wither | 150 | 300 | 500 | 900 | 1,500 | |
| Terminator | Dragon | 100 | 200 | 300 | 500 | 750 | |
| Midas Staff | Gold | 50 | 100 | 150 | 200 | 250 | |

The 10-star ladder seen on the (non-dungeon) Crimson gear goes 20/40/60/80/100/150/200/250/300/350 essence and Wither Souls plus coins from star 6 (50,000 up to 1,000,000 coins).

### Essence shops (essenceshops.json, Malik)

| Currency | Perk (levels: costs) |
|---|---|
| Undead Essence | Boss Luck 4 levels (100 / 1,000 / 10,000 / 100,000); Looting 5 (1,000-5,000); Health Essence 5 (1,000 / 2,500 / 5,000 / 10,000 / 25,000); Defense 5 (1,000 / 4,000 / 6,000 / 8,000 / 10,000); Strength 5 (same); Intelligence 5 (same); Critical 5 (1,000 / 3,000 / 10,000 / 20,000 / 50,000); Help of the Fairies (revive stone) 1 (200,000) |
| Wither Essence | Forbidden Health/Defense/Intelligence/Strength 5 levels each (100 / 250 / 500 / 1,000 / 1,500); Forbidden Speed 2 (100 / 250); Forbidden Blessing 10 (200-2,000 in steps of 200); Echos of the Lost (Dungeonbreaker secrets) 5 (250 / 500 / 1,000 / 2,000 / 5,000) |
| Dragon Essence | Dragon-related perks (End content, out of scope) |
| Other essences | Spider, Ice, Crimson, Gold, Diamond, Forest, Fossil, Safari shops exist (other systems) |

---

## 9. Dungeon mobs

Sources: NEU items/*_MONSTER.json lore (health range = lowest floor of appearance to F7; MM = Master Mode range); https://hypixelskyblock.minecraft.wiki/w/Dungeon_Mobs (via summarizer)

Mob stats scale with floor and with "clear progress": wiki formula `CurrentAmount x (1 + 0.05 x (TotalRoomsExploredBefore - 1))` (lossy, (?)).

| Mob | Health normal (low floor to F7) | Health Master Mode | Notes |
|---|---|---|---|
| Zombie Grunt | 22,000 - 120,000 | 1,600,000 - 3,000,000 | basic melee |
| Zombie Soldier | 140,000 - 1,200,000 | 6,000,000 - 36,000,000 | F3-F7; wiki: F3 77,000 HP / 738 dmg, F7 36M / 111,600 dmg (MM) (?) |
| Zombie Knight | 170,000 - 1,280,000 | 7,000,000 - 40,000,000 | |
| Zombie Commander | 1,700,000 - 3,500,000 | 160,000,000 - 250,000,000 | leader, mini-boss like |
| Skeleton Soldier | 31,000 - 750,000 | 1,600,000 - 28,000,000 | |
| Skeleton Master | 59,000 - 900,000 | 3,450,000 - 42,000,000 | F2-F7; wiki: F2 31,200 HP / 945 dmg, F7 42M / 182,000 dmg (MM) (?) |
| Skeleton Lord | (?) | (?) | no NEU item found |
| Crypt Lurker | 12,000 - 420,000 | 850,000 - 13,000,000 | |
| Crypt Dreadlord | 19,000 - 840,000 | 1,050,000 - 20,000,200 | |
| Crypt Soul Eater | 18,000 - 800,000 | 1,100,000 - 20,000,000 | |
| Super Archer | 400,000 - 1,600,000 | 48,000,000 - 200,000,000 | bouncy archer |
| Sniper | 13,000 - 36,000,000 | (?) | skeleton bow mob |
| Withermancer | 300,000 - 88,000,000 | (?) | wither magic |
| Cellar Spider | 22,000 - 420,000 | 500,000 - 9,500,000 | |
| Lonely Spider | 5,000 - 120,000 | 140,000 - 2,000,000 | |
| Lost Adventurer (Young/Unstable/Superior/Holy) | 60,000 - 7,000,000 | 8,000,000 - 160,000,000 | miniboss |
| Angry Archaeologist | 20,000 - 900,000 | 1,000,000 - 12,000,000 | miniboss |
| Shadow Assassin | 700,000 - 20,000,000 | 55,000,000 - 145,000,000 | miniboss, teleports |
| Mimic | 1,000,000 - 2,000,000 | up to 150,000,000 | F6+ |
| Fels / Frozen Adventurer / King Midas | (?) | (?) | not fetched |

### Star mobs

| Rule | Detail |
|---|---|
| Star mobs | mobs with a star glyph on the name tag; these are required to clear the room (counted toward room completion for the 60 Explore points) |
| Drops | cleared star mobs in a room allow the room to show cleared; in Blood room Watcher mobs count too |
| Dungeon level | mob level scales with floor, F7 mob levels reach several hundred (?) |

---

## Biggest uncertainties

1. Official rules for Dungeon Class XP, Catacombs skill stat bonus and XP per run were not retrievable (pages 404); only the NEU XP table is solid.
2. Puzzle rules, room list (rare rooms), secret counts per room and time allowances per floor come from general knowledge, not fetched pages.
3. Item Quality/Floor-tier/Dungeon Item Level multipliers and Master Star mechanics (stars 6-10) are not numerically confirmed.
4. Chest opening cost per floor (25k-100k gold, etc.) is a range only; the exact per-floor price list was not obtained.
5. Skeleton Lord, Fels, King Midas and Frozen Adventurer stats are missing; Master Mode mob multiplier is not a single number.
6. RNG meter interpretation (S 189 / S+ 300 per run) is a guess from rngscore.json.
7. Bessie and Ophelia's exact stock were not verified.
