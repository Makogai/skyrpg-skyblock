package com.skyrpg.skyblock.mining;

import com.skyrpg.skyblock.storage.SkyMenus;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/** The Commissions board (the King's and the Emissaries'): each slot's task and progress; button {@code i} claims slot {@code i}. */
public final class CommissionsMenu extends AbstractContainerMenu {
    public record SlotView(String name, String objective, int progress, int amount) {
        static final StreamCodec<RegistryFriendlyByteBuf, SlotView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SlotView::name,
                ByteBufCodecs.STRING_UTF8, SlotView::objective,
                ByteBufCodecs.VAR_INT, SlotView::progress,
                ByteBufCodecs.VAR_INT, SlotView::amount,
                SlotView::new);

        public boolean done() {
            return progress >= amount;
        }
    }

    public record View(List<SlotView> slots, int completed, int dailyLeft, int hotm) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                SlotView.CODEC.apply(ByteBufCodecs.list()), View::slots,
                ByteBufCodecs.VAR_INT, View::completed,
                ByteBufCodecs.VAR_INT, View::dailyLeft,
                ByteBufCodecs.VAR_INT, View::hotm,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;
    /** Which board this is: the one where it was opened. */
    private final Commissions.Area area;

    public CommissionsMenu(int id, Inventory inventory, View view) {
        this(id, view, null, Commissions.Area.MINES);
    }

    private CommissionsMenu(int id, View view, ServerPlayer owner, Commissions.Area area) {
        super(SkyMenus.COMMISSIONS, id);
        this.view = view;
        this.owner = owner;
        this.area = area;
    }

    public View view() {
        return view;
    }

    public static void open(ServerPlayer player) {
        Commissions.Area area = Commissions.area(player);
        View view = view(player, area);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Commissions");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new CommissionsMenu(id, view, player, area);
            }
        });
    }

    private static View view(ServerPlayer player, Commissions.Area area) {
        Commissions.Board board = Commissions.board(player, area);
        Commissions.Board daily = player.getAttachedOrElse(Commissions.BOARD, Commissions.Board.EMPTY);
        List<SlotView> slots = new ArrayList<>();
        for (Commissions.Slot slot : board.slots()) {
            Commissions.Task task = Commissions.task(slot.task());
            if (task != null) {
                slots.add(new SlotView(task.name(), task.objective(), Math.min(slot.progress(), task.amount()), task.amount()));
            }
        }
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        int today = day.equals(daily.day()) ? daily.today() : 0;
        return new View(slots, board.completed(), Math.max(0, 4 - today), Hotm.level(player));
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        String problem = Commissions.claim(owner, area, id);
        if (!problem.isEmpty()) {
            owner.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
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
