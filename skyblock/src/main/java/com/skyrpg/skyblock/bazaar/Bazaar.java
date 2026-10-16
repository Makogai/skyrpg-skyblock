package com.skyrpg.skyblock.bazaar;

import com.mojang.brigadier.CommandDispatcher;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.net.OpenPage;
import com.skyrpg.core.progress.SkyBlockLevels;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Bazaar: buy and sell instantly, or place buy orders and sell offers that fill when someone else trades
 * instantly (orders never trade with each other, as on Hypixel). Instant trades take the best player order first and,
 * when no one beats it, the market maker's price from the catalogue (Hypixel's at the snapshot), so the Bazaar trades
 * on a server of any size. Selling costs 1.25% tax. It opens at SkyBlock Level 7.
 */
public final class Bazaar {
    public static final double TAX = 0.0125;
    /** Hypixel's largest order. */
    public static final int MAX_AMOUNT = 71_680;
    public static final String PAGE = "bazaar";

    public static final int INSTANT_BUY = 0;
    public static final int INSTANT_SELL = 1;
    public static final int BUY_ORDER = 2;
    public static final int SELL_OFFER = 3;
    public static final int CANCEL = 4;
    public static final int CLAIM = 5;

    private static MinecraftServer server;
    private static volatile boolean catalogChanged;

    private Bazaar() {
    }

    // ------------------------------------------------------------------ payloads

