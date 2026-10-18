package com.skyrpg.skyblock.ui;

import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.function.Function;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A themed card screen for any NPC or menu: {@link #open} with a function that builds the view for a player and a click
 * handler; clicking card {@code n} calls the handler (it returns "" or why not, shown in red) and the view is rebuilt.
 * The client draws it with {@code CardScreen}.
 */
public final class CardMenu extends AbstractContainerMenu {
    /** Clicking a card: "" when done, else what went wrong. Return null to close the screen. */
    @FunctionalInterface
    public interface Clicker {
        String click(ServerPlayer player, int card);
    }

    private final CardView view;
    private final ServerPlayer owner;
    private final Function<ServerPlayer, CardView> builder;
    private final Clicker clicker;

    public CardMenu(int id, Inventory inventory, CardView view) {
        this(id, view, null, null, null);
    }

    private CardMenu(int id, CardView view, ServerPlayer owner, Function<ServerPlayer, CardView> builder, Clicker clicker) {
        super(SkyMenus.CARDS, id);
        this.view = view;
        this.owner = owner;
        this.builder = builder;
        this.clicker = clicker;
    }

    public CardView view() {
        return view;
    }

    public static void open(ServerPlayer player, Function<ServerPlayer, CardView> builder, Clicker clicker) {
        CardView view = builder.apply(player);
        player.openMenu(new ExtendedMenuProvider<CardView>() {
            @Override
            public CardView getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(view.title());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new CardMenu(id, view, player, builder, clicker);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner || this.clicker == null || id < 0 || id >= view.cards().size()) {
            return false;
        }
        String problem = this.clicker.click(owner, id);
        if (problem == null) {
            owner.closeContainer();
            return true;
        }
        if (!problem.isEmpty()) {
            owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
        owner.level().getServer().execute(() -> open(owner, builder, this.clicker));
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
