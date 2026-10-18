package com.skyrpg.skyblock.quiver;

import com.skyrpg.skyblock.storage.SkyMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The quiver's 45 slots (arrows only), written back to the player's data after every change, and the inventory. */
public final class QuiverMenu extends AbstractContainerMenu {
    public static final int GRID_X = 10;
    public static final int GRID_Y = 36;
    public static final int INV_X = 11;
    public static final int INV_Y = 148;

    private final ServerPlayer owner;

    public QuiverMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(Quiver.SIZE), null);
    }

    private QuiverMenu(int id, Inventory inventory, SimpleContainer quiver, ServerPlayer owner) {
        super(SkyMenus.QUIVER, id);
        this.owner = owner;
        for (int i = 0; i < Quiver.SIZE; i++) {
            addSlot(new Slot(quiver, i, GRID_X + 1 + (i % 9) * 18, GRID_Y + 1 + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Quiver.isArrow(stack);
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new QuiverMenu(id, inventory, new Stored(player), player),
                Component.literal("Quiver")));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < Quiver.SIZE ? moveItemStackTo(stack, Quiver.SIZE, slots.size(), true)
                : Quiver.isArrow(stack) && moveItemStackTo(stack, 0, Quiver.SIZE, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || player == owner && owner.isAlive();
    }

    /** The player's quiver, saved after every change. */
    private static final class Stored extends SimpleContainer {
        private final ServerPlayer owner;
        private boolean loading = true;

        Stored(ServerPlayer owner) {
            super(Quiver.SIZE);
            this.owner = owner;
            NonNullList<ItemStack> items = Quiver.contents(owner);
            for (int i = 0; i < Quiver.SIZE; i++) {
                setItem(i, items.get(i));
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) {
                Quiver.save(owner, getItems());
            }
        }
    }
}
