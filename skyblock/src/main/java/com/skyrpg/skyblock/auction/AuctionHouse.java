package com.skyrpg.skyblock.auction;

import com.mojang.brigadier.CommandDispatcher;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.net.OpenPage;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Auction House: put an item up as Buy It Now or for bidding (1, 6, 12, 24 or 48 hours), browse by category,
 * bid (each bid 15% over the last) or buy, and collect what you won or sold. Fees as on Hypixel: 1% of the price to
 * list (2% from 10M, 2.5% from 100M) plus a fee for the duration, and 1% of what you collect over 1M.
 */
public final class AuctionHouse {
    public static final String PAGE = "auction_house";
    public static final int MAX_AUCTIONS = 14;
    public static final double OUTBID = 1.15;
    public static final int[] HOURS = {1, 6, 12, 24, 48};
    public static final long[] DURATION_FEE = {20, 45, 100, 350, 1200};
    public static final List<String> CATEGORIES = List.of("Weapons", "Armor", "Accessories", "Consumables", "Blocks", "Tools & Misc");
    public static final int PAGE_SIZE = 24;

    public static final int SORT_ENDING = 0;
    public static final int SORT_LOW = 1;
    public static final int SORT_HIGH = 2;

    private static MinecraftServer server;

    private AuctionHouse() {
    }

    // ------------------------------------------------------------------ payloads

