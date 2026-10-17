package com.skyrpg.skyblock.dungeon;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Malik's screen: the held dungeon item, its stars and the next star's cost; your essence. Button {@link #UPGRADE}. */
public final class MalikMenu extends AbstractContainerMenu {
    public static final int UPGRADE = 1;
    public static final int TAB = 2;
    public static final int PERK = 100;
    /** Who's on the essence shop tab. */
    private static final java.util.Set<java.util.UUID> SHOP = new java.util.HashSet<>();

    /** The held item, its stars and maximum, the next star's costs, and your essence. */
    public record View(ItemStack item, int stars, int max, List<String> cost, String essence, boolean shop, List<String> perks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readBoolean(),
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.item());
                buf.writeVarInt(view.stars());
                buf.writeVarInt(view.max());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, view.cost());
                buf.writeUtf(view.essence());
                buf.writeBoolean(view.shop());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, view.perks());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;

    public MalikMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private MalikMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.MALIK, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void open(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        ItemDefinition def = SkyItems.definition(held);
        int max = DungeonItems.maxStars(def);
        View view = new View(max > 0 ? held.copy() : ItemStack.EMPTY, ItemMods.stars(held), max, max > 0 ? DungeonItems.nextCost(held) : List.of(),
                DungeonItems.essenceLine(player), SHOP.contains(player.getUUID()),
                EssencePerks.PERKS.stream().map(p -> EssencePerks.describe(player, p)).toList());
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Malik");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new MalikMenu(id, view, player);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id == TAB) {
            if (!SHOP.remove(owner.getUUID())) {
                SHOP.add(owner.getUUID());
            }
            owner.level().getServer().execute(() -> open(owner));
            return true;
        }
        String problem = id >= PERK ? EssencePerks.buy(owner, EssencePerks.PERKS.get(Math.min(id - PERK, EssencePerks.PERKS.size() - 1)).id())
                : id == UPGRADE ? DungeonItems.upgrade(owner) : "";
        if (!problem.isEmpty()) {
            owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
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
        return true;
    }
}
