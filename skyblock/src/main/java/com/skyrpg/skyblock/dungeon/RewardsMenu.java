package com.skyrpg.skyblock.dungeon;

import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
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

/** The reward chests at the end of a run: each chest's contents and price; a button opens one (button = its index). */
public final class RewardsMenu extends AbstractContainerMenu {
    /** One chest as shown: tier (0 Wood .. 5 Bedrock), price, items, essence lines, opened. */
    public record ChestView(int tier, long cost, List<ItemStack> items, List<String> essence, boolean opened) {
        static final StreamCodec<RegistryFriendlyByteBuf, ChestView> CODEC = new StreamCodec<>() {
            @Override
            public ChestView decode(RegistryFriendlyByteBuf buf) {
                return new ChestView(buf.readVarInt(), buf.readVarLong(), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, ChestView chest) {
                buf.writeVarInt(chest.tier());
                buf.writeVarLong(chest.cost());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, chest.items());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, chest.essence());
                buf.writeBoolean(chest.opened());
            }
        };
    }

    /** The floor, the rank and score, the chests, and the player's purse. */
    public record View(String floor, String rank, int score, List<ChestView> chests, long purse) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readUtf(), buf.readUtf(), buf.readVarInt(), ChestView.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarLong());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeUtf(view.floor());
                buf.writeUtf(view.rank());
                buf.writeVarInt(view.score());
                ChestView.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.chests());
                buf.writeVarLong(view.purse());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;
    private final Run run;

    public RewardsMenu(int id, Inventory inventory, View view) {
        this(id, view, null, null);
    }

    private RewardsMenu(int id, View view, ServerPlayer owner, Run run) {
        super(SkyMenus.DUNGEON_REWARDS, id);
        this.view = view;
        this.owner = owner;
        this.run = run;
    }

    public View view() {
        return view;
    }

    static void open(ServerPlayer player, Run run) {
        List<ChestView> chests = new ArrayList<>();
        for (Rewards.Chest chest : Rewards.chestsOf(run, player)) {
            List<String> essence = new ArrayList<>();
            chest.essence.forEach((kind, amount) -> essence.add(amount + " " + com.skyrpg.skyblock.essence.Essence.name(kind)));
            chests.add(new ChestView(chest.tier, chest.cost, List.copyOf(chest.items), essence, chest.opened));
        }
        View view = new View(run.floor.title(), run.result == null ? "?" : run.result.rank(), run.result == null ? 0 : run.result.total(), chests,
                com.skyrpg.core.economy.Wallet.balance(player));
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Reward Chests");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new RewardsMenu(id, view, player, run);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        String problem = Rewards.open(run, owner, id);
        if (!problem.isEmpty()) {
            owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
        owner.level().getServer().execute(() -> open(owner, run));
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
