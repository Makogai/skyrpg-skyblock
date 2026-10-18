package com.skyrpg.skyblock.shop;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * An NPC shop: the offers (drawn by the screen from {@link View}; buying is a menu button), a row of your last sales
 * to buy back (real slots, so they stay in sync), and your inventory, where clicking an item sells it, as on Hypixel.
 * Nothing in here moves by hand.
 */
public final class ShopMenu extends AbstractContainerMenu {
    /** Button ids: the offer's index to buy one, plus this to buy a stack. */
    public static final int STACK = 10_000;
    // Layout, shared with the screen.
    public static final int PAD = 10;
    public static final int TOP = 36;
    public static final int CELL = 20;
    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    public static final int WIDTH = COLUMNS * CELL + 2 * PAD + 6;
    public static final int OFFERS_X = PAD;
    public static final int OFFERS_Y = TOP;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int BUYBACK_Y = OFFERS_Y + ROWS * CELL + 16;
    public static final int INV_Y = BUYBACK_Y + 18 + 16;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;
    public static final int INVENTORY_START = SkyShops.BUYBACK;

    /** One cost of an offer: the item (a picture of it) and how many. */
    public record Cost(ItemStack icon, int count) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cost> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Cost::icon, ByteBufCodecs.VAR_INT, Cost::count, Cost::new);
    }

    /** One offer as the screen shows it: the item with its count, the coins and the other costs. */
    public record OfferView(ItemStack item, int count, long coins, List<Cost> costs) {
        static final StreamCodec<RegistryFriendlyByteBuf, OfferView> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, OfferView::item,
                ByteBufCodecs.VAR_INT, OfferView::count,
                ByteBufCodecs.VAR_LONG, OfferView::coins,
                Cost.CODEC.apply(ByteBufCodecs.list()), OfferView::costs,
                OfferView::new);
    }

    public record View(String id, String name, String island, List<OfferView> offers) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, View::id,
                ByteBufCodecs.STRING_UTF8, View::name,
                ByteBufCodecs.STRING_UTF8, View::island,
                OfferView.CODEC.apply(ByteBufCodecs.list()), View::offers,
                View::new);
    }

    private final View view;
    private final SkyShops.Shop shop;
    private final ServerPlayer owner;

    /** The client's copy. */
    public ShopMenu(int id, Inventory inventory, View view) {
        this(id, inventory, view, new SimpleContainer(SkyShops.BUYBACK), null, null);
    }

    private ShopMenu(int id, Inventory inventory, View view, Container buyback, SkyShops.Shop shop, ServerPlayer owner) {
        super(SkyMenus.SHOP, id);
        this.view = view;
        this.shop = shop;
        this.owner = owner;
        for (int i = 0; i < SkyShops.BUYBACK; i++) {
            addSlot(new Slot(buyback, i, INV_X + i * 18, BUYBACK_Y + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
    }

    public View view() {
        return view;
    }

    public static void open(ServerPlayer player, SkyShops.Shop shop) {
        View view = view(shop);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(shop.name());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new ShopMenu(id, inventory, view, buyback(player), shop, player);
            }
        });
    }

    private static View view(SkyShops.Shop shop) {
        List<OfferView> offers = new ArrayList<>();
        for (SkyShops.Offer offer : shop.offers()) {
            ItemDefinition def = ItemRegistry.skyblock(offer.item());
            if (def == null) {
                continue;
            }
            ItemStack item = SkyItems.create(def, 1);
            item.setCount(Math.min(99, offer.count()));
            List<Cost> costs = new ArrayList<>();
            for (Map.Entry<String, Integer> cost : offer.costs().entrySet()) {
                ItemDefinition costDef = ItemRegistry.skyblock(cost.getKey());
                costs.add(new Cost(costDef == null ? ItemStack.EMPTY : SkyItems.create(costDef, 1), cost.getValue()));
            }
            offers.add(new OfferView(item, offer.count(), offer.coins(), costs));
        }
        return new View(shop.id().getPath(), shop.name(), shop.island(), offers);
    }

    /** The buyback row, read from the player's recent sales. */
    private static Container buyback(ServerPlayer player) {
        return new SimpleContainer(SkyShops.BUYBACK) {
            @Override
            public ItemStack getItem(int index) {
                List<SkyShops.Sale> sales = new ArrayList<>(SkyShops.sales(player));
                return index < sales.size() ? sales.get(index).stack() : ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItem(int index, int count) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItemNoUpdate(int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public void setItem(int index, ItemStack stack) {
            }
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (owner == null || shop == null) {
            return false;
        }
        SkyShops.buy(owner, shop, id % STACK, id >= STACK);
        broadcastChanges();
        return true;
    }

    /** Clicking sells (your inventory) or buys back (the row above it); nothing gets picked up. */
    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (owner == null || slot < 0 || slot >= slots.size()) {
            return;
        }
        if (slot < INVENTORY_START) {
            SkyShops.buyback(owner, slot);
        } else {
            SkyShops.sell(owner, slots.get(slot).getContainerSlot());
        }
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