    /** Server to client: the catalogue (on join and when it reloads). */
    public record Catalog(List<BazaarCatalog.Category> categories) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE = new Type<>(SkyRpgSkyblock.id("bazaar_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC = BazaarCatalog.Category.CODEC.apply(ByteBufCodecs.list())
                .map(Catalog::new, Catalog::categories);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One price level of the order book: {@code amount} at {@code price} over {@code orders} orders; the market maker's has amount -1. */
    public record Level(double price, int amount, int orders) {
        static final StreamCodec<RegistryFriendlyByteBuf, Level> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Level::price, ByteBufCodecs.VAR_INT, Level::amount, ByteBufCodecs.VAR_INT, Level::orders, Level::new);

        public boolean maker() {
            return amount < 0;
        }
    }

    /** Server to client: a product's order book, cheapest sell offers and best buy orders first. */
    public record Book(String product, List<Level> sellOffers, List<Level> buyOrders) implements CustomPacketPayload {
        public static final Type<Book> TYPE = new Type<>(SkyRpgSkyblock.id("bazaar_book"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Book> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Book::product,
                Level.CODEC.apply(ByteBufCodecs.list()), Book::sellOffers,
                Level.CODEC.apply(ByteBufCodecs.list()), Book::buyOrders, Book::new);

        /** What buying one instantly costs (the cheapest offer), or 0. */
        public double instantBuy() {
            return sellOffers.isEmpty() ? 0 : sellOffers.getFirst().price();
        }

        /** What selling one instantly pays before tax (the best order), or 0. */
        public double instantSell() {
            return buyOrders.isEmpty() ? 0 : buyOrders.getFirst().price();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One of your orders as the page shows it. */
    public record OrderView(long id, String product, boolean buy, double price, int amount, int filled, int claimable) {
        static final StreamCodec<RegistryFriendlyByteBuf, OrderView> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, OrderView::id, ByteBufCodecs.STRING_UTF8, OrderView::product, ByteBufCodecs.BOOL, OrderView::buy,
                ByteBufCodecs.DOUBLE, OrderView::price, ByteBufCodecs.VAR_INT, OrderView::amount, ByteBufCodecs.VAR_INT, OrderView::filled,
                ByteBufCodecs.VAR_INT, OrderView::claimable, OrderView::new);
    }

    /** Server to client: your orders. */
    public record Orders(List<OrderView> orders) implements CustomPacketPayload {
        public static final Type<Orders> TYPE = new Type<>(SkyRpgSkyblock.id("bazaar_orders"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Orders> CODEC = OrderView.CODEC.apply(ByteBufCodecs.list())
                .map(Orders::new, Orders::orders);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: show me this product's book (and my orders). */
    public record Request(String product) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(SkyRpgSkyblock.id("bazaar_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = ByteBufCodecs.STRING_UTF8.map(Request::new, Request::product)
                .cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: a trade ({@link #INSTANT_BUY}...), with the order id for cancelling and claiming (-1: claim all). */
    public record Act(int kind, String product, int amount, double price, long order) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(SkyRpgSkyblock.id("bazaar_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Act::kind, ByteBufCodecs.STRING_UTF8, Act::product, ByteBufCodecs.VAR_INT, Act::amount,
                ByteBufCodecs.DOUBLE, Act::price, ByteBufCodecs.VAR_LONG, Act::order, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Catalog.TYPE, Catalog.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Book.TYPE, Book.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Orders.TYPE, Orders.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
        JsonDataLoader.register(SkyRpgSkyblock.id("bazaar"), "skyrpg/bazaar", files -> {
            BazaarCatalog.load(files);
            catalogChanged = true;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            catalogChanged = false;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> ServerPlayNetworking.send(handler.player, new Catalog(BazaarCatalog.categories())));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (catalogChanged) {
                catalogChanged = false;
                Catalog catalog = new Catalog(BazaarCatalog.categories());
                s.getPlayerList().getPlayers().forEach(player -> ServerPlayNetworking.send(player, catalog));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (BazaarCatalog.product(payload.product()) != null) {
                ServerPlayNetworking.send(player, book(player.level().getServer(), payload.product()));
            }
            ServerPlayNetworking.send(player, orders(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> act(context.player(), payload));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : List.of("bazaar", "bz")) {
            dispatcher.register(Commands.literal(name).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!allowed(player)) {
                    return 0;
                }
                OpenPage.send(player, SkyRpgSkyblock.id(PAGE));
                return 1;
            }));
        }
    }

    /** The Bazaar opens at SkyBlock Level 7. */
    private static boolean allowed(ServerPlayer player) {
        if (SkyBlockXp.level(player) >= 7 || player.isCreative()) {
            return true;
        }
        player.sendOverlayMessage(Component.literal("You need SkyBlock Level 7 to use the Bazaar!").withStyle(ChatFormatting.RED));
        return false;
    }

    // ------------------------------------------------------------------ the book

    /** Player levels plus the market maker's, best first. */
    static Book book(MinecraftServer server, String product) {
        BazaarCatalog.Product p = BazaarCatalog.product(product);
        List<BazaarOrders.Order> all = BazaarOrders.get(server).orders.stream().filter(o -> o.product().equals(product) && o.open() > 0).toList();
        return new Book(product, levels(all, false, p == null ? 0 : p.buy()), levels(all, true, p == null ? 0 : p.sell()));
    }

    private static List<Level> levels(List<BazaarOrders.Order> orders, boolean buy, double maker) {
        List<Level> out = new ArrayList<>();
        orders.stream().filter(o -> o.buy() == buy)
                .sorted(buy ? Comparator.comparingDouble(BazaarOrders.Order::price).reversed() : Comparator.comparingDouble(BazaarOrders.Order::price))
                .forEach(o -> {
                    if (!out.isEmpty() && out.getLast().price() == o.price()) {
                        Level last = out.removeLast();
                        out.add(new Level(o.price(), last.amount() + o.open(), last.orders() + 1));
                    } else {
                        out.add(new Level(o.price(), o.open(), 1));
                    }
                });
        if (maker > 0) {
            out.add(new Level(maker, -1, 0));
            out.sort(buy ? Comparator.comparingDouble(Level::price).reversed() : Comparator.comparingDouble(Level::price));
            // Nobody trades past the market maker: drop the levels behind it.
            int at = 0;
            while (at < out.size() && !out.get(at).maker()) {
                at++;
            }
            return List.copyOf(out.subList(0, Math.min(out.size(), at + 1)));
        }
        return List.copyOf(out);
    }

    static Orders orders(ServerPlayer player) {
        UUID profile = Profiles.current(player).id();
        List<OrderView> mine = new ArrayList<>();
        for (BazaarOrders.Order o : BazaarOrders.get(player.level().getServer()).orders) {
            if (o.profile().equals(profile)) {
                mine.add(new OrderView(o.id(), o.product(), o.buy(), o.price(), o.amount(), o.filled(), o.claimable()));
            }
        }
        return new Orders(mine);
    }

    // ------------------------------------------------------------------ trading

    private static void act(ServerPlayer player, Act act) {
        if (!allowed(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        BazaarOrders data = BazaarOrders.get(server);
        switch (act.kind()) {
            case INSTANT_BUY -> instantBuy(player, data, act.product(), Math.clamp(act.amount(), 1, MAX_AMOUNT));
            case INSTANT_SELL -> instantSell(player, data, act.product(), Math.clamp(act.amount(), 1, MAX_AMOUNT));
            case BUY_ORDER -> buyOrder(player, data, act.product(), Math.clamp(act.amount(), 1, MAX_AMOUNT), round(act.price()));
            case SELL_OFFER -> sellOffer(player, data, act.product(), Math.clamp(act.amount(), 1, MAX_AMOUNT), round(act.price()));
            case CANCEL -> cancel(player, data, act.order());
            case CLAIM -> claim(player, data, act.order());
            default -> {
                return;
            }
        }
        if (BazaarCatalog.product(act.product()) != null) {
            ServerPlayNetworking.send(player, book(server, act.product()));
        }
        ServerPlayNetworking.send(player, orders(player));
    }

    /** Prices go in tenths of a coin, as on Hypixel. */
    public static double round(double price) {
        return Math.round(price * 10) / 10.0;
    }

    private static void instantBuy(ServerPlayer player, BazaarOrders data, String product, int wanted) {
        BazaarCatalog.Product p = BazaarCatalog.product(product);
        ItemDefinition def = ItemRegistry.skyblock(product);
        if (p == null || def == null) {
            return;
        }
        UUID profile = Profiles.current(player).id();
        int room = room(player.getInventory(), SkyItems.create(def, 1));
        int amount = Math.min(wanted, room);
        if (amount <= 0) {
            fail(player, "Your inventory is full!");
            return;
        }
        // The plan: the cheapest offers first, then the market maker for the rest.
        List<BazaarOrders.Order> offers = data.orders.stream()
                .filter(o -> o.product().equals(product) && !o.buy() && o.open() > 0 && !o.profile().equals(profile) && (p.buy() <= 0 || o.price() <= p.buy()))
                .sorted(Comparator.comparingDouble(BazaarOrders.Order::price).thenComparingLong(BazaarOrders.Order::created)).toList();
        int left = amount;
        double cost = 0;
        List<long[]> takes = new ArrayList<>();
        for (BazaarOrders.Order offer : offers) {
            if (left <= 0) {
                break;
            }
            int n = Math.min(left, offer.open());
            takes.add(new long[] {offer.id(), n});
            cost += n * offer.price();
            left -= n;
        }
        if (left > 0 && p.buy() > 0) {
            cost += left * p.buy();
            left = 0;
        }
        int bought = amount - left;
        long total = (long) Math.ceil(cost);
        if (bought <= 0) {
            fail(player, "Nobody is selling that right now!");
            return;
        }
        if (Wallet.balance(player) < total) {
            fail(player, "You need " + Msg.number(total) + " coins for that!");
            return;
        }
        Wallet.take(player, total);
        for (long[] take : takes) {
            BazaarOrders.Order offer = data.find(take[0]);
            if (offer != null) {
                BazaarOrders.Order now = offer.withFilled(offer.filled() + (int) take[1]);
                data.update(now);
                if (now.open() == 0) {
                    filled(now);
                }
            }
        }
        give(player, def, bought);
        player.sendOverlayMessage(Component.literal("[Bazaar] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Bought " + Msg.number(bought) + "x ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(def.name()).withColor(def.rarity().color()))
                .append(Component.literal(" for " + Msg.number(total) + " coins").withStyle(ChatFormatting.GOLD)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.7f);
    }

    private static void instantSell(ServerPlayer player, BazaarOrders data, String product, int wanted) {
        BazaarCatalog.Product p = BazaarCatalog.product(product);
        ItemDefinition def = ItemRegistry.skyblock(product);
        if (p == null || def == null) {
            return;
        }
        UUID profile = Profiles.current(player).id();
        int amount = Math.min(wanted, SkyStacks.count(player.getInventory(), product));
        if (amount <= 0) {
            fail(player, "You don't have any " + def.name() + "!");
            return;
        }
        List<BazaarOrders.Order> orders = data.orders.stream()
                .filter(o -> o.product().equals(product) && o.buy() && o.open() > 0 && !o.profile().equals(profile) && o.price() >= p.sell())
                .sorted(Comparator.comparingDouble(BazaarOrders.Order::price).reversed().thenComparingLong(BazaarOrders.Order::created)).toList();
        int left = amount;
        double earned = 0;
        for (BazaarOrders.Order order : orders) {
            if (left <= 0) {
                break;
            }
            int n = Math.min(left, order.open());
            BazaarOrders.Order now = order.withFilled(order.filled() + n);
            data.update(now);
            if (now.open() == 0) {
                filled(now);
            }
            earned += n * order.price();
            left -= n;
        }
        if (left > 0 && p.sell() > 0) {
            earned += left * p.sell();
            left = 0;
        }
        int sold = amount - left;
        if (sold <= 0) {
            fail(player, "Nobody is buying that right now!");
            return;
        }
        SkyStacks.take(player.getInventory(), product, sold);
        long coins = (long) Math.floor(earned * (1 - TAX));
        Wallet.give(player, coins);
        player.sendOverlayMessage(Component.literal("[Bazaar] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Sold " + Msg.number(sold) + "x ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(def.name()).withColor(def.rarity().color()))
                .append(Component.literal(" for " + Msg.number(coins) + " coins").withStyle(ChatFormatting.GOLD)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void buyOrder(ServerPlayer player, BazaarOrders data, String product, int amount, double price) {
        ItemDefinition def = ItemRegistry.skyblock(product);
        if (BazaarCatalog.product(product) == null || def == null || price <= 0) {
            return;
        }
        long escrow = (long) Math.ceil(amount * price);
        if (!Wallet.take(player, escrow)) {
            fail(player, "You need " + Msg.number(escrow) + " coins for that order!");
            return;
        }
        data.add(new BazaarOrders.Order(data.nextId(), player.getUUID(), Profiles.current(player).id(), product, true, price, amount, 0, 0,
                System.currentTimeMillis()));
        player.sendOverlayMessage(Component.literal("[Bazaar] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Buy Order placed: " + Msg.number(amount) + "x ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(def.name()).withColor(def.rarity().color()))
                .append(Component.literal(" at " + Msg.number(price) + " each").withStyle(ChatFormatting.GOLD)));
    }

    private static void sellOffer(ServerPlayer player, BazaarOrders data, String product, int wanted, double price) {
        ItemDefinition def = ItemRegistry.skyblock(product);
        if (BazaarCatalog.product(product) == null || def == null || price <= 0) {
            return;
        }
        int amount = Math.min(wanted, SkyStacks.count(player.getInventory(), product));
        if (amount <= 0) {
            fail(player, "You don't have any " + def.name() + "!");
            return;
        }
        SkyStacks.take(player.getInventory(), product, amount);
        data.add(new BazaarOrders.Order(data.nextId(), player.getUUID(), Profiles.current(player).id(), product, false, price, amount, 0, 0,
                System.currentTimeMillis()));
        player.sendOverlayMessage(Component.literal("[Bazaar] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Sell Offer placed: " + Msg.number(amount) + "x ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(def.name()).withColor(def.rarity().color()))
                .append(Component.literal(" at " + Msg.number(price) + " each").withStyle(ChatFormatting.GOLD)));
    }

    private static void cancel(ServerPlayer player, BazaarOrders data, long id) {
        BazaarOrders.Order order = mine(player, data, id);
        if (order == null || order.open() <= 0) {
            return;
        }
        ItemDefinition def = ItemRegistry.skyblock(order.product());
        if (order.buy()) {
            Wallet.give(player, (long) Math.floor(order.open() * order.price()));
        } else {
            if (def == null || room(player.getInventory(), SkyItems.create(def, 1)) < order.open()) {
                fail(player, "Make room in your inventory first!");
                return;
            }
            give(player, def, order.open());
        }
        data.update(order.cancelled());
        player.sendOverlayMessage(Component.literal("[Bazaar] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Cancelled your " + (order.buy() ? "Buy Order" : "Sell Offer") + ".").withStyle(ChatFormatting.YELLOW)));
    }

    /** Claims one order's goods ({@code id}) or every order's ({@code -1}). */
    private static void claim(ServerPlayer player, BazaarOrders data, long id) {
        List<BazaarOrders.Order> orders = new ArrayList<>();
        if (id < 0) {
            UUID profile = Profiles.current(player).id();
            data.orders.stream().filter(o -> o.profile().equals(profile) && o.claimable() > 0).forEach(orders::add);
        } else {
            BazaarOrders.Order order = mine(player, data, id);
            if (order != null) {
                orders.add(order);
            }
        }
        long coins = 0;
        int items = 0;
        for (BazaarOrders.Order order : orders) {
            int n = order.claimable();
            if (n <= 0) {
                continue;
            }
            if (order.buy()) {
                ItemDefinition def = ItemRegistry.skyblock(order.product());
                if (def == null) {
                    continue;
                }
                n = Math.min(n, room(player.getInventory(), SkyItems.create(def, 1)));
                if (n <= 0) {
                    fail(player, "Make room in your inventory first!");
                    break;
                }
                give(player, def, n);
                items += n;
            } else {
                long earned = (long) Math.floor(n * order.price() * (1 - TAX));
                Wallet.give(player, earned);
                coins += earned;
            }
            data.update(order.withClaimed(order.claimed() + n));
        }
        if (coins > 0 || items > 0) {
            player.sendOverlayMessage(Component.literal("[Bazaar] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Claimed" + (items > 0 ? " " + Msg.number(items) + " items" : "")
                            + (coins > 0 ? (items > 0 ? " and" : "") + " " + Msg.number(coins) + " coins" : "")).withStyle(ChatFormatting.GREEN)));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    private static BazaarOrders.Order mine(ServerPlayer player, BazaarOrders data, long id) {
        BazaarOrders.Order order = data.find(id);
        return order != null && order.profile().equals(Profiles.current(player).id()) ? order : null;
    }

    /** An order filled completely: tell its owner if they're online. */
    private static void filled(BazaarOrders.Order order) {
        if (server == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(order.owner());
        ItemDefinition def = ItemRegistry.skyblock(order.product());
        if (owner == null || def == null) {
            return;
        }
        Notice.of(Notice.Kind.SHOP).tag("BAZAAR").color(0xFFAA00).panel()
                .line(Component.literal("Claim it in the Bazaar (/bz, Manage Orders)").withStyle(ChatFormatting.GRAY))
                .send(owner, Component.literal("Your " + (order.buy() ? "Buy Order" : "Sell Offer") + " for " + Msg.number(order.amount()) + "x ")
                        .withStyle(ChatFormatting.YELLOW).append(Component.literal(def.name()).withColor(def.rarity().color()))
                        .append(Component.literal(" was filled!").withStyle(ChatFormatting.YELLOW)));
    }

    // ------------------------------------------------------------------ inventory

    private static int room(Inventory inventory, ItemStack sample) {
        int room = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack in = inventory.getItem(i);
            if (in.isEmpty()) {
                room += sample.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(in, sample)) {
                room += Math.max(0, in.getMaxStackSize() - in.getCount());
            }
        }
        return room;
    }

    private static void give(ServerPlayer player, ItemDefinition def, int count) {
        boolean unique = SkyItems.unstackable(def);
        int left = count;
        while (left > 0) {
            ItemStack stack = SkyItems.create(def, 1);
            int n = unique ? 1 : Math.min(left, stack.getMaxStackSize());
            stack.setCount(n);
            left -= n;
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
    }

    private static void fail(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1f);
    }

    /** The level that opens the Bazaar, for the menu tile and the page. */
    public static int level() {
        return SkyBlockLevels.UNLOCKS.stream().filter(u -> u.name().contains("Bazaar")).mapToInt(SkyBlockLevels.Unlock::level).findFirst().orElse(7);
    }
}
