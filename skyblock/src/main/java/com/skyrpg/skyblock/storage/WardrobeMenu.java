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
 * The Wardrobe's menu: {@link Wardrobe#SETS} sets of four armour slots (set {@code s}, piece {@code p} is slot
 * {@code s * 4 + p}) and the player's inventory, laid out for SkyRPG's own Wardrobe screen. The set being worn is the
 * player's armour slots themselves. Button {@code s} wears (or takes off) set {@code s}.
 */
public final class WardrobeMenu extends AbstractContainerMenu {
    // Layout, shared with the screen (pixels from the screen's top-left).
    public static final int PAD = 10;
    public static final int TOP = 36;
    public static final int CARD_W = 40;
    public static final int CARD_GAP = 2;
    public static final int MODEL_H = 42;
    public static final int SLOTS_Y = TOP + MODEL_H + 3;
    public static final int BUTTON_Y = SLOTS_Y + 38;
    public static final int BUTTON_H = 13;
    public static final int WIDTH = PAD * 2 + Wardrobe.SETS * CARD_W + (Wardrobe.SETS - 1) * CARD_GAP;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = BUTTON_Y + BUTTON_H + 16;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + 10;

    private final Container wardrobe;
    private final ContainerData data;
    private final ServerPlayer owner;

    public static int cardX(int set) {
        return PAD + set * (CARD_W + CARD_GAP);
    }

    /** Where piece {@code piece} of set {@code set} sits: a 2 by 2 grid under the set's model (helmet, chestplate / leggings, boots). */
    public static int slotX(int set, int piece) {
        return cardX(set) + 2 + (piece % 2) * 18;
    }

    public static int slotY(int piece) {
        return SLOTS_Y + 1 + (piece / 2) * 18;
    }

    /** The client's copy, filled in by the server's slot updates. */
    public WardrobeMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(Wardrobe.SETS * 4), new SimpleContainerData(1), null);
    }

    static WardrobeMenu server(int id, Inventory inventory, ServerPlayer owner) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return Wardrobe.equipped(owner) + 1;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        return new WardrobeMenu(id, inventory, new Sets(owner), data, owner);
    }

    private WardrobeMenu(int id, Inventory inventory, Container wardrobe, ContainerData data, ServerPlayer owner) {
        super(SkyMenus.WARDROBE, id);
        this.wardrobe = wardrobe;
        this.data = data;
        this.owner = owner;
        for (int set = 0; set < Wardrobe.SETS; set++) {
            for (int piece = 0; piece < 4; piece++) {
                int row = piece;
                addSlot(new Slot(wardrobe, set * 4 + piece, slotX(set, piece) + 1, slotY(piece)) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return Wardrobe.fits(stack, row);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    /** The set being worn, or -1. */
    public int equipped() {
        return data.get(0) - 1;
    }

    /** What's in set {@code set} (the worn set: what the player is wearing). */
    public ItemStack piece(int set, int piece) {
        return getSlot(set * 4 + piece).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (owner == null || id < 0 || id >= Wardrobe.SETS) {
            return false;
        }
        Wardrobe.toggle(owner, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int wardrobeSlots = Wardrobe.SETS * 4;
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        if (index < wardrobeSlots) {
            if (!moveItemStackTo(stack, wardrobeSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the first set that has room for this piece.
            boolean moved = false;
            for (int i = 0; i < wardrobeSlots && !moved; i++) {
                Slot to = slots.get(i);
                if (!to.hasItem() && to.mayPlace(stack)) {
                    to.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        from.setChanged();
        return stack.isEmpty() ? before : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || player == owner && owner.isAlive();
    }

    /**
     * The sets as a container: the worn set's four slots are the player's armour, the rest is the saved Wardrobe.
     * Edits go straight to the player's data.
     */
    private static final class Sets implements Container {
        private final ServerPlayer owner;

        Sets(ServerPlayer owner) {
            this.owner = owner;
        }

        @Override
        public int getContainerSize() {
            return Wardrobe.SETS * 4;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ItemStack getItem(int index) {
            int set = index / 4;
            int piece = index % 4;
            if (set == Wardrobe.equipped(owner)) {
                return owner.getItemBySlot(Wardrobe.ROWS[piece]);
            }
            return Wardrobe.stored(owner).get(index);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = getItem(index);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = current.copy();
            setItem(index, ItemStack.EMPTY);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return removeItem(index, 1);
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            int set = index / 4;
            int piece = index % 4;
            if (set == Wardrobe.equipped(owner)) {
                owner.setItemSlot(Wardrobe.ROWS[piece], stack);
            } else {
                NonNullList<ItemStack> items = Wardrobe.stored(owner);
                items.set(index, stack);
                Wardrobe.store(owner, items);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return player == owner;
        }

        @Override
        public void clearContent() {
        }

        @Override
        public void stopOpen(ContainerUser user) {
            StoragePayloads.sendSummary(owner);
        }
    }
}
