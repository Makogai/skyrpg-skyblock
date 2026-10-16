package com.skyrpg.skyblock.storage;

import com.mojang.serialization.Codec;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Hypixel's Wardrobe: {@link #SETS} armour sets, one per column (helmet, chestplate, leggings, boots from the top),
 * and under each a button to wear it. The set you're wearing stays in its column (those four slots are your armour
 * slots), so equipping another puts it back first. Armour you wear that came from nowhere has to go into your
 * inventory to make room.
 */
public final class Wardrobe {
    public static final int SETS = 9;
    static final EquipmentSlot[] ROWS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Column-major: set {@code s}, piece {@code r} is at {@code s * 4 + r}. */
    static final AttachmentType<ItemContainerContents> SLOTS = AttachmentRegistry.create(SkyRpgSkyblock.id("wardrobe"),
            builder -> builder.initializer(() -> ItemContainerContents.EMPTY).persistent(ItemContainerContents.CODEC).copyOnDeath());
    /** The set being worn, or -1. */
    static final AttachmentType<Integer> EQUIPPED = AttachmentRegistry.create(SkyRpgSkyblock.id("wardrobe_equipped"),
            builder -> builder.initializer(() -> -1).persistent(Codec.INT).copyOnDeath());

    private Wardrobe() {
    }

    static void init() {
        SLOTS.identifier();
        EQUIPPED.identifier();
    }

