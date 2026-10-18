# Mayors & Elections

Hypixel's elections on the SkyBlock calendar (`mayor/Mayors`, `mayor/ElectionMenu`, `skyrpg/mayors/mayors.json`).
Sources: the Hypixel API's election resource and the community wiki (research 2026-10-18).

## The cycle (Hypixel's)

- Election `Y` opens on the 27th of Late Summer of SkyBlock year `Y` and closes on the 27th of Late Spring of `Y + 1`.
  The winner is **Mayor** (all their perks) for one SkyBlock year (124 real hours); the runner-up is **Minister**
  (the one perk marked for the Minister). There is no election between Late Spring 27 and Late Summer 27.
- Five candidates. Regular: Aatrox, Cole, Diana, Diaz, Finnegan, Foxy, Marina, Paul (last term's Mayor and Minister
  sit out; Diaz's Long Term Investment brings the Minister back with every perk). Every 8 years a special candidate
  in the last slot, with all their perks: Jerry, Scorpius, Derpy in turn, on Hypixel's years (Jerry 520, Scorpius 528,
  Derpy 536). A special runner-up leaves no Minister.
- Hypixel's real calendar epoch, so our years match Hypixel's (year 520 now, with Jerry running).

## Decided

- **Candidates are rolled from the election year** (the same on every server and after restarts): the regular
  candidates, how many perks each runs with (1: 25%, 2: 40%, 3: 27%, 4: 8%; Hypixel's carry-over between elections
  isn't modelled) and the Minister perk.
- **Players' votes decide**; one vote per player, changeable until the election closes. Ties, and an election nobody
  voted in (a quiet server), go to a popularity rolled per election, so there's always a Mayor.
- Votes, results and bribes are kept in the world (`elections` saved data); the first election the world sees is the
  one in office then, with nobody excluded.
- **Voting**: Clerk Seraphine on the Hub plaza (south-west of the fountain) or `/election`; `/mayor` shows the
  perks in effect. Admins: `/sbmayor set <mayor>` (this term, every perk), `/sbmayor clear`, `/sbmayor year <y>`.
- **Scorpius's Bribe**: Hypixel's coins by playtime (50k under 50 h, 100k, 250k, 500k, 1M from 300 h), claimed at
  the Election once per election if you voted for him and he won.

## Perks in effect

Stats (`Mayor` stat source): Pathfinder (+20 Magic Find, decided for "rare drops 20% more often"), Huntress'
Intuition (+10 Tracking), Luck of the Sea 2.0 (+15 Sea Creature Chance), Mining XP Buff (+60 Mining Wisdom on public
islands), Mining Fiesta (+25 Mining Wisdom and +25 Mining Fortune on public islands, decided for Hypixel's +25%),
Fishing XP Buff (+50 Fishing Wisdom on public islands), MOAR SKILLZ (+50 to every Wisdom), Statspocalypse (+10% of the
combat stats). Jerry's Perkpocalypse: all of Aatrox's, Cole's, Diana's, Finnegan's, Marina's and Paul's perks in turn,
18 SkyBlock days each.

Hooks in the systems (`core.world.Mayor.active`, installed by the elections): SLASHED Pricing (Slayer quests half
price), Slayer XP Buff (+25%), Molten Forge (forge jobs 25% shorter), Pet XP Buff (+35%), Prospection (mining
minions 25% faster), TURBO MINIONS (minion output x2), QUAD TAXES (the Auction House's claim tax and the Bazaar's sell
tax x4; listing fees aren't taxes), DOUBLE MOBS HP (zone monsters), Marauder (dungeon chests 20% cheaper), EZPZ (+10
Dungeon score), Pest Eradicator (a fifth off the wait between pests), Volume Trading (Shen's Auctions: twice the
winners), Double Trouble (+0.1 Double Hook Chance per Sea Creature Chance), Scorpius's Bribe.

## Part 2: the mayor events

- **Calendar**: the client now gets the perks in effect (`MayorSync`), so the calendar shows the Mayor's events:
  the Fishing Festival (first 3 days of every month), the Mythological Ritual and the Mining Fiesta (all term).
- **Marina's Fishing Festival** (`FishingFestival`, `SkySeaCreatures`): Hypixel's start and end messages; during it,
  15% of water sea creatures are sharks (Shark Bait +20%), picked by Hypixel's weights from those your Fishing level
  unlocks: Nurse (5, weight 2,875), Blue (10, 1,500), Tiger (18, 500), Great White (24, 100), with the wiki's levels,
  health, damage, XP and coins and NEU's drops (fins, teeth, Megalodon pets, Carnival Tickets). Decided: sharks look
  like dolphins.
- **Cole's Mining Fiesta** (`MiningFiesta`): Refined Minerals from ores and Dwarven Metals and Glossy Gemstones from
  gemstones, Hypixel's chance (the block's Breaking Power / 5000).
- **Diana's Mythological Ritual** (`Diana`): Diana on the Hub plaza sells the Griffin (25,000 coins, during her ritual)
  and the Ancestral Spade (5,000 coins and 32 Gold Ingots). Hold a spade in the Hub and three start burrows (green)
  wait on the grass near you; left-click one to dig it, opening a chain of 4 / 6 / 8 burrows (Ancestral, Archaic,
  Deific); the spade's Echo (right-click) draws a trail to the next. Every later burrow is 75% a mob (red) and 25%
  treasure (gold): Hypixel's treasure tables per spade (Griffin Feathers, coins, Mythos Fragments, Braided Griffin
  Feather, Myth the Fish), the Crown of Greed doubling coins; and Hypixel's twelve Mythological mobs, the pool growing
  with the Griffin's rarity (Minos Hunter and Siamese Lynxes at Common to Manticore and King Minos at Mythic, elusive
  ones x (1 + Tracking / 100)), each with the wiki's level, health, damage, coins and XP at every rarity, Ancient
  Claws and Enchanted Gold, and its rare drop (Hilt of Revelations, Crochet Tiger Plushie, Washed-up Souvenir, Cretan
  Urn, Antique Remedies, Dwarf Turtle Shelmet, Daedalus Stick, Minos Relic, Chimera I, Brain Food, Fateful Stinger,
  Manti-core, Crown of Greed, Shimmering Wool) with Magic Find. A mob must be beaten for its burrow to count, and only a
  Griffin of at least its rarity can hurt it. Decided: burrows come 15 to 50 blocks away; the trail is yellow under 25
  blocks, red under 60, grey beyond; mob burrows need a Griffin out.
- **More perks**: Benediction (blessings +25%), GOATed (contest brackets reach 10% further), Blooming Business (+10%
  Copper, rarer visitors 20% more likely).
- Fix found on the way: anything a card-menu click put in or took from your inventory (purchases) didn't show until
  the inventory re-synced; card menus now resend the inventory when they close.

## Not yet (part 3)

The mobs' special attacks (Gaia Construct's stages, the Minotaur's bleed, the Champion's and Inquisitor's damage
ramps, the Sphinx's quiz, King Minos's phases), Griffin Upgrade Stones at Kat, Foxy's Carnival and Extra Event,
Jerrypocalypse, Darker Auctions, Diaz's Stonks Auction and Shopping Spree, Grand Feast, Sharing is Caring, Sweet
Benevolence, A Time for Giving. Voting weight (Fame, Voter's Badge) and Hypixel's perk carry-over.
