package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Shen's Auctions in the Rift (Hypixel's), run by Damia on Barrier Street: a new auction every SkyBlock year, bids in
 * Motes. Each lot goes to its top bidders (Dead Cat Food 60, Flex Helmet 15, Placeable Fairy Soul 15, Punchcard Artifact
 * 25, Rift Prism 25, Ancient Elevator 15). Bidding takes the Motes; when the year ends the winners collect their lot and
 * everyone else their Motes back, from Damia. Decided: the opening bid (1,000 Motes) and the raises (Hypixel's weren't
 * found), one bid per lot per player, raised as often as you like.
 */
final class ShenAuction {
    record Lot(String item, int winners) {
    }

    static final List<Lot> LOTS = List.of(new Lot("DEAD_CAT_FOOD", 60), new Lot("FLEX_HELMET", 15), new Lot("PLACEABLE_FAIRY_SOUL_RIFT", 15),
            new Lot("PUNCHCARD_ARTIFACT", 25), new Lot("RIFT_PRISM", 25), new Lot("ANCIENT_ELEVATOR", 15));
    private static final long OPENING = 1_000;
    private static final long[] RAISES = {1_000, 10_000};
    private static final Map<UUID, Integer> CHOSEN = new HashMap<>();

    /** One bid. */
    record Bid(String player, String lot, long motes) {
        static final Codec<Bid> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("player").forGetter(Bid::player),
                Codec.STRING.fieldOf("lot").forGetter(Bid::lot),
                Codec.LONG.fieldOf("motes").forGetter(Bid::motes)).apply(i, Bid::new));
    }

    /** This year's bids, and what last year's left to collect (lots won, Motes back). */
    static final class Book extends SavedData {
        static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("year", -1).forGetter(b -> b.year),
                Bid.CODEC.listOf().optionalFieldOf("bids", List.of()).forGetter(b -> b.bids),
                Bid.CODEC.listOf().optionalFieldOf("won", List.of()).forGetter(b -> b.won),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("refunds", Map.of()).forGetter(b -> b.refunds)).apply(i, Book::new));
        static final SavedDataType<Book> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("shen_auction"), Book::new, CODEC, null);

        int year;
        final List<Bid> bids;
        final List<Bid> won;
        final Map<String, Long> refunds;

        private Book() {
            this(-1, List.of(), List.of(), Map.of());
        }

        private Book(int year, List<Bid> bids, List<Bid> won, Map<String, Long> refunds) {
            this.year = year;
            this.bids = new ArrayList<>(bids);
            this.won = new ArrayList<>(won);
            this.refunds = new HashMap<>(refunds);
        }

        static Book get(MinecraftServer server) {
            Book book = server.overworld().getDataStorage().computeIfAbsent(TYPE);
            book.roll();
            return book;
        }

        /** A new SkyBlock year: last year's lots go to their top bidders, the rest get their Motes back. */
        private void roll() {
            int current = SkyBlockTime.now().year();
            if (current == year) {
                return;
            }
            for (Lot lot : LOTS) {
                List<Bid> ranked = bids.stream().filter(b -> b.lot().equals(lot.item())).sorted(Comparator.comparingLong(Bid::motes).reversed()).toList();
                for (int k = 0; k < ranked.size(); k++) {
                    Bid bid = ranked.get(k);
                    if (k < lot.winners()) {
                        won.add(bid);
                    } else {
                        refunds.merge(bid.player(), bid.motes(), Long::sum);
                    }
                }
            }
            bids.clear();
            year = current;
            setDirty();
        }

        Bid mine(String player, String lot) {
            return bids.stream().filter(b -> b.player().equals(player) && b.lot().equals(lot)).findFirst().orElse(null);
        }

        /** Your place among this lot's bids (1 = highest). */
        int rank(String player, String lot) {
            Bid mine = mine(player, lot);
            return mine == null ? 0 : 1 + (int) bids.stream().filter(b -> b.lot().equals(lot) && b.motes() > mine.motes()).count();
        }
    }

    private ShenAuction() {
    }

    static void open(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            Book book = Book.get(p.level().getServer());
            String me = p.getUUID().toString();
            long motes = Motes.get(p);
            List<CardView.Card> cards = new ArrayList<>();
            long owed = book.refunds.getOrDefault(me, 0L);
            List<Bid> wins = book.won.stream().filter(b -> b.player().equals(me)).toList();
            if (owed > 0 || !wins.isEmpty()) {
                cards.add(new CardView.Card("Collect last year's", (wins.isEmpty() ? "" : wins.size() + " lot" + (wins.size() == 1 ? "" : "s") + " won")
                        + (owed > 0 ? (wins.isEmpty() ? "" : " · ") + Msg.number(owed) + " Motes back" : ""), 0x55FF55, true, "Collect",
                        RiftNpcs.icon("RIFT_PRISM"), "", List.of(), -1, ""));
            }
            for (Lot lot : LOTS) {
                Bid mine = book.mine(me, lot.item());
                long bidders = book.bids.stream().filter(b -> b.lot().equals(lot.item())).count();
                int rank = book.rank(me, lot.item());
                ItemStack icon = RiftNpcs.icon(lot.item());
                cards.add(new CardView.Card(icon.isEmpty() ? lot.item() : icon.getHoverName().getString(), mine == null ? "Top " + lot.winners() + " bids win  ·  "
                        + bidders + " bidder" + (bidders == 1 ? "" : "s") : "Your bid: " + Msg.number(mine.motes()) + "  ·  #" + rank
                        + (rank <= lot.winners() ? " (winning)" : " (outbid)"), 0xFFFFFF, true, mine == null ? "Bid" : "Raise", icon, "", List.of(), -1, ""));
            }
            long left = SkyBlockTime.start(SkyBlockTime.now().year() + 1, 0, 1) - System.currentTimeMillis();
            return new CardView("RIFT", "Damia", "Shen's Auctions", npc, List.of(Chip.balance("motes", motes)), cards,
                    "This year's auction ends in " + SkyBlockTime.countdown(left) + ". Bids are paid now, refunded if you lose.", 1, 34);
        }, (p, card) -> {
            Book book = Book.get(p.level().getServer());
            String me = p.getUUID().toString();
            boolean collect = book.refunds.getOrDefault(me, 0L) > 0 || book.won.stream().anyMatch(b -> b.player().equals(me));
            if (collect && card == 0) {
                long owed = book.refunds.getOrDefault(me, 0L);
                if (owed > 0) {
                    Motes.give(p, owed);
                    book.refunds.remove(me);
                }
                for (Bid bid : List.copyOf(book.won)) {
                    if (bid.player().equals(me)) {
                        give(p, bid.lot());
                        book.won.remove(bid);
                    }
                }
                book.setDirty();
                Msg.playSound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
                return "";
            }
            CHOSEN.put(p.getUUID(), card - (collect ? 1 : 0));
            bid(p, npc);
            return null;
        });
    }

    /** A bid on one lot: the opening bid, or a raise on yours. */
    private static void bid(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            Lot lot = LOTS.get(CHOSEN.getOrDefault(p.getUUID(), 0));
            Book book = Book.get(p.level().getServer());
            Bid mine = book.mine(p.getUUID().toString(), lot.item());
            long motes = Motes.get(p);
            List<CardView.Card> cards = new ArrayList<>();
            if (mine == null) {
                cards.add(new CardView.Card("Bid " + Msg.number(OPENING) + " Motes", "The opening bid", 0xFFAA00, motes >= OPENING, "Bid",
                        RiftNpcs.icon(lot.item()), "", List.of(Chip.price("motes", OPENING, motes)), -1, ""));
            }
            for (long raise : RAISES) {
                if (mine != null || raise > OPENING) {
                    long total = (mine == null ? OPENING : 0) + raise;
                    cards.add(new CardView.Card((mine == null ? "Bid " + Msg.number(OPENING + raise) : "Raise by " + Msg.number(raise)) + " Motes",
                            mine == null ? "" : "To " + Msg.number(mine.motes() + raise), 0xFFDD55, motes >= total, mine == null ? "Bid" : "Raise",
                            new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET), "", List.of(Chip.price("motes", total, motes)), -1, ""));
                }
            }
            return new CardView("RIFT", "Damia", RiftNpcs.icon(lot.item()).getHoverName().getString(), npc, List.of(Chip.balance("motes", motes)), cards,
                    "The top " + lot.winners() + " bids win when the year ends.", 1, 34);
        }, (p, card) -> {
            Lot lot = LOTS.get(CHOSEN.getOrDefault(p.getUUID(), 0));
            Book book = Book.get(p.level().getServer());
            String me = p.getUUID().toString();
            Bid mine = book.mine(me, lot.item());
            long add = mine == null ? (card == 0 ? OPENING : OPENING + RAISES[card]) : RAISES[card];
            if (!Motes.take(p, add)) {
                return "You need " + Msg.number(add) + " Motes.";
            }
            if (mine != null) {
                book.bids.remove(mine);
            }
            Bid now = new Bid(me, lot.item(), (mine == null ? 0 : mine.motes()) + add);
            book.bids.add(now);
            book.setDirty();
            p.sendSystemMessage(Component.literal("[NPC] Damia: ").withStyle(ChatFormatting.YELLOW).append(Component.literal("Your bid of "
                    + Msg.number(now.motes()) + " Motes is in. You're #" + book.rank(me, lot.item()) + ".").withStyle(ChatFormatting.WHITE)));
            Msg.playSound(p, SoundEvents.VILLAGER_TRADE, 1f, 1f);
            open(p, npc);
            return null;
        });
    }

    private static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
