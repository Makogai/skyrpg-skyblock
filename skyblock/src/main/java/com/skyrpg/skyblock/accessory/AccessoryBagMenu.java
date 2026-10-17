package com.skyrpg.skyblock.accessory;

import com.skyrpg.skyblock.storage.SkyMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One page of the Accessory Bag (45 slots, the ones past the bag's size locked) and the player's inventory. Only
 * accessories go in. Buttons 0-8 switch pages; {@link #MAXWELL} opens Maxwell's powers.
 */
public final class AccessoryBagMenu extends AbstractContainerMenu {
    public static final int MAXWELL = 99;
    public static final int MAX_PAGES = 9;
    // Layout, shared with the screen.
    public static final int PAD = 8;
    public static final int TOP = 26;
    public static final int TAB_W = 26;
    public static final int TAB_H = 17;
    public static final int GRID_X = PAD + TAB_W + 8;
    public static final int GRID_Y = TOP;
    public static final int ROWS = AccessoryBag.PAGE_SIZE / 9;
    public static final int WIDTH = GRID_X + 9 * 18 + PAD;
    public static final int INV_X = GRID_X;
    public static final int INV_Y = GRID_Y + ROWS * 18 + 18;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;

    private final Container page;
    private final ContainerData data;
    private final ServerPlayer owner;

    /** The client's copy. */
    public AccessoryBagMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(AccessoryBag.PAGE_SIZE), new SimpleContainerData(3), null);
    }

    static AccessoryBagMenu server(int id, Inventory inventory, ServerPlayer owner, int page) {
        Page container = new Page(owner, Math.min(page, AccessoryBag.pages(owner) - 1));
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> container.page;
                    case 1 -> AccessoryBag.slots(owner);
                    default -> AccessoryBag.magicalPower(owner);
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        return new AccessoryBagMenu(id, inventory, container, data, owner);
    }

    private AccessoryBagMenu(int id, Inventory inventory, Container page, ContainerData data, ServerPlayer owner) {
        super(SkyMenus.ACCESSORY_BAG, id);
        this.page = page;
        this.data = data;
        this.owner = owner;
        for (int i = 0; i < AccessoryBag.PAGE_SIZE; i++) {
            int index = i;
            addSlot(new Slot(page, i, GRID_X + 1 + (i % 9) * 18, GRID_Y + 1 + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return open(index) && AccessoryBag.isAccessory(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X + 1, INV_Y);
        addDataSlots(data);
    }

    public int page() {
        return data.get(0);
    }

    public int bagSlots() {
        return data.get(1);
    }

    public int magicalPower() {
        return data.get(2);
    }

    public int pages() {
        return Math.max(1, (bagSlots() + AccessoryBag.PAGE_SIZE - 1) / AccessoryBag.PAGE_SIZE);
    }

    /** Whether this slot of the page shown is within the bag's size. */
    public boolean open(int slot) {
        return page() * AccessoryBag.PAGE_SIZE + slot < bagSlots();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (owner == null) {
            return false;
        }
        if (id == MAXWELL) {
            owner.level().getServer().execute(() -> MaxwellMenu.open(owner));
            return true;
        }
        if (id >= 0 && id < AccessoryBag.pages(owner) && page instanceof Page pages) {
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
        boolean moved;
        if (index < AccessoryBag.PAGE_SIZE) {
            moved = moveItemStackTo(stack, AccessoryBag.PAGE_SIZE, slots.size(), true);
        } else {
            moved = false;
            for (int i = 0; i < AccessoryBag.PAGE_SIZE && !moved; i++) {
                Slot to = slots.get(i);
                if (!to.hasItem() && to.mayPlace(stack)) {
                    to.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
        }
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

    /** The page shown, written back to the player's data after every change; can switch to another page. */
    private static final class Page extends SimpleContainer {
        private final ServerPlayer owner;
        private int page;
        private boolean loading;

        Page(ServerPlayer owner, int page) {
            super(AccessoryBag.PAGE_SIZE);
            this.owner = owner;
            show(Math.max(0, page));
        }

        void show(int page) {
            this.page = page;
            loading = true;
            NonNullList<ItemStack> items = AccessoryBag.page(owner, page);
            for (int i = 0; i < AccessoryBag.PAGE_SIZE; i++) {
                setItem(i, items.get(i));
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) {
                AccessoryBag.save(owner, page, getItems());
            }
        }
    }
}
