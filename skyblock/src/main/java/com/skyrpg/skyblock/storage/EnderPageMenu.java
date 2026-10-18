package com.skyrpg.skyblock.storage;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One Ender Chest page (45 slots) and the player's inventory, for SkyRPG's own Storage screen. Buttons 0-8 switch to
 * another page in place; {@link #WARDROBE_BUTTON} opens the Wardrobe.
 */
public final class EnderPageMenu extends AbstractContainerMenu {
    public static final int WARDROBE_BUTTON = 99;
    // Layout, shared with the screen.
    public static final int PAD = 10;
    public static final int TOP = 36;
    public static final int TAB_W = 26;
    public static final int TAB_H = 15;
    public static final int GRID_X = PAD + TAB_W + 8;
    public static final int GRID_Y = TOP;
    public static final int ROWS = Storage.PAGE_SIZE / 9;
    public static final int WIDTH = GRID_X + 9 * 18 + PAD;
    public static final int INV_X = GRID_X;
    public static final int INV_Y = GRID_Y + ROWS * 18 + 16;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;

    private final Container page;
    private final ContainerData data;
    private final ServerPlayer owner;

    /** The client's copy. */
    public EnderPageMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(Storage.PAGE_SIZE), new SimpleContainerData(3), null);
    }

    static EnderPageMenu server(int id, Inventory inventory, ServerPlayer owner, int page) {
        Page container = new Page(owner, page);
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? container.page : index == 1 ? Storage.unlocked(owner) : container.size();
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        return new EnderPageMenu(id, inventory, container, data, owner);
    }

    private EnderPageMenu(int id, Inventory inventory, Container page, ContainerData data, ServerPlayer owner) {
        super(SkyMenus.ENDER_PAGE, id);
        this.page = page;
        this.data = data;
        this.owner = owner;
        for (int i = 0; i < Storage.PAGE_SIZE; i++) {
            int index = i;
            // a backpack smaller than a page: the slots past it are switched off
            addSlot(new Slot(page, i, GRID_X + 1 + (i % 9) * 18, GRID_Y + 1 + (i / 9) * 18) {
                @Override
                public boolean isActive() {
                    return index < size();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return index < size() && Storage.capacity(stack) <= 0;
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X + 1, INV_Y);
        addDataSlots(data);
    }

    /** The page shown (0-based). */
    public int page() {
        return data.get(0);
    }

    public int unlocked() {
        return data.get(1);
    }

    /** How many of the 45 slots are in use: a page has all, a backpack its size. */
    public int size() {
        int size = data.get(2);
        return size <= 0 ? Storage.PAGE_SIZE : size;
    }

    /** A backpack (not an Ender Chest page) is shown. */
    public boolean backpack() {
        return page() >= Storage.BACKPACK_PAGE;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (owner == null) {
            return false;
        }
        if (id == WARDROBE_BUTTON) {
            owner.level().getServer().execute(() -> Wardrobe.open(owner));
            return true;
        }
        if (id >= 0 && id < Storage.unlocked(owner) && page instanceof Page pages) {
            pages.show(id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < Storage.PAGE_SIZE
                ? moveItemStackTo(stack, Storage.PAGE_SIZE, slots.size(), true)
                : Storage.capacity(stack) <= 0 && moveItemStackTo(stack, 0, size(), false);
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

    /** The page being shown, written back to the player's data after every change; can switch to another page. */
    private static final class Page extends SimpleContainer {
        private final ServerPlayer owner;
        private int page;
        private boolean loading;

        /** Slots in use: 45 for a page, the backpack's size for a backpack. */
        private int size = Storage.PAGE_SIZE;

        Page(ServerPlayer owner, int page) {
            super(Storage.PAGE_SIZE);
            this.owner = owner;
            show(page);
        }

        int size() {
            return size;
        }

        void show(int page) {
            this.page = page;
            loading = true;
            boolean backpack = page >= Storage.BACKPACK_PAGE;
            NonNullList<ItemStack> items = backpack ? Storage.backpackItems(owner, page - Storage.BACKPACK_PAGE) : Storage.page(owner, page);
            size = backpack ? items.size() : Storage.PAGE_SIZE;
            for (int i = 0; i < Storage.PAGE_SIZE; i++) {
                setItem(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) {
                return;
            }
            if (page >= Storage.BACKPACK_PAGE) {
                Storage.saveBackpack(owner, page - Storage.BACKPACK_PAGE, getItems().subList(0, size));
            } else {
                Storage.save(owner, page, getItems());
            }
        }

        @Override
        public void stopOpen(ContainerUser user) {
            super.stopOpen(user);
            StoragePayloads.sendSummary(owner);
        }
    }
}
