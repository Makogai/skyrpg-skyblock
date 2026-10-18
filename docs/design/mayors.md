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

## Not yet (part 2)

The events: Diana's Mythological Ritual (Griffin burrows, the Inquisitor), Marina's Fishing Festival (sharks), Cole's
Mining Fiesta drops, Foxy's Carnival and Extra Event, Jerrypocalypse, Darker Auctions, Diaz's Stonks Auction and
Shopping Spree, Finnegan's GOATed, Blooming Business and Grand Feast, Diana's Sharing is Caring, Paul's Benediction,
Sweet Benevolence, A Time for Giving. Voting weight (Fame, Voter's Badge) and Hypixel's perk carry-over.