    static NonNullList<ItemStack> stored(ServerPlayer player) {
        NonNullList<ItemStack> items = NonNullList.withSize(SETS * 4, ItemStack.EMPTY);
        player.getAttachedOrElse(SLOTS, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    static void store(ServerPlayer player, List<ItemStack> items) {
        player.setAttached(SLOTS, ItemContainerContents.fromItems(items));
    }

    public static int equipped(ServerPlayer player) {
        return player.getAttachedOrElse(EQUIPPED, -1);
    }

    /** What's in each set's helmet slot (the worn set shows what you're wearing), for the Storage page. */
    public static List<ItemStack> heads(ServerPlayer player) {
        NonNullList<ItemStack> items = stored(player);
        int worn = equipped(player);
        List<ItemStack> out = new ArrayList<>();
        for (int set = 0; set < SETS; set++) {
            ItemStack head = set == worn ? player.getItemBySlot(EquipmentSlot.HEAD) : items.get(set * 4);
            if (head.isEmpty()) {
                ItemStack chest = set == worn ? player.getItemBySlot(EquipmentSlot.CHEST) : items.get(set * 4 + 1);
                head = chest;
            }
            out.add(head.copy());
        }
        return out;
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory, player), Component.literal("Wardrobe")));
    }

    /** Wears set {@code set}, or takes it off if it's the one being worn. */
    static void toggle(ServerPlayer player, int set) {
        int worn = equipped(player);
        NonNullList<ItemStack> items = stored(player);
        if (worn == set) {
            for (int r = 0; r < 4; r++) {
                items.set(set * 4 + r, player.getItemBySlot(ROWS[r]).copy());
                player.setItemSlot(ROWS[r], ItemStack.EMPTY);
            }
            store(player, items);
            player.setAttached(EQUIPPED, -1);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1f, 0.8f);
            return;
        }
        boolean empty = true;
        for (int r = 0; r < 4; r++) {
            empty &= items.get(set * 4 + r).isEmpty();
        }
        if (empty) {
            player.sendSystemMessage(Component.literal("Wardrobe slot " + (set + 1) + " is empty: put armour in it first.").withStyle(ChatFormatting.RED));
            return;
        }
        if (worn >= 0) {
            // The worn set goes back into its column.
            for (int r = 0; r < 4; r++) {
                items.set(worn * 4 + r, player.getItemBySlot(ROWS[r]).copy());
                player.setItemSlot(ROWS[r], ItemStack.EMPTY);
            }
        } else {
            // Armour from outside the wardrobe goes into the inventory, if there's room for all of it.
            int pieces = 0;
            for (EquipmentSlot slot : ROWS) {
                pieces += player.getItemBySlot(slot).isEmpty() ? 0 : 1;
            }
            if (pieces > freeSlots(player.getInventory())) {
                player.sendSystemMessage(Component.literal("Make room in your inventory for the armour you're wearing first.").withStyle(ChatFormatting.RED));
                return;
            }
            for (EquipmentSlot slot : ROWS) {
                ItemStack piece = player.getItemBySlot(slot);
                if (!piece.isEmpty()) {
                    player.getInventory().add(piece.copy());
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        for (int r = 0; r < 4; r++) {
            player.setItemSlot(ROWS[r], items.get(set * 4 + r).copy());
            items.set(set * 4 + r, ItemStack.EMPTY);
        }
        store(player, items);
        player.setAttached(EQUIPPED, set);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_DIAMOND.value(), SoundSource.PLAYERS, 1f, 1.2f);
    }

    private static int freeSlots(Inventory inventory) {
        int free = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            free += inventory.getItem(i).isEmpty() ? 1 : 0;
        }
        return free;
    }

    /** Whether this item may go in this row: something worn in that armour slot. */
    static boolean fits(ItemStack stack, int row) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == ROWS[row];
    }

    /** Six rows: four of armour, one of buttons, one with a close button. */
    static final class Menu extends ChestMenu {
        private final ServerPlayer owner;

        Menu(int id, Inventory inventory, ServerPlayer owner) {
            this(id, inventory, owner, new Slots(owner));
        }

        private Menu(int id, Inventory inventory, ServerPlayer owner, Slots container) {
            super(MenuType.GENERIC_9x6, id, inventory, container, 6);
            this.owner = owner;
            for (int i = 0; i < 54; i++) {
                Slot plain = slots.get(i);
                int row = i / 9;
                Slot slot = new Slot(container, plain.getContainerSlot(), plain.x, plain.y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return row < 4 && fits(stack, row);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return row < 4;
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                };
                slot.index = i;
                slots.set(i, slot);
            }
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            if (slotId >= 36 && slotId < 54) {
                if (slotId < 45) {
                    toggle(owner, slotId - 36);
                    ((Slots) getContainer()).refresh();
                } else if (slotId == 49) {
                    owner.closeContainer();
                }
                return;
            }
            super.clicked(slotId, button, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index < 54) {
                // Out of the wardrobe into the inventory.
                return index < 36 ? super.quickMoveStack(player, index) : ItemStack.EMPTY;
            }
            // From the inventory into the first free fitting wardrobe slot.
            Slot from = slots.get(index);
            ItemStack stack = from.getItem();
            for (int i = 0; i < 36; i++) {
                Slot to = slots.get(i);
                if (!to.hasItem() && to.mayPlace(stack)) {
                    to.setByPlayer(stack.split(1));
                    from.setChanged();
                    return ItemStack.EMPTY;
                }
            }
            return ItemStack.EMPTY;
        }
    }

    /**
     * The wardrobe as a 54-slot container: rows 0-3 are the sets (the worn set's column is the player's armour
     * slots), row 4 the buttons, row 5 a close button. Edits go straight to the player's data.
     */
    private static final class Slots implements Container {
        private final ServerPlayer owner;
        private final ItemStack[] buttons = new ItemStack[18];

        Slots(ServerPlayer owner) {
            this.owner = owner;
            refresh();
        }

        void refresh() {
            int worn = equipped(owner);
            NonNullList<ItemStack> items = stored(owner);
            for (int set = 0; set < SETS; set++) {
                boolean empty = set != worn;
                for (int r = 0; r < 4 && empty; r++) {
                    empty = items.get(set * 4 + r).isEmpty();
                }
                ItemStack button;
                if (set == worn) {
                    button = named(Items.DYE.lime(), "Slot " + (set + 1) + ": Equipped", ChatFormatting.GREEN, "Click to take this set off.");
                } else if (empty) {
                    button = named(Items.DYE.gray(), "Slot " + (set + 1) + ": Empty", ChatFormatting.GRAY, "Put armour above to save a set.");
                } else {
                    button = named(Items.DYE.pink(), "Slot " + (set + 1) + ": Ready", ChatFormatting.YELLOW, "Click to wear this set.");
                }
                buttons[set] = button;
            }
            for (int i = 9; i < 18; i++) {
                buttons[i] = i == 13 ? named(Items.BARRIER, "Close", ChatFormatting.RED, "")
                        : named(Items.STAINED_GLASS_PANE.black(), " ", ChatFormatting.DARK_GRAY, "");
            }
        }

        private static ItemStack named(net.minecraft.world.item.Item item, String name, ChatFormatting color, String lore) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color));
            if (!lore.isEmpty()) {
                stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
            }
            return stack;
        }

        @Override
        public int getContainerSize() {
            return 54;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ItemStack getItem(int index) {
            int row = index / 9;
            int set = index % 9;
            if (row >= 4) {
                return buttons[index - 36];
            }
            if (set == equipped(owner)) {
                return owner.getItemBySlot(ROWS[row]);
            }
            return stored(owner).get(set * 4 + row);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = getItem(index);
            if (index >= 36 || current.isEmpty()) {
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
            int row = index / 9;
            int set = index % 9;
            if (row >= 4) {
                return;
            }
            if (set == equipped(owner)) {
                owner.setItemSlot(ROWS[row], stack);
            } else {
                NonNullList<ItemStack> items = stored(owner);
                items.set(set * 4 + row, stack);
                store(owner, items);
            }
            refresh();
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
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            StoragePayloads.sendSummary(owner);
        }
    }
}
