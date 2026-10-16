package com.skyrpg.skyblock.auction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every auction, saved with the world, until both sides have collected (the seller the coins or the unsold item, the
 * winner the item), and the coins owed to outbid players who weren't online to get them straight back.
 */
public final class AuctionData extends SavedData {
    /** Who: the player, which profile, and the name to show. */
    public record Who(UUID player, UUID profile, String name) {
        static final Codec<Who> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Who::player),
                UUIDUtil.STRING_CODEC.fieldOf("profile").forGetter(Who::profile),
                Codec.STRING.fieldOf("name").forGetter(Who::name)).apply(i, Who::new));
    }

    /**
     * One auction: Buy It Now ({@code bin}, sold at {@code price}) or bidding (starts at {@code price}, {@code bid} is the
     * highest so far). {@code end} is when it ends, in real milliseconds.
     */
    public record Auction(long id, Who seller, ItemStack item, boolean bin, long price, long bid, Optional<Who> bidder, int bids, long start,
                          long end, boolean sold, boolean sellerCollected, boolean buyerCollected) {
        static final Codec<Auction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Auction::id),
                Who.CODEC.fieldOf("seller").forGetter(Auction::seller),
                ItemStack.CODEC.fieldOf("item").forGetter(Auction::item),
                Codec.BOOL.fieldOf("bin").forGetter(Auction::bin),
                Codec.LONG.fieldOf("price").forGetter(Auction::price),
                Codec.LONG.fieldOf("bid").forGetter(Auction::bid),
                Who.CODEC.optionalFieldOf("bidder").forGetter(Auction::bidder),
                Codec.INT.fieldOf("bids").forGetter(Auction::bids),
                Codec.LONG.fieldOf("start").forGetter(Auction::start),
                Codec.LONG.fieldOf("end").forGetter(Auction::end),
                Codec.BOOL.fieldOf("sold").forGetter(Auction::sold),
                Codec.BOOL.fieldOf("seller_collected").forGetter(Auction::sellerCollected),
                Codec.BOOL.fieldOf("buyer_collected").forGetter(Auction::buyerCollected)).apply(i, Auction::new));

        public boolean ended(long now) {
            return sold || now >= end;
        }

        /** What it went for (a BIN's price or the winning bid), or 0 if it didn't sell. */
        public long finalPrice() {
            return bin ? (sold ? price : 0) : (bidder.isPresent() ? bid : 0);
        }

        public boolean done(long now) {
            return ended(now) && sellerCollected && (finalPrice() == 0 || buyerCollected);
        }

        Auction withBid(Who who, long amount) {
            return new Auction(id, seller, item, bin, price, amount, Optional.of(who), bids + 1, start, end, sold, sellerCollected, buyerCollected);
        }

        Auction boughtBy(Who who) {
            return new Auction(id, seller, item, bin, price, price, Optional.of(who), 1, start, end, true, sellerCollected, buyerCollected);
        }

        Auction sellerDone() {
            return new Auction(id, seller, item, bin, price, bid, bidder, bids, start, end, sold, true, buyerCollected);
        }

        Auction buyerDone() {
            return new Auction(id, seller, item, bin, price, bid, bidder, bids, start, end, sold, sellerCollected, true);
        }

        /** Ended early by the seller (only while nobody has bid). */
        Auction cancelled(long now) {
            return new Auction(id, seller, item, bin, price, bid, bidder, bids, start, now, sold, sellerCollected, buyerCollected);
        }
    }

    /** Coins owed to someone who was outbid while away. */
    public record Refund(UUID player, UUID profile, long coins) {
        static final Codec<Refund> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Refund::player),
                UUIDUtil.STRING_CODEC.fieldOf("profile").forGetter(Refund::profile),
                Codec.LONG.fieldOf("coins").forGetter(Refund::coins)).apply(i, Refund::new));
    }

    static final Codec<AuctionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Auction.CODEC.listOf().optionalFieldOf("auctions", List.of()).forGetter(d -> d.auctions),
            Refund.CODEC.listOf().optionalFieldOf("refunds", List.of()).forGetter(d -> d.refunds),
            Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(d -> d.nextId)).apply(i, AuctionData::new));
    static final SavedDataType<AuctionData> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("auctions"), AuctionData::new, CODEC, null);

    final List<Auction> auctions;
    final List<Refund> refunds;
    private long nextId;

    private AuctionData() {
        this(List.of(), List.of(), 1L);
    }

    private AuctionData(List<Auction> auctions, List<Refund> refunds, long nextId) {
        this.auctions = new ArrayList<>(auctions);
        this.refunds = new ArrayList<>(refunds);
        this.nextId = nextId;
    }

    static AuctionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    long nextId() {
        setDirty();
        return nextId++;
    }

    void add(Auction auction) {
        auctions.add(auction);
        setDirty();
    }

    Auction find(long id) {
        for (Auction auction : auctions) {
            if (auction.id() == id) {
                return auction;
            }
        }
        return null;
    }

    /** Swaps an auction for its new state; drops it once everything's collected. */
    void update(Auction auction, long now) {
        for (int i = 0; i < auctions.size(); i++) {
            if (auctions.get(i).id() == auction.id()) {
                if (auction.done(now)) {
                    auctions.remove(i);
                } else {
                    auctions.set(i, auction);
                }
                setDirty();
                return;
            }
        }
    }

    void refund(Refund refund) {
        refunds.add(refund);
        setDirty();
    }
}
