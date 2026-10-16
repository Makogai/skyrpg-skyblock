package com.skyrpg.skyblock.bazaar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every open Bazaar order, saved with the world: buy orders (coins paid in, items to claim as they fill) and sell
 * offers (items handed in, coins to claim as they fill). An order belongs to a player's profile, so what it earns
 * goes to the purse and inventory of the profile that placed it.
 */
public final class BazaarOrders extends SavedData {
    /**
     * One order: {@code amount} of {@code product} at {@code price} each; {@code filled} of them have traded and
     * {@code claimed} of those were collected.
     */
    public record Order(long id, UUID owner, UUID profile, String product, boolean buy, double price, int amount, int filled, int claimed,
                        long created) {
        static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Order::id),
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Order::owner),
                UUIDUtil.STRING_CODEC.fieldOf("profile").forGetter(Order::profile),
                Codec.STRING.fieldOf("product").forGetter(Order::product),
                Codec.BOOL.fieldOf("buy").forGetter(Order::buy),
                Codec.DOUBLE.fieldOf("price").forGetter(Order::price),
                Codec.INT.fieldOf("amount").forGetter(Order::amount),
                Codec.INT.fieldOf("filled").forGetter(Order::filled),
                Codec.INT.fieldOf("claimed").forGetter(Order::claimed),
                Codec.LONG.fieldOf("created").forGetter(Order::created)).apply(i, Order::new));

        public int open() {
            return amount - filled;
        }

        public int claimable() {
            return filled - claimed;
        }

        public boolean done() {
            return filled >= amount && claimed >= filled;
        }

        Order withFilled(int value) {
            return new Order(id, owner, profile, product, buy, price, amount, value, claimed, created);
        }

        Order withClaimed(int value) {
            return new Order(id, owner, profile, product, buy, price, amount, filled, value, created);
        }

        /** Cancelled: nothing more trades; what filled stays to claim. */
        Order cancelled() {
            return new Order(id, owner, profile, product, buy, price, filled, filled, claimed, created);
        }
    }

    static final Codec<BazaarOrders> CODEC = RecordCodecBuilder.create(i -> i.group(
            Order.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(d -> d.orders),
            Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(d -> d.nextId)).apply(i, BazaarOrders::new));
    static final SavedDataType<BazaarOrders> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("bazaar"), BazaarOrders::new, CODEC, null);

    final List<Order> orders;
    private long nextId;

    private BazaarOrders() {
        this(List.of(), 1L);
    }

    private BazaarOrders(List<Order> orders, long nextId) {
        this.orders = new ArrayList<>(orders);
        this.nextId = nextId;
    }

    static BazaarOrders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    long nextId() {
        setDirty();
        return nextId++;
    }

    void add(Order order) {
        orders.add(order);
        setDirty();
    }

    /** Swaps an order for its new state; drops it once it's done. */
    void update(Order order) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).id() == order.id()) {
                if (order.done()) {
                    orders.remove(i);
                } else {
                    orders.set(i, order);
                }
                setDirty();
                return;
            }
        }
    }

    Order find(long id) {
        for (Order order : orders) {
            if (order.id() == id) {
                return order;
            }
        }
        return null;
    }
}
