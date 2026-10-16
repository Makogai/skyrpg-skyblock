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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Hypixel's Wardrobe: {@link #SETS} armour sets, each helmet, chestplate, leggings and boots, and a button to wear
 * one (the screen is {@link WardrobeMenu}). The set you're wearing stays in its column (those four slots are your armour
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
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> WardrobeMenu.server(id, inventory, player), Component.literal("Wardrobe")));
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
}
