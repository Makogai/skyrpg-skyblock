package com.skyrpg.skyblock.sack;

import com.skyrpg.core.action.Actions;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Your Sacks: each sack type you have, its size and what's in it. Button {@code type * 100 + item} takes a stack out,
 * {@link #ALL} {@code +} that takes as much as fits, {@link #INSERT} puts your inventory in, {@link #SACK_OF_SACKS} opens
 * the Sack of Sacks.
 */
public final class SacksMenu extends AbstractContainerMenu {
    public static final int ALL = 50000;
    public static final int INSERT = 99999;
    /** Opens the Sack of Sacks. */
    public static final int SACK_OF_SACKS = 99998;

    /** One item in a sack: what it is (count 1) and how many you have. */
    public record ItemView(ItemStack item, long count) {
        static final StreamCodec<RegistryFriendlyByteBuf, ItemView> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, ItemView::item,
                ByteBufCodecs.VAR_LONG, ItemView::count,
                ItemView::new);
    }

    /** One sack type you have: name, icon, how much it holds of each item, and its items. */
    public record TypeView(String name, ItemStack icon, long size, List<ItemView> items) {
        static final StreamCodec<RegistryFriendlyByteBuf, TypeView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TypeView::name,
                ItemStack.OPTIONAL_STREAM_CODEC, TypeView::icon,
                ByteBufCodecs.VAR_LONG, TypeView::size,
                ItemView.CODEC.apply(ByteBufCodecs.list()), TypeView::items,
                TypeView::new);
    }

    public record View(List<TypeView> types, int missing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                TypeView.CODEC.apply(ByteBufCodecs.list()), View::types,
                ByteBufCodecs.VAR_INT, View::missing,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;
    /** The sack types behind the view's, in order (the server's side). */
    private final List<Sacks.Type> shown;

    public SacksMenu(int id, Inventory inventory, View view) {
        this(id, view, null, List.of());
    }

    private SacksMenu(int id, View view, ServerPlayer owner, List<Sacks.Type> shown) {
        super(SkyMenus.SACKS, id);
        this.view = view;
        this.owner = owner;
        this.shown = shown;
    }

    public View view() {
        return view;
    }

    static void init() {
        Actions.register("sacks", (player, params) -> open(player));
    }

    public static void open(ServerPlayer player) {
        List<Sacks.Type> shown = new ArrayList<>();
        List<TypeView> types = new ArrayList<>();
        Map<String, Long> contents = Sacks.contents(player);
        int missing = 0;
        for (Sacks.Type type : Sacks.types()) {
            long size = Sacks.size(player, type);
            if (size <= 0) {
                missing++;
                continue;
            }
            List<ItemView> items = new ArrayList<>();
            for (String id : type.items()) {
                ItemDefinition def = ItemRegistry.skyblock(id);
                if (def != null) {
                    items.add(new ItemView(SkyItems.create(def, 1), contents.getOrDefault(id, 0L)));
                }
            }
            ItemDefinition icon = ItemRegistry.skyblock(type.icon());
            types.add(new TypeView(type.name(), icon == null ? ItemStack.EMPTY : SkyItems.create(icon, 1), size, items));
            shown.add(type);
        }
        View view = new View(types, missing);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Sacks");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new SacksMenu(id, view, player, shown);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id == SACK_OF_SACKS) {
            SackOfSacks.open(owner);
            return true;
        }
        if (id == INSERT) {
            Sacks.insertInventory(owner);
        } else {
            boolean all = id >= ALL;
            int code = all ? id - ALL : id;
            int typeIndex = code / 100;
            int itemIndex = code % 100;
            if (typeIndex < 0 || typeIndex >= shown.size()) {
                return false;
            }
            List<ItemView> items = view.types().get(typeIndex).items();
            if (itemIndex >= items.size()) {
                return false;
            }
            String item = SkyItems.skyblockId(items.get(itemIndex).item());
            if (item == null) {
                return false;
            }
            Sacks.take(owner, item, all ? 36 * 64 : 64);
        }
        owner.level().getServer().execute(() -> open(owner));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || player == owner && owner.isAlive();
    }
}