    /** One auction as the page shows it: {@code price} is what it costs now (BIN, starting bid or highest bid). */
    public record View(long id, ItemStack item, boolean bin, long price, long nextBid, int bids, long leftMillis, String seller, boolean mine,
                       boolean topBidder, int status, long claimable) {
        /** For your own list: still going, sold (coins to collect), ended unsold (item to collect), won (item to collect), lost. */
        public static final int ACTIVE = 0;
        public static final int SOLD = 1;
        public static final int EXPIRED = 2;
        public static final int WON = 3;
        public static final int LOST = 4;

        static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, View::id,
                ItemStack.OPTIONAL_STREAM_CODEC, View::item,
                ByteBufCodecs.BOOL, View::bin,
                ByteBufCodecs.VAR_LONG, View::price,
                ByteBufCodecs.VAR_LONG, View::nextBid,
                ByteBufCodecs.VAR_INT, View::bids,
                ByteBufCodecs.VAR_LONG, View::leftMillis,
                ByteBufCodecs.STRING_UTF8, View::seller,
                ByteBufCodecs.BOOL, View::mine,
                ByteBufCodecs.BOOL, View::topBidder,
                ByteBufCodecs.VAR_INT, View::status,
                ByteBufCodecs.VAR_LONG, View::claimable,
                View::new);
    }

    /** Client to server: one page of auctions in a category (-1: all), matching a search, sorted, BIN only or not. */
    public record Browse(int category, String query, int sort, boolean binOnly, int page) implements CustomPacketPayload {
        public static final Type<Browse> TYPE = new Type<>(SkyRpgSkyblock.id("ah_browse"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Browse> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Browse::category, ByteBufCodecs.STRING_UTF8, Browse::query, ByteBufCodecs.VAR_INT, Browse::sort,
                ByteBufCodecs.BOOL, Browse::binOnly, ByteBufCodecs.VAR_INT, Browse::page, Browse::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: a page of auctions, and how many pages there are. */
    public record Listing(List<View> auctions, int page, int pages) implements CustomPacketPayload {
        public static final Type<Listing> TYPE = new Type<>(SkyRpgSkyblock.id("ah_listing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Listing> CODEC = StreamCodec.composite(
                View.CODEC.apply(ByteBufCodecs.list()), Listing::auctions, ByteBufCodecs.VAR_INT, Listing::page, ByteBufCodecs.VAR_INT, Listing::pages,
                Listing::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: your auctions, the auctions you bid on, and coins owed to you from being outbid. */
    public record Mine(List<View> auctions, List<View> bids, long refunds) implements CustomPacketPayload {
        public static final Type<Mine> TYPE = new Type<>(SkyRpgSkyblock.id("ah_mine"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mine> CODEC = StreamCodec.composite(
                View.CODEC.apply(ByteBufCodecs.list()), Mine::auctions, View.CODEC.apply(ByteBufCodecs.list()), Mine::bids,
                ByteBufCodecs.VAR_LONG, Mine::refunds, Mine::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: an action on an auction. */
    public record Act(int kind, long id, long amount, int slot, boolean bin, int duration) implements CustomPacketPayload {
        public static final int BID = 0;
        public static final int BUY = 1;
        public static final int CREATE = 2;
        public static final int COLLECT = 3;
        public static final int COLLECT_ALL = 4;
        public static final int CANCEL = 5;
        public static final int MINE = 6;

        public static final Type<Act> TYPE = new Type<>(SkyRpgSkyblock.id("ah_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Act::kind, ByteBufCodecs.VAR_LONG, Act::id, ByteBufCodecs.VAR_LONG, Act::amount,
                ByteBufCodecs.VAR_INT, Act::slot, ByteBufCodecs.BOOL, Act::bin, ByteBufCodecs.VAR_INT, Act::duration, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Browse.TYPE, Browse.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Listing.TYPE, Listing.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Mine.TYPE, Mine.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerPlayNetworking.registerGlobalReceiver(Browse.TYPE, (payload, context) -> ServerPlayNetworking.send(context.player(),
                browse(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> act(context.player(), payload));
        // Tell sellers and winners when their auctions end.
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 20 == 0) {
                sweep(s);
            }
        });
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : List.of("ah", "auctionhouse")) {
            dispatcher.register(Commands.literal(name).executes(ctx -> {
                OpenPage.send(ctx.getSource().getPlayerOrException(), SkyRpgSkyblock.id(PAGE));
                return 1;
            }));
        }
    }

    // ------------------------------------------------------------------ rules

    /** Which of {@link #CATEGORIES} an item goes in. */
    public static int category(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        String type = def == null ? "" : def.json().has("type") ? def.json().get("type").getAsString() : "";
        return switch (type) {
            case "Sword", "Bow", "Longsword", "Wand", "Fishing Weapon", "Dagger" -> 0;
            case "Helmet", "Chestplate", "Leggings", "Boots", "Necklace", "Cloak", "Belt", "Gloves", "Bracelet" -> 1;
            case "Accessory" -> 2;
            case "Consumable", "Booster", "Bait", "Travel Scroll", "Potion", "Dungeon Pass", "Arrow", "Deployable" -> 3;
            default -> type.isEmpty() && stack.getItem() instanceof BlockItem ? 4 : 5;
        };
    }

    /** What listing for {@code price} for {@code hours} costs. */
    public static long fee(long price, int durationIndex) {
        double rate = price >= 100_000_000 ? 0.025 : price >= 10_000_000 ? 0.02 : 0.01;
        return (long) Math.ceil(price * rate) + DURATION_FEE[Math.clamp(durationIndex, 0, DURATION_FEE.length - 1)];
    }

    /** The 1% taken from what you collect over 1M. */
    public static long tax(long coins) {
        return coins > 1_000_000 ? (long) Math.floor(coins * 0.01) : 0;
    }

    public static long nextBid(AuctionData.Auction auction) {
        return auction.bidder().isEmpty() ? auction.price() : (long) Math.ceil(auction.bid() * OUTBID);
    }

    private static AuctionData.Who who(ServerPlayer player) {
        return new AuctionData.Who(player.getUUID(), Profiles.current(player).id(), player.getPlainTextName());
    }

    private static boolean is(Optional<AuctionData.Who> who, UUID profile) {
        return who.isPresent() && who.get().profile().equals(profile);
    }

    // ------------------------------------------------------------------ views

    private static View view(AuctionData.Auction a, UUID profile, long now) {
        boolean mine = a.seller().profile().equals(profile);
        boolean top = is(a.bidder(), profile);
        int status;
        long claimable = 0;
        if (!a.ended(now)) {
            status = View.ACTIVE;
        } else if (mine) {
            status = a.finalPrice() > 0 ? View.SOLD : View.EXPIRED;
            claimable = a.sellerCollected() ? -1 : a.finalPrice() > 0 ? a.finalPrice() - tax(a.finalPrice()) : 0;
        } else {
            status = top ? View.WON : View.LOST;
        }
        long price = a.bin() ? a.price() : (a.bidder().isPresent() ? a.bid() : a.price());
        return new View(a.id(), a.item(), a.bin(), price, a.bin() ? a.price() : nextBid(a), a.bids(), Math.max(0, a.end() - now),
                a.seller().name(), mine, top, status, claimable);
    }

    static Listing browse(ServerPlayer player, Browse request) {
        long now = System.currentTimeMillis();
        UUID profile = Profiles.current(player).id();
        String needle = request.query().toLowerCase(Locale.ROOT).trim();
        List<AuctionData.Auction> found = AuctionData.get(player.level().getServer()).auctions.stream()
                .filter(a -> !a.ended(now))
                .filter(a -> request.category() < 0 || category(a.item()) == request.category())
                .filter(a -> !request.binOnly() || a.bin())
                .filter(a -> needle.isEmpty() || a.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(switch (request.sort()) {
                    case SORT_LOW -> Comparator.comparingLong(AuctionHouse::current);
                    case SORT_HIGH -> Comparator.comparingLong(AuctionHouse::current).reversed();
                    default -> Comparator.comparingLong(AuctionData.Auction::end);
                })
                .toList();
        int pages = Math.max(1, (found.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(request.page(), 0, pages - 1);
        List<View> views = new ArrayList<>();
        for (AuctionData.Auction a : found.subList(page * PAGE_SIZE, Math.min(found.size(), (page + 1) * PAGE_SIZE))) {
            views.add(view(a, profile, now));
        }
        return new Listing(views, page, pages);
    }

    private static long current(AuctionData.Auction a) {
        return a.bin() ? a.price() : Math.max(a.price(), a.bid());
    }

    static Mine mine(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID profile = Profiles.current(player).id();
        AuctionData data = AuctionData.get(player.level().getServer());
        List<View> auctions = new ArrayList<>();
        List<View> bids = new ArrayList<>();
        for (AuctionData.Auction a : data.auctions) {
            if (a.seller().profile().equals(profile) && !a.sellerCollected()) {
                auctions.add(view(a, profile, now));
            } else if (is(a.bidder(), profile) && !a.buyerCollected()) {
                bids.add(view(a, profile, now));
            }
        }
        long refunds = data.refunds.stream().filter(r -> r.profile().equals(profile)).mapToLong(AuctionData.Refund::coins).sum();
        return new Mine(auctions, bids, refunds);
    }

    // ------------------------------------------------------------------ actions

    private static void act(ServerPlayer player, Act act) {
        AuctionData data = AuctionData.get(player.level().getServer());
        long now = System.currentTimeMillis();
        switch (act.kind()) {
            case Act.BID -> bid(player, data, act.id(), act.amount(), now);
            case Act.BUY -> buy(player, data, act.id(), now);
            case Act.CREATE -> create(player, data, act.slot(), act.bin(), act.amount(), act.duration(), now);
            case Act.COLLECT -> collect(player, data, act.id(), now);
            case Act.COLLECT_ALL -> collectAll(player, data, now);
            case Act.CANCEL -> cancel(player, data, act.id(), now);
            default -> { }
        }
        ServerPlayNetworking.send(player, mine(player));
    }

    private static void create(ServerPlayer player, AuctionData data, int slot, boolean bin, long price, int duration, long now) {
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= Inventory.INVENTORY_SIZE || inventory.getItem(slot).isEmpty()) {
            fail(player, "Pick an item from your inventory first!");
            return;
        }
        UUID profile = Profiles.current(player).id();
        long active = data.auctions.stream().filter(a -> a.seller().profile().equals(profile) && !a.ended(now)).count();
        if (active >= MAX_AUCTIONS) {
            fail(player, "You can only have " + MAX_AUCTIONS + " auctions at once!");
            return;
        }
        if (price < 1 || price > 999_999_999_999L) {
            fail(player, "Set a price first!");
            return;
        }
        int index = Math.clamp(duration, 0, HOURS.length - 1);
        long fee = fee(price, index);
        if (!Wallet.take(player, fee)) {
            fail(player, "You need " + Msg.number(fee) + " coins for the listing fee!");
            return;
        }
        ItemStack item = inventory.getItem(slot).copy();
        inventory.setItem(slot, ItemStack.EMPTY);
        data.add(new AuctionData.Auction(data.nextId(), who(player), item, bin, price, 0, Optional.empty(), 0, now,
                now + HOURS[index] * 3_600_000L, false, false, false));
        player.sendOverlayMessage(Component.literal(bin ? "BIN Auction started for " : "Auction started for ").withStyle(ChatFormatting.YELLOW)
                .append(item.getHoverName()).append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static void bid(ServerPlayer player, AuctionData data, long id, long amount, long now) {
        AuctionData.Auction a = data.find(id);
        UUID profile = Profiles.current(player).id();
        if (a == null || a.bin() || a.ended(now)) {
            fail(player, "This auction has ended!");
            return;
        }
        if (a.seller().profile().equals(profile)) {
            fail(player, "You can't bid on your own auction!");
            return;
        }
        if (is(a.bidder(), profile)) {
            fail(player, "You already have the top bid!");
            return;
        }
        long needed = nextBid(a);
        long offer = Math.max(amount, needed);
        if (!Wallet.take(player, offer)) {
            fail(player, "You need " + Msg.number(offer) + " coins to bid!");
            return;
        }
        // The last top bidder gets their coins back: now if they're here on that profile, else when they next look.
        a.bidder().ifPresent(previous -> {
            ServerPlayer online = player.level().getServer().getPlayerList().getPlayer(previous.player());
            if (online != null && Profiles.current(online).id().equals(previous.profile())) {
                Wallet.give(online, a.bid());
                Notice.of(Notice.Kind.SHOP).tag("OUTBID").color(0xFFAA00).panel()
                        .line(Component.literal(Msg.number(a.bid()) + " coins came back to your purse.").withStyle(ChatFormatting.GRAY))
                        .send(online, Component.literal(player.getPlainTextName() + " outbid you on ").withStyle(ChatFormatting.YELLOW).append(a.item().getHoverName()));
            } else {
                data.refund(new AuctionData.Refund(previous.player(), previous.profile(), a.bid()));
            }
        });
        data.update(a.withBid(who(player), offer), now);
        player.sendOverlayMessage(Component.literal("Bid placed: " + Msg.number(offer) + " coins on ").withStyle(ChatFormatting.YELLOW).append(a.item().getHoverName()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    private static void buy(ServerPlayer player, AuctionData data, long id, long now) {
        AuctionData.Auction a = data.find(id);
        UUID profile = Profiles.current(player).id();
        if (a == null || !a.bin() || a.ended(now)) {
            fail(player, "This item has already been bought!");
            return;
        }
        if (a.seller().profile().equals(profile)) {
            fail(player, "You can't buy your own item!");
            return;
        }
        if (!Wallet.take(player, a.price())) {
            fail(player, "You need " + Msg.number(a.price()) + " coins to buy this!");
            return;
        }
        AuctionData.Auction bought = a.boughtBy(who(player));
        // Straight into your inventory when it fits.
        if (player.getInventory().getFreeSlot() >= 0) {
            player.getInventory().add(a.item().copy());
            bought = bought.buyerDone();
        }
        data.update(bought, now);
        player.sendOverlayMessage(Component.literal("You bought ").withStyle(ChatFormatting.GREEN).append(a.item().getHoverName())
                .append(Component.literal(" for " + Msg.number(a.price()) + " coins!").withStyle(ChatFormatting.GREEN)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.8f);
        tellSeller(player.level().getServer(), bought, player.getPlainTextName());
    }

    private static void cancel(ServerPlayer player, AuctionData data, long id, long now) {
        AuctionData.Auction a = data.find(id);
        if (a == null || !a.seller().profile().equals(Profiles.current(player).id()) || a.ended(now)) {
            return;
        }
        if (a.bidder().isPresent()) {
            fail(player, "You can't cancel an auction someone has bid on!");
            return;
        }
        data.update(a.cancelled(now), now);
        collect(player, data, id, now);
    }

    /** Collects one ended auction: the coins it sold for, the item back if it didn't sell, or the item you won. */
    private static void collect(ServerPlayer player, AuctionData data, long id, long now) {
        AuctionData.Auction a = data.find(id);
        UUID profile = Profiles.current(player).id();
        if (a == null || !a.ended(now)) {
            return;
        }
        if (a.seller().profile().equals(profile) && !a.sellerCollected()) {
            long coins = a.finalPrice();
            if (coins > 0) {
                long tax = tax(coins);
                Wallet.give(player, coins - tax);
                player.sendOverlayMessage(Component.literal("Collected " + Msg.number(coins - tax) + " coins").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(tax > 0 ? " (" + Msg.number(tax) + " tax)" : "").withStyle(ChatFormatting.GRAY)));
            } else {
                if (!give(player, a.item())) {
                    return;
                }
                player.sendOverlayMessage(Component.literal("Collected your unsold ").withStyle(ChatFormatting.YELLOW).append(a.item().getHoverName()));
            }
            data.update(a.sellerDone(), now);
        } else if (is(a.bidder(), profile) && !a.buyerCollected()) {
            if (!give(player, a.item())) {
                return;
            }
            player.sendOverlayMessage(Component.literal("Collected ").withStyle(ChatFormatting.GREEN).append(a.item().getHoverName()));
            data.update(a.buyerDone(), now);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void collectAll(ServerPlayer player, AuctionData data, long now) {
        UUID profile = Profiles.current(player).id();
        for (AuctionData.Auction a : List.copyOf(data.auctions)) {
            if (a.ended(now) && (a.seller().profile().equals(profile) && !a.sellerCollected() || is(a.bidder(), profile) && !a.buyerCollected())) {
                collect(player, data, a.id(), now);
            }
        }
        long owed = 0;
        for (AuctionData.Refund refund : List.copyOf(data.refunds)) {
            if (refund.profile().equals(profile)) {
                owed += refund.coins();
                data.refunds.remove(refund);
            }
        }
        if (owed > 0) {
            Wallet.give(player, owed);
            data.setDirty();
            player.sendSystemMessage(Component.literal("Collected " + Msg.number(owed) + " coins from bids you lost.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean give(ServerPlayer player, ItemStack item) {
        if (player.getInventory().getFreeSlot() < 0) {
            fail(player, "Make room in your inventory first!");
            return false;
        }
        player.getInventory().add(item.copy());
        return true;
    }

    /** Ended auctions tell their seller and winner once (when they're online). */
    private static void sweep(MinecraftServer s) {
        long now = System.currentTimeMillis();
        AuctionData data = AuctionData.get(s);
        for (AuctionData.Auction a : data.auctions) {
            if (!a.bin() && a.end() <= now && a.end() > now - 1000) {
                // just ended this second
                tellSeller(s, a, a.bidder().map(AuctionData.Who::name).orElse(""));
                a.bidder().ifPresent(winner -> {
                    ServerPlayer online = s.getPlayerList().getPlayer(winner.player());
                    if (online != null) {
                        Notice.of(Notice.Kind.SHOP).tag("AUCTION WON").color(0x55FF55).panel()
                                .line(Component.literal("Collect it in the Auction House (/ah, Manage)").withStyle(ChatFormatting.GRAY))
                                .send(online, Component.literal("You won ").withStyle(ChatFormatting.GREEN).append(a.item().getHoverName())
                                        .append(Component.literal(" for " + Msg.number(a.bid()) + " coins!").withStyle(ChatFormatting.GREEN)));
                    }
                });
            }
        }
    }

    private static void tellSeller(MinecraftServer s, AuctionData.Auction a, String buyer) {
        ServerPlayer seller = s.getPlayerList().getPlayer(a.seller().player());
        if (seller == null) {
            return;
        }
        if (a.finalPrice() > 0) {
            Notice.of(Notice.Kind.SHOP).tag("SOLD").color(0xFFAA00).panel()
                    .line(Component.literal("Collect " + Msg.number(a.finalPrice() - tax(a.finalPrice())) + " coins in the Auction House (/ah, Manage)")
                            .withStyle(ChatFormatting.GRAY))
                    .send(seller, Component.literal(buyer + " bought your ").withStyle(ChatFormatting.YELLOW).append(a.item().getHoverName())
                            .append(Component.literal(" for " + Msg.number(a.finalPrice()) + " coins!").withStyle(ChatFormatting.YELLOW)));
        } else {
            Notice.of(Notice.Kind.SHOP).tag("EXPIRED").color(0xAAAAAA).send(seller,
                    Component.literal("Your auction for ").withStyle(ChatFormatting.GRAY).append(a.item().getHoverName())
                            .append(Component.literal(" ended without bids. Collect it in /ah.").withStyle(ChatFormatting.GRAY)));
        }
    }

    private static void fail(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1f);
    }
}
